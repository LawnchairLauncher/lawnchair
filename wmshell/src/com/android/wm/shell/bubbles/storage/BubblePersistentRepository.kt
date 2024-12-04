/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.wm.shell.bubbles.storage

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import android.util.SparseArray
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class BubblePersistentRepository(context: Context) {

    private val bubbleFile: AtomicFile = AtomicFile(File(context.filesDir,
            "overflow_bubbles.xml"), "overflow-bubbles")

    fun persistsToDisk(bubbles: SparseArray<List<BubbleEntity>>): Boolean {
        if (DEBUG) Log.d(TAG, "persisting ${bubbles.size()} bubbles")
        synchronized(bubbleFile) {
            val stream: FileOutputStream = try { bubbleFile.startWrite() } catch (e: IOException) {
                Log.e(TAG, "Failed to save bubble file", e)
                return false
            }
            try {
                writeXml(stream, bubbles)
                bubbleFile.finishWrite(stream)
                if (DEBUG) Log.d(TAG, "persisted ${bubbles.size()} bubbles")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save bubble file, restoring backup", e)
                bubbleFile.failWrite(stream)
            }
        }
        return false
    }

    fun readFromDisk(): SparseArray<List<BubbleEntity>> {
        synchronized(bubbleFile) {
            if (!bubbleFile.exists()) return SparseArray()
            try { return bubbleFile.openRead().use(::readXml) } catch (e: Throwable) {
                Log.e(TAG, "Failed to open bubble file", e)
            }
            return SparseArray()
        }
    }
}

private const val TAG = "BubblePersistentRepository"
private const val DEBUG = false
