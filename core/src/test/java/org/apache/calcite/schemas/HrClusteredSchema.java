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
package org.apache.calcite.schemas; // 声明包名，该类属于org.apache.calcite.schemas包，这是Calcite框架中用于定义数据模式(schema)的包

import org.apache.calcite.DataContext; // 导入DataContext类，用于在查询执行过程中传递上下文信息
import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口，用于表示可枚举的数据集合，是LINQ4J库的核心接口
import org.apache.calcite.linq4j.Linq4j; // 导入Linq4j工具类，提供了将Java集合转换为Enumerable对象的静态方法
import org.apache.calcite.rel.RelCollations; // 导入RelCollations工具类，用于创建和管理关系表达式的排序规则
import org.apache.calcite.rel.RelFieldCollation; // 导入RelFieldCollation类，用于描述单个字段的排序方向和空值处理方式
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，用于表示关系数据类型，描述表的结构
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建RelDataType对象的工厂类
import org.apache.calcite.schema.ScannableTable; // 导入ScannableTable接口，表示可以被扫描的表，提供scan方法获取数据
import org.apache.calcite.schema.Statistic; // 导入Statistic接口，用于提供表的统计信息，如行数、排序、键等
import org.apache.calcite.schema.Statistics; // 导入Statistics工具类，用于创建Statistic对象的静态工厂方法
import org.apache.calcite.schema.Table; // 导入Table接口，是Calcite中所有表的基接口
import org.apache.calcite.schema.impl.AbstractSchema; // 导入AbstractSchema抽象类，提供了Schema的基本实现，子类只需实现getTableMap方法
import org.apache.calcite.schema.impl.AbstractTable; // 导入AbstractTable抽象类，提供了Table的基本实现，简化了表的定义
import org.apache.calcite.util.ImmutableBitSet; // 导入ImmutableBitSet类，用于表示不可变的位集合，常用于标识哪些列是主键

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList类，用于创建不可变的列表
import com.google.common.collect.ImmutableMap; // 导入Google Guava的ImmutableMap类，用于创建不可变的映射

import org.checkerframework.checker.nullness.qual.Nullable; // 导入注解，用于标记可能为null的值，帮助静态分析工具检测空指针问题

import java.util.ArrayList; // 导入ArrayList类，用于动态数组列表
import java.util.Arrays; // 导入Arrays工具类，提供操作数组的静态方法
import java.util.List; // 导入List接口，表示有序的集合
import java.util.Map; // 导入Map接口，表示键值对映射
import java.util.function.Function; // 导入Function函数式接口，用于表示接受一个参数并产生结果的函数

/**
 * A typical HR schema with employees (emps) and departments (depts) tables that are naturally // 一个典型的人力资源(HR)模式，包含员工(emps)和部门(depts)表，这些表基于主键自然排序，代表聚集表
 * ordered based on their primary keys representing clustered tables. // 基于主键自然排序，表示这些表是按照主键物理存储的聚集表
 */ // 类级别的Javadoc注释结束
public final class HrClusteredSchema extends AbstractSchema { // 定义HrClusteredSchema类，继承自AbstractSchema，final表示该类不能被继承

  private final ImmutableMap<String, Table> tables; // 定义一个不可变的Map，键是表名(String类型)，值是Table对象，用于存储该schema中的所有表

  public HrClusteredSchema() { // 无参构造函数，用于创建HrClusteredSchema实例
    tables = ImmutableMap.<String, Table>builder() // 创建ImmutableMap构建器，用于构建不可变的表映射
        .put("emps", // 向映射中添加第一个表，表名为"emps"（员工表）
            new PkClusteredTable( // 创建一个新的PkClusteredTable实例，这是一个按主键聚集的表
                factory -> // 使用lambda表达式定义一个函数，接受RelDataTypeFactory参数，返回RelDataType
                    new RelDataTypeFactory.Builder(factory) // 创建RelDataType构建器，用于构建关系数据类型
                        .add("empid", factory.createJavaType(int.class)) // 添加名为"empid"的列，类型为Java的int类型，这是员工ID，主键
                        .add("deptno", factory.createJavaType(int.class)) // 添加名为"deptno"的列，类型为Java的int类型，这是部门编号，外键
                        .add("name", factory.createJavaType(String.class)) // 添加名为"name"的列，类型为Java的String类型，这是员工姓名
                        .add("salary", factory.createJavaType(int.class)) // 添加名为"salary"的列，类型为Java的int类型，这是员工薪水
                        .add("commission", factory.createJavaType(Integer.class)) // 添加名为"commission"的列，类型为Java的Integer类型，这是员工佣金，可为null
                        .build(), // 构建RelDataType对象，完成员工表的行类型定义
                ImmutableBitSet.of(0), // 创建ImmutableBitSet，包含索引0，表示第一列(empid)是主键
                Arrays.asList( // 创建一个列表，包含员工表的所有数据行，每行是一个Object数组
                    new Object[]{100, 10, "Bill", 10000, 1000}, // 第一行数据：empid=100, deptno=10, name="Bill", salary=10000, commission=1000
                    new Object[]{110, 10, "Theodore", 11500, 250}, // 第二行数据：empid=110, deptno=10, name="Theodore", salary=11500, commission=250
                    new Object[]{150, 10, "Sebastian", 7000, null}, // 第三行数据：empid=150, deptno=10, name="Sebastian", salary=7000, commission=null
                    new Object[]{200, 20, "Eric", 8000, 500}))) // 第四行数据：empid=200, deptno=20, name="Eric", salary=8000, commission=500
        .put("depts", // 向映射中添加第二个表，表名为"depts"（部门表）
            new PkClusteredTable( // 创建一个新的PkClusteredTable实例，这是一个按主键聚集的表
                factory -> // 使用lambda表达式定义一个函数，接受RelDataTypeFactory参数，返回RelDataType
                    new RelDataTypeFactory.Builder(factory) // 创建RelDataType构建器，用于构建关系数据类型
                        .add("deptno", factory.createJavaType(int.class)) // 添加名为"deptno"的列，类型为Java的int类型，这是部门编号，主键
                        .add("name", factory.createJavaType(String.class)) // 添加名为"name"的列，类型为Java的String类型，这是部门名称
                        .build(), // 构建RelDataType对象，完成部门表的行类型定义
                ImmutableBitSet.of(0), // 创建ImmutableBitSet，包含索引0，表示第一列(deptno)是主键
                Arrays.asList( // 创建一个列表，包含部门表的所有数据行，每行是一个Object数组
                    new Object[]{10, "Sales"}, // 第一行数据：deptno=10, name="Sales"（销售部）
                    new Object[]{30, "Marketing"}, // 第二行数据：deptno=30, name="Marketing"（市场部）
                    new Object[]{40, "HR"}))) // 第三行数据：deptno=40, name="HR"（人力资源部）
        .build(); // 构建ImmutableMap对象，完成表的映射
  } // 构造函数结束

