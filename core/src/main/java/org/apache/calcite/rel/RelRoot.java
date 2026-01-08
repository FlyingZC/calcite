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
package org.apache.calcite.rel; // RelRoot类所在的包，包含关系表达式(RelNode)相关的核心类

import org.apache.calcite.rel.hint.RelHint; // 导入关系表达式提示类，用于存储查询提示信息
import org.apache.calcite.rel.logical.LogicalProject; // 导入逻辑投影操作节点，用于字段投影和重命名
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型，描述关系表达式的行类型
import org.apache.calcite.rex.RexBuilder; // 导入Rex表达式构建器，用于构建行表达式
import org.apache.calcite.rex.RexNode; // 导入行表达式节点，表示关系表达式中的表达式
import org.apache.calcite.runtime.ImmutablePairList; // 导入不可变的键值对列表，用于存储字段映射
import org.apache.calcite.runtime.PairList; // 导入可变的键值对列表，用于构建字段映射
import org.apache.calcite.sql.SqlKind; // 导入SQL语句类型枚举，如SELECT、UPDATE、DELETE等
import org.apache.calcite.util.ImmutableIntList; // 导入不可变的整数列表，用于字段索引
import org.apache.calcite.util.Pair; // 导入键值对工具类
import org.apache.calcite.util.mapping.Mappings; // 导入映射工具类，用于字段映射判断

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表
import com.google.common.collect.ImmutableSet; // 导入Google Guava的不可变集合

import java.util.ArrayList; // 导入Java标准ArrayList，用于存储投影表达式
import java.util.List; // 导入Java标准List接口
import java.util.Map; // 导入Java标准Map接口

import static java.util.Objects.requireNonNull; // 导入对象非空检查工具方法

/**
 * 关系表达式树(RelNode)的根节点包装类
 *
 * <p>RelRoot存在的一个重要原因是处理类似这样的查询：
 *
 * <blockquote><code>SELECT name
 * FROM emp
 * ORDER BY empno DESC</code></blockquote>
 *
 * <p>Calcite知道结果必须按empno排序，但无法将排序顺序表示为排序规则(collation)，
 * 因为{@code empno}不是结果集中的字段。
 *
 * <p>相反，我们将其表示为：
 *
 * <blockquote><code>RelRoot: {
 *   rel: Sort($1 DESC)          // 按第1个字段(empno)降序排序
 *          Project(name, empno) // 投影出name和empno两个字段
 *            TableScan(EMP)     // 扫描EMP表
 *   fields: [0]                // 最终只输出第0个字段(name)
 *   collation: [1 DESC]        // 排序规则是按第1个字段降序
 * }</code></blockquote>
 *
 * <p>注意：{@code empno}字段存在于投影结果中，但{@code fields}掩码告诉消费者将其丢弃。
 *
 * <p>另一个使用场景是这样的查询：
 *
 * <blockquote><code>SELECT name AS n, name AS n2, empno AS n
 * FROM emp</code></blockquote>
 *
 * <p>这里{@code name}字段被多次使用，并且有多列别名为{@code n}。可以这样表示：
 *
 * <blockquote><code>RelRoot: {
 *   rel: Project(name, empno)  // 投影出name和empno
 *          TableScan(EMP)       // 扫描EMP表
 *   fields: [(0, "n"), (0, "n2"), (1, "n")] // 字段映射：第0列别名为n，第0列别名为n2，第1列别名为n
 *   collation: []              // 无排序要求
 * }</code></blockquote>
 *
 * <p>RelRoot的核心作用是：
 * 1. 封装关系表达式树的根节点
 * 2. 管理输出字段的映射关系(字段索引到字段名的映射)
 * 3. 保存排序规则(collation)，即使排序字段不在最终输出中
 * 4. 保存查询提示(hints)
 * 5. 记录SQL语句类型(如SELECT、UPDATE等)
 * 6. 保存验证后的行类型(validatedRowType)
 */
public class RelRoot { // 定义RelRoot类，作为关系表达式树的根节点包装器
  public final RelNode rel; // 关系表达式树的根节点，表示整个查询计划的关系代数树
  public final RelDataType validatedRowType; // 验证后的行类型，由SQL验证器返回的原始行类型，包含所有字段的类型信息
  public final SqlKind kind; // SQL语句类型，如SELECT、UPDATE、DELETE、INSERT等，用于标识查询的DML操作类型
  public final ImmutablePairList<Integer, String> fields; // 字段映射列表，键是输入字段的索引(从0开始)，值是输出字段的名称，用于处理字段重命名和重复使用
  public final RelCollation collation; // 排序规则，定义结果集的排序顺序，即使排序字段不在最终输出中也保留此信息
  public final ImmutableList<RelHint> hints; // 查询提示列表，包含用户提供的优化提示，用于指导查询优化器

