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
package org.apache.calcite.adapter.pig; // Pig适配器包，包含将Calcite查询转换为Pig Latin的适配器代码

import org.apache.calcite.sql.SqlKind; // 导入Calcite的SQL操作类型枚举，用于标识SQL函数的种类（如COUNT、SUM等）

/**
 * Supported Pig aggregate functions and their Calcite counterparts. The enum's // 支持的Pig聚合函数及其对应的Calcite函数。枚举的
 * name() is the same as the function's name in Pig Latin. // name()方法返回的值与Pig Latin中函数名称相同
 */ // 这个枚举类建立了Pig聚合函数与Calcite SQL函数之间的映射关系
public enum PigAggFunction { // 定义一个枚举类，表示Pig支持的聚合函数类型

  // 定义枚举常量：COUNT表示普通的COUNT函数（COUNT(column)），对应Calcite的SqlKind.COUNT，star参数为false表示不是COUNT(*)
  COUNT(SqlKind.COUNT, false), COUNT_STAR(SqlKind.COUNT, true); // COUNT_STAR表示COUNT(*)函数，对应Calcite的SqlKind.COUNT，star参数为true

  private final SqlKind calciteFunc; // 成员变量：存储对应的Calcite SQL函数类型，用于在Calcite和Pig之间进行函数映射
  private final boolean star; // 成员变量：标识是否为COUNT(*)这种带星号的聚合函数，true表示是COUNT(*)，false表示是普通COUNT(column)

  // 构造方法：接收一个Calcite函数类型，默认star参数为false（即不是COUNT(*)），内部调用双参数构造函数
  PigAggFunction(SqlKind calciteFunc) { // 单参数构造函数，用于创建非星号版本的聚合函数
    this(calciteFunc, false); // 调用双参数构造函数，将star参数默认设置为false
  }

  // 构造方法：接收Calcite函数类型和是否为星号函数的标志，初始化成员变量
  PigAggFunction(SqlKind calciteFunc, boolean star) { // 双参数构造函数，用于创建带星号标识的聚合函数
    this.calciteFunc = calciteFunc; // 将传入的Calcite函数类型赋值给成员变量calciteFunc
    this.star = star; // 将传入的星号标志赋值给成员变量star
  }

  // 静态工厂方法：根据Calcite函数类型和星号标志查找对应的PigAggFunction枚举实例
  public static PigAggFunction valueOf(SqlKind calciteFunc, boolean star) { // 根据Calcite的SQL函数类型和是否为星号函数来获取对应的Pig聚合函数枚举值
    for (PigAggFunction pigAggFunction : values()) { // 遍历枚举类中所有的枚举常量
      if (pigAggFunction.calciteFunc == calciteFunc && pigAggFunction.star == star) { // 检查当前枚举常量的calciteFunc和star是否与传入参数匹配
        return pigAggFunction; // 如果匹配，返回该枚举常量
      }
    }
    throw new IllegalArgumentException("Pig agg func for " + calciteFunc + " is not supported"); // 如果遍历完所有枚举常量都没有找到匹配项，抛出 IllegalArgumentException 异常，表示Pig不支持该聚合函数
  }
}
