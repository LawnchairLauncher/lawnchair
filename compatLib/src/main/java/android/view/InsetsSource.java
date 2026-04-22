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

package android.view;

import static android.view.InsetsSourceProto.ATTACHED_INSETS;
import static android.view.InsetsSourceProto.FRAME;
import static android.view.InsetsSourceProto.TYPE;
import static android.view.InsetsSourceProto.TYPE_NUMBER;
import static android.view.InsetsSourceProto.VISIBLE;
import static android.view.InsetsSourceProto.VISIBLE_FRAME;
import static android.view.WindowInsets.Type.captionBar;
import static android.view.WindowInsets.Type.ime;

import android.annotation.IntDef;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.proto.ProtoOutputStream;
import android.view.WindowInsets.Type.InsetsType;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Represents the state of a single entity generating insets for clients.
 * @hide
 */
public class InsetsSource implements Parcelable {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "SIDE_", value = {
            SIDE_NONE,
            SIDE_LEFT,
            SIDE_TOP,
            SIDE_RIGHT,
            SIDE_BOTTOM,
            SIDE_UNKNOWN
    })
    public @interface InternalInsetsSide {}

    static final int SIDE_NONE = 0;
    static final int SIDE_LEFT = 1;
    static final int SIDE_TOP = 2;
    static final int SIDE_RIGHT = 3;
    static final int SIDE_BOTTOM = 4;
    static final int SIDE_UNKNOWN = 5;

    /** The insets source ID of IME */
    public static final int ID_IME = createId(null, 0, ime());

    /** The insets source ID of the IME caption bar ("fake" IME navigation bar). */
    public static final int ID_IME_CAPTION_BAR =
            InsetsSource.createId(null /* owner */, 1 /* index */, captionBar());

    /**
     * Controls whether this source suppresses the scrim. If the scrim is ignored, the system won't
     * draw a semi-transparent scrim behind the system bar area even when the bar contrast is
     * enforced.
     *
     * @see android.R.styleable#Window_enforceStatusBarContrast
     * @see android.R.styleable#Window_enforceNavigationBarContrast
     */
    public static final int FLAG_SUPPRESS_SCRIM = 1;

    /**
     * Controls whether the insets frame will be used to move {@link RoundedCorner} inward with the
     * insets frame size when calculating the rounded corner insets to other windows.
     *
     * For example, task bar will draw fake rounded corners above itself, so we need to move the
     * rounded corner up by the task bar insets size to make other windows see a rounded corner
     * above the task bar.
     */
    public static final int FLAG_INSETS_ROUNDED_CORNER = 1 << 1;

    /**
     * Controls whether the insets provided by this source should be forcibly consumed.
     */
    public static final int FLAG_FORCE_CONSUMING = 1 << 2;

    /**
     * Controls whether the insets source will play an animation when resizing.
     */
    public static final int FLAG_ANIMATE_RESIZING = 1 << 3;

    /**
     * Controls whether the {@link WindowInsets.Type#captionBar()} insets provided by this source
     * should always be forcibly consumed. Unlike with {@link #FLAG_FORCE_CONSUMING}, when this
     * flag is used the caption bar will be consumed even when the bar is requested to be visible.
     *
     * Note: this flag does not take effect when the window applies
     * {@link WindowInsetsController.Appearance#APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND}.
     */
    public static final int FLAG_FORCE_CONSUMING_OPAQUE_CAPTION_BAR = 1 << 4;

    /**
     * Indicates whether the insets source is valid.
     */
    public static final int FLAG_INVALID = 1 << 5;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, prefix = "FLAG_", value = {
            FLAG_SUPPRESS_SCRIM,
            FLAG_INSETS_ROUNDED_CORNER,
            FLAG_FORCE_CONSUMING,
            FLAG_ANIMATE_RESIZING,
            FLAG_FORCE_CONSUMING_OPAQUE_CAPTION_BAR,
            FLAG_INVALID,
    })
    public @interface Flags {}

    /**
     * Used when there are no bounding rects to describe an inset, which is only possible when the
     * insets itself is {@link Insets#NONE}.
     */
    private static final Rect[] NO_BOUNDING_RECTS = new Rect[0];

    private @Flags int mFlags;

    /**
     * An unique integer to identify this source across processes.
     */
    private final int mId;

    private final @InsetsType int mType;

    /** Frame of the source in screen coordinate space */
    private final Rect mFrame;
    private @Nullable Rect mVisibleFrame;
    private @Nullable Rect[] mBoundingRects;

    // If not null, this will be used to calculate insets based on the container bounds the insets
    // source attached to, and all other frame, including side hints will be ignored.
    private @Nullable Insets mAttachedInsets;

    private boolean mVisible;

    /**
     * Used to decide which side of the relative frame should receive insets when the frame fully
     * covers the relative frame.
     */
    private @InternalInsetsSide int mSideHint = SIDE_NONE;

    private final Rect mTmpFrame = new Rect();
    private final Rect mTmpFrame2 = new Rect();

    public InsetsSource(int id, @InsetsType int type) {
        mId = id;
        mType = type;
        mFrame = new Rect();
        mVisible = (WindowInsets.Type.defaultVisible() & type) != 0;
    }

    public InsetsSource(InsetsSource other) {
        mId = other.mId;
        mType = other.mType;
        mFrame = new Rect(other.mFrame);
        mVisible = other.mVisible;
        mVisibleFrame = other.mVisibleFrame != null
                ? new Rect(other.mVisibleFrame)
                : null;
        mFlags = other.mFlags;
        mSideHint = other.mSideHint;
        mBoundingRects = other.mBoundingRects != null
                ? other.mBoundingRects.clone()
                : null;
        mAttachedInsets = other.mAttachedInsets;
    }

    public void set(InsetsSource other) {
        mFrame.set(other.mFrame);
        mVisible = other.mVisible;
        mVisibleFrame = other.mVisibleFrame != null
                ? new Rect(other.mVisibleFrame)
                : null;
        mFlags = other.mFlags;
        mSideHint = other.mSideHint;
        mBoundingRects = other.mBoundingRects != null
                ? other.mBoundingRects.clone()
                : null;
        mAttachedInsets = other.mAttachedInsets;
    }

    public InsetsSource setFrame(int left, int top, int right, int bottom) {
        mFrame.set(left, top, right, bottom);
        return this;
    }

    public InsetsSource setFrame(Rect frame) {
        mFrame.set(frame);
        return this;
    }

    public InsetsSource setVisibleFrame(@Nullable Rect visibleFrame) {
        mVisibleFrame = visibleFrame != null ? new Rect(visibleFrame) : null;
        return this;
    }

    public InsetsSource setAttachedInsets(@Nullable Insets attachedInsets) {
        mAttachedInsets = attachedInsets;
        return this;
    }

    public InsetsSource setVisible(boolean visible) {
        mVisible = visible;
        return this;
    }

    public InsetsSource setFlags(@Flags int flags) {
        mFlags = flags;
        return this;
    }

    public InsetsSource setFlags(@Flags int flags, @Flags int mask) {
        mFlags = (mFlags & ~mask) | (flags & mask);
        return this;
    }

    /**
     * Updates the side hint which is used to decide which side of the relative frame should receive
     * insets when the frame fully covers the relative frame.
     *
     * @param bounds A rectangle which contains the frame. It will be used to calculate the hint.
     */
    public InsetsSource updateSideHint(Rect bounds) {
        mSideHint = getInsetSide(mAttachedInsets != null
                ? mAttachedInsets
                : calculateArbitraryInsets(bounds, mFrame, true /* ignoreVisibility */));
        return this;
    }

    /**
     * Set the bounding rectangles of this source. They are expected to be relative to the source
     * frame.
     */
    public InsetsSource setBoundingRects(@Nullable Rect[] rects) {
        mBoundingRects = rects != null ? rects.clone() : null;
        return this;
    }

    public int getId() {
        return mId;
    }

    public @InsetsType int getType() {
        return mType;
    }

    public Rect getFrame() {
        return mFrame;
    }

    public @Nullable Rect getVisibleFrame() {
        return mVisibleFrame;
    }

    public boolean isVisible() {
        return mVisible;
    }

    public @Flags int getFlags() {
        return mFlags;
    }

    public boolean hasFlags(int flags) {
        return (mFlags & flags) == flags;
    }

    /**
     * Returns the bounding rectangles of this source.
     */
    public @Nullable Rect[] getBoundingRects() {
        return mBoundingRects;
    }

    public @Nullable Insets getAttachedInsets() {
        return mAttachedInsets;
    }

    /**
     * Calculates the insets this source will cause to a client window.
     *
     * @param relativeFrame The frame to calculate the insets relative to.
     * @param hostBounds the bounds of the host window. Can be none if no local insets with
     *                   attached insets is set.
     * @param ignoreVisibility If true, always reports back insets even if source isn't visible.
     * @return The resulting insets. The contract is that only one side will be occupied by a
     * source.
     */
    public Insets calculateInsets(Rect relativeFrame, @Nullable Rect hostBounds,
            boolean ignoreVisibility) {
        if (mAttachedInsets != null) {
            return calculateAttachedInsets(relativeFrame, hostBounds, ignoreVisibility);
        } else {
            return calculateArbitraryInsets(relativeFrame, mFrame, ignoreVisibility);
        }
    }

    /**
     * Like {@link #calculateInsets(Rect, Rect, boolean)}, but will return visible insets.
     */
    public Insets calculateVisibleInsets(Rect relativeFrame, Rect hostBounds) {
        if (mAttachedInsets != null) {
            return calculateAttachedInsets(relativeFrame, hostBounds,
                    false /* ignoreVisibility */);
        } else {
            return calculateArbitraryInsets(relativeFrame, mVisibleFrame != null
                    ? mVisibleFrame : mFrame, false /* ignoreVisibility */);
        }
    }

    /**
     * Calculates the insets this source will cause to a client window. The insets frame is a given
     * rectangle on a display coordinate system, and the client window frame is also on the same
     * coordinate system.
     *
     * @param relativeFrame The frame to calculate the insets relative to. The client window
     *                      frame.
     * @param frame the frame of the insets to be used during the calculation.
     * @param ignoreVisibility If true, always reports back insets even if source isn't visible.
     * @return The resulting insets. The contract is that only one side will be occupied by a
     * source.
     */
    private Insets calculateArbitraryInsets(Rect relativeFrame, Rect frame,
            boolean ignoreVisibility) {
        if (!ignoreVisibility && !mVisible) {
            return Insets.NONE;
        }

        // During drag-move and drag-resizing, the caption insets position may not get updated
        // before the app frame get updated. To layout the app content correctly during drag events,
        // we always return the insets with the corresponding height covering the top.
        // However, with the "fake" IME navigation bar treated as a caption bar, we return the
        // insets with the corresponding height the bottom.
        if (getType() == WindowInsets.Type.captionBar()) {
            return getId() == ID_IME_CAPTION_BAR
                    ? Insets.of(0, 0, 0, frame.height())
                    : Insets.of(0, frame.height(), 0, 0);
        }
        // Checks for whether there is shared edge with insets for 0-width/height window.
        final boolean hasIntersection = relativeFrame.isEmpty()
                ? getIntersection(frame, relativeFrame, mTmpFrame)
                : mTmpFrame.setIntersect(frame, relativeFrame);
        if (!hasIntersection) {
            return Insets.NONE;
        }

        // TODO: Currently, non-floating IME always intersects at bottom due to issues with cutout.
        // However, we should let the policy decide from the server.
        if (getType() == WindowInsets.Type.ime()) {
            return Insets.of(0, 0, 0, mTmpFrame.height());
        }

        if (mTmpFrame.equals(relativeFrame)) {
            // Covering all sides
            switch (mSideHint) {
                default:
                case SIDE_LEFT:
                    return Insets.of(mTmpFrame.width(), 0, 0, 0);
                case SIDE_TOP:
                    return Insets.of(0, mTmpFrame.height(), 0, 0);
                case SIDE_RIGHT:
                    return Insets.of(0, 0, mTmpFrame.width(), 0);
                case SIDE_BOTTOM:
                    return Insets.of(0, 0, 0, mTmpFrame.height());
            }
        } else if (mTmpFrame.width() == relativeFrame.width()) {
            // Intersecting at top/bottom
            if (mTmpFrame.top == relativeFrame.top) {
                return Insets.of(0, mTmpFrame.height(), 0, 0);
            } else if (mTmpFrame.bottom == relativeFrame.bottom) {
                return Insets.of(0, 0, 0, mTmpFrame.height());
            }
            // TODO: remove when insets are shell-customizable.
            // This is a hack that says "if this is a top-inset (eg statusbar), always apply it
            // to the top". It is used when adjusting primary split for IME.
            if (mTmpFrame.top == 0) {
                return Insets.of(0, mTmpFrame.height(), 0, 0);
            }
        } else if (mTmpFrame.height() == relativeFrame.height()) {
            // Intersecting at left/right
            if (mTmpFrame.left == relativeFrame.left) {
                return Insets.of(mTmpFrame.width(), 0, 0, 0);
            } else if (mTmpFrame.right == relativeFrame.right) {
                return Insets.of(0, 0, mTmpFrame.width(), 0);
            }
        } else {
            // The source doesn't cover the width or the height of relativeFrame, but just parts of
            // them. Here uses mSideHint to decide which side should be inset.
            switch (mSideHint) {
                case SIDE_LEFT:
                    if (mTmpFrame.left == relativeFrame.left) {
                        return Insets.of(mTmpFrame.width(), 0, 0, 0);
                    }
                    break;
                case SIDE_TOP:
                    if (mTmpFrame.top == relativeFrame.top) {
                        return Insets.of(0, mTmpFrame.height(), 0, 0);
                    }
                    break;
                case SIDE_RIGHT:
                    if (mTmpFrame.right == relativeFrame.right) {
                        return Insets.of(0, 0, mTmpFrame.width(), 0);
                    }
                    break;
                case SIDE_BOTTOM:
                    if (mTmpFrame.bottom == relativeFrame.bottom) {
                        return Insets.of(0, 0, 0, mTmpFrame.height());
                    }
                    break;
            }
        }
        return Insets.NONE;
    }

    /**
     * Calculates the insets this source will cause to a client window when the insets is attached
     * to a container.
     *
     * @param relativeFrame The frame to calculate the insets relative to.
     * @param hostBounds the bounds of the container where the insets attached to.
     * @param ignoreVisibility If true, always reports back insets even if source isn't visible.
     * @return The resulting insets. The contract is that only one side will be occupied by a
     * source.
     */
    private Insets calculateAttachedInsets(Rect relativeFrame, Rect hostBounds,
            boolean ignoreVisibility) {
        if (hostBounds == null) {
            throw new IllegalArgumentException("A local relative insets requires the host "
                    + "container bounds to be calculated correctly.");
        }
        if (!ignoreVisibility && !mVisible) {
            return Insets.NONE;
        }
        if (!mAttachedInsets.equals(Insets.NONE)) {
            mTmpFrame2.set(hostBounds);
            mTmpFrame2.inset(mAttachedInsets);
            return mTmpFrame.setIntersect(mTmpFrame2, relativeFrame)
                    ? Insets.of(
                            mTmpFrame.left - relativeFrame.left,
                            mTmpFrame.top - relativeFrame.top,
                            relativeFrame.right - mTmpFrame.right,
                            relativeFrame.bottom - mTmpFrame.bottom)
                    : Insets.NONE;
        }
        return Insets.NONE;
    }

    /**
     * Calculates the bounding rects the source will cause to a client window.
     */
    public @NonNull Rect[] calculateBoundingRects(Rect relativeFrame, boolean ignoreVisibility) {
        if (!ignoreVisibility && !mVisible) {
            return NO_BOUNDING_RECTS;
        }

        final Rect frame = getFrame();
        if (mBoundingRects == null) {
            // No bounding rects set, make a single bounding rect that covers the intersection of
            // the |frame| and the |relativeFrame|. Also make it relative to the window origin.
            return mTmpFrame2.setIntersect(frame, relativeFrame)
                    ? new Rect[]{
                            new Rect(
                                    mTmpFrame2.left - relativeFrame.left,
                                    mTmpFrame2.top - relativeFrame.top,
                                    mTmpFrame2.right - relativeFrame.left,
                                    mTmpFrame2.bottom - relativeFrame.top
                            )
                    }
                    : NO_BOUNDING_RECTS;
        }

        // Special treatment for captionBar inset type. During drag-resizing, the |frame| and
        // |boundingRects| may not get updated as quickly as |relativeFrame|, so just assume the
        // |frame| will always be either at the top or bottom of |relativeFrame|. This means some
        // calculations to make |boundingRects| relative to |relativeFrame| can be skipped or
        // simplified.
        // TODO(b/254128050): remove special treatment.
        if (getType() == WindowInsets.Type.captionBar()) {
            final ArrayList<Rect> validBoundingRects = new ArrayList<>();
            for (final Rect boundingRect : mBoundingRects) {
                // Assume that the caption |frame| and |relativeFrame| perfectly align at the top
                // or bottom, meaning that the provided |boundingRect|, which is relative to the
                // |frame| either is already relative to |relativeFrame| (for top captionBar()), or
                // just needs to be made relative to |relativeFrame| for bottom bars.
                final int frameHeight = frame.height();
                mTmpFrame2.set(boundingRect);
                if (getId() == ID_IME_CAPTION_BAR) {
                    mTmpFrame2.offset(0, relativeFrame.height() - frameHeight);
                }
                validBoundingRects.add(new Rect(mTmpFrame2));
            }
            return validBoundingRects.toArray(new Rect[validBoundingRects.size()]);
        }

        // Regular treatment for non-captionBar inset types.
        final ArrayList<Rect> validBoundingRects = new ArrayList<>();
        for (final Rect boundingRect : mBoundingRects) {
            // |boundingRect| was provided relative to |frame|. Make it absolute to be in the same
            // coordinate system as |frame|.
            final Rect absBoundingRect = new Rect(
                    boundingRect.left + frame.left,
                    boundingRect.top + frame.top,
                    boundingRect.right + frame.left,
                    boundingRect.bottom + frame.top
            );
            // Now find the intersection of that |absBoundingRect| with |relativeFrame|. In other
            // words, whichever part of the bounding rect is inside the window frame.
            if (!mTmpFrame2.setIntersect(absBoundingRect, relativeFrame)) {
                // It's possible for this to be empty if the frame and bounding rects were larger
                // than the |relativeFrame|, such as when a system window is wider than the app
                // window width. Just ignore that rect since it will have no effect on the
                // window insets.
                continue;
            }
            // At this point, |mTmpBoundingRect| is a valid bounding rect located fully inside the
            // window, convert it to be relative to the window so that apps don't need to know the
            // location of the window to understand bounding rects.
            validBoundingRects.add(new Rect(
                    mTmpFrame2.left - relativeFrame.left,
                    mTmpFrame2.top - relativeFrame.top,
                    mTmpFrame2.right - relativeFrame.left,
                    mTmpFrame2.bottom - relativeFrame.top));
        }
        if (validBoundingRects.isEmpty()) {
            return NO_BOUNDING_RECTS;
        }
        return validBoundingRects.toArray(new Rect[validBoundingRects.size()]);
    }

    /**
     * Outputs the intersection of two rectangles. The shared edges will also be counted in the
     * intersection.
     *
     * @param a The first rectangle being intersected with.
     * @param b The second rectangle being intersected with.
     * @param out The rectangle which represents the intersection.
     * @return {@code true} if there is any intersection.
     */
    private static boolean getIntersection(@NonNull Rect a, @NonNull Rect b, @NonNull Rect out) {
        if (a.left <= b.right && b.left <= a.right && a.top <= b.bottom && b.top <= a.bottom) {
            out.left = Math.max(a.left, b.left);
            out.top = Math.max(a.top, b.top);
            out.right = Math.min(a.right, b.right);
            out.bottom = Math.min(a.bottom, b.bottom);
            return true;
        }
        out.setEmpty();
        return false;
    }

    /**
     * Retrieves the side for a certain {@code insets}. It is required that only one field l/t/r/b
     * is set in order that this method returns a meaningful result.
     */
    static @InternalInsetsSide int getInsetSide(Insets insets) {
        if (Insets.NONE.equals(insets)) {
            return SIDE_NONE;
        }
        if (insets.left != 0) {
            return SIDE_LEFT;
        }
        if (insets.top != 0) {
            return SIDE_TOP;
        }
        if (insets.right != 0) {
            return SIDE_RIGHT;
        }
        if (insets.bottom != 0) {
            return SIDE_BOTTOM;
        }
        return SIDE_UNKNOWN;
    }

    static String sideToString(@InternalInsetsSide int side) {
        switch (side) {
            case SIDE_NONE:
                return "NONE";
            case SIDE_LEFT:
                return "LEFT";
            case SIDE_TOP:
                return "TOP";
            case SIDE_RIGHT:
                return "RIGHT";
            case SIDE_BOTTOM:
                return "BOTTOM";
            default:
                return "UNKNOWN:" + side;
        }
    }

    /**
     * Creates an identifier of an {@link InsetsSource}.
     *
     * @param owner An object owned by the owner. Only the owner can modify its own sources.
     * @param index An owner may have multiple sources with the same type. For example, the system
     *              server might have multiple display cutout sources. This is used to identify
     *              which one is which. The value must be in a range of [0, 2047].
     * @param type The {@link InsetsType type} of the source.
     * @return a unique integer as the identifier.
     */
    public static int createId(Object owner, @IntRange(from = 0, to = 2047) int index,
            @InsetsType int type) {
        if (index < 0 || index >= 2048) {
            throw new IllegalArgumentException();
        }
        // owner takes top 16 bits;
        // index takes 11 bits since the 6th bit;
        // type takes bottom 5 bits.
        return ((System.identityHashCode(owner) % (1 << 16)) << 16)
                + (index << 5)
                + WindowInsets.Type.indexOf(type);
    }

    /**
     * Gets the index from the ID.
     *
     * @see #createId(Object, int, int)
     */
    public static int getIndex(int id) {
        //   start: ????????????????***********?????
        // & 65535: 0000000000000000***********?????
        //    >> 5: 000000000000000000000***********
        return (id & 65535) >> 5;
    }

    /**
     * Gets the {@link InsetsType} from the ID.
     *
     * @see #createId(Object, int, int)
     * @see WindowInsets.Type#indexOf(int)
     */
    public static int getType(int id) {
        // start: ???????????????????????????*****
        //  & 31: 000000000000000000000000000*****
        //  1 <<: See WindowInsets.Type#indexOf
        return 1 << (id & 31);
    }

    public static String flagsToString(@Flags int flags) {
        final StringJoiner joiner = new StringJoiner("|");
        if ((flags & FLAG_SUPPRESS_SCRIM) != 0) {
            joiner.add("SUPPRESS_SCRIM");
        }
        if ((flags & FLAG_INSETS_ROUNDED_CORNER) != 0) {
            joiner.add("INSETS_ROUNDED_CORNER");
        }
        if ((flags & FLAG_FORCE_CONSUMING) != 0) {
            joiner.add("FORCE_CONSUMING");
        }
        if ((flags & FLAG_ANIMATE_RESIZING) != 0) {
            joiner.add("ANIMATE_RESIZING");
        }
        if ((flags & FLAG_FORCE_CONSUMING_OPAQUE_CAPTION_BAR) != 0) {
            joiner.add("FORCE_CONSUMING_OPAQUE_CAPTION_BAR");
        }
        if ((flags & FLAG_INVALID) != 0) {
            joiner.add("INVALID");
        }
        return joiner.toString();
    }

    /**
     * Export the state of {@link InsetsSource} into a protocol buffer output stream.
     *
     * @param proto   Stream to write the state to
     * @param fieldId FieldId of InsetsSource as defined in the parent message
     */
    public void dumpDebug(ProtoOutputStream proto, long fieldId) {
        final long token = proto.start(fieldId);
        if (!android.os.Flags.androidOsBuildVanillaIceCream()) {
            // Deprecated since V.
            proto.write(TYPE, WindowInsets.Type.toString(mType));
        }
        mFrame.dumpDebug(proto, FRAME);
        if (mVisibleFrame != null) {
            mVisibleFrame.dumpDebug(proto, VISIBLE_FRAME);
        }
        proto.write(VISIBLE, mVisible);
        proto.write(TYPE_NUMBER, mType);
        if (mAttachedInsets != null) {
            mAttachedInsets.dumpDebug(proto, ATTACHED_INSETS);
        }
        proto.end(token);
    }

    public void dump(String prefix, PrintWriter pw) {
        pw.print(prefix);
        pw.print("InsetsSource id="); pw.print(Integer.toHexString(mId));
        pw.print(" type="); pw.print(WindowInsets.Type.toString(mType));
        if (mAttachedInsets != null) {
            pw.print(" attachedInsets="); pw.print(mAttachedInsets);
        } else {
            pw.print(" frame="); pw.print(mFrame.toShortString());
        }
        if (mVisibleFrame != null) {
            pw.print(" visibleFrame="); pw.print(mVisibleFrame.toShortString());
        }
        pw.print(" visible="); pw.print(mVisible);
        pw.print(" flags="); pw.print(flagsToString(mFlags));
        pw.print(" sideHint="); pw.print(sideToString(mSideHint));
        pw.print(" boundingRects="); pw.print(Arrays.toString(mBoundingRects));
        pw.println();
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return equals(o, false);
    }

    /**
     * @param excludeInvisibleImeFrames If {@link WindowInsets.Type#ime()} frames should be ignored
     *                                  when IME is not visible.
     */
    public boolean equals(@Nullable Object o, boolean excludeInvisibleImeFrames) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InsetsSource that = (InsetsSource) o;

        if (mId != that.mId) return false;
        if (mType != that.mType) return false;
        if (mVisible != that.mVisible) return false;
        if (mFlags != that.mFlags) return false;
        if (mSideHint != that.mSideHint) return false;
        if (excludeInvisibleImeFrames && !mVisible && mType == WindowInsets.Type.ime()) return true;
        if (!Objects.equals(mVisibleFrame, that.mVisibleFrame)) return false;
        if (!mFrame.equals(that.mFrame)) return false;
        return Arrays.equals(mBoundingRects, that.mBoundingRects);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mType, mFrame, mVisibleFrame, mVisible, mFlags, mSideHint,
                Arrays.hashCode(mBoundingRects));
    }

    public InsetsSource(Parcel in) {
        mId = in.readInt();
        mType = in.readInt();
        mFrame = Rect.CREATOR.createFromParcel(in);
        if (in.readInt() != 0) {
            mVisibleFrame = Rect.CREATOR.createFromParcel(in);
        } else {
            mVisibleFrame = null;
        }
        mVisible = in.readBoolean();
        mFlags = in.readInt();
        mSideHint = in.readInt();
        mBoundingRects = in.createTypedArray(Rect.CREATOR);
        if (in.readInt() != 0) {
            mAttachedInsets = Insets.CREATOR.createFromParcel(in);
        } else {
            mAttachedInsets = null;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mId);
        dest.writeInt(mType);
        mFrame.writeToParcel(dest, 0);
        if (mVisibleFrame != null) {
            dest.writeInt(1);
            mVisibleFrame.writeToParcel(dest, 0);
        } else {
            dest.writeInt(0);
        }
        dest.writeBoolean(mVisible);
        dest.writeInt(mFlags);
        dest.writeInt(mSideHint);
        dest.writeTypedArray(mBoundingRects, flags);
        if (mAttachedInsets != null) {
            dest.writeInt(1);
            mAttachedInsets.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
    }

    @Override
    public String toString() {
        return "InsetsSource: {" + Integer.toHexString(mId)
                + " mType=" + WindowInsets.Type.toString(mType)
                + " mFrame=" + mFrame.toShortString()
                + " mAttachedInsets=" + mAttachedInsets
                + " mVisible=" + mVisible
                + " mFlags=" + flagsToString(mFlags)
                + " mSideHint=" + sideToString(mSideHint)
                + " mBoundingRects=" + Arrays.toString(mBoundingRects)
                + "}";
    }

    public static final @NonNull Creator<InsetsSource> CREATOR = new Creator<>() {

        public InsetsSource createFromParcel(Parcel in) {
            return new InsetsSource(in);
        }

        public InsetsSource[] newArray(int size) {
            return new InsetsSource[size];
        }
    };
}
