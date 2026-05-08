---
name: architecture-review-checklist
description: What to review before presenting or sharing the risk decision platform exploration
---

# Workflow: architecture-review-checklist

## Project: Risk Decision Platform — Three-Architecture Exploration

Target: 150 TPS, p99 < 300ms, production-grade fraud detection use case

## Pre-Presentation Verification

### Verify that everything runs

```bash
# PoC 1: bare-javac
cd poc/java-risk-engine && ./scripts/run.sh &
./scripts/test.sh
# should show: p50/p99 latency, decisions

# PoC 2: Vert.x distributed
cd poc/java-vertx-distributed && ./gradlew shadowJar -q
docker-compose up -d
./gradlew :poc:java-vertx-distributed:atdd-tests:test -Patdd
# all scenarios green

# PoC 3: k8s-local
cd poc/k8s-local && ./scripts/up.sh
./scripts/status.sh
# ArgoCD sync OK, app running

# CLI smoke
cd cli/risk-smoke && go run .
# 9/9 checks green ideally, at least 7/9
```

### Review key documents (15 min each)

- [ ] `docs/01-design-conversation-framework.md` — how to decompose systems design problems
- [ ] `docs/09-architecture-question-bank.md` — architecture questions with model analyses
- [ ] `docs/00-mapa-tecnico.md` — fraud system architecture map
- [ ] `docs/07-lambda-vs-eks.md` — Lambda to EKS migration trade-offs
- [ ] `docs/02-platform-discovery-questions.md` — discovery questions for evaluating a platform

### Prepare demos

| Demo | Command | Time |
|---|---|---|
| Real-time decision | `curl -s -X POST localhost:8080/risk -d '{...}'` | 30s |
| Rules engine | `./poc/java-risk-engine/scripts/test.sh` | 1min |
| k8s + ArgoCD | open ArgoCD UI, show sync | 2min |
| Canary deployment | `kubectl argo rollouts get rollout risk-engine` | 1min |
| Trace in OpenObserve | browser to localhost:5080 | 1min |

### Reminders for the discussion

1. Lead with the PROBLEM statement first, then the technical solution.
   "The problem is reducing fraud without impacting conversion. The solution is..."

2. Name trade-offs explicitly.
   "We chose Vert.x over Spring Boot because... although we traded off..."

3. Keep SLO grounded.
   "The SLO is p99 < 300ms at 150 TPS. This is how we validated it..."

4. Discovery questions: see `docs/02-platform-discovery-questions.md`.

5. Do not rush. If you do not know something: "my approach would be... and I would validate with the team on..."

## Pre-Presentation Checklist

- [ ] Computer charged
- [ ] Docker running
- [ ] Terminal with commands prepared
- [ ] Browser with OpenObserve, Prometheus, ArgoCD already open
- [ ] `docs/09-architecture-question-bank.md` in a tab for quick reference
- [ ] Notes on discovery questions

## If Something Does Not Run

- k8s PoC fails: use PoC 2 (Vert.x + docker-compose). Simpler and equally demonstrable.
- PoC 2 fails: use PoC 1 (bare-javac). Most robust, zero dependencies.
- Smoke TUI fails: run the curls manually. The code is what matters.
