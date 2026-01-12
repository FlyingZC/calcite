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
package org.apache.calcite.adapter.druid; // Druid适配器包，包含与Druid数据源集成的相关类

import org.apache.calcite.avatica.util.TimeUnitRange; // 时间单位范围枚举，表示从年到秒等不同时间粒度
import org.apache.calcite.rel.type.RelDataType; // 关系数据类型，表示Calcite中的类型系统
import org.apache.calcite.rex.RexCall; // Rex调用节点，表示函数调用表达式
import org.apache.calcite.rex.RexInputRef; // Rex输入引用，表示对输入字段的引用
import org.apache.calcite.rex.RexLiteral; // Rex字面量，表示常量值
import org.apache.calcite.rex.RexNode; // Rex节点基类，表示关系表达式树中的节点
import org.apache.calcite.sql.SqlKind; // SQL操作类型枚举，如比较运算符、逻辑运算符等
import org.apache.calcite.sql.type.SqlTypeName; // SQL类型名称枚举，如INTEGER、VARCHAR等
import org.apache.calcite.util.DateString; // 日期字符串包装类，用于处理日期类型值
import org.apache.calcite.util.RangeSets; // 范围集合工具类，提供范围操作的工具方法
import org.apache.calcite.util.Sarg; // 搜索参数(SEARCH ARGument)，用于表示IN、BETWEEN等搜索条件
import org.apache.calcite.util.TimestampString; // 时间戳字符串包装类，用于处理时间戳类型值
import org.apache.calcite.util.Util; // 通用工具类，提供各种辅助方法
import org.apache.calcite.util.trace.CalciteTrace; // Calcite日志追踪工具，用于获取日志记录器

import com.google.common.collect.BoundType; // 边界类型枚举，表示范围的开闭类型(OPEN/CLOSED)
import com.google.common.collect.ImmutableList; // 不可变列表实现，提供线程安全的列表操作
import com.google.common.collect.ImmutableRangeSet; // 不可变范围集合，用于管理范围集合
import com.google.common.collect.Range; // 范围类，表示一个连续的值区间
import com.google.common.collect.TreeRangeSet; // 基于树的范围集合，提供高效的范围操作

import org.checkerframework.checker.nullness.qual.Nullable; // 可空性注解，标记可能为null的值
import org.joda.time.Interval; // Joda时间间隔类，表示一个时间区间
import org.joda.time.Period; // Joda时间段类，表示一段时间长度
import org.joda.time.chrono.ISOChronology; // ISO时间年表，提供ISO标准的时间计算
import org.slf4j.Logger; // SLF4J日志接口，用于日志记录

import java.util.ArrayList; // 动态数组列表，提供可变大小的数组实现
import java.util.List; // 列表接口，定义有序集合的操作

import static java.util.Objects.requireNonNull; // 静态导入，用于对象非空检查

/**
 * Druid日期时间工具类，用于从RexNode表达式生成时间区间
 * 
 * 这个类是Calcite与Druid适配器中的核心工具类，主要负责以下功能：
 * 1. 将SQL中的时间过滤条件(如WHERE子句中的时间谓词)转换为Druid所需的时间区间(Interval)
 * 2. 支持各种时间比较运算符(=, <, <=, >, >=, BETWEEN, IN, SEARCH等)
 * 3. 支持逻辑运算符(AND, OR, NOT)的组合
 * 4. 从时间表达式提取时间粒度(Granularity)，用于Druid的聚合操作
 * 5. 在Calcite的时间单位和Druid的粒度之间进行转换
 * 
 * Druid是一个分布式实时分析数据库，它使用时间区间来优化查询性能。
 * 当查询中包含时间过滤条件时，Druid可以只扫描相关时间片段的数据，
 * 从而大幅减少需要扫描的数据量。
 * 
 * 该类通过解析RexNode表达式树，提取出时间范围约束，并将其转换为
 * Joda Time的Interval对象列表，这些Interval对象可以直接传递给Druid查询。
 * 
 * 主要使用场景：
 * - SQL查询优化：将时间谓词下推到Druid数据源
 * - 查询计划生成：在VolcanoPlanner中生成Druid查询计划
 * - 时间粒度推断：从FLOOR和EXTRACT函数中推断时间聚合粒度
 */
@SuppressWarnings({"rawtypes", "unchecked" }) // 抑制原始类型和未检查转换的警告，因为使用了泛型擦除
public class DruidDateTimeUtils { // Druid日期时间工具类，提供时间区间生成和转换功能

  protected static final Logger LOGGER = CalciteTrace.getPlannerTracer(); // 日志记录器，用于记录时间区间推断和转换的调试信息

  private DruidDateTimeUtils() { // 私有构造方法，防止实例化，该类只提供静态工具方法
  } // 私有构造方法结束，确保该类不能被实例化

