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
// Apache 许可证头部声明，说明该代码遵循 Apache 2.0 许可协议
package org.apache.calcite.linq4j.tree; // 声明该类属于 org.apache.calcite.linq4j.tree 包，这是 Calcite LINQ4J 表达式树构建的核心包

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 Checker Framework 的可空类型注解，用于静态空指针检查
import org.checkerframework.checker.nullness.qual.PolyNull; // 导入 Checker Framework 的多态可空类型注解

import java.lang.reflect.Modifier; // 导入 Java 反射 Modifier 类，用于访问类和成员的修饰符（如 public、final 等）
import java.lang.reflect.Type; // 导入 Java 反射 Type 接口，表示 Java 类型（包括泛型）
import java.util.ArrayList; // 导入 ArrayList，动态数组实现，用于存储语句列表
import java.util.HashMap; // 导入 HashMap，哈希表实现，用于存储可重用表达式的映射
import java.util.HashSet; // 导入 HashSet，哈希集合实现，用于存储已使用的变量名
import java.util.IdentityHashMap; // 导入 IdentityHashMap，使用恒等（==）而非 equals() 比较键的哈希映射
import java.util.List; // 导入 List 接口，表示有序集合
import java.util.Map; // 导入 Map 接口，表示键值对映射
import java.util.Set; // 导入 Set 接口，表示不重复元素的集合

import static java.util.Objects.requireNonNull; // 静态导入 Objects.requireNonNull 方法，用于空值检查

/**
 * Builder for {@link BlockStatement}. // BlockStatement 的构建器
 * // 这个类是 Calcite 表达式树系统中用于构建代码块的核心类，主要用于生成 Java 字节码或源代码
 * // 它负责管理语句列表、变量作用域、表达式优化等关键功能
 * 
 * <p>Has methods that help ensure that variable names are unique. // 提供帮助确保变量名称唯一的方法
 * // 该类还实现了变量去重、子表达式消除、内联优化等代码生成优化技术
 * // 在生成复杂查询代码时，能够自动优化生成的代码，避免重复计算
 */
public class BlockBuilder { // BlockBuilder 类定义，用于构建和优化代码块
  // statements: 存储代码块中的所有语句的列表，按添加顺序排列
  // 每个语句可以是声明语句、表达式语句、控制流语句等
  // 这是构建代码块的核心数据结构，最终会转换为 BlockStatement
  final List<Statement> statements = new ArrayList<>(); // 使用 ArrayList 存储语句列表，初始容量为默认值
  // variables: 存储当前代码块作用域内所有已声明的变量名称集合
  // 用于确保变量名的唯一性，避免重复声明导致的编译错误
  final Set<String> variables = new HashSet<>(); // 使用 HashSet 存储变量名，保证快速查找和唯一性
  /** Contains final-fine-to-reuse-declarations. // 包含可以重用的 final 声明
   * An entry to this map is added when adding final declaration of a // 当使用 optimize=true 参数添加 final 声明时，会向该映射添加一个条目
   * statement with optimize=true parameter. // 该映射用于实现公共子表达式消除（CSE）优化
   * // Key: 标准化后的表达式（去除类型差异）
   * // Value: 对应的声明语句，包含变量名和初始化表达式
   * // 当遇到相同的表达式时，可以直接重用已计算的变量，避免重复计算
   */ 
  final Map<Expression, DeclarationStatement> expressionForReuse = // 使用 HashMap 存储表达式到声明语句的映射
      new HashMap<>(); // 初始化 HashMap，用于实现表达式重用优化

  // optimizing: 布尔标志，指示是否启用优化功能
  // 当为 true 时，会执行以下优化：
  // 1. 公共子表达式消除（CSE）：检测并重用相同的表达式
  // 2. 变量内联：将只使用一次的变量直接内联到使用处
  // 3. 死代码消除：移除未使用的变量声明
  private final boolean optimizing; // 优化开关，构造后不可修改
  // parent: 父级 BlockBuilder 的引用，用于支持嵌套代码块
  // 允许子代码块访问父代码块中声明的变量
  // 用于实现变量的作用域链查找
  private final @Nullable BlockBuilder parent; // 父级构建器，可能为 null（表示顶层代码块）
  // removeUnused: 布尔标志，指示是否移除未使用的变量
  // 当为 true 时，toBlock() 方法会执行死代码消除，移除只声明但从未使用的变量
  // 通常与 optimizing 配合使用
  private final boolean removeUnused; // 是否移除未使用变量的标志

  // OPTIMIZE_SHUTTLE: 静态优化访问器实例，用于在代码块优化过程中遍历和转换表达式树
  // Shuttle 是访问者模式的实现，用于遍历表达式树并进行优化转换
  // 具体的优化逻辑由 OptimizeShuttle 类实现
  private static final Shuttle OPTIMIZE_SHUTTLE = new OptimizeShuttle(); // 创建静态优化访问器实例

  /** Private constructor. // 私有构造方法，不允许外部直接调用
   * // 使用私有构造方法强制通过公共工厂方法创建实例，确保参数的正确性
   * 
   * @param optimizing 是否启用优化功能（公共子表达式消除、变量内联等）
   * @param parent 父级 BlockBuilder，用于支持嵌套作用域，可为 null
   * @param removeUnused 是否移除未使用的变量，通常与 optimizing 配合使用
   */ 
  private BlockBuilder(boolean optimizing, @Nullable BlockBuilder parent, // 私有构造方法，接收三个参数
      boolean removeUnused) { // removeUnused 参数
    this.optimizing = optimizing; // 初始化优化标志
    this.parent = parent; // 初始化父级构建器引用
    this.removeUnused = removeUnused; // 初始化移除未使用变量的标志
  } // 构造方法结束

  /**
   * Creates a non-optimizing BlockBuilder. // 创建一个非优化的 BlockBuilder
   * // 这是一个便捷构造方法，默认启用优化功能
   * // 相当于调用 new BlockBuilder(true)
   * 
   * 使用场景：需要生成优化代码的常规情况
   */ 
  public BlockBuilder() { // 无参构造方法
    this(true); // 委托给单参数构造方法，传入 true（启用优化）
  } // 构造方法结束

  /**
   * Creates a BlockBuilder. // 创建一个 BlockBuilder
   * 
   * @param optimizing Whether to eliminate common sub-expressions // 是否消除公共子表达式
   * // 当为 true 时，会识别并重用相同的表达式，避免重复计算
   * // 当为 false 时，每个表达式都会独立计算，不进行优化
   * // 使用 false 的场景：表达式有副作用或时间依赖性，不能被重用
   * 
   * 示例：
   * - optimizing=true: int x = a + b; int y = a + b; → int x = a + b; int y = x;
   * - optimizing=false: int x = a + b; int y = a + b; → 保持原样
   */ 
  public BlockBuilder(boolean optimizing) { // 单参数构造方法
    this(optimizing, null); // 委托给双参数构造方法，parent 传入 null（顶层代码块）
  } // 构造方法结束

  /**
   * Creates a BlockBuilder. // 创建一个 BlockBuilder，支持嵌套作用域
   * 
   * @param optimizing Whether to eliminate common sub-expressions // 是否消除公共子表达式
   * @param parent Parent BlockBuilder, for nested blocks // 父级 BlockBuilder，用于支持嵌套代码块
   * // parent 参数允许子代码块访问父代码块中声明的变量
   * // 用于实现变量的作用域链：子代码块可以引用父代码块的变量
   * // 但父代码块不能访问子代码块的变量
   * 
   * 使用场景：
   * - 生成嵌套循环、条件语句等需要嵌套作用域的代码
   * - 在 lambda 表达式或匿名类中访问外部变量
   * 
   * 示例：
   * BlockBuilder parent = new BlockBuilder();
   * BlockBuilder child = new BlockBuilder(true, parent);
   */ 
  public BlockBuilder(boolean optimizing, @Nullable BlockBuilder parent) { // 双参数构造方法
    this(optimizing, parent, true); // 委托给三参数构造方法，removeUnused 传入 true
  } // 构造方法结束

