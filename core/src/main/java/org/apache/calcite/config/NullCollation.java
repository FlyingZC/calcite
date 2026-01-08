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
 * Unless required by applicable law or agreed to in writing,
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.config;

/** Strategy for how NULL values are to be sorted if NULLS FIRST or NULLS LAST
 * are not specified in an item in the ORDER BY clause. */
// 空值排序策略枚举类：定义了当 ORDER BY 子句中没有明确指定 NULLS FIRST 或 NULLS LAST 时，NULL 值应该如何排序
// 在 SQL 查询中，排序时 NULL 值的处理方式因数据库而异，Calcite 通过这个枚举支持不同的数据库行为
// 这个类是 Calcite 框架中处理 SQL 排序语义的核心组件之一，用于适配不同数据库的 NULL 值排序规则
public enum NullCollation {

  /** Nulls first for DESC, nulls last for ASC. */
  // HIGH 策略：NULL 值被视为"高值"
  // 当排序方向为 DESC（降序）时，NULL 值排在最前面（因为高值在降序中应该在前）
  // 当排序方向为 ASC（升序）时，NULL 值排在最后面（因为高值在升序中应该在最后）
  // 这种行为类似于 Oracle 数据库的默认行为
  HIGH,

  /** Nulls last for DESC, nulls first for ASC. */
  // LOW 策略：NULL 值被视为"低值"
  // 当排序方向为 DESC（降序）时，NULL 值排在最后面（因为低值在降序中应该在最后）
  // 当排序方向为 ASC（升序）时，NULL 值排在最前面（因为低值在升序中应该在前）
  // 这种行为类似于 PostgreSQL、MySQL 的默认行为
  LOW,

  /** Nulls first for DESC and ASC. */
  // FIRST 策略：NULL 值总是排在最前面
  // 无论排序方向是 ASC 还是 DESC，NULL 值都排在非 NULL 值之前
  // 这种行为类似于 Sybase 数据库的默认行为
  FIRST,

  /** Nulls last for DESC and ASC. */
  // LAST 策略：NULL 值总是排在最后面
  // 无论排序方向是 ASC 还是 DESC，NULL 值都排在非 NULL 值之后
  // 这种行为类似于 Informix 数据库的默认行为
  LAST;

  /** Returns whether NULL values should appear last.
   *
   * @param desc Whether sort is descending
   */
  // 判断 NULL 值是否应该排在最后面的方法
  // 参数 desc：布尔值，表示排序方向，true 表示降序（DESC），false 表示升序（ASC）
  // 返回值：布尔值，true 表示 NULL 值应该排在最后面，false 表示 NULL 值应该排在最前面
  // 这个方法根据当前的 NullCollation 策略和排序方向，决定 NULL 值的排序位置
  public boolean last(boolean desc) {
    // 根据当前的枚举实例（this）判断使用哪种 NULL 排序策略
    switch (this) {
    case FIRST:
      // FIRST 策略：NULL 值总是排在最前面，所以返回 false（表示不排在最后）
      return false;
    case LAST:
      // LAST 策略：NULL 值总是排在最后面，所以返回 true（表示排在最后）
      return true;
    case LOW:
      // LOW 策略：NULL 值被视为低值
      // 如果是降序（desc=true），低值排在最后，所以返回 true
      // 如果是升序（desc=false），低值排在最前，所以返回 false
      return desc;
    case HIGH:
    default:
      // HIGH 策略：NULL 值被视为高值
      // 如果是降序（desc=true），高值排在最前，所以返回 false（即 !desc）
      // 如果是升序（desc=false），高值排在最后，所以返回 true（即 !desc）
      // default 分支作为安全网，虽然理论上不会执行到这里
      return !desc;
    }
  }

  /** Returns whether a given combination of null direction and sort order is
   * the default order of nulls returned in the ORDER BY clause. */
  // 判断给定的 NULL 方向和排序顺序组合是否是 ORDER BY 子句返回的默认 NULL 排序顺序
  // 参数 nullsFirst：布尔值，true 表示 NULL 值排在最前面，false 表示 NULL 值排在最后面
  // 参数 desc：布尔值，true 表示降序排序（DESC），false 表示升序排序（ASC）
  // 返回值：布尔值，true 表示给定的组合是默认的 NULL 排序顺序，false 表示不是默认顺序
  // 这个方法用于优化 SQL 查询，如果显式指定的 NULL 排序顺序与默认顺序一致，可以省略 NULLS FIRST/LAST 子句
  public boolean isDefaultOrder(boolean nullsFirst, boolean desc) {
    // 计算升序标志：desc 的反值，true 表示升序，false 表示降序
    final boolean asc = !desc;
    // 计算 NULL 值排在后面的标志：nullsFirst 的反值，true 表示 NULL 值排在最后面，false 表示 NULL 值排在最前面
    final boolean nullsLast = !nullsFirst;

    // 根据当前的枚举实例（this）判断使用哪种 NULL 排序策略
    switch (this) {
    case FIRST:
      // FIRST 策略：默认情况下 NULL 值应该排在最前面
      // 所以只有当 nullsFirst 为 true 时，才是默认顺序
      return nullsFirst;
    case LAST:
      // LAST 策略：默认情况下 NULL 值应该排在最后面
      // 所以只有当 nullsLast 为 true 时（即 nullsFirst 为 false），才是默认顺序
      return nullsLast;
    case LOW:
      // LOW 策略：NULL 值被视为低值
      // 在升序（asc=true）时，低值应该排在最前面，所以默认是 nullsFirst=true
      // 在降序（desc=true）时，低值应该排在最后面，所以默认是 nullsLast=true
      // 因此，当（升序且 NULL 在前）或（降序且 NULL 在后）时，才是默认顺序
      return (asc && nullsFirst) || (desc && nullsLast);
    case HIGH:
      // HIGH 策略：NULL 值被视为高值
      // 在升序（asc=true）时，高值应该排在最后面，所以默认是 nullsLast=true
      // 在降序（desc=true）时，高值应该排在最前面，所以默认是 nullsFirst=true
      // 因此，当（升序且 NULL 在后）或（降序且 NULL 在前）时，才是默认顺序
      return (asc && nullsLast) || (desc && nullsFirst);
    default:
      // 理论上不会执行到这里，如果执行到这里说明遇到了未知的 NULL 排序策略
      // 抛出非法参数异常，提示未识别的 NULL 排序规则
      throw new IllegalArgumentException("Unrecognized Null Collation");
    }
  }
}