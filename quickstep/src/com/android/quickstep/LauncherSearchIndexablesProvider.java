/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.quickstep;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.ResolveInfo;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Build;
import android.provider.SearchIndexablesContract.XmlResource;
import android.provider.SearchIndexablesProvider;
import android.util.Xml;

import com.android.launcher3.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

import static android.provider.SearchIndexablesContract.INDEXABLES_RAW_COLUMNS;
import static android.provider.SearchIndexablesContract.INDEXABLES_XML_RES_COLUMNS;
import static android.provider.SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS;

@TargetApi(Build.VERSION_CODES.O)
public class LauncherSearchIndexablesProvider extends SearchIndexablesProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor queryXmlResources(String[] strings) {
        MatrixCursor cursor = new MatrixCursor(INDEXABLES_XML_RES_COLUMNS);
        ResolveInfo settingsActivity = getContext().getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_APPLICATION_PREFERENCES)
                        .setPackage(getContext().getPackageName()), 0);
        cursor.newRow()
                .add(XmlResource.COLUMN_XML_RESID, R.xml.indexable_launcher_prefs)
                .add(XmlResource.COLUMN_INTENT_ACTION, Intent.ACTION_APPLICATION_PREFERENCES)
                .add(XmlResource.COLUMN_INTENT_TARGET_PACKAGE, getContext().getPackageName())
                .add(XmlResource.COLUMN_INTENT_TARGET_CLASS, settingsActivity.activityInfo.name);
        return cursor;
    }

    @Override
    public Cursor queryRawData(String[] projection) {
        return new MatrixCursor(INDEXABLES_RAW_COLUMNS);
    }

    @Override
    public Cursor queryNonIndexableKeys(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(NON_INDEXABLES_KEYS_COLUMNS);
        if (!getContext().getSystemService(LauncherApps.class).hasShortcutHostPermission()) {
            // We are not the current launcher. Hide all preferences
            try (XmlResourceParser parser = getContext().getResources()
                    .getXml(R.xml.indexable_launcher_prefs)) {
                final int depth = parser.getDepth();
                final int[] attrs = new int[] { android.R.attr.key };
                int type;
                while (((type = parser.next()) != XmlPullParser.END_TAG ||
                        parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
                    if (type == XmlPullParser.START_TAG) {
                        TypedArray a = getContext().obtainStyledAttributes(
                                Xml.asAttributeSet(parser), attrs);
                        cursor.addRow(new String[] {a.getString(0)});
                        a.recycle();
                    }
                }
            } catch (IOException |XmlPullParserException e) {
                throw new RuntimeException(e);
            }
        }
        return cursor;
    }
}
