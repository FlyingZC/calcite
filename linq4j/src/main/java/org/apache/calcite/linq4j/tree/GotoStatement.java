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
// 声明包名，表示当前类属于 org.apache.calcite.linq4j.tree 包，这是 Calcite LINQ4J 表达式树包的一部分，用于表示表达式语法树的节点
package org.apache.calcite.linq4j.tree;
// 导入 Nullable 注解，用于标记可能为 null 的参数或返回值，来自 CheckerFramework 框架，用于静态空值检查
import org.checkerframework.checker.nullness.qual.Nullable;
// 导入 Objects 工具类，用于对象的比较和哈希码计算，提供 null 安全的方法
import java.util.Objects;
// 静态导入 Preconditions 的 checkArgument 方法，用于参数校验，如果条件不满足则抛出 IllegalArgumentException 异常
import static com.google.common.base.Preconditions.checkArgument;
// 静态导入 Objects 的 requireNonNull 方法，用于确保对象不为 null，如果为 null 则抛出 NullPointerException 异常
import static java.util.Objects.requireNonNull;

/**
 * Represents an unconditional jump. This includes return statements, break and
 * continue statements, and other jumps.
 * 表示无条件跳转语句。这个类用于表示各种控制流跳转操作，包括 return 语句（返回值）、break 语句（跳出循环）、continue 语句（继续下一次循环）以及其他类型的跳转。
 * 它是 LINQ4J 表达式树中用于表示控制流转移的核心类，通过 GotoExpressionKind 枚举来区分不同类型的跳转操作。
 * 这个类继承自 Statement 基类，是表达式树中的一种语句节点，用于在代码生成过程中生成相应的跳转指令。
 */
public class GotoStatement extends Statement {
  // 跳转类型字段，使用 final 修饰表示不可变，存储当前跳转语句的类型，通过 GotoExpressionKind 枚举定义
  // 可能的值包括：Break（跳出循环）、Continue（继续循环）、Goto（无条件跳转到标签）、Return（返回值）、Sequence（序列语句）
  public final GotoExpressionKind kind;
  // 标签目标字段，使用 @Nullable 注解表示可能为 null，用于存储跳转的目标标签
  // 对于 Goto 类型的跳转，这个字段必须非 null，指向要跳转到的 LabelTarget；对于 Break 和 Continue，这个字段可能指向循环标签；对于 Return 和 Sequence，这个字段必须为 null
  public final @Nullable LabelTarget labelTarget;
  // 表达式字段，使用 @Nullable 注解表示可能为 null，用于存储跳转时携带的表达式值
  // 对于 Return 类型，这个字段存储要返回的表达式值；对于 Sequence 类型，这个字段存储要执行的表达式；对于 Break、Continue 和 Goto，这个字段必须为 null
  public final @Nullable Expression expression;

  // 私有构造方法，用于创建 GotoStatement 实例，接受跳转类型、标签目标和表达式三个参数
  // 参数 kind：跳转类型枚举，指定当前跳转语句的具体类型
  // 参数 labelTarget：可选的标签目标，用于指定跳转的目标位置
  // 参数 expression：可选的表达式，用于存储跳转时需要携带的值或要执行的表达式
  GotoStatement(GotoExpressionKind kind, @Nullable LabelTarget labelTarget,
      @Nullable Expression expression) {
    // 调用父类 Statement 的构造方法，传入表达式类型为 Goto，并确定返回类型
    // 如果表达式为 null，则返回类型为 Void.TYPE（表示无返回值）；否则返回表达式的类型
    super(ExpressionType.Goto,
        expression == null ? Void.TYPE : expression.getType());
    // 使用 requireNonNull 确保 kind 参数不为 null，如果为 null 则抛出 NullPointerException，并提示错误信息 "kind"
    this.kind = requireNonNull(kind, "kind");
    // 将 labelTarget 参数赋值给实例变量，可能为 null
    this.labelTarget = labelTarget;
    // 将 expression 参数赋值给实例变量，可能为 null
    this.expression = expression;

    // 根据 kind 的不同类型，进行参数有效性验证，确保参数组合符合语义要求
    switch (kind) {
    // 对于 Break 类型（跳出循环语句）
    case Break:
    // 对于 Continue 类型（继续循环语句）
    case Continue:
      // 检查 expression 必须为 null，因为 break 和 continue 语句不携带表达式值，如果 expression 不为 null 则抛出 IllegalArgumentException
      checkArgument(expression == null, "for %s, expression must be null",
          kind);
      // 跳出 switch 语句
      break;
    // 对于 Goto 类型（无条件跳转到标签）
    case Goto:
      // 断言 expression 为 null，goto 语句不携带表达式值，这是一个开发时断言，在断言启用时会检查
      assert expression == null;
      // 使用 requireNonNull 确保 labelTarget 不为 null，goto 语句必须指定跳转目标，如果为 null 则抛出 NullPointerException
      requireNonNull(labelTarget, "labelTarget");
      // 跳出 switch 语句
      break;
    // 对于 Return 类型（返回语句）
    case Return:
    // 对于 Sequence 类型（序列语句）
    case Sequence:
      // 检查 labelTarget 必须为 null，因为 return 和 sequence 语句不需要标签目标，如果 labelTarget 不为 null 则抛出 IllegalArgumentException
      checkArgument(labelTarget == null, "for %s, labelTarget must be null",
          kind);
      // 跳出 switch 语句
      break;
    // 默认情况，处理未知的跳转类型
    default:
      // 抛出运行时异常，提示遇到了意外的跳转类型
      throw new RuntimeException("unexpected: " + kind);
    }
  }