  /**
   * Clears this BlockBuilder. // 清空此 BlockBuilder
   * // 移除所有已添加的语句、变量声明和可重用表达式映射
   * // 重置构建器到初始状态，可以重新开始构建新的代码块
   * 
   * 使用场景：
   * - 需要复用同一个 BlockBuilder 实例构建多个不同的代码块
   * - 在回溯或重试构建代码时
   */ 
  public void clear() { // 清空方法
    statements.clear(); // 清空语句列表
    variables.clear(); // 清空变量名集合
    expressionForReuse.clear(); // 清空可重用表达式映射
  } // 方法结束

  /**
   * Appends a block to a list of statements and returns an expression // 将代码块追加到语句列表，并返回一个表达式
   * (possibly a variable) that represents the result of the newly added // （可能是变量）表示新添加代码块的结果
   * block. // 该方法用于将一个完整的代码块合并到当前构建器中
   * 
   * @param name Suggested variable name for the result // 结果的建议变量名
   * @param block The BlockStatement to append // 要追加的代码块
   * @return Expression representing the result of the block // 表示代码块结果的表达式
   * // 返回值通常是代码块最后一个语句的结果表达式
   * // 如果代码块以声明语句结束，返回声明的变量
   * // 如果代码块以 return 语句结束，返回返回的表达式
   * 
   * 使用场景：
   * - 将子代码块合并到父代码块
   * - 在生成复杂代码时，分步构建不同部分的代码块
   * 
   * 示例：
   * BlockStatement subBlock = ...;
   * Expression result = builder.append("temp", subBlock);
   */ 
  public Expression append(String name, BlockStatement block) { // 追加代码块方法
    return append(name, block, true); // 委托给三参数版本，optimize 传入 true
  } // 方法结束

  /**
   * Appends an expression to a list of statements, optionally optimizing it // 将表达式追加到语句列表，可选择优化
   * to a variable if it is used more than once. // 如果表达式被多次使用，则优化为变量
   * 
   * @param name Suggested variable name // 建议的变量名
   * @param block Expression // 表达式（实际上是代码块）
   * @param optimize Whether to try to optimize by assigning the expression to // 是否通过将表达式赋值给变量来优化
   * a variable. Do not do this if the expression has // 如果表达式有副作用或时间依赖性，不要这样做
   * side effects or a time-dependent value. // optimize=true 时，会尝试重用相同的表达式
   * // optimize=false 时，每个表达式都会独立计算
   * 
   * @return Expression representing the result // 表示结果的表达式
   * 
   * 处理逻辑：
   * 1. 检查最后一个语句是否是 GotoStatement（如 return），如果是则转换为普通语句
   * 2. 遍历代码块中的每个语句
   * 3. 如果是声明语句，检查变量名是否冲突，冲突则重命名
   * 4. 处理变量替换映射，确保变量引用正确
   * 5. 返回代码块的最终结果表达式
   * 
   * 使用场景：
   * - 合并代码块并处理变量名冲突
   * - 在生成嵌套代码时，确保变量作用域正确
   */ 
  public Expression append(String name, BlockStatement block, // 追加代码块方法（带优化参数）
      boolean optimize) { // optimize 参数
    if (!statements.isEmpty()) { // 如果当前语句列表不为空
      Statement lastStatement = statements.get(statements.size() - 1); // 获取最后一个语句
      if (lastStatement instanceof GotoStatement) { // 如果最后一个语句是 GotoStatement（如 return）
        // convert "return expr;" into "expr;" // 将 "return expr;" 转换为 "expr;"
        statements.set(statements.size() - 1, // 替换最后一个语句
            Expressions.statement(((GotoStatement) lastStatement).expression)); // 将 return 语句转换为普通表达式语句
      } // if 结束
    } // if 结束
    Expression result = null; // 初始化结果表达式为 null
    final IdentityHashMap<ParameterExpression, Expression> replacements = // 创建变量替换映射
        new IdentityHashMap<>(); // 使用 IdentityHashMap，确保使用对象恒等比较
    final Shuttle shuttle = new SubstituteVariableVisitor(replacements); // 创建变量替换访问器
    for (int i = 0; i < block.statements.size(); i++) { // 遍历代码块中的每个语句
      Statement statement = block.statements.get(i); // 获取当前语句
      if (!replacements.isEmpty()) { // 如果存在需要替换的变量
        // Save effort, and only substitute variables if there are some. // 只在有需要替换的变量时才进行替换，节省开销
        statement = statement.accept(shuttle); // 使用访问器替换变量引用
      } // if 结束
      if (statement instanceof DeclarationStatement) { // 如果当前语句是声明语句
        DeclarationStatement declaration = (DeclarationStatement) statement; // 强制类型转换
        if (!variables.contains(declaration.parameter.name)) { // 如果变量名不存在冲突
          add(statement); // 直接添加该声明语句
        } else { // 如果变量名已存在（冲突）
          String newName = newName(declaration.parameter.name, optimize); // 生成新的唯一变量名
          Expression x; // 声明结果表达式变量
          // When initializer is null, append(name, initializer) can't deduce expression type // 当初始化器为 null 时，无法推断表达式类型
          if (declaration.initializer != null && isSafeForReuse(declaration)) { // 如果初始化器不为 null 且可以安全重用
            x = append(newName, declaration.initializer); // 递归调用 append 处理初始化表达式
          } else { // 如果初始化器为 null 或不能安全重用
            ParameterExpression pe = // 创建新的参数表达式
                Expressions.parameter(declaration.parameter.type, newName); // 使用新名称和原类型
            DeclarationStatement newDeclaration = // 创建新的声明语句
                Expressions.declare(declaration.modifiers, pe, declaration.initializer); // 使用新的参数表达式
            x = pe; // 结果表达式为新参数表达式
            add(newDeclaration); // 添加新的声明语句
          } // else 结束
          statement = null; // 将当前语句设为 null（已处理）
          result = x; // 设置结果表达式
          if (declaration.parameter != x) { // 如果原参数表达式与新表达式不同
            // declaration.parameter can be equal to x if exactly the same // 如果声明完全相同，declaration.parameter 可能等于 x
            // declaration was present in BlockBuilder // 这发生在 BlockBuilder 中已存在完全相同的声明时
            replacements.put(declaration.parameter, x); // 添加变量替换映射
          } // if 结束
        } // else 结束
      } else { // 如果当前语句不是声明语句
        add(statement); // 直接添加该语句
      } // else 结束
      if (i == block.statements.size() - 1) { // 如果是最后一个语句
        if (statement instanceof DeclarationStatement) { // 如果是声明语句
          result = ((DeclarationStatement) statement).parameter; // 结果为声明的参数表达式
        } else if (statement instanceof GotoStatement) { // 如果是 GotoStatement（如 return）
          statements.remove(statements.size() - 1); // 移除最后一个语句（return）
          final GotoStatement gotoStatement = (GotoStatement) statement; // 强制类型转换
          result = // 设置结果表达式
              append_(name, // 递归调用 append_ 处理返回表达式
                  requireNonNull(gotoStatement.expression, "expression"), // 确保 expression 不为 null
                  optimize); // 传入 optimize 参数
          if (isSimpleExpression(result)) { // 如果结果是简单表达式
            // already simple; no need to declare a variable or // 已经是简单表达式，不需要声明变量
            // even to evaluate the expression // 甚至不需要计算表达式
          } // if 结束
          else { // 如果结果不是简单表达式
            DeclarationStatement declare = // 创建声明语句
                Expressions.declare(Modifier.FINAL, newName(name, optimize), result); // 使用 final 修饰符
            add(declare); // 添加声明语句
            result = declare.parameter; // 结果为声明的参数表达式
          } // else 结束
        } else { // 如果是其他类型的语句
          // not an expression -- result remains null // 不是表达式 -- 结果保持 null
        } // else 结束
      } // if 结束
    } // for 循环结束
    return requireNonNull(result, () -> "empty result when appending name=" + name + ", " + block); // 返回结果，确保不为 null
  } // 方法结束

