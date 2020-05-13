#!/usr/bin/env sh

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

cd $DIR/../..
./gradlew install --parallel

cd $DIR
./gradlew -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.debug=true "$@"