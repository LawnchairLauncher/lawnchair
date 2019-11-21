package com.android.launcher3.compat;

import android.content.Context;
import android.icu.text.AlphabeticIndex;
import android.os.LocaleList;

import com.android.launcher3.Utilities;

import java.util.Locale;

import androidx.annotation.NonNull;

public class AlphabeticIndexCompat {

    private static final String MID_DOT = "\u2219";
    private final String mDefaultMiscLabel;

    private final AlphabeticIndex.ImmutableIndex mBaseIndex;

    public AlphabeticIndexCompat(Context context) {
        this(context.getResources().getConfiguration().getLocales());
    }

    public AlphabeticIndexCompat(LocaleList locales) {
        int localeCount = locales.size();

        Locale primaryLocale = localeCount == 0 ? Locale.ENGLISH : locales.get(0);
        AlphabeticIndex indexBuilder = new AlphabeticIndex(primaryLocale);
        for (int i = 1; i < localeCount; i++) {
            indexBuilder.addLabels(locales.get(i));
        }
        indexBuilder.addLabels(Locale.ENGLISH);
        mBaseIndex = indexBuilder.buildImmutableIndex();

        if (primaryLocale.getLanguage().equals(Locale.JAPANESE.getLanguage())) {
            // Japanese character 他 ("misc")
            mDefaultMiscLabel = "\u4ed6";
            // TODO(winsonc, omakoto): We need to handle Japanese sections better,
            // especially the kanji
        } else {
            // Dot
            mDefaultMiscLabel = MID_DOT;
        }
    }

    /**
     * Computes the section name for an given string {@param s}.
     */
    public String computeSectionName(@NonNull CharSequence cs) {
        String s = Utilities.trim(cs);
        String sectionName = mBaseIndex.getBucket(mBaseIndex.getBucketIndex(s)).getLabel();
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
}
