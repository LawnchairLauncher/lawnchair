/**
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.launcher3.util;

import java.util.LinkedList;
import java.util.function.IntConsumer;

public class TraceHelperForTest extends TraceHelper {

    private static final TraceHelperForTest INSTANCE_FOR_TEST = new TraceHelperForTest();

    private final ThreadLocal<LinkedList<TraceInfo>> mStack =
            ThreadLocal.withInitial(LinkedList::new);

    private RaceConditionReproducer mRaceConditionReproducer;
    private IntConsumer mFlagsChangeListener;

    public static void setRaceConditionReproducer(RaceConditionReproducer reproducer) {
        TraceHelper.INSTANCE = INSTANCE_FOR_TEST;
        INSTANCE_FOR_TEST.mRaceConditionReproducer = reproducer;
    }

    public static void cleanup() {
        INSTANCE_FOR_TEST.mRaceConditionReproducer = null;
        INSTANCE_FOR_TEST.mFlagsChangeListener = null;
    }

    public static void setFlagsChangeListener(IntConsumer listener) {
        TraceHelper.INSTANCE = INSTANCE_FOR_TEST;
        INSTANCE_FOR_TEST.mFlagsChangeListener = listener;
    }

    private TraceHelperForTest() { }

    @Override
    public void beginSection(String sectionName, int flags) {
        LinkedList<TraceInfo> stack = mStack.get();
        stack.add(new TraceInfo(sectionName, flags));

        if ((flags & TraceHelper.FLAG_CHECK_FOR_RACE_CONDITIONS) != 0
                 && mRaceConditionReproducer != null) {
            mRaceConditionReproducer.onEvent(RaceConditionReproducer.enterEvt(sectionName));
        }
        updateBinderTracking(stack);

        super.beginSection(sectionName, flags);
    }

    @Override
    public void endSection() {
        LinkedList<TraceInfo> stack = mStack.get();
        TraceInfo info = stack.pollLast();
        if ((info.flags & TraceHelper.FLAG_CHECK_FOR_RACE_CONDITIONS) != 0
                && mRaceConditionReproducer != null) {
            mRaceConditionReproducer.onEvent(RaceConditionReproducer.exitEvt(info.sectionName));
        }
        updateBinderTracking(stack);

        super.endSection();
    }

    @Override
    public void beginFlagsOverride(int flags) {
        LinkedList<TraceInfo> stack = mStack.get();
        stack.push(new TraceInfo(null, flags));
        updateBinderTracking(stack);
        super.beginFlagsOverride(flags);
    }

    @Override
    public void endFlagsOverride() {
        super.endFlagsOverride();
        updateBinderTracking(mStack.get());
    }

    private void updateBinderTracking(LinkedList<TraceInfo> stack) {
        if (mFlagsChangeListener != null) {
            mFlagsChangeListener.accept(stack.stream()
                    .mapToInt(s -> s.flags).reduce(0, (a, b) -> a | b));
        }
    }

    private static class TraceInfo {
        public final String sectionName;
        public final int flags;

        TraceInfo(String sectionName, int flags) {
            this.sectionName = sectionName;
            this.flags = flags;
        }
    }
}
