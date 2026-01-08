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
package org.apache.calcite.plan.visualizer; // 定义包名，该类属于org.apache.calcite.plan.visualizer包，用于可视化相关的辅助功能

import org.apache.calcite.rel.RelNode; // 导入Calcite核心接口RelNode，代表关系代数表达式中的一个节点

import org.checkerframework.checker.nullness.qual.Nullable; // 导入注解，用于标记可能为null的类型

import java.util.Collections; // 导入Collections工具类，用于创建不可修改的集合
import java.util.LinkedHashMap; // 导入LinkedHashMap，保持插入顺序的Map实现
import java.util.List; // 导入List接口，表示有序集合
import java.util.Map; // 导入Map接口，表示键值对映射
import java.util.Objects; // 导入Objects工具类，用于对象操作，如equals和null检查

/**
 * Helper class to create the node update. // 辅助类，用于创建节点更新操作
 * 
 * 该类是Calcite查询计划可视化框架的核心辅助类，主要负责跟踪和管理RelNode节点属性的更新状态。
 * 它维护了节点的当前状态（state）和待更新的变更（update），提供了属性更新、状态查询和变更获取等功能。
 * 
 * 核心功能：
 * 1. 跟踪RelNode节点的属性值变化
 * 2. 记录属性的历史状态和当前状态
 * 3. 提供增量更新机制，只记录实际发生变化的属性
 * 4. 支持获取并重置更新记录，用于可视化刷新
 * 
 * 使用场景：
 * 在查询计划优化过程中，优化器会不断修改RelNode树的结构和属性。
 * 该类用于记录这些变化，使得可视化组件能够高效地更新显示，而不需要重新渲染整个树。
 * 
 * 设计模式：
 * - 使用状态模式：维护state（当前状态）和update（待更新状态）
 * - 使用快照模式：getAndResetUpdate()方法提供变更快照并重置
 * - 使用不可变对象：getState()返回不可修改的Map，保证状态安全性
 */
class NodeUpdateHelper { // 定义NodeUpdateHelper类，用于管理RelNode节点的更新状态

  private final String key; // 成员变量：节点的唯一标识符，用于在可视化中区分不同的节点，通常是节点的ID或名称
  private final @Nullable RelNode rel; // 成员变量：关联的RelNode对象，可能为null，表示该辅助类管理的实际关系代数节点
  private final NodeUpdateInfo state; // 成员变量：节点的当前状态，存储所有属性的当前值，使用LinkedHashMap保持属性顺序
  private @Nullable NodeUpdateInfo update = null; // 成员变量：待更新的变更记录，存储自上次getAndResetUpdate()调用以来的所有属性变更，初始为null表示无变更

  NodeUpdateHelper(String key, @Nullable RelNode rel) { // 构造方法：创建NodeUpdateHelper实例，初始化节点标识和关联的RelNode
    this.key = key; // 将传入的key参数赋值给成员变量key，设置节点的唯一标识符
    this.rel = rel; // 将传入的rel参数赋值给成员变量rel，设置关联的RelNode对象
    this.state = new NodeUpdateInfo(); // 创建新的NodeUpdateInfo对象作为初始状态，此时状态为空Map
  } // 构造方法结束，完成NodeUpdateHelper实例的初始化

  String getKey() { // 方法：获取节点的唯一标识符
    return key; // 返回成员变量key的值，即节点的标识符
  } // 方法结束，返回节点标识符

  @Nullable RelNode getRel() { // 方法：获取关联的RelNode对象，可能返回null
    return this.rel; // 返回成员变量rel的值，即关联的RelNode对象
  } // 方法结束，返回关联的RelNode对象

  void updateAttribute(final String attr, final Object newValue) { // 方法：更新指定属性的值，attr是属性名，newValue是新值
    if (Objects.equals(newValue, state.get(attr))) { // 检查新值是否与当前状态中的值相等，使用Objects.equals避免NPE
      return; // 如果值没有变化，直接返回，不执行任何操作
    } // 条件判断结束，跳过无意义的更新

    state.put(attr, newValue); // 将新值更新到state中，记录属性的当前状态

    if (update == null) { // 检查update对象是否为null，即是否是第一次更新
      update = new NodeUpdateInfo(); // 如果是第一次更新，创建新的NodeUpdateInfo对象来存储变更
    } // 条件判断结束，确保update对象已初始化

    if (newValue instanceof List // 检查新值是否为List类型
        && ((List<?>) newValue).isEmpty() // 并且List为空
        && !update.containsKey(attr)) { // 并且update中不包含该属性
      return; // 如果新值是空列表且之前没有记录过该属性，则跳过记录，避免不必要的空列表更新
    } // 条件判断结束，过滤掉空列表的首次更新

    update.put(attr, newValue); // 将属性名和新值存入update中，记录本次变更
  } // 方法结束，完成属性更新和变更记录

  boolean isEmptyUpdate() { // 方法：检查是否有待处理的更新
    return this.update == null || update.isEmpty(); // 返回true如果update为null或update为空Map，表示没有变更需要处理
  } // 方法结束，返回是否有更新的布尔值

  /**
   * Gets an object representing all the changes since the last call to this method. // 方法：获取自上次调用此方法以来的所有变更
   *
   * @return an object or null if there are no changes. // 返回值：包含所有变更的对象，如果没有变更则返回null
   */
  @Nullable Object getAndResetUpdate() { // 方法：获取并重置更新记录，返回自上次调用以来的所有变更
    if (isEmptyUpdate()) { // 检查是否有待处理的更新
      return null; // 如果没有更新，返回null
    } // 条件判断结束，处理无更新情况
    NodeUpdateInfo update = this.update; // 保存当前update对象的引用到局部变量
    this.update = null; // 将成员变量update重置为null，清空变更记录，为下一次更新做准备
    return update; // 返回保存的update对象，包含所有变更
  } // 方法结束，返回变更对象并重置内部状态

  Map<String, Object> getState() { // 方法：获取节点的当前状态，返回不可修改的Map
    return Collections.unmodifiableMap(this.state); // 使用Collections.unmodifiableMap包装state，返回不可修改的Map视图，防止外部修改
  } // 方法结束，返回不可修改的状态Map

  /**
   * Get the current value for the attribute. // 方法：获取指定属性的当前值
   */
  @Nullable Object getValue(final String attr) { // 方法：获取指定属性名的当前值，可能返回null
    return this.state.get(attr); // 从state中获取属性名对应的值，如果属性不存在则返回null
  } // 方法结束，返回属性的当前值

  /**
   * Type alias. // 内部类：类型别名，简化LinkedHashMap<String, Object>的使用
   * 
   * 该内部类继承自LinkedHashMap<String, Object>，用于存储节点属性信息。
   * 使用LinkedHashMap而不是HashMap是为了保持属性的插入顺序，这在可视化中很重要，
   * 可以确保属性按照一定的顺序显示，提升用户体验。
   * 
   * 为什么使用内部类：
   * 1. 提供类型别名，使代码更简洁易读
   * 2. 封装属性存储的细节，便于未来扩展
   * 3. 保持NodeUpdateHelper类的内聚性
   */
  private static class NodeUpdateInfo extends LinkedHashMap<String, Object> { // 内部类：继承LinkedHashMap，用于存储节点属性信息
  } // 内部类结束
} // 类结束
