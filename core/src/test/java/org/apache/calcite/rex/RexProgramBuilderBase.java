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
package org.apache.calcite.rex; // 定义包名，org.apache.calcite.rex 包包含了 Calcite 中行表达式(Row Expressions)相关的所有类

import org.apache.calcite.DataContext; // 导入 DataContext 类，用于提供执行 SQL 查询时需要的上下文信息，如时区、时间戳等
import org.apache.calcite.DataContexts; // 导入 DataContexts 工具类，用于创建 DataContext 实例
import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入 JavaTypeFactory 接口，用于创建基于 Java 类型的 SQL 类型
import org.apache.calcite.jdbc.JavaTypeFactoryImpl; // 导入 JavaTypeFactoryImpl 实现类，是 JavaTypeFactory 的具体实现
import org.apache.calcite.plan.RelOptPredicateList; // 导入 RelOptPredicateList 类，用于在关系表达式优化过程中存储谓词（条件表达式）列表
import org.apache.calcite.rel.type.RelDataType; // 导入 RelDataType 接口，表示 SQL 数据类型，如 INTEGER、VARCHAR 等
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入 RelDataTypeFactory 接口，是创建 RelDataType 的工厂
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入 RelDataTypeSystem 接口，定义了类型系统的默认行为，如最大精度、最大长度等
import org.apache.calcite.sql.fun.SqlInternalOperators; // 导入 SqlInternalOperators 类，包含 Calcite 内部使用的 SQL 操作符
import org.apache.calcite.sql.fun.SqlLibraryOperators; // 导入 SqlLibraryOperators 类，包含 SQL 库中定义的操作符
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入 SqlStdOperatorTable 类，包含所有标准 SQL 操作符，如 =、<、>、AND、OR 等
import org.apache.calcite.sql.type.SqlTypeName; // 导入 SqlTypeName 枚举，定义了所有 SQL 类型名称，如 INTEGER、VARCHAR、BOOLEAN 等

import com.google.common.collect.ImmutableList; // 导入 Google Guava 的 ImmutableList，用于创建不可变的列表
import com.google.common.collect.ImmutableMap; // 导入 Google Guava 的 ImmutableMap，用于创建不可变的映射

import org.junit.jupiter.api.BeforeEach; // 导入 JUnit 5 的 BeforeEach 注解，用于在每个测试方法执行前执行初始化操作

import java.math.BigDecimal; // 导入 BigDecimal 类，用于精确的十进制数值计算
import java.util.HashMap; // 导入 HashMap 类，用于存储键值对映射
import java.util.Map; // 导入 Map 接口，定义了映射的基本操作
import java.util.TimeZone; // 导入 TimeZone 类，用于表示时区信息

/**
 * RexProgramBuilderBase 类是一个抽象基类，为 RexProgram 相关的测试提供辅助方法来构建 Rex 表达式。
 * 
 * Rex (Row Expression) 是 Calcite 中用于表示行表达式的抽象语法树节点，例如：字段引用、字面量、函数调用、操作符等。
 * 这个类封装了创建各种 Rex 表达式（如字面量、变量、操作符调用、类型转换等）的便捷方法，大大简化了测试代码的编写。
 * 
 * 核心功能包括：
 * 1. 类型系统管理：提供创建各种 SQL 类型（可空和不可空）的方法，如 tInt()、tVarchar()、tBool() 等
 * 2. 字面量创建：提供创建各种类型字面量的方法，如 literal(42)、literal("hello")、null_(tInt()) 等
 * 3. 变量引用：提供创建动态参数变量的方法，如 vInt()、vVarchar()、vBool() 等，这些变量模拟 SQL 中的参数占位符
 * 4. 操作符封装：提供封装常用 SQL 操作符的方法，如 eq()、and()、or()、isNull()、cast() 等
 * 5. 表达式构建：通过组合上述方法，可以构建复杂的 Rex 表达式树
 * 
 * 设计特点：
 * - 使用动态参数（RexDynamicParam）来表示输入变量，避免了使用简单的输入引用（RexInputRef）导致类型信息丢失的问题
 * - 每种类型都提供可空和不可空两个版本，方便测试不同场景
 * - 变量索引从 0 到 MAX_FIELDS-1，每个类型最多支持 10 个不同的变量
 * - 提供了丰富的 Javadoc 注释，方便开发者理解每个方法的用途
 * 
 * 使用场景：
 * 这个类主要用于 Calcite 的单元测试中，特别是 RexSimplify、RexExecutor、RexProgram 等模块的测试。
 * 通过继承这个类，测试类可以快速构建各种测试场景所需的 Rex 表达式，而无需手动创建复杂的表达式树。
 */
public abstract class RexProgramBuilderBase { // 定义一个抽象类，因为不需要实例化，只作为测试基类使用
  /**
   * MAX_FIELDS 常量定义了每种类型的最大字段数量。
   * 
   * 测试输入变量应该来自一个结构体类型，因此创建了一个结构体，其中前 MAX_FIELDS 个字段是可空的，
   * 后 MAX_FIELDS 个字段是不可空的。
   * 
   * 例如，对于 int 类型，会创建如下结构：
   * - int0 (可空)
   * - int1 (可空)
   * - ... (可空)
   * - int9 (可空)
   * - notNullInt0 (不可空)
   * - notNullInt1 (不可空)
   * - ... (不可空)
   * - notNullInt9 (不可空)
   * 
   * 这样设计的原因：
   * 1. 提供足够多的不同变量来测试，避免变量名冲突
   * 2. 支持测试可空和不可空两种场景
   * 3. 通过索引可以区分不同的变量，便于调试
   */
  protected static final int MAX_FIELDS = 10; // 定义最大字段数为 10，这是一个常量，使用 protected 修饰符允许子类访问

  // ==================== 核心成员变量 ====================
  
  /**
   * typeFactory 是类型工厂，用于创建和管理 SQL 数据类型。
   * 
   * JavaTypeFactory 是 RelDataTypeFactory 的实现，它将 SQL 类型映射到 Java 类型。
   * 例如：SQL 的 INTEGER 类型映射到 Java 的 int 类型，VARCHAR 映射到 String 类型。
   * 
   * 主要用途：
   * - 创建基本 SQL 类型：createSqlType(SqlTypeName.INTEGER)
   * - 设置类型的可空性：createTypeWithNullability(type, true)
   * - 创建数组类型：createArrayType(elemType, -1)
   * - 创建结构体类型：builder().add("field1", type1).add("field2", type2).build()
   * 
   * 在 setUp() 方法中初始化为 JavaTypeFactoryImpl 实例
   */
  protected JavaTypeFactory typeFactory; // 声明 Java 类型工厂，使用 protected 修饰符允许子类访问

  /**
   * rexBuilder 是 Rex 表达式构建器，用于创建各种 Rex 节点。
   * 
   * RexBuilder 是创建 Rex 表达式树的中心类，它封装了创建各种 Rex 节点的方法：
   * - makeLiteral(value, type)：创建字面量节点
   * - makeInputRef(type, index)：创建输入引用节点
   * - makeCall(operator, operands)：创建函数调用或操作符调用节点
   * - makeDynamicParam(type, index)：创建动态参数节点
   * - makeCast(type, expr)：创建类型转换节点
   * 
   * RexBuilder 依赖于 RelDataTypeFactory 来获取类型信息，确保创建的表达式具有正确的类型。
   * 
   * 在 setUp() 方法中初始化，传入 typeFactory 作为参数
   */
  protected RexBuilder rexBuilder; // 声明 Rex 构建器，使用 protected 修饰符允许子类访问

  /**
   * executor 是 Rex 表达式执行器，用于执行和化简 Rex 表达式。
   * 
   * RexExecutor 提供了在运行时执行 Rex 表达式的能力，主要用于：
   * - 常量折叠：在编译时可以计算的表达式，会被实际计算并替换为结果
   * - 表达式化简：简化复杂的表达式，例如：TRUE AND x → x
   * - 类型检查和转换：确保表达式类型匹配
   * 
   * 执行过程需要一个 DataContext 提供上下文信息，例如时区、当前时间戳等。
   * 
   * 在 setUp() 方法中初始化，传入 DataContext 和 RexBuilder
   */
  protected RexExecutor executor; // 声明 Rex 执行器，使用 protected 修饰符允许子类访问

  /**
   * simplify 是 Rex 表达式化简器，用于将复杂的表达式转换为等价的简化形式。
   * 
   * RexSimplify 是 Calcite 中表达式优化的重要组件，它应用各种化简规则来简化表达式：
   * - 布尔代数化简：NOT NOT x → x，x AND TRUE → x
   * - 常量折叠：1 + 2 → 3
   * - 空值处理：NULL AND x → NULL，COALESCE(x, y) → x（如果 x 不为空）
   * - 类型转换消除：CAST(42 AS INTEGER) → 42
   * - 谓词下推：将条件尽可能下推到数据源
   * 
   * RexSimplify 使用 RexExecutor 来执行可以立即计算的表达式。
   * 
   * 在 setUp() 方法中初始化，使用 withParanoid(true) 启用更严格的化简
   */
  protected RexSimplify simplify; // 声明 Rex 化简器，使用 protected 修饰符允许子类访问

  // ==================== 常用字面量成员变量 ====================
  
  /**
   * trueLiteral 是布尔值 TRUE 的字面量节点。
   * 
   * 这是一个 RexLiteral 节点，表示 SQL 中的 TRUE 常量。
   * 在 setUp() 方法中通过 rexBuilder.makeLiteral(true) 创建。
   * 
   * 用途：在测试中经常需要使用 TRUE 常量，例如：x AND TRUE，IS TRUE(x)
   */
  protected RexLiteral trueLiteral; // 声明布尔值 TRUE 字面量

  /**
   * falseLiteral 是布尔值 FALSE 的字面量节点。
   * 
   * 这是一个 RexLiteral 节点，表示 SQL 中的 FALSE 常量。
   * 在 setUp() 方法中通过 rexBuilder.makeLiteral(false) 创建。
   * 
   * 用途：在测试中经常需要使用 FALSE 常量，例如：x OR FALSE，IS FALSE(x)
   */
  protected RexLiteral falseLiteral; // 声明布尔值 FALSE 字面量

  /**
   * nullBool 是可空布尔类型的 NULL 字面量节点。
   * 
   * 这是一个 RexLiteral 节点，表示可空 BOOLEAN 类型的 NULL 值。
   * 在 setUp() 方法中通过 rexBuilder.makeNullLiteral(nullableBool) 创建。
   * 
   * 用途：测试 NULL 值的处理，例如：IS NULL(x)，x IS NOT NULL
   */
  protected RexLiteral nullBool; // 声明可空布尔类型的 NULL 字面量

  /**
   * nullInt 是可空整数类型的 NULL 字面量节点。
   * 
   * 这是一个 RexLiteral 节点，表示可空 INTEGER 类型的 NULL 值。
   * 在 setUp() 方法中通过 rexBuilder.makeNullLiteral(nullableInt) 创建。
   * 
   * 用途：测试整数类型的 NULL 值处理
   */
  protected RexLiteral nullInt; // 声明可空整数类型的 NULL 字面量

  /**
   * nullSmallInt 是可空短整数类型的 NULL 字面量节点。
   * 
   * 这是一个 RexLiteral 节点，表示可空 SMALLINT 类型的 NULL 值。
   * 在 setUp() 方法中通过 rexBuilder.makeNullLiteral(nullableSmallInt) 创建。
   * 
   * 用途：测试短整数类型的 NULL 值处理
   */
  protected RexLiteral nullSmallInt; // 声明可空短整数类型的 NULL 字面量

