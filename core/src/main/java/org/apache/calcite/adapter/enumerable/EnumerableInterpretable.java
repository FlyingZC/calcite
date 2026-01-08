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
// 声明包名,该类属于org.apache.calcite.adapter.enumerable包,用于Enumerable适配器相关功能
package org.apache.calcite.adapter.enumerable;

// 导入DataContext类,用于提供查询执行时的上下文环境(如变量、时间等)
import org.apache.calcite.DataContext;
// 导入Helper类,用于异常包装和工具方法
import org.apache.calcite.avatica.Helper;
// 导入CalciteSystemProperty类,用于访问Calcite系统属性配置
import org.apache.calcite.config.CalciteSystemProperty;
// 导入Compiler接口,用于解释器编译器
import org.apache.calcite.interpreter.Compiler;
// 导入InterpretableConvention类,表示可解释调用约定
import org.apache.calcite.interpreter.InterpretableConvention;
// 导入InterpretableRel接口,表示可解释的关系表达式
import org.apache.calcite.interpreter.InterpretableRel;
// 导入Node接口,表示解释器执行节点
import org.apache.calcite.interpreter.Node;
// 导入Row类,表示一行数据
import org.apache.calcite.interpreter.Row;
// 导入Sink接口,表示数据接收器,用于处理输出数据
import org.apache.calcite.interpreter.Sink;
// 导入CalcitePrepare.SparkHandler类,用于Spark相关处理
import org.apache.calcite.jdbc.CalcitePrepare;
// 导入AbstractEnumerable抽象类,提供Enumerable的基础实现
import org.apache.calcite.linq4j.AbstractEnumerable;
// 导入Enumerable接口,表示可枚举的数据集合
import org.apache.calcite.linq4j.Enumerable;
// 导入Enumerator接口,表示枚举器,用于遍历数据
import org.apache.calcite.linq4j.Enumerator;
// 导入ClassDeclaration类,表示Java类声明(用于代码生成)
import org.apache.calcite.linq4j.tree.ClassDeclaration;
// 导入Expressions类,用于表达式操作和字符串转换
import org.apache.calcite.linq4j.tree.Expressions;
// 导入FieldDeclaration类,表示字段声明
import org.apache.calcite.linq4j.tree.FieldDeclaration;
// 导入VisitorImpl类,提供AST访问者模式的基类
import org.apache.calcite.linq4j.tree.VisitorImpl;
// 导入ConventionTraitDef类,定义调用约定特征
import org.apache.calcite.plan.ConventionTraitDef;
// 导入RelOptCluster类,表示关系优化集群,包含优化上下文
import org.apache.calcite.plan.RelOptCluster;
// 导入RelTraitSet类,表示关系特征集合
import org.apache.calcite.plan.RelTraitSet;
// 导入RelNode接口,表示关系表达式节点
import org.apache.calcite.rel.RelNode;
// 导入ConverterImpl类,提供关系转换器的基类实现
import org.apache.calcite.rel.convert.ConverterImpl;
// 导入ArrayBindable接口,表示可绑定的数组数据源
import org.apache.calcite.runtime.ArrayBindable;
// 导入Bindable接口,表示可绑定的数据源
import org.apache.calcite.runtime.Bindable;
// 导入Hook类,用于在特定点执行钩子函数(如调试、监控)
import org.apache.calcite.runtime.Hook;
// 导入Typed接口,表示有类型的数据
import org.apache.calcite.runtime.Typed;
// 导入Util类,提供通用工具方法
import org.apache.calcite.util.Util;

// 导入Guava的Cache接口,用于缓存功能
import com.google.common.cache.Cache;
// 导入Guava的CacheBuilder类,用于构建缓存
import com.google.common.cache.CacheBuilder;

// 导入可空注解,用于标记可能为null的类型
import org.checkerframework.checker.nullness.qual.Nullable;
// 导入编译异常类
import org.codehaus.commons.compiler.CompileException;
// 导入编译器工厂工厂类
import org.codehaus.commons.compiler.CompilerFactoryFactory;
// 导入编译器工厂接口
import org.codehaus.commons.compiler.ICompilerFactory;
// 导入简单编译器接口
import org.codehaus.commons.compiler.ISimpleCompiler;

