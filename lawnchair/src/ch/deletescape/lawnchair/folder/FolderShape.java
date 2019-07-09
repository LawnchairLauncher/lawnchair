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
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Region.Op;
import android.graphics.RegionIterator;
import android.graphics.drawable.ColorDrawable;
import android.os.Build.VERSION_CODES;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.util.Xml;
import android.view.ViewOutlineProvider;
import ch.deletescape.lawnchair.iconpack.AdaptiveIconCompat;
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

        public final Animator createRevealAnimator(final Folder folder, Rect rect, Rect rect2, float f, boolean z) {
            Path path = new Path();
            AnimatorUpdateListener newUpdateListener = newUpdateListener(rect, rect2, f, path);
            float[] fArr = new float[2];
            ValueAnimator ofFloat = ValueAnimator.ofFloat(z ? new float[]{1, 0} : new float[]{0, 1});
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

        public final void drawShape(Canvas canvas, float f, float f2, float f3, Paint paint) {
            this.mTmpPath.reset();
            addShape(this.mTmpPath, f, f2, f3);
            canvas.drawPath(this.mTmpPath, paint);
        }

        public abstract AnimatorUpdateListener newUpdateListener(Rect rect, Rect rect2, float f, Path path);
    }

    public static abstract class SimpleRectShape extends FolderShape {

        public final Animator createRevealAnimator(Folder folder, Rect rect, Rect rect2, float f, boolean z) {
            return new RoundedRectRevealOutlineProvider(getStartRadius(rect), f, rect, rect2) {
                public boolean shouldRemoveElevationDuringAnimation() {
                    return true;
                }
            }.createRevealAnimator(folder, z);
        }

        public abstract float getStartRadius(Rect rect);
    }

    public static final class Circle extends SimpleRectShape {

        public void addShape(Path path, float f, float f2, float f3) {
            path.addCircle(f + f3, f2 + f3, f3, Direction.CW);
        }

        public void drawShape(Canvas canvas, float f, float f2, float f3, Paint paint) {
            canvas.drawCircle(f + f3, f2 + f3, f3, paint);
        }

        public float getStartRadius(Rect rect) {
            return ((float) rect.width()) / 2.0f;
        }
    }

    public static class RoundedSquare extends SimpleRectShape {
        public final float mRadiusRatio;

        public RoundedSquare(float f) {
            this.mRadiusRatio = f;
        }

        public void addShape(Path path, float f, float f2, float f3) {
            f += f3;
            f2 += f3;
            float f4 = f3 * this.mRadiusRatio;
            path.addRoundRect(f - f3, f2 - f3, f + f3, f2 + f3, f4, f4, Direction.CW);
        }

        public void drawShape(Canvas canvas, float f, float f2, float f3, Paint paint) {
            f += f3;
            f2 += f3;
            float f4 = f3 * this.mRadiusRatio;
            canvas.drawRoundRect(f - f3, f2 - f3, f + f3, f2 + f3, f4, f4, paint);
        }

        public float getStartRadius(Rect rect) {
            return (((float) rect.width()) / 2.0f) * this.mRadiusRatio;
        }
    }

    public static class Squircle extends PathShape {
        public final float mRadiusRatio;

        public Squircle(float f) {
            mRadiusRatio = f;
        }

        public void apply(float f, float f2, float f3, float f4, float f5, float f6, float f7, float f8, float f9, float f10, float f11, float f12, Path path, ValueAnimator valueAnimator) {
            float floatValue = (float) valueAnimator.getAnimatedValue();
            float f13 = 1.0f - floatValue;
            float f14 = (floatValue * f2) + (f13 * f);
            float f15 = (floatValue * f4) + (f13 * f3);
            float f16 = (f13 * f5) + (floatValue * f6);
            float f17 = (f13 * f7) + (floatValue * f8);
            float f18 = (floatValue * f10) + (f13 * f9);
            floatValue = (floatValue * f12) + (f13 * f11);
            f13 = f15 - floatValue;
            path.moveTo(f14, f13 - f16);
            path.rLineTo(-f18, 0f);
            f2 = f14 - f18;
            f4 = f16;
            f5 = f17;
            addLeftCurve(f2, f13, f4, f5, path);
            path.rLineTo(0f, floatValue + floatValue);
            f3 = f15 + floatValue;
            addRightCurve(f2, f3, f4, f5, path);
            path.rLineTo(f18 + f18, 0f);
            f14 += f18;
            f16 = -f16;
            f17 = -f17;
            addLeftCurve(f14, f3, f16, f17, path);
            path.rLineTo(0f, (-floatValue) - floatValue);
            addRightCurve(f14, f13, f16, f17, path);
            path.close();
        }

        public final void addLeftCurve(float f, float f2, float f3, float f4, Path path) {
            float f5 = f - f3;
            path.cubicTo(f - f4, f2 - f3, f5, f2 - f4, f5, f2);
        }

        public final void addRightCurve(float f, float f2, float f3, float f4, Path path) {
            float f5 = f2 + f3;
            path.cubicTo(f - f3, f2 + f4, f - f4, f5, f, f5);
        }

        public void addShape(Path path, float f, float f2, float f3) {
            f += f3;
            f2 += f3;
            float f4 = f3 - (this.mRadiusRatio * f3);
            path.moveTo(f, f2 - f3);
            float f5 = f;
            float f6 = f2;
            float f7 = f3;
            float f8 = f4;
            addLeftCurve(f5, f6, f7, f8, path);
            addRightCurve(f5, f6, f7, f8, path);
            f7 = -f3;
            f8 = -f4;
            addLeftCurve(f5, f6, f7, f8, path);
            addRightCurve(f5, f6, f7, f8, path);
            path.close();
        }

        public AnimatorUpdateListener newUpdateListener(Rect rect, Rect rect2, float f, Path path) {
            float exactCenterX = rect.exactCenterX();
            float exactCenterY = rect.exactCenterY();
            float width = ((float) rect.width()) / 2.0f;
            float f2 = width - (this.mRadiusRatio * width);
            float exactCenterX2 = rect2.exactCenterX();
            return animation -> apply(exactCenterX, exactCenterX2, exactCenterY, rect2.exactCenterY(), width, f, f2, f * 0.55191505f, 0, rect2.width() / 2f - f, 0, rect2.height() / 2f - f, path, animation);
        }
    }

    public static class TearDrop extends PathShape {
        public final float mRadiusRatio;
        public final float[] mTempRadii = new float[8];

        public TearDrop(float f) {
            mRadiusRatio = f;
        }

        public /* synthetic */ void a(TearDrop tearDrop, FloatArrayEvaluator floatArrayEvaluator, float[] fArr, float[] fArr2, Path path, ValueAnimator valueAnimator) {
            float[] evaluate = floatArrayEvaluator.evaluate((Float) valueAnimator.getAnimatedValue(), fArr, fArr2);
            path.addRoundRect(evaluate[0], evaluate[1], evaluate[2], evaluate[3], tearDrop.getRadiiArray(evaluate[4], evaluate[5]), Direction.CW);
        }

        public void addShape(Path path, float f, float f2, float f3) {
            f += f3;
            f2 += f3;
            path.addRoundRect(f - f3, f2 - f3, f + f3, f2 + f3, getRadiiArray(f3, this.mRadiusRatio * f3), Direction.CW);
        }

        public final float[] getRadiiArray(float f, float f2) {
            float[] fArr = this.mTempRadii;
            fArr[7] = f;
            fArr[6] = f;
            fArr[3] = f;
            fArr[2] = f;
            fArr[1] = f;
            fArr[0] = f;
            fArr[5] = f2;
            fArr[4] = f2;
            return fArr;
        }

        public AnimatorUpdateListener newUpdateListener(Rect rect, Rect rect2, float f, Path path) {
            float width = this.mRadiusRatio * (((float) rect.width()) / 2.0f);
            FloatArrayEvaluator floatArrayEvaluator = new FloatArrayEvaluator(new float[6]);
            float[] fArr = new float[]{(float) rect.left, (float) rect.top, (float) rect.right, (float) rect.bottom, ((float) rect.width()) / 2.0f, width};
            float[] fArr2 = new float[]{(float) rect2.left, (float) rect2.top, (float) rect2.right, (float) rect2.bottom, f, f};
            return animation -> {
                float[] evaluate = floatArrayEvaluator.evaluate((float) animation.getAnimatedValue(), fArr, fArr2);
                path.addRoundRect(evaluate[0], evaluate[1], evaluate[2], evaluate[3], getRadiiArray(evaluate[4], evaluate[5]), Direction.CW);
            };
        }
    }

    public static FolderShape getShapeDefinition(String shape, float radiusRatio) {
        switch (shape) {
            case "Circle":
                return new Circle();
            case "RoundedSquare":
                return new RoundedSquare(radiusRatio);
            case "Squircle":
                return new Squircle(radiusRatio);
            case "TearDrop":
                return new TearDrop(radiusRatio);
            default:
                throw new IllegalArgumentException("Invalid shape type: " + shape);
        }
    }

    public static void init(Context context) {
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

        if (!Utilities.ATLEAST_OREO) {
            sInstance = shapes.get(0);
            return;
        }

        Region v1 = new Region(0, 0, 200, 200);
        Region v4 = new Region();
        v4.setPath(getIconMask(context), v1);
        Path v5 = new Path();
        Region v6 = new Region();
        Rect v7 = new Rect();

        int smallest = Integer.MAX_VALUE;
        for (FolderShape shape : shapes) {
            v5.reset();
            shape.addShape(v5, 0, 0, 100);
            v6.setPath(v5, v1);
            v6.op(v4, Op.XOR);
            RegionIterator iterator = new RegionIterator(v6);
            int v12 = 0;
            while (iterator.next(v7)) {
                v12 += v7.width() * v7.height();
            }
            if (v12 < smallest) {
                sInstance = shape;
                smallest = v12;
            }
        }
    }

    @SuppressLint("RestrictedApi")
    @RequiresApi(api = VERSION_CODES.O)
    private static Path getIconMask(Context context) {
        AdaptiveIconCompat tmp = new AdaptiveIconCompat(new ColorDrawable(Color.BLACK), new ColorDrawable(Color.BLACK));
        tmp.setBounds(0, 0, 200, 200);
        return tmp.getIconMask();
    }

    public abstract void addShape(Path path, float f, float f2, float f3);

    public abstract Animator createRevealAnimator(Folder folder, Rect rect, Rect rect2, float f, boolean z);

    public abstract void drawShape(Canvas canvas, float f, float f2, float f3, Paint paint);
}
