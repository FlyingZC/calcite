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
package org.apache.calcite.piglet; // 指定当前类所属的包，位于org.apache.calcite.piglet包下

import org.apache.calcite.rel.type.DynamicRecordTypeImpl; // 导入动态记录类型实现类，作为当前类的父类
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建关系数据类型
import org.apache.calcite.rel.type.RelDataTypeField; // 导入关系数据类型字段接口，表示字段定义

import java.util.regex.Matcher; // 导入正则表达式匹配器类，用于匹配字符串模式
import java.util.regex.Pattern; // 导入正则表达式模式类，用于编译正则表达式

import static java.lang.Integer.parseInt; // 静态导入Integer的parseInt方法，用于将字符串转换为整数

/**
 * Represents Pig Tuples with unknown fields. The tuple field
 * can only be accessed via name '$index', like ('$0', '$1').
 * The tuple is then resized to match the index.
 */
// 这个类代表Pig语言中的元组(Tuple)，其字段是动态且未知的，字段只能通过'$index'格式访问（如'$0'、'$1'）
// 当访问某个索引的字段时，元组会自动调整大小以匹配该索引，实现动态扩展字段的能力
public class DynamicTupleRecordType extends DynamicRecordTypeImpl { // 定义DynamicTupleRecordType类，继承自DynamicRecordTypeImpl动态记录类型实现类
  private static final Pattern INDEX_PATTERN = Pattern.compile("^\\$(\\d+)$"); // 定义静态常量正则表达式模式，用于匹配以$开头后跟数字的字段名（如$0、$1、$2等）

  DynamicTupleRecordType(RelDataTypeFactory typeFactory) { // 构造方法，接收一个关系数据类型工厂参数
    super(typeFactory); // 调用父类DynamicRecordTypeImpl的构造方法，传入typeFactory参数进行初始化
  }

  @Override public RelDataTypeField getField(String fieldName, // 重写父类的getField方法，用于获取指定名称的字段定义，返回RelDataTypeField字段对象
      boolean caseSensitive, boolean elideRecord) { // 参数：fieldName-字段名称；caseSensitive-是否区分大小写；elideRecord-是否省略记录类型
    final int index = nameToIndex(fieldName); // 调用nameToIndex方法将字段名转换为索引值，如'$1'转换为1
    if (index >= 0) { // 如果索引值大于等于0，说明字段名符合'$index'格式
      resize(index + 1); // 调用resize方法调整元组大小，确保元组至少有index+1个字段（因为索引从0开始）
      return super.getField(fieldName, caseSensitive, elideRecord); // 调用父类的getField方法获取字段定义并返回
    } // 如果字段名不符合'$index'格式，则跳过if块
    return null; // 返回null表示无法获取该字段（因为字段名格式不正确）
  }

  /**
   * Resizes the record if the new size greater than the current size.
   *
   * @param size New size
   */
  // 调整记录类型的大小，使其包含指定数量的字段，如果新大小大于当前大小则进行扩展
  void resize(int size) { // 定义resize方法，接收size参数表示新的字段数量
    int currentSize = getFieldCount(); // 调用getFieldCount方法获取当前字段数量
    if (size > currentSize) { // 如果请求的新大小大于当前大小，需要进行扩展
      for (int i = currentSize; i < size; i++) { // 循环从当前字段数量开始，直到size-1，为每个新索引创建字段
        super.getField("$" + i, true, true); // 调用父类的getField方法，字段名为'$i'，caseSensitive为true（区分大小写），elideRecord为true（省略记录类型）
      } // 循环结束后，已创建从$currentSize到$size-1的所有字段
      computeDigest(); // 调用computeDigest方法重新计算记录类型的摘要信息（用于缓存和比较）
    } // 如果新大小不大于当前大小，则不需要调整，直接跳过if块
  }

  /**
   * Gets index number from field name.
   *
   * @param fieldName Field name, format example '$1'
   */
  // 从字段名中提取索引号，例如从'$1'中提取出1，从'$0'中提取出0
  private static int nameToIndex(String fieldName) { // 定义私有静态方法nameToIndex，接收fieldName参数表示字段名
    Matcher matcher = INDEX_PATTERN.matcher(fieldName); // 使用INDEX_PATTERN正则表达式模式创建匹配器，匹配字段名
    if (matcher.find()) { // 如果找到匹配项（即字段名符合'$数字'的格式）
      return parseInt(matcher.group(1)); // 返回正则表达式第一个捕获组（数字部分）转换为整数的值
    } // 如果没有找到匹配项，则跳过if块
    return -1; // 返回-1表示字段名不符合'$index'格式
  } // 方法结束
} // 类定义结束
