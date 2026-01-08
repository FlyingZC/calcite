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
// Apache Calcite 是一个动态数据管理框架,提供SQL解析、优化、执行等功能,本文件位于关系表达式(rel)包中
// 本类提供了关系表达式(RelNode)相关的工具方法,用于比较、查找、遍历关系表达式树
package org.apache.calcite.rel;

// 导入聚合操作相关类,Aggregate表示聚合操作节点,AggregateCall表示聚合函数调用
import org.apache.calcite.rel.core.Aggregate;
import org.apache.calcite.rel.core.AggregateCall;
// 导入过滤操作相关类,Filter表示过滤操作节点
import org.apache.calcite.rel.core.Filter;
// 导入连接操作相关类,Join表示连接操作节点
import org.apache.calcite.rel.core.Join;
// 导入投影操作相关类,Project表示投影操作节点
import org.apache.calcite.rel.core.Project;
// 导入行表达式相关类,RexNode表示行表达式节点,是Calcite中表达式的抽象
import org.apache.calcite.rex.RexNode;
// 导入行表达式工具类,提供表达式查找等实用方法
import org.apache.calcite.rex.RexUtil;
// 导入Calcite工具类,提供通用的工具方法,如FoundOne异常用于提前终止遍历
import org.apache.calcite.util.Util;

// 导入Google Guava库的Ordering类,用于提供排序功能
import com.google.common.collect.Ordering;

// 导入空值检查框架的注解,@Nullable表示参数或返回值可以为null
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入Java标准库接口,Comparator用于比较两个对象
import java.util.Comparator;
// 导入Java 8函数式接口,BiConsumer接收两个参数并执行操作,无返回值
import java.util.function.BiConsumer;
// 导入Java 8函数式接口,Predicate接收一个参数并返回布尔值,用于条件判断
import java.util.function.Predicate;

/**
 * Utilities concerning relational expressions.
 * 关系表达式(RelNode)的工具类,提供静态方法用于比较、查找和遍历关系表达式树
 * RelNode是Calcite中关系代数操作的基本抽象,表示SQL查询中的各种操作(如扫描、过滤、投影、连接、聚合等)
 * 关系表达式树(RelNode Tree)是由多个RelNode节点组成的树形结构,表示完整的查询计划
 * 本类提供的主要功能:
 * 1. 提供RelNode的比较器,用于对关系表达式进行排序和比较
 * 2. 在关系表达式树中查找特定的行表达式(RexNode)
 * 3. 判断关系表达式树中是否包含满足条件的聚合调用
 * 4. 遍历关系表达式树并对找到的节点执行回调操作
 */
public class RelNodes {
  /** Comparator that provides an arbitrary but stable ordering to
   * {@link RelNode}s.
   * 提供任意但稳定的排序的比较器,用于对RelNode进行排序
   * 稳定排序意味着相同的输入总是产生相同的输出顺序
   * 该比较器先比较行类型(RowType)的字段数量,再比较RelNode的ID
   * 字段数量比较优先于ID比较,因为当规则添加到活动规则集时,字段数量更稳定
   * 使用场景:在规则匹配、集合操作、缓存键生成等需要确定RelNode顺序的场景
   */
  public static final Comparator<RelNode> COMPARATOR =
      new RelNodeComparator();

  /** Ordering for {@link RelNode}s.
   * 基于COMPARATOR的Guava Ordering对象,提供更强大的排序功能
   * Ordering是Guava库对Comparator的增强,提供了链式调用、反向排序、空值处理等额外功能
   * 例如可以使用ORDERING.reverse()进行反向排序,或ORDERING.nullsFirst()处理空值
   * 这是一个静态常量,可以在整个Calcite代码库中复用
   */
  public static final Ordering<RelNode> ORDERING = Ordering.from(COMPARATOR);

  // 私有构造方法,防止实例化,因为这是一个工具类,所有方法都是静态的
  private RelNodes() {}

  /** Compares arrays of {@link RelNode}.
   * 比较两个RelNode数组的大小和内容
   * 比较规则:先比较数组长度,长度不同则返回长度差;长度相同则逐个比较数组元素
   * 使用COMPARATOR比较每个元素,返回第一个不相同的比较结果
   * 如果两个数组长度相同且所有元素都相等,则返回0表示数组相等
   * 
   * @param rels0 第一个RelNode数组
   * @param rels1 第二个RelNode数组
   * @return 负数表示rels0小于rels1,0表示相等,正数表示rels0大于rels1
   * 
   * 使用场景:在规则匹配时比较两个关系表达式集合,或在缓存键生成时比较输入集合
   */
  public static int compareRels(RelNode[] rels0, RelNode[] rels1) {
    // 先比较两个数组的长度,使用Integer.compare返回长度差
    int c = Integer.compare(rels0.length, rels1.length);
    // 如果长度不同,直接返回长度差,不需要继续比较元素
    if (c != 0) {
      return c;
    }
    // 长度相同,逐个比较数组的每个元素
    for (int i = 0; i < rels0.length; i++) {
      // 使用COMPARATOR比较当前位置的两个RelNode元素
      c = COMPARATOR.compare(rels0[i], rels1[i]);
      // 如果发现不相等的元素,立即返回比较结果
      if (c != 0) {
        return c;
      }
    }
    // 所有元素都相等,返回0表示两个数组相等
    return 0;
  }

