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
// org.apache.calcite.adapter.arrow - Apache Arrow适配器包，用于将Calcite查询引擎与Apache Arrow列式存储格式集成
package org.apache.calcite.adapter.arrow;

// DataContext - Calcite的数据上下文接口，提供执行查询所需的运行时环境信息
import org.apache.calcite.DataContext;
// JavaTypeFactory - Java类型工厂，用于创建和管理Java类型系统与Calcite类型系统之间的映射
import org.apache.calcite.adapter.java.JavaTypeFactory;
// Enumerable - LINQ4J中的可枚举接口，表示可以进行迭代的数据集合
import org.apache.calcite.linq4j.Enumerable;
// QueryProvider - LINQ4J中的查询提供者接口，用于创建和执行查询
import org.apache.calcite.linq4j.QueryProvider;
// Queryable - LINQ4J中的可查询接口，表示可以进行查询操作的数据源
import org.apache.calcite.linq4j.Queryable;
// Expression - LINQ4J中的表达式类，用于表示代码生成中的表达式树
import org.apache.calcite.linq4j.tree.Expression;
// RelOptCluster - 关系优化集群，包含优化器需要的共享资源
import org.apache.calcite.plan.RelOptCluster;
// RelOptTable - 关系优化表，表示优化过程中的表对象
import org.apache.calcite.plan.RelOptTable;
// RelNode - 关系节点接口，表示关系代数表达式树中的节点
import org.apache.calcite.rel.RelNode;
// RelDataType - 关系数据类型，表示表中列的类型信息
import org.apache.calcite.rel.type.RelDataType;
// RelDataTypeFactory - 关系数据类型工厂，用于创建关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory;
// RelProtoDataType - 关系原型数据类型，用于延迟解析数据类型
import org.apache.calcite.rel.type.RelProtoDataType;
// QueryableTable - 可查询表接口，表示可以被查询的表
import org.apache.calcite.schema.QueryableTable;
// SchemaPlus - Calcite的Schema增强接口，提供Schema的注册和管理功能
import org.apache.calcite.schema.SchemaPlus;
// Schemas - Schema工具类，提供创建和操作Schema的静态方法
import org.apache.calcite.schema.Schemas;
// TranslatableTable - 可转换表接口，表示可以转换为关系代数表达式的表
import org.apache.calcite.schema.TranslatableTable;
// AbstractTable - 抽象表类，提供了表的基本实现
import org.apache.calcite.schema.impl.AbstractTable;
// ImmutableIntList - 不可变的整数列表，用于高效存储和访问整数集合
import org.apache.calcite.util.ImmutableIntList;
// Util - Calcite工具类，提供各种通用的静态方法
import org.apache.calcite.util.Util;

// Filter - Apache Arrow Gandiva表达式求值器中的过滤器，用于执行过滤操作
import org.apache.arrow.gandiva.evaluator.Filter;
// Projector - Apache Arrow Gandiva表达式求值器中的投影器，用于执行投影操作
import org.apache.arrow.gandiva.evaluator.Projector;
// GandivaException - Apache Arrow Gandiva异常类，表示Gandiva执行过程中的错误
import org.apache.arrow.gandiva.exceptions.GandivaException;
// Condition - Apache Arrow Gandiva表达式中的条件类，表示过滤条件
import org.apache.arrow.gandiva.expression.Condition;
// ExpressionTree - Apache Arrow Gandiva表达式树，表示表达式求值的树形结构
import org.apache.arrow.gandiva.expression.ExpressionTree;
// TreeBuilder - Apache Arrow Gandiva表达式树构建器，用于构建表达式树
import org.apache.arrow.gandiva.expression.TreeBuilder;
// TreeNode - Apache Arrow Gandiva表达式树节点，表示表达式树中的单个节点
import org.apache.arrow.gandiva.expression.TreeNode;
// ArrowFileReader - Apache Arrow文件读取器，用于读取Arrow格式的文件
import org.apache.arrow.vector.ipc.ArrowFileReader;
// ArrowType - Apache Arrow类型类，表示Arrow支持的数据类型
import org.apache.arrow.vector.types.pojo.ArrowType;
// Field - Apache Arrow字段类，表示表中的列定义
import org.apache.arrow.vector.types.pojo.Field;
// Schema - Apache Arrow Schema类，表示表的结构定义，包含所有字段信息
import org.apache.arrow.vector.types.pojo.Schema;

