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
package org.apache.calcite.adapter.enumerable; // 包声明：该类位于org.apache.calcite.adapter.enumerable包下，用于可枚举适配器中的聚合函数实现

import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入BlockBuilder类：用于构建代码块，是LINQ4J表达式树的一部分
import org.apache.calcite.linq4j.tree.BlockStatement; // 导入BlockStatement类：表示代码块语句，是LINQ4J表达式树的一部分
import org.apache.calcite.linq4j.tree.Expression; // 导入Expression类：表示表达式，是LINQ4J表达式树的基础类
import org.apache.calcite.linq4j.tree.Expressions; // 导入Expressions类：表达式工具类，用于创建各种表达式
import org.apache.calcite.linq4j.tree.ParameterExpression; // 导入ParameterExpression类：表示参数表达式
import org.apache.calcite.linq4j.tree.Primitive; // 导入Primitive类：用于处理基本类型
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类：表示Calcite的关系数据类型
import org.apache.calcite.rex.RexNode; // 导入RexNode类：表示行表达式节点，是Calcite表达式树的基类

import java.lang.reflect.Type; // 导入Type类：Java反射API中的类型接口
import java.util.ArrayList; // 导入ArrayList类：Java集合框架中的动态数组实现
import java.util.Collections; // 导入Collections类：Java集合工具类，提供静态方法操作集合
import java.util.List; // 导入List接口：Java集合框架中的列表接口

/**
 * The base implementation of strict aggregate function.
 * 严格聚合函数的基实现类
 *
 * 严格聚合函数（strict aggregate function）是指忽略NULL值的聚合函数，
 * 例如SUM、COUNT、AVG等。这类函数在遇到NULL值时会跳过而不是参与计算。
 *
 * 该类实现了AggImplementor接口，提供了严格聚合函数的通用实现框架，
 * 子类只需实现核心的聚合逻辑即可。
 *
 * 主要功能：
 * 1. 管理聚合状态，包括是否需要跟踪空集合和每行的NULL值
 * 2. 提供状态类型、重置、添加值、获取结果的默认实现
 * 3. 处理空集合和NULL值的特殊情况
 * 4. 生成高效的Java代码来实现聚合函数
 *
 * @see org.apache.calcite.adapter.enumerable.RexImpTable.CountImplementor // 参见CountImplementor：COUNT聚合函数的实现
 * @see org.apache.calcite.adapter.enumerable.RexImpTable.SumImplementor // 参见SumImplementor：SUM聚合函数的实现
 */
public abstract class StrictAggImplementor implements AggImplementor { // 定义抽象类StrictAggImplementor，实现AggImplementor接口
  private boolean needTrackEmptySet; // 私有成员变量：标记是否需要跟踪空集合（即没有匹配任何行的情况），当返回类型可为空时为true
  private boolean trackNullsPerRow; // 私有成员变量：标记是否需要跟踪每行的NULL值，用于判断是否至少有一行非NULL值
  private int stateSize; // 私有成员变量：存储聚合状态的大小（不包括跟踪标志），即实际聚合值的数量

  /**
   * 判断空集合时是否返回非默认值
   *
   * 该方法用于确定当聚合函数没有处理任何行时，是否需要返回NULL而不是默认值。
   * 例如，对于SUM函数，如果没有处理任何行，应该返回NULL而不是0。
   *
   * @param info AggContext对象，包含聚合函数的上下文信息（如返回类型、参数类型等）
   * @return 如果返回类型可为空（nullable），返回true；否则返回false
   */
  protected boolean nonDefaultOnEmptySet(AggContext info) { // 定义保护方法nonDefaultOnEmptySet，判断空集合时是否返回非默认值
    return info.returnRelType().isNullable(); // 返回聚合函数返回类型的可空性：如果可为空则返回true
  }