  /**
   * nullVarchar 是可空字符串类型的 NULL 字面量节点。
   * 
   * 这是一个 RexLiteral 节点，表示可空 VARCHAR 类型的 NULL 值。
   * 在 setUp() 方法中通过 rexBuilder.makeNullLiteral(nullableVarchar) 创建。
   * 
   * 用途：测试字符串类型的 NULL 值处理
   */
  protected RexLiteral nullVarchar; // 声明可空字符串类型的 NULL 字面量

  /**
   * nullDecimal 是可空十进制类型的 NULL 字面量节点。
   * 
   * 这是一个 RexLiteral 节点，表示可空 DECIMAL 类型的 NULL 值。
   * 在 setUp() 方法中通过 rexBuilder.makeNullLiteral(nullableDecimal) 创建。
   * 
   * 用途：测试十进制类型的 NULL 值处理
   */
  protected RexLiteral nullDecimal; // 声明可空十进制类型的 NULL 字面量

  /**
   * nullVarbinary 是可空二进制类型的 NULL 字面量节点。
   * 
   * 这是一个 RexLiteral 节点，表示可空 VARBINARY 类型的 NULL 值。
   * 在 setUp() 方法中通过 rexBuilder.makeNullLiteral(nullableVarbinary) 创建。
   * 
   * 用途：测试二进制类型的 NULL 值处理
   */
  protected RexLiteral nullVarbinary; // 声明可空二进制类型的 NULL 字面量

  // ==================== 类型成员变量（可空和不可空） ====================
  
  /**
   * nullableBool 是可空的布尔类型。
   * 
   * RelDataType 表示 SQL 类型，这个类型允许存储 NULL 值。
   * 在 setUp() 方法中通过 typeFactory.createTypeWithNullability(nonNullableBool, true) 创建。
   * 
   * 用途：用于创建可空布尔类型的变量和字面量
   */
  private RelDataType nullableBool; // 声明可空布尔类型

  /**
   * nonNullableBool 是不可空的布尔类型。
   * 
   * RelDataType 表示 SQL 类型，这个类型不允许存储 NULL 值。
   * 在 setUp() 方法中通过 typeFactory.createSqlType(SqlTypeName.BOOLEAN) 创建。
   * 
   * 用途：用于创建不可空布尔类型的变量和字面量
   */
  private RelDataType nonNullableBool; // 声明不可空布尔类型

  /**
   * nullableSmallInt 是可空的短整数类型。
   * 
   * RelDataType 表示 SQL 类型，这个类型允许存储 NULL 值。
   * SMALLINT 通常占用 2 字节，范围是 -32768 到 32767。
   * 在 setUp() 方法中通过 typeFactory.createTypeWithNullability(nonNullableSmallInt, true) 创建。
   * 
   * 用途：用于创建可空短整数类型的变量和字面量
   */
  private RelDataType nullableSmallInt; // 声明可空短整数类型

  /**
   * nonNullableSmallInt 是不可空的短整数类型。
   * 
   * RelDataType 表示 SQL 类型，这个类型不允许存储 NULL 值。
   * SMALLINT 通常占用 2 字节，范围是 -32768 到 32767。
   * 在 setUp() 方法中通过 typeFactory.createSqlType(SqlTypeName.SMALLINT) 创建。
   * 
   * 用途：用于创建不可空短整数类型的变量和字面量
   */
  private RelDataType nonNullableSmallInt; // 声明不可空短整数类型

  /**
   * nullableInt 是可空的整数类型。
   * 
   * RelDataType 表示 SQL 类型，这个类型允许存储 NULL 值。
   * INTEGER 通常占用 4 字节，范围是 -2147483648 到 2147483647。
   * 在 setUp() 方法中通过 typeFactory.createTypeWithNullability(nonNullableInt, true) 创建。
   * 
   * 用途：用于创建可空整数类型的变量和字面量
   */
  private RelDataType nullableInt; // 声明可空整数类型

  /**
   * nonNullableInt 是不可空的整数类型。
   * 
   * RelDataType 表示 SQL 类型，这个类型不允许存储 NULL 值。
   * INTEGER 通常占用 4 字节，范围是 -2147483648 到 2147483647。
   * 在 setUp() 方法中通过 typeFactory.createSqlType(SqlTypeName.INTEGER) 创建。
   * 
   * 用途：用于创建不可空整数类型的变量和字面量
   */
  private RelDataType nonNullableInt; // 声明不可空整数类型

  /**
   * nullableVarchar 是可空的变长字符串类型。
   * 
   * RelDataType 表示 SQL 类型，这个类型允许存储 NULL 值。
   * VARCHAR 是变长字符串类型，可以存储任意长度的字符串（受限于最大长度）。
   * 在 setUp() 方法中通过 typeFactory.createTypeWithNullability(nonNullableVarchar, true) 创建。
   * 
   * 用途：用于创建可空字符串类型的变量和字面量
   */
  private RelDataType nullableVarchar; // 声明可空变长字符串类型

  /**
   * nonNullableVarchar 是不可空的变长字符串类型。
   * 
   * RelDataType 表示 SQL 类型，这个类型不允许存储 NULL 值。
   * VARCHAR 是变长字符串类型，可以存储任意长度的字符串（受限于最大长度）。
   * 在 setUp() 方法中通过 typeFactory.createSqlType(SqlTypeName.VARCHAR) 创建。
   * 
   * 用途：用于创建不可空字符串类型的变量和字面量
   */
  private RelDataType nonNullableVarchar; // 声明不可空变长字符串类型

  /**
   * nullableDecimal 是可空的十进制类型。
   * 
   * RelDataType 表示 SQL 类型，这个类型允许存储 NULL 值。
   * DECIMAL 是精确的十进制数值类型，用于存储货币等需要精确计算的数据。
   * 在 setUp() 方法中通过 typeFactory.createTypeWithNullability(nonNullableDecimal, true) 创建。
   * 
   * 用途：用于创建可空十进制类型的变量和字面量
   */
  private RelDataType nullableDecimal; // 声明可空十进制类型

  /**
   * nonNullableDecimal 是不可空的十进制类型。
   * 
   * RelDataType 表示 SQL 类型，这个类型不允许存储 NULL 值。
   * DECIMAL 是精确的十进制数值类型，用于存储货币等需要精确计算的数据。
   * 在 setUp() 方法中通过 typeFactory.createSqlType(SqlTypeName.DECIMAL) 创建。
   * 
   * 用途：用于创建不可空十进制类型的变量和字面量
   */
  private RelDataType nonNullableDecimal; // 声明不可空十进制类型

  /**
   * nullableVarbinary 是可空的变长二进制类型。
   * 
   * RelDataType 表示 SQL 类型，这个类型允许存储 NULL 值。
   * VARBINARY 是变长二进制类型，用于存储二进制数据，如图片、文件等。
   * 在 setUp() 方法中通过 typeFactory.createTypeWithNullability(nonNullableVarbinary, true) 创建。
   * 
   * 用途：用于创建可空二进制类型的变量和字面量
   */
  private RelDataType nullableVarbinary; // 声明可空变长二进制类型

  /**
   * nonNullableVarbinary 是不可空的变长二进制类型。
   * 
   * RelDataType 表示 SQL 类型，这个类型不允许存储 NULL 值。
   * VARBINARY 是变长二进制类型，用于存储二进制数据，如图片、文件等。
   * 在 setUp() 方法中通过 typeFactory.createSqlType(SqlTypeName.VARBINARY) 创建。
   * 
   * 用途：用于创建不可空二进制类型的变量和字面量
   */
  private RelDataType nonNullableVarbinary; // 声明不可空变长二进制类型

  // ==================== 动态参数映射 ====================
  
  /**
   * dynamicParams 是一个映射，用于缓存已创建的动态参数。
   * 
   * 注意：JUnit 4 会为每个测试方法创建新的实例，因此这些结构按需初始化。
   * 
   * 映射关系：不可空类型 → 结构体类型（包含 10 个可空字段和 10 个不可空字段）的动态参数
   * 
   * 例如，对于 INTEGER 类型：
   * - 键：nonNullableInt（不可空的 INTEGER 类型）
   * - 值：RexDynamicParam，类型为结构体，包含 int0-int9（可空）和 notNullInt0-notNullInt9（不可空）
   * 
   * 设计目的：
   * 1. 避免重复创建相同类型的动态参数
   * 2. 确保相同类型使用相同的动态参数，保持一致性
   * 3. 通过结构体类型提供多个字段，支持创建多个不同的变量
   * 
   * 使用方式：
   * - 第一次调用 getDynamicParam() 时，dynamicParams 为 null，会初始化为空的 HashMap
   * - 后续调用会从缓存中获取已创建的动态参数
   */
  private Map<RelDataType, RexDynamicParam> dynamicParams; // 声明动态参数映射

  // ==================== 初始化方法 ====================
  
