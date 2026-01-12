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
// Apache许可证声明：允许用户在遵守Apache License 2.0条款的前提下使用、修改和分发本代码
// 本文件是Apache Calcite项目的一部分，Calcite是一个动态数据管理框架，提供SQL解析、优化、执行等功能
package org.apache.calcite.adapter.clone; // 定义包名，表示本类属于org.apache.calcite.adapter.clone包，该包包含与克隆适配器相关的类

import org.apache.calcite.jdbc.JavaTypeFactoryImpl; // 导入Java类型工厂实现类，用于创建和管理Java类型
import org.apache.calcite.linq4j.Enumerable; // 导入可枚举接口，提供类似LINQ的数据查询功能
import org.apache.calcite.linq4j.Linq4j; // 导入LINQ4j工具类，提供静态方法创建可枚举对象
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示关系型数据的类型
import org.apache.calcite.rel.type.RelDataTypeImpl; // 导入关系数据类型实现类
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入关系数据类型系统接口，定义类型系统的行为

import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，用于标记测试方法

import java.util.Arrays; // 导入Java工具类，提供数组操作方法

import static org.hamcrest.CoreMatchers.instanceOf; // 导入Hamcrest匹配器，用于断言对象是否为指定类型的实例
import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest匹配器，用于断言值相等
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言工具类，用于执行断言
import static org.hamcrest.Matchers.hasToString; // 导入Hamcrest匹配器，用于断言对象的toString结果
import static org.junit.jupiter.api.Assertions.assertFalse; // 导入JUnit5断言方法，用于断言条件为假
import static org.junit.jupiter.api.Assertions.assertNull; // 导入JUnit5断言方法，用于断言对象为null
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入JUnit5断言方法，用于断言条件为真

/**
 * Unit test for {@link ArrayTable} and {@link ColumnLoader}.
 * ArrayTable和ColumnLoader的单元测试类
 * ArrayTable：基于数组存储的表实现，使用列式存储，支持多种压缩表示方式
 * ColumnLoader：列加载器，负责将数据加载到ArrayTable中，并选择最优的列表示方式
 * 本测试类验证了ArrayTable和ColumnLoader的核心功能，包括：
 * 1. 位切片原始数组的位操作功能
 * 2. 下一个2的幂次方计算
 * 3. 对数2计算
 * 4. 值集合的各种类型处理（整数、布尔值、字符串、null值）
 * 5. 列加载器的排序功能
 */
class ArrayTableTest { // 定义测试类ArrayTableTest，用于测试ArrayTable和ColumnLoader的功能
  @Test void testPrimitiveArray() { // 测试方法：测试位切片原始数组的基本操作
    long[] values = {0, 0}; // 创建一个包含两个0的long数组，用于存储位操作的结果
    ArrayTable.BitSlicedPrimitiveArray.orLong(4, values, 0, 0x0F); // 调用位切片数组的orLong方法，在第0个位置，使用4位宽度，将0x0F（二进制1111）按位或到values数组中
    assertThat(values[0], is(0x0FL)); // 断言values[0]的值为0x0F（十进制15），验证第一个4位被正确设置
    ArrayTable.BitSlicedPrimitiveArray.orLong(4, values, 2, 0x0F); // 调用位切片数组的orLong方法，在第2个位置，使用4位宽度，将0x0F按位或到values数组中
    assertThat(values[0], is(0xF0FL)); // 断言values[0]的值为0xF0F（十六进制），验证第二个4位也被正确设置（第一个4位在低位，第二个4位在高位）

    values = new long[]{ // 重新初始化values数组，包含三个特定的long值，用于测试getLong方法
        0x1213141516171819L, 0x232425262728292AL, 0x3435363738393A3BL}; // 这三个long值包含了多个12位的值，用于测试位切片读取
    assertThat(ArrayTable.BitSlicedPrimitiveArray.getLong(12, values, 9), // 断言从values数组的第9个位置读取12位值的结果
        is(0x324L)); // 期望值为0x324，验证12位值的正确读取
    assertThat(ArrayTable.BitSlicedPrimitiveArray.getLong(12, values, 10), // 断言从values数组的第10个位置读取12位值的结果
        is(0xa3bL)); // 期望值为0xa3b，验证跨long边界的位读取

    Arrays.fill(values, 0); // 将values数组的所有元素填充为0，为下一个测试做准备
    for (int i = 0; i < 10; i++) { // 循环10次，测试写入和读取的对称性
      ArrayTable.BitSlicedPrimitiveArray.orLong(10, values, i, i); // 在第i个位置，使用10位宽度，将值i写入values数组
    } // 循环结束，完成了0到9的值的写入

    for (int i = 0; i < 10; i++) { // 循环10次，验证写入的值能够正确读取
      assertThat(ArrayTable.BitSlicedPrimitiveArray.getLong(10, values, i), // 断言从values数组的第i个位置读取10位值的结果
          is((long) i)); // 期望值为i，验证写入和读取的正确性
    } // 循环结束，验证完成
  } // 测试方法结束

