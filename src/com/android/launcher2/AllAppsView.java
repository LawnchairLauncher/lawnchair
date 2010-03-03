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

import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.view.LayoutInflater;
import android.view.SoundEffectConstants;
import android.view.SurfaceHolder;
import android.view.VelocityTracker;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.widget.GridView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;


public interface AllAppsView {
    public interface Watcher {
        public void zoomed(float zoom);
    };

    public void setLauncher(Launcher launcher);

    public void setDragController(DragController dragger);

    public void zoom(float zoom, boolean animate);

    public boolean isVisible();

    public boolean isOpaque();

    public void setApps(ArrayList<ApplicationInfo> list);

    public void addApps(ArrayList<ApplicationInfo> list);

    public void removeApps(ArrayList<ApplicationInfo> list);

    public void updateApps(String packageName, ArrayList<ApplicationInfo> list);

    public void dumpState();
}


