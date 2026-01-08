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
package org.apache.calcite.rel.hint; // 定义包名，该类位于org.apache.calcite.rel.hint包下，属于Calcite关系表达式提示（hint）相关的功能模块

import org.apache.calcite.util.Litmus; // 导入Litmus工具类，用于验证检查并处理错误信息，支持不同的错误级别（FAIL、WARN、IGNORE）

/**
 * A {@code HintOptionChecker} validates the options of a {@link RelHint}.
 * HintOptionChecker是一个函数式接口，用于验证RelHint（关系表达式提示）的选项配置是否合法
 *
 * <p>Every hint would have a validation when converting to a {@link RelHint}, the
 * validation logic is: i) checks whether the hint was already registered;
 * ii) use the registered {@code HintOptionChecker} to check the hint options.
 * 每个提示在转换为RelHint对象时都会进行验证，验证逻辑包括：i)检查提示是否已经注册；
 * ii)使用已注册的HintOptionChecker来检查提示的选项参数是否合法
 *
 * <p>In {@link HintStrategyTable} the option checker is used for
 * hints registration as an optional parameter.
 * 在HintStrategyTable（提示策略表）中，选项检查器作为可选参数用于提示的注册过程，
 * 允许自定义验证逻辑来确保提示的选项配置符合预期
 *
 * @see HintStrategyTable#validateHint
 */
@FunctionalInterface // 标记这是一个函数式接口，只包含一个抽象方法，可以使用Lambda表达式实现
public interface HintOptionChecker { // 定义HintOptionChecker接口，用于验证关系表达式提示的选项配置
  /**
   * Checks if the given hint is valid.
   * 检查给定的提示对象是否有效，验证其选项配置是否符合要求
   *
   * <p>Always use the {@link Litmus#check(boolean, String, Object...)}
   * to do the check. The default behavior is to log warnings for any hint error.
   * 应该始终使用Litmus.check()方法来执行检查操作，该方法可以根据错误级别（FAIL/WARN/IGNORE）
   * 来处理验证失败的情况，默认行为是对任何提示错误记录警告信息而不是抛出异常
   *
   * @param hint The hint to check
   * hint参数表示需要被检查的RelHint对象，包含提示的名称和选项列表
   * @param errorHandler The error handler
   * errorHandler参数是Litmus类型的错误处理器，用于根据错误级别处理验证失败的情况，
   * 可以是FAIL（失败并抛出异常）、WARN（记录警告）、IGNORE（忽略错误）三种模式之一
   *
   * @return True if the check passes
   * 返回值表示检查是否通过，true表示提示的选项配置合法，false表示验证失败
   */
  boolean checkOptions(RelHint hint, Litmus errorHandler); // 定义抽象方法checkOptions，用于验证提示的选项配置，接受RelHint和Litmus两个参数，返回布尔值表示验证结果
}
