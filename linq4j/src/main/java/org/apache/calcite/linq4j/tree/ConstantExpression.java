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

import org.apache.calcite.linq4j.util.Compatible;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Represents an expression that has a constant value.
 * 表示一个具有常量值的表达式节点，是LINQ表达式树中的常量表达式
 * 用于在表达式树中表示字面量值（如数字、字符串、布尔值等）
 * 该类继承自Expression基类，是表达式树的一种基本节点类型
 * 支持各种类型的常量值，包括基本类型、字符串、集合、数组、枚举等
 * 提供了将常量表达式转换为Java代码字符串的功能，用于代码生成
 */
public class ConstantExpression extends Expression {
  // 常量表达式的值，可以为null
  // 使用@Nullable注解表示该字段可能为null
  // 该字段是final的，一旦创建就不可修改，保证常量表达式的不可变性
  public final @Nullable Object value;

  // 构造方法：创建一个常量表达式
  // 参数type：常量的类型，可以是Class对象或其他Type实现
  // 参数value：常量的实际值，可以为null
  // 该构造方法会验证value的类型是否与声明的type匹配
  // 如果类型不匹配会抛出AssertionError异常
  public ConstantExpression(Type type, @Nullable Object value) {
    // 调用父类Expression的构造方法，设置节点类型为Constant，并传入类型信息
    super(ExpressionType.Constant, type);
    // 将传入的值赋给value成员变量
    this.value = value;
    // 如果value不为null，则进行类型验证
    if (value != null) {
      // 检查type是否是Class对象（即具体的Java类）
      if (type instanceof Class) {
        // 将type转换为Class对象
        Class clazz = (Class) type;
        // 将基本类型包装为对应的包装类（如int->Integer）
        clazz = Primitive.box(clazz);
        // 验证value是否是clazz的实例
        // 特殊情况：如果clazz是Float或Double，且value是BigDecimal，也认为是匹配的
        // 这是因为BigDecimal可以精确表示浮点数
        if (!clazz.isInstance(value)
            && !((clazz == Float.class || clazz == Double.class)
                && value instanceof BigDecimal)) {
          // 如果类型不匹配，抛出断言错误
          throw new AssertionError(
              "value " + value + " does not match type " + type);
        }
      }
    }
  }

  // 重写父类的evaluate方法，用于在运行时计算表达式的值
  // 参数evaluator：表达式求值器，但常量表达式不需要使用它
  // 返回值：返回常量表达式的值
  // 对于常量表达式，求值结果就是其存储的value值本身
  @Override public @Nullable Object evaluate(Evaluator evaluator) {
    // 直接返回常量值，不需要任何计算
    return value;
  }

  // 重写accept方法，接受Shuttle访问者模式的访问
  // 参数shuttle：表达式穿梭器，用于遍历和转换表达式树
  // 返回值：返回shuttle访问后的结果，可能是新的表达式或原表达式
  // Shuttle是一种表达式转换器，可以遍历表达式树并进行转换
  @Override public Expression accept(Shuttle shuttle) {
    // 调用shuttle的visit方法，让shuttle处理这个常量表达式
    return shuttle.visit(this);
  }

  // 重写accept方法，接受Visitor访问者模式的访问
  // 参数visitor：表达式访问者，用于遍历表达式树并收集信息
  // 返回值：返回visitor访问后的结果，类型由泛型R决定
  // Visitor是一种表达式分析器，可以遍历表达式树并提取信息
  @Override public <R> R accept(Visitor<R> visitor) {
    // 调用visitor的visit方法，让visitor处理这个常量表达式
    return visitor.visit(this);
  }

