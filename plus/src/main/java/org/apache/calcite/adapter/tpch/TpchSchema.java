// Apache许可证头声明，说明该代码遵循Apache 2.0许可证
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
// 声明当前类所在的包路径，属于Calcite的TPC-H适配器模块
package org.apache.calcite.adapter.tpch;

// 导入Calcite核心抽象类，用于实现可查询的表
import org.apache.calcite.adapter.java.AbstractQueryableTable;
// 导入枚举器接口，用于遍历数据集
import org.apache.calcite.linq4j.Enumerator;
// 导入LINQ工具类，提供枚举器创建等功能
import org.apache.calcite.linq4j.Linq4j;
// 导入查询提供者接口，用于执行LINQ查询
import org.apache.calcite.linq4j.QueryProvider;
// 导入可查询接口，表示可进行LINQ查询的数据源
import org.apache.calcite.linq4j.Queryable;
// 导入关系数据类型接口，表示Calcite中的数据类型
import org.apache.calcite.rel.type.RelDataType;
// 导入关系数据类型工厂接口，用于创建数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory;
// 导入SchemaPlus接口，表示可扩展的Schema
import org.apache.calcite.schema.SchemaPlus;
// 导入Table接口，表示数据库表
import org.apache.calcite.schema.Table;
// 导入抽象Schema基类，提供Schema的基本实现
import org.apache.calcite.schema.impl.AbstractSchema;
// 导入抽象表可查询基类，用于实现可查询的表
import org.apache.calcite.schema.impl.AbstractTableQueryable;

// 导入Google Guava的不可变Map构建器
import com.google.common.collect.ImmutableMap;

// 导入Presto SQL TPC-H库的列定义
import io.prestosql.tpch.TpchColumn;
// 导入Presto SQL TPC-H库的实体接口
import io.prestosql.tpch.TpchEntity;
// 导入Presto SQL TPC-H库的表定义
import io.prestosql.tpch.TpchTable;

// 导入Java SQL日期类
import java.sql.Date;
// 导入Java列表接口
import java.util.List;
// 导入Java本地化类，用于大小写转换
import java.util.Locale;
// 导入Java Map接口
import java.util.Map;

// 静态导入Objects的requireNonNull方法，用于参数校验
import static java.util.Objects.requireNonNull;

// 类文档注释：说明这是一个提供TPC-H表的Schema，根据特定的缩放因子填充数据
// TPC-H是数据库性能测试标准，包含8个表（REGION、NATION、SUPPLIER、CUSTOMER、PART、PARTSUPP、ORDERS、LINEITEM）
// scaleFactor控制数据量大小，通常为0.01、0.1、1、10等
/** Schema that provides TPC-H tables, populated according to a
 * particular scale factor. */
// TpchSchema类继承AbstractSchema，实现了TPC-H测试数据集的Schema
// 该类负责创建和管理所有TPC-H标准表，支持数据分片和列名前缀
public class TpchSchema extends AbstractSchema {
  // 成员变量：scaleFactor表示TPC-H数据集的缩放因子，控制生成的数据量大小
  // 例如：scaleFactor=1表示1GB数据，scaleFactor=10表示10GB数据
  private final double scaleFactor;
  // 成员变量：part表示当前分片的编号，用于分布式场景下数据分片
  // 取值范围：0到partCount-1
  private final int part;
  // 成员变量：partCount表示总分片数，用于将数据均匀分布到多个分片中
  // 例如：partCount=4表示数据分为4个分片
  private final int partCount;
  // 成员变量：columnPrefix表示是否为列名添加表名前缀
  // true时，LINEITEM表的L_ORDERKEY列会变成L_L_ORDERKEY，避免多表连接时列名冲突
  private final boolean columnPrefix;
  // 成员变量：tableMap存储所有TPC-H表的映射关系，键为表名（大写），值为Table对象
  // 包含8个标准表：LINEITEM、CUSTOMER、SUPPLIER、PARTSUPP、PART、ORDERS、NATION、REGION
  private final ImmutableMap<String, Table> tableMap;
  // 成员变量：columnPrefixes存储每个表的列名前缀映射
  // 例如：LINEITEM表的前缀是"L_"，CUSTOMER表的前缀是"C_"
  private final ImmutableMap<String, String> columnPrefixes;

