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
package org.apache.calcite.adapter.enumerable;

import org.apache.calcite.DataContext;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.function.Function1;
import org.apache.calcite.linq4j.tree.BlockBuilder;
import org.apache.calcite.linq4j.tree.BlockStatement;
import org.apache.calcite.linq4j.tree.Blocks;
import org.apache.calcite.linq4j.tree.ClassDeclaration;
import org.apache.calcite.linq4j.tree.ConditionalStatement;
import org.apache.calcite.linq4j.tree.ConstantExpression;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.ExpressionType;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.calcite.linq4j.tree.FunctionExpression;
import org.apache.calcite.linq4j.tree.GotoStatement;
import org.apache.calcite.linq4j.tree.MemberDeclaration;
import org.apache.calcite.linq4j.tree.MethodCallExpression;
import org.apache.calcite.linq4j.tree.NewArrayExpression;
import org.apache.calcite.linq4j.tree.NewExpression;
import org.apache.calcite.linq4j.tree.ParameterExpression;
import org.apache.calcite.linq4j.tree.Primitive;
import org.apache.calcite.linq4j.tree.Statement;
import org.apache.calcite.linq4j.tree.Types;
import org.apache.calcite.linq4j.tree.UnaryExpression;
import org.apache.calcite.linq4j.tree.VisitorImpl;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.runtime.Bindable;
import org.apache.calcite.sql.SqlExplainLevel;
import org.apache.calcite.sql.validate.SqlConformance;
import org.apache.calcite.sql.validate.SqlConformanceEnum;
import org.apache.calcite.util.BuiltInMethod;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Equivalence;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Subclass of {@link org.apache.calcite.plan.RelImplementor} for relational
 * operators of {@link EnumerableConvention} calling convention.
 * {@link org.apache.calcite.plan.RelImplementor}的子类,用于实现使用{@link EnumerableConvention}调用约定的关系运算符
 * 
 * 这个类是Calcite框架中将关系代数树(RelNode树)转换为可执行的LINQ表达式的核心实现器
 * 主要功能:
 * 1. 将EnumerableConvention约定的关系算子树转换为Java代码
 * 2. 生成实现Bindable接口的类,该类可以返回Enumerable<Row>对象
 * 3. 管理相关变量(correlation variables)和参数缓存
 * 4. 生成合成类型(synthetic record types)的类定义
 * 
 * 工作原理:
 * - 从根节点开始递归调用每个RelNode的implement方法
 * - 每个RelNode返回一个Result对象,包含Java表达式块和物理类型信息
 * - 最终生成一个实现了Bindable接口的类,该类包含bind()方法返回Enumerable
 */
public class EnumerableRelImplementor extends JavaRelImplementor {
  // 内部参数映射表,用于存储需要在运行时传递的参数(如常量、集合等)
  // 键是参数名,值是参数的实际值
  // 这些参数会在生成的代码开始部分从DataContext中获取
  public final Map<String, Object> map;
  
  // 相关变量(correlation variables)映射表
  // 用于在子查询中引用外部查询的变量
  // 键是相关变量名,值是InputGetter对象,用于获取该变量的值
  private final Map<String, RexToLixTranslator.InputGetter> corrVars =
      new HashMap<>();
  
  // 身份等价性对象,用于基于对象身份(==)而非equals()进行相等性判断
  // 用于stashedParameters的key,确保相同对象只缓存一次
  private static final Equivalence<Object> IDENTITY = Equivalence.identity();
  
  // 缓存的参数映射表
  // 结合了IdentityHashMap和LinkedHashMap的特性:
  // - 使用IdentityHashMap的语义(基于对象身份而非equals)
  // - 使用LinkedHashMap保证确定性顺序(便于调试和一致性)
  // 键是Equivalence.Wrapper包装的对象,值是参数表达式
  // 用于在生成的代码中引用非字面量的"编译时常量"
  private final Map<Equivalence.Wrapper<Object>, ParameterExpression> stashedParameters =
      new LinkedHashMap<>();

  // 所有相关变量的获取器,通过方法引用实现
  // 这是一个函数,输入变量名,返回对应的InputGetter
  // 用于在RexToLixTranslator中访问所有相关变量
  @SuppressWarnings("methodref.receiver.bound.invalid")
  protected final Function1<String, RexToLixTranslator.InputGetter> allCorrelateVariables =
      this::getCorrelVariableGetter;

