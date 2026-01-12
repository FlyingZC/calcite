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
package org.apache.calcite.piglet; // 包声明:org.apache.calcite.piglet 包,包含 Piglet 模块的相关类,Piglet 是 Calcite 的 Pig Latin 脚本转换器

import org.apache.calcite.plan.Context; // 导入 Context 类:Calcite 的上下文接口,用于传递配置和状态信息
import org.apache.calcite.plan.Contexts; // 导入 Contexts 工具类:用于创建和管理 Context 对象的工具类
import org.apache.calcite.plan.Convention; // 导入 Convention 类:定义关系代数节点的调用约定(如物理实现方式)
import org.apache.calcite.plan.RelOptCluster; // 导入 RelOptCluster 类:关系代数优化集群,包含类型工厂、表达式构建器等共享资源
import org.apache.calcite.plan.RelOptSchema; // 导入 RelOptSchema 接口:关系代数优化模式接口,用于查询表的元数据
import org.apache.calcite.plan.RelOptTable; // 导入 RelOptTable 类:关系代数优化表,封装表的元数据和访问信息
import org.apache.calcite.plan.ViewExpanders; // 导入 ViewExpanders 类:视图扩展器工厂,用于展开视图定义
import org.apache.calcite.rel.RelNode; // 导入 RelNode 接口:关系代数节点的基类接口,所有关系代数操作都实现此接口
import org.apache.calcite.rel.SingleRel; // 导入 SingleRel 类:单输入关系代数节点的基类,如投影、过滤等
import org.apache.calcite.rel.core.CorrelationId; // 导入 CorrelationId 类:相关联标识符,用于标识相关联变量
import org.apache.calcite.rel.core.JoinRelType; // 导入 JoinRelType 枚举:连接类型枚举(内连接、左外连接、全外连接等)
import org.apache.calcite.rel.core.Uncollect; // 导入 Uncollect 类:Uncollect 操作符节点,用于将多集或数组展开为行
import org.apache.calcite.rel.logical.LogicalJoin; // 导入 LogicalJoin 类:逻辑连接节点,表示关系代数中的连接操作
import org.apache.calcite.rel.logical.LogicalValues; // 导入 LogicalValues 类:逻辑值节点,表示常量行或空表
import org.apache.calcite.rel.type.RelDataType; // 导入 RelDataType 接口:关系数据类型接口,描述关系代数表达式的类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入 RelDataTypeFactory 接口:关系数据类型工厂接口,用于创建类型对象
import org.apache.calcite.rel.type.RelDataTypeField; // 导入 RelDataTypeField 类:关系数据类型字段,描述字段的名称和类型
import org.apache.calcite.rex.RexLiteral; // 导入 RexLiteral 类:字面量表达式节点,表示常量值
import org.apache.calcite.rex.RexNode; // 导入 RexNode 接口:行表达式节点接口,所有行表达式都实现此接口
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入 SqlStdOperatorTable 类:标准 SQL 操作符表,包含所有标准 SQL 操作符
import org.apache.calcite.sql.type.MultisetSqlType; // 导入 MultisetSqlType 类:多集 SQL 类型,表示集合类型
import org.apache.calcite.tools.FrameworkConfig; // 导入 FrameworkConfig 接口:Calcite 框架配置接口,用于配置查询框架
import org.apache.calcite.tools.RelBuilder; // 导入 RelBuilder 类:关系代数构建器,用于构建关系代数表达式树
import org.apache.calcite.util.Static; // 导入 Static 工具类:静态资源工具类,用于访问错误消息等静态资源
import org.apache.calcite.util.Util; // 导入 Util 工具类:Calcite 的通用工具类,提供各种辅助方法

import org.apache.pig.FuncSpec; // 导入 FuncSpec 类:Pig 函数规范类,描述 Pig UDF 的规范信息
import org.apache.pig.data.DataBag; // 导入 DataBag 类:Pig 数据包类,表示一个数据集合(多集)
import org.apache.pig.data.Tuple; // 导入 Tuple 类:Pig 元组类,表示一行数据
import org.apache.pig.newplan.Operator; // 导入 Operator 类:Pig 操作符基类,所有 Pig 操作符都继承此类
import org.apache.pig.newplan.logical.relational.LogicalRelationalOperator; // 导入 LogicalRelationalOperator 接口:Pig 逻辑关系操作符接口
import org.apache.pig.scripting.jython.JythonFunction; // 导入 JythonFunction 类:Pig 的 Jython 函数类,用于 Python UDF

import com.google.common.collect.ImmutableList; // 导入 ImmutableList 类:Google Guava 的不可变列表类
import com.google.common.collect.ImmutableSet; // 导入 ImmutableSet 类:Google Guava 的不可变集合类

import java.util.ArrayList; // 导入 ArrayList 类:Java 的动态数组列表
import java.util.Arrays; // 导入 Arrays 类:Java 的数组工具类
import java.util.Collections; // 导入 Collections 类:Java 的集合工具类
import java.util.HashMap; // 导入 HashMap 类:Java 的哈希映射表
import java.util.List; // 导入 List 接口:Java 的列表接口
import java.util.Map; // 导入 Map 接口:Java 的映射表接口
import java.util.function.UnaryOperator; // 导入 UnaryOperator 接口:Java 的单参数函数式接口

import static com.google.common.base.Preconditions.checkArgument; // 导入 checkArgument 方法:Google Guava 的前置条件检查方法

