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
package org.apache.calcite.adapter.enumerable; // 包声明:属于可枚举适配器包,提供可枚举调用约定的实现

import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入BlockBuilder:用于构建代码块,生成方法体
import org.apache.calcite.linq4j.tree.Expression; // 导入Expression:表示LINQ4J表达式树中的表达式节点
import org.apache.calcite.linq4j.tree.Expressions; // 导入Expressions:工具类,用于创建各种表达式节点
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster:关系表达式集群,包含优化器上下文信息
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet:关系特征集合,定义物理属性如约定、排序等
import org.apache.calcite.rel.RelNode; // 导入RelNode:关系表达式接口,所有关系操作符的基类
import org.apache.calcite.rel.core.Uncollect; // 导入Uncollect:核心抽象类,定义UNNEST/UNCOLLECT操作的语义
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType:关系数据类型,描述字段的类型信息
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField:关系数据类型字段,包含字段名和类型
import org.apache.calcite.runtime.SqlFunctions.FlatProductInputType; // 导入FlatProductInputType:枚举,定义扁平化输入类型(MAP/LIST/SCALAR)
import org.apache.calcite.sql.type.MapSqlType; // 导入MapSqlType:MAP类型的SQL类型表示
import org.apache.calcite.util.BuiltInMethod; // 导入BuiltInMethod:内置方法枚举,引用运行时辅助方法

import com.google.common.primitives.Ints; // 导入Ints:Guava工具类,处理int数组和集合的转换

import java.util.ArrayList; // 导入ArrayList:动态数组实现
import java.util.Collections; // 导入Collections:集合工具类,提供不可变集合等
import java.util.List; // 导入List:列表接口,有序集合

import static org.apache.calcite.sql.type.NonNullableAccessors.getComponentTypeOrThrow; // 静态导入:获取数组/多集类型的元素类型,如果不存在则抛出异常

/** Implementation of {@link org.apache.calcite.rel.core.Uncollect} in
 * {@link org.apache.calcite.adapter.enumerable.EnumerableConvention enumerable calling convention}. */
// 类注释:EnumerableUncollect是Uncollect操作在可枚举调用约定(EnumerableConvention)下的实现
// 作用:将输入的数组或多值集合展开为多行,类似于SQL中的UNNEST操作
// 实现方式:通过生成Java代码,使用LINQ4J的selectMany方法实现扁平化
// 使用场景:处理ARRAY、MULTISET、MAP等集合类型的展开操作
public class EnumerableUncollect extends Uncollect implements EnumerableRel { // 类定义:继承Uncollect抽象类,实现EnumerableRel接口
  @Deprecated // to be removed before 2.0 // 注解:标记为已废弃,将在2.0版本前移除
  public EnumerableUncollect(RelOptCluster cluster, RelTraitSet traitSet, // 构造方法参数:cluster-关系表达式集群,traitSet-特征集合,child-子节点
      RelNode child) { // 构造方法参数:child-输入的关系表达式(包含要展开的数组或多集)
    this(cluster, traitSet, child, false); // 调用主构造方法,withOrdinality默认为false(不包含序号列)
  } // 构造方法结束

  /** Creates an EnumerableUncollect.
   *
   * <p>Use {@link #create} unless you know what you're doing. */
  // 方法注释:创建EnumerableUncollect实例,建议使用create工厂方法
  public EnumerableUncollect(RelOptCluster cluster, RelTraitSet traitSet, // 构造方法参数:cluster-关系表达式集群,traitSet-特征集合,child-子节点
      RelNode child, boolean withOrdinality) { // 构造方法参数:child-输入关系表达式,withOrdinality-是否在输出中添加ORDINALITY列(序号列)
    super(cluster, traitSet, child, withOrdinality, Collections.emptyList()); // 调用父类Uncollect构造方法,传入空列表作为排除字段
    assert getConvention() instanceof EnumerableConvention; // 断言:当前节点的约定必须是EnumerableConvention类型
    assert getConvention() == child.getConvention(); // 断言:当前节点的约定必须与子节点的约定一致,确保约定兼容
  } // 构造方法结束

  /**
   * Creates an EnumerableUncollect.
   *
   * <p>Each field of the input relational expression must be an array or
   * multiset.
   *
   * @param traitSet Trait set
   * @param input    Input relational expression
   * @param withOrdinality Whether output should contain an ORDINALITY column
   */
  // 方法注释:工厂方法,创建EnumerableUncollect实例
  // 说明:输入关系表达式的每个字段必须是数组或多集类型
  // 参数说明:traitSet-特征集合,input-输入关系表达式,withOrdinality-是否输出ORDINALITY列(从0开始的序号)
  public static EnumerableUncollect create(RelTraitSet traitSet, RelNode input, // 方法参数:traitSet-特征集合,input-输入关系表达式
      boolean withOrdinality) { // 方法参数:withOrdinality-是否包含序号列
    final RelOptCluster cluster = input.getCluster(); // 获取输入节点的集群,用于创建新实例
    return new EnumerableUncollect(cluster, traitSet, input, withOrdinality); // 创建并返回新的EnumerableUncollect实例
  } // 方法结束

