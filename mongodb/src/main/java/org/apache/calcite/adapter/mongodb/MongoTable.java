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
package org.apache.calcite.adapter.mongodb; // 声明包名，表示此类属于Calcite的MongoDB适配器包

import org.apache.calcite.adapter.java.AbstractQueryableTable; // 导入抽象可查询表基类，提供LINQ查询能力
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入抽象可枚举类，用于实现可枚举的数据源
import org.apache.calcite.linq4j.Enumerable; // 导入可枚举接口，表示可以被枚举的数据集合
import org.apache.calcite.linq4j.Enumerator; // 导入枚举器接口，用于遍历数据集合
import org.apache.calcite.linq4j.QueryProvider; // 导入查询提供者接口，提供LINQ查询执行能力
import org.apache.calcite.linq4j.Queryable; // 导入可查询接口，表示可以被查询的数据源
import org.apache.calcite.linq4j.function.Function1; // 导入函数式接口，表示接受一个参数并返回结果的函数
import org.apache.calcite.plan.RelOptCluster; // 导入关系表达式优化集群，包含优化器的共享状态
import org.apache.calcite.plan.RelOptTable; // 导入关系优化表，表示优化过程中的表
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，表示关系代数树中的一个节点
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示数据的类型信息
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建数据类型
import org.apache.calcite.schema.SchemaPlus; // 导入模式增强接口，表示Calcite中的模式（Schema）
import org.apache.calcite.schema.TranslatableTable; // 导入可转换表接口，表示可以转换为关系节点的表
import org.apache.calcite.schema.impl.AbstractTableQueryable; // 导入抽象表可查询类，提供表查询的基础实现
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举，定义标准SQL数据类型
import org.apache.calcite.util.Util; // 导入工具类，提供各种实用方法

import com.mongodb.client.FindIterable; // 导入MongoDB查找结果可迭代接口，表示查询结果集
import com.mongodb.client.MongoCollection; // 导入MongoDB集合接口，表示MongoDB中的一个集合（类似表）
import com.mongodb.client.MongoDatabase; // 导入MongoDB数据库接口，表示MongoDB中的一个数据库

import org.bson.BsonDocument; // 导入BSON文档类，表示MongoDB的二进制JSON文档
import org.bson.Document; // 导入Document类，表示MongoDB的文档对象
import org.bson.conversions.Bson; // 导入Bson接口，表示MongoDB的二进制JSON对象

import java.util.ArrayList; // 导入ArrayList类，提供动态数组实现
import java.util.Iterator; // 导入迭代器接口，用于遍历集合
import java.util.List; // 导入List接口，表示有序集合
import java.util.Map; // 导入Map接口，表示键值对映射

/**
 * Table based on a MongoDB collection. // 基于MongoDB集合的表实现，将MongoDB集合映射为Calcite中的表
 * 
 * MongoTable是Calcite适配器模式中的关键类，它将MongoDB的集合（Collection）抽象为Calcite的表（Table）
 * 这个类实现了TranslatableTable接口，使得MongoDB集合可以被转换为关系表达式树中的节点
 * 通过这个类，Calcite可以将SQL查询转换为MongoDB的原生查询（find或aggregate操作）
 * 
 * 主要功能：
 * 1. 定义MongoDB集合的行类型（RowType），使用Map类型来存储动态的MongoDB文档
 * 2. 提供Queryable接口，支持LINQ风格的查询
 * 3. 实现toRel方法，将表转换为MongoTableScan关系节点
 * 4. 提供find和aggregate方法，执行MongoDB的原生查询操作
 * 5. 内部类MongoQueryable提供具体的查询执行逻辑
 */
