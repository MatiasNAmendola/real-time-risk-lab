# Changelog

## [Unreleased]

## [1.0.0] - 2026-05-08
### Added
- Initial release.
- Sync REST: evaluate, evaluateBatch, health.
- Stream SSE: decisions stream.
- Channel WebSocket: bidirectional.
- Events: HTTP/SSE adapter consume + publish custom (Go avoids direct Kafka wire against Tansu 0.6.0).
- Queue: SQS send + receive.
- Webhooks: subscribe, unsubscribe, list, verify.
- Admin: listRules, reloadRules, testRule, audit.
