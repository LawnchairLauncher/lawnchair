/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.launcher3.compat;

import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectUtils {
    private static final String TAG = "LauncherReflect";

    public static Class getClassForName(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Couldn't find class " + className, e);
            return null;
        }
    }

    public static Method getMethod(Class clazz, String method) {
        try {
            return clazz.getMethod(method);
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Couldn't find methid " + clazz.getName() + " " + method, e);
            return null;
        }
    }

    public static Method getMethod(Class clazz, String method, Class param1) {
        try {
            return clazz.getMethod(method, param1);
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Couldn't find methid " + clazz.getName() + " " + method, e);
            return null;
        }
    }

    public static Method getMethod(Class clazz, String method, Class param1, Class param2) {
        try {
            return clazz.getMethod(method, param1, param2);
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Couldn't find methid " + clazz.getName() + " " + method, e);
            return null;
        }
    }

    public static Method getMethod(Class clazz, String method, Class param1, Class param2,
            Class param3) {
        try {
            return clazz.getMethod(method, param1, param2, param3);
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Couldn't find methid " + clazz.getName() + " " + method, e);
            return null;
        }
    }

    public static Method getMethod(Class clazz, String method, Class param1, Class param2,
            Class param3, Class param4) {
        try {
            return clazz.getMethod(method, param1, param2, param3, param4);
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Couldn't find methid " + clazz.getName() + " " + method, e);
            return null;
        }
    }

    public static Object invokeMethod(Object object, Method method) {
        try {
            return method.invoke(object);
        } catch (SecurityException e) {
            Log.e(TAG, "Couldn't invoke method " + method, e);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Couldn't invoke method " + method, e);
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Couldn't invoke method " + method, e);
        } catch (InvocationTargetException e) {
            Log.e(TAG, "Couldn't invoke method " + method, e);
        }
        return null;
    }

    public static Object invokeMethod(Object object, Method method, Object param1) {
        try {
            return method.invoke(object, param1);
        } catch (SecurityException e) {
            Log.e(TAG, "Couldn't invoke method " + method, e);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Couldn't invoke method " + method, e);
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Couldn't invoke method " + method, e);
        } catch (InvocationTargetException e) {
            Log.e(TAG, "Couldn't invoke method " + method, e);
        }
        return null;
    }

    public static Object invokeMethod(Object object, Method method, Object param1, Object param2) {
        try {
            return method.invoke(object, param1, param2);
        } catch (SecurityException e) {
            Log.e(TAG, "Couldn't invoke method " + method, e);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Couldn't invoke method " + method, e);
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Couldn't invoke method " + method, e);
        } catch (InvocationTargetException e) {
            Log.e(TAG, "Couldn't invoke method " + method, e);
        }
        return null;
    }

    public static Object invokeMethod(Object object, Method method, Object param1, Object param2,
            Object param3) {
        try {
            return method.invoke(object, param1, param2, param3);
        } catch (SecurityException e) {
            Log.e(TAG, "Couldn't invoke method " + method, e);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Couldn't invoke method " + method, e);
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Couldn't invoke method " + method, e);
        } catch (InvocationTargetException e) {
            Log.e(TAG, "Couldn't invoke method " + method, e);
        }
        return null;
    }

    public static Object invokeMethod(Object object, Method method, Object param1, Object param2,
            Object param3, Object param4) {
        try {
            return method.invoke(object, param1, param2, param3, param4);
        } catch (SecurityException e) {
            Log.e(TAG, "Couldn't invoke method " + method, e);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Couldn't invoke method " + method, e);
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Couldn't invoke method " + method, e);
        } catch (InvocationTargetException e) {
            Log.e(TAG, "Couldn't invoke method " + method, e);
        }
        return null;
    }
}