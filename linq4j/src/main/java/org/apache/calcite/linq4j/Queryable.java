/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache软件基金会许可证声明
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议，查看NOTICE文件获取更多信息
 * this work for additional information regarding copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0 // 关于版权所有权的额外信息，ASF根据Apache 2.0许可证授权给您
 * (the "License"); you may not use this file except in compliance with // 您只能在遵守许可证的情况下使用此文件
 * the License.  You may obtain a copy of the License at // 您可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0 // Apache许可证2.0的在线地址
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意，否则
 * distributed under the License is distributed on an "AS IS" BASIS, // 根据许可证分发的软件是按"原样"基础分发的
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 不含任何明示或暗示的保证或条件
 * See the License for the specific language governing permissions and // 请参阅许可证以了解特定语言的权限和
 * limitations under the License. // 使用限制
 */
package org.apache.calcite.linq4j; // 定义包名为org.apache.calcite.linq4j，这是Calcite的LINQ4J模块

import org.checkerframework.framework.qual.Covariant; // 导入Checker框架的协变注解，用于类型系统的协变性检查

/**
 * Provides functionality to evaluate queries against a specific data source // 提供针对特定数据源执行查询的功能
 * wherein the type of the data is known. // 其中数据类型是已知的
 *
 * <p>Analogous to LINQ's System.Linq.IQueryable. // 类似于LINQ框架中的System.Linq.IQueryable接口
 *
 * @param <T> Element type // 泛型参数T表示查询中元素的类型
 */
@Covariant(0) // 使用协变注解，标记第0个类型参数（即T）是协变的，允许子类型转换
public interface Queryable<T> extends RawQueryable<T>, ExtendedQueryable<T> { // Queryable接口定义，继承自RawQueryable和ExtendedQueryable两个接口，T表示元素类型
} // 接口定义结束，Queryable是一个复合接口，结合了原始查询功能和扩展查询功能