  /**
   * setUp 方法使用 @BeforeEach 注解，在每个测试方法执行前自动调用。
   * 
   * 这个方法负责初始化测试所需的所有核心组件：
   * 1. 创建类型工厂（JavaTypeFactory）
   * 2. 创建 Rex 构建器（RexBuilder）
   * 3. 创建数据上下文（DataContext），提供时区和时间戳信息
   * 4. 创建 Rex 执行器（RexExecutor）
   * 5. 创建 Rex 化简器（RexSimplify）
   * 6. 创建常用字面量（TRUE、FALSE、各种类型的 NULL）
   * 7. 创建常用的可空和不可空类型
   * 
   * 初始化顺序很重要，因为某些组件依赖于其他组件：
   * - rexBuilder 依赖于 typeFactory
   * - executor 依赖于 dataContext
   * - simplify 依赖于 rexBuilder 和 executor
   * - 各种字面量依赖于 rexBuilder 和类型
   */
  @BeforeEach public void setUp() { // 使用 JUnit 5 的 BeforeEach 注解，在每个测试方法执行前调用
    typeFactory = new JavaTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建 Java 类型工厂实例，使用默认的类型系统
    rexBuilder = new RexBuilder(typeFactory); // 创建 Rex 构建器，传入类型工厂作为参数
    final DataContext dataContext = // 创建数据上下文，用于提供执行表达式时所需的上下文信息
        DataContexts.of( // 使用 DataContexts 工具类创建 DataContext 实例
            ImmutableMap.of( // 创建不可变的映射，包含两个键值对
                DataContext.Variable.TIME_ZONE.camelName, // 第一个键：时区变量的驼峰命名名称
                TimeZone.getTimeZone("America/Los_Angeles"), // 第一个值：洛杉矶时区
                DataContext.Variable.CURRENT_TIMESTAMP.camelName, // 第二个键：当前时间戳变量的驼峰命名名称
                1311120000000L)); // 第二个值：时间戳值（毫秒）
    executor = new RexExecutorImpl(dataContext); // 创建 Rex 执行器实现，传入数据上下文
    simplify = // 创建 Rex 化简器
        new RexSimplify(rexBuilder, RelOptPredicateList.EMPTY, executor) // 传入 Rex 构建器、空的谓词列表和执行器
            .withParanoid(true); // 启用偏执模式，进行更严格的化简
    trueLiteral = rexBuilder.makeLiteral(true); // 创建布尔值 TRUE 的字面量节点
    falseLiteral = rexBuilder.makeLiteral(false); // 创建布尔值 FALSE 的字面量节点

    // 初始化整数类型和 NULL 字面量
    nonNullableInt = typeFactory.createSqlType(SqlTypeName.INTEGER); // 创建不可空的 INTEGER 类型
    nullableInt = typeFactory.createTypeWithNullability(nonNullableInt, true); // 基于不可空类型创建可空的 INTEGER 类型
    nullInt = rexBuilder.makeNullLiteral(nullableInt); // 创建可空 INTEGER 类型的 NULL 字面量

    // 初始化短整数类型和 NULL 字面量
    nonNullableSmallInt = typeFactory.createSqlType(SqlTypeName.SMALLINT); // 创建不可空的 SMALLINT 类型
    nullableSmallInt = typeFactory.createTypeWithNullability(nonNullableSmallInt, true); // 基于不可空类型创建可空的 SMALLINT 类型
    nullSmallInt = rexBuilder.makeNullLiteral(nullableSmallInt); // 创建可空 SMALLINT 类型的 NULL 字面量

    // 初始化布尔类型和 NULL 字面量
    nonNullableBool = typeFactory.createSqlType(SqlTypeName.BOOLEAN); // 创建不可空的 BOOLEAN 类型
    nullableBool = typeFactory.createTypeWithNullability(nonNullableBool, true); // 基于不可空类型创建可空的 BOOLEAN 类型
    nullBool = rexBuilder.makeNullLiteral(nullableBool); // 创建可空 BOOLEAN 类型的 NULL 字面量

    // 初始化变长字符串类型和 NULL 字面量
    nonNullableVarchar = typeFactory.createSqlType(SqlTypeName.VARCHAR); // 创建不可空的 VARCHAR 类型
    nullableVarchar = typeFactory.createTypeWithNullability(nonNullableVarchar, true); // 基于不可空类型创建可空的 VARCHAR 类型
    nullVarchar = rexBuilder.makeNullLiteral(nullableVarchar); // 创建可空 VARCHAR 类型的 NULL 字面量

    // 初始化十进制类型和 NULL 字面量
    nonNullableDecimal = typeFactory.createSqlType(SqlTypeName.DECIMAL); // 创建不可空的 DECIMAL 类型
    nullableDecimal = typeFactory.createTypeWithNullability(nonNullableDecimal, true); // 基于不可空类型创建可空的 DECIMAL 类型
    nullDecimal = rexBuilder.makeNullLiteral(nullableDecimal); // 创建可空 DECIMAL 类型的 NULL 字面量

    // 初始化变长二进制类型和 NULL 字面量
    nonNullableVarbinary = typeFactory.createSqlType(SqlTypeName.VARBINARY); // 创建不可空的 VARBINARY 类型
    nullableVarbinary = typeFactory.createTypeWithNullability(nonNullableVarbinary, true); // 基于不可空类型创建可空的 VARBINARY 类型
    nullVarbinary = rexBuilder.makeNullLiteral(nullableVarbinary); // 创建可空 VARBINARY 类型的 NULL 字面量
  }

  // ==================== 私有辅助方法 ====================
  
  /**
   * getDynamicParam 方法用于获取或创建指定类型的动态参数。
   * 
   * 动态参数（RexDynamicParam）表示 SQL 中的参数占位符（如 ?0、?1 等）。
   * 这个方法创建的结构体类型包含：
   * - 10 个可空字段：fieldNamePrefix + 0 到 fieldNamePrefix + 9
   * - 10 个不可空字段：notNull + 首字母大写的 fieldNamePrefix + 0 到 ... + 9
   * 
   * 例如，如果 fieldNamePrefix 是 "int"，则创建的结构体包含：
   * - int0 (可空), int1 (可空), ..., int9 (可空)
   * - notNullInt0 (不可空), notNullInt1 (不可空), ..., notNullInt9 (不可空)
   * 
   * 参数：
   * @param type 基础类型（不可空），用于创建字段类型
   * @param fieldNamePrefix 字段名前缀，用于生成字段名称
   * 
   * 返回：
   * @return RexDynamicParam 表示参数占位符，类型为结构体
   * 
   * 实现细节：
   * - 使用延迟初始化模式，dynamicParams 第一次使用时才创建
   * - 使用 computeIfAbsent 方法，如果缓存中存在则直接返回，否则创建新的
   * - 动态参数的索引固定为 0
   */
  private RexDynamicParam getDynamicParam(RelDataType type, String fieldNamePrefix) { // 获取或创建动态参数
    if (dynamicParams == null) { // 检查 dynamicParams 是否已初始化
      dynamicParams = new HashMap<>(); // 如果未初始化，创建新的 HashMap 实例
    }
    return dynamicParams.computeIfAbsent(type, k -> { // 使用 computeIfAbsent 方法，如果 type 不在映射中则创建新的动态参数
      RelDataType nullableType = typeFactory.createTypeWithNullability(k, true); // 创建可空类型
      RelDataTypeFactory.Builder builder = typeFactory.builder(); // 创建类型构建器，用于构建结构体类型
      for (int i = 0; i < MAX_FIELDS; i++) { // 循环创建 MAX_FIELDS 个可空字段
        builder.add(fieldNamePrefix + i, nullableType); // 添加可空字段，字段名为 fieldNamePrefix + i
      }
      String notNullPrefix = "notNull" // 创建不可空字段的前缀
          + Character.toUpperCase(fieldNamePrefix.charAt(0)) // 将字段名前缀的首字母大写
          + fieldNamePrefix.substring(1); // 拼接字段名前缀的剩余部分

      for (int i = 0; i < MAX_FIELDS; i++) { // 循环创建 MAX_FIELDS 个不可空字段
        builder.add(notNullPrefix + i, k); // 添加不可空字段，字段名为 notNullPrefix + i
      }
      return rexBuilder.makeDynamicParam(builder.build(), 0); // 创建动态参数，索引为 0，类型为构建的结构体类型
    });
  }

  // ==================== 操作符封装方法 ====================
  
  /**
   * isNull 方法创建 IS NULL 操作符调用。
   * 
   * IS NULL 是 SQL 中用于判断表达式是否为 NULL 的操作符。
   * 例如：x IS NULL
   * 
   * 参数：
   * @param node 要判断的表达式节点
   * 
   * 返回：
   * @return IS NULL 操作符调用节点
   */
  protected RexNode isNull(RexNode node) { // 创建 IS NULL 操作符调用
    return rexBuilder.makeCall(SqlStdOperatorTable.IS_NULL, node); // 使用 rexBuilder 创建 IS NULL 操作符调用
  }

  /**
   * isUnknown 方法创建 IS UNKNOWN 操作符调用。
   * 
   * IS UNKNOWN 是 SQL 中用于判断布尔表达式是否为 UNKNOWN（即 NULL）的操作符。
   * UNKNOWN 是 SQL 三值逻辑中的第三个值（TRUE、FALSE、UNKNOWN）。
   * 例如：x IS UNKNOWN
   * 
   * 参数：
   * @param node 要判断的表达式节点
   * 
   * 返回：
   * @return IS UNKNOWN 操作符调用节点
   */
  protected RexNode isUnknown(RexNode node) { // 创建 IS UNKNOWN 操作符调用
    return rexBuilder.makeCall(SqlStdOperatorTable.IS_UNKNOWN, node); // 使用 rexBuilder 创建 IS UNKNOWN 操作符调用
  }

  /**
   * isNotNull 方法创建 IS NOT NULL 操作符调用。
   * 
   * IS NOT NULL 是 SQL 中用于判断表达式是否不为 NULL 的操作符。
   * 例如：x IS NOT NULL
   * 
   * 参数：
   * @param node 要判断的表达式节点
   * 
   * 返回：
   * @return IS NOT NULL 操作符调用节点
   */
  protected RexNode isNotNull(RexNode node) { // 创建 IS NOT NULL 操作符调用
    return rexBuilder.makeCall(SqlStdOperatorTable.IS_NOT_NULL, node); // 使用 rexBuilder 创建 IS NOT NULL 操作符调用
  }

  /**
   * isFalse 方法创建 IS FALSE 操作符调用。
   * 
   * IS FALSE 是 SQL 中用于判断布尔表达式是否为 FALSE 的操作符。
   * 注意：这个方法会断言节点类型为 BOOLEAN。
   * 例如：x IS FALSE
   * 
   * 参数：
   * @param node 要判断的表达式节点，类型必须是 BOOLEAN
   * 
   * 返回：
   * @return IS FALSE 操作符调用节点
   */
  protected RexNode isFalse(RexNode node) { // 创建 IS FALSE 操作符调用
    assert node.getType().getSqlTypeName() == SqlTypeName.BOOLEAN; // 断言节点类型为 BOOLEAN，如果不是则抛出 AssertionError
    return rexBuilder.makeCall(SqlStdOperatorTable.IS_FALSE, node); // 使用 rexBuilder 创建 IS FALSE 操作符调用
  }

  /**
   * isNotFalse 方法创建 IS NOT FALSE 操作符调用。
   * 
   * IS NOT FALSE 是 SQL 中用于判断布尔表达式是否不为 FALSE 的操作符。
   * 注意：这个方法会断言节点类型为 BOOLEAN。
   * IS NOT FALSE 等价于 IS TRUE OR IS UNKNOWN。
   * 例如：x IS NOT FALSE
   * 
   * 参数：
   * @param node 要判断的表达式节点，类型必须是 BOOLEAN
   * 
   * 返回：
   * @return IS NOT FALSE 操作符调用节点
   */
  protected RexNode isNotFalse(RexNode node) { // 创建 IS NOT FALSE 操作符调用
    assert node.getType().getSqlTypeName() == SqlTypeName.BOOLEAN; // 断言节点类型为 BOOLEAN，如果不是则抛出 AssertionError
    return rexBuilder.makeCall(SqlStdOperatorTable.IS_NOT_FALSE, node); // 使用 rexBuilder 创建 IS NOT FALSE 操作符调用
  }

  /**
   * isTrue 方法创建 IS TRUE 操作符调用。
   * 
   * IS TRUE 是 SQL 中用于判断布尔表达式是否为 TRUE 的操作符。
   * 注意：这个方法会断言节点类型为 BOOLEAN。
   * 例如：x IS TRUE
   * 
   * 参数：
   * @param node 要判断的表达式节点，类型必须是 BOOLEAN
   * 
   * 返回：
   * @return IS TRUE 操作符调用节点
   */
  protected RexNode isTrue(RexNode node) { // 创建 IS TRUE 操作符调用
    assert node.getType().getSqlTypeName() == SqlTypeName.BOOLEAN; // 断言节点类型为 BOOLEAN，如果不是则抛出 AssertionError
    return rexBuilder.makeCall(SqlStdOperatorTable.IS_TRUE, node); // 使用 rexBuilder 创建 IS TRUE 操作符调用
  }

  /**
   * isNotTrue 方法创建 IS NOT TRUE 操作符调用。
   * 
   * IS NOT TRUE 是 SQL 中用于判断布尔表达式是否不为 TRUE 的操作符。
   * 注意：这个方法会断言节点类型为 BOOLEAN。
   * IS NOT TRUE 等价于 IS FALSE OR IS UNKNOWN。
   * 例如：x IS NOT TRUE
   * 
   * 参数：
   * @param node 要判断的表达式节点，类型必须是 BOOLEAN
   * 
   * 返回：
   * @return IS NOT TRUE 操作符调用节点
   */
  protected RexNode isNotTrue(RexNode node) { // 创建 IS NOT TRUE 操作符调用
    assert node.getType().getSqlTypeName() == SqlTypeName.BOOLEAN; // 断言节点类型为 BOOLEAN，如果不是则抛出 AssertionError
    return rexBuilder.makeCall(SqlStdOperatorTable.IS_NOT_TRUE, node); // 使用 rexBuilder 创建 IS NOT TRUE 操作符调用
  }