  // 重写accept方法，将常量表达式写入ExpressionWriter
  // 参数writer：表达式写入器，用于将表达式转换为Java代码字符串
  // 参数lprec：左操作符的优先级，用于判断是否需要添加括号
  // 参数rprec：右操作符的优先级，用于判断是否需要添加括号
  // 该方法将常量表达式转换为Java代码字符串并写入writer
  @Override void accept(ExpressionWriter writer, int lprec, int rprec) {
    // 如果常量值为null
    if (value == null) {
      // 检查是否需要添加括号（根据优先级判断）
      if (!writer.requireParentheses(this, lprec, rprec)) {
        // 如果不需要括号，直接写入类型转换和null
        // 格式为：(Type) null
        writer.append("(").append(type).append(") null");
      }
      // 如果已经写了括号或不需要写，直接返回
      return;
    }
    // 如果值不为null，调用write方法将值转换为Java代码字符串
    write(writer, value, type);
  }

  // 私有静态方法：将任意类型的常量值转换为Java代码字符串并写入writer
  // 参数writer：表达式写入器，用于构建Java代码字符串
  // 参数value：要写入的常量值
  // 参数type：常量值的类型，可以为null，如果为null则从value推断
  // 返回值：返回writer对象，支持链式调用
  // 该方法根据value的类型选择不同的转换策略，生成合法的Java代码
  private static ExpressionWriter write(ExpressionWriter writer,
      final Object value, @Nullable Type type) {
    // 如果value为null，直接写入"null"关键字
    if (value == null) {
      return writer.append("null");
    }
    // 如果type为null，从value的实际类型推断
    if (type == null) {
      type = value.getClass();
      // 将包装类型转换为基本类型（如Integer->int）
      type = Primitive.unbox(type);
    }
    // 处理字符串类型，需要转义特殊字符
    if (value instanceof String) {
      // 调用escapeString方法转义字符串中的特殊字符
      escapeString(writer.getBuf(), (String) value);
      return writer;
    }
    // 判断type是否是基本类型
    final Primitive primitive = Primitive.of(type);
    final BigDecimal bigDecimal;
    // 如果是基本类型，根据不同类型进行特殊处理
    if (primitive != null) {
      switch (primitive) {
      // 处理byte类型，输出格式：(byte) 123
      case BYTE:
        return writer.append("(byte)").append(((Byte) value).intValue());
      // 处理char类型，输出格式：(char) 65
      case CHAR:
        return writer.append("(char)").append((int) (Character) value);
      // 处理short类型，输出格式：(short) 12345
      case SHORT:
        return writer.append("(short)").append(((Short) value).intValue());
      // 处理long类型，输出格式：123L（必须加L后缀）
      case LONG:
        return writer.append(value).append("L");
      // 处理float类型，需要特殊处理无穷大、NaN和精度问题
      case FLOAT:
        // 如果value是BigDecimal，直接使用
        if (value instanceof BigDecimal) {
          bigDecimal = (BigDecimal) value;
        } else {
          // 转换为float类型
          float f = (Float) value;
          // 处理正无穷大
          if (f == Float.POSITIVE_INFINITY) {
            return writer.append("Float.POSITIVE_INFINITY");
          // 处理负无穷大
          } else if (f == Float.NEGATIVE_INFINITY) {
            return writer.append("Float.NEGATIVE_INFINITY");
          // 处理非数字NaN
          } else if (Float.isNaN(f)) {
            return writer.append("Float.NaN");
          }
          // 转换为BigDecimal以检查精度
          bigDecimal = BigDecimal.valueOf(f);
        }
        // 如果精度超过6位，使用intBitsToFloat方法确保精确表示
        if (bigDecimal.precision() > 6) {
          return writer.append("Float.intBitsToFloat(")
              .append(Float.floatToIntBits(bigDecimal.floatValue()))
              .append(")");
        }
        // 否则直接输出值并添加F后缀
        return writer.append(value).append("F");
      // 处理double类型，逻辑与float类似
      case DOUBLE:
        if (value instanceof BigDecimal) {
          bigDecimal = (BigDecimal) value;
        } else {
          double d = (Double) value;
          if (d == Double.POSITIVE_INFINITY) {
            return writer.append("Double.POSITIVE_INFINITY");
          } else if (d == Double.NEGATIVE_INFINITY) {
            return writer.append("Double.NEGATIVE_INFINITY");
          } else if (Double.isNaN(d)) {
            return writer.append("Double.NaN");
          }
          bigDecimal = BigDecimal.valueOf(d);
        }
        // 如果精度超过10位，使用longBitsToDouble方法确保精确表示
        if (bigDecimal.precision() > 10) {
          return writer.append("Double.longBitsToDouble(")
              .append(Double.doubleToLongBits(bigDecimal.doubleValue()))
              .append("L)");
        }
        // 否则直接输出值并添加D后缀
        return writer.append(value).append("D");
      // 其他基本类型（int, boolean等）直接输出
      default:
        return writer.append(value);
      }
    }
    // 判断type是否是包装类型
    final Primitive primitive2 = Primitive.ofBox(type);
    if (primitive2 != null) {
      // 使用valueOf方法创建包装类对象，如Integer.valueOf(123)
      writer.append(primitive2.boxName + ".valueOf(");
      // 递归调用write方法写入值
      write(writer, value, primitive2.primitiveClass);
      return writer.append(")");
    }
    // 如果type是Object.class，尝试从value的实际类型推断
    Primitive primitive3 = Primitive.ofBox(value.getClass());
    if (Object.class.equals(type) && primitive3 != null) {
      // 递归调用write方法，使用value的实际类型
      return write(writer, value, primitive3.primitiveClass);
    }
    // 处理枚举类型，输出格式：EnumType.ENUM_VALUE
    if (value instanceof Enum) {
      return writer.append(((Enum) value).getDeclaringClass())
          .append('.')
          .append(((Enum) value).name());
    }
    // 处理BigDecimal类型
    if (value instanceof BigDecimal) {
      // 去除尾部的零，简化表示
      bigDecimal = ((BigDecimal) value).stripTrailingZeros();
      try {
        // 获取小数位数
        final int scale = bigDecimal.scale();
        // 将BigDecimal转换为精确的long值
        final long exact = bigDecimal.scaleByPowerOfTen(scale).longValueExact();
        // 使用valueOf方法创建BigDecimal，如BigDecimal.valueOf(123L, 2)
        writer.append("java.math.BigDecimal.valueOf(").append(exact).append("L");
        // 如果有小数位数，添加第二个参数
        if (scale != 0) {
          writer.append(", ").append(scale);
        }
        return writer.append(")");
      } catch (ArithmeticException e) {
        // 如果无法精确转换为long，使用字符串构造方法
        return writer.append("new java.math.BigDecimal(\"")
            .append(bigDecimal.toString()).append("\")");
      }
    }
    // 处理BigInteger类型，使用字符串构造方法
    if (value instanceof BigInteger) {
      BigInteger bigInteger = (BigInteger) value;
      return writer.append("new java.math.BigInteger(\"")
          .append(bigInteger.toString()).append("\")");
    }
    // 处理Class对象，输出格式：ClassName.class
    if (value instanceof Class) {
      Class clazz = (Class) value;
      return writer.append(clazz.getCanonicalName()).append(".class");
    }
    // 处理RecordType类型
    if (value instanceof Types.RecordType) {
      final Types.RecordType recordType = (Types.RecordType) value;
      return writer.append(recordType.getName()).append(".class");
    }
    // 处理数组类型
    if (value.getClass().isArray()) {
      // 输出数组类型和初始化语法
      writer.append("new ").append(requireNonNull(value.getClass().getComponentType()));
      // 调用list方法输出数组元素
      list(writer, Primitive.asList(value), "[] {\n", ",\n", "}");
      return writer;
    }
    // 处理List类型
    if (value instanceof List) {
      // 如果是空列表，使用Collections.EMPTY_LIST
      if (((List) value).isEmpty()) {
        writer.append("java.util.Collections.EMPTY_LIST");
        return writer;
      }
      // 否则使用Arrays.asList方法创建列表
      list(writer, (List) value, "java.util.Arrays.asList(", ",\n", ")");
      return writer;
    }
    // 处理Map类型
    if (value instanceof Map) {
      return writeMap(writer, (Map) value);
    }
    // 处理Set类型
    if (value instanceof Set) {
      return writeSet(writer, (Set) value);
    }
    // 处理Charset类型
    if (value instanceof Charset) {
      // 使用Charset.forName方法创建字符集
      writer.append("java.nio.charset.Charset.forName(\"");
      writer.append(value);
      writer.append("\")");
      return writer;
    }

    // 对于其他类型，尝试使用反射获取字段值并构造对象
    // 获取类的所有字段
    final Field[] classFields = getClassFields(value.getClass());
    // 查找匹配的构造方法
    Constructor constructor = matchingConstructor(value, classFields);
    if (constructor != null) {
      // 输出new关键字和类名
      writer.append("new ").append(value.getClass());
      // 调用list方法输出构造参数
      list(writer,
          Arrays.stream(classFields)
              // 获取每个字段的值
              // <@Nullable Object> is needed for CheckerFramework
              .<@Nullable Object>map(field -> getFieldValue(value, field))
              .collect(Collectors.toList()),
          "(\n", ",\n", ")");
      return writer;
    }

    // 如果以上都不匹配，直接输出value的toString结果
    return writer.append(value);
  }

