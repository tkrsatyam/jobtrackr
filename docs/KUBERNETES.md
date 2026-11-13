# JobTrackr Kubernetes Deployment & Orchestration Guide

This guide describes how to deploy, manage, and verify the JobTrackr microservices architecture on Kubernetes using declarative manifests and Kustomize overlays.

---

## 1. Architecture & Directory Layout

The Kubernetes configuration is located under `deploy/k8s/` and structured into a reusable **base** and environment-specific **overlays**:

```
deploy/k8s/
├── base/
│   ├── kustomization.yaml            # Base resource aggregator
│   ├── configmap.yaml                # Shared environment properties & service discovery URLs
│   ├── secret-template.yaml          # Documented secret schema template
│   ├── secrets.yaml                  # Safe default local secrets
│   ├── postgres.yaml                 # PostgreSQL deployment, PVC, and database init ConfigMap
│   ├── redis.yaml                    # In-cluster Redis deployment and ClusterIP service
│   ├── kafka.yaml                    # Apache Kafka single-node broker in KRaft mode
│   ├── api-gateway.yaml              # Spring Cloud Gateway deployment & ClusterIP service (8080)
│   ├── user-service.yaml             # User Service deployment & ClusterIP service (8081)
│   ├── application-service.yaml      # Application Service deployment & ClusterIP service (8082)
│   └── email-ingestion-service.yaml  # Email Ingestion Service deployment & ClusterIP service (8088)
└── overlays/
    ├── local/
    │   └── kustomization.yaml        # Local overlay: namespace 'jobtrackr-local', IfNotPresent image policy
    └── prod/
        └── kustomization.yaml        # Production overlay: namespace 'jobtrackr', Docker Hub images, scaled replicas
```

### Key Components

- **In-Cluster Infrastructure**:
  - **PostgreSQL**: 1Gi PVC with automated database provisioning (`jobtrackr_users`, `jobtrackr_applications`, `jobtrackr_ingestion`).
  - **Redis**: In-memory caching and token blacklist (`redis:7-alpine`).
  - **Apache Kafka**: KRaft mode (controller/broker combo) with zero ZooKeeper dependency (`apache/kafka:latest`).
- **Core Microservices**:
  - `api-gateway` (Port `8080`): Entry point, routing, and JWT token authentication.
  - `user-service` (Port `8081`): Authentication, user profiles, and OAuth integration.
  - `application-service` (Port `8082`): Job application pipeline, tracking, and status transitions.
  - `email-ingestion-service` (Port `8088`): Automated email synchronization and Gemini AI parsing.
- **Observability Probes**:
  - Each microservice is configured with Spring Boot Actuator health probes:
    - Liveness: `GET /actuator/health/liveness`
    - Readiness: `GET /actuator/health/readiness`
  - Prometheus scrape endpoint exposed at `GET /actuator/prometheus`.

---

## 2. Prerequisites

