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
package org.apache.calcite.util;

import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.enumerable.EnumerableTableScan;
import org.apache.calcite.adapter.java.AbstractQueryableTable;
import org.apache.calcite.config.CalciteConnectionConfig;
import org.apache.calcite.linq4j.AbstractEnumerable;
import org.apache.calcite.linq4j.BaseQueryable;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.linq4j.QueryProvider;
import org.apache.calcite.linq4j.Queryable;
import org.apache.calcite.linq4j.function.Deterministic;
import org.apache.calcite.linq4j.function.Parameter;
import org.apache.calcite.linq4j.function.SemiStrict;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.calcite.linq4j.tree.MethodCallExpression;
import org.apache.calcite.linq4j.tree.Types;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.externalize.RelJsonReader;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.runtime.PairList;
import org.apache.calcite.runtime.SqlFunctions;
import org.apache.calcite.schema.FunctionContext;
import org.apache.calcite.schema.FunctionParameter;
import org.apache.calcite.schema.QueryableTable;
import org.apache.calcite.schema.ScannableTable;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.Schemas;
import org.apache.calcite.schema.Statistic;
import org.apache.calcite.schema.Statistics;
import org.apache.calcite.schema.TableMacro;
import org.apache.calcite.schema.TranslatableTable;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.schema.impl.AbstractTableQueryable;
import org.apache.calcite.schema.impl.ViewTable;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.dialect.CalciteSqlDialect;
import org.apache.calcite.sql.type.SqlTypeName;

import com.google.common.collect.ImmutableList;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import static java.lang.Integer.parseInt;
import static java.util.Objects.requireNonNull;

/** // 用于测试的各种类和函数的持有类，包括用户定义函数等。这个类包含了许多用于测试Calcite功能的工具类和函数，特别是用户定义函数（UDF）、用户定义聚合函数（UDAF）、表函数等。这些测试类和函数覆盖了Calcite的各种特性和功能点。
 * Holder for various classes and functions used in tests as user-defined
 * functions and so forth. // 生成字符串序列的方法常量，用于生成IntString值的序列
 */ // 查找generateStrings方法
