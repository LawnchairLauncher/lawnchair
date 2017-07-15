MERGE_PREFIX="Merge pull request"
NEWLINE="
"

CHANGELOG=""

while read -r line; do
  if [[ $line != ${MERGE_PREFIX}* ]] ;
  then
    CHANGELOG="${CHANGELOG}- ${line}${NEWLINE}"
  fi
done <<< "$(git log --format=%s $TRAVIS_COMMIT_RANGE)"

echo "${CHANGELOG}"