  // 私有静态方法：将列表中的元素转换为Java代码字符串
  // 参数writer：表达式写入器
  // 参数list：要写入的列表
  // 参数begin：列表开始字符串（如"java.util.Arrays.asList("）
  // 参数sep：元素之间的分隔符（如",\n"）
  // 参数end：列表结束字符串（如")"）
  // 该方法用于生成数组、列表等集合类型的初始化代码
  private static void list(ExpressionWriter writer, List list,
      String begin, String sep, String end) {
    // 开始写入列表，记录缩进级别
    writer.begin(begin);
    // 遍历列表中的每个元素
    for (int i = 0; i < list.size(); i++) {
      // 获取当前元素
      Object value = list.get(i);
      // 如果不是第一个元素，先写入分隔符并增加缩进
      if (i > 0) {
        writer.append(sep).indent();
      }
      // 递归调用write方法将元素转换为Java代码字符串
      // type参数为null，表示自动推断类型
      write(writer, value, null);
    }
    // 结束列表写入，恢复缩进级别
    writer.end(end);
  }

  // 私有静态方法：将Map对象转换为Java代码字符串
  // 参数writer：表达式写入器
  // 参数map：要写入的Map对象
  // 返回值：返回writer对象，支持链式调用
  // 使用Guava的ImmutableMap来生成不可变Map的初始化代码
  private static ExpressionWriter writeMap(ExpressionWriter writer, Map map) {
    // 写入ImmutableMap的类名前缀
    writer.append("com.google.common.collect.ImmutableMap.");
    // 如果Map为空，使用of()方法创建空Map
    if (map.isEmpty()) {
      return writer.append("of()");
    }
    // 如果Map元素少于5个，使用of()方法直接传入键值对
    if (map.size() < 5) {
      return map(writer, map, "of(", ",\n", ")");
    }
    // 如果Map元素较多，使用builder模式构建Map
    return map(writer, map, "builder().put(", ")\n.put(", ").build()");
  }

