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
// 声明包名：org.apache.calcite.sql.babel，表示这个类属于Calcite框架中Babel SQL方言包
package org.apache.calcite.sql.babel;

// 导入SqlIdentifier类，用于表示SQL标识符（如表名、列名等）
import org.apache.calcite.sql.SqlIdentifier;
// 导入SqlNode类，是所有SQL语法树节点的基类
import org.apache.calcite.sql.SqlNode;
// 导入SqlNodeList类，用于表示SQL节点的列表（如列定义列表）
import org.apache.calcite.sql.SqlNodeList;
// 导入SqlWriter类，用于将SQL语法树反解析为SQL文本
import org.apache.calcite.sql.SqlWriter;
// 导入SqlCreateTable类，这是标准CREATE TABLE语句的基类
import org.apache.calcite.sql.ddl.SqlCreateTable;
// 导入SqlParserPos类，用于记录SQL解析位置信息（行号、列号等）
import org.apache.calcite.sql.parser.SqlParserPos;

/**
 * Parse tree for {@code CREATE TABLE} statement, with extensions for particular
 * SQL dialects supported by Babel.
 * 这是CREATE TABLE语句的语法树节点，专门为Babel支持的特定SQL方言提供扩展功能
 * Babel是Calcite的一个扩展模块，支持多种SQL方言的解析和处理
 * 这个类继承自SqlCreateTable，添加了特定方言的特性支持
 */
// 定义SqlBabelCreateTable类，继承自SqlCreateTable，表示支持Babel方言扩展的CREATE TABLE语句
public class SqlBabelCreateTable extends SqlCreateTable {
  // 成员变量：tableCollectionType，表示表的集合类型
  // 这是一个枚举类型，可以是SET（不允许重复行）、MULTISET（允许重复行）或UNSPECIFIED（未指定）
  // 这个特性主要针对Teradata数据库，Teradata最初要求表中行唯一，后来添加了MULTISET关键字允许重复行
  // 在其他数据库和SQL标准中，默认都是MULTISET，所以不需要显式指定
  // final关键字表示该字段在构造后不可变，保证线程安全
  private final TableCollectionType tableCollectionType;

  // 成员变量：volatile_，表示表是否为易失性表（VOLATILE TABLE）
  // 易失性表是临时表的一种，生命周期仅限于当前会话，会话结束时自动删除
  // 使用volatile_而不是volatile是因为volatile是Java关键字，不能用作变量名
  // CHECKSTYLE: IGNORE 2 表示忽略checkstyle对变量名的检查
  // final关键字表示该字段在构造后不可变
  // CHECKSTYLE: IGNORE 2; can't use 'volatile' because it is a Java keyword
  // but checkstyle does not like trailing '_'.
  private final boolean volatile_;

  /** Creates a SqlBabelCreateTable. */
  // 构造方法：创建SqlBabelCreateTable实例
  // 参数说明：
  //   pos - SqlParserPos类型，记录SQL语句在原始SQL文本中的解析位置（行号、列号），用于错误定位和调试
  //   replace - boolean类型，表示是否是CREATE OR REPLACE TABLE语句，true表示如果表已存在则替换
  //   tableCollectionType - TableCollectionType枚举类型，指定表的集合类型（SET/MULTISET/UNSPECIFIED）
  //   volatile_ - boolean类型，表示是否创建易失性表（VOLATILE TABLE），true表示创建会话级临时表
  //   ifNotExists - boolean类型，表示是否包含IF NOT EXISTS子句，true表示如果表已存在则不报错
  //   name - SqlIdentifier类型，表示要创建的表名
  //   columnList - SqlNodeList类型，表示列定义列表，每个元素是一个列定义节点
  //   query - SqlNode类型，表示CREATE TABLE ... AS查询语句中的SELECT查询，可为null（当不使用AS子句时）
  // 构造方法首先调用父类SqlCreateTable的构造函数初始化基类字段，然后初始化本类的特有字段
  public SqlBabelCreateTable(SqlParserPos pos, boolean replace,
      TableCollectionType tableCollectionType, boolean volatile_,
      boolean ifNotExists, SqlIdentifier name, SqlNodeList columnList,
      SqlNode query) {
    // 调用父类SqlCreateTable的构造函数，传入位置、replace标志、ifNotExists标志、表名、列列表和查询语句
    // 这会初始化父类中的name、columnList、query等字段，以及从SqlCreate继承的OPERATOR、pos、replace、ifNotExists等字段
    super(pos, replace, ifNotExists, name, columnList, query);
    // 将传入的tableCollectionType参数赋值给成员变量，记录表的集合类型
    this.tableCollectionType = tableCollectionType;
    // 将传入的volatile_参数赋值给成员变量，记录表是否为易失性表
    this.volatile_ = volatile_;
  }

