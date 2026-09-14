/*
 * Copyright 2025 Kyant
 * Copyright 2026 Renns Project
 *
 * The AGSL below is vendored from Backdrop's internal Shaders.kt:
 * https://github.com/Kyant0/AndroidLiquidGlass
 * backdrop/src/commonMain/kotlin/com/kyant/backdrop/internal/Shaders.kt
 *
 * Backdrop only exposes these through Compose modifiers. Sora's folders and
 * popups are plain Views, so the shader source is used directly against
 * android.graphics.RuntimeShader instead.
 *
 * Changes from the original: the SDF helper is inlined into the single shader
 * Sora uses, and the Compose-only dispersion and highlight variants are dropped.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.ui.liquid

import org.intellij.lang.annotations.Language

/**
 * Refracts [content] through a rounded rectangle, bending it near the edges the
 * way a thick pane of glass does.
 *
 * Uniforms:
 * - `size`             the pane's size in pixels
 * - `offset`           shifts the SDF origin; 0,0 unless the effect is padded
 * - `cornerRadii`      top-left, top-right, bottom-right, bottom-left
 * - `refractionHeight` how far in from the edge the bending reaches
 * - `refractionAmount` how hard it bends (negative pulls inward)
 * - `depthEffect`      0 flat, 1 adds a centre-outward component
 */
@Language("AGSL")
const val ROUNDED_RECT_REFRACTION_SHADER = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;

float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    return content.eval(refractedCoord);
}
"""
