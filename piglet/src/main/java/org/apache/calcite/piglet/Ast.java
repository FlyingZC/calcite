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
package org.apache.calcite.piglet; // Piglet 包，包含 Apache Calcite 对 Pig Latin 语言的抽象语法树实现

import org.apache.calcite.avatica.util.Spacer; // 导入 Spacer 工具类，用于处理缩进和格式化
import org.apache.calcite.linq4j.Ord; // 导入 Ord 类，用于为列表元素添加索引
import org.apache.calcite.sql.parser.SqlParserPos; // 导入 SqlParserPos，用于记录 SQL 解析位置信息
import org.apache.calcite.sql.parser.SqlParserUtil; // 导入 SqlParserUtil，用于 SQL 解析工具方法
import org.apache.calcite.util.Pair; // 导入 Pair 类，用于存储键值对
import org.apache.calcite.util.Util; // 导入 Util 工具类，提供通用工具方法

import com.google.common.collect.ImmutableList; // 导入 ImmutableList，用于创建不可变列表

import java.math.BigDecimal; // 导入 BigDecimal，用于高精度数值运算
import java.util.List; // 导入 List 接口，用于列表操作

import static java.util.Objects.requireNonNull; // 导入 requireNonNull 静态方法，用于空值检查

/** Abstract syntax tree. // 抽象语法树类，用于表示 Pig Latin 语言的语法结构
 *
 * <p>Contains inner classes for various kinds of parse tree node. // 包含各种解析树节点的内部类
 */
public class Ast { // Ast 类，定义 Pig Latin 抽象语法树的根节点和相关操作
  private Ast() {} // 私有构造方法，防止实例化，该类只提供静态方法和内部类

  public static String toString(Node x) { // 将语法树节点转换为字符串表示的静态方法
    return new UnParser().append(x).buf.toString(); // 创建 UnParser 对象，追加节点，返回缓冲区的字符串内容
  }

