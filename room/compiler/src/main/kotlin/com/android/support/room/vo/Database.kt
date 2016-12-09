/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.support.room.vo

import com.squareup.javapoet.ClassName
import javax.lang.model.element.Element
import javax.lang.model.type.TypeMirror

/**
 * Holds information about a class annotated with Database.
 */
data class Database(val element : Element,
                    val type : TypeMirror,
                    val entities : List<Entity>,
                    val daoMethods : List<DaoMethod>) {
    val typeName by lazy { ClassName.get(type) as ClassName }

    val implClassName by lazy {
        "${typeName.simpleName()}_Impl"
    }

    val implTypeName by lazy {
        ClassName.get(typeName.packageName(), implClassName)
    }
}
