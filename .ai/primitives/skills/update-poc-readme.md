---
name: update-poc-readme
intent: Update a PoC README and synchronize PoC inventory/docs
inputs: [poc_path, new_features, current_status]
preconditions:
  - poc/<name>/README.md exists
postconditions:
  - README describes purpose, stack, run commands, status and demos
  - .ai/context/poc-inventory.md is current
  - vault/03-PoCs/<poc-name>.md exists or is current
  - Relevant MOCs are updated when the PoC introduces a pattern
related_rules: [naming-conventions, documentation-system]
---

# Skill: update-poc-readme

## README template

```markdown
# <PoC name> — Real-Time Risk Lab

Two-line purpose: what this PoC demonstrates and why it exists.

## What it demonstrates

- <pattern 1>
- <pattern 2>
- <pattern 3>

## Stack

| Component | Version |
|---|---|
| Java | 21 LTS executable baseline (`--release 21`); Java 25 documented target |
| Vert.x | 5.0.12 |

## Structure

Short package/tree layout.

## Run

```bash
./scripts/run.sh
```

## Verify

```bash
./scripts/test.sh
curl http://localhost:<port>/healthz
```

## Status

- [x] Clean Architecture layout
- [x] REST endpoint
- [ ] ATDD complete

## Live demos

1. Show X: `curl ...`
2. Show Y: `./scripts/demo.sh`
```

## Steps

1. Read the current README.
2. Remove stale sections and add current run/test commands.
3. Update `.ai/context/poc-inventory.md`.
4. Create/update `vault/03-PoCs/<name>.md` and link related concepts/ADRs.
5. Update `vault/00-MOCs/*` if the PoC adds a relevant pattern.
6. Commit as `docs(poc/<name>): update README with current state`.
