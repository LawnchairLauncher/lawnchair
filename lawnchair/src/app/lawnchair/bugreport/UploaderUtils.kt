package app.lawnchair.bugreport

import android.content.Context

object UploaderUtils {

    private val ctrlVService = CtrlVService.create()
    const val isAvailable = true

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun upload(context: Context, report: BugReport): String {
        val result = ctrlVService.upload(report.getTitle(context), report.contents)
        return "https://ctrl-v.app/raw/${result.hash}"
    }
}
