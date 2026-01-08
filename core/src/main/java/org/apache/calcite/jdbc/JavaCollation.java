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
package org.apache.calcite.jdbc;  // 定义包名，表示该类属于 org.apache.calcite.jdbc 包，这是 Calcite JDBC 驱动的核心包之一

import org.apache.calcite.sql.SqlCollation;  // 导入 SqlCollation 基类，JavaCollation 继承自该类，SqlCollation 是 Calcite 中表示排序规则的抽象基类

import org.checkerframework.checker.initialization.qual.UnderInitialization;  // 导入 CheckerFramework 的初始化检查注解，用于标记对象可能尚未完全初始化的状态
import org.checkerframework.checker.nullness.qual.Nullable;  // 导入 CheckerFramework 的空值检查注解，@Nullable 表示返回值可能为 null

import java.nio.charset.Charset;  // 导入字符集类，用于处理字符编码，如 UTF-8、GBK 等
import java.text.Collator;  // 导入 Java 的 Collator 类，用于执行区分语言环境的字符串比较，是 Java 提供的本地化排序规则实现
import java.util.Locale;  // 导入 Locale 类，表示特定的地理、政治或文化区域，用于本地化排序规则

/**
 * Collation that uses a specific {@link Collator} for comparison.
 * 这是一个使用特定 Collator（比较器）进行字符串比较的排序规则类
 * 
 * 类作用说明：
 * JavaCollation 是 Calcite 中基于 Java Collator 实现的排序规则类。
 * 它继承自 SqlCollation，提供了使用 Java 标准库中的 Collator 进行字符串排序的能力。
 * 
 * 核心功能：
 * 1. 封装 Java 的 Collator 对象，用于执行本地化的字符串比较
 * 2. 支持不同的排序强度（strength），包括 PRIMARY、SECONDARY、TERTIARY、IDENTICAL 四个级别
 * 3. 支持不同的语言环境（Locale），如中文、英文、日文等
 * 4. 支持不同的字符集（Charset），如 UTF-8、GBK 等
 * 5. 支持不同的强制转换级别（Coercibility），用于 SQL 标准中的排序规则合并
 * 
 * 使用场景：
 * - 在 SQL 查询中需要按照特定语言环境排序字符串时
 * - 需要区分大小写、重音符号等细节的比较时
 * - 需要按照本地化规则排序时（如中文拼音排序）
 * 
 * 与父类 SqlCollation 的关系：
 * - SqlCollation 是抽象基类，定义了排序规则的通用接口
 * - JavaCollation 是具体实现，使用 Java 的 Collator 来实现排序逻辑
 * - JavaCollation 提供了 getCollator() 方法返回实际的 Collator 对象
 */
public class JavaCollation extends SqlCollation {  // 定义 JavaCollation 类，继承自 SqlCollation，表示这是一个基于 Java Collator 的排序规则实现
  private final Collator collator;  // 成员变量：保存 Java 的 Collator 对象，用于执行实际的字符串比较操作，final 表示一旦初始化就不能修改，确保排序规则的一致性

  public JavaCollation(Coercibility coercibility, Locale locale, Charset charset, int strength) {  // 构造方法：创建一个 JavaCollation 对象
    super(coercibility, locale, charset, getStrengthString(strength));  // 调用父类 SqlCollation 的构造方法，传入强制转换级别、语言环境、字符集和排序强度字符串
    collator = Collator.getInstance(locale);  // 根据指定的语言环境获取对应的 Collator 实例，Collator.getInstance() 会返回适合该语言环境的比较器
    collator.setStrength(strength);  // 设置 Collator 的排序强度，strength 参数决定了比较的严格程度（如是否区分大小写、重音等）
  }

  // Strength values  // 注释：以下是排序强度的常量定义，用于标识不同的排序级别
  private static final String STRENGTH_PRIMARY = "primary";  // 常量：表示 PRIMARY 级别的排序强度，PRIMARY 级别只比较基本字母差异，忽略大小写、重音、变音符号等
  private static final String STRENGTH_SECONDARY = "secondary";  // 常量：表示 SECONDARY 级别的排序强度，SECONDARY 级别比较重音差异，但忽略大小写
  private static final String STRENGTH_TERTIARY = "tertiary";  // 常量：表示 TERTIARY 级别的排序强度，TERTIARY 级别比较大小写差异，这是默认的排序强度
  private static final String STRENGTH_IDENTICAL = "identical";  // 常量：表示 IDENTICAL 级别的排序强度，IDENTICAL 级别比较所有差异，包括字符编码级别的差异

  private static String getStrengthString(int strengthValue) {  // 私有静态方法：将 Collator 的整型强度值转换为字符串表示，用于在排序规则名称中标识强度级别
    switch (strengthValue) {  // 使用 switch 语句根据传入的强度值进行匹配
    case Collator.PRIMARY:  // 如果强度值是 Collator.PRIMARY（值为 0）
      return STRENGTH_PRIMARY;  // 返回 "primary" 字符串，表示 PRIMARY 级别
    case Collator.SECONDARY:  // 如果强度值是 Collator.SECONDARY（值为 1）
      return STRENGTH_SECONDARY;  // 返回 "secondary" 字符串，表示 SECONDARY 级别
    case Collator.TERTIARY:  // 如果强度值是 Collator.TERTIARY（值为 2）
      return STRENGTH_TERTIARY;  // 返回 "tertiary" 字符串，表示 TERTIARY 级别
    case Collator.IDENTICAL:  // 如果强度值是 Collator.IDENTICAL（值为 3）
      return STRENGTH_IDENTICAL;  // 返回 "identical" 字符串，表示 IDENTICAL 级别
    default:  // 如果传入的强度值不在上述四种范围内
      throw new IllegalArgumentException("Incorrect strength value.");  // 抛出 IllegalArgumentException 异常，提示强度值不正确
    }
  }

  @Override protected String generateCollationName(  // 重写父类方法：生成排序规则的唯一名称，用于标识这个排序规则
      @UnderInitialization JavaCollation this,  // 注解：表示当前对象可能尚未完全初始化，这是 CheckerFramework 的初始化检查注解
      Charset charset) {  // 参数：字符集对象，用于生成排序规则名称
    return super.generateCollationName(charset) + "$JAVA_COLLATOR";  // 调用父类的 generateCollationName 方法生成基础名称，然后追加 "$JAVA_COLLATOR" 后缀，标识这是基于 Java Collator 的排序规则
  }

  @Override public @Nullable Collator getCollator() {  // 重写父类方法：获取实际的 Collator 对象，用于执行字符串比较
    return collator;  // 返回成员变量 collator，即 Java 的 Collator 对象，@Nullable 注解表示返回值可能为 null（虽然在这个实现中实际上不会返回 null）
  }
}
