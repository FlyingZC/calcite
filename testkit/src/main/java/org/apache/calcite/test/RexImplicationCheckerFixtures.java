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
package org.apache.calcite.test; // 定义包名，表示这个类属于org.apache.calcite.test包

import org.apache.calcite.DataContexts; // 导入DataContexts类，用于提供数据上下文
import org.apache.calcite.jdbc.JavaTypeFactoryImpl; // 导入Java类型工厂实现类
import org.apache.calcite.plan.RelOptPredicateList; // 导入关系表达式谓词列表类
import org.apache.calcite.plan.RexImplicationChecker; // 导入Rex表达式蕴含检查器类
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入关系数据类型系统接口
import org.apache.calcite.rex.RexBuilder; // 导入Rex表达式构建器类
import org.apache.calcite.rex.RexExecutorImpl; // 导入Rex表达式执行器实现类
import org.apache.calcite.rex.RexInputRef; // 导入Rex输入引用类
import org.apache.calcite.rex.RexLiteral; // 导入Rex字面量类
import org.apache.calcite.rex.RexNode; // 导入Rex表达式节点基类
import org.apache.calcite.rex.RexSimplify; // 导入Rex表达式简化器类
import org.apache.calcite.sql.SqlCollation; // 导入SQL排序规则类
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SQL标准操作符表
import org.apache.calcite.tools.Frameworks; // 导入框架工具类
import org.apache.calcite.util.DateString; // 导入日期字符串工具类
import org.apache.calcite.util.NlsString; // 导入国际化字符串类
import org.apache.calcite.util.TimeString; // 导入时间字符串工具类
import org.apache.calcite.util.TimestampString; // 导入时间戳字符串工具类

import java.math.BigDecimal; // 导入BigDecimal类，用于精确数值计算
import java.sql.Date; // 导入SQL日期类
import java.sql.Time; // 导入SQL时间类
import java.sql.Timestamp; // 导入SQL时间戳类

import static org.junit.jupiter.api.Assertions.assertFalse; // 导入JUnit5的assertFalse断言方法
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入JUnit5的assertTrue断言方法

/**
 * Fixtures for verifying {@link RexImplicationChecker}.
 * // 用于验证RexImplicationChecker（Rex表达式蕴含检查器）的测试固定装置接口
 * // 这个接口提供了测试RexImplicationChecker所需的工具方法和数据结构
 * // RexImplicationChecker用于检查一个Rex表达式是否蕴含另一个Rex表达式
 * // 例如：检查表达式"x > 5"是否蕴含表达式"x > 3"
 */
public interface RexImplicationCheckerFixtures { // 定义一个接口，包含测试固定装置
  /** Contains all the nourishment a test case could possibly need.
   * // 包含测试用例可能需要的所有资源
   *
   * <p>We put the data in here, rather than as fields in the test case, so that
   * the data can be garbage-collected as soon as the test has executed.
   * // 我们将数据放在这里，而不是作为测试用例的字段，这样数据可以在测试执行后立即被垃圾回收
   */
  @SuppressWarnings("WeakerAccess") // 抑制"访问权限可以更弱"的警告
  class Fixture { // 定义Fixture内部类，提供测试所需的全部资源
    public final RelDataTypeFactory typeFactory; // 关系数据类型工厂，用于创建各种数据类型
    public final RexBuilder rexBuilder; // Rex表达式构建器，用于构建各种Rex表达式
    public final RelDataType boolRelDataType; // Boolean类型的关系数据类型
    public final RelDataType intRelDataType; // Integer类型的关系数据类型
    public final RelDataType decRelDataType; // Double类型的关系数据类型
    public final RelDataType longRelDataType; // Long类型的关系数据类型
    public final RelDataType shortDataType; // Short类型的关系数据类型
    public final RelDataType byteDataType; // Byte类型的关系数据类型
    public final RelDataType floatDataType; // Float类型的关系数据类型
    public final RelDataType charDataType; // Character类型的关系数据类型
    public final RelDataType dateDataType; // Date类型的关系数据类型
    public final RelDataType timestampDataType; // Timestamp类型的关系数据类型
    public final RelDataType timeDataType; // Time类型的关系数据类型
    public final RelDataType stringDataType; // String类型的关系数据类型

