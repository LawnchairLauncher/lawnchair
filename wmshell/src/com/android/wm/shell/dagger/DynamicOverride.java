/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.dagger;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Qualifier;

/**
 * This is a qualifier that Shell uses to workaround an issue with providing nullable optionals
 * which are by default unbound.
 *
 * For example, ideally we would have this scenario:
 * BaseModule:
 *   @BindsOptionalOf
 *   abstract Optional<Interface> optionalInterface();
 *
 * SpecializedModule:
 *   @Provides
 *   static Interface providesInterface() {
 *       return new InterfaceImpl();
 *   }
 *
 * However, if the interface is supposed to be provided dynamically, then Dagger is not able to bind
 * the optional interface to a null instance, and @BindsOptionalOf does not support @Nullable
 * instances of the interface provided by the specialized module.
 *
 * For example, this does not work:
 * BaseModule:
 *   @BindsOptionalOf
 *   abstract Optional<Interface> optionalInterface();
 *
 * SpecializedModule:
 *   @Provides
 *   static Interface providesInterface() {
 *       if (systemSupportsInterfaceFeature) {
 *           return new InterfaceImpl();
 *       } else {
 *           return null;
 *       }
 *   }
 *
 * To workaround this, we can instead upstream the check (assuming it can be upstreamed into the
 * base module), and then always provide a non-null instance in the specialized module.
 *
 * For example:
 * BaseModule:
 *   @BindsOptionalOf
 *   @DynamicOverride
 *   abstract Interface dynamicInterface();
 *
 *   @Provides
 *   static Optional<Interface> providesOptionalInterface(
 *           @DynamicOverride Optional<Interface> interface) {
 *       if (systemSupportsInterfaceFeature) {
 *           return interface;
 *       }
 *       return Optional.empty();
 *   }
 *
 * SpecializedModule:
 *   @Provides
 *   @DynamicOverride
 *   static Interface providesInterface() {
 *       return new InterfaceImpl();
 *   }
 *
 * This is also useful in cases where there needs to be a default implementation in the base module
 * which is also overridable in the specialized module.  This isn't generally recommended, but
 * due to the nature of Shell modules being referenced from a number of various projects, this
 * can be useful for *required* components that
 * 1) clearly identifies which are intended for overriding in the base module, and
 * 2) allows us to declare a default implementation in the base module, without having to force
 *    every SysUI impl to explicitly provide it (if a large number of them share the default impl)
 *
 * For example, this uses the same setup as above, but the interface provided (if bound) is used
 * otherwise the default is created:
 *
 * BaseModule:
 *   @BindsOptionalOf
 *   @DynamicOverride
 *   abstract Interface dynamicInterface();
 *
 *   @Provides
 *   static Optional<Interface> providesOptionalInterface(
 *           @DynamicOverride Optional<Interface> overrideInterfaceImpl) {
 *       if (overrideInterfaceImpl.isPresent()) {
 *           return overrideInterfaceImpl.get();
 *       }
 *       return new DefaultImpl();
 *   }
 *
 * SpecializedModule:
 *   @Provides
 *   @DynamicOverride
 *   static Interface providesInterface() {
 *       return new SuperSpecialImpl();
 *   }
 */
@Documented
@Inherited
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface DynamicOverride {}