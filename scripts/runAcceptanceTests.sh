#!/bin/bash

set -o errexit

mkdir -p target

SCRIPT_URL="https://raw.githubusercontent.com/spring-cloud-samples/brewery/master/acceptance-tests/scripts/runDockerAcceptanceTests.sh"
AT_WHAT_TO_TEST="SLEUTH"

curl "${SCRIPT_URL}" --output target/runDockerAcceptanceTests.sh

cd target

chmod +x runDockerAcceptanceTests.sh

./runDockerAcceptanceTests.sh -t "${AT_WHAT_TO_TEST}"
(cd brewery; docker-compose -f docker-compose-"${AT_WHAT_TO_TEST}".yml kill)

AT_WHAT_TO_TEST="SLEUTH_STREAM"

./runDockerAcceptanceTests.sh -t "${AT_WHAT_TO_TEST}"
