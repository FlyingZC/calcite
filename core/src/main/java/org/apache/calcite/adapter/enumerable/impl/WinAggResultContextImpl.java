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
// Apache许可证声明，说明代码的版权和使用许可
package org.apache.calcite.adapter.enumerable.impl; // 声明包名，这个类位于org.apache.calcite.adapter.enumerable.impl包下

// 导入RexToLixTranslator类，用于将Rex表达式转换为LINQ表达式
import org.apache.calcite.adapter.enumerable.RexToLixTranslator;
// 导入WinAggFrameResultContext接口，窗口聚合帧结果上下文接口
import org.apache.calcite.adapter.enumerable.WinAggFrameResultContext;
// 导入WinAggImplementor接口，窗口聚合实现器接口
import org.apache.calcite.adapter.enumerable.WinAggImplementor;
// 导入WinAggResultContext接口，窗口聚合结果上下文接口
import org.apache.calcite.adapter.enumerable.WinAggResultContext;
// 导入BlockBuilder类，用于构建代码块
import org.apache.calcite.linq4j.tree.BlockBuilder;
// 导入Expression类，表示LINQ表达式
import org.apache.calcite.linq4j.tree.Expression;

// 导入List接口，用于列表集合操作
import java.util.List;
// 导入Function接口，用于函数式编程
import java.util.function.Function;

/**
 * Implementation of
 * {@link org.apache.calcite.adapter.enumerable.WinAggResultContext}.
 */
