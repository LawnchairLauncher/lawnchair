/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.plugins.processor

import java.io.BufferedWriter
import java.io.Writer

/** [JavaFileWriter] has a collection of utility functions for generating java files. */
class JavaFileWriter(val writer: TabbedWriter) : TabbedWriter by writer {
    fun pkg(pkg: String) {
        line("package $pkg;")
        line()
    }

    fun imports(vararg importGroups: List<String>) {
        var written = mutableSetOf<String>()
        for (importGroup in importGroups) {
            if (importGroup.size > 0) {
                for (importTarget in importGroup) {
                    if (written.add(importTarget)) {
                        line("import $importTarget;")
                    }
                }
                line()
            }
        }
    }

    fun cls(
        className: String,
        isFinal: Boolean = false,
        visibility: String = "public",
        baseType: String? = null,
        interfaces: List<String> = listOf(),
        contents: JavaClassWriter.() -> Unit = {},
    ) {
        val writer = JavaClassWriter(this.writer, className)
        braceBlock(
            buildString {
                append(visibility)
                if (isFinal) {
                    append(" final")
                }

                append(" class ")
                append(className)

                if (baseType != null) {
                    append(" extends ")
                    append(baseType)
                }

                if (interfaces.size > 0) {
                    append(" implements ")
                    var isFirst = true
                    for (interfaceType in interfaces) {
                        if (!isFirst) append(", ")
                        append(interfaceType)
                        isFirst = false
                    }
                }
            }
        ) {
            writer.contents()
        }
    }

    companion object {
        fun writeTo(writer: Writer, write: JavaFileWriter.() -> Unit) {
            BufferedWriter(writer).use { bufferedWriter ->
                JavaFileWriter(TabbedWriterImpl(bufferedWriter)).write()
            }
        }
    }
}

/** [JavaClassWriter] has a collection of utility functions for generating java classes. */
class JavaClassWriter(val writer: TabbedWriter, val className: String) : TabbedWriter by writer {
    fun constructor(
        visibility: String = "public",
        args: JavaMethodWriter.() -> Unit = {},
        contents: JavaMethodWriter.() -> Unit = {},
    ) {
        method(
            methodName = "$className",
            returnType = "",
            visibility = visibility,
            args = args,
            contents = contents,
        )
    }

    fun method(
        methodName: String,
        visibility: String = "public",
        returnType: String = "void",
        isStatic: Boolean = false,
        args: JavaMethodWriter.() -> Unit = {},
        contents: JavaMethodWriter.() -> Unit = {},
    ) {
        val writer =
            JavaMethodWriter(
                this.writer,
                className = className,
                methodName = methodName,
                returnType = returnType,
                isStatic = isStatic,
            )

        parenBlock(
            buildString {
                append(visibility)
                append(" ")

                if (isStatic) {
                    append("static ")
                }

                if (!returnType.isNullOrEmpty()) {
                    append(returnType)
                    append(" ")
                }

                append(methodName)
            }
        ) {
            writer.args()
            if (!writer.isFirstArg) {
                completeLine("")
            }
        }

        braceBlock { writer.contents() }
        line()
    }
}

/** [JavaMethodWriter] has a collection of utility functions for generating java functions. */
class JavaMethodWriter(
    val writer: TabbedWriter,
    val className: String,
    val methodName: String,
    val returnType: String,
    val isStatic: Boolean,
) : TabbedWriter by writer {
    val isVoid: Boolean = returnType == "void"

    var isFirstArg = true
        private set

    var callArgs = StringBuilder()
        private set

    fun arg(name: String, type: String) {
        if (!isFirstArg) {
            completeLine(",")
            callArgs.append(", ")
        }

        startLine("$type $name")
        callArgs.append(name)
        isFirstArg = false
    }
}