  /**
   * Appends an expression to a list of statements, and returns an expression // 将表达式追加到语句列表，返回表达式
   * (possibly a variable) that represents the result of the newly added // （可能是变量）表示新添加代码块的结果
   * block. // 该方法用于追加单个表达式到代码块中
   * 
   * @param name Suggested variable name // 建议的变量名
   * @param expression The expression to append // 要追加的表达式
   * @return Expression representing the result // 表示结果的表达式
   * // 如果表达式是简单的（如常量、参数），直接返回表达式本身
   * // 如果表达式是复杂的，会创建一个 final 变量存储结果，返回变量引用
   * 
   * 使用场景：
   * - 向代码块中添加单个表达式
   * - 自动处理表达式的优化和变量声明
   * 
   * 示例：
   * Expression result = builder.append("sum", Expressions.add(a, b));
   * // 可能生成：final int sum = a + b;
   */ 
  public Expression append(String name, Expression expression) { // 追加表达式方法
    return append(name, expression, true); // 委托给三参数版本，optimize 传入 true
  } // 方法结束

  /**
   * Appends an expression to a list of statements if it is not null, // 如果表达式不为 null，则追加到语句列表
   * and returns the expression. // 并返回该表达式
   * 
   * @param name Suggested variable name // 建议的变量名
   * @param expression The expression to append, may be null // 要追加的表达式，可能为 null
   * @return The expression if not null, otherwise null // 如果表达式不为 null 则返回表达式，否则返回 null
   * 
   * 使用场景：
   * - 条件性地添加表达式（仅在表达式存在时添加）
   * - 处理可能为 null 的可选表达式
   * 
   * 示例：
   * Expression optionalExpr = ...; // 可能为 null
   * Expression result = builder.appendIfNotNull("temp", optionalExpr);
   */ 
  public @PolyNull Expression appendIfNotNull(String name, @PolyNull Expression expression) { // 条件追加方法
    if (expression == null) { // 如果表达式为 null
      return null; // 直接返回 null
    } // if 结束
    return append(name, expression, true); // 否则调用 append 方法追加表达式
  } // 方法结束

  /**
   * Appends an expression to a list of statements, optionally optimizing if // 将表达式追加到语句列表，可选择优化
   * the expression is used more than once. // 如果表达式被多次使用，则进行优化
   * 
   * @param name Suggested variable name // 建议的变量名
   * @param expression The expression to append // 要追加的表达式
   * @param optimize Whether to try to optimize by assigning the expression to // 是否通过将表达式赋值给变量来优化
   * a variable // optimize=true 时，会检查表达式是否已计算过，如果是则重用
   * // optimize=false 时，总是创建新变量
   * 
   * @return Expression representing the result // 表示结果的表达式
   * 
   * 处理逻辑：
   * 1. 检查最后一个语句是否是 GotoStatement，如果是则转换
   * 2. 调用 append_ 方法处理表达式
   * 3. append_ 会根据 optimize 参数决定是否重用已计算的表达式
   * 
   * 使用场景：
   * - 控制表达式的优化行为
   * - 对于有副作用的表达式，使用 optimize=false
   * 
   * 示例：
   * // 对于有副作用的表达式，禁用优化
   * builder.append("count", Expressions.call(collection, "size"), false);
   */ 
  public Expression append(String name, Expression expression, // 追加表达式方法（带优化参数）
      boolean optimize) { // optimize 参数
    if (!statements.isEmpty()) { // 如果当前语句列表不为空
      Statement lastStatement = statements.get(statements.size() - 1); // 获取最后一个语句
      if (lastStatement instanceof GotoStatement) { // 如果最后一个语句是 GotoStatement
        // convert "return expr;" into "expr;" // 将 "return expr;" 转换为 "expr;"
        statements.set(statements.size() - 1, // 替换最后一个语句
            Expressions.statement(((GotoStatement) lastStatement).expression)); // 转换为普通语句
      } // if 结束
    } // if 结束
    return append_(name, expression, optimize); // 委托给私有方法 append_ 处理
  } // 方法结束

  private Expression append_(String name, Expression expression, // 私有方法：追加表达式核心逻辑
      boolean optimize) { // optimize 参数
    if (isSimpleExpression(expression)) { // 如果表达式是简单表达式（常量、参数等）
      // already simple; no need to declare a variable or // 已经是简单表达式，不需要声明变量
      // even to evaluate the expression // 甚至不需要计算表达式
      return expression; // 直接返回表达式本身
    } // if 结束
    if (optimizing && optimize) { // 如果启用了优化且允许优化
      DeclarationStatement decl = getComputedExpression(expression); // 检查表达式是否已计算过
      if (decl != null) { // 如果找到了已计算的声明
        return decl.parameter; // 返回已声明的参数表达式（重用）
      } // if 结束
    } // if 结束
    DeclarationStatement declare = // 创建新的声明语句
        Expressions.declare(Modifier.FINAL, newName(name, optimize), expression); // 使用 final 修饰符和新名称
    add(declare); // 添加声明语句到语句列表
    return declare.parameter; // 返回声明的参数表达式
  } // 方法结束

  /**
   * Checks if expression is simple enough to always inline at zero cost. // 检查表达式是否足够简单，可以零成本内联
   * // 简单表达式是指不需要计算就能直接使用的表达式
   * // 内联简单表达式可以减少变量声明，提高代码可读性
   * 
   * @param expr expression to test // 要测试的表达式
   * @return true when given expression is safe to always inline // 当给定表达式可以安全内联时返回 true
   * 
   * 简单表达式的定义：
   * 1. ParameterExpression：参数表达式，直接使用参数引用
   * 2. ConstantExpression：常量表达式，直接使用常量值
   * 3. 类型转换表达式（UnaryExpression with Convert）：如果其操作数也是简单表达式
   * 
   * 非简单表达式的例子：
   * - 方法调用：Expressions.call(obj, "method")
   * - 二元运算：Expressions.add(a, b)
   * - 对象创建：Expressions.new_(Constructor)
   * 
   * 使用场景：
   * - 优化时决定是否需要为表达式创建变量
   * - 内联简单表达式，减少不必要的变量声明
   */ 
  protected boolean isSimpleExpression(@Nullable Expression expr) { // 判断表达式是否简单
    if (expr instanceof ParameterExpression // 如果表达式是参数表达式
        || expr instanceof ConstantExpression) { // 或者是常量表达式
      return true; // 返回 true（简单表达式）
    } // if 结束
    if (expr instanceof UnaryExpression) { // 如果表达式是一元表达式
      UnaryExpression una = (UnaryExpression) expr; // 强制类型转换
      return una.getNodeType() == ExpressionType.Convert // 如果是类型转换操作
          && isSimpleExpression(una.expression); // 且操作数也是简单表达式
    } // if 结束
    return false; // 否则返回 false（不是简单表达式）
  } // 方法结束

