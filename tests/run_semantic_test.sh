#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/test-classes
javac -encoding UTF-8 -d build/test-classes \
  app/src/main/java/com/example/voicesemantic/SemanticAgent.java \
  app/src/main/java/com/example/voicesemantic/ActionCanonicalizer.java \
  app/src/main/java/com/example/voicesemantic/DomainGrammar.java \
  app/src/main/java/com/example/voicesemantic/PcmPreprocessor.java \
  app/src/main/java/com/example/voicesemantic/PcmWavWriter.java \
  app/src/main/java/com/example/voicesemantic/CerMetrics.java \
  app/src/main/java/com/example/voicesemantic/SpeechChunker.java \
  app/src/main/java/com/example/voicesemantic/ShortCommandHints.java \
  tests/SpeechChunkerTest.java \
  tests/ShortCommandHintsTest.java \
  tests/PcmPreprocessorTest.java \
  tests/PcmWavWriterTest.java \
  tests/CerMetricsTest.java \
  tests/SemanticAgentTest.java \
  tests/ActionCanonicalizerTest.java \
  tests/DomainGrammarTest.java
java -cp build/test-classes SemanticAgentTest

java -cp build/test-classes com.example.voicesemantic.PcmPreprocessorTest

java -cp build/test-classes PcmWavWriterTest
java -cp build/test-classes CerMetricsTest

java -cp build/test-classes com.example.voicesemantic.ActionCanonicalizerTest

java -cp build/test-classes com.example.voicesemantic.DomainGrammarTest

java -cp build/test-classes com.example.voicesemantic.SpeechChunkerTest
java -cp build/test-classes com.example.voicesemantic.ShortCommandHintsTest
