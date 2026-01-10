// vars/standardBackendPipeline.groovy
def call(Map config = [:]) {
    def dockerRegistry = config.registry ?: "yassinekamouss"
    def appName = config.appName
    
    pipeline {
        agent any 
        
        tools {
            maven 'maven-3' 
            jdk 'jdk-17'
        }
        
        stages {
            stage('Checkout & Init') {
                steps {
                    checkout scm
                    script {
                        env.IMAGE_TAG = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                        env.FULL_IMAGE_NAME = "${dockerRegistry}/${appName}:${env.IMAGE_TAG}"
                        
                        // 1. AJOUT : Définir la variable pour le tag latest
                        env.LATEST_IMAGE_NAME = "${dockerRegistry}/${appName}:latest"
                        
                        echo "Version : ${env.IMAGE_TAG}"
                        echo "Image tagguée : ${env.FULL_IMAGE_NAME}"
                        echo "Image latest : ${env.LATEST_IMAGE_NAME}"
                    }
                }
            }

            stage('Build & Test') {
                steps {
                    sh 'mvn clean package -DskipTests'
                }
            }

            stage('Docker Build') {
                steps {
                    script {
                        // 2. MODIFICATION : Ajouter un deuxième flag -t pour le tag latest
                        sh "docker build -t ${env.FULL_IMAGE_NAME} -t ${env.LATEST_IMAGE_NAME} ."
                    }
                }
            }

            stage('Docker Push') {
                steps {
                    script {
                        withCredentials([usernamePassword(credentialsId: 'DOCKER_HUB', passwordVariable: 'DOCKER_PASS', usernameVariable: 'DOCKER_USER')]) {
                            sh 'echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin'
                            
                            // 3. MODIFICATION : Pousser les deux tags
                            sh "docker push ${env.FULL_IMAGE_NAME}"
                            sh "docker push ${env.LATEST_IMAGE_NAME}"
                        }
                    }
                }
            }
        }
        
        post {
            always {
                cleanWs()
            }
        }
    }
}
