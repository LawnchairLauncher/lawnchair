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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UClass

/** Detector to identify custom usage of Android's Dialog within the Launcher3 codebase. */
class CustomDialogDetector : Detector(), SourceCodeScanner {

    override fun applicableSuperClasses(): List<String> {
        return listOf(DIALOG_CLASS_NAME)
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val superTypeClassNames = declaration.superTypes.mapNotNull { it.resolve()?.qualifiedName }
        if (superTypeClassNames.contains(DIALOG_CLASS_NAME)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "Class implements Dialog",
            )
        }
    }

    companion object {
        private const val DIALOG_CLASS_NAME = "android.app.Dialog"

        @JvmField
        val ISSUE =
            Issue.create(
                id = "IllegalUseOfCustomDialog",
                briefDescription = "dialogs should not be used in Launcher",
                explanation =
                    """
                Don't use custom Dialogs within the launcher code base, instead consider utilizing
                AbstractFloatingView to display content that should float above the launcher where
                it can be correctly managed for dismissal.
            """
                        .trimIndent(),
                category = Category.CORRECTNESS,
                priority = 10,
                severity = Severity.ERROR,
                implementation =
                    Implementation(CustomDialogDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )
    }
}