  /**
   * 从给定的RexNode表达式生成等价的时间区间列表
   * 
   * 这是该类的核心入口方法，用于将SQL中的时间过滤条件转换为Druid所需的时间区间。
   * 该方法假设输入表达式中的所有谓词都引用同一列：时间戳列。
   * 
   * 工作流程：
   * 1. 调用extractRanges方法从RexNode表达式中提取时间范围列表
   * 2. 如果无法提取范围，返回null表示无法优化
   * 3. 使用TreeRangeSet对提取的范围进行合并和压缩，消除重叠和相邻的范围
   * 4. 将压缩后的范围转换为Interval对象列表
   * 
   * 支持的表达式类型：
   * - 简单比较：timestamp = '2020-01-01', timestamp > '2020-01-01'
   * - 范围查询：timestamp BETWEEN '2020-01-01' AND '2020-12-31'
   * - IN列表：timestamp IN ('2020-01-01', '2020-02-01', '2020-03-01')
   * - 搜索参数：timestamp SEARCH (SARG)
   * - 逻辑组合：使用AND、OR、NOT组合上述条件
   * 
   * @param e RexNode表达式，表示SQL中的时间过滤条件
   * @return Interval对象列表，表示等价的时间区间；如果无法转换则返回null
   */
  public static @Nullable List<Interval> createInterval(RexNode e) { // 创建时间区间的主入口方法
    final List<Range<Long>> ranges = extractRanges(e, false); // 从表达式中提取时间范围列表，withNot=false表示不处理NOT运算
    if (ranges == null) { // 如果提取失败，返回null表示无法优化该查询
      // We did not succeed, bail out // 提取失败，退出处理
      return null; // 返回null，让调用者知道无法生成时间区间
    } // 提取失败的判断结束
    final TreeRangeSet condensedRanges = TreeRangeSet.create(); // 创建树形范围集合，用于范围合并和压缩
    for (Range r : ranges) { // 遍历所有提取的范围
      condensedRanges.add(r); // 将范围添加到集合中，TreeRangeSet会自动处理重叠和相邻范围的合并
    } // 范围合并循环结束
    LOGGER.debug("Inferred ranges on interval : {}", condensedRanges); // 记录调试日志，输出推断出的时间范围
    return toInterval(ImmutableList.<Range<Long>>copyOf(condensedRanges.asRanges())); // 将合并后的范围转换为Interval列表并返回
  } // createInterval方法结束

  /**
   * 将时间范围列表转换为Interval列表
   * 
   * 该方法将Guava的Range<Long>对象转换为Joda Time的Interval对象。
   * Range表示一个数值区间，而Interval表示一个时间区间。
   * 
   * 转换规则：
   * 1. 如果范围既无下界也无上界，返回DruidTable的默认时间区间
   * 2. 如果范围缺少下界，使用默认区间的开始时间
   * 3. 如果范围缺少上界，使用默认区间的结束时间
   * 4. 如果下界是开区间(OPEN)，起始时间需要+1毫秒
   * 5. 如果上界是闭区间(CLOSED)，结束时间需要+1毫秒(因为Druid的Interval是半开区间[start, end))
   * 
   * 边界类型说明：
   * - OPEN: 开区间，不包含边界值，如 (10, 20) 表示大于10且小于20
   * - CLOSED: 闭区间，包含边界值，如 [10, 20] 表示大于等于10且小于等于20
   * 
   * 时间戳说明：
   * - 使用毫秒级时间戳，表示自1970-01-01 00:00:00 UTC以来的毫秒数
   * - 使用ISOChronology.getInstanceUTC()确保使用UTC时区，避免时区转换问题
   * 
   * @param ranges Long类型的时间范围列表，每个范围表示一个时间区间
   * @return Interval对象列表，使用Joda Time的ISO时间年表
   */
  protected static List<Interval> toInterval( // 将范围列表转换为时间区间列表的辅助方法
      List<Range<Long>> ranges) { // 参数：Long类型的时间范围列表
    List<Interval> intervals = Util.transform(ranges, range -> { // 使用Util.transform将每个Range转换为Interval
      if (!range.hasLowerBound() && !range.hasUpperBound()) { // 如果范围既无下界也无上界
        return DruidTable.DEFAULT_INTERVAL; // 返回Druid表的默认时间区间
      } // 无边界情况处理结束
      long start = range.hasLowerBound() // 计算起始时间戳
          ? range.lowerEndpoint().longValue() // 如果有下界，使用下界值
          : DruidTable.DEFAULT_INTERVAL.getStartMillis(); // 否则使用默认区间的开始时间
      long end = range.hasUpperBound() // 计算结束时间戳
          ? range.upperEndpoint().longValue() // 如果有上界，使用上界值
          : DruidTable.DEFAULT_INTERVAL.getEndMillis(); // 否则使用默认区间的结束时间
      if (range.hasLowerBound() // 如果范围有下界
          && range.lowerBoundType() == BoundType.OPEN) { // 且下界是开区间
        start++; // 起始时间+1毫秒，因为开区间不包含边界值
      } // 下界开区间调整结束
      if (range.hasUpperBound() // 如果范围有上界
          && range.upperBoundType() == BoundType.CLOSED) { // 且上界是闭区间
        end++; // 结束时间+1毫秒，因为Druid的Interval是半开区间[start, end)
      } // 上界闭区间调整结束
      return new Interval(start, end, ISOChronology.getInstanceUTC()); // 创建并返回新的Interval对象，使用UTC时区
    }); // transform转换结束
    if (LOGGER.isDebugEnabled()) { // 如果开启了调试日志级别
      LOGGER.debug("Converted time ranges " + ranges + " to interval " + intervals); // 记录调试日志，显示转换前后的范围和区间
    } // 调试日志记录结束
    return intervals; // 返回转换后的Interval列表
  } // toInterval方法结束