  /** Returns whether a tree of {@link RelNode}s contains a match for a
   * {@link RexNode} finder.
   * 判断关系表达式树中是否包含满足条件的行表达式(RexNode)或聚合调用(AggregateCall)
   * 该方法通过调用findRex方法遍历关系表达式树,并在找到匹配项时抛出FoundOne异常来提前终止
   * 
   * @param rel 要搜索的关系表达式树的根节点
   * @param aggPredicate 用于判断聚合调用是否满足条件的谓词,如果找到满足条件的聚合调用则返回true
   * @param finder RexFinder对象,用于在行表达式中查找特定的模式或节点
   * @return 如果找到匹配的行表达式或满足条件的聚合调用则返回true,否则返回false
   * 
   * 工作原理:
   * 1. 调用findRex方法遍历关系表达式树
   * 2. 传入一个BiConsumer回调,当找到匹配项时抛出Util.FoundOne.NULL异常
   * 3. 如果findRex正常完成(未抛出异常),说明没有找到匹配项,返回false
   * 4. 如果捕获到FoundOne异常,说明找到了匹配项,返回true
   * 
   * 使用场景:
   * - 检查查询计划中是否包含某个特定的表达式
   * - 检查是否存在使用特定函数的聚合调用
   * - 在规则匹配时判断是否满足某些条件
   */
  public static boolean contains(RelNode rel,
      Predicate<AggregateCall> aggPredicate, RexUtil.RexFinder finder) {
    try {
      // 调用findRex方法遍历关系表达式树,传入finder和aggPredicate
      // 传入一个BiConsumer作为回调,当找到匹配项时抛出FoundOne异常
      findRex(rel, finder, aggPredicate, (relNode, rexNode) -> {
        // 抛出FoundOne异常来提前终止遍历,表示找到了匹配项
        throw Util.FoundOne.NULL;
      });
      // 如果findRex正常完成(未抛出异常),说明没有找到匹配项,返回false
      return false;
    } catch (Util.FoundOne e) {
      // 捕获到FoundOne异常,说明找到了匹配项,返回true
      return true;
    }
  }

