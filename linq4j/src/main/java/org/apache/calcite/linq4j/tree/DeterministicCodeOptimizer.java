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
package org.apache.calcite.linq4j.tree; // 声明包名，该类属于 org.apache.calcite.linq4j.tree 包

import org.apache.calcite.linq4j.function.Deterministic; // 导入 Deterministic 注解，用于标记确定性的方法
import org.apache.calcite.linq4j.function.NonDeterministic; // 导入 NonDeterministic 注解，用于标记非确定性的方法

import com.google.common.collect.ImmutableSet; // 导入 Google Guava 的不可变集合类，用于存储不可变集合

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空性注解，用于标记可能为 null 的值

import java.lang.reflect.Constructor; // 导入 Constructor 类，用于反射获取构造函数
import java.lang.reflect.Method; // 导入 Method 类，用于反射获取方法
import java.lang.reflect.Modifier; // 导入 Modifier 类，用于检查方法的修饰符
import java.math.BigDecimal; // 导入 BigDecimal 类，用于高精度十进制运算
import java.math.BigInteger; // 导入 BigInteger 类，用于大整数运算
import java.util.HashMap; // 导入 HashMap 类，用于存储键值对映射
import java.util.IdentityHashMap; // 导入 IdentityHashMap 类，用于使用对象身份作为键的映射
import java.util.List; // 导入 List 接口，用于存储列表
import java.util.Map; // 导入 Map 接口，用于存储映射
import java.util.Set; // 导入 Set 接口，用于存储集合
import java.util.regex.Pattern; // 导入 Pattern 类，用于正则表达式匹配

/**
 * Factors out deterministic expressions to final static fields. // 将确定性表达式提取为 final static 字段
 * Instances of this class should not be reused, so new visitor should be // 该类的实例不应被重用，因此应该为优化新的表达式树创建新的访问器
 * created for optimizing a new expression tree. // 创建新的访问器来优化新的表达式树
 * 
 * 这个类是 Calcite LINQ4J 框架中的代码优化器，专门用于优化 Java 表达式树。
 * 它的主要功能是识别表达式树中的确定性表达式（即在相同输入下总是产生相同输出的表达式），
 * 并将这些表达式提取为类的静态 final 字段，从而避免重复计算，提高代码执行效率。
 * 
 * 例如：表达式 "new BigInteger(\"42\")" 是确定性的，每次执行都会创建相同的值，
 * 优化器会将其提取为一个静态 final 字段，这样整个类中所有使用该表达式的地方都会引用同一个字段。
 * 
 * 该类继承自 ClassDeclarationFinder，是一个访问者模式的实现，通过遍历表达式树来识别和优化表达式。
 */ 
public class DeterministicCodeOptimizer extends ClassDeclarationFinder { // 定义 DeterministicCodeOptimizer 类，继承自 ClassDeclarationFinder
  /**
   * The map contains known to be effectively-final expression. // 该映射包含已知为有效 final 的表达式
   * The map uses identity equality. // 该映射使用对象身份相等性进行比较
   * Typically the key is {@code ParameterExpression}, however there might be // 通常键是 ParameterExpression，但也可能有
   * non-factored to final field expression that is known to be constant. // 未提取到 final 字段但已知为常量的表达式
   * For instance, cast expression will not be factored to a field, // 例如，类型转换表达式不会被提取为字段，
   * but we still need to track its constant status. // 但我们仍然需要跟踪其常量状态
   * 
   * 这个成员变量用于跟踪所有已知为常量的表达式。
   * 使用 IdentityHashMap 而不是普通的 HashMap，是因为我们需要使用对象身份（==）而不是 equals() 来比较表达式对象。
   * 这是必要的，因为表达式对象可能没有正确实现 equals() 方法，或者我们需要精确跟踪特定的表达式实例。
   * 
   * 常量表达式包括：
   * 1. 字面量常量（如数字、字符串）
   * 2. final static 字段
   * 3. 确定性方法调用的结果
   * 4. 由常量表达式计算得到的表达式
   */ 
  protected final IdentityHashMap<Expression, Boolean> constants = new IdentityHashMap<>(); // 使用 IdentityHashMap 存储常量表达式，键为表达式，值为布尔值表示是否为常量