  /**
   * Checks if a declaration statement is safe for reuse (optimization). // 检查声明语句是否可以安全重用（优化）
   * // 只有满足特定条件的声明才能被重用，避免因重用导致语义错误
   * 
   * @param decl The declaration statement to check // 要检查的声明语句
   * @return true if the declaration is safe for reuse // 如果声明可以安全重用则返回 true
   * 
   * 安全重用的条件：
   * 1. 声明必须是 final 的（不可重新赋值）
   * 2. 变量名不能以 "_" 开头（"_" 前缀是防止优化的标记）
   * 
   * 为什么需要这些条件：
   * - final 确保：变量的值不会改变，可以安全地多次引用
   * - 不以 "_" 开头：允许开发者显式标记变量不应被优化
   * 
   * 使用场景：
   * - 在添加表达式到可重用映射之前检查
   * - 决定是否可以将表达式替换为已计算的变量
   * 
   * 示例：
   * // 可以安全重用
   * final int count = list.size(); // isSafeForReuse 返回 true
   * 
   * // 不能安全重用
   * int count = list.size(); // 不是 final，返回 false
   * final int _count = list.size(); // 名字以 "_" 开头，返回 false
   */ 
  protected boolean isSafeForReuse(DeclarationStatement decl) { // 判断声明是否可以安全重用
    return (decl.modifiers & Modifier.FINAL) != 0 // 检查是否是 final 修饰符
        && !decl.parameter.name.startsWith("_"); // 且变量名不以 "_" 开头
  } // 方法结束

  /**
   * Adds a declaration to the expression reuse map if it is safe for reuse. // 如果声明可以安全重用，则将其添加到表达式重用映射
   * // 该方法实现了公共子表达式消除（CSE）的核心逻辑
   * 
   * @param decl The declaration statement to add // 要添加的声明语句
   * 
   * 处理逻辑：
   * 1. 首先检查声明是否可以安全重用（调用 isSafeForReuse）
   * 2. 如果可以，则标准化声明表达式（调用 normalizeDeclaration）
   * 3. 将标准化后的表达式作为 key，声明语句作为 value 存入映射
   * 
   * 为什么需要标准化：
   * - 相同的表达式可能有不同的类型表示
   * - 例如：int x = 1; 和 Integer y = 1; 的初始化表达式类型不同
   * - 标准化后可以正确识别它们是相同的表达式
   * 
   * 使用场景：
   * - 在添加声明语句时自动注册到重用映射
   * - 实现表达式去重和重用优化
   * 
   * 示例：
   * // 第一次遇到
   * final int sum = a + b; // 添加到 expressionForReuse
   * 
   * // 第二次遇到相同表达式
   * final int total = a + b; // 可以重用 sum 而不是重新计算
   */ 
  protected void addExpressionForReuse(DeclarationStatement decl) { // 添加表达式到重用映射
    if (isSafeForReuse(decl)) { // 如果声明可以安全重用
      Expression expr = normalizeDeclaration(decl); // 标准化声明表达式
      expressionForReuse.put(expr, decl); // 将标准化表达式和声明存入映射
    } // if 结束
  } // 方法结束

  /**
   * Checks if a declaration is costly (expensive to compute). // 检查声明是否昂贵（计算成本高）
   * // 昂贵的声明通常不应该被内联，而是应该保留为变量
   * 
   * @param decl The declaration statement to check // 要检查的声明语句
   * @return true if the declaration is costly // 如果声明昂贵则返回 true
   * 
   * 昂贵声明的定义：
   * - 初始化器是 NewExpression（对象创建表达式）
   * 
   * 为什么对象创建是昂贵的：
   * - 对象创建涉及内存分配、构造函数调用等操作
   * - 重复创建相同的对象会浪费资源
   * - 应该创建一次后多次引用，而不是每次都创建新对象
   * 
   * 使用场景：
   * - 在优化时决定是否应该内联变量
   * - 对于昂贵的声明，保留为变量而不是内联
   * 
   * 示例：
   * // 昂贵的声明
   * final MyFunction func = new MyFunction(); // isCostly 返回 true
   * 
   * // 不昂贵的声明
   * final int sum = a + b; // isCostly 返回 false
   */ 
  private static boolean isCostly(DeclarationStatement decl) { // 判断声明是否昂贵
    return decl.initializer instanceof NewExpression; // 检查初始化器是否是对象创建表达式
  } // 方法结束

  /**
   * Prepares declaration for inlining, adds cast if necessary. // 准备声明以进行内联，必要时添加类型转换
   * // 标准化声明表达式，使其可以正确比较和重用
   * 
   * @param decl inlining candidate // 内联候选声明
   * @return normalized expression // 标准化后的表达式
   * 
   * 标准化处理：
   * 1. 如果初始化器为 null，则转换为对应类型的 null 常量表达式
   * 2. 如果初始化器类型与声明类型不同，则添加类型转换
   * 
   * 为什么需要标准化：
   * - 确保相同语义的表达式可以正确匹配
   * - 处理类型转换和 null 值的特殊情况
   * - 使表达式可以正确地用于比较和映射
   * 
   * 示例：
   * // 原始声明
   * final Integer x = 1; // 初始化器类型是 int，声明类型是 Integer
   * 
   * // 标准化后
   * (Integer) 1 // 添加了类型转换
   * 
   * // 另一个例子
   * final String s = null; // 初始化器为 null
   * 
   * // 标准化后
   * (String) null // 转换为类型化的 null 表达式
   */ 
  private static Expression normalizeDeclaration(DeclarationStatement decl) { // 标准化声明表达式
    Expression expr = decl.initializer; // 获取初始化表达式
    Type declType = decl.parameter.getType(); // 获取声明类型
    if (expr == null) { // 如果初始化表达式为 null
      expr = Expressions.constant(null, declType); // 创建类型化的 null 常量表达式
    } else if (expr.getType() != declType) { // 如果表达式类型与声明类型不同
      expr = Expressions.convert_(expr, declType); // 添加类型转换表达式
    } // if 结束
    return expr; // 返回标准化后的表达式
  } // 方法结束

  /**
   * Returns the reference to ParameterExpression if given expression was // 如果给定表达式已经计算并存储到局部变量中，则返回参数表达式引用
   * already computed and stored to local variable. // 该方法实现了表达式重用的查找逻辑
   * 
   * @param expr expression to test // 要测试的表达式
   * @return existing ParameterExpression or null // 返回已存在的参数表达式或 null
   * 
   * 查找逻辑：
   * 1. 首先在父级 BlockBuilder 中查找（支持嵌套作用域）
   * 2. 如果父级中找到，直接返回
   * 3. 否则在当前 BlockBuilder 的 expressionForReuse 映射中查找
   * 4. 如果找到，返回对应的声明语句的参数表达式
   * 5. 如果未找到，返回 null
   * 
   * 为什么支持父级查找：
   * - 子代码块可以重用父代码块中已计算的表达式
   * - 实现了跨作用域的表达式重用
   * 
   * 使用场景：
   * - 在追加表达式时检查是否可以重用已计算的变量
   * - 实现公共子表达式消除优化
   * 
   * 示例：
   * // 父代码块
   * final int sum = a + b; // 计算并存储
   * 
   * // 子代码块
   * final int total = sum + c; // 可以重用 sum
   * getComputedExpression(a + b) // 返回 sum 的参数表达式
   */ 
  public @Nullable DeclarationStatement getComputedExpression(Expression expr) { // 获取已计算的表达式
    if (parent != null) { // 如果存在父级 BlockBuilder
      DeclarationStatement decl = parent.getComputedExpression(expr); // 在父级中查找
      if (decl != null) { // 如果父级中找到
        return decl; // 返回父级中的声明
      } // if 结束
    } // if 结束
    return optimizing ? expressionForReuse.get(expr) : null; // 在当前映射中查找（如果启用优化）
  } // 方法结束

