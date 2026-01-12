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
package org.apache.calcite.linq4j.test; // 声明包名，该测试类位于org.apache.calcite.linq4j.test包下

import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口，表示可枚举的集合，支持LINQ风格的查询操作
import org.apache.calcite.linq4j.Enumerator; // 导入Enumerator接口，用于遍历Enumerable集合中的元素
import org.apache.calcite.linq4j.ExtendedEnumerable; // 导入ExtendedEnumerable接口，扩展了Enumerable，提供额外的查询方法如correlateJoin
import org.apache.calcite.linq4j.JoinType; // 导入JoinType枚举，定义连接类型：INNER、LEFT、SEMI、ANTI等
import org.apache.calcite.linq4j.Linq4j; // 导入Linq4j工具类，提供创建Enumerable的静态工厂方法
import org.apache.calcite.linq4j.function.Function1; // 导入Function1函数式接口，表示接受一个参数的函数
import org.apache.calcite.linq4j.function.Function2; // 导入Function2函数式接口，表示接受两个参数的函数

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList，用于创建不可变的列表

import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，用于标记测试方法

import java.util.ArrayList; // 导入ArrayList，动态数组实现
import java.util.List; // 导入List接口，表示有序集合

import static org.junit.jupiter.api.Assertions.assertArrayEquals; // 导入断言方法，用于比较数组是否相等

/**
 * Tests {@link ExtendedEnumerable#correlateJoin(JoinType, Function1, Function2)}.
 * 测试类：用于测试ExtendedEnumerable接口中的correlateJoin方法
 * correlateJoin是一种相关连接（Correlated Join），类似于SQL中的相关子查询
 * 它允许对左侧集合的每个元素，根据该元素的值动态生成右侧集合，然后进行连接操作
 * 支持四种连接类型：INNER（内连接）、LEFT（左连接）、SEMI（半连接）、ANTI（反连接）
 */
class CorrelateJoinTest { // 测试类定义，用于测试correlateJoin功能
  // 静态常量：定义一个函数式接口实现，用于将两个Integer值组合成一个Integer数组
  // 这个函数作为correlateJoin的结果选择器，将左右两侧的值组合成输出结果
  // Function2<Integer, Integer, Integer[]>表示接受两个Integer参数，返回Integer数组
  static final Function2<Integer, Integer, Integer[]> SELECT_BOTH = // 声明静态常量函数，用于选择并组合左右两侧的值
      (v0, v1) -> new Integer[]{v0, v1}; // Lambda表达式：接收两个参数v0和v1，返回包含这两个值的数组

  @Test void testInner() { // 测试方法：测试内连接（INNER JOIN）功能
    testJoin(JoinType.INNER, new Integer[][]{ // 调用testJoin方法，传入INNER连接类型和期望结果
        {2, 20},  // 期望结果：2与20匹配（2*10=20）
        {3, -30}, // 期望结果：3与-30匹配（3*-10=-30）
        {3, -60}, // 期望结果：3与-60匹配（3*-20=-60）
        {20, 200}, // 期望结果：20与200匹配（20*10=200）
        {30, -300}, // 期望结果：30与-300匹配（30*-10=-300）
        {30, -600}}); // 期望结果：30与-600匹配（30*-20=-600）
  } // 内连接只返回左右两侧都匹配的记录，1和10没有匹配所以不出现

  @Test void testLeft() { // 测试方法：测试左连接（LEFT JOIN）功能
    testJoin(JoinType.LEFT, new Integer[][]{ // 调用testJoin方法，传入LEFT连接类型和期望结果
        {1, null}, // 期望结果：1没有匹配，右侧为null（左连接保留左侧所有记录）
        {2, 20},  // 期望结果：2与20匹配
        {3, -30}, // 期望结果：3与-30匹配
        {3, -60}, // 期望结果：3与-60匹配
        {10, null}, // 期望结果：10没有匹配，右侧为null
        {20, 200}, // 期望结果：20与200匹配
        {30, -300}, // 期望结果：30与-300匹配
        {30, -600}}); // 期望结果：30与-600匹配
  } // 左连接返回左侧所有记录，即使右侧没有匹配，右侧值设为null

