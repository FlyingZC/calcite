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
 */ // Apache许可证声明，说明代码版权和使用许可
package org.apache.calcite.linq4j.function; // 声明包名，该类属于calcite的linq4j功能模块下的function子包

/**
 * Compares values for equality.
 * 用于比较值是否相等，这是一个泛型接口，定义了对象相等性比较和哈希码计算的契约
 * 该接口在LINQ4J框架中用于实现类似Java中Comparator的比较器功能，但专门用于相等性判断
 * 主要用于集合操作（如DISTINCT、GROUP BY、JOIN等）中，确定两个对象是否相等以及计算哈希码
 * 通过提供自定义的比较器，可以支持复杂对象的相等性判断，而不仅仅是依赖equals()方法
 *
 * @param <T> Value type // 泛型参数T表示要比较的值的类型，可以是任何Java对象类型
 */ // 接口文档注释，说明接口的作用和泛型参数含义
public interface EqualityComparer<T> { // 定义一个泛型接口，用于比较类型T的对象的相等性和计算哈希码
  boolean equal(T v1, T v2); // 声明equal方法，用于比较两个类型T的对象v1和v2是否相等，返回true表示相等，false表示不相等

  int hashCode(T t); // 声明hashCode方法，用于计算类型T的对象t的哈希码，返回int类型的哈希值，需满足相等的对象具有相同的哈希码
} // 接口定义结束
