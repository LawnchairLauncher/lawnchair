/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.InsetsSource.Flags;
import android.view.WindowInsets.Type.InsetsType;

import java.util.Arrays;
import java.util.Objects;

/**
 * Insets provided by a window.
 *
 * The insets frame will by default as the window frame size. If the providers are set, the
 * calculation result based on the source size will be used as the insets frame.
 *
 * The InsetsFrameProvider should be self-contained. Nothing describing the window itself, such as
 * contentInsets, visibleInsets, etc. won't affect the insets providing to other windows when this
 * is set.
 * @hide
 */
public class InsetsFrameProvider implements Parcelable {

    /**
     * Uses the display frame as the source.
     */
    public static final int SOURCE_DISPLAY = 0;

    /**
     * Uses the window bounds as the source.
     */
    public static final int SOURCE_CONTAINER_BOUNDS = 1;

    /**
     * Uses the window frame as the source.
     */
    public static final int SOURCE_FRAME = 2;

    /**
     * Uses {@link #mArbitraryRectangle} as the source.
     */
    public static final int SOURCE_ARBITRARY_RECTANGLE = 3;

    /**
     * Uses the container bounds to which the insets attached as the source.
     * Only use this if the insets is a local insets only applied to the children of the container.
     */
    public static final int SOURCE_ATTACHED_CONTAINER_BOUNDS = 4;

    private final int mId;

    /**
     * The selection of the starting rectangle to be converted into source frame.
     */
    private int mSource = SOURCE_FRAME;

    /**
     * This is used as the source frame only if SOURCE_ARBITRARY_RECTANGLE is applied.
     */
    private Rect mArbitraryRectangle;

    /**
     * Modifies the starting rectangle selected by {@link #mSource}.
     *
     * For example, when the given source frame is (0, 0) - (100, 200), and the insetsSize is null,
     * the source frame will be directly used as the final insets frame. If the insetsSize is set to
     * (0, 0, 0, 50) instead, the insets frame will be a frame starting from the bottom side of the
     * source frame with height of 50, i.e., (0, 150) - (100, 200).
     */
    private Insets mInsetsSize = null;

    /**
     * Various behavioral options/flags. Default is none.
     *
     * @see Flags
     */
    private @Flags int mFlags;

    /**
     * If null, the size set in insetsSize will be applied to all window types. If it contains
     * element of some types, the insets reported to the window with that types will be overridden.
     */
    private InsetsSizeOverride[] mInsetsSizeOverrides = null;

    /**
     * This field, if set, is indicating the insets needs to be at least the given size inside the
     * display cutout safe area. This will be compared to the insets size calculated based on other
     * attributes, and will be applied when this is larger. This is independent of the
     * PRIVATE_FLAG_LAYOUT_SIZE_EXTENDED_BY_CUTOUT in LayoutParams, as this is not going to change
     * the layout of the window, but only change the insets frame. This can be applied to insets
     * calculated based on all three source frames.
     *
     * Be cautious, this will not be in effect for the window types whose insets size is overridden.
     */
    private Insets mMinimalInsetsSizeInDisplayCutoutSafe = null;

    /**
     * Indicates the bounding rectangles within the provided insets frame, in relative coordinates
     * to the source frame.
     */
    private Rect[] mBoundingRects = null;

    /**
     * Creates an InsetsFrameProvider which describes what frame an insets source should have.
     *
     * @param owner the owner of this provider. We might have multiple sources with the same type on
     *              a display, this is used to identify them.
     * @param index the index of this provider. An owner might provide multiple sources with the
     *              same type, this is used to identify them.
     *              The value must be in a range of [0, 2047].
     * @param type the {@link InsetsType}.
     * @see InsetsSource#createId(Object, int, int)
     */
    public InsetsFrameProvider(Object owner, @IntRange(from = 0, to = 2047) int index,
            @InsetsType int type) {
        mId = InsetsSource.createId(owner, index, type);
    }

    /**
     * Returns an unique integer which identifies the insets source.
     */
    public int getId() {
        return mId;
    }

    /**
     * Returns the index specified in {@link #InsetsFrameProvider(IBinder, int, int)}.
     */
    public int getIndex() {
        return InsetsSource.getIndex(mId);
    }