  @Test void testNextPowerOf2() { // 测试方法：测试计算下一个2的幂次方的功能
    assertThat(ColumnLoader.nextPowerOf2(1), is(1)); // 断言1的下一个2的幂次方是1（1本身就是2的0次方）
    assertThat(ColumnLoader.nextPowerOf2(2), is(2)); // 断言2的下一个2的幂次方是2（2本身就是2的1次方）
    assertThat(ColumnLoader.nextPowerOf2(3), is(4)); // 断言3的下一个2的幂次方是4（4是2的2次方）
    assertThat(ColumnLoader.nextPowerOf2(4), is(4)); // 断言4的下一个2的幂次方是4（4本身就是2的2次方）
    assertThat(ColumnLoader.nextPowerOf2(0x3456789a), is(0x40000000)); // 断言0x3456789a的下一个2的幂次方是0x40000000（2的30次方）
    assertThat(ColumnLoader.nextPowerOf2(0x40000000), is(0x40000000)); // 断言0x40000000的下一个2的幂次方是0x40000000（本身就是2的幂次方）
    // overflow // 注释：以下测试溢出情况
    assertThat(ColumnLoader.nextPowerOf2(0x7fffffff), is(0x80000000)); // 断言0x7fffffff（Integer.MAX_VALUE）的下一个2的幂次方是0x80000000（2的31次方，溢出为负数）
    assertThat(ColumnLoader.nextPowerOf2(0x7ffffffe), is(0x80000000)); // 断言0x7ffffffe的下一个2的幂次方是0x80000000（2的31次方）
  } // 测试方法结束

  @Test void testLog2() { // 测试方法：测试计算以2为底的对数的功能
    assertThat(ColumnLoader.log2(0), is(0)); // 断言log2(0)返回0（特殊情况处理）
    assertThat(ColumnLoader.log2(1), is(0)); // 断言log2(1)返回0（2的0次方等于1）
    assertThat(ColumnLoader.log2(2), is(1)); // 断言log2(2)返回1（2的1次方等于2）
    assertThat(ColumnLoader.log2(4), is(2)); // 断言log2(4)返回2（2的2次方等于4）
    assertThat(ColumnLoader.log2(65536), is(16)); // 断言log2(65536)返回16（2的16次方等于65536）
    assertThat(ColumnLoader.log2(65535), is(15)); // 断言log2(65535)返回15（向下取整）
    assertThat(ColumnLoader.log2(65537), is(16)); // 断言log2(65537)返回16（向上取整）
    assertThat(ColumnLoader.log2(Integer.MAX_VALUE), is(30)); // 断言log2(Integer.MAX_VALUE)返回30
    assertThat(ColumnLoader.log2(Integer.MAX_VALUE - 1), is(30)); // 断言log2(Integer.MAX_VALUE - 1)返回30
    assertThat(ColumnLoader.log2(0x3fffffff), is(29)); // 断言log2(0x3fffffff)返回29
    assertThat(ColumnLoader.log2(0x40000000), is(30)); // 断言log2(0x40000000)返回30
  } // 测试方法结束

