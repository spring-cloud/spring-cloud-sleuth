#!/bin/bash

set -o errexit

SCRIPT_URL="https://raw.githubusercontent.com/spring-cloud-samples/brewery/master/acceptance-tests/scripts/runDockerAcceptanceTests.sh"
AT_WHAT_TO_TEST="SLEUTH"
AT_VERSION="1.0.0.BUILD-SNAPSHOT"

curl "${SCRIPT_URL}" --output runDockerAcceptanceTests.sh

chmod +x runDockerAcceptanceTests.sh

./runDockerAcceptanceTests.sh -t "${AT_WHAT_TO_TEST}" -v "${AT_VERSION}"
