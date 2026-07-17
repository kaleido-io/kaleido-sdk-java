#!/usr/bin/env bash
#
# Build and push a one-off getting-started-sample image directly from a local
# checkout, bypassing GitHub Packages entirely: builds against the sibling
# workflow-engine-sdk source (composite build) rather than a published SDK
# artifact. This is a stopgap for testing this sample before
# workflow-engine-sdk has a published release for CI to build against.
#
# Run from anywhere; this script cds to the SDK repo root itself since
# Dockerfile.local's composite build needs that as its Docker build context.
#
# Required environment:
#   ARTIFACT_REGISTRY_HOST  e.g. registry.my-instance.kaleido.io
#   API_KEY_NAME            platform API key name with push access to the registry
#   API_KEY_VALUE           platform API key value
# Optional:
#   REGISTRY_NAMESPACE      registry namespace for images (default: getting-started-images)
#   IMAGE_NAME              image repository name    (default: workflow-engine-getting-started)
#   IMAGE_TAG               immutable image tag      (default: local-<timestamp>)
#   PLATFORMS               target platform(s)       (default: host arch only)

set -euo pipefail

: "${ARTIFACT_REGISTRY_HOST:?set ARTIFACT_REGISTRY_HOST to the artifact registry hostname}"
: "${API_KEY_NAME:?set API_KEY_NAME to a platform API key name}"
: "${API_KEY_VALUE:?set API_KEY_VALUE to the platform API key value}"
REGISTRY_NAMESPACE="${REGISTRY_NAMESPACE:-getting-started-images}"
IMAGE_NAME="${IMAGE_NAME:-workflow-engine-getting-started}"
IMAGE_TAG="${IMAGE_TAG:-local-$(date +%Y%m%d-%H%M%S)}"
PLATFORMS="${PLATFORMS:-}"

IMAGE_REF="${ARTIFACT_REGISTRY_HOST}/${REGISTRY_NAMESPACE}/${IMAGE_NAME}:${IMAGE_TAG}"
SDK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
DOCKERFILE="${SDK_ROOT}/samples/workflow-engine-getting-started/Dockerfile.local"

docker login "${ARTIFACT_REGISTRY_HOST}" -u "${API_KEY_NAME}" -p "${API_KEY_VALUE}"

echo "==> Building ${IMAGE_REF} against the local kaleido-sdk-java checkout"
if [ -n "${PLATFORMS}" ]; then
  # buildx --push: multi-arch images cannot be loaded into the local docker
  # store, so build and push in one step.
  docker buildx build \
    --platform "${PLATFORMS}" \
    --provenance=false --sbom=false \
    --push \
    -f "${DOCKERFILE}" \
    -t "${IMAGE_REF}" \
    "${SDK_ROOT}"
else
  docker build -f "${DOCKERFILE}" -t "${IMAGE_REF}" "${SDK_ROOT}"
  echo "==> Pushing ${IMAGE_REF}"
  docker push "${IMAGE_REF}"
fi

echo "Pushed ${IMAGE_REF}"