  // 重写父类的unparse方法，将语法树节点反解析为SQL文本
  // 参数说明：
  //   writer - SqlWriter类型，SQL写入器，负责将SQL文本输出到流或字符串
  //   leftPrec - int类型，左侧运算符优先级，用于确定是否需要加括号
  //   rightPrec - int类型，右侧运算符优先级，用于确定是否需要加括号
  // 返回值：void，无返回值，直接通过writer输出SQL文本
  // 这个方法实现了将SqlBabelCreateTable对象转换为标准SQL语句的过程
  @Override public void unparse(SqlWriter writer, int leftPrec, int rightPrec) {
    // 输出关键字"CREATE"，表示创建操作
    writer.keyword("CREATE");
    // 使用switch语句根据tableCollectionType的值输出相应的集合类型关键字
    switch (tableCollectionType) {
    // 如果集合类型是SET，输出"SET"关键字，表示不允许重复行
    case SET:
      writer.keyword("SET");
      break;
    // 如果集合类型是MULTISET，输出"MULTISET"关键字，表示允许重复行
    case MULTISET:
      writer.keyword("MULTISET");
      break;
    // 如果集合类型是UNSPECIFIED或其他情况，不输出任何关键字
    default:
      break;
    }
    // 如果volatile_为true，输出"VOLATILE"关键字，表示创建易失性表
    // 易失性表是会话级临时表，会话结束时自动删除
    if (volatile_) {
      writer.keyword("VOLATILE");
    }
    // 输出关键字"TABLE"，表示创建表
    writer.keyword("TABLE");
    // 如果ifNotExists为true，输出"IF NOT EXISTS"子句
    // 这表示如果表已存在则不报错，直接跳过创建操作
    if (ifNotExists) {
      writer.keyword("IF NOT EXISTS");
    }
    // 调用name的unparse方法输出表名，传入writer和优先级参数
    // SqlIdentifier会处理表名的格式化（如是否需要加引号、是否需要schema前缀等）
    name.unparse(writer, leftPrec, rightPrec);
    // 如果columnList不为null，说明有列定义，需要输出列列表
    if (columnList != null) {
      // 开始一个列表框架，使用"("和")"作为分隔符，表示列定义列表的开始
      // Frame是SqlWriter的一种机制，用于管理缩进和格式化
      SqlWriter.Frame frame = writer.startList("(", ")");
      // 遍历列定义列表中的每一个列节点
      for (SqlNode c : columnList) {
        // 在列之间输出逗号分隔符（除了第一个列）
        // sep方法会智能处理逗号，只在需要时输出
        writer.sep(",");
        // 调用列节点的unparse方法输出列定义
        // 传入0,0作为优先级，因为列定义不需要考虑运算符优先级
        c.unparse(writer, 0, 0);
      }
      // 结束列表框架，输出")"并恢复之前的缩进级别
      // 这会自动关闭括号并调整格式
      writer.endList(frame);
    }
    // 如果query不为null，说明有AS子句，需要输出查询语句
    // 这表示通过SELECT查询的结果来创建表并填充数据（CREATE TABLE ... AS SELECT ...）
    if (query != null) {
      // 输出关键字"AS"，表示后面跟随的是查询语句
      writer.keyword("AS");
      // 输出换行并增加缩进，提高SQL语句的可读性
      // newlineAndIndent会根据当前的缩进级别自动格式化
      writer.newlineAndIndent();
      // 调用查询节点的unparse方法输出SELECT查询语句
      // 传入0,0作为优先级，因为查询语句本身不需要考虑外部运算符优先级
      query.unparse(writer, 0, 0);
    }
  }
}