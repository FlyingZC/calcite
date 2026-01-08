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
package org.apache.calcite.model; // 所属包,包含模型处理相关的类

import org.apache.calcite.adapter.jdbc.JdbcSchema; // JDBC适配器Schema,用于连接关系型数据库
import org.apache.calcite.avatica.AvaticaUtils; // Avatica工具类,提供插件实例化等通用功能
import org.apache.calcite.jdbc.CalciteConnection; // Calcite连接接口,提供数据库连接功能
import org.apache.calcite.jdbc.CalciteSchema; // Calcite Schema包装类,提供Schema的元数据管理
import org.apache.calcite.materialize.Lattice; // Lattice(格状结构),用于物化视图优化
import org.apache.calcite.rel.type.RelDataType; // 关系数据类型接口,表示SQL中的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 关系数据类型工厂,用于创建数据类型
import org.apache.calcite.schema.AggregateFunction; // 聚合函数接口,定义聚合函数规范
import org.apache.calcite.schema.Function; // 函数接口,Calcite中所有函数的基接口
import org.apache.calcite.schema.ScalarFunction; // 标量函数接口,返回单个值的函数
import org.apache.calcite.schema.Schema; // Schema接口,表示数据库模式(包含表、视图、函数等)
import org.apache.calcite.schema.SchemaFactory; // Schema工厂接口,用于创建自定义Schema实例
import org.apache.calcite.schema.SchemaPlus; // SchemaPlus接口,扩展Schema接口,提供可变Schema功能
import org.apache.calcite.schema.Table; // Table接口,表示数据库表
import org.apache.calcite.schema.TableFactory; // Table工厂接口,用于创建自定义Table实例
import org.apache.calcite.schema.TableFunction; // 表函数接口,返回表结果的函数
import org.apache.calcite.schema.TableMacro; // 表宏接口,可以像表一样使用的宏
import org.apache.calcite.schema.impl.AbstractSchema; // 抽象Schema基类,提供Schema的默认实现
import org.apache.calcite.schema.impl.AggregateFunctionImpl; // 聚合函数实现类,基于反射实现聚合函数
import org.apache.calcite.schema.impl.MaterializedViewTable; // 物化视图表实现类
import org.apache.calcite.schema.impl.ScalarFunctionImpl; // 标量函数实现类,基于反射实现标量函数
import org.apache.calcite.schema.impl.TableFunctionImpl; // 表函数实现类,基于反射实现表函数
import org.apache.calcite.schema.impl.TableMacroImpl; // 表宏实现类,基于反射实现表宏
import org.apache.calcite.schema.impl.ViewTable; // 视图表实现类,表示SQL视图
import org.apache.calcite.schema.lookup.LikePattern; // LIKE模式匹配工具类
import org.apache.calcite.sql.SqlDialectFactory; // SQL方言工厂接口,用于创建特定数据库的方言
import org.apache.calcite.sql.type.SqlTypeName; // SQL类型名称枚举,定义所有SQL标准类型
import org.apache.calcite.util.Pair; // 不可变键值对工具类
import org.apache.calcite.util.Util; // 通用工具类,提供各种辅助方法

import com.fasterxml.jackson.core.JsonParser; // Jackson JSON解析器,用于解析JSON格式数据
import com.fasterxml.jackson.databind.ObjectMapper; // Jackson对象映射器,用于JSON/YAML与Java对象的转换
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper; // Jackson YAML映射器,专门用于解析YAML格式
import com.google.common.collect.ImmutableList; // Google Guava不可变列表,提供线程安全的列表实现
import com.google.common.collect.ImmutableMap; // Google Guava不可变Map,提供线程安全的Map实现

import org.checkerframework.checker.nullness.qual.Nullable; // CheckerFramework空值注解,用于标记可空类型

import java.io.File; // 文件类,用于文件系统操作
import java.io.IOException; // IO异常类,处理输入输出错误
import java.sql.SQLException; // SQL异常类,处理数据库操作错误
import java.util.ArrayDeque; // 数组双端队列,提供高效的栈操作
import java.util.Collections; // 集合工具类,提供不可修改集合等
import java.util.Deque; // 双端队列接口,支持两端插入删除
import java.util.List; // 列表接口,表示有序集合
import java.util.Locale; // 地区设置类,用于国际化
import java.util.Map; // Map接口,表示键值对映射
import javax.sql.DataSource; // JDBC数据源接口,提供数据库连接池

import static java.util.Objects.requireNonNull; // 静态导入,用于空值检查

/**
 * Reads a model and creates schema objects accordingly.
 * 读取模型文件并据此创建Schema对象
 * ModelHandler是Calcite模型加载的核心处理器,负责解析JSON或YAML格式的模型文件,
 * 并将模型定义转换为实际的Schema、表、视图、函数等对象
 * 它是Calcite配置系统的入口点,通过声明式的方式定义数据库结构
 */