import static java.util.Objects.requireNonNull; // 导入 requireNonNull 方法:Java 的非空检查方法

/**
 * Extension to {@link RelBuilder} for Pig logical operators.
 */
public class PigRelBuilder extends RelBuilder { // PigRelBuilder 是 RelBuilder 的扩展类,专门用于构建 Pig 逻辑操作符对应的关系代数表达式树,它提供了将 Pig Latin 脚本转换为 Calcite 关系代数表示的功能
  private final Map<RelNode, String> reverseAliasMap = new HashMap<>(); // 反向别名映射表:从关系代数节点(RelNode)到 Pig 操作符别名的映射,用于通过关系代数节点快速查找对应的别名
  private final Map<String, RelNode> aliasMap = new HashMap<>(); // 别名映射表:从 Pig 操作符别名到关系代数节点(RelNode)的映射,用于通过别名快速查找对应的关系代数表达式
  private final Map<Operator, RelNode> pigRelMap = new HashMap<>(); // Pig 到关系代数映射表:从 Pig 逻辑操作符(Operator)到关系代数节点(RelNode)的映射,记录每个 Pig 操作符已转换的关系代数表达式
  private final Map<RelNode, Operator> relPigMap = new HashMap<>(); // 关系代数到 Pig 映射表:从关系代数节点(RelNode)到 Pig 逻辑操作符(Operator)的映射,是 pigRelMap 的反向映射
  private final Map<String, RelNode> storeMap = new HashMap<>(); // 存储映射表:从 Pig STORE 操作符的别名到关系代数节点(RelNode)的映射,用于记录所有输出结果的关系代数表达式
  private int nextCorrelId = 0; // 下一个相关联 ID 的计数器,用于生成唯一的相关联标识符,相关联用于处理嵌套查询和子查询
  private final PigRelTranslationContext pigRelContext = // Pig 关系代数转换上下文对象,用于存储 Pig 到关系代数转换过程中的上下文信息,如用户自定义函数(UDF)的注册信息
      new PigRelTranslationContext();

  private PigRelBuilder(Context context, RelOptCluster cluster, // 私有构造方法:创建 PigRelBuilder 实例,需要提供三个核心参数:context(上下文环境)、cluster(优化集群)、relOptSchema(关系代数模式)
      RelOptSchema relOptSchema) { // relOptSchema 参数:关系代数优化模式,用于提供表的元数据信息和模式定义
    super(context, cluster, relOptSchema); // 调用父类 RelBuilder 的构造方法,初始化关系代数构建器的基础功能,包括上下文、集群和模式信息
  }

  /** Creates a PigRelBuilder. */ // 工厂方法注释:创建 PigRelBuilder 实例的静态工厂方法
public static PigRelBuilder create(FrameworkConfig config) { // create 方法实现:根据框架配置创建 PigRelBuilder 实例
    final RelBuilder relBuilder = RelBuilder.create(config); // 首先使用框架配置创建标准的 RelBuilder 实例,作为基础构建器
    return new PigRelBuilder( // 创建并返回 PigRelBuilder 实例,传入三个参数
        transform(config.getContext(), c -> c.withBloat(-1)), // 第一个参数:转换后的上下文,设置 bloat 为 -1 以禁用某些膨胀优化
        relBuilder.getCluster(), // 第二个参数:从基础 RelBuilder 获取的优化集群,包含类型工厂和表达式构建器
        relBuilder.getRelOptSchema()); // 第三个参数:从基础 RelBuilder 获取的关系代数优化模式,用于表扫描和模式查询
  }

  private static Context transform(Context context, // transform 方法:转换上下文对象,应用给定的转换函数到 RelBuilder 配置
      UnaryOperator<RelBuilder.Config> transform) { // transform 参数:一个函数式接口,用于对 RelBuilder.Config 进行转换操作
    final Config config = // 从上下文中提取 RelBuilder.Config 配置对象
        context.maybeUnwrap(Config.class).orElse(Config.DEFAULT); // 如果上下文中包含 Config 则使用它,否则使用默认配置
    return Contexts.of(transform.apply(config), context); // 返回新的上下文对象,包含转换后的配置和原始上下文的其他信息
  }

  public RelNode getRel(String alias) { // getRel 方法:根据 Pig 操作符的别名获取对应的关系代数节点
    return aliasMap.get(alias); // 从别名映射表中查找并返回对应的关系代数节点,如果别名不存在则返回 null
  }

  public RelNode getRel(Operator pig) { // getRel 方法重载:根据 Pig 逻辑操作符获取对应的关系代数节点
    return pigRelMap.get(pig); // 从 Pig 到关系代数映射表中查找并返回对应的关系代数节点,如果操作符未转换则返回 null
  }

  Operator getPig(RelNode rel)  { // getPig 方法:根据关系代数节点获取对应的 Pig 逻辑操作符
    return relPigMap.get(rel); // 从关系代数到 Pig 映射表中查找并返回对应的 Pig 操作符,如果关系代数节点未注册则返回 null
  }

  String getAlias(RelNode rel)  { // getAlias 方法:根据关系代数节点获取对应的 Pig 操作符别名
    return reverseAliasMap.get(rel); // 从反向别名映射表中查找并返回对应的别名,如果关系代数节点没有别名则返回 null
  }

  /**
   * Gets the next correlation id.
   *
   * @return The correlation id
   */
  CorrelationId nextCorrelId() { // nextCorrelId 方法:获取下一个可用的相关联 ID,用于标识相关联变量
    return new CorrelationId(nextCorrelId++); // 创建新的 CorrelationId 对象,使用当前计数器值,然后递增计数器以供下次使用
  }

