# Runbook: [Operation Name]

## Overview

Brief description of this runbook's purpose and when it should be used.

## Audience

| Role | Responsibilities |
|------|------------------|
| Developer | Execute during development |
| DevOps | Execute in staging/production |
| On-Call Engineer | Execute during incidents |

## Preconditions

### System Requirements

- [ ] Kubernetes cluster is accessible
- [ ] kubectl configured with correct context
- [ ] Helm 3.x installed
- [ ] Terraform 1.x installed
- [ ] Required secrets are available

### Access Requirements

- [ ] Kubernetes RBAC permissions for target namespace
- [ ] Database credentials available
- [ ] Monitoring system access

### Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| KUBECONFIG | Yes | Path to kubeconfig file |
| NAMESPACE | Yes | Target Kubernetes namespace |
| ENV | Yes | Environment (dev/staging/prod) |

## Procedure

### Step 1: [Step Name]

**Purpose:** What this step accomplishes

**Commands:**

```bash
# Command with explanation
kubectl get pods -n $NAMESPACE
```

**Expected Output:**

```
NAME                        READY   STATUS    RESTARTS   AGE
backend-xxx-yyy            1/1     Running   0          1d
```

**Verification:**

- [ ] All pods show READY 1/1
- [ ] No pods in CrashLoopBackOff

### Step 2: [Step Name]

**Purpose:** What this step accomplishes

**Commands:**

```bash
# Next command
helm upgrade --install ...
```

**Expected Output:**

```
Release "app" has been upgraded. Happy Helming!
```

**Verification:**

- [ ] Helm release shows deployed status

### Step 3: Post-Deployment Verification

**Health Checks:**

```bash
# Check application health
curl -s https://app.local/healthz | jq .

# Expected response
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "redis": {"status": "UP"},
    "rabbitmq": {"status": "UP"},
    "temporal": {"status": "UP"}
  }
}
```

**Smoke Tests:**

```bash
# Run smoke test suite
./scripts/smoke-test.sh $ENV
```

## Rollback Procedure

### When to Rollback

- Health checks fail after deployment
- Error rate exceeds threshold (>1%)
- P1 incidents reported within 30 minutes of deployment

### Rollback Steps

#### Step R1: Rollback Helm Release

```bash
# List release history
helm history app -n $NAMESPACE

# Rollback to previous version
helm rollback app [REVISION] -n $NAMESPACE
```

#### Step R2: Verify Rollback

```bash
# Verify pods are running previous version
kubectl get pods -n $NAMESPACE -o wide

# Check health
curl -s https://app.local/healthz
```

#### Step R3: Notify Stakeholders

- [ ] Post in #incidents channel
- [ ] Update incident ticket
- [ ] Notify on-call if escalation needed

## Backup and Restore

### Database Backup

```bash
# Create backup
kubectl exec -n $NAMESPACE deploy/postgres -- \
  pg_dump -U app -d homewarehouse > backup-$(date +%Y%m%d-%H%M%S).sql

# Verify backup
ls -la backup-*.sql
```

### Database Restore

```bash
# Restore from backup (DESTRUCTIVE - requires approval)
kubectl exec -i -n $NAMESPACE deploy/postgres -- \
  psql -U app -d homewarehouse < backup-YYYYMMDD-HHMMSS.sql
```

### Redis Backup

```bash
# Trigger RDB snapshot
kubectl exec -n $NAMESPACE deploy/redis -- redis-cli BGSAVE

# Copy RDB file
kubectl cp $NAMESPACE/redis-pod:/data/dump.rdb ./redis-backup.rdb
```

## Troubleshooting

### Issue 1: Pods Not Starting

**Symptoms:**
- Pods in Pending or CrashLoopBackOff state
- Events show image pull errors or resource constraints

**Diagnosis:**

```bash
# Check pod events
kubectl describe pod $POD_NAME -n $NAMESPACE

# Check logs
kubectl logs $POD_NAME -n $NAMESPACE --previous
```

