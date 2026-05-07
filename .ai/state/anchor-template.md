# Pre-Compact Anchor Template

This file documents the structure of anchor files written by pre-compact-anchor.sh.
Actual anchors are written as anchor-<sessionid>.md in this directory.

## Structure

```
# Pre-Compact Anchor — <ISO timestamp>

Session: <CLAUDE_SESSION_ID or timestamp>
Written at: <ISO timestamp>

## Current State Summary
## Recent Skill Invocations
## Files In Flight
## Pending Agent Bus Messages
## Critical Decisions
## Background Agents
## Resume Instructions
```

## How session-bootstrap.sh uses this

On session start, session-bootstrap.sh looks for the most recent anchor file
in .ai/state/ and injects its content into the bootstrap context output,
allowing the next session to resume without a cold start.