  /**
   * Adds a statement to the block. // 向代码块添加一个语句
   * // 这是向代码块添加语句的基本方法，所有其他添加方法最终都会调用此方法
   * 
   * @param statement The statement to add // 要添加的语句
   * 
   * 处理逻辑：
   * 1. 将语句添加到 statements 列表
   * 2. 如果是声明语句，检查变量名是否重复
   * 3. 如果变量名重复，抛出 AssertionError
   * 4. 如果是声明语句且可以安全重用，则添加到 expressionForReuse 映射
   * 
   * 为什么检查变量名重复：
   * - 确保生成的代码中不会有重复的变量声明
   * - 避免编译错误
   * 
   * 使用场景：
   * - 添加任意类型的语句到代码块
   * - 确保变量名唯一性和表达式重用
   * 
   * 示例：
   * builder.add(Expressions.declare(0, param, init));
   * builder.add(Expressions.return_(null, result));
   */ 
  public void add(Statement statement) { // 添加语句方法
    statements.add(statement); // 将语句添加到语句列表
    if (statement instanceof DeclarationStatement) { // 如果是声明语句
      DeclarationStatement decl = (DeclarationStatement) statement; // 强制类型转换
      String name = decl.parameter.name; // 获取变量名
      if (!variables.add(name)) { // 尝试添加变量名到集合（如果已存在则返回 false）
        throw new AssertionError("duplicate variable " + name); // 抛出断言错误，变量名重复
      } // if 结束
      addExpressionForReuse(decl); // 如果可以安全重用，添加到重用映射
    } // if 结束
  } // 方法结束

  /**
   * Adds an expression as a return statement. // 将表达式作为 return 语句添加
   * // 便捷方法，用于添加返回语句
   * 
   * @param expression The expression to return // 要返回的表达式
   * 
   * 处理逻辑：
   * 1. 创建一个 return 语句，包含给定的表达式
   * 2. 调用 add 方法将 return 语句添加到代码块
   * 
   * 使用场景：
   * - 在代码块末尾添加返回语句
   * - 生成带有返回值的方法体
   * 
   * 示例：
   * builder.add(Expressions.constant(42));
   * // 生成：return 42;
   */ 
  public void add(Expression expression) { // 添加表达式作为 return 语句
    add(Expressions.return_(null, expression)); // 创建 return 语句并添加
  } // 方法结束

  /**
   * Returns a block consisting of the current list of statements. // 返回由当前语句列表组成的代码块
   * // 这是构建代码块的最后一步，会执行所有优化并返回最终的 BlockStatement
   * 
   * @return BlockStatement containing all the statements // 包含所有语句的 BlockStatement
   * 
   * 处理逻辑：
   * 1. 如果启用了优化和移除未使用变量：
   *    a. 最多执行 10 次优化循环（防止无限循环）
   *    b. 每次循环调用 optimize 方法进行优化
   *    c. 如果没有优化发生，则跳出循环
   *    d. 最后调用完成优化（createFinishingOptimizeShuttle）
   * 2. 创建并返回 BlockStatement，包含所有优化后的语句
   * 
   * 为什么限制优化循环次数：
   * - 优化过程可能需要多次迭代才能完成
   * - 但理论上不应该无限循环
   * - 设置 10 次作为安全限制，防止意外情况
   * 
   * 优化包括：
   * - 变量内联：将只使用一次的变量内联到使用处
   * - 死代码消除：移除未使用的变量声明
   * - 表达式简化：简化复杂的表达式
   * 
   * 使用场景：
   * - 完成代码块构建，生成最终代码
   * - 执行所有优化，生成高质量代码
   * 
   * 示例：
   * BlockStatement block = builder.toBlock();
   * // block 包含所有优化后的语句，可以直接用于代码生成
   */ 
  public BlockStatement toBlock() { // 生成代码块方法
    if (optimizing && removeUnused) { // 如果启用了优化和移除未使用变量
      // We put an artificial limit of 10 iterations just to prevent an endless // 我们设置 10 次迭代的人为限制，以防止无限循环
      // loop. Optimize should not loop forever, however it is hard to prove if // 优化不应该永远循环，但很难证明它
      // it always finishes in reasonable time. // 总是在合理时间内完成
      for (int i = 0; i < 10; i++) { // 最多执行 10 次优化循环
        if (!optimize(createOptimizeShuttle(), true)) { // 调用 optimize 方法，如果返回 false（没有优化）
          break; // 则跳出循环
        } // if 结束
      } // for 循环结束
      optimize(createFinishingOptimizeShuttle(), false); // 执行完成优化（不执行内联）
    } // if 结束
    return Expressions.block(statements); // 创建并返回 BlockStatement
  } // 方法结束

