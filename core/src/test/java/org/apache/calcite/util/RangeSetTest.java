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
package org.apache.calcite.util;
import org.apache.calcite.linq4j.Ord;
import org.apache.calcite.rel.externalize.RelJson;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeSystem;
import org.apache.calcite.sql.type.BasicSqlType;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.test.Matchers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.apache.calcite.test.Matchers.isRangeSet;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasToString;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static java.util.Arrays.asList;

/**
 * Unit test for {@link RangeSets} and other utilities relating to Guava
 * {@link Range} and {@link RangeSet}.
 */
// RangeSetTest类：RangeSets工具类和Guava Range、RangeSet相关工具的单元测试类
// 主要测试功能包括：范围的序列化和反序列化、范围集合的减法操作、点判断、区间判断、点数统计、映射和遍历、哈希码计算、比较操作、打印功能等
class RangeSetTest {

  /** Tests {@link org.apache.calcite.rel.externalize.RelJson#toJson(Range)}
   * and {@link RelJson#rangeFromJson(List, RelDataType)}. */
  // 测试RelJson的toJson和rangeFromJson方法，验证Range和RangeSet的序列化和反序列化功能
  @Test void testRangeSetSerializeDeserialize() {
    RelJson relJson = RelJson.create(); // 创建RelJson对象用于序列化和反序列化
    RelDataType integerType = new BasicSqlType(RelDataTypeSystem.DEFAULT, SqlTypeName.INTEGER); // 创建INTEGER类型
    RelDataType decimalType = new BasicSqlType(RelDataTypeSystem.DEFAULT, SqlTypeName.DECIMAL); // 创建DECIMAL类型
    RelDataType doubleType = new BasicSqlType(RelDataTypeSystem.DEFAULT, SqlTypeName.DOUBLE); // 创建DOUBLE类型

    final Range<Integer> integerPoint = Range.singleton(Integer.valueOf(0)); // 创建Integer类型的单点范围{0}
    final Range<BigDecimal> bigDecimalPoint = Range.singleton(BigDecimal.valueOf(0)); // 创建BigDecimal类型的单点范围{0}
    final Range<Double> doublePoint = Range.singleton(Double.valueOf(0)); // 创建Double类型的单点范围{0}
    final Range<BigDecimal> closedRange1 =
        Range.closed(BigDecimal.valueOf(0), BigDecimal.valueOf(5)); // 创建闭区间[0, 5]
    final Range<BigDecimal> closedRange2 =
        Range.closed(BigDecimal.valueOf(6), BigDecimal.valueOf(10)); // 创建闭区间[6, 10]

    final Range<BigDecimal> gt1 = Range.greaterThan(BigDecimal.valueOf(7)); // 创建开区间(7, +∞)
    final Range<BigDecimal> al1 = Range.atLeast(BigDecimal.valueOf(8)); // 创建半开区间[8, +∞)
    final Range<BigDecimal> lt1 = Range.lessThan(BigDecimal.valueOf(4)); // 创建开区间(-∞, 4)
    final Range<BigDecimal> am1 = Range.atMost(BigDecimal.valueOf(3)); // 创建半开区间(-∞, 3]

    // Test serialize/deserialize Range
    //    Integer Point
    //    Deserializes as BigDecimal because Calcite uses BigDecimal for exact numerics
    assertThat(RelJson.rangeFromJson(relJson.toJson(integerPoint), integerType), // 测试Integer单点范围的序列化和反序列化，反序列化为BigDecimal因为Calcite使用BigDecimal表示精确数值
        is(bigDecimalPoint));
    //    BigDecimal Point
    assertThat(RelJson.rangeFromJson(relJson.toJson(bigDecimalPoint), decimalType), // 测试BigDecimal单点范围的序列化和反序列化
        is(bigDecimalPoint));
    //    Double Point
    assertThat(RelJson.rangeFromJson(relJson.toJson(doublePoint), doubleType), // 测试Double单点范围的序列化和反序列化
        is(doublePoint));
    //    Closed Range
    assertThat(RelJson.rangeFromJson(relJson.toJson(closedRange1), decimalType), // 测试闭区间的序列化和反序列化
        is(closedRange1));
    //    Open Range
    assertThat(RelJson.rangeFromJson(relJson.toJson(gt1), decimalType), is(gt1)); // 测试大于区间的序列化和反序列化
    assertThat(RelJson.rangeFromJson(relJson.toJson(al1), decimalType), is(al1)); // 测试大于等于区间的序列化和反序列化
    assertThat(RelJson.rangeFromJson(relJson.toJson(lt1), decimalType), is(lt1)); // 测试小于区间的序列化和反序列化
    assertThat(RelJson.rangeFromJson(relJson.toJson(am1), decimalType), is(am1)); // 测试小于等于区间的序列化和反序列化
    // Test closed single RangeSet
    final RangeSet<BigDecimal> closedRangeSet = ImmutableRangeSet.of(closedRange1); // 创建包含单个闭区间的RangeSet
    assertThat(RelJson.rangeSetFromJson(relJson.toJson(closedRangeSet), decimalType), // 测试单个闭区间RangeSet的序列化和反序列化
        is(closedRangeSet));
    // Test complex RangeSets
    final RangeSet<BigDecimal> complexClosedRangeSet1 = // 创建包含两个不相交闭区间的复杂RangeSet
        ImmutableRangeSet.<BigDecimal>builder()
            .add(closedRange1)
            .add(closedRange2)
            .build();
    assertThat(
        RelJson.rangeSetFromJson(relJson.toJson(complexClosedRangeSet1), decimalType), // 测试复杂闭区间RangeSet的序列化和反序列化
        is(complexClosedRangeSet1));
    final RangeSet<BigDecimal> complexClosedRangeSet2 = // 创建包含不同类型区间的复杂RangeSet
        ImmutableRangeSet.<BigDecimal>builder()
            .add(gt1)
            .add(am1)
            .build();
    assertThat(RelJson.rangeSetFromJson(relJson.toJson(complexClosedRangeSet2), decimalType), // 测试混合区间类型RangeSet的序列化和反序列化
        is(complexClosedRangeSet2));

    // Test None and All
    final RangeSet<BigDecimal> setNone = ImmutableRangeSet.of(); // 创建空RangeSet（不包含任何范围）
    final RangeSet<BigDecimal> setAll = setNone.complement(); // 创建全集RangeSet（包含所有可能的值）
    assertThat(RelJson.rangeSetFromJson(relJson.toJson(setNone), decimalType), is(setNone)); // 测试空RangeSet的序列化和反序列化
    assertThat(RelJson.rangeSetFromJson(relJson.toJson(setAll), decimalType), is(setAll)); // 测试全集RangeSet的序列化和反序列化
  }

