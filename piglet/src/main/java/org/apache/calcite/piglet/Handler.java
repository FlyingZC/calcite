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
// Apache许可证声明，说明代码的版权和使用条款
package org.apache.calcite.piglet; // 定义包名，表示这个类属于org.apache.calcite.piglet包

// 导入Calcite框架中的关系代数相关类
import org.apache.calcite.rel.RelNode; // 导入RelNode类，代表关系代数节点，是关系表达式的基本单元
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，代表关系数据类型，描述行或字段的类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory类，用于创建关系数据类型的工厂类
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField类，代表关系数据类型中的字段
// 导入Calcite框架中的Rex表达式相关类
import org.apache.calcite.rex.RexBuilder; // 导入RexBuilder类，用于构建行表达式(RexNode)的构建器
import org.apache.calcite.rex.RexLiteral; // 导入RexLiteral类，代表行表达式中的字面量常量
import org.apache.calcite.rex.RexNode; // 导入RexNode类，代表行表达式(Row Expression)，用于描述表达式
// 导入Calcite框架中的SQL操作符和类型相关类
import org.apache.calcite.sql.SqlOperator; // 导入SqlOperator类，代表SQL操作符的基类
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SqlStdOperatorTable类，包含标准SQL操作符的表
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName类，枚举了所有SQL类型名称
// 导入Calcite框架中的构建器相关类
import org.apache.calcite.tools.PigRelBuilder; // 导入PigRelBuilder类，专门用于构建Pig关系代数的构建器
import org.apache.calcite.tools.RelBuilder; // 导入RelBuilder类，通用关系代数构建器的基类
import org.apache.calcite.util.Pair; // 导入Pair类，用于存储键值对的工具类

// 导入Google Guava库中的不可变集合类
import com.google.common.collect.ImmutableList; // 导入ImmutableList类，提供不可变的列表实现

// 导入Java标准库中的集合和列表类
import java.util.ArrayList; // 导入ArrayList类，可变数组的列表实现
import java.util.HashMap; // 导入HashMap类，基于哈希表的Map实现
import java.util.List; // 导入List接口，表示有序集合
import java.util.Map; // 导入Map接口，表示键值对映射

/**
 * Walks over a Piglet AST and calls the corresponding methods in a
 * {@link PigRelBuilder}.
 * 遍历Piglet抽象语法树(AST)并调用PigRelBuilder中相应的方法
 * 这个类是Piglet语言的处理器，负责将Piglet脚本转换为Calcite的关系代数表达式
 * Piglet是一个简化的类Pig Latin语言，用于数据流处理
 */
public class Handler { // 定义Handler类，处理Piglet AST并构建关系代数表达式
  private final PigRelBuilder builder; // 成员变量：PigRelBuilder实例，用于构建Pig关系代数表达式，final表示不可变
  private final Map<String, RelNode> map = new HashMap<>(); // 成员变量：Map映射，存储关系名称到RelNode的映射，用于跟踪各个关系表达式，final表示引用不可变

  public Handler(PigRelBuilder builder) { // 构造方法：创建Handler实例，传入PigRelBuilder构建器
    this.builder = builder; // 将传入的PigRelBuilder赋值给成员变量builder，初始化关系构建器
  }

