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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import com.android.launcher.R;

import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.renderscript.*;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.SurfaceHolder;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;

public class AllApps3D extends RSSurfaceView
        implements AllAppsView, View.OnClickListener, View.OnLongClickListener, DragSource {
    private static final String TAG = "Launcher.AllApps3D";

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
    private ArrayList<ApplicationInfo> mAllAppsList;

    private static RenderScriptGL sRS;
    private static RolloRS sRollo;

    private static boolean sZoomDirty = false;
    private static boolean sAnimateNextZoom;
    private static float sNextZoom;

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
    private int[] mTouchYBorders;
    private int[] mTouchXBorders;

    private boolean mShouldGainFocus;

    private boolean mHaveSurface = false;
    private float mZoom;
    private float mVelocity;
    private AAMessage mMessageProc;

    private int mColumnsPerPage;
    private int mRowsPerPage;
    private boolean mSurrendered;

    private int mRestoreFocusIndex = -1;

    @SuppressWarnings({"UnusedDeclaration"})
    static class Defines {
        public static final int COLUMNS_PER_PAGE_PORTRAIT = 4;
        public static final int ROWS_PER_PAGE_PORTRAIT = 4;

        public static final int COLUMNS_PER_PAGE_LANDSCAPE = 6;
        public static final int ROWS_PER_PAGE_LANDSCAPE = 3;

        public static final int SELECTION_TEXTURE_WIDTH_PX = 74 + 20;
        public static final int SELECTION_TEXTURE_HEIGHT_PX = 74 + 20;
    }

    public AllApps3D(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);
        setSoundEffectsEnabled(false);
        final ViewConfiguration config = ViewConfiguration.get(context);
        mSlop = config.getScaledTouchSlop();
        mMaxFlingVelocity = config.getScaledMaximumFlingVelocity();

        setOnClickListener(this);
        setOnLongClickListener(this);
        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);

        if (sRS == null) {
            RenderScriptGL.SurfaceConfig sc = new RenderScriptGL.SurfaceConfig();
            sc.setDepth(16, 16);
            sc.setAlpha(8, 8);
            sRS = createRenderScriptGL(sc);
        } else {
            // Is this even possible?
            setRenderScriptGL(sRS);
        }

        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        final boolean isPortrait = metrics.widthPixels < metrics.heightPixels;
        mColumnsPerPage = isPortrait ? Defines.COLUMNS_PER_PAGE_PORTRAIT :
                Defines.COLUMNS_PER_PAGE_LANDSCAPE;
        mRowsPerPage = isPortrait ? Defines.ROWS_PER_PAGE_PORTRAIT :
                Defines.ROWS_PER_PAGE_LANDSCAPE;

        if (sRollo != null) {
            sRollo.mAllApps = this;
            sRollo.mRes = getResources();
            sRollo.mInitialize = true;
        }
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public AllApps3D(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs);
    }

    public void surrender() {
        if (sRS != null) {
            sRS.setSurface(null, 0, 0);
            sRS.setMessageHandler(null);
        }
        mSurrendered = true;
    }

    /**
     * Note that this implementation prohibits this view from ever being reattached.
     */
    @Override
    protected void onDetachedFromWindow() {
        sRS.setMessageHandler(null);
        if (!mSurrendered) {
            Log.i(TAG, "onDetachedFromWindow");
            destroyRenderScriptGL();
            sRS = null;
            sRollo = null;
            super.onDetachedFromWindow();
        }
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

    public void setLauncher(Launcher launcher) {
        mLauncher = launcher;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        super.surfaceDestroyed(holder);
        // Without this, we leak mMessageCallback which leaks the context.
        if (!mSurrendered) {
            sRS.setMessageHandler(null);
        }
        // We may lose any callbacks that are pending, so make sure that we re-sync that
        // on the next surfaceChanged.
        sZoomDirty = true;
        mHaveSurface = false;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        //long startTime = SystemClock.uptimeMillis();

        super.surfaceChanged(holder, format, w, h);

        if (mSurrendered) return;

        mHaveSurface = true;

        if (sRollo == null) {
            sRollo = new RolloRS(this);
            sRollo.init(getResources(), w, h);
            if (mAllAppsList != null) {
                sRollo.setApps(mAllAppsList);
            }
            if (mShouldGainFocus) {
                gainFocus();
                mShouldGainFocus = false;
            }
        } else if (sRollo.mInitialize) {
            sRollo.initGl();
            sRollo.mInitialize = false;
        }

        initTouchState(w, h);

        sRollo.dirtyCheck();
        sRollo.resize(w, h);

        Log.d(TAG, "sc " + sRS);
        if (sRS != null) {
            mMessageProc = new AAMessage();
            sRS.setMessageHandler(mMessageProc);
        }

        //long endTime = SystemClock.uptimeMillis();
        //Log.d(TAG, "surfaceChanged took " + (endTime-startTime) + "ms");
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);

        if (mSurrendered) return;

        if (mArrowNavigation) {
            if (!hasWindowFocus) {
                // Clear selection when we lose window focus
                mLastSelectedIcon = sRollo.mScript.get_gSelectedIconIndex();
                sRollo.setHomeSelected(SELECTED_NONE);
                sRollo.clearSelectedIcon();
            } else {
                if (sRollo.mScript.get_gIconCount() > 0) {
                    if (mLastSelection == SELECTION_ICONS) {
                        int selection = mLastSelectedIcon;
                        final int firstIcon = Math.round(sRollo.mScrollPos) * mColumnsPerPage;
                        if (selection < 0 || // No selection
                                selection < firstIcon || // off the top of the screen
                                selection >= sRollo.mScript.get_gIconCount() || // past last icon
                                selection >= firstIcon + // past last icon on screen
                                    (mColumnsPerPage * mRowsPerPage)) {
                            selection = firstIcon;
                        }

                        // Select the first icon when we gain window focus
                        sRollo.selectIcon(selection, SELECTED_FOCUSED);
                    } else if (mLastSelection == SELECTION_HOME) {
                        sRollo.setHomeSelected(SELECTED_FOCUSED);
                    }
                }
            }
        }
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);

        if (!isVisible() || mSurrendered) {
            return;
        }

        if (gainFocus) {
            if (sRollo != null) {
                gainFocus();
            } else {
                mShouldGainFocus = true;
            }
        } else {
            if (sRollo != null) {
                if (mArrowNavigation) {
                    // Clear selection when we lose focus
                    sRollo.clearSelectedIcon();
                    sRollo.setHomeSelected(SELECTED_NONE);
                    mArrowNavigation = false;
                }
            } else {
                mShouldGainFocus = false;
            }
        }
    }

    private void gainFocus() {
        if (!mArrowNavigation && sRollo.mScript.get_gIconCount() > 0) {
            // Select the first icon when we gain keyboard focus
            mArrowNavigation = true;
            sRollo.selectIcon(Math.round(sRollo.mScrollPos) * mColumnsPerPage, SELECTED_FOCUSED);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        boolean handled = false;

        if (!isVisible()) {
            return false;
        }
        final int iconCount = sRollo.mScript.get_gIconCount();

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (mArrowNavigation) {
                if (mLastSelection == SELECTION_HOME) {
                    reallyPlaySoundEffect(SoundEffectConstants.CLICK);
                    mLauncher.showWorkspace(true);
                } else {
                    int whichApp = sRollo.mScript.get_gSelectedIconIndex();
                    if (whichApp >= 0) {
                        ApplicationInfo app = mAllAppsList.get(whichApp);
                        mLauncher.startActivitySafely(app.intent, app);
                        handled = true;
                    }
                }
            }
        }

        if (iconCount > 0) {
            final boolean isPortrait = getWidth() < getHeight();

            mArrowNavigation = true;

            int currentSelection = sRollo.mScript.get_gSelectedIconIndex();
            int currentTopRow = Math.round(sRollo.mScrollPos);

            // The column of the current selection, in the range 0..COLUMNS_PER_PAGE_PORTRAIT-1
            final int currentPageCol = currentSelection % mColumnsPerPage;

            // The row of the current selection, in the range 0..ROWS_PER_PAGE_PORTRAIT-1
            final int currentPageRow = (currentSelection - (currentTopRow * mColumnsPerPage))
                    / mRowsPerPage;

            int newSelection = currentSelection;

            switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (mLastSelection == SELECTION_HOME) {
                    if (isPortrait) {
                        sRollo.setHomeSelected(SELECTED_NONE);
                        int lastRowCount = iconCount % mColumnsPerPage;
                        if (lastRowCount == 0) {
                            lastRowCount = mColumnsPerPage;
                        }
                        newSelection = iconCount - lastRowCount + (mColumnsPerPage / 2);
                        if (newSelection >= iconCount) {
                            newSelection = iconCount-1;
                        }
                        int target = (newSelection / mColumnsPerPage) - (mRowsPerPage - 1);
                        if (target < 0) {
                            target = 0;
                        }
                        if (currentTopRow != target) {
                            sRollo.moveTo(target);
                        }
                    }
                } else {
                    if (currentPageRow > 0) {
                        newSelection = currentSelection - mColumnsPerPage;
                        if (currentTopRow > newSelection / mColumnsPerPage) {
                            sRollo.moveTo(newSelection / mColumnsPerPage);
                        }
                    } else if (currentTopRow > 0) {
                        newSelection = currentSelection - mColumnsPerPage;
                        sRollo.moveTo(newSelection / mColumnsPerPage);
                    } else if (currentPageRow != 0) {
                        newSelection = currentTopRow * mRowsPerPage;
                    }
                }
                handled = true;
                break;

            case KeyEvent.KEYCODE_DPAD_DOWN: {
                final int rowCount = iconCount / mColumnsPerPage
                        + (iconCount % mColumnsPerPage == 0 ? 0 : 1);
                final int currentRow = currentSelection / mColumnsPerPage;
                if (mLastSelection != SELECTION_HOME) {
                    if (currentRow < rowCount-1) {
                        sRollo.setHomeSelected(SELECTED_NONE);
                        if (currentSelection < 0) {
                            newSelection = 0;
                        } else {
                            newSelection = currentSelection + mColumnsPerPage;
                        }
                        if (newSelection >= iconCount) {
                            // Go from D to G in this arrangement:
                            //     A B C D
                            //     E F G
                            newSelection = iconCount - 1;
                        }
                        if (currentPageRow >= mRowsPerPage - 1) {
                            sRollo.moveTo((newSelection / mColumnsPerPage) - mRowsPerPage + 1);
                        }
                    } else if (isPortrait) {
                        newSelection = -1;
                        sRollo.setHomeSelected(SELECTED_FOCUSED);
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
                } else if (!isPortrait) {
                    newSelection = ((int) (sRollo.mScrollPos) * mColumnsPerPage) +
                            (mRowsPerPage / 2 * mColumnsPerPage) + mColumnsPerPage - 1;
                    sRollo.setHomeSelected(SELECTED_NONE);
                }
                handled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (mLastSelection != SELECTION_HOME) {
                    if (!isPortrait && (currentPageCol == mColumnsPerPage - 1 ||
                            currentSelection == iconCount - 1)) {
                        newSelection = -1;
                        sRollo.setHomeSelected(SELECTED_FOCUSED);
                    } else if ((currentPageCol < mColumnsPerPage - 1) &&
                            (currentSelection < iconCount - 1)) {
                        newSelection = currentSelection + 1;
                    }
                }
                handled = true;
                break;
            }
            if (newSelection != currentSelection) {
                sRollo.selectIcon(newSelection, SELECTED_FOCUSED);
            }
        }
        return handled;
    }

    void initTouchState(int width, int height) {
        boolean isPortrait = width < height;

        int[] viewPos = new int[2];
        getLocationOnScreen(viewPos);

        mTouchXBorders = new int[mColumnsPerPage + 1];
        mTouchYBorders = new int[mRowsPerPage + 1];

        // TODO: Put this in a config file/define
        int cellHeight = 145;//iconsSize / Defines.ROWS_PER_PAGE_PORTRAIT;
        if (!isPortrait) cellHeight -= 12;
        int centerY = (int) (height * (isPortrait ? 0.5f : 0.47f));
        if (!isPortrait) centerY += cellHeight / 2;
        int half = (int) Math.floor((mRowsPerPage + 1) / 2);
        int end = mTouchYBorders.length - (half + 1);

        for (int i = -half; i <= end; i++) {
            mTouchYBorders[i + half] = centerY + (i * cellHeight) - viewPos[1];
        }

        int x = 0;
        // TODO: Put this in a config file/define
        int columnWidth = 120;
        for (int i = 0; i < mColumnsPerPage + 1; i++) {
            mTouchXBorders[i] = x - viewPos[0];
            x += columnWidth;
        }
    }

    int chooseTappedIcon(int x, int y) {
        float pos = sRollo != null ? sRollo.mScrollPos : 0;

        int oldY = y;

        // Adjust for scroll position if not zero.
        y += (pos - ((int)pos)) * (mTouchYBorders[1] - mTouchYBorders[0]);

        int col = -1;
        int row = -1;
        final int columnsCount = mColumnsPerPage;
        for (int i=0; i< columnsCount; i++) {
            if (x >= mTouchXBorders[i] && x < mTouchXBorders[i+1]) {
                col = i;
                break;
            }
        }
        final int rowsCount = mRowsPerPage;
        for (int i=0; i< rowsCount; i++) {
            if (y >= mTouchYBorders[i] && y < mTouchYBorders[i+1]) {
                row = i;
                break;
            }
        }

        if (row < 0 || col < 0) {
            return -1;
        }

        int index = (((int) pos) * columnsCount) + (row * columnsCount) + col;

        if (index >= mAllAppsList.size()) {
            return -1;
        } else {
            return index;
        }
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

        final boolean isPortrait = getWidth() < getHeight();
        int action = ev.getAction();
        switch (action) {
        case MotionEvent.ACTION_DOWN:
            if ((isPortrait && y > mTouchYBorders[mTouchYBorders.length-1]) ||
                    (!isPortrait && x > mTouchXBorders[mTouchXBorders.length-1])) {
                mTouchTracking = TRACKING_HOME;
                sRollo.setHomeSelected(SELECTED_PRESSED);
                mCurrentIconIndex = -1;
            } else {
                mTouchTracking = TRACKING_FLING;

                mMotionDownRawX = (int)ev.getRawX();
                mMotionDownRawY = (int)ev.getRawY();

                if (!sRollo.checkClickOK()) {
                    sRollo.clearSelectedIcon();
                } else {
                    mDownIconIndex = mCurrentIconIndex
                            = sRollo.selectIcon(x, y, SELECTED_PRESSED);
                    if (mDownIconIndex < 0) {
                        // if nothing was selected, no long press.
                        cancelLongPress();
                    }
                }
                sRollo.move(ev.getRawY() / getHeight());
                mVelocityTracker = VelocityTracker.obtain();
                mVelocityTracker.addMovement(ev);
                mStartedScrolling = false;
            }
            break;
        case MotionEvent.ACTION_MOVE:
        case MotionEvent.ACTION_OUTSIDE:
            if (mTouchTracking == TRACKING_HOME) {
                sRollo.setHomeSelected((isPortrait &&
                        y > mTouchYBorders[mTouchYBorders.length-1]) || (!isPortrait
                        && x > mTouchXBorders[mTouchXBorders.length-1])
                        ? SELECTED_PRESSED : SELECTED_NONE);
            } else if (mTouchTracking == TRACKING_FLING) {
                int rawY = (int)ev.getRawY();
                int slop;
                slop = Math.abs(rawY - mMotionDownRawY);

                if (!mStartedScrolling && slop < mSlop) {
                    // don't update anything so when we do start scrolling
                    // below, we get the right delta.
                    mCurrentIconIndex = chooseTappedIcon(x, y);
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
                    sRollo.move(ev.getRawY() / getHeight());

                    mStartedScrolling = true;
                    sRollo.clearSelectedIcon();
                    mVelocityTracker.addMovement(ev);
                }
            }
            break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
            if (mTouchTracking == TRACKING_HOME) {
                if (action == MotionEvent.ACTION_UP) {
                    if ((isPortrait && y > mTouchYBorders[mTouchYBorders.length-1]) ||
                        (!isPortrait && x > mTouchXBorders[mTouchXBorders.length-1])) {
                        reallyPlaySoundEffect(SoundEffectConstants.CLICK);
                        mLauncher.showWorkspace(true);
                    }
                    sRollo.setHomeSelected(SELECTED_NONE);
                }
                mCurrentIconIndex = -1;
            } else if (mTouchTracking == TRACKING_FLING) {
                mVelocityTracker.computeCurrentVelocity(1000 /* px/sec */, mMaxFlingVelocity);
                sRollo.clearSelectedIcon();
                sRollo.fling(ev.getRawY() / getHeight(),
                             mVelocityTracker.getYVelocity() / getHeight());

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
        if (sRollo.checkClickOK() && mCurrentIconIndex == mDownIconIndex
                && mCurrentIconIndex >= 0 && mCurrentIconIndex < mAllAppsList.size()) {
            reallyPlaySoundEffect(SoundEffectConstants.CLICK);
            ApplicationInfo app = mAllAppsList.get(mCurrentIconIndex);
            mLauncher.startActivitySafely(app.intent, app);
        }
    }

    public boolean onLongClick(View v) {
        // We don't accept long click events in these cases
        // - If the workspace isn't ready to accept a drop
        // - If we're not done loading (because we might be confused about which item
        //   to pick up
        // - If we're not visible
        if (!isVisible() || mLauncher.isWorkspaceLocked() || mLocks != 0) {
            return true;
        }
        if (sRollo.checkClickOK() && mCurrentIconIndex == mDownIconIndex
                && mCurrentIconIndex >= 0 && mCurrentIconIndex < mAllAppsList.size()) {
            ApplicationInfo app = mAllAppsList.get(mCurrentIconIndex);

            final Bitmap bmp = app.iconBitmap;

            // We don't really have an accurate location to use.  This will do.
            int screenX = mMotionDownRawX - (bmp.getWidth() / 2);
            int screenY = mMotionDownRawY - bmp.getHeight();

            mLauncher.lockScreenOrientation();
            mLauncher.getWorkspace().onDragStartedWithItemSpans(1, 1, bmp);
            mDragController.startDrag(
                    bmp, screenX, screenY, this, app, DragController.DRAG_ACTION_COPY);

            mLauncher.showWorkspace(true);
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
                index = sRollo.mScript.get_gSelectedIconIndex();
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

    @Override
    public void setDragController(DragController dragger) {
        mDragController = dragger;
    }

    @Override
    public void onDragViewVisible() {
    }

    @Override
    public void onDropCompleted(View target, Object dragInfo, boolean success) {
        mLauncher.getWorkspace().onDragStopped(success);
        mLauncher.unlockScreenOrientation();
    }

    /**
     * Zoom to the specifed level.
     *
     * @param zoom [0..1] 0 is hidden, 1 is open
     */
    public void zoom(float zoom, boolean animate) {
        cancelLongPress();
        sNextZoom = zoom;
        sAnimateNextZoom = animate;
        // if we do setZoom while we don't have a surface, we won't
        // get the callbacks that actually set mZoom.
        if (sRollo == null || !mHaveSurface) {
            sZoomDirty = true;
            mZoom = zoom;
        } else {
            sRollo.setZoom(zoom, animate);
        }
    }

    /**
     * If sRollo is null, then we're not visible.  This is also used to guard against
     * sRollo being null.
     */
    public boolean isVisible() {
        return sRollo != null && mZoom > 0.001f;
    }

    public boolean isAnimating() {
        return isVisible() && mZoom <= 0.999f;
    }

    public void setApps(ArrayList<ApplicationInfo> list) {
        if (sRS == null) {
            // We've been removed from the window.  Don't bother with all this.
            return;
        }

        if (list != null) {
            Collections.sort(list, LauncherModel.APP_NAME_COMPARATOR);
        }

        boolean reload = false;
        if (mAllAppsList == null) {
            reload = true;
        } else if (list.size() != mAllAppsList.size()) {
            reload = true;
        } else {
            final int count = list.size();
            for (int i = 0; i < count; i++) {
                if (list.get(i) != mAllAppsList.get(i)) {
                    reload = true;
                    break;
                }
            }
        }

        mAllAppsList = list;
        if (sRollo != null && reload) {
            sRollo.setApps(list);
        }

        if (hasFocus() && mRestoreFocusIndex != -1) {
            sRollo.selectIcon(mRestoreFocusIndex, SELECTED_FOCUSED);
            mRestoreFocusIndex = -1;
        }

        mLocks &= ~LOCK_ICONS_PENDING;
    }

    public void addApps(ArrayList<ApplicationInfo> list) {
        if (mAllAppsList == null) {
            // Not done loading yet.  We'll find out about it later.
            return;
        }
        if (sRS == null) {
            // We've been removed from the window.  Don't bother with all this.
            return;
        }

        final int N = list.size();
        if (sRollo != null) {
            sRollo.pause();
            sRollo.reallocAppsList(sRollo.mScript.get_gIconCount() + N);
        }

        for (int i=0; i<N; i++) {
            final ApplicationInfo item = list.get(i);
            int index = Collections.binarySearch(mAllAppsList, item,
                    LauncherModel.APP_NAME_COMPARATOR);
            if (index < 0) {
                index = -(index+1);
            }
            mAllAppsList.add(index, item);
            if (sRollo != null) {
                sRollo.addApp(index, item);
            }
        }

        if (sRollo != null) {
            sRollo.saveAppsList();
            sRollo.resume();
        }
    }

    public void removeApps(ArrayList<ApplicationInfo> list) {
        if (mAllAppsList == null) {
            // Not done loading yet.  We'll find out about it later.
            return;
        }

        if (sRollo != null) {
            sRollo.pause();
        }
        final int N = list.size();
        for (int i=0; i<N; i++) {
            final ApplicationInfo item = list.get(i);
            int index = findAppByComponent(mAllAppsList, item);
            if (index >= 0) {
                mAllAppsList.remove(index);
                if (sRollo != null) {
                    sRollo.removeApp(index);
                }
            } else {
                Log.w(TAG, "couldn't find a match for item \"" + item + "\"");
                // Try to recover.  This should keep us from crashing for now.
            }
        }

        if (sRollo != null) {
            sRollo.saveAppsList();
            sRollo.resume();
        }
    }

    public void updateApps(ArrayList<ApplicationInfo> list) {
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

    class AAMessage extends RenderScript.RSMessageHandler {
        public void run() {
            sRollo.mScrollPos = ((float)mData[0]) / (1 << 16);
            mVelocity = ((float)mData[1]) / (1 << 16);

            boolean lastVisible = isVisible();
            mZoom = ((float)mData[2]) / (1 << 16);

            final boolean visible = isVisible();
            if (visible != lastVisible) {
                post(new Runnable() {
                    public void run() {
                        if (visible) {
                            showSurface();
                        } else {
                            hideSurface();
                        }
                    }
                });
            }

            sZoomDirty = false;
        }
    }

    public static class RolloRS {
        // Allocations ======
        private int mWidth;
        private int mHeight;

        private Resources mRes;
        ScriptC_allapps mScript;

        private Mesh mMesh;
        private ProgramVertexFixedFunction.Constants mPVA;

        private ScriptField_VpConsts mUniformAlloc;

        private Allocation mHomeButtonNormal;
        private Allocation mHomeButtonFocused;
        private Allocation mHomeButtonPressed;

        private Allocation[] mIcons;
        private Allocation mAllocIcons;

        private Allocation[] mLabels;
        private Allocation mAllocLabels;

        private Bitmap mSelectionBitmap;
        private Canvas mSelectionCanvas;

        private float mScrollPos;

        AllApps3D mAllApps;
        boolean mInitialize;

        private boolean checkClickOK() {
            return (Math.abs(mAllApps.mVelocity) < 0.4f) &&
                   (Math.abs(mScrollPos - Math.round(mScrollPos)) < 0.4f);
        }

        void pause() {
            if (sRS != null) {
                sRS.bindRootScript(null);
            }
        }

        void resume() {
            if (sRS != null) {
                sRS.bindRootScript(mScript);
            }
        }

        public RolloRS(AllApps3D allApps) {
            mAllApps = allApps;
        }

        public void init(Resources res, int width, int height) {
            mRes = res;
            mWidth = width;
            mHeight = height;
            mScript = new ScriptC_allapps(sRS, mRes, R.raw.allapps);

            initProgramVertex();
            initProgramFragment();
            initProgramStore();
            initGl();
            initData();

            mScript.bind_gIcons(mAllocIcons);
            mScript.bind_gLabels(mAllocLabels);
            sRS.bindRootScript(mScript);
        }

        public void initMesh() {
            Mesh.TriangleMeshBuilder tm = new Mesh.TriangleMeshBuilder(sRS, 2, 0);

            for (int ct=0; ct < 16; ct++) {
                float pos = (1.f / (16.f - 1)) * ct;
                tm.addVertex(0.0f, pos);
                tm.addVertex(1.0f, pos);
            }
            for (int ct=0; ct < (16 * 2 - 2); ct+= 2) {
                tm.addTriangle(ct, ct+1, ct+2);
                tm.addTriangle(ct+1, ct+3, ct+2);
            }
            mMesh = tm.create(true);
            mScript.set_gSMCell(mMesh);
        }

        Matrix4f getProjectionMatrix(int w, int h) {
            // range -1,1 in the narrow axis at z = 0.
            Matrix4f m1 = new Matrix4f();
            Matrix4f m2 = new Matrix4f();

            if(w > h) {
                float aspect = ((float)w) / h;
                m1.loadFrustum(-aspect,aspect,  -1,1,  1,100);
            } else {
                float aspect = ((float)h) / w;
                m1.loadFrustum(-1,1, -aspect,aspect, 1,100);
            }

            m2.loadRotate(180, 0, 1, 0);
            m1.loadMultiply(m1, m2);

            m2.loadScale(-2, 2, 1);
            m1.loadMultiply(m1, m2);

            m2.loadTranslate(0, 0, 2);
            m1.loadMultiply(m1, m2);
            return m1;
        }

        void resize(int w, int h) {
            Matrix4f proj = getProjectionMatrix(w, h);
            mPVA.setProjection(proj);

            if (mUniformAlloc != null) {
                ScriptField_VpConsts.Item i = new ScriptField_VpConsts.Item();
                i.Proj = proj;
                i.ScaleOffset.x = (2.f / 480.f);
                i.ScaleOffset.y = 0;
                i.ScaleOffset.z = -((float)w / 2) - 0.25f;
                i.ScaleOffset.w = -380.25f;
                i.BendPos.x = 120.f;
                i.BendPos.y = 680.f;
                if (w > h) {
                    i.ScaleOffset.z = 40.f;
                    i.ScaleOffset.w = h - 40.f;
                    i.BendPos.y = 1.f;
                }
                mUniformAlloc.set(i, 0, true);
            }

            mWidth = w;
            mHeight = h;
        }

        private void initProgramVertex() {
            mPVA = new ProgramVertexFixedFunction.Constants(sRS);
            resize(mWidth, mHeight);

            ProgramVertexFixedFunction.Builder pvb = new ProgramVertexFixedFunction.Builder(sRS);
            pvb.setTextureMatrixEnable(true);
            ProgramVertex pv = pvb.create();
            ((ProgramVertexFixedFunction)pv).bindConstants(mPVA);
            sRS.bindProgramVertex(pv);

            mUniformAlloc = new ScriptField_VpConsts(sRS, 1);
            mScript.bind_vpConstants(mUniformAlloc);

            initMesh();
            ProgramVertex.Builder sb = new ProgramVertex.Builder(sRS);
            String t = "varying vec4 varColor;\n" +
                    "varying vec2 varTex0;\n" +
                    "void main() {\n" +
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

                    "  gl_Position = UNI_Proj * pos;\n" +
                    "  varColor.rgba = vec4(lum, lum, lum, 1.0);\n" +
                    "  varTex0.xy = ATTRIB_position;\n" +
                    "  varTex0.y = 1.0 - varTex0.y;\n" +
                    "}\n";
            sb.setShader(t);
            sb.addConstant(mUniformAlloc.getType());
            sb.addInput(mMesh.getVertexAllocation(0).getType().getElement());
            ProgramVertex pvc = sb.create();
            pvc.bindConstants(mUniformAlloc.getAllocation(), 0);

            mScript.set_gPVCurve(pvc);
        }

        private void initProgramFragment() {
            Sampler.Builder sb = new Sampler.Builder(sRS);
            sb.setMinification(Sampler.Value.LINEAR_MIP_LINEAR);
            sb.setMagnification(Sampler.Value.NEAREST);
            sb.setWrapS(Sampler.Value.CLAMP);
            sb.setWrapT(Sampler.Value.CLAMP);
            Sampler linear = sb.create();

            sb.setMinification(Sampler.Value.NEAREST);
            sb.setMagnification(Sampler.Value.NEAREST);
            Sampler nearest = sb.create();

            ProgramFragmentFixedFunction.Builder bf = new ProgramFragmentFixedFunction.Builder(sRS);
            bf.setTexture(ProgramFragmentFixedFunction.Builder.EnvMode.MODULATE,
                          ProgramFragmentFixedFunction.Builder.Format.RGBA, 0);
            bf.setVaryingColor(true);
            ProgramFragment pfTexMip = bf.create();
            pfTexMip.bindSampler(linear, 0);

            bf.setVaryingColor(false);
            ProgramFragment pfTexNearest = bf.create();
            pfTexNearest.bindSampler(nearest, 0);

            bf.setTexture(ProgramFragmentFixedFunction.Builder.EnvMode.MODULATE,
                          ProgramFragmentFixedFunction.Builder.Format.ALPHA, 0);
            bf.setVaryingColor(true);
            ProgramFragment pfTexMipAlpha = bf.create();
            pfTexMipAlpha.bindSampler(linear, 0);

            mScript.set_gPFTexNearest(pfTexNearest);
            mScript.set_gPFTexMip(pfTexMip);
            mScript.set_gPFTexMipAlpha(pfTexMipAlpha);
        }

        private void initProgramStore() {
            ProgramStore.Builder bs = new ProgramStore.Builder(sRS);
            bs.setDepthFunc(ProgramStore.DepthFunc.ALWAYS);
            bs.setColorMaskEnabled(true,true,true,false);
            bs.setDitherEnabled(true);
            bs.setBlendFunc(ProgramStore.BlendSrcFunc.SRC_ALPHA,
                            ProgramStore.BlendDstFunc.ONE_MINUS_SRC_ALPHA);
            mScript.set_gPS(bs.create());
        }

        private void initGl() {
        }

        private void initData() {
            mScript.set_COLUMNS_PER_PAGE_PORTRAIT(Defines.COLUMNS_PER_PAGE_PORTRAIT);
            mScript.set_ROWS_PER_PAGE_PORTRAIT(Defines.ROWS_PER_PAGE_PORTRAIT);
            mScript.set_COLUMNS_PER_PAGE_LANDSCAPE(Defines.COLUMNS_PER_PAGE_LANDSCAPE);
            mScript.set_ROWS_PER_PAGE_LANDSCAPE(Defines.ROWS_PER_PAGE_LANDSCAPE);

            mHomeButtonNormal = Allocation.createFromBitmapResource(sRS, mRes,
                    R.drawable.home_button_normal);
            mHomeButtonFocused = Allocation.createFromBitmapResource(sRS, mRes,
                    R.drawable.home_button_focused);
            mHomeButtonPressed = Allocation.createFromBitmapResource(sRS, mRes,
                    R.drawable.home_button_pressed);

            mScript.set_gHomeButton(mHomeButtonNormal);

            mSelectionBitmap = Bitmap.createBitmap(Defines.SELECTION_TEXTURE_WIDTH_PX,
                    Defines.SELECTION_TEXTURE_HEIGHT_PX, Bitmap.Config.ARGB_8888);
            mSelectionCanvas = new Canvas(mSelectionBitmap);

            setApps(null);
        }

        void dirtyCheck() {
            if (sZoomDirty) {
                setZoom(mAllApps.sNextZoom, mAllApps.sAnimateNextZoom);
            }
        }

        @SuppressWarnings({"ConstantConditions"})
        private void setApps(ArrayList<ApplicationInfo> list) {
            sRollo.pause();
            final int count = list != null ? list.size() : 0;
            int allocCount = count;
            if (allocCount < 1) {
                allocCount = 1;
            }

            mIcons = new Allocation[count];
            mAllocIcons = Allocation.createSized(sRS, Element.ALLOCATION(sRS), allocCount);

            mLabels = new Allocation[count];
            mAllocLabels = Allocation.createSized(sRS, Element.ALLOCATION(sRS), allocCount);

            mScript.set_gIconCount(count);
            for (int i=0; i < count; i++) {
                createAppIconAllocations(i, list.get(i));
            }
            saveAppsList();
            android.util.Log.e("rs", "setApps");
            sRollo.resume();
        }

        private void setZoom(float zoom, boolean animate) {
            if (animate) {
                sRollo.clearSelectedIcon();
                sRollo.setHomeSelected(SELECTED_NONE);
            }
            sRollo.mScript.invoke_setZoom(zoom, animate ? 1 : 0);
        }

        private void createAppIconAllocations(int index, ApplicationInfo item) {
            mIcons[index] = Allocation.createFromBitmap(sRS, item.iconBitmap,
                               Allocation.MipmapControl.MIPMAP_ON_SYNC_TO_TEXTURE,
                               Allocation.USAGE_GRAPHICS_TEXTURE);
            mLabels[index] = Allocation.createFromBitmap(sRS, item.titleBitmap,
                               Allocation.MipmapControl.MIPMAP_ON_SYNC_TO_TEXTURE,
                               Allocation.USAGE_GRAPHICS_TEXTURE);
        }

        /**
         * Puts the empty spaces at the end.  Updates mState.iconCount.  You must
         * fill in the values and call saveAppsList().
         */
        private void reallocAppsList(int count) {
            Allocation[] icons = new Allocation[count];
            mAllocIcons = Allocation.createSized(sRS, Element.ALLOCATION(sRS), count);

            Allocation[] labels = new Allocation[count];
            mAllocLabels = Allocation.createSized(sRS, Element.ALLOCATION(sRS), count);

            final int oldCount = sRollo.mScript.get_gIconCount();

            System.arraycopy(mIcons, 0, icons, 0, oldCount);
            System.arraycopy(mLabels, 0, labels, 0, oldCount);

            mIcons = icons;
            mLabels = labels;
        }

        /**
         * Handle the allocations for the new app.  Make sure you call saveAppsList when done.
         */
        private void addApp(int index, ApplicationInfo item) {
            final int count = mScript.get_gIconCount() - index;
            final int dest = index + 1;

            System.arraycopy(mIcons, index, mIcons, dest, count);
            System.arraycopy(mLabels, index, mLabels, dest, count);

            createAppIconAllocations(index, item);

            mScript.set_gIconCount(mScript.get_gIconCount() + 1);
        }

        /**
         * Handle the allocations for the removed app.  Make sure you call saveAppsList when done.
         */
        private void removeApp(int index) {
            final int count = mScript.get_gIconCount() - index - 1;
            final int src = index + 1;

            System.arraycopy(mIcons, src, mIcons, index, count);
            System.arraycopy(mLabels, src, mLabels, index, count);

            mScript.set_gIconCount(mScript.get_gIconCount() - 1);
            final int last = mScript.get_gIconCount();

            mIcons[last] = null;
            mLabels[last] = null;
        }

        /**
         * Send the apps list structures to RS.
         */
        private void saveAppsList() {
            // WTF: how could mScript be not null but mAllocIconIds null b/2460740.
            if (mScript != null && mAllocIcons != null) {
                if (mIcons.length > 0) {
                    mAllocIcons.copyFrom(mIcons);
                    mAllocLabels.copyFrom(mLabels);
                }

                mScript.bind_gIcons(mAllocIcons);
                mScript.bind_gLabels(mAllocLabels);
            }
        }

        void fling(float pos, float v) {
            mScript.invoke_fling(pos, v);
        }

        void move(float pos) {
            mScript.invoke_move(pos);
        }

        void moveTo(float row) {
            mScript.invoke_moveTo(row);
        }

        /**
         * You need to call save() on mState on your own after calling this.
         *
         * @return the index of the icon that was selected.
         */
        int selectIcon(int x, int y, int pressed) {
            if (mAllApps != null) {
                final int index = mAllApps.chooseTappedIcon(x, y);
                selectIcon(index, pressed);
                return index;
            } else {
                return -1;
            }
        }

        /**
         * Select the icon at the given index.
         *
         * @param index The index.
         * @param pressed one of SELECTED_PRESSED or SELECTED_FOCUSED
         */
        void selectIcon(int index, int pressed) {
            final ArrayList<ApplicationInfo> appsList = mAllApps.mAllAppsList;
            if (appsList == null || index < 0 || index >= appsList.size()) {
                if (mAllApps != null) {
                    mAllApps.mRestoreFocusIndex = index;
                }
                mScript.set_gSelectedIconIndex(-1);
                if (mAllApps.mLastSelection == SELECTION_ICONS) {
                    mAllApps.mLastSelection = SELECTION_NONE;
                }
            } else {
                if (pressed == SELECTED_FOCUSED) {
                    mAllApps.mLastSelection = SELECTION_ICONS;
                }

                int prev = mScript.get_gSelectedIconIndex();
                mScript.set_gSelectedIconIndex(index);

                ApplicationInfo info = appsList.get(index);
                Bitmap selectionBitmap = mSelectionBitmap;

                Utilities.drawSelectedAllAppsBitmap(mSelectionCanvas,
                        selectionBitmap.getWidth(), selectionBitmap.getHeight(),
                        pressed == SELECTED_PRESSED, info.iconBitmap);

                Allocation si = Allocation.createFromBitmap(sRS, selectionBitmap);
                mScript.set_gSelectedIconTexture(si);

                if (prev != index) {
                    if (info.title != null && info.title.length() > 0) {
                        //setContentDescription(info.title);
                        mAllApps.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
                    }
                }
            }
        }

        /**
         * You need to call save() on mState on your own after calling this.
         */
        void clearSelectedIcon() {
            mScript.set_gSelectedIconIndex(-1);
        }

        void setHomeSelected(int mode) {
            final int prev = mAllApps.mLastSelection;
            switch (mode) {
            case SELECTED_NONE:
                mScript.set_gHomeButton(mHomeButtonNormal);
                break;
            case SELECTED_FOCUSED:
                mAllApps.mLastSelection = SELECTION_HOME;
                mScript.set_gHomeButton(mHomeButtonFocused);
                if (prev != SELECTION_HOME) {
                    mAllApps.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
                }
                break;
            case SELECTED_PRESSED:
                mScript.set_gHomeButton(mHomeButtonPressed);
                break;
            }
        }

        public void dumpState() {
            Log.d(TAG, "sRollo.mWidth=" + mWidth);
            Log.d(TAG, "sRollo.mHeight=" + mHeight);
            Log.d(TAG, "sRollo.mIcons=" + Arrays.toString(mIcons));
            if (mIcons != null) {
                Log.d(TAG, "sRollo.mIcons.length=" + mIcons.length);
            }
            //Log.d(TAG, "sRollo.mState.newPositionX=" + mState.newPositionX);
            //Log.d(TAG, "sRollo.mState.newTouchDown=" + mState.newTouchDown);
            //Log.d(TAG, "sRollo.mState.flingVelocity=" + mState.flingVelocity);
            //Log.d(TAG, "sRollo.mState.iconCount=" + mState.iconCount);
            //Log.d(TAG, "sRollo.mState.selectedIconIndex=" + mState.selectedIconIndex);
            //Log.d(TAG, "sRollo.mState.selectedIconTexture=" + mState.selectedIconTexture);
            //Log.d(TAG, "sRollo.mState.zoomTarget=" + mState.zoomTarget);
            //Log.d(TAG, "sRollo.mState.homeButtonId=" + mState.homeButtonId);
            //Log.d(TAG, "sRollo.mState.targetPos=" + mState.targetPos);
        }
    }

    public void dumpState() {
        Log.d(TAG, "sRS=" + sRS);
        Log.d(TAG, "sRollo=" + sRollo);
        ApplicationInfo.dumpApplicationInfoList(TAG, "mAllAppsList", mAllAppsList);
        Log.d(TAG, "mTouchXBorders=" +  Arrays.toString(mTouchXBorders));
        Log.d(TAG, "mTouchYBorders=" +  Arrays.toString(mTouchYBorders));
        Log.d(TAG, "mArrowNavigation=" + mArrowNavigation);
        Log.d(TAG, "mStartedScrolling=" + mStartedScrolling);
        Log.d(TAG, "mLastSelection=" + mLastSelection);
        Log.d(TAG, "mLastSelectedIcon=" + mLastSelectedIcon);
        Log.d(TAG, "mVelocityTracker=" + mVelocityTracker);
        Log.d(TAG, "mTouchTracking=" + mTouchTracking);
        Log.d(TAG, "mShouldGainFocus=" + mShouldGainFocus);
        Log.d(TAG, "sZoomDirty=" + sZoomDirty);
        Log.d(TAG, "sAnimateNextZoom=" + sAnimateNextZoom);
        Log.d(TAG, "mZoom=" + mZoom);
        Log.d(TAG, "mScrollPos=" + sRollo.mScrollPos);
        Log.d(TAG, "mVelocity=" + mVelocity);
        Log.d(TAG, "mMessageProc=" + mMessageProc);
        if (sRollo != null) {
            sRollo.dumpState();
        }
        if (sRS != null) {
            sRS.contextDump();
        }
    }
}