  /**
   * Optimizes the list of statements. If an expression is used only once, // 优化语句列表。如果表达式只使用一次，则内联它
   * it is inlined. // 该方法实现了核心的优化逻辑
   * 
   * @param optimizer The shuttle to use for optimization // 用于优化的访问器
   * @param performInline Whether to perform variable inlining // 是否执行变量内联
   * @return whether any optimizations were made // 是否进行了任何优化
   * 
   * 优化过程：
   * 1. 创建使用计数器（UseCounter），统计每个变量的使用次数
   * 2. 遍历所有语句，统计变量使用次数
   * 3. 创建变量替换映射（subMap），用于内联变量
   * 4. 创建内联访问器（InlineVariableVisitor）
   * 5. 遍历所有语句，根据使用次数决定处理方式：
   *    a. 使用次数为 0：移除声明（死代码消除）
   *    b. 使用次数为 1：内联变量（变量内联）
   *    c. 使用次数 > 1：保留声明，但可能优化表达式
   * 6. 应用优化器访问器进行表达式优化
   * 7. 返回是否进行了优化
   * 
   * 内联规则：
   * - 简单表达式（常量、参数）总是内联
   * - 非 final 变量不内联（可能被重新赋值）
   * - 昂贵表达式（对象创建）不内联
   * - 名字以 "_" 开头的变量不内联
   * - 匿名内部类不内联
   * 
   * 使用场景：
   * - 在 toBlock 方法中调用，执行代码优化
   * - 生成更高效、更简洁的代码
   * 
   * 示例：
   * // 优化前
   * final int x = a + b;
   * final int y = x + c;
   * return y;
   * 
   * // 优化后（x 只使用一次，被内联）
   * final int y = a + b + c;
   * return y;
   */ 
  private boolean optimize(Shuttle optimizer, boolean performInline) { // 优化方法
    int optimizeCount = 0; // 优化计数器，记录优化次数
    final UseCounter useCounter = new UseCounter(); // 创建使用计数器
    for (Statement statement : statements) { // 遍历所有语句
      if (statement instanceof DeclarationStatement && performInline) { // 如果是声明语句且需要内联
        DeclarationStatement decl = (DeclarationStatement) statement; // 强制类型转换
        useCounter.map.put(decl.parameter, new Slot()); // 为该参数创建使用计数槽
      } // if 结束
      // We are added only counters up to current statement. // 我们只添加到当前语句的计数器
      // It is fine to count usages as the latter declarations cannot be used // 统计使用次数是合理的，因为后面的声明不能
      // in more recent statements. // 在更近的语句中使用
      if (!useCounter.map.isEmpty()) { // 如果存在计数器
        statement.accept(useCounter); // 使用计数器访问语句，统计使用次数
      } // if 结束
    } // for 循环结束
    final IdentityHashMap<ParameterExpression, Expression> subMap = // 创建变量替换映射
        new IdentityHashMap<>(useCounter.map.size()); // 初始容量为计数器映射的大小
    final Shuttle visitor = new InlineVariableVisitor(subMap); // 创建内联访问器
    final ArrayList<Statement> oldStatements = new ArrayList<>(statements); // 复制原始语句列表
    statements.clear(); // 清空当前语句列表

    for (Statement oldStatement : oldStatements) { // 遍历原始语句列表
      if (oldStatement instanceof DeclarationStatement) { // 如果是声明语句
        DeclarationStatement statement = (DeclarationStatement) oldStatement; // 强制类型转换
        final Slot slot = useCounter.map.get(statement.parameter); // 获取该参数的使用计数槽
        int count = slot == null ? Integer.MAX_VALUE - 10 : slot.count; // 获取使用次数，如果没有槽则设为很大
        if (count > 1 && isSimpleExpression(statement.initializer)) { // 如果使用次数 > 1 且初始化器是简单表达式
          // Inline simple final constants // 内联简单的 final 常量
          count = 1; // 将使用次数设为 1，触发内联
        } // if 结束
        if (!isSafeForReuse(statement)) { // 如果声明不安全重用
          // Don't inline variables that are not final. They might be assigned // 不要内联非 final 变量，它们可能被
          // more than once. // 多次赋值
          count = 100; // 将使用次数设为 100，阻止内联
        } // if 结束
        if (isCostly(statement)) { // 如果声明昂贵
          // Don't inline variables that are costly, such as "new MyFunction()". // 不要内联昂贵的变量，如 "new MyFunction()"
          // Later we will make their declarations static. // 稍后我们会将它们的声明设为静态
          count = 100; // 将使用次数设为 100，阻止内联
        } // if 结束
        if (statement.parameter.name.startsWith("_")) { // 如果变量名以 "_" 开头
          // Don't inline variables whose name begins with "_". This // 不要内联名字以 "_" 开头的变量，这是一种
          // is a hacky way to prevent inlining. E.g. // 防止内联的 hack 方式。例如：
          //   final int _count = collection.size(); //   final int _count = collection.size();
          //   foo(collection); //   foo(collection);
          //   return collection.size() - _count; //   return collection.size() - _count;
          count = Integer.MAX_VALUE; // 将使用次数设为最大值，阻止内联
        } // if 结束
        if (statement.initializer instanceof NewExpression // 如果初始化器是对象创建表达式
            && ((NewExpression) statement.initializer).memberDeclarations // 且包含成员声明（匿名内部类）
                != null) { // 不为 null
          // Don't inline anonymous inner classes. Janino gets // 不要内联匿名内部类，Janino 会
          // confused referencing variables from deeply nested // 在引用深层嵌套匿名类中的变量时感到困惑
          // anonymous classes. // 导致编译错误
          count = Integer.MAX_VALUE; // 将使用次数设为最大值，阻止内联
        } // if 结束
        Expression normalized = normalizeDeclaration(statement); // 标准化声明表达式
        expressionForReuse.remove(normalized); // 从重用映射中移除（因为正在重新优化）
        switch (count) { // 根据使用次数决定处理方式
        case 0: // 使用次数为 0
          // Only declared, never used. Throw away declaration. // 只声明了，从未使用。丢弃声明。
          break; // 不添加任何语句（死代码消除）
        case 1: // 使用次数为 1
          // declared, used once. inline it. // 声明了，使用一次。内联它。
          subMap.put(statement.parameter, normalized); // 将参数映射到标准化表达式（用于内联）
          break; // 不添加声明语句
        default: // 使用次数 > 1
          Statement beforeOptimize = oldStatement; // 保存优化前的语句
          if (!subMap.isEmpty()) { // 如果存在需要内联的变量
            oldStatement = oldStatement.accept(visitor); // 使用内联访问器重映射变量
          } // if 结束
          oldStatement = oldStatement.accept(optimizer); // 使用优化器访问器优化表达式
          if (beforeOptimize != oldStatement) { // 如果语句发生了变化
            ++optimizeCount; // 增加优化计数
            if (count != Integer.MAX_VALUE // 如果使用次数不是最大值
                && oldStatement instanceof DeclarationStatement // 且是声明语句
                && isSafeForReuse((DeclarationStatement) oldStatement) // 且可以安全重用
                && isSimpleExpression( // 且初始化器是简单表达式
                  ((DeclarationStatement) oldStatement).initializer)) { // 检查初始化器
              // Allow to inline the expression that became simple after // 允许内联在优化后变得简单的表达式
              // optimizations. // 例如：复杂的表达式被简化为常量
              DeclarationStatement newDecl = // 获取新的声明
                  (DeclarationStatement) oldStatement; // 强制类型转换
              subMap.put(newDecl.parameter, normalizeDeclaration(newDecl)); // 将新参数映射到标准化表达式
              oldStatement = OptimizeShuttle.EMPTY_STATEMENT; // 将语句设为空（标记为已内联）
            } // if 结束
          } // if 结束
          if (oldStatement != OptimizeShuttle.EMPTY_STATEMENT) { // 如果语句不是空语句
            if (oldStatement instanceof DeclarationStatement) { // 如果是声明语句
              addExpressionForReuse((DeclarationStatement) oldStatement); // 添加到重用映射
            } // if 结束
            statements.add(oldStatement); // 添加优化后的语句
          } // if 结束
          break; // switch 结束
        } // switch 结束
      } else { // 如果不是声明语句
        Statement beforeOptimize = oldStatement; // 保存优化前的语句
        if (!subMap.isEmpty()) { // 如果存在需要内联的变量
          oldStatement = oldStatement.accept(visitor); // 使用内联访问器重映射变量
        } // if 结束
        oldStatement = oldStatement.accept(optimizer); // 使用优化器访问器优化表达式
        if (beforeOptimize != oldStatement) { // 如果语句发生了变化
          ++optimizeCount; // 增加优化计数
        } // if 结束
        if (oldStatement != OptimizeShuttle.EMPTY_STATEMENT) { // 如果语句不是空语句
          statements.add(oldStatement); // 添加优化后的语句
        } // if 结束
      } // else 结束
    } // for 循环结束
    return optimizeCount > 0; // 返回是否进行了优化（优化计数 > 0）
  } // 方法结束

  /**
   * Creates a shuttle that will be used during block optimization. // 创建用于代码块优化的访问器
   * // 子类可以重写此方法提供更具体的优化（如部分求值）
   * 
   * @return shuttle used to optimize the statements when converting to block // 在转换为代码块时用于优化语句的访问器
   * 
   * 默认实现：
   * - 返回静态的 OPTIMIZE_SHUTTLE（OptimizeShuttle 实例）
   * - OptimizeShuttle 提供基本的表达式优化功能
   * 
   * 子类扩展：
   * - 可以重写此方法提供自定义的优化逻辑
   * - 例如：常量折叠、部分求值、特定模式优化等
   * 
   * 使用场景：
   * - 在 optimize 方法中调用，获取优化访问器
   * - 子类可以自定义优化策略
   * 
   * 示例：
   * // 默认实现
   * protected Shuttle createOptimizeShuttle() {
   *     return OPTIMIZE_SHUTTLE;
   * }
   * 
   * // 自定义实现
   * protected Shuttle createOptimizeShuttle() {
   *     return new CustomOptimizeShuttle();
   * }
   */ 
  protected Shuttle createOptimizeShuttle() { // 创建优化访问器方法
    return OPTIMIZE_SHUTTLE; // 返回静态优化访问器实例
  } // 方法结束

  /**
   * Creates a final optimization shuttle. // 创建最终的优化访问器
   * // 该访问器用于完成优化阶段，通常会提取常量表达式
   * 
   * @return shuttle that is used to finalize the optimization // 用于完成优化的访问器
   * 
   * 默认实现：
   * - 返回 ClassDeclarationFinder.create() 创建的访问器
   * - ClassDeclarationFinder 负责查找类声明并优化
   * 
   * 完成优化的作用：
   * - 提取常量表达式为静态字段
   * - 优化类声明和匿名内部类
   * - 执行最终的表达式简化
   * 
   * 使用场景：
   * - 在 toBlock 方法中最后调用
   * - 执行最终的优化和清理
   * 
   * 示例：
   * // 在 toBlock 方法中
   * optimize(createFinishingOptimizeShuttle(), false);
   */ 
  protected Shuttle createFinishingOptimizeShuttle() { // 创建完成优化访问器方法
    return ClassDeclarationFinder.create(); // 返回类声明查找器
  } // 方法结束

