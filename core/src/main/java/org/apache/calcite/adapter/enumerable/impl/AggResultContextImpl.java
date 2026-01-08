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
// 声明包名：org.apache.calcite.adapter.enumerable.impl，表示该类属于Calcite框架的可枚举适配器实现包
package org.apache.calcite.adapter.enumerable.impl;

// 导入AggResultContext接口，该接口定义了聚合结果上下文的契约
import org.apache.calcite.adapter.enumerable.AggResultContext;
// 导入PhysType类，表示物理类型，用于描述数据在运行时的类型信息
import org.apache.calcite.adapter.enumerable.PhysType;
// 导入RexToLixTranslator类，用于将Rex表达式（关系表达式）转换为Linq4j表达式（Java表达式）
import org.apache.calcite.adapter.enumerable.RexToLixTranslator;
// 导入BlockBuilder类，用于构建Java代码块
import org.apache.calcite.linq4j.tree.BlockBuilder;
// 导入Expression类，表示Linq4j表达式树的基类
import org.apache.calcite.linq4j.tree.Expression;
// 导入ParameterExpression类，表示参数表达式，用于声明方法参数
import org.apache.calcite.linq4j.tree.ParameterExpression;
// 导入AggregateCall类，表示聚合函数调用，如SUM、COUNT、AVG等
import org.apache.calcite.rel.core.AggregateCall;
// 导入SqlConformanceEnum枚举，定义SQL符合性级别（如DEFAULT、STRICT等）
import org.apache.calcite.sql.validate.SqlConformanceEnum;

// 导入Nullable注解，用于标注可能为null的值，由CheckerFramework提供
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入List接口，Java集合框架的核心接口
import java.util.List;

// 导入requireNonNull静态方法，用于检查非空参数，如果为null则抛出NullPointerException
import static java.util.Objects.requireNonNull;

/**
 * Implementation of
 * {@link org.apache.calcite.adapter.enumerable.AggResultContext}.
 * 聚合结果上下文实现类
 * 
 * 类作用：
 * 该类实现了AggResultContext接口，是Calcite框架中用于处理聚合操作结果阶段的核心上下文类。
 * 它继承自AggResetContextImpl，扩展了聚合重置上下文的功能，专门用于聚合函数的结果计算阶段。
 * 
 * 核心功能：
 * 1. 提供聚合结果计算所需的上下文信息，包括聚合函数调用、分组键、累加器等
 * 2. 支持访问分组键的字段，用于从分组键中提取特定字段值
 * 3. 提供RexToLixTranslator转换器，用于将关系表达式转换为Java表达式
 * 4. 管理代码块构建器，用于生成聚合结果计算的Java代码
 * 
 * 使用场景：
 * 在Enumerable适配器中，当需要生成聚合函数结果的计算代码时使用。
 * 例如：在生成SUM、COUNT、AVG等聚合函数的最终结果代码时，需要通过该上下文获取必要的信息。
 * 
 * 设计模式：
 * 采用了上下文对象（Context Object）模式，将聚合结果计算所需的所有信息封装在一个对象中，
 * 便于在代码生成过程中传递和使用。
 */
