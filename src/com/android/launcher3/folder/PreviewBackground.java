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

package com.android.launcher3.folder;

import static com.android.app.animation.Interpolators.ACCELERATE_DECELERATE;
import static com.android.app.animation.Interpolators.EMPHASIZED_DECELERATE;
import static com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound;
import static com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.animation.OvershootInterpolator;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.BitmapShader;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.util.DisplayMetrics;
import android.util.Property;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.BaseAllAppsAdapter;
import com.android.launcher3.celllayout.DelegatedCellDrawing;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.graphics.ShapeDelegate;

import app.lawnchair.ui.liquid.LiquidGlassWallpaper;
import app.lawnchair.views.LawnchairScrimView;
import com.android.launcher3.Launcher;
import com.android.launcher3.views.ScrimView;
import app.lawnchair.ui.liquid.LiquidRimLight;
import app.lawnchair.ui.liquid.ShapeCorner;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import app.lawnchair.preferences2.PreferenceCacheExtensionsKt;

import app.lawnchair.preferences2.PreferenceManager2;
import app.lawnchair.theme.color.ColorOption;
import app.lawnchair.theme.color.tokens.ColorTokens;
import app.lawnchair.util.LawnchairUtilsKt;

/**
 * This object represents a FolderIcon preview background. It stores drawing /
 * measurement
 * information, handles drawing, and animation (accept state <--> rest state).
 */
public class PreviewBackground extends DelegatedCellDrawing {

    private static final boolean DRAW_SHADOW = false;
    private static final boolean DRAW_STROKE = false;

    @VisibleForTesting
    protected static final int CONSUMPTION_ANIMATION_DURATION = 100;

    @VisibleForTesting
    protected static final float HOVER_SCALE = 1.1f;
    @VisibleForTesting
    protected static final int HOVER_ANIMATION_DURATION = 300;

    /**
     * Caddy's tile, measured off the App Library it copies.
     *
     * On the 1170px screen the reference was taken from: the grid is held 45 in
     * from each edge, the plate is 488 square with an 82 corner, and 105 is left
     * below it for the label. Those numbers check out against the screen twice
     * over -- 71 + 488 + 52 + 488 + 71 = 1170 across the pair, and 37 + 192 + 30
     * + 192 + 37 = 488 across the apps inside one -- which is what says they
     * were read off the real thing rather than guessed at.
     *
     * Kept together because they only mean anything together: the margin at the
     * screen's edge is the grid padding plus half of whatever the plate leaves
     * in its tile, so moving either one alone moves the margin and the gap in
     * opposite directions. Caddy sizes itself from these and ignores the
     * drawer's own column and row-height settings entirely.
     */
    public static final float CADDY_PLATE_RATIO = 488f / 1170f;

    /** How far Caddy holds its grid off each edge of the screen. */
    public static final float CADDY_GRID_PADDING_RATIO = 45f / 1170f;

    /** The plate's corner, against its own width. */
    public static final float CADDY_PLATE_CORNER_RATIO = 82f / 488f;

    /** Room left below the plate for the label, against the screen's width. */
    public static final float CADDY_ROW_GAP_RATIO = 105f / 1170f;

    /**
     * The drop from the plate's bottom edge to the top of the label's letters.
     *
     * 25 of the 105 below the plate, which is why the label reads as belonging
     * to the tile above it rather than floating midway between two: 25 above the
     * letters against 54 below them.
     */
    public static final float CADDY_LABEL_GAP_RATIO = 25f / 1170f;

    /** Caddy's plate corner, or 0 when this plate is not one of Caddy's. */
    private float mCaddyCornerRadius = 0f;

    private static final int GLASS_TINT_LIGHT = 0x40FFFFFF;
    private static final int GLASS_TINT_DARK = 0x338A8A8E;
    /**
     * The lit edge. Bright where the surface faces the light, all but gone where
     * it turns away -- the two together are what make an edge look lit rather
     * than outlined.
     */
    private static final int GLASS_RIM_LIT = 0x8CFFFFFF;
    private static final int GLASS_RIM_UNLIT = 0x14FFFFFF;

