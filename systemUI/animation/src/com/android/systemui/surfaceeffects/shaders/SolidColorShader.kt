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
package com.android.systemui.surfaceeffects.shaders

import android.graphics.RuntimeShader

/** Simply renders a solid color. */
class SolidColorShader(color: Int) : RuntimeShader(SHADER) {
    // language=AGSL
    private companion object {
        private const val SHADER =
            """
                layout(color) uniform vec4 in_color;
                vec4 main(vec2 p) {
                    return in_color;
                }
            """
    }

    init {
        setColorUniform("in_color", color)
    }
}
