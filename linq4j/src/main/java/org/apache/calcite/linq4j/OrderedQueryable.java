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
// Apache许可证声明，说明该代码遵循Apache 2.0开源协议
// 声明版权归属Apache软件基金会，允许在遵守协议的前提下自由使用和修改
// 定义包名，表示该类属于org.apache.calcite.linq4j包，这是Calcite项目中用于LINQ(Language Integrated Query)功能的包
package org.apache.calcite.linq4j; // linq4j包提供了类似.NET LINQ的查询功能，用于在Java中进行声明式的数据查询操作

/**
 * Represents the result of applying a sorting operation to a {@link Queryable}.
 * 表示对Queryable应用排序操作后的结果接口
 * 
 * 该接口是Calcite LINQ4J框架中的核心接口之一，专门用于表示已经应用了排序操作的查询结果
 * 它继承了Queryable和ExtendedOrderedQueryable两个接口，因此既具备基本查询能力，又具备扩展的排序能力
 * 
 * 在LINQ查询链中，当调用OrderBy、OrderByDescending、ThenBy、ThenByDescending等排序方法时，
 * 会返回OrderedQueryable类型的对象，该对象保留了排序信息，可以继续进行后续的排序或查询操作
 * 
 * OrderedQueryable支持多级排序，即可以在一个排序的基础上继续添加排序条件(ThenBy操作)
 * 这种多级排序在查询复杂场景中非常有用，比如先按部门排序，再按工资排序
 *
 * @param <T> Element type
 * 泛型参数T表示查询结果中元素的类型，可以是任意Java类型
 * 例如：OrderedQueryable<Employee>表示包含Employee对象的有序查询结果
 */
public interface OrderedQueryable<T> // 定义公共接口OrderedQueryable，使用泛型参数T指定元素类型
    extends Queryable<T>, ExtendedOrderedQueryable<T> { // 该接口同时继承Queryable<T>和ExtendedOrderedQueryable<T>，继承了它们的全部方法
    // Queryable<T>提供基本的查询功能，如filter、map、groupBy等
    // ExtendedOrderedQueryable<T>提供扩展的排序功能，支持多级排序操作
    // 作为一个接口，这里没有定义任何新的方法，所有功能都从父接口继承
    // 这种设计遵循了接口隔离原则，OrderedQueryable只是标记当前查询已经应用了排序
} // 接口定义结束
