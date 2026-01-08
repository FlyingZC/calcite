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
package org.apache.calcite.prepare;

import org.apache.calcite.config.CalciteConnectionConfig;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.linq4j.function.Hints;
import org.apache.calcite.model.ModelHandler;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeFactoryImpl;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.schema.AggregateFunction;
import org.apache.calcite.schema.ScalarFunction;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.TableFunction;
import org.apache.calcite.schema.TableMacro;
import org.apache.calcite.schema.Wrapper;
import org.apache.calcite.schema.impl.ScalarFunctionImpl;
import org.apache.calcite.schema.lookup.LikePattern;
import org.apache.calcite.sql.SqlFunctionCategory;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.SqlOperatorTable;
import org.apache.calcite.sql.SqlSyntax;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.InferTypes;
import org.apache.calcite.sql.type.OperandTypes;
import org.apache.calcite.sql.type.ReturnTypes;
import org.apache.calcite.sql.type.SqlOperandMetadata;
import org.apache.calcite.sql.type.SqlOperandTypeInference;
import org.apache.calcite.sql.type.SqlReturnTypeInference;
import org.apache.calcite.sql.type.SqlTypeFamily;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.util.SqlOperatorTables;
import org.apache.calcite.sql.validate.SqlConformanceEnum;
import org.apache.calcite.sql.validate.SqlMoniker;
import org.apache.calcite.sql.validate.SqlMonikerImpl;
import org.apache.calcite.sql.validate.SqlMonikerType;
import org.apache.calcite.sql.validate.SqlNameMatcher;
import org.apache.calcite.sql.validate.SqlNameMatchers;
import org.apache.calcite.sql.validate.SqlUserDefinedAggFunction;
import org.apache.calcite.sql.validate.SqlUserDefinedFunction;
import org.apache.calcite.sql.validate.SqlUserDefinedTableFunction;
import org.apache.calcite.sql.validate.SqlUserDefinedTableMacro;
import org.apache.calcite.sql.validate.SqlValidatorUtil;
import org.apache.calcite.util.Optionality;
import org.apache.calcite.util.Util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.google.common.collect.ImmutableList.toImmutableList;

import static java.util.Objects.requireNonNull;

/**
 * Implementation of {@link org.apache.calcite.prepare.Prepare.CatalogReader}
 * and also {@link org.apache.calcite.sql.SqlOperatorTable} based on tables and
 * functions defined schemas.
 * CalciteCatalogReader类：Calcite目录读取器实现类，实现了Prepare.CatalogReader和SqlOperatorTable接口
 * 核心功能：负责从Calcite的Schema中读取和查找表、函数、类型等元数据信息
 * 主要职责：
 * 1. 提供表、视图的查询功能，支持通过多级名称路径查找表
 * 2. 提供函数（标量函数、聚合函数、表函数、表宏）的查询和查找功能
 * 3. 管理Schema路径，支持在多个Schema中搜索对象
 * 4. 提供类型信息的查询功能
 * 5. 支持大小写敏感/不敏感的名称匹配
 * 6. 实现SqlOperatorTable接口，用于SQL验证阶段的操作符查找
 * 7. 支持自动补全功能，提供Schema中所有对象的名称列表
 * 使用场景：SQL解析和验证阶段，用于解析SQL中的表名、函数名等标识符
 */
public class CalciteCatalogReader implements Prepare.CatalogReader {
  protected final CalciteSchema rootSchema; // 根Schema对象，整个Schema树的根节点，所有表和函数都从这里开始查找
  protected final RelDataTypeFactory typeFactory; // 关系数据类型工厂，用于创建和管理SQL数据类型（如INTEGER、VARCHAR等）
  private final List<List<String>> schemaPaths; // Schema路径列表，用于指定查找的Schema优先级顺序，支持多个搜索路径
  protected final SqlNameMatcher nameMatcher; // 名称匹配器，用于控制标识符的匹配规则（大小写敏感/不敏感）
  protected final CalciteConnectionConfig config; // Calcite连接配置对象，包含连接级别的配置参数（如大小写敏感设置、SQL兼容性等）

