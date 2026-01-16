def call(Map config = [:]) {
    def dockerRegistry = config.registry ?: "saaymo"
    def appName = config.appName
    def sonarProjectKey = config.sonarProjectKey ?: appName // si non fourni, on utilise appName

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

            // stage('SonarQube Analysis') {
            //     steps {
            //         script {
            //             // Appel de la shared library Sonar avec le projectKey spécifique
            //             runSonarAnalysis(sonarProjectKey, 'maven')
            //         }
            //     }
            // }

            // stage('Quality Gate') {
            //     steps {
            //         catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
            //             timeout(time: 2, unit: 'MINUTES') {
            //                 waitForQualityGate abortPipeline: true
            //             }
            //         }
            //     }
            // }

            stage('Docker Build') {
                steps {
                    script {
                        sh "docker build -t ${env.FULL_IMAGE_NAME} -t ${env.LATEST_IMAGE_NAME} ."
                    }
                }
            }

            stage('Docker Push') {
                steps {
                    script {
                        withCredentials([usernamePassword(credentialsId: 'DOCKER_HUB', passwordVariable: 'DOCKER_PASS', usernameVariable: 'DOCKER_USER')]) {
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
