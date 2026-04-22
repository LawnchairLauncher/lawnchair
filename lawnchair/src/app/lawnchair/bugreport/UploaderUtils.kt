package app.lawnchair.bugreport

object UploaderUtils {

    private val katbinService = KatbinService.create()
    const val IS_ALIVE_AVAILABLE = true

    suspend fun upload(report: BugReport): String {
        val body = KatbinUploadBody(KatbinPaste(content = report.contents))
        val result = katbinService.upload(body)
        return "https://katb.in/${result.id}"
    }
}
