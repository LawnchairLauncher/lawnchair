#!/bin/bash

if [ $DRONE_BRANCH = "beta" ]; then
    BUILD_COMMAND="assembleAospWithQuickstepPieLawnchairPlahRelease"
    exit 1 # Not supported yet
else
    # TODO: switch to "optimized" variant
    BUILD_COMMAND_PIE="assembleAospWithQuickstepPieLawnchairCiDebug"
    BUILD_COMMAND_TEN="assembleAospWithQuickstepTenLawnchairCiDebug"
fi

OUT_DIR=out
APKS_OUT_DIR=$OUT_DIR/apks
MAPPINGS_OUT_DIR=$OUT_DIR/mappings
mkdir -p $APKS_OUT_DIR
mkdir -p $MAPPINGS_OUT_DIR

echo "Building for both targets"
bash ./gradlew $BUILD_COMMAND_PIE $BUILD_COMMAND_TEN

mv build/outputs/apk/*/*/*pie*.apk $APKS_OUT_DIR/Lawnchair-pie.apk
mv build/outputs/apk/*/*/*ten*.apk $APKS_OUT_DIR/Lawnchair-10.apk
# TODO: copy the mapping file once we enable ProGuard
#mv build/outputs/mapping/*/*/mapping.txt $MAPPINGS_OUT_DIR/mapping-pie.txt
