package com.android.launcher3.compat;

import android.content.Context;
import com.android.launcher3.Utilities;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Fallback class to support Alphabetic indexing if not supported by the framework.
 * TODO(winsonc): disable for non-english locales
 */
class BaseAlphabeticIndex {

    private static final String BUCKETS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-";
    private static final int UNKNOWN_BUCKET_INDEX = BUCKETS.length() - 1;

    public BaseAlphabeticIndex() {}

    /**
     * Sets the max number of the label buckets in this index.
     */
    public void setMaxLabelCount(int count) {
        // Not currently supported
    }

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
 * Reflected libcore.icu.AlphabeticIndex implementation, falls back to the base alphabetic index.
 */
public class AlphabeticIndexCompat extends BaseAlphabeticIndex {

    private static final String MID_DOT = "\u2219";

    private Object mAlphabeticIndex;
    private Method mAddLabelsMethod;
    private Method mSetMaxLabelCountMethod;
    private Method mGetBucketIndexMethod;
    private Method mGetBucketLabelMethod;
    private boolean mHasValidAlphabeticIndex;
    private String mDefaultMiscLabel;

    public AlphabeticIndexCompat(Context context) {
        super();
        try {
            Locale curLocale = context.getResources().getConfiguration().locale;
            Class clazz = Class.forName("libcore.icu.AlphabeticIndex");
            Constructor ctor = clazz.getConstructor(Locale.class);
            mAddLabelsMethod = clazz.getDeclaredMethod("addLabels", Locale.class);
            mSetMaxLabelCountMethod = clazz.getDeclaredMethod("setMaxLabelCount", int.class);
            mGetBucketIndexMethod = clazz.getDeclaredMethod("getBucketIndex", String.class);
            mGetBucketLabelMethod = clazz.getDeclaredMethod("getBucketLabel", int.class);
            mAlphabeticIndex = ctor.newInstance(curLocale);
            try {
                // Ensure we always have some base English locale buckets
                if (!curLocale.getLanguage().equals(Locale.ENGLISH.getLanguage())) {
                    mAddLabelsMethod.invoke(mAlphabeticIndex, Locale.ENGLISH);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (curLocale.getLanguage().equals(Locale.JAPANESE.getLanguage())) {
                // Japanese character 他 ("misc")
                mDefaultMiscLabel = "\u4ed6";
                // TODO(winsonc, omakoto): We need to handle Japanese sections better, especially the kanji
            } else {
                // Dot
                mDefaultMiscLabel = MID_DOT;
            }
            mHasValidAlphabeticIndex = true;
        } catch (Exception e) {
            mHasValidAlphabeticIndex = false;
        }
    }

    /**
     * Sets the max number of the label buckets in this index.
     * (ICU 51 default is 99)
     */
    public void setMaxLabelCount(int count) {
        if (mHasValidAlphabeticIndex) {
            try {
                mSetMaxLabelCountMethod.invoke(mAlphabeticIndex, count);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            super.setMaxLabelCount(count);
        }
    }

    /**
     * Computes the section name for an given string {@param s}.
     */
    public String computeSectionName(CharSequence cs) {
        String s = Utilities.trim(cs);
        String sectionName = getBucketLabel(getBucketIndex(s));
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
     * Returns the index of the bucket in which {@param s} should appear.
     * Function is synchronized because underlying routine walks an iterator
     * whose state is maintained inside the index object.
     */
    protected int getBucketIndex(String s) {
        if (mHasValidAlphabeticIndex) {
            try {
                return (Integer) mGetBucketIndexMethod.invoke(mAlphabeticIndex, s);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return super.getBucketIndex(s);
    }

    /**
     * Returns the label for the bucket at the given index (as returned by getBucketIndex).
     */
    protected String getBucketLabel(int index) {
        if (mHasValidAlphabeticIndex) {
            try {
                return (String) mGetBucketLabelMethod.invoke(mAlphabeticIndex, index);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return super.getBucketLabel(index);
    }
}