  /** Creates relational expressions for a given AST node.
   * 为给定的AST节点创建关系表达式
   * 这是Handler类的核心方法，根据AST节点的操作类型分发到不同的处理逻辑
   * 支持LOAD、VALUES、FOREACH、FILTER、DISTINCT、ORDER、LIMIT、GROUP等多种操作
   * @param node AST节点，包含操作类型和相关参数
   * @return 返回Handler实例本身，支持链式调用
   */
  public Handler handle(Ast.Node node) { // 方法入口：处理AST节点并构建对应的关系表达式
    final RelNode input; // 声明输入关系节点变量，用于存储从map中获取的源关系表达式
    final List<RexNode> rexNodes; // 声明行表达式列表变量，用于存储转换后的RexNode表达式列表
    switch (node.op) { // 使用switch语句根据AST节点的操作类型进行分发处理
    case LOAD: // 处理LOAD操作：从数据源加载数据
      final Ast.LoadStmt load = (Ast.LoadStmt) node; // 将node强制转换为LoadStmt类型，获取加载语句的详细信息
      builder.scan((String) load.name.value); // 调用builder的scan方法，扫描指定名称的数据源（如文件或表）
      register(load.target.value); // 将生成的RelNode注册到map中，使用目标关系名称作为键
      return this; // 返回当前Handler实例，支持链式调用
    case VALUES: // 处理VALUES操作：创建内联数据值
      final Ast.ValuesStmt values = (Ast.ValuesStmt) node; // 将node强制转换为ValuesStmt类型，获取值语句的详细信息
      final RelDataType rowType = toType(values.schema); // 调用toType方法，将AST的schema转换为RelDataType关系数据类型
      builder.values(tuples(values, rowType), rowType); // 调用builder的values方法，创建包含指定元组数据的关系表达式
      register(values.target.value); // 将生成的RelNode注册到map中，使用目标关系名称作为键
      return this; // 返回当前Handler实例，支持链式调用
    case FOREACH: // 处理FOREACH操作：对每个元组应用投影表达式
      final Ast.ForeachStmt foreach = (Ast.ForeachStmt) node; // 将node强制转换为ForeachStmt类型，获取foreach语句的详细信息
      builder.clear(); // 清空builder的栈，准备构建新的关系表达式
      input = map.get(foreach.source.value); // 从map中获取源关系表达式，使用源关系名称作为键
      builder.push(input); // 将输入关系表达式压入builder的栈中
      rexNodes = new ArrayList<>(); // 创建新的ArrayList用于存储RexNode表达式列表
      for (Ast.Node exp : foreach.expList) { // 遍历foreach语句中的表达式列表
        rexNodes.add(toRex(exp)); // 将每个AST表达式转换为RexNode并添加到列表中
      }
      builder.project(rexNodes); // 调用builder的project方法，创建投影操作，只保留指定的表达式
      register(foreach.target.value); // 将生成的RelNode注册到map中，使用目标关系名称作为键
      return this; // 返回当前Handler实例，支持链式调用
    case FOREACH_NESTED: // 处理FOREACH_NESTED操作：嵌套foreach，用于处理嵌套数据结构
      final Ast.ForeachNestedStmt foreachNested = (Ast.ForeachNestedStmt) node; // 将node强制转换为ForeachNestedStmt类型
      builder.clear(); // 清空builder的栈，准备构建新的关系表达式
      input = map.get(foreachNested.source.value); // 从map中获取源关系表达式
      builder.push(input); // 将输入关系表达式压入builder的栈中
      System.out.println(input.getRowType()); // 调试输出：打印输入关系的行类型信息
      for (RelDataTypeField field : input.getRowType().getFieldList()) { // 遍历输入关系的所有字段
        switch (field.getType().getSqlTypeName()) { // 根据字段的SQL类型进行判断
        case ARRAY: // 如果字段类型是数组
          System.out.println(field); // 调试输出：打印该字段信息
          break; // 跳出switch语句
        default: // 其他类型
          break; // 不做任何处理
        }
      }
      for (Ast.Stmt stmt : foreachNested.nestedStmtList) { // 遍历嵌套语句列表
        handle(stmt); // 递归调用handle方法处理每个嵌套语句
      }
      rexNodes = new ArrayList<>(); // 创建新的ArrayList用于存储RexNode表达式列表
      for (Ast.Node exp : foreachNested.expList) { // 遍历foreach嵌套语句中的表达式列表
        rexNodes.add(toRex(exp)); // 将每个AST表达式转换为RexNode并添加到列表中
      }
      builder.project(rexNodes); // 调用builder的project方法，创建投影操作
      register(foreachNested.target.value); // 将生成的RelNode注册到map中，使用目标关系名称作为键
      return this; // 返回当前Handler实例，支持链式调用
    case FILTER: // 处理FILTER操作：过滤数据
      final Ast.FilterStmt filter = (Ast.FilterStmt) node; // 将node强制转换为FilterStmt类型，获取过滤语句的详细信息
      builder.clear(); // 清空builder的栈，准备构建新的关系表达式
      input = map.get(filter.source.value); // 从map中获取源关系表达式
      builder.push(input); // 将输入关系表达式压入builder的栈中
      final RexNode rexNode = toRex(filter.condition); // 将过滤条件转换为RexNode表达式
      builder.filter(rexNode); // 调用builder的filter方法，创建过滤操作，只保留满足条件的行
      register(filter.target.value); // 将生成的RelNode注册到map中，使用目标关系名称作为键
      return this; // 返回当前Handler实例，支持链式调用
    case DISTINCT: // 处理DISTINCT操作：去重
      final Ast.DistinctStmt distinct = (Ast.DistinctStmt) node; // 将node强制转换为DistinctStmt类型，获取去重语句的详细信息
      builder.clear(); // 清空builder的栈，准备构建新的关系表达式
      input = map.get(distinct.source.value); // 从map中获取源关系表达式
      builder.push(input); // 将输入关系表达式压入builder的栈中
      builder.distinct(null, -1); // 调用builder的distinct方法，创建去重操作，null表示所有字段都参与去重，-1表示无限制
      register(distinct.target.value); // 将生成的RelNode注册到map中，使用目标关系名称作为键
      return this; // 返回当前Handler实例，支持链式调用
    case ORDER: // 处理ORDER操作：排序
      final Ast.OrderStmt order = (Ast.OrderStmt) node; // 将node强制转换为OrderStmt类型，获取排序语句的详细信息
      builder.clear(); // 清空builder的栈，准备构建新的关系表达式
      input = map.get(order.source.value); // 从map中获取源关系表达式
      builder.push(input); // 将输入关系表达式压入builder的栈中
      final List<RexNode> nodes = new ArrayList<>(); // 创建新的ArrayList用于存储排序表达式列表
      for (Pair<Ast.Identifier, Ast.Direction> field : order.fields) { // 遍历排序字段列表，每个字段包含标识符和排序方向
        toSortRex(nodes, field); // 调用toSortRex方法，将字段转换为排序RexNode并添加到列表中
      }
      builder.sort(nodes); // 调用builder的sort方法，创建排序操作，按照指定的字段和方向排序
      register(order.target.value); // 将生成的RelNode注册到map中，使用目标关系名称作为键
      return this; // 返回当前Handler实例，支持链式调用
    case LIMIT: // 处理LIMIT操作：限制结果行数
      final Ast.LimitStmt limit = (Ast.LimitStmt) node; // 将node强制转换为LimitStmt类型，获取限制语句的详细信息
      builder.clear(); // 清空builder的栈，准备构建新的关系表达式
      input = map.get(limit.source.value); // 从map中获取源关系表达式
      final int count = ((Number) limit.count.value).intValue(); // 获取限制的行数，将值转换为整数
      builder.push(input); // 将输入关系表达式压入builder的栈中
      builder.limit(0, count); // 调用builder的limit方法，创建限制操作，从第0行开始，限制返回count行
      register(limit.target.value); // 将生成的RelNode注册到map中，使用目标关系名称作为键
      return this; // 返回当前Handler实例，支持链式调用
    case GROUP: // 处理GROUP操作：分组
      final Ast.GroupStmt group = (Ast.GroupStmt) node; // 将node强制转换为GroupStmt类型，获取分组语句的详细信息
      builder.clear(); // 清空builder的栈，准备构建新的关系表达式
      input = map.get(group.source.value); // 从map中获取源关系表达式
      builder.push(input).as(group.source.value); // 将输入关系表达式压入builder的栈中，并为其设置别名
      final List<RelBuilder.GroupKey> groupKeys = new ArrayList<>(); // 创建新的ArrayList用于存储分组键列表
      final List<RexNode> keys = new ArrayList<>(); // 创建新的ArrayList用于存储分组表达式列表
      if (group.keys != null) { // 如果分组键不为空
        for (Ast.Node key : group.keys) { // 遍历分组键列表
          keys.add(toRex(key)); // 将每个分组键转换为RexNode并添加到列表中
        }
      }
      groupKeys.add(builder.groupKey(keys)); // 调用builder的groupKey方法，创建分组键并添加到groupKeys列表中
      builder.group(PigRelBuilder.GroupOption.COLLECTED, null, -1, groupKeys); // 调用builder的group方法，创建分组操作，使用COLLECTED选项收集分组结果
      register(group.target.value); // 将生成的RelNode注册到map中，使用目标关系名称作为键
      return this; // 返回当前Handler实例，支持链式调用
    case PROGRAM: // 处理PROGRAM操作：程序入口，包含多个语句
      final Ast.Program program = (Ast.Program) node; // 将node强制转换为Program类型，获取程序的详细信息
      for (Ast.Stmt stmt : program.stmtList) { // 遍历程序中的所有语句
        handle(stmt); // 递归调用handle方法处理每个语句
      }
      return this; // 返回当前Handler实例，支持链式调用
    case DUMP: // 处理DUMP操作：输出关系表达式的结果
      final Ast.DumpStmt dump = (Ast.DumpStmt) node; // 将node强制转换为DumpStmt类型，获取dump语句的详细信息
      final RelNode relNode = map.get(dump.relation.value); // 从map中获取要输出的关系表达式
      dump(relNode); // 调用dump方法，执行关系表达式并输出结果
      return this; // 返回当前Handler实例，支持链式调用，dump操作不包含代数运算
    default: // 默认情况：未知的操作类型
      throw new AssertionError("unknown operation " + node.op); // 抛出断言错误，表示遇到了未知的操作类型
    }
  }

