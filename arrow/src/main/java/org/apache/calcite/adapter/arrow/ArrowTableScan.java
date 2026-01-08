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
package org.apache.calcite.adapter.arrow; // 声明包名：Apache Calcite Arrow适配器包，包含Arrow数据源相关的适配器实现

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster：表示关系表达式集群，包含类型工厂、表达式工厂等共享资源
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner：查询优化器的抽象接口，负责注册和执行优化规则
import org.apache.calcite.plan.RelOptRule; // 导入RelOptRule：优化规则的抽象基类，定义了如何转换关系表达式
import org.apache.calcite.plan.RelOptTable; // 导入RelOptTable：优化过程中对表的抽象表示，包含表的元数据信息
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet：关系表达式特征的集合，如约定、排序等属性
import org.apache.calcite.rel.RelNode; // 导入RelNode：关系表达式的接口，所有关系操作符都实现此接口
import org.apache.calcite.rel.RelWriter; // 导入RelWriter：用于将关系表达式输出为可读格式的工具
import org.apache.calcite.rel.core.TableScan; // 导入TableScan：表扫描操作符的基类，表示从表中读取数据
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType：关系数据类型的抽象表示
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory：用于创建关系数据类型的工厂
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField：表示关系类型中的字段，包含字段名和类型
import org.apache.calcite.util.ImmutableIntList; // 导入ImmutableIntList：不可变的整数列表，用于存储字段索引

import com.google.common.collect.ImmutableList; // 导入ImmutableList：Google Guava提供的不可变列表实现

import java.util.List; // 导入List：Java标准库的列表接口

import static com.google.common.base.Preconditions.checkArgument; // 导入静态方法checkArgument：用于参数校验，如果条件不满足则抛出IllegalArgumentException

/**
 * Relational expression representing a scan of an Arrow collection.
 * 表示扫描Arrow集合的关系表达式类，继承自TableScan并实现ArrowRel接口
 * Arrow是Apache的列式内存格式，此类负责从Arrow数据源中读取数据
 */
class ArrowTableScan extends TableScan implements ArrowRel { // 定义ArrowTableScan类，继承TableScan（表扫描基类）并实现ArrowRel接口（Arrow关系表达式接口）
  private final ArrowTable arrowTable; // 成员变量：ArrowTable实例，表示被扫描的Arrow表，包含Arrow数据的元数据和访问方法，final表示不可变
  private final ImmutableIntList fields; // 成员变量：不可变整数列表，存储要扫描的字段索引（原始表的字段索引），用于实现字段裁剪优化，final表示不可变

  // 构造方法：创建ArrowTableScan实例
  ArrowTableScan(RelOptCluster cluster, RelTraitSet traitSet, // 参数1：RelOptCluster，包含优化集群的共享资源（类型工厂、表达式工厂等）；参数2：RelTraitSet，关系表达式的特征集合（约定、排序等）
      RelOptTable relOptTable, ArrowTable arrowTable, ImmutableIntList fields) { // 参数3：RelOptTable，优化过程中的表抽象表示，包含表的元数据；参数4：ArrowTable，实际的Arrow表对象，包含Arrow数据访问逻辑；参数5：ImmutableIntList，要扫描的字段索引列表，用于字段裁剪
    super(cluster, traitSet, ImmutableList.of(), relOptTable); // 调用父类TableScan的构造方法，传入集群、特征集、空的输入列表（表扫描没有子节点）和表对象
    this.arrowTable = arrowTable; // 将传入的ArrowTable对象赋值给成员变量，保存对Arrow表的引用，用于后续数据访问
    this.fields = fields; // 将传入的字段索引列表赋值给成员变量，保存要扫描的字段，用于实现字段裁剪优化

    assert getConvention() == ArrowRel.CONVENTION; // 断言检查：确保当前关系表达式的约定是ArrowRel.CONVENTION，即使用Arrow适配器的约定，如果不是则抛出断言错误
  } // 构造方法结束

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法：创建当前关系表达式的副本，用于优化器进行转换时创建新的关系表达式
    checkArgument(inputs.isEmpty()); // 参数校验：检查输入列表是否为空，因为TableScan是叶子节点，不应该有子节点，如果不为空则抛出异常
    return this; // 返回当前对象本身，因为ArrowTableScan是不可变的，不需要创建新的副本，直接返回this即可
  } // copy方法结束

  @Override public RelWriter explainTerms(RelWriter pw) { // 重写explainTerms方法：将关系表达式转换为可读的解释信息，用于查询计划的可视化输出
    return super.explainTerms(pw).item("fields", fields); // 调用父类的explainTerms方法添加基础信息，然后添加"fields"项及其值（字段索引列表），这样在查询计划中可以看到扫描了哪些字段
  } // explainTerms方法结束

  @Override public RelDataType deriveRowType() { // 重写deriveRowType方法：推导当前关系表达式的行类型（输出行的结构），根据扫描的字段索引构建新的行类型
    final List<RelDataTypeField> fieldList = table.getRowType().getFieldList(); // 获取原始表的行类型中的所有字段列表，table是从父类继承的RelOptTable对象
    final RelDataTypeFactory.Builder builder = // 创建RelDataType工厂的构建器，用于构建新的行类型
        getCluster().getTypeFactory().builder(); // 从集群中获取类型工厂，然后创建构建器，类型工厂用于创建各种关系数据类型
    for (int field : fields) { // 遍历fields列表中的每个字段索引（整数）
      builder.add(fieldList.get(field)); // 从原始字段列表中获取对应索引的字段，并添加到构建器中，这样构建出的新行类型只包含被扫描的字段
    } // for循环结束
    return builder.build(); // 调用构建器的build方法，构建并返回新的RelDataType对象，这个类型就是当前ArrowTableScan的输出行类型
  } // deriveRowType方法结束

  @Override public void register(RelOptPlanner planner) { // 重写register方法：向优化器注册相关的优化规则，这些规则可以将Arrow关系表达式转换为其他形式
    planner.addRule(ArrowRules.TO_ENUMERABLE); // 向优化器添加TO_ENUMERABLE规则，这个规则用于将Arrow关系表达式转换为Enumerable关系表达式（可枚举的Java集合）
    for (RelOptRule rule : ArrowRules.RULES) { // 遍历ArrowRules.RULES集合中的所有优化规则
      planner.addRule(rule); // 将每个规则添加到优化器中，这些规则用于优化Arrow相关的查询计划，如投影下推、过滤下推等
    } // for循环结束
  } // register方法结束

  @Override public void implement(ArrowRel.Implementor implementor) { // 重写implement方法：实现Arrow关系表达式，将逻辑计划转换为物理执行计划
    implementor.arrowTable = arrowTable; // 将ArrowTable对象设置到实现器中，实现器可以使用这个对象访问Arrow数据
    implementor.table = table; // 将RelOptTable对象设置到实现器中，实现器可以使用这个对象获取表的元数据信息
  } // implement方法结束
} // ArrowTableScan类结束