  @Test void testSemi() { // 测试方法：测试半连接（SEMI JOIN）功能
    testJoin(JoinType.SEMI, new Integer[][]{ // 调用testJoin方法，传入SEMI连接类型和期望结果
        {2, null}, // 期望结果：2有匹配，但只保留左侧值，右侧为null
        {3, null}, // 期望结果：3有匹配，但只保留左侧值，右侧为null
        {20, null}, // 期望结果：20有匹配，但只保留左侧值，右侧为null
        {30, null}}); // 期望结果：30有匹配，但只保留左侧值，右侧为null
  } // 半连接只返回左侧有匹配的记录，右侧值设为null（不关心具体匹配值）

  @Test void testAnti() { // 测试方法：测试反连接（ANTI JOIN）功能
    testJoin(JoinType.ANTI, new Integer[][]{ // 调用testJoin方法，传入ANTI连接类型和期望结果
        {1, null}, // 期望结果：1没有匹配，保留左侧记录
        {10, null}}); // 期望结果：10没有匹配，保留左侧记录
  } // 反连接只返回左侧没有匹配的记录，右侧值设为null

  public void testJoin(JoinType joinType, Integer[][] expected) { // 测试方法：通用的连接测试方法，接受连接类型和期望结果
    Enumerable<Integer[]> join = // 声明一个Integer数组的可枚举集合，用于存储连接结果
        Linq4j.asEnumerable(ImmutableList.of(1, 2, 3, 10, 20, 30)) // 创建左侧集合：包含6个整数1,2,3,10,20,30的不可变列表
            .correlateJoin(joinType, a0 -> { // 调用correlateJoin方法进行相关连接，传入连接类型和相关子查询函数
              // 相关子查询函数：根据左侧集合的每个元素a0，动态生成右侧集合
              if (a0 == 1 || a0 == 10) { // 如果左侧元素是1或10
                return Linq4j.emptyEnumerable(); // 返回空集合，表示这些元素没有匹配
              } // 1和10在右侧集合中没有对应元素
              if (a0 == 2 || a0 == 20) { // 如果左侧元素是2或20
                return Linq4j.singletonEnumerable(a0 * 10); // 返回只包含一个元素的集合，值为a0*10
              } // 2返回{20}，20返回{200}
              if (a0 == 3 || a0 == 30) { // 如果左侧元素是3或30
                return Linq4j.asEnumerable( // 返回包含两个元素的集合
                    ImmutableList.of(-a0 * 10, -a0 * 20)); // 值为-a0*10和-a0*20
              } // 3返回{-30, -60}，30返回{-300, -600}
              throw new IllegalArgumentException( // 如果遇到未处理的值，抛出异常
                  "Unexpected input " + a0); // 异常信息包含未预期的输入值
            }, SELECT_BOTH); // 结果选择器：将左右两侧的值组合成数组输出
    for (int i = 0; i < 2; i++) { // 循环两次，确保枚举器可以被重复使用
      Enumerator<Integer[]> e = join.enumerator(); // 获取连接结果的枚举器，用于遍历结果
      checkResults(e, expected); // 检查实际结果是否与期望结果匹配
      e.close(); // 关闭枚举器，释放资源
    } // 重复测试确保枚举器的可重用性和结果的稳定性
  } // testJoin方法完成对correlateJoin功能的测试

  private void checkResults(Enumerator<Integer[]> e, Integer[][] expected) { // 私有方法：检查枚举器的结果是否与期望结果匹配
    List<Integer[]> res = new ArrayList<>(); // 创建一个动态列表，用于存储实际结果
    while (e.moveNext()) { // 遍历枚举器，moveNext()移动到下一个元素，如果有更多元素返回true
      res.add(e.current()); // 将当前元素添加到结果列表中
    } // 遍历完成后，res包含所有连接结果
    Integer[][] actual = res.toArray(new Integer[res.size()][]); // 将列表转换为二维数组，便于比较
    assertArrayEquals(expected, actual); // 断言期望结果与实际结果相等
  } // checkResults方法完成结果的验证
} // CorrelateJoinTest类结束
