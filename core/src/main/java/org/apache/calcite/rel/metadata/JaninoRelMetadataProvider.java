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
package org.apache.calcite.rel.metadata;

import org.apache.calcite.config.CalciteSystemProperty;
import org.apache.calcite.interpreter.JaninoRexCompiler;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.metadata.janino.RelMetadataHandlerGeneratorUtil;
import org.apache.calcite.util.Util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.UncheckedExecutionException;

import org.apiguardian.api.API;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.CompilerFactoryFactory;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.codehaus.commons.compiler.ISimpleCompiler;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Implementation of the {@link RelMetadataProvider} interface that generates
 * a class that dispatches to the underlying providers.
 * JaninoRelMetadataProvider是RelMetadataProvider接口的实现类，它通过动态生成Java类来实现元数据提供器的功能。
 * 这个类使用Janino编译器在运行时编译生成的Java代码，创建一个能够分发到底层providers的类。
 * 主要作用：提供一种高效的元数据查询机制，通过动态代码生成和编译来优化元数据访问性能。
 * 核心功能：1. 动态生成元数据处理器类；2. 使用Janino编译器编译生成的代码；3. 缓存已生成的处理器以提高性能；
 * 4. 作为元数据查询的入口点，将请求分发到合适的底层provider。
 */
public class JaninoRelMetadataProvider implements RelMetadataProvider, MetadataHandlerProvider {
  // 成员变量：provider - 底层的元数据提供器，实际的元数据查询逻辑由这个provider实现
  // 这个provider通常是一个链式的元数据提供器，包含多个元数据提供器的实现
  private final RelMetadataProvider provider;

  // Constants and static fields - 常量和静态字段区域

  // 静态常量：DEFAULT - 默认的JaninoRelMetadataProvider实例
  // 这个实例使用DefaultRelMetadataProvider.INSTANCE作为底层provider
  // 它是整个Calcite框架中元数据查询的默认入口点
  public static final JaninoRelMetadataProvider DEFAULT =
      JaninoRelMetadataProvider.of(DefaultRelMetadataProvider.INSTANCE);


  /** Cache of pre-generated handlers by provider and kind of metadata.
   * For the cache to be effective, providers should implement identity
   * correctly. */
  // 静态成员变量：HANDLERS - 预生成的元数据处理器缓存
  // 这是一个LoadingCache，用于缓存已经生成和编译好的元数据处理器
  // Key：由handlerClass和provider组成的复合键，用于唯一标识一个处理器
  // Value：MetadataHandler<?> - 实际的元数据处理器实例
  // 缓存的作用：避免重复生成和编译相同的处理器，提高性能
  // 缓存的有效性依赖于provider正确实现equals和hashCode方法
  private static final LoadingCache<Key, MetadataHandler<?>> HANDLERS =
      maxSize(CacheBuilder.newBuilder(),
          CalciteSystemProperty.METADATA_HANDLER_CACHE_MAXIMUM_SIZE.value())
          .build(
              // CacheLoader：当缓存中没有对应的处理器时，自动调用这个方法生成新的处理器
              // key：缓存键，包含handlerClass和provider信息
              // key.handlerClass：需要生成的处理器类类型
              // key.provider.handlers(key.handlerClass)：从底层provider获取所有相关的处理器
              CacheLoader.from(key ->
                  generateCompileAndInstantiate(key.handlerClass,
                      key.provider.handlers(key.handlerClass))));

  /** Private constructor; use {@link #of}. */
  // 私有构造函数：创建JaninoRelMetadataProvider实例
  // 参数：provider - 底层的元数据提供器，实际的元数据查询逻辑由这个provider实现
  // 注意：这是私有构造函数，外部应该使用静态工厂方法of()来创建实例
  private JaninoRelMetadataProvider(RelMetadataProvider provider) {
    this.provider = provider;
  }

  /** Creates a JaninoRelMetadataProvider.
   *
   * @param provider Underlying provider
   */
  // 静态工厂方法：创建JaninoRelMetadataProvider实例
  // 参数：provider - 底层的元数据提供器
  // 返回值：JaninoRelMetadataProvider实例
  // 逻辑：如果传入的provider已经是JaninoRelMetadataProvider类型，直接返回；否则创建新的实例
  // 这样可以避免不必要的包装，提高效率
  public static JaninoRelMetadataProvider of(RelMetadataProvider provider) {
    if (provider instanceof JaninoRelMetadataProvider) {
      return (JaninoRelMetadataProvider) provider;
    }
    return new JaninoRelMetadataProvider(provider);
  }

