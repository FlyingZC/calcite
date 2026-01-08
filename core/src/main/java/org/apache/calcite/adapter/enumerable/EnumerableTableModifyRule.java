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
 */ // Apache许可证声明，说明代码的版权和使用条款
package org.apache.calcite.adapter.enumerable; // 声明包名，该类属于org.apache.calcite.adapter.enumerable包，用于可枚举适配器相关功能

import org.apache.calcite.plan.Convention; // 导入Convention类，用于表示关系代数节点的调用约定（convention）
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，用于表示关系节点的特性集合（trait set）
import org.apache.calcite.rel.RelNode; // 导入RelNode类，这是所有关系代数节点的基类
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule类，这是转换规则的基类，用于将一种关系节点转换为另一种
import org.apache.calcite.rel.core.TableModify; // 导入TableModify类，表示表修改操作的关系节点
import org.apache.calcite.rel.logical.LogicalTableModify; // 导入LogicalTableModify类，表示逻辑层面的表修改操作节点
import org.apache.calcite.schema.ModifiableTable; // 导入ModifiableTable接口，表示可以被修改的表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的返回值

/** Planner rule that converts a {@link LogicalTableModify} to an {@link EnumerableTableModify}. // 这是一个规划器规则，用于将逻辑表修改节点转换为可枚举表修改节点
 * You may provide a custom config to convert other nodes that extend {@link TableModify}. // 你可以提供自定义配置来转换其他继承自TableModify的节点
 *
 * @see EnumerableRules#ENUMERABLE_TABLE_MODIFICATION_RULE */ // 参见EnumerableRules中的ENUMERABLE_TABLE_MODIFICATION_RULE常量
public class EnumerableTableModifyRule extends ConverterRule { // 定义EnumerableTableModifyRule类，继承自ConverterRule，用于表修改的转换规则
  /** Default configuration. */ // 默认配置常量的注释
  public static final Config DEFAULT_CONFIG = Config.INSTANCE // 定义静态常量DEFAULT_CONFIG，使用Config.INSTANCE作为基础配置
      .withConversion(LogicalTableModify.class, Convention.NONE, // 配置转换规则：将LogicalTableModify类从Convention.NONE约定
          EnumerableConvention.INSTANCE, "EnumerableTableModificationRule") // 转换为EnumerableConvention.INSTANCE约定，规则名称为"EnumerableTableModificationRule"
      .withRuleFactory(EnumerableTableModifyRule::new); // 设置规则工厂，使用方法引用创建EnumerableTableModifyRule实例

  /** Creates an EnumerableTableModifyRule. */ // 构造方法的注释
  protected EnumerableTableModifyRule(Config config) { // 定义受保护的构造方法，接收Config参数
    super(config); // 调用父类ConverterRule的构造方法，传入配置参数
  }

  @Override public @Nullable RelNode convert(RelNode rel) { // 覆盖父类的convert方法，用于将RelNode转换为另一种RelNode，返回值可能为null
    final TableModify modify = (TableModify) rel; // 将传入的RelNode强制转换为TableModify类型，赋值给modify变量
    final ModifiableTable modifiableTable = // 定义modifiableTable变量，用于存储可修改的表
        modify.getTable().unwrap(ModifiableTable.class); // 从modify的table中解包出ModifiableTable对象
    if (modifiableTable == null) { // 如果解包出的modifiableTable为null，说明表不支持修改操作
      return null; // 返回null，表示转换失败
    } // if语句结束
    final RelTraitSet traitSet = // 定义traitSet变量，用于存储新的特性集合
        modify.getTraitSet().replace(EnumerableConvention.INSTANCE); // 获取modify的特性集，并将其中的约定替换为EnumerableConvention.INSTANCE
    return new EnumerableTableModify( // 创建并返回一个新的EnumerableTableModify对象，这是转换后的节点
        modify.getCluster(), traitSet, // 传入集群对象和特性集合
        modify.getTable(), // 传入表对象
        modify.getCatalogReader(), // 传入目录读取器，用于读取元数据
        convert(modify.getInput(), traitSet), // 递归转换modify的输入节点，传入新的特性集合
        modify.getOperation(), // 传入修改操作类型（INSERT、UPDATE、DELETE等）
        modify.getUpdateColumnList(), // 传入更新操作涉及的列列表
        modify.getSourceExpressionList(), // 传入源表达式列表，用于INSERT和UPDATE操作
        modify.isFlattened()); // 传入是否扁平化的标志
  } // convert方法结束
} // EnumerableTableModifyRule类结束
