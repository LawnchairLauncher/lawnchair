/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.window;

import static android.app.ActivityOptions.ANIM_CLIP_REVEAL;
import static android.app.ActivityOptions.ANIM_CUSTOM;
import static android.app.ActivityOptions.ANIM_FROM_STYLE;
import static android.app.ActivityOptions.ANIM_OPEN_CROSS_PROFILE_APPS;
import static android.app.ActivityOptions.ANIM_SCALE_UP;
import static android.app.ActivityOptions.ANIM_SCENE_TRANSITION;
import static android.app.ActivityOptions.ANIM_THUMBNAIL_SCALE_DOWN;
import static android.app.ActivityOptions.ANIM_THUMBNAIL_SCALE_UP;
import static android.app.WindowConfiguration.ROTATION_UNDEFINED;
import static android.view.Display.INVALID_DISPLAY;
import static android.view.WindowManager.LayoutParams.ROTATION_ANIMATION_UNSPECIFIED;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_APPEARING;
import static android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_GOING_AWAY;
import static android.view.WindowManager.TRANSIT_NONE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;
import static android.view.WindowManager.TransitionFlags;
import static android.view.WindowManager.TransitionType;
import static android.view.WindowManager.transitTypeToString;

import android.annotation.ColorInt;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.WindowManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Used to communicate information about what is changing during a transition to a TransitionPlayer.
 *
 * @hide
 */
public final class TransitionInfo implements Parcelable {
    private static final String TAG = "TransitionInfo";

    /**
     * Modes are only a sub-set of all the transit-types since they are per-container
     *
     * @hide
     */
    @IntDef(
            prefix = {"TRANSIT_"},
            value = {
                TRANSIT_NONE,
                TRANSIT_OPEN,
                TRANSIT_CLOSE,
                // Note: to_front/to_back really mean show/hide respectively at the container level.
                TRANSIT_TO_FRONT,
                TRANSIT_TO_BACK,
                TRANSIT_CHANGE
            })
    public @interface TransitionMode {}

    /** No flags */
    public static final int FLAG_NONE = 0;

    /** The container shows the wallpaper behind it. */
    public static final int FLAG_SHOW_WALLPAPER = 1;

    /** The container IS the wallpaper. */
    public static final int FLAG_IS_WALLPAPER = 1 << 1;

    /** The container is translucent. */
    public static final int FLAG_TRANSLUCENT = 1 << 2;

    // TODO: remove when starting-window is moved to Task
    /** The container is the recipient of a transferred starting-window */
    public static final int FLAG_STARTING_WINDOW_TRANSFER_RECIPIENT = 1 << 3;

    /** The container has voice session. */
    public static final int FLAG_IS_VOICE_INTERACTION = 1 << 4;

    /** The container is the display. */
    public static final int FLAG_IS_DISPLAY = 1 << 5;

    /**
     * Only for IS_DISPLAY containers. Is set if the display has system alert windows. This is used
     * to prevent seamless rotation. TODO(b/194540864): Once we can include all windows in
     * transition, then replace this with something like FLAG_IS_SYSTEM_ALERT instead. Then we can
     * do mixed rotations.
     */
    public static final int FLAG_DISPLAY_HAS_ALERT_WINDOWS = 1 << 7;

    /** The container is an input-method window. */
    public static final int FLAG_IS_INPUT_METHOD = 1 << 8;

    /** The container is in a Task with embedded activity. */
    public static final int FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY = 1 << 9;

    /** The container fills its parent Task before and after the transition. */
    public static final int FLAG_FILLS_TASK = 1 << 10;

    /** The container is going to show IME on its task after the transition. */
    public static final int FLAG_WILL_IME_SHOWN = 1 << 11;

    /** The container attaches owner profile thumbnail for cross profile animation. */
    public static final int FLAG_CROSS_PROFILE_OWNER_THUMBNAIL = 1 << 12;

    /** The container attaches work profile thumbnail for cross profile animation. */
    public static final int FLAG_CROSS_PROFILE_WORK_THUMBNAIL = 1 << 13;

    /**
     * Whether the window is covered by an app starting window. This is different from {@link
     * #FLAG_STARTING_WINDOW_TRANSFER_RECIPIENT} which is only set on the Activity window that
     * contains the starting window.
     */
    public static final int FLAG_IS_BEHIND_STARTING_WINDOW = 1 << 14;

    /** This change happened underneath something else. */
    public static final int FLAG_IS_OCCLUDED = 1 << 15;

    /** The container is a system window, excluding wallpaper and input-method. */
    public static final int FLAG_IS_SYSTEM_WINDOW = 1 << 16;

    /** The window was animated by back gesture. */
    public static final int FLAG_BACK_GESTURE_ANIMATED = 1 << 17;

    /** The window should have no animation (by policy). */
    public static final int FLAG_NO_ANIMATION = 1 << 18;

    /** The task is launching behind home. */
    public static final int FLAG_TASK_LAUNCHING_BEHIND = 1 << 19;

    /** The task became the top-most task even if it didn't change visibility. */
    public static final int FLAG_MOVED_TO_TOP = 1 << 20;

    /**
     * This transition must be the only transition when it starts (ie. it must wait for all other
     * transition animations to finish).
     */
    public static final int FLAG_SYNC = 1 << 21;

    /** The first unused bit. This can be used by remotes to attach custom flags to this change. */
    public static final int FLAG_FIRST_CUSTOM = 1 << 22;

    /** The change belongs to a window that won't contain activities. */
    public static final int FLAGS_IS_NON_APP_WINDOW =
            FLAG_IS_WALLPAPER | FLAG_IS_INPUT_METHOD | FLAG_IS_SYSTEM_WINDOW;

