#!/bin/sh
set -e

curl https://raw.githubusercontent.com/spring-cloud-samples/brewery/master/acceptance-tests/scripts/runDockerAcceptanceTests.sh --output runDockerAcceptanceTests.sh
sh runDockerAcceptanceTests.sh -t=SLEUTH -v=1.0.0.BUILD-SNAPSHOT