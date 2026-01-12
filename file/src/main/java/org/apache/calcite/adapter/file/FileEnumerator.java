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
package org.apache.calcite.adapter.file; // 定义包名，该类属于org.apache.calcite.adapter.file包，是Calcite文件适配器的一部分

import org.apache.calcite.linq4j.Enumerator; // 导入Enumerator接口，这是Calcite中用于枚举数据的接口，提供类似迭代器的功能

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的字段，帮助进行空值检查

import org.jsoup.select.Elements; // 导入Jsoup库的Elements类，用于表示HTML/XML元素的集合，这里用于表示表格行元素

import java.util.Iterator; // 导入Java标准库的Iterator接口，用于遍历集合

/**
 * Wraps {@link FileReader} and {@link FileRowConverter}, enumerates tr DOM
 * elements as table rows.
 * 该类封装了FileReader和FileRowConverter，将HTML表格的tr元素枚举为表行数据
 * 它实现了Enumerable接口，使得文件数据可以像数据库表一样被查询
 * 主要功能：遍历文件中的DOM元素，将其转换为Calcite可识别的行对象
 */
class FileEnumerator implements Enumerator<Object> { // 定义FileEnumerator类，实现Enumerator接口，泛型参数为Object表示每行数据可以是任意对象
  private final Iterator<Elements> iterator; // 成员变量：迭代器，用于遍历DOM元素集合（Elements），每个Elements代表表格的一行数据
  private final FileRowConverter converter; // 成员变量：行转换器，负责将DOM元素转换为Calcite可识别的行对象（Object数组）
  private final int[] fields; // 成员变量：字段索引数组，指定需要提取哪些字段，用于列投影优化，只返回需要的列
  private @Nullable Object current; // 成员变量：当前行数据，使用@Nullable注解标记可能为null，缓存当前遍历到的行数据

  FileEnumerator(Iterator<Elements> iterator, FileRowConverter converter) { // 构造方法：接收迭代器和行转换器，创建一个包含所有字段的枚举器
    this(iterator, converter, identityList(converter.width())); // 调用完整构造方法，fields参数使用identityList生成0到width-1的索引数组，表示选择所有字段
  }

  FileEnumerator(Iterator<Elements> iterator, FileRowConverter converter, // 构造方法：完整构造方法，接收迭代器、行转换器和字段索引数组
      int[] fields) { // 参数：fields指定需要提取的字段索引，用于列投影，只返回指定列的数据
    this.iterator = iterator; // 初始化迭代器成员变量
    this.converter = converter; // 初始化行转换器成员变量
    this.fields = fields; // 初始化字段索引数组，用于后续只提取指定的列
  }

  @Override public Object current() { // 实现Enumerator接口的current方法，返回当前行的数据
    if (current == null) { // 如果当前行为null，说明还没有开始遍历
      this.moveNext(); // 调用moveNext方法移动到第一行数据
    }
    return current; // 返回当前行数据
  }

  @Override public boolean moveNext() { // 实现Enumerator接口的moveNext方法，移动到下一行数据，返回是否还有数据
    try { // 使用try-catch捕获异常，确保异常处理
      if (this.iterator.hasNext()) { // 检查迭代器是否还有下一个元素
        final Elements row = this.iterator.next(); // 获取下一个DOM元素（表格行）
        current = this.converter.toRow(row, this.fields); // 使用行转换器将DOM元素转换为行对象，只提取fields指定的字段
        return true; // 返回true表示成功移动到下一行
      } else { // 如果迭代器没有下一个元素
        current = null; // 将当前行设置为null
        return false; // 返回false表示已经遍历完毕
      }
    } catch (RuntimeException | Error e) { // 捕获运行时异常和错误
      throw e; // 直接重新抛出运行时异常和错误
    } catch (Exception e) { // 捕获其他检查型异常
      throw new RuntimeException(e); // 将检查型异常包装为运行时异常抛出
    }
  }

  // required by linq4j Enumerator interface
  @Override public void reset() { // 实现Enumerator接口的reset方法，重置枚举器到初始位置
    throw new UnsupportedOperationException(); // 抛出不支持操作异常，因为文件迭代器不支持重置
  }

  // required by linq4j Enumerator interface
  @Override public void close() { // 实现Enumerator接口的close方法，关闭枚举器释放资源
  } // 空实现，因为FileEnumerator没有需要释放的资源

  /** Returns an array of integers {0, ..., n - 1}. */
  private static int[] identityList(int n) { // 静态方法：生成从0到n-1的整数数组，用于表示选择所有字段
    int[] integers = new int[n]; // 创建长度为n的整数数组

    for (int i = 0; i < n; i++) { // 循环从0到n-1
      integers[i] = i; // 将索引值赋给数组元素
    }

    return integers; // 返回生成的数组，结果为[0, 1, 2, ..., n-1]
  }

} // 类定义结束
