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
// Apache Calcite 是一个动态数据管理框架，提供 SQL 解析、优化、执行等功能
// 本文件位于可枚举适配器包中，实现了可枚举调用约定的相关操作
package org.apache.calcite.adapter.enumerable;

// 导入代码块构建器，用于构建 Java 代码块
import org.apache.calcite.linq4j.tree.BlockBuilder;
// 导入表达式类，用于表示 LINQ 表达式树中的表达式节点
import org.apache.calcite.linq4j.tree.Expression;
// 导入表达式工具类，用于创建各种表达式
import org.apache.calcite.linq4j.tree.Expressions;
// 导入参数表达式类，用于表示方法参数
import org.apache.calcite.linq4j.tree.ParameterExpression;
// 导入原始类型工具类，用于处理基本类型的装箱和拆箱
import org.apache.calcite.linq4j.tree.Primitive;
// 导入派生模式枚举，定义了特征派生的模式
import org.apache.calcite.plan.DeriveMode;
// 导入关系优化集群类，包含共享的优化上下文
import org.apache.calcite.plan.RelOptCluster;
// 导入关系特征集合类，定义了关系节点的物理属性
import org.apache.calcite.plan.RelTraitSet;
// 导入关系排序特征定义类，定义了排序特征
import org.apache.calcite.rel.RelCollationTraitDef;
// 导入关系节点接口，所有关系表达式的基类
import org.apache.calcite.rel.RelNode;
// 导入相关联类，表示相关子查询的关联操作
import org.apache.calcite.rel.core.Correlate;
// 导入关联 ID 类，唯一标识一个相关变量
import org.apache.calcite.rel.core.CorrelationId;
// 导入连接关系类型枚举，定义了 INNER、LEFT、SEMI 等连接类型
import org.apache.calcite.rel.core.JoinRelType;
// 导入关系元数据排序类，提供排序相关的元数据计算
import org.apache.calcite.rel.metadata.RelMdCollation;
// 导入关系元数据查询类，用于查询关系的元数据
import org.apache.calcite.rel.metadata.RelMetadataQuery;
// 导入内置方法枚举，定义了常用的内置方法
import org.apache.calcite.util.BuiltInMethod;
// 导入不可变位集合类，用于表示列位图
import org.apache.calcite.util.ImmutableBitSet;
// 导入配对类，用于存储两个值的对
import org.apache.calcite.util.Pair;

// 导入 Google Guava 的不可变列表类，提供不可变的列表实现
import com.google.common.collect.ImmutableList;

// 导入可空注解，用于标记可能为 null 的返回值
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入修饰符类，用于获取 Java 方法、字段的修饰符
import java.lang.reflect.Modifier;
// 导入类型类，表示 Java 类型
import java.lang.reflect.Type;
// 导入列表接口，Java 集合框架的基础接口
import java.util.List;

/**
 * 可枚举相关联类
 * 
 * 这是 Correlate 关系操作在可枚举调用约定（EnumerableConvention）下的实现
 * 
 * 核心作用：
 * 1. 实现相关子查询的关联操作，即子查询引用了外层查询的变量
 * 2. 在可枚举调用约定下，将相关联操作转换为可执行的 LINQ 表达式代码
 * 3. 支持嵌套循环连接的实现，左输入作为外层循环，右输入作为内层循环
 * 4. 处理相关变量的注册和传递，使内层查询可以访问外层查询的变量
 * 
 * 典型应用场景：
 * - EXISTS 子查询：SELECT * FROM emp WHERE EXISTS (SELECT * FROM dept WHERE emp.deptno = dept.deptno)
 * - IN 子查询：SELECT * FROM emp WHERE emp.deptno IN (SELECT deptno FROM dept WHERE loc = 'NY')
 * - 标量子查询：SELECT emp.*, (SELECT COUNT(*) FROM dept WHERE emp.deptno = dept.deptno) FROM emp
 * 
 * 实现原理：
 * 1. 使用嵌套循环连接算法：遍历左输入的每一行，对每一行执行右输入的查询
 * 2. 通过相关变量（CorrelationId）将左输入的当前行传递给右输入
 * 3. 利用 LINQ 的 lambda 表达式实现相关变量的捕获和传递
 * 4. 根据连接类型（INNER、LEFT、SEMI）生成相应的连接逻辑
 */
