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

import android.app.LauncherActivity;
import android.app.ListActivity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Drawable;
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
import android.view.View;
import android.view.WindowManager;
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
public class LiveWallpaperPickActivity extends LauncherActivity
        implements View.OnClickListener {
    private static final String TAG = "LiveWallpaperPickActivity";

    private PackageManager mPackageManager;
    private WallpaperManager mWallpaperManager;
    
    Intent mSelectedIntent;
    WallpaperConnection mWallpaperConnection;
    
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
    
    
    @Override
    public void onCreate(Bundle icicle) {
        mPackageManager = getPackageManager();
        mWallpaperManager = WallpaperManager.getInstance(this);
        
        super.onCreate(icicle);
        
        View button = findViewById(R.id.set);
        button.setEnabled(false);
        button.setOnClickListener(this);
        
        // Set default return data
        setResult(RESULT_CANCELED);
    }
    
    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mWallpaperConnection != null) {
            mWallpaperConnection.disconnect();
        }
        mWallpaperConnection = null;
    }
    
    @Override
    protected void onSetContentView() {
        setContentView(R.layout.live_wallpaper_content);
    }
    
    @Override
    protected Intent getTargetIntent() {
        return new Intent(WallpaperService.SERVICE_INTERFACE);
    }
    
    @Override
    protected List<ResolveInfo> onQueryPackageManager(Intent queryIntent) {
        return mPackageManager.queryIntentServices(queryIntent, /* no flags */ 0);
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        mSelectedIntent = intentForPosition(position);
        findViewById(R.id.set).setEnabled(true);
        
        WallpaperConnection conn = new WallpaperConnection(mSelectedIntent);
        if (conn.connect()) {
            if (mWallpaperConnection != null) {
                mWallpaperConnection.disconnect();
            }
            mWallpaperConnection = conn;
        }
    }
    
    public void onClick(View v) {
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
}
