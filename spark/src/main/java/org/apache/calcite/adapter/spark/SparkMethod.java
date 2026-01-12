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
// Apache许可证声明，说明该文件遵循Apache 2.0许可证
package org.apache.calcite.adapter.spark; // 定义包名，该类位于org.apache.calcite.adapter.spark包下，属于Calcite的Spark适配器模块

import org.apache.calcite.DataContext; // 导入Calcite的数据上下文类，用于在查询执行过程中传递运行时信息和参数
import org.apache.calcite.linq4j.Enumerable; // 导入Calcite的LINQ4J可枚举接口，用于表示可枚举的数据集合
import org.apache.calcite.linq4j.tree.Types; // 导入Calcite的类型工具类，用于反射查找方法

import org.apache.spark.api.java.JavaRDD; // 导入Spark的JavaRDD类，表示Spark中的弹性分布式数据集
import org.apache.spark.api.java.JavaSparkContext; // 导入Spark的JavaSparkContext类，表示Spark的Java编程入口
import org.apache.spark.api.java.function.FlatMapFunction; // 导入Spark的FlatMapFunction函数式接口，用于实现flatMap转换操作

import java.lang.reflect.Method; // 导入Java反射API的Method类，用于表示方法对象
import java.util.HashMap; // 导入Java集合框架的HashMap类，用于存储键值对映射关系

/**
 * Built-in methods in the Spark adapter.
 * Spark适配器中内置的方法枚举类，用于统一管理和访问Spark适配器中的关键方法
 * 
 * 该枚举类的主要作用：
 * 1. 定义Spark适配器中所有需要反射调用的方法常量
 * 2. 提供方法查找功能，通过Method对象反向查找对应的SparkMethod枚举值
 * 3. 封装Spark运行时相关的核心操作方法，如RDD创建、转换、上下文获取等
 * 4. 便于代码中统一引用这些方法，避免硬编码方法名和参数类型
 * 
 * @see org.apache.calcite.util.BuiltInMethod // 参考Calcite的BuiltInMethod类，这是类似的设计模式
 */
public enum SparkMethod { // 定义一个枚举类，枚举Spark适配器中的所有内置方法
  // 枚举常量1：AS_ENUMERABLE - 表示SparkRuntime类的asEnumerable方法，用于将JavaRDD转换为Enumerable
  // 参数说明：SparkRuntime.class - 方法所在的类，"asEnumerable" - 方法名，JavaRDD.class - 方法参数类型
  AS_ENUMERABLE(SparkRuntime.class, "asEnumerable", JavaRDD.class), // 该方法用于将Spark的RDD转换为Calcite的Enumerable，实现Spark数据到Calcite查询引擎的桥接
  // 枚举常量2：ARRAY_TO_RDD - 表示SparkRuntime类的createRdd方法，用于从数组创建JavaRDD
  // 参数说明：SparkRuntime.class - 方法所在的类，"createRdd" - 方法名，JavaSparkContext.class - 第一个参数类型，Object[].class - 第二个参数类型
  ARRAY_TO_RDD(SparkRuntime.class, "createRdd", JavaSparkContext.class, // 该方法用于将Java数组转换为Spark的RDD，实现内存数据到分布式数据集的转换
      Object[].class),
  // 枚举常量3：CREATE_RDD - 表示SparkRuntime类的createRdd方法的重载版本，用于从Enumerable创建JavaRDD
  // 参数说明：SparkRuntime.class - 方法所在的类，"createRdd" - 方法名，JavaSparkContext.class - 第一个参数类型，Enumerable.class - 第二个参数类型
  CREATE_RDD(SparkRuntime.class, "createRdd", JavaSparkContext.class, // 该方法用于将Calcite的Enumerable转换为Spark的RDD，实现Calcite数据到Spark分布式计算的转换
      Enumerable.class),
  // 枚举常量4：GET_SPARK_CONTEXT - 表示SparkRuntime类的getSparkContext方法，用于获取Spark上下文
  // 参数说明：SparkRuntime.class - 方法所在的类，"getSparkContext" - 方法名，DataContext.class - 方法参数类型
  GET_SPARK_CONTEXT(SparkRuntime.class, "getSparkContext", DataContext.class), // 该方法从Calcite的数据上下文中提取Spark上下文，用于在查询执行过程中访问Spark环境
  // 枚举常量5：RDD_FLAT_MAP - 表示JavaRDD类的flatMap方法，用于对RDD进行flatMap转换操作
  // 参数说明：JavaRDD.class - 方法所在的类，"flatMap" - 方法名，FlatMapFunction.class - 方法参数类型
  RDD_FLAT_MAP(JavaRDD.class, "flatMap", FlatMapFunction.class), // 该方法是Spark的核心转换操作，用于将RDD中的每个元素映射为0到多个输出元素
  // 枚举常量6：FLAT_MAP_FUNCTION_CALL - 表示FlatMapFunction接口的call方法，用于执行flatMap的具体转换逻辑
  // 参数说明：FlatMapFunction.class - 方法所在的接口，"call" - 方法名，Object.class - 方法参数类型
  FLAT_MAP_FUNCTION_CALL(FlatMapFunction.class, "call", Object.class); // 该方法是FlatMapFunction的核心方法，定义了如何将输入对象转换为输出集合

