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
// 声明包名，表示这个类属于org.apache.calcite.sql.babel.postgres包，是Calcite框架中用于处理PostgreSQL方言的SQL解析树节点
package org.apache.calcite.sql.babel.postgres;

// 导入SqlCall类，这是Calcite中所有SQL调用节点的基类，SqlDiscard继承自它
import org.apache.calcite.sql.SqlCall;
// 导入SqlIdentifier类，用于表示SQL中的标识符（如表名、列名、关键字等）
import org.apache.calcite.sql.SqlIdentifier;
// 导入SqlNode接口，这是Calcite中所有SQL语法树节点的顶级接口
import org.apache.calcite.sql.SqlNode;
// 导入SqlOperator接口，用于表示SQL操作符（如SELECT、INSERT、DISCARD等）
import org.apache.calcite.sql.SqlOperator;
// 导入SqlWriter类，用于将SQL语法树节点转换为可读的SQL字符串
import org.apache.calcite.sql.SqlWriter;
// 导入SqlBasicOperator类，这是Calcite中基础SQL操作符的实现类
import org.apache.calcite.sql.fun.SqlBasicOperator;
// 导入SqlParserPos类，用于记录SQL语法树节点在原始SQL语句中的位置信息（行号、列号等），便于错误定位和调试
import org.apache.calcite.sql.parser.SqlParserPos;

// 导入Google Guava库的ImmutableList类，用于创建不可变的列表，保证线程安全和数据一致性
import com.google.common.collect.ImmutableList;

// 导入Java标准库的List接口，用于表示有序的元素集合
import java.util.List;

/**
 * Parse tree node representing a {@code DISCARD} clause.
 * 表示DISCARD子句的语法树节点
 * 
 * 类作用说明：
 * 这个类是Calcite框架中用于表示PostgreSQL DISCARD语句的语法树节点。
 * DISCARD是PostgreSQL特有的SQL命令，用于重置会话状态，释放与当前会话相关的资源。
 * 
 * DISCARD命令支持以下子命令：
 * - ALL: 重置所有会话状态，包括临时表、通知、预准备语句、游标等
 * - PLANS: 释放所有缓存的查询计划
 * - SEQUENCES: 关闭所有打开的序列对象并重置它们的缓存
 * - TEMP: 删除当前会话创建的所有临时表
 * - TEMPORARY: 同TEMP
 * 
 * 这个类继承自SqlCall，表示这是一个SQL调用类型的语法树节点。
 * 在Calcite的SQL解析过程中，当解析器遇到DISCARD语句时，会创建这个类的实例来表示该语句的语法结构。
 * 
 * 通过这个类，Calcite可以：
 * 1. 解析DISCARD语句的语法结构
 * 2. 验证DISCARD语句的语法正确性
 * 3. 将DISCARD语句转换为可执行的SQL字符串（通过unparse方法）
 * 4. 在查询优化和执行过程中处理DISCARD语句
 *
 * @see <a href="https://www.postgresql.org/docs/current/sql-discard.html">DISCARD specification</a>
 * 参考PostgreSQL官方文档中关于DISCARD命令的详细说明
 */
public class SqlDiscard extends SqlCall {
  // 定义DISCARD操作符的静态常量，这个操作符用于在SQL解析器中识别DISCARD语句
  // 使用SqlBasicOperator.create方法创建一个名为"DISCARD"的基本操作符
  // withCallFactory方法设置调用工厂，当解析器遇到DISCARD操作符时，会使用这个工厂来创建SqlDiscard实例
  // 调用工厂是一个lambda表达式，接收操作符、函数限定符、位置信息和操作数列表作为参数
  // 它将操作数列表的第一个元素（ operands[0]）转换为SqlIdentifier类型，并创建一个新的SqlDiscard实例
  // 这样设计的好处是将操作符的定义和节点的创建逻辑分离，提高了代码的可维护性和灵活性
  public static final SqlBasicOperator OPERATOR =
      SqlBasicOperator.create("DISCARD").withCallFactory(
          (operator, functionQualifier, pos, operands) ->
              new SqlDiscard(pos, (SqlIdentifier) operands[0]));