  /**
   * isDistinctFrom 方法创建 IS DISTINCT FROM 操作符调用。
   * 
   * IS DISTINCT FROM 是 SQL 中用于判断两个值是否不同的操作符。
   * 与普通的 != 操作符不同，IS DISTINCT FROM 认为 NULL 不等于 NULL。
   * 例如：x IS DISTINCT FROM y
   * 
   * 参数：
   * @param a 第一个表达式节点
   * @param b 第二个表达式节点
   * 
   * 返回：
   * @return IS DISTINCT FROM 操作符调用节点
   */
  protected RexNode isDistinctFrom(RexNode a, RexNode b) { // 创建 IS DISTINCT FROM 操作符调用
    return rexBuilder.makeCall(SqlStdOperatorTable.IS_DISTINCT_FROM, a, b); // 使用 rexBuilder 创建 IS DISTINCT FROM 操作符调用
  }

  /**
   * isNotDistinctFrom 方法创建 IS NOT DISTINCT FROM 操作符调用。
   * 
   * IS NOT DISTINCT FROM 是 SQL 中用于判断两个值是否相同的操作符。
   * 与普通的 = 操作符不同，IS NOT DISTINCT FROM 认为 NULL 等于 NULL。
   * 例如：x IS NOT DISTINCT FROM y
   * 
   * 参数：
   * @param a 第一个表达式节点
   * @param b 第二个表达式节点
   * 
   * 返回：
   * @return IS NOT DISTINCT FROM 操作符调用节点
   */
  protected RexNode isNotDistinctFrom(RexNode a, RexNode b) { // 创建 IS NOT DISTINCT FROM 操作符调用
    return rexBuilder.makeCall(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, a, b); // 使用 rexBuilder 创建 IS NOT DISTINCT FROM 操作符调用
  }

  /**
   * nullIf 方法创建 NULLIF 操作符调用。
   * 
   * NULLIF 是 SQL 中的条件函数，如果两个参数相等则返回 NULL，否则返回第一个参数。
   * NULLIF(expr1, expr2) 等价于 CASE WHEN expr1 = expr2 THEN NULL ELSE expr1 END
   * 例如：NULLIF(x, y)
   * 
   * 参数：
   * @param node1 第一个表达式节点
   * @param node2 第二个表达式节点
   * 
   * 返回：
   * @return NULLIF 操作符调用节点
   */
  protected RexNode nullIf(RexNode node1, RexNode node2) { // 创建 NULLIF 操作符调用
    return rexBuilder.makeCall(SqlStdOperatorTable.NULLIF, node1, node2); // 使用 rexBuilder 创建 NULLIF 操作符调用
  }

  /**
   * not 方法创建 NOT 操作符调用。
   * 
   * NOT 是 SQL 中的逻辑非操作符，用于反转布尔值。
   * NOT TRUE = FALSE，NOT FALSE = TRUE，NOT UNKNOWN = UNKNOWN
   * 例如：NOT x
   * 
   * 参数：
   * @param node 要取反的表达式节点
   * 
   * 返回：
   * @return NOT 操作符调用节点
   */
  protected RexNode not(RexNode node) { // 创建 NOT 操作符调用
    return rexBuilder.makeCall(SqlStdOperatorTable.NOT, node); // 使用 rexBuilder 创建 NOT 操作符调用
  }

  /**
   * unaryMinus 方法创建一元负号操作符调用。
   * 
   * 一元负号是 SQL 中的算术操作符，用于取数值的负值。
   * 例如：-x
   * 
   * 参数：
   * @param node 要取负的表达式节点
   * 
   * 返回：
   * @return 一元负号操作符调用节点
   */
  protected RexNode unaryMinus(RexNode node) { // 创建一元负号操作符调用
    return rexBuilder.makeCall(SqlStdOperatorTable.UNARY_MINUS, node); // 使用 rexBuilder 创建一元负号操作符调用
  }

  /**
   * unaryPlus 方法创建一元正号操作符调用。
   * 
   * 一元正号是 SQL 中的算术操作符，用于保持数值的正值（通常不改变值）。
   * 例如：+x
   * 
   * 参数：
   * @param node 要取正的表达式节点
   * 
   * 返回：
   * @return 一元正号操作符调用节点
   */
  protected RexNode unaryPlus(RexNode node) { // 创建一元正号操作符调用
    return rexBuilder.makeCall(SqlStdOperatorTable.UNARY_PLUS, node); // 使用 rexBuilder 创建一元正号操作符调用
  }

  /**
   * and 方法创建 AND 操作符调用（可变参数版本）。
   * 
   * AND 是 SQL 中的逻辑与操作符，用于连接多个布尔表达式。
   * 只有当所有操作数都为 TRUE 时，结果才为 TRUE。
   * 例如：x AND y AND z
   * 
   * 注意：这个方法不会扁平化嵌套的 AND，我们希望测试输入包含嵌套的 AND。
   * 
   * 参数：
   * @param nodes 要连接的表达式节点数组
   * 
   * 返回：
   * @return AND 操作符调用节点
   */
  protected RexNode and(RexNode... nodes) { // 创建 AND 操作符调用（可变参数版本）
    return and(ImmutableList.copyOf(nodes)); // 调用另一个 and 方法，将数组转换为不可变列表
  }

  /**
   * and 方法创建 AND 操作符调用（可迭代参数版本）。
   * 
   * AND 是 SQL 中的逻辑与操作符，用于连接多个布尔表达式。
   * 只有当所有操作数都为 TRUE 时，结果才为 TRUE。
   * 例如：x AND y AND z
   * 
   * 注意：这个方法不会扁平化嵌套的 AND，我们希望测试输入包含嵌套的 AND。
   * 
   * 参数：
   * @param nodes 要连接的表达式节点集合
   * 
   * 返回：
   * @return AND 操作符调用节点
   */
  protected RexNode and(Iterable<? extends RexNode> nodes) { // 创建 AND 操作符调用（可迭代参数版本）
    // 不会扁平化嵌套的 AND。我们希望测试输入包含嵌套的 AND。
    return rexBuilder.makeCall(SqlStdOperatorTable.AND, // 使用 rexBuilder 创建 AND 操作符调用
        ImmutableList.copyOf(nodes)); // 将可迭代集合转换为不可变列表
  }

  /**
   * or 方法创建 OR 操作符调用（可变参数版本）。
   * 
   * OR 是 SQL 中的逻辑或操作符，用于连接多个布尔表达式。
   * 只要有一个操作数为 TRUE，结果就为 TRUE。
   * 例如：x OR y OR z
   * 
   * 注意：这个方法不会扁平化嵌套的 OR，我们希望测试输入包含嵌套的 OR。
   * 
   * 参数：
   * @param nodes 要连接的表达式节点数组
   * 
   * 返回：
   * @return OR 操作符调用节点
   */
  protected RexNode or(RexNode... nodes) { // 创建 OR 操作符调用（可变参数版本）
    return or(ImmutableList.copyOf(nodes)); // 调用另一个 or 方法，将数组转换为不可变列表
  }

  /**
   * or 方法创建 OR 操作符调用（可迭代参数版本）。
   * 
   * OR 是 SQL 中的逻辑或操作符，用于连接多个布尔表达式。
   * 只要有一个操作数为 TRUE，结果就为 TRUE。
   * 例如：x OR y OR z
   * 
   * 注意：这个方法不会扁平化嵌套的 OR，我们希望测试输入包含嵌套的 OR。
   * 
   * 参数：
   * @param nodes 要连接的表达式节点集合
   * 
   * 返回：
   * @return OR 操作符调用节点
   */
  protected RexNode or(Iterable<? extends RexNode> nodes) { // 创建 OR 操作符调用（可迭代参数版本）
    // 不会扁平化嵌套的 OR。我们希望测试输入包含嵌套的 OR。
    return rexBuilder.makeCall(SqlStdOperatorTable.OR, // 使用 rexBuilder 创建 OR 操作符调用
        ImmutableList.copyOf(nodes)); // 将可迭代集合转换为不可变列表
  }

  /**
   * case_ 方法创建 CASE 操作符调用（可变参数版本）。
   * 
   * CASE 是 SQL 中的条件表达式，类似于编程语言中的 switch-case 语句。
   * CASE WHEN condition1 THEN result1 WHEN condition2 THEN result2 ... ELSE default END
   * 例如：case_(x, 1, y, 2, z)
   * 
   * 参数：
   * @param nodes CASE 表达式的节点数组（交替的 WHEN 条件和 THEN 结果）
   * 
   * 返回：
   * @return CASE 操作符调用节点
   */
  protected RexNode case_(RexNode... nodes) { // 创建 CASE 操作符调用（可变参数版本）
    return case_(ImmutableList.copyOf(nodes)); // 调用另一个 case_ 方法，将数组转换为不可变列表
  }

  /**
   * case_ 方法创建 CASE 操作符调用（可迭代参数版本）。
   * 
   * CASE 是 SQL 中的条件表达式，类似于编程语言中的 switch-case 语句。
   * CASE WHEN condition1 THEN result1 WHEN condition2 THEN result2 ... ELSE default END
   * 例如：case_(x, 1, y, 2, z)
   * 
   * 参数：
   * @param nodes CASE 表达式的节点集合（交替的 WHEN 条件和 THEN 结果）
   * 
   * 返回：
   * @return CASE 操作符调用节点
   */
  protected RexNode case_(Iterable<? extends RexNode> nodes) { // 创建 CASE 操作符调用（可迭代参数版本）
    return rexBuilder.makeCall(SqlStdOperatorTable.CASE, ImmutableList.copyOf(nodes)); // 使用 rexBuilder 创建 CASE 操作符调用
  }

  /**
   * abstractCast 方法创建抽象的 CAST 操作符调用。
   * 
   * CAST 是 SQL 中的类型转换操作符，用于将一个值转换为指定的类型。
   * 这个方法启用了创建 "CAST(42 nullable int)" 这样的表达式。
   * 
   * 与 cast() 方法不同，abstractCast() 不会尝试展开类型转换，总是返回一个 CAST 调用。
   * 
   * 参数：
   * @param e 输入表达式节点
   * @param type 要转换到的目标类型
   * 
   * 返回：
   * @return CAST 操作符调用节点
   */
  protected RexNode abstractCast(RexNode e, RelDataType type) { // 创建抽象的 CAST 操作符调用
    return rexBuilder.makeAbstractCast(type, e, false); // 使用 rexBuilder 创建抽象的 CAST 操作符调用，不保留可空性
  }

  /**
   * cast 方法创建 CAST 操作符调用，尝试展开转换。
   * 
   * CAST 是 SQL 中的类型转换操作符，用于将一个值转换为指定的类型。
   * 这个方法尝试展开类型转换，因此结果可能不是 CAST 操作符调用，
   * 而可能是其他节点，如 RexLiteral。
   * 
   * 例如：CAST(42 AS INTEGER) 可能会被展开为字面量 42。
   * 
   * 参数：
   * @param e 输入表达式节点
   * @param type 要转换到的目标类型
   * 
   * 返回：
   * @return 转换为目标类型的表达式节点（可能是 CAST 调用，也可能是其他节点）
   */
  protected RexNode cast(RexNode e, RelDataType type) { // 创建 CAST 操作符调用，尝试展开转换
    return rexBuilder.makeCast(type, e); // 使用 rexBuilder 创建 CAST 操作符调用，会尝试展开转换
  }