  @Test void testValueSetInt() { // 测试方法：测试整数类型值集合的处理
    ArrayTable.BitSlicedPrimitiveArray representation; // 声明位切片原始数组表示对象，用于存储列的表示方式
    ArrayTable.Column pair; // 声明列对象，包含列的数据和表示方式

    final ColumnLoader.ValueSet valueSet = // 创建一个整数类型的值集合，用于收集列的值
        new ColumnLoader.ValueSet(int.class); // 值集合会自动分析值的范围，选择最优的表示方式
    valueSet.add(0); // 向值集合添加值0
    valueSet.add(1); // 向值集合添加值1
    valueSet.add(10); // 向值集合添加值10
    pair = valueSet.freeze(0, null); // 冻结值集合，生成列对象，参数0表示列序号，null表示无类型工厂
    assertThat(pair.representation, // 断言列的表示方式
        instanceOf(ArrayTable.BitSlicedPrimitiveArray.class)); // 是位切片原始数组类型
    representation = // 将列的表示方式转换为位切片原始数组
        (ArrayTable.BitSlicedPrimitiveArray) pair.representation; // 类型转换，便于访问特定位切片数组的属性

    // unsigned 4 bit integer (values 0..15) // 注释：以下测试无符号4位整数（值范围0..15）
    assertThat(representation.bitCount, is(4)); // 断言位数为4，因为值0、1、10都可以用4位表示
    assertFalse(representation.signed); // 断言为无符号类型，因为所有值都是非负数
    assertThat(representation.getInt(pair.dataSet, 0), is(0)); // 断言第0个位置的值为0
    assertThat(representation.getInt(pair.dataSet, 1), is(1)); // 断言第1个位置的值为1
    assertThat(representation.getInt(pair.dataSet, 2), is(10)); // 断言第2个位置的值为10
    assertThat(representation.getObject(pair.dataSet, 2), is(10)); // 断言第2个位置的对象值为10

    // -32 takes us to 6 bit signed // 注释：添加-32后需要6位有符号整数
    valueSet.add(-32); // 向值集合添加值-32，负数需要使用有符号表示
    pair = valueSet.freeze(0, null); // 重新冻结值集合，生成新的列对象
    assertThat(pair.representation, // 断言列的表示方式
        instanceOf(ArrayTable.BitSlicedPrimitiveArray.class)); // 仍然是位切片原始数组类型
    representation = // 将列的表示方式转换为位切片原始数组
        (ArrayTable.BitSlicedPrimitiveArray) pair.representation; // 类型转换
    assertThat(representation.bitCount, is(6)); // 断言位数为6，因为-32到10需要6位有符号表示
    assertTrue(representation.signed); // 断言为有符号类型，因为包含负数
    assertThat(representation.getInt(pair.dataSet, 2), is(10)); // 断言第2个位置的值为10
    assertThat(representation.getObject(pair.dataSet, 2), is(10)); // 断言第2个位置的对象值为10
    assertThat(representation.getInt(pair.dataSet, 3), is(-32)); // 断言第3个位置的值为-32
    assertThat(representation.getObject(pair.dataSet, 3), is(-32)); // 断言第3个位置的对象值为-32

    // 63 takes us to 7 bit signed // 注释：添加63后需要7位有符号整数
    valueSet.add(63); // 向值集合添加值63
    pair = valueSet.freeze(0, null); // 重新冻结值集合，生成新的列对象
    assertThat(pair.representation, // 断言列的表示方式
        instanceOf(ArrayTable.BitSlicedPrimitiveArray.class)); // 仍然是位切片原始数组类型
    representation = // 将列的表示方式转换为位切片原始数组
        (ArrayTable.BitSlicedPrimitiveArray) pair.representation; // 类型转换
    assertThat(representation.bitCount, is(7)); // 断言位数为7，因为-32到63需要7位有符号表示
    assertTrue(representation.signed); // 断言为有符号类型

    // 128 pushes us to 8 bit signed, i.e. byte // 注释：添加128后需要8位有符号整数，即字节
    valueSet.add(64); // 向值集合添加值64
    pair = valueSet.freeze(0, null); // 重新冻结值集合，生成新的列对象
    assertThat(pair.representation, // 断言列的表示方式
        instanceOf(ArrayTable.PrimitiveArray.class)); // 变为原始数组类型（byte数组）
    ArrayTable.PrimitiveArray representation2 = // 将列的表示方式转换为原始数组
        (ArrayTable.PrimitiveArray) pair.representation; // 类型转换
    assertThat(representation2.getInt(pair.dataSet, 0), is(0)); // 断言第0个位置的值为0
    assertThat(representation2.getInt(pair.dataSet, 3), is(-32)); // 断言第3个位置的值为-32
    assertThat(representation2.getObject(pair.dataSet, 3), is(-32)); // 断言第3个位置的对象值为-32
    assertThat(representation2.getInt(pair.dataSet, 5), is(64)); // 断言第5个位置的值为64
    assertThat(representation2.getObject(pair.dataSet, 5), is(64)); // 断言第5个位置的对象值为64
  } // 测试方法结束

