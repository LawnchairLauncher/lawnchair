#!/usr/bin/env sh

if [ ! -z "$TRAVIS_TAG" ]
then
    TRAVIS_COMMIT_RANGE="$(git describe --abbrev=0 --tags $TRAVIS_TAG^)..$TRAVIS_TAG"
fi

GIT_COMMIT_LOG="$(git log --format='%s (by %cn)' $TRAVIS_COMMIT_RANGE)"
printf '%s\n' "$GIT_COMMIT_LOG" | while IFS= read -r line
do
    echo "- ${line}"
done