    public final RexNode bl; // a field of Java type "Boolean" // Boolean类型的字段引用
    public final RexNode i; // a field of Java type "Integer" // Integer类型的字段引用
    public final RexNode dec; // a field of Java type "Double" // Double类型的字段引用
    public final RexNode lg; // a field of Java type "Long" // Long类型的字段引用
    public final RexNode sh; // a  field of Java type "Short" // Short类型的字段引用
    public final RexNode by; // a field of Java type "Byte" // Byte类型的字段引用
    public final RexNode fl; // a field of Java type "Float" (not a SQL FLOAT) // Float类型的字段引用（不是SQL FLOAT）
    public final RexNode d; // a field of Java type "Date" // Date类型的字段引用
    public final RexNode ch; // a field of Java type "Character" // Character类型的字段引用
    public final RexNode ts; // a field of Java type "Timestamp" // Timestamp类型的字段引用
    public final RexNode t; // a field of Java type "Time" // Time类型的字段引用
    public final RexNode str; // a field of Java type "String" // String类型的字段引用

    public final RexImplicationChecker checker; // Rex表达式蕴含检查器，用于检查表达式之间的蕴含关系
    public final RelDataType rowType; // 行类型，描述测试数据行的结构
    public final RexExecutorImpl executor; // Rex表达式执行器，用于执行和简化Rex表达式
    public final RexSimplify simplify; // Rex表达式简化器，用于简化复杂的Rex表达式

    public Fixture() { // Fixture构造方法，初始化所有测试所需的资源
      typeFactory = new JavaTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建Java类型工厂实例，使用默认的RelDataTypeSystem
      rexBuilder = new RexBuilder(typeFactory); // 使用类型工厂创建Rex表达式构建器
      boolRelDataType = typeFactory.createJavaType(Boolean.class); // 创建Boolean类型的关系数据类型
      intRelDataType = typeFactory.createJavaType(Integer.class); // 创建Integer类型的关系数据类型
      decRelDataType = typeFactory.createJavaType(Double.class); // 创建Double类型的关系数据类型
      longRelDataType = typeFactory.createJavaType(Long.class); // 创建Long类型的关系数据类型
      shortDataType = typeFactory.createJavaType(Short.class); // 创建Short类型的关系数据类型
      byteDataType = typeFactory.createJavaType(Byte.class); // 创建Byte类型的关系数据类型
      floatDataType = typeFactory.createJavaType(Float.class); // 创建Float类型的关系数据类型
      charDataType = typeFactory.createJavaType(Character.class); // 创建Character类型的关系数据类型
      dateDataType = typeFactory.createJavaType(Date.class); // 创建Date类型的关系数据类型
      timestampDataType = typeFactory.createJavaType(Timestamp.class); // 创建Timestamp类型的关系数据类型
      timeDataType = typeFactory.createJavaType(Time.class); // 创建Time类型的关系数据类型
      stringDataType = typeFactory.createJavaType(String.class); // 创建String类型的关系数据类型

      bl = ref(0, this.boolRelDataType); // 创建索引0的Boolean类型字段引用
      i = ref(1, intRelDataType); // 创建索引1的Integer类型字段引用
      dec = ref(2, decRelDataType); // 创建索引2的Double类型字段引用
      lg = ref(3, longRelDataType); // 创建索引3的Long类型字段引用
      sh = ref(4, shortDataType); // 创建索引4的Short类型字段引用
      by = ref(5, byteDataType); // 创建索引5的Byte类型字段引用
      fl = ref(6, floatDataType); // 创建索引6的Float类型字段引用
      ch = ref(7, charDataType); // 创建索引7的Character类型字段引用
      d = ref(8, dateDataType); // 创建索引8的Date类型字段引用
      ts = ref(9, timestampDataType); // 创建索引9的Timestamp类型字段引用
      t = ref(10, timeDataType); // 创建索引10的Time类型字段引用
      str = ref(11, stringDataType); // 创建索引11的String类型字段引用

      rowType = typeFactory.builder() // 开始构建行类型
          .add("bool", this.boolRelDataType) // 添加名为"bool"的Boolean类型字段
          .add("int", intRelDataType) // 添加名为"int"的Integer类型字段
          .add("dec", decRelDataType) // 添加名为"dec"的Double类型字段
          .add("long", longRelDataType) // 添加名为"long"的Long类型字段
          .add("short", shortDataType) // 添加名为"short"的Short类型字段
          .add("byte", byteDataType) // 添加名为"byte"的Byte类型字段
          .add("float", floatDataType) // 添加名为"float"的Float类型字段
          .add("char", charDataType) // 添加名为"char"的Character类型字段
          .add("date", dateDataType) // 添加名为"date"的Date类型字段
          .add("timestamp", timestampDataType) // 添加名为"timestamp"的Timestamp类型字段
          .add("time", timeDataType) // 添加名为"time"的Time类型字段
          .add("string", stringDataType) // 添加名为"string"的String类型字段
          .build(); // 完成行类型的构建

      executor = // 创建Rex表达式执行器
          Frameworks.withPrepare((cluster, relOptSchema, rootSchema, statement) -> // 使用框架准备上下文
              new RexExecutorImpl( // 创建Rex执行器实例
                  DataContexts.of(statement.getConnection(), rootSchema))); // 基于连接和根Schema创建数据上下文
      simplify = // 创建Rex表达式简化器
          new RexSimplify(rexBuilder, RelOptPredicateList.EMPTY, executor) // 使用构建器、空谓词列表和执行器创建简化器
              .withParanoid(true); // 启用偏执模式，进行更严格的简化
      checker = new RexImplicationChecker(rexBuilder, executor, rowType); // 创建Rex蕴含检查器，用于检查表达式间的蕴含关系
    }