  public String getAlias() { // getAlias 方法:获取当前栈顶关系代数节点的别名
    final RelNode input = peek(); // 获取栈顶的关系代数节点,即当前正在处理的关系代数表达式
    if (reverseAliasMap.containsKey(input)) { // 检查该节点是否在反向别名映射表中
      return reverseAliasMap.get(input); // 如果存在则返回对应的别名
    }
    return null; // 如果不存在则返回 null,表示该节点没有别名
  }

  @Override public void clear() { // clear 方法:清空构建器的所有状态,重置为初始状态
    super.clear(); // 调用父类 RelBuilder 的 clear 方法,清空关系代数表达式栈
    reverseAliasMap.clear(); // 清空反向别名映射表
    aliasMap.clear(); // 清空别名映射表
    pigRelMap.clear(); // 清空 Pig 到关系代数映射表
    relPigMap.clear(); // 清空关系代数到 Pig 映射表
    storeMap.clear(); // 清空存储映射表
    nextCorrelId = 0; // 重置相关联 ID 计数器为 0
  }

  /**
   * Checks if a Pig logical operator has been translated before. If it has,
   * push the corresponding relational algebra operator on top instead of
   * doing the translation work again.
   *
   * @param pigOp The Pig logical operator to check.
   * @return true iff the pigOp has been processed before.
   */
  public boolean checkMap(LogicalRelationalOperator pigOp) { // checkMap 方法:检查 Pig 逻辑操作符是否已经被转换过,避免重复转换
    if (pigRelMap.containsKey(pigOp)) { // 检查 Pig 操作符是否在 pigRelMap 中,即是否已经转换过
      push(pigRelMap.get(pigOp)); // 如果已经转换过,直接将对应的关系代数节点压入栈顶,避免重复转换工作
      return true; // 返回 true 表示该操作符已经处理过
    }
    return false; // 返回 false 表示该操作符尚未转换,需要进行转换
  }

  /**
   * Updates the Pig logical operator and its alias with the top
   * relational algebra node.
   *
   * @param pigOp the Pig logical operator
   * @param alias the alias
   * @param updatePigRelMap whether to update the PigRelMap
   */
  public void updateAlias(Operator pigOp, String alias, boolean updatePigRelMap) { // updateAlias 方法:更新 Pig 逻辑操作符和别名与栈顶关系代数节点的映射关系
    final RelNode rel = peek(); // 获取栈顶的关系代数节点
    if (updatePigRelMap) { // 如果需要更新 Pig 到关系代数映射表
      pigRelMap.put(pigOp, rel); // 将 Pig 操作符映射到当前的关系代数节点
    }
    relPigMap.put(rel, pigOp); // 将关系代数节点反向映射到 Pig 操作符
    aliasMap.put(alias, rel); // 将别名映射到关系代数节点
    reverseAliasMap.put(rel, alias); // 将关系代数节点反向映射到别名
  }

  /**
   * Registers the Pig logical operator with the top relational algebra node.
   *
   * @param pigOp the Pig logical operator
   */
  void register(LogicalRelationalOperator pigOp) { // register 方法:注册 Pig 逻辑操作符与栈顶关系代数节点的映射关系
    updateAlias(pigOp, pigOp.getAlias(), true); // 调用 updateAlias 方法,使用 Pig 操作符的别名,并更新所有映射表
  }

  void registerPigUDF(String className, FuncSpec pigFunc) { // registerPigUDF 方法:注册 Pig 用户自定义函数(UDF)到转换上下文中
    Class udfClass = pigFunc.getClass(); // 获取 UDF 的类类型
    String key = className; // 初始化键为类名
    if (udfClass == JythonFunction.class) { // 如果是 Jython 函数(Python UDF)
      final String[] args = pigFunc.getCtorArgs(); // 获取构造函数参数
      requireNonNull(args, "args"); // 检查参数不为 null
      checkArgument(args.length == 2); // 检查参数长度必须为 2
      final String fileName = // 从文件路径中提取文件名(不带路径和扩展名)
          args[0].substring(args[0].lastIndexOf("/") + 1, // 从最后一个斜杠后开始
              args[0].lastIndexOf(".py")); // 到 .py 扩展名之前结束
      // key = [clas name]_[file name]_[function name] // 注释说明键的格式
      key = udfClass.getName() + "_" + fileName + "_" + args[1]; // 构造唯一键:类名_文件名_函数名
    }
    pigRelContext.pigUdfs.put(key, pigFunc); // 将 UDF 注册到上下文的 pigUdfs 映射表中
  }

  /**
   * Replaces the relational algebra operator at the top of the stack with
   * a new one.
   *
   * @param newRel the new relational algebra operator to replace
   */
  void replaceTop(RelNode newRel) { // replaceTop 方法:用新的关系代数节点替换栈顶的节点,并更新所有相关的映射关系
    final RelNode topRel = peek(); // 获取栈顶的关系代数节点
    if (topRel instanceof SingleRel) { // 检查栈顶节点是否是单输入节点(SingleRel)
      String alias = reverseAliasMap.get(topRel); // 获取栈顶节点的别名
      if (alias != null) { // 如果存在别名
        reverseAliasMap.remove(topRel); // 从反向别名映射表中移除旧节点的映射
        reverseAliasMap.put(newRel, alias); // 添加新节点到别名的映射
        aliasMap.put(alias, newRel); // 更新别名到新节点的映射
      }
      Operator pig = getPig(topRel); // 获取栈顶节点对应的 Pig 操作符
      if (pig != null) { // 如果存在对应的 Pig 操作符
        relPigMap.remove(topRel); // 从关系代数到 Pig 映射表中移除旧节点
        relPigMap.put(newRel, pig); // 添加新节点到 Pig 操作符的映射
        pigRelMap.put(pig, newRel); // 更新 Pig 操作符到新节点的映射
      }
      build(); // 构建当前栈中的所有节点
      push(newRel); // 将新节点压入栈顶
    }
  }

