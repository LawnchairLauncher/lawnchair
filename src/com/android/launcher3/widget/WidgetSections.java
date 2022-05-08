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
package com.android.launcher3.widget;

import static android.content.res.Resources.ID_NULL;

import android.content.ComponentName;
import android.content.Context;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.Xml;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.android.launcher3.R;
import com.android.launcher3.util.IntSet;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Map;

/** A helper class to parse widget sections (categories) resource overlay. */
public final class WidgetSections {
    /** The package is not categorized in the widget tray. */
    public static final int NO_CATEGORY = -1;

    private static final String TAG_SECTION_NAME = "section";
    private static final String TAG_WIDGET_NAME = "widget";

    private static SparseArray<WidgetSection> sWidgetSections;
    private static Map<ComponentName, IntSet> sWidgetsToCategories;

    /** Returns a list of widget sections that are shown in the widget picker. */
    public static synchronized SparseArray<WidgetSection> getWidgetSections(Context context) {
        if (sWidgetSections != null) {
            return sWidgetSections;
        }
        parseWidgetSectionsXml(context);
        return sWidgetSections;
    }

    /** Returns a map which maps app widget providers to app widget categories. */
    public static synchronized Map<ComponentName, IntSet> getWidgetsToCategory(
            Context context) {
        if (sWidgetsToCategories != null) {
            return sWidgetsToCategories;
        }
        parseWidgetSectionsXml(context);
        return sWidgetsToCategories;
    }

    private static synchronized void parseWidgetSectionsXml(Context context) {
        SparseArray<WidgetSection> widgetSections = new SparseArray();
        Map<ComponentName, IntSet> widgetsToCategories = new ArrayMap<>();
        try (XmlResourceParser parser = context.getResources().getXml(R.xml.widget_sections)) {
            final int depth = parser.getDepth();
            int type;
            while (((type = parser.next()) != XmlPullParser.END_TAG
                    || parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
                if ((type == XmlPullParser.START_TAG)
                        && TAG_SECTION_NAME.equals(parser.getName())) {
                    AttributeSet sectionAttributes = Xml.asAttributeSet(parser);
                    WidgetSection section = new WidgetSection(context, sectionAttributes);
                    final int sectionDepth = parser.getDepth();
                    while (((type = parser.next()) != XmlPullParser.END_TAG
                                    || parser.getDepth() > sectionDepth)
                            && type != XmlPullParser.END_DOCUMENT) {
                        if ((type == XmlPullParser.START_TAG)
                                && TAG_WIDGET_NAME.equals(parser.getName())) {
                            TypedArray a = context.obtainStyledAttributes(
                                    Xml.asAttributeSet(parser), R.styleable.WidgetSections);
                            ComponentName provider = ComponentName.unflattenFromString(
                                    a.getString(R.styleable.WidgetSections_provider));
                            boolean alsoKeepInApp = a.getBoolean(
                                    R.styleable.WidgetSections_alsoKeepInApp,
                                    /* defValue= */ false);
                            final IntSet categories;
                            if (widgetsToCategories.containsKey(provider)) {
                                categories = widgetsToCategories.get(provider);
                            } else {
                                categories = new IntSet();
                                widgetsToCategories.put(provider, categories);
                            }
                            if (alsoKeepInApp) {
                                categories.add(NO_CATEGORY);
                            }
                            categories.add(section.mCategory);
                        }
                    }
                    widgetSections.put(section.mCategory, section);
                }
            }
            sWidgetSections = widgetSections;
            sWidgetsToCategories = widgetsToCategories;
        } catch (IOException | XmlPullParserException e) {
            throw new RuntimeException(e);
        }
    }

    /** A data class which contains a widget section's information. */
    public static final class WidgetSection {
        public final int mCategory;
        @StringRes
        public final int mSectionTitle;
        @DrawableRes
        public final int mSectionDrawable;

        public WidgetSection(Context context, AttributeSet attrs) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.WidgetSections);
            mCategory = a.getInt(R.styleable.WidgetSections_category, NO_CATEGORY);
            mSectionTitle = a.getResourceId(R.styleable.WidgetSections_sectionTitle, ID_NULL);
            mSectionDrawable = a.getResourceId(R.styleable.WidgetSections_sectionDrawable, ID_NULL);
        }
    }
}
