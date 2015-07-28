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
# Build app code.
#
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src) \
    $(call all-java-files-under, WallpaperPicker/src) \
    $(call all-proto-files-under, protos)
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/WallpaperPicker/res $(LOCAL_PATH)/res
LOCAL_PROGUARD_FLAG_FILES := proguard.flags
LOCAL_STATIC_JAVA_LIBRARIES := android-support-v4 android-support-v7-recyclerview

LOCAL_PROTOC_OPTIMIZE_TYPE := nano
LOCAL_PROTOC_FLAGS := --proto_path=$(LOCAL_PATH)/protos/
LOCAL_AAPT_FLAGS := --auto-add-overlay

LOCAL_SDK_VERSION := current
LOCAL_PACKAGE_NAME := Launcher3
LOCAL_PRIVILEGED_MODULE := true
#LOCAL_CERTIFICATE := shared

LOCAL_OVERRIDES_PACKAGES := Home Launcher2

include $(BUILD_PACKAGE)


#
# Protocol Buffer Debug Utility in Java
#
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, util) \
    $(call all-proto-files-under, protos)

LOCAL_PROTOC_OPTIMIZE_TYPE := nano
LOCAL_PROTOC_FLAGS := --proto_path=$(LOCAL_PATH)/protos/

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := launcher_protoutil_lib
LOCAL_IS_HOST_MODULE := true
LOCAL_JAR_MANIFEST := util/etc/manifest.txt

include $(BUILD_HOST_JAVA_LIBRARY)

#
# Protocol Buffer Debug Utility Wrapper Script
#
include $(CLEAR_VARS)
LOCAL_IS_HOST_MODULE := true
LOCAL_MODULE_CLASS := EXECUTABLES
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := launcher_protoutil

include $(BUILD_SYSTEM)/base_rules.mk

$(LOCAL_BUILT_MODULE): | $(HOST_OUT_JAVA_LIBRARIES)/launcher_protoutil_lib.jar
$(LOCAL_BUILT_MODULE): $(LOCAL_PATH)/util/etc/launcher_protoutil | $(ACP)
	@echo "Copy: $(PRIVATE_MODULE) ($@)"
	$(copy-file-to-new-target)
	$(hide) chmod 755 $@

INTERNAL_DALVIK_MODULES += $(LOCAL_INSTALLED_MODULE)

include $(call all-makefiles-under,$(LOCAL_PATH))