  /**
   * 构造函数,创建一个EnumerableRelImplementor实例
   * 
   * @param rexBuilder Rex表达式构建器,用于创建RexNode对象
   * @param internalParameters 内部参数映射表,用于存储需要在运行时传递的参数
   *                          这些参数会被放入map字段中
   */
  public EnumerableRelImplementor(RexBuilder rexBuilder,
      Map<String, Object> internalParameters) {
    super(rexBuilder); // 调用父类JavaRelImplementor的构造函数,初始化rexBuilder
    this.map = internalParameters; // 保存内部参数映射表
  }

  /**
   * 访问并实现子节点
   * 
   * 这个方法是实现器模式的核心,用于递归地实现关系算子树的子节点
   * 
   * @param parent 父节点关系算子(可为null,表示没有父节点)
   * @param ordinal 子节点在父节点输入列表中的序号(0-based)
   * @param child 要实现的子节点关系算子
   * @param prefer 实现时首选的行格式(ARRAY、SCALAR等)
   * @return 子节点的实现结果,包含生成的代码块和物理类型信息
   */
  public EnumerableRel.Result visitChild(
      EnumerableRel parent,
      int ordinal,
      EnumerableRel child,
      EnumerableRel.Prefer prefer) {
    // 如果有父节点,则断言子节点确实是父节点在指定序号处的输入
    if (parent != null) {
      assert child == parent.getInputs().get(ordinal);
    }
    // 调用子节点的implement方法,传入当前实现器和首选格式
    // 这会递归地实现整个子树
    return child.implement(this, prefer);
  }

  /**
   * 实现根节点,生成完整的可执行类
   * 
   * 这是实现过程的入口点,负责:
   * 1. 调用根节点的implement方法生成初始结果
   * 2. 根据首选项(ARRAY/SCALAR)调整结果格式
   * 3. 注册所有合成类型(synthetic types)
   * 4. 生成参数声明语句(从DataContext获取缓存的参数)
   * 5. 生成bind()和getElementType()方法
   * 6. 返回完整的类声明
   * 
   * @param rootRel 根节点关系算子
   * @param prefer 首选的行格式(ARRAY、SCALAR等)
   * @return 生成的类声明,实现了Bindable接口
   */
  public ClassDeclaration implementRoot(EnumerableRel rootRel,
      EnumerableRel.Prefer prefer) {
    // 调用根节点的implement方法,获取初始实现结果
    EnumerableRel.Result result;
    try {
      result = rootRel.implement(this, prefer);
    } catch (RuntimeException e) {
      // 如果实现过程中出现异常,包装成更详细的异常信息
      // 包含关系算子树的完整表示,便于调试
      IllegalStateException ex = new IllegalStateException("Unable to implement "
          + RelOptUtil.toString(rootRel, SqlExplainLevel.ALL_ATTRIBUTES));
      ex.addSuppressed(e); // 将原始异常作为被抑制的异常添加
      throw ex;
    }
    // 根据首选项调整结果格式
    switch (prefer) {
    case ARRAY:
      // 如果首选ARRAY格式,且当前结果已经是ARRAY格式且只有一列
      // 则转换为SCALAR格式(去掉数组包装)
      if (result.physType.getFormat() == JavaRowFormat.ARRAY
          && rootRel.getRowType().getFieldCount() == 1) {
        BlockBuilder bb = new BlockBuilder(); // 创建新的代码块构建器
        Expression e = null; // 用于存储goto语句的表达式
        // 遍历结果块中的所有语句
        for (Statement statement : result.block.statements) {
          if (statement instanceof GotoStatement) {
            // 如果是goto语句(通常是return语句),提取其表达式
            final GotoStatement gotoStatement = (GotoStatement) statement;
            e =
                bb.append("v",
                    requireNonNull(gotoStatement.expression, "expression"));
          } else {
            // 其他语句直接添加到新块中
            bb.add(statement);
          }
        }
        // 如果找到了goto语句的表达式
        if (e != null) {
          // 调用SLICE0方法提取数组的第一个元素,转换为标量
          bb.add(
              Expressions.return_(null,
                  Expressions.call(null, BuiltInMethod.SLICE0.method, e)));
        }
        // 创建新的结果对象,格式改为SCALAR
        result =
            new EnumerableRel.Result(bb.toBlock(), result.physType,
                JavaRowFormat.SCALAR);
      }
      break;
    default:
      break;
    }

    // 创建成员声明列表,用于存储类的所有成员(字段、方法等)
    final List<MemberDeclaration> memberDeclarations = new ArrayList<>();
    // 使用TypeRegistrar注册所有在结果块中使用的合成类型
    // 这会生成合成记录类型的类定义(如字段、构造函数、equals、hashCode等)
    new TypeRegistrar(memberDeclarations).go(result);

    // 生成缓存参数的声明语句
    // 这些语句会生成类似这样的代码:
    // final Integer v1stashed = (Integer) root.get("v1stashed")
    // 用于从DataContext中获取之前通过stash()方法缓存的参数
    final Collection<Statement> stashed =
        Collections2.transform(stashedParameters.values(),
            input -> Expressions.declare(Modifier.FINAL, input,
                Expressions.convert_(
                    Expressions.call(DataContext.ROOT,
                        BuiltInMethod.DATA_CONTEXT_GET.method,
                        Expressions.constant(input.name)),
                    input.type)));

    // 创建最终的代码块,包含参数声明和结果块中的语句
    final BlockStatement block =
        Expressions.block(
            Iterables.concat(stashed, result.block.statements));
    
    // 添加bind()方法声明
    // 该方法是Bindable接口的核心方法,返回Enumerable<Row>
    memberDeclarations.add(
        Expressions.methodDecl(Modifier.PUBLIC, Enumerable.class,
            BuiltInMethod.BINDABLE_BIND.method.getName(),
            Expressions.list(DataContext.ROOT),
            block));
    
    // 添加getElementType()方法声明
    // 该方法返回结果集中元素的Java类型
    memberDeclarations.add(
        Expressions.methodDecl(Modifier.PUBLIC, Class.class,
            BuiltInMethod.TYPED_GET_ELEMENT_TYPE.method.getName(),
            ImmutableList.of(),
            Blocks.toFunctionBlock(
                Expressions.return_(null,
                    Expressions.constant(result.physType.getJavaRowType())))));
    
    // 返回最终的类声明
    // 类名为"Baz",实现了Bindable接口
    return Expressions.classDecl(Modifier.PUBLIC,
        "Baz",
        null,
        Collections.singletonList(Bindable.class),
        memberDeclarations);
  }