  // 私有静态方法：将Map的键值对转换为Java代码字符串
  // 参数writer：表达式写入器
  // 参数map：要写入的Map对象
  // 参数begin：Map开始字符串（如"of("或"builder().put("）
  // 参数entrySep：键值对之间的分隔符（如",\n"或")\n.put("）
  // 参数end：Map结束字符串（如")"或").build()"）
  // 返回值：返回writer对象，支持链式调用
  private static ExpressionWriter map(ExpressionWriter writer, Map map,
      String begin, String entrySep, String end) {
    // 写入Map开始字符串
    writer.append(begin);
    // 标记是否需要添加分隔符（第一个元素不需要）
    boolean comma = false;
    // 遍历Map的所有条目
    for (Object o : map.entrySet()) {
      // 获取当前键值对
      Map.Entry entry = (Map.Entry) o;
      // 如果不是第一个条目，先写入分隔符并增加缩进
      if (comma) {
        writer.append(entrySep).indent();
      }
      // 递归调用write方法写入键
      write(writer, entry.getKey(), null);
      // 写入键和值之间的逗号分隔符
      writer.append(", ");
      // 递归调用write方法写入值
      write(writer, entry.getValue(), null);
      // 标记已经写过至少一个条目
      comma = true;
    }
    // 写入Map结束字符串
    return writer.append(end);
  }

