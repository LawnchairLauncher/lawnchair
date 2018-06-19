#
# Copyright (C) 2013 The Android Open Source Project
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
#

LOCAL_PATH := $(call my-dir)

#
# Prebuilt Java Libraries
#
include $(CLEAR_VARS)
LOCAL_MODULE := libSharedSystemUI
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_SRC_FILES := quickstep/libs/sysui_shared.jar
LOCAL_UNINSTALLABLE_MODULE := true
LOCAL_SDK_VERSION := current
include $(BUILD_PREBUILT)

#
# Build rule for Launcher3 dependencies lib.
#
include $(CLEAR_VARS)
LOCAL_USE_AAPT2 := true
LOCAL_AAPT2_ONLY := true
LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_ANDROID_LIBRARIES := \
    android-support-v4 \
    android-support-v7-recyclerview \
    android-support-dynamic-animation

LOCAL_SRC_FILES := \
    $(call all-proto-files-under, protos) \
    $(call all-proto-files-under, proto_overrides)

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_PROTOC_OPTIMIZE_TYPE := nano
LOCAL_PROTOC_FLAGS := --proto_path=$(LOCAL_PATH)/protos/ --proto_path=$(LOCAL_PATH)/proto_overrides/
LOCAL_PROTO_JAVA_OUTPUT_PARAMS := enum_style=java

LOCAL_SDK_VERSION := current
LOCAL_MIN_SDK_VERSION := 21
LOCAL_MODULE := Launcher3CommonDepsLib
LOCAL_PRIVILEGED_MODULE := true
LOCAL_MANIFEST_FILE := AndroidManifest-common.xml

include $(BUILD_STATIC_JAVA_LIBRARY)

#
# Build rule for Launcher3 app.
#
include $(CLEAR_VARS)
LOCAL_USE_AAPT2 := true
LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_ANDROID_LIBRARIES := Launcher3CommonDepsLib
LOCAL_SRC_FILES := \
    $(call all-java-files-under, src) \
    $(call all-java-files-under, src_ui_overrides) \
    $(call all-java-files-under, src_flags)

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

LOCAL_SDK_VERSION := current
LOCAL_MIN_SDK_VERSION := 21
LOCAL_PACKAGE_NAME := Launcher3
LOCAL_PRIVILEGED_MODULE := true
LOCAL_OVERRIDES_PACKAGES := Home Launcher2

LOCAL_FULL_LIBS_MANIFEST_FILES := $(LOCAL_PATH)/AndroidManifest-common.xml

LOCAL_JACK_COVERAGE_INCLUDE_FILTER := com.android.launcher3.*

include $(BUILD_PACKAGE)

#
# Build rule for Launcher3 Go app for Android Go devices.
#
include $(CLEAR_VARS)
LOCAL_USE_AAPT2 := true
LOCAL_MODULE_TAGS := optional
LOCAL_STATIC_ANDROID_LIBRARIES := Launcher3CommonDepsLib

LOCAL_SRC_FILES := \
    $(call all-java-files-under, src) \
    $(call all-java-files-under, src_ui_overrides) \
    $(call all-java-files-under, go/src_flags)

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/go/res

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

LOCAL_SDK_VERSION := current
LOCAL_MIN_SDK_VERSION := 21
LOCAL_PACKAGE_NAME := Launcher3Go
LOCAL_PRIVILEGED_MODULE := true
LOCAL_OVERRIDES_PACKAGES := Home Launcher2 Launcher3 Launcher3QuickStep

LOCAL_FULL_LIBS_MANIFEST_FILES := \
    $(LOCAL_PATH)/AndroidManifest.xml \
    $(LOCAL_PATH)/AndroidManifest-common.xml

LOCAL_MANIFEST_FILE := go/AndroidManifest.xml
LOCAL_JACK_COVERAGE_INCLUDE_FILTER := com.android.launcher3.*
include $(BUILD_PACKAGE)

#
# Build rule for Quickstep library.
#
include $(CLEAR_VARS)
LOCAL_USE_AAPT2 := true
LOCAL_AAPT2_ONLY := true
LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_JAVA_LIBRARIES := libSharedSystemUI
LOCAL_STATIC_ANDROID_LIBRARIES := Launcher3CommonDepsLib

LOCAL_SRC_FILES := \
    $(call all-java-files-under, src) \
    $(call all-java-files-under, quickstep/src) \
    $(call all-java-files-under, src_flags)

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/quickstep/res
LOCAL_PROGUARD_ENABLED := disabled

LOCAL_SDK_VERSION := system_current
LOCAL_MIN_SDK_VERSION := 26
LOCAL_MODULE := Launcher3QuickStepLib
LOCAL_PRIVILEGED_MODULE := true

LOCAL_MANIFEST_FILE := quickstep/AndroidManifest.xml
include $(BUILD_STATIC_JAVA_LIBRARY)

#
# Build rule for Quickstep app.
#
include $(CLEAR_VARS)
LOCAL_USE_AAPT2 := true
LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_ANDROID_LIBRARIES := Launcher3QuickStepLib
LOCAL_PROGUARD_ENABLED := disabled

LOCAL_SDK_VERSION := system_current
LOCAL_MIN_SDK_VERSION := 26
LOCAL_PACKAGE_NAME := Launcher3QuickStep
LOCAL_PRIVILEGED_MODULE := true
LOCAL_OVERRIDES_PACKAGES := Home Launcher2 Launcher3

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/quickstep/res

LOCAL_FULL_LIBS_MANIFEST_FILES := \
    $(LOCAL_PATH)/AndroidManifest.xml \
    $(LOCAL_PATH)/AndroidManifest-common.xml

LOCAL_MANIFEST_FILE := quickstep/AndroidManifest.xml
LOCAL_JACK_COVERAGE_INCLUDE_FILTER := com.android.launcher3.*

include $(BUILD_PACKAGE)


#
# Build rule for Launcher3 Go app with quickstep for Android Go devices.
#
include $(CLEAR_VARS)
LOCAL_USE_AAPT2 := true
LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_JAVA_LIBRARIES := libSharedSystemUI
LOCAL_STATIC_ANDROID_LIBRARIES := Launcher3CommonDepsLib

LOCAL_SRC_FILES := \
    $(call all-java-files-under, src) \
    $(call all-java-files-under, quickstep/src) \
    $(call all-java-files-under, go/src_flags)

LOCAL_RESOURCE_DIR := \
    $(LOCAL_PATH)/quickstep/res \
    $(LOCAL_PATH)/go/res

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_SDK_VERSION := system_current
LOCAL_MIN_SDK_VERSION := 26
LOCAL_PACKAGE_NAME := Launcher3QuickStepGo
LOCAL_PRIVILEGED_MODULE := true
LOCAL_OVERRIDES_PACKAGES := Home Launcher2 Launcher3 Launcher3QuickStep

LOCAL_FULL_LIBS_MANIFEST_FILES := \
    $(LOCAL_PATH)/go/AndroidManifest.xml \
    $(LOCAL_PATH)/AndroidManifest.xml \
    $(LOCAL_PATH)/AndroidManifest-common.xml

LOCAL_MANIFEST_FILE := quickstep/AndroidManifest.xml
LOCAL_JACK_COVERAGE_INCLUDE_FILTER := com.android.launcher3.*
include $(BUILD_PACKAGE)


# ==================================================
include $(call all-makefiles-under,$(LOCAL_PATH))
