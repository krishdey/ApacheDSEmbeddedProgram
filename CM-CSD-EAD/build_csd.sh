#!/usr/bin/env bash

if [ "$1" = "" ]; then
    echo "Usage: $0 <VERSION>"
    echo
    echo "Example: $0 1.0"
    exit 1
fi

set -ex

JARNAME=JPMISEAD-$1.jar

# validate service description
java -jar /Hadoop/git/CSD/cm_ext/validator/target/validator.jar -s ./csd-src/descriptor/service.sdl

jar -cvf ./parcel-and-csd/$JARNAME -C ./csd-src .
echo "Created $JARNAME"
