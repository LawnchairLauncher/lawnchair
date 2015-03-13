package com.android.launcher3.compat;

import android.content.Context;

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

    private Object mAlphabeticIndex;
    private Method mAddLabelsMethod;
    private Method mSetMaxLabelCountMethod;
    private Method mGetBucketIndexMethod;
    private Method mGetBucketLabelMethod;
    private boolean mHasValidAlphabeticIndex;

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
                if (!curLocale.getLanguage().equals(new Locale("en").getLanguage())) {
                    mAddLabelsMethod.invoke(mAlphabeticIndex, Locale.ENGLISH);
                }
            } catch (Exception e) {
                e.printStackTrace();
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
    public String computeSectionName(String s) {
        String sectionName = getBucketLabel(getBucketIndex(s));
        if (sectionName.trim().isEmpty() && s.length() > 0) {
            boolean startsWithDigit = Character.isDigit(s.charAt(0));
            if (startsWithDigit) {
                // Digit section
                return "#";
            } else {
                // Unknown section
                return "\u2022";
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
