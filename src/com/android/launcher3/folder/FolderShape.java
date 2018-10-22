/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.folder;

import static com.android.launcher3.Workspace.MAP_NO_RECURSE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.FloatArrayEvaluator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.TargetApi;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Region.Op;
import android.graphics.RegionIterator;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.ViewOutlineProvider;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider;

/**
 * Abstract representation of the shape of a folder icon
 */
public abstract class FolderShape {

    private static FolderShape sInstance = new Circle();

    public static FolderShape getShape() {
        return sInstance;
    }

    private static FolderShape[] getAllShapes() {
        return new FolderShape[] {
                new Circle(),
                new RoundedSquare(8f / 50),  // Ratios based on path defined in config_icon_mask
                new RoundedSquare(30f / 50),
                new Square(),
                new TearDrop(),
                new Squircle()};
    }

    public abstract void drawShape(Canvas canvas, float offsetX, float offsetY, float radius,
            Paint paint);

    public abstract void addShape(Path path, float offsetX, float offsetY, float radius);

    public abstract Animator createRevealAnimator(Folder target, Rect startRect, Rect endRect,
            float endRadius, boolean isReversed);

    /**
     * Abstract shape where the reveal animation is a derivative of a round rect animation
     */
    private static abstract class SimpleRectShape extends FolderShape {

        @Override
        public final Animator createRevealAnimator(Folder target, Rect startRect, Rect endRect,
                float endRadius, boolean isReversed) {
            return new RoundedRectRevealOutlineProvider(
                    getStartRadius(startRect), endRadius, startRect, endRect) {
                @Override
                public boolean shouldRemoveElevationDuringAnimation() {
                    return true;
                }
            }.createRevealAnimator(target, isReversed);
        }

        protected abstract float getStartRadius(Rect startRect);
    }

    /**
     * Abstract shape which draws using {@link Path}
     */
    private static abstract class PathShape extends FolderShape {

        private final Path mTmpPath = new Path();

        @Override
        public final void drawShape(Canvas canvas, float offsetX, float offsetY, float radius,
                Paint paint) {
            mTmpPath.reset();
            addShape(mTmpPath, offsetX, offsetY, radius);
            canvas.drawPath(mTmpPath, paint);
        }

        protected abstract AnimatorUpdateListener newUpdateListener(
                Rect startRect, Rect endRect, float endRadius, Path outPath);

        @Override
        public final Animator createRevealAnimator(Folder target, Rect startRect, Rect endRect,
                float endRadius, boolean isReversed) {
            Path path = new Path();
            AnimatorUpdateListener listener =
                    newUpdateListener(startRect, endRect, endRadius, path);

            ValueAnimator va =
                    isReversed ? ValueAnimator.ofFloat(1f, 0f) : ValueAnimator.ofFloat(0f, 1f);
            va.addListener(new AnimatorListenerAdapter() {
                private ViewOutlineProvider mOldOutlineProvider;

                public void onAnimationStart(Animator animation) {
                    mOldOutlineProvider = target.getOutlineProvider();
                    target.setOutlineProvider(null);

                    target.setTranslationZ(-target.getElevation());
                }

                public void onAnimationEnd(Animator animation) {
                    target.setTranslationZ(0);
                    target.setClipPath(null);
                    target.setOutlineProvider(mOldOutlineProvider);
                }
            });

            va.addUpdateListener((anim) -> {
                path.reset();
                listener.onAnimationUpdate(anim);
                target.setClipPath(path);
            });

            return va;
        }
    }

    public static final class Circle extends SimpleRectShape {

        @Override
        public void drawShape(Canvas canvas, float offsetX, float offsetY, float radius, Paint p) {
            canvas.drawCircle(radius + offsetX, radius + offsetY, radius, p);
        }

        @Override
        public void addShape(Path path, float offsetX, float offsetY, float radius) {
            path.addCircle(radius + offsetX, radius + offsetY, radius, Path.Direction.CW);
        }

        @Override
        protected float getStartRadius(Rect startRect) {
            return startRect.width() / 2f;
        }
    }

    public static class Square extends SimpleRectShape {

        @Override
        public void drawShape(Canvas canvas, float offsetX, float offsetY, float radius,  Paint p) {
            float cx = radius + offsetX;
            float cy = radius + offsetY;
            canvas.drawRect(cx - radius, cy - radius, cx + radius, cy + radius, p);
        }

