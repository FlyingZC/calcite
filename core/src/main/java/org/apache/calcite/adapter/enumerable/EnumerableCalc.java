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
// Apache License 2.0 许可证声明，说明代码的版权和使用条款
package org.apache.calcite.adapter.enumerable; // 声明包名，表示此类属于 org.apache.calcite.adapter.enumerable 包

import org.apache.calcite.DataContext; // 导入 DataContext 类，用于提供数据访问上下文信息
import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入 JavaTypeFactory，用于创建和管理 Java 类型
import org.apache.calcite.linq4j.Enumerator; // 导入 Enumerator 接口，用于遍历数据集合
import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入 BlockBuilder，用于构建代码块
import org.apache.calcite.linq4j.tree.BlockStatement; // 导入 BlockStatement，表示代码块语句
import org.apache.calcite.linq4j.tree.Blocks; // 导入 Blocks 工具类，提供代码块操作方法
import org.apache.calcite.linq4j.tree.Expression; // 导入 Expression，表示表达式树节点
import org.apache.calcite.linq4j.tree.Expressions; // 导入 Expressions 工具类，用于创建各种表达式
import org.apache.calcite.linq4j.tree.MemberDeclaration; // 导入 MemberDeclaration，表示成员声明
import org.apache.calcite.linq4j.tree.ParameterExpression; // 导入 ParameterExpression，表示参数表达式
import org.apache.calcite.linq4j.tree.Types; // 导入 Types 工具类，用于类型操作
import org.apache.calcite.plan.RelOptCluster; // 导入 RelOptCluster，表示关系表达式集群
import org.apache.calcite.plan.RelOptPredicateList; // 导入 RelOptPredicateList，表示谓词列表
import org.apache.calcite.plan.RelTraitSet; // 导入 RelTraitSet，表示关系表达式特征集合
import org.apache.calcite.rel.RelCollation; // 导入 RelCollation，表示排序规则
import org.apache.calcite.rel.RelCollationTraitDef; // 导入 RelCollationTraitDef，排序规则特征定义
import org.apache.calcite.rel.RelDistributionTraitDef; // 导入 RelDistributionTraitDef，数据分布特征定义
import org.apache.calcite.rel.RelNode; // 导入 RelNode，关系表达式节点基类
import org.apache.calcite.rel.core.Calc; // 导入 Calc，计算关系表达式基类
import org.apache.calcite.rel.metadata.RelMdCollation; // 导入 RelMdCollation，排序规则元数据
import org.apache.calcite.rel.metadata.RelMdDistribution; // 导入 RelMdDistribution，数据分布元数据
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入 RelMetadataQuery，元数据查询接口
import org.apache.calcite.rex.RexBuilder; // 导入 RexBuilder，用于构建 Rex 表达式
import org.apache.calcite.rex.RexNode; // 导入 RexNode，行表达式节点
import org.apache.calcite.rex.RexProgram; // 导入 RexProgram，行表达式程序
import org.apache.calcite.rex.RexSimplify; // 导入 RexSimplify，用于简化 Rex 表达式
import org.apache.calcite.rex.RexUtil; // 导入 RexUtil，Rex 表达式工具类
import org.apache.calcite.sql.validate.SqlConformance; // 导入 SqlConformance，SQL 兼容性接口
import org.apache.calcite.sql.validate.SqlConformanceEnum; // 导入 SqlConformanceEnum，SQL 兼容性枚举
import org.apache.calcite.util.BuiltInMethod; // 导入 BuiltInMethod，内置方法常量
import org.apache.calcite.util.Pair; // 导入 Pair，键值对工具类
import org.apache.calcite.util.Util; // 导入 Util，通用工具类

import com.google.common.collect.ImmutableList; // 导入 Google Guava 的 ImmutableList，不可变列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 Nullable 注解，表示可空类型

import java.lang.reflect.Modifier; // 导入 Modifier，用于反射访问修饰符
import java.lang.reflect.Type; // 导入 Type，Java 类型接口
import java.util.List; // 导入 List，列表接口

import static org.apache.calcite.adapter.enumerable.EnumUtils.BRIDGE_METHODS; // 导入 BRIDGE_METHODS 常量
import static org.apache.calcite.adapter.enumerable.EnumUtils.NO_EXPRS; // 导入 NO_EXPRS 常量，表示无表达式
import static org.apache.calcite.adapter.enumerable.EnumUtils.NO_PARAMS; // 导入 NO_PARAMS 常量，表示无参数

/** Implementation of {@link org.apache.calcite.rel.core.Calc} in
 * {@link org.apache.calcite.adapter.enumerable.EnumerableConvention enumerable calling convention}. */
