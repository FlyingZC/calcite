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
// Apache许可证头部声明，说明代码的版权和使用许可
package org.apache.calcite.linq4j.test; // 定义包名，该类位于org.apache.calcite.linq4j.test包下

import org.apache.calcite.linq4j.tree.Primitive; // 导入Primitive类，该类用于表示Java基本类型及其包装类

import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法

import java.util.ArrayList; // 导入ArrayList类，用于创建动态数组
import java.util.List; // 导入List接口，用于表示列表集合

import static org.hamcrest.CoreMatchers.instanceOf; // 导入Hamcrest断言库的instanceOf匹配器，用于检查对象是否是指定类型的实例
import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest断言库的is匹配器，用于检查值是否相等
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest的断言方法，用于执行断言检查
import static org.hamcrest.Matchers.hasToString; // 导入Hamcrest的hasToString匹配器，用于检查对象的toString()方法返回值
import static org.junit.jupiter.api.Assertions.assertFalse; // 导入JUnit 5的assertFalse断言方法，用于验证条件为false
import static org.junit.jupiter.api.Assertions.assertNull; // 导入JUnit 5的assertNull断言方法，用于验证对象为null
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入JUnit 5的assertTrue断言方法，用于验证条件为true
import static org.junit.jupiter.api.Assertions.fail; // 导入JUnit 5的fail方法，用于让测试失败

/**
 * Unit test for {@link Primitive}. // Primitive类的单元测试类
 * 该测试类用于全面测试Primitive枚举类的各种功能，包括类型转换、装箱拆箱、数组操作等
 * Primitive枚举类代表了Java的8种基本类型（boolean, byte, char, short, int, long, float, double）
 * 以及它们对应的包装类，提供了类型安全的操作方法
 */
