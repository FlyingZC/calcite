/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */ // Apache 许可证声明，声明版权归属和使用许可条件

/**
 * Provides an implementation of relational expressions using an interpreter.
 * 提供使用解释器实现关系代数表达式的功能
 *
 * <p>The implementation is not efficient compared to generated code, but
 * preparation time is less, and so the total prepare + execute time is
 * competitive for queries over small data sets.
 * 该实现与生成的代码相比效率不高，但准备时间更短，因此对于小数据集的查询，
 * 总的准备+执行时间具有竞争力
 */ // 包级别的 Javadoc 文档，说明 interpreter 包的作用和特点
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.FIELD) // 指定字段类型默认为非空，使用 Checker Framework 进行空值检查
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.PARAMETER) // 指定参数类型默认为非空，使用 Checker Framework 进行空值检查
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.RETURN) // 指定返回值类型默认为非空，使用 Checker Framework 进行空值检查
package org.apache.calcite.interpreter; // 声明当前包名为 org.apache.calcite.interpreter，这是 Calcite 解释器模块的包

import org.checkerframework.checker.nullness.qual.NonNull; // 导入 Checker Framework 的 NonNull 注解，用于标记非空类型
import org.checkerframework.framework.qual.DefaultQualifier; // 导入 DefaultQualifier 注解，用于设置默认的非空类型限定符
import org.checkerframework.framework.qual.TypeUseLocation; // 导入 TypeUseLocation 枚举，用于指定类型使用位置（字段、参数、返回值等）
