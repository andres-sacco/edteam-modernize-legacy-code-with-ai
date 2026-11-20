#!/bin/sh
# Wait until Microcks is ready and upload the contracts of the providers
until curl -sf "$MICROCKS_URL/api/health" > /dev/null; do
  echo "Waiting for Microcks..."
  sleep 2
done

for artifact in /artifacts/*-openapi.json; do
  echo "Importing $artifact"
  curl -sf -F "file=@$artifact" "$MICROCKS_URL/api/artifact/upload?mainArtifact=true" || exit 1
  echo
done