  /** Executes a relational expression and prints the output.
   * 执行关系表达式并打印输出结果
   * 这个方法用于执行关系代数表达式并输出结果，默认实现不执行任何操作
   * 子类可以重写此方法以提供实际的执行和输出逻辑
   *
   * <p>The default implementation does nothing.
   * 默认实现不执行任何操作
   *
   * @param rel Relational expression，要执行的关系表达式
   */
  protected void dump(RelNode rel) { // 方法定义：执行关系表达式并输出结果，protected表示子类可以访问
    // 默认实现为空，不做任何操作
  }

  private ImmutableList<ImmutableList<RexLiteral>> tuples( // 方法定义：将VALUES语句转换为RexLiteral元组列表的列表（即二维列表）
      Ast.ValuesStmt valuesStmt, RelDataType rowType) { // 参数：valuesStmt是VALUES语句，rowType是行类型
    final ImmutableList.Builder<ImmutableList<RexLiteral>> listBuilder = // 创建不可变列表的构建器，用于构建二维的RexLiteral列表
        ImmutableList.builder(); // 调用ImmutableList.builder()方法创建构建器实例
    for (List<Ast.Node> nodeList : valuesStmt.tupleList) { // 遍历VALUES语句中的元组列表，每个元组是一个AST节点列表
      listBuilder.add(tuple(nodeList, rowType)); // 调用tuple方法将每个元组转换为RexLiteral列表，并添加到构建器中
    }
    return listBuilder.build(); // 调用build方法构建不可变的二维RexLiteral列表并返回
  }

