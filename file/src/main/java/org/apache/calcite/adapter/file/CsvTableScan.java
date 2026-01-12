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
package org.apache.calcite.adapter.file; // 定义包名，表示该类属于 file 适配器包

import org.apache.calcite.adapter.enumerable.EnumerableConvention; // 导入 EnumerableConvention，用于定义可枚举的约定（物理执行约定）
import org.apache.calcite.adapter.enumerable.EnumerableRel; // 导入 EnumerableRel 接口，表示可枚举的关系表达式，支持生成 LINQ4J 代码
import org.apache.calcite.adapter.enumerable.EnumerableRelImplementor; // 导入 EnumerableRelImplementor，用于实现可枚举关系表达式的执行器
import org.apache.calcite.adapter.enumerable.PhysType; // 导入 PhysType 接口，表示物理类型，用于描述 Java 类型的物理表示
import org.apache.calcite.adapter.enumerable.PhysTypeImpl; // 导入 PhysTypeImpl，PhysType 的实现类
import org.apache.calcite.linq4j.tree.Blocks; // 导入 Blocks，用于构建代码块表达式
import org.apache.calcite.linq4j.tree.Expression; // 导入 Expression，表示 LINQ4J 表达式树中的表达式节点
import org.apache.calcite.linq4j.tree.Expressions; // 导入 Expressions，用于创建各种表达式节点
import org.apache.calcite.linq4j.tree.Primitive; // 导入 Primitive，用于处理基本类型
import org.apache.calcite.plan.RelOptCluster; // 导入 RelOptCluster，关系表达式集群，包含查询优化相关的共享信息
import org.apache.calcite.plan.RelOptCost; // 导入 RelOptCost，表示关系操作的成本
import org.apache.calcite.plan.RelOptPlanner; // 导入 RelOptPlanner，关系优化规划器接口
import org.apache.calcite.plan.RelOptTable; // 导入 RelOptTable，表示优化器中的表
import org.apache.calcite.plan.RelTraitSet; // 导入 RelTraitSet，关系特征集合，用于定义关系表达式的物理属性
import org.apache.calcite.rel.RelNode; // 导入 RelNode，关系表达式节点的基类
import org.apache.calcite.rel.RelWriter; // 导入 RelWriter，用于将关系表达式转换为可读的字符串表示
import org.apache.calcite.rel.core.TableScan; // 导入 TableScan，表扫描的基类，表示从表中读取数据的操作
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入 RelMetadataQuery，用于查询关系表达式的元数据
import org.apache.calcite.rel.type.RelDataType; // 导入 RelDataType，表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入 RelDataTypeFactory，用于创建关系数据类型的工厂
import org.apache.calcite.rel.type.RelDataTypeField; // 导入 RelDataTypeField，表示关系数据类型中的字段

import com.google.common.collect.ImmutableList; // 导入 Google Guava 的 ImmutableList，用于创建不可变列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 Nullable 注解，用于标记可能为 null 的返回值

import java.util.List; // 导入 Java 的 List 接口

import static java.util.Objects.requireNonNull; // 导入 Objects.requireNonNull 静态方法，用于参数非空检查

/**
 * Relational expression representing a scan of a CSV file. // 表示扫描 CSV 文件的关系表达式
 *
 * <p>Like any table scan, it serves as a leaf node of a query tree. // 像任何表扫描一样，它作为查询树的叶子节点
 */ // 类注释结束
public class CsvTableScan extends TableScan implements EnumerableRel { // CsvTableScan 类，继承自 TableScan 并实现 EnumerableRel 接口，表示 CSV 表的扫描操作
  final CsvTranslatableTable csvTable; // 成员变量：csvTable，表示被扫描的 CSV 表对象，final 修饰表示不可变，CsvTranslatableTable 是可翻译的表接口
  private final int[] fields; // 成员变量：fields，表示扫描的字段索引数组，private final 表示私有且不可变，int[] 存储需要扫描的字段在表中的索引位置

