#!/usr/bin/env bash
#!/bin/bash
set -o errexit
set -o nounset

APP_BASE_NAME=$(basename "$0")
APP_HOME=$( cd "${0%/*}" && pwd )

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

exec java -Dorg.gradle.appname=$APP_BASE_NAME -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