  /**
   * 从RexNode表达式中递归提取时间范围列表
   * 
   * 这是一个递归方法，根据表达式的类型采用不同的提取策略：
   * - 叶子节点(比较运算符)：直接调用leafToRanges转换
   * - NOT运算符：反转withNot标志后递归处理子节点
   * - OR运算符：收集所有子节点的范围并合并(并集操作)
   * - AND运算符：计算所有子节点范围的交集(交集操作)
   * - 其他类型：返回null表示无法提取
   * 
   * withNot参数说明：
   * - withNot=false：正常处理，将条件转换为对应的时间范围
   * - withNot=true：反转处理，将条件转换为否定的时间范围
   * 例如：timestamp > '2020-01-01'，withNot=false时转换为(2020-01-01, +∞)
   *       withNot=true时转换为(-∞, 2020-01-01]
   * 
   * OR运算符处理逻辑：
   * OR表示并集，例如：timestamp < '2020-01-01' OR timestamp > '2020-12-31'
   * 结果是两个不连续的范围：(-∞, 2020-01-01) 和 (2020-12-31, +∞)
   * 
   * AND运算符处理逻辑：
   * AND表示交集，例如：timestamp > '2020-01-01' AND timestamp < '2020-12-31'
   * 结果是一个范围：(2020-01-01, 2020-12-31)
   * 如果某个子节点无法提取范围，整个AND表达式就无法提取，返回null
   * 
   * @param node RexNode表达式节点，可能是各种类型的表达式
   * @param withNot 是否需要反转逻辑，true表示处理NOT运算符后的条件
   * @return 时间范围列表；如果无法提取则返回null
   */
  protected static @Nullable List<Range<Long>> extractRanges(RexNode node, boolean withNot) { // 递归提取时间范围的核心方法
    switch (node.getKind()) { // 根据表达式类型进行分支处理
    case EQUALS: // 等于运算符
    case LESS_THAN: // 小于运算符
    case LESS_THAN_OR_EQUAL: // 小于等于运算符
    case GREATER_THAN: // 大于运算符
    case GREATER_THAN_OR_EQUAL: // 大于等于运算符
    case DRUID_IN: // Druid的IN运算符
    case SEARCH: // 搜索参数(SARG)
      return leafToRanges((RexCall) node, withNot); // 叶子节点，调用leafToRanges方法将条件转换为范围

    case NOT: // NOT运算符
      return extractRanges(((RexCall) node).getOperands().get(0), !withNot); // 递归处理子节点，并反转withNot标志

    case OR: { // OR运算符，表示并集
      RexCall call = (RexCall) node; // 将节点转换为RexCall类型
      List<Range<Long>> intervals = new ArrayList<>(); // 创建列表用于收集所有子节点的范围
      for (RexNode child : call.getOperands()) { // 遍历OR的所有子节点
        List<Range<Long>> extracted = // 递归提取子节点的范围
            extractRanges(child, withNot); // 传递withNot标志，保持一致性
        if (extracted != null) { // 如果提取成功
          intervals.addAll(extracted); // 将提取的范围添加到结果列表中
        } // 提取成功处理结束
      } // 子节点遍历结束
      return intervals; // 返回所有范围的列表，表示并集
    } // OR运算符处理结束

    case AND: { // AND运算符，表示交集
      RexCall call = (RexCall) node; // 将节点转换为RexCall类型
      List<Range<Long>> ranges = new ArrayList<>(); // 创建列表用于存储计算交集后的范围
      for (RexNode child : call.getOperands()) { // 遍历AND的所有子节点
        List<Range<Long>> extractedRanges = // 递归提取子节点的范围
            extractRanges(child, false); // AND的子节点不需要反转，withNot固定为false
        if (extractedRanges == null || extractedRanges.isEmpty()) { // 如果提取失败或结果为空
          // We could not extract, we bail out // 无法提取范围，退出处理
          return null; // 返回null，表示整个AND表达式无法提取
        } // 提取失败处理结束
        if (ranges.isEmpty()) { // 如果这是第一个子节点
          ranges.addAll(extractedRanges); // 直接使用第一个子节点的范围
          continue; // 继续处理下一个子节点
        } // 第一个子节点处理结束
        List<Range<Long>> overlapped = new ArrayList<>(); // 创建列表用于存储交集结果
        for (Range current : ranges) { // 遍历当前已有的范围
          for (Range interval : extractedRanges) { // 遍历新提取的范围
            if (current.isConnected(interval)) { // 如果两个范围相连(可能相交或相邻)
              overlapped.add(current.intersection(interval)); // 计算交集并添加到结果中
            } // 相连范围处理结束
          } // 新范围遍历结束
        } // 当前范围遍历结束
        ranges = overlapped; // 用交集结果替换原有范围
      } // 子节点遍历结束
      return ranges; // 返回计算交集后的范围列表
    } // AND运算符处理结束

    default: // 其他不支持的运算符类型
      return null; // 返回null表示无法提取范围
    } // switch分支结束
  } // extractRanges方法结束

