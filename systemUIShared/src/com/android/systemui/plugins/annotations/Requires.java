/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.plugins.annotations;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Used to annotate which interfaces a given plugin depends on.
 *
 * At minimum all plugins should have at least one @Requires annotation
 * for the plugin interface that they are implementing. They will also
 * need an @Requires for each class that the plugin interface @DependsOn.
 */
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(value = Requirements.class)
public @interface Requires {
    Class<?> target();
    int version();
}
