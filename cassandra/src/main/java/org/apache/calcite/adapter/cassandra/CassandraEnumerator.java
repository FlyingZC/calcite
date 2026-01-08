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
package org.apache.calcite.adapter.cassandra;  // Cassandra适配器包，包含Cassandra数据库的适配器实现

import org.apache.calcite.avatica.util.ByteString;  // Avatica工具类，用于处理字节字符串
import org.apache.calcite.linq4j.Enumerator;  // LINQ4j接口，定义了枚举器的行为，用于遍历数据集合
import org.apache.calcite.rel.type.RelDataTypeFactory;  // 关系类型工厂接口，用于创建关系数据类型
import org.apache.calcite.rel.type.RelDataTypeField;  // 关系类型字段，表示表中的一个列及其类型
import org.apache.calcite.rel.type.RelDataTypeSystem;  // 关系类型系统，定义类型系统的默认行为
import org.apache.calcite.rel.type.RelProtoDataType;  // 关系原型数据类型，用于延迟解析数据类型
import org.apache.calcite.sql.type.SqlTypeFactoryImpl;  // SQL类型工厂实现类

import com.datastax.oss.driver.api.core.cql.ResultSet;  // Cassandra驱动API，CQL查询结果集
import com.datastax.oss.driver.api.core.cql.Row;  // Cassandra驱动API，CQL查询结果行
import com.datastax.oss.driver.api.core.data.TupleValue;  // Cassandra驱动API，元组值类型
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;  // Cassandra驱动API，类型编解码器注册表

import org.checkerframework.checker.nullness.qual.Nullable;  // Checker框架注解，标记可空类型

import java.nio.ByteBuffer;  // Java NIO字节缓冲区
import java.time.Instant;  // Java时间API，表示时间戳
import java.time.LocalDate;  // Java时间API，表示日期（年月日）
import java.time.LocalTime;  // Java时间API，表示时间（时分秒）
import java.util.Date;  // Java旧版日期类
import java.util.Iterator;  // Java迭代器接口
import java.util.LinkedHashSet;  // Java集合类，有序的HashSet
import java.util.List;  // Java列表接口
import java.util.Objects;  // Java工具类，用于对象操作
import java.util.stream.IntStream;  // Java流API，整数流

import static java.util.Objects.requireNonNull;  // 静态导入Objects.requireNonNull方法

/** Enumerator that reads from a Cassandra column family. */  // 类注释：从Cassandra列族（表）读取数据的枚举器
class CassandraEnumerator implements Enumerator<Object> {  // CassandraEnumerator类实现Enumerator接口，用于遍历Cassandra查询结果
  private final Iterator<Row> iterator;  // 成员变量：Cassandra结果集的迭代器，用于逐行遍历查询结果，final表示不可变
  private final List<RelDataTypeField> fieldTypes;  // 成员变量：字段类型列表，存储结果集中每个字段的类型信息，final表示不可变
  @Nullable private Row current;  // 成员变量：当前行对象，存储迭代器当前位置的行数据，@Nullable表示可以为null

