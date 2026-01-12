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
 */ // Apache许可证声明，指定代码的使用权限和限制
package org.apache.calcite.test.schemata.countries; // 包声明，该类属于testkit模块的countries模式

import org.apache.calcite.DataContext; // 导入DataContext接口，用于提供查询执行时的上下文信息（如用户会话、时间等）
import org.apache.calcite.config.CalciteConnectionConfig; // 导入Calcite连接配置接口，用于获取Calcite特定的连接配置
import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口，表示可枚举的集合，支持LINQ风格的查询操作
import org.apache.calcite.linq4j.Linq4j; // 导入Linq4j工具类，提供将Java集合转换为Enumerable的方法
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系数据类型（即表的结构）
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory工厂接口，用于创建关系数据类型
import org.apache.calcite.schema.ScannableTable; // 导入ScannableTable接口，表示可以被扫描的表（可迭代的数据源）
import org.apache.calcite.schema.Schema; // 导入Schema接口，表示数据库模式（包含表、视图等）
import org.apache.calcite.schema.Statistic; // 导入Statistic接口，表示表的统计信息（如行数、唯一键等）
import org.apache.calcite.schema.Statistics; // 导入Statistics工具类，用于创建Statistic对象
import org.apache.calcite.sql.SqlCall; // 导入SqlCall类，表示SQL函数调用表达式
import org.apache.calcite.sql.SqlNode; // 导入SqlNode接口，表示SQL语法树的抽象节点
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，定义SQL标准数据类型名称
import org.apache.calcite.util.ImmutableBitSet; // 导入ImmutableBitSet类，表示不可变的位集合，用于标识列索引

import com.google.common.collect.ImmutableList; // 导入Guava的ImmutableList类，表示不可变的列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空性注解，用于标记可能为null的类型

/** A table function that returns states and their boundaries; also national
 * parks.
 * // 类注释：这是一个表函数，返回美国各州及其边界信息，同时也包含国家公园信息
 *
 * <p>Has same content as
 * <code>file/src/test/resources/geo/states.json</code>. */ // 该表函数的内容与geo/states.json文件相同
public class StatesTableFunction { // 定义StatesTableFunction类，作为表函数的入口点，提供静态方法返回ScannableTable对象
  private StatesTableFunction() {} // 私有构造方法，防止实例化，该类只提供静态方法

  private static final Object[][] STATE_ROWS = { // 定义静态常量二维数组，存储美国各州的数据，每行包含州名和边界多边形信息
      {"NV", "Polygon((-120 42, -114 42, -114 37, -114.75 35.1, -120 39," // 第1行：内华达州(NV)的边界多边形坐标
          + " -120 42))"}, // 多边形闭合坐标
      {"UT", "Polygon((-114 42, -111.05 42, -111.05 41, -109.05 41, -109.05 37," // 第2行：犹他州(UT)的边界多边形坐标
          + " -114 37, -114 42))"}, // 多边形闭合坐标
      {"CA", "Polygon((-124.25 42, -120 42, -120 39, -114.75 35.1," // 第3行：加利福尼亚州(CA)的边界多边形坐标
          + " -114.75 32.5, -117.15 32.5, -118.30 33.75, -120.5 34.5," // 继续多边形坐标
          + " -122.4 37.2, -124.25 42))"}, // 多边形闭合坐标
      {"AZ", "Polygon((-114 37, -109.05 37, -109.05 31.33, -111.07 31.33," // 第4行：亚利桑那州(AZ)的边界多边形坐标
          + " -114.75 32.5, -114.75 35.1, -114 37))"}, // 多边形闭合坐标
      {"CO", "Polygon((-109.05 41, -102 41, -102 37, -109.05 37, -109.05 41))"}, // 第5行：科罗拉多州(CO)的边界多边形坐标，矩形形状
      {"OR", "Polygon((-123.9 46.2, -122.7 45.7, -119 46, -117 46, -116.5 45.5," // 第6行：俄勒冈州(OR)的边界多边形坐标
          + " -117.03 44.2, -117.03 42, -124.25 42, -124.6 42.8," // 继续多边形坐标
          + " -123.9 46.2))"}, // 多边形闭合坐标
      {"WA", "Polygon((-124.80 48.4, -123.2 48.2, -123.2 49, -117 49, -117 46," // 第7行：华盛顿州(WA)的边界多边形坐标
          + " -119 46, -122.7 45.7, -123.9 46.2, -124.80 48.4))"}, // 多边形闭合坐标
      {"ID", "Polygon((-117 49, -116.05 49, -116.05 48, -114.4 46.6," // 第8行：爱达荷州(ID)的边界多边形坐标
          + " -112.9 44.45, -111.05 44.45, -111.05 42, -117.03 42," // 继续多边形坐标
          + " -117.03 44.2, -116.5 45.5, -117 46, -117 49))"}, // 多边形闭合坐标
      {"MT", "Polygon((-116.05 49, -104.05 49, -104.05 45, -111.05 45," // 第9行：蒙大拿州(MT)的边界多边形坐标
          + " -111.05 44.45, -112.9 44.45, -114.4 46.6, -116.05 48," // 继续多边形坐标
          + " -116.05 49))"}, // 多边形闭合坐标
      {"WY", "Polygon((-111.05 45, -104.05 45, -104.05 41, -111.05 41," // 第10行：怀俄明州(WY)的边界多边形坐标，矩形形状
          + " -111.05 45))"}, // 多边形闭合坐标
      {"NM", "Polygon((-109.05 37, -103 37, -103 32, -106.65 32, -106.5 31.8," // 第11行：新墨西哥州(NM)的边界多边形坐标
          + " -108.2 31.8, -108.2 31.33, -109.05 31.33, -109.05 37))"} // 多边形闭合坐标
  }; // STATE_ROWS数组结束，共包含11个州的边界数据

