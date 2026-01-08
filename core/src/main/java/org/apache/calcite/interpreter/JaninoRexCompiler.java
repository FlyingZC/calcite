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
package org.apache.calcite.interpreter; // 包声明：该类属于解释器包，用于解释执行RelNode

import org.apache.calcite.DataContext; // 导入DataContext：数据上下文接口，提供运行时数据访问
import org.apache.calcite.adapter.enumerable.JavaRowFormat; // 导入JavaRowFormat：定义Java中行的表示格式（如数组、对象等）
import org.apache.calcite.adapter.enumerable.PhysTypeImpl; // 导入PhysTypeImpl：物理类型实现，描述Java中的类型表示
import org.apache.calcite.adapter.enumerable.RexToLixTranslator; // 导入RexToLixTranslator：将RexNode转换为Linq4j表达式的转换器
import org.apache.calcite.config.CalciteSystemProperty; // 导入CalciteSystemProperty：Calcite系统属性配置
import org.apache.calcite.jdbc.JavaTypeFactoryImpl; // 导入JavaTypeFactoryImpl：Java类型工厂实现，用于创建Java类型
import org.apache.calcite.linq4j.Ord; // 导入Ord：带索引的元素包装类，用于遍历时获取索引
import org.apache.calcite.linq4j.function.Function1; // 导入Function1：单参数函数接口
import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入BlockBuilder：代码块构建器，用于构建Java代码块
import org.apache.calcite.linq4j.tree.BlockStatement; // 导入BlockStatement：代码块语句，表示一个Java代码块
import org.apache.calcite.linq4j.tree.ClassDeclaration; // 导入ClassDeclaration：类声明，表示一个Java类的定义
import org.apache.calcite.linq4j.tree.Expression; // 导入Expression：表达式基类，表示Java表达式
import org.apache.calcite.linq4j.tree.Expressions; // 导入Expressions：表达式工具类，用于创建各种表达式
import org.apache.calcite.linq4j.tree.MemberDeclaration; // 导入MemberDeclaration：成员声明，表示类的成员（方法、字段等）
import org.apache.calcite.linq4j.tree.ParameterExpression; // 导入ParameterExpression：参数表达式，表示方法参数
import org.apache.calcite.linq4j.tree.Statement; // 导入Statement：语句基类，表示Java语句
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType：关系数据类型，表示Calcite中的类型系统
import org.apache.calcite.rex.RexBuilder; // 导入RexBuilder：Rex表达式构建器，用于构建RexNode
import org.apache.calcite.rex.RexNode; // 导入RexNode：行表达式节点，表示SQL表达式
import org.apache.calcite.rex.RexProgram; // 导入RexProgram：Rex程序，包含输入、输出和表达式的完整程序
import org.apache.calcite.rex.RexProgramBuilder; // 导入RexProgramBuilder：Rex程序构建器，用于构建RexProgram
import org.apache.calcite.sql.validate.SqlConformance; // 导入SqlConformance：SQL符合性接口，定义SQL方言标准
import org.apache.calcite.sql.validate.SqlConformanceEnum; // 导入SqlConformanceEnum：SQL符合性枚举，预定义的SQL标准
import org.apache.calcite.util.BuiltInMethod; // 导入BuiltInMethod：内置方法枚举，定义Calcite的内置方法
import org.apache.calcite.util.Util; // 导入Util：工具类，提供各种实用方法

import com.google.common.collect.ImmutableList; // 导入ImmutableList：Google Guava提供的不可变列表

import org.codehaus.commons.compiler.CompileException; // 导入CompileException：编译异常，Janino编译器抛出的异常
import org.codehaus.commons.compiler.CompilerFactoryFactory; // 导入CompilerFactoryFactory：编译器工厂工厂，用于创建编译器工厂
import org.codehaus.commons.compiler.IClassBodyEvaluator; // 导入IClassBodyEvaluator：类体求值器接口，用于动态编译类
import org.codehaus.commons.compiler.ICompilerFactory; // 导入ICompilerFactory：编译器工厂接口，用于创建编译器

import java.io.IOException; // 导入IOException：IO异常
import java.io.StringReader; // 导入StringReader：字符串读取器，用于从字符串读取
import java.lang.reflect.Modifier; // 导入Modifier：修饰符类，用于访问Java修饰符
import java.util.ArrayList; // 导入ArrayList：动态数组列表
import java.util.List; // 导入List：列表接口

