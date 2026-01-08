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
package org.apache.calcite.adapter.enumerable;  // 包声明:枚举适配器包,包含可枚举调用约定相关的实现类

import org.apache.calcite.linq4j.tree.BlockBuilder;  // 导入:代码块构建器,用于构建Java代码块
import org.apache.calcite.linq4j.tree.DeclarationStatement;  // 导入:声明语句,用于变量声明
import org.apache.calcite.linq4j.tree.Expression;  // 导入:表达式基类,表示LINQ4J表达式树中的节点
import org.apache.calcite.linq4j.tree.Expressions;  // 导入:表达式工具类,提供创建各种表达式的静态方法
import org.apache.calcite.linq4j.tree.ParameterExpression;  // 导入:参数表达式,表示方法或lambda的参数
import org.apache.calcite.linq4j.tree.Primitive;  // 导入:原始类型工具类,处理Java原始类型
import org.apache.calcite.plan.DeriveMode;  // 导入:派生模式,定义特性如何从子节点派生
import org.apache.calcite.plan.RelOptCluster;  // 导入:关系表达式集群,包含优化器上下文和共享资源
import org.apache.calcite.plan.RelOptCost;  // 导入:关系表达式成本,表示执行计划的成本估算
import org.apache.calcite.plan.RelOptPlanner;  // 导入:关系表达式优化器,用于优化查询计划
import org.apache.calcite.plan.RelTraitSet;  // 导入:关系特性集合,定义关系表达式的物理属性
import org.apache.calcite.rel.RelCollationTraitDef;  // 导入:排序特性定义,定义排序规则
import org.apache.calcite.rel.RelNode;  // 导入:关系表达式基类,所有关系操作符的基类
import org.apache.calcite.rel.RelWriter;  // 导入:关系表达式写入器,用于输出关系表达式的文本表示
import org.apache.calcite.rel.core.CorrelationId;  // 导入:相关性ID,标识相关变量
import org.apache.calcite.rel.core.Join;  // 导入:连接操作符基类,所有连接操作的父类
import org.apache.calcite.rel.core.JoinRelType;  // 导入:连接类型枚举,定义INNER/LEFT/RIGHT/FULL等连接类型
import org.apache.calcite.rel.metadata.RelMdCollation;  // 导入:排序元数据,提供排序相关的元数据
import org.apache.calcite.rel.metadata.RelMetadataQuery;  // 导入:元数据查询接口,用于查询关系表达式的元数据
import org.apache.calcite.rex.RexNode;  // 导入:行表达式节点,表示行级别的表达式(如条件、投影等)
import org.apache.calcite.util.BuiltInMethod;  // 导入:内置方法枚举,定义Calcite内置的LINQ4J方法
import org.apache.calcite.util.ImmutableBitSet;  // 导入:不可变位集合,高效表示列索引集合
import org.apache.calcite.util.Pair;  // 导入:键值对类,存储两个相关联的值

import com.google.common.collect.ImmutableList;  // 导入:不可变列表,Guava提供的不可变集合实现

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入:可空注解,标记可能为null的值

import java.lang.reflect.Modifier;  // 导入:修饰符类,表示Java访问修饰符
import java.lang.reflect.Type;  // 导入:类型接口,表示Java类型
import java.util.ArrayList;  // 导入:动态数组列表,可变长度的数组实现
import java.util.List;  // 导入:列表接口,定义有序集合
import java.util.Set;  // 导入:集合接口,定义无序不重复集合

/** Implementation of batch nested loop join in  // 类注释:批量嵌套循环连接的实现
 * {@link org.apache.calcite.adapter.enumerable.EnumerableConvention enumerable calling convention}. */  // 在可枚举调用约定下实现
public class EnumerableBatchNestedLoopJoin extends Join implements EnumerableRel {  // 类定义:批量嵌套循环连接类,继承Join基类并实现EnumerableRel接口

  private final ImmutableBitSet requiredColumns;  // 成员变量:必需的列集合,表示输出结果中必须包含的列索引集合
  protected EnumerableBatchNestedLoopJoin(  // 构造方法:创建批量嵌套循环连接实例
      RelOptCluster cluster,  // 参数:关系表达式集群,包含优化器上下文和共享资源
      RelTraitSet traits,  // 参数:关系特性集合,定义物理属性(如调用约定、排序等)
      RelNode left,  // 参数:左子节点,表示连接操作的左输入表
      RelNode right,  // 参数:右子节点,表示连接操作的右输入表
      RexNode condition,  // 参数:连接条件,表示连接谓词(如ON子句中的条件)
      Set<CorrelationId> variablesSet,  // 参数:相关变量集合,表示需要相关处理的变量集合
      ImmutableBitSet requiredColumns,  // 参数:必需列集合,表示输出结果必须包含的列
      JoinRelType joinType) {  // 参数:连接类型,指定是INNER/LEFT/RIGHT/FULL连接
    super(cluster, traits, ImmutableList.of(), left, right, condition, variablesSet, joinType);  // 调用父类Join构造方法初始化基类部分,传入空列表作为系统字段
    this.requiredColumns = requiredColumns;  // 初始化成员变量:保存必需列集合
  }