    /**
     * Returns the {@link InsetsType} specified in {@link #InsetsFrameProvider(IBinder, int, int)}.
     */
    public int getType() {
        return InsetsSource.getType(mId);
    }

    public InsetsFrameProvider setSource(int source) {
        mSource = source;
        return this;
    }

    public int getSource() {
        return mSource;
    }

    /** Set the flags of this provider. */
    public InsetsFrameProvider setFlags(@Flags int flags) {
        mFlags = flags;
        return this;
    }

    public InsetsFrameProvider setFlags(@Flags int flags, @Flags int mask) {
        mFlags = (mFlags & ~mask) | (flags & mask);
        return this;
    }

    public @Flags int getFlags() {
        return mFlags;
    }

    public boolean hasFlags(@Flags int mask) {
        return (mFlags & mask) == mask;
    }

    public InsetsFrameProvider setInsetsSize(Insets insetsSize) {
        mInsetsSize = insetsSize;
        return this;
    }

    public Insets getInsetsSize() {
        return mInsetsSize;
    }

    public InsetsFrameProvider setArbitraryRectangle(Rect rect) {
        mArbitraryRectangle = new Rect(rect);
        return this;
    }

    public Rect getArbitraryRectangle() {
        return mArbitraryRectangle;
    }

    public InsetsFrameProvider setInsetsSizeOverrides(InsetsSizeOverride[] insetsSizeOverrides) {
        mInsetsSizeOverrides = insetsSizeOverrides;
        return this;
    }

    public InsetsSizeOverride[] getInsetsSizeOverrides() {
        return mInsetsSizeOverrides;
    }

    public InsetsFrameProvider setMinimalInsetsSizeInDisplayCutoutSafe(
            Insets minimalInsetsSizeInDisplayCutoutSafe) {
        mMinimalInsetsSizeInDisplayCutoutSafe = minimalInsetsSizeInDisplayCutoutSafe;
        return this;
    }

    public Insets getMinimalInsetsSizeInDisplayCutoutSafe() {
        return mMinimalInsetsSizeInDisplayCutoutSafe;
    }

    /**
     * Sets the bounding rectangles within and relative to the source frame.
     */
    public InsetsFrameProvider setBoundingRects(@Nullable Rect[] boundingRects) {
        mBoundingRects = boundingRects == null ? null : boundingRects.clone();
        return this;
    }

    /**
     * Returns the arbitrary bounding rects, or null if none were set.
     */
    @Nullable
    public Rect[] getBoundingRects() {
        return mBoundingRects;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("InsetsFrameProvider: {");
        sb.append("id=#").append(Integer.toHexString(mId));
        sb.append(", index=").append(getIndex());
        sb.append(", type=").append(WindowInsets.Type.toString(getType()));
        sb.append(", source=").append(sourceToString(mSource));
        sb.append(", flags=[").append(InsetsSource.flagsToString(mFlags)).append("]");
        if (mInsetsSize != null) {
            sb.append(", insetsSize=").append(mInsetsSize);
        }
        if (mInsetsSizeOverrides != null) {
            sb.append(", insetsSizeOverrides=").append(Arrays.toString(mInsetsSizeOverrides));
        }
        if (mArbitraryRectangle != null) {
            sb.append(", mArbitraryRectangle=").append(mArbitraryRectangle.toShortString());
        }
        if (mMinimalInsetsSizeInDisplayCutoutSafe != null) {
            sb.append(", mMinimalInsetsSizeInDisplayCutoutSafe=")
                    .append(mMinimalInsetsSizeInDisplayCutoutSafe);
        }
        if (mBoundingRects != null) {
            sb.append(", mBoundingRects=").append(Arrays.toString(mBoundingRects));
        }
        sb.append("}");
        return sb.toString();
    }

    private static String sourceToString(int source) {
        switch (source) {
            case SOURCE_DISPLAY:
                return "DISPLAY";
            case SOURCE_CONTAINER_BOUNDS:
                return "CONTAINER_BOUNDS";
            case SOURCE_FRAME:
                return "FRAME";
            case SOURCE_ARBITRARY_RECTANGLE:
                return "ARBITRARY_RECTANGLE";
            case SOURCE_ATTACHED_CONTAINER_BOUNDS:
                return "ATTACHED_CONTAINER_BOUNDS";
        }
        return "UNDEFINED";
    }

