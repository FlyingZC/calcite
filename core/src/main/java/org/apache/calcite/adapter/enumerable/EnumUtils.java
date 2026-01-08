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
package org.apache.calcite.adapter.enumerable; // 包声明：定义该类属于org.apache.calcite.adapter.enumerable包

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂，用于创建Java类型
import org.apache.calcite.avatica.util.DateTimeUtils; // 导入日期时间工具类，提供常量和方法用于日期时间计算
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入抽象可枚举类，用于实现可枚举接口
import org.apache.calcite.linq4j.Enumerable; // 导入可枚举接口，表示可以迭代的序列
import org.apache.calcite.linq4j.Enumerator; // 导入枚举器接口，用于遍历集合
import org.apache.calcite.linq4j.JoinType; // 导入连接类型枚举，定义INNER、LEFT、RIGHT等连接类型
import org.apache.calcite.linq4j.Nullness; // 导入空值处理工具类
import org.apache.calcite.linq4j.Ord; // 导入有序包装类，用于索引和值的配对
import org.apache.calcite.linq4j.function.Function1; // 导入单参数函数接口
import org.apache.calcite.linq4j.function.Function2; // 导入双参数函数接口
import org.apache.calcite.linq4j.function.Predicate2; // 导入双参数谓词接口
import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入代码块构建器，用于构建Java代码块
import org.apache.calcite.linq4j.tree.BlockStatement; // 导入代码块语句，表示一个代码块
import org.apache.calcite.linq4j.tree.ConstantExpression; // 导入常量表达式，表示常量值
import org.apache.calcite.linq4j.tree.ConstantUntypedNull; // 导入无类型空常量表达式
import org.apache.calcite.linq4j.tree.DeclarationStatement; // 导入声明语句，用于变量声明
import org.apache.calcite.linq4j.tree.Expression; // 导入表达式基类，表示各种表达式
import org.apache.calcite.linq4j.tree.ExpressionType; // 导入表达式类型枚举，定义各种表达式类型
import org.apache.calcite.linq4j.tree.Expressions; // 导入表达式工厂类，用于创建各种表达式
import org.apache.calcite.linq4j.tree.FunctionExpression; // 导入函数表达式，表示Lambda表达式
import org.apache.calcite.linq4j.tree.MethodCallExpression; // 导入方法调用表达式，表示方法调用
import org.apache.calcite.linq4j.tree.MethodDeclaration; // 导入方法声明，表示方法定义
import org.apache.calcite.linq4j.tree.NewArrayExpression; // 导入数组创建表达式
import org.apache.calcite.linq4j.tree.ParameterExpression; // 导入参数表达式，表示方法参数
import org.apache.calcite.linq4j.tree.Primitive; // 导入基本类型枚举，定义Java基本类型
import org.apache.calcite.linq4j.tree.Statement; // 导入语句基类，表示各种语句
import org.apache.calcite.linq4j.tree.Types; // 导入类型工具类，提供类型操作方法
import org.apache.calcite.linq4j.tree.UnaryExpression; // 导入一元表达式，表示单目运算符表达式
import org.apache.calcite.rel.RelNode; // 导入关系表达式节点，表示关系代数操作
import org.apache.calcite.rel.core.JoinRelType; // 导入关系连接类型，定义INNER、LEFT、RIGHT等连接类型
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型，表示Calcite中的数据类型
import org.apache.calcite.rel.type.RelDataTypeField; // 导入关系数据类型字段，表示字段定义
import org.apache.calcite.rex.RexBuilder; // 导入Rex表达式构建器，用于构建行表达式
import org.apache.calcite.rex.RexNode; // 导入行表达式节点，表示行表达式
import org.apache.calcite.rex.RexProgramBuilder; // 导入Rex程序构建器，用于构建Rex程序
import org.apache.calcite.runtime.PairList; // 导入键值对列表，存储键值对集合
import org.apache.calcite.runtime.SortedMultiMap; // 导入排序多重映射，支持一对多映射
import org.apache.calcite.runtime.SqlFunctions; // 导入SQL函数工具类，提供SQL内置函数实现
import org.apache.calcite.runtime.Utilities; // 导入工具类，提供通用工具方法
import org.apache.calcite.sql.SqlCollation; // 导入SQL排序规则，定义字符串比较规则
import org.apache.calcite.util.BuiltInMethod; // 导入内置方法枚举，列出Calcite内置方法
import org.apache.calcite.util.Pair; // 导入键值对类，存储两个值
import org.apache.calcite.util.Util; // 导入工具类，提供通用工具方法

import com.google.common.collect.ImmutableList; // 导入Google不可变列表，提供不可变列表实现
import com.google.common.collect.ImmutableMap; // 导入Google不可变映射，提供不可变映射实现

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，标记可能为null的值

import java.lang.reflect.Method; // 导入方法类，用于反射操作
import java.lang.reflect.Modifier; // 导入修饰符类，用于获取方法修饰符
import java.lang.reflect.Type; // 导入类型接口，表示Java类型
import java.math.BigDecimal; // 导入BigDecimal类，用于高精度十进制运算
import java.math.RoundingMode; // 导入舍入模式枚举，定义各种舍入方式
import java.sql.Date; // 导入SQL日期类，表示日期值
import java.sql.Time; // 导入SQL时间类，表示时间值
import java.sql.Timestamp; // 导入SQL时间戳类，表示日期时间值
import java.text.Collator; // 导入排序器类，用于字符串比较
import java.util.AbstractList; // 导入抽象列表类，用于自定义列表实现
import java.util.ArrayDeque; // 导入数组双端队列，提供高效的队列操作
import java.util.ArrayList; // 导入动态数组列表，提供可变长度数组
import java.util.Arrays; // 导入数组工具类，提供数组操作方法
import java.util.Deque; // 导入双端队列接口，支持两端操作
import java.util.HashMap; // 导入哈希映射，提供键值对存储
import java.util.List; // 导入列表接口，表示有序集合
import java.util.Locale; // 导入语言环境类，用于本地化
import java.util.Map; // 导入映射接口，表示键值对集合
import java.util.TimeZone; // 导入时区类，用于时区处理
import java.util.function.Function; // 导入函数接口，表示函数式编程

import static org.apache.calcite.config.CalciteSystemProperty.JOIN_SELECTOR_COMPACT_CODE_THRESHOLD; // 导入连接选择器紧凑代码阈值配置

import static java.util.Objects.requireNonNull; // 导入Objects工具类的requireNonNull方法，用于空值检查

/**
 * Utilities for generating programs in the Enumerable (functional)
 * style.
 * 用于生成可枚举（函数式）风格程序的工具类。
 * 
 * 这个类提供了许多静态方法，用于在Calcite的Enumerable适配器中生成Java代码。
 * 它是Calcite将关系代数转换为可执行的Java代码的核心工具类之一。
 * 
 * 主要功能包括：
 * 1. 类型转换：在Calcite内部类型和Java类型之间进行转换
 * 2. 表达式生成：生成各种Java表达式，如方法调用、类型转换等
 * 3. 连接操作：生成连接选择器，用于连接左右输入
 * 4. 窗口操作：生成滚动、滑动、会话窗口的选择器
 * 5. 方法调用：智能匹配方法参数，支持类型转换
 */