// @Nullable - CheckerFramework注解，表示返回值可能为null
import org.checkerframework.checker.nullness.qual.Nullable;

// IOException - Java IO异常类，表示输入输出操作中的错误
import java.io.IOException;
// Type - Java类型类，表示Java的类型信息
import java.lang.reflect.Type;
// ArrayList - Java动态数组类，用于存储可变长度的对象列表
import java.util.ArrayList;
// List - Java列表接口，表示有序的元素集合
import java.util.List;

// parseDouble - 将字符串解析为double类型的静态方法
import static java.lang.Double.parseDouble;
// parseFloat - 将字符串解析为float类型的静态方法
import static java.lang.Float.parseFloat;
// parseInt - 将字符串解析为int类型的静态方法
import static java.lang.Integer.parseInt;
// parseLong - 将字符串解析为long类型的静态方法
import static java.lang.Long.parseLong;
// requireNonNull - 检查对象是否为null的静态方法，如果为null则抛出NullPointerException
import static java.util.Objects.requireNonNull;

/**
 * Arrow Table.
 * Arrow表类，用于将Apache Arrow格式的数据表集成到Calcite查询引擎中
 * 该类实现了TranslatableTable和QueryableTable接口，支持将Arrow表转换为关系代数表达式
 * 并支持通过LINQ4J进行查询操作，利用Arrow的列式存储特性提供高性能的数据查询能力
 */
