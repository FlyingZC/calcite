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
package org.apache.calcite.adapter.enumerable;  // 声明包名，该类位于org.apache.calcite.adapter.enumerable包下，属于可枚举适配器模块

import org.apache.calcite.linq4j.tree.Expression;  // 导入Expression类，用于表示LINQ表达式树中的节点，是代码生成的基础

import java.lang.reflect.Type;  // 导入Type类，用于表示Java类型，用于类型系统相关的操作
import java.util.List;  // 导入List接口，用于表示有序的元素集合

/**
 * 严格窗口聚合函数的基础实现类
 * 
 * 【类的作用】：
 * 这个类是Calcite框架中用于实现严格（strict）窗口聚合函数的抽象基类。
 * 
 * 什么是窗口聚合函数？
 * 窗口聚合函数是在SQL窗口函数中使用的聚合函数，如ROW_NUMBER()、RANK()、FIRST_VALUE()、LAST_VALUE()等。
 * 与普通聚合函数（如SUM、AVG）不同，窗口聚合函数不会将多行数据聚合成一行，而是为每一行数据计算一个基于窗口的结果。
 * 
 * 什么是"严格"（strict）聚合函数？
 * 严格聚合函数是指那些在遇到空值（NULL）时，会跳过空值而不进行计算的聚合函数。
 * 例如：SUM(col)会跳过col为NULL的行，只对非NULL值求和。
 * 
 * 【类的设计模式】：
 * 1. 继承体系：继承自StrictAggImplementor（严格聚合函数实现器），实现了WinAggImplementor接口（窗口聚合函数实现器接口）
 * 2. 模板方法模式：定义了窗口聚合函数实现的骨架，子类只需要实现特定的抽象方法
 * 3. 适配器模式：将通用的聚合函数接口（AggContext等）适配为窗口聚合函数专用接口（WinAggContext等）
 * 
 * 【核心功能】：
 * 1. 提供窗口聚合函数的标准实现框架
 * 2. 处理窗口函数特有的状态管理和结果计算
 * 3. 协调父类StrictAggImplementor的通用聚合逻辑
 * 4. 提供窗口函数需要的缓存机制（当窗口帧完整时需要缓存）
 * 
 * 【使用场景】：
 * 当需要实现一个新的窗口聚合函数时，可以继承这个类并实现抽象方法。
 * 常见的实现包括：
 * - FirstLastValueImplementor：实现FIRST_VALUE和LAST_VALUE函数
 * - RankImplementor：实现RANK函数
 * - RowNumberImplementor：实现ROW_NUMBER函数
 * 
 * @see org.apache.calcite.adapter.enumerable.RexImpTable.FirstLastValueImplementor  // 参见FIRST_VALUE和LAST_VALUE的实现
 * @see org.apache.calcite.adapter.enumerable.RexImpTable.RankImplementor  // 参见RANK函数的实现
 * @see org.apache.calcite.adapter.enumerable.RexImpTable.RowNumberImplementor  // 参见ROW_NUMBER函数的实现
 */
