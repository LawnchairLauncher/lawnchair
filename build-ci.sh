#!/bin/bash

if [ $DRONE_BRANCH = "beta" ]; then
    BUILD_COMMAND="assembleAospWithQuickstepLawnchairPlahRelease"
    exit 1 # Not supported yet
else
    # TODO: switch to "optimized" variant
    BUILD_COMMAND="assembleAospWithQuickstepLawnchairCiDebug"
fi

OUT_DIR=out
APKS_OUT_DIR=$OUT_DIR/apks
MAPPINGS_OUT_DIR=$OUT_DIR/mappings
mkdir -p $APKS_OUT_DIR
mkdir -p $MAPPINGS_OUT_DIR

bash ./gradlew $BUILD_COMMAND

mv build/outputs/apk/*/*/*.apk $APKS_OUT_DIR/Lawnchair.apk
# TODO: copy the mapping file once we enable ProGuard
#mv build/outputs/mapping/*/*/mapping.txt $MAPPINGS_OUT_DIR/mapping.txt