// 类作用：EnumerableCalc 是 Calc 关系表达式在 EnumerableConvention 调用约定下的实现
// 它负责将关系代数的计算操作（投影、过滤等）转换为可执行的 LINQ4J 枚举器代码
// EnumerableConvention 表示该关系表达式可以被枚举，即可以生成 Java 代码来执行
public class EnumerableCalc extends Calc implements EnumerableRel { // 类定义：继承自 Calc，实现 EnumerableRel 接口
  /**
   * Creates an EnumerableCalc.
   *
   * <p>Use {@link #create} unless you know what you're doing.
   */
  // 成员变量作用说明：本类没有显式定义成员变量，所有成员变量都继承自父类 Calc
  // 继承的主要成员变量包括：
  // - RexProgram program: 存储计算操作的程序，包括输入引用、投影列表、条件等
  // - RelNode input: 输入关系表达式节点
  // - RelOptCluster cluster: 关系表达式集群，包含 RexBuilder 等共享资源
  /**
   * 构造方法作用：创建一个 EnumerableCalc 实例
   * @param cluster 关系表达式集群，提供 RexBuilder、类型工厂等共享资源
   * @param traitSet 特征集合，定义此节点的物理属性（如调用约定、排序规则等）
   * @param input 输入关系表达式节点，表示数据来源
   * @param program RexProgram，定义要执行的计算操作（投影、过滤等）
   */
  public EnumerableCalc(RelOptCluster cluster, // 参数：关系表达式集群
      RelTraitSet traitSet, // 参数：特征集合
      RelNode input, // 参数：输入关系表达式
      RexProgram program) { // 参数：RexProgram 计算程序
    super(cluster, traitSet, ImmutableList.of(), input, program); // 调用父类 Calc 的构造方法，传入 cluster、traitSet、空列表、input 和 program
    assert getConvention() instanceof EnumerableConvention; // 断言：确保调用约定是 EnumerableConvention 类型
    assert !program.containsAggs(); // 断言：确保程序中不包含聚合操作（Calc 不应该处理聚合）
  }

  @Deprecated // to be removed before 2.0 // 注解：标记为已过时，将在 2.0 版本前移除
  /**
   * 构造方法作用：已过时的构造方法，创建一个 EnumerableCalc 实例
   * @param cluster 关系表达式集群
   * @param traitSet 特征集合
   * @param input 输入关系表达式
   * @param program RexProgram 计算程序
   * @param collationList 排序规则列表（已废弃，不再使用）
   */
  public EnumerableCalc( // 构造方法定义
      RelOptCluster cluster, // 参数：关系表达式集群
      RelTraitSet traitSet, // 参数：特征集合
      RelNode input, // 参数：输入关系表达式
      RexProgram program, // 参数：RexProgram 计算程序
      List<RelCollation> collationList) { // 参数：排序规则列表（已废弃）
    this(cluster, traitSet, input, program); // 调用主构造方法，忽略 collationList 参数
    Util.discard(collationList); // 丢弃 collationList 参数，避免编译器警告
  }

  /** Creates an EnumerableCalc. */
  /**
   * 静态工厂方法作用：创建一个 EnumerableCalc 实例，这是推荐的创建方式
   * 该方法会自动计算并设置适当的特征集合（traitSet）
   * @param input 输入关系表达式节点
   * @param program RexProgram 计算程序
   * @return 创建的 EnumerableCalc 实例
   */
  public static EnumerableCalc create(final RelNode input, // 参数：输入关系表达式
      final RexProgram program) { // 参数：RexProgram 计算程序
    final RelOptCluster cluster = input.getCluster(); // 获取输入节点的集群
    final RelMetadataQuery mq = cluster.getMetadataQuery(); // 获取元数据查询对象，用于查询元数据
    final RelTraitSet traitSet = cluster.traitSet() // 获取集群的默认特征集合
        .replace(EnumerableConvention.INSTANCE) // 替换为 EnumerableConvention 调用约定
        .replaceIfs(RelCollationTraitDef.INSTANCE, // 如果需要，替换排序规则特征
            () -> RelMdCollation.calc(mq, input, program)) // 使用 RelMdCollation.calc 计算排序规则
        .replaceIf(RelDistributionTraitDef.INSTANCE, // 如果需要，替换数据分布特征
            () -> RelMdDistribution.calc(mq, input, program)); // 使用 RelMdDistribution.calc 计算数据分布
    return new EnumerableCalc(cluster, traitSet, input, program); // 创建并返回新的 EnumerableCalc 实例
  }

