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
package org.apache.calcite.materialize; // 包声明：Apache Calcite物化视图相关类

import org.apache.calcite.plan.RelOptTable; // 导入关系优化表接口，表示一个表对象
import org.apache.calcite.util.graph.AttributedDirectedGraph; // 导入属性有向图接口，用于构建Lattice图结构
import org.apache.calcite.util.graph.DefaultEdge; // 导入默认边类，Step继承此类作为图的边
import org.apache.calcite.util.mapping.IntPair; // 导入整数对类，用于表示源表和目标表之间的列映射关系

import com.google.common.collect.ImmutableList; // 导入不可变列表类，用于存储键值对
import com.google.common.collect.Ordering; // 导入排序工具类，用于比较和排序

import org.checkerframework.checker.initialization.qual.NotOnlyInitialized; // 导入初始化检查注解，表示对象可能未完全初始化
import org.checkerframework.checker.initialization.qual.UnderInitialization; // 导入初始化检查注解，表示对象正在初始化中
import org.checkerframework.checker.nullness.qual.Nullable; // 导入空值检查注解，表示参数可能为null

import java.util.List; // 导入列表接口
import java.util.Objects; // 导入对象工具类，用于equals和hashCode方法

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于参数非空检查

/** Edge in the join graph. // 连接图中的边（Step类表示Lattice图中两个表之间的连接关系）
 *
 * <p>It is directed: the "parent" must be the "many" side containing the
 * foreign key, and the "target" is the "one" side containing the primary
 * key. For example, EMP &rarr; DEPT. // 它是有向的："parent"（父节点）必须是包含外键的"多"端，"target"（目标节点）是包含主键的"一"端。例如：EMP表（员工表）→ DEPT表（部门表），表示员工属于某个部门
 *
 * <p>When created via
 * {@link LatticeSpace#addEdge(LatticeTable, LatticeTable, List)}
 * it is unique within the {@link LatticeSpace}. */ // 当通过LatticeSpace.addEdge方法创建时，它在LatticeSpace中是唯一的
class Step extends DefaultEdge { // Step类继承DefaultEdge，表示Lattice图中的有向边
  final List<IntPair> keys; // 成员变量：键值对列表，存储源表和目标表之间的列映射关系（外键-主键对应关系），每个IntPair包含source列索引和target列索引

  /** String representation of {@link #keys}. Computing the string requires a
   * {@link LatticeSpace}, so we pre-compute it before construction. */ // 成员变量：keys的字符串表示形式。计算这个字符串需要LatticeSpace对象，所以我们在构造之前预先计算它以提高性能
  final String keyString; // 成员变量：预计算的键字符串，格式为" 源字段名:目标字段名"，例如" dept_id:dept_id emp_id:emp_id"

  private Step(LatticeTable source, LatticeTable target, // 私有构造方法：创建Step实例，参数包括源表、目标表、键值对列表和键字符串
      List<IntPair> keys, String keyString) { // 参数：keys是外键-主键映射列表，keyString是预计算的键字符串表示
    super(source, target); // 调用父类DefaultEdge的构造方法，设置边的源节点和目标节点
    this.keys = ImmutableList.copyOf(keys); // 将传入的键值对列表复制为不可变列表，确保外部修改不影响Step对象
    this.keyString = requireNonNull(keyString, "keyString"); // 检查keyString不为null，如果为null则抛出NullPointerException
    assert IntPair.ORDERING.isStrictlyOrdered(keys); // 断言：确保键值对列表是严格有序且唯一的（即没有重复的键值对）
  }

  /** Creates a Step. */ // 静态工厂方法注释：创建一个Step实例
  static Step create(LatticeTable source, LatticeTable target, // 静态工厂方法：创建Step对象，参数包括源表、目标表、键值对列表和LatticeSpace对象
      List<IntPair> keys, LatticeSpace space) { // 参数：space是LatticeSpace对象，用于获取字段名称以构建keyString
    final StringBuilder b = new StringBuilder(); // 创建StringBuilder对象，用于构建键字符串
    for (IntPair key : keys) { // 遍历所有键值对
      b.append(' ') // 在字符串前添加空格作为分隔符
          .append(space.fieldName(source, key.source)) // 获取源表中对应列的字段名并追加到字符串
          .append(':') // 添加冒号作为源字段和目标字段的分隔符
          .append(space.fieldName(target, key.target)); // 获取目标表中对应列的字段名并追加到字符串
    }
    return new Step(source, target, keys, b.toString()); // 调用私有构造方法创建并返回Step对象
  }