class PrimitiveTest { // 定义PrimitiveTest测试类
  @Test void testIsAssignableFrom() { // 测试方法：测试Primitive.assignableFrom方法，验证基本类型之间的赋值兼容性
    assertTrue(Primitive.INT.assignableFrom(Primitive.BYTE)); // 验证int类型可以接受byte类型的赋值（byte可以自动提升为int）
    assertTrue(Primitive.INT.assignableFrom(Primitive.SHORT)); // 验证int类型可以接受short类型的赋值（short可以自动提升为int）
    assertTrue(Primitive.INT.assignableFrom(Primitive.CHAR)); // 验证int类型可以接受char类型的赋值（char可以自动提升为int）
    assertTrue(Primitive.INT.assignableFrom(Primitive.INT)); // 验证int类型可以接受int类型的赋值（相同类型）
    assertTrue(Primitive.INT.assignableFrom(Primitive.SHORT)); // 验证int类型可以接受short类型的赋值（重复测试）
    assertFalse(Primitive.INT.assignableFrom(Primitive.LONG)); // 验证int类型不能接受long类型的赋值（long范围更大，需要显式转换）
    assertFalse(Primitive.INT.assignableFrom(Primitive.FLOAT)); // 验证int类型不能接受float类型的赋值（float是浮点类型，需要显式转换）
    assertFalse(Primitive.INT.assignableFrom(Primitive.DOUBLE)); // 验证int类型不能接受double类型的赋值（double范围更大，需要显式转换）

    assertTrue(Primitive.LONG.assignableFrom(Primitive.BYTE)); // 验证long类型可以接受byte类型的赋值
    assertTrue(Primitive.LONG.assignableFrom(Primitive.SHORT)); // 验证long类型可以接受short类型的赋值
    assertTrue(Primitive.LONG.assignableFrom(Primitive.CHAR)); // 验证long类型可以接受char类型的赋值
    assertTrue(Primitive.LONG.assignableFrom(Primitive.INT)); // 验证long类型可以接受int类型的赋值
    assertTrue(Primitive.LONG.assignableFrom(Primitive.LONG)); // 验证long类型可以接受long类型的赋值
    assertFalse(Primitive.LONG.assignableFrom(Primitive.FLOAT)); // 验证long类型不能接受float类型的赋值
    assertFalse(Primitive.LONG.assignableFrom(Primitive.DOUBLE)); // 验证long类型不能接受double类型的赋值

    // SHORT and CHAR cannot be assigned to each other // 注释说明：short和char类型不能互相赋值
    // 这是因为short是有符号16位整数（-32768到32767），而char是无符号16位整数（0到65535）

    assertTrue(Primitive.SHORT.assignableFrom(Primitive.BYTE)); // 验证short类型可以接受byte类型的赋值
    assertTrue(Primitive.SHORT.assignableFrom(Primitive.SHORT)); // 验证short类型可以接受short类型的赋值
    assertFalse(Primitive.SHORT.assignableFrom(Primitive.CHAR)); // 验证short类型不能接受char类型的赋值（char是无符号的）
    assertFalse(Primitive.SHORT.assignableFrom(Primitive.INT)); // 验证short类型不能接受int类型的赋值（int范围更大）
    assertFalse(Primitive.SHORT.assignableFrom(Primitive.LONG)); // 验证short类型不能接受long类型的赋值
    assertFalse(Primitive.SHORT.assignableFrom(Primitive.FLOAT)); // 验证short类型不能接受float类型的赋值
    assertFalse(Primitive.SHORT.assignableFrom(Primitive.DOUBLE)); // 验证short类型不能接受double类型的赋值

    assertFalse(Primitive.CHAR.assignableFrom(Primitive.BYTE)); // 验证char类型不能接受byte类型的赋值
    assertFalse(Primitive.CHAR.assignableFrom(Primitive.SHORT)); // 验证char类型不能接受short类型的赋值
    assertTrue(Primitive.CHAR.assignableFrom(Primitive.CHAR)); // 验证char类型可以接受char类型的赋值
    assertFalse(Primitive.CHAR.assignableFrom(Primitive.INT)); // 验证char类型不能接受int类型的赋值
    assertFalse(Primitive.CHAR.assignableFrom(Primitive.LONG)); // 验证char类型不能接受long类型的赋值
    assertFalse(Primitive.CHAR.assignableFrom(Primitive.FLOAT)); // 验证char类型不能接受float类型的赋值
    assertFalse(Primitive.CHAR.assignableFrom(Primitive.DOUBLE)); // 验证char类型不能接受double类型的赋值

    assertTrue(Primitive.DOUBLE.assignableFrom(Primitive.BYTE)); // 验证double类型可以接受byte类型的赋值（所有整数类型都可以自动转换为double）
    assertTrue(Primitive.DOUBLE.assignableFrom(Primitive.SHORT)); // 验证double类型可以接受short类型的赋值
    assertTrue(Primitive.DOUBLE.assignableFrom(Primitive.CHAR)); // 验证double类型可以接受char类型的赋值
    assertTrue(Primitive.DOUBLE.assignableFrom(Primitive.INT)); // 验证double类型可以接受int类型的赋值
    assertTrue(Primitive.DOUBLE.assignableFrom(Primitive.LONG)); // 验证double类型可以接受long类型的赋值
    assertTrue(Primitive.DOUBLE.assignableFrom(Primitive.FLOAT)); // 验证double类型可以接受float类型的赋值（float可以自动提升为double）
    assertTrue(Primitive.DOUBLE.assignableFrom(Primitive.DOUBLE)); // 验证double类型可以接受double类型的赋值

    // cross-family assignments // 注释说明：跨类型家族的赋值检查
    // 测试boolean类型与其他数值类型之间的赋值兼容性

    assertFalse(Primitive.BOOLEAN.assignableFrom(Primitive.INT)); // 验证boolean类型不能接受int类型的赋值（boolean和数值类型不兼容）
    assertFalse(Primitive.INT.assignableFrom(Primitive.BOOLEAN)); // 验证int类型不能接受boolean类型的赋值（数值类型和boolean不兼容）
  }

  @Test void testBox() { // 测试方法：测试Primitive.box方法，验证基本类型到包装类的装箱操作
    assertThat(Primitive.box(String.class), is(String.class)); // 验证String类（非基本类型）装箱后仍为String类
    assertThat(Primitive.box(int.class), is(Integer.class)); // 验证int基本类型装箱后为Integer包装类
    assertThat(Primitive.box(Integer.class), is(Integer.class)); // 验证Integer包装类装箱后仍为Integer包装类（已经是包装类）
    assertThat(Primitive.box(boolean[].class), is(boolean[].class)); // 验证基本类型数组装箱后仍为数组类型（数组不会被装箱）
  }

  @Test void testOfBox() { // 测试方法：测试Primitive.ofBox方法，验证从包装类获取对应的Primitive枚举值
    assertThat(Primitive.ofBox(Integer.class), is(Primitive.INT)); // 验证Integer包装类对应的Primitive枚举值为INT
    assertNull(Primitive.ofBox(int.class)); // 验证int基本类型对应的Primitive枚举值为null（ofBox只接受包装类）
    assertNull(Primitive.ofBox(String.class)); // 验证String类对应的Primitive枚举值为null（String不是基本类型）
    assertNull(Primitive.ofBox(Integer[].class)); // 验证Integer数组对应的Primitive枚举值为null（数组不是基本类型）
  }

