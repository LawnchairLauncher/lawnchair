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

package com.android.systemui.shared.system;

/**
 *  These strings are part of the {@link com.android.systemui.people.PeopleProvider} API
 *  contract. The API returns a People Tile preview that can be displayed by calling packages.
 *  The provider is part of the SystemUI service, and the strings live here for shared access with
 *  Launcher (caller).
 */
public class PeopleProviderUtils {
    /**
     * ContentProvider URI scheme.
     * @hide
     */
    public static final String PEOPLE_PROVIDER_SCHEME = "content://";

    /**
     * ContentProvider URI authority.
     * @hide
     */
    public static final String PEOPLE_PROVIDER_AUTHORITY =
            "com.android.systemui.people.PeopleProvider";

    /**
     * Method name for getting People Tile preview.
     * @hide
     */
    public static final String GET_PEOPLE_TILE_PREVIEW_METHOD = "get_people_tile_preview";

    /**
     * Extras bundle key specifying shortcut Id of the People Tile preview requested.
     * @hide
     */
    public static final String EXTRAS_KEY_SHORTCUT_ID = "shortcut_id";

    /**
     * Extras bundle key specifying package name of the People Tile preview requested.
     * @hide
     */
    public static final String EXTRAS_KEY_PACKAGE_NAME = "package_name";

    /**
     * Extras bundle key specifying {@code UserHandle} of the People Tile preview requested.
     * @hide
     */
    public static final String EXTRAS_KEY_USER_HANDLE = "user_handle";

    /**
     * Response bundle key to access the returned People Tile preview.
     * @hide
     */
    public static final String RESPONSE_KEY_REMOTE_VIEWS = "remote_views";

    /**
     * Name of the permission needed to get a People Tile preview for a given conversation shortcut.
     * @hide
     */
    public static final String GET_PEOPLE_TILE_PREVIEW_PERMISSION =
            "android.permission.GET_PEOPLE_TILE_PREVIEW";

}
