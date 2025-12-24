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
import android.text.TextUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.Flags
import com.google.common.truth.Truth.assertThat
import java.io.PrintWriter
import java.io.StringWriter
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Unit tests for [BubbleLog]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(Flags.FLAG_ENABLE_BUBBLE_EVENT_HISTORY_LOGS)
class BubbleLogTest {

    @get:Rule val flagsRule = SetFlagsRule()

    @Before
    fun setup() {
        // Clear all loggers except BubbleEventHistoryLogger
        BubbleLog.loggers.removeIf { it !is BubbleEventHistoryLogger }
        val historyLogger = BubbleLog.loggers.first() as BubbleEventHistoryLogger
        // Clear BubbleEventHistoryLogger previous events
        historyLogger.recentEvents.clear()
    }

    @Test
    fun addLogger_addedLoggerHasAllEvents() {
        assertThat(BubbleLog.loggers.size).isEqualTo(1)
        val testLogger = TestLogger()
        val testData = "test data"
        BubbleLog.addLogger(testLogger)

        BubbleLog.d("debug test message", eventData = testData)
        BubbleLog.v("verbose test message", eventData = testData)
        BubbleLog.i("info test message", eventData = testData)
        BubbleLog.w("warning test message", eventData = testData)
        BubbleLog.e("error test message", eventData = testData)

        val testLogs = testLogger.logs
        assertThat(testLogs).containsExactly(
            "d: debug test message | $testData",
            "v: verbose test message | $testData",
            "i: info test message | $testData",
            "w: warning test message | $testData",
            "e: error test message | $testData"
        ).inOrder()
    }

    @Test
    fun exceptionsDoNotAffectBubbleLogger() {
        val errorLogger = ExceptionThrowingLogger()
        assertThat(BubbleLog.loggers.size).isEqualTo(1)
        BubbleLog.addLogger(errorLogger)

        BubbleLog.d("debug test message")

        assertThat(errorLogger.exceptionThrown).isTrue()
        assertThat(getTrimmedLogLines()).isNotEmpty()
    }

    @Test
    fun dump_logsHistoryEventLoggerOutput() {
        BubbleLog.d("debug test message boolean = %b", false)
        BubbleLog.v("verbose test message int = %d", 1)

        val loggerOutput = getDumpOutput { BubbleLog.dump(pw = it) }
        assertThat(loggerOutput).contains("Bubbles events history:")
        assertThat(getTrimmedLogLines().size).isEqualTo(2)
    }

    private fun getTrimmedLogLines(): List<String> {
        return getDumpOutput { BubbleLog.dump(pw = it) }
            .split("\n")
            .drop(1)
            .dropLast(1)
            .map { it.trim() }
    }

    private fun getDumpOutput(dumpFunction: (PrintWriter) -> Unit): String {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        dumpFunction.invoke(printWriter)
        printWriter.flush() // Ensure all content is written to StringWriter
        return stringWriter.toString()
    }

    class TestLogger : DebugLogger {

        val logs = mutableListOf<String>()

        override fun d(message: String, vararg parameters: Any, eventData: String?) {
            logEvent("d: ${TextUtils.formatSimple(message, *parameters)}", eventData)
        }

        override fun v(message: String, vararg parameters: Any, eventData: String?) {
            logEvent("v: ${TextUtils.formatSimple(message, *parameters)}", eventData)
        }

        override fun i(message: String, vararg parameters: Any, eventData: String?) {
            logEvent("i: ${TextUtils.formatSimple(message, *parameters)}", eventData)
        }

        override fun w(message: String, vararg parameters: Any, eventData: String?) {
            logEvent("w: ${TextUtils.formatSimple(message, *parameters)}", eventData)
        }

        override fun e(message: String, vararg parameters: Any, eventData: String?) {
            logEvent("e: ${TextUtils.formatSimple(message, *parameters)}", eventData)
        }

        private fun logEvent(title: String, eventData: String? = null) {
            logs.add("$title | $eventData")
        }
    }

    class ExceptionThrowingLogger : DebugLogger {

        var exceptionThrown = false

        override fun d(message: String, vararg parameters: Any, eventData: String?) {
            throwException()
        }

        override fun v(message: String, vararg parameters: Any, eventData: String?) {
            throwException()
        }

        override fun i(message: String, vararg parameters: Any, eventData: String?) {
            throwException()
        }

        override fun w(message: String, vararg parameters: Any, eventData: String?) {
            throwException()
        }

        override fun e(message: String, vararg parameters: Any, eventData: String?) {
            throwException()
        }

        private fun throwException() {
            exceptionThrown = true
            throw RuntimeException()
        }
    }
}
