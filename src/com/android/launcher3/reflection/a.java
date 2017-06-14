package com.android.launcher3.reflection;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.Iterator;
import java.util.List;
import android.content.SharedPreferences;

public class a
{
    private final SharedPreferences mPrefs;

    public a(final SharedPreferences mPrefs) {
        this.mPrefs = mPrefs;
    }

    public static String T(final List list) {
        final StringBuilder sb = new StringBuilder();
        final Iterator<b_research> iterator = (Iterator<b_research>)list.iterator();
        while (iterator.hasNext()) {
            final Matcher matcher = m.bg.matcher(iterator.next().Ld);
            if (matcher.find()) {
                final String group = matcher.group(1);
                final String group2 = matcher.group(2);
                final String group3 = matcher.group(4);
                if (sb.length() > 0) {
                    sb.append(";");
                }
                sb.append(group);
                sb.append("/");
                sb.append(group2);
                if (group3 == null) {
                    continue;
                }
                sb.append("#");
                sb.append(group3);
            }
        }
        return sb.toString();
    }

    public void U(final List list) {
        this.mPrefs.edit().putString("reflection_last_predictions", T(list)).putLong("reflection_last_predictions_timestamp", System.currentTimeMillis()).apply();
    }
}
