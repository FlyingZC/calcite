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
package org.apache.calcite.adapter.generate; // 包声明：此类位于 org.apache.calcite.adapter.generate 包中，属于生成适配器包

import org.apache.calcite.adapter.java.AbstractQueryableTable; // 导入抽象可查询表基类，提供可查询表的基础实现
import org.apache.calcite.linq4j.Enumerator; // 导入枚举器接口，用于遍历数据集合
import org.apache.calcite.linq4j.QueryProvider; // 导入查询提供者接口，用于创建和执行查询
import org.apache.calcite.linq4j.Queryable; // 导入可查询接口，表示可以被LINQ查询的数据源
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示关系模型中的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建关系数据类型
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，表示Calcite中的模式（schema），可以包含表、视图等
import org.apache.calcite.schema.TableFactory; // 导入表工厂接口，用于创建表实例
import org.apache.calcite.schema.impl.AbstractTableQueryable; // 导入抽象表可查询类，提供表的可查询实现
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举，定义了SQL中的标准数据类型

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的参数

import java.util.Map; // 导入Map接口，用于存储键值对
import java.util.NoSuchElementException; // 导入无此元素异常类，当访问不存在的元素时抛出

/**
 * Table that returns a range of integers. // 类注释：RangeTable是一个返回整数范围的表，用于生成连续的整数序列
 * 这个类实现了Calcite的可查询表接口，可以生成从start到end-1的整数序列
 * 主要用于测试目的，提供一个简单可预测的数据源
 * 它实现了Enumerable接口，可以通过LINQ4j进行查询
 */
public class RangeTable extends AbstractQueryableTable { // 类定义：RangeTable继承自AbstractQueryableTable，表示一个可查询的范围表
  private final String columnName; // 成员变量：列名，表示表中唯一列的名称，该列包含整数序列
  private final int start; // 成员变量：起始值，表示整数序列的起始值（包含）
  private final int end; // 成员变量：结束值，表示整数序列的结束值（不包含）

  protected RangeTable(Class<?> elementType, String columnName, int start, // 构造方法：创建RangeTable实例，参数包括元素类型、列名、起始值和结束值
      int end) { // 构造方法参数续：结束值
    super(elementType); // 调用父类构造方法，传入元素类型，AbstractQueryableTable需要知道查询结果的元素类型
    this.columnName = columnName; // 初始化列名成员变量
    this.start = start; // 初始化起始值成员变量
    this.end = end; // 初始化结束值成员变量
  }

  /** Creates a RangeTable. */ // 方法注释：创建RangeTable实例的静态工厂方法
  public static RangeTable create(Class<?> elementType, String columnName, // 静态工厂方法：创建RangeTable实例，提供更简洁的创建方式
      int start, int end) { // 方法参数续：起始值和结束值
    return new RangeTable(elementType, columnName, start, end); // 返回新创建的RangeTable实例
  }

  public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 方法：获取表的行类型（即表的结构），返回包含一个整数列的关系数据类型
    return typeFactory.builder() // 使用类型工厂创建一个构建器，用于构建关系数据类型
        .add(columnName, SqlTypeName.INTEGER) // 添加一个列，列名为columnName，类型为INTEGER（整数类型）
        .build(); // 构建并返回关系数据类型对象
  }

  public <T> Queryable<T> asQueryable(QueryProvider queryProvider, // 方法：将表转换为可查询对象，用于执行LINQ查询
      SchemaPlus schema, String tableName) { // 方法参数续：schema（模式）和tableName（表名）
    return new AbstractTableQueryable<T>(queryProvider, schema, this, // 返回一个匿名内部类实例，继承自AbstractTableQueryable
        tableName) { // 匿名类构造参数续：表名
      public Enumerator<T> enumerator() { // 方法：创建枚举器，用于遍历表中的数据
        //noinspection unchecked // 抑制未检查的类型转换警告，因为这里我们知道类型转换是安全的
        return (Enumerator<T>) RangeTable.this.enumerator(); // 调用RangeTable的enumerator方法创建整数枚举器，并强制转换为泛型类型T
      }
    };
  }

  public Enumerator<Integer> enumerator() { // 方法：创建并返回一个整数枚举器，用于遍历从start到end-1的整数序列
    return new Enumerator<Integer>() { // 返回一个匿名内部类实例，实现Enumerator<Integer>接口
      int current = start - 1; // 成员变量：当前值，初始化为start-1，这样第一次调用moveNext()后current变为start

      public Integer current() { // 方法：返回当前元素值
        if (current >= end) { // 检查当前值是否超出范围
          throw new NoSuchElementException(); // 如果超出范围，抛出无此元素异常
        }
        return current; // 返回当前值
      }

      public boolean moveNext() { // 方法：移动到下一个元素，返回是否还有更多元素
        ++current; // 将当前值加1，移动到下一个元素
        return current < end; // 如果当前值小于end，说明还有元素，返回true；否则返回false
      }

      public void reset() { // 方法：重置枚举器到初始状态
        current = start - 1; // 将当前值重置为start-1，这样下次调用moveNext()会返回第一个元素
      }

      public void close() { // 方法：关闭枚举器，释放资源
        // 此实现不需要释放任何资源，所以方法体为空
      }
    };
  }

  /** Implementation of {@link org.apache.calcite.schema.TableFactory} that // 内部类注释：TableFactory的实现类，允许RangeTable作为自定义表包含在Calcite模型文件中
   * allows a {@link RangeTable} to be included as a custom table in a Calcite // 注释续：这样可以通过JSON或YAML配置文件来创建RangeTable实例
   * model file. */
  public static class Factory implements TableFactory<RangeTable> { // 内部类：工厂类，实现TableFactory<RangeTable>接口，用于从配置创建RangeTable
    public RangeTable create( // 方法：根据配置参数创建RangeTable实例
        SchemaPlus schema, // 参数：schema对象，表示表所属的模式
        String name, // 参数：表名
        Map<String, Object> operand, // 参数：操作数映射，包含创建表所需的配置参数（column, start, end, elementType）
        @Nullable RelDataType rowType) { // 参数：行类型（可为null），指定表的结构
      final String columnName = (String) operand.get("column"); // 从操作数映射中获取列名参数
      final int start = (Integer) operand.get("start"); // 从操作数映射中获取起始值参数
      final int end = (Integer) operand.get("end"); // 从操作数映射中获取结束值参数
      final String elementType = (String) operand.get("elementType"); // 从操作数映射中获取元素类型参数
      Class<?> type; // 声明类型变量，用于存储元素类型的Class对象
      if ("array".equals(elementType)) { // 如果元素类型为"array"
        type = Object[].class; // 设置类型为数组类型
      } else if ("object".equals(elementType)) { // 如果元素类型为"object"
        type = Object.class; // 设置类型为Object类型
      } else if ("integer".equals(elementType)) { // 如果元素类型为"integer"
        type = Integer.class; // 设置类型为Integer类型
      } else { // 如果元素类型不是以上任何一种
        throw new IllegalArgumentException( // 抛出非法参数异常
            "Illegal 'elementType' value: " + elementType); // 异常消息：非法的elementType值
      }
      return RangeTable.create(type, columnName, start, end); // 调用RangeTable的create静态方法创建并返回RangeTable实例
    }
  }
}