  /**
   * 方法作用：复制当前节点，创建一个新的 EnumerableCalc 实例
   * 这是关系表达式树转换的标准方法，用于创建修改后的节点副本
   * @param traitSet 新的特征集合
   * @param child 子节点（输入关系表达式）
   * @param program 新的 RexProgram 计算程序
   * @return 新的 EnumerableCalc 实例
   */
  @Override public EnumerableCalc copy(RelTraitSet traitSet, RelNode child, // 方法签名：重写父类的 copy 方法
      RexProgram program) { // 参数：新的 RexProgram
    // we do not need to copy program; it is immutable // 注释：不需要复制 program，因为它是不可变的
    return new EnumerableCalc(getCluster(), traitSet, child, program); // 创建并返回新的 EnumerableCalc 实例
  }

  /**
   * 方法作用：实现此关系表达式，生成可执行的 LINQ4J 代码
   * 这是 EnumerableRel 接口的核心方法，负责将关系表达式转换为 Java 代码
   * 生成的代码会创建一个 Enumerator，用于遍历和转换数据
   * @param implementor 实现器，负责生成代码
   * @param pref 优先级偏好，指定输出格式偏好
   * @return 实现结果，包含物理类型和生成的代码块
   */
  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) { // 方法签名：实现关系表达式
    final JavaTypeFactory typeFactory = implementor.getTypeFactory(); // 获取 Java 类型工厂，用于类型转换
    final BlockBuilder builder = new BlockBuilder(); // 创建代码块构建器，用于构建生成的代码
    final EnumerableRel child = (EnumerableRel) getInput(); // 获取子节点（输入关系表达式），强制转换为 EnumerableRel

    final Result result = // 变量：存储子节点的实现结果
        implementor.visitChild(this, 0, child, pref); // 访问子节点，生成子节点的实现代码

    final PhysType physType = // 变量：物理类型，描述输出的 Java 类型
        PhysTypeImpl.of( // 创建物理类型实现
            typeFactory, getRowType(), pref.prefer(result.format)); // 使用类型工厂、行类型和格式偏好

    // final Enumerable<Employee> inputEnumerable = <<child adapter>>;
    // return new Enumerable<IntString>() {
    //     Enumerator<IntString> enumerator() {
    //         return new Enumerator<IntString>() {
    //             public void reset() {
    // ...
    // 注释：上述是生成代码的示例结构，展示将要生成的代码模式
    Type outputJavaType = physType.getJavaRowType(); // 获取输出行的 Java 类型
    final Type enumeratorType = // 变量：枚举器类型，如 Enumerator<IntString>
        Types.of( // 创建参数化类型
            Enumerator.class, outputJavaType); // 指定 Enumerator 接口和输出类型
    Type inputJavaType = result.physType.getJavaRowType(); // 获取输入行的 Java 类型
    ParameterExpression inputEnumerator = // 变量：输入枚举器参数表达式
        Expressions.parameter( // 创建参数表达式
            Types.of( // 指定参数类型
                Enumerator.class, inputJavaType), // Enumerator<输入类型>
            "inputEnumerator"); // 参数名称
    Expression input = // 变量：输入表达式，表示当前行的数据
        EnumUtils.convert( // 转换表达式类型
            Expressions.call( // 调用方法
                inputEnumerator, // 在输入枚举器上调用
                BuiltInMethod.ENUMERATOR_CURRENT.method), // 调用 current() 方法获取当前元素
            inputJavaType); // 转换为输入 Java 类型

    final RexBuilder rexBuilder = getCluster().getRexBuilder(); // 获取 RexBuilder，用于构建 Rex 表达式
    final RelMetadataQuery mq = getCluster().getMetadataQuery(); // 获取元数据查询对象
    final RelOptPredicateList predicates = mq.getPulledUpPredicates(child); // 获取子节点的上推谓词，用于表达式简化
    final RexSimplify simplify = // 变量：Rex 表达式简化器
        new RexSimplify(rexBuilder, predicates, RexUtil.EXECUTOR); // 创建简化器，使用 RexBuilder、谓词和执行器
    final RexProgram program = this.program.normalize(rexBuilder, simplify); // 标准化 RexProgram，简化表达式

    BlockStatement moveNextBody; // 变量：moveNext() 方法的代码块
    if (program.getCondition() == null) { // 如果程序中没有条件（没有过滤）
      moveNextBody = // 直接调用输入枚举器的 moveNext()
          Blocks.toFunctionBlock( // 将表达式转换为函数代码块
              Expressions.call( // 调用方法
                  inputEnumerator, // 在输入枚举器上调用
                  BuiltInMethod.ENUMERATOR_MOVE_NEXT.method)); // 调用 moveNext() 方法
    } else { // 如果程序中有条件（需要过滤）
      final BlockBuilder builder2 = new BlockBuilder(); // 创建新的代码块构建器
      Expression condition = // 变量：条件表达式
          RexToLixTranslator.translateCondition( // 将 Rex 条件转换为 LINQ 表达式
              program, // RexProgram
              typeFactory, // 类型工厂
              builder2, // 代码块构建器
              new RexToLixTranslator.InputGetterImpl(input, result.physType), // 输入获取器
              implementor.allCorrelateVariables, // 所有关联变量
              implementor.getConformance()); // SQL 兼容性
      builder2.add( // 添加语句到代码块
          Expressions.ifThen( // 创建 if-then 语句
              condition, // 条件表达式
              Expressions.return_( // 返回语句
                  null, Expressions.constant(true)))); // 返回 true（找到符合条件的行）
      moveNextBody = // 构建 moveNext() 方法体
          Expressions.block( // 创建代码块
              Expressions.while_( // 创建 while 循环
                  Expressions.call( // 循环条件
                      inputEnumerator, // 在输入枚举器上调用
                      BuiltInMethod.ENUMERATOR_MOVE_NEXT.method), // 调用 moveNext()
                  builder2.toBlock()), // 循环体：检查条件并返回
              Expressions.return_( // 循环结束后的返回语句
                  null,
                  Expressions.constant(false))); // 返回 false（没有更多符合条件的行）
    }

    final BlockBuilder builder3 = new BlockBuilder(); // 创建新的代码块构建器，用于构建 current() 方法
    final SqlConformance conformance = // 变量：SQL 兼容性设置
        (SqlConformance) implementor.map.getOrDefault("_conformance", // 从实现器获取兼容性设置
            SqlConformanceEnum.DEFAULT); // 默认使用 DEFAULT 兼容性
    List<Expression> expressions = // 变量：投影表达式列表
        RexToLixTranslator.translateProjects( // 将 Rex 投影转换为 LINQ 表达式
            program, // RexProgram
            typeFactory, // 类型工厂
            conformance, // SQL 兼容性
            builder3, // 代码块构建器
            null, // 无额外输入
            physType, // 输出物理类型
            DataContext.ROOT, // 数据上下文根
            new RexToLixTranslator.InputGetterImpl(input, result.physType), // 输入获取器
            implementor.allCorrelateVariables); // 所有关联变量
    builder3.add( // 添加返回语句到代码块
        Expressions.return_( // 返回语句
            null, physType.record(expressions))); // 返回记录对象，包含所有投影表达式的值
    BlockStatement currentBody = // 变量：current() 方法的代码块
        builder3.toBlock(); // 将代码块构建器转换为代码块语句

    final Expression inputEnumerable = // 变量：输入枚举器表达式
        builder.append( // 添加到主代码块
            "inputEnumerable", result.block, false); // 调用子节点的实现代码，获取输入枚举器
    final Expression body = // 变量：枚举器对象主体
        Expressions.new_( // 创建新对象表达式
            enumeratorType, // 枚举器类型
            NO_EXPRS, // 无构造函数参数
            Expressions.list( // 成员声明列表
                Expressions.fieldDecl( // 字段声明
                    Modifier.PUBLIC | Modifier.FINAL, // public final 修饰符
                    inputEnumerator, // 字段类型
                    Expressions.call( // 初始化表达式
                        inputEnumerable, // 在输入枚举器上调用
                        BuiltInMethod.ENUMERABLE_ENUMERATOR.method)), // 调用 enumerator() 方法
                EnumUtils.overridingMethodDecl( // 重写方法声明
                    BuiltInMethod.ENUMERATOR_RESET.method, // reset() 方法
                    NO_PARAMS, // 无参数
                    Blocks.toFunctionBlock( // 转换为函数代码块
                        Expressions.call( // 调用方法
                            inputEnumerator, // 在输入枚举器上调用
                            BuiltInMethod.ENUMERATOR_RESET.method))), // 调用 reset() 方法
                EnumUtils.overridingMethodDecl( // 重写方法声明
                    BuiltInMethod.ENUMERATOR_MOVE_NEXT.method, // moveNext() 方法
                    NO_PARAMS, // 无参数
                    moveNextBody), // 使用前面构建的 moveNext() 方法体
                EnumUtils.overridingMethodDecl( // 重写方法声明
                    BuiltInMethod.ENUMERATOR_CLOSE.method, // close() 方法
                    NO_PARAMS, // 无参数
                    Blocks.toFunctionBlock( // 转换为函数代码块
                        Expressions.call( // 调用方法
                            inputEnumerator, // 在输入枚举器上调用
                            BuiltInMethod.ENUMERATOR_CLOSE.method))), // 调用 close() 方法
                Expressions.methodDecl( // 方法声明
                    Modifier.PUBLIC, // public 修饰符
                    BRIDGE_METHODS // 如果启用桥接方法
                        ? Object.class // 返回 Object 类型（桥接）
                        : outputJavaType, // 否则返回输出 Java 类型
                    "current", // 方法名
                    NO_PARAMS, // 无参数
                    currentBody))); // 使用前面构建的 current() 方法体
    builder.add( // 添加返回语句到主代码块
        Expressions.return_( // 返回语句
            null,
            Expressions.new_( // 创建新对象表达式
                BuiltInMethod.ABSTRACT_ENUMERABLE_CTOR.constructor, // AbstractEnumerable 的构造函数
                // TODO: generics // TODO: 泛型支持
                //   Collections.singletonList(inputRowType), // 泛型参数列表（待实现）
                NO_EXPRS, // 无构造函数参数
                ImmutableList.<MemberDeclaration>of( // 成员声明列表
                    Expressions.methodDecl( // 方法声明
                        Modifier.PUBLIC, // public 修饰符
                        enumeratorType, // 返回类型：枚举器类型
                        BuiltInMethod.ENUMERABLE_ENUMERATOR.method.getName(), // 方法名：enumerator
                        NO_PARAMS, // 无参数
                        Blocks.toFunctionBlock(body)))))); // 返回前面构建的枚举器对象
    return implementor.result(physType, builder.toBlock()); // 返回实现结果，包含物理类型和生成的代码块
  }

  /**
   * 方法作用：确定哪些特征可以传递给子节点
   * 这是关系表达式优化的重要方法，用于特征传播
   * @param required 父节点要求的特征集合
   * @return 键值对，第一个元素是此节点保留的特征，第二个元素是可传递给子节点的特征列表
   */
  @Override public @Nullable Pair<RelTraitSet, List<RelTraitSet>> passThroughTraits( // 方法签名：传递特征
      final RelTraitSet required) { // 参数：要求的特征集合
    final List<RexNode> exps = // 变量：投影表达式列表
        Util.transform(program.getProjectList(), program::expandLocalRef); // 将投影列表转换为 RexNode，展开局部引用

    return EnumerableTraitsUtils.passThroughTraitsForProject(required, exps, // 调用工具方法计算可传递的特征
        input.getRowType(), input.getCluster().getTypeFactory(), traitSet); // 传入输入行类型、类型工厂和当前特征集
  }

  /**
   * 方法作用：从子节点派生特征
   * 这是关系表达式优化的重要方法，用于从子节点推导此节点的特征
   * @param childTraits 子节点的特征集合
   * @param childId 子节点 ID
   * @return 键值对，第一个元素是此节点保留的特征，第二个元素是派生的特征列表
   */
  @Override public @Nullable Pair<RelTraitSet, List<RelTraitSet>> deriveTraits( // 方法签名：派生特征
      final RelTraitSet childTraits, final int childId) { // 参数：子节点特征集合和子节点 ID
    final List<RexNode> exps = // 变量：投影表达式列表
        Util.transform(program.getProjectList(), program::expandLocalRef); // 将投影列表转换为 RexNode，展开局部引用

    return EnumerableTraitsUtils.deriveTraitsForProject(childTraits, childId, exps, // 调用工具方法计算派生的特征
        input.getRowType(), input.getCluster().getTypeFactory(), traitSet); // 传入输入行类型、类型工厂和当前特征集
  }

  /**
   * 方法作用：获取此节点的 RexProgram
   * RexProgram 包含了所有要执行的计算操作（投影、过滤等）
   * @return RexProgram 计算程序
   */
  @Override public RexProgram getProgram() { // 方法签名：获取程序
    return program; // 返回继承自父类的 program 成员变量
  }
}
// 类总结：EnumerableCalc 是 Calc 关系表达式在 EnumerableConvention 调用约定下的实现
// 它的主要功能是将关系代数的计算操作（投影、过滤等）转换为可执行的 LINQ4J 枚举器代码
// 核心方法 implement() 负责生成 Java 代码，创建一个 Enumerator 对象来遍历和转换数据
// 生成的代码包含：
// 1. moveNext() 方法：遍历输入数据，如果存在过滤条件则跳过不符合条件的行
// 2. current() 方法：应用投影表达式，生成输出行的数据
// 3. reset() 和 close() 方法：委托给输入枚举器的相应方法
// 这个类是 Calcite 查询执行引擎的关键组件，负责将逻辑查询计划转换为可执行的代码