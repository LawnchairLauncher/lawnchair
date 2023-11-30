package app.lawnchair.root

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader

object RootCommandExecutor {
    private const val TAG = "ROOT"

    fun close(process: Process?, os: DataOutputStream?, osRes: BufferedReader?) {
        try {
            os?.writeBytes("exit\n")
            os?.flush()
            os?.close()
            osRes?.close()
            val exitCode = process?.waitFor()
            if (exitCode != 0) {
                Log.w(TAG, "su Process exited with code $exitCode")
            }
        } catch (e: IOException) {
            Log.w(TAG, "Error while closing", e)
        } catch (e: InterruptedException) {
            Log.w(TAG, "Interrupted while closing", e)
        }
    }

    private fun executeCommand(command: String, showResult: Boolean): List<String>? {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "/system/bin/sh"))
            val os = DataOutputStream(process.outputStream)
            val osRes = BufferedReader(InputStreamReader(process.inputStream))
            os.writeBytes("$command\n")
            os.flush()

            return if (showResult) {
                osRes.readLines()
            } else {
                null
            }
        } catch (e: IOException) {
            Log.w(TAG, "Error executing command", e)
        }
        return null
    }

    fun canRunRootCommands(): Boolean {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "/system/bin/sh"))
            val os = DataOutputStream(process.outputStream)
            val osRes = BufferedReader(InputStreamReader(process.inputStream))

            os.writeBytes("id\n")
            os.flush()
            val currUid = osRes.readLine()

            return if (currUid == null) {
                Log.d(TAG, "Can't get root access or denied by user")
                false
            } else if (currUid.contains("uid=0")) {
                Log.d(TAG, "Root access granted")
                true
            } else {
                Log.d(TAG, "Root access rejected: $currUid")
                false
            }
        } catch (e: IOException) {
            Log.d(TAG, "Root access rejected", e)
        }
        return false
    }

    fun execute(command: String) {
        executeCommand(command, false)
    }

    fun execute(commands: List<String>, showResult: Boolean): List<List<String>>? {
        try {
            val results = mutableListOf<List<String>>()

            if (commands.isEmpty() && !canRunRootCommands()) {
                throw SecurityException("Can't run root commands")
            }

            for (currCommand in commands) {
                Log.d(TAG, "Executing \"$currCommand\"")
                val result = executeCommand(currCommand, showResult)
                if (result != null) {
                    results.add(result)
                }
            }
            return results
        } catch (ex: SecurityException) {
            Log.w(TAG, "SecurityException", ex)
        } catch (ex: Exception) {
            Log.w(TAG, "Error executing internal operation", ex)
        }
        return null
    }
}