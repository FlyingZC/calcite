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
 */ // Apache许可证声明，声明该代码遵循Apache 2.0许可证，允许在特定条件下使用和分发
package org.apache.calcite.test; // 声明包名为org.apache.calcite.test，表示该类属于Calcite测试工具包

import org.hamcrest.Matcher; // 导入Hamcrest的Matcher接口，用于对象匹配和断言

import java.util.Locale; // 导入Java的Locale类，用于表示特定的地理、政治或文化区域

/**
 * Contains methods that call JDK methods that the
 * <a href="https://github.com/policeman-tools/forbidden-apis">forbidden
 * APIs checker</a> does not approve of.
 *
 * <p>This class is excluded from the check, so methods called via this class
 * will not fail the build.
 */ // Unsafe类：这是一个特殊的工具类，用于包装那些被"forbidden-apis"检查器禁止使用的JDK方法
 // 该类的主要作用是提供一个安全的通道来调用某些被认为不安全或不推荐的API
 // 在构建过程中，forbidden-apis检查器会扫描代码并禁止使用某些API（如直接调用Locale.setDefault或Matcher.matches）
 // 但是这个类本身被排除在检查之外，因此通过这个类调用这些方法可以避免构建失败
 // 这种设计模式允许在特定情况下（如测试环境）使用这些API，同时保持代码库的整体安全性
public class Unsafe { // 定义Unsafe类，这是一个公共的工具类，用于绕过forbidden-apis检查
  private Unsafe() {} // 私有构造方法，防止实例化该类，确保该类只能作为静态工具类使用

  /**
   * {@link Matcher#matches(Object)} is forbidden in regular test code in favour of
   * {@link org.hamcrest.MatcherAssert#assertThat}.
   * Note: {@code Matcher#matches} is still useful when testing matcher implementations.
   *
   * @param matcher matcher
   * @param actual actual value
   * @return the result of matcher.matches(actual)
   */ // matches方法：这是一个静态泛型方法，用于调用Matcher的matches方法
 // 在普通的测试代码中，直接调用Matcher.matches是被禁止的，推荐使用MatcherAssert.assertThat
 // 但是在测试Matcher实现本身时，matches方法是必需的
 // 该方法允许安全地调用matches方法，避免触发forbidden-apis检查
 // 参数说明：
 //   - matcher: Matcher<T>类型的匹配器对象，用于验证实际值是否符合预期
 //   - actual: Object类型的实际值，是要被匹配器验证的对象
 // 返回值：boolean类型，返回匹配器对实际值的匹配结果，true表示匹配成功，false表示匹配失败
  public static <T> boolean matches(Matcher<T> matcher, Object actual) { // 定义静态泛型方法matches，接受一个Matcher对象和一个实际值
    return matcher.matches(actual); // 调用matcher的matches方法，验证实际值是否符合匹配器的规则，并返回匹配结果
  } // 方法结束

  /** Sets locale. */ // setDefaultLocale方法：用于设置JVM的默认Locale（语言环境）
 // 在测试代码中，直接调用Locale.setDefault是被禁止的，因为它会影响整个JVM的全局状态
 // 但是在测试国际化功能时，设置不同的Locale是必需的
 // 该方法提供了一个安全的通道来设置默认Locale，避免触发forbidden-apis检查
 // 参数说明：
 //   - locale: Locale类型的语言环境对象，表示要设置的默认语言环境（如Locale.US、Locale.CHINA等）
 // 返回值：void，无返回值
 // 注意：此方法会改变JVM的全局Locale设置，可能会影响其他并发运行的测试，使用时需要谨慎
  public static void setDefaultLocale(Locale locale) { // 定义静态方法setDefaultLocale，接受一个Locale对象作为参数
    Locale.setDefault(locale); // 调用Locale的静态方法setDefault，设置JVM的默认语言环境为指定的locale
  } // 方法结束
} // 类定义结束