  // 私有静态方法：将Set对象转换为Java代码字符串
  // 参数writer：表达式写入器
  // 参数set：要写入的Set对象
  // 返回值：返回writer对象，支持链式调用
  // 使用Guava的ImmutableSet来生成不可变Set的初始化代码
  private static ExpressionWriter writeSet(ExpressionWriter writer, Set set) {
    // 写入ImmutableSet的类名前缀
    writer.append("com.google.common.collect.ImmutableSet.");
    // 如果Set为空，使用of()方法创建空Set
    if (set.isEmpty()) {
      return writer.append("of()");
    }
    // 如果Set元素少于5个，使用of()方法直接传入元素
    if (set.size() < 5) {
      return set(writer, set, "of(", ",", ")");
    }
    // 如果Set元素较多，使用builder模式构建Set
    return set(writer, set, "builder().add(", ")\n.add(", ").build()");
  }

  // 私有静态方法：将Set的元素转换为Java代码字符串
  // 参数writer：表达式写入器
  // 参数set：要写入的Set对象
  // 参数begin：Set开始字符串（如"of("或"builder().add("）
  // 参数entrySep：元素之间的分隔符（如","或")\n.add("）
  // 参数end：Set结束字符串（如")"或").build()"）
  // 返回值：返回writer对象，支持链式调用
  private static ExpressionWriter set(ExpressionWriter writer, Set set,
                                      String begin, String entrySep, String end) {
    // 写入Set开始字符串
    writer.append(begin);
    // 标记是否需要添加分隔符（第一个元素不需要）
    boolean comma = false;
    // 遍历Set的所有元素（转换为数组以支持迭代）
    for (Object o : set.toArray()) {
      // 如果不是第一个元素，先写入分隔符并增加缩进
      if (comma) {
        writer.append(entrySep).indent();
      }
      // 递归调用write方法写入元素
      write(writer, o, null);
      // 标记已经写过至少一个元素
      comma = true;
    }
    // 写入Set结束字符串
    return writer.append(end);
  }

  // 私有静态方法：查找与给定字段匹配的构造方法
  // 参数value：要查找构造方法的对象
  // 参数fields：对象的字段数组
  // 返回值：返回匹配的构造方法，如果没有匹配的则返回null
  // 该方法用于在代码生成时找到合适的构造方法来重建对象
  private static @Nullable Constructor matchingConstructor(Object value, Field[] fields) {
    // 遍历类的所有公共构造方法
    for (Constructor<?> constructor : value.getClass().getConstructors()) {
      // 检查构造方法的参数类型是否与字段类型匹配
      if (argsMatchFields(fields, constructor.getParameterTypes())) {
        // 如果匹配，返回该构造方法
        return constructor;
      }
    }
    // 如果没有找到匹配的构造方法，返回null
    return null;
  }

  // 私有静态方法：检查构造方法的参数类型是否与字段类型匹配
  // 参数fields：对象的字段数组
  // 参数parameterTypes：构造方法的参数类型数组
  // 返回值：如果参数类型与字段类型完全匹配则返回true，否则返回false
  // 该方法用于判断构造方法是否可以用于重建对象
  private static boolean argsMatchFields(Field[] fields,
      Class<?>[] parameterTypes) {
    // 首先检查参数数量是否与字段数量相同
    if (parameterTypes.length != fields.length) {
      return false;
    }
    // 逐个检查每个参数类型是否与对应的字段类型相同
    for (int i = 0; i < fields.length; i++) {
      if (fields[i].getType() != parameterTypes[i]) {
        // 如果有任何类型不匹配，返回false
        return false;
      }
    }
    // 所有类型都匹配，返回true
    return true;
  }

