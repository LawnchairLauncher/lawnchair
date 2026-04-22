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

package com.android.launcher3.util.rule

import android.content.Context
import android.os.FileUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class ZipFilesRule(val context: Context, val name: String) : TestRule {

    var resultsZip: ZipOutputStream? = null

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            @Throws(Throwable::class)
            override fun evaluate() {
                resultsZip =
                    ZipOutputStream(
                        FileOutputStream(
                            File(context.filesDir, "${description.testClass.simpleName}-$name.zip")
                        )
                    )
                try {
                    base.evaluate() // This will run the test.
                } finally {
                    resultsZip?.close()
                }
            }
        }
    }

    fun write(file: File) {
        if (resultsZip !is ZipOutputStream) {
            throw RuntimeException(
                "Cannot save files before the test rule starts! We need the rule to start to get the name of the test"
            )
        }
        resultsZip!!.let {
            it.putNextEntry(ZipEntry(file.name))
            FileUtils.copy(FileInputStream(file), it)
            it.closeEntry()
        }
    }
}
