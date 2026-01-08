/*  // Apache许可证声明,说明该文件遵循Apache 2.0许可证
 * Licensed to the Apache Software Foundation (ASF) under one or more  // 授权给Apache软件基金会
 * contributor license agreements.  See the NOTICE file distributed with  // 贡献者许可协议,查看NOTICE文件获取版权信息
 * this work for additional information regarding copyright ownership.  // 关于版权所有权的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0  // ASF根据Apache 2.0许可证授权给你
 * (the "License"); you may not use this file except in compliance with  // 你只能在遵守许可证的情况下使用此文件
 * the License.  You may obtain a copy of the License at  // 你可以从以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0  // 许可证获取网址
 *
 * Unless required by applicable law or agreed to in writing, software  // 除非适用法律要求或书面同意
 * distributed under the License is distributed on an "AS IS" BASIS,  // 否则按"原样"基础分发
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  // 不提供任何明示或暗示的担保或条件
 * See the License for the specific language governing permissions and  // 查看许可证了解具体的语言权限和
 * limitations under the License.  // 限制条款
 */  // Apache许可证声明结束
package org.apache.calcite.rel;  // 声明包名,该类属于org.apache.calcite.rel包,是Calcite关系代数表达式的核心包

import org.apache.calcite.plan.Convention;  // 导入Convention类,表示调用约定,描述关系表达式如何传递数据
import org.apache.calcite.plan.RelDigest;  // 导入RelDigest类,表示关系表达式的摘要信息,用于标识和比较
import org.apache.calcite.plan.RelOptCost;  // 导入RelOptCost类,表示关系表达式的优化成本
import org.apache.calcite.plan.RelOptNode;  // 导入RelOptNode接口,表示优化节点的基接口
import org.apache.calcite.plan.RelOptPlanner;  // 导入RelOptPlanner类,表示优化器,用于规划和优化关系表达式
import org.apache.calcite.plan.RelOptTable;  // 导入RelOptTable类,表示优化器中的表抽象
import org.apache.calcite.plan.RelOptUtil;  // 导入RelOptUtil类,提供关系表达式优化的工具方法
import org.apache.calcite.plan.RelTraitSet;  // 导入RelTraitSet类,表示关系表达式的特征集合
import org.apache.calcite.rel.core.CorrelationId;  // 导入CorrelationId类,表示相关ID,用于标识相关变量
import org.apache.calcite.rel.metadata.Metadata;  // 导入Metadata接口,表示元数据接口
import org.apache.calcite.rel.metadata.RelMetadataQuery;  // 导入RelMetadataQuery类,用于查询关系表达式的元数据
import org.apache.calcite.rel.type.RelDataType;  // 导入RelDataType类,表示关系数据类型
import org.apache.calcite.rex.RexNode;  // 导入RexNode类,表示行表达式节点
import org.apache.calcite.rex.RexShuttle;  // 导入RexShuttle类,用于遍历和修改行表达式
import org.apache.calcite.util.Litmus;  // 导入Litmus枚举,定义验证失败时的处理方式

import org.apiguardian.api.API;  // 导入API注解,用于标记API的稳定性和内部使用
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;  // 导入注解,表示如果方法返回true则参数非null
import org.checkerframework.checker.nullness.qual.Nullable;  // 导入注解,表示可能为null
import org.checkerframework.dataflow.qual.Pure;  // 导入注解,表示纯函数,无副作用

import java.util.List;  // 导入List接口,表示有序集合
import java.util.Set;  // 导入Set接口,表示无序不重复集合

