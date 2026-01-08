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
// 声明当前类所属的包为 org.apache.calcite.jdbc，表示这是Calcite JDBC模块的一部分
package org.apache.calcite.jdbc;

// 导入RelDataTypeField接口，用于表示关系数据类型中的字段
import org.apache.calcite.rel.type.RelDataTypeField;
// 导入RelRecordType类，这是JavaRecordType的父类，表示关系记录类型
import org.apache.calcite.rel.type.RelRecordType;

// 导入Nullable注解，用于标记可能为null的参数或返回值，来自CheckerFramework框架
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入List接口，用于存储字段列表
import java.util.List;
// 导入Objects工具类，用于equals和hashCode方法的实现
import java.util.Objects;

// 静态导入requireNonNull方法，用于非空检查
import static java.util.Objects.requireNonNull;

/**
 * Record type based on a Java class. The fields of the type are the fields
 * of the class.
 * 基于Java类的记录类型。该类型的字段就是Java类的字段。
 *
 * <p><strong>NOTE: This class is experimental and subject to
 * change/removal without notice</strong>.
 * <p><strong>注意：此类是实验性的，可能会在无通知的情况下更改或移除</strong>。
 */
// 定义JavaRecordType类，继承自RelRecordType，表示基于Java类的记录类型
public class JavaRecordType extends RelRecordType {
  // 成员变量：存储对应的Java类对象，final表示该引用一旦赋值就不能改变
  // 这个Class对象代表了该记录类型对应的Java类，用于类型映射和反射操作
  final Class clazz;

  // 构造方法：创建一个JavaRecordType实例
  // 参数fields：字段列表，包含该记录类型的所有字段信息，每个字段用RelDataTypeField表示
  // 参数clazz：对应的Java类对象，用于表示这个记录类型对应的Java类
  public JavaRecordType(List<RelDataTypeField> fields, Class clazz) {
    // 调用父类RelRecordType的构造方法，传入字段列表，初始化父类的字段列表
    super(fields);
    // 使用requireNonNull方法检查clazz参数是否为null，如果为null则抛出NullPointerException
    // 将clazz参数赋值给成员变量this.clazz，"clazz"是异常消息中的参数名
    this.clazz = requireNonNull(clazz, "clazz");
  }

  // 重写equals方法，用于比较两个JavaRecordType对象是否相等
  // 参数obj：要比较的对象，可能为null，使用@Nullable注解标记
  // 返回值：如果相等返回true，否则返回false
  @Override public boolean equals(@Nullable Object obj) {
    // 首先检查对象引用是否相同，如果相同则直接返回true
    // 然后检查obj是否是JavaRecordType的实例，如果不是则返回false
    // 如果是JavaRecordType实例，则比较两个对象的fieldList和clazz是否都相等
    return this == obj
        || obj instanceof JavaRecordType
        && Objects.equals(fieldList, ((JavaRecordType) obj).fieldList)
        && clazz == ((JavaRecordType) obj).clazz;
  }

  // 重写hashCode方法，用于生成对象的哈希码，基于fieldList和clazz
  // 返回值：基于fieldList和clazz的哈希值
  @Override public int hashCode() {
    // 使用Objects.hash方法生成哈希码，该方法会根据fieldList和clazz计算哈希值
    // 这样可以确保相等的对象具有相同的哈希码
    return Objects.hash(fieldList, clazz);
  }
}