  /**
   * 为合成记录类型生成类声明
   * 
   * 这个静态方法为Calcite生成的合成记录类型(SyntheticRecordType)创建完整的Java类定义
   * 生成的类包含:
   * 1. 所有字段的声明
   * 2. 无参构造函数(避免字段过多导致的编译错误)
   * 3. equals()方法 - 用于对象相等性比较
   * 4. hashCode()方法 - 用于哈希表等数据结构
   * 5. compareTo()方法 - 实现Comparable接口,用于排序
   * 6. toString()方法 - 用于调试输出
   * 
   * @param type 合成记录类型,包含字段信息
   * @return 生成的类声明,包含所有必要的成员
   */
  private static ClassDeclaration classDecl(
      JavaTypeFactoryImpl.SyntheticRecordType type) {
    // 创建类声明,类名为type.getName(),修饰符为public static
    // 实现Serializable接口,支持序列化
    ClassDeclaration classDeclaration =
        Expressions.classDecl(
            Modifier.PUBLIC | Modifier.STATIC,
            type.getName(),
            null,
            ImmutableList.of(Serializable.class),
            new ArrayList<>());

    // 为每个字段生成声明语句
    // 生成的代码格式: public T0 f0;
    // where T0是字段类型,f0是字段名
    for (Types.RecordField field : type.getRecordFields()) {
      classDeclaration.memberDeclarations.add(
          Expressions.fieldDecl(
              field.getModifiers(), // 字段修饰符(如public)
              Expressions.parameter(
                  field.getType(), field.getName()), // 字段类型和名称
              null)); // 初始值为null
    }

    // 生成构造函数
    // 注意:这里生成无参构造函数,而不是有参构造函数
    // 原因:当字段数量过多时,有参构造函数可能导致编译错误(Java方法参数限制)
    // 生成的代码格式: public Foo() { }
    final BlockBuilder blockBuilder = new BlockBuilder(); // 构造函数的代码块构建器
    final List<ParameterExpression> parameters = new ArrayList<>(); // 构造函数参数列表(空)
    final ParameterExpression thisParameter =
        Expressions.parameter(type, "this"); // this参数

    // 添加无参构造函数声明
    classDeclaration.memberDeclarations.add(
        Expressions.constructorDecl(
            Modifier.PUBLIC,
            type,
            parameters,
            blockBuilder.toBlock()));

    // 生成equals()方法
    // 用于比较两个对象是否相等
    // 生成的代码格式:
    // public boolean equals(Object o) {
    //     if (this == o) return true;
    //     if (!(o instanceof MyClass)) return false;
    //     final MyClass that = (MyClass) o;
    //     return this.f0 == that.f0 && equal(this.f1, that.f1) ...
    // }
    final BlockBuilder blockBuilder2 = new BlockBuilder(); // equals方法的代码块构建器
    final ParameterExpression thatParameter =
        Expressions.parameter(type, "that"); // that参数,表示要比较的另一个对象
    final ParameterExpression oParameter =
        Expressions.parameter(Object.class, "o"); // o参数,equals方法的参数
    // 如果是同一个对象引用,直接返回true
    blockBuilder2.add(
        Expressions.ifThen(
            Expressions.equal(thisParameter, oParameter),
            Expressions.return_(null, Expressions.constant(true))));
    // 如果类型不匹配,返回false
    blockBuilder2.add(
        Expressions.ifThen(
            Expressions.not(
                Expressions.typeIs(oParameter, type)),
            Expressions.return_(null, Expressions.constant(false))));
    // 将o参数转换为正确类型
    blockBuilder2.add(
        Expressions.declare(
            Modifier.FINAL,
            thatParameter,
            Expressions.convert_(oParameter, type)));
    // 为每个字段生成比较条件
    final List<Expression> conditions = new ArrayList<>();
    for (Types.RecordField field : type.getRecordFields()) {
      // 基本类型使用==比较,对象类型使用Objects.equals()比较
      conditions.add(
          Primitive.is(field.getType())
              ? Expressions.equal(
                  Expressions.field(thisParameter, field.getName()),
                  Expressions.field(thatParameter, field.getName()))
              : Expressions.call(BuiltInMethod.OBJECTS_EQUAL.method,
                  Expressions.field(thisParameter, field.getName()),
                  Expressions.field(thatParameter, field.getName())));
    }
    // 所有条件用&&连接
    blockBuilder2.add(
        Expressions.return_(null, Expressions.foldAnd(conditions)));
    // 添加equals方法声明
    classDeclaration.memberDeclarations.add(
        Expressions.methodDecl(
            Modifier.PUBLIC,
            boolean.class,
            "equals",
            Collections.singletonList(oParameter),
            blockBuilder2.toBlock()));

    // 生成hashCode()方法
    // 用于计算对象的哈希码,支持在哈希表等数据结构中使用
    // 生成的代码格式:
    // public int hashCode() {
    //   int h = 0;
    //   h = hash(h, f0);
    //   ...
    //   return h;
    // }
    final BlockBuilder blockBuilder3 = new BlockBuilder(); // hashCode方法的代码块构建器
    final ParameterExpression hParameter =
        Expressions.parameter(int.class, "h"); // 哈希值累加器
    final ConstantExpression constantZero =
        Expressions.constant(0); // 常量0
    // 初始化h为0
    blockBuilder3.add(
        Expressions.declare(0, hParameter, constantZero));
    // 为每个字段调用hash方法更新哈希值
    for (Types.RecordField field : type.getRecordFields()) {
      final Method method = BuiltInMethod.HASH.method;
      blockBuilder3.add(
          Expressions.statement(
              Expressions.assign(
                  hParameter,
                  Expressions.call(
                      method.getDeclaringClass(),
                      method.getName(),
                      ImmutableList.of(
                          hParameter,
                          Expressions.field(thisParameter, field))))));
    }
    // 返回最终的哈希值
    blockBuilder3.add(
        Expressions.return_(null, hParameter));
    // 添加hashCode方法声明
    classDeclaration.memberDeclarations.add(
        Expressions.methodDecl(
            Modifier.PUBLIC,
            int.class,
            "hashCode",
            Collections.emptyList(),
            blockBuilder3.toBlock()));

    // 生成compareTo()方法
    // 实现Comparable接口,用于对象排序
    // 生成的代码格式:
    // public int compareTo(MyClass that) {
    //   int c;
    //   c = compare(this.f0, that.f0);
    //   if (c != 0) return c;
    //   ...
    //   return 0;
    // }
    final BlockBuilder blockBuilder4 = new BlockBuilder(); // compareTo方法的代码块构建器
    final ParameterExpression cParameter =
        Expressions.parameter(int.class, "c"); // 比较结果
    final int mod = type.getRecordFields().size() == 1 ? Modifier.FINAL : 0; // 如果只有一个字段,c是final的
    // 声明比较结果变量
    blockBuilder4.add(
        Expressions.declare(mod, cParameter, null));
    // 如果比较结果不为0,立即返回(实现字典序比较)
    final ConditionalStatement conditionalStatement =
        Expressions.ifThen(
            Expressions.notEqual(cParameter, constantZero),
            Expressions.return_(null, cParameter));
    // 逐个字段比较
    for (Types.RecordField field : type.getRecordFields()) {
      MethodCallExpression compareCall;
      try {
        // 根据字段是否可空选择不同的比较方法
        final Method method = (field.nullable()
            ? BuiltInMethod.COMPARE_NULLS_LAST  // 可空类型使用COMPARE_NULLS_LAST
            : BuiltInMethod.COMPARE).method;    // 非空类型使用COMPARE
        compareCall =
            Expressions.call(method.getDeclaringClass(), method.getName(),
                Expressions.field(thisParameter, field),
                Expressions.field(thatParameter, field));
      } catch (RuntimeException e) {
        if (e.getCause() instanceof NoSuchMethodException) {
          // 如果找不到比较方法,跳过该字段
          // 原因:合成记录类型可能用于聚合计算的临时状态,不一定会作为排序键
          // 这种情况下跳过无法比较的字段是可以接受的
          continue;
        }
        throw e;
      }
      // 调用比较方法并更新c
      blockBuilder4.add(
          Expressions.statement(
              Expressions.assign(
                  cParameter,
                  compareCall)));
      // 如果c不为0,立即返回
      blockBuilder4.add(conditionalStatement);
    }
    // 所有字段都相等,返回0
    blockBuilder4.add(
        Expressions.return_(null, constantZero));
    // 添加compareTo方法声明
    classDeclaration.memberDeclarations.add(
        Expressions.methodDecl(
            Modifier.PUBLIC,
            int.class,
            "compareTo",
            Collections.singletonList(thatParameter),
            blockBuilder4.toBlock()));

    // 生成toString()方法
    // 用于生成对象的字符串表示,便于调试
    // 生成的代码格式:
    // public String toString() {
    //   return "{f0=" + f0 + ", f1=" + f1 ... + "}";
    // }
    final BlockBuilder blockBuilder5 = new BlockBuilder(); // toString方法的代码块构建器
    Expression expression5 = null; // 用于构建最终的字符串表达式
    // 逐个字段拼接字符串
    for (Types.RecordField field : type.getRecordFields()) {
      if (expression5 == null) {
        // 第一个字段,添加"{field="
        expression5 =
            Expressions.constant("{" + field.getName() + "=");
      } else {
        // 后续字段,添加", field="
        expression5 =
            Expressions.add(
                expression5,
                Expressions.constant(", " + field.getName() + "="));
      }
      // 添加字段值
      expression5 =
          Expressions.add(
              expression5,
              Expressions.field(thisParameter, field.getName()));
    }
    // 添加结束的"}"
    expression5 =
        expression5 == null
            ? Expressions.constant("{}")  // 没有字段的情况
            : Expressions.add(
                expression5,
                Expressions.constant("}"));
    // 返回字符串表达式
    blockBuilder5.add(
        Expressions.return_(
            null,
            expression5));
    // 添加toString方法声明
    classDeclaration.memberDeclarations.add(
        Expressions.methodDecl(
            Modifier.PUBLIC,
            String.class,
            "toString",
            Collections.emptyList(),
            blockBuilder5.toBlock()));

    // 返回完整的类声明
    return classDeclaration;
  }

