#!/usr/bin/env bash
# .ai/scripts/engram-bootstrap.sh
# Carga contexto de Engram al arrancar una sesion.
# Este script es informativo — la carga real de Engram se hace via MCP tools.
# Usar como referencia para el hook session-start-engram-load.

set -e

PROJECT="riskplatform/risk-platform-practice"
CONTEXT_DIR="$(dirname "$0")/../context"

echo "=== Engram Bootstrap: $PROJECT ==="
echo ""
echo "Para cargar el contexto del proyecto, ejecutar en el agente:"
echo ""
echo "  1. mem_current_project()"
echo "     → verifica que estamos en: $PROJECT"
echo ""
echo "  2. mem_context(project: \"$PROJECT\")"
echo "     → carga historial reciente de sesiones"
echo ""
echo "  3. mem_search(query: \"risk decision platform state\")"
echo "     → busca el estado actual de la preparacion"
echo ""
echo "  4. Si hay trabajo previo en una PoC especifica:"
echo "     mem_search(query: \"riskplatform poc <poc-name>\")"
echo ""
echo "Context files locales disponibles:"
echo "  - $CONTEXT_DIR/exploration-state.md   (estado de prep)"
echo "  - $CONTEXT_DIR/architecture.md      (arquitectura del repo)"
echo "  - $CONTEXT_DIR/poc-inventory.md     (inventario de PoCs)"
echo "  - $CONTEXT_DIR/decisions-log.md     (ADRs)"
echo "  - $CONTEXT_DIR/stack.md             (versiones exactas)"
echo ""
echo "Topic keys de Engram para este proyecto:"
echo "  riskplatform/practice/state"
echo "  riskplatform/poc/java-risk-engine"
echo "  riskplatform/poc/java-vertx-distributed"
echo "  riskplatform/poc/vertx-risk-platform"
echo "  riskplatform/poc/k8s-local"
echo "  riskplatform/primitives/system"
echo ""
echo "=== Bootstrap completo ==="
