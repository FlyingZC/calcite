/*
 * Licensed to the Apache Software Foundation (ASF) under one or more  // Apache软件基金会许可证声明
 * contributor license agreements.  See the NOTICE file distributed with  // 贡献者许可证协议，详见分发的NOTICE文件
 * this work for additional information regarding copyright ownership.  // 关于版权所有权的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0  // ASF根据Apache 2.0许可证授权给你
 * (the "License"); you may not use this file except in compliance with  // 除非遵守许可证，否则不得使用此文件
 * the License.  You may obtain a copy of the License at  // 您可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0  // 许可证网址
 *
 * Unless required by applicable law or agreed to in writing, software  // 除非适用法律要求或书面同意
 * distributed under the License is distributed on an "AS IS" BASIS,  // 否则按"原样"分发，不提供任何明示或暗示的保证
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  // 不包含任何形式的保证或条件
 * See the License for the specific language governing permissions and  // 查看许可证以了解具体的权限和限制
 * limitations under the License.  // 许可证下的限制
 */
package org.apache.calcite.adapter.enumerable;  // 声明包名，属于Calcite的可枚举适配器包

import org.apache.calcite.plan.Convention;  // 导入Convention类，用于定义关系代数节点的调用约定
import org.apache.calcite.plan.RelTraitSet;  // 导入RelTraitSet类，用于存储关系节点的特征集合
import org.apache.calcite.rel.RelNode;  // 导入RelNode接口，表示关系代数树的节点
import org.apache.calcite.rel.convert.ConverterRule;  // 导入ConverterRule基类，用于定义转换规则的基类
import org.apache.calcite.rel.core.Intersect;  // 导入Intersect类，表示INTERSECT操作（集合交操作）
import org.apache.calcite.rel.logical.LogicalIntersect;  // 导入LogicalIntersect类，表示逻辑层面的INTERSECT操作

/**
 * Rule to convert a {@link LogicalIntersect} to an {@link EnumerableIntersect}.  // 这是一个规则类，用于将逻辑INTERSECT节点转换为可枚举INTERSECT节点
 * You may provide a custom config to convert other nodes that extend {@link Intersect}.  // 您可以提供自定义配置来转换其他继承自Intersect的节点
 *
 * @see EnumerableRules#ENUMERABLE_INTERSECT_RULE  // 参见EnumerableRules中的ENUMERABLE_INTERSECT_RULE常量
 */
class EnumerableIntersectRule extends ConverterRule {  // 定义EnumerableIntersectRule类，继承自ConverterRule转换规则基类
  /** Default configuration. */  // 默认配置的Javadoc注释
  public static final Config DEFAULT_CONFIG = Config.INSTANCE  // 声明静态常量DEFAULT_CONFIG，表示默认配置，使用Config.INSTANCE作为基础配置
      .withConversion(LogicalIntersect.class, Convention.NONE,  // 配置转换规则：从LogicalIntersect类，输入约定为Convention.NONE（无约定）
          EnumerableConvention.INSTANCE, "EnumerableIntersectRule")  // 转换目标为EnumerableConvention.INSTANCE，规则名称为"EnumerableIntersectRule"
      .withRuleFactory(EnumerableIntersectRule::new);  // 设置规则工厂，使用方法引用创建EnumerableIntersectRule实例

  /** Called from the Config. */  // 从Config调用的构造方法的Javadoc注释
  protected EnumerableIntersectRule(Config config) {  // 定义受保护的构造方法，接收Config参数
    super(config);  // 调用父类ConverterRule的构造方法，传入config配置
  }

  @Override public RelNode convert(RelNode rel) {  // 重写convert方法，将关系节点转换为可枚举节点
    final Intersect intersect = (Intersect) rel;  // 将输入的RelNode强制转换为Intersect类型，并声明为final常量
    final EnumerableConvention out = EnumerableConvention.INSTANCE;  // 获取可枚举约定实例作为输出约定，声明为final常量
    final RelTraitSet traitSet = intersect.getTraitSet().replace(out);  // 从Intersect节点获取特征集，并将约定替换为可枚举约定，形成新的特征集
    return new EnumerableIntersect(rel.getCluster(), traitSet,  // 创建并返回新的EnumerableIntersect节点，传入集群、特征集
        convertList(intersect.getInputs(), out), intersect.all);  // 转换输入节点列表，并传入all标志（是否保留重复行）
  }
}  // 类定义结束
