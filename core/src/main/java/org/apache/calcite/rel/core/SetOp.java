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
package org.apache.calcite.rel.core;  // 定义包名，该类位于org.apache.calcite.rel.core包中，是Calcite关系代数核心包

import org.apache.calcite.linq4j.Ord;  // 导入Ord类，用于为集合元素添加索引，方便遍历时获取元素位置
import org.apache.calcite.plan.RelOptCluster;  // 导入RelOptCluster类，表示关系代数优化集群，包含类型工厂等共享资源
import org.apache.calcite.plan.RelOptUtil;  // 导入RelOptUtil工具类，提供关系代数优化的实用方法
import org.apache.calcite.plan.RelTraitSet;  // 导入RelTraitSet类，表示关系节点的特征集合，如物理实现方式
import org.apache.calcite.rel.AbstractRelNode;  // 导入AbstractRelNode抽象类，SetOp将继承此类作为基础关系节点
import org.apache.calcite.rel.RelInput;  // 导入RelInput类，用于从序列化数据反序列化关系节点
import org.apache.calcite.rel.RelNode;  // 导入RelNode接口，表示关系代数中的关系节点
import org.apache.calcite.rel.RelWriter;  // 导入RelWriter接口，用于将关系节点输出为可读格式
import org.apache.calcite.rel.hint.Hintable;  // 导入Hintable接口，支持查询提示功能
import org.apache.calcite.rel.hint.RelHint;  // 导入RelHint类，表示查询提示信息
import org.apache.calcite.rel.type.RelDataType;  // 导入RelDataType类，表示关系数据的类型
import org.apache.calcite.sql.SqlKind;  // 导入SqlKind枚举，表示SQL操作的类型，如UNION、INTERSECT等
import org.apache.calcite.util.Util;  // 导入Util工具类，提供各种通用实用方法

import com.google.common.collect.ImmutableList;  // 导入Google Guava的ImmutableList类，用于创建不可变列表

import java.util.ArrayList;  // 导入ArrayList类，用于动态数组
import java.util.Collections;  // 导入Collections工具类，用于集合操作
import java.util.List;  // 导入List接口，表示有序集合

import static com.google.common.base.Preconditions.checkArgument;  // 静态导入Preconditions的checkArgument方法，用于参数校验

import static org.apache.calcite.sql.SqlKind.SET_QUERY;  // 静态导入SqlKind的SET_QUERY，用于判断是否为集合查询操作

/**
 * <code>SetOp</code> is an abstract base for relational set operators such
 * as UNION, MINUS (aka EXCEPT), and INTERSECT.
 * SetOp是关系代数集合操作符的抽象基类，用于表示UNION（并集）、MINUS（差集，也称为EXCEPT）和INTERSECT（交集）等集合操作
 * 该类继承自AbstractRelNode，实现了Hintable接口，支持查询提示功能
 * 集合操作是SQL中用于合并多个查询结果的重要操作，在关系代数中对应于集合论中的并、交、差运算
 * SetOp类提供了集合操作的通用框架，具体的集合操作由其子类实现，如Union、Intersect、Minus等
 */
public abstract class SetOp extends AbstractRelNode implements Hintable {  // 定义抽象类SetOp，继承AbstractRelNode并实现Hintable接口
  //~ Instance fields --------------------------------------------------------  // 实例字段部分的分隔注释

  protected ImmutableList<RelNode> inputs;  // 输入关系节点列表，使用不可变列表存储，表示参与集合操作的所有输入关系，如UNION的多个SELECT语句
  public final SqlKind kind;  // 集合操作的类型，使用SqlKind枚举表示，如SqlKind.UNION、SqlKind.INTERSECT、SqlKind.EXCEPT等，final修饰表示不可变
  public final boolean all;  // 是否为ALL操作，true表示保留重复行（如UNION ALL），false表示去重（如UNION），final修饰表示不可变
  protected final ImmutableList<RelHint> hints;  // 查询提示列表，使用不可变列表存储，包含优化器使用的提示信息，final修饰表示不可变

  //~ Constructors -----------------------------------------------------------  // 构造方法部分的分隔注释