  private ImmutableList<RexLiteral> tuple(List<Ast.Node> nodeList, // 方法定义：将AST节点列表转换为RexLiteral列表（即一维列表）
      RelDataType rowType) { // 参数：nodeList是AST节点列表，rowType是行类型
    final ImmutableList.Builder<RexLiteral> listBuilder = // 创建不可变列表的构建器，用于构建RexLiteral列表
        ImmutableList.builder(); // 调用ImmutableList.builder()方法创建构建器实例
    for (Pair<Ast.Node, RelDataTypeField> pair // 遍历AST节点列表和字段列表的配对
        : Pair.zip(nodeList, rowType.getFieldList())) { // 使用Pair.zip方法将节点列表和字段列表配对
      final Ast.Node node = pair.left; // 从配对中获取AST节点（左值）
      final RelDataType type = pair.right.getType(); // 从配对中获取字段类型（右值的类型）
      listBuilder.add(item(node, type)); // 调用item方法将AST节点转换为RexLiteral，并添加到构建器中
    }
    return listBuilder.build(); // 调用build方法构建不可变的RexLiteral列表并返回
  }

  private ImmutableList<RexLiteral> bag(List<Ast.Node> nodeList, // 方法定义：将AST节点列表转换为RexLiteral列表（用于BAG类型）
      RelDataType type) { // 参数：nodeList是AST节点列表，type是BAG类型
    final ImmutableList.Builder<RexLiteral> listBuilder = // 创建不可变列表的构建器，用于构建RexLiteral列表
        ImmutableList.builder(); // 调用ImmutableList.builder()方法创建构建器实例
    for (Ast.Node node : nodeList) { // 遍历AST节点列表
      listBuilder.add(item(node, type.getComponentType())); // 调用item方法将AST节点转换为RexLiteral，使用BAG的元素类型，并添加到构建器中
    }
    return listBuilder.build(); // 调用build方法构建不可变的RexLiteral列表并返回
  }

