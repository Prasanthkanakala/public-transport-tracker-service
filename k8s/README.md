# Kubernetes Manifests – Public Transport Tracker

Production-ready Kubernetes manifests for deploying the Public Transport Tracker to **Google Kubernetes Engine (GKE)** with images published to **Google Artifact Registry (GAR)**.

## 📁 Directory Structure

```
k8s/
├── base/                          # Base manifests (shared across all environments)
│   ├── kustomization.yaml          # Kustomize base configuration
│   ├── namespace.yaml              # transport-tracker namespace
│   ├── service-account.yaml        # K8s ServiceAccount with Workload Identity
│   ├── backend-configmap.yaml      # Non-sensitive backend config
│   ├── backend-secret.yaml         # Sensitive API keys (base64 encoded)
│   ├── backend-deployment.yaml     # Spring Boot backend deployment
│   ├── backend-service.yaml        # Backend ClusterIP service
│   ├── backend-hpa.yaml            # Backend Horizontal Pod Autoscaler
│   ├── backend-pdb.yaml            # Backend PodDisruptionBudget
│   ├── frontend-nginx-configmap.yaml # Nginx config (proxies /api → backend)
│   ├── frontend-deployment.yaml    # React/Nginx frontend deployment
│   ├── frontend-service.yaml       # Frontend ClusterIP service
│   ├── frontend-hpa.yaml           # Frontend Horizontal Pod Autoscaler
│   ├── frontend-pdb.yaml           # Frontend PodDisruptionBudget
│   ├── ingress.yaml                # GKE Ingress (GCE controller)
│   ├── managed-certificate.yaml    # Google-managed SSL certificate
│   └── network-policy.yaml         # Network policies for pod isolation
├── overlays/
│   ├── dev/                        # Development environment overlay
│   │   └── kustomization.yaml      # Reduced replicas, lower resources
│   └── prod/                       # Production environment overlay
│       └── kustomization.yaml      # Full replicas, production images
└── README.md                       # This file
```

## 🚀 Quick Start

### Prerequisites

1. **GCP Project** with billing enabled
2. **GKE Cluster** created
3. **GAR Repository** created:
   ```bash
   gcloud artifacts repositories create transport-tracker \
     --repository-format=docker \
     --location=us-central1 \
     --description="Transport Tracker Docker images"
   ```
4. **Tools installed**: `gcloud`, `kubectl`, `kustomize`, `docker`

### Step 1: Configure GCP Authentication

```bash
# Authenticate with GCP
gcloud auth login
gcloud config set project YOUR_GCP_PROJECT_ID

# Get GKE credentials
gcloud container clusters get-credentials transport-tracker-cluster \
  --zone us-central1-a \
  --project YOUR_GCP_PROJECT_ID

# Configure Docker for GAR
gcloud auth configure-docker us-central1-docker.pkg.dev
```

### Step 2: Build & Push Docker Images

```bash
# Set variables
export PROJECT_ID=YOUR_GCP_PROJECT_ID
export REGION=us-central1
export REPO=transport-tracker
export TAG=v1.0.0

# Build and push backend
docker build -t ${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}/transport-backend:${TAG} ./backend
docker push ${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}/transport-backend:${TAG}

# Build and push frontend
docker build -t ${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}/transport-frontend:${TAG} ./frontend
docker push ${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}/transport-frontend:${TAG}
```

### Step 3: Update Configuration

1. **Replace placeholders** in `k8s/base/service-account.yaml`:
   - `YOUR_GCP_PROJECT_ID` → your actual GCP project ID

2. **Update secrets** in `k8s/base/backend-secret.yaml`:
   ```bash
   echo -n 'your-actual-api-key' | base64
   ```

3. **Update image references** in overlay kustomization files:
   - `k8s/overlays/dev/kustomization.yaml`
   - `k8s/overlays/prod/kustomization.yaml`

### Step 4: Deploy

