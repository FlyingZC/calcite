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
package org.apache.calcite.rel.externalize; // 包声明：org.apache.calcite.rel.externalize，这是Calcite框架中用于关系表达式外部化的包

import org.apache.calcite.avatica.util.TimeUnitRange; // 导入TimeUnitRange类，表示时间单位范围，用于SQL时间函数
import org.apache.calcite.rel.core.TableModify; // 导入TableModify类，表示表修改操作（INSERT/UPDATE/DELETE）
import org.apache.calcite.rex.RexUnknownAs; // 导入RexUnknownAs类，表示对未知值的处理方式
import org.apache.calcite.sql.JoinConditionType; // 导入JoinConditionType类，表示连接条件类型
import org.apache.calcite.sql.JoinType; // 导入JoinType类，表示连接类型（INNER/LEFT/RIGHT/FULL等）
import org.apache.calcite.sql.SqlExplain; // 导入SqlExplain类，表示SQL解释器
import org.apache.calcite.sql.SqlExplainFormat; // 导入SqlExplainFormat类，表示解释输出格式
import org.apache.calcite.sql.SqlExplainLevel; // 导入SqlExplainLevel类，表示解释详细级别
import org.apache.calcite.sql.SqlInsertKeyword; // 导入SqlInsertKeyword类，表示INSERT语句的关键字
import org.apache.calcite.sql.SqlJsonConstructorNullClause; // 导入SqlJsonConstructorNullClause类，表示JSON构造函数的NULL处理子句
import org.apache.calcite.sql.SqlJsonQueryWrapperBehavior; // 导入SqlJsonQueryWrapperBehavior类，表示JSON查询的包装行为
import org.apache.calcite.sql.SqlJsonValueEmptyOrErrorBehavior; // 导入SqlJsonValueEmptyOrErrorBehavior类，表示JSON值为空或错误时的行为
import org.apache.calcite.sql.SqlMatchRecognize; // 导入SqlMatchRecognize类，表示SQL模式匹配识别
import org.apache.calcite.sql.SqlSelectKeyword; // 导入SqlSelectKeyword类，表示SELECT语句的关键字
import org.apache.calcite.sql.fun.SqlTrimFunction; // 导入SqlTrimFunction类，表示TRIM函数

import com.google.common.collect.ImmutableMap; // 导入Guava的ImmutableMap，用于创建不可变映射

import org.checkerframework.checker.nullness.qual.NonNull; // 导入NonNull注解，表示非空类型
import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，表示可空类型

import static org.apache.calcite.linq4j.Nullness.castNonNull; // 导入castNonNull方法，用于类型转换时消除空值警告

import static java.util.Objects.requireNonNull; // 导入requireNonNull方法，用于检查参数非空

/** Registry of {@link Enum} classes that can be serialized to JSON. // 类文档：这是一个可以序列化为JSON的枚举类的注册表
 *
 * <p>Suppose you want to serialize the value // 假设你想将值
 * {@link SqlTrimFunction.Flag#LEADING} to JSON. // 序列化为JSON
 * First, make sure that {@link SqlTrimFunction.Flag} is registered. // 首先，确保SqlTrimFunction.Flag已注册
 * The type will be serialized as "SYMBOL". // 类型将被序列化为"SYMBOL"
 * The value will be serialized as the string "LEADING". // 值将被序列化为字符串"LEADING"
 *
 * <p>When we deserialize, we rely on the fact that the registered // 当我们反序列化时，我们依赖于已注册的
 * {@code enum} classes have distinct values. Therefore, knowing that // 枚举类具有不同的值。因此，知道
 * {@code (type="SYMBOL", value="LEADING")} we can convert the string "LEADING" // 我们可以将字符串"LEADING"
 * to the enum {@code Flag.LEADING}. */ // 转换为枚举Flag.LEADING
@SuppressWarnings({"rawtypes", "unchecked"}) // 抑制原始类型和未检查转换的警告
public abstract class RelEnumTypes { // 声明一个抽象类RelEnumTypes，用于枚举类型的注册和转换
  private RelEnumTypes() {} // 私有构造方法，防止实例化，这是一个工具类

  private static final ImmutableMap<String, Enum<?>> ENUM_BY_NAME; // 成员变量：不可变映射，存储枚举常量名称到枚举值的映射，用于快速查找