    public InsetsFrameProvider(Parcel in) {
        mId = in.readInt();
        mSource = in.readInt();
        mFlags = in.readInt();
        mInsetsSize = in.readTypedObject(Insets.CREATOR);
        mInsetsSizeOverrides = in.createTypedArray(InsetsSizeOverride.CREATOR);
        mArbitraryRectangle = in.readTypedObject(Rect.CREATOR);
        mMinimalInsetsSizeInDisplayCutoutSafe = in.readTypedObject(Insets.CREATOR);
        mBoundingRects = in.createTypedArray(Rect.CREATOR);
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mId);
        out.writeInt(mSource);
        out.writeInt(mFlags);
        out.writeTypedObject(mInsetsSize, flags);
        out.writeTypedArray(mInsetsSizeOverrides, flags);
        out.writeTypedObject(mArbitraryRectangle, flags);
        out.writeTypedObject(mMinimalInsetsSizeInDisplayCutoutSafe, flags);
        out.writeTypedArray(mBoundingRects, flags);
    }

    public boolean idEquals(InsetsFrameProvider o) {
        return mId == o.mId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final InsetsFrameProvider other = (InsetsFrameProvider) o;
        return mId == other.mId && mSource == other.mSource && mFlags == other.mFlags
                && Objects.equals(mInsetsSize, other.mInsetsSize)
                && Arrays.equals(mInsetsSizeOverrides, other.mInsetsSizeOverrides)
                && Objects.equals(mArbitraryRectangle, other.mArbitraryRectangle)
                && Objects.equals(mMinimalInsetsSizeInDisplayCutoutSafe,
                        other.mMinimalInsetsSizeInDisplayCutoutSafe)
                && Arrays.equals(mBoundingRects, other.mBoundingRects);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mSource, mFlags, mInsetsSize,
                Arrays.hashCode(mInsetsSizeOverrides), mArbitraryRectangle,
                mMinimalInsetsSizeInDisplayCutoutSafe, Arrays.hashCode(mBoundingRects));
    }

    public static final @NonNull Parcelable.Creator<InsetsFrameProvider> CREATOR =
            new Parcelable.Creator<>() {
                @Override
                public InsetsFrameProvider createFromParcel(Parcel in) {
                    return new InsetsFrameProvider(in);
                }

                @Override
                public InsetsFrameProvider[] newArray(int size) {
                    return new InsetsFrameProvider[size];
                }
            };

    /**
     * Class to describe the insets size to be provided to window with specific window type. If not
     * used, same insets size will be sent as instructed in the insetsSize and source.
     *
     * If the insetsSize of given type is set to {@code null}, the insets source frame will be used
     * directly for that window type.
     */
    public static class InsetsSizeOverride implements Parcelable {

        private final int mWindowType;
        private final Insets mInsetsSize;

        protected InsetsSizeOverride(Parcel in) {
            mWindowType = in.readInt();
            mInsetsSize = in.readTypedObject(Insets.CREATOR);
        }

        public InsetsSizeOverride(int windowType, Insets insetsSize) {
            mWindowType = windowType;
            mInsetsSize = insetsSize;
        }
        public int getWindowType() {
            return mWindowType;
        }

        public Insets getInsetsSize() {
            return mInsetsSize;
        }

        public static final Creator<InsetsSizeOverride> CREATOR = new Creator<>() {
            @Override
            public InsetsSizeOverride createFromParcel(Parcel in) {
                return new InsetsSizeOverride(in);
            }

            @Override
            public InsetsSizeOverride[] newArray(int size) {
                return new InsetsSizeOverride[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(mWindowType);
            out.writeTypedObject(mInsetsSize, flags);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(32);
            sb.append("TypedInsetsSize: {");
            sb.append("windowType=").append(ViewDebug.intToString(
                    WindowManager.LayoutParams.class, "type", mWindowType));
            sb.append(", insetsSize=").append(mInsetsSize);
            sb.append("}");
            return sb.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(mWindowType, mInsetsSize);
        }
    }
}