Ensure the following tools are installed and available on your PATH:
- **Kubernetes CLI**: `kubectl` (v1.28 or later, includes Kustomize v5).
- **Local Cluster**: Either [Minikube](https://minikube.sigs.k8s.io/) or [Kind](https://kind.sigs.k8s.io/) or Docker Desktop Kubernetes.
- **Docker**: For building local container images.

---

## 3. Local Cluster Quickstart

### Step 1: Start your local cluster

For Minikube:
```bash
minikube start --cpus=4 --memory=6144 --disk-size=20g
```

For Kind:
```bash
kind create cluster --name jobtrackr
```

### Step 2: Build local Docker images

If using Minikube, configure your shell to build directly into Minikube's Docker daemon:
```bash
# Windows PowerShell
& minikube -p minikube docker-env --shell powershell | Invoke-Expression

# Linux / macOS
eval $(minikube docker-env)
```

Build the container images for all four core services:
```bash
docker build -t jobtrackr-api-gateway:latest ./services/api-gateway
docker build -t jobtrackr-user-service:latest ./services/user-service
docker build -t jobtrackr-application-service:latest ./services/application-service
docker build -t jobtrackr-email-ingestion-service:latest ./services/email-ingestion-service
```

### Step 3: Create the local namespace

```bash
kubectl create namespace jobtrackr-local
```

### Step 4: Apply the Local Kustomize Overlay

Deploy the entire JobTrackr stack with a single command:
```bash
kubectl apply -k deploy/k8s/overlays/local
```

---

## 4. Verification & Health Probes

### Check Pod Status
```bash
kubectl get pods -n jobtrackr-local -w
```

Example expected output:
```
NAME                                       READY   STATUS    RESTARTS   AGE
api-gateway-7489f66874-abcde               1/1     Running   0          45s
application-service-6d86b774b-fghij        1/1     Running   0          45s
email-ingestion-service-58dc788947-klmno   1/1     Running   0          45s
kafka-5df7bb8bf7-pqrst                     1/1     Running   0          60s
postgres-5c4bbd7c6f-uvwxy                  1/1     Running   0          60s
redis-76d8b99d65-zabcd                     1/1     Running   0          60s
user-service-6598c8c697-efghi              1/1     Running   0          45s
```

### Inspect Health Probes

Inspect any running service pod to confirm that liveness and readiness probes are succeeding:
```bash
kubectl describe pod -l app=api-gateway -n jobtrackr-local
```

You should see:
```
Liveness:   http-get http://:8080/actuator/health/liveness delay=30s timeout=3s period=15s #success=1 #failure=3
Readiness:  http-get http://:8080/actuator/health/readiness delay=20s timeout=3s period=10s #success=1 #failure=3
```

---

## 5. Accessing Services via Port Forwarding

Forward the API Gateway port to your host machine:
```bash
kubectl port-forward -n jobtrackr-local svc/api-gateway 8080:8080
```

Now you can test endpoints from your local browser or terminal:
- **Actuator Health**: `curl http://localhost:8080/actuator/health`
- **Prometheus Metrics**: `curl http://localhost:8080/actuator/prometheus`
- **API Endpoints**: Routed downstream to `user-service`, `application-service`, etc.

To port-forward individual downstream services directly:
```bash
kubectl port-forward -n jobtrackr-local svc/user-service 8081:8081
kubectl port-forward -n jobtrackr-local svc/application-service 8082:8082
kubectl port-forward -n jobtrackr-local svc/email-ingestion-service 8088:8088
```

---

## 6. Secrets Management for Production

The `deploy/k8s/base/secrets.yaml` file contains safe development placeholders. For staging or production:

1. Refer to `deploy/k8s/base/secret-template.yaml`.
2. Generate the secret imperatively or via a GitOps secret manager (e.g., Sealed Secrets, Vault, or External Secrets Operator):
   ```bash
   kubectl create secret generic jobtrackr-secrets \
     --from-literal=JWT_SECRET="<prod-jwt-secret>" \
     --from-literal=GOOGLE_CLIENT_ID="<client-id>" \
     --from-literal=GOOGLE_CLIENT_SECRET="<client-secret>" \
     --from-literal=ENCRYPTION_KEY="<32-char-encryption-key>" \
     --from-literal=GEMINI_API_KEY="<gemini-api-key>" \
     --from-literal=POSTGRES_USER="<db-user>" \
     --from-literal=POSTGRES_PASSWORD="<db-password>" \
     -n jobtrackr
   ```

To deploy to production:
```bash
kubectl create namespace jobtrackr
kubectl apply -k deploy/k8s/overlays/prod
```

---

## 7. Teardown & Cleanup

To destroy all deployed resources:
```bash
kubectl delete -k deploy/k8s/overlays/local
```

To delete the namespace and persistent volumes:
```bash
kubectl delete namespace jobtrackr-local
```