  private RexLiteral item(Ast.Node node, RelDataType type) { // 方法定义：将AST节点转换为RexLiteral字面量
    final RexBuilder rexBuilder = builder.getRexBuilder(); // 获取RexBuilder实例，用于构建RexLiteral
    switch (node.op) { // 根据AST节点的操作类型进行分发处理
    case LITERAL: // 处理字面量类型
      final Ast.Literal literal = (Ast.Literal) node; // 将node强制转换为Literal类型，获取字面量的详细信息
      return rexBuilder.makeLiteral(literal.value, type); // 调用RexBuilder的makeLiteral方法，创建字面量RexLiteral
    case TUPLE: // 处理元组类型
      final Ast.Call tuple = (Ast.Call) node; // 将node强制转换为Call类型，获取元组的详细信息
      final ImmutableList<RexLiteral> list = tuple(tuple.operands, type); // 调用tuple方法将操作数转换为RexLiteral列表
      return rexBuilder.makeLiteral(list, type); // 调用RexBuilder的makeLiteral方法，创建元组RexLiteral
    case BAG: // 处理BAG类型（集合）
      final Ast.Call bag = (Ast.Call) node; // 将node强制转换为Call类型，获取BAG的详细信息
      final ImmutableList<RexLiteral> list2 = bag(bag.operands, type); // 调用bag方法将操作数转换为RexLiteral列表
      return rexBuilder.makeLiteral(list2, type); // 调用RexBuilder的makeLiteral方法，创建BAG RexLiteral
    default: // 默认情况：不是字面量
      throw new IllegalArgumentException("not a literal: " + node); // 抛出非法参数异常，表示节点不是字面量
    }
  }


  private RelDataType toType(Ast.Schema schema) { // 方法定义：将AST的Schema转换为RelDataType关系数据类型
    final RelDataTypeFactory.Builder typeBuilder = // 创建关系数据类型工厂的构建器
        builder.getTypeFactory().builder(); // 获取类型工厂并创建构建器实例
    for (Ast.FieldSchema fieldSchema : schema.fieldSchemaList) { // 遍历Schema中的字段列表
      typeBuilder.add(fieldSchema.id.value, toType(fieldSchema.type)); // 调用toType方法将字段类型转换为RelDataType，并添加到构建器中
    }
    return typeBuilder.build(); // 调用build方法构建RelDataType并返回
  }

  private RelDataType toType(Ast.Type type) { // 方法定义：将AST的Type转换为RelDataType关系数据类型（重载方法）
    switch (type.op) { // 根据类型的操作类型进行分发处理
    case SCALAR_TYPE: // 处理标量类型
      return toType((Ast.ScalarType) type); // 调用toType方法处理标量类型
    case BAG_TYPE: // 处理BAG类型（集合）
      return toType((Ast.BagType) type); // 调用toType方法处理BAG类型
    case MAP_TYPE: // 处理MAP类型（映射）
      return toType((Ast.MapType) type); // 调用toType方法处理MAP类型
    case TUPLE_TYPE: // 处理TUPLE类型（元组）
      return toType((Ast.TupleType) type); // 调用toType方法处理TUPLE类型
    default: // 默认情况：未知类型
      throw new AssertionError("unknown type " + type); // 抛出断言错误，表示遇到了未知类型
    }
  }

  private RelDataType toType(Ast.ScalarType type) { // 方法定义：将AST的标量类型转换为RelDataType关系数据类型
    final RelDataTypeFactory typeFactory = builder.getTypeFactory(); // 获取关系数据类型工厂
    switch (type.name) { // 根据标量类型的名称进行分发处理
    case "boolean": // 处理布尔类型
      return typeFactory.createSqlType(SqlTypeName.BOOLEAN); // 创建BOOLEAN类型的RelDataType
    case "int": // 处理整数类型
      return typeFactory.createSqlType(SqlTypeName.INTEGER); // 创建INTEGER类型的RelDataType
    case "float": // 处理浮点类型
      return typeFactory.createSqlType(SqlTypeName.REAL); // 创建REAL类型的RelDataType
    default: // 默认情况：其他类型
      return typeFactory.createSqlType(SqlTypeName.VARCHAR); // 创建VARCHAR类型的RelDataType作为默认类型
    }
  }

