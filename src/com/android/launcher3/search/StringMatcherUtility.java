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

package com.android.launcher3.search;

import java.text.Collator;

/**
 * Utilities for matching query string to target string.
 */
public class StringMatcherUtility {

    /**
     * Returns {@code true} if {@code query} is a prefix of a substring in {@code target}. How to
     * break target to valid substring is defined in the given {@code matcher}.
     */
    public static boolean matches(String query, String target, StringMatcher matcher) {
        int queryLength = query.length();

        int targetLength = target.length();

        if (targetLength < queryLength || queryLength <= 0) {
            return false;
        }

        if (requestSimpleFuzzySearch(query)) {
            return target.toLowerCase().contains(query);
        }

        int lastType;
        int thisType = Character.UNASSIGNED;
        int nextType = Character.getType(target.codePointAt(0));

        int end = targetLength - queryLength;
        for (int i = 0; i <= end; i++) {
            lastType = thisType;
            thisType = nextType;
            nextType = i < (targetLength - 1)
                    ? Character.getType(target.codePointAt(i + 1)) : Character.UNASSIGNED;
            if (matcher.isBreak(thisType, lastType, nextType)
                    && matcher.matches(query, target.substring(i, i + queryLength))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Performs locale sensitive string comparison using {@link Collator}.
     */
    public static class StringMatcher {

        private static final char MAX_UNICODE = '\uFFFF';

        private final Collator mCollator;

        StringMatcher() {
            // On android N and above, Collator uses ICU implementation which has a much better
            // support for non-latin locales.
            mCollator = Collator.getInstance();
            mCollator.setStrength(Collator.PRIMARY);
            mCollator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
        }

        /**
         * Returns true if {@param query} is a prefix of {@param target}
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

        /**
         * Returns true if the current point should be a break point.
         *
         * Following cases are considered as break points:
         *     1) Any non space character after a space character
         *     2) Any digit after a non-digit character
         *     3) Any capital character after a digit or small character
         *     4) Any capital character before a small character
         *
         * E.g., "YouTube" matches the input "you" and "tube", but not "out".
         */
        protected boolean isBreak(int thisType, int prevType, int nextType) {
            switch (prevType) {
                case Character.UNASSIGNED:
                case Character.SPACE_SEPARATOR:
                case Character.LINE_SEPARATOR:
                case Character.PARAGRAPH_SEPARATOR:
                    return true;
            }
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
                    // Always a break point for a symbol
                    return true;
                default:
                    return  false;
            }
        }
    }

    /**
     * Subclass of {@code StringMatcher} using simple space break for prefix matching.
     * E.g., "YouTube" matches the input "you". "Play Store" matches the input "play".
     */
    public static class StringMatcherSpace extends StringMatcher {

        public static StringMatcherSpace getInstance() {
            return new StringMatcherSpace();
        }

        /**
         * The first character or any character after a space is considered as a break point.
         * Returns true if the current point should be a break point.
         */
        @Override
        protected boolean isBreak(int thisType, int prevType, int nextType) {
            return prevType == Character.UNASSIGNED || prevType == Character.SPACE_SEPARATOR;
        }
    }

    /**
     * Matching optimization to search in Chinese.
     */
    private static boolean requestSimpleFuzzySearch(String s) {
        for (int i = 0; i < s.length(); ) {
            int codepoint = s.codePointAt(i);
            i += Character.charCount(codepoint);
            switch (Character.UnicodeScript.of(codepoint)) {
                case HAN:
                    //Character.UnicodeScript.HAN: use String.contains to match
                    return true;
            }
        }
        return false;
    }
}
