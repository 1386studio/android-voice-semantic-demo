#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/test-classes
javac -encoding UTF-8 -d build/test-classes \
  app/src/main/java/com/example/voicesemantic/SemanticAgent.java \
  app/src/main/java/com/example/voicesemantic/PcmPreprocessor.java \
  tests/PcmPreprocessorTest.java \
  tests/SemanticAgentTest.java
java -cp build/test-classes SemanticAgentTest

java -cp build/test-classes com.example.voicesemantic.PcmPreprocessorTest