  /**
   * 将叶子节点(比较运算符)转换为时间范围列表
   * 
   * 该方法处理各种比较运算符，将它们转换为Guava的Range对象。
   * 支持的运算符包括：=, <, <=, >, >=, BETWEEN, IN, SEARCH
   * 
   * 操作数处理：
   * - 支持两种形式：column OP literal 和 literal OP column
   * - 如果是第二种形式，需要反转运算符类型
   * - 例如：timestamp > '2020-01-01' 和 '2020-01-01' < timestamp 是等价的
   * 
   * withNot反转逻辑：
   * - LESS_THAN (<) withNot=true -> atLeast(>=)
   * - LESS_THAN_OR_EQUAL (<=) withNot=true -> greaterThan(>)
   * - GREATER_THAN (>) withNot=true -> atMost(<=)
   * - GREATER_THAN_OR_EQUAL (>=) withNot=true -> lessThan(<)
   * - EQUALS (=) withNot=true -> lessThan(<) OR greaterThan(>)
   * 
   * 时间戳处理：
   * - 所有时间值都转换为毫秒级时间戳
   * - 时间戳表示自1970-01-01 00:00:00 UTC以来的毫秒数
   * 
   * @param call RexCall调用节点，表示比较运算符
   * @param withNot 是否需要反转逻辑
   * @return 时间范围列表；如果转换失败则返回null
   */
  protected static @Nullable List<Range<Long>> leafToRanges(RexCall call, boolean withNot) { // 将叶子节点转换为范围的方法
    final ImmutableList.Builder<Range<Long>> ranges; // 范围构建器，用于构建不可变范围列表
    switch (call.getKind()) { // 根据运算符类型进行分支处理
    case EQUALS: // 等于运算符
    case LESS_THAN: // 小于运算符
    case LESS_THAN_OR_EQUAL: // 小于等于运算符
    case GREATER_THAN: // 大于运算符
    case GREATER_THAN_OR_EQUAL: { // 大于等于运算符
      final Long value; // 用于存储字面量值的变量
      SqlKind kind = call.getKind(); // 获取运算符类型
      if (call.getOperands().get(0) instanceof RexInputRef // 如果第一个操作数是输入引用(列名)
          && literalValue(call.getOperands().get(1)) != null) { // 且第二个操作数是字面量
        value = requireNonNull(literalValue(call.getOperands().get(1))); // 提取字面量值
      } else if (call.getOperands().get(1) instanceof RexInputRef // 如果第二个操作数是输入引用(列名)
          && literalValue(call.getOperands().get(0)) != null) { // 且第一个操作数是字面量
        value = requireNonNull(literalValue(call.getOperands().get(0))); // 提取字面量值
        kind = kind.reverse(); // 反转运算符类型，因为操作数顺序相反
      } else { // 如果操作数不符合预期格式
        return null; // 返回null表示无法转换
      } // 操作数处理结束
      switch (kind) { // 根据运算符类型生成对应的范围
      case LESS_THAN: // 小于运算符
        return ImmutableList.of(withNot ? Range.atLeast(value) : Range.lessThan(value)); // withNot时转为>=，否则转为<
      case LESS_THAN_OR_EQUAL: // 小于等于运算符
        return ImmutableList.of(withNot ? Range.greaterThan(value) : Range.atMost(value)); // withNot时转为>，否则转为<=
      case GREATER_THAN: // 大于运算符
        return ImmutableList.of(withNot ? Range.atMost(value) : Range.greaterThan(value)); // withNot时转为<=，否则转为>
      case GREATER_THAN_OR_EQUAL: // 大于等于运算符
        return ImmutableList.of(withNot ? Range.lessThan(value) : Range.atLeast(value)); // withNot时转为<，否则转为>=
      default: // 等于运算符(EQUALS)
        if (!withNot) { // 如果不需要反转
          return ImmutableList.of(Range.closed(value, value)); // 返回点范围[value, value]
        } // 正常情况处理结束
        return ImmutableList.of(Range.lessThan(value), Range.greaterThan(value)); // 返回两个范围：(-∞, value) 和 (value, +∞)
      } // 运算符类型分支结束
    } // 比较运算符处理块结束
    case BETWEEN: { // BETWEEN运算符，表示范围查询
      final Long value1; // 用于存储第一个边界值的变量
      final Long value2; // 用于存储第二个边界值的变量
      if (literalValue(call.getOperands().get(2)) != null // 如果第三个操作数是字面量(BETWEEN的下界)
          && literalValue(call.getOperands().get(3)) != null) { // 且第四个操作数是字面量(BETWEEN的上界)
        value1 = requireNonNull(literalValue(call.getOperands().get(2))); // 提取下界值
        value2 = requireNonNull(literalValue(call.getOperands().get(3))); // 提取上界值
      } else { // 如果边界值不是字面量
        return null; // 返回null表示无法转换
      } // 边界值提取结束

      boolean inverted = value1.compareTo(value2) > 0; // 检查边界值是否颠倒(下界大于上界)
      if (!withNot) { // 如果不需要反转
        return ImmutableList.of( // 返回单个范围
            inverted ? Range.closed(value2, value1) : Range.closed(value1, value2)); // 如果颠倒则交换，否则正常返回
      } // 正常BETWEEN处理结束
      return ImmutableList.of(Range.lessThan(inverted ? value2 : value1), // 返回两个范围：(-∞, 下界)
          Range.greaterThan(inverted ? value1 : value2)); // 和 (上界, +∞)
    } // BETWEEN运算符处理结束
    case DRUID_IN: // Druid的IN运算符，表示值列表查询
      ranges = ImmutableList.builder(); // 创建范围构建器
      for (RexNode operand : Util.skip(call.operands)) { // 遍历IN列表中的所有值(跳过第一个操作数，它是列引用)
        final Long element = literalValue(operand); // 提取当前元素的值
        if (element == null) { // 如果元素值无法提取
          return null; // 返回null表示无法转换
        } // 元素提取失败处理结束
        if (withNot) { // 如果需要反转(NOT IN)
          ranges.add(Range.lessThan(element)); // 添加小于该元素的范围
          ranges.add(Range.greaterThan(element)); // 添加大于该元素的范围
        } else { // 正常IN情况
          ranges.add(Range.closed(element, element)); // 添加点范围[element, element]
        } // IN值处理结束
      } // IN列表遍历结束
      return ranges.build(); // 构建并返回范围列表

    case SEARCH: // SEARCH运算符，使用SARG(搜索参数)表示复杂的搜索条件
      final RexLiteral right = (RexLiteral) call.operands.get(1); // 获取第二个操作数(字面量)
      final Sarg<?> sarg = requireNonNull(right.getValueAs(Sarg.class)); // 提取SARG对象，包含范围集合信息
      ranges = ImmutableList.builder(); // 创建范围构建器
      for (Range range : sarg.rangeSet.asRanges()) { // 遍历SARG中的所有范围
        Range<Long> range2 = RangeSets.copy(range, DruidDateTimeUtils::toLong); // 将范围转换为Long类型范围
        if (withNot) { // 如果需要反转
          ranges.addAll(ImmutableRangeSet.of(range2).complement().asRanges()); // 添加该范围的补集
        } else { // 正常SEARCH情况
          ranges.add(range2); // 直接添加该范围
        } // SEARCH范围处理结束
      } // SARG范围遍历结束
      return ranges.build(); // 构建并返回范围列表

    default: // 其他不支持的运算符类型
      return null; // 返回null表示无法转换
    } // switch分支结束
  } // leafToRanges方法结束

