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
package org.apache.calcite.adapter.enumerable; // 声明包名，该类属于org.apache.calcite.adapter.enumerable包，这是Calcite中可枚举适配器相关的包

import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入BlockBuilder类，用于构建代码块，是LINQ4J表达式树构建工具
import org.apache.calcite.linq4j.tree.Expression; // 导入Expression类，表示LINQ4J表达式树中的表达式节点
import org.apache.calcite.linq4j.tree.Expressions; // 导入Expressions工具类，提供创建各种表达式节点的静态方法
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系表达式优化集群，包含优化器的共享状态
import org.apache.calcite.plan.RelOptCost; // 导入RelOptCost类，表示关系操作的成本估算
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner类，表示关系优化器，用于优化查询计划
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系表达式的特征集合，如排序、分区等
import org.apache.calcite.rel.RelCollation; // 导入RelCollation类，表示排序规范，定义字段的排序顺序
import org.apache.calcite.rel.RelCollationTraitDef; // 导入RelCollationTraitDef类，表示排序特征定义
import org.apache.calcite.rel.RelCollations; // 导入RelCollations工具类，提供创建排序规范的静态方法
import org.apache.calcite.rel.RelFieldCollation; // 导入RelFieldCollation类，表示单个字段的排序方向和空值处理
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系表达式，是Calcite中所有关系操作的基础接口
import org.apache.calcite.rel.core.AsofJoin; // 导入AsofJoin抽象类，表示ASOF（As-Of）连接操作的基类
import org.apache.calcite.rel.core.CorrelationId; // 导入CorrelationId类，表示关联标识符，用于标识子查询中的关联变量
import org.apache.calcite.rel.core.Join; // 导入Join抽象类，表示连接操作的基类
import org.apache.calcite.rel.core.JoinRelType; // 导入JoinRelType枚举，表示连接类型（内连接、左外连接等）
import org.apache.calcite.rel.logical.LogicalAsofJoin; // 导入LogicalAsofJoin类，表示逻辑层的ASOF连接操作
import org.apache.calcite.rel.metadata.RelMdCollation; // 导入RelMdCollation类，提供排序相关的元数据计算
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，用于查询关系表达式的元数据
import org.apache.calcite.rex.RexCall; // 导入RexCall类，表示函数调用表达式
import org.apache.calcite.rex.RexInputRef; // 导入RexInputRef类，表示对输入字段的引用
import org.apache.calcite.rex.RexNode; // 导入RexNode接口，表示行表达式，是Calcite中所有表达式的基础接口
import org.apache.calcite.sql.SqlKind; // 导入SqlKind枚举，表示SQL操作符的类型（如比较、算术、逻辑等）
import org.apache.calcite.util.BuiltInMethod; // 导入BuiltInMethod类，定义内置方法的枚举，用于反射调用
import org.apache.calcite.util.Pair; // 导入Pair类，表示键值对，用于存储两个相关联的对象

import com.google.common.collect.ImmutableList; // 导入ImmutableList类，表示不可变列表，Guava库提供的集合工具

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的值

import java.util.ArrayList; // 导入ArrayList类，表示动态数组，Java集合框架的一部分
import java.util.List; // 导入List接口，表示有序集合，Java集合框架的一部分
import java.util.Set; // 导入Set接口，表示不重复元素的集合，Java集合框架的一部分

/** Implementation of {@link LogicalAsofJoin} in
 * {@link EnumerableConvention enumerable calling convention}. */
