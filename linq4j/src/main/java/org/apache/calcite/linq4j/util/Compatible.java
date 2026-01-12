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
package org.apache.calcite.linq4j.util; // 声明包路径，该类位于 org.apache.calcite.linq4j.util 包下

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 Checker Framework 的可空注解，用于标记可能为 null 的值

import java.lang.invoke.MethodHandle; // 导入 MethodHandle 类，用于动态调用方法，是 Java 7 引入的反射机制的改进版本
import java.lang.invoke.MethodHandles; // 导入 MethodHandles 类，用于创建 MethodHandle 对象的工厂类
import java.lang.invoke.MethodType; // 导入 MethodType 类，用于描述方法的参数和返回值类型
import java.lang.reflect.Proxy; // 导入 Proxy 类，用于动态创建代理对象
import java.util.Locale; // 导入 Locale 类，用于本地化相关的操作，如格式化字符串

import static java.util.Objects.requireNonNull; // 静态导入 Objects.requireNonNull 方法，用于参数非空校验

/**
 * Compatibility layer.
 * 兼容性层接口，用于封装不同 JDK 版本之间的兼容性处理
 *
 * <p>Allows to use advanced functionality if the latest JDK version is present.
 * 允许在最新 JDK 版本存在时使用高级功能，同时在旧版本中也能正常工作
 * 这个接口主要用于处理 JDK 16+ 引入的 record（记录类）特性
 * 通过反射和动态代理的方式，使得代码可以在不同版本的 JDK 上运行
 * 如果当前 JDK 版本支持 record，则使用原生方法；否则返回 false
 */
public interface Compatible { // 定义 Compatible 接口，作为兼容性处理的入口点
  Compatible INSTANCE = new Compatible.Factory().create(); // 创建 Compatible 接口的单例实例，通过 Factory 工厂类创建代理对象

  /** Tells whether the given class is a JDK 16+ record. */
  // 判断给定的类是否是 JDK 16+ 引入的 record（记录类）
  // record 是 Java 16 引入的一种特殊的类，用于声明不可变的数据载体
  // 使用泛型 <T> 使得该方法可以接受任意类型的 Class 对象
  // 参数 clazz: 要检查的 Class 对象
  // 返回值: 如果是 record 类返回 true，否则返回 false
  <T> boolean isRecord(Class<T> clazz); // 声明 isRecord 方法，用于检查类是否为 record 类型

  /** Creates an implementation of {@link Compatible} suitable for the current environment. */
  // 创建适合当前环境的 Compatible 接口的实现
  // Factory 是一个静态内部类，负责创建 Compatible 接口的动态代理实例
  // 它使用 Java 的动态代理机制来实现 Compatible 接口
  // 通过反射获取 Class.isRecord() 方法（JDK 16+ 才有），如果获取失败则返回 false
  class Factory { // 定义 Factory 工厂类，用于创建 Compatible 接口的实现
    // IS_RECORD 是一个静态常量，持有 Class.isRecord() 方法的 MethodHandle 对象
    // 使用 @Nullable 注解标记，表示该值可能为 null（当 JDK 版本低于 16 时）
    // MethodHandle 是 Java 7 引入的，比传统的反射机制性能更高
    // 通过 tryGetIsRecordMethod 方法尝试获取 isRecord 方法的句柄
    private static final @Nullable MethodHandle IS_RECORD =
        tryGetIsRecordMethod(MethodHandles.lookup()); // 调用静态方法尝试获取 isRecord 方法的 MethodHandle

    Compatible create() { // 定义 create 方法，用于创建 Compatible 接口的动态代理实例
      return (Compatible) Proxy.newProxyInstance( // 使用 Proxy.newProxyInstance 创建动态代理对象
          Compatible.class.getClassLoader(), // 参数1: 类加载器，使用 Compatible 接口的类加载器
          new Class<?>[]{Compatible.class}, // 参数2: 代理类要实现的接口数组，这里只实现 Compatible 接口
          (proxy, method, args) -> { // 参数3: InvocationHandler 接口的匿名实现，处理方法调用
            if ("isRecord".equals(method.getName())) { // 判断调用的方法名是否为 "isRecord"
              return isRecord(requireNonNull(args[0], "args[0]")); // 如果是 isRecord 方法，调用静态的 isRecord 方法，并校验第一个参数不为 null
            }
            return null; // 如果调用其他方法，返回 null（虽然 Compatible 接口只有一个方法）
          });
    }

    private static boolean isRecord(Object clazz) { // 定义静态的 isRecord 方法，实际执行 record 类型检查
      if (IS_RECORD == null) { // 检查 IS_RECORD MethodHandle 是否为 null
        return false; // 如果为 null，说明当前 JDK 版本不支持 isRecord 方法（JDK < 16），直接返回 false
      }

      try {
        return (boolean) IS_RECORD.invoke(clazz); // 使用 MethodHandle 调用 Class.isRecord() 方法，传入 Class 对象，返回布尔值
      } catch (Throwable e) { // 捕获所有可能的异常（包括检查型异常和运行时异常）
        throw new RuntimeException( // 抛出运行时异常，包装原始异常信息
            String.format(Locale.ROOT, "Failed to invoke %s on %s", IS_RECORD, clazz), e); // 使用 Locale.ROOT 确保错误消息格式一致，包含 MethodHandle 和 Class 对象信息
      }
    }

    private static @Nullable MethodHandle tryGetIsRecordMethod(MethodHandles.Lookup lookup) { // 定义静态方法，尝试获取 Class.isRecord() 方法的 MethodHandle
      try {
        MethodType methodType = MethodType.methodType(boolean.class); // 创建 MethodType 对象，描述方法的返回类型为 boolean，无参数
        return lookup.findVirtual(Class.class, "isRecord", methodType); // 使用 MethodHandles.Lookup 查找 Class 类的 isRecord 虚方法，返回 MethodHandle
      } catch (NoSuchMethodException | IllegalAccessException e) { // 捕获方法不存在或访问权限异常
        return null; // 如果找不到 isRecord 方法（JDK < 16），返回 null
      }
    }
  }
}