import static java.util.Objects.requireNonNull; // 导入requireNonNull：静态方法，用于检查对象非空

/**
 * Compiles a scalar expression ({@link RexNode}) to an expression that
 * can be evaluated ({@link Scalar}) by generating a Java AST and compiling it
 * to a class using Janino.
 * 
 * 类作用说明：
 * JaninoRexCompiler是Calcite解释器中的关键编译器类，负责将RexNode（行表达式节点）编译成可执行的Scalar对象。
 * 
 * 核心功能：
 * 1. 将SQL表达式（以RexNode形式表示）转换为Java代码
 * 2. 使用Janino编译器在运行时动态编译生成的Java代码
 * 3. 生成实现了Scalar接口的类，该类可以执行表达式计算
 * 
 * 工作流程：
 * 1. 接收一个或多个RexNode表达式和输入行类型
 * 2. 构建RexProgram（Rex程序），包含所有表达式
 * 3. 将RexNode转换为Linq4j表达式树（Java AST）
 * 4. 生成实现Scalar.Producer接口的Java类
 * 5. 使用Janino编译器编译生成的Java类
 * 6. 返回可执行的Scalar.Producer实例
 * 
 * 技术细节：
 * - 使用Janino（轻量级Java编译器）在运行时编译生成的Java代码
 * - 生成的代码实现了Scalar.Producer接口，提供execute方法
 * - 支持多个表达式同时编译，结果存储在Object[]数组中
 * - 生成的类包含桥接方法，支持不同的execute签名
 * 
 * 性能特点：
 * - 动态编译避免了解释执行的性能开销
 * - 编译后的代码可以像普通Java代码一样执行
 * - 适用于需要频繁执行相同表达式的场景
 * 
 * 使用场景：
 * - Calcite解释器执行模式
 * - 需要动态执行SQL表达式的场景
 * - 当无法使用JIT编译或需要快速原型开发时
 */
public class JaninoRexCompiler implements Interpreter.ScalarCompiler { // 类声明：JaninoRexCompiler实现ScalarCompiler接口，用于编译标量表达式
  private final RexBuilder rexBuilder; // 成员变量：RexBuilder实例，用于构建Rex表达式，提供类型系统和表达式构建功能

  public JaninoRexCompiler(RexBuilder rexBuilder) { // 构造方法：创建JaninoRexCompiler实例
    this.rexBuilder = rexBuilder; // 初始化成员变量：保存传入的RexBuilder实例，用于后续构建表达式
  }