// 导入反射相关的异常类
import java.lang.reflect.InvocationTargetException;
// 导入Modifier类,用于获取类成员的修饰符信息
import java.lang.reflect.Modifier;
// 导入List接口,用于列表集合
import java.util.List;
// 导入Map接口,用于键值对映射
import java.util.Map;
// 导入ExecutionException类,表示执行过程中的异常
import java.util.concurrent.ExecutionException;

// 导入Objects.requireNonNull静态方法,用于参数非空检查
import static java.util.Objects.requireNonNull;

/**
 * 关系表达式类,将可枚举(Enumerable)输入转换为可解释(Interpretable)调用约定
 *
 * 该类是Calcite查询优化和执行过程中的关键组件,负责在两种不同的执行约定之间进行转换:
 * 1. Enumerable约定:基于LINQ4j的枚举式执行,适合Java环境
 * 2. Interpretable约定:基于解释器的执行模式,适合调试和特殊场景
 *
 * 转换过程包括:
 * - 将EnumerableRel关系表达式转换为Bindable对象
 * - 通过动态代码生成创建可执行的Java类
 * - 使用Janino编译器编译生成的代码
 * - 支持缓存机制以避免重复编译
 * - 支持Spark分布式执行模式
 *
 * @see EnumerableConvention 可枚举约定
 * @see org.apache.calcite.interpreter.BindableConvention 可绑定约定
 */
