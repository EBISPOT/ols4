#!/usr/bin/env bash

curl "https://raw.githubusercontent.com/OBOFoundry/OBOFoundry.github.io/master/_config.yml" \
        | yq eval -j - > foundry.json



