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
package org.apache.calcite.plan;

import org.apache.calcite.config.CalciteConnectionConfig;

import com.google.common.collect.ImmutableList;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Utilities for {@link Context}.
 * Context接口的工具类，提供创建和管理Context实例的静态方法
 * Context是Calcite中用于传递上下文信息的核心机制，可以在规则匹配和优化过程中传递配置、状态等信息
 */
public class Contexts {
  // 空上下文常量，用于表示不包含任何信息的上下文
  // 这是一个单例模式，全局共享同一个空上下文实例，避免重复创建对象
  public static final EmptyContext EMPTY_CONTEXT = new EmptyContext();

  // 私有构造方法，防止实例化，因为这是一个工具类，所有方法都是静态的
  private Contexts() {}

  /** Returns a context that contains a
   * {@link org.apache.calcite.config.CalciteConnectionConfig}.
   *
   * @deprecated Use {@link #of}
   * 已废弃方法：创建包含CalciteConnectionConfig的上下文
   * @param config Calcite连接配置对象，包含连接级别的配置信息
   * @return 包装了config的Context对象
   */
  @Deprecated // to be removed before 2.0
  public static Context withConfig(CalciteConnectionConfig config) {
    return of(config);
  }

  /** Returns a context that returns null for all inquiries.
   * 获取空上下文实例，该上下文对于任何unwrap调用都返回null
   * 用于表示没有任何上下文信息的情况
   * @return 空上下文单例实例
   */
  public static Context empty() {
    return EMPTY_CONTEXT;
  }

  /** Returns a context that wraps an object.
   * 创建包装单个对象的上下文
   *
   * <p>A call to {@code unwrap(C)} will return {@code target} if it is an
   * instance of {@code C}.
   * 当调用unwrap(C)方法时，如果target是C类型的实例，则返回target，否则返回null
   * 这是Context最基本的使用方式：将任意对象包装成Context，以便在规则匹配过程中传递
   * @param o 要包装的对象，不能为null
   * @return WrapContext实例，包装了传入的对象
   */
  public static Context of(Object o) {
    return new WrapContext(o);
  }

  /** Returns a context that wraps an array of objects, ignoring any nulls.
   * 创建包装多个对象的上下文，会自动忽略数组中的null值
   * 这个方法会将每个非null对象包装成独立的Context，然后通过chain方法链接起来
   * 这样可以在一个Context中包含多个对象，按顺序查找
   * @param os 可变参数数组，包含要包装的对象，允许为null
   * @return 链接了所有非null对象的Context
   */
  public static Context of(@Nullable Object... os) {
    final List<Context> contexts = new ArrayList<>();
    for (Object o : os) {
      if (o != null) {
        contexts.add(of(o));
      }
    }
    return chain(contexts);
  }

  /** Returns a context that wraps a list of contexts.
   * 将多个Context链接成一个Context，形成上下文链
   *
   * <p>A call to {@code unwrap(C)} will return the first object that is an
   * instance of {@code C}.
   * 当调用unwrap(C)方法时，会按顺序在链中的每个Context里查找，返回第一个匹配的对象
   * 这实现了优先级机制：前面的Context优先于后面的Context
   *
   * <p>If any of the contexts is a {@link Context}, recursively looks in that
   * object. Thus this method can be used to chain contexts.
   * 如果传入的Context本身也是ChainContext，会递归展开，确保最终的Context链是扁平的
   * 这个方法可以用于嵌套链接Context，形成灵活的上下文层次结构
   * @param contexts 可变参数数组，包含要链接的Context对象
   * @return 链接后的Context对象
   */
  public static Context chain(Context... contexts) {
    return chain(ImmutableList.copyOf(contexts));
  }

  // 私有重载方法：处理Iterable类型的Context集合
  // 这个方法会扁平化Context链，移除重复的Context，并优化最终结果
  // @param contexts Context的可迭代集合
  // @return 优化后的Context，可能是EmptyContext、单个Context或ChainContext
  private static Context chain(Iterable<? extends Context> contexts) {
    // Flatten any chain contexts in the list, and remove duplicates
    // 展平列表中的任何链上下文，并移除重复项
    final List<Context> list = new ArrayList<>();
    for (Context context : contexts) {
      build(list, context);
    }
    // 根据处理后的列表大小返回最优结果
    switch (list.size()) {
    case 0:
      // 如果列表为空，返回空上下文
      return empty();
    case 1:
      // 如果只有一个Context，直接返回它，避免不必要的包装
      return list.get(0);
    default:
      // 如果有多个Context，创建ChainContext包装它们
      return new ChainContext(ImmutableList.copyOf(list));
    }
  }

