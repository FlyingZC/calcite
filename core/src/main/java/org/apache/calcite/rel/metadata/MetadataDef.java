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
package org.apache.calcite.rel.metadata; // 定义元数据定义类的包路径

import org.apache.calcite.rel.RelNode; // 导入关系节点基类，用于表示关系代数中的操作节点
import org.apache.calcite.util.Pair; // 导入工具类，用于处理键值对
import org.apache.calcite.util.Util; // 导入通用工具类，提供各种辅助方法

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类

import java.lang.reflect.Method; // 导入反射方法类，用于获取方法信息
import java.util.Arrays; // 导入数组工具类，提供数组操作方法
import java.util.Comparator; // 导入比较器接口，用于对象排序
import java.util.List; // 导入列表接口，表示有序集合
import java.util.SortedMap; // 导入排序映射接口，表示键值对的有序集合

import static com.google.common.base.Preconditions.checkArgument; // 导入前置条件检查方法，用于验证参数
import static com.google.common.collect.ImmutableList.toImmutableList; // 导入将流转换为不可变列表的收集器

/**
 * Definition of metadata. // 元数据定义类，用于定义关系代数中元数据的类型和处理方式
 * // 这个类是Calcite元数据系统的核心组件之一，它建立了元数据接口与处理器之间的映射关系
 * // 通过MetadataDef，Calcite可以知道如何为特定的RelNode计算和查询元数据
 * // 元数据包括行数、唯一性、分布情况、排序属性等信息，这些信息对查询优化至关重要
 *
 * @param <M> Kind of metadata // 泛型参数M表示元数据的类型，必须是Metadata的子类
 */
public class MetadataDef<M extends Metadata> { // 定义元数据定义类，使用泛型M约束元数据类型必须继承自Metadata
  public final Class<M> metadataClass; // 元数据接口的Class对象，定义了元数据的类型和可用的查询方法
  // 例如：RowCount.class表示行数元数据，Unique.class表示唯一性元数据
  // 这个类定义了用户可以查询哪些元数据信息，每个元数据类型对应一个接口

  public final Class<? extends MetadataHandler<M>> handlerClass; // 元数据处理器的Class对象，负责实际计算元数据值
  // 处理器实现了MetadataHandler接口，为每种RelNode类型提供具体的元数据计算逻辑
  // 例如：RowCountHandler负责计算不同RelNode的行数

  public final ImmutableList<Method> methods; // 不可变的方法列表，存储元数据接口中定义的所有方法
  // 这些方法按方法名排序，每个方法对应一个可以查询的元数据属性
  // 例如：getRowCount()、isUnique()等方法

  private MetadataDef(Class<M> metadataClass, // 私有构造方法，创建元数据定义对象
      Class<? extends MetadataHandler<M>> handlerClass, Method... methods) { // 接收元数据类、处理器类和可变方法参数
    this.metadataClass = metadataClass; // 初始化元数据类字段，保存元数据接口的类型信息
    this.handlerClass = handlerClass; // 初始化处理器类字段，保存元数据处理器的类型信息
    this.methods = // 初始化方法列表字段
        Arrays.stream(methods) // 将方法数组转换为流，便于进行函数式操作
            .sorted(Comparator.comparing(Method::getName)) // 按方法名对方法进行排序，确保方法顺序一致
            .collect(toImmutableList()); // 将流收集为不可变列表，防止方法列表被修改
    final SortedMap<String, Method> handlerMethods = // 获取处理器类中定义的所有方法，按方法名排序存储
        MetadataHandler.handlerMethods(handlerClass); // 通过反射获取处理器类中所有符合规范的方法

    // Handler must have the same methods as Metadata, each method having
    // additional "subclass-of-RelNode, RelMetadataQuery" parameters.
    // 处理器必须与元数据接口具有相同的方法，但每个方法需要额外添加两个参数：
    // 第一个参数是RelNode的子类（具体的RelNode类型），第二个参数是RelMetadataQuery对象
    // 这样设计是因为处理器方法需要知道具体的RelNode类型和元数据查询上下文
    checkArgument(this.methods.size() == handlerMethods.size(), // 检查元数据接口的方法数量是否与处理器的方法数量相等
        "handlerMethods.length = methods.length", this.methods, handlerMethods); // 如果不相等，抛出异常并显示相关信息
    Pair.forEach(this.methods, handlerMethods.values(), // 遍历元数据接口方法和处理器方法，对它们进行配对检查
        (method, handlerMethod) -> { // 对每对方法执行验证逻辑
          final List<Class<?>> methodTypes = // 获取元数据接口方法的参数类型列表
              Arrays.asList(method.getParameterTypes()); // 通过反射获取方法的所有参数类型
          final List<Class<?>> handlerTypes = // 获取处理器方法的参数类型列表
              Arrays.asList(handlerMethod.getParameterTypes()); // 通过反射获取处理器方法的所有参数类型
          checkArgument(methodTypes.size() + 2 == handlerTypes.size(), // 检查处理器方法的参数数量是否比元数据方法多2个
              "methodTypes.size + 2 == handlerTypes.size", handlerMethod, // 如果不满足，抛出异常
              methodTypes, handlerTypes); // 显示相关的参数类型信息
          checkArgument(RelNode.class.isAssignableFrom(handlerTypes.get(0)), // 检查处理器方法的第一个参数是否是RelNode的子类
              "RelNode.assignableFrom(handlerType[0])", handlerMethod, // 如果不是RelNode子类，抛出异常
              handlerTypes); // 显示参数类型信息
          checkArgument(RelMetadataQuery.class == handlerTypes.get(1), // 检查处理器方法的第二个参数是否是RelMetadataQuery类型
              "handlerTypes[1] == RelMetadataQuery", handlerMethod, // 如果不是RelMetadataQuery类型，抛出异常
              handlerTypes); // 显示参数类型信息
          checkArgument(methodTypes.equals(Util.skip(handlerTypes, 2)), // 检查处理器方法从第3个参数开始的参数类型是否与元数据方法的参数类型完全一致
              "methodTypes == handlerTypes.skip(2)", handlerMethod, methodTypes, // 如果不一致，抛出异常
              handlerTypes); // 显示相关的参数类型信息
        }); // 配对检查结束
  } // 构造方法结束

  /** Creates a {@link org.apache.calcite.rel.metadata.MetadataDef}. */
  // 创建元数据定义对象的静态工厂方法，提供更简洁的创建方式
  // 这是创建MetadataDef实例的推荐方式，而不是直接调用构造方法
  public static <M extends Metadata> MetadataDef<M> of(Class<M> metadataClass, // 静态方法，接收元数据类作为参数
      Class<? extends MetadataHandler<M>> handlerClass, Method... methods) { // 接收处理器类和可变方法参数
    return new MetadataDef<>(metadataClass, handlerClass, methods); // 调用私有构造方法创建并返回MetadataDef实例
  } // 静态工厂方法结束
} // MetadataDef类定义结束