  /** Formats a node and its children as a string. // 将节点及其子节点格式化为字符串的方法，用于调试和输出
   */
  public static UnParser unParse(UnParser u, Node n) { // 静态方法，使用 UnParser 格式化指定节点
    switch (n.op) { // 根据节点操作类型进行分支处理
    case PROGRAM: // 处理程序节点类型
      final Program program = (Program) n; // 将节点强制转换为 Program 类型
      return u.append("{op: PROGRAM, stmts: ").appendList(program.stmtList) // 追加操作类型和语句列表
          .append("}"); // 追加右大括号并返回
    case LOAD: // 处理 LOAD 语句节点类型
      final LoadStmt load = (LoadStmt) n; // 将节点强制转换为 LoadStmt 类型
      return u.append("{op: LOAD, target: " + load.target.value + ", name: " // 输出操作类型、目标变量名和加载名称
          + load.name.value + "}"); // 追加右大括号并返回
    case DUMP: // 处理 DUMP 语句节点类型
      final DumpStmt dump = (DumpStmt) n; // 将节点强制转换为 DumpStmt 类型
      return u.append("{op: DUMP, relation: " + dump.relation.value + "}"); // 输出操作类型和关系名称
    case DESCRIBE: // 处理 DESCRIBE 语句节点类型
      final DescribeStmt describe = (DescribeStmt) n; // 将节点强制转换为 DescribeStmt 类型
      return u.append("{op: DESCRIBE, relation: " + describe.relation.value // 输出操作类型和关系名称
          + "}"); // 追加右大括号并返回
    case FOREACH: // 处理 FOREACH 语句节点类型（非嵌套）
      final ForeachStmt foreach = (ForeachStmt) n; // 将节点强制转换为 ForeachStmt 类型
      return u.append("{op: FOREACH, target: " + foreach.target.value // 输出操作类型、目标变量名
          + ", source: " + foreach.source.value + ", expList: ") // 输出源关系名称和表达式列表标签
          .appendList(foreach.expList) // 追加表达式列表
          .append("}"); // 追加右大括号并返回
    case FOREACH_NESTED: // 处理 FOREACH 嵌套语句节点类型
      final ForeachNestedStmt foreachNested = (ForeachNestedStmt) n; // 将节点强制转换为 ForeachNestedStmt 类型
      return u.append("{op: FOREACH, target: " + foreachNested.target.value // 输出操作类型、目标变量名
          + ", source: " + foreachNested.source.value // 输出源关系名称
          + ", nestedOps: ") // 输出嵌套操作标签
          .appendList(foreachNested.nestedStmtList) // 追加嵌套语句列表
          .append(", expList: ") // 输出表达式列表标签
          .appendList(foreachNested.expList) // 追加表达式列表
          .append("}"); // 追加右大括号并返回
    case FILTER: // 处理 FILTER 语句节点类型
      final FilterStmt filter = (FilterStmt) n; // 将节点强制转换为 FilterStmt 类型
      u.append("{op: FILTER, target: " + filter.target.value + ", source: " // 输出操作类型、目标变量名和源关系名称
          + filter.source.value + ", condition: "); // 输出条件标签
      u.in().append(filter.condition).out(); // 增加缩进，追加条件节点，减少缩进
      return u.append("}"); // 追加右大括号并返回
    case DISTINCT: // 处理 DISTINCT 语句节点类型
      final DistinctStmt distinct = (DistinctStmt) n; // 将节点强制转换为 DistinctStmt 类型
      return u.append("{op: DISTINCT, target: " + distinct.target.value // 输出操作类型、目标变量名
          + ", source: " + distinct.source.value + "}"); // 输出源关系名称并返回
    case LIMIT: // 处理 LIMIT 语句节点类型
      final LimitStmt limit = (LimitStmt) n; // 将节点强制转换为 LimitStmt 类型
      return u.append("{op: LIMIT, target: ").append(limit.target.value) // 输出操作类型和目标变量名
          .append(", source: ").append(limit.source.value) // 输出源关系名称
          .append(", count: ").append(limit.count.value.toString()) // 输出限制数量
          .append("}"); // 追加右大括号并返回
    case ORDER: // 处理 ORDER 语句节点类型
      final OrderStmt order = (OrderStmt) n; // 将节点强制转换为 OrderStmt 类型
      return u.append("{op: ORDER, target: " + order.target.value // 输出操作类型、目标变量名
          + ", source: " + order.source.value + "}"); // 输出源关系名称并返回
    case GROUP: // 处理 GROUP 语句节点类型
      final GroupStmt group = (GroupStmt) n; // 将节点强制转换为 GroupStmt 类型
      u.append("{op: GROUP, target: " + group.target.value // 输出操作类型、目标变量名
          + ", source: " + group.source.value); // 输出源关系名称
      if (group.keys != null) { // 如果分组键不为空
        u.append(", keys: ").appendList(group.keys); // 输出分组键列表
      }
      return u.append("}"); // 追加右大括号并返回
    case LITERAL: // 处理字面量节点类型
      final Literal literal = (Literal) n; // 将节点强制转换为 Literal 类型
      return u.append(String.valueOf(literal.value)); // 输出字面量的值
    case IDENTIFIER: // 处理标识符节点类型
      final Identifier id = (Identifier) n; // 将节点强制转换为 Identifier 类型
      return u.append(id.value); // 输出标识符的值
    default: // 默认情况，处理未知操作类型
      throw new AssertionError("unknown op " + n.op); // 抛出断言错误，表示遇到未知操作
    }
  }

  /** Parse tree node type. // 解析树节点类型枚举，定义了 Pig Latin 语法中所有可能的节点操作类型
   */
  public enum Op { // Op 枚举类，枚举所有语法树节点的操作类型
    PROGRAM, // 程序节点，表示完整的 Pig Latin 脚本

    // atoms // 原子类型节点，表示基本的语法元素
    LITERAL, IDENTIFIER, BAG, TUPLE, // 字面量、标识符、包（Bag）、元组（Tuple）

    // statements // 语句类型节点，表示 Pig Latin 的各种语句
    DESCRIBE, DISTINCT, DUMP, LOAD, FOREACH, FILTER, // 描述、去重、输出、加载、foreach、过滤语句
    FOREACH_NESTED, LIMIT, ORDER, GROUP, VALUES, // 嵌套 foreach、限制、排序、分组、值语句

    // types // 类型节点，表示数据类型定义
    SCHEMA, SCALAR_TYPE, BAG_TYPE, TUPLE_TYPE, MAP_TYPE, FIELD_SCHEMA, // 模式、标量类型、包类型、元组类型、映射类型、字段模式

    // operators // 运算符节点，表示各种运算符
    DOT, EQ, NE, GT, LT, GTE, LTE, PLUS, MINUS, AND, OR, NOT // 点操作、等于、不等于、大于、小于、大于等于、小于等于、加、减、与、或、非
  }

  /** Abstract base class for parse tree node. // 解析树节点的抽象基类，所有语法树节点都继承此类
   */
  public abstract static class Node { // Node 抽象类，定义所有语法树节点的基本属性和行为
    public final Op op; // 操作类型字段，标识节点的类型（如 PROGRAM、LOAD 等）
    public final SqlParserPos pos; // 解析位置字段，记录节点在源代码中的位置信息（行号、列号等）