  // 私有静态方法：转义字符串中的特殊字符，使其成为合法的Java字符串字面量
  // 参数buf：字符串构建器，用于写入转义后的字符串
  // 参数s：要转义的原始字符串
  // 该方法处理字符串中的反斜杠、双引号、换行符、回车符等特殊字符
  private static void escapeString(StringBuilder buf, String s) {
    // 写入字符串开始的双引号
    buf.append('"');
    // 获取字符串长度
    int n = s.length();
    // 记录上一个字符，用于处理Windows风格的换行符(\r\n)
    char lastChar = 0;
    // 遍历字符串中的每个字符
    for (int i = 0; i < n; ++i) {
      // 获取当前字符
      char c = s.charAt(i);
      // 根据字符类型进行转义处理
      switch (c) {
      // 反斜杠需要转义为双反斜杠
      case '\\':
        buf.append("\\\\");
        break;
      // 双引号需要转义为\"
      case '"':
        buf.append("\\\"");
        break;
      // 换行符转义为\n
      case '\n':
        buf.append("\\n");
        break;
      // 回车符转义为\r，但如果是\r\n组合，则跳过\r
      case '\r':
        if (lastChar != '\n') {
          buf.append("\\r");
        }
        break;
      // 其他字符直接写入
      default:
        buf.append(c);
        break;
      }
      // 更新上一个字符
      lastChar = c;
    }
    // 写入字符串结束的双引号
    buf.append('"');
  }

  // 私有静态方法：获取对象的字段值
  // 参数source：要获取字段值的对象
  // 参数field：要获取值的字段
  // 返回值：返回字段的值，可能为null
  // 该方法根据对象类型选择不同的获取字段值的方式
  // 对于Record类型，使用getter方法获取值（因为Record的字段是私有的）
  // 对于普通类，直接通过反射获取字段值
  private static @Nullable Object getFieldValue(Object source, Field field) {
    // 检查对象是否是Record类型
    if (isRecord(source.getClass())) {
      // 如果是Record，通过getter方法获取字段值
      return getValueFromGetterMethod(source, field);
    }
    // 如果不是Record，直接通过反射获取字段值
    return getValueFromField(source, field);
  }

  // 私有静态方法：通过getter方法获取Record的字段值
  // 参数source：要获取字段值的Record对象
  // 参数field：要获取值的字段
  // 返回值：返回字段的值，可能为null
  // Record类型的字段是私有的，必须通过公共的getter方法访问
  private static @Nullable Object getValueFromGetterMethod(Object source, Field field) {
    try {
      // 查找与字段匹配的公共getter方法
      // 然后调用该getter方法获取字段值
      return findPublicGetter(field, source.getClass().getMethods()).invoke(source);
    } catch (IllegalAccessException | InvocationTargetException e) {
      // 如果调用失败，抛出IllegalArgumentException
      throw new IllegalArgumentException("Could not invoke getter method for field: "
          + field.getName(), e);
    }
  }

  // 私有静态方法：通过反射直接获取字段值
  // 参数source：要获取字段值的对象
  // 参数field：要获取值的字段
  // 返回值：返回字段的值，可能为null
  // 该方法适用于普通类（非Record），直接通过反射访问字段
  private static @Nullable Object getValueFromField(Object source, Field field) {
    try {
      // 使用Field.get()方法直接获取字段值
      return field.get(source);
    } catch (IllegalAccessException e) {
      // 如果访问失败（如字段是私有的），抛出IllegalArgumentException
      throw new IllegalArgumentException("Could not get field value for field: "
          + field.getName(), e);
    }
  }

  // 私有静态方法：获取类的字段数组
  // 参数clazz：要获取字段的类
  // 返回值：返回类的字段数组
  // 对于Record类型，返回所有声明的字段（包括私有字段）
  // 对于普通类，返回所有公共字段
  private static Field[] getClassFields(Class<?> clazz) {
    // 如果是Record类型，使用getDeclaredFields()获取所有字段
    // 如果是普通类，使用getFields()获取公共字段
    return isRecord(clazz) ? clazz.getDeclaredFields() : clazz.getFields();
  }

