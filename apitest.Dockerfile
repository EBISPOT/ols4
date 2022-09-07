
FROM ubuntu:20.04

RUN apt-get update && apt-get install -y curl

ADD ./wait_for_urls.sh /


CMD bash -c "\
echo Running API test... \
rm -rf /mnt/out/* && \
mkdir /mnt/out/v1 && \
mkdir /mnt/out/v2 && \
echo Waiting for OLS4 server to become available... && \
/wait_for_urls.sh http://ols4-server:8080/api/v2/ontologies && \
echo Calling endpoints... && \
curl http://ols4-server:8080/api/v2/ontologies > /mnt/out/v2/ontologies.json \
"





