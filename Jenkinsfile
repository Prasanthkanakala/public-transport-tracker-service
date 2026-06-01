/**
 * Jenkins Pipeline – Public Transport Tracker
 *
 * CI/CD Pipeline for GKE Deployment with Google Artifact Registry (GAR)
 *
 * Prerequisites:
 *   - Jenkins credentials:
 *       'gcp-service-account-key'  → GCP SA JSON key (for GAR push & GKE deploy)
 *       'gke-kubeconfig'           → (optional) kubeconfig for GKE cluster
 *   - Jenkins tools:
 *       'JDK-17'                   → JDK 17 installation
 *   - GCloud SDK installed on Jenkins agent (or use google/cloud-sdk Docker image)
 *   - kubectl installed on Jenkins agent
 *   - kustomize installed on Jenkins agent (or use kubectl -k)
 *
 * Stages:
 *   1. Checkout
 *   2. Backend – Test & Build
 *   3. Frontend – Test & Build
 *   4. Docker – Build Images
 *   5. Docker – Push to GAR
 *   6. Deploy to GKE (develop → dev, main → prod)
 *
 * Environment Variables to configure:
 *   GCP_PROJECT_ID   → Your GCP project ID
 *   GAR_REGION       → GAR region (e.g., us-central1)
 *   GAR_REPOSITORY   → GAR Docker repository name
 *   GKE_CLUSTER      → GKE cluster name
 *   GKE_ZONE         → GKE cluster zone/region
 */