  /**
   * 构造RelRoot对象，创建一个关系表达式树的根节点包装器
   *
   * @param rel 关系表达式树的根节点，表示整个查询计划
   * @param validatedRowType 由查询验证器返回的原始行类型，包含完整的字段类型信息
   * @param kind 查询类型，如SELECT、UPDATE、DELETE、INSERT等DML操作类型
   * @param fields 字段映射的可迭代对象，每个条目包含字段索引和字段名称，用于处理字段重命名
   * @param collation 排序规则，定义结果集的排序顺序，可以为空
   * @param hints 查询提示列表，包含优化提示信息
   */

  public RelRoot(RelNode rel, RelDataType validatedRowType, SqlKind kind, // 构造方法，接收所有必要参数创建RelRoot对象
      Iterable<? extends Map.Entry<Integer, String>> fields, // 字段映射，键是字段索引，值是字段名
      RelCollation collation, List<RelHint> hints) { // 排序规则和提示列表
    this.rel = rel; // 保存关系表达式根节点
    this.validatedRowType = validatedRowType; // 保存验证后的行类型
    this.kind = kind; // 保存SQL语句类型
    this.fields = ImmutablePairList.copyOf(fields); // 将字段映射转换为不可变的PairList
    this.collation = requireNonNull(collation, "collation"); // 保存排序规则，要求非空
    this.hints = ImmutableList.copyOf(hints); // 将提示列表转换为不可变列表
  }

  /** 创建一个简单的RelRoot，使用关系表达式自身的行类型 */
  public static RelRoot of(RelNode rel, SqlKind kind) { // 静态工厂方法，创建简单的RelRoot
    return of(rel, rel.getRowType(), kind); // 调用重载方法，使用rel的行类型
  }

  /** 创建一个简单的RelRoot，自动构建字段映射 */
  public static RelRoot of(RelNode rel, RelDataType rowType, SqlKind kind) { // 重载的静态工厂方法
    final PairList<Integer, String> fields = PairList.of(); // 创建空的字段映射列表
    Pair.forEach(ImmutableIntList.identity(rowType.getFieldCount()), // 遍历字段索引(0,1,2,...)
        rowType.getFieldNames(), // 遍历字段名称
        fields::add); // 将索引和名称的配对添加到fields列表
    return new RelRoot(rel, rowType, kind, fields, RelCollations.EMPTY, // 创建RelRoot，使用空排序规则和空提示
        ImmutableList.of()); // 使用空的不可变提示列表
  }

  @Override public String toString() { // 重写toString方法，返回RelRoot的字符串表示
    return "Root {kind: " + kind // 返回包含SQL类型的字符串
        + ", rel: " + rel // 包含关系表达式的字符串
        + ", rowType: " + validatedRowType // 包含行类型的字符串
        + ", fields: " + fields // 包含字段映射的字符串
        + ", collation: " + collation + "}"; // 包含排序规则的字符串
  }

  /** 创建RelRoot的副本，并指定新的关系表达式节点 */
  public RelRoot withRel(RelNode rel) { // withRel方法，用于替换rel字段
    if (rel == this.rel) { // 如果新的rel与当前rel相同
      return this; // 直接返回当前对象，避免不必要的复制
    }
    return new RelRoot(rel, validatedRowType, kind, fields, collation, hints); // 创建新的RelRoot对象，替换rel字段
  }

  /** 创建RelRoot的副本，并指定新的SQL语句类型 */
  public RelRoot withKind(SqlKind kind) { // withKind方法，用于替换kind字段
    if (kind == this.kind) { // 如果新的kind与当前kind相同
      return this; // 直接返回当前对象，避免不必要的复制
    }
    return new RelRoot(rel, validatedRowType, kind, fields, collation, hints); // 创建新的RelRoot对象，替换kind字段
  }

  public RelRoot withCollation(RelCollation collation) { // withCollation方法，用于替换collation字段
    return new RelRoot(rel, validatedRowType, kind, fields, collation, hints); // 创建新的RelRoot对象，替换collation字段
  }

  /** 创建RelRoot的副本，并指定新的查询提示列表 */
  public RelRoot withHints(List<RelHint> hints) { // withHints方法，用于替换hints字段
    return new RelRoot(rel, validatedRowType, kind, fields, collation, hints); // 创建新的RelRoot对象，替换hints字段
  }

  /** 返回根关系表达式，如果需要则创建LogicalProject来移除不需要的字段 */
  public RelNode project() { // project方法，返回投影后的关系表达式
    return project(false); // 调用重载方法，force参数为false，表示只在必要时创建Project
  }

