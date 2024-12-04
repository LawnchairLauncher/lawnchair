/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.launcher3;

import android.content.Context;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Workspace items have a fixed height, so we need a way to distribute any unused workspace height.
 *
 * The unused or "extra" height is allocated to three different variable heights:
 * - The space above the workspace
 * - The space between the workspace and hotseat
 * - The space below the hotseat
 */
public class DevicePaddings {

    private static final String DEVICE_PADDINGS = "device-paddings";
    private static final String DEVICE_PADDING = "device-padding";

    private static final String WORKSPACE_TOP_PADDING = "workspaceTopPadding";
    private static final String WORKSPACE_BOTTOM_PADDING = "workspaceBottomPadding";
    private static final String HOTSEAT_BOTTOM_PADDING = "hotseatBottomPadding";

    private static final String TAG = "DevicePaddings";
    private static final boolean DEBUG = false;

    ArrayList<DevicePadding> mDevicePaddings = new ArrayList<>();

    public DevicePaddings(Context context, int devicePaddingId) {
        try (XmlResourceParser parser = context.getResources().getXml(devicePaddingId)) {
            final int depth = parser.getDepth();
            int type;
            while (((type = parser.next()) != XmlPullParser.END_TAG ||
                    parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
                if ((type == XmlPullParser.START_TAG) && DEVICE_PADDINGS.equals(parser.getName())) {
                    final int displayDepth = parser.getDepth();
                    while (((type = parser.next()) != XmlPullParser.END_TAG ||
                            parser.getDepth() > displayDepth)
                            && type != XmlPullParser.END_DOCUMENT) {
                        if ((type == XmlPullParser.START_TAG)
                                && DEVICE_PADDING.equals(parser.getName())) {
                            TypedArray a = context.obtainStyledAttributes(
                                    Xml.asAttributeSet(parser), R.styleable.DevicePadding);
                            int maxWidthPx = a.getDimensionPixelSize(
                                    R.styleable.DevicePadding_maxEmptySpace, 0);
                            a.recycle();

                            PaddingFormula workspaceTopPadding = null;
                            PaddingFormula workspaceBottomPadding = null;
                            PaddingFormula hotseatBottomPadding = null;

                            final int limitDepth = parser.getDepth();
                            while (((type = parser.next()) != XmlPullParser.END_TAG ||
                                    parser.getDepth() > limitDepth)
                                    && type != XmlPullParser.END_DOCUMENT) {
                                AttributeSet attr = Xml.asAttributeSet(parser);
                                if ((type == XmlPullParser.START_TAG)) {
                                    if (WORKSPACE_TOP_PADDING.equals(parser.getName())) {
                                        workspaceTopPadding = new PaddingFormula(context, attr);
                                    } else if (WORKSPACE_BOTTOM_PADDING.equals(parser.getName())) {
                                        workspaceBottomPadding = new PaddingFormula(context, attr);
                                    } else if (HOTSEAT_BOTTOM_PADDING.equals(parser.getName())) {
                                        hotseatBottomPadding = new PaddingFormula(context, attr);
                                    }
                                }
                            }

                            if (workspaceTopPadding == null
                                    || workspaceBottomPadding == null
                                    || hotseatBottomPadding == null) {
                                if (Utilities.IS_DEBUG_DEVICE) {
                                    throw new RuntimeException("DevicePadding missing padding.");
                                }
                            }

                            DevicePadding dp = new DevicePadding(maxWidthPx, workspaceTopPadding,
                                    workspaceBottomPadding, hotseatBottomPadding);
                            if (dp.isValid()) {
                                mDevicePaddings.add(dp);
                            } else {
                                Log.e(TAG, "Invalid device padding found.");
                                if (Utilities.IS_DEBUG_DEVICE) {
                                    throw new RuntimeException("DevicePadding is invalid");
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException | XmlPullParserException e) {
            Log.e(TAG, "Failure parsing device padding layout.", e);
            throw new RuntimeException(e);
        }

        // Sort ascending by maxEmptySpacePx
        mDevicePaddings.sort((sl1, sl2) -> Integer.compare(sl1.maxEmptySpacePx,
                sl2.maxEmptySpacePx));
    }

    public DevicePadding getDevicePadding(int extraSpacePx) {
        for (DevicePadding limit : mDevicePaddings) {
            if (extraSpacePx <= limit.maxEmptySpacePx) {
                return limit;
            }
        }

        return mDevicePaddings.get(mDevicePaddings.size() - 1);
    }

    /**
     * Holds all the formulas to calculate the padding for a particular device based on the
     * amount of extra space.
     */
    public static final class DevicePadding {

        // One for each padding since they can each be off by 1 due to rounding errors.
        private static final int ROUNDING_THRESHOLD_PX = 3;

        private final int maxEmptySpacePx;
        private final PaddingFormula workspaceTopPadding;
        private final PaddingFormula workspaceBottomPadding;
        private final PaddingFormula hotseatBottomPadding;

        public DevicePadding(int maxEmptySpacePx,
                PaddingFormula workspaceTopPadding,
                PaddingFormula workspaceBottomPadding,
                PaddingFormula hotseatBottomPadding) {
            this.maxEmptySpacePx = maxEmptySpacePx;
            this.workspaceTopPadding = workspaceTopPadding;
            this.workspaceBottomPadding = workspaceBottomPadding;
            this.hotseatBottomPadding = hotseatBottomPadding;
        }

        public int getMaxEmptySpacePx() {
            return maxEmptySpacePx;
        }

        public int getWorkspaceTopPadding(int extraSpacePx) {
            return workspaceTopPadding.calculate(extraSpacePx);
        }

        public int getWorkspaceBottomPadding(int extraSpacePx) {
            return workspaceBottomPadding.calculate(extraSpacePx);
        }

        public int getHotseatBottomPadding(int extraSpacePx) {
            return hotseatBottomPadding.calculate(extraSpacePx);
        }

        public boolean isValid() {
            int workspaceTopPadding = getWorkspaceTopPadding(maxEmptySpacePx);
            int workspaceBottomPadding = getWorkspaceBottomPadding(maxEmptySpacePx);
            int hotseatBottomPadding = getHotseatBottomPadding(maxEmptySpacePx);
            int sum = workspaceTopPadding + workspaceBottomPadding + hotseatBottomPadding;
            int diff = Math.abs(sum - maxEmptySpacePx);
            if (DEBUG) {
                Log.d(TAG, "isValid: workspaceTopPadding=" + workspaceTopPadding
                        + ", workspaceBottomPadding=" + workspaceBottomPadding
                        + ", hotseatBottomPadding=" + hotseatBottomPadding
                        + ", sum=" + sum
                        + ", diff=" + diff);
            }
            return diff <= ROUNDING_THRESHOLD_PX;
        }
    }

    /**
     * Used to calculate a padding based on three variables: a, b, and c.
     *
     * Calculation: a * (extraSpace - c) + b
     */
    private static final class PaddingFormula {

        private final float a;
        private final float b;
        private final float c;

        public PaddingFormula(Context context, AttributeSet attrs) {
            TypedArray t = context.obtainStyledAttributes(attrs,
                    R.styleable.DevicePaddingFormula);

            a = getValue(t, R.styleable.DevicePaddingFormula_a);
            b = getValue(t, R.styleable.DevicePaddingFormula_b);
            c = getValue(t, R.styleable.DevicePaddingFormula_c);

            t.recycle();
        }

        public int calculate(int extraSpacePx) {
            if (DEBUG) {
                Log.d(TAG, "a=" + a + " * (" + extraSpacePx + " - " + c + ") + b=" + b);
            }
            return Math.round(a * (extraSpacePx - c) + b);
        }

        private static float getValue(TypedArray a, int index) {
            if (a.getType(index) == TypedValue.TYPE_DIMENSION) {
                return a.getDimensionPixelSize(index, 0);
            } else if (a.getType(index) == TypedValue.TYPE_FLOAT) {
                return a.getFloat(index, 0);
            }
            return 0;
        }

        @Override
        public String toString() {
            return "a=" + a + ", b=" + b + ", c=" + c;
        }
    }
}
