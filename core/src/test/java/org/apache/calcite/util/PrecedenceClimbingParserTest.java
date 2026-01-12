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
package org.apache.calcite.util; // 定义包名，该类属于org.apache.calcite.util包，提供工具类实现

import com.google.common.collect.ImmutableList; // 导入Google Guava库的不可变列表类，用于创建不可修改的列表集合

import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest断言库的is匹配器，用于验证值相等
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言工具类，用于执行断言验证

/**
 * Unit test for {@link PrecedenceClimbingParser}. // PrecedenceClimbingParser的单元测试类
 * 
 * 这个测试类用于测试PrecedenceClimbingParser（优先级爬升解析器）的各种功能。
 * PrecedenceClimbingParser是一种用于解析表达式的解析器，它能够正确处理运算符的优先级和结合性。
 * 
 * 核心概念：
 * 1. 优先级（Precedence）：运算符的优先级决定了运算的执行顺序，优先级高的先执行
 * 2. 结合性（Associativity）：运算符的结合性决定了相同优先级运算符的执行顺序
 *    - 左结合（Left-associative）：从左到右执行，如 a + b + c = (a + b) + c
 *    - 右结合（Right-associative）：从右到左执行，如 a ^ b ^ c = a ^ (b ^ c)
 * 3. 前缀运算符（Prefix）：出现在操作数之前的运算符，如 -a
 * 4. 中缀运算符（Infix）：出现在两个操作数之间的运算符，如 a + b
 * 5. 后缀运算符（Postfix）：出现在操作数之后的运算符，如 a!
 * 6. 原子（Atom）：表达式的基本单元，如变量名、常量等
 * 7. 特殊运算符（Special）：需要多个操作数的特殊运算符，如 BETWEEN a AND b AND c
 * 
 * 测试覆盖的场景：
 * - 基本功能测试：混合使用前缀、中缀、后缀运算符
 * - 重复前缀/后缀测试：测试多个连续的前缀或后缀运算符
 * - 单独原子测试：测试只有原子的情况
 * - 只有前缀运算符测试：测试只有前缀运算符的情况
 * - 只有后缀运算符测试：测试只有后缀运算符的情况
 * - 左结合性测试：测试左结合运算符的解析
 * - 右结合性测试：测试右结合运算符的解析
 * - 特殊运算符测试：测试BETWEEN等需要多个操作数的特殊运算符
 * - 相同优先级测试：测试相同优先级但不同结合性的运算符
 */
class PrecedenceClimbingParserTest { // 定义测试类，用于测试PrecedenceClimbingParser解析器的各种功能
  @Test void testBasic() { // 测试方法：测试解析器的基本功能，包括前缀、中缀、后缀运算符的混合使用
    final PrecedenceClimbingParser p = new PrecedenceClimbingParser.Builder() // 创建解析器构建器，用于构建解析器实例
        .atom("a") // 添加原子"a"，表示一个基本操作数或变量
        .infix("+", 1, true) // 添加中缀运算符"+"，优先级为1，true表示左结合
        .prefix("-", 3) // 添加前缀运算符"-"，优先级为3，前缀运算符只有一个操作数
        .atom("b") // 添加原子"b"，表示一个基本操作数或变量
        .infix("*", 2, true) // 添加中缀运算符"*"，优先级为2，true表示左结合
        .atom("c") // 添加原子"c"，表示一个基本操作数或变量
        .postfix("!", 4) // 添加后缀运算符"!"，优先级为4，后缀运算符只有一个操作数
        .build(); // 构建解析器实例，完成解析器的创建
    final PrecedenceClimbingParser.Token token = p.parse(); // 调用解析器的parse方法，解析表达式并返回语法树根节点token
    assertThat(p.print(token), is("(a + ((- b) * (c !)))")); // 断言验证：将解析后的语法树打印成字符串，验证解析结果是否正确
    // 解析结果分析：
    // 原始表达式序列：a, +, -, b, *, c, !
    // 根据优先级：!(4) > -(3) > *(2) > +(1)
    // 解析过程：
    // 1. c! 后缀运算符优先级最高，先结合：c!
    // 2. -b 前缀运算符优先级次之，结合：-b
    // 3. (-b) * (c!) 中缀运算符*优先级再次，结合：(-b) * (c!)
    // 4. a + ((-b) * (c!)) 中缀运算符+优先级最低，最后结合：a + ((-b) * (c!))
    // 最终结果：(a + ((- b) * (c !)))
  }

