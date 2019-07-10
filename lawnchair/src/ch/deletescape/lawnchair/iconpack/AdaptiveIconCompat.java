/*
 * Copyright (C) 2017 The Android Open Source Project
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

package ch.deletescape.lawnchair.iconpack;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.graphics.PathParser;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import ch.deletescape.lawnchair.adaptive.IconShapeManager;
import com.android.launcher3.Utilities;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * <p>This class can also be created via XML inflation using <code>&lt;adaptive-icon></code> tag
 * in addition to dynamic creation.
 *
 * <p>This drawable supports two drawable layers: foreground and background. The layers are clipped
 * when rendering using the mask defined in the device configuration.
 *
 * <ul>
 * <li>Both foreground and background layers should be sized at 108 x 108 dp.</li>
 * <li>The inner 72 x 72 dp  of the icon appears within the masked viewport.</li>
 * <li>The outer 18 dp on each of the 4 sides of the layers is reserved for use by the system UI
 * surfaces to create interesting visual effects, such as parallax or pulsing.</li>
 * </ul>
 *
 * Such motion effect is achieved by internally setting the bounds of the foreground and
 * background layer as following:
 * <pre>
 * Rect(getBounds().left - getBounds().getWidth() * #getExtraInsetFraction(),
 *      getBounds().top - getBounds().getHeight() * #getExtraInsetFraction(),
 *      getBounds().right + getBounds().getWidth() * #getExtraInsetFraction(),
 *      getBounds().bottom + getBounds().getHeight() * #getExtraInsetFraction())
 * </pre>
 */
public class AdaptiveIconCompat extends Drawable implements Drawable.Callback {

    public static final String TAG = "AdaptiveIconCompat";

    /**
     * Mask path is defined inside device configuration in following dimension: [100 x 100]
     */
    public static final float MASK_SIZE = 100f;

    /**
     * Launcher icons design guideline
     */
    private static final float SAFEZONE_SCALE = 66f/72f;

    /**
     * All four sides of the layers are padded with extra inset so as to provide
     * extra content to reveal within the clip path when performing affine transformations on the
     * layers.
     *
     * Each layers will reserve 25% of it's width and height.
     *
     * As a result, the view port of the layers is smaller than their intrinsic width and height.
     */
    private static final float EXTRA_INSET_PERCENTAGE = 1 / 4f;
    private static final float DEFAULT_VIEW_PORT_SCALE = 1f / (1 + 2 * EXTRA_INSET_PERCENTAGE);

    /**
     * Clip path defined in R.string.config_icon_mask.
     */
    private static Path sMask;

    /**
     * Scaled mask based on the view bounds.
     */
    private final Path mMask;
    private final Matrix mMaskMatrix;
    private final Region mTransparentRegion;
    private final int mMaskId;

    private Bitmap mMaskBitmap;

    private static final int BACKGROUND_ID = 0;
    private static final int FOREGROUND_ID = 1;

    /**
     * State variable that maintains the {@link ChildDrawable} array.
     */
    LayerState mLayerState;

    private Shader mLayersShader;
    private Bitmap mLayersBitmap;

    private final Rect mTmpOutRect = new Rect();
    private Rect mHotspotBounds;
    private boolean mMutated;

