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
package org.apache.calcite.adapter.spark; // 定义包名，该类属于Spark适配器包，用于Calcite与Spark的集成

// 导入Java行格式枚举，用于定义Java代码生成时的行格式规范
import org.apache.calcite.adapter.enumerable.JavaRowFormat;
// 导入物理类型接口，表示物理执行计划中的类型信息
import org.apache.calcite.adapter.enumerable.PhysType;
// 导入物理类型实现类，提供物理类型的具体实现
import org.apache.calcite.adapter.enumerable.PhysTypeImpl;
// 导入Java类型工厂，用于创建和管理Java类型
import org.apache.calcite.adapter.java.JavaTypeFactory;
// 导入JDBC约定类，定义JDBC适配器的特征和规范
import org.apache.calcite.adapter.jdbc.JdbcConvention;
// 导入JDBC实现器，用于将关系表达式转换为JDBC SQL语句
import org.apache.calcite.adapter.jdbc.JdbcImplementor;
// 导入JDBC关系表达式接口，表示JDBC数据源上的关系表达式
import org.apache.calcite.adapter.jdbc.JdbcRel;
// 导入JDBC Schema类，表示JDBC数据源的Schema（模式）定义
import org.apache.calcite.adapter.jdbc.JdbcSchema;
// 导入Calcite系统属性类，用于获取和设置系统级别的配置属性
import org.apache.calcite.config.CalciteSystemProperty;
// 导入代码块构建器，用于构建Java代码块表达式
import org.apache.calcite.linq4j.tree.BlockBuilder;
// 导入表达式基类，表示LINQ4J中的表达式节点
import org.apache.calcite.linq4j.tree.Expression;
// 导入表达式工具类，提供创建各种表达式的方法
import org.apache.calcite.linq4j.tree.Expressions;
// 导入基本类型枚举，表示Java的基本类型及其包装类
import org.apache.calcite.linq4j.tree.Primitive;
// 导入约定特征定义类，定义关系表达式转换的特征规范
import org.apache.calcite.plan.ConventionTraitDef;
// 导入优化集群类，表示优化器的工作单元，包含共享资源
import org.apache.calcite.plan.RelOptCluster;
// 导入优化成本接口，表示关系表达式执行的成本估算
import org.apache.calcite.plan.RelOptCost;
// 导入优化器接口，定义关系表达式优化器的行为
import org.apache.calcite.plan.RelOptPlanner;
// 导入关系特征集合类，表示关系表达式的一组特征（如约定、排序等）
import org.apache.calcite.plan.RelTraitSet;
// 导入关系节点接口，表示关系代数表达式的基本单元
import org.apache.calcite.rel.RelNode;
// 导入转换器实现类，用于将一个约定的关系表达式转换为另一个约定
import org.apache.calcite.rel.convert.ConverterImpl;
// 导入关系元数据查询类，用于查询关系表达式的元数据信息
import org.apache.calcite.rel.metadata.RelMetadataQuery;
// 导入SQL方言类，定义不同数据库的SQL语法差异
import org.apache.calcite.sql.SqlDialect;
// 导入内置方法枚举，包含Calcite内置的方法定义
import org.apache.calcite.util.BuiltInMethod;

// 导入可空注解，用于标记可能为null的返回值
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入数组列表类，用于存储动态数组
import java.util.ArrayList;
// 导入列表接口，定义列表操作的规范
import java.util.List;

// 静态导入Objects类的requireNonNull方法，用于参数非空校验
import static java.util.Objects.requireNonNull;

/**
 * Relational expression representing a scan of a table in a JDBC data source
 * that returns its results as a Spark RDD.
 */
