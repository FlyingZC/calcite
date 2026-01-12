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
package org.apache.calcite.test.schemata.orderstream;

import org.apache.calcite.DataContext;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.schema.StreamableTable;
import org.apache.calcite.schema.Table;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Iterator;

/**
 * Table representing an infinitely larger ORDERS stream. // 表示一个无限大的订单流的表类，用于测试流式查询场景
 */ // 该类继承自 BaseOrderStreamTable 并实现了 StreamableTable 接口，提供无限生成的订单数据流
public class InfiniteOrdersTable extends BaseOrderStreamTable // 继承基础订单流表类，复用基础功能
    implements StreamableTable { // 实现可流化表接口，支持流式数据查询
  @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 扫描数据方法，返回可枚举的对象数组，root是数据上下文，包含运行时环境信息
    return Linq4j.asEnumerable(() -> new Iterator<Object[]>() { // 使用 Linq4j 工具类将迭代器转换为可枚举对象，返回一个无限迭代器
      private final String[] items = {"paint", "paper", "brush"}; // 订单商品列表，包含三种商品：油漆、纸张、画笔，用于循环生成订单数据
      private int counter = 0; // 订单计数器，用于生成递增的订单ID索引

      @Override public boolean hasNext() { // 判断是否还有下一个元素的方法，对于无限流始终返回true
        return true; // 始终返回true，表示流永远不会结束，可以无限生成订单数据
      }

      @Override public Object[] next() { // 获取下一个订单数据的方法，返回一个Object数组表示一行订单记录
        final int index = counter++; // 获取当前计数器值并递增，index作为订单的索引号
        return new Object[]{ // 返回一个包含4个字段的订单记录数组
            System.currentTimeMillis(), index, items[index % items.length], 10}; // 字段1：当前时间戳（订单时间），字段2：订单索引ID，字段3：商品名称（通过取模循环使用items数组），字段4：数量（固定为10）
      }

      @Override public void remove() { // 移除元素的方法，不支持此操作
        throw new UnsupportedOperationException(); // 抛出不支持操作异常，因为流式数据不支持删除操作
      }
    });
  }

  @Override public Table stream() { // 返回流式表的方法，将当前表转换为流式表
    return this; // 返回当前对象本身，因为当前类已经实现了流式表接口
  }
}
