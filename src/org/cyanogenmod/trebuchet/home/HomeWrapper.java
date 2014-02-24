/*
 * Copyright (C) 2014 The CyanogenMod Project
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

package org.cyanogenmod.trebuchet.home;

import android.content.Context;
import android.util.Base64;
import android.util.SparseArray;
import android.view.View;

import com.android.launcher.home.Home;

import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HomeWrapper {

    private static final int M_ID_ONSTART = 0;
    private static final int M_ID_ONDESTROY = 1;
    private static final int M_ID_ONRESUME = 2;
    private static final int M_ID_ONPAUSE = 3;
    private static final int M_ID_ONSHOW = 4;
    private static final int M_ID_ONSCROLLPROGRESSCHANGED = 5;
    private static final int M_ID_ONHIDE = 6;
    private static final int M_ID_ONINVALIDATE = 7;
    private static final int M_ID_ONREQUESTSEARCH = 8;
    private static final int M_ID_CREATECUSTOMVIEW = 9;
    private static final int M_ID_GETNAME = 10;
    private static final int M_ID_GETNOTIFICATIONFLAGS = 11;
    private static final int M_ID_GETOPERATIONFLAGS = 12;
    private static final int M_LAST_ID = M_ID_GETOPERATIONFLAGS + 1;

    private final Context mContext;
    private final Class<?> mClass;
    private final Object mInstance;

    private final SparseArray<Method> cachedMethods;

    private final int mNotificationFlags;
    private final int mOperationFlags;

    public HomeWrapper(Context context, Class<?> cls, Object instance) throws SecurityException {
        super();
        mContext = context;
        mClass = cls;
        mInstance = instance;
        cachedMethods = new SparseArray<Method>(M_LAST_ID);

        final String sha1 = createDigest(cls);
        if (!sha1.equals(Home.SIGNATURE)) {
            throw new SecurityException("The remote Home app doesn't implement " +
                    "the current Home Host Protocol. Signature: " + sha1);
        }

        // Obtain the app flags
        mNotificationFlags = getNotificationFlags();
        mOperationFlags = getOperationFlags();
    }

    /** @see Home#onStart(Context) **/
    public void onStart() {
        invokeVoidContextMethod(M_ID_ONSTART, "onStart");
    }

    /** @see Home#onDestroy(Context) **/
    public void onDestroy() {
        invokeVoidContextMethod(M_ID_ONDESTROY, "onDestroy");
    }

    /** @see Home#onResume(Context) **/
    public void onResume() {
        if (!isNotificationSupported(Home.FLAG_NOTIFY_ON_RESUME)) {
            return;
        }
        invokeVoidContextMethod(M_ID_ONRESUME, "onResume");
    }

    /** @see Home#onPause(Context) **/
    public void onPause() {
        if (!isNotificationSupported(Home.FLAG_NOTIFY_ON_PAUSE)) {
            return;
        }
        invokeVoidContextMethod(M_ID_ONPAUSE, "onPause");
    }

    /** @see Home#onShow(Context) **/
    public void onShow() {
        if (!isNotificationSupported(Home.FLAG_NOTIFY_ON_SHOW)) {
            return;
        }
        invokeVoidContextMethod(M_ID_ONSHOW, "onShow");
    }

    /** @see Home#onScrollProgressChanged(Context, float) **/
    public void onScrollProgressChanged(float progress) {
        if (!isNotificationSupported(Home.FLAG_NOTIFY_ON_SCROLL_PROGRESS_CHANGED)) {
            return;
        }
        try {
            Method method = cachedMethods.get(M_ID_ONSCROLLPROGRESSCHANGED);
            if (method == null) {
                method = mClass.getMethod("onScrollProgressChanged", Context.class, float.class);
            }
            method.invoke(mInstance, mContext, progress);
        } catch (ReflectiveOperationException ex) {
            throw new SecurityException(ex);
        }
    }

    /** @see Home#onHide(Context) **/
    public void onHide() {
        if (!isNotificationSupported(Home.FLAG_NOTIFY_ON_HIDE)) {
            return;
        }
        invokeVoidContextMethod(M_ID_ONHIDE, "onHide");
    }

    /** @see Home#onInvalidate(Context) **/
    public void onInvalidate() {
        invokeVoidContextMethod(M_ID_ONINVALIDATE, "onInvalidate");
    }

    /**
     * @see Home#onRequestSearch(Context, int)
     */
    public void onRequestSearch(int mode) {
        try {
            Method method = cachedMethods.get(M_ID_ONREQUESTSEARCH);
            if (method == null) {
                method = mClass.getMethod("onRequestSearch", Context.class, int.class);
            }
            method.invoke(mInstance, mContext, mode);
        } catch (ReflectiveOperationException ex) {
            throw new SecurityException(ex);
        }
    }

    /** @see Home#createCustomView(Context) **/
    public View createCustomView() {
        try {
            Method method = cachedMethods.get(M_ID_CREATECUSTOMVIEW);
            if (method == null) {
                method = mClass.getMethod("createCustomView", Context.class);
            }
            return (View) method.invoke(mInstance, mContext);
        } catch (ReflectiveOperationException ex) {
            throw new SecurityException(ex);
        }
    }

    /** @see Home#getName(Context) **/
    public String getName() {
        try {
            Method method = cachedMethods.get(M_ID_GETNAME);
            if (method == null) {
                method = mClass.getMethod("getName", Context.class);
            }
            return (String) method.invoke(mInstance, mContext);
        } catch (ReflectiveOperationException ex) {
            throw new SecurityException(ex);
        }
    }

    /** @see Home#getNotificationFlags() **/
    private int getNotificationFlags() {
        try {
            Method method = cachedMethods.get(M_ID_GETNOTIFICATIONFLAGS);
            if (method == null) {
                method = mClass.getMethod("getNotificationFlags");
            }
            return (Integer) method.invoke(mInstance);
        } catch (ReflectiveOperationException ex) {
            return 0;
        }
    }

    /** @see Home#getOperationFlags() **/
    private int getOperationFlags() {
        try {
            Method method = cachedMethods.get(M_ID_GETOPERATIONFLAGS);
            if (method == null) {
                method = mClass.getMethod("getOperationFlags");
            }
            return (Integer) method.invoke(mInstance);
        } catch (ReflectiveOperationException ex) {
            return 0;
        }
    }

    private void invokeVoidContextMethod(int methodId, String methodName) {
        try {
            Method method = cachedMethods.get(methodId);
            if (method == null) {
                method = mClass.getMethod(methodName, Context.class);
            }
            method.invoke(mInstance, mContext);
        } catch (ReflectiveOperationException ex) {
            throw new SecurityException(ex);
        }
    }

    private final String createDigest(Class<?> cls) throws SecurityException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA1");
            Method[] methods = cls.getDeclaredMethods();
            for (Method method : methods) {
                digest.update(method.toString().getBytes());
            }
            return new String(Base64.encode(digest.digest(), Base64.NO_WRAP));
        } catch (NoSuchAlgorithmException ex) {
            throw new SecurityException(ex);
        }
    }

    public boolean isNotificationSupported(int flag) {
        return (mNotificationFlags & flag) == flag;
    }

    public boolean isOperationSupported(int flag) {
        return (mOperationFlags & flag) == flag;
    }
}
