#!/bin/bash
set -ev

BRANCH="${GITHUB_REF##*/}"

getVersion() {
  ./gradlew properties -q | grep -E "^version" | awk '{print $2}' | tr -d '[:space:]'
}

removeSnapshots() {
  sed -i 's/-SNAPSHOT//' gradle.properties
}

commitRelease() {
  local APP_VERSION
  APP_VERSION=$(getVersion)
  git commit -a -m "Update version for release"
  git tag -a "v${APP_VERSION}" -m "Tag release version"
}

# bumpVersion() {
#   echo "Bump version number"
#   local APP_VERSION
#   APP_VERSION=15.1.1-jahia #$(getVersion | xargs)
#   local SEMANTIC_REGEX='^([0-9]+)\.([0-9]+)(\.([0-9]+))?-jahia$'
#   echo "semantic regex: ${SEMANTIC_REGEX}"
#   if [[ ${APP_VERSION} =~ ${SEMANTIC_REGEX} ]]; then
#     if [[ ${BASH_REMATCH[4]} ]]; then
#       nextVersion=$((BASH_REMATCH[4] + 1))
#       nextVersion="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}.${nextVersion}-jahia-SNAPSHOT"
#     else
#       nextVersion=$((BASH_REMATCH[2] + 1))
#       nextVersion="${BASH_REMATCH[1]}.${nextVersion}-jahia-SNAPSHOT"
#     fi

#     echo "Next version: ${nextVersion}"
#     sed -i -E "s/^version(\s)?=.*/version=${nextVersion}/" gradle.properties
#     git commit -a -m "Bumped version for next release"
#   else
#     echo "No semantic version and therefore cannot publish to maven repository: '${APP_VERSION}'"
#   fi
# }

echo "Deploying release to Maven Central"
removeSnapshots
commitRelease
# bumpVersion
git push --follow-tags