  @Test void testValueSetBoolean() { // 测试方法：测试布尔类型值集合的处理
    final ColumnLoader.ValueSet valueSet = // 创建一个布尔类型的值集合
        new ColumnLoader.ValueSet(boolean.class); // 布尔值会被优化为1位表示
    valueSet.add(0); // 向值集合添加值0（表示false）
    valueSet.add(1); // 向值集合添加值1（表示true）
    valueSet.add(1); // 向值集合再次添加值1（重复值）
    valueSet.add(0); // 向值集合再次添加值0（重复值）
    final ArrayTable.Column pair = valueSet.freeze(0, null); // 冻结值集合，生成列对象
    assertThat(pair.representation, // 断言列的表示方式
        instanceOf(ArrayTable.BitSlicedPrimitiveArray.class)); // 是位切片原始数组类型
    final ArrayTable.BitSlicedPrimitiveArray representation = // 将列的表示方式转换为位切片原始数组
        (ArrayTable.BitSlicedPrimitiveArray) pair.representation; // 类型转换

    assertThat(representation.bitCount, is(1)); // 断言位数为1，因为布尔值只需要1位
    assertThat(representation.getInt(pair.dataSet, 0), is(0)); // 断言第0个位置的值为0（false）
    assertThat(representation.getInt(pair.dataSet, 1), is(1)); // 断言第1个位置的值为1（true）
    assertThat(representation.getInt(pair.dataSet, 2), is(1)); // 断言第2个位置的值为1（true）
    assertThat(representation.getInt(pair.dataSet, 3), is(0)); // 断言第3个位置的值为0（false）
  } // 测试方法结束

  @Test void testValueSetZero() { // 测试方法：测试只有0值的值集合的处理
    final ColumnLoader.ValueSet valueSet = // 创建一个布尔类型的值集合
        new ColumnLoader.ValueSet(boolean.class); // 使用布尔类型，但只添加0值
    valueSet.add(0); // 向值集合只添加值0
    final ArrayTable.Column pair = valueSet.freeze(0, null); // 冻结值集合，生成列对象
    assertThat(pair.representation, instanceOf(ArrayTable.Constant.class)); // 断言列的表示方式为常量类型（所有值相同）
    final ArrayTable.Constant representation = // 将列的表示方式转换为常量
        (ArrayTable.Constant) pair.representation; // 类型转换

    assertThat(representation.getInt(pair.dataSet, 0), is(0)); // 断言第0个位置的值为0
    assertThat(pair.cardinality, is(1)); // 断言基数为1（只有一个不同的值）
  } // 测试方法结束