// 类注释：这是LogicalAsofJoin在可枚举调用约定（EnumerableConvention）下的实现类
// ASOF（As-Of）连接是一种特殊的连接操作，它将左表的每一行与右表中"最近"的匹配行连接
// "最近"通常基于时间戳字段，例如：左表的时间戳必须小于等于右表的时间戳，且差值最小
// 该类实现了EnumerableRel接口，说明它可以通过LINQ4J生成可执行的Java代码
public class EnumerableAsofJoin extends AsofJoin implements EnumerableRel { // 定义类EnumerableAsofJoin，继承自AsofJoin抽象类，实现EnumerableRel接口
  /** Creates an EnumerableAsofJoin.
   *
   * <p>Use {@link #create} unless you know what you're doing. */
  // 方法注释：构造方法，创建一个EnumerableAsofJoin实例
  // 参数说明：
  //   - cluster: 关系表达式优化集群，包含优化器的共享状态和类型工厂
  //   - traits: 关系表达式的特征集合，定义了该节点的物理属性（如排序、分区等）
  //   - left: 左输入关系节点，表示连接操作左边的表
  //   - right: 右输入关系节点，表示连接操作右边的表
  //   - condition: 连接条件，通常是等值条件（例如：left.key = right.key）
  //   - matchCondition: 匹配条件，用于ASOF连接的时间戳比较（例如：left.timestamp <= right.timestamp）
  //   - variablesSet: 关联变量集合，用于处理子查询中的相关性
  //   - joinType: 连接类型（INNER、LEFT、RIGHT等）
  // 注意：建议使用静态工厂方法create()来创建实例，除非你清楚自己在做什么
  protected EnumerableAsofJoin( // 定义受保护的构造方法
      RelOptCluster cluster, // 参数：关系表达式优化集群
      RelTraitSet traits, // 参数：特征集合
      RelNode left, // 参数：左输入关系节点
      RelNode right, // 参数：右输入关系节点
      RexNode condition, // 参数：连接条件
      RexNode matchCondition, // 参数：匹配条件（时间戳比较）
      Set<CorrelationId> variablesSet, // 参数：关联变量集合
      JoinRelType joinType) { // 参数：连接类型
    super( // 调用父类AsofJoin的构造方法
        cluster, // 传递集群参数
        traits, // 传递特征集合参数
        ImmutableList.of(), // 传递空的字段集合参数
        left, // 传递左输入参数
        right, // 传递右输入参数
        condition, // 传递连接条件参数
        matchCondition, // 传递匹配条件参数
        variablesSet, // 传递关联变量集合参数
        joinType); // 传递连接类型参数
  } // 构造方法结束

  /** Creates an EnumerableAsofJoin. */
  // 方法注释：静态工厂方法，创建一个EnumerableAsofJoin实例
  // 这是推荐的创建实例的方式，它会自动计算和设置适当的特征集合
  // 参数说明：
  //   - left: 左输入关系节点
  //   - right: 右输入关系节点
  //   - condition: 连接条件
  //   - matchCondition: 匹配条件（时间戳比较）
  //   - variablesSet: 关联变量集合
  //   - joinType: 连接类型
  // 返回值：创建好的EnumerableAsofJoin实例
  public static EnumerableAsofJoin create( // 定义静态工厂方法
      RelNode left, // 参数：左输入关系节点
      RelNode right, // 参数：右输入关系节点
      RexNode condition, // 参数：连接条件
      RexNode matchCondition, // 参数：匹配条件
      Set<CorrelationId> variablesSet, // 参数：关联变量集合
      JoinRelType joinType) { // 参数：连接类型
    final RelOptCluster cluster = left.getCluster(); // 从左输入节点获取关系表达式优化集群
    final RelMetadataQuery mq = cluster.getMetadataQuery(); // 从集群获取元数据查询对象，用于查询各种元数据
    final RelTraitSet traitSet = // 定义特征集合变量
        cluster.traitSetOf(EnumerableConvention.INSTANCE) // 创建包含可枚举约定特征的特征集合
            .replaceIfs(RelCollationTraitDef.INSTANCE, // 如果需要，替换排序特征
                () -> RelMdCollation.enumerableHashJoin(mq, left, right, joinType)); // 使用元数据查询计算哈希连接的排序特征
    return new EnumerableAsofJoin(cluster, traitSet, left, right, condition, matchCondition, // 创建并返回新的EnumerableAsofJoin实例
        variablesSet, joinType); // 传递关联变量集合和连接类型
  } // 静态工厂方法结束

