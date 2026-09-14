/*
 * Copyright 2025 Kyant0
 * Copyright 2026 Renns Project
 *
 * Vendored from the Backdrop catalog application:
 * https://github.com/Kyant0/AndroidLiquidGlass
 * app/src/{commonMain,androidMain}/kotlin/com/kyant/backdrop/catalog/utils/Coroutines.kt
 *
 * Required by DampedDragAnimation, which lived in the same package upstream and
 * so referenced it without an import.
 *
 * Changes from the original: the expect/actual pair is collapsed into a single
 * Android declaration, since Sora Launcher is not multiplatform. The delegate is
 * aliased so the call cannot be read as recursing into this function.
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

import kotlinx.coroutines.android.awaitFrame as awaitChoreographerFrame

suspend fun awaitFrame() {
    awaitChoreographerFrame()
}