  private static final Object[][] PARK_ROWS = { // 定义静态常量二维数组，存储美国国家公园的数据，每行包含公园名称和边界多边形信息
      {"Yellowstone NP", "Polygon((-111.2 45.1, -109.30 45.1, -109.30 44.1," // 第1行：黄石国家公园的边界多边形坐标
          + " -109 43.8, -110 43, -111.2 43.4, -111.2 45.1))"}, // 多边形闭合坐标
      {"Yosemite NP", "Polygon((-120.2 38, -119.30 38.2, -119 37.7," // 第2行：约塞米蒂国家公园的边界多边形坐标
          + " -119.9 37.6, -120.2 38))"}, // 多边形闭合坐标
      {"Death Valley NP", "Polygon((-118.2 37.3, -117 37, -116.3 35.7," // 第3行：死亡谷国家公园的边界多边形坐标
          + " -117 35.7, -117.2 36.2, -117.8 36.4, -118.2 37.3))"}, // 多边形闭合坐标
  }; // PARK_ROWS数组结束，共包含3个国家公园的边界数据

  public static ScannableTable states(boolean b) { // 公共静态方法，返回一个ScannableTable对象，用于查询各州数据，参数b未被使用（保留以匹配表函数签名）
    return eval(STATE_ROWS); // 调用eval方法，传入STATE_ROWS数组，创建并返回一个ScannableTable实例
  }; // states方法结束

  public static ScannableTable parks(boolean b) { // 公共静态方法，返回一个ScannableTable对象，用于查询国家公园数据，参数b未被使用（保留以匹配表函数签名）
    return eval(PARK_ROWS); // 调用eval方法，传入PARK_ROWS数组，创建并返回一个ScannableTable实例
  }; // parks方法结束

  private static ScannableTable eval(final Object[][] rows) { // 私有静态方法，根据传入的行数据创建并返回一个ScannableTable匿名实现对象
    return new ScannableTable() { // 返回ScannableTable接口的匿名实现类实例
      @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 实现scan方法，扫描表数据并返回可枚举的结果集，root参数提供查询执行上下文
        return Linq4j.asEnumerable(rows); // 使用Linq4j工具类将二维数组转换为Enumerable对象，支持LINQ风格的查询操作
      } // scan方法结束

      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 实现getRowType方法，获取表的行类型（即表结构），typeFactory用于创建关系数据类型
        return typeFactory.builder() // 创建RelDataType构建器，用于定义表的结构
            .add("name", SqlTypeName.VARCHAR) // 添加第一列：name列，类型为VARCHAR，用于存储州名或公园名称
            .add("geom", SqlTypeName.VARCHAR) // 添加第二列：geom列，类型为VARCHAR，用于存储边界多边形的几何信息（WKT格式）
            .build(); // 构建并返回RelDataType对象
      } // getRowType方法结束

      @Override public Statistic getStatistic() { // 实现getStatistic方法，获取表的统计信息，用于查询优化
        return Statistics.of(rows.length, // 创建Statistic对象，传入表的行数（rows.length）
            ImmutableList.of(ImmutableBitSet.of(0))); // 传入主键列集合，ImmutableBitSet.of(0)表示第0列（name列）是唯一键
      } // getStatistic方法结束

      @Override public Schema.TableType getJdbcTableType() { // 实现getJdbcTableType方法，获取JDBC表类型
        return Schema.TableType.TABLE; // 返回TABLE类型，表示这是一个普通表
      } // getJdbcTableType方法结束

      @Override public boolean isRolledUp(String column) { // 实现isRolledUp方法，判断指定列是否是上卷列（用于聚合操作）
        return false; // 返回false，表示该表没有上卷列
      } // isRolledUp方法结束

      @Override public boolean rolledUpColumnValidInsideAgg(String column, SqlCall call, // 实现rolledUpColumnValidInsideAgg方法，判断上卷列是否可以在聚合函数中使用
          @Nullable SqlNode parent, @Nullable CalciteConnectionConfig config) { // parent是父SQL节点，config是连接配置
        return false; // 返回false，表示该表不支持上卷列的聚合操作
      } // rolledUpColumnValidInsideAgg方法结束
    }; // ScannableTable匿名实现类结束
  } // eval方法结束
} // StatesTableFunction类结束