  /**
   * 获取聚合状态的大小
   *
   * 该方法返回聚合状态的大小，即实际存储的聚合值的数量。
   * 这个值在getStateType方法中被设置。
   *
   * @return 聚合状态的大小（不包括跟踪标志）
   */
  protected final int getStateSize() { // 定义保护方法getStateSize，获取聚合状态的大小
    return stateSize; // 返回状态大小成员变量
  }

  /**
   * 推进累加器的值
   *
   * 这是一个静态辅助方法，用于将新值赋给累加器。
   * 它会自动进行类型转换，确保新值的类型与累加器的类型匹配。
   *
   * @param add AggAddContext对象，包含添加操作的上下文信息（如当前代码块等）
   * @param acc 累加器表达式，表示要更新的累加器变量
   * @param next 新值的表达式，表示要累加的新值
   */
  protected static void accAdvance(AggAddContext add, Expression acc, // 定义静态方法accAdvance，用于推进累加器的值
      Expression next) { // 参数next：新值的表达式
    add.currentBlock().add( // 向当前代码块添加一条语句
        Expressions.statement( // 创建一个语句表达式
            Expressions.assign(acc, EnumUtils.convert(next, acc.type)))); // 创建赋值表达式，将next转换为acc的类型后赋值给acc
  }

  /**
   * 获取聚合状态的类型列表
   *
   * 该方法定义了聚合函数需要维护的状态类型。
   * 对于严格聚合函数，状态包括：
   * 1. 实际的聚合值（如SUM的累加和、COUNT的计数器等）
   * 2. 可选的跟踪标志（用于判断是否存在非NULL值）
   *
   * 该方法会根据返回类型和参数类型决定是否需要额外的跟踪标志。
   *
   * @param info AggContext对象，包含聚合函数的上下文信息
   * @return 状态类型列表，可能包含实际的聚合值类型和跟踪标志类型
   */
  @Override public final List<Type> getStateType(AggContext info) { // 重写接口方法getStateType，获取聚合状态的类型列表
    List<Type> subState = getNotNullState(info); // 调用getNotNullState方法获取非空状态的类型列表（子类可重写）
    stateSize = subState.size(); // 保存状态大小（不包括跟踪标志）
    needTrackEmptySet = nonDefaultOnEmptySet(info); // 调用nonDefaultOnEmptySet方法判断是否需要跟踪空集合
    if (!needTrackEmptySet) { // 如果不需要跟踪空集合
      return subState; // 直接返回状态类型列表
    }
    final boolean hasNullableArgs = anyNullable(info.parameterRelTypes()); // 检查聚合函数的参数中是否有可空类型
    trackNullsPerRow = !(info instanceof WinAggContext) || hasNullableArgs; // 如果不是窗口聚合或者参数中有可空类型，则需要跟踪每行的NULL值

    List<Type> res = new ArrayList<>(subState.size() + 1); // 创建结果列表，容量为状态大小+1（用于存储跟踪标志）
    res.addAll(subState); // 将所有状态类型添加到结果列表
    res.add(boolean.class); // 添加boolean类型的跟踪标志，用于记录是否至少有一行非NULL值
    return res; // 返回完整的状态类型列表
  }

  /**
   * 检查类型列表中是否有可空类型
   *
   * 这是一个静态辅助方法，用于遍历类型列表，判断是否存在可空类型。
   *
   * @param types RelDataType类型列表，要检查的类型集合
   * @return 如果列表中有任何可空类型，返回true；否则返回false
   */
  private static boolean anyNullable(List<? extends RelDataType> types) { // 定义私有静态方法anyNullable，检查是否有可空类型
    for (RelDataType type : types) { // 遍历类型列表中的每个类型
      if (type.isNullable()) { // 如果当前类型是可空的
        return true; // 立即返回true
      }
    }
    return false; // 所有类型都不可空，返回false
  }

