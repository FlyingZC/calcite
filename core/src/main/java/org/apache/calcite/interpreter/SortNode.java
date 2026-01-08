/* // Apache 软件基金会许可证头，声明代码的版权信息和使用条款
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.  // 贡献者许可证协议和版权声明
 * The ASF licenses this file to you under the Apache License, Version 2.0  // 授予用户 Apache 2.0 许可证
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software  // 除非适用法律要求或书面同意
 * distributed under the License is distributed on an "AS IS" BASIS,  // 按原样分发，无任何明示或暗示保证
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and  // 查看许可证了解具体权限和限制
 * limitations under the License.
 */
package org.apache.calcite.interpreter;  // 声明包名，属于 Calcite 解释器模块，解释器是 Calcite 中用于执行查询计划的一种机制

import org.apache.calcite.rel.RelFieldCollation;  // 导入关系代数中的字段排序规则类，定义单个字段的排序方式（升序/降序、NULL值处理）
import org.apache.calcite.rel.core.Sort;  // 导入 Sort 关系节点类，表示 SQL 中的 ORDER BY 和 LIMIT/OFFSET 操作
import org.apache.calcite.rex.RexLiteral;  // 导入 RexLiteral 类，表示行表达式中的字面量常量
import org.apache.calcite.rex.RexNode;  // 导入 RexNode 基类，表示行表达式（Row Expression），Calcite 中表达式的统一抽象
import org.apache.calcite.util.Util;  // 导入工具类，提供各种实用方法

import com.google.common.collect.Ordering;  // 导入 Google Guava 的 Ordering 类，提供链式比较器组合功能

import java.util.ArrayList;  // 导入 ArrayList 动态数组类，用于存储排序后的行数据
import java.util.Comparator;  // 导入 Comparator 接口，用于定义自定义比较规则
import java.util.List;  // 导入 List 接口，表示有序集合

import static java.util.Objects.requireNonNull;  // 静态导入 requireNonNull 方法，用于空值检查

/**
 * Interpreter node that implements a  // 解释器节点，实现了 Sort 关系操作
 * {@link org.apache.calcite.rel.core.Sort}.  // Sort 节点对应 SQL 中的 ORDER BY、LIMIT 和 OFFSET 子句
 */  // 该类负责在解释器模式下执行排序和分页操作
public class SortNode extends AbstractSingleNode<Sort> {  // SortNode 继承自 AbstractSingleNode，表示这是一个单输入节点的解释器实现，泛型参数 Sort 表示对应的关系节点类型
  public SortNode(Compiler compiler, Sort rel) {  // 构造方法，接收编译器实例和 Sort 关系节点作为参数
    super(compiler, rel);  // 调用父类 AbstractSingleNode 的构造方法，初始化编译器和关系节点，父类会设置 source（输入源）和 sink（输出目标）
  }  // 构造方法结束，此时 SortNode 已准备好执行排序操作

  private static int getValueAsInt(RexNode node) {  // 静态辅助方法，将 RexNode 转换为整数值，用于提取 OFFSET 和 FETCH 的值
    return requireNonNull(((RexLiteral) node).getValueAs(Integer.class),  // 将 RexNode 强制转换为 RexLiteral，并获取其 Integer 类型的值，如果为 null 则抛出异常
        () -> "getValueAs(Integer.class) for " + node);  // 当值为 null 时，提供详细的错误信息，帮助调试
  }  // 方法结束，返回从 RexLiteral 中提取的整数值

