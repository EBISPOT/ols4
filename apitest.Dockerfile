
FROM ubuntu:20.04

RUN apt-get update && apt-get install -y curl


CMD bash -c "\
rm -rf /mnt/out/* && \
mkdir /mnt/out/v1 && \
mkdir /mnt/out/v2 && \
echo Waiting for OLS4 server to become available... && \
curl --connect-timeout 5 --max-time 10 --retry 5 --retry-delay 5 --retry-max-time 60 http://ols4-server:8080/api/v2/ontologies && \
echo Calling endpoints... && \
curl http://ols4-server:8080/api/v2/ontologies > /mnt/out/v2/ontologies.json \
"