// 定义EnumerableInterpretable类,继承ConverterImpl(转换器实现基类),实现InterpretableRel(可解释关系表达式)接口
// 这是适配器模式的应用,将Enumerable适配为Interpretable
public class EnumerableInterpretable extends ConverterImpl
    implements InterpretableRel {
  // 构造方法:创建一个EnumerableInterpretable实例
  // 参数cluster:关系优化集群,包含优化器上下文、Rex构建器等
  // 参数input:输入的关系表达式节点,通常是EnumerableRel类型
  protected EnumerableInterpretable(RelOptCluster cluster, RelNode input) {
    // 调用父类ConverterImpl的构造方法
    // ConventionTraitDef.INSTANCE:调用约定特征定义的单例
    // cluster.traitSetOf(InterpretableConvention.INSTANCE):创建只包含Interpretable约定的特征集,表示输出约定为Interpretable
    // input:输入的关系表达式
    super(cluster, ConventionTraitDef.INSTANCE,
        cluster.traitSetOf(InterpretableConvention.INSTANCE), input);
  }

  // 复制方法:根据给定的特征集和输入列表创建当前关系表达式的副本
  // 这是Calcite关系表达式树的标准方法,用于在优化过程中创建修改后的节点
  // 参数traitSet:新的特征集,通常用于修改调用约定或其他特征
  // 参数inputs:新的输入关系表达式列表
  // 返回值:新的EnumerableInterpretable实例
  @Override public EnumerableInterpretable copy(RelTraitSet traitSet,
      List<RelNode> inputs) {
    // 创建并返回新的EnumerableInterpretable实例
    // getCluster():获取当前节点的集群
    // sole(inputs):从输入列表中获取唯一的输入(因为该转换器只有一个输入)
    return new EnumerableInterpretable(getCluster(), sole(inputs));
  }

  // 实现方法:根据给定的解释器实现器创建解释器节点
  // 这是InterpretableRel接口的核心方法,用于将关系表达式转换为可执行的解释器节点
  // 参数implementor:解释器实现器,包含执行上下文(参数、编译器、数据上下文等)
  // 返回值:解释器节点Node,可以被执行以产生结果
  @Override public Node implement(final InterpreterImplementor implementor) {
    // 将输入的EnumerableRel转换为Bindable对象
    // implementor.internalParameters:内部参数映射,包含查询参数
    // implementor.spark:Spark处理器(可能为null,用于分布式执行)
    // (EnumerableRel) getInput():获取输入关系表达式并转换为EnumerableRel类型
    // EnumerableRel.Prefer.ARRAY:优先使用数组形式的数据处理
    final Bindable bindable =
        toBindable(implementor.internalParameters, implementor.spark,
            (EnumerableRel) getInput(), EnumerableRel.Prefer.ARRAY);
    // 将Bindable转换为ArrayBindable,确保每行数据都是Object[]数组形式
    // 这样可以统一处理单列和多列情况
    final ArrayBindable arrayBindable = box(bindable);
    // 绑定到数据上下文,创建可枚举的数据源
    // implementor.dataContext:数据上下文,提供执行环境信息
    final Enumerable<@Nullable Object[]> enumerable =
        arrayBindable.bind(implementor.dataContext);
    // 创建并返回EnumerableNode节点,该节点会遍历enumerable并将数据发送到sink
    // implementor.compiler:编译器,用于创建sink
    // this:当前的关系表达式,用于标识数据源
    return new EnumerableNode(enumerable, implementor.compiler, this);
  }

  /**
   * 缓存Bindable对象的静态常量,这些Bindable对象通过动态生成的Java类实例化
   *
   * <p>该缓存允许重用频繁出现的查询的Bindable对象。
   * 它用于避免编译和生成新类的开销,以及实例化对象的开销。
   *
   * 缓存机制说明:
   * 1. 键(String):生成的Java类的完整源代码字符串
   * 2. 值(Bindable):编译并实例化后的Bindable对象
   * 3. 缓存策略:基于Guava Cache,支持并发访问和大小限制
   *
   * 使用缓存的条件:
   * - 生成的代码不包含静态字段(通过StaticFieldDetector检测)
   * - 缓存最大大小配置不为0(BINDABLE_CACHE_MAX_SIZE)
   *
   * 不使用缓存的情况:
   * - 代码包含静态字段(可能导致线程安全问题)
   * - 缓存被禁用(大小为0)
   *
   * 性能影响:
   * - 命中缓存时,直接返回已编译的Bindable,避免编译开销
   * - 未命中时,需要编译和实例化新类
   */
  private static final Cache<String, Bindable> BINDABLE_CACHE =
      // 创建缓存构建器
      CacheBuilder.newBuilder()
          // 设置并发级别,允许指定数量的线程并发写入缓存
          // 从系统属性读取并发级别配置,默认值通常为4
          .concurrencyLevel(CalciteSystemProperty.BINDABLE_CACHE_CONCURRENCY_LEVEL.value())
          // 设置缓存的最大容量,超过后会根据LRU策略淘汰旧条目
          // 从系统属性读取最大大小配置,默认值通常为1000
          .maximumSize(CalciteSystemProperty.BINDABLE_CACHE_MAX_SIZE.value())
          // 构建缓存实例
          .build();

  // 静态方法:将EnumerableRel关系表达式转换为Bindable对象
  // 这是代码生成的核心入口点,负责将关系表达式树转换为可执行的Java代码
  // 参数parameters:查询参数映射,键为参数名,值为参数值
  // 参数spark:Spark处理器,如果启用则使用Spark编译,否则使用本地Janino编译
  // 参数rel:要转换的EnumerableRel关系表达式
  // 参数prefer:数据格式偏好(ARRAY或CUSTOM),影响代码生成策略
  // 返回值:编译后的Bindable对象,可以绑定到数据上下文执行
  public static Bindable toBindable(Map<String, Object> parameters,
      CalcitePrepare.@Nullable SparkHandler spark, EnumerableRel rel,
      EnumerableRel.Prefer prefer) {
    // 创建关系表达式实现器,用于将关系表达式树转换为Java代码
    // rel.getCluster().getRexBuilder():获取Rex表达式构建器,用于创建表达式
    // parameters:传入查询参数
    EnumerableRelImplementor relImplementor =
        new EnumerableRelImplementor(rel.getCluster().getRexBuilder(),
            parameters);

    // 实现根节点,生成Java类声明
    // 这会递归遍历整个关系表达式树,生成对应的Java方法实现
    // prefer:指定生成的代码偏好(数组或自定义)
    final ClassDeclaration expr = relImplementor.implementRoot(rel, prefer);
    // 将类声明转换为字符串形式的Java代码
    // expr.memberDeclarations:类成员声明列表
    // "\n":换行符分隔
    // false:不包含前导空格
    String s = Expressions.toString(expr.memberDeclarations, "\n", false);

    // 如果启用调试模式,将生成的Java代码打印到标准输出
    // 这对于调试代码生成问题非常有帮助
    if (CalciteSystemProperty.DEBUG.value()) {
      Util.debugCode(System.out, s);
    }

    // 运行JAVA_PLAN钩子,允许外部监听器获取生成的Java代码
    // 可用于代码分析、监控或日志记录
    Hook.JAVA_PLAN.run(s);

    try {
      // 如果Spark处理器存在且已启用,则使用Spark编译器编译代码
      // 这会生成可以在Spark集群上分布式执行的代码
      if (spark != null && spark.enabled()) {
        return spark.compile(expr, s);
      } else {
        // 否则使用本地Janino编译器编译代码
        // rel.getRowType().getFieldCount():获取输出字段数量,决定实现接口类型
        return getBindable(expr, s, rel.getRowType().getFieldCount());
      }
    } catch (Exception e) {
      // 如果编译失败,包装异常信息并重新抛出
      // 异常消息包含生成的Java代码,便于调试
      throw Helper.INSTANCE.wrap("Error while compiling generated Java code:\n"
          + s, e);
    }
  }

  // 静态方法:使用Janino编译器编译Java代码并创建Bindable实例
  // 这是代码编译的核心方法,处理动态类的编译和实例化
  // 参数expr:类声明对象,包含类名等信息
  // 参数classBody:Java类的完整源代码字符串(不包含类声明头部)
  // 参数fieldCount:输出字段数量,用于决定实现哪个接口
  // 返回值:编译并实例化后的Bindable对象
  // 异常:可能抛出编译、执行、类加载、反射调用等多种异常
  static Bindable getBindable(ClassDeclaration expr, String classBody, int fieldCount)
      throws CompileException, ExecutionException, ClassNotFoundException,
      InvocationTargetException, InstantiationException, IllegalAccessException {
    // 编译器工厂,用于创建编译器实例
    ICompilerFactory compilerFactory;
    // 获取类加载器,用于加载编译后的类
    // 使用当前类的类加载器,确保可以访问Calcite的所有类
    ClassLoader classLoader =
        requireNonNull(EnumerableInterpretable.class.getClassLoader(),
            "classLoader");
    try {
      // 获取默认的编译器工厂(Janino)
      // Janino是一个轻量级的Java编译器,可以在运行时编译Java代码
      compilerFactory = CompilerFactoryFactory.getDefaultCompilerFactory(classLoader);
    } catch (Exception e) {
      // 如果获取编译器工厂失败,抛出异常
      // 这通常表示Janino库不可用
      throw new IllegalStateException(
          "Unable to instantiate java compiler", e);
    }
    // 创建新的简单编译器实例
    final ISimpleCompiler compiler = compilerFactory.newSimpleCompiler();
    // 设置父类加载器,确保编译器可以访问应用程序的所有类
    compiler.setParentClassLoader(classLoader);
    // 构建完整的Java类源代码字符串
    // 包括类声明、实现的接口、类体
    final String s = "public final class " + expr.name + " implements "
        // 根据字段数量决定实现的接口
        // 如果只有1个字段,实现Bindable和Typed接口(返回单个标量值)
        // 如果有多个字段,实现ArrayBindable接口(返回数组)
        + (fieldCount == 1
          ? Bindable.class.getCanonicalName() + ", " + Typed.class.getCanonicalName()
          : ArrayBindable.class.getCanonicalName())
        + " {\n"  // 类体开始
        + classBody  // 类体内容(方法实现)
        + "\n"
        + "}";  // 类体结束

    // 如果启用调试模式,为编译器设置调试信息
    // 这会在编译后的类中包含行号信息,便于调试
    if (CalciteSystemProperty.DEBUG.value()) {
      // Add line numbers to the generated janino class
      // 三个true分别表示:行号、变量表、源文件信息
      compiler.setDebuggingInformation(true, true, true);
    }

    // 如果缓存启用(最大大小不为0),尝试使用缓存
    if (CalciteSystemProperty.BINDABLE_CACHE_MAX_SIZE.value() != 0) {
      // 创建静态字段检测器,用于检测生成的代码是否包含静态字段
      StaticFieldDetector detector = new StaticFieldDetector();
      // 遍历AST,检测是否包含静态字段
      expr.accept(detector);
      // 如果不包含静态字段,可以安全地使用缓存
      // 静态字段可能导致线程安全问题,因此包含静态字段的类不缓存
      if (!detector.containsStaticField) {
        // 尝试从缓存获取Bindable,如果不存在则编译并缓存
        // classBody作为缓存键,确保相同的代码只编译一次
        return BINDABLE_CACHE.get(classBody, () ->  compileToBindable(expr.name, s, compiler));
      }
    }
    // 如果禁用缓存或包含静态字段,直接编译
    return compileToBindable(expr.name, s, compiler);
  }

  // 静态方法:编译Java源代码并实例化Bindable对象
  // 参数className:要编译的类名
  // 参数s:完整的Java源代码字符串
  // 参数compiler:编译器实例
  // 返回值:编译并实例化后的Bindable对象
  // 异常:可能抛出编译、类加载、反射调用等异常
  private static Bindable<?> compileToBindable(String className, String s, ISimpleCompiler compiler)
      throws CompileException, ClassNotFoundException, InvocationTargetException,
      InstantiationException, IllegalAccessException {
    // 编译Java源代码字符串
    // cook方法会解析、编译并加载类
    compiler.cook(s);
    // 通过反射加载编译后的类并实例化
    // compiler.getClassLoader():获取编译器的类加载器
    // .loadClass(className):加载指定的类
    // .getDeclaredConstructors()[0]:获取第一个构造函数
    // .newInstance():调用构造函数创建实例
    return (Bindable<?>) compiler.getClassLoader()
        .loadClass(className)
        .getDeclaredConstructors()[0]
        .newInstance();
  }

  /**
   * 静态内部类:访问者模式实现,用于检测Java抽象语法树(AST)中是否包含静态字段
   *
   * 检测静态字段的原因:
   * 1. 静态字段在多线程环境下可能导致并发安全问题
   * 2. 缓存的Bindable对象可能被多个线程同时使用
   * 3. 如果包含静态字段,不同查询之间可能共享状态,导致错误结果
   *
   * 使用方法:
   * 1. 创建StaticFieldDetector实例
   * 2. 调用ClassDeclaration的accept方法,传入detector
   * 3. 检查containsStaticField字段判断是否包含静态字段
   *
   * 实现细节:
   * - 继承VisitorImpl<Void>,实现访问者模式
   * - 只重写visit方法访问字段声明
   * - 一旦发现静态字段,立即停止遍历(返回null)
   */
  static class StaticFieldDetector extends VisitorImpl<Void> {
    // 标记位,记录是否检测到静态字段
    // 初始值为false,表示未检测到
    boolean containsStaticField = false;

    // 访问方法:当遍历到字段声明时调用
    // 参数fieldDeclaration:字段声明对象
    // 返回值:如果已发现静态字段返回null(停止遍历),否则继续遍历
    @Override public Void visit(final FieldDeclaration fieldDeclaration) {
      // 检查字段修饰符是否包含STATIC
      // fieldDeclaration.modifier:字段的修饰符位掩码
      // Modifier.STATIC:静态修饰符的位掩码
      // 使用位与运算(&)检查是否包含STATIC标志
      // 将结果通过或运算(|=)累加到containsStaticField
      containsStaticField |= (fieldDeclaration.modifier & Modifier.STATIC) != 0;
      // 如果已发现静态字段,返回null停止遍历
      // 否则调用父类方法继续遍历子节点
      return containsStaticField ? null : super.visit(fieldDeclaration);
    }
  }

  // 静态方法:将标量值的Bindable转换为数组形式的ArrayBindable
  *
  * 转换说明:
  * 1. Bindable:每行返回单个标量值(当只有1个字段时)
  * 2. ArrayBindable:每行返回Object[]数组(包含所有字段)
  *
  * 转换策略:
  * - 如果输入已经是ArrayBindable,直接返回(无需转换)
  * - 如果输入是Bindable,包装为ArrayBindable,将每个标量值包装为单元素数组
  *
  * 使用场景:
  * - 统一数据格式,简化后续处理
  * - 解释器节点需要数组形式的数据
  *
  * 参数bindable:要转换的Bindable对象(可能返回标量值)
  * 返回值:包装后的ArrayBindable对象(每行返回单元素数组)
  static ArrayBindable box(final Bindable bindable) {
    // 如果输入已经是ArrayBindable,直接返回
    // 这避免了不必要的包装
    if (bindable instanceof ArrayBindable) {
      return (ArrayBindable) bindable;
    }
    // 否则创建新的ArrayBindable实现,包装Bindable
    return new ArrayBindable() {
      // 返回元素类型为Object[]
      @Override public Class<Object[]> getElementType() {
        return Object[].class;
      }

      // 绑定到数据上下文,返回可枚举的数据源
      // 参数dataContext:数据上下文
      // 返回值:可枚举的Object[]数组
      @Override public Enumerable<@Nullable Object[]> bind(DataContext dataContext) {
        // 调用原始Bindable的bind方法,获取标量值的可枚举数据源
        final Enumerable<?> enumerable = bindable.bind(dataContext);
        // 创建新的AbstractEnumerable,将每个标量值包装为单元素数组
        return new AbstractEnumerable<@Nullable Object[]>() {
          // 创建枚举器,用于遍历数据
          @Override public Enumerator<@Nullable Object[]> enumerator() {
            // 获取原始标量值的枚举器
            final Enumerator<?> enumerator = enumerable.enumerator();
            // 创建新的枚举器,将标量值包装为数组
            return new Enumerator<@Nullable Object[]>() {
              // 获取当前元素,将标量值包装为单元素数组
              @Override public @Nullable Object[] current() {
                return new Object[] {enumerator.current()};
              }

              // 移动到下一个元素,委托给原始枚举器
              @Override public boolean moveNext() {
                return enumerator.moveNext();
              }

              // 重置枚举器,委托给原始枚举器
              @Override public void reset() {
                enumerator.reset();
              }

              // 关闭枚举器,释放资源,委托给原始枚举器
              @Override public void close() {
                enumerator.close();
              }
            };
          }
        };
      }
    };
  }

  // 私有静态内部类:解释器节点,从Enumerable读取数据
  *
  * <p>从解释器的角度来看,这是一个叶子节点,因为它直接从数据源读取数据,
  * 不再委托给其他解释器节点。
  *
  * 节点职责:
  * 1. 从Enumerable数据源枚举数据
  * 2. 将每行数据转换为Row对象
  * 3. 将Row对象发送到Sink接收器
  *
  * 执行流程:
  * 1. 创建Enumerator遍历Enumerable
  * 2. 循环调用moveNext()移动到下一行
  * 3. 获取当前行数据并包装为Row对象
  * 4. 通过sink.send()发送到接收器
  * 5. 直到数据遍历完成
  *
  * 使用场景:
  * - 解释器执行模式下的数据源节点
  * - 连接Enumerable和解释器执行引擎
  */
  private static class EnumerableNode implements Node {
    // 可枚举的数据源,包含Object[]数组形式的数据行
    private final Enumerable<@Nullable Object[]> enumerable;
    // 数据接收器,用于处理输出数据
    // Sink是解释器框架中的输出接口,可以发送数据到下一个节点或最终输出
    private final Sink sink;

    // 构造方法:创建EnumerableNode节点
    // 参数enumerable:可枚举的数据源
    // 参数compiler:编译器,用于创建sink
    // 参数rel:关系表达式,用于标识数据源
    EnumerableNode(Enumerable<@Nullable Object[]> enumerable, Compiler compiler,
        EnumerableInterpretable rel) {
      // 保存可枚举数据源引用
      this.enumerable = enumerable;
      // 通过编译器创建sink,并保存引用
      // sink会根据rel的类型创建相应的接收器
      this.sink = compiler.sink(rel);
    }

    // 执行方法:运行节点,从Enumerable读取数据并发送到sink
    // 这是解释器框架的标准方法,由解释器引擎调用
    // 可能抛出InterruptedException,支持中断执行
    @Override public void run() throws InterruptedException {
      // 从enumerable创建枚举器,用于遍历数据
      final Enumerator<@Nullable Object[]> enumerator = enumerable.enumerator();
      // 循环遍历所有数据行
      // moveNext()返回true表示还有更多数据
      while (enumerator.moveNext()) {
        // 获取当前行的数据(Object[]数组)
        @Nullable Object[] values = enumerator.current();
        // 将数据包装为Row对象并发送到sink
        // Row.of()创建包含该行数据的Row对象
        sink.send(Row.of(values));
      }
    }
  }
}