  @Override public EnumerableAsofJoin copy(RelTraitSet traitSet, RexNode condition, // 重写copy方法，用于复制关系节点
                                           RelNode left, RelNode right, JoinRelType joinType,
                                           boolean semiJoinDone) {
    // This method does not know about the matchCondition, so it should not be called
    // 注释：这个方法不知道matchCondition（匹配条件），所以不应该被调用
    // 因为ASOF连接需要matchCondition来执行时间戳比较，而这个方法没有该参数
    throw new RuntimeException("This method should not be called"); // 抛出运行时异常，表示不应该调用此方法
  } // copy方法结束

  @Override public Join copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法，用于复制Join节点
    assert inputs.size() == 2; // 断言输入列表必须包含2个节点（左输入和右输入）
    return new EnumerableAsofJoin( // 创建并返回新的EnumerableAsofJoin实例
        getCluster(), traitSet, inputs.get(0), inputs.get(1), // 传递集群、特征集合、左输入、右输入
            getCondition(), matchCondition, variablesSet, joinType); // 传递连接条件、匹配条件、关联变量集合、连接类型
  } // copy方法结束

  @Override public @Nullable Pair<RelTraitSet, List<RelTraitSet>> passThroughTraits( // 重写passThroughTraits方法，用于传递特征
      final RelTraitSet required) { // 参数：要求的特征集合
    return EnumerableTraitsUtils.passThroughTraitsForJoin( // 调用工具类方法，计算需要传递给子节点的特征
        required, joinType, left.getRowType().getFieldCount(), getTraitSet()); // 传递要求的特征、连接类型、左表字段数、当前特征集合
  } // passThroughTraits方法结束

  @Override public @Nullable Pair<RelTraitSet, List<RelTraitSet>> deriveTraits( // 重写deriveTraits方法，用于从子节点派生特征
      final RelTraitSet childTraits, final int childId) { // 参数：子节点的特征集合，子节点ID
    // should only derive traits (limited to collation for now) from left join input.
    // 注释：应该只从左连接输入派生特征（目前仅限于排序特征）
    // 这意味着排序信息从左表继承，因为左表在ASOF连接中通常决定了输出的顺序
    return EnumerableTraitsUtils.deriveTraitsForJoin( // 调用工具类方法，从子节点派生特征
        childTraits, childId, joinType, getTraitSet(), right.getTraitSet()); // 传递子节点特征、子节点ID、连接类型、当前特征、右表特征
  } // deriveTraits方法结束

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写computeSelfCost方法，计算自己的成本
      RelMetadataQuery mq) { // 参数：优化器、元数据查询对象
    double rowCount = mq.getRowCount(this); // 从元数据查询获取当前节点的行数估计
    return planner.getCostFactory().makeCost(rowCount, 0, 0); // 创建成本对象，只考虑行数，CPU和IO成本都设为0
  } // computeSelfCost方法结束

  /** Generates the function that compares two rows from the right collection on
   * their timestamp field.
   *
   * @param rightCollectionType  Type of data in right collection.
   * @param kind                 Comparison kind.
   * @param timestampFieldIndex  Index of the field that is the timestamp field.
   */
  // 方法注释：生成一个比较函数，用于比较右集合中两行的时间戳字段
  // 参数说明：
  //   - rightCollectionType: 右集合中数据的物理类型
  //   - kind: 比较操作符的类型（小于、小于等于、大于、大于等于）
  //   - timestampFieldIndex: 时间戳字段的索引位置
  // 返回值：生成的比较函数表达式
  private static Expression generateTimestampComparator( // 定义静态私有方法，生成时间戳比较器
      PhysType rightCollectionType, SqlKind kind, int timestampFieldIndex) { // 参数：右集合物理类型、比较类型、时间戳字段索引
    RelFieldCollation.Direction direction; // 定义排序方向变量
    switch (kind) { // 根据比较类型确定排序方向
    case LESS_THAN: // 如果是小于操作符
    case LESS_THAN_OR_EQUAL: // 或小于等于操作符
      direction = RelFieldCollation.Direction.ASCENDING; // 设置方向为升序
      break; // 跳出switch
    case GREATER_THAN: // 如果是大于操作符
    case GREATER_THAN_OR_EQUAL: // 或大于等于操作符
      direction = RelFieldCollation.Direction.DESCENDING; // 设置方向为降序
      break; // 跳出switch
    default: // 如果是其他操作符
      throw new RuntimeException("Unexpected timestamp comparison in ASOF join " + kind); // 抛出运行时异常，表示遇到意外的比较操作符
    } // switch结束

    final List<RelFieldCollation> fieldCollations = new ArrayList<>(1); // 创建字段排序列表，初始容量为1
    fieldCollations.add( // 添加字段排序规则
        new RelFieldCollation(timestampFieldIndex, direction, // 创建字段排序对象，指定字段索引、方向、空值处理
            RelFieldCollation.NullDirection.FIRST)); // 空值排在最前面
    final RelCollation collation = RelCollations.of(fieldCollations); // 根据字段排序列表创建排序规范
    return rightCollectionType.generateComparator(collation); // 根据排序规范生成比较器表达式
  } // generateTimestampComparator方法结束

  /** Extracts from a comparison 'call' the index of the field from
   * the inner collection that is used in the comparison. */
  // 方法注释：从比较调用表达式中提取内集合（右表）中用于比较的字段索引
  // 这个方法用于确定哪个字段是时间戳字段
  // 参数说明：
  //   - call: 比较调用表达式（例如：left.timestamp <= right.timestamp）
  // 返回值：时间戳字段在右表中的索引位置
  private int getTimestampFieldIndex(RexCall call) { // 定义私有方法，获取时间戳字段索引
    int timestampFieldIndex; // 定义时间戳字段索引变量
    int leftFieldCount = left.getRowType().getFieldCount(); // 获取左表的字段数量
    List<RexNode> operands = call.getOperands(); // 获取比较表达式的操作数列表（通常是两个）
    assert operands.size() == 2; // 断言操作数数量必须为2（二元比较）
    RexNode compareLeft = operands.get(0); // 获取第一个操作数（比较表达式的左边）
    RexNode compareRight = operands.get(1); // 获取第二个操作数（比较表达式的右边）
    assert compareLeft instanceof RexInputRef; // 断言左边操作数必须是输入引用
    assert compareRight instanceof RexInputRef; // 断言右边操作数必须是输入引用
    RexInputRef leftInputRef = (RexInputRef) compareLeft; // 将左边操作数转换为输入引用
    RexInputRef rightInputRef = (RexInputRef) compareRight; // 将右边操作数转换为输入引用
    // We know for sure that these two come from the inner and outer collection respectively,
    // but we don't know which is which
    // 注释：我们知道这两个引用分别来自内集合和外集合，但不知道哪个是哪个
    if (leftInputRef.getIndex() < leftFieldCount) { // 如果左边的索引小于左表字段数
      // Left input comes from the left collection
      // 注释：左边的输入来自左集合
      timestampFieldIndex = rightInputRef.getIndex() - leftFieldCount; // 计算右表中的时间戳字段索引（减去左表字段数）
    } else { // 否则
      // Left input comes from the right collection
      // 注释：左边的输入来自右集合
      timestampFieldIndex = leftInputRef.getIndex() - leftFieldCount; // 计算右表中的时间戳字段索引
    } // if结束
    return timestampFieldIndex; // 返回时间戳字段索引
  } // getTimestampFieldIndex方法结束

  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) { // 重写implement方法，实现可枚举代码生成
    BlockBuilder builder = new BlockBuilder(); // 创建代码块构建器，用于构建生成的代码块
    final Result leftResult = // 定义左子节点的实现结果
        implementor.visitChild(this, 0, (EnumerableRel) left, pref); // 访问左子节点，生成左表的实现代码
    Expression leftExpression = // 定义左表表达式变量
        builder.append( // 将左表实现代码添加到代码块
            "left", leftResult.block); // 使用变量名"left"，添加左表的代码块
    final Result rightResult = // 定义右子节点的实现结果
        implementor.visitChild(this, 1, (EnumerableRel) right, pref); // 访问右子节点，生成右表的实现代码
    Expression rightExpression = // 定义右表表达式变量
        builder.append( // 将右表实现代码添加到代码块
            "right", rightResult.block); // 使用变量名"right"，添加右表的代码块
    final PhysType physType = // 定义输出行的物理类型
        PhysTypeImpl.of( // 创建物理类型实现
            implementor.getTypeFactory(), getRowType(), pref.preferArray()); // 使用类型工厂、行类型、数组偏好
    // ASOF joins conditions are restricted to equalities
    // 注释：ASOF连接的条件限制为等值条件
    // 这意味着除了时间戳比较外，其他条件必须是等值连接
    assert joinInfo.nonEquiConditions.isEmpty(); // 断言非等值条件列表为空

    // From the match condition we need to find out the kind of comparison performed
    // and the timestamp field in the right collection.
    // 注释：从匹配条件中，我们需要找出执行的比较类型和右集合中的时间戳字段
    assert matchCondition instanceof RexCall; // 断言匹配条件必须是函数调用
    RexCall call = (RexCall) matchCondition; // 将匹配条件转换为函数调用
    SqlKind kind = call.getKind(); // 获取比较操作符的类型（如LESS_THAN_OR_EQUAL）
    int timestampFieldIndex = getTimestampFieldIndex(call); // 获取时间戳字段在右表中的索引

    Expression timestampComparator = // 定义时间戳比较器表达式
        generateTimestampComparator(rightResult.physType, kind, timestampFieldIndex); // 生成时间戳比较器

    Expression matchPredicate = // 定义匹配谓词表达式
        EnumUtils.generatePredicate(implementor, getCluster().getRexBuilder(), // 生成匹配谓词
            left, right, leftResult.physType, rightResult.physType, matchCondition); // 传递实现器、表达式构建器、左右表、物理类型、匹配条件
    return implementor.result( // 返回实现结果
        physType, // 输出物理类型
        builder.append( // 将ASOF连接调用添加到代码块
            Expressions.call( // 创建方法调用表达式
                leftExpression, // 在左表表达式上调用方法
                BuiltInMethod.ASOF_JOIN.method, // 调用ASOF_JOIN内置方法
                Expressions.list( // 创建参数列表
                    rightExpression, // 参数1：右表表达式
                    // outer key selector
                    // 注释：外键选择器，用于从左表提取连接键
                    leftResult.physType.generateAccessorWithoutNulls(joinInfo.leftKeys), // 生成左表连接键的访问器
                    // inner key selector
                    // 注释：内键选择器，用于从右表提取连接键
                    rightResult.physType.generateAccessorWithoutNulls(joinInfo.rightKeys), // 生成右表连接键的访问器
                    // result selector
                    // 注释：结果选择器，用于组合左右表的输出行
                    EnumUtils.joinSelector(joinType, // 生成连接结果选择器
                        physType, // 输出物理类型
                        ImmutableList.of( // 左右表物理类型的不可变列表
                            leftResult.physType, rightResult.physType))) // 左表和右表的物理类型
                    // match comparator
                    // 注释：匹配比较器，用于时间戳比较
                    .append(matchPredicate) // 添加匹配谓词
                    // comparator for the columns used as "timestamps"
                    // 注释：用于时间戳列的比较器
                    .append(timestampComparator) // 添加时间戳比较器
                    // generatesNullOnRight
                    // 注释：是否在右侧生成空值（用于左外连接）
                    .append(Expressions.constant(joinType.generatesNullsOnRight())))) // 添加是否生成空值的常量
            .toBlock()); // 将代码块转换为最终形式
  } // implement方法结束
} // 类结束