    /** @hide */
    @IntDef(
            prefix = {"FLAG_"},
            value = {
                FLAG_NONE,
                FLAG_SHOW_WALLPAPER,
                FLAG_IS_WALLPAPER,
                FLAG_TRANSLUCENT,
                FLAG_STARTING_WINDOW_TRANSFER_RECIPIENT,
                FLAG_IS_VOICE_INTERACTION,
                FLAG_IS_DISPLAY,
                FLAG_DISPLAY_HAS_ALERT_WINDOWS,
                FLAG_IS_INPUT_METHOD,
                FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY,
                FLAG_FILLS_TASK,
                FLAG_WILL_IME_SHOWN,
                FLAG_CROSS_PROFILE_OWNER_THUMBNAIL,
                FLAG_CROSS_PROFILE_WORK_THUMBNAIL,
                FLAG_IS_BEHIND_STARTING_WINDOW,
                FLAG_IS_OCCLUDED,
                FLAG_IS_SYSTEM_WINDOW,
                FLAG_BACK_GESTURE_ANIMATED,
                FLAG_NO_ANIMATION,
                FLAG_TASK_LAUNCHING_BEHIND,
                FLAG_MOVED_TO_TOP,
                FLAG_SYNC,
                FLAG_FIRST_CUSTOM
            })
    public @interface ChangeFlags {}

    private final @TransitionType int mType;
    private @TransitionFlags int mFlags;
    private int mTrack = 0;
    private final ArrayList<Change> mChanges = new ArrayList<>();
    private final ArrayList<Root> mRoots = new ArrayList<>();
    private AnimationOptions mOptions;

    /** This is only a BEST-EFFORT id used for log correlation. DO NOT USE for any real work! */
    private int mDebugId = -1;

    /** @hide */
    public TransitionInfo(@TransitionType int type, @TransitionFlags int flags) {
        mType = type;
        mFlags = flags;
    }

    private TransitionInfo(Parcel in) {
        mType = in.readInt();
        mFlags = in.readInt();
        in.readTypedList(mChanges, Change.CREATOR);
        in.readTypedList(mRoots, Root.CREATOR);
        mOptions = in.readTypedObject(AnimationOptions.CREATOR);
        mDebugId = in.readInt();
        mTrack = in.readInt();
    }