  @Test void testOfBoxOr() { // 测试方法：测试Primitive.ofBox方法（注意：这个测试方法名可能是testOfBox的误写）
    assertThat(Primitive.ofBox(Integer.class), is(Primitive.INT)); // 验证Integer包装类对应的Primitive枚举值为INT
    assertNull(Primitive.ofBox(int.class)); // 验证int基本类型对应的Primitive枚举值为null
    assertNull(Primitive.ofBox(String.class)); // 验证String类对应的Primitive枚举值为null
    assertNull(Primitive.ofBox(Integer[].class)); // 验证Integer数组对应的Primitive枚举值为null
  }

  /** Tests the {@link Primitive#number(Number)} method. */ // JavaDoc注释：测试Primitive.number方法
  @Test void testNumber() { // 测试方法：测试Primitive.number方法，验证数值类型转换功能
    Number number = Primitive.SHORT.number(2); // 调用Primitive.SHORT的number方法，将整数2转换为Short类型的Number对象
    assertThat(number, instanceOf(Short.class)); // 验证转换后的对象是Short类的实例
    assertThat(number.shortValue(), is((short) 2)); // 验证转换后的Short对象的值为2

    number = Primitive.FLOAT.number(2); // 调用Primitive.FLOAT的number方法，将整数2转换为Float类型的Number对象
    assertThat(number, instanceOf(Float.class)); // 验证转换后的对象是Float类的实例
    assertThat(number.doubleValue(), is(2.0d)); // 验证转换后的Float对象的double值为2.0

    try { // 开始异常测试块
      number = Primitive.INT.number(null); // 尝试将null值转换为INT类型
      fail("expected exception, got " + number); // 如果没有抛出异常，则测试失败
    } catch (NullPointerException e) { // 捕获NullPointerException异常
      // ok // 注释说明：捕获到预期的异常，测试通过
    }

    // not a number // 注释说明：测试非数值类型
    try { // 开始异常测试块
      number = Primitive.CHAR.number(3); // 尝试将整数3转换为CHAR类型（CHAR不是数值类型）
      fail("expected exception, got " + number); // 如果没有抛出异常，则测试失败
    } catch (AssertionError e) { // 捕获AssertionError异常
      assertThat(e.getMessage(), is("CHAR: 3")); // 验证异常消息为"CHAR: 3"
    }

    // not a number // 注释说明：测试非数值类型
    try { // 开始异常测试块
      number = Primitive.BOOLEAN.number(null); // 尝试将null值转换为BOOLEAN类型（BOOLEAN不是数值类型）
      fail("expected exception, got " + number); // 如果没有抛出异常，则测试失败
    } catch (AssertionError e) { // 捕获AssertionError异常
      assertThat(e.getMessage(), is("BOOLEAN: null")); // 验证异常消息为"BOOLEAN: null"
    }
  }