public class ArrowTable extends AbstractTable
    implements TranslatableTable, QueryableTable {
  // protoRowType - 关系原型数据类型，用于延迟解析表的行类型（列结构），可能为null
  // 如果不为null，则使用预定义的行类型；如果为null，则从Arrow schema中推导行类型
  private final @Nullable RelProtoDataType protoRowType;
  /** Arrow schema. (In Calcite terminology, more like a row type than a Schema.)
   * schema - Apache Arrow的Schema对象，包含表的结构定义（字段列表、字段类型等）
   * 在Calcite术语中，这更像是一个行类型而不是完整的Schema，因为它只描述了行的结构
   */
  private final Schema schema;
  // arrowFileReader - Apache Arrow文件读取器，用于读取Arrow格式文件中的数据
  // 该读取器提供了对Arrow文件中矢量数据的访问能力，支持高效的列式数据读取
  private final ArrowFileReader arrowFileReader;

  // ArrowTable构造方法，创建一个Arrow表实例
  // 参数protoRowType - 关系原型数据类型，可以为null，如果为null则从Arrow schema推导
  // 参数arrowFileReader - Arrow文件读取器，用于读取Arrow文件中的数据
  ArrowTable(@Nullable RelProtoDataType protoRowType, ArrowFileReader arrowFileReader) {
    try {
      // 从Arrow文件读取器的矢量架构根节点中获取Schema，并赋值给当前对象的schema成员变量
      // getVectorSchemaRoot()返回Arrow文件的根矢量容器，getSchema()从中提取表结构定义
      this.schema = arrowFileReader.getVectorSchemaRoot().getSchema();
    } catch (IOException e) {
      // 如果获取schema时发生IO异常，将其转换为未检查异常并抛出
      // Util.toUnchecked()方法将受检异常转换为运行时异常，简化异常处理
      throw Util.toUnchecked(e);
    }
    // 将传入的protoRowType参数赋值给当前对象的成员变量
    // protoRowType用于延迟解析表的行类型，如果为null则从Arrow schema中推导
    this.protoRowType = protoRowType;
    // 将传入的arrowFileReader参数赋值给当前对象的成员变量
    // arrowFileReader用于读取Arrow文件中的数据，是数据访问的核心对象
    this.arrowFileReader = arrowFileReader;
  }

  // getRowType方法，获取表的行类型（列结构），实现TranslatableTable接口
  // 参数typeFactory - 关系数据类型工厂，用于创建关系数据类型
  // 返回值 - 返回表的行类型（RelDataType），包含所有列的名称和类型信息
  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) {
    // 如果protoRowType不为null，则使用预定义的行类型
    // apply()方法将原型数据类型转换为实际的关系数据类型
    if (this.protoRowType != null) {
      return this.protoRowType.apply(typeFactory);
    }
    // 如果protoRowType为null，则从Arrow schema中推导行类型
    // deduceRowType()方法将Arrow schema转换为Calcite的关系数据类型
    return deduceRowType(this.schema, (JavaTypeFactory) typeFactory);
  }

  // getExpression方法，获取表的表达式，用于代码生成，实现TranslatableTable接口
  // 参数schema - SchemaPlus对象，表示表所在的schema
  // 参数tableName - 表名称，用于生成表达式
  // 参数clazz - 类类型，用于生成表达式
  // 返回值 - 返回表的表达式（Expression），用于LINQ4J查询
  @Override public Expression getExpression(SchemaPlus schema, String tableName,
      Class clazz) {
    // 调用Schemas工具类的tableExpression方法，生成表的表达式
    // 该表达式表示对表的引用，用于在LINQ4J查询中访问表数据
    return Schemas.tableExpression(schema, getElementType(), tableName, clazz);
  }

  /** Called via code generation; see uses of
   * {@link org.apache.calcite.adapter.arrow.ArrowMethod#ARROW_QUERY}.
   * query方法，执行查询并返回可枚举的结果集
   * 该方法通过代码生成调用，参见ArrowMethod#ARROW_QUERY的使用
   * 参数root - 数据上下文，提供执行查询所需的运行时环境信息
   * 参数fields - 需要查询的字段索引列表，表示要返回哪些列
   * 参数conditions - 查询条件列表，每个条件字符串表示一个过滤条件
   * 返回值 - 返回可枚举的对象集合，表示查询结果
   */
  @SuppressWarnings("unused")
  public Enumerable<Object> query(DataContext root, ImmutableIntList fields,
      List<String> conditions) {
    // 检查fields参数是否为null，如果为null则抛出NullPointerException
    // requireNonNull是一个空值检查工具方法，确保参数不为null
    requireNonNull(fields, "fields");
    // 声明投影器变量，用于执行投影操作（选择特定列）
    final Projector projector;
    // 声明过滤器变量，用于执行过滤操作（根据条件筛选行）
    final Filter filter;

    // 如果查询条件列表为空，表示只需要投影操作，不需要过滤
    if (conditions.isEmpty()) {
      // 将过滤器设置为null，表示不需要过滤操作
      filter = null;

      // 创建表达式树列表，用于存储投影表达式
      final List<ExpressionTree> expressionTrees = new ArrayList<>();
      // 遍历需要查询的字段索引列表
      for (int fieldOrdinal : fields) {
        // 从Arrow schema中获取指定索引的字段对象
        Field field = schema.getFields().get(fieldOrdinal);
        // 为该字段创建一个字段节点，表示对该字段的引用
        TreeNode node = TreeBuilder.makeField(field);
        // 将字段节点包装成表达式树，并添加到表达式树列表中
        // makeExpression()方法创建一个表达式，表示对字段的访问
        expressionTrees.add(TreeBuilder.makeExpression(node, field));
      }
      try {
        // 使用Arrow Gandiva创建投影器，用于执行投影操作
        // Projector.make()方法根据schema和表达式树创建投影器
        projector = Projector.make(schema, expressionTrees);
      } catch (GandivaException e) {
        // 如果创建投影器时发生Gandiva异常，将其转换为未检查异常并抛出
        throw Util.toUnchecked(e);
      }
    } else {
      // 如果查询条件列表不为空，表示需要进行过滤操作
      // 将投影器设置为null，表示不需要投影操作
      projector = null;

      // 创建条件节点列表，用于存储过滤条件
      final List<TreeNode> conditionNodes = new ArrayList<>(conditions.size());
      // 遍历查询条件列表
      for (String condition : conditions) {
        // 将条件字符串按空格分割成多个部分
        // 格式通常为：字段名 操作符 值 类型，例如 "age > 18 integer"
        String[] data = condition.split(" ");
        // 创建树节点列表，用于存储条件表达式中的节点
        List<TreeNode> treeNodes = new ArrayList<>(2);
        // 添加字段节点，表示条件表达式的左操作数（字段）
        // schema.findField(data[0])根据字段名查找字段
        // schema.getFields().indexOf()获取字段在字段列表中的索引
        // TreeBuilder.makeField()为该字段创建节点
        treeNodes.add(
            TreeBuilder.makeField(schema.getFields()
                .get(schema.getFields().indexOf(schema.findField(data[0])))));

        // 如果分割后的条件数据超过2个部分，说明这是一个二元操作符，带有字面量节点
        // 例如 "age > 18 integer" 有4个部分，需要添加字面量节点
        // if the split condition has more than two parts it's a binary operator
        // with an additional literal node
        if (data.length > 2) {
          // 添加字面量节点，表示条件表达式的右操作数（值）
          // data[2]是字面量的值，data[3]是字面量的类型
          treeNodes.add(makeLiteralNode(data[2], data[3]));
        }

        // 获取操作符，例如 ">"、"<"、"="等
        String operator = data[1];
        // 创建函数节点，表示条件表达式中的函数调用（例如比较函数）
        // TreeBuilder.makeFunction()创建一个函数调用节点
        // operator是函数名，treeNodes是参数列表，ArrowType.Bool()是返回类型
        conditionNodes.add(
            TreeBuilder.makeFunction(operator, treeNodes, new ArrowType.Bool()));
      }
      // 声明过滤条件变量
      final Condition filterCondition;
      // 如果条件节点列表只有一个条件，直接使用该条件创建过滤条件
      if (conditionNodes.size() == 1) {
        // TreeBuilder.makeCondition()将单个条件节点转换为条件对象
        filterCondition = TreeBuilder.makeCondition(conditionNodes.get(0));
      } else {
        // 如果条件节点列表有多个条件，使用AND操作符将它们组合成一个条件
        // TreeBuilder.makeAnd()创建一个AND节点，将多个条件用AND连接
        TreeNode treeNode = TreeBuilder.makeAnd(conditionNodes);
        // 将AND节点转换为条件对象
        filterCondition = TreeBuilder.makeCondition(treeNode);
      }

      try {
        // 使用Arrow Gandiva创建过滤器，用于执行过滤操作
        // Filter.make()方法根据schema和过滤条件创建过滤器
        filter = Filter.make(schema, filterCondition);
      } catch (GandivaException e) {
        // 如果创建过滤器时发生Gandiva异常，将其转换为未检查异常并抛出
        throw Util.toUnchecked(e);
      }
    }

    // 创建并返回ArrowEnumerable对象，该对象封装了查询结果的可枚举集合
    // ArrowEnumerable使用Arrow文件读取器、字段列表、投影器和过滤器来执行查询
    return new ArrowEnumerable(arrowFileReader, fields, projector, filter);
  }

  // asQueryable方法，将表转换为可查询对象，实现QueryableTable接口
  // 参数queryProvider - 查询提供者，用于创建和执行查询
  // 参数schema - SchemaPlus对象，表示表所在的schema
  // 参数tableName - 表名称
  // 返回值 - 返回可查询对象
  // 注意：该方法抛出不支持操作异常，因为Arrow表不支持直接转换为Queryable对象
  @Override public <T> Queryable<T> asQueryable(QueryProvider queryProvider,
      SchemaPlus schema, String tableName) {
    // 抛出不支持操作异常，表示Arrow表不支持直接转换为Queryable对象
    // Arrow表使用TranslatableTable接口进行查询转换，而不是QueryableTable接口
    throw new UnsupportedOperationException();
  }

  // getElementType方法，获取元素的类型，实现QueryableTable接口
  // 返回值 - 返回Object[].class，表示查询结果中每一行的类型是对象数组
  @Override public Type getElementType() {
    // 返回Object[].class，表示查询结果中的每一行都是一个对象数组
    // 数组中的每个元素对应一列的值
    return Object[].class;
  }

  // toRel方法，将表转换为关系节点，实现TranslatableTable接口
  // 参数context - 关系优化上下文，提供优化器需要的资源
  // 参数relOptTable - 关系优化表，表示优化过程中的表对象
  // 返回值 - 返回ArrowTableScan关系节点，表示对Arrow表的扫描操作
  @Override public RelNode toRel(RelOptTable.ToRelContext context,
      RelOptTable relOptTable) {
    // 获取表的字段数量
    final int fieldCount = relOptTable.getRowType().getFieldCount();
    // 创建字段索引列表，包含所有字段的索引（从0到fieldCount-1）
    // Util.range(fieldCount)生成从0到fieldCount-1的整数序列
    // ImmutableIntList.copyOf()将其转换为不可变的整数列表
    final ImmutableIntList fields =
        ImmutableIntList.copyOf(Util.range(fieldCount));
    // 从上下文中获取关系优化集群
    final RelOptCluster cluster = context.getCluster();
    // 创建并返回ArrowTableScan关系节点
    // ArrowTableScan表示对Arrow表的扫描操作
    // 参数包括：集群、特征集（包含Arrow约定）、关系优化表、当前ArrowTable对象、字段索引列表
    return new ArrowTableScan(cluster, cluster.traitSetOf(ArrowRel.CONVENTION),
        relOptTable, this, fields);
  }

  // deduceRowType方法，从Arrow schema推导Calcite的行类型（静态方法）
  // 参数schema - Arrow schema对象，包含表的字段定义
  // 参数typeFactory - Java类型工厂，用于创建Calcite类型
  // 返回值 - 返回Calcite的关系数据类型，包含所有字段的名称和类型信息
  private static RelDataType deduceRowType(Schema schema,
      JavaTypeFactory typeFactory) {
    // 创建关系数据类型构建器，用于构建行类型
    final RelDataTypeFactory.Builder builder = typeFactory.builder();
    // 遍历Arrow schema中的所有字段
    for (Field field : schema.getFields()) {
      // 将每个字段添加到行类型构建器中
      // field.getName()获取字段名称
      // ArrowFieldTypeFactory.toType()将Arrow类型转换为Calcite类型
      builder.add(field.getName(),
          ArrowFieldTypeFactory.toType(field.getType(), typeFactory));
    }
    // 构建并返回行类型
    return builder.build();
  }

  // makeLiteralNode方法，根据字面量值和类型创建字面量节点（静态方法）
  // 参数literal - 字面量字符串，表示字面量的值
  // 参数type - 类型字符串，表示字面量的类型
  // 返回值 - 返回树节点，表示字面量表达式
  private static TreeNode makeLiteralNode(String literal, String type) {
    // 如果类型以"decimal"开头，表示是Decimal类型
    if (type.startsWith("decimal")) {
      // 从类型字符串中提取精度和标度信息
      // 格式为：decimal(precision,scale)，例如 decimal(10,2)
      String[] typeParts =
          type.substring(type.indexOf('(') + 1, type.indexOf(')')).split(",");
      // 解析精度（总位数）
      int precision = parseInt(typeParts[0]);
      // 解析标度（小数位数）
      int scale = parseInt(typeParts[1]);
      // 创建Decimal字面量节点
      return TreeBuilder.makeDecimalLiteral(literal, precision, scale);
    // 如果类型是"integer"，表示是整数类型
    } else if (type.equals("integer")) {
      // 创建整数字面量节点，将字符串解析为整数
      return TreeBuilder.makeLiteral(parseInt(literal));
    // 如果类型是"long"，表示是长整型类型
    } else if (type.equals("long")) {
      // 创建长整型字面量节点，将字符串解析为长整型
      return TreeBuilder.makeLiteral(parseLong(literal));
    // 如果类型是"float"，表示是浮点型类型
    } else if (type.equals("float")) {
      // 创建浮点型字面量节点，将字符串解析为浮点数
      return TreeBuilder.makeLiteral(parseFloat(literal));
    // 如果类型是"double"，表示是双精度浮点型类型
    } else if (type.equals("double")) {
      // 创建双精度浮点型字面量节点，将字符串解析为双精度浮点数
      return TreeBuilder.makeLiteral(parseDouble(literal));
    // 如果类型是"string"，表示是字符串类型
    } else if (type.equals("string")) {
      // 创建字符串字面量节点
      // substring(1, literal.length() - 1)去掉字符串两端的引号
      return TreeBuilder.makeStringLiteral(literal.substring(1, literal.length() - 1));
    // 如果类型不匹配任何已知类型，抛出非法参数异常
    } else {
      throw new IllegalArgumentException("Invalid literal " + literal
          + ", type " + type);
    }
  }
}
