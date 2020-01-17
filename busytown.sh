#!/bin/bash
set -e
readonly PROG_DIR="$(dirname $0)"
readonly OUT=${1:-"out"}
readonly DIST=${2:-"dist"}
readonly BUILD_NUMBER="$3"
function set_java_home() {
    case `uname -s` in
        Darwin)
            export JAVA_HOME=../../../prebuilts/jdk/jdk8/darwin-x86
            export JDK_9=../../../prebuilts/jdk/jdk9/darwin-x86
            ;;
        *)
            export JAVA_HOME=../../../prebuilts/jdk/jdk8/linux-x86
            export JDK_9=../../../prebuilts/jdk/jdk9/linux-x86
            ;;
    esac
}
readonly R4A_BUILD_NUMBER=1.3.61
function copy_jar_into_maven_repo() {
    local SOURCE_JAR="$1"
    local MODULE_NAME="$2"
    if [ ! -f $SOURCE_JAR ]; then
        echo -e "\033[1;31mERROR: Could not publish module $MODULE_NAME \033[0m"
        echo "  File $SOURCE_JAR does not exist"
        exit 1
    fi
    local MODULE_DIRECTORY=$OUT/m2/org/jetbrains/kotlin/$MODULE_NAME/$R4A_BUILD_NUMBER
    mkdir -p $MODULE_DIRECTORY
    cp $SOURCE_JAR $MODULE_DIRECTORY/$MODULE_NAME-$R4A_BUILD_NUMBER.jar
}
set_java_home
export JDK_18=$JAVA_HOME
export JDK_17=$JAVA_HOME
export JDK_16=$JAVA_HOME
cd $PROG_DIR
rm -rf $OUT
mkdir -p $OUT
mkdir -p $DIST
# Build a custom version of Kotlin
./gradlew install ideaPlugin  :compiler:tests-common:testJar --no-daemon -Pbuild.number=$R4A_BUILD_NUMBER -PdeployVersion=$R4A_BUILD_NUMBER -Dmaven.repo.local=$OUT/m2

# Copy jar files that are not published in the build but are required by androidx.compose
echo "Copying additional repositories"
readonly INTELLIJ_SDK_VERSION=$(grep intellijSdk gradle/versions.properties | sed 's/^[^=]*=//')
readonly ANDROID_STUDIO_BUILD=$(grep androidStudioBuild gradle/versions.properties | sed 's/^[^=]*=//')
readonly INTELLIJ_DEPENDENCIES=dependencies/repo/kotlin.build
if [ ! -f $INTELLIJ_DEPENDENCIES/intellij-core/$INTELLIJ_SDK_VERSION/artifacts/intellij-core.jar ]; then
    echo -e "\033[1;31mError: Could not determine intellij version, tried $INTELLIJ_DEPENDENCIES/intellij-core/$INTELLIJ_SDK_VERSION/artifacts/intellij-core.jar\033[0m"
    exit 1
fi
copy_jar_into_maven_repo $INTELLIJ_DEPENDENCIES/intellij-core/$INTELLIJ_SDK_VERSION/artifacts/intellij-core.jar kotlin-intellij-core
copy_jar_into_maven_repo dist/artifacts/ideaPlugin/Kotlin/lib/kotlin-plugin.jar kotlin-plugin
copy_jar_into_maven_repo dist/artifacts/ideaPlugin/Kotlin/lib/jps/kotlin-jps-plugin.jar kotlin-jps-plugin
copy_jar_into_maven_repo idea/idea-jps-common/build/libs/idea-jps-common-$R4A_BUILD_NUMBER.jar kotlin-jps-common-ide
copy_jar_into_maven_repo j2k/build/libs/j2k-$R4A_BUILD_NUMBER.jar kotlin-j2k
copy_jar_into_maven_repo compiler/tests-common/build/libs/tests-common-$R4A_BUILD_NUMBER-tests.jar kotlin-tests-common
# remove a bunch of build artifacts that often accidentally get committed and then break future builds
rm -rf libraries/tools/kotlin-source-map-loader/lib/
rm -rf libraries/tools/kotlin-source-map-loader/node_modules/
rm -rf libraries/tools/kotlin-test-js-runner/.rpt2_cache/
rm -rf libraries/tools/kotlin-test-js-runner/lib/
rm -rf libraries/tools/kotlin-test-js-runner/node_modules/
# tar up the distrbution
echo "tar'ing result"
tar cf $OUT/m2.tar $OUT/m2
mv $OUT/m2.tar $DIST
cp -r dist/artifacts/ideaPlugin/Kotlin $OUT/Kotlin
tar cf $OUT/Kotlin.tar $OUT/Kotlin
mv $OUT/Kotlin.tar $DIST