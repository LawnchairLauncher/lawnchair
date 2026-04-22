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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.view.WindowManager.TransitionType;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.WindowConfiguration;
import android.content.ComponentName;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.WindowManager;

/**
 * A parcelable filter that can be used for rerouting transitions to a remote. This is a local
 * representation so that the transition system doesn't need to make blocking queries over
 * binder.
 *
 * @hide
 */
public final class TransitionFilter implements Parcelable {

    /** The associated requirement doesn't care about the z-order. */
    public static final int CONTAINER_ORDER_ANY = 0;
    /** The associated requirement only matches the top-most (z-order) container. */
    public static final int CONTAINER_ORDER_TOP = 1;

    /** @hide */
    @IntDef(prefix = { "CONTAINER_ORDER_" }, value = {
            CONTAINER_ORDER_ANY,
            CONTAINER_ORDER_TOP,
    })
    public @interface ContainerOrder {}

    /**
     * When non-null: this is a list of transition types that this filter applies to. This filter
     * will fail for transitions that aren't one of these types.
     */
    @Nullable public @TransitionType int[] mTypeSet = null;

    /** All flags must be set on a transition. */
    public @WindowManager.TransitionFlags int mFlags = 0;

    /** All flags must NOT be set on a transition. */
    public @WindowManager.TransitionFlags int mNotFlags = 0;

    /**
     * A list of required changes. To pass, a transition must meet all requirements.
     */
    @Nullable public Requirement[] mRequirements = null;

    public TransitionFilter() {
    }

    private TransitionFilter(Parcel in) {
        mTypeSet = in.createIntArray();
        mFlags = in.readInt();
        mNotFlags = in.readInt();
        mRequirements = in.createTypedArray(Requirement.CREATOR);
    }

