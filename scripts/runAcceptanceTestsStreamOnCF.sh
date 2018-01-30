#!/bin/bash

set -o errexit

mkdir -p target

SCRIPT_URL="https://raw.githubusercontent.com/spring-cloud-samples/brewery/master/runAcceptanceTests.sh"
AT_WHAT_TO_TEST="SLEUTH"
BRANCH_NAME="master"

cd target

curl "${SCRIPT_URL}" --output runAcceptanceTests.sh

chmod +x runAcceptanceTests.sh

./runAcceptanceTests.sh --whattotest "${AT_WHAT_TO_TEST}" --usecloudfoundry --reset -br "${BRANCH_NAME}"
