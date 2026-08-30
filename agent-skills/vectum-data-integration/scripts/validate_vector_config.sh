#!/usr/bin/env bash
set -euo pipefail

usage() {
    printf 'Usage: %s <vector-config.yaml|toml|json>\n' "$0" >&2
    printf 'Environment:\n' >&2
    printf '  VECTOR_BIN=vector                  Vector binary path/name.\n' >&2
    printf '  VECTOR_VALIDATE_MODE=no-environment|skip-healthchecks|full\n' >&2
    printf '  VECTOR_VALIDATE_TIMEOUT=60         Seconds before killing a stuck validation.\n' >&2
}

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
    usage
    exit 0
fi

if [ "$#" -ne 1 ]; then
    usage
    exit 2
fi

config_path="$1"
vector_bin="${VECTOR_BIN:-vector}"
mode="${VECTOR_VALIDATE_MODE:-no-environment}"
timeout_seconds="${VECTOR_VALIDATE_TIMEOUT:-60}"

if [ ! -f "$config_path" ]; then
    printf 'Vector config file not found: %s\n' "$config_path" >&2
    exit 2
fi

if ! command -v "$vector_bin" >/dev/null 2>&1; then
    printf 'Vector binary not found: %s\n' "$vector_bin" >&2
    printf 'Install Vector or set VECTOR_BIN to an executable path.\n' >&2
    exit 127
fi

run_vector_validate() {
    "$vector_bin" "$@" &
    validate_pid="$!"

    (
        sleep "$timeout_seconds"
        if kill -0 "$validate_pid" >/dev/null 2>&1; then
            printf 'Vector validation timed out after %s seconds.\n' "$timeout_seconds" >&2
            kill "$validate_pid" >/dev/null 2>&1 || true
            sleep 2
            kill -9 "$validate_pid" >/dev/null 2>&1 || true
        fi
    ) &
    watchdog_pid="$!"

    set +e
    wait "$validate_pid" 2>/dev/null
    status="$?"
    set -e
    kill "$watchdog_pid" >/dev/null 2>&1 || true
    wait "$watchdog_pid" >/dev/null 2>&1 || true
    return "$status"
}

case "$mode" in
    no-environment)
        run_vector_validate validate --no-environment "$config_path"
        ;;
    skip-healthchecks)
        run_vector_validate validate --skip-healthchecks "$config_path"
        ;;
    full)
        run_vector_validate validate "$config_path"
        ;;
    *)
        printf 'Unsupported VECTOR_VALIDATE_MODE: %s\n' "$mode" >&2
        usage
        exit 2
        ;;
esac