public class EnumUtils { // 类声明：EnumUtils工具类，提供生成可枚举风格程序的静态方法

  private EnumUtils() {} // 私有构造方法，防止实例化（工具类不需要实例）

  static final boolean BRIDGE_METHODS = true; // 静态常量：是否生成桥接方法，用于处理类型擦除后的方法重写

  static final List<ParameterExpression> NO_PARAMS = // 静态常量：空的参数表达式列表，用于表示无参数
      ImmutableList.of(); // 使用不可变列表创建空列表

  static final List<Expression> NO_EXPRS = // 静态常量：空的表达式列表，用于表示无表达式
      ImmutableList.of(); // 使用不可变列表创建空列表

  public static final List<String> LEFT_RIGHT = // 公共静态常量：左右输入的参数名称列表，用于连接操作
      ImmutableList.of("left", "right"); // 使用不可变列表创建包含"left"和"right"的列表

  /**
   * Declares a method that overrides another method.
   * 声明一个覆盖另一个方法的方法。
   *
   * @param method 要覆盖的方法
   * @param parameters 方法的参数表达式列表
   * @param body 方法体代码块
   * @return 方法声明表达式
   */
  public static MethodDeclaration overridingMethodDecl(Method method, // 参数：要覆盖的方法对象
      Iterable<ParameterExpression> parameters, // 参数：方法的参数表达式列表（可迭代）
      BlockStatement body) { // 参数：方法体代码块语句
    return Expressions.methodDecl( // 返回：调用Expressions工厂创建方法声明
        method.getModifiers() & ~Modifier.ABSTRACT, // 移除ABSTRACT修饰符，保留其他修饰符（如public等）
        method.getReturnType(), // 使用原方法的返回类型
        method.getName(), // 使用原方法的方法名
        parameters, // 使用传入的参数列表
        body); // 使用传入的方法体
  }

  /**
   * Gets the Java class for a given RelDataType.
   * 获取给定关系数据类型对应的Java类。
   *
   * @param typeFactory Java类型工厂，用于类型转换
   * @param type 关系数据类型
   * @return Java类型（Class或Object[].class）
   */
  static Type javaClass( // 静态方法：获取Java类
      JavaTypeFactory typeFactory, // 参数：Java类型工厂
      RelDataType type) { // 参数：关系数据类型
    final Type clazz = typeFactory.getJavaClass(type); // 调用类型工厂获取Java类
    return clazz instanceof Class ? clazz : Object[].class; // 如果是Class类型则返回，否则返回Object[].class（用于数组类型）
  }

  /**
   * Gets a list of Java types for a list of RelDataTypes.
   * 获取关系数据类型列表对应的Java类型列表。
   * 使用延迟计算，只在需要时才计算每个类型。
   *
   * @param typeFactory Java类型工厂
   * @param inputTypes 关系数据类型列表
   * @return Java类型列表（通过AbstractList实现延迟计算）
   */
  static List<Type> fieldTypes( // 静态方法：获取字段类型列表
      final JavaTypeFactory typeFactory, // 参数：Java类型工厂（final，不可修改）
      final List<? extends RelDataType> inputTypes) { // 参数：关系数据类型列表（final，不可修改）
    return new AbstractList<Type>() { // 返回：创建一个抽象列表，实现延迟计算
      @Override public Type get(int index) { // 重写get方法：获取指定索引的Java类型
        return EnumUtils.javaClass(typeFactory, inputTypes.get(index)); // 调用javaClass方法转换
      }
      @Override public int size() { // 重写size方法：返回列表大小
        return inputTypes.size(); // 直接返回输入列表的大小
      }
    };
  }

  /**
   * Gets a list of RelDataTypes for fields based on argument indices.
   * 根据参数索引列表获取字段的关系数据类型列表。
   * 支持从输入行类型和额外的输入中获取类型。
   *
   * @param inputRowType 输入行的关系数据类型
   * @param extraInputs 额外的输入表达式列表（可能为null）
   * @param argList 参数索引列表，指定要获取哪些字段
   * @return 关系数据类型列表（通过AbstractList实现延迟计算）
   */
  static List<RelDataType> fieldRowTypes( // 静态方法：获取字段行类型列表
      final RelDataType inputRowType, // 参数：输入行的关系数据类型
      final @Nullable List<? extends RexNode> extraInputs, // 参数：额外的输入表达式列表（可能为null，@Nullable注解标记）
      final List<Integer> argList) { // 参数：参数索引列表
    final List<RelDataTypeField> inputFields = inputRowType.getFieldList(); // 获取输入行的所有字段列表
    return new AbstractList<RelDataType>() { // 返回：创建一个抽象列表，实现延迟计算
      @Override public RelDataType get(int index) { // 重写get方法：获取指定索引的关系数据类型
        final int arg = argList.get(index); // 获取参数索引
        return arg < inputFields.size() // 如果索引小于输入字段数量
            ? inputFields.get(arg).getType() // 则从输入字段中获取类型
            : requireNonNull(extraInputs, "extraInputs") // 否则从额外输入中获取（确保extraInputs不为null）
                .get(arg - inputFields.size()).getType(); // 计算在extraInputs中的索引并获取类型
      }
      @Override public int size() { // 重写size方法：返回列表大小
        return argList.size(); // 返回参数索引列表的大小
      }
    };
  }