  /**
   * 将值缓存到执行器中,用于在生成的代码中引用非字面量的"编译时常量"
   *
   * 这个方法的核心作用是:
   * 1. 允许在生成的代码中引用运行时的对象(如集合、自定义对象等)
   * 2. 相同的对象只缓存一次(基于对象身份,而非equals方法)
   * 3. 返回一个表达式,该表达式会在运行时从DataContext中获取缓存的值
   *
   * 工作原理:
   * - 对于简单字面量(字符串、数字等),直接返回常量表达式
   * - 对于复杂对象,将其存储在map和stashedParameters中
   * - 在生成的代码开始部分,会生成从DataContext获取这些值的语句
   * - 返回的参数表达式会在代码中引用这些值
   *
   * 使用示例:
   * 要将ArrayList传递给方法,可以使用:
   * Expressions.call(method, implementor.stash(arrayList))
   *
   * 注意事项:
   * - 输入值会在语句生命周期内一直保存在内存中
   * - 如果只使用值的一部分内容,考虑创建一个更小的持有者
   * - 相同对象(==)只缓存一次,避免重复
   *
   * @param input 要缓存的值(可以是任意对象)
   * @param clazz 值在使用时的Java类类型(必须是input的父类或相同类型)
   * @param <T> 值在使用时的Java类类型
   * @return 表达式,该表达式将在运行时代表input的值
   */
  public <T> Expression stash(T input, Class<? super T> clazz) {
    // 对于已知的final类和简单字面量,直接返回常量表达式
    // 这些类型可以直接嵌入到生成的代码中,不需要从DataContext获取
    if (input == null
        || input instanceof String
        || input instanceof Boolean
        || input instanceof Byte
        || input instanceof Short
        || input instanceof Integer
        || input instanceof Long
        || input instanceof Float
        || input instanceof Double) {
      return Expressions.constant(input, clazz);
    }
    // 使用IDENTITY等价性包装输入对象,确保基于对象身份(==)而非equals()进行去重
    final Equivalence.Wrapper<Object> key = IDENTITY.wrap(input);
    // 检查是否已经缓存过该对象
    ParameterExpression cached = stashedParameters.get(key);
    if (cached != null) {
      // 如果已缓存,直接返回缓存的参数表达式
      return cached;
    }
    // 生成唯一的变量名,格式为"v{序号}stashed"
    // 序号基于map的大小,确保唯一性
    // "stashed"后缀避免与其他变量名冲突
    final String name = "v" + map.size() + "stashed";
    // 创建参数表达式,类型为clazz,名称为name
    final ParameterExpression x = Expressions.variable(clazz, name);
    // 将值存储在map中,键为变量名,值为实际对象
    // 这些值会在生成的代码开始部分从DataContext中获取
    map.put(name, input);
    // 将参数表达式存储在stashedParameters中,键为对象的包装,值为参数表达式
    // 这样后续相同的对象可以直接返回缓存的参数表达式
    stashedParameters.put(key, x);
    // 返回参数表达式,用于在生成的代码中引用该值
    return x;
  }

