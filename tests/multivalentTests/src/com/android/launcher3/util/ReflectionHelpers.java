/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.launcher3.util;

import java.lang.reflect.Field;

public class ReflectionHelpers {

    /**
     * Reflectively get the value of a field.
     *
     * @param object Target object.
     * @param fieldName The field name.
     * @param <R> The return type.
     * @return Value of the field on the object.
     */
    public static <R> R getField(Object object, String fieldName) {
        try {
            Field field = object.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (R) field.get(object);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reflectively set the value of a field.
     *
     * @param object Target object.
     * @param fieldName The field name.
     * @param fieldNewValue New value.
     */
    public static void setField(Object object, String fieldName, Object fieldNewValue) {
        try {
            Field field = object.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(object, fieldNewValue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