  /**
   * The map that de-duplicates expressions, so the same expressions may reuse // 用于去重表达式的映射，使相同的表达式可以重用
   * the same final static fields. // 同一个 final static 字段
   * 
   * 这个成员变量用于实现表达式的去重优化。
   * 当优化器在表达式树中发现相同的表达式时，它会确保只创建一个静态 final 字段来存储该表达式的值，
   * 然后在所有使用该表达式的地方都引用这个字段，从而避免重复计算和存储。
   * 
   * 映射的键是原始表达式，值是对应的 ParameterExpression（代表静态 final 字段）。
   * 这样当优化器遇到相同的表达式时，可以直接查找并重用已创建的字段。
   * 
   * 去重示例：
   * 如果代码中多次出现 "new BigInteger(\"42\")"，优化器会创建一个静态 final 字段，
   * 然后将所有出现的地方都替换为对该字段的引用。
   */ 
  protected final Map<Expression, ParameterExpression> dedup = new HashMap<>(); // 使用 HashMap 存储表达式到参数表达式的映射，用于去重

  /**
   * The map of all the added final static fields. Allows to identify if the // 所有添加的 final static 字段的映射，用于识别
   * name is occupied or not. // 名称是否已被占用
   * 
   * 这个成员变量用于跟踪所有已创建的静态 final 字段的名称。
   * 它的主要作用是避免字段名冲突，确保每个生成的字段名都是唯一的。
   * 
   * 当优化器需要为表达式创建新的静态 final 字段时，它会检查这个映射，
   * 确保生成的字段名不会与已有的字段名冲突。
   * 
   * 映射的键是字段名（字符串），值是对应的 ParameterExpression。
   * 这个映射还会在父类优化器中查找，以支持嵌套类的情况。
   */ 
  protected final Map<String, ParameterExpression> fieldsByName = // 定义字段名到参数表达式的映射
      new HashMap<>(); // 创建 HashMap 实例

  // Pre-compiled patterns for generation names for the final static fields // 用于生成 final static 字段名称的预编译正则表达式模式
  private static final Pattern NON_ASCII = Pattern.compile("[^0-9a-zA-Z$]+"); // 匹配所有非字母数字和美元符号的字符，用于替换为下划线
  private static final String FIELD_PREFIX = "$L4J$C$"; // 静态字段的前缀，用于避免与用户定义的变量名冲突，L4J 代表 LINQ4J
  private static final Pattern PREFIX_PATTERN = // 匹配字段前缀的模式
      Pattern.compile(Pattern.quote(FIELD_PREFIX)); // 编译前缀模式，用于从表达式文本中移除已有的前缀

  /**
   * 这个集合包含了所有被认为是确定性的 Java 类。
   * 这些类的所有方法都被认为是确定性的，即在相同输入下总是产生相同输出。
   * 
   * 确定性类包括：
   * - 基本类型的包装类（Byte, Boolean, Short, Integer, Long）：这些类是不可变的
   * - 大数类（BigInteger, BigDecimal）：这些类也是不可变的，所有操作都返回新对象
   * - String：字符串是不可变的
   * - Math：数学工具类的所有静态方法都是确定性的
   * 
   * 当优化器遇到调用这些类的方法时，如果所有参数都是常量，就可以将方法调用结果提取为静态 final 字段。
   */ 
  private static final Set<Class> DETERMINISTIC_CLASSES = // 定义确定性类的集合
      ImmutableSet.of(Byte.class, Boolean.class, Short.class, // 包含基本类型包装类
          Integer.class, Long.class, BigInteger.class, BigDecimal.class, // 包含大数类
          String.class, Math.class); // 包含字符串和数学工具类

  /**
   * Creates a child optimizer. // 创建一个子优化器
   * Typically a child is created for each class declaration, // 通常为每个类声明创建一个子优化器，
   * so each optimizer collects fields for exactly one class. // 这样每个优化器只为一个类收集字段
   *
   * 这个构造方法用于创建嵌套类的优化器。
   * 当优化器遇到内部类或匿名类时，会创建一个新的优化器实例来处理该类。
   * 子优化器会继承父优化器的常量映射和字段映射，从而可以访问父类中定义的常量。
   * 
   * 这种设计模式允许优化器递归地处理嵌套的类结构，每个类都有自己的字段集合，
   * 但可以访问外部类的常量。
   *
   * @param parent parent optimizer // 父优化器，用于继承常量和字段映射
   */
  public DeterministicCodeOptimizer(ClassDeclarationFinder parent) { // 构造方法，接收父优化器作为参数
    super(parent); // 调用父类构造方法，初始化父优化器引用
  }