  /**
   * 注册相关变量(correlation variable)
   *
   * 相关变量用于在子查询中引用外部查询的变量
   * 这个方法将相关变量的名称与获取值的函数关联起来
   *
   * 工作原理:
   * 1. 创建一个InputGetter函数,该函数知道如何从相关变量中获取指定字段的值
   * 2. 将这个函数存储在corrVars映射表中,以变量名为键
   * 3. 当需要在子查询中引用外部变量时,通过getCorrelVariableGetter获取这个函数
   *
   * @param name 相关变量的名称(在SQL中定义的名称)
   * @param pe 参数表达式,表示相关变量本身(通常是行对象)
   * @param corrBlock 代码块构建器,用于添加获取字段值的代码
   * @param physType 物理类型,包含字段信息和如何访问字段的方法
   */
  public void registerCorrelVariable(final String name,
      final ParameterExpression pe,
      final BlockBuilder corrBlock, final PhysType physType) {
    // 将相关变量名称与InputGetter函数关联
    // InputGetter是一个函数,输入是(字段列表,索引,存储类型),返回字段值的表达式
    corrVars.put(name, (list, index, storageType) -> {
      // 使用physType的fieldReference方法创建字段引用表达式
      // 该表达式知道如何从pe(相关变量)中获取指定索引的字段
      Expression fieldReference =
          physType.fieldReference(pe, index, storageType);
      // 将字段引用表达式添加到corrBlock中,并返回
      // corrBlock会在生成的代码中包含这些字段访问语句
      return corrBlock.append(name + "_" + index, fieldReference);
    });
  }