  @Test void testRepeatedPrefixPostfix() { // 测试方法：测试多个连续的前缀和后缀运算符的解析
    final PrecedenceClimbingParser p = new PrecedenceClimbingParser.Builder() // 创建解析器构建器
        .prefix("+", 3) // 添加前缀运算符"+"，优先级为3
        .prefix("-", 3) // 添加前缀运算符"-"，优先级为3
        .prefix("+", 3) // 添加前缀运算符"+"，优先级为3
        .prefix("+", 3) // 添加前缀运算符"+"，优先级为3
        .atom("a") // 添加原子"a"
        .postfix("!", 4) // 添加后缀运算符"!"，优先级为4
        .infix("+", 1, true) // 添加中缀运算符"+"，优先级为1，左结合
        .prefix("-", 3) // 添加前缀运算符"-"，优先级为3
        .prefix("-", 3) // 添加前缀运算符"-"，优先级为3
        .atom("b") // 添加原子"b"
        .postfix("!", 4) // 添加后缀运算符"!"，优先级为4
        .postfix("!", 4) // 添加后缀运算符"!"，优先级为4
        .build(); // 构建解析器实例
    final PrecedenceClimbingParser.Token token = p.parse(); // 解析表达式并返回语法树根节点
    assertThat(p.print(token), // 断言验证解析结果
        is("((+ (- (+ (+ (a !))))) + (- (- ((b !) !))))")); // 验证解析后的字符串表示是否正确
    // 解析结果分析：
    // 原始表达式序列：+, -, +, +, a, !, +, -, -, b, !, !
    // 解析过程：
    // 1. a! 后缀运算符优先级最高：(a!)
    // 2. +, -, +, + 是连续的前缀运算符，从右到左结合：+(-(+(+ (a!))))
    // 3. b!!, 是连续的后缀运算符，从左到右结合：((b!)!)
    // 4. -, - 是连续的前缀运算符，从右到左结合：-(-((b!)!))
    // 5. 最后用中缀+连接两个部分：(+(-(+(+ (a!))))) + (-(-((b!)!)))
    // 最终结果：((+ (- (+ (+ (a !))))) + (- (- ((b !) !))))
  }

  @Test void testAtom() { // 测试方法：测试只有原子的情况
    final PrecedenceClimbingParser p = new PrecedenceClimbingParser.Builder() // 创建解析器构建器
        .atom("a") // 添加原子"a"
        .build(); // 构建解析器实例
    final PrecedenceClimbingParser.Token token = p.parse(); // 解析表达式并返回语法树根节点
    assertThat(p.print(token), is("a")); // 断言验证：只有原子时，解析结果应该就是原子本身
    // 解析结果分析：
    // 原始表达式序列：a
    // 没有任何运算符，只有一个原子
    // 最终结果：a
  }

  @Test void testOnlyPrefix() { // 测试方法：测试只有前缀运算符的情况
    final PrecedenceClimbingParser p = new PrecedenceClimbingParser.Builder() // 创建解析器构建器
        .prefix("-", 3) // 添加前缀运算符"-"，优先级为3
        .prefix("-", 3) // 添加前缀运算符"-"，优先级为3
        .atom(1) // 添加原子1（整数）
        .build(); // 构建解析器实例
    final PrecedenceClimbingParser.Token token = p.parse(); // 解析表达式并返回语法树根节点
    assertThat(p.print(token), is("(- (- 1))")); // 断言验证：两个前缀-应该从右到左结合
    // 解析结果分析：
    // 原始表达式序列：-, -, 1
    // 前缀运算符从右到左结合：
    // 1. 先结合右边的-和1：-1
    // 2. 再结合左边的-和-1：-(-1)
    // 最终结果：(- (- 1))
  }

  @Test void testOnlyPostfix() { // 测试方法：测试只有后缀运算符的情况
    final PrecedenceClimbingParser p = new PrecedenceClimbingParser.Builder() // 创建解析器构建器
        .atom(1) // 添加原子1（整数）
        .postfix("!", 33333) // 添加后缀运算符"!"，优先级为33333（任意高值）
        .postfix("!", 33333) // 添加后缀运算符"!"，优先级为33333
        .build(); // 构建解析器实例
    final PrecedenceClimbingParser.Token token = p.parse(); // 解析表达式并返回语法树根节点
    assertThat(p.print(token), is("((1 !) !)")); // 断言验证：两个后缀!应该从左到右结合
    // 解析结果分析：
    // 原始表达式序列：1, !, !
    // 后缀运算符从左到右结合：
    // 1. 先结合左边的!和1：1!
    // 2. 再结合右边的!和1!：(1!)!
    // 最终结果：((1 !) !)
  }