/**  // JavaDoc注释开始,描述RelNode接口的作用和特性
 * A <code>RelNode</code> is a relational expression.  // RelNode是一个关系表达式,是Calcite中关系代数操作的核心抽象
 *
 * <p>Relational expressions process data, so their names are typically verbs:  // 关系表达式处理数据,因此它们的名称通常是动词
 * Sort, Join, Project, Filter, Scan, Sample.  // 例如:排序、连接、投影、过滤、扫描、采样等操作
 *
 * <p>A relational expression is not a scalar expression; see  // 关系表达式不是标量表达式,标量表达式参见
 * {@link org.apache.calcite.sql.SqlNode} and {@link RexNode}.  // SqlNode和RexNode,分别表示SQL节点和行表达式节点
 *
 * <p>If this type of relational expression has some particular planner rules,  // 如果此类关系表达式有特定的规划器规则
 * it should implement the <em>public static</em> method  // 它应该实现公共静态方法
 * {@link AbstractRelNode#register}.  // register方法,用于注册规则到规划器
 *
 * <p>When a relational expression comes to be implemented, the system allocates  // 当关系表达式被实现时,系统分配
 * a {@link org.apache.calcite.plan.RelImplementor} to manage the process. Every  // RelImplementor来管理实现过程,每个
 * implementable relational expression has a {@link RelTraitSet} describing its  // 可实现的关系表达式都有一个RelTraitSet描述其
 * physical attributes. The RelTraitSet always contains a {@link Convention}  // 物理属性,RelTraitSet总是包含一个Convention
 * describing how the expression passes data to its consuming  // 描述表达式如何将数据传递给消费它的
 * relational expression, but may contain other traits, including some applied  // 关系表达式,但可能包含其他特征,包括外部应用的
 * externally. Because traits can be applied externally, implementations of  // 特征,因为特征可以外部应用,RelNode的实现
 * RelNode should never assume the size or contents of their trait set (beyond  // 永远不应假设其特征集的大小或内容(除了
 * those traits configured by the RelNode itself).  // RelNode本身配置的特征)
 *
 * <p>For each calling-convention, there is a corresponding sub-interface of  // 对于每个调用约定,都有一个对应的RelNode子接口
 * RelNode. For example,  // 例如
 * {@code org.apache.calcite.adapter.enumerable.EnumerableRel}  // EnumerableRel接口,用于管理到
 * has operations to manage the conversion to a graph of  // EnumerableConvention调用约定的转换
 * {@code org.apache.calcite.adapter.enumerable.EnumerableConvention}  // 它与EnumerableRelImplementor交互
 * calling-convention, and it interacts with a  // 来实现可枚举的关系表达式
 * {@code EnumerableRelImplementor}.
 *
 * <p>A relational expression is only required to implement its  // 关系表达式只有在实际实现时才需要实现
 * calling-convention's interface when it is actually implemented, that is,  // 其调用约定的接口,即被转换为计划/程序时
 * converted into a plan/program. This means that relational expressions which  // 这意味着无法实现的关系表达式,如转换器
 * cannot be implemented, such as converters, are not required to implement  // 不需要实现其约定的接口
 * their convention's interface.
 *
 * <p>Every relational expression must derive from {@link AbstractRelNode}. (Why  // 每个关系表达式必须继承自AbstractRelNode(为什么
 * have the <code>RelNode</code> interface, then? We need a root interface,  // 还要有RelNode接口?因为我们需要一个根接口,
 * because an interface can only derive from an interface.)  // 因为接口只能继承自接口)
 */  // JavaDoc注释结束
public interface RelNode extends RelOptNode, Cloneable {  // RelNode接口定义,继承RelOptNode和Cloneable,表示关系表达式节点
  //~ Methods ----------------------------------------------------------------  // 方法分隔符,表示以下是方法定义部分

  /**  // JavaDoc注释开始,描述getConvention方法
   * Return the CallingConvention trait from this RelNode's  // 返回此RelNode的特征集中的CallingConvention特征
   * {@link #getTraitSet() trait set}.  // CallingConvention描述表达式如何传递数据
   *
   * @return this RelNode's CallingConvention  // 返回此RelNode的调用约定
   */  // JavaDoc注释结束
  @Pure  // 纯函数注解,表示方法无副作用
  @Nullable Convention getConvention();  // 获取调用约定,可能返回null