  @Test void testStrings() { // 测试方法：测试字符串类型值集合的处理
    ArrayTable.Column pair; // 声明列对象

    final ColumnLoader.ValueSet valueSet = // 创建一个字符串类型的值集合
        new ColumnLoader.ValueSet(String.class); // 字符串类型需要使用对象数组或对象字典
    valueSet.add("foo"); // 向值集合添加字符串"foo"
    valueSet.add("foo"); // 向值集合再次添加字符串"foo"（重复值）
    pair = valueSet.freeze(0, null); // 冻结值集合，生成列对象
    assertThat(pair.representation, instanceOf(ArrayTable.ObjectArray.class)); // 断言列的表示方式为对象数组类型
    final ArrayTable.ObjectArray representation = // 将列的表示方式转换为对象数组
        (ArrayTable.ObjectArray) pair.representation; // 类型转换
    assertThat(representation.getObject(pair.dataSet, 0), is("foo")); // 断言第0个位置的值为"foo"
    assertThat(representation.getObject(pair.dataSet, 1), is("foo")); // 断言第1个位置的值为"foo"
    assertThat(pair.cardinality, is(1)); // 断言基数为1（只有一个不同的字符串）

    // Large number of the same string. ObjectDictionary backed by Constant. // 注释：大量相同字符串，使用常量支持的对象字典
    for (int i = 0; i < 2000; i++) { // 循环2000次，添加大量相同的字符串
      valueSet.add("foo"); // 每次都添加字符串"foo"
    } // 循环结束，共添加2002个"foo"（前面已添加2个）
    pair = valueSet.freeze(0, null); // 重新冻结值集合，生成列对象
    final ArrayTable.ObjectDictionary representation2 = // 将列的表示方式转换为对象字典
        (ArrayTable.ObjectDictionary) pair.representation; // 类型转换
    assertThat(representation2.representation, // 断言对象字典的底层表示方式
        instanceOf(ArrayTable.Constant.class)); // 是常量类型（因为所有值都相同）
    assertThat(representation2.getObject(pair.dataSet, 0), is("foo")); // 断言第0个位置的值为"foo"
    assertThat(representation2.getObject(pair.dataSet, 1000), is("foo")); // 断言第1000个位置的值为"foo"
    assertThat(pair.cardinality, is(1)); // 断言基数为1（只有一个不同的字符串）

    // One different string. ObjectDictionary backed by 1-bit // 注释：添加一个不同的字符串，使用1位位切片数组支持的对象字典
    // BitSlicedPrimitiveArray // 注释：底层使用1位位切片原始数组
    valueSet.add("bar"); // 向值集合添加字符串"bar"
    pair = valueSet.freeze(0, null); // 重新冻结值集合，生成列对象
    final ArrayTable.ObjectDictionary representation3 = // 将列的表示方式转换为对象字典
        (ArrayTable.ObjectDictionary) pair.representation; // 类型转换
    assertThat(representation3.representation, // 断言对象字典的底层表示方式
        instanceOf(ArrayTable.BitSlicedPrimitiveArray.class)); // 是位切片原始数组类型
    final ArrayTable.BitSlicedPrimitiveArray representation4 = // 将底层表示方式转换为位切片原始数组
        (ArrayTable.BitSlicedPrimitiveArray) representation3.representation; // 类型转换
    assertThat(representation4.bitCount, is(1)); // 断言位数为1（因为只有两个不同的字符串）
    assertFalse(representation4.signed); // 断言为无符号类型
    assertThat(representation3.getObject(pair.dataSet, 0), is("foo")); // 断言第0个位置的值为"foo"
    assertThat(representation3.getObject(pair.dataSet, 1000), is("foo")); // 断言第1000个位置的值为"foo"
    assertThat(representation3.getObject(pair.dataSet, 2003), is("bar")); // 断言第2003个位置的值为"bar"
    assertThat(pair.cardinality, is(2)); // 断言基数为2（有两个不同的字符串）
  } // 测试方法结束

