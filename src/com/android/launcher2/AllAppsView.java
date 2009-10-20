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

    private static final int TRACKING_NONE = 0;
    private static final int TRACKING_FLING = 1;
    private static final int TRACKING_HOME = 2;

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

    static class Defines {
        public static final int ALLOC_PARAMS = 0;
        public static final int ALLOC_STATE = 1;
        public static final int ALLOC_ICON_IDS = 3;
        public static final int ALLOC_LABEL_IDS = 4;

        public static final int COLUMNS_PER_PAGE = 4;
        public static final int ROWS_PER_PAGE = 4;

        public static final int ICON_WIDTH_PX = 64;
        public static final int ICON_TEXTURE_WIDTH_PX = 128;

        public static final int ICON_HEIGHT_PX = 64;
        public static final int ICON_TEXTURE_HEIGHT_PX = 128;

        public int SCREEN_WIDTH_PX;
        public int SCREEN_HEIGHT_PX;

        public void recompute(int w, int h) {
            SCREEN_WIDTH_PX = 480;
            SCREEN_HEIGHT_PX = 800;
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
            if (y > mRollo.mTouchYBorders[mRollo.mTouchYBorders.length-1]) {
                mTouchTracking = TRACKING_HOME;
                mRollo.setHomeSelected(true);
                mRollo.mState.save();
            } else {
                mTouchTracking = TRACKING_FLING;

                mMotionDownRawX = (int)ev.getRawX();
                mMotionDownRawY = (int)ev.getRawY();

                mRollo.mState.newPositionX = ev.getRawY() / getWidth();
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
                mRollo.setHomeSelected(y > mRollo.mTouchYBorders[mRollo.mTouchYBorders.length-1]);
                mRollo.mState.save();
            } else if (mTouchTracking == TRACKING_FLING) {
                int rawX = (int)ev.getRawX();
                int rawY = (int)ev.getRawY();
                int slop;
                slop = Math.abs(rawY - mMotionDownRawY);

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
                    mRollo.mState.newPositionX = ev.getRawY() / getWidth();
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
                    mRollo.setHomeSelected(false);
                    mRollo.mState.save();
                }
            } else if (mTouchTracking == TRACKING_FLING) {
                mRollo.mState.newTouchDown = 0;
                mRollo.mState.newPositionX = ev.getRawY() / getWidth();

                mVelocity.computeCurrentVelocity(1000 /* px/sec */, mMaxFlingVelocity);
                mRollo.mState.flingVelocity = mVelocity.getYVelocity() / getHeight();
                mRollo.clearSelectedIcon();
                mRollo.mState.save();
                mRollo.fling();

                if (mVelocity != null) {
                    mVelocity.recycle();
                    mVelocity = null;
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
        mRollo.setHomeSelected(false);
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

    /*
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
    */

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
        private int mWidth;
        private int mHeight;

        private Resources mRes;
        private Script mScript;
        private Script.Invokable mInvokeMove;
        private Script.Invokable mInvokeMoveTo;
        private Script.Invokable mInvokeFling;
        private Script.Invokable mInvokeResetWAR;


        private ProgramStore mPSIcons;
        private ProgramStore mPSText;
        private ProgramFragment mPFColor;
        private ProgramFragment mPFTexLinear;
        private ProgramVertex mPV;
        private ProgramVertex mPVOrtho;
        private SimpleMesh mMesh;
        private SimpleMesh mMesh2;

        private Allocation mHomeButtonNormal;
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
            mDefines.recompute(width, height);
            initProgramVertex();
            initProgramFragment();
            initProgramStore();
            initMesh();
            initGl();
            initData();
            initTouchState();
            initRs();
        }

        public void initMesh() {
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
            mMesh2.setName("SMMesh");
        }

        private void initProgramVertex() {
            ProgramVertex.MatrixAllocation pva = new ProgramVertex.MatrixAllocation(mRS);
            pva.setupProjectionNormalized(mWidth, mHeight);

            ProgramVertex.Builder pvb = new ProgramVertex.Builder(mRS, null, null);
            pvb.setTextureMatrixEnable(true);
            mPV = pvb.create();
            mPV.setName("PV");
            mPV.bindAllocation(pva);

            //pva = new ProgramVertex.MatrixAllocation(mRS);
            //pva.setupOrthoWindow(mWidth, mHeight);
            //pvb.setTextureMatrixEnable(true);
            //mPVOrtho = pvb.create();
            //mPVOrtho.setName("PVOrtho");
            //mPVOrtho.bindAllocation(pva);

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
            //mPFColor = bf.create();
            //mPFColor.setName("PFColor");

            bf.setTexEnable(true, 0);
            bf.setTexEnvMode(ProgramFragment.EnvMode.MODULATE, 0);
            mPFTexLinear = bf.create();
            mPFTexLinear.setName("PFTexLinear");
            mPFTexLinear.bindSampler(linear, 0);
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

            mSelectionBitmap = Bitmap.createBitmap(Defines.ICON_TEXTURE_WIDTH_PX,
                    Defines.ICON_TEXTURE_HEIGHT_PX, Bitmap.Config.ARGB_8888);
            mSelectionCanvas = new Canvas(mSelectionBitmap);

            setApps(null);
        }

        private void initScript(int id) {
        }

        private void initRs() {
            ScriptC.Builder sb = new ScriptC.Builder(mRS);
            sb.setScript(mRes, R.raw.rollo3);
            sb.setRoot(true);
            sb.addDefines(mDefines);
            sb.setType(mParams.mType, "params", Defines.ALLOC_PARAMS);
            sb.setType(mState.mType, "state", Defines.ALLOC_STATE);
            mInvokeMove = sb.addInvokable("move");
            mInvokeFling = sb.addInvokable("fling");
            mInvokeMoveTo = sb.addInvokable("moveTo");
            mInvokeResetWAR = sb.addInvokable("resetHWWar");
            mScript = sb.create();
            mScript.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            mScript.bindAllocation(mParams.mAlloc, Defines.ALLOC_PARAMS);
            mScript.bindAllocation(mState.mAlloc, Defines.ALLOC_STATE);
            mScript.bindAllocation(mAllocIconIds, Defines.ALLOC_ICON_IDS);
            mScript.bindAllocation(mAllocLabelIds, Defines.ALLOC_LABEL_IDS);

            mMessageProc = new AAMessage();
            mRS.mMessageCallback = mMessageProc;
            mRS.contextBindRootScript(mScript);
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

            if (mScript != null) { // this happens when we init it
                mScript.bindAllocation(mAllocIconIds, Defines.ALLOC_ICON_IDS);
                mScript.bindAllocation(mAllocLabelIds, Defines.ALLOC_LABEL_IDS);
            }

            mState.save();

            // Note: mScript may be null if we haven't initialized it yet.
            // In that case, this is a no-op.
            if (mInvokeResetWAR != null) {
                mInvokeResetWAR.execute();
            }
            mRS.contextBindRootScript(mScript);
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

            return (((int)pos) * Defines.COLUMNS_PER_PAGE)
                    + (row * Defines.ROWS_PER_PAGE) + col;
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

        void setHomeSelected(boolean pressed) {
            if (pressed) {
                mState.homeButtonId = mHomeButtonPressed.getID();
            } else {
                mState.homeButtonId = mHomeButtonNormal.getID();
            }
        }
    }
}


