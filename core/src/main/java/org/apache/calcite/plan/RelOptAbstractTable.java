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
 */ // Apache许可证声明,表明代码遵循Apache 2.0许可证
package org.apache.calcite.plan; // 声明包名,该类属于org.apache.calcite.plan包,该包负责关系代数优化相关功能

import org.apache.calcite.linq4j.tree.Expression; // 导入Expression类,用于表示LINQ表达式树,用于代码生成
import org.apache.calcite.prepare.RelOptTableImpl; // 导入RelOptTableImpl类,RelOptTable接口的默认实现类
import org.apache.calcite.rel.RelCollation; // 导入RelCollation接口,表示关系的排序规则
import org.apache.calcite.rel.RelDistribution; // 导入RelDistribution接口,表示关系的分布方式
import org.apache.calcite.rel.RelDistributions; // 导入RelDistributions类,提供常用的分布方式常量
import org.apache.calcite.rel.RelNode; // 导入RelNode接口,表示关系代数表达式树中的节点
import org.apache.calcite.rel.RelReferentialConstraint; // 导入RelReferentialConstraint接口,表示外键约束
import org.apache.calcite.rel.logical.LogicalTableScan; // 导入LogicalTableScan类,表示逻辑表扫描操作符
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口,表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField接口,表示关系数据类型的字段
import org.apache.calcite.schema.ColumnStrategy; // 导入ColumnStrategy枚举,表示列的策略(如虚拟列、存储列等)
import org.apache.calcite.util.ImmutableBitSet; // 导入ImmutableBitSet类,不可变的位集合,用于表示列索引集合

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList类,提供不可变列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解,用于标记可能为null的返回值

import java.util.Collections; // 导入Java的Collections工具类,提供集合操作方法
import java.util.List; // 导入Java的List接口,表示有序集合

/**
 * Partial implementation of {@link RelOptTable}.
 */ // 类文档注释:这是RelOptTable接口的部分实现,提供了一些默认行为
public abstract class RelOptAbstractTable implements RelOptTable { // 声明抽象类RelOptAbstractTable,实现RelOptTable接口,表示关系优化表的抽象基类
  //~ Instance fields -------------------------------------------------------- // 成员变量区域的分隔注释
  // 定义三个受保护的最终成员变量,分别表示表所属的模式、表的行类型、表的名称
  protected final RelOptSchema schema; // 成员变量:schema,表示该表所属的关系优化模式(RelOptSchema),包含了该表所在的模式信息,用于查询优化器了解表所在的上下文
  protected final RelDataType rowType; // 成员变量:rowType,表示该表的行类型(RelDataType),描述了表中所有字段的数据类型、名称等信息,是类型系统的重要组成部分
  protected final String name; // 成员变量:name,表示该表的名称(String),用于唯一标识表,在SQL查询中引用表时使用

  //~ Constructors ----------------------------------------------------------- // 构造方法区域的分隔注释
  // 定义受保护的构造方法,用于初始化RelOptAbstractTable实例
  protected RelOptAbstractTable( // 构造方法声明,受保护访问修饰符,表示只有子类可以调用此构造方法
      RelOptSchema schema, // 构造参数:schema,关系优化模式对象,指定表所属的模式
      String name, // 构造参数:name,表名称字符串,用于标识表
      RelDataType rowType) { // 构造参数:rowType,关系数据类型对象,描述表的行结构
    this.schema = schema; // 构造方法体:将传入的schema参数赋值给成员变量schema
    this.name = name; // 构造方法体:将传入的name参数赋值给成员变量name
    this.rowType = rowType; // 构造方法体:将传入的rowType参数赋值给成员变量rowType
  } // 构造方法结束

  //~ Methods ---------------------------------------------------------------- // 方法区域的分隔注释
  // 定义公共方法getName,用于获取表名称
  public String getName() { // 方法声明:公共方法,返回String类型,无参数
    return name; // 方法体:返回成员变量name的值
  } // 方法结束

  @Override public List<String> getQualifiedName() { // 重写方法:获取表的限定名称列表,使用@Override注解表示覆盖接口方法
    return ImmutableList.of(name); // 方法体:返回只包含表名称的不可变列表,限定名称通常包含模式名和表名,但这里简化为只返回表名
  } // 方法结束

