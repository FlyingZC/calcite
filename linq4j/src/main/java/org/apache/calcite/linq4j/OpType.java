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
 */
// Apache许可证声明，说明该文件遵循Apache 2.0许可证，允许在特定条件下使用和修改
package org.apache.calcite.linq4j; // 包声明，表示该类属于org.apache.calcite.linq4j包，这是Calcite项目中用于语言集成查询(LINQ)的包

/**
 * Operator type.
 * 操作符类型枚举类，用于标识和分类LINQ查询中的不同操作类型
 * 该枚举定义了LINQ(Language Integrated Query)查询中可用的各种操作符类型
 * LINQ是一种将查询功能直接集成到编程语言中的技术，Calcite通过linq4j模块在Java中实现了类似功能
 */
public enum OpType { // 定义一个公共枚举类OpType，用于表示操作符类型
  WHERE, // WHERE操作符，表示过滤操作，用于根据条件筛选数据集中的元素，类似于SQL中的WHERE子句
} // 枚举定义结束，目前只定义了一个WHERE操作符类型
