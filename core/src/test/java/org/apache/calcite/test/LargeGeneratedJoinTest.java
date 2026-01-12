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
package org.apache.calcite.test; // Apache Calcite测试包，包含各种测试类

import org.apache.calcite.adapter.java.AbstractQueryableTable; // 抽象可查询表基类，用于创建可查询的表实现
import org.apache.calcite.config.CalciteConnectionProperty; // Calcite连接属性配置类
import org.apache.calcite.config.Lex; // 词法分析配置类，控制SQL解析的词法规则
import org.apache.calcite.interpreter.Row; // 解释器中的行表示类，用于表示数据行
import org.apache.calcite.jdbc.CalciteSchema; // Calcite模式类，表示数据库模式结构
import org.apache.calcite.linq4j.Enumerable; // LINQ4J可枚举接口，用于数据集合操作
import org.apache.calcite.linq4j.Linq4j; // LINQ4J工具类，提供各种LINQ操作方法
import org.apache.calcite.linq4j.QueryProvider; // LINQ查询提供者接口，用于执行查询
import org.apache.calcite.linq4j.Queryable; // LINQ可查询接口，支持延迟查询
import org.apache.calcite.rel.type.RelDataType; // 关系数据类型类，表示Calcite中的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 关系数据类型工厂接口，用于创建数据类型
import org.apache.calcite.schema.QueryableTable; // 可查询表接口，支持LINQ查询的表
import org.apache.calcite.schema.Schema; // 模式接口，表示数据库模式
import org.apache.calcite.schema.SchemaPlus; // 增强模式接口，提供额外的模式操作功能
import org.apache.calcite.schema.Table; // 表接口，表示数据库表
import org.apache.calcite.schema.impl.AbstractSchema; // 抽象模式实现类，提供模式的基础实现
import org.apache.calcite.sql.type.SqlTypeName; // SQL类型名称枚举，定义各种SQL数据类型

import com.google.common.collect.ImmutableMap; // Google Guava不可变Map类，用于创建不可变映射

import org.junit.jupiter.api.Test; // JUnit 5测试注解，标记测试方法

import java.sql.SQLException; // SQL异常类，处理SQL相关错误
import java.util.ArrayList; // Java动态数组类
import java.util.List; // Java列表接口
import java.util.Map; // Java映射接口
import java.util.function.BiConsumer; // 双参数消费者函数接口
import java.util.function.Function; // 单参数函数接口

import static org.apache.calcite.config.CalciteSystemProperty.JOIN_SELECTOR_COMPACT_CODE_THRESHOLD; // 静态导入连接选择器紧凑代码生成阈值配置

import static org.hamcrest.CoreMatchers.is; // Hamcrest匹配器，用于断言相等
import static org.hamcrest.CoreMatchers.nullValue; // Hamcrest匹配器，用于断言为null
import static org.hamcrest.MatcherAssert.assertThat; // Hamcrest断言工具类
import static org.junit.jupiter.api.Assertions.assertTrue; // JUnit断言方法，断言条件为真

/**
 * Test case for
 * <a href="https://issues.apache.org/jira/browse/CALCITE-3094">[CALCITE-3094]
 * Code of method grows beyond 64 KB when joining two tables with many fields</a>.
 * 测试用例：用于验证CALCITE-3094问题，即当连接两个包含大量字段的表时，生成的方法代码超过64KB限制的问题
 * 这个测试类专门用于测试Calcite在处理大规模表连接时的代码生成能力，确保不会因为字段过多而导致生成的字节码超过JVM方法大小限制
 * 类中包含多个测试方法，分别测试内连接、左外连接、右外连接和全外连接在处理大量字段时的正确性
 */
public class LargeGeneratedJoinTest { // 大规模生成连接测试类，测试包含大量字段的表连接场景

  /**
   * Marker interface for Field.
   * 字段标记接口，用于表示表中的字段定义
   * 这个接口扩展了BiConsumer，接受RelDataTypeFactory和Builder两个参数，用于构建字段类型
   */
  interface FieldT extends BiConsumer<RelDataTypeFactory, RelDataTypeFactory.Builder> { // 字段类型接口，用于定义表字段
  }

