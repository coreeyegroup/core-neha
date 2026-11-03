#!/bin/bash
# Readiness probe on the internal management port (curl is not in the image).
exec 3<>/dev/tcp/127.0.0.1/8081 || exit 1
printf 'GET /actuator/health/readiness HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3
IFS= read -r status_line <&3 || exit 1
[[ "$status_line" == "HTTP/1.1 200"* ]]