  /** Tests {@link RangeSets#minus(RangeSet, Range)}. */
  // 测试RangeSets.minus方法，验证从RangeSet中减去一个Range的功能
  @Test void testRangeSetMinus() {
    final RangeSet<Integer> setNone = ImmutableRangeSet.of(); // 创建空RangeSet
    final RangeSet<Integer> setAll = setNone.complement(); // 创建全集RangeSet
    final RangeSet<Integer> setGt2 = ImmutableRangeSet.of(Range.greaterThan(2)); // 创建大于2的RangeSet
    final RangeSet<Integer> setGt1 = ImmutableRangeSet.of(Range.greaterThan(1)); // 创建大于1的RangeSet
    final RangeSet<Integer> setGe1 = ImmutableRangeSet.of(Range.atLeast(1)); // 创建大于等于1的RangeSet
    final RangeSet<Integer> setGt0 = ImmutableRangeSet.of(Range.greaterThan(0)); // 创建大于0的RangeSet
    final RangeSet<Integer> setComplex = // 创建复杂的RangeSet，包含闭区间、单点和开区间
        ImmutableRangeSet.<Integer>builder()
            .add(Range.closed(0, 2))
            .add(Range.singleton(3))
            .add(Range.greaterThan(5))
            .build();
    assertThat(setComplex, isRangeSet("[[0..2], [3..3], (5..+\u221e)]")); // 验证复杂RangeSet的格式

    assertThat(RangeSets.minus(setAll, Range.singleton(1)), // 从全集RangeSet中减去单点1，得到两个不相交的开区间
        isRangeSet("[(-\u221e..1), (1..+\u221e)]"));
    assertThat(RangeSets.minus(setNone, Range.singleton(1)), is(setNone)); // 从空RangeSet中减去任意点，结果仍为空
    assertThat(RangeSets.minus(setGt2, Range.singleton(1)), is(setGt2)); // 从(2,+∞)中减去1，结果不变
    assertThat(RangeSets.minus(setGt1, Range.singleton(1)), is(setGt1)); // 从(1,+∞)中减去1，结果不变
    assertThat(RangeSets.minus(setGe1, Range.singleton(1)), is(setGt1)); // 从[1,+∞)中减去1，结果变为(1,+∞)
    assertThat(RangeSets.minus(setGt0, Range.singleton(1)), // 从(0,+∞)中减去1，结果分为(0,1)和(1,+∞)
        isRangeSet("[(0..1), (1..+\u221e)]"));
    assertThat(RangeSets.minus(setComplex, Range.singleton(1)), // 从复杂RangeSet中减去单点1，[0,2]被分割为[0,1)和(1,2]
        isRangeSet("[[0..1), (1..2], [3..3], (5..+\u221e)]"));
    assertThat(RangeSets.minus(setComplex, Range.singleton(2)), // 从复杂RangeSet中减去单点2，[0,2]变为[0,2)
        isRangeSet("[[0..2), [3..3], (5..+\u221e)]"));
    assertThat(RangeSets.minus(setComplex, Range.singleton(3)), // 从复杂RangeSet中减去单点3，单点3被完全移除
        isRangeSet("[[0..2], (5..+\u221e)]"));
    assertThat(RangeSets.minus(setComplex, Range.open(2, 3)), // 从复杂RangeSet中减去开区间(2,3)，(2,3)不包含任何点，结果不变
        isRangeSet("[[0..2], [3..3], (5..+\u221e)]"));
    assertThat(RangeSets.minus(setComplex, Range.closed(2, 3)), // 从复杂RangeSet中减去闭区间[2,3]，移除[0,2]的2和单点3
        isRangeSet("[[0..2), (5..+\u221e)]"));
    assertThat(RangeSets.minus(setComplex, Range.closed(2, 7)), // 从复杂RangeSet中减去闭区间[2,7]，移除[0,2]的2、单点3和(5,+∞)的(5,7]
        isRangeSet("[[0..2), (7..+\u221e)]"));
  }

