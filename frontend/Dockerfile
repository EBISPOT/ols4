

FROM node:25-bookworm AS frontend-builder

WORKDIR /opt/ols4-frontend

COPY package.json package-lock.json ./
RUN npm ci

COPY . ./

# Runtime configuration is injected by the serving container, so build a
# portable, production-minified bundle without loading frontend/.env here.
RUN mkdir -p dist && PATH="/opt/ols4-frontend/node_modules/.bin:$PATH" OLS_MINIFY=true PUBLIC_URL=/ node build.mjs

FROM caddy:2-alpine

COPY Caddyfile /etc/caddy/Caddyfile
COPY .env /opt/ols4-frontend/default.env
COPY entrypoint.dockersh /opt/ols4-frontend/entrypoint.dockersh
COPY --from=frontend-builder /opt/ols4-frontend/dist /opt/ols4-frontend/dist

RUN chmod +x /opt/ols4-frontend/entrypoint.dockersh
RUN chmod -R 777 /opt/ols4-frontend/dist

EXPOSE 8080

CMD ["/opt/ols4-frontend/entrypoint.dockersh"]
