#!/bin/bash

set -o errexit

mkdir -p target

SCRIPT_URL="https://raw.githubusercontent.com/spring-cloud-samples/brewery/master/acceptance-tests/scripts/runDockerAcceptanceTests.sh"
AT_WHAT_TO_TEST="SLEUTH"
AT_VERSION="1.0.0.BUILD-SNAPSHOT"

curl "${SCRIPT_URL}" --output target/runDockerAcceptanceTests.sh

cd target

chmod +x runDockerAcceptanceTests.sh

./runDockerAcceptanceTests.sh -t "${AT_WHAT_TO_TEST}" -v "${AT_VERSION}"

AT_WHAT_TO_TEST="SLEUTH_ZIPKIN"

./runDockerAcceptanceTests.sh -t "${AT_WHAT_TO_TEST}" -v "${AT_VERSION}"