  @Override public EnumerableUncollect copy(RelTraitSet traitSet, // 方法覆盖:复制当前节点,返回具有新特征集的副本
      RelNode newInput) { // 方法参数:newInput-新的输入关系表达式
    return new EnumerableUncollect(getCluster(), traitSet, newInput, // 创建新实例,保持原集群、新特征集、新输入,以及原withOrdinality值
        withOrdinality); // 保持原有的withOrdinality设置
  } // 方法结束

  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) { // 方法覆盖:实现可枚举关系节点,生成执行代码
    final BlockBuilder builder = new BlockBuilder(); // 创建代码块构建器,用于生成方法体的代码块
    final EnumerableRel child = (EnumerableRel) getInput(); // 获取输入子节点并转换为EnumerableRel类型
    final Result result = implementor.visitChild(this, 0, child, pref); // 访问子节点,获取子节点的实现结果(包含生成的代码块)
    final PhysType physType = // 创建物理类型对象,描述输出行的Java类型表示
        PhysTypeImpl.of( // 调用PhysTypeImpl.of工厂方法创建物理类型
            implementor.getTypeFactory(), // 参数:类型工厂,用于创建RelDataType
            getRowType(), // 参数:当前节点的行类型,描述输出的字段结构
            JavaRowFormat.LIST); // 参数:Java行格式为LIST,表示每行数据表示为List对象

    // final Enumerable<List<Employee>> child = <<child adapter>>;
    // return child.selectMany(FLAT_PRODUCT);
    // 注释说明:生成的代码模式,child是子节点的可枚举结果,selectMany用于扁平化展开
    final Expression child_ = // 创建表达式变量,引用子节点的执行结果
        builder.append( // 将子节点的代码块添加到builder中,并返回引用该结果的表达式
            "child", result.block); // 变量名为"child",值为result.block(子节点生成的代码块)

    final List<Integer> fieldCounts = new ArrayList<>(); // 创建字段计数列表,记录每个输入字段展开后的字段数
    final List<FlatProductInputType> inputTypes = new ArrayList<>(); // 创建输入类型列表,记录每个输入字段的类型(MAP/LIST/SCALAR)

    Expression lambdaForStructWithSingleItem = null; // 声明特殊lambda表达式,用于处理单字段单元素结构的特殊情况
    for (RelDataTypeField field : child.getRowType().getFieldList()) { // 遍历子节点的所有字段
      final RelDataType type = field.getType(); // 获取当前字段的类型
      if (type instanceof MapSqlType) { // 判断:如果是MAP类型
        fieldCounts.add(2); // MAP展开为2个字段(KEY和VALUE),所以添加2到fieldCounts
        inputTypes.add(FlatProductInputType.MAP); // 添加MAP类型到inputTypes列表
      } else { // 如果不是MAP类型
        final RelDataType elementType = getComponentTypeOrThrow(type); // 获取数组/多集类型的元素类型
        if (elementType.isStruct()) { // 判断:如果元素类型是结构体(有多个字段)
          if (elementType.getFieldCount() == 1 && child.getRowType().getFieldList().size() == 1 // 特殊情况:元素是单字段结构体,且输入只有1个字段
              && !withOrdinality) { // 且不包含ORDINALITY列
            // Solves CALCITE-4063: if we are processing a single field, which is a struct with a
            // single item inside, and no ordinality; the result must be a scalar, hence use a
            // special lambda that does not return lists, but the (single) items within those lists
            // 注释:解决CALCITE-4063问题,处理单字段单元素结构体且无ORDINALITY的情况,结果应该是标量而非列表
            lambdaForStructWithSingleItem = Expressions.call(BuiltInMethod.FLAT_LIST.method); // 使用FLAT_LIST方法,直接返回标量值而非列表
          } else { // 普通情况:结构体有多个字段,或有多个输入字段,或包含ORDINALITY
            fieldCounts.add(elementType.getFieldCount()); // 添加结构体的字段数到fieldCounts
            inputTypes.add(FlatProductInputType.LIST); // 添加LIST类型到inputTypes列表
          } // if-else结束
        } else { // 元素类型不是结构体(是标量类型)
          fieldCounts.add(-1); // 标量类型展开为1个字段,使用-1表示标量
          inputTypes.add(FlatProductInputType.SCALAR); // 添加SCALAR类型到inputTypes列表
        } // if-else结束
      } // if-else结束
    } // for循环结束

    final Expression lambda = lambdaForStructWithSingleItem != null // 选择lambda表达式:如果有特殊情况则使用特殊lambda
        ? lambdaForStructWithSingleItem // 使用FLAT_LIST方法处理单字段单元素情况
        : Expressions.call(BuiltInMethod.FLAT_PRODUCT.method, // 否则使用FLAT_PRODUCT方法,处理常规展开操作
            Expressions.constant(Ints.toArray(fieldCounts)), // 参数1:字段计数数组,转换为int数组
            Expressions.constant(withOrdinality), // 参数2:是否包含ORDINALITY列的布尔值
            Expressions.constant( // 参数3:输入类型数组
                inputTypes.toArray(new FlatProductInputType[0]))); // 将inputTypes列表转换为FlatProductInputType数组
    builder.add( // 向代码块添加返回语句
        Expressions.return_(null, // 创建返回表达式,null表示返回值不需要指定类型
            Expressions.call(child_, // 调用child_表达式(子节点的可枚举结果)
                BuiltInMethod.SELECT_MANY.method, // 调用selectMany方法,实现扁平化展开(一对多转换)
                lambda))); // 参数:lambda表达式,定义如何展开每个元素
    return implementor.result(physType, builder.toBlock()); // 返回实现结果,包含物理类型和生成的代码块
  } // 方法结束

}