  /**
   * Creates a join selector expression for joining two inputs.
   * 创建用于连接两个输入的连接选择器表达式。
   * 连接选择器是一个函数，接收左右两个输入，返回连接后的结果行。
   *
   * @param joinType 连接类型（INNER、LEFT、RIGHT、FULL等）
   * @param physType 输出行的物理类型
   * @param inputPhysTypes 输入行的物理类型列表
   * @return 连接选择器Lambda表达式
   */
  static Expression joinSelector(JoinRelType joinType, // 参数：连接关系类型
      PhysType physType, // 参数：输出行的物理类型
      List<PhysType> inputPhysTypes) { // 参数：输入行的物理类型列表
    final int outputFieldCount = physType.getRowType().getFieldCount(); // 获取输出行的字段数量
    // If there are many output fields, create the output dynamically so that the code size stays
    // below the limit. See CALCITE-3094.
    // 如果输出字段很多，则动态创建输出以保持代码大小低于限制。参见CALCITE-3094。
    if (shouldGenerateCompactCode(outputFieldCount)) { // 如果需要生成紧凑代码
      return joinSelectorCompact(joinType, physType, inputPhysTypes); // 返回紧凑版本的连接选择器
    }

    // A parameter for each input.
    // 为每个输入创建一个参数。
    final List<ParameterExpression> parameters = new ArrayList<>(); // 创建参数表达式列表

    // Generate all fields.
    // 生成所有字段表达式。
    final List<Expression> expressions = new ArrayList<>(); // 创建表达式列表
    for (Ord<PhysType> ord : Ord.zip(inputPhysTypes)) { // 遍历输入物理类型列表（带索引）
      final PhysType inputPhysType = // 获取当前输入物理类型
          ord.e.makeNullable(joinType.generatesNullsOn(ord.i)); // 根据连接类型，如果该侧可能生成null，则使类型可空
      // If input item is just a primitive, we do not generate specialized
      // primitive apply override since it won't be called anyway
      // Function<T> always operates on boxed arguments
      // 如果输入项只是基本类型，我们不生成专门的基本类型应用覆盖，因为它不会被调用
      // Function<T>总是操作装箱参数
      final ParameterExpression parameter = // 创建参数表达式
          Expressions.parameter(Primitive.box(inputPhysType.getJavaRowType()), // 使用装箱后的Java行类型
              EnumUtils.LEFT_RIGHT.get(ord.i)); // 使用"left"或"right"作为参数名
      parameters.add(parameter); // 添加参数到参数列表
      if (expressions.size() == outputFieldCount) { // 如果已生成的表达式数量等于输出字段数
        // For instance, if semi-join needs to return just the left inputs
        // 例如，如果半连接只需要返回左输入
        break; // 则停止生成更多字段
      }
      final int fieldCount = inputPhysType.getRowType().getFieldCount(); // 获取当前输入的字段数量
      for (int i = 0; i < fieldCount; i++) { // 遍历当前输入的所有字段
        Expression expression = // 创建字段引用表达式
            inputPhysType.fieldReference(parameter, i, // 参数、字段索引
                physType.getJavaFieldType(expressions.size())); // 输出字段的Java类型
        if (joinType.generatesNullsOn(ord.i)) { // 如果该侧连接会生成null值（如LEFT JOIN的右侧）
          expression = // 创建条件表达式，处理null值
              Expressions.condition( // 条件表达式：if-then-else
                  Expressions.equal(parameter, Expressions.constant(null)), // 条件：参数是否为null
                  Expressions.constant(null), // 为null时返回null
                  expression); // 不为null时返回字段引用
        }
        expressions.add(expression); // 添加字段表达式到表达式列表
      }
    }
    return Expressions.lambda( // 返回：创建Lambda表达式
        Function2.class, // 函数类型：双参数函数
        physType.record(expressions), // 函数体：创建记录（输出行）
        parameters); // 参数列表：left和right参数
  }

  /**
   * Determines whether to generate compact code based on output field count.
   * 根据输出字段数量决定是否生成紧凑代码。
   * 紧凑代码可以减少生成的代码大小，避免超过Java方法大小限制。
   *
   * @param outputFieldCount 输出字段数量
   * @return 是否生成紧凑代码
   */
  static boolean shouldGenerateCompactCode(int outputFieldCount) { // 静态方法：判断是否生成紧凑代码
    int compactCodeThreshold = JOIN_SELECTOR_COMPACT_CODE_THRESHOLD.value(); // 获取紧凑代码阈值配置
    return compactCodeThreshold >= 0 && outputFieldCount >= compactCodeThreshold; // 如果阈值>=0且字段数>=阈值，则返回true
  }

  /**
   * Creates a compact join selector expression for joining two inputs.
   * 创建紧凑版本的连接选择器表达式。
   * 紧凑版本使用数组来存储输出字段，减少代码大小。
   *
   * @param joinType 连接类型
   * @param physType 输出行的物理类型
   * @param inputPhysTypes 输入行的物理类型列表
   * @return 连接选择器Lambda表达式
   */
  static Expression joinSelectorCompact(JoinRelType joinType, // 参数：连接关系类型
      PhysType physType, // 参数：输出行的物理类型
      List<PhysType> inputPhysTypes) { // 参数：输入行的物理类型列表
    // A parameter for each input.
    // 为每个输入创建一个参数。
    final List<ParameterExpression> parameters = new ArrayList<>(); // 创建参数表达式列表

    // Generate all fields.
    // 生成所有字段。
    final int outputFieldCount = physType.getRowType().getFieldCount(); // 获取输出行的字段数量

    final BlockBuilder compactCode = new BlockBuilder(); // 创建代码块构建器，用于构建紧凑代码
    // Even if the fields are all of the same type, they are always boxed,
    // so we use an Object[] that is easier to match with the input arrays.
    // 即使所有字段都是相同类型，它们也总是被装箱，所以我们使用Object[]，这更容易与输入数组匹配。
    final ParameterExpression compactOutputVar = // 创建输出数组变量表达式
        Expressions.variable(Object[].class, "outputArray"); // 类型：Object[]，名称：outputArray
    final DeclarationStatement exp = // 创建数组声明语句
        Expressions.declare( // 声明变量
            0, // 修饰符：0表示无修饰符
            compactOutputVar, // 变量表达式
            new NewArrayExpression(Object.class, 1, // 创建新数组：Object类型，一维
                Expressions.constant(outputFieldCount), // 数组长度：输出字段数量
                null)); // 初始化值：null
    compactCode.add(exp); // 添加数组声明到代码块

    int outputField = 0; // 输出字段索引，从0开始
    for (Ord<PhysType> ord : Ord.zip(inputPhysTypes)) { // 遍历输入物理类型列表（带索引）
      final PhysType inputPhysType = // 获取当前输入物理类型
          ord.e.makeNullable(joinType.generatesNullsOn(ord.i)); // 根据连接类型，如果该侧可能生成null，则使类型可空
      // If the parameter is an array we declare as Object[] because it
      // needs to match the type of the array that will be returned
      // 如果参数是数组，我们声明为Object[]，因为它需要与将被返回的数组类型匹配
      final Type parameterType = Types.isArray(inputPhysType.getJavaRowType()) // 如果Java行类型是数组
          ? Object[].class // 则使用Object[]作为参数类型
          : Primitive.box(inputPhysType.getJavaRowType()); // 否则使用装箱后的Java行类型

      final ParameterExpression parameter = // 创建参数表达式
          Expressions.parameter(parameterType, // 参数类型
              EnumUtils.LEFT_RIGHT.get(ord.i)); // 参数名称："left"或"right"
      parameters.add(parameter); // 添加参数到参数列表
      if (outputField == outputFieldCount) { // 如果已处理的字段数等于输出字段数
        // For instance, if semi-join needs to return just the left inputs
        // 例如，如果半连接只需要返回左输入
        break; // 则停止处理
      }
      final int fieldCount = inputPhysType.getRowType().getFieldCount(); // 获取当前输入的字段数量
      // Delegate copying the row values to JavaRowFormat
      // 将行值复制委托给JavaRowFormat
      final List<Statement> copyStatements = // 获取复制语句列表
          Nullness.castNonNull( // 强制转换非空（编译器无法推断）
              inputPhysType.getFormat().copy(parameter, // 调用格式对象的copy方法复制字段
                  Nullness.castNonNull(compactOutputVar), // 输出数组（强制非空）
                  outputField, // 输出数组起始位置
                  fieldCount)); // 要复制的字段数量
      if (joinType.generatesNullsOn(ord.i)) { // 如果该侧连接会生成null值
        // [CALCITE-6593] NPE when outer joining tables with many fields and unmatching rows
        // [CALCITE-6593] 在外连接多字段表且行不匹配时出现空指针异常
        compactCode.add( // 添加条件语句到代码块
            Expressions.ifThen( // if-then语句
                Expressions.notEqual(parameter, Expressions.constant(null)), // 条件：参数不为null
                Expressions.block(copyStatements))); // 执行复制语句块
      } else { // 如果该侧连接不会生成null值
        for (Statement copyStatement : copyStatements) { // 遍历所有复制语句
          compactCode.add(copyStatement); // 直接添加到代码块
        }
      }
      outputField += fieldCount; // 更新输出字段索引
    }

    compactCode.add(Nullness.castNonNull(compactOutputVar)); // 添加返回语句，返回输出数组
    return Expressions.lambda( // 返回：创建Lambda表达式
        Function2.class, // 函数类型：双参数函数
        compactCode.toBlock(), // 函数体：代码块
        parameters); // 参数列表：left和right参数
  }

