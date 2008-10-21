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

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.Gallery;
import android.widget.ImageView;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import java.io.IOException;
import java.io.InputStream;

public class WallpaperChooser extends Activity implements AdapterView.OnItemSelectedListener,
        OnClickListener {

    private static final Integer[] THUMB_IDS = {
            R.drawable.wallpaper_lake_small,
            R.drawable.wallpaper_sunset_small,
            R.drawable.wallpaper_beach_small,
            R.drawable.wallpaper_snow_leopard_small,
            R.drawable.wallpaper_path_small,
            R.drawable.wallpaper_sunrise_small,
            R.drawable.wallpaper_mountain_small,
            R.drawable.wallpaper_ripples_small,
            R.drawable.wallpaper_road_small,
            R.drawable.wallpaper_jellyfish_small,
            R.drawable.wallpaper_zanzibar_small,
            R.drawable.wallpaper_blue_small,
            R.drawable.wallpaper_grey_small,
            R.drawable.wallpaper_green_small,
            R.drawable.wallpaper_pink_small,
            R.drawable.wallpaper_dale_chihuly_small,
            R.drawable.wallpaper_john_maeda_small,
            R.drawable.wallpaper_marc_ecko_small,
    };

    private static final Integer[] IMAGE_IDS = {
            R.drawable.wallpaper_lake,
            R.drawable.wallpaper_sunset,
            R.drawable.wallpaper_beach,
            R.drawable.wallpaper_snow_leopard,
            R.drawable.wallpaper_path,
            R.drawable.wallpaper_sunrise,
            R.drawable.wallpaper_mountain,
            R.drawable.wallpaper_ripples,
            R.drawable.wallpaper_road,
            R.drawable.wallpaper_jellyfish,
            R.drawable.wallpaper_zanzibar,
            R.drawable.wallpaper_blue,
            R.drawable.wallpaper_grey,
            R.drawable.wallpaper_green,
            R.drawable.wallpaper_pink,
            R.drawable.wallpaper_dale_chihuly,
            R.drawable.wallpaper_john_maeda,
            R.drawable.wallpaper_marc_ecko,
    };

    private Gallery mGallery;
    private ImageView mImageView;
    private boolean mIsWallpaperSet;

    private BitmapFactory.Options mOptions;
    private Bitmap mBitmap;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.wallpaper_chooser);

        mOptions = new BitmapFactory.Options();
        mOptions.inDither = false;
        mOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;

        mGallery = (Gallery) findViewById(R.id.gallery);
        mGallery.setAdapter(new ImageAdapter(this));
        mGallery.setOnItemSelectedListener(this);
        
        Button b = (Button) findViewById(R.id.set);
        b.setOnClickListener(this);

        mImageView = (ImageView) findViewById(R.id.wallpaper);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsWallpaperSet = false;
    }

    public void onItemSelected(AdapterView parent, View v, int position, long id) {
        final ImageView view = mImageView;
        Bitmap b = BitmapFactory.decodeResource(getResources(), IMAGE_IDS[position], mOptions);
        view.setImageBitmap(b);

        // Help the GC
        if (mBitmap != null) {
            mBitmap.recycle();
        }
        mBitmap = b;

        final Drawable drawable = view.getDrawable();
        drawable.setFilterBitmap(true);
        drawable.setDither(true);
    }

    /*
     * When using touch if you tap an image it triggers both the onItemClick and
     * the onTouchEvent causing the wallpaper to be set twice. Ensure we only
     * set the wallpaper once.
     */
    private void selectWallpaper(int position) {
        if (mIsWallpaperSet) {
            return;
        }

        mIsWallpaperSet = true;
        try {
            InputStream stream = getResources().openRawResource(IMAGE_IDS[position]);
            setWallpaper(stream);
            setResult(RESULT_OK);
            finish();
        } catch (IOException e) {
            Log.e(Launcher.LOG_TAG, "Failed to set wallpaper: " + e);
        }
    }

    public void onNothingSelected(AdapterView parent) {
    }

    private class ImageAdapter extends BaseAdapter {
        private LayoutInflater mLayoutInflater;

        ImageAdapter(WallpaperChooser context) {
            mLayoutInflater = context.getLayoutInflater();
        }

        public int getCount() {
            return THUMB_IDS.length;
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView image;

            if (convertView == null) {
                image = (ImageView) mLayoutInflater.inflate(R.layout.wallpaper_item, parent, false);
            } else {
                image = (ImageView) convertView;
            }

            image.setImageResource(THUMB_IDS[position]);
            image.getDrawable().setDither(true);
            return image;
        }
    }

    public void onClick(View v) {
        selectWallpaper(mGallery.getSelectedItemPosition());
    }
}
