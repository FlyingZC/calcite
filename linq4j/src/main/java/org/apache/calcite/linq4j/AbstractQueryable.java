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
// Apache 开源许可证声明，说明代码的版权和使用条款
package org.apache.calcite.linq4j; // 声明包名，表示这个类属于 org.apache.calcite.linq4j 包

/**
 * Abstract implementation of the {@link Queryable} interface that implements
 * the extension methods.
 * Queryable 接口的抽象实现，实现了扩展方法
 *
 * <p>It is helpful to derive from this class if you are implementing
 * {@code Queryable}, because {@code Queryable} has so many extension methods,
 * but it is not required.
 * 如果你要实现 Queryable 接口，从这个类派生会很有帮助，因为 Queryable 有很多扩展方法，但这不是必需的
 *
 * @param <T> Element type
 * 泛型参数 T 表示元素类型，即这个 Queryable 集合中包含的元素类型
 */
// AbstractQueryable 是一个抽象类，继承自 DefaultQueryable<T>，并实现了 Queryable<T> 接口
// DefaultQueryable 提供了 Queryable 接口的默认实现，包含了很多扩展方法
// Queryable 是 LINQ4J 框架中的核心接口，表示可查询的数据集合，支持延迟执行和表达式树
public abstract class AbstractQueryable<T> extends DefaultQueryable<T>
    implements Queryable<T> {
// 类体为空，因为所有实现都继承自 DefaultQueryable<T>
// 这个类存在的意义是提供一个便捷的基类，让开发者可以轻松实现自定义的 Queryable
// 通过继承 AbstractQueryable，开发者只需要关注特定的查询逻辑，而不需要实现所有扩展方法
}