  /**
   * Scans a table with its given schema and names.
   *
   * @param userSchema The schema of the table to scan
   * @param tableNames The names of the table to scan
   * @return This builder
   */
  public RelBuilder scan(RelOptTable userSchema, String... tableNames) { // scan 方法:扫描表,使用给定的模式和表名创建表扫描操作
    // First, look up the database schema to find the table schema with the given names // 注释:首先查找数据库模式,找到给定表名的表模式
    final List<String> names = ImmutableList.copyOf(tableNames); // 将表名数组转换为不可变列表
    requireNonNull(relOptSchema, "relOptSchema"); // 检查 relOptSchema 不为 null
    final RelOptTable systemSchema = relOptSchema.getTableForMember(names); // 从系统模式中查找表

    // Now we may end up with two different schemas. // 注释:现在我们可能有两个不同的模式
    if (systemSchema != null) { // 如果系统模式存在
      if (userSchema != null && !compatibleType(userSchema.getRowType(), // 如果用户模式也存在且两者类型不兼容
          systemSchema.getRowType())) {
        // If both schemas are valid, they must be compatible // 注释:如果两个模式都有效,它们必须兼容
        throw new IllegalArgumentException( // 抛出非法参数异常
            "Pig script schema does not match database schema for table " + names + ".\n" // 错误信息:Pig 脚本模式与数据库模式不匹配
                + "\t Scrip schema: " + userSchema.getRowType().getFullTypeString() + "\n" // 显示脚本模式
                + "\t Database schema: " + systemSchema.getRowType().getFullTypeString()); // 显示数据库模式
      }
      // We choose to use systemSchema if it is valid // 注释:如果系统模式有效,我们选择使用系统模式
      return scan(systemSchema); // 使用系统模式进行扫描
    } else if (userSchema != null) { // 如果系统模式不存在但用户模式存在
      // If systemSchema is not valid, use userSchema if it is valid // 注释:如果系统模式无效,使用用户模式(如果有效)
      return scan(userSchema); // 使用用户模式进行扫描
    } else { // 如果两个模式都不存在
      // At least one of them needs to be valid // 注释:至少其中一个需要有效
      throw Static.RESOURCE.tableNotFound(String.join(".", names)).ex(); // 抛出表未找到异常
    }
  }

  /**
   * Scans a table with a given schema.
   *
   * @param tableSchema The table schema
   * @return This builder
   */
  private RelBuilder scan(RelOptTable tableSchema) { // scan 方法重载:使用给定的表模式创建表扫描操作(私有方法)
    final RelNode scan = // 创建表扫描节点
        getScanFactory() // 获取扫描工厂
            .createScan(ViewExpanders.simpleContext(cluster), tableSchema); // 使用简单的视图扩展器上下文和表模式创建扫描
    push(scan); // 将扫描节点压入栈顶
    return this; // 返回当前构建器实例,支持链式调用
  }

  /**
   * Makes a table scan operator for a given row type and names.
   *
   * @param rowType Row type
   * @param tableNames Table names
   * @return This builder
   */
  public RelBuilder scan(RelDataType rowType, String... tableNames) { // scan 方法重载:使用给定的行类型和表名创建表扫描操作
    return scan(rowType, Arrays.asList(tableNames)); // 调用另一个 scan 方法,将表名数组转换为列表
  }

  /**
   * Makes a table scan operator for a given row type and names.
   *
   * @param rowType Row type
   * @param tableNames Table names
   * @return This builder
   */
  public RelBuilder scan(RelDataType rowType, List<String> tableNames) { // scan 方法重载:使用给定的行类型和表名列表创建表扫描操作
    final RelOptTable relOptTable = // 创建关系代数优化表对象
        PigTable.createRelOptTable(getRelOptSchema(), rowType, tableNames); // 使用 PigTable 工厂方法创建表
    return scan(relOptTable); // 调用 scan 方法进行表扫描
  }

  /**
   * Projects a specific row type out of a relation algebra operator.
   * For any field in output type, if there is no matching input field, we project
   * null value of the corresponding output field type.
   *
   * <p>For example, given:
   * <ul>
   * <li>Input rel {@code A} with {@code A_type(X: int, Y: varchar)}
   * <li>Output type {@code B_type(X: int, Y: varchar, Z: boolean, W: double)}
   * </ul>
   *
   * <p>{@code project(A, B_type)} gives new relation
   * {@code C(X: int, Y: varchar, null, null)}.
   *
   * @param input The relation algebra operator to be projected
   * @param outputType The data type for the projected relation algebra operator
   * @return The projected relation algebra operator
   */
  public RelNode project(RelNode input, RelDataType outputType) { // project 方法:将关系代数节点投影到指定的输出类型,对于输出类型中存在但输入类型中不存在的字段,投影 null 值
    final RelDataType inputType = input.getRowType(); // 获取输入关系代数节点的行类型
    if (compatibleType(inputType, outputType) // 如果输入类型和输出类型兼容
        && inputType.getFieldNames().equals(outputType.getFieldNames())) { // 并且字段名称也相同
      // Same data type, simply returns the input rel // 注释:相同的数据类型,直接返回输入关系代数节点
      return input; // 返回输入节点,无需投影
    }

    // Now build the projection expressions on top of the input rel. // 注释:现在在输入关系代数节点之上构建投影表达式
    push(input); // 将输入节点压入栈顶
    project(projects(inputType, outputType), outputType.getFieldNames(), true); // 调用父类的 project 方法,使用生成的投影表达式和字段名
    return build(); // 构建并返回投影后的关系代数节点
  }

