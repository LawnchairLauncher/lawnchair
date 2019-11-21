/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.launcher3.util.Executors.createAndStartNewLooper;
import static com.android.launcher3.util.RaceConditionTracker.ENTER_POSTFIX;
import static com.android.launcher3.util.RaceConditionTracker.EXIT_POSTFIX;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Event processor for reliably reproducing multithreaded apps race conditions in tests.
 *
 * The app notifies us about “events” that happen in its threads. The race condition test runs the
 * test action multiple times (aka iterations), trying to generate all possible permutations of
 * these events. It keeps a set of all seen event sequences and steers the execution towards
 * executing events in previously unseen order. It does it by postponing execution of threads that
 * would lead to an already seen sequence.
 *
 * If an event A occurs before event B in the sequence, this is how execution order looks like:
 * Events: ... A ... B ...
 * Events and instructions, guaranteed order:
 * (instructions executed prior to A) A ... B (instructions executed after B)
 *
 * Each iteration has 3 parts (phases).
 * Phase 1. Picking a previously seen event subsequence that we believe can have previously unseen
 * continuations. Reproducing this sequence by pausing threads that would lead to other sequences.
 * Phase 2. Trying to generate previously unseen continuation of the sequence from Phase 1. We need
 * one new event after that sequence. All threads leading to seen continuations will be postponed
 * for some short period of time. The phase ends once the new event is registered, or after the
 * period of time ends (in which case we declare that the sequence can’t have new continuations).
 * Phase 3. Releasing all threads and letting the test iteration run till its end.
 *
 * The iterations end when all seen paths have been declared “uncontinuable”.
 *
 * When we register event XXX:enter, we hold all other events until we register XXX:exit.
 */
public class RaceConditionReproducer implements RaceConditionTracker.EventProcessor {
    private static final String TAG = "RaceConditionReproducer";
    private static final long SHORT_TIMEOUT_MS = 2000;
    private static final long LONG_TIMEOUT_MS = 60000;
    // Handler used to resume postponed events.
    private static final Handler POSTPONED_EVENT_RESUME_HANDLER = createEventResumeHandler();

    private static Handler createEventResumeHandler() {
        return new Handler(createAndStartNewLooper("RaceConditionEventResumer"));
    }

    /**
     * Event in a particular sequence of events. A node in the prefix tree of all seen event
     * sequences.
     */
    private class EventNode {
        // Events that were seen just after this event.
        private final Map<String, EventNode> mNextEvents = new HashMap<>();
        // Whether we believe that further iterations will not be able to add more events to
        // mNextEvents.
        private boolean mStoppedAddingChildren = true;

        private void debugDump(StringBuilder sb, int indent, String name) {
            for (int i = 0; i < indent; ++i) sb.append('.');
            sb.append(!mStoppedAddingChildren ? "+" : "-");
            sb.append(" : ");
            sb.append(name);
            if (mLastRegisteredEvent == this) sb.append(" <");
            sb.append('\n');

            for (String key : mNextEvents.keySet()) {
                mNextEvents.get(key).debugDump(sb, indent + 2, key);
            }
        }

        /** Number of leaves in the subtree with this node as a root. */
        private int numberOfLeafNodes() {
            if (mNextEvents.isEmpty()) return 1;

            int leaves = 0;
            for (String event : mNextEvents.keySet()) {
                leaves += mNextEvents.get(event).numberOfLeafNodes();
            }
            return leaves;
        }

        /**
         * Whether we believe that further iterations will not be able add nodes to the subtree with
         * this node as a root.
         */
        private boolean stoppedAddingChildrenToTree() {
            if (!mStoppedAddingChildren) return false;

            for (String event : mNextEvents.keySet()) {
                if (!mNextEvents.get(event).stoppedAddingChildrenToTree()) return false;
            }
            return true;
        }

        /**
         * In the subtree with this node as a root, tries finding a node where we may have a
         * chance to add new children.
         * If succeeds, returns true and fills 'path' with the sequence of events to that node;
         * otherwise returns false.
         */
        private boolean populatePathToGrowthPoint(List<String> path) {
            for (String event : mNextEvents.keySet()) {
                if (mNextEvents.get(event).populatePathToGrowthPoint(path)) {
                    path.add(0, event);
                    return true;
                }
            }
            if (!mStoppedAddingChildren) {
                // Mark that we have finished adding children. It will remain true if no new
                // children are added, or will be set to false upon adding a new child.
                mStoppedAddingChildren = true;
                return true;
            }
            return false;
        }
    }

