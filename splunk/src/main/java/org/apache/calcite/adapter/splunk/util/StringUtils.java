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
package org.apache.calcite.adapter.splunk.util; // 声明包名，该类位于 org.apache.calcite.adapter.splunk.util 包中，属于 Calcite 的 Splunk 适配器工具类包

import org.slf4j.Logger; // 导入 SLF4J 日志接口，用于日志记录
import org.slf4j.LoggerFactory; // 导入 SLF4J 日志工厂，用于创建 Logger 实例

import java.util.ArrayList; // 导入 ArrayList 类，用于动态数组列表
import java.util.List; // 导入 List 接口，用于定义列表类型

/**
 * Utility methods for encoding and decoding strings for Splunk REST calls. // 字符串工具类，提供为 Splunk REST 调用进行字符串编码和解码的实用方法，主要用于处理包含分隔符的字符串列表的序列化和反序列化
 */
public class StringUtils { // 定义 StringUtils 工具类，该类提供静态方法用于字符串处理，专门服务于 Splunk REST API 调用中的字符串编码和解码需求
  private StringUtils() {} // 私有构造方法，防止实例化该工具类，所有方法都是静态方法，不需要创建对象即可调用

  public static StringBuilder encodeList( // 定义静态方法 encodeList，将字符串列表编码为单个字符串，用指定分隔符分隔，并对分隔符进行转义处理，返回 StringBuilder 对象
      List<? extends CharSequence> list, char delim) { // 参数 list：要编码的字符序列列表，可以是 String、StringBuilder 等实现 CharSequence 接口的对象列表；参数 delim：分隔符字符，用于分隔列表中的不同元素
    StringBuilder result = new StringBuilder(); // 创建一个 StringBuilder 对象用于存储编码后的结果字符串
    boolean first = true; // 标记是否是第一个元素，用于控制分隔符的添加，第一个元素前不添加分隔符
    for (CharSequence cs : list) { // 遍历列表中的每个字符序列元素
      if (!first) { // 如果不是第一个元素，则在元素前添加分隔符
        result.append(delim); // 将分隔符添加到结果字符串中
      }
      int len = cs.length(); // 获取当前字符序列的长度
      for (int i = 0; i < len; ++i) { // 遍历字符序列中的每个字符
        char c = cs.charAt(i); // 获取当前位置的字符
        if (c == delim) { // 如果当前字符是分隔符，则需要转义
          result.append('\\'); // 在分隔符前添加反斜杠转义符
        }
        result.append(c); // 将当前字符添加到结果字符串中（可能是分隔符，也可能是其他字符）
      }
      first = false; // 将 first 标记设置为 false，表示后续元素都需要添加分隔符
    }
    return result; // 返回编码后的 StringBuilder 对象
  }