public class EnumerableCorrelate extends Correlate
    implements EnumerableRel {

  /**
   * 构造函数：创建一个可枚举相关联节点
   * 
   * 参数说明：
   * @param cluster 关系优化集群，包含共享的优化上下文（如类型工厂、表达式工厂等）
   * @param traits 关系特征集合，定义了该节点的物理属性（如调用约定、排序等）
   * @param left 左子节点（外层查询），作为外层循环的数据源
   * @param right 右子节点（内层查询），作为内层循环的数据源，可以访问左子节点的相关变量
   * @param correlationId 关联 ID，唯一标识一个相关变量，用于在右子查询中引用左子查询的变量
   * @param requiredColumns 必需列位图，指定右子查询需要从左子查询中访问哪些列
   * @param joinType 连接类型（INNER、LEFT、SEMI、ANTI），决定连接的语义和结果集
   * 
   * 调用父类构造函数，传入空的条件列表（相关联不使用连接条件，而是使用相关变量）
   */
  public EnumerableCorrelate(RelOptCluster cluster, RelTraitSet traits,
      RelNode left, RelNode right,
      CorrelationId correlationId,
      ImmutableBitSet requiredColumns, JoinRelType joinType) {
    // 调用父类 Correlate 的构造函数，初始化所有必要字段
    // ImmutableList.of() 表示相关联不使用显式的连接条件，而是通过相关变量实现关联
    super(cluster, traits, ImmutableList.of(), left, right, correlationId, requiredColumns,
        joinType);
  }

  /**
   * 静态工厂方法：创建一个可枚举相关联节点
   * 
   * 这是创建 EnumerableCorrelate 的推荐方式，会自动处理特征集合的设置
   * 
   * 参数说明：
   * @param left 左子节点（外层查询）
   * @param right 右子节点（内层查询）
   * @param correlationId 关联 ID，标识相关变量
   * @param requiredColumns 必需列位图，指定右子查询需要访问的左子查询列
   * @param joinType 连接类型
   * 
   * @return 新创建的 EnumerableCorrelate 节点
   * 
   * 实现细节：
   * 1. 从左子节点获取优化集群和元数据查询
   * 2. 创建特征集合，设置调用约定为 EnumerableConvention
   * 3. 如果存在排序特征，使用 RelMdCollation 计算相关联的排序属性
   * 4. 创建并返回新的 EnumerableCorrelate 实例
   */
  public static EnumerableCorrelate create(
      RelNode left,
      RelNode right,
      CorrelationId correlationId,
      ImmutableBitSet requiredColumns,
      JoinRelType joinType) {
    // 从左子节点获取优化集群，集群包含类型工厂、表达式工厂等共享资源
    final RelOptCluster cluster = left.getCluster();
    // 获取元数据查询对象，用于查询关系的元数据信息（如行数、排序等）
    final RelMetadataQuery mq = cluster.getMetadataQuery();
    // 创建特征集合：
    // 1. 设置调用约定为 EnumerableConvention（可枚举调用约定）
    // 2. 如果支持排序特征，则使用 RelMdCollation 计算相关联的排序属性
    //    相关联的排序属性取决于左输入、右输入和连接类型
    final RelTraitSet traitSet =
        cluster.traitSetOf(EnumerableConvention.INSTANCE)
            .replaceIfs(RelCollationTraitDef.INSTANCE,
                () -> RelMdCollation.enumerableCorrelate(mq, left, right, joinType));
    // 创建并返回新的 EnumerableCorrelate 实例
    return new EnumerableCorrelate(
        cluster,
        traitSet,
        left,
        right,
        correlationId,
        requiredColumns,
        joinType);
  }

  /**
   * 复制方法：创建该节点的副本，可以修改部分属性
   * 
   * 这是 RelNode 接口的核心方法，用于在优化过程中创建节点的变体
   * 
   * 参数说明：
   * @param traitSet 新的特征集合
   * @param left 新的左子节点
   * @param right 新的右子节点
   * @param correlationId 新的关联 ID
   * @param requiredColumns 新的必需列位图
   * @param joinType 新的连接类型
   * 
   * @return 新的 EnumerableCorrelate 节点
   * 
   * 使用场景：
   * - 优化器应用规则时，需要创建修改后的节点
   * - 改变特征集合（如添加排序特征）
   * - 替换子节点
   */
  @Override public EnumerableCorrelate copy(RelTraitSet traitSet,
      RelNode left, RelNode right, CorrelationId correlationId,
      ImmutableBitSet requiredColumns, JoinRelType joinType) {
    // 创建新的 EnumerableCorrelate 实例，保留原有的集群，使用传入的新参数
    return new EnumerableCorrelate(getCluster(),
        traitSet, left, right, correlationId, requiredColumns, joinType);
  }

  /**
   * 传递特征方法：将父节点要求的特征传递给子节点
   * 
   * 这个方法实现了特征的向下传递，让子节点知道父节点期望的属性
   * 
   * 参数说明：
   * @param required 父节点要求的特征集合
   * 
   * @return 配对对象，第一个元素是本节点可以满足的特征集合，
   *         第二个元素是传递给每个子节点的特征集合列表
   * 
   * 实现细节：
   * - 对于相关联，只有左输入可以保留排序，因为左输入是外层循环
   * - 右输入的排序会被忽略，因为对于每一行左输入数据，都要遍历右输入
   * - 使用 EnumerableTraitsUtils 工具类处理特征的传递逻辑
   */
  @Override public @Nullable Pair<RelTraitSet, List<RelTraitSet>> passThroughTraits(
      final RelTraitSet required) {
    // 可枚举相关联的特征传递只将排序特征传递给左输入
    // 这是因为可枚举相关联总是使用左输入作为外层循环，因此只有左输入可以保持排序
    // 使用工具类处理特征的传递，考虑连接类型和左输入的字段数
    return EnumerableTraitsUtils.passThroughTraitsForJoin(
        required, joinType, left.getRowType().getFieldCount(), getTraitSet());
  }

  /**
   * 派生特征方法：从子节点的特征派生出本节点的特征
   * 
   * 这个方法实现了特征的向上派生，让父节点可以了解子节点的属性
   * 
   * 参数说明：
   * @param childTraits 子节点的特征集合
   * @param childId 子节点的 ID（0 表示左子节点，1 表示右子节点）
   * 
   * @return 配对对象，第一个元素是本节点可以满足的特征集合，
   *         第二个元素是传递给每个子节点的特征集合列表
   * 
   * 实现细节：
   * - 只从左输入派生特征（目前只支持排序特征）
   * - 右输入的特征不影响本节点的特征
   * - 使用 EnumerableTraitsUtils 工具类处理特征的派生逻辑
   */
  @Override public @Nullable Pair<RelTraitSet, List<RelTraitSet>> deriveTraits(
      final RelTraitSet childTraits, final int childId) {
    // 应该只从左输入派生特征（目前仅限于排序特征）
    // 使用工具类处理特征的派生，考虑连接类型、当前特征集和右输入的特征集
    return EnumerableTraitsUtils.deriveTraitsForJoin(
        childTraits, childId, joinType, traitSet, right.getTraitSet());
  }

  /**
   * 获取派生模式方法：定义特征的派生策略
   * 
   * @return 派生模式，这里是 LEFT_FIRST，表示优先从左子节点派生特征
   * 
   * 派生模式说明：
   * - LEFT_FIRST：优先从左子节点派生特征
   * - LEFT_SECOND：优先从右子节点派生特征
   * - LEFT_PROHIBITED：禁止从左子节点派生特征
   */
  @Override public DeriveMode getDeriveMode() {
    // 返回 LEFT_FIRST 模式，表示优先从左子节点派生特征
    return DeriveMode.LEFT_FIRST;
  }

  /**
   * 实现方法：将相关联节点转换为可执行的 LINQ 表达式
   * 
   这是 EnumerableRel 接口的核心方法，负责将关系节点转换为 Java 代码（LINQ 表达式）
   * 
   * 参数说明：
   * @param implementor 实现器，负责管理整个实现过程，包括变量注册、子节点访问等
   * @param pref 偏好设置，指定生成的代码格式偏好（如数组格式、自定义格式等）
   * 
   * @return 结果对象，包含生成的代码块和物理类型信息
   * 
   * 实现原理：
   * 1. 访问左子节点，生成左输入的代码
   * 2. 创建相关变量（correlate variable），用于在右子查询中引用左子查询的当前行
   * 3. 注册相关变量，使右子查询可以访问它
   * 4. 访问右子节点，生成右输入的代码（此时可以使用相关变量）
   * 5. 清除相关变量注册
   * 6. 生成连接选择器（selector），用于合并左右输入的结果
   * 7. 调用 LINQ 的 correlateJoin 方法，生成最终的连接代码
   * 
   * 代码生成示例（伪代码）：
   * left.correlateJoin(
   *   joinType,
   *   corrVar -> {
   *     // 在这里可以使用 corrVar 访问左输入的当前行
   *     return right;  // 右输入的代码，可以使用 corrVar
   *   },
   *   (leftRow, rightRow) -> {
   *     // 合并左右输入的行
   *     return combinedRow;
   *   }
   * )
   */
  @Override public Result implement(EnumerableRelImplementor implementor,
      Prefer pref) {
    // 创建代码块构建器，用于构建最终的 Java 代码块
    final BlockBuilder builder = new BlockBuilder();
    // 访问左子节点（索引为 0），生成左输入的代码
    // implementor.visitChild 会递归调用子节点的 implement 方法
    final Result leftResult =
        implementor.visitChild(this, 0, (EnumerableRel) left, pref);
    // 将左子节点的代码块添加到构建器中，并获取左输入的表达式
    // leftExpression 是一个可枚举对象，代表左输入的数据流
    Expression leftExpression =
        builder.append(
            "left", leftResult.block);

    // 创建相关代码块构建器，用于构建相关变量和右输入的代码
    final BlockBuilder corrBlock = new BlockBuilder();
    // 获取相关变量的 Java 类型，即左输入的行类型
    Type corrVarType = leftResult.physType.getJavaRowType();
    // 声明相关变量引用，将在内层循环中使用
    ParameterExpression corrRef; // correlate to be used in inner loop
    // 声明相关变量参数，用于 lambda 表达式的参数（必须装箱）
    ParameterExpression corrArg; // argument to correlate lambda (must be boxed)
    // 判断相关变量类型是否为原始类型
    if (!Primitive.is(corrVarType)) {
      // 如果不是原始类型，直接使用该类型创建参数
      // Modifier.FINAL 表示参数是 final 的
      corrArg =
          Expressions.parameter(Modifier.FINAL,
              corrVarType, getCorrelVariable());
      // 相关变量引用直接使用相关变量参数
      corrRef = corrArg;
    } else {
      // 如果是原始类型，需要装箱（使用包装类型）
      // 参数名前加 "$box" 前缀表示这是装箱后的变量
      corrArg =
          Expressions.parameter(Modifier.FINAL,
              Primitive.box(corrVarType), "$box" + getCorrelVariable());
      // 创建拆箱表达式，将装箱的变量拆箱为原始类型
      // 在相关代码块中添加拆箱操作，并将结果赋给相关变量引用
      corrRef =
          (ParameterExpression) corrBlock.append(getCorrelVariable(),
              Expressions.unbox(corrArg));
    }

    // 注册相关变量，使右子查询可以访问它
    // implementor 会维护一个相关变量注册表，记录变量名、变量引用、代码块和类型
    implementor.registerCorrelVariable(getCorrelVariable(), corrRef,
        corrBlock, leftResult.physType);

    // 访问右子节点（索引为 1），生成右输入的代码
    // 此时右子节点的实现可以使用已注册的相关变量
    final Result rightResult =
        implementor.visitChild(this, 1, (EnumerableRel) right, pref);

    // 清除相关变量注册，避免影响后续代码
    implementor.clearCorrelVariable(getCorrelVariable());

    // 将右子节点的代码块添加到相关代码块中
    corrBlock.add(rightResult.block);

    // 创建输出行的物理类型
    // 使用类型工厂、行类型和偏好设置创建物理类型
    final PhysType physType =
        PhysTypeImpl.of(
            implementor.getTypeFactory(),
            getRowType(),
            pref.prefer(JavaRowFormat.CUSTOM));

    // 创建连接选择器（selector），用于合并左右输入的结果
    // selector 是一个 lambda 表达式，接收左右输入的行，返回合并后的行
    Expression selector =
        EnumUtils.joinSelector(
            joinType, physType,
            ImmutableList.of(leftResult.physType, rightResult.physType));

    // 调用 LINQ 的 correlateJoin 方法，生成最终的连接代码
    // correlateJoin 是 LINQ4J 提供的扩展方法，实现相关联的逻辑
    // 参数说明：
    // - leftExpression: 左输入的可枚举对象
    // - BuiltInMethod.CORRELATE_JOIN.method: correlateJoin 方法引用
    // - EnumUtils.toLinq4jJoinType(joinType): 连接类型（转换为 LINQ4J 的枚举）
    // - Expressions.lambda(corrBlock.toBlock(), corrArg): lambda 表达式，接收相关变量，返回右输入
    // - selector: 选择器，用于合并左右输入的行
    builder.append(
        Expressions.call(leftExpression, BuiltInMethod.CORRELATE_JOIN.method,
            Expressions.constant(EnumUtils.toLinq4jJoinType(joinType)),
            Expressions.lambda(corrBlock.toBlock(), corrArg),
            selector));

    // 返回实现结果，包含物理类型和最终的代码块
    return implementor.result(physType, builder.toBlock());
  }
}
