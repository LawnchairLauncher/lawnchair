/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.config;

/**
 * Defines a set of flags used to control various launcher behaviors
 * All the flags must be defined as
 *   public static boolean LAUNCHER3_FLAG_NAME = true/false;
 *
 * Use LAUNCHER3_ prefix for prevent namespace conflicts.
 */
public final class FeatureFlags {

    private FeatureFlags() {}

    public static boolean IS_DEV_BUILD = false;
    public static boolean IS_ALPHA_BUILD = false;
    public static boolean IS_RELEASE_BUILD = true;

    // Custom flags go below this
    public static boolean LAUNCHER3_ICON_NORMALIZATION = false;

}
