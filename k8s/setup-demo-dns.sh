#!/bin/bash
# ─────────────────────────────────────────────────────────────────
# 🚀 Demo DNS Setup Script for Public Transport Tracker
# Creates a shareable URL using nip.io (FREE, no domain needed)
# Works with private GKE clusters!
# ─────────────────────────────────────────────────────────────────

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}  🚌 Public Transport Tracker – Demo DNS Setup${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
echo ""

# ─────────────────────────────────────────────────────────────────
# Step 1: Reserve a Global Static IP
# ─────────────────────────────────────────────────────────────────
echo -e "${YELLOW}Step 1: Reserving a global static IP address...${NC}"

if gcloud compute addresses describe transport-tracker-ip --global &>/dev/null; then
  echo -e "${GREEN}✅ Static IP 'transport-tracker-ip' already exists.${NC}"
else
  gcloud compute addresses create transport-tracker-ip --global
  echo -e "${GREEN}✅ Static IP 'transport-tracker-ip' created.${NC}"
fi

# Get the IP address
EXTERNAL_IP=$(gcloud compute addresses describe transport-tracker-ip --global --format='value(address)')
echo -e "${GREEN}   External IP: ${EXTERNAL_IP}${NC}"
echo ""

# ─────────────────────────────────────────────────────────────────
# Step 2: Generate the nip.io demo URL
# ─────────────────────────────────────────────────────────────────
DEMO_HOST="transport-tracker.${EXTERNAL_IP}.nip.io"
DEMO_URL="http://${DEMO_HOST}"

echo -e "${YELLOW}Step 2: Your demo DNS hostname:${NC}"
echo -e "${GREEN}   🌐 ${DEMO_HOST}${NC}"
echo ""

# ─────────────────────────────────────────────────────────────────
# Step 3: Update the Ingress manifest with the actual IP
# ─────────────────────────────────────────────────────────────────
echo -e "${YELLOW}Step 3: Updating ingress.yaml with the demo hostname...${NC}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INGRESS_FILE="${SCRIPT_DIR}/base/ingress.yaml"

if [ -f "${INGRESS_FILE}" ]; then
  # Replace the placeholder with actual nip.io hostname
  sed -i.bak "s/transport-tracker\.EXTERNAL_IP\.nip\.io/${DEMO_HOST}/g" "${INGRESS_FILE}"
  rm -f "${INGRESS_FILE}.bak"
  echo -e "${GREEN}✅ ingress.yaml updated with hostname: ${DEMO_HOST}${NC}"
else
  echo -e "${RED}❌ ingress.yaml not found at ${INGRESS_FILE}${NC}"
  echo -e "${YELLOW}   Please manually update the host in your ingress.yaml to: ${DEMO_HOST}${NC}"
fi
echo ""

# ─────────────────────────────────────────────────────────────────
# Step 4: Apply the Ingress
# ─────────────────────────────────────────────────────────────────
echo -e "${YELLOW}Step 4: Applying Kubernetes manifests...${NC}"

if kubectl get namespace transport-tracker &>/dev/null; then
  echo -e "${GREEN}   Namespace 'transport-tracker' exists.${NC}"
else
  echo -e "${YELLOW}   Creating namespace 'transport-tracker'...${NC}"
  kubectl apply -f "${SCRIPT_DIR}/base/namespace.yaml"
fi

# Apply using kustomize (dev overlay for demo)
echo -e "${YELLOW}   Deploying with kustomize (dev overlay)...${NC}"
kustomize build "${SCRIPT_DIR}/overlays/dev" | kubectl apply -f -
echo -e "${GREEN}✅ Manifests applied successfully.${NC}"
echo ""

# ─────────────────────────────────────────────────────────────────
# Step 5: Wait for Ingress to get the external IP
# ─────────────────────────────────────────────────────────────────
echo -e "${YELLOW}Step 5: Waiting for Ingress to be provisioned (this may take 3-5 minutes)...${NC}"

for i in $(seq 1 30); do
  INGRESS_IP=$(kubectl get ingress transport-tracker-ingress -n transport-tracker -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)
  if [ -n "${INGRESS_IP}" ]; then
    echo -e "${GREEN}✅ Ingress is ready! IP: ${INGRESS_IP}${NC}"
    break
  fi
  echo -e "   ⏳ Waiting... (${i}/30)"
  sleep 10
done
echo ""

# ─────────────────────────────────────────────────────────────────
# Step 6: Print the shareable demo URL
# ─────────────────────────────────────────────────────────────────
echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}  🎉 DEMO READY! Share this URL with your leads:${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${GREEN}  🌐 Frontend:  ${DEMO_URL}${NC}"
echo -e "${GREEN}  🔌 API:       ${DEMO_URL}/api${NC}"
echo -e "${GREEN}  📊 Health:    ${DEMO_URL}/actuator/health${NC}"
echo ""
echo -e "${YELLOW}  📝 How nip.io works:${NC}"
echo -e "     nip.io is a free wildcard DNS service.${NC}"
echo -e "     It resolves 'anything.${EXTERNAL_IP}.nip.io' → ${EXTERNAL_IP}${NC}"
echo -e "     No domain purchase or DNS configuration needed!${NC}"
echo ""
echo -e "${YELLOW}  ⚡ Quick verification:${NC}"
echo -e "     curl ${DEMO_URL}${NC}"
echo -e "     curl ${DEMO_URL}/api/actuator/health${NC}"
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
