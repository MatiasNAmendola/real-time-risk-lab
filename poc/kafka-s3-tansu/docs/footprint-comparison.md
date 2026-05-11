# Footprint comparison — Tansu vs Redpanda

All measurements taken on the same host (macOS arm64, OrbStack), idle state,
no client traffic, right after broker became healthy. Captured via
`docker stats --no-stream`.

## Idle memory (RSS reported by Docker)

| Container          | Image                                 | Mem usage    | Mem limit  |
|--------------------|---------------------------------------|--------------|------------|
| `compose-redpanda-1` | `redpandadata/redpanda:v24.2.4`     | **421.2 MiB**| 768 MiB    |
| `compose-tansu-1`    | `ghcr.io/tansu-io/tansu:0.6.0`      | **7.73 MiB** | 256 MiB    |
| `compose-floci-1`    | `floci/floci:latest`                | **48.77 MiB**| 256 MiB    |

So the apples-to-apples comparison is:

- **Redpanda standalone**: ~421 MiB.
- **Tansu + Floci (storage backend)**: 7.73 + 48.77 = **~56.5 MiB**.

Tansu+Floci together use roughly **13%** of Redpanda's RAM at idle, and
Floci is already a shared infra service in this repo (ADR-0042) — so on a
stack that already runs Floci, the *marginal* cost of adding Tansu is just
**~7.7 MiB**.

## Why the gap is this wide

- Redpanda runs the Seastar reactor pool, in-process Raft, schema registry,
  pandaproxy, kafka, and admin listeners — even on a single-broker dev box.
- Tansu is a stateless Rust binary; durability is delegated to S3.
- Redpanda's `--memory 512M` is a *reservation*, not an upper bound on
  resident usage; the 421 MiB observed is post-startup steady-state.

## Caveats

- These are **idle** numbers. Under sustained 150 TPS load the gap will
  narrow (Tansu allocates page-cache-sized buffers; S3 in-flight requests
  add memory pressure). This PoC did **not** run a load test.
- Redpanda numbers measured with its in-repo flags
  (`--smp 1 --memory 512M --reserve-memory 0M --overprovisioned`) which
  are tuned for dev, not prod.
- CPU at idle was ~0% for Tansu and ~1% for Redpanda — not a meaningful
  differentiator at zero traffic.

## Source data (verbatim)

```
$ docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}" \
    compose-redpanda-1 compose-tansu-1 compose-floci-1
NAME                 MEM USAGE / LIMIT   CPU %
compose-redpanda-1   421.2MiB / 768MiB   0.99%
compose-tansu-1      7.73MiB / 256MiB    0.00%
compose-floci-1      48.77MiB / 256MiB   0.03%
```
