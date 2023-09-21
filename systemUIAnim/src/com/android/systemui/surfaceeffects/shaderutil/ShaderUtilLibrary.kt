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

/** A common utility functions that are used for computing shaders. */
class ShaderUtilLibrary {
    // language=AGSL
    companion object {
        const val SHADER_LIB =
            """
            float triangleNoise(vec2 n) {
                n  = fract(n * vec2(5.3987, 5.4421));
                n += dot(n.yx, n.xy + vec2(21.5351, 14.3137));
                float xy = n.x * n.y;
                // compute in [0..2[ and remap to [-1.0..1.0[
                return fract(xy * 95.4307) + fract(xy * 75.04961) - 1.0;
            }

            const float PI = 3.1415926535897932384626;

            float sparkles(vec2 uv, float t) {
                float n = triangleNoise(uv);
                float s = 0.0;
                for (float i = 0; i < 4; i += 1) {
                    float l = i * 0.01;
                    float h = l + 0.1;
                    float o = smoothstep(n - l, h, n);
                    o *= abs(sin(PI * o * (t + 0.55 * i)));
                    s += o;
                }
                return s;
            }

            vec2 distort(vec2 p, float time, float distort_amount_radial,
                float distort_amount_xy) {
                    float angle = atan(p.y, p.x);
                      return p + vec2(sin(angle * 8 + time * 0.003 + 1.641),
                                cos(angle * 5 + 2.14 + time * 0.00412)) * distort_amount_radial
                         + vec2(sin(p.x * 0.01 + time * 0.00215 + 0.8123),
                                cos(p.y * 0.01 + time * 0.005931)) * distort_amount_xy;
            }

            // Return range [-1, 1].
            vec3 hash(vec3 p) {
                p = fract(p * vec3(.3456, .1234, .9876));
                p += dot(p, p.yxz + 43.21);
                p = (p.xxy + p.yxx) * p.zyx;
                return (fract(sin(p) * 4567.1234567) - .5) * 2.;
            }

            // Skew factors (non-uniform).
            const float SKEW = 0.3333333;  // 1/3
            const float UNSKEW = 0.1666667;  // 1/6

            // Return range roughly [-1,1].
            // It's because the hash function (that returns a random gradient vector) returns
            // different magnitude of vectors. Noise doesn't have to be in the precise range thus
            // skipped normalize.
            float simplex3d(vec3 p) {
                // Skew the input coordinate, so that we get squashed cubical grid
                vec3 s = floor(p + (p.x + p.y + p.z) * SKEW);

                // Unskew back
                vec3 u = s - (s.x + s.y + s.z) * UNSKEW;

                // Unskewed coordinate that is relative to p, to compute the noise contribution
                // based on the distance.
                vec3 c0 = p - u;

                // We have six simplices (in this case tetrahedron, since we are in 3D) that we
                // could possibly in.
                // Here, we are finding the correct tetrahedron (simplex shape), and traverse its
                // four vertices (c0..3) when computing noise contribution.
                // The way we find them is by comparing c0's x,y,z values.
                // For example in 2D, we can find the triangle (simplex shape in 2D) that we are in
                // by comparing x and y values. i.e. x>y lower, x<y, upper triangle.
                // Same applies in 3D.
                //
                // Below indicates the offsets (or offset directions) when c0=(x0,y0,z0)
                // x0>y0>z0: (1,0,0), (1,1,0), (1,1,1)
                // x0>z0>y0: (1,0,0), (1,0,1), (1,1,1)
                // z0>x0>y0: (0,0,1), (1,0,1), (1,1,1)
                // z0>y0>x0: (0,0,1), (0,1,1), (1,1,1)
                // y0>z0>x0: (0,1,0), (0,1,1), (1,1,1)
                // y0>x0>z0: (0,1,0), (1,1,0), (1,1,1)
                //
                // The rule is:
                // * For offset1, set 1 at the max component, otherwise 0.
                // * For offset2, set 0 at the min component, otherwise 1.
                // * For offset3, set 1 for all.
                //
                // Encode x0-y0, y0-z0, z0-x0 in a vec3
                vec3 en = c0 - c0.yzx;
                // Each represents whether x0>y0, y0>z0, z0>x0
                en = step(vec3(0.), en);
                // en.zxy encodes z0>x0, x0>y0, y0>x0
                vec3 offset1 = en * (1. - en.zxy); // find max
                vec3 offset2 = 1. - en.zxy * (1. - en); // 1-(find min)
                vec3 offset3 = vec3(1.);

                vec3 c1 = c0 - offset1 + UNSKEW;
                vec3 c2 = c0 - offset2 + UNSKEW * 2.;
                vec3 c3 = c0 - offset3 + UNSKEW * 3.;

                // Kernel summation: dot(max(0, r^2-d^2))^4, noise contribution)
                //
                // First compute d^2, squared distance to the point.
                vec4 w; // w = max(0, r^2 - d^2))
                w.x = dot(c0, c0);
                w.y = dot(c1, c1);
                w.z = dot(c2, c2);
                w.w = dot(c3, c3);

                // Noise contribution should decay to zero before they cross the simplex boundary.
                // Usually r^2 is 0.5 or 0.6;
                // 0.5 ensures continuity but 0.6 increases the visual quality for the application
                // where discontinuity isn't noticeable.
                w = max(0.6 - w, 0.);

                // Noise contribution from each point.
                vec4 nc;
                nc.x = dot(hash(s), c0);
                nc.y = dot(hash(s + offset1), c1);
                nc.z = dot(hash(s + offset2), c2);
                nc.w = dot(hash(s + offset3), c3);

                nc *= w*w*w*w;

                // Add all the noise contributions.
                // Should multiply by the possible max contribution to adjust the range in [-1,1].
                return dot(vec4(32.), nc);
            }
            """
    }
}