  @Test void testLeftAssociative() { // 测试方法：测试左结合运算符的解析
    final PrecedenceClimbingParser p = new PrecedenceClimbingParser.Builder() // 创建解析器构建器
        .atom("a") // 添加原子"a"
        .infix("*", 2, true) // 添加中缀运算符"*"，优先级为2，true表示左结合
        .atom("b") // 添加原子"b"
        .infix("+", 1, true) // 添加中缀运算符"+"，优先级为1，左结合
        .atom("c") // 添加原子"c"
        .infix("+", 1, true) // 添加中缀运算符"+"，优先级为1，左结合
        .atom("d") // 添加原子"d"
        .infix("+", 1, true) // 添加中缀运算符"+"，优先级为1，左结合
        .atom("e") // 添加原子"e"
        .infix("*", 2, true) // 添加中缀运算符"*"，优先级为2，左结合
        .atom("f") // 添加原子"f"
        .build(); // 构建解析器实例
    final PrecedenceClimbingParser.Token token = p.parse(); // 解析表达式并返回语法树根节点
    assertThat(p.print(token), is("((((a * b) + c) + d) + (e * f))")); // 断言验证：左结合运算符应该从左到右结合
    // 解析结果分析：
    // 原始表达式序列：a, *, b, +, c, +, d, +, e, *, f
    // 根据优先级：*(2) > +(1)
    // 解析过程：
    // 1. a * b 优先级高，先结合：(a * b)
    // 2. e * f 优先级高，先结合：(e * f)
    // 3. (a * b) + c 左结合，从左到右：((a * b) + c)
    // 4. ((a * b) + c) + d 左结合，从左到右：(((a * b) + c) + d)
    // 5. (((a * b) + c) + d) + (e * f) 左结合，从左到右：((((a * b) + c) + d) + (e * f))
    // 最终结果：((((a * b) + c) + d) + (e * f))
  }

  @Test void testRightAssociative() { // 测试方法：测试右结合运算符的解析
    final PrecedenceClimbingParser p = new PrecedenceClimbingParser.Builder() // 创建解析器构建器
        .atom("a") // 添加原子"a"
        .infix("^", 3, false) // 添加中缀运算符"^"，优先级为3，false表示右结合
        .atom("b") // 添加原子"b"
        .infix("^", 3, false) // 添加中缀运算符"^"，优先级为3，右结合
        .atom("c") // 添加原子"c"
        .infix("^", 3, false) // 添加中缀运算符"^"，优先级为3，右结合
        .atom("d") // 添加原子"d"
        .infix("+", 1, true) // 添加中缀运算符"+"，优先级为1，左结合
        .atom("e") // 添加原子"e"
        .infix("*", 2, true) // 添加中缀运算符"*"，优先级为2，左结合
        .atom("f") // 添加原子"f"
        .build(); // 构建解析器实例
    final PrecedenceClimbingParser.Token token = p.parse(); // 解析表达式并返回语法树根节点
    assertThat(p.print(token), is("((a ^ (b ^ (c ^ d))) + (e * f))")); // 断言验证：右结合运算符应该从右到左结合
    // 解析结果分析：
    // 原始表达式序列：a, ^, b, ^, c, ^, d, +, e, *, f
    // 根据优先级：^(3) > *(2) > +(1)
    // 解析过程：
    // 1. e * f 优先级次高，先结合：(e * f)
    // 2. a ^ b ^ c ^ d 都是右结合，从右到左：
    //    - c ^ d 先结合：c ^ d
    //    - b ^ (c ^ d) 再结合：b ^ (c ^ d)
    //    - a ^ (b ^ (c ^ d)) 最后结合：a ^ (b ^ (c ^ d))
    // 3. (a ^ (b ^ (c ^ d))) + (e * f) 左结合，优先级最低
    // 最终结果：((a ^ (b ^ (c ^ d))) + (e * f))
  }

