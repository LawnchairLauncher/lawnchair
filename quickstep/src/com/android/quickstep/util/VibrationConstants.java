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
package com.android.quickstep.util;

import android.os.VibrationEffect;
import com.android.launcher3.Utilities;

public class VibrationConstants {
    public static final VibrationEffect EFFECT_TEXTURE_TICK = Utilities.ATLEAST_Q ?
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TEXTURE_TICK) : VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE);
}
