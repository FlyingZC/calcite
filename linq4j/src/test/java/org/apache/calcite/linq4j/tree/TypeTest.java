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
// 声明包名，表示该类属于org.apache.calcite.linq4j.tree包，这是Calcite框架中LINQ4J模块的树形结构包
package org.apache.calcite.linq4j.tree;

// 导入JUnit 5的Test注解，用于标记测试方法
import org.junit.jupiter.api.Test;

// 导入静态方法，用于断言测试结果，is匹配器用于判断值是否相等
import static org.hamcrest.CoreMatchers.is;
// 导入静态方法，用于执行断言操作，assertThat是Hamcrest框架的核心断言方法
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test for {@link Types#gcd}.
 * 这是一个测试类，用于测试Types类的gcd方法的功能
 * gcd方法用于计算两个类型的最大公共类型（Greatest Common Denominator，最大公约数类型）
 * 最大公共类型是指能够同时容纳两个类型值的类型，类似于Java的类型提升规则
 * 例如：int和long的最大公共类型是long，因为long可以容纳int的值
 */
class TypeTest {
  // 测试方法，使用@Test注解标记，JUnit会自动执行该方法
  @Test void testGcd() {
    // 声明int类型变量i并初始化为0，用于测试int类型的赋值和类型转换
    int i = 0;
    // 声明char类型变量c并初始化为0，用于测试char类型的赋值和类型转换
    char c = 0;
    // 声明byte类型变量b并初始化为0，用于测试byte类型的赋值和类型转换
    byte b = 0;
    // 声明short类型变量s并初始化为0，用于测试short类型的赋值和类型转换
    short s = 0;
    // 声明int类型变量l并初始化为0，虽然变量名是l（通常用于long），但这里声明为int，用于测试类型转换
    int l = 0;

    // 注释说明：测试int到long的转换
    // 将int类型的i赋值给int类型的l，验证基本类型赋值操作
    l = i;
    // 断言：验证Types.gcd(int.class, long.class)的返回值是long.class
    // 这表示int和long的最大公共类型是long，因为long可以容纳int的所有值
    // gcd方法模拟了Java的类型提升规则，int和long运算时会提升为long
    assertThat(Types.gcd(int.class, long.class), is(long.class));

    // 注释说明：测试反向参数的情况
    // 断言：验证Types.gcd(long.class, int.class)的返回值是long.class
    // gcd方法应该是对称的，参数顺序不影响结果，仍然是long
    assertThat(Types.gcd(long.class, int.class), is(long.class));

    // 注释说明：测试char到int的转换
    // 将char类型的c赋值给int类型的i，验证char可以隐式转换为int
    l = i;
    // 断言：验证Types.gcd(char.class, int.class)的返回值是int.class
    // char和int的最大公共类型是int，因为int可以容纳char的所有值（0-65535）
    assertThat(Types.gcd(char.class, int.class), is(int.class));

    // 注释说明：测试byte可以赋值给short
    // 断言：验证Types.gcd(byte.class, short.class)的返回值是short.class
    // byte和short的最大公共类型是short，因为short可以容纳byte的所有值（-128到127）
    assertThat(Types.gcd(byte.class, short.class), is(short.class));
    // 将byte类型的b赋值给short类型的s，验证byte可以隐式转换为short
    s = b;

    // 注释说明：以下注释解释了类型转换的限制
    // 注释：不能将byte赋值给char（因为char是无符号的，byte是有符号的，范围不兼容）
    // 注释：不能将char赋值给short（因为char是无符号的0-65535，short是有符号的-32768到32767）
    // 注释：可以将byte和char都赋值给int（int可以同时容纳两者的值范围）
    // 注释：失败的转换：c = b; （byte不能直接赋值给char）
    // 注释：失败的转换：s = c; （char不能直接赋值给short）
    // 将byte类型的b赋值给int类型的i，验证byte可以隐式转换为int
    i = b;
    // 将char类型的c赋值给int类型的i，验证char可以隐式转换为int
    i = c;
    // 断言：验证Types.gcd(char.class, byte.class)的返回值是int.class
    // char和byte的最大公共类型是int，因为两者之间没有直接的兼容关系，都需要提升到int
    // byte是-128到127，char是0到65535，只有int可以同时容纳两者的值范围
    assertThat(Types.gcd(char.class, byte.class), is(int.class));

    // 断言：验证Types.gcd(byte.class, char.class)的返回值是int.class
    // 同样，byte和char的最大公共类型是int，验证gcd方法的对称性
    assertThat(Types.gcd(byte.class, char.class), is(int.class));

    // 注释说明：测试基本类型和对象类型的混合
    // 注释：正确的答案应该是java.io.Serializable（因为String和int都可以转换为Serializable）
    // 但实际上gcd方法返回Object.class，因为Object是所有类型的最终父类
    // 断言：验证Types.gcd(String.class, int.class)的返回值是Object.class
    // String是引用类型，int是基本类型，它们之间的最大公共类型是Object
    // 这是因为基本类型和引用类型之间没有直接的继承关系，只能提升到Object
    assertThat(Types.gcd(String.class, int.class), is(Object.class));
    // 声明一个Serializable类型的变量o，通过三元运算符赋值
    // 如果条件为true，赋值为字符串"x"；如果为false，赋值为整数1
    // 这个赋值是合法的，因为String和Integer都实现了Serializable接口
    // 这验证了在Java中，String和基本类型（通过自动装箱）都可以赋值给Serializable
    java.io.Serializable o = true ? "x" : 1;
  }
}
