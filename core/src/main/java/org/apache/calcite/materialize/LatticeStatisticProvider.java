/*
 * Licensed to the Apache Software Foundation (ASF) under one or more  // Apache软件基金会许可证声明
 * contributor license agreements.  See the NOTICE file distributed with  // 贡献者许可协议，查看NOTICE文件获取版权信息
 * this work for additional information regarding copyright ownership.  // 本作品，了解版权所有信息
 * The ASF licenses this file to you under the Apache License, Version 2.0  // ASF根据Apache 2.0许可证授权您使用本文件
 * (the "License"); you may not use this file except in compliance with  // （"许可证"）；除非遵守许可证，否则您不得使用本文件
 * the License.  You may obtain a copy of the License at  // 您可以在以下网址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0  // Apache 2.0许可证网址
 *
 * Unless required by applicable law or agreed to in writing, software  // 除非适用法律要求或书面同意，否则
 * distributed under the License is distributed on an "AS IS" BASIS,  // 本软件按"原样"基础分发，不提供任何形式的明示或暗示保证
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  // 不提供任何明示或暗示的保证或条件
 * See the License for the specific language governing permissions and  // 查看许可证以了解特定语言的权限和
 * limitations under the License.  // 许可下的限制
 */
package org.apache.calcite.materialize;  // 包声明：org.apache.calcite.materialize，包含物化视图相关的类

import java.util.List;  // 导入List接口，用于存储列的列表
import java.util.function.Function;  // 导入Function函数式接口，用于定义工厂方法

/**
 * Estimates row counts for a lattice and its attributes.  // LatticeStatisticProvider接口：用于估算Lattice（立方体）及其属性的行数
 * 
 * 【类的作用详解】：
 * LatticeStatisticProvider是一个接口，专门用于为Lattice（数据立方体）提供统计信息。
 * Lattice是Calcite中用于物化视图优化的核心概念，它表示一个多维数据模型。
 * 
 * 主要功能：
 * 1. 估算Lattice中某个列或列组合的基数（cardinality），即不同值的数量
 * 2. 基数估算对于查询优化器选择最优执行计划至关重要
 * 3. 基数信息可以帮助优化器决定：
 *    - 是否使用物化视图
 *    - 选择哪种连接顺序
 *    - 选择哪种聚合策略
 *    - 估算查询成本
 * 
 * 使用场景：
 * - 在物化视图推荐算法中，用于评估不同物化视图的收益
 * - 在查询优化过程中，用于估算中间结果的行数
 * - 在Lattice构建过程中，用于选择最优的维度和度量组合
 * 
 * 实现方式：
 * - 可以基于实际统计数据（从数据库统计信息中获取）
 * - 可以基于采样估算
 * - 可以使用启发式算法进行估算
 */
public interface LatticeStatisticProvider {  // LatticeStatisticProvider接口定义，用于提供Lattice统计信息

  /** Returns an estimate of the number of distinct values in a column  // cardinality方法：返回某个列或列组合中不同值数量的估算值
   * or list of columns.  // 参数可以是单个列，也可以是多个列的组合
   * 
   * 【方法详解】：
   * 此方法用于估算给定列或列组合的基数（cardinality）。
   * 基数是指列中不同值的数量，这是查询优化中的一个重要统计信息。
   * 
   * 参数说明：
   * - List<Lattice.Column> columns：要估算基数的列列表
   *   - 可以是单个列，此时返回该列的不同值数量
   *   - 可以是多个列，此时返回这些列组合的不同值数量
   *   - 例如：对于列(A, B)，基数是指(A,B)这个组合的不同值数量
   * 
   * 返回值：
   * - double：基数的估算值
   *   - 返回double类型，因为估算值可能不是整数
   *   - 可以使用小数表示不确定性或概率分布
   * 
   * 应用场景：
   * 1. 查询优化：优化器使用基数估算来选择最优执行计划
   * 2. 物化视图选择：评估物化视图的存储成本和查询收益
   * 3. 连接顺序选择：估算连接操作的中间结果大小
   * 4. 聚合操作估算：估算GROUP BY操作的输出行数
   * 
   * 估算策略：
   * - 精确计算：对实际数据进行COUNT(DISTINCT)查询（成本高）
   * - 统计信息：使用数据库维护的统计信息
   * - 采样：通过采样数据进行估算
   * - 启发式：基于数据分布特征进行估算
   * 
   * 注意事项：
   * - 估算值可能与实际值有偏差
   * - 估算精度影响优化器决策质量
   * - 列组合的基数通常小于或等于各列基数的乘积
   */
  double cardinality(List<Lattice.Column> columns);  // cardinality方法签名，接收列列表参数，返回基数估算值

  /** Creates a {@link LatticeStatisticProvider} for a given  // Factory接口：为给定的Lattice创建LatticeStatisticProvider实例
   * {@link org.apache.calcite.materialize.Lattice}.  // 参数是Lattice对象，返回对应的统计信息提供者
   * 
   * 【接口详解】：
   * Factory是一个嵌套接口，继承自Function<Lattice, LatticeStatisticProvider>。
   * 它的作用是作为工厂接口，用于为特定的Lattice对象创建对应的统计信息提供者实例。
   * 
   * 设计模式：
   * - 工厂模式：将对象的创建逻辑封装起来
   * - 函数式接口：继承自Function，支持lambda表达式和方法引用
   * 
   * 为什么需要Factory接口？
   * 1. 解耦：将Lattice对象的创建与LatticeStatisticProvider的创建分离
   * 2. 灵活性：可以为不同的Lattice创建不同类型的统计信息提供者
   * 3. 可配置：在运行时根据需要选择不同的实现
   * 4. 依赖注入：便于在框架中进行依赖注入
   * 
   * 使用方式：
   * - 通过Factory的apply方法，传入Lattice对象，返回对应的LatticeStatisticProvider
   * - 支持lambda表达式：lattice -> new MyStatisticProvider(lattice)
   * - 支持方法引用：MyStatisticProvider::new
   * 
   * 实现示例：
   * - 基于实际数据的统计提供者工厂
   * - 基于采样的统计提供者工厂
   * - 基于启发式规则的统计提供者工厂
   * 
   * 与主接口的关系：
   * - LatticeStatisticProvider定义了统计信息的查询接口
   * - Factory定义了统计信息提供者的创建接口
   * - 两者配合使用，实现完整的统计信息管理
   */
  interface Factory extends Function<Lattice, LatticeStatisticProvider> {  // Factory接口定义，继承自Function函数式接口
  }  // Factory接口结束
}  // LatticeStatisticProvider接口结束
