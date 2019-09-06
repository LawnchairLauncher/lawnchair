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

import static com.android.launcher3.graphics.IconNormalizer.ICON_VISIBLE_AREA_FACTOR;

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
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.TypedValue;
import android.util.Xml;
import android.view.View;
import android.view.ViewOutlineProvider;

import ch.deletescape.lawnchair.adaptive.IconShapeManager;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ClipPathView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;

/**
 * Abstract representation of the shape of an icon shape
 */
public abstract class IconShape {

    private static IconShape sInstance = new Circle();
    private static Path sShapePath;
    private static float sNormalizationScale = ICON_VISIBLE_AREA_FACTOR;

    public static final int DEFAULT_PATH_SIZE = 100;

    public static IconShape getShape() {
        return sInstance;
    }

    public static Path getShapePath() {
        if (sShapePath == null) {
            Path p = new Path();
            getShape().addToPath(p, 0, 0, DEFAULT_PATH_SIZE * 0.5f);
            sShapePath = p;
        }
        return sShapePath;
    }

    public static float getNormalizationScale() {
        return sNormalizationScale;
    }

    protected SparseArray<TypedValue> mAttrs;

    public boolean enableShapeDetection(){
        return false;
    };

    public abstract void drawShape(Canvas canvas, float offsetX, float offsetY, float radius,
            Paint paint);

    public abstract void addToPath(Path path, float offsetX, float offsetY, float radius);

    public abstract <T extends View & ClipPathView> Animator createRevealAnimator(T target,
            Rect startRect, Rect endRect, float endRadius, boolean isReversed);

    @Nullable
    public TypedValue getAttrValue(int attr) {
        return mAttrs == null ? null : mAttrs.get(attr);
    }

    /**
     * Abstract shape where the reveal animation is a derivative of a round rect animation
     */
    private static abstract class SimpleRectShape extends IconShape {

        @Override
        public final <T extends View & ClipPathView> Animator createRevealAnimator(T target,
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
    private static abstract class PathShape extends IconShape {

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
        public final <T extends View & ClipPathView> Animator createRevealAnimator(T target,
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

    public static class AdaptiveIconShape extends PathShape {

        private final ch.deletescape.lawnchair.adaptive.IconShape mIconShape;

        public AdaptiveIconShape(Context context) {
            mIconShape = IconShapeManager.Companion.getInstance(context).getIconShape();
            mAttrs = new SparseArray<>();
            int qsbEdgeRadius = mIconShape.getQsbEdgeRadius();
            if (qsbEdgeRadius != 0) {
                TypedValue value = new TypedValue();
                context.getResources().getValue(qsbEdgeRadius, value, false);
                mAttrs.append(R.attr.qsbEdgeRadius, value);
            }
        }

        @Override
        public void addToPath(Path path, float offsetX, float offsetY, float radius) {
            mIconShape.addShape(path, offsetX, offsetY, radius);
        }

        @Override
        public AnimatorUpdateListener newUpdateListener(Rect startRect, Rect endRect,
                float endRadius, Path outPath) {
            float startRadius = startRect.width() / 2f;
            float[] start = new float[] {startRect.left, startRect.top, startRect.right, startRect.bottom};
            float[] end = new float[] {endRect.left, endRect.top, endRect.right, endRect.bottom};
            FloatArrayEvaluator evaluator = new FloatArrayEvaluator();
            return animation -> {
                float progress = (float) animation.getAnimatedValue();
                float[] values = evaluator.evaluate(progress, start, end);
                mIconShape.addToPath(outPath,
                        values[0], values[1], values[2], values[3],
                        startRadius, endRadius, progress);
            };
        }
    }

    public static final class Circle extends SimpleRectShape {

        @Override
        public void drawShape(Canvas canvas, float offsetX, float offsetY, float radius, Paint p) {
            canvas.drawCircle(radius + offsetX, radius + offsetY, radius, p);
        }

        @Override
        public void addToPath(Path path, float offsetX, float offsetY, float radius) {
            path.addCircle(radius + offsetX, radius + offsetY, radius, Path.Direction.CW);
        }

        @Override
        protected float getStartRadius(Rect startRect) {
            return startRect.width() / 2f;
        }

        @Override
        public boolean enableShapeDetection() {
            return true;
        }
    }

    /**
     * Initializes the shape which is closest to the {@link AdaptiveIconDrawable}
     */
    public static void init(Context context) {
        if (Utilities.ATLEAST_OREO) {
            sInstance = new AdaptiveIconShape(context);

            AdaptiveIconDrawable drawable = new AdaptiveIconDrawable(
                    new ColorDrawable(Color.BLACK), new ColorDrawable(Color.BLACK));
            // Initialize shape properties
            drawable.setBounds(0, 0, DEFAULT_PATH_SIZE, DEFAULT_PATH_SIZE);
            sShapePath = new Path(drawable.getIconMask());
//            sNormalizationScale = IconNormalizer.normalizeAdaptiveIcon(drawable, size, null);
            return;
        }
        sInstance = getAllShapes(context).get(0);
    }

    private static IconShape getShapeDefinition(String type, float radius) {
        switch (type) {
            case "Circle":
                return new Circle();
            default:
                throw new IllegalArgumentException("Invalid shape type: " + type);
        }
    }

    private static List<IconShape> getAllShapes(Context context) {
        ArrayList<IconShape> result = new ArrayList<>();
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
                    IconShape shape = getShapeDefinition(parser.getName(), a.getFloat(0, 1));
                    a.recycle();

                    shape.mAttrs = Themes.createValueMap(context, attrs);
                    result.add(shape);
                }
            }
        } catch (IOException | XmlPullParserException e) {
            throw new RuntimeException(e);
        }
        return result;
    }
}
