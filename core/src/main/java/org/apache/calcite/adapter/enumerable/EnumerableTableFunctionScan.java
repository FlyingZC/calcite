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
package org.apache.calcite.adapter.enumerable; // 包声明：声明此类属于org.apache.calcite.adapter.enumerable包，这是Calcite中可枚举适配器包

import org.apache.calcite.DataContext; // 导入DataContext接口，提供查询执行时的上下文信息
import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入JavaTypeFactory接口，用于创建Java类型
import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入BlockBuilder类，用于构建代码块表达式
import org.apache.calcite.linq4j.tree.Expression; // 导入Expression类，表示LINQ表达式树中的表达式
import org.apache.calcite.linq4j.tree.Expressions; // 导入Expressions工具类，用于创建各种表达式
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系代数表达式集群
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系表达式的特征集合
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数表达式树的节点
import org.apache.calcite.rel.core.TableFunctionScan; // 导入TableFunctionScan类，表示表函数扫描操作
import org.apache.calcite.rel.metadata.RelColumnMapping; // 导入RelColumnMapping类，表示列映射元数据
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系数据类型
import org.apache.calcite.rex.RexCall; // 导入RexCall类，表示行表达式调用
import org.apache.calcite.rex.RexNode; // 导入RexNode接口，表示行表达式节点
import org.apache.calcite.schema.QueryableTable; // 导入QueryableTable接口，表示可查询的表
import org.apache.calcite.schema.impl.TableFunctionImpl; // 导入TableFunctionImpl类，表示表函数实现
import org.apache.calcite.sql.SqlWindowTableFunction; // 导入SqlWindowTableFunction类，表示SQL窗口表函数
import org.apache.calcite.sql.validate.SqlConformance; // 导入SqlConformance接口，表示SQL兼容性
import org.apache.calcite.sql.validate.SqlConformanceEnum; // 导入SqlConformanceEnum枚举，表示SQL兼容性枚举值
import org.apache.calcite.sql.validate.SqlUserDefinedTableFunction; // 导入SqlUserDefinedTableFunction类，表示用户定义的SQL表函数

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，表示可空类型

import java.lang.reflect.Method; // 导入Method类，用于反射获取方法信息
import java.lang.reflect.Type; // 导入Type接口，表示Java类型
import java.util.List; // 导入List接口，表示列表集合
import java.util.Set; // 导入Set接口，表示集合

/** Implementation of {@link org.apache.calcite.rel.core.TableFunctionScan} in
 * {@link org.apache.calcite.adapter.enumerable.EnumerableConvention enumerable calling convention}. */