public class MongoTable extends AbstractQueryableTable // MongoTable继承自AbstractQueryableTable，获得LINQ查询能力
    implements TranslatableTable { // 实现TranslatableTable接口，支持转换为关系节点
  private final String collectionName; // 成员变量：MongoDB集合名称，用于标识这个表对应的MongoDB集合

  /** Creates a MongoTable. */ // 创建MongoTable实例的构造方法注释
  MongoTable(String collectionName) { // 构造方法：接收MongoDB集合名称作为参数
    super(Object[].class); // 调用父类构造方法，指定行类型为Object[]数组类型
    this.collectionName = collectionName; // 将传入的集合名称赋值给成员变量
  }

  @Override public String toString() { // 重写toString方法，提供对象的字符串表示
    return "MongoTable {" + collectionName + "}"; // 返回包含集合名称的格式化字符串
  }

  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 实现接口方法：获取表的行类型
    final RelDataType mapType = // 创建一个Map类型，用于存储MongoDB文档的键值对
        typeFactory.createMapType( // 创建Map类型，键为VARCHAR类型，值为可空的ANY类型
            typeFactory.createSqlType(SqlTypeName.VARCHAR), // Map的键类型：VARCHAR字符串类型
            typeFactory.createTypeWithNullability( // Map的值类型：ANY类型且允许为空
                typeFactory.createSqlType(SqlTypeName.ANY), true)); // createSqlType创建ANY类型，createTypeWithNullability设置为可空
    return typeFactory.builder().add("_MAP", mapType).build(); // 使用类型构建器创建行类型，添加一个名为"_MAP"的Map类型字段
  }

  @Override public <T> Queryable<T> asQueryable(QueryProvider queryProvider, // 实现接口方法：将表转换为可查询对象
      SchemaPlus schema, String tableName) { // 参数：查询提供者、模式、表名
    return new MongoQueryable<>(queryProvider, schema, this, tableName); // 创建并返回MongoQueryable实例，包装当前表对象
  }

  @Override public RelNode toRel( // 实现接口方法：将表转换为关系表达式节点
      RelOptTable.ToRelContext context, // 转换上下文，包含优化集群等信息
      RelOptTable relOptTable) { // 优化表对象
    final RelOptCluster cluster = context.getCluster(); // 从上下文中获取优化集群，用于创建关系节点
    return new MongoTableScan(cluster, cluster.traitSetOf(MongoRel.CONVENTION), // 创建MongoTableScan节点，使用MongoDB约定特征集
        relOptTable, this, null); // 参数：集群、特征集、优化表、当前MongoTable对象、过滤器（null表示无过滤）
  }

  /** Executes a "find" operation on the underlying collection. // 执行MongoDB的find查询操作，用于检索符合条件的文档
   *
   * <p>For example, // 使用示例：
   * <code>zipsTable.find("{state: 'OR'}", "{city: 1, zipcode: 1}")</code> // 查找state为OR的文档，只返回city和zipcode字段
   *
   * @param mongoDb MongoDB connection // 参数：MongoDB数据库连接对象
   * @param filterJson Filter JSON string, or null // 参数：过滤条件的JSON字符串，null表示不过滤
   * @param projectJson Project JSON string, or null // 参数：投影条件的JSON字符串，null表示返回所有字段
   * @param fields List of fields to project; or null to return map // 参数：要投影的字段列表，null表示返回完整的Map
   * @return Enumerator of results // 返回值：结果枚举器，用于遍历查询结果
   */
  private Enumerable<Object> find(MongoDatabase mongoDb, String filterJson, // 私有方法：执行MongoDB的find操作
      String projectJson, List<Map.Entry<String, Class>> fields) { // 参数：数据库连接、过滤JSON、投影JSON、字段列表
    final MongoCollection collection = // 获取MongoDB集合对象，使用collectionName指定集合名称
        mongoDb.getCollection(collectionName); // getCollection方法返回指定名称的MongoDB集合
    final Bson filter = // 创建Bson过滤对象，如果filterJson为null则不过滤
        filterJson == null ? null : BsonDocument.parse(filterJson); // 三元运算符：null返回null，否则解析JSON字符串为BsonDocument
    final Bson project = // 创建Bson投影对象，如果projectJson为null则不投影
        projectJson == null ? null : BsonDocument.parse(projectJson); // 三元运算符：null返回null，否则解析JSON字符串为BsonDocument
    final Function1<Document, Object> getter = MongoEnumerator.getter(fields); // 创建getter函数，用于从MongoDB文档中提取指定字段
    return new AbstractEnumerable<Object>() { // 返回一个匿名AbstractEnumerable子类，提供枚举能力
      @Override public Enumerator<Object> enumerator() { // 实现enumerator方法，创建枚举器
        @SuppressWarnings("unchecked") final FindIterable<Document> cursor = // 执行MongoDB的find查询，返回可迭代结果
            collection.find(filter).projection(project); // find方法执行查询，projection方法设置投影条件
        return new MongoEnumerator(cursor.iterator(), getter); // 创建MongoEnumerator对象，包装MongoDB游标迭代器和getter函数
      }
    };
  }

  /** Executes an "aggregate" operation on the underlying collection. // 执行MongoDB的聚合操作，用于复杂的数据处理和分析
   *
   * <p>For example: // 使用示例：
   * <code>zipsTable.aggregate( // 对zips集合执行聚合操作
   * "{$filter: {state: 'OR'}", // 第一步：过滤state为OR的文档
   * "{$group: {_id: '$city', c: {$sum: 1}, p: {$sum: '$pop'}}}")</code> // 第二步：按city分组，计算每个城市的文档数和人口总和
   *
   * @param mongoDb MongoDB connection // 参数：MongoDB数据库连接对象
   * @param fields List of fields to project; or null to return map // 参数：要投影的字段列表，null表示返回完整的Map
   * @param operations One or more JSON strings // 参数：聚合操作的JSON字符串列表，包含一个或多个聚合阶段
   * @return Enumerator of results // 返回值：结果枚举器，用于遍历聚合结果
   */
  private Enumerable<Object> aggregate(final MongoDatabase mongoDb, // 私有方法：执行MongoDB的aggregate操作
      final List<Map.Entry<String, Class>> fields, // 参数：字段列表，用于结果投影
      final List<String> operations) { // 参数：聚合操作列表，包含多个聚合阶段的JSON字符串
    final List<Bson> list = new ArrayList<>(); // 创建Bson列表，用于存储解析后的聚合操作
    for (String operation : operations) { // 遍历操作列表，逐个解析JSON字符串
      list.add(BsonDocument.parse(operation)); // 将JSON字符串解析为BsonDocument并添加到列表中
    }
    final Function1<Document, Object> getter = // 创建getter函数，用于从聚合结果文档中提取指定字段
        MongoEnumerator.getter(fields); // MongoEnumerator.getter根据字段列表创建相应的提取函数
    return new AbstractEnumerable<Object>() { // 返回一个匿名AbstractEnumerable子类，提供枚举能力
      @Override public Enumerator<Object> enumerator() { // 实现enumerator方法，创建枚举器
        final Iterator<Document> resultIterator; // 声明结果迭代器变量
        try { // try-catch块，捕获聚合操作可能抛出的异常
          resultIterator = mongoDb.getCollection(collectionName) // 获取MongoDB集合并执行聚合操作
              .aggregate(list).iterator(); // aggregate方法执行聚合，iterator方法返回结果迭代器
        } catch (Exception e) { // 捕获异常
          throw new RuntimeException("While running MongoDB query " // 抛出运行时异常，包含错误信息和原始异常
              + Util.toString(operations, "[", ",\n", "]"), e); // Util.toString将操作列表格式化为字符串
        }
        return new MongoEnumerator(resultIterator, getter); // 创建MongoEnumerator对象，包装结果迭代器和getter函数
      }
    };
  }

  /** Implementation of {@link org.apache.calcite.linq4j.Queryable} based on // MongoQueryable是Queryable接口的实现，基于MongoTable
   * a {@link org.apache.calcite.adapter.mongodb.MongoTable}. // 提供对MongoDB表的LINQ查询能力
   *
   * @param <T> element type */ // 泛型参数T：元素类型，表示查询结果的类型
  public static class MongoQueryable<T> extends AbstractTableQueryable<T> { // MongoQueryable继承自AbstractTableQueryable，提供表查询的基础实现
    MongoQueryable(QueryProvider queryProvider, SchemaPlus schema, // 构造方法：初始化MongoQueryable对象
        MongoTable table, String tableName) { // 参数：查询提供者、模式、MongoTable对象、表名
      super(queryProvider, schema, table, tableName); // 调用父类构造方法，初始化基础属性
    }

    @Override public Enumerator<T> enumerator() { // 实现接口方法：创建枚举器，用于遍历查询结果
      //noinspection unchecked // 忽略未检查的类型转换警告
      final Enumerable<T> enumerable = // 调用MongoTable的find方法，执行无过滤、无投影的查询
          (Enumerable<T>) getTable().find(getMongoDb(), null, null, null); // 强制类型转换，所有参数为null表示查询全部数据
      return enumerable.enumerator(); // 从enumerable对象获取枚举器并返回
    }

    private MongoDatabase getMongoDb() { // 私有方法：获取MongoDB数据库连接
      return schema.unwrap(MongoSchema.class).mongoDb; // 从schema中解包获取MongoSchema对象，然后获取其mongoDb属性
    }

    private MongoTable getTable() { // 私有方法：获取MongoTable对象
      return (MongoTable) table; // 将父类的table属性强制转换为MongoTable类型
    }

    /** Called via code-generation. // 通过代码生成调用此方法，Calcite会在运行时生成调用此方法的代码
     *
     * @see org.apache.calcite.adapter.mongodb.MongoMethod#MONGO_QUERYABLE_AGGREGATE // 参见MongoMethod枚举中的聚合方法定义
     */
    @SuppressWarnings("UnusedDeclaration") // 抑制未使用声明的警告，因为此方法通过代码生成调用
    public Enumerable<Object> aggregate(List<Map.Entry<String, Class>> fields, // 公共方法：执行MongoDB聚合操作
        List<String> operations) { // 参数：字段列表、聚合操作列表
      return getTable().aggregate(getMongoDb(), fields, operations); // 调用MongoTable的aggregate方法执行聚合
    }

    /** Called via code-generation. // 通过代码生成调用此方法，Calcite会在运行时生成调用此方法的代码
     *
     * @param filterJson Filter document // 参数：过滤文档的JSON字符串
     * @param projectJson Projection document // 参数：投影文档的JSON字符串
     * @param fields List of expected fields (and their types) // 参数：期望的字段列表及其类型
     * @return result of mongo query // 返回值：MongoDB查询结果
     *
     * @see org.apache.calcite.adapter.mongodb.MongoMethod#MONGO_QUERYABLE_FIND // 参见MongoMethod枚举中的find方法定义
     */
    @SuppressWarnings("UnusedDeclaration") // 抑制未使用声明的警告，因为此方法通过代码生成调用
    public Enumerable<Object> find(String filterJson, // 公共方法：执行MongoDB find操作
        String projectJson, List<Map.Entry<String, Class>> fields) { // 参数：过滤JSON、投影JSON、字段列表
      return getTable().find(getMongoDb(), filterJson, projectJson, fields); // 调用MongoTable的find方法执行查询
    }
  }
}
