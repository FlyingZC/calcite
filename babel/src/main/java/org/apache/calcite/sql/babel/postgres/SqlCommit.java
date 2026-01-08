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
// 定义包名：org.apache.calcite.sql.babel.postgres，表示这是Calcite SQL框架中用于PostgreSQL方言的babel扩展包
package org.apache.calcite.sql.babel.postgres;

// 导入SqlCall类：这是Calcite中所有SQL调用节点的抽象基类，表示对SQL操作符的调用
import org.apache.calcite.sql.SqlCall;
// 导入SqlLiteral类：表示SQL中的字面值，如关键字、常量等
import org.apache.calcite.sql.SqlLiteral;
// 导入SqlNode类：这是Calcite中所有SQL语法树节点的基类
import org.apache.calcite.sql.SqlNode;
// 导入SqlOperator类：表示SQL操作符，如COMMIT、ROLLBACK等
import org.apache.calcite.sql.SqlOperator;
// 导入SqlWriter类：用于将SQL语法树节点序列化为SQL字符串
import org.apache.calcite.sql.SqlWriter;
// 导入SqlBasicOperator类：表示基本的SQL操作符，用于创建COMMIT操作符
import org.apache.calcite.sql.fun.SqlBasicOperator;
// 导入SqlParserPos类：表示SQL解析器中元素的位置信息，用于错误报告和调试
import org.apache.calcite.sql.parser.SqlParserPos;

// 导入ImmutableList类：Google Guava库提供的不可变列表，用于安全地存储操作数列表
import com.google.common.collect.ImmutableList;

// 导入List接口：Java标准库的列表接口
import java.util.List;

/**
 * Parse tree node representing a {@code COMMIT} clause.
 * 表示PostgreSQL的COMMIT语句的语法树节点
 * 
 * 这个类的作用：
 * 1. 在Calcite的SQL解析树中表示PostgreSQL的COMMIT语句
 * 2. COMMIT语句用于提交当前事务，使所有在该事务中做的修改永久生效
 * 3. 支持PostgreSQL特有的AND CHAIN语法，用于在提交后立即启动一个新事务
 * 4. 继承自SqlCall，因为COMMIT是SQL操作符的一种调用形式
 * 
 * PostgreSQL的COMMIT语法：
 * COMMIT [ AND [ NO ] CHAIN ]
 * - COMMIT：提交当前事务
 * - AND CHAIN：提交后立即启动一个新事务，新事务继承当前事务的事务特性
 * - AND NO CHAIN：提交后不启动新事务（默认行为）
 *
 * @see <a href="https://www.postgresql.org/docs/current/sql-commit.html">COMMIT specification</a>
 */
// 定义SqlCommit类，继承自SqlCall，表示PostgreSQL的COMMIT语句节点
public class SqlCommit extends SqlCall {

  // 定义一个静态的COMMIT操作符，使用SqlBasicOperator创建，名称为"COMMIT"
  // 这个操作符是单例的，在整个应用中共享
  public static final SqlBasicOperator OPERATOR =
      // 使用SqlBasicOperator的静态工厂方法创建一个名为"COMMIT"的操作符
      SqlBasicOperator.create("COMMIT").withCallFactory(
          // 设置调用工厂（CallFactory），用于在解析COMMIT语句时创建SqlCommit实例
          // 这是一个lambda表达式，定义了如何从解析的操作数创建SqlCommit对象
          // 参数说明：
          // - operator：操作符本身（即OPERATOR）
          // - functionQualifier：函数限定符（对于COMMIT不使用）
          // - pos：解析位置信息，记录COMMIT语句在SQL文本中的位置
          // - operands：操作数数组，对于COMMIT语句，只有一个操作数，表示是否使用AND CHAIN
          (operator, functionQualifier, pos, operands) ->
              // 创建并返回一个新的SqlCommit实例
              // pos：解析位置，传递给构造函数
              // (SqlLiteral) operands[0]：将第一个操作数转换为SqlLiteral类型，表示事务链模式
              new SqlCommit(pos, (SqlLiteral) operands[0]));
  // 成员变量：chain，表示事务链模式
  // 类型：SqlLiteral，表示一个字面值，存储TransactionChainingMode枚举值
  // 作用：存储COMMIT语句中的AND CHAIN或AND NO CHAIN选项
  // 可能的值：
  // - TransactionChainingMode.AND_CHAIN：表示使用AND CHAIN，提交后立即启动新事务
  // - TransactionChainingMode.AND_NO_CHAIN：表示使用AND NO CHAIN或省略，提交后不启动新事务
  // final修饰符：表示该字段在构造后不可变，保证线程安全和语义一致性
  private final SqlLiteral chain;

