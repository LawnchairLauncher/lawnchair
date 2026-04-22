/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.wm.shell.common.suppliers

import java.util.function.Supplier

/**
 * Utility class we can use to test a []Supplier<T>] of any parameters type [T].
 */
class SuppliersUtilsTest {

    companion object {
        /**
         * Allows to check that the object supplied is asserts what in [assertion].
         */
        fun <T> assertSupplierProvidesValue(supplier: Supplier<T>, assertion: (Any?) -> Boolean) {
            assert(assertion(supplier.get())) { "Supplier didn't provided what is expected" }
        }
    }
}
