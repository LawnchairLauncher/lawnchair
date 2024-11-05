/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.plugins.qs;

import android.annotation.NonNull;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.metrics.LogMaker;
import android.service.quicksettings.Tile;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.android.internal.logging.InstanceId;
import com.android.systemui.animation.Expandable;
import com.android.systemui.plugins.annotations.DependsOn;
import com.android.systemui.plugins.annotations.ProvidesInterface;
import com.android.systemui.plugins.qs.QSTile.Callback;
import com.android.systemui.plugins.qs.QSTile.Icon;
import com.android.systemui.plugins.qs.QSTile.State;

import java.util.Objects;
import java.util.function.Supplier;

@ProvidesInterface(version = QSTile.VERSION)
@DependsOn(target = QSIconView.class)
@DependsOn(target = Callback.class)
@DependsOn(target = Icon.class)
@DependsOn(target = State.class)
public interface QSTile {
    int VERSION = 4;

    String getTileSpec();

    boolean isAvailable();
    void setTileSpec(String tileSpec);

    @Deprecated default void clearState() {}
    void refreshState();

    void addCallback(Callback callback);
    void removeCallback(Callback callback);
    void removeCallbacks();

    /**
     * The tile was clicked.
     *
     * @param expandable {@link Expandable} that was clicked.
     */
    void click(@Nullable Expandable expandable);

    /**
     * The tile secondary click was triggered.
     *
     * @param expandable {@link Expandable} that was clicked.
     */
    void secondaryClick(@Nullable Expandable expandable);

    /**
     * The tile was long clicked.
     *
     * @param expandable {@link Expandable} that was clicked.
     */
    void longClick(@Nullable Expandable expandable);

    void userSwitch(int currentUser);

    /**
     * @deprecated not needed as {@link com.android.internal.logging.UiEvent} will use
     * {@link #getMetricsSpec}
     */
    @Deprecated
    int getMetricsCategory();

    void setListening(Object client, boolean listening);
    void setDetailListening(boolean show);

    void destroy();

    CharSequence getTileLabel();

    State getState();

    default LogMaker populate(LogMaker logMaker) {
        return logMaker;
    }

    /**
     * Return a string to be used to identify the tile in UiEvents.
     */
    default String getMetricsSpec() {
        return getClass().getSimpleName();
    }

    /**
     * Return an {@link InstanceId} to be used to identify the tile in UiEvents.
     */
    InstanceId getInstanceId();

    default boolean isTileReady() {
        return false;
    }

    /**
     * Return whether the tile is set to its listening state and therefore receiving updates and
     * refreshes from controllers
     */
    boolean isListening();

    @ProvidesInterface(version = Callback.VERSION)
    interface Callback {
        static final int VERSION = 2;
        void onStateChanged(State state);
    }

    @ProvidesInterface(version = Icon.VERSION)
    public static abstract class Icon {
        public static final int VERSION = 1;
        abstract public Drawable getDrawable(Context context);

        public Drawable getInvisibleDrawable(Context context) {
            return getDrawable(context);
        }

        @Override
        public int hashCode() {
            return Icon.class.hashCode();
        }

        public int getPadding() {
            return 0;
        }

        @Override
        @NonNull
        public String toString() {
            return "Icon";
        }
    }

    @ProvidesInterface(version = State.VERSION)
    public static class State {
        public static final int VERSION = 1;
        public static final int DEFAULT_STATE = Tile.STATE_ACTIVE;

        public Icon icon;
        public Supplier<Icon> iconSupplier;
        public int state = DEFAULT_STATE;
        public CharSequence label;
        @Nullable public CharSequence secondaryLabel;
        public CharSequence contentDescription;
        @Nullable public CharSequence stateDescription;
        public CharSequence dualLabelContentDescription;
        public boolean disabledByPolicy;
        public boolean dualTarget = false;
        public boolean isTransient = false;
        public String expandedAccessibilityClassName;
        public boolean handlesLongClick = true;
        @Nullable
        public Drawable sideViewCustomDrawable;
        public String spec;

        /** Get the state text. */
        public CharSequence getStateText(int arrayResId, Resources resources) {
            if (state == Tile.STATE_UNAVAILABLE || this instanceof QSTile.BooleanState) {
                String[] array = resources.getStringArray(arrayResId);
                return array[state];
            } else {
                return "";
            }
        }

        /** Get the text for secondaryLabel. */
        public CharSequence getSecondaryLabel(CharSequence stateText) {
            // Use a local reference as the value might change from other threads
            CharSequence localSecondaryLabel = secondaryLabel;
            if (TextUtils.isEmpty(localSecondaryLabel)) {
                return stateText;
            }
            return localSecondaryLabel;
        }

