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
package org.apache.calcite.test.schemata.catchall; // 声明包名，CatchallSchema类位于org.apache.calcite.test.schemata.catchall包下

import org.apache.calcite.adapter.java.Array; // 导入Array注解，用于标记数组类型的字段
import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口，用于支持LINQ风格的查询操作
import org.apache.calcite.linq4j.Linq4j; // 导入Linq4j工具类，提供LINQ操作的静态方法
import org.apache.calcite.linq4j.tree.Primitive; // 导入Primitive类，用于处理基本类型及其包装类
import org.apache.calcite.test.schemata.hr.Employee; // 导入Employee类，用于表示员工记录
import org.apache.calcite.test.schemata.hr.HrSchema; // 导入HrSchema类，用于获取HR模式的数据

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可空类型

import java.lang.reflect.Field; // 导入Field类，用于反射获取类的字段信息
import java.math.BigDecimal; // 导入BigDecimal类，用于高精度十进制数值计算
import java.sql.Time; // 导入Time类，用于表示SQL时间类型
import java.sql.Timestamp; // 导入Timestamp类，用于表示SQL时间戳类型
import java.util.Arrays; // 导入Arrays工具类，提供数组操作的静态方法
import java.util.BitSet; // 导入BitSet类，用于位集合操作
import java.util.Collections; // 导入Collections工具类，提供集合操作的静态方法
import java.util.Date; // 导入Date类，用于表示日期时间
import java.util.List; // 导入List接口，用于列表操作

/**
 * Object whose fields are relations. Called "catch-all" because it's OK
 * if tests add new fields.
 * // CatchallSchema是一个测试用的Schema类，其字段都是关系（表），称为"catch-all"是因为测试可以随意添加新字段
 * // 这个类主要用于测试Calcite对各种不同类型数据源的支持能力，包括数组、集合、基本类型、包装类型等
 * // 它提供了多种不同类型的数据表，用于测试Calcite的类型系统、查询优化器和适配器功能
 */
@SuppressWarnings("UnusedVariable") // 抑制未使用变量的警告，因为这些字段主要用于反射访问和测试
public class CatchallSchema { // CatchallSchema类定义，这是一个测试用的Schema，包含多种类型的测试数据
  public final Enumerable<Employee> enumerable = // 定义一个枚举器类型的字段，存储Employee对象的枚举集合
      Linq4j.asEnumerable( // 将数组转换为LINQ可枚举的集合
          Arrays.asList(new HrSchema().emps)); // 创建HrSchema实例并获取其emps数组，转换为List后再转换为Enumerable

  public final List<Employee> list = // 定义一个List类型的字段，存储Employee对象的列表
      Arrays.asList(new HrSchema().emps); // 创建HrSchema实例并获取其emps数组，转换为List

  public final BitSet bitSet = new BitSet(1); // 定义一个BitSet类型的字段，用于测试BitSet类型的支持，初始化大小为1

  @SuppressWarnings("JavaUtilDate") // 抑制使用java.util.Date的警告
  public final EveryType[] everyTypes = { // 定义EveryType数组，用于测试包含所有可能类型的记录
      new EveryType( // 创建第一个EveryType实例，所有字段使用默认值或最小值
          false, (byte) 0, (char) 0, (short) 0, 0, 0L, 0F, 0D, // 基本类型的默认值：boolean=false, byte=0, char=0, short=0, int=0, long=0, float=0, double=0
          false, (byte) 0, (char) 0, (short) 0, 0, 0L, 0F, 0D, // 包装类型的默认值：Boolean=false, Byte=0, Character=0, Short=0, Integer=0, Long=0, Float=0, Double=0
          new java.sql.Date(0), new Time(0), new Timestamp(0), // 日期时间类型：sql.Date=0, Time=0, Timestamp=0
          new Date(0), "1", BigDecimal.ZERO, Collections.emptyList()), // util.Date=0, String="1", BigDecimal=0, List=空列表
      new EveryType( // 创建第二个EveryType实例，所有字段使用最大值或null值
          true, Byte.MAX_VALUE, Character.MAX_VALUE, Short.MAX_VALUE, // 基本类型的最大值：boolean=true, byte=127, char=65535, short=32767
          Integer.MAX_VALUE, Long.MAX_VALUE, Float.MAX_VALUE, // 基本类型的最大值：int=2147483647, long=9223372036854775807, float=3.4028235E38
          Double.MAX_VALUE, // 基本类型的最大值：double=1.7976931348623157E308
          null, null, null, null, null, null, null, null, // 包装类型全部为null：Boolean=null, Byte=null, Character=null, Short=null, Integer=null, Long=null, Float=null, Double=null
          null, null, null, null, null, null, null), // 日期时间和其他类型全部为null：sql.Date=null, Time=null, Timestamp=null, util.Date=null, String=null, BigDecimal=null, List=null
  };

