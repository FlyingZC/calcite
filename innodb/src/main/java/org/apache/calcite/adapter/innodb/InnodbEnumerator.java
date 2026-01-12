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
// Apache许可证声明，声明此文件的版权归属和使用条款
package org.apache.calcite.adapter.innodb; // 声明包名，此类属于org.apache.calcite.adapter.innodb包

import org.apache.calcite.avatica.util.ByteString; // 导入ByteString类，用于处理二进制数据
import org.apache.calcite.avatica.util.DateTimeUtils; // 导入DateTimeUtils类，提供日期时间工具方法
import org.apache.calcite.linq4j.Enumerator; // 导入Enumerator接口，用于枚举数据集合
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField类，表示关系数据类型字段
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，表示SQL类型名称

import com.alibaba.innodb.java.reader.page.index.GenericRecord; // 导入GenericRecord类，表示InnoDB通用记录
import com.alibaba.innodb.java.reader.util.Utils; // 导入Utils工具类，提供InnoDB读取器的实用方法

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可空类型

import java.sql.Date; // 导入Date类，表示SQL日期类型
import java.sql.Time; // 导入Time类，表示SQL时间类型
import java.sql.Timestamp; // 导入Timestamp类，表示SQL时间戳类型
import java.time.LocalDate; // 导入LocalDate类，表示本地日期
import java.util.Iterator; // 导入Iterator接口，用于迭代集合
import java.util.List; // 导入List接口，表示列表集合
import java.util.TimeZone; // 导入TimeZone类，表示时区信息

/**
 * Enumerator that reads from InnoDB data file.
 * 从InnoDB数据文件读取数据的枚举器
 * 
 * 这个类是Calcite适配器中用于读取InnoDB数据文件的核心实现类
 * 它实现了Enumerator接口，用于逐行枚举InnoDB数据文件中的记录
 * 
 * 主要功能：
 * 1. 封装了从InnoDB数据文件读取记录的迭代器
 * 2. 提供了类型转换功能，将InnoDB记录转换为Calcite内部表示
 * 3. 处理各种SQL数据类型，包括二进制、日期、时间、时间戳等
 * 4. 实现了Enumerator接口的标准方法：current()、moveNext()、reset()、close()
 * 
 * 使用场景：
 * 当Calcite查询引擎需要从InnoDB数据文件中读取数据时，会创建此类的实例
 * 通过迭代器模式逐行读取数据，并进行必要的类型转换
 * 
 * 设计模式：
 * - 迭代器模式：实现Enumerator接口，提供统一的遍历接口
 * - 适配器模式：将InnoDB的GenericRecord适配为Calcite的Object类型
 */
class InnodbEnumerator implements Enumerator<Object> { // 定义InnodbEnumerator类，实现Enumerator<Object>接口
  private final Iterator<GenericRecord> iterator; // 成员变量：InnoDB记录的迭代器，用于逐行读取数据，final表示初始化后不可改变
  private @Nullable GenericRecord current; // 成员变量：当前指向的InnoDB记录，@Nullable表示可为null，用于存储当前迭代到的记录
  private final List<RelDataTypeField> fieldTypes; // 成员变量：结果行的字段类型列表，final表示初始化后不可改变，用于描述每行的数据类型

  /**
   * Creates an InnodbEnumerator.
   * 创建InnodbEnumerator实例
   *
   * @param resultIterator result iterator，结果迭代器，包含从InnoDB数据文件读取的记录
   * @param rowType   the type of resulting rows，结果行的类型，描述每行的字段和数据类型
   */
  InnodbEnumerator(Iterator<GenericRecord> resultIterator, RelDataType rowType) { // 构造方法，初始化InnodbEnumerator实例
    this.iterator = resultIterator; // 将传入的迭代器赋值给成员变量iterator，用于后续迭代
    this.current = null; // 初始化当前记录为null，表示还没有开始迭代
    this.fieldTypes = rowType.getFieldList(); // 从行类型中获取字段列表，用于后续类型转换
  }

  /**
   * Produces the next row from the results.
   * 从结果中产生下一行数据
   *
   * @return a new row from the results，结果中的一行新数据
   * 如果只有一个字段，返回该字段的值；如果有多个字段，返回包含所有字段值的数组
   */
  @Override public Object current() { // 实现Enumerator接口的current()方法，返回当前行的数据
    if (fieldTypes.size() == 1) { // 判断是否只有一个字段
      // If we just have one field, produce it directly
      // 如果只有一个字段，直接返回该字段的值
      return currentRowField(fieldTypes.get(0)); // 获取第一个字段的值并返回
    } else { // 如果有多个字段
      // Build an array with all fields in this row
      // 构建一个包含当前行所有字段的数组
      Object[] row = new Object[fieldTypes.size()]; // 创建一个Object数组，大小为字段数量
      for (int i = 0; i < fieldTypes.size(); i++) { // 遍历所有字段
        row[i] = currentRowField(fieldTypes.get(i)); // 获取每个字段的值，存入数组
      }
      return row; // 返回包含所有字段值的数组
    }
  }

