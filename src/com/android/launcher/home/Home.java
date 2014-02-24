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

package com.android.launcher.home;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

/**
 * The generic contract that should supports a <code>Home</code> app to could
 * be invoked and registered by an Android launcher.<br/>
 * <br/>
 * This interface contains the version 1 of the <code>Home Host App</code> protocol.<br/>
 * <br/>
 * <br/>
 * A <code>Home</code> app should:
 * <ul>
 *  <li>
 *    should have at least a constructor with no arguments
 *  </li>
 *  <li>
 *    declares inside its manifest a <code>com.android.launcher.home</code> metadata with the
 *    full qualified that contains this interface<br/>
 *    <pre>
 *    &lt;meta-data android:name="com.android.launcher.home" value="org.cyanogenmod.launcher.home.HomeStub"/&gt;
 *    </pre>
 *  </li>
 *  <li>
 *    define the "com.android.launcher.home.permissions.HOME_APP" permission<br/>
 *    <pre>
 *    &lt;uses-permission android:name="com.android.launcher.home.permissions.HOME_APP"/&gt;
 *    </pre>
 *  </li>
 *  <li>
 *    implements the contract defined by this protocol.
 *  </li>
 * </ul>
 * <br/>
 * Implementors classes of this protocol should be aware that all the {@link Context} references
 * passed to this class owns to the host launcher app. This means that you cannot access
 * to settings defined by the <code>Home</code> app inside its shared context.
 */
public interface Home {

    /**
     * A SHA-1 hash of all declared method of this interface. Home apps should compute as:<br/>
     * <br/>
     * <pre>
     *   for method in Home.class.getDeclaredMehod
     *      sha1.update method.toString.bytes
     * </pre><br/>
     * DO NOT MODIFY!
     */
    public static final String SIGNATURE = "5/A6Mxkz8gHHzzVf4qZR+hiSOAw=";

    /**
     * Defines the name of the metadata used to declared the full qualified Home stub class
     * that implements this protocol.
     */
    public static final String METADATA_HOME_STUB = "com.android.launcher.home";

    /**
     * Defines the name of the permission that the Home app should explicitly declare.
     */
    public static final String PERMISSION_HOME_APP = "com.android.launcher.home.permissions.HOME_APP";

    // Notification flags
    public static final int FLAG_NOTIFY_MASK = 0x0000;
    public static final int FLAG_NOTIFY_ON_RESUME = FLAG_NOTIFY_MASK + 0x0002;
    public static final int FLAG_NOTIFY_ON_PAUSE = FLAG_NOTIFY_MASK +  0x0004;
    public static final int FLAG_NOTIFY_ON_SHOW = FLAG_NOTIFY_MASK + 0x0008;
    public static final int FLAG_NOTIFY_ON_SCROLL_PROGRESS_CHANGED = FLAG_NOTIFY_MASK + 0x0010;
    public static final int FLAG_NOTIFY_ON_HIDE = FLAG_NOTIFY_MASK + 0x0020;
    public static final int FLAG_NOTIFY_ALL = FLAG_NOTIFY_ON_RESUME | FLAG_NOTIFY_ON_PAUSE |
            FLAG_NOTIFY_ON_SHOW | FLAG_NOTIFY_ON_SCROLL_PROGRESS_CHANGED | FLAG_NOTIFY_ON_HIDE;

    // Operation support flags
    public static final int FLAG_OP_MASK = 0x1000;
    public static final int FLAG_OP_CUSTOM_SEARCH = FLAG_OP_MASK + 0x0002;
    public static final int FLAG_OP_ALL = FLAG_OP_CUSTOM_SEARCH;

    // Search modes
    public static final int MODE_SEARCH_TEXT = 0x0000;
    public static final int MODE_SEARCH_VOICE = 0x0001;

    /**
     * Invoked the first time the <code>Home</code> app is created.<br/>
     * This method should be used by implementors classes of this protocol to load the needed
     * resources.
     * @param context the current {@link Context} of the host launcher.
     */
    void onStart(Context context);

    /**
     * Invoked when the <code>Home</code> app should be destroy.<br/>
     * This method should be used by implementors classes of this protocol to unload all unneeded
     * resources.
     * @param context the current {@link Context} of the host launcher.
     */
    void onDestroy(Context context);

    /**
     * Invoked when the host launcher enters in resume mode.
     * @param context the current {@link Context} of the host launcher.
     */
    void onResume(Context context);

    /**
     * Invoked when the host launcher enters in pause mode.
     * @param context the current {@link Context} of the host launcher.
     */
    void onPause(Context context);

    /**
     * Invoked when the custom content page is totally displayed.
     * @param context the current {@link Context} of the host launcher.
     */
    void onShow(Context context);

    /**
     * Invoked when the custom content page is scrolled.
     * @param context the current {@link Context} of the host launcher.
     * @param progress the current scroll progress.
     */
    void onScrollProgressChanged(Context context, float progress);

    /**
     * Invoked when the custom content page is totally hidden.
     * @param context the current {@link Context} of the host launcher.
     */
    void onHide(Context context);

    /**
     * Invoked by the host launcher to request an invalidation of the ui elements and data used by
     * the <code>Home</code> implementation class.
     * @param context the current {@link Context} of the host launcher.
     */
    void onInvalidate(Context context);

    /**
     * Invoked when the host launcher request enter in search mode.
     * @param context the current {@link Context} of the host launcher.
     * @param mode the requested search mode. Must be one of:
     * <ul>
     * <li>{@link #MODE_SEARCH_TEXT}: Textual mode</li>
     * <li>{@link #MODE_SEARCH_VOICE}: Voice mode</li>
     * </ul>
     */
    void onRequestSearch(Context context, int mode);

    /**
     * Returns an instance of a {@link View} that holds the custom content to be displayed
     * by this <code>Home</code> app.
     * @param context the current {@link Context} of the host launcher.
     * @return View The custom content view that will be enclosed inside a
     * <code>com.android.launcher3.Launcher.QSBScroller</code>.<br/>
     * Be aware the the height layout of the returned should be defined as
     * {link {@link LayoutParams#WRAP_CONTENT}, so the view could be scrolled inside the
     * custom content page.
     */
    View createCustomView(Context context);

    /**
     * Returns the name of the Home app (LIMIT: 30 characters).
     * @param context the current {@link Context} of the host launcher.
     */
    String getName(Context context);

    /**
     * Implementations should return the combination of notification flags that want to listen to.
     * @see #FLAG_NOTIFY_ON_RESUME
     * @see #FLAG_NOTIFY_ON_PAUSE
     * @see #FLAG_NOTIFY_ON_SHOW
     * @see #FLAG_NOTIFY_ON_SCROLL_PROGRESS_CHANGED
     * @see #FLAG_NOTIFY_ON_HIDE
     * @see #FLAG_NOTIFY_ALL
     */
    int getNotificationFlags();

    /**
     * Implementations should return the combination of operation flags that want they want
     * to support to.
     * @see #FLAG_OP_CUSTOM_SEARCH
     * @see #FLAG_OP_ALL
     */
    int getOperationFlags();
}
