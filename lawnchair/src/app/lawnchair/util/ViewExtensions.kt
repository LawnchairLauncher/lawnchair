/*
 * Copyright 2022, Lawnchair
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

package app.lawnchair.util

import android.util.Log
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

inline val View.viewAttachedScope: CoroutineScope
    get() {
        var detached = false
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (detached) {
                    Log.e(
                        "ViewExtensions",
                        "view attached after being detached ${this@viewAttachedScope}",
                        Throwable()
                    )
                }
            }

            override fun onViewDetachedFromWindow(v: View) {
                detached = true
                scope.cancel()
            }
        })
        return scope
    }