  /**
   * Optimizes {@code new Type()} constructs. // 优化 new Type() 构造表达式
   *
   * 这个方法用于优化对象创建表达式（new 操作符）。
   * 如果满足以下条件，它会将对象创建表达式提取为静态 final 字段：
   * 1. 创建的类型是一个 Class 对象（不是泛型类型）
   * 2. 构造函数的所有参数都是常量
   * 3. 构造函数是确定性的（创建的对象是不可变的）
   * 
   * 优化示例：
   * new BigInteger("42") -> 提取为静态 final 字段 $L4J$C$BigInteger_42
   * new Integer(100) -> 提取为静态 final 字段 $L4J$C$Integer_100
   * 
   * 这种优化对于不可变对象特别有效，因为这些对象创建后状态不会改变，
   * 可以安全地在整个类中重用同一个实例。
   *
   * @param newExpression expression to optimize // 要优化的 new 表达式
   * @return optimized expression // 优化后的表达式，可能是原始表达式，也可能是对静态字段的引用
   */
  @Override protected Expression // 重写父类方法，返回优化后的表达式
  tryOptimizeNewInstance(NewExpression newExpression) { // 方法签名，接收 NewExpression 参数
    if (newExpression.type instanceof Class // 检查类型是否为 Class 对象
        && isConstant(newExpression.arguments) // 检查构造函数参数是否都是常量
        && isConstructorDeterministic(newExpression)) { // 检查构造函数是否是确定性的
      // Reuse instance creation when class is immutable: new BigInteger(3) // 当类不可变时重用实例创建：如 new BigInteger(3)
      return createField(newExpression); // 创建静态 final 字段来存储实例，返回字段引用
    }
    return newExpression; // 如果不满足优化条件，返回原始表达式
  }

  @Override public Expression visit(BinaryExpression binaryExpression, // 重写 visit 方法处理二元表达式
      Expression expression0, Expression expression1) { // 接收二元表达式和两个操作数
    Expression result = super.visit(binaryExpression, expression0, expression1); // 先调用父类方法递归处理子表达式
    if (binaryExpression.getNodeType().modifiesLvalue) { // 检查表达式类型是否会修改左值（如赋值操作）
      return result; // 如果会修改左值，直接返回结果，不进行优化
    }

    if (isConstant(expression0) && isConstant(expression1)) { // 检查两个操作数是否都是常量
      return createField(result); // 如果都是常量，将二元表达式结果提取为静态 final 字段
    }
    return result; // 如果不满足常量条件，返回处理后的表达式
  }

  @Override public Expression visit(TernaryExpression ternaryExpression, // 重写 visit 方法处理三元表达式（条件表达式）
      Expression expression0, Expression expression1, Expression expression2) { // 接收三元表达式和三个子表达式（条件、真值、假值）
    Expression result = // 声明结果表达式变量
        super.visit(ternaryExpression, expression0, expression1, expression2); // 调用父类方法递归处理所有子表达式

    if (isConstant(expression0) // 检查条件表达式是否为常量
        && isConstant(expression1) // 检查真值表达式是否为常量
        && isConstant(expression2)) { // 检查假值表达式是否为常量
      return createField(result); // 如果三个子表达式都是常量，将三元表达式结果提取为静态 final 字段
    }
    return result; // 如果不满足常量条件，返回处理后的表达式
  }

  @Override public Expression visit(UnaryExpression unaryExpression, // 重写 visit 方法处理一元表达式
      Expression expression) { // 接收一元表达式和操作数
    Expression result = super.visit(unaryExpression, expression); // 调用父类方法递归处理操作数

    if (isConstant(expression)) { // 检查操作数是否为常量
      constants.put(result, true); // 将结果标记为常量，即使不提取为字段也要标记
      if (result.getNodeType() != ExpressionType.Convert) { // 检查结果类型是否不是类型转换
        return createField(result); // 如果不是类型转换，将一元表达式结果提取为静态 final 字段
      }
    }
    return result; // 如果不满足常量条件或是类型转换，返回处理后的表达式
  }

  @Override public Expression visit(TypeBinaryExpression typeBinaryExpression, // 重写 visit 方法处理类型二元表达式（如 instanceof）
      Expression expression) { // 接收类型二元表达式和操作数
    Expression result = super.visit(typeBinaryExpression, expression); // 调用父类方法递归处理操作数

    if (isConstant(expression)) { // 检查操作数是否为常量
      constants.put(result, true); // 将结果标记为常量，但不提取为字段（类型检查通常不值得提取）
    }
    return result; // 返回处理后的表达式
  }

