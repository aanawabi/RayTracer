#!/bin/bash
mkdir -p out
javac -d out $(find src -name "*.java")
echo "Compiled successfully."