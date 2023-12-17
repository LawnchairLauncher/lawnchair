#!/bin/bash
cd $ANDROID_BUILD_TOP/frameworks/base/packages/SystemUI/plugin
# Clear out anything old.
rm -rf /tmp/plugin_classes/
mkdir /tmp/plugin_classes

# Compile the jar
javac -cp $ANDROID_BUILD_TOP/out/target/common/obj/JAVA_LIBRARIES/framework_intermediates/classes.jar:$ANDROID_BUILD_TOP/out/target/common/obj/JAVA_LIBRARIES/core-all_intermediates/classes.jar `find ../plugin*/src -name *.java` -d /tmp/plugin_classes/
echo "" >> /tmp/plugin_classes/manifest.txt
jar cvfm SystemUIPluginLib.jar /tmp/plugin_classes/manifest.txt -C /tmp/plugin_classes .

# Place the jar and update the latest
mv SystemUIPluginLib.jar ./SystemUIPluginLib-`date +%m-%d-%Y`.jar
rm SystemUIPluginLib-latest.jar
ln -s SystemUIPluginLib-`date +%m-%d-%Y`.jar SystemUIPluginLib-latest.jar
