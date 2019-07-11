/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.folder;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.FloatArrayEvaluator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.util.Xml;
import android.view.ViewOutlineProvider;
import ch.deletescape.lawnchair.adaptive.IconShape;
import ch.deletescape.lawnchair.adaptive.IconShapeManager;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider;
import com.android.launcher3.folder.Folder;
import java.io.IOException;
import java.util.ArrayList;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public abstract class FolderShape {
    private static final String TAG = "FolderShape";

    public static FolderShape sInstance = new Circle();
    public SparseArray<TypedValue> mAttrs;

    public static abstract class PathShape extends FolderShape {
        public final Path mTmpPath = new Path();

        public final Animator createRevealAnimator(final Folder folder, Rect startRect, Rect endRect, float endRadius, boolean isReversed) {
            Path path = new Path();
            AnimatorUpdateListener newUpdateListener = newUpdateListener(startRect, endRect,
                    endRadius, path);
            ValueAnimator ofFloat = ValueAnimator.ofFloat(
                    isReversed ? new float[]{1, 0} : new float[]{0, 1});
            ofFloat.addListener(new AnimatorListenerAdapter() {
                public ViewOutlineProvider mOldOutlineProvider;

                public void onAnimationEnd(Animator animator) {
                    folder.setTranslationZ(0);
                    folder.setClipPath(null);
                    folder.setOutlineProvider(this.mOldOutlineProvider);
                }

                public void onAnimationStart(Animator animator) {
                    this.mOldOutlineProvider = folder.getOutlineProvider();
                    folder.setOutlineProvider(null);
                    folder.setTranslationZ(-folder.getElevation());
                }
            });
            ofFloat.addUpdateListener(animation -> {
                path.reset();
                newUpdateListener.onAnimationUpdate(animation);
                folder.setClipPath(path);
            });
            return ofFloat;
        }

        public final void drawShape(Canvas canvas, float x, float y, float radius, Paint paint) {
            this.mTmpPath.reset();
            addShape(this.mTmpPath, x, y, radius);
            canvas.drawPath(this.mTmpPath, paint);
        }

        public abstract AnimatorUpdateListener newUpdateListener(Rect startRect, Rect endRect, float endRadius, Path path);
    }

    public static class AdaptiveIconShape extends PathShape {

        private final IconShape mIconShape;

        public AdaptiveIconShape(Context context) {
            mIconShape = IconShapeManager.Companion.getInstance(context).getIconShape();
            mAttrs = new SparseArray<>();
        }

        @Override
        public void addShape(Path path, float x, float y, float radius) {
            float size = radius * 2;
            mIconShape.addToPath(path, x, y, x + size, y + size, radius);
        }

        @Override
        public AnimatorUpdateListener newUpdateListener(Rect startRect, Rect endRect,
                float endRadius, Path path) {
            float startRadius = startRect.width() / 2f;
            float[] start = new float[] {startRect.left, startRect.top, startRect.right, startRect.bottom};
            float[] end = new float[] {endRect.left, endRect.top, endRect.right, endRect.bottom};
            FloatArrayEvaluator evaluator = new FloatArrayEvaluator();
            return animation -> {
                float progress = (float) animation.getAnimatedValue();
                float[] values = evaluator.evaluate(progress, start, end);
                mIconShape.addToPath(path,
                        values[0], values[1], values[2], values[3],
                        startRadius, endRadius, progress);
            };
        }
    }

    public static abstract class SimpleRectShape extends FolderShape {

        public final Animator createRevealAnimator(Folder folder, Rect startRect, Rect endRect, float endRadius, boolean isReversed) {
            return new RoundedRectRevealOutlineProvider(getStartRadius(startRect), endRadius, startRect,
                    endRect) {
                public boolean shouldRemoveElevationDuringAnimation() {
                    return true;
                }
            }.createRevealAnimator(folder, isReversed);
        }

        public abstract float getStartRadius(Rect rect);
    }

    public static final class Circle extends SimpleRectShape {

        public void addShape(Path path, float x, float y, float radius) {
            path.addCircle(x + radius, y + radius, radius, Direction.CW);
        }

        public void drawShape(Canvas canvas, float x, float y, float radius, Paint paint) {
            canvas.drawCircle(x + radius, y + radius, radius, paint);
        }

        public float getStartRadius(Rect rect) {
            return ((float) rect.width()) / 2.0f;
        }
    }

    public static FolderShape getShapeDefinition(String shape, float radiusRatio) {
        switch (shape) {
            case "Circle":
                return new Circle();
            default:
                throw new IllegalArgumentException("Invalid shape type: " + shape);
        }
    }

    public static void init(Context context) {
        if (Utilities.ATLEAST_OREO) {
            sInstance = new AdaptiveIconShape(context);
            return;
        }

        ArrayList<FolderShape> shapes = new ArrayList<>();
        try {
            XmlPullParser parser = context.getResources().getXml(R.xml.folder_shapes);
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    if ("shapes".equals(name)) continue;

                    AttributeSet attrs = Xml.asAttributeSet(parser);
                    TypedArray ta = context.obtainStyledAttributes(attrs, new int[] { R.attr.folderIconRadius });
                    float radius = ta.getFloat(0, 1);
                    ta.recycle();

                    FolderShape shape = getShapeDefinition(name, radius);

                    int[] indices = new int[attrs.getAttributeCount()];
                    for (int i = 0; i < attrs.getAttributeCount(); i++) {
                        indices[i] = attrs.getAttributeNameResource(i);
                    }
                    ta = context.obtainStyledAttributes(attrs, indices);
                    shape.mAttrs = new SparseArray<>();
                    for (int i = 0; i < indices.length; i++) {
                        TypedValue value = new TypedValue();
                        ta.getValue(i, value);
                        shape.mAttrs.append(indices[i], value);
                    }
                    ta.recycle();

                    shapes.add(shape);
                }
            }
        } catch (XmlPullParserException | IOException e) {
            Log.d(TAG, "error parsing xml", e);
        }

        sInstance = shapes.get(0);
    }

    public abstract void addShape(Path path, float x, float y, float radius);

    public abstract Animator createRevealAnimator(Folder folder, Rect startRect, Rect endRect, float endRadius, boolean isReversed);

    public abstract void drawShape(Canvas canvas, float x, float y, float radius, Paint paint);
}