  @Override public double getRowCount() { // 重写方法:获取表的行数估计值,使用@Override注解
    return 100; // 方法体:返回固定值100作为默认行数估计,这个估计值用于查询优化器的成本计算
  } // 方法结束

  @Override public RelDataType getRowType() { // 重写方法:获取表的行类型,使用@Override注解
    return rowType; // 方法体:返回成员变量rowType,提供表的结构信息(字段列表及类型)
  } // 方法结束

  @Override public RelOptSchema getRelOptSchema() { // 重写方法:获取表所属的关系优化模式,使用@Override注解
    return schema; // 方法体:返回成员变量schema,提供表所在的模式上下文
  } // 方法结束

  // Override to define collations. // 注释说明:子类可以重写此方法来定义排序规则
  @Override public @Nullable List<RelCollation> getCollationList() { // 重写方法:获取表的排序规则列表,@Nullable表示返回值可能为null
    return Collections.emptyList(); // 方法体:返回空列表,表示该表没有预定义的排序规则,子类可以重写以提供实际的排序规则
  } // 方法结束

  @Override public @Nullable RelDistribution getDistribution() { // 重写方法:获取表的数据分布方式,@Nullable表示返回值可能为null
    return RelDistributions.BROADCAST_DISTRIBUTED; // 方法体:返回广播分布,表示数据会被广播到所有节点,这是默认的分布方式
  } // 方法结束

  @Override public <T extends Object> @Nullable T unwrap(Class<T> clazz) { // 重写方法:将表对象包装为指定类型的实例,泛型方法,T必须继承自Object
    return clazz.isInstance(this) // 方法体:检查当前对象是否是指定类型的实例
        ? clazz.cast(this) // 三元运算符条件为真时:将当前对象强制转换为指定类型并返回
        : null; // 三元运算符条件为假时:返回null,表示无法转换为指定类型
  } // 方法结束

  // Override to define keys // 注释说明:子类可以重写此方法来定义键
  @Override public boolean isKey(ImmutableBitSet columns) { // 重写方法:检查指定的列集合是否构成键,参数columns是列索引的位集合
    return false; // 方法体:返回false,表示默认情况下列集合不是键,子类可以重写以实现实际的键检查逻辑
  } // 方法结束

  // Override to get unique keys // 注释说明:子类可以重写此方法来获取唯一键
  @Override public @Nullable List<ImmutableBitSet> getKeys() { // 重写方法:获取表中所有的唯一键列表,返回值是列索引位集合的列表
    return Collections.emptyList(); // 方法体:返回空列表,表示该表没有唯一键,子类可以重写以提供实际的键信息
  } // 方法结束

  // Override to define foreign keys // 注释说明:子类可以重写此方法来定义外键
  @Override public @Nullable List<RelReferentialConstraint> getReferentialConstraints() { // 重写方法:获取表的外键约束列表
    return Collections.emptyList(); // 方法体:返回空列表,表示该表没有外键约束,子类可以重写以提供实际的外键约束信息
  } // 方法结束

  @Override public RelNode toRel(ToRelContext context) { // 重写方法:将表转换为关系节点(RelNode),参数context是转换上下文
    return LogicalTableScan.create(context.getCluster(), this, // 方法体:创建逻辑表扫描节点,参数包括集群(Cluster)、表对象本身(this)和表提示
        context.getTableHints()); // 继续传递表提示(TableHints),这些提示可以影响查询优化器的决策
  } // 方法结束

  @Override public @Nullable Expression getExpression(Class clazz) { // 重写方法:获取该表的LINQ表达式,参数clazz是表达式类型
    return null; // 方法体:返回null,表示默认情况下无法生成LINQ表达式,子类可以重写以提供表达式生成逻辑
  } // 方法结束

  @Override public RelOptTable extend(List<RelDataTypeField> extendedFields) { // 重写方法:扩展表,添加新的字段,参数extendedFields是要添加的字段列表
    throw new UnsupportedOperationException(); // 方法体:抛出不支持操作异常,表示默认情况下不支持表扩展,子类可以重写以实现扩展逻辑
  } // 方法结束

  @Override public List<ColumnStrategy> getColumnStrategies() { // 重写方法:获取表中每一列的策略,返回列策略列表
    return RelOptTableImpl.columnStrategies(this); // 方法体:调用RelOptTableImpl的静态方法columnStrategies,传入当前表对象,计算并返回各列的策略
  } // 方法结束

} // 类定义结束