  /**
   * Marker interface for Row.
   * 行标记接口，用于表示行的类型定义
   * 这个接口扩展了Function，接受RelDataTypeFactory参数，返回RelDataType类型的行结构
   */
  interface RowT extends Function<RelDataTypeFactory, RelDataType> { // 行类型接口，用于定义行结构
  }

  static FieldT field(String name) { // 静态工厂方法：创建一个字段定义，参数name为字段名称
    return (tf, b) -> b.add(name, SqlTypeName.VARCHAR); // 返回一个BiConsumer，将字段添加到类型构建器中，类型为VARCHAR
  }

  static RowT row(FieldT... fields) { // 静态工厂方法：创建一个行定义，参数fields为可变数量的字段定义
    return tf -> { // 返回一个Function，接受类型工厂参数
      RelDataTypeFactory.Builder builder = tf.builder(); // 创建类型构建器
      for (FieldT f : fields) { // 遍历所有字段定义
        f.accept(tf, builder); // 将每个字段添加到构建器中
      }
      return builder.build(); // 构建并返回完整的行类型
    };
  }

  private static QueryableTable tab(String table, int fieldCount) { // 私有静态方法：创建一个可查询表，参数table为表名，fieldCount为字段数量
    List<Row> lRow = new ArrayList<>(); // 创建行列表，用于存储表的数据行
    for (int r = 0; r < 2; r++) { // 循环创建2行数据
      Object[] current = new Object[fieldCount]; // 创建当前行的对象数组，大小为字段数量
      for (int i = 0; i < fieldCount; i++) { // 遍历每个字段
        current[i] = "v" + i; // 设置字段值为"v"加上索引，如v0, v1, v2等
      }
      lRow.add(Row.of(current)); // 将当前行添加到行列表中
    }

    List<FieldT> fields = new ArrayList<>(); // 创建字段类型列表，用于存储所有字段的类型定义
    for (int i = 0; i < fieldCount; i++) { // 遍历字段数量
      fields.add(field(table + "_F_" + i)); // 添加字段定义，字段名为表名加上"_F_"加上索引
    }

    final Enumerable<?> enumerable = Linq4j.asEnumerable(lRow); // 将行列表转换为LINQ可枚举对象
    return new AbstractQueryableTable(Row.class) { // 创建并返回一个抽象可查询表的匿名子类实例

      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写方法：获取表的行类型
        return row(fields.toArray(new FieldT[fieldCount])).apply(typeFactory); // 使用字段数组构建行类型并应用类型工厂
      }

      @Override public <T> Queryable<T> asQueryable(QueryProvider queryProvider, SchemaPlus schema,
          String tableName) { // 重写方法：将表转换为可查询对象
        return (Queryable<T>) enumerable.asQueryable(); // 返回可枚举对象的可查询形式
      }
    };
  }

  private static int getBaseTableSize() { // 私有静态方法：获取基础表的大小（字段数量）
    // If compact code generation is turned off, we generate tables that
    // will cause the issue. Otherwise, to avoid impacting the test duration,
    // we only generate tables wide enough to enable the compact code generation.
    // 如果紧凑代码生成被关闭，我们生成会触发问题的表；否则，为了避免影响测试持续时间，
    // 我们只生成足够宽的表来启用紧凑代码生成
    int compactCodeThreshold = JOIN_SELECTOR_COMPACT_CODE_THRESHOLD.value(); // 获取紧凑代码生成阈值配置值
    return compactCodeThreshold < 0 ? 3000 : Math.max(100, compactCodeThreshold); // 如果阈值为负数返回3000，否则返回100和阈值中的较大值
  }

  private static int getT0Size() { // 私有静态方法：获取T0表的大小（字段数量）
    return getBaseTableSize(); // 返回基础表大小
  }
  private static int getT1Size() { // 私有静态方法：获取T1表的大小（字段数量）
    return getBaseTableSize() + 1; // 返回基础表大小加1，确保两个表大小不同以测试边界情况
  }

  private static CalciteAssert.AssertQuery assertQuery(String sql) { // 私有静态方法：创建查询断言，参数sql为要执行的SQL语句
    Schema rootSchema = new AbstractSchema() { // 创建抽象模式的匿名子类作为根模式
      @Override protected Map<String, Table> getTableMap() { // 重写方法：获取表映射
        return ImmutableMap.of("T0", tab("T0", getT0Size()), // 创建包含T0和T1两个表的不可变映射
            "T1", tab("T1", getT1Size()));
      }
    };

    final CalciteSchema sp = CalciteSchema.createRootSchema(false, true); // 创建Calcite根模式实例，参数表示不缓存且添加为子模式
    sp.add("ROOT", rootSchema); // 将根模式添加到Calcite模式中，命名为"ROOT"

    final CalciteAssert.AssertThat ca = CalciteAssert.that() // 创建Calcite断言构建器
        .with(CalciteConnectionProperty.LEX, Lex.JAVA) // 设置词法分析规则为JAVA风格
        .withSchema("ROOT", rootSchema) // 设置模式为ROOT
        .withDefaultSchema("ROOT"); // 设置默认模式为ROOT

    return ca.query(sql); // 返回查询断言对象
  }

  @Test public void test() { // 测试方法：测试内连接两个包含大量字段的表，验证连接操作的正确性和代码生成能力
    String sql = "SELECT * \n" // 创建SQL字符串，选择所有列
        + "FROM ROOT.T0 \n" // 从T0表
        + "JOIN ROOT.T1 \n" // 与T1表进行内连接
        + "ON TRUE"; // 连接条件为TRUE，即笛卡尔积

    sql = "select T0_F_0||T0_F_1, * from (" + sql + ")"; // 在原查询外层添加字符串连接和所有列，测试字符串操作

    final CalciteAssert.AssertQuery query = assertQuery(sql); // 创建查询断言
    query.returns(rs -> { // 定义结果集验证逻辑
      try {
        assertTrue(rs.next()); // 断言结果集有第一行数据
        assertThat(rs.getMetaData().getColumnCount(), // 断言列数等于1（连接列）+ T0字段数 + T1字段数
            is(1 + getT0Size() + getT1Size()));
        long row = 0; // 行计数器初始化
        do { // 遍历结果集的所有行
          ++row; // 行号递增
          for (int i = 1; i <= rs.getMetaData().getColumnCount(); ++i) { // 遍历每一列
            // Rows have the format: v0v1, v0, v1, v2, ..., v99, v0, v1, v2, ..., v99, v100
            // 行格式说明：第一列是v0v1（T0前两字段连接），后面是T0所有字段，再后面是T1所有字段
            final String reason = "Error at row: " + row + ", column: " + i; // 创建错误信息
            if (i == 1) { // 第一列：连接后的字符串
              assertThat(reason, rs.getString(i), is("v0v1")); // 断言值为"v0v1"
            } else if (i <= getT0Size() + 1) { // T0表的字段列
              assertThat(reason, rs.getString(i), is("v" + (i - 2))); // 断言值为"v"加上相应索引
            } else { // T1表的字段列
              assertThat(reason, rs.getString(i), // 断言值为"v"加上相应索引（减去T0字段数）
                  is("v" + ((i - 2) - getT0Size())));
            }
          }
        } while (rs.next()); // 继续下一行
        assertThat(row, is(4L)); // 断言总行数为4（T0有2行，T1有2行，内连接产生4行）
      } catch (SQLException e) { // 捕获SQL异常
        throw new RuntimeException(e); // 包装为运行时异常抛出
      }
    });
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6593">[CALCITE-6593]
   * NPE when outer joining tables with many fields and unmatching rows</a>.
   * 测试用例：用于验证CALCITE-6593问题，即当外连接包含大量字段的表且有不匹配行时出现空指针异常的问题
   */
  @Test public void testLeftJoinWithEmptyRightSide() { // 测试方法：测试左外连接，右侧表为空的情况
    String sql = "SELECT * \n" // 创建SQL字符串，选择所有列
        + "FROM ROOT.T0 \n" // 从T0表
        + "LEFT JOIN (SELECT * FROM ROOT.T1 WHERE T1_F_0 = 'xyz') \n" // 与T1表的子查询进行左外连接，子查询条件使结果为空
        + "ON TRUE"; // 连接条件为TRUE

    sql = "select T0_F_0||T0_F_1, * from (" + sql + ")"; // 在原查询外层添加字符串连接和所有列

    final CalciteAssert.AssertQuery query = assertQuery(sql); // 创建查询断言
    query.returns(rs -> { // 定义结果集验证逻辑
      try {
        assertTrue(rs.next()); // 断言结果集有第一行数据
        assertThat(rs.getMetaData().getColumnCount(), // 断言列数等于1（连接列）+ T0字段数 + T1字段数
            is(1 + getT0Size() + getT1Size()));
        long row = 0; // 行计数器初始化
        do { // 遍历结果集的所有行
          ++row; // 行号递增
          for (int i = 1; i <= rs.getMetaData().getColumnCount(); ++i) { // 遍历每一列
            // Rows have the format: v0v1, v0, v1, v2, ..., v99, null, ..., null
            // 行格式说明：第一列是v0v1，后面是T0所有字段，再后面是T1所有字段（全部为null）
            final String reason = "Error at row: " + row + ", column: " + i; // 创建错误信息
            if (i == 1) { // 第一列：连接后的字符串
              assertThat(reason, rs.getString(i), is("v0v1")); // 断言值为"v0v1"
            } else if (i <= getT0Size() + 1) { // T0表的字段列
              assertThat(reason, rs.getString(i), is("v" + (i - 2))); // 断言值为"v"加上相应索引
            } else { // T1表的字段列（因为右侧为空，应该全部为null）
              assertThat(reason, rs.getString(i), nullValue()); // 断言值为null
            }
          }
        } while (rs.next()); // 继续下一行
        assertThat(row, is(2L)); // 断言总行数为2（只有T0的2行，右侧为空）
      } catch (SQLException e) { // 捕获SQL异常
        throw new RuntimeException(e); // 包装为运行时异常抛出
      }
    });
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6593">[CALCITE-6593]
   * NPE when outer joining tables with many fields and unmatching rows</a>.
   * 测试用例：用于验证CALCITE-6593问题，即当外连接包含大量字段的表且有不匹配行时出现空指针异常的问题
   */
  @Test public void testRightJoinWithEmptyLeftSide() { // 测试方法：测试右外连接，左侧表为空的情况
    String sql = "SELECT * \n" // 创建SQL字符串，选择所有列
        + "FROM (SELECT * FROM ROOT.T0 WHERE T0_F_0 = 'xyz') \n" // 从T0表的子查询，条件使结果为空
        + "RIGHT JOIN ROOT.T1 \n" // 与T1表进行右外连接
        + "ON TRUE"; // 连接条件为TRUE

    sql = "select T1_F_0||T1_F_1, * from (" + sql + ")"; // 在原查询外层添加字符串连接和所有列

    final CalciteAssert.AssertQuery query = assertQuery(sql); // 创建查询断言
    query.returns(rs -> { // 定义结果集验证逻辑
      try {
        assertTrue(rs.next()); // 断言结果集有第一行数据
        assertThat(rs.getMetaData().getColumnCount(), // 断言列数等于1（连接列）+ T0字段数 + T1字段数
            is(1 + getT0Size() + getT1Size()));
        long row = 0; // 行计数器初始化
        do { // 遍历结果集的所有行
          ++row; // 行号递增
          for (int i = 1; i <= rs.getMetaData().getColumnCount(); ++i) { // 遍历每一列
            // Rows have the format: v0v1, null, ..., null, v0, v1, v2, ..., v100
            // 行格式说明：第一列是v0v1，后面是T0所有字段（全部为null），再后面是T1所有字段
            final String reason = "Error at row: " + row + ", column: " + i; // 创建错误信息
            if (i == 1) { // 第一列：连接后的字符串
              assertThat(reason, rs.getString(i), is("v0v1")); // 断言值为"v0v1"
            } else if (i <= getT0Size() + 1) { // T0表的字段列（因为左侧为空，应该全部为null）
              assertThat(reason, rs.getString(i), nullValue()); // 断言值为null
            } else { // T1表的字段列
              assertThat(reason, rs.getString(i), // 断言值为"v"加上相应索引（减去T0字段数）
                  is("v" + (i - 2 - getT0Size())));
            }
          }
        } while (rs.next()); // 继续下一行
        assertThat(row, is(2L)); // 断言总行数为2（只有T1的2行，左侧为空）
      } catch (SQLException e) { // 捕获SQL异常
        throw new RuntimeException(e); // 包装为运行时异常抛出
      }
    });
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6593">[CALCITE-6593]
   * NPE when outer joining tables with many fields and unmatching rows</a>.
   * 测试用例：用于验证CALCITE-6593问题，即当外连接包含大量字段的表且有不匹配行时出现空指针异常的问题
   */
  @Test public void testFullJoinWithUnmatchedRows() { // 测试方法：测试全外连接，包含不匹配行的情况
    String sql = "SELECT * \n" // 创建SQL字符串，选择所有列
        + "FROM ROOT.T0 \n" // 从T0表
        + "FULL JOIN ROOT.T1 \n" // 与T1表进行全外连接
        + "ON T0_F_0 <> T1_F_0"; // 连接条件为T0_F_0不等于T1_F_0，确保所有行都不匹配

    sql = "select T0_F_0||T0_F_1, T1_F_0||T1_F_1, * from (" + sql + ")"; // 在原查询外层添加两个字符串连接列和所有列

    final CalciteAssert.AssertQuery query = assertQuery(sql); // 创建查询断言
    query.returns(rs -> { // 定义结果集验证逻辑
      try {
        assertTrue(rs.next()); // 断言结果集有第一行数据
        assertThat(rs.getMetaData().getColumnCount(), // 断言列数等于2（两个连接列）+ T0字段数 + T1字段数
            is(1 + 1 + getT0Size() + getT1Size()));
        long row = 0; // 行计数器初始化
        do { // 遍历结果集的所有行
          ++row; // 行号递增
          for (int i = 1; i <= rs.getMetaData().getColumnCount(); ++i) { // 遍历每一列
            final String reason = "Error at row: " + row + ", column: " + i; // 创建错误信息
            if (row <= 2) { // 前2行：T0的行，T1不匹配
              // First 2 rows have the format: v0v1, null, v0, v1, v2, ..., v99, null, ..., null
              // 前2行格式：第一列是v0v1，第二列是null，后面是T0所有字段，再后面是T1所有字段（全部为null）
              if (i == 1) { // 第一列：T0的连接字符串
                assertThat(reason, rs.getString(i), is("v0v1")); // 断言值为"v0v1"
              } else if (i == 2) { // 第二列：T1的连接字符串（不匹配，为null）
                assertThat(reason, rs.getString(i), nullValue()); // 断言值为null
              } else if (i <= getT0Size() + 2) { // T0表的字段列
                assertThat(reason, rs.getString(i), is("v" + (i - 3))); // 断言值为"v"加上相应索引
              } else { // T1表的字段列（不匹配，全部为null）
                assertThat(reason, rs.getString(i), nullValue()); // 断言值为null
              }
            } else { // 后2行：T1的行，T0不匹配
              // Last 2 rows have the format: null, v0v1, null, ..., null, v0, v1, v2, ..., v100
              // 后2行格式：第一列是null，第二列是v0v1，后面是T0所有字段（全部为null），再后面是T1所有字段
              if (i == 1) { // 第一列：T0的连接字符串（不匹配，为null）
                assertThat(reason, rs.getString(i), nullValue()); // 断言值为null
              } else if (i == 2) { // 第二列：T1的连接字符串
                assertThat(reason, rs.getString(i), is("v0v1")); // 断言值为"v0v1"
              } else if (i <= getT0Size() + 2) { // T0表的字段列（不匹配，全部为null）
                assertThat(reason, rs.getString(i), nullValue()); // 断言值为null
              } else { // T1表的字段列
                assertThat(reason, rs.getString(i), // 断言值为"v"加上相应索引（减去T0字段数）
                    is("v" + (i - 3 - getT0Size())));
              }
            }
          }
        } while (rs.next()); // 继续下一行
        assertThat(row, is(4L)); // 断言总行数为4（T0的2行不匹配 + T1的2行不匹配）
      } catch (SQLException e) { // 捕获SQL异常
        throw new RuntimeException(e); // 包装为运行时异常抛出
      }
    });
  }
}
