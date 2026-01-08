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
package org.apache.calcite.rel; // 定义包名，该类属于org.apache.calcite.rel包，是Calcite关系代数框架的一部分

import org.apache.calcite.util.mapping.IntPair; // 导入IntPair类，用于表示整数对，用于存储源表和目标表列的映射关系

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList，用于创建不可变列表，确保线程安全和数据一致性

import java.util.List; // 导入Java标准库的List接口，用于存储有序的元素集合

/** RelOptReferentialConstraint base implementation. */ // 文档注释：这是RelOptReferentialConstraint接口的基础实现类
public class RelReferentialConstraintImpl implements RelReferentialConstraint { // 定义类名：RelReferentialConstraintImpl，实现RelReferentialConstraint接口，表示关系引用约束（如外键约束）的不可变实现

  private final List<String> sourceQualifiedName; // 成员变量：源表的完全限定名列表（如["catalog", "schema", "table"]），表示外键所在的表，使用final修饰确保不可变
  private final List<String> targetQualifiedName; // 成员变量：目标表的完全限定名列表（如["catalog", "schema", "table"]），表示被引用的主键所在的表，使用final修饰确保不可变
  private final List<IntPair> columnPairs; // 成员变量：列对列表，每个IntPair表示源表列索引和目标表列索引的对应关系，定义了外键列到主键列的映射，使用final修饰确保不可变

  private RelReferentialConstraintImpl(List<String> sourceQualifiedName, // 构造方法：私有构造函数，创建RelReferentialConstraintImpl实例
      List<String> targetQualifiedName, // 参数：目标表的完全限定名列表，如["catalog", "schema", "table"]
      List<IntPair> columnPairs) { // 参数：列对列表，表示源表和目标表列的映射关系
    this.sourceQualifiedName = ImmutableList.copyOf(sourceQualifiedName); // 将传入的源表限定名列表转换为不可变列表并赋值，确保外部无法修改内部数据
    this.targetQualifiedName = ImmutableList.copyOf(targetQualifiedName); // 将传入的目标表限定名列表转换为不可变列表并赋值，确保外部无法修改内部数据
    this.columnPairs = ImmutableList.copyOf(columnPairs); // 将传入的列对列表转换为不可变列表并赋值，确保外部无法修改内部数据
  } // 构造方法结束

  @Override public List<String> getSourceQualifiedName() { // 方法：重写接口方法，获取源表的完全限定名列表，@Override注解表示这是接口方法的实现
    return sourceQualifiedName; // 返回源表的完全限定名列表，由于是ImmutableList，返回的是不可变视图
  } // 方法结束

  @Override public List<String> getTargetQualifiedName() { // 方法：重写接口方法，获取目标表的完全限定名列表，@Override注解表示这是接口方法的实现
    return targetQualifiedName; // 返回目标表的完全限定名列表，由于是ImmutableList，返回的是不可变视图
  } // 方法结束

  @Override public List<IntPair> getColumnPairs() { // 方法：重写接口方法，获取列对列表，@Override注解表示这是接口方法的实现
    return columnPairs; // 返回列对列表，每个IntPair包含源表列索引和目标表列索引，由于是ImmutableList，返回的是不可变视图
  } // 方法结束

  public static RelReferentialConstraintImpl of(List<String> sourceQualifiedName, // 静态工厂方法：创建RelReferentialConstraintImpl实例，使用"of"命名模式，更符合函数式编程风格
      List<String> targetQualifiedName, // 参数：目标表的完全限定名列表
      List<IntPair> columnPairs) { // 参数：列对列表
    return new RelReferentialConstraintImpl( // 返回新创建的RelReferentialConstraintImpl实例
        sourceQualifiedName, targetQualifiedName, columnPairs); // 传入所有参数到私有构造函数
  } // 静态工厂方法结束

  @Override public String toString() { // 方法：重写Object的toString方法，用于生成对象的字符串表示，@Override注解表示这是父类方法的重写
    return "{ " + sourceQualifiedName + ", " + targetQualifiedName + ", " // 返回格式化的字符串，包含源表限定名、目标表限定名和列对列表，用花括号和逗号分隔
        + columnPairs + " }"; // 继续拼接列对列表，最后闭合花括号
  } // 方法结束

} // 类定义结束