  /**
   * Creates a name for a new variable, unique within this block, controlling // 为新变量创建唯一的名称，控制
   * whether the variable can be inlined later. // 变量是否可以被后续内联
   * 
   * @param suggestion Suggested variable name // 建议的变量名
   * @param optimize Whether the variable can be optimized (inlined) // 变量是否可以被优化（内联）
   * @return unique variable name // 唯一的变量名
   * 
   * 命名规则：
   * 1. 如果 optimize=false 且建议名称不以 "_" 开头：
   *    - 在建议名称前添加 "_" 前缀
   *    - "_" 前缀表示该变量不应被内联
   * 2. 调用 newName(suggestion) 生成唯一名称
   * 
   * 为什么使用 "_" 前缀：
   * - 防止不应内联的变量被意外内联
   * - 例如：有副作用的表达式、时间依赖的表达式
   * - 提供一种简单的方式来控制优化行为
   * 
   * 使用场景：
   * - 在创建新变量时确保名称唯一
   * - 控制变量的优化行为
   * 
   * 示例：
   * // 可以优化的变量
   * newName("sum", true) → "sum" 或 "sum1"（如果 sum 已存在）
   * 
   * // 不能优化的变量
   * newName("sum", false) → "_sum" 或 "_sum1"（如果 _sum 已存在）
   * 
   * // 已经以 "_" 开头的变量
   * newName("_temp", false) → "_temp" 或 "_temp1"（不添加额外前缀）
   */ 
  private String newName(String suggestion, boolean optimize) { // 生成新变量名方法（带优化控制）
    if (!optimize && !suggestion.startsWith("_")) { // 如果不能优化且建议名称不以 "_" 开头
      // "_" prefix reminds us not to consider the variable for inlining // "_" 前缀提醒我们不要考虑内联该变量
      suggestion = '_' + suggestion; // 在建议名称前添加 "_"
    } // if 结束
    return newName(suggestion); // 委托给单参数版本生成唯一名称
  } // 方法结束

  /**
   * Creates a name for a new variable, unique within this block. // 为新变量创建唯一的名称
   * // 该方法确保生成的变量名在当前代码块作用域内是唯一的
   * 
   * @param suggestion Suggested variable name // 建议的变量名
   * @return unique variable name // 唯一的变量名
   * 
   * 命名算法：
   * 1. 从建议名称开始
   * 2. 检查名称是否已存在（调用 hasVariable）
   * 3. 如果已存在，在名称后添加数字后缀（0, 1, 2, ...）
   * 4. 递增数字直到找到不存在的名称
   * 5. 返回唯一名称
   * 
   * 为什么检查父级：
   * - 确保变量名在整个作用域链中都是唯一的
   * - 避免与父代码块中的变量名冲突
   * 
   * 使用场景：
   * - 在创建新变量时确保名称唯一
   * - 处理变量名冲突
   * 
   * 示例：
   * // 没有冲突
   * newName("sum") → "sum"
   * 
   * // 有冲突
   * newName("sum") → "sum0"（如果 sum 已存在）
   * newName("sum") → "sum1"（如果 sum 和 sum0 都已存在）
   * 
   * // 建议名称以数字结尾
   * newName("temp2") → "temp2"（如果 temp2 不存在）
   * newName("temp2") → "temp20"（如果 temp2 已存在）
   */ 
  public String newName(String suggestion) { // 生成新变量名方法
    int i = 0; // 初始化数字后缀为 0
    String candidate = suggestion; // 候选名称为建议名称
    while (hasVariable(candidate)) { // 当候选名称已存在时
      candidate = suggestion + i++; // 在建议名称后添加数字后缀，并递增 i
    } // while 循环结束
    return candidate; // 返回唯一的候选名称
  } // 方法结束

  /**
   * Checks if a variable with the given name exists in this block or any // 检查给定名称的变量是否存在于当前代码块或任何
   * parent block. // 父代码块中
   * 
   * @param name Variable name to check // 要检查的变量名
   * @return true if the variable exists // 如果变量存在则返回 true
   * 
   * 检查逻辑：
   * 1. 首先检查当前代码块的 variables 集合
   * 2. 如果存在父级 BlockBuilder，递归检查父级
   * 3. 只要任一级存在，就返回 true
   * 
   * 为什么需要检查父级：
   * - 实现作用域链查找
   * - 确保变量名在整个嵌套作用域中都是唯一的
   * - 避免遮蔽（shadowing）父级变量
   * 
   * 使用场景：
   * - 在创建新变量前检查名称是否已存在
   * - 生成唯一的变量名
   * 
   * 示例：
   * // 父代码块
   * final int sum = 0;
   * 
   * // 子代码块
   * hasVariable("sum") → true（父级中存在）
   * hasVariable("count") → false（不存在）
   */ 
  public boolean hasVariable(String name) { // 检查变量是否存在方法
    return variables.contains(name) // 检查当前代码块中是否存在
        || (parent != null && parent.hasVariable(name)); // 或者在父级中存在（递归检查）
  } // 方法结束

  /**
   * Appends an expression as a return statement and returns this builder. // 将表达式作为 return 语句添加，并返回此构建器
   * // 便捷方法，支持链式调用
   * 
   * @param expression The expression to append // 要追加的表达式
   * @return this BlockBuilder // 返回当前 BlockBuilder，支持链式调用
   * 
   * 处理逻辑：
   * 1. 调用 add(expression) 将表达式作为 return 语句添加
   * 2. 返回 this，支持链式调用
   * 
   * 使用场景：
   * - 链式调用，添加多个语句
   * - 生成多个 return 语句（虽然不太常见）
   * 
   * 示例：
   * builder.append(Expressions.constant(1))
   *        .append(Expressions.constant(2))
   *        .append(Expressions.return_(null, result));
   */ 
  public BlockBuilder append(Expression expression) { // 链式追加方法
    add(expression); // 调用 add 方法添加表达式
    return this; // 返回 this，支持链式调用
  } // 方法结束

  /**
   * Creates a new BlockBuilder with the same settings but different // 创建一个新的 BlockBuilder，具有相同的设置但不同的
   * removeUnused flag. // removeUnused 标志
   * 
   * @param removeUnused Whether to remove unused variables // 是否移除未使用的变量
   * @return new BlockBuilder with the specified removeUnused setting // 返回具有指定 removeUnused 设置的新 BlockBuilder
   * 
   * 使用场景：
   * - 在某些情况下临时禁用或启用移除未使用变量的功能
   * - 创建具有不同优化设置的构建器变体
   * 
   * 示例：
   * BlockBuilder builder = new BlockBuilder();
   * BlockBuilder noRemove = builder.withRemoveUnused(false);
   * // noRemove 具有相同的优化设置，但不移除未使用的变量
   */ 
  public BlockBuilder withRemoveUnused(boolean removeUnused) { // 创建具有不同 removeUnused 设置的构建器
    return new BlockBuilder(optimizing, parent, removeUnused); // 创建新的 BlockBuilder，使用相同的其他设置
  } // 方法结束

  /** Substitute Variable Visitor. // 变量替换访问器
   * // 该访问器用于遍历表达式树，并将参数表达式替换为指定的表达式
   * // 用于实现变量内联和变量重命名
   * 
   * 工作原理：
   * - 维护一个映射表（map），记录要替换的参数表达式和目标表达式
   * - 遍历表达式树时，遇到参数表达式就查找映射表
   * - 如果找到映射，则用目标表达式替换参数表达式
   * - 递归处理目标表达式，确保嵌套替换正确
   * - 检测循环引用，防止无限递归
   * 
   * 使用场景：
   * - 变量内联：将只使用一次的变量替换为其实际值
   * - 变量重命名：在变量名冲突时，将旧变量名替换为新变量名
   * - 表达式重用：将重复的表达式替换为已计算的变量
   * 
   * 示例：
   * // 原始表达式
   * int result = x + y;
   * // 其中 x = a + b, y = c + d
   * 
   * // 替换映射
   * x → a + b
   * y → c + d
   * 
   * // 替换后
   * int result = (a + b) + (c + d);
   */ 
  private static class SubstituteVariableVisitor extends Shuttle { // 变量替换访问器类
    protected final Map<ParameterExpression, Expression> map; // 参数表达式到目标表达式的映射
    private final IdentityHashMap<ParameterExpression, Boolean> actives = // 活跃参数集合，用于检测循环引用
        new IdentityHashMap<>(); // 使用 IdentityHashMap，确保使用对象恒等比较

