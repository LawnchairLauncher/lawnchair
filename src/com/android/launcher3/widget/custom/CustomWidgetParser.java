/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.widget.custom;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.os.Parcel;
import android.os.Process;
import android.util.SparseArray;
import android.util.Xml;

import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.android.launcher3.LauncherAppWidgetProviderInfo.CLS_CUSTOM_WIDGET_PREFIX;

/**
 * Utility class to parse {@ink CustomAppWidgetProviderInfo} definitions from xml
 */
public class CustomWidgetParser {

    private static List<LauncherAppWidgetProviderInfo> sCustomWidgets;
    private static SparseArray<ComponentName> sWidgetsIdMap;

    public static List<LauncherAppWidgetProviderInfo> getCustomWidgets(Context context) {
        if (sCustomWidgets == null) {
            // Synchronization not needed as it it safe to load multiple times
            parseCustomWidgets(context);
        }

        return sCustomWidgets;
    }

    public static int getWidgetIdForCustomProvider(Context context, ComponentName provider) {
        if (sWidgetsIdMap == null) {
            parseCustomWidgets(context);
        }
        int index = sWidgetsIdMap.indexOfValue(provider);
        if (index >= 0) {
            return LauncherAppWidgetInfo.CUSTOM_WIDGET_ID - sWidgetsIdMap.keyAt(index);
        } else {
            return AppWidgetManager.INVALID_APPWIDGET_ID;
        }
    }

    public static LauncherAppWidgetProviderInfo getWidgetProvider(Context context, int widgetId) {
        if (sWidgetsIdMap == null || sCustomWidgets == null) {
            parseCustomWidgets(context);
        }
        ComponentName cn = sWidgetsIdMap.get(LauncherAppWidgetInfo.CUSTOM_WIDGET_ID - widgetId);
        for (LauncherAppWidgetProviderInfo info : sCustomWidgets) {
            if (info.provider.equals(cn)) {
                return info;
            }
        }
        return null;
    }

    private static void parseCustomWidgets(Context context) {
        ArrayList<LauncherAppWidgetProviderInfo> widgets = new ArrayList<>();
        SparseArray<ComponentName> idMap = new SparseArray<>();

        List<AppWidgetProviderInfo> providers = AppWidgetManager.getInstance(context)
                .getInstalledProvidersForProfile(Process.myUserHandle());
        if (providers.isEmpty()) {
            sCustomWidgets = widgets;
            sWidgetsIdMap = idMap;
            return;
        }

        Parcel parcel = Parcel.obtain();
        providers.get(0).writeToParcel(parcel, 0);

        try (XmlResourceParser parser = context.getResources().getXml(R.xml.custom_widgets)) {
            final int depth = parser.getDepth();
            int type;

            while (((type = parser.next()) != XmlPullParser.END_TAG ||
                    parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
                if ((type == XmlPullParser.START_TAG) && "widget".equals(parser.getName())) {
                    TypedArray a = context.obtainStyledAttributes(
                            Xml.asAttributeSet(parser), R.styleable.CustomAppWidgetProviderInfo);

                    parcel.setDataPosition(0);
                    CustomAppWidgetProviderInfo info = newInfo(a, parcel, context);
                    widgets.add(info);
                    a.recycle();

                    idMap.put(info.providerId, info.provider);
                }
            }
        } catch (IOException | XmlPullParserException e) {
            throw new RuntimeException(e);
        }
        parcel.recycle();
        sCustomWidgets = widgets;
        sWidgetsIdMap = idMap;
    }

    private static CustomAppWidgetProviderInfo newInfo(TypedArray a, Parcel parcel, Context context) {
        int providerId = a.getInt(R.styleable.CustomAppWidgetProviderInfo_providerId, 0);
        CustomAppWidgetProviderInfo info = new CustomAppWidgetProviderInfo(parcel, false, providerId);
        info.provider = new ComponentName(context.getPackageName(), CLS_CUSTOM_WIDGET_PREFIX + providerId);

        info.label = a.getString(R.styleable.CustomAppWidgetProviderInfo_android_label);
        info.initialLayout = a.getResourceId(R.styleable.CustomAppWidgetProviderInfo_android_initialLayout, 0);
        info.icon = a.getResourceId(R.styleable.CustomAppWidgetProviderInfo_android_icon, 0);
        info.previewImage = a.getResourceId(R.styleable.CustomAppWidgetProviderInfo_android_previewImage, 0);
        info.resizeMode = a.getInt(R.styleable.CustomAppWidgetProviderInfo_android_resizeMode, 0);

        info.spanX = a.getInt(R.styleable.CustomAppWidgetProviderInfo_numColumns, 1);
        info.spanY = a.getInt(R.styleable.CustomAppWidgetProviderInfo_numRows, 1);
        info.minSpanX = a.getInt(R.styleable.CustomAppWidgetProviderInfo_numMinColumns, 1);
        info.minSpanY = a.getInt(R.styleable.CustomAppWidgetProviderInfo_numMinRows, 1);
        return info;
    }
}
