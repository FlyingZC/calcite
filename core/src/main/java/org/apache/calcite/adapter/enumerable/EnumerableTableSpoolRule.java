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
package org.apache.calcite.adapter.enumerable; // 指定当前类所在的包路径，位于可枚举适配器包中

import org.apache.calcite.linq4j.function.Experimental; // 导入实验性功能注解，标记API可能变化
import org.apache.calcite.plan.Convention; // 导入调用约定接口，用于定义关系代数节点的物理实现约定
import org.apache.calcite.rel.RelNode; // 导入关系节点基类，表示关系代数树中的一个节点
import org.apache.calcite.rel.convert.ConverterRule; // 导入转换规则基类，用于定义如何将一种关系节点转换为另一种
import org.apache.calcite.rel.core.TableSpool; // 导入表暂存节点基类，表示对表数据进行暂存的逻辑节点
import org.apache.calcite.rel.logical.LogicalTableSpool; // 导入逻辑表暂存节点，表示逻辑层面的表暂存操作

/**
 * Rule to convert a {@link LogicalTableSpool} into an {@link EnumerableTableSpool}. // 规则类的作用：将逻辑表暂存节点转换为可枚举表暂存节点
 * You may provide a custom config to convert other nodes that extend {@link TableSpool}. // 允许提供自定义配置来转换其他继承自TableSpool的节点
 *
 * <p>NOTE: The current API is experimental and subject to change without // 注意：当前API是实验性的，可能会在没有通知的情况下发生变化
 * notice. //
 *
 * @see EnumerableRules#ENUMERABLE_TABLE_SPOOL_RULE // 参见：枚举规则集合中的表暂存规则实例
 */ //
@Experimental // 使用实验性注解标记该类，表明API可能不稳定
public class EnumerableTableSpoolRule extends ConverterRule { // 定义可枚举表暂存规则类，继承自转换规则基类
  /** Default configuration. */ // 默认配置对象的说明注释
  public static final Config DEFAULT_CONFIG = Config.INSTANCE // 定义默认配置常量，使用Config单例实例作为基础
      .withConversion(LogicalTableSpool.class, Convention.NONE, // 配置转换规则：将LogicalTableSpool类从NONE约定转换
          EnumerableConvention.INSTANCE, "EnumerableTableSpoolRule") // 到可枚举约定，规则名称为"EnumerableTableSpoolRule"
      .withRuleFactory(EnumerableTableSpoolRule::new); // 设置规则工厂方法，使用构造函数引用来创建规则实例

  /** Called from the Config. */ // 构造函数说明注释：由配置对象调用
  protected EnumerableTableSpoolRule(Config config) { // 定义受保护的构造函数，接收配置对象作为参数
    super(config); // 调用父类ConverterRule的构造函数，传入配置对象进行初始化
  } //

  @Override public RelNode convert(RelNode rel) { // 重写convert方法，实现关系节点的转换逻辑，接收待转换的关系节点
    TableSpool spool = (TableSpool) rel; // 将输入的关系节点强制转换为TableSpool类型，以便访问暂存相关属性
    return EnumerableTableSpool.create( // 创建并返回可枚举表暂存节点，传入转换后的输入和相关属性
        convert(spool.getInput(), // 首先转换spool的输入节点，将其转换为可枚举约定
            spool.getInput().getTraitSet().replace(EnumerableConvention.INSTANCE)), // 获取输入节点的特征集，并将约定替换为可枚举约定
        spool.readType, // 传入读取类型，表示从暂存中读取数据的方式（如LAZY懒加载或EAGER急切加载）
        spool.writeType, // 传入写入类型，表示向暂存中写入数据的方式
        spool.getTable()); // 传入暂存表对象，包含暂存数据的物理存储信息
  } //
} // 类结束