  @Override public Scalar.Producer compile(List<RexNode> nodes, // 方法签名：编译RexNode列表，返回Scalar.Producer
      RelDataType inputRowType) { // 参数：inputRowType表示输入行的数据类型
    final RexProgramBuilder programBuilder = // 创建RexProgramBuilder：用于构建包含所有表达式的Rex程序
        new RexProgramBuilder(inputRowType, rexBuilder); // 初始化：指定输入行类型和RexBuilder
    for (RexNode node : nodes) { // 遍历：处理每个需要编译的RexNode表达式
      programBuilder.addProject(node, null); // 添加投影：将表达式添加到程序中，null表示没有别名
    }
    final RexProgram program = programBuilder.getProgram(); // 获取RexProgram：构建完成的Rex程序，包含所有表达式

    final BlockBuilder list = new BlockBuilder(); // 创建代码块构建器：用于构建execute方法的主代码块
    final BlockBuilder staticList = new BlockBuilder().withRemoveUnused(false); // 创建静态代码块构建器：用于存放静态字段和方法，withRemoveUnused(false)表示不移除未使用的代码
    final ParameterExpression context_ = // 创建参数表达式：表示Context类型的参数，命名为"context"
        Expressions.parameter(Context.class, "context"); // Context包含运行时信息，如输入数据、变量等
    final ParameterExpression outputValues_ = // 创建参数表达式：表示Object[]类型的参数，命名为"outputValues"
        Expressions.parameter(Object[].class, "outputValues"); // outputValues用于存储表达式计算结果，每个表达式对应一个数组元素
    final JavaTypeFactoryImpl javaTypeFactory = // 创建Java类型工厂：用于在Java中表示Calcite类型
        new JavaTypeFactoryImpl(rexBuilder.getTypeFactory().getTypeSystem()); // 使用RexBuilder的类型系统初始化

    // public void execute(Context, Object[] outputValues) - 注释说明：生成的execute方法签名
    final RexToLixTranslator.InputGetter inputGetter = // 创建输入获取器：用于从Context中获取输入行的值
        new RexToLixTranslator.InputGetterImpl( // InputGetterImpl实现：从指定表达式获取输入
            Expressions.field(context_, // 访问context对象的values字段：values字段存储输入行数据
                BuiltInMethod.CONTEXT_VALUES.field), // BuiltInMethod.CONTEXT_VALUES定义了values字段的访问方式
            PhysTypeImpl.of(javaTypeFactory, inputRowType, // 创建物理类型：描述输入行在Java中的表示方式
                JavaRowFormat.ARRAY, false)); // 使用数组格式表示行，false表示非装箱
    final Function1<String, RexToLixTranslator.InputGetter> correlates = a0 -> { // 创建相关变量获取器：用于处理相关子查询（当前不支持）
      throw new UnsupportedOperationException(); // 抛出异常：JaninoRexCompiler不支持相关变量
    };
    final Expression root = // 创建根表达式：访问context的root字段，root是DataContext根对象
        Expressions.field(context_, BuiltInMethod.CONTEXT_ROOT.field); // CONTEXT_ROOT定义了root字段的访问方式
    final SqlConformance conformance = // 创建SQL符合性：定义SQL方言标准
        SqlConformanceEnum.DEFAULT; // TODO: get this from implementor - 使用默认SQL标准，未来可以从实现器获取
    final List<Expression> expressionList = // 翻译表达式：将RexProgram中的所有RexNode转换为Java表达式
        RexToLixTranslator.translateProjects(program, javaTypeFactory, // translateProjects：将投影表达式转换为Java表达式
            conformance, list, staticList, null, root, inputGetter, correlates); // 参数：程序、类型工厂、SQL标准、代码块、静态代码块、转换器、根、输入获取器、相关变量获取器
    Ord.forEach(expressionList, (expression, i) -> // 遍历表达式列表：将每个表达式赋值到outputValues数组的对应位置
        list.add( // 添加语句到代码块
            Expressions.statement( // 创建语句：将表达式包装成语句
                Expressions.assign( // 创建赋值表达式：将expression的结果赋值给outputValues[i]
                    Expressions.arrayIndex(outputValues_, // 访问数组元素：outputValues[i]
                        Expressions.constant(i)), // 常量索引i
                    expression)))); // 要赋值的表达式
    return baz(context_, outputValues_, list.toBlock(), // 调用baz方法：生成并编译最终的Scalar.Producer实例
        staticList.toBlock().statements); // 传递静态代码块的语句列表
  }

