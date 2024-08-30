#!/usr/bin/env bash

if [ $# == 0 ]; then
    echo "Usage: $0 <rel_json_config_url> <rel_output_dir>"
    echo "If <rel_json_config_url> is a file it will read and load this single configuration."
    echo "If <rel_json_config_url> as a directory, it will read and load all json configuration in the directory and
    subdirectories."
    exit 1
fi

config_url=$1
out_dir=$2

# Create or clean output directory
if [ -d "$out_dir" ]; then
    echo "$out_dir already exists and will now be cleaned."
    rm -Rf $out_dir/*
else
    echo "$out_dir does not exist and will now be created."
    mkdir "$out_dir"
fi

function process_config {
  echo "process_config param1="$1
  echo "process_config param2="$2

  local config_url=$1
  local out_dir=$2


  if [ -d "$config_url" ]; then
      echo "$config_url is a directory. Processing config files in $config_url"
      local basename=$(basename $config_url)
      echo "basename for config_url="$basename
      local out_dir_basename=$out_dir/$basename
      mkdir $out_dir_basename
      for filename in $config_url/*.json; do
          echo "filename="$filename
          process_config $filename $out_dir_basename
      done
      for dir in $config_url/*/; do
          process_config $dir $out_dir_basename
      done
  elif [ -f "$config_url"  ]; then
    echo "$config_url is a file. Processing single config file."
    local basename=$(basename $config_url .json)

    local relative_out_dir=$out_dir/$basename
    mkdir $relative_out_dir

    local absolute_out_dir=$(realpath -q $relative_out_dir)
    echo "absolute_out_dir="$absolute_out_dir

    $OLS4_HOME/dataload/create_datafiles.sh $config_url $absolute_out_dir --noDates

    $OLS4_HOME/dev-testing/load_test_into_solr.sh $absolute_out_dir
  else
    echo "$config_url does not exist."
  fi
}

$OLS4_HOME/dev-testing/clean-neo4j.sh
$OLS4_HOME/dev-testing/clean-solr.sh
$OLS4_HOME/dev-testing/start-solr.sh

process_config $config_url $out_dir

$OLS4_HOME/dev-testing/load_test_into_neo4j.sh $out_dir
$OLS4_HOME/dev-testing/start-neo4j.sh