  @Test void testAllNull() { // 测试方法：测试全null值的处理
    ArrayTable.Column pair; // 声明列对象

    final ColumnLoader.ValueSet valueSet = // 创建一个字符串类型的值集合
        new ColumnLoader.ValueSet(String.class); // 字符串类型可以包含null值

    valueSet.add(null); // 向值集合添加null值
    pair = valueSet.freeze(0, null); // 冻结值集合，生成列对象
    assertThat(pair.representation, instanceOf(ArrayTable.ObjectArray.class)); // 断言列的表示方式为对象数组类型
    final ArrayTable.ObjectArray representation = // 将列的表示方式转换为对象数组
        (ArrayTable.ObjectArray) pair.representation; // 类型转换
    assertNull(representation.getObject(pair.dataSet, 0)); // 断言第0个位置的值为null
    assertThat(pair.cardinality, is(1)); // 断言基数为1（只有一个不同的值：null）

    for (int i = 0; i < 3000; i++) { // 循环3000次，添加大量null值
      valueSet.add(null); // 每次都添加null
    } // 循环结束，共添加3001个null
    pair = valueSet.freeze(0, null); // 重新冻结值集合，生成列对象
    final ArrayTable.ObjectDictionary representation2 = // 将列的表示方式转换为对象字典
        (ArrayTable.ObjectDictionary) pair.representation; // 类型转换
    assertThat(representation2.representation, // 断言对象字典的底层表示方式
        instanceOf(ArrayTable.Constant.class)); // 是常量类型（因为所有值都是null）
    assertThat(pair.cardinality, is(1)); // 断言基数为1（只有一个不同的值）
  } // 测试方法结束

  @Test void testOneValueOneNull() { // 测试方法：测试一个值和一个null值的处理
    ArrayTable.Column pair; // 声明列对象

    final ColumnLoader.ValueSet valueSet = // 创建一个字符串类型的值集合
        new ColumnLoader.ValueSet(String.class); // 字符串类型可以包含null值
    valueSet.add(null); // 向值集合添加null值
    valueSet.add("foo"); // 向值集合添加字符串"foo"

    pair = valueSet.freeze(0, null); // 冻结值集合，生成列对象
    assertThat(pair.representation, instanceOf(ArrayTable.ObjectArray.class)); // 断言列的表示方式为对象数组类型
    final ArrayTable.ObjectArray representation = // 将列的表示方式转换为对象数组
        (ArrayTable.ObjectArray) pair.representation; // 类型转换
    assertNull(representation.getObject(pair.dataSet, 0)); // 断言第0个位置的值为null
    assertThat(pair.cardinality, is(2)); // 断言基数为2（有两个不同的值：null和"foo"）

    for (int i = 0; i < 3000; i++) { // 循环3000次，添加大量null值
      valueSet.add(null); // 每次都添加null
    } // 循环结束，共添加3002个null和1个"foo"
    pair = valueSet.freeze(0, null); // 重新冻结值集合，生成列对象
    final ArrayTable.ObjectDictionary representation2 = // 将列的表示方式转换为对象字典
        (ArrayTable.ObjectDictionary) pair.representation; // 类型转换
    assertThat( // 断言对象字典的底层表示方式的位数
        ((ArrayTable.BitSlicedPrimitiveArray) // 将底层表示方式转换为位切片原始数组
            representation2.representation).bitCount, is(1)); // 断言位数为1（因为只有两个不同的值）
    assertThat(representation2.getObject(pair.dataSet, 1), is("foo")); // 断言第1个位置的值为"foo"
    assertNull(representation2.getObject(pair.dataSet, 10)); // 断言第10个位置的值为null
    assertThat(pair.cardinality, is(2)); // 断言基数为2（有两个不同的值）
  } // 测试方法结束

