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
package com.android.launcher3.celllayout;

import android.graphics.Point;

import com.android.launcher3.celllayout.board.CellLayoutBoard;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ReorderTestCase {
    public List<CellLayoutBoard> mStart;
    public Point moveMainTo;
    public List<List<CellLayoutBoard>> mEnd;

    public ReorderTestCase(List<CellLayoutBoard> start, Point moveMainTo,
            List<CellLayoutBoard>... end) {
        mStart = start;
        this.moveMainTo = moveMainTo;
        mEnd = Arrays.asList(end);
    }

    public ReorderTestCase(String start, Point moveMainTo, String ... end) {
        mStart = CellLayoutBoard.boardListFromString(start);
        this.moveMainTo = moveMainTo;
        mEnd = Arrays
                .asList(end)
                .stream()
                .map(CellLayoutBoard::boardListFromString)
                .collect(Collectors.toList());
    }
}