  @Override public void run() throws InterruptedException {  // 重写父类的 run 方法，这是解释器节点的核心执行方法，负责执行排序和分页逻辑
    final int offset =  // 声明 offset 变量，表示跳过的行数（对应 SQL 的 OFFSET 子句）
        rel.offset == null  // 检查关系节点的 offset 属性是否为 null
            ? 0  // 如果 offset 为 null，表示没有 OFFSET 子句，默认跳过 0 行
            : getValueAsInt(rel.offset);  // 如果 offset 不为 null，调用 getValueAsInt 方法提取整数值
    final int fetch =  // 声明 fetch 变量，表示获取的行数限制（对应 SQL 的 LIMIT/FETCH 子句）
        rel.fetch == null  // 检查关系节点的 fetch 属性是否为 null
            ? -1  // 如果 fetch 为 null，表示没有 LIMIT 子句，-1 表示获取所有剩余行
            : getValueAsInt(rel.fetch);  // 如果 fetch 不为 null，调用 getValueAsInt 方法提取整数值
    // In pure limit mode. No sort required.  // 纯限制模式，不需要排序（当没有排序字段时，只需要处理 OFFSET 和 FETCH）
    Row row;  // 声明 Row 变量，用于临时存储从输入源接收的每一行数据
  loop:  // 定义标签 loop，用于在嵌套循环中直接跳出外层循环
    if (rel.getCollation().getFieldCollations().isEmpty()) {  // 检查排序规则是否为空，如果为空表示不需要排序，只需要处理 OFFSET 和 FETCH（纯限制模式）
      for (int i = 0; i < offset; i++) {  // 循环 offset 次，跳过前 offset 行数据
        row = source.receive();  // 从输入源接收一行数据
        if (row == null) {  // 检查接收到的行是否为 null（表示数据源已耗尽）
          break loop;  // 如果数据源已耗尽，直接跳出 loop 标签所在的整个代码块
        }  // 结束 if 判断
      }  // 结束跳过 offset 行的循环
      if (fetch >= 0) {  // 检查 fetch 是否大于等于 0（表示有 LIMIT 子句）
        for (int i = 0; i < fetch && (row = source.receive()) != null; i++) {  // 循环 fetch 次，每次从输入源接收一行并发送到输出目标，条件：i < fetch 且接收到的行不为 null
          sink.send(row);  // 将接收到的行发送到输出目标
        }  // 结束获取 fetch 行的循环
      } else {  // fetch < 0，表示没有 LIMIT 子句，需要获取所有剩余行
        while ((row = source.receive()) != null) {  // 循环从输入源接收行，直到数据源耗尽（row 为 null）
          sink.send(row);  // 将接收到的每一行发送到输出目标
        }  // 结束 while 循环
      }  // 结束 if-else 判断
    } else {  // 排序规则不为空，需要执行排序操作
      // Build a sorted collection.  // 构建一个已排序的集合，需要先将所有数据收集到内存中
      final List<Row> list = new ArrayList<>();  // 创建一个 ArrayList 用于存储所有从输入源接收的行数据
      while ((row = source.receive()) != null) {  // 循环从输入源接收所有行数据，直到数据源耗尽
        list.add(row);  // 将接收到的每一行添加到列表中
      }  // 结束 while 循环，此时 list 包含了所有需要排序的行
      list.sort(comparator());  // 调用 comparator() 方法获取比较器，对列表中的所有行进行排序
      final int end =  // 声明 end 变量，表示需要输出的最后一行的索引（不包含）
          fetch < 0 || offset + fetch > list.size()  // 检查是否没有 LIMIT 子句，或者 offset + fetch 超出了列表大小
          ? list.size()  // 如果没有 LIMIT 或超出范围，end 设置为列表大小（表示输出到最后一行）
          : offset + fetch;  // 否则 end 设置为 offset + fetch（表示输出到 offset + fetch - 1 行）
      for (int i = offset; i < end; i++) {  // 循环从 offset 开始到 end 结束（不包含 end），输出排序后的行
        sink.send(list.get(i));  // 从列表中获取索引为 i 的行，并发送到输出目标
      }  // 结束 for 循环
    }  // 结束 if-else 判断
    sink.end();  // 调用输出目标的 end 方法，通知输出目标数据已全部发送完毕
  }  // run 方法结束

  private Comparator<Row> comparator() {  // 私有方法，构建并返回用于比较 Row 对象的比较器
    if (rel.getCollation().getFieldCollations().size() == 1) {  // 检查排序字段的数量是否为 1（单字段排序）
      return comparator(rel.getCollation().getFieldCollations().get(0));  // 如果只有一个排序字段，直接调用单字段比较器方法，返回对应的比较器
    }  // 结束 if 判断
    return Ordering.compound(  // 如果有多个排序字段，使用 Guava 的 Ordering.compound 方法组合多个比较器，实现多字段排序
        Util.transform(rel.getCollation().getFieldCollations(),  // 使用 Util.transform 方法将字段排序规则列表转换为比较器列表
            SortNode::comparator));  // 对每个字段排序规则调用 comparator 静态方法，生成对应的比较器
  }  // 方法结束，返回组合后的比较器

  private static Comparator<Row> comparator(RelFieldCollation fieldCollation) {  // 静态私有方法，根据单个字段排序规则创建对应的比较器
    final int nullComparison = fieldCollation.nullDirection.nullComparison;  // 获取 NULL 值的比较规则：1 表示 NULL 值最小，-1 表示 NULL 值最大
    final int x = fieldCollation.getFieldIndex();  // 获取需要排序的字段索引（在行数据中的位置）
    switch (fieldCollation.direction) {  // 根据排序方向（升序或降序）选择不同的比较逻辑
    case ASCENDING:  // 升序排序
      return (o1, o2) -> {  // 返回一个 Lambda 表达式实现的比较器，接收两个 Row 对象 o1 和 o2
        final Comparable c1 = (Comparable) o1.getValues()[x];  // 从第一行 o1 中获取索引为 x 的字段值，强制转换为 Comparable 类型以便比较
        final Comparable c2 = (Comparable) o2.getValues()[x];  // 从第二行 o2 中获取索引为 x 的字段值，强制转换为 Comparable 类型以便比较
        return RelFieldCollation.compare(c1, c2, nullComparison);  // 调用 RelFieldCollation.compare 方法比较两个值，传入 NULL 比较规则，返回比较结果（负数、0、正数）
      };  // Lambda 表达式结束
    default:  // 降序排序（DESCENDING）
      return (o1, o2) -> {  // 返回一个 Lambda 表达式实现的比较器，接收两个 Row 对象 o1 和 o2
        final Comparable c1 = (Comparable) o1.getValues()[x];  // 从第一行 o1 中获取索引为 x 的字段值，强制转换为 Comparable 类型以便比较
        final Comparable c2 = (Comparable) o2.getValues()[x];  // 从第二行 o2 中获取索引为 x 的字段值，强制转换为 Comparable 类型以便比较
        return RelFieldCollation.compare(c2, c1, -nullComparison);  // 调用 RelFieldCollation.compare 方法，但交换 c1 和 c2 的位置实现降序，同时反转 NULL 比较规则
      };  // Lambda 表达式结束
    }  // switch 语句结束
  }  // 方法结束，返回单字段比较器
}  // SortNode 类结束