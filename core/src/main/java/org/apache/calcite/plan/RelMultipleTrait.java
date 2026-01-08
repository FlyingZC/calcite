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
package org.apache.calcite.plan; // 声明包名，该类位于org.apache.calcite.plan包下，这是Calcite查询优化器的核心包之一

/**
 * Trait for which a given relational expression can have multiple values. // 特征接口，表示一个关系表达式可以具有多个值的特征
 *
 * <p>The most common example is sorted-ness (collation). The TIME dimension // 最常见的例子是排序特征（排序规则）
 * table might be sorted by [year, month, date] and also by [time_id]. // 例如：时间维度表可能按[年, 月, 日]排序，也可能按[时间ID]排序
 */
public interface RelMultipleTrait // 定义RelMultipleTrait接口，这是一个公共接口，表示可以有多个值的特征
    extends RelTrait, Comparable<RelMultipleTrait> { // 该接口继承自RelTrait（关系特征基接口）和Comparable（可比较接口），使其具有特征属性和可比较能力
  /** Returns whether this trait is satisfied by every instance of the trait // 返回该特征是否满足特征的所有实例（包括自身）
   * (including itself). */ // 即该特征是否是"最顶层的"或"最宽松的"特征，能够满足所有其他特征实例
  boolean isTop(); // 抽象方法，返回布尔值，用于判断当前特征是否是顶层特征（Top特征），在特征层次结构中，Top特征是最宽松的约束
}