  // 定义一个私有的最终成员变量，用于存储DISCARD命令的子命令（如ALL、PLANS、SEQUENCES、TEMP等）
  // 使用SqlIdentifier类型表示，因为子命令在SQL中是一个标识符
  // final关键字表示这个变量在构造后不能被修改，保证了对象的不可变性
  // 这个成员变量是SqlDiscard节点的核心数据，记录了用户想要丢弃什么类型的会话状态
  private final SqlIdentifier subcommand;

  // 构造方法：创建一个新的SqlDiscard实例
  // 参数pos：SqlParserPos类型，表示DISCARD语句在原始SQL中的位置信息（起始行号、列号等）
  //         这个信息对于错误报告和调试非常重要，可以精确定位问题所在
  // 参数subcommand：SqlIdentifier类型，表示DISCARD命令的子命令（如"ALL"、"PLANS"等）
  //                 这个子命令决定了具体要丢弃什么类型的会话状态
  // 构造方法首先调用父类SqlCall的构造方法，传入位置信息pos，初始化父类的状态
  // 然后将subcommand参数赋值给成员变量this.subcommand，保存子命令信息
  // 使用final参数和final成员变量确保了对象的不可变性，符合函数式编程的原则
  public SqlDiscard(final SqlParserPos pos, final SqlIdentifier subcommand) {
    super(pos); // 调用父类SqlCall的构造方法，初始化位置信息
    this.subcommand = subcommand; // 保存子命令到成员变量
  }

  // 重写父类SqlCall的getOperator方法，返回这个节点对应的操作符
  // 这个方法是SqlNode接口的一部分，用于获取当前节点对应的SQL操作符
  // 返回值：SqlOperator类型，返回静态常量OPERATOR，即DISCARD操作符
  // 这个方法在查询优化、验证和SQL生成过程中会被调用，用于识别节点的类型
  // 通过返回预定义的OPERATOR常量，确保了所有SqlDiscard实例都使用相同的操作符定义
  @Override public SqlOperator getOperator() {
    return OPERATOR; // 返回DISCARD操作符
  }

  // 重写父类SqlCall的getOperandList方法，返回这个节点的操作数列表
  // 在Calcite中，操作数是指SQL语句中的参数或子表达式
  // 对于DISCARD语句，只有一个操作数，就是子命令（如ALL、PLANS等）
  // 返回值：List<SqlNode>类型，返回一个不可变的列表，包含subcommand作为唯一元素
  // 使用ImmutableList.of方法创建不可变列表，确保返回的列表不能被修改
  // 这个方法在语法树遍历、操作数访问和SQL生成过程中会被调用
  // 将操作数封装在列表中返回，使得调用者可以统一处理不同类型的SQL节点
  @Override public List<SqlNode> getOperandList() {
    return ImmutableList.of(subcommand); // 返回包含子命令的不可变列表
  }

  // 重写父类SqlNode的unparse方法，将这个语法树节点转换为SQL字符串
  // 这个方法用于将解析后的语法树重新生成为可执行的SQL语句
  // 参数writer：SqlWriter类型，用于写入SQL字符串的写入器
  //             SqlWriter提供了多种方法来格式化SQL输出，如keyword、literal、identifier等
  // 参数leftPrec：int类型，表示左侧运算符的优先级，用于确定是否需要添加括号
  //               在表达式解析中，优先级用于控制运算顺序和括号的使用
  // 参数rightPrec：int类型，表示右侧运算符的优先级，用于确定是否需要添加括号
  //                对于DISCARD语句，优先级参数通常不使用，因为DISCARD是一个命令而不是表达式
  // 方法实现：
  // 1. 调用writer.keyword("DISCARD")写入DISCARD关键字，keyword方法会根据SQL方言正确处理关键字的大小写
  // 2. 调用writer.literal(subcommand.toString())写入子命令，literal方法会将值作为字面量写入
  //    subcommand.toString()将SqlIdentifier转换为字符串形式
  // 这样生成的SQL语句格式为：DISCARD <subcommand>，例如：DISCARD ALL、DISCARD PLANS等
  @Override public void unparse(final SqlWriter writer, final int leftPrec, final int rightPrec) {
    writer.keyword("DISCARD"); // 写入DISCARD关键字
    writer.literal(subcommand.toString()); // 写入子命令字面量
  }
}
