#!/bin/bash

echo "Running JMH Benchmarks"
ROOT="$(pwd)"
JMH_RESULT_FILE_PARENT="${ROOT}/target"
mkdir -p "${JMH_RESULT_FILE_PARENT}"
JMH_RESULT_FILE="${JMH_RESULT_FILE_PARENT}/jmh-result.csv"
echo "Will produce results under [${JMH_RESULT_FILE}]"
./mvnw clean install -Pbenchmarks -pl benchmarks --also-make -DskipTests && ./mvnw verify -DpublishTo=csv:"${JMH_RESULT_FILE}" -Djmh.mbr.report.publishTo=csv:"${JMH_RESULT_FILE}"  -pl benchmarks -Pbenchmarks

# java -Djmh.ignoreLock=true -jar benchmarks/target/benchmarks.jar org.springframework.cloud.sleuth.benchmarks.jmh.* -rf csv -rff jmh-result.csv | tee target/benchmarks.log
