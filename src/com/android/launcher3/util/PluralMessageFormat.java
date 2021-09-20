/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.util;

import android.content.Context;
import android.icu.text.MessageFormat;

import androidx.annotation.StringRes;

import java.util.HashMap;
import java.util.Locale;

/** A helper class to format common ICU plural strings. */
public class PluralMessageFormat {

    /**
     * Returns a plural string from a ICU format message template, which takes "count" as an
     * argument.
     *
     * <p>An example of ICU format message template provided by {@code stringId}:
     * {count, plural, =1{# widget} other{# widgets}}
     */
    public static final String getIcuPluralString(Context context, @StringRes int stringId,
            int count) {
        MessageFormat icuCountFormat = new MessageFormat(
                context.getResources().getString(stringId),
                Locale.getDefault());
        HashMap<String, Object> args = new HashMap();
        args.put("count", count);
        return icuCountFormat.format(args);
    }
}