  private RelDataType toType(Ast.BagType type) { // 方法定义：将AST的BAG类型转换为RelDataType关系数据类型
    final RelDataTypeFactory typeFactory = builder.getTypeFactory(); // 获取关系数据类型工厂
    final RelDataType t = toType(type.componentType); // 调用toType方法将BAG的元素类型转换为RelDataType
    return typeFactory.createMultisetType(t, -1); // 调用createMultisetType方法创建多重集合类型，-1表示无最大长度限制
  }

  private RelDataType toType(Ast.MapType type) { // 方法定义：将AST的MAP类型转换为RelDataType关系数据类型
    final RelDataTypeFactory typeFactory = builder.getTypeFactory(); // 获取关系数据类型工厂
    final RelDataType k = toType(type.keyType); // 调用toType方法将MAP的键类型转换为RelDataType
    final RelDataType v = toType(type.valueType); // 调用toType方法将MAP的值类型转换为RelDataType
    return typeFactory.createMapType(k, v); // 调用createMapType方法创建MAP类型，指定键类型和值类型
  }

  private RelDataType toType(Ast.TupleType type) { // 方法定义：将AST的TUPLE类型转换为RelDataType关系数据类型
    final RelDataTypeFactory typeFactory = builder.getTypeFactory(); // 获取关系数据类型工厂
    final RelDataTypeFactory.Builder builder = typeFactory.builder(); // 创建关系数据类型工厂的构建器
    for (Ast.FieldSchema fieldSchema : type.fieldSchemaList) { // 遍历TUPLE类型中的字段列表
      builder.add(fieldSchema.id.value, toType(fieldSchema.type)); // 调用toType方法将字段类型转换为RelDataType，并添加到构建器中
    }
    return builder.build(); // 调用build方法构建RelDataType并返回
  }

  private void toSortRex(List<RexNode> nodes, // 方法定义：将字段和排序方向转换为排序RexNode并添加到列表中
      Pair<Ast.Identifier, Ast.Direction> pair) { // 参数：nodes是RexNode列表，pair是字段标识符和排序方向的配对
    if (pair.left.isStar()) { // 如果字段是通配符（*），表示所有字段都参与排序
      for (RexNode node : builder.fields()) { // 遍历所有字段
        switch (pair.right) { // 根据排序方向进行处理
        case DESC: // 如果是降序
          node = builder.desc(node); // 调用builder的desc方法，将节点转换为降序节点
          break; // 跳出switch语句
        default: // 默认情况（升序）
          break; // 不做任何处理
        }
        nodes.add(node); // 将处理后的节点添加到列表中
      }
    } else { // 如果字段不是通配符，指定了具体字段
      RexNode node = toRex(pair.left); // 调用toRex方法将字段标识符转换为RexNode
      switch (pair.right) { // 根据排序方向进行处理
      case DESC: // 如果是降序
        node = builder.desc(node); // 调用builder的desc方法，将节点转换为降序节点
        break; // 跳出switch语句
      default: // 默认情况（升序）
        break; // 不做任何处理
      }
      nodes.add(node); // 将处理后的节点添加到列表中
    }
  }

  private RexNode toRex(Ast.Node exp) { // 方法定义：将AST表达式节点转换为RexNode行表达式
    final Ast.Call call; // 声明AST.Call变量，用于存储函数调用类型的节点
    switch (exp.op) { // 根据AST节点的操作类型进行分发处理
    case LITERAL: // 处理字面量
      return builder.literal(((Ast.Literal) exp).value); // 调用builder的literal方法，将字面量值转换为RexNode
    case IDENTIFIER: // 处理标识符（字段引用）
      final String value = ((Ast.Identifier) exp).value; // 获取标识符的值
      if (value.matches("^\\$[0-9]+")) { // 如果标识符匹配$数字的格式（如$0, $1），表示位置引用
        int i = Integer.valueOf(value.substring(1)); // 提取数字部分并转换为整数
        return builder.field(i); // 调用builder的field方法，根据位置创建字段引用RexNode
      }
      return builder.field(value); // 调用builder的field方法，根据名称创建字段引用RexNode
    case DOT: // 处理点操作（字段访问，如tuple.field）
      call = (Ast.Call) exp; // 将节点强制转换为Call类型
      final RexNode left = toRex(call.operands.get(0)); // 递归调用toRex方法，将左操作数转换为RexNode
      final Ast.Identifier right = (Ast.Identifier) call.operands.get(1); // 获取右操作数（字段名）
      return builder.dot(left, right.value); // 调用builder的dot方法，创建字段访问RexNode
    case EQ: // 处理等于操作
    case NE: // 处理不等于操作
    case GT: // 处理大于操作
    case GTE: // 处理大于等于操作
    case LT: // 处理小于操作
    case LTE: // 处理小于等于操作
    case AND: // 处理逻辑与操作
    case OR: // 处理逻辑或操作
    case NOT: // 处理逻辑非操作
    case PLUS: // 处理加法操作
    case MINUS: // 处理减法操作
      call = (Ast.Call) exp; // 将节点强制转换为Call类型
      return builder.call(op(exp.op), toRex(call.operands)); // 调用builder的call方法，创建函数调用RexNode，传入操作符和转换后的操作数
    default: // 默认情况：未知操作
      throw new AssertionError("unknown op " + exp.op); // 抛出断言错误，表示遇到了未知的操作类型
    }
  }