  static { // 静态初始化块，在类加载时执行，用于初始化ENUM_BY_NAME映射
    // Build a mapping from enum constants (e.g. LEADING) to the enum // 构建从枚举常量（如LEADING）到枚举
    // that contains them (e.g. SqlTrimFunction.Flag). If there two // 的映射（如SqlTrimFunction.Flag）。如果有两个
    // enum constants have the same name, the builder will throw. // 枚举常量同名，构建器将抛出异常
    final ImmutableMap.Builder<String, Enum<?>> enumByName = // 创建不可变映射的构建器，键为枚举常量名称（字符串），值为枚举对象
        ImmutableMap.builder(); // 调用builder()方法创建构建器实例
    register(enumByName, JoinConditionType.class); // 注册JoinConditionType枚举类，用于连接条件类型
    register(enumByName, JoinType.class); // 注册JoinType枚举类，用于连接类型
    register(enumByName, RexUnknownAs.class); // 注册RexUnknownAs枚举类，用于未知值处理
    register(enumByName, SqlExplain.Depth.class); // 注册SqlExplain.Depth枚举类，用于解释深度
    register(enumByName, SqlExplainFormat.class); // 注册SqlExplainFormat枚举类，用于解释格式
    register(enumByName, SqlExplainLevel.class); // 注册SqlExplainLevel枚举类，用于解释级别
    register(enumByName, SqlInsertKeyword.class); // 注册SqlInsertKeyword枚举类，用于INSERT关键字
    register(enumByName, SqlJsonConstructorNullClause.class); // 注册SqlJsonConstructorNullClause枚举类，用于JSON构造NULL子句
    register(enumByName, SqlJsonQueryWrapperBehavior.class); // 注册SqlJsonQueryWrapperBehavior枚举类，用于JSON查询包装行为
    register(enumByName, SqlJsonValueEmptyOrErrorBehavior.class); // 注册SqlJsonValueEmptyOrErrorBehavior枚举类，用于JSON空值或错误行为
    register(enumByName, SqlMatchRecognize.AfterOption.class); // 注册SqlMatchRecognize.AfterOption枚举类，用于模式匹配后选项
    register(enumByName, SqlSelectKeyword.class); // 注册SqlSelectKeyword枚举类，用于SELECT关键字
    register(enumByName, SqlTrimFunction.Flag.class); // 注册SqlTrimFunction.Flag枚举类，用于TRIM函数标志
    register(enumByName, TimeUnitRange.class); // 注册TimeUnitRange枚举类，用于时间单位范围
    register(enumByName, TableModify.Operation.class); // 注册TableModify.Operation枚举类，用于表修改操作
    ENUM_BY_NAME = enumByName.build(); // 构建不可变映射并赋值给ENUM_BY_NAME字段，此时映射已不可修改
  }

  private static void register(ImmutableMap.Builder<String, Enum<?>> builder, // 私有静态方法：注册枚举类的所有常量到映射中
      Class<? extends Enum> aClass) { // 参数：要注册的枚举类
    for (Enum enumConstant : castNonNull(aClass.getEnumConstants())) { // 遍历枚举类的所有常量，castNonNull消除空值警告
      builder.put(enumConstant.name(), enumConstant); // 将枚举常量的名称作为键，枚举对象本身作为值放入映射
    } // 循环结束，所有枚举常量都已注册
  }

  /** Converts a literal into a value that can be serialized to JSON. // 方法文档：将字面量转换为可以序列化为JSON的值
   * In particular, if is an enum, converts it to its name. */ // 特别是，如果是枚举，将其转换为其名称
  public static @Nullable Object fromEnum(@Nullable Object value) { // 公共静态方法：将对象转换为可JSON序列化的形式，参数可为空，返回值可为空
    return value instanceof Enum ? fromEnum((Enum) value) : value; // 如果值是枚举类型，调用fromEnum(Enum)方法转换；否则直接返回原值
  }

  /** Converts an enum into its name. // 方法文档：将枚举转换为其名称
   * Throws if the enum's class is not registered. */ // 如果枚举类未注册则抛出异常
  public static String fromEnum(Enum enumValue) { // 公共静态方法：将枚举值转换为其名称字符串
    if (ENUM_BY_NAME.get(enumValue.name()) != enumValue) { // 检查枚举值是否在注册表中，并且映射的值是否匹配
      throw new AssertionError("cannot serialize enum value to JSON: " // 如果不匹配，抛出断言错误，表示无法序列化
          + enumValue.getDeclaringClass().getCanonicalName() + "." // 错误信息包含枚举类的规范名称
          + enumValue); // 错误信息包含枚举常量的名称
    } // 检查通过，枚举值已注册
    return enumValue.name(); // 返回枚举常量的名称字符串
  }

  /** Converts a string to an enum value. // 方法文档：将字符串转换为枚举值
   * The converse of {@link #fromEnum(Enum)}. // 这是fromEnum(Enum)方法的逆操作
   *
   * @throws NullPointerException if there is no corresponding registered {@link Enum} // 如果没有对应的已注册枚举则抛出空指针异常
   * */
  static <E extends Enum<E>> @NonNull E toEnum(String name) { // 包级私有静态方法：根据枚举名称获取枚举值，使用泛型确保类型安全
    return (E) requireNonNull(ENUM_BY_NAME.get(name)); // 从映射中获取枚举值，如果不存在则抛出NullPointerException，然后强制转换为泛型类型E
  } // 方法结束
} // 类结束
