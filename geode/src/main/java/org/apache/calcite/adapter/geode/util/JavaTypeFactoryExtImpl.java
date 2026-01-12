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
package org.apache.calcite.adapter.geode.util; // 声明包名，该类位于org.apache.calcite.adapter.geode.util包中，是Calcite Geode适配器的工具类

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入JavaTypeFactory接口，用于定义Java类型工厂的契约
import org.apache.calcite.jdbc.JavaRecordType; // 导入JavaRecordType类，用于表示Java记录类型
import org.apache.calcite.jdbc.JavaTypeFactoryImpl; // 导入JavaTypeFactoryImpl类，作为基类提供基础实现
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，用于表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField接口，用于表示关系数据类型字段
import org.apache.calcite.rel.type.RelDataTypeFieldImpl; // 导入RelDataTypeFieldImpl类，提供关系数据类型字段的实现
import org.apache.calcite.rel.type.RelRecordType; // 导入RelRecordType类，用于表示关系记录类型

import org.apache.geode.pdx.PdxInstance; // 导入PdxInstance接口，用于表示Geode PDX（Portable Data Exchange）实例

import java.lang.reflect.Field; // 导入Field类，用于通过反射获取类的字段信息
import java.lang.reflect.Type; // 导入Type接口，用于表示Java类型
import java.util.ArrayList; // 导入ArrayList类，用于动态数组存储
import java.util.List; // 导入List接口，用于列表集合操作
import java.util.Map; // 导入Map接口，用于键值对映射

import static org.apache.calcite.util.ReflectUtil.isStatic; // 静态导入ReflectUtil工具类的isStatic方法，用于判断字段是否为静态

/**
 * Implementation of {@link JavaTypeFactory}.
 * JavaTypeFactory的实现类，专门用于扩展类型工厂功能
 *
 * <p><strong>NOTE: This class is experimental and subject to
 * change/removal without notice</strong>.
 * 注意：此类是实验性的，可能会在没有通知的情况下更改或移除
 */
public class JavaTypeFactoryExtImpl // 定义JavaTypeFactoryExtImpl类，继承自JavaTypeFactoryImpl基类
    extends JavaTypeFactoryImpl { // 继承JavaTypeFactoryImpl，获得基础类型工厂功能

  /**
   * See <a href="http://stackoverflow.com/questions/16966629/what-is-the-difference-between-getfields-and-getdeclaredfields-in-java-reflectio">
   *   the difference between fields and declared fields</a>.
   * 参见链接：解释了getFields和getDeclaredFields的区别
   * getFields返回所有public字段（包括继承的），getDeclaredFields返回当前类声明的所有字段（不包括继承的）
   */
  @Override public RelDataType createStructType(Class type) { // 重写createStructType方法，根据Java类创建结构化关系数据类型

    final List<RelDataTypeField> list = new ArrayList<>(); // 创建字段列表，用于存储结构化类型的所有字段
    for (Field field : type.getDeclaredFields()) { // 遍历类声明的所有字段（不包括继承的字段）
      if (!isStatic(field)) { // 检查字段是否为非静态字段，静态字段不包含在结构类型中
        // FIXME: watch out for recursion
        // FIXME：注意递归问题，如果字段类型包含自身可能导致无限递归
        final Type fieldType = field.getType(); // 获取字段的Java类型
        list.add( // 将字段信息添加到字段列表中
            new RelDataTypeFieldImpl( // 创建关系数据类型字段实现
                field.getName(), // 字段名称
                list.size(), // 字段索引（当前列表大小）
                createType(fieldType))); // 根据字段类型创建对应的关系数据类型
      }
    }
    return canonize(new JavaRecordType(list, type)); // 创建Java记录类型并进行规范化处理，返回规范化的结构类型
  }

  public RelDataType createPdxType(PdxInstance pdxInstance) { // 创建基于PdxInstance的关系数据类型，用于处理Geode PDX数据
    final List<RelDataTypeField> list = new ArrayList<>(); // 创建字段列表，用于存储PDX实例的所有字段信息
    for (String fieldName : pdxInstance.getFieldNames()) { // 遍历PDX实例的所有字段名称
      Object field = pdxInstance.getField(fieldName); // 获取字段对应的值对象

      Type fieldType; // 声明字段类型变量

      if (field == null) { // 如果字段值为null
        fieldType = String.class; // 默认将null值字段类型设置为String类型
      } else if (field instanceof PdxInstance) { // 如果字段值是PdxInstance类型（嵌套PDX结构）
        // Map Nested PDX structures as String. This relates with
        // GeodeUtils.convert case when clazz is Null.
        // 将嵌套的PDX结构映射为Map类型。这与GeodeUtils.convert方法中clazz为Null的情况相关
        fieldType = Map.class; // 设置字段类型为Map，表示嵌套结构
        // RelDataType boza = createPdxType((PdxInstance) field);
        // 注释掉的代码：原本可能考虑递归创建嵌套PDX类型，但当前采用Map类型简化处理
      } else { // 字段值为普通Java对象
        fieldType = field.getClass(); // 获取字段值的实际Class类型
      }

      list.add( // 将字段信息添加到字段列表中
          new RelDataTypeFieldImpl( // 创建关系数据类型字段实现
              fieldName, // 字段名称
              list.size(), // 字段索引（当前列表大小）
              createType(fieldType))); // 根据字段类型创建对应的关系数据类型
    }

    return canonize(new RelRecordType(list)); // 创建关系记录类型并进行规范化处理，返回规范化的PDX类型
  }

  // Experimental flattering the nested structures.
// 实验性功能：扁平化嵌套结构，将嵌套的PDX结构展开为扁平的字段列表
public RelDataType createPdxType2(PdxInstance pdxInstance) { // 创建扁平化的PDX类型（实验性方法）
    final List<RelDataTypeField> list = new ArrayList<>(); // 创建字段列表，用于存储扁平化后的所有字段
    recursiveCreatePdxType(pdxInstance, list, ""); // 递归创建扁平化的PDX类型，初始字段前缀为空字符串
    return canonize(new RelRecordType(list)); // 创建关系记录类型并进行规范化处理，返回扁平化的PDX类型
  }

  private void recursiveCreatePdxType(PdxInstance pdxInstance, // 递归创建扁平化PDX类型的私有辅助方法
      List<RelDataTypeField> list, String fieldNamePrefix) { // 参数：PDX实例、字段列表、字段名前缀（用于表示嵌套层级）

    for (String fieldName : pdxInstance.getFieldNames()) { // 遍历PDX实例的所有字段名称
      Object field = pdxInstance.getField(fieldName); // 获取字段对应的值对象
      final Type fieldType = field.getClass(); // 获取字段值的实际Class类型
      if (fieldType instanceof PdxInstance) { // 检查字段类型是否为PdxInstance（即嵌套PDX结构）
        recursiveCreatePdxType( // 递归调用自身处理嵌套PDX结构
            (PdxInstance) field, list, fieldNamePrefix + fieldName + "."); // 将字段名前缀加上当前字段名和点号，表示嵌套层级
      } else { // 字段不是PdxInstance，为普通类型
        list.add( // 将扁平化后的字段添加到字段列表中
            new RelDataTypeFieldImpl( // 创建关系数据类型字段实现
                fieldNamePrefix + fieldName, // 使用前缀+字段名作为扁平化后的字段名
                list.size(), // 字段索引（当前列表大小）
                createType(fieldType))); // 根据字段类型创建对应的关系数据类型
      }
    }
  }

} // 类定义结束