    protected Node(SqlParserPos pos, Op op) { // Node 构造方法，初始化解析位置和操作类型
      this.op = requireNonNull(op, "op"); // 设置操作类型，要求不能为空
      this.pos = requireNonNull(pos, "pos"); // 设置解析位置，要求不能为空
    }
  }

  /** Abstract base class for parse tree node representing a statement. // 表示语句的解析树节点抽象基类
   */
  public abstract static class Stmt extends Node { // Stmt 抽象类，继承自 Node，专门用于表示 Pig Latin 语句
    protected Stmt(SqlParserPos pos, Op op) { // Stmt 构造方法，初始化解析位置和操作类型
      super(pos, op); // 调用父类 Node 的构造方法
    }
  }

  /** Abstract base class for statements that assign to a named relation. // 赋值语句的抽象基类，表示将结果赋值给命名关系的语句
   */
  public abstract static class Assignment extends Stmt { // Assignment 抽象类，继承自 Stmt，表示赋值语句（如 a = LOAD ...）
    final Identifier target; // 目标标识符字段，表示赋值的目标变量名（即等号左边的别名）

    protected Assignment(SqlParserPos pos, Op op, Identifier target) { // Assignment 构造方法，初始化位置、操作类型和目标标识符
      super(pos, op); // 调用父类 Stmt 的构造方法
      this.target = requireNonNull(target, "target"); // 设置目标标识符，要求不能为空
    }
  }

  /** Parse tree node for LOAD statement. // LOAD 语句的解析树节点，表示从数据源加载数据
   */
  public static class LoadStmt extends Assignment { // LoadStmt 类，继承自 Assignment，表示 LOAD 语句
    final Literal name; // 数据源名称字段，表示要加载的数据文件或表的名称（字面量形式）

    public LoadStmt(SqlParserPos pos, Identifier target, Literal name) { // LoadStmt 构造方法，初始化位置、目标标识符和数据源名称
      super(pos, Op.LOAD, target); // 调用父类 Assignment 的构造方法，指定操作类型为 LOAD
      this.name = requireNonNull(name, "name"); // 设置数据源名称，要求不能为空
    }
  }

  /** Parse tree node for VALUES statement. // VALUES 语句的解析树节点，表示直接创建包含指定值的元组
   *
   * <p>VALUES is an extension to Pig, inspired by SQL's VALUES clause. // VALUES 是对 Pig 的扩展，灵感来自 SQL 的 VALUES 子句
   */
  public static class ValuesStmt extends Assignment { // ValuesStmt 类，继承自 Assignment，表示 VALUES 语句
    final List<List<Node>> tupleList; // 元组列表字段，表示要插入的多个元组，每个元组是一个节点列表
    final Schema schema; // 模式字段，定义结果关系的模式（列名和类型）

    public ValuesStmt(SqlParserPos pos, Identifier target, Schema schema, // ValuesStmt 构造方法，初始化位置、目标标识符、模式和元组列表
        List<List<Node>> tupleList) {
      super(pos, Op.VALUES, target); // 调用父类 Assignment 的构造方法，指定操作类型为 VALUES
      this.schema = schema; // 设置模式
      this.tupleList = ImmutableList.copyOf(tupleList); // 创建元组列表的不可变副本
    }
  }

  /** Abstract base class for an assignment with one source relation. // 具有一个源关系的赋值语句抽象基类
   */
  public static class Assignment1 extends Assignment { // Assignment1 抽象类，继承自 Assignment，表示只有一个源关系的赋值语句
    final Identifier source; // 源标识符字段，表示数据来源的关系名称（即等号右边的别名）

    protected Assignment1(SqlParserPos pos, Op op, Identifier target, // Assignment1 构造方法，初始化位置、操作类型、目标标识符和源标识符
        Identifier source) {
      super(pos, op, target); // 调用父类 Assignment 的构造方法
      this.source = source; // 设置源标识符
    }
  }

  /** Parse tree node for FOREACH statement (non-nested). // FOREACH 语句（非嵌套）的解析树节点，用于对每个元组应用表达式
   *
   * <p>Syntax: // 语法格式：
   * <blockquote><code>
   * alias = FOREACH alias GENERATE expression [, expression]... // 别名 = FOREACH 别名 GENERATE 表达式 [, 表达式]...
   * [ AS schema ];</code> // [ AS 模式 ];
   * </blockquote>
   *
   * @see org.apache.calcite.piglet.Ast.ForeachNestedStmt // 参见嵌套 FOREACH 语句类
   */
  public static class ForeachStmt extends Assignment1 { // ForeachStmt 类，继承自 Assignment1，表示非嵌套的 FOREACH 语句
    final List<Node> expList; // 表达式列表字段，表示 GENERATE 子句中的表达式列表