    private final Context mContext;
    private final PorterDuffXfermode mShadowPorterDuffXfermode
            = new PorterDuffXfermode(PorterDuff.Mode.DST_OUT);
    private RadialGradient mShadowShader = null;

    private final Matrix mShaderMatrix = new Matrix();
    private final Path mPath = new Path();

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Sora: paints the blurred wallpaper through the folder's own shape. */
    private final Paint mGlassPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix mGlassMatrix = new Matrix();
    private final Path mGlassPath = new Path();
    private final int[] mGlassLocation = new int[2];

    float mScale = 1f;
    private int mBgColor;
    private int mStrokeColor;
    private int mDotColor;
    private float mStrokeWidth;
    private int mStrokeAlpha = MAX_BG_OPACITY;
    private int mShadowAlpha = 255;
    private View mInvalidateDelegate;

    int previewSize;
    int previewWidth;
    int previewHeight;
    /** Whether the last {@link #setup} could size the plate for the span it covers. */
    private boolean mPlateResolved = true;

    /** Where a resize is animating from, and how far along it is. */
    private int mResizeFromWidth;
    private int mResizeFromHeight;
    private float mResizeProgress = 1f;
    private ValueAnimator mResizeAnimator;
    private static final int RESIZE_DURATION = 420;
    /**
     * How far past its new size the plate swings before settling.
     *
     * A folder that simply eases into its new size reads as a picture being
     * rescaled. Letting it carry a little past and come back reads as something
     * with weight snapping into the cells it was just given -- which is the
     * whole point of the gesture being a drag rather than a menu item.
     */
    private static final float RESIZE_OVERSHOOT = 1.6f;
    int basePreviewOffsetX;
    int basePreviewOffsetY;

    private CellLayout mDrawingDelegate;

    // When the PreviewBackground is drawn under an icon (for creating a folder) the
    // border
    // should not occlude the icon
    public boolean isClipping = true;

    // Drawing / animation configurations
    @VisibleForTesting
    protected static final float ACCEPT_SCALE_FACTOR = 1.20f;

    // Expressed on a scale from 0 to 255.
    private static final int BG_OPACITY = 255;
    private static final int MAX_BG_OPACITY = 255;
    private static final int SHADOW_OPACITY = 40;

    @VisibleForTesting
    protected ValueAnimator mScaleAnimator;
    private ObjectAnimator mStrokeAlphaAnimator;
    private ObjectAnimator mShadowAnimator;

    @VisibleForTesting
    protected boolean mIsAccepting;
    @VisibleForTesting
    protected boolean mIsHovered;
    @VisibleForTesting
    protected boolean mIsHoveredOrAnimating;

    private static final Property<PreviewBackground, Integer> STROKE_ALPHA = new Property<PreviewBackground, Integer>(
            Integer.class, "strokeAlpha") {
        @Override
        public Integer get(PreviewBackground previewBackground) {
            return previewBackground.mStrokeAlpha;
        }

        @Override
        public void set(PreviewBackground previewBackground, Integer alpha) {
            previewBackground.mStrokeAlpha = alpha;
            previewBackground.invalidate();
        }
    };

    private static final Property<PreviewBackground, Integer> SHADOW_ALPHA = new Property<PreviewBackground, Integer>(
            Integer.class, "shadowAlpha") {
        @Override
        public Integer get(PreviewBackground previewBackground) {
            return previewBackground.mShadowAlpha;
        }

        @Override
        public void set(PreviewBackground previewBackground, Integer alpha) {
            previewBackground.mShadowAlpha = alpha;
            previewBackground.invalidate();
        }
    };

    public PreviewBackground(Context context) {
        mContext = context;
    }

    /**
     * Draws folder background under cell layout
     */
    @Override
    public void drawUnderItem(Canvas canvas) {
        drawBackground(canvas);
        if (!isClipping) {
            drawBackgroundStroke(canvas);
        }
    }

    /**
     * Draws folder background on cell layout
     */
    @Override
    public void drawOverItem(Canvas canvas) {
        if (isClipping) {
            drawBackgroundStroke(canvas);
        }
    }

