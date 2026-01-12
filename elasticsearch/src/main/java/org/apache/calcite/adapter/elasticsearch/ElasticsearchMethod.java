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
package org.apache.calcite.adapter.elasticsearch; // 声明包名，表示该类属于Elasticsearch适配器包

import org.apache.calcite.linq4j.tree.Types; // 导入Types工具类，用于反射查找方法

import com.google.common.collect.ImmutableMap; // 导入Google Guava的不可变Map类

import java.lang.reflect.Method; // 导入Java反射Method类，用于表示方法对象
import java.util.List; // 导入List接口，用于列表集合
import java.util.Map; // 导入Map接口，用于键值对映射

/**
 * Builtin methods in the Elasticsearch adapter. // 类注释：Elasticsearch适配器中的内置方法枚举类
 * 该枚举类定义了Elasticsearch适配器中所有可用的内置方法，主要用于在Calcite查询引擎和Elasticsearch之间建立方法映射关系
 * 通过反射机制查找并存储方法对象，使得Calcite能够调用Elasticsearch特定的查询功能
 */
enum ElasticsearchMethod { // 定义一个枚举类，包含Elasticsearch适配器的所有内置方法

  // 定义一个枚举常量，表示ElasticsearchQueryable类中的find方法
  // 该方法用于在Elasticsearch中执行查询操作，包含以下参数：
  // 第一个参数：ElasticsearchTable.ElasticsearchQueryable.class - 方法所在的类
  // 第二个参数："find" - 方法名称
  // 后续参数：方法的参数类型列表
  ELASTICSEARCH_QUERYABLE_FIND(ElasticsearchTable.ElasticsearchQueryable.class, // 指定方法所在的类为ElasticsearchQueryable
      "find", // 方法名称为find，表示执行查询操作
      List.class, // ops  - projections and other stuff // 第一个参数类型为List，表示投影操作和其他操作
      List.class, // fields // 第二个参数类型为List，表示要查询的字段列表
      List.class, // sort // 第三个参数类型为List，表示排序条件
      List.class, // nulls sort // 第四个参数类型为List，表示空值排序规则
      List.class, // groupBy // 第五个参数类型为List，表示分组字段
      List.class, // aggregations // 第六个参数类型为List，表示聚合函数
      Map.class, // item to expression mapping. Eg. _MAP['a.b.c'] and EXPR$1 // 第七个参数类型为Map，表示字段到表达式的映射，例如 _MAP['a.b.c'] 映射到 EXPR$1
      Long.class, // offset // 第八个参数类型为Long，表示偏移量（跳过的记录数）
      Long.class); // fetch // 第九个参数类型为Long，表示获取的记录数限制

  @SuppressWarnings("ImmutableEnumChecker") // 禁止不可变枚举检查警告，因为枚举在Java中是不可变的，但method字段是final的，所以这个警告可以忽略
  public final Method method; // 声明一个公共的final方法对象，存储通过反射找到的方法引用，final表示该字段不可变

  public static final ImmutableMap<Method, ElasticsearchMethod> MAP; // 声明一个静态的不可变Map，用于建立Method到ElasticsearchMethod的反向映射，方便通过方法对象快速查找对应的枚举值

  static { // 静态初始化块，在类加载时执行，用于初始化静态字段MAP
    final ImmutableMap.Builder<Method, ElasticsearchMethod> builder = ImmutableMap.builder(); // 创建一个不可变Map的构建器对象
    for (ElasticsearchMethod value : ElasticsearchMethod.values()) { // 遍历枚举类的所有枚举值
      builder.put(value.method, value); // 将每个枚举值的method字段作为key，枚举值本身作为value，添加到构建器中
    }
    MAP = builder.build(); // 调用构建器的build方法，创建并赋值给MAP字段，此时MAP是一个不可变的Map
  }

  // 构造方法，用于创建枚举实例
  // 参数说明：
  // Class clazz - 方法所在的类对象
  // String methodName - 方法的名称
  // Class... argumentTypes - 方法的参数类型列表，使用可变参数语法
  ElasticsearchMethod(Class clazz, String methodName, Class... argumentTypes) { // 构造方法，接收类、方法名和参数类型作为参数
    this.method = Types.lookupMethod(clazz, methodName, argumentTypes); // 使用Types工具类的lookupMethod方法，通过反射查找指定类中的方法，并将结果赋值给method字段
  }
} // 枚举类结束