        @Override
        public void addShape(Path path, float offsetX, float offsetY, float radius) {
            float cx = radius + offsetX;
            float cy = radius + offsetY;
            path.addRect(cx - radius, cy - radius, cx + radius, cy + radius, Path.Direction.CW);
        }

        @Override
        protected float getStartRadius(Rect startRect) {
            return 0;
        }
    }

    public static class RoundedSquare extends SimpleRectShape {

        /**
         * Ratio of corner radius to half size. Based on the
         */
        private final float mRadiusFactor;

        public RoundedSquare(float radiusFactor) {
            mRadiusFactor = radiusFactor;
        }

        @Override
        public void drawShape(Canvas canvas, float offsetX, float offsetY, float radius, Paint p) {
            float cx = radius + offsetX;
            float cy = radius + offsetY;
            float cr = radius * mRadiusFactor;
            canvas.drawRoundRect(cx - radius, cy - radius, cx + radius, cy + radius, cr, cr, p);
        }

        @Override
        public void addShape(Path path, float offsetX, float offsetY, float radius) {
            float cx = radius + offsetX;
            float cy = radius + offsetY;
            float cr = radius * mRadiusFactor;
            path.addRoundRect(cx - radius, cy - radius, cx + radius, cy + radius, cr, cr,
                    Path.Direction.CW);
        }

        @Override
        protected float getStartRadius(Rect startRect) {
            return (startRect.width() / 2f) * mRadiusFactor;
        }
    }

    public static class TearDrop extends PathShape {

        /**
         * Radio of short radius to large radius, based on the shape options defined in the config.
         */
        private static final float RADIUS_RATIO = 15f / 50;

        private final float[] mTempRadii = new float[8];

        @Override
        public void addShape(Path p, float offsetX, float offsetY, float r1) {
            float r2 = r1 * RADIUS_RATIO;
            float cx = r1 + offsetX;
            float cy = r1 + offsetY;

            p.addRoundRect(cx - r1, cy - r1, cx + r1, cy + r1, getRadiiArray(r1, r2),
                    Path.Direction.CW);
        }

        private float[] getRadiiArray(float r1, float r2) {
            mTempRadii[0] = mTempRadii [1] = mTempRadii[2] = mTempRadii[3] =
                    mTempRadii[6] = mTempRadii[7] = r1;
            mTempRadii[4] = mTempRadii[5] = r2;
            return mTempRadii;
        }

        @Override
        protected AnimatorUpdateListener newUpdateListener(Rect startRect, Rect endRect,
                float endRadius, Path outPath) {
            float r1 = startRect.width() / 2f;
            float r2 = r1 * RADIUS_RATIO;

            float[] startValues = new float[] {
                    startRect.left, startRect.top, startRect.right, startRect.bottom, r1, r2};
            float[] endValues = new float[] {
                    endRect.left, endRect.top, endRect.right, endRect.bottom, endRadius, endRadius};

            FloatArrayEvaluator evaluator = new FloatArrayEvaluator(new float[6]);

            return (anim) -> {
                float progress = (Float) anim.getAnimatedValue();
                float[] values = evaluator.evaluate(progress, startValues, endValues);
                outPath.addRoundRect(
                        values[0], values[1], values[2], values[3],
                        getRadiiArray(values[4], values[5]), Path.Direction.CW);
            };
        }
    }

    public static class Squircle extends PathShape {

        /**
         * Radio of radius to circle radius, based on the shape options defined in the config.
         */
        private static final float RADIUS_RATIO = 10f / 50;

        @Override
        public void addShape(Path p, float offsetX, float offsetY, float r) {
            float cx = r + offsetX;
            float cy = r + offsetY;
            float control = r - r * RADIUS_RATIO;

            p.moveTo(cx, cy - r);
            addLeftCurve(cx, cy, r, control, p);
            addRightCurve(cx, cy, r, control, p);
            addLeftCurve(cx, cy, -r, -control, p);
            addRightCurve(cx, cy, -r, -control, p);
            p.close();
        }

        private void addLeftCurve(float cx, float cy, float r, float control, Path path) {
            path.cubicTo(
                    cx - control, cy - r,
                    cx - r, cy - control,
                    cx - r, cy);
        }

