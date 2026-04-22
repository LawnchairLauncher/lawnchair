/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell;

import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.view.SurfaceControl;

/**
 * Helper class to provide mocks for {@link SurfaceControl.Builder} and
 * {@link SurfaceControl.Transaction} with method chaining support.
 */
public class MockSurfaceControlHelper {
    private MockSurfaceControlHelper() {}

    /**
     * Creates a mock {@link SurfaceControl.Builder} that supports method chaining and return the
     * given {@link SurfaceControl} when calling {@link SurfaceControl.Builder#build()}.
     *
     * @param mockSurfaceControl the first {@link SurfaceControl} to return
     * @return the mock of {@link SurfaceControl.Builder}
     */
    public static SurfaceControl.Builder createMockSurfaceControlBuilder(
            SurfaceControl mockSurfaceControl) {
        final SurfaceControl.Builder mockBuilder = mock(SurfaceControl.Builder.class, RETURNS_SELF);
        doReturn(mockSurfaceControl)
                .when(mockBuilder)
                .build();
        return mockBuilder;
    }

    /**
     * Creates a mock {@link SurfaceControl.Transaction} that supports method chaining.
     * @return the mock of {@link SurfaceControl.Transaction}
     */
    public static SurfaceControl.Transaction createMockSurfaceControlTransaction() {
        return mock(SurfaceControl.Transaction.class, RETURNS_SELF);
    }
}
