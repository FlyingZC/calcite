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
 */ // Apache许可证声明，说明代码的版权和使用条款
package org.apache.calcite.plan; // 声明包名，该接口位于org.apache.calcite.plan包下

/**
 * Cost model for query planning.
 */ // 这是一个查询规划的成本模型接口的Javadoc注释，用于说明接口的用途
public interface RelOptCostFactory { // 定义公共接口RelOptCostFactory（关系式优化成本工厂），用于创建成本对象的工厂接口
  /**
   * Creates a cost object.
   */ // 创建成本对象的方法的Javadoc注释
  RelOptCost makeCost(double rowCount, double cpu, double io); // 根据行数、CPU成本和I/O成本创建一个RelOptCost成本对象，参数：rowCount表示预估的行数，cpu表示CPU计算成本，io表示I/O成本

  /**
   * Creates a cost object representing an enormous non-infinite cost.
   */ // 创建一个巨大的但非无限的成本对象的方法的Javadoc注释
  RelOptCost makeHugeCost(); // 创建一个表示巨大成本（但不是无限大）的RelOptCost对象，用于标记某个执行计划的成本极高但不完全不可能

  /**
   * Creates a cost object representing infinite cost.
   */ // 创建一个无限大的成本对象的方法的Javadoc注释
  RelOptCost makeInfiniteCost(); // 创建一个表示无限大成本的RelOptCost对象，用于标记某个执行计划完全不可行或被排除

  /**
   * Creates a cost object representing a small positive cost.
   */ // 创建一个极小的正数成本对象的方法的Javadoc注释
  RelOptCost makeTinyCost(); // 创建一个表示极小正数成本的RelOptCost对象，用于标记某个操作成本接近于零但不为零

  /**
   * Creates a cost object representing zero cost.
   */ // 创建一个零成本对象的方法的Javadoc注释
  RelOptCost makeZeroCost(); // 创建一个表示零成本的RelOptCost对象，用于标记某个操作没有任何成本消耗
} // 接口定义结束