  public static EnumerableBatchNestedLoopJoin create(  // 静态工厂方法:创建批量嵌套循环连接实例(推荐使用此方法创建对象)
      RelNode left,  // 参数:左子节点,连接操作的左输入表
      RelNode right,  // 参数:右子节点,连接操作的右输入表
      RexNode condition,  // 参数:连接条件,连接谓词表达式
      ImmutableBitSet requiredColumns,  // 参数:必需列集合,输出必须包含的列
      Set<CorrelationId> variablesSet,  // 参数:相关变量集合,需要相关处理的变量
      JoinRelType joinType) {  // 参数:连接类型,INNER/LEFT/RIGHT/FULL
    final RelOptCluster cluster = left.getCluster();  // 获取左节点的集群对象,包含优化器上下文
    final RelMetadataQuery mq = cluster.getMetadataQuery();  // 获取元数据查询对象,用于查询统计信息
    final RelTraitSet traitSet =  // 构建特性集合
        cluster.traitSetOf(EnumerableConvention.INSTANCE)  // 基础特性:使用可枚举调用约定
            .replaceIfs(RelCollationTraitDef.INSTANCE,  // 条件替换:如果存在排序特性定义
                () -> RelMdCollation.enumerableBatchNestedLoopJoin(mq, left, right, joinType));  // 则使用批量嵌套循环连接的排序元数据
    return new EnumerableBatchNestedLoopJoin(  // 返回新创建的批量嵌套循环连接实例
        cluster,  // 传入集群对象
        traitSet,  // 传入特性集合
        left,  // 传入左子节点
        right,  // 传入右子节点
        condition,  // 传入连接条件
        variablesSet,  // 传入相关变量集合
        requiredColumns,  // 传入必需列集合
        joinType);  // 传入连接类型
  }

  @Override public @Nullable Pair<RelTraitSet, List<RelTraitSet>> passThroughTraits(  // 重写方法:传递特性,确定哪些特性可以传递给子节点
      final RelTraitSet required) {  // 参数:父节点要求的特性集合
    return EnumerableTraitsUtils.passThroughTraitsForJoin(  // 调用工具方法计算连接操作的特性传递规则
        required, joinType, getLeft().getRowType().getFieldCount(), traitSet);  // 传入要求的特性、连接类型、左表字段数和当前特性集
  }

  @Override public @Nullable Pair<RelTraitSet, List<RelTraitSet>> deriveTraits(  // 重写方法:派生特性,从子节点特性派生当前节点特性
      final RelTraitSet childTraits, final int childId) {  // 参数:子节点的特性集合和子节点ID(0表示左子节点,1表示右子节点)
    return EnumerableTraitsUtils.deriveTraitsForJoin(  // 调用工具方法计算连接操作的特性派生规则
        childTraits, childId, joinType, traitSet, right.getTraitSet());  // 传入子节点特性、子节点ID、连接类型、当前特性集和右子节点特性集
  }

  @Override public DeriveMode getDeriveMode() {  // 重写方法:获取派生模式,定义特性如何从子节点派生
    if (joinType == JoinRelType.FULL || joinType == JoinRelType.RIGHT) {  // 如果是全连接或右连接
      return DeriveMode.PROHIBITED;  // 返回禁止模式,不允许派生特性(因为右连接和全连接需要先访问右表)
    }  // 结束条件判断

    return DeriveMode.LEFT_FIRST;  // 否则返回左优先模式,先从左子节点派生特性
  }

  @Override public EnumerableBatchNestedLoopJoin copy(RelTraitSet traitSet,  // 重写方法:复制当前节点,创建一个具有不同特性的副本
      RexNode condition, RelNode left, RelNode right, JoinRelType joinType,  // 参数:新特性集、新连接条件、新左右子节点、新连接类型
      boolean semiJoinDone) {  // 参数:半连接是否完成标志
    return new EnumerableBatchNestedLoopJoin(getCluster(), traitSet,  // 返回新创建的批量嵌套循环连接实例,保持原有的requiredColumns和variablesSet
        left, right, condition, variablesSet, requiredColumns, joinType);  // 传入集群、特性集、左右子节点、连接条件、相关变量集、必需列集和连接类型
  }