    /** @return true if `info` meets all the requirements to pass this filter. */
    public boolean matches(@NonNull TransitionInfo info) {
        if (mTypeSet != null) {
            // non-null typeset, so make sure info is one of the types.
            boolean typePass = false;
            for (int i = 0; i < mTypeSet.length; ++i) {
                if (info.getType() == mTypeSet[i]) {
                    typePass = true;
                    break;
                }
            }
            if (!typePass) return false;
        }
        if ((info.getFlags() & mFlags) != mFlags) {
            return false;
        }
        if ((info.getFlags() & mNotFlags) != 0) {
            return false;
        }
        // Make sure info meets all of the requirements.
        if (mRequirements != null) {
            for (int i = 0; i < mRequirements.length; ++i) {
                final boolean matches = mRequirements[i].matches(info);
                if (matches == mRequirements[i].mNot) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    /** @hide */
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeIntArray(mTypeSet);
        dest.writeInt(mFlags);
        dest.writeInt(mNotFlags);
        dest.writeTypedArray(mRequirements, flags);
    }

    @NonNull
    public static final Creator<TransitionFilter> CREATOR =
            new Creator<TransitionFilter>() {
                @Override
                public TransitionFilter createFromParcel(Parcel in) {
                    return new TransitionFilter(in);
                }

                @Override
                public TransitionFilter[] newArray(int size) {
                    return new TransitionFilter[size];
                }
            };

    @Override
    /** @hide */
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{types=[");
        if (mTypeSet != null) {
            for (int i = 0; i < mTypeSet.length; ++i) {
                sb.append((i == 0 ? "" : ",") + WindowManager.transitTypeToString(mTypeSet[i]));
            }
        }
        sb.append("] flags=0x" + Integer.toHexString(mFlags));
        sb.append("] notFlags=0x" + Integer.toHexString(mNotFlags));
        sb.append(" checks=[");
        if (mRequirements != null) {
            for (int i = 0; i < mRequirements.length; ++i) {
                sb.append((i == 0 ? "" : ",") + mRequirements[i]);
            }
        }
        return sb.append("]}").toString();
    }

    /**
     * Matches a change that a transition must contain to pass this filter. All requirements in a
     * filter must be met to pass the filter.
     */
    public static final class Requirement implements Parcelable {
        public int mActivityType = ACTIVITY_TYPE_UNDEFINED;

        /** This only matches if the change is independent of its parents. */
        public boolean mMustBeIndependent = true;

        /** If this matches, the parent filter will fail */
        public boolean mNot = false;

        public int[] mModes = null;

        /** Matches only if all the flags here are set on the change. */
        public @TransitionInfo.ChangeFlags int mFlags = 0;

        /** If this needs to be a task. */
        public boolean mMustBeTask = false;

        public @ContainerOrder int mOrder = CONTAINER_ORDER_ANY;
        public ComponentName mTopActivity;
        public IBinder mLaunchCookie;

        /** If non-null, requires the change to specifically have or not-have a custom animation. */
        public Boolean mCustomAnimation = null;
        public IBinder mTaskFragmentToken = null;

        public int mWindowingMode = WINDOWING_MODE_UNDEFINED;

        public Requirement() {
        }

        private Requirement(Parcel in) {
            mActivityType = in.readInt();
            mMustBeIndependent = in.readBoolean();
            mNot = in.readBoolean();
            mModes = in.createIntArray();
            mFlags = in.readInt();
            mMustBeTask = in.readBoolean();
            mOrder = in.readInt();
            mTopActivity = in.readTypedObject(ComponentName.CREATOR);
            mLaunchCookie = in.readStrongBinder();
            // 0: null, 1: false, 2: true
            final int customAnimRaw = in.readInt();
            mCustomAnimation = customAnimRaw == 0 ? null : Boolean.valueOf(customAnimRaw == 2);
            mTaskFragmentToken = in.readStrongBinder();
            mWindowingMode = in.readInt();
        }

        /** Go through changes and find if at-least one change matches this filter */
        boolean matches(@NonNull TransitionInfo info) {
            for (int i = info.getChanges().size() - 1; i >= 0; --i) {
                final TransitionInfo.Change change = info.getChanges().get(i);

                if (mTaskFragmentToken != null
                        && !mTaskFragmentToken.equals(change.getTaskFragmentToken())) {
                    continue;
                }

                if (mMustBeIndependent && !TransitionInfo.isIndependent(change, info)) {
                    // Only look at independent animating windows.
                    continue;
                }
                if (mOrder == CONTAINER_ORDER_TOP && i > 0) {
                    continue;
                }
                if (mActivityType != ACTIVITY_TYPE_UNDEFINED) {
                    if (change.getTaskInfo() == null
                            || change.getTaskInfo().getActivityType() != mActivityType) {
                        continue;
                    }
                }
                if (!matchesTopActivity(change.getTaskInfo(), change.getActivityComponent())) {
                    continue;
                }
                if (mModes != null) {
                    boolean pass = false;
                    for (int m = 0; m < mModes.length; ++m) {
                        if (mModes[m] == change.getMode()) {
                            pass = true;
                            break;
                        }
                    }
                    if (!pass) continue;
                }
                if ((change.getFlags() & mFlags) != mFlags) {
                    continue;
                }
                if (mMustBeTask && change.getTaskInfo() == null) {
                    continue;
                }
                if (!matchesCookie(change.getTaskInfo())) {
                    continue;
                }
                if (mCustomAnimation != null
                        // only applies to activity/task
                        && (change.getTaskInfo() != null
                                || change.getActivityComponent() != null)) {
                    final TransitionInfo.AnimationOptions opts = change.getAnimationOptions();
                    if (opts != null) {
                        boolean canActuallyOverride = change.getTaskInfo() == null
                                || opts.getOverrideTaskTransition();
                        if (mCustomAnimation != canActuallyOverride) {
                            continue;
                        }
                    } else if (mCustomAnimation) {
                        continue;
                    }
                }
                if (mWindowingMode != WINDOWING_MODE_UNDEFINED) {
                    if (change.getTaskInfo() == null
                            || change.getTaskInfo().getWindowingMode() != mWindowingMode) {
                        continue;
                    }
                }
                return true;
            }
            return false;
        }

        private boolean matchesTopActivity(ActivityManager.RunningTaskInfo taskInfo,
                @Nullable ComponentName activityComponent) {
            if (mTopActivity == null) return true;
            if (activityComponent != null) {
                return mTopActivity.equals(activityComponent);
            } else if (taskInfo != null) {
                return mTopActivity.equals(taskInfo.topActivity);
            }
            return false;
        }

        private boolean matchesCookie(ActivityManager.RunningTaskInfo info) {
            if (mLaunchCookie == null) return true;
            if (info == null) return false;
            for (IBinder cookie : info.launchCookies) {
                if (mLaunchCookie.equals(cookie)) {
                    return true;
                }
            }
            return false;
        }

        /** Check if the request matches this filter. It may generate false positives */
        boolean matches(@NonNull TransitionRequestInfo request) {
            // Can't check modes/order since the transition hasn't been built at this point.
            if (mActivityType == ACTIVITY_TYPE_UNDEFINED) return true;
            return request.getTriggerTask() != null
                    && request.getTriggerTask().getActivityType() == mActivityType
                    && matchesTopActivity(request.getTriggerTask(), null /* activityCmp */)
                    && matchesCookie(request.getTriggerTask());
        }

        @Override
        /** @hide */
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeInt(mActivityType);
            dest.writeBoolean(mMustBeIndependent);
            dest.writeBoolean(mNot);
            dest.writeIntArray(mModes);
            dest.writeInt(mFlags);
            dest.writeBoolean(mMustBeTask);
            dest.writeInt(mOrder);
            dest.writeTypedObject(mTopActivity, flags);
            dest.writeStrongBinder(mLaunchCookie);
            int customAnimRaw = mCustomAnimation == null ? 0 : (mCustomAnimation ? 2 : 1);
            dest.writeInt(customAnimRaw);
            dest.writeStrongBinder(mTaskFragmentToken);
            dest.writeInt(mWindowingMode);
        }

        @NonNull
        public static final Creator<Requirement> CREATOR =
                new Creator<Requirement>() {
                    @Override
                    public Requirement createFromParcel(Parcel in) {
                        return new Requirement(in);
                    }

                    @Override
                    public Requirement[] newArray(int size) {
                        return new Requirement[size];
                    }
                };

        @Override
        /** @hide */
        public int describeContents() {
            return 0;
        }

        @Override
        public String toString() {
            StringBuilder out = new StringBuilder();
            out.append('{');
            if (mNot) out.append("NOT ");
            out.append("atype=" + WindowConfiguration.activityTypeToString(mActivityType));
            out.append(" independent=" + mMustBeIndependent);
            out.append(" modes=[");
            if (mModes != null) {
                for (int i = 0; i < mModes.length; ++i) {
                    out.append((i == 0 ? "" : ",") + TransitionInfo.modeToString(mModes[i]));
                }
            }
            out.append("]");
            out.append(" flags=" + TransitionInfo.flagsToString(mFlags));
            out.append(" mustBeTask=" + mMustBeTask);
            out.append(" order=" + containerOrderToString(mOrder));
            out.append(" topActivity=").append(mTopActivity);
            out.append(" launchCookie=").append(mLaunchCookie);
            if (mCustomAnimation != null) {
                out.append(" customAnim=").append(mCustomAnimation.booleanValue());
            }
            if (mTaskFragmentToken != null) {
                out.append(" taskFragmentToken=").append(mTaskFragmentToken);
            }
            out.append(" windowingMode="
                    + WindowConfiguration.windowingModeToString(mWindowingMode));
            out.append("}");
            return out.toString();
        }
    }

    private static String containerOrderToString(int order) {
        switch (order) {
            case CONTAINER_ORDER_ANY: return "ANY";
            case CONTAINER_ORDER_TOP: return "TOP";
        }
        return "UNKNOWN(" + order + ")";
    }
}