  /**
   * In Calcite, {@code java.sql.Date} and {@code java.sql.Time} are
   * stored as {@code Integer} type, {@code java.sql.Timestamp} is
   * stored as {@code Long} type.
   * 在Calcite中，java.sql.Date和java.sql.Time以Integer类型存储，
   * java.sql.Timestamp以Long类型存储。
   *
   * Converts an expression to internal representation type.
   * 将表达式转换为内部表示类型。
   * 内部表示类型是指Calcite内部使用的优化存储类型，如日期和时间用整数存储。
   *
   * @param operand 要转换的表达式
   * @param targetType 目标类型（可能为null）
   * @return 转换后的表达式
   */
  static Expression toInternal(Expression operand, // 参数：操作数表达式
      @Nullable Type targetType) { // 参数：目标类型（可能为null）
    return toInternal(operand, operand.getType(), targetType); // 调用重载方法，传入源类型
  }

  /**
   * Converts an expression to internal representation type.
   * 将表达式转换为内部表示类型。
   *
   * @param operand 要转换的表达式
   * @param fromType 源类型
   * @param targetType 目标类型（可能为null）
   * @return 转换后的表达式
   */
  private static Expression toInternal(Expression operand, // 私有静态方法：转换为内部表示
      Type fromType, // 参数：源类型
      @Nullable Type targetType) { // 参数：目标类型（可能为null）
    if (fromType == java.sql.Date.class) { // 如果源类型是java.sql.Date
      if (targetType == int.class) { // 如果目标类型是基本int
        return Expressions.call(BuiltInMethod.DATE_TO_INT.method, operand); // 调用DateToInt方法
      } else if (targetType == Integer.class) { // 如果目标类型是包装类Integer
        return Expressions.call(BuiltInMethod.DATE_TO_INT_OPTIONAL.method, operand); // 调用DateToIntOptional方法
      }
    } else if (fromType == java.sql.Time.class) { // 如果源类型是java.sql.Time
      if (targetType == int.class) { // 如果目标类型是基本int
        return Expressions.call(BuiltInMethod.TIME_TO_INT.method, operand); // 调用TimeToInt方法
      } else if (targetType == Integer.class) { // 如果目标类型是包装类Integer
        return Expressions.call(BuiltInMethod.TIME_TO_INT_OPTIONAL.method, operand); // 调用TimeToIntOptional方法
      }
    } else if (fromType == java.sql.Timestamp.class) { // 如果源类型是java.sql.Timestamp
      if (targetType == long.class) { // 如果目标类型是基本long
        return Expressions.call(BuiltInMethod.TIMESTAMP_TO_LONG.method, operand); // 调用TimestampToLong方法
      } else if (targetType == Long.class) { // 如果目标类型是包装类Long
        return Expressions.call(BuiltInMethod.TIMESTAMP_TO_LONG_OPTIONAL.method, operand); // 调用TimestampToLongOptional方法
      }
    }
    return operand; // 如果不需要转换，直接返回原表达式
  }

  /** Converts from internal representation to JDBC representation used by
   * arguments of user-defined functions. For example, converts date values from
   * {@code int} to {@link java.sql.Date}.
   * 从内部表示转换为用户定义函数参数使用的JDBC表示。
   * 例如，将日期值从int转换为java.sql.Date。
   *
   * @param operand 要转换的表达式
   * @param targetType 目标类型
   * @return 转换后的表达式
   */
  private static Expression fromInternal(Expression operand, // 私有静态方法：从内部表示转换
      Type targetType) { // 参数：目标类型
    return fromInternal(operand, operand.getType(), targetType); // 调用重载方法，传入源类型
  }

  /**
   * Converts from internal representation to JDBC representation.
   * 从内部表示转换为JDBC表示。
   *
   * @param operand 要转换的表达式
   * @param fromType 源类型
   * @param targetType 目标类型
   * @return 转换后的表达式
   */
  private static Expression fromInternal(Expression operand, // 私有静态方法：从内部表示转换
      Type fromType, // 参数：源类型
      Type targetType) { // 参数：目标类型
    if (operand == ConstantUntypedNull.INSTANCE) { // 如果操作数是无类型空常量
      return operand; // 直接返回
    }
    if (!(operand.getType() instanceof Class)) { // 如果操作数类型不是Class（如泛型类型）
      return operand; // 直接返回
    }
    if (Types.isAssignableFrom(targetType, fromType)) { // 如果目标类型可以赋值源类型
      return operand; // 直接返回，不需要转换
    }
    if (targetType == java.sql.Date.class) { // 如果目标类型是java.sql.Date
      // E.g. from "int" or "Integer" to "java.sql.Date",
      // generate "SqlFunctions.internalToDate".
      // 例如，从"int"或"Integer"到"java.sql.Date"，生成"SqlFunctions.internalToDate"。
      if (isA(fromType, Primitive.INT)) { // 如果源类型是int或Integer
        return Expressions.call(BuiltInMethod.INTERNAL_TO_DATE.method, operand); // 调用internalToDate方法
      }
    } else if (targetType == java.sql.Time.class) { // 如果目标类型是java.sql.Time
      // E.g. from "int" or "Integer" to "java.sql.Time",
      // generate "SqlFunctions.internalToTime".
      // 例如，从"int"或"Integer"到"java.sql.Time"，生成"SqlFunctions.internalToTime"。
      if (isA(fromType, Primitive.INT)) { // 如果源类型是int或Integer
        return Expressions.call(BuiltInMethod.INTERNAL_TO_TIME.method, operand); // 调用internalToTime方法
      }
    } else if (targetType == java.sql.Timestamp.class) { // 如果目标类型是java.sql.Timestamp
      // E.g. from "long" or "Long" to "java.sql.Timestamp",
      // generate "SqlFunctions.internalToTimestamp".
      // 例如，从"long"或"Long"到"java.sql.Timestamp"，生成"SqlFunctions.internalToTimestamp"。
      if (isA(fromType, Primitive.LONG)) { // 如果源类型是long或Long
        return Expressions.call(BuiltInMethod.INTERNAL_TO_TIMESTAMP.method, operand); // 调用internalToTimestamp方法
      }
    }
    if (Primitive.is(operand.type) // 如果操作数是基本类型
        && Primitive.isBox(targetType)) { // 且目标是包装类型
      // E.g. operand is "int", target is "Long", generate "(long) operand".
      // 例如，操作数是"int"，目标是"Long"，生成"(long) operand"。
      return Expressions.convert_(operand, // 转换表达式
          Primitive.unbox(targetType)); // 转换为基本类型
    }
    return operand; // 如果不需要转换，直接返回原表达式
  }