    public ForeachStmt(SqlParserPos pos, Identifier target, Identifier source, // ForeachStmt 构造方法，初始化位置、目标、源、表达式列表和模式
        List<Node> expList, Schema schema) {
      super(pos, Op.FOREACH, target, source); // 调用父类 Assignment1 的构造方法，指定操作类型为 FOREACH
      this.expList = expList; // 设置表达式列表
      assert schema == null; // not supported yet // 断言模式为空，因为尚未支持 AS schema 子句
    }
  }

  /** Parse tree node for FOREACH statement (nested). // FOREACH 语句（嵌套）的解析树节点，用于在 FOREACH 内部执行多个嵌套操作
   *
   * <p>Syntax: // 语法格式：
   *
   * <blockquote><code>
   * alias = FOREACH nested_alias { // 别名 = FOREACH 嵌套别名 {
   *   alias = nested_op; [alias = nested_op; ]... // 别名 = 嵌套操作; [别名 = 嵌套操作; ]...
   *   GENERATE expression [, expression]... // GENERATE 表达式 [, 表达式]...
   * };<br>
   * &nbsp; // 空行
   * nested_op ::= DISTINCT, FILTER, LIMIT, ORDER, SAMPLE // 嵌套操作可以是 DISTINCT、FILTER、LIMIT、ORDER、SAMPLE
   * </code>
   * </blockquote>
   *
   * @see org.apache.calcite.piglet.Ast.ForeachStmt // 参见非嵌套 FOREACH 语句类
   */
  public static class ForeachNestedStmt extends Assignment1 { // ForeachNestedStmt 类，继承自 Assignment1，表示嵌套的 FOREACH 语句
    final List<Stmt> nestedStmtList; // 嵌套语句列表字段，表示 FOREACH 块内的嵌套操作语句列表
    final List<Node> expList; // 表达式列表字段，表示 GENERATE 子句中的表达式列表

    public ForeachNestedStmt(SqlParserPos pos, Identifier target, // ForeachNestedStmt 构造方法，初始化位置、目标、源、嵌套语句列表、表达式列表和模式
        Identifier source, List<Stmt> nestedStmtList,
        List<Node> expList, Schema schema) {
      super(pos, Op.FOREACH_NESTED, target, source); // 调用父类 Assignment1 的构造方法，指定操作类型为 FOREACH_NESTED
      this.nestedStmtList = nestedStmtList; // 设置嵌套语句列表
      this.expList = expList; // 设置表达式列表
      assert schema == null; // not supported yet // 断言模式为空，因为尚未支持 AS schema 子句
    }
  }

  /** Parse tree node for FILTER statement. // FILTER 语句的解析树节点，用于根据条件过滤数据
   *
   * <p>Syntax: // 语法格式：
   * <blockquote><pre>alias = FILTER alias BY expression;</pre></blockquote> // 别名 = FILTER 别名 BY 表达式;
   */
  public static class FilterStmt extends Assignment1 { // FilterStmt 类，继承自 Assignment1，表示 FILTER 语句
    final Node condition; // 条件节点字段，表示过滤条件的表达式（布尔表达式）

    public FilterStmt(SqlParserPos pos, Identifier target, // FilterStmt 构造方法，初始化位置、目标、源和条件
        Identifier source, Node condition) {
      super(pos, Op.FILTER, target, source); // 调用父类 Assignment1 的构造方法，指定操作类型为 FILTER
      this.condition = condition; // 设置过滤条件
    }
  }

  /** Parse tree node for DISTINCT statement. // DISTINCT 语句的解析树节点，用于去除重复行
   *
   * <p>Syntax: // 语法格式：
   * <blockquote><pre>alias = DISTINCT alias;</pre></blockquote> // 别名 = DISTINCT 别名;
   */
  public static class DistinctStmt extends Assignment1 { // DistinctStmt 类，继承自 Assignment1，表示 DISTINCT 语句
    public DistinctStmt(SqlParserPos pos, Identifier target, // DistinctStmt 构造方法，初始化位置、目标和源
        Identifier source) {
      super(pos, Op.DISTINCT, target, source); // 调用父类 Assignment1 的构造方法，指定操作类型为 DISTINCT
    }
  }

  /** Parse tree node for LIMIT statement. // LIMIT 语句的解析树节点，用于限制输出行数
   *
   * <p>Syntax: // 语法格式：
   * <blockquote><pre>alias = LIMIT alias n;</pre></blockquote> // 别名 = LIMIT 别名 n;
   */
  public static class LimitStmt extends Assignment1 { // LimitStmt 类，继承自 Assignment1，表示 LIMIT 语句
    final Literal count; // 计数字段，表示要限制的行数（字面量形式）

