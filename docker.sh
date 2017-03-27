#!/bin/sh

# variables that can be set
# DEBUG   : set to echo to print command and not execute
# PUSH    : set to push to push, anthing else not to push. If not set
#           the program will push if master or develop.
# PROJECT : the project to add to the image, default is clowder

#DEBUG=echo
#BUILT=doit

# set default for clowder
PROJECT=${PROJECT:-"clowder"}

# copy dist file to docker folder
ZIPFILE=$( /bin/ls -1rt target/universal/clowder-*.zip 2>/dev/null | tail -1 )
if [ "$ZIPFILE" = "" -o ! "$BUILT" = ""  ]; then
  echo "Running ./sbt dist"
  ./sbt dist
  ZIPFILE=$( /bin/ls -1rt target/universal/clowder-*.zip 2>/dev/null | tail -1 )
  if [ "$ZIPFILE" = "" ]; then
    exit -1
  fi
fi
${DEBUG} rm -rf docker/files
${DEBUG} mkdir -p docker/files
${DEBUG} unzip -q -d docker ${ZIPFILE}
${DEBUG} mv docker/$( basename ${ZIPFILE} .zip ) docker/files/clowder

# find version if we are develop/latest/release and if should be pushed
BRANCH="$(git rev-parse --abbrev-ref HEAD)"
VERSION=${VERSION:-""}
if [ "$VERSION" = "" ]; then
  if [ "$BRANCH" = "master" ]; then
    PUSH=${PUSH:-"push"}
    VERSION="latest"
  elif [ "$BRANCH" = "develop" ]; then
    PUSH=${PUSH:-"push"}
    VERSION="develop"
  elif [ "$( echo $BRANCH | sed -e 's#^release/.*$#release#')" = "release" ]; then
    PUSH=${PUSH:-"push"}
    VERSION="$( echo $BRANCH | sed -e 's#^release/\(.*\)$#\1#' )"
  else
    PUSH=${PUSH:-""}
    VERSION="local"
  fi
else
  PUSH=${PUSH:-""}
fi

# create image using temp id
${DEBUG} docker build --pull --tag clowder-$$ docker
if [ $? -ne 0 ]; then
  echo "FAILED build of clowder"
  exit -1
fi

${DEBUG} docker build --pull --tag toolserver-$$ scripts/toollaunchservice
if [ $? -ne 0 ]; then
  echo "FAILED build of toolserver"
  exit -1
fi

${DEBUG} docker build --pull --tag mongo-init-$$ scripts/mongo-init
if [ $? -ne 0 ]; then
  echo "FAILED build of mongo-init"
  exit -1
fi


# tag all versions and push if need be
for v in $VERSION; do
  if [ "$PROJECT" = "" ]; then
    ${DEBUG} docker tag clowder-$$ clowder:${v}
    ${DEBUG} docker tag toolserver-$$ toolserver:${v}
    ${DEBUG} docker tag mongo-init-$$ mongo-init:${v}
    echo "Tagged clowder and toolserver and mongo-init with ${v}"
  else
    for p in ${PROJECT}; do
      ${DEBUG} docker tag clowder-$$ ${p}/clowder:${v}
      ${DEBUG} docker tag toolserver-$$ ${p}/toolserver:${v}
      ${DEBUG} docker tag mongo-init-$$ ${p}/mongo-init:${v}
      echo "Tagged clowder toolserver with ${v}"
      echo "Tagged clowder mongo-init with ${v}"
      if [ ! -z "$PUSH" ]; then
        ${DEBUG} docker push ${p}/clowder:${v}
        ${DEBUG} docker push ${p}/toolserver:${v}
        ${DEBUG} docker push ${p}/mongo-init:${v}
        echo "Pushed clowder and toolserver and mongo-init to dockerhub $p"
      fi
    done
  fi
done

# cleanup
${DEBUG} docker rmi toolserver-$$ clowder-$$ mongo-init-$$
${DEBUG} rm -rf docker/files