  @Override public int hashCode() { // 重写hashCode方法：用于支持Step对象在哈希集合中的使用
    return Objects.hash(source, target, keys); // 使用source、target和keys三个属性计算哈希值，确保相等的Step对象有相同的哈希值
  }

  @Override public boolean equals(@Nullable Object obj) { // 重写equals方法：用于比较两个Step对象是否相等，@Nullable表示参数可能为null
    return this == obj // 首先检查是否是同一个对象引用（最快路径）
        || obj instanceof Step // 如果不是同一个引用，检查obj是否是Step类的实例
        && ((Step) obj).source.equals(source) // 检查source表是否相等
        && ((Step) obj).target.equals(target) // 检查target表是否相等
        && ((Step) obj).keys.equals(keys); // 检查keys键值对列表是否相等
  }

  @Override public String toString() { // 重写toString方法：返回Step对象的字符串表示形式，用于调试和日志输出
    return "Step(" + source + ", " + target + "," + keyString + ")"; // 返回格式为"Step(源表, 目标表, 键字符串)"的字符串
  }

  LatticeTable source() { // 方法：获取Step的源表（LatticeTable对象）
    return (LatticeTable) source; // 返回父类DefaultEdge的source属性，并强制转换为LatticeTable类型
  }

  LatticeTable target() { // 方法：获取Step的目标表（LatticeTable对象）
    return (LatticeTable) target; // 返回父类DefaultEdge的target属性，并强制转换为LatticeTable类型
  }

  boolean isBackwards(SqlStatisticProvider statisticProvider) { // 方法：判断Step的方向是否是"反向"的，即是否符合外键关系（多对一），参数是SQL统计信息提供者
    final RelOptTable sourceTable = source().t; // 获取源表对应的RelOptTable对象（关系优化表）
    final List<Integer> sourceColumns = IntPair.left(keys); // 提取keys中所有源表的列索引（外键列）
    final RelOptTable targetTable = target().t; // 获取目标表对应的RelOptTable对象
    final List<Integer> targetColumns = IntPair.right(keys); // 提取keys中所有目标表的列索引（主键列）
    final boolean noDerivedSourceColumns = // 检查源表列是否都是原始列（非派生列）
        sourceColumns.stream().allMatch(i -> // 流式处理：所有源表列索引
            i < sourceTable.getRowType().getFieldCount()); // 索引小于源表字段总数，说明是原始列而非派生列
    final boolean noDerivedTargetColumns = // 检查目标表列是否都是原始列（非派生列）
        targetColumns.stream().allMatch(i -> // 流式处理：所有目标表列索引
            i < targetTable.getRowType().getFieldCount()); // 索引小于目标表字段总数，说明是原始列而非派生列
    final boolean forwardForeignKey = noDerivedSourceColumns // 判断正向外键关系：源表→目标表是否是有效的外键关系
        && noDerivedTargetColumns // 且两端都是原始列
        && statisticProvider.isForeignKey(sourceTable, sourceColumns, // 检查源表列是否是目标表的外键
            targetTable, targetColumns) // 检查目标表列是否是主键
        && statisticProvider.isKey(targetTable, targetColumns); // 确保目标表列是唯一键
    final boolean backwardForeignKey = noDerivedSourceColumns // 判断反向外键关系：目标表→源表是否是有效的外键关系
        && noDerivedTargetColumns // 且两端都是原始列
        && statisticProvider.isForeignKey(targetTable, targetColumns, // 检查目标表列是否是源表的外键
            sourceTable, sourceColumns) // 检查源表列是否是主键
        && statisticProvider.isKey(sourceTable, sourceColumns); // 确保源表列是唯一键
    if (backwardForeignKey != forwardForeignKey) { // 如果正向和反向外键关系判断结果不一致（只有一个是true）
      return backwardForeignKey; // 返回backwardForeignKey的值，即如果反向是外键关系则返回true（表示Step是反向的）
    }
    // Tie-break if it's a foreign key in neither or both directions // 如果两个方向都不是外键关系，或者两个方向都是外键关系，则使用比较规则来决定
    return compare(sourceTable, sourceColumns, targetTable, targetColumns) < 0; // 调用compare方法比较两个表和列，返回true表示Step是反向的
  }