    public LimitStmt(SqlParserPos pos, Identifier target, // LimitStmt 构造方法，初始化位置、目标、源和计数
        Identifier source, Literal count) {
      super(pos, Op.LIMIT, target, source); // 调用父类 Assignment1 的构造方法，指定操作类型为 LIMIT
      this.count = count; // 设置计数值
    }
  }

  /** Parse tree node for ORDER statement. // ORDER 语句的解析树节点，用于对数据进行排序
   *
   * <p>Syntax: // 语法格式：
   * <blockquote>
   *   <code>alias = ORDER alias BY (* | field) [ASC | DESC] // 别名 = ORDER 别名 BY (* | 字段) [ASC | DESC]
   *     [, field [ASC | DESC] ]...;</code> // [, 字段 [ASC | DESC] ]...;
   * </blockquote>
   */
  public static class OrderStmt extends Assignment1 { // OrderStmt 类，继承自 Assignment1，表示 ORDER 语句
    final List<Pair<Identifier, Direction>> fields; // 字段列表字段，表示排序字段及其排序方向的列表

    public OrderStmt(SqlParserPos pos, Identifier target, // OrderStmt 构造方法，初始化位置、目标、源和字段列表
        Identifier source, List<Pair<Identifier, Direction>> fields) {
      super(pos, Op.ORDER, target, source); // 调用父类 Assignment1 的构造方法，指定操作类型为 ORDER
      this.fields = fields; // 设置排序字段列表
    }
  }

  /** Parse tree node for GROUP statement. // GROUP 语句的解析树节点，用于对数据进行分组
   *
   * <p>Syntax: // 语法格式：
   * <blockquote>
   *   <code>alias = GROUP alias // 别名 = GROUP 别名
   *   ( ALL | BY ( exp | '(' exp [, exp]... ')' ) )</code> // ( ALL | BY ( 表达式 | '(' 表达式 [, 表达式]... ')' ) )
   * </blockquote>
   */
  public static class GroupStmt extends Assignment1 { // GroupStmt 类，继承自 Assignment1，表示 GROUP 语句
    /** Grouping keys. May be null (for ALL), or a list of one or more // 分组键字段，可能为 null（表示 ALL），或者是一个或多个表达式的列表
     * expressions. */
    final List<Node> keys; // 分组键列表，用于指定分组的字段或表达式

    public GroupStmt(SqlParserPos pos, Identifier target, // GroupStmt 构造方法，初始化位置、目标、源和分组键
        Identifier source, List<Node> keys) {
      super(pos, Op.GROUP, target, source); // 调用父类 Assignment1 的构造方法，指定操作类型为 GROUP
      this.keys = keys; // 设置分组键列表
      assert keys == null || keys.size() >= 1; // 断言分组键为空或者至少有一个元素
    }
  }

  /** Parse tree node for DUMP statement. // DUMP 语句的解析树节点，用于输出关系的内容到标准输出
   */
  public static class DumpStmt extends Stmt { // DumpStmt 类，继承自 Stmt，表示 DUMP 语句
    final Identifier relation; // 关系标识符字段，表示要输出的关系名称

    public DumpStmt(SqlParserPos pos, Identifier relation) { // DumpStmt 构造方法，初始化位置和关系标识符
      super(pos, Op.DUMP); // 调用父类 Stmt 的构造方法，指定操作类型为 DUMP
      this.relation = requireNonNull(relation, "relation"); // 设置关系标识符，要求不能为空
    }
  }

  /** Parse tree node for DESCRIBE statement. // DESCRIBE 语句的解析树节点，用于描述关系的模式（schema）
   */
  public static class DescribeStmt extends Stmt { // DescribeStmt 类，继承自 Stmt，表示 DESCRIBE 语句
    final Identifier relation; // 关系标识符字段，表示要描述的关系名称

    public DescribeStmt(SqlParserPos pos, Identifier relation) { // DescribeStmt 构造方法，初始化位置和关系标识符
      super(pos, Op.DESCRIBE); // 调用父类 Stmt 的构造方法，指定操作类型为 DESCRIBE
      this.relation = requireNonNull(relation, "relation"); // 设置关系标识符，要求不能为空
    }
  }

  /** Parse tree node for Literal. // 字面量的解析树节点，表示常量值（如数字、字符串等）
   */
  public static class Literal extends Node { // Literal 类，继承自 Node，表示字面量节点
    final Object value; // 值字段，表示字面量的实际值（可以是 String、Integer、BigDecimal 等）

