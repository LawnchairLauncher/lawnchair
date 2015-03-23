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

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.android.launcher3.util.Thunk;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LiveWallpaperListAdapter extends BaseAdapter implements ListAdapter {
    private static final String LOG_TAG = "LiveWallpaperListAdapter";

    private final LayoutInflater mInflater;
    private final PackageManager mPackageManager;

    @Thunk List<LiveWallpaperTile> mWallpapers;

    @SuppressWarnings("unchecked")
    public LiveWallpaperListAdapter(Context context) {
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPackageManager = context.getPackageManager();

        List<ResolveInfo> list = mPackageManager.queryIntentServices(
                new Intent(WallpaperService.SERVICE_INTERFACE),
                PackageManager.GET_META_DATA);

        mWallpapers = new ArrayList<LiveWallpaperTile>();

        new LiveWallpaperEnumerator(context).execute(list);
    }

    public int getCount() {
        if (mWallpapers == null) {
            return 0;
        }
        return mWallpapers.size();
    }

    public LiveWallpaperTile getItem(int position) {
        return mWallpapers.get(position);
    }

    public long getItemId(int position) {
        return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        View view;

        if (convertView == null) {
            view = mInflater.inflate(R.layout.wallpaper_picker_live_wallpaper_item, parent, false);
        } else {
            view = convertView;
        }

        LiveWallpaperTile wallpaperInfo = mWallpapers.get(position);
        wallpaperInfo.setView(view);
        ImageView image = (ImageView) view.findViewById(R.id.wallpaper_image);
        ImageView icon = (ImageView) view.findViewById(R.id.wallpaper_icon);
        if (wallpaperInfo.mThumbnail != null) {
            image.setImageDrawable(wallpaperInfo.mThumbnail);
            icon.setVisibility(View.GONE);
        } else {
            icon.setImageDrawable(wallpaperInfo.mInfo.loadIcon(mPackageManager));
            icon.setVisibility(View.VISIBLE);
        }

        TextView label = (TextView) view.findViewById(R.id.wallpaper_item_label);
        label.setText(wallpaperInfo.mInfo.loadLabel(mPackageManager));

        return view;
    }

    public static class LiveWallpaperTile extends WallpaperPickerActivity.WallpaperTileInfo {
        @Thunk Drawable mThumbnail;
        @Thunk WallpaperInfo mInfo;
        public LiveWallpaperTile(Drawable thumbnail, WallpaperInfo info, Intent intent) {
            mThumbnail = thumbnail;
            mInfo = info;
        }
        @Override
        public void onClick(WallpaperPickerActivity a) {
            Intent preview = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            preview.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    mInfo.getComponent());
            a.startActivityForResultSafely(preview,
                    WallpaperPickerActivity.PICK_WALLPAPER_THIRD_PARTY_ACTIVITY);
        }
    }

    private class LiveWallpaperEnumerator extends
            AsyncTask<List<ResolveInfo>, LiveWallpaperTile, Void> {
        private Context mContext;
        private int mWallpaperPosition;

        public LiveWallpaperEnumerator(Context context) {
            super();
            mContext = context;
            mWallpaperPosition = 0;
        }

        @Override
        protected Void doInBackground(List<ResolveInfo>... params) {
            final PackageManager packageManager = mContext.getPackageManager();

            List<ResolveInfo> list = params[0];

            Collections.sort(list, new Comparator<ResolveInfo>() {
                final Collator mCollator;

                {
                    mCollator = Collator.getInstance();
                }

                public int compare(ResolveInfo info1, ResolveInfo info2) {
                    return mCollator.compare(info1.loadLabel(packageManager),
                            info2.loadLabel(packageManager));
                }
            });

            for (ResolveInfo resolveInfo : list) {
                WallpaperInfo info = null;
                try {
                    info = new WallpaperInfo(mContext, resolveInfo);
                } catch (XmlPullParserException e) {
                    Log.w(LOG_TAG, "Skipping wallpaper " + resolveInfo.serviceInfo, e);
                    continue;
                } catch (IOException e) {
                    Log.w(LOG_TAG, "Skipping wallpaper " + resolveInfo.serviceInfo, e);
                    continue;
                }


                Drawable thumb = info.loadThumbnail(packageManager);
                Intent launchIntent = new Intent(WallpaperService.SERVICE_INTERFACE);
                launchIntent.setClassName(info.getPackageName(), info.getServiceName());
                LiveWallpaperTile wallpaper = new LiveWallpaperTile(thumb, info, launchIntent);
                publishProgress(wallpaper);
            }
            // Send a null object to show loading is finished
            publishProgress((LiveWallpaperTile) null);

            return null;
        }

        @Override
        protected void onProgressUpdate(LiveWallpaperTile...infos) {
            for (LiveWallpaperTile info : infos) {
                if (info == null) {
                    LiveWallpaperListAdapter.this.notifyDataSetChanged();
                    break;
                }
                if (info.mThumbnail != null) {
                    info.mThumbnail.setDither(true);
                }
                if (mWallpaperPosition < mWallpapers.size()) {
                    mWallpapers.set(mWallpaperPosition, info);
                } else {
                    mWallpapers.add(info);
                }
                mWallpaperPosition++;
            }
        }
    }
}
