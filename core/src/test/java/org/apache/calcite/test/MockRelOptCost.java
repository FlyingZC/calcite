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
package org.apache.calcite.test; // 包声明，该类属于org.apache.calcite.test测试包

import org.apache.calcite.plan.RelOptCost; // 导入RelOptCost接口，该接口定义了关系表达式代价评估的标准

/**
 * MockRelOptCost类：RelOptCost接口的模拟实现类
 * 作用：在单元测试中提供一个简单的关系操作符代价模拟对象，所有方法返回固定的默认值（通常是0、false或true）
 * 应用场景：当测试不需要真实的代价计算逻辑时，可以使用这个Mock对象来替代复杂的代价实现
 * 特点：
 *   1. 所有代价指标（CPU、IO、行数）都返回0，表示零代价
 *   2. 所有比较操作返回固定值（isLe返回true表示小于等于，isLt返回false表示不小于）
 *   3. 所有算术操作（加、减、乘）都返回this，表示操作不改变对象
 *   4. equals总是返回true，表示所有MockRelOptCost对象都相等
 * TODO: constructors for various scenarios
 */
public class MockRelOptCost implements RelOptCost { // MockRelOptCost类实现RelOptCost接口，提供模拟的代价评估功能
  //~ Methods ---------------------------------------------------------------- // 方法区域分隔符，用于代码组织

  @Override public boolean equals(Object obj) { // 重写Object类的equals方法，用于比较两个对象是否相等
    return this == obj // 如果是同一个对象引用，直接返回true
        || obj instanceof MockRelOptCost // 或者如果obj是MockRelOptCost类的实例
        && equals((MockRelOptCost) obj); // 则调用类型特定的equals方法进行比较
  } // equals方法结束，返回比较结果

  @Override public int hashCode() { // 重写Object类的hashCode方法，用于支持哈希表等数据结构
    return 1; // 返回固定的哈希码值1，所有MockRelOptCost对象都有相同的哈希码
  } // hashCode方法结束

  public double getCpu() { // 实现RelOptCost接口的getCpu方法，获取CPU代价
    return 0; // 返回0，表示模拟的CPU代价为0（零代价）
  } // getCpu方法结束

  public boolean isInfinite() { // 实现RelOptCost接口的isInfinite方法，判断代价是否为无穷大
    return false; // 返回false，表示代价不是无穷大（有限代价）
  } // isInfinite方法结束

  public double getIo() { // 实现RelOptCost接口的getIo方法，获取I/O代价
    return 0; // 返回0，表示模拟的I/O代价为0（零代价）
  } // getIo方法结束

  public boolean isLe(RelOptCost cost) { // 实现RelOptCost接口的isLe方法，判断当前代价是否小于等于给定代价
    return true; // 返回true，表示当前代价总是小于等于任何其他代价（模拟最小代价）
  } // isLe方法结束

  public boolean isLt(RelOptCost cost) { // 实现RelOptCost接口的isLt方法，判断当前代价是否严格小于给定代价
    return false; // 返回false，表示当前代价从不严格小于其他代价（模拟相等或大于）
  } // isLt方法结束

  public double getRows() { // 实现RelOptCost接口的getRows方法，获取行数（影响代价的行数）
    return 0; // 返回0，表示模拟的行数为0（零行）
  } // getRows方法结束

  public boolean equals(RelOptCost cost) { // 实现RelOptCost接口的equals方法，比较两个RelOptCost对象是否相等
    return true; // 返回true，表示所有MockRelOptCost对象都相等（模拟相等）
  } // equals方法结束

  public boolean isEqWithEpsilon(RelOptCost cost) { // 实现RelOptCost接口的isEqWithEpsilon方法，使用epsilon容差判断是否相等
    return true; // 返回true，表示在允许的误差范围内总是相等（模拟相等）
  } // isEqWithEpsilon方法结束

  public RelOptCost minus(RelOptCost cost) { // 实现RelOptCost接口的minus方法，计算当前代价减去给定代价
    return this; // 返回this，表示减法操作不改变对象（模拟零代价减法）
  } // minus方法结束

  public RelOptCost multiplyBy(double factor) { // 实现RelOptCost接口的multiplyBy方法，将当前代价乘以给定因子
    return this; // 返回this，表示乘法操作不改变对象（模拟零代价乘法）
  } // multiplyBy方法结束

  public double divideBy(RelOptCost cost) { // 实现RelOptCost接口的divideBy方法，计算当前代价除以给定代价的比率
    return 1; // 返回1，表示除法结果总是1（模拟相等代价的除法）
  } // divideBy方法结束

  public RelOptCost plus(RelOptCost cost) { // 实现RelOptCost接口的plus方法，计算当前代价加上给定代价
    return this; // 返回this，表示加法操作不改变对象（模拟零代价加法）
  } // plus方法结束

  public String toString() { // 重写Object类的toString方法，返回对象的字符串表示
    return "MockRelOptCost(0)"; // 返回固定的字符串表示，显示类名和零代价值
  } // toString方法结束
} // MockRelOptCost类定义结束