  /**
   * 清除相关变量
   *
   * 当相关变量的作用域结束时,需要清除它以释放资源
   * 这个方法从corrVars映射表中移除指定名称的相关变量
   *
   * @param name 要清除的相关变量名称
   * @throws AssertionError 如果相关变量不存在
   */
  public void clearCorrelVariable(String name) {
    // 断言相关变量已定义,否则抛出异常
    // 这有助于捕获编程错误,如清除未定义的变量
    assert corrVars.containsKey(name) : "Correlation variable " + name
        + " should be defined";
    // 从映射表中移除相关变量
    corrVars.remove(name);
  }

  /**
   * 获取相关变量的获取器
   *
   * 这个方法返回一个InputGetter函数,该函数知道如何从相关变量中获取字段的值
   * 主要用于在RexToLixTranslator中访问相关变量
   *
   * @param name 相关变量名称
   * @return InputGetter函数,用于获取相关变量的字段值
   * @throws AssertionError 如果相关变量不存在
   */
  public RexToLixTranslator.InputGetter getCorrelVariableGetter(String name) {
    // 断言相关变量已定义,否则抛出异常
    // 这有助于捕获编程错误,如访问未定义的变量
    assert corrVars.containsKey(name) : "Correlation variable " + name
        + " should be defined";
    // 返回相关变量的InputGetter函数
    return corrVars.get(name);
  }