  public final AllPrivate[] allPrivates = // 定义AllPrivate数组，用于测试所有字段都是私有的情况
      {new AllPrivate()}; // 创建AllPrivate实例，其所有字段都是私有的，因此生成的记录将没有可访问的字段

  public final BadType[] badTypes = {new BadType()}; // 定义BadType数组，用于测试包含无法识别为SQL类型的字段的情况

  public final Employee[] prefixEmps = { // 定义Employee数组，用于测试字符串前缀匹配功能
      new Employee(1, 10, "A", 0f, null), // 创建Employee实例：id=1, deptno=10, name="A", salary=0f, commission=null
      new Employee(2, 10, "Ab", 0f, null), // 创建Employee实例：id=2, deptno=10, name="Ab", salary=0f, commission=null
      new Employee(3, 10, "Abc", 0f, null), // 创建Employee实例：id=3, deptno=10, name="Abc", salary=0f, commission=null
      new Employee(4, 10, "Abd", 0f, null), // 创建Employee实例：id=4, deptno=10, name="Abd", salary=0f, commission=null
  };

  public final Integer[] primesBoxed = {1, 3, 5}; // 定义Integer包装类型数组，用于测试基本类型的包装类型支持

  public final int[] primes = {1, 3, 5}; // 定义int基本类型数组，用于测试基本类型的支持

  public final IntHolder[] primesCustomBoxed = // 定义IntHolder数组，用于测试自定义包装类型
      {new IntHolder(1), new IntHolder(3), // 创建IntHolder实例：value=1和value=3
          new IntHolder(5)}; // 创建IntHolder实例：value=5

  public final IntAndString[] nullables = { // 定义IntAndString数组，用于测试包含null值的字段
      new IntAndString(1, "A"), new IntAndString(2, // 创建IntAndString实例：id=1, value="A"和id=2, value="B"
      "B"), new IntAndString(2, "C"), // 创建IntAndString实例：id=2, value="C"
      new IntAndString(3, null)}; // 创建IntAndString实例：id=3, value=null（测试null值）

  public final IntAndString[] bools = { // 定义IntAndString数组，用于测试布尔值的字符串表示
      new IntAndString(1, "T"), new IntAndString(2, // 创建IntAndString实例：id=1, value="T"(true)和id=2, value="F"(false)
      "F"), new IntAndString(3, null)}; // 创建IntAndString实例：id=3, value=null

  private static boolean isNumeric(Class type) { // 静态方法：判断给定的类型是否为数值类型
    switch (Primitive.flavor(type)) { // 获取类型的类型（基本类型、包装类型或其他）
    case BOX: // 如果是包装类型
      return Primitive.ofBox(type).isNumeric(); // 返回该包装类型对应的基本类型是否为数值类型
    case PRIMITIVE: // 如果是基本类型
      return Primitive.of(type).isNumeric(); // 返回该基本类型是否为数值类型
    default: // 其他情况（如BigDecimal）
      return Number.class.isAssignableFrom(type); // 检查类型是否为Number的子类（例如BigDecimal是Number的子类，属于数值类型）
    }
  }

