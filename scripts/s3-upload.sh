#!/usr/bin/env sh
# Upload files to custom S3 using curl
# Example: ./s3-upload.sh play.minio.io example.txt bucket Q3AM3UQ867SPQQA43P2F zuf+tfteSlswRu7BJ86wekitnifILbZam1KYY3TG

HOST=$3

FILE=$1
BUCKET=$2
RESOURCE="/${BUCKET}/${FILE}"
CONTENT_TYPE=$(mimetype -b $FILE)
TIMESTAMP=$(date -R)
STRING_TO_SIGN="PUT\n\n${CONTENT_TYPE}\n${TIMESTAMP}\n${RESOURCE}"

S3_KEY=$4
S3_SECRET=$5

SIGNATURE=$(echo -n ${STRING_TO_SIGN} | openssl sha1 -hmac ${S3_SECRET} -binary | base64)
curl -X PUT -T "${FILE}" \
    -H "Host: ${HOST}" \
    -H "Date: ${TIMESTAMP}" \
    -H "Content-Type: ${CONTENT_TYPE}" \
    -H "Authorization: AWS ${S3_KEY}:${SIGNATURE}" \
    https://${HOST}${RESOURCE}
