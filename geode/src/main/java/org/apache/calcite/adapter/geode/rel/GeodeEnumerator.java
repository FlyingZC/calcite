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
// 包声明：定义该类属于 org.apache.calcite.adapter.geode.rel 包，这是 Calcite 框架中 Geode 适配器的核心包
package org.apache.calcite.adapter.geode.rel;

// 导入 Calcite LINQ4J 的 Enumerator 接口，用于实现数据遍历功能
import org.apache.calcite.linq4j.Enumerator;
// 导入关系数据类型工厂接口，用于创建关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory;
// 导入关系数据类型字段类，表示关系表中的字段定义
import org.apache.calcite.rel.type.RelDataTypeField;
// 导入关系数据类型系统接口，提供默认的数据类型系统实现
import org.apache.calcite.rel.type.RelDataTypeSystem;
// 导入关系原型数据类型接口，用于延迟创建数据类型
import org.apache.calcite.rel.type.RelProtoDataType;
// 导入 SQL 类型工厂实现类，用于创建 SQL 类型的数据类型
import org.apache.calcite.sql.type.SqlTypeFactoryImpl;

// 导入 Geode 的 SelectResults 接口，表示 Geode OQL 查询的查询结果集
import org.apache.geode.cache.query.SelectResults;

// 导入 Checker Framework 的可空注解，用于标记可能为 null 的值
import org.checkerframework.checker.nullness.qual.Nullable;
// 导入 SLF4J 的 Logger 接口，用于日志记录
import org.slf4j.Logger;
// 导入 SLF4J 的 LoggerFactory 类，用于创建 Logger 实例
import org.slf4j.LoggerFactory;

// 导入 Java 集合工具类，用于创建空迭代器
import java.util.Collections;
// 导入 Java 迭代器接口，用于遍历数据集合
import java.util.Iterator;
// 导入 Java 列表接口，用于存储字段类型列表
import java.util.List;

// 静态导入 GeodeUtils 工具类的 convertToRowValues 方法，用于将 Geode 对象转换为 Calcite 行值
import static org.apache.calcite.adapter.geode.util.GeodeUtils.convertToRowValues;

/**
 * Enumerator that reads from a Geode Regions.
 * // 这是一个枚举器类，用于从 Apache Geode 的 Region（数据区域）中读取数据
 * // Geode 是一个内存数据网格平台，Region 是其核心的数据容器，类似于数据库表
 * // 该类实现了 Calcite 的 Enumerator 接口，使得 Calcite 查询引擎能够以统一的方式遍历 Geode 中的数据
 * // Enumerator 是 LINQ4J 风格的迭代器模式，支持 moveNext() 和 current() 方法进行数据遍历
 * // 该类是 Calcite 适配器模式的关键组件，负责将外部数据源（Geode）的数据转换为 Calcite 可处理的格式
 */
class GeodeEnumerator implements Enumerator<Object> {

  // 创建日志记录器实例，使用当前类的全限定名作为日志记录器的名称
  // protected 修饰符允许子类访问和复用这个日志记录器
  // static final 表示这是一个类级别的常量，所有实例共享同一个日志记录器
  protected static final Logger LOGGER = LoggerFactory.getLogger(GeodeEnumerator.class.getName());

  // 声明一个迭代器成员变量，用于遍历 Geode 查询结果集中的数据
  // Iterator 是 Java 标准库的迭代器接口，提供 hasNext() 和 next() 方法
  // final 修饰符表示迭代器在构造后不可改变，保证线程安全性和数据一致性
  // 使用原始类型 Iterator 而非泛型 Iterator<Object> 是为了兼容 Geode 的 SelectResults 接口
  private final Iterator iterator;

  // 声明当前行的数据对象，用于存储迭代器当前指向的数据项
  // @Nullable 注解表示该字段可能为 null，这是 Checker Framework 的空安全检查
  // 该字段在每次 moveNext() 调用成功后更新，在 current() 方法中返回
  // 初始值为 null，表示尚未开始遍历或已遍历完成
  private @Nullable Object current;

  // 声明字段类型列表，存储结果集中每个字段的类型信息
  // List<RelDataTypeField> 是一个关系数据类型字段的列表，每个元素描述一个字段的名称、类型等信息
  // final 修饰符表示字段类型列表在构造后不可改变，保证类型信息的稳定性
  // 该列表用于在 current() 方法中将 Geode 对象转换为 Calcite 行值时进行类型转换
  private final List<RelDataTypeField> fieldTypes;