  /**  // JavaDoc注释开始,描述getCorrelVariable方法
   * Returns the name of the variable which is to be implicitly set at runtime  // 返回运行时隐式设置的变量名称
   * each time a row is returned from the first input of this relational  // 每次从此关系表达式的第一个输入返回行时
   * expression; or null if there is no variable.  // 如果没有变量则返回null
   *
   * @return Name of correlating variable, or null  // 返回相关变量的名称或null
   */  // JavaDoc注释结束
  @Nullable String getCorrelVariable();  // 获取相关变量名,可能返回null

  /**  // JavaDoc注释开始,描述getInput方法
   * Returns the <code>i</code><sup>th</sup> input relational expression.  // 返回第i个输入关系表达式
   *
   * @param i Ordinal of input  // 参数i表示输入的序号(从0开始)
   * @return <code>i</code><sup>th</sup> input  // 返回第i个输入
   */  // JavaDoc注释结束
  RelNode getInput(int i);  // 根据序号获取输入关系表达式

  /**  // JavaDoc注释开始,描述getRowType方法
   * Returns the type of the rows returned by this relational expression.  // 返回此关系表达式返回的行的类型
   */  // JavaDoc注释结束
  @Override RelDataType getRowType();  // 重写方法,获取行类型(输出行的数据类型)

  /**  // JavaDoc注释开始,描述getExpectedInputRowType方法
   * Returns the type of the rows expected for an input. Defaults to  // 返回输入期望的行类型,默认为getRowType
   * {@link #getRowType}.  // 即与输出行类型相同
   *
   * @param ordinalInParent input's 0-based ordinal with respect to this  // 参数ordinalInParent表示相对于此父关系的
   *                        parent rel  // 输入的0基序号
   * @return expected row type  // 返回期望的行类型
   */  // JavaDoc注释结束
  RelDataType getExpectedInputRowType(int ordinalInParent);  // 获取指定输入期望的行类型

  /**  // JavaDoc注释开始,描述getInputs方法
   * Returns an array of this relational expression's inputs. If there are no  // 返回此关系表达式的输入数组,如果没有输入
   * inputs, returns an empty list, not {@code null}.  // 则返回空列表,而不是null
   *
   * @return Array of this relational expression's inputs  // 返回此关系表达式的输入数组
   */  // JavaDoc注释结束
  @Override List<RelNode> getInputs();  // 重写方法,获取所有输入关系表达式列表

  /**  // JavaDoc注释开始,描述estimateRowCount方法
   * Returns an estimate of the number of rows this relational expression will  // 返回此关系表达式将返回的行数估计值
   * return.
   *
   * <p>NOTE jvs 29-Mar-2006: Don't call this method directly. Instead, use  // 注意:不要直接调用此方法,而应使用
   * {@link RelMetadataQuery#getRowCount}, which gives plugins a chance to  // RelMetadataQuery.getRowCount,它允许插件
   * override the rel's default ideas about row count.  // 覆盖关系的默认行数估计
   *
   * @param mq Metadata query  // 参数mq是元数据查询对象
   * @return Estimate of the number of rows this relational expression will  // 返回此关系表达式将返回的行数估计值
   *   return
   */  // JavaDoc注释结束
  double estimateRowCount(RelMetadataQuery mq);  // 估计行数,返回double类型的估计值

  /**  // JavaDoc注释开始,描述getVariablesSet方法
   * Returns the variables that are set in this relational  // 返回在此关系表达式中设置但也被使用
   * expression but also used and therefore not available to parents of this  // 因此对父关系表达式不可用的变量
   * relational expression.
   *
   * @return Names of variables which are set in this relational  // 返回在此关系表达式中设置的变量名称
   *   expression
   */  // JavaDoc注释结束
  Set<CorrelationId> getVariablesSet();  // 获取在此关系表达式中设置的相关变量集合

  /**  // JavaDoc注释开始,描述collectVariablesUsed方法
   * Collects variables known to be used by this expression or its  // 收集此表达式或其后代使用的变量
   * descendants. By default, no such information is available and must be  // 默认情况下没有此类信息,必须通过
   * derived by analyzing sub-expressions, but some optimizer implementations  // 分析子表达式来推导,但某些优化器实现
   * may insert special expressions which remember such information.  // 可能插入记住此类信息的特殊表达式
   *
   * @param variableSet receives variables used  // 参数variableSet接收使用的变量
   */  // JavaDoc注释结束
  void collectVariablesUsed(Set<CorrelationId> variableSet);  // 收集使用的变量到指定集合中

