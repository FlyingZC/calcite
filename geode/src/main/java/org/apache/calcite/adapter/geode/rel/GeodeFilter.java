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
// 包声明：Geode适配器的关系表达式包，包含Geode特定的关系操作符实现
package org.apache.calcite.adapter.geode.rel;
// 导入Calcite优化器核心类：RelOptCluster表示优化器集群，包含RexBuilder等共享对象
import org.apache.calcite.plan.RelOptCluster;
// 导入RelOptCost：表示关系操作符的成本，用于优化器选择最优执行计划
import org.apache.calcite.plan.RelOptCost;
// 导入RelOptPlanner：优化器接口，用于计算成本和选择执行计划
import org.apache.calcite.plan.RelOptPlanner;
// 导入RelOptUtil：提供关系表达式操作的工具方法，如分解AND/OR条件
import org.apache.calcite.plan.RelOptUtil;
// 导入RelTraitSet：关系表达式特性集合，如物理特性、排序特性等
import org.apache.calcite.plan.RelTraitSet;
// 导入RelNode：关系表达式的基础接口，所有关系操作符都实现此接口
import org.apache.calcite.rel.RelNode;
// 导入Filter：过滤操作符的基类，实现关系代数中的选择操作
import org.apache.calcite.rel.core.Filter;
// 导入RelMetadataQuery：用于查询关系表达式元数据的工具类
import org.apache.calcite.rel.metadata.RelMetadataQuery;
// 导入RelDataType：表示关系数据类型，包含字段信息
import org.apache.calcite.rel.type.RelDataType;
// 导入RexBuilder：用于构建RexNode表达式树的工厂类
import org.apache.calcite.rex.RexBuilder;
// 导入RexCall：表示函数调用表达式，如运算符、函数调用等
import org.apache.calcite.rex.RexCall;
// 导入RexInputRef：表示对输入字段的引用，如t.column
import org.apache.calcite.rex.RexInputRef;
// 导入RexLiteral：表示字面量常量，如数字、字符串等
import org.apache.calcite.rex.RexLiteral;
// 导入RexNode：行表达式的基础接口，所有表达式都实现此接口
import org.apache.calcite.rex.RexNode;
// 导入RexUtil：提供行表达式操作的工具方法
import org.apache.calcite.rex.RexUtil;
// 导入SqlKind：SQL操作符类型枚举，如EQUALS、LESS_THAN等
import org.apache.calcite.sql.SqlKind;
// 导入DateString：表示日期值的工具类
import org.apache.calcite.util.DateString;
// 导入TimeString：表示时间值的工具类
import org.apache.calcite.util.TimeString;
// 导入TimestampString：表示时间戳值的工具类
import org.apache.calcite.util.TimestampString;
// 导入Util：Calcite通用工具类，提供字符串连接、集合操作等方法
import org.apache.calcite.util.Util;
// 导入Nullable注解：表示返回值可能为null，用于空值检查
import org.checkerframework.checker.nullness.qual.Nullable;
// 导入Java集合类：ArrayList动态数组，Collections集合工具类
import java.util.ArrayList;
import java.util.Collections;
// 导入LinkedHashSet：保持插入顺序的Set集合，用于去重
import java.util.LinkedHashSet;
import java.util.List;
// 导入Locale：本地化信息，用于格式化字符串
import java.util.Locale;
import java.util.Set;
// 导入Stream API：用于函数式编程和集合操作
import java.util.stream.Collectors;
// 导入checkArgument：Google Guava前置条件检查工具
import static com.google.common.base.Preconditions.checkArgument;
// 导入CHAR：SQL字符类型常量
import static org.apache.calcite.sql.type.SqlTypeName.CHAR;
// 导入requireNonNull：Java Objects工具类，用于非空检查
import static java.util.Objects.requireNonNull;

/**
 * Geode过滤器关系表达式的实现
 * 
 * 这个类实现了Calcite的Filter关系表达式，用于将SQL中的WHERE条件转换为Geode OQL查询的过滤条件。
 * Geode是Apache的一个内存数据网格，支持OQL（Object Query Language）查询语言。
 * 
 * 主要功能：
 * 1. 将Calcite的RexNode表达式树转换为Geode OQL的WHERE子句字符串
 * 2. 支持常见的比较操作符：=、<、<=、>、>=
 * 3. 支持逻辑操作符：AND、OR
 * 4. 支持IN SET优化：将多个OR条件转换为IN SET以提高查询性能
 * 5. 支持字段引用、类型转换、字面量等表达式
 * 
 * 工作原理：
 * - 构造函数中创建Translator对象，将RexNode条件转换为OQL字符串
 * - implement方法将过滤条件添加到Geode实现上下文中
 * - 内部Translator类负责具体的表达式转换逻辑
 * 
 * 性能优化：
 * - 将Filter的成本乘以0.1，鼓励优化器尽早应用过滤
 * - 使用IN SET替代多个OR条件，减少Geode查询的复杂度
 */