  /**
   * Converts a list of expressions from internal to JDBC representation.
   * 将表达式列表从内部表示转换为JDBC表示。
   *
   * @param targetTypes 目标类型数组
   * @param expressions 表达式列表
   * @return 转换后的表达式列表
   */
  static List<Expression> fromInternal(Class<?>[] targetTypes, // 静态方法：批量从内部表示转换
      List<Expression> expressions) { // 参数：表达式列表
    final List<Expression> list = new ArrayList<>(); // 创建结果列表
    if (targetTypes.length == expressions.size()) { // 如果目标类型数量等于表达式数量
      for (int i = 0; i < expressions.size(); i++) { // 遍历所有表达式
        list.add(fromInternal(expressions.get(i), targetTypes[i])); // 逐个转换并添加
      }
    } else { // 如果数量不匹配（可能是处理可变参数）
      int j = 0; // 目标类型索引
      for (Expression expression : expressions) { // 遍历所有表达式
        Class<?> type; // 目标类型变量
        if (!targetTypes[j].isArray()) { // 如果当前目标类型不是数组
          type = targetTypes[j]; // 直接使用该类型
          j++; // 移动到下一个目标类型
        } else { // 如果是数组类型（可变参数）
          type = targetTypes[j].getComponentType(); // 获取数组元素类型
        }
        list.add(fromInternal(expression, type)); // 转换并添加
      }
    }
    return list; // 返回转换后的列表
  }

  /**
   * Gets the internal representation type for a given type.
   * 获取给定类型的内部表示类型。
   *
   * @param type Java类型
   * @return 内部表示类型
   */
  static Type fromInternal(Type type) { // 静态方法：获取内部表示类型
    if (type == java.sql.Date.class || type == java.sql.Time.class) { // 如果是Date或Time
      return int.class; // 返回int类型
    }
    if (type == java.sql.Timestamp.class) { // 如果是Timestamp
      return long.class; // 返回long类型
    }
    return type; // 其他类型直接返回
  }

  /**
   * Gets the internal representation type for a RelDataType.
   * 获取关系数据类型的内部表示类型。
   *
   * @param type 关系数据类型
   * @return 内部表示类型（可能为null）
   */
  private static @Nullable Type toInternal(RelDataType type) { // 私有静态方法：获取内部表示类型
    return toInternal(type, false); // 调用重载方法，不强制非空
  }

  /**
   * Gets the internal representation type for a RelDataType.
   * 获取关系数据类型的内部表示类型。
   *
   * @param type 关系数据类型
   * @param forceNotNull 是否强制非空（忽略可空性）
   * @return 内部表示类型（可能为null）
   */
  static @Nullable Type toInternal(RelDataType type, // 静态方法：获取内部表示类型
      boolean forceNotNull) { // 参数：是否强制非空
    switch (type.getSqlTypeName()) { // 根据SQL类型名称判断
    case DATE: // 如果是日期类型
    case TIME: // 如果是时间类型
      return type.isNullable() && !forceNotNull ? Integer.class : int.class; // 如果可空且不强制非空，返回Integer，否则返回int
    case TIMESTAMP: // 如果是时间戳类型
      return type.isNullable() && !forceNotNull ? Long.class : long.class; // 如果可空且不强制非空，返回Long，否则返回long
    default: // 其他类型
      return null; // 不关心，使用默认存储类型
    }
  }

  /**
   * Gets the internal representation types for a list of RexNodes.
   * 获取Rex节点列表的内部表示类型列表。
   *
   * @param operandList Rex节点列表
   * @return 内部表示类型列表
   */
  static List<@Nullable Type> internalTypes(List<? extends RexNode> operandList) { // 静态方法：批量获取内部表示类型
    return Util.transform(operandList, // 使用Util工具类转换列表
        node -> toInternal(node.getType())); // 对每个节点调用toInternal方法
  }

  /**
   * Convert {@code operand} to target type {@code toType}.
   * 将操作数转换为目标类型。
   *
   * @param operand The expression to convert 要转换的表达式
   * @param toType  Target type 目标类型
   * @return A new expression with type {@code toType} or original if there
   * is no need to convert 返回具有目标类型的新表达式，如果不需要转换则返回原表达式
   */
  public static Expression convert(Expression operand, // 公共静态方法：类型转换
      Type toType) { // 参数：目标类型
    final Type fromType = operand.getType(); // 获取源类型
    return convert(operand, fromType, toType); // 调用重载方法进行转换
  }

