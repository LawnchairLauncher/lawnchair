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
import android.os.SystemClock;
import android.renderscript.Allocation;
import android.renderscript.Dimension;
import android.renderscript.Element;
import android.renderscript.ProgramFragment;
import android.renderscript.ProgramStore;
import android.renderscript.ProgramVertex;
import android.renderscript.RSSurfaceView;
import android.renderscript.RenderScript;
import android.renderscript.Sampler;
import android.renderscript.Script;
import android.renderscript.ScriptC;
import android.renderscript.SimpleMesh;
import android.renderscript.Type;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.SurfaceHolder;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;


public class AllAppsView extends RSSurfaceView
        implements View.OnClickListener, View.OnLongClickListener, DragSource {
    private static final String TAG = "Launcher.AllAppsView";

    /** Bit for mLocks for when there are icons being loaded. */
    private static final int LOCK_ICONS_PENDING = 1;

    private static final int TRACKING_NONE = 0;
    private static final int TRACKING_FLING = 1;
    private static final int TRACKING_HOME = 2;

    private static final int SELECTED_NONE = 0;
    private static final int SELECTED_FOCUSED = 1;
    private static final int SELECTED_PRESSED = 2;

    private static final int SELECTION_NONE = 0;
    private static final int SELECTION_ICONS = 1;
    private static final int SELECTION_HOME = 2;

    private Launcher mLauncher;
    private DragController mDragController;

    /** When this is 0, modifications are allowed, when it's not, they're not.
     * TODO: What about scrolling? */
    private int mLocks = LOCK_ICONS_PENDING;

    private int mSlop;
    private int mMaxFlingVelocity;

    private Defines mDefines = new Defines();
    private RenderScript mRS;
    private RolloRS mRollo;
    private ArrayList<ApplicationInfo> mAllAppsList;

    /**
     * True when we are using arrow keys or trackball to drive navigation
     */
    private boolean mArrowNavigation = false;
    private boolean mStartedScrolling;

    /**
     * Used to keep track of the selection when AllAppsView loses window focus.
     * One of the SELECTION_ constants.
     */
    private int mLastSelection;

    /**
     * Used to keep track of the selection when AllAppsView loses window focus
     */
    private int mLastSelectedIcon;

    private VelocityTracker mVelocityTracker;
    private int mTouchTracking;
    private int mMotionDownRawX;
    private int mMotionDownRawY;
    private int mDownIconIndex = -1;
    private int mCurrentIconIndex = -1;

    private boolean mShouldGainFocus;

    private boolean mHaveSurface = false;
    private boolean mZoomDirty = false;
    private boolean mAnimateNextZoom;
    private float mNextZoom;
    private float mZoom;
    private float mPosX;
    private float mVelocity;
    private AAMessage mMessageProc;

    static class Defines {
        public static final int ALLOC_PARAMS = 0;
        public static final int ALLOC_STATE = 1;
        public static final int ALLOC_ICON_IDS = 3;
        public static final int ALLOC_LABEL_IDS = 4;
        public static final int ALLOC_VP_CONSTANTS = 5;

        public static final int COLUMNS_PER_PAGE = 4;
        public static final int ROWS_PER_PAGE = 4;

        public static final int ICON_WIDTH_PX = 64;
        public static final int ICON_TEXTURE_WIDTH_PX = 74;
        public static final int SELECTION_TEXTURE_WIDTH_PX = 74 + 20;

        public static final int ICON_HEIGHT_PX = 64;
        public static final int ICON_TEXTURE_HEIGHT_PX = 74;
        public static final int SELECTION_TEXTURE_HEIGHT_PX = 74 + 20;
    }

    public AllAppsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);
        setSoundEffectsEnabled(false);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        final ViewConfiguration config = ViewConfiguration.get(context);
        mSlop = config.getScaledTouchSlop();
        mMaxFlingVelocity = config.getScaledMaximumFlingVelocity();

        setOnClickListener(this);
        setOnLongClickListener(this);
        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);

        mRS = createRenderScript(true);
    }

    /**
     * Note that this implementation prohibits this view from ever being reattached.
     */
    @Override
    protected void onDetachedFromWindow() {
        destroyRenderScript();
        mRS.mMessageCallback = null;
        mRS = null;
    }

    /**
     * If you have an attached click listener, View always plays the click sound!?!?
     * Deal with sound effects by hand.
     */
    public void reallyPlaySoundEffect(int sound) {
        boolean old = isSoundEffectsEnabled();
        setSoundEffectsEnabled(true);
        playSoundEffect(sound);
        setSoundEffectsEnabled(old);
    }

    public AllAppsView(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs);
    }

    public void setLauncher(Launcher launcher) {
        mLauncher = launcher;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        super.surfaceDestroyed(holder);
        // Without this, we leak mMessageCallback which leaks the context.
        mRS.mMessageCallback = null;
        // We may lose any callbacks that are pending, so make sure that we re-sync that
        // on the next surfaceChanged.
        mZoomDirty = true;
        mHaveSurface = false;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        //long startTime = SystemClock.uptimeMillis();

        super.surfaceChanged(holder, format, w, h);

        mHaveSurface = true;

        if (mRollo == null) {
            mRollo = new RolloRS();
            mRollo.init(getResources(), w, h);
            if (mAllAppsList != null) {
                mRollo.setApps(mAllAppsList);
            }
            if (mShouldGainFocus) {
                gainFocus();
                mShouldGainFocus = false;
            }
        }
        mRollo.dirtyCheck();
        mRollo.resize(w, h);

        if (mRS != null) {
            mRS.mMessageCallback = mMessageProc = new AAMessage();
        }

        Resources res = getContext().getResources();
        int barHeight = (int)res.getDimension(R.dimen.button_bar_height);


        if (mRollo.mUniformAlloc != null) {
            float tf[] = new float[] {72.f, 72.f,
                                      120.f, 120.f, 0.f, 0.f,
                                      120.f, 680.f,
                                      (2.f / 480.f), 0, -((float)w / 2) - 0.25f, -380.25f};
            if (w > h) {
                tf[6] = 40.f;
                tf[7] = h - 40.f;
                tf[9] = 1.f;
                tf[10] = -((float)w / 2) - 0.25f;
                tf[11] = -((float)h / 2) - 0.25f;
            }

            mRollo.mUniformAlloc.data(tf);
        }

        //long endTime = SystemClock.uptimeMillis();
        //Log.d(TAG, "surfaceChanged took " + (endTime-startTime) + "ms");
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (mArrowNavigation) {
            if (!hasWindowFocus) {
                // Clear selection when we lose window focus
                mLastSelectedIcon = mRollo.mState.selectedIconIndex;
                mRollo.setHomeSelected(SELECTED_NONE);
                mRollo.clearSelectedIcon();
                mRollo.mState.save();
            } else if (hasWindowFocus) {
                if (mRollo.mState.iconCount > 0) {
                    if (mLastSelection == SELECTION_ICONS) {
                        int selection = mLastSelectedIcon;
                        final int firstIcon = Math.round(mPosX) *
                            Defines.COLUMNS_PER_PAGE;
                        if (selection < 0 || // No selection
                                selection < firstIcon || // off the top of the screen
                                selection >= mRollo.mState.iconCount || // past last icon
                                selection >= firstIcon + // past last icon on screen
                                    (Defines.COLUMNS_PER_PAGE * Defines.ROWS_PER_PAGE)) {
                            selection = firstIcon;
                        }

                        // Select the first icon when we gain window focus
                        mRollo.selectIcon(selection, SELECTED_FOCUSED);
                        mRollo.mState.save();
                    } else if (mLastSelection == SELECTION_HOME) {
                        mRollo.setHomeSelected(SELECTED_FOCUSED);
                        mRollo.mState.save();
                    }
                }
            }
        }
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);

        if (!isVisible()) {
            return;
        }

        if (gainFocus) {
            if (mRollo != null) {
                gainFocus();
            } else {
                mShouldGainFocus = true;
            }
        } else {
            if (mRollo != null) {
                if (mArrowNavigation) {
                    // Clear selection when we lose focus
                    mRollo.clearSelectedIcon();
                    mRollo.setHomeSelected(SELECTED_NONE);
                    mRollo.mState.save();
                    mArrowNavigation = false;
                }
            } else {
                mShouldGainFocus = false;
            }
        }
    }

    private void gainFocus() {
        if (!mArrowNavigation && mRollo.mState.iconCount > 0) {
            // Select the first icon when we gain keyboard focus
            mArrowNavigation = true;
            mRollo.selectIcon(Math.round(mPosX) * Defines.COLUMNS_PER_PAGE,
                    SELECTED_FOCUSED);
            mRollo.mState.save();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        boolean handled = false;

        if (!isVisible()) {
            return false;
        }
        final int iconCount = mRollo.mState.iconCount;

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (mArrowNavigation) {
                if (mLastSelection == SELECTION_HOME) {
                    reallyPlaySoundEffect(SoundEffectConstants.CLICK);
                    mLauncher.closeAllApps(true);
                } else {
                    int whichApp = mRollo.mState.selectedIconIndex;
                    if (whichApp >= 0) {
                        ApplicationInfo app = mAllAppsList.get(whichApp);
                        mLauncher.startActivitySafely(app.intent);
                        handled = true;
                    }
                }
            }
        }

        if (iconCount > 0) {
            mArrowNavigation = true;

            int currentSelection = mRollo.mState.selectedIconIndex;
            int currentTopRow = Math.round(mPosX);

            // The column of the current selection, in the range 0..COLUMNS_PER_PAGE-1
            final int currentPageCol = currentSelection % Defines.COLUMNS_PER_PAGE;

            // The row of the current selection, in the range 0..ROWS_PER_PAGE-1
            final int currentPageRow = (currentSelection - (currentTopRow*Defines.COLUMNS_PER_PAGE))
                    / Defines.ROWS_PER_PAGE;

            int newSelection = currentSelection;

            switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (mLastSelection == SELECTION_HOME) {
                    mRollo.setHomeSelected(SELECTED_NONE);
                    int lastRowCount = iconCount % Defines.COLUMNS_PER_PAGE;
                    if (lastRowCount == 0) {
                        lastRowCount = Defines.COLUMNS_PER_PAGE;
                    }
                    newSelection = iconCount - lastRowCount + (Defines.COLUMNS_PER_PAGE / 2);
                    if (newSelection >= iconCount) {
                        newSelection = iconCount-1;
                    }
                    int target = (newSelection / Defines.COLUMNS_PER_PAGE)
                            - (Defines.ROWS_PER_PAGE - 1);
                    if (target < 0) {
                        target = 0;
                    }
                    if (currentTopRow != target) {
                        mRollo.moveTo(target);
                    }
                } else {
                    if (currentPageRow > 0) {
                        newSelection = currentSelection - Defines.COLUMNS_PER_PAGE;
                    } else if (currentTopRow > 0) {
                        newSelection = currentSelection - Defines.COLUMNS_PER_PAGE;
                        mRollo.moveTo(newSelection / Defines.COLUMNS_PER_PAGE);
                    } else if (currentPageRow != 0) {
                        newSelection = currentTopRow * Defines.ROWS_PER_PAGE;
                    }
                }
                handled = true;
                break;

            case KeyEvent.KEYCODE_DPAD_DOWN: {
                final int rowCount = iconCount / Defines.COLUMNS_PER_PAGE
                        + (iconCount % Defines.COLUMNS_PER_PAGE == 0 ? 0 : 1);
                final int currentRow = currentSelection / Defines.COLUMNS_PER_PAGE;
                if (mLastSelection != SELECTION_HOME) {
                    if (currentRow < rowCount-1) {
                        mRollo.setHomeSelected(SELECTED_NONE);
                        if (currentSelection < 0) {
                            newSelection = 0;
                        } else {
                            newSelection = currentSelection + Defines.COLUMNS_PER_PAGE;
                        }
                        if (newSelection >= iconCount) {
                            // Go from D to G in this arrangement:
                            //     A B C D
                            //     E F G
                            newSelection = iconCount - 1;
                        }
                        if (currentPageRow >= Defines.ROWS_PER_PAGE - 1) {
                            mRollo.moveTo((newSelection / Defines.COLUMNS_PER_PAGE) -
                                    Defines.ROWS_PER_PAGE + 1);
                        }
                    } else {
                        newSelection = -1;
                        mRollo.setHomeSelected(SELECTED_FOCUSED);
                    }
                }
                handled = true;
                break;
            }
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (mLastSelection != SELECTION_HOME) {
                    if (currentPageCol > 0) {
                        newSelection = currentSelection - 1;
                    }
                }
                handled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (mLastSelection != SELECTION_HOME) {
                    if ((currentPageCol < Defines.COLUMNS_PER_PAGE - 1) &&
                            (currentSelection < iconCount - 1)) {
                        newSelection = currentSelection + 1;
                    }
                }
                handled = true;
                break;
            }
            if (newSelection != currentSelection) {
                mRollo.selectIcon(newSelection, SELECTED_FOCUSED);
                mRollo.mState.save();
            }
        }
        return handled;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev)
    {
        mArrowNavigation = false;

        if (!isVisible()) {
            return true;
        }

        if (mLocks != 0) {
            return true;
        }

        super.onTouchEvent(ev);

        int x = (int)ev.getX();
        int y = (int)ev.getY();

        int action = ev.getAction();
        switch (action) {
        case MotionEvent.ACTION_DOWN:
            if (y > mRollo.mTouchYBorders[mRollo.mTouchYBorders.length-1]) {
                mTouchTracking = TRACKING_HOME;
                mRollo.setHomeSelected(SELECTED_PRESSED);
                mRollo.mState.save();
                mCurrentIconIndex = -1;
            } else {
                mTouchTracking = TRACKING_FLING;

                mMotionDownRawX = (int)ev.getRawX();
                mMotionDownRawY = (int)ev.getRawY();

                mRollo.mState.newPositionX = ev.getRawY() / getHeight();
                mRollo.mState.newTouchDown = 1;

                if (!mRollo.checkClickOK()) {
                    mRollo.clearSelectedIcon();
                } else {
                    mDownIconIndex = mCurrentIconIndex
                            = mRollo.selectIcon(x, y, mPosX, SELECTED_PRESSED);
                    if (mDownIconIndex < 0) {
                        // if nothing was selected, no long press.
                        cancelLongPress();
                    }
                }
                mRollo.mState.save();
                mRollo.move();
                mVelocityTracker = VelocityTracker.obtain();
                mVelocityTracker.addMovement(ev);
                mStartedScrolling = false;
            }
            break;
        case MotionEvent.ACTION_MOVE:
        case MotionEvent.ACTION_OUTSIDE:
            if (mTouchTracking == TRACKING_HOME) {
                mRollo.setHomeSelected(y > mRollo.mTouchYBorders[mRollo.mTouchYBorders.length-1]
                        ? SELECTED_PRESSED : SELECTED_NONE);
                mRollo.mState.save();
            } else if (mTouchTracking == TRACKING_FLING) {
                int rawX = (int)ev.getRawX();
                int rawY = (int)ev.getRawY();
                int slop;
                slop = Math.abs(rawY - mMotionDownRawY);

                if (!mStartedScrolling && slop < mSlop) {
                    // don't update anything so when we do start scrolling
                    // below, we get the right delta.
                    mCurrentIconIndex = mRollo.chooseTappedIcon(x, y, mPosX);
                    if (mDownIconIndex != mCurrentIconIndex) {
                        // If a different icon is selected, don't allow it to be picked up.
                        // This handles off-axis dragging.
                        cancelLongPress();
                        mCurrentIconIndex = -1;
                    }
                } else {
                    if (!mStartedScrolling) {
                        cancelLongPress();
                        mCurrentIconIndex = -1;
                    }
                    mRollo.mState.newPositionX = ev.getRawY() / getHeight();
                    mRollo.mState.newTouchDown = 1;
                    mRollo.move();

                    mStartedScrolling = true;
                    mRollo.clearSelectedIcon();
                    mVelocityTracker.addMovement(ev);
                    mRollo.mState.save();
                }
            }
            break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
            if (mTouchTracking == TRACKING_HOME) {
                if (action == MotionEvent.ACTION_UP) {
                    if (y > mRollo.mTouchYBorders[mRollo.mTouchYBorders.length-1]) {
                        reallyPlaySoundEffect(SoundEffectConstants.CLICK);
                        mLauncher.closeAllApps(true);
                    }
                    mRollo.setHomeSelected(SELECTED_NONE);
                    mRollo.mState.save();
                }
                mCurrentIconIndex = -1;
            } else if (mTouchTracking == TRACKING_FLING) {
                mRollo.mState.newTouchDown = 0;
                mRollo.mState.newPositionX = ev.getRawY() / getHeight();

                mVelocityTracker.computeCurrentVelocity(1000 /* px/sec */, mMaxFlingVelocity);
                mRollo.mState.flingVelocity = mVelocityTracker.getYVelocity() / getHeight();
                mRollo.clearSelectedIcon();
                mRollo.mState.save();
                mRollo.fling();

                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
            }
            mTouchTracking = TRACKING_NONE;
            break;
        }

        return true;
    }

    public void onClick(View v) {
        if (mLocks != 0 || !isVisible()) {
            return;
        }
        if (mRollo.checkClickOK() && mCurrentIconIndex == mDownIconIndex
                && mCurrentIconIndex >= 0 && mCurrentIconIndex < mAllAppsList.size()) {
            reallyPlaySoundEffect(SoundEffectConstants.CLICK);
            ApplicationInfo app = mAllAppsList.get(mCurrentIconIndex);
            mLauncher.startActivitySafely(app.intent);
        }
    }

    public boolean onLongClick(View v) {
        if (mLocks != 0 || !isVisible()) {
            return true;
        }
        if (mRollo.checkClickOK() && mCurrentIconIndex == mDownIconIndex
                && mCurrentIconIndex >= 0 && mCurrentIconIndex < mAllAppsList.size()) {
            ApplicationInfo app = mAllAppsList.get(mCurrentIconIndex);

            Bitmap bmp = app.iconBitmap;
            final int w = bmp.getWidth();
            final int h = bmp.getHeight();

            // We don't really have an accurate location to use.  This will do.
            int screenX = mMotionDownRawX - (w / 2);
            int screenY = mMotionDownRawY - h;

            int left = (mDefines.ICON_TEXTURE_WIDTH_PX - mDefines.ICON_WIDTH_PX) / 2;
            int top = (mDefines.ICON_TEXTURE_HEIGHT_PX - mDefines.ICON_HEIGHT_PX) / 2;
            mDragController.startDrag(bmp, screenX, screenY,
                    0, 0, w, h, this, app, DragController.DRAG_ACTION_COPY);

            mLauncher.closeAllApps(true);
        }
        return true;
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SELECTED) {
            if (!isVisible()) {
                return false;
            }
            String text = null;
            int index;
            int count = mAllAppsList.size() + 1; // +1 is home
            int pos = -1;
            switch (mLastSelection) {
            case SELECTION_ICONS:
                index = mRollo.mState.selectedIconIndex;
                if (index >= 0) {
                    ApplicationInfo info = mAllAppsList.get(index);
                    if (info.title != null) {
                        text = info.title.toString();
                        pos = index;
                    }
                }
                break;
            case SELECTION_HOME:
                text = getContext().getString(R.string.all_apps_home_button_label);
                pos = count;
                break;
            }
            if (text != null) {
                event.setEnabled(true);
                event.getText().add(text);
                //event.setContentDescription(text);
                event.setItemCount(count);
                event.setCurrentItemIndex(pos);
            }
        }
        return false;
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
        cancelLongPress();
        mNextZoom = zoom;
        mAnimateNextZoom = animate;
        // if we do setZoom while we don't have a surface, we won't
        // get the callbacks that actually set mZoom.
        if (mRollo == null || !mHaveSurface) {
            mZoomDirty = true;
            mZoom = zoom;
            return;
        } else {
            mRollo.setZoom(zoom, animate);
        }
    }

    public boolean isVisible() {
        return mZoom > 0.001f;
    }

    public boolean isOpaque() {
        return mZoom > 0.999f;
    }

    public void setApps(ArrayList<ApplicationInfo> list) {
        if (mRS == null) {
            // We've been removed from the window.  Don't bother with all this.
            return;
        }

        mAllAppsList = list;
        if (mRollo != null) {
            mRollo.setApps(list);
        }
        mLocks &= ~LOCK_ICONS_PENDING;
    }

    public void addApps(ArrayList<ApplicationInfo> list) {
        if (mAllAppsList == null) {
            // Not done loading yet.  We'll find out about it later.
            return;
        }
        if (mRS == null) {
            // We've been removed from the window.  Don't bother with all this.
            return;
        }

        final int N = list.size();
        if (mRollo != null) {
            mRollo.reallocAppsList(mRollo.mState.iconCount + N);
        }

        for (int i=0; i<N; i++) {
            final ApplicationInfo item = list.get(i);
            int index = Collections.binarySearch(mAllAppsList, item,
                    LauncherModel.APP_NAME_COMPARATOR);
            if (index < 0) {
                index = -(index+1);
            }
            mAllAppsList.add(index, item);
            if (mRollo != null) {
                mRollo.addApp(index, item);
            }
        }

        if (mRollo != null) {
            mRollo.saveAppsList();
        }
    }

    public void removeApps(ArrayList<ApplicationInfo> list) {
        if (mAllAppsList == null) {
            // Not done loading yet.  We'll find out about it later.
            return;
        }

        final int N = list.size();
        for (int i=0; i<N; i++) {
            final ApplicationInfo item = list.get(i);
            int index = findAppByComponent(mAllAppsList, item);
            if (index >= 0) {
                int ic = mRollo != null ? mRollo.mState.iconCount : 666;
                mAllAppsList.remove(index);
                if (mRollo != null) {
                    mRollo.removeApp(index);
                }
            } else {
                Log.w(TAG, "couldn't find a match for item \"" + item + "\"");
                // Try to recover.  This should keep us from crashing for now.
            }
        }

        if (mRollo != null) {
            mRollo.saveAppsList();
        }
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

    private static int countPages(int iconCount) {
        int iconsPerPage = Defines.COLUMNS_PER_PAGE * Defines.ROWS_PER_PAGE;
        int pages = iconCount / iconsPerPage;
        if (pages*iconsPerPage != iconCount) {
            pages++;
        }
        return pages;
    }

    class AAMessage extends RenderScript.RSMessage {
        public void run() {
            mPosX = ((float)mData[0]) / (1 << 16);
            mVelocity = ((float)mData[1]) / (1 << 16);
            mZoom = ((float)mData[2]) / (1 << 16);
            mZoomDirty = false;
        }
    }

    public class RolloRS {

        // Allocations ======
        private int mWidth;
        private int mHeight;

        private Resources mRes;
        private Script mScript;
        private Script.Invokable mInvokeMove;
        private Script.Invokable mInvokeMoveTo;
        private Script.Invokable mInvokeFling;
        private Script.Invokable mInvokeResetWAR;
        private Script.Invokable mInvokeSetZoom;

        private ProgramStore mPSIcons;
        private ProgramFragment mPFTexMip;
        private ProgramFragment mPFTexMipAlpha;
        private ProgramFragment mPFTexNearest;
        private ProgramVertex mPV;
        private ProgramVertex mPVCurve;
        private SimpleMesh mMesh;
        private ProgramVertex.MatrixAllocation mPVA;

        private Allocation mUniformAlloc;

        private Allocation mHomeButtonNormal;
        private Allocation mHomeButtonFocused;
        private Allocation mHomeButtonPressed;

        private Allocation[] mIcons;
        private int[] mIconIds;
        private Allocation mAllocIconIds;

        private Allocation[] mLabels;
        private int[] mLabelIds;
        private Allocation mAllocLabelIds;
        private Allocation mSelectedIcon;

        private int[] mTouchYBorders;
        private int[] mTouchXBorders;

        private Bitmap mSelectionBitmap;
        private Canvas mSelectionCanvas;

        Params mParams;
        State mState;

        class BaseAlloc {
            Allocation mAlloc;
            Type mType;

            void save() {
                mAlloc.data(this);
            }
        }

        private boolean checkClickOK() {
            return (Math.abs(mVelocity) < 0.4f) &&
                   (Math.abs(mPosX - Math.round(mPosX)) < 0.4f);
        }

        class Params extends BaseAlloc {
            Params() {
                mType = Type.createFromClass(mRS, Params.class, 1, "ParamsClass");
                mAlloc = Allocation.createTyped(mRS, mType);
                save();
            }
            public int bubbleWidth;
            public int bubbleHeight;
            public int bubbleBitmapWidth;
            public int bubbleBitmapHeight;

            public int homeButtonWidth;
            public int homeButtonHeight;
            public int homeButtonTextureWidth;
            public int homeButtonTextureHeight;
        }

        class State extends BaseAlloc {
            public float newPositionX;
            public int newTouchDown;
            public float flingVelocity;
            public int iconCount;
            public int selectedIconIndex = -1;
            public int selectedIconTexture;
            public float zoomTarget;
            public int homeButtonId;
            public float targetPos;

            State() {
                mType = Type.createFromClass(mRS, State.class, 1, "StateClass");
                mAlloc = Allocation.createTyped(mRS, mType);
                save();
            }
        }

        public RolloRS() {
        }

        public void init(Resources res, int width, int height) {
            mRes = res;
            mWidth = width;
            mHeight = height;
            initProgramVertex();
            initProgramFragment();
            initProgramStore();
            initGl();
            initData();
            initTouchState();
            initRs();
        }

        public void initMesh() {
            SimpleMesh.TriangleMeshBuilder tm = new SimpleMesh.TriangleMeshBuilder(mRS, 2, 0);

            for (int ct=0; ct < 16; ct++) {
                float pos = (1.f / (16.f - 1)) * ct;
                tm.addVertex(0.0f, pos);
                tm.addVertex(1.0f, pos);
            }
            for (int ct=0; ct < (16 * 2 - 2); ct+= 2) {
                tm.addTriangle(ct, ct+1, ct+2);
                tm.addTriangle(ct+1, ct+3, ct+2);
            }
            mMesh = tm.create();
            mMesh.setName("SMCell");
        }

        void resize(int w, int h) {
            mPVA.setupProjectionNormalized(w, h);
            mWidth = w;
            mHeight = h;
        }

        private void initProgramVertex() {
            mPVA = new ProgramVertex.MatrixAllocation(mRS);
            resize(mWidth, mHeight);

            ProgramVertex.Builder pvb = new ProgramVertex.Builder(mRS, null, null);
            pvb.setTextureMatrixEnable(true);
            mPV = pvb.create();
            mPV.setName("PV");
            mPV.bindAllocation(mPVA);

            Element.Builder eb = new Element.Builder(mRS);
            eb.add(Element.createVector(mRS, Element.DataType.FLOAT_32, 2), "ImgSize");
            eb.add(Element.createVector(mRS, Element.DataType.FLOAT_32, 4), "Position");
            eb.add(Element.createVector(mRS, Element.DataType.FLOAT_32, 2), "BendPos");
            eb.add(Element.createVector(mRS, Element.DataType.FLOAT_32, 4), "ScaleOffset");
            Element e = eb.create();

            mUniformAlloc = Allocation.createSized(mRS, e, 1);

            initMesh();
            ProgramVertex.ShaderBuilder sb = new ProgramVertex.ShaderBuilder(mRS);
            String t = new String("void main() {\n" +
                                  // Animation
                                  "  float ani = UNI_Position.z;\n" +

                                  "  float bendY1 = UNI_BendPos.x;\n" +
                                  "  float bendY2 = UNI_BendPos.y;\n" +
                                  "  float bendAngle = 47.0 * (3.14 / 180.0);\n" +
                                  "  float bendDistance = bendY1 * 0.4;\n" +
                                  "  float distanceDimLevel = 0.6;\n" +

                                  "  float bendStep = (bendAngle / bendDistance) * (bendAngle * 0.5);\n" +
                                  "  float aDy = cos(bendAngle);\n" +
                                  "  float aDz = sin(bendAngle);\n" +

                                  "  float scale = (2.0 / 480.0);\n" +
                                  "  float x = UNI_Position.x + UNI_ImgSize.x * (1.0 - ani) * (ATTRIB_position.x - 0.5);\n" +
                                  "  float ys= UNI_Position.y + UNI_ImgSize.y * (1.0 - ani) * ATTRIB_position.y;\n" +
                                  "  float y = 0.0;\n" +
                                  "  float z = 0.0;\n" +
                                  "  float lum = 1.0;\n" +

                                  "  float cv = min(ys, bendY1 - bendDistance) - (bendY1 - bendDistance);\n" +
                                  "  y += cv * aDy;\n" +
                                  "  z += -cv * aDz;\n" +
                                  "  cv = clamp(ys, bendY1 - bendDistance, bendY1) - bendY1;\n" +  // curve range
                                  "  lum += cv / bendDistance * distanceDimLevel;\n" +
                                  "  y += cv * cos(cv * bendStep);\n" +
                                  "  z += cv * sin(cv * bendStep);\n" +

                                  "  cv = max(ys, bendY2 + bendDistance) - (bendY2 + bendDistance);\n" +
                                  "  y += cv * aDy;\n" +
                                  "  z += cv * aDz;\n" +
                                  "  cv = clamp(ys, bendY2, bendY2 + bendDistance) - bendY2;\n" +
                                  "  lum -= cv / bendDistance * distanceDimLevel;\n" +
                                  "  y += cv * cos(cv * bendStep);\n" +
                                  "  z += cv * sin(cv * bendStep);\n" +

                                  "  y += clamp(ys, bendY1, bendY2);\n" +

                                  "  vec4 pos;\n" +
                                  "  pos.x = (x + UNI_ScaleOffset.z) * UNI_ScaleOffset.x;\n" +
                                  "  pos.y = (y + UNI_ScaleOffset.w) * UNI_ScaleOffset.x;\n" +
                                  "  pos.z = z * UNI_ScaleOffset.x;\n" +
                                  "  pos.w = 1.0;\n" +

                                  "  pos.x *= 1.0 + ani * 4.0;\n" +
                                  "  pos.y *= 1.0 + ani * 4.0;\n" +
                                  "  pos.z -= ani * 1.5;\n" +
                                  "  lum *= 1.0 - ani;\n" +

                                  "  gl_Position = UNI_MVP * pos;\n" +
                                  "  varColor.rgba = vec4(lum, lum, lum, 1.0);\n" +
                                  "  varTex0.xy = ATTRIB_position;\n" +
                                  "  varTex0.y = 1.0 - varTex0.y;\n" +
                                  "  varTex0.zw = vec2(0.0, 0.0);\n" +
                                  "}\n");
            sb.setShader(t);
            sb.addConstant(mUniformAlloc.getType());
            sb.addInput(mMesh.getVertexType(0).getElement());
            mPVCurve = sb.create();
            mPVCurve.setName("PVCurve");
            mPVCurve.bindAllocation(mPVA);
            mPVCurve.bindConstants(mUniformAlloc, 1);

            mRS.contextBindProgramVertex(mPV);
        }

        private void initProgramFragment() {
            Sampler.Builder sb = new Sampler.Builder(mRS);
            sb.setMin(Sampler.Value.LINEAR_MIP_LINEAR);
            sb.setMag(Sampler.Value.NEAREST);
            sb.setWrapS(Sampler.Value.CLAMP);
            sb.setWrapT(Sampler.Value.CLAMP);
            Sampler linear = sb.create();

            sb.setMin(Sampler.Value.NEAREST);
            sb.setMag(Sampler.Value.NEAREST);
            Sampler nearest = sb.create();

            ProgramFragment.Builder bf = new ProgramFragment.Builder(mRS);
            bf.setTexture(ProgramFragment.Builder.EnvMode.MODULATE,
                          ProgramFragment.Builder.Format.RGBA, 0);
            mPFTexMip = bf.create();
            mPFTexMip.setName("PFTexMip");
            mPFTexMip.bindSampler(linear, 0);

            mPFTexNearest = bf.create();
            mPFTexNearest.setName("PFTexNearest");
            mPFTexNearest.bindSampler(nearest, 0);

            bf.setTexture(ProgramFragment.Builder.EnvMode.MODULATE,
                          ProgramFragment.Builder.Format.ALPHA, 0);
            mPFTexMipAlpha = bf.create();
            mPFTexMipAlpha.setName("PFTexMipAlpha");
            mPFTexMipAlpha.bindSampler(linear, 0);

        }

        private void initProgramStore() {
            ProgramStore.Builder bs = new ProgramStore.Builder(mRS, null, null);
            bs.setDepthFunc(ProgramStore.DepthFunc.ALWAYS);
            bs.setColorMask(true,true,true,false);
            bs.setDitherEnable(true);
            bs.setBlendFunc(ProgramStore.BlendSrcFunc.SRC_ALPHA,
                            ProgramStore.BlendDstFunc.ONE_MINUS_SRC_ALPHA);
            mPSIcons = bs.create();
            mPSIcons.setName("PSIcons");
        }

        private void initGl() {
            mTouchXBorders = new int[Defines.COLUMNS_PER_PAGE+1];
            mTouchYBorders = new int[Defines.ROWS_PER_PAGE+1];
        }

        private void initData() {
            mParams = new Params();
            mState = new State();

            final Utilities.BubbleText bubble = new Utilities.BubbleText(getContext());

            mParams.bubbleWidth = bubble.getBubbleWidth();
            mParams.bubbleHeight = bubble.getMaxBubbleHeight();
            mParams.bubbleBitmapWidth = bubble.getBitmapWidth();
            mParams.bubbleBitmapHeight = bubble.getBitmapHeight();

            mHomeButtonNormal = Allocation.createFromBitmapResource(mRS, mRes,
                    R.drawable.home_button_normal, Element.RGBA_8888(mRS), false);
            mHomeButtonNormal.uploadToTexture(0);
            mHomeButtonFocused = Allocation.createFromBitmapResource(mRS, mRes,
                    R.drawable.home_button_focused, Element.RGBA_8888(mRS), false);
            mHomeButtonFocused.uploadToTexture(0);
            mHomeButtonPressed = Allocation.createFromBitmapResource(mRS, mRes,
                    R.drawable.home_button_pressed, Element.RGBA_8888(mRS), false);
            mHomeButtonPressed.uploadToTexture(0);
            mParams.homeButtonWidth = 76;
            mParams.homeButtonHeight = 68;
            mParams.homeButtonTextureWidth = 128;
            mParams.homeButtonTextureHeight = 128;

            mState.homeButtonId = mHomeButtonNormal.getID();

            mParams.save();
            mState.save();

            mSelectionBitmap = Bitmap.createBitmap(Defines.SELECTION_TEXTURE_WIDTH_PX,
                    Defines.SELECTION_TEXTURE_HEIGHT_PX, Bitmap.Config.ARGB_8888);
            mSelectionCanvas = new Canvas(mSelectionBitmap);

            setApps(null);
        }

        private void initScript(int id) {
        }

        private void initRs() {
            ScriptC.Builder sb = new ScriptC.Builder(mRS);
            sb.setScript(mRes, R.raw.allapps);
            sb.setRoot(true);
            sb.addDefines(mDefines);
            sb.setType(mParams.mType, "params", Defines.ALLOC_PARAMS);
            sb.setType(mState.mType, "state", Defines.ALLOC_STATE);
            sb.setType(mUniformAlloc.getType(), "vpConstants", Defines.ALLOC_VP_CONSTANTS);
            mInvokeMove = sb.addInvokable("move");
            mInvokeFling = sb.addInvokable("fling");
            mInvokeMoveTo = sb.addInvokable("moveTo");
            mInvokeResetWAR = sb.addInvokable("resetHWWar");
            mInvokeSetZoom = sb.addInvokable("setZoom");
            mScript = sb.create();
            mScript.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            mScript.bindAllocation(mParams.mAlloc, Defines.ALLOC_PARAMS);
            mScript.bindAllocation(mState.mAlloc, Defines.ALLOC_STATE);
            mScript.bindAllocation(mAllocIconIds, Defines.ALLOC_ICON_IDS);
            mScript.bindAllocation(mAllocLabelIds, Defines.ALLOC_LABEL_IDS);
            mScript.bindAllocation(mUniformAlloc, Defines.ALLOC_VP_CONSTANTS);

            mRS.contextBindRootScript(mScript);
        }

        void dirtyCheck() {
            if (mZoomDirty) {
                setZoom(mNextZoom, mAnimateNextZoom);
            }
        }

        private void setApps(ArrayList<ApplicationInfo> list) {
            final int count = list != null ? list.size() : 0;
            int allocCount = count;
            if (allocCount < 1) {
                allocCount = 1;
            }

            mIcons = new Allocation[count];
            mIconIds = new int[allocCount];
            mAllocIconIds = Allocation.createSized(mRS, Element.USER_I32(mRS), allocCount);

            mLabels = new Allocation[count];
            mLabelIds = new int[allocCount];
            mAllocLabelIds = Allocation.createSized(mRS, Element.USER_I32(mRS), allocCount);

            Element ie8888 = Element.RGBA_8888(mRS);

            mState.iconCount = count;
            for (int i=0; i < mState.iconCount; i++) {
                createAppIconAllocations(i, list.get(i));
            }
            for (int i=0; i < mState.iconCount; i++) {
                uploadAppIcon(i, list.get(i));
            }
            saveAppsList();
        }

        private void setZoom(float zoom, boolean animate) {
            mRollo.clearSelectedIcon();
            mRollo.setHomeSelected(SELECTED_NONE);
            if (zoom > 0.001f) {
                mRollo.mState.zoomTarget = zoom;
            } else {
                mRollo.mState.zoomTarget = 0;
            }
            mRollo.mState.save();
            if (!animate) {
                mRollo.mInvokeSetZoom.execute();
            }
        }

        private void createAppIconAllocations(int index, ApplicationInfo item) {
            mIcons[index] = Allocation.createFromBitmap(mRS, item.iconBitmap,
                    Element.RGBA_8888(mRS), true);
            mLabels[index] = Allocation.createFromBitmap(mRS, item.titleBitmap,
                    Element.A_8(mRS), true);
            mIconIds[index] = mIcons[index].getID();
            mLabelIds[index] = mLabels[index].getID();
        }

        private void uploadAppIcon(int index, ApplicationInfo item) {
            if (mIconIds[index] != mIcons[index].getID()) {
                throw new IllegalStateException("uploadAppIcon index=" + index
                    + " mIcons[index].getID=" + mIcons[index].getID()
                    + " mIconsIds[index]=" + mIconIds[index]
                    + " item=" + item);
            }
            mIcons[index].uploadToTexture(0);
            mLabels[index].uploadToTexture(0);
        }

        /**
         * Puts the empty spaces at the end.  Updates mState.iconCount.  You must
         * fill in the values and call saveAppsList().
         */
        private void reallocAppsList(int count) {
            Allocation[] icons = new Allocation[count];
            int[] iconIds = new int[count];
            mAllocIconIds = Allocation.createSized(mRS, Element.USER_I32(mRS), count);

            Allocation[] labels = new Allocation[count];
            int[] labelIds = new int[count];
            mAllocLabelIds = Allocation.createSized(mRS, Element.USER_I32(mRS), count);

            final int oldCount = mRollo.mState.iconCount;

            System.arraycopy(mIcons, 0, icons, 0, oldCount);
            System.arraycopy(mIconIds, 0, iconIds, 0, oldCount);
            System.arraycopy(mLabels, 0, labels, 0, oldCount);
            System.arraycopy(mLabelIds, 0, labelIds, 0, oldCount);

            mIcons = icons;
            mIconIds = iconIds;
            mLabels = labels;
            mLabelIds = labelIds;
        }

        /**
         * Handle the allocations for the new app.  Make sure you call saveAppsList when done.
         */
        private void addApp(int index, ApplicationInfo item) {
            final int count = mState.iconCount - index;
            final int dest = index + 1;

            System.arraycopy(mIcons, index, mIcons, dest, count);
            System.arraycopy(mIconIds, index, mIconIds, dest, count);
            System.arraycopy(mLabels, index, mLabels, dest, count);
            System.arraycopy(mLabelIds, index, mLabelIds, dest, count);

            createAppIconAllocations(index, item);
            uploadAppIcon(index, item);
            mRollo.mState.iconCount++;
        }

        /**
         * Handle the allocations for the removed app.  Make sure you call saveAppsList when done.
         */
        private void removeApp(int index) {
            final int count = mState.iconCount - index - 1;
            final int src = index + 1;

            System.arraycopy(mIcons, src, mIcons, index, count);
            System.arraycopy(mIconIds, src, mIconIds, index, count);
            System.arraycopy(mLabels, src, mLabels, index, count);
            System.arraycopy(mLabelIds, src, mLabelIds, index, count);

            mRollo.mState.iconCount--;
            final int last = mState.iconCount;

            mIcons[last] = null;
            mIconIds[last] = 0;
            mLabels[last] = null;
            mLabelIds[last] = 0;
        }

        /**
         * Send the apps list structures to RS.
         */
        private void saveAppsList() {
            // WTF: how could mScript be not null but mAllocIconIds null b/2460740.
            if (mScript != null && mAllocIconIds != null) {
                mRS.contextBindRootScript(null);

                mAllocIconIds.data(mIconIds);
                mAllocLabelIds.data(mLabelIds);

                mScript.bindAllocation(mAllocIconIds, Defines.ALLOC_ICON_IDS);
                mScript.bindAllocation(mAllocLabelIds, Defines.ALLOC_LABEL_IDS);

                mState.save();

                // Note: mScript may be null if we haven't initialized it yet.
                // In that case, this is a no-op.
                if (mInvokeResetWAR != null) {
                    mInvokeResetWAR.execute();
                }

                mRS.contextBindRootScript(mScript);
            }
        }

        void initTouchState() {
            int width = getWidth();
            int height = getHeight();
            int cellHeight = 145;//iconsSize / Defines.ROWS_PER_PAGE;
            int cellWidth = width / Defines.COLUMNS_PER_PAGE;

            int centerY = (height / 2);
            mTouchYBorders[0] = centerY - (cellHeight * 2);
            mTouchYBorders[1] = centerY - cellHeight;
            mTouchYBorders[2] = centerY;
            mTouchYBorders[3] = centerY + cellHeight;
            mTouchYBorders[4] = centerY + (cellHeight * 2);

            int centerX = (width / 2);
            mTouchXBorders[0] = 0;
            mTouchXBorders[1] = centerX - (width / 4);
            mTouchXBorders[2] = centerX;
            mTouchXBorders[3] = centerX + (width / 4);
            mTouchXBorders[4] = width;
        }

        void fling() {
            mInvokeFling.execute();
        }

        void move() {
            mInvokeMove.execute();
        }

        void moveTo(float row) {
            mState.targetPos = row;
            mState.save();
            mInvokeMoveTo.execute();
        }

        int chooseTappedIcon(int x, int y, float pos) {
            // Adjust for scroll position if not zero.
            y += (pos - ((int)pos)) * (mTouchYBorders[1] - mTouchYBorders[0]);

            int col = -1;
            int row = -1;
            for (int i=0; i<Defines.COLUMNS_PER_PAGE; i++) {
                if (x >= mTouchXBorders[i] && x < mTouchXBorders[i+1]) {
                    col = i;
                    break;
                }
            }
            for (int i=0; i<Defines.ROWS_PER_PAGE; i++) {
                if (y >= mTouchYBorders[i] && y < mTouchYBorders[i+1]) {
                    row = i;
                    break;
                }
            }

            if (row < 0 || col < 0) {
                return -1;
            }

            int index = (((int)pos) * Defines.COLUMNS_PER_PAGE)
                    + (row * Defines.ROWS_PER_PAGE) + col;

            if (index >= mState.iconCount) {
                return -1;
            } else {
                return index;
            }
        }

        /**
         * You need to call save() on mState on your own after calling this.
         *
         * @return the index of the icon that was selected.
         */
        int selectIcon(int x, int y, float pos, int pressed) {
            final int index = chooseTappedIcon(x, y, pos);
            selectIcon(index, pressed);
            return index;
        }

        /**
         * Select the icon at the given index.
         *
         * @param index The index.
         * @param pressed one of SELECTED_PRESSED or SELECTED_FOCUSED
         */
        void selectIcon(int index, int pressed) {
            if (mAllAppsList == null || index < 0 || index >= mAllAppsList.size()) {
                mState.selectedIconIndex = -1;
                if (mLastSelection == SELECTION_ICONS) {
                    mLastSelection = SELECTION_NONE;
                }
            } else {
                if (pressed == SELECTED_FOCUSED) {
                    mLastSelection = SELECTION_ICONS;
                }

                int prev = mState.selectedIconIndex;
                mState.selectedIconIndex = index;

                ApplicationInfo info = mAllAppsList.get(index);
                Bitmap selectionBitmap = mSelectionBitmap;

                Utilities.drawSelectedAllAppsBitmap(mSelectionCanvas,
                        selectionBitmap.getWidth(), selectionBitmap.getHeight(),
                        pressed == SELECTED_PRESSED, info.iconBitmap);

                mSelectedIcon = Allocation.createFromBitmap(mRS, selectionBitmap,
                        Element.RGBA_8888(mRS), false);
                mSelectedIcon.uploadToTexture(0);
                mState.selectedIconTexture = mSelectedIcon.getID();

                if (prev != index) {
                    if (info.title != null && info.title.length() > 0) {
                        //setContentDescription(info.title);
                        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
                    }
                }
            }
        }

        /**
         * You need to call save() on mState on your own after calling this.
         */
        void clearSelectedIcon() {
            mState.selectedIconIndex = -1;
        }

        void setHomeSelected(int mode) {
            final int prev = mLastSelection;
            switch (mode) {
            case SELECTED_NONE:
                mState.homeButtonId = mHomeButtonNormal.getID();
                break;
            case SELECTED_FOCUSED:
                mLastSelection = SELECTION_HOME;
                mState.homeButtonId = mHomeButtonFocused.getID();
                if (prev != SELECTION_HOME) {
                    sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
                }
                break;
            case SELECTED_PRESSED:
                mState.homeButtonId = mHomeButtonPressed.getID();
                break;
            }
        }

        public void dumpState() {
            Log.d(TAG, "mRollo.mWidth=" + mWidth);
            Log.d(TAG, "mRollo.mHeight=" + mHeight);
            Log.d(TAG, "mRollo.mIcons=" + mIcons);
            if (mIcons != null) {
                Log.d(TAG, "mRollo.mIcons.length=" + mIcons.length);
            }
            if (mIconIds != null) {
                Log.d(TAG, "mRollo.mIconIds.length=" + mIconIds.length);
            }
            Log.d(TAG, "mRollo.mIconIds=" +  Arrays.toString(mIconIds));
            if (mLabelIds != null) {
                Log.d(TAG, "mRollo.mLabelIds.length=" + mLabelIds.length);
            }
            Log.d(TAG, "mRollo.mLabelIds=" +  Arrays.toString(mLabelIds));
            Log.d(TAG, "mRollo.mTouchXBorders=" +  Arrays.toString(mTouchXBorders));
            Log.d(TAG, "mRollo.mTouchYBorders=" +  Arrays.toString(mTouchYBorders));
            Log.d(TAG, "mRollo.mState.newPositionX=" + mState.newPositionX);
            Log.d(TAG, "mRollo.mState.newTouchDown=" + mState.newTouchDown);
            Log.d(TAG, "mRollo.mState.flingVelocity=" + mState.flingVelocity);
            Log.d(TAG, "mRollo.mState.iconCount=" + mState.iconCount);
            Log.d(TAG, "mRollo.mState.selectedIconIndex=" + mState.selectedIconIndex);
            Log.d(TAG, "mRollo.mState.selectedIconTexture=" + mState.selectedIconTexture);
            Log.d(TAG, "mRollo.mState.zoomTarget=" + mState.zoomTarget);
            Log.d(TAG, "mRollo.mState.homeButtonId=" + mState.homeButtonId);
            Log.d(TAG, "mRollo.mState.targetPos=" + mState.targetPos);
            Log.d(TAG, "mRollo.mParams.bubbleWidth=" + mParams.bubbleWidth);
            Log.d(TAG, "mRollo.mParams.bubbleHeight=" + mParams.bubbleHeight);
            Log.d(TAG, "mRollo.mParams.bubbleBitmapWidth=" + mParams.bubbleBitmapWidth);
            Log.d(TAG, "mRollo.mParams.bubbleBitmapHeight=" + mParams.bubbleBitmapHeight);
            Log.d(TAG, "mRollo.mParams.homeButtonWidth=" + mParams.homeButtonWidth);
            Log.d(TAG, "mRollo.mParams.homeButtonHeight=" + mParams.homeButtonHeight);
            Log.d(TAG, "mRollo.mParams.homeButtonTextureWidth=" + mParams.homeButtonTextureWidth);
            Log.d(TAG, "mRollo.mParams.homeButtonTextureHeight=" + mParams.homeButtonTextureHeight);
        }
    }

    public void dumpState() {
        Log.d(TAG, "mRS=" + mRS);
        Log.d(TAG, "mRollo=" + mRollo);
        ApplicationInfo.dumpApplicationInfoList(TAG, "mAllAppsList", mAllAppsList);
        Log.d(TAG, "mArrowNavigation=" + mArrowNavigation);
        Log.d(TAG, "mStartedScrolling=" + mStartedScrolling);
        Log.d(TAG, "mLastSelection=" + mLastSelection);
        Log.d(TAG, "mLastSelectedIcon=" + mLastSelectedIcon);
        Log.d(TAG, "mVelocityTracker=" + mVelocityTracker);
        Log.d(TAG, "mTouchTracking=" + mTouchTracking);
        Log.d(TAG, "mShouldGainFocus=" + mShouldGainFocus);
        Log.d(TAG, "mZoomDirty=" + mZoomDirty);
        Log.d(TAG, "mAnimateNextZoom=" + mAnimateNextZoom);
        Log.d(TAG, "mZoom=" + mZoom);
        Log.d(TAG, "mPosX=" + mPosX);
        Log.d(TAG, "mVelocity=" + mVelocity);
        Log.d(TAG, "mMessageProc=" + mMessageProc);
        if (mRollo != null) {
            mRollo.dumpState();
        }
        if (mRS != null) {
            mRS.contextDump(0);
        }
    }
}