public class ModelHandler { // ModelHandler类定义,模型处理器
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper() // 创建JSON对象映射器,用于解析JSON格式模型
      .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true) // 允许字段名不加引号,提高JSON容错性
      .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true) // 允许使用单引号,兼容更多JSON风格
      .configure(JsonParser.Feature.ALLOW_COMMENTS, true); // 允许JSON中包含注释,便于配置文件编写
  private static final ObjectMapper YAML_MAPPER = new YAMLMapper(); // 创建YAML对象映射器,用于解析YAML格式模型

  private final SchemaPlus rootSchema; // 根Schema对象,整个Schema树的根节点,所有其他Schema都是其子节点
  private final @Nullable String defaultSchemaName; // 默认Schema名称,未指定Schema时使用的默认Schema,可为null
  private final Deque<Pair<? extends @Nullable String, SchemaPlus>> schemaStack = // Schema栈,用于维护当前处理的Schema层级关系
      new ArrayDeque<>(); // 使用ArrayDeque实现栈结构,支持快速的push/pop操作
  private final String modelUri; // 模型文件的URI标识,可以是文件路径、URL或inline内联字符串
  Lattice.@Nullable Builder latticeBuilder; // Lattice构建器,用于构建物化视图优化结构,可为null表示当前未在构建Lattice
  Lattice.@Nullable TileBuilder tileBuilder; // Tile构建器,用于构建Lattice的Tile(瓦片)结构,可为null表示当前未在构建Tile

  @SuppressWarnings("method.invocation.invalid") // 抑制编译器警告,忽略方法调用无效的警告
  public ModelHandler(SchemaPlus rootSchema, String uri) throws IOException { // 构造方法,接收根Schema和模型URI,可能抛出IO异常
    super(); // 调用父类Object的构造方法
    this.modelUri = uri; // 保存模型URI到成员变量
    this.rootSchema = rootSchema; // 保存根Schema到成员变量
    JsonRoot root; // 声明JsonRoot变量,用于存储解析后的模型根对象
    ObjectMapper mapper; // 声明ObjectMapper变量,用于选择JSON或YAML解析器
    if (uri.startsWith("inline:")) { // 判断是否为内联模型(以"inline:"开头)
      // trim here is to correctly autodetect if it is json or not in case of leading spaces
      // 去除首尾空格是为了正确检测是否为JSON格式,避免前导空格影响判断
      String inline = uri.substring("inline:".length()).trim(); // 提取"inline:"后的内容并去除首尾空格
      mapper = (inline.startsWith("/*") || inline.startsWith("{")) // 根据内容判断是JSON还是YAML
          ? JSON_MAPPER // 如果以"/*"或"{"开头,使用JSON解析器
          : YAML_MAPPER; // 否则使用YAML解析器
      root = mapper.readValue(inline, JsonRoot.class); // 从内联字符串解析为JsonRoot对象
    } else { // 如果不是内联模型,则是文件路径或URL
      mapper = uri.endsWith(".yaml") || uri.endsWith(".yml") ? YAML_MAPPER : JSON_MAPPER; // 根据文件扩展名选择解析器:.yaml/.yml用YAML,其他用JSON
      root = mapper.readValue(new File(uri), JsonRoot.class); // 从文件解析为JsonRoot对象
    }
    visit(root); // 访问(处理)JsonRoot对象,开始递归处理模型的所有元素
    this.defaultSchemaName = root.defaultSchema; // 从模型中获取默认Schema名称并保存
  }

  @Deprecated // to be removed before 2.0 // 标记为已废弃,将在2.0版本前移除
  public ModelHandler(CalciteConnection connection, String uri) throws IOException { // 废弃的构造方法,接收Calcite连接和模型URI
    this(connection.getRootSchema(), uri); // 调用新构造方法,从连接中获取根Schema
    if (defaultSchemaName != null) { // 如果模型中定义了默认Schema名称
      try { // 尝试执行可能抛出异常的代码
        connection.setSchema(defaultSchemaName); // 设置连接的默认Schema
      } catch (SQLException e) { // 捕获SQL异常
        throw new RuntimeException(e); // 将SQL异常包装为运行时异常抛出
      }
    }
  }

  // CHECKSTYLE: IGNORE 1 // 告诉Checkstyle忽略下一行的检查
  /** @deprecated Use {@link #addFunctions}. */ // 标记为已废弃,建议使用addFunctions方法
  @Deprecated // 标记为已废弃
  public static void create(SchemaPlus schema, String functionName, // 静态方法,创建并添加函数到Schema(废弃方法)
      List<String> path, String className, String methodName) { // 接收Schema、函数名、路径、类名、方法名
    addFunctions(schema, functionName, path, className, methodName, false); // 调用新的addFunctions方法,upCase参数为false(不转大写)
  }

  /** Creates and validates a {@link ScalarFunctionImpl}, and adds it to a
   * schema. If {@code methodName} is "*", may add more than one function.
   * 创建并验证标量函数实现,并将其添加到Schema中。如果methodName为"*",可能会添加多个函数。
   *
   * @param schema Schema to add to // 要添加函数的Schema对象
   * @param functionName Name of function; null to derived from method name // 函数名称,如果为null则从方法名派生
   * @param unusedPath Path to look for functions (currently ignored) // 查找函数的路径(当前未使用)
   * @param className Class to inspect for methods that may be user-defined
   *                  functions // 要检查的类名,该类可能包含用户定义的函数方法
   * @param methodName Method name;
   *                  null means use the class as a UDF;
   *                  "*" means add all methods // 方法名,null表示将类作为UDF,"*"表示添加所有方法
   * @param upCase Whether to convert method names to upper case, so that they
   *               can be called without using quotes // 是否将方法名转为大写,这样调用时可以不加引号
   */
  public static void addFunctions(SchemaPlus schema, // 静态方法,向Schema添加函数
      @Nullable String functionName, List<String> unusedPath, // 可空的函数名,未使用的路径参数
      String className, @Nullable String methodName, boolean upCase) { // 类名,可空的方法名,是否转大写标志
    final Class<?> clazz; // 声明Class变量,用于存储加载的类对象
    try { // 尝试执行可能抛出异常的代码
      clazz = Class.forName(className); // 通过类名加载类,使用反射机制
    } catch (ClassNotFoundException e) { // 捕获类未找到异常
      throw new RuntimeException("UDF class '" // 抛出运行时异常,提示UDF类未找到
          + className + "' not found"); // 拼接异常消息
    }
    String methodNameOrDefault = Util.first(methodName, "eval"); // 获取方法名,如果为null则默认使用"eval"
    String actualFunctionName; // 声明实际使用的函数名变量
    if (functionName != null) { // 如果指定了函数名
      actualFunctionName = functionName; // 使用指定的函数名
    } else { // 如果没有指定函数名
      actualFunctionName = methodNameOrDefault; // 使用方法名作为函数名
    }
    if (upCase) { // 如果需要转大写
      actualFunctionName = actualFunctionName.toUpperCase(Locale.ROOT); // 将函数名转为大写,使用ROOT地区设置
    }
    final TableFunction tableFunction = // 尝试创建表函数
        TableFunctionImpl.create(clazz, methodNameOrDefault); // 通过反射创建表函数实现
    if (tableFunction != null) { // 如果成功创建了表函数
      schema.add(Util.first(functionName, methodNameOrDefault), // 添加表函数到Schema,优先使用指定名称,否则使用方法名
          tableFunction); // 添加的表函数对象
      return; // 直接返回,函数已添加
    }
    // Must look for TableMacro before ScalarFunction. Both have an "eval" method.
    // 必须在查找标量函数之前查找表宏。两者都有"eval"方法。
    final TableMacro macro = TableMacroImpl.create(clazz); // 尝试创建表宏
    if (macro != null) { // 如果成功创建了表宏
      schema.add(actualFunctionName, macro); // 添加表宏到Schema,使用实际函数名
      return; // 直接返回,函数已添加
    }
    if (methodName != null && methodName.equals("*")) { // 如果方法名为"*",表示要添加所有方法
      for (Map.Entry<String, Function> entry // 遍历类中的所有标量函数
          : ScalarFunctionImpl.functions(clazz).entries()) { // 获取该类的所有标量函数映射
        String name = entry.getKey(); // 获取函数名
        if (upCase) { // 如果需要转大写
          name = name.toUpperCase(Locale.ROOT); // 将函数名转为大写
        }
        schema.add(name, entry.getValue()); // 将函数添加到Schema
      }
      return; // 直接返回,所有函数已添加
    } else { // 如果方法名不是"*"
      final ScalarFunction function = // 尝试创建标量函数
          ScalarFunctionImpl.create(clazz, methodNameOrDefault); // 通过反射创建标量函数实现
      if (function != null) { // 如果成功创建了标量函数
        schema.add(actualFunctionName, function); // 添加标量函数到Schema,使用实际函数名
        return; // 直接返回,函数已添加
      }
    }
    if (methodName == null) { // 如果方法名为null
      final AggregateFunction aggFunction = AggregateFunctionImpl.create(clazz); // 尝试创建聚合函数
      if (aggFunction != null) { // 如果成功创建了聚合函数
        schema.add(actualFunctionName, aggFunction); // 添加聚合函数到Schema,使用实际函数名
        return; // 直接返回,函数已添加
      }
    }
    throw new RuntimeException("Not a valid function class: " + clazz // 抛出运行时异常,提示这不是有效的函数类
        + ". Scalar functions and table macros have an 'eval' method; " // 标量函数和表宏需要'eval'方法
        + "aggregate functions have 'init' and 'add' methods, and optionally " // 聚合函数需要'init'和'add'方法
        + "'initAdd', 'merge' and 'result' methods."); // 以及可选的'initAdd'、'merge'和'result'方法
  }

  public void visit(JsonRoot jsonRoot) { // 访问JsonRoot对象,开始处理模型根节点
    final Pair<@Nullable String, SchemaPlus> pair = // 创建键值对,包含null名称和根Schema
        Pair.of(null, rootSchema); // 使用Pair工具类创建不可变键值对
    schemaStack.push(pair); // 将根Schema压入栈中,作为当前处理的Schema
    for (JsonType rootType : jsonRoot.types) { // 遍历模型中定义的所有类型
      rootType.accept(this); // 调用类型的accept方法,让类型访问this(ModelHandler)
    }
    for (JsonSchema schema : jsonRoot.schemas) { // 遍历模型中定义的所有Schema
      schema.accept(this); // 调用Schema的accept方法,让Schema访问this(ModelHandler)
    }
    final Pair<? extends @Nullable String, SchemaPlus> p = schemaStack.pop(); // 弹出栈顶元素(应该是根Schema)
    assert p == pair; // 断言弹出的元素与之前压入的一致,确保栈的正确性
  }

  public void visit(JsonMapSchema jsonSchema) { // 访问Map类型的Schema(基于Map的简单Schema)
    final SchemaPlus parentSchema = currentMutableSchema("schema"); // 获取当前可变的父Schema
    final SchemaPlus schema = // 创建SchemaPlus对象
        parentSchema.add(jsonSchema.name, new AbstractSchema()); // 在父Schema中添加新的抽象Schema,使用指定名称
    if (jsonSchema.path != null) { // 如果Schema定义了路径
      schema.setPath(stringListList(jsonSchema.path)); // 设置Schema的路径,将路径转换为嵌套列表格式
    }
    populateSchema(jsonSchema, schema); // 填充Schema,处理Schema的子元素(表、视图、函数等)
  }

  private static ImmutableList<ImmutableList<String>> stringListList( // 静态私有方法,将路径转换为不可变的嵌套字符串列表
      List path) { // 接收路径列表参数
    final ImmutableList.Builder<ImmutableList<String>> builder = // 创建不可变列表的构建器
        ImmutableList.builder(); // 初始化构建器
    for (Object s : path) { // 遍历路径中的每个元素
      builder.add(stringList(s)); // 将每个元素转换为字符串列表并添加到构建器
    }
    return builder.build(); // 构建并返回不可变的嵌套列表
  }

  private static ImmutableList<String> stringList(Object s) { // 静态私有方法,将对象转换为不可变字符串列表
    if (s instanceof String) { // 如果对象是字符串
      return ImmutableList.of((String) s); // 返回包含该字符串的单元素不可变列表
    } else if (s instanceof List) { // 如果对象是列表
      final ImmutableList.Builder<String> builder2 = // 创建字符串列表的构建器
          ImmutableList.builder(); // 初始化构建器
      for (Object o : (List) s) { // 遍历列表中的每个元素
        if (o instanceof String) { // 如果元素是字符串
          builder2.add((String) o); // 将字符串添加到构建器
        } else { // 如果元素不是字符串
          throw new RuntimeException("Invalid path element " + o // 抛出运行时异常,提示路径元素无效
              + "; was expecting string"); // 说明期望的是字符串
        }
      }
      return builder2.build(); // 构建并返回不可变字符串列表
    } else { // 如果对象既不是字符串也不是列表
      throw new RuntimeException("Invalid path element " + s // 抛出运行时异常,提示路径元素无效
          + "; was expecting string or list of string"); // 说明期望的是字符串或字符串列表
    }
  }

  private void populateSchema(JsonSchema jsonSchema, SchemaPlus schema) { // 私有方法,填充Schema的子元素
    if (jsonSchema.cache != null) { // 如果Schema定义了缓存配置
      schema.setCacheEnabled(jsonSchema.cache); // 设置Schema的缓存启用状态
    }
    final Pair<String, SchemaPlus> pair = Pair.of(jsonSchema.name, schema); // 创建Schema名称和对象的键值对
    schemaStack.push(pair); // 将Schema压入栈中,作为当前处理的Schema
    jsonSchema.visitChildren(this); // 访问Schema的所有子元素(表、视图、函数等)
    final Pair<? extends @Nullable String, SchemaPlus> p = schemaStack.pop(); // 弹出栈顶元素(应该是当前Schema)
    assert p == pair; // 断言弹出的元素与之前压入的一致,确保栈的正确性
  }

  public void visit(JsonCustomSchema jsonSchema) { // 访问自定义Schema(使用SchemaFactory创建的Schema)
    try { // 尝试执行可能抛出异常的代码
      final SchemaPlus parentSchema = currentMutableSchema("sub-schema"); // 获取当前可变的父Schema,用于验证可变性
      final SchemaFactory schemaFactory = // 实例化Schema工厂
          AvaticaUtils.instantiatePlugin(SchemaFactory.class, // 使用Avatica工具实例化指定类型的插件
              jsonSchema.factory); // 使用Schema定义中的工厂类名
      final Schema schema = // 创建Schema实例
          schemaFactory.create( // 调用工厂的create方法
              parentSchema, jsonSchema.name, operandMap(jsonSchema, jsonSchema.operand)); // 传入父Schema、名称和操作数映射
      final SchemaPlus schemaPlus = parentSchema.add(jsonSchema.name, schema); // 将创建的Schema添加到父Schema中
      populateSchema(jsonSchema, schemaPlus); // 填充Schema,处理Schema的子元素
    } catch (Exception e) { // 捕获所有异常
      throw new RuntimeException("Error instantiating " + jsonSchema, e); // 抛出运行时异常,包装原始异常信息
    }
  }

  /** Adds extra entries to an operand to a custom schema. */
  /** 向自定义Schema的操作数添加额外条目。 */
  protected Map<String, Object> operandMap(@Nullable JsonSchema jsonSchema, // 受保护方法,构建操作数映射
      @Nullable Map<String, Object> operand) { // 接收Schema对象和原始操作数映射
    if (operand == null) { // 如果原始操作数为null
      return ImmutableMap.of(); // 返回空的不可变Map
    }
    final ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder(); // 创建不可变Map的构建器
    builder.putAll(operand); // 将原始操作数的所有条目添加到构建器
    for (ExtraOperand extraOperand : ExtraOperand.values()) { // 遍历所有额外的操作数类型
      if (!operand.containsKey(extraOperand.camelName)) { // 如果操作数中不包含该额外操作数
        switch (extraOperand) { // 根据额外操作数类型进行不同处理
        case MODEL_URI: // 如果是模型URI
          builder.put(extraOperand.camelName, modelUri); // 将模型URI添加到操作数中
          break; // 跳出switch
        case BASE_DIRECTORY: // 如果是基础目录
          File f = null; // 声明文件变量,初始为null
          if (!modelUri.startsWith("inline:")) { // 如果模型URI不是内联的
            final File file = new File(modelUri); // 根据URI创建文件对象
            f = file.getParentFile(); // 获取文件的父目录
          }
          if (f == null) { // 如果父目录为null
            f = new File(""); // 使用当前目录
          }
          builder.put(extraOperand.camelName, f); // 将基础目录添加到操作数中
          break; // 跳出switch
        case TABLES: // 如果是表定义
          if (jsonSchema instanceof JsonCustomSchema) { // 如果Schema是自定义Schema类型
            builder.put(extraOperand.camelName, // 将Schema中的表定义添加到操作数中
                ((JsonCustomSchema) jsonSchema).tables); // 强制类型转换并获取tables字段
          }
          break; // 跳出switch
        default: // 其他情况
          break; // 跳出switch
        }
      }
    }
    return builder.build(); // 构建并返回不可变的操作数映射
  }

  public void visit(JsonJdbcSchema jsonSchema) { // 访问JDBC Schema(连接关系型数据库的Schema)
    final SchemaPlus parentSchema = currentMutableSchema("jdbc schema"); // 获取当前可变的父Schema
    final DataSource dataSource = // 创建JDBC数据源
        JdbcSchema.dataSource(jsonSchema.jdbcUrl, // 使用JDBC URL创建数据源
            jsonSchema.jdbcDriver, // JDBC驱动类名
            jsonSchema.jdbcUser, // 数据库用户名
            jsonSchema.jdbcPassword); // 数据库密码
    final JdbcSchema schema; // 声明JDBC Schema变量
    if (jsonSchema.sqlDialectFactory == null || jsonSchema.sqlDialectFactory.isEmpty()) { // 如果没有指定SQL方言工厂
      schema = // 创建默认的JDBC Schema
          JdbcSchema.create(parentSchema, jsonSchema.name, dataSource, // 传入父Schema、名称、数据源
              jsonSchema.jdbcCatalog, // JDBC catalog名称(数据库目录)
              jsonSchema.jdbcSchema); // JDBC schema名称(数据库模式)
    } else { // 如果指定了SQL方言工厂
      SqlDialectFactory factory = // 实例化SQL方言工厂
          AvaticaUtils.instantiatePlugin(SqlDialectFactory.class, // 使用Avatica工具实例化
              jsonSchema.sqlDialectFactory); // 使用Schema定义中的方言工厂类名
      schema = // 创建带方言的JDBC Schema
          JdbcSchema.create(parentSchema, jsonSchema.name, dataSource, // 传入父Schema、名称、数据源
              factory, // SQL方言工厂
              jsonSchema.jdbcCatalog, // JDBC catalog名称
              jsonSchema.jdbcSchema); // JDBC schema名称
    }
    final SchemaPlus schemaPlus = parentSchema.add(jsonSchema.name, schema); // 将JDBC Schema添加到父Schema中
    populateSchema(jsonSchema, schemaPlus); // 填充Schema,处理Schema的子元素
  }

  public void visit(JsonMaterialization jsonMaterialization) { // 访问物化视图定义
    try { // 尝试执行可能抛出异常的代码
      final SchemaPlus schema = currentSchema(); // 获取当前Schema
      if (!schema.isMutable()) { // 检查Schema是否可变
        throw new RuntimeException( // 抛出运行时异常
            "Cannot define materialization; parent schema '" // 提示无法定义物化视图
                + currentSchemaName() // 显示当前Schema名称
                + "' is not a SemiMutableSchema"); // 说明父Schema不是半可变Schema
      }
      CalciteSchema calciteSchema = CalciteSchema.from(schema); // 将SchemaPlus转换为CalciteSchema

      final String viewName; // 声明视图名称变量
      final boolean existing; // 声明是否已存在标志
      if (jsonMaterialization.view == null) { // 如果没有指定视图名称
        // If the user did not supply a view name, that means the materialized
        // view is pre-populated. Generate a synthetic view name.
        // 如果用户没有提供视图名称,说明物化视图是预先填充的。生成一个合成的视图名称。
        viewName = "$" + schema.tables().getNames(LikePattern.any()).size(); // 生成以"$"开头的视图名,后跟表数量
        existing = true; // 标记为已存在
      } else { // 如果指定了视图名称
        viewName = jsonMaterialization.view; // 使用指定的视图名称
        existing = false; // 标记为不存在
      }
      List<String> viewPath = calciteSchema.path(viewName); // 获取视图的完整路径
      schema.add(viewName, // 添加物化视图到Schema
          MaterializedViewTable.create(calciteSchema, // 创建物化视图表
              jsonMaterialization.getSql(), // 物化视图的SQL查询语句
              jsonMaterialization.viewSchemaPath, // 视图Schema路径
              viewPath, // 视图路径
              jsonMaterialization.table, // 物化视图对应的表名
              existing)); // 是否已存在标志
    } catch (Exception e) { // 捕获所有异常
      throw new RuntimeException("Error instantiating " + jsonMaterialization, // 抛出运行时异常
          e); // 包装原始异常
    }
  }

  public void visit(JsonLattice jsonLattice) { // 访问Lattice定义(用于物化视图优化的格状结构)
    try { // 尝试执行可能抛出异常的代码
      final SchemaPlus schema = currentSchema(); // 获取当前Schema
      if (!schema.isMutable()) { // 检查Schema是否可变
        throw new RuntimeException("Cannot define lattice; parent schema '" // 抛出运行时异常,提示无法定义Lattice
            + currentSchemaName() // 显示当前Schema名称
            + "' is not a SemiMutableSchema"); // 说明父Schema不是半可变Schema
      }
      CalciteSchema calciteSchema = CalciteSchema.from(schema); // 将SchemaPlus转换为CalciteSchema
      Lattice.Builder latticeBuilder = // 创建Lattice构建器
          Lattice.builder(calciteSchema, jsonLattice.getSql()) // 使用Schema和SQL创建构建器
              .auto(jsonLattice.auto) // 设置是否自动优化
              .algorithm(jsonLattice.algorithm); // 设置优化算法
      if (jsonLattice.rowCountEstimate != null) { // 如果指定了行数估计
        latticeBuilder.rowCountEstimate(jsonLattice.rowCountEstimate); // 设置行数估计值
      }
      if (jsonLattice.statisticProvider != null) { // 如果指定了统计信息提供者
        latticeBuilder.statisticProvider(jsonLattice.statisticProvider); // 设置统计信息提供者
      }
      populateLattice(jsonLattice, latticeBuilder); // 填充Lattice,处理Lattice的子元素(度量、维度等)
      schema.add(jsonLattice.name, latticeBuilder.build()); // 将构建好的Lattice添加到Schema中
    } catch (Exception e) { // 捕获所有异常
      throw new RuntimeException("Error instantiating " + jsonLattice, // 抛出运行时异常
          e); // 包装原始异常
    }
  }

  private void populateLattice(JsonLattice jsonLattice, // 私有方法,填充Lattice的子元素
      Lattice.Builder latticeBuilder) { // 接收Lattice定义和构建器
    assert this.latticeBuilder == null; // 断言当前没有正在构建的Lattice
    this.latticeBuilder = latticeBuilder; // 保存Lattice构建器到成员变量
    jsonLattice.visitChildren(this); // 访问Lattice的所有子元素(度量、Tile等)
    this.latticeBuilder = null; // 清空Lattice构建器引用
  }

  public void visit(JsonCustomTable jsonTable) { // 访问自定义表(使用TableFactory创建的表)
    try { // 尝试执行可能抛出异常的代码
      final SchemaPlus schema = currentMutableSchema("table"); // 获取当前可变的Schema
      final TableFactory tableFactory = // 实例化表工厂
          AvaticaUtils.instantiatePlugin(TableFactory.class, // 使用Avatica工具实例化
              jsonTable.factory); // 使用表定义中的工厂类名
      final Table table = // 创建表实例
          tableFactory.create(schema, jsonTable.name, // 调用工厂的create方法,传入Schema、表名
              operandMap(null, jsonTable.operand), null); // 传入操作数映射和null(未使用的参数)
      for (JsonColumn column : jsonTable.columns) { // 遍历表的所有列定义
        column.accept(this); // 调用列的accept方法,让列访问this(ModelHandler)
      }
      schema.add(jsonTable.name, table); // 将创建的表添加到Schema中
    } catch (Exception e) { // 捕获所有异常
      throw new RuntimeException("Error instantiating " + jsonTable, // 抛出运行时异常
          e); // 包装原始异常
    }
  }

  public void visit(JsonColumn jsonColumn) { // 访问列定义(当前方法为空实现)
    // 列定义的处理逻辑(当前为空,可能在其他地方处理)
  }

  public void visit(JsonView jsonView) { // 访问视图定义
    try { // 尝试执行可能抛出异常的代码
      final SchemaPlus schema = currentMutableSchema("view"); // 获取当前可变的Schema
      final List<String> path = // 获取视图路径
          Util.first(jsonView.path, currentSchemaPath()); // 优先使用视图定义的路径,否则使用当前Schema路径
      final List<String> viewPath = ImmutableList.<String>builder().addAll(path) // 构建视图完整路径
          .add(jsonView.name).build(); // 添加视图名称并构建不可变列表
      schema.add(jsonView.name, // 添加视图到Schema
          ViewTable.viewMacro(schema, jsonView.getSql(), path, viewPath, // 创建视图宏
              jsonView.modifiable)); // 是否可修改标志
    } catch (Exception e) { // 捕获所有异常
      throw new RuntimeException("Error instantiating " + jsonView, // 抛出运行时异常
          e); // 包装原始异常
    }
  }

  private List<String> currentSchemaPath() { // 私有方法,获取当前Schema的路径
    return Collections.singletonList(currentSchemaName()); // 返回包含当前Schema名称的单元素列表
  }

  private Pair<? extends @Nullable String, SchemaPlus> nameAndSchema() { // 私有方法,获取当前Schema的名称和对象
    return schemaStack.getFirst(); // 返回栈顶元素(当前Schema的名称和对象)
  }

  private SchemaPlus currentSchema() { // 私有方法,获取当前Schema对象
    return nameAndSchema().right; // 返回键值对中的Schema对象
  }

  private String currentSchemaName() { // 私有方法,获取当前Schema名称
    return requireNonNull(nameAndSchema().left, "currentSchema.name"); // 返回键值对中的名称,确保不为null
  }

  private SchemaPlus currentMutableSchema(String elementType) { // 私有方法,获取当前可变的Schema
    final SchemaPlus schema = currentSchema(); // 获取当前Schema
    if (!schema.isMutable()) { // 检查Schema是否可变
      throw new RuntimeException("Cannot define " + elementType // 抛出运行时异常
          + "; parent schema '" + schema.getName() + "' is not mutable"); // 提示无法定义元素,父Schema不可变
    }
    return schema; // 返回可变的Schema
  }

  public @Nullable String defaultSchemaName() { // 公共方法,获取默认Schema名称
    return this.defaultSchemaName; // 返回默认Schema名称
  }

  public void visit(final JsonType jsonType) { // 访问类型定义
    try { // 尝试执行可能抛出异常的代码
      final SchemaPlus schema = currentMutableSchema("type"); // 获取当前可变的Schema
      schema.add(jsonType.name, typeFactory -> { // 添加类型到Schema,使用lambda表达式创建类型
        if (jsonType.type != null) { // 如果类型定义了基本类型
          return typeFactory.createSqlType( // 创建SQL类型
              requireNonNull(SqlTypeName.get(jsonType.type), // 获取SQL类型名称,确保不为null
                  () -> "SqlTypeName.get for " + jsonType.type)); // 错误消息
        } else { // 如果类型定义了复合类型(有属性)
          final RelDataTypeFactory.Builder builder = typeFactory.builder(); // 创建类型构建器
          for (JsonTypeAttribute jsonTypeAttribute : jsonType.attributes) { // 遍历类型的所有属性
            final SqlTypeName typeName = // 获取属性的类型名称
                requireNonNull(SqlTypeName.get(jsonTypeAttribute.type), // 获取SQL类型名称,确保不为null
                    () -> "SqlTypeName.get for " + jsonTypeAttribute.type); // 错误消息
            RelDataType type = typeFactory.createSqlType(typeName); // 尝试创建SQL类型
            if (type == null) { // 如果类型为null
              type = // 从当前Schema中查找类型
                  requireNonNull(currentSchema().getType(jsonTypeAttribute.type), // 获取类型,确保不为null
                      () -> "type " + jsonTypeAttribute.type // 错误消息
                          + " is not found in schema " + currentSchemaName()) // 说明类型未找到
                      .apply(typeFactory); // 应用类型工厂
            }
            builder.add(jsonTypeAttribute.name, type); // 将属性名和类型添加到构建器
          }
          return builder.build(); // 构建并返回复合类型
        }
      });
    } catch (Exception e) { // 捕获所有异常
      throw new RuntimeException("Error instantiating " + jsonType, // 抛出运行时异常
          e); // 包装原始异常
    }
  }

  public void visit(JsonFunction jsonFunction) { // 访问函数定义
    // "name" is not required - a class can have several functions
    // "name"不是必需的 - 一个类可以有多个函数
    try { // 尝试执行可能抛出异常的代码
      final SchemaPlus schema = currentMutableSchema("function"); // 获取当前可变的Schema
      final List<String> path = // 获取函数路径
          Util.first(jsonFunction.path, currentSchemaPath()); // 优先使用函数定义的路径,否则使用当前Schema路径
      addFunctions(schema, jsonFunction.name, path, jsonFunction.className, // 调用addFunctions方法添加函数
          jsonFunction.methodName, false); // 不转大写
    } catch (Exception e) { // 捕获所有异常
      throw new RuntimeException("Error instantiating " + jsonFunction, // 抛出运行时异常
          e); // 包装原始异常
    }
  }

  public void visit(JsonMeasure jsonMeasure) { // 访问Lattice度量定义
    requireNonNull(latticeBuilder, "latticeBuilder"); // 确保latticeBuilder不为null
    final boolean distinct = false; // no distinct field in JsonMeasure.yet // 是否distinct标志,当前JsonMeasure不支持distinct字段
    final Lattice.Measure measure = // 解析度量
        latticeBuilder.resolveMeasure(jsonMeasure.agg, distinct, // 解析聚合函数和distinct标志
            jsonMeasure.args); // 解析度量参数
    if (tileBuilder != null) { // 如果正在构建Tile
      tileBuilder.addMeasure(measure); // 将度量添加到Tile构建器
    } else if (latticeBuilder != null) { // 如果正在构建Lattice
      latticeBuilder.addMeasure(measure); // 将度量添加到Lattice构建器
    } else { // 如果都没有
      throw new AssertionError("nowhere to put measure"); // 抛出断言错误,没有地方放置度量
    }
  }

  public void visit(JsonTile jsonTile) { // 访问Lattice Tile定义(瓦片,用于物化视图的分区)
    assert tileBuilder == null; // 断言当前没有正在构建的Tile
    Lattice.TileBuilder tileBuilder = this.tileBuilder = Lattice.Tile.builder(); // 创建Tile构建器并保存到成员变量
    for (JsonMeasure jsonMeasure : jsonTile.measures) { // 遍历Tile的所有度量
      jsonMeasure.accept(this); // 调用度量的accept方法,让度量访问this(ModelHandler)
    }
    Lattice.Builder latticeBuilder = requireNonNull(this.latticeBuilder, "latticeBuilder"); // 获取Lattice构建器,确保不为null
    for (Object dimension : jsonTile.dimensions) { // 遍历Tile的所有维度
      final Lattice.Column column = latticeBuilder.resolveColumn(dimension); // 解析维度为Lattice列
      tileBuilder.addDimension(column); // 将维度添加到Tile构建器
    }
    latticeBuilder.addTile(tileBuilder.build()); // 将构建好的Tile添加到Lattice构建器
    this.tileBuilder = null; // 清空Tile构建器引用
  }

  /** Extra operands automatically injected into a
   * {@link JsonCustomSchema#operand}, as extra context for the adapter. */
  /** 自动注入到自定义Schema操作数中的额外操作数,作为适配器的额外上下文。 */
  public enum ExtraOperand { // 额外操作数枚举
    /** URI of model, e.g. "target/test-classes/model.json",
     * "http://localhost/foo/bar.json", "inline:{...}",
     * "target/test-classes/model.yaml",
     * "http://localhost/foo/bar.yaml", "inline:..."
     * */
    MODEL_URI("modelUri"), // 模型URI,可以是文件路径、URL或内联字符串

    /** Base directory from which to read files. */
    BASE_DIRECTORY("baseDirectory"), // 基础目录,用于读取文件的根目录

    /** Tables defined in this schema. */
    TABLES("tables"); // Schema中定义的表

    public final String camelName; // 驼峰命名的操作数名称

    ExtraOperand(String camelName) { // 枚举构造方法
      this.camelName = camelName; // 保存驼峰命名的名称
    }
  }
}