  /**
   * Builds the projection expressions for a data type on top of an input data type.
   * For any field in output type, if there is no matching input field, we build
   * the literal null expression with the corresponding output field type.
   *
   * @param inputType The input data type
   * @param outputType The output data type that defines the types of projection expressions
   * @return List of projection expressions
   */
  private List<RexNode> projects(RelDataType inputType, RelDataType outputType) { // projects 方法:构建从输入类型到输出类型的投影表达式列表
    final List<RelDataTypeField> outputFields = outputType.getFieldList(); // 获取输出类型的所有字段
    final List<RelDataTypeField> inputFields = inputType.getFieldList(); // 获取输入类型的所有字段
    final List<RexNode> projectionExprs = new ArrayList<>(); // 创建投影表达式列表

    for (RelDataTypeField outputField : outputFields) { // 遍历输出类型的每个字段
      RelDataTypeField matchInputField = null; // 初始化匹配的输入字段为 null
      // First find the matching input field // 注释:首先找到匹配的输入字段
      for (RelDataTypeField inputField : inputFields) { // 遍历输入类型的每个字段
        if (inputField.getName().equals(outputField.getName())) { // 如果字段名称相同
          // Matched if same name // 注释:如果名称相同则匹配
          matchInputField = inputField; // 记录匹配的输入字段
          break; // 跳出循环
        }
      }
      if (matchInputField != null) { // 如果找到匹配的输入字段
        RexNode fieldProject = field(matchInputField.getIndex()); // 创建字段引用表达式
        if (matchInputField.getType().equals(outputField.getType())) { // 如果类型也相同
          // If found and on same type, just project the field // 注释:如果找到且类型相同,直接投影该字段
          projectionExprs.add(fieldProject); // 添加字段引用到投影表达式列表
        } else { // 如果类型不同
          // Different types, CAST is required // 注释:类型不同,需要类型转换
          projectionExprs.add( // 添加类型转换表达式到投影列表
              getRexBuilder().makeCast(outputField.getType(), fieldProject)); // 使用 RexBuilder 创建 CAST 表达式
        }
      } else { // 如果没有找到匹配的输入字段
        final RelDataType columnType = outputField.getType(); // 获取输出字段的类型
        if (!columnType.isStruct() && columnType.getComponentType() == null) { // 如果不是结构类型且没有组件类型(基本类型)
          // If not, project the null Literal with the same basic type // 注释:如果不是,投影具有相同基本类型的 null 字面量
          projectionExprs.add(getRexBuilder().makeNullLiteral(outputField.getType())); // 创建 null 字面量表达式
        } else { // 如果是结构类型或多集类型
          // If Record or Multiset just project a constant null // 注释:如果是记录或多集,只投影常量 null
          projectionExprs.add(literal(null)); // 添加 null 字面量到投影列表
        }
      }
    }
    return projectionExprs; // 返回投影表达式列表
  }