  public CalciteCatalogReader(CalciteSchema rootSchema, // 根Schema对象，所有元数据的起点
      List<String> defaultSchema, // 默认Schema路径，当SQL中未指定Schema时使用此路径
      RelDataTypeFactory typeFactory, // 类型工厂，用于创建SQL数据类型
      CalciteConnectionConfig config) { // 连接配置对象
    // 调用受保护的构造方法，初始化所有成员变量
    // SqlNameMatchers.withCaseSensitive：根据配置创建名称匹配器，控制大小写敏感
    // ImmutableList.of创建Schema路径列表：包含defaultSchema和空路径（根Schema）
    this(rootSchema, SqlNameMatchers.withCaseSensitive(config.caseSensitive()),
        ImmutableList.of(ImmutableList.copyOf(defaultSchema),
            ImmutableList.of()),
        typeFactory, config);
  }

  protected CalciteCatalogReader(CalciteSchema rootSchema, // 根Schema对象
      SqlNameMatcher nameMatcher, // 名称匹配器，控制标识符匹配规则
      List<List<String>> schemaPaths, // Schema路径列表，支持多个搜索路径
      RelDataTypeFactory typeFactory, // 类型工厂
      CalciteConnectionConfig config) { // 连接配置
    this.rootSchema = requireNonNull(rootSchema, "rootSchema"); // 验证rootSchema非空，否则抛出NullPointerException
    this.nameMatcher = nameMatcher; // 保存名称匹配器
    // 创建不可变的Schema路径列表副本
    // Util.isDistinct检查schemaPaths中是否有重复路径
    // 如果有重复，使用LinkedHashSet去重并保持顺序
    // Util.immutableCopy创建不可变列表，防止外部修改
    this.schemaPaths =
        Util.immutableCopy(Util.isDistinct(schemaPaths)
            ? schemaPaths
            : new LinkedHashSet<>(schemaPaths));
    this.typeFactory = typeFactory; // 保存类型工厂
    this.config = config; // 保存连接配置
  }

  @Override public CalciteCatalogReader withSchemaPath(List<String> schemaPath) { // 创建一个新的CatalogReader，使用指定的Schema路径
    // 返回一个新的CalciteCatalogReader实例，保持其他参数不变，只修改Schema路径
    // 新的Schema路径列表包含：指定的schemaPath和空路径（根Schema）
    // 这样可以在指定的Schema和根Schema中查找对象
    return new CalciteCatalogReader(rootSchema, nameMatcher,
        ImmutableList.of(schemaPath, ImmutableList.of()), typeFactory, config);
  }

  @Override public Prepare.@Nullable PreparingTable getTable(final List<String> names) { // 根据名称列表获取表对象，返回null表示未找到
    // 首先在默认Schema中查找，如果未找到则在根Schema中查找
    // names是表名的多级路径，如["schema1", "schema2", "table1"]
    // SqlValidatorUtil.getTableEntry会按照schemaPaths的顺序依次查找
    CalciteSchema.TableEntry entry = SqlValidatorUtil.getTableEntry(this, names);
    if (entry != null) { // 如果找到了表
      final Table table = entry.getTable(); // 获取表对象
      if (table instanceof Wrapper) { // 检查表是否实现了Wrapper接口（支持类型包装）
        // 尝试解包获取PreparingTable对象（RelOptTable接口）
        // Wrapper模式允许表对象包装额外的信息
        final Prepare.PreparingTable relOptTable =
            ((Wrapper) table).unwrap(Prepare.PreparingTable.class);
        if (relOptTable != null) { // 如果成功解包，直接返回
          return relOptTable;
        }
      }
      // 如果表不是Wrapper或解包失败，创建RelOptTableImpl包装器
      // RelOptTableImpl是PreparingTable的标准实现
      // table.getRowType(typeFactory)：获取表的行类型（包含所有列的类型信息）
      // entry：表条目，包含表的路径和名称信息
      // null：表示没有额外的统计信息
      return RelOptTableImpl.create(this,
          table.getRowType(typeFactory), entry, null);
    }
    return null; // 未找到表，返回null
  }

  @Override public CalciteConnectionConfig getConfig() { // 获取Calcite连接配置对象
    return config; // 返回连接配置，包含大小写敏感、SQL兼容性等配置参数
  }