  /** Recursively populates a list of contexts.
   * 递归构建Context列表，处理嵌套的ChainContext并去重
   * 这个方法是Context链扁平化的核心实现
   * @param list 目标列表，用于存储扁平化后的Context
   * @param context 要处理的Context，可能是普通Context或ChainContext
   */
  private static void build(List<Context> list, Context context) {
    // 如果是空上下文或已经存在于列表中，直接返回，避免重复
    if (context == EMPTY_CONTEXT || list.contains(context)) {
      return;
    }
    // 如果是ChainContext，递归展开其内部的Context
    if (context instanceof ChainContext) {
      ChainContext chainContext = (ChainContext) context;
      for (Context child : chainContext.contexts) {
        build(list, child);
      }
    } else {
      // 普通Context直接添加到列表
      list.add(context);
    }
  }

  /** Context that wraps an object.
   * 包装单个对象的Context实现类
   * 这是Context接口最简单的实现，只持有一个对象
   */
  private static class WrapContext implements Context {
    // 被包装的目标对象，这个对象会被unwrap方法返回
    // final表示这个字段在构造后不可变，保证线程安全
    final Object target;

    // 构造方法：创建包装指定对象的WrapContext
    // @param target 要包装的对象，不能为null，否则会抛出NullPointerException
    WrapContext(Object target) {
      this.target = requireNonNull(target, "target");
    }

    // 实现Context接口的unwrap方法，尝试将target转换为指定类型
    // @param clazz 目标类型的Class对象，用于类型检查和转换
    // @return 如果target是clazz的实例，返回转换后的对象；否则返回null
    // @param <T> 泛型类型参数，表示期望的返回类型
    @Override public <T extends Object> @Nullable T unwrap(Class<T> clazz) {
      if (clazz.isInstance(target)) {
        // 使用clazz.cast进行类型安全的转换
        return clazz.cast(target);
      }
      // 类型不匹配，返回null
      return null;
    }
  }

  /** Empty context.
   * 空上下文实现类，不包含任何对象
   * 用于表示"无上下文"的状态，所有unwrap调用都返回null
   */
  static class EmptyContext implements Context {
    // 实现Context接口的unwrap方法，始终返回null
    // 因为空上下文不包含任何对象，所以无法满足任何类型的请求
    // @param clazz 目标类型的Class对象（在此实现中被忽略）
    // @return 始终返回null
    // @param <T> 泛型类型参数
    @Override public <T extends Object> @Nullable T unwrap(Class<T> clazz) {
      return null;
    }
  }

  /** Context that wraps a chain of contexts.
   * 链式上下文实现类，包装多个Context形成链
   * unwrap时会按顺序在链中的每个Context里查找，返回第一个匹配的对象
   * 这是Context功能最强大的实现，支持灵活的上下文组合和优先级
   */
  private static final class ChainContext implements Context {
    // 不可变列表，存储链中的所有Context
    // 使用ImmutableList保证线程安全和不可变性
    // 这个列表在构造时已经被扁平化，不包含嵌套的ChainContext
    final ImmutableList<Context> contexts;

    // 构造方法：创建包装多个Context的ChainContext
    // @param contexts Context的不可变列表，不能为null，且不能包含嵌套的ChainContext
    ChainContext(ImmutableList<Context> contexts) {
      this.contexts = requireNonNull(contexts, "contexts");
      // 断言：contexts中不能包含ChainContext，确保列表是扁平的
      for (Context context : contexts) {
        assert !(context instanceof ChainContext) : "must be flat";
      }
    }

    // 实现Context接口的unwrap方法，按顺序在链中查找匹配的对象
    // @param clazz 目标类型的Class对象
    // @return 返回链中第一个能转换为clazz类型的对象，如果都没有则返回null
    // @param <T> 泛型类型参数
    @Override public <T extends Object> @Nullable T unwrap(Class<T> clazz) {
      // 遍历链中的每个Context，按顺序查找
      for (Context context : contexts) {
        final T t = context.unwrap(clazz);
        if (t != null) {
          // 找到第一个匹配的对象，立即返回
          return t;
        }
      }
      // 遍历完所有Context都没有找到匹配的，返回null
      return null;
    }
  }
}