public class Smalls { // 根据输入List大小生成字符串序列的方法常量
  public static final Method GENERATE_STRINGS_METHOD = // 查找generateStringsOfInputSize方法
      Types.lookupMethod(Smalls.class, "generateStrings", Integer.class); // 根据输入Map大小生成字符串序列的方法常量
  public static final Method GENERATE_STRINGS_OF_INPUT_SIZE_METHOD = // 查找generateStringsOfInputMapSize方法
      Types.lookupMethod(Smalls.class, "generateStringsOfInputSize", List.class); // 生成迷宫表的方法常量（三个参数：宽度、高度、种子）
  public static final Method GENERATE_STRINGS_OF_INPUT_MAP_SIZE_METHOD = // 查找MazeTable的generate方法
      Types.lookupMethod(Smalls.class, "generateStringsOfInputMapSize", Map.class); // 第三个参数是种子
  public static final Method MAZE_METHOD = // 生成迷宫表的方法常量（第二个版本，第三个参数可选）
      Types.lookupMethod(MazeTable.class, "generate", int.class, int.class, // 查找MazeTable的generate2方法
          int.class); // 第三个参数是可选的种子
  public static final Method MAZE2_METHOD = // 生成迷宫表的方法常量（第三个版本，字符串参数）
      Types.lookupMethod(MazeTable.class, "generate2", int.class, int.class, // 查找MazeTable的generate3方法
          Integer.class); // 生成乘法表的方法常量（列数、行数、偏移量）
  public static final Method MAZE3_METHOD = // 查找multiplicationTable方法
      Types.lookupMethod(MazeTable.class, "generate3", String.class); // 第三个参数是可选的偏移量
  public static final Method MULTIPLICATION_TABLE_METHOD = // 生成斐波那契序列表的方法常量（无限序列）
      Types.lookupMethod(Smalls.class, "multiplicationTable", int.class, // 查找fibonacciTable方法
        int.class, Integer.class); // 生成限制为100项的斐波那契序列表的方法常量
  public static final Method FIBONACCI_TABLE_METHOD = // 查找fibonacciTableWithLimit100方法
      Types.lookupMethod(Smalls.class, "fibonacciTable"); // 生成带限制的斐波那契序列表的方法常量
  public static final Method FIBONACCI_LIMIT_100_TABLE_METHOD = // 查找fibonacciTableWithLimit方法，参数是限制值
      Types.lookupMethod(Smalls.class, "fibonacciTableWithLimit100"); // 生成斐波那契序列表的实例方法常量
  public static final Method FIBONACCI_LIMIT_TABLE_METHOD = // 查找FibonacciTableFunction的eval方法
      Types.lookupMethod(Smalls.class, "fibonacciTableWithLimit", long.class); // 生成带两个参数的虚拟表的方法常量
  public static final Method FIBONACCI_INSTANCE_TABLE_METHOD = // 查找dummyTableFuncWithTwoParams方法
      Types.lookupMethod(Smalls.FibonacciTableFunction.class, "eval"); // 生成动态行类型表的方法常量
  public static final Method DUMMY_TABLE_METHOD_WITH_TWO_PARAMS = // 查找dynamicRowTypeTable方法
      Types.lookupMethod(Smalls.class, "dummyTableFuncWithTwoParams", long.class, long.class); // 第二个参数是行数
  public static final Method DYNAMIC_ROW_TYPE_TABLE_METHOD = // 生成视图的方法常量
      Types.lookupMethod(Smalls.class, "dynamicRowTypeTable", String.class, // 查找view方法
          int.class); // 生成字符串视图的方法常量
  public static final Method VIEW_METHOD = // 查找str方法，两个对象参数
      Types.lookupMethod(Smalls.class, "view", String.class); // 字符串联合方法常量
  public static final Method STR_METHOD = // 查找stringUnion方法
      Types.lookupMethod(Smalls.class, "str", Object.class, Object.class); // 两个Queryable参数
  public static final Method STRING_UNION_METHOD = // 处理游标的方法常量
      Types.lookupMethod(Smalls.class, "stringUnion", Queryable.class, // 查找processCursor方法
          Queryable.class); // 偏移量和可枚举参数
  public static final Method PROCESS_CURSOR_METHOD = // 处理多个游标的方法常量
      Types.lookupMethod(Smalls.class, "processCursor", // 查找processCursors方法
          int.class, Enumerable.class); // 偏移量和两个可枚举参数
  public static final Method PROCESS_CURSORS_METHOD = // MyPlusFunction的eval方法常量
      Types.lookupMethod(Smalls.class, "processCursors", // 查找MyPlusFunction的eval方法，两个整数参数
          int.class, Enumerable.class, Enumerable.class); // MyPlusInitFunction的eval方法常量
  public static final Method MY_PLUS_EVAL_METHOD = // 查找MyPlusInitFunction的eval方法，两个整数参数
      Types.lookupMethod(MyPlusFunction.class, "eval", int.class, int.class);
  public static final Method MY_PLUS_INIT_EVAL_METHOD = // 私有构造方法，防止实例化，因为这是一个工具类，所有成员都是静态的
      Types.lookupMethod(MyPlusInitFunction.class, "eval", int.class,
          int.class); // 创建一个只包含单个整数的可查询表，用于测试identity函数
 // 参数是要包含的整数值
  private Smalls() {} // 将整数转换为可枚举对象
 // 创建抽象可查询表，元素类型为Integer
  private static QueryableTable identity(Integer i) { // 将表转换为可查询对象
    final Enumerable<Integer> enumerable = Linq4j.asEnumerable(ImmutableList.of(i)); // 查询提供者、模式、表名
    return new AbstractQueryableTable(Integer.class) { // 忽略类型检查警告
      @Override public <E> Queryable<E> asQueryable( // 将可枚举对象转换为可查询对象
          QueryProvider queryProvider, SchemaPlus schema, String tableName) { // 获取行类型
        //noinspection unchecked // 构建行类型，包含一个名为"i"的整数列
        return (Queryable<E>) enumerable.asQueryable();
      } // 创建包含1、3和解析值的可查询表
 // 存储整数列表
      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) {
        return typeFactory.builder().add("i", SqlTypeName.INTEGER).build(); // 当SQL包含表达式调用函数时，参数为null
      } // 引擎使用null参数调用函数以获取行类型
    }; // 如果字符串参数为null
  } // 创建空列表
 // 否则解析字符串
  private static QueryableTable oneThreePlus(String s) { // 去除首尾字符后解析为整数
    List<Integer> items; // 创建包含1、3和解析值的列表
    // Argument is null in case SQL contains function call with expression. // 将列表转换为可枚举对象
    // Then the engine calls a function with null arguments to get getRowType. // 创建抽象可查询表
    if (s == null) { // 转换为可查询对象
      items = ImmutableList.of(); // 查询提供者、模式、表名
    } else { // 忽略类型检查警告
      Integer latest = parseInt(s.substring(1, s.length() - 1)); // 返回可查询对象
      items = ImmutableList.of(1, 3, latest); // 获取行类型
    } // 构建包含"c"整数列的行类型
    final Enumerable<Integer> enumerable = Linq4j.asEnumerable(items);
    return new AbstractQueryableTable(Integer.class) { // 将两个可查询对象合并成一个
      @Override public <E> Queryable<E> asQueryable( // 两个可查询对象参数，类型必须相同
          QueryProvider queryProvider, SchemaPlus schema, String tableName) { // 使用concat方法将两个可查询对象连接起来
        //noinspection unchecked
        return (Queryable<E>) enumerable.asQueryable();
      } // 一个生成包含IntString值序列的表的函数。该方法创建一个可查询表，表中包含指定数量的IntString对象，每个IntString包含一个整数索引和一个对应的字符串。
 // 参数是要生成的IntString对象数量
      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 创建抽象可查询表，元素类型为IntString
        return typeFactory.builder().add("c", SqlTypeName.INTEGER).build(); // 获取行类型
      } // 创建Java类型为IntString的行类型
    }; // 转换为可查询对象
  } // 查询提供者、模式、表名
 // 创建基础可查询对象
  public static <T> Queryable<T> stringUnion( // 查询提供者为null，元素类型为IntString
      Queryable<T> q0, Queryable<T> q1) { // 创建枚举器
    return q0.concat(q1); // 返回IntString枚举器
  } // 用于生成字符串的字符集
 // 当前索引
  /** A function that generates a table that generates a sequence of // 当前整数
   * {@link IntString} values. */ // 当前字符串
  public static QueryableTable generateStrings(final Integer count) { // 获取当前元素
    return new AbstractQueryableTable(IntString.class) { // 返回包含当前整数和字符串的IntString对象
      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 移动到下一个元素
        return typeFactory.createJavaType(IntString.class); // 如果还有元素未生成
      } // 设置当前整数
 // 设置当前字符串（从字符集中截取）
      @Override public <T> Queryable<T> asQueryable(QueryProvider queryProvider, // 增加索引
          SchemaPlus schema, String tableName) { // 返回true表示成功移动
        BaseQueryable<IntString> queryable = // 如果已生成所有元素
            new BaseQueryable<IntString>(null, IntString.class, null) { // 返回false表示没有更多元素
              @Override public Enumerator<IntString> enumerator() { // 重置枚举器
                return new Enumerator<IntString>() { // 将索引重置为0
                  static final String Z = "abcdefghijklm"; // 关闭枚举器
 // 空实现，无需清理资源
                  int i = 0; // 忽略类型检查警告
                  int curI; // 返回可查询对象
                  String curS;
 // 根据输入列表大小生成字符串序列
                  @Override public IntString current() { // 调用generateStrings方法，参数为列表大小
                    return new IntString(curI, curS); // 根据输入Map大小生成字符串序列
                  } // 调用generateStrings方法，参数为Map大小

                  @Override public boolean moveNext() { // 一个生成ncol列×nrow行乘法表的函数。该方法创建一个乘法表，每行包含行名和nrow列的乘积值，每个单元格的值为列索引乘以行索引加上偏移量。
                    if (i < count) { // 列数
                      curI = i; // 行数和可选的偏移量
                      curS = Z.substring(0, i % Z.length()); // 如果偏移量为null，则使用0作为默认值
                      ++i; // 创建抽象可查询表，元素类型为Object数组
                      return true; // 获取行类型
                    } else { // 创建类型构建器
                      return false; // 添加row_name列，类型为String
                    } // 创建整数类型
                  } // 为每一列添加类型定义
 // 列名为c1, c2, ..., cncol，类型为int
                  @Override public void reset() { // 构建并返回行类型
                    i = 0; // 转换为可查询对象
                  } // 查询提供者、模式、表名
 // 创建抽象列表作为表数据
                  @Override public void close() { // 获取指定索引的行
                  } // 创建行数组，大小为列数+1（包括row_name）
                }; // 设置行名
              } // 为每列计算乘积值
            }; // 值为列索引×行索引+偏移量
        //noinspection unchecked // 返回行数组
        return (Queryable<T>) queryable; // 获取行数
      } // 返回行数
    }; // 将列表转换为可查询对象
  }
 // 一个生成斐波那契序列的函数。有趣之处在于它只有一列且没有参数，而且它是无限序列。
  public static QueryableTable generateStringsOfInputSize(final List<Integer> list) { // 生成无限斐波那契序列
    return generateStrings(list.size()); // 调用带限制的方法，-1表示无限制
  }
  public static QueryableTable generateStringsOfInputMapSize(final Map<Integer, Integer> map) { // 一个生成斐波那契序列前100项的函数。有趣之处在于它只有一列且没有参数。
    return generateStrings(map.size()); // 生成限制为100项的斐波那契序列
  } // 调用带限制的方法，限制为100

  /** A function that generates multiplication table of {@code ncol} columns x // 一个接受两个参数作为输入的函数。该方法创建一个虚拟的可扫描表，用于测试带参数的表函数。
   * {@code nrow} rows. */ // 两个long类型参数
  public static QueryableTable multiplicationTable(final int ncol, // 返回可扫描表
      final int nrow, Integer offset) { // 获取行类型
    final int offs = offset == null ? 0 : offset; // 构建包含"N"BIGINT列的行类型
    return new AbstractQueryableTable(Object[].class) { // 扫描表数据
      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 返回可枚举对象
        final RelDataTypeFactory.Builder builder = typeFactory.builder(); // 创建枚举器
        builder.add("row_name", typeFactory.createJavaType(String.class)); // 返回空枚举器
        final RelDataType int_ = typeFactory.createJavaType(int.class); // 获取当前元素
        for (int i = 1; i <= ncol; i++) { // 返回空数组
          builder.add("c" + i, int_); // 移动到下一个元素
        } // 总是返回false，表示没有元素
        return builder.build(); // 重置枚举器
      } // 空实现
 // 关闭枚举器
      @Override public Queryable<Object[]> asQueryable(QueryProvider queryProvider, // 空实现
          SchemaPlus schema, String tableName) { // 获取统计信息
        final List<Object[]> table = new AbstractList<Object[]>() { // 返回未知统计信息
          @Override public Object[] get(int index) { // 获取JDBC表类型
            Object[] cur = new Object[ncol + 1]; // 返回TABLE类型
            cur[0] = "row " + index; // 检查列是否为上卷列
            for (int j = 1; j <= ncol; j++) { // 返回false，表示不是上卷列
              cur[j] = j * (index + 1) + offs; // 检查上卷列在聚合中是否有效
            } // 父节点和连接配置
            return cur; // 返回true，表示有效
          }
 // 一个生成斐波那契序列的函数。有趣之处在于它只有一列且没有参数。
          @Override public int size() { // 生成带限制的斐波那契序列
            return nrow; // 返回可扫描表
          } // 获取行类型
        }; // 构建包含"N"BIGINT列的行类型
        return Linq4j.asEnumerable(table).asQueryable(); // 扫描表数据
      } // 返回可枚举对象
    }; // 创建枚举器
  } // 返回斐波那契序列枚举器
 // 前一个值
  /** A function that generates the Fibonacci sequence. // 当前值
   * // 获取当前元素
   * <p>Interesting because it has one column and no arguments, // 返回包含当前值的数组
   * and because it is infinite. */ // 移动到下一个元素
  public static ScannableTable fibonacciTable() { // 计算下一个斐波那契数
    return fibonacciTableWithLimit(-1L); // 如果有限制且下一个值超过限制
  } // 返回false，停止生成
 // 更新前一个值
  /** A function that generates the first 100 terms of the Fibonacci sequence. // 更新当前值
   * // 返回true，继续生成
   * <p>Interesting because it has one column and no arguments. */ // 重置枚举器
  public static ScannableTable fibonacciTableWithLimit100() { // 重置前一个值
    return fibonacciTableWithLimit(100L); // 重置当前值
  } // 关闭枚举器
 // 空实现
  /** A function that takes 2 param as input. */ // 获取统计信息
  public static ScannableTable dummyTableFuncWithTwoParams(final long param1, final long param2) { // 返回未知统计信息
    return new ScannableTable() { // 获取JDBC表类型
      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 返回TABLE类型
        return typeFactory.builder().add("N", SqlTypeName.BIGINT).build(); // 检查列是否为上卷列
      } // 返回false，表示不是上卷列
 // 检查上卷列在聚合中是否有效
      @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 父节点和连接配置
        return new AbstractEnumerable<Object[]>() { // 返回true，表示有效
          @Override public Enumerator<Object[]> enumerator() {
            return new Enumerator<Object[]>() { // 生成动态行类型的表
              @Override public Object[] current() { // JSON格式的行类型定义和行数
                return new Object[] {}; // 创建动态行类型表
              }
 // 一个行类型由解析JSON参数确定的表。该类实现了一个可扫描表，其行类型通过解析JSON字符串动态确定。
              @Override public boolean moveNext() { // 动态行类型表类，继承抽象表
                return false; // 实现可扫描表接口
              } // JSON格式的行类型定义
 // 构造方法
              @Override public void reset() { // 保存JSON行类型定义
              } // 获取行类型
 // 尝试解析JSON
              @Override public void close() { // 使用RelJsonReader读取JSON类型
              } // 捕获IO异常
            }; // 将异常作为运行时异常抛出
          } // 扫描表数据
        }; // 返回空的可枚举对象
      }
 // 一个将数字添加到输入游标第一列的表函数。该方法接收一个偏移量和一个可枚举对象，将每个输入行的第一列加上偏移量后返回。
      @Override public Statistic getStatistic() { // 偏移量
        return Statistics.UNKNOWN; // 输入的可枚举对象数组
      } // 创建抽象可查询表
 // 获取行类型
      @Override public Schema.TableType getJdbcTableType() { // 创建类型构建器
        return Schema.TableType.TABLE; // 添加result列，类型为INTEGER
      } // 构建行类型
 // 转换为可查询对象
      @Override public boolean isRolledUp(String column) { // 查询提供者、模式、表名
        return false; // 创建可枚举对象
      } // 对每个输入行的第一列加上偏移量
 // 忽略类型检查警告
      @Override public boolean rolledUpColumnValidInsideAgg(String column, SqlCall call, // 返回可查询对象
          @Nullable SqlNode parent, @Nullable CalciteConnectionConfig config) {
        return true; // 一个函数，计算第一个输入游标的第二列、第一个输入的第二列和给定整数的和。该方法处理两个输入游标，将第一个游标的第二列和第二个游标的n字段相加，再加上偏移量。
      } // 偏移量
    }; // 两个输入的可枚举对象
  } // 创建抽象可查询表
 // 获取行类型
  /** A function that generates the Fibonacci sequence. // 创建类型构建器
   * Interesting because it has one column and no arguments. */ // 添加result列，类型为INTEGER
  public static ScannableTable fibonacciTableWithLimit(final long limit) { // 构建行类型
    return new ScannableTable() { // 转换为可查询对象
      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 查询提供者、模式、表名
        return typeFactory.builder().add("N", SqlTypeName.BIGINT).build(); // 创建可枚举对象
      } // 将两个游标zip在一起，计算和
 // 忽略类型检查警告
      @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 返回可查询对象
        return new AbstractEnumerable<Object[]>() {
          @Override public Enumerator<Object[]> enumerator() { // 创建视图表
            return new Enumerator<Object[]>() { // 创建视图表对象
              private long prev = 1; // 构建包含"c"INTEGER列的行类型
              private long current = 0; // SQL语句，包含values子句和参数s
 // 路径和视图名称列表
              @Override public Object[] current() {
                return new Object[] {current}; // 创建字符串视图表
              } // 创建视图表对象
 // 构建包含"c"VARCHAR列的行类型
              @Override public boolean moveNext() { // SQL语句，包含values子句和引号字符串
                final long next = current + prev; // 路径和视图名称列表
                if (limit >= 0 && next > limit) {
                  return false; // 创建包含两个对象的字符串视图表
                } // 断言o是有效常量
                prev = current; // 断言p是有效常量
                current = next; // 创建视图表对象
                return true; // 构建包含"c"VARCHAR列的行类型
              } // SQL语句，包含两个values
 // 第二个value
              @Override public void reset() { // 路径和视图名称列表
                prev = 0;
                current = 1; // 包含int和String字段的类。该类用于表示一个包含整数和字符串的复合值，常用于测试。
              } // IntString类
 // 整数字段
              @Override public void close() { // 字符串字段
              } // 构造方法
            }; // 初始化整数字段
          } // 初始化字符串字段
        }; // 重写toString方法
      } // 返回格式化的字符串表示

      @Override public Statistic getStatistic() { // 一个具有非静态eval方法的UDF示例，并且使用命名参数。该类演示了如何创建一个用户定义函数，使用非静态eval方法和命名参数。
        return Statistics.UNKNOWN; // MyPlusFunction类，实现加法功能
      } // 实例计数器，使用ThreadLocal保证线程安全
 // 初始化为新的AtomicInteger
      @Override public Schema.TableType getJdbcTableType() { // 注意：未标记为确定性函数
        return Schema.TableType.TABLE; // 构造方法
      } // 增加实例计数
 // eval方法，计算两个整数的和
      @Override public boolean isRolledUp(String column) { // 两个命名参数x和y
        return false; // 返回x和y的和
      }
 // 与MyPlusFunction类似，但构造方法有一个FunctionContext参数。该类演示了如何使用FunctionContext参数来获取函数的参数信息。
      @Override public boolean rolledUpColumnValidInsideAgg(String column, SqlCall call, // MyPlusInitFunction类
          @Nullable SqlNode parent, @Nullable CalciteConnectionConfig config) { // 实例计数器
        return true; // 初始化为新的AtomicInteger
      } // 线程摘要信息
    }; // 初始化为空字符串
  } // 初始化的Y值
 // 构造方法，接收FunctionContext参数
  public static ScannableTable dynamicRowTypeTable(String jsonRowType, // 增加实例计数
      int rowCount) { // 创建StringBuilder
    return new DynamicRowTypeTable(jsonRowType, rowCount); // 获取参数数量
  } // 添加参数数量
 // 遍历所有参数
  /** A table whose row type is determined by parsing a JSON argument. */ // 添加参数索引
  private static class DynamicRowTypeTable extends AbstractTable // 如果参数是常量
      implements ScannableTable { // 添加常量信息
    private final String jsonRowType; // 获取参数值
 // 如果参数不是常量
    DynamicRowTypeTable(String jsonRowType, int count) { // 添加非常量信息
      this.jsonRowType = jsonRowType; // 设置线程摘要
    } // 初始化Y值
 // 如果第二个参数是常量，使用其值
    @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 否则使用默认值100
      try { // eval方法
        return RelJsonReader.readType(typeFactory, jsonRowType); // 两个命名参数x和y
      } catch (IOException e) { // 返回x和初始化Y值的和
        throw Util.throwAsRuntime(e);
      } // 与MyPlusFunction类似，但声明为确定性函数。该类演示了如何创建一个确定性用户定义函数，使用@Deterministic注解标记。
    } // MyDeterministicPlusFunction类
 // 实例计数器
    @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 初始化为新的AtomicInteger
      return Linq4j.emptyEnumerable(); // 构造方法，标记为确定性
    } // 增加实例计数
  } // eval方法，计算两个整数的和
 // 两个命名参数x和y，类型为Integer
  /** Table function that adds a number to the first column of input cursor. */ // 如果任一参数为null
  public static QueryableTable processCursor(final int offset, // 返回null
      final Enumerable<Object[]> a) { // 返回x和y的和
    return new AbstractQueryableTable(Object[].class) {
      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 一个使用命名参数的UDF示例。该类演示了如何创建一个用户定义函数，使用命名参数。
        return typeFactory.builder() // MyLeftFunction类，实现字符串截取功能
            .add("result", SqlTypeName.INTEGER) // eval方法，返回字符串的前n个字符
            .build(); // 两个命名参数：字符串s和长度n
      } // 返回字符串s从0到n的子串

      @Override public <T> Queryable<T> asQueryable(QueryProvider queryProvider, // 一个使用命名参数的UDF示例，部分参数是可选的。该类演示了如何创建一个用户定义函数，使用命名参数，其中部分参数是可选的。
          SchemaPlus schema, String tableName) { // MyAbcdeFunction类
        final Enumerable<Integer> enumerable = // eval方法，返回格式化的字符串
            a.select(a0 -> offset + ((Integer) a0[0])); // 参数A是必需的
        //noinspection unchecked // 参数B是可选的
        return (Queryable) enumerable.asQueryable(); // 参数C是必需的
      } // 参数D和E是可选的
    }; // 返回格式化的字符串
  } // 包含所有参数的值

  /** // 一个非严格UDF的示例（当传入NULL时也会执行有用操作）。该类演示了如何创建一个非严格用户定义函数，能够处理null值。
   * A function that sums the second column of first input cursor, second // MyToStringFunction类，将对象转换为字符串
   * column of first input and the given int. // eval方法，将对象转换为字符串
   */ // 如果对象为null
  public static QueryableTable processCursors(final int offset, // 返回"<null>"
      final Enumerable<Object[]> a, final Enumerable<IntString> b) { // 返回对象toString的结果，用尖括号包围
    return new AbstractQueryableTable(Object[].class) {
      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 一个半严格UDF的示例（如果参数为null或长度为4，则返回null）。该类演示了如何创建一个半严格用户定义函数，使用@SemiStrict注解标记。
        return typeFactory.builder() // Null4Function类
            .add("result", SqlTypeName.INTEGER) // eval方法，标记为半严格
            .build(); // 如果字符串为null或长度为4
      } // 返回null
 // 返回原字符串
      @Override public <T> Queryable<T> asQueryable(QueryProvider queryProvider,
          SchemaPlus schema, String tableName) { // 一个挑剔的半严格UDF示例。如果参数为null，抛出NullPointerException。如果参数长度为8，返回null。该类演示了如何创建一个半严格用户定义函数，对null值有特殊处理。
        final Enumerable<Integer> enumerable = // Null8Function类
            a.zip(b, (v0, v1) -> ((Integer) v0[1]) + v1.n + offset); // eval方法，标记为半严格
        //noinspection unchecked // 如果字符串长度为8
        return (Queryable) enumerable.asQueryable(); // 返回null
      } // 返回原字符串
    };
  } // 一个具有静态eval方法的UDF示例。类是抽象的，但代码生成器不应该需要实例化它。该类演示了如何创建一个抽象用户定义函数，使用静态eval方法。
 // MyDoubleFunction抽象类
  public static TranslatableTable view(String s) { // 私有构造方法，防止实例化
    return new ViewTable(Object.class, typeFactory -> // 静态eval方法，计算x的两倍
        typeFactory.builder().add("c", SqlTypeName.INTEGER).build(), // 返回x乘以2的结果
        "values (1), (3), " + s, ImmutableList.of(), Arrays.asList("view"));
  } // 一个具有非默认构造方法的UDF示例。未使用；我们目前没有方法通过非默认构造方法实例化函数对象。
 // FibonacciTableFunction类
  public static TranslatableTable strView(String s) { // 斐波那契序列的限制值
    return new ViewTable(Object.class, typeFactory -> // 构造方法，接收限制值
        typeFactory.builder().add("c", SqlTypeName.VARCHAR, 100).build(), // 保存限制值
        "values (" + CalciteSqlDialect.DEFAULT.quoteStringLiteral(s) + ")", // eval方法，返回斐波那契表
        ImmutableList.of(), Arrays.asList("view")); // 调用fibonacciTableWithLimit方法
  }
 // 一个具有两个参数的用户定义函数。该类演示了如何创建一个具有两个参数的用户定义函数。
  public static TranslatableTable str(Object o, Object p) { // MyIncrement类，实现增量计算
    assertThat(RexLiteral.validConstant(o, Litmus.THROW), is(true)); // eval方法，计算增量值
    assertThat(RexLiteral.validConstant(p, Litmus.THROW), is(true)); // 返回x加上x*y/100的结果
    return new ViewTable(Object.class, typeFactory ->
        typeFactory.builder().add("c", SqlTypeName.VARCHAR, 100).build(), // 一个声明异常的用户定义函数。该类演示了如何创建一个会抛出异常的用户定义函数。
        "values " + CalciteSqlDialect.DEFAULT.quoteStringLiteral(o.toString()) // MyExceptionFunction类
            + ", " + CalciteSqlDialect.DEFAULT.quoteStringLiteral(p.toString()), // 构造方法
        ImmutableList.of(), Arrays.asList("view")); // 静态eval方法，声明异常
  } // 如果x小于0
 // 抛出IllegalArgumentException
  /** Class with int and String fields. */ // 如果x大于100
  public static class IntString { // 抛出IOException
    public final int n; // 返回x加10的结果
    public final String s;
 // 一个具有无元括号的用户定义函数。该类演示了如何创建一个无参数的用户定义函数。
    public IntString(int n, String s) { // MyNiladicParenthesesFunction类
      this.n = n; // 这是一个测试无元括号的常量函数，它返回常量值"foo"
      this.s = s; // eval方法，无参数
    } // 返回常量字符串"foo"

    @Override public String toString() { // 一个具有重载UDF的示例（同名，不同参数）。该类演示了如何创建一个无参数的用户定义函数。
      return "{n=" + n + ", s=" + s + "}"; // CountArgs0Function抽象类
    } // 私有构造方法，防止实例化
  } // 静态eval方法，无参数
 // 返回0
  /** Example of a UDF with a non-static {@code eval} method,
   * and named parameters. */ // 参见CountArgs0Function。该类演示了如何创建一个具有一个参数的用户定义函数。
  public static class MyPlusFunction { // CountArgs1Function抽象类
    public static final ThreadLocal<AtomicInteger> INSTANCE_COUNT = // 私有构造方法，防止实例化
        ThreadLocal.withInitial(AtomicInteger::new); // 静态eval方法，一个整数参数
 // 返回1
    // Note: Not marked @Deterministic
    public MyPlusFunction() { // 参见CountArgs0Function。该类演示了如何创建一个具有一个可空Short参数的用户定义函数。
      INSTANCE_COUNT.get().incrementAndGet(); // CountArgs1NullableFunction抽象类
    } // 私有构造方法，防止实例化
 // 静态eval方法，一个Short参数
    public int eval(@Parameter(name = "x") int x, // 返回-1
        @Parameter(name = "y") int y) {
      return x + y; // 参见CountArgs0Function。该类演示了如何创建一个具有两个参数的用户定义函数。
    } // CountArgs2Function抽象类
  } // 私有构造方法，防止实例化
 // 静态eval方法，两个整数参数
  /** As {@link MyPlusFunction} but constructor has a // 返回2
   * {@link org.apache.calcite.schema.FunctionContext} parameter. */
  public static class MyPlusInitFunction { // 一个需要实例化但无法实例化的UDF类示例。该类演示了一个抽象类，具有私有的构造方法，因此无法被实例化。
    public static final ThreadLocal<AtomicInteger> INSTANCE_COUNT = // AwkwardFunction抽象类
        ThreadLocal.withInitial(AtomicInteger::new); // 私有构造方法，防止实例化
    public static final ThreadLocal<String> THREAD_DIGEST = // eval方法，一个整数参数
        new ThreadLocal<>(); // 返回0

    private final int initY; // 一个具有多个方法（部分重载）的UDF类。该类演示了如何在一个类中定义多个用户定义函数，包括重载方法。
 // MultipleFunction类
    public MyPlusInitFunction(FunctionContext fx) { // 私有构造方法，防止实例化
      INSTANCE_COUNT.get().incrementAndGet(); // 三个重载方法
      final StringBuilder b = new StringBuilder(); // fun1方法，String参数
      final int parameterCount = fx.getParameterCount(); // 返回小写字符串
      b.append("parameterCount=").append(parameterCount); // fun1方法，int参数
      for (int i = 0; i < parameterCount; i++) { // 返回x乘以2的结果
        b.append("; argument ").append(i); // fun1方法，两个int参数
        if (fx.isArgumentConstant(i)) { // 返回x加y的结果
          b.append(" is constant and has value ") // 另一个方法
              .append(fx.getArgumentValueAs(i, String.class)); // fun2方法，int参数
        } else { // 返回x乘以3的结果
          b.append(" is not constant"); // 非静态方法无法使用，因为构造方法是私有的
        } // nonStatic方法，int参数
      } // 返回x乘以3的结果
      THREAD_DIGEST.set(b.toString());
      this.initY = fx.isArgumentConstant(1) // 一个为每种数据类型提供用户定义函数的UDF类。该类演示了如何为各种数据类型创建用户定义函数。
          ? fx.getArgumentValueAs(1, Integer.class) // 标记为确定性函数
          : 100; // AllTypesFunction类
    } // 私有构造方法，防止实例化
 // 我们使用SqlFunctions.toLong(Date)而不是Date.getTime()
    public int eval(@Parameter(name = "x") int x, // 和SqlFunctions.internalToTimestamp(long)而不是new Date(long)
        @Parameter(name = "y") int y) { // 因为JDBC（也被UDF使用）的约定是表示
      return x + initY; // 本地时区的日期时间值
    } // dateFun方法，将Date转换为long
  } // 如果为null返回-1，否则转换为long
 // timestampFun方法，将Timestamp转换为long
  /** As {@link MyPlusFunction} but declared to be deterministic. */ // 如果为null返回-1，否则转换为long
  public static class MyDeterministicPlusFunction { // timeFun方法，将Time转换为long
    public static final ThreadLocal<AtomicInteger> INSTANCE_COUNT = // 如果为null返回-1，否则转换为long
        ThreadLocal.withInitial(AtomicInteger::new); // 与toDateFun(Long)重载，具有挑战性。该方法演示了如何处理重载方法。
 // toDateFun方法，int参数
    @Deterministic public MyDeterministicPlusFunction() { // 将int转换为Date
      INSTANCE_COUNT.get().incrementAndGet(); // toDateFun方法，Long参数
    } // 如果为null返回null，否则转换为Date
 // toTimestampFun方法，将Long转换为Timestamp
    public Integer eval(@Parameter(name = "x") Integer x, // toTimeFun方法，将Long转换为Time
        @Parameter(name = "y") Integer y) { // 如果为null返回null，否则转换为Time
      if (x == null || y == null) { // 对于具有double和BigDecimal参数的重载用户定义函数会出现问题。该方法演示了如何处理不同数值类型的转换。
        return null; // toDouble方法，BigDecimal参数
      } // 如果为null返回0.0，否则转换为double
      return x + y; // toDouble方法，Double参数
    } // 如果为null返回0.0，否则返回原值
  } // toDouble方法，Float参数
 // 如果为null返回0.0，否则转换为double
  /** Example of a UDF with named parameters. */ // arrayAppendFun方法，向列表添加元素
  public static class MyLeftFunction { // 如果列表或元素为null
    public String eval(@Parameter(name = "s") String s, // 返回null
        @Parameter(name = "n") int n) { // 否则
      return s.substring(0, n); // 添加元素到列表
    } // 返回列表
  } // 具有DATE、TIMESTAMP和TIME参数的重载函数。该方法演示了如何处理不同日期时间类型的转换。
 // toLong方法，Date参数
  /** Example of a UDF with named parameters, some of them optional. */ // 如果为null返回0，否则转换为long
  public static class MyAbcdeFunction { // toLong方法，Timestamp参数
    public String eval(@Parameter(name = "A", optional = false) Integer a, // 如果为null返回0，否则转换为long
        @Parameter(name = "B", optional = true) Integer b, // toLong方法，Time参数
        @Parameter(name = "C", optional = false) Integer c, // 如果为null返回0，否则转换为long
        @Parameter(name = "D", optional = true) Integer d,
        @Parameter(name = "E", optional = true) Integer e) { // 一个用户定义聚合函数（UDAF）的示例。该类演示了如何创建一个用户定义聚合函数，实现求和功能。
      return "{a: " + a + ", b: " + b +  ", c: " + c +  ", d: " + d  + ", e: " // MySumFunction类
          + e + "}"; // 构造方法
    } // init方法，初始化累加器
  } // 返回0作为初始值
 // add方法，添加值到累加器
  /** Example of a non-strict UDF. (Does something useful when passed NULL.) */ // 返回累加器加上新值的结果
  public static class MyToStringFunction { // merge方法，合并两个累加器
    public static String eval(@Parameter(name = "o") Object o) { // 返回两个累加器的和
      if (o == null) { // result方法，返回最终结果
        return "<null>"; // 返回累加器的值
      }
      return "<" + o.toString() + ">"; // 一个用于定义用户定义聚合函数的通用接口。该接口定义了聚合函数的标准方法，使用泛型支持不同类型。
    } // MyGenericAggFunction接口
  } // 累加器类型
 // 值类型
  /** Example of a semi-strict UDF. // 结果类型
   * (Returns null if its parameter is null or if its length is 4.) */ // 初始化方法，返回初始累加器
  public static class Null4Function { // 添加方法，将值添加到累加器
    @SemiStrict public static String eval(@Parameter(name = "s") String s) { // 合并方法，合并两个累加器
      if (s == null || s.length() == 4) { // 结果方法，从累加器获取最终结果
        return null;
      } // 一个实现通用接口的用户定义聚合函数示例。该类演示了如何实现通用聚合函数接口。
      return s; // MySum3类
    } // 实现MyGenericAggFunction接口，类型都为Integer
  } // 初始化方法
 // 返回0作为初始值
  /** Example of a picky, semi-strict UDF. // 添加方法
   * Throws {@link NullPointerException} if argument is null. // 返回累加器加上新值的结果
   * Returns null if its argument's length is 8. */ // 合并方法
  public static class Null8Function { // 返回两个累加器的和
    @SemiStrict public static String eval(@Parameter(name = "s") String s) { // 结果方法
      if (s.length() == 8) { // 返回累加器的值
        return null;
      } // 一个用户定义聚合函数（UDAF）示例，其方法是静态的。该类演示了如何创建一个使用静态方法的用户定义聚合函数。
      return s; // MyStaticSumFunction类
    } // 静态init方法
  } // 返回0作为初始值
 // 静态add方法
  /** Example of a UDF with a static {@code eval} method. Class is abstract, // 返回累加器加上新值的结果
   * but code-generator should not need to instantiate it. */ // 静态merge方法
  public abstract static class MyDoubleFunction { // 返回两个累加器的和
    private MyDoubleFunction() { // 静态result方法
    } // 返回累加器的值

    public static int eval(int x) { // 一个具有两个参数的用户定义聚合函数（UDAF）示例。构造方法有一个初始化参数。该类演示了如何创建一个具有两个参数和过滤条件的用户定义聚合函数。
      return x * 2; // MyTwoParamsSumFunctionFilter1类
    } // 构造方法，接收FunctionContext
  } // 确保fx不为null
 // 断言参数数量为2
  /** Example of a UDF with non-default constructor. // init方法，初始化累加器
   * // 返回0作为初始值
   * <p>Not used; we do not currently have a way to instantiate function // add方法，添加值到累加器
   * objects other than via their default constructor. */ // 如果v1大于v2
  public static class FibonacciTableFunction { // 返回累加器加上v1的结果
    private final int limit; // 否则返回累加器
 // merge方法，合并两个累加器
    public FibonacciTableFunction(int limit) { // 返回两个累加器的和
      this.limit = limit; // result方法，返回最终结果
    } // 返回累加器的值

    public ScannableTable eval() { // 另一个具有两个参数的用户定义聚合函数（UDAF）示例。该类演示了如何创建一个具有不同类型参数的聚合函数。
      return fibonacciTableWithLimit(limit); // MyTwoParamsSumFunctionFilter2类
    } // 构造方法
  } // init方法，初始化累加器
 // 返回0作为初始值
  /** User-defined function with two arguments. */ // add方法，添加值到累加器
  public static class MyIncrement { // 如果v2等于"Eric"
    public float eval(int x, int y) { // 返回累加器加上v1的结果
      return x + x * y / 100; // 否则返回累加器
    } // merge方法，合并两个累加器
  } // 返回两个累加器的和
 // result方法，返回最终结果
  /** User-defined function that declares exceptions. */ // 返回累加器的值
  public static class MyExceptionFunction {
    public MyExceptionFunction() {} // 一个用户定义聚合函数（UDAF）示例，其方法是静态的。该类演示了如何创建一个具有三个参数和过滤条件的静态方法聚合函数。
 // MyThreeParamsSumFunctionWithFilter1类
    public static int eval(int x) throws IllegalArgumentException, IOException { // 静态init方法
      if (x < 0) { // 返回0作为初始值
        throw new IllegalArgumentException("Illegal argument: " + x); // 静态add方法，三个参数
      } else if (x > 100) { // 如果v2等于v3
        throw new IOException("IOException when argument > 100"); // 返回累加器加上v1的结果
      } // 否则返回累加器
      return x + 10; // 静态merge方法
    } // 返回两个累加器的和
  } // 静态result方法
 // 返回累加器的值
  /** User-defined function with niladic parentheses. */
  public static class MyNiladicParenthesesFunction { // 一个用户定义聚合函数（UDAF）示例，其方法是静态的。与MyThreeParamsSumFunctionWithFilter1类似，但参数类型不同。该类演示了如何创建一个具有三个整数参数的静态方法聚合函数。
    // This is a constant function that tests for niladic parentheses, // MyThreeParamsSumFunctionWithFilter2类
    // and it returns the constant value foo // 静态init方法
    public String eval() { // 返回0作为初始值
      return "foo"; // 静态add方法，三个整数参数
    } // 如果v3大于250
  } // 返回累加器加上v1和v2的结果
 // 否则返回累加器
  /** Example of a UDF that has overloaded UDFs (same name, different args). */ // 静态merge方法
  public abstract static class CountArgs0Function { // 返回两个累加器的和
    private CountArgs0Function() {} // 静态result方法
 // 返回累加器的值
    public static int eval() {
      return 0; // 用户定义函数。该类演示了一个具有不兼容累加器类型的聚合函数示例。
    } // SumFunctionBadIAdd类
  } // init方法，初始化累加器
 // 返回0作为初始值
  /** See {@link CountArgs0Function}. */ // add方法，累加器类型为short
  public abstract static class CountArgs1Function { // 使用Math.addExact添加值，但类型不匹配
    private CountArgs1Function() {}
 // 用户定义表宏函数。该类演示了如何创建一个用户定义的表宏函数。
    public static int eval(int x) { // TableMacroFunction类
      return 1; // eval方法，返回可转换表
    } // 调用view方法创建视图
  }
 // 一个eval方法为静态的用户定义表宏函数。该类演示了如何创建一个使用静态eval方法的表宏函数。
  /** See {@link CountArgs0Function}. */ // StaticTableMacroFunction类
  public abstract static class CountArgs1NullableFunction { // 静态eval方法，返回可转换表
    private CountArgs1NullableFunction() {} // 调用view方法创建视图

    public static int eval(Short x) { // 一个具有命名和可选参数的用户定义表宏函数。该类演示了如何创建一个使用命名和可选参数的表宏函数。
      return -1; // TableMacroFunctionWithNamedParameters类
    } // eval方法，返回可转换表
  } // 参数R是可选的
 // 参数S是必需的
  /** See {@link CountArgs0Function}. */ // 参数T是可选的
  public abstract static class CountArgs2Function { // 创建StringBuilder
    private CountArgs2Function() {} // 添加参数R
 // 添加参数S
    public static int eval(int x, int y) { // 添加参数T
      return 2; // 调用view方法创建视图
    } // abc方法，将参数添加到StringBuilder
  } // 如果参数不为null
 // 如果StringBuilder不为空
  /** Example of a UDF class that needs to be instantiated but cannot be. */ // 添加逗号和空格
  public abstract static class AwkwardFunction { // 添加括号包围的参数值
    private AwkwardFunction() {
    } // 一个具有命名和可选参数的用户定义表宏函数。该类演示了如何创建一个具有四个参数的表宏函数。
 // AnotherTableMacroFunctionWithNamedParameters类
    public int eval(int x) { // eval方法，返回可转换表
      return 0; // 参数R是可选的
    } // 参数S是必需的
  } // 参数T是可选的
 // 参数S2是可选的
  /** UDF class that has multiple methods, some overloaded. */ // 创建StringBuilder
  public static class MultipleFunction { // 添加参数R
    private MultipleFunction() {} // 添加参数S
 // 添加参数T
    // Three overloads // 调用view方法创建视图
    public static String fun1(String x) { // abc方法，将参数添加到StringBuilder
      return x.toLowerCase(Locale.ROOT); // 如果参数不为null
    } // 如果StringBuilder不为空
    public static int fun1(int x) { // 添加逗号和空格
      return x * 2; // 添加括号包围的参数值
    }
    public static int fun1(int x, int y) { // 一个返回QueryableTable的表函数。该类演示了如何创建一个简单的表函数。
      return x + y; // SimpleTableFunction类
    } // eval方法，返回可查询表
 // 调用generateStrings方法生成字符串序列表
    // Another method
    public static int fun2(int x) { // 一个返回QueryableTable的表函数。该类演示了如何创建一个处理字符串参数的表函数。
      return x * 3; // MyTableFunction类
    } // eval方法，返回可查询表
 // 调用oneThreePlus方法生成包含1、3和解析值的表
    // Non-static method cannot be used because constructor is private
    public int nonStatic(int x) { // 一个通过静态方法返回QueryableTable的表函数。该类演示了如何创建一个使用静态eval方法的表函数。
      return x * 3; // TestStaticTableFunction类
    } // 静态eval方法，返回可查询表
  } // 调用oneThreePlus方法生成包含1、3和解析值的表

  /** UDF class that provides user-defined functions for each data type. */ // 一个返回其输入值的表函数。该类演示了如何创建一个返回单个值的表函数。
  @Deterministic // IdentityTableFunction类
  public static class AllTypesFunction { // 静态eval方法，返回可查询表
    private AllTypesFunction() {} // 调用identity方法生成包含单个整数的表

    // We use SqlFunctions.toLong(Date) ratter than Date.getTime(), // 真正的MazeTable可以在example/function中找到。这是一个用于支持测试的简化版本。该类演示了如何创建一个迷宫表，用于测试表函数功能。
    // and SqlFunctions.internalToTimestamp(long) rather than new Date(long), // MazeTable类，继承抽象表
    // because the contract of JDBC (also used by UDFs) is to represent // 实现可扫描表接口
    // date-time values in the LOCAL time zone. // 迷宫内容
 // 构造方法
    public static long dateFun(java.sql.Date v) { // 保存迷宫内容
      return v == null ? -1L : SqlFunctions.toLong(v); // generate方法，生成迷宫表
    } // 创建新的MazeTable
    public static long timestampFun(java.sql.Timestamp v) { // 格式化字符串
      return v == null ? -1L : SqlFunctions.toLong(v); // 包含宽度、高度和种子
    } // generate2方法，生成迷宫表（第二个版本）
    public static long timeFun(java.sql.Time v) { // 命名参数：宽度
      return v == null ? -1L : SqlFunctions.toLong(v); // 命名参数：高度
    } // 命名参数：种子（可选）
 // 创建新的MazeTable
    /** Overloaded, in a challenging way, with {@link #toDateFun(Long)}. */ // 格式化字符串
    public static java.sql.Date toDateFun(int v) { // 包含宽度、高度和种子
      return SqlFunctions.internalToDate(v); // generate3方法，生成迷宫表（第三个版本）
    } // 命名参数：foo
 // 创建新的MazeTable
    public static java.sql.Date toDateFun(Long v) { // 格式化字符串，包含foo
      return v == null ? null : SqlFunctions.internalToDate(v.intValue()); // 获取行类型
    } // 创建类型构建器
    public static java.sql.Timestamp toTimestampFun(Long v) { // 添加"S"列，类型为VARCHAR，长度为12
      return SqlFunctions.internalToTimestamp(v); // 构建行类型
    } // 扫描表数据
    public static java.sql.Time toTimeFun(Long v) { // 创建行数据
      return v == null ? null : SqlFunctions.internalToTime(v.intValue()); // 将行数据转换为可枚举对象
    }
 // 包含一个具有大量列的prod表的Schema。该类演示了如何创建一个包含宽表的Schema。
    /** For overloaded user-defined functions that have {@code double} and // WideSaleSchema类
     * {@code BigDecimal} arguments will go wrong. */ // 重写toString方法
    public static double toDouble(BigDecimal var) { // 返回Schema名称
      return var == null ? 0.0d : var.doubleValue(); // 抑制未使用警告
    } // prod表，包含一个产品销售记录
    public static double toDouble(Double var) { // 创建产品销售记录，产品ID为100，销售量为10
      return var == null ? 0.0d : var;
    } // 一个具有大量列的表。该类演示了如何创建一个具有很多列的产品销售表，用于测试宽表处理。
    public static double toDouble(Float var) { // 抑制未使用警告
      return var == null ? 0.0d : Double.valueOf(var.toString()); // WideProductSale类
    } // 产品ID
 // 销售量0
    public static List arrayAppendFun(List v, Integer i) { // 销售量1，默认值为10
      if (v == null || i == null) { // 销售量2，默认值为10
        return null; // 销售量3，默认值为10
      } else { // 销售量4，默认值为10
        v.add(i); // 销售量5，默认值为10
        return v; // 销售量6，默认值为10
      } // 销售量7，默认值为10
    } // 销售量8，默认值为10
 // 销售量9，默认值为10
    /** Overloaded functions with DATE, TIMESTAMP and TIME arguments. */ // 销售量10，默认值为10
    public static long toLong(Date date) { // 销售量11，默认值为10
      return date == null ? 0 : SqlFunctions.toLong(date); // 销售量12，默认值为10
    } // 销售量13，默认值为10
 // 销售量14，默认值为10
    public static long toLong(Timestamp timestamp) { // 销售量15，默认值为10
      return timestamp == null ? 0 : SqlFunctions.toLong(timestamp); // 销售量16，默认值为10
    } // 销售量17，默认值为10
 // 销售量18，默认值为10
    public static long toLong(Time time) { // 销售量19，默认值为10
      return time == null ? 0 : SqlFunctions.toLong(time); // 销售量20，默认值为10
    } // 销售量21，默认值为10
 // 销售量22，默认值为10
  } // 销售量23，默认值为10
 // 销售量24，默认值为10
  /** Example of a user-defined aggregate function (UDAF). */ // 销售量25，默认值为10
  public static class MySumFunction { // 销售量26，默认值为10
    public MySumFunction() { // 销售量27，默认值为10
    } // 销售量28，默认值为10
    public long init() { // 销售量29，默认值为10
      return 0L; // 销售量30，默认值为10
    } // 销售量31，默认值为10
    public long add(long accumulator, int v) { // 销售量32，默认值为10
      return accumulator + v; // 销售量33，默认值为10
    } // 销售量34，默认值为10
    public long merge(long accumulator0, long accumulator1) { // 销售量35，默认值为10
      return accumulator0 + accumulator1; // 销售量36，默认值为10
    } // 销售量37，默认值为10
    public long result(long accumulator) { // 销售量38，默认值为10
      return accumulator; // 销售量39，默认值为10
    } // 销售量40，默认值为10
  } // 销售量41，默认值为10
 // 销售量42，默认值为10
  /** A generic interface for defining user-defined aggregate functions. // 销售量43，默认值为10
   * // 销售量44，默认值为10
   * @param <A> accumulator type // 销售量45，默认值为10
   * @param <V> value type // 销售量46，默认值为10
   * @param <R> result type */ // 销售量47，默认值为10
  private interface MyGenericAggFunction<A, V, R> { // 销售量48，默认值为10
    A init(); // 销售量49，默认值为10
 // 销售量50，默认值为10
    A add(A accumulator, V val); // 销售量51，默认值为10
 // 销售量52，默认值为10
    A merge(A accumulator1, A accumulator2); // 销售量53，默认值为10
 // 销售量54，默认值为10
    R result(A accumulator); // 销售量55，默认值为10
  } // 销售量56，默认值为10
 // 销售量57，默认值为10
  /** Example of a user-defined aggregate function that implements a generic // 销售量58，默认值为10
   * interface. */ // 销售量59，默认值为10
  public static class MySum3 // 销售量60，默认值为10
      implements MyGenericAggFunction<Integer, Integer, Integer> { // 销售量61，默认值为10
    @Override public Integer init() { // 销售量62，默认值为10
      return 0; // 销售量63，默认值为10
    } // 销售量64，默认值为10
 // 销售量65，默认值为10
    @Override public Integer add(Integer accumulator, Integer val) { // 销售量66，默认值为10
      return accumulator + val; // 销售量67，默认值为10
    } // 销售量68，默认值为10
 // 销售量69，默认值为10
    @Override public Integer merge(Integer accumulator1, Integer accumulator2) { // 销售量70，默认值为10
      return accumulator1 + accumulator2; // 销售量71，默认值为10
    } // 销售量72，默认值为10
 // 销售量73，默认值为10
    @Override public Integer result(Integer accumulator) { // 销售量74，默认值为10
      return accumulator; // 销售量75，默认值为10
    } // 销售量76，默认值为10
  } // 销售量77，默认值为10
 // 销售量78，默认值为10
  /** Example of a user-defined aggregate function (UDAF), whose methods are // 销售量79，默认值为10
   * static. */ // 销售量80，默认值为10
  public static class MyStaticSumFunction { // 销售量81，默认值为10
    public static long init() { // 销售量82，默认值为10
      return 0L; // 销售量83，默认值为10
    } // 销售量84，默认值为10
    public static long add(long accumulator, int v) { // 销售量85，默认值为10
      return accumulator + v; // 销售量86，默认值为10
    } // 销售量87，默认值为10
    public static long merge(long accumulator0, long accumulator1) { // 销售量88，默认值为10
      return accumulator0 + accumulator1; // 销售量89，默认值为10
    } // 销售量90，默认值为10
    public static long result(long accumulator) { // 销售量91，默认值为10
      return accumulator; // 销售量92，默认值为10
    } // 销售量93，默认值为10
  } // 销售量94，默认值为10
 // 销售量95，默认值为10
  /** Example of a user-defined aggregate function (UDAF) with two parameters. // 销售量96，默认值为10
   * The constructor has an initialization parameter. */ // 销售量97，默认值为10
  public static class MyTwoParamsSumFunctionFilter1 { // 销售量98，默认值为10
    public MyTwoParamsSumFunctionFilter1(FunctionContext fx) { // 销售量99，默认值为10
      requireNonNull(fx, "fx"); // 销售量100，默认值为10
      assert fx.getParameterCount() == 2; // 销售量101，默认值为10
    } // 销售量102，默认值为10
    public int init() { // 销售量103，默认值为10
      return 0; // 销售量104，默认值为10
    } // 销售量105，默认值为10
    public int add(int accumulator, int v1, int v2) { // 销售量106，默认值为10
      if (v1 > v2) { // 销售量107，默认值为10
        return accumulator + v1; // 销售量108，默认值为10
      } // 销售量109，默认值为10
      return accumulator; // 销售量110，默认值为10
    } // 销售量111，默认值为10
    public int merge(int accumulator0, int accumulator1) { // 销售量112，默认值为10
      return accumulator0 + accumulator1; // 销售量113，默认值为10
    } // 销售量114，默认值为10
    public int result(int accumulator) { // 销售量115，默认值为10
      return accumulator; // 销售量116，默认值为10
    } // 销售量117，默认值为10
  } // 销售量118，默认值为10
 // 销售量119，默认值为10
  /** Another example of a user-defined aggregate function (UDAF) with two // 销售量120，默认值为10
   * parameters. */ // 销售量121，默认值为10
  public static class MyTwoParamsSumFunctionFilter2 { // 销售量122，默认值为10
    public MyTwoParamsSumFunctionFilter2() { // 销售量123，默认值为10
    } // 销售量124，默认值为10
    public long init() { // 销售量125，默认值为10
      return 0L; // 销售量126，默认值为10
    } // 销售量127，默认值为10
    public long add(long accumulator, int v1, String v2) { // 销售量128，默认值为10
      if (v2.equals("Eric")) { // 销售量129，默认值为10
        return accumulator + v1; // 销售量130，默认值为10
      } // 销售量131，默认值为10
      return accumulator; // 销售量132，默认值为10
    } // 销售量133，默认值为10
    public long merge(long accumulator0, long accumulator1) { // 销售量134，默认值为10
      return accumulator0 + accumulator1; // 销售量135，默认值为10
    } // 销售量136，默认值为10
    public long result(long accumulator) { // 销售量137，默认值为10
      return accumulator; // 销售量138，默认值为10
    } // 销售量139，默认值为10
  } // 销售量140，默认值为10
 // 销售量141，默认值为10
  /** Example of a user-defined aggregate function (UDAF), whose methods are // 销售量142，默认值为10
   * static. */ // 销售量143，默认值为10
  public static class MyThreeParamsSumFunctionWithFilter1 { // 销售量144，默认值为10
    public static long init() { // 销售量145，默认值为10
      return 0L; // 销售量146，默认值为10
    } // 销售量147，默认值为10
    public static long add(long accumulator, int v1, String v2, String v3) { // 销售量148，默认值为10
      if (v2.equals(v3)) { // 销售量149，默认值为10
        return accumulator + v1; // 销售量150，默认值为10
      } // 销售量151，默认值为10
      return accumulator; // 销售量152，默认值为10
    } // 销售量153，默认值为10
    public static long merge(long accumulator0, long accumulator1) { // 销售量154，默认值为10
      return accumulator0 + accumulator1; // 销售量155，默认值为10
    } // 销售量156，默认值为10
    public static long result(long accumulator) { // 销售量157，默认值为10
      return accumulator; // 销售量158，默认值为10
    } // 销售量159，默认值为10
  } // 销售量160，默认值为10
 // 销售量161，默认值为10
  /** Example of a user-defined aggregate function (UDAF), whose methods are // 销售量162，默认值为10
   * static. Similar to {@link MyThreeParamsSumFunctionWithFilter1}, but // 销售量163，默认值为10
   * argument types are different. */ // 销售量164，默认值为10
  public static class MyThreeParamsSumFunctionWithFilter2 { // 销售量165，默认值为10
    public static long init() { // 销售量166，默认值为10
      return 0L; // 销售量167，默认值为10
    } // 销售量168，默认值为10
    public static long add(long accumulator, int v1, int v2, int v3) { // 销售量169，默认值为10
      if (v3 > 250) { // 销售量170，默认值为10
        return accumulator + v1 + v2; // 销售量171，默认值为10
      } // 销售量172，默认值为10
      return accumulator; // 销售量173，默认值为10
    } // 销售量174，默认值为10
    public static long merge(long accumulator0, long accumulator1) { // 销售量175，默认值为10
      return accumulator0 + accumulator1; // 销售量176，默认值为10
    } // 销售量177，默认值为10
    public static long result(long accumulator) { // 销售量178，默认值为10
      return accumulator; // 销售量179，默认值为10
    } // 销售量180，默认值为10
  } // 销售量181，默认值为10
 // 销售量182，默认值为10
  /** User-defined function. */ // 销售量183，默认值为10
  public static class SumFunctionBadIAdd { // 销售量184，默认值为10
    public long init() { // 销售量185，默认值为10
      return 0L; // 销售量186，默认值为10
    } // 销售量187，默认值为10
    public long add(short accumulator, int v) { // 销售量188，默认值为10
      return Math.addExact(accumulator, v); // 销售量189，默认值为10
    } // 销售量190，默认值为10
  } // 销售量191，默认值为10
 // 销售量192，默认值为10
  /** User-defined table-macro function. */ // 销售量193，默认值为10
  public static class TableMacroFunction { // 销售量194，默认值为10
    public TranslatableTable eval(String s) { // 销售量195，默认值为10
      return view(s); // 销售量196，默认值为10
    } // 销售量197，默认值为10
  } // 销售量198，默认值为10
 // 销售量199，默认值为10
  /** User-defined table-macro function whose eval method is static. */ // 构造方法
  public static class StaticTableMacroFunction { // 设置产品ID
    public static TranslatableTable eval(String s) { // 设置销售量0
      return view(s);
    } // TableMacro接口的实现，具有返回Queryable表的apply方法。该类演示了如何实现TableMacro接口。
  } // SimpleTableMacro类，实现TableMacro接口
 // apply方法，返回可转换表
  /** User-defined table-macro function with named and optional parameters. */ // 创建新的SimpleTable
  public static class TableMacroFunctionWithNamedParameters { // getParameters方法，返回参数列表
    public TranslatableTable eval( // 返回空列表，表示无参数
        @Parameter(name = "R", optional = true) String r,
        @Parameter(name = "S") String s, // 一个具有(A, B)列的表。该类演示了如何创建一个简单的可查询表，实现TranslatableTable接口。
        @Parameter(name = "T", optional = true) Integer t) { // SimpleTable类，继承抽象可查询表
      final StringBuilder sb = new StringBuilder(); // 实现可转换表接口
      abc(sb, r); // 列名数组
      abc(sb, s); // 列类型数组
      abc(sb, t); // 行数据数组
      return view(sb.toString()); // 构造方法
    } // 调用父类构造方法，元素类型为Object数组
 // 第一行数据
    private static void abc(StringBuilder sb, Object s) { // 第二行数据
      if (s != null) { // 第三行数据
        if (sb.length() > 0) { // 获取行类型
          sb.append(", "); // 获取列数
        } // 创建列描述列表
        sb.append('(').append(s).append(')'); // 设置容量
      } // 遍历所有列
    } // 创建列类型
  } // 根据Java类型创建RelDataType
 // 添加列名和类型到描述
  /** User-defined table-macro function with named and optional parameters. */ // 创建结构类型
  public static class AnotherTableMacroFunctionWithNamedParameters { // 获取迭代器
    public TranslatableTable eval( // 将枚举器转换为迭代器
        @Parameter(name = "R", optional = true) String r, // 获取枚举器
        @Parameter(name = "S") String s, // 调用枚举器实现方法
        @Parameter(name = "T", optional = true) Integer t, // 转换为可查询对象
        @Parameter(name = "S2", optional = true) String s2) { // 查询提供者、模式、表名
      final StringBuilder sb = new StringBuilder(); // 创建抽象表可查询对象
      abc(sb, r); // 表名
      abc(sb, s); // 创建枚举器
      abc(sb, t); // 忽略类型检查警告
      return view(sb.toString()); // 调用枚举器实现方法
    } // 枚举器实现方法
 // 返回枚举器
    private static void abc(StringBuilder sb, Object s) { // 当前行
      if (s != null) { // 创建迭代器
        if (sb.length() > 0) { // 从行数据创建迭代器
          sb.append(", "); // 获取当前元素
        } // 返回当前行
        sb.append('(').append(s).append(')'); // 移动到下一个元素
      } // 如果还有元素
    } // 获取下一行
  } // 如果指定了字段，转换行
 // 返回true，表示成功移动
  /** A table function that returns a {@link QueryableTable}. */ // 如果没有更多元素
  public static class SimpleTableFunction { // 设置当前行为null
    public QueryableTable eval(Integer s) { // 返回false，表示没有更多元素
      return generateStrings(s); // 重置枚举器
    } // 抛出不支持操作异常
  } // 关闭枚举器
 // 空操作
  /** A table function that returns a {@link QueryableTable}. */ // 转换行方法
  public static class MyTableFunction { // 创建新数组
    public QueryableTable eval(String s) { // 遍历所有字段
      return oneThreePlus(s); // 复制指定字段
    } // 返回转换后的行
  } // 转换为关系节点
 // 转换上下文
  /** A table function that returns a {@link QueryableTable} via a // 关系优化表
   * static method. */ // 创建可枚举表扫描节点
  public static class TestStaticTableFunction { // 获取表达式
    public static QueryableTable eval(String s) { // 创建方法调用表达式
      return oneThreePlus(s); // 调用SimpleTable构造方法
    } // asQueryable方法
  } // null参数
 // 模式表达式
  /** A table function that returns its input value. */ // 表名常量
  public static class IdentityTableFunction { // 调用asEnumerable方法
    public static QueryableTable eval(Integer i) { // 返回可枚举表达式
      return identity(i);
    }
  }

  /** The real MazeTable may be found in example/function. This is a cut-down
   * version to support a test. */
  public static class MazeTable extends AbstractTable
      implements ScannableTable {

    private final String content;

    public MazeTable(String content) {
      this.content = content;
    }

    public static ScannableTable generate(int width, int height, int seed) {
      return new MazeTable(
          String.format(Locale.ROOT, "generate(w=%d, h=%d, s=%d)", width,
              height, seed));
    }

    public static ScannableTable generate2(
        @Parameter(name = "WIDTH") int width,
        @Parameter(name = "HEIGHT") int height,
        @Parameter(name = "SEED", optional = true) Integer seed) {
      return new MazeTable(
          String.format(Locale.ROOT, "generate2(w=%d, h=%d, s=%d)", width,
              height, seed));
    }

    public static ScannableTable generate3(
        @Parameter(name = "FOO") String foo) {
      return new MazeTable(
          String.format(Locale.ROOT, "generate3(foo=%s)", foo));
    }

    @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) {
      return typeFactory.builder()
          .add("S", SqlTypeName.VARCHAR, 12)
          .build();
    }

    @Override public Enumerable<@Nullable Object[]> scan(DataContext root) {
      Object[][] rows = {{"abcde"}, {"xyz"}, {content}};
      return Linq4j.asEnumerable(rows);
    }
  }

  /** Schema containing a {@code prod} table with a lot of columns. */
  public static class WideSaleSchema {
    @Override public String toString() {
      return "WideSaleSchema";
    }

    @SuppressWarnings("unused")
    public final WideProductSale[] prod = {
        new WideProductSale(100, 10)
    };
  }

  /** Table with a lot of columns. */
  @SuppressWarnings("unused")
  public static class WideProductSale {
    public final int prodId;
    public final double sale0;
    public final double sale1 = 10;
    public final double sale2 = 10;
    public final double sale3 = 10;
    public final double sale4 = 10;
    public final double sale5 = 10;
    public final double sale6 = 10;
    public final double sale7 = 10;
    public final double sale8 = 10;
    public final double sale9 = 10;
    public final double sale10 = 10;
    public final double sale11 = 10;
    public final double sale12 = 10;
    public final double sale13 = 10;
    public final double sale14 = 10;
    public final double sale15 = 10;
    public final double sale16 = 10;
    public final double sale17 = 10;
    public final double sale18 = 10;
    public final double sale19 = 10;
    public final double sale20 = 10;
    public final double sale21 = 10;
    public final double sale22 = 10;
    public final double sale23 = 10;
    public final double sale24 = 10;
    public final double sale25 = 10;
    public final double sale26 = 10;
    public final double sale27 = 10;
    public final double sale28 = 10;
    public final double sale29 = 10;
    public final double sale30 = 10;
    public final double sale31 = 10;
    public final double sale32 = 10;
    public final double sale33 = 10;
    public final double sale34 = 10;
    public final double sale35 = 10;
    public final double sale36 = 10;
    public final double sale37 = 10;
    public final double sale38 = 10;
    public final double sale39 = 10;
    public final double sale40 = 10;
    public final double sale41 = 10;
    public final double sale42 = 10;
    public final double sale43 = 10;
    public final double sale44 = 10;
    public final double sale45 = 10;
    public final double sale46 = 10;
    public final double sale47 = 10;
    public final double sale48 = 10;
    public final double sale49 = 10;
    public final double sale50 = 10;
    public final double sale51 = 10;
    public final double sale52 = 10;
    public final double sale53 = 10;
    public final double sale54 = 10;
    public final double sale55 = 10;
    public final double sale56 = 10;
    public final double sale57 = 10;
    public final double sale58 = 10;
    public final double sale59 = 10;
    public final double sale60 = 10;
    public final double sale61 = 10;
    public final double sale62 = 10;
    public final double sale63 = 10;
    public final double sale64 = 10;
    public final double sale65 = 10;
    public final double sale66 = 10;
    public final double sale67 = 10;
    public final double sale68 = 10;
    public final double sale69 = 10;
    public final double sale70 = 10;
    public final double sale71 = 10;
    public final double sale72 = 10;
    public final double sale73 = 10;
    public final double sale74 = 10;
    public final double sale75 = 10;
    public final double sale76 = 10;
    public final double sale77 = 10;
    public final double sale78 = 10;
    public final double sale79 = 10;
    public final double sale80 = 10;
    public final double sale81 = 10;
    public final double sale82 = 10;
    public final double sale83 = 10;
    public final double sale84 = 10;
    public final double sale85 = 10;
    public final double sale86 = 10;
    public final double sale87 = 10;
    public final double sale88 = 10;
    public final double sale89 = 10;
    public final double sale90 = 10;
    public final double sale91 = 10;
    public final double sale92 = 10;
    public final double sale93 = 10;
    public final double sale94 = 10;
    public final double sale95 = 10;
    public final double sale96 = 10;
    public final double sale97 = 10;
    public final double sale98 = 10;
    public final double sale99 = 10;
    public final double sale100 = 10;
    public final double sale101 = 10;
    public final double sale102 = 10;
    public final double sale103 = 10;
    public final double sale104 = 10;
    public final double sale105 = 10;
    public final double sale106 = 10;
    public final double sale107 = 10;
    public final double sale108 = 10;
    public final double sale109 = 10;
    public final double sale110 = 10;
    public final double sale111 = 10;
    public final double sale112 = 10;
    public final double sale113 = 10;
    public final double sale114 = 10;
    public final double sale115 = 10;
    public final double sale116 = 10;
    public final double sale117 = 10;
    public final double sale118 = 10;
    public final double sale119 = 10;
    public final double sale120 = 10;
    public final double sale121 = 10;
    public final double sale122 = 10;
    public final double sale123 = 10;
    public final double sale124 = 10;
    public final double sale125 = 10;
    public final double sale126 = 10;
    public final double sale127 = 10;
    public final double sale128 = 10;
    public final double sale129 = 10;
    public final double sale130 = 10;
    public final double sale131 = 10;
    public final double sale132 = 10;
    public final double sale133 = 10;
    public final double sale134 = 10;
    public final double sale135 = 10;
    public final double sale136 = 10;
    public final double sale137 = 10;
    public final double sale138 = 10;
    public final double sale139 = 10;
    public final double sale140 = 10;
    public final double sale141 = 10;
    public final double sale142 = 10;
    public final double sale143 = 10;
    public final double sale144 = 10;
    public final double sale145 = 10;
    public final double sale146 = 10;
    public final double sale147 = 10;
    public final double sale148 = 10;
    public final double sale149 = 10;
    public final double sale150 = 10;
    public final double sale151 = 10;
    public final double sale152 = 10;
    public final double sale153 = 10;
    public final double sale154 = 10;
    public final double sale155 = 10;
    public final double sale156 = 10;
    public final double sale157 = 10;
    public final double sale158 = 10;
    public final double sale159 = 10;
    public final double sale160 = 10;
    public final double sale161 = 10;
    public final double sale162 = 10;
    public final double sale163 = 10;
    public final double sale164 = 10;
    public final double sale165 = 10;
    public final double sale166 = 10;
    public final double sale167 = 10;
    public final double sale168 = 10;
    public final double sale169 = 10;
    public final double sale170 = 10;
    public final double sale171 = 10;
    public final double sale172 = 10;
    public final double sale173 = 10;
    public final double sale174 = 10;
    public final double sale175 = 10;
    public final double sale176 = 10;
    public final double sale177 = 10;
    public final double sale178 = 10;
    public final double sale179 = 10;
    public final double sale180 = 10;
    public final double sale181 = 10;
    public final double sale182 = 10;
    public final double sale183 = 10;
    public final double sale184 = 10;
    public final double sale185 = 10;
    public final double sale186 = 10;
    public final double sale187 = 10;
    public final double sale188 = 10;
    public final double sale189 = 10;
    public final double sale190 = 10;
    public final double sale191 = 10;
    public final double sale192 = 10;
    public final double sale193 = 10;
    public final double sale194 = 10;
    public final double sale195 = 10;
    public final double sale196 = 10;
    public final double sale197 = 10;
    public final double sale198 = 10;
    public final double sale199 = 10;

    public WideProductSale(int prodId, double sale) {
      this.prodId = prodId;
      this.sale0 = sale;
    }
  }

  /**
   * Implementation of {@link TableMacro} interface with
   * {@link #apply} method that returns {@link Queryable} table.
   */
  public static class SimpleTableMacro implements TableMacro {

    @Override public TranslatableTable apply(List<?> arguments) {
      return new SimpleTable();
    }

    @Override public List<FunctionParameter> getParameters() {
      return Collections.emptyList();
    }
  }

  /** Table with columns (A, B). */
  public static class SimpleTable extends AbstractQueryableTable
      implements TranslatableTable {
    private final String[] columnNames = { "A", "B" };
    private final Class<?>[] columnTypes = { String.class, Integer.class };
    private final Object[][] rows = new Object[3][];

    public SimpleTable() {
      super(Object[].class);

      rows[0] = new Object[] { "foo", 5 };
      rows[1] = new Object[] { "bar", 4 };
      rows[2] = new Object[] { "foo", 3 };
    }

    @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) {
      int columnCount = columnNames.length;
      final PairList<String, RelDataType> columnDesc =
          PairList.withCapacity(columnCount);
      for (int i = 0; i < columnCount; i++) {
        final RelDataType colType = typeFactory
            .createJavaType(columnTypes[i]);
        columnDesc.add(columnNames[i], colType);
      }
      return typeFactory.createStructType(columnDesc);
    }

    public Iterator<Object[]> iterator() {
      return Linq4j.enumeratorIterator(enumerator());
    }

    public Enumerator<Object[]> enumerator() {
      return enumeratorImpl(null);
    }

    @Override public <T> Queryable<T> asQueryable(QueryProvider queryProvider,
        SchemaPlus schema, String tableName) {
      return new AbstractTableQueryable<T>(queryProvider, schema, this,
          tableName) {
        @Override public Enumerator<T> enumerator() {
          //noinspection unchecked
          return (Enumerator<T>) enumeratorImpl(null);
        }
      };
    }

    private Enumerator<Object[]> enumeratorImpl(final int[] fields) {
      return new Enumerator<Object[]>() {
        private Object[] current;
        private final Iterator<Object[]> iterator = Arrays.asList(rows)
            .iterator();

        @Override public Object[] current() {
          return current;
        }

        @Override public boolean moveNext() {
          if (iterator.hasNext()) {
            Object[] full = iterator.next();
            current = fields != null ? convertRow(full) : full;
            return true;
          } else {
            current = null;
            return false;
          }
        }

        @Override public void reset() {
          throw new UnsupportedOperationException();
        }

        @Override public void close() {
          // noop
        }

        private Object[] convertRow(Object[] full) {
          final Object[] objects = new Object[fields.length];
          for (int i = 0; i < fields.length; i++) {
            objects[i] = full[fields[i]];
          }
          return objects;
        }
      };
    }

    @Override public RelNode toRel(
        RelOptTable.ToRelContext context,
        RelOptTable relOptTable) {
      return EnumerableTableScan.create(context.getCluster(), relOptTable);
    }

    @Override public Expression getExpression(SchemaPlus schema, String tableName, Class clazz) {
      MethodCallExpression queryableExpression =
          Expressions.call(Expressions.new_(SimpleTable.class),
              BuiltInMethod.QUERYABLE_TABLE_AS_QUERYABLE.method,
              Expressions.constant(null),
              Schemas.expression(schema),
              Expressions.constant(tableName));
      return Expressions.call(queryableExpression,
          BuiltInMethod.QUERYABLE_AS_ENUMERABLE.method);
    }
  }
}