    @Override
    /** @hide */
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mType);
        dest.writeInt(mFlags);
        dest.writeTypedList(mChanges);
        dest.writeTypedList(mRoots, flags);
        dest.writeTypedObject(mOptions, flags);
        dest.writeInt(mDebugId);
        dest.writeInt(mTrack);
    }

    @NonNull
    public static final Creator<TransitionInfo> CREATOR =
            new Creator<TransitionInfo>() {
                @Override
                public TransitionInfo createFromParcel(Parcel in) {
                    return new TransitionInfo(in);
                }

                @Override
                public TransitionInfo[] newArray(int size) {
                    return new TransitionInfo[size];
                }
            };

    @Override
    /** @hide */
    public int describeContents() {
        return 0;
    }

    /**
     * @see #getRoot
     */
    public void addRootLeash(
            int displayId, @NonNull SurfaceControl leash, int offsetLeft, int offsetTop) {
        mRoots.add(new Root(displayId, leash, offsetLeft, offsetTop));
    }

    /**
     * @see #getRoot
     */
    public void addRoot(Root other) {
        mRoots.add(other);
    }

    public void setAnimationOptions(AnimationOptions options) {
        mOptions = options;
    }

    public @TransitionType int getType() {
        return mType;
    }

    public void setFlags(int flags) {
        mFlags = flags;
    }

    public int getFlags() {
        return mFlags;
    }

    /**
     * @return The number of animation roots. Most transitions should have 1, but there may be more
     *     in some cases (such as a transition spanning multiple displays).
     */
    public int getRootCount() {
        return mRoots.size();
    }

    /**
     * @return the transition-root at a specific index.
     */
    @NonNull
    public Root getRoot(int idx) {
        return mRoots.get(idx);
    }

    /**
     * @return the index of the transition-root associated with `displayId` or -1 if not found.
     */
    public int findRootIndex(int displayId) {
        for (int i = 0; i < mRoots.size(); ++i) {
            if (mRoots.get(i).mDisplayId == displayId) {
                return i;
            }
        }
        return -1;
    }

    /**
     * @return a surfacecontrol that can serve as a parent surfacecontrol for all the changing
     *     participants to animate within. This will generally be placed at the highest-z-order
     *     shared ancestor of all participants. While this is non-null, it's possible for the
     *     rootleash to be invalid if the transition is a no-op.
     * @deprecated Use {@link #getRoot} instead. This call assumes there is only one root.
     */
    @Deprecated
    @NonNull
    public SurfaceControl getRootLeash() {
        if (mRoots.isEmpty()) {
            throw new IllegalStateException("Trying to get a root leash from a no-op transition.");
        }
        if (mRoots.size() > 1) {
            android.util.Log.e(
                    TAG, "Assuming one animation root when there are more.", new Throwable());
        }
        return mRoots.get(0).mLeash;
    }

    public AnimationOptions getAnimationOptions() {
        return mOptions;
    }

    /**
     * @return the list of {@link Change}s in this transition. The list is sorted top-to-bottom in Z
     *     (meaning index 0 is the top-most container).
     */
    @NonNull
    public List<Change> getChanges() {
        return mChanges;
    }

    /**
     * @return the Change that a window is undergoing or {@code null} if not directly represented.
     */
    @Nullable
    public Change getChange(@NonNull WindowContainerToken token) {
        for (int i = mChanges.size() - 1; i >= 0; --i) {
            if (token.equals(mChanges.get(i).mContainer)) {
                return mChanges.get(i);
            }
        }
        return null;
    }

    /** Add a {@link Change} to this transition. */
    public void addChange(@NonNull Change change) {
        mChanges.add(change);
    }

    /**
     * Whether this transition contains any changes to the window hierarchy, including keyguard
     * visibility.
     */
    public boolean hasChangesOrSideEffects() {
        return !mChanges.isEmpty()
                || isKeyguardGoingAway()
                || (mFlags & TRANSIT_FLAG_KEYGUARD_APPEARING) != 0;
    }

    /** Whether this transition includes keyguard going away. */
    public boolean isKeyguardGoingAway() {
        return (mFlags & TRANSIT_FLAG_KEYGUARD_GOING_AWAY) != 0;
    }

    /** Gets which animation track this transition should run on. */
    public int getTrack() {
        return mTrack;
    }

    /** Sets which animation track this transition should run on. */
    public void setTrack(int track) {
        mTrack = track;
    }

    /**
     * Set an arbitrary "debug" id for this info. This id will not be used for any "real work", it
     * is just for debugging and logging.
     */
    public void setDebugId(int id) {
        mDebugId = id;
    }

    /** Get the "debug" id of this info. Do NOT use this for real work, only use for debugging. */
    public int getDebugId() {
        return mDebugId;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{id=")
                .append(mDebugId)
                .append(" t=")
                .append(transitTypeToString(mType))
                .append(" f=0x")
                .append(Integer.toHexString(mFlags))
                .append(" trk=")
                .append(mTrack)
                .append(" r=[");
        for (int i = 0; i < mRoots.size(); ++i) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(mRoots.get(i).mDisplayId).append("@").append(mRoots.get(i).mOffset);
        }
        sb.append("] c=[");
        for (int i = 0; i < mChanges.size(); ++i) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(mChanges.get(i));
        }
        sb.append("]}");
        return sb.toString();
    }

    /** Converts a transition mode/action to its string representation. */
    @NonNull
    public static String modeToString(@TransitionMode int mode) {
        switch (mode) {
            case TRANSIT_NONE:
                return "NONE";
            case TRANSIT_OPEN:
                return "OPEN";
            case TRANSIT_CLOSE:
                return "CLOSE";
            case TRANSIT_TO_FRONT:
                return "TO_FRONT";
            case TRANSIT_TO_BACK:
                return "TO_BACK";
            case TRANSIT_CHANGE:
                return "CHANGE";
            default:
                return "<unknown:" + mode + ">";
        }
    }

    /** Converts change flags into a string representation. */
    @NonNull
    public static String flagsToString(@ChangeFlags int flags) {
        if (flags == 0) return "NONE";
        final StringBuilder sb = new StringBuilder();
        if ((flags & FLAG_SHOW_WALLPAPER) != 0) {
            sb.append("SHOW_WALLPAPER");
        }
        if ((flags & FLAG_IS_WALLPAPER) != 0) {
            sb.append("IS_WALLPAPER");
        }
        if ((flags & FLAG_IS_INPUT_METHOD) != 0) {
            sb.append("IS_INPUT_METHOD");
        }
        if ((flags & FLAG_TRANSLUCENT) != 0) {
            sb.append(sb.length() == 0 ? "" : "|").append("TRANSLUCENT");
        }
        if ((flags & FLAG_STARTING_WINDOW_TRANSFER_RECIPIENT) != 0) {
            sb.append(sb.length() == 0 ? "" : "|").append("STARTING_WINDOW_TRANSFER");
        }
        if ((flags & FLAG_IS_VOICE_INTERACTION) != 0) {
            sb.append(sb.length() == 0 ? "" : "|").append("IS_VOICE_INTERACTION");
        }
        if ((flags & FLAG_IS_DISPLAY) != 0) {
            sb.append(sb.length() == 0 ? "" : "|").append("IS_DISPLAY");
        }
        if ((flags & FLAG_DISPLAY_HAS_ALERT_WINDOWS) != 0) {
            sb.append(sb.length() == 0 ? "" : "|").append("DISPLAY_HAS_ALERT_WINDOWS");
        }
        if ((flags & FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY) != 0) {
            sb.append(sb.length() == 0 ? "" : "|").append("IN_TASK_WITH_EMBEDDED_ACTIVITY");
        }
        if ((flags & FLAG_FILLS_TASK) != 0) {
            sb.append(sb.length() == 0 ? "" : "|").append("FILLS_TASK");
        }
        if ((flags & FLAG_IS_BEHIND_STARTING_WINDOW) != 0) {
            sb.append(sb.length() == 0 ? "" : "|").append("IS_BEHIND_STARTING_WINDOW");
        }
        if ((flags & FLAG_IS_OCCLUDED) != 0) {
            sb.append(sb.length() == 0 ? "" : "|").append("IS_OCCLUDED");
        }
        if ((flags & FLAG_IS_SYSTEM_WINDOW) != 0) {
            sb.append(sb.length() == 0 ? "" : "|").append("FLAG_IS_SYSTEM_WINDOW");
        }
        if ((flags & FLAG_BACK_GESTURE_ANIMATED) != 0) {
            sb.append(sb.length() == 0 ? "" : "|").append("FLAG_BACK_GESTURE_ANIMATED");
        }
        if ((flags & FLAG_NO_ANIMATION) != 0) {
            sb.append(sb.length() == 0 ? "" : "|").append("NO_ANIMATION");
        }
        if ((flags & FLAG_TASK_LAUNCHING_BEHIND) != 0) {
            sb.append((sb.length() == 0 ? "" : "|") + "TASK_LAUNCHING_BEHIND");
        }
        if ((flags & FLAG_SYNC) != 0) {
            sb.append((sb.length() == 0 ? "" : "|") + "SYNC");
        }
        if ((flags & FLAG_FIRST_CUSTOM) != 0) {
            sb.append(sb.length() == 0 ? "" : "|").append("FIRST_CUSTOM");
        }
        if ((flags & FLAG_MOVED_TO_TOP) != 0) {
            sb.append(sb.length() == 0 ? "" : "|").append("MOVE_TO_TOP");
        }
        return sb.toString();
    }

    /**
     * Indication that `change` is independent of parents (ie. it has a different type of transition
     * vs. "going along for the ride")
     */
    public static boolean isIndependent(
            @NonNull TransitionInfo.Change change, @NonNull TransitionInfo info) {
        // If the change has no parent (it is root), then it is independent
        if (change.getParent() == null) return true;
        // non-visibility changes will just be folded into the parent change, so they aren't
        // independent either.
        if (change.getMode() == TRANSIT_CHANGE) return false;
        TransitionInfo.Change parentChg = info.getChange(change.getParent());
        while (parentChg != null) {
            // If the parent is a visibility change, it will include the results of all child
            // changes into itself, so none of its children can be independent.
            if (parentChg.getMode() != TRANSIT_CHANGE) return false;
            // If there are no more parents left, then all the parents, so far, have not been
            // visibility changes which means this change is independent.
            if (parentChg.getParent() == null) return true;
            parentChg = info.getChange(parentChg.getParent());
        }
        return false;
    }

    /**
     * Releases temporary-for-animation surfaces referenced by this to potentially free up memory.
     * This includes root-leash and snapshots.
     */
    public void releaseAnimSurfaces() {
        for (int i = mChanges.size() - 1; i >= 0; --i) {
            final Change c = mChanges.get(i);
            if (c.mSnapshot != null) {
                c.mSnapshot.release();
                c.mSnapshot = null;
            }
        }
        for (int i = 0; i < mRoots.size(); ++i) {
            mRoots.get(i).mLeash.release();
        }
    }

    /**
     * Releases ALL the surfaces referenced by this to potentially free up memory. Do NOT use this
     * if the surface-controls get stored and used elsewhere in the process. To just release
     * temporary-for-animation surfaces, use {@link #releaseAnimSurfaces}.
     */
    public void releaseAllSurfaces() {
        releaseAnimSurfaces();
        for (int i = mChanges.size() - 1; i >= 0; --i) {
            mChanges.get(i).getLeash().release();
        }
    }

    /**
     * Updates the callsites of all the surfaces in this transition, which aids in the debugging of
     * lingering surfaces.
     */
    public void setUnreleasedWarningCallSiteForAllSurfaces(String callsite) {
        for (int i = mChanges.size() - 1; i >= 0; --i) {
            mChanges.get(i).getLeash().setUnreleasedWarningCallSite(callsite);
        }
    }

    /**
     * Makes a copy of this as if it were parcel'd and unparcel'd. This implies that surfacecontrol
     * refcounts are incremented which allows the "remote" receiver to release them without breaking
     * the caller's references. Use this only if you need to "send" this to a local function which
     * assumes it is being called from a remote caller.
     */
    public TransitionInfo localRemoteCopy() {
        final TransitionInfo out = new TransitionInfo(mType, mFlags);
        out.mTrack = mTrack;
        out.mDebugId = mDebugId;
        for (int i = 0; i < mChanges.size(); ++i) {
            out.mChanges.add(mChanges.get(i).localRemoteCopy());
        }
        for (int i = 0; i < mRoots.size(); ++i) {
            out.mRoots.add(mRoots.get(i).localRemoteCopy());
        }
        // Doesn't have any native stuff, so no need for actual copy
        out.mOptions = mOptions;
        return out;
    }

    /** Represents the change a WindowContainer undergoes during a transition */
    public static final class Change implements Parcelable {
        private final WindowContainerToken mContainer;
        private WindowContainerToken mParent;
        private WindowContainerToken mLastParent;
        private final SurfaceControl mLeash;
        private @TransitionMode int mMode = TRANSIT_NONE;
        private @ChangeFlags int mFlags = FLAG_NONE;
        private final Rect mStartAbsBounds = new Rect();
        private final Rect mEndAbsBounds = new Rect();
        private final Point mEndRelOffset = new Point();
        private ActivityManager.RunningTaskInfo mTaskInfo = null;
        private boolean mAllowEnterPip;
        private int mStartDisplayId = INVALID_DISPLAY;
        private int mEndDisplayId = INVALID_DISPLAY;
        private @Surface.Rotation int mStartRotation = ROTATION_UNDEFINED;
        private @Surface.Rotation int mEndRotation = ROTATION_UNDEFINED;

        /**
         * The end rotation of the top activity after fixed rotation is finished. If the top
         * activity is not in fixed rotation, it will be {@link ROTATION_UNDEFINED}.
         */
        private @Surface.Rotation int mEndFixedRotation = ROTATION_UNDEFINED;

        private int mRotationAnimation = ROTATION_ANIMATION_UNSPECIFIED;
        private @ColorInt int mBackgroundColor;
        private SurfaceControl mSnapshot = null;
        private float mSnapshotLuma;

        public Change(@Nullable WindowContainerToken container, @NonNull SurfaceControl leash) {
            mContainer = container;
            mLeash = leash;
        }

        private Change(Parcel in) {
            mContainer = in.readTypedObject(WindowContainerToken.CREATOR);
            mParent = in.readTypedObject(WindowContainerToken.CREATOR);
            mLastParent = in.readTypedObject(WindowContainerToken.CREATOR);
            mLeash = new SurfaceControl();
            mLeash.readFromParcel(in);
            mMode = in.readInt();
            mFlags = in.readInt();
            mStartAbsBounds.readFromParcel(in);
            mEndAbsBounds.readFromParcel(in);
            mEndRelOffset.readFromParcel(in);
            mTaskInfo = in.readTypedObject(ActivityManager.RunningTaskInfo.CREATOR);
            mAllowEnterPip = in.readBoolean();
            mStartDisplayId = in.readInt();
            mEndDisplayId = in.readInt();
            mStartRotation = in.readInt();
            mEndRotation = in.readInt();
            mEndFixedRotation = in.readInt();
            mRotationAnimation = in.readInt();
            mBackgroundColor = in.readInt();
            mSnapshot = in.readTypedObject(SurfaceControl.CREATOR);
            mSnapshotLuma = in.readFloat();
        }

        private Change localRemoteCopy() {
            final Change out = new Change(mContainer, new SurfaceControl(mLeash, "localRemote"));
            out.mParent = mParent;
            out.mLastParent = mLastParent;
            out.mMode = mMode;
            out.mFlags = mFlags;
            out.mStartAbsBounds.set(mStartAbsBounds);
            out.mEndAbsBounds.set(mEndAbsBounds);
            out.mEndRelOffset.set(mEndRelOffset);
            out.mTaskInfo = mTaskInfo;
            out.mAllowEnterPip = mAllowEnterPip;
            out.mStartDisplayId = mStartDisplayId;
            out.mEndDisplayId = mEndDisplayId;
            out.mStartRotation = mStartRotation;
            out.mEndRotation = mEndRotation;
            out.mEndFixedRotation = mEndFixedRotation;
            out.mRotationAnimation = mRotationAnimation;
            out.mBackgroundColor = mBackgroundColor;
            out.mSnapshot = mSnapshot != null ? new SurfaceControl(mSnapshot, "localRemote") : null;
            out.mSnapshotLuma = mSnapshotLuma;
            return out;
        }

        /** Sets the parent of this change's container. The parent must be a participant or null. */
        public void setParent(@Nullable WindowContainerToken parent) {
            mParent = parent;
        }

        /**
         * Sets the parent of this change's container before the transition if this change's
         * container is reparented in the transition.
         */
        public void setLastParent(@Nullable WindowContainerToken lastParent) {
            mLastParent = lastParent;
        }

        /** Sets the transition mode for this change */
        public void setMode(@TransitionMode int mode) {
            mMode = mode;
        }

        /** Sets the flags for this change */
        public void setFlags(@ChangeFlags int flags) {
            mFlags = flags;
        }

        /** Sets the bounds this container occupied before the change in screen space */
        public void setStartAbsBounds(@Nullable Rect rect) {
            mStartAbsBounds.set(rect);
        }

        /** Sets the bounds this container will occupy after the change in screen space */
        public void setEndAbsBounds(@Nullable Rect rect) {
            mEndAbsBounds.set(rect);
        }

        /** Sets the offset of this container from its parent surface */
        public void setEndRelOffset(int left, int top) {
            mEndRelOffset.set(left, top);
        }

        /**
         * Sets the taskinfo of this container if this is a task. WARNING: this takes the reference,
         * so don't modify it afterwards.
         */
        public void setTaskInfo(@Nullable ActivityManager.RunningTaskInfo taskInfo) {
            mTaskInfo = taskInfo;
        }

        /** Sets the allowEnterPip flag which represents AppOpsManager check on PiP permission */
        public void setAllowEnterPip(boolean allowEnterPip) {
            mAllowEnterPip = allowEnterPip;
        }

        /** Sets the start and end rotation of this container. */
        public void setDisplayId(int start, int end) {
            mStartDisplayId = start;
            mEndDisplayId = end;
        }

        /** Sets the start and end rotation of this container. */
        public void setRotation(@Surface.Rotation int start, @Surface.Rotation int end) {
            mStartRotation = start;
            mEndRotation = end;
        }

        /** Sets end rotation that top activity will be launched to after fixed rotation. */
        public void setEndFixedRotation(@Surface.Rotation int endFixedRotation) {
            mEndFixedRotation = endFixedRotation;
        }

        /**
         * Sets the app-requested animation type for rotation. Will be one of the
         * ROTATION_ANIMATION_ values in {@link android.view.WindowManager.LayoutParams};
         */
        public void setRotationAnimation(int anim) {
            mRotationAnimation = anim;
        }

        /** Sets the background color of this change's container. */
        public void setBackgroundColor(@ColorInt int backgroundColor) {
            mBackgroundColor = backgroundColor;
        }

        /** Sets a snapshot surface for the "start" state of the container. */
        public void setSnapshot(@Nullable SurfaceControl snapshot, float luma) {
            mSnapshot = snapshot;
            mSnapshotLuma = luma;
        }

        /**
         * @return the container that is changing. May be null if non-remotable (eg. activity)
         */
        @Nullable
        public WindowContainerToken getContainer() {
            return mContainer;
        }

        /**
         * @return the parent of the changing container. This is the parent within the participants,
         *     not necessarily the actual parent.
         */
        @Nullable
        public WindowContainerToken getParent() {
            return mParent;
        }

        /**
         * @return the parent of the changing container before the transition if it is reparented in
         *     the transition. The parent window may not be collected in the transition as a
         *     participant, and it may have been detached from the display. {@code null} if the
         *     changing container has not been reparented in the transition, or if the parent is not
         *     organizable.
         */
        @Nullable
        public WindowContainerToken getLastParent() {
            return mLastParent;
        }

        /**
         * @return which action this change represents.
         */
        public @TransitionMode int getMode() {
            return mMode;
        }

        /**
         * @return the flags for this change.
         */
        public @ChangeFlags int getFlags() {
            return mFlags;
        }

        /** Whether this change contains any of the given change flags. */
        public boolean hasFlags(@ChangeFlags int flags) {
            return (mFlags & flags) != 0;
        }

        /** Whether this change contains all of the given change flags. */
        public boolean hasAllFlags(@ChangeFlags int flags) {
            return (mFlags & flags) == flags;
        }

        /**
         * @return the bounds of the container before the change. It may be empty if the container
         *     is coming into existence.
         */
        @NonNull
        public Rect getStartAbsBounds() {
            return mStartAbsBounds;
        }

        /**
         * @return the bounds of the container after the change. It may be empty if the container is
         *     disappearing.
         */
        @NonNull
        public Rect getEndAbsBounds() {
            return mEndAbsBounds;
        }

        /**
         * @return the offset of the container's surface from its parent surface after the change.
         */
        @NonNull
        public Point getEndRelOffset() {
            return mEndRelOffset;
        }

        /**
         * @return the leash or surface to animate for this container
         */
        @NonNull
        public SurfaceControl getLeash() {
            return mLeash;
        }

        /**
         * @return the task info or null if this isn't a task
         */
        @Nullable
        public ActivityManager.RunningTaskInfo getTaskInfo() {
            return mTaskInfo;
        }

        public boolean getAllowEnterPip() {
            return mAllowEnterPip;
        }

        public int getStartDisplayId() {
            return mStartDisplayId;
        }

        public int getEndDisplayId() {
            return mEndDisplayId;
        }

        @Surface.Rotation
        public int getStartRotation() {
            return mStartRotation;
        }

        @Surface.Rotation
        public int getEndRotation() {
            return mEndRotation;
        }

        @Surface.Rotation
        public int getEndFixedRotation() {
            return mEndFixedRotation;
        }

        /**
         * @return the rotation animation.
         */
        public int getRotationAnimation() {
            return mRotationAnimation;
        }

        /**
         * @return get the background color of this change's container.
         */
        @ColorInt
        public int getBackgroundColor() {
            return mBackgroundColor;
        }

        /**
         * @return a snapshot surface (if applicable).
         */
        @Nullable
        public SurfaceControl getSnapshot() {
            return mSnapshot;
        }

        /**
         * @return the luma calculated for the snapshot surface (if applicable).
         */
        public float getSnapshotLuma() {
            return mSnapshotLuma;
        }

        /** @hide */
        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeTypedObject(mContainer, flags);
            dest.writeTypedObject(mParent, flags);
            dest.writeTypedObject(mLastParent, flags);
            mLeash.writeToParcel(dest, flags);
            dest.writeInt(mMode);
            dest.writeInt(mFlags);
            mStartAbsBounds.writeToParcel(dest, flags);
            mEndAbsBounds.writeToParcel(dest, flags);
            mEndRelOffset.writeToParcel(dest, flags);
            dest.writeTypedObject(mTaskInfo, flags);
            dest.writeBoolean(mAllowEnterPip);
            dest.writeInt(mStartDisplayId);
            dest.writeInt(mEndDisplayId);
            dest.writeInt(mStartRotation);
            dest.writeInt(mEndRotation);
            dest.writeInt(mEndFixedRotation);
            dest.writeInt(mRotationAnimation);
            dest.writeInt(mBackgroundColor);
            dest.writeTypedObject(mSnapshot, flags);
            dest.writeFloat(mSnapshotLuma);
        }

        @NonNull
        public static final Creator<Change> CREATOR =
                new Creator<Change>() {
                    @Override
                    public Change createFromParcel(Parcel in) {
                        return new Change(in);
                    }

                    @Override
                    public Change[] newArray(int size) {
                        return new Change[size];
                    }
                };

        /** @hide */
        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append('{');
            sb.append(mContainer);
            sb.append(" m=");
            sb.append(modeToString(mMode));
            sb.append(" f=");
            sb.append(flagsToString(mFlags));
            if (mParent != null) {
                sb.append(" p=");
                sb.append(mParent);
            }
            if (mLeash != null) {
                sb.append(" leash=");
                sb.append(mLeash);
            }
            sb.append(" sb=");
            sb.append(mStartAbsBounds);
            sb.append(" eb=");
            sb.append(mEndAbsBounds);
            if (mEndRelOffset.x != 0 || mEndRelOffset.y != 0) {
                sb.append(" eo=");
                sb.append(mEndRelOffset);
            }
            sb.append(" d=");
            if (mStartDisplayId != mEndDisplayId) {
                sb.append(mStartDisplayId).append("->");
            }
            sb.append(mEndDisplayId);
            if (mStartRotation != mEndRotation) {
                sb.append(" r=");
                sb.append(mStartRotation);
                sb.append("->");
                sb.append(mEndRotation);
                sb.append(':');
                sb.append(mRotationAnimation);
            }
            if (mEndFixedRotation != ROTATION_UNDEFINED) {
                sb.append(" endFixedRotation=");
                sb.append(mEndFixedRotation);
            }
            if (mSnapshot != null) {
                sb.append(" snapshot=");
                sb.append(mSnapshot);
            }
            if (mLastParent != null) {
                sb.append(" lastParent=");
                sb.append(mLastParent);
            }
            sb.append('}');
            return sb.toString();
        }
    }

    /** Represents animation options during a transition */
    public static final class AnimationOptions implements Parcelable {
        private int mType;
        private int mEnterResId;
        private int mExitResId;
        private boolean mOverrideTaskTransition;
        private String mPackageName;
        private final Rect mTransitionBounds = new Rect();
        private HardwareBuffer mThumbnail;
        private int mAnimations;
        private @ColorInt int mBackgroundColor;
        // Customize activity transition animation
        private CustomActivityTransition mCustomActivityOpenTransition;
        private CustomActivityTransition mCustomActivityCloseTransition;

        private AnimationOptions(int type) {
            mType = type;
        }

        public AnimationOptions(Parcel in) {
            mType = in.readInt();
            mEnterResId = in.readInt();
            mExitResId = in.readInt();
            mBackgroundColor = in.readInt();
            mOverrideTaskTransition = in.readBoolean();
            mPackageName = in.readString();
            mTransitionBounds.readFromParcel(in);
            mThumbnail = in.readTypedObject(HardwareBuffer.CREATOR);
            mAnimations = in.readInt();
            mCustomActivityOpenTransition = in.readTypedObject(CustomActivityTransition.CREATOR);
            mCustomActivityCloseTransition = in.readTypedObject(CustomActivityTransition.CREATOR);
        }

        /** Make basic customized animation for a package */
        public static AnimationOptions makeCommonAnimOptions(String packageName) {
            AnimationOptions options = new AnimationOptions(ANIM_FROM_STYLE);
            options.mPackageName = packageName;
            return options;
        }

        public static AnimationOptions makeAnimOptionsFromLayoutParameters(
                WindowManager.LayoutParams lp) {
            AnimationOptions options = new AnimationOptions(ANIM_FROM_STYLE);
            options.mPackageName = lp.packageName;
            options.mAnimations = lp.windowAnimations;
            return options;
        }

        /** Add customized window animations */
        public void addOptionsFromLayoutParameters(WindowManager.LayoutParams lp) {
            mAnimations = lp.windowAnimations;
        }

        /** Add customized activity animation attributes */
        public void addCustomActivityTransition(
                boolean isOpen, int enterResId, int exitResId, int backgroundColor) {
            CustomActivityTransition customTransition =
                    isOpen ? mCustomActivityOpenTransition : mCustomActivityCloseTransition;
            if (customTransition == null) {
                customTransition = new CustomActivityTransition();
                if (isOpen) {
                    mCustomActivityOpenTransition = customTransition;
                } else {
                    mCustomActivityCloseTransition = customTransition;
                }
            }
            customTransition.addCustomActivityTransition(enterResId, exitResId, backgroundColor);
        }

        public static AnimationOptions makeCustomAnimOptions(
                String packageName,
                int enterResId,
                int exitResId,
                @ColorInt int backgroundColor,
                boolean overrideTaskTransition) {
            AnimationOptions options = new AnimationOptions(ANIM_CUSTOM);
            options.mPackageName = packageName;
            options.mEnterResId = enterResId;
            options.mExitResId = exitResId;
            options.mBackgroundColor = backgroundColor;
            options.mOverrideTaskTransition = overrideTaskTransition;
            return options;
        }

        public static AnimationOptions makeClipRevealAnimOptions(
                int startX, int startY, int width, int height) {
            AnimationOptions options = new AnimationOptions(ANIM_CLIP_REVEAL);
            options.mTransitionBounds.set(startX, startY, startX + width, startY + height);
            return options;
        }

        public static AnimationOptions makeScaleUpAnimOptions(
                int startX, int startY, int width, int height) {
            AnimationOptions options = new AnimationOptions(ANIM_SCALE_UP);
            options.mTransitionBounds.set(startX, startY, startX + width, startY + height);
            return options;
        }

        public static AnimationOptions makeThumbnailAnimOptions(
                HardwareBuffer srcThumb, int startX, int startY, boolean scaleUp) {
            AnimationOptions options =
                    new AnimationOptions(
                            scaleUp ? ANIM_THUMBNAIL_SCALE_UP : ANIM_THUMBNAIL_SCALE_DOWN);
            options.mTransitionBounds.set(startX, startY, startX, startY);
            options.mThumbnail = srcThumb;
            return options;
        }

        public static AnimationOptions makeCrossProfileAnimOptions() {
            AnimationOptions options = new AnimationOptions(ANIM_OPEN_CROSS_PROFILE_APPS);
            return options;
        }

        public static AnimationOptions makeSceneTransitionAnimOptions() {
            AnimationOptions options = new AnimationOptions(ANIM_SCENE_TRANSITION);
            return options;
        }

        public int getType() {
            return mType;
        }

        public int getEnterResId() {
            return mEnterResId;
        }

        public int getExitResId() {
            return mExitResId;
        }

        public @ColorInt int getBackgroundColor() {
            return mBackgroundColor;
        }

        public boolean getOverrideTaskTransition() {
            return mOverrideTaskTransition;
        }

        public String getPackageName() {
            return mPackageName;
        }

        public Rect getTransitionBounds() {
            return mTransitionBounds;
        }

        public HardwareBuffer getThumbnail() {
            return mThumbnail;
        }

        public int getAnimations() {
            return mAnimations;
        }

        /** Return customized activity transition if existed. */
        public CustomActivityTransition getCustomActivityTransition(boolean open) {
            return open ? mCustomActivityOpenTransition : mCustomActivityCloseTransition;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mType);
            dest.writeInt(mEnterResId);
            dest.writeInt(mExitResId);
            dest.writeInt(mBackgroundColor);
            dest.writeBoolean(mOverrideTaskTransition);
            dest.writeString(mPackageName);
            mTransitionBounds.writeToParcel(dest, flags);
            dest.writeTypedObject(mThumbnail, flags);
            dest.writeInt(mAnimations);
            dest.writeTypedObject(mCustomActivityOpenTransition, flags);
            dest.writeTypedObject(mCustomActivityCloseTransition, flags);
        }

        @NonNull
        public static final Creator<AnimationOptions> CREATOR =
                new Creator<AnimationOptions>() {
                    @Override
                    public AnimationOptions createFromParcel(Parcel in) {
                        return new AnimationOptions(in);
                    }

                    @Override
                    public AnimationOptions[] newArray(int size) {
                        return new AnimationOptions[size];
                    }
                };

        /** @hide */
        @Override
        public int describeContents() {
            return 0;
        }

        @NonNull
        private static String typeToString(int mode) {
            switch (mode) {
                case ANIM_CUSTOM:
                    return "ANIM_CUSTOM";
                case ANIM_CLIP_REVEAL:
                    return "ANIM_CLIP_REVEAL";
                case ANIM_SCALE_UP:
                    return "ANIM_SCALE_UP";
                case ANIM_THUMBNAIL_SCALE_UP:
                    return "ANIM_THUMBNAIL_SCALE_UP";
                case ANIM_THUMBNAIL_SCALE_DOWN:
                    return "ANIM_THUMBNAIL_SCALE_DOWN";
                case ANIM_OPEN_CROSS_PROFILE_APPS:
                    return "ANIM_OPEN_CROSS_PROFILE_APPS";
                default:
                    return "<unknown:" + mode + ">";
            }
        }

        @Override
        public String toString() {
            return "{ AnimationOptions type= "
                    + typeToString(mType)
                    + " package="
                    + mPackageName
                    + " override="
                    + mOverrideTaskTransition
                    + " b="
                    + mTransitionBounds
                    + "}";
        }

        /** Customized activity transition. */
        public static class CustomActivityTransition implements Parcelable {
            private int mCustomEnterResId;
            private int mCustomExitResId;
            private int mCustomBackgroundColor;

            /** Returns customize activity animation enter resource id */
            public int getCustomEnterResId() {
                return mCustomEnterResId;
            }

            /** Returns customize activity animation exit resource id */
            public int getCustomExitResId() {
                return mCustomExitResId;
            }

            /** Returns customize activity animation background color */
            public int getCustomBackgroundColor() {
                return mCustomBackgroundColor;
            }

            CustomActivityTransition() {}

            CustomActivityTransition(Parcel in) {
                mCustomEnterResId = in.readInt();
                mCustomExitResId = in.readInt();
                mCustomBackgroundColor = in.readInt();
            }

            /** Add customized activity animation attributes */
            public void addCustomActivityTransition(
                    int enterResId, int exitResId, int backgroundColor) {
                mCustomEnterResId = enterResId;
                mCustomExitResId = exitResId;
                mCustomBackgroundColor = backgroundColor;
            }

            @Override
            public int describeContents() {
                return 0;
            }

            @Override
            public void writeToParcel(Parcel dest, int flags) {
                dest.writeInt(mCustomEnterResId);
                dest.writeInt(mCustomExitResId);
                dest.writeInt(mCustomBackgroundColor);
            }

            @NonNull
            public static final Creator<CustomActivityTransition> CREATOR =
                    new Creator<CustomActivityTransition>() {
                        @Override
                        public CustomActivityTransition createFromParcel(Parcel in) {
                            return new CustomActivityTransition(in);
                        }

                        @Override
                        public CustomActivityTransition[] newArray(int size) {
                            return new CustomActivityTransition[size];
                        }
                    };
        }
    }

    /**
     * An animation root in a transition. There is one of these for each display that contains
     * participants. It will be placed, in z-order, right above the top-most participant and at the
     * same position in the hierarchy. As a result, if all participants are animating within a part
     * of the screen, the root-leash will only be in that part of the screen. In these cases, it's
     * relative position (from the screen) is stored in {@link Root#getOffset}.
     */
    public static final class Root implements Parcelable {
        private final int mDisplayId;
        private final SurfaceControl mLeash;
        private final Point mOffset = new Point();

        public Root(int displayId, @NonNull SurfaceControl leash, int offsetLeft, int offsetTop) {
            mDisplayId = displayId;
            mLeash = leash;
            mOffset.set(offsetLeft, offsetTop);
        }

        private Root(Parcel in) {
            mDisplayId = in.readInt();
            mLeash = new SurfaceControl();
            mLeash.readFromParcel(in);
            mLeash.setUnreleasedWarningCallSite("TransitionInfo.Root");
            mOffset.readFromParcel(in);
        }

        private Root localRemoteCopy() {
            return new Root(
                    mDisplayId, new SurfaceControl(mLeash, "localRemote"), mOffset.x, mOffset.y);
        }

        /**
         * @return the id of the display this root is on.
         */
        public int getDisplayId() {
            return mDisplayId;
        }

        /**
         * @return the root's leash. Surfaces should be parented to this while animating.
         */
        @NonNull
        public SurfaceControl getLeash() {
            return mLeash;
        }

        /**
         * @return the offset (relative to its screen) of the root leash.
         */
        @NonNull
        public Point getOffset() {
            return mOffset;
        }

        /** @hide */
        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeInt(mDisplayId);
            mLeash.writeToParcel(dest, flags);
            mOffset.writeToParcel(dest, flags);
        }

        @NonNull
        public static final Creator<Root> CREATOR =
                new Creator<Root>() {
                    @Override
                    public Root createFromParcel(Parcel in) {
                        return new Root(in);
                    }

                    @Override
                    public Root[] newArray(int size) {
                        return new Root[size];
                    }
                };

        /** @hide */
        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public String toString() {
            return mDisplayId + "@" + mOffset + ":" + mLeash;
        }
    }
}
