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
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

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

    private List<LiveWallpaperInfo> mWallpapers;

    @SuppressWarnings("unchecked")
    public LiveWallpaperListAdapter(Context context) {
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPackageManager = context.getPackageManager();

        List<ResolveInfo> list = mPackageManager.queryIntentServices(
                new Intent(WallpaperService.SERVICE_INTERFACE),
                PackageManager.GET_META_DATA);

        mWallpapers = generatePlaceholderViews(list.size());

        new LiveWallpaperEnumerator(context).execute(list);
    }

    private List<LiveWallpaperInfo> generatePlaceholderViews(int amount) {
        ArrayList<LiveWallpaperInfo> list = new ArrayList<LiveWallpaperInfo>(amount);
        for (int i = 0; i < amount; i++) {
            LiveWallpaperInfo info = new LiveWallpaperInfo();
            list.add(info);
        }
        return list;
    }

    public int getCount() {
        if (mWallpapers == null) {
            return 0;
        }
        return mWallpapers.size();
    }

    public Object getItem(int position) {
        return mWallpapers.get(position);
    }

    public long getItemId(int position) {
        return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        View view;

        if (convertView == null) {
            view = mInflater.inflate(R.layout.live_wallpaper_picker_item, parent, false);
        } else {
            view = convertView;
        }

        WallpaperPickerActivity.setWallpaperItemPaddingToZero((FrameLayout) view);

        LiveWallpaperInfo wallpaperInfo = mWallpapers.get(position);
        ImageView image = (ImageView) view.findViewById(R.id.wallpaper_image);
        ImageView icon = (ImageView) view.findViewById(R.id.wallpaper_icon);
        if (wallpaperInfo.thumbnail != null) {
            image.setImageDrawable(wallpaperInfo.thumbnail);
            icon.setVisibility(View.GONE);
        } else {
            icon.setImageDrawable(wallpaperInfo.info.loadIcon(mPackageManager));
            icon.setVisibility(View.VISIBLE);
        }

        TextView label = (TextView) view.findViewById(R.id.wallpaper_item_label);
        label.setText(wallpaperInfo.info.loadLabel(mPackageManager));

        return view;
    }

    public class LiveWallpaperInfo {
        public Drawable thumbnail;
        public WallpaperInfo info;
        public Intent intent;
    }

    private class LiveWallpaperEnumerator extends
            AsyncTask<List<ResolveInfo>, LiveWallpaperInfo, Void> {
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

                LiveWallpaperInfo wallpaper = new LiveWallpaperInfo();
                wallpaper.intent = new Intent(WallpaperService.SERVICE_INTERFACE);
                wallpaper.intent.setClassName(info.getPackageName(), info.getServiceName());
                wallpaper.info = info;

                Drawable thumb = info.loadThumbnail(packageManager);
                // TODO: generate a default thumb
                /*
                final Resources res = mContext.getResources();
                Canvas canvas = new Canvas();
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
                paint.setTextAlign(Paint.Align.CENTER);
                BitmapDrawable galleryIcon = (BitmapDrawable) res.getDrawable(
                        R.drawable.livewallpaper_placeholder);
                if (thumb == null) {
                    int thumbWidth = res.getDimensionPixelSize(
                            R.dimen.live_wallpaper_thumbnail_width);
                    int thumbHeight = res.getDimensionPixelSize(
                            R.dimen.live_wallpaper_thumbnail_height);

                    Bitmap thumbnail = Bitmap.createBitmap(thumbWidth, thumbHeight,
                            Bitmap.Config.ARGB_8888);

                    paint.setColor(res.getColor(R.color.live_wallpaper_thumbnail_background));
                    canvas.setBitmap(thumbnail);
                    canvas.drawPaint(paint);

                    galleryIcon.setBounds(0, 0, thumbWidth, thumbHeight);
                    galleryIcon.setGravity(Gravity.CENTER);
                    galleryIcon.draw(canvas);

                    String title = info.loadLabel(packageManager).toString();

                    paint.setColor(res.getColor(R.color.live_wallpaper_thumbnail_text_color));
                    paint.setTextSize(
                            res.getDimensionPixelSize(R.dimen.live_wallpaper_thumbnail_text_size));

                    canvas.drawText(title, (int) (thumbWidth * 0.5),
                            thumbHeight - res.getDimensionPixelSize(
                                    R.dimen.live_wallpaper_thumbnail_text_offset), paint);

                    thumb = new BitmapDrawable(res, thumbnail);
                }*/
                wallpaper.thumbnail = thumb;
                publishProgress(wallpaper);
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(LiveWallpaperInfo...infos) {
            for (LiveWallpaperInfo info : infos) {
                info.thumbnail.setDither(true);
                if (mWallpaperPosition < mWallpapers.size()) {
                    mWallpapers.set(mWallpaperPosition, info);
                } else {
                    mWallpapers.add(info);
                }
                mWallpaperPosition++;
                if (mWallpaperPosition == getCount()) {
                    LiveWallpaperListAdapter.this.notifyDataSetChanged();
                }
            }
        }
    }
}
