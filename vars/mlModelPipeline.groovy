def call(Map config = [:]) {
    def dockerRegistry = "saaymo"
    def appName = config.appName
    // Paramètres Hugging Face
    def hfRepo = config.hfRepo
    def modelFiles = config.modelFiles ?: [] // Liste de maps : [[name: 'f.pkl', dir: 'path'], ...]

    pipeline {
        agent any 

        stages {
            stage('Checkout & Init') {
                steps {
                    checkout scm
                    script {
                        env.IMAGE_TAG = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                        env.FULL_IMAGE_NAME = "${dockerRegistry}/${appName}:${env.IMAGE_TAG}"
                        env.LATEST_IMAGE_NAME = "${dockerRegistry}/${appName}:latest"
                    }
                }
            }

            stage('Download Models from Hugging Face') {

                    agent {
                        docker {
                            image 'python:3.9-slim'
                            // Crucial pour ne pas perdre le code récupéré au Checkout
                            reuseNode true 
                        }
                    }

                
                steps {
                    // Utilisation du token Hugging Face configuré dans Jenkins
                    withCredentials([string(credentialsId: 'Hug-Face', variable: 'TOKEN')]) {
                        script {
                            sh "pip install --user huggingface_hub"
                            
                            modelFiles.each { file ->
                                echo "Téléchargement de ${file.name} vers ${file.targetDir}..."
                                // Création du dossier cible s'il n'existe pas
                                sh "mkdir -p ${file.targetDir}"
                                
                                // Commande Python pour télécharger le fichier spécifique
                                sh """
                                python3 -c "from huggingface_hub import hf_hub_download; \
                                hf_hub_download(repo_id='${hfRepo}', \
                                filename='${file.name}', \
                                token='${TOKEN}', \
                                local_dir='${file.targetDir}', \
                                local_dir_use_symlinks=False)"
                                """
                            }
                        }
                    }
                }
            }

            stage('Docker Build & Push') {
                steps {
                    script {
                        // Le Dockerfile fera un COPY . . et récupérera les fichiers téléchargés
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
                cleanWs()
            }
        }
    }
}
