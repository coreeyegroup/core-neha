#!/bin/bash
# Readiness probe on the management port without curl (not in the image).
# Keycloak answers 200 when ready and 503 otherwise.
exec 3<>/dev/tcp/127.0.0.1/9000 || exit 1
printf 'GET %s/auth/health/ready HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' "${CORE_NEHA_BASE_PATH}" >&3
IFS= read -r status_line <&3 || exit 1
[[ "$status_line" == "HTTP/1.1 200"* ]]
