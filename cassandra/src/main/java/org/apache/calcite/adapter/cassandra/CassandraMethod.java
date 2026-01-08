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
// 声明包名：org.apache.calcite.adapter.cassandra，表示这个类属于Calcite框架的Cassandra适配器模块
package org.apache.calcite.adapter.cassandra;

// 导入Types工具类，用于反射查找方法，来自Calcite的LINQ4J库
import org.apache.calcite.linq4j.tree.Types;

// 导入ImmutableMap，Google Guava库提供的不可变Map实现，用于构建方法映射表
import com.google.common.collect.ImmutableMap;

// 导入Method类，Java反射API的核心类，用于表示类的方法
import java.lang.reflect.Method;

// 导入List接口，Java集合框架的列表接口
import java.util.List;

/**
 * Builtin methods in the Cassandra adapter.
 * Cassandra适配器中内置方法的枚举定义
 * 
 * 这个枚举类是Cassandra适配器的核心组件之一，它定义了Cassandra适配器中所有可用的内置方法
 * 通过枚举的方式管理方法引用，提供了类型安全和便捷的方法访问机制
 * 
 * 主要功能：
 * 1. 定义Cassandra适配器中可用的内置方法
 * 2. 提供方法到枚举值的反向映射，方便通过Method对象查找对应的枚举
 * 3. 使用反射机制查找和存储方法引用
 * 4. 为代码生成和运行时调用提供方法元数据
 * 
 * 设计模式：
 * - 使用枚举类型确保方法定义的单一性和类型安全
 * - 使用静态初始化块构建方法映射表，提高查找效率
 * - 使用不可变Map确保映射关系的线程安全性
 */
public enum CassandraMethod {
  // 枚举常量：CASSANDRA_QUERYABLE_QUERY，表示Cassandra查询接口的query方法
  // 参数说明：
  //   - CassandraTable.CassandraQueryable.class: 方法所在的类
  //   - "query": 方法名
  //   - List.class, List.class, List.class, List.class: 四个List类型的参数，分别表示字段列表、投影列表、过滤条件列表、排序列表
  //   - Integer.class, Integer.class: 两个Integer类型的参数，分别表示偏移量和限制数量
  // 这个方法用于执行Cassandra数据查询，支持字段选择、投影、过滤、排序和分页功能
  CASSANDRA_QUERYABLE_QUERY(CassandraTable.CassandraQueryable.class, "query",
      List.class, List.class, List.class, List.class, Integer.class, Integer.class);

  // 注解：忽略不可变枚举检查器的警告
  // 因为枚举实例的method字段是final但不是不可变的（Method对象本身是可变的），所以需要抑制这个警告
  @SuppressWarnings("ImmutableEnumChecker")
  // 成员变量：method，存储反射得到的Method对象
  // public final表示这个字段是公开的、不可变的，可以被外部访问但不能修改
  // Method对象包含了方法的完整元数据信息（方法名、参数类型、返回类型等）
  // 这个字段在构造函数中初始化后就不会再改变，确保了方法引用的稳定性
  public final Method method;

  // 静态常量：MAP，一个不可变的Map，用于存储Method对象到CassandraMethod枚举值的映射
  // public static final表示这是一个公开的、静态的、不可变的类常量
  // 这个映射表允许通过Method对象反向查找对应的CassandraMethod枚举值
  // 在代码生成和运行时调用时非常有用，可以快速定位方法对应的枚举常量
  public static final ImmutableMap<Method, CassandraMethod> MAP;

  // 静态初始化块：在类加载时执行，用于构建方法映射表
  // 静态块会在类第一次被使用时执行，且只执行一次
  // 这里使用Builder模式构建不可变的Map，确保映射表在初始化完成后就不能被修改
  static {
    // 创建ImmutableMap.Builder对象，用于构建不可变的Map
    // Builder模式提供了一种流畅的API来逐步构建复杂对象
    final ImmutableMap.Builder<Method, CassandraMethod> builder =
        ImmutableMap.builder();
    // 遍历CassandraMethod枚举的所有值
    // CassandraMethod.values()返回枚举类型的所有常量数组
    // for-each循环遍历每个枚举常量
    for (CassandraMethod value : CassandraMethod.values()) {
      // 将枚举常量的method字段作为key，枚举常量本身作为value，放入builder中
      // 这样就建立了Method对象到CassandraMethod枚举值的双向映射关系
      builder.put(value.method, value);
    }
    // 调用builder.build()方法构建不可变的Map，并赋值给MAP常量
    // 一旦构建完成，这个Map就不能再被修改，保证了线程安全性
    MAP = builder.build();
  }

  // 构造方法：CassandraMethod，枚举的构造函数
  // 参数说明：
  //   - Class<?> clazz: 方法所在的类对象
  //   - String methodName: 方法名称
  //   - Class<?>... argumentTypes: 可变参数，表示方法的参数类型列表
  // 这个构造函数使用反射机制查找指定类中的方法，并将结果存储在method字段中
  // 可变参数语法(Class<?>...)允许传入0个或多个Class对象，方便处理不同参数数量的方法
  CassandraMethod(Class<?> clazz, String methodName, Class<?>... argumentTypes) {
    // 使用Types.lookupMethod方法通过反射查找指定类中的方法
    // Types是Calcite的LINQ4J库提供的工具类，封装了Java反射API
    // lookupMethod会根据类名、方法名和参数类型列表查找对应的Method对象
    // 如果找不到方法，会抛出NoSuchMethodException异常
    // 将查找得到的Method对象赋值给method字段，完成初始化
    this.method = Types.lookupMethod(clazz, methodName, argumentTypes);
  }
}
