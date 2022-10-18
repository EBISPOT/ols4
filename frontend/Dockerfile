

FROM node:16 AS build


RUN mkdir /opt/ols-frontend
COPY . /opt/ols-frontend

WORKDIR /opt/ols-frontend

RUN yarn install

RUN npm run build:docker

FROM caddy:2-alpine

COPY --from=build /opt/ols-frontend/build /opt/ols-www
COPY Caddyfile /etc/caddy/Caddyfile


