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

package com.android.wm.shell.bubbles

import com.android.internal.logging.testing.UiEventLoggerFake.FakeUiEvent
import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject
import com.google.common.truth.Truth

/** Subclass of [Subject] to simplify verifying [FakeUiEvent] data */
class UiEventSubject(metadata: FailureMetadata, private val actual: FakeUiEvent?) :
    Subject(metadata, actual) {

    /** Check that [FakeUiEvent] contains the expected data from the [bubble] passed id */
    fun hasBubbleInfo(bubble: Bubble) {
        check("uid").that(actual?.uid).isEqualTo(bubble.appUid)
        check("packageName").that(actual?.packageName).isEqualTo(bubble.packageName)
        check("instanceId").that(actual?.instanceId).isEqualTo(bubble.instanceId)
    }

    companion object {
        @JvmStatic
        fun assertThat(event: FakeUiEvent): UiEventSubject =
            Truth.assertAbout(Factory(::UiEventSubject)).that(event)
    }
}
