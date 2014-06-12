/*
 * Copyright (C) 2014 The CyanogenMod Project
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

package org.cyanogenmod.trebuchet;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.animation.AccelerateInterpolator;
import android.widget.ImageView;

import com.android.launcher.home.Home;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;

import org.cyanogenmod.trebuchet.home.HomeUtils;
import org.cyanogenmod.trebuchet.home.HomeWrapper;

import java.lang.Override;

public class TrebuchetLauncher extends Launcher {

    private static final String TAG = "TrebuchetLauncher";

    private static final boolean DEBUG = false;
    private static final float MIN_PROGRESS = 0;
    private static final float MAX_PROGRESS = 1;


    private static class HomeAppStub {
        private final int mUid;
        private final ComponentName mComponentName;
        private final HomeWrapper mInstance;

        private HomeAppStub(int uid, ComponentName componentName, Context context)
                throws SecurityException, ReflectiveOperationException {
            super();
            mUid = uid;
            mComponentName = componentName;

            // Load a new instance of the Home app
            ClassLoader classloader = context.getClassLoader();
            Class<?> homeInterface = classloader.loadClass(Home.class.getName());
            Class<?> homeClazz = classloader.loadClass(mComponentName.getClassName());
            mInstance = new HomeWrapper(context, homeInterface, homeClazz.newInstance());
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((mComponentName == null) ? 0 : mComponentName.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            HomeAppStub other = (HomeAppStub) obj;
            if (mComponentName == null) {
                if (other.mComponentName != null)
                    return false;
            } else if (!mComponentName.equals(other.mComponentName))
                return false;
            return true;
        }
    }

    private BroadcastReceiver mPackageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Obtain the current instance or a new one if the current instance not exists
            boolean invalidate = false;
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_PACKAGE_CHANGED) ||
                    action.equals(Intent.ACTION_PACKAGE_REPLACED) ||
                    action.equals(Intent.ACTION_PACKAGE_RESTARTED)) {
                if (mCurrentHomeApp != null && intent.getIntExtra(Intent.EXTRA_UID, -1)
                        == mCurrentHomeApp.mUid) {
                    // The current Home app has changed or restarted. Invalidate the current
                    // one to be sure we will get all the new changes (if any)
                    if (DEBUG) Log.d(TAG, "Home package has changed. Invalidate layout.");
                    invalidate = true;
                }
            }
            obtainCurrentHomeAppStubLocked(invalidate);
        }
    };

    private CustomContentCallbacks mCustomContentCallbacks = new CustomContentCallbacks() {
        @Override
        public void onShow() {
            if (mCurrentHomeApp != null) {
                mCurrentHomeApp.mInstance.onShow();
            }
        }

        @Override
        public void onScrollProgressChanged(float progress) {
            updateQsbBarColorState(progress);
            if (mCurrentHomeApp != null) {
                mCurrentHomeApp.mInstance.onScrollProgressChanged(progress);
            }
        }

        @Override
        public void onHide() {
            if (mCurrentHomeApp != null) {
                mCurrentHomeApp.mInstance.onHide();
            }
        }
    };

    private HomeAppStub mCurrentHomeApp;
    private AccelerateInterpolator mQSBAlphaInterpolator;

    private QSBScroller mQsbScroller;
    private int mQsbInitialAlphaState;
    private int mQsbEndAlphaState;
    private int mQsbButtonsEndColorFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mQSBAlphaInterpolator = new AccelerateInterpolator();

        // Set QsbBar color state
        final Resources res = getResources();
        mQsbInitialAlphaState = res.getInteger(R.integer.qsb_initial_alpha_state);
        mQsbEndAlphaState = res.getInteger(R.integer.qsb_end_alpha_state);
        mQsbButtonsEndColorFilter = res.getInteger(R.integer.qsb_buttons_end_colorfilter);
        updateQsbBarColorState(MIN_PROGRESS);

        // Obtain the user-defined Home app or a valid one
        obtainCurrentHomeAppStubLocked(true);

        // Register this class to listen for new/deleted packages
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_RESTARTED);
        filter.addDataScheme("package");
        registerReceiver(mPackageReceiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Unregister services
        unregisterReceiver(mPackageReceiver);
    }

    @Override
    protected void onResume() {
        if (mCurrentHomeApp != null) {
            mCurrentHomeApp.mInstance.onResume();
        }
        super.onResume();
    }

    @Override
    protected void onPause() {
        if (mCurrentHomeApp != null) {
            mCurrentHomeApp.mInstance.onPause();
        }
        super.onPause();
    }

    @Override
    protected boolean hasCustomContentToLeft() {
        return mCurrentHomeApp != null && super.hasCustomContentToLeft();
    }

    @Override
    protected void invalidateHasCustomContentToLeft() {
        invalidateHomeStub();
        super.invalidateHasCustomContentToLeft();
    }

    @Override
    protected void populateCustomContentContainer() {
        if (mCurrentHomeApp != null) {
            mQsbScroller = addToCustomContentPage(mCurrentHomeApp.mInstance.createCustomView(),
                    mCustomContentCallbacks, mCurrentHomeApp.mInstance.getName());
            mQsbScroller.setScrollY(200);
        }
    }

    @Override
    protected boolean hasCustomSearchSupport() {
        return hasCustomContentToLeft() && mCurrentHomeApp.mInstance.isOperationSupported(
                Home.FLAG_OP_CUSTOM_SEARCH);
    }

    @Override
    protected void requestSearch(int mode) {
        if (!hasCustomSearchSupport()) {
            return;
        }
        mCurrentHomeApp.mInstance.onRequestSearch(mode);
    }

    private synchronized void obtainCurrentHomeAppStubLocked(boolean invalidate) {
        if (DEBUG) Log.d(TAG, "obtainCurrentHomeAppStubLocked called (" + invalidate + ")");

        SparseArray<ComponentName> packages = HomeUtils.getInstalledHomePackages(this);
        if (!invalidate && mCurrentHomeApp != null &&
                packages.get(mCurrentHomeApp.mUid) != null) {
            // We still have a valid Home app
            return;
        }

        // We don't have a valid Home app, so we need to destroy the current the custom content
        destroyHomeStub();

        // Return the default valid home app
        int size = packages.size();
        for (int i = 0; i < size; i++) {
            int key = packages.keyAt(i);
            ComponentName pkg = packages.get(key);
            String qualifiedPkg = pkg.toShortString();
            Context ctx = HomeUtils.createNewHomePackageContext(this, pkg);
            if (ctx == null) {
                // We failed to create a valid context. Will try with the next package
                continue;
            }
            try {
                mCurrentHomeApp = new HomeAppStub(key, pkg, ctx);
            } catch (ReflectiveOperationException ex) {
                if (!DEBUG) {
                    Log.w(TAG, "Cannot instantiate home package: " + qualifiedPkg + ". Ignored.");
                } else {
                    Log.w(TAG, "Cannot instantiate home package: " + qualifiedPkg +
                            ". Ignored.", ex);
                }
            } catch (SecurityException ex) {
                if (!DEBUG) {
                    Log.w(TAG, "Home package is insecure: " + qualifiedPkg + ". Ignored.");
                } else {
                    Log.w(TAG, "Home package is insecure: " + qualifiedPkg + ". Ignored.", ex);
                }
            }

            // Notify home app that is going to be used
            if (mCurrentHomeApp != null) {
                mCurrentHomeApp.mInstance.onStart();
            }
        }

        // Don't have a valid package. Anyway notify the launcher that custom content has changed
        invalidateHasCustomContentToLeft();
    }

    private void invalidateHomeStub() {
        if (mCurrentHomeApp != null) {
            mCurrentHomeApp.mInstance.onInvalidate();
            if (DEBUG) Log.d(TAG, "Home package " + mCurrentHomeApp.mComponentName.toShortString()
                    + " was invalidated.");
        }
    }

    private void destroyHomeStub() {
        if (mCurrentHomeApp != null) {
            mCurrentHomeApp.mInstance.onInvalidate();
            mCurrentHomeApp.mInstance.onDestroy();
            if (DEBUG) Log.d(TAG, "Home package " + mCurrentHomeApp.mComponentName.toShortString()
                    + " was destroyed.");
        }
        mQsbScroller = null;
        mCurrentHomeApp = null;
    }

    private void updateQsbBarColorState(float progress) {
        if (getQsbBar() != null) {
            float interpolation = mQSBAlphaInterpolator.getInterpolation(progress);

            // Background alpha
            int alphaInterpolation = (int)(mQsbInitialAlphaState +
                    (interpolation * (mQsbEndAlphaState - mQsbInitialAlphaState)));
            Drawable background = getQsbBar().getBackground();
            if (background != null) {
                background.setAlpha(alphaInterpolation);
            }

            // Buttons color filter
            int colorInterpolation = (int)(255 - (interpolation * mQsbButtonsEndColorFilter));
            int color = Color.rgb(colorInterpolation, colorInterpolation,colorInterpolation);
            ImageView voiceButton = getQsbBarVoiceButton();
            if (voiceButton != null) {
                if (progress > 0) {
                    voiceButton.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                }
            }
            ImageView searchButton = getQsbBarSearchButton();
            if (searchButton != null) {
                if (progress > 0) {
                    searchButton.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                }
            }
        }
    }
}