// 类的中文注释：JdbcToSparkConverter是一个关系表达式转换器，用于将JDBC数据源上的表扫描操作转换为Spark RDD格式
// 它继承自ConverterImpl，实现了SparkRel接口，是Calcite适配器架构中连接JDBC和Spark的关键组件
// 该类主要负责将JDBC约定（JdbcConvention）的关系表达式转换为Spark约定（SparkConvention）的关系表达式
// 使得Calcite优化器可以将JDBC数据源的查询下推到Spark集群执行
public class JdbcToSparkConverter // 定义类名，表示从JDBC到Spark的转换器
    extends ConverterImpl // 继承转换器实现基类，提供关系表达式转换的基础功能
    implements SparkRel { // 实现SparkRel接口，表示这是一个Spark适配器的关系表达式
  // 构造方法：创建JdbcToSparkConverter实例，初始化关系表达式的基本属性
  // 参数说明：
  //   cluster - 优化集群，包含类型工厂等共享资源
  //   traits - 关系特征集合，定义该关系表达式的特征（如约定、排序等）
  //   input - 输入关系节点，即要转换的JDBC关系表达式
  protected JdbcToSparkConverter(RelOptCluster cluster, RelTraitSet traits, // 接收优化集群和特征集合参数
      RelNode input) { // 接收输入关系节点参数
    super(cluster, ConventionTraitDef.INSTANCE, traits, input); // 调用父类构造方法，初始化转换器
  } // 构造方法结束

  // copy方法：创建该关系表达式的副本，用于优化器的转换和重写过程
  // 参数说明：
  //   traitSet - 新的特征集合，可能包含不同的约定或其他特征
  //   inputs - 新的输入关系节点列表
  // 返回值：返回新的JdbcToSparkConverter实例
  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法
    return new JdbcToSparkConverter( // 创建新的JdbcToSparkConverter实例
        getCluster(), traitSet, sole(inputs)); // 使用当前集群、新特征集和唯一的输入节点
  } // copy方法结束

  // computeSelfCost方法：计算该关系表达式自身的执行成本，用于优化器的成本估算
  // 参数说明：
  //   planner - 优化器实例，用于访问成本估算函数
  //   mq - 关系元数据查询对象，用于获取元数据信息
  // 返回值：返回优化后的成本，通常比输入节点的成本低，因为这是转换节点本身
  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写成本计算方法
      RelMetadataQuery mq) { // 接收元数据查询参数
    final RelOptCost cost = requireNonNull(super.computeSelfCost(planner, mq)); // 调用父类方法计算基础成本
    return cost.multiplyBy(.1); // 将成本乘以0.1，使转换节点的成本较低，鼓励优化器使用此转换
  } // computeSelfCost方法结束

  // implementSpark方法：实现Spark执行逻辑，将关系表达式转换为可执行的Spark代码块
  // 参数说明：
  //   implementor - Spark实现器，提供类型工厂等资源
  // 返回值：返回SparkRel.Result对象，包含物理类型和生成的代码块
  @Override public SparkRel.Result implementSpark(SparkRel.Implementor implementor) { // 重写Spark实现方法
    // Generate:
    //   ResultSetEnumerable.of(schema.getDataSource(), "select ...")
    // 生成代码的目标是创建一个ResultSetEnumerable对象，通过JDBC执行SQL查询并返回结果集
    final BlockBuilder list = new BlockBuilder(); // 创建代码块构建器，用于构建Java代码块
    final JdbcRel child = (JdbcRel) getInput(); // 获取输入节点并转换为JdbcRel类型，这是JDBC关系表达式
    final PhysType physType = // 创建物理类型对象，描述输出行的Java类型信息
        PhysTypeImpl.of( // 使用PhysTypeImpl工厂方法创建
            implementor.getTypeFactory(), getRowType(), // 使用实现器的类型工厂和当前行类型
            JavaRowFormat.CUSTOM); // 使用自定义Java行格式
    final JdbcConvention jdbcConvention = // 获取JDBC约定对象，包含JDBC方言和数据源表达式
        requireNonNull((JdbcConvention) child.getConvention()); // 从子节点获取约定并确保非空
    String sql = generateSql(jdbcConvention.dialect); // 生成SQL语句，根据JDBC方言将关系表达式转换为SQL
    if (CalciteSystemProperty.DEBUG.value()) { // 检查是否启用了DEBUG模式
      System.out.println("[" + sql + "]"); // 如果启用DEBUG，打印生成的SQL语句用于调试
    } // DEBUG打印结束
    final Expression sqlLiteral = // 创建SQL字符串的常量表达式
        list.append("sql", Expressions.constant(sql)); // 将SQL字符串作为常量添加到代码块中
    final List<Primitive> primitives = new ArrayList<>(); // 创建基本类型列表，用于存储每列的Java类型
    for (int i = 0; i < getRowType().getFieldCount(); i++) { // 遍历结果集的每一列
      final Primitive primitive = Primitive.ofBoxOr(physType.fieldClass(i)); // 获取字段对应的基本类型
      primitives.add(primitive != null ? primitive : Primitive.OTHER); // 添加到列表，如果不是基本类型则使用OTHER
    } // 遍历列结束
    final Expression primitivesLiteral = // 创建基本类型数组的常量表达式
        list.append("primitives", // 将基本类型数组添加到代码块中
            Expressions.constant( // 创建常量表达式
                primitives.toArray(new Primitive[0]))); // 将列表转换为数组
    final Expression enumerable = // 创建可枚举对象表达式，用于执行JDBC查询
        list.append( // 将表达式添加到代码块
            "enumerable", // 变量名为enumerable
            Expressions.call( // 创建方法调用表达式
                BuiltInMethod.RESULT_SET_ENUMERABLE_OF.method, // 调用ResultSetEnumerable.of方法
                Expressions.call( // 创建嵌套方法调用
                    Expressions.convert_( // 类型转换表达式
                        jdbcConvention.expression, // 将JDBC约定表达式转换
                        JdbcSchema.class), // 转换为JdbcSchema类型
                    BuiltInMethod.JDBC_SCHEMA_DATA_SOURCE.method), // 调用getDataSource方法获取数据源
                sqlLiteral, // SQL字符串参数
                primitivesLiteral)); // 基本类型数组参数
    list.add( // 添加返回语句到代码块
        Expressions.return_(null, enumerable)); // 返回可枚举对象
    return implementor.result(physType, list.toBlock()); // 返回SparkRel.Result对象，包含物理类型和生成的代码块
  } // implementSpark方法结束

  // generateSql方法：生成SQL语句，将关系表达式转换为特定方言的SQL字符串
  // 参数说明：
  //   dialect - SQL方言对象，定义目标数据库的SQL语法
  // 返回值：返回生成的SQL字符串
  private String generateSql(SqlDialect dialect) { // 私有方法，生成SQL语句
    final JdbcImplementor jdbcImplementor = // 创建JDBC实现器，用于将关系表达式转换为SQL
        new JdbcImplementor(dialect, // 使用传入的SQL方言
            (JavaTypeFactory) getCluster().getTypeFactory()); // 使用集群的类型工厂并转换为Java类型工厂
    final JdbcImplementor.Result result = // 调用实现器访问根节点，生成SQL结果
        jdbcImplementor.visitRoot(this.getInput()); // 访问输入关系表达式并生成SQL
    return result.asStatement().toSqlString(dialect).getSql(); // 将结果转换为SQL语句并返回SQL字符串
  } // generateSql方法结束
} // JdbcToSparkConverter类结束