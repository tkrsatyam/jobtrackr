#!/usr/bin/env bash
set -e

# Determine script root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -d "$SCRIPT_DIR/services" ]; then
  ROOT_DIR="$SCRIPT_DIR"
elif [ -d "$SCRIPT_DIR/../services" ]; then
  ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
else
  ROOT_DIR="$(pwd)"
fi

SERVICES_DIR="$ROOT_DIR/services"

# Default services required for Docker compose
DOCKER_SERVICES=(
  "eureka-server"
  "api-gateway"
  "user-service"
  "application-service"
  "email-ingestion-service"
)

TARGET_SERVICES=()
SKIP_TESTS=true
BUILD_ALL=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --all|-a)
      BUILD_ALL=true
      shift
      ;;
    --run-tests|-t)
      SKIP_TESTS=false
      shift
      ;;
    *)
      TARGET_SERVICES+=("$1")
      shift
      ;;
  esac
done

if [ "$BUILD_ALL" = true ]; then
  TARGET_SERVICES=()
  for dir in "$SERVICES_DIR"/*/; do
    if [ -f "$dir/pom.xml" ]; then
      TARGET_SERVICES+=("$(basename "$dir")")
    fi
  done
elif [ ${#TARGET_SERVICES[@]} -eq 0 ]; then
  TARGET_SERVICES=("${DOCKER_SERVICES[@]}")
fi

echo "===================================================="
echo " JobTrackr - Microservices Build Script"
echo "===================================================="
echo "Target services (${#TARGET_SERVICES[@]}): ${TARGET_SERVICES[*]}"
echo "Skip tests: $SKIP_TESTS"
echo ""

MVN_ARGS=("clean" "install")
if [ "$SKIP_TESTS" = true ]; then
  MVN_ARGS+=("-DskipTests")
fi

START_TOTAL=$(date +%s)
SUMMARY=()
FAILED=false

INDEX=1
TOTAL=${#TARGET_SERVICES[@]}

for svc in "${TARGET_SERVICES[@]}"; do
  SVC_PATH="$SERVICES_DIR/$svc"
  if [ ! -d "$SVC_PATH" ]; then
    echo "[-] Service directory '$svc' not found at '$SVC_PATH'!"
    SUMMARY+=("[-] $svc (NOT FOUND)")
    FAILED=true
    break
  fi

  echo "[$INDEX/$TOTAL] Building $svc..."
  START_SVC=$(date +%s)

  cd "$SVC_PATH"
  if [ -f "./mvnw" ]; then
    MVN_CMD="./mvnw"
  elif [ -f "./mvnw.cmd" ]; then
    MVN_CMD="./mvnw.cmd"
  else
    MVN_CMD="mvn"
  fi

  if "$MVN_CMD" "${MVN_ARGS[@]}"; then
    END_SVC=$(date +%s)
    DIFF_SVC=$((END_SVC - START_SVC))
    echo "[+] $svc built successfully in ${DIFF_SVC}s"
    SUMMARY+=("[OK]   $svc (${DIFF_SVC}s)")
  else
    END_SVC=$(date +%s)
    DIFF_SVC=$((END_SVC - START_SVC))
    echo "[-] Failed to build $svc"
    SUMMARY+=("[FAIL] $svc (${DIFF_SVC}s)")
    FAILED=true
    cd "$ROOT_DIR"
    break
  fi

  cd "$ROOT_DIR"
  INDEX=$((INDEX + 1))
  echo ""
done

END_TOTAL=$(date +%s)
TOTAL_DIFF=$((END_TOTAL - START_TOTAL))
TOTAL_MIN=$((TOTAL_DIFF / 60))
TOTAL_SEC=$((TOTAL_DIFF % 60))

echo "===================================================="
echo " BUILD SUMMARY"
echo "===================================================="
for item in "${SUMMARY[@]}"; do
  echo " $item"
done
echo "----------------------------------------------------"
echo "Total elapsed time: ${TOTAL_MIN}m ${TOTAL_SEC}s"

if [ "$FAILED" = true ]; then
  echo "Build failed. Please check the logs above."
  exit 1
else
  echo "All target JARs updated successfully. Ready for docker compose."
  exit 0
fi