  /**
   * Creates a GeodeEnumerator.
   * // 构造方法：创建 GeodeEnumerator 实例
   * // 该构造方法初始化枚举器的所有必要状态，包括迭代器、当前值和字段类型信息
   * // 它接收 Geode 查询结果和行类型原型，将其转换为可遍历的形式
   *
   * @param results      Geode result set ({@link SelectResults})
   * // results 参数：Geode OQL 查询的结果集，可能为 null
   * // SelectResults 是 Geode 查询 API 的核心接口，包含查询返回的所有数据
   * // 如果为 null，说明查询没有返回结果或者查询执行失败
   *
   * @param protoRowType The type of resulting rows
   * // protoRowType 参数：结果行的原型数据类型
   * // RelProtoDataType 是一个延迟计算的数据类型，只有在需要时才创建实际的数据类型
   * // 这种延迟计算模式可以优化性能，避免不必要的类型信息创建
   * // 该类型定义了结果集中每个字段的名称、类型、可空性等元数据信息
   */
  GeodeEnumerator(SelectResults results, RelProtoDataType protoRowType) {
    // 检查 Geode 查询结果是否为 null，如果为 null 则记录警告日志
    // 这是防御性编程的体现，提前发现和处理异常情况
    // null 结果可能表示查询语法错误、Region 不存在或其他 Geode 运行时问题
    if (results == null) {
      LOGGER.warn("Null OQL results!"); // 使用日志记录器输出警告信息，便于问题排查
    }
    // 初始化迭代器成员变量：如果结果集为 null，则创建一个空迭代器；否则使用结果集的迭代器
    // Collections.emptyIterator() 返回一个不可变的空迭代器，避免 NullPointerException
    // results.iterator() 返回 Geode 结果集的迭代器，用于遍历查询结果
    // 三元运算符确保无论结果集是否为 null，iterator 都有有效的初始值
    this.iterator = (results == null) ? Collections.emptyIterator() : results.iterator();
    // 初始化当前行数据为 null，表示尚未开始遍历
    // 在第一次调用 moveNext() 之前，current 应该保持为 null
    this.current = null;

    // 创建关系数据类型工厂实例，用于将原型数据类型转换为实际的数据类型
    // SqlTypeFactoryImpl 是 Calcite 提供的 SQL 类型工厂实现
    // RelDataTypeSystem.DEFAULT 提供默认的关系数据类型系统，定义了各种 SQL 类型的行为
    // final 修饰符表示类型工厂在构造后不可改变
    final RelDataTypeFactory typeFactory =
        new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT);
    // 通过类型工厂将原型数据类型转换为实际的数据类型，并获取字段列表
    // protoRowType.apply(typeFactory) 应用类型工厂，创建实际的 RelDataType 对象
    // getFieldList() 返回该数据类型的所有字段信息，包括字段名、类型、可空性等
    // 这些字段信息后续用于将 Geode 对象转换为符合 Calcite 类型的行值
    this.fieldTypes = protoRowType.apply(typeFactory).getFieldList();
  }

  /**
   * Produces the next row from the results.
   * // 获取当前行的数据，将其转换为 Calcite 可处理的行值格式
   * // 该方法是 Enumerator 接口的核心方法之一，返回当前迭代位置的行数据
   * // 调用者应该在调用 moveNext() 返回 true 后调用此方法获取当前行
   *
   * @return A rel row from the results
   * // 返回值：转换后的关系行数据，类型为 Object
   * // 实际返回的是一个对象数组或类似结构，每个元素对应结果集的一列
   * // 如果 current 为 null（即尚未调用 moveNext() 或已遍历完成），则抛出异常
   */
  @Override public @Nullable Object current() {
    // 检查当前行数据是否为 null，如果为 null 则抛出非法状态异常
    // 这是防御性编程，确保在正确的状态下调用 current() 方法
    // IllegalStateException 表示对象的内部状态不允许执行该操作
    if (current == null) {
      throw new IllegalStateException(); // 抛出异常，提示调用者先调用 moveNext()
    }
    // 调用 GeodeUtils 工具类的 convertToRowValues 方法，将 Geode 对象转换为 Calcite 行值
    // fieldTypes 参数：字段类型列表，用于指导类型转换
    // current 参数：当前的 Geode 对象，可能是 POJO、Map 或其他 Geode 支持的数据结构
    // convertToRowValues 方法会根据字段类型信息，从 Geode 对象中提取对应字段的值，并进行必要的类型转换
    // 返回值：转换后的行值对象，通常是 Object[] 数组，每个元素对应一个字段的值
    return convertToRowValues(fieldTypes, current);
  }

  // 移动到下一行数据，判断是否还有更多数据可以遍历
  // 这是 Enumerator 接口的另一个核心方法，控制遍历的进度
  // 该方法修改内部状态（current 值），并返回是否成功移动到下一行
  @Override public boolean moveNext() {
    // 检查迭代器是否还有下一个元素
    // iterator.hasNext() 是标准迭代器方法，返回 true 表示还有数据可以遍历
    if (iterator.hasNext()) {
      // 如果还有下一个元素，则获取该元素并存储到 current 成员变量中
      // iterator.next() 返回迭代器的当前元素，并将迭代器位置向前移动
      current = iterator.next(); // 更新当前行数据，为后续 current() 方法调用做准备
      return true; // 返回 true 表示成功移动到下一行，调用者可以调用 current() 获取数据
    } else {
      // 如果迭代器没有下一个元素，说明已经遍历完成
      return false; // 返回 false 表示遍历结束，没有更多数据
    }
  }

  // 重置迭代器到初始位置
  // 这是 Enumerator 接口的方法，但在此实现中不支持重置操作
  // 重置操作在某些场景下很有用，但 Geode 的 SelectResults 迭代器可能不支持重置
  @Override public void reset() {
    // 抛出不支持操作异常，表示该枚举器不支持重置功能
    // UnsupportedOperationException 是 Java 标准异常，表示不支持的操作
    // 这样设计是因为 Geode 的 SelectResults 迭代器通常是单向的，不支持重置
    throw new UnsupportedOperationException(); // 明确告知调用者此操作不被支持
  }

  // 关闭枚举器，释放相关资源
  // 这是 Enumerator 接口的方法，用于清理资源
  @Override public void close() {
    // 当前实现不需要执行任何清理操作
    // Geode 的 SelectResults 迭代器不需要显式关闭，GC 会自动回收
    // 如果未来需要关闭数据库连接、释放文件句柄等资源，可以在此处添加代码
    // Nothing to do here // 注释说明当前不需要做任何事情
  }
}
