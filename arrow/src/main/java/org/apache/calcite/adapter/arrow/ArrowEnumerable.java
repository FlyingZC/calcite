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
package org.apache.calcite.adapter.arrow; // 定义包名，该类属于org.apache.calcite.adapter.arrow包，是Calcite框架中Apache Arrow适配器的一部分

import org.apache.calcite.linq4j.AbstractEnumerable; // 导入Calcite的LINQ4J抽象枚举基类，提供可枚举数据源的核心功能
import org.apache.calcite.linq4j.Enumerator; // 导入枚举器接口，用于遍历数据集合的标准接口
import org.apache.calcite.util.ImmutableIntList; // 导入不可变整数列表工具类，用于存储字段索引
import org.apache.calcite.util.Util; // 导入Calcite工具类，提供异常转换等通用功能

import org.apache.arrow.gandiva.evaluator.Filter; // 导入Arrow Gandiva库的过滤器接口，用于在Arrow数据上执行过滤操作
import org.apache.arrow.gandiva.evaluator.Projector; // 导入Arrow Gandiva库的投影器接口，用于在Arrow数据上执行投影（字段选择）操作
import org.apache.arrow.vector.ipc.ArrowFileReader; // 导入Arrow文件读取器，用于读取Arrow格式的列式存储文件

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的参数或返回值

/**
 * Enumerable that reads from Arrow value-vectors. // 该类是一个可枚举对象，用于从Apache Arrow的值向量（value-vectors）中读取数据
 * Arrow是Apache基金会的一个列式内存格式，用于高效存储和处理大数据
 * 该类实现了Calcite的适配器模式，使得Calcite可以查询Arrow格式的数据源
 * 它继承自AbstractEnumerable，提供了LINQ风格的查询能力
 * 该类是Calcite查询引擎与Arrow数据存储之间的桥梁
 */
class ArrowEnumerable extends AbstractEnumerable<Object> { // 定义ArrowEnumerable类，继承自AbstractEnumerable<Object>，表示这是一个可枚举的对象集合
  private final ArrowFileReader arrowFileReader; // Arrow文件读取器，用于读取Arrow格式文件中的数据，是数据源的核心对象
  private final ImmutableIntList fields; // 不可变整数列表，存储需要读取的字段索引，用于指定要查询哪些列
  private final @Nullable Projector projector; // 可空的投影器对象，用于执行投影操作（即选择特定的字段或对字段进行转换），如果为null表示不需要投影
  private final @Nullable Filter filter; // 可空的过滤器对象，用于执行过滤操作（即根据条件筛选行），如果为null表示不需要过滤


  ArrowEnumerable(ArrowFileReader arrowFileReader, ImmutableIntList fields, // 构造方法，用于创建ArrowEnumerable实例
      @Nullable Projector projector, @Nullable Filter filter) { // 接收投影器和过滤器参数，两者至少有一个不能为null
    this.arrowFileReader = arrowFileReader; // 将传入的Arrow文件读取器保存到成员变量中，用于后续读取数据
    this.projector = projector; // 将传入的投影器保存到成员变量中，用于后续的数据投影操作
    this.filter = filter; // 将传入的过滤器保存到成员变量中，用于后续的数据过滤操作
    this.fields = fields; // 将传入的字段索引列表保存到成员变量中，用于指定要访问的字段
  }

  @Override public Enumerator<Object> enumerator() { // 重写父类方法，创建并返回一个枚举器对象，用于遍历Arrow数据
    try { // 开始try块，用于捕获可能发生的异常
      if (projector != null) { // 如果投影器不为null，说明需要进行投影操作
        return new ArrowProjectEnumerator(arrowFileReader, fields, projector); // 创建并返回ArrowProjectEnumerator实例，该枚举器会执行投影操作
      } else if (filter != null) { // 如果投影器为null但过滤器不为null，说明需要进行过滤操作
        return new ArrowFilterEnumerator(arrowFileReader, fields, filter); // 创建并返回ArrowFilterEnumerator实例，该枚举器会执行过滤操作
      }
      throw new IllegalArgumentException( // 如果投影器和过滤器都为null，抛出非法参数异常
          "The arrow enumerator must have either a filter or a projection"); // 异常信息说明必须提供过滤器或投影器中的至少一个
    } catch (Exception e) { // 捕获所有可能的异常
      throw Util.toUnchecked(e); // 使用Calcite工具类将检查型异常转换为非检查型异常并抛出，简化异常处理
    }
  }
}
