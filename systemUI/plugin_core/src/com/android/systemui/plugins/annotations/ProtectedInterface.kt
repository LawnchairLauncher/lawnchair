/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.systemui.plugins.annotations

/**
 * This annotation marks denotes that an interface should use a proxy layer to protect the plugin
 * host from crashing due to the [Exception] types originating within the plugin's implementation.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class ProtectedInterface(vararg val exTypes: String) {
    companion object {
        val Default = ProtectedInterface("java.lang.Exception", "java.lang.LinkageError")
    }
}

/**
 * This annotation specifies any additional imports that the processor will require when generating
 * the proxy implementation for the target interface. The interface in question must still be
 * annotated with [ProtectedInterface].
 */
@Repeatable
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class GeneratedImport(val extraImport: String)

/**
 * This annotation provides default values to return when the proxy implementation catches a target
 * [Exception]. The string specified should be a simple but valid java statement. In most cases it
 * should be a return statement of the appropriate type, but in some cases throwing a known
 * exception type may be preferred.
 *
 * This annotation is not required for methods that return void, but will behave the same way.
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
)
@Retention(AnnotationRetention.BINARY)
annotation class ProtectedReturn(val statement: String)

/**
 * Some very simple properties and methods need not be protected by the proxy implementation. This
 * annotation can be used to omit the normal try-catch wrapper the proxy is using. These members
 * will instead be a direct passthrough.
 *
 * It should only be used for members where the plugin implementation is expected to be exceedingly
 * simple. Any member marked with this annotation should be no more complex than kotlin's automatic
 * properties, and make no other method calls whatsoever.
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
)
@Retention(AnnotationRetention.BINARY)
annotation class SimpleProperty
