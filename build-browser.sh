#!/usr/bin/env bash
set -eu

mkdir -p out/browser/classes
mkdir -p dist/out/browser dist/lib

javac --release 17 -cp lib/zxing-core-3.5.4.jar -d out/browser/classes src/*.java

jar --create \
    --file out/browser/rsa-encrypted-messaging.jar \
    --main-class Main \
    -C out/browser/classes .

cp index.html dist/index.html
cp out/browser/rsa-encrypted-messaging.jar dist/out/browser/rsa-encrypted-messaging.jar
cp lib/zxing-core-3.5.4.jar dist/lib/zxing-core-3.5.4.jar
