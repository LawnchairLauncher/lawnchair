/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.uioverrides;

import android.content.pm.ShortcutInfo;

import androidx.core.app.Person;

import com.android.launcher3.Utilities;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ApiWrapper {

    public static Person[] getPersons(ShortcutInfo si) {
        if (!Utilities.ATLEAST_Q) return Utilities.EMPTY_PERSON_ARRAY;
//        Person[] persons = si.getPersons();
        Person[] persons = null;
        try {
            Class<?> threadClazz = si.getClass();
            Method method = threadClazz.getMethod("getPersons");
            persons = (Person[]) method.invoke(null);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return persons == null ? Utilities.EMPTY_PERSON_ARRAY : persons;
    }
}
