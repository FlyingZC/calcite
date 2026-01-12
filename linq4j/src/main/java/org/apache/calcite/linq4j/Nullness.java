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
// 包声明：定义该类属于 org.apache.calcite.linq4j 包，这是 Calcite LINQ4J 模块的核心包
package org.apache.calcite.linq4j; // 导入 CheckerFramework 的初始化检查注解，用于标记对象未完全初始化的状态
import org.checkerframework.checker.initialization.qual.UnderInitialization; // 导入 CheckerFramework 的非空保证注解，用于标记方法确保返回值非空
import org.checkerframework.checker.nullness.qual.EnsuresNonNull; // 导入 CheckerFramework 的非空注解，用于标记类型不允许为 null
import org.checkerframework.checker.nullness.qual.NonNull; // 导入 CheckerFramework 的可空注解，用于标记类型允许为 null
import org.checkerframework.checker.nullness.qual.Nullable; // 导入 CheckerFramework 的纯函数注解，用于标记方法不修改对象状态
import org.checkerframework.dataflow.qual.Pure; // 导入 Java 标准库的 List 接口，用于处理列表集合

import java.util.List; // 类文档注释：这个类的方法允许将可空引用转换为非空引用
/**
 * The methods in this class allow to cast nullable reference to a non-nullable one.
 * 这是一个内部类，不作为公共 API 使用
 * This is an internal class, and it is not meant to be used as a public API.
 *
 * 该类能够移除 checker-qual 运行时依赖，并帮助 IDE 更好地查看 {@code castNonNull} 的结果类型
 * <p>The class enables to remove checker-qual runtime dependency, and helps IDEs to see
 * the resulting types of {@code castNonNull} better.
 */ // 类定义：Nullness 类，用于处理空值类型转换，是一个工具类
@SuppressWarnings({"cast.unsafe", "RedundantCast", "contracts.postcondition.not.satisfied"}) // 压制警告注解：抑制不安全转换、冗余转换和后置条件不满足的警告
public class Nullness { // 私有构造方法：防止实例化，确保这是一个纯工具类
  private Nullness() { // 构造方法体为空，不需要任何初始化操作
  } // 方法文档注释：允许你将可空类型视为非空类型，而不进行断言检查