    public RexInputRef ref(int i, RelDataType type) { // 创建Rex输入引用的方法，用于引用输入行的字段
      return new RexInputRef(i, // 创建RexInputRef实例，i是字段索引
          typeFactory.createTypeWithNullability(type, true)); // 创建带可空性的类型，允许为NULL
    }

    public RexLiteral literal(int i) { // 创建整数字面量的方法
      return rexBuilder.makeExactLiteral(new BigDecimal(i)); // 使用BigDecimal创建精确的整数字面量
    }

    public RexNode gt(RexNode node1, RexNode node2) { // 创建大于表达式的方法
      return rexBuilder.makeCall(SqlStdOperatorTable.GREATER_THAN, node1, node2); // 使用GREATER_THAN操作符创建node1 > node2表达式
    }

    public RexNode ge(RexNode node1, RexNode node2) { // 创建大于等于表达式的方法
      return rexBuilder.makeCall( // 使用操作符创建表达式
          SqlStdOperatorTable.GREATER_THAN_OR_EQUAL, node1, node2); // 创建node1 >= node2表达式
    }

    public RexNode eq(RexNode node1, RexNode node2) { // 创建等于表达式的方法
      return rexBuilder.makeCall(SqlStdOperatorTable.EQUALS, node1, node2); // 使用EQUALS操作符创建node1 = node2表达式
    }

    public RexNode ne(RexNode node1, RexNode node2) { // 创建不等于表达式的方法
      return rexBuilder.makeCall(SqlStdOperatorTable.NOT_EQUALS, node1, node2); // 使用NOT_EQUALS操作符创建node1 != node2表达式
    }

    public RexNode lt(RexNode node1, RexNode node2) { // 创建小于表达式的方法
      return rexBuilder.makeCall(SqlStdOperatorTable.LESS_THAN, node1, node2); // 使用LESS_THAN操作符创建node1 < node2表达式
    }

    public RexNode le(RexNode node1, RexNode node2) { // 创建小于等于表达式的方法
      return rexBuilder.makeCall(SqlStdOperatorTable.LESS_THAN_OR_EQUAL, node1, // 使用LESS_THAN_OR_EQUAL操作符
          node2); // 创建node1 <= node2表达式
    }

