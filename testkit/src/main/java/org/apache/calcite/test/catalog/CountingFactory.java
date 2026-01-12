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
package org.apache.calcite.test.catalog; // 测试包，包含用于测试目录相关的类

import org.apache.calcite.plan.RelOptTable; // 导入关系优化表接口，表示表在优化器中的表示
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示表或表达式的类型
import org.apache.calcite.rel.type.RelDataTypeField; // 导入关系数据类型字段接口，表示表中的列
import org.apache.calcite.rex.RexBuilder; // 导入Rex表达式构建器，用于构建行表达式
import org.apache.calcite.rex.RexNode; // 导入行表达式节点接口，表示关系表达式
import org.apache.calcite.schema.ColumnStrategy; // 导入列策略枚举，定义列的生成策略
import org.apache.calcite.sql.SqlFunction; // 导入SQL函数类，表示SQL中的函数
import org.apache.calcite.sql2rel.InitializerContext; // 导入初始化器上下文接口，提供初始化所需的信息
import org.apache.calcite.sql2rel.InitializerExpressionFactory; // 导入初始化表达式工厂接口，用于创建列默认值和属性初始化表达式
import org.apache.calcite.sql2rel.NullInitializerExpressionFactory; // 导入空初始化表达式工厂类，提供默认的空实现
import org.apache.calcite.util.TryThreadLocal; // 导入TryThreadLocal工具类，提供线程局部变量支持

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类

import java.math.BigDecimal; // 导入BigDecimal类，用于精确的十进制数值
import java.util.List; // 导入List接口，表示有序集合
import java.util.concurrent.atomic.AtomicInteger; // 导入原子整数类，提供线程安全的整数操作

/** To check whether
 * {@link InitializerExpressionFactory#newColumnDefaultValue} is called.
 * // 用于检查InitializerExpressionFactory的newColumnDefaultValue方法是否被调用
 *
 * <p>If a column is in {@code defaultColumns}, returns 1 as the default
 * value. */ // 如果列在defaultColumns列表中，返回1作为默认值
public class CountingFactory extends NullInitializerExpressionFactory { // 计数工厂类，继承自空初始化表达式工厂，用于测试和统计方法调用次数
  public static final TryThreadLocal<AtomicInteger> THREAD_CALL_COUNT = // 静态线程局部变量，用于统计每个线程中工厂方法被调用的次数
      TryThreadLocal.withInitial(AtomicInteger::new); // 使用TryThreadLocal创建，初始值为新的AtomicInteger对象

  private final List<String> defaultColumns; // 私有成员变量，存储需要使用默认值策略的列名列表，使用不可变列表保证线程安全

  CountingFactory(List<String> defaultColumns) { // 构造方法，接收列名列表作为参数
    this.defaultColumns = ImmutableList.copyOf(defaultColumns); // 将传入的列表复制为不可变列表，防止外部修改
  }

  @Override public ColumnStrategy generationStrategy(RelOptTable table, // 重写父类方法，确定列的生成策略
      int iColumn) { // 参数iColumn表示列的索引
    final RelDataTypeField field = // 获取指定索引的字段对象
        table.getRowType().getFieldList().get(iColumn); // 从表的行类型字段列表中获取对应索引的字段
    if (defaultColumns.contains(field.getName())) { // 如果字段名在默认列名列表中
      return ColumnStrategy.DEFAULT; // 返回DEFAULT策略，表示该列使用默认值
    }
    return super.generationStrategy(table, iColumn); // 否则调用父类方法，返回默认的生成策略
  }

  @Override public RexNode newColumnDefaultValue(RelOptTable table, // 重写父类方法，创建列的默认值表达式
      int iColumn, InitializerContext context) { // 参数iColumn是列索引，context是初始化器上下文
    THREAD_CALL_COUNT.get().incrementAndGet(); // 增加当前线程的方法调用计数
    final RelDataTypeField field = // 获取指定索引的字段对象
        table.getRowType().getFieldList().get(iColumn); // 从表的行类型字段列表中获取对应索引的字段
    if (defaultColumns.contains(field.getName())) { // 如果字段名在默认列名列表中
      final RexBuilder rexBuilder = context.getRexBuilder(); // 从上下文中获取Rex表达式构建器
      return rexBuilder.makeExactLiteral(BigDecimal.ONE); // 创建精确字面量表达式，值为1（BigDecimal类型）
    }
    return super.newColumnDefaultValue(table, iColumn, context); // 否则调用父类方法，返回父类的默认值表达式
  }

  @Override public RexNode newAttributeInitializer(RelDataType type, // 重写父类方法，创建属性初始化表达式
      SqlFunction constructor, int iAttribute, // constructor是构造函数，iAttribute是属性索引
      List<RexNode> constructorArgs, InitializerContext context) { // constructorArgs是构造函数参数列表，context是初始化器上下文
    THREAD_CALL_COUNT.get().incrementAndGet(); // 增加当前线程的方法调用计数
    return super.newAttributeInitializer(type, constructor, iAttribute, // 调用父类方法，返回父类的属性初始化表达式
       constructorArgs, context); // 传递所有参数给父类方法
  }
}