  /**
   * Convert {@code operand} to target type {@code toType}.
   * 将操作数转换为目标类型。
   *
   * @param operand  The expression to convert 要转换的表达式
   * @param fromType Field type 字段类型
   * @param toType   Target type 目标类型
   * @return A new expression with type {@code toType} or original if there
   * is no need to convert 返回具有目标类型的新表达式，如果不需要转换则返回原表达式
   */
  public static Expression convert(Expression operand, // 公共静态方法：类型转换
      Type fromType, // 参数：源类型
      Type toType) { // 参数：目标类型
    if (!Types.needTypeCast(fromType, toType)) { // 如果不需要类型转换
      return operand; // 直接返回原表达式
    }

    // TODO use Expressions#convertChecked to throw exception in case of overflow (CALCITE-6366)
    // TODO: 使用Expressions#convertChecked在溢出时抛出异常（CALCITE-6366）

    // E.g. from "Short" to "int".
    // Generate "x.intValue()".
    // 例如，从"Short"到"int"，生成"x.intValue()"。
    final Primitive toPrimitive = Primitive.of(toType); // 获取目标基本类型枚举
    final Primitive toBox = Primitive.ofBox(toType); // 获取目标包装类型枚举
    final Primitive fromBox = Primitive.ofBox(fromType); // 获取源包装类型枚举
    final Primitive fromPrimitive = Primitive.of(fromType); // 获取源基本类型枚举
    final boolean fromNumber = fromType instanceof Class // 判断源类型是否是Number的子类
        && Number.class.isAssignableFrom((Class) fromType);
    if (fromType == String.class) { // 如果源类型是String
      if (toPrimitive != null) { // 如果目标是基本类型
        switch (toPrimitive) { // 根据目标基本类型判断
        case CHAR: // 如果是char
        case SHORT: // 如果是short
        case INT: // 如果是int
        case LONG: // 如果是long
        case FLOAT: // 如果是float
        case DOUBLE: // 如果是double
          // Generate "SqlFunctions.toShort(x)".
          // 生成"SqlFunctions.toShort(x)"。
          return Expressions.call( // 调用SqlFunctions的静态方法
              SqlFunctions.class, // 类名
              "to" + SqlFunctions.initcap(toPrimitive.getPrimitiveName()), // 方法名：toShort、toInt等
              operand); // 参数
        default: // 其他基本类型
          // Generate "parseShort(x)".
          // 生成"parseShort(x)"。
          return Expressions.call( // 调用包装类的parse方法
              toPrimitive.getBoxClass(), // 包装类
              "parse" + SqlFunctions.initcap(toPrimitive.getPrimitiveName()), // 方法名：parseShort、parseInt等
              operand); // 参数
        }
      }
      if (toBox != null) { // 如果目标是包装类型
        switch (toBox) { // 根据目标包装类型判断
        case VOID: // 如果是Void
          return Expressions.constant(null); // 返回null常量
        case CHAR: // 如果是Character
          // Generate "SqlFunctions.toCharBoxed(x)".
          // 生成"SqlFunctions.toCharBoxed(x)"。
          return Expressions.call( // 调用SqlFunctions的静态方法
              SqlFunctions.class, // 类名
              "to" + SqlFunctions.initcap(toBox.getPrimitiveName()) + "Boxed", // 方法名：toCharBoxed等
              operand); // 参数
        default: // 其他包装类型
          // Generate "Short.valueOf(x)".
          // 生成"Short.valueOf(x)"。
          return Expressions.call( // 调用包装类的valueOf方法
              toBox.getBoxClass(), // 包装类
              "valueOf", // 方法名
              operand); // 参数
        }
      }
    }
    if (toPrimitive != null) { // 如果目标是基本类型
      if (fromPrimitive != null) { // 如果源也是基本类型
        // E.g. from "float" to "double"
        // 例如，从"float"到"double"
        if (toPrimitive == Primitive.BOOLEAN) { // 如果目标是boolean
          // Conversion to Boolean can use the existing 'convert_' function
          // 转换为Boolean可以使用现有的'convert_'函数
          return Expressions.convert_(operand, toPrimitive.getPrimitiveClass()); // 调用convert_方法
        }
        // Other destination types require checked conversions
        // 其他目标类型需要检查转换
        return Expressions.convertChecked( // 调用convertChecked方法，会检查溢出
            operand, toPrimitive.getPrimitiveClass()); // 转换为目标基本类型
      }
      if (fromType == BigDecimal.class && toPrimitive.isFixedNumeric()) { // 如果源是BigDecimal且目标是精确数值类型
        // Conversion from decimal to an exact type
        // 从十进制到精确类型的转换
        ConstantExpression zero = Expressions.constant(0); // 创建0常量
        // Elsewhere Calcite uses this rounding mode implicitly, so we have to be consistent.
        // E.g., this is the rounding mode used by BigDecimal.longValue().
        // Calcite在其他地方隐式使用这种舍入模式，所以我们必须保持一致。
        // 例如，这是BigDecimal.longValue()使用的舍入模式。
        Expression rounding = Expressions.constant(RoundingMode.DOWN); // 创建DOWN舍入模式常量
        // Generate 'rounded = operand.setScale(0, RoundingMode.DOWN);'
        // 生成'rounded = operand.setScale(0, RoundingMode.DOWN);'
        Expression rounded = Expressions.call(operand, "setScale", zero, rounding); // 调用setScale方法
        // Generate 'return rounded.to*ValueExact()'
        // 生成'return rounded.to*ValueExact()'
        return Expressions.unboxExact(rounded, toPrimitive); // 调用unboxExact方法，精确拆箱
      } else if (fromNumber || fromBox == Primitive.CHAR) { // 如果源是Number或Character
        // Generate "x.shortValue()".
        // 生成"x.shortValue()"。
        return Expressions.unbox(operand, toPrimitive); // 调用unbox方法，拆箱
      } else { // 其他情况
        // E.g. from "Object" to "short".
        // Generate "SqlFunctions.toShort(x)"
        // 例如，从"Object"到"short"，生成"SqlFunctions.toShort(x)"
        return Expressions.call( // 调用SqlFunctions的静态方法
            SqlFunctions.class, // 类名
            "to" + SqlFunctions.initcap(toPrimitive.getPrimitiveName()), // 方法名：toShort等
            operand); // 参数
      }
    } else if (fromNumber && toBox != null) { // 如果源是Number且目标是包装类型
      // E.g. from "Short" to "Integer"
      // Generate "x == null ? null : Integer.valueOf(x.intValue())"
      // 例如，从"Short"到"Integer"，生成"x == null ? null : Integer.valueOf(x.intValue())"
      return Expressions.condition( // 创建条件表达式
          Expressions.equal(operand, RexImpTable.NULL_EXPR), // 条件：操作数是否为null
          RexImpTable.NULL_EXPR, // 为null时返回null
          Expressions.box( // 不为null时
              Expressions.unbox(operand, toBox), // 先拆箱
              toBox)); // 再装箱为目标类型
    } else if (fromPrimitive != null && toBox != null) { // 如果源是基本类型且目标是包装类型
      // E.g. from "int" to "Long".
      // Generate Long.valueOf(x)
      // Eliminate primitive casts like Long.valueOf((long) x)
      // 例如，从"int"到"Long"，生成Long.valueOf(x)
      // 消除基本类型转换，如Long.valueOf((long) x)
      if (operand instanceof UnaryExpression) { // 如果操作数是一元表达式
        UnaryExpression una = (UnaryExpression) operand; // 强制转换
        if (una.nodeType == ExpressionType.Convert // 如果是转换表达式
            && Primitive.of(una.getType()) == toBox) { // 且转换结果类型与目标类型相同
          Primitive origin = Primitive.of(una.expression.type); // 获取原始类型
          if (origin != null && toBox.assignableFrom(origin)) { // 如果原始类型可以赋值给目标类型
            return Expressions.box(una.expression, toBox); // 直接对原始表达式装箱，消除冗余转换
          }
        }
      }
      if (fromType == toBox.primitiveClass) { // 如果源类型就是目标类型的基本类型
        return Expressions.box(operand, toBox); // 直接装箱
      }
      // E.g., from "int" to "Byte".
      // Convert it first and generate "Byte.valueOf((byte)x)"
      // Because there is no method "Byte.valueOf(int)" in Byte
      // 例如，从"int"到"Byte"。
      // 先转换再生成"Byte.valueOf((byte)x)"
      // 因为Byte中没有"Byte.valueOf(int)"方法
      return Expressions.box( // 装箱
          Expressions.convert_(operand, toBox.getPrimitiveClass()), // 先转换为基本类型
          toBox); // 再装箱
    }
    // Convert datetime types to internal storage type:
    // 1. java.sql.Date -> int or Integer
    // 2. java.sql.Time -> int or Integer
    // 3. java.sql.Timestamp -> long or Long
    // 将日期时间类型转换为内部存储类型：
    // 1. java.sql.Date -> int or Integer
    // 2. java.sql.Time -> int or Integer
    // 3. java.sql.Timestamp -> long or Long
    if (representAsInternalType(fromType)) { // 如果源类型需要用内部类型表示
      final Expression internalTypedOperand = // 转换为内部类型
          toInternal(operand, fromType, toType);
      if (operand != internalTypedOperand) { // 如果转换后不同
        return internalTypedOperand; // 返回转换后的表达式
      }
    }
    // Convert internal storage type to datetime types:
    // 1. int or Integer -> java.sql.Date
    // 2. int or Integer -> java.sql.Time
    // 3. long or Long -> java.sql.Timestamp
    // 将内部存储类型转换为日期时间类型：
    // 1. int or Integer -> java.sql.Date
    // 2. int or Integer -> java.sql.Time
    // 3. long or Long -> java.sql.Timestamp
    if (representAsInternalType(toType)) { // 如果目标类型需要用内部类型表示
      final Expression originTypedOperand = // 从内部类型转换
          fromInternal(operand, fromType, toType);
      if (operand != originTypedOperand) { // 如果转换后不同
        return originTypedOperand; // 返回转换后的表达式
      }
    }
    if (toType == BigDecimal.class) { // 如果目标是BigDecimal
      if (fromBox != null) { // 如果源是包装类型
        // E.g. from "Integer" to "BigDecimal".
        // Generate "x == null ? null : new BigDecimal(x.intValue())"
        // 例如，从"Integer"到"BigDecimal"，生成"x == null ? null : new BigDecimal(x.intValue())"
        return Expressions.condition( // 创建条件表达式
            Expressions.equal(operand, RexImpTable.NULL_EXPR), // 条件：操作数是否为null
            RexImpTable.NULL_EXPR, // 为null时返回null
            Expressions.new_( // 不为null时
                BigDecimal.class, // 创建BigDecimal对象
                Expressions.unbox(operand, fromBox))); // 拆箱后作为构造参数
      }
      if (fromPrimitive != null) { // 如果源是基本类型
        // E.g. from "int" to "BigDecimal".
        // Generate "new BigDecimal(x)"
        // 例如，从"int"到"BigDecimal"，生成"new BigDecimal(x)"
        return Expressions.new_(BigDecimal.class, operand); // 创建BigDecimal对象
      }
      // E.g. from "Object" to "BigDecimal".
      // Generate "x == null ? null : SqlFunctions.toBigDecimal(x)"
      // 例如，从"Object"到"BigDecimal"，生成"x == null ? null : SqlFunctions.toBigDecimal(x)"
      return Expressions.condition( // 创建条件表达式
          Expressions.equal(operand, RexImpTable.NULL_EXPR), // 条件：操作数是否为null
          RexImpTable.NULL_EXPR, // 为null时返回null
          Expressions.call( // 不为null时
              SqlFunctions.class, // 类名
              "toBigDecimal", // 方法名
              operand)); // 参数
    } else if (toType == String.class) { // 如果目标是String
      if (fromPrimitive != null) { // 如果源是基本类型
        switch (fromPrimitive) { // 根据源基本类型判断
        case DOUBLE: // 如果是double
        case FLOAT: // 如果是float
          // E.g. from "double" to "String"
          // Generate "SqlFunctions.toString(x)"
          // 例如，从"double"到"String"，生成"SqlFunctions.toString(x)"
          return Expressions.call( // 调用SqlFunctions的toString方法
              SqlFunctions.class, // 类名
              "toString", // 方法名
              operand); // 参数
        default: // 其他基本类型
          // E.g. from "int" to "String"
          // Generate "Integer.toString(x)"
          // 例如，从"int"到"String"，生成"Integer.toString(x)"
          return Expressions.call( // 调用包装类的toString方法
              fromPrimitive.getBoxClass(), // 包装类
              "toString", // 方法名
              operand); // 参数
        }
      } else if (fromType == BigDecimal.class) { // 如果源是BigDecimal
        // E.g. from "BigDecimal" to "String"
        // Generate "SqlFunctions.toString(x)"
        // 例如，从"BigDecimal"到"String"，生成"SqlFunctions.toString(x)"
        return Expressions.condition( // 创建条件表达式
            Expressions.equal(operand, RexImpTable.NULL_EXPR), // 条件：操作数是否为null
            RexImpTable.NULL_EXPR, // 为null时返回null
            Expressions.call( // 不为null时
                SqlFunctions.class, // 类名
                "toString", // 方法名
                operand)); // 参数
      } else { // 其他类型
        Expression result; // 结果表达式变量
        try { // 尝试调用toString方法
          // Avoid to generate code like:
          // "null.toString()" or "(xxx) null.toString()"
          // 避免生成如下代码："null.toString()"或"(xxx) null.toString()"
          if (operand instanceof ConstantExpression) { // 如果是常量表达式
            ConstantExpression ce = (ConstantExpression) operand; // 强制转换
            if (ce.value == null) { // 如果值为null
              return Expressions.convert_(operand, toType); // 直接转换
            }
          }
          // Try to call "toString()" method
          // E.g. from "Integer" to "String"
          // Generate "x == null ? null : x.toString()"
          // 尝试调用"toString()"方法
          // 例如，从"Integer"到"String"，生成"x == null ? null : x.toString()"
          result = // 创建结果表达式
              Expressions.condition( // 条件表达式
                  Expressions.equal(operand, RexImpTable.NULL_EXPR), // 条件：操作数是否为null
                  RexImpTable.NULL_EXPR, // 为null时返回null
                  Expressions.call(operand, "toString")); // 不为null时调用toString方法
        } catch (RuntimeException e) { // 捕获运行时异常
          // For some special cases, e.g., "BuiltInMethod.LESSER",
          // its return type is generic ("Comparable"), which contains
          // no "toString()" method. We fall through to "(String)x".
          // 对于某些特殊情况，例如"BuiltInMethod.LESSER"，
          // 其返回类型是泛型（"Comparable"），不包含"toString()"方法。
          // 我们回退到"(String)x"。
          return Expressions.convert_(operand, toType); // 直接转换
        }
        return result; // 返回结果
      }
    }
    return Expressions.convert_(operand, toType); // 默认情况：直接转换
  }

