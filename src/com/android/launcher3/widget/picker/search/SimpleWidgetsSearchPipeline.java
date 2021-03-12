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

package com.android.launcher3.widget.picker.search;

import com.android.launcher3.widget.model.WidgetsListBaseEntry;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Implementation of {@link WidgetsPickerSearchPipeline} that performs search by prefix matching on
 * app names and widget labels.
 */
public final class SimpleWidgetsSearchPipeline implements WidgetsPickerSearchPipeline {

    private final List<WidgetsListBaseEntry> mAllEntries;

    public SimpleWidgetsSearchPipeline(List<WidgetsListBaseEntry> allEntries) {
        mAllEntries = allEntries;
    }

    @Override
    public void query(String input, Consumer<List<WidgetsListBaseEntry>> callback) {
        StringMatcher matcher =  StringMatcher.getInstance();
        ArrayList<WidgetsListBaseEntry> results = new ArrayList<>();
        // TODO(b/157286785): Filter entries based on query prefix matching on widget labels also.
        for (WidgetsListBaseEntry e : mAllEntries) {
            if (matcher.matches(input, e.mPkgItem.title.toString())) {
                results.add(e);
            }
        }
        callback.accept(results);
    }

    /**
     * Performs locale sensitive string comparison using {@link Collator}.
     */
    public static class StringMatcher {

        private static final char MAX_UNICODE = '\uFFFF';

        private final Collator mCollator;

        StringMatcher() {
            mCollator = Collator.getInstance();
            mCollator.setStrength(Collator.PRIMARY);
            mCollator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
        }

        /**
         * Returns true if {@param query} is a prefix of {@param target}.
         */
        public boolean matches(String query, String target) {
            switch (mCollator.compare(query, target)) {
                case 0:
                    return true;
                case -1:
                    // The target string can contain a modifier which would make it larger than
                    // the query string (even though the length is same). If the query becomes
                    // larger after appending a unicode character, it was originally a prefix of
                    // the target string and hence should match.
                    return mCollator.compare(query + MAX_UNICODE, target) > -1;
                default:
                    return false;
            }
        }

        public static StringMatcher getInstance() {
            return new StringMatcher();
        }
    }
}
