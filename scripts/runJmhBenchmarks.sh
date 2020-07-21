#!/bin/bash

echo "Running JMH Benchmarks"
./mvnw clean install -Pbenchmarks -pl benchmarks --also-make -DskipTests && ./mvnw verify -Djmh.mbr.report.publishTo=csv:http.csv -pl benchmarks -Pbenchmarks
# java -Djmh.ignoreLock=true -jar benchmarks/target/benchmarks.jar org.springframework.cloud.sleuth.benchmarks.jmh.* -rf csv -rff jmh-result.csv | tee target/benchmarks.log
