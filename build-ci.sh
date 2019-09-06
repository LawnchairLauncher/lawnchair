#!/bin/bash

if [ $DRONE_BRANCH = "beta" ]; then
    BUILD_COMMAND="assembleQuickstepLawnchairPlahRelease"
else
    BUILD_COMMAND="assembleQuickstepLawnchairCiOptimized"
fi

echo "Running $BUILD_COMMAND"
bash ./gradlew $BUILD_COMMAND
