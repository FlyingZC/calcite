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
 */  // Apache许可证声明，说明代码的版权和使用条款
package org.apache.calcite.adapter.arrow;  // 定义包名，说明这个类属于org.apache.calcite.adapter.arrow包，这是Calcite的Arrow适配器包

import org.apache.calcite.DataContext;  // 导入DataContext类，用于表示查询执行时的上下文环境，包含会话信息、参数等
import org.apache.calcite.linq4j.tree.Types;  // 导入Types工具类，用于通过反射查找方法，是LINQ4J表达式树的一部分
import org.apache.calcite.util.ImmutableIntList;  // 导入ImmutableIntList类，表示不可变的整数列表，用于存储字段索引

import com.google.common.collect.ImmutableMap;  // 导入Google Guava的ImmutableMap类，表示不可变的Map，用于方法查找

import java.lang.reflect.Method;  // 导入Method类，用于表示Java反射中的方法对象
import java.util.List;  // 导入List接口，表示有序集合

/**
 * Built-in methods in the Arrow adapter.  // 类注释：Arrow适配器中的内置方法枚举
 *
 * @see org.apache.calcite.util.BuiltInMethod  // 参考Calcite的BuiltInMethod类，说明这个类的设计模式类似于BuiltInMethod
 */  // 这是一个枚举类，用于注册和管理Arrow适配器中需要通过反射调用的方法
@SuppressWarnings("ImmutableEnumChecker")  // 抑制警告：告诉编译器这个枚举类虽然包含可变字段（Method），但是是安全的
enum ArrowMethod {  // 定义一个枚举类，用于枚举Arrow适配器中所有需要反射调用的方法
  ARROW_QUERY(ArrowTable.class, "query", DataContext.class,  // 定义一个枚举常量ARROW_QUERY，表示ArrowTable类的query方法
      ImmutableIntList.class, List.class);  // 该方法接受三个参数：DataContext上下文、ImmutableIntList字段索引列表、List结果列表

  final Method method;  // 成员变量：存储枚举常量对应的Java反射Method对象，用于后续的反射调用

  static final ImmutableMap<Method, ArrowMethod> MAP;  // 静态成员变量：创建一个不可变的Map，用于从Method对象反向查找对应的ArrowMethod枚举常量

  static {  // 静态初始化块，在类加载时执行，用于初始化MAP静态变量
    final ImmutableMap.Builder<Method, ArrowMethod> builder =  // 创建一个ImmutableMap构建器，用于构建不可变Map
        ImmutableMap.builder();  // 调用builder()方法获取构建器实例
    for (ArrowMethod value : ArrowMethod.values()) {  // 遍历ArrowMethod枚举的所有枚举常量
      builder.put(value.method, value);  // 将每个枚举常量的method对象作为键，枚举常量本身作为值放入构建器
    }  // 遍历结束后，构建器中包含了所有方法的映射关系
    MAP = builder.build();  // 调用build()方法构建不可变Map并赋值给MAP静态变量
  }  // 静态初始化块结束，此时MAP已经包含了所有方法的映射关系

  /** Defines a method. */  // 构造方法注释：定义一个方法枚举常量
  ArrowMethod(Class<?> clazz, String methodName, Class<?>... argumentTypes) {  // 构造方法：接收类对象、方法名和参数类型数组，用于查找并初始化method字段
    this.method = Types.lookupMethod(clazz, methodName, argumentTypes);  // 调用Types.lookupMethod通过反射查找指定类中的方法，并赋值给method成员变量
  }  // 构造方法结束，此时枚举常量的method字段已经被初始化为对应的Method对象
}  // 类定义结束