  private Collection<org.apache.calcite.schema.Function> getFunctionsFrom(
      List<String> names) { // 根据名称列表查找函数，返回匹配的函数集合
    // 创建函数列表用于存储找到的函数
    final List<org.apache.calcite.schema.Function> functions2 =
        new ArrayList<>();
    // 创建Schema名称列表，用于确定在哪些Schema中查找函数
    final List<List<String>> schemaNameList = new ArrayList<>();
    if (names.size() > 1) { // 如果名称是限定的（包含Schema路径，如["schema1", "func1"]）
      // 限定名称：忽略schemaPaths路径，只在"/catalog"和"/"中查找
      // Util.skip跳过第一个路径（默认Schema），保留最后两个路径
      if (schemaPaths.size() > 1) {
        schemaNameList.addAll(Util.skip(schemaPaths));
      } else {
        schemaNameList.addAll(schemaPaths);
      }
    } else { // 如果名称是简单的（只有函数名，如["func1"]）
      // 遍历所有Schema路径，收集有效的Schema名称
      for (List<String> schemaPath : schemaPaths) {
        // 根据路径从根Schema开始查找
        CalciteSchema schema =
            SqlValidatorUtil.getSchema(rootSchema, schemaPath, nameMatcher);
        if (schema != null) { // 如果找到Schema，将其路径添加到列表
          schemaNameList.addAll(schema.getPath());
        }
      }
    }
    // 在收集到的每个Schema中查找函数
    for (List<String> schemaNames : schemaNameList) {
      // 构建完整的Schema路径：当前Schema路径 + 函数名前面的Schema部分
      // Iterables.concat连接两个迭代器
      // Util.skipLast(names)跳过names的最后一项（函数名），保留前面的Schema部分
      CalciteSchema schema =
          SqlValidatorUtil.getSchema(rootSchema,
              Iterables.concat(schemaNames, Util.skipLast(names)), nameMatcher);
      if (schema != null) { // 如果找到Schema
        final String name = Util.last(names); // 获取函数名（names的最后一项）
        boolean caseSensitive = nameMatcher.isCaseSensitive(); // 获取大小写敏感设置
        // 在Schema中查找指定名称的函数，添加到结果列表
        functions2.addAll(schema.getFunctions(name, caseSensitive));
      }
    }
    return functions2; // 返回找到的所有函数
  }

  @Override public @Nullable RelDataType getNamedType(SqlIdentifier typeName) { // 根据类型名称获取命名类型（如自定义类型、STRUCT类型等）
    // 使用SqlValidatorUtil在根Schema中查找类型条目
    // typeName是类型标识符，可能是简单名称或限定名称
    CalciteSchema.TypeEntry typeEntry = SqlValidatorUtil.getTypeEntry(getRootSchema(), typeName);
    if (typeEntry != null) { // 如果找到类型条目
      // typeEntry.getType()获取类型定义，apply(typeFactory)使用类型工厂创建实际的RelDataType对象
      return typeEntry.getType().apply(typeFactory);
    } else { // 未找到类型
      return null; // 返回null表示类型不存在
    }
  }

  @Override public List<SqlMoniker> getAllSchemaObjectNames(List<String> names) { // 获取指定Schema中所有对象的名称列表，用于自动补全功能
    // 根据名称列表查找Schema
    final CalciteSchema schema =
        SqlValidatorUtil.getSchema(rootSchema, names, nameMatcher);
    if (schema == null) { // 如果Schema不存在
      return ImmutableList.of(); // 返回空列表
    }
    // 创建不可变列表构建器，用于存储所有对象名称
    final ImmutableList.Builder<SqlMoniker> result = new ImmutableList.Builder<>();

    // 如果Schema不是匿名的（名称不为空），添加根Schema本身
    if (!schema.name.equals("")) {
      result.add(moniker(schema, null, SqlMonikerType.SCHEMA)); // null表示当前Schema
    }

    // 获取所有子Schema的映射
    final Map<String, CalciteSchema> schemaMap = schema.getSubSchemaMap();

    // 遍历所有子Schema，添加到结果列表
    for (String subSchema : schemaMap.keySet()) {
      result.add(moniker(schema, subSchema, SqlMonikerType.SCHEMA)); // 子Schema
    }

    // 遍历所有表，添加到结果列表
    // LikePattern.any()匹配所有表名（无过滤条件）
    for (String table : schema.getTableNames(LikePattern.any())) {
      result.add(moniker(schema, table, SqlMonikerType.TABLE)); // 表
    }

    // 遍历所有函数，添加到结果列表
    final NavigableSet<String> functions = schema.getFunctionNames();
    for (String function : functions) { // views are here as well（视图也作为函数存储）
      result.add(moniker(schema, function, SqlMonikerType.FUNCTION)); // 函数
    }
    return result.build(); // 构建并返回不可变列表
  }