  /**  // JavaDoc注释开始,描述collectVariablesSet方法
   * Collects variables set by this expression.  // 收集由此表达式设置的变量
   * TODO: is this required?  // TODO:这个方法是否必需?
   *
   * @param variableSet receives variables known to be set by  // 参数variableSet接收已知由此表达式设置的变量
   */  // JavaDoc注释结束
  void collectVariablesSet(Set<CorrelationId> variableSet);  // 收集设置的变量到指定集合中

  /**  // JavaDoc注释开始,描述childrenAccept方法
   * Interacts with the {@link RelVisitor} in a  // 与RelVisitor交互,使用访问者模式
   * {@link org.apache.calcite.util.Glossary#VISITOR_PATTERN visitor pattern} to  // 遍历关系表达式树
   * traverse the tree of relational expressions.
   *
   * @param visitor Visitor that will traverse the tree of relational  // 参数visitor是遍历关系表达式树的访问者
   *                expressions
   */  // JavaDoc注释结束
  void childrenAccept(RelVisitor visitor);  // 接受访问者遍历子节点

  /**  // JavaDoc注释开始,描述computeSelfCost方法
   * Returns the cost of this plan (not including children). The base  // 返回此计划的成本(不包括子节点),基本实现
   * implementation throws an error; derived classes should override.  // 抛出错误,派生类应该重写
   *
   * <p>NOTE jvs 29-Mar-2006: Don't call this method directly. Instead, use  // 注意:不要直接调用此方法,而应使用
   * {@link RelMetadataQuery#getNonCumulativeCost}, which gives plugins a  // RelMetadataQuery.getNonCumulativeCost,它允许插件
   * chance to override the rel's default ideas about cost.  // 覆盖关系的默认成本估计
   *
   * @param planner Planner for cost calculation  // 参数planner是用于成本计算的规划器
   * @param mq Metadata query  // 参数mq是元数据查询对象
   * @return Cost of this plan (not including children)  // 返回此计划的成本(不包括子节点)
   */  // JavaDoc注释结束
  @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, RelMetadataQuery mq);  // 计算自身成本,不包括子节点

  /**  // JavaDoc注释开始,描述metadata方法
   * Returns a metadata interface.  // 返回元数据接口
   *
   * @deprecated Use {@link RelMetadataQuery} via {@link #getCluster()}.  // 已废弃:通过getCluster使用RelMetadataQuery
   *
   * @param <M> Type of metadata being requested  // 泛型M表示请求的元数据类型
   * @param metadataClass Metadata interface  // 参数metadataClass是元数据接口
   * @param mq Metadata query  // 参数mq是元数据查询对象
   *
   * @return Metadata object that supplies the desired metadata (never null,  // 返回提供所需元数据的元数据对象(从不为null,
   *     although if the information is not present the metadata object may  // 但如果信息不存在,元数据对象可能从所有方法
   *     return null from all methods)  // 返回null)
   */  // JavaDoc注释结束
  @Deprecated // to be removed before 2.0  // 已废弃注解,将在2.0版本前移除
  <@Nullable M extends @Nullable Metadata> M metadata(Class<M> metadataClass, RelMetadataQuery mq);  // 获取元数据对象

  /**  // JavaDoc注释开始,描述explain方法
   * Describes the inputs and attributes of this relational expression.  // 描述此关系表达式的输入和属性
   * Each node should call {@code super.explain}, then call the  // 每个节点应该调用super.explain,然后调用
   * {@link org.apache.calcite.rel.externalize.RelWriterImpl#input(String, RelNode)}  // input方法描述每个输入
   * and  // 和
   * {@link RelWriter#item(String, Object)}  // item方法描述每个属性
   * methods for each input and attribute.
   *
   * @param pw Plan writer  // 参数pw是计划写入器,用于输出计划描述
   */  // JavaDoc注释结束
  void explain(RelWriter pw);  // 解释关系表达式,输出计划描述

  /**  // JavaDoc注释开始,描述explain无参方法
   * Returns a relational expression string of this {@code RelNode}.  // 返回此RelNode的关系表达式字符串
   * The string returned is the same as  // 返回的字符串与
   * {@link RelOptUtil#toString(org.apache.calcite.rel.RelNode)}.  // RelOptUtil.toString相同
   *
   * <p>This method is intended mainly for use while debugging in an IDE,  // 此方法主要用于在IDE中调试时使用
   * as a convenient shorthand for {@link RelOptUtil#toString}.  // 作为RelOptUtil.toString的便捷简写
   * We recommend that classes implementing this interface  // 我们建议实现此接口的类
   * do not override this method.  // 不要重写此方法
   *
   * @return Relational expression string of this {@code RelNode}  // 返回此RelNode的关系表达式字符串
   */  // JavaDoc注释结束
  default String explain() {  // 默认方法,返回关系表达式字符串
    return RelOptUtil.toString(this);  // 使用RelOptUtil工具类转换为字符串
  }

  /**  // JavaDoc注释开始,描述onRegister方法
   * Receives notification that this expression is about to be registered. The  // 接收此表达式即将被注册的通知,实现必须
   * implementation of this method must at least register all child  // 至少注册所有子表达式
   * expressions.
   *
   * @param planner Planner that plans this relational node  // 参数planner是规划此关系节点的规划器
   * @return Relational expression that should be used by the planner  // 返回规划器应使用的关系表达式
   */  // JavaDoc注释结束
  RelNode onRegister(RelOptPlanner planner);  // 注册时调用,返回规划器使用的关系表达式

  /**  // JavaDoc注释开始,描述getDigest方法
   * Returns a digest string of this {@code RelNode}.  // 返回此RelNode的摘要字符串
   *
   * <p>Each call creates a new digest string,  // 每次调用都会创建新的摘要字符串
   * so don't forget to cache the result if necessary.  // 所以如有必要别忘了缓存结果
   *
   * @return Digest string of this {@code RelNode}  // 返回此RelNode的摘要字符串
   *
   * @see #getRelDigest()  // 参见getRelDigest方法
   */  // JavaDoc注释结束
  @Override default String getDigest() {  // 重写方法,返回摘要字符串
    return getRelDigest().toString();  // 转换RelDigest为字符串
  }

  /**  // JavaDoc注释开始,描述getRelDigest方法
   * Returns a digest of this {@code RelNode}.  // 返回此RelNode的摘要对象
   *
   * <p>INTERNAL USE ONLY. For use by the planner.  // 内部使用,仅供规划器使用
   *
   * @return Digest of this {@code RelNode}  // 返回此RelNode的摘要
   * @see #getDigest()  // 参见getDigest方法
   */  // JavaDoc注释结束
  @API(since = "1.24", status = API.Status.INTERNAL)  // API注解,标记为内部API,从1.24版本开始
  RelDigest getRelDigest();  // 获取RelDigest对象

  /**  // JavaDoc注释开始,描述recomputeDigest方法
   * Recomputes the digest.  // 重新计算摘要
   *
   * <p>INTERNAL USE ONLY. For use by the planner.  // 内部使用,仅供规划器使用
   *
   * @see #getDigest()  // 参见getDigest方法
   */  // JavaDoc注释结束
  @API(since = "1.24", status = API.Status.INTERNAL)  // API注解,标记为内部API,从1.24版本开始
  void recomputeDigest();  // 重新计算摘要

  /**  // JavaDoc注释开始,描述deepEquals方法
   * Deep equality check for RelNode digest.  // RelNode摘要的深度相等性检查
   *
   * <p>By default this method collects digest attributes from  // 默认情况下,此方法从explain terms收集
   * explain terms, then compares each attribute pair.  // 摘要属性,然后比较每个属性对
   *
   * @return Whether the 2 RelNodes are equivalent or have the same digest.  // 返回两个RelNode是否等价或具有相同摘要
   * @see #deepHashCode()  // 参见deepHashCode方法
   */  // JavaDoc注释结束
  @EnsuresNonNullIf(expression = "#1", result = true)  // 注解,表示如果返回true则参数非null
  boolean deepEquals(@Nullable Object obj);  // 深度相等性检查

  /**  // JavaDoc注释开始,描述deepHashCode方法
   * Compute deep hash code for RelNode digest.  // 为RelNode摘要计算深度哈希码
   *
   * @see #deepEquals(Object)  // 参见deepEquals方法
   */  // JavaDoc注释结束
  int deepHashCode();  // 计算深度哈希码

  /**  // JavaDoc注释开始,描述replaceInput方法
   * Replaces the <code>ordinalInParent</code><sup>th</sup> input. You must  // 替换第ordinalInParent个输入,如果重写了
   * override this method if you override {@link #getInputs}.  // getInputs方法,必须重写此方法
   *
   * @param ordinalInParent Position of the child input, 0 is the first  // 参数ordinalInParent是子输入的位置,0表示第一个
   * @param p New node that should be put at position {@code ordinalInParent}  // 参数p是要放在ordinalInParent位置的新节点
   */  // JavaDoc注释结束
  void replaceInput(  // 替换输入方法
      int ordinalInParent,  // 输入位置序号
      RelNode p);  // 新的关系节点

  /**  // JavaDoc注释开始,描述getTable方法
   * If this relational expression represents an access to a table, returns  // 如果此关系表达式表示对表的访问,则返回该表
   * that table, otherwise returns null.  // 否则返回null
   *
   * @return If this relational expression represents an access to a table,  // 返回如果此关系表达式表示对表的访问则返回该表
   *   returns that table, otherwise returns null  // 否则返回null
   */  // JavaDoc注释结束
  @Nullable RelOptTable getTable();  // 获取表,可能返回null

  /**  // JavaDoc注释开始,描述getRelTypeName方法
   * Returns the name of this relational expression's class, sans package  // 返回此关系表达式类的名称,不包括包名
   * name, for use in explain. For example, for a <code>  // 用于explain输出,例如对于
   * org.apache.calcite.rel.ArrayRel.ArrayReader</code>, this method returns  // org.apache.calcite.rel.ArrayRel.ArrayReader,此方法返回
   * "ArrayReader".  // "ArrayReader"
   *
   * @return Name of this relational expression's class, sans package name,  // 返回此关系表达式类的名称,不包括包名
   *   for use in explain  // 用于explain输出
   */  // JavaDoc注释结束
  String getRelTypeName();  // 获取关系类型名称

  /**  // JavaDoc注释开始,描述isValid方法
   * Returns whether this relational expression is valid.  // 返回此关系表达式是否有效
   *
   * <p>If assertions are enabled, this method is typically called with <code>  // 如果启用了断言,此方法通常使用
   * litmus</code> = <code>THROW</code>, as follows:  // litmus=THROW调用,如下所示
   *
   * <blockquote>  // 代码块开始
   * <pre>assert rel.isValid(Litmus.THROW)</pre>  // 断言语句示例
   * </blockquote>  // 代码块结束
   *
   * <p>This signals that the method can throw an {@link AssertionError} if it  // 这表示如果关系表达式无效且litmus为THROW,
   * is not valid.  // 方法可以抛出AssertionError
   *
   * @param litmus What to do if invalid  // 参数litmus定义无效时的处理方式
   * @param context Context for validity checking  // 参数context是有效性检查的上下文
   * @return Whether relational expression is valid  // 返回关系表达式是否有效
   * @throws AssertionError if this relational expression is invalid and  // 如果关系表达式无效且litmus为THROW则抛出断言错误
   *                        litmus is THROW
   */  // JavaDoc注释结束
  boolean isValid(Litmus litmus, @Nullable Context context);  // 检查关系表达式是否有效

  /**  // JavaDoc注释开始,描述copy方法
   * Creates a copy of this relational expression, perhaps changing traits and  // 创建此关系表达式的副本,可能改变特征和输入
   * inputs.
   *
   * <p>Sub-classes with other important attributes are encouraged to create  // 具有其他重要属性的子类应该创建
   * variants of this method with more parameters.  // 具有更多参数的此方法变体
   *
   * @param traitSet Trait set  // 参数traitSet是特征集合
   * @param inputs   Inputs  // 参数inputs是输入列表
   * @return Copy of this relational expression, substituting traits and  // 返回此关系表达式的副本,替换特征和输入
   * inputs
   */  // JavaDoc注释结束
  RelNode copy(  // 复制方法
      RelTraitSet traitSet,  // 新的特征集合
      List<RelNode> inputs);  // 新的输入列表

  /**  // JavaDoc注释开始,描述register方法
   * Registers any special rules specific to this kind of relational  // 注册特定于此类关系表达式的特殊规则
   * expression.
   *
   * <p>The planner calls this method this first time that it sees a  // 规划器第一次看到此类关系表达式时调用此方法,派生类应该为每个规则调用
   * relational expression of this class. The derived class should call  // RelOptPlanner.addRule,然后调用super.register
   * {@link org.apache.calcite.plan.RelOptPlanner#addRule} for each rule, and
   * then call {@code super.register}.
   *
   * @param planner Planner to be used to register additional relational  // 参数planner是用于注册额外关系表达式的规划器
   *                expressions
   */  // JavaDoc注释结束
  void register(RelOptPlanner planner);  // 注册规则到规划器

  /**  // JavaDoc注释开始,描述isEnforcer方法
   * Indicates whether it is an enforcer operator, e.g. PhysicalSort,  // 指示是否是强制算子,例如PhysicalSort,
   * PhysicalHashDistribute, etc. As an enforcer, the operator must be  // PhysicalHashDistribute等,作为强制算子,只有当输入不满足所需的traitSet时
   * created only when required traitSet is not satisfied by its input.  // 才创建此算子
   *
   * @return Whether it is an enforcer operator  // 返回是否是强制算子
   */  // JavaDoc注释结束
  default boolean isEnforcer() {  // 默认方法,返回是否是强制算子
    return false;  // 默认返回false,表示不是强制算子
  }

  /**  // JavaDoc注释开始,描述accept(RelShuttle)方法
   * Accepts a visit from a shuttle.  // 接受shuttle的访问
   *
   * @param shuttle Shuttle  // 参数shuttle是访问shuttle
   * @return A copy of this node incorporating changes made by the shuttle to  // 返回此节点的副本,包含shuttle对子节点所做的更改
   * this node's children
   */  // JavaDoc注释结束
  RelNode accept(RelShuttle shuttle);  // 接受RelShuttle访问

  /**  // JavaDoc注释开始,描述accept(RexShuttle)方法
   * Accepts a visit from a shuttle. If the shuttle updates expression, then  // 接受shuttle的访问,如果shuttle更新表达式,则
   * a copy of the relation should be created. This new relation might have  // 应该创建关系的副本,这个新关系可能有
   * a different row-type.  // 不同的行类型
   *
   * @param shuttle Shuttle  // 参数shuttle是访问shuttle
   * @return A copy of this node incorporating changes made by the shuttle to  // 返回此节点的副本,包含shuttle对子节点所做的更改
   * this node's children
   */  // JavaDoc注释结束
  RelNode accept(RexShuttle shuttle);  // 接受RexShuttle访问

  /** Returns whether a field is nullable. */  // 返回字段是否可为null
  default boolean fieldIsNullable(int i) {  // 默认方法,检查字段是否可为null
    return getRowType().getFieldList().get(i).getType().isNullable();  // 从行类型获取字段信息并检查是否可为null
  }

  /** Returns this node without any wrapper added by the planner. */  // 返回不带规划器添加的包装器的此节点
  default RelNode stripped() {  // 默认方法,返回剥离包装器的节点
    return this;  // 默认返回this
  }

  /** Context of a relational expression, for purposes of checking validity. */  // 关系表达式的上下文,用于有效性检查
  interface Context {  // Context接口定义,用于有效性检查的上下文
    Set<CorrelationId> correlationIds();  // 返回相关ID集合
  }
}