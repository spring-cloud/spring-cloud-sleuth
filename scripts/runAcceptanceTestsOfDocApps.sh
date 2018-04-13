#!/bin/bash

set -o errexit

mkdir -p target

REPO_URL="https://github.com/spring-cloud-samples/sleuth-documentation-apps.git"
BRANCH_NAME="master"

pushd target
git clone "${REPO_URL}"

pushd sleuth-documentation-apps
./scripts/runAcceptanceTests.sh

popd
popd