  /**
   * 将可比较对象转换为Long类型的时间戳
   * 
   * 该方法用于在处理SARG范围时，将各种时间类型转换为统一的毫秒级时间戳。
   * 支持的时间类型：
   * - TimestampString：时间戳字符串，表示精确到毫秒的时间
   * - DateString：日期字符串，表示精确到天的日期
   * 
   * 时间戳格式：
   * - 返回自1970-01-01 00:00:00 UTC以来的毫秒数
   * - 使用UTC时区，避免时区转换问题
   * 
   * 使用场景：
   * - 在leafToRanges方法中处理SEARCH运算符时
   * - 将SARG中的范围从通用类型转换为Long类型
   * 
   * @param comparable 可比较对象，可能是TimestampString或DateString
   * @return 毫秒级时间戳
   * @throws AssertionError 如果遇到不支持的时间类型
   */
  private static Long toLong(Comparable comparable) { // 将时间对象转换为时间戳的辅助方法
    if (comparable instanceof TimestampString) { // 如果对象是TimestampString类型
      TimestampString timestampString = (TimestampString) comparable; // 强制类型转换
      return timestampString.getMillisSinceEpoch(); // 获取自纪元以来的毫秒数
    } // TimestampString处理结束
    if (comparable instanceof DateString) { // 如果对象是DateString类型
      DateString dataString = (DateString) comparable; // 强制类型转换
      return dataString.getMillisSinceEpoch(); // 获取自纪元以来的毫秒数
    } // DateString处理结束
    throw new AssertionError("unsupported type: " + comparable.getClass()); // 抛出断言错误，表示遇到不支持的时间类型
  } // toLong方法结束

