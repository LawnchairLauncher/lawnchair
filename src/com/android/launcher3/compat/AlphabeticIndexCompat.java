package com.android.launcher3.compat;

import android.annotation.TargetApi;
import android.content.Context;
import android.icu.text.AlphabeticIndex;
import android.os.Build;
import android.os.LocaleList;
import android.util.Log;

import com.android.launcher3.Utilities;

import java.lang.reflect.Method;
import java.util.Locale;

public class AlphabeticIndexCompat {
    private static final String TAG = "AlphabeticIndexCompat";

    private static final String MID_DOT = "\u2219";
    private final BaseIndex mBaseIndex;
    private final String mDefaultMiscLabel;

    public AlphabeticIndexCompat(Context context) {
        BaseIndex index = null;

        try {
            if (Utilities.ATLEAST_NOUGAT) {
                index = new AlphabeticIndexVN(context);
            }
        } catch (Exception e) {
            Log.d(TAG, "Unable to load the system index", e);
        }
        if (index == null) {
            try {
                index = new AlphabeticIndexV16(context);
            } catch (Exception e) {
                Log.d(TAG, "Unable to load the system index", e);
            }
        }

        mBaseIndex = index == null ? new BaseIndex() : index;

        if (context.getResources().getConfiguration().locale
                .getLanguage().equals(Locale.JAPANESE.getLanguage())) {
            // Japanese character 他 ("misc")
            mDefaultMiscLabel = "\u4ed6";
            // TODO(winsonc, omakoto): We need to handle Japanese sections better, especially the kanji
        } else {
            // Dot
            mDefaultMiscLabel = MID_DOT;
        }
    }

    /**
     * Computes the section name for an given string {@param s}.
     */
    public String computeSectionName(CharSequence cs) {
        String s = Utilities.trim(cs);
        String sectionName = mBaseIndex.getBucketLabel(mBaseIndex.getBucketIndex(s));
        if (Utilities.trim(sectionName).isEmpty() && s.length() > 0) {
            int c = s.codePointAt(0);
            boolean startsWithDigit = Character.isDigit(c);
            if (startsWithDigit) {
                // Digit section
                return "#";
            } else {
                boolean startsWithLetter = Character.isLetter(c);
                if (startsWithLetter) {
                    return mDefaultMiscLabel;
                } else {
                    // In languages where these differ, this ensures that we differentiate
                    // between the misc section in the native language and a misc section
                    // for everything else.
                    return MID_DOT;
                }
            }
        }
        return sectionName;
    }

    /**
     * Base class to support Alphabetic indexing if not supported by the framework.
     * TODO(winsonc): disable for non-english locales
     */
    private static class BaseIndex {

        private static final String BUCKETS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-";
        private static final int UNKNOWN_BUCKET_INDEX = BUCKETS.length() - 1;

        /**
         * Returns the index of the bucket in which the given string should appear.
         */
        protected int getBucketIndex(String s) {
            if (s.isEmpty()) {
                return UNKNOWN_BUCKET_INDEX;
            }
            int index = BUCKETS.indexOf(s.substring(0, 1).toUpperCase());
            if (index != -1) {
                return index;
            }
            return UNKNOWN_BUCKET_INDEX;
        }

        /**
         * Returns the label for the bucket at the given index (as returned by getBucketIndex).
         */
        protected String getBucketLabel(int index) {
            return BUCKETS.substring(index, index + 1);
        }
    }

    /**
     * Reflected libcore.icu.AlphabeticIndex implementation, falls back to the base
     * alphabetic index.
     */
    private static class AlphabeticIndexV16 extends BaseIndex {

        private Object mAlphabeticIndex;
        private Method mGetBucketIndexMethod;
        private Method mGetBucketLabelMethod;

        public AlphabeticIndexV16(Context context) throws Exception {
            Locale curLocale = context.getResources().getConfiguration().locale;
            Class clazz = Class.forName("libcore.icu.AlphabeticIndex");
            mGetBucketIndexMethod = clazz.getDeclaredMethod("getBucketIndex", String.class);
            mGetBucketLabelMethod = clazz.getDeclaredMethod("getBucketLabel", int.class);
            mAlphabeticIndex = clazz.getConstructor(Locale.class).newInstance(curLocale);

            if (!curLocale.getLanguage().equals(Locale.ENGLISH.getLanguage())) {
                clazz.getDeclaredMethod("addLabels", Locale.class)
                        .invoke(mAlphabeticIndex, Locale.ENGLISH);
            }
        }

        /**
         * Returns the index of the bucket in which {@param s} should appear.
         * Function is synchronized because underlying routine walks an iterator
         * whose state is maintained inside the index object.
         */
        protected int getBucketIndex(String s) {
            try {
                return (Integer) mGetBucketIndexMethod.invoke(mAlphabeticIndex, s);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return super.getBucketIndex(s);
        }

        /**
         * Returns the label for the bucket at the given index (as returned by getBucketIndex).
         */
        protected String getBucketLabel(int index) {
            try {
                return (String) mGetBucketLabelMethod.invoke(mAlphabeticIndex, index);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return super.getBucketLabel(index);
        }
    }

    /**
     * Implementation based on {@link AlphabeticIndex}.
     */
    @TargetApi(Build.VERSION_CODES.N)
    private static class AlphabeticIndexVN extends BaseIndex {

        private final AlphabeticIndex.ImmutableIndex mAlphabeticIndex;

        public AlphabeticIndexVN(Context context) {
            LocaleList locales = context.getResources().getConfiguration().getLocales();
            int localeCount = locales.size();

            Locale primaryLocale = localeCount == 0 ? Locale.ENGLISH : locales.get(0);
            AlphabeticIndex indexBuilder = new AlphabeticIndex(primaryLocale);
            for (int i = 1; i < localeCount; i++) {
                indexBuilder.addLabels(locales.get(i));
            }
            indexBuilder.addLabels(Locale.ENGLISH);

            mAlphabeticIndex = indexBuilder.buildImmutableIndex();
        }

        /**
         * Returns the index of the bucket in which {@param s} should appear.
         */
        protected int getBucketIndex(String s) {
            return mAlphabeticIndex.getBucketIndex(s);
        }

        /**
         * Returns the label for the bucket at the given index
         */
        protected String getBucketLabel(int index) {
            return mAlphabeticIndex.getBucket(index).getLabel();
        }
    }
}
