/*
 * Copyright (C) 2009 The Android Open Source Project
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
import android.app.ListActivity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Drawable;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.SystemClock;
import android.service.wallpaper.IWallpaperConnection;
import android.service.wallpaper.IWallpaperEngine;
import android.service.wallpaper.IWallpaperService;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.ListView;

import java.text.Collator;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Displays a list of live wallpapers, allowing the user to select one
 * and make it the system global wallpaper.
 */
public class LiveWallpaperPickActivity 
    extends Activity
    implements AdapterView.OnItemSelectedListener,
               View.OnClickListener
{
    private static final String TAG = "LiveWallpaperPickActivity";

    private PackageManager mPackageManager;
    private WallpaperManager mWallpaperManager;
    
    Intent mSelectedIntent;
    WallpaperConnection mWallpaperConnection;
 
    private Gallery mGallery;

    private ArrayList<Intent> mWallpaperIntents;
 
    private ArrayList<Bitmap> mThumbBitmaps;

    class WallpaperConnection extends IWallpaperConnection.Stub
            implements ServiceConnection {
        final Intent mIntent;
        IWallpaperService mService;
        IWallpaperEngine mEngine;
        boolean mConnected;

        public WallpaperConnection(Intent intent) {
            mIntent = intent;
        }
        
        public boolean connect() {
            synchronized (this) {
                if (!bindService(mIntent, this, Context.BIND_AUTO_CREATE)) {
                    return false;
                }
                
                mConnected = true;
                return true;
            }
        }
        
        public void disconnect() {
            synchronized (this) {
                mConnected = false;
                if (mEngine != null) {
                    try {
                        mEngine.destroy();
                    } catch (RemoteException e) {
                    }
                    mEngine = null;
                }
                unbindService(this);
                mService = null;
            }
        }
        
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (mWallpaperConnection == this) {
                mService = IWallpaperService.Stub.asInterface(service);
                try {
                    View button = findViewById(R.id.set);
                    mService.attach(this, button.getWindowToken(),
                            WindowManager.LayoutParams.TYPE_APPLICATION_MEDIA,
                            true,
                            button.getRootView().getWidth(),
                            button.getRootView().getHeight());
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed attaching wallpaper; clearing", e);
                }
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mEngine = null;
            if (mWallpaperConnection == this) {
                Log.w(TAG, "Wallpaper service gone: " + name);
            }
        }
        
        public void attachEngine(IWallpaperEngine engine) {
            synchronized (this) {
                if (mConnected) {
                    mEngine = engine;
                    try {
                        engine.setVisibility(true);
                    } catch (RemoteException e) {
                    }
                } else {
                    try {
                        engine.destroy();
                    } catch (RemoteException e) {
                    }
                }
            }
        }
        
        public ParcelFileDescriptor setWallpaper(String name) {
            return null;
        }
    }

    private class ImageAdapter extends BaseAdapter {
        private LayoutInflater mLayoutInflater;

        ImageAdapter(LiveWallpaperPickActivity context) {
            mLayoutInflater = context.getLayoutInflater();
        }

        public int getCount() {
            return mThumbBitmaps.size();
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
          
            image.setImageBitmap(mThumbBitmaps.get(position));
            image.getDrawable().setDither(true);
            return image;
        }
    }


    private void findLiveWallpapers() {
        mThumbBitmaps = new ArrayList<Bitmap>(24);
        List<ResolveInfo> list = 
            mPackageManager.queryIntentServices(getTargetIntent(),
                                                  /*noflags*/ 0);
        
        mWallpaperIntents = new ArrayList<Intent>(list.size());
        
        int listSize = list.size();
        Log.d(TAG, String.format("findLiveWallpapers: found %d wallpaper services", listSize));
        for (int i = 0; i < listSize; i++) {
            ResolveInfo resolveInfo = list.get(i);
            ComponentInfo ci = resolveInfo.serviceInfo;
            String packageName = ci.applicationInfo.packageName;
            String className = ci.name;
            Log.d(TAG, String.format("findLiveWallpapers: [%d] pkg=%s cls=%s",
                i, packageName, className));
            Intent intent = new Intent(getTargetIntent());
            intent.setClassName(packageName, className);
            mWallpaperIntents.add(intent);

            Bitmap thumb = Bitmap.createBitmap(240,160,Bitmap.Config.ARGB_8888);
            android.graphics.Canvas can = new android.graphics.Canvas(thumb);
            android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG|android.graphics.Paint.DITHER_FLAG);
            pt.setARGB(255, 0, 0, 255);
            can.drawPaint(pt);
            pt.setARGB(255, 255, 255, 255);
            pt.setTextSize(12);
            can.drawText(className, 16, 150, pt);
            pt.setTextSize(80);
            can.drawText(String.format("%d", i), 100,100, pt);
            mThumbBitmaps.add(thumb);
        }


    }

    
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mPackageManager = getPackageManager();
        mWallpaperManager = WallpaperManager.getInstance(this);
        
        findLiveWallpapers();

        setContentView(R.layout.live_wallpaper_content);
        
        mGallery = (Gallery) findViewById(R.id.gallery);
        mGallery.setAdapter(new ImageAdapter(this));
        mGallery.setOnItemSelectedListener(this);
        mGallery.setCallbackDuringFling(false);

        View button = findViewById(R.id.set);
//        button.setEnabled(false);
        button.setOnClickListener(this);
        
        // Set default return data
        setResult(RESULT_CANCELED);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        if (mWallpaperConnection != null && mWallpaperConnection.mEngine != null) {
            try {
                mWallpaperConnection.mEngine.setVisibility(true);
            } catch (RemoteException e) {
            }
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        if (mWallpaperConnection != null && mWallpaperConnection.mEngine != null) {
            try {
                mWallpaperConnection.mEngine.setVisibility(false);
            } catch (RemoteException e) {
            }
        }
    }
    
    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mWallpaperConnection != null) {
            mWallpaperConnection.disconnect();
        }
        mWallpaperConnection = null;
    }
    
    protected Intent getTargetIntent() {
        return new Intent(WallpaperService.SERVICE_INTERFACE);
    }
    
    public void onItemSelected(AdapterView parent, View v, int position, long id) {
        Log.d(TAG, String.format("onItemSelected: position=%d", position));

        mSelectedIntent = mWallpaperIntents.get(position);
        findViewById(R.id.set).setEnabled(true);
        
        WallpaperConnection conn = new WallpaperConnection(mSelectedIntent);
        if (conn.connect()) {
            if (mWallpaperConnection != null) {
                mWallpaperConnection.disconnect();
            }
            mWallpaperConnection = conn;
        }
    }
    
    public void onClick(View v) { // "Set" button
        Log.d(TAG, "Set clicked");

//        mSelectedIntent = mWallpaperIntents.get(mGallery.getSelectedItemPosition());
        if (mSelectedIntent != null) {
            try {
                mWallpaperManager.getIWallpaperManager().setWallpaperComponent(
                        mSelectedIntent.getComponent());
                this.setResult(RESULT_OK);
            } catch (RemoteException e) {
                // do nothing
            } catch (RuntimeException e) {
                Log.w(TAG, "Failure setting wallpaper", e);
            }
            finish();
        }
    }

    public void onNothingSelected(AdapterView parent) {
    }

}