// 类注释：这是TableFunctionScan在可枚举调用约定下的实现类
// TableFunctionScan：表示表函数扫描操作，即调用表函数生成数据
// EnumerableConvention：可枚举调用约定，表示该关系表达式可以被转换为可枚举的Java代码
// 这个类主要负责将表函数扫描操作转换为可执行的Java代码
public class EnumerableTableFunctionScan extends TableFunctionScan // 类声明：继承自TableFunctionScan，表示表函数扫描
    implements EnumerableRel { // 实现EnumerableRel接口，表示这是一个可枚举的关系表达式

  // 构造方法：创建一个EnumerableTableFunctionScan实例
  // 参数说明：
  // - cluster: 关系代数表达式集群，包含优化器和类型工厂等共享资源
  // - traits: 关系表达式的特征集合，定义了该节点的物理属性（如可枚举约定）
  // - inputs: 输入关系节点列表，表函数可能有0个或多个输入
  // - elementType: 表函数返回的元素类型，可能为null
  // - rowType: 表函数返回的行类型，定义了输出行的结构
  // - call: 表函数调用的Rex表达式，包含函数名和参数
  // - columnMappings: 列映射集合，定义了输出列与输入列的映射关系，可能为null
  public EnumerableTableFunctionScan(RelOptCluster cluster, // 参数：关系表达式集群
      RelTraitSet traits, // 参数：特征集合
      List<RelNode> inputs, // 参数：输入关系节点列表
      @Nullable Type elementType, // 参数：元素类型，可为空
      RelDataType rowType, // 参数：行类型
      RexNode call, // 参数：表函数调用表达式
      @Nullable Set<RelColumnMapping> columnMappings) { // 参数：列映射集合，可为空
    super(cluster, traits, inputs, call, elementType, rowType, // 调用父类构造方法，初始化TableFunctionScan基类
        columnMappings); // 传递列映射参数
  }

  // copy方法：创建当前关系节点的副本，用于优化器重写关系树
  // 参数说明：
  // - traitSet: 新的特征集合，可能包含不同的物理属性
  // - inputs: 新的输入关系节点列表
  // - rexCall: 新的表函数调用表达式
  // - elementType: 新的元素类型
  // - rowType: 新的行类型
  // - columnMappings: 新的列映射集合
  // 返回值：返回一个新的EnumerableTableFunctionScan实例
  @Override public EnumerableTableFunctionScan copy( // 方法声明：重写copy方法
      RelTraitSet traitSet, // 参数：特征集合
      List<RelNode> inputs, // 参数：输入节点列表
      RexNode rexCall, // 参数：表函数调用表达式
      @Nullable Type elementType, // 参数：元素类型
      RelDataType rowType, // 参数：行类型
      @Nullable Set<RelColumnMapping> columnMappings) { // 参数：列映射集合
    return new EnumerableTableFunctionScan(getCluster(), traitSet, inputs, // 创建并返回新的实例，使用当前集群和新参数
        elementType, rowType, rexCall, columnMappings); // 传递所有参数
  }

  // implement方法：实现该关系表达式为可执行的Java代码
  // 这是EnumerableRel接口的核心方法，用于将关系表达式转换为可执行的LINQ表达式
  // 参数说明：
  // - implementor: 关系表达式实现器，负责代码生成过程
  // - pref: 代码生成偏好设置，影响生成的代码风格
  // 返回值：返回Result对象，包含生成的代码块和物理类型信息
  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) { // 方法声明：实现关系表达式
    if (isImplementorDefined((RexCall) getCall())) { // 判断是否有专门为该表函数定义的实现器
      return tvfImplementorBasedImplement(implementor, pref); // 如果有，使用基于TVF实现器的实现方式
    } else { // 如果没有专门定义的实现器
      return defaultTableFunctionImplement(implementor, pref); // 使用默认的表函数实现方式
    }
  }

  // isImplementorDefined方法：判断表函数是否有专门定义的实现器
  // 这是一个静态方法，用于检查RexImpTable中是否注册了该表函数的实现
  // 参数说明：
  // - call: 表函数调用的RexCall表达式
  // 返回值：如果有专门定义的实现器返回true，否则返回false
  private static boolean isImplementorDefined(RexCall call) { // 方法声明：静态方法，检查实现器是否定义
    if (call.getOperator() instanceof SqlWindowTableFunction // 检查操作符是否是SQL窗口表函数
        && RexImpTable.INSTANCE.get((SqlWindowTableFunction) call.getOperator()) != null) { // 并且在RexImpTable中找到了对应的实现
      return true; // 返回true，表示有专门定义的实现器
    }
    return false; // 返回false，表示没有专门定义的实现器
  }

  // isQueryable方法：判断表函数是否返回QueryableTable类型
  // QueryableTable是Calcite中的一种特殊表类型，支持LINQ查询
  // 返回值：如果表函数返回QueryableTable类型返回true，否则返回false
  private boolean isQueryable() { // 方法声明：判断是否可查询
    if (!(getCall() instanceof RexCall)) { // 检查调用是否是RexCall类型
      return false; // 如果不是，返回false
    }
    final RexCall call = (RexCall) getCall(); // 将调用转换为RexCall类型
    if (!(call.getOperator() instanceof SqlUserDefinedTableFunction)) { // 检查操作符是否是用户定义的表函数
      return false; // 如果不是，返回false
    }
    final SqlUserDefinedTableFunction udtf = // 获取用户定义的表函数
        (SqlUserDefinedTableFunction) call.getOperator(); // 强制类型转换
    if (!(udtf.getFunction() instanceof TableFunctionImpl)) { // 检查函数是否是TableFunctionImpl实现
      return false; // 如果不是，返回false
    }
    final TableFunctionImpl tableFunction = // 获取表函数实现
        (TableFunctionImpl) udtf.getFunction(); // 强制类型转换
    final Method method = tableFunction.method; // 获取表函数的Java方法
    return QueryableTable.class.isAssignableFrom(method.getReturnType()); // 检查返回类型是否是QueryableTable或其子类
  }

  // defaultTableFunctionImplement方法：使用默认方式实现表函数
  // 这个方法处理没有专门定义实现器的表函数，通过翻译Rex表达式生成代码
  // 参数说明：
  // - implementor: 关系表达式实现器
  // - pref: 代码生成偏好设置（当前未使用）
  // 返回值：返回Result对象，包含生成的代码
  private Result defaultTableFunctionImplement( // 方法声明：默认表函数实现
      EnumerableRelImplementor implementor, // 参数：实现器
      @SuppressWarnings("unused") Prefer pref) { // 参数：偏好设置，抑制未使用警告
    BlockBuilder bb = new BlockBuilder(); // 创建代码块构建器，用于构建Java代码块
    // Non-array user-specified types are not supported yet
    // 注释：非数组的用户指定类型尚不支持
    final JavaRowFormat format; // 声明Java行格式变量，决定行的表示方式
    Type elementType = getElementType(); // 获取表函数返回的元素类型
    if (elementType == null) { // 如果元素类型为null
      format = JavaRowFormat.ARRAY; // 使用数组格式表示行
    } else if (getRowType().getFieldCount() == 1 && isQueryable()) { // 如果只有一列且可查询
      format = JavaRowFormat.SCALAR; // 使用标量格式，直接返回单个值
    } else if (elementType instanceof Class // 如果元素类型是Class类型
        && Object[].class.isAssignableFrom((Class<?>) elementType)) { // 并且是Object数组的子类
      format = JavaRowFormat.ARRAY; // 使用数组格式
    } else { // 其他情况
      format = JavaRowFormat.CUSTOM; // 使用自定义格式
    }
    final PhysType physType = // 创建物理类型，描述代码生成时的类型信息
        PhysTypeImpl.of(implementor.getTypeFactory(), getRowType(), format, // 使用类型工厂、行类型和格式创建
            false); // 不优化
    RexToLixTranslator t = // 创建Rex到LINQ表达式的转换器
        RexToLixTranslator.forAggregation( // 为聚合场景创建转换器
            (JavaTypeFactory) getCluster().getTypeFactory(), // 传入Java类型工厂
            bb, null, // 传入代码块构建器
            implementor.getConformance()); // 传入SQL兼容性设置
    t = t.setCorrelates(implementor.allCorrelateVariables); // 设置关联变量，用于处理相关子查询
    bb.add(Expressions.return_(null, t.translate(getCall()))); // 添加返回语句，翻译表函数调用并返回结果
    return implementor.result(physType, bb.toBlock()); // 返回实现结果，包含物理类型和生成的代码块
  }

  // tvfImplementorBasedImplement方法：使用基于TVF实现器的方式实现表函数
  // 这个方法处理有专门定义实现器的表函数（如窗口函数），使用RexImpTable中的实现
  // 参数说明：
  // - implementor: 关系表达式实现器
  // - pref: 代码生成偏好设置
  // 返回值：返回Result对象，包含生成的代码
  private Result tvfImplementorBasedImplement( // 方法声明：基于TVF实现器的实现
      EnumerableRelImplementor implementor, Prefer pref) { // 参数：实现器和偏好设置
    final JavaTypeFactory typeFactory = implementor.getTypeFactory(); // 获取Java类型工厂
    final BlockBuilder builder = new BlockBuilder(); // 创建代码块构建器
    final EnumerableRel child = (EnumerableRel) getInputs().get(0); // 获取第一个输入子节点（转换为可枚举关系）
    final Result result = // 实现子节点，获取子节点的实现结果
        implementor.visitChild(this, 0, child, pref); // 访问并实现子节点
    final PhysType physType = // 创建物理类型，使用偏好设置选择格式
        PhysTypeImpl.of(typeFactory, getRowType(), pref.prefer(result.format)); // 根据偏好选择行格式
    final Expression inputEnumerable = // 将子节点的结果作为输入可枚举对象
        builder.append("_input", result.block, false); // 添加到代码块中，变量名为"_input"
    final SqlConformance conformance = // 获取SQL兼容性设置
        (SqlConformance) implementor.map.getOrDefault("_conformance", // 从实现器的map中获取，默认为DEFAULT
            SqlConformanceEnum.DEFAULT); // 使用枚举默认值

    builder.add( // 添加表函数翻译代码到代码块
        RexToLixTranslator.translateTableFunction( // 调用RexToLixTranslator的静态方法翻译表函数
            typeFactory, // 传入类型工厂
            conformance, // 传入SQL兼容性
            builder, // 传入代码块构建器
            DataContext.ROOT, // 传入数据上下文根
            (RexCall) getCall(), // 传入表函数调用表达式
            inputEnumerable, // 传入输入可枚举对象
            result.physType, // 传入子节点的物理类型
            physType)); // 传入当前节点的物理类型

    return implementor.result(physType, builder.toBlock()); // 返回实现结果，包含物理类型和生成的代码块
  }
} // 类结束