// 类定义：AggResultContextImpl继承自AggResetContextImpl并实现AggResultContext接口
// 继承AggResetContextImpl：表示它具备聚合重置上下文的所有功能（如访问累加器、代码块等）
// 实现AggResultContext：表示它提供了聚合结果计算阶段所需的特定功能
public class AggResultContextImpl extends AggResetContextImpl
    implements AggResultContext {
  // 成员变量：call - 聚合函数调用对象
  // 类型：AggregateCall（可为null）
  // 作用：表示当前正在处理的聚合函数调用，包含聚合函数的类型、参数、过滤条件等信息
  // 例如：SUM(salary)、COUNT(*)、AVG(age)等
  // 注意：对于AggAddContextImpl，该字段为null（因为AggAddContextImpl不需要聚合调用信息）
  private final @Nullable AggregateCall call;
  
  // 成员变量：key - 分组键参数表达式
  // 类型：ParameterExpression（可为null）
  // 作用：表示GROUP BY子句中的分组键，用于标识不同分组的参数表达式
  // 例如：当SQL中有"GROUP BY dept_id"时，该参数表达式代表dept_id字段
  // 在生成的Java代码中，该参数表达式会被用于访问分组键的值
  // 注意：对于AggAddContextImpl，该字段为null（因为添加阶段不需要访问分组键）
  private final @Nullable ParameterExpression key;
  
  // 成员变量：keyPhysType - 分组键的物理类型
  // 类型：PhysType（可为null）
  // 作用：描述分组键在运行时的物理类型信息，包括Java类型、字段布局等
  // 该类型信息用于：
  // 1. 生成正确的Java类型代码
  // 2. 访问分组键的各个字段（当分组键是复合类型时）
  // 3. 创建类型工厂用于表达式转换
  // 注意：对于AggAddContextImpl，该字段为null（因为添加阶段不需要分组键类型信息）
  private final @Nullable PhysType keyPhysType;

  /**
   * Creates aggregate result context.
   * 创建聚合结果上下文对象
   *
   * @param block Code block that will contain the result calculation statements
   *             代码块构建器，用于包含结果计算语句
   *             该代码块将生成聚合结果计算的Java代码
   *             例如：生成计算SUM结果的代码 "result = sumAccumulator;"
   * @param call Aggregate call
   *             聚合函数调用对象，包含聚合函数的详细信息
   *             例如：SUM函数调用，包含参数列表、是否distinct、过滤条件等
   * @param accumulator Accumulator variables that store the intermediate
   *                    aggregate state
   *                    累加器变量列表，存储聚合的中间状态
   *                    例如：SUM累加器、COUNT累加器等，这些变量在聚合过程中不断更新
   * @param key Key
   *            分组键参数表达式，表示GROUP BY的分组字段
   *            例如：当SQL为"GROUP BY dept_id"时，该参数代表dept_id
   * @param keyPhysType 分组键的物理类型，描述分组键的类型信息
   *                   用于类型转换和字段访问
   *                   例如：如果是复合分组键，该类型包含所有分组的字段类型
   */
  // 构造方法：初始化聚合结果上下文对象
  // 参数说明：
  // - block: 代码块构建器，用于生成聚合结果计算的代码
  // - call: 聚合函数调用对象（可为null）
  // - accumulator: 累加器变量列表，存储聚合中间状态
  // - key: 分组键参数表达式（可为null）
  // - keyPhysType: 分组键的物理类型（可为null）
  public AggResultContextImpl(BlockBuilder block, @Nullable AggregateCall call,
      List<Expression> accumulator, @Nullable ParameterExpression key,
      @Nullable PhysType keyPhysType) {
    // 调用父类AggResetContextImpl的构造方法，初始化代码块和累加器
    // 这确保了父类的功能（如访问累加器和代码块）在子类中可用
    super(block, accumulator);
    // 保存聚合函数调用对象到成员变量
    // null for AggAddContextImpl - 说明AggAddContextImpl不需要聚合调用信息
    this.call = call; // null for AggAddContextImpl
    // 保存分组键参数表达式到成员变量
    // 该表达式用于访问分组键的值
    this.key = key;
    // 保存分组键的物理类型到成员变量
    // null for AggAddContextImpl - 说明AggAddContextImpl不需要分组键类型信息
    this.keyPhysType = keyPhysType; // null for AggAddContextImpl
  }

  /**
   * 方法：key()
   * 作用：返回分组键的参数表达式
   * 
   * 返回值：ParameterExpression - 分组键的参数表达式，如果不存在分组键则返回null
   * 
   * 使用场景：
   * 在生成聚合结果代码时，如果需要访问分组键的值，可以通过该方法获取分组键表达式
   * 例如：在计算每个分组的平均值时，可能需要知道当前是哪个分组
   * 
   * 注意事项：
   * - 如果是全局聚合（无GROUP BY），该方法返回null
   * - 如果有GROUP BY，该方法返回分组键的参数表达式
   */
  // 重写AggResultContext接口的key()方法
  // 返回分组键的参数表达式
  @Override public @Nullable Expression key() {
    // 直接返回成员变量key，该变量在构造方法中初始化
    return key;
  }

  /**
   * 方法：keyField(int i)
   * 作用：返回分组键中指定索引位置的字段引用表达式
   * 
   * 参数：int i - 字段索引，从0开始
   *       例如：如果分组键是(dept_id, year)，i=0返回dept_id，i=1返回year
   * 
   * 返回值：Expression - 字段引用表达式，表示访问分组键的第i个字段
   * 
   * 异常：如果keyPhysType或key为null，抛出NullPointerException
   *       这意味着该方法只能在有分组键的情况下调用
   * 
   * 使用场景：
   * 当GROUP BY包含多个字段（复合分组键）时，需要访问分组键中的特定字段
   * 例如：SQL "GROUP BY dept_id, year"，需要分别访问dept_id和year字段
   * 
   * 实现细节：
   * 1. 使用requireNonNull确保keyPhysType不为null
   * 2. 调用keyPhysType的fieldReference方法生成字段引用表达式
   * 3. 传入key（分组键参数表达式）和字段索引i
   * 4. 生成的表达式类似于 "key.dept_id" 或 "key.year"
   */
  // 重写AggResultContext接口的keyField()方法
  // 参数：i - 字段索引
  @Override public Expression keyField(int i) {
    // 使用requireNonNull确保keyPhysType不为null，否则抛出NullPointerException
    // 调用keyPhysType的fieldReference方法，传入key和字段索引i
    // 生成字段引用表达式，例如：key.field0
    return requireNonNull(keyPhysType, "keyPhysType")
        .fieldReference(requireNonNull(key, "key"), i);
  }

  /**
   * 方法：call()
   * 作用：返回聚合函数调用对象
   * 
   * 返回值：AggregateCall - 聚合函数调用对象，包含聚合函数的所有信息
   * 
   * 异常：如果call为null，抛出NullPointerException
   *       这意味着该方法只能在有聚合调用的情况下使用
   * 
   * 返回的AggregateCall对象包含：
   * - 聚合函数类型（SUM、COUNT、AVG、MIN、MAX等）
   * - 聚合函数的参数列表（哪些字段参与聚合）
   * - 是否使用DISTINCT
   * - 过滤条件（FILTER子句）
   * - 聚合函数的别名
   * 
   * 使用场景：
   * 在生成聚合结果代码时，需要知道聚合函数的类型和参数
   * 例如：生成SUM结果时需要知道是对哪个字段求和
   */
  // 重写AggResultContext接口的call()方法
  @Override public AggregateCall call() {
    // 使用requireNonNull确保call不为null，否则抛出NullPointerException
    // 返回聚合函数调用对象
    return requireNonNull(call, "call");
  }

  /**
   * 方法：resultTranslator()
   * 作用：创建并返回用于聚合结果阶段的RexToLixTranslator转换器
   * 
   * 返回值：RexToLixTranslator - 关系表达式到Linq4j表达式的转换器
   * 
   * 异常：如果keyPhysType为null，抛出NullPointerException
   * 
   * RexToLixTranslator的作用：
   * - 将Calcite的关系表达式（RexNode）转换为Linq4j表达式（Java表达式）
   * - 处理类型转换，确保生成的Java代码类型正确
   * - 处理SQL符合性，确保生成的代码符合特定的SQL标准
   * 
   * 使用场景：
   * 在生成聚合结果代码时，如果需要将关系表达式转换为Java表达式
   * 例如：将RexNode表示的"salary * 1.1"转换为Java表达式"salary * 1.1"
   * 
   * 转换器的配置：
   * - 类型工厂：从keyPhysType获取，用于创建正确的类型信息
   * - 代码块：当前代码块构建器，用于将生成的表达式添加到代码块中
   * - 输入行表达式：传入null，表示聚合结果阶段不需要输入行
   * - SQL符合性：使用DEFAULT级别，表示使用标准SQL语法
   */
  // 重写AggResultContext接口的resultTranslator()方法
  @Override public RexToLixTranslator resultTranslator() {
    // 使用requireNonNull确保keyPhysType不为null，否则抛出NullPointerException
    requireNonNull(keyPhysType, "keyPhysType");
    // 创建并返回RexToLixTranslator转换器，专门用于聚合结果阶段
    // 参数说明：
    // - keyPhysType.getTypeFactory(): 获取类型工厂，用于创建类型信息
    // - currentBlock(): 获取当前代码块构建器，用于生成代码
    // - null: 输入行表达式为null，因为结果阶段不需要输入行
    // - SqlConformanceEnum.DEFAULT: 使用默认SQL符合性级别
    return RexToLixTranslator.forAggregation(keyPhysType.getTypeFactory(),
        currentBlock(), null, SqlConformanceEnum.DEFAULT);
  }
}
