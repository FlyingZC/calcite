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
package org.apache.calcite.adapter.file; // 声明包名，表示这个类属于 org.apache.calcite.adapter.file 包，是文件适配器模块的一部分

/** Planner rules relating to the File adapter. */
// FileRules 类：文件适配器的优化规则集合类
// 作用：这是一个抽象工具类，用于定义和管理与文件适配器相关的所有优化规则
// 它作为规则注册中心，集中管理文件适配器在查询优化过程中使用的各种转换规则
// 这些规则主要用于优化基于文件（如CSV文件）的数据源查询，通过规则匹配和转换来提升查询性能
// 该类是抽象类且私有构造，确保只能通过静态成员访问规则实例，防止实例化
public abstract class FileRules {
  private FileRules() {} // 私有构造函数，防止类被实例化，确保这是一个纯工具类，只能通过静态成员访问

  /** Rule that matches a {@link org.apache.calcite.rel.core.Project} on
   * a {@link CsvTableScan} and pushes down projects if possible. */
  // PROJECT_SCAN 成员变量：CSV文件表扫描的投影下推规则实例
  // 作用：定义了一个静态常量规则，用于将投影（Project）操作下推到CSV表扫描（CsvTableScan）操作中
  // 这是一个优化规则，通过将投影操作尽可能下推到数据源层面，减少后续处理的数据量，提升查询性能
  // CsvProjectTableScanRule 是具体的规则实现类，Config.DEFAULT.toRule() 使用默认配置创建规则实例
  // 该规则会匹配模式：Project -> CsvTableScan，并将Project中的投影表达式下推到CsvTableScan中执行
  // 这样可以在读取文件时就只选择需要的列，避免读取和处理不需要的数据
  public static final CsvProjectTableScanRule PROJECT_SCAN =
      CsvProjectTableScanRule.Config.DEFAULT.toRule(); // 使用默认配置创建CsvProjectTableScanRule规则实例并赋值给PROJECT_SCAN常量
}
