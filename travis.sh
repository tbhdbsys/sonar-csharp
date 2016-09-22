#!/bin/bash

set -euo pipefail

function installTravisTools {
  mkdir ~/.local
  curl -sSL https://github.com/SonarSource/travis-utils/tarball/v28 | tar zx --strip-components 1 -C ~/.local
  source ~/.local/bin/install
}

installTravisTools

#change analyzer version if available as an environment variable
if [ -n "${ANALYZER_VERSION:-}" ]; then
	echo sonarAnalyzer.version: $ANALYZER_VERSION
	sed -i -E "s/<sonarAnalyzer.version>[^<]*</<sonarAnalyzer.version>$ANALYZER_VERSION</g" pom.xml 
fi

regular_mvn_build_deploy_analyze