  protected CsvTableScan(RelOptCluster cluster, RelOptTable table, // 构造方法：protected 访问权限，接受集群对象、表对象、CSV 表对象和字段数组作为参数
      CsvTranslatableTable csvTable, int[] fields) { // 参数：cluster - 关系表达式集群，包含共享的查询优化信息；table - 优化器中的表对象；csvTable - CSV 表的具体实现；fields - 需要扫描的字段索引数组
    super(cluster, cluster.traitSetOf(EnumerableConvention.INSTANCE), ImmutableList.of(), table); // 调用父类 TableScan 的构造方法，传入集群、特征集（使用 EnumerableConvention 约定）、空的输入列表和表对象
    this.csvTable = requireNonNull(csvTable, "csvTable"); // 初始化 csvTable 成员变量，使用 requireNonNull 确保 csvTable 不为 null，否则抛出 NullPointerException
    this.fields = fields; // 初始化 fields 成员变量，保存需要扫描的字段索引数组
  } // 构造方法结束

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写 copy 方法，用于复制当前关系表达式，接受新的特征集和输入列表
    assert inputs.isEmpty(); // 断言输入列表为空，因为 TableScan 是叶子节点，不应该有输入
    return new CsvTableScan(getCluster(), table, csvTable, fields); // 返回一个新的 CsvTableScan 对象，使用相同的集群、表、CSV 表和字段数组
  } // copy 方法结束

  @Override public RelWriter explainTerms(RelWriter pw) { // 重写 explainTerms 方法，用于将当前节点转换为可读的字符串表示，接受 RelWriter 对象作为参数
    return super.explainTerms(pw) // 调用父类的 explainTerms 方法获取基础的 RelWriter
        .item("fields", Primitive.asList(fields)); // 添加 "fields" 项到输出中，将 fields 数组转换为列表形式显示
  } // explainTerms 方法结束

  @Override public RelDataType deriveRowType() { // 重写 deriveRowType 方法，用于推导当前扫描操作的行类型（即输出行的数据类型）
    final List<RelDataTypeField> fieldList = table.getRowType().getFieldList(); // 获取表的行类型中的所有字段列表
    final RelDataTypeFactory.Builder builder = // 创建一个关系数据类型工厂的构建器，用于构建新的行类型
        getCluster().getTypeFactory().builder(); // 通过集群获取类型工厂，然后创建构建器
    for (int field : fields) { // 遍历 fields 数组中的每个字段索引
      builder.add(fieldList.get(field)); // 将对应索引的字段添加到构建器中，只包含需要扫描的字段
    } // for 循环结束
    return builder.build(); // 构建并返回新的行类型，只包含指定的字段
  } // deriveRowType 方法结束

  @Override public void register(RelOptPlanner planner) { // 重写 register 方法，用于向优化器注册相关的规则，接受 RelOptPlanner 对象作为参数
    planner.addRule(FileRules.PROJECT_SCAN); // 向优化器添加 PROJECT_SCAN 规则，该规则用于优化 Project 和 Scan 的组合
  } // register 方法结束

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写 computeSelfCost 方法，用于计算当前扫描操作的成本，接受优化器和元数据查询对象作为参数，返回值可能为 null
      RelMetadataQuery mq) { // 参数：planner - 关系优化规划器；mq - 关系元数据查询对象
    // Multiply the cost by a factor that makes a scan more attractive if it // 将成本乘以一个因子，如果扫描的字段数明显少于原始扫描，则使该扫描更具吸引力
    // has significantly fewer fields than the original scan. // 这个因子基于字段数量的比例
    //
    // The "+ 2D" on top and bottom keeps the function fairly smooth. // 分子和分母上的 "+ 2D" 使函数保持相对平滑，避免极端值
    //
    // For example, if table has 3 fields, project has 1 field, // 例如，如果表有 3 个字段，项目有 1 个字段
    // then factor = (1 + 2) / (3 + 2) = 0.6 // 那么因子 = (1 + 2) / (3 + 2) = 0.6
    final RelOptCost cost = requireNonNull(super.computeSelfCost(planner, mq)); // 调用父类的 computeSelfCost 方法计算基础成本，并确保结果不为 null
    return cost // 返回调整后的成本
        .multiplyBy(((double) fields.length + 2D) // 将成本乘以因子：(扫描的字段数 + 2) / (表的总字段数 + 2)
            / ((double) table.getRowType().getFieldCount() + 2D)); // 这样字段数越少，成本因子越小，使优化器更倾向于选择字段较少的扫描
  } // computeSelfCost 方法结束

  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) { // 重写 implement 方法，用于生成可执行的代码，接受实现器和偏好设置作为参数，返回 Result 对象
    PhysType physType = // 创建物理类型对象，用于描述 Java 类型的物理表示
        PhysTypeImpl.of( // 使用 PhysTypeImpl 创建物理类型
            implementor.getTypeFactory(), // 从实现器获取类型工厂
            getRowType(), // 获取当前节点的行类型
            pref.preferArray()); // 根据偏好设置决定是否优先使用数组

    final Expression expression = // 获取表的表达式，用于生成代码
        requireNonNull(table.getExpression(CsvTranslatableTable.class)); // 通过表对象获取 CsvTranslatableTable 类型的表达式，确保不为 null
    return implementor.result( // 返回实现结果，包含物理类型和生成的代码块
        physType, // 传入物理类型
        Blocks.toBlock( // 将表达式转换为代码块
            Expressions.call(expression, // 创建方法调用表达式，调用 expression 对象的方法
                "project", implementor.getRootExpression(), // 方法名为 "project"，传入根表达式
                Expressions.constant(fields)))); // 传入常量表达式，包含 fields 数组
  } // implement 方法结束
} // CsvTableScan 类结束