  /** Test for // JavaDoc注释：测试Primitive.send方法
   * {@link Primitive#send(org.apache.calcite.linq4j.tree.Primitive.Source, org.apache.calcite.linq4j.tree.Primitive.Sink)}. */ // send方法用于从Source获取值并发送到Sink
  @Test void testSendSource() { // 测试方法：测试Primitive.send方法，验证从Source获取值并发送到Sink的功能
    final List<Object> list = new ArrayList<>(); // 创建一个ArrayList用于记录send操作的过程
    for (Primitive primitive : Primitive.values()) { // 遍历所有Primitive枚举值（BOOLEAN, BYTE, CHAR, SHORT, INT, LONG, FLOAT, DOUBLE）
      primitive.send( // 调用当前Primitive枚举值的send方法
          new Primitive.Source() { // 创建匿名内部类实现Primitive.Source接口，用于提供各种类型的值
            public boolean getBoolean() { // 实现getBoolean方法，提供boolean类型的值
              list.add(boolean.class); // 将boolean.class添加到列表中，记录类型
              return true; // 返回true值
            }

            public byte getByte() { // 实现getByte方法，提供byte类型的值
              list.add(byte.class); // 将byte.class添加到列表中，记录类型
              return 0; // 返回0值
            }

            public char getChar() { // 实现getChar方法，提供char类型的值
              list.add(char.class); // 将char.class添加到列表中，记录类型
              return 0; // 返回0值（字符'\u0000'）
            }

            public short getShort() { // 实现getShort方法，提供short类型的值
              list.add(short.class); // 将short.class添加到列表中，记录类型
              return 0; // 返回0值
            }

            public int getInt() { // 实现getInt方法，提供int类型的值
              list.add(int.class); // 将int.class添加到列表中，记录类型
              return 0; // 返回0值
            }

            public long getLong() { // 实现getLong方法，提供long类型的值
              list.add(long.class); // 将long.class添加到列表中，记录类型
              return 0; // 返回0值
            }

            public float getFloat() { // 实现getFloat方法，提供float类型的值
              list.add(float.class); // 将float.class添加到列表中，记录类型
              return 0; // 返回0.0f值
            }

            public double getDouble() { // 实现getDouble方法，提供double类型的值
              list.add(double.class); // 将double.class添加到列表中，记录类型
              return 0; // 返回0.0d值
            }

            public Object getObject() { // 实现getObject方法，提供Object类型的值
              list.add(Object.class); // 将Object.class添加到列表中，记录类型
              return 0; // 返回0值（自动装箱为Integer对象）
            }
          },
          new Primitive.Sink() { // 创建匿名内部类实现Primitive.Sink接口，用于接收各种类型的值
            public void set(boolean v) { // 实现set方法，接收boolean类型的值
              list.add(boolean.class); // 将boolean.class添加到列表中，记录类型
              list.add(v); // 将接收到的值添加到列表中
            }

            public void set(byte v) { // 实现set方法，接收byte类型的值
              list.add(byte.class); // 将byte.class添加到列表中，记录类型
              list.add(v); // 将接收到的值添加到列表中
            }

            public void set(char v) { // 实现set方法，接收char类型的值
              list.add(char.class); // 将char.class添加到列表中，记录类型
              list.add(v); // 将接收到的值添加到列表中
            }

            public void set(short v) { // 实现set方法，接收short类型的值
              list.add(short.class); // 将short.class添加到列表中，记录类型
              list.add(v); // 将接收到的值添加到列表中
            }

            public void set(int v) { // 实现set方法，接收int类型的值
              list.add(int.class); // 将int.class添加到列表中，记录类型
              list.add(v); // 将接收到的值添加到列表中
            }

            public void set(long v) { // 实现set方法，接收long类型的值
              list.add(long.class); // 将long.class添加到列表中，记录类型
              list.add(v); // 将接收到的值添加到列表中
            }

            public void set(float v) { // 实现set方法，接收float类型的值
              list.add(float.class); // 将float.class添加到列表中，记录类型
              list.add(v); // 将接收到的值添加到列表中
            }

            public void set(double v) { // 实现set方法，接收double类型的值
              list.add(double.class); // 将double.class添加到列表中，记录类型
              list.add(v); // 将接收到的值添加到列表中
            }

            public void set(Object v) { // 实现set方法，接收Object类型的值
              list.add(Object.class); // 将Object.class添加到列表中，记录类型
              list.add(v); // 将接收到的值添加到列表中
            }
          });
    }
    assertThat(list, // 验证list的内容是否符合预期
        hasToString("[boolean, boolean, true, " // BOOLEAN类型：从Source获取boolean值，然后发送给Sink的set(boolean)方法
            + "byte, byte, 0, " // BYTE类型：从Source获取byte值，然后发送给Sink的set(byte)方法
            + "char, char, \u0000, " // CHAR类型：从Source获取char值，然后发送给Sink的set(char)方法
            + "short, short, 0, " // SHORT类型：从Source获取short值，然后发送给Sink的set(short)方法
            + "int, int, 0, " // INT类型：从Source获取int值，然后发送给Sink的set(int)方法
            + "long, long, 0, " // LONG类型：从Source获取long值，然后发送给Sink的set(long)方法
            + "float, float, 0.0, " // FLOAT类型：从Source获取float值，然后发送给Sink的set(float)方法
            + "double, double, 0.0, " // DOUBLE类型：从Source获取double值，然后发送给Sink的set(double)方法
            + "class java.lang.Object, class java.lang.Object, 0, " // OBJECT类型：从Source获取Object值，然后发送给Sink的set(Object)方法
            + "class java.lang.Object, class java.lang.Object, 0]")); // 再次验证OBJECT类型（Primitive.values()包含OBJECT和VOID）
  }

