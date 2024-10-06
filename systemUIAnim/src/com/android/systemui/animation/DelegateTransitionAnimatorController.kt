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

package com.android.systemui.animation

/**
 * A base class to easily create an implementation of [ActivityTransitionAnimator.Controller] which
 * delegates most of its call to [delegate]. This is mostly useful for Java code which can't easily
 * create such a delegated class.
 */
open class DelegateTransitionAnimatorController(
    protected val delegate: ActivityTransitionAnimator.Controller
) : ActivityTransitionAnimator.Controller by delegate
