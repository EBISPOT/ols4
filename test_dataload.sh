#!/usr/bin/env bash

TEST_CONFIGS=$(find testcases | grep json)

rm -rf testcases_output/*
mkdir testcases_output

export OLS_ORCID_NAME_FIXTURE="${OLS_ORCID_NAME_FIXTURE:-$(pwd)/dev-testing/orcid-name-fixture.json}"

for f in $TEST_CONFIGS
do

BASENAME=$(basename $f .json)
DIRNAME=$(basename $(dirname $f))

TEST_FOLDER=$DIRNAME/$BASENAME
mkdir -p ./testcases_output/$DIRNAME
mkdir -p ./testcases_output/$TEST_FOLDER

./dev-testing/create_datafiles.sh $f ./testcases_output/$TEST_FOLDER --loadLocalFiles --noDates
done

# Compare outputs against the expected fixtures. JSON files are compared by
# normalized *content* (key/array order preserved, whitespace normalized) rather
# than byte-for-byte, because the Rust rdf2json port emits valid JSON via
# serde_json whose formatting differs from the Java/Gson output while the content
# is identical. Binary files (.pgbin) are still compared byte-for-byte.
normalize_tree() {
    local src="$1" dst="$2"
    rm -rf "$dst"; mkdir -p "$dst"
    while IFS= read -r -d '' f; do
        local rel="${f#"$src"/}"
        mkdir -p "$dst/$(dirname "$rel")"
        if [[ "$f" == *.json ]]; then
            python3 -m json.tool "$f" > "$dst/$rel" 2>/dev/null || cp "$f" "$dst/$rel"
        else
            cp "$f" "$dst/$rel"
        fi
    done < <(find "$src" -type f ! -name .gitkeep -print0)
}

NORM_OUT=$(mktemp -d)
NORM_EXP=$(mktemp -d)
normalize_tree testcases_output "$NORM_OUT"
normalize_tree testcases_expected_output "$NORM_EXP"
diff --recursive "$NORM_OUT" "$NORM_EXP"
DIFF_STATUS=$?
rm -rf "$NORM_OUT" "$NORM_EXP"
exit $DIFF_STATUS