  /** Arbitrarily compares (table, columns). */ // 私有静态方法注释：任意比较两个（表，列）组合，用于在无法通过外键关系确定方向时的决胜规则
  private static int compare(RelOptTable table1, List<Integer> columns1, // 私有静态方法：比较两个表和列的组合，返回负数表示第一个小于第二个
      RelOptTable table2, List<Integer> columns2) { // 参数：table1/columns1是第一个表和列，table2/columns2是第二个表和列
    int c = Ordering.natural().<String>lexicographical() // 使用自然排序按字典序比较两个表的限定名（如["schema", "table"]）
        .compare(table1.getQualifiedName(), table2.getQualifiedName()); // 获取表的完全限定名并比较
    if (c == 0) { // 如果两个表的限定名相同（同一个表）
      c = Ordering.natural().<Integer>lexicographical() // 则按字典序比较两个列索引列表
          .compare(columns1, columns2); // 比较列索引列表，确保比较结果的一致性
    }
    return c; // 返回比较结果：负数表示table1小于table2，0表示相等，正数表示table1大于table2
  }

  /** Temporary method. We should use (inferred) primary keys to figure out
   * the direction of steps. */ // 临时方法注释：这是一个临时方法，未来应该使用推断的主键来确定Step的方向
  @SuppressWarnings("unused") // 抑制未使用警告：虽然当前未使用，但保留用于未来可能的功能
  private static double cardinality(SqlStatisticProvider statisticProvider, // 私有静态方法：获取表的基数（行数估计），用于确定表的大小
      LatticeTable table) { // 参数：table是要计算基数的LatticeTable对象
    return statisticProvider.tableCardinality(table.t); // 调用统计信息提供者获取表的行数估计值
  }

  /** Creates {@link Step} instances. */ // 内部类注释：Factory类用于创建Step实例，实现了AttributedEdgeFactory接口
  static class Factory implements AttributedDirectedGraph.AttributedEdgeFactory< // 静态内部类：Step的工厂类，用于在有向图中创建带属性的边
      LatticeTable, Step> { // 泛型参数：节点类型是LatticeTable，边类型是Step
    private final @NotOnlyInitialized LatticeSpace space; // 成员变量：LatticeSpace对象引用，@NotOnlyInitialized注解表示该对象可能未完全初始化（因为Factory在LatticeSpace构造时创建）

    @SuppressWarnings("type.argument.type.incompatible") // 抑制类型参数不兼容警告：由于初始化顺序的特殊性，需要此注解
    Factory(@UnderInitialization LatticeSpace space) { // 构造方法：创建Factory实例，参数是正在初始化的LatticeSpace对象
      this.space = requireNonNull(space, "space"); // 检查space不为null并赋值给成员变量
    }

    @Override public Step createEdge(LatticeTable source, LatticeTable target) { // 重写方法：创建边（不使用属性），此方法不支持
      throw new UnsupportedOperationException(); // 抛出不支持操作异常，因为Step必须通过键值对创建
    }

    @Override public Step createEdge(LatticeTable source, LatticeTable target, // 重写方法：创建带属性的边，使用属性数组中的键值对
        Object... attributes) { // 可变参数：属性数组，第一个元素应该是List<IntPair>类型的键值对列表
      @SuppressWarnings("unchecked") final List<IntPair> keys = // 抑制未检查转换警告：将属性数组的第一个元素转换为List<IntPair>类型
          (List) attributes[0]; // 从属性数组中提取键值对列表
      return Step.create(source, target, keys, space); // 调用Step.create静态方法创建并返回Step对象
    }
  }
}
