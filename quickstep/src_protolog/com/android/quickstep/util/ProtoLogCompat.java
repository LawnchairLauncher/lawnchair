/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.quickstep.util;

import android.util.Log;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Reflection bridge for platform ProtoLog APIs that are not present on every framework image. */
public final class ProtoLogCompat {

    private static final String TAG = "ProtoLogCompat";
    private static final Class<?> PROTOLOG_GROUP = findGroupClass();
    private static final Method PROTOLOG_D = findLogMethod("d");
    private static final Method PROTOLOG_E = findLogMethod("e");
    private static final Method PROTOLOG_INIT = findInitMethod();
    private static final Map<Object, Object> GROUP_PROXIES = new ConcurrentHashMap<>();

    private ProtoLogCompat() { }

    public static boolean isAvailable() {
        return PROTOLOG_D != null && PROTOLOG_INIT != null;
    }

    public static void init(Object[] groups) {
        if (PROTOLOG_INIT == null || PROTOLOG_GROUP == null) return;
        try {
            Object proxyGroups = Array.newInstance(PROTOLOG_GROUP, groups.length);
            for (int i = 0; i < groups.length; i++) {
                Array.set(proxyGroups, i, groupProxy(groups[i]));
            }
            PROTOLOG_INIT.invoke(null, proxyGroups);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "Failed to initialize platform ProtoLog", e);
        }
    }

    public static void d(Object group, String message, Object... args) {
        if (PROTOLOG_D == null || PROTOLOG_GROUP == null) return;
        try {
            PROTOLOG_D.invoke(null, groupProxy(group), message, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "Failed to write platform ProtoLog", e);
        }
    }

    public static void e(Object group, String message, Object... args) {
        if (PROTOLOG_E == null || PROTOLOG_GROUP == null) return;
        try {
            PROTOLOG_E.invoke(null, groupProxy(group), message, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "Failed to write platform ProtoLog", e);
        }
    }

    private static Object groupProxy(Object group) {
        return GROUP_PROXIES.computeIfAbsent(group, ProtoLogCompat::newGroupProxy);
    }

    private static Object newGroupProxy(Object group) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return group.toString();
                default:
                    Method target = group.getClass().getMethod(
                            method.getName(), method.getParameterTypes());
                    return target.invoke(group, args);
            }
        };
        return Proxy.newProxyInstance(
                ProtoLogCompat.class.getClassLoader(), new Class<?>[] {PROTOLOG_GROUP}, handler);
    }

    private static Class<?> findGroupClass() {
        try {
            return Class.forName("com.android.internal.protolog.common.IProtoLogGroup");
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static Method findLogMethod(String name) {
        if (PROTOLOG_GROUP == null) return null;
        try {
            Class<?> protoLog = Class.forName("com.android.internal.protolog.ProtoLog");
            return protoLog.getMethod(name, PROTOLOG_GROUP, String.class, Object[].class);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static Method findInitMethod() {
        if (PROTOLOG_GROUP == null) return null;
        try {
            Class<?> protoLog = Class.forName("com.android.internal.protolog.ProtoLog");
            return protoLog.getMethod("init", Array.newInstance(PROTOLOG_GROUP, 0).getClass());
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
