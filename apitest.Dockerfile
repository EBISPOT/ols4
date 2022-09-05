
FROM ubuntu:20.04

RUN apt-get update && apt-get install -y curl

rm -rf /mnt/out/*
mkdir /mnt/out/v1
mkdir /mnt/out/v2


mkdir 
CMD bash -c "\
    sleep 20 && \
	curl http://ols4-server:8080/api/v2/ontologies > /mnt/out/v2/ontologies.json \
"



