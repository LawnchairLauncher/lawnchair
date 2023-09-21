/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.quickstep.util;

import android.content.Context;

import com.android.launcher3.R;
import com.android.launcher3.util.ResourceBasedOverride;

/** Utilities to work with Assistant functionality. */
public class AssistUtils implements ResourceBasedOverride {

    public AssistUtils() {}

    /** Creates AssistUtils as specified by overrides */
    public static AssistUtils newInstance(Context context) {
        return Overrides.getObject(AssistUtils.class, context, R.string.assist_utils_class);
    }

    /** @return Array of AssistUtils.INVOCATION_TYPE_* that we want to handle instead of SysUI. */
    public int[] getSysUiAssistOverrideInvocationTypes() {
        return new int[0];
    }

    /**
     * @return {@code true} if the override was handled, i.e. an assist surface was shown or the
     * request should be ignored. {@code false} means the caller should start assist another way.
     */
    public boolean tryStartAssistOverride(int invocationType) {
        return false;
    }
}