  /**
   * Allows you to treat a nullable type as non-nullable with no assertions.
   *
   * 当你有一个延迟初始化的可空字段时，这很有用，例如：
   * <p>It is useful in the case you have a nullable lately-initialized field
   * like the following:
   *
   * <pre><code>
   * class Wrapper&lt;T&gt; {
   *   &#64;Nullable T value;
   * }
   * </code></pre>
   *
   * 该签名允许你使用 {@code Wrapper} 处理可空或非空类型：{@code Wrapper<@Nullable Integer>}
   * <p>That signature allows you to use {@code Wrapper} with both nullable or
   * non-nullable types: {@code Wrapper<@Nullable Integer>}
   * vs {@code Wrapper<Integer>}. 假设你需要实现
   * vs {@code Wrapper<Integer>}. Suppose you need to implement
   *
   * <pre><code>
   * T get() { return value; }
   * </code></pre>
   *
   * 问题是 checkerframework 不允许这样做，因为 {@code T} 的可空性未知，所以需要使用以下方式：
   * <p>The issue is checkerframework does not permit that because {@code T}
   * has unknown nullability, so the following needs to be used:
   *
   * <pre><code>
   * T get() { return castNonNull(value); }
   * </code></pre>
   *
   * @param <T>     引用的类型
   * @param ref     一个 @Nullable 类型的引用，但在运行时是非空的
   * @param <T>     the type of the reference
   * @param ref     a reference of @Nullable type, that is non-null at run time
   *
   * @return 参数，转换为具有类型限定符 @NonNull
   * @return the argument, cast to have the type qualifier @NonNull
   */ // 纯函数注解：标记该方法不修改对象状态，返回值仅依赖于输入参数
  @Pure // 非空保证注解：标记该方法确保第一个参数（#1）在方法返回后是非空的
  public static @EnsuresNonNull("#1") // 泛型方法声明：T 必须继承自 @Nullable Object，返回类型为 @NonNull T
  <T extends @Nullable Object> @NonNull T castNonNull( // 参数声明：ref 是一个可能为 null 的引用，但在运行时实际非空
      @Nullable T ref) { // 抑制常量条件警告：告诉编译器忽略常量条件检查
    //noinspection ConstantConditions // 返回语句：将 ref 强制转换为 @NonNull T 类型并返回，实际上不进行任何运行时检查
    return (@NonNull T) ref; // 方法文档注释：允许你将可空值数组视为非空值数组
  } // 泛型参数文档：数组元素的类型
  /**
   * Allows you to treat an array of nullable values as an array of non-nullable
   * values.
   *
   * @param <T>     Type of the array elements
   * @param ts      Array
   * @return the argument, cast so that elements are @NonNull
   */ // 抑制未检查转换和常量条件警告
  @SuppressWarnings({"unchecked", "ConstantConditions"}) // 纯函数注解：标记该方法不修改对象状态
  @Pure // 泛型方法声明：返回一个元素类型为 @NonNull T 的数组
  public static <T> @NonNull T[] castNonNullArray( // 参数声明：ts 是一个可能包含 null 元素的数组
      @Nullable T[] ts) { // 返回语句：将 ts 数组先转换为 Object，再强制转换为 @NonNull T[] 类型
    return (@NonNull T []) (Object) ts; // 方法文档注释：允许你将可空值列表视为非空值列表
  } // 泛型参数文档：列表元素的类型
  /**
   * Allows you to treat a list of nullable values as an list of non-nullable
   * values.
   *
   * @param <T>     Type of the list elements
   * @param ts      List
   * @return the argument, cast so that elements are @NonNull
   */ // 抑制未检查转换和原始类型警告
  @SuppressWarnings({"unchecked", "rawtypes"}) // 纯函数注解：标记该方法不修改对象状态
  @Pure // 泛型方法声明：返回一个元素类型为 @NonNull T 的 List
  public static <T> List<@NonNull T> castNonNullList( // 参数声明：ts 是一个列表，其元素类型是 ? extends @Nullable T（可能包含 null）
      List<? extends @Nullable T> ts) { // 返回语句：将 ts 列表先转换为 Object，再强制转换为 List（原始类型），从而绕过类型检查
    return (List) (Object) ts; // 方法文档注释：允许你将未初始化或部分初始化的对象视为已初始化对象，而不进行断言检查
  } // 泛型参数文档：引用的类型
  /**
   * Allows you to treat an uninitialized or under-initialization object as
   * initialized with no assertions.
   *
   * @param <T>     The type of the reference
   * @param ref     A reference that was @Uninitialized at some point but is
   *                now fully initialized
   *
   * @return the argument, cast to have type qualifier @Initialized
   */ // 抑制未检查转换警告
  @SuppressWarnings({"unchecked"}) // 纯函数注解：标记该方法不修改对象状态
  @Pure // 泛型方法声明：返回类型为 T
  public static <T> T castToInitialized(@UnderInitialization T ref) { // 注释说明：为了迷惑 CheckerFramework，我们将对象放入数组中，将数组转换为 Object，然后再转换回数组
    // To throw CheckerFramework off the scent, we put the object into an array,
    // cast the array to an Object, and cast back to an array. // 创建一个包含 ref 的 Object 数组，这样可以通过数组的间接方式绕过 CheckerFramework 的初始化检查
    Object src = new Object[] {ref}; // 将 src 数组强制转换为 Object[] 类型
    Object[] dest = (Object[]) src; // 返回 dest 数组的第一个元素，并将其强制转换为类型 T，这样就绕过了 CheckerFramework 的初始化检查
    return (T) dest[0]; // 类结束
  }
}
