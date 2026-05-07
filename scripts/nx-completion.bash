#!/usr/bin/env bash
# nx-completion.bash — bash completion for ./nx
#
# Usage:
#   ./nx completion bash > ~/.bash_completion.d/nx
#   source ~/.bash_completion.d/nx
#
# Or source this file directly:
#   source scripts/nx-completion.bash

_nx_complete() {
  local cur prev words
  COMPREPLY=()
  cur="${COMP_WORDS[COMP_CWORD]}"
  prev="${COMP_WORDS[COMP_CWORD-1]}"
  words=("${COMP_WORDS[@]}")

  local commands="setup up down status logs build run test bench demo dashboard admin audit scrub help version"

  if [ "${COMP_CWORD}" -eq 1 ]; then
    COMPREPLY=( $(compgen -W "$commands" -- "$cur") )
    return 0
  fi

  local cmd="${words[1]}"
  case "$cmd" in
    up|down)
      COMPREPLY=( $(compgen -W "compose k8s" -- "$cur") )
      ;;
    run)
      COMPREPLY=( $(compgen -W "risk-engine vertx k8s" -- "$cur") )
      ;;
    test)
      COMPREPLY=( $(compgen -W "all smoke atdd-karate atdd-cucumber integration architecture unit --help" -- "$cur") )
      if [ "${words[2]}" = "all" ]; then
        COMPREPLY=( $(compgen -W "--with-infra-compose --with-infra-k8s --headless --only" -- "$cur") )
      fi
      ;;
    bench)
      COMPREPLY=( $(compgen -W "inproc distributed competition --help" -- "$cur") )
      ;;
    demo)
      COMPREPLY=( $(compgen -W "rest websocket sse webhook kafka --help" -- "$cur") )
      ;;
    dashboard)
      COMPREPLY=( $(compgen -W "up down" -- "$cur") )
      ;;
    admin)
      if [ "${COMP_CWORD}" -eq 2 ]; then
        COMPREPLY=( $(compgen -W "rules" -- "$cur") )
      elif [ "${words[2]}" = "rules" ]; then
        COMPREPLY=( $(compgen -W "list reload test" -- "$cur") )
      fi
      ;;
    audit)
      COMPREPLY=( $(compgen -W "docs cli primitives all --report-md --strict --json" -- "$cur") )
      ;;
    setup)
      COMPREPLY=( $(compgen -W "--verify --upgrade --dry-run --only --skip" -- "$cur") )
      ;;
    help)
      COMPREPLY=( $(compgen -W "$commands" -- "$cur") )
      ;;
  esac
  return 0
}

complete -F _nx_complete nx
complete -F _nx_complete ./nx