  /** Creates a CassandraEnumerator.  // 方法注释：创建CassandraEnumerator实例
   *
   * @param results Cassandra result set ({@link com.datastax.oss.driver.api.core.cql.ResultSet})  // 参数：Cassandra查询结果集
   * @param protoRowType The type of resulting rows  // 参数：结果行的类型原型，用于延迟解析行类型
   */
  CassandraEnumerator(ResultSet results, RelProtoDataType protoRowType) {  // 构造方法：初始化Cassandra枚举器
    this.iterator = results.iterator();  // 从结果集中获取迭代器，用于遍历查询结果
    this.current = null;  // 初始化当前行为null，表示尚未开始遍历

    final RelDataTypeFactory typeFactory =  // 创建关系类型工厂，用于解析数据类型
        new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT);  // 使用默认的关系类型系统创建SQL类型工厂
    this.fieldTypes = protoRowType.apply(typeFactory).getFieldList();  // 应用类型原型到工厂，获取字段类型列表
  }  // 构造方法结束

  /** Produces the next row from the results.  // 方法注释：从结果中产生下一行数据
   *
   * @return A new row from the results  // 返回值：结果集中的新行数据
   */
  @Override public Object current() {  // 实现Enumerator接口的current方法，返回当前行的数据
    if (fieldTypes.size() == 1) {  // 如果只有一个字段
      // If we just have one field, produce it directly  // 单字段时直接返回该字段的值
      return currentRowField(0);  // 返回当前行的第一个字段值
    } else {  // 如果有多个字段
      // Build an array with all fields in this row  // 构建包含当前行所有字段的数组
      Object[] row = new Object[fieldTypes.size()];  // 创建对象数组，大小为字段数量
      for (int i = 0; i < fieldTypes.size(); i++) {  // 遍历所有字段
        row[i] = currentRowField(i);  // 获取当前行第i个字段的值并放入数组
      }  // 循环结束

      return row;  // 返回包含所有字段值的数组
    }  // if-else结束
  }  // current方法结束

  /** Get a field for the current row from the underlying object.  // 方法注释：从底层对象获取当前行的指定字段
   *
   * @param index Index of the field within the Row object  // 参数：字段在Row对象中的索引位置
   */
  private @Nullable Object currentRowField(int index) {  // 私有方法：获取当前行指定索引的字段值
    requireNonNull(current, "current");  // 检查current不为null，否则抛出NullPointerException
    final Object o =  // 从当前行获取指定索引的字段值
         current.get(index,  // 获取第index列的值
             CodecRegistry.DEFAULT.codecFor(  // 使用默认编解码器注册表获取对应的编解码器
                 current.getColumnDefinitions().get(index).getType()));  // 根据列类型获取编解码器

    return convertToEnumeratorObject(o);  // 将获取的值转换为枚举器期望的内部表示
  }  // currentRowField方法结束

  /** Convert an object into the expected internal representation.  // 方法注释：将对象转换为期望的内部表示形式
   *
   * @param obj Object to convert, if needed  // 参数：需要转换的对象（如果需要）
   */
  private @Nullable Object convertToEnumeratorObject(@Nullable Object obj) {  // 私有方法：类型转换方法，将Cassandra类型转换为Calcite内部类型
    if (obj instanceof ByteBuffer) {  // 如果对象是字节缓冲区（BLOB类型）
      ByteBuffer buf = (ByteBuffer) obj;  // 强制转换为ByteBuffer
      byte [] bytes = new byte[buf.remaining()];  // 创建字节数组，大小为缓冲区剩余字节数
      buf.get(bytes, 0, bytes.length);  // 将缓冲区数据读取到字节数组
      return new ByteString(bytes);  // 将字节数组包装为ByteString返回
    } else if (obj instanceof LocalDate) {  // 如果对象是LocalDate（日期类型）
      // converts dates to the expected numeric format  // 将日期转换为期望的数字格式
      return ((LocalDate) obj).toEpochDay();  // 转换为从1970-01-01开始的天数
    } else if (obj instanceof Date) {  // 如果对象是旧版Date类
      @SuppressWarnings("JdkObsolete")  // 抑制过时API警告
      long milli = ((Date) obj).toInstant().toEpochMilli();  // 转换为毫秒时间戳
      return milli;  // 返回毫秒时间戳
    } else if (obj instanceof Instant) {  // 如果对象是Instant（时间戳类型）
      return ((Instant) obj).toEpochMilli();  // 转换为毫秒时间戳返回
    } else if (obj instanceof LocalTime) {  // 如果对象是LocalTime（时间类型）
      return ((LocalTime) obj).toNanoOfDay();  // 转换为当天的纳秒数返回
    } else if (obj instanceof LinkedHashSet) {  // 如果对象是LinkedHashSet（MULTISET类型）
      // MULTISET is handled as an array  // MULTISET作为数组处理
      return ((LinkedHashSet<?>) obj).toArray();  // 转换为数组返回
    } else if (obj instanceof TupleValue) {  // 如果对象是TupleValue（STRUCT类型）
      // STRUCT can be handled as an array  // STRUCT作为数组处理
      final TupleValue tupleValue = (TupleValue) obj;  // 强制转换为TupleValue
      int numComponents = tupleValue.getType().getComponentTypes().size();  // 获取元组的组件数量
      return IntStream.range(0, numComponents)  // 创建从0到numComponents-1的整数流
          .mapToObj(i ->  // 将每个索引映射为对应的组件值
              tupleValue.get(i,  // 获取第i个组件的值
                  CodecRegistry.DEFAULT.codecFor(  // 使用默认编解码器注册表获取编解码器
                      tupleValue.getType().getComponentTypes().get(i))))  // 根据组件类型获取编解码器
          .map(this::convertToEnumeratorObject)  // 递归转换每个组件为枚举器对象
          .map(Objects::requireNonNull) // "null" cannot appear inside collections  // 确保集合内不能出现null值
          .toArray();  // 收集为数组返回
    }  // if-else链结束

    return obj;  // 其他类型直接返回原对象
  }  // convertToEnumeratorObject方法结束

  @Override public boolean moveNext() {  // 实现Enumerator接口的moveNext方法，移动到下一行
    if (iterator.hasNext()) {  // 如果迭代器还有下一行
      current = iterator.next();  // 移动到下一行并更新current
      return true;  // 返回true表示成功移动
    } else {  // 如果迭代器没有下一行
      return false;  // 返回false表示已到末尾
    }  // if-else结束
  }  // moveNext方法结束

  @Override public void reset() {  // 实现Enumerator接口的reset方法，重置枚举器
    throw new UnsupportedOperationException();  // 抛出不支持操作异常，Cassandra结果集不支持重置
  }  // reset方法结束

  @Override public void close() {  // 实现Enumerator接口的close方法，关闭枚举器
    // Nothing to do here  // 无需执行任何操作，资源由ResultSet管理
  }  // close方法结束
}  // CassandraEnumerator类结束
