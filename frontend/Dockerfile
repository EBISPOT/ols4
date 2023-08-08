

FROM ubuntu:22.04

RUN apt update && apt install -y curl gpg

# node
RUN curl -sL https://deb.nodesource.com/setup_16.x | bash

# caddy
RUN curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
RUN curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | tee /etc/apt/sources.list.d/caddy-stable.list


RUN apt update && apt install -y nodejs caddy

RUN mkdir /opt/ols4-frontend


WORKDIR /opt/ols4-frontend


COPY package.json package-lock.json /opt/ols4-frontend/
RUN npm install

COPY . /opt/ols4-frontend/

RUN chmod +x /opt/ols4-frontend/entrypoint.dockersh

CMD ["/opt/ols4-frontend/entrypoint.dockersh"]


