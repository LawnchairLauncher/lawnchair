#!/usr/bin/env sh

# Uncomment only for debugging purposes
#MAJOR_MINOR=1
#TRAVIS_BUILD_NUMBER=655

APP_VERSION=$MAJOR_MINOR.$TRAVIS_BUILD_NUMBER
mkdir -p $APP_VERSION

# Fix commit range for tagged builds
if [ ! -z "$TRAVIS_TAG" ]
then
    COMMIT_RANGE="$(git describe --abbrev=0 --tags $TRAVIS_TAG^)..$TRAVIS_TAG"
fi


# Upload Lawnchair signed release apk
cp app/build/outputs/apk/release/app-release.apk $APP_VERSION/Lawnchair-$APP_VERSION.apk
./scripts/s3-upload.sh $APP_VERSION/Lawnchair-$APP_VERSION.apk $S3_BUCKET $S3_HOST $S3_KEY $S3_SECRET

# Upload calculated md5 checksum for app-release.apk
md5sum $(readlink -f app/build/outputs/apk/release/app-release.apk) > $APP_VERSION/Lawnchair-$APP_VERSION.md5sum
./scripts/s3-upload.sh $APP_VERSION/Lawnchair-$APP_VERSION.md5sum $S3_BUCKET $S3_HOST $S3_KEY $S3_SECRET

# Upload proguard file
cp app/build/outputs/mapping/release/mapping.txt $APP_VERSION/proguard-$MAJOR_MINOR.$TRAVIS_BUILD_NUMBER.txt
./scripts/s3-upload.sh $APP_VERSION/proguard-$MAJOR_MINOR.$TRAVIS_BUILD_NUMBER.txt $S3_BUCKET $S3_HOST $S3_KEY $S3_SECRET


# Check if changes have been committed to Lawnfeed
CHANGELOG="$(git diff --name-only -r $COMMIT_RANGE lawnfeed)"
if [ -z "$CHANGELOG" ]
then
    # No need to deploy a new Lawnfeed build when no changes have been made
    exit
fi

# Upload Lawnfeed signed debug apk
cp lawnfeed/build/outputs/apk/debug/lawnfeed-debug.apk $APP_VERSION/Lawnfeed-$APP_VERSION.apk
./scripts/s3-upload.sh $APP_VERSION/Lawnfeed-$APP_VERSION.apk $S3_BUCKET $S3_HOST $S3_KEY $S3_SECRET

# Upload calculated md5 checksum for lawnfeed-debug.apk
md5sum $(readlink -f lawnfeed/build/outputs/apk/debug/lawnfeed-debug.apk) > $APP_VERSION/Lawnfeed-$APP_VERSION.md5sum
./scripts/s3-upload.sh $APP_VERSION/Lawnfeed-$APP_VERSION.md5sum $S3_BUCKET $S3_HOST $S3_KEY $S3_SECRET

# Upload version info
echo "{\"major_minor\": \""$MAJOR_MINOR"\", \"travis_build_number\": \""$TRAVIS_BUILD_NUMBER"\", \"app_version\": \""$APP_VERSION"\"}" > version.json
./scripts/s3-upload.sh version.json $S3_BUCKET $S3_HOST $S3_KEY $S3_SECRET