  // 重写父类的 accept 方法，接受一个 Shuttle 访问器对象，用于遍历和转换表达式树
  // Shuttle 是一个表达式树的访问器模式实现，可以遍历和修改表达式树的节点
  // 参数 shuttle：表达式树访问器，用于处理当前节点及其子节点
  // 返回值：返回处理后的 Statement 对象，可能是修改后的新对象
  @Override public Statement accept(Shuttle shuttle) {
    // 首先调用 shuttle 的 preVisit 方法，在访问当前节点之前进行预处理，可能返回修改后的 shuttle 对象
    shuttle = shuttle.preVisit(this);
    // 如果当前节点的 expression 不为 null，则调用 expression 的 accept 方法，使用 shuttle 访问器处理表达式
    // 如果 expression 为 null，则保持 null 不变
    // expression1 是处理后的表达式，可能被 shuttle 修改或替换
    Expression expression1 =
        expression == null ? null : expression.accept(shuttle);
    // 调用 shuttle 的 visit 方法，传入当前 GotoStatement 对象和处理后的表达式 expression1
    // shuttle 可以根据需要创建新的 GotoStatement 对象或返回修改后的对象
    return shuttle.visit(this, expression1);
  }

  // 重写父类的 accept 方法，接受一个泛型 Visitor 访问器对象，用于遍历表达式树并返回指定类型的结果
  // 这是访问者模式的实现，允许外部访问者对表达式树进行各种操作而不修改节点类本身
  // 参数 visitor：泛型访问器，类型参数 R 表示访问方法的返回类型
  // 返回值：返回访问器处理后的结果，类型由 R 泛型参数决定
  @Override public <R> R accept(Visitor<R> visitor) {
    // 调用 visitor 的 visit 方法，传入当前 GotoStatement 对象
    // visitor 根据 GotoStatement 的类型调用相应的处理逻辑，并返回处理结果
    return visitor.visit(this);
  }

  // 重写父类的 accept0 方法，用于将当前 GotoStatement 节点写入表达式输出器
  // 这个方法负责将 GotoStatement 转换为实际的代码字符串，输出到 ExpressionWriter 中
  // 参数 writer：表达式输出器，用于生成代码字符串
  @Override void accept0(ExpressionWriter writer) {
    // 首先将跳转类型的前缀写入 writer，例如 "return"、"break"、"continue"、"goto" 等
    writer.append(kind.prefix);
    // 如果 labelTarget 不为 null，说明需要跳转到指定的标签
    if (labelTarget != null) {
      // 在前缀后添加一个空格，然后将标签目标的名称写入 writer
      writer.append(' ').append(labelTarget.name);
    }
    // 如果 expression 不为 null，说明需要输出表达式内容（用于 Return 或 Sequence 类型）
    if (expression != null) {
      // 如果跳转类型的前缀不为空（即不是 Sequence 类型），则在前缀和表达式之间添加一个空格
      if (!kind.prefix.isEmpty()) {
        writer.append(' ');
      }
      // 根据 kind 的不同类型，采用不同的表达式输出策略
      switch (kind) {
      // 对于 Sequence 类型（序列语句）
      case Sequence:
        // 不进行缩进处理，直接将表达式输出到 writer，传入缩进参数 0,0 表示不添加额外缩进
        // Sequence 类型通常用于表示连续的语句序列，不需要额外的格式化
        expression.accept(writer, 0, 0);
        // 跳出 switch 语句
        break;
      // 默认情况（包括 Return 等其他类型）
      default:
        // 调用 writer 的 begin 方法，开始一个新的表达式块，可能用于添加缩进或括号
        writer.begin();
        // 将表达式输出到 writer，同样传入缩进参数 0,0
        expression.accept(writer, 0, 0);
        // 调用 writer 的 end 方法，结束表达式块，可能用于关闭括号或恢复缩进
        writer.end();
      }
    }
    // 在语句末尾添加分号，然后换行并添加适当的缩进，完成当前语句的输出
    writer.append(';').newlineAndIndent();
  }

