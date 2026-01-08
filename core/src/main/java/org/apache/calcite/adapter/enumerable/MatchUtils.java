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
package org.apache.calcite.adapter.enumerable; // 声明包名，该类属于 org.apache.calcite.adapter.enumerable 包，这是 Calcite 框架中用于可枚举适配器的包

import java.util.List; // 导入 Java 标准库的 List 接口，用于处理列表集合

/** Class with static Helpers for MATCH_RECOGNIZE. */ // 类级别的 JavaDoc 注释：这是一个为 MATCH_RECOGNIZE（SQL 标准中的模式匹配功能）提供静态辅助方法的工具类
public class MatchUtils { // 定义公共类 MatchUtils，这是一个工具类，包含处理 MATCH_RECOGNIZE 模式匹配的静态辅助方法

  // Should not be instantiated // 注释说明：这个类不应该被实例化，因为它是纯工具类，只包含静态方法
  private MatchUtils() { // 私有构造方法，防止外部实例化该类，确保该类只能作为工具类使用
    throw new IllegalStateException(); // 抛出 IllegalStateException 异常，如果有人尝试通过反射或其他方式实例化这个类，会立即抛出异常
  } // 构造方法结束

  /**
   * Returns the row with the highest index whose corresponding symbol matches, null otherwise.
   * JavaDoc 方法注释：返回具有最高索引的行，该行对应的符号匹配指定的 symbol，如果没有匹配则返回 null
   *
   * @param symbol Target Symbol // 参数说明：symbol 是目标符号，用于在符号列表中查找匹配的符号
   * @param rows List of passed rows // 参数说明：rows 是已传递的行列表，虽然方法参数中声明了但实际未使用，可能是为了保持接口一致性或未来扩展
   * @param symbols Corresponding symbols to rows // 参数说明：symbols 是与行对应的符号列表，每个符号表示对应行在模式匹配中的角色
   * @return index or -1 // 返回值说明：返回匹配符号的最高索引位置，如果没有找到匹配则返回 -1
   */
  public static <E> int lastWithSymbol(String symbol, List<E> rows, List<String> symbols, // 定义公共静态泛型方法 lastWithSymbol，泛型 E 表示行元素的类型，参数包括目标符号、行列表、符号列表和起始索引
      int startIndex) { // 参数 startIndex 表示开始搜索的起始索引位置，方法从这个位置向前搜索匹配的符号
    for (int i = startIndex; i >= 0; i--) { // 使用 for 循环从 startIndex 开始向前遍历（索引递减），直到索引为 0，这样可以找到最接近 startIndex 的匹配符号（即最高索引的匹配）
      if (symbol.equals(symbols.get(i))) { // 判断当前位置 i 的符号是否等于目标 symbol，使用 String.equals() 方法进行字符串比较
        return i; // 如果找到匹配的符号，立即返回当前索引 i，这是从 startIndex 向前搜索的第一个匹配项，因此是最高索引的匹配
      } // if 语句结束
    } // for 循环结束
    return -1; // 如果循环结束仍未找到匹配的符号，返回 -1 表示没有找到匹配项
  } // lastWithSymbol 方法结束

  public static void print(int s) { // 定义公共静态方法 print，接收一个整数参数 s，这个方法看起来是调试用的辅助方法，用于打印整数值
    System.out.println(s); // 使用标准输出打印整数 s 并换行，这是一个简单的调试输出方法
  } // print 方法结束
} // MatchUtils 类结束
