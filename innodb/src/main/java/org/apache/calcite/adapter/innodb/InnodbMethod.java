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
package org.apache.calcite.adapter.innodb; // 指定当前类所在的包路径，org.apache.calcite.adapter.innodb表示这是Calcite框架中InnoDB适配器包下的类

import org.apache.calcite.linq4j.tree.Types; // 导入Calcite的LINQ4J工具类，用于类型查找和方法反射操作

import com.google.common.collect.ImmutableMap; // 导入Google Guava库的不可变Map类，用于创建不可变的映射集合

import java.lang.reflect.Method; // 导入Java反射API的Method类，用于表示和操作类的方法

import java.util.List; // 导入Java集合框架的List接口，用于表示有序列表

/**
 * Builtin methods in InnoDB data source. // InnoDB数据源中内置方法的枚举类，用于定义和管理InnoDB适配器中所有可用的内置方法
 * 这个枚举类的主要作用是：1) 定义InnoDB数据源支持的所有内置方法；2) 为代码生成提供方法签名；3) 建立Method对象到InnodbMethod枚举值的映射关系
 * 通过这个枚举，Calcite可以在运行时动态调用InnoDB适配器的方法，实现数据查询和过滤功能
 */
public enum InnodbMethod { // 声明一个枚举类InnodbMethod，包含InnoDB数据源的所有内置方法
  /** Method signature to call for code generation. */ // 此枚举值用于代码生成时调用的方法签名说明
  INNODB_QUERYABLE_QUERY(InnodbTable.InnodbQueryable.class, "query", // 定义InnoDB查询方法，第一个参数是包含该方法的类，第二个参数是方法名
      List.class, List.class, IndexCondition.class, Boolean.class); // 方法的参数类型列表：List(字段列表)、List(投影列表)、IndexCondition(索引条件)、Boolean(是否使用索引)

  @SuppressWarnings("ImmutableEnumChecker") // 禁止编译器检查枚举常量的不可变性警告，因为method字段虽然是final但可能在初始化时被修改
  public final Method method; // 成员变量：反射方法对象，用于存储当前枚举值对应的Java反射Method对象，通过它可以动态调用方法

  public static final ImmutableMap<Method, InnodbMethod> MAP; // 成员变量：静态不可变映射表，用于建立Method对象到InnodbMethod枚举值的反向映射，方便通过Method查找对应的枚举值

  static { // 静态初始化块，在类加载时执行，用于初始化静态成员变量MAP
    final ImmutableMap.Builder<Method, InnodbMethod> builder = // 创建一个不可变Map的构建器，用于构建MAP映射表
        ImmutableMap.builder(); // 调用builder()方法获取构建器实例
    for (InnodbMethod value : InnodbMethod.values()) { // 遍历InnodbMethod枚举的所有值，values()方法返回所有枚举常量的数组
      builder.put(value.method, value); // 将每个枚举值的method字段作为键，枚举值本身作为值，添加到构建器中
    }
    MAP = builder.build(); // 调用build()方法构建不可变Map并赋值给MAP变量，此时MAP包含所有Method到InnodbMethod的映射关系
  }

  InnodbMethod(Class clazz, String methodName, Class... argumentTypes) { // 构造方法，用于创建枚举常量，参数：clazz-方法所在的类，methodName-方法名，argumentTypes-可变参数列表表示方法的参数类型
    this.method = Types.lookupMethod(clazz, methodName, argumentTypes); // 调用Types.lookupMethod方法通过反射查找指定类中的方法，并将结果赋值给method字段
  } // 构造方法结束，this.method存储了查找到的Method对象
}
