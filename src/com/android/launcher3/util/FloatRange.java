/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.util;

/**
 * A mutable class for describing the range of two int values.
 */
public class FloatRange {

    public float start, end;

    public FloatRange() { }

    public FloatRange(float s, float e) {
        set(s, e);
    }

    public void set(float s, float e) {
        start = s;
        end = e;
    }

    public boolean contains(float value) {
        return value >= start && value <= end;
    }
}