  /**
   * 获取非空状态的类型列表
   *
   * 该方法返回聚合函数实际需要的非空状态类型。
   * 默认实现返回一个单元素列表，包含返回类型对应的基本类型。
   * 子类可以重写此方法以支持多个状态值（如AVG需要维护sum和count）。
   *
   * @param info AggContext对象，包含聚合函数的上下文信息
   * @return 非空状态类型列表，默认为返回类型对应的基本类型
   */
  public List<Type> getNotNullState(AggContext info) { // 定义公共方法getNotNullState，获取非空状态的类型列表
    Type type = info.returnType(); // 获取聚合函数的返回类型
    type = EnumUtils.fromInternal(type); // 将内部类型转换为Java类型
    type = Primitive.unbox(type); // 将包装类型拆箱为基本类型（如Integer拆箱为int）
    return Collections.singletonList(type); // 返回包含该类型的单元素不可变列表
  }

  /**
   * 实现聚合状态的重置逻辑
   *
   * 该方法生成重置聚合状态的代码，在聚合开始前调用。
   * 如果需要跟踪每行的NULL值，还会重置跟踪标志。
   *
   * @param info AggContext对象，包含聚合函数的上下文信息
   * @param reset AggResetContext对象，包含重置操作的上下文信息（如累加器、当前代码块等）
   */
  @Override public final void implementReset(AggContext info, AggResetContext reset) { // 重写接口方法implementReset，实现聚合状态的重置
    if (trackNullsPerRow) { // 如果需要跟踪每行的NULL值
      List<Expression> acc = reset.accumulator(); // 获取累加器表达式列表
      Expression flag = acc.get(acc.size() - 1); // 获取最后一个累加器，即跟踪标志（boolean类型）
      BlockBuilder block = reset.currentBlock(); // 获取当前代码块构建器
      block.add( // 向代码块添加一条语句
          Expressions.statement( // 创建一个语句表达式
              Expressions.assign(flag, // 创建赋值表达式
                  RexImpTable.getDefaultValue(flag.getType())))); // 将跟踪标志设置为其类型的默认值（false）
    }
    implementNotNullReset(info, reset); // 调用implementNotNullReset方法重置实际的聚合状态
  }

  /**
   * 实现非空状态的重置逻辑
   *
   * 该方法生成重置实际聚合状态的代码，将所有状态值设置为其类型的默认值。
   * 默认实现遍历所有状态值，将它们设置为默认值。
   * 子类可以重写此方法以实现自定义的重置逻辑。
   *
   * @param info AggContext对象，包含聚合函数的上下文信息
   * @param reset AggResetContext对象，包含重置操作的上下文信息
   */
  protected void implementNotNullReset(AggContext info, // 定义保护方法implementNotNullReset，实现非空状态的重置
      AggResetContext reset) { // 参数reset：重置操作的上下文信息
    BlockBuilder block = reset.currentBlock(); // 获取当前代码块构建器
    List<Expression> accumulator = reset.accumulator(); // 获取累加器表达式列表
    for (int i = 0; i < getStateSize(); i++) { // 遍历所有状态（不包括跟踪标志）
      Expression exp = accumulator.get(i); // 获取第i个状态表达式
      block.add( // 向代码块添加一条语句
          Expressions.statement( // 创建一个语句表达式
              Expressions.assign(exp, // 创建赋值表达式
                  RexImpTable.getDefaultValue(exp.getType())))); // 将状态值设置为其类型的默认值
    }
  }