        public boolean copyTo(State other) {
            if (other == null) throw new IllegalArgumentException();
            if (!other.getClass().equals(getClass())) throw new IllegalArgumentException();
            final boolean changed = !Objects.equals(other.spec, spec)
                    || !Objects.equals(other.icon, icon)
                    || !Objects.equals(other.iconSupplier, iconSupplier)
                    || !Objects.equals(other.label, label)
                    || !Objects.equals(other.secondaryLabel, secondaryLabel)
                    || !Objects.equals(other.contentDescription, contentDescription)
                    || !Objects.equals(other.stateDescription, stateDescription)
                    || !Objects.equals(other.dualLabelContentDescription,
                            dualLabelContentDescription)
                    || !Objects.equals(other.expandedAccessibilityClassName,
                            expandedAccessibilityClassName)
                    || !Objects.equals(other.disabledByPolicy, disabledByPolicy)
                    || !Objects.equals(other.state, state)
                    || !Objects.equals(other.isTransient, isTransient)
                    || !Objects.equals(other.dualTarget, dualTarget)
                    || !Objects.equals(other.handlesLongClick, handlesLongClick)
                    || !Objects.equals(other.sideViewCustomDrawable, sideViewCustomDrawable);
            other.spec = spec;
            other.icon = icon;
            other.iconSupplier = iconSupplier;
            other.label = label;
            other.secondaryLabel = secondaryLabel;
            other.contentDescription = contentDescription;
            other.stateDescription = stateDescription;
            other.dualLabelContentDescription = dualLabelContentDescription;
            other.expandedAccessibilityClassName = expandedAccessibilityClassName;
            other.disabledByPolicy = disabledByPolicy;
            other.state = state;
            other.dualTarget = dualTarget;
            other.isTransient = isTransient;
            other.handlesLongClick = handlesLongClick;
            other.sideViewCustomDrawable = sideViewCustomDrawable;
            return changed;
        }

        @Override
        public String toString() {
            return toStringBuilder().toString();
        }

        // Used in dumps to determine current state of a tile.
        // This string may be used for CTS testing of tiles, so removing elements is discouraged.
        protected StringBuilder toStringBuilder() {
            final StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append('[');
            sb.append("spec=").append(spec);
            sb.append(",icon=").append(icon);
            sb.append(",iconSupplier=").append(iconSupplier);
            sb.append(",label=").append(label);
            sb.append(",secondaryLabel=").append(secondaryLabel);
            sb.append(",contentDescription=").append(contentDescription);
            sb.append(",stateDescription=").append(stateDescription);
            sb.append(",dualLabelContentDescription=").append(dualLabelContentDescription);
            sb.append(",expandedAccessibilityClassName=").append(expandedAccessibilityClassName);
            sb.append(",disabledByPolicy=").append(disabledByPolicy);
            sb.append(",dualTarget=").append(dualTarget);
            sb.append(",isTransient=").append(isTransient);
            sb.append(",state=").append(state);
            sb.append(",sideViewCustomDrawable=").append(sideViewCustomDrawable);
            return sb.append(']');
        }

        public State copy() {
            State state = new State();
            copyTo(state);
            return state;
        }
    }

    /**
     * Distinguished from [BooleanState] for use-case purposes such as allowing null secondary label
     */
    @ProvidesInterface(version = AdapterState.VERSION)
    class AdapterState extends State {
        public static final int VERSION = 1;
        public boolean value;
        public boolean forceExpandIcon;

        @Override
        public boolean copyTo(State other) {
            final AdapterState o = (AdapterState) other;
            final boolean changed = super.copyTo(other)
                    || o.value != value
                    || o.forceExpandIcon != forceExpandIcon;
            o.value = value;
            o.forceExpandIcon = forceExpandIcon;
            return changed;
        }

        @Override
        protected StringBuilder toStringBuilder() {
            final StringBuilder rt = super.toStringBuilder();
            rt.insert(rt.length() - 1, ",value=" + value);
            rt.insert(rt.length() - 1, ",forceExpandIcon=" + forceExpandIcon);
            return rt;
        }

        @Override
        public State copy() {
            AdapterState state = new AdapterState();
            copyTo(state);
            return state;
        }
    }

    @ProvidesInterface(version = BooleanState.VERSION)
    class BooleanState extends AdapterState {
        public static final int VERSION = 1;

        @Override
        public State copy() {
            BooleanState state = new BooleanState();
            copyTo(state);
            return state;
        }
    }
}