  public static List<String> decodeList(CharSequence encoded, char delim) { // 定义静态方法 decodeList，将编码后的字符串解码回字符串列表，处理转义的分隔符，返回字符串列表
    List<String> list = new ArrayList<>(); // 创建一个 ArrayList 用于存储解码后的字符串列表
    int len = encoded.length(); // 获取编码字符串的长度
    int start = 0; // 记录当前元素的起始位置
    int end = 0; // 记录当前元素的结束位置
    boolean hasEscapedDelim = false; // 标记当前元素中是否包含转义的分隔符
    char p = '\0'; // 存储前一个字符，用于判断当前分隔符是否被转义
    char c = '\0'; // 存储当前字符
    for (int i = 0; i < len; i++, ++end) { // 遍历编码字符串的每个字符，同时更新 end 位置
      p = c; // 将前一个字符保存为 p
      c = encoded.charAt(i); // 获取当前字符
      if (c == delim) { // 如果当前字符是分隔符
        if (p == '\\') { // 检查前一个字符是否是转义符，如果是则表示这个分隔符是被转义的
          hasEscapedDelim = true; // 标记当前元素包含转义的分隔符
        } else { // 如果前一个字符不是转义符，则这是一个真正的分隔符，表示元素结束
          if (!hasEscapedDelim) { // 如果当前元素中没有转义的分隔符，则直接截取子串
            list.add(encoded.subSequence(start, end).toString()); // 将从 start 到 end 的子串添加到列表中
          } else { // 如果当前元素中有转义的分隔符，则需要处理转义字符
            StringBuilder sb = new StringBuilder(end - start); // 创建 StringBuilder 用于处理转义字符
            char a = '\0'; // 存储当前字符
            char b = '\0'; // 存储前一个字符
            for (int j = start; j < end; ++j) { // 遍历当前元素的每个字符
              b = a; // 保存前一个字符
              a = encoded.charAt(j); // 获取当前字符
              if (b == '\\' && a != delim) { // 如果前一个字符是转义符且当前字符不是分隔符，则保留转义符
                sb.append(b); // 将转义符添加到结果中
              }
              if (a != '\\') { // 如果当前字符不是转义符，则添加到结果中
                sb.append(a); // 将当前字符添加到结果中
              }
            }
            list.add(sb.toString()); // 将处理后的字符串添加到列表中
          }
          start = end + 1; // 更新下一个元素的起始位置
          hasEscapedDelim = false; // 重置转义标记
        }
      }
    }

    if (!hasEscapedDelim) { // 处理最后一个元素，如果没有转义的分隔符
      list.add(encoded.subSequence(start, end).toString()); // 直接截取最后一个元素并添加到列表
    } else { // 如果最后一个元素有转义的分隔符，则需要处理转义字符
      StringBuilder sb = new StringBuilder(end - start); // 创建 StringBuilder 用于处理转义字符
      char a = '\0'; // 存储当前字符
      char b = '\0'; // 存储前一个字符
      for (int j = start; j < end; ++j) { // 遍历最后一个元素的每个字符
        b = a; // 保存前一个字符
        a = encoded.charAt(j); // 获取当前字符
        if (b == '\\' && a != delim) { // 如果前一个字符是转义符且当前字符不是分隔符，则保留转义符
          sb.append(b); // 将转义符添加到结果中
        }
        if (a != '\\') { // 如果当前字符不是转义符，则添加到结果中
          sb.append(a); // 将当前字符添加到结果中
        }
      }
      list.add(sb.toString()); // 将处理后的字符串添加到列表
    }

    return list; // 返回解码后的字符串列表
  }

  public static boolean parseBoolean( // 定义静态方法 parseBoolean，将字符串解析为布尔值，支持多种表示方式
      String str, boolean defaultVal, boolean missingVal) { // 参数 str：要解析的字符串；参数 defaultVal：当字符串无法识别时返回的默认值；参数 missingVal：当字符串为 null 或空时返回的值
    if (str == null || str.isEmpty()) { // 检查字符串是否为 null 或空
      return missingVal; // 如果为 null 或空，返回 missingVal
    }
    if (str.equalsIgnoreCase("t") // 检查字符串是否等于 "t"（不区分大小写）
        || str.equalsIgnoreCase("true") // 或等于 "true"（不区分大小写）
        || str.equalsIgnoreCase("yes") // 或等于 "yes"（不区分大小写）
        || str.equals("1")) { // 或等于 "1"（区分大小写）
      return true; // 如果匹配任意一个 true 值，返回 true
    }
    if (str.equalsIgnoreCase("f") // 检查字符串是否等于 "f"（不区分大小写）
        || str.equalsIgnoreCase("false") // 或等于 "false"（不区分大小写）
        || str.equalsIgnoreCase("no") // 或等于 "no"（不区分大小写）
        || str.equals("0")) { // 或等于 "0"（区分大小写）
      return false; // 如果匹配任意一个 false 值，返回 false
    }
    return defaultVal; // 如果字符串无法识别为 true 或 false，返回默认值
  }


  public static void main(String[] args) { // 主方法，用于测试 encodeList 和 decodeList 方法的正确性
    List<String> list = new ArrayList<>(); // 创建一个测试用的字符串列表
    list.add("test"); // 添加普通字符串
    list.add("test,with,comma"); // 添加包含逗号的字符串（逗号将被转义）
    list.add(""); // 添加空字符串
    list.add(","); // 添加只有逗号的字符串

    System.out.println("============="); // 打印分隔线

    StringBuilder sb = encodeList(list, ','); // 调用 encodeList 方法，使用逗号作为分隔符编码列表
    System.out.println(sb); // 打印编码后的字符串

    list.clear(); // 清空列表
    list = decodeList(sb, ','); // 调用 decodeList 方法，解码字符串回列表
    for (String s : list) { // 遍历解码后的列表
      System.out.println(s); // 打印每个元素
    }
    System.out.println("============="); // 打印分隔线
  }

  public static Logger getClassTracer(Class clazz) { // 定义静态方法 getClassTracer，获取指定类的日志记录器，用于日志记录
    return LoggerFactory.getLogger(clazz); // 使用 SLF4J 的 LoggerFactory 创建并返回指定类的 Logger 实例
  }
}
