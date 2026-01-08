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
package org.apache.calcite.adapter.enumerable; // 定义包路径，该类位于 enumerable 适配器包中，用于可枚举的聚合计算

import org.apache.calcite.rel.type.RelDataType; // 导入 Calcite 关系数据类型，用于描述关系表中字段的类型信息
import org.apache.calcite.sql.SqlAggFunction; // 导入 SQL 聚合函数接口，如 SUM、COUNT、AVG 等
import org.apache.calcite.util.ImmutableBitSet; // 导入不可变位集合，用于高效表示分组集合的字段索引

import java.lang.reflect.Type; // 导入 Java 反射类型，用于表示运行时类型信息
import java.util.List; // 导入 Java 列表接口，用于存储集合数据

/**
 * Information on the aggregate calculation context. // 聚合计算上下文信息的接口定义
 * {@link AggAddContext} provides basic static information on types of arguments // AggAddContext 提供了关于正在实现的聚合函数的参数类型和返回值的基本静态信息
 * and the return value of the aggregate being implemented. // 这是一个接口，用于在生成聚合函数实现代码时提供必要的类型和上下文信息
 * 
 * 该接口的核心作用：
 * 1. 为聚合函数的实现提供类型信息（参数类型、返回类型、分组键类型）
 * 2. 提供聚合函数本身的元数据（SqlAggFunction）
 * 3. 提供分组相关的信息（分组键序号、分组集合）
 * 4. 支持两种类型系统：RelDataType（Calcite 内部类型）和 Java Type（运行时类型）
 * 
 * 使用场景：
 * - 在 Enumerable 适配器中生成聚合函数的 Java 代码时
 * - 需要知道聚合函数的输入输出类型以生成正确的类型转换代码
 * - 需要知道分组键信息以生成分组逻辑
 * - 支持复杂的多重分组（GROUPING SETS）
 */
public interface AggContext { // 定义聚合上下文接口，提供聚合计算所需的元数据信息
  /**
   * Returns the aggregation being implemented. // 返回正在实现的聚合函数对象
   *
   * @return aggregation being implemented. // 返回值：SqlAggFunction 对象，表示具体的聚合函数（如 SUM、COUNT、AVG、MIN、MAX 等）
   * 
   * 方法详细说明：
   * - 返回的 SqlAggFunction 包含聚合函数的所有元数据信息
   * - 包括函数名称、函数类型（是否是近似聚合、是否需要去重等）
   * - 可以通过返回值判断聚合函数的特性，例如是否支持 DISTINCT 修饰
   * - 在生成代码时，需要根据不同的聚合函数类型生成不同的实现逻辑
   * - 例如：COUNT(*) 和 COUNT(column) 的实现方式不同
   */
  SqlAggFunction aggregation(); // 声明方法：获取当前正在实现的聚合函数对象

  /**
   * Returns the return type of the aggregate as // 返回聚合函数的返回类型，使用 Calcite 的 RelDataType 表示
   * {@link org.apache.calcite.rel.type.RelDataType}. // RelDataType 是 Calcite 的内部类型系统，包含完整的类型信息
   * This can be helpful to test // 这个方法特别有用，可以用来测试类型是否可为空
   * {@link org.apache.calcite.rel.type.RelDataType#isNullable()}. // 通过调用 isNullable() 方法判断返回值是否允许为 NULL
   *
   * @return return type of the aggregate // 返回值：聚合函数的返回类型，使用 RelDataType 表示
   * 
   * 方法详细说明：
   * - RelDataType 包含丰富的类型信息：类型名称、精度、标度、是否可为空等
   * - 例如：SUM(INT) 的返回类型可能是 BIGINT，COUNT 的返回类型是 BIGINT 且不可为空
   * - 在生成代码时，需要根据返回类型确定 Java 变量的类型
   * - 如果返回类型可为空，需要生成处理 NULL 值的代码逻辑
   * - 与 returnType() 方法的区别：本方法返回 Calcite 内部类型，returnType() 返回 Java 运行时类型
   */
  RelDataType returnRelType(); // 声明方法：获取聚合函数返回值的 RelDataType 类型

  /**
   * Returns the return type of the aggregate as {@link java.lang.reflect.Type}. // 返回聚合函数的返回类型，使用 Java 反射 Type 表示
   *
   * @return return type of the aggregate as {@link java.lang.reflect.Type} // 返回值：聚合函数的返回类型，使用 Java Type 表示
   * 
   * 方法详细说明：
   * - 返回的是 Java 运行时类型，用于生成 Java 代码
   * - 例如：RelDataType 的 BIGINT 对应 Java 的 long 或 Long 类型
   * - 在生成可执行代码时，需要使用这个类型来声明变量
   * - 与 returnRelType() 的区别：本方法用于代码生成，returnRelType() 用于类型检查和元数据查询
   * - 如果返回类型可为空，返回的可能是包装类型（如 Long）而不是基本类型（如 long）
   */
  Type returnType(); // 声明方法：获取聚合函数返回值的 Java Type 类型

