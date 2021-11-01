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
# Build rule for Launcher3 Go app for Android Go devices.
#
include $(CLEAR_VARS)
LOCAL_USE_AAPT2 := true
LOCAL_MODULE_TAGS := optional
LOCAL_STATIC_ANDROID_LIBRARIES := Launcher3CommonDepsLib

LOCAL_SRC_FILES := \
    $(call all-java-files-under, src) \
    $(call all-java-files-under, src_ui_overrides) \
    $(call all-java-files-under, go/src)

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/go/res

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

LOCAL_SDK_VERSION := current
LOCAL_MIN_SDK_VERSION := 26
LOCAL_PACKAGE_NAME := Launcher3Go
LOCAL_PRIVILEGED_MODULE := true
LOCAL_SYSTEM_EXT_MODULE := true
LOCAL_OVERRIDES_PACKAGES := Home Launcher2 Launcher3 Launcher3QuickStep
LOCAL_REQUIRED_MODULES := privapp_whitelist_com.android.launcher3

LOCAL_FULL_LIBS_MANIFEST_FILES := \
    $(LOCAL_PATH)/AndroidManifest.xml \
    $(LOCAL_PATH)/AndroidManifest-common.xml

LOCAL_MANIFEST_FILE := go/AndroidManifest.xml
LOCAL_JACK_COVERAGE_INCLUDE_FILTER := com.android.launcher3.*
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/NOTICE
include $(BUILD_PACKAGE)

#
# Build rule for Quickstep app.
#
include $(CLEAR_VARS)
LOCAL_USE_AAPT2 := true
LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_ANDROID_LIBRARIES := Launcher3QuickStepLib
LOCAL_PROGUARD_ENABLED := disabled

ifneq (,$(wildcard frameworks/base))
  LOCAL_PRIVATE_PLATFORM_APIS := true
else
  LOCAL_SDK_VERSION := system_current
  LOCAL_MIN_SDK_VERSION := 26
endif
LOCAL_PACKAGE_NAME := Launcher3QuickStep
LOCAL_PRIVILEGED_MODULE := true
LOCAL_SYSTEM_EXT_MODULE := true
LOCAL_OVERRIDES_PACKAGES := Home Launcher2 Launcher3
LOCAL_REQUIRED_MODULES := privapp_whitelist_com.android.launcher3

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/quickstep/res

LOCAL_FULL_LIBS_MANIFEST_FILES := \
    $(LOCAL_PATH)/quickstep/AndroidManifest-launcher.xml \
    $(LOCAL_PATH)/AndroidManifest-common.xml

LOCAL_MANIFEST_FILE := quickstep/AndroidManifest.xml
LOCAL_JACK_COVERAGE_INCLUDE_FILTER := com.android.launcher3.*

LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/NOTICE
include $(BUILD_PACKAGE)


#
# Build rule for Launcher3 Go app with quickstep for Android Go devices.
#
include $(CLEAR_VARS)
LOCAL_USE_AAPT2 := true
LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_JAVA_LIBRARIES := \
    SystemUI-statsd \
    SystemUISharedLib
ifneq (,$(wildcard frameworks/base))
  LOCAL_PRIVATE_PLATFORM_APIS := true
else
  LOCAL_SDK_VERSION := system_current
  LOCAL_MIN_SDK_VERSION := 26
endif
LOCAL_STATIC_ANDROID_LIBRARIES := LauncherGoResLib

LOCAL_SRC_FILES := \
    $(call all-java-files-under, src) \
    $(call all-java-files-under, quickstep/src) \
    $(call all-java-files-under, go/src) \
    $(call all-java-files-under, go/quickstep/src)

LOCAL_RESOURCE_DIR := \
    $(LOCAL_PATH)/go/quickstep/res \
    $(LOCAL_PATH)/go/res \
    $(LOCAL_PATH)/quickstep/res

LOCAL_PROGUARD_FLAG_FILES := proguard.flags
LOCAL_PROGUARD_ENABLED := full

LOCAL_PACKAGE_NAME := Launcher3QuickStepGo
LOCAL_PRIVILEGED_MODULE := true
LOCAL_SYSTEM_EXT_MODULE := true
LOCAL_OVERRIDES_PACKAGES := Home Launcher2 Launcher3 Launcher3QuickStep
LOCAL_REQUIRED_MODULES := privapp_whitelist_com.android.launcher3

LOCAL_FULL_LIBS_MANIFEST_FILES := \
    $(LOCAL_PATH)/go/AndroidManifest.xml \
    $(LOCAL_PATH)/go/AndroidManifest-launcher.xml \
    $(LOCAL_PATH)/AndroidManifest-common.xml

LOCAL_MANIFEST_FILE := quickstep/AndroidManifest.xml
LOCAL_JACK_COVERAGE_INCLUDE_FILTER := com.android.launcher3.*
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/NOTICE
include $(BUILD_PACKAGE)


# ==================================================
include $(call all-makefiles-under,$(LOCAL_PATH))