  @Override public @Nullable RelOptCost computeSelfCost(  // 重写方法:计算自身成本,估算执行此连接操作的成本
      final RelOptPlanner planner,  // 参数:优化器,用于创建成本对象
      final RelMetadataQuery mq) {  // 参数:元数据查询,用于获取行数等统计信息
    double rowCount = mq.getRowCount(this);  // 获取当前连接节点的输出行数

    final double rightRowCount = mq.getRowCount(right);  // 获取右子节点的行数
    final double leftRowCount = mq.getRowCount(left);  // 获取左子节点的行数
    if (Double.isInfinite(leftRowCount) || Double.isInfinite(rightRowCount)) {  // 如果左表或右表的行数是无穷大
      return planner.getCostFactory().makeInfiniteCost();  // 返回无穷大成本,表示此计划不可行
    }  // 结束条件判断

    Double restartCount = mq.getRowCount(getLeft()) / variablesSet.size();  // 计算重启次数:左表行数除以批量大小(相关变量集合的大小)

    RelOptCost rightCost = planner.getCost(getRight(), mq);  // 计算右子节点的成本
    if (rightCost == null) {  // 如果右子节点成本无法计算
      return null;  // 返回null,表示成本计算失败
    }  // 结束条件判断
    RelOptCost rescanCost =  // 计算重扫描成本
        rightCost.multiplyBy(Math.max(1.0, restartCount - 1));  // 右节点成本乘以(重启次数-1),表示需要多次重扫描右表

    // TODO Add cost of last loop (the one that looks for the match)  // TODO注释:需要添加最后一次循环的成本(查找匹配的循环)
    return planner.getCostFactory().makeCost(  // 返回总成本
        rowCount + leftRowCount, 0, 0).plus(rescanCost);  // CPU成本=输出行数+左表行数,I/O成本=0,加上重扫描成本
  }