**Resolution:**

1. If image pull error: Verify image exists and credentials are correct
2. If resource constraints: Scale down other workloads or add node capacity
3. If crash loop: Check application logs for startup errors

### Issue 2: Database Connection Failures

**Symptoms:**
- Health check shows db: DOWN
- Application logs show connection refused/timeout

**Diagnosis:**

```bash
# Check PostgreSQL pod
kubectl get pods -n $NAMESPACE -l app=postgres

# Check PostgreSQL logs
kubectl logs -n $NAMESPACE deploy/postgres --tail=100

# Test connection from backend pod
kubectl exec -n $NAMESPACE deploy/backend -- \
  pg_isready -h postgres -p 5432
```

**Resolution:**

1. If PostgreSQL pod is down: Restart the pod
2. If connection refused: Check network policies and service configuration
3. If credentials error: Verify secrets are correctly mounted

### Issue 3: High Memory Usage

**Symptoms:**
- OOMKilled events
- Slow response times
- Memory metrics approaching limits

**Diagnosis:**

```bash
# Check resource usage
kubectl top pods -n $NAMESPACE

# Check for OOMKilled
kubectl get events -n $NAMESPACE --field-selector reason=OOMKilled
```

**Resolution:**

1. Increase memory limits in Helm values
2. Investigate memory leaks via profiling
3. Scale horizontally if single-pod limit is reached

### Issue 4: Message Queue Backlog

**Symptoms:**
- Messages accumulating in RabbitMQ
- Delayed processing of events
- Consumer lag alerts

**Diagnosis:**

```bash
# Check queue depths
kubectl exec -n $NAMESPACE deploy/rabbitmq -- \
  rabbitmqctl list_queues name messages consumers

# Check consumer health
kubectl logs -n $NAMESPACE deploy/backend --tail=100 | grep -i rabbit
```

**Resolution:**

1. Scale up consumer pods
2. Check for consumer errors causing requeue loops
3. Purge queue if messages are stale (with approval)

## Alerts and Monitoring

### Alert Response Matrix

| Alert | Severity | Response Time | Runbook Section |
|-------|----------|---------------|-----------------|
| HighErrorRate | P1 | 15 min | Troubleshooting: Issue 1 |
| DatabaseDown | P1 | 15 min | Troubleshooting: Issue 2 |
| HighMemoryUsage | P2 | 1 hour | Troubleshooting: Issue 3 |
| QueueBacklog | P2 | 1 hour | Troubleshooting: Issue 4 |

### Key Dashboards

| Dashboard | URL | Purpose |
|-----------|-----|---------|
| Application Overview | /grafana/d/app-overview | High-level health |
| API Latency | /grafana/d/api-latency | Request performance |
| Database Metrics | /grafana/d/postgres | Database health |
| Message Queue | /grafana/d/rabbitmq | Queue depths and rates |

## Appendix

### Useful Commands

```bash
# Get all resources in namespace
kubectl get all -n $NAMESPACE

# Watch pod status
kubectl get pods -n $NAMESPACE -w

# Port forward to service
kubectl port-forward -n $NAMESPACE svc/backend 8080:8080

# Execute shell in pod
kubectl exec -it -n $NAMESPACE deploy/backend -- /bin/sh

# View real-time logs
kubectl logs -f -n $NAMESPACE deploy/backend
```

### Configuration Files

| File | Location | Purpose |
|------|----------|---------|
| values.yaml | /helm/app/values.yaml | Helm chart values |
| terraform.tfvars | /terraform/envs/$ENV/ | Terraform variables |
| .env | /backend/.env.template | Environment template |

## References

- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Helm Documentation](https://helm.sh/docs/)
- [Internal Wiki: Deployment Process](./internal-link)
- [Incident Response Playbook](./incident-response.md)

## Review History

| Date | Author | Changes |
|------|--------|---------|
| YYYY-MM-DD | Name | Initial version |
