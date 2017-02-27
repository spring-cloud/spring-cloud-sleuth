#!/bin/bash

echo "Running JMH Benchmarks"
./mvnw clean install -DskipTests --projects benchmarks --also-make -Pbenchmarks,jmh
java -Djmh.ignoreLock=true -jar benchmarks/target/benchmarks.jar org.springframework.cloud.sleuth.benchmarks.jmh.* -rf csv -rff jmh-result.csv | tee target/benchmarks.log