    public Literal(SqlParserPos pos, Object value) { // Literal 构造方法，初始化位置和值
      super(pos, Op.LITERAL); // 调用父类 Node 的构造方法，指定操作类型为 LITERAL
      this.value = requireNonNull(value, "value"); // 设置值，要求不能为空
    }

    public static NumericLiteral createExactNumeric(String s, // 静态工厂方法，从字符串创建精确数值字面量
        SqlParserPos pos) {
      BigDecimal value; // BigDecimal 对象，用于存储解析后的数值
      int prec; // 精度字段，表示数值的总位数
      int scale; // 标度字段，表示小数点后的位数

      int i = s.indexOf('.'); // 查找小数点的位置
      if ((i >= 0) && ((s.length() - 1) != i)) { // 如果有小数点且小数点不是最后一个字符（即有小数部分）
        value = SqlParserUtil.parseDecimal(s); // 解析为十进制数
        scale = s.length() - i - 1; // 计算小数位数（总长度 - 小数点位置 - 1）
        assert scale == value.scale() : s; // 断言计算的小数位数与解析结果一致
        prec = s.length() - 1; // 计算精度（总长度 - 1，因为小数点不算一位）
      } else if ((i >= 0) && ((s.length() - 1) == i)) { // 如果有小数点且小数点是最后一个字符（如 "123."）
        value = SqlParserUtil.parseInteger(s.substring(0, i)); // 解析整数部分
        scale = 0; // 小数位数为 0
        prec = s.length() - 1; // 精度为总长度减 1
      } else { // 如果没有小数点（纯整数）
        value = SqlParserUtil.parseInteger(s); // 解析为整数
        scale = 0; // 小数位数为 0
        prec = s.length(); // 精度为总长度
      }
      return new NumericLiteral(pos, value, prec, scale, true); // 创建并返回 NumericLiteral 对象，标记为精确值
    }

  }

  /** Parse tree node for NumericLiteral. // 数值字面量的解析树节点，表示数字类型的字面量
   */
  public static class NumericLiteral extends Literal { // NumericLiteral 类，继承自 Literal，专门用于数值字面量
    final int prec; // 精度字段，表示数值的总位数（包括整数和小数部分）
    final int scale; // 标度字段，表示小数点后的位数
    final boolean exact; // 精确标志字段，表示该数值是否为精确值（非近似值）

    NumericLiteral(SqlParserPos pos, BigDecimal value, int prec, int scale, // NumericLiteral 构造方法，初始化位置、值、精度、标度和精确标志
        boolean exact) {
      super(pos, value); // 调用父类 Literal 的构造方法
      this.prec = prec; // 设置精度
      this.scale = scale; // 设置标度
      this.exact = exact; // 设置精确标志
    }

    public NumericLiteral negate(SqlParserPos pos) { // 取反方法，返回当前数值的相反数
      BigDecimal value = (BigDecimal) this.value; // 获取当前值并转换为 BigDecimal
      return new NumericLiteral(pos, value.negate(), prec, scale, exact); // 创建并返回新的 NumericLiteral，值为原值的相反数
    }
  }

  /** Parse tree node for Identifier. // 标识符的解析树节点，表示变量名、字段名、表名等标识符
   */
  public static class Identifier extends Node { // Identifier 类，继承自 Node，表示标识符节点
    final String value; // 值字段，表示标识符的字符串值

    public Identifier(SqlParserPos pos, String value) { // Identifier 构造方法，初始化位置和值
      super(pos, Op.IDENTIFIER); // 调用父类 Node 的构造方法，指定操作类型为 IDENTIFIER
      this.value = requireNonNull(value, "value"); // 设置标识符值，要求不能为空
    }

    public boolean isStar() { // 判断是否为星号标识符的方法
      return false; // 默认返回 false，表示不是星号
    }
  }

  /** Parse tree node for "*", a special kind of identifier. // 星号标识符的解析树节点，表示通配符（如 SELECT *）
   */
  public static class SpecialIdentifier extends Identifier { // SpecialIdentifier 类，继承自 Identifier，表示特殊的标识符（如 *）
    public SpecialIdentifier(SqlParserPos pos) { // SpecialIdentifier 构造方法，初始化位置
      super(pos, "*"); // 调用父类构造方法，设置值为 "*"
    }

    @Override public boolean isStar() { // 重写 isStar 方法
      return true; // 返回 true，表示这是星号标识符
    }
  }

