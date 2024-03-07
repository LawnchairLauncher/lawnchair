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

import android.graphics.Point;

public class FolderPoint {
    public Point coord;
    public char mType;

    public FolderPoint(Point coord, char type) {
        this.coord = coord;
        mType = type;
    }

    /**
     * [A-Z]: Represents a folder and number of icons in the folder is represented by
     * the order of letter in the alphabet, A=2, B=3, C=4 ... etc.
     */
    public int getNumberIconsInside() {
        return (mType - 'A') + 2;
    }
}