  /** Tests {@link RangeSets#isPoint(Range)}. */
  // 测试RangeSets.isPoint方法，验证判断一个Range是否是单点（只包含一个值）的功能
  @Test void testRangeSetIsPoint() {
    assertThat(RangeSets.isPoint(Range.singleton(0)), is(true)); // 单点范围{0}是点
    assertThat(RangeSets.isPoint(Range.closed(0, 0)), is(true)); // 闭区间[0,0]是点
    assertThat(RangeSets.isPoint(Range.closed(0, 1)), is(false)); // 闭区间[0,1]不是点（包含多个值）
    assertThat(RangeSets.isPoint(Range.openClosed(0, 1)), is(false)); // 半开半闭区间(0,1]不是点

    // The integer range '0 > x and x < 2' contains only one valid integer
    // but it is not a point.
    assertThat(RangeSets.isPoint(Range.open(0, 2)), is(false)); // 开区间(0,2)虽然只包含整数1，但本身不是点

    assertThat(RangeSets.isPoint(Range.lessThan(0)), is(false)); // (-∞,0)不是点
    assertThat(RangeSets.isPoint(Range.atMost(0)), is(false)); // (-∞,0]不是点
    assertThat(RangeSets.isPoint(Range.greaterThan(0)), is(false)); // (0,+∞)不是点
    assertThat(RangeSets.isPoint(Range.atLeast(0)), is(false)); // [0,+∞)不是点

    // Test situation where endpoints of closed range are equal under
    // Comparable.compareTo but not T.equals.
    final BigDecimal one = new BigDecimal("1"); // 创建BigDecimal "1"
    final BigDecimal onePoint = new BigDecimal("1.0"); // 创建BigDecimal "1.0"，数值相等但表示不同
    assertThat(RangeSets.isPoint(Range.closed(one, onePoint)), is(true)); // 闭区间[1, 1.0]是点，因为compareTo返回0
  }

