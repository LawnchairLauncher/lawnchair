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
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.RelativeLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;


public class AllApps2D
        extends RelativeLayout
        implements AllAppsView,
                   AdapterView.OnItemClickListener,
                   AdapterView.OnItemLongClickListener,
                   View.OnKeyListener,
                   DragSource {

    private static final String TAG = "Launcher.AllApps2D";

    private Launcher mLauncher;
    private DragController mDragController;

    private GridView mGrid;

    private ArrayList<ApplicationInfo> mAllAppsList = new ArrayList<ApplicationInfo>();

    /**
     * True when we are using arrow keys or trackball to drive navigation
     */
    private boolean mArrowNavigation = false;
    private boolean mStartedScrolling;

    /**
     * Used to keep track of the selection when AllApps2D loses window focus.
     * One of the SELECTION_ constants.
     */
    private int mLastSelection;

    /**
     * Used to keep track of the selection when AllApps2D loses window focus
     */
    private int mLastSelectedIcon;

    private float mZoom;

    private AppsAdapter mAppsAdapter;

    // ------------------------------------------------------------

    public class AppsAdapter extends ArrayAdapter<ApplicationInfo> {
        private final LayoutInflater mInflater;

        public AppsAdapter(Context context, ArrayList<ApplicationInfo> apps) {
            super(context, 0, apps);
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final ApplicationInfo info = getItem(position);

            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.application_boxed, parent, false);
            }

//            if (!info.filtered) {
//                info.icon = Utilities.createIconThumbnail(info.icon, getContext());
//                info.filtered = true;
//            }

            final TextView textView = (TextView) convertView;
            textView.setCompoundDrawablesWithIntrinsicBounds(null, new BitmapDrawable(info.iconBitmap), null, null);
            textView.setText(info.title);

            return convertView;
        }
    }

    public AllApps2D(Context context, AttributeSet attrs) {
        super(context, attrs);
        setVisibility(View.GONE);
        setSoundEffectsEnabled(false);

        mAppsAdapter = new AppsAdapter(getContext(), mAllAppsList);
        mAppsAdapter.setNotifyOnChange(false);
    }

    @Override
    protected void onFinishInflate() {
        setBackgroundColor(0xFF000000);

        try {
            mGrid = (GridView)findViewWithTag("all_apps_2d_grid");
            if (mGrid == null) throw new Resources.NotFoundException();
            mGrid.setOnItemClickListener(this);
            mGrid.setOnItemLongClickListener(this);
            
            ImageButton homeButton = (ImageButton) findViewWithTag("all_apps_2d_home");
            if (homeButton == null) throw new Resources.NotFoundException();
            homeButton.setOnClickListener(
                new View.OnClickListener() {
                    public void onClick(View v) {
                        mLauncher.closeAllApps(true);
                    }
                });
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Can't find necessary layout elements for AllApps2D");
        }

        setOnKeyListener(this);
    }

    public AllApps2D(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs);
    }

    public void setLauncher(Launcher launcher) {
        mLauncher = launcher;
    }

    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (!isVisible()) return false;

        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                mLauncher.closeAllApps(true);
                break;
            default:
                return false;
        }

        return true;
    }

    public void onItemClick(AdapterView parent, View v, int position, long id) {
        ApplicationInfo app = (ApplicationInfo) parent.getItemAtPosition(position);
        mLauncher.startActivitySafely(app.intent);
    }

    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (!view.isInTouchMode()) {
            return false;
        }

        ApplicationInfo app = (ApplicationInfo) parent.getItemAtPosition(position);
        app = new ApplicationInfo(app);

        mDragController.startDrag(view, this, app, DragController.DRAG_ACTION_COPY);
        mLauncher.closeAllApps(true);

        return true;
    }


    public void setDragController(DragController dragger) {
        mDragController = dragger;
    }

    public void onDropCompleted(View target, boolean success) {
    }

    /**
     * Zoom to the specifed level.
     *
     * @param zoom [0..1] 0 is hidden, 1 is open
     */
    public void zoom(float zoom, boolean animate) {
//        Log.d(TAG, "zooming " + ((zoom == 1.0) ? "open" : "closed"));
        cancelLongPress();

        mZoom = zoom;

        if (isVisible()) {
            getParent().bringChildToFront(this);
            setVisibility(View.VISIBLE);
            mGrid.setAdapter(mAppsAdapter);
            if (animate) {
                startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.all_apps_2d_fade_in));
            } else {
                onAnimationEnd();
            }
        } else {
            if (animate) {
                startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.all_apps_2d_fade_out));
            } else {
                onAnimationEnd();
            }
        }
    }

    protected void onAnimationEnd() {
        if (!isVisible()) {
            setVisibility(View.GONE);
            mGrid.setAdapter(null);
            mZoom = 0.0f;
        } else {
            mZoom = 1.0f;
        }

        mLauncher.zoomed(mZoom);
    }

    public boolean isVisible() {
        return mZoom > 0.001f;
    }

    @Override
    public boolean isOpaque() {
        return mZoom > 0.999f;
    }

    public void setApps(ArrayList<ApplicationInfo> list) {
        mAllAppsList.clear();
        addApps(list);
    }

    public void addApps(ArrayList<ApplicationInfo> list) {
//        Log.d(TAG, "addApps: " + list.size() + " apps: " + list.toString());

        final int N = list.size();

        for (int i=0; i<N; i++) {
            final ApplicationInfo item = list.get(i);
            int index = Collections.binarySearch(mAllAppsList, item,
                    LauncherModel.APP_NAME_COMPARATOR);
            if (index < 0) {
                index = -(index+1);
            }
            mAllAppsList.add(index, item);
        }
        mAppsAdapter.notifyDataSetChanged();
    }

    public void removeApps(ArrayList<ApplicationInfo> list) {
        final int N = list.size();
        for (int i=0; i<N; i++) {
            final ApplicationInfo item = list.get(i);
            int index = findAppByComponent(mAllAppsList, item);
            if (index >= 0) {
                mAllAppsList.remove(index);
            } else {
                Log.w(TAG, "couldn't find a match for item \"" + item + "\"");
                // Try to recover.  This should keep us from crashing for now.
            }
        }
        mAppsAdapter.notifyDataSetChanged();
    }

    public void updateApps(String packageName, ArrayList<ApplicationInfo> list) {
        // Just remove and add, because they may need to be re-sorted.
        removeApps(list);
        addApps(list);
    }

    private static int findAppByComponent(ArrayList<ApplicationInfo> list, ApplicationInfo item) {
        ComponentName component = item.intent.getComponent();
        final int N = list.size();
        for (int i=0; i<N; i++) {
            ApplicationInfo x = list.get(i);
            if (x.intent.getComponent().equals(component)) {
                return i;
            }
        }
        return -1;
    }

    public void dumpState() {
        ApplicationInfo.dumpApplicationInfoList(TAG, "mAllAppsList", mAllAppsList);
    }
}


