/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.bubbles;

import android.content.Context;
import android.provider.Settings;

import java.util.List;

/**
 * Common class for the various debug {@link android.util.Log} output configuration in the Bubbles
 * package.
 */
public class BubbleDebugConfig {

    // All output logs in the Bubbles package use the {@link #TAG_BUBBLES} string for tagging their
    // log output. This makes it easy to identify the origin of the log message when sifting
    // through a large amount of log output from multiple sources. However, it also makes trying
    // to figure-out the origin of a log message while debugging the Bubbles a little painful. By
    // setting this constant to true, log messages from the Bubbles package will be tagged with
    // their class names instead fot the generic tag.
    public static final boolean TAG_WITH_CLASS_NAME = false;

    // Default log tag for the Bubbles package.
    public static final String TAG_BUBBLES = "Bubbles";
    public static final boolean DEBUG_USER_EDUCATION = false;

    private static final boolean FORCE_SHOW_USER_EDUCATION = false;
    private static final String FORCE_SHOW_USER_EDUCATION_SETTING =
            "force_show_bubbles_user_education";
    /**
     * When set to true, bubbles user education flow never shows up.
     */
    private static final String FORCE_HIDE_USER_EDUCATION_SETTING =
            "force_hide_bubbles_user_education";

    /**
     * @return whether we should force show user education for bubbles. Used for debugging & demos.
     */
    static boolean forceShowUserEducation(Context context) {
        boolean forceShow = Settings.Secure.getInt(context.getContentResolver(),
                FORCE_SHOW_USER_EDUCATION_SETTING, 0) != 0;
        return FORCE_SHOW_USER_EDUCATION || forceShow;
    }

    /**
     * @return whether we should never show user education for bubbles. Used in tests.
     */
    static boolean neverShowUserEducation(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(),
                FORCE_HIDE_USER_EDUCATION_SETTING, 0) != 0;
    }

    static String formatBubblesString(List<Bubble> bubbles, BubbleViewProvider selected) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bubbles.size(); i++) {
            Bubble bubble = bubbles.get(i);
            if (bubble == null) {
                sb.append("   <null> !!!!!");
            } else {
                boolean isSelected = (selected != null
                        && !BubbleOverflow.KEY.equals(selected.getKey())
                        && bubble == selected);
                String arrow = isSelected ? "=>" : "  ";

                sb.append(String.format("%s Bubble{act=%12d, showInShade=%d, key=%s}",
                        arrow,
                        bubble.getLastActivity(),
                        (bubble.showInShade() ? 1 : 0),
                        bubble.getKey()));
            }
            if (i != bubbles.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
