def call(Map config = [:]) {
    def dockerRegistry = "saaymo"
    def appName = config.appName 

    pipeline {
        // On définit un agent global pour que le 'post' et les stages simples fonctionnent
        agent any 

        stages {
            stage('Checkout & Init') {
                steps {
                    checkout scm
                    script {
                        env.IMAGE_TAG = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                        env.FULL_IMAGE_NAME = "${dockerRegistry}/${appName}:${env.IMAGE_TAG}"
                        env.LATEST_IMAGE_NAME = "${dockerRegistry}/${appName}:latest"
                        echo "Déploiement du modèle : ${appName}"
                    }
                }
            }

            stage('Environment & Training') {
                // On utilise le conteneur Python UNIQUEMENT pour l'entraînement
                agent {
                    docker {
                        image 'python:3.9-slim'
                        // Crucial pour ne pas perdre le code récupéré au Checkout
                        reuseNode true 
                    }
                }
                steps {
                    // C'est ici que vous perdez du temps (voir section optimisation plus bas)
                    sh 'pip install -r requirements.txt' 
                    sh 'python generate_data.py'
                    sh 'python train_model.py'
                }
            }

            stage('Docker Build & Push') {
                // Ce stage utilisera l'agent global 'any'
                steps {
                    script {
                        sh "docker build -t ${env.FULL_IMAGE_NAME} -t ${env.LATEST_IMAGE_NAME} ."
                        
                        withCredentials([usernamePassword(credentialsId: 'CRED_DOCK', passwordVariable: 'DOCKER_PASS', usernameVariable: 'DOCKER_USER')]) {
                            sh 'echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin'
                            sh "docker push ${env.FULL_IMAGE_NAME}"
                            sh "docker push ${env.LATEST_IMAGE_NAME}"
                        }
                    }
                }
            }
        }
        post {
            always {
                // Maintenant cleanWs() fonctionnera car il utilisera l'agent global 'any'
                cleanWs()
            }
        }
    }
}
