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

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashMap;

public class ShortcutInfoCompatBackport extends ShortcutInfoCompat {
    private final Context mContext;
    private final String mPackageName;
    private final String mId;
    private final String mAction;
    private final Integer mIcon;
    private final String mShortLabel;
    private final String mLongLabel;
    private final ComponentName mTargetActivity;
    private final boolean mUseTargetActivity;
    private final Uri mData;
    private final String mTargetPackage;
    private final boolean mEnabled;

    public ShortcutInfoCompatBackport(Context context, Resources resources, String packageName, ComponentName activity, XmlResourceParser parseXml) throws XmlPullParserException, IOException {
        super(null);
        mContext = context;
        mPackageName = packageName;

        HashMap<String, String> xmlData = new HashMap<>();
        for (int i = 0; i < parseXml.getAttributeCount(); i++) {
            xmlData.put(parseXml.getAttributeName(i), parseXml.getAttributeValue(i));
        }
        parseXml.nextToken();
        for (int i = 0; i < parseXml.getAttributeCount(); i++) {
            xmlData.put(parseXml.getAttributeName(i), parseXml.getAttributeValue(i));
        }

        mId = xmlData.get("shortcutId");

        mAction = xmlData.containsKey("action") ?
                xmlData.get("action") :
                Intent.ACTION_MAIN;

        mShortLabel = xmlData.containsKey("shortcutShortLabel") ?
                resources.getString(Integer.valueOf(xmlData.get("shortcutShortLabel").substring(1))) :
                "";

        mLongLabel = xmlData.containsKey("shortcutLongLabel") ?
                resources.getString(Integer.valueOf(xmlData.get("shortcutLongLabel").substring(1))) :
                mShortLabel;

        mTargetPackage = xmlData.containsKey("targetPackage") ?
                xmlData.get("targetPackage") :
                mPackageName;

        mUseTargetActivity = xmlData.containsKey("targetClass");
        mTargetActivity = mUseTargetActivity ?
                new ComponentName(getPackage(), xmlData.get("targetClass")) :
                activity;

        mIcon = xmlData.containsKey("icon") ?
                Integer.valueOf(xmlData.get("icon").substring(1)) :
                0;

        mData = xmlData.containsKey("data") ?
                Uri.parse(xmlData.get("data")) :
                null;

        mEnabled = !xmlData.containsKey("enabled") || xmlData.get("enabled").toLowerCase().equals("true");
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
        return new Intent(mAction)
                .setComponent(mUseTargetActivity ? getActivity() : null)
                .setPackage(getPackage())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME)
                .putExtra(EXTRA_SHORTCUT_ID, getId())
                .setData(mData);
    }

    @Override
    public String getPackage() {
        return mTargetPackage;
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
        return mTargetActivity;
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
        return "Disabled";
    }

    @Override
    public String toString() {
        return "";
    }
}
