/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache软件基金会许可证声明开头
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议,查看NOTICE文件了解更多版权信息
 * this work for additional information regarding copyright ownership.  // 关于版权所有权的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0 // ASF根据Apache 2.0许可证将此文件授权给您
 * (the "License"); you may not use this file except in compliance with // "许可证");除非符合许可证要求,否则您不得使用此文件
 * the License.  You may obtain a copy of the License at // 您可以在以下位置获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0 // Apache许可证2.0的官方URL地址
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意,否则
 * distributed under the License is distributed on an "AS IS" BASIS, // 根据许可证分发的软件是按"原样"基础分发的
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 不提供任何形式的明示或暗示的保证或条件
 * See the License for the specific language governing permissions and // 请参阅许可证以了解特定语言的权限和
 * limitations under the License. // 许可证下的限制
 */

/**
 * Calcite adapters. // Calcite适配器包级别的文档注释
 *
 * <p>An adapter allows Calcite to access data in a particular data source as // 适配器允许Calcite访问特定数据源中的数据,就像
 * if it were a collection of tables in a schema. Each adapter typically // 它是schema中的一组表一样。每个适配器通常包含
 * contains an implementation of {@link org.apache.calcite.schema.SchemaFactory} // SchemaFactory接口的实现和一些实现其他schema SPI的类
 * and some classes that implement other schema SPIs. // 以及一些实现其他schema SPI(服务提供者接口)的类
 *
 * <p>To use an adapter, include a custom schema in a JSON model file: // 要使用适配器,需要在JSON模型文件中包含一个自定义schema
 *
 * <blockquote><pre> // 引用块,用于展示代码示例
 *    schemas: [ // schemas数组开始,定义一个或多个schema配置
 *      { // 第一个schema配置对象开始
 *        type: 'custom', // schema类型为'custom',表示自定义schema
 *        name: 'My Custom Schema', // schema的名称为'My Custom Schema'
 *        factory: 'com.acme.MySchemaFactory', // 指定SchemaFactory实现类的全限定名
 *        operand: {a: 'foo', b: [1, 3.5] } // 传递给工厂类的操作数参数,可以是任意JSON对象
 *      } // 第一个schema配置对象结束
 *   ] // schemas数组结束
 * </pre> // 预格式化文本块结束
 * </blockquote> // 引用块结束
 */
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.FIELD) // 包级别注解:声明所有字段默认为非空(NonNull),使用CheckerFramework进行空值检查
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.PARAMETER) // 包级别注解:声明所有方法参数默认为非空(NonNull),使用CheckerFramework进行空值检查
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.RETURN) // 包级别注解:声明所有方法返回值默认为非空(NonNull),使用CheckerFramework进行空值检查
package org.apache.calcite.adapter; // 包声明:声明此package-info.java文件属于org.apache.calcite.adapter包

import org.checkerframework.checker.nullness.qual.NonNull; // 导入CheckerFramework的NonNull注解,用于标记非空类型
import org.checkerframework.framework.qual.DefaultQualifier; // 导入CheckerFramework的DefaultQualifier注解,用于设置默认的空值限定符
import org.checkerframework.framework.qual.TypeUseLocation; // 导入CheckerFramework的TypeUseLocation枚举,用于指定注解应用的位置类型