  /** Given a method that implements {@link Scalar#execute(Context, Object[])},
   * adds a bridge method that implements {@link Scalar#execute(Context)}, and
   * compiles.
   * 
   * 方法作用说明：
   * baz方法是代码生成的核心方法，负责：
   * 1. 构建实现Scalar.Producer接口的Java类
   * 2. 添加桥接方法以支持不同的execute签名
   * 3. 使用Janino编译器编译生成的Java类
   * 4. 返回可执行的Scalar.Producer实例
   * 
   * 参数说明：
   * - context_: Context参数表达式，表示运行时上下文
   * - outputValues_: Object[]参数表达式，用于存储输出结果
   * - block: BlockStatement，包含execute方法的主要逻辑
   * - declList: List<Statement>，静态声明列表（如静态字段、方法等）
   * 
   * 生成的类结构：
   * 1. 外部类（名为"Buzz"）：实现Scalar.Producer接口
   * 2. 内部类：实现Scalar接口，包含实际的execute逻辑
   * 3. 桥接方法：apply(DataContext)和apply(Object)
   * 
   * 方法签名：
   * - public Scalar apply(DataContext root): 创建并返回Scalar实例
   * - public Object apply(Object root): 桥接方法，调用apply(DataContext)
   * - public void execute(Context, Object[] outputValues): 执行表达式，将结果存储在数组中
   * - public Object execute(Context): 单值版本的execute方法
   */
  static Scalar.Producer baz(ParameterExpression context_, // 参数：context_是Context类型的参数表达式
      ParameterExpression outputValues_, BlockStatement block, // 参数：outputValues_是输出数组参数，block是execute方法的代码块
      List<Statement> declList) { // 参数：declList是静态声明列表，包含辅助方法和字段
    final List<MemberDeclaration> declarations = new ArrayList<>(); // 创建成员声明列表：用于存放外部类的所有成员声明
    final List<MemberDeclaration> innerDeclarations = new ArrayList<>(); // 创建内部成员声明列表：用于存放内部Scalar类的所有成员声明

    // public Scalar apply(DataContext root) { - 注释说明：生成的apply方法签名
    //   <<staticList>> - 静态声明，如辅助方法和字段
    //   return new Scalar() { - 返回内部Scalar类实例
    //     <<inner declarations>> - 内部类的成员声明
    //   };
    // }
    final List<Statement> statements = new ArrayList<>(declList); // 创建语句列表：复制静态声明到语句列表
    statements.add( // 添加语句：返回内部Scalar类实例
        Expressions.return_(null, // 创建return语句：返回新创建的Scalar对象
            Expressions.new_(Scalar.class, ImmutableList.of(), // 创建new表达式：实例化Scalar类，无构造参数
                innerDeclarations))); // 添加内部类的成员声明
    declarations.add( // 添加方法声明：将apply方法添加到外部类
        Expressions.methodDecl(Modifier.PUBLIC, Scalar.class, // 声明方法：public修饰符，返回Scalar类型
            BuiltInMethod.FUNCTION_APPLY.method.getName(), // 方法名：apply
            ImmutableList.of(DataContext.ROOT), // 参数列表：DataContext.ROOT常量作为参数
            Expressions.block(statements))); // 方法体：包含静态声明和return语句

    // (bridge method) - 注释说明：桥接方法，用于支持泛型擦除
    // public Object apply(Object root) { - 桥接方法签名，参数类型为Object
    //   return this.apply((DataContext) root); - 将Object强转为DataContext，调用apply(DataContext)
    // }
    final ParameterExpression objectRoot = // 创建参数表达式：Object类型的root参数
        Expressions.parameter(Object.class, "root"); // 参数名为"root"
    declarations.add( // 添加桥接方法声明
        Expressions.methodDecl(Modifier.PUBLIC, Object.class, // 声明方法：public修饰符，返回Object类型
            BuiltInMethod.FUNCTION_APPLY.method.getName(), // 方法名：apply（与上面的方法同名，参数类型不同，实现重载）
            ImmutableList.of(objectRoot), // 参数列表：objectRoot参数
            Expressions.block( // 方法体
                Expressions.return_(null, // return语句
                    Expressions.call( // 方法调用表达式：调用apply(DataContext)方法
                        Expressions.parameter(Scalar.Producer.class, "this"), // 调用对象：this（当前Scalar.Producer实例）
                        BuiltInMethod.FUNCTION_APPLY.method, // 调用的方法：apply方法
                        Expressions.convert_(objectRoot, // 类型转换：将objectRoot转换为DataContext类型
                            DataContext.class)))))); // 目标类型：DataContext.class

    // public void execute(Context, Object[] outputValues) - 注释说明：内部Scalar类的execute方法
    innerDeclarations.add( // 添加方法声明到内部类
        Expressions.methodDecl(Modifier.PUBLIC, void.class, // 声明方法：public修饰符，返回void类型
            BuiltInMethod.SCALAR_EXECUTE2.method.getName(), // 方法名：execute（带两个参数的版本）
            ImmutableList.of(context_, outputValues_), block)); // 参数列表：context和outputValues，方法体是传入的block

    // public Object execute(Context) - 注释说明：单值版本的execute方法
    final BlockBuilder builder = new BlockBuilder(); // 创建代码块构建器：用于构建单值execute方法的代码块
    final Expression values_ = // 创建数组表达式：创建长度为1的Object数组
        builder.append("values", // 添加表达式到代码块，命名为"values"
            Expressions.newArrayBounds(Object.class, 1, // 创建数组：Object类型，维度1
                Expressions.constant(1))); // 数组长度：1
    builder.add( // 添加语句：调用双参数execute方法
        Expressions.statement( // 创建语句
            Expressions.call( // 方法调用表达式：调用execute(Context, Object[])方法
                Expressions.parameter(Scalar.class, "this"), // 调用对象：this（当前Scalar实例）
                BuiltInMethod.SCALAR_EXECUTE2.method, context_, values_))); // 方法、context参数、values数组参数
    builder.add( // 添加语句：返回数组的第一个元素
        Expressions.return_(null, // return语句
            Expressions.arrayIndex(values_, Expressions.constant(0)))); // 访问values[0]并返回
    innerDeclarations.add( // 添加方法声明到内部类
        Expressions.methodDecl(Modifier.PUBLIC, Object.class, // 声明方法：public修饰符，返回Object类型
            BuiltInMethod.SCALAR_EXECUTE1.method.getName(), // 方法名：execute（单参数版本）
            ImmutableList.of(context_), builder.toBlock())); // 参数列表：context参数，方法体是builder构建的代码块

    final ClassDeclaration classDeclaration = // 创建类声明：定义实现Scalar.Producer接口的类
        Expressions.classDecl(Modifier.PUBLIC, "Buzz", null, // 类声明：public修饰符，类名"Buzz"，无父类
            ImmutableList.of(Scalar.Producer.class), declarations); // 实现接口：Scalar.Producer，成员声明列表
    String s = Expressions.toString(declarations, "\n", false); // 将声明转换为字符串：生成Java源代码，使用换行符分隔，不缩进
    if (CalciteSystemProperty.DEBUG.value()) { // 检查调试标志：如果开启了调试模式
      Util.debugCode(System.out, s); // 输出生成的代码：将生成的Java代码打印到控制台
    }
    try { // 尝试编译生成的代码
      return getScalar(classDeclaration, s); // 调用getScalar方法：编译并返回Scalar.Producer实例
    } catch (CompileException | IOException e) { // 捕获编译异常或IO异常
      throw new RuntimeException(e); // 包装为运行时异常并抛出
    }
  }

