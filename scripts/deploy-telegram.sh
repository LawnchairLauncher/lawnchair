#!/usr/bin/env sh

cp app/build/outputs/apk/debug/app-debug.apk Lawnchair-$TRAVIS_BUILD_NUMBER.apk
# cp app/build/outputs/mapping/debug/mapping.txt proguard-$TRAVIS_BUILD_NUMBER.txt

curl -F chat_id="-1001083653933" -F document=@"Lawnchair-$TRAVIS_BUILD_NUMBER.apk" https://api.telegram.org/bot$BOT_TOKEN/sendDocument
curl -F chat_id="-1001083653933" -F text="$(./scripts/changelog.sh)" -F parse_mode="HTML" https://api.telegram.org/bot$BOT_TOKEN/sendMessage
# curl -F chat_id="152559768" -F document=@"proguard-$TRAVIS_BUILD_NUMBER.txt" https://api.telegram.org/bot$BOT_TOKEN/sendDocument