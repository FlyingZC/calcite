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
package org.apache.calcite.linq4j.tree;

import org.apiguardian.api.API;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Enumeration of Java's primitive types.
 *
 * <p>There are fields for the native class (e.g. <code>int</code>, also
 * known as {@link Integer#TYPE}) and the boxing class
 * (e.g. {@link Integer}).
 * // Java基本类型的枚举类，用于表示和操作Java的8种基本类型（boolean、byte、char、short、int、long、float、double）以及void和其他类型
 * // 该类提供了基本类型与其包装类型之间的转换、类型判断、数组操作、数值转换、JDBC操作等功能
 * // 是Calcite LINQ4J框架中处理类型系统的核心类，支持类型安全的数据操作和转换
 */
public enum Primitive {
  // BOOLEAN: 布尔类型，对应boolean和Boolean，默认值false，无大小限制，family为1表示布尔类型族
  BOOLEAN(Boolean.TYPE, Boolean.class, 1, false, false, null, null, true, -1),
  // BYTE: 字节类型，对应byte和Byte，默认值0，范围-128到127，大小8位，family为2表示整数类型族
  BYTE(Byte.TYPE, Byte.class, 2, (byte) 0, Byte.MIN_VALUE, null, null,
      Byte.MAX_VALUE, Byte.SIZE),
  // CHAR: 字符类型，对应char和Character，默认值'\0'，范围0到65535，大小16位，family为2表示整数类型族
  CHAR(Character.TYPE, Character.class, 2, (char) 0, Character.MIN_VALUE,
      null, null, Character.MAX_VALUE, Character.SIZE),
  // SHORT: 短整型，对应short和Short，默认值0，范围-32768到32767，大小16位，family为2表示整数类型族
  SHORT(Short.TYPE, Short.class, 2, (short) 0, Short.MIN_VALUE, null, null,
      Short.MAX_VALUE, Short.SIZE),
  // INT: 整型，对应int和Integer，默认值0，范围-2^31到2^31-1，大小32位，family为2表示整数类型族
  INT(Integer.TYPE, Integer.class, 2, 0, Integer.MIN_VALUE, null, null,
      Integer.MAX_VALUE, Integer.SIZE),
  // LONG: 长整型，对应long和Long，默认值0L，范围-2^63到2^63-1，大小64位，family为2表示整数类型族
  LONG(Long.TYPE, Long.class, 2, 0L, Long.MIN_VALUE, null, null,
      Long.MAX_VALUE, Long.SIZE),
  // FLOAT: 单精度浮点数，对应float和Float，默认值0.0F，范围±3.4E38，有效精度约7位，大小32位，family为2表示数值类型族
  FLOAT(Float.TYPE, Float.class, 2, 0F, -Float.MAX_VALUE, -Float.MIN_VALUE,
      Float.MIN_VALUE, Float.MAX_VALUE, Float.SIZE),
  // DOUBLE: 双精度浮点数，对应double和Double，默认值0.0D，范围±1.8E308，有效精度约15位，大小64位，family为2表示数值类型族
  DOUBLE(Double.TYPE, Double.class, 2, 0D, -Double.MAX_VALUE, -Double.MIN_VALUE,
      Double.MIN_VALUE, Double.MAX_VALUE, Double.SIZE),
  // VOID: 空类型，对应void和Void，无默认值和范围，family为3表示空类型族
  VOID(Void.TYPE, Void.class, 3, null, null, null, null, null, -1),
  // OTHER: 其他类型（非基本类型），无对应的原始类型和包装类型，family为4表示其他类型族
  OTHER(null, null, 4, null, null, null, null, null, -1);

  // primitiveClass: 原始类型Class对象，如int.class、boolean.class等，OTHER类型为null
  public final @Nullable Class primitiveClass;
  // boxClass: 包装类型Class对象，如Integer.class、Boolean.class等，OTHER类型为null
  public final @Nullable Class boxClass;
  // primitiveName: 原始类型名称，如"int"、"boolean"等，OTHER类型为null
  public final @Nullable String primitiveName; // e.g. "int"
  // boxName: 包装类型名称，如"Integer"、"Boolean"等，OTHER类型为null
  public final @Nullable String boxName;
  // family: 类型族标识，1=布尔族，2=数值族（整数和浮点数），3=空类型族，4=其他类型族，用于类型兼容性判断
  private final int family;

  /** The default value of this primitive class. This is the value
   * taken by uninitialized fields, for instance; 0 for {@code int}, false for
   * {@code boolean}, etc. */
  // defaultValue: 该原始类型的默认值，如int的0、boolean的false等，用于未初始化字段的默认值，VOID和OTHER类型为null
  @SuppressWarnings("ImmutableEnumChecker")
  public final @Nullable Object defaultValue;

  /** The minimum value of this primitive class. */
  // min: 该原始类型的最小值，如Integer.MIN_VALUE、Byte.MIN_VALUE等，非数值类型为null
  @SuppressWarnings("ImmutableEnumChecker")
  public final @Nullable Object min;

  /** The largest value that is less than zero. Null if not applicable for this
   * type. */
  // maxNegative: 小于零的最大值（绝对值最小的负数），用于浮点数类型，整数和布尔类型为null
  @SuppressWarnings("ImmutableEnumChecker")
  public final @Nullable Object maxNegative;

  /** The smallest value that is greater than zero. Null if not applicable for
   * this type. */
  // minPositive: 大于零的最小值（绝对值最小的正数），用于浮点数类型，整数和布尔类型为null
  @SuppressWarnings("ImmutableEnumChecker")
  public final @Nullable Object minPositive;

  /** The maximum value of this primitive class. */
  // max: 该原始类型的最大值，如Integer.MAX_VALUE、Byte.MAX_VALUE等，非数值类型为null
  @SuppressWarnings("ImmutableEnumChecker")
  public final @Nullable Object max;

  /** The size of a value of this type, in bits. Null if not applicable for this
   * type. */
  // size: 该类型的位数，如Integer.SIZE=32、Byte.SIZE=8等，VOID和OTHER类型为-1
  public final int size;

  // PRIMITIVE_MAP: 原始类型Class到Primitive枚举的映射表，用于快速查找原始类型对应的枚举值，如int.class->INT
  private static final Map<Class, Primitive> PRIMITIVE_MAP = new HashMap<>();
  // BOX_MAP: 包装类型Class到Primitive枚举的映射表，用于快速查找包装类型对应的枚举值，如Integer.class->INT
  private static final Map<Class, Primitive> BOX_MAP = new HashMap<>();

  // 静态初始化块：构建PRIMITIVE_MAP和BOX_MAP两个映射表，方便后续快速查找
  static {
    Primitive[] values = Primitive.values(); // 获取所有Primitive枚举值
    for (Primitive value : values) { // 遍历所有枚举值
      if (value.primitiveClass != null) { // 如果原始类型不为空（排除OTHER）
        PRIMITIVE_MAP.put(value.primitiveClass, value); // 建立原始类型到枚举的映射
      }
      if (value.boxClass != null) { // 如果包装类型不为空（排除OTHER）
        BOX_MAP.put(value.boxClass, value); // 建立包装类型到枚举的映射
      }
    }
  }

  // 构造方法：初始化Primitive枚举实例，设置原始类型、包装类型、类型族、默认值、范围和大小等属性
  Primitive(@Nullable Class primitiveClass, @Nullable Class boxClass, int family,
      @Nullable Object defaultValue, @Nullable Object min, @Nullable Object maxNegative,
      @Nullable Object minPositive, @Nullable Object max, int size) {
    this.primitiveClass = primitiveClass; // 设置原始类型Class对象
    this.family = family; // 设置类型族标识
    this.primitiveName =
        primitiveClass != null ? primitiveClass.getSimpleName() : null; // 从原始类型Class获取简单名称，如"int"
    this.boxClass = boxClass; // 设置包装类型Class对象
    this.boxName = boxClass != null ? boxClass.getSimpleName() : null; // 从包装类型Class获取简单名称，如"Integer"
    this.defaultValue = defaultValue; // 设置默认值
    this.min = min; // 设置最小值
    this.maxNegative = maxNegative; // 设置绝对值最小的负数
    this.minPositive = minPositive; // 设置绝对值最小的正数
    this.max = max; // 设置最大值
    this.size = size; // 设置位数
  }

  /**
   * Returns the Primitive object for a given primitive class.
   *
   * <p>For example, <code>of(long.class)</code> returns {@link #LONG}.
   * Returns {@code null} when applied to a boxing or other class; for example
   * <code>of(Long.class)</code> and <code>of(String.class)</code> return
   * {@code null}.
   */
  // 根据原始类型Class获取对应的Primitive枚举值，例如of(long.class)返回LONG
  // 如果传入的是包装类型或其他类型（如Long.class、String.class），则返回null
  public static @Nullable Primitive of(Type type) {
    //noinspection SuspiciousMethodCalls
    return PRIMITIVE_MAP.get(type); // 从PRIMITIVE_MAP映射表中查找对应的枚举值
  }

  /**
   * Returns the Primitive object for a given boxing class.
   *
   * <p>For example, <code>ofBox(java.util.Long.class)</code>
   * returns {@link #LONG}.
   */
  // 根据包装类型Class获取对应的Primitive枚举值，例如ofBox(Long.class)返回LONG
  public static @Nullable Primitive ofBox(Type type) {
    //noinspection SuspiciousMethodCalls
    return BOX_MAP.get(type); // 从BOX_MAP映射表中查找对应的枚举值
  }

  /**
   * Returns the Primitive object for a given primitive or boxing class.
   *
   * <p>For example, <code>ofBoxOr(Long.class)</code> and
   * <code>ofBoxOr(long.class)</code> both return {@link #LONG}.
   */
  // 根据原始类型或包装类型Class获取对应的Primitive枚举值
  // 例如ofBoxOr(Long.class)和ofBoxOr(long.class)都返回LONG
  public static @Nullable Primitive ofBoxOr(Type type) {
    Primitive primitive = of(type); // 先尝试作为原始类型查找
    if (primitive == null) { // 如果找不到
      primitive = ofBox(type); // 再尝试作为包装类型查找
    }
    return primitive; // 返回找到的枚举值，找不到则返回null
  }

  /**
   * Returns whether a given type is primitive.
   */
  // 判断给定的类型是否是原始类型（如int.class、boolean.class等）
  public static boolean is(Type type) {
    //noinspection SuspiciousMethodCalls
    return PRIMITIVE_MAP.containsKey(type); // 检查PRIMITIVE_MAP中是否包含该类型
  }

  /**
   * Returns whether a given type is a box type (e.g. {@link Integer}).
   */
  // 判断给定的类型是否是包装类型（如Integer.class、Boolean.class等）
  public static boolean isBox(Type type) {
    //noinspection SuspiciousMethodCalls
    return BOX_MAP.containsKey(type); // 检查BOX_MAP中是否包含该类型
  }

  /** Returns whether this type is a primitive, box or other type. Useful for
   * switch statements. */
  // 返回给定类型的风格（Flavor），用于switch语句判断类型是原始类型、包装类型还是其他类型
  public static Flavor flavor(Type type) {
    if (is(type)) { // 如果是原始类型
      return Flavor.PRIMITIVE; // 返回PRIMITIVE风格
    } else if (isBox(type)) { // 如果是包装类型
      return Flavor.BOX; // 返回BOX风格
    } else { // 其他情况
      return Flavor.OBJECT; // 返回OBJECT风格
    }
  }

  /** Returns whether this Primitive is a numeric type. */
  // 判断该Primitive是否是数值类型（byte、short、int、long、float、double）
  // 根据Java规范，Boolean和Character不继承Number，所以不算数值类型
  public boolean isNumeric() {
    // Per Java: Boolean and Character do not extend Number
    switch (this) {
    case BYTE:
    case SHORT:
    case INT:
    case LONG:
    case FLOAT:
    case DOUBLE:
      return true; // 这6种类型是数值类型
    default:
      return false; // 其他类型（BOOLEAN、CHAR、VOID、OTHER）不是数值类型
    }
  }

  /** Returns whether this Primitive is a fixed-point numeric type. */
  // 判断该Primitive是否是定点数值类型（byte、short、int、long）
  // 定点数是指没有小数部分的整数类型
  public boolean isFixedNumeric() {
    switch (this) {
    case BYTE:
    case SHORT:
    case INT:
    case LONG:
      return true; // 这4种类型是定点数值类型
    default:
      return false; // 其他类型（FLOAT、DOUBLE等）不是定点数值类型
    }
  }

  /**
   * Converts a primitive type to a boxed type; returns other types
   * unchanged.
   */
  // 将原始类型转换为包装类型，其他类型保持不变
  // 例如：box(int.class)返回Integer.class，box(String.class)返回String.class
  public static Type box(Type type) {
    Primitive primitive = of(type); // 尝试获取对应的Primitive枚举
    return primitive == null ? type : requireNonNull(primitive.boxClass); // 如果找不到则返回原类型，否则返回包装类型
  }

  /**
   * Converts a primitive class to a boxed class; returns other classes
   * unchanged.
   */
  // 将原始类型Class转换为包装类型Class，其他类型Class保持不变
  // 例如：box(int.class)返回Integer.class，box(String.class)返回String.class
  public static Class box(Class type) {
    Primitive primitive = of(type); // 尝试获取对应的Primitive枚举
    return primitive == null ? type : requireNonNull(primitive.boxClass); // 如果找不到则返回原类型，否则返回包装类型
  }

  /**
   * Converts a boxed type to a primitive type; returns other types
   * unchanged.
   */
  // 将包装类型转换为原始类型，其他类型保持不变
  // 例如：unbox(Integer.class)返回int.class，unbox(String.class)返回String.class
  public static Type unbox(Type type) {
    Primitive primitive = ofBox(type); // 尝试获取对应的Primitive枚举
    return primitive == null ? type : requireNonNull(primitive.primitiveClass); // 如果找不到则返回原类型，否则返回原始类型
  }

  /**
   * Converts a boxed class to a primitive class; returns other classes
   * unchanged.
   */
  // 将包装类型Class转换为原始类型Class，其他类型Class保持不变
  // 例如：unbox(Integer.class)返回int.class，unbox(String.class)返回String.class
  public static Class unbox(Class type) {
    Primitive primitive = ofBox(type); // 尝试获取对应的Primitive枚举
    return primitive == null ? type : requireNonNull(primitive.primitiveClass); // 如果找不到则返回原类型，否则返回原始类型
  }


  @API(since = "1.27", status = API.Status.EXPERIMENTAL)
  // 获取原始类型Class对象，如果不存在则抛出异常
  public Class<?> getPrimitiveClass() {
    return requireNonNull(primitiveClass, () -> "no primitiveClass for " + this); // 返回primitiveClass，如果为null则抛出异常
  }

  @API(since = "1.27", status = API.Status.EXPERIMENTAL)
  // 获取包装类型Class对象，如果不存在则抛出异常
  public Class<?> getBoxClass() {
    return requireNonNull(boxClass, () -> "no boxClass for " + this); // 返回boxClass，如果为null则抛出异常
  }

  @API(since = "1.27", status = API.Status.EXPERIMENTAL)
  // 获取原始类型名称，如果不存在则抛出异常
  public String getPrimitiveName() {
    return requireNonNull(primitiveName, () -> "no primitiveName for " + this); // 返回primitiveName，如果为null则抛出异常
  }

  @API(since = "1.27", status = API.Status.EXPERIMENTAL)
  // 获取包装类型名称，如果不存在则抛出异常
  public String getBoxName() {
    return requireNonNull(boxName, () -> "no boxName for " + this); // 返回boxName，如果为null则抛出异常
  }

  /**
   * Adapts a primitive array into a {@link List}. For example,
   * {@code asList(new double[2])} returns a {@code List<Double>}.
   */
  // 将原始类型数组适配为List视图，例如asList(new double[2])返回List<Double>
  // 使用AbstractList实现，通过反射Array.get和Array.getLength来访问数组元素
  public static List<?> asList(final Object array) {
    // REVIEW: A per-type list might be more efficient. (Or might not.)
    return new AbstractList() { // 创建一个匿名AbstractList子类
      @Override public Object get(int index) {
        return Array.get(array, index); // 使用反射获取数组指定索引的元素
      }

      @Override public int size() {
        return Array.getLength(array); // 使用反射获取数组长度
      }
    };
  }

  /**
   * Adapts an array of {@code boolean} into a {@link List} of
   * {@link Boolean}.
   */
  // 将boolean数组适配为List<Boolean>
  public static List<Boolean> asList(boolean[] elements) {
    //noinspection unchecked
    return (List<Boolean>) asList((Object) elements); // 调用通用asList方法并强转类型
  }

  /**
   * Adapts an array of {@code byte} into a {@link List} of
   * {@link Byte}.
   */
  // 将byte数组适配为List<Byte>
  public static List<Byte> asList(byte[] elements) {
    //noinspection unchecked
    return (List<Byte>) asList((Object) elements); // 调用通用asList方法并强转类型
  }

  /**
   * Adapts an array of {@code char} into a {@link List} of
   * {@link Character}.
   */
  // 将char数组适配为List<Character>
  public static List<Character> asList(char[] elements) {
    //noinspection unchecked
    return (List<Character>) asList((Object) elements); // 调用通用asList方法并强转类型
  }

  /**
   * Adapts an array of {@code short} into a {@link List} of
   * {@link Short}.
   */
  // 将short数组适配为List<Short>
  public static List<Short> asList(short[] elements) {
    //noinspection unchecked
    return (List<Short>) asList((Object) elements); // 调用通用asList方法并强转类型
  }

  /**
   * Adapts an array of {@code int} into a {@link List} of
   * {@link Integer}.
   */
  // 将int数组适配为List<Integer>
  public static List<Integer> asList(int[] elements) {
    //noinspection unchecked
    return (List<Integer>) asList((Object) elements); // 调用通用asList方法并强转类型
  }

  /**
   * Adapts an array of {@code long} into a {@link List} of
   * {@link Long}.
   */
  // 将long数组适配为List<Long>
  public static List<Long> asList(long[] elements) {
    //noinspection unchecked
    return (List<Long>) asList((Object) elements); // 调用通用asList方法并强转类型
  }

  /**
   * Adapts an array of {@code float} into a {@link List} of
   * {@link Float}.
   */
  // 将float数组适配为List<Float>
  public static List<Float> asList(float[] elements) {
    //noinspection unchecked
    return (List<Float>) asList((Object) elements); // 调用通用asList方法并强转类型
  }

  /**
   * Adapts an array of {@code double} into a {@link List} of
   * {@link Double}.
   */
  // 将double数组适配为List<Double>
  public static List<Double> asList(double[] elements) {
    //noinspection unchecked
    return (List<Double>) asList((Object) elements); // 调用通用asList方法并强转类型
  }

  /**
   * Check if a value after rounding falls within a specified range.
   *
   * @param value  Value to compare.
   * @param min    Minimum value allowed.
   * @param max    Maximum value allowed.
   */
  // 检查四舍五入后的值是否在指定范围内，用于数值类型转换时的溢出检查
  // 使用DOWN舍入模式（正数向下取整，负数向上取整），相当于BigDecimal的DOWN舍入
  static void checkRoundedRange(Number value, double min, double max) {
    double dbl = value.doubleValue(); // 将值转换为double
    // The equivalent of DOWN rounding for BigDecimal
    dbl = dbl > 0 ? Math.floor(dbl) : Math.ceil(dbl); // 正数向下取整，负数向上取整（相当于DOWN舍入）
    if (dbl < min || dbl > max) { // 如果超出范围
      throw new ArithmeticException("Value " + value + " out of range"); // 抛出算术异常
    }
  }

  /** Called from BuiltInMethod.INTEGER_CAST */
  // 整数类型转换，使用DOWN舍入模式，将数值转换为指定原始类型
  // 从BuiltInMethod.INTEGER_CAST方法调用，用于SQL的CAST操作
  public static @Nullable Object integerCast(Primitive primitive, final Object value) {
    return requireNonNull(primitive, "primitive").numberValue((Number) value, RoundingMode.DOWN); // 调用numberValue方法进行转换
  }

  /** Called from BuiltInMethod.INTEGER_CAST_ROUNDING_MODE */
  // 整数类型转换，使用指定的舍入模式，将数值转换为指定原始类型
  // 从BuiltInMethod.INTEGER_CAST_ROUNDING_MODE方法调用，用于SQL的CAST操作
  public static @Nullable Object integerCast(Primitive primitive, final Object value,
      RoundingMode roundingMode) {
    return requireNonNull(primitive, "primitive").numberValue((Number) value, roundingMode); // 调用numberValue方法进行转换
  }

  // 检查BigDecimal值是否溢出指定的精度和范围，用于DECIMAL类型的类型转换
  // 如果值超出精度限制，则抛出ArithmeticException异常
  static BigDecimal checkOverflow(BigDecimal value, int precision, int scale,
      RoundingMode roundingMode) {
    BigDecimal result = value.setScale(scale, roundingMode); // 按指定的小数位数和舍入模式进行舍入
    result = result.stripTrailingZeros(); // 去除尾部的零
    if (result.scale() < scale) { // 如果去除尾部零后的小数位数小于指定的小数位数
      // stripTrailingZeros also removes zeros if there is no
      // decimal point, converting 1000 to 1e+3, using a negative scale.
      // Here we undo this change.
      // stripTrailingZeros会在没有小数点时也去除零，将1000转换为1e+3，使用负数的小数位数
      // 这里我们撤销这个改变，恢复到指定的小数位数
      result = result.setScale(scale, roundingMode); // 恢复到指定的小数位数
    }
    int actualPrecision = result.precision(); // 获取实际精度（有效数字位数）
    if (actualPrecision > precision) { // 如果实际精度超过指定精度
      throw new ArithmeticException("Value " + value
          + " cannot be represented as a DECIMAL(" + precision + ", " + scale + ")"); // 抛出算术异常
    }
    if (scale < 0) { // 如果小数位数为负数
      // The result maybe scientific notation string,e.g. 1.234E+6,
      // we need to convert it to 1234000
      // 结果可能是科学计数法字符串，如1.234E+6，我们需要将其转换为1234000
      return new BigDecimal(result.toPlainString()); // 使用普通字符串格式创建BigDecimal
    }
    return result; // 返回检查后的BigDecimal值
  }

  /** Called from BuiltInMethod.CHAR_DECIMAL_CAST */
  // 将字符串转换为DECIMAL类型，使用DOWN舍入模式
  // 从BuiltInMethod.CHAR_DECIMAL_CAST方法调用，用于SQL的CAST操作
  public static @Nullable Object charToDecimalCast(
      @Nullable String value, int precision, int scale) {
    return charToDecimalCast(value, precision, scale, RoundingMode.DOWN); // 调用带舍入模式的版本
  }

  /** Called from BuiltInMethod.CHAR_DECIMAL_CAST_ROUNDING_MODE */
  // 将字符串转换为DECIMAL类型，使用指定的舍入模式
  // 从BuiltInMethod.CHAR_DECIMAL_CAST_ROUNDING_MODE方法调用，用于SQL的CAST操作
  public static @Nullable Object charToDecimalCast(
      @Nullable String value, int precision, int scale, RoundingMode roundingMode) {
    if (value == null) { // 如果值为null
      return null; // 返回null
    }
    BigDecimal result = new BigDecimal(value.trim()); // 去除首尾空格后创建BigDecimal
    return checkOverflow(result, precision, scale, roundingMode); // 检查是否溢出并返回结果
  }

  /**
   * Convert a short time interval to a decimal value.
   * Called from BuiltInMethod.SHORT_INTERVAL_DECIMAL_CAST.
   * @param unitScale Scale describing source interval type */
  // 将短时间间隔转换为DECIMAL类型，使用DOWN舍入模式
  // 从BuiltInMethod.SHORT_INTERVAL_DECIMAL_CAST方法调用
  // unitScale描述源间隔类型的比例因子
  public static @Nullable Object shortIntervalToDecimalCast(
      @Nullable Long value, int precision, int scale,
      BigDecimal unitScale) {
    return shortIntervalToDecimalCast(value, precision, scale, unitScale, RoundingMode.DOWN); // 调用带舍入模式的版本
  }

  /**
   * Convert a short time interval to a decimal value.
   * Called from BuiltInMethod.SHORT_INTERVAL_DECIMAL_CAST_ROUNDING_MODE.
   * @param unitScale Scale describing source interval type */
  // 将短时间间隔转换为DECIMAL类型，使用指定的舍入模式
  // 从BuiltInMethod.SHORT_INTERVAL_DECIMAL_CAST_ROUNDING_MODE方法调用
  // unitScale描述源间隔类型的比例因子
  public static @Nullable Object shortIntervalToDecimalCast(
      @Nullable Long value, int precision, int scale,
      BigDecimal unitScale, RoundingMode roundingMode) {
    if (value == null) { // 如果值为null
      return null; // 返回null
    }
    // Divide with the scale expected of the result
    // 使用结果期望的小数位数进行除法运算
    BigDecimal result = new BigDecimal(value).divide(unitScale, scale, roundingMode); // 将值除以比例因子并舍入
    return checkOverflow(result, precision, scale, roundingMode); // 检查是否溢出并返回结果
  }

  /**
   * Convert a long time interval to a decimal value.
   * Called from BuiltInMethod.LONG_INTERVAL_DECIMAL_CAST.
   * @param unitScale Scale describing source interval type */
  // 将长时间间隔转换为DECIMAL类型，使用DOWN舍入模式
  // 从BuiltInMethod.LONG_INTERVAL_DECIMAL_CAST方法调用
  // unitScale描述源间隔类型的比例因子
  public static @Nullable Object longIntervalToDecimalCast(
      @Nullable Integer value, int precision, int scale,
      BigDecimal unitScale) {
    return longIntervalToDecimalCast(value, precision, scale, unitScale, RoundingMode.DOWN); // 调用带舍入模式的版本
  }

  /**
   * Convert a long time interval to a decimal value.
   * Called from BuiltInMethod.LONG_INTERVAL_DECIMAL_CAST_ROUNDING_MODE.
   * @param unitScale Scale describing source interval type */
  // 将长时间间隔转换为DECIMAL类型，使用指定的舍入模式
  // 从BuiltInMethod.LONG_INTERVAL_DECIMAL_CAST_ROUNDING_MODE方法调用
  // unitScale描述源间隔类型的比例因子
  public static @Nullable Object longIntervalToDecimalCast(
      @Nullable Integer value, int precision, int scale,
      BigDecimal unitScale, RoundingMode roundingMode) {
    if (value == null) { // 如果值为null
      return null; // 返回null
    }
    // Divide with the scale expected of the result
    // 使用结果期望的小数位数进行除法运算
    BigDecimal result = new BigDecimal(value).divide(unitScale, scale, roundingMode); // 将值除以比例因子并舍入
    return checkOverflow(result, precision, scale, roundingMode); // 检查是否溢出并返回结果
  }

  /** Called from BuiltInMethod.DECIMAL_DECIMAL_CAST */
  // 将DECIMAL类型转换为DECIMAL类型（精度转换），使用DOWN舍入模式
  // 从BuiltInMethod.DECIMAL_DECIMAL_CAST方法调用，用于SQL的CAST操作
  public static @Nullable Object decimalDecimalCast(
      @Nullable BigDecimal value, int precision, int scale) {
    return decimalDecimalCast(value, precision, scale, RoundingMode.DOWN); // 调用带舍入模式的版本
  }

  /** Called from BuiltInMethod.DECIMAL_DECIMAL_CAST_ROUNDING_MODE */
  // 将DECIMAL类型转换为DECIMAL类型（精度转换），使用指定的舍入模式
  // 从BuiltInMethod.DECIMAL_DECIMAL_CAST_ROUNDING_MODE方法调用，用于SQL的CAST操作
  public static @Nullable Object decimalDecimalCast(
      @Nullable BigDecimal value, int precision, int scale, RoundingMode roundingMode) {
    if (value == null) { // 如果值为null
      return null; // 返回null
    }
    return checkOverflow(value, precision, scale, roundingMode); // 检查是否溢出并返回结果
  }

  /** Called from BuiltInMethod.INTEGER_DECIMAL_CAST */
  // 将整数类型转换为DECIMAL类型，使用DOWN舍入模式
  // 从BuiltInMethod.INTEGER_DECIMAL_CAST方法调用，用于SQL的CAST操作
  public static @Nullable Object integerDecimalCast(
      @Nullable Number value, int precision, int scale) {
    return integerDecimalCast(value, precision, scale, RoundingMode.DOWN); // 调用带舍入模式的版本
  }

  /** Called from BuiltInMethod.INTEGER_DECIMAL_CAST_ROUNDING_MODE */
  // 将整数类型转换为DECIMAL类型，使用指定的舍入模式
  // 从BuiltInMethod.INTEGER_DECIMAL_CAST_ROUNDING_MODE方法调用，用于SQL的CAST操作
  public static @Nullable Object integerDecimalCast(
      @Nullable Number value, int precision, int scale, RoundingMode roundingMode) {
    if (value == null) { // 如果值为null
      return null; // 返回null
    }
    final BigDecimal decimal = new BigDecimal(value.longValue()); // 将整数转换为BigDecimal
    return checkOverflow(decimal, precision, scale, roundingMode); // 检查是否溢出并返回结果
  }

  /** Called from BuiltInMethod.FP_DECIMAL_CAST */
  // 将浮点数类型转换为DECIMAL类型，使用DOWN舍入模式
  // 从BuiltInMethod.FP_DECIMAL_CAST方法调用，用于SQL的CAST操作
  public static @Nullable Object fpDecimalCast(
      @Nullable Number value, int precision, int scale) {
    return fpDecimalCast(value, precision, scale, RoundingMode.DOWN); // 调用带舍入模式的版本
  }

  /** Called from BuiltInMethod.FP_DECIMAL_CAST_ROUNDING_MODE */
  // 将浮点数类型转换为DECIMAL类型，使用指定的舍入模式
  // 从BuiltInMethod.FP_DECIMAL_CAST_ROUNDING_MODE方法调用，用于SQL的CAST操作
  public static @Nullable Object fpDecimalCast(
      @Nullable Number value, int precision, int scale, RoundingMode roundingMode) {
    if (value == null) { // 如果值为null
      return null; // 返回null
    }
    final BigDecimal decimal = BigDecimal.valueOf(value.doubleValue()); // 将浮点数转换为BigDecimal
    return checkOverflow(decimal, precision, scale, roundingMode); // 检查是否溢出并返回结果
  }

  // 使用DOWN舍入模式将数值转换为该原始类型的值
  public @Nullable Object numberValueRoundDown(Number value) {
    return numberValue(value, RoundingMode.DOWN); // 调用numberValue方法，使用DOWN舍入模式
  }

  /**
   * Converts a number into a value of the type specified by this primitive
   * using the SQL CAST rules.  If the value conversion causes loss of significant digits,
   * an exception is thrown.
   *
   * @param value  Value to convert.
   * @param roundingMode Rounding behavior.
   * @return       The converted value, or null if the type of the result is not a number.
   */
  // 使用SQL CAST规则将数值转换为该原始类型的值，如果转换导致有效数字丢失则抛出异常
  // 支持BYTE、CHAR、SHORT、INT、LONG、FLOAT、DOUBLE类型的转换
  public @Nullable Object numberValue(Number value, RoundingMode roundingMode) {
    switch (this) {
    case BYTE:
      checkRoundedRange(value, Byte.MIN_VALUE, Byte.MAX_VALUE); // 检查是否在byte范围内
      if (value instanceof BigDecimal) { // 如果是BigDecimal类型
        return ((BigDecimal) value).setScale(0, roundingMode).byteValue(); // 先舍入再转换为byte
      }
      return value.byteValue(); // 直接转换为byte
    case CHAR:
      // No overflow checks for char values.
      // For example, Postgres has this behavior.
      // char值不进行溢出检查，例如Postgres就有这种行为
      return (char) value.intValue(); // 转换为int再转换为char
    case SHORT:
      checkRoundedRange(value, Short.MIN_VALUE, Short.MAX_VALUE); // 检查是否在short范围内
      if (value instanceof BigDecimal) { // 如果是BigDecimal类型
        return ((BigDecimal) value).setScale(0, roundingMode).shortValue(); // 先舍入再转换为short
      }
      return value.shortValue(); // 直接转换为short
    case INT:
      checkRoundedRange(value, Integer.MIN_VALUE, Integer.MAX_VALUE); // 检查是否在int范围内
      if (value instanceof BigDecimal) { // 如果是BigDecimal类型
        return ((BigDecimal) value).setScale(0, roundingMode).intValue(); // 先舍入再转换为int
      }
      return value.intValue(); // 直接转换为int
    case LONG:
      if (value instanceof Byte
          || value instanceof Short
          || value instanceof Integer
          || value instanceof Long) { // 如果是整数类型
        return value.longValue(); // 直接转换为long
      }
      if (value instanceof Float
          || value instanceof Double) { // 如果是浮点数类型
        // The value Long.MAX_VALUE cannot be represented exactly as a double,
        // so we cannot use checkRoundedRange.
        // Long.MAX_VALUE无法精确表示为double，所以不能使用checkRoundedRange
        BigDecimal decimal = BigDecimal.valueOf(value.doubleValue())
            // Round to an integer
            .setScale(0, roundingMode); // 舍入为整数
        // longValueExact will throw ArithmeticException if out of range
        // longValueExact会在超出范围时抛出ArithmeticException
        return decimal.longValueExact(); // 精确转换为long，超出范围会抛出异常
      }
      if (value instanceof BigDecimal) { // 如果是BigDecimal类型
        BigDecimal decimal = ((BigDecimal) value)
            // Round to an integer
            .setScale(0, roundingMode); // 舍入为整数
        // longValueExact will throw ArithmeticException if out of range
        // longValueExact会在超出范围时抛出ArithmeticException
        return decimal.longValueExact(); // 精确转换为long，超出范围会抛出异常
      }
      throw new AssertionError("Unexpected Number type "
          + value.getClass().getSimpleName()); // 不应该到达这里，抛出断言错误
    case FLOAT:
      // out of range values will be represented as infinities
      // 超出范围的值将表示为无穷大
      return value.floatValue(); // 转换为float，超出范围会变成±Infinity
    case DOUBLE:
      // out of range values will be represented as infinities
      // 超出范围的值将表示为无穷大
      return value.doubleValue(); // 转换为double，超出范围会变成±Infinity
    default:
      return null; // 非数值类型返回null
    }
  }

  /**
   * Converts a collection of boxed primitives into an array of primitives.
   *
   * @param collection Collection of boxed primitives
   *
   * @return array of primitives
   * @throws ClassCastException   if any element is not of the box type
   * @throws NullPointerException if any element is null
   */
  // 将包装类型的集合转换为原始类型数组
  // 例如：将Collection<Integer>转换为int[]
  @SuppressWarnings("unchecked")
  public Object toArray(Collection collection) {
    int i = 0; // 数组索引
    switch (this) {
    case DOUBLE:
      double[] doubles = new double[collection.size()]; // 创建double数组
      for (double v : (Collection<Double>) collection) { // 遍历集合
        doubles[i++] = v; // 将元素添加到数组
      }
      return doubles; // 返回double数组
    case FLOAT:
      float[] floats = new float[collection.size()]; // 创建float数组
      for (float v : (Collection<Float>) collection) { // 遍历集合
        floats[i++] = v; // 将元素添加到数组
      }
      return floats; // 返回float数组
    case INT:
      int[] ints = new int[collection.size()]; // 创建int数组
      for (int v : (Collection<Integer>) collection) { // 遍历集合
        ints[i++] = v; // 将元素添加到数组
      }
      return ints; // 返回int数组
    case LONG:
      long[] longs = new long[collection.size()]; // 创建long数组
      for (long v : (Collection<Long>) collection) { // 遍历集合
        longs[i++] = v; // 将元素添加到数组
      }
      return longs; // 返回long数组
    case SHORT:
      short[] shorts = new short[collection.size()]; // 创建short数组
      for (short v : (Collection<Short>) collection) { // 遍历集合
        shorts[i++] = v; // 将元素添加到数组
      }
      return shorts; // 返回short数组
    case BOOLEAN:
      boolean[] booleans = new boolean[collection.size()]; // 创建boolean数组
      for (boolean v : (Collection<Boolean>) collection) { // 遍历集合
        booleans[i++] = v; // 将元素添加到数组
      }
      return booleans; // 返回boolean数组
    case BYTE:
      byte[] bytes = new byte[collection.size()]; // 创建byte数组
      for (byte v : (Collection<Byte>) collection) { // 遍历集合
        bytes[i++] = v; // 将元素添加到数组
      }
      return bytes; // 返回byte数组
    case CHAR:
      char[] chars = new char[collection.size()]; // 创建char数组
      for (char v : (Collection<Character>) collection) { // 遍历集合
        chars[i++] = v; // 将元素添加到数组
      }
      return chars; // 返回char数组
    default:
      throw new RuntimeException("unexpected: " + this); // 不应该到达这里
    }
  }

  /**
   * Converts a collection of {@link Number} to a primitive array.
   */
  // 将Number类型的集合转换为原始类型数组，支持任意Number子类型（Integer、Long、Double等）
  public Object toArray2(Collection<Number> collection) {
    int i = 0; // 数组索引
    switch (this) {
    case DOUBLE:
      double[] doubles = new double[collection.size()]; // 创建double数组
      for (Number number : collection) { // 遍历集合
        doubles[i++] = number.doubleValue(); // 调用doubleValue()转换
      }
      return doubles; // 返回double数组
    case FLOAT:
      float[] floats = new float[collection.size()]; // 创建float数组
      for (Number number : collection) { // 遍历集合
        floats[i++] = number.floatValue(); // 调用floatValue()转换
      }
      return floats; // 返回float数组
    case INT:
      int[] ints = new int[collection.size()]; // 创建int数组
      for (Number number : collection) { // 遍历集合
        ints[i++] = number.intValue(); // 调用intValue()转换
      }
      return ints; // 返回int数组
    case LONG:
      long[] longs = new long[collection.size()]; // 创建long数组
      for (Number number : collection) { // 遍历集合
        longs[i++] = number.longValue(); // 调用longValue()转换
      }
      return longs; // 返回long数组
    case SHORT:
      short[] shorts = new short[collection.size()]; // 创建short数组
      for (Number number : collection) { // 遍历集合
        shorts[i++] = number.shortValue(); // 调用shortValue()转换
      }
      return shorts; // 返回short数组
    case BOOLEAN:
      boolean[] booleans = new boolean[collection.size()]; // 创建boolean数组
      for (Number number : collection) { // 遍历集合
        booleans[i++] = number.byteValue() != 0; // byteValue()不为0则为true，否则为false
      }
      return booleans; // 返回boolean数组
    case BYTE:
      byte[] bytes = new byte[collection.size()]; // 创建byte数组
      for (Number number : collection) { // 遍历集合
        bytes[i++] = number.byteValue(); // 调用byteValue()转换
      }
      return bytes; // 返回byte数组
    case CHAR:
      char[] chars = new char[collection.size()]; // 创建char数组
      for (Number number : collection) { // 遍历集合
        chars[i++] = (char) number.shortValue(); // 调用shortValue()转换后强转为char
      }
      return chars; // 返回char数组
    default:
      throw new RuntimeException("unexpected: " + this); // 不应该到达这里
    }
  }

  /** Permutes an array. */
  // 对数组进行排列（重排），根据sources数组指定的源索引重新排列数组元素
  // 例如：sources=[2,0,1]表示新数组的第0个元素来自原数组的第2个位置，第1个元素来自第0个位置，第2个元素来自第1个位置
  public Object permute(Object array, int[] sources) {
    int i; // 循环索引
    switch (this) {
    case DOUBLE:
      double[] doubles0 = (double[]) array; // 获取原始double数组
      double[] doubles = new double[doubles0.length]; // 创建新的double数组
      for (i = 0; i < doubles.length; i++) { // 遍历数组
        doubles[i] = doubles0[sources[i]]; // 根据sources数组指定的索引从原数组取值
      }
      return doubles; // 返回排列后的数组
    case FLOAT:
      float[] floats0 = (float[]) array; // 获取原始float数组
      float[] floats = new float[floats0.length]; // 创建新的float数组
      for (i = 0; i < floats.length; i++) { // 遍历数组
        floats[i] = floats0[sources[i]]; // 根据sources数组指定的索引从原数组取值
      }
      return floats; // 返回排列后的数组
    case INT:
      int[] ints0 = (int[]) array; // 获取原始int数组
      int[] ints = new int[ints0.length]; // 创建新的int数组
      for (i = 0; i < ints.length; i++) { // 遍历数组
        ints[i] = ints0[sources[i]]; // 根据sources数组指定的索引从原数组取值
      }
      return ints; // 返回排列后的数组
    case LONG:
      long[] longs0 = (long[]) array; // 获取原始long数组
      long[] longs = new long[longs0.length]; // 创建新的long数组
      for (i = 0; i < longs.length; i++) { // 遍历数组
        longs[i] = longs0[sources[i]]; // 根据sources数组指定的索引从原数组取值
      }
      return longs; // 返回排列后的数组
    case SHORT:
      short[] shorts0 = (short[]) array; // 获取原始short数组
      short[] shorts = new short[shorts0.length]; // 创建新的short数组
      for (i = 0; i < shorts.length; i++) { // 遍历数组
        shorts[i] = shorts0[sources[i]]; // 根据sources数组指定的索引从原数组取值
      }
      return shorts; // 返回排列后的数组
    case BOOLEAN:
      boolean[] booleans0 = (boolean[]) array; // 获取原始boolean数组
      boolean[] booleans = new boolean[booleans0.length]; // 创建新的boolean数组
      for (i = 0; i < booleans.length; i++) { // 遍历数组
        booleans[i] = booleans0[sources[i]]; // 根据sources数组指定的索引从原数组取值
      }
      return booleans; // 返回排列后的数组
    case BYTE:
      byte[] bytes0 = (byte[]) array; // 获取原始byte数组
      byte[] bytes = new byte[bytes0.length]; // 创建新的byte数组
      for (i = 0; i < bytes.length; i++) { // 遍历数组
        bytes[i] = bytes0[sources[i]]; // 根据sources数组指定的索引从原数组取值
      }
      return bytes; // 返回排列后的数组
    case CHAR:
      char[] chars0 = (char[]) array; // 获取原始char数组
      char[] chars = new char[chars0.length]; // 创建新的char数组
      for (i = 0; i < chars.length; i++) { // 遍历数组
        chars[i] = chars0[sources[i]]; // 根据sources数组指定的索引从原数组取值
      }
      return chars; // 返回排列后的数组
    default:
      throw new RuntimeException("unexpected: " + this); // 不应该到达这里
    }
  }

  /**
   * Converts an array to a string.
   *
   * @param array Array of this primitive type
   *
   * @return String representation of array
   */
  // 将数组转换为字符串表示，使用Arrays.toString方法
  public String arrayToString(Object array) {
    switch (this) {
    case BOOLEAN:
      return Arrays.toString((boolean[]) array); // boolean数组转字符串，如[true, false, true]
    case BYTE:
      return Arrays.toString((byte[]) array); // byte数组转字符串，如[1, 2, 3]
    case CHAR:
      return Arrays.toString((char[]) array); // char数组转字符串，如[a, b, c]
    case DOUBLE:
      return Arrays.toString((double[]) array); // double数组转字符串，如[1.0, 2.0, 3.0]
    case FLOAT:
      return Arrays.toString((float[]) array); // float数组转字符串，如[1.0, 2.0, 3.0]
    case INT:
      return Arrays.toString((int[]) array); // int数组转字符串，如[1, 2, 3]
    case LONG:
      return Arrays.toString((long[]) array); // long数组转字符串，如[1, 2, 3]
    case SHORT:
      return Arrays.toString((short[]) array); // short数组转字符串，如[1, 2, 3]
    case OTHER:
    case VOID:
      return Arrays.toString((Object[]) array); // Object数组转字符串
    default:
      throw new AssertionError("unexpected " + this); // 不应该到达这里
    }
  }

  /**
   * Sorts an array of this primitive type.
   *
   * @param array Array of this primitive type
   */
  // 对原始类型数组进行排序，使用Arrays.sort方法
  public void sortArray(Object array) {
    switch (this) {
    case BOOLEAN:
      // there is no Arrays.sort(boolean[])
      // Java没有提供Arrays.sort(boolean[])方法，需要自己实现
      final boolean[] booleans = (boolean[]) array; // 获取boolean数组
      sortBooleanArray(booleans, 0, booleans.length); // 调用自定义的boolean数组排序方法
      return;
    case BYTE:
      Arrays.sort((byte[]) array); // 对byte数组排序
      return;
    case CHAR:
      Arrays.sort((char[]) array); // 对char数组排序
      return;
    case DOUBLE:
      Arrays.sort((double[]) array); // 对double数组排序
      return;
    case FLOAT:
      Arrays.sort((float[]) array); // 对float数组排序
      return;
    case INT:
      Arrays.sort((int[]) array); // 对int数组排序
      return;
    case LONG:
      Arrays.sort((long[]) array); // 对long数组排序
      return;
    case SHORT:
      Arrays.sort((short[]) array); // 对short数组排序
      return;
    case OTHER:
    case VOID:
      Arrays.sort((Object[]) array); // 对Object数组排序
      return;
    default:
      throw new AssertionError("unexpected " + this); // 不应该到达这里
    }
  }

  /**
   * Sorts a specified range of an array of this primitive type.
   *
   * @param array Array of this primitive type
   * @param fromIndex the index of the first element, inclusive, to be sorted
   * @param toIndex the index of the last element, exclusive, to be sorted
   */
  // 对原始类型数组的指定范围进行排序，使用Arrays.sort方法
  public void sortArray(Object array, int fromIndex, int toIndex) {
    switch (this) {
    case BOOLEAN:
      // there is no Arrays.sort(boolean[], int, int)
      // Java没有提供Arrays.sort(boolean[], int, int)方法，需要自己实现
      sortBooleanArray((boolean[]) array, fromIndex, toIndex); // 调用自定义的boolean数组排序方法
      return;
    case BYTE:
      Arrays.sort((byte[]) array, fromIndex, toIndex); // 对byte数组的指定范围排序
      return;
    case CHAR:
      Arrays.sort((char[]) array, fromIndex, toIndex); // 对char数组的指定范围排序
      return;
    case DOUBLE:
      Arrays.sort((double[]) array, fromIndex, toIndex); // 对double数组的指定范围排序
      return;
    case FLOAT:
      Arrays.sort((float[]) array, fromIndex, toIndex); // 对float数组的指定范围排序
      return;
    case INT:
      Arrays.sort((int[]) array, fromIndex, toIndex); // 对int数组的指定范围排序
      return;
    case LONG:
      Arrays.sort((long[]) array, fromIndex, toIndex); // 对long数组的指定范围排序
      return;
    case SHORT:
      Arrays.sort((short[]) array, fromIndex, toIndex); // 对short数组的指定范围排序
      return;
    case OTHER:
    case VOID:
      Arrays.sort((Object[]) array, fromIndex, toIndex); // 对Object数组的指定范围排序
      return;
    default:
      throw new AssertionError("unexpected " + this); // 不应该到达这里
    }
  }

  // 对boolean数组的指定范围进行排序，排序后数组为[false, false, ..., false, true, ..., true]

    // false排在前面，true排在后面

    private static void sortBooleanArray(boolean[] booleans, int fromIndex,

        int toIndex) {

      // The sorted array will be like [false, false, ..., false, true, ... true].

      // Every time we see a "false", the transition point moves up one.

      // 排序后的数组形式为[false, false, ..., false, true, ... true]

      // 每次看到"false"，转换点就向上移动一位

      int midIndex = fromIndex; // midIndex是false和true的分界点，初始为fromIndex

      for (int i = fromIndex; i < toIndex; i++) { // 遍历指定范围

        if (!booleans[i]) { // 如果是false

          ++midIndex; // 分界点向后移动一位

        }

      }

  

      Arrays.fill(booleans, fromIndex, midIndex, false); // 将fromIndex到midIndex-1的区间填充为false

      Arrays.fill(booleans, midIndex, toIndex, true); // 将midIndex到toIndex-1的区间填充为true

    }

  /**
   * Sends a field value to a sink.
   */
  // 将对象的字段值发送到Sink，根据不同的原始类型调用不同的Field.get方法
  public void send(Field field, Object o, Sink sink)
      throws IllegalAccessException {
    switch (this) {
    case BOOLEAN:
      sink.set(field.getBoolean(o)); // 调用field.getBoolean获取boolean值并发送到sink
      break;
    case BYTE:
      sink.set(field.getByte(o)); // 调用field.getByte获取byte值并发送到sink
      break;
    case CHAR:
      sink.set(field.getChar(o)); // 调用field.getChar获取char值并发送到sink
      break;
    case SHORT:
      sink.set(field.getShort(o)); // 调用field.getShort获取short值并发送到sink
      break;
    case INT:
      sink.set(field.getInt(o)); // 调用field.getInt获取int值并发送到sink
      break;
    case LONG:
      sink.set(field.getLong(o)); // 调用field.getLong获取long值并发送到sink
      break;
    case FLOAT:
      sink.set(field.getFloat(o)); // 调用field.getFloat获取float值并发送到sink
      break;
    case DOUBLE:
      sink.set(field.getDouble(o)); // 调用field.getDouble获取double值并发送到sink
      break;
    default:
      sink.set(field.get(o)); // 调用field.get获取Object值并发送到sink
      break;
    }
  }

  /**
   * Gets an item from an array.
   */
  // 从数组中获取指定索引的元素，根据不同的原始类型调用不同的Array.get方法
  // Plain old Array.get doesn't cut it when you have an array of
  // Integer values but you want to read Short values. Array.getShort
  // does the right thing.
  // 普通的Array.get方法在处理Integer数组但想读取Short值时不够用，Array.getShort能正确处理这种情况
  public @Nullable Object arrayItem(Object dataSet, int ordinal) {
    switch (this) {
    case DOUBLE:
      return Array.getDouble(dataSet, ordinal); // 调用Array.getDouble获取double值
    case FLOAT:
      return Array.getFloat(dataSet, ordinal); // 调用Array.getFloat获取float值
    case BOOLEAN:
      return Array.getBoolean(dataSet, ordinal); // 调用Array.getBoolean获取boolean值
    case BYTE:
      return Array.getByte(dataSet, ordinal); // 调用Array.getByte获取byte值
    case CHAR:
      return Array.getChar(dataSet, ordinal); // 调用Array.getChar获取char值
    case SHORT:
      return Array.getShort(dataSet, ordinal); // 调用Array.getShort获取short值
    case INT:
      return Array.getInt(dataSet, ordinal); // 调用Array.getInt获取int值
    case LONG:
      return Array.getLong(dataSet, ordinal); // 调用Array.getLong获取long值
    case OTHER:
      return Array.get(dataSet, ordinal); // 调用Array.get获取Object值
    default:
      throw new AssertionError("unexpected " + this); // 不应该到达这里
    }
  }

  /**
   * Reads value from a source into an array.
   */
  // 从Source读取值并设置到数组的指定位置，根据不同的原始类型调用不同的Array.set方法
  @SuppressWarnings("argument.type.incompatible")
  public void arrayItem(Source source, Object dataSet, int ordinal) {
    switch (this) {
    case DOUBLE:
      Array.setDouble(dataSet, ordinal, source.getDouble()); // 从source获取double值并设置到数组
      return;
    case FLOAT:
      Array.setFloat(dataSet, ordinal, source.getFloat()); // 从source获取float值并设置到数组
      return;
    case BOOLEAN:
      Array.setBoolean(dataSet, ordinal, source.getBoolean()); // 从source获取boolean值并设置到数组
      return;
    case BYTE:
      Array.setByte(dataSet, ordinal, source.getByte()); // 从source获取byte值并设置到数组
      return;
    case CHAR:
      Array.setChar(dataSet, ordinal, source.getChar()); // 从source获取char值并设置到数组
      return;
    case SHORT:
      Array.setShort(dataSet, ordinal, source.getShort()); // 从source获取short值并设置到数组
      return;
    case INT:
      Array.setInt(dataSet, ordinal, source.getInt()); // 从source获取int值并设置到数组
      return;
    case LONG:
      Array.setLong(dataSet, ordinal, source.getLong()); // 从source获取long值并设置到数组
      return;
    case OTHER:
      Array.set(dataSet, ordinal, source.getObject()); // 从source获取Object值并设置到数组
      return;
    default:
      throw new AssertionError("unexpected " + this); // 不应该到达这里
    }
  }

  /**
   * Sends to a sink an from an array.
   */
  // 从数组中获取指定索引的元素并发送到Sink，根据不同的原始类型调用不同的Array.get方法
  public void arrayItem(Object dataSet, int ordinal, Sink sink) {
    switch (this) {
    case DOUBLE:
      sink.set(Array.getDouble(dataSet, ordinal)); // 获取double值并发送到sink
      return;
    case FLOAT:
      sink.set(Array.getFloat(dataSet, ordinal)); // 获取float值并发送到sink
      return;
    case BOOLEAN:
      sink.set(Array.getBoolean(dataSet, ordinal)); // 获取boolean值并发送到sink
      return;
    case BYTE:
      sink.set(Array.getByte(dataSet, ordinal)); // 获取byte值并发送到sink
      return;
    case CHAR:
      sink.set(Array.getChar(dataSet, ordinal)); // 获取char值并发送到sink
      return;
    case SHORT:
      sink.set(Array.getShort(dataSet, ordinal)); // 获取short值并发送到sink
      return;
    case INT:
      sink.set(Array.getInt(dataSet, ordinal)); // 获取int值并发送到sink
      return;
    case LONG:
      sink.set(Array.getLong(dataSet, ordinal)); // 获取long值并发送到sink
      return;
    case OTHER:
      sink.set(Array.get(dataSet, ordinal)); // 获取Object值并发送到sink
      return;
    default:
      throw new AssertionError("unexpected " + this); // 不应该到达这里
    }
  }

  /**
   * Gets a value from a given column in a JDBC result set.
   *
   * @param resultSet Result set
   * @param i Ordinal of column (1-based, per JDBC)
   */
  // 从JDBC结果集的指定列获取值，列索引从1开始（符合JDBC规范）
  public @Nullable Object jdbcGet(ResultSet resultSet, int i) throws SQLException {
    switch (this) {
    case BOOLEAN:
      return resultSet.getBoolean(i); // 获取boolean值
    case BYTE:
      return resultSet.getByte(i); // 获取byte值
    case CHAR:
      return (char) resultSet.getShort(i); // 获取short值并转换为char
    case DOUBLE:
      return resultSet.getDouble(i); // 获取double值
    case FLOAT:
      return resultSet.getFloat(i); // 获取float值
    case INT:
      return resultSet.getInt(i); // 获取int值
    case LONG:
      return resultSet.getLong(i); // 获取long值
    case SHORT:
      return resultSet.getShort(i); // 获取short值
    default:
      return resultSet.getObject(i); // 获取Object值
    }
  }

  /**
   * Sends to a sink a value from a given column in a JDBC result set.
   *
   * @param resultSet Result set
   * @param i Ordinal of column (1-based, per JDBC)
   * @param sink Sink
   */
  // 从JDBC结果集的指定列获取值并发送到Sink，列索引从1开始（符合JDBC规范）
  public void jdbc(ResultSet resultSet, int i, Sink sink) throws SQLException {
    switch (this) {
    case BOOLEAN:
      sink.set(resultSet.getBoolean(i)); // 获取boolean值并发送到sink
      break;
    case BYTE:
      sink.set(resultSet.getByte(i)); // 获取byte值并发送到sink
      break;
    case CHAR:
      sink.set((char) resultSet.getShort(i)); // 获取short值，转换为char并发送到sink
      break;
    case DOUBLE:
      sink.set(resultSet.getDouble(i)); // 获取double值并发送到sink
      break;
    case FLOAT:
      sink.set(resultSet.getFloat(i)); // 获取float值并发送到sink
      break;
    case INT:
      sink.set(resultSet.getInt(i)); // 获取int值并发送到sink
      break;
    case LONG:
      sink.set(resultSet.getLong(i)); // 获取long值并发送到sink
      break;
    case SHORT:
      sink.set(resultSet.getShort(i)); // 获取short值并发送到sink
      break;
    default:
      sink.set(resultSet.getObject(i)); // 获取Object值并发送到sink
      break;
    }
  }

  /**
   * Sends a value from a source to a sink.
   */
  // 从Source读取值并发送到Sink，根据不同的原始类型调用不同的Source.get方法
  public void send(Source source, Sink sink) {
    switch (this) {
    case BOOLEAN:
      sink.set(source.getBoolean()); // 获取boolean值并发送到sink
      break;
    case BYTE:
      sink.set(source.getByte()); // 获取byte值并发送到sink
      break;
    case CHAR:
      sink.set(source.getChar()); // 获取char值并发送到sink
      break;
    case DOUBLE:
      sink.set(source.getDouble()); // 获取double值并发送到sink
      break;
    case FLOAT:
      sink.set(source.getFloat()); // 获取float值并发送到sink
      break;
    case INT:
      sink.set(source.getInt()); // 获取int值并发送到sink
      break;
    case LONG:
      sink.set(source.getLong()); // 获取long值并发送到sink
      break;
    case SHORT:
      sink.set(source.getShort()); // 获取short值并发送到sink
      break;
    default:
      sink.set(source.getObject()); // 获取Object值并发送到sink
      break;
    }
  }

  /**
   * Calls the appropriate {@link Integer#valueOf(String) valueOf(String)}
   * method.
   */
  // 将字符串解析为对应的包装类型值，调用相应的valueOf方法
  public Object parse(String stringValue) {
    switch (this) {
    case BOOLEAN:
      return Boolean.valueOf(stringValue); // 解析为Boolean
    case BYTE:
      return Byte.valueOf(stringValue); // 解析为Byte
    case CHAR:
      return Character.valueOf(stringValue.charAt(0)); // 取字符串的第一个字符解析为Character
    case DOUBLE:
      return Double.valueOf(stringValue); // 解析为Double
    case FLOAT:
      return Float.valueOf(stringValue); // 解析为Float
    case INT:
      return Integer.valueOf(stringValue); // 解析为Integer
    case LONG:
      return Long.valueOf(stringValue); // 解析为Long
    case SHORT:
      return Short.valueOf(stringValue); // 解析为Short
    default:
      throw new AssertionError(stringValue); // 不应该到达这里
    }
  }

  // 判断是否可以从指定的Primitive类型赋值到当前类型
  // 类型必须属于同一类型族（family相同），且当前类型的序号必须大于等于源类型的序号
  // 特殊情况：SHORT不能从CHAR赋值，CHAR不能从BYTE赋值
  public boolean assignableFrom(Primitive primitive) {
    return family == primitive.family // 类型族必须相同
        && ordinal() >= primitive.ordinal() // 当前类型序号必须大于等于源类型序号
        && !(this == SHORT && primitive == CHAR) // SHORT不能从CHAR赋值
        && !(this == CHAR && primitive == BYTE); // CHAR不能从BYTE赋值
  }

  /** Creates a number value of this primitive's box type. For example,
   * {@code SHORT.number(Integer(0))} will return {@code Short(0)}. */
  // 创建该原始类型对应的包装类型的Number值
  // 例如：SHORT.number(Integer(0))返回Short(0)
  public Number number(Number value) {
    switch (this) {
    case BYTE:
      return Byte.valueOf(value.byteValue()); // 转换为Byte
    case DOUBLE:
      return Double.valueOf(value.doubleValue()); // 转换为Double
    case FLOAT:
      return Float.valueOf(value.floatValue()); // 转换为Float
    case INT:
      return Integer.valueOf(value.intValue()); // 转换为Integer
    case LONG:
      return Long.valueOf(value.longValue()); // 转换为Long
    case SHORT:
      return Short.valueOf(value.shortValue()); // 转换为Short
    default:
      throw new AssertionError(this + ": " + value); // 不应该到达这里
    }
  }

  /**
   * A place to send a value.
   */
  // 值的接收器接口，用于接收和存储不同类型的值
  // 提供了针对所有原始类型的set方法重载，以及一个通用的Object类型set方法
  public interface Sink {
    void set(boolean v); // 设置boolean值

    void set(byte v); // 设置byte值

    void set(char v); // 设置char值

    void set(short v); // 设置short值

    void set(int v); // 设置int值

    void set(long v); // 设置long值

    void set(float v); // 设置float值

    void set(double v); // 设置double值

    void set(@Nullable Object v); // 设置Object值
  }

  /**
   * A place from which to read a value.
   */
  // 值的源接口，用于读取不同类型的值
  // 提供了针对所有原始类型的get方法，以及一个通用的Object类型get方法
  public interface Source {
    boolean getBoolean(); // 获取boolean值

    byte getByte(); // 获取byte值

    char getChar(); // 获取char值

    short getShort(); // 获取short值

    int getInt(); // 获取int值

    long getLong(); // 获取long值

    float getFloat(); // 获取float值

    double getDouble(); // 获取double值

    @Nullable Object getObject(); // 获取Object值
  }

  /** Whether a type is primitive (e.g. {@code int}),
   * a box type for a primitive (e.g. {@code java.lang.Integer}),
   * or something else. */
  // 类型风格枚举，表示类型是原始类型、包装类型还是其他类型
  public enum Flavor {
    /** A primitive type, e.g. {@code int}. */
    PRIMITIVE, // 原始类型，如int、boolean等
    /** A type that boxes a primitive, e.g. {@link Integer}. */
    BOX, // 包装类型，如Integer、Boolean等
    /** Neither a primitive nor a boxing type. */
    OBJECT // 其他类型，既不是原始类型也不是包装类型
  }
}