  private static SqlMonikerImpl moniker(CalciteSchema schema, @Nullable String name,
      SqlMonikerType type) { // 创建SqlMoniker对象，表示Schema中的一个对象（表、函数、Schema等）
    // schema.path(name)构建对象的完整路径
    // 如果name为null，返回Schema自身的路径；否则返回Schema路径 + name
    final List<String> path = schema.path(name);
    // 特殊处理：如果路径只有一个元素，且根Schema不是匿名的，且类型是SCHEMA
    // 则将类型改为CATALOG（表示这是一个Catalog级别的Schema）
    if (path.size() == 1
        && !schema.root().name.equals("")
        && type == SqlMonikerType.SCHEMA) {
      type = SqlMonikerType.CATALOG;
    }
    // 创建并返回SqlMonikerImpl对象，包含完整路径和类型
    return new SqlMonikerImpl(path, type);
  }

  @Override public List<List<String>> getSchemaPaths() { // 获取Schema搜索路径列表
    return schemaPaths; // 返回Schema路径列表，按优先级顺序排列
  }

  @Override public Prepare.@Nullable PreparingTable getTableForMember(List<String> names) { // 根据成员名称获取表（与getTable方法功能相同）
    return getTable(names); // 直接调用getTable方法，提供一致的接口
  }

  @SuppressWarnings("deprecation") // 抑制过时警告
  @Override public @Nullable RelDataTypeField field(RelDataType rowType, String alias) { // 根据别名从行类型中获取字段
    // 使用名称匹配器在行类型中查找指定别名的字段
    // rowType：行类型，包含所有字段的类型信息
    // alias：字段别名
    return nameMatcher.field(rowType, alias);
  }

  @SuppressWarnings("deprecation") // 抑制过时警告
  @Override public boolean matches(String string, String name) { // 检查两个字符串是否匹配（根据名称匹配器的规则）
    // 使用名称匹配器比较两个字符串是否匹配
    // 支持大小写敏感/不敏感的匹配
    return nameMatcher.matches(string, name);
  }

  @Override public RelDataType createTypeFromProjection(final RelDataType type, // 从行类型创建投影类型（只包含指定列）
      final List<String> columnNameList) { // 要保留的列名列表
    // 使用SqlValidatorUtil创建投影类型
    // type：原始行类型
    // columnNameList：要保留的列名列表
    // typeFactory：类型工厂
    // nameMatcher.isCaseSensitive()：是否大小写敏感
    return SqlValidatorUtil.createTypeFromProjection(type, columnNameList,
        typeFactory, nameMatcher.isCaseSensitive());
  }

  @Override public void lookupOperatorOverloads(final SqlIdentifier opName, // 查找操作符的重载版本，用于SQL验证阶段
      @Nullable SqlFunctionCategory category, // 函数类别（标量函数、聚合函数、表函数等），null表示所有类别
      SqlSyntax syntax, // SQL语法类型（函数、操作符等）
      List<SqlOperator> operatorList, // 输出参数，将找到的操作符添加到此列表
      SqlNameMatcher nameMatcher) { // 名称匹配器
    // 如果语法类型不是函数，直接返回（本类只处理函数）
    if (syntax != SqlSyntax.FUNCTION) {
      return;
    }

    // 创建函数谓词，用于过滤函数
    final Predicate<org.apache.calcite.schema.Function> predicate;
    if (category == null) { // 如果类别为null，接受所有函数
      predicate = function -> true;
    } else if (category.isTableFunction()) { // 如果是表函数类别，只接受表宏和表函数
      predicate = function ->
          function instanceof TableMacro
              || function instanceof TableFunction;
    } else { // 否则只接受非表函数（标量函数、聚合函数等）
      predicate = function ->
          !(function instanceof TableMacro
              || function instanceof TableFunction);
    }
    // 使用Stream API查找函数并转换为操作符
    // getFunctionsFrom：根据操作符名称查找函数
    // filter：根据谓词过滤函数
    // map：将函数转换为SqlOperator
    // forEachOrdered：按顺序添加到结果列表
    getFunctionsFrom(opName.names)
        .stream()
        .filter(predicate)
        .map(function -> toOp(opName, function, config))
        .forEachOrdered(operatorList::add);
  }

