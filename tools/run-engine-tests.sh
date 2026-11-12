#!/bin/sh
# Compiles the plain-Java engine and its tests and runs them against a tiny JUnit stand-in.
# Use this only where the real JUnit jar is unavailable. Run from the project root.
set -e
OUT=build/engine-test
rm -rf "$OUT" && mkdir -p "$OUT"
JAVAC="java -m jdk.compiler/com.sun.tools.javac.Main"
$JAVAC -d "$OUT" tools/standin/org/junit/*.java \
  app/src/main/java/com/yash/upsc400v2/engine/*.java \
  app/src/test/java/com/yash/upsc400v2/engine/*.java
cd app
CLASSES=$(cd ../app/src/test/java && find . -name '*Test.java' | sed 's#^\./##; s#\.java$##; s#/#.#g' | tr '\n' ' ')
java -cp "../$OUT" org.junit.Runner $CLASSES