  private static SqlOperator op(Ast.Op op) { // 方法定义：将AST操作类型转换为对应的SQL操作符
    switch (op) { // 根据AST操作类型进行分发处理
    case EQ: // 处理等于操作
      return SqlStdOperatorTable.EQUALS; // 返回标准SQL操作符表中的EQUALS操作符
    case NE: // 处理不等于操作
      return SqlStdOperatorTable.NOT_EQUALS; // 返回标准SQL操作符表中的NOT_EQUALS操作符
    case GT: // 处理大于操作
      return SqlStdOperatorTable.GREATER_THAN; // 返回标准SQL操作符表中的GREATER_THAN操作符
    case GTE: // 处理大于等于操作
      return SqlStdOperatorTable.GREATER_THAN_OR_EQUAL; // 返回标准SQL操作符表中的GREATER_THAN_OR_EQUAL操作符
    case LT: // 处理小于操作
      return SqlStdOperatorTable.LESS_THAN; // 返回标准SQL操作符表中的LESS_THAN操作符
    case LTE: // 处理小于等于操作
      return SqlStdOperatorTable.LESS_THAN_OR_EQUAL; // 返回标准SQL操作符表中的LESS_THAN_OR_EQUAL操作符
    case AND: // 处理逻辑与操作
      return SqlStdOperatorTable.AND; // 返回标准SQL操作符表中的AND操作符
    case OR: // 处理逻辑或操作
      return SqlStdOperatorTable.OR; // 返回标准SQL操作符表中的OR操作符
    case NOT: // 处理逻辑非操作
      return SqlStdOperatorTable.NOT; // 返回标准SQL操作符表中的NOT操作符
    case PLUS: // 处理加法操作
      return SqlStdOperatorTable.PLUS; // 返回标准SQL操作符表中的PLUS操作符
    case MINUS: // 处理减法操作
      return SqlStdOperatorTable.MINUS; // 返回标准SQL操作符表中的MINUS操作符
    default: // 默认情况：未知操作
      throw new AssertionError("unknown: " + op); // 抛出断言错误，表示遇到了未知的操作类型
    }
  }

  private ImmutableList<RexNode> toRex(Iterable<Ast.Node> operands) { // 方法定义：将AST操作数列表转换为RexNode列表（重载方法）
    final ImmutableList.Builder<RexNode> builder = ImmutableList.builder(); // 创建不可变列表的构建器，用于构建RexNode列表
    for (Ast.Node operand : operands) { // 遍历AST操作数列表
      builder.add(toRex(operand)); // 调用toRex方法将每个AST操作数转换为RexNode，并添加到构建器中
    }
    return builder.build(); // 调用build方法构建不可变的RexNode列表并返回
  }

  /** Assigns the current relational expression to a given name.
   * 将当前的关系表达式分配给指定的名称
   * 这个方法将builder栈顶的关系表达式注册到map中，使用名称作为键
   * 这样后续操作可以通过名称引用这个关系表达式
   * @param name 关系表达式的名称
   */
  private void register(String name) { // 方法定义：将当前关系表达式注册到map中
    map.put(name, builder.peek()); // 调用builder的peek方法获取栈顶的RelNode，并将其放入map中，使用name作为键
  }
}
