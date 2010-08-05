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

package com.android.launcher2;

import com.android.launcher.R;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.provider.LiveFolders;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class IntentListAdapter extends BaseAdapter {
    private LayoutInflater mLayoutInflater;
    private PackageManager mPackageManager;
    protected List<ResolveInfo> mIntentList;

    public IntentListAdapter(Context context, String actionFilter) {
        mLayoutInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPackageManager = context.getPackageManager();

        Intent createLiveFolderIntent = new Intent(actionFilter);
        mIntentList = mPackageManager.queryIntentActivities(createLiveFolderIntent, 0);
    }

    public int getCount() {
        return mIntentList.size();
    }

    public Object getItem(int position) {
        return mIntentList.get(position);
    }

    public long getItemId(int position) {
        return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        TextView textView;

        if (convertView == null) {
            textView = (TextView) mLayoutInflater.inflate(
                    R.layout.home_customization_drawer_item, parent, false);
        } else {
            textView = (TextView) convertView;
        }

        ResolveInfo info = mIntentList.get(position);
        Drawable image = info.loadIcon(mPackageManager);
        image.setBounds(0, 0, image.getIntrinsicWidth(), image.getIntrinsicHeight());
        textView.setCompoundDrawables(null, image, null, null);

        CharSequence label = info.loadLabel(mPackageManager);
        textView.setText(label);

        return textView;
    }
}
