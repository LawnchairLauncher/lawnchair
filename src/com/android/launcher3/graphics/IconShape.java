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
package com.android.launcher3.graphics;

import static com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.FloatArrayEvaluator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Region.Op;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.android.launcher3.R;
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider;
import com.android.launcher3.icons.GraphicsUtils;
import com.android.launcher3.icons.IconNormalizer;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.views.ClipPathView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract representation of the shape of an icon shape
 */
public final class IconShape implements SafeCloseable {

    public static final MainThreadInitializedObject<IconShape> INSTANCE =
            new MainThreadInitializedObject<>(IconShape::new);


    private ShapeDelegate mDelegate = new Circle();
    private float mNormalizationScale = ICON_VISIBLE_AREA_FACTOR;

    private IconShape(Context context) {
        pickBestShape(context);
    }

    public ShapeDelegate getShape() {
        return mDelegate;
    }

    public float getNormalizationScale() {
        return mNormalizationScale;
    }

    @Override
    public void close() { }

    /**
     * Initializes the shape which is closest to the {@link AdaptiveIconDrawable}
     */
    public void pickBestShape(Context context) {
        // Pick any large size
        final int size = 200;

        Region full = new Region(0, 0, size, size);
        Region iconR = new Region();
        AdaptiveIconDrawable drawable = new AdaptiveIconDrawable(
                new ColorDrawable(Color.BLACK), new ColorDrawable(Color.BLACK));
        drawable.setBounds(0, 0, size, size);
        iconR.setPath(drawable.getIconMask(), full);

        Path shapePath = new Path();
        Region shapeR = new Region();

        // Find the shape with minimum area of divergent region.
        int minArea = Integer.MAX_VALUE;
        ShapeDelegate closestShape = null;
        for (ShapeDelegate shape : getAllShapes(context)) {
            shapePath.reset();
            shape.addToPath(shapePath, 0, 0, size / 2f);
            shapeR.setPath(shapePath, full);
            shapeR.op(iconR, Op.XOR);

            int area = GraphicsUtils.getArea(shapeR);
            if (area < minArea) {
                minArea = area;
                closestShape = shape;
            }
        }

        if (closestShape != null) {
            mDelegate = closestShape;
        }

        // Initialize shape properties
        mNormalizationScale = IconNormalizer.normalizeAdaptiveIcon(drawable, size, null);
    }



    public interface ShapeDelegate {

        default boolean enableShapeDetection() {
            return false;
        }

        void drawShape(Canvas canvas, float offsetX, float offsetY, float radius, Paint paint);

        void addToPath(Path path, float offsetX, float offsetY, float radius);

        <T extends View & ClipPathView> ValueAnimator createRevealAnimator(T target,
                Rect startRect, Rect endRect, float endRadius, boolean isReversed);
    }

    /**
     * Abstract shape where the reveal animation is a derivative of a round rect animation
     */
    private static abstract class SimpleRectShape implements ShapeDelegate {