  /**
   * eq 方法创建等于操作符调用（=）。
   * 
   * = 是 SQL 中的等于比较操作符，用于判断两个值是否相等。
   * 例如：x = y
   * 
   * 参数：
   * @param n1 第一个表达式节点
   * @param n2 第二个表达式节点
   * 
   * 返回：
   * @return 等于操作符调用节点
   */
  protected RexNode eq(RexNode n1, RexNode n2) { // 创建等于操作符调用
    return rexBuilder.makeCall(SqlStdOperatorTable.EQUALS, n1, n2); // 使用 rexBuilder 创建等于操作符调用
  }

  /**
   * ne 方法创建不等于操作符调用（<> 或 !=）。
   * 
   * <> 是 SQL 中的不等于比较操作符，用于判断两个值是否不相等。
   * 例如：x <> y
   * 
   * 参数：
   * @param n1 第一个表达式节点
   * @param n2 第二个表达式节点
   * 
   * 返回：
   * @return 不等于操作符调用节点
   */
  protected RexNode ne(RexNode n1, RexNode n2) { // 创建不等于操作符调用
    return rexBuilder.makeCall(SqlStdOperatorTable.NOT_EQUALS, n1, n2); // 使用 rexBuilder 创建不等于操作符调用
  }

  /**
   * le 方法创建小于等于操作符调用（<=）。
   * 
   * <= 是 SQL 中的小于等于比较操作符，用于判断第一个值是否小于或等于第二个值。
   * 例如：x <= y
   * 
   * 参数：
   * @param n1 第一个表达式节点
   * @param n2 第二个表达式节点
   * 
   * 返回：
   * @return 小于等于操作符调用节点
   */
  protected RexNode le(RexNode n1, RexNode n2) { // 创建小于等于操作符调用
    return rexBuilder.makeCall(SqlStdOperatorTable.LESS_THAN_OR_EQUAL, n1, n2); // 使用 rexBuilder 创建小于等于操作符调用
  }

  /**
   * lt 方法创建小于操作符调用（<）。
   * 
   * < 是 SQL 中的小于比较操作符，用于判断第一个值是否小于第二个值。
   * 例如：x < y
   * 
   * 参数：
   * @param n1 第一个表达式节点
   * @param n2 第二个表达式节点
   * 
   * 返回：
   * @return 小于操作符调用节点
   */
  protected RexNode lt(RexNode n1, RexNode n2) { // 创建小于操作符调用
    return rexBuilder.makeCall(SqlStdOperatorTable.LESS_THAN, n1, n2); // 使用 rexBuilder 创建小于操作符调用
  }

  /**
   * ge 方法创建大于等于操作符调用（>=）。
   * 
   * >= 是 SQL 中的大于等于比较操作符，用于判断第一个值是否大于或等于第二个值。
   * 例如：x >= y
   * 
   * 参数：
   * @param n1 第一个表达式节点
   * @param n2 第二个表达式节点
   * 
   * 返回：
   * @return 大于等于操作符调用节点
   */
  protected RexNode ge(RexNode n1, RexNode n2) { // 创建大于等于操作符调用
    return rexBuilder.makeCall(SqlStdOperatorTable.GREATER_THAN_OR_EQUAL, n1, n2); // 使用 rexBuilder 创建大于等于操作符调用
  }

  /**
   * gt 方法创建大于操作符调用（>）。
   * 
   * > 是 SQL 中的大于比较操作符，用于判断第一个值是否大于第二个值。
   * 例如：x > y
   * 
   * 参数：
   * @param n1 第一个表达式节点
   * @param n2 第二个表达式节点
   * 
   * 返回：
   * @return 大于操作符调用节点
   */
  protected RexNode gt(RexNode n1, RexNode n2) { // 创建大于操作符调用
    return rexBuilder.makeCall(SqlStdOperatorTable.GREATER_THAN, n1, n2); // 使用 rexBuilder 创建大于操作符调用
  }

  /**
   * like 方法创建 LIKE 操作符调用（简单模式匹配）。
   * 
   * LIKE 是 SQL 中的模式匹配操作符，用于字符串匹配。
   * 支持通配符：%（匹配任意字符序列）和 _（匹配单个字符）。
   * 例如：name LIKE '%John%'
   * 
   * 参数：
   * @param ref 要匹配的字符串表达式节点
   * @param pattern 模式表达式节点
   * 
   * 返回：
   * @return LIKE 操作符调用节点
   */
  protected RexNode like(RexNode ref, RexNode pattern) { // 创建 LIKE 操作符调用（简单模式匹配）
    return rexBuilder.makeCall(SqlStdOperatorTable.LIKE, ref, pattern); // 使用 rexBuilder 创建 LIKE 操作符调用
  }

  /**
   * similar 方法创建 SIMILAR TO 操作符调用。
   * 
   * SIMILAR TO 是 SQL 中的正则表达式匹配操作符，类似于 LIKE 但支持更复杂的模式。
   * 支持正则表达式语法。
   * 例如：name SIMILAR TO '%(John|Jane)%'
   * 
   * 参数：
   * @param ref 要匹配的字符串表达式节点
   * @param pattern 模式表达式节点
   * 
   * 返回：
   * @return SIMILAR TO 操作符调用节点
   */
  protected RexNode similar(RexNode ref, RexNode pattern) { // 创建 SIMILAR TO 操作符调用
    return rexBuilder.makeCall(SqlStdOperatorTable.SIMILAR_TO, ref, pattern); // 使用 rexBuilder 创建 SIMILAR TO 操作符调用
  }

  /**
   * like 方法创建 LIKE 操作符调用（带转义字符）。
   * 
   * LIKE 是 SQL 中的模式匹配操作符，用于字符串匹配。
   * 支持通配符：%（匹配任意字符序列）和 _（匹配单个字符）。
   * 转义字符用于转义通配符本身。
   * 例如：name LIKE '%100\%' ESCAPE '\'
   * 
   * 参数：
   * @param ref 要匹配的字符串表达式节点
   * @param pattern 模式表达式节点
   * @param escape 转义字符表达式节点
   * 
   * 返回：
   * @return LIKE 操作符调用节点
   */
  protected RexNode like(RexNode ref, RexNode pattern, RexNode escape) { // 创建 LIKE 操作符调用（带转义字符）
    return rexBuilder.makeCall(SqlStdOperatorTable.LIKE, ref, pattern, escape); // 使用 rexBuilder 创建 LIKE 操作符调用
  }

  /**
   * plus 方法创建加法操作符调用（+）。
   * 
   * + 是 SQL 中的加法操作符，用于数值加法或字符串连接。
   * 例如：x + y
   * 
   * 参数：
   * @param n1 第一个表达式节点
   * @param n2 第二个表达式节点
   * 
   * 返回：
   * @return 加法操作符调用节点
   */
  protected RexNode plus(RexNode n1, RexNode n2) { // 创建加法操作符调用
    return rexBuilder.makeCall(SqlStdOperatorTable.PLUS, n1, n2); // 使用 rexBuilder 创建加法操作符调用
  }

  /**
   * mul 方法创建乘法操作符调用（*）。
   * 
   * * 是 SQL 中的乘法操作符，用于数值乘法。
   * 例如：x * y
   * 
   * 参数：
   * @param n1 第一个表达式节点
   * @param n2 第二个表达式节点
   * 
   * 返回：
   * @return 乘法操作符调用节点
   */
  protected RexNode mul(RexNode n1, RexNode n2) { // 创建乘法操作符调用
    return rexBuilder.makeCall(SqlStdOperatorTable.MULTIPLY, n1, n2); // 使用 rexBuilder 创建乘法操作符调用
  }

  /**
   * coalesce 方法创建 COALESCE 操作符调用。
   * 
   * COALESCE 是 SQL 中的空值处理函数，返回第一个非 NULL 的参数。
   * 如果所有参数都是 NULL，则返回 NULL。
   * 例如：COALESCE(x, y, z)
   * 
   * 参数：
   * @param nodes 要检查的表达式节点数组
   * 
   * 返回：
   * @return COALESCE 操作符调用节点
   */
  protected RexNode coalesce(RexNode... nodes) { // 创建 COALESCE 操作符调用
    return rexBuilder.makeCall(SqlStdOperatorTable.COALESCE, nodes); // 使用 rexBuilder 创建 COALESCE 操作符调用
  }

  /**
   * divInt 方法创建整数除法操作符调用（DIV）。
   * 
   * DIV 是 SQL 中的整数除法操作符，返回两个整数相除的整数部分（向下取整）。
   * 例如：7 DIV 2 = 3
   * 
   * 参数：
   * @param n1 被除数表达式节点
   * @param n2 除数表达式节点
   * 
   * 返回：
   * @return 整数除法操作符调用节点
   */
  protected RexNode divInt(RexNode n1, RexNode n2) { // 创建整数除法操作符调用
    return rexBuilder.makeCall(SqlStdOperatorTable.DIVIDE_INTEGER, n1, n2); // 使用 rexBuilder 创建整数除法操作符调用
  }

  /**
   * div 方法创建除法操作符调用（/）。
   * 
   * / 是 SQL 中的除法操作符，用于数值除法。
   * 例如：x / y
   * 
   * 参数：
   * @param n1 被除数表达式节点
   * @param n2 除数表达式节点
   * 
   * 返回：
   * @return 除法操作符调用节点
   */
  protected RexNode div(RexNode n1, RexNode n2) { // 创建除法操作符调用
    return rexBuilder.makeCall(SqlStdOperatorTable.DIVIDE, n1, n2); // 使用 rexBuilder 创建除法操作符调用
  }

  /**
   * sub 方法创建减法操作符调用（-）。
   * 
   * - 是 SQL 中的减法操作符，用于数值减法。
   * 例如：x - y
   * 
   * 参数：
   * @param n1 被减数表达式节点
   * @param n2 减数表达式节点
   * 
   * 返回：
   * @return 减法操作符调用节点
   */
  protected RexNode sub(RexNode n1, RexNode n2) { // 创建减法操作符调用
    return rexBuilder.makeCall(SqlStdOperatorTable.MINUS, n1, n2); // 使用 rexBuilder 创建减法操作符调用
  }

  /**
   * add 方法创建加法操作符调用（+）。
   * 
   * + 是 SQL 中的加法操作符，用于数值加法或字符串连接。
   * 这个方法与 plus() 方法功能相同。
   * 例如：x + y
   * 
   * 参数：
   * @param n1 第一个表达式节点
   * @param n2 第二个表达式节点
   * 
   * 返回：
   * @return 加法操作符调用节点
   */
  protected RexNode add(RexNode n1, RexNode n2) { // 创建加法操作符调用
    return rexBuilder.makeCall(SqlStdOperatorTable.PLUS, n1, n2); // 使用 rexBuilder 创建加法操作符调用
  }
  /**
   * greatest 方法创建 GREATEST 操作符调用。
   * 
   * GREATEST 是 SQL 中的最大值函数，返回参数中的最大值。
   * 例如：GREATEST(x, y, z)
   * 
   * 参数：
   * @param nodes 要比较的表达式节点数组
   * 
   * 返回：
   * @return GREATEST 操作符调用节点
   */
  protected RexNode greatest(RexNode... nodes) { // 创建 GREATEST 操作符调用
    return rexBuilder.makeCall(SqlLibraryOperators.GREATEST, nodes); // 使用 rexBuilder 创建 GREATEST 操作符调用
  }