  @Test void testSpecial() { // 测试方法：测试特殊运算符（如BETWEEN）的解析
    // price > 5 and price between 1 + 2 and 3 * 4 and price is null // 注释：测试SQL表达式，包含BETWEEN特殊运算符
    final PrecedenceClimbingParser p = new PrecedenceClimbingParser.Builder() // 创建解析器构建器
        .atom("price") // 添加原子"price"
        .infix(">", 4, true) // 添加中缀运算符">"，优先级为4，左结合
        .atom("5") // 添加原子"5"
        .infix("and", 2, true) // 添加中缀运算符"and"，优先级为2，左结合
        .atom("price") // 添加原子"price"
        .special("between", 3, 3, // 添加特殊运算符"between"，优先级为3，需要3个操作数
            (parser, op) -> // 提供一个lambda函数来处理特殊运算符的解析
                new PrecedenceClimbingParser.Result(op.previous, // 创建解析结果，第一个操作数是op.previous（price）
                    op.next.next.next, // 第二个操作数是op.next.next.next（3 * 4的结果）
                    parser.call(op, // 调用parser.call创建函数调用节点
                        ImmutableList.of(op.previous, op.next, // 操作数列表：price, 1+2, 3*4
                            op.next.next.next)))) // 第三个操作数
        .atom("1") // 添加原子"1"
        .infix("+", 5, true) // 添加中缀运算符"+"，优先级为5，左结合
        .atom("2") // 添加原子"2"
        .infix("and", 2, true) // 添加中缀运算符"and"，优先级为2，左结合
        .atom("3") // 添加原子"3"
        .infix("*", 6, true) // 添加中缀运算符"*"，优先级为6，左结合
        .atom("4") // 添加原子"4"
        .infix("and", 2, true) // 添加中缀运算符"and"，优先级为2，左结合
        .atom("price") // 添加原子"price"
        .postfix("is null", 4) // 添加后缀运算符"is null"，优先级为4
        .build(); // 构建解析器实例
    final PrecedenceClimbingParser.Token token = p.parse(); // 解析表达式并返回语法树根节点
    assertThat(p.print(token), // 断言验证解析结果
        is("(((price > 5) and between(price, (1 + 2), (3 * 4)))" // 验证解析后的字符串表示
            + " and (price is null))")); // 拼接第二部分
    // 解析结果分析：
    // 原始表达式序列：price, >, 5, and, price, between, 1, +, 2, and, 3, *, 4, and, price, is null
    // 根据优先级：*(6) > +(5) > >(4), is null(4) > between(3) > and(2)
    // 解析过程：
    // 1. 1 + 2 优先级高，先结合：(1 + 2)
    // 2. 3 * 4 优先级最高，先结合：(3 * 4)
    // 3. price > 4 优先级次高，结合：(price > 5)
    // 4. price is null 后缀运算符，结合：(price is null)
    // 5. between(price, (1 + 2), (3 * 4)) 特殊运算符，需要3个操作数
    // 6. (price > 5) and between(price, (1 + 2), (3 * 4)) and优先级低，左结合
    // 7. ((price > 5) and between(price, (1 + 2), (3 * 4))) and (price is null) 最后结合
    // 最终结果：(((price > 5) and between(price, (1 + 2), (3 * 4))) and (price is null))
  }

  @Test void testEqualPrecedence() { // 测试方法：测试相同优先级但不同结合性的运算符
    // LIKE has same precedence as '='; LIKE is right-assoc, '=' is left // 注释：LIKE和=优先级相同，但LIKE右结合，=左结合
    final PrecedenceClimbingParser p = new PrecedenceClimbingParser.Builder() // 创建解析器构建器
        .atom("a") // 添加原子"a"
        .infix("=", 3, true) // 添加中缀运算符"="，优先级为3，true表示左结合
        .atom("b") // 添加原子"b"
        .infix("like", 3, false) // 添加中缀运算符"like"，优先级为3，false表示右结合
        .atom("c") // 添加原子"c"
        .infix("=", 3, true) // 添加中缀运算符"="，优先级为3，左结合
        .atom("d") // 添加原子"d"
        .build(); // 构建解析器实例
    final PrecedenceClimbingParser.Token token = p.parse(); // 解析表达式并返回语法树根节点
    assertThat(p.print(token), is("(((a = b) like c) = d)")); // 断言验证：相同优先级时，左结合的运算符会先结合
    // 解析结果分析：
    // 原始表达式序列：a, =, b, like, c, =, d
    // 所有运算符优先级相同，都是3
    // 但是结合性不同：=是左结合，like是右结合
    // 解析过程：
    // 1. a = b 左结合，先结合：(a = b)
    // 2. (a = b) like c 右结合，like会尝试和后面的=结合，但是=是左结合
    //    所以like会先和前面的(a = b)结合：((a = b) like c)
    // 3. ((a = b) like c) = d 左结合，最后结合：(((a = b) like c) = d)
    // 最终结果：(((a = b) like c) = d)
    // 
    // 注意：当优先级相同时，左结合的运算符会优先结合，右结合的运算符会等待
    // 这是因为优先级爬升算法在处理相同优先级时，会优先处理左结合的运算符
  }
}
