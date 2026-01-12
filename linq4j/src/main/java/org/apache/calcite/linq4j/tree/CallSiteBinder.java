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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证协议
// 定义包路径：org.apache.calcite.linq4j.tree，表示该类属于Calcite项目中linq4j模块的tree包
package org.apache.calcite.linq4j.tree;

/**
 * Creates a {@link DynamicExpression} that represents a dynamic operation bound
 * by the provided {@code CallSiteBinder}.
 * // 创建一个DynamicExpression（动态表达式），该表达式表示由提供的CallSiteBinder绑定的动态操作
 * // CallSiteBinder是一个接口，用于动态绑定调用站点的表达式生成器
 * // 在LINQ4J中，动态表达式是指在运行时才确定具体调用的表达式，而不是在编译时确定
 * // 该接口的主要作用是为动态方法调用提供绑定机制，允许在运行时根据实际参数类型和方法签名来生成相应的表达式树
 * // 这对于实现动态语言特性、反射调用、以及延迟绑定等场景非常重要
 * // DynamicExpression是表达式树中表示动态操作的特殊节点类型
 */
public interface CallSiteBinder {
}