  /** Test for {@link Primitive#permute(Object, int[])}. */ // JavaDoc注释：测试Primitive.permute方法
  @Test void testPermute() { // 测试方法：测试Primitive.permute方法，验证数组元素的重排列功能
    char[] chars = {'a', 'b', 'c', 'd', 'e', 'f', 'g'}; // 创建一个字符数组，包含7个字符
    int[] sources = {1, 2, 3, 4, 5, 6, 0}; // 创建一个源索引数组，指定新数组的每个元素应该从原数组的哪个位置获取
    final Object permute = Primitive.CHAR.permute(chars, sources); // 调用Primitive.CHAR的permute方法，根据sources数组重排chars数组
    assertThat(permute, instanceOf(char[].class)); // 验证返回的对象是char数组的实例
    assertThat(String.valueOf((char[]) permute), is("bcdefga")); // 验证重排后的数组内容为"bcdefga"（原数组向左循环移动一位）
  }

  /** Test for {@link Primitive#arrayToString(Object)}. */ // JavaDoc注释：测试Primitive.arrayToString方法
  @Test void testArrayToString() { // 测试方法：测试Primitive.arrayToString方法，验证数组转换为字符串的功能
    char[] chars = {'a', 'b', 'c', 'd', 'e', 'f', 'g'}; // 创建一个字符数组，包含7个字符
    assertThat(Primitive.CHAR.arrayToString(chars), // 调用Primitive.CHAR的arrayToString方法，将字符数组转换为字符串
        is("[a, b, c, d, e, f, g]")); // 验证转换后的字符串格式为"[a, b, c, d, e, f, g]"
  }

  /** Test for {@link Primitive#sortArray(Object)}. */ // JavaDoc注释：测试Primitive.sortArray方法
  @Test void testArraySort() { // 测试方法：测试Primitive.sortArray方法，验证数组排序功能
    char[] chars = {'m', 'o', 'n', 'o', 'l', 'a', 'k', 'e'}; // 创建一个字符数组，包含8个无序字符
    Primitive.CHAR.sortArray(chars); // 调用Primitive.CHAR的sortArray方法，对字符数组进行排序
    assertThat(Primitive.CHAR.arrayToString(chars), // 验证排序后的数组内容
        is("[a, e, k, l, m, n, o, o]")); // 验证排序结果为字母顺序

    // mixed true and false // 注释说明：测试混合true和false的布尔数组排序
    boolean[] booleans0 = {true, false, true, true, false}; // 创建一个布尔数组，包含true和false混合
    Primitive.BOOLEAN.sortArray(booleans0); // 调用Primitive.BOOLEAN的sortArray方法，对布尔数组进行排序
    assertThat(Primitive.BOOLEAN.arrayToString(booleans0), // 验证排序后的数组内容
        is("[false, false, true, true, true]")); // 验证排序结果为false在前，true在后

    // all false // 注释说明：测试全为false的布尔数组排序
    boolean[] booleans1 = {false, false, false, false, false}; // 创建一个全为false的布尔数组
    Primitive.BOOLEAN.sortArray(booleans1); // 调用Primitive.BOOLEAN的sortArray方法，对布尔数组进行排序
    assertThat(Primitive.BOOLEAN.arrayToString(booleans1), // 验证排序后的数组内容
        is("[false, false, false, false, false]")); // 验证排序结果保持不变

    // all true // 注释说明：测试全为true的布尔数组排序
    boolean[] booleans2 = {true, true, true, true, true}; // 创建一个全为true的布尔数组
    Primitive.BOOLEAN.sortArray(booleans2); // 调用Primitive.BOOLEAN的sortArray方法，对布尔数组进行排序
    assertThat(Primitive.BOOLEAN.arrayToString(booleans2), // 验证排序后的数组内容
        is("[true, true, true, true, true]")); // 验证排序结果保持不变

    // empty // 注释说明：测试空布尔数组排序
    boolean[] booleans3 = {}; // 创建一个空的布尔数组
    Primitive.BOOLEAN.sortArray(booleans3); // 调用Primitive.BOOLEAN的sortArray方法，对空数组进行排序
    assertThat(Primitive.BOOLEAN.arrayToString(booleans3), is("[]")); // 验证排序结果仍为空数组

    // ranges specified // 注释说明：测试指定范围的布尔数组排序
    boolean[] booleans4 = {true, true, false, false, true, false, false}; // 创建一个布尔数组，包含7个元素
    Primitive.BOOLEAN.sortArray(booleans4, 1, 6); // 调用Primitive.BOOLEAN的sortArray方法，对索引1到6（不包括6）的元素进行排序
    assertThat(Primitive.BOOLEAN.arrayToString(booleans4), // 验证排序后的数组内容
        is("[true, false, false, false, true, true, false]")); // 验证排序结果：索引0保持不变，索引1-5被排序，索引6保持不变
  }
} // 类定义结束