  /**
   * Optimized method call, possibly converting it to final static field. // 优化方法调用，可能将其转换为 final static 字段
   *
   * 这个方法用于优化方法调用表达式。
   * 如果满足以下条件，它会将方法调用表达式提取为静态 final 字段：
   * 1. 方法调用的目标对象是常量（对于实例方法）
   * 2. 方法的所有参数都是常量
   * 3. 方法本身是确定性的（相同输入总是产生相同输出）
   * 
   * 优化示例：
   * Math.abs(-5) -> 提取为静态 final 字段 $L4J$C$Math_abs_5
   * "hello".length() -> 如果 "hello" 是常量，提取为静态 final 字段
   * BigInteger.valueOf(42) -> 提取为静态 final 字段
   * 
   * 这种优化对于计算密集型或耗时操作特别有效，可以避免重复计算。
   * 
   * 注意：只有标记为 @Deterministic 注解的方法，或者属于确定性类的方法，
   * 才会被优化。非确定性方法（如 System.currentTimeMillis()）不会被优化。
   *
   * @param methodCallExpression method call to optimize // 要优化的方法调用表达式
   * @return optimized expression // 优化后的表达式，可能是原始表达式，也可能是对静态字段的引用
   */
  protected Expression tryOptimizeMethodCall(MethodCallExpression // 方法签名，返回优化后的表达式
      methodCallExpression) { // 接收方法调用表达式作为参数
    if (isConstant(methodCallExpression.targetExpression) // 检查目标对象是否为常量（实例方法）
        && isConstant(methodCallExpression.expressions) // 检查方法参数是否都是常量
        && isMethodDeterministic(methodCallExpression.method)) { // 检查方法是否是确定性的
      return createField(methodCallExpression); // 如果满足所有条件，将方法调用结果提取为静态 final 字段
    }
    return methodCallExpression; // 如果不满足优化条件，返回原始方法调用表达式
  }

  @Override public Expression visit(MethodCallExpression methodCallExpression, // 重写 visit 方法处理方法调用表达式
      @Nullable Expression targetExpression, List<Expression> expressions) { // 接收方法调用表达式、目标对象和参数列表
    Expression result = // 声明结果表达式变量
        super.visit(methodCallExpression, targetExpression, expressions); // 调用父类方法递归处理目标对象和参数

    result = tryOptimizeMethodCall((MethodCallExpression) result); // 尝试优化方法调用表达式
    return result; // 返回优化后的表达式
  }

  @Override public Expression visit(MemberExpression memberExpression, // 重写 visit 方法处理成员表达式（字段访问）
      @Nullable Expression expression) { // 接收成员表达式和目标对象
    Expression result = super.visit(memberExpression, expression); // 调用父类方法递归处理目标对象

    if (isConstant(expression) // 检查目标对象是否为常量
        && Modifier.isFinal(memberExpression.field.getModifiers())) { // 检查访问的字段是否是 final 的
      constants.put(result, true); // 如果目标对象是常量且字段是 final 的，将结果标记为常量
    }
    return result; // 返回处理后的表达式
  }

  @Override public MemberDeclaration visit(FieldDeclaration fieldDeclaration, // 重写 visit 方法处理字段声明
      @Nullable Expression initializer) { // 接收字段声明和初始化表达式
    if (Modifier.isStatic(fieldDeclaration.modifier)) { // 检查字段是否是静态的
      // Avoid optimization of static fields, since we'll have to track order // 避免优化静态字段，因为我们需要跟踪
      // of static declarations. // 静态声明的顺序
      return fieldDeclaration; // 如果是静态字段，直接返回原始声明，不进行优化
    }
    return super.visit(fieldDeclaration, initializer); // 如果不是静态字段，调用父类方法处理
  }

