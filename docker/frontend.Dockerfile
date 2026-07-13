# syntax=docker/dockerfile:1.7
FROM node:20-alpine AS build
WORKDIR /workspace
RUN corepack enable && corepack prepare pnpm@9.15.4 --activate
COPY pnpm-workspace.yaml pnpm-lock.yaml ./
COPY frontend/package.json frontend/package.json
RUN --mount=type=cache,id=carenest-pnpm,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile
COPY frontend/ frontend/
WORKDIR /workspace/frontend
ARG VITE_FRONTEND_API_BASE=/api/v1
ENV VITE_FRONTEND_API_BASE=${VITE_FRONTEND_API_BASE}
ENV VITE_USE_MOCK=false
RUN pnpm build:h5

FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime
COPY docker/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY docker/nginx/proxy-headers.conf /etc/nginx/proxy-headers.conf
COPY --from=build /workspace/frontend/dist/build/h5 /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=6 \
  CMD wget -q -O - http://127.0.0.1:8080/nginx-health | grep -q '^ok$' || exit 1
