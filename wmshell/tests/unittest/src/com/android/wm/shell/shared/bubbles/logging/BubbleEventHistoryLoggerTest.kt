/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.shared.bubbles.logging

import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.Flags
import com.android.wm.shell.shared.bubbles.logging.BubbleEventHistoryLogger.Companion.DATE_FORMAT
import com.android.wm.shell.shared.bubbles.logging.BubbleEventHistoryLogger.Companion.DATE_FORMATTER
import com.android.wm.shell.shared.bubbles.logging.BubbleEventHistoryLogger.Companion.MAX_EVENTS
import com.google.common.truth.Truth.assertThat
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.text.split
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Unit tests for [BubbleEventHistoryLogger]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(Flags.FLAG_ENABLE_BUBBLE_EVENT_HISTORY_LOGS)
class BubbleEventHistoryLoggerTest {

    @get:Rule val flagsRule = SetFlagsRule()

    private val logger = BubbleEventHistoryLogger()
    private val logPattern = Regex("^\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} .: .*$")

    @Test
    fun dump_printsHeaderWhenNoEvents() {
        val expectedOutput = "Bubbles events history:\n"
        assertThat(getDumpOutput()).isEqualTo(expectedOutput)
    }

    @Test
    fun dump_printsHeaderWithEvents() {
        val timestamp = System.currentTimeMillis()
        val formattedTimeStamp = DATE_FORMATTER.format(timestamp)
        logger.logEvent("e: test", "eventData", timestamp)
        logger.logEvent("d: hey", timestamp = timestamp)
        val expectedOutput = """
            Bubbles events history:
              $formattedTimeStamp d: hey
              $formattedTimeStamp e: test | eventData
            """.trimIndent() + "\n"
        assertThat(getDumpOutput()).isEqualTo(expectedOutput)
    }

    @Test
    fun dump_respectsMAX_EVENTS() {
        repeat(MAX_EVENTS + 10) { logger.d(message = "title") }
        val linesCount = getTrimmedLogLines().size

        assertThat(linesCount).isEqualTo(MAX_EVENTS)
    }

    @Test
    fun dump_printsEventsInReverseChronologicalOrderStartingFromTheMostRecentEvent() {
        val repetitions = MAX_EVENTS * 2
        repeat(repetitions) { repetition ->
            logger.logEvent(title = "", timestamp = repetition.toLong())
        }
        val lastEventDateTime = DATE_FORMATTER.format(repetitions - 1)
        val logLines = getTrimmedLogLines()

        // reversed timestamps should be in chronological order
        assertThat(logLines.reversed()).isInOrder()
        // first log entry corresponds to the most recent event
        assertThat(logLines.first()).contains(lastEventDateTime)
    }

    @Test
    fun dump_printsEventsInExpectedFormat() {
        logger.d("test %b", true, eventData = "eventData")
        logger.v("test %d", 0, eventData = "eventData")
        logger.i("test %s", "stringArgument", eventData = "eventData")
        logger.w("test")
        logger.e("test", eventData = "eventData")

        val logLines = getTrimmedLogLines()

        assertLogFormat(logLines[4], "d: test true | eventData")
        assertLogFormat(logLines[3], "v: test 0 | eventData")
        assertLogFormat(logLines[2], "i: test stringArgument | eventData")
        assertLogFormat(logLines[1], "w: test")
        assertLogFormat(logLines[0], "e: test | eventData")
    }

    @Test
    fun multiThreadLogging_dump_RespectsMAX_EVENTS() {
        val numberOfThreads = 50
        val eventsPerThread = MAX_EVENTS // each thread will emmit MAX_EVENTS
        val startLatch = CountDownLatch(1) // Main thread signals worker threads to start
        val doneLatch = CountDownLatch(numberOfThreads) // Worker threads signal
        val executorService = Executors.newFixedThreadPool(numberOfThreads)
        for (i in 0 until numberOfThreads) {
            executorService.submit {
                try {
                    startLatch.await() // Wait until the main thread gives the green light
                    repeat(eventsPerThread) { logger.logEvent("Thread $i", "Data $i-$it") }
                } finally {
                    doneLatch.countDown() // Signal that this thread has finished
                }
            }
        }

        // Give all threads the signal to start logging which unblocks all threads
        startLatch.countDown()
        // Wait for all threads to complete their logging tasks
        // Add a timeout to prevent the test from hanging indefinitely if something goes wrong
        assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue()

        // Check that there are no more than MAX_EVENTS events in the log history
        val logLinesCount = getTrimmedLogLines().size
        assertThat(logLinesCount).isEqualTo(MAX_EVENTS)
    }

    private fun assertLogFormat(logEntry: String, expectedLogWithoutDate: String) {
        assertThat(logEntry.matches(logPattern)).isTrue()
        val trimmedDateTime = logEntry.substring(DATE_FORMAT.length + 1)
        assertThat(trimmedDateTime).isEqualTo(expectedLogWithoutDate)
    }

    private fun getTrimmedLogLines(): List<String> {
        return getDumpOutput().split("\n").drop(1).dropLast(1).map { it.trim() }
    }

    private fun getDumpOutput(): String {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)

        logger.dump(pw = printWriter)
        printWriter.flush() // Ensure all content is written to StringWriter
        return stringWriter.toString()
    }
}
