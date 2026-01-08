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
package org.apache.calcite.rel.metadata;

import org.apache.calcite.plan.RelOptTable;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * RelColumnOrigin是一个数据结构，用于描述关系表达式产生的输出列的来源之一
 * 在Calcite的元数据系统中，这个类用于追踪列的血缘关系，即一个输出列最终来自于哪个表的哪一列
 * 它是列级元数据的重要组成部分，帮助优化器理解数据流和依赖关系
 * 例如：在查询"SELECT a+b AS c FROM t"中，输出列c有两个来源：t表中的a列和b列
 * 这个类在查询优化、谓词下推、列裁剪等优化规则中起到关键作用
 */
public class RelColumnOrigin {
  //~ Instance fields --------------------------------------------------------

  // 源表对象，表示该列最初来自哪个表
  // RelOptTable是Calcite中表示表的抽象接口，包含了表的元数据信息如表名、列信息、统计信息等
  // 通过这个字段可以追溯到列的原始来源表
  private final RelOptTable originTable;

  // 源表中列的索引（从0开始），表示该列在源表中的位置
  // 这个索引值是扁平化还是非扁平化取决于生成此描述的关系表达式是否已经执行了UDT（用户定义类型）扁平化
  // 例如：如果源表有3列，索引0表示第一列，索引1表示第二列，索引2表示第三列
  private final int iOriginColumn;

  // 标识该列是否是派生列，即是否经过计算或转换
  // false表示值直接从源表的列中获取（原始列）
  // true表示值是通过表达式计算、函数调用或其他转换操作得到的（派生列）
  // 例如：在"SELECT a+b AS c"中，c是派生列；在"SELECT a AS c"中，c是原始列
  private final boolean isDerived;

  //~ Constructors -----------------------------------------------------------

  /**
   * 构造函数：创建一个RelColumnOrigin对象
   * @param originTable 源表对象，指定列来自哪个表
   * @param iOriginColumn 源表中的列索引（从0开始），指定列在表中的位置
   * @param isDerived 是否为派生列，true表示是派生列，false表示是原始列
   */
  public RelColumnOrigin(
      RelOptTable originTable,
      int iOriginColumn,
      boolean isDerived) {
    this.originTable = originTable;
    this.iOriginColumn = iOriginColumn;
    this.isDerived = isDerived;
  }

  //~ Methods ----------------------------------------------------------------

  /** 返回源表对象，获取该列最初来自哪个表 */
  public RelOptTable getOriginTable() {
    return originTable;
  }

  /** 返回源表中列的索引（从0开始），获取该列在源表中的位置
   * 注意：这个索引是扁平化还是非扁平化取决于生成此描述的关系表达式是否已经执行了UDT扁平化
   * UDT（User Defined Type）扁平化是指将嵌套的用户定义类型展开为多列的过程
   */
  public int getOriginColumnOrdinal() {
    return iOriginColumn;
  }

  /**
   * 判断该列是否是派生列
   * 示例：考虑查询<code>select a+b as c, d as e from t</code>
   * 输出列c有两个来源（a和b），它们都是派生的
   * 输出列e有一个来源（d），它不是派生的
   *
   * @return 如果值直接从源表的列中获取则返回false；否则返回true
   */
  public boolean isDerived() {
    return isDerived;
  }

  // 重写Object类的equals方法，用于比较两个RelColumnOrigin对象是否相等
  @Override public boolean equals(@Nullable Object obj) {
    // 首先检查obj是否是RelColumnOrigin类型的实例
    if (!(obj instanceof RelColumnOrigin)) {
      return false;
    }
    // 将obj转换为RelColumnOrigin类型
    RelColumnOrigin other = (RelColumnOrigin) obj;
    // 比较三个字段是否都相等：源表的全限定名、列索引、是否派生
    return originTable.getQualifiedName().equals(
        other.originTable.getQualifiedName())
        && (iOriginColumn == other.iOriginColumn)
        && (isDerived == other.isDerived);
  }

  // 重写Object类的hashCode方法，用于生成对象的哈希码
  // 当两个对象equals返回true时，它们的hashCode必须相同
  @Override public int hashCode() {
    // 使用源表全限定名的哈希码、列索引和派生标志来计算哈希值
    // 派生标志为true时加上313（一个质数），以区分派生和非派生的情况
    return originTable.getQualifiedName().hashCode()
        + iOriginColumn + (isDerived ? 313 : 0);
  }
}
