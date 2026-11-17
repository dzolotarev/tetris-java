#!/usr/bin/env bash
# Компиляция и запуск Тетриса
set -e
cd "$(dirname "$0")"
rm -rf out
javac -d out src/main/java/tetris/*.java
java -cp out tetris.Game
