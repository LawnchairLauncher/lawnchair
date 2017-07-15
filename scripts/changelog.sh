MERGE_PREFIX="Merge pull request"
NEWLINE="
"

CHANGELOG=" <b>Changelog for build #${TRAVIS_BUILD_NUMBER}</b>${NEWLINE}"

while read -r line; do
  if [[ $line != ${MERGE_PREFIX}* ]] ;
  then
    CHANGELOG="${CHANGELOG}- ${line}${NEWLINE}"
  fi
done <<< "$(git log --format=%s $TRAVIS_COMMIT_RANGE)"

echo "${CHANGELOG}"