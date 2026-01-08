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
// 声明包路径，表明该类属于Calcite的Babel适配器模块中的PostgreSQL方言包
package org.apache.calcite.sql.babel.postgres;

// 导入SqlCall抽象类，这是Calcite中所有SQL调用语句的基类，SqlRollback继承自此类
import org.apache.calcite.sql.SqlCall;
// 导入SqlLiteral类，用于表示SQL中的字面量值，如布尔值、枚举值等
import org.apache.calcite.sql.SqlLiteral;
// 导入SqlNode接口，这是Calcite SQL抽象语法树(AST)中所有节点的基接口
import org.apache.calcite.sql.SqlNode;
// 导入SqlOperator接口，表示SQL操作符，ROLLBACK就是一个操作符
import org.apache.calcite.sql.SqlOperator;
// 导入SqlWriter类，用于将SQL节点反解析为SQL文本字符串
import org.apache.calcite.sql.SqlWriter;
// 导入SqlBasicOperator类，表示基本的SQL操作符实现
import org.apache.calcite.sql.fun.SqlBasicOperator;
// 导入SqlParserPos类，用于记录SQL节点在原始SQL语句中的位置信息，便于错误定位
import org.apache.calcite.sql.parser.SqlParserPos;

// 导入Google Guava库的ImmutableList类，用于创建不可变的列表集合
import com.google.common.collect.ImmutableList;

// 导入Java标准库的List接口
import java.util.List;

/**
 * Parse tree node representing a {@code ROLLBACK} clause.
 * 这是一个表示PostgreSQL的ROLLBACK语句的解析树节点类
 * ROLLBACK用于回滚当前事务，撤销事务中所有未提交的修改
 *
 * 在PostgreSQL中，ROLLBACK语句的语法格式为：
 * ROLLBACK [AND CHAIN | AND NO CHAIN]
 * 其中AND CHAIN表示回滚后立即开启一个具有相同事务特性的新事务
 * AND NO CHAIN表示回滚后不开启新事务（默认行为）
 *
 * 该类继承自SqlCall，表示这是一个SQL调用语句节点
 *
 * @see <a href="https://www.postgresql.org/docs/current/sql-rollback.html">ROLLBACK specification</a>
 * 参考PostgreSQL官方文档中的ROLLBACK语句规范
 */
// 定义SqlRollback类，继承自SqlCall，表示ROLLBACK语句的AST节点
public class SqlRollback extends SqlCall {

  // 定义ROLLBACK操作符的静态常量，这是该SQL语句的操作符标识
  // 使用SqlBasicOperator创建一个名为"ROLLBACK"的操作符
  public static final SqlBasicOperator OPERATOR =
      SqlBasicOperator.create("ROLLBACK").withCallFactory(
          // 设置调用工厂，当解析器遇到ROLLBACK语句时会调用此工厂方法创建SqlRollback实例
          // 参数说明：
          // - operator: 操作符本身（即ROLLBACK）
          // - functionQualifier: 函数限定符（通常为null）
          // - pos: 解析位置信息（记录在SQL语句中的位置）
          // - operands: 操作数数组，这里只有一个元素，即表示是否使用AND CHAIN的字面量
          // 返回值：创建并返回一个新的SqlRollback实例
          (operator, functionQualifier, pos, operands) ->
              new SqlRollback(pos, (SqlLiteral) operands[0]));

  // 成员变量：chain，类型为SqlLiteral（字面量）
  // 该变量表示ROLLBACK语句后的链式事务模式，可以是AND_CHAIN或AND_NO_CHAIN
  // 使用final修饰表示该字段在构造后不可变，保证线程安全
  // SqlLiteral是一个包装类，用于存储各种字面量值（布尔值、数字、字符串、符号等）
  private final SqlLiteral chain;

  /**
   * SqlRollback类的构造方法，用于创建ROLLBACK语句的AST节点
   * 构造方法使用protected修饰，表示只能通过工厂方法或子类创建实例
   *
   * @param pos SqlParserPos类型，表示该节点在原始SQL语句中的位置信息
   *            包含行号、列号等信息，用于错误报告和调试
   * @param chain SqlLiteral类型，表示事务链式模式
   *              该字面量的值是TransactionChainingMode枚举类型
   *              可能的值：TransactionChainingMode.AND_CHAIN 或 TransactionChainingMode.AND_NO_CHAIN
   */
  protected SqlRollback(final SqlParserPos pos, final SqlLiteral chain) {
    // 调用父类SqlCall的构造方法，传入位置信息，初始化AST节点的基本属性
    super(pos);
    // 将传入的chain参数赋值给成员变量this.chain
    // 该字段存储了ROLLBACK语句是否使用AND CHAIN选项的信息
    this.chain = chain;
  }

  /**
   * 获取当前SQL节点的操作符
   * 这是SqlCall抽象类要求实现的方法，用于返回该调用语句的操作符
   *
   * @return SqlOperator类型，返回ROLLBACK操作符（即上面定义的OPERATOR常量）
   *         操作符用于标识这个SQL调用是什么类型的语句
   */
  @Override public SqlOperator getOperator() {
    // 返回ROLLBACK操作符的静态常量
    return OPERATOR;
  }

  /**
   * 获取当前SQL节点的操作数列表
   * 这是SqlCall抽象类要求实现的方法，用于返回该调用语句的所有操作数
   * 操作数是指操作符作用的参数，例如在函数调用中函数名是操作符，参数是操作数
   *
   * @return List<SqlNode>类型，返回包含一个元素的不可变列表
   *         列表中的元素是this.chain，即表示AND CHAIN/AND NO CHAIN选项的字面量
   *         使用ImmutableList.of()创建不可变列表，确保操作数列表不会被意外修改
   */
  @Override public List<SqlNode> getOperandList() {
    // 返回一个包含chain成员变量的不可变列表
    // ImmutableList.of()创建一个包含指定元素的不可变列表
    return ImmutableList.of(this.chain);
  }

  /**
   * 将AST节点反解析为SQL文本字符串
   * 这是SqlNode接口要求实现的方法，用于将语法树节点转换回可执行的SQL语句
   *
   * @param writer SqlWriter类型，SQL写入器，用于构建输出SQL字符串
   *               提供了keyword()、literal()等方法来写入不同类型的SQL文本
   * @param leftPrec int类型，左优先级，用于确定是否需要添加括号
   *                表示当前节点左侧表达式的优先级
   * @param rightPrec int类型，右优先级，用于确定是否需要添加括号
   *                 表示当前节点右侧表达式的优先级
   *                 对于ROLLBACK这种顶层语句，优先级参数通常不使用
   */
  @Override public void unparse(final SqlWriter writer, final int leftPrec, final int rightPrec) {
    // 使用writer写入ROLLBACK关键字
    // keyword()方法会确保关键字按照SQL规范格式化（如大写）
    writer.keyword("ROLLBACK");

    // 检查chain字面量的值是否为AND_CHAIN模式
    // symbolValue()方法从SqlLiteral中提取实际的符号值，转换为TransactionChainingMode枚举类型
    // 如果值为AND_CHAIN，表示ROLLBACK后需要立即开启新事务
    if (chain.symbolValue(TransactionChainingMode.class) == TransactionChainingMode.AND_CHAIN) {
      // 写入"AND CHAIN"文本，表示回滚后继续开启新事务
      // literal()方法用于写入SQL中的字面量文本
      writer.literal("AND CHAIN");
    }
    // 如果chain的值是AND_NO_CHAIN，则不写入任何内容
    // 因为AND NO CHAIN是默认行为，PostgreSQL允许省略这个选项
  }
}