  /** Parse tree node for a call to a function or operator. // 函数或运算符调用的解析树节点
   */
  public static class Call extends Node { // Call 类，继承自 Node，表示函数调用或运算符表达式
    final ImmutableList<Node> operands; // 操作数列表字段，表示函数或运算符的参数列表（不可变列表）

    private Call(SqlParserPos pos, Op op, ImmutableList<Node> operands) { // Call 私有构造方法，初始化位置、操作类型和操作数列表
      super(pos, op); // 调用父类 Node 的构造方法
      this.operands = ImmutableList.copyOf(operands); // 创建操作数列表的不可变副本
    }

    public Call(SqlParserPos pos, Op op, Iterable<? extends Node> operands) { // Call 公共构造方法，接受可迭代的操作数
      this(pos, op, ImmutableList.copyOf(operands)); // 调用私有构造方法
    }

    public Call(SqlParserPos pos, Op op, Node... operands) { // Call 公共构造方法，接受可变参数的操作数
      this(pos, op, ImmutableList.copyOf(operands)); // 调用私有构造方法
    }
  }

  /** Parse tree node for a program. // 程序的解析树节点，表示完整的 Pig Latin 脚本
   */
  public static class Program extends Node { // Program 类，继承自 Node，表示完整的程序（由多个语句组成）
    public final List<Stmt> stmtList; // 语句列表字段，表示程序中的所有语句

    public Program(SqlParserPos pos, List<Stmt> stmtList) { // Program 构造方法，初始化位置和语句列表
      super(pos, Op.PROGRAM); // 调用父类 Node 的构造方法，指定操作类型为 PROGRAM
      this.stmtList = stmtList; // 设置语句列表
    }
  }

  /** Parse tree for field schema. // 字段模式的解析树节点，表示单个字段的定义
   *
   * <p>Syntax: // 语法格式：
   * <blockquote><pre>identifier:type</pre></blockquote> // 标识符:类型
   */
  public static class FieldSchema extends Node { // FieldSchema 类，继承自 Node，表示字段的模式定义
    final Identifier id; // 标识字段，表示字段的名称
    final Type type; // 类型字段，表示字段的数据类型

    public FieldSchema(SqlParserPos pos, Identifier id, Type type) { // FieldSchema 构造方法，初始化位置、标识符和类型
      super(pos, Op.FIELD_SCHEMA); // 调用父类 Node 的构造方法，指定操作类型为 FIELD_SCHEMA
      this.id = requireNonNull(id, "id"); // 设置字段标识符，要求不能为空
      this.type = requireNonNull(type, "type"); // 设置字段类型，要求不能为空
    }
  }

  /** Parse tree for schema. // 模式的解析树节点，表示关系的数据结构定义（包含多个字段）
   *
   * <p>Syntax: // 语法格式：
   * <blockquote>
   *   <pre>AS ( identifier:type [, identifier:type]... )</pre> // AS ( 标识符:类型 [, 标识符:类型]... )
   * </blockquote>
   */
  public static class Schema extends Node { // Schema 类，继承自 Node，表示完整的模式定义
    final List<FieldSchema> fieldSchemaList; // 字段模式列表字段，表示模式中所有字段的定义

    public Schema(SqlParserPos pos, List<FieldSchema> fieldSchemaList) { // Schema 构造方法，初始化位置和字段模式列表
      super(pos, Op.SCHEMA); // 调用父类 Node 的构造方法，指定操作类型为 SCHEMA
      this.fieldSchemaList = ImmutableList.copyOf(fieldSchemaList); // 创建字段模式列表的不可变副本
    }
  }

  /** Parse tree for type. // 类型的解析树节点抽象基类，表示各种数据类型
   */
  public abstract static class Type extends Node { // Type 抽象类，继承自 Node，表示数据类型节点
    protected Type(SqlParserPos pos, Op op) { // Type 构造方法，初始化位置和操作类型
      super(pos, op); // 调用父类 Node 的构造方法
    }
  }

  /** Parse tree for scalar type such as {@code int}. // 标量类型的解析树节点，表示基本数据类型（如 int、chararray 等）
   */
  public static class ScalarType extends Type { // ScalarType 类，继承自 Type，表示标量类型
    final String name; // 名称字段，表示类型的名称（如 "int"、"chararray" 等）

    public ScalarType(SqlParserPos pos, String name) { // ScalarType 构造方法，初始化位置和类型名称
      super(pos, Op.SCALAR_TYPE); // 调用父类 Type 的构造方法，指定操作类型为 SCALAR_TYPE
      this.name = name; // 设置类型名称
    }
  }