    // Starting point of all event sequences; the root of the prefix tree representation all
    // sequences generated by test iterations. A test iteration can add nodes int it.
    private EventNode mRoot = new EventNode();
    // During a test iteration, the last event that was registered.
    private EventNode mLastRegisteredEvent;
    // Length of the current sequence of registered events for the current test iteration.
    private int mRegisteredEventCount = 0;
    // During the first part of a test iteration, we go to a specific node under mRoot by
    // 'playing back' mSequenceToFollow. During this part, all events that don't belong to this
    // sequence get postponed.
    private List<String> mSequenceToFollow = new ArrayList<>();
    // Collection of events that got postponed, with corresponding wait objects used to let them go.
    private Map<String, Semaphore> mPostponedEvents = new HashMap<>();
    // Callback to run by POSTPONED_EVENT_RESUME_HANDLER, used to let go of all currently
    // postponed events.
    private Runnable mResumeAllEventsCallback;
    // String representation of the sequence of events registered so far for the current test
    // iteration. After registering any event, we output it to the log. The last output before
    // the test failure can be later played back to reliable reproduce the exact sequence of
    // events that broke the test.
    // Format: EV1|EV2|...\EVN
    private StringBuilder mCurrentSequence;
    // When not null, we are in a repro mode. We run only one test iteration, and are trying to
    // reproduce the event sequence represented by this string. The format is same as for
    // mCurrentSequence.
    private final String mReproString;

    /* Constructor for a normal test. */
    public RaceConditionReproducer() {
        mReproString = null;
    }

    /**
     * Constructor for reliably reproducing a race condition failure. The developer should find in
     * the log the latest "Repro sequence:" record and locally modify the test by passing that
     * string to the constructor. Running the test will have only one iteration that will reliably
     * "play back" that sequence.
     */
    public RaceConditionReproducer(String reproString) {
        mReproString = reproString;
    }

    public RaceConditionReproducer(String... reproSequence) {
        this(String.join("|", reproSequence));
    }

    public synchronized String getCurrentSequenceString() {
        return mCurrentSequence.toString();
    }

    /**
     * Starts a new test iteration. Events reported via RaceConditionTracker.onEvent before this
     * call will be ignored.
     */
    public synchronized void startIteration() {
        mLastRegisteredEvent = mRoot;
        mRegisteredEventCount = 0;
        mCurrentSequence = new StringBuilder();
        Log.d(TAG, "Repro sequence: " + mCurrentSequence);
        mSequenceToFollow = mReproString != null ?
                parseReproString(mReproString) : generateSequenceToFollowLocked();
        Log.e(TAG, "---- Start of iteration; state:\n" + dumpStateLocked());
        checkIfCompletedSequenceToFollowLocked();
        RaceConditionTracker.setEventProcessor(this);
    }

    /**
     * Ends a new test iteration. Events reported via RaceConditionTracker.onEvent after this call
     * will be ignored.
     * Returns whether we need more iterations.
     */
    public synchronized boolean finishIteration() {
        RaceConditionTracker.setEventProcessor(null);
        runResumeAllEventsCallbackLocked();
        assertTrue("Non-empty postponed events", mPostponedEvents.isEmpty());
        assertTrue("Last registered event is :enter", lastEventAsEnter() == null);

        // No events came after mLastRegisteredEvent. It doesn't make sense to come to it again
        // because we won't see new continuations.
        mLastRegisteredEvent.mStoppedAddingChildren = true;
        Log.e(TAG, "---- End of iteration; state:\n" + dumpStateLocked());
        if (mReproString != null) {
            assertTrue("Repro mode: failed to reproduce the sequence",
                    mCurrentSequence.toString().startsWith(mReproString));
        }
        // If we are in a repro mode, we need only one iteration. Otherwise, continue if the tree
        // has prospective growth points.
        return mReproString == null && !mRoot.stoppedAddingChildrenToTree();
    }

    private static List<String> parseReproString(String reproString) {
        return Arrays.asList(reproString.split("\\|"));
    }

    /**
     * Called when the app issues an event.
     */
    @Override
    public void onEvent(String event) {
        final Semaphore waitObject = tryRegisterEvent(event);
        if (waitObject != null) {
            waitUntilCanRegister(event, waitObject);
        }
    }