    public void setup(Context context, ActivityContext activity, View invalidateDelegate,
            int availableSpaceX, int topPadding) {
        mInvalidateDelegate = invalidateDelegate;

        PreferenceManager2 preferenceManager2 = PreferenceManager2.INSTANCE.get(context);

        TypedArray ta = context.getTheme().obtainStyledAttributes(R.styleable.FolderIconPreview);
        ColorOption dotColorOption = PreferenceCacheExtensionsKt.firstCached(preferenceManager2.getNotificationDotColor());
        mDotColor = dotColorOption.getColorPreferenceEntry().getLightColor().invoke(context);
        mStrokeColor = ColorTokens.FolderIconBorderColor.resolveColor(context);
        mBgColor = LawnchairUtilsKt.resolveFolderPreviewColor(context);
        ta.recycle();

        DeviceProfile grid = activity.getDeviceProfile();
        // Lawnchair: Find the correct icon size depending on which parent owned them
        boolean inDrawer = invalidateDelegate instanceof FolderIcon
                && ((FolderIcon) invalidateDelegate).isInAppDrawer();
        if (inDrawer) {
            int allAppsIconSize = grid.getAllAppsProfile().getIconSizePx();
            previewSize = Math.round(allAppsIconSize * ICON_VISIBLE_AREA_FACTOR);
            basePreviewOffsetX = (availableSpaceX - previewSize) / 2;
            basePreviewOffsetY = topPadding + (allAppsIconSize - previewSize) / 2;
        } else {
            previewSize = grid.folderIconSizePx;

            basePreviewOffsetX = (availableSpaceX - previewSize) / 2;
            basePreviewOffsetY = topPadding + grid.folderIconOffsetYPx;
        }

        previewWidth = previewSize;
        previewHeight = previewSize;
        // A one-cell plate needs nothing from the cell layout, so it is settled
        // the moment it is asked for. Anything larger is only settled once the
        // pitch below comes back.
        mPlateResolved = true;

        mCaddyCornerRadius = 0f;
        if (inDrawer && BaseAllAppsAdapter.isCaddy(context)) {
            // A square plate, sized straight off the screen. Nothing here is
            // derived from a cell or a pitch: Caddy is copying a fixed layout,
            // so it states that layout and lets the drawer's own column count
            // and row height alone.
            //
            // The apps inside need no help. The rule that lays them out already
            // works in fractions of an app -- 0.19 of one for the margin round
            // the block, 0.15 between them -- and the reference measures 37 and
            // 30 against an app of 192, which is the same two numbers. Give it
            // the right plate and the apps land at the right size on their own.
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int plate = Math.max(previewSize, Math.round(screenWidth * CADDY_PLATE_RATIO));
            previewWidth = plate;
            previewHeight = plate;
            basePreviewOffsetX = (availableSpaceX - previewWidth) / 2;
            // Flush with the top of the tile; the label has the room below it.
            basePreviewOffsetY = topPadding;
            // Cut from the plate, not from an icon. A corner taken from one
            // app's worth would be far too tight on a plate this size -- the
            // reference's 82 is 16.8% of its own 488, not a fraction of the 192
            // apps sitting in it.
            mCaddyCornerRadius = plate * CADDY_PLATE_CORNER_RATIO;
        }

        if (invalidateDelegate instanceof FolderIcon icon
                && !icon.isInAppDrawer() && icon.mInfo != null) {
            int spanX = Math.max(1, icon.mInfo.spanX);
            int spanY = Math.max(1, icon.mInfo.spanY);
            // Taken from the cell layout, which is the only place that knows
            // where a cell actually is.
            //
            // DeviceProfile's cell size is the content box, not the pitch
            // children are placed on. And dividing the folder's own view by its
            // span is worse: this runs during layout, so the height is still the
            // one from before the resize -- the old height over the new span,
            // which grew the plate by half a cell. Enough to reach past the icon
            // and cover the label, nowhere near the row below.
            int pitchX = 0;
            int pitchY = 0;
            if (icon.getParent() != null
                    && icon.getParent().getParent() instanceof CellLayout cellLayout
                    && icon.getLayoutParams() instanceof CellLayoutLayoutParams cellParams) {
                // The distance between one cell and the next, not the size of a
                // cell. Those are not the same number here: a cell reports 259
                // wide and 262 tall while the rows actually sit 309 apart, so a
                // plate grown by the cell's height lands short of the row below
                // -- far enough to cover the label, never far enough to reach
                // the next folder. Measuring the step between neighbours is
                // right whatever a cell's own height is taken to mean.
                Rect here = new Rect();
                Rect next = new Rect();
                cellLayout.cellToRect(cellParams.getCellX(), cellParams.getCellY(), 1, 1, here);
                cellLayout.cellToRect(cellParams.getCellX() + 1, cellParams.getCellY(), 1, 1, next);
                pitchX = next.left - here.left;
                cellLayout.cellToRect(cellParams.getCellX(), cellParams.getCellY() + 1, 1, 1, next);
                pitchY = next.top - here.top;
            }
            if (spanX > 1 && pitchX > 0) {
                previewWidth += (spanX - 1) * pitchX;
            }
            if (spanY > 1 && pitchY > 0) {
                previewHeight += (spanY - 1) * pitchY;
            }
            // A span the pitch could not answer for leaves the plate one cell
            // wide where it should have been several, and the caller memoises
            // what it is told. Nothing it keys that memo on changes when the
            // icon is finally parented into a laid-out cell layout, so a plate
            // that came out short here would stay short for the life of the
            // icon. Saying so lets the caller ask again next time.
            mPlateResolved = (spanX <= 1 || pitchX > 0) && (spanY <= 1 || pitchY > 0);
        }
        basePreviewOffsetX = (availableSpaceX - previewWidth) / 2;

        // Stroke width is 1dp
        mStrokeWidth = context.getResources().getDisplayMetrics().density;

        if (DRAW_SHADOW) {
            float radius = getScaledRadius();
            float shadowRadius = radius + mStrokeWidth;
            int shadowColor = Color.argb(SHADOW_OPACITY, 0, 0, 0);
            mShadowShader = new RadialGradient(0, 0, 1,
                    new int[] { shadowColor, Color.TRANSPARENT },
                    new float[] { radius / shadowRadius, 1 },
                    Shader.TileMode.CLAMP);
        }

        invalidate();
    }

