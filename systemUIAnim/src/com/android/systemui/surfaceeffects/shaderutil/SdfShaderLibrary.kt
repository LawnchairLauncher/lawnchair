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
package com.android.systemui.surfaceeffects.shaderutil

/** Library class that contains 2D signed distance functions. */
class SdfShaderLibrary {
    // language=AGSL
    companion object {
        const val CIRCLE_SDF =
            """
            float sdCircle(vec2 p, float r) {
                return (length(p)-r) / r;
            }

            float circleRing(vec2 p, float radius) {
                float thicknessHalf = radius * 0.25;

                float outerCircle = sdCircle(p, radius + thicknessHalf);
                float innerCircle = sdCircle(p, radius);

                return subtract(outerCircle, innerCircle);
            }
        """

        const val ROUNDED_BOX_SDF =
            """
            float sdRoundedBox(vec2 p, vec2 size, float cornerRadius) {
                size *= 0.5;
                cornerRadius *= 0.5;
                vec2 d = abs(p)-size+cornerRadius;

                float outside = length(max(d, 0.0));
                float inside = min(max(d.x, d.y), 0.0);

                return (outside+inside-cornerRadius)/size.y;
            }

            float roundedBoxRing(vec2 p, vec2 size, float cornerRadius,
                float borderThickness) {
                float outerRoundBox = sdRoundedBox(p, size + vec2(borderThickness),
                    cornerRadius + borderThickness);
                float innerRoundBox = sdRoundedBox(p, size, cornerRadius);
                return subtract(outerRoundBox, innerRoundBox);
            }
        """

        // Used non-trigonometry parametrization and Halley's method (iterative) for root finding.
        // This is more expensive than the regular circle SDF, recommend to use the circle SDF if
        // possible.
        const val ELLIPSE_SDF =
            """float sdEllipse(vec2 p, vec2 wh) {
            wh *= 0.5;

            // symmetry
            (wh.x > wh.y) ? wh = wh.yx, p = abs(p.yx) : p = abs(p);

            vec2 u = wh*p, v = wh*wh;

            float U1 = u.y/2.0;
            float U2 = v.y-v.x;
            float U3 = u.x-U2;
            float U4 = u.x+U2;
            float U5 = 4.0*U1;
            float U6 = 6.0*U1;
            float U7 = 3.0*U3;

            float t = 0.5;
            for (int i = 0; i < 3; i ++) {
                float F1 = t*(t*t*(U1*t+U3)+U4)-U1;
                float F2 = t*t*(U5*t+U7)+U4;
                float F3 = t*(U6*t+U7);

                t += (F1*F2)/(F1*F3-F2*F2);
            }

            t = clamp(t, 0.0, 1.0);

            float d = distance(p, wh*vec2(1.0-t*t,2.0*t)/(t*t+1.0));
            d /= wh.y;

            return (dot(p/wh,p/wh)>1.0) ? d : -d;
        }

        float ellipseRing(vec2 p, vec2 wh) {
            vec2 thicknessHalf = wh * 0.25;

            float outerEllipse = sdEllipse(p, wh + thicknessHalf);
            float innerEllipse = sdEllipse(p, wh);

            return subtract(outerEllipse, innerEllipse);
        }
        """

        const val SHADER_SDF_OPERATION_LIB =
            """
            float soften(float d, float blur) {
                float blurHalf = blur * 0.5;
                return smoothstep(-blurHalf, blurHalf, d);
            }

            float subtract(float outer, float inner) {
                return max(outer, -inner);
            }
        """
    }
}