// 类注释：WinAggResultContextImpl是WinAggResultContext接口的实现类
// 这个类是抽象类，用于处理窗口聚合函数的结果上下文
// 它继承自AggResultContextImpl（聚合结果上下文实现），并实现了WinAggResultContext接口
// 窗口聚合函数是指在SQL窗口函数（如SUM() OVER()、AVG() OVER()等）中使用的聚合函数
// 这个类提供了访问窗口帧、分区、行索引等窗口相关信息的上下文环境
public abstract class WinAggResultContextImpl extends AggResultContextImpl // 抽象类，继承自AggResultContextImpl
    implements WinAggResultContext { // 实现WinAggResultContext接口

  // 成员变量：frame是一个函数对象，类型为Function<BlockBuilder, WinAggFrameResultContext>
  // 这个函数接收一个BlockBuilder参数，返回一个WinAggFrameResultContext对象
  // WinAggFrameResultContext表示窗口聚合帧的结果上下文，包含了窗口帧的详细信息
  // 使用final修饰，表示这个引用在构造后不能被改变，保证线程安全和不可变性
  // 这个成员变量是延迟初始化的，只有当需要访问帧上下文时才会调用这个函数
  // 延迟初始化的好处是可以避免不必要的计算，提高性能
  private final Function<BlockBuilder, WinAggFrameResultContext> frame;

  /**
   * Creates window aggregate result context.
   * 创建窗口聚合结果上下文的构造方法
   *
   * @param block code block that will contain the added initialization
   *              参数block：代码块构建器，用于包含添加的初始化代码
   *              BlockBuilder是Calcite中用于构建Java代码块的工具类
   *              它允许我们动态地构建Java代码，用于生成可执行的聚合函数实现
   * @param accumulator accumulator variables that store the intermediate
   *                    aggregate state
   *              参数accumulator：累加器变量列表，用于存储聚合的中间状态
   *              每个聚合函数都有一个或多个累加器来保存中间计算结果
   *              例如：SUM函数的累加器存储累加和，COUNT函数的累加器存储计数
   *              List<Expression>表示这些累加器都是以表达式形式存在的
   */
  // 构造方法：protected修饰符，表示只有子类或同一个包内的类可以调用
  // 这是一个受保护的构造方法，用于创建窗口聚合结果上下文实例
  protected WinAggResultContextImpl(BlockBuilder block, // 参数1：代码块构建器，用于构建生成的代码
      List<Expression> accumulator, // 参数2：累加器变量列表，存储聚合中间状态
      Function<BlockBuilder, WinAggFrameResultContext> frameContextBuilder) { // 参数3：帧上下文构建器函数
    // 调用父类AggResultContextImpl的构造方法
    // 参数说明：
    // - block：传递给父类的代码块构建器
    // - null：传递null给父类的第二个参数（通常是一些其他上下文信息）
    // - accumulator：传递累加器变量列表给父类
    // - null：传递null给父类的第四个参数
    // - null：传递null给父类的第五个参数
    // 这里使用null是因为窗口聚合的上下文与普通聚合有所不同，某些参数不需要
    super(block, null, accumulator, null, null);
    // 将传入的帧上下文构建器函数赋值给成员变量frame
    // frame是一个函数，当调用时会根据当前的BlockBuilder创建WinAggFrameResultContext
    // 这种设计允许延迟创建帧上下文，只在需要时才创建
    this.frame = frameContextBuilder;
  }

  /**
   * 构造方法的重载版本，使用Guava的Function接口
   * 这个构造方法已被标记为@Deprecated，将在2.0版本之前移除
   * 原因是Calcite正在从Guava库迁移到Java标准库
   * Guava的com.google.common.base.Function与Java 8的java.util.function.Function功能相同
   */
  @SuppressWarnings("Guava") // 压制Guava相关的警告
  @Deprecated // 标记为已弃用，将在2.0版本之前移除
  protected WinAggResultContextImpl(BlockBuilder block, // 参数1：代码块构建器
      List<Expression> accumulator, // 参数2：累加器变量列表
      com.google.common.base.Function<BlockBuilder, WinAggFrameResultContext> frameContextBuilder) { // 参数3：Guava版本的帧上下文构建器
    // 调用上面的主构造方法，将Guava的Function转换为Java 8的Function
    // 使用类型转换将com.google.common.base.Function转换为java.util.function.Function
    // 这样可以保持向后兼容性，同时逐步迁移到Java标准库
    this(block, accumulator, // 传递block和accumulator参数
        (Function<BlockBuilder, WinAggFrameResultContext>) frameContextBuilder); // 强制转换函数类型
  }

  /**
   * 私有辅助方法：获取当前的窗口帧结果上下文
   * 这个方法是私有的，只在类内部使用
   * 
   * @return WinAggFrameResultContext 窗口帧结果上下文对象
   * 
   * 方法作用：
   * 1. 通过调用frame函数来获取或创建WinAggFrameResultContext实例
   * 2. 传入currentBlock()作为参数，这是当前正在构建的代码块
   * 3. 延迟初始化：只有在第一次调用时才会真正创建帧上下文
   * 4. 后续调用会返回相同的实例（如果frame函数实现为缓存的话）
   * 
   * 设计考虑：
   * - 使用函数式编程的方式，将创建帧上下文的逻辑封装在frame函数中
   * - 每次调用时传入currentBlock()，确保帧上下文与当前代码块同步
   * - 这种设计允许帧上下文根据代码块的状态动态调整
   */
  private WinAggFrameResultContext getFrame() { // 私有方法，返回窗口帧结果上下文
    // 调用frame函数，传入当前代码块，返回窗口帧结果上下文
    // currentBlock()是从父类继承的方法，返回当前正在构建的代码块
    // frame.apply()会执行函数，创建或返回WinAggFrameResultContext实例
    return frame.apply(currentBlock());
  }

  /**
   * 重写方法：获取指定行的参数表达式列表
   * 这个方法用于将Rex表达式（Calcite内部的表达式表示）转换为LINQ表达式（Java代码表达式）
   * 
   * @param rowIndex 行索引表达式，表示要获取哪一行的参数
   * @return List<Expression> 参数表达式列表，每个参数都是一个LINQ表达式
   * 
   * 方法作用：
   * 1. 根据行索引获取该行的行翻译器（RexToLixTranslator）
   * 2. 使用翻译器将Rex参数列表转换为LINQ表达式列表
   * 3. 返回转换后的表达式列表，这些表达式可以直接用于生成Java代码
   * 
   * 使用场景：
   * - 在实现窗口聚合函数时，需要访问函数的参数
   * - 例如：SUM(salary) OVER (...)，需要访问salary列的值
   * - 这个方法将salary列的Rex表达式转换为可执行的Java表达式
   */
  @Override public final List<Expression> arguments(Expression rowIndex) { // 重写父类方法，final表示不能被子类重写
    // 调用rowTranslator方法获取行翻译器，传入行索引
    // rowTranslator方法会根据行索引创建一个翻译器，用于将Rex表达式转换为LINQ表达式
    // 然后调用translateList方法，将rexArguments()返回的Rex表达式列表转换为LINQ表达式列表
    // rexArguments()是从父类继承的方法，返回当前聚合函数的Rex参数列表
    return rowTranslator(rowIndex).translateList(rexArguments());
  }

  /**
   * 重写方法：计算窗口帧内的索引
   * 这个方法用于根据偏移量和查找类型计算窗口帧内的实际行索引
   * 
   * @param offset 偏移量表达式，表示距离当前位置的偏移
   *               例如：offset=1表示下一行，offset=-1表示上一行
   * @param seekType 查找类型，枚举值，定义如何查找行
   *                 例如：SeekType.SET表示绝对位置，SeekType.BOUND表示边界
   * @return Expression 计算后的索引表达式
   * 
   * 方法作用：
   * 1. 根据偏移量和查找类型计算窗口帧内的行索引
   * 2. 处理各种窗口帧类型（ROWS、RANGE、GROUPS）的索引计算
   * 3. 考虑窗口帧的边界条件（UNBOUNDED PRECEDING、CURRENT ROW等）
   * 
   * 使用场景：
   * - 实现需要访问窗口帧内特定行的聚合函数
   * - 例如：LAG(col, 1)需要访问前一行的值
   * - LEAD(col, 1)需要访问后一行的值
   * - FIRST_VALUE(col)需要访问窗口帧的第一行
   */
  @Override public Expression computeIndex(Expression offset, // 重写接口方法
      WinAggImplementor.SeekType seekType) { // 参数：偏移量和查找类型
    // 调用getFrame()获取窗口帧上下文，然后调用其computeIndex方法
    // 将计算委托给帧上下文对象，因为帧上下文包含了窗口帧的详细信息
    // 返回计算后的索引表达式
    return getFrame().computeIndex(offset, seekType);
  }

  /**
   * 重写方法：判断指定行是否在当前窗口帧内
   * 
   * @param rowIndex 行索引表达式
   * @return Expression 布尔表达式，true表示在窗口帧内，false表示不在
   * 
   * 方法作用：
   * 1. 检查给定的行索引是否落在当前窗口帧的范围内
   * 2. 窗口帧的范围由OVER子句中的ROWS/RANGE/GROUPS子句定义
   * 3. 返回一个布尔表达式，可以在生成的代码中用于条件判断
   * 
   * 使用场景：
   * - 实现需要遍历窗口帧内所有行的聚合函数
   * - 例如：SUM()需要累加窗口帧内所有行的值
   * - 可以用这个方法判断一行是否应该被包含在聚合计算中
   */
  @Override public Expression rowInFrame(Expression rowIndex) { // 重写接口方法
    // 调用getFrame()获取窗口帧上下文，然后调用其rowInFrame方法
    // 将判断逻辑委托给帧上下文对象
    // 返回布尔表达式，表示该行是否在窗口帧内
    return getFrame().rowInFrame(rowIndex);
  }

  /**
   * 重写方法：判断指定行是否在当前分区内
   * 
   * @param rowIndex 行索引表达式
   * @return Expression 布尔表达式，true表示在分区内，false表示不在
   * 
   * 方法作用：
   * 1. 检查给定的行索引是否落在当前窗口分区内
   * 2. 分区由OVER子句中的PARTITION BY子句定义
   * 3. 每个分区是独立的，窗口函数在每个分区内分别计算
   * 
   * 使用场景：
   * - 实现需要访问分区信息的聚合函数
   * - 例如：RANK()、DENSE_RANK()需要在分区内计算排名
   * - 可以用这个方法确保不会跨分区访问数据
   */
  @Override public Expression rowInPartition(Expression rowIndex) { // 重写接口方法
    // 调用getFrame()获取窗口帧上下文，然后调用其rowInPartition方法
    // 将判断逻辑委托给帧上下文对象
    // 返回布尔表达式，表示该行是否在当前分区内
    return getFrame().rowInPartition(rowIndex);
  }

  /**
   * 重写方法：获取指定行的行翻译器
   * 
   * @param rowIndex 行索引表达式
   * @return RexToLixTranslator 行翻译器对象
   * 
   * 方法作用：
   * 1. 创建或获取指定行的RexToLixTranslator对象
   * 2. 行翻译器用于将Rex表达式转换为LINQ表达式
   * 3. 每一行都有自己的翻译器，因为不同行的变量绑定可能不同
   * 
   * 使用场景：
   * - 在arguments方法中已经使用过这个方法
   * - 当需要访问特定行的列值时，需要使用该行的翻译器
   * - 翻译器会处理变量作用域、类型转换等问题
   */
  @Override public RexToLixTranslator rowTranslator(Expression rowIndex) { // 重写接口方法
    // 调用getFrame()获取窗口帧上下文，然后调用其rowTranslator方法
    // 将翻译器的创建和获取委托给帧上下文对象
    // 返回指定行的RexToLixTranslator对象
    return getFrame().rowTranslator(rowIndex);
  }

  /**
   * 重写方法：比较两行的顺序
   * 
   * @param a 第一行的索引表达式
   * @param b 第二行的索引表达式
   * @return Expression 比较结果表达式，通常是一个整数（负数、0、正数）
   * 
   * 方法作用：
   * 1. 比较两行的相对顺序
   * 2. 返回值类似于Java的Comparable接口的compareTo方法
   * 3. 负数表示a在b之前，0表示相等，正数表示a在b之后
   * 
   * 使用场景：
   * - 实现需要排序或比较行的聚合函数
   * - 例如：RANK()、DENSE_RANK()需要比较行的顺序
   * - NTILE(n)需要根据行顺序将行分配到桶中
   */
  @Override public Expression compareRows(Expression a, Expression b) { // 重写接口方法
    // 调用getFrame()获取窗口帧上下文，然后调用其compareRows方法
    // 将比较逻辑委托给帧上下文对象
    // 返回比较结果的表达式
    return getFrame().compareRows(a, b);
  }

  /**
   * 重写方法：获取当前行的索引
   * 
   * @return Expression 当前行的索引表达式
   * 
   * 方法作用：
   * 1. 返回当前正在处理的行的索引
   * 2. 这个索引是相对于整个结果集的绝对索引
   * 3. 用于定位当前行在窗口中的位置
   * 
   * 使用场景：
   * - 实现需要知道当前位置的窗口函数
   * - 例如：ROW_NUMBER()需要知道当前是第几行
   * - 在计算偏移量时需要以当前行为基准
   */
  @Override public Expression index() { // 重写接口方法
    // 调用getFrame()获取窗口帧上下文，然后调用其index方法
    // 将获取当前行索引的逻辑委托给帧上下文对象
    // 返回当前行的索引表达式
    return getFrame().index();
  }

  /**
   * 重写方法：获取窗口帧的起始索引
   * 
   * @return Expression 窗口帧起始索引表达式
   * 
   * 方法作用：
   * 1. 返回当前窗口帧的起始位置索引
   * 2. 起始索引由OVER子句中的窗口帧定义决定
   * 3. 例如：ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING，起始索引是当前行-1
   * 
   * 使用场景：
   * - 实现需要遍历窗口帧的聚合函数
   * - 可以从起始索引开始遍历到结束索引
   * - 用于确定窗口帧的范围
   */
  @Override public Expression startIndex() { // 重写接口方法
    // 调用getFrame()获取窗口帧上下文，然后调用其startIndex方法
    // 将获取起始索引的逻辑委托给帧上下文对象
    // 返回窗口帧的起始索引表达式
    return getFrame().startIndex();
  }

  /**
   * 重写方法：获取窗口帧的结束索引
   * 
   * @return Expression 窗口帧结束索引表达式
   * 
   * 方法作用：
   * 1. 返回当前窗口帧的结束位置索引
   * 2. 结束索引由OVER子句中的窗口帧定义决定
   * 3. 例如：ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING，结束索引是当前行+1
   * 
   * 使用场景：
   * - 实现需要遍历窗口帧的聚合函数
   * - 可以从起始索引遍历到结束索引
   * - 用于确定窗口帧的范围
   */
  @Override public Expression endIndex() { // 重写接口方法
    // 调用getFrame()获取窗口帧上下文，然后调用其endIndex方法
    // 将获取结束索引的逻辑委托给帧上下文对象
    // 返回窗口帧的结束索引表达式
    return getFrame().endIndex();
  }

  /**
   * 重写方法：判断窗口帧是否有行
   * 
   * @return Expression 布尔表达式，true表示窗口帧有行，false表示没有
   * 
   * 方法作用：
   * 1. 检查当前窗口帧内是否包含任何行
   * 2. 某些情况下窗口帧可能为空（例如：在分区的边界）
   * 3. 返回布尔表达式用于条件判断
   * 
   * 使用场景：
   * - 实现需要处理空窗口帧的聚合函数
   * - 避免在空窗口帧上进行计算导致错误
   * - 例如：FIRST_VALUE()在空窗口帧时应该返回NULL
   */
  @Override public Expression hasRows() { // 重写接口方法
    // 调用getFrame()获取窗口帧上下文，然后调用其hasRows方法
    // 将判断是否有行的逻辑委托给帧上下文对象
    // 返回布尔表达式，表示窗口帧是否有行
    return getFrame().hasRows();
  }

  /**
   * 重写方法：获取窗口帧的行数
   * 
   * @return Expression 行数表达式
   * 
   * 方法作用：
   * 1. 返回当前窗口帧中包含的行数
   * 2. 行数 = endIndex - startIndex + 1（对于ROWS类型）
   * 3. 对于RANGE和GROUPS类型，计算方式可能不同
   * 
   * 使用场景：
   * - 实现需要知道窗口帧大小的聚合函数
   * - 例如：AVG()需要行数来计算平均值
   * - COUNT()可以使用这个值来快速获取计数
   */
  @Override public Expression getFrameRowCount() { // 重写接口方法
    // 调用getFrame()获取窗口帧上下文，然后调用其getFrameRowCount方法
    // 将获取行数的逻辑委托给帧上下文对象
    // 返回窗口帧的行数表达式
    return getFrame().getFrameRowCount();
  }

  /**
   * 重写方法：获取当前分区的行数
   * 
   * @return Expression 分区行数表达式
   * 
   * 方法作用：
   * 1. 返回当前窗口分区中包含的行数
   * 2. 分区由PARTITION BY子句定义
   * 3. 每个分区是独立的，函数在每个分区内分别计算
   * 
   * 使用场景：
   * - 实现需要知道分区大小的聚合函数
   * - 例如：PERCENT_RANK()需要知道分区的总行数
   * - CUME_DIST()也需要分区行数来计算累积分布
   * - RANK()在计算排名时可能需要考虑分区大小
   */
  @Override public Expression getPartitionRowCount() { // 重写接口方法
    // 调用getFrame()获取窗口帧上下文，然后调用其getPartitionRowCount方法
    // 将获取分区行数的逻辑委托给帧上下文对象
    // 返回当前分区的行数表达式
    return getFrame().getPartitionRowCount();
  }
} // 类结束