    SubstituteVariableVisitor(Map<ParameterExpression, Expression> map) { // 构造方法
      this.map = map; // 保存映射表
    } // 构造方法结束

    @Override public Expression visit(ParameterExpression parameterExpression) { // 访问参数表达式
      Expression e = map.get(parameterExpression); // 在映射表中查找参数表达式
      if (e != null) { // 如果找到映射
        try { // 尝试处理
          final Boolean put = actives.put(parameterExpression, true); // 将参数标记为活跃
          if (put != null) { // 如果参数已经在活跃集合中
            throw new AssertionError( // 抛出断言错误
                "recursive expansion of " + parameterExpression + " in " // 递归展开参数
                + actives.keySet()); // 在活跃集合中
          } // if 结束
          // recursively substitute // 递归替换
          return e.accept(this); // 递归访问目标表达式，继续替换
        } finally { // 无论是否抛出异常
          actives.remove(parameterExpression); // 将参数从活跃集合中移除
        } // finally 结束
      } // if 结束
      return super.visit(parameterExpression); // 如果没有映射，调用父类方法
    } // 方法结束
  } // 类结束

  /** Inline Variable Visitor. // 内联变量访问器
   * // 该访问器扩展了 SubstituteVariableVisitor，专门用于变量内联
   * // 它考虑了左值修改的情况，避免错误地内联被修改的变量
   * 
   * 特殊处理：
   * - 对于修改左值的一元表达式（如 ++, --），避免替换操作数
   * - 对于修改左值的二元表达式（如赋值），避免替换左操作数
   * - 这些处理确保内联不会改变程序的语义
   * 
   * 为什么需要特殊处理：
   * - 如果内联被修改的变量，会导致语义错误
   * - 例如：int t = 1; t++; 如果内联 t，会变成 1++; 这是错误的
   * - 正确的做法是保留变量声明，不进行内联
   * 
   * 使用场景：
   * - 在优化过程中内联变量
   * - 确保内联不会改变程序语义
   * 
   * 示例：
   * // 原始代码
   * int t = 1;
   * int v = t++;
   * 
   * // 错误的内联
   * int v = 1++; // 编译错误
   * 
   * // 正确的处理（不内联）
   * int t = 1;
   * int v = t++;
   */ 
  private static class InlineVariableVisitor extends SubstituteVariableVisitor { // 内联变量访问器类
    InlineVariableVisitor( // 构造方法
        Map<ParameterExpression, Expression> map) { // 接收映射表参数
      super(map); // 调用父类构造方法
    } // 构造方法结束

    @Override public Expression visit(UnaryExpression unaryExpression, // 访问一元表达式
        Expression expression) { // 表达式参数
      if (unaryExpression.getNodeType().modifiesLvalue) { // 如果一元表达式修改左值（如 ++, --）
        expression = unaryExpression.expression; // 避免替换，直接使用原始表达式
        if (expression instanceof ParameterExpression) { // 如果操作数是参数表达式
          // avoid "optimization of" int t=1; t++; to 1++ // 避免将 "int t=1; t++;" 优化为 "1++"
          return unaryExpression; // 直接返回原始表达式，不进行替换
        } // if 结束
      } // if 结束
      return super.visit(unaryExpression, expression); // 否则调用父类方法
    } // 方法结束

    @Override public Expression visit(BinaryExpression binaryExpression, // 访问二元表达式
        Expression expression0, Expression expression1) { // 两个操作数参数
      if (binaryExpression.getNodeType().modifiesLvalue) { // 如果二元表达式修改左值（如赋值）
        expression0 = binaryExpression.expression0; // 避免替换左操作数
        if (expression0 instanceof ParameterExpression) { // 如果左操作数是参数表达式
          // If t is a declaration used only once, replace // 如果 t 是只使用一次的声明，替换
          //   int t; //   int t;
          //   int v = (t = 1) != a ? c : d; //   int v = (t = 1) != a ? c : d;
          // with // 为
          //   int v = 1 != a ? c : d; //   int v = 1 != a ? c : d;
          if (map.containsKey(expression0)) { // 如果左操作数在映射表中（应该被内联）
            return expression1.accept(this); // 只返回右操作数（跳过赋值）
          } // if 结束
        } // if 结束
      } // if 结束
      return super.visit(binaryExpression, expression0, expression1); // 否则调用父类方法
    } // 方法结束
  } // 类结束

  /** Use counter. // 使用计数器
   * // 该访问器用于统计每个参数表达式的使用次数
   * // 用于优化时决定是否应该内联变量
   * 
   * 工作原理：
   * - 维护一个映射表（map），记录每个参数表达式及其使用计数槽
   * - 遍历表达式树时，遇到参数表达式就增加其使用次数
   * - 遇到声明语句时，只访问初始化器，不访问参数本身
   * - 这样可以准确统计变量的实际使用次数
   * 
   * 为什么不访问声明语句的参数：
   * - 声明语句中的参数只是声明，不是使用
   * - 如果访问它，会导致使用次数被错误地增加
   * - 例如：final int x = 1; 这里的 x 不是使用，而是声明
   * 
   * 使用场景：
   * - 在优化前统计变量使用次数
   * - 决定哪些变量应该被内联（使用次数为 1）
   * - 决定哪些变量应该被移除（使用次数为 0）
   * 
   * 示例：
   * // 代码
   * final int x = a + b;
   * final int y = x + c;
   * return y;
   * 
   * // 使用次数
   * x: 1（在 y 的初始化中使用）
   * y: 1（在 return 中使用）
   * 
   * // 优化后
   * final int y = a + b + c; // x 被内联
   * return y;
   */ 
  private static class UseCounter extends VisitorImpl<Void> { // 使用计数器类
    private final IdentityHashMap<ParameterExpression, Slot> map = // 参数表达式到使用计数槽的映射
        new IdentityHashMap<>(); // 使用 IdentityHashMap，确保使用对象恒等比较

    @Override public Void visit(ParameterExpression parameter) { // 访问参数表达式
      final Slot slot = map.get(parameter); // 获取参数的使用计数槽
      if (slot != null) { // 如果槽存在（参数被注册）
        // Count use of parameter, if it's registered. It's OK if // 统计参数的使用次数，如果已注册。如果参数
        // parameter is not registered. It might be beyond the control // 未注册也没关系。它可能超出了此代码块
        // of this block. // 的控制范围
        slot.count++; // 增加使用次数
      } // if 结束
      return super.visit(parameter); // 调用父类方法
    } // 方法结束

    @Override public Void visit(DeclarationStatement declarationStatement) { // 访问声明语句
      // Unlike base class, do not visit declarationStatement.parameter. // 与基类不同，不访问声明语句的参数
      if (declarationStatement.initializer != null) { // 如果初始化器不为 null
        declarationStatement.initializer.accept(this); // 只访问初始化器
      } // if 结束
      return null; // 返回 null
    } // 方法结束
  } // 类结束

  /**
   * Holds the number of times a declaration was used. // 保存声明被使用的次数
   * // 这是一个简单的计数器类，用于跟踪变量的使用次数
   * 
   * 使用场景：
   * - 在 UseCounter 中为每个参数表达式创建一个 Slot
   * - 每次参数被使用时，增加 count 的值
   * - 优化时根据 count 的值决定处理方式：
   *   - count = 0：移除声明（死代码消除）
   *   - count = 1：内联变量（变量内联）
   *   - count > 1：保留声明（多次使用）
   * 
   * 示例：
   * Slot slot = new Slot();
   * slot.count++; // count = 1
   * slot.count++; // count = 2
   */ 
  private static class Slot { // 使用计数槽类
    private int count; // 使用次数计数器
  } // 类结束
} // 类结束