  /**
   * Cogroups relations on top of the stack. The number of relations and the
   * group key are specified in groupKeys
   *
   * @param groupKeys Lists of group keys of relations to be cogrouped.
   * @return This builder
   */
  public RelBuilder cogroup(Iterable<? extends GroupKey> groupKeys) { // cogroup 方法:对栈上的多个关系进行协同分组(COGROUP)操作,按指定的分组键将多个关系分组并连接
    final List<GroupKey> groupKeyList = ImmutableList.copyOf(groupKeys); // 将分组键迭代器转换为不可变列表
    final int groupCount = groupKeyList.get(0).groupKeyCount(); // 获取分组键的数量

    // Pull out all relations needed for the group // 注释:提取所有需要分组的关系
    final int numRels = groupKeyList.size(); // 获取关系的数量
    List<RelNode> cogroupRels = new ArrayList<>(); // 创建关系代数节点列表
    for (int i = 0; i < numRels; i++) { // 遍历所有关系
      cogroupRels.add(0, build()); // 从栈中构建关系代数节点并添加到列表开头
    }

    // Group and join relations from left to right // 注释:从左到右分组和连接关系
    for (int i = 0; i < numRels; i++) { // 遍历每个关系
      // 1. Group each rel first by using COLLECT operator // 注释:1. 首先使用 COLLECT 操作符对每个关系进行分组
      push(cogroupRels.get(i)); // 将关系代数节点压入栈顶
      // Create a ROW to pass to COLLECT. // 注释:创建一个 ROW 传递给 COLLECT
      final RexNode row = field(groupCount); // 获取分组键之后的所有字段(有效载荷)
      aggregate(groupKeyList.get(i), // 使用指定的分组键进行聚合
          aggregateCall(SqlStdOperatorTable.COLLECT, row).as(getAlias())); // 调用 COLLECT 聚合函数,收集所有行到多集中
      if (i == 0) { // 如果是第一个关系
        continue; // 跳过后续步骤,因为第一个关系不需要连接
      }

      // 2. Then join with the previous group relation // 注释:2. 然后与前一个分组关系进行连接
      List<RexNode> predicates = new ArrayList<>(); // 创建连接谓词列表
      for (int key : Util.range(groupCount)) { // 遍历所有分组键
        predicates.add(equals(field(2, 0, key), field(2, 1, key))); // 添加等值谓词,比较左右关系的分组键
      }
      join(JoinRelType.FULL, and(predicates)); // 执行全外连接,使用所有分组键的等值条件

      // 3. Project group keys from one of these two joined relations, whichever // 注释:3. 从两个连接的关系中选择非空的分组键,并投影剩余的有效载荷列
      // is not null and the remaining payload columns // 注释:选择非空的分组键和剩余的有效载荷列
      RexNode[] projectFields = new RexNode[groupCount + i + 1]; // 创建投影表达式数组
      String[] fieldNames = new String [groupCount + i + 1]; // 创建字段名数组
      LogicalJoin join = (LogicalJoin) peek(); // 获取栈顶的连接节点
      for (int j = 0; j < groupCount; j++) { // 遍历所有分组键
        RexNode[] caseOperands = new RexNode[3]; // 创建 CASE 表达式的操作数数组
        // WHEN groupKey[i] of leftRel IS NOT NULL // 注释:当左关系的分组键不为 null
        caseOperands[0] = call(SqlStdOperatorTable.IS_NOT_NULL, field(j)); // 创建 IS NOT NULL 条件
        // THEN choose groupKey[i] of leftRel // 注释:则选择左关系的分组键
        caseOperands[1] = field(j); // 选择左关系的字段
        // ELSE choose groupKey[i] of rightRel // 注释:否则选择右关系的分组键
        caseOperands[2] = field(j + groupCount + i); // 选择右关系的字段
        projectFields[j] = call(SqlStdOperatorTable.CASE, caseOperands); // 创建 CASE 表达式,选择非空的分组键
        String leftName = join.getLeft().getRowType().getFieldNames().get(j); // 获取左关系的字段名
        String rightName = join.getRight().getRowType().getFieldNames().get(j); // 获取右关系的字段名
        fieldNames[j] = leftName.equals(rightName) ? leftName : rightName; // 如果名称相同则使用左名称,否则使用右名称
      }

      // Project the group fields of the leftRel // 注释:投影左关系的分组字段
      for (int j = groupCount; j < groupCount + i + 1; j++) { // 遍历左关系的有效载荷字段
        projectFields[j] = field(j); // 添加字段引用
        fieldNames[j] = peek().getRowType().getFieldNames().get(j); // 获取字段名
      }

      // Project the group fields of the rightRel // 注释:投影右关系的分组字段
      projectFields[groupCount + i] = field(2 * groupCount + i); // 添加右关系的有效载荷字段引用
      fieldNames[groupCount + i] = peek().getRowType().getFieldNames().get(2 * groupCount + i); // 获取字段名

      project(ImmutableList.copyOf(projectFields), ImmutableList.copyOf(fieldNames)); // 执行投影操作
    }
    return this; // 返回当前构建器实例
  }

  /**
   * Flattens the top relation on provided columns.
   *
   * @param flattenCols Indexes of columns to be flattened. These columns should have multiset type.
   * @return This builder
   */
  public RelBuilder multiSetFlatten(List<Integer> flattenCols, List<String> flattenOutputAliases) { // multiSetFlatten 方法:对栈顶关系的指定多集列进行扁平化操作,将多集中的每个元素展开为单独的行
    final int colCount = peek().getRowType().getFieldCount(); // 获取栈顶关系的列数
    final List<RelDataTypeField> inputFields = peek().getRowType().getFieldList(); // 获取栈顶关系的所有字段
    final CorrelationId correlId = nextCorrelId(); // 获取下一个相关联 ID

    // First build a correlated expression from the input row // 注释:首先从输入行构建相关联表达式
    final RexNode cor = correl(inputFields, correlId); // 创建相关联表达式

    // Then project out flatten columns from the correlated expression // 注释:然后从相关联表达式中投影出需要扁平化的列
    List<RexNode> flattenNodes = new ArrayList<>(); // 创建扁平化节点列表
    for (int i : flattenCols) { // 遍历需要扁平化的列索引
      assert inputFields.get(i).getType().getFamily() instanceof MultisetSqlType; // 断言该列的类型是多集类型
      flattenNodes.add(getRexBuilder().makeFieldAccess(cor, i)); // 创建字段访问表达式,访问相关联表达式的指定字段
    }
    push(LogicalValues.createOneRow(getCluster())); // 创建一个单行逻辑值节点并压入栈顶
    project(flattenNodes); // 投影出需要扁平化的多集列

    // Now do flatten on input rel that contains only multiset columns // 注释:现在对只包含多集列的输入关系进行扁平化
    multiSetFlatten(); // 调用无参的 multiSetFlatten 方法执行实际的扁平化操作

    // And rejoin the result -> output: original columns + new flattened columns // 注释:然后重新连接结果 -> 输出:原始列 + 新的扁平化列
    join(JoinRelType.INNER, literal(true), ImmutableSet.of(correlId)); // 执行内连接,使用相关联 ID 进行相关联连接

    // Finally project out only required columns. The original multiset columns are replaced // 注释:最后只投影所需的列。原始的多集列被替换为对应的新扁平化列
    // by the new corresponding flattened columns // 注释:被新的对应扁平化列替换
    final List<RexNode> finnalCols = new ArrayList<>(); // 创建最终列表达式列表
    final List<String> finnalColNames = new ArrayList<>(); // 创建最终列名列表
    int flattenCount = 0; // 扁平化列计数器
    for (int i = 0; i < colCount; i++) { // 遍历所有原始列
      if (flattenCols.indexOf(i) >= 0) { // 如果当前列需要扁平化
        // The original multiset columns to be flattened, select new flattened columns instead // 注释:原始的多集列需要扁平化,选择新的扁平化列代替
        RelDataType componentType = inputFields.get(i).getType().getComponentType(); // 获取多集的组件类型
        final int numSubFields = componentType.isStruct() ? componentType.getFieldCount() : 1; // 计算子字段数量,如果是结构类型则使用字段数,否则为 1
        for (int j = 0; j < numSubFields; j++) { // 遍历所有子字段
          finnalCols.add(field(colCount + flattenCount)); // 添加扁平化后的列引用
          finnalColNames.add(flattenOutputAliases.get(flattenCount)); // 添加扁平化列的别名
          flattenCount++; // 递增扁平化计数器
        }
      } else { // 如果当前列不需要扁平化
        // Otherwise, just copy the original column // 注释:否则,直接复制原始列
        finnalCols.add(field(i)); // 添加原始列引用
        finnalColNames.add(inputFields.get(i).getName()); // 添加原始列名
      }
    }
    project(finnalCols, finnalColNames); // 执行最终投影操作
    return this; // 返回当前构建器实例
  }