  /** Record that has a field of every interesting type. */
  // EveryType是一个内部静态类，包含所有可能的SQL类型字段，用于测试Calcite对各种类型的支持
  public static class EveryType { // EveryType类定义，包含所有可能的SQL类型字段
    public final boolean primitiveBoolean; // 基本类型boolean字段，用于测试布尔类型
    public final byte primitiveByte; // 基本类型byte字段，用于测试字节类型
    public final char primitiveChar; // 基本类型char字段，用于测试字符类型
    public final short primitiveShort; // 基本类型short字段，用于测试短整型
    public final int primitiveInt; // 基本类型int字段，用于测试整型
    public final long primitiveLong; // 基本类型long字段，用于测试长整型
    public final float primitiveFloat; // 基本类型float字段，用于测试单精度浮点型
    public final double primitiveDouble; // 基本类型double字段，用于测试双精度浮点型
    public final @Nullable Boolean wrapperBoolean; // 包装类型Boolean字段，可为null，用于测试布尔包装类型
    public final @Nullable Byte wrapperByte; // 包装类型Byte字段，可为null，用于测试字节包装类型
    public final @Nullable Character wrapperCharacter; // 包装类型Character字段，可为null，用于测试字符包装类型
    public final @Nullable Short wrapperShort; // 包装类型Short字段，可为null，用于测试短整型包装类型
    public final @Nullable Integer wrapperInteger; // 包装类型Integer字段，可为null，用于测试整型包装类型
    public final @Nullable Long wrapperLong; // 包装类型Long字段，可为null，用于测试长整型包装类型
    public final @Nullable Float wrapperFloat; // 包装类型Float字段，可为null，用于测试单精度浮点型包装类型
    public final @Nullable Double wrapperDouble; // 包装类型Double字段，可为null，用于测试双精度浮点型包装类型
    public final java.sql.@Nullable Date sqlDate; // SQL Date类型字段，可为null，用于测试SQL日期类型
    public final @Nullable Time sqlTime; // SQL Time类型字段，可为null，用于测试SQL时间类型
    public final @Nullable Timestamp sqlTimestamp; // SQL Timestamp类型字段，可为null，用于测试SQL时间戳类型
    public final @Nullable Date utilDate; // util Date类型字段，可为null，用于测试Java util日期类型
    public final @Nullable String string; // String类型字段，可为null，用于测试字符串类型
    public final @Nullable BigDecimal bigDecimal; // BigDecimal类型字段，可为null，用于测试高精度十进制类型
    public final @Nullable @Array(component = String.class) List<String> list; // List<String>类型字段，可为null，使用@Array注解标记为数组类型

    public EveryType( // EveryType构造方法，初始化所有字段
        boolean primitiveBoolean, // 基本类型boolean参数
        byte primitiveByte, // 基本类型byte参数
        char primitiveChar, // 基本类型char参数
        short primitiveShort, // 基本类型short参数
        int primitiveInt, // 基本类型int参数
        long primitiveLong, // 基本类型long参数
        float primitiveFloat, // 基本类型float参数
        double primitiveDouble, // 基本类型double参数
        @Nullable Boolean wrapperBoolean, // 包装类型Boolean参数，可为null
        @Nullable Byte wrapperByte, // 包装类型Byte参数，可为null
        @Nullable Character wrapperCharacter, // 包装类型Character参数，可为null
        @Nullable Short wrapperShort, // 包装类型Short参数，可为null
        @Nullable Integer wrapperInteger, // 包装类型Integer参数，可为null
        @Nullable Long wrapperLong, // 包装类型Long参数，可为null
        @Nullable Float wrapperFloat, // 包装类型Float参数，可为null
        @Nullable Double wrapperDouble, // 包装类型Double参数，可为null
        java.sql.@Nullable Date sqlDate, // SQL Date类型参数，可为null
        @Nullable Time sqlTime, // SQL Time类型参数，可为null
        @Nullable Timestamp sqlTimestamp, // SQL Timestamp类型参数，可为null
        @Nullable Date utilDate, // util Date类型参数，可为null
        @Nullable String string, // String类型参数，可为null
        @Nullable BigDecimal bigDecimal, // BigDecimal类型参数，可为null
        @Nullable List<String> list) { // List<String>类型参数，可为null
      this.primitiveBoolean = primitiveBoolean; // 初始化primitiveBoolean字段
      this.primitiveByte = primitiveByte; // 初始化primitiveByte字段
      this.primitiveChar = primitiveChar; // 初始化primitiveChar字段
      this.primitiveShort = primitiveShort; // 初始化primitiveShort字段
      this.primitiveInt = primitiveInt; // 初始化primitiveInt字段
      this.primitiveLong = primitiveLong; // 初始化primitiveLong字段
      this.primitiveFloat = primitiveFloat; // 初始化primitiveFloat字段
      this.primitiveDouble = primitiveDouble; // 初始化primitiveDouble字段
      this.wrapperBoolean = wrapperBoolean; // 初始化wrapperBoolean字段
      this.wrapperByte = wrapperByte; // 初始化wrapperByte字段
      this.wrapperCharacter = wrapperCharacter; // 初始化wrapperCharacter字段
      this.wrapperShort = wrapperShort; // 初始化wrapperShort字段
      this.wrapperInteger = wrapperInteger; // 初始化wrapperInteger字段
      this.wrapperLong = wrapperLong; // 初始化wrapperLong字段
      this.wrapperFloat = wrapperFloat; // 初始化wrapperFloat字段
      this.wrapperDouble = wrapperDouble; // 初始化wrapperDouble字段
      this.sqlDate = sqlDate; // 初始化sqlDate字段
      this.sqlTime = sqlTime; // 初始化sqlTime字段
      this.sqlTimestamp = sqlTimestamp; // 初始化sqlTimestamp字段
      this.utilDate = utilDate; // 初始化utilDate字段
      this.string = string; // 初始化string字段
      this.bigDecimal = bigDecimal; // 初始化bigDecimal字段
      this.list = list; // 初始化list字段
    }