  /** Creates an operator table that contains functions in the given class
   * or classes.
   * 创建一个操作符表，包含指定类中的所有函数
   *
   * @see ModelHandler#addFunctions */
  public static SqlOperatorTable operatorTable(String... classNames) { // classNames：包含函数的Java类全限定名数组
    // 创建一个虚拟的根Schema，用于收集函数
    // createRootSchema(false, false)：false=不添加子Schema, false=不缓存
    final CalciteSchema schema =
        CalciteSchema.createRootSchema(false, false);
    // 遍历所有类名，使用ModelHandler将类中的函数添加到Schema
    for (String className : classNames) {
      // schema.plus()：创建子Schema
      // null：父Schema
      // ImmutableList.of()：空列表
      // className：类名
      // "*"：匹配所有方法
      // true：替换已存在的函数
      ModelHandler.addFunctions(schema.plus(), null, ImmutableList.of(),
          className, "*", true);
    }

    // 创建操作符列表
    final List<SqlOperator> list = new ArrayList<>();
    // 遍历Schema中的所有函数名
    for (String name : schema.getFunctionNames()) {
      // 获取每个函数名的所有函数（支持重载）
      schema.getFunctions(name, true).forEach(function -> {
        // 创建函数标识符
        final SqlIdentifier id = new SqlIdentifier(name, SqlParserPos.ZERO);
        // 将函数转换为SqlOperator，添加到列表
        list.add(toOp(id, function, CalciteConnectionConfig.DEFAULT));
      });
    }
    // 使用操作符列表创建SqlOperatorTable并返回
    return SqlOperatorTables.of(list);
  }

  /** Converts a function to a {@link org.apache.calcite.sql.SqlOperator}.
 * 将Schema函数转换为SqlOperator对象
 */
  private static SqlOperator toOp(SqlIdentifier name, // 函数标识符
      final org.apache.calcite.schema.Function function, // Schema函数对象
      CalciteConnectionConfig config) { // 连接配置
    // 创建参数类型工厂函数：根据类型工厂返回函数的参数类型列表
    // function.getParameters()：获取函数的参数列表
    // o.getType(typeFactory)：获取每个参数的类型
    // collect(toImmutableList)：收集为不可变列表
    final Function<RelDataTypeFactory, List<RelDataType>> argTypesFactory =
        typeFactory -> function.getParameters()
            .stream()
            .map(o -> o.getType(typeFactory))
            .collect(toImmutableList());
    // 创建类型族工厂函数：根据类型工厂返回参数的类型族列表
    // 类型族是类型的分类（如NUMERIC、STRING等）
    // type.getSqlTypeName().getFamily()：获取类型族
    // Util.first：如果类型族为null，使用SqlTypeFamily.ANY
    final Function<RelDataTypeFactory, List<SqlTypeFamily>> typeFamiliesFactory =
        typeFactory -> argTypesFactory.apply(typeFactory)
            .stream()
            .map(type ->
                Util.first(type.getSqlTypeName().getFamily(),
                    SqlTypeFamily.ANY))
            .collect(toImmutableList());
    // 创建参数类型工厂函数：根据类型工厂返回参数的SQL类型列表
    // toSql(typeFactory, type)：将Java类型转换为SQL类型
    final Function<RelDataTypeFactory, List<RelDataType>> paramTypesFactory =
        typeFactory ->
            argTypesFactory.apply(typeFactory)
                .stream()
                .map(type -> toSql(typeFactory, type))
                .collect(toImmutableList());

    // 使用临时的类型工厂来填充"typeFamilies"和"argTypes"
    // SqlOperandMetadata.paramTypes将在验证期间使用真实的类型工厂
    // 这样可以避免在创建操作符时就确定具体的类型
    final RelDataTypeFactory dummyTypeFactory = new JavaTypeFactoryImpl();
    final List<RelDataType> argTypes = argTypesFactory.apply(dummyTypeFactory);
    final List<SqlTypeFamily> typeFamilies =
        typeFamiliesFactory.apply(dummyTypeFactory);

    // 创建操作数类型推断器：使用显式类型推断
    // InferTypes.explicit：参数类型必须与定义的类型完全匹配
    final SqlOperandTypeInference operandTypeInference =
        InferTypes.explicit(argTypes);

    // 创建操作数元数据：包含参数的类型族、类型、名称和是否可选等信息
    // typeFamilies：参数的类型族
    // paramTypesFactory：参数类型的工厂函数
    // i -> function.getParameters().get(i).getName()：获取参数名称
    // i -> function.getParameters().get(i).isOptional()：获取参数是否可选
    final SqlOperandMetadata operandMetadata =
        OperandTypes.operandMetadata(typeFamilies, paramTypesFactory,
            i -> function.getParameters().get(i).getName(),
            i -> function.getParameters().get(i).isOptional());

    // 获取函数的SqlKind（函数类型，如标量函数、聚合函数等）
    final SqlKind kind = kind(function);
    // 根据函数类型创建相应的SqlOperator
    if (function instanceof ScalarFunction) { // 标量函数
      // 推断返回类型
      final SqlReturnTypeInference returnTypeInference =
          infer((ScalarFunction) function);
      // 获取SQL语法类型（FUNCTION或FUNCTION_ID）
      SqlSyntax syntax = getSqlSyntax(function, config);
      // 创建用户定义的标量函数操作符
      return new SqlUserDefinedFunction(name, kind, returnTypeInference,
          operandTypeInference, operandMetadata, function, syntax);
    } else if (function instanceof AggregateFunction) { // 聚合函数
      // 推断返回类型
      final SqlReturnTypeInference returnTypeInference =
          infer((AggregateFunction) function);
      // 创建用户定义的聚合函数操作符
      // false, false：不允许DISTINCT、不允许FILTER
      // Optionality.FORBIDDEN：不允许空参数
      return new SqlUserDefinedAggFunction(name, kind,
          returnTypeInference, operandTypeInference,
          operandMetadata, (AggregateFunction) function, false, false,
          Optionality.FORBIDDEN);
    } else if (function instanceof TableMacro) { // 表宏（返回表的宏）
      // 创建用户定义的表宏操作符
      // ReturnTypes.CURSOR：返回类型是游标（表）
      return new SqlUserDefinedTableMacro(name, kind, ReturnTypes.CURSOR,
          operandTypeInference, operandMetadata, (TableMacro) function);
    } else if (function instanceof TableFunction) { // 表函数（返回表的函数）
      // 创建用户定义的表函数操作符
      // ReturnTypes.CURSOR：返回类型是游标（表）
      return new SqlUserDefinedTableFunction(name, kind, ReturnTypes.CURSOR,
          operandTypeInference, operandMetadata, (TableFunction) function);
    } else { // 未知函数类型
      throw new AssertionError("unknown function type " + function);
    }
  }

