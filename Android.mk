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

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v13

LOCAL_SRC_FILES := $(call all-java-files-under, src) \
    $(call all-renderscript-files-under, src) \
    $(call all-proto-files-under, protos)

LOCAL_PROTOC_OPTIMIZE_TYPE := nano
LOCAL_PROTOC_FLAGS := --proto_path=$(LOCAL_PATH)/protos/

LOCAL_SDK_VERSION := 19

LOCAL_PACKAGE_NAME := Launcher3
#LOCAL_CERTIFICATE := shared

LOCAL_OVERRIDES_PACKAGES := Launcher2

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include $(BUILD_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))

#
# Protocol Buffer Debug Utility in Java
#
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, util) \
    $(call all-proto-files-under, protos)

LOCAL_PROTOC_OPTIMIZE_TYPE := nano
LOCAL_PROTOC_FLAGS := --proto_path=$(LOCAL_PATH)/protos/

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := protoutil

include $(BUILD_HOST_JAVA_LIBRARY)
