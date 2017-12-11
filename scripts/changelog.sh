#!/usr/bin/env sh
MERGE_PREFIX="Merge pull request"

if [ ! -z "$TRAVIS_TAG" ]
then
    TRAVIS_COMMIT_RANGE="$(git describe --abbrev=0 --tags $TRAVIS_TAG^)..$TRAVIS_TAG"
fi

GIT_COMMIT_LOG="$(git log --format='%s (by %cn)' $TRAVIS_COMMIT_RANGE)"

echo " <b>Changelog for build ${MAJOR_MINOR}.${TRAVIS_BUILD_NUMBER}</b>${NEWLINE}"

printf '%s\n' "$GIT_COMMIT_LOG" | while IFS= read -r line
do
  echo "- ${line}"
done