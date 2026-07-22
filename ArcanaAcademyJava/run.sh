#!/bin/bash
# Arcana Academy — compile & run (Mac / Linux)
cd "$(dirname "$0")"
mkdir -p bin
javac -d bin src/arcana/*.java && java -cp bin arcana.ArcanaApp
