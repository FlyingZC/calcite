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
 */ // Apache许可证声明，说明此代码遵循Apache 2.0许可证
package org.apache.calcite.rel; // 声明包名，表示此接口属于org.apache.calcite.rel包，即Calcite关系表达式相关类包

import org.apache.calcite.plan.RelMultipleTrait; // 导入RelMultipleTrait接口，表示这是一个多重特征接口，可以包含多个排序特征
import org.apache.calcite.util.ImmutableIntList; // 导入ImmutableIntList工具类，用于创建不可变的整数列表

import java.util.List; // 导入Java标准库的List接口，用于存储排序字段列表

/**
 * Description of the physical ordering of a relational expression.
 * 描述关系表达式的物理排序特征
 * 
 * <p>An ordering consists of a list of one or more column ordinals and the
 * direction of the ordering.
 * 一个排序由一个或多个列的序号及其排序方向组成
 * 
 * 此接口定义了Calcite中关系表达式的排序特征，用于描述数据的物理排序方式
 * 例如：按第0列升序、第1列降序排序，可以表示为[(0, ASC), (1, DESC)]
 * 
 * RelCollation是RelMultipleTrait的子接口，表示一个关系表达式可以具有多个排序特征
 * 在查询优化过程中，排序特征用于判断是否可以利用已有的排序，避免不必要的排序操作
 * 
 * 典型使用场景：
 * 1. 在Order By操作中定义排序规则
 * 2. 在Join操作中定义连接键的排序
 * 3. 在聚合操作中定义分组键的排序
 * 4. 在索引扫描中定义索引的排序顺序
 * 
 * 实现类通常为RelCollationImpl，它包含一个RelFieldCollation列表
 */
public interface RelCollation extends RelMultipleTrait { // 定义RelCollation接口，继承自RelMultipleTrait，表示这是一个排序特征接口
  //~ Methods ---------------------------------------------------------------- // 方法区域分隔符，表示下面是方法定义部分

  /**
   * Returns the ordinals and directions of the columns in this ordering.
   * 返回此排序中各列的序号和排序方向
   * 
   * 此方法返回一个RelFieldCollation列表，每个RelFieldCollation包含：
   * - fieldIndex: 列的序号（从0开始）
   * - direction: 排序方向（ASC升序、DESC降序、NULLS_FIRST、NULLS_LAST等）
   * 
   * 例如：如果排序规则为"ORDER BY col1 ASC, col2 DESC"
   * 返回的列表可能是：[(0, ASC), (1, DESC)]
   * 
   * @return RelFieldCollation列表，描述每列的排序规则
   */
  List<RelFieldCollation> getFieldCollations(); // 定义抽象方法，获取排序字段列表，返回包含字段序号和排序方向的列表

  /**
   * Returns the ordinals of the key columns.
   * 返回关键列的序号列表
   * 
   * 此方法是一个默认实现方法，从getFieldCollations()中提取出所有列的序号
   * 忽略排序方向，只返回列的序号
   * 
   * 例如：如果getFieldCollations()返回[(0, ASC), (1, DESC), (2, ASC)]
   * 此方法返回[0, 1, 2]
   * 
   * 实现逻辑：
   * 1. 获取排序字段列表
   * 2. 创建一个与列表长度相同的整数数组
   * 3. 遍历列表，提取每个字段的序号
   * 4. 将数组转换为ImmutableIntList返回
   * 
   * @return ImmutableIntList，包含所有排序列的序号
   */
  default ImmutableIntList getKeys() { // 定义默认方法，获取排序键的列序号列表
    final List<RelFieldCollation> collations = getFieldCollations(); // 调用getFieldCollations()获取排序字段列表，声明为final表示不可重新赋值
    final int size = collations.size(); // 获取列表大小，即排序字段的个数
    final int[] keys = new int[size]; // 创建整数数组，用于存储列序号，长度为排序字段个数
    for (int i = 0; i < size; i++) { // 遍历排序字段列表，从0到size-1
      keys[i] = collations.get(i).getFieldIndex(); // 获取第i个排序字段的列序号，存入keys数组
    } // 循环结束，所有列序号已提取到keys数组
    return ImmutableIntList.of(keys); // 将keys数组转换为不可变的ImmutableIntList并返回
  } // 方法结束
} // 接口定义结束