  @SuppressWarnings("ImmutableEnumChecker") // 抑制编译器警告，因为枚举中的method字段是可变的（非final），但在这里是安全的
  public final Method method; // 成员变量：存储反射得到的Method对象，表示该枚举常量对应的具体Java方法，public final修饰保证外部可访问且不可修改

  // 静态成员变量：用于建立Method对象到SparkMethod枚举值的反向映射关系
  // HashMap用于存储<Method, SparkMethod>的键值对，实现通过Method对象快速查找对应的SparkMethod枚举值
  private static final HashMap<Method, SparkMethod> MAP = new HashMap<>(); // 该映射表在类加载时初始化，用于lookup方法实现反向查找

  // 静态初始化块：在类加载时执行，用于初始化MAP映射表
  static { // 静态块开始，在类加载时自动执行
    for (SparkMethod method : SparkMethod.values()) { // 遍历SparkMethod枚举的所有枚举值
      MAP.put(method.method, method); // 将每个枚举值的method字段作为键，枚举值本身作为值存入MAP，建立双向映射关系
    } // 遍历结束，此时MAP包含了所有枚举常量的映射
  } // 静态初始化块结束

  // 构造方法：私有构造方法，用于创建枚举常量并初始化method字段
  // 参数说明：clazz - 方法所在的类对象，methodName - 方法名称字符串，argumentTypes - 方法的参数类型数组（可变参数）
  SparkMethod(Class clazz, String methodName, Class... argumentTypes) { // 构造方法定义，使用可变参数接收多个参数类型
    this.method = Types.lookupMethod(clazz, methodName, argumentTypes); // 使用Types工具类的lookupMethod方法通过反射查找指定类中指定名称和参数类型的方法，并赋值给method字段
  } // 构造方法结束，每个枚举常量在声明时都会调用此构造方法初始化其method字段

  // 静态方法：根据Method对象查找对应的SparkMethod枚举值
  // 参数说明：method - 要查找的Method对象
  // 返回值：如果找到对应的SparkMethod枚举值则返回，否则返回null
  public static SparkMethod lookup(Method method) { // 定义静态公共方法，供外部调用进行反向查找
    return MAP.get(method); // 从MAP映射表中根据Method对象作为键查找对应的SparkMethod枚举值并返回
  } // 方法结束，实现了从Method到SparkMethod的反向映射查找功能
} // 枚举类定义结束，SparkMethod是一个包含6个枚举常量的枚举类，提供了Spark适配器中核心方法的统一管理和查找功能