  /**
   * Creates a SetOp.
   * 创建一个SetOp集合操作关系节点，这是主要的构造方法，接受所有必要的参数
   * @param cluster 关系代数优化集群，包含类型工厂等共享资源，用于创建和管理关系节点
   * @param traits 关系节点的特征集合，定义了节点的物理实现特征，如排序方式、并行度等
   * @param hints 查询提示列表，包含优化器使用的提示信息，可以影响查询执行计划
   * @param inputs 输入关系节点列表，表示参与集合操作的所有输入关系，如UNION操作的多个子查询
   * @param kind 集合操作的类型，如UNION、INTERSECT、MINUS等，必须是SET_QUERY中定义的类型之一
   * @param all 是否为ALL操作，true表示保留重复行（UNION ALL），false表示自动去重（UNION）
   */
  protected SetOp(RelOptCluster cluster, RelTraitSet traits, List<RelHint> hints,  // 定义构造方法，参数包括集群、特征集、提示、输入列表、操作类型和ALL标志
      List<RelNode> inputs, SqlKind kind, boolean all) {  // 继续定义构造方法参数
    super(cluster, traits);  // 调用父类AbstractRelNode的构造方法，初始化集群和特征集
    checkArgument(SET_QUERY.contains(kind));  // 校验kind参数是否为有效的集合查询类型，如果不是则抛出IllegalArgumentException
    this.kind = kind;  // 初始化kind成员变量，保存集合操作类型
    this.inputs = ImmutableList.copyOf(inputs);  // 将输入列表转换为不可变列表并保存，确保输入列表不会被外部修改
    this.all = all;  // 初始化all成员变量，保存是否保留重复行的标志
    this.hints = ImmutableList.copyOf(hints);  // 将提示列表转换为不可变列表并保存，确保提示列表不会被外部修改
  }

  /**
   * Creates a SetOp.
   * 创建一个SetOp集合操作关系节点，这是一个简化版的构造方法，不包含提示参数
   * @param cluster 关系代数优化集群，包含类型工厂等共享资源
   * @param traits 关系节点的特征集合，定义了节点的物理实现特征
   * @param inputs 输入关系节点列表，表示参与集合操作的所有输入关系
   * @param kind 集合操作的类型，如UNION、INTERSECT、MINUS等
   * @param all 是否为ALL操作，true表示保留重复行，false表示自动去重
   */
  protected SetOp(RelOptCluster cluster, RelTraitSet traits,  // 定义简化版构造方法，参数不包括hints
      List<RelNode> inputs, SqlKind kind, boolean all) {  // 继续定义构造方法参数
    this(cluster, traits, Collections.emptyList(), inputs, kind, all);  // 调用主构造方法，传入空的提示列表作为hints参数
  }

  /**
   * Creates a SetOp by parsing serialized output.
   * 通过解析序列化输出来创建SetOp集合操作关系节点，用于从持久化格式恢复关系节点
   * @param input 序列化的关系输入对象，包含集群、特征集、输入列表等信息
   */
  protected SetOp(RelInput input) {  // 定义从序列化输入创建的构造方法
    this(input.getCluster(), input.getTraitSet(), Collections.emptyList(),  // 调用主构造方法，从input对象获取集群和特征集，使用空提示列表
        input.getInputs(), SqlKind.UNION, input.getBoolean("all", false));  // 从input对象获取输入列表，默认使用UNION类型，从input获取all标志（默认false）
  }

  //~ Methods ----------------------------------------------------------------  // 方法部分的分隔注释

  /**
   * 抽象方法：复制SetOp节点，允许修改特征集、输入列表和ALL标志
   * 该方法由子类实现，用于创建一个新的SetOp节点实例，常用于优化器转换和重写
   * @param traitSet 新的关系特征集合，可以改变节点的物理实现特征
   * @param inputs 新的输入关系节点列表，可以替换原有的输入
   * @param all 新的ALL标志，可以改变是否保留重复行的行为
   * @return 新的SetOp节点实例，类型与当前节点相同
   */
  public abstract SetOp copy(  // 定义抽象方法copy，子类必须实现
      RelTraitSet traitSet,  // 参数：新的特征集合
      List<RelNode> inputs,  // 参数：新的输入列表
      boolean all);  // 参数：新的ALL标志

  /**
   * 复制SetOp节点，只修改特征集和输入列表，保持ALL标志不变
   * 这是父类RelNode接口定义的copy方法的实现
   * @param traitSet 新的关系特征集合
   * @param inputs 新的输入关系节点列表
   * @return 新的SetOp节点实例，ALL标志与当前节点相同
   */
  @Override public SetOp copy(RelTraitSet traitSet, List<RelNode> inputs) {  // 重写父类的copy方法
    return copy(traitSet, inputs, all);  // 调用三参数的copy方法，保持当前的all值不变
  }

