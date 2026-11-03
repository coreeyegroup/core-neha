# Keycloak image for core-neha: optimised build with PostgreSQL, health and
# metrics, and the relative path derived from CORE_NEHA_BASE_PATH.
ARG KC_VERSION=26.8.1
FROM quay.io/keycloak/keycloak:${KC_VERSION} AS builder
ARG BASE_PATH=/core-neha
ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true \
    KC_HTTP_RELATIVE_PATH=${BASE_PATH}/auth
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:${KC_VERSION}
ARG BASE_PATH=/core-neha
COPY --from=builder /opt/keycloak/ /opt/keycloak/
COPY --chown=1000:0 realm/ /opt/keycloak/data/import/
COPY --chown=1000:0 core-neha/ /opt/core-neha/
ENV CORE_NEHA_BASE_PATH=${BASE_PATH}
ENTRYPOINT ["/bin/bash", "/opt/core-neha/entrypoint.sh"]