  /** 返回根关系表达式作为LogicalProject，根据条件决定是否强制创建Project节点
   *
   * @param force 是否强制创建Project节点，即使所有字段都被使用 */
  public RelNode project(boolean force) { // 重载的project方法，接收force参数
    if (isRefTrivial() // 如果字段引用是平凡的(即字段顺序与rel一致)
        && (SqlKind.DML.contains(kind) // 或者是DML语句(如UPDATE/DELETE)
            || !force // 或者不强制创建Project
            || (rel instanceof LogicalProject && isNameTrivial()))) { // 或者rel已经是Project且名称平凡
      return rel; // 直接返回原始rel，不需要创建新的Project
    }
    final List<RexNode> projects = new ArrayList<>(fields.size()); // 创建投影表达式列表，大小与字段数相同
    final RexBuilder rexBuilder = rel.getCluster().getRexBuilder(); // 获取Rex表达式构建器
    fields.forEach((i, name) -> projects.add(rexBuilder.makeInputRef(rel, i))); // 遍历字段映射，为每个字段创建输入引用表达式
    return LogicalProject.create(rel, hints, projects, fields.rightList(), // 创建LogicalProject节点
        ImmutableSet.of()); // 使用空的不可变集合作为其他参数
  }

  /**
   * 判断RelRoot中定义的字段名称是否与嵌入的关系表达式(rel)的字段名称相同
   *
   * <p>正例(名称相同):
   *
   * <blockquote><code>RelRoot: {
   *   rel: Project(empno)
   *          TableScan(EMP)
   *   fields: [0 -&gt; empno]
   *   collation: []
   * }</code></blockquote>
   *
   * <p>反例(名称不同):
   *
   * <blockquote><code>RelRoot: {
   *   rel: Project(empno)
   *          TableScan(EMP)
   *   fields: [0 -&gt; empid]
   *   collation: []
   * }</code></blockquote>
   *
   * @return 如果字段名称与嵌入的关系表达式相同则返回true，否则返回false
   */
  public boolean isNameTrivial() { // isNameTrivial方法，判断字段名称是否平凡
    final RelDataType inputRowType = rel.getRowType(); // 获取rel的行类型
    return fields.rightList().equals(inputRowType.getFieldNames()); // 比较fields的字段名列表与rel的字段名列表
  }

  /**
   * 判断嵌入的关系表达式是否是DML关系，或者RelRoot中定义的字段顺序与嵌入的关系表达式字段顺序是否相同
   *
   * <p>正例(顺序相同):
   *
   * <blockquote><code>RelRoot: {
   *   rel: Project(name, empno)
   *          TableScan(EMP)
   *   fields: [0 -&gt; name, 1 -&gt; empno]
   *   collation: []
   * }</code></blockquote>
   *
   * <p>反例(顺序不同):
   *
   * <blockquote><code>RelRoot: {
   *   rel: Project(name, empno)
   *          TableScan(EMP)
   *   fields: [0 -&gt; empno, 1 -&gt; name]
   *   collation: []
   * }</code></blockquote>
   *
   * @return 如果嵌入的关系表达式是DML关系，或者RelRoot的字段顺序与嵌入关系表达式相同则返回true，否则返回false
   */
  public boolean isRefTrivial() { // isRefTrivial方法，判断字段引用是否平凡
    if (SqlKind.DML.contains(kind)) { // 如果是DML语句(UPDATE/DELETE/INSERT)
      // DML语句返回单个计数列，验证类型是SELECT的类型，但我们仍然认为映射是平凡的
      return true; // 直接返回true
    }
    final RelDataType inputRowType = rel.getRowType(); // 获取rel的行类型
    return Mappings.isIdentity(fields.leftList(), inputRowType.getFieldCount()); // 检查字段索引列表是否是恒等映射(0,1,2,...)
  }

  /**
   * 判断嵌入的关系表达式是否只有一个排序规则，并且该规则与RelRoot的排序规则匹配
   *
   * @return 如果嵌入的关系表达式只有一个排序规则且与RelRoot的排序规则匹配则返回true，否则返回false
   */
  public boolean isCollationTrivial() { // isCollationTrivial方法，判断排序规则是否平凡
    final List<RelCollation> collations = rel.getTraitSet() // 获取rel的特征集合
        .getTraits(RelCollationTraitDef.INSTANCE); // 获取排序规则特征
    return collations != null // 如果排序规则列表不为空
        && collations.size() == 1 // 并且只有一个排序规则
        && collations.get(0).equals(collation); // 并且该排序规则与RelRoot的排序规则相同
  }
}