        @Override
        public final <T extends View & ClipPathView> ValueAnimator createRevealAnimator(T target,
                Rect startRect, Rect endRect, float endRadius, boolean isReversed) {
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
    private static abstract class PathShape implements ShapeDelegate {

        private final Path mTmpPath = new Path();

        @Override
        public final void drawShape(Canvas canvas, float offsetX, float offsetY, float radius,
                Paint paint) {
            mTmpPath.reset();
            addToPath(mTmpPath, offsetX, offsetY, radius);
            canvas.drawPath(mTmpPath, paint);
        }

        protected abstract AnimatorUpdateListener newUpdateListener(
                Rect startRect, Rect endRect, float endRadius, Path outPath);

        @Override
        public final <T extends View & ClipPathView> ValueAnimator createRevealAnimator(T target,
                Rect startRect, Rect endRect, float endRadius, boolean isReversed) {
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

    public static final class Circle extends PathShape {

        private final float[] mTempRadii = new float[8];

        protected AnimatorUpdateListener newUpdateListener(Rect startRect, Rect endRect,
                float endRadius, Path outPath) {
            float r1 = getStartRadius(startRect);

            float[] startValues = new float[] {
                    startRect.left, startRect.top, startRect.right, startRect.bottom, r1, r1};
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

        private float[] getRadiiArray(float r1, float r2) {
            mTempRadii[0] = mTempRadii [1] = mTempRadii[2] = mTempRadii[3] =
                    mTempRadii[6] = mTempRadii[7] = r1;
            mTempRadii[4] = mTempRadii[5] = r2;
            return mTempRadii;
        }


        @Override
        public void addToPath(Path path, float offsetX, float offsetY, float radius) {
            path.addCircle(radius + offsetX, radius + offsetY, radius, Path.Direction.CW);
        }

        protected float getStartRadius(Rect startRect) {
            return startRect.width() / 2f;
        }

        @Override
        public boolean enableShapeDetection() {
            return true;
        }
    }

    private static class RoundedSquare extends SimpleRectShape {

        /**
         * Ratio of corner radius to half size.
         */
        private final float mRadiusRatio;

        public RoundedSquare(float radiusRatio) {
            mRadiusRatio = radiusRatio;
        }

        @Override
        public void drawShape(Canvas canvas, float offsetX, float offsetY, float radius, Paint p) {
            float cx = radius + offsetX;
            float cy = radius + offsetY;
            float cr = radius * mRadiusRatio;
            canvas.drawRoundRect(cx - radius, cy - radius, cx + radius, cy + radius, cr, cr, p);
        }

        @Override
        public void addToPath(Path path, float offsetX, float offsetY, float radius) {
            float cx = radius + offsetX;
            float cy = radius + offsetY;
            float cr = radius * mRadiusRatio;
            path.addRoundRect(cx - radius, cy - radius, cx + radius, cy + radius, cr, cr,
                    Path.Direction.CW);
        }

        @Override
        protected float getStartRadius(Rect startRect) {
            return (startRect.width() / 2f) * mRadiusRatio;
        }
    }

    private static class TearDrop extends PathShape {

        /**
         * Radio of short radius to large radius, based on the shape options defined in the config.
         */
        private final float mRadiusRatio;
        private final float[] mTempRadii = new float[8];

        public TearDrop(float radiusRatio) {
            mRadiusRatio = radiusRatio;
        }

        @Override
        public void addToPath(Path p, float offsetX, float offsetY, float r1) {
            float r2 = r1 * mRadiusRatio;
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
            float r2 = r1 * mRadiusRatio;

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

    private static class Squircle extends PathShape {

        /**
         * Radio of radius to circle radius, based on the shape options defined in the config.
         */
        private final float mRadiusRatio;

        public Squircle(float radiusRatio) {
            mRadiusRatio = radiusRatio;
        }

        @Override
        public void addToPath(Path p, float offsetX, float offsetY, float r) {
            float cx = r + offsetX;
            float cy = r + offsetY;
            float control = r - r * mRadiusRatio;

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
            float startControl = startR - startR * mRadiusRatio;
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

    private static ShapeDelegate getShapeDefinition(String type, float radius) {
        switch (type) {
            case "Circle":
                return new Circle();
            case "RoundedSquare":
                return new RoundedSquare(radius);
            case "TearDrop":
                return new TearDrop(radius);
            case "Squircle":
                return new Squircle(radius);
            default:
                throw new IllegalArgumentException("Invalid shape type: " + type);
        }
    }

    private static List<ShapeDelegate> getAllShapes(Context context) {
        ArrayList<ShapeDelegate> result = new ArrayList<>();
        try (XmlResourceParser parser = context.getResources().getXml(R.xml.folder_shapes)) {

            // Find the root tag
            int type;
            while ((type = parser.next()) != XmlPullParser.END_TAG
                    && type != XmlPullParser.END_DOCUMENT
                    && !"shapes".equals(parser.getName()));

            final int depth = parser.getDepth();
            int[] radiusAttr = new int[] {R.attr.folderIconRadius};

            while (((type = parser.next()) != XmlPullParser.END_TAG ||
                    parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {

                if (type == XmlPullParser.START_TAG) {
                    AttributeSet attrs = Xml.asAttributeSet(parser);
                    TypedArray a = context.obtainStyledAttributes(attrs, radiusAttr);
                    ShapeDelegate shape = getShapeDefinition(parser.getName(), a.getFloat(0, 1));
                    a.recycle();

                    result.add(shape);
                }
            }
        } catch (IOException | XmlPullParserException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

}
