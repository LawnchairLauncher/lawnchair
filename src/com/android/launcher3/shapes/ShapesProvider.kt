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
    const val CIRCLE_PATH = "M50 0A50 50,0,1,1,50 100A50 50,0,1,1,50 0"
    const val SQUARE_PATH =
        "M53.689 0.82 L53.689 .82 C67.434 .82 74.306 .82 79.758 2.978 87.649 6.103 93.897 12.351 97.022 20.242 99.18 25.694 99.18 32.566 99.18 46.311 V53.689 C99.18 67.434 99.18 74.306 97.022 79.758 93.897 87.649 87.649 93.897 79.758 97.022 74.306 99.18 67.434 99.18 53.689 99.18 H46.311 C32.566 99.18 25.694 99.18 20.242 97.022 12.351 93.897 6.103 87.649 2.978 79.758 .82 74.306 .82 67.434 .82 53.689 L.82 46.311 C.82 32.566 .82 25.694 2.978 20.242 6.103 12.351 12.351 6.103 20.242 2.978 25.694 .82 32.566 .82 46.311 .82Z"
    const val FOUR_SIDED_COOKIE_PATH =
        "M39.888,4.517C46.338 7.319 53.662 7.319 60.112 4.517L63.605 3C84.733 -6.176 106.176 15.268 97 36.395L95.483 39.888C92.681 46.338 92.681 53.662 95.483 60.112L97 63.605C106.176 84.732 84.733 106.176 63.605 97L60.112 95.483C53.662 92.681 46.338 92.681 39.888 95.483L36.395 97C15.267 106.176 -6.176 84.732 3 63.605L4.517 60.112C7.319 53.662 7.319 46.338 4.517 39.888L3 36.395C -6.176 15.268 15.267 -6.176 36.395 3Z"
    const val SEVEN_SIDED_COOKIE_PATH =
        "M35.209 4.878C36.326 3.895 36.884 3.404 37.397 3.006 44.82 -2.742 55.18 -2.742 62.603 3.006 63.116 3.404 63.674 3.895 64.791 4.878 65.164 5.207 65.351 5.371 65.539 5.529 68.167 7.734 71.303 9.248 74.663 9.932 74.902 9.981 75.147 10.025 75.637 10.113 77.1 10.375 77.831 10.506 78.461 10.66 87.573 12.893 94.032 21.011 94.176 30.412 94.186 31.062 94.151 31.805 94.08 33.293 94.057 33.791 94.045 34.04 94.039 34.285 93.958 37.72 94.732 41.121 96.293 44.18 96.404 44.399 96.522 44.618 96.759 45.056 97.467 46.366 97.821 47.021 98.093 47.611 102.032 56.143 99.727 66.266 92.484 72.24 91.983 72.653 91.381 73.089 90.177 73.961 89.774 74.254 89.572 74.4 89.377 74.548 86.647 76.626 84.477 79.353 83.063 82.483 82.962 82.707 82.865 82.936 82.671 83.395 82.091 84.766 81.8 85.451 81.51 86.033 77.31 94.44 67.977 98.945 58.801 96.994 58.166 96.859 57.451 96.659 56.019 96.259 55.54 96.125 55.3 96.058 55.063 95.998 51.74 95.154 48.26 95.154 44.937 95.998 44.699 96.058 44.46 96.125 43.981 96.259 42.549 96.659 41.834 96.859 41.199 96.994 32.023 98.945 22.69 94.44 18.49 86.033 18.2 85.451 17.909 84.766 17.329 83.395 17.135 82.936 17.038 82.707 16.937 82.483 15.523 79.353 13.353 76.626 10.623 74.548 10.428 74.4 10.226 74.254 9.823 73.961 8.619 73.089 8.017 72.653 7.516 72.24 .273 66.266 -2.032 56.143 1.907 47.611 2.179 47.021 2.533 46.366 3.241 45.056 3.478 44.618 3.596 44.399 3.707 44.18 5.268 41.121 6.042 37.72 5.961 34.285 5.955 34.04 5.943 33.791 5.92 33.293 5.849 31.805 5.814 31.062 5.824 30.412 5.968 21.011 12.427 12.893 21.539 10.66 22.169 10.506 22.9 10.375 24.363 10.113 24.853 10.025 25.098 9.981 25.337 9.932 28.697 9.248 31.833 7.734 34.461 5.529 34.649 5.371 34.836 5.207 35.209 4.878Z"
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