        private void addRightCurve(float cx, float cy, float r, float control, Path path) {
            path.cubicTo(
                    cx - r, cy + control,
                    cx - control, cy + r,
                    cx, cy + r);
        }

        @Override
        protected AnimatorUpdateListener newUpdateListener(Rect startRect, Rect endRect,
                float endR, Path outPath) {

            float startCX = startRect.exactCenterX();
            float startCY = startRect.exactCenterY();
            float startR = startRect.width() / 2f;
            float startControl = startR - startR * RADIUS_RATIO;
            float startHShift = 0;
            float startVShift = 0;

            float endCX = endRect.exactCenterX();
            float endCY = endRect.exactCenterY();
            // Approximate corner circle using bezier curves
            // http://spencermortensen.com/articles/bezier-circle/
            float endControl = endR * 0.551915024494f;
            float endHShift = endRect.width() / 2f - endR;
            float endVShift = endRect.height() / 2f - endR;

            return (anim) -> {
                float progress = (Float) anim.getAnimatedValue();

                float cx = (1 - progress) * startCX + progress * endCX;
                float cy = (1 - progress) * startCY + progress * endCY;
                float r = (1 - progress) * startR + progress * endR;
                float control = (1 - progress) * startControl + progress * endControl;
                float hShift = (1 - progress) * startHShift + progress * endHShift;
                float vShift = (1 - progress) * startVShift + progress * endVShift;

                outPath.moveTo(cx, cy - vShift - r);
                outPath.rLineTo(-hShift, 0);

                addLeftCurve(cx - hShift, cy - vShift, r, control, outPath);
                outPath.rLineTo(0, vShift + vShift);

                addRightCurve(cx - hShift, cy + vShift, r, control, outPath);
                outPath.rLineTo(hShift + hShift, 0);

                addLeftCurve(cx + hShift, cy + vShift, -r, -control, outPath);
                outPath.rLineTo(0, -vShift - vShift);

                addRightCurve(cx + hShift, cy - vShift, -r, -control, outPath);
                outPath.close();
            };
        }
    }

    /**
     * Initializes the shape which is closest to closest to the {@link AdaptiveIconDrawable}
     */
    public static void init() {
        if (!Utilities.ATLEAST_OREO) {
            return;
        }
        new MainThreadExecutor().execute(FolderShape::pickShapeInBackground);
    }

    @TargetApi(Build.VERSION_CODES.O)
    protected static void pickShapeInBackground() {
        // Pick any large size
        int size = 200;

        Region full = new Region(0, 0, size, size);
        Region iconR = new Region();
        AdaptiveIconDrawable drawable = new AdaptiveIconDrawable(
                new ColorDrawable(Color.BLACK), new ColorDrawable(Color.BLACK));
        drawable.setBounds(0, 0, size, size);
        iconR.setPath(drawable.getIconMask(), full);

        Path shapePath = new Path();
        Region shapeR = new Region();
        Rect tempRect = new Rect();

        // Find the shape with minimum area of divergent region.
        int minArea = Integer.MAX_VALUE;
        FolderShape closestShape = null;
        for (FolderShape shape : getAllShapes()) {
            shapePath.reset();
            shape.addShape(shapePath, 0, 0, size / 2f);
            shapeR.setPath(shapePath, full);
            shapeR.op(iconR, Op.XOR);

            RegionIterator itr = new RegionIterator(shapeR);
            int area = 0;

            while (itr.next(tempRect)) {
                area += tempRect.width() * tempRect.height();
            }
            if (area < minArea) {
                minArea = area;
                closestShape = shape;
            }
        }

        if (closestShape != null) {
            FolderShape shape = closestShape;
            new MainThreadExecutor().execute(() -> updateFolderShape(shape));
        }
    }

    private static void updateFolderShape(FolderShape shape) {
        sInstance = shape;
        LauncherAppState app = LauncherAppState.getInstanceNoCreate();
        if (app == null) {
            return;
        }
        Launcher launcher = (Launcher) app.getModel().getCallback();
        if (launcher != null) {
            launcher.getWorkspace().mapOverItems(MAP_NO_RECURSE, (i, v) -> {
                if (v instanceof FolderIcon) {
                    v.invalidate();
                }
                return false;
            });
        }
    }
}