    /**
     * Returns whether the last event was not an XXX:enter, or this event is a matching XXX:exit.
     */
    private boolean canRegisterEventNowLocked(String event) {
        final String lastEventAsEnter = lastEventAsEnter();
        final String thisEventAsExit = eventAsExit(event);

        if (lastEventAsEnter != null) {
            if (!lastEventAsEnter.equals(thisEventAsExit)) {
                assertTrue("YYY:exit after XXX:enter", thisEventAsExit == null);
                // Last event was :enter, but this event is not :exit.
                return false;
            }
        } else {
            // Previous event was not :enter.
            assertTrue(":exit after a non-enter event", thisEventAsExit == null);
        }
        return true;
    }

    /**
     * Registers an event issued by the app and returns null or decides that the event must be
     * postponed, and returns an object to wait on.
     */
    private synchronized Semaphore tryRegisterEvent(String event) {
        Log.d(TAG, "Event issued by the app: " + event);

        if (!canRegisterEventNowLocked(event)) {
            return createWaitObjectForPostponedEventLocked(event);
        }

        if (mRegisteredEventCount < mSequenceToFollow.size()) {
            // We are in the first part of the iteration. We only register events that follow the
            // mSequenceToFollow and postponing all other events.
            if (event.equals(mSequenceToFollow.get(mRegisteredEventCount))) {
                // The event is the next one expected in the sequence. Register it.
                registerEventLocked(event);

                // If there are postponed events that could continue the sequence, register them.
                while (mRegisteredEventCount < mSequenceToFollow.size() &&
                        mPostponedEvents.containsKey(
                                mSequenceToFollow.get(mRegisteredEventCount))) {
                    registerPostponedEventLocked(mSequenceToFollow.get(mRegisteredEventCount));
                }

                // Perhaps we just completed the required sequence...
                checkIfCompletedSequenceToFollowLocked();
            } else {
                // The event is not the next one in the sequence. Postpone it.
                return createWaitObjectForPostponedEventLocked(event);
            }
        } else if (mRegisteredEventCount == mSequenceToFollow.size()) {
            // The second phase of the iteration. We have just registered the whole
            // mSequenceToFollow, and want to add previously not seen continuations for the last
            // node in the sequence aka 'growth point'.
            if (!mLastRegisteredEvent.mNextEvents.containsKey(event) || mReproString != null) {
                // The event was never seen as a continuation for the current node.
                // Or we are in repro mode, in which case we are not in business of generating
                // new sequences after we've played back the required sequence.
                // Register it immediately.
                registerEventLocked(event);
            } else {
                // The event was seen as a continuation for the current node. Postpone it, hoping
                // that a new event will come from other threads.
                return createWaitObjectForPostponedEventLocked(event);
            }
        } else {
            // The third phase of the iteration. We are past the growth point and register
            // everything that comes.
            registerEventLocked(event);
            // Register events that may have been postponed while waiting for an :exit event
            // during the third phase. We don't do this if just registered event is :enter.
            if (eventAsEnter(event) == null && mRegisteredEventCount > mSequenceToFollow.size()) {
                registerPostponedEventsLocked(new HashSet<>(mPostponedEvents.keySet()));
            }
        }
        return null;
    }

    /** Called when there are chances that we just have registered the whole mSequenceToFollow. */
    private void checkIfCompletedSequenceToFollowLocked() {
        if (mRegisteredEventCount == mSequenceToFollow.size()) {
            // We just entered the second phase of the iteration. We have just registered the
            // whole mSequenceToFollow, and want to add previously not seen continuations for the
            // last node in the sequence aka 'growth point'. All seen continuations will be
            // postponed for SHORT_TIMEOUT_MS. At the end of this time period, we'll let them go.
            scheduleResumeAllEventsLocked();

            // Among the events that were postponed during the first stage, there may be an event
            // that wasn't seen after the current. If so, register it immediately because this
            // creates a new sequence.
            final Set<String> keys = new HashSet<>(mPostponedEvents.keySet());
            keys.removeAll(mLastRegisteredEvent.mNextEvents.keySet());
            if (!keys.isEmpty()) {
                registerPostponedEventLocked(keys.iterator().next());
            }
        }
    }

    private Semaphore createWaitObjectForPostponedEventLocked(String event) {
        final Semaphore waitObject = new Semaphore(0);
        assertTrue("Event already postponed: " + event, !mPostponedEvents.containsKey(event));
        mPostponedEvents.put(event, waitObject);
        return waitObject;
    }

