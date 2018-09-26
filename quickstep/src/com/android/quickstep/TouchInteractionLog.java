/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.view.MotionEvent;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;

/**
 * Keeps track of debugging logs for a particular quickstep/scrub gesture.
 */
public class TouchInteractionLog {

    // The number of gestures to log
    private static final int MAX_NUM_LOG_GESTURES = 5;

    private final Calendar mCalendar = Calendar.getInstance();
    private final SimpleDateFormat mDateFormat = new SimpleDateFormat("MMM dd - kk:mm:ss:SSS");
    private final LinkedList<ArrayList<String>> mGestureLogs = new LinkedList<>();

    public void prepareForNewGesture() {
        mGestureLogs.add(new ArrayList<>());
        while (mGestureLogs.size() > MAX_NUM_LOG_GESTURES) {
            mGestureLogs.pop();
        }
        getCurrentLog().add("[" + mDateFormat.format(mCalendar.getTime()) + "]");
    }

    public void setTouchConsumer(TouchConsumer consumer) {
        getCurrentLog().add("tc=" + consumer.getClass().getSimpleName());
    }

    public void addMotionEvent(MotionEvent event) {
        getCurrentLog().add("ev=" + event.getActionMasked());
    }

    public void startQuickStep() {
        getCurrentLog().add("qstStart");
    }

    public void startQuickScrub() {
        getCurrentLog().add("qsStart");
    }

    public void setQuickScrubProgress(float progress) {
        getCurrentLog().add("qsP=" + progress);
    }

    public void endQuickScrub(String reason) {
        getCurrentLog().add("qsEnd=" + reason);
    }

    public void startRecentsAnimation() {
        getCurrentLog().add("raStart");
    }

    public void startRecentsAnimationCallback(int numTargets) {
        getCurrentLog().add("raStartCb=" + numTargets);
    }

    public void cancelRecentsAnimation() {
        getCurrentLog().add("raCancel");
    }

    public void finishRecentsAnimation(boolean toHome) {
        getCurrentLog().add("raFinish=" + toHome);
    }

    public void launchTaskStart() {
        getCurrentLog().add("launchStart");
    }

    public void launchTaskEnd(boolean result) {
        getCurrentLog().add("launchEnd=" + result);
    }

    public void dump(PrintWriter pw) {
        pw.println("TouchInteractionLog {");
        for (ArrayList<String> gesture : mGestureLogs) {
            pw.print("    ");
            for (String log : gesture) {
                pw.print(log + " ");
            }
            pw.println();
        }
        pw.println("}");
    }

    private ArrayList<String> getCurrentLog() {
        return mGestureLogs.getLast();
    }
}