  @Override public RelWriter explainTerms(RelWriter pw) {  // 重写方法:解释术语,生成关系表达式的文本描述
    super.explainTerms(pw);  // 调用父类方法,输出基类Join的解释信息(如连接条件、连接类型等)
    return pw.item("batchSize", variablesSet.size());  // 添加批量大小项到解释信息中,值为相关变量集合的大小
  }

  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) {  // 重写方法:实现可枚举关系,生成Java代码实现
    final BlockBuilder builder = new BlockBuilder();  // 创建代码块构建器,用于构建方法体
    final Result leftResult =  // 实现左子节点,生成左表的代码
        implementor.visitChild(this, 0, (EnumerableRel) left, pref);  // 访问第0个子节点(左表),传入当前节点、子节点ID、左表对象和偏好设置
    final Expression leftExpression =  // 创建左表表达式
        builder.append(  // 将左表代码块添加到构建器
            "left", leftResult.block);  // 使用"left"作为变量名,值为左表的结果代码块

    List<String> corrVar = new ArrayList<>();  // 创建相关变量名称列表,用于存储相关变量的名称
    for (CorrelationId c : variablesSet) {  // 遍历相关变量集合
      corrVar.add(c.getName());  // 将每个相关变量的名称添加到列表中
    }  // 结束循环

    final BlockBuilder corrBlock = new BlockBuilder();  // 创建相关变量代码块构建器,用于构建相关变量的初始化代码
    final Type corrVarType = leftResult.physType.getJavaRowType();  // 获取相关变量的Java类型,即左表行的Java类型
    ParameterExpression corrArg;  // 声明相关变量参数表达式,用于表示单个相关变量
    final ParameterExpression corrArgList =  // 创建相关变量列表参数表达式
        Expressions.parameter(Modifier.FINAL,  // 使用final修饰符,表示不可变
            List.class, "corrList" + Integer.toUnsignedString(this.getId()));  // 参数类型为List,参数名为"corrList"加上当前节点ID(确保唯一性)

    // Declare batchSize correlation variables  // 注释:声明批量大小的相关变量
    if (!Primitive.is(corrVarType)) {  // 如果相关变量类型不是原始类型(即对象类型)
      for (int c = 0; c < corrVar.size(); c++) {  // 遍历所有相关变量
        corrArg =  // 创建相关变量参数表达式
            Expressions.parameter(Modifier.FINAL,  // 使用final修饰符
                corrVarType, corrVar.get(c));  // 参数类型为相关变量类型,参数名为相关变量名称
        final DeclarationStatement decl =  // 创建声明语句,用于声明和初始化相关变量
            Expressions.declare(Modifier.FINAL, corrArg,  // 声明final变量,变量名为corrArg
                Expressions.convert_(  // 进行类型转换
                    Expressions.call(corrArgList,  // 调用列表的get方法
                        BuiltInMethod.LIST_GET.method,  // 使用List.get()方法
                        Expressions.constant(c)),  // 获取索引为c的元素
                    corrVarType));  // 转换为相关变量类型
        corrBlock.add(decl);  // 将声明语句添加到相关变量代码块
        implementor.registerCorrelVariable(corrVar.get(c), corrArg,  // 注册相关变量,使其可以在后续代码中使用
            corrBlock, leftResult.physType);  // 传入变量名、参数表达式、代码块和物理类型
      }  // 结束循环
    } else {  // 如果相关变量类型是原始类型(如int、double等)
      for (int c = 0; c < corrVar.size(); c++) {  // 遍历所有相关变量
        corrArg =  // 创建装箱后的相关变量参数表达式
            Expressions.parameter(Modifier.FINAL,  // 使用final修饰符
                Primitive.box(corrVarType), "$box" + corrVar.get(c));  // 参数类型为装箱类型(如Integer),参数名加"$box"前缀
        final DeclarationStatement decl =  // 创建声明语句,用于声明和初始化装箱后的相关变量
            Expressions.declare(Modifier.FINAL, corrArg,  // 声明final变量
                Expressions.call(corrArgList,  // 调用列表的get方法
                    BuiltInMethod.LIST_GET.method,  // 使用List.get()方法
                    Expressions.constant(c)));  // 获取索引为c的元素
        corrBlock.add(decl);  // 将声明语句添加到相关变量代码块
        final ParameterExpression corrRef =  // 创建拆箱后的相关变量引用
            (ParameterExpression) corrBlock.append(corrVar.get(c),  // 添加到代码块,使用原始变量名
                Expressions.unbox(corrArg));  // 将装箱变量拆箱为原始类型
        implementor.registerCorrelVariable(corrVar.get(c), corrRef,  // 注册相关变量,使用拆箱后的引用
            corrBlock, leftResult.physType);  // 传入变量名、拆箱后的引用、代码块和物理类型
      }  // 结束循环
    }  // 结束条件判断
    final Result rightResult =  // 实现右子节点,生成右表的代码
        implementor.visitChild(this, 1, (EnumerableRel) right, pref);  // 访问第1个子节点(右表),传入当前节点、子节点ID、右表对象和偏好设置

    corrBlock.add(rightResult.block);  // 将右表的结果代码块添加到相关变量代码块中
    for (String c : corrVar) {  // 遍历所有相关变量名称
      implementor.clearCorrelVariable(c);  // 清除相关变量注册,释放资源
    }  // 结束循环

    final PhysType physType =  // 创建物理类型对象,表示结果集的Java类型
        PhysTypeImpl.of(  // 使用物理类型实现类创建
            implementor.getTypeFactory(),  // 传入类型工厂
            getRowType(),  // 传入结果集的行类型
            pref.prefer(JavaRowFormat.CUSTOM));  // 传入偏好的行格式(自定义格式)
    final Expression selector =  // 创建选择器表达式,用于将左右表的行合并为结果行
        EnumUtils.joinSelector(  // 调用工具方法创建连接选择器
            joinType, physType,  // 传入连接类型和结果物理类型
            ImmutableList.of(leftResult.physType, rightResult.physType));  // 传入左右表的物理类型列表

    final Expression predicate =  // 创建谓词表达式,用于过滤连接结果
        EnumUtils.generatePredicate(implementor, getCluster().getRexBuilder(), left, right,  // 调用工具方法生成连接条件谓词
            leftResult.physType, rightResult.physType, condition);  // 传入实现器、表达式构建器、左右子节点、左右表物理类型和连接条件

    builder.append(  // 将批量连接调用添加到主代码块
        Expressions.call(BuiltInMethod.CORRELATE_BATCH_JOIN.method,  // 调用内置的批量相关连接方法
            Expressions.constant(EnumUtils.toLinq4jJoinType(joinType)),  // 参数1:连接类型(转换为LINQ4J的JoinType枚举)
            leftExpression,  // 参数2:左表表达式
            Expressions.lambda(corrBlock.toBlock(), corrArgList),  // 参数3:lambda表达式,用于处理相关变量列表
            selector,  // 参数4:选择器,用于合并左右表行
            predicate,  // 参数5:谓词,用于过滤连接结果
            Expressions.constant(variablesSet.size())));  // 参数6:批量大小,即相关变量集合的大小
    return implementor.result(physType, builder.toBlock());  // 返回实现结果,包含结果物理类型和生成的代码块
  }  // 结束方法实现
}  // 结束类定义
