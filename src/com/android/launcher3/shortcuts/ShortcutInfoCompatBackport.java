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

package com.android.launcher3.shortcuts;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Process;
import android.os.UserHandle;

import com.android.launcher3.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ShortcutInfoCompatBackport extends ShortcutInfoCompat {
    private final static String USE_PACKAGE = "shortcut_backport_use_package";

    static Intent stripPackage(Intent intent) {
        intent = new Intent(intent);
        if (!intent.getBooleanExtra(ShortcutInfoCompatBackport.USE_PACKAGE, true)) {
            intent.setPackage(null);
        }
        intent.removeExtra(ShortcutInfoCompatBackport.USE_PACKAGE);
        return intent;
    }

    private final Context mContext;
    private final String mPackageName;
    private final ComponentName mActivity;

    private final String mId;
    private final boolean mEnabled;
    private final Integer mIcon;
    private final String mShortLabel;
    private final String mLongLabel;
    private final String mDisabledMessage;

    private final Intent mIntent;

    public ShortcutInfoCompatBackport(Context context, Resources resources, String packageName, ComponentName activity, XmlResourceParser parseXml) throws XmlPullParserException, IOException {
        super(null);
        mContext = context;
        mPackageName = packageName;
        mActivity = activity;

        HashMap<String, String> xmlData = new HashMap<>();
        for (int i = 0; i < parseXml.getAttributeCount(); i++) {
            xmlData.put(parseXml.getAttributeName(i), parseXml.getAttributeValue(i));
        }

        mId = xmlData.get("shortcutId");

        mEnabled = !xmlData.containsKey("enabled") || xmlData.get("enabled").toLowerCase().equals("true");

        mIcon = xmlData.containsKey("icon") ?
                Integer.valueOf(xmlData.get("icon").substring(1)) :
                0;

        mShortLabel = xmlData.containsKey("shortcutShortLabel") ?
                resources.getString(Integer.valueOf(xmlData.get("shortcutShortLabel").substring(1))) :
                "";

        mLongLabel = xmlData.containsKey("shortcutLongLabel") ?
                resources.getString(Integer.valueOf(xmlData.get("shortcutLongLabel").substring(1))) :
                mShortLabel;

        mDisabledMessage = xmlData.containsKey("shortcutDisabledMessage") ?
                resources.getString(Integer.valueOf(xmlData.get("shortcutDisabledMessage").substring(1))) :
                "";

        HashMap<String, String> xmlDataIntent = new HashMap<>();
        HashMap<String, String> xmlDataExtras = new HashMap<>();
        HashMap<String, String> extras = new HashMap<>();
        int startDepth = parseXml.getDepth();
        do {
            if (parseXml.nextToken() == XmlPullParser.START_TAG) {
                String xmlName = parseXml.getName();
                if (xmlName.equals("intent")) {
                    xmlDataIntent.clear();
                    extras.clear();
                    for (int i = 0; i < parseXml.getAttributeCount(); i++) {
                        xmlDataIntent.put(parseXml.getAttributeName(i), parseXml.getAttributeValue(i));
                    }
                } else if (xmlName.equals("extra")) {
                    xmlDataExtras.clear();
                    for (int i = 0; i < parseXml.getAttributeCount(); i++) {
                        xmlDataExtras.put(parseXml.getAttributeName(i), parseXml.getAttributeValue(i));
                    }
                    if (xmlDataExtras.containsKey("name") && xmlDataExtras.containsKey("value")) {
                        extras.put(xmlDataExtras.get("name"), xmlDataExtras.get("value"));
                    }
                }
            }
        } while (parseXml.getDepth() > startDepth);

        String action = xmlDataIntent.containsKey("action") ?
                xmlDataIntent.get("action") :
                Intent.ACTION_MAIN;

        boolean useTargetPackage = xmlDataIntent.containsKey("targetPackage");
        String targetPackage = useTargetPackage ?
                xmlDataIntent.get("targetPackage") :
                mPackageName;

        mIntent = new Intent(action)
                .setPackage(targetPackage)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME)
                .putExtra(EXTRA_SHORTCUT_ID, mId);

        if (xmlDataIntent.containsKey("targetClass")) {
            mIntent.setComponent(new ComponentName(targetPackage, xmlDataIntent.get("targetClass")));
        }

        if (xmlDataIntent.containsKey("data")) {
            mIntent.setData(Uri.parse(xmlDataIntent.get("data")));
        }

        for (Map.Entry<String, String> entry : extras.entrySet()) {
            mIntent.putExtra(entry.getKey(), entry.getValue());
        }
        
        mIntent.putExtra(USE_PACKAGE, useTargetPackage);
    }

    public Drawable getIcon(int density) {
        try {
            return mContext.getPackageManager()
                    .getResourcesForApplication(mPackageName)
                    .getDrawableForDensity(mIcon, density);
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) {
            return mContext.getResources()
                    .getDrawableForDensity(R.drawable.ic_default_shortcut, density);
        }
    }

    @Override
    public Intent makeIntent() {
        return mIntent;
    }

    @Override
    public String getPackage() {
        return mIntent.getPackage();
    }

    @Override
    public String getId() {
        return mId;
    }

    @Override
    public CharSequence getShortLabel() {
        return mShortLabel;
    }

    @Override
    public CharSequence getLongLabel() {
        return mLongLabel;
    }

    @Override
    public ComponentName getActivity() {
        return mIntent.getComponent() == null ? mActivity : mIntent.getComponent();
    }

    @Override
    public UserHandle getUserHandle() {
        return Process.myUserHandle();
    }

    @Override
    public boolean isPinned() {
        return false;
    }

    @Override
    public boolean isDeclaredInManifest() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return mEnabled;
    }

    @Override
    public boolean isDynamic() {
        return false;
    }

    @Override
    public int getRank() {
        return 1;
    }

    @Override
    public CharSequence getDisabledMessage() {
        return mDisabledMessage;
    }

    @Override
    public String toString() {
        return "";
    }
}
