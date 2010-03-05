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

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageView;

import java.io.IOException;
import java.util.ArrayList;

import com.android.launcher.R;

public class WallpaperChooser extends Activity implements AdapterView.OnItemSelectedListener,
        OnClickListener {
    private static final String TAG = "Launcher.WallpaperChooser";

    private Gallery mGallery;
    private ImageView mImageView;
    private boolean mIsWallpaperSet;

    private Bitmap mBitmap;

    private ArrayList<Integer> mThumbs;
    private ArrayList<Integer> mImages;
    private WallpaperLoader mLoader;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        findWallpapers();

        setContentView(R.layout.wallpaper_chooser);

        mGallery = (Gallery) findViewById(R.id.gallery);
        mGallery.setAdapter(new ImageAdapter(this));
        mGallery.setOnItemSelectedListener(this);
        mGallery.setCallbackDuringFling(false);

        findViewById(R.id.set).setOnClickListener(this);

        mImageView = (ImageView) findViewById(R.id.wallpaper);
    }

    private void findWallpapers() {
        mThumbs = new ArrayList<Integer>(24);
        mImages = new ArrayList<Integer>(24);

        final Resources resources = getResources();
        // Context.getPackageName() may return the "original" package name,
        // com.android.launcher2; Resources needs the real package name,
        // com.android.launcher. So we ask Resources for what it thinks the
        // package name should be.
        final String packageName = resources.getResourcePackageName(R.array.wallpapers);

        addWallpapers(resources, packageName, R.array.wallpapers);
        addWallpapers(resources, packageName, R.array.extra_wallpapers);
    }

    private void addWallpapers(Resources resources, String packageName, int list) {
        final String[] extras = resources.getStringArray(list);
        for (String extra : extras) {
            int res = resources.getIdentifier(extra, "drawable", packageName);
            if (res != 0) {
                final int thumbRes = resources.getIdentifier(extra + "_small",
                        "drawable", packageName);

                if (thumbRes != 0) {
                    mThumbs.add(thumbRes);
                    mImages.add(res);
                    // Log.d(TAG, "addWallpapers: [" + packageName + "]: " + extra + " (" + res + ")");
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsWallpaperSet = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        if (mLoader != null && mLoader.getStatus() != WallpaperLoader.Status.FINISHED) {
            mLoader.cancel(true);
            mLoader = null;
        }
    }

    public void onItemSelected(AdapterView parent, View v, int position, long id) {
        if (mLoader != null && mLoader.getStatus() != WallpaperLoader.Status.FINISHED) {
            mLoader.cancel();
        }
        mLoader = (WallpaperLoader) new WallpaperLoader().execute(position);
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
            WallpaperManager wpm = (WallpaperManager)getSystemService(WALLPAPER_SERVICE);
            wpm.setResource(mImages.get(position));
            setResult(RESULT_OK);
            finish();
        } catch (IOException e) {
            Log.e(TAG, "Failed to set wallpaper: " + e);
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
            return mThumbs.size();
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
            
            int thumbRes = mThumbs.get(position);
            image.setImageResource(thumbRes);
            Drawable thumbDrawable = image.getDrawable();
            if (thumbDrawable != null) {
                thumbDrawable.setDither(true);
            } else {
                Log.e(TAG, "Error decoding thumbnail resId=" + thumbRes + " for wallpaper #"
                        + position);
            }
            return image;
        }
    }

    public void onClick(View v) {
        selectWallpaper(mGallery.getSelectedItemPosition());
    }

    class WallpaperLoader extends AsyncTask<Integer, Void, Bitmap> {
        BitmapFactory.Options mOptions;

        WallpaperLoader() {
            mOptions = new BitmapFactory.Options();
            mOptions.inDither = false;
            mOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;            
        }
        
        protected Bitmap doInBackground(Integer... params) {
            if (isCancelled()) return null;
            try {
                return BitmapFactory.decodeResource(getResources(),
                        mImages.get(params[0]), mOptions);
            } catch (OutOfMemoryError e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap b) {
            if (b == null) return;

            if (!isCancelled() && !mOptions.mCancel) {
                // Help the GC
                if (mBitmap != null) {
                    mBitmap.recycle();
                }
    
                final ImageView view = mImageView;
                view.setImageBitmap(b);
    
                mBitmap = b;
    
                final Drawable drawable = view.getDrawable();
                drawable.setFilterBitmap(true);
                drawable.setDither(true);

                view.postInvalidate();

                mLoader = null;
            } else {
               b.recycle(); 
            }
        }

        void cancel() {
            mOptions.requestCancelDecode();
            super.cancel(true);
        }
    }
}
