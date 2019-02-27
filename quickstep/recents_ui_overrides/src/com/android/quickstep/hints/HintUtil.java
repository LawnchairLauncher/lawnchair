/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep.hints;

import android.app.PendingIntent;
import android.graphics.drawable.Icon;
import android.os.Bundle;

public final class HintUtil {

    public static final String ID_KEY = "id";
    public static final String ICON_KEY = "icon";
    public static final String TEXT_KEY = "text";
    public static final String TAP_ACTION_KEY = "tap_action";

    private HintUtil() {}

    public static Bundle makeHint(String id, Icon icon, CharSequence text) {
        Bundle hint = new Bundle();
        hint.putString(ID_KEY, id);
        hint.putParcelable(ICON_KEY, icon);
        hint.putCharSequence(TEXT_KEY, text);
        return hint;
    }

    public static Bundle makeHint(Icon icon, CharSequence text, PendingIntent tapAction) {
        Bundle hint = new Bundle();
        hint.putParcelable(ICON_KEY, icon);
        hint.putCharSequence(TEXT_KEY, text);
        hint.putParcelable(TAP_ACTION_KEY, tapAction);
        return hint;
    }

    public static String getId(Bundle hint) {
        String id = hint.getString(ID_KEY);
        if (id == null) {
            throw new IllegalArgumentException("Hint does not contain an ID");
        }
        return id;
    }

    public static Icon getIcon(Bundle hint) {
        Icon icon = hint.getParcelable(ICON_KEY);
        if (icon == null) {
            throw new IllegalArgumentException("Hint does not contain an icon");
        }
        return icon;
    }

    public static CharSequence getText(Bundle hint) {
        CharSequence text = hint.getCharSequence(TEXT_KEY);
        if (text == null) {
            throw new IllegalArgumentException("Hint does not contain text");
        }
        return text;
    }

    public static PendingIntent getTapAction(Bundle hint) {
        PendingIntent tapAction = hint.getParcelable(TAP_ACTION_KEY);
        if (tapAction == null) {
            throw new IllegalArgumentException("Hint does not contain a tap action");
        }
        return tapAction;
    }
}
