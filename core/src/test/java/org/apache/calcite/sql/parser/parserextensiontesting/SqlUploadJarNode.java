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
// Apache许可证声明，表明此代码遵循Apache 2.0许可证协议
package org.apache.calcite.sql.parser.parserextensiontesting; // 定义包名，属于Calcite SQL解析器扩展测试包

import org.apache.calcite.sql.SqlAlter; // 导入SqlAlter基类，表示SQL ALTER语句的抽象基类
import org.apache.calcite.sql.SqlKind; // 导入SqlKind枚举，定义SQL语句的类型
import org.apache.calcite.sql.SqlNode; // 导入SqlNode接口，表示SQL语法树的节点
import org.apache.calcite.sql.SqlOperator; // 导入SqlOperator接口，表示SQL操作符
import org.apache.calcite.sql.SqlSpecialOperator; // 导入SqlSpecialOperator类，表示特殊SQL操作符
import org.apache.calcite.sql.SqlWriter; // 导入SqlWriter接口，用于将SQL语法树转换为SQL字符串
import org.apache.calcite.sql.parser.SqlParserPos; // 导入SqlParserPos类，表示SQL解析位置信息

import java.util.List; // 导入List接口，用于存储JAR路径列表

/**
 * Simple test example of a custom alter system call.
 */
// 类注释：这是一个自定义的ALTER SYSTEM调用的简单测试示例，用于演示如何扩展Calcite的SQL语法
// 该类实现了上传JAR文件的功能，用于向系统中加载自定义的JAR包
public class SqlUploadJarNode extends SqlAlter { // 定义SqlUploadJarNode类，继承自SqlAlter基类，表示上传JAR的SQL节点
  public static final SqlOperator OPERATOR = // 定义静态常量OPERATOR，表示此SQL节点的操作符
      new SqlSpecialOperator("UPLOAD JAR", SqlKind.OTHER_DDL); // 创建特殊操作符，名称为"UPLOAD JAR"，类型为其他DDL语句

  private final List<SqlNode> jarPaths; // 定义私有常量成员变量jarPaths，存储要上传的JAR文件路径列表，类型为SqlNode列表

  /** Creates a SqlUploadJarNode. */
  // 构造方法注释：创建SqlUploadJarNode实例，用于初始化上传JAR的SQL节点
  public SqlUploadJarNode(SqlParserPos pos, String scope, List<SqlNode> jarPaths) { // 构造方法，接收解析位置、作用域和JAR路径列表参数
    super(pos, scope); // 调用父类SqlAlter的构造方法，传递位置和作用域参数
    this.jarPaths = jarPaths; // 将传入的JAR路径列表赋值给成员变量jarPaths
  } // 构造方法结束

  @Override public SqlOperator getOperator() { // 重写getOperator方法，返回此节点的操作符
    return OPERATOR; // 返回静态常量OPERATOR，即"UPLOAD JAR"操作符
  } // getOperator方法结束

  @Override public List<SqlNode> getOperandList() { // 重写getOperandList方法，返回操作数列表
    return jarPaths; // 返回JAR路径列表作为操作数列表
  } // getOperandList方法结束

  @Override protected void unparseAlterOperation(SqlWriter writer, int leftPrec, int rightPrec) { // 重写unparseAlterOperation方法，将此节点转换为SQL字符串，接收SqlWriter和左右优先级参数
    writer.keyword("UPLOAD"); // 使用SqlWriter写入关键字"UPLOAD"
    writer.keyword("JAR"); // 使用SqlWriter写入关键字"JAR"
    SqlWriter.Frame frame = writer.startList("", ""); // 开始一个列表帧，用于处理多个JAR路径，无前缀和后缀
    for (SqlNode jarPath : jarPaths) { // 遍历JAR路径列表中的每个路径节点
      jarPath.unparse(writer, leftPrec, rightPrec); // 调用每个JAR路径节点的unparse方法，将其转换为SQL字符串并写入writer
    } // for循环结束
    writer.endList(frame); // 结束列表帧，完成SQL字符串的生成
  } // unparseAlterOperation方法结束
} // SqlUploadJarNode类定义结束