  /**
   * 创建一个实现结果对象
   *
   * 这是一个工厂方法,用于创建EnumerableRel.Result实例
   * Result对象包含生成的代码块和物理类型信息
   *
   * @param physType 物理类型,描述结果集的Java类型和格式
   * @param block 生成的代码块,包含实现该关系算子的Java代码
   * @return Result对象,封装了代码块和物理类型
   */
  public EnumerableRel.Result result(PhysType physType, BlockStatement block) {
    // 创建并返回Result对象
    // 格式从PhysTypeImpl中提取
    return new EnumerableRel.Result(
        block, physType, ((PhysTypeImpl) physType).format);
  }

  /**
   * 获取SQL符合性配置
   *
   * SQL符合性定义了SQL方言的兼容性级别
   * 例如:是否支持某些特定的SQL语法、函数等
   *
   * @return SQL符合性配置,如果未指定则返回DEFAULT
   */
  @Override public SqlConformance getConformance() {
    // 从map中获取"_conformance"键对应的值
    // 如果不存在,返回默认的符合性配置
    return (SqlConformance) map.getOrDefault("_conformance",
        SqlConformanceEnum.DEFAULT);
  }

  /** Visitor that finds types in an {@link Expression} tree. */
  /**
   * 表达式树类型查找器
   * 
   * 这是一个访问器模式的实现,用于遍历表达式树并收集其中使用的所有类型
   * 主要用于注册合成类型(synthetic types),确保所有使用的类型都有对应的类定义
   * 
   * 工作原理:
   * - 继承VisitorImpl<Void>,实现访问者模式
   * - 重写各种表达式类型的visit方法,提取类型信息
   * - 将找到的类型添加到types集合中
   * - 被TypeRegistrar使用,用于生成必要的类定义
   * 
   * 使用场景:
   * 当生成的代码中使用了合成记录类型(如临时结果类型)时,
   * 需要为这些类型生成类定义(字段、构造函数、equals等)
   * TypeFinder负责在表达式中找出所有需要定义的类型
   */
  @VisibleForTesting
  static class TypeFinder extends VisitorImpl<Void> {
    // 类型集合,用于存储找到的所有类型
    // 这个集合是引用类型,在构造时传入,由调用者管理
    private final Collection<Type> types;

    /**
     * 构造函数
     * 
     * @param types 类型集合,用于存储找到的类型
     *               调用者负责创建和管理这个集合
     */
    TypeFinder(Collection<Type> types) {
      this.types = types;
    }

    /**
     * 访问NewExpression(对象创建表达式)
     * 
     * NewExpression表示创建新对象的表达式,如new MyClass()
     * 
     * @param newExpression 对象创建表达式
     * @return null(访问者模式的约定)
     */
    @Override public Void visit(NewExpression newExpression) {
      // 将创建的对象类型添加到集合中
      types.add(newExpression.type);
      // 继续访问子表达式
      return super.visit(newExpression);
    }

    /**
     * 访问NewArrayExpression(数组创建表达式)
     * 
     * NewArrayExpression表示创建数组的表达式,如new int[10]或new String[]{}
     * 这个方法会提取数组的元素类型(最内层的类型)
     * 
     * @param newArrayExpression 数组创建表达式
     * @return null(访问者模式的约定)
     */
    @Override public Void visit(NewArrayExpression newArrayExpression) {
      // 获取数组类型
      Type type = newArrayExpression.type;
      // 循环获取数组的元素类型,直到不是数组类型为止
      // 例如:对于String[][][],最终会得到String类型
      for (;;) {
        final Type componentType = Types.getComponentType(type);
        if (componentType == null) {
          // 如果没有组件类型,说明已经到达最内层类型
          break;
        }
        type = componentType;
      }
      // 将元素类型添加到集合中
      types.add(type);
      // 继续访问子表达式
      return super.visit(newArrayExpression);
    }

    /**
     * 访问ConstantExpression(常量表达式)
     * 
     * ConstantExpression表示常量值,如字符串、数字、null等
     * 
     * @param constantExpression 常量表达式
     * @return null(访问者模式的约定)
     */
    @Override public Void visit(ConstantExpression constantExpression) {
      // 获取常量的值
      final Object value = constantExpression.value;
      // 如果常量值本身是一个Type对象(如Class对象),添加到集合中
      if (value instanceof Type) {
        types.add((Type) value);
      }
      // 如果常量值是null,添加其声明的类型
      // 这对于null字面量很重要,因为需要知道null的类型
      if (value == null) {
        // null literal
        Type type = constantExpression.getType();
        types.add(type);
      }
      // 继续访问子表达式
      return super.visit(constantExpression);
    }