  // helper for initialization - 初始化辅助方法
  // 私有静态方法：设置缓存的最大大小
  // 参数：builder - CacheBuilder实例，用于构建缓存
  // 参数：size - 缓存的最大大小，如果size < 0则不设置大小限制
  // 返回值：配置好最大大小的CacheBuilder实例
  // 作用：根据系统属性配置缓存的最大大小，避免缓存无限增长
  private static <K, V> CacheBuilder<K, V> maxSize(CacheBuilder<K, V> builder,
      int size) {
    if (size >= 0) {
      builder.maximumSize(size);
    }
    return builder;
  }

  @Override public boolean equals(@Nullable Object obj) {
    // 重写equals方法：比较两个JaninoRelMetadataProvider实例是否相等
    // 参数：obj - 要比较的对象
    // 返回值：如果相等返回true，否则返回false
    // 逻辑：先比较引用，再比较类型，最后比较内部的provider
    return obj == this
        || obj instanceof JaninoRelMetadataProvider
        && ((JaninoRelMetadataProvider) obj).provider.equals(provider);
  }

  @Override public int hashCode() {
    // 重写hashCode方法：计算JaninoRelMetadataProvider实例的哈希值
    // 返回值：哈希值
    // 逻辑：使用固定值109加上provider的哈希值，确保与equals方法一致
    return 109 + provider.hashCode();
  }

  @Deprecated // to be removed before 2.0
  @Override public <@Nullable M extends @Nullable Metadata> UnboundMetadata<M> apply(
      Class<? extends RelNode> relClass, Class<? extends M> metadataClass) {
    // 已废弃的方法：将在2.0版本之前移除
    // 这个方法不再使用，直接抛出UnsupportedOperationException
    // 参数：relClass - RelNode的子类类型
    // 参数：metadataClass - Metadata的子类类型
    // 返回值：UnboundMetadata<M> - 未绑定的元数据
    throw new UnsupportedOperationException();
  }

  @Deprecated // to be removed before 2.0
  @Override public <M extends Metadata> Multimap<Method, MetadataHandler<M>>
      handlers(MetadataDef<M> def) {
    // 已废弃的方法：将在2.0版本之前移除
    // 获取指定元数据定义的所有处理器
    // 参数：def - 元数据定义
    // 返回值：Multimap<Method, MetadataHandler<M>> - 方法到处理器的映射
    // 逻辑：直接委托给底层的provider处理
    return provider.handlers(def);
  }

  @Override public List<MetadataHandler<?>> handlers(
      Class<? extends MetadataHandler<?>> handlerClass) {
    // 获取指定处理器类的所有处理器实例
    // 参数：handlerClass - 处理器类的类型
    // 返回值：List<MetadataHandler<?>> - 处理器实例列表
    // 逻辑：直接委托给底层的provider获取处理器列表
    return provider.handlers(handlerClass);
  }

  private static <MH extends MetadataHandler<?>> MH generateCompileAndInstantiate(
      Class<MH> handlerClass,
      List<? extends MetadataHandler<? extends Metadata>> handlers) {
    // 私有静态方法：生成、编译并实例化元数据处理器
    // 参数：handlerClass - 要生成的处理器类类型
    // 参数：handlers - 底层provider提供的处理器列表
    // 返回值：MH - 生成的处理器实例
    // 逻辑：1. 去重处理器列表；2. 生成处理器代码；3. 编译代码；4. 实例化处理器

    final List<? extends MetadataHandler<? extends Metadata>> uniqueHandlers = handlers.stream()
        .distinct()
        .collect(Collectors.toList());
    RelMetadataHandlerGeneratorUtil.HandlerNameAndGeneratedCode handlerNameAndGeneratedCode =
        // 调用工具类生成处理器代码
        // generateHandler：根据handlerClass和uniqueHandlers生成处理器的Java源代码
        // 返回值：包含处理器类名和生成的源代码的对象
        RelMetadataHandlerGeneratorUtil.generateHandler(handlerClass, uniqueHandlers);

    try {
      // 编译生成的代码并实例化
      // 参数1：生成的处理器类名
      // 参数2：生成的源代码
      // 参数3：目标处理器类类型
      // 参数4：处理器构造函数的参数列表
      return compile(handlerNameAndGeneratedCode.getHandlerName(),
          handlerNameAndGeneratedCode.getGeneratedCode(), handlerClass, uniqueHandlers);
    } catch (CompileException e) {
      // 捕获编译异常，包装成RuntimeException并抛出
      // 异常信息包含生成的源代码，便于调试
      throw new RuntimeException("Error compiling:\n"
          + handlerNameAndGeneratedCode.getGeneratedCode(), e);
    }
  }