  /** Tests ranges with a data type that implements {@link Comparable}
   * but is not consistent with {@code equals}.
   *
   * <p>Per {@link Comparable}:
   *
   * <blockquote>
   * Virtually all Java core classes that implement Comparable have natural
   * orderings that are consistent with equals. One exception is
   * {@link BigDecimal}, whose natural ordering equates {@code BigDecimal}
   * objects with equal numerical values and different representations
   * (such as 4.0 and 4.00). For {@link BigDecimal#equals} to return true,
   * the representation and numerical value of the two {@code BigDecimal}
   * objects must be the same.
   * </blockquote>
   */
  // 测试实现了Comparable但不与equals一致的数据类型的Range行为
  // BigDecimal是特例：compareTo基于数值比较，而equals基于精确表示比较
  @Test void testNotConsistentWithEquals() {
    final BigDecimal one = new BigDecimal("1"); // 创建BigDecimal "1"
    final BigDecimal onePoint = new BigDecimal("1.0"); // 创建BigDecimal "1.0"，数值相等但表示不同
    final BigDecimal two = BigDecimal.valueOf(2); // 创建BigDecimal 2
    assertThat(one.equals(onePoint), is(false)); // "1"不等于"1.0"（表示不同）
    assertThat(onePoint.equals(one), is(false)); // "1.0"不等于"1"（表示不同）
    assertThat(one.compareTo(onePoint), is(0)); // "1"与"1.0"比较返回0（数值相等）
    assertThat(onePoint.equals(one), is(false)); // 再次验证equals
    assertThat(one.equals(BigDecimal.ONE), is(true)); // "1"等于BigDecimal.ONE（都是"1"）
    assertThat(onePoint.equals(BigDecimal.ONE), is(false)); // "1.0"不等于BigDecimal.ONE（表示不同）
    assertThat(RangeSets.isPoint(Range.closed(one, onePoint)), is(true)); // [1, 1.0]是点（基于compareTo）

    // Ranges (0, 1], [1.0, 2) merges to [0, 2).
    final Range<BigDecimal> range01 = Range.closed(BigDecimal.ZERO, one); // 创建闭区间[0, 1]
    final Range<BigDecimal> range01point = Range.closed(BigDecimal.ZERO, onePoint); // 创建闭区间[0, 1.0]
    final Range<BigDecimal> range1point2 = Range.closedOpen(onePoint, two); // 创建半开区间[1.0, 2)
    final Range<BigDecimal> range12 = Range.closedOpen(one, two); // 创建半开区间[1, 2)
    assertThrows(IllegalArgumentException.class, // 尝试添加重叠的Range会抛出异常
        () -> ImmutableRangeSet.<BigDecimal>builder()
            .add(range01point)
            .add(range12)
            .build());

    assertThat(RangeSets.compare(range01, range12), is(-1)); // [0,1] < [1,2)
    assertThat(RangeSets.compare(range12, range01), is(1)); // [1,2) > [0,1]
    assertThat(RangeSets.compare(range01, range01point), is(0)); // [0,1] == [0,1.0]（基于compareTo）
    assertThat(RangeSets.compare(range12, range1point2), is(0)); // [1,2) == [1.0,2)（基于compareTo）

    // Ranges are merged correctly.
    final ImmutableRangeSet<BigDecimal> rangeSet = // 使用unionOf合并Range，[0,1.0]和[1,2)合并为[0,2)
        ImmutableRangeSet.unionOf(asList(range01point, range12));
    final ImmutableRangeSet<BigDecimal> rangeSet2 = // 使用unionOf合并Range，[0,1]和[1,2)合并为[0,2)
        ImmutableRangeSet.unionOf(asList(range01, range12));
    final ImmutableRangeSet<BigDecimal> rangeSet3 = // 使用unionOf合并Range，[0,1]和[1.0,2)合并为[0,2)
        ImmutableRangeSet.unionOf(asList(range01, range1point2));
    assertThat(rangeSet.asRanges(), hasSize(1)); // 合并后只有一个Range
    assertThat(rangeSet, is(rangeSet2)); // 两种方式合并结果相同
    assertThat(rangeSet, is(rangeSet3)); // 三种方式合并结果相同

    // Check the Consumer mechanism; RangeSets.printer is a Consumer,
    // and it gives the Consumer mechanism a pretty good workout.
    final Function<RangeSet<BigDecimal>, String> f = // 创建函数，将RangeSet转换为字符串
        rs -> {
          final StringBuilder buf = new StringBuilder();
          final RangeSets.Consumer<BigDecimal> printer = // 创建printer Consumer
              RangeSets.printer(buf, StringBuilder::append);
          RangeSets.forEach(rs, printer); // 遍历RangeSet并使用printer打印
          return Matchers.sanitizeRangeSet(buf.toString());
        };
    final Function<Range<BigDecimal>, String> f2 = // 创建函数，将Range转换为字符串（先转为RangeSet）
        r -> f.apply(ImmutableRangeSet.of(r));
    assertThat(f.apply(rangeSet), is("[0..2)")); // 验证合并后的RangeSet字符串表示

    // If a closed range has bounds that are equal, Consumer should treat them
    // as singletons of the lower bound; but not if the bounds compareTo 0.
    assertThat(f2.apply(Range.singleton(onePoint)), is("1.0")); // 单点{1.0}打印为"1.0"
    assertThat(f2.apply(Range.closed(one, one)), is("1")); // [1,1]打印为"1"
    assertThat(f2.apply(Range.closed(one, onePoint)), is("[1..1.0]")); // [1,1.0]打印为"[1..1.0]"（equals不同）
    assertThat(f2.apply(Range.closed(onePoint, one)), is("[1.0..1]")); // [1.0,1]打印为"[1.0..1]"（equals不同）
    assertThat(f2.apply(Range.closed(onePoint, onePoint)), is("1.0")); // [1.0,1.0]打印为"1.0"
    assertThat(f2.apply(Range.closed(onePoint, two)), is("[1.0..2]")); // [1.0,2]打印为"[1.0..2]"

    // As for Consumer, now for Handler.
    // RangeSets.copy tests Handler pretty thoroughly.
    final Function<RangeSet<BigDecimal>, RangeSet<BigDecimal>> g = // 创建函数，复制RangeSet并乘以2
        rs -> RangeSets.copy(rs, v -> v.multiply(two));
    final Function<Range<BigDecimal>, RangeSet<BigDecimal>> g2 = // 创建函数，复制Range并乘以2（先转为RangeSet）
        r -> g.apply(ImmutableRangeSet.of(r));
    assertThat(g.apply(rangeSet), isRangeSet("[[0..4)]")); // [0,2)乘以2得到[0,4)

    assertThat(g2.apply(Range.singleton(onePoint)), isRangeSet("[[2.0..2.0]]")); // {1.0}乘以2得到{2.0}
    assertThat(g2.apply(Range.closed(one, one)), isRangeSet("[[2..2]]")); // [1,1]乘以2得到[2,2]
    assertThat(g2.apply(Range.closed(one, onePoint)), isRangeSet("[[2..2.0]]")); // [1,1.0]乘以2得到[2,2.0]
    assertThat(g2.apply(Range.closed(onePoint, one)), isRangeSet("[[2.0..2]]")); // [1.0,1]乘以2得到[2.0,2]
    assertThat(g2.apply(Range.closed(onePoint, onePoint)), // [1.0,1.0]乘以2得到[2.0,2.0]
        isRangeSet("[[2.0..2.0]]"));
    assertThat(g2.apply(Range.closed(onePoint, two)), isRangeSet("[[2.0..4]]")); // [1.0,2]乘以2得到[2.0,4]
  }