  /**
   * least 方法创建 LEAST 操作符调用。
   * 
   * LEAST 是 SQL 中的最小值函数，返回参数中的最小值。
   * 例如：LEAST(x, y, z)
   * 
   * 参数：
   * @param nodes 要比较的表达式节点数组
   * 
   * 返回：
   * @return LEAST 操作符调用节点
   */
  protected RexNode least(RexNode... nodes) { // 创建 LEAST 操作符调用
    return rexBuilder.makeCall(SqlLibraryOperators.LEAST, nodes); // 使用 rexBuilder 创建 LEAST 操作符调用
  }

  /**
   * m2v 方法创建 M2V（Multi-Valued to Vector）操作符调用。
   * 
   * M2V 是 Calcite 内部操作符，用于将多值转换为向量。
   * 这是 Calcite 内部使用的操作符，不是标准 SQL 操作符。
   * 
   * 参数：
   * @param n 要转换的表达式节点
   * 
   * 返回：
   * @return M2V 操作符调用节点
   */
  protected RexNode m2v(RexNode n) { // 创建 M2V 操作符调用
    return rexBuilder.makeCall(SqlInternalOperators.M2V, n); // 使用 rexBuilder 创建 M2V 操作符调用
  }

  /**
   * v2m 方法创建 V2M（Vector to Multi-Valued）操作符调用。
   * 
   * V2M 是 Calcite 内部操作符，用于将向量转换为多值。
   * 这是 Calcite 内部使用的操作符，不是标准 SQL 操作符。
   * 
   * 参数：
   * @param n 要转换的表达式节点
   * 
   * 返回：
   * @return V2M 操作符调用节点
   */
  protected RexNode v2m(RexNode n) { // 创建 V2M 操作符调用
    return rexBuilder.makeCall(SqlInternalOperators.V2M, n); // 使用 rexBuilder 创建 V2M 操作符调用
  }

  /**
   * item 方法创建 ITEM 操作符调用。
   * 
   * ITEM 是 SQL 中的数组或映射元素访问操作符。
   * 例如：array[index] 或 map[key]
   * 
   * 参数：
   * @param inputRef 输入表达式节点（数组或映射）
   * @param literal 索引或键表达式节点
   * 
   * 返回：
   * @return ITEM 操作符调用节点
   */
  protected RexNode item(RexNode inputRef, RexNode literal) { // 创建 ITEM 操作符调用
    return rexBuilder.makeCall(SqlStdOperatorTable.ITEM, inputRef, literal); // 使用 rexBuilder 创建 ITEM 操作符调用
  }

  /**
   * in 方法创建 IN 操作符调用。
   * 
   * IN 是 SQL 中的成员关系操作符，用于判断一个值是否在给定的值列表中。
   * 当调用为 in(x, y, z) 时，生成 "x IN (y, z)" 表达式。
   * 
   * 参数：
   * @param node IN 表达式的左侧节点
   * @param nodes IN 表达式右侧的节点数组
   * 
   * 返回：
   * @return IN 操作符调用节点
   */
  protected RexNode in(RexNode node, RexNode... nodes) { // 创建 IN 操作符调用
    return rexBuilder.makeIn(node, ImmutableList.copyOf(nodes)); // 使用 rexBuilder 创建 IN 操作符调用
  }

  // ==================== 类型创建方法 ====================
  
  /**
   * nullable 方法将类型转换为可空类型。
   * 
   * 如果类型已经是可空的，则直接返回原类型；
   * 否则创建该类型的可空版本。
   * 
   * 参数：
   * @param type 要转换的类型
   * 
   * 返回：
   * @return 可空类型
   */
  protected RelDataType nullable(RelDataType type) { // 将类型转换为可空类型
    if (type.isNullable()) { // 检查类型是否已经是可空的
      return type; // 如果已经是可空的，直接返回原类型
    }
    return typeFactory.createTypeWithNullability(type, true); // 否则创建可空版本
  }

  /**
   * tVarchar 方法返回不可空的 VARCHAR 类型。
   * 
   * 这是一个便捷方法，用于快速获取不可空的 VARCHAR 类型。
   * 
   * 返回：
   * @return 不可空的 VARCHAR 类型
   */
  protected RelDataType tVarchar() { // 返回不可空的 VARCHAR 类型
    return nonNullableVarchar; // 返回预定义的不可空 VARCHAR 类型
  }

  /**
   * tVarchar 方法返回 VARCHAR 类型（可空或不可空）。
   * 
   * 这是一个便捷方法，用于快速获取指定可空性的 VARCHAR 类型。
   * 
   * 参数：
   * @param nullable 是否可空
   * 
   * 返回：
   * @return VARCHAR 类型
   */
  protected RelDataType tVarchar(boolean nullable) { // 返回 VARCHAR 类型（可空或不可空）
    return nullable ? nullableVarchar : nonNullableVarchar; // 根据 nullable 参数返回对应类型
  }

  /**
   * tVarchar 方法返回不可空的 VARCHAR 类型（指定精度）。
   * 
   * 这是一个便捷方法，用于快速获取指定精度的不可空 VARCHAR 类型。
   * 
   * 参数：
   * @param precision VARCHAR 的精度（最大长度）
   * 
   * 返回：
   * @return 不可空的 VARCHAR 类型
   */
  protected RelDataType tVarchar(int precision) { // 返回不可空的 VARCHAR 类型（指定精度）
    return tVarchar(false, precision); // 调用另一个 tVarchar 方法，指定不可空
  }

  /**
   * tVarchar 方法返回 VARCHAR 类型（指定精度和可空性）。
   * 
   * 这是一个便捷方法，用于快速获取指定精度和可空性的 VARCHAR 类型。
   * 
   * 参数：
   * @param nullable 是否可空
   * @param precision VARCHAR 的精度（最大长度）
   * 
   * 返回：
   * @return VARCHAR 类型
   */
  protected RelDataType tVarchar(boolean nullable, int precision) { // 返回 VARCHAR 类型（指定精度和可空性）
    RelDataType sqlType = typeFactory.createSqlType(SqlTypeName.VARCHAR, precision); // 创建指定精度的 VARCHAR 类型
    if (nullable) { // 检查是否需要可空
      sqlType = typeFactory.createTypeWithNullability(sqlType, true); // 创建可空版本
    }
    return sqlType; // 返回创建的类型
  }

  /**
   * tChar 方法返回不可空的 CHAR 类型（指定精度）。
   * 
   * 这是一个便捷方法，用于快速获取指定精度的不可空 CHAR 类型。
   * CHAR 是定长字符串类型。
   * 
   * 参数：
   * @param precision CHAR 的精度（固定长度）
   * 
   * 返回：
   * @return 不可空的 CHAR 类型
   */
  protected RelDataType tChar(int precision) { // 返回不可空的 CHAR 类型（指定精度）
    return tChar(false, precision); // 调用另一个 tChar 方法，指定不可空
  }

  /**
   * tChar 方法返回 CHAR 类型（指定精度和可空性）。
   * 
   * 这是一个便捷方法，用于快速获取指定精度和可空性的 CHAR 类型。
   * CHAR 是定长字符串类型。
   * 
   * 参数：
   * @param nullable 是否可空
   * @param precision CHAR 的精度（固定长度）
   * 
   * 返回：
   * @return CHAR 类型
   */
  protected RelDataType tChar(boolean nullable, int precision) { // 返回 CHAR 类型（指定精度和可空性）
    RelDataType sqlType = typeFactory.createSqlType(SqlTypeName.CHAR, precision); // 创建指定精度的 CHAR 类型
    if (nullable) { // 检查是否需要可空
      sqlType = typeFactory.createTypeWithNullability(sqlType, true); // 创建可空版本
    }
    return sqlType; // 返回创建的类型
  }

  /**
   * tBool 方法返回不可空的 BOOLEAN 类型。
   * 
   * 这是一个便捷方法，用于快速获取不可空的 BOOLEAN 类型。
   * 
   * 返回：
   * @return 不可空的 BOOLEAN 类型
   */
  protected RelDataType tBool() { // 返回不可空的 BOOLEAN 类型
    return nonNullableBool; // 返回预定义的不可空 BOOLEAN 类型
  }

  /**
   * tBool 方法返回 BOOLEAN 类型（可空或不可空）。
   * 
   * 这是一个便捷方法，用于快速获取指定可空性的 BOOLEAN 类型。
   * 
   * 参数：
   * @param nullable 是否可空
   * 
   * 返回：
   * @return BOOLEAN 类型
   */
  protected RelDataType tBool(boolean nullable) { // 返回 BOOLEAN 类型（可空或不可空）
    return nullable ? nullableBool : nonNullableBool; // 根据 nullable 参数返回对应类型
  }

  /**
   * tInt 方法返回不可空的 INTEGER 类型。
   * 
   * 这是一个便捷方法，用于快速获取不可空的 INTEGER 类型。
   * 
   * 返回：
   * @return 不可空的 INTEGER 类型
   */
  protected RelDataType tInt() { // 返回不可空的 INTEGER 类型
    return nonNullableInt; // 返回预定义的不可空 INTEGER 类型
  }

  /**
   * tInt 方法返回 INTEGER 类型（可空或不可空）。
   * 
   * 这是一个便捷方法，用于快速获取指定可空性的 INTEGER 类型。
   * 
   * 参数：
   * @param nullable 是否可空
   * 
   * 返回：
   * @return INTEGER 类型
   */
  protected RelDataType tInt(boolean nullable) { // 返回 INTEGER 类型（可空或不可空）
    return nullable ? nullableInt : nonNullableInt; // 根据 nullable 参数返回对应类型
  }

  /**
   * tSmallInt 方法返回不可空的 SMALLINT 类型。
   * 
   * 这是一个便捷方法，用于快速获取不可空的 SMALLINT 类型。
   * 
   * 返回：
   * @return 不可空的 SMALLINT 类型
   */
  protected RelDataType tSmallInt() { // 返回不可空的 SMALLINT 类型
    return nonNullableSmallInt; // 返回预定义的不可空 SMALLINT 类型
  }

  /**
   * tSmallInt 方法返回 SMALLINT 类型（可空或不可空）。
   * 
   * 这是一个便捷方法，用于快速获取指定可空性的 SMALLINT 类型。
   * 
   * 参数：
   * @param nullable 是否可空
   * 
   * 返回：
   * @return SMALLINT 类型
   */
  protected RelDataType tSmallInt(boolean nullable) { // 返回 SMALLINT 类型（可空或不可空）
    return nullable ? nullableSmallInt : nonNullableSmallInt; // 根据 nullable 参数返回对应类型
  }

  /**
   * tDecimal 方法返回不可空的 DECIMAL 类型。
   * 
   * 这是一个便捷方法，用于快速获取不可空的 DECIMAL 类型。
   * 
   * 返回：
   * @return 不可空的 DECIMAL 类型
   */
  protected RelDataType tDecimal() { // 返回不可空的 DECIMAL 类型
    return nonNullableDecimal; // 返回预定义的不可空 DECIMAL 类型
  }

  /**
   * tDecimal 方法返回 DECIMAL 类型（可空或不可空）。
   * 
   * 这是一个便捷方法，用于快速获取指定可空性的 DECIMAL 类型。
   * 
   * 参数：
   * @param nullable 是否可空
   * 
   * 返回：
   * @return DECIMAL 类型
   */
  protected RelDataType tDecimal(boolean nullable) { // 返回 DECIMAL 类型（可空或不可空）
    return nullable ? nullableDecimal : nonNullableDecimal; // 根据 nullable 参数返回对应类型
  }