  private static SqlSyntax getSqlSyntax(org.apache.calcite.schema.Function function, // 获取函数的SQL语法类型
      CalciteConnectionConfig config) { // 连接配置
    if (!function.getParameters().isEmpty()) { // 如果函数有参数
      return SqlSyntax.FUNCTION; // 使用标准函数语法：func(arg1, arg2)
    }
    // 无参数函数的特殊处理
    // 保持与Calcite默认兼容性的同时支持Foo()和Foo两种语法
    if (SqlConformanceEnum.DEFAULT == config.conformance()) { // 如果是默认兼容性
      return SqlSyntax.FUNCTION_ID_CONSTANT; // 使用FUNCTION_ID_CONSTANT语法
    }
    // 根据配置决定是否允许无参函数使用括号
    return config.conformance().allowNiladicParentheses()
        ? SqlSyntax.FUNCTION // 允许括号：func()
        : SqlSyntax.FUNCTION_ID; // 不允许括号：func
  }

  /** Deduces the {@link org.apache.calcite.sql.SqlKind} of a user-defined
   * function based on a {@link Hints} annotation, if present.
   * 根据Hints注解推断用户定义函数的SqlKind类型
   */
  private static SqlKind kind(org.apache.calcite.schema.Function function) { // 函数对象
    // 检查函数是否是ScalarFunctionImpl（基于Java方法的标量函数）
    if (function instanceof ScalarFunctionImpl) {
      // 获取方法上的Hints注解
      Hints hints =
          ((ScalarFunctionImpl) function).method.getAnnotation(Hints.class);
      if (hints != null) { // 如果存在Hints注解
        // 遍历所有提示
        for (String hint : hints.value()) {
          // 查找"SqlKind:"开头的提示
          if (hint.startsWith("SqlKind:")) {
            // 提取SqlKind值并返回
            return SqlKind.valueOf(hint.substring("SqlKind:".length()));
          }
        }
      }
    }
    // 默认返回OTHER_FUNCTION
    return SqlKind.OTHER_FUNCTION;
  }

