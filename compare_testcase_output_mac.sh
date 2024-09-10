#!/usr/bin/env bash
diff -r -q -a -B -w --strip-trailing-cr --exclude=.gitkeep testcases_output/testcases testcases_expected_output/ > testcases_compare_result.log