  /**
   * 提取给定节点的字面量值，假设它是日期时间类型的字面量，
   * 或者是在日期时间类型字面量之上的仅改变可空性的类型转换
   * 
   * 该方法用于从RexNode中提取时间字面量的值，支持以下情况：
   * 1. 直接的时间字面量(LITERAL)：TIMESTAMP、TIMESTAMP_WITH_LOCAL_TIME_ZONE、DATE
   * 2. 仅改变可空性的CAST：TIMESTAMP NOT NULL -> TIMESTAMP
   * 
   * 时间类型说明：
   * - TIMESTAMP：时间戳，包含日期和时间，不带时区信息
   * - TIMESTAMP_WITH_LOCAL_TIME_ZONE：本地时区时间戳，使用会话时区
   * - DATE：日期，只包含年月日，不包含时间
   * 
   * CAST处理逻辑：
   * - 正常情况下，所有CAST都会在常量折叠阶段被消除
   * - 但在使用HiveExecutor时，可能存在只改变可空性的CAST
   * - 例如：CAST(TIMESTAMP '2020-01-01 00:00:00' AS TIMESTAMP NULL)
   * - 这种CAST不改变实际值，只改变类型系统中的可空性标记
   * - 我们可以通过遍历这种"虚拟"CAST来获取实际的字面量值
   * 
   * 返回值说明：
   * - 返回毫秒级时间戳，自1970-01-01 00:00:00 UTC以来的毫秒数
   * - 如果节点不是时间字面量或无法提取值，返回null
   * 
   * @param node RexNode节点，可能是字面量或CAST表达式
   * @return 毫秒级时间戳；如果无法提取则返回null
   */
  protected static @Nullable Long literalValue(RexNode node) { // 提取字面量值的核心方法
    switch (node.getKind()) { // 根据节点类型进行分支处理
    case LITERAL: // 字面量节点
      switch (((RexLiteral) node).getTypeName()) { // 根据字面量的类型进行分支处理
      case TIMESTAMP: // 时间戳类型
      case TIMESTAMP_WITH_LOCAL_TIME_ZONE: // 本地时区时间戳类型
        TimestampString tsVal = ((RexLiteral) node).getValueAs(TimestampString.class); // 获取时间戳字符串值
        if (tsVal == null) { // 如果值为null
          return null; // 返回null
        } // null值检查结束
        return tsVal.getMillisSinceEpoch(); // 返回自纪元以来的毫秒数
      case DATE: // 日期类型
        DateString dateVal = ((RexLiteral) node).getValueAs(DateString.class); // 获取日期字符串值
        if (dateVal == null) { // 如果值为null
          return null; // 返回null
        } // null值检查结束
        return dateVal.getMillisSinceEpoch(); // 返回自纪元以来的毫秒数
      default: // 其他不支持的时间类型
        break; // 跳出内层switch
      } // 字面量类型分支结束
      break; // 跳出外层switch
    case CAST: // 类型转换节点
      // Normally all CASTs are eliminated by now by constant reduction. // 正常情况下，所有CAST都会在常量折叠阶段被消除
      // But when HiveExecutor is used there may be a cast that changes only // 但在使用HiveExecutor时，可能存在只改变可空性的CAST
      // nullability, from TIMESTAMP NOT NULL literal to TIMESTAMP literal. // 例如从TIMESTAMP NOT NULL字面量转换为TIMESTAMP字面量
      // We can handle that case by traversing the dummy CAST. // 我们可以通过遍历这种"虚拟"CAST来处理这种情况
      assert node instanceof RexCall; // 断言节点是RexCall类型
      final RexCall call = (RexCall) node; // 强制类型转换
      final RexNode operand = call.getOperands().get(0); // 获取CAST的操作数
      final RelDataType callType = call.getType(); // 获取CAST后的类型
      final RelDataType operandType = operand.getType(); // 获取操作数的类型
      if (operand.getKind() == SqlKind.LITERAL // 如果操作数是字面量
          && callType.getSqlTypeName() == operandType.getSqlTypeName() // 且SQL类型名称相同
          && (callType.getSqlTypeName() == SqlTypeName.DATE // 且是日期类型
              || callType.getSqlTypeName() == SqlTypeName.TIMESTAMP // 或时间戳类型
              || callType.getSqlTypeName() == SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE) // 或本地时区时间戳类型
          && callType.isNullable() // 且转换后类型可空
          && !operandType.isNullable()) { // 但操作数类型不可空
        return literalValue(operand); // 递归提取操作数的字面量值
      } // 仅改变可空性的CAST处理结束
      break; // 跳出switch
    default: // 其他不支持的节点类型
      break; // 跳出switch
    } // switch分支结束
    return null; // 返回null表示无法提取字面量值
  } // literalValue方法结束