  private static SqlReturnTypeInference infer(final ScalarFunction function) { // 推断标量函数的返回类型
    // 返回一个返回类型推断器，根据操作符绑定推断返回类型
    return opBinding -> { // opBinding：操作符绑定，包含参数类型等信息
      final RelDataTypeFactory typeFactory = opBinding.getTypeFactory(); // 获取类型工厂
      final RelDataType type; // 返回类型
      if (function instanceof ScalarFunctionImpl) { // 如果是基于Java方法的标量函数
        // 使用ScalarFunctionImpl的getReturnType方法，可以访问操作符绑定
        type =
            ((ScalarFunctionImpl) function).getReturnType(typeFactory, opBinding);
      } else { // 其他类型的标量函数
        // 使用标准的getReturnType方法
        type = function.getReturnType(typeFactory);
      }
      // 将Java类型转换为SQL类型
      return toSql(typeFactory, type);
    };
  }

  private static SqlReturnTypeInference infer(
      final AggregateFunction function) { // 推断聚合函数的返回类型
    // 返回一个返回类型推断器
    return opBinding -> { // opBinding：操作符绑定
      final RelDataTypeFactory typeFactory = opBinding.getTypeFactory(); // 获取类型工厂
      final RelDataType type = function.getReturnType(typeFactory); // 获取聚合函数的返回类型
      // 将Java类型转换为SQL类型
      return toSql(typeFactory, type);
    };
  }

  private static RelDataType toSql(RelDataTypeFactory typeFactory, // 将Java类型转换为SQL类型
      RelDataType type) { // 要转换的类型
    // 特殊处理：如果类型是Java的Object.class
    if (type instanceof RelDataTypeFactoryImpl.JavaType
        && ((RelDataTypeFactoryImpl.JavaType) type).getJavaClass()
        == Object.class) {
      // 创建ANY类型，并允许为null
      return typeFactory.createTypeWithNullability(
          typeFactory.createSqlType(SqlTypeName.ANY), true);
    }
    // 使用JavaTypeFactoryImpl的标准转换方法
    return JavaTypeFactoryImpl.toSql(typeFactory, type);
  }

  @Override public List<SqlOperator> getOperatorList() { // 获取所有可用的操作符列表（函数）
    // 创建不可变列表构建器
    final ImmutableList.Builder<SqlOperator> builder = ImmutableList.builder();
    // 遍历所有Schema路径
    for (List<String> schemaPath : schemaPaths) {
      // 根据路径查找Schema
      CalciteSchema schema =
          SqlValidatorUtil.getSchema(rootSchema, schemaPath, nameMatcher);
      if (schema != null) { // 如果找到Schema
        // 遍历Schema中的所有函数名
        for (String name : schema.getFunctionNames()) {
          // 获取每个函数名的所有函数（支持重载）
          schema.getFunctions(name, true).forEach(f ->
              // 将函数转换为SqlOperator并添加到列表
              builder.add(toOp(new SqlIdentifier(name, SqlParserPos.ZERO), f, config)));
        }
      }
    }
    // 构建并返回不可变列表
    return builder.build();
  }

  @Override public CalciteSchema getRootSchema() { // 获取根Schema对象
    return rootSchema; // 返回根Schema，所有元数据的起点
  }

  @Override public RelDataTypeFactory getTypeFactory() { // 获取类型工厂
    return typeFactory; // 返回类型工厂，用于创建和管理SQL数据类型
  }

  @Override public void registerRules(RelOptPlanner planner) { // 注册优化规则到查询优化器
    // 空实现：CalciteCatalogReader不需要注册优化规则
    // 优化规则通常由其他组件注册
  }

  @SuppressWarnings("deprecation") // 抑制过时警告
  @Override public boolean isCaseSensitive() { // 检查是否大小写敏感
    return nameMatcher.isCaseSensitive(); // 返回名称匹配器的大小写敏感设置
  }

  @Override public SqlNameMatcher nameMatcher() { // 获取名称匹配器
    return nameMatcher; // 返回名称匹配器，用于控制标识符的匹配规则
  }

  @Override public <C extends Object> @Nullable C unwrap(Class<C> aClass) { // 解包对象，获取指定类型的实例
    // 检查当前对象是否是指定类型的实例
    if (aClass.isInstance(this)) {
      // 如果是，强制转换并返回
      return aClass.cast(this);
    }
    // 如果不是，返回null
    return null;
  }
}