public abstract class StrictWinAggImplementor extends StrictAggImplementor  // 定义抽象类，继承StrictAggImplementor，复用严格聚合函数的通用逻辑
    implements WinAggImplementor {  // 实现WinAggImplementor接口，提供窗口聚合函数的特定功能

  /**
   * 【方法作用】：实现窗口聚合函数的"添加数据"逻辑（非空版本）
   * 
   * 【详细说明】：
   * 这是一个抽象方法，子类必须实现。当窗口聚合函数需要将一行数据添加到聚合状态时，
   * 会调用这个方法。这个方法负责生成将当前行数据添加到聚合状态的代码。
   * 
   * 【参数说明】：
   * @param info WinAggContext 窗口聚合函数的上下文信息，包含：
   *             - 窗口函数的类型（如ROW_NUMBER、RANK等）
   *             - 窗口帧的定义（如ROWS BETWEEN...）
   *             - 分区键信息
   *             - 排序键信息
   *             - 返回类型信息
   * @param add WinAggAddContext 添加操作的上下文信息，包含：
   *             - 当前行的表达式
   *             - 状态变量的访问器
   *             - 用于生成代码的辅助方法
   * 
   * 【返回值】：void 无返回值，但会修改代码生成上下文，生成添加数据的代码
   * 
   * 【调用时机】：当需要将一行数据添加到窗口聚合状态时调用
   * 
   * 【实现要点】：
   * 1. 子类需要根据具体的窗口函数语义实现添加逻辑
   * 2. 例如ROW_NUMBER需要递增计数器，RANK需要处理相同排名的情况
   * 3. 生成的代码应该能够正确处理窗口边界和分区
   */
  protected abstract void implementNotNullAdd(WinAggContext info,  // 窗口聚合函数的上下文信息，包含窗口定义、类型等元数据
      WinAggAddContext add);  // 添加操作的上下文，提供当前行数据和状态变量的访问

  /**
   * 【方法作用】：判断在空集合上是否返回非默认值
   * 
   * 【详细说明】：
   * 这个方法决定了当窗口帧中没有数据（空集合）时，聚合函数应该返回什么值。
   * 返回true表示即使窗口为空，也应该计算一个非默认值；
   * 返回false表示窗口为空时返回默认值（通常是NULL或0）。
   * 
   * 【参数说明】：
   * @param info WinAggContext 窗口聚合函数的上下文信息
   * 
   * 【返回值】：boolean 如果为true，表示空集合时返回非默认值；如果为false，表示返回默认值
   * 
   * 【默认行为】：调用父类StrictAggImplementor的nonDefaultOnEmptySet方法
   * 
   * 【使用场景】：
   * - 对于ROW_NUMBER，即使窗口为空也可能返回0
   * - 对于RANK，空窗口时返回NULL
   * 
   * 【重写建议】：子类可以根据需要重写此方法来改变空集合时的行为
   */
  protected boolean nonDefaultOnEmptySet(WinAggContext info) {  // 接收窗口聚合上下文作为参数
    return super.nonDefaultOnEmptySet(info);  // 调用父类StrictAggImplementor的同名方法，使用父类的默认行为
  }

  /**
   * 【方法作用】：获取窗口聚合函数所需的状态变量类型列表（非空版本）
   * 
   * 【详细说明】：
   * 窗口聚合函数在计算过程中需要维护一些状态变量，例如：
   * - ROW_NUMBER需要一个计数器
   * - RANK需要维护当前排名和前一个排名
   * - FIRST_VALUE可能需要缓存窗口内的所有值
   * 
   * 这个方法返回这些状态变量的Java类型列表，用于生成状态变量的声明代码。
   * 
   * 【参数说明】：
   * @param info WinAggContext 窗口聚合函数的上下文信息，包含类型信息
   * 
   * 【返回值】：List<Type> 状态变量的类型列表，每个Type对应一个状态变量的Java类型
   * 
   * 【返回值举例】：
   * - ROW_NUMBER可能返回 [int.class]（一个整数计数器）
   * - RANK可能返回 [int.class, int.class]（当前排名和前一个排名）
   * - FIRST_VALUE可能返回 [Object.class, List.class]（当前值和值列表）
   * 
   * 【调用时机】：在生成聚合函数实现代码时，需要先声明状态变量
   * 
   * 【默认行为】：调用父类StrictAggImplementor的getNotNullState方法
   */
  public List<Type> getNotNullState(WinAggContext info) {  // 接收窗口聚合上下文，用于确定需要的状态类型
    return super.getNotNullState(info);  // 调用父类的方法获取状态类型列表
  }

  /**
   * 【方法作用】：实现窗口聚合函数的"重置状态"逻辑（非空版本）
   * 
   * 【详细说明】：
   * 当窗口聚合函数需要重置其内部状态时（例如开始新的分区时），
   * 会调用这个方法。这个方法负责生成重置状态变量的代码。
   * 
   * 【参数说明】：
   * @param info WinAggContext 窗口聚合函数的上下文信息
   * @param reset WinAggResetContext 重置操作的上下文信息，包含：
   *              - 状态变量的访问器
   *              - 用于生成重置代码的辅助方法
   * 
   * 【返回值】：void 无返回值，但会修改代码生成上下文，生成重置状态的代码
   * 
   * 【调用时机】：
   * - 当遇到新的分区键值时
   * - 当窗口帧开始新的计算周期时
   * 
   * 【默认行为】：调用父类StrictAggImplementor的implementNotNullReset方法
   * 
   * 【实现要点】：
   * - ROW_NUMBER需要将计数器重置为0
   * - RANK需要重置排名变量
   * - 需要清理所有分区级别的状态
   */
  protected void implementNotNullReset(WinAggContext info,  // 窗口聚合上下文，包含窗口定义信息
      WinAggResetContext reset) {  // 重置上下文，提供状态变量重置的辅助方法
    super.implementNotNullReset(info, reset);  // 调用父类的方法实现重置逻辑
  }

  /**
   * 【方法作用】：实现窗口聚合函数的"计算结果"逻辑（非空版本）
   * 
   * 【详细说明】：
   * 当需要从聚合状态中获取最终结果时，会调用这个方法。
   * 这个方法负责生成计算并返回聚合结果的代码表达式。
   * 
   * 【参数说明】：
   * @param info WinAggContext 窗口聚合函数的上下文信息，包含返回类型等
   * @param result WinAggResultContext 结果计算的上下文信息，包含：
   *               - 状态变量的访问器
   *               - 当前行的表达式
   *               - 结果表达式生成器
   * 
   * 【返回值】：Expression 表示计算结果的表达式树节点
   * 
   * 【返回值举例】：
   * - ROW_NUMBER返回计数器值的表达式
   * - RANK返回当前排名的表达式
   * - FIRST_VALUE返回当前第一个值的表达式
   * 
   * 【调用时机】：为每一行计算窗口函数的最终结果时
   * 
   * 【默认行为】：调用父类StrictAggImplementor的implementNotNullResult方法
   * 
   * 【实现要点】：
   * - 返回的表达式必须与info中定义的返回类型匹配
   * - 可能需要进行类型转换
   * - 需要考虑NULL值的处理
   */
  protected Expression implementNotNullResult(WinAggContext info,  // 窗口聚合上下文，包含结果类型信息
      WinAggResultContext result) {  // 结果上下文，提供访问状态和生成结果表达式的方法
    return super.implementNotNullResult(info, result);  // 调用父类的方法实现结果计算
  }

  /**
   * 【方法作用】：实现窗口聚合函数的"添加数据"逻辑（适配器方法）
   * 
   * 【详细说明】：
   * 这是AggImplementor接口中定义的抽象方法的实现。
   * 它的作用是将通用的AggContext和AggAddContext转换为窗口专用的WinAggContext和WinAggAddContext，
   * 然后调用子类实现的窗口专用版本的方法。
   * 
   * 【设计模式】：适配器模式
   * 将通用聚合函数接口适配为窗口聚合函数接口，使父类的通用逻辑可以调用子类的窗口专用实现。
   * 
   * 【参数说明】：
   * @param info AggContext 通用聚合上下文（会被强制转换为WinAggContext）
   * @param add AggAddContext 通用添加上下文（会被强制转换为WinAggAddContext）
   * 
   * 【返回值】：void 无返回值
   * 
   * 【调用链】：父类StrictAggImplementor -> implementNotNullAdd(AggContext) -> 本方法 -> implementNotNullAdd(WinAggContext)
   * 
   * 【类型转换】：强制转换是安全的，因为在实际使用中，这些上下文对象就是窗口聚合的上下文
   * 
   * 【final关键字】：标记为final，防止子类重写，确保适配逻辑的一致性
   */
  @Override protected final void implementNotNullAdd(AggContext info,  // 覆盖父类方法，接收通用聚合上下文
      AggAddContext add) {  // 接收通用添加上下文
    implementNotNullAdd((WinAggContext) info, (WinAggAddContext) add);  // 强制转换为窗口专用上下文，调用子类实现的窗口版本方法
  }

  /**
   * 【方法作用】：判断在空集合上是否返回非默认值（适配器方法）
   * 
   * 【详细说明】：
   * 这是AggImplementor接口中定义的方法的实现。
   * 它的作用是将通用的AggContext转换为窗口专用的WinAggContext，
   * 然后调用窗口专用版本的方法。
   * 
   * 【设计模式】：适配器模式
   * 
   * 【参数说明】：
   * @param info AggContext 通用聚合上下文（会被强制转换为WinAggContext）
   * 
   * 【返回值】：boolean 空集合时的行为标志
   * 
   * 【调用链】：父类StrictAggImplementor -> nonDefaultOnEmptySet(AggContext) -> 本方法 -> nonDefaultOnEmptySet(WinAggContext)
   * 
   * 【final关键字】：标记为final，防止子类重写
   */
  @Override protected boolean nonDefaultOnEmptySet(AggContext info) {  // 覆盖父类方法
    return nonDefaultOnEmptySet((WinAggContext) info);  // 强制转换并调用窗口专用版本
  }

  /**
   * 【方法作用】：获取状态变量类型列表（适配器方法）
   * 
   * 【详细说明】：
   * 这是AggImplementor接口中定义的方法的实现。
   * 它的作用是将通用的AggContext转换为窗口专用的WinAggContext，
   * 然后调用窗口专用版本的方法。
   * 
   * 【设计模式】：适配器模式
   * 
   * 【参数说明】：
   * @param info AggContext 通用聚合上下文（会被强制转换为WinAggContext）
   * 
   * 【返回值】：List<Type> 状态变量的类型列表
   * 
   * 【调用链】：AggImplementor接口 -> 本方法 -> getNotNullState(WinAggContext)
   * 
   * 【final关键字】：标记为final，防止子类重写
   */
  @Override public final List<Type> getNotNullState(AggContext info) {  // 覆盖接口方法
    return getNotNullState((WinAggContext) info);  // 强制转换并调用窗口专用版本
  }

  /**
   * 【方法作用】：实现窗口聚合函数的"重置状态"逻辑（适配器方法）
   * 
   * 【详细说明】：
   * 这是AggImplementor接口中定义的方法的实现。
   * 它的作用是将通用的AggContext和AggResetContext转换为窗口专用的WinAggContext和WinAggResetContext，
   * 然后调用窗口专用版本的方法。
   * 
   * 【设计模式】：适配器模式
   * 
   * 【参数说明】：
   * @param info AggContext 通用聚合上下文（会被强制转换为WinAggContext）
   * @param reset AggResetContext 通用重置上下文（会被强制转换为WinAggResetContext）
   * 
   * 【返回值】：void 无返回值
   * 
   * 【调用链】：父类StrictAggImplementor -> implementNotNullReset(AggContext) -> 本方法 -> implementNotNullReset(WinAggContext)
   * 
   * 【final关键字】：标记为final，防止子类重写
   */
  @Override protected final void implementNotNullReset(AggContext info,  // 覆盖父类方法
      AggResetContext reset) {  // 接收通用重置上下文
    implementNotNullReset((WinAggContext) info, (WinAggResetContext) reset);  // 强制转换并调用窗口专用版本
  }

  /**
   * 【方法作用】：实现窗口聚合函数的"计算结果"逻辑（适配器方法）
   * 
   * 【详细说明】：
   * 这是AggImplementor接口中定义的方法的实现。
   * 它的作用是将通用的AggContext和AggResultContext转换为窗口专用的WinAggContext和WinAggResultContext，
   * 然后调用窗口专用版本的方法。
   * 
   * 【设计模式】：适配器模式
   * 
   * 【参数说明】：
   * @param info AggContext 通用聚合上下文（会被强制转换为WinAggContext）
   * @param result AggResultContext 通用结果上下文（会被强制转换为WinAggResultContext）
   * 
   * 【返回值】：Expression 表示计算结果的表达式树节点
   * 
   * 【调用链】：父类StrictAggImplementor -> implementNotNullResult(AggContext) -> 本方法 -> implementNotNullResult(WinAggContext)
   * 
   * 【final关键字】：标记为final，防止子类重写
   */
  @Override protected final Expression implementNotNullResult(AggContext info,  // 覆盖父类方法
      AggResultContext result) {  // 接收通用结果上下文
    return implementNotNullResult((WinAggContext) info,  // 强制转换上下文
        (WinAggResultContext) result);  // 强制转换结果上下文，并调用窗口专用版本
  }

  /**
   * 【方法作用】：判断当窗口帧完整时是否需要缓存数据
   * 
   * 【详细说明】：
   * 这个方法决定了在窗口帧保持完整（没有变化）的情况下，是否需要缓存数据。
   * 
   * 【什么是窗口帧完整？】
   * 窗口帧（window frame）是窗口函数中定义的当前行相关的行范围。
   * 例如：ROWS BETWEEN 2 PRECEDING AND 1 FOLLOWING
   * 当滑动窗口时，窗口帧会逐行移动。
   * 
   * 【为什么需要缓存？】
   * 某些窗口函数（如FIRST_VALUE、LAST_VALUE）需要访问窗口内的所有数据，
   * 如果窗口帧完整，可以缓存这些数据以提高性能，避免重复计算。
   * 
   * 【返回值说明】：
   * - true：需要缓存数据（默认值）
   * - false：不需要缓存数据
   * 
   * 【默认行为】：返回true，表示严格窗口聚合函数通常需要缓存
   * 
   * 【性能影响】：
   * - 缓存可以减少重复计算，提高性能
   * - 但缓存会占用内存，需要权衡
   * 
   * 【重写建议】：如果子类实现的窗口函数不需要缓存（例如ROW_NUMBER），可以重写此方法返回false
   */
  @Override public boolean needCacheWhenFrameIntact() {  // 实现WinAggImplementor接口方法
    return true;  // 默认返回true，表示当窗口帧完整时需要缓存数据
  }
}