  /** Searches for expressions in a tree of {@link RelNode}s.
   * 在关系表达式树中搜索行表达式(RexNode)或聚合调用(AggregateCall)
   * 该方法递归遍历关系表达式树,对Filter、Project、Join、Aggregate等节点类型进行特殊处理
   * 对于每种节点类型,提取其中的行表达式并使用finder进行查找
   * 
   * @param rel 要搜索的关系表达式树的根节点
   * @param finder RexFinder对象,用于在行表达式中查找特定的模式或节点
   * @param aggPredicate 用于判断聚合调用是否满足条件的谓词
   * @param consumer 回调函数,当找到匹配项时被调用,接收RelNode和RexNode两个参数
   * 
   * 工作原理:
   * 1. 遍历关系表达式树,对每个节点进行类型判断
   * 2. 如果是Filter节点,使用finder查找其条件表达式中的匹配项
   * 3. 如果是Project节点,使用finder查找其投影列表中每个表达式的匹配项
   * 4. 如果是Join节点,使用finder查找其连接条件中的匹配项
   * 5. 如果是Aggregate节点,使用aggPredicate判断每个聚合调用是否满足条件
   * 6. 对于找到的匹配项,调用consumer回调函数
   * 7. 递归处理所有输入节点,继续向下遍历
   * 
   * 注意:
   * - finder在找到匹配项时会抛出FoundOne异常,需要捕获并调用consumer
   * - 对于Aggregate节点,consumer的第二个参数为null,因为匹配的是AggregateCall而不是RexNode
   * - 该方法是递归的,会遍历整个关系表达式树
   * 
   * TODO: a new method RelNode.accept(RexVisitor, BiConsumer), with similar
   * overrides to RelNode.accept(RexShuttle), would be better.
   * TODO注释:建议在RelNode中添加一个新的accept方法,接收RexVisitor和BiConsumer参数
   * 这样可以避免使用instanceof判断,利用多态性使代码更优雅
   * 类似于RelNode.accept(RexShuttle)的设计模式,每种RelNode类型可以重写accept方法
   */
  public static void findRex(RelNode rel, RexUtil.RexFinder finder,
      Predicate<AggregateCall> aggPredicate,
      BiConsumer<RelNode, @Nullable RexNode> consumer) {
    // 判断当前节点是否为Filter(过滤)节点
    if (rel instanceof Filter) {
      // 类型转换,获取Filter节点
      Filter filter = (Filter) rel;
      try {
        // 使用finder访问Filter的条件表达式,查找匹配项
        // 如果找到匹配项,finder会抛出FoundOne异常
        filter.getCondition().accept(finder);
      } catch (Util.FoundOne e) {
        // 捕获FoundOne异常,表示找到了匹配项
        // 调用consumer回调,传入Filter节点和找到的RexNode
        consumer.accept(filter, (RexNode) e.getNode());
      }
    }
    // 判断当前节点是否为Project(投影)节点
    if (rel instanceof Project) {
      // 类型转换,获取Project节点
      Project project = (Project) rel;
      // 遍历Project节点的投影列表(每个RexNode代表一个输出字段的表达式)
      for (RexNode node : project.getProjects()) {
        try {
          // 使用finder访问当前投影表达式,查找匹配项
          node.accept(finder);
        } catch (Util.FoundOne e) {
          // 捕获FoundOne异常,表示找到了匹配项
          // 调用consumer回调,传入Project节点和找到的RexNode
          consumer.accept(project, (RexNode) e.getNode());
        }
      }
    }
    // 判断当前节点是否为Join(连接)节点
    if (rel instanceof Join) {
      // 类型转换,获取Join节点
      Join join = (Join) rel;
      try {
        // 使用finder访问Join的连接条件表达式,查找匹配项
        join.getCondition().accept(finder);
      } catch (Util.FoundOne e) {
        // 捕获FoundOne异常,表示找到了匹配项
        // 调用consumer回调,传入Join节点和找到的RexNode
        consumer.accept(join, (RexNode) e.getNode());
      }
    }
    // 判断当前节点是否为Aggregate(聚合)节点
    if (rel instanceof Aggregate) {
      // 类型转换,获取Aggregate节点
      Aggregate aggregate = (Aggregate) rel;
      // 遍历Aggregate节点的聚合调用列表
      for (AggregateCall aggregateCall : aggregate.getAggCallList()) {
        // 使用aggPredicate测试当前聚合调用是否满足条件
        if (aggPredicate.test(aggregateCall)) {
          // 如果满足条件,调用consumer回调
          // 注意:第二个参数为null,因为匹配的是AggregateCall而不是RexNode
          consumer.accept(aggregate, null);
        }
      }
    }
    // 递归遍历当前节点的所有输入节点,继续向下搜索
    // RelNode可能有多个输入(如Join有两个输入),也可能只有一个输入(如Filter)
    for (RelNode input : rel.getInputs()) {
      // 递归调用findRex,处理每个输入节点
      findRex(input, finder, aggPredicate, consumer);
    }
  }

  /** Arbitrary stable comparator for {@link RelNode}s.
   * RelNode的任意但稳定的比较器,实现了Comparator接口
   * 该比较器提供了一种确定性的比较方式,使得相同的RelNode总是得到相同的比较结果
   * 
   * 比较策略:
   * 1. 首先比较行类型(RowType)的字段数量,字段数量多的被认为"更小"(返回负值)
   * 2. 如果字段数量相同,则比较RelNode的唯一ID,ID小的被认为"更小"
   * 
   * 为什么先比较字段数量:
   * - 字段数量比ID更稳定,因为ID可能会因为规则的添加而变化
   * - 当规则添加到活动规则集时,新规则可能会影响ID的生成
   * - 字段数量反映了关系表达式的结构特征,通常在优化过程中保持不变
   * 
   * 注意:
   * - 这是一个私有静态内部类,只能被RelNodes类使用
   * - 该比较器不关心RelNode的实际内容或逻辑,只关心结构特征
   * - 反转字段数量的比较结果(-c)是为了让字段数量多的排在前面
   */
  private static class RelNodeComparator implements Comparator<RelNode> {
    // 实现Comparator接口的compare方法,比较两个RelNode
    @Override public int compare(RelNode o1, RelNode o2) {
      // Compare on field count first. It is more stable than id (when rules
      // are added to the set of active rules).
      // 注释说明:首先比较字段数量,这比ID更稳定(当规则添加到活动规则集时)
      // 获取两个RelNode的行类型(RowType),并比较它们的字段数量
      // RowType描述了关系表达式的输出字段,包括字段名、类型等信息
      final int c =
          Integer.compare(o1.getRowType().getFieldCount(),
              o2.getRowType().getFieldCount());
      // 如果字段数量不同,返回字段数量差的负值
      // 这意味着字段数量多的RelNode会被认为"更小"(排在前面)
      if (c != 0) {
        return -c;
      }
      // 字段数量相同,比较RelNode的唯一ID
      // 每个RelNode在创建时会被分配一个唯一的ID,用于标识和调试
      // ID小的RelNode被认为"更小"(排在前面)
      return Integer.compare(o1.getId(), o2.getId());
    }
  }
}