```bash
# Deploy to dev
kustomize build k8s/overlays/dev | kubectl apply -f -

# Deploy to prod
kustomize build k8s/overlays/prod | kubectl apply -f -
```

### Step 5: Verify

```bash
kubectl get all -n transport-tracker
kubectl get ingress -n transport-tracker
```

## 🔐 Security Features

| Feature | Description |
|---------|-------------|
| **Workload Identity** | K8s SA bound to GCP SA for secure GAR access |
| **Non-root containers** | Backend runs as non-root user |
| **Network Policies** | Pod-to-pod traffic restricted |
| **Security Contexts** | `readOnlyRootFilesystem`, `drop ALL` capabilities |
| **Secrets Management** | Sensitive data in K8s Secrets (use External Secrets in prod) |
| **Managed SSL** | Google-managed TLS certificate |

## 📊 Observability

- **Health checks**: Liveness, readiness, and startup probes configured
- **Prometheus**: Backend exposes `/actuator/prometheus` metrics
- **HPA**: Auto-scaling based on CPU/memory utilization
- **PDB**: Pod disruption budgets ensure availability during upgrades

## 🌐 Demo DNS Setup (Share URL with Leads)

Need a quick shareable URL for demos? Use **nip.io** — a free wildcard DNS service that requires **no domain purchase** and works with **private GKE clusters**.

### Option A: Automated Setup (Recommended)

```bash
# Run the setup script — it does everything for you!
chmod +x k8s/setup-demo-dns.sh
./k8s/setup-demo-dns.sh
```

The script will:
1. Reserve a global static IP (`transport-tracker-ip`)
2. Generate a nip.io hostname (e.g., `transport-tracker.34.120.55.10.nip.io`)
3. Update `ingress.yaml` with the hostname
4. Deploy to GKE using the dev overlay
5. Print the shareable demo URL

### Option B: Manual Setup

```bash
# 1. Reserve a global static IP
gcloud compute addresses create transport-tracker-ip --global

# 2. Get the IP address
EXTERNAL_IP=$(gcloud compute addresses describe transport-tracker-ip --global --format='value(address)')
echo "Your IP: $EXTERNAL_IP"

# 3. Update ingress.yaml — replace EXTERNAL_IP placeholder
#    host: transport-tracker.EXTERNAL_IP.nip.io
#    becomes: transport-tracker.34.120.55.10.nip.io (example)
sed -i "s/EXTERNAL_IP/$EXTERNAL_IP/g" k8s/base/ingress.yaml

# 4. Deploy
kustomize build k8s/overlays/dev | kubectl apply -f -

# 5. Wait ~3-5 minutes for the Ingress to provision, then access:
#    http://transport-tracker.<YOUR_IP>.nip.io
```

### How nip.io Works

| What you type | Resolves to |
|---|---|
| `transport-tracker.34.120.55.10.nip.io` | `34.120.55.10` |
| `anything.1.2.3.4.nip.io` | `1.2.3.4` |

> **Why this works with private nodes:** The GKE Ingress controller creates a GCP-managed external HTTP(S) Load Balancer. This load balancer has a public IP and routes traffic to your private nodes via their internal IPs. Your nodes never need external IPs!

### Shareable URLs

Once deployed, share these with your leads:
- **🌐 App:** `http://transport-tracker.<IP>.nip.io`
- **🔌 API:** `http://transport-tracker.<IP>.nip.io/api`
- **📊 Health:** `http://transport-tracker.<IP>.nip.io/actuator/health`

---

## 🔄 CI/CD with Jenkins

The `Jenkinsfile` at the project root automates:
1. Code checkout from GitHub
2. Backend tests (Gradle) and frontend tests (npm)
3. Docker image builds with commit-based tags
4. Push to Google Artifact Registry
5. Deploy to GKE using Kustomize overlays
6. Rollout verification

**Branch mapping:**
- `develop` → deploys to **dev** environment
- `main` → deploys to **prod** environment