  @Test void testLoadSorted() { // 测试方法：测试列加载器的排序功能
    final JavaTypeFactoryImpl typeFactory = // 创建Java类型工厂实例，使用默认的关系数据类型系统
        new JavaTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 类型工厂用于创建和管理数据类型
    final RelDataType rowType = // 创建行类型，定义行的结构（列名和列类型）
        typeFactory.builder() // 获取类型构建器
            .add("empid", typeFactory.createType(int.class)) // 添加empid列，类型为int
            .add("deptno", typeFactory.createType(int.class)) // 添加deptno列，类型为int
            .add("name", typeFactory.createType(String.class)) // 添加name列，类型为String
            .build(); // 构建行类型
    final Enumerable<Object[]> enumerable = // 创建可枚举对象，包含要加载的数据
        Linq4j.asEnumerable( // 将列表转换为可枚举对象
            Arrays.asList( // 创建包含员工数据的列表
                new Object[]{100, 10, "Bill"}, // 第一条记录：empid=100, deptno=10, name="Bill"
                new Object[]{200, 20, "Eric"}, // 第二条记录：empid=200, deptno=20, name="Eric"
                new Object[]{150, 10, "Sebastian"}, // 第三条记录：empid=150, deptno=10, name="Sebastian"
                new Object[]{160, 10, "Theodore"})); // 第四条记录：empid=160, deptno=10, name="Theodore"
    final ColumnLoader<Object[]> loader = // 创建列加载器，加载数据到ArrayTable
        new ColumnLoader<Object[]>(typeFactory, enumerable, // 传入类型工厂和可枚举数据
            RelDataTypeImpl.proto(rowType), null); // 传入行类型原型，无额外参数
    checkColumn( // 检查第0列（empid）的表示方式
        loader.representationValues.get(0), // 获取第0列的列对象
        ArrayTable.RepresentationType.BIT_SLICED_PRIMITIVE_ARRAY, // 期望的表示类型为位切片原始数组
        "Column(representation=BitSlicedPrimitiveArray(ordinal=0, bitCount=8, primitive=INT, signed=false), value=[100, 150, 160, 200, 0, 0, 0, 0])"); // 期望的字符串表示，注意数据已按empid排序
    checkColumn( // 检查第1列（deptno）的表示方式
        loader.representationValues.get(1), // 获取第1列的列对象
        ArrayTable.RepresentationType.BIT_SLICED_PRIMITIVE_ARRAY, // 期望的表示类型为位切片原始数组
        "Column(representation=BitSlicedPrimitiveArray(ordinal=1, bitCount=5, primitive=INT, signed=false), value=[10, 10, 10, 20, 0, 0, 0, 0, 0, 0, 0, 0])"); // 期望的字符串表示，注意数据已按empid排序
    checkColumn( // 检查第2列（name）的表示方式
        loader.representationValues.get(2), // 获取第2列的列对象
        ArrayTable.RepresentationType.OBJECT_ARRAY, // 期望的表示类型为对象数组
        "Column(representation=ObjectArray(ordinal=2), value=[Bill, Sebastian, Theodore, Eric])"); // 期望的字符串表示，注意数据已按empid排序
  } // 测试方法结束

