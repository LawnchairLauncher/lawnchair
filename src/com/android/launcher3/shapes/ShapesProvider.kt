/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.shapes

import com.android.launcher3.Flags as LauncherFlags
import com.android.launcher3.R
import com.android.systemui.shared.Flags

object ShapesProvider {
    private const val CIRCLE_PATH = "M50 0A50 50,0,1,1,50 100A50 50,0,1,1,50 0"
    private const val SQUARE_PATH =
        "M53.689 0.82 L53.689 .82 C67.434 .82 74.306 .82 79.758 2.978 87.649 6.103 93.897 12.351 97.022 20.242 99.18 25.694 99.18 32.566 99.18 46.311 V53.689 C99.18 67.434 99.18 74.306 97.022 79.758 93.897 87.649 87.649 93.897 79.758 97.022 74.306 99.18 67.434 99.18 53.689 99.18 H46.311 C32.566 99.18 25.694 99.18 20.242 97.022 12.351 93.897 6.103 87.649 2.978 79.758 .82 74.306 .82 67.434 .82 53.689 L.82 46.311 C.82 32.566 .82 25.694 2.978 20.242 6.103 12.351 12.351 6.103 20.242 2.978 25.694 .82 32.566 .82 46.311 .82Z"
    const val FOUR_SIDED_COOKIE_PATH =
        "M39.888,4.517C46.338 7.319 53.662 7.319 60.112 4.517L63.605 3C84.733 -6.176 106.176 15.268 97 36.395L95.483 39.888C92.681 46.338 92.681 53.662 95.483 60.112L97 63.605C106.176 84.732 84.733 106.176 63.605 97L60.112 95.483C53.662 92.681 46.338 92.681 39.888 95.483L36.395 97C15.267 106.176 -6.176 84.732 3 63.605L4.517 60.112C7.319 53.662 7.319 46.338 4.517 39.888L3 36.395C -6.176 15.268 15.267 -6.176 36.395 3Z"
    const val SEVEN_SIDED_COOKIE_PATH =
        "M 35.209 6.878 C 36.326 5.895 36.884 5.404 37.397 5.006 C 44.82 -0.742 55.18 -0.742 62.603 5.006 C 63.116 5.404 63.674 5.895 64.791 6.878 C 65.164 7.207 65.351 7.371 65.539 7.529 C 68.167 9.734 71.303 11.248 74.663 11.932 C 74.902 11.981 75.147 12.025 75.637 12.113 C 77.1 12.375 77.831 12.506 78.461 12.66 C 87.573 14.893 94.032 23.011 94.176 32.412 C 94.186 33.062 94.151 33.805 94.08 35.293 C 94.057 35.791 94.045 36.04 94.039 36.285 C 93.958 39.72 94.732 43.121 96.293 46.18 C 96.404 46.399 96.522 46.618 96.759 47.056 C 97.467 48.366 97.821 49.021 98.093 49.611 C 102.032 58.143 99.727 68.266 92.484 74.24 C 91.983 74.653 91.381 75.089 90.177 75.961 C 89.774 76.254 89.572 76.4 89.377 76.548 C 86.647 78.626 84.477 81.353 83.063 84.483 C 82.962 84.707 82.865 84.936 82.671 85.395 C 82.091 86.766 81.8 87.451 81.51 88.033 C 77.31 96.44 67.977 100.945 58.801 98.994 C 58.166 98.859 57.451 98.659 56.019 98.259 C 55.54 98.125 55.3 98.058 55.063 97.998 C 51.74 97.154 48.26 97.154 44.937 97.998 C 44.699 98.058 44.46 98.125 43.981 98.259 C 42.549 98.659 41.834 98.859 41.199 98.994 C 32.023 100.945 22.69 96.44 18.49 88.033 C 18.2 87.451 17.909 86.766 17.329 85.395 C 17.135 84.936 17.038 84.707 16.937 84.483 C 15.523 81.353 13.353 78.626 10.623 76.548 C 10.428 76.4 10.226 76.254 9.823 75.961 C 8.619 75.089 8.017 74.653 7.516 74.24 C 0.273 68.266 -2.032 58.143 1.907 49.611 C 2.179 49.021 2.533 48.366 3.241 47.056 C 3.478 46.618 3.596 46.399 3.707 46.18 C 5.268 43.121 6.042 39.72 5.961 36.285 C 5.955 36.04 5.943 35.791 5.92 35.293 C 5.849 33.805 5.814 33.062 5.824 32.412 C 5.968 23.011 12.427 14.893 21.539 12.66 C 22.169 12.506 22.9 12.375 24.363 12.113 C 24.853 12.025 25.098 11.981 25.337 11.932 C 28.697 11.248 31.833 9.734 34.461 7.529 C 34.649 7.371 34.836 7.207 35.209 6.878 Z"
    const val ARCH_PATH =
        "M50 0C77.614 0 100 22.386 100 50C100 85.471 100 86.476 99.9 87.321 99.116 93.916 93.916 99.116 87.321 99.9 86.476 100 85.471 100 83.46 100H16.54C14.529 100 13.524 100 12.679 99.9 6.084 99.116 .884 93.916 .1 87.321 0 86.476 0 85.471 0 83.46L0 50C0 22.386 22.386 0 50 0Z"
    const val CIRCLE_KEY = "circle"
    const val SQUARE_KEY = "square"
    const val FOUR_SIDED_COOKIE_KEY = "four_sided_cookie"
    const val SEVEN_SIDED_COOKIE_KEY = "seven_sided_cookie"
    const val ARCH_KEY = "arch"

    val iconShapes: Array<IconShapeModel> =
        if (Flags.newCustomizationPickerUi() && LauncherFlags.enableLauncherIconShapes()) {
            arrayOf(
                IconShapeModel(
                    key = CIRCLE_KEY,
                    titleId = R.string.circle_shape_title,
                    pathString = CIRCLE_PATH,
                ),
                IconShapeModel(
                    key = SQUARE_KEY,
                    titleId = R.string.square_shape_title,
                    pathString = SQUARE_PATH,
                    folderRadiusRatio = 1 / 3f,
                    shapeRadius = 17.33f,
                ),
                IconShapeModel(
                    key = FOUR_SIDED_COOKIE_KEY,
                    titleId = R.string.four_sided_cookie_shape_title,
                    pathString = FOUR_SIDED_COOKIE_PATH,
                    folderRadiusRatio = 1 / 3f,
                    shapeRadius = 13.5f,
                ),
                IconShapeModel(
                    key = SEVEN_SIDED_COOKIE_KEY,
                    titleId = R.string.seven_sided_cookie_shape_title,
                    pathString = SEVEN_SIDED_COOKIE_PATH,
                ),
                IconShapeModel(
                    key = ARCH_KEY,
                    titleId = R.string.arch_shape_title,
                    pathString = ARCH_PATH,
                    shapeRadius = 7.8f,
                    folderRadiusRatio = 1 / 4f,
                ),
            )
        } else {
            arrayOf(
                IconShapeModel(
                    key = CIRCLE_KEY,
                    titleId = R.string.circle_shape_title,
                    pathString = CIRCLE_PATH,
                )
            )
        }
}