  /** Converts a value to a given class.
   * 将值转换为给定的类。
   *
   * @param o 要转换的对象
   * @param clazz 目标类
   * @return 转换后的值（可能为null）
   */
  public static <T> @Nullable T evaluate(Object o, // 公共静态方法：评估并转换值
      Class<T> clazz) { // 参数：目标类
    // We need optimization here for constant folding.
    // Not all the expressions can be interpreted (e.g. ternary), so
    // we rely on optimization capabilities to fold non-interpretable
    // expressions.
    // 我们需要在这里进行常量折叠优化。
    // 并非所有表达式都可以被解释（例如三元运算符），
    // 所以我们依赖优化能力来折叠不可解释的表达式。
    //noinspection unchecked // 忽略未检查的转换警告
    clazz = Primitive.box(clazz); // 将类转换为包装类型
    BlockBuilder bb = new BlockBuilder(); // 创建代码块构建器
    final Expression expr = // 创建转换表达式
        convert(Expressions.constant(o), clazz); // 将对象转换为常量表达式并转换类型
    bb.add(Expressions.return_(null, expr)); // 添加返回语句
    final FunctionExpression<?> convert = // 创建Lambda表达式
        Expressions.lambda(bb.toBlock(), ImmutableList.of()); // 无参数Lambda
    return clazz.cast(convert.compile().dynamicInvoke()); // 编译并动态调用，然后转换类型
  }

