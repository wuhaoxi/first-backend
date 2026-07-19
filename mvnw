#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup batch script, version 3.3.2
#
# Environment Variable Prerequisites
#
#   JAVA_HOME       Must point at your Java Development Kit installation.
#   MAVEN_OPTS      (Optional) Java runtime options used when Maven is executed.
#   MAVEN_SKIP_RC   (Optional) Flag to disable loading of mavenrc files.
# ----------------------------------------------------------------------------

if [ -z "$MAVEN_SKIP_RC" ] ; then

  if [ -f /usr/local/etc/mavenrc ] ; then
    . /usr/local/etc/mavenrc
  fi

  if [ -f /etc/mavenrc ] ; then
    . /etc/mavenrc
  fi

  if [ -f "$HOME/.mavenrc" ] ; then
    . "$HOME/.mavenrc"
  fi

fi

# OS specific support.
cygwin=false;
darwin=false;
mingw=false
case "$(uname)" in
  CYGWIN*) cygwin=true ;;
  MINGW*)  mingw=true;;
  Darwin*) darwin=true
    # Use /usr/libexec/java_home if available, otherwise fall back
    # to /Library/Java/Home
    # See https://developer.apple.com/library/mac/qa/qa1170/_index.html
    if [ -z "$JAVA_HOME" ]; then
      if [ -x "/usr/libexec/java_home" ]; then
        JAVA_HOME="$(/usr/libexec/java_home)"; export JAVA_HOME
      else
        JAVA_HOME="/Library/Java/Home"; export JAVA_HOME
      fi
    fi
    ;;
esac

if [ -z "$JAVA_HOME" ] ; then
  JAVACMD="$(\unset -f command 2>/dev/null; \command -v java)"
else
  JAVACMD="$JAVA_HOME/bin/java"
fi

if [ ! -x "$JAVACMD" ] ; then
  echo "Error: JAVA_HOME is not defined correctly." >&2
  echo "  We cannot execute $JAVACMD" >&2
  exit 1
fi

# traverses directory structure from process work directory to filesystem root
# first directory with .mvn subdirectory is considered project base directory
find_maven_basedir() {
  if [ -z "$1" ]
  then
    echo "Path not specified to find_maven_basedir"
    return 1
  fi

  basedir="$1"
  wdir="$1"
  while [ "$wdir" != '/' ] ; do
    if [ -d "$wdir"/.mvn ] ; then
      basedir=$wdir
      break
    fi
    # workaround for JBEAP-8://resolve path://resolve
    if [ -d "${wdir}" ]; then
      wdir="$(cd "$wdir/.." && pwd)"
    fi
    # end of workaround
  done
  printf '%s' "$(cd "$basedir" && pwd -P)"
}

# concatenates all lines of a file
concat_lines() {
  if [ -f "$1" ]; then
    # Remove \r in case we run on Windows within Git Bash
    # and have configured Git to bytes preserve line endings
    tr -s '\r\n' ' ' < "$1"
  fi
}

BASE_DIR=$(find_maven_basedir "$(dirname "$0")")
if [ -z "$BASE_DIR" ] ; then
  exit 1;
fi

MAVEN_PROJECTBASEDIR=${MAVEN_BASEDIR:-"$BASE_DIR"}; export MAVEN_PROJECTBASEDIR
if [ "$MVNW_VERBOSE" = true ] ; then
  echo $MAVEN_PROJECTBASEDIR
fi

wrapperJarPath="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
wrapperUrl="$(sed -n 's/^wrapperUrl=//p' "$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties" | tr -d '\r\n')"
wrapperJarAvailable=false
if [ -f "$wrapperJarPath" ] ; then
  wrapperJarAvailable=true
fi

wrapperSha256Sum=""
if [ -f "$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar.sha256" ] ; then
  wrapperSha256Sum=$(cat "$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar.sha256")
fi

