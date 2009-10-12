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

import android.content.Context;
import android.content.res.Resources;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
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

    private int mSlopX;
    private int mMaxFlingVelocity;

    private Defines mDefines = new Defines();
    private RenderScript mRS;
    private RolloRS mRollo;
    private ArrayList<ApplicationInfo> mAllAppsList;

    private int mPageCount;
    private boolean mStartedScrolling;
    private VelocityTracker mVelocity;
    private int mTouchTracking;
    private int mLastMotionX;
    private int mMotionDownRawX;
    private int mMotionDownRawY;
    private int mHomeButtonTop;
    private long mTouchTime;
    private boolean mZoomSwipeInProgress;

    static class Defines {
        public static final int ALLOC_PARAMS = 0;
        public static final int ALLOC_STATE = 1;
        public static final int ALLOC_READBACK = 2;
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
        mSlopX = config.getScaledTouchSlop();
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

        int deltaX;
        int action = ev.getAction();
        switch (action) {
        case MotionEvent.ACTION_DOWN:
            if (y > mRollo.mTouchYBorders[mRollo.mTouchYBorders.length-1]) {
                mTouchTracking = TRACKING_HOME;
            } else {
                mTouchTracking = TRACKING_FLING;

                mMotionDownRawX = (int)ev.getRawX();
                mMotionDownRawY = (int)ev.getRawY();
                mLastMotionX = x;
                mRollo.mReadback.read();

                mRollo.mState.newPositionX = ev.getRawX() / mDefines.SCREEN_WIDTH_PX;
                mRollo.mState.newTouchDown = 1;

                if (!mRollo.checkClickOK()) {
                    mRollo.clearSelectedIcon();
                } else {
                    mRollo.selectIcon(x, y, mRollo.mReadback.posX);
                }
                mRollo.mState.save();
                mRollo.mInvokeMove.execute();
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
                int slopX = Math.abs(x - mLastMotionX);
                if (!mStartedScrolling && slopX < mSlopX) {
                    // don't update mLastMotionX so slopX is right and when we do start scrolling
                    // below, we get the right delta.
                } else {
                    mRollo.mState.newPositionX = ev.getRawX() / mDefines.SCREEN_WIDTH_PX;
                    mRollo.mState.newTouchDown = 1;
                    mRollo.mInvokeMove.execute();

                    mStartedScrolling = true;
                    mRollo.clearSelectedIcon();
                    deltaX = x - mLastMotionX;
                    mVelocity.addMovement(ev);
                    mRollo.mState.save();
                    mLastMotionX = x;
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
                mRollo.mState.newPositionX = ev.getRawX() / mDefines.SCREEN_WIDTH_PX;

                if (!mZoomSwipeInProgress) {
                    mVelocity.computeCurrentVelocity(1000 /* px/sec */, mMaxFlingVelocity);
                    mRollo.mState.flingVelocityX
                            = mVelocity.getXVelocity() / mDefines.SCREEN_WIDTH_PX;
                    mRollo.clearSelectedIcon();
                    mRollo.mState.save();
                    mRollo.mInvokeFling.execute();
                } else {
                    mRollo.mState.save();
                    mRollo.mInvokeMove.execute();
                }

                mLastMotionX = -10000;
                mVelocity.recycle();
                mVelocity = null;
                break;
            }
        }

        return true;
    }

    public void onClick(View v) {
        if (mLocks != 0 || !isVisible()) {
            return;
        }
        int index = mRollo.mState.selectedIconIndex;
        if (mRollo.checkClickOK() && index >= 0 && index < mAllAppsList.size()) {
            ApplicationInfo app = mAllAppsList.get(index);
            mLauncher.startActivitySafely(app.intent);
        }
    }

    public boolean onLongClick(View v) {
        if (mLocks != 0 || !isVisible()) {
            return true;
        }
        int index = mRollo.mState.selectedIconIndex;
        Log.d(TAG, "long click! velocity=" + mRollo.mReadback.velocity + " index=" + index);
        if (mRollo.checkClickOK() && index >= 0 && index < mAllAppsList.size()) {
            ApplicationInfo app = mAllAppsList.get(index);

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

    public void setZoomSwipeInProgress(boolean swiping, boolean touchStillDown) {
        mZoomSwipeInProgress = swiping;
        if (!touchStillDown) {
            mRollo.mState.newTouchDown = 0;
            mRollo.mState.save();
            mRollo.mInvokeTouchUp.execute();
        }
    }

    public void setZoomTarget(float amount) {
        zoom(amount, true);
    }

    public void setZoom(float amount) {
        zoom(amount, false);
    }

    private void zoom(float amount, boolean animate) {
        if (mRollo == null) {
            return;
        }

        cancelLongPress();
        mRollo.clearSelectedIcon();
        if (amount > 0.001f) {
            // set in readback, so we're correct even before the next frame
            mRollo.mReadback.zoom = mRollo.mState.zoomTarget = amount;
            if (!animate) {
                mRollo.mState.zoom = amount;
                mRollo.mReadback.save();
            }
        } else {
            mRollo.mState.zoomTarget = 0;
            if (!animate) {
                mRollo.mReadback.zoom = mRollo.mState.zoom = 0;
                mRollo.mReadback.save();
            }
        }
        mRollo.mState.save();
        if (!animate) {
            mRollo.mInvokeSetZoom.execute();
        } else {
            mRollo.mInvokeSetZoomTarget.execute();
        }
        mReadZoom.removeMessages(1);
        mReadZoom.sendEmptyMessageDelayed(1, 1000);
    }

    Handler mReadZoom = new Handler() {
        public void handleMessage(Message msg) {
            if(mRollo != null && mRollo.mReadback != null) {
                // FIXME: These checks may indicate other problems.
                mRollo.mReadback.read();
            }
        }
    };

    public boolean isVisible() {
        if (mRollo == null) {
            return false;
        }
        //mRollo.mReadback.read();
        return mRollo.mReadback.zoom > 0.001f;
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
        Log.d(TAG, "setApps mRollo=" + mRollo + " list=" + list);
        mLocks &= ~LOCK_ICONS_PENDING;
    }

    private void invokeIcon(int index) {
        Log.d(TAG, "launch it!!!! index=" + index);
    }

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

        private int mWidth;
        private int mHeight;

        private Resources mRes;
        private Script mScript;

        private Script.Invokable mInvokeMove;
        private Script.Invokable mInvokeFling;
        private Script.Invokable mInvokeSetZoomTarget;
        private Script.Invokable mInvokeSetZoom;
        private Script.Invokable mInvokeTouchUp;

        private ProgramStore mPSIcons;
        private ProgramStore mPSText;
        private ProgramFragment mPFColor;
        private ProgramFragment mPFTexLinear;
        private ProgramFragment mPFTexNearest;
        private ProgramVertex mPV;
        private ProgramVertex mPVOrtho;

        private Allocation mHomeButton;

        private Allocation[] mIcons;
        private int[] mIconIds;
        private Allocation mAllocIconID;

        private Allocation[] mLabels;
        private int[] mLabelIds;
        private Allocation mAllocLabelID;
        private Allocation mSelectedIcon;

        private int[] mTouchYBorders;
        private Allocation mAllocTouchYBorders;
        private int[] mTouchXBorders;
        private Allocation mAllocTouchXBorders;

        private Bitmap mSelectionBitmap;
        private Canvas mSelectionCanvas;

        Params mParams;
        State mState;
        Readback mReadback;

        class BaseAlloc {
            Allocation mAlloc;
            Type mType;

            void save() {
                mAlloc.data(this);
            }
        }

        private boolean checkClickOK() {
            //android.util.Log.e("rs", "check click " + Float.toString(mReadback.velocity) + ", " + Float.toString(mReadback.posX));
            return (Math.abs(mReadback.velocity) < 0.1f) &&
                   (Math.abs(mReadback.posX - Math.round(mReadback.posX)) < 0.1f);
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
            public float zoom;

            State() {
                mType = Type.createFromClass(mRS, State.class, 1, "StateClass");
                mAlloc = Allocation.createTyped(mRS, mType);
                save();
            }
        }

        class Readback extends BaseAlloc {
            public float posX;
            public float velocity;
            public float zoom;

            Readback() {
                mType = Type.createFromClass(mRS, Readback.class, 1, "ReadbackClass");
                mAlloc = Allocation.createTyped(mRS, mType);
                save();
            }

            void read() {
                if(mAlloc != null) {
                    mAlloc.read(this);
                }
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
            initGl();
            initData();
            initTouchState();
            initRs();
        }

        private void initProgramVertex() {
            ProgramVertex.MatrixAllocation pva = new ProgramVertex.MatrixAllocation(mRS);
            pva.setupProjectionNormalized(mWidth, mHeight);

            ProgramVertex.Builder pvb = new ProgramVertex.Builder(mRS, null, null);
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
            mReadback = new Readback();

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
            mReadback.save();

            mSelectionBitmap = Bitmap.createBitmap(Defines.ICON_TEXTURE_WIDTH_PX,
                    Defines.ICON_TEXTURE_HEIGHT_PX, Bitmap.Config.ARGB_8888);
            mSelectionCanvas = new Canvas(mSelectionBitmap);

            Log.d(TAG, "initData calling mRollo.setApps");
            setApps(null);
        }

        private void initRs() {
            ScriptC.Builder sb = new ScriptC.Builder(mRS);
            sb.setScript(mRes, R.raw.rollo);
            sb.setRoot(true);
            sb.addDefines(mDefines);
            sb.setType(mParams.mType, "params", Defines.ALLOC_PARAMS);
            sb.setType(mState.mType, "state", Defines.ALLOC_STATE);
            sb.setType(mReadback.mType, "readback", Defines.ALLOC_READBACK);
            mInvokeMove = sb.addInvokable("move");
            mInvokeFling = sb.addInvokable("fling");
            mInvokeSetZoomTarget = sb.addInvokable("setZoomTarget");
            mInvokeSetZoom = sb.addInvokable("setZoom");
            mInvokeTouchUp = sb.addInvokable("touchUp");
            mScript = sb.create();
            mScript.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);

            mScript.bindAllocation(mParams.mAlloc, Defines.ALLOC_PARAMS);
            mScript.bindAllocation(mState.mAlloc, Defines.ALLOC_STATE);
            mScript.bindAllocation(mReadback.mAlloc, Defines.ALLOC_READBACK);
            mScript.bindAllocation(mAllocIconID, Defines.ALLOC_ICON_IDS);
            mScript.bindAllocation(mAllocLabelID, Defines.ALLOC_LABEL_IDS);
            mScript.bindAllocation(mAllocTouchXBorders, Defines.ALLOC_X_BORDERS);
            mScript.bindAllocation(mAllocTouchYBorders, Defines.ALLOC_Y_BORDERS);

            mRS.contextBindRootScript(mScript);
        }

        private void setApps(ArrayList<ApplicationInfo> list) {
            final int count = list != null ? list.size() : 0;
            int allocCount = count;
            if(allocCount < 1) {
                allocCount = 1;
            }

            mIcons = new Allocation[count];
            mIconIds = new int[allocCount];
            mAllocIconID = Allocation.createSized(mRS, Element.USER_I32(mRS), allocCount);

            mLabels = new Allocation[count];
            mLabelIds = new int[allocCount];
            mAllocLabelID = Allocation.createSized(mRS, Element.USER_I32(mRS), allocCount);

            Element ie8888 = Element.RGBA_8888(mRS);

            Utilities.BubbleText bubble = new Utilities.BubbleText(getContext());

            for (int i=0; i<count; i++) {
                final ApplicationInfo item = list.get(i);

                mIcons[i] = Allocation.createFromBitmap(mRS, item.iconBitmap,
                        Element.RGBA_8888(mRS), false);
                mLabels[i] = Allocation.createFromBitmap(mRS, item.titleBitmap,
                        Element.RGBA_8888(mRS), false);

                mIcons[i].uploadToTexture(0);
                mLabels[i].uploadToTexture(0);

                mIconIds[i] = mIcons[i].getID();
                mLabelIds[i] = mLabels[i].getID();
            }

            mAllocIconID.data(mIconIds);
            mAllocLabelID.data(mLabelIds);

            mState.iconCount = count;

            if (mScript != null) { // wtf
                mScript.bindAllocation(mAllocIconID, Defines.ALLOC_ICON_IDS);
                mScript.bindAllocation(mAllocLabelID, Defines.ALLOC_LABEL_IDS);
            }

            mState.save();
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

        int chooseTappedIcon(int x, int y, float page) {
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

        /**
         * You need to call save() on mState on your own after calling this.
         */
        void selectIcon(int x, int y, float pos) {
            int index = chooseTappedIcon(x, y, pos);
            selectIcon(index);
        }

        void selectIcon(int index) {
            Log.d(TAG, "selectIcon index=" + index);
            int iconCount = mAllAppsList.size();
            if (index < 0 || index >= iconCount) {
                mState.selectedIconIndex = -1;
                return;
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