  // 构造方法：SqlCommit
  // 作用：创建一个新的SqlCommit实例，初始化COMMIT语句节点
  // 参数说明：
  // - pos：SqlParserPos类型，表示COMMIT语句在SQL文本中的位置信息
  //   用于错误报告和调试，记录语句的起始和结束位置
  // - chain：SqlLiteral类型，表示事务链模式
  //   存储是使用AND CHAIN还是AND NO CHAIN
  // protected修饰符：表示该构造方法受保护，只能通过OPERATOR的调用工厂创建实例
  protected SqlCommit(final SqlParserPos pos, final SqlLiteral chain) {
    // 调用父类SqlCall的构造方法，传递位置信息
    // 这将初始化SqlNode基类的位置字段，用于错误报告和调试
    super(pos);
    // 将传入的chain参数赋值给成员变量this.chain
    // 存储事务链模式，后续在unparse方法中使用该信息生成SQL字符串
    this.chain = chain;
  }

  // 重写getOperator方法：获取该节点对应的SQL操作符
  // 作用：返回COMMIT操作符，用于标识这个SqlCall节点代表的是COMMIT语句
  // 返回值：SqlOperator类型，返回静态的OPERATOR字段
  // Override注解：表示重写父类SqlCall的抽象方法
  @Override public SqlOperator getOperator() {
    // 返回COMMIT操作符的静态实例
    return OPERATOR;
  }

  // 重写getOperandList方法：获取该节点的操作数列表
  // 作用：返回COMMIT语句的所有操作数，对于COMMIT语句只有一个操作数（chain）
  // 返回值：List<SqlNode>类型，返回包含chain字段的不可变列表
  // Override注解：表示重写父类SqlCall的抽象方法
  @Override public List<SqlNode> getOperandList() {
    // 使用ImmutableList.of创建一个包含this.chain的不可变列表
    // ImmutableList.of是工厂方法，创建包含指定元素的不可变列表
    // this.chain是唯一的操作数，表示事务链模式
    return ImmutableList.of(this.chain);
  }

  // 重写unparse方法：将SQL语法树节点反解析为SQL字符串
  // 作用：将SqlCommit节点转换为PostgreSQL的COMMIT SQL语句文本
  // 参数说明：
  // - writer：SqlWriter类型，用于输出SQL字符串的写入器
  //   提供了keyword、literal等方法来格式化输出
  // - leftPrec：int类型，左侧运算符的优先级
  //   用于确定是否需要添加括号，对于COMMIT语句不需要
  // - rightPrec：int类型，右侧运算符的优先级
  //   用于确定是否需要添加括号，对于COMMIT语句不需要
  // Override注解：表示重写父类SqlNode的方法
  @Override public void unparse(final SqlWriter writer, final int leftPrec, final int rightPrec) {
    // 使用writer的keyword方法输出"COMMIT"关键字
    // keyword方法会根据SQL方言和格式化规则输出关键字
    writer.keyword("COMMIT");
    // 检查chain字段的值是否为AND_CHAIN
    // chain.symbolValue(TransactionChainingMode.class)方法：
    // - 将SqlLiteral转换为TransactionChainingMode枚举值
    // - symbolValue是SqlLiteral的方法，用于获取字面值的符号表示
    // - TransactionChainingMode.class指定要转换的枚举类型
    // 如果chain的值是TransactionChainingMode.AND_CHAIN，说明使用了AND CHAIN语法
    if (chain.symbolValue(TransactionChainingMode.class) == TransactionChainingMode.AND_CHAIN) {
      // 使用writer的literal方法输出"AND CHAIN"字面量
      // literal方法会输出字面量文本，包括空格
      // 这将生成完整的"COMMIT AND CHAIN"语句
      writer.literal("AND CHAIN");
    }
    // 如果chain的值是AND_NO_CHAIN，则不输出任何内容
    // 因为AND NO CHAIN是默认行为，PostgreSQL允许省略它
  }
}
