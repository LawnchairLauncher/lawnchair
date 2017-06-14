package com.android.launcher3.reflection;

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.Context;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class m
{
    public static final Pattern bg;
    public static final List bh;

    static {
        bg = Pattern.compile("^([^/]+)/([^#/]+)(#(\\d+))?$");
        bh = Collections.unmodifiableList((List<?>)Arrays.asList("reflection.engine", "reflection.engine.background", "reflection.events", "model.properties.xml", "reflection_multi_process.xml", "client_actions"));
    }

    public static SharedPreferences aJ(final Context context) {
        return context.getSharedPreferences("reflection.private.properties", 0);
    }

    public static String aK(final ComponentName componentName) {
        return componentName.flattenToString();
    }
}
