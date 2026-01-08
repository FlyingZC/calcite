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
package org.apache.calcite.rel.convert;  // 包声明：定义该类所属的包，位于rel.convert子包中，专门处理关系表达式转换

import org.apache.calcite.plan.Convention;  // 导入Convention类，用于表示关系表达式的调用约定或规范
import org.apache.calcite.plan.ConventionTraitDef;  // 导入ConventionTraitDef类，定义Convention特征的元数据
import org.apache.calcite.plan.RelOptCluster;  // 导入RelOptCluster类，表示关系表达式优化集群，包含上下文信息
import org.apache.calcite.plan.RelOptPlanner;  // 导入RelOptPlanner类，表示关系表达式优化器
import org.apache.calcite.plan.RelTraitSet;  // 导入RelTraitSet类，表示关系表达式的特征集合
import org.apache.calcite.rel.RelNode;  // 导入RelNode接口，表示关系表达式树的节点
import org.apache.calcite.util.Util;  // 导入Util工具类，提供通用工具方法

import java.util.List;  // 导入List接口，用于处理列表集合

/**
 * <code>NoneConverter</code> converts a plan from <code>inConvention</code> to
 * {@link org.apache.calcite.plan.Convention#NONE}.
 * NoneConverter类的作用：将关系表达式从任意调用约定转换为NONE调用约定
 * NONE是Calcite中的一种特殊调用约定，表示关系表达式不绑定到任何特定的物理实现
 * 它是所有调用约定的超集，可以作为不同数据源或计算引擎之间的桥梁
 * 这个转换器主要用于在优化过程中将特定约定的关系表达式转换为通用形式
 */
public class NoneConverter extends ConverterImpl {  // 定义NoneConverter类，继承自ConverterImpl基类，实现从任意约定到NONE约定的转换
  //~ Constructors -----------------------------------------------------------  // 构造方法部分的分隔标记

  public NoneConverter(  // 构造方法：创建一个NoneConverter实例，用于将子节点转换为NONE约定
      RelOptCluster cluster,  // 参数cluster：关系表达式优化集群，包含查询的上下文信息和共享资源
      RelNode child) {  // 参数child：需要被转换的子关系表达式节点
    super(  // 调用父类ConverterImpl的构造方法
        cluster,  // 将优化集群传递给父类
        ConventionTraitDef.INSTANCE,  // 传递Convention特征定义，用于处理调用约定特征
        cluster.traitSetOf(Convention.NONE),  // 创建包含NONE约定的特征集合，表示转换后的目标特征
        child);  // 传递子节点，将被转换为NONE约定
  }  // 构造方法结束

  //~ Methods ----------------------------------------------------------------  // 方法部分的分隔标记


  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {  // 重写copy方法：创建当前节点的副本，可以指定新的特征集合和输入
    assert traitSet.comprises(Convention.NONE);  // 断言：确保新的特征集合包含NONE约定，这是NoneConverter的基本要求
    return new NoneConverter(  // 返回一个新的NoneConverter实例
        getCluster(),  // 获取当前节点的集群，传递给新的转换器实例
        sole(inputs));  // 从输入列表中获取唯一的子节点（NoneConverter只有一个输入）
  }  // copy方法结束

  public static void init(RelOptPlanner planner) {  // 静态初始化方法：向优化器注册转换规则，但这个类实际上不注册任何规则
    // we can't convert from any conventions, therefore no rules to register  // 注释：我们不能从任何约定进行转换，因此不需要注册规则
    Util.discard(planner);  // 使用Util.discard方法显式忽略planner参数，避免编译器警告，表示该参数暂时不使用
  }  // init方法结束
}  // NoneConverter类定义结束
