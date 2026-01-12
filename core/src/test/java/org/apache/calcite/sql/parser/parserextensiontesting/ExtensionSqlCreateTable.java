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
package org.apache.calcite.sql.parser.parserextensiontesting; // 包声明：该类位于parserextensiontesting包下，用于测试SQL解析器扩展功能

import org.apache.calcite.sql.SqlDataTypeSpec; // 导入SqlDataTypeSpec类，用于表示SQL数据类型规范
import org.apache.calcite.sql.SqlIdentifier; // 导入SqlIdentifier类，用于表示SQL标识符（如表名、列名等）
import org.apache.calcite.sql.SqlNode; // 导入SqlNode类，它是所有SQL语法树的基类
import org.apache.calcite.sql.SqlNodeList; // 导入SqlNodeList类，用于表示SQL节点列表
import org.apache.calcite.sql.SqlWriter; // 导入SqlWriter类，用于将SQL语法树反解析为SQL字符串
import org.apache.calcite.sql.ddl.SqlCreateTable; // 导入SqlCreateTable类，这是标准的CREATE TABLE语句的SQL节点
import org.apache.calcite.sql.parser.SqlParserPos; // 导入SqlParserPos类，用于表示SQL语法元素在原始SQL语句中的位置
import org.apache.calcite.util.ImmutableNullableList; // 导入ImmutableNullableList类，用于创建不可变的可空列表
import org.apache.calcite.util.Pair; // 导入Pair类，用于表示键值对
import org.apache.calcite.util.Util; // 导入Util工具类，提供各种实用方法

import java.util.List; // 导入List接口，用于表示列表
import java.util.function.BiConsumer; // 导入BiConsumer函数式接口，用于双参数消费操作

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于空值检查

/**
 * Simple test example of a CREATE TABLE statement. // 类注释：这是一个简单的CREATE TABLE语句测试示例，继承自SqlCreateTable
 * 该类展示了如何扩展Calcite的CREATE TABLE语句以支持自定义的SQL语法解析和反解析功能
 * 主要用于演示如何实现自定义的DDL语句节点，特别是如何处理列定义和查询部分
 */
public class ExtensionSqlCreateTable extends SqlCreateTable { // 定义ExtensionSqlCreateTable类，继承自SqlCreateTable，表示扩展的CREATE TABLE语句
  /** Creates a SqlCreateTable. */ // 构造方法注释：创建一个ExtensionSqlCreateTable实例
  public ExtensionSqlCreateTable(SqlParserPos pos, SqlIdentifier name, // pos参数表示该SQL节点在原始SQL语句中的位置信息，用于错误报告和调试
      SqlNodeList columnList, SqlNode query) { // columnList参数表示列定义列表，包含列名和数据类型的交替元素；query参数表示CREATE TABLE AS SELECT语句中的查询部分
    super(pos, false, false, name, columnList, query); // 调用父类SqlCreateTable的构造函数，传入位置、是否临时表(false)、是否替换(false)、表名、列列表和查询
  } // 构造方法结束

  @Override public List<SqlNode> getOperandList() { // 重写getOperandList方法，返回该SQL节点的操作数列表，用于SQL语法树的遍历和处理
    return ImmutableNullableList.of(name, columnList, query); // 返回包含表名、列列表和查询的不可变列表，这些是该CREATE TABLE语句的主要组成部分
  } // getOperandList方法结束

  @Override public void unparse(SqlWriter writer, int leftPrec, int rightPrec) { // 重写unparse方法，将SQL语法树反解析为SQL字符串；writer参数用于输出SQL；leftPrec和rightPrec用于控制运算符优先级
    writer.keyword("CREATE"); // 输出CREATE关键字
    writer.keyword("TABLE"); // 输出TABLE关键字
    name.unparse(writer, leftPrec, rightPrec); // 反解析表名，输出到writer中
    if (columnList != null) { // 如果列列表不为空，则处理列定义部分
      SqlWriter.Frame frame = writer.startList("(", ")"); // 创建一个列表框架，用括号包围列定义
      forEachNameType((name, typeSpec) -> { // 遍历每一列，对每对(列名, 类型)执行以下操作
        writer.sep(","); // 输出列分隔符逗号
        name.unparse(writer, leftPrec, rightPrec); // 反解析列名
        typeSpec.unparse(writer, leftPrec, rightPrec); // 反解析数据类型
        if (Boolean.FALSE.equals(typeSpec.getNullable())) { // 如果该列不允许为空
          writer.keyword("NOT NULL"); // 输出NOT NULL约束
        } // NOT NULL约束输出结束
      }); // forEachNameType的lambda表达式结束
      writer.endList(frame); // 结束列表框架，输出右括号
    } // 列列表处理结束
    if (query != null) { // 如果查询部分不为空，表示这是CREATE TABLE AS SELECT语句
      writer.keyword("AS"); // 输出AS关键字
      writer.newlineAndIndent(); // 输出换行并增加缩进，使SQL更易读
      query.unparse(writer, 0, 0); // 反解析查询部分，优先级设为0表示不需要额外的括号
    } // 查询部分处理结束
  } // unparse方法结束

  /** Calls an action for each (name, type) pair from {@code columnList}, in which
   * they alternate. */ // 方法注释：对columnList中的每一对(列名, 类型)调用指定的操作，其中列名和类型元素交替出现
  @SuppressWarnings({"unchecked", "rawtypes"}) // 抑制编译器警告，因为这里需要使用原始类型和未检查的类型转换
  public void forEachNameType(BiConsumer<SqlIdentifier, SqlDataTypeSpec> consumer) { // 定义forEachNameType方法，接收一个BiConsumer函数，对每对(列名, 类型)执行该函数
    final List list = requireNonNull(columnList, "columnList"); // 获取列列表并确保不为null，如果为null则抛出NullPointerException
    Pair.forEach((List<SqlIdentifier>) Util.quotientList(list, 2, 0), // 从列表中提取所有偶数位置的元素（索引0, 2, 4...），转换为列名列表
        Util.quotientList((List<SqlDataTypeSpec>) list, 2, 1), consumer); // 从列表中提取所有奇数位置的元素（索引1, 3, 5...），转换为类型列表，然后对每对(列名, 类型)调用consumer函数
  } // forEachNameType方法结束
} // ExtensionSqlCreateTable类结束