    /**
     * 访问FunctionExpression(函数表达式)
     * 
     * FunctionExpression表示lambda表达式或匿名函数
     * 
     * @param functionExpression 函数表达式
     * @return null(访问者模式的约定)
     */
    @Override public Void visit(FunctionExpression functionExpression) {
      // 获取函数的参数列表
      final List<ParameterExpression> list = functionExpression.parameterList;
      // 为每个参数添加其类型
      for (ParameterExpression pe : list) {
        types.add(pe.getType());
      }
      // 如果函数体为null,直接返回
      if (functionExpression.body == null) {
        return super.visit(functionExpression);
      }
      // 添加函数体的返回类型
      types.add(functionExpression.body.getType());
      // 继续访问子表达式
      return super.visit(functionExpression);
    }

    /**
     * 访问UnaryExpression(一元表达式)
     * 
     * UnaryExpression表示一元运算,如类型转换、取反等
     * 
     * @param unaryExpression 一元表达式
     * @return null(访问者模式的约定)
     */
    @Override public Void visit(UnaryExpression unaryExpression) {
      // 如果是类型转换表达式,添加转换后的类型
      if (unaryExpression.nodeType == ExpressionType.Convert) {
        types.add(unaryExpression.getType());
      }
      // 继续访问子表达式
      return super.visit(unaryExpression);
    }
  }

  /** Adds a declaration of each synthetic type found in a code block. */
  /**
   * 类型注册器
   * 
   * 这个类负责在代码块中查找所有合成类型(synthetic types),并为它们生成类定义
   * 合成类型是Calcite在运行时生成的记录类型,用于表示查询的中间结果
   * 
   * 工作流程:
   * 1. 使用TypeFinder遍历表达式树,收集所有使用的类型
   * 2. 对每个类型调用register方法,生成相应的类声明
   * 3. 对于合成记录类型,调用classDecl方法生成完整的类定义
   * 4. 对于参数化类型,递归注册其类型参数
   * 5. 将生成的类声明添加到memberDeclarations列表中
   * 
   * 使用场景:
   * 在implementRoot方法中,生成最终的类之前,
   * 需要确保所有使用的合成类型都有对应的类定义
   * TypeRegistrar负责完成这个任务
   */
  private static class TypeRegistrar {
    // 成员声明列表,用于存储生成的所有类定义
    // 这些声明会被添加到最终的类中
    private final List<MemberDeclaration> memberDeclarations;
    
    // 已处理的类型集合,用于避免重复注册
    // 使用HashSet确保快速查找
    private final Set<Type> seen = new HashSet<>();

    /**
     * 构造函数
     * 
     * @param memberDeclarations 成员声明列表,生成的类定义会添加到这里
     *                          这个列表由调用者创建和管理
     */
    TypeRegistrar(List<MemberDeclaration> memberDeclarations) {
      this.memberDeclarations = memberDeclarations;
    }

    /**
     * 注册一个类型
     * 
     * 这个方法会检查类型是否需要生成类定义,如果需要则生成并添加到memberDeclarations中
     * 
     * @param type 要注册的类型
     */
    private void register(Type type) {
      // 如果类型已经注册过,直接返回(避免重复)
      if (!seen.add(type)) {
        return;
      }
      // 如果是合成记录类型,生成完整的类定义
      // 合成记录类型是Calcite在运行时生成的,用于表示查询结果
      if (type instanceof JavaTypeFactoryImpl.SyntheticRecordType) {
        // 调用classDecl方法生成类定义,并添加到成员声明列表中
        memberDeclarations.add(
            classDecl((JavaTypeFactoryImpl.SyntheticRecordType) type));
      }
      // 如果是参数化类型(泛型),递归注册其类型参数
      // 例如:对于List<String>,需要注册String类型
      if (type instanceof ParameterizedType) {
        for (Type type1 : ((ParameterizedType) type).getActualTypeArguments()) {
          register(type1);
        }
      }
    }

    /**
     * 执行类型注册过程
     * 
     * 这是TypeRegistrar的主要入口方法,负责:
     * 1. 使用TypeFinder在结果块中查找所有类型
     * 2. 添加结果集的Java行类型
     * 3. 对每个类型调用register方法生成类定义
     * 
     * @param result 实现结果,包含代码块和物理类型信息
     */
    public void go(EnumerableRel.Result result) {
      // 创建类型集合,使用LinkedHashSet保持插入顺序
      final Set<Type> types = new LinkedHashSet<>();
      // 使用TypeFinder遍历结果块,收集所有使用的类型
      result.block.accept(new TypeFinder(types));
      // 添加结果集的Java行类型(这是最重要的类型,必须定义)
      types.add(result.physType.getJavaRowType());
      // 对每个类型调用register方法,生成类定义
      for (Type type : types) {
        register(type);
      }
    }
  }
}
