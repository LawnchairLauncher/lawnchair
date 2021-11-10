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

package com.android.quickstep;

import com.android.launcher3.R;

/**
 * Bridge class to allow using resources in Kotlin.
 * <br/>
 * TODO(b/204069723) Can't use resources directly in Kotlin
 */
public class KtR {
    public static final class id {
        public static int menu_option_layout = R.id.menu_option_layout;
    }

    public static final class dimen {
        public static int task_menu_spacing = R.dimen.task_menu_spacing;
    }

    public static final class layout {
        public static int task_menu_with_arrow = R.layout.task_menu_with_arrow;
        public static int task_view_menu_option = R.layout.task_view_menu_option;
    }
}