  /**
   * Returns the parameter types of the aggregate as // 返回聚合函数的参数类型列表，使用 Calcite 的 RelDataType 表示
   * {@link org.apache.calcite.rel.type.RelDataType}. // 每个参数都是一个 RelDataType 对象
   * This can be helpful to test // 这个方法特别有用，可以用来测试参数类型是否可为空
   * {@link org.apache.calcite.rel.type.RelDataType#isNullable()}. // 通过调用 isNullable() 方法判断每个参数是否允许为 NULL
   *
   * @return Parameter types of the aggregate // 返回值：聚合函数的参数类型列表，每个元素是一个 RelDataType 对象
   * 
   * 方法详细说明：
   * - 返回列表的长度等于聚合函数的参数个数
   * - 例如：SUM(column) 有一个参数，AVG(x, y) 有两个参数
   * - 每个参数的 RelDataType 包含完整的类型信息
   * - 在生成代码时，需要根据参数类型生成类型转换逻辑
   * - 如果参数可为空，需要生成处理 NULL 值的代码逻辑
   * - 与 parameterTypes() 方法的区别：本方法返回 Calcite 内部类型，parameterTypes() 返回 Java 运行时类型
   */
  List<? extends RelDataType> parameterRelTypes(); // 声明方法：获取聚合函数参数的 RelDataType 类型列表

  /**
   * Returns the parameter types of the aggregate as // 返回聚合函数的参数类型列表，使用 Java 反射 Type 表示
   * {@link java.lang.reflect.Type}. // 每个参数都是一个 Java Type 对象
   *
   * @return Parameter types of the aggregate // 返回值：聚合函数的参数类型列表，每个元素是一个 Java Type 对象
   * 
   * 方法详细说明：
   * - 返回的是 Java 运行时类型列表，用于生成 Java 代码
   * - 列表长度等于聚合函数的参数个数
   * - 在生成可执行代码时，需要使用这些类型来声明参数变量
   * - 例如：RelDataType 的 INTEGER 对应 Java 的 int 或 Integer 类型
   * - 与 parameterRelTypes() 的区别：本方法用于代码生成，parameterRelTypes() 用于类型检查和元数据查询
   * - 如果参数可为空，返回的可能是包装类型（如 Integer）而不是基本类型（如 int）
   */
  List<? extends Type> parameterTypes(); // 声明方法：获取聚合函数参数的 Java Type 类型列表

  /** Returns the ordinals of the input fields that make up the key. */ // 返回组成分组键的输入字段的序号列表
  List<Integer> keyOrdinals(); // 声明方法：获取分组键在输入字段中的序号列表
  /**
   * 方法详细说明：
   * - 返回的序号列表表示哪些字段用于分组
   * - 例如：GROUP BY a, b，则返回 [0, 1]（假设 a 是第 0 个字段，b 是第 1 个字段）
   * - 序号从 0 开始，对应输入行的字段索引
   * - 在生成代码时，需要根据这些序号提取分组键的值
   * - 对于没有 GROUP BY 的聚合（如 SELECT SUM(x) FROM t），返回空列表
   * - 这个信息对于生成哈希表或排序分组逻辑至关重要
   */

  /**
   * Returns the types of the group key as // 返回分组键的类型列表，使用 Calcite 的 RelDataType 表示
   * {@link org.apache.calcite.rel.type.RelDataType}. // 每个分组键都是一个 RelDataType 对象
   */
  List<? extends RelDataType> keyRelTypes(); // 声明方法：获取分组键的 RelDataType 类型列表
  /**
   * 方法详细说明：
   * - 返回列表的长度等于 keyOrdinals() 返回的分组键个数
   * - 每个元素的顺序与 keyOrdinals() 的顺序对应
   * - RelDataType 包含完整的类型信息，包括精度、标度、是否可为空等
   * - 在生成代码时，需要根据这些类型确定分组键变量的 Java 类型
   * - 用于生成分组键的比较逻辑（如 equals、hashCode 方法）
   * - 与 keyTypes() 方法的区别：本方法返回 Calcite 内部类型，keyTypes() 返回 Java 运行时类型
   */

  /**
   * Returns the types of the group key as // 返回分组键的类型列表，使用 Java 反射 Type 表示
   * {@link java.lang.reflect.Type}. // 每个分组键都是一个 Java Type 对象
   */
  List<? extends Type> keyTypes(); // 声明方法：获取分组键的 Java Type 类型列表
  /**
   * 方法详细说明：
   * - 返回的是 Java 运行时类型列表，用于生成 Java 代码
   * - 列表长度和顺序与 keyOrdinals() 完全对应
   * - 在生成可执行代码时，需要使用这些类型来声明分组键变量
   * - 例如：RelDataType 的 VARCHAR 对应 Java 的 String 类型
   * - 用于生成哈希表的键类型、比较方法的参数类型等
   * - 与 keyRelTypes() 的区别：本方法用于代码生成，keyRelTypes() 用于类型检查和元数据查询
   */

  /** Returns the grouping sets we are aggregating on. */ // 返回我们正在聚合的分组集合
  List<ImmutableBitSet> groupSets(); // 声明方法：获取分组集合列表，每个分组集合用一个 ImmutableBitSet 表示
  /**
   * 方法详细说明：
   * - 返回列表的长度等于 GROUPING SETS 的个数
   * - 每个 ImmutableBitSet 表示一个分组集合，其中的位表示哪些字段参与分组
   * - 例如：GROUP BY GROUPING SETS ((a, b), (a), ()) 返回三个 ImmutableBitSet
   * - 位集合中的位位置对应输入字段的序号（0-based）
   * - 对于普通的 GROUP BY a, b，返回包含一个 ImmutableBitSet 的列表
   * - 对于没有 GROUP BY 的聚合，返回包含一个空 ImmutableBitSet 的列表
   * - 这个信息对于支持 SQL 标准的 GROUPING SETS、ROLLUP、CUBE 语法至关重要
   * - 在生成代码时，需要为每个分组集合生成独立的聚合逻辑
   * - ImmutableBitSet 提供高效的位操作，可以快速判断字段是否在分组集合中
   */
}