  /**
   * Processes the list of declarations and learns final static ones as // 处理声明列表，并将 final static 声明学习为
   * effectively constant. // 有效常量
   *
   * 这个方法用于扫描类中已有的 final static 字段声明，并将它们标记为常量。
   * 这样优化器在后续处理时就可以将这些字段视为常量，从而可以优化使用这些字段的表达式。
   * 
   * 例如，如果类中已经定义了：
   * private static final BigInteger ZERO = new BigInteger("0");
   * 
   * 那么优化器会将 ZERO 字段标记为常量，并在后续优化中使用这个信息。
   * 
   * 该方法还会将这些字段添加到 dedup 映射中，以便其他表达式可以重用这些字段。
   *
   * @param memberDeclarations list of declarations to search finals from // 要搜索 final 声明的成员声明列表
   */
  @Override protected void learnFinalStaticDeclarations( // 重写父类方法，学习 final static 声明
      List<MemberDeclaration> memberDeclarations) { // 接收成员声明列表作为参数
    for (MemberDeclaration decl : memberDeclarations) { // 遍历所有成员声明
      if (decl instanceof FieldDeclaration) { // 检查是否为字段声明
        FieldDeclaration field = (FieldDeclaration) decl; // 强制转换为字段声明
        if (Modifier.isStatic(field.modifier) // 检查字段是否是静态的
            && Modifier.isFinal(field.modifier) // 检查字段是否是 final 的
            && field.initializer != null) { // 检查字段是否有初始化表达式
          constants.put(field.parameter, true); // 将字段参数标记为常量
          fieldsByName.put(field.parameter.name, field.parameter); // 将字段名添加到字段名映射中
          dedup.put(field.initializer, field.parameter); // 将初始化表达式与字段参数关联，用于去重
        }
      }
    }
  }

  /**
   * Finds if there exists ready for reuse declaration for given expression. // 查找是否存在可以重用的声明来存储给定表达式
   *
   * 这个方法用于检查给定的表达式是否已经被提取为静态 final 字段。
   * 如果存在，则返回对应的字段参数，以便直接重用该字段而不是创建新的字段。
   * 
   * 这是优化器实现表达式去重的关键方法。它会先在当前优化器的 dedup 映射中查找，
   * 如果找不到，还会递归地在父优化器中查找（支持嵌套类的情况）。
   * 
   * 查找流程：
   * 1. 检查当前优化器的 dedup 映射是否非空
   * 2. 在 dedup 映射中查找给定表达式
   * 3. 如果找到，返回对应的 ParameterExpression
   * 4. 如果找不到，递归在父优化器中查找
   * 5. 如果都找不到，返回 null
   *
   * @param expression input expression // 输入表达式
   * @return parameter of the already existing declaration, or null // 已存在声明的参数，如果不存在则返回 null
   */
  @Override protected @Nullable ParameterExpression findDeclaredExpression(Expression expression) { // 重写父类方法，查找已声明的表达式
    if (!dedup.isEmpty()) { // 检查去重映射是否非空
      ParameterExpression pe = dedup.get(expression); // 在去重映射中查找表达式
      if (pe != null) { // 如果找到了对应的参数表达式
        return pe; // 返回参数表达式
      }
    }
    return parent == null ? null : parent.findDeclaredExpression(expression); // 如果父优化器不为空，递归在父优化器中查找
  }

  /**
   * Creates final static field to hold the given expression. // 创建 final static 字段来存储给定表达式
   * The method might reuse existing declarations if appropriate. // 如果合适，该方法可能会重用已有的声明
   *
   * 这个方法是优化器的核心方法之一，用于将表达式提取为静态 final 字段。
   * 它会先检查是否已经存在可以重用的字段，如果不存在则创建新的字段。
   * 
   * 创建字段的步骤：
   * 1. 调用 findDeclaredExpression 检查是否已有可重用的字段
   * 2. 如果有，直接返回该字段引用
   * 3. 如果没有，生成唯一的字段名
   * 4. 创建 ParameterExpression 表示字段
   * 5. 创建 FieldDeclaration 声明静态 final 字段
   * 6. 将表达式和字段添加到去重映射中
   * 7. 将字段声明添加到新增声明列表中
   * 8. 将字段标记为常量
   * 9. 将字段名添加到字段名映射中
   * 10. 返回字段引用
   * 
   * 生成的字段声明示例：
   * private static final BigInteger $L4J$C$BigInteger_42 = new BigInteger("42");
   * 
   * 生成的字段名格式：$L4J$C$<表达式文本>，其中非字母数字字符会被替换为下划线
   *
   * @param expression expression to store in final field // 要存储在 final 字段中的表达式
   * @return expression for the given input expression // 对给定输入表达式的引用（字段引用）
   */
  protected Expression createField(Expression expression) { // 方法签名，接收要存储的表达式
    ParameterExpression pe = findDeclaredExpression(expression); // 查找是否已有可重用的声明
    if (pe != null) { // 如果找到了可重用的字段
      return pe; // 直接返回字段引用
    }

    String name = inventFieldName(expression); // 生成唯一的字段名
    pe = Expressions.parameter(expression.getType(), name); // 创建参数表达式表示字段
    FieldDeclaration decl = // 创建字段声明
        Expressions.fieldDecl(Modifier.FINAL | Modifier.STATIC, pe, expression); // 声明为 final static 字段
    dedup.put(expression, pe); // 将表达式和字段添加到去重映射中
    addedDeclarations.add(decl); // 将字段声明添加到新增声明列表中
    constants.put(pe, true); // 将字段标记为常量
    fieldsByName.put(name, pe); // 将字段名添加到字段名映射中
    return pe; // 返回字段引用
  }

