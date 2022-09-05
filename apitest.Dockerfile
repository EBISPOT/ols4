
FROM ubuntu:20.04

RUN apt-get update && apt-get install -y curl

CMD bash -c "\
    sleep 20 && \
	curl http://ols4-server:8080/api/v2/ontologies > /mnt/out/ontologies.json \
"



