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

public class CellType {
    // The cells marked by this will be filled by 1x1 widgets and will be ignored when
    // validating
    public static final char IGNORE = 'x';
    // The cells marked by this will be filled by app icons
    public static final char ICON = 'i';
    // The cells marked by FOLDER will be filled by folders with 27 app icons inside
    public static final char FOLDER = 'Z';
    // Empty space
    public static final char EMPTY = '-';
    // Widget that will be saved as "main widget" for easier retrieval
    public static final char MAIN_WIDGET = 'm';
    // Everything else will be consider a widget
}
