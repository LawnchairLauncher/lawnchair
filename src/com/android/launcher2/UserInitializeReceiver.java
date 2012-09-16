/*
 * Copyright (C) 2012 The Android Open Source Project
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

import java.io.IOException;
import java.util.ArrayList;

import com.android.launcher.R;

import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;

/**
 * Takes care of setting initial wallpaper for a user, by selecting the
 * first wallpaper that is not in use by another user.
 */
public class UserInitializeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final Resources resources = context.getResources();
        // Context.getPackageName() may return the "original" package name,
        // com.android.launcher2; Resources needs the real package name,
        // com.android.launcher. So we ask Resources for what it thinks the
        // package name should be.
        final String packageName = resources.getResourcePackageName(R.array.wallpapers);
        ArrayList<Integer> list = new ArrayList<Integer>();
        addWallpapers(resources, packageName, R.array.wallpapers, list);
        addWallpapers(resources, packageName, R.array.extra_wallpapers, list);
        WallpaperManager wpm = (WallpaperManager) context.getSystemService(
                Context.WALLPAPER_SERVICE);
        for (int i=1; i<list.size(); i++) {
            int resid = list.get(i);
            if (!wpm.hasResourceWallpaper(resid)) {
                try {
                    wpm.setResource(resid);
                } catch (IOException e) {
                }
                return;
            }
        }
    }

    private void addWallpapers(Resources resources, String packageName, int resid,
            ArrayList<Integer> outList) {
        final String[] extras = resources.getStringArray(resid);
        for (String extra : extras) {
            int res = resources.getIdentifier(extra, "drawable", packageName);
            if (res != 0) {
                outList.add(res);
            }
        }
    }
}
