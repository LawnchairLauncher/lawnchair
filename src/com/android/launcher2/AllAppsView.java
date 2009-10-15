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

import java.io.Writer;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.lang.Float;
import java.util.Collections;
import java.util.Comparator;

import android.renderscript.RSSurfaceView;
import android.renderscript.RenderScript;

import android.renderscript.RenderScript;
import android.renderscript.ProgramVertex;
import android.renderscript.Element;
import android.renderscript.Allocation;
import android.renderscript.Type;
import android.renderscript.Script;
import android.renderscript.ScriptC;
import android.renderscript.ProgramFragment;
import android.renderscript.ProgramStore;
import android.renderscript.Sampler;
import android.renderscript.SimpleMesh;

import android.content.Context;
import android.content.res.Resources;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Message;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.graphics.PixelFormat;


public class AllAppsView extends RSSurfaceView
        implements View.OnClickListener, View.OnLongClickListener, DragSource {
    private static final String TAG = "Launcher.AllAppsView";

    /** Bit for mLocks for when there are icons being loaded. */
    private static final int LOCK_ICONS_PENDING = 1;

    private static final int TRACKING_FLING = 0;
    private static final int TRACKING_HOME = 1;

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

    private int mPageCount;
    private boolean mStartedScrolling;
    private VelocityTracker mVelocity;
    private int mTouchTracking;
    private int mMotionDownRawX;
    private int mMotionDownRawY;
    private int mDownIconIndex = -1;
    private int mCurrentIconIndex = -1;
    private int mHomeButtonTop;
    private long mTouchTime;
    private boolean mRotateMove = true;

    static class Defines {
        public static final int ALLOC_PARAMS = 0;
        public static final int ALLOC_STATE = 1;
        public static final int ALLOC_ICON_IDS = 3;
        public static final int ALLOC_LABEL_IDS = 4;
        public static final int ALLOC_X_BORDERS = 5;
        public static final int ALLOC_Y_BORDERS = 6;

        public static final int COLUMNS_PER_PAGE = 4;
        public static final int ROWS_PER_PAGE = 4;

        public static final float RADIUS = 4.0f;

        public static final int ICON_WIDTH_PX = 64;
        public static final int ICON_TEXTURE_WIDTH_PX = 128;

        public static final int ICON_HEIGHT_PX = 64;
        public static final int ICON_TEXTURE_HEIGHT_PX = 128;
        public static final float ICON_TOP_OFFSET = 0.2f;

        public static final float CAMERA_Z = -2;

        public int SCREEN_WIDTH_PX;
        public int SCREEN_HEIGHT_PX;

        public float FAR_ICON_SIZE;

        public void recompute(int w, int h) {
            SCREEN_WIDTH_PX = 480;
            SCREEN_HEIGHT_PX = 800;
            FAR_ICON_SIZE = farSize(2 * ICON_WIDTH_PX / (float)w);
        }

        private static float farSize(float sizeAt0) {
            return sizeAt0 * (Defines.RADIUS - Defines.CAMERA_Z) / -Defines.CAMERA_Z;
        }
    }

    public AllAppsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        final ViewConfiguration config = ViewConfiguration.get(context);
        mSlop = config.getScaledTouchSlop();
        mMaxFlingVelocity = config.getScaledMaximumFlingVelocity();

        setOnClickListener(this);
        setOnLongClickListener(this);
        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
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

        destroyRenderScript();
        mRS = null;
        mRollo = null;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        super.surfaceChanged(holder, format, w, h);

        long startTime = SystemClock.uptimeMillis();

        mRS = createRenderScript(true);
        mRollo = new RolloRS();
        mRollo.init(getResources(), w, h);
        if (mAllAppsList != null) {
            mRollo.setApps(mAllAppsList);
            Log.d(TAG, "surfaceChanged... calling mRollo.setApps");
        }

        Resources res = getContext().getResources();
        int barHeight = (int)res.getDimension(R.dimen.button_bar_height);
        mHomeButtonTop = h - barHeight;

        long endTime = SystemClock.uptimeMillis();
        Log.d(TAG, "surfaceChanged took " + (endTime-startTime) + "ms");
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        // this method doesn't work when 'extends View' include 'extends ScrollView'.
        return super.onKeyDown(keyCode, event);
    }

    private int mRSMode = 0;

    @Override
    public boolean onTouchEvent(MotionEvent ev)
    {
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
            if (x < 60 && y > 700) {
                //mRotateMove = mRollo.setView((++mRSMode) & 3);
            }

            if (y > mRollo.mTouchYBorders[mRollo.mTouchYBorders.length-1]) {
                mTouchTracking = TRACKING_HOME;
            } else {
                mTouchTracking = TRACKING_FLING;

                mMotionDownRawX = (int)ev.getRawX();
                mMotionDownRawY = (int)ev.getRawY();

                if (mRotateMove) {
                    mRollo.mState.newPositionX = ev.getRawY() / mDefines.SCREEN_WIDTH_PX;
                } else {
                    mRollo.mState.newPositionX = ev.getRawX() / mDefines.SCREEN_WIDTH_PX;
                }
                mRollo.mState.newTouchDown = 1;

                if (!mRollo.checkClickOK()) {
                    mRollo.clearSelectedIcon();
                } else {
                    mDownIconIndex = mCurrentIconIndex
                            = mRollo.selectIcon(x, y, mRollo.mMessageProc.mPosX);
                    if (mDownIconIndex < 0) {
                        // if nothing was selected, no long press.
                        cancelLongPress();
                    }
                }
                mRollo.mState.save();
                mRollo.move();
                mVelocity = VelocityTracker.obtain();
                mVelocity.addMovement(ev);
                mStartedScrolling = false;
            }
            break;
        case MotionEvent.ACTION_MOVE:
        case MotionEvent.ACTION_OUTSIDE:
            if (mTouchTracking == TRACKING_HOME) {
                // TODO: highlight?
            } else {
                int rawX = (int)ev.getRawX();
                int rawY = (int)ev.getRawY();
                int slop;
                if (mRotateMove) {
                    slop = Math.abs(rawY - mMotionDownRawY);
                } else {
                    slop = Math.abs(rawX - mMotionDownRawX);
                }

                if (!mStartedScrolling && slop < mSlop) {
                    // don't update anything so when we do start scrolling
                    // below, we get the right delta.
                    mCurrentIconIndex = mRollo.chooseTappedIcon(x, y, mRollo.mMessageProc.mPosX);
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
                    if (mRotateMove) {
                        mRollo.mState.newPositionX = ev.getRawY() / mDefines.SCREEN_WIDTH_PX;
                    } else {
                        mRollo.mState.newPositionX = ev.getRawX() / mDefines.SCREEN_WIDTH_PX;
                    }
                    mRollo.mState.newTouchDown = 1;
                    mRollo.move();

                    mStartedScrolling = true;
                    mRollo.clearSelectedIcon();
                    mVelocity.addMovement(ev);
                    mRollo.mState.save();
                }
            }
            break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
            if (mTouchTracking == TRACKING_HOME) {
                if (action == MotionEvent.ACTION_UP) {
                    if (y > mRollo.mTouchYBorders[mRollo.mTouchYBorders.length-1]) {
                        mLauncher.closeAllApps(true);
                    }
                }
            } else {
                mRollo.mState.newTouchDown = 0;
                if (mRotateMove) {
                    mRollo.mState.newPositionX = ev.getRawY() / mDefines.SCREEN_WIDTH_PX;
                } else {
                    mRollo.mState.newPositionX = ev.getRawX() / mDefines.SCREEN_WIDTH_PX;
                }

                mVelocity.computeCurrentVelocity(1000 /* px/sec */, mMaxFlingVelocity);
                if (mRotateMove) {
                    mRollo.mState.flingVelocityX
                            = mVelocity.getYVelocity() / mDefines.SCREEN_WIDTH_PX;
                } else {
                    mRollo.mState.flingVelocityX
                            = mVelocity.getXVelocity() / mDefines.SCREEN_WIDTH_PX;
                }
                mRollo.clearSelectedIcon();
                mRollo.mState.save();
                mRollo.fling();

                if (mVelocity != null) {
                    mVelocity.recycle();
                    mVelocity = null;
                }
                break;
            }
        }

        return true;
    }

    public void onClick(View v) {
        if (mLocks != 0 || !isVisible()) {
            return;
        }
        if (mRollo.checkClickOK() && mCurrentIconIndex == mDownIconIndex
                && mCurrentIconIndex >= 0 && mCurrentIconIndex < mAllAppsList.size()) {
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

            // We don't really have an accurate location to use.  This will do.
            int screenX = mMotionDownRawX - (mDefines.ICON_WIDTH_PX / 2);
            int screenY = mMotionDownRawY - mDefines.ICON_HEIGHT_PX;

            int left = (mDefines.ICON_TEXTURE_WIDTH_PX - mDefines.ICON_WIDTH_PX) / 2;
            int top = (mDefines.ICON_TEXTURE_HEIGHT_PX - mDefines.ICON_HEIGHT_PX) / 2;
            mDragController.startDrag(app.iconBitmap, screenX, screenY,
                    left, top, mDefines.ICON_WIDTH_PX, mDefines.ICON_HEIGHT_PX,
                    this, app, DragController.DRAG_ACTION_COPY);

            mLauncher.closeAllApps(true);
        }
        return true;
    }

    public void setDragController(DragController dragger) {
        mDragController = dragger;
    }

    public void onDropCompleted(View target, boolean success) {
    }

    /**
     * Zoom to the specifed amount.
     *
     * @param amount [0..1] 0 is hidden, 1 is open
     * @param animate Whether to animate.
     */
    public void zoom(float amount) {
        if (mRollo == null) {
            return;
        }

        cancelLongPress();
        mRollo.clearSelectedIcon();
        if (amount > 0.001f) {
            // set in readback, so we're correct even before the next frame
            mRollo.mState.zoomTarget = amount;
        } else {
            mRollo.mState.zoomTarget = 0;
        }
        mRollo.mState.save();
    }

    public boolean isVisible() {
        if (mRollo == null) {
            return false;
        }
        return mRollo.mMessageProc.mZoom > 0.001f;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev)
    {
        float x = ev.getX();
        float y = ev.getY();
        //Float tx = new Float(x);
        //Float ty = new Float(y);
        //Log.e("rs", "tbe " + tx.toString() + ", " + ty.toString());


        return true;
    }

    public void setApps(ArrayList<ApplicationInfo> list) {
        mAllAppsList = list;
        if (mRollo != null) {
            mRollo.setApps(list);
        }
        mPageCount = countPages(list.size());
        mLocks &= ~LOCK_ICONS_PENDING;
    }

    public void addApps(ArrayList<ApplicationInfo> list) {
        final int N = list.size();
        if (mRollo != null) {
            mRollo.reallocAppsList(mRollo.mState.iconCount + N);
        }

        for (int i=0; i<N; i++) {
            final ApplicationInfo item = list.get(i);
            int index = Collections.binarySearch(mAllAppsList, item, mAppNameComp);
            if (index < 0) {
                index = -(index+1);
            }
            mAllAppsList.add(index, item);
            if (mRollo != null) {
                mRollo.addApp(index, item);
                mRollo.mState.iconCount++;
            }
        }

        if (mRollo != null) {
            mRollo.saveAppsList();
        }
    }

    public void removeApps(ArrayList<ApplicationInfo> list) {
        final int N = list.size();
        for (int i=0; i<N; i++) {
            final ApplicationInfo item = list.get(i);
            int index = Collections.binarySearch(mAllAppsList, item, mAppIntentComp);
            if (index >= 0) {
                mAllAppsList.remove(index);
                if (mRollo != null) {
                    mRollo.removeApp(index);
                    mRollo.mState.iconCount--;
                }
            } else {
                Log.e(TAG, "couldn't find a match for item \"" + item + "\"");
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

    private Comparator<ApplicationInfo> mAppNameComp = new Comparator<ApplicationInfo>() {
        public int compare(ApplicationInfo a, ApplicationInfo b) {
            int result = a.title.toString().compareTo(b.toString());
            if (result != 0) {
                return result;
            }
            return a.intent.getComponent().compareTo(b.intent.getComponent());
        }
    };

    private Comparator<ApplicationInfo> mAppIntentComp = new Comparator<ApplicationInfo>() {
        public int compare(ApplicationInfo a, ApplicationInfo b) {
            return a.intent.getComponent().compareTo(b.intent.getComponent());
        }
    };

    private static int countPages(int iconCount) {
        int iconsPerPage = Defines.COLUMNS_PER_PAGE * Defines.ROWS_PER_PAGE;
        int pages = iconCount / iconsPerPage;
        if (pages*iconsPerPage != iconCount) {
            pages++;
        }
        return pages;
    }

    public class RolloRS {

        // Allocations ======
        private int mViewMode = 0;

        private int mWidth;
        private int mHeight;

        private Resources mRes;
        private Script[] mScript = new Script[4];

        private Script.Invokable[] mInvokeMove = new Script.Invokable[4];
        private Script.Invokable[] mInvokeFling = new Script.Invokable[4];
        private Script.Invokable[] mInvokeResetWAR = new Script.Invokable[4];

        private ProgramStore mPSIcons;
        private ProgramStore mPSText;
        private ProgramFragment mPFColor;
        private ProgramFragment mPFTexLinear;
        private ProgramFragment mPFTexNearest;
        private ProgramVertex mPV;
        private ProgramVertex mPVOrtho;
        private SimpleMesh mMesh;
        private SimpleMesh mMesh2;

        private Allocation mHomeButton;

        private Allocation[] mIcons;
        private int[] mIconIds;
        private Allocation mAllocIconIds;

        private Allocation[] mLabels;
        private int[] mLabelIds;
        private Allocation mAllocLabelIds;
        private Allocation mSelectedIcon;

        private int[] mTouchYBorders;
        private Allocation mAllocTouchYBorders;
        private int[] mTouchXBorders;
        private Allocation mAllocTouchXBorders;

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

        class AAMessage extends RenderScript.RSMessage {
            public void run() {
                mPosX = ((float)mData[0]) / (1 << 16);
                mVelocity = ((float)mData[1]) / (1 << 16);
                mZoom = ((float)mData[2]) / (1 << 16);
                //Log.d("rs", "new msg " + mPosX + "  " + mVelocity + "  " + mZoom);
            }
            float mZoom;
            float mPosX;
            float mVelocity;
        }
        AAMessage mMessageProc;

        private boolean checkClickOK() {
            //android.util.Log.e("rs", "check click " + Float.toString(mReadback.velocity) + ", " + Float.toString(mReadback.posX));
            return (Math.abs(mMessageProc.mVelocity) < 0.1f) &&
                   (Math.abs(mMessageProc.mPosX - Math.round(mMessageProc.mPosX)) < 0.1f);
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

            public int homeButtonId;
            public int homeButtonWidth;
            public int homeButtonHeight;
            public int homeButtonTextureWidth;
            public int homeButtonTextureHeight;
        }

        class State extends BaseAlloc {
            public float newPositionX;
            public int newTouchDown;
            public float flingVelocityX;
            public int iconCount;
            public int selectedIconIndex = -1;
            public int selectedIconTexture;
            public float zoomTarget;

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
            mDefines.recompute(width, height);
            initProgramVertex();
            initProgramFragment();
            initProgramStore();
            initMesh();
            initMesh2();
            initGl();
            initData();
            initTouchState();
            initRs();
        }

        public void initMesh() {
            SimpleMesh.TriangleMeshBuilder tm = new SimpleMesh.TriangleMeshBuilder(mRS, 3,
                SimpleMesh.TriangleMeshBuilder.TEXTURE_0 | SimpleMesh.TriangleMeshBuilder.COLOR);

            for (int ct=0; ct < 450; ct++) {
                float x = 0;
                float z = 0;
                float l = 1.f;

                if (ct < 190) {
                    z = 0.1f + 0.05f * (190 - ct);
                    x = -1;
                    l = 0.125f + (0.125f / 190.f) * ct;
                } else if (ct >= 190 && ct < 200) {
                    float a = (3.14f * 0.5f) * (0.1f * (ct - 200));
                    float s = (float)Math.sin(a);
                    float c = (float)Math.cos(a);
                    x = -0.9f + s * 0.1f;
                    z = 0.1f - c * 0.1f;
                    l = 0.25f + 0.075f * (ct - 190);
                } else if (ct >= 200 && ct < 250) {
                    z = 0.f;
                    x = -0.9f + (1.8f * (ct - 200) / 50.f);
                } else if (ct >= 250 && ct < 260) {
                    float a = (3.14f * 0.5f) * (0.1f * (ct - 250));
                    float s = (float)Math.sin(a);
                    float c = (float)Math.cos(a);
                    x = 0.9f + s * 0.1f;
                    z = 0.1f - c * 0.1f;
                    l = 0.25f + 0.075f * (260 - ct);
                } else if (ct >= 260) {
                    z = 0.1f + 0.05f * (ct - 260);
                    x = 1;
                    l = 0.125f + (0.125f / 190.f) * (450 - ct);
                }
                //Log.e("rs", "ct " + Integer.toString(ct) + "  x = " + Float.toString(x) + ", z = " + Float.toString(z));
                //Log.e("rs", "ct " + Integer.toString(ct) + "  l = " + Float.toString(l));
                float s = ct * 0.1f;
                tm.setColor(l, l, l, 0.99f);
                tm.setTexture(s, 1);
                tm.addVertex(x, -0.5f, z);
                tm.setTexture(s, 0);
                tm.addVertex(x, 0.5f, z);
            }
            for (int ct=0; ct < (450*2 - 2); ct+= 2) {
                tm.addTriangle(ct, ct+1, ct+2);
                tm.addTriangle(ct+1, ct+3, ct+2);
            }
            mMesh = tm.create();
            mMesh.setName("SMMesh");

        }


        public void initMesh2() {
            SimpleMesh.TriangleMeshBuilder tm = new SimpleMesh.TriangleMeshBuilder(mRS, 3,
                SimpleMesh.TriangleMeshBuilder.TEXTURE_0 | SimpleMesh.TriangleMeshBuilder.COLOR);

            float y = 0;
            float z = 0;
            for (int ct=0; ct < 200; ct++) {
                float angle = 0;
                float maxAngle = 3.14f * 0.16f;
                float l = 1.f;

                l = 1 - ((ct-5) * 0.10f);
                if (ct > 7) {
                    angle = maxAngle * (ct - 7) * 0.2f;
                    angle = Math.min(angle, maxAngle);
                }
                l = Math.max(0.3f, l);
                l = Math.min(1.0f, l);

                y += 0.1f * Math.cos(angle);
                z += 0.1f * Math.sin(angle);

                float t = 0.1f * ct;
                float ds = 0.08f;
                tm.setColor(l, l, l, 0.99f);
                tm.setTexture(ds, t);
                tm.addVertex(-0.5f, y, z);
                tm.setTexture(1 - ds, t);
                tm.addVertex(0.5f, y, z);
            }
            for (int ct=0; ct < (200 * 2 - 2); ct+= 2) {
                tm.addTriangle(ct, ct+1, ct+2);
                tm.addTriangle(ct+1, ct+3, ct+2);
            }
            mMesh2 = tm.create();
            mMesh2.setName("SMMesh2");
        }

        private void initProgramVertex() {
            ProgramVertex.MatrixAllocation pva = new ProgramVertex.MatrixAllocation(mRS);
            pva.setupProjectionNormalized(mWidth, mHeight);

            ProgramVertex.Builder pvb = new ProgramVertex.Builder(mRS, null, null);
            pvb.setTextureMatrixEnable(true);
            mPV = pvb.create();
            mPV.setName("PV");
            mPV.bindAllocation(pva);

            pva = new ProgramVertex.MatrixAllocation(mRS);
            pva.setupOrthoWindow(mWidth, mHeight);
            pvb.setTextureMatrixEnable(true);
            mPVOrtho = pvb.create();
            mPVOrtho.setName("PVOrtho");
            mPVOrtho.bindAllocation(pva);

            mRS.contextBindProgramVertex(mPV);
        }

        private void initProgramFragment() {
            Sampler.Builder sb = new Sampler.Builder(mRS);
            sb.setMin(Sampler.Value.LINEAR);
            sb.setMag(Sampler.Value.LINEAR);
            sb.setWrapS(Sampler.Value.CLAMP);
            sb.setWrapT(Sampler.Value.CLAMP);
            Sampler linear = sb.create();

            sb.setMin(Sampler.Value.NEAREST);
            sb.setMag(Sampler.Value.NEAREST);
            Sampler nearest = sb.create();

            ProgramFragment.Builder bf = new ProgramFragment.Builder(mRS, null, null);
            mPFColor = bf.create();
            mPFColor.setName("PFColor");

            bf.setTexEnable(true, 0);
            bf.setTexEnvMode(ProgramFragment.EnvMode.MODULATE, 0);
            mPFTexLinear = bf.create();
            mPFTexLinear.setName("PFTexLinear");
            mPFTexLinear.bindSampler(linear, 0);

            mPFTexNearest = bf.create();
            mPFTexNearest.setName("PFTexNearest");
            mPFTexNearest.bindSampler(nearest, 0);
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

            //bs.setDitherEnable(false);
            //mPSText = bs.create();
            //mPSText.setName("PSText");
        }

        private void initGl() {
            mTouchXBorders = new int[Defines.COLUMNS_PER_PAGE+1];
            mAllocTouchXBorders = Allocation.createSized(mRS, Element.USER_I32(mRS),
                    mTouchXBorders.length);
            mAllocTouchXBorders.data(mTouchXBorders);

            mTouchYBorders = new int[Defines.ROWS_PER_PAGE+1];
            mAllocTouchYBorders = Allocation.createSized(mRS, Element.USER_I32(mRS),
                    mTouchYBorders.length);
            mAllocTouchYBorders.data(mTouchYBorders);
        }

        private void initData() {
            mParams = new Params();
            mState = new State();

            final Utilities.BubbleText bubble = new Utilities.BubbleText(getContext());

            mParams.bubbleWidth = bubble.getBubbleWidth();
            mParams.bubbleHeight = bubble.getMaxBubbleHeight();
            mParams.bubbleBitmapWidth = bubble.getBitmapWidth();
            mParams.bubbleBitmapHeight = bubble.getBitmapHeight();

            mHomeButton = Allocation.createFromBitmapResource(mRS, mRes,
                    R.drawable.home_button, Element.RGBA_8888(mRS), false);
            mHomeButton.uploadToTexture(0);
            mParams.homeButtonId = mHomeButton.getID();
            mParams.homeButtonWidth = 76;
            mParams.homeButtonHeight = 68;
            mParams.homeButtonTextureWidth = 128;
            mParams.homeButtonTextureHeight = 128;

            mParams.save();
            mState.save();

            mSelectionBitmap = Bitmap.createBitmap(Defines.ICON_TEXTURE_WIDTH_PX,
                    Defines.ICON_TEXTURE_HEIGHT_PX, Bitmap.Config.ARGB_8888);
            mSelectionCanvas = new Canvas(mSelectionBitmap);

            setApps(null);
        }

        private void initScript(int idx, int id) {
            ScriptC.Builder sb = new ScriptC.Builder(mRS);
            sb.setScript(mRes, id);
            sb.setRoot(true);
            sb.addDefines(mDefines);
            sb.setType(mParams.mType, "params", Defines.ALLOC_PARAMS);
            sb.setType(mState.mType, "state", Defines.ALLOC_STATE);
            mInvokeMove[idx] = sb.addInvokable("move");
            mInvokeFling[idx] = sb.addInvokable("fling");
            mInvokeResetWAR[idx] = sb.addInvokable("resetHWWar");
            mScript[idx] = sb.create();
            mScript[idx].setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            mScript[idx].bindAllocation(mParams.mAlloc, Defines.ALLOC_PARAMS);
            mScript[idx].bindAllocation(mState.mAlloc, Defines.ALLOC_STATE);
            mScript[idx].bindAllocation(mAllocIconIds, Defines.ALLOC_ICON_IDS);
            mScript[idx].bindAllocation(mAllocLabelIds, Defines.ALLOC_LABEL_IDS);
            mScript[idx].bindAllocation(mAllocTouchXBorders, Defines.ALLOC_X_BORDERS);
            mScript[idx].bindAllocation(mAllocTouchYBorders, Defines.ALLOC_Y_BORDERS);
        }

        private void initRs() {
            mViewMode = 0;
            initScript(0, R.raw.rollo3);
            initScript(1, R.raw.rollo2);
            initScript(2, R.raw.rollo);
            initScript(3, R.raw.rollo4);

            mMessageProc = new AAMessage();
            mRS.mMessageCallback = mMessageProc;
            mRS.contextBindRootScript(mScript[mViewMode]);
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

            Utilities.BubbleText bubble = new Utilities.BubbleText(getContext());

            for (int i=0; i<count; i++) {
                uploadAppIcon(i, list.get(i));
            }

            mState.iconCount = count;

            saveAppsList();
        }

        private void uploadAppIcon(int index, ApplicationInfo item) {
            mIcons[index] = Allocation.createFromBitmap(mRS, item.iconBitmap,
                    Element.RGBA_8888(mRS), false);
            mLabels[index] = Allocation.createFromBitmap(mRS, item.titleBitmap,
                    Element.RGBA_8888(mRS), false);

            mIcons[index].uploadToTexture(0);
            mLabels[index].uploadToTexture(0);

            mIconIds[index] = mIcons[index].getID();
            mLabelIds[index] = mLabels[index].getID();
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

            final int oldCount = mIcons.length;

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

            uploadAppIcon(index, item);
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

            final int last = mState.iconCount - 1;
            mIcons[last] = null;
            mIconIds[last] = 0;
            mLabels[last] = null;
            mLabelIds[last] = 0;
        }

        /**
         * Send the apps list structures to RS.
         */
        private void saveAppsList() {
            mRS.contextBindRootScript(null);

            mAllocIconIds.data(mIconIds);
            mAllocLabelIds.data(mLabelIds);

            if (mScript[0] != null) { // this happens when we init it
                for (int ct=0; ct < 4; ct++) {
                    mScript[ct].bindAllocation(mAllocIconIds, Defines.ALLOC_ICON_IDS);
                    mScript[ct].bindAllocation(mAllocLabelIds, Defines.ALLOC_LABEL_IDS);
                }
            }

            mState.save();

            // Note: mScript may be null if we haven't initialized it yet.
            // In that case, this is a no-op.
            if (mInvokeResetWAR != null &&
                mInvokeResetWAR[mViewMode] != null) {
                mInvokeResetWAR[mViewMode].execute();
            }
            mRS.contextBindRootScript(mScript[mViewMode]);
        }

        void initTouchState() {
            int width = getWidth();
            int height = getHeight();

            int iconsSize;
            if (width < height) {
                iconsSize = width;
            } else {
                iconsSize = height;
            }
            int cellHeight = iconsSize / Defines.ROWS_PER_PAGE;
            int cellWidth = iconsSize / Defines.COLUMNS_PER_PAGE;

            int centerY = (height / 2) - (int)(cellHeight * 0.2f);
            mTouchYBorders[0] = centerY - (int)(2.8f * cellHeight);
            mTouchYBorders[1] = centerY - (int)(1.25f * cellHeight);
            mTouchYBorders[2] = centerY;
            mTouchYBorders[3] = centerY + (int)(1.25f * cellHeight);;
            mTouchYBorders[4] = centerY + (int)(2.6f * cellHeight);

            mAllocTouchYBorders.data(mTouchYBorders);

            int centerX = (width / 2);
            mTouchXBorders[0] = centerX - (2 * cellWidth);
            mTouchXBorders[1] = centerX - (int)(0.85f * cellWidth);
            mTouchXBorders[2] = centerX;
            mTouchXBorders[3] = centerX + (int)(0.85f * cellWidth);
            mTouchXBorders[4] = centerX + (2 * cellWidth);

            mAllocTouchXBorders.data(mTouchXBorders);
        }

        int chooseTappedIconHorz(int x, int y, float page) {
            int currentPage = (int)page;

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

            return (currentPage * Defines.ROWS_PER_PAGE * Defines.COLUMNS_PER_PAGE)
                    + (row * Defines.ROWS_PER_PAGE) + col;
        }

        int chooseTappedIconVert(int x, int y, float pos) {
            int ydead = (getHeight() - 4 * 145) / 2;
            if (y < ydead || y > (getHeight() - ydead)) {
                return -1;
            }

            y -= ydead;
            y += pos * 145;
            int row = y / 145;
            int col = x / 120;

            return row * 4 + col;
        }

        int chooseTappedIcon(int x, int y, float pos) {
            int index;
            if (mViewMode != 0) {
                index = chooseTappedIconHorz(x, y, pos);
            } else {
                index = chooseTappedIconVert(x, y, pos);
            }
            final int iconCount = mAllAppsList.size();
            if (index >= iconCount) {
                index = -1;
            }
            return index;
        }

        boolean setView(int v) {
            mViewMode = v;
            mRS.contextBindRootScript(mScript[mViewMode]);
            return (v == 0);
        }

        void fling() {
            mInvokeFling[mViewMode].execute();
        }

        void move() {
            mInvokeMove[mViewMode].execute();
        }

        /**
         * You need to call save() on mState on your own after calling this.
         *
         * @return the index of the icon that was selected.
         */
        int selectIcon(int x, int y, float pos) {
            final int index = chooseTappedIcon(x, y, pos);
            selectIcon(index);
            return index;
        }

        void selectIcon(int index) {
            if (index < 0) {
                mState.selectedIconIndex = -1;
            } else {
                mState.selectedIconIndex = index;

                Bitmap selectionBitmap = mSelectionBitmap;

                Utilities.drawSelectedAllAppsBitmap(mSelectionCanvas,
                        selectionBitmap.getWidth(), selectionBitmap.getHeight(),
                        mAllAppsList.get(index).iconBitmap);

                mSelectedIcon = Allocation.createFromBitmap(mRS, selectionBitmap,
                        Element.RGBA_8888(mRS), false);
                mSelectedIcon.uploadToTexture(0);
                mState.selectedIconTexture = mSelectedIcon.getID();
            }
        }

        /**
         * You need to call save() on mState on your own after calling this.
         */
        void clearSelectedIcon() {
            mState.selectedIconIndex = -1;
        }

    }
}


