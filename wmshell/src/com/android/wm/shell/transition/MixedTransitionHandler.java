/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.transition;

/**
 * Interface for a {@link Transitions.TransitionHandler} that can take the subset of transitions
 * that it handles and further decompose those transitions into sub-transitions which can be
 * independently delegated to separate handlers.
 */
public interface MixedTransitionHandler extends Transitions.TransitionHandler {

    // TODO(b/335685449) this currently exists purely as a marker interface for use in form-factor
    // specific/sysui dagger modules. Going forward, we should define this in a meaningful
    // way so as to provide a clear basis for expectations/behaviours associated with mixed
    // transitions and their default handlers.

}