  /** Tests {@link RangeSets#isOpenInterval(RangeSet)}. */
  // 测试RangeSets.isOpenInterval方法，验证判断一个RangeSet是否是开区间（单个半无限区间）的功能
  @Test void testRangeSetIsOpenInterval() {
    final RangeSet<Integer> setGt0 = ImmutableRangeSet.of(Range.greaterThan(0)); // 创建(0,+∞)
    final RangeSet<Integer> setAl0 = ImmutableRangeSet.of(Range.atLeast(0)); // 创建[0,+∞)
    final RangeSet<Integer> setLt0 = ImmutableRangeSet.of(Range.lessThan(0)); // 创建(-∞,0)
    final RangeSet<Integer> setAm0 = ImmutableRangeSet.of(Range.atMost(0)); // 创建(-∞,0]

    assertThat(RangeSets.isOpenInterval(setGt0), is(true)); // (0,+∞)是开区间
    assertThat(RangeSets.isOpenInterval(setAl0), is(true)); // [0,+∞)是开区间
    assertThat(RangeSets.isOpenInterval(setLt0), is(true)); // (-∞,0)是开区间
    assertThat(RangeSets.isOpenInterval(setAm0), is(true)); // (-∞,0]是开区间

    final RangeSet<Integer> setNone = ImmutableRangeSet.of(); // 创建空RangeSet
    final RangeSet<Integer> multiRanges = ImmutableRangeSet.<Integer>builder() // 创建包含多个Range的RangeSet
        .add(Range.lessThan(0))
        .add(Range.greaterThan(3))
        .build();

    assertThat(RangeSets.isOpenInterval(setNone), is(false)); // 空RangeSet不是开区间
    assertThat(RangeSets.isOpenInterval(multiRanges), is(false)); // 多个Range的RangeSet不是开区间

    final RangeSet<Integer> open = ImmutableRangeSet.of(Range.open(0, 3)); // 创建(0,3)
    final RangeSet<Integer> closed = ImmutableRangeSet.of(Range.closed(0, 3)); // 创建[0,3]
    final RangeSet<Integer> openClosed = ImmutableRangeSet.of(Range.openClosed(0, 3)); // 创建(0,3]
    final RangeSet<Integer> closedOpen = ImmutableRangeSet.of(Range.closedOpen(0, 3)); // 创建[0,3)

    assertThat(RangeSets.isOpenInterval(open), is(false)); // (0,3)不是开区间（有限区间）
    assertThat(RangeSets.isOpenInterval(closed), is(false)); // [0,3]不是开区间（有限区间）
    assertThat(RangeSets.isOpenInterval(openClosed), is(false)); // (0,3]不是开区间（有限区间）
    assertThat(RangeSets.isOpenInterval(closedOpen), is(false)); // [0,3)不是开区间（有限区间）
  }

  /** Tests {@link RangeSets#countPoints(RangeSet)}. */
  // 测试RangeSets.countPoints方法，验证计算RangeSet中离散点数量的功能
  @Test void testRangeCountPoints() {
    final Fixture f = new Fixture();
    assertThat(RangeSets.countPoints(f.empty), is(0)); // 空RangeSet有0个点
    assertThat(RangeSets.countPoints(f.zeroRangeSet), is(1)); // {0}有1个点
    assertThat(RangeSets.countPoints(f.rangeSet), is(1)); // 包含{7}的RangeSet有1个点
    final ImmutableRangeSet<Integer> set = // 创建包含开区间和单点的RangeSet
        ImmutableRangeSet.<Integer>builder()
            .add(Range.singleton(0))
            .add(Range.open(1, 2))
            .add(Range.singleton(3))
            .add(Range.atLeast(4)).build();
    assertThat(RangeSets.countPoints(set), is(2)); // {0}和{3}是点，共2个点
    final ImmutableRangeSet<Integer> set2 = // 创建只包含开区间和半无限区间的RangeSet
        ImmutableRangeSet.<Integer>builder()
            .add(Range.open(1, 2))
            .add(Range.atLeast(4)).build();
    assertThat(RangeSets.countPoints(set2), is(0)); // 没有离散点，共0个点
  }

