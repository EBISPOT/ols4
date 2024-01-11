#!/bin/bash

if [ $# == 0 ]; then
    echo "Usage: $0 <config_url> <download_dir>"
    exit 1
fi
# exit if anything fails
set -e

SCRIPT_PATH=$(dirname $(readlink -f $0))

config_url=$1
download_dir=$2

mkdir -p $download_dir


java -DentityExpansionLimit=0 -DtotalEntitySizeLimit=0 -Djdk.xml.totalEntitySizeLimit=0 -Djdk.xml.entityExpansionLimit=0 \
        -jar $SCRIPT_PATH/predownloader/target/predownloader-1.0-SNAPSHOT.jar --config $config_url --downloadPath $download_dir