  /**
   * 方法作用说明：
   * getScalar方法负责使用Janino编译器编译生成的Java类，并创建实例。
   * 
   * 技术细节：
   * 1. 获取Janino编译器工厂
   * 2. 创建类体求值器（IClassBodyEvaluator）
   * 3. 配置编译选项（类名、接口、类加载器等）
   * 4. 编译生成的Java源代码
   * 5. 创建并返回编译后的类实例
   * 
   * 性能优化：
   * - 支持调试信息生成（行号、变量名等）
   * - 使用父类加载器，避免类加载冲突
   * - 动态编译，无需预先编译
   * 
   * 异常处理：
   * - CompileException：编译错误（语法错误、类型错误等）
   * - IOException：IO错误（读取源代码失败）
   * 
   * 参数说明：
   * - expr: ClassDeclaration，类声明对象，包含类名等信息
   * - s: String，生成的Java源代码字符串
   * 
   * 返回值：
   * - Scalar.Producer：编译后的类实例，实现了Scalar.Producer接口
   */
  static Scalar.Producer getScalar(ClassDeclaration expr, String s) // 方法签名：编译类声明并返回Scalar.Producer实例
      throws CompileException, IOException { // 声明抛出的异常：编译异常和IO异常
    ICompilerFactory compilerFactory; // 声明编译器工厂：用于创建编译器
    ClassLoader classLoader = // 获取类加载器：用于加载编译后的类
        requireNonNull(JaninoRexCompiler.class.getClassLoader(), "classLoader"); // 获取当前类的类加载器，如果为null则抛出NullPointerException
    try { // 尝试获取编译器工厂
      compilerFactory = CompilerFactoryFactory.getDefaultCompilerFactory(classLoader); // 获取默认编译器工厂：使用Janino实现
    } catch (Exception e) { // 捕获获取编译器工厂时的异常
      throw new IllegalStateException( // 抛出非法状态异常：无法实例化Java编译器
          "Unable to instantiate java compiler", e); // 异常消息和原因
    }
    IClassBodyEvaluator cbe = compilerFactory.newClassBodyEvaluator(); // 创建类体求值器：用于编译类体
    cbe.setClassName(expr.name); // 设置类名：使用类声明中的名称（"Buzz"）
    cbe.setImplementedInterfaces(new Class[] {Scalar.Producer.class}); // 设置实现的接口：Scalar.Producer接口
    cbe.setParentClassLoader(classLoader); // 设置父类加载器：使用当前类的类加载器
    if (CalciteSystemProperty.DEBUG.value()) { // 检查调试标志：如果开启了调试模式
      // Add line numbers to the generated janino class - 注释说明：为生成的Janino类添加行号信息
      cbe.setDebuggingInformation(true, true, true); // 设置调试信息：行号、源文件、变量信息
    }
    return (Scalar.Producer) cbe.createInstance(new StringReader(s)); // 编译并创建实例：从字符串读取源代码，编译并返回实例
  }
}