  /**
   * tBigInt 方法返回不可空的 BIGINT 类型。
   * 
   * 这是一个便捷方法，用于快速获取不可空的 BIGINT 类型。
   * BIGINT 是大整数类型，通常占用 8 字节。
   * 
   * 返回：
   * @return 不可空的 BIGINT 类型
   */
  protected RelDataType tBigInt() { // 返回不可空的 BIGINT 类型
    return tBigInt(false); // 调用另一个 tBigInt 方法，指定不可空
  }

  /**
   * tBigInt 方法返回 BIGINT 类型（可空或不可空）。
   * 
   * 这是一个便捷方法，用于快速获取指定可空性的 BIGINT 类型。
   * BIGINT 是大整数类型，通常占用 8 字节。
   * 
   * 参数：
   * @param nullable 是否可空
   * 
   * 返回：
   * @return BIGINT 类型
   */
  protected RelDataType tBigInt(boolean nullable) { // 返回 BIGINT 类型（可空或不可空）
    RelDataType type = typeFactory.createSqlType(SqlTypeName.BIGINT); // 创建 BIGINT 类型
    if (nullable) { // 检查是否需要可空
      type = nullable(type); // 创建可空版本
    }
    return type; // 返回创建的类型
  }

  /**
   * tVarbinary 方法返回不可空的 VARBINARY 类型。
   * 
   * 这是一个便捷方法，用于快速获取不可空的 VARBINARY 类型。
   * VARBINARY 是变长二进制类型。
   * 
   * 返回：
   * @return 不可空的 VARBINARY 类型
   */
  protected RelDataType tVarbinary() { // 返回不可空的 VARBINARY 类型
    return nonNullableVarbinary; // 返回预定义的不可空 VARBINARY 类型
  }

  /**
   * tVarbinary 方法返回 VARBINARY 类型（可空或不可空）。
   * 
   * 这是一个便捷方法，用于快速获取指定可空性的 VARBINARY 类型。
   * VARBINARY 是变长二进制类型。
   * 
   * 参数：
   * @param nullable 是否可空
   * 
   * 返回：
   * @return VARBINARY 类型
   */
  protected RelDataType tVarbinary(boolean nullable) { // 返回 VARBINARY 类型（可空或不可空）
    return nullable ? nullableVarbinary : nonNullableVarbinary; // 根据 nullable 参数返回对应类型
  }


  /**
   * tArray 方法返回数组类型。
   * 
   * 这是一个便捷方法，用于快速创建指定元素类型的数组类型。
   * 数组长度为 -1，表示不定长数组。
   * 
   * 参数：
   * @param elemType 数组元素类型
   * 
   * 返回：
   * @return 数组类型
   */
  protected RelDataType tArray(RelDataType elemType) { // 返回数组类型
    return typeFactory.createArrayType(elemType, -1); // 创建不定长数组类型
  }

  // ==================== 字面量创建方法 ====================
  
  /**
   * null_ 方法创建指定类型的 NULL 字面量。
   * 
   * 例如：null_(tInt()) 创建 INTEGER 类型的 NULL 字面量。
   * 
   * 参数：
   * @param type 所需的 NULL 类型
   * 
   * 返回：
   * @return 指定类型的 NULL 字面量
   */
  protected RexLiteral null_(RelDataType type) { // 创建指定类型的 NULL 字面量
    return rexBuilder.makeNullLiteral(nullable(type)); // 使用 rexBuilder 创建可空类型的 NULL 字面量
  }

  /**
   * literal 方法创建布尔值字面量。
   * 
   * 创建一个不可空的 BOOLEAN 字面量。
   * 
   * 参数：
   * @param value 布尔值
   * 
   * 返回：
   * @return 布尔值字面量
   */
  protected RexLiteral literal(boolean value) { // 创建布尔值字面量
    return rexBuilder.makeLiteral(value, nonNullableBool); // 使用 rexBuilder 创建不可空的布尔值字面量
  }

  /**
   * literal 方法创建布尔值字面量（可空版本）。
   * 
   * 如果值为 null，则创建 NULL 字面量；否则创建布尔值字面量。
   * 
   * 参数：
   * @param value 布尔值（可能为 null）
   * 
   * 返回：
   * @return 布尔值字面量或 NULL 字面量
   */
  protected RexLiteral literal(Boolean value) { // 创建布尔值字面量（可空版本）
    if (value == null) { // 检查值是否为 null
      return rexBuilder.makeNullLiteral(nullableBool); // 如果为 null，创建可空 BOOLEAN 类型的 NULL 字面量
    }
    return literal(value.booleanValue()); // 否则创建布尔值字面量
  }

  /**
   * literal 方法创建整数字面量。
   * 
   * 创建一个不可空的 INTEGER 字面量。
   * 
   * 参数：
   * @param value 整数值
   * 
   * 返回：
   * @return 整数字面量
   */
  protected RexLiteral literal(int value) { // 创建整数字面量
    return rexBuilder.makeLiteral(value, nonNullableInt); // 使用 rexBuilder 创建不可空的整数字面量
  }

  /**
   * literal 方法创建精确的十进制数字面量。
   * 
   * 创建一个 DECIMAL 类型的字面量，使用 BigDecimal 保证精度。
   * 
   * 参数：
   * @param value 十进制数值
   * 
   * 返回：
   * @return 十进制数字面量
   */
  protected RexLiteral literal(BigDecimal value) { // 创建精确的十进制数字面量
    return rexBuilder.makeExactLiteral(value); // 使用 rexBuilder 创建精确的十进制数字面量
  }

  /**
   * literal 方法创建指定类型的精确十进制数字面量。
   * 
   * 创建一个指定 DECIMAL 类型的字面量，使用 BigDecimal 保证精度。
   * 
   * 参数：
   * @param value 十进制数值
   * @param type 目标类型
   * 
   * 返回：
   * @return 十进制数字面量
   */
  protected RexLiteral literal(BigDecimal value, RelDataType type) { // 创建指定类型的精确十进制数字面量
    return rexBuilder.makeExactLiteral(value, type); // 使用 rexBuilder 创建指定类型的精确十进制数字面量
  }

  /**
   * literal 方法创建整数字面量（可空版本）。
   * 
   * 如果值为 null，则创建 NULL 字面量；否则创建整数字面量。
   * 
   * 参数：
   * @param value 整数值（可能为 null）
   * 
   * 返回：
   * @return 整数字面量或 NULL 字面量
   */
  protected RexLiteral literal(Integer value) { // 创建整数字面量（可空版本）
    if (value == null) { // 检查值是否为 null
      return rexBuilder.makeNullLiteral(nullableInt); // 如果为 null，创建可空 INTEGER 类型的 NULL 字面量
    }
    return literal(value.intValue()); // 否则创建整数字面量
  }

  /**
   * literal 方法创建字符串字面量。
   * 
   * 如果值为 null，则创建 NULL 字面量；否则创建字符串字面量。
   * 
   * 参数：
   * @param value 字符串值（可能为 null）
   * 
   * 返回：
   * @return 字符串字面量或 NULL 字面量
   */
  protected RexLiteral literal(String value) { // 创建字符串字面量
    if (value == null) { // 检查值是否为 null
      return rexBuilder.makeNullLiteral(nullableVarchar); // 如果为 null，创建可空 VARCHAR 类型的 NULL 字面量
    }
    return rexBuilder.makeLiteral(value, nonNullableVarchar); // 否则创建字符串字面量
  }

  // ==================== 变量创建方法 ====================
  
  /**
   * input 方法创建指定类型和索引的输入引用。
   * 
   * 输入引用（RexInputRef）表示对输入行的第 arg 个字段的引用。
   * 
   * 注意：优先使用 vBool()、vInt() 等方法。
   * 
   * "输入引用"的问题在于 input(tInt(), 0).toString() 输出 "$0"，
   * 所以表达式的类型不会被打印出来，这使得分析表达式变得困难。
   * 
   * 参数：
   * @param type 节点的期望类型
   * @param arg 参数索引（从 0 开始）
   * 
   * 返回：
   * @return 指定类型和索引的输入引用
   */
  protected RexNode input(RelDataType type, int arg) { // 生成指定类型和索引的输入引用
    return rexBuilder.makeInputRef(type, arg); // 使用 rexBuilder 创建输入引用
  }

  /**
   * assertArgValue 方法断言参数值在有效范围内。
   * 
   * 参数值必须在 0 到 MAX_FIELDS-1 的范围内。
   * 
   * 参数：
   * @param arg 参数索引
   */
  private void assertArgValue(int arg) { // 断言参数值在有效范围内
    assert arg >= 0 && arg < MAX_FIELDS // 断言 arg 在 0 到 MAX_FIELDS-1 范围内
        : "arg should be in 0.." + (MAX_FIELDS - 1) + " range. Actual value was " + arg; // 如果断言失败，显示错误信息
  }

  /**
   * vBool 方法创建索引为 0 的可空布尔变量。
   * 
   * 如果需要多个不同的变量，使用 vBool(int)。
   * 
   * 返回：
   * @return 索引为 0 的可空布尔变量
   */
  protected RexNode vBool() { // 创建索引为 0 的可空布尔变量
    return vBool(0); // 调用另一个 vBool 方法，指定索引为 0
  }

  /**
   * vBool 方法创建索引为 arg 的可空布尔变量。
   * 
   * 创建的结果节点看起来像 ?0.bool3（如果 arg 是 3）。
   * 
   * 参数：
   * @param arg 参数索引（从 0 开始）
   * 
   * 返回：
   * @return 指定索引的可空布尔变量
   */
  protected RexNode vBool(int arg) { // 创建索引为 arg 的可空布尔变量
    return vParam("bool", arg, nullableBool); // 调用 vParam 方法，指定类型为可空 BOOLEAN
  }

  /**
   * vBoolNotNull 方法创建索引为 0 的不可空布尔变量。
   * 
   * 如果需要多个不同的变量，使用 vBoolNotNull(int)。
   * 创建的结果节点看起来像 ?0.notNullBool0
   * 
   * 返回：
   * @return 索引为 0 的不可空布尔变量
   */
  protected RexNode vBoolNotNull() { // 创建索引为 0 的不可空布尔变量
    return vBoolNotNull(0); // 调用另一个 vBoolNotNull 方法，指定索引为 0
  }

  /**
   * vBoolNotNull 方法创建索引为 arg 的不可空布尔变量。
   * 
   * 创建的结果节点看起来像 ?0.notNullBool3（如果 arg 是 3）。
   * 
   * 参数：
   * @param arg 参数索引（从 0 开始）
   * 
   * 返回：
   * @return 指定索引的不可空布尔变量
   */
  protected RexNode vBoolNotNull(int arg) { // 创建索引为 arg 的不可空布尔变量
    return vParamNotNull("bool", arg, nonNullableBool); // 调用 vParamNotNull 方法，指定类型为不可空 BOOLEAN
  }

  /**
   * vInt 方法创建索引为 0 的可空整数变量。
   * 
   * 如果需要多个不同的变量，使用 vInt(int)。
   * 创建的结果节点看起来像 ?0.notNullInt0
   * 
   * 返回：
   * @return 索引为 0 的可空整数变量
   */
  protected RexNode vInt() { // 创建索引为 0 的可空整数变量
    return vInt(0); // 调用另一个 vInt 方法，指定索引为 0
  }

  /**
   * vInt 方法创建索引为 arg 的可空整数变量。
   * 
   * 创建的结果节点看起来像 ?0.int3（如果 arg 是 3）。
   * 
   * 参数：
   * @param arg 参数索引（从 0 开始）
   * 
   * 返回：
   * @return 指定索引的可空整数变量
   */
  protected RexNode vInt(int arg) { // 创建索引为 arg 的可空整数变量
    return vParam("int", arg, nullableInt); // 调用 vParam 方法，指定类型为可空 INTEGER
  }