public class GeodeFilter extends Filter implements GeodeRel {
  // match成员变量：存储转换后的OQL过滤条件字符串
  // 例如："name = 'John' AND age > 18" 或 "id IN SET(1, 2, 3)"
  // 这个字符串会被直接添加到Geode OQL查询的WHERE子句中
  private final String match;
  /**
   * GeodeFilter构造函数
   * 
   * 参数说明：
   * @param cluster 优化器集群，包含RexBuilder等共享对象，用于构建表达式
   * @param traitSet 关系表达式特性集合，包含物理特性等
   * @param input 输入关系节点，通常是GeodeScan或GeodeProject等
   * @param condition 过滤条件，以RexNode表达式树形式表示
   * 
   * 构造过程：
   * 1. 调用父类Filter构造函数初始化基础属性
   * 2. 创建Translator对象，用于将RexNode转换为OQL字符串
   * 3. 调用translator.translateMatch()将条件转换为OQL字符串并存储到match成员变量
   * 4. 断言检查：确保当前节点的convention是GeodeRel.CONVENTION
   * 5. 断言检查：确保输入节点的convention也是GeodeRel.CONVENTION
   * 
   * 注意事项：
   * - Translator使用rowType获取字段名称映射
   * - 转换过程中会优化IN SET条件以提高性能
   */
  GeodeFilter(RelOptCluster cluster, RelTraitSet traitSet,
      RelNode input, RexNode condition) {
    // 调用父类Filter构造函数，初始化cluster、traitSet、input和condition
    super(cluster, traitSet, input, condition);
    // 创建Translator对象，传入行类型和RexBuilder
    // getRowType()获取输入行的数据类型，包含字段信息
    // getCluster().getRexBuilder()获取RexBuilder用于构建表达式
    Translator translator = new Translator(getRowType(), getCluster().getRexBuilder());
    // 调用Translator的translateMatch方法将RexNode条件转换为OQL字符串
    // 转换结果存储到match成员变量中，后续implement方法会使用
    this.match = translator.translateMatch(condition);
    // 断言检查：确保当前节点的convention是GeodeRel.CONVENTION
    // GeodeRel.CONVENTION是Geode适配器的物理特性标识
    assert getConvention() == GeodeRel.CONVENTION;
    // 断言检查：确保输入节点的convention也是GeodeRel.CONVENTION
    // 这保证了整个查询树都是Geode物理实现
    assert getConvention() == input.getConvention();
  }
  /**
   * 计算当前关系操作符的成本
   * 
   * 参数说明：
   * @param planner 优化器，用于计算成本
   * @param mq 元数据查询，用于获取统计信息
   * 
   * 返回值：
   * @return 关系操作符的成本，包含行数、CPU和IO成本
   * 
   * 成本计算：
   * 1. 调用父类computeSelfCost计算基础成本
   * 2. 将成本乘以0.1，表示Filter操作的成本较低
   * 
   * 优化策略：
   * - 将Filter成本设置为较低的值，鼓励优化器尽早应用过滤
   * - 这样可以减少后续操作的数据量，提高整体查询性能
   */
  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,
      RelMetadataQuery mq) {
    // 调用父类computeSelfCost计算基础成本
    // requireNonNull确保cost不为null
    final RelOptCost cost = requireNonNull(super.computeSelfCost(planner, mq));
    // 将成本乘以0.1，表示Filter操作的成本较低
    // 这样优化器会倾向于尽早应用过滤条件
    return cost.multiplyBy(0.1);
  }
  /**
   * 创建GeodeFilter的副本
   * 
   * 参数说明：
   * @param traitSet 新的特性集合
   * @param input 新的输入节点
   * @param condition 新的过滤条件
   * 
   * 返回值：
   * @return 新的GeodeFilter实例，保持相同的cluster但使用新的参数
   * 
   * 使用场景：
   * - 优化器在应用规则时需要创建关系节点的副本
   * - 例如：应用Filter下推规则时，将Filter移动到Scan节点之前
   */
  @Override public GeodeFilter copy(RelTraitSet traitSet, RelNode input, RexNode condition) {
    // 创建新的GeodeFilter实例，使用相同的cluster但新的traitSet、input和condition
    return new GeodeFilter(getCluster(), traitSet, input, condition);
  }
  /**
   * 实现GeodeFilter，将过滤条件添加到Geode实现上下文中
   * 
   * 参数说明：
   * @param geodeImplementContext Geode实现上下文，用于构建OQL查询
   * 
   * 实现过程：
   * 1. 调用visitChild处理输入节点，实现自底向上的遍历
   * 2. 将match字符串添加到上下文的谓词列表中
   * 
   * 工作原理：
   * - 采用访问者模式，先处理子节点再处理当前节点
   * - 输入节点可能是GeodeScan或GeodeProject等
   * - 谓词列表最终会被组合成OQL查询的WHERE子句
   */
  @Override public void implement(GeodeImplementContext geodeImplementContext) {
    // 首先调用输入节点的visitChild方法，实现自底向上的遍历
    // 这样可以确保先处理子节点，再处理当前节点
    geodeImplementContext.visitChild(getInput());
    // 将转换后的OQL过滤条件字符串添加到上下文的谓词列表中
    // Collections.singletonList创建只包含一个元素的不可变列表
    geodeImplementContext.addPredicates(Collections.singletonList(match));
  }
  /**
   * Translator静态内部类
   * 
   * 作用：将Calcite的RexNode表达式树转换为Geode OQL表达式字符串
   * 
   * 主要功能：
   * 1. 处理二元比较操作符：=、<、<=、>、>=
   * 2. 处理逻辑操作符：AND、OR
   * 3. 处理特殊表达式：IS NOT NULL、NOT、CAST
   * 4. 优化IN SET：将多个OR条件转换为IN SET以提高性能
   * 5. 处理字面量：包括日期、时间、时间戳等特殊类型
   * 
   * 设计模式：
   * - 使用访问者模式遍历RexNode表达式树
   * - 递归处理嵌套的表达式
   * - 使用模式匹配处理不同类型的表达式
   */
  static class Translator {
    // fieldNames成员变量：存储行类型的字段名称列表
    // 例如：["id", "name", "age", "salary"]
    // 用于将RexInputRef的索引映射到实际的字段名称
    private final List<String> fieldNames;
    // rexBuilder成员变量：RexBuilder实例，用于构建RexNode表达式
    // 标记为@SuppressWarnings("unused")是因为在某些情况下可能不直接使用
    // 但保留它以备将来扩展使用
    @SuppressWarnings("unused")
    private final RexBuilder rexBuilder;
    /**
     * Translator构造函数
     * 
     * 参数说明：
     * @param rowType 行数据类型，包含字段信息
     * @param rexBuilder RexBuilder实例，用于构建表达式
     * 
     * 初始化过程：
     * 1. 检查rowType非空，否则抛出NullPointerException
     * 2. 检查rexBuilder非空，否则抛出NullPointerException
     * 3. 调用GeodeRules.geodeFieldNames()获取字段名称列表
     * 
     * 字段名称处理：
     * - GeodeRules.geodeFieldNames()会将rowType的字段转换为Geode兼容的名称
     * - 可能处理大小写、特殊字符等转换
     */
    Translator(RelDataType rowType, RexBuilder rexBuilder) {
      // 检查rowType非空，如果为null抛出NullPointerException并提示"rowType"
      requireNonNull(rowType, "rowType");
      // 存储rexBuilder，标记为非空检查
      this.rexBuilder = requireNonNull(rexBuilder, "rexBuilder");
      // 调用GeodeRules.geodeFieldNames()获取字段名称列表
      // 这个方法会处理rowType，返回Geode兼容的字段名称
      this.fieldNames = GeodeRules.geodeFieldNames(rowType);
    }
    /**
     * 将字面量的值转换为字符串表示
     * 
     * 参数说明：
     * @param literal 要转换的字面量表达式
     * 
     * 返回值：
     * @return 字面量的字符串表示，可能包含类型前缀
     * 
     * 转换规则：
     * - TIMESTAMP/TIMESTAMP_WITH_LOCAL_TIME_ZONE：返回 "TIMESTAMP '值'"
     * - DATE：返回 "DATE '值'"
     * - TIME/TIME_WITH_LOCAL_TIME_ZONE：返回 "TIME '值'"
     * - 其他类型：返回值的字符串表示
     * 
     * 示例：
     * - TIMESTAMP字面量：TIMESTAMP '2023-01-01 12:00:00'
     * - DATE字面量：DATE '2023-01-01'
     * - TIME字面量：TIME '12:00:00'
     * - 整数字面量：123
     * - 字符串字面量：'hello'
     */
    private static String literalValue(RexLiteral literal) {
      // 获取字面量的值，转换为Comparable类型以便比较
      final Comparable valueComparable = literal.getValueAs(Comparable.class);
      // 根据字面量的类型进行不同的处理
      switch (literal.getTypeName()) {
      // 时间戳类型：带时区或不带时区
      case TIMESTAMP:
      case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
        // 断言值是TimestampString类型
        assert valueComparable instanceof TimestampString;
        // 返回带TIMESTAMP前缀的字符串，例如：TIMESTAMP '2023-01-01 12:00:00'
        return "TIMESTAMP '" + valueComparable + "'";
      // 日期类型
      case DATE:
        // 断言值是DateString类型
        assert valueComparable instanceof DateString;
        // 返回带DATE前缀的字符串，例如：DATE '2023-01-01'
        return "DATE '" + valueComparable + "'";
      // 时间类型：带时区或不带时区
      case TIME:
      case TIME_WITH_LOCAL_TIME_ZONE:
        // 断言值是TimeString类型
        assert valueComparable instanceof TimeString;
        // 返回带TIME前缀的字符串，例如：TIME '12:00:00'
        return "TIME '" + valueComparable + "'";
      // 其他类型：直接返回值的字符串表示
      default:
        // getValue3()获取字面量的值，转换为字符串
        return String.valueOf(literal.getValue3());
      }
    }
    /**
     * 将条件表达式转换为OQL谓词字符串
     * 
     * 参数说明：
     * @param condition 要转换的条件表达式，以RexNode形式表示
     * 
     * 返回值：
     * @return OQL谓词字符串，例如："name = 'John' AND age > 18"
     * 
     * 转换过程：
     * 1. 展开SEARCH表达式：将SEARCH转换为多个OR条件
     * 2. 分解OR条件：将条件按OR分解为多个子条件
     * 3. 处理单个条件：如果只有一个条件，调用translateAnd处理AND逻辑
     * 4. 处理多个条件：如果有多个OR条件，调用translateOr处理OR逻辑
     * 
     * SEARCH展开：
     * - SEARCH是Calcite的一种特殊表达式，表示在集合中查找
     * - Geode不支持SEARCH，所以需要展开为多个OR条件
     * - 例如：SEARCH(name IN SET('a', 'b')) -> name = 'a' OR name = 'b'
     */
    private String translateMatch(RexNode condition) {
      // 展开SEARCH表达式，因为当前转换逻辑无法直接处理SEARCH
      // RexUtil.expandSearch会将SEARCH转换为多个OR条件
      // 例如：SEARCH(name IN SET('a', 'b')) 转换为 name = 'a' OR name = 'b'
      // 这样可以后续使用IN SET优化提高查询性能
      final RexNode condition2 =
          RexUtil.expandSearch(rexBuilder, null, condition);
      // 使用RelOptUtil.disjunctions将条件按OR分解为多个子条件
      // 例如：a = 1 OR b = 2 OR c = 3 分解为 [a = 1, b = 2, c = 3]
      List<RexNode> disjunctions = RelOptUtil.disjunctions(condition2);
      // 如果只有一个条件，直接调用translateAnd处理AND逻辑
      if (disjunctions.size() == 1) {
        return translateAnd(disjunctions.get(0));
      // 如果有多个OR条件，调用translateOr处理OR逻辑
      } else {
        return translateOr(disjunctions);
      }
    }
    /**
     * 将合取谓词（AND条件）转换为OQL字符串
     * 
     * 参数说明：
     * @param condition 合取谓词，可能包含多个AND连接的条件
     * 
     * 返回值：
     * @return OQL字符串，多个条件用AND连接
     * 
     * 转换过程：
     * 1. 使用RelOptUtil.conjunctions分解AND条件
     * 2. 对每个子条件调用translateMatch2进行转换
     * 3. 使用Util.toString将所有条件用AND连接
     * 
     * 示例：
     * - 输入：name = 'John' AND age > 18 AND salary > 5000
     * - 输出："name = 'John' AND age > 18 AND salary > 5000"
     */
    private String translateAnd(RexNode condition) {
      // 创建谓词列表，存储每个AND条件的转换结果
      List<String> predicates = new ArrayList<>();
      // 使用RelOptUtil.conjunctions将条件按AND分解为多个子条件
      // 例如：a = 1 AND b = 2 AND c = 3 分解为 [a = 1, b = 2, c = 3]
      for (RexNode node : RelOptUtil.conjunctions(condition)) {
        // 对每个子条件调用translateMatch2进行转换
        // translateMatch2处理二元比较操作符、IS NOT NULL、NOT等
        predicates.add(translateMatch2(node));
      }
      // 使用Util.toString将所有条件用AND连接
      // 参数：列表、前缀、分隔符、后缀
      // 例如：["a = 1", "b = 2", "c = 3"] -> "a = 1 AND b = 2 AND c = 3"
      return Util.toString(predicates, "", " AND ", "");
    }
    /**
     * 获取左节点的字段名称，用于IN SET查询
     * 
     * 参数说明：
     * @param left 表达式的左节点，通常是字段引用
     * 
     * 返回值：
     * @return 字段名称，如果不能识别则返回null
     * 
     * 处理的节点类型：
     * - INPUT_REF：直接获取字段名称
     * - CAST：递归处理CAST内部的节点
     * - ITEM/OTHER_FUNCTION：使用RexToGeodeTranslator转换
     * - 其他类型：返回null
     * 
     * 示例：
     * - INPUT_REF：id -> "id"
     * - CAST：CAST(id AS INTEGER) -> "id"
     * - ITEM：address.city -> "address.city"
     */
    /** 获取左节点的字段名称，用于IN SET查询 */
    private @Nullable String getLeftNodeFieldName(RexNode left) {
      // 根据左节点的类型进行不同的处理
      switch (left.getKind()) {
      // 输入字段引用：直接获取字段名称
      case INPUT_REF:
        // 将RexNode转换为RexInputRef
        final RexInputRef left1 = (RexInputRef) left;
        // 使用索引从fieldNames列表中获取字段名称
        return fieldNames.get(left1.getIndex());
      // 类型转换：递归处理CAST内部的节点
      case CAST:
        // FIXME 这在所有情况下都不适用（例如，我们忽略字符串编码）
        // 获取CAST的第一个操作数，递归调用getLeftNodeFieldName
        return getLeftNodeFieldName(((RexCall) left).operands.get(0));
      // 数组项访问或其他函数：使用RexToGeodeTranslator转换
      case ITEM:
      case OTHER_FUNCTION:
        // 使用访问者模式，让RexNode自己转换为Geode表达式
        return left.accept(new GeodeRules.RexToGeodeTranslator(this.fieldNames));
      // 其他类型：返回null，表示无法识别
      default:
        return null;
      }
    }
    /**
     * 判断是否可以使用IN SET查询子句来提高查询性能
     * 
     * 参数说明：
     * @param disjunctions OR条件列表
     * 
     * 返回值：
     * @return 如果可以使用IN SET则返回true，否则返回false
     * 
     * 使用IN SET的条件：
     * 1. 必须有多个OR条件（至少2个）
     * 2. 每个条件必须是EQUALS操作符
     * 3. 每个条件的左节点必须是可识别的字段
     * 4. 每个条件的右节点必须是字面量
     * 5. 所有条件的左节点字段必须相同
     * 
     * 性能优化：
     * - IN SET比多个OR条件性能更好
     * - Geode可以优化IN SET查询，使用索引等
     * 
     * 示例：
     * - 可以优化：id = 1 OR id = 2 OR id = 3 -> id IN SET(1, 2, 3)
     * - 不能优化：id = 1 OR name = 'John'（字段不同）
     * - 不能优化：id = 1 OR id > 2（操作符不同）
     */
    /** 判断是否可以使用IN SET查询子句来提高查询性能 */
    private boolean useInSetQueryClause(List<RexNode> disjunctions) {
      // 只对多个OR条件使用IN SET，单个条件不优化
      if (disjunctions.size() <= 1) {
        return false;
      }
      // 检查所有条件是否都满足IN SET的要求
      return disjunctions.stream().allMatch(node -> {
        // IN SET查询只能用于EQUALS操作符
        if (node.getKind() != SqlKind.EQUALS) {
          return false;
        }
        // 将RexNode转换为RexCall
        RexCall call = (RexCall) node;
        // 获取左操作数
        final RexNode left = call.operands.get(0);
        // 获取右操作数
        final RexNode right = call.operands.get(1);
        // 右节点必须是字面量
        if (right.getKind() != SqlKind.LITERAL) {
          return false;
        }
        // 获取左节点的字段名称
        String name = getLeftNodeFieldName(left);
        // 如果字段名称为null，表示无法识别，不能使用IN SET
        return name != null;
      });
    }
    /**
     * 创建OQL的IN SET谓词字符串
     * 
     * 参数说明：
     * @param disjunctions OR条件列表，所有条件必须满足IN SET的要求
     * 
     * 返回值：
     * @return IN SET谓词字符串，例如："id IN SET(1, 2, 3)"
     * 
     * 转换过程：
     * 1. 检查disjunctions非空
     * 2. 获取第一个条件的左节点字段名称
     * 3. 收集所有条件的右节点字面量值
     * 4. 使用LinkedHashSet去重并保持顺序
     * 5. 格式化为IN SET字符串
     * 
     * 示例：
     * - 输入：[id = 1, id = 2, id = 3]
     * - 输出："id IN SET(1, 2, 3)"
     */
    /** 创建OQL的IN SET谓词字符串 */
    private String translateInSet(List<RexNode> disjunctions) {
      // 检查disjunctions非空，如果为空抛出IllegalArgumentException
      checkArgument(!disjunctions.isEmpty(), "empty disjunctions");
      // 获取第一个条件
      RexNode firstNode = disjunctions.get(0);
      // 转换为RexCall
      RexCall firstCall = (RexCall) firstNode;
      // 获取第一个条件的左操作数
      final RexNode left = firstCall.operands.get(0);
      // 获取左节点的字段名称
      String name = getLeftNodeFieldName(left);
      // 创建LinkedHashSet存储右字面量值，使用LinkedHashSet去重并保持插入顺序
      Set<String> rightLiteralValueList = new LinkedHashSet<>();
      // 遍历所有条件，收集右字面量值
      disjunctions.forEach(node -> {
        // 将RexNode转换为RexCall
        RexCall call = (RexCall) node;
        // 获取右操作数，转换为RexLiteral
        RexLiteral rightLiteral = (RexLiteral) call.operands.get(1);
        // 将字面量值添加到集合中，quoteCharLiteral处理字符类型
        rightLiteralValueList.add(quoteCharLiteral(rightLiteral));
      });
      // 格式化为IN SET字符串，使用Locale.ROOT确保格式一致性
      // 例如："id IN SET(1, 2, 3)"
      return String.format(Locale.ROOT, "%s IN SET(%s)", name,
          String.join(", ", rightLiteralValueList));
    }
    /**
     * 获取节点的左节点字段名称
     * 
     * 参数说明：
     * @param node 节点，通常是EQUALS操作符的调用
     * 
     * 返回值：
     * @return 左节点的字段名称，如果不能识别则返回null
     * 
     * 使用场景：
     * - 在translateOr中判断是否可以使用IN SET优化
     * - 将相同字段的条件分组，分别处理
     */
    private @Nullable String getLeftNodeFieldNameForNode(RexNode node) {
      // 将RexNode转换为RexCall
      final RexCall call = (RexCall) node;
      // 获取第一个操作数（左节点）
      final RexNode left = call.operands.get(0);
      // 调用getLeftNodeFieldName获取字段名称
      return getLeftNodeFieldName(left);
    }
    /**
     * 获取与指定节点左字段相同的所有OR条件
     * 
     * 参数说明：
     * @param node 指定的节点
     * @param disjunctions 所有OR条件列表
     * 
     * 返回值：
     * @return 左字段相同的所有条件列表
     * 
     * 使用场景：
     * - 在translateOr中将相同字段的条件分组
     * - 对每个字段组分别应用IN SET优化
     * 
     * 示例：
     * - 输入：node=id=1, disjunctions=[id=1, id=2, name='John']
     * - 输出：[id=1, id=2]
     */
    private List<RexNode> getLeftNodeDisjunctions(RexNode node, List<RexNode> disjunctions) {
      // 创建结果列表
      List<RexNode> leftNodeDisjunctions = new ArrayList<>();
      // 获取指定节点的左字段名称
      String leftNodeFieldName = getLeftNodeFieldNameForNode(node);
      // 如果左字段名称不为null，进行过滤
      if (leftNodeFieldName != null) {
        // 使用Stream API过滤出左字段相同的所有条件
        leftNodeDisjunctions = disjunctions.stream().filter(rexNode -> {
          // 将RexNode转换为RexCall
          RexCall rexCall = (RexCall) rexNode;
          // 获取左操作数
          RexNode rexCallLeft = rexCall.operands.get(0);
          // 检查左字段名称是否相同
          return leftNodeFieldName.equals(getLeftNodeFieldName(rexCallLeft));
        // 收集为列表
        }).collect(Collectors.toList());
      }
      // 返回过滤后的条件列表
      return leftNodeDisjunctions;
    }
    /**
     * 将析取谓词（OR条件）转换为OQL字符串
     * 
     * 参数说明：
     * @param disjunctions OR条件列表
     * 
     * 返回值：
     * @return OQL字符串，多个条件用OR连接，可能包含IN SET优化
     * 
     * 转换过程：
     * 1. 遍历每个条件，按左字段分组
     * 2. 对每个字段组，检查是否可以使用IN SET优化
     * 3. 如果可以使用IN SET，调用translateInSet
     * 4. 否则，使用translateMatch或translateMatch2转换
     * 5. 使用Util.toString将所有条件用OR连接
     * 
     * 优化策略：
     * - 相同字段的多个OR条件优先使用IN SET
     * - 避免重复处理相同的字段
     * - 复杂条件（包含AND）需要用括号包裹
     * 
     * 示例：
     * - 输入：[id=1, id=2, name='John']
     * - 输出："id IN SET(1, 2) OR name = 'John'"
     */
    private String translateOr(List<RexNode> disjunctions) {
      // 创建谓词列表，存储每个条件的转换结果
      List<String> predicates = new ArrayList<>();
      // 创建左字段名称列表，用于跟踪已处理的字段
      List<String> leftFieldNameList = new ArrayList<>();
      // 创建已使用IN SET的字段名称列表
      List<String> inSetLeftFieldNameList = new ArrayList<>();
      // 遍历每个条件
      for (RexNode node : disjunctions) {
        // 获取当前节点的左字段名称
        final String leftNodeFieldName = getLeftNodeFieldNameForNode(node);
        // 如果任何左节点已经用IN SET谓词处理过，跳过
        // 因为所有节点已经被处理过了
        if (inSetLeftFieldNameList.contains(leftNodeFieldName)) {
          continue;
        }
        // 创建左节点条件列表
        List<RexNode> leftNodeDisjunctions = new ArrayList<>();
        // 标记是否可以使用IN SET查询子句
        boolean useInSetQueryClause = false;
        // 如果左字段节点名称已经处理过且不适用于IN SET查询子句，可以跳过检查
        if (!leftFieldNameList.contains(leftNodeFieldName)) {
          // 获取与当前节点左字段相同的所有条件
          leftNodeDisjunctions = getLeftNodeDisjunctions(node, disjunctions);
          // 检查是否可以使用IN SET查询子句
          useInSetQueryClause = useInSetQueryClause(leftNodeDisjunctions);
        }
        // 如果可以使用IN SET查询子句
        if (useInSetQueryClause) {
          // 调用translateInSet创建IN SET谓词字符串
          predicates.add(translateInSet(leftNodeDisjunctions));
          // 将字段名称添加到已使用IN SET的列表中
          inSetLeftFieldNameList.add(leftNodeFieldName);
        // 如果节点包含多个AND条件，需要用括号包裹
        } else if (RelOptUtil.conjunctions(node).size() > 1) {
          // 用括号包裹，递归调用translateMatch
          predicates.add("(" + translateMatch(node) + ")");
        // 否则，直接调用translateMatch2转换
        } else {
          predicates.add(translateMatch2(node));
        }
        // 将字段名称添加到已处理列表中
        leftFieldNameList.add(leftNodeFieldName);
      }
      // 使用Util.toString将所有条件用OR连接
      return Util.toString(predicates, "", " OR ", "");
    }
    /**
     * 将二元关系表达式转换为OQL字符串
     * 
     * 参数说明：
     * @param node 要转换的二元关系表达式
     * 
     * 返回值：
     * @return OQL字符串，例如："name = 'John'"
     * 
     * 支持的表达式类型：
     * - EQUALS：等于操作符
     * - LESS_THAN：小于操作符
     * - LESS_THAN_OR_EQUAL：小于等于操作符
     * - GREATER_THAN：大于操作符
     * - GREATER_THAN_OR_EQUAL：大于等于操作符
     * - INPUT_REF：输入字段引用（转换为字段 = true）
     * - IS_NOT_NULL：非空判断
     * - NOT：非操作（转换为字段 = false）
     * - CAST：类型转换（递归处理）
     * 
     * 注意事项：
     * - 不支持的表达式类型会抛出AssertionError
     * - 目前主要支持相等比较，未来可能支持不等比较
     */
    /**
     * 将二元关系表达式转换为OQL字符串
     */
    private String translateMatch2(RexNode node) {
      // 我们目前只使用相等比较，但未来应该支持聚类键上的不等比较
      RexNode child;
      // 根据节点的类型进行不同的处理
      switch (node.getKind()) {
      // 等于操作符
      case EQUALS:
        // 调用translateBinary转换，op和rop都是"="
        return translateBinary("=", "=", (RexCall) node);
      // 小于操作符
      case LESS_THAN:
        // 调用translateBinary转换，op是"<"，rop是">"（用于反转）
        return translateBinary("<", ">", (RexCall) node);
      // 小于等于操作符
      case LESS_THAN_OR_EQUAL:
        // 调用translateBinary转换，op是"<="，rop是">="（用于反转）
        return translateBinary("<=", ">=", (RexCall) node);
      // 大于操作符
      case GREATER_THAN:
        // 调用translateBinary转换，op是">"，rop是"<"（用于反转）
        return translateBinary(">", "<", (RexCall) node);
      // 大于等于操作符
      case GREATER_THAN_OR_EQUAL:
        // 调用translateBinary转换，op是">="，rop是"<="（用于反转）
        return translateBinary(">=", "<=", (RexCall) node);
      // 输入字段引用：转换为字段 = true
      case INPUT_REF:
        // 将字段引用转换为"字段 = true"
        return translateBinary2("=", node, rexBuilder.makeLiteral(true));
      // 非空判断：转换为字段 <> null
      case IS_NOT_NULL:
        // 获取IS_NOT_NULL的操作数
        child = ((RexCall) node).getOperands().get(0);
        // 转换为"字段 <> null"
        return translateBinary2("<>", child, rexBuilder.makeNullLiteral(node.getType()));
      // 非操作：转换为字段 = false
      case NOT:
        // 获取NOT的操作数
        child = ((RexCall) node).getOperands().get(0);
        // 如果操作数是CAST，获取CAST内部的节点
        if (child.getKind() == SqlKind.CAST) {
          child = ((RexCall) child).getOperands().get(0);
        }
        // 如果操作数是INPUT_REF，转换为"字段 = false"
        if (child.getKind() == SqlKind.INPUT_REF) {
          return translateBinary2("=", child, rexBuilder.makeLiteral(false));
        }
        // 否则跳出switch
        break;
      // 类型转换：递归处理CAST内部的节点
      case CAST:
        // 获取CAST的第一个操作数，递归调用translateMatch2
        return translateMatch2(((RexCall) node).getOperands().get(0));
      // 其他类型：跳出switch
      default:
        break;
      }
      // 如果不支持的节点类型，抛出AssertionError
      throw new AssertionError("Cannot translate " + node + ", kind=" + node.getKind());
    }
    /**
     * 将二元操作符调用转换为OQL字符串，必要时反转参数
     * 
     * 参数说明：
     * @param op 正向操作符，例如："="
     * @param rop 反向操作符，例如：">"
     * @param call 二元操作符调用
     * 
     * 返回值：
     * @return OQL字符串，例如："name = 'John'"
     * 
     * 转换过程：
     * 1. 尝试使用正向操作符（左操作数 op 右操作数）
     * 2. 如果失败，尝试使用反向操作符（右操作数 rop 左操作数）
     * 3. 如果都失败，抛出AssertionError
     * 
     * 使用场景：
     * - 处理不等操作符，如<、<=、>、>=
     * - 当左操作数不是字段引用时，尝试反转操作数
     * 
     * 示例：
     * - 输入：op="<", rop=">", call=(5 < age)
     * - 输出："age > 5"（反转操作数和操作符）
     */
    /**
     * 将二元操作符调用转换为OQL字符串，必要时反转参数
     */
    private String translateBinary(String op, String rop, RexCall call) {
      // 获取左操作数
      final RexNode left = call.operands.get(0);
      // 获取右操作数
      final RexNode right = call.operands.get(1);
      // 尝试使用正向操作符转换（左操作数 op 右操作数）
      String expression = translateBinary2Opt(op, left, right);
      // 如果转换成功，返回结果
      if (expression != null) {
        return expression;
      }
      // 尝试使用反向操作符转换（右操作数 rop 左操作数）
      expression = translateBinary2Opt(rop, right, left);
      // 如果转换成功，返回结果
      if (expression != null) {
        return expression;
      }
      // 如果都失败，抛出AssertionError
      throw new AssertionError("cannot translate op " + op + " call " + call);
    }
    /**
     * 将二元操作符调用转换为OQL字符串，失败时抛出异常
     * 
     * 参数说明：
     * @param op 操作符，例如："="
     * @param left 左操作数
     * @param right 右操作数
     * 
     * 返回值：
     * @return OQL字符串，例如："name = 'John'"
     * 
     * 使用场景：
     * - 当确定可以成功转换时使用此方法
     * - 如果转换失败会抛出NullPointerException
     */
    /**
     * 将二元操作符调用转换为OQL字符串，失败时抛出异常
     */
    private String translateBinary2(String op, RexNode left,
        RexNode right) {
      // 调用translateBinary2Opt进行转换
      final String s = translateBinary2Opt(op, left, right);
      // 如果结果为null，抛出NullPointerException
      return requireNonNull(s, "s");
    }
    /**
     * 将二元操作符调用转换为OQL字符串，失败时返回null
     * 
     * 参数说明：
     * @param op 操作符，例如："="
     * @param left 左操作数
     * @param right 右操作数
     * 
     * 返回值：
     * @return OQL字符串，如果无法转换则返回null
     * 
     * 转换条件：
     * 1. 右操作数必须是LITERAL（字面量）
     * 2. 左操作数必须是以下之一：
     *    - INPUT_REF：输入字段引用
     *    - CAST：类型转换
     *    - ITEM：数组项访问
     * 3. 其他情况返回null
     * 
     * 转换过程：
     * 1. 检查右操作数是否为LITERAL
     * 2. 根据左操作数类型进行不同的处理
     * 3. 调用translateOp2或quoteCharLiteral生成最终字符串
     */
    /**
     * 将二元操作符调用转换为OQL字符串，失败时返回null
     */
    private @Nullable String translateBinary2Opt(String op, RexNode left,
        RexNode right) {
      // 检查右操作数是否为LITERAL（字面量）
      switch (right.getKind()) {
      case LITERAL:
        // 如果是字面量，跳出switch继续处理
        break;
      default:
        // 如果不是字面量，返回null表示无法转换
        return null;
      }
      // 将右操作数转换为RexLiteral
      final RexLiteral rightLiteral = (RexLiteral) right;
      // 根据左操作数类型进行不同的处理
      switch (left.getKind()) {
      // 输入字段引用
      case INPUT_REF:
        // 将RexNode转换为RexInputRef
        final RexInputRef left1 = (RexInputRef) left;
        // 使用索引从fieldNames列表中获取字段名称
        String name = fieldNames.get(left1.getIndex());
        // 调用translateOp2生成"字段 op 值"格式的字符串
        return translateOp2(op, name, rightLiteral);
      // 类型转换：递归处理CAST内部的节点
      case CAST:
        // FIXME 这在所有情况下都不适用（例如，我们忽略字符串编码）
        // 获取CAST的第一个操作数，递归调用translateBinary2
        return translateBinary2(op, ((RexCall) left).operands.get(0), right);
      // 数组项访问：使用RexToGeodeTranslator转换
      case ITEM:
        // 使用访问者模式，让RexNode自己转换为Geode表达式
        String item = left.accept(new GeodeRules.RexToGeodeTranslator(this.fieldNames));
        // 如果转换成功，拼接"字段 op 值"格式的字符串
        return (item == null) ? null : item + " " + op + " " + quoteCharLiteral(rightLiteral);
      // 其他类型：返回null表示无法转换
      default:
        return null;
      }
    }
    /**
     * 为字符字面量添加引号
     * 
     * 参数说明：
     * @param literal 字面量表达式
     * 
     * 返回值：
     * @return 带引号的字符串，例如："'hello'"
     * 
     * 处理规则：
     * - 如果是CHAR类型，添加单引号
     * - 其他类型直接返回字面量值
     * 
     * 示例：
     * - CHAR类型：'hello' -> "'hello'"
     * - 整数类型：123 -> "123"
     */
    private static String quoteCharLiteral(RexLiteral literal) {
      // 调用literalValue获取字面量的字符串表示
      String value = literalValue(literal);
      // 如果是CHAR类型，添加单引号
      if (literal.getTypeName() == CHAR) {
        value = "'" + value + "'";
      }
      // 返回处理后的字符串
      return value;
    }
    /**
     * 将字段名称、操作符和字面量组合成谓词字符串
     * 
     * 参数说明：
     * @param op 操作符，例如："="
     * @param name 字段名称，例如："name"
     * @param right 字面量，例如：'John'
     * 
     * 返回值：
     * @return 谓词字符串，例如："name = 'John'"
     * 
     * 组合过程：
     * 1. 调用quoteCharLiteral处理字面量
     * 2. 使用空格分隔字段名称、操作符和字面量
     * 
     * 示例：
     * - 输入：op="=", name="name", right='John'
     * - 输出："name = 'John'"
     */
    /**
     * 将字段名称、操作符和字面量组合成谓词字符串
     */
    private static String translateOp2(String op, String name, RexLiteral right) {
      // 调用quoteCharLiteral处理字面量，可能添加引号
      String valueString = quoteCharLiteral(right);
      // 使用空格分隔字段名称、操作符和字面量，返回最终字符串
      return name + " " + op + " " + valueString;
    }
  }
}