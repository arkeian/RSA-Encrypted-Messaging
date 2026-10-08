#!/usr/bin/env bash
set -eu

mkdir -p out/browser/classes

javac --release 17 -cp lib/zxing-core-3.5.4.jar -d out/browser/classes src/*.java

jar --create \
    --file out/browser/rsa-encrypted-messaging.jar \
    --main-class Main \
    -C out/browser/classes .