  /**
   * 实现向聚合状态添加值的逻辑
   *
   * 该方法生成将新值添加到聚合状态的代码。
   * 它会处理以下情况：
   * 1. 检查参数是否为NULL，如果为NULL则跳过
   * 2. 如果存在过滤条件，检查过滤条件是否满足
   * 3. 如果需要跟踪NULL值，更新跟踪标志
   * 4. 调用子类实现的implementNotNullAdd方法进行实际的累加操作
   *
   * @param info AggContext对象，包含聚合函数的上下文信息
   * @param add AggAddContext对象，包含添加操作的上下文信息
   */
  @Override public final void implementAdd(AggContext info, final AggAddContext add) { // 重写接口方法implementAdd，实现向聚合状态添加值
    final List<RexNode> args = add.rexArguments(); // 获取聚合函数的参数表达式列表
    final RexToLixTranslator translator = add.rowTranslator(); // 获取行表达式到LINQ表达式的转换器
    final List<Expression> conditions = new ArrayList<>(); // 创建条件表达式列表，用于收集所有需要检查的条件
    conditions.addAll( // 将所有参数的非NULL检查条件添加到条件列表
        translator.translateList(args, RexImpTable.NullAs.IS_NOT_NULL)); // 转换参数列表，检查每个参数是否不为NULL
    RexNode filterArgument = add.rexFilterArgument(); // 获取过滤参数（HAVING子句中的过滤条件）
    if (filterArgument != null) { // 如果存在过滤参数
      conditions.add( // 将过滤条件添加到条件列表
          translator.translate(filterArgument, // 转换过滤参数
              RexImpTable.NullAs.FALSE)); // 如果过滤参数为NULL，将其视为false
    }
    Expression condition = Expressions.foldAnd(conditions); // 将所有条件用AND操作合并为一个表达式
    if (Expressions.constant(false).equals(condition)) { // 如果合并后的条件是常量false（总是不满足）
      return; // 直接返回，不做任何操作
    }

    boolean argsNotNull = Expressions.constant(true).equals(condition); // 判断参数是否总是非NULL（条件为常量true）
    final BlockBuilder thenBlock = // 创建then代码块，用于在条件满足时执行
        argsNotNull // 如果参数总是非NULL
        ? add.currentBlock() // 则直接使用当前代码块（不需要if语句）
        : new BlockBuilder(true, add.currentBlock()); // 否则创建一个新的嵌套代码块
    if (trackNullsPerRow) { // 如果需要跟踪每行的NULL值
      List<Expression> acc = add.accumulator(); // 获取累加器表达式列表
      thenBlock.add( // 向then代码块添加一条语句
          Expressions.statement( // 创建一个语句表达式
              Expressions.assign(acc.get(acc.size() - 1), // 创建赋值表达式，更新跟踪标志
                  Expressions.constant(true)))); // 将跟踪标志设置为true（表示至少有一行非NULL值）
    }
    if (argsNotNull) { // 如果参数总是非NULL
      implementNotNullAdd(info, add); // 直接调用implementNotNullAdd方法进行累加
      return; // 返回
    }

    add.nestBlock(thenBlock); // 嵌套then代码块（用于生成if语句的then分支）
    implementNotNullAdd(info, add); // 调用implementNotNullAdd方法进行累加
    add.exitBlock(); // 退出嵌套代码块
    add.currentBlock().add(Expressions.ifThen(condition, thenBlock.toBlock())); // 向当前代码块添加if语句：如果条件满足，执行then代码块
  }

  /**
   * 实现向非空状态添加值的逻辑（抽象方法）
   *
   * 该方法由子类实现，用于在参数非NULL时执行实际的累加操作。
   * 子类需要根据具体的聚合逻辑实现该方法。
   *
   * @param info AggContext对象，包含聚合函数的上下文信息
   * @param add AggAddContext对象，包含添加操作的上下文信息
   */
  protected abstract void implementNotNullAdd(AggContext info, // 定义抽象方法implementNotNullAdd，子类必须实现
      AggAddContext add); // 参数add：添加操作的上下文信息