download() {
  if [ -z "$MVNW_USERNAME" ] || [ -z "$MVNW_PASSWORD" ] ; then
    if command -v wget > /dev/null ; then
      quiet_opt="--quiet"
      if [ "$MVNW_VERBOSE" = true ] ; then
        quiet_opt=""
      fi
      wget $quiet_opt -O "$1" "$2" || return $?
    elif command -v curl > /dev/null ; then
      fail_opt="--fail"
      if [ -z "${MVNW_VERBOSE}" ] || [ "${MVNW_VERBOSE}" != "true" ] ; then
        fail_opt="--silent --show-error $fail_opt"
      fi
      curl $fail_opt -o "$1" "$2" || return $?
    else
      printf "Cannot download %s; wget or curl not available\n" "$2" >&2
      return 1
    fi
  else
    if command -v wget > /dev/null ; then
      quiet_opt="--quiet"
      if [ "$MVNW_VERBOSE" = true ] ; then
        quiet_opt=""
      fi
      wget $quiet_opt --http-user="$MVNW_USERNAME" --http-password="$MVNW_PASSWORD" -O "$1" "$2" || return $?
    elif command -v curl > /dev/null ; then
      fail_opt="--fail"
      if [ -z "${MVNW_VERBOSE}" ] || [ "${MVNW_VERBOSE}" != "true" ] ; then
        fail_opt="--silent --show-error $fail_opt"
      fi
      curl $fail_opt --user "$MVNW_USERNAME:$MVNW_PASSWORD" -o "$1" "$2" || return $?
    else
      printf "Cannot download %s; wget or curl not available\n" "$2" >&2
      return 1
    fi
  fi
  printf "Done\n"
}

verify() {
  if command -v sha256sum > /dev/null ; then
    echo "$1  $2" | sha256sum -c > /dev/null 2>&1
  elif command -v shasum > /dev/null ; then
    echo "$1  $2" | shasum -a 256 -c > /dev/null 2>&1
  else
    printf "Checksum validation not available (sha256sum or shasum not found)\n" >&2
    return 1
  fi
}

# If the maven-wrapper.jar is missing, download it
if [ "$wrapperJarAvailable" = false ] ; then
  if [ -n "$wrapperUrl" ] ; then
    if [ "$MVNW_VERBOSE" = true ] ; then
      echo "Downloading Maven Wrapper from $wrapperUrl ..."
    fi
    if ! download "$wrapperJarPath" "$wrapperUrl" ; then
      printf "Cannot download maven-wrapper.jar from %s\n" "$wrapperUrl" >&2
      exit 1
    fi
    wrapperJarAvailable=true
  fi
fi

if [ "$wrapperJarAvailable" = true ] && [ -n "$wrapperSha256Sum" ] ; then
  if ! verify "$wrapperSha256Sum" "$wrapperJarPath" ; then
    printf "Checksum validation failed for maven-wrapper.jar\n" >&2
    exit 1
  fi
fi

MAVEN_OPTS="$(concat_lines "$MAVEN_PROJECTBASEDIR/.mvn/jvm.config") $MAVEN_OPTS"

# For Cygwin, switch paths to appropriate format before running mvn
if $cygwin; then
  [ -n "$JAVA_HOME" ] &&
    JAVA_HOME=$(cygpath --path --windows "$JAVA_HOME")
  [ -n "$CLASSPATH" ] &&
    CLASSPATH=$(cygpath --path --windows "$CLASSPATH")
  [ -n "$MAVEN_PROJECTBASEDIR" ] &&
    MAVEN_PROJECTBASEDIR=$(cygpath --path --windows "$MAVEN_PROJECTBASEDIR")
fi

# Provide a "standardized" way to retrieve the CLI args that will
# work with both Windows and non-Windows executions.
MAVEN_CMD_LINE_ARGS="$MAVEN_CONFIG $*"
export MAVEN_CMD_LINE_ARGS

WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

# Find project.basedir by searching up from the script location
exec "$JAVACMD" \
  $MAVEN_OPTS \
  $MAVEN_DEBUG_OPTS \
  -classpath "$wrapperJarPath" \
  "-Dmaven.multiModuleProjectDirectory=${MAVEN_PROJECTBASEDIR}" \
  ${WRAPPER_LAUNCHER} $MAVEN_CMD_LINE_ARGS
