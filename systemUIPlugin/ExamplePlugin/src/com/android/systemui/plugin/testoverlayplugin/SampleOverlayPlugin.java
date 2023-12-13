/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.plugin.testoverlayplugin;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.view.ViewTreeObserver.InternalInsetsInfo;
import android.view.ViewTreeObserver.OnComputeInternalInsetsListener;
import com.android.systemui.plugins.OverlayPlugin;
import com.android.systemui.plugins.annotations.Requires;

@Requires(target = OverlayPlugin.class, version = OverlayPlugin.VERSION)
public class SampleOverlayPlugin implements OverlayPlugin {
    private static final String TAG = "SampleOverlayPlugin";
    private Context mPluginContext;

    private View mStatusBarView;
    private View mNavBarView;
    private boolean mInputSetup;
    private boolean mCollapseDesired;
    private float mStatusBarHeight;

    @Override
    public void onCreate(Context sysuiContext, Context pluginContext) {
        Log.d(TAG, "onCreate");
        mPluginContext = pluginContext;
    }

    @Override
    public void onDestroy() {
        if (mInputSetup) {
            mStatusBarView.getViewTreeObserver().removeOnComputeInternalInsetsListener(
                    onComputeInternalInsetsListener);
        }
        Log.d(TAG, "onDestroy");
        if (mStatusBarView != null) {
            mStatusBarView.post(
                    () -> ((ViewGroup) mStatusBarView.getParent()).removeView(mStatusBarView));
        }
        if (mNavBarView != null) {
            mNavBarView.post(() -> ((ViewGroup) mNavBarView.getParent()).removeView(mNavBarView));
        }
    }

    @Override
    public void setup(View statusBar, View navBar) {
        Log.d(TAG, "Setup");

        int id = mPluginContext.getResources().getIdentifier("status_bar_height", "dimen",
                "android");
        mStatusBarHeight = mPluginContext.getResources().getDimension(id);
        if (statusBar instanceof ViewGroup) {
            mStatusBarView = LayoutInflater.from(mPluginContext)
                    .inflate(R.layout.colored_overlay, (ViewGroup) statusBar, false);
            ((ViewGroup) statusBar).addView(mStatusBarView);
        }
        if (navBar instanceof ViewGroup) {
            mNavBarView = LayoutInflater.from(mPluginContext)
                    .inflate(R.layout.colored_overlay, (ViewGroup) navBar, false);
            ((ViewGroup) navBar).addView(mNavBarView);
        }
    }

    @Override
    public void setCollapseDesired(boolean collapseDesired) {
        mCollapseDesired = collapseDesired;
    }

    @Override
    public boolean holdStatusBarOpen() {
        if (!mInputSetup) {
            mInputSetup = true;
            mStatusBarView.getViewTreeObserver().addOnComputeInternalInsetsListener(
                    onComputeInternalInsetsListener);
        }
        return true;
    }

    final OnComputeInternalInsetsListener onComputeInternalInsetsListener = inoutInfo -> {
        inoutInfo.setTouchableInsets(InternalInsetsInfo.TOUCHABLE_INSETS_REGION);
        if (mCollapseDesired) {
            inoutInfo.touchableRegion.set(new Rect(0, 0, 50000, (int) mStatusBarHeight));
        } else {
            inoutInfo.touchableRegion.set(new Rect(0, 0, 50000, 50000));
        }
    };
}
