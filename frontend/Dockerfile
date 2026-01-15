

FROM node:25-bookworm

RUN apt-get update && apt-get install -y curl gpg && rm -rf /var/lib/apt/lists/*

# caddy
RUN curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
RUN curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | tee /etc/apt/sources.list.d/caddy-stable.list
RUN apt-get update && apt-get install -y caddy && rm -rf /var/lib/apt/lists/*

RUN mkdir /opt/ols4-frontend

WORKDIR /opt/ols4-frontend

COPY package.json package-lock.json /opt/ols4-frontend/
RUN npm install

COPY . /opt/ols4-frontend/

RUN chmod +x /opt/ols4-frontend/entrypoint.dockersh
RUN chmod -R 777 /opt/ols4-frontend/dist

CMD ["/opt/ols4-frontend/entrypoint.dockersh"]

