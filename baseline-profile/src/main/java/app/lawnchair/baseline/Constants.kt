package app.lawnchair.baseline

import androidx.test.platform.app.InstrumentationRegistry

object Constants {
    val PACKAGE_NAME: String
        get() = InstrumentationRegistry.getArguments()
            .getString("androidx.benchmark.targetPackageName")
            ?: InstrumentationRegistry.getInstrumentation().targetContext.packageName
                .removeSuffix(".baseline")
}