  /** As {@link #testLoadSorted()} but column #1 is the unique column, not
   * column #0. The algorithm needs to go back and permute the values of
   * column #0 after it discovers that column #1 is unique and sorts by it. */
  // 类似testLoadSorted()，但第1列是唯一列，不是第0列。算法需要在发现第1列唯一并按其排序后，重新排列第0列的值。
  @Test void testLoadSorted2() { // 测试方法：测试列加载器的排序功能（唯一列在第1列）
    final JavaTypeFactoryImpl typeFactory = // 创建Java类型工厂实例
        new JavaTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用默认的关系数据类型系统
    final RelDataType rowType = // 创建行类型
        typeFactory.builder() // 获取类型构建器
            .add("deptno", typeFactory.createType(int.class)) // 添加deptno列，类型为int
            .add("empid", typeFactory.createType(int.class)) // 添加empid列，类型为int（这是唯一列）
            .add("name", typeFactory.createType(String.class)) // 添加name列，类型为String
            .build(); // 构建行类型
    final Enumerable<Object[]> enumerable = // 创建可枚举对象，包含要加载的数据
        Linq4j.asEnumerable( // 将列表转换为可枚举对象
            Arrays.asList( // 创建包含员工数据的列表
                new Object[]{10, 100, "Bill"}, // 第一条记录：deptno=10, empid=100, name="Bill"
                new Object[]{20, 200, "Eric"}, // 第二条记录：deptno=20, empid=200, name="Eric"
                new Object[]{30, 150, "Sebastian"}, // 第三条记录：deptno=30, empid=150, name="Sebastian"
                new Object[]{10, 160, "Theodore"})); // 第四条记录：deptno=10, empid=160, name="Theodore"
    final ColumnLoader<Object[]> loader = // 创建列加载器，加载数据到ArrayTable
        new ColumnLoader<Object[]>(typeFactory, enumerable, // 传入类型工厂和可枚举数据
            RelDataTypeImpl.proto(rowType), null); // 传入行类型原型，无额外参数
    // Note that values have been sorted with {20, 200, Eric} last because the
    // value 200 is the highest value of empid, the unique column.
    // 注意：值已排序，{20, 200, Eric}排在最后，因为200是empid（唯一列）的最大值
    checkColumn( // 检查第0列（deptno）的表示方式
        loader.representationValues.get(0), // 获取第0列的列对象
        ArrayTable.RepresentationType.BIT_SLICED_PRIMITIVE_ARRAY, // 期望的表示类型为位切片原始数组
        "Column(representation=BitSlicedPrimitiveArray(ordinal=0, bitCount=5, primitive=INT, signed=false), value=[10, 30, 10, 20, 0, 0, 0, 0, 0, 0, 0, 0])"); // 期望的字符串表示，注意数据已按empid排序
    checkColumn( // 检查第1列（empid）的表示方式
        loader.representationValues.get(1), // 获取第1列的列对象
        ArrayTable.RepresentationType.BIT_SLICED_PRIMITIVE_ARRAY, // 期望的表示类型为位切片原始数组
        "Column(representation=BitSlicedPrimitiveArray(ordinal=1, bitCount=8, primitive=INT, signed=false), value=[100, 150, 160, 200, 0, 0, 0, 0])"); // 期望的字符串表示，注意数据已按empid排序
    checkColumn( // 检查第2列（name）的表示方式
        loader.representationValues.get(2), // 获取第2列的列对象
        ArrayTable.RepresentationType.OBJECT_ARRAY, // 期望的表示类型为对象数组
        "Column(representation=ObjectArray(ordinal=2), value=[Bill, Sebastian, Theodore, Eric])"); // 期望的字符串表示，注意数据已按empid排序
  } // 测试方法结束

  private void checkColumn(ArrayTable.Column x, // 私有辅助方法：检查列的表示方式和字符串表示
      ArrayTable.RepresentationType expectedRepresentationType, // 参数：期望的表示类型
      String expectedString) { // 参数：期望的字符串表示
    assertThat(x.representation.getType(), is(expectedRepresentationType)); // 断言列的实际表示类型与期望类型一致
    assertThat(x, hasToString(expectedString)); // 断言列的toString结果与期望字符串一致
  } // 方法结束
} // 类结束