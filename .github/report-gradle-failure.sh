#!/usr/bin/env bash
# Trasforma gli errori di un log di Gradle in un'unica annotazione del check, leggibile dall'API di
# GitHub anche quando il log non è scaricabile. Uso: report-gradle-failure.sh <log>
set +e
log=$1
{
  sed -n '/What went wrong/,/Try:/p' "$log" | head -40
  grep -E "^(e: |.*\.(java|kt):[0-9]+: error)" "$log" | head -15
  grep -E "FAILED$|AssertionError|ComparisonFailure|expected:<|Exception:" "$log" | head -25
} | sed -e 's/\x1b\[[0-9;]*m//g' -e 's/::/: :/g' > failure.txt
msg=$(sed -e 's/%/%25/g' failure.txt | awk 'BEGIN{ORS="%0A"} {print}')
echo "::error title=Gradle::${msg:0:6000}"
exit 0