  /**
   * vIntNotNull 方法创建索引为 0 的不可空整数变量。
   * 
   * 如果需要多个不同的变量，使用 vIntNotNull(int)。
   * 创建的结果节点看起来像 ?0.notNullInt0
   * 
   * 返回：
   * @return 索引为 0 的不可空整数变量
   */
  protected RexNode vIntNotNull() { // 创建索引为 0 的不可空整数变量
    return vIntNotNull(0); // 调用另一个 vIntNotNull 方法，指定索引为 0
  }

  /**
   * vIntNotNull 方法创建索引为 arg 的不可空整数变量。
   * 
   * 创建的结果节点看起来像 ?0.notNullInt3（如果 arg 是 3）。
   * 
   * 参数：
   * @param arg 参数索引（从 0 开始）
   * 
   * 返回：
   * @return 指定索引的不可空整数变量
   */
  protected RexNode vIntNotNull(int arg) { // 创建索引为 arg 的不可空整数变量
    return vParamNotNull("int", arg, nonNullableInt); // 调用 vParamNotNull 方法，指定类型为不可空 INTEGER
  }

  /**
   * vSmallInt 方法创建索引为 0 的可空短整数变量。
   * 
   * 如果需要多个不同的变量，使用 vSmallInt(int)。
   * 创建的结果节点看起来像 ?0.notNullSmallInt0
   * 
   * 返回：
   * @return 索引为 0 的可空短整数变量
   */
  protected RexNode vSmallInt() { // 创建索引为 0 的可空短整数变量
    return vSmallInt(0); // 调用另一个 vSmallInt 方法，指定索引为 0
  }

  /**
   * vSmallInt 方法创建索引为 arg 的可空短整数变量。
   * 
   * 创建的结果节点看起来像 ?0.int3（如果 arg 是 3）。
   * 
   * 参数：
   * @param arg 参数索引（从 0 开始）
   * 
   * 返回：
   * @return 指定索引的可空短整数变量
   */
  protected RexNode vSmallInt(int arg) { // 创建索引为 arg 的可空短整数变量
    return vParam("smallint", arg, nullableSmallInt); // 调用 vParam 方法，指定类型为可空 SMALLINT
  }

  /**
   * vSmallIntNotNull 方法创建索引为 0 的不可空短整数变量。
   * 
   * 如果需要多个不同的变量，使用 vSmallIntNotNull(int)。
   * 创建的结果节点看起来像 ?0.notNullSmallInt0
   * 
   * 返回：
   * @return 索引为 0 的不可空短整数变量
   */
  protected RexNode vSmallIntNotNull() { // 创建索引为 0 的不可空短整数变量
    return vSmallIntNotNull(0); // 调用另一个 vSmallIntNotNull 方法，指定索引为 0
  }

  /**
   * vSmallIntNotNull 方法创建索引为 arg 的不可空短整数变量。
   * 
   * 创建的结果节点看起来像 ?0.notNullSmallInt3（如果 arg 是 3）。
   * 
   * 参数：
   * @param arg 参数索引（从 0 开始）
   * 
   * 返回：
   * @return 指定索引的不可空短整数变量
   */
  protected RexNode vSmallIntNotNull(int arg) { // 创建索引为 arg 的不可空短整数变量
    return vParamNotNull("smallint", arg, nonNullableSmallInt); // 调用 vParamNotNull 方法，指定类型为不可空 SMALLINT
  }

  /**
   * vVarchar 方法创建索引为 0 的可空字符串变量。
   * 
   * 如果需要多个不同的变量，使用 vVarchar(int)。
   * 创建的结果节点看起来像 ?0.notNullVarchar0
   * 
   * 返回：
   * @return 索引为 0 的可空字符串变量
   */
  protected RexNode vVarchar() { // 创建索引为 0 的可空字符串变量
    return vVarchar(0); // 调用另一个 vVarchar 方法，指定索引为 0
  }

  /**
   * vVarchar 方法创建索引为 arg 的可空字符串变量。
   * 
   * 创建的结果节点看起来像 ?0.varchar3（如果 arg 是 3）。
   * 
   * 参数：
   * @param arg 参数索引（从 0 开始）
   * 
   * 返回：
   * @return 指定索引的可空字符串变量
   */
  protected RexNode vVarchar(int arg) { // 创建索引为 arg 的可空字符串变量
    return vParam("varchar", arg, nullableVarchar); // 调用 vParam 方法，指定类型为可空 VARCHAR
  }

  /**
   * vVarcharNotNull 方法创建索引为 0 的不可空字符串变量。
   * 
   * 如果需要多个不同的变量，使用 vVarcharNotNull(int)。
   * 创建的结果节点看起来像 ?0.notNullVarchar0
   * 
   * 返回：
   * @return 索引为 0 的不可空字符串变量
   */
  protected RexNode vVarcharNotNull() { // 创建索引为 0 的不可空字符串变量
    return vVarcharNotNull(0); // 调用另一个 vVarcharNotNull 方法，指定索引为 0
  }

  /**
   * vVarcharNotNull 方法创建索引为 arg 的不可空字符串变量。
   * 
   * 创建的结果节点看起来像 ?0.notNullVarchar3（如果 arg 是 3）。
   * 
   * 参数：
   * @param arg 参数索引（从 0 开始）
   * 
   * 返回：
   * @return 指定索引的不可空字符串变量
   */
  protected RexNode vVarcharNotNull(int arg) { // 创建索引为 arg 的不可空字符串变量
    return vParamNotNull("varchar", arg, nonNullableVarchar); // 调用 vParamNotNull 方法，指定类型为不可空 VARCHAR
  }

  /**
   * vDecimal 方法创建索引为 0 的可空十进制变量。
   * 
   * 如果需要多个不同的变量，使用 vDecimal(int)。
   * 创建的结果节点看起来像 ?0.notNullDecimal0
   * 
   * 返回：
   * @return 索引为 0 的可空十进制变量
   */
  protected RexNode vDecimal() { // 创建索引为 0 的可空十进制变量
    return vDecimal(0); // 调用另一个 vDecimal 方法，指定索引为 0
  }

  /**
   * vDecimal 方法创建索引为 arg 的可空十进制变量。
   * 
   * 创建的结果节点看起来像 ?0.decimal3（如果 arg 是 3）。
   * 
   * 参数：
   * @param arg 参数索引（从 0 开始）
   * 
   * 返回：
   * @return 指定索引的可空十进制变量
   */
  protected RexNode vDecimal(int arg) { // 创建索引为 arg 的可空十进制变量
    return vParam("decimal", arg, nullableDecimal); // 调用 vParam 方法，指定类型为可空 DECIMAL
  }

  /**
   * vDecimalNotNull 方法创建索引为 0 的不可空十进制变量。
   * 
   * 如果需要多个不同的变量，使用 vDecimalNotNull(int)。
   * 创建的结果节点看起来像 ?0.notNullDecimal0
   * 
   * 返回：
   * @return 索引为 0 的不可空十进制变量
   */
  protected RexNode vDecimalNotNull() { // 创建索引为 0 的不可空十进制变量
    return vDecimalNotNull(0); // 调用另一个 vDecimalNotNull 方法，指定索引为 0
  }

  /**
   * vDecimalNotNull 方法创建索引为 arg 的不可空十进制变量。
   * 
   * 创建的结果节点看起来像 ?0.notNullDecimal3（如果 arg 是 3）。
   * 
   * 参数：
   * @param arg 参数索引（从 0 开始）
   * 
   * 返回：
   * @return 指定索引的不可空十进制变量
   */
  protected RexNode vDecimalNotNull(int arg) { // 创建索引为 arg 的不可空十进制变量
    return vParamNotNull("decimal", arg, nonNullableDecimal); // 调用 vParamNotNull 方法，指定类型为不可空 DECIMAL
  }

  /**
   * vParam 方法创建指定类型和名称的可空变量（索引为 0）。
   * 
   * 这使得类型可以动态构建时创建可空变量。
   * 例如：vParam("char(2)_", tChar(2)) 会生成一个可空的 char(2) 变量，
   * 看起来像 ?0.char(2)_0。
   * 如果需要多个这样的变量，使用 vParam(String, int, RelDataType)。
   * 
   * 参数：
   * @param name 变量名前缀
   * @param type 变量类型
   * 
   * 返回：
   * @return 指定类型的可空变量
   */
  protected RexNode vParam(String name, RelDataType type) { // 创建指定类型和名称的可空变量
    return vParam(name, 0, type); // 调用另一个 vParam 方法，指定索引为 0
  }

  /**
   * vParam 方法创建指定类型和名称的可空变量（索引为 arg）。
   * 
   * 这使得类型可以动态构建时创建可空变量。
   * 例如：vParam("char(2)_", 3, tChar(2)) 会生成一个可空的 char(2) 变量，
   * 看起来像 ?0.char(2)_3。
   * 
   * 参数：
   * @param name 变量名前缀
   * @param arg 参数索引（从 0 开始）
   * @param type 变量类型
   * 
   * 返回：
   * @return 指定索引的可空变量
   */
  protected RexNode vParam(String name, int arg, RelDataType type) { // 创建指定类型和名称的可空变量
    assertArgValue(arg); // 断言参数值在有效范围内
    RelDataType nonNullableType = typeFactory.createTypeWithNullability(type, false); // 创建不可空类型
    return rexBuilder.makeFieldAccess(getDynamicParam(nonNullableType, name), arg); // 创建字段访问节点，访问动态参数的第 arg 个字段
  }

  /**
   * vParamNotNull 方法创建指定类型和名称的不可空变量。
   * 
   * 这使得类型可以动态构建时创建不可空变量。
   * 例如：vParam("char(2)_", tChar(2)) 会生成一个不可空的 char(2) 变量，
   * 看起来像 ?0.char(2)_0。
   * 如果需要多个这样的变量，使用 vParamNotNull(String, int, RelDataType)。
   * 
   * 参数：
   * @param name 变量名前缀
   * @param type 变量类型
   * 
   * 返回：
   * @return 指定类型的不可空变量
   */
  protected RexNode vParamNotNull(String name, RelDataType type) { // 创建指定类型和名称的不可空变量
    return vParamNotNull(name, 0, type); // 调用另一个 vParamNotNull 方法，指定索引为 0
  }

  /**
   * vParamNotNull 方法创建指定类型和名称的不可空变量（索引为 arg）。
   * 
   * 这使得类型可以动态构建时创建不可空变量。
   * 例如：vParam("char(2)_", 3, tChar(2)) 会生成一个不可空的 char(2) 变量，
   * 看起来像 ?0.char(2)_3。
   * 
   * 参数：
   * @param name 变量名前缀
   * @param arg 参数索引（从 0 开始）
   * @param type 变量类型
   * 
   * 返回：
   * @return 指定索引的不可空变量
   */
  protected RexNode vParamNotNull(String name, int arg, RelDataType type) { // 创建指定类型和名称的不可空变量
    assertArgValue(arg); // 断言参数值在有效范围内
    RelDataType nonNullableType = typeFactory.createTypeWithNullability(type, false); // 创建不可空类型
    return rexBuilder.makeFieldAccess(getDynamicParam(nonNullableType, name), arg + MAX_FIELDS); // 创建字段访问节点，访问动态参数的第 arg + MAX_FIELDS 个字段（不可空字段）
  }
}