  static  <MH extends MetadataHandler<?>> MH compile(String className,
      String generatedCode, Class<MH> handlerClass,
      List<? extends Object> argList) throws CompileException {
    // 静态方法：使用Janino编译器编译生成的Java代码并实例化
    // 参数：className - 要编译的类名
    // 参数：generatedCode - 生成的Java源代码
    // 参数：handlerClass - 目标处理器类类型
    // 参数：argList - 构造函数参数列表
    // 返回值：MH - 编译并实例化后的处理器对象
    // 异常：CompileException - 编译失败时抛出

    final ICompilerFactory compilerFactory;
    // 获取类加载器，用于加载编译后的类
    ClassLoader classLoader =
        requireNonNull(JaninoRelMetadataProvider.class.getClassLoader(),
            "classLoader");
    try {
      // 获取默认的编译器工厂，使用Janino作为编译器实现
      // Janino是一个轻量级的Java编译器，可以在运行时编译Java代码
      compilerFactory = CompilerFactoryFactory.getDefaultCompilerFactory(classLoader);
    } catch (Exception e) {
      // 如果无法创建编译器工厂，抛出IllegalStateException
      // 这通常意味着Janino库没有正确配置或加载
      throw new IllegalStateException(
          "Unable to instantiate java compiler", e);
    }

    final ISimpleCompiler compiler = compilerFactory.newSimpleCompiler();
    // 设置父类加载器，确保编译后的类可以访问Calcite的所有类
    compiler.setParentClassLoader(JaninoRexCompiler.class.getClassLoader());

    if (CalciteSystemProperty.DEBUG.value()) {
      // 如果开启了DEBUG模式，添加调试信息（行号、源文件、变量名）
      // Add line numbers to the generated janino class
      compiler.setDebuggingInformation(true, true, true);
      // 打印生成的源代码，便于调试
      System.out.println(generatedCode);
    }

    // 编译生成的Java源代码
    // cook方法会将源代码编译成字节码并加载到JVM中
    compiler.cook(generatedCode);
    final Constructor constructor;
    final Object o;
    try {
      // 加载编译后的类并获取其构造函数
      // getDeclaredConstructors()[0]：获取第一个构造函数
      constructor = compiler.getClassLoader().loadClass(className)
          .getDeclaredConstructors()[0];
      // 使用反射创建实例，传入处理器列表作为构造参数
      o = constructor.newInstance(argList.toArray());
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | ClassNotFoundException e) {
      // 捕获反射相关的异常并包装成RuntimeException
      // InstantiationException：实例化失败
      // IllegalAccessException：访问权限不足
      // InvocationTargetException：构造函数抛出异常
      // ClassNotFoundException：类未找到
      throw new RuntimeException(e);
    }
    // 将实例转换为指定的处理器类型并返回
    return handlerClass.cast(o);
  }

  @Override public synchronized <H extends MetadataHandler<?>> H revise(Class<H> handlerClass) {
    // 同步方法：获取或创建指定类型的元数据处理器
    // 参数：handlerClass - 要获取的处理器类类型
    // 返回值：H - 处理器实例
    // 逻辑：1. 创建缓存键；2. 从缓存中获取处理器；3. 如果缓存中没有，自动生成
    // synchronized：确保线程安全，避免并发情况下重复生成处理器

    try {
      final Key key = new Key(handlerClass, provider);
      // 从缓存中获取处理器，如果不存在则自动生成
      //noinspection unchecked
      return handlerClass.cast(HANDLERS.get(key));
    } catch (UncheckedExecutionException | ExecutionException e) {
      // 捕获缓存执行异常，提取根本原因并作为RuntimeException抛出
      // UncheckedExecutionException：Guava缓存的未检查执行异常
      // ExecutionException：执行过程中的异常
      throw Util.throwAsRuntime(Util.causeOrSelf(e));
    }
  }

