/**
* Jenkins Pipeline – Public Transport Tracker
*
* Stages:
*   1. Checkout
*   2. Backend Test
*   3. Frontend Test
*   4. Docker Build
*   5. Docker Push (main branch only)
*   6. Deploy (main branch only)
*/
pipeline {
    agent any

    environment {
        JAVA_HOME        = tool 'JDK-17'
        PATH             = "${JAVA_HOME}/bin:${env.PATH}"
        DOCKER_REGISTRY  = 'registry.example.com'
        BACKEND_IMAGE    = "${DOCKER_REGISTRY}/transport-backend"
        FRONTEND_IMAGE   = "${DOCKER_REGISTRY}/transport-frontend"
        DEPLOY_HOST      = credentials('deploy-host')
        DOCKER_CREDS     = credentials('docker-registry-creds')
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_COMMIT_SHORT = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.BUILD_TAG = "${env.BRANCH_NAME}-${env.GIT_COMMIT_SHORT}"
                }
                echo "Building commit: ${env.BUILD_TAG}"
            }
        }

        stage('Backend – Test & Build') {
            steps {
                dir('backend') {
                    sh '''
                        chmod +x gradlew 2>/dev/null || true
                        gradle clean test jacocoTestReport bootJar --no-daemon
                    '''
                }
            }
            post {
                always {
                    junit 'backend/build/test-results/**/*.xml'
                    publishHTML([
                        allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: false,
                        reportDir: 'backend/build/reports/jacoco/html',
                        reportFiles: 'index.html',
                        reportName: 'Coverage Report'
                    ])
                }
            }
        }

        stage('Frontend – Test & Build') {
            agent {
                docker {
                    image 'node:20-alpine'
                    reuseNode true
                }
            }
            steps {
                dir('frontend') {
                    sh '''
                        npm ci
                        npm test -- --watchAll=false --coverage
                        npm run build
                    '''
                }
            }
        }

        stage('Docker – Build Images') {
            steps {
                script {
                    backendImage  = docker.build("${BACKEND_IMAGE}:${BUILD_TAG}",  "./backend")
                    frontendImage = docker.build("${FRONTEND_IMAGE}:${BUILD_TAG}", "./frontend")
                }
            }
        }

        stage('Docker – Push Images') {
            when {
                branch 'main'
            }
            steps {
                script {
                    docker.withRegistry("https://${DOCKER_REGISTRY}", DOCKER_CREDS) {
                        backendImage.push("${BUILD_TAG}")
                        backendImage.push("latest")
                        frontendImage.push("${BUILD_TAG}")
                        frontendImage.push("latest")
                    }
                }
            }
        }

        stage('Deploy to Production') {
            when {
                branch 'main'
            }
            steps {
                echo "Deploying ${BUILD_TAG} to ${DEPLOY_HOST}…"
                sshagent(['deploy-ssh-key']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no deploy@${DEPLOY_HOST} \\
                          'cd /opt/transport-tracker && \\
                           docker-compose pull && \\
                           docker-compose up -d --remove-orphans && \\
                           docker image prune -f'
                    """
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }
        success {
            echo "Pipeline succeeded for ${env.BUILD_TAG}"
        }
        failure {
            echo "Pipeline FAILED for ${env.BUILD_TAG}"
        }
    }
}