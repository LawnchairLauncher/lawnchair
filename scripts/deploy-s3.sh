#!/usr/bin/env sh

# Uncomment only for debugging purposes
#MAJOR_MINOR=1
#TRAVIS_BUILD_NUMBER=655


# Upload Lawnchair signed release apk
cp app/build/outputs/apk/release/app-release.apk Lawnchair-$MAJOR_MINOR.$TRAVIS_BUILD_NUMBER.apk
./scripts/s3-upload.sh Lawnchair-$MAJOR_MINOR.$TRAVIS_BUILD_NUMBER.apk $S3_BUCKET $S3_HOST $S3_KEY $S3_SECRET

# Upload calculated md5 checksum for app-release.apk
md5sum $(readlink -f app/build/outputs/apk/release/app-release.apk) > Lawnchair-$MAJOR_MINOR.$TRAVIS_BUILD_NUMBER.md5sum
./scripts/s3-upload.sh Lawnchair-$MAJOR_MINOR.$TRAVIS_BUILD_NUMBER.md5sum $S3_BUCKET $S3_HOST $S3_KEY $S3_SECRET

# Upload Lawnfeed signed debug apk
cp lawnfeed/build/outputs/apk/debug/lawnfeed-debug.apk Lawnfeed-$MAJOR_MINOR.$TRAVIS_BUILD_NUMBER.apk
./scripts/s3-upload.sh Lawnfeed-$MAJOR_MINOR.$TRAVIS_BUILD_NUMBER.apk $S3_BUCKET $S3_HOST $S3_KEY $S3_SECRET

# Upload calculated md5 checksum for lawnfeed-debug.apk
md5sum $(readlink -f lawnfeed/build/outputs/apk/debug/lawnfeed-debug.apk) > Lawnfeed-$MAJOR_MINOR.$TRAVIS_BUILD_NUMBER.md5sum
./scripts/s3-upload.sh Lawnfeed-$MAJOR_MINOR.$TRAVIS_BUILD_NUMBER.md5sum $S3_BUCKET $S3_HOST $S3_KEY $S3_SECRET
