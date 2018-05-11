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
package ch.deletescape.lawnchair.allapps;

import android.os.Handler;

import java.util.ArrayList;
import java.util.List;

import ch.deletescape.lawnchair.AppInfo;
import ch.deletescape.lawnchair.util.ComponentKey;

/**
 * The default search implementation.
 */
public class DefaultAppSearchAlgorithm {

    private final List<AppInfo> mApps;
    protected final Handler mResultHandler;

    public DefaultAppSearchAlgorithm(List<AppInfo> apps) {
        mApps = apps;
        mResultHandler = new Handler();
    }

    public void cancel(boolean interruptActiveRequests) {
        if (interruptActiveRequests) {
            mResultHandler.removeCallbacksAndMessages(null);
        }
    }

    public void doSearch(final String query,
                         final AllAppsSearchBarController.Callbacks callback) {
        final ArrayList<ComponentKey> result = getTitleMatchResult(query);
        mResultHandler.post(new Runnable() {

            @Override
            public void run() {
                callback.onSearchResult(query, result);
            }
        });
    }

    protected ArrayList<ComponentKey> getTitleMatchResult(String query) {
        // Do an intersection of the words in the query and each title, and filter out all the
        // apps that don't match all of the words in the query.
        final String queryTextLower = query.toLowerCase();
        final ArrayList<ComponentKey> result = new ArrayList<>();
        for (AppInfo info : mApps) {
            if (matches(info, queryTextLower)) {
                result.add(info.toComponentKey());
            }
        }
        return result;
    }

    protected boolean matches(AppInfo info, String query) {
        return matches(info.title.toString().toLowerCase(), query);
    }

    protected boolean matches(String haystack, String needle) {
        // Assumes both haystack and needle are lowercase
        int queryLength = needle.length();
        int titleLength = haystack.length();

        if (titleLength < queryLength || queryLength <= 0) {
            return false;
        }

        // This algorithms works by iterating over the "haystack" string,
        // and the "needle" query is searched character by character on it
        // without going back. For instance, "ffox" would match in "firefox".
        int hi;
        int ni = 0;
        for (hi = 0; hi < titleLength; hi++) {
            if (haystack.charAt(hi) == needle.charAt(ni)) {
                ni++;
                if (ni == queryLength)
                    return true; // All characters consumed, the query matched
            }
        }
        return false;
    }

    /**
     * Returns true if the current point should be a break point. Following cases
     * are considered as break points:
     * 1) Any non space character after a space character
     * 2) Any digit after a non-digit character
     * 3) Any capital character after a digit or small character
     * 4) Any capital character before a small character
     */
    protected boolean isBreak(int thisType, int prevType, int nextType) {
        switch (thisType) {
            case Character.UPPERCASE_LETTER:
                if (nextType == Character.UPPERCASE_LETTER) {
                    return true;
                }
                // Follow through
            case Character.TITLECASE_LETTER:
                // Break point if previous was not a upper case
                return prevType != Character.UPPERCASE_LETTER;
            case Character.LOWERCASE_LETTER:
                // Break point if previous was not a letter.
                return prevType > Character.OTHER_LETTER || prevType <= Character.UNASSIGNED;
            case Character.DECIMAL_DIGIT_NUMBER:
            case Character.LETTER_NUMBER:
            case Character.OTHER_NUMBER:
                // Break point if previous was not a number
                return !(prevType == Character.DECIMAL_DIGIT_NUMBER
                        || prevType == Character.LETTER_NUMBER
                        || prevType == Character.OTHER_NUMBER);
            case Character.MATH_SYMBOL:
            case Character.CURRENCY_SYMBOL:
            case Character.OTHER_PUNCTUATION:
            case Character.DASH_PUNCTUATION:
            case Character.OTHER_LETTER:
                // Always a break point for a symbol
                return true;
            default:
                return false;
        }
    }
}
