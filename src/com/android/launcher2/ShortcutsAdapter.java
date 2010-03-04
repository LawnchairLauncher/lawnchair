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

package com.android.launcher2;

import android.content.Context;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

import com.android.launcher.R;

/**
 * GridView adapter to show the list of applications and shortcuts
 */
public class ShortcutsAdapter  extends ArrayAdapter<ShortcutInfo> {
    private final LayoutInflater mInflater;
    private final IconCache mIconCache;

    public ShortcutsAdapter(Context context, ArrayList<ShortcutInfo> apps) {
        super(context, 0, apps);
        mInflater = LayoutInflater.from(context);
        mIconCache = ((LauncherApplication)context.getApplicationContext()).getIconCache();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ShortcutInfo info = getItem(position);

        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.application_boxed, parent, false);
        }

        final TextView textView = (TextView) convertView;
        textView.setCompoundDrawablesWithIntrinsicBounds(null,
                new FastBitmapDrawable(info.getIcon(mIconCache)), null, null);
        textView.setText(info.title);

        return convertView;
    }
}
