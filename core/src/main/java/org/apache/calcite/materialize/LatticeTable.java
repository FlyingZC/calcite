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
// 声明该类所属的包：org.apache.calcite.materialize，这个包负责处理物化视图相关的功能
package org.apache.calcite.materialize;

// 导入 RelOptTable 接口，这是 Calcite 中表示表优化对象的接口，包含了表的元数据信息
import org.apache.calcite.plan.RelOptTable;
// 导入 RelDataTypeField 类，表示关系数据类型的字段，包含字段的名称、类型等信息
import org.apache.calcite.rel.type.RelDataTypeField;
// 导入 Util 工具类，提供各种实用的静态方法
import org.apache.calcite.util.Util;

// 导入 Nullable 注解，用于标记参数或返回值可能为 null
import org.checkerframework.checker.nullness.qual.Nullable;

// 静态导入 requireNonNull 方法，用于非空检查，如果为 null 则抛出 NullPointerException
import static java.util.Objects.requireNonNull;

/** Table registered in the graph. */
// 类注释：表示在图中注册的表
// 这个类是 Lattice（网格/立方体）结构中的一个表的表示
// Lattice 是一种多维数据结构，通常用于物化视图和 OLAP（联机分析处理）场景
// LatticeTable 封装了底层的 RelOptTable 对象，并提供了方便的访问方法
public class LatticeTable {
  // 成员变量：t，类型为 RelOptTable，使用 final 修饰表示不可变
  // 这个变量持有实际的表对象，包含了表的完整元数据信息，如表名、字段列表、类型等
  // RelOptTable 是 Calcite 优化器中表的抽象表示，包含了表在优化过程中的所有信息
  public final RelOptTable t;
  
  // 成员变量：alias，类型为 String，使用 final 修饰表示不可变
  // 这个变量存储表的别名，通常是表的最后一级名称
  // 例如：对于完全限定名 "catalog.schema.table"，alias 就是 "table"
  // 别名用于在 Lattice 结构中引用这个表，避免使用完全限定名
  public final String alias;

  // 构造方法：LatticeTable，接收一个 RelOptTable 参数
  // 这个构造方法是包级私有的（没有 public 修饰符），只能在同一个包内被调用
  // 参数 table：要封装的表对象，不能为 null
  LatticeTable(RelOptTable table) {
    // 将传入的 table 参数赋值给成员变量 t
    // 使用 requireNonNull 进行非空检查，如果 table 为 null，抛出 NullPointerException，错误信息为 "table"
    // 这确保了 t 成员变量永远不会是 null
    t = requireNonNull(table, "table");
    
    // 从 table 的完全限定名中提取最后一级名称作为别名
    // table.getQualifiedName() 返回表的完全限定名列表，例如 ["catalog", "schema", "table"]
    // Util.last() 方法获取列表的最后一个元素，即 "table"
    // 使用 requireNonNull 确保别名不为 null
    alias = requireNonNull(Util.last(table.getQualifiedName()));
  }

  // 重写 hashCode 方法，用于支持基于对象内容的哈希计算
  // 这个方法对于将 LatticeTable 对象放入 HashMap、HashSet 等集合中是必需的
  @Override public int hashCode() {
    // 返回表的完全限定名的哈希码
    // t.getQualifiedName() 返回表的完全限定名列表
    // hashCode() 方法计算这个列表的哈希值
    // 这样，两个表示同一个表的 LatticeTable 对象会有相同的哈希码
    return t.getQualifiedName().hashCode();
  }

  // 重写 equals 方法，用于比较两个 LatticeTable 对象是否相等
  // 这个方法对于将 LatticeTable 对象放入 HashMap、HashSet 等集合中是必需的
  // @Nullable 注解表示 obj 参数可能为 null
  @Override public boolean equals(@Nullable Object obj) {
    // 首先检查对象引用是否相同（this == obj），如果是，直接返回 true
    // 然后检查 obj 是否是 LatticeTable 类的实例
    // 最后比较两个对象的表的完全限定名是否相等
    // 使用 && 短路运算符，如果前面的条件不满足，后面的条件不会执行
    return this == obj
        || obj instanceof LatticeTable
        && t.getQualifiedName().equals(
            // 将 obj 强制转换为 LatticeTable 类型，然后获取其表的完全限定名进行比较
            ((LatticeTable) obj).t.getQualifiedName());
  }

  // 重写 toString 方法，用于返回对象的字符串表示
  // 这个方法在调试和日志输出时非常有用
  @Override public String toString() {
    // 返回表的完全限定名的字符串表示
    // t.getQualifiedName() 返回完全限定名列表，toString() 将其转换为字符串
    // 例如：["catalog", "schema", "table"] 会转换为 "catalog.schema.table"
    return t.getQualifiedName().toString();
  }

  // 实例方法：field，用于获取表中指定索引位置的字段
  // 这个方法是包级私有的，只能在同一个包内被调用
  // 参数 i：字段的索引位置，从 0 开始
  // 返回值：RelDataTypeField 对象，表示指定位置的字段信息
  RelDataTypeField field(int i) {
    // 首先获取表的行类型（RowType），它包含了所有字段的信息
    // t.getRowType() 返回 RelDataType 对象，表示表的行类型
    // getFieldList() 获取字段列表，返回 List<RelDataTypeField>
    // get(i) 获取指定索引位置的字段
    return t.getRowType().getFieldList().get(i);
  }
}