  /**
   * Flattens the top relation will all multiset columns. Call this method only if
   * the top relation contains multiset columns only.
   *
   * @return This builder.
   */
  public RelBuilder multiSetFlatten() { // multiSetFlatten 方法重载:对栈顶关系的所有多集列进行扁平化操作,仅当栈顶关系只包含多集列时调用此方法
    // [CALCITE-3193] Add RelBuilder.uncollect method, and interface // 注释:[CALCITE-3193] 添加 RelBuilder.uncollect 方法和接口
    // UncollectFactory, to instantiate Uncollect // 注释:UncollectFactory,用于实例化 Uncollect
    Uncollect uncollect = // 创建 Uncollect 节点,用于将多集展开为行
        Uncollect.create(cluster.traitSetOf(Convention.NONE), // 使用集群的特征集,约定为 NONE
            build(), // 构建栈顶的关系代数节点作为输入
            false, // 不使用 WITH ORDINALITY(不生成行号)
            Collections.emptyList()); // 空列表,表示没有额外的项
    push(uncollect); // 将 Uncollect 节点压入栈顶
    return this; // 返回当前构建器实例
  }

  /**
   * Makes the correlated expression from rel input fields and correlation id.
   *
   * @param inputFields Rel input field list
   * @param correlId Correlation id
   *
   * @return This builder
   */
  public RexNode correl(List<RelDataTypeField> inputFields, // correl 方法:从关系代数输入字段和相关联 ID 创建相关联表达式
      CorrelationId correlId) { // correlId 参数:相关联标识符,用于标识相关联变量
    final RelDataTypeFactory.Builder fieldBuilder = // 创建类型工厂构建器
        PigTypes.TYPE_FACTORY.builder(); // 使用 PigTypes 的类型工厂
    for (RelDataTypeField field : inputFields) { // 遍历所有输入字段
      fieldBuilder.add(field.getName(), field.getType()); // 添加字段名和类型到构建器
    }
    return getRexBuilder().makeCorrel(fieldBuilder.uniquify().build(), correlId); // 使用 RexBuilder 创建相关联表达式,确保字段名唯一
  }

  /**
   * Collects all rows of the top rel into a single multiset value.
   *
   * @return This builder
   */
  public RelBuilder collect() { // collect 方法:将栈顶关系的所有行收集到一个单一的多集值中
    final  RelNode inputRel = peek(); // 获取栈顶的关系代数节点

    // First project out a combined column which is a of all other columns // 注释:首先投影出一个组合列,该列包含所有其他列
    final RexNode row = // 创建 ROW 表达式,将所有字段组合成一个行值
        getRexBuilder() // 获取 RexBuilder
            .makeCall(inputRel.getRowType(), SqlStdOperatorTable.ROW, fields()); // 使用 ROW 操作符创建行值表达式
    project(ImmutableList.of(literal("all"), row)); // 投影出一个常量 "all" 和组合的行值

    // Update the alias map for the new projected rel. // 注释:为新投影的关系更新别名映射
    updateAlias(getPig(inputRel), getAlias(inputRel), false); // 更新别名映射,但不更新 pigRelMap

    // Build a single group for all rows // 注释:为所有行构建单个分组
    cogroup(ImmutableList.of(groupKey(ImmutableList.<RexNode>of(field(0))))); // 使用常量 "all" 作为分组键执行 COGROUP

    // Finally project out the final multiset value // 注释:最后投影出最终的多集值
    project(field(1)); // 投影出 COLLECT 聚合的结果(多集值)

    return this; // 返回当前构建器实例
  }

