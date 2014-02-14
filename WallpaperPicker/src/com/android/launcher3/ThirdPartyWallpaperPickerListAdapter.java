/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.launcher3;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class ThirdPartyWallpaperPickerListAdapter extends BaseAdapter implements ListAdapter {
    private static final String LOG_TAG = "LiveWallpaperListAdapter";

    private final LayoutInflater mInflater;
    private final PackageManager mPackageManager;
    private final int mIconSize;

    private List<ThirdPartyWallpaperTile> mThirdPartyWallpaperPickers =
            new ArrayList<ThirdPartyWallpaperTile>();

    public static class ThirdPartyWallpaperTile extends WallpaperPickerActivity.WallpaperTileInfo {
        private ResolveInfo mResolveInfo;
        public ThirdPartyWallpaperTile(ResolveInfo resolveInfo) {
            mResolveInfo = resolveInfo;
        }
        @Override
        public void onClick(WallpaperPickerActivity a) {
            final ComponentName itemComponentName = new ComponentName(
                    mResolveInfo.activityInfo.packageName, mResolveInfo.activityInfo.name);
            Intent launchIntent = new Intent(Intent.ACTION_SET_WALLPAPER);
            launchIntent.setComponent(itemComponentName);
            a.startActivityForResultSafely(
                    launchIntent, WallpaperPickerActivity.PICK_WALLPAPER_THIRD_PARTY_ACTIVITY);
        }
    }

    public ThirdPartyWallpaperPickerListAdapter(Context context) {
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPackageManager = context.getPackageManager();
        mIconSize = context.getResources().getDimensionPixelSize(R.dimen.wallpaperItemIconSize);
        final PackageManager pm = mPackageManager;

        final Intent pickWallpaperIntent = new Intent(Intent.ACTION_SET_WALLPAPER);
        final List<ResolveInfo> apps =
                pm.queryIntentActivities(pickWallpaperIntent, 0);

        // Get list of image picker intents
        Intent pickImageIntent = new Intent(Intent.ACTION_GET_CONTENT);
        pickImageIntent.setType("image/*");
        final List<ResolveInfo> imagePickerActivities =
                pm.queryIntentActivities(pickImageIntent, 0);
        final ComponentName[] imageActivities = new ComponentName[imagePickerActivities.size()];
        for (int i = 0; i < imagePickerActivities.size(); i++) {
            ActivityInfo activityInfo = imagePickerActivities.get(i).activityInfo;
            imageActivities[i] = new ComponentName(activityInfo.packageName, activityInfo.name);
        }

        outerLoop:
        for (ResolveInfo info : apps) {
            final ComponentName itemComponentName =
                    new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
            final String itemPackageName = itemComponentName.getPackageName();
            // Exclude anything from our own package, and the old Launcher,
            // and live wallpaper picker
            if (itemPackageName.equals(context.getPackageName()) ||
                    itemPackageName.equals("com.android.launcher") ||
                    itemPackageName.equals("com.android.wallpaper.livepicker")) {
                continue;
            }
            // Exclude any package that already responds to the image picker intent
            for (ResolveInfo imagePickerActivityInfo : imagePickerActivities) {
                if (itemPackageName.equals(
                        imagePickerActivityInfo.activityInfo.packageName)) {
                    continue outerLoop;
                }
            }
            mThirdPartyWallpaperPickers.add(new ThirdPartyWallpaperTile(info));
        }
    }

    public int getCount() {
        return mThirdPartyWallpaperPickers.size();
    }

    public ThirdPartyWallpaperTile getItem(int position) {
        return mThirdPartyWallpaperPickers.get(position);
    }

    public long getItemId(int position) {
        return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        View view;

        if (convertView == null) {
            view = mInflater.inflate(R.layout.wallpaper_picker_third_party_item, parent, false);
        } else {
            view = convertView;
        }

        WallpaperPickerActivity.setWallpaperItemPaddingToZero((FrameLayout) view);

        ResolveInfo info = mThirdPartyWallpaperPickers.get(position).mResolveInfo;
        TextView label = (TextView) view.findViewById(R.id.wallpaper_item_label);
        label.setText(info.loadLabel(mPackageManager));
        Drawable icon = info.loadIcon(mPackageManager);
        icon.setBounds(new Rect(0, 0, mIconSize, mIconSize));
        label.setCompoundDrawables(null, icon, null, null);
        return view;
    }
}
