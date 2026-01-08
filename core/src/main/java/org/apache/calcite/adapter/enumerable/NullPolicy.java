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
package org.apache.calcite.adapter.enumerable; // 声明包名,该类属于 org.apache.calcite.adapter.enumerable 包,这是 Calcite 框架中用于可枚举适配器的包

/**
 * 描述函数/操作符在什么情况下会返回 null 的策略枚举类
 *
 * <p>这个枚举定义了 Calcite 在处理 SQL 函数和操作符时,如何处理 null 值的规则
 * 在 SQL 中,不同的函数对 null 值的处理方式不同,这个枚举就是为了区分这些不同的处理策略
 *
 * <p>STRICT 和 ANY 是相似的策略。STRICT 表示如果所有参数都不为 null,那么函数 f(a0, a1)
 * 绝不会返回 null。这意味着我们只需要检查参数就能判断函数是否会返回 null。
 * 在可能的情况下,应该优先使用 STRICT 而不是 ANY,因为 STRICT 提供了更强的保证,有助于优化器进行优化
 *
 * <p>这个类在 Calcite 的表达式求值和代码生成过程中非常重要,它决定了:
 * 1. 如何生成处理 null 值的代码
 * 2. 是否可以跳过某些 null 检查以优化性能
 * 3. 如何处理聚合函数中的 null 值
 * 4. 如何实现 SQL 标准中的 null 语义
 */
public enum NullPolicy { // 定义一个名为 NullPolicy 的公共枚举类,用于描述函数/操作符返回 null 的不同策略

  /**
   * 只有当所有参数都为 null 时才返回 null;如果所有参数都为 false 则返回 false,否则返回 true
   *
   * <p>这个策略主要用于逻辑运算符,特别是 AND 操作符
   * 例如:在 SQL 中,TRUE AND NULL AND FALSE 的结果是 FALSE(因为有一个参数是 FALSE)
   * 而 NULL AND NULL 的结果是 NULL(因为所有参数都是 NULL)
   *
   * <p>这个策略的特点是:
   * - 需要检查所有参数才能确定结果
   * - 如果有任何一个非 null 的参数是 false,则直接返回 false
   * - 只有当所有参数都是 null 时,才返回 null
   * - 用于需要"短路求值"的场景
   */
  ALL, // 枚举值 ALL:表示只有当所有参数都为 null 时才返回 null,否则根据逻辑判断返回 true 或 false

  /**
   * 只有当任意一个参数为 null 时才返回 null
   *
   * <p>这是最常见的 null 处理策略,大多数 SQL 函数都遵循这个规则
   * 例如:在 SQL 中,1 + NULL 的结果是 NULL,因为有一个参数是 NULL
   *
   * <p>这个策略的特点是:
   * - 只要有一个参数是 null,函数就返回 null
   * - 如果所有参数都不为 null,则函数正常计算并返回结果
   * - 提供了强保证:只要参数不为 null,结果一定不为 null
   * - 这使得优化器可以进行某些优化,例如跳过 null 检查
   *
   * <p>示例:
   * - 算术运算:1 + 2 = 3, 1 + NULL = NULL, NULL + NULL = NULL
   * - 比较运算:1 > 2 = false, 1 > NULL = NULL, NULL > 2 = NULL
   */
  STRICT, // 枚举值 STRICT:表示只要有一个参数为 null,函数就返回 null;如果所有参数都不为 null,则正常计算

  /**
   * 如果有一个参数为 null,则返回 null,但也可能在其他情况下返回 null
   *
   * <p>这个策略比 STRICT 更宽松,它表示:
   * - 如果有参数为 null,函数会返回 null
   * - 但是即使所有参数都不为 null,函数也可能因为其他原因返回 null
   *
   * <p>这个策略的特点是:
   * - 提供了部分保证:参数为 null 时一定返回 null
   - 但不保证参数不为 null 时结果一定不为 null
   * - 用于那些可能因为其他原因(如除零错误、类型转换失败等)返回 null 的函数
   *
   * <p>示例:
   * - 某些特殊的转换函数可能在参数有效时也返回 null
   * - 某些用户定义函数可能有自定义的 null 返回逻辑
   */
  SEMI_STRICT, // 枚举值 SEMI_STRICT:表示参数为 null 时返回 null,但也可能在其他情况下返回 null

  /**
   * 如果任意一个参数为 null,则返回 null
   *
   * <p>这个策略与 STRICT 相似,但语义上有细微差别
   * STRICT 强调"只有"在参数为 null 时才返回 null,而 ANY 只是说"如果"参数为 null 则返回 null
   *
   * <p>这个策略的特点是:
   * - 只要有一个参数是 null,函数就返回 null
   * - 但不保证参数不为 null 时结果一定不为 null
   * - 比 STRICT 弱,比 SEMI_STRICT 也弱
   * - 在某些情况下可以用于替代 STRICT,但优化效果不如 STRICT
   *
   * <p>为什么有 STRICT 和 ANY 两个相似的策略?
   * - STRICT 提供了更强的保证,优先使用 STRICT 可以帮助优化器做更多优化
   * - ANY 保留用于那些不确定是否能提供 STRICT 保证的场景
   */
  ANY, // 枚举值 ANY:表示如果任意一个参数为 null,则返回 null

  /**
   * 如果第一个参数为 null,则返回 null
   *
   * <p>这个策略用于那些只关心第一个参数是否为 null 的函数
   * 这在某些特殊的函数或操作符中很有用
   *
   * <p>这个策略的特点是:
   * - 只检查第一个参数是否为 null
   * - 其他参数的 null 值不影响函数的 null 返回行为
   * - 用于那些第一个参数是"接收者"或"目标对象"的函数
   *
   * <p>示例:
   * - 某些方法调用:obj.method(x, y) 只关心 obj 是否为 null
   * - 某些特殊操作符:COALESCE 函数可能使用这个策略
   */
  ARG0, // 枚举值 ARG0:表示如果第一个参数为 null,则返回 null

  /**
   * 不返回 null,或者函数的 null 行为无法用上述策略描述
   *
   * <p>这个策略表示:
   * - 函数永远不会返回 null(即使参数为 null)
   * - 或者函数的 null 行为太复杂,无法用上述简单的策略描述
   *
   * <p>这个策略的特点是:
   * - 用于那些总是返回非 null 结果的函数
   * - 或者用于那些有自定义 null 处理逻辑的函数
   * - 或者用于那些 null 行为不确定的函数
   *
   * <p>示例:
   * - 某些总是返回非 null 值的函数:如某些类型转换函数
   * - 某些特殊的聚合函数
   * - 某些用户定义函数
   */
  NONE // 枚举值 NONE:表示函数不返回 null,或者 null 行为无法用上述策略描述
} // 枚举类结束
