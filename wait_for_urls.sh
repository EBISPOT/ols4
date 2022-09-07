#/bin/bash


# https://gist.github.com/eisenreich/195ab1f05715ec86e300f75d007d711c



##############################################################################################
# Wait for URLs until return HTTP 200
#
# - Just pass as many urls as required to the script - the script will wait for each, one by one
#
# Example: ./wait_for_urls.sh "${MY_VARIABLE}" "http://192.168.56.101:8080"
##############################################################################################

wait-for-url() {
    echo "Testing $1"
    timeout --foreground -s TERM 30s bash -c \
        'while [[ "$(curl -s -o /dev/null -m 3 -L -w ''%{http_code}'' ${0})" != "200" ]];\
        do echo "Waiting for ${0}" && sleep 2;\
        done' ${1}
    echo "${1} - OK!"
}

echo "Wait for URLs: $@"

for var in "$@"; do
    wait-for-url "$var"
done