  /**
   * Generates field name to store given expression. // 生成字段名来存储给定表达式
   * The expression is converted to string and all the non-ascii/numeric // 表达式被转换为字符串，所有非字母数字
   * characters are replaced with underscores and {@code "_$L4J$C$"} suffix is // 字符被替换为下划线，并添加 "_$L4J$C$" 后缀以
   * added to avoid conflicts with other variables. // 避免与其他变量冲突
   * When multiple variables are mangled to the same name, // 当多个变量被转换为相同的名称时，
   * counter is used to avoid conflicts. // 使用计数器来避免冲突
   *
   * 这个方法用于为表达式生成唯一的字段名。
   * 生成的字段名需要满足以下要求：
   * 1. 必须是合法的 Java 标识符
   * 2. 必须唯一，不与已有字段冲突
   * 3. 应该反映表达式的特征，便于调试
   * 4. 长度不能过长（限制为 70 个字符）
   * 
   * 生成字段名的步骤：
   * 1. 将表达式转换为字符串
   * 2. 移除已有的前缀（避免重复前缀）
   * 3. 添加固定前缀 "$L4J$C$"
   * 4. 将所有非字母数字字符替换为下划线
   * 5. 如果长度超过 70，截断并添加哈希码
   * 6. 检查名称是否已存在，如果存在则添加数字后缀
   * 
   * 生成示例：
   * new BigInteger("42") -> $L4J$C$new_BigInteger_42_
   * Math.abs(-5) -> $L4J$C$Math_abs__5_
   * "hello" + "world" -> $L4J$C$_hello____world_
   *
   * @param expression input expression // 输入表达式
   * @return unique name to store given expression // 存储给定表达式的唯一名称
   */
  protected String inventFieldName(Expression expression) { // 方法签名，接收表达式作为参数
    String exprText = expression.toString(); // 将表达式转换为字符串
    exprText = PREFIX_PATTERN.matcher(exprText).replaceAll(""); // 移除已有的前缀
    exprText = FIELD_PREFIX + NON_ASCII.matcher(exprText).replaceAll("_"); // 添加前缀并将非字母数字字符替换为下划线
    if (exprText.length() > 70) { // 检查字段名长度是否超过 70
      exprText = exprText.substring(0, 70) // 截取前 70 个字符
          + Integer.toHexString(exprText.hashCode()); // 添加哈希码的十六进制表示
    }
    String fieldName = exprText; // 保存基础字段名
    for (int i = 0; hasField(fieldName); i++) { // 检查字段名是否已存在，如果存在则添加数字后缀
      fieldName = exprText + i; // 添加数字后缀
    }
    return fieldName; // 返回唯一的字段名
  }

  /**
   * Verifies if the expression is effectively constant. // 验证表达式是否是有效常量
   * It is assumed the expression is simple (e.g. {@code ConstantExpression} or // 假设表达式是简单的（如 ConstantExpression 或
   * {@code ParameterExpression}). // ParameterExpression）
   * The method verifies parent chain since the expression might be defined // 该方法验证父链，因为表达式可能定义在
   * in enclosing class. // 外部类中
   *
   * 这个方法用于判断给定的表达式是否是常量。
   * 常量表达式包括：
   * 1. null 值
   * 2. 字面量常量（ConstantExpression，如数字、字符串、布尔值）
   * 3. 已标记为常量的表达式（在 constants 映射中）
   * 4. 在父优化器中标记为常量的表达式（支持嵌套类）
   * 
   * 判断逻辑：
   * 1. 如果表达式为 null，返回 true
   * 2. 如果表达式是 ConstantExpression，返回 true
   * 3. 如果 constants 映射非空且包含该表达式，返回 true
   * 4. 如果父优化器不为空且父优化器认为该表达式是常量，返回 true
   * 5. 否则返回 false
   * 
   * 这个方法是优化器的核心判断方法之一，用于决定是否可以将表达式提取为静态 final 字段。
   * 只有当表达式的所有子表达式都是常量时，该表达式才能被优化。
   *
   * @param expression expression to test // 要测试的表达式
   * @return true when the expression is known to be constant // 当表达式已知为常量时返回 true
   */
  @Override protected boolean isConstant(@Nullable Expression expression) { // 重写父类方法，判断表达式是否为常量
    return expression == null // 如果表达式为 null，返回 true
        || expression instanceof ConstantExpression // 如果表达式是常量表达式，返回 true
        || !constants.isEmpty() && constants.containsKey(expression) // 如果常量映射非空且包含该表达式，返回 true
        || parent != null && parent.isConstant(expression); // 如果父优化器不为空且父优化器认为该表达式是常量，返回 true
  }

