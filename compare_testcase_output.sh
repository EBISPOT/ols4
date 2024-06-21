#!/usr/bin/env bash
diff --recursive --brief --text --ignore-tab-expansion --ignore-trailing-space --strip-trailing-cr \
--exclude=.gitkeep testcases_output/testcases testcases_expected_output/ > testcases_compare_result.log