  /** Parse tree for a bag type. // 包类型的解析树节点，表示包（Bag）类型，即元组的集合
   */
  public static class BagType extends Type { // BagType 类，继承自 Type，表示包类型
    final Type componentType; // 组件类型字段，表示包中元素（元组）的类型

    public BagType(SqlParserPos pos, Type componentType) { // BagType 构造方法，初始化位置和组件类型
      super(pos, Op.BAG_TYPE); // 调用父类 Type 的构造方法，指定操作类型为 BAG_TYPE
      this.componentType = componentType; // 设置组件类型（通常是 TupleType）
    }
  }

  /** Parse tree for a tuple type. // 元组类型的解析树节点，表示元组类型，即有序的值集合
   */
  public static class TupleType extends Type { // TupleType 类，继承自 Type，表示元组类型
    final List<FieldSchema> fieldSchemaList; // 字段模式列表字段，表示元组中所有字段的定义

    public TupleType(SqlParserPos pos, List<FieldSchema> fieldSchemaList) { // TupleType 构造方法，初始化位置和字段模式列表
      super(pos, Op.TUPLE_TYPE); // 调用父类 Type 的构造方法，指定操作类型为 TUPLE_TYPE
      this.fieldSchemaList = ImmutableList.copyOf(fieldSchemaList); // 创建字段模式列表的不可变副本
    }
  }

  /** Parse tree for a map type. // 映射类型的解析树节点，表示键值对映射类型
   */
  public static class MapType extends Type { // MapType 类，继承自 Type，表示映射类型
    final Type keyType; // 键类型字段，表示映射中键的数据类型
    final Type valueType; // 值类型字段，表示映射中值的数据类型

    public MapType(SqlParserPos pos) { // MapType 构造方法，初始化位置
      super(pos, Op.MAP_TYPE); // 调用父类 Type 的构造方法，指定操作类型为 MAP_TYPE
      // REVIEW: Why does Pig's "map" type not have key and value types? // 审查：为什么 Pig 的 "map" 类型没有键和值类型？
      this.keyType = new ScalarType(pos, "int"); // 设置键类型为 int（默认值，Pig 的 map 类型实际上是 chararray 到任意类型的映射）
      this.valueType = new ScalarType(pos, "int"); // 设置值类型为 int（默认值，实际使用时可能需要调整）
    }
  }

  /** Contains output and indentation level while a tree of nodes is // UnParser 类，用于将语法树节点转换为文本字符串，同时管理输出缓冲区和缩进级别
   * being converted to text. */
  static class UnParser { // UnParser 类，提供将语法树节点格式化为字符串的功能
    final StringBuilder buf = new StringBuilder(); // 字符串构建器字段，用于存储格式化后的输出内容
    final Spacer spacer = new Spacer(0); // 缩进工具字段，用于管理当前的缩进级别（初始为 0）

    public UnParser in() { // 增加缩进级别的方法
      spacer.add(2); // 将缩进级别增加 2 个空格
      return this; // 返回 this 以支持链式调用
    }

    public UnParser out() { // 减少缩进级别的方法
      spacer.subtract(2); // 将缩进级别减少 2 个空格
      return this; // 返回 this 以支持链式调用
    }

    public UnParser newline() { // 添加新行并应用当前缩进的方法
      buf.append(Util.LINE_SEPARATOR); // 追加系统换行符
      spacer.spaces(buf); // 追加当前缩进级别的空格
      return this; // 返回 this 以支持链式调用
    }

    public UnParser append(String s) { // 追加字符串到缓冲区的方法
      buf.append(s); // 将字符串追加到缓冲区
      return this; // 返回 this 以支持链式调用
    }

    public UnParser append(Node n) { // 追加节点到缓冲区的方法
      return unParse(this, n); // 调用 unParse 静态方法将节点转换为字符串并追加
    }

    public UnParser appendList(List<? extends Node> list) { // 追加节点列表到缓冲区的方法（格式化为数组形式）
      append("[").in(); // 追加左方括号并增加缩进
      for (Ord<Node> n : Ord.<Node>zip(list)) { // 遍历节点列表，Ord 为每个元素添加索引
        newline().append(n.e); // 添加新行并追加节点元素
        if (n.i < list.size() - 1) { // 如果不是最后一个元素
          append(","); // 追加逗号分隔符
        }
      }
      return out().append("]"); // 减少缩进并追加右方括号，返回 this
    }
  }

  /** Sort direction. // 排序方向枚举，用于 ORDER 语句中指定排序方向
   */
  public enum Direction { // Direction 枚举类，定义排序方向的三种可能值
    ASC, // 升序（Ascending）
    DESC, // 降序（Descending）
    NOT_SPECIFIED // 未指定（Not Specified）
  }
}