  /**
   * Checks if given method is deterministic (i.e. returns the same output // 检查给定方法是否是确定性的（即在相同输入下
   * given the same inputs). // 返回相同输出）
   *
   * 这个方法用于判断一个方法是否是确定性的。
   * 确定性方法是指在相同输入下总是产生相同输出的方法。
   * 
   * 判断标准：
   * 1. 方法所属类的所有方法都是确定性的，且方法没有 @NonDeterministic 注解
   * 2. 或者方法有 @Deterministic 注解
   * 
   * 确定性方法的例子：
   * - Math.abs(-5) -> 总是返回 5
   * - String.valueOf(42) -> 总是返回 "42"
   * - BigInteger.valueOf(100) -> 总是返回相同的 BigInteger 对象
   * 
   * 非确定性方法的例子：
   * - System.currentTimeMillis() -> 每次调用返回不同的值
   * - Math.random() -> 每次调用返回不同的随机数
   * - new Date() -> 每次调用返回不同的日期对象
   * 
   * 只有确定性方法才能被优化器提取为静态 final 字段。
   *
   * @param method method to test // 要测试的方法
   * @return true when the method is deterministic // 当方法是确定性的时候返回 true
   */
  protected boolean isMethodDeterministic(Method method) { // 方法签名，接收方法作为参数
    return (allMethodsDeterministic(method.getDeclaringClass()) // 如果方法所属类的所有方法都是确定性的
            && !method.isAnnotationPresent(NonDeterministic.class)) // 且方法没有 @NonDeterministic 注解
           || method.isAnnotationPresent(Deterministic.class); // 或者方法有 @Deterministic 注解
  }

  /**
   * Checks if new instance creation can be reused. For instance {@code new // 检查新实例创建是否可以重用。例如 new
   * BigInteger("42")} is effectively final and can be reused. // BigInteger("42")} 是有效的 final 且可以重用
   *
   * 这个方法用于判断构造函数调用是否是确定性的。
   * 如果构造函数是确定性的，那么创建的对象可以提取为静态 final 字段并重用。
   * 
   * 判断标准：
   * 1. 构造函数所属类的所有方法都是确定性的
   * 2. 或者构造函数有 @Deterministic 注解
   * 
   * 确定性构造函数的例子：
   * - new BigInteger("42") -> 总是创建相同的 BigInteger 对象
   * - new Integer(100) -> 总是创建相同的 Integer 对象
   * - new BigDecimal("3.14") -> 总是创建相同的 BigDecimal 对象
   * 
   * 非确定性构造函数的例子：
   * - new Date() -> 每次调用创建不同的日期对象
   * - new Random() -> 每次调用创建不同的随机数生成器
   * - new ArrayList() -> 虽然初始状态相同，但后续操作会改变状态
   * 
   * 注意：只有创建不可变对象的构造函数才能被认为是确定性的。
   *
   * @param newExpression method to test // 要测试的 new 表达式
   * @return true when the method is deterministic // 当构造函数是确定性的时候返回 true
   */
  protected boolean isConstructorDeterministic(NewExpression newExpression) { // 方法签名，接收 new 表达式作为参数
    final Class klass = (Class) newExpression.type; // 获取构造的类类型
    final Constructor constructor = getConstructor(klass); // 获取无参构造函数
    return allMethodsDeterministic(klass) // 如果类的所有方法都是确定性的
        || constructor != null // 或者构造函数不为空
        && constructor.isAnnotationPresent(Deterministic.class); // 且构造函数有 @Deterministic 注解
  }

  private static <C> @Nullable Constructor<C> getConstructor(Class<C> klass) { // 获取类的无参构造函数
    try { // 尝试获取构造函数
      return klass.getConstructor(); // 返回无参构造函数
    } catch (NoSuchMethodException e) { // 如果没有无参构造函数
      return null; // 返回 null
    }
  }

