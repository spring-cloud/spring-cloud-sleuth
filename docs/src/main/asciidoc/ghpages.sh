#!/bin/bash -x

#git remote set-url --push origin `git config remote.origin.url | sed -e 's/^git:/https:/'`

if ! (git remote set-branches --add origin gh-pages && git fetch -q); then
    echo "No gh-pages, so not syncing"
    exit 0
fi

if ! [ -d docs/target/generated-docs ]; then
    echo "No gh-pages sources in docs/target/generated-docs, so not syncing"
    exit 0
fi

# The script should be executed from the root folder

ROOT_FOLDER=`pwd`
echo "Current folder is ${ROOT_FOLDER}"

if [[ ! -e "${ROOT_FOLDER}/.git" ]]; then
    echo "You're not in the root folder of the project!"
    exit 1
fi

# Retrieve version number, name of the main adoc and name of the current branch
###################################################################

# Code grepping for the 1st presence of "version>" in pom.xml.
# First one is project version, second parent version.
VERSION_NODE=`awk '/version>/{i++}i==1{print; exit}' $ROOT_FOLDER/pom.xml`
# Extract the contents of the version node
VERSION_VALUE=$(sed -ne '/version/{s/.*<version>\(.*\)<\/version>.*/\1/p;q;}' <<< "$VERSION_NODE")
echo "Extracted version from root pom.xml is [${VERSION_VALUE}]"

# Code grepping for the 2nd presence of "version>" in pom.xml.
# First one is parent, second project version.
MAIN_ADOC_NODE=`awk '/docs.main/{i++}i==1{print; exit}' $ROOT_FOLDER/docs/pom.xml`
# Extract the contents of the version node
MAIN_ADOC_VALUE=$(sed -ne '/docs.main/{s/.*<docs.main>\(.*\)<\/docs.main>.*/\1/p;q;}' <<< "$MAIN_ADOC_NODE")
echo "Extracted version from docs pom.xml is [${MAIN_ADOC_VALUE}]"

# Code getting the name of the current branch. For master we want to publish as we did until now
# http://stackoverflow.com/questions/1593051/how-to-programmatically-determine-the-current-checked-out-git-branch
CURRENT_BRANCH=$(git symbolic-ref -q HEAD)
CURRENT_BRANCH=${CURRENT_BRANCH##refs/heads/}
CURRENT_BRANCH=${CURRENT_BRANCH:-HEAD}
echo "Current branch is [${CURRENT_BRANCH}]"

# Stash any outstanding changes
###################################################################
git diff-index --quiet HEAD
dirty=$?
if [ "$dirty" != "0" ]; then git stash; fi

# Switch to gh-pages branch to sync it with master
###################################################################
git checkout gh-pages
git pull origin gh-pages

# Add git branches
###################################################################
mkdir -p ${ROOT_FOLDER}/${VERSION_VALUE}
if [[ "${CURRENT_BRANCH}" == "master" ]] ; then
    echo -e "Current branch is master - will copy the current docs also to [${VERSION_VALUE}] folder"
    for f in docs/target/generated-docs/*; do
        file=${f#docs/target/generated-docs/*}
        if ! git ls-files -i -o --exclude-standard --directory | grep -q ^$file$; then
            # Not ignored...
            cp -rf $f ${ROOT_FOLDER}/
            cp -rf $f ${ROOT_FOLDER}/${VERSION_VALUE}
            # We want users to access 1.0.0.RELEASE/ instead of 1.0.0.RELEASE/spring-cloud.sleuth.html
            if [[ "${file}" == "${MAIN_ADOC_VALUE}" ]] ; then
                ln -s ${ROOT_FOLDER}/${VERSION_VALUE}/index.html ${ROOT_FOLDER}/${VERSION_VALUE}/${MAIN_ADOC_VALUE}.adoc
                git add -A ${ROOT_FOLDER}/${VERSION_VALUE}/index.html
            fi
            git add -A $file
            git add -A ${ROOT_FOLDER}/${VERSION_VALUE}/$file
        fi
    done
else
    echo -e "Current branch is [${CURRENT_BRANCH}] - will copy the current docs ONLY to [${VERSION_VALUE}] folder"
    for f in docs/target/generated-docs/*; do
        file=${f#docs/target/generated-docs/*}
        if ! git ls-files -i -o --exclude-standard --directory | grep -q ^$file$; then
            # Not ignored...
            cp -rf $f ${ROOT_FOLDER}/${VERSION_VALUE}
            if [[ "${file}" == "${MAIN_ADOC_VALUE}" ]] ; then
                ln -s ${ROOT_FOLDER}/${VERSION_VALUE}/index.html ${ROOT_FOLDER}/${VERSION_VALUE}/${MAIN_ADOC_VALUE}.adoc
                git add -A ${ROOT_FOLDER}/${VERSION_VALUE}/index.html
            fi
            git add -A ${ROOT_FOLDER}/${VERSION_VALUE}/$file
        fi
    done
fi

git commit -a -m "Sync docs from ${CURRENT_BRANCH} to gh-pages"

# Uncomment the following push if you want to auto push to
# the gh-pages branch whenever you commit to master locally.
# This is a little extreme. Use with care!
###################################################################
git push origin gh-pages

# Finally, switch back to the master branch and exit block
git checkout ${CURRENT_BRANCH}
if [ "$dirty" != "0" ]; then git stash pop; fi

exit 0