  // 构造方法：创建TPC-H Schema实例
  // 参数说明：
  //   - scaleFactor：数据缩放因子，控制生成数据量的大小
  //   - part：当前分片编号，从0开始
  //   - partCount：总分片数
  //   - columnPrefix：是否为列名添加表名前缀
  public TpchSchema(double scaleFactor, int part, int partCount,
      boolean columnPrefix) {
    // 初始化缩放因子成员变量
    this.scaleFactor = scaleFactor;
    // 初始化当前分片编号成员变量
    this.part = part;
    // 初始化总分片数成员变量
    this.partCount = partCount;
    // 初始化列名前缀标志成员变量
    this.columnPrefix = columnPrefix;

    // 创建不可变Map构建器，用于构建表名到Table对象的映射
    final ImmutableMap.Builder<String, Table> builder = ImmutableMap.builder();
    // 遍历TPC-H标准定义的所有表（共8个表）
    for (TpchTable<?> tpchTable : TpchTable.getTables()) {
      // 将表名转换为大写（确保大小写不敏感），并创建对应的TpchQueryableTable对象
      // TpchQueryableTable是内部类，实现了可查询表的功能
      builder.put(tpchTable.getTableName().toUpperCase(Locale.ROOT),
          new TpchQueryableTable(tpchTable));
    }
    // 构建不可变的表映射Map
    this.tableMap = builder.build();

    // 初始化列名前缀映射，为每个TPC-H表定义标准前缀
    // 这些前缀遵循TPC-H规范的命名约定
    this.columnPrefixes = ImmutableMap.<String, String>builder()
        // LINEITEM表的前缀为"L_"，包含订单明细信息
        .put("LINEITEM", "L_")
        // CUSTOMER表的前缀为"C_"，包含客户信息
        .put("CUSTOMER", "C_")
        // SUPPLIER表的前缀为"S_"，包含供应商信息
        .put("SUPPLIER", "S_")
        // PARTSUPP表的前缀为"PS_"，包含零件供应商信息
        .put("PARTSUPP", "PS_")
        // PART表的前缀为"P_"，包含零件信息
        .put("PART", "P_")
        // ORDERS表的前缀为"O_"，包含订单信息
        .put("ORDERS", "O_")
        // NATION表的前缀为"N_"，包含国家信息
        .put("NATION", "N_")
        // REGION表的前缀为"R_"，包含地区信息
        .put("REGION", "R_")
        .build();
  }

  // 重写父类方法：获取Schema中所有表的映射
  // 返回值：包含所有TPC-H表的不可变Map，键为表名，值为Table对象
  // 该方法被Calcite框架调用，用于发现Schema中的所有表
  @Override protected Map<String, Table> getTableMap() {
    // 返回已构建好的表映射Map
    return tableMap;
  }