pipeline {
    agent any

    environment {
        // ── Java ────────────────────────────────────────────────────────
        // NOTE: JAVA_HOME is set inside stages that need it, using the
        //       'tools' directive, to avoid pipeline failure when the
        //       JDK tool installation is not yet configured in Jenkins.
        //       Configure a JDK installation named 'JDK-17' in
        //       Manage Jenkins → Tools → JDK installations, then
        //       uncomment the tools block in the relevant stages.
        // JAVA_HOME        = tool 'JDK-17'
        // PATH             = "${JAVA_HOME}/bin:${env.PATH}"

        // ── GCP / GAR Configuration ────────────────────────────────────
        GCP_PROJECT_ID      = 'burner-kanprasa1-01'                    // TODO: Replace with your GCP project ID
        GAR_REGION          = 'us-central1'                             // TODO: Replace with your GAR region
        GAR_REPOSITORY      = 'transport-tracker'                       // GAR Docker repository name
        GAR_REGISTRY        = "${GAR_REGION}-docker.pkg.dev/${GCP_PROJECT_ID}/${GAR_REPOSITORY}"

        // ── Image names ────────────────────────────────────────────────
        BACKEND_IMAGE       = "${GAR_REGISTRY}/transport-backend"
        FRONTEND_IMAGE      = "${GAR_REGISTRY}/transport-frontend"

        // ── GKE Configuration ──────────────────────────────────────────
        GKE_CLUSTER         = 'transport-tracker-cluster'               // TODO: Replace with your GKE cluster name
        GKE_ZONE            = 'us-central1-a'                           // TODO: Replace with your GKE zone/region

        // ── Credentials ────────────────────────────────────────────────
        GCP_SA_KEY          = credentials('gcp-service-account-key')    // GCP Service Account JSON key

        // ── GitHub Repo ────────────────────────────────────────────────
        GITHUB_REPO         = 'https://github.com/Prasanthkanakala/public-transport-tracker-service.git'
    }

    options {
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '15'))
        // timestamps()    – Requires 'Timestamper' plugin (not installed)
        // ansiColor('xterm') – Requires 'AnsiColor' plugin (not installed)
    }
    
    tools {
        gradle 'Gradle-8.14'
    }
    
    stages {

        // ════════════════════════════════════════════════════════════════
        //  Stage 1: Checkout
        // ════════════════════════════════════════════════════════════════
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_COMMIT_SHORT = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.BUILD_TAG_CUSTOM = "${env.BRANCH_NAME}-${env.GIT_COMMIT_SHORT}-${env.BUILD_NUMBER}"
                    // Determine target environment
                    env.DEPLOY_ENV = (env.BRANCH_NAME == 'main') ? 'prod' : 'dev'
                }
                echo "\u001B[32m✔ Building commit: ${env.BUILD_TAG_CUSTOM} → Environment: ${env.DEPLOY_ENV}\u001B[0m"
            }
        }

        // ════════════════════════════════════════════════════════════════
        //  Stage 2: Backend – Test & Build
        // ════════════════════════════════════════════════════════════════
        stage('Backend – Test & Build') {
            steps {
                dir('backend') {
                    sh '''
                        chmod +x gradlew 2>/dev/null || true
                        gradle clean test bootJar --no-daemon --info
                    '''
                }
            }
            post {
                always {
                    // junit requires the 'JUnit' plugin (not installed)
                    // Using archiveArtifacts as a fallback to preserve test results
                    archiveArtifacts artifacts: 'backend/build/test-results/**/*.xml', allowEmptyArchive: true
                }
                success {
                    echo '\u001B[32m✔ Backend tests passed and JAR built successfully\u001B[0m'
                }
            }
        }

        // ════════════════════════════════════════════════════════════════
        //  Stage 3: Frontend – Test & Build
        // ════════════════════════════════════════════════════════════════
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
                        npm test -- --watchAll=false --coverage --passWithNoTests
                        npm run build
                    '''
                }
            }
            post {
                success {
                    echo '\u001B[32m✔ Frontend tests passed and build completed\u001B[0m'
                }
            }
        }

        // ════════════════════════════════════════════════════════════════
        //  Stage 4: Authenticate with GCP & GAR
        // ════════════════════════════════════════════════════════════════
        stage('GCP – Authenticate') {
            steps {
                script {
                    sh """
                        # Authenticate with GCP using service account key
                        gcloud auth activate-service-account --key-file=\${GCP_SA_KEY}
                        gcloud config set project ${GCP_PROJECT_ID}

                        # Configure Docker to authenticate with GAR
                        gcloud auth configure-docker ${GAR_REGION}-docker.pkg.dev --quiet
                    """
                }
                echo '\u001B[32m✔ Authenticated with GCP and configured Docker for GAR\u001B[0m'
            }
        }

        // ════════════════════════════════════════════════════════════════
        //  Stage 5: Docker – Build Images
        // ════════════════════════════════════════════════════════════════
        stage('Docker – Build Images') {
            steps {
                script {
                    echo "Building Docker images with tag: ${BUILD_TAG_CUSTOM}"

                    // Build backend image
                    sh """
                        docker build \\
                            --label "git.commit=${GIT_COMMIT_SHORT}" \\
                            --label "build.number=${BUILD_NUMBER}" \\
                            --label "build.branch=${BRANCH_NAME}" \\
                            -t ${BACKEND_IMAGE}:${BUILD_TAG_CUSTOM} \\
                            -t ${BACKEND_IMAGE}:${DEPLOY_ENV}-latest \\
                            ./backend
                    """

                    // Build frontend image
                    sh """
                        docker build \\
                            --label "git.commit=${GIT_COMMIT_SHORT}" \\
                            --label "build.number=${BUILD_NUMBER}" \\
                            --label "build.branch=${BRANCH_NAME}" \\
                            -t ${FRONTEND_IMAGE}:${BUILD_TAG_CUSTOM} \\
                            -t ${FRONTEND_IMAGE}:${DEPLOY_ENV}-latest \\
                            ./frontend
                    """
                }
                echo '\u001B[32m✔ Docker images built successfully\u001B[0m'
            }
        }

        // ════════════════════════════════════════════════════════════════
        //  Stage 6: Docker – Push to Google Artifact Registry (GAR)
        // ════════════════════════════════════════════════════════════════
        stage('Docker – Push to GAR') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                }
            }
            steps {
                script {
                    echo "Pushing images to GAR: ${GAR_REGISTRY}"

                    // Push backend images
                    sh """
                        docker push ${BACKEND_IMAGE}:${BUILD_TAG_CUSTOM}
                        docker push ${BACKEND_IMAGE}:${DEPLOY_ENV}-latest
                    """

                    // Push frontend images
                    sh """
                        docker push ${FRONTEND_IMAGE}:${BUILD_TAG_CUSTOM}
                        docker push ${FRONTEND_IMAGE}:${DEPLOY_ENV}-latest
                    """
                }
                echo '\u001B[32m✔ Images pushed to Google Artifact Registry\u001B[0m'
            }
        }

        // ════════════════════════════════════════════════════════════════
        //  Stage 7: Deploy to GKE
        // ════════════════════════════════════════════════════════════════
        stage('Deploy to GKE') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                }
            }
            steps {
                script {
                    echo "Deploying to GKE (${DEPLOY_ENV}) – Cluster: ${GKE_CLUSTER}"

                    // Get GKE credentials
                    sh """
                        gcloud container clusters get-credentials ${GKE_CLUSTER} \\
                            --zone ${GKE_ZONE} \\
                            --project ${GCP_PROJECT_ID}
                    """

                    // Deploy using Kustomize with image override
                    sh """
                        # Set the correct image tags using kustomize
                        cd k8s/overlays/${DEPLOY_ENV}

                        # Override images with the current build tag
                        kustomize edit set image \\
                            IMAGE_PLACEHOLDER=${BACKEND_IMAGE}:${BUILD_TAG_CUSTOM}

                        # Apply the manifests
                        kustomize build . | kubectl apply -f -

                        # Also update the frontend image in the deployment directly
                        kubectl set image deployment/transport-frontend \\
                            frontend=${FRONTEND_IMAGE}:${BUILD_TAG_CUSTOM} \\
                            -n transport-tracker || true
                    """

                    // Wait for rollout to complete
                    sh """
                        echo "Waiting for backend rollout..."
                        kubectl rollout status deployment/transport-backend \\
                            -n transport-tracker --timeout=300s

                        echo "Waiting for frontend rollout..."
                        kubectl rollout status deployment/transport-frontend \\
                            -n transport-tracker --timeout=300s
                    """
                }
                echo "\u001B[32m✔ Successfully deployed ${BUILD_TAG_CUSTOM} to GKE (${DEPLOY_ENV})\u001B[0m"
            }
        }

        // ════════════════════════════════════════════════════════════════
        //  Stage 8: Verify Deployment
        // ════════════════════════════════════════════════════════════════
        stage('Verify Deployment') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                }
            }
            steps {
                script {
                    sh """
                        echo "=== Deployment Status ==="
                        kubectl get deployments -n transport-tracker -o wide

                        echo ""
                        echo "=== Pod Status ==="
                        kubectl get pods -n transport-tracker -o wide

                        echo ""
                        echo "=== Services ==="
                        kubectl get svc -n transport-tracker

                        echo ""
                        echo "=== Ingress ==="
                        kubectl get ingress -n transport-tracker

                        echo ""
                        echo "=== HPA Status ==="
                        kubectl get hpa -n transport-tracker
                    """
                }
            }
        }
    }

    post {
        always {
            // Wrap in node block to ensure workspace context is available
            // even when the pipeline fails before reaching a stage with an agent
            node('') {
                // Clean up Docker images to save disk space
                sh '''
                    docker image prune -f 2>/dev/null || true
                '''
                // cleanWs() requires the 'Workspace Cleanup' plugin (not installed)
                // Using deleteDir() as a built-in alternative to clean the workspace
                deleteDir()
            }
        }
        success {
            echo "\u001B[32m══════════════════════════════════════════════════\u001B[0m"
            echo "\u001B[32m  ✔ Pipeline SUCCEEDED for ${env.BUILD_TAG_CUSTOM}\u001B[0m"
            echo "\u001B[32m  Environment: ${env.DEPLOY_ENV}\u001B[0m"
            echo "\u001B[32m══════════════════════════════════════════════════\u001B[0m"
        }
        failure {
            echo "\u001B[31m══════════════════════════════════════════════════\u001B[0m"
            echo "\u001B[31m  ✘ Pipeline FAILED for ${env.BUILD_TAG_CUSTOM}\u001B[0m"
            echo "\u001B[31m  Environment: ${env.DEPLOY_ENV}\u001B[0m"
            echo "\u001B[31m══════════════════════════════════════════════════\u001B[0m"
            // Uncomment to enable Slack notifications
            // slackSend channel: '#deployments',
            //     color: 'danger',
            //     message: "FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER} (${env.BUILD_TAG_CUSTOM})"
        }
    }
}