  // 私有静态方法：检查类是否是Record类型
  // 参数clazz：要检查的类
  // 返回值：如果是Record类型返回true，否则返回false
  // Record是Java 14引入的特性，用于声明不可变的数据类
  // 使用Compatible工具类来检测Record类型，确保兼容性
  private static boolean isRecord(Class<?> clazz) {
    // 委托给Compatible工具类的isRecord方法
    return Compatible.INSTANCE.isRecord(clazz);
  }

  // 私有静态方法：查找与字段匹配的公共getter方法
  // 参数field：要查找getter方法的字段
  // 参数methods：类的所有方法数组
  // 返回值：返回匹配的getter方法
  // Record的getter方法名与字段名相同，这是Record的特性
  private static Method findPublicGetter(Field field, Method[] methods) {
    // 使用Stream API查找匹配的getter方法
    return Arrays.stream(methods)
        // 过滤出符合getter条件的方法
        .filter(method -> isFieldGetter(field, method))
        // 返回第一个匹配的方法
        .findFirst()
        // 如果没有找到，抛出异常
        .orElseThrow(() -> new IllegalArgumentException("Could not get field value"));
  }

  // 私有静态方法：检查方法是否是字段的getter方法
  // 参数field：要检查的字段
  // 参数method：要检查的方法
  // 返回值：如果方法是该字段的getter方法则返回true，否则返回false
  // 对于Record，getter方法的返回类型必须与字段类型相同，方法名与字段名相同
  private static boolean isFieldGetter(Field field, Method method) {
    // 检查方法的返回类型是否与字段类型相同
    return method.getReturnType().equals(field.getType())
        // 检查方法是否是public的
        && Modifier.isPublic(method.getModifiers())
        // 检查方法是否没有参数（getter方法不应该有参数）
        && method.getParameterCount() == 0
        // 检查方法名是否与字段名匹配
        && nameMatchesGetter(field, method);
  }

  // 私有静态方法：检查方法名是否与字段名匹配
  // 参数field：要检查的字段
  // 参数method：要检查的方法
  // 返回值：如果方法名与字段名相同则返回true，否则返回false
  // 对于Record，getter方法的名称与字段名称完全相同（没有get前缀）
  // 这是Record与传统JavaBean的区别
  private static boolean nameMatchesGetter(Field field, Method method) {
    // 直接比较方法名和字段名
    return method.getName().equals(field.getName());
  }

  // 重写equals方法，判断两个常量表达式是否相等
  // 参数o：要比较的对象
  // 返回值：如果相等返回true，否则返回false
  // 注意：该注释提出了一个问题，即值相同但类型不同的常量（如3L和3）是否应该被认为相等
  // 当前实现中，类型不同的常量表达式不会被认为相等（因为会调用super.equals检查类型）
  @Override public boolean equals(@Nullable Object o) {
    // REVIEW: Should constants with the same value and different type
    // (e.g. 3L and 3) be considered equal.
    // 如果是同一个对象引用，直接返回true
    if (this == o) {
      return true;
    }
    // 如果o为null或类型不同，返回false
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    // 调用父类的equals方法，检查节点类型和类型是否相等
    if (!super.equals(o)) {
      return false;
    }

    // 将o转换为ConstantExpression类型
    ConstantExpression that = (ConstantExpression) o;

    // 比较value字段是否相等
    // 使用null安全的方式比较，防止NullPointerException
    if (value != null ? !value.equals(that.value) : that.value != null) {
      return false;
    }

    // 所有比较都通过，返回true
    return true;
  }

  // 重写hashCode方法，生成常量表达式的哈希码
  // 返回值：返回哈希码值
  // 哈希码基于节点类型、类型和值三个字段生成
  // 必须与equals方法保持一致：如果equals返回true，hashCode必须相同
  @Override public int hashCode() {
    // 使用Objects.hash方法基于多个字段生成哈希码
    // 包括nodeType（节点类型）、type（表达式类型）和value（常量值）
    return Objects.hash(nodeType, type, value);
  }
}
