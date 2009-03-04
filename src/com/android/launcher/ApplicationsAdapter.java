/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * GridView adapter to show the list of applications and shortcuts
 */
public class ApplicationsAdapter extends ArrayAdapter<ApplicationInfo> {
    private final LayoutInflater mInflater;

    public ApplicationsAdapter(Context context, ArrayList<ApplicationInfo> apps) {
        super(context, 0, apps);
        mInflater = LayoutInflater.from(context);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ApplicationInfo info = getItem(position);

        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.application_boxed, parent, false);
        }

        if (!info.filtered) {
            info.icon = Utilities.createIconThumbnail(info.icon, getContext());
            info.filtered = true;
        }

        final TextView textView = (TextView) convertView;
        textView.setCompoundDrawablesWithIntrinsicBounds(null, info.icon, null, null);
        textView.setText(info.title);

        return convertView;
    }
}