    public static Enumerable<Field> fields() { // 静态方法：获取EveryType类的所有公共字段
      return Linq4j.asEnumerable(EveryType.class.getFields()); // 使用反射获取所有公共字段，并转换为可枚举的集合
    }

    public static Enumerable<Field> numericFields() { // 静态方法：获取EveryType类的所有数值类型字段
      return fields() // 获取所有字段
          .where(v1 -> isNumeric(v1.getType())); // 过滤出类型为数值类型的字段
    }
  }

  /** All field are private, therefore the resulting record has no fields. */
  // AllPrivate是一个内部静态类，所有字段都是私有的，用于测试Calcite对私有字段的处理（生成的记录将没有可访问的字段）
  public static class AllPrivate { // AllPrivate类定义，所有字段都是私有的
    private final int x = 0; // 私有字段x，初始化为0，因为字段是私有的，所以生成的记录将没有可访问的字段
  }

  /** Table that has a field that cannot be recognized as a SQL type. */
  // BadType是一个内部静态类，包含无法识别为SQL类型的字段（BitSet），用于测试Calcite对未知类型的处理
  public static class BadType { // BadType类定义，包含无法识别为SQL类型的字段
    public final int integer = 0; // 公共字段integer，初始化为0，这是一个正常的SQL类型
    public final BitSet bitSet = new BitSet(0); // 公共字段bitSet，初始化为空的BitSet，BitSet类型无法被识别为SQL类型
  }

  /** Table that has integer and string fields. */
  // IntAndString是一个内部静态类，包含整数和字符串字段，用于测试Calcite对基本类型和字符串类型的支持
  public static class IntAndString { // IntAndString类定义，包含整数和字符串字段
    public final int id; // 公共字段id，表示记录的标识符
    public final @Nullable String value; // 公共字段value，可为null，表示字符串值

    public IntAndString(int id, @Nullable String value) { // IntAndString构造方法，初始化所有字段
      this.id = id; // 初始化id字段
      this.value = value; // 初始化value字段
    }
  }

  /**
   * Custom java class that holds just a single field.
   */
  // IntHolder是一个内部静态类，是一个自定义的Java类，只包含一个字段，用于测试Calcite对自定义包装类型的支持
  public static class IntHolder { // IntHolder类定义，只包含一个字段
    public final int value; // 公共字段value，表示整数值

    public IntHolder(int value) { // IntHolder构造方法，初始化value字段
      this.value = value; // 初始化value字段
    }
  }
}
