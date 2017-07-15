#!/usr/bin/env sh

# skipping for now
exit 0

BRANCH=$1

URLBASE="https://api.mobile.azure.com/v0.1/apps/$MOBILE_CENTER_USER/$MOBILE_CENTER_APP"

RESP1=$(curl -X POST --header 'Content-Type: application/json' --header 'Accept: application/json' --header "X-API-Token: $MOBILE_CENTER_API_TOKEN" "$URLBASE/release_uploads")

UPLOADURL=$(python -c "import sys, json; print json.loads($RESP1)['upload_url']")
UPLOADID=$(python -c "import sys, json; print json.loads($RESP1)['upload_id']")

curl -F "ipa=@$APKPATH" $UPLOADURL
RESP2=$(curl -X PATCH --header 'Content-Type: application/json' --header 'Accept: application/json' --header "X-API-Token: $MOBILE_CENTER_API_TOKEN" -d '{ "status": "committed" }' "$URLBASE/release_uploads/$UPLOADID")

RELEASEURL=$(python -c "import sys, json; print json.loads($RESP2)['release_url']")

curl -X PATCH --header 'Content-Type: application/json' --header 'Accept: application/json' --header "X-API-Token: $MOBILE_CENTER_API_TOKEN" -d "{ \"destination_name\": \"$BRANCH\", \"release_notes\": \"$TRAVIS_COMMIT_MESSAGE\" }" "$RELEASEURL"