  /** Tests {@link RangeSets#map} and {@link RangeSets#forEach}. */
  // 测试RangeSets.map和RangeSets.forEach方法，验证Range的映射和遍历功能
  @Test void testRangeMap() {
    final StringBuilder sb = new StringBuilder();
    final RangeSets.Handler<Integer, StringBuilder> h = // 创建Handler实现，用于处理不同类型的Range并返回StringBuilder
        new RangeSets.Handler<Integer, StringBuilder>() {
          @Override public StringBuilder all() { // 处理全集Range
            return sb.append("all()");
          }

          @Override public StringBuilder atLeast(Integer lower) { // 处理[lower,+∞)区间
            return sb.append("atLeast(").append(lower).append(")");
          }

          @Override public StringBuilder atMost(Integer upper) { // 处理(-∞,upper]区间
            return sb.append("atMost(").append(upper).append(")");
          }

          @Override public StringBuilder greaterThan(Integer lower) { // 处理(lower,+∞)区间
            return sb.append("greaterThan(").append(lower).append(")");
          }

          @Override public StringBuilder lessThan(Integer upper) { // 处理(-∞,upper)区间
            return sb.append("lessThan(").append(upper).append(")");
          }

          @Override public StringBuilder singleton(Integer value) { // 处理单点{value}
            return sb.append("singleton(").append(value).append(")");
          }

          @Override public StringBuilder closed(Integer lower, Integer upper) { // 处理闭区间[lower,upper]
            return sb.append("closed(").append(lower).append(", ")
                .append(upper).append(")");
          }

          @Override public StringBuilder closedOpen(Integer lower, Integer upper) { // 处理半开区间[lower,upper)
            return sb.append("closedOpen(").append(lower).append(", ")
                .append(upper).append(")");
          }

          @Override public StringBuilder openClosed(Integer lower, Integer upper) { // 处理半开区间(lower,upper]
            return sb.append("openClosed(").append(lower).append(", ")
                .append(upper).append(")");
          }

          @Override public StringBuilder open(Integer lower, Integer upper) { // 处理开区间(lower,upper)
            return sb.append("open(").append(lower).append(", ")
                .append(upper).append(")");
          }
        };
    final RangeSets.Consumer<Integer> c = // 创建Consumer实现，用于遍历Range并处理不同类型
        new RangeSets.Consumer<Integer>() {
          @Override public void all() { // 处理全集Range
            sb.append("all()");
          }

          @Override public void atLeast(Integer lower) { // 处理[lower,+∞)区间
            sb.append("atLeast(").append(lower).append(")");
          }

          @Override public void atMost(Integer upper) { // 处理(-∞,upper]区间
            sb.append("atMost(").append(upper).append(")");
          }

          @Override public void greaterThan(Integer lower) { // 处理(lower,+∞)区间
            sb.append("greaterThan(").append(lower).append(")");
          }

          @Override public void lessThan(Integer upper) { // 处理(-∞,upper)区间
            sb.append("lessThan(").append(upper).append(")");
          }

          @Override public void singleton(Integer value) { // 处理单点{value}
            sb.append("singleton(").append(value).append(")");
          }

          @Override public void closed(Integer lower, Integer upper) { // 处理闭区间[lower,upper]
            sb.append("closed(").append(lower).append(", ")
                .append(upper).append(")");
          }

          @Override public void closedOpen(Integer lower, Integer upper) { // 处理半开区间[lower,upper)
            sb.append("closedOpen(").append(lower).append(", ")
                .append(upper).append(")");
          }

          @Override public void openClosed(Integer lower, Integer upper) { // 处理半开区间(lower,upper]
            sb.append("openClosed(").append(lower).append(", ")
                .append(upper).append(")");
          }

          @Override public void open(Integer lower, Integer upper) { // 处理开区间(lower,upper)
            sb.append("open(").append(lower).append(", ")
                .append(upper).append(")");
          }
        };
    final Fixture f = new Fixture();
    for (Range<Integer> range : f.ranges) { // 遍历所有测试Range
      RangeSets.map(range, h); // 使用Handler处理每个Range
    }
    assertThat(sb, hasToString(f.rangesString)); // 验证Handler处理结果

    sb.setLength(0); // 清空StringBuilder
    for (Range<Integer> range : f.ranges) { // 遍历所有测试Range
      RangeSets.forEach(range, c); // 使用Consumer处理每个Range
    }
    assertThat(sb, hasToString(f.rangesString)); // 验证Consumer处理结果

    // Use a smaller set of ranges that does not overlap
    sb.setLength(0); // 清空StringBuilder
    for (Range<Integer> range : f.disjointRanges) { // 遍历不相交的Range集合
      RangeSets.forEach(range, c); // 使用Consumer处理每个Range
    }
    assertThat(sb, hasToString(f.disjointRangesString)); // 验证Consumer处理结果

    // For a RangeSet consisting of disjointRanges the effect is the same,
    // but the ranges are sorted.
    sb.setLength(0); // 清空StringBuilder
    RangeSets.forEach(f.rangeSet, c); // 使用Consumer处理RangeSet（RangeSet会自动排序）
    assertThat(sb, hasToString(f.disjointRangesSortedString)); // 验证Consumer处理结果（已排序）
  }