  @Override protected Map<String, Table> getTableMap() { // 重写父类AbstractSchema的getTableMap方法，返回该schema中的所有表映射
    return tables; // 返回不可变的表映射
  } // getTableMap方法结束

  /**
   * A table sorted (ascending direction and nulls last) on the primary key. // 一个按主键排序的表，排序方向为升序，空值排在最后
   */ // PkClusteredTable类的Javadoc注释开始
  private static class PkClusteredTable extends AbstractTable implements ScannableTable { // 定义PkClusteredTable内部类，继承AbstractTable并实现ScannableTable接口，表示一个按主键聚集的可扫描表
    private final ImmutableBitSet pkColumns; // 定义主键列索引集合，使用ImmutableBitSet存储哪些列是主键
    private final List<Object[]> data; // 定义表的数据列表，每个元素是一个Object数组，代表一行数据
    private final Function<RelDataTypeFactory, RelDataType> typeBuilder; // 定义类型构建器函数，用于根据RelDataTypeFactory创建RelDataType

    PkClusteredTable( // PkClusteredTable构造函数，初始化聚集表
        Function<RelDataTypeFactory, RelDataType> dataTypeBuilder, // 参数：类型构建器函数，用于创建表的行类型
        ImmutableBitSet pkColumns, // 参数：主键列索引集合，标识哪些列是主键
        List<Object[]> data) { // 参数：表的数据列表
      this.data = data; // 将参数data赋值给成员变量data，存储表的数据
      this.typeBuilder = dataTypeBuilder; // 将参数dataTypeBuilder赋值给成员变量typeBuilder，存储类型构建器
      this.pkColumns = pkColumns; // 将参数pkColumns赋值给成员变量pkColumns，存储主键列索引
    } // 构造函数结束

    @Override public Statistic getStatistic() { // 重写getStatistic方法，返回表的统计信息，包括行数、键和排序
      List<RelFieldCollation> collationFields = new ArrayList<>(); // 创建一个列表，用于存储每个主键列的排序规则
      for (Integer key : pkColumns) { // 遍历主键列索引集合
        collationFields.add( // 向排序规则列表中添加一个RelFieldCollation对象
            new RelFieldCollation( // 创建RelFieldCollation对象，描述单个字段的排序
                key, // 字段索引，表示按哪一列排序
                RelFieldCollation.Direction.ASCENDING, // 排序方向为升序
                RelFieldCollation.NullDirection.LAST)); // 空值排在最后
      } // for循环结束
      return Statistics.of(data.size(), ImmutableList.of(pkColumns), // 返回Statistic对象，包含表的大小(行数)、主键集合和排序规则
          ImmutableList.of(RelCollations.of(collationFields))); // 将排序规则列表转换为RelCollations对象并包装成ImmutableList
    } // getStatistic方法结束

    @Override public RelDataType getRowType(final RelDataTypeFactory typeFactory) { // 重写getRowType方法，返回表的行类型(即表结构)
      return typeBuilder.apply(typeFactory); // 调用类型构建器函数，传入typeFactory参数，生成并返回RelDataType对象
    } // getRowType方法结束

    @Override public Enumerable<@Nullable Object[]> scan(final DataContext root) { // 重写scan方法，返回表数据的可枚举集合，用于扫描表数据
      return Linq4j.asEnumerable(data); // 将表的数据列表转换为Enumerable对象并返回，使数据可以被LINQ查询
    } // scan方法结束

  } // PkClusteredTable内部类结束
} // HrClusteredSchema类结束
