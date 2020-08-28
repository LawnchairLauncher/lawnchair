# Copyright (C) 2018 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#############################################
# Launcher Robolectric test target.         #
#############################################
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := LauncherRoboTests
LOCAL_MODULE_CLASS := JAVA_LIBRARIES

LOCAL_SDK_VERSION := system_current
LOCAL_SRC_FILES := \
	$(call all-java-files-under, src) \
	$(call all-java-files-under, ../tests/src_common)

LOCAL_STATIC_JAVA_LIBRARIES := \
    androidx.test.runner \
    androidx.test.rules \
    mockito-robolectric-prebuilt \
    truth-prebuilt
LOCAL_JAVA_LIBRARIES := \
    platform-robolectric-4.3.1-prebuilt

LOCAL_JAVA_RESOURCE_DIRS := resources config

LOCAL_INSTRUMENTATION_FOR := Launcher3
LOCAL_MODULE_TAGS := optional

# Generate test_config.properties
include external/robolectric-shadows/gen_test_config.mk

include $(BUILD_STATIC_JAVA_LIBRARY)

############################################
# Target to run the previous target.       #
############################################
include $(CLEAR_VARS)

LOCAL_MODULE := RunLauncherRoboTests
LOCAL_SDK_VERSION := system_current
LOCAL_JAVA_LIBRARIES := LauncherRoboTests

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res
LOCAL_TEST_PACKAGE := Launcher3
LOCAL_INSTRUMENT_SOURCE_DIRS := packages/apps/Launcher3/src

LOCAL_ROBOTEST_TIMEOUT := 36000

include prebuilts/misc/common/robolectric/4.3.1/run_robotests.mk