  /** Tests that {@link RangeSets#hashCode(RangeSet)} returns the same result
   * as the hashCode of a list of the same ranges. */
  // 测试RangeSets.hashCode方法，验证其返回值与Range列表的hashCode相同
  @Test void testRangeSetHashCode() {
    final Fixture f = new Fixture();
    final int h = new ArrayList<>(f.rangeSet.asRanges()).hashCode(); // 计算Range列表的hashCode
    assertThat(RangeSets.hashCode(f.rangeSet), is(h)); // 验证ImmutableRangeSet的hashCode与列表相同
    assertThat(RangeSets.hashCode(f.treeRangeSet), is(h)); // 验证TreeRangeSet的hashCode与列表相同

    assertThat(RangeSets.hashCode(ImmutableRangeSet.<Integer>of()), // 验证空RangeSet的hashCode与空列表相同
        is(ImmutableList.of().hashCode()));
  }

  /** Tests {@link RangeSets#compare(Range, Range)}. */
  // 测试RangeSets.compare方法，验证Range之间的比较功能
  @Test void testRangeCompare() {
    final Fixture f = new Fixture();
    Ord.forEach(f.sortedRanges, (r0, i) -> // 遍历所有排序后的Range
        Ord.forEach(f.sortedRanges, (r1, j) -> { // 双重遍历，测试所有Range对的比较
          final String reason = "compare " + r0 + " to " + r1;
          assertThat(reason, RangeSets.compare(r0, r1), // 验证Range比较结果与索引比较一致
              is(Integer.compare(i, j)));
        }));
  }

  /** Tests {@link RangeSets#compare(RangeSet, RangeSet)}. */
  // 测试RangeSets.compare方法，验证RangeSet之间的比较功能
  @Test void testRangeSetCompare() {
    final Fixture f = new Fixture();
    assertThat(RangeSets.compare(f.rangeSet, f.treeRangeSet), is(0)); // 相同RangeSet的比较结果为0
    assertThat(RangeSets.compare(f.rangeSet, f.rangeSet), is(0)); // 自身比较结果为0
    assertThat(RangeSets.compare(f.treeRangeSet, f.rangeSet), is(0)); // 反向比较结果仍为0

    // empty range set collates before everything
    assertThat(RangeSets.compare(f.empty, f.treeRangeSet), is(-1)); // 空RangeSet排在非空RangeSet之前
    assertThat(RangeSets.compare(f.treeRangeSet, f.empty), is(1)); // 非空RangeSet排在空RangeSet之后
    assertThat(RangeSets.compare(f.empty, f.zeroRangeSet), is(-1)); // 空RangeSet排在{0}之前
    assertThat(RangeSets.compare(f.zeroRangeSet, f.empty), is(1)); // {0}排在空RangeSet之后

    // removing the first element (if it's not the only element)
    // makes a range set collate later
    final RangeSet<Integer> s2 = TreeRangeSet.create(f.treeRangeSet); // 复制treeRangeSet
    s2.asRanges().remove(Iterables.getFirst(s2.asRanges(), null)); // 移除第一个Range
    assertThat(RangeSets.compare(s2, f.treeRangeSet), is(1)); // 移除第一个Range后排在原RangeSet之后
    assertThat(RangeSets.compare(f.treeRangeSet, s2), is(-1)); // 原RangeSet排在移除后的RangeSet之前
    assertThat(RangeSets.compare(f.empty, s2), is(-1)); // 空RangeSet仍排在最前
    assertThat(RangeSets.compare(s2, f.empty), is(1)); // 移除后的RangeSet排在空RangeSet之后

    // removing the last element
    // makes a range set collate earlier
    final RangeSet<Integer> s3 = TreeRangeSet.create(f.treeRangeSet); // 复制treeRangeSet
    s3.asRanges().remove(Iterables.getLast(s3.asRanges(), null)); // 移除最后一个Range
    assertThat(RangeSets.compare(s3, f.treeRangeSet), is(-1)); // 移除最后一个Range后排在原RangeSet之前
    assertThat(RangeSets.compare(f.treeRangeSet, s3), is(1)); // 原RangeSet排在移除后的RangeSet之后
  }