    private boolean mSuspendChildInvalidation;
    private boolean mChildRequestedInvalidation;
    private final Canvas mCanvas;
    private Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG |
            Paint.FILTER_BITMAP_FLAG);

    private static Method methodExtractThemeAttrs;
    private static Method methodCreateFromXmlInnerForDensity;

    static {
        try {
            methodExtractThemeAttrs = TypedArray.class.getDeclaredMethod("extractThemeAttrs");
            methodCreateFromXmlInnerForDensity = Drawable.class.getDeclaredMethod(
                    "createFromXmlInnerForDensity", Resources.class,
                    XmlPullParser.class, AttributeSet.class, int.class, Theme.class);
            methodCreateFromXmlInnerForDensity.setAccessible(true);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    /**
     * Constructor used for xml inflation.
     */
    @RequiresApi(api = VERSION_CODES.O)
    public AdaptiveIconCompat() throws Exception {
        this((LayerState) null, null);
    }

    /**
     * The one constructor to rule them all. This is called by all public
     * constructors to set the state and initialize local properties.
     */
    @RequiresApi(api = VERSION_CODES.O)
    @SuppressLint("RestrictedApi")
    AdaptiveIconCompat(@Nullable LayerState state, @Nullable Resources res) {
        mLayerState = createConstantState(state, res);

        if (sMask == null) {
            sMask = createMaskPath();
        }
        mMask = createMaskPath();
        mMaskMatrix = new Matrix();
        mCanvas = new Canvas();
        mTransparentRegion = new Region();
        mMaskId = sMask.hashCode();
    }

    private int getInt(Field field, Object obj) {
        try {
            return field.getInt(obj);
        } catch (IllegalAccessException e) {
            return 0;
        }
    }

    private <T> T invoke(Method method, Object obj, Object... params) {
        try {
            return (T) method.invoke(obj, params);
        } catch (IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }

    @RequiresApi(api = VERSION_CODES.O)
    @SuppressLint("RestrictedApi")
    private Path createMaskPath() {
        try {
            return IconShapeManager.getInstanceNoCreate().getIconShape().getMaskPath();
        } catch (Exception e) {
            Log.d(TAG, "Can't load icon mask", e);
        }
        return new AdaptiveIconDrawable(null, null).getIconMask();
    }

    private ChildDrawable createChildDrawable(Drawable drawable) {
        final ChildDrawable layer = new ChildDrawable(mLayerState.mDensity);
        layer.mDrawable = drawable;
        layer.mDrawable.setCallback(this);
        mLayerState.mChildrenChangingConfigurations |=
                layer.mDrawable.getChangingConfigurations();
        return layer;
    }

    LayerState createConstantState(@Nullable LayerState state, @Nullable Resources res) {
        return new LayerState(state, this, res);
    }

    /**
     * Constructor used to dynamically create this drawable.
     *
     * @param backgroundDrawable drawable that should be rendered in the background
     * @param foregroundDrawable drawable that should be rendered in the foreground
     */
    public AdaptiveIconCompat(Drawable backgroundDrawable,
            Drawable foregroundDrawable) {
        this((LayerState)null, null);
        if (backgroundDrawable != null) {
            addLayer(BACKGROUND_ID, createChildDrawable(backgroundDrawable));
        }
        if (foregroundDrawable != null) {
            addLayer(FOREGROUND_ID, createChildDrawable(foregroundDrawable));
        }
    }

    /**
     * Sets the layer to the {@param index} and invalidates cache.
     *
     * @param index The index of the layer.
     * @param layer The layer to add.
     */
    private void addLayer(int index, @NonNull ChildDrawable layer) {
        mLayerState.mChildren[index] = layer;
        mLayerState.invalidateCache();
    }

    @Override
    public void inflate(@NonNull Resources r, @NonNull XmlPullParser parser,
            @NonNull AttributeSet attrs, @Nullable Theme theme)
            throws XmlPullParserException, IOException {
        super.inflate(r, parser, attrs, theme);

        final LayerState state = mLayerState;
        if (state == null) {
            return;
        }

        // The density may have changed since the last update. This will
        // apply scaling to any existing constant state properties.
        final int deviceDensity = resolveDensity(r, 0);
        state.setDensity(deviceDensity);

        final ChildDrawable[] array = state.mChildren;
        for (int i = 0; i < state.mChildren.length; i++) {
            final ChildDrawable layer = array[i];
            layer.setDensity(deviceDensity);
        }

        inflateLayers(r, parser, attrs, theme);
    }

    static int resolveDensity(@Nullable Resources r, int parentDensity) {
        final int densityDpi = r == null ? parentDensity : r.getDisplayMetrics().densityDpi;
        return densityDpi == 0 ? DisplayMetrics.DENSITY_DEFAULT : densityDpi;
    }

    /**
     * All four sides of the layers are padded with extra inset so as to provide
     * extra content to reveal within the clip path when performing affine transformations on the
     * layers.
     *
     * @see #getForeground() and #getBackground() for more info on how this value is used
     */
    public static float getExtraInsetFraction() {
        return EXTRA_INSET_PERCENTAGE;
    }

    /**
     * @hide
     */
    public static float getExtraInsetPercentage() {
        return EXTRA_INSET_PERCENTAGE;
    }

    /**
     * When called before the bound is set, the returned path is identical to
     * R.string.config_icon_mask. After the bound is set, the
     * returned path's computed bound is same as the #getBounds().
     *
     * @return the mask path object used to clip the drawable
     */
    public Path getIconMask() {
        return mMask;
    }

    /**
     * Returns the foreground drawable managed by this class. The bound of this drawable is
     * extended by {@link #getExtraInsetFraction()} * getBounds().width on left/right sides and by
     * {@link #getExtraInsetFraction()} * getBounds().height on top/bottom sides.
     *
     * @return the foreground drawable managed by this drawable
     */
    public Drawable getForeground() {
        return mLayerState.mChildren[FOREGROUND_ID].mDrawable;
    }

    /**
     * Returns the foreground drawable managed by this class. The bound of this drawable is
     * extended by {@link #getExtraInsetFraction()} * getBounds().width on left/right sides and by
     * {@link #getExtraInsetFraction()} * getBounds().height on top/bottom sides.
     *
     * @return the background drawable managed by this drawable
     */
    public Drawable getBackground() {
        return mLayerState.mChildren[BACKGROUND_ID].mDrawable;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        if (bounds.isEmpty()) {
            return;
        }
        updateLayerBounds(bounds);
    }

    private void updateLayerBounds(Rect bounds) {
        try {
            suspendChildInvalidation();
            updateLayerBoundsInternal(bounds);
            updateMaskBoundsInternal(bounds);
        } finally {
            resumeChildInvalidation();
        }
    }

    /**
     * Set the child layer bounds bigger than the view port size by {@link #DEFAULT_VIEW_PORT_SCALE}
     */
    private void updateLayerBoundsInternal(Rect bounds) {
        int cX = bounds.width() / 2;
        int cY = bounds.height() / 2;

        for (int i = 0, count = mLayerState.N_CHILDREN; i < count; i++) {
            final ChildDrawable r = mLayerState.mChildren[i];
            if (r == null) {
                continue;
            }
            final Drawable d = r.mDrawable;
            if (d == null) {
                continue;
            }

            int insetWidth = (int) (bounds.width() / (DEFAULT_VIEW_PORT_SCALE * 2));
            int insetHeight = (int) (bounds.height() / (DEFAULT_VIEW_PORT_SCALE * 2));
            final Rect outRect = mTmpOutRect;
            outRect.set(cX - insetWidth, cY - insetHeight, cX + insetWidth, cY + insetHeight);

            d.setBounds(outRect);
        }
    }

    private void updateMaskBoundsInternal(Rect b) {
        mMaskMatrix.setScale(b.width() / MASK_SIZE, b.height() / MASK_SIZE);
        sMask.transform(mMaskMatrix, mMask);

        if (mMaskBitmap == null || mMaskBitmap.getWidth() != b.width() ||
                mMaskBitmap.getHeight() != b.height()) {
            mMaskBitmap = Bitmap.createBitmap(b.width(), b.height(), Bitmap.Config.ALPHA_8);
            mLayersBitmap = Bitmap.createBitmap(b.width(), b.height(), Bitmap.Config.ARGB_8888);
        }
        // mMaskBitmap bound [0, w] x [0, h]
        mCanvas.setBitmap(mMaskBitmap);
        mPaint.setShader(null);
        mPaint.setColor(0xFFFFFFFF);
        mCanvas.drawPath(mMask, mPaint);

        // mMask bound [left, top, right, bottom]
        mMaskMatrix.postTranslate(b.left, b.top);
        mMask.reset();
        sMask.transform(mMaskMatrix, mMask);
        // reset everything that depends on the view bounds
        mTransparentRegion.setEmpty();
        mLayersShader = null;
    }

    @Override
    public void draw(Canvas canvas) {
        if (mLayersBitmap == null) {
            return;
        }
        if (mLayersShader == null) {
            mCanvas.setBitmap(mLayersBitmap);
            mCanvas.drawColor(Color.BLACK);
            for (int i = 0; i < mLayerState.N_CHILDREN; i++) {
                if (mLayerState.mChildren[i] == null) {
                    continue;
                }
                final Drawable dr = mLayerState.mChildren[i].mDrawable;
                if (dr != null) {
                    dr.draw(mCanvas);
                }
            }
            mLayersShader = new BitmapShader(mLayersBitmap, TileMode.CLAMP, TileMode.CLAMP);
            mPaint.setShader(mLayersShader);
        }
        if (mMaskBitmap != null) {
            Rect bounds = getBounds();
            canvas.drawBitmap(mMaskBitmap, bounds.left, bounds.top, mPaint);
        }
    }

    @Override
    public void invalidateSelf() {
        mLayersShader = null;
        super.invalidateSelf();
    }

    @Override
    public void getOutline(@NonNull Outline outline) {
        outline.setConvexPath(mMask);
    }

    public Region getSafeZone() {
        mMaskMatrix.reset();
        mMaskMatrix.setScale(SAFEZONE_SCALE, SAFEZONE_SCALE, getBounds().centerX(), getBounds().centerY());
        Path p = new Path();
        mMask.transform(mMaskMatrix, p);
        Region safezoneRegion = new Region(getBounds());
        safezoneRegion.setPath(p, safezoneRegion);
        return safezoneRegion;
    }

    @Override
    public @Nullable Region getTransparentRegion() {
        if (mTransparentRegion.isEmpty()) {
            mMask.toggleInverseFillType();
            mTransparentRegion.set(getBounds());
            mTransparentRegion.setPath(mMask, mTransparentRegion);
            mMask.toggleInverseFillType();
        }
        return mTransparentRegion;
    }

    /**
     * Inflates child layers using the specified parser.
     */
    private void inflateLayers(@NonNull Resources r, @NonNull XmlPullParser parser,
            @NonNull AttributeSet attrs, @Nullable Theme theme)
            throws XmlPullParserException, IOException {
        final LayerState state = mLayerState;

        final int innerDepth = parser.getDepth() + 1;
        int type;
        int depth;
        int childIndex = 0;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && ((depth = parser.getDepth()) >= innerDepth || type != XmlPullParser.END_TAG)) {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }

            if (depth > innerDepth) {
                continue;
            }
            String tagName = parser.getName();
            switch (tagName) {
                case "background":
                    childIndex = BACKGROUND_ID;
                    break;
                case "foreground":
                    childIndex = FOREGROUND_ID;
                    break;
                default:
                    continue;
            }

            final ChildDrawable layer = new ChildDrawable(state.mDensity);
            final TypedArray a = obtainAttributes(r, theme, attrs, new int[] { android.R.attr.drawable });
            updateLayerFromTypedArray(layer, a);
            a.recycle();

            // If the layer doesn't have a drawable or unresolved theme
            // attribute for a drawable, attempt to parse one from the child
            // element. If multiple child elements exist, we'll only use the
            // first one.
            if (layer.mDrawable == null && (layer.mThemeAttrs == null)) {
                while ((type = parser.next()) == XmlPullParser.TEXT) {
                }
                if (type != XmlPullParser.START_TAG) {
                    throw new XmlPullParserException(parser.getPositionDescription()
                            + ": <foreground> or <background> tag requires a 'drawable'"
                            + "attribute or child tag defining a drawable");
                }

                // We found a child drawable. Take ownership.
                layer.mDrawable = createFromXmlInnerForDensity(r, parser, attrs,
                        mLayerState.mSrcDensityOverride, theme);
                layer.mDrawable.setCallback(this);
                state.mChildrenChangingConfigurations |=
                        layer.mDrawable.getChangingConfigurations();
            }
            addLayer(childIndex, layer);
        }
    }

    private static Drawable createFromXmlInnerForDensity(@NonNull Resources r,
            @NonNull XmlPullParser parser, @NonNull AttributeSet attrs, int density,
            @Nullable Theme theme) {
        try {
            return (Drawable) methodCreateFromXmlInnerForDensity.invoke(null,
                    r, parser, attrs, density, theme);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Error while creating adaptive icon", e);
        }
    }

    private void dumpAttrs(AttributeSet attrs) {
        final int N = attrs.getAttributeCount();
        for (int i = 0; i < N; i++) {
            Log.d(TAG, "name: " + attrs.getAttributeName(i));
            Log.d(TAG, "id: " + attrs.getAttributeNameResource(i));
        }
    }

    private void updateLayerFromTypedArray(@NonNull ChildDrawable layer, @NonNull TypedArray a) {
        final LayerState state = mLayerState;

        // Account for any configuration changes.
        state.mChildrenChangingConfigurations |= a.getChangingConfigurations();

        // Extract the theme attributes, if any.
        layer.mThemeAttrs = invoke(methodExtractThemeAttrs, a);

        @SuppressLint("ResourceType") Drawable dr = getDrawable(a, 0);
        if (dr != null) {
            if (layer.mDrawable != null) {
                // It's possible that a drawable was already set, in which case
                // we should clear the callback. We may have also integrated the
                // drawable's changing configurations, but we don't have enough
                // information to revert that change.
                layer.mDrawable.setCallback(null);
            }

            // Take ownership of the new drawable.
            layer.mDrawable = dr;
            layer.mDrawable.setCallback(this);
            state.mChildrenChangingConfigurations |=
                    layer.mDrawable.getChangingConfigurations();
        }
    }

    private Drawable getDrawable(TypedArray a, int index) {
        final TypedValue value = new TypedValue();
        a.getValue(index, value);
        if (value.resourceId != 0)
            return a.getResources().getDrawableForDensity(value.resourceId, 480, null);
        return null;
    }

    @Override
    public boolean canApplyTheme() {
        return (mLayerState != null && mLayerState.canApplyTheme()) || super.canApplyTheme();
    }

    /**
     * Temporarily suspends child invalidation.
     *
     * @see #resumeChildInvalidation()
     */
    private void suspendChildInvalidation() {
        mSuspendChildInvalidation = true;
    }

    /**
     * Resumes child invalidation after suspension, immediately performing an
     * invalidation if one was requested by a child during suspension.
     *
     * @see #suspendChildInvalidation()
     */
    private void resumeChildInvalidation() {
        mSuspendChildInvalidation = false;

        if (mChildRequestedInvalidation) {
            mChildRequestedInvalidation = false;
            invalidateSelf();
        }
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable who) {
        if (mSuspendChildInvalidation) {
            mChildRequestedInvalidation = true;
        } else {
            invalidateSelf();
        }
    }

    @Override
    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
        scheduleSelf(what, when);
    }

    @Override
    public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
        unscheduleSelf(what);
    }

    @Override
    public int getChangingConfigurations() {
        return super.getChangingConfigurations() | mLayerState.getChangingConfigurations();
    }

    @Override
    public void setHotspot(float x, float y) {
        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.N_CHILDREN; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) {
                dr.setHotspot(x, y);
            }
        }
    }

    @Override
    public void setHotspotBounds(int left, int top, int right, int bottom) {
        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.N_CHILDREN; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) {
                dr.setHotspotBounds(left, top, right, bottom);
            }
        }

        if (mHotspotBounds == null) {
            mHotspotBounds = new Rect(left, top, right, bottom);
        } else {
            mHotspotBounds.set(left, top, right, bottom);
        }
    }

    @Override
    public void getHotspotBounds(Rect outRect) {
        if (mHotspotBounds != null) {
            outRect.set(mHotspotBounds);
        } else {
            super.getHotspotBounds(outRect);
        }
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        final boolean changed = super.setVisible(visible, restart);
        final ChildDrawable[] array = mLayerState.mChildren;

        for (int i = 0; i < mLayerState.N_CHILDREN; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) {
                dr.setVisible(visible, restart);
            }
        }

        return changed;
    }

    @Override
    public void setDither(boolean dither) {
        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.N_CHILDREN; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) {
                dr.setDither(dither);
            }
        }
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.N_CHILDREN; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) {
                dr.setColorFilter(colorFilter);
            }
        }
    }

    @Override
    public void setTintList(ColorStateList tint) {
        final ChildDrawable[] array = mLayerState.mChildren;
        final int N = mLayerState.N_CHILDREN;
        for (int i = 0; i < N; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) {
                dr.setTintList(tint);
            }
        }
    }

    @Override
    public void setTintMode(Mode tintMode) {
        final ChildDrawable[] array = mLayerState.mChildren;
        final int N = mLayerState.N_CHILDREN;
        for (int i = 0; i < N; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) {
                dr.setTintMode(tintMode);
            }
        }
    }

    public void setOpacity(int opacity) {
        mLayerState.mOpacityOverride = opacity;
    }

    @Override
    public int getOpacity() {
        if (mLayerState.mOpacityOverride != PixelFormat.UNKNOWN) {
            return mLayerState.mOpacityOverride;
        }
        return mLayerState.getOpacity();
    }

    @Override
    public void setAutoMirrored(boolean mirrored) {
        mLayerState.mAutoMirrored = mirrored;

        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.N_CHILDREN; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) {
                dr.setAutoMirrored(mirrored);
            }
        }
    }

    @Override
    public boolean isAutoMirrored() {
        return mLayerState.mAutoMirrored;
    }

    @Override
    public void jumpToCurrentState() {
        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.N_CHILDREN; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) {
                dr.jumpToCurrentState();
            }
        }
    }

    @Override
    public boolean isStateful() {
        return mLayerState.isStateful();
    }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean changed = false;

        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.N_CHILDREN; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null && dr.isStateful() && dr.setState(state)) {
                changed = true;
            }
        }

        if (changed) {
            updateLayerBounds(getBounds());
        }

        return changed;
    }

    @Override
    protected boolean onLevelChange(int level) {
        boolean changed = false;

        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.N_CHILDREN; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null && dr.setLevel(level)) {
                changed = true;
            }
        }

        if (changed) {
            updateLayerBounds(getBounds());
        }

        return changed;
    }

    @Override
    public int getIntrinsicWidth() {
        return (int)(getMaxIntrinsicWidth() * DEFAULT_VIEW_PORT_SCALE);
    }

    private int getMaxIntrinsicWidth() {
        int width = -1;
        for (int i = 0; i < mLayerState.N_CHILDREN; i++) {
            final ChildDrawable r = mLayerState.mChildren[i];
            if (r.mDrawable == null) {
                continue;
            }
            final int w = r.mDrawable.getIntrinsicWidth();
            if (w > width) {
                width = w;
            }
        }
        return width;
    }

    @Override
    public int getIntrinsicHeight() {
        return (int)(getMaxIntrinsicHeight() * DEFAULT_VIEW_PORT_SCALE);
    }

    private int getMaxIntrinsicHeight() {
        int height = -1;
        for (int i = 0; i < mLayerState.N_CHILDREN; i++) {
            final ChildDrawable r = mLayerState.mChildren[i];
            if (r.mDrawable == null) {
                continue;
            }
            final int h = r.mDrawable.getIntrinsicHeight();
            if (h > height) {
                height = h;
            }
        }
        return height;
    }

    @Override
    public ConstantState getConstantState() {
        if (mLayerState.canConstantState()) {
            mLayerState.mChangingConfigurations = getChangingConfigurations();
            return mLayerState;
        }
        return null;
    }

    @Override
    public Drawable mutate() {
        if (!mMutated && super.mutate() == this) {
            mLayerState = createConstantState(mLayerState, null);
            for (int i = 0; i < mLayerState.N_CHILDREN; i++) {
                final Drawable dr = mLayerState.mChildren[i].mDrawable;
                if (dr != null) {
                    dr.mutate();
                }
            }
            mMutated = true;
        }
        return this;
    }

    public boolean isMaskValid() {
        return sMask != null && mMaskId == sMask.hashCode();
    }

    protected static @NonNull TypedArray obtainAttributes(@NonNull Resources res,
            @Nullable Theme theme, @NonNull AttributeSet set, @NonNull int[] attrs) {
        if (theme == null) {
            return res.obtainAttributes(set, attrs);
        }
        return theme.obtainStyledAttributes(set, attrs, 0, 0);
    }

    static class ChildDrawable {
        public Drawable mDrawable;
        public int[] mThemeAttrs;
        public int mDensity = DisplayMetrics.DENSITY_DEFAULT;

        ChildDrawable(int density) {
            mDensity = density;
        }

        ChildDrawable(@NonNull ChildDrawable orig, @NonNull AdaptiveIconCompat owner,
                @Nullable Resources res) {

            final Drawable dr = orig.mDrawable;
            final Drawable clone;
            if (dr != null) {
                final ConstantState cs = dr.getConstantState();
                if (cs == null) {
                    clone = dr;
                } else if (res != null) {
                    clone = cs.newDrawable(res);
                } else {
                    clone = cs.newDrawable();
                }
                clone.setCallback(owner);
                clone.setBounds(dr.getBounds());
                clone.setLevel(dr.getLevel());
            } else {
                clone = null;
            }

            mDrawable = clone;
            mThemeAttrs = orig.mThemeAttrs;

            mDensity = resolveDensity(res, orig.mDensity);
        }

        public boolean canApplyTheme() {
            return mThemeAttrs != null
                    || (mDrawable != null && mDrawable.canApplyTheme());
        }

        public final void setDensity(int targetDensity) {
            if (mDensity != targetDensity) {
                mDensity = targetDensity;
            }
        }
    }

    static class LayerState extends ConstantState {
        private int[] mThemeAttrs;

        final static int N_CHILDREN = 2;
        ChildDrawable[] mChildren;

        // The density at which to render the drawable and its children.
        int mDensity;

        // The density to use when inflating/looking up the children drawables. A value of 0 means
        // use the system's density.
        int mSrcDensityOverride = 0;

        int mOpacityOverride = PixelFormat.UNKNOWN;

        int mChangingConfigurations;
        int mChildrenChangingConfigurations;

        private boolean mCheckedOpacity;
        private int mOpacity;

        private boolean mCheckedStateful;
        private boolean mIsStateful;
        private boolean mAutoMirrored = false;

        LayerState(@Nullable LayerState orig, @NonNull AdaptiveIconCompat owner,
                @Nullable Resources res) {
            mDensity = resolveDensity(res, orig != null ? orig.mDensity : 0);
            mChildren = new ChildDrawable[N_CHILDREN];
            if (orig != null) {
                final ChildDrawable[] origChildDrawable = orig.mChildren;

                mChangingConfigurations = orig.mChangingConfigurations;
                mChildrenChangingConfigurations = orig.mChildrenChangingConfigurations;

                for (int i = 0; i < N_CHILDREN; i++) {
                    final ChildDrawable or = origChildDrawable[i];
                    mChildren[i] = new ChildDrawable(or, owner, res);
                }

                mCheckedOpacity = orig.mCheckedOpacity;
                mOpacity = orig.mOpacity;
                mCheckedStateful = orig.mCheckedStateful;
                mIsStateful = orig.mIsStateful;
                mAutoMirrored = orig.mAutoMirrored;
                mThemeAttrs = orig.mThemeAttrs;
                mOpacityOverride = orig.mOpacityOverride;
                mSrcDensityOverride = orig.mSrcDensityOverride;
            } else {
                for (int i = 0; i < N_CHILDREN; i++) {
                    mChildren[i] = new ChildDrawable(mDensity);
                }
            }
        }

        public final void setDensity(int targetDensity) {
            if (mDensity != targetDensity) {
                mDensity = targetDensity;
            }
        }

        @Override
        public boolean canApplyTheme() {
            if (mThemeAttrs != null || super.canApplyTheme()) {
                return true;
            }

            final ChildDrawable[] array = mChildren;
            for (int i = 0; i < N_CHILDREN; i++) {
                final ChildDrawable layer = array[i];
                if (layer.canApplyTheme()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Drawable newDrawable() {
            return new AdaptiveIconCompat(this, null);
        }

        @Override
        public Drawable newDrawable(@Nullable Resources res) {
            return new AdaptiveIconCompat(this, res);
        }

        @Override
        public int getChangingConfigurations() {
            return mChangingConfigurations
                    | mChildrenChangingConfigurations;
        }

        public final int getOpacity() {
            if (mCheckedOpacity) {
                return mOpacity;
            }

            final ChildDrawable[] array = mChildren;

            // Seek to the first non-null drawable.
            int firstIndex = -1;
            for (int i = 0; i < N_CHILDREN; i++) {
                if (array[i].mDrawable != null) {
                    firstIndex = i;
                    break;
                }
            }

            int op;
            if (firstIndex >= 0) {
                op = array[firstIndex].mDrawable.getOpacity();
            } else {
                op = PixelFormat.TRANSPARENT;
            }

            // Merge all remaining non-null drawables.
            for (int i = firstIndex + 1; i < N_CHILDREN; i++) {
                final Drawable dr = array[i].mDrawable;
                if (dr != null) {
                    op = Drawable.resolveOpacity(op, dr.getOpacity());
                }
            }

            mOpacity = op;
            mCheckedOpacity = true;
            return op;
        }

        public final boolean isStateful() {
            if (mCheckedStateful) {
                return mIsStateful;
            }

            final ChildDrawable[] array = mChildren;
            boolean isStateful = false;
            for (int i = 0; i < N_CHILDREN; i++) {
                final Drawable dr = array[i].mDrawable;
                if (dr != null && dr.isStateful()) {
                    isStateful = true;
                    break;
                }
            }

            mIsStateful = isStateful;
            mCheckedStateful = true;
            return isStateful;
        }

        public final boolean canConstantState() {
            final ChildDrawable[] array = mChildren;
            for (int i = 0; i < N_CHILDREN; i++) {
                final Drawable dr = array[i].mDrawable;
                if (dr != null && dr.getConstantState() == null) {
                    return false;
                }
            }

            // Don't cache the result, this method is not called very often.
            return true;
        }

        public void invalidateCache() {
            mCheckedOpacity = false;
            mCheckedStateful = false;
        }
    }

    @NonNull
    public static Drawable wrap(@NonNull Drawable icon) {
        if (Utilities.ATLEAST_OREO && icon instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable) icon;
            return new AdaptiveIconCompat(adaptive.getBackground(), adaptive.getForeground());
        } else {
            return icon;
        }
    }

    @Nullable
    public static Drawable wrapNullable(@Nullable Drawable icon) {
        if (Utilities.ATLEAST_OREO && icon instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable) icon;
            return new AdaptiveIconCompat(adaptive.getBackground(), adaptive.getForeground());
        } else {
            return icon;
        }
    }

    public static void resetMask() {
        sMask = null;
    }
}
