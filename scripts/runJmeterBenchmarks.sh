#!/bin/bash

echo "Killing the remaining apps - if something went wrong previously"
pkill -f SleuthBenchmarkingSpringApp || echo "No apps to kill"

echo "Running JMeter Benchmarks"
./mvnw clean verify --projects benchmarks --also-make -Pbenchmarks,jmeter
echo "Killing the remaining apps - if something went wrong after the tests"
pkill -f SleuthBenchmarkingSpringApp || echo "No apps to kill"