  /**
   * 从时间单位推断时间粒度
   * 
   * 该方法支持从两种SQL函数中推断时间粒度：
   * 1. FLOOR(<time> TO <timeunit>)：向下取整到指定时间单位
   *    例如：FLOOR(timestamp TO DAY) 将时间戳向下取整到天
   * 2. EXTRACT(<timeunit> FROM <time>)：提取时间单位的值
   *    例如：EXTRACT(YEAR FROM timestamp) 提取年份
   * 
   * 时间单位说明：
   * - 支持的时间单位：YEAR, QUARTER, MONTH, WEEK, DAY, HOUR, MINUTE, SECOND
   * - 这些时间单位对应Druid的粒度类型
   * 
   * 时区处理：
   * - 对于DATE和TIMESTAMP类型，使用UTC时区
   * - 对于TIMESTAMP_WITH_LOCAL_TIME_ZONE类型，使用传入的时区参数
   * - Druid需要时区信息来正确处理时间粒度计算
   * 
   * 操作数位置：
   * - EXTRACT函数：第一个操作数是时间单位，第二个操作数是时间值
   * - FLOOR函数：第一个操作数是时间值，第二个操作数是时间单位
   * 
   * 使用场景：
   * - 在生成Druid查询时，将SQL的时间聚合函数转换为Druid的粒度
   * - 用于GROUP BY时间维度时确定聚合粒度
   * 
   * @param node RexNode节点，表示FLOOR或EXTRACT函数调用
   * @param timeZone 时区字符串，用于TIMESTAMP_WITH_LOCAL_TIME_ZONE类型
   * @return Granularity对象，表示Druid的时间粒度；如果无法推断则返回null
   */
  public static @Nullable Granularity extractGranularity(RexNode node, String timeZone) { // 从时间单位推断粒度的方法
    final int valueIndex; // 用于存储时间值操作数索引的变量
    final int flagIndex; // 用于存储时间单位操作数索引的变量

    if (TimeExtractionFunction.isValidTimeExtract(node)) { // 如果节点是有效的EXTRACT函数
      flagIndex = 0; // EXTRACT函数中，时间单位是第一个操作数
      valueIndex = 1; // EXTRACT函数中，时间值是第二个操作数
    } else if (TimeExtractionFunction.isValidTimeFloor(node)) { // 如果节点是有效的FLOOR函数
      valueIndex = 0; // FLOOR函数中，时间值是第一个操作数
      flagIndex = 1; // FLOOR函数中，时间单位是第二个操作数
    } else { // 如果节点既不是EXTRACT也不是FLOOR函数
      // We can only infer granularity from floor and extract. // 我们只能从floor和extract函数推断粒度
      return null; // 返回null表示无法推断
    } // 函数类型判断结束
    final RexCall call = (RexCall) node; // 将节点转换为RexCall类型
    final RexNode value = call.operands.get(valueIndex); // 获取时间值操作数
    final RexLiteral flag = (RexLiteral) call.operands.get(flagIndex); // 获取时间单位操作数
    final TimeUnitRange timeUnit = // 提取时间单位枚举值
        requireNonNull((TimeUnitRange) flag.getValue()); // 获取并确保时间单位不为null

    final RelDataType valueType = value.getType(); // 获取时间值的类型
    if (valueType.getSqlTypeName() == SqlTypeName.DATE // 如果是日期类型
        || valueType.getSqlTypeName() == SqlTypeName.TIMESTAMP) { // 或时间戳类型
      // We use 'UTC' for date/timestamp type as Druid needs timezone information // 对于日期/时间戳类型使用UTC时区，因为Druid需要时区信息
      return Granularities.createGranularity(timeUnit, "UTC"); // 创建使用UTC时区的粒度对象
    } else if (valueType.getSqlTypeName() == SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE) { // 如果是本地时区时间戳类型
      return Granularities.createGranularity(timeUnit, timeZone); // 创建使用传入时区的粒度对象
    } // 时间类型判断结束
    // Type not recognized // 类型无法识别
    return null; // 返回null表示无法推断粒度
  } // extractGranularity方法结束

