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
 */ // Apache许可证声明,说明该代码遵循Apache 2.0开源协议
package org.apache.calcite.linq4j.function; // 声明该接口所在的包路径,位于calcite的linq4j功能包下

/**
 * Function with one parameter returning a native {@code boolean} value.
 * // 这是一个接受一个参数并返回原生boolean值的函数接口
 * // Predicate1是LINQ(Language Integrated Query)4j框架中的核心接口之一
 * // 它定义了一个单参数的谓词(断言)函数,用于测试某个条件是否成立
 * // 该接口继承自Function<Boolean>,表示它是一个返回Boolean类型的函数
 * // 在函数式编程中,谓词通常用于过滤、判断、验证等场景
 * // 例如:在集合过滤时,可以用Predicate1来判断元素是否满足某个条件
 *
 * @param <T0> Type of argument #0
 * // T0是泛型类型参数,表示该谓词函数接受的第一个(也是唯一一个)参数的类型
 * // 使用泛型使得Predicate1可以应用于任何类型的对象
 */ // 例如:Predicate1<String>可以判断字符串,Predicate1<Integer>可以判断整数
public interface Predicate1<T0> extends Function<Boolean> { // 定义Predicate1接口,它是一个泛型接口,继承自Function<Boolean>
  /**
   * Predicate that always evaluates to {@code true}.
   * // 这是一个总是返回true的谓词常量
   * // 它是一个Predicate1<Object>类型的实例,接受任意类型的对象,总是返回true
   * // 使用场景:当需要接受所有元素时,可以使用TRUE作为过滤条件
   * // 例如:在filter操作中,使用TRUE会保留所有元素
   * // 这是一个Lambda表达式实现:v0 -> true,表示忽略参数v0,直接返回true
   *
   * @see Functions#truePredicate1()
   * // 参考Functions类中的truePredicate1()方法,该方法可能返回类似的true谓词
   */ // TRUE是预定义的常量,避免重复创建相同功能的谓词对象
  Predicate1<Object> TRUE = v0 -> true; // 定义TRUE常量,使用Lambda表达式实现,接受任意对象v0,始终返回true

  /**
   * Predicate that always evaluates to {@code false}.
   * // 这是一个总是返回false的谓词常量
   * // 它是一个Predicate1<Object>类型的实例,接受任意类型的对象,总是返回false
   * // 使用场景:当需要拒绝所有元素时,可以使用FALSE作为过滤条件
   * // 例如:在filter操作中,使用FALSE会过滤掉所有元素
   * // 这是一个Lambda表达式实现:v0 -> false,表示忽略参数v0,直接返回false
   *
   * @see Functions#falsePredicate1()
   * // 参考Functions类中的falsePredicate1()方法,该方法可能返回类似的false谓词
   */ // FALSE是预定义的常量,避免重复创建相同功能的谓词对象
  Predicate1<Object> FALSE = v0 -> false; // 定义FALSE常量,使用Lambda表达式实现,接受任意对象v0,始终返回false

  boolean apply(T0 v0); // 定义抽象方法apply,这是Predicate1接口的核心方法
} // apply方法接受一个T0类型的参数v0,返回一个boolean值,用于判断v0是否满足某个条件
  // 实现类需要提供具体的判断逻辑,例如:v0 > 10, v0.startsWith("A")等
  // 该方法是函数式接口的唯一抽象方法,使得Predicate1可以使用Lambda表达式或方法引用来创建实例
  // 在LINQ操作中,apply方法会被框架调用,用于对集合中的每个元素进行判断
  // 返回true表示元素满足条件,保留该元素;返回false表示元素不满足条件,过滤掉该元素