    void getBounds(Rect outBounds) {
        int top = basePreviewOffsetY;
        int left = basePreviewOffsetX;
        outBounds.set(left, top, left + previewWidth, top + previewHeight);
    }

    /**
     * Sora: the plate this folder draws, which is only square when the folder
     * occupies a single cell.
     *
     * A folder given more than one cell stops being an icon and becomes a panel,
     * and everything downstream has to follow: the shape it is cut to, the
     * corner it is cut with, the rectangle its glass pane covers, and the
     * rectangle its opening animation grows out of.
     */
    public int getPlateWidth() {
        return (int) (mScale * lerp(mResizeFromWidth, previewWidth));
    }

    public int getPlateHeight() {
        return (int) (mScale * lerp(mResizeFromHeight, previewHeight));
    }

    private float lerp(int from, int to) {
        return from <= 0 ? to : from + (to - from) * mResizeProgress;
    }

    /**
     * Grows the plate from the size it used to be into the size it now is.
     *
     * The cell layout re-places the folder the moment its span changes, so the
     * view is already its new size before anything is drawn. Animating the plate
     * inside it is what turns that into a folder growing rather than a folder
     * replaced: the glass pane reads its bounds from here every frame, so it
     * comes along without being told.
     */
    public void animateResizeFrom(int fromWidth, int fromHeight) {
        if (fromWidth <= 0 || fromHeight <= 0) return;
        if (mResizeAnimator != null) {
            mResizeAnimator.cancel();
        }
        mResizeFromWidth = fromWidth;
        mResizeFromHeight = fromHeight;
        mResizeProgress = 0f;

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(RESIZE_DURATION);
        animator.setInterpolator(new OvershootInterpolator(RESIZE_OVERSHOOT));
        animator.addUpdateListener(a -> {
            mResizeProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mResizeFromWidth = 0;
                mResizeFromHeight = 0;
                mResizeProgress = 1f;
                mResizeAnimator = null;
                invalidate();
            }
        });
        mResizeAnimator = animator;
        animator.start();
    }

    /**
     * Whether the plate has the size the folder's span calls for.
     *
     * False only in the window where the icon has not yet been parented into a
     * laid-out cell layout, which is the one thing the plate's size is read
     * from. Callers that cache anything derived from the plate have to ask
     * again while this is false.
     */
    boolean isPlateResolved() {
        return mPlateResolved;
    }

    /**
     * Whether this plate covers a single cell.
     *
     * Not the same question as whether it is square, though it was asked that
     * way at first. A folder given two cells across and two down comes out 415
     * by 465 on this grid, so a width-height comparison happens to catch it --
     * but on a grid whose rows and columns share a pitch that same folder is
     * square, and would be cut to the icon shape and blown up into one enormous
     * circle. What decides the shape is how many cells the plate covers, never
     * the proportions it happens to land at.
     */
    boolean isSingleCellPlate() {
        return previewWidth == previewSize && previewHeight == previewSize;
    }

    /**
     * The size the plate's corner is cut for: one cell, always.
     *
     * Two cells given to a folder are two folders merged, with no seam between
     * them, so the outer corners have to stay exactly as round as one folder's
     * and the edges between them run straight. Taking the corner from the whole
     * plate instead is what rounded a 2x2 into a single blob.
     *
     * Everything that draws this plate reads the corner from here, so the glass
     * pane in front of the workspace and the path drawn behind it cannot drift
     * apart -- which is how the two came to disagree in the first place.
     */
    public int getPlateCornerSize() {
        return (int) (mScale * previewSize);
    }

    /**
     * The corner this plate is actually cut with.
     *
     * Caddy states its own, measured against the whole plate; everywhere else it
     * is whatever the icon shape's corner comes to at one cell. The glass pane
     * in front of the plate has to be told this rather than working it out for
     * itself -- asked for one cell's corner it drew a 49px radius where the
     * plate is cut at 76, and the pane and the plate stopped being the same
     * shape.
     */
    public float getPlateCornerRadius() {
        return mCaddyCornerRadius > 0f
                ? mCaddyCornerRadius
                : ShapeCorner.radiusFor(getShape(), getPlateCornerSize());
    }

    /**
     * Builds the plate's outline.
     *
     * A one-cell plate is cut to the icon shape itself, so a folder sits among
     * the icons as one of them -- circle, squircle, teardrop, whatever is set.
     * A plate covering more cannot be: the icon mask is defined on a square and
     * stretching it distorts the very corners it was chosen for. It becomes a
     * rounded rectangle instead, wearing the corner that shape would have had
     * at one cell, so the two still read as the same family.
     */
    void addPlateToPath(Path outPath, float offsetX, float offsetY) {
        addPlateToPath(outPath, offsetX, offsetY, 0f);
    }

    void addPlateToPath(Path outPath, float offsetX, float offsetY, float inset) {
        outPath.reset();
        float width = getPlateWidth() - 2 * inset;
        float height = getPlateHeight() - 2 * inset;
        float left = offsetX + inset;
        float top = offsetY + inset;
        // A square plate wears the icon shape itself, whatever that shape is.
        //
        // Caddy's tile is square, so the mask can be used as it stands -- no
        // stretching, and therefore none of the distortion that made a shape
        // unusable on the oblong plates. Pick iOS and the folder becomes the
        // same squircle its icons are, which is what makes the drawer read as
        // one set of shapes rather than two.
        boolean squarePlate = Math.abs(width - height) < 2f;
        if (isSingleCellPlate() || (mCaddyCornerRadius > 0f && squarePlate)) {
            getShape().addToPath(outPath, left, top, width / 2f);
            return;
        }
        // Caddy states its own corner; everywhere else the plate wears the
        // corner the chosen icon shape would have had at one cell.
        float corner = mCaddyCornerRadius > 0f
                ? mCaddyCornerRadius * (width / Math.max(1f, getPlateWidth()))
                : ShapeCorner.radiusFor(getShape(), getPlateCornerSize());
        outPath.addRoundRect(
                left, top, left + width, top + height,
                corner, corner, Path.Direction.CW);
    }

    public int getRadius() {
        return previewSize / 2;
    }

    int getScaledRadius() {
        return (int) (mScale * getRadius());
    }

    int getOffsetX() {
        return basePreviewOffsetX - (getScaledRadius() - getRadius());
    }

    int getOffsetY() {
        return basePreviewOffsetY - (getScaledRadius() - getRadius());
    }

    /**
     * Returns the progress of the scale animation to accept state, where 0 means
     * the scale is at
     * 1f and 1 means the scale is at ACCEPT_SCALE_FACTOR. Returns 0 when scaled due
     * to hover.
     */
    float getAcceptScaleProgress() {
        return mIsHoveredOrAnimating ? 0 : (mScale - 1f) / (ACCEPT_SCALE_FACTOR - 1f);
    }

    void invalidate() {
        if (mInvalidateDelegate != null) {
            mInvalidateDelegate.invalidate();
        }

        if (mDrawingDelegate != null) {
            mDrawingDelegate.invalidate();
        }
    }

    void setInvalidateDelegate(View invalidateDelegate) {
        mInvalidateDelegate = invalidateDelegate;
        invalidate();
    }

    public int getBgColor() {
        return mBgColor;
    }

    public int getDotColor() {
        return mDotColor;
    }

    public void drawBackground(Canvas canvas) {
        if (drawGlassBackground(canvas)) {
            drawShadow(canvas);
            return;
        }

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(getBgColor());

        addPlateToPath(mGlassPath, getOffsetX(), getOffsetY());
        canvas.drawPath(mGlassPath, mPaint);
        drawShadow(canvas);
    }

    /**
     * Sora: fills the folder's shape with the wallpaper behind it, blurred.
     *
     * What sits behind a closed folder icon is the wallpaper -- capturing the view
     * tree here would mean capturing the very draw that is in progress. The
     * wallpaper is blurred once and cached, so each frame is only a shader-backed
     * shape fill; a per-frame blur would be far too costly on a surface that is
     * redrawn throughout every home screen scroll.
     *
     * The image is anchored to the screen rather than to the icon, so pages
     * sliding past reveal different parts of it. That is what makes a still image
     * read as live glass.
     *
     * Returns false when there is no wallpaper to read, leaving the caller to draw
     * its usual flat background.
     */
    /**
     * Set when a real glass pane is drawing this icon's background instead.
     *
     * The pane lives behind the workspace so the preview icons stay in front of
     * it, which means anything painted here would cover it up.
     */
    private boolean mHasGlassPane = false;


    public void setHasGlassPane(boolean hasGlassPane) {
        if (mHasGlassPane != hasGlassPane) {
            mHasGlassPane = hasGlassPane;
            invalidate();
        }
    }

    private boolean drawGlassBackground(Canvas canvas) {
        // The pane behind the workspace is showing through; painting here would
        // only hide it. Reporting success keeps the flat fallback away too.
        if (mHasGlassPane) {
            return true;
        }
        if (mInvalidateDelegate == null) {
            return false;
        }
        // A folder in the drawer refracts the same frosted home screen the
        // drawer itself is sitting on, not the bare wallpaper.
        //
        // They are two different pictures: the drawer's backdrop is the
        // workspace captured with its icons on it, the wallpaper is just the
        // wallpaper. A tile refracting the second while floating on the first
        // shows a patch of scenery that does not match its surroundings, which
        // is the one thing a pane of glass cannot do.
        Bitmap wallpaper = drawerBackdrop();
        if (wallpaper == null) {
            wallpaper = LiquidGlassWallpaper.INSTANCE.getBlurred(mContext);
        }
        if (wallpaper == null) {
            return false;
        }

        int radius = getScaledRadius();
        int size = radius * 2;
        if (size <= 0) {
            return false;
        }

        mInvalidateDelegate.getLocationOnScreen(mGlassLocation);
        DisplayMetrics display = mContext.getResources().getDisplayMetrics();
        if (display.widthPixels <= 0 || display.heightPixels <= 0) {
            return false;
        }

        // Map screen pixels into the cached wallpaper, which is stored downscaled.
        float scaleX = (float) wallpaper.getWidth() / display.widthPixels;
        float scaleY = (float) wallpaper.getHeight() / display.heightPixels;
        float screenLeft = mGlassLocation[0] + getOffsetX();
        float screenTop = mGlassLocation[1] + getOffsetY();

        mGlassMatrix.reset();
        // Undo the wallpaper's downscale, then slide it so the pixel under this
        // icon's top-left corner lands at the shape's origin.
        mGlassMatrix.setScale(1f / scaleX, 1f / scaleY);
        mGlassMatrix.postTranslate(-screenLeft + getOffsetX(), -screenTop + getOffsetY());

        BitmapShader shader = new BitmapShader(
                wallpaper, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        shader.setLocalMatrix(mGlassMatrix);
        mGlassPaint.setShader(shader);
        mGlassPaint.setStyle(Paint.Style.FILL);

        addPlateToPath(mGlassPath, getOffsetX(), getOffsetY());

        canvas.drawPath(mGlassPath, mGlassPaint);
        mGlassPaint.setShader(null);

        // A wash keeps icons legible over a busy wallpaper, and a thin rim is what
        // separates a pane of glass from a plain blur.
        mGlassPaint.setColor(getGlassTint());
        canvas.drawPath(mGlassPath, mGlassPaint);

        LiquidRimLight.drawPath(canvas, mGlassPath, display.density * LiquidRimLight.WIDTH_DP);
        return true;
    }

    /**
     * The frosted home screen the drawer is drawn on, when this plate is in it.
     *
     * Null everywhere else -- a folder on the workspace is in front of the real
     * home screen, not a picture of it, and has the wallpaper to refract.
     */
    @Nullable
    private Bitmap drawerBackdrop() {
        if (!(mInvalidateDelegate instanceof FolderIcon icon) || !icon.isInAppDrawer()) {
            return null;
        }
        if (!(icon.mActivity instanceof Launcher launcher)) {
            return null;
        }
        ScrimView scrim = launcher.getScrimView();
        return scrim instanceof LawnchairScrimView lawnScrim
                ? lawnScrim.getDrawerBackdrop()
                : null;
    }


    /** Wash laid over the blurred wallpaper, following the light/dark theme. */
    private int getGlassTint() {
        boolean dark = (mContext.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        return dark ? GLASS_TINT_DARK : GLASS_TINT_LIGHT;
    }

    private ShapeDelegate getShape() {
        return ThemeManager.INSTANCE.get(mContext).getFolderShape();
    }

    public void drawShadow(Canvas canvas) {
        if (!DRAW_SHADOW) {
            return;
        }
        if (mShadowShader == null) {
            return;
        }

        float radius = getScaledRadius();
        float shadowRadius = radius + mStrokeWidth;
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.BLACK);
        int offsetX = getOffsetX();
        int offsetY = getOffsetY();
        final int saveCount;
        if (canvas.isHardwareAccelerated()) {
            saveCount = canvas.saveLayer(offsetX - mStrokeWidth, offsetY,
                    offsetX + radius + shadowRadius, offsetY + shadowRadius + shadowRadius, null);

        } else {
            saveCount = canvas.save();
            canvas.clipPath(getClipPath(), Region.Op.DIFFERENCE);
        }

        mShaderMatrix.setScale(shadowRadius, shadowRadius);
        mShaderMatrix.postTranslate(radius + offsetX, shadowRadius + offsetY);
        mShadowShader.setLocalMatrix(mShaderMatrix);
        mPaint.setAlpha(mShadowAlpha);
        mPaint.setShader(mShadowShader);
        canvas.drawPaint(mPaint);
        mPaint.setAlpha(255);
        mPaint.setShader(null);
        if (canvas.isHardwareAccelerated()) {
            mPaint.setXfermode(mShadowPorterDuffXfermode);
            getShape().drawShape(canvas, offsetX, offsetY, radius, mPaint);
            mPaint.setXfermode(null);
        }

        canvas.restoreToCount(saveCount);
    }

    public void fadeInBackgroundShadow() {
        if (!DRAW_SHADOW) {
            return;
        }
        if (mShadowAnimator != null) {
            mShadowAnimator.cancel();
        }
        mShadowAnimator = ObjectAnimator
                .ofInt(this, SHADOW_ALPHA, 0, 255)
                .setDuration(100);
        mShadowAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mShadowAnimator = null;
            }
        });
        mShadowAnimator.start();
    }

    public void animateBackgroundStroke() {
        if (!DRAW_STROKE) {
            return;
        }

        if (mStrokeAlphaAnimator != null) {
            mStrokeAlphaAnimator.cancel();
        }
        mStrokeAlphaAnimator = ObjectAnimator
                .ofInt(this, STROKE_ALPHA, MAX_BG_OPACITY / 2, MAX_BG_OPACITY)
                .setDuration(100);
        mStrokeAlphaAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mStrokeAlphaAnimator = null;
            }
        });
        mStrokeAlphaAnimator.start();
    }

    public void drawBackgroundStroke(Canvas canvas) {
        if (!DRAW_STROKE) {
            return;
        }
        mPaint.setColor(setColorAlphaBound(mStrokeColor, mStrokeAlpha));
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(mStrokeWidth);

        float inset = 1f;
        getShape().drawShape(canvas,
                getOffsetX() + inset, getOffsetY() + inset, getScaledRadius() - inset, mPaint);
    }

    /**
     * Draws the leave-behind circle on the given canvas and in the given color.
     */
    public void drawLeaveBehind(Canvas canvas, int color) {
        float originalScale = mScale;
        mScale = 0.5f;

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(color);
        getShape().drawShape(canvas, getOffsetX(), getOffsetY(), getScaledRadius(), mPaint);

        mScale = originalScale;
    }

    public Path getClipPath() {
        // The plate, whatever size it now is. Built as a one-cell shape pinned to
        // the plate's top-left corner, this cut off every preview app outside
        // that first cell -- and on a grown folder most of them are.
        addPlateToPath(mPath, getOffsetX(), getOffsetY());
        return mPath;
    }

    private void delegateDrawing(CellLayout delegate, int cellX, int cellY) {
        if (mDrawingDelegate != delegate) {
            delegate.addDelegatedCellDrawing(this);
        }

        mDrawingDelegate = delegate;
        mDelegateCellX = cellX;
        mDelegateCellY = cellY;

        invalidate();
    }

    private void clearDrawingDelegate() {
        if (mDrawingDelegate != null) {
            mDrawingDelegate.removeDelegatedCellDrawing(this);
        }

        mDrawingDelegate = null;
        isClipping = false;
        invalidate();
    }

    boolean drawingDelegated() {
        return mDrawingDelegate != null;
    }

    protected void animateScale(boolean isAccepting, boolean isHovered) {
        if (mScaleAnimator != null) {
            mScaleAnimator.cancel();
        }

        final float startScale = mScale;
        final float endScale = isAccepting ? ACCEPT_SCALE_FACTOR : (isHovered ? HOVER_SCALE : 1f);
        Interpolator interpolator = isAccepting != mIsAccepting ? ACCELERATE_DECELERATE : EMPHASIZED_DECELERATE;
        int duration = isAccepting != mIsAccepting ? CONSUMPTION_ANIMATION_DURATION
                : HOVER_ANIMATION_DURATION;
        mIsAccepting = isAccepting;
        mIsHovered = isHovered;
        if (startScale == endScale) {
            if (!mIsAccepting) {
                clearDrawingDelegate();
            }
            mIsHoveredOrAnimating = mIsHovered;
            return;
        }

        mScaleAnimator = ValueAnimator.ofFloat(0f, 1.0f);
        mScaleAnimator.addUpdateListener(animation -> {
            float prog = animation.getAnimatedFraction();
            mScale = prog * endScale + (1 - prog) * startScale;
            invalidate();
        });
        mScaleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (mIsHovered) {
                    mIsHoveredOrAnimating = true;
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!mIsAccepting) {
                    clearDrawingDelegate();
                }
                mIsHoveredOrAnimating = mIsHovered;
                mScaleAnimator = null;
            }
        });
        mScaleAnimator.setInterpolator(interpolator);
        mScaleAnimator.setDuration(duration);
        mScaleAnimator.start();
    }

    public void animateToAccept(CellLayout cl, int cellX, int cellY) {
        delegateDrawing(cl, cellX, cellY);
        animateScale(/* isAccepting= */ true, mIsHovered);
    }

    public void animateToRest() {
        animateScale(/* isAccepting= */ false, mIsHovered);
    }

    public float getStrokeWidth() {
        return mStrokeWidth;
    }

    protected void setHovered(boolean hovered) {
        animateScale(mIsAccepting, /* isHovered= */ hovered);
    }
}
