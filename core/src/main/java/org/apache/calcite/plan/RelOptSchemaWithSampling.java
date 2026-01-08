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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.plan; // 声明包名，该类属于org.apache.calcite.plan包，这是Calcite优化器相关的核心包

import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework的注解，用于标记可能为null的类型，帮助进行静态空值检查

import java.util.List; // 导入Java集合框架中的List接口，用于存储有序的元素集合

/**
 * Extension to {@link RelOptSchema} with support for sample data-sets.
 * RelOptSchema的扩展接口，增加了对样本数据集的支持
 * RelOptSchema是Calcite中定义关系表达式优化模式(schema)的接口，包含了表、函数等元数据信息
 * RelOptSchemaWithSampling在RelOptSchema的基础上，增加了对样本数据集的支持能力
 * 样本数据集是指从原始数据集中抽取的代表性数据，常用于查询优化、统计信息收集等场景
 * 通过样本数据集，可以在不处理全部数据的情况下，快速评估查询性能或收集统计信息
 * 
 * @see RelOptSchema // 参见RelOptSchema接口，了解基础schema的定义
 */
public interface RelOptSchemaWithSampling extends RelOptSchema { // 定义接口RelOptSchemaWithSampling，继承自RelOptSchema接口，表示支持样本数据集的关系优化模式
  //~ Methods ---------------------------------------------------------------- // 方法分隔符，用于在IDE中区分成员变量和方法区域

  /**
   * Retrieves a {@link RelOptTable} based upon a member access, using a
   * sample dataset if it exists.
   * 根据成员访问路径获取RelOptTable对象，如果存在样本数据集则使用样本数据集
   * RelOptTable是Calcite中表示关系表的抽象，包含了表的元数据信息，如列信息、统计信息等
   * 该方法是RelOptSchemaWithSampling接口的核心方法，提供了获取表并支持样本数据集替换的能力
   * 
   * @param names       Compound name of table
   *                    表的复合名称，通常是一个或多个字符串组成的列表，表示表的完整路径
   *                    例如：["schema_name", "table_name"] 或 ["catalog_name", "schema_name", "table_name"]
   *                    这个参数用于在schema中定位特定的表
   * @param datasetName Name of sample dataset to substitute, if it exists;
   *                    如果存在样本数据集，则使用该样本数据集进行替换
   *                    参数为样本数据集的名称，如果为null则表示不查找样本数据集，直接使用原始表
   *                    样本数据集通常用于测试、优化或统计信息收集等场景
   * @param usedDataset Output parameter which is set to true if a sample
   *                    dataset is found; may be null
   *                    输出参数，如果找到了样本数据集，则将该布尔数组的第一个元素设置为true
   *                    该参数用于让调用者知道是否实际使用了样本数据集
   *                    如果为null，表示调用者不需要知道是否使用了样本数据集
   *                    注意：这是一个输出参数，通过数组引用来修改调用者的变量
   * @return Table, or null if not found
   *                    返回找到的RelOptTable对象，如果没有找到则返回null
   *                    返回的表可能是原始表，也可能是样本数据集对应的表，取决于datasetName参数和样本数据集是否存在
   */
  @Nullable RelOptTable getTableForMember( // 定义方法getTableForMember，返回可能为null的RelOptTable对象，@Nullable注解表示返回值可能为null
      List<String> names, // 参数1：表的复合名称列表，用于定位表
      @Nullable String datasetName, // 参数2：样本数据集名称，可能为null，@Nullable注解表示该参数可能为null
      boolean @Nullable [] usedDataset); // 参数3：输出参数，布尔数组，用于记录是否使用了样本数据集，可能为null，@Nullable注解表示该参数可能为null
} // 接口定义结束