  /**
   * 替换指定位置的输入关系节点
   * 该方法用于在优化过程中动态修改输入关系，修改后会重新计算节点的摘要信息
   * @param ordinalInParent 要替换的输入节点的位置索引（从0开始）
   * @param p 新的关系节点，将替换指定位置的输入
   */
  @Override public void replaceInput(int ordinalInParent, RelNode p) {  // 重写父类的replaceInput方法
    final List<RelNode> newInputs = new ArrayList<>(inputs);  // 创建当前输入列表的可变副本
    newInputs.set(ordinalInParent, p);  // 将指定位置的输入替换为新的关系节点p
    inputs = ImmutableList.copyOf(newInputs);  // 将修改后的列表转换为不可变列表并赋值给inputs成员变量
    recomputeDigest();  // 重新计算节点的摘要信息，因为输入已改变
  }

  /**
   * 获取所有输入关系节点列表
   * 该方法返回集合操作的所有输入关系，如UNION操作的多个子查询
   * @return 不可变的输入关系节点列表
   */
  @Override public List<RelNode> getInputs() {  // 重写父类的getInputs方法
    return inputs;  // 返回inputs成员变量，该变量已经是不可变列表
  }

  /**
   * 将节点信息输出到RelWriter，用于生成查询计划的可读表示
   * 该方法会输出所有输入关系和ALL标志，帮助理解查询计划的结构
   * @param pw RelWriter对象，用于构建输出文本
   * @return RelWriter对象，支持链式调用
   */
  @Override public RelWriter explainTerms(RelWriter pw) {  // 重写父类的explainTerms方法
    super.explainTerms(pw);  // 调用父类的explainTerms方法，输出基础信息如集群、特征集等
    for (Ord<RelNode> ord : Ord.zip(inputs)) {  // 遍历输入列表，Ord.zip为每个元素添加索引
      pw.input("input#" + ord.i, ord.e);  // 输出每个输入关系，格式为"input#0: 关系节点"
    }
    return pw.item("all", all);  // 输出ALL标志，格式为"all: true/false"，并返回pw支持链式调用
  }

  /**
   * 推导集合操作的输出行类型
   * 集合操作的输出类型是所有输入类型的公共超类型（最小限制类型）
   * 例如，如果输入1的类型是INTEGER，输入2的类型是BIGINT，则输出类型为BIGINT
   * @return 推导出的输出行类型，包含字段名和字段类型
   * @throws IllegalArgumentException 如果无法计算出兼容的行类型
   */
  @Override protected RelDataType deriveRowType() {  // 重写父类的deriveRowType方法
    final List<RelDataType> inputRowTypes =  // 声明变量存储所有输入的行类型
        Util.transform(inputs, RelNode::getRowType);  // 使用Util.transform将输入列表转换为行类型列表，通过调用每个输入的getRowType方法
    final RelDataType rowType =  // 声明变量存储推导出的公共行类型
        getCluster().getTypeFactory().leastRestrictive(inputRowTypes);  // 调用类型工厂的leastRestrictive方法，计算所有输入类型的最小限制类型（公共超类型）
    if (rowType == null) {  // 如果无法计算出兼容的行类型（leastRestrictive返回null）
      throw new IllegalArgumentException("Cannot compute compatible row type "  // 抛出IllegalArgumentException异常
          + "for arguments to set op: "  // 异常消息：无法计算集合操作的兼容行类型
          + Util.sepList(inputRowTypes, ", "));  // 列出所有输入的行类型，用逗号分隔
    }
    return rowType;  // 返回推导出的行类型
  }

  /**
   * 获取查询提示列表
   * 该方法返回与该集合操作节点关联的所有查询提示
   * @return 不可变的查询提示列表
   */
  @Override public ImmutableList<RelHint> getHints() {  // 实现Hintable接口的getHints方法
    return hints;  // 返回hints成员变量，该变量已经是不可变列表
  }

  /**
   * 检查集合操作的所有输入是否具有相同的行类型（同质性检查）
   * 同质性是指所有输入的行类型与输出行类型是否一致
   * 该方法用于验证集合操作的输入是否兼容，某些优化规则可能要求输入同质
   * @param compareNames 是否在比较时考虑列名，true表示列名必须相同，false表示只比较类型
   * @return 如果所有输入的行类型都与输出行类型相同则返回true，否则返回false
   */
  public boolean isHomogeneous(boolean compareNames) {  // 定义方法检查输入是否同质
    RelDataType unionType = getRowType();  // 获取集合操作的输出行类型
    for (RelNode input : getInputs()) {  // 遍历所有输入关系节点
      if (!RelOptUtil.areRowTypesEqual(  // 检查当前输入的行类型是否与输出行类型相等
          input.getRowType(), unionType, compareNames)) {  // 调用RelOptUtil.areRowTypesEqual方法比较行类型
        return false;  // 如果发现不匹配的输入类型，立即返回false
      }
    }
    return true;  // 所有输入类型都与输出类型匹配，返回true
  }
}