  public RexNode dot(RexNode node, Object field) { // dot 方法:访问节点的字段,支持通过索引或名称访问
    if (field instanceof Integer) { // 如果字段参数是整数(索引访问)
      int fieldIndex = (Integer) field; // 获取字段索引
      final RelDataType type = node.getType(); // 获取节点类型
      if (type instanceof DynamicTupleRecordType) { // 如果是动态元组记录类型
        ((DynamicTupleRecordType) type).resize(fieldIndex + 1); // 调整类型大小以适应字段索引
      }
      return super.dot(node, fieldIndex); // 调用父类的 dot 方法,通过索引访问字段
    }
    return super.dot(node, (String) field); // 如果字段参数是字符串,调用父类的 dot 方法,通过名称访问字段
  }

  public RexLiteral literal(Object value, RelDataType type) { // literal 方法:创建字面量表达式,支持 Pig 的 Tuple 和 DataBag 类型
    if (value instanceof Tuple) { // 如果值是 Pig 的 Tuple 类型
      assert type.isStruct(); // 断言类型是结构类型
      return getRexBuilder().makeLiteral(((Tuple) value).getAll(), type); // 创建 Tuple 的字面量表达式
    }

    if (value instanceof DataBag) { // 如果值是 Pig 的 DataBag 类型(多集)
      assert type.getComponentType() != null && type.getComponentType().isStruct(); // 断言类型有组件类型且组件类型是结构类型
      final List<List<Object>> multisetObj = new ArrayList<>(); // 创建多集对象列表
      for (Tuple tuple : (DataBag) value) { // 遍历 DataBag 中的每个 Tuple
        multisetObj.add(tuple.getAll()); // 将 Tuple 的所有字段添加到多集对象列表
      }
      return getRexBuilder().makeLiteral(multisetObj, type); // 创建多集的字面量表达式
    }
    return getRexBuilder().makeLiteral(value, type); // 对于其他类型,创建标准的字面量表达式
  }

  /**
   * Saves the store alias with the corresponding relational algebra node.
   *
   * @param storeAlias alias of the Pig store operator
   * @return This builder
   */
  RelBuilder store(String storeAlias) { // store 方法:保存 Pig STORE 操作符的别名与对应关系代数节点的映射
    storeMap.put(storeAlias, build()); // 将别名和构建的关系代数节点存入 storeMap
    return this; // 返回当前构建器实例
  }

  /**
   * Gets all relational plans corresponding to Pig Store operators.
   *
   */
  public List<RelNode> getRelsForStores() { // getRelsForStores 方法:获取所有对应 Pig STORE 操作符的关系代数节点
    if (storeMap.isEmpty()) { // 如果 storeMap 为空
      return null; // 返回 null
    }
    return ImmutableList.copyOf(storeMap.values()); // 返回 storeMap 中所有值的不可变副本
  }

  public ImmutableList<RexNode> getFields(int inputCount, int inputOrdinal, int fieldOrdinal) { // getFields 方法:获取指定输入的字段表达式,支持获取单个字段或所有字段
    if (fieldOrdinal == -1) { // 如果字段序号为 -1
      return fields(inputCount, inputOrdinal); // 返回指定输入的所有字段
    }
    return ImmutableList.of(field(inputCount, inputOrdinal, fieldOrdinal)); // 返回包含单个字段表达式的不可变列表
  }

  /**
   * Checks if two relational data types are compatible.
   *
   * @param t1 first type
   * @param t2 second type
   * @return true if t1 is compatible with t2
   */
  public static boolean compatibleType(RelDataType t1, RelDataType t2) { // compatibleType 方法:检查两个关系数据类型是否兼容
    if (t1.isStruct() || t2.isStruct()) { // 如果任一类型是结构类型
      if (!t1.isStruct() || !t2.isStruct()) { // 如果只有一个类型是结构类型
        return false; // 不兼容
      }
      if (t1.getFieldCount() != t2.getFieldCount()) { // 如果字段数量不同
        return false; // 不兼容
      }
      List<RelDataTypeField> fields1 = t1.getFieldList(); // 获取类型 1 的字段列表
      List<RelDataTypeField> fields2 = t2.getFieldList(); // 获取类型 2 的字段列表
      for (int i = 0; i < fields1.size(); ++i) { // 遍历所有字段
        if (!compatibleType( // 递归检查字段类型是否兼容
            fields1.get(i).getType(), // 字段 1 的类型
            fields2.get(i).getType())) { // 字段 2 的类型
          return false; // 如果任一字段类型不兼容,则整体不兼容
        }
      }
      return true; // 所有字段类型都兼容
    }
    RelDataType comp1 = t1.getComponentType(); // 获取类型 1 的组件类型
    RelDataType comp2 = t2.getComponentType(); // 获取类型 2 的组件类型
    if ((comp1 != null) || (comp2 != null)) { // 如果任一类型有组件类型
      if ((comp1 == null) || (comp2 == null)) { // 如果只有一个类型有组件类型
        return false; // 不兼容
      }
      if (!compatibleType(comp1, comp2)) { // 递归检查组件类型是否兼容
        return false; // 不兼容
      }
    }
    return t1.getSqlTypeName().getFamily() == t2.getSqlTypeName().getFamily(); // 检查 SQL 类型名称族是否相同
  }

  /**
   * Context constructed during Pig-to-{@link RelNode} translation process.
   */
  public static class PigRelTranslationContext { // PigRelTranslationContext 内部类:Pig 到关系代数转换过程中构建的上下文,用于存储转换过程中的状态信息
    final Map<String, FuncSpec> pigUdfs = new HashMap<>(); // pigUdfs 映射表:存储 Pig 用户自定义函数(UDF)的规范(FuncSpec),键为 UDF 的唯一标识符
  }
}
