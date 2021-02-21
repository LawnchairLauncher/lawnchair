#!/bin/bash

TRACK=$DRONE_BRANCH

if [[ $TAG =~ ([0-9]+).([0-9]+)(-([a-z]+).([0-9]+))? ]]; then
    export TAG_MAJOR=${BASH_REMATCH[1]}
    export TAG_MINOR=${BASH_REMATCH[2]}
    TRACK=${BASH_REMATCH[4]}
    if [[ ! -z $TRACK ]]; then
        TRACK_NAME="$(tr '[:lower:]' '[:upper:]' <<< ${TRACK:0:1})${TRACK:1}"
        RELEASE_NO=${BASH_REMATCH[5]}
        export TAG_VERSION_NAME="${TAG_MAJOR}.${TAG_MINOR} ${TRACK_NAME} ${RELEASE_NO}"
    else
        TRACK="stable"
        export TAG_VERSION_NAME="${TAG_MAJOR}.${TAG_MINOR}"
    fi
else
    if [[ ! -z "$TAG" ]]; then
        echo "Invalid tag: $TAG"
        exit 1
    fi
fi

if [ $DRONE_BRANCH = "beta" ] || [ $DRONE_BRANCH = "stable" ]; then
    BUILD_COMMAND="assembleAospWithQuickstepLawnchairPlahRelease"
else
    BUILD_COMMAND="assembleAospWithQuickstepLawnchairCiRelease"
fi

OUT_DIR=out
APKS_OUT_DIR=$OUT_DIR/apks
MAPPINGS_OUT_DIR=$OUT_DIR/mappings
mkdir -p $APKS_OUT_DIR
mkdir -p $MAPPINGS_OUT_DIR

bash ./gradlew $BUILD_COMMAND

mv build/outputs/apk/*/*/*.apk $APKS_OUT_DIR/Lawnchair.apk
if [ -f build/outputs/mapping/*/*/mapping.txt ]; then
    mv build/outputs/mapping/*/*/mapping.txt $MAPPINGS_OUT_DIR/mapping.txt
fi
