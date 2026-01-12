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
package org.apache.calcite.adapter.spark; // 定义包名，该类位于org.apache.calcite.adapter.spark包下

import org.apache.calcite.adapter.jdbc.JdbcConvention; // 导入JdbcConvention类，用于表示JDBC适配器的约定
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，用于表示关系表达式的特征集合
import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示关系表达式树的节点
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule类，作为转换规则的基类
import org.apache.calcite.tools.RelBuilderFactory; // 导入RelBuilderFactory类，用于构建关系表达式

/**
 * Rule to convert a relational expression from
 * {@link org.apache.calcite.adapter.jdbc.JdbcConvention} to
 * {@link org.apache.calcite.adapter.spark.SparkRel#CONVENTION Spark convention}.
 */
// 类文档注释：该类是一个转换规则，用于将关系表达式从JDBC约定转换为Spark约定
// 它继承自ConverterRule，是Calcite优化器规则体系的一部分
// 在查询优化过程中，当需要将JDBC数据源的操作转换为Spark执行引擎的操作时，会使用此规则
public class JdbcToSparkConverterRule extends ConverterRule { // 定义JdbcToSparkConverterRule类，继承自ConverterRule
  /** Creates a JdbcToSparkConverterRule. */
  // 静态工厂方法注释：创建一个JdbcToSparkConverterRule实例
  // 参数out表示源约定，即JdbcConvention，表示要从JDBC约定转换
  // 返回配置好的JdbcToSparkConverterRule实例
  public static JdbcToSparkConverterRule create(JdbcConvention out) { // 定义静态工厂方法create，用于创建规则实例
    return Config.INSTANCE // 获取ConverterRule的默认配置实例
        .withConversion(RelNode.class, out, SparkRel.CONVENTION, // 配置转换规则：将任意RelNode从JdbcConvention转换为SparkRel.CONVENTION
            "JdbcToSparkConverterRule") // 设置规则的名称为"JdbcToSparkConverterRule"
        .withRuleFactory(JdbcToSparkConverterRule::new) // 设置规则工厂，使用构造器引用JdbcToSparkConverterRule::new
        .toRule(JdbcToSparkConverterRule.class); // 将配置转换为JdbcToSparkConverterRule规则实例并返回
  } // 静态工厂方法结束

  @Deprecated // to be removed before 2.0 // 标记为已过时，将在2.0版本之前移除
  // 过时的构造方法注释：已废弃的构造方法，保留用于向后兼容
  // 参数out表示JDBC约定
  // 参数relBuilderFactory表示关系表达式构建工厂
  // 建议使用create()静态工厂方法代替
  public JdbcToSparkConverterRule(JdbcConvention out, // 定义已过时的构造方法
      RelBuilderFactory relBuilderFactory) { // 接收RelBuilderFactory参数
    this(create(out).config.withRelBuilderFactory(relBuilderFactory) // 调用create方法获取实例，然后设置RelBuilderFactory
        .as(Config.class)); // 将配置转换为Config类型，并调用受保护的构造方法
  } // 已过时的构造方法结束

  /** Called from the Config. */
  // 受保护的构造方法注释：从Config调用的构造方法
  // 参数config表示转换规则的配置对象
  // 这是实际使用的构造方法，由静态工厂方法或已过时的构造方法调用
  protected JdbcToSparkConverterRule(Config config) { // 定义受保护的构造方法
    super(config); // 调用父类ConverterRule的构造方法，传入配置对象
  } // 受保护的构造方法结束

  @Override public RelNode convert(RelNode rel) { // 重写convert方法，用于执行实际的转换操作
    RelTraitSet newTraitSet = rel.getTraitSet().replace(getOutTrait()); // 获取输入关系表达式的特征集，并将输出约定（Spark约定）替换进去，生成新的特征集
    return new JdbcToSparkConverter(rel.getCluster(), newTraitSet, rel); // 创建并返回JdbcToSparkConverter实例，传入集群、新特征集和原始关系表达式
  } // convert方法结束，完成从JDBC约定到Spark约定的转换
} // JdbcToSparkConverterRule类结束
