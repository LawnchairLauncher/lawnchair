#!/bin/bash

if [ $DRONE_BRANCH = "beta" ]; then
    BUILD_COMMAND="assembleAospWithQuickstepPieLawnchairPlahRelease"
else
    # TODO: switch to "optimized" variant
    BUILD_COMMAND="assembleAospWithQuickstepPieLawnchairCiDebug"
fi

echo "Running $BUILD_COMMAND"
bash ./gradlew $BUILD_COMMAND
