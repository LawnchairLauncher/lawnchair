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

import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;

import android.view.SurfaceControl;

import com.android.wm.shell.shared.TransactionPool;
import com.android.wm.shell.util.StubTransaction;

public class MockTransactionPool extends TransactionPool {

    public static SurfaceControl.Transaction create() {
        return mock(StubTransaction.class, RETURNS_SELF);
    }

    @Override
    public SurfaceControl.Transaction acquire() {
        return create();
    }

    @Override
    public void release(SurfaceControl.Transaction t) {
    }
}
