// vars/standardBackendPipeline.groovy
def call(Map config = [:]) {
    // Valeur par défaut
    def dockerRegistry = config.registry ?: "yassinekamouss"
    def appName = config.appName
    
    pipeline {
        agent any 
        
        tools {
            maven 'maven-3' 
            jdk 'jdk-17'
        }
        
        // On enlève le bloc environment global dynamique pour éviter le crash
        
        stages {
            stage('Checkout & Init') {
                steps {
                    checkout scm
                    script {
                        // On calcule les variables ici, une fois que le code est là
                        env.IMAGE_TAG = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                        env.FULL_IMAGE_NAME = "${dockerRegistry}/${appName}:${env.IMAGE_TAG}"
                        
                        echo "Version détectée : ${env.IMAGE_TAG}"
                        echo "Nom complet de l'image : ${env.FULL_IMAGE_NAME}"
                    }
                }
            }

            stage('Build & Test') {
                steps {
                    // Le -DskipTests est optionnel, retire-le si tu veux lancer les tests unitaires
                    sh 'mvn clean package -DskipTests' 
                }
            }

            stage('Docker Build') {
                steps {
                    script {
                        // On utilise la variable d'environnement définie plus haut
                        sh "docker build -t ${env.FULL_IMAGE_NAME} ."
                    }
                }
            }

            stage('Docker Push') {
                steps {
                    script {
                        withCredentials([usernamePassword(credentialsId: 'DOCKER_HUB', passwordVariable: 'DOCKER_PASS', usernameVariable: 'DOCKER_USER')]) {
                            // Sécurité : ne jamais afficher le mot de passe dans les logs, le 'echo' ici est masqué par Jenkins mais attention
                            sh 'echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin'
                            sh "docker push ${env.FULL_IMAGE_NAME}"
                        }
                    }
                }
            }
        }
        
        post {
            always {
                cleanWs() 
            }
            success {
                echo "Pipeline réussi pour ${appName} ! Image : ${env.FULL_IMAGE_NAME}"
            }
        }
    }
}