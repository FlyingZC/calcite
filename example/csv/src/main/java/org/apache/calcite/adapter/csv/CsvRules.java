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
// Apache Calcite 是一个动态数据管理框架，提供SQL解析、优化、执行等功能
// 本包 org.apache.calcite.adapter.csv 包含CSV文件适配器的实现
package org.apache.calcite.adapter.csv;

/**
 * Planner rules relating to the CSV adapter.
 *
 * <p>CsvRules 类是CSV适配器的优化规则集合中心，负责管理和提供CSV适配器相关的所有优化规则
 *
 * <p><b>类的作用：</b>
 * <ul>
 *   <li>作为CSV适配器优化规则的统一入口点和注册中心</li>
 *   <li>集中管理所有与CSV表扫描相关的优化规则</li>
 *   <li>提供规则实例的静态访问方式，方便其他组件使用</li>
 *   <li>采用抽象类设计，防止实例化，仅作为规则容器使用</li>
 * </ul>
 *
 * <p><b>核心优化技术：</b>
 * <ul>
 *   <li>列裁剪（Column Pruning）：只读取查询所需的列，减少I/O开销</li>
 *   <li>投影下推（Projection Pushdown）：将投影操作下推到表扫描阶段</li>
 *   <li>谓词下推（Predicate Pushdown）：将过滤条件下推到数据源</li>
 * </ul>
 *
 * <p><b>设计模式：</b>
 * <ul>
 *   <li>单例模式：规则实例作为静态常量，全局唯一</li>
 *   <li>工厂模式：通过Config配置对象创建规则实例</li>
 *   <li>策略模式：不同的优化规则实现不同的优化策略</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>
 * // 在CsvTableScan中注册规则
 * planner.addRule(CsvRules.PROJECT_SCAN);
 *
 * // 优化器会自动应用该规则
 * // 当查询包含投影操作时，规则被触发
 * </pre>
 *
 * @see CsvProjectTableScanRule 具体的投影下推规则实现
 * @see CsvTableScan CSV表扫描节点
 * @see org.apache.calcite.plan.RelOptRule Calcite优化规则基类
 */
public abstract class CsvRules { // 抽象类，不能被实例化，仅作为规则容器使用
  /**
   * 私有构造方法，防止外部实例化
   * 
   * <p>由于CsvRules仅作为规则容器，所有规则都通过静态常量访问，
   * 因此不需要实例化。私有构造方法确保该类不会被意外实例化。
   */
  private CsvRules() {} // 私有无参构造方法，防止实例化

  /**
   * CSV投影扫描规则实例
   * 
   * <p><b>规则作用：</b>
   * <ul>
   *   <li>匹配模式：LogicalProject(投影) → CsvTableScan(表扫描)</li>
   *   <li>优化目标：将投影操作下推到CSV表扫描阶段</li>
   *   <li>优化效果：只读取查询所需的列，减少I/O和内存开销</li>
   *   <li>适用场景：查询只使用表的部分列时</li>
   * </ul>
   *
   * <p><b>工作原理：</b>
   * <ol>
   *   <li>检测到LogicalProject节点，其输入是CsvTableScan节点</li>
   *   <li>分析投影表达式，判断是否都是简单的字段引用</li>
   *   <li>如果是简单引用，提取字段索引，创建新的CsvTableScan</li>
   *   <li>新的CsvTableScan只读取指定的列</li>
   *   <li>移除LogicalProject节点，因为投影已在扫描时完成</li>
   * </ol>
   *
   * <p><b>示例：</b>
   * <pre>
   * 原始SQL: SELECT emp_id, emp_name FROM employees
   * 
   * 优化前关系树:
   *   LogicalProject(emp_id, emp_name)
   *     └─ CsvTableScan(读取所有列: emp_id, emp_name, emp_dept, emp_salary)
   * 
   * 优化后关系树:
   *   CsvTableScan(只读取: emp_id, emp_name)
   * </pre>
   *
   * <p><b>限制条件：</b>
   * <ul>
   *   <li>投影表达式必须是简单的字段引用（RexInputRef）</li>
   *   <li>不能包含函数调用、算术运算等复杂表达式</li>
   *   <li>如果包含复杂表达式，规则不会触发，保持原样</li>
   * </ul>
   *
   * <p><b>性能影响：</b>
   * <ul>
   *   <li>减少CSV文件读取的数据量</li>
   *   <li>减少内存占用</li>
   *   <li>提高后续处理阶段的性能</li>
   * </ul>
   *
   * <p><b>配置方式：</b>
   * 通过CsvProjectTableScanRule.Config.DEFAULT.toRule()创建规则实例
   * Config定义了规则的匹配模式和描述信息
   *
   * @see CsvProjectTableScanRule 规则的具体实现类
   * @see CsvProjectTableScanRule.Config 规则配置接口
   * @see org.apache.calcite.rel.core.Project 投影操作节点
   * @see CsvTableScan CSV表扫描节点
   */
  public static final CsvProjectTableScanRule PROJECT_SCAN = // 静态常量，全局唯一的规则实例
      CsvProjectTableScanRule.Config.DEFAULT.toRule(); // 使用默认配置创建规则实例
}