  // 重写父类的 evaluate 方法，用于在给定的求值器中评估当前 GotoStatement 的值
  // 这个方法主要用于解释执行表达式树，计算语句的结果
  // 参数 evaluator：表达式求值器，用于计算表达式的值和管理执行上下文
  // 返回值：返回语句执行的结果，对于 Return 类型返回表达式的值，对于其他类型可能返回 null 或抛出异常
  @Override public @Nullable Object evaluate(Evaluator evaluator) {
    // 根据 kind 的不同类型，执行不同的求值逻辑
    switch (kind) {
    // 对于 Return 类型（返回语句）
    case Return:
    // 对于 Sequence 类型（序列语句）
    case Sequence:
      // 注意：这里忽略了控制流的影响。这种处理方式只有在 return 语句是代码块中的最后一条语句时才是正确的
      // 使用 requireNonNull 确保 expression 不为 null，然后调用 expression 的 evaluate 方法在 evaluator 中计算表达式的值
      // 计算结果作为当前语句的返回值返回
      return requireNonNull(expression, "expression").evaluate(evaluator);
    // 默认情况（包括 Break、Continue、Goto 等类型）
    default:
      // 抛出断言错误，表示这些类型的跳转语句的 evaluate 方法尚未实现
      // 这是因为 Break、Continue、Goto 等控制流语句在解释执行时需要特殊的控制流处理，不能简单地返回一个值
      throw new AssertionError("evaluate not implemented");
    }
  }

  // 重写 Object 类的 equals 方法，用于比较两个 GotoStatement 对象是否相等
  // 参数 o：要比较的对象，可能为 null
  // 返回值：如果两个对象相等返回 true，否则返回 false
  @Override public boolean equals(@Nullable Object o) {
    // 首先检查是否是同一个对象的引用，如果是则直接返回 true
    if (this == o) {
      return true;
    }
    // 检查 o 是否为 null，或者 o 的类型是否与当前对象的类型不同，如果是则返回 false
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    // 调用父类 Statement 的 equals 方法，比较父类的属性是否相等，如果不相等则返回 false
    if (!super.equals(o)) {
      return false;
    }

    // 将 o 强制转换为 GotoStatement 类型
    GotoStatement that = (GotoStatement) o;
    // 使用 Objects.equals 方法比较 expression 字段是否相等（null 安全）
    // 使用 == 比较 kind 字段是否相等（枚举类型可以直接用 == 比较）
    // 使用 Objects.equals 方法比较 labelTarget 字段是否相等（null 安全）
    // 只有当所有字段都相等时才返回 true
    return Objects.equals(expression, that.expression)
        && kind == that.kind
        && Objects.equals(labelTarget, that.labelTarget);
  }

  // 重写 Object 类的 hashCode 方法，用于计算 GotoStatement 对象的哈希码
  // 返回值：返回对象的哈希码值，基于所有关键字段计算
  @Override public int hashCode() {
    // 使用 Objects.hash 方法计算哈希码，传入所有关键字段
    // 包括：nodeType（节点类型，继承自父类）、type（返回类型，继承自父类）、kind（跳转类型）、labelTarget（标签目标）、expression（表达式）
    // 这样确保相等的对象具有相同的哈希码，满足 equals 和 hashCode 的一致性约定
    return Objects.hash(nodeType, type, kind, labelTarget, expression);
  }
}
