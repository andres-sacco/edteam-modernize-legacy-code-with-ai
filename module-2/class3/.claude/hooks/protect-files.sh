#!/usr/bin/env bash
# PreToolUse hook (Edit|Write): blocks changes to files that must not be edited by hand.
# Exit code 2 blocks the tool call and sends the message of stderr back to Claude.

file_path=$(python3 -c 'import json, sys; print(json.load(sys.stdin).get("tool_input", {}).get("file_path", ""))')
[ -z "$file_path" ] && exit 0

project_dir="${CLAUDE_PROJECT_DIR:-$(pwd)}"
relative_path="${file_path#"$project_dir"/}"

case "$relative_path" in
  twa-flights-monolith/src/main/resources/db/migration/V*.sql)
    if [ -e "$file_path" ]; then
      echo "No se editan migraciones de Flyway existentes ($relative_path): una base que ya la aplicó falla al validar. Creá una nueva migración con el siguiente número de versión." >&2
      exit 2
    fi
    ;;
  # The contracts of the providers and of the catalog service are generated
  microcks/provider-*-openapi.json|microcks/catalog-openapi.json)
    echo "Los contratos de Microcks son generados ($relative_path). Modificá microcks/generate-contracts.py y ejecutá: python3 microcks/generate-contracts.py" >&2
    exit 2
    ;;
  docs/NOTAS-INSTRUCTOR.md)
    echo "docs/NOTAS-INSTRUCTOR.md es material del instructor y no se modifica desde el agente." >&2
    exit 2
    ;;
esac

exit 0