  /** Registers some classes. Does not flush the providers, but next time we
   * need to generate a provider, it will handle all of these classes. So,
   * calling this method reduces the number of times we need to re-generate. */
  // 已废弃的方法：注册RelNode类
  // 参数：classes - 要注册的RelNode类列表
  // 作用：预注册类以减少重新生成的次数，但当前实现为空
  // 注意：这个方法已废弃，不再使用
  @Deprecated
  public void register(Iterable<Class<? extends RelNode>> classes) {
  }

  /** Exception that indicates there there should be a handler for
   * this class but there is not. The action is probably to
   * re-generate the handler class. Use {@link MetadataHandlerProvider.NoHandler} instead.
   * */
  // 已废弃的异常类：表示缺少处理器
  // 作用：当某个RelNode类应该有处理器但没有时抛出此异常
  // 建议：使用MetadataHandlerProvider.NoHandler代替
  @Deprecated
  public static class NoHandler extends MetadataHandlerProvider.NoHandler {
    // 构造函数：创建NoHandler异常
    // 参数：relClass - 缺少处理器的RelNode类
    public NoHandler(Class<? extends RelNode> relClass) {
      super(relClass);
    }
  }

  /** Key for the cache. */
  // 私有静态内部类：缓存的键
  // 作用：作为HANDLERS缓存的键，唯一标识一个处理器
  // 组成：handlerClass和provider的组合
  private static class Key {
    // 成员变量：handlerClass - 处理器类的类型
    final Class<? extends MetadataHandler<? extends Metadata>> handlerClass;
    // 成员变量：provider - 元数据提供器
    final RelMetadataProvider provider;

    // 构造函数：创建缓存键
    // 参数：handlerClass - 处理器类类型
    // 参数：provider - 元数据提供器
    private Key(Class<? extends MetadataHandler<?>> handlerClass,
        RelMetadataProvider provider) {
      this.handlerClass = handlerClass;
      this.provider = provider;
    }

    @Override public int hashCode() {
      // 重写hashCode方法：计算缓存键的哈希值
      // 返回值：哈希值
      // 算法：使用37作为乘数，组合handlerClass和provider的哈希值
      return (handlerClass.hashCode() * 37
          + provider.hashCode()) * 37;
    }

    @Override public boolean equals(@Nullable Object obj) {
      // 重写equals方法：比较两个缓存键是否相等
      // 参数：obj - 要比较的对象
      // 返回值：如果相等返回true，否则返回false
      // 逻辑：先比较引用，再比较类型，最后比较handlerClass和provider
      return this == obj
          || obj instanceof Key
          && ((Key) obj).handlerClass.equals(handlerClass)
          && ((Key) obj).provider.equals(provider);
    }
  }

  @SuppressWarnings("deprecation")
  @Override public <MH extends MetadataHandler<?>> MH handler(final Class<MH> handlerClass) {
    // 已废弃的方法：获取处理器
    // 参数：handlerClass - 处理器类类型
    // 返回值：MH - 处理器实例
    // 逻辑：使用动态代理创建一个总是抛出NoHandler异常的处理器
    // 这是一个占位实现，实际应该使用revise方法
    return handlerClass.cast(
        // 使用Java动态代理创建处理器实例
        Proxy.newProxyInstance(RelMetadataQuery.class.getClassLoader(),
            new Class[] {handlerClass}, (proxy, method, args) -> {
              // 代理方法：当调用任何方法时，抛出NoHandler异常
              final RelNode r = requireNonNull((RelNode) args[0], "(RelNode) args[0]");
              throw new NoHandler(r.getClass());
            }));
  }

  @API(status = API.Status.INTERNAL)
  @VisibleForTesting
  // 公共静态方法：清除静态缓存
  // 作用：清除HANDLERS缓存中的所有条目
  // 用途：主要用于测试，确保测试之间的隔离性
  // 注意：这是一个内部API，不应该在生产代码中使用
  public static void clearStaticCache() {
    HANDLERS.invalidateAll();
  }
}
