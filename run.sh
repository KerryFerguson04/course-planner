#!/bin/sh
# Compile and launch the course planner.
set -e
cd "$(dirname "$0")"
rm -rf out
javac -d out src/planner/*.java
java -cp out planner.Main catalog.json
