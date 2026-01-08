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
// 声明包名，表示这个类属于 org.apache.calcite.rel.metadata 包，是 Calcite 关系表达式元数据处理相关的包
package org.apache.calcite.rel.metadata;

// 导入 Google Guava 库中的 ImmutableSortedMap 类，用于构建不可变的排序映射
import com.google.common.collect.ImmutableSortedMap;

// 导入 Java 反射相关的 Method 类，用于获取方法信息
import java.lang.reflect.Method;
// 导入 Java 工具类 Arrays，用于数组操作
import java.util.Arrays;
// 导入 Java 集合框架中的 SortedMap 接口，表示有序的映射
import java.util.SortedMap;

// 导入静态工具方法 isStatic，用于判断方法是否为静态方法
import static org.apache.calcite.util.ReflectUtil.isStatic;

/**
 * 元数据处理器的标记接口。
 * 
 * 【类的作用】：
 * MetadataHandler 是 Calcite 元数据系统的核心接口之一，它定义了元数据处理器的基本规范。
 * 
 * 在 Calcite 的优化器中，元数据（Metadata）是指关于关系表达式（RelNode）的各种统计信息和属性，
 * 例如行数（RowCount）、唯一键（UniqueKeys）、分布（Distribution）等。这些元数据对于查询优化至关重要，
 * 优化器根据这些元数据来选择最优的执行计划。
 * 
 * MetadataHandler 接口的作用是：
 * 1. 作为所有特定元数据处理器（如 RowCountHandler、UniqueKeysHandler 等）的基接口
 * 2. 提供统一的接口规范，确保所有元数据处理器都遵循相同的设计模式
 * 3. 支持元数据的计算和缓存机制
 * 4. 通过泛型参数 M 来指定该处理器处理的元数据类型
 * 
 * MetadataHandler 的实现类通常包含：
 * - getDef() 方法：返回元数据定义，描述该处理器支持的元数据类型
 * - 一个或多个处理方法：用于计算特定关系节点的元数据值
 * 
 * 例如，RowCountHandler 可能包含 getRowCount(RelNode rel, RelMetadataQuery mq) 方法
 * 用于计算某个关系节点的行数。
 *
 * @param <M> 元数据类型，必须是 Metadata 接口的子类，表示该处理器处理的元数据种类
 */
public interface MetadataHandler<M extends Metadata> {
  // 【方法作用】：
  // 获取此元数据处理器的定义信息
  // 
  // 【返回值】：
  // MetadataDef<M> - 元数据定义对象，包含：
  //   - 元数据的类型信息（通过泛型 M 指定）
  //   - 元数据的接口类
  //   - 元数据处理器类
  //   - 元数据中定义的方法列表
  // 
  // 【使用场景】：
  // 1. 在元数据系统初始化时，通过此方法获取处理器的元数据定义
  // 2. 用于注册元数据处理器到元数据提供器（MetadataProvider）
  // 3. 在运行时动态查询处理器的元数据信息
  // 
  // 【实现要求】：
  // 每个实现类必须返回一个固定的 MetadataDef 实例，通常使用单例模式
  MetadataDef<M> getDef();

  /**
   * 查找由 {@link MetadataHandler} 定义的处理方法，并返回以方法名为键的映射。
   *
   * 【方法作用】：
   * 这是一个静态工具方法，用于从元数据处理器类中提取所有有效的处理方法。
   * 它通过反射机制扫描处理器类，过滤掉不符合条件的方法，并将结果组织成有序映射。
   *
   * 【过滤规则】：
   * 1. 忽略静态方法（static methods）：静态方法不属于实例方法，不能用于处理元数据
   * 2. 忽略合成方法（synthetic methods）：编译器自动生成的方法，如桥接方法
   * 3. 忽略 getDef() 方法：这是接口定义的方法，不是处理方法
   *
   * 【方法名唯一性要求】：
   * 所有的处理方法必须具有唯一的名称。这是元数据系统的重要约束，
   * 因为方法名被用作映射的键，重复的方法名会导致覆盖，从而引发错误。
   *
   * 【返回值结构】：
   * SortedMap<String, Method> - 一个有序的映射，其中：
   *   - 键（String）：方法的名称
   *   - 值（Method）：对应的 Method 对象，包含方法的完整信息
   *   - 使用自然排序，确保方法按名称字母顺序排列
   *
   * 【使用场景】：
   * 1. 在元数据处理器注册时，扫描处理器的所有方法
   * 2. 在元数据查询时，根据方法名快速定位对应的处理方法
   * 3. 在元数据缓存系统中，建立方法名到处理逻辑的映射关系
   *
   * @param handlerClass 要检查的处理器类，必须是 MetadataHandler 的子类或实现类
   * @return 处理器方法的有序映射，键为方法名，值为 Method 对象
   */
  static SortedMap<String, Method> handlerMethods(
      Class<? extends MetadataHandler<?>> handlerClass) {
    // 【代码说明】：
    // 创建一个不可变的有序映射构建器，使用自然顺序排序
    // ImmutableSortedMap.Builder 是 Guava 提供的工具类，用于构建不可变的排序映射
    // naturalOrder() 表示使用元素的自然顺序（对于字符串，即字母顺序）
    final ImmutableSortedMap.Builder<String, Method> map =
        ImmutableSortedMap.naturalOrder();
    // 【代码说明】：
    // 使用 Java 8 Stream API 处理处理器类中声明的所有方法
    // handlerClass.getDeclaredMethods() 获取该类声明的所有方法（不包括继承的方法）
    // Arrays.stream() 将方法数组转换为流，便于进行链式操作
    Arrays.stream(handlerClass.getDeclaredMethods())
        // 【代码说明】：
        // 第一个过滤器：排除名为 "getDef" 的方法
        // getDef() 是接口定义的方法，不是元数据处理方法，需要过滤掉
        .filter(m -> !m.getName().equals("getDef"))
        // 【代码说明】：
        // 第二个过滤器：排除合成方法
        // 合成方法是编译器自动生成的方法，如内部类的桥接方法、默认方法等
        // 这些方法不是程序员显式定义的，不应该被当作处理方法
        .filter(m -> !m.isSynthetic())
        // 【代码说明】：
        // 第三个过滤器：排除静态方法
        // 使用工具方法 isStatic() 判断方法是否为静态
        // 静态方法不依赖于实例，不能用于处理特定关系节点的元数据
        .filter(m -> !isStatic(m))
        // 【代码说明】：
        // 将过滤后的每个方法添加到映射中
        // m.getName() 获取方法名作为键
        // m 是 Method 对象本身，作为值
        // forEach() 对流中的每个元素执行指定操作
        .forEach(m -> map.put(m.getName(), m));
    // 【代码说明】：
    // 构建并返回不可变的有序映射
    // build() 方法会创建一个 ImmutableSortedMap 实例
    // 这个映射是线程安全的，并且一旦创建就不能修改
    return map.build();
  }
}