  /**
   * Get a field for the current row from the underlying object.
   * 从底层对象获取当前行的某个字段值
   * 
   * 此方法负责从当前GenericRecord中提取指定字段的值，并进行类型转换
   * 
   * @param relDataTypeField 关系数据类型字段，包含字段名和类型信息
   * @return 字段的值，可能为null
   * @throws IllegalStateException 如果当前记录为null时调用此方法
   */
  private @Nullable Object currentRowField(RelDataTypeField relDataTypeField) { // 私有方法，获取当前行的指定字段值
    if (current == null) { // 检查当前记录是否为null
      throw new IllegalStateException(); // 如果为null，抛出非法状态异常，表示还没有调用moveNext()
    }
    final Object o = current.get(relDataTypeField.getName()); // 从当前记录中根据字段名获取原始值
    return convertToEnumeratorObject(o, relDataTypeField.getType()); // 将原始值转换为Calcite内部表示并返回
  }

  /**
   * Convert an object into the expected internal representation.
   * 将对象转换为预期的内部表示形式
   * 
   * 此方法是类型转换的核心方法，负责将InnoDB存储的原始数据转换为Calcite期望的格式
   * 支持的转换类型包括：
   * - BINARY/VARBINARY：转换为ByteString
   * - TIMESTAMP/TIMESTAMP_WITH_LOCAL_TIME_ZONE：转换为时间戳的毫秒值
   * - TIME：转换为时间的毫秒值
   * - DATE：转换为Unix日期值
   * - 其他类型：直接返回原值
   * 
   * @param obj         object to convert, if needed，需要转换的对象，可能为null
   * @param relDataType data type，数据类型信息，包含SQL类型名称和精度
   * @return 转换后的对象，如果输入为null则返回null
   */
  private static @Nullable Object convertToEnumeratorObject( // 私有静态方法，转换对象为Calcite内部表示
      @Nullable Object obj, RelDataType relDataType) { // 参数：可能为null的对象，关系数据类型
    if (obj == null) { // 检查对象是否为null
      return null; // 如果为null，直接返回null
    }
    SqlTypeName sqlTypeName = relDataType.getSqlTypeName(); // 获取SQL类型名称
    switch (sqlTypeName) { // 根据SQL类型名称进行不同的转换处理
    case BINARY: // 处理二进制类型
    case VARBINARY: // 处理可变二进制类型
      return new ByteString((byte[]) obj); // 将字节数组转换为ByteString对象
    case TIMESTAMP: // 处理时间戳类型
    case TIMESTAMP_WITH_LOCAL_TIME_ZONE: // 处理带本地时区的时间戳类型
      Timestamp timestamp = // 使用InnoDB工具类将字符串转换为时间戳
          Utils.convertDateTime((String) obj, relDataType.getPrecision()); // 根据精度转换日期时间字符串
      return shift(timestamp).getTime(); // 对时间戳进行时区偏移调整，并返回毫秒值
    case TIME: // 处理时间类型
      Time time = // 使用InnoDB工具类将字符串转换为时间
          Utils.convertTime((String) obj, relDataType.getPrecision()); // 根据精度转换时间字符串
      return shift(time).getTime(); // 对时间进行时区偏移调整，并返回毫秒值
    case DATE: // 处理日期类型
      Date date = Date.valueOf(LocalDate.parse((String) obj)); // 将字符串解析为LocalDate，再转换为Date
      return DateTimeUtils.dateStringToUnixDate(date.toString()); // 将日期字符串转换为Unix日期值
    default: // 其他类型
      return obj; // 直接返回原对象，不做转换
    }
  }

  @Override public boolean moveNext() { // 实现Enumerator接口的moveNext()方法，移动到下一行
    if (iterator.hasNext()) { // 检查迭代器是否还有下一个元素
      current = iterator.next(); // 如果有，获取下一个元素并赋值给current
      return true; // 返回true，表示成功移动到下一行
    } else { // 如果没有下一个元素
      return false; // 返回false，表示已经到达迭代器末尾
    }
  }

  @Override public void reset() { // 实现Enumerator接口的reset()方法，重置迭代器
    throw new UnsupportedOperationException(); // 抛出不支持操作异常，因为InnoDB迭代器不支持重置
  }

  @Override public void close() { // 实现Enumerator接口的close()方法，关闭枚举器
    // Nothing to do here
    // 这里不需要做任何事情，因为没有需要释放的资源
  }

  /**
   * Shift timestamp by timezone offset.
   * 根据时区偏移量调整时间戳
   * 
   * 此方法用于将时间戳从本地时区转换为UTC时间
   * 通过加上时区偏移量来实现
   * 
   * @param v 原始时间戳
   * @return 调整后的时间戳（UTC时间）
   */
  private static Timestamp shift(Timestamp v) { // 私有静态方法，调整时间戳的时区偏移
    long time = v.getTime(); // 获取时间戳的毫秒值
    int offset = TimeZone.getDefault().getOffset(time); // 获取默认时区的偏移量（毫秒）
    return new Timestamp(time + offset); // 返回加上偏移量后的新时间戳
  }

  /**
   * Shift time by timezone offset.
   * 根据时区偏移量调整时间
   * 
   * 此方法用于将时间从本地时区转换为UTC时间
   * 通过加上时区偏移量，并对一天内的毫秒数取模来实现
   * 
   * @param v 原始时间
   * @return 调整后的时间（UTC时间）
   */
  private static Time shift(Time v) { // 私有静态方法，调整时间的时区偏移
    long time = v.getTime(); // 获取时间的毫秒值
    int offset = TimeZone.getDefault().getOffset(time); // 获取默认时区的偏移量（毫秒）
    return new Time((time + offset) % DateTimeUtils.MILLIS_PER_DAY); // 返回加上偏移量后对一天毫秒数取模的新时间
  }
}
