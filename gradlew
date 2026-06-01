#!/bin/sh
#
# Gradle start up script for UN*X
#
APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
APP_HOME="`pwd -P`"

MAX_FD="maximum"
warn () { echo "$*"; }
die () { echo; echo "$*"; echo; exit 1; }

OS_NAME=`uname`
case $OS_NAME in
  Darwin* ) IS_DARWIN=1 ;;
  CYGWIN* ) IS_CYGWIN=1 ;;
  MSYS* | MINGW* ) IS_MSYS=1 ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

org_gradle_jvm_opts=""

set_jvm_opts () {
  org_gradle_jvm_opts="$org_gradle_jvm_opts $1"
}

exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $org_gradle_jvm_opts \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
