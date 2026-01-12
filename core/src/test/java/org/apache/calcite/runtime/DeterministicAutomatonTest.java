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
package org.apache.calcite.runtime; // 声明包名，该测试类位于org.apache.calcite.runtime包下
import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest的is匹配器，用于断言值相等
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest的断言方法，用于验证测试结果
import static org.hamcrest.Matchers.hasSize; // 导入Hamcrest的hasSize匹配器，用于验证集合大小

/** Tests for the {@link DeterministicAutomaton}. */ // 类文档注释：这是DeterministicAutomaton（确定型自动机）的测试类
// 确定型自动机是Calcite运行时包中用于模式匹配的重要组件，它将非确定型自动机转换为确定型自动机
// 该类测试了各种模式转换为确定型自动机的正确性，包括重复、或、序列、可选、闭包等操作
class DeterministicAutomatonTest { // 测试类定义，类名为DeterministicAutomatonTest
  @Test void convertAutomaton() { // 测试方法1：测试将包含重复符号的模式转换为确定型自动机
    // 该方法测试一个重复1到2次符号"A"的模式（即"A"或"AA"）能否正确转换为确定型自动机
    final Pattern.PatternBuilder builder = Pattern.builder(); // 创建一个模式构建器，用于构建模式
    final Pattern pattern = builder.symbol("A") // 添加符号"A"到模式中
        .repeat(1, 2) // 设置符号"A"的重复次数为1到2次，意味着匹配"A"或"AA"
        .build(); // 构建模式对象
    final Automaton automaton = pattern.toAutomaton(); // 将模式转换为非确定型自动机

    final DeterministicAutomaton da = // 创建确定型自动机对象
        new DeterministicAutomaton(automaton); // 通过构造函数传入非确定型自动机，进行确定化转换

    assertThat(da.startState, // 断言验证确定型自动机的起始状态
        is( // 使用is匹配器验证期望值
            new DeterministicAutomaton.MultiState(new Automaton.State(0), // 起始状态应该是一个多重状态，包含状态0
                new Automaton.State(2)))); // 和状态2，这是确定化过程的结果

    // Result should have three states // 注释说明：结果应该有三个状态
    // 0 -A-> 1 -A-> 2 // 状态机转换路径：从状态0通过"A"转到状态1，再从状态1通过"A"转到状态2
    // 1 and 2 should be final // 状态1和状态2应该是接受状态（终态）
    assertThat(da.getTransitions(), hasSize(2)); // 断言验证转换关系集合的大小为2，即有2条转换边
    assertThat(da.getEndStates(), hasSize(2)); // 断言验证终态集合的大小为2，即有2个接受状态
  }

  @Test void convertAutomaton2() { // 测试方法2：测试将包含或操作的模式转换为确定型自动机
    // 该方法测试一个"A或B"的模式能否正确转换为确定型自动机
    final Pattern.PatternBuilder builder = Pattern.builder(); // 创建一个模式构建器
    final Pattern pattern = builder // 使用构建器构建模式
        .symbol("A") // 添加符号"A"
        .symbol("B") // 添加符号"B"
        .or() // 应用或操作，表示匹配"A"或"B"
        .build(); // 构建模式对象
    final Automaton automaton = pattern.toAutomaton(); // 将模式转换为非确定型自动机

    final DeterministicAutomaton da = // 创建确定型自动机对象
        new DeterministicAutomaton(automaton); // 传入非确定型自动机进行确定化转换

    // Result should have two transitions // 注释说明：结果应该有两个转换
    // 0 -A-> 1 // 从状态0通过"A"转到状态1
    //   -B-> // 从状态0通过"B"转到某个状态（隐含）
    // 1 should be final // 状态1应该是接受状态
    assertThat(da.getTransitions(), hasSize(2)); // 断言验证转换关系集合的大小为2
    assertThat(da.getEndStates(), hasSize(1)); // 断言验证终态集合的大小为1
  }

  @Test void convertAutomaton3() { // 测试方法3：测试将包含闭包操作的模式转换为确定型自动机
    // 该方法测试一个"A后跟零个或多个B"的模式（即"A"、"AB"、"ABB"、"ABBB"...）能否正确转换
    final Pattern.PatternBuilder builder = Pattern.builder(); // 创建一个模式构建器
    final Pattern pattern = builder // 使用构建器构建模式
        .symbol("A") // 添加符号"A"
        .symbol("B").star().seq() // 添加符号"B"，应用star操作（零次或多次），然后应用seq操作（序列化）
        .build(); // 构建模式对象
    final Automaton automaton = pattern.toAutomaton(); // 将模式转换为非确定型自动机

    final DeterministicAutomaton da = // 创建确定型自动机对象
        new DeterministicAutomaton(automaton); // 传入非确定型自动机进行确定化转换

    // Result should have two transitions // 注释说明：结果应该有两个转换（实际注释有误，应该是3个）
    // 0 -A-> 1 -B-> 2 (which again goes to 2 on a "B") // 状态转换路径：0通过"A"到1，1通过"B"到2，2通过"B"又回到2（循环）
    // 1 should be final // 状态1应该是接受状态（因为B可以出现零次）
    assertThat(da.getTransitions(), hasSize(3)); // 断言验证转换关系集合的大小为3（包括循环）
    assertThat(da.getEndStates(), hasSize(2)); // 断言验证终态集合的大小为2（状态1和状态2都是终态）
  }

  @Test void convertAutomaton4() { // 测试方法4：测试将包含可选操作和序列的模式转换为确定型自动机
    // 该方法测试一个"A后跟可选的B，再跟A"的模式（即"AA"或"ABA"）能否正确转换
    final Pattern.PatternBuilder builder = Pattern.builder(); // 创建一个模式构建器
    final Pattern pattern = builder // 使用构建器构建模式
        .symbol("A") // 添加符号"A"
        .symbol("B").optional().seq() // 添加符号"B"，应用optional操作（零次或一次），然后应用seq操作
        .symbol("A").seq() // 添加符号"A"，应用seq操作，形成序列
        .build(); // 构建模式对象
    final Automaton automaton = pattern.toAutomaton(); // 将模式转换为非确定型自动机

    final DeterministicAutomaton da = // 创建确定型自动机对象
        new DeterministicAutomaton(automaton); // 传入非确定型自动机进行确定化转换

    // Result should have four transitions and one end state // 注释说明：结果应该有4个转换和1个终态
    assertThat(da.getTransitions(), hasSize(4)); // 断言验证转换关系集合的大小为4
    assertThat(da.getEndStates(), hasSize(1)); // 断言验证终态集合的大小为1
  }
} // 类定义结束