  // 内部类文档注释：定义TPC-H Schema中的表
  // 这是一个泛型类，E表示实体类型，必须继承TpchEntity
  // TpchEntity是Presto TPC-H库中所有实体的基类
  /** Definition of a table in the TPC-H schema.
   *
   * @param <E> entity type */
  // TpchQueryableTable内部类，继承AbstractQueryableTable，表示一个可查询的TPC-H表
  // 该类实现了将TPC-H数据转换为Calcite可查询格式的功能
  private class TpchQueryableTable<E extends TpchEntity>
      extends AbstractQueryableTable {
    // 成员变量：tpchTable存储TPC-H表的元数据定义
    // 包含表名、列定义、数据生成器等信息
    private final TpchTable<E> tpchTable;

    // 构造方法：创建TPC-H可查询表实例
    // 参数：tpchTable - TPC-H表的元数据定义
    TpchQueryableTable(TpchTable<E> tpchTable) {
      // 调用父类构造方法，指定行类型为Object[]数组
      // 每一行数据表示为一个Object数组，数组元素对应各列的值
      super(Object[].class);
      // 保存TPC-H表元数据定义
      this.tpchTable = tpchTable;
    }

    // 重写父类方法：将表转换为可查询对象（Queryable）
    // 参数说明：
    //   - queryProvider：查询提供者，用于执行LINQ查询
    //   - schema：所属的Schema对象
    //   - tableName：表名
    // 返回值：可查询对象，支持LINQ查询操作
    @Override public <T> Queryable<T> asQueryable(final QueryProvider queryProvider,
        final SchemaPlus schema, final String tableName) {
      // 创建匿名内部类，继承AbstractTableQueryable，实现数据枚举功能
      //noinspection unchecked 表示忽略未检查的类型转换警告
      //noinspection unchecked
      return (Queryable) new AbstractTableQueryable<Object[]>(queryProvider,
          schema, this, tableName) {
        // 重写enumerator方法，创建数据枚举器
        // 枚举器用于逐行遍历表数据
        @Override public Enumerator<Object[]> enumerator() {
          // 创建TPC-H数据生成器的枚举器
          // createGenerator根据缩放因子和分片信息生成数据
          // Linq4j.iterableEnumerator将Iterable转换为Enumerator接口
          final Enumerator<E> iterator =
              Linq4j.iterableEnumerator(
                  tpchTable.createGenerator(scaleFactor, part, partCount));
          // 创建匿名内部类，实现Object[]类型的枚举器
          // 该枚举器负责将TPC-H实体对象转换为Object[]数组
          return new Enumerator<Object[]>() {
            // 重写current方法，获取当前行的数据
            // 返回值：Object数组，包含当前行所有列的值
            @Override public Object[] current() {
              // 获取表的所有列定义
              final List<TpchColumn<E>> columns = tpchTable.getColumns();
              // 创建Object数组，大小等于列数
              final Object[] objects = new Object[columns.size()];
              // 初始化列索引
              int i = 0;
              // 遍历所有列，将每列的值提取到数组中
              for (TpchColumn<E> column : columns) {
                // 调用value方法获取当前行该列的值，并存入数组
                objects[i++] = value(column, iterator.current());
              }
              // 返回包含当前行所有列值的数组
              return objects;
            }

            // 私有辅助方法：从实体对象中提取指定列的值
            // 参数说明：
            //   - tpchColumn：列定义，包含列名和类型信息
            //   - current：当前实体对象
            // 返回值：该列的值，根据列类型转换为相应的Java对象
            private Object value(TpchColumn<E> tpchColumn, E current) {
              // 获取列的真实Java类型
              final Class<?> type = realType(tpchColumn);
              // 根据列类型调用相应的getter方法获取值
              if (type == String.class) {
                // 字符串类型：调用getString方法
                return tpchColumn.getString(current);
              } else if (type == Double.class) {
                // 双精度浮点类型：调用getDouble方法
                return tpchColumn.getDouble(current);
              } else if (type == Date.class) {
                // 日期类型：先获取字符串，再转换为java.sql.Date
                return Date.valueOf(tpchColumn.getString(current));
              } else if (type == Integer.class) {
                // 整数类型：调用getInteger方法
                return tpchColumn.getInteger(current);
              } else if (type == Long.class) {
                // 标识符类型：调用getIdentifier方法
                return tpchColumn.getIdentifier(current);
              } else {
                // 未知类型：抛出断言错误
                throw new AssertionError(type);
              }
            }

            // 重写moveNext方法，移动到下一行
            // 返回值：true表示还有下一行，false表示已到达末尾
            @Override public boolean moveNext() {
              // 委托给底层迭代器的moveNext方法
              return iterator.moveNext();
            }

            // 重写reset方法，重置枚举器到初始位置
            @Override public void reset() {
              // 委托给底层迭代器的reset方法
              iterator.reset();
            }

            // 重写close方法，关闭枚举器并释放资源
            @Override public void close() {
              // 当前实现为空，因为TPC-H生成器不需要显式关闭
            }
          };
        }
      };
    }

    // 重写父类方法：获取表的行类型（RelDataType）
    // RelDataType是Calcite中表示关系数据类型的接口，包含表的结构信息
    // 参数：typeFactory - 数据类型工厂，用于创建数据类型
    // 返回值：表的行类型，包含所有列的名称和类型
    @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) {
      // 创建数据类型构建器，用于构建行类型
      final RelDataTypeFactory.Builder builder = typeFactory.builder();
      // 列名前缀变量，根据配置决定是否添加
      final String prefix;
      // 判断是否需要为列名添加表名前缀
      if (columnPrefix) {
        // 获取表名并转换为大写
        final String t = tpchTable.getTableName().toUpperCase(Locale.ROOT);
        // 从columnPrefixes映射中获取该表的前缀
        // requireNonNull确保前缀存在，否则抛出异常
        prefix =
            requireNonNull(columnPrefixes.get(t),
                () -> "prefix for table " + t);
      } else {
        // 不使用前缀，设置为空字符串
        prefix = "";
      }
      // 遍历表的所有列，为每列添加到行类型中
      for (TpchColumn<E> column : tpchTable.getColumns()) {
        // 构建列名：前缀+原始列名，并转换为大写
        final String c = (prefix + column.getColumnName())
            .toUpperCase(Locale.ROOT);
        // 将列名和类型添加到构建器中
        // realType方法将TPC-H列类型转换为Java类型
        builder.add(c, typeFactory.createJavaType(realType(column)));
      }
      // 构建并返回行类型
      return builder.build();
    }

    // 私有辅助方法：获取TPC-H列的真实Java类型
    // 该方法负责将TPC-H的类型系统映射到Java类型
    // 参数：column - TPC-H列定义
    // 返回值：对应的Java类型Class对象
    private Class<?> realType(TpchColumn<E> column) {
      // 特殊处理：如果列名以"date"结尾，则认为是日期类型
      // 这是一种启发式规则，因为TPC-H规范中日期列都包含"date"关键字
      if (column.getColumnName().endsWith("date")) {
        return java.sql.Date.class;
      }
      // 根据TPC-H列的基础类型进行映射
      switch (column.getType().getBase()) {
      // 日期类型映射到java.sql.Date
      case DATE:
        return java.sql.Date.class;
      // 双精度浮点类型映射到java.lang.Double
      case DOUBLE:
        return Double.class;
      // 整数类型映射到java.lang.Integer
      case INTEGER:
        return Integer.class;
      // 标识符类型映射到java.lang.Long
      // TPC-H中的标识符是长整型，用于主键和外键
      case IDENTIFIER:
        return Long.class;
      // 可变字符类型映射到java.lang.String
      case VARCHAR:
        return String.class;
      // 未知类型抛出断言错误
      default:
        throw new AssertionError(column.getType());
      }
    }
  }
}