  /**
   * Checks if all the methods in given class are deterministic (i.e. return // 检查给定类中的所有方法是否都是确定性的（即在相同输入下
   * the same value given the same inputs) // 返回相同值）
   *
   * 这个方法用于判断一个类的所有方法是否都是确定性的。
   * 如果一个类的所有方法都是确定性的，那么该类通常创建的对象也是不可变的。
   * 
   * 判断标准：
   * 1. 类在 DETERMINISTIC_CLASSES 集合中（预定义的确定性类）
   * 2. 或者类是 org.apache.calcite.avatica.util.DateTimeUtils（特殊的确定性类）
   * 3. 或者类有 @Deterministic 注解
   * 
   * 预定义的确定性类包括：
   * - Byte, Boolean, Short, Integer, Long（基本类型包装类）
   * - BigInteger, BigDecimal（大数类）
   * - String（字符串类）
   * - Math（数学工具类）
   * 
   * 这些类的特点是：
   * - 不可变：对象创建后状态不会改变
   * - 线程安全：可以在多线程环境中安全使用
   * - 所有方法都是确定性的：相同输入总是产生相同输出
   *
   * @param klass class to test // 要测试的类
   * @return true when all the methods including constructors are deterministic // 当所有方法（包括构造函数）都是确定性的时候返回 true
   */
  protected boolean allMethodsDeterministic(Class klass) { // 方法签名，接收类作为参数
    return DETERMINISTIC_CLASSES.contains(klass) // 如果类在确定性类集合中
        || "org.apache.calcite.avatica.util.DateTimeUtils".equals(klass.getCanonicalName()) // 或者是 DateTimeUtils 类
        || klass.isAnnotationPresent(Deterministic.class); // 或者类有 @Deterministic 注解
  }

  /**
   * Verifies if the variable name is already in use. // 验证变量名是否已被使用
   * Only the variables that are explicitly added to {@code fieldsByName} are // 只有显式添加到 fieldsByName 的变量才会被
   * verified. The method verifies parent chain. // 验证。该方法验证父链
   *
   * 这个方法用于检查给定的字段名是否已被使用。
   * 它会检查当前优化器的 fieldsByName 映射，以及父优化器的映射（支持嵌套类）。
   * 
   * 检查逻辑：
   * 1. 如果当前优化器的 fieldsByName 映射非空且包含该名称，返回 true
   * 2. 如果父优化器不为空且父优化器认为该名称已被使用，返回 true
   * 3. 否则返回 false
   * 
   * 这个方法用于在生成新字段名时避免名称冲突，确保每个字段名都是唯一的。
   *
   * @param name name of the variable to test // 要测试的变量名
   * @return true if the name is used by one of static final fields // 如果该名称被某个静态 final 字段使用，返回 true
   */
  @Override protected boolean hasField(String name) { // 重写父类方法，检查字段名是否已被使用
    return !fieldsByName.isEmpty() && fieldsByName.containsKey(name) // 如果字段名映射非空且包含该名称
        || parent != null && parent.hasField(name); // 或者父优化器不为空且父优化器认为该名称已被使用
  }

  /**
   * Creates child visitor. It is used to traverse nested class declarations. // 创建子访问器。用于遍历嵌套类声明
   *
   * 这个方法用于创建子优化器来处理嵌套类声明。
   * 当优化器遇到内部类或匿名类时，会调用这个方法创建一个新的优化器实例。
   * 
   * 子优化器的特点：
   * 1. 继承父优化器的常量映射和字段映射
   * 2. 有自己的字段集合，不会与父类的字段冲突
   * 3. 可以访问父类中定义的常量
   * 4. 为嵌套类独立收集字段
   * 
   * 这种设计模式允许优化器递归地处理复杂的类结构，包括：
   * - 内部类（Inner Class）
   * - 静态嵌套类（Static Nested Class）
   * - 匿名类（Anonymous Class）
   * - 局部类（Local Class）
   * 
   * 每个类都有自己的字段集合，但可以访问外部类的常量，从而实现正确的优化。
   *
   * @return new Visitor that is used to optimize class declarations // 用于优化类声明的新访问器
   */
  @Override protected DeterministicCodeOptimizer goDeeper() { // 重写父类方法，创建子优化器
    return new DeterministicCodeOptimizer(this); // 创建新的优化器实例，以当前优化器作为父优化器
  }
} // 类定义结束