  /**
   * 将Druid粒度类型转换为ISO8601时间段格式
   * 
   * 该方法将Druid的Granularity.Type枚举转换为ISO8601标准的时间段字符串格式。
   * ISO8601时间段格式使用PnYnMnDTnHnMnS的语法表示时间段。
   * 
   * 转换规则：
   * - SECOND -> PT1S (1秒)
   * - MINUTE -> PT1M (1分钟)
   * - HOUR -> PT1H (1小时)
   * - DAY -> P1D (1天)
   * - WEEK -> P1W (1周)
   * - MONTH -> P1M (1月)
   * - QUARTER -> P3M (3个月)
   * - YEAR -> P1Y (1年)
   * 
   * ISO8601时间段格式说明：
   * - P表示时间段(Period)的开始
   * - T表示时间部分的开始(小时、分钟、秒)
   * - n表示数量
   * - Y=年, M=月, W=周, D=天, H=小时, M=分钟, S=秒
   * 
   * 使用场景：
   * - 在与支持ISO8601标准的系统集成时使用
   * - 用于需要标准时间段格式的API调用
   * - Joda Time库使用此格式进行时间段解析和格式化
   * 
   * @param type Druid粒度类型，表示时间聚合的粒度
   * @return ISO8601格式的时间段字符串；如果类型未知则返回null
   */
  public static @Nullable String toISOPeriodFormat(Granularity.Type type) { // 将粒度类型转换为ISO时间段格式的方法
    switch (type) { // 根据粒度类型进行分支处理
    case SECOND: // 秒级粒度
      return Period.seconds(1).toString(); // 返回PT1S，表示1秒
    case MINUTE: // 分钟级粒度
      return Period.minutes(1).toString(); // 返回PT1M，表示1分钟
    case HOUR: // 小时级粒度
      return Period.hours(1).toString(); // 返回PT1H，表示1小时
    case DAY: // 天级粒度
      return Period.days(1).toString(); // 返回P1D，表示1天
    case WEEK: // 周级粒度
      return Period.weeks(1).toString(); // 返回P1W，表示1周
    case MONTH: // 月级粒度
      return Period.months(1).toString(); // 返回P1M，表示1月
    case QUARTER: // 季度粒度
      return Period.months(3).toString(); // 返回P3M，表示3个月
    case YEAR: // 年级粒度
      return Period.years(1).toString(); // 返回P1Y，表示1年
    default: // 其他未知的粒度类型
      return null; // 返回null表示无法转换
    } // switch分支结束
  } // toISOPeriodFormat方法结束

  /**
   * 将Calcite的时间单位范围转换为Druid的粒度类型
   * 
   * 该方法在Calcite和Druid的时间单位之间进行映射转换。
   * Calcite使用TimeUnitRange枚举表示时间单位，Druid使用Granularity.Type枚举表示粒度。
   * 
   * 映射关系：
   * - TimeUnitRange.YEAR -> Granularity.Type.YEAR (年)
   * - TimeUnitRange.QUARTER -> Granularity.Type.QUARTER (季度)
   * - TimeUnitRange.MONTH -> Granularity.Type.MONTH (月)
   * - TimeUnitRange.WEEK -> Granularity.Type.WEEK (周)
   * - TimeUnitRange.DAY -> Granularity.Type.DAY (天)
   * - TimeUnitRange.HOUR -> Granularity.Type.HOUR (小时)
   * - TimeUnitRange.MINUTE -> Granularity.Type.MINUTE (分钟)
   * - TimeUnitRange.SECOND -> Granularity.Type.SECOND (秒)
   * 
   * 使用场景：
   * - 在生成Druid查询时，将Calcite的时间单位转换为Druid的粒度
   * - 用于时间聚合操作，如GROUP BY YEAR(timestamp)
   * - 确保时间聚合在两个系统中的语义一致
   * 
   * @param timeUnit Calcite时间单位枚举，表示时间聚合的粒度
   * @return Druid粒度类型；如果时间单位为null或不支持则返回null
   */
  public static Granularity.@Nullable Type toDruidGranularity(TimeUnitRange timeUnit) { // 将Calcite时间单位转换为Druid粒度的方法
    if (timeUnit == null) { // 如果时间单位为null
      return null; // 返回null
    } // null检查结束
    switch (timeUnit) { // 根据时间单位进行分支处理
    case YEAR: // 年级时间单位
      return Granularity.Type.YEAR; // 返回Druid的年级粒度
    case QUARTER: // 季度时间单位
      return Granularity.Type.QUARTER; // 返回Druid的季度粒度
    case MONTH: // 月级时间单位
      return Granularity.Type.MONTH; // 返回Druid的月级粒度
    case WEEK: // 周级时间单位
      return Granularity.Type.WEEK; // 返回Druid的周级粒度
    case DAY: // 天级时间单位
      return Granularity.Type.DAY; // 返回Druid的天级粒度
    case HOUR: // 小时级时间单位
      return Granularity.Type.HOUR; // 返回Druid的小时级粒度
    case MINUTE: // 分钟级时间单位
      return Granularity.Type.MINUTE; // 返回Druid的分钟级粒度
    case SECOND: // 秒级时间单位
      return Granularity.Type.SECOND; // 返回Druid的秒级粒度
    default: // 其他不支持的时间单位
      return null; // 返回null表示无法转换
    } // switch分支结束
  } // toDruidGranularity方法结束
} // DruidDateTimeUtils类结束
