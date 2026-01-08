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
package org.apache.calcite.config;  // 定义包名，这个类属于org.apache.calcite.config包，用于配置相关的类

import org.apache.calcite.avatica.util.Casing;  // 导入Casing枚举类，用于定义标识符的大小写转换规则（如转大写、转小写、保持不变）
import org.apache.calcite.avatica.util.Quoting;  // 导入Quoting枚举类，用于定义标识符的引用方式（如反引号、双引号、方括号）

import com.google.common.collect.ImmutableSet;  // 导入Google Guava库的ImmutableSet类，用于创建不可变的集合

import java.util.Set;  // 导入Java集合框架的Set接口，用于存储字符字面量风格的集合

import static java.util.Objects.requireNonNull;  // 导入Objects类的requireNonNull静态方法，用于检查参数是否为null

/** Named, built-in lexical policy. A lexical policy describes how
 * identifiers are quoted, whether they are converted to upper- or
 * lower-case when they are read, and whether they are matched
 * case-sensitively. */  // JavaDoc注释：这是一个命名、内置的词法策略枚举类。词法策略描述了如何引用标识符，读取时是否转换为大写或小写，以及是否区分大小写匹配
public enum Lex {  // 定义一个名为Lex的枚举类，用于表示不同的SQL方言的词法策略
  /** Lexical policy similar to BigQuery.
   * The case of identifiers is preserved whether or not they quoted;
   * after which, identifiers are matched case-insensitively.
   * Back-ticks allow identifiers to contain non-alphanumeric characters;
   * a back-tick is escaped using a backslash.
   * Character literals may be enclosed in single or double quotes. */  // JavaDoc注释：BigQuery的词法策略。标识符的大小写会被保留（无论是否被引用），标识符匹配时不区分大小写。反引号允许标识符包含非字母数字字符，反引号本身使用反斜杠转义。字符字面量可以用单引号或双引号括起来
  BIG_QUERY(Quoting.BACK_TICK_BACKSLASH, Casing.UNCHANGED, Casing.UNCHANGED,  // 枚举常量BIG_QUERY，表示BigQuery的词法策略。参数：引用方式为反引号且用反斜杠转义，未引用标识符大小写不变，引用标识符大小写不变
      false, CharLiteralStyle.BQ_SINGLE, CharLiteralStyle.BQ_DOUBLE),  // 参数：不区分大小写匹配，支持BigQuery风格的单引号和双引号字符字面量

  /** Lexical policy similar to Oracle. The case of identifiers enclosed in
   * double-quotes is preserved; unquoted identifiers are converted to
   * upper-case; after which, identifiers are matched case-sensitively. */  // JavaDoc注释：Oracle的词法策略。双引号内的标识符大小写被保留，未引用的标识符转换为大写，标识符匹配时区分大小写
  ORACLE(Quoting.DOUBLE_QUOTE, Casing.TO_UPPER, Casing.UNCHANGED, true,  // 枚举常量ORACLE，表示Oracle的词法策略。参数：引用方式为双引号，未引用标识符转大写，引用标识符大小写不变，区分大小写匹配
      CharLiteralStyle.STANDARD),  // 参数：支持标准字符字面量风格

  /** Lexical policy similar to MySQL. (To be precise: MySQL on Windows;
   * MySQL on Linux uses case-sensitive matching, like the Linux file system.)
   * The case of identifiers is preserved whether or not they quoted;
   * after which, identifiers are matched case-insensitively.
   * Back-ticks allow identifiers to contain non-alphanumeric characters;
   * a back-tick is escaped using a back-tick. */  // JavaDoc注释：MySQL的词法策略（准确说是Windows上的MySQL，Linux上的MySQL使用区分大小写匹配，类似Linux文件系统）。标识符大小写被保留（无论是否被引用），标识符匹配时不区分大小写。反引号允许标识符包含非字母数字字符，反引号本身用反引号转义
  MYSQL(Quoting.BACK_TICK, Casing.UNCHANGED, Casing.UNCHANGED, false,  // 枚举常量MYSQL，表示MySQL的词法策略。参数：引用方式为反引号，未引用标识符大小写不变，引用标识符大小写不变，不区分大小写匹配
      CharLiteralStyle.STANDARD),  // 参数：支持标准字符字面量风格

  /** Lexical policy similar to MySQL with ANSI_QUOTES option enabled. (To be
   * precise: MySQL on Windows; MySQL on Linux uses case-sensitive matching,
   * like the Linux file system.) The case of identifiers is preserved whether
   * or not they quoted; after which, identifiers are matched
   * case-insensitively. Double quotes allow identifiers to contain
   * non-alphanumeric characters. */  // JavaDoc注释：启用ANSI_QUOTES选项的MySQL词法策略（准确说是Windows上的MySQL）。标识符大小写被保留（无论是否被引用），标识符匹配时不区分大小写。双引号允许标识符包含非字母数字字符
  MYSQL_ANSI(Quoting.DOUBLE_QUOTE, Casing.UNCHANGED, Casing.UNCHANGED, false,  // 枚举常量MYSQL_ANSI，表示启用ANSI_QUOTES的MySQL词法策略。参数：引用方式为双引号，未引用标识符大小写不变，引用标识符大小写不变，不区分大小写匹配
      CharLiteralStyle.STANDARD),  // 参数：支持标准字符字面量风格

