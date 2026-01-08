/* // Apache 软件基金会许可证声明开头
 * Licensed to the Apache Software Foundation (ASF) under one or more // 授权给 Apache 软件基金会（ASF）使用，基于一个或多个
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议。查看随此工作分发的 NOTICE 文件
 * this work for additional information regarding copyright ownership.  // 以获取关于版权所有权的更多信息
 * The ASF licenses this file to you under the Apache License, Version 2.0 // ASF 根据 Apache 许可证版本 2.0 授予您使用此文件的许可
 * (the "License"); you may not use this file except in compliance with // （"许可证"）；除非遵守许可证，否则您不得使用此文件
 * the License.  You may obtain a copy of the License at // 您可以在以下位置获取许可证副本
 * // Apache 许可证官方下载地址
 * http://www.apache.org/licenses/LICENSE-2.0 // Apache 许可证 2.0 版本的官方 URL
 * // 除非适用法律要求或书面同意，否则分发此软件
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意，否则分发此软件
 * distributed under the License is distributed on an "AS IS" BASIS, // 是基于"原样"基础分发的，不提供任何形式的明示或
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 暗示的担保或条件。查看许可证以了解特定语言的
 * See the License for the specific language governing permissions and // 许可权限和限制
 * limitations under the License. // 许可证中的特定语言所管辖的权限和限制
 */ // 许可证声明结束

/** // Javadoc 注释开始，用于描述整个包的用途
 * Tests for Calcite. // Calcite 框架的测试包说明，表明此包包含 Calcite 的所有测试类
 */ // Javadoc 注释结束
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.FIELD) // Checker Framework 注解：指定此包中所有字段默认为非空（NonNull），防止空指针异常
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.PARAMETER) // Checker Framework 注解：指定此包中所有方法参数默认为非空，提高代码健壮性
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.RETURN) // Checker Framework 注解：指定此包中所有方法返回值默认为非空，确保返回值不为 null
package org.apache.calcite.test; // 包声明，定义此包的完整名称为 org.apache.calcite.test，这是 Calcite 测试模块的根包

import org.checkerframework.checker.nullness.qual.NonNull; // 导入 Checker Framework 的 NonNull 注解，用于标记值不能为 null
import org.checkerframework.framework.qual.DefaultQualifier; // 导入 Checker Framework 的 DefaultQualifier 注解，用于设置默认的非空限定符
import org.checkerframework.framework.qual.TypeUseLocation; // 导入 Checker Framework 的 TypeUseLocation 枚举，用于指定注解应用的位置类型
