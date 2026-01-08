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
package org.apache.calcite.sql.babel.postgres; // 定义包名，该类位于org.apache.calcite.sql.babel.postgres包下，用于处理PostgreSQL方言的SHOW语句

import org.apache.calcite.sql.SqlCall; // 导入SqlCall类，SqlShow继承自SqlCall，表示一个SQL函数调用节点
import org.apache.calcite.sql.SqlIdentifier; // 导入SqlIdentifier类，用于表示SQL标识符（如变量名、表名等）
import org.apache.calcite.sql.SqlNode; // 导入SqlNode类，所有SQL语法树节点的基类
import org.apache.calcite.sql.SqlOperator; // 导入SqlOperator类，表示SQL操作符（如函数、运算符等）
import org.apache.calcite.sql.SqlWriter; // 导入SqlWriter类，用于将SQL语法树节点序列化为SQL字符串
import org.apache.calcite.sql.fun.SqlBasicOperator; // 导入SqlBasicOperator类，表示基本的SQL操作符
import org.apache.calcite.sql.parser.SqlParserPos; // 导入SqlParserPos类，用于记录SQL语法树节点在原始SQL文本中的位置信息

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList类，用于创建不可变列表

import java.util.List; // 导入Java标准库的List接口

/**
 * Parse tree node representing a {@code SHOW} clause.
 *
 * @see <a href="https://www.postgresql.org/docs/current/sql-show.html">SHOW specification</a>
 */
public class SqlShow extends SqlCall { // 定义SqlShow类，继承自SqlCall，表示PostgreSQL的SHOW语句的语法树节点，用于显示系统参数、配置等信息
  public static final SqlBasicOperator OPERATOR = // 定义一个静态常量OPERATOR，类型为SqlBasicOperator，表示SHOW操作符
      SqlBasicOperator.create("SHOW").withCallFactory( // 创建一个名为"SHOW"的基本操作符，并设置调用工厂
          (operator, functionQualifier, pos, operands) -> // 使用Lambda表达式定义调用工厂，用于创建SqlShow实例
              new SqlShow(pos, (SqlIdentifier) operands[0])); // 创建并返回一个新的SqlShow对象，传入位置参数和操作数（第一个操作数转换为SqlIdentifier）
  private final SqlIdentifier name; // 定义成员变量name，类型为SqlIdentifier，用于存储SHOW语句中要显示的参数名或标识符，final表示该字段不可变

  protected SqlShow(final SqlParserPos pos, SqlIdentifier name) { // 定义构造方法，接收SqlParserPos和SqlIdentifier参数，用于创建SqlShow实例
    super(pos); // 调用父类SqlCall的构造方法，传入位置信息
    this.name = name; // 将传入的name参数赋值给成员变量name
  }

  @Override public SqlOperator getOperator() { // 重写父类SqlCall的getOperator方法，返回当前节点对应的操作符
    return OPERATOR; // 返回静态常量OPERATOR，即SHOW操作符
  }

  @Override public List<SqlNode> getOperandList() { // 重写父类SqlCall的getOperandList方法，返回当前节点的操作数列表
    return ImmutableList.of(name); // 返回一个包含name的不可变列表，作为操作数列表
  }

  @Override public void unparse(final SqlWriter writer, final int leftPrec, final int rightPrec) { // 重写父类SqlNode的unparse方法，将当前语法树节点序列化为SQL字符串
    writer.keyword("SHOW"); // 使用SqlWriter写入关键字"SHOW"
    writer.identifier(name.getSimple(), false); // 使用SqlWriter写入标识符name的简单名称，第二个参数false表示不强制加引号
  }

  public SqlIdentifier getName() { // 定义公共方法getName，用于获取SHOW语句中的参数名
    return name; // 返回成员变量name
  }
}
