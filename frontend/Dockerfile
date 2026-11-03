# Staff application built into the gateway image (nginx serves it at <base path>/).
FROM node:24.22.2-alpine3.24 AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
ARG BASE_PATH=/core-neha
ENV CORE_NEHA_BASE_PATH=${BASE_PATH}
RUN npm run build

FROM nginx:1.30.6-alpine3.24
ARG BASE_PATH=/core-neha
COPY --from=build /app/dist /usr/share/nginx/html${BASE_PATH}
