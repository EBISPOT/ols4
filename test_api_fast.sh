#!/bin/bash

# Usage information
usage() {
  echo "Usage: $0 <ols_url> <output_dir> <compare_dir> [<ontology_id>] [--deep]"
  echo
  echo "Arguments:"
  echo "  <ols_url>      The URL of the OLS instance"
  echo "  <output_dir>   The directory to save the output"
  echo "  <compare_dir>  The directory to compare against"
  echo "  <ontology_id>  (Optional) The ID of the ontology to query"
  echo "  --deep         (Optional) Perform a deep search"
  exit 1
}

# Check for at least two arguments
if [ $# -lt 3 ]; then
  usage
fi

# Initialize variables
ols_url=""
output_dir=""
compare_dir=""
ontology_id=""
deep_search=0

# Process positional parameters
ols_url=$1
output_dir=$2
compare_dir=$3
shift 3

# Process optional parameters
while (( "$#" )); do
  case "$1" in
    --deep)
      deep_search=1
      shift
      ;;
    *)
      if [ -z "$ontology_id" ]; then
        ontology_id=$1
      else
        echo "Unexpected argument: $1"
        usage
      fi
      shift
      ;;
  esac
done

# Display the parsed parameters
echo "OLS URL: $ols_url"
echo "Output Directory: $output_dir"
echo "Compare Directory: $compare_dir"
echo "Ontology ID: $ontology_id"
echo "Deep Search: $deep_search"

#java -jar ./apitester4/target/apitester-1.0-SNAPSHOT-jar-with-dependencies.jar --url $ols_url --outDir $output_dir --compareDir $compare_dir --deep > ./apitester4.log

java -jar ./apitester4/target/apitester-1.0-SNAPSHOT-jar-with-dependencies.jar --url $ols_url --outDir $output_dir --compareDir $compare_dir --deep > ./apitester4.log
