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
 */ // Apache许可证声明，规定了代码的使用权限和限制
package org.apache.calcite.sql.babel.postgres; // 定义包名，表示这个类属于Calcite项目中PostgreSQL方言的babel适配器模块

import org.apache.calcite.sql.SqlCall; // 导入SqlCall类，这是所有SQL调用节点的基类，SqlBegin继承自它
import org.apache.calcite.sql.SqlNode; // 导入SqlNode接口，这是所有SQL语法树节点的基接口
import org.apache.calcite.sql.SqlNodeList; // 导入SqlNodeList类，用于存储SqlNode的列表，这里用来存储事务模式列表
import org.apache.calcite.sql.SqlOperator; // 导入SqlOperator接口，表示SQL操作符，BEGIN也是一个操作符
import org.apache.calcite.sql.SqlWriter; // 导入SqlWriter类，用于将SQL语法树节点反解析为SQL文本字符串
import org.apache.calcite.sql.fun.SqlBasicOperator; // 导入SqlBasicOperator类，表示基本的SQL操作符实现
import org.apache.calcite.sql.parser.SqlParserPos; // 导入SqlParserPos类，用于记录SQL语法树节点在原始SQL文本中的位置信息

import com.google.common.collect.ImmutableList; // 导入Google Guava库的ImmutableList类，用于创建不可变的列表

import java.util.List; // 导入Java标准库的List接口，用于返回操作数列表

/**
 * Parse tree node representing a {@code BEGIN} clause. // 类的JavaDoc注释：表示这是一个代表BEGIN子句的语法树节点
 * // BEGIN语句用于在PostgreSQL中启动一个新的事务块
 * // 这个类封装了BEGIN语句的所有信息，包括事务模式（如READ ONLY、READ WRITE、ISOLATION LEVEL等）
 * // 继承自SqlCall类，表示这是一个SQL调用类型的节点
 * // 在Calcite的SQL解析过程中，当解析到BEGIN语句时会创建这个类的实例
 *
 * @see <a href="https://www.postgresql.org/docs/current/sql-begin.html">BEGIN specification</a> // 引用PostgreSQL官方文档中BEGIN语句的规范说明
 */ // JavaDoc注释结束
public class SqlBegin extends SqlCall { // 定义SqlBegin类，继承自SqlCall，表示这是一个BEGIN事务语句的语法树节点
  public static final SqlBasicOperator OPERATOR = // 定义一个公共静态常量OPERATOR，表示BEGIN操作符
      SqlBasicOperator.create("BEGIN").withCallFactory( // 创建一个名为"BEGIN"的基本SQL操作符，并设置调用工厂
          (operator, functionQualifier, pos, operands) -> // 使用Lambda表达式定义调用工厂，用于创建SqlBegin实例
              new SqlBegin(pos, (SqlNodeList) operands[0])); // 调用工厂的逻辑：根据位置和操作数创建新的SqlBegin对象，操作数第0个元素转换为SqlNodeList类型作为事务模式列表

  private final SqlNodeList transactionModeList; // 定义私有final成员变量，存储事务模式列表，包含BEGIN语句中指定的所有事务模式参数（如ISOLATION LEVEL、READ ONLY等）

  protected SqlBegin(final SqlParserPos pos, final SqlNodeList transactionModeList) { // 定义受保护的构造方法，接收位置参数和事务模式列表参数
    super(pos); // 调用父类SqlCall的构造方法，传入位置参数，初始化语法树节点的位置信息
    this.transactionModeList = transactionModeList; // 将传入的事务模式列表赋值给成员变量，保存BEGIN语句的事务模式配置
  } // 构造方法结束

  @Override public SqlOperator getOperator() { // 重写父类SqlCall的getOperator方法，用于获取当前节点对应的SQL操作符
    return OPERATOR; // 返回类中定义的BEGIN操作符常量
  } // 方法结束

  @Override public List<SqlNode> getOperandList() { // 重写父类SqlCall的getOperandList方法，用于获取当前节点的所有操作数列表
    return ImmutableList.of(transactionModeList); // 返回一个不可变列表，包含事务模式列表作为唯一的操作数
  } // 方法结束

  @Override public void unparse(final SqlWriter writer, final int leftPrec, final int rightPrec) { // 重写父类SqlNode的unparse方法，用于将语法树节点反解析为SQL字符串
    writer.keyword("BEGIN"); // 使用SqlWriter输出关键字"BEGIN"，这是BEGIN语句的起始部分
    transactionModeList.unparse(writer, -1, -1); // 将事务模式列表反解析为SQL文本，使用-1作为左右优先级参数表示不需要额外括号
  } // 方法结束
} // 类定义结束
