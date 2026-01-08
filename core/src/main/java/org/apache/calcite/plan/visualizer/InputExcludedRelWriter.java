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
package org.apache.calcite.plan.visualizer; // 包声明：该类位于org.apache.calcite.plan.visualizer包中，用于RelNode的可视化功能

import org.apache.calcite.rel.RelNode; // 导入RelNode类：Calcite中关系表达式的抽象基类，代表关系代数中的一个操作
import org.apache.calcite.rel.RelWriter; // 导入RelWriter接口：用于将RelNode的解释信息写入到某个输出目标
import org.apache.calcite.sql.SqlExplainLevel; // 导入SqlExplainLevel枚举：定义SQL解释的详细程度级别
import org.apache.calcite.util.Pair; // 导入Pair类：用于存储键值对的工具类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解：用于标记可能为null的值，进行静态空值检查

import java.util.LinkedHashMap; // 导入LinkedHashMap类：保持插入顺序的Map实现，用于存储RelNode的属性
import java.util.List; // 导入List接口：Java集合框架的列表接口
import java.util.Map; // 导入Map接口：Java集合框架的映射接口

/**
 * InputExcludedRelWriter类：RelWriter接口的一个实现，用于解释单个RelNode的属性信息
 * 该类的特点是：结果只包含RelNode自身的属性，但不解释其子节点（输入）
 * 这使得该类特别适合用于获取单个关系节点的摘要信息，而不需要递归遍历整个关系树
 *
 * 使用示例：
 * <pre>{@code
 * InputExcludedRelWriter relWriter = new InputExcludedRelWriter(); // 创建一个不包含输入的RelWriter实例
 * rel.explain(relWriter); // 调用RelNode的explain方法，将信息写入到relWriter中
 * String digest = relWriter.toString(); // 通过toString获取RelNode的属性摘要
 * }</pre>
 *
 * 应用场景：
 * 1. 当只需要获取单个RelNode的属性信息，而不关心其子节点时
 * 2. 在可视化工具中显示单个节点的详细信息
 * 3. 在RelNode的equals和hashCode方法中获取节点指纹
 * 4. 在RelNode的digest计算中排除子节点的影响
 *
 */
class InputExcludedRelWriter implements RelWriter { // 类定义：实现RelWriter接口，用于写入不包含输入的RelNode解释信息

  private final Map<String, @Nullable Object> values = new LinkedHashMap<>(); // 成员变量：使用LinkedHashMap存储RelNode的属性键值对，使用LinkedHashMap是为了保持属性的插入顺序，@Nullable Object表示值可能为null

  InputExcludedRelWriter() { // 构造方法：无参构造函数，创建一个空的InputExcludedRelWriter实例，初始化values为空的LinkedHashMap
  } // 构造方法结束


  @Override public void explain(RelNode rel, List<Pair<String, @Nullable Object>> valueList) { // 方法：重写RelWriter接口的explain方法，用于将RelNode的属性列表写入到values中，rel参数是要解释的RelNode对象（虽然方法参数中有rel，但实际不使用），valueList是属性键值对列表
    valueList.forEach(pair -> values.put(pair.left, pair.right)); // 遍历valueList中的每个键值对，将键（pair.left）和值（pair.right）存入values Map中，使用forEach进行批量插入，保持原有顺序
  } // explain方法结束

  @Override public SqlExplainLevel getDetailLevel() { // 方法：重写RelWriter接口的getDetailLevel方法，返回解释的详细程度级别
    return SqlExplainLevel.EXPPLAN_ATTRIBUTES; // 返回EXPPLAN_ATTRIBUTES级别，表示只解释属性，不包含输入信息，这是该类的核心特性：排除输入节点
  } // getDetailLevel方法结束

  @Override public RelWriter input(String term, RelNode input) { // 方法：重写RelWriter接口的input方法，用于处理RelNode的输入（子节点），term是输入的术语描述，input是输入的RelNode对象
    // do nothing, ignore input // 注释说明：不做任何操作，忽略输入，这是该类的关键行为，确保不处理子节点
    return this; // 返回this，支持链式调用，虽然不做任何操作，但为了保持RelWriter接口的契约，仍然返回自身
  } // input方法结束

  @Override public RelWriter item(String term, @Nullable Object value) { // 方法：重写RelWriter接口的item方法，用于添加一个属性项到values中，term是属性的键（名称），value是属性的值，可能为null
    this.values.put(term, value); // 将属性键值对存入values Map中，term作为键，value作为值
    return this; // 返回this，支持链式调用，允许连续调用item方法添加多个属性
  } // item方法结束

  @Override public RelWriter itemIf(String term, @Nullable Object value, boolean condition) { // 方法：重写RelWriter接口的itemIf方法，条件性地添加属性项，只有在condition为true时才添加，term是属性的键，value是属性的值，condition是条件标志
    if (condition) { // 判断条件是否为true
      this.values.put(term, value); // 如果条件满足，将属性键值对存入values Map中
    } // if语句结束
    return this; // 返回this，支持链式调用
  } // itemIf方法结束

  @Override public RelWriter done(RelNode node) { // 方法：重写RelWriter接口的done方法，表示完成对当前RelNode的写入，node参数是完成的RelNode对象（虽然参数中有node，但实际不使用）
    return this; // 返回this，支持链式调用，表示写入操作完成，可以进行下一步操作
  } // done方法结束

  @Override public boolean nest() { // 方法：重写RelWriter接口的nest方法，指示是否需要嵌套写入子节点
    return false; // 返回false，表示不需要嵌套，这是该类的核心特性之一，确保不会递归处理子节点
  } // nest方法结束

  @Override public String toString() { // 方法：重写Object类的toString方法，将values Map转换为字符串表示
    return values.toString(); // 调用values Map的toString方法，返回格式化的属性字符串，格式如：{key1=value1, key2=value2}
  } // toString方法结束
} // 类定义结束