    private void waitUntilCanRegister(String event, Semaphore waitObject) {
        try {
            assertTrue("Never registered event: " + event,
                    waitObject.tryAcquire(LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail("Wait was interrupted");
        }
    }

    /** Schedules resuming all postponed events after SHORT_TIMEOUT_MS */
    private void scheduleResumeAllEventsLocked() {
        assertTrue(mResumeAllEventsCallback == null);
        mResumeAllEventsCallback = this::allEventsResumeCallback;
        POSTPONED_EVENT_RESUME_HANDLER.postDelayed(mResumeAllEventsCallback, SHORT_TIMEOUT_MS);
    }

    private synchronized void allEventsResumeCallback() {
        assertTrue("In callback, but callback is not set", mResumeAllEventsCallback != null);
        mResumeAllEventsCallback = null;
        registerPostponedEventsLocked(new HashSet<>(mPostponedEvents.keySet()));
    }

    private void registerPostponedEventsLocked(Collection<String> events) {
        for (String event : events) {
            registerPostponedEventLocked(event);
            if (eventAsEnter(event) != null) {
                // Once :enter is registered, switch to waiting for :exit to come. Won't register
                // other postponed events.
                break;
            }
        }
    }

    private void registerPostponedEventLocked(String event) {
        mPostponedEvents.remove(event).release();
        registerEventLocked(event);
    }

    /**
     * If the last registered event was XXX:enter, returns XXX, otherwise, null.
     */
    private String lastEventAsEnter() {
        return eventAsEnter(mCurrentSequence.substring(mCurrentSequence.lastIndexOf("|") + 1));
    }

    /**
     * If the event is XXX:postfix, returns XXX, otherwise, null.
     */
    private static String prefixFromPostfixedEvent(String event, String postfix) {
        final int columnPos = event.indexOf(':');
        if (columnPos != -1 && postfix.equals(event.substring(columnPos + 1))) {
            return event.substring(0, columnPos);
        }
        return null;
    }

    /**
     * If the event is XXX:enter, returns XXX, otherwise, null.
     */
    private static String eventAsEnter(String event) {
        return prefixFromPostfixedEvent(event, ENTER_POSTFIX);
    }

    /**
     * If the event is XXX:exit, returns XXX, otherwise, null.
     */
    private static String eventAsExit(String event) {
        return prefixFromPostfixedEvent(event, EXIT_POSTFIX);
    }

    private void registerEventLocked(String event) {
        assertTrue(canRegisterEventNowLocked(event));

        Log.d(TAG, "Actually registering event: " + event);
        EventNode next = mLastRegisteredEvent.mNextEvents.get(event);
        if (next == null) {
            // This event wasn't seen after mLastRegisteredEvent.
            next = new EventNode();
            mLastRegisteredEvent.mNextEvents.put(event, next);
            // The fact that we've added a new event after the previous one means that the
            // previous event is still a growth point, unless this event is :exit, which means
            // that the previous event is :enter.
            mLastRegisteredEvent.mStoppedAddingChildren = eventAsExit(event) != null;
        }

        mLastRegisteredEvent = next;
        mRegisteredEventCount++;

        if (mCurrentSequence.length() > 0) mCurrentSequence.append("|");
        mCurrentSequence.append(event);
        Log.d(TAG, "Repro sequence: " + mCurrentSequence);
    }

    private void runResumeAllEventsCallbackLocked() {
        if (mResumeAllEventsCallback != null) {
            POSTPONED_EVENT_RESUME_HANDLER.removeCallbacks(mResumeAllEventsCallback);
            mResumeAllEventsCallback.run();
        }
    }

    private CharSequence dumpStateLocked() {
        StringBuilder sb = new StringBuilder();

        sb.append("Sequence to follow: ");
        for (String event : mSequenceToFollow) sb.append(" " + event);
        sb.append(".\n");
        sb.append("Registered event count: " + mRegisteredEventCount);

        sb.append("\nPostponed events: ");
        for (String event : mPostponedEvents.keySet()) sb.append(" " + event);
        sb.append(".");

        sb.append("\nNodes: \n");
        mRoot.debugDump(sb, 0, "");
        return sb;
    }

    public int numberOfLeafNodes() {
        return mRoot.numberOfLeafNodes();
    }

    private List<String> generateSequenceToFollowLocked() {
        ArrayList<String> sequence = new ArrayList<>();
        mRoot.populatePathToGrowthPoint(sequence);
        return sequence;
    }
}
