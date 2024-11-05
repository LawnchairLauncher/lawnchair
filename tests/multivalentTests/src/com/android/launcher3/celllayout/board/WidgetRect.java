/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.launcher3.celllayout.board;

import android.graphics.Rect;

public class WidgetRect {
    public char mType;
    public Rect mBounds;

    public WidgetRect(char type, Rect bounds) {
        this.mType = type;
        this.mBounds = bounds;
    }

    public int getSpanX() {
        return mBounds.right - mBounds.left + 1;
    }

    public int getSpanY() {
        return mBounds.top - mBounds.bottom + 1;
    }

    public int getCellX() {
        return mBounds.left;
    }

    public int getCellY() {
        return mBounds.bottom;
    }

    boolean shouldIgnore() {
        return this.mType == CellType.IGNORE;
    }

    boolean contains(int x, int y) {
        return mBounds.contains(x, y);
    }

    @Override
    public String toString() {
        return "WidgetRect type = " + mType + " x = " + getCellX() + " | y " + getCellY()
                + " xs = " + getSpanX() + " ys = " + getSpanY();
    }
}
