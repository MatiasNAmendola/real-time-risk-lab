---
title: Bubble Tea Documentation
tags: [reference, go, tui]
created: 2026-05-07
---

# Bubble Tea Docs

Reference for the Go TUI framework used in [[risk-smoke-tui]].

## Key URLs

- https://github.com/charmbracelet/bubbletea — Bubble Tea framework
- https://github.com/charmbracelet/lipgloss — styling
- https://github.com/charmbracelet/bubbles — spinner, list, progress bar

## Architecture (Elm-style)

- **Model**: application state
- **Update**: pure function, `(Model, Msg) → (Model, Cmd)`
- **View**: pure function, `Model → string`

Commands (`Cmd`) run async tasks (HTTP checks, WS connections) and send results back as `Msg`.

## Backlinks

[[risk-smoke-tui]] · [[0009-bubbletea-tui-smoke]]