  /** Lexical policy similar to Microsoft SQL Server.
   * The case of identifiers is preserved whether or not they are quoted;
   * after which, identifiers are matched case-insensitively.
   * Brackets allow identifiers to contain non-alphanumeric characters. */  // JavaDoc注释：Microsoft SQL Server的词法策略。标识符大小写被保留（无论是否被引用），标识符匹配时不区分大小写。方括号允许标识符包含非字母数字字符
  SQL_SERVER(Quoting.BRACKET, Casing.UNCHANGED, Casing.UNCHANGED, false,  // 枚举常量SQL_SERVER，表示SQL Server的词法策略。参数：引用方式为方括号，未引用标识符大小写不变，引用标识符大小写不变，不区分大小写匹配
      CharLiteralStyle.STANDARD),  // 参数：支持标准字符字面量风格

  /** Lexical policy similar to Java.
   * The case of identifiers is preserved whether or not they are quoted;
   * after which, identifiers are matched case-sensitively.
   * Unlike Java, back-ticks allow identifiers to contain non-alphanumeric
   * characters; a back-tick is escaped using a back-tick. */  // JavaDoc注释：Java的词法策略。标识符大小写被保留（无论是否被引用），标识符匹配时区分大小写。与Java不同，反引号允许标识符包含非字母数字字符，反引号本身用反引号转义
  JAVA(Quoting.BACK_TICK, Casing.UNCHANGED, Casing.UNCHANGED, true,  // 枚举常量JAVA，表示Java的词法策略。参数：引用方式为反引号，未引用标识符大小写不变，引用标识符大小写不变，区分大小写匹配
      CharLiteralStyle.STANDARD);  // 参数：支持标准字符字面量风格

  public final Quoting quoting;  // 公共final成员变量，存储标识符的引用方式（如反引号、双引号、方括号），final表示该变量在初始化后不能被修改
  public final Casing unquotedCasing;  // 公共final成员变量，存储未引用标识符的大小写转换规则（如转大写、转小写、保持不变）
  public final Casing quotedCasing;  // 公共final成员变量，存储引用标识符的大小写转换规则（如转大写、转小写、保持不变）
  public final boolean caseSensitive;  // 公共final成员变量，布尔值，表示标识符匹配时是否区分大小写，true表示区分大小写，false表示不区分大小写
  @SuppressWarnings("ImmutableEnumChecker")  // 注解，告诉编译器忽略ImmutableEnumChecker的警告，因为枚举实例本身就是不可变的
  public final Set<CharLiteralStyle> charLiteralStyles;  // 公共final成员变量，存储字符字面量风格的集合，表示该词法策略支持的字符字面量风格（如单引号、双引号等）

  Lex(Quoting quoting,  // 构造方法，参数quoting表示标识符的引用方式
      Casing unquotedCasing,  // 参数unquotedCasing表示未引用标识符的大小写转换规则
      Casing quotedCasing,  // 参数quotedCasing表示引用标识符的大小写转换规则
      boolean caseSensitive,  // 参数caseSensitive表示标识符匹配时是否区分大小写
      CharLiteralStyle... charLiteralStyles) {  // 可变参数charLiteralStyles，表示支持的字符字面量风格列表
    this.quoting = requireNonNull(quoting, "quoting");  // 使用requireNonNull检查quoting参数是否为null，如果为null则抛出NullPointerException，并赋值给成员变量quoting
    this.unquotedCasing = requireNonNull(unquotedCasing, "unquotedCasing");  // 使用requireNonNull检查unquotedCasing参数是否为null，如果为null则抛出NullPointerException，并赋值给成员变量unquotedCasing
    this.quotedCasing = requireNonNull(quotedCasing, "quotedCasing");  // 使用requireNonNull检查quotedCasing参数是否为null，如果为null则抛出NullPointerException，并赋值给成员变量quotedCasing
    this.caseSensitive = caseSensitive;  // 将caseSensitive参数直接赋值给成员变量caseSensitive
    this.charLiteralStyles = ImmutableSet.copyOf(charLiteralStyles);  // 使用ImmutableSet.copyOf创建一个不可变的Set集合，将可变参数charLiteralStyles转换为不可变集合并赋值给成员变量charLiteralStyles
  }  // 构造方法结束
}  // 枚举类Lex定义结束