  /** Tests {@link RangeSets#printer(StringBuilder, BiConsumer)}. */
  // 测试RangeSets.printer方法，验证Range的打印功能
  @Test void testRangePrint() {
    final Fixture f = new Fixture();

    // RangeSet's native printing; format used a unicode symbol up to 28.2, and
    // ".." 29.0 and later.
    final List<String> list = new ArrayList<>();
    f.ranges.forEach(r -> list.add(r.toString())); // 使用Guava原生toString方法
    final String expectedGuava28 = "[(-\u221e\u2025+\u221e), (-\u221e\u20253], " // Guava 28.2及之前版本的格式（使用unicode符号）
        + "[4\u2025+\u221e), (-\u221e\u20255), (6\u2025+\u221e), [7\u20257], "
        + "(8\u20259), (10\u202511], [12\u202513], [14\u202515)]";
    final String expectedGuava29 = "[(-\u221e..+\u221e), (-\u221e..3], " // Guava 29.0及之后版本的格式（使用".."）
        + "[4..+\u221e), (-\u221e..5), (6..+\u221e), [7..7], "
        + "(8..9), (10..11], [12..13], [14..15)]";
    assertThat(list, // 验证Guava原生打印结果
        hasToString(anyOf(is(expectedGuava28), is(expectedGuava29))));
    list.clear(); // 清空列表

    final StringBuilder sb = new StringBuilder();
    f.ranges.forEach(r -> { // 遍历所有Range
      RangeSets.forEach(r, RangeSets.printer(sb, StringBuilder::append)); // 使用RangeSets.printer打印
      list.add(sb.toString());
      sb.setLength(0); // 清空StringBuilder
    });
    // our format matches Guava's, except points ("7" vs "[7, 7]")
    final String expected2 = "[(-\u221e..+\u221e), (-\u221e..3], " // RangeSets.printer的格式，单点打印为"7"而非"[7..7]"
        + "[4..+\u221e), (-\u221e..5), (6..+\u221e), 7, "
        + "(8..9), (10..11], [12..13], [14..15)]";
    assertThat(list, hasToString(expected2)); // 验证RangeSets.printer打印结果
    list.clear(); // 清空列表
  }

  /** Data sets used by various tests. */
  // Fixture类：测试数据集，为各种测试提供预定义的Range和RangeSet数据
  static class Fixture {
    final ImmutableRangeSet<Integer> empty = ImmutableRangeSet.of(); // 空RangeSet

    final List<Range<Integer>> ranges = // 包含各种类型Range的列表，用于测试
        asList(Range.all(),
            Range.atMost(3),
            Range.atLeast(4),
            Range.lessThan(5),
            Range.greaterThan(6),
            Range.singleton(7),
            Range.open(8, 9),
            Range.openClosed(10, 11),
            Range.closed(12, 13),
            Range.closedOpen(14, 15));
    final String rangesString = "all()" // ranges列表对应的字符串表示（用于Handler和Consumer测试）
        + "atMost(3)"
        + "atLeast(4)"
        + "lessThan(5)"
        + "greaterThan(6)"
        + "singleton(7)"
        + "open(8, 9)"
        + "openClosed(10, 11)"
        + "closed(12, 13)"
        + "closedOpen(14, 15)";

    final List<Range<Integer>> sortedRanges = // 按比较规则排序的Range列表，用于测试Range比较
        asList(
            Range.lessThan(3),
            Range.atMost(3),
            Range.lessThan(5),
            Range.all(),
            Range.greaterThan(4),
            Range.atLeast(4),
            Range.greaterThan(6),
            Range.singleton(7),
            Range.open(8, 9),
            Range.openClosed(8, 9),
            Range.closedOpen(8, 9),
            Range.closed(8, 9),
            Range.openClosed(10, 11),
            Range.closed(12, 13),
            Range.closedOpen(14, 15));

    final List<Range<Integer>> disjointRanges = // 不相交的Range列表，用于构建RangeSet
        asList(Range.lessThan(5),
            Range.greaterThan(16),
            Range.singleton(7),
            Range.open(8, 9),
            Range.openClosed(10, 11),
            Range.closed(12, 13),
            Range.closedOpen(14, 15));

    final String disjointRangesString = "lessThan(5)" // disjointRanges列表对应的字符串表示（未排序）
        + "greaterThan(16)"
        + "singleton(7)"
        + "open(8, 9)"
        + "openClosed(10, 11)"
        + "closed(12, 13)"
        + "closedOpen(14, 15)";

    final String disjointRangesSortedString = "lessThan(5)" // disjointRanges排序后的字符串表示
        + "singleton(7)"
        + "open(8, 9)"
        + "openClosed(10, 11)"
        + "closed(12, 13)"
        + "closedOpen(14, 15)"
        + "greaterThan(16)";

    final RangeSet<Integer> rangeSet; // 由disjointRanges构建的ImmutableRangeSet
    final TreeRangeSet<Integer> treeRangeSet; // 由rangeSet构建的TreeRangeSet

    final RangeSet<Integer> zeroRangeSet = // 只包含单点0的RangeSet
        ImmutableRangeSet.of(Range.singleton(0));

    Fixture() { // Fixture构造方法，初始化所有测试数据
      final ImmutableRangeSet.Builder<Integer> builder = // 创建ImmutableRangeSet构建器
          ImmutableRangeSet.builder();
      disjointRanges.forEach(builder::add); // 添加所有不相交的Range
      rangeSet = builder.build(); // 构建ImmutableRangeSet
      treeRangeSet = TreeRangeSet.create(); // 创建TreeRangeSet
      treeRangeSet.addAll(rangeSet); // 将rangeSet的所有Range添加到treeRangeSet
    }
  }
}
