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
package org.apache.calcite.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Integer.parseInt;

/**
 * Version string parsed into major and minor parts. // 版本号类，将版本字符串解析为主版本号和次版本号等部分，用于版本比较和管理
 */ // 这个类实现了 Comparable 接口，支持版本号的排序和比较操作
public class Version implements Comparable<Version> { // 版本号类，用于表示和比较软件版本号
  private static final Ordering<Iterable<Integer>> ORDERING = // 定义一个用于比较整数列表的排序器，使用字典序（lexicographical）比较
      Ordering.<Integer>natural().lexicographical(); // 使用自然顺序进行字典序比较，即按从左到右的顺序逐个比较整数

  public final List<Integer> integers; // 版本号的整数列表，例如 "1.2.3" 会被解析为 [1, 2, 3]，用于版本比较的核心数据
  public final String string; // 原始版本字符串，用于保持版本号的原始表示形式，便于调试和显示

  /** Private constructor. */ // 私有构造方法，防止外部直接创建 Version 对象，必须通过静态工厂方法 of() 创建
  private Version(List<Integer> integers, String string) { // 私有构造方法，接收整数列表和原始字符串
    this.integers = ImmutableList.copyOf(integers); // 使用不可变列表存储版本号整数部分，确保数据不被修改
    this.string = string; // 保存原始版本字符串，用于后续可能需要的字符串表示
  }

  /** Creates a Version by parsing a string. */ // 静态工厂方法：通过解析字符串创建 Version 对象，支持常见的版本号格式
  public static Version of(String s) { // 静态工厂方法，接收版本字符串如 "1.2.3" 或 "1.2-beta"
    final String[] strings = s.split("[.-]"); // 使用正则表达式按点号或连字符分割字符串，例如 "1.2.3" 分割为 ["1", "2", "3"]
    List<Integer> integers = new ArrayList<>(); // 创建整数列表用于存储解析出的版本号数字部分
    for (String string : strings) { // 遍历分割后的字符串数组，尝试将每个部分转换为整数
      try { // 尝试将字符串转换为整数
        integers.add(parseInt(string)); // 如果转换成功，将整数添加到列表中
      } catch (NumberFormatException e) { // 如果转换失败（遇到非数字部分如 "beta"）
        break; // 停止解析，不再处理后续部分，因为版本号通常数字部分在前
      }
    }
    return new Version(integers, s); // 使用解析出的整数列表和原始字符串创建 Version 对象并返回
  }

  /** Creates a Version from a sequence of integers. */ // 静态工厂方法：通过整数序列创建 Version 对象，适合程序化创建版本号
  public static Version of(int... integers) { // 静态工厂方法，接收可变参数的整数数组，例如 of(1, 2, 3)
    return new Version(ImmutableIntList.copyOf(integers), ""); // 使用不可变整数列表创建 Version 对象，原始字符串为空
  }

  @Override public int compareTo(Version version) { // 实现 Comparable 接口的比较方法，用于版本号排序
    return ORDERING.compare(this.integers, version.integers); // 使用字典序比较器比较两个版本号的整数列表，返回比较结果
  }
}