  /**
   * Checks if a type matches a primitive type.
   * 检查类型是否匹配基本类型。
   *
   * @param fromType 要检查的类型
   * @param primitive 基本类型枚举
   * @return 是否匹配
   */
  private static boolean isA(Type fromType, // 私有静态方法：检查是否是某基本类型
      Primitive primitive) { // 参数：基本类型枚举
    return Primitive.of(fromType) == primitive // 判断是否是基本类型
        || Primitive.ofBox(fromType) == primitive; // 或判断是否是包装类型
  }

  /**
   * Checks if a type should be represented as internal type.
   * 检查类型是否应该用内部类型表示。
   *
   * @param type 要检查的类型
   * @return 是否用内部类型表示
   */
  private static boolean representAsInternalType(Type type) { // 私有静态方法：检查是否用内部类型表示
    return type == java.sql.Date.class // 如果是Date
        || type == java.sql.Time.class // 或Time
        || type == java.sql.Timestamp.class; // 或Timestamp
  }

  /**
   * In {@link org.apache.calcite.sql.type.SqlTypeAssignmentRule},
   * some rules decide whether one type can be assignable to another type.
   * Based on these rules, a function can accept arguments with assignable types.
   *
   * <p>For example, a function with Long type operand can accept Integer as input.
   * See {@code org.apache.calcite.sql.SqlUtil#filterRoutinesByParameterType()} for details.
   *
   * <p>During query execution, some of the assignable types need explicit conversion
   * to the target types. i.e., Decimal expression should be converted to Integer
   * before it is assigned to the Integer type Lvalue(In Java, Decimal can not be assigned to
   * Integer directly).
   *
   * 在SqlTypeAssignmentRule中，一些规则决定一种类型是否可以赋值给另一种类型。
   * 基于这些规则，函数可以接受可赋值类型的参数。
   *
   * <p>例如，Long类型的函数操作数可以接受Integer作为输入。
   * 详情参见org.apache.calcite.sql.SqlUtil#filterRoutinesByParameterType()。
   *
   * <p>在查询执行期间，一些可赋值类型需要显式转换为目标类型。
   * 即，Decimal表达式应该在赋值给Integer类型左值之前转换为Integer
   * （在Java中，Decimal不能直接赋值给Integer）。
   *
   * @param targetTypes Formal operand types declared for the function arguments 函数参数声明的形式操作数类型
   * @param arguments Input expressions to the function 函数的输入表达式
   * @return Input expressions with probable type conversion 带有可能类型转换的输入表达式
   */
  static List<Expression> convertAssignableTypes(Class<?>[] targetTypes, // 静态方法：转换可赋值类型
      List<Expression> arguments) { // 参数：参数类型数组和表达式列表
    final List<Expression> list = new ArrayList<>(); // 创建结果列表
    if (targetTypes.length == arguments.size()) { // 如果类型数量等于表达式数量
      for (int i = 0; i < arguments.size(); i++) { // 遍历所有表达式
        list.add(convertAssignableType(arguments.get(i), targetTypes[i])); // 逐个转换
      }
    } else { // 如果数量不匹配（可能是处理可变参数）
      int j = 0; // 类型索引
      for (Expression argument : arguments) { // 遍历所有表达式
        Class<?> type; // 目标类型变量
        if (!targetTypes[j].isArray()) { // 如果当前类型不是数组
          type = targetTypes[j]; // 直接使用该类型
          j++; // 移动到下一个类型
        } else { // 如果是数组类型（可变参数）
          type = targetTypes[j].getComponentType(); // 获取数组元素类型
        }
        list.add(convertAssignableType(argument, type)); // 转换并添加
      }
    }
    return list; // 返回转换后的列表
  }

  /**
   * Handles decimal type specifically with explicit type conversion.
   * 专门处理Decimal类型，进行显式类型转换。
   *
   * @param argument 参数表达式
   * @param targetType 目标类型
   * @return 转换后的表达式
   */
  private static Expression convertAssignableType( // 私有静态方法：转换可赋值类型
      Expression argument, // 参数：参数表达式
      Type targetType) { // 参数：目标类型
    if (targetType != BigDecimal.class) { // 如果目标不是BigDecimal
      return argument; // 直接返回
    }
    return convert(argument, targetType); // 否则进行转换
  }

  /**
   * A more powerful version of
   * {@link org.apache.calcite.linq4j.tree.Expressions#call(Type, String, Iterable)}.
   * Tries best effort to convert the
   * accepted arguments to match parameter type.
   *
   * 是org.apache.calcite.linq4j.tree.Expressions#call的更强大版本。
   * 尽最大努力将接受的参数转换为匹配参数类型。
   *
   * @param targetExpression Target expression, or null if method is static 目标表达式，如果方法是静态则为null
   * @param clazz Class against which method is invoked 调用方法的类
   * @param methodName Name of method 方法名
   * @param arguments Argument expressions 参数表达式列表
   *
   * @return MethodCallExpression that call the given name method 调用给定名称方法的方法调用表达式
   * @throws RuntimeException if no suitable method found 如果找不到合适的方法则抛出运行时异常
   */
  public static MethodCallExpression call(@Nullable Expression targetExpression, // 公共静态方法：智能方法调用
      Class clazz, // 参数：目标类
      String methodName, // 参数：方法名
      List<? extends Expression> arguments) { // 参数：参数表达式列表
    Class[] argumentTypes = Types.toClassArray(arguments); // 将参数表达式转换为类型数组
    try { // 尝试精确匹配
      Method candidate = clazz.getMethod(methodName, argumentTypes); // 获取精确匹配的方法
      return Expressions.call(targetExpression, candidate, arguments); // 调用方法
    } catch (NoSuchMethodException e) { // 捕获无方法异常
      for (Method method : clazz.getMethods()) { // 遍历所有公共方法
        if (method.getName().equals(methodName)) { // 如果方法名匹配
          final boolean varArgs = method.isVarArgs(); // 判断是否是可变参数方法
          final Class<?>[] parameterTypes = method.getParameterTypes(); // 获取参数类型数组
          if (Types.allAssignable(varArgs, parameterTypes, argumentTypes)) { // 如果所有参数都可赋值
            return Expressions.call(targetExpression, method, arguments); // 调用方法
          }
          // fall through
          // 继续尝试类型转换匹配
          final List<? extends Expression> typeMatchedArguments = // 尝试类型转换匹配
              matchMethodParameterTypes(varArgs, parameterTypes, arguments);
          if (typeMatchedArguments != null) { // 如果匹配成功