  /**
   * 实现获取聚合结果的逻辑
   *
   * 该方法生成获取聚合结果的代码。
   * 它会处理以下情况：
   * 1. 如果不需要跟踪空集合，直接返回聚合结果
   * 2. 如果需要跟踪空集合，检查是否至少有一行非NULL值
   * 3. 如果没有非NULL值，返回NULL；否则返回聚合结果
   *
   * @param info AggContext对象，包含聚合函数的上下文信息
   * @param result AggResultContext对象，包含结果操作的上下文信息
   * @return 表示聚合结果的表达式
   */
  @Override public final Expression implementResult(AggContext info, // 重写接口方法implementResult，实现获取聚合结果
      final AggResultContext result) { // 参数result：结果操作的上下文信息
    if (!needTrackEmptySet) { // 如果不需要跟踪空集合
      return EnumUtils.convert( // 直接返回转换后的聚合结果
          implementNotNullResult(info, result), info.returnType()); // 调用implementNotNullResult获取结果并转换为返回类型
    }
    String tmpName = result.accumulator().isEmpty() // 生成临时变量名
        ? "ar" // 如果累加器为空，使用"ar"作为名称
        : (result.accumulator().get(0) + "$Res"); // 否则使用第一个累加器名称加上"$Res"后缀
    ParameterExpression res = // 创建参数表达式，用于存储结果
        Expressions.parameter(0, info.returnType(), // 创建参数，类型为返回类型，修饰符为0（表示局部变量）
            result.currentBlock().newName(tmpName)); // 使用当前代码块生成唯一的变量名

    List<Expression> acc = result.accumulator(); // 获取累加器表达式列表
    final BlockBuilder thenBlock = result.nestBlock(); // 嵌套then代码块（用于在至少有一行非NULL值时执行）
    Expression nonNull = // 获取非NULL情况下的结果
        EnumUtils.convert(implementNotNullResult(info, result), info.returnType()); // 调用implementNotNullResult获取结果并转换为返回类型
    result.exitBlock(); // 退出嵌套代码块
    thenBlock.add(Expressions.statement(Expressions.assign(res, nonNull))); // 向then代码块添加赋值语句，将结果赋值给res变量
    BlockStatement thenBranch = thenBlock.toBlock(); // 将then代码块转换为语句
    Expression seenNotNullRows = // 获取是否见过非NULL行的表达式
        trackNullsPerRow // 如果需要跟踪每行的NULL值
        ? acc.get(acc.size() - 1) // 则使用跟踪标志（最后一个累加器）
        : ((WinAggResultContext) result).hasRows(); // 否则使用窗口聚合的hasRows()方法

    if (thenBranch.statements.size() == 1) { // 如果then分支只有一条语句（简单的赋值语句）
      return Expressions.condition(seenNotNullRows, // 返回条件表达式：如果见过非NULL行
          nonNull, RexImpTable.getDefaultValue(res.getType())); // 则返回nonNull结果，否则返回默认值（NULL）
    }
    result.currentBlock().add(Expressions.declare(0, res, null)); // 向当前代码块添加变量声明，声明res变量并初始化为null
    result.currentBlock().add( // 向当前代码块添加if-else语句
        Expressions.ifThenElse(seenNotNullRows, // 如果见过非NULL行
            thenBranch, // 则执行then分支
            Expressions.statement( // 否则执行else分支
                Expressions.assign(res, // 将res变量赋值为
                    RexImpTable.getDefaultValue(res.getType()))))); // 默认值（NULL）
    return res; // 返回res变量
  }

  /**
   * 实现获取非空结果的逻辑
   *
   * 该方法返回非NULL情况下的聚合结果。
   * 默认实现直接返回第一个累加器的值。
   * 子类可以重写此方法以实现自定义的结果计算逻辑。
   *
   * @param info AggContext对象，包含聚合函数的上下文信息
   * @param result AggResultContext对象，包含结果操作的上下文信息
   * @return 表示非空结果的表达式，默认为第一个累加器的值
   */
  protected Expression implementNotNullResult(AggContext info, // 定义保护方法implementNotNullResult，获取非空结果
      AggResultContext result) { // 参数result：结果操作的上下文信息
    return result.accumulator().get(0); // 返回第一个累加器的值（默认实现）
  }
} // 类定义结束