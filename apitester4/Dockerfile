
FROM maven:3.8-jdk-11

ARG USERNAME=apitester
ARG USER_UID=1000
ARG USER_GID=$USER_UID

RUN groupadd --gid $USER_GID $USERNAME \
    && useradd --create-home --no-log-init --uid $USER_UID --gid $USER_GID -m $USERNAME

ADD . /opt/apitester
RUN chown -R apitester:apitester /opt/apitester

RUN cd /opt/apitester && mvn clean package
RUN find opt/apitester