    public RexNode notNull(RexNode node1) { // 创建非空判断表达式的方法
      return rexBuilder.makeCall(SqlStdOperatorTable.IS_NOT_NULL, node1); // 创建node1 IS NOT NULL表达式
    }

    public RexNode isNull(RexNode node2) { // 创建空值判断表达式的方法
      return rexBuilder.makeCall(SqlStdOperatorTable.IS_NULL, node2); // 创建node2 IS NULL表达式
    }

    public RexNode and(RexNode... nodes) { // 创建逻辑与表达式的方法
      return rexBuilder.makeCall(SqlStdOperatorTable.AND, nodes); // 使用AND操作符连接多个节点，创建逻辑与表达式
    }

    public RexNode or(RexNode... nodes) { // 创建逻辑或表达式的方法
      return rexBuilder.makeCall(SqlStdOperatorTable.OR, nodes); // 使用OR操作符连接多个节点，创建逻辑或表达式
    }

    public RexNode longLiteral(long value) { // 创建Long类型字面量的方法
      return rexBuilder.makeLiteral(value, longRelDataType, true); // 创建Long类型的字面量，第三个参数表示字面量是否精确
    }

    public RexNode shortLiteral(short value) { // 创建Short类型字面量的方法
      return rexBuilder.makeLiteral(value, shortDataType, true); // 创建Short类型的字面量
    }

    public RexLiteral floatLiteral(double value) { // 创建浮点数字面量的方法
      return rexBuilder.makeApproxLiteral(new BigDecimal(value)); // 使用BigDecimal创建近似的浮点数字面量
    }

    public RexLiteral charLiteral(String z) { // 创建字符字面量的方法
      return rexBuilder.makeCharLiteral( // 创建字符类型的字面量
          new NlsString(z, null, SqlCollation.COERCIBLE)); // 使用NlsString包装字符值，null表示无特定字符集，COERCIBLE表示可强制排序
    }

    public RexNode dateLiteral(DateString d) { // 创建日期字面量的方法
      return rexBuilder.makeDateLiteral(d); // 使用DateString创建日期字面量
    }

    public RexNode timestampLiteral(TimestampString ts) { // 创建时间戳字面量的方法
      return rexBuilder.makeTimestampLiteral(ts, // 使用TimestampString创建时间戳字面量
          timestampDataType.getPrecision()); // 使用时间戳数据类型的精度
    }

    public RexNode timestampLocalTzLiteral(TimestampString ts) { // 创建本地时区时间戳字面量的方法
      return rexBuilder.makeTimestampWithLocalTimeZoneLiteral(ts, // 创建带本地时区的时间戳字面量
          timestampDataType.getPrecision()); // 使用时间戳数据类型的精度
    }

    public RexNode timeLiteral(TimeString t) { // 创建时间字面量的方法
      return rexBuilder.makeTimeLiteral(t, timeDataType.getPrecision()); // 使用TimeString创建时间字面量，并指定精度
    }

    public RexNode cast(RelDataType type, RexNode exp) { // 创建类型转换表达式的方法
      return rexBuilder.makeCast(type, exp, true, false); // 将表达式exp转换为指定类型type，true表示允许类型强制转换，false表示不进行类型匹配检查
    }

    void checkImplies(RexNode node1, RexNode node2) { // 检查node1是否蕴含node2的断言方法
      assertTrue(checker.implies(node1, node2), // 断言node1蕴含node2为真
          () -> node1 + " does not imply " + node2 + " when it should"); // 如果断言失败，输出错误信息
    }

    void checkNotImplies(RexNode node1, RexNode node2) { // 检查node1是否不蕴含node2的断言方法
      assertFalse(checker.implies(node1, node2), // 断言node1蕴含node2为假
          () -> node1 + " does implies " + node2 + " when it should not"); // 如果断言失败，输出错误信息
    }
  } // Fixture类的结束大括号
} // RexImplicationCheckerFixtures接口的结束大括号
