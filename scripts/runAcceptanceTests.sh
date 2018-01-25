#!/bin/bash

set -o errexit

mkdir -p target

SCRIPT_URL="https://raw.githubusercontent.com/spring-cloud-samples/brewery/master/runAcceptanceTests.sh"
AT_WHAT_TO_TEST="SLEUTH"
BRANCH_NAME="master"

cd target

curl "${SCRIPT_URL}" --output runAcceptanceTests.sh

chmod +x runAcceptanceTests.sh

echo "Killing all running apps"
./runAcceptanceTests.sh -t "${AT_WHAT_TO_TEST}" -n -br "${BRANCH_NAME}"

./runAcceptanceTests.sh --whattotest "${AT_WHAT_TO_TEST}" --killattheend -br "${BRANCH_NAME}"
