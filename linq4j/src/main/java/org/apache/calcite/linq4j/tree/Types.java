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
package org.apache.calcite.linq4j.tree; // 包声明：定义该类属于 org.apache.calcite.linq4j.tree 包，该包负责处理 LINQ 表达式树相关的类型操作

import org.apache.calcite.linq4j.Enumerator; // 导入 Calcite LINQ4j 的枚举器接口，用于支持数据枚举操作

import com.google.common.collect.ImmutableList; // 导入 Google Guava 的不可变列表类，用于创建不可变的类型参数列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 Checker Framework 的可空注解，用于标记可能为 null 的返回值

import java.lang.reflect.Array; // 导入反射 API 的 Array 类，用于动态创建数组实例
import java.lang.reflect.Constructor; // 导入反射 API 的 Constructor 类，用于获取构造函数信息
import java.lang.reflect.Field; // 导入反射 API 的 Field 类，用于获取字段信息
import java.lang.reflect.GenericArrayType; // 导入反射 API 的 GenericArrayType 接口，表示泛型数组类型
import java.lang.reflect.Method; // 导入反射 API 的 Method 类，用于获取方法信息
import java.lang.reflect.ParameterizedType; // 导入反射 API 的 ParameterizedType 接口，表示参数化类型（泛型类型）
import java.lang.reflect.Type; // 导入反射 API 的 Type 接口，是所有类型的公共超接口
import java.lang.reflect.TypeVariable; // 导入反射 API 的 TypeVariable 接口，表示类型变量（泛型参数）
import java.util.ArrayList; // 导入 Java 集合框架的 ArrayList 类，用于动态数组列表
import java.util.Arrays; // 导入 Java 工具类的 Arrays，用于数组操作
import java.util.Collection; // 导入 Java 集合框架的 Collection 接口，表示集合类型
import java.util.Iterator; // 导入 Java 集合框架的 Iterator 接口，表示迭代器
import java.util.List; // 导入 Java 集合框架的 List 接口，表示有序列表

import static java.util.Objects.requireNonNull; // 静态导入 Objects.requireNonNull 方法，用于非空检查

/**
 * Utilities for converting between {@link Expression}, {@link Type} and
 * {@link Class}.
 * 类型工具类：提供在表达式（Expression）、类型（Type）和类（Class）之间进行转换的实用工具方法
 * 该类主要用于处理 LINQ 表达式树中的类型操作，包括类型转换、类型检查、方法查找等功能
 *
 * @see Primitive // 参见 Primitive 类，该类处理基本类型的装箱和拆箱操作
 */
public abstract class Types { // 声明 Types 为抽象类，不能被实例化，只提供静态工具方法
  private Types() {} // 私有构造函数，防止该类被实例化，确保该类只能作为工具类使用

  /**
   * Creates a type with generic parameters.
   * 创建带有泛型参数的类型，用于将原始类型和类型参数组合成参数化类型
   * 例如：of(List.class, String.class) 会创建 List<String> 类型
   */
  public static Type of(Type type, Type... typeArguments) { // 静态方法：创建参数化类型，接收原始类型和可变的类型参数数组
    if (typeArguments.length == 0) { // 检查类型参数数组是否为空
      return type; // 如果没有类型参数，直接返回原始类型（非泛型类型）
    }
    return new ParameterizedTypeImpl(type, ImmutableList.copyOf(typeArguments), // 创建 ParameterizedTypeImpl 实例，传入原始类型、类型参数的不可变副本，以及所有者类型（null 表示没有所有者）
        null); // 所有者类型为 null，表示这是一个顶级类型，不是内部类
  }

  /**
   * Returns the element type of a {@link Collection}, {@link Iterable}
   * (including {@link org.apache.calcite.linq4j.Queryable Queryable} and
   * {@link org.apache.calcite.linq4j.Enumerable Enumerable}), {@link Iterator},
   * {@link Enumerator}, or an array.
   * 获取集合、可迭代对象（包括 Queryable 和 Enumerable）、迭代器、枚举器或数组的元素类型
   * 该方法用于从容器类型中提取其包含的元素类型，例如从 List<String> 中提取 String 类型
   *
   * <p>Returns null if the type is not one of these.
   * 如果类型不是上述类型之一，则返回 null
   */
  public static @Nullable Type getElementType(Type type) { // 静态方法：获取类型的元素类型，返回可能为 null 的类型
    if (type instanceof ArrayType) { // 检查类型是否为自定义的 ArrayType（Calcite 的数组类型）
      return ((ArrayType) type).getComponentType(); // 如果是 ArrayType，返回其组件类型（元素类型）
    }
    if (type instanceof GenericArrayType) { // 检查类型是否为 Java 反射的泛型数组类型
      return ((GenericArrayType) type).getGenericComponentType(); // 如果是泛型数组，返回其泛型组件类型
    }
    Class<?> clazz = toClass(type); // 将类型转换为 Class 对象
    if (clazz.isArray()) { // 检查类是否为数组类型
      return clazz.getComponentType(); // 如果是数组，返回其组件类型
    }
    if (Collection.class.isAssignableFrom(clazz) // 检查类是否为 Collection 的子类或实现
        || Iterable.class.isAssignableFrom(clazz) // 或是否为 Iterable 的子类或实现
        || Iterator.class.isAssignableFrom(clazz) // 或是否为 Iterator 的子类或实现
        || Enumerator.class.isAssignableFrom(clazz)) { // 或是否为 Enumerator 的子类或实现
      if (type instanceof ParameterizedType) { // 检查类型是否为参数化类型（带泛型的类型）
        return ((ParameterizedType) type).getActualTypeArguments()[0]; // 如果是参数化类型，返回第一个类型参数（元素类型）
      }
      return Object.class; // 如果不是参数化类型（原始类型），返回 Object.class 作为元素类型
    }
    return null; // 如果不是上述任何类型，返回 null
  }

  static Field getField(String fieldName, Class<?> clazz) { // 静态方法：根据字段名和类获取公共字段
    try {
      return clazz.getField(fieldName); // 尝试获取类的公共字段（包括继承的公共字段）
    } catch (NoSuchFieldException e) { // 捕获字段不存在的异常
      throw new RuntimeException( // 抛出运行时异常，包含详细的错误信息
          "Unknown field '" + fieldName + "' in class " + clazz, e); // 异常消息包含字段名和类名
    }
  }

  static PseudoField getField(String fieldName, Type type) { // 静态方法：根据字段名和类型获取伪字段（可以是反射字段或记录字段）
    if (type instanceof RecordType) { // 检查类型是否为 RecordType（记录类型，Calcite 自定义的类型）
      return getRecordField(fieldName, (RecordType) type); // 如果是记录类型，从记录字段中查找
    } else if (type instanceof Class && ((Class<?>) type).isArray()) { // 检查类型是否为数组类
      return getSystemField(fieldName, (Class<?>) type); // 如果是数组，获取系统字段（如 length）
    } else {
      return field(getField(fieldName, toClass(type))); // 否则，转换为类并获取反射字段，然后包装为伪字段
    }
  }

  private static RecordField getRecordField(String fieldName, RecordType type) { // 私有静态方法：从记录类型中获取指定名称的记录字段
    for (RecordField field : type.getRecordFields()) { // 遍历记录类型的所有字段
      if (field.getName().equals(fieldName)) { // 检查字段名是否匹配
        return field; // 如果找到匹配的字段，返回该字段
      }
    }
    throw new RuntimeException( // 如果遍历完所有字段都没找到，抛出运行时异常
        "Unknown field '" + fieldName + "' in type " + type); // 异常消息包含字段名和类型
  }

  private static RecordField getSystemField(final String fieldName, // 私有静态方法：获取数组的系统字段（如 length 字段）
      final Class<?> clazz) { // 参数：字段名和数组类
    // The "length" field of an array does not appear in Class.getFields().
    // 注释：数组的 "length" 字段不会出现在 Class.getFields() 方法返回的结果中
    return new ArrayLengthRecordField(fieldName, clazz); // 创建并返回一个数组长度记录字段
  }

  public static Class<?> toClass(Type type) { // 静态公共方法：将 Type 对象转换为 Class 对象
    if (type instanceof Class) { // 检查类型是否已经是 Class 对象
      return (Class<?>) type; // 如果是，直接返回该 Class 对象
    }
    if (type instanceof ParameterizedType) { // 检查类型是否为参数化类型（泛型类型）
      return toClass(((ParameterizedType) type).getRawType()); // 递归调用，获取参数化类型的原始类型（擦除泛型后的类）
    }
    if (type instanceof TypeVariable) { // 检查类型是否为类型变量（泛型参数，如 T）
      TypeVariable<?> typeVariable = (TypeVariable<?>) type; // 强制转换为类型变量
      return toClass(typeVariable.getBounds()[0]); // 递归调用，获取类型变量的第一个边界类型（通常是 Object）
    }
    throw new RuntimeException("unsupported type " + type); // TODO: 如果类型不支持，抛出运行时异常
  }

  public static Class<?>[] toClassArray( // 静态公共方法：将表达式列表转换为 Class 数组
      Iterable<? extends Expression> arguments) { // 参数：表达式列表（可迭代对象）
    List<Class<?>> classes = new ArrayList<>(); // 创建一个 Class 列表用于存储转换结果
    for (Expression argument : arguments) { // 遍历所有表达式
      classes.add(toClass(argument.getType())); // 将每个表达式的类型转换为 Class 并添加到列表中
    }
    return classes.toArray(new Class[0]); // 将列表转换为 Class 数组并返回
  }

  /**
   * Returns the component type of an array.
   * 返回数组的组件类型（元素类型），支持多种数组类型表示形式
   */
  public static @Nullable Type getComponentType(Type type) { // 静态公共方法：获取数组的组件类型
    if (type instanceof Class) { // 检查类型是否为 Class 对象
      return ((Class<?>) type).getComponentType(); // 如果是，调用 Class.getComponentType() 获取组件类型
    }
    if (type instanceof ArrayType) { // 检查类型是否为 Calcite 的 ArrayType
      return ((ArrayType) type).getComponentType(); // 如果是，调用 ArrayType.getComponentType() 获取组件类型
    }
    if (type instanceof GenericArrayType) { // 检查类型是否为 Java 反射的泛型数组类型
      return ((GenericArrayType) type).getGenericComponentType(); // 如果是，获取泛型组件类型
    }
    if (type instanceof ParameterizedType) { // 检查类型是否为参数化类型
      return getComponentType(((ParameterizedType) type).getRawType()); // 递归调用，获取原始类型的组件类型
    }
    if (type instanceof TypeVariable) { // 检查类型是否为类型变量
      TypeVariable<?> typeVariable = (TypeVariable<?>) type; // 强制转换为类型变量
      return getComponentType(typeVariable.getBounds()[0]); // 递归调用，获取第一个边界类型的组件类型
    }
    return null; // 如果不是数组类型，返回 null
  }

  static Type getComponentTypeN(Type type) { // 静态方法：获取数组的最终组件类型（递归获取直到不是数组为止）
    for (;;) { // 无限循环，直到找到非数组类型
      Type componentType = getComponentType(type); // 获取当前类型的组件类型
      if (componentType == null) { // 如果组件类型为 null，说明当前类型不是数组
        return type; // 返回当前类型（最终的非数组类型）
      }
      type = componentType; // 如果是数组，继续向下一层查找
    }
  }

  public static Type box(Type type) { // 静态公共方法：将基本类型装箱为对应的包装类型
    return Primitive.box(type); // 委托给 Primitive.box 方法执行装箱操作（如 int -> Integer）
  }

  public static Type unbox(Type type) { // 静态公共方法：将包装类型拆箱为对应的基本类型
    return Primitive.unbox(type); // 委托给 Primitive.unbox 方法执行拆箱操作（如 Integer -> int）
  }

  static String className(Type type) { // 静态方法：获取类型的类名（简化格式）
    if (type instanceof ArrayType) { // 检查类型是否为 ArrayType
      return className(((ArrayType) type).getComponentType()) + "[]"; // 递归调用，获取组件类型名并添加 "[]" 后缀
    }
    if (!(type instanceof Class)) { // 检查类型是否不是 Class 对象
      return type.toString(); // 如果不是，直接返回类型的字符串表示
    }
    Class<?> clazz = (Class<?>) type; // 强制转换为 Class 对象
    if (clazz.isArray()) { // 检查类是否为数组
      return className(clazz.getComponentType()) + "[]"; // 递归调用，获取组件类型名并添加 "[]" 后缀
    }
    String className = clazz.getName(); // 获取类的全限定名
    if (!clazz.isPrimitive() // 检查类是否不是基本类型
        && clazz.getPackage() != null // 且类有包信息
        && clazz.getPackage().getName().equals("java.lang")) { // 且包名为 java.lang
      return className.substring("java.lang.".length()); // 去掉 "java.lang." 前缀，返回简化类名（如 String 而不是 java.lang.String）
    }
    return className.replace('$', '.'); // 将内部类的分隔符 '$' 替换为 '.'（如 Outer$Inner -> Outer.Inner）
  }

  public static boolean isAssignableFrom(Type type0, Type type) { // 静态公共方法：检查 type0 类型是否可以赋值为 type 类型
    return toClass(type0).isAssignableFrom(toClass(type)); // 将两个类型都转换为 Class，然后调用 Class.isAssignableFrom 方法检查赋值兼容性
  }

  public static boolean isArray(Type type) { // 静态公共方法：检查类型是否为数组类型
    return toClass(type).isArray(); // 将类型转换为 Class，然后调用 Class.isArray 方法检查是否为数组
  }

  public static Field nthField(int ordinal, Class<?> clazz) { // 静态公共方法：根据序号获取类的公共字段
    return clazz.getFields()[ordinal]; // 返回指定序号的公共字段
  }

  public static PseudoField nthField(int ordinal, Type clazz) { // 静态公共方法：根据序号获取类型的伪字段
    if (clazz instanceof RecordType) { // 检查类型是否为 RecordType
      RecordType recordType = (RecordType) clazz; // 强制转换为记录类型
      return recordType.getRecordFields().get(ordinal); // 返回指定序号的记录字段
    }
    return field(toClass(clazz).getFields()[ordinal]); // 否则，转换为类并获取指定序号的公共字段，然后包装为伪字段
  }

  public static boolean allAssignable(boolean varArgs, // 静态公共方法：检查所有参数类型是否可以赋值为参数类型
      Class<?>[] parameterTypes, Class<?>[] argumentTypes) { // 参数：是否可变参数、参数类型数组、参数实际类型数组
    if (varArgs) { // 检查是否为可变参数方法
      if (argumentTypes.length < parameterTypes.length - 1) { // 如果实际参数数量少于固定参数数量（可变参数前必须满足固定参数）
        return false; // 返回 false，参数不匹配
      }
    } else { // 如果不是可变参数方法
      if (parameterTypes.length != argumentTypes.length) { // 检查参数数量是否相等
        return false; // 如果不相等，返回 false
      }
    }
    for (int i = 0; i < argumentTypes.length; i++) { // 遍历所有实际参数
      Class<?> parameterType = // 确定当前参数的目标类型
          !varArgs || i < parameterTypes.length - 1 // 如果不是可变参数，或者是可变参数的固定参数部分
              ? parameterTypes[i] // 使用对应的参数类型
              : Object.class; // 如果是可变参数部分，使用 Object.class（可变参数可以接受任何类型）
      if (!assignableFrom(parameterType, argumentTypes[i])) { // 检查参数类型是否可以赋值为实际类型
        return false; // 如果不能赋值，返回 false
      }
    }
    return true; // 所有参数都匹配，返回 true
  }

  /**
   * Returns whether a parameter is assignable from an argument by virtue
   * of (a) sub-classing (e.g. Writer is assignable from PrintWriter) and (b)
   * up-casting (e.g. int is assignable from short).
   * 返回参数类型是否可以从参数类型赋值，包括两种情况：
   * (a) 子类关系（例如 Writer 可以从 PrintWriter 赋值）
   * (b) 基本类型向上转换（例如 int 可以从 short 赋值）
   *
   * @param parameter Parameter type - 参数类型（目标类型）
   * @param argument Argument type - 参数类型（源类型）
   *
   * @return Whether parameter can be assigned from argument - 参数是否可以从参数赋值
   */
  @SuppressWarnings("nullness") // 抑制空值检查警告
  private static boolean assignableFrom(Class<?> parameter, Class<?> argument) { // 私有静态方法：检查两个类之间是否可以赋值
    return parameter.isAssignableFrom(argument) // 检查参数是否可以从参数赋值（子类关系）
           || parameter.isPrimitive() // 或者，如果参数是基本类型
        && argument.isPrimitive() // 且参数也是基本类型
        && requireNonNull(Primitive.of(parameter)) // 获取参数对应的 Primitive 枚举（非空）
            .assignableFrom(requireNonNull(Primitive.of(argument))); // 检查基本类型是否可以向上转换
  }

  /**
   * Finds a method of a given name that accepts a给定 set of arguments.
   * 查找具有给定名称且接受给定参数集的方法
   * 搜索范围包括继承的方法和具有更宽泛参数类型的方法（支持类型向上转换）
   *
   * @param clazz Class against which method is invoked - 调用方法的类
   * @param methodName Name of method - 方法名称
   * @param argumentTypes Types of arguments - 参数类型数组
   *
   * @return A method with the given name that matches the arguments given - 匹配给定参数的方法
   * @throws RuntimeException if method not found - 如果找不到方法则抛出运行时异常
   */
  public static Method lookupMethod(Class<?> clazz, String methodName, // 静态公共方法：查找与给定名称和参数类型匹配的方法
      Class<?>... argumentTypes) { // 可变参数：参数类型数组
    try {
      return clazz.getMethod(methodName, argumentTypes); // 尝试直接获取精确匹配的公共方法
    } catch (NoSuchMethodException e) { // 如果找不到精确匹配的方法
      for (Method method : clazz.getMethods()) { // 遍历类的所有公共方法（包括继承的方法）
        if (method.getName().equals(methodName) && allAssignable( // 检查方法名是否匹配，且参数类型是否兼容
            method.isVarArgs(), method.getParameterTypes(), argumentTypes)) { // 检查可变参数和参数类型兼容性
          return method; // 如果找到匹配的方法，返回该方法
        }
      }
      throw new RuntimeException("while resolving method '" + methodName // 如果遍历完所有方法都没找到，抛出运行时异常
          + Arrays.toString(argumentTypes) + "' in class " + clazz, e); // 异常消息包含方法名、参数类型和类名
    }
  }

  /**
   * Finds a constructor of a given class that accepts a given set of
   * arguments. Includes in its search methods with wider argument types.
   * 查找给定类中接受给定参数集的构造函数
   * 搜索范围包括具有更宽泛参数类型的构造函数（支持类型向上转换）
   *
   * @param type Class against which method is invoked - 要查找构造函数的类类型
   * @param argumentTypes Types of arguments - 参数类型数组
   *
   * @return A method with the given name that matches the arguments given - 匹配给定参数的构造函数
   * @throws RuntimeException if method not found - 如果找不到构造函数则抛出运行时异常
   */
  public static Constructor<?> lookupConstructor(Type type, // 静态公共方法：查找与给定参数类型匹配的构造函数
      Class<?>... argumentTypes) { // 可变参数：参数类型数组
    final Class<?> clazz = toClass(type); // 将类型转换为 Class 对象
    Constructor<?>[] constructors = clazz.getDeclaredConstructors(); // 获取类声明的所有构造函数（包括私有的）
    for (Constructor<?> constructor : constructors) { // 遍历所有构造函数
      if (allAssignable(constructor.isVarArgs(), // 检查可变参数和参数类型兼容性
          constructor.getParameterTypes(), argumentTypes)) { // 使用 allAssignable 方法检查参数类型是否匹配
        return constructor; // 如果找到匹配的构造函数，返回该构造函数
      }
    }
    if (constructors.length == 0 && argumentTypes.length == 0) { // 如果没有构造函数且没有参数（可能是接口或抽象类）
      try {
        return clazz.getConstructor(); // 尝试获取默认的无参构造函数
      } catch (NoSuchMethodException e) { // 捕获方法不存在的异常
        // ignore - 忽略异常，继续执行
      }
    }
    throw new RuntimeException( // 如果找不到任何匹配的构造函数，抛出运行时异常
        "while resolving constructor in class " + type + " with types " + Arrays // 异常消息包含类名和参数类型
            .toString(argumentTypes));
  }

  public static Field lookupField(Type type, String name) { // 静态公共方法：根据类型和名称查找字段
    final Class<?> clazz = toClass(type); // 将类型转换为 Class 对象
    try {
      return clazz.getField(name); // 尝试获取指定名称的公共字段（包括继承的公共字段）
    } catch (NoSuchFieldException e) { // 捕获字段不存在的异常
      throw new RuntimeException("while resolving field in class " + type); // 抛出运行时异常，包含类名
    }
  }

  public static void discard(Object ignored) { // 静态公共方法：丢弃对象（用于某些场景下的占位操作）
  }

  /**
   * Returns the most restrictive type that is assignable from all given
   * types.
   * 返回可以从所有给定类型赋值的最受限类型（类型的最大公约数）
   * 该方法用于类型推断，找到多个类型的共同父类型
   */
  static Type gcd(Type... types) { // 静态方法：计算多个类型的最大公约数类型
    // TODO: improve this - TODO：改进此算法
    if (types.length == 0) { // 检查类型数组是否为空
      return Object.class; // 如果为空，返回 Object.class（所有类型的超类）
    }
    Type best = types[0]; // 取第一个类型作为初始最佳类型
    Primitive bestPrimitive = Primitive.of(best); // 获取第一个类型对应的 Primitive 枚举
    if (bestPrimitive != null) { // 如果第一个类型是基本类型
      for (int i = 1; i < types.length; i++) { // 遍历其余类型
        final Primitive primitive = Primitive.of(types[i]); // 获取当前类型对应的 Primitive 枚举
        if (primitive == null) { // 如果当前类型不是基本类型
          return Object.class; // 返回 Object.class（基本类型和非基本类型的共同父类型是 Object）
        }
        if (primitive.assignableFrom(bestPrimitive)) { // 检查当前基本类型是否可以从最佳基本类型赋值（向上转换）
          bestPrimitive = primitive; // 如果可以，更新最佳基本类型为更宽泛的类型
        } else if (bestPrimitive.assignableFrom(primitive)) { // 检查最佳基本类型是否可以从当前基本类型赋值
          // ok - 可以赋值，保持最佳基本类型不变
        } else if (bestPrimitive == Primitive.CHAR // 特殊处理：char 和 byte 类型
                   || bestPrimitive == Primitive.BYTE) { // char 和 byte 之间存在特殊转换规则
          // 'char' and 'byte' are problematic, because they don't
          // assign to each other. 'char' can't even assign to
          // 'short'. Before we give up, try one last time with 'int'.
          // 注释：char 和 byte 有问题，因为它们不能互相赋值。char 甚至不能赋值给 short。
          // 在放弃之前，尝试最后一次使用 int。
          bestPrimitive = Primitive.INT; // 将最佳基本类型设置为 int（可以兼容 char 和 byte）
          --i; // 减少索引，重新检查当前类型与 int 的兼容性
        } else {
          return Object.class; // 如果类型不兼容，返回 Object.class
        }
      }
      return requireNonNull(bestPrimitive.primitiveClass); // 返回最佳基本类型对应的 Class 对象
    } else { // 如果第一个类型不是基本类型（对象类型）
      for (int i = 1; i < types.length; i++) { // 遍历其余类型
        if (types[i] != types[0]) { // 检查类型是否与第一个类型相同
          return Object.class; // 如果不相同，返回 Object.class
        }
      }
    }
    return types[0]; // 如果所有类型都相同，返回该类型
  }

  /**
   * Wraps an expression in a cast if it is not already of the desired type,
   * or cannot be implicitly converted to it.
   * 如果表达式还不是所需的类型，或者不能隐式转换为所需类型，则将表达式包装在类型转换中
   * 该方法用于在表达式树中添加必要的类型转换操作
   *
   * @param returnType Desired type - 目标类型
   * @param expression Expression - 要转换的表达式
   *
   * @return Expression of desired type - 具有目标类型的表达式
   */
  public static Expression castIfNecessary(Type returnType, // 静态公共方法：如果需要，对表达式进行类型转换
      Expression expression) { // 参数：目标类型和表达式
    final Type type = expression.getType(); // 获取表达式的当前类型
    if (!needTypeCast(type, returnType)) { // 检查是否需要进行类型转换
      return expression; // 如果不需要转换，直接返回原表达式
    }
    if (returnType instanceof Class // 检查目标类型是否为 Class
        && Number.class.isAssignableFrom((Class<?>) returnType) // 且是否为 Number 的子类
        && type instanceof Class // 且当前类型是否为 Class
        && Number.class.isAssignableFrom((Class<?>) type)) { // 且是否为 Number 的子类
      // E.g.
      //   Integer foo(BigDecimal o) {
      //     return o.intValue();
      //   }
      // 示例：将 BigDecimal 转换为 Integer，调用 intValue() 方法
      return Expressions.unbox(expression, requireNonNull(Primitive.ofBox(returnType))); // 对表达式进行拆箱操作，提取数值
    }
    if (Primitive.is(returnType) && !Primitive.is(type)) { // 检查目标类型是否为基本类型且当前类型不是
      // E.g.
      //   int foo(Object o) {
      //     return ((Integer) o).intValue();
      //   }
      // 示例：将 Object 转换为 int，需要先转换为 Integer 再拆箱
      return Expressions.unbox( // 拆箱操作
          Expressions.convert_(expression, Types.box(returnType)), // 先将表达式转换为对应的包装类型
          requireNonNull(Primitive.of(returnType))); // 然后拆箱为目标基本类型
    }
    if (!Primitive.is(returnType) && Primitive.is(type)) { // 检查目标类型不是基本类型但当前类型是
      // E.g.
      //   Short foo(Object o) {
      //     return (short) (int) o;
      //   }
      // 示例：将基本类型转换为包装类型
      return Expressions.convert_(expression, // 进行类型转换
          Types.unbox(returnType)); // 目标类型对应的基本类型
    }
    return Expressions.convert_(expression, returnType); // 默认情况：直接进行类型转换
  }

  /**
   * When trying to cast/convert a {@code Type} to another {@code Type},
   * it is necessary to pre-check whether the cast operation is needed.
   * We summarize general exceptions, including:
   * 当尝试将一个 {@code Type} 转换为另一个 {@code Type} 时，需要预先检查是否需要进行类型转换操作
   * 总结了一般不需要转换的情况：
   *
   * <ol>
   *   <li>target Type {@code toType} equals with original Type {@code fromType}</li>
   *   <li>target Type can be assignable from original Type</li>
   *   <li>target Type is an instance of {@code RecordType},
   *   since the mapping Java Class might not generated yet</li>
   * </ol>
   *
   * @param fromType original type - 原始类型（源类型）
   * @param toType   target type - 目标类型
   * @return Whether a cast operation is needed - 是否需要进行类型转换操作
   */
  public static boolean needTypeCast(Type fromType, Type toType) { // 静态公共方法：检查是否需要进行类型转换
    return !(fromType.equals(toType) // 检查源类型是否等于目标类型
        || toType instanceof RecordType // 或目标类型是否为 RecordType（记录类型，可能尚未生成对应的 Java 类）
        || isAssignableFrom(toType, fromType)); // 或目标类型是否可以从源类型赋值（类型兼容）
  }

  public static PseudoField field(final Field field) { // 静态公共方法：将反射字段包装为伪字段
    return new ReflectedPseudoField(field); // 创建并返回 ReflectedPseudoField 实例
  }

  static Type arrayType(Type type, int dimension) { // 静态方法：创建指定维度的数组类型
    for (int i = 0; i < dimension; i++) { // 循环 dimension 次
      type = arrayType(type); // 每次循环增加一维
    }
    return type; // 返回最终的数组类型
  }

  static Type arrayType(Type type) { // 静态方法：创建一维数组类型
    if (type instanceof Class) { // 检查类型是否为 Class 对象
      Class<?> clazz = (Class<?>) type; // 强制转换为 Class

      // REVIEW: Is there a way to do this without creating an instance?
      //   We just need the inverse of Class.getComponentType().
      // 注释：审查：有没有办法在不创建实例的情况下做到这一点？我们只需要 Class.getComponentType() 的逆操作。
      return Array.newInstance(clazz, 0).getClass(); // 创建一个长度为 0 的数组实例，然后获取其 Class 对象（数组类型）
    }
    return new ArrayType(type); // 如果不是 Class，创建 Calcite 的 ArrayType 实例
  }

  public static Type stripGenerics(Type type) { // 静态公共方法：去除类型的泛型信息（类型擦除）
    if (type instanceof GenericArrayType) { // 检查类型是否为泛型数组类型
      final Type componentType = // 获取泛型数组的组件类型
          ((GenericArrayType) type).getGenericComponentType();
      return new ArrayType(stripGenerics(componentType)); // 递归调用，去除组件类型的泛型，然后创建新的数组类型
    } else if (type instanceof ParameterizedType) { // 检查类型是否为参数化类型（带泛型的类型）
      return ((ParameterizedType) type).getRawType(); // 返回原始类型（擦除泛型参数）
    } else {
      return type; // 如果不是泛型类型，直接返回原类型
    }
  }

  /** Implementation of {@link ParameterizedType}. */
  static class ParameterizedTypeImpl implements ParameterizedType { // 静态内部类：ParameterizedType 接口的实现类，用于表示参数化类型（泛型类型）
    private final Type rawType; // 成员变量：原始类型（擦除泛型后的类，如 List<String> 的 rawType 是 List）
    private final List<Type> typeArguments; // 成员变量：类型参数列表（如 List<String> 的 typeArguments 是 [String]）
    private final @Nullable Type ownerType; // 成员变量：所有者类型（如果是内部类，则为外部类的类型；否则为 null）

    ParameterizedTypeImpl(Type rawType, List<Type> typeArguments, // 构造函数：创建参数化类型实例
        @Nullable Type ownerType) { // 参数：原始类型、类型参数列表、所有者类型
      super(); // 调用父类构造函数
      this.rawType = requireNonNull(rawType, "rawType"); // 设置原始类型，非空检查
      this.typeArguments = ImmutableList.copyOf(typeArguments); // 创建类型参数的不可变副本
      this.ownerType = ownerType; // 设置所有者类型
    }

    @Override public String toString() { // 重写 toString 方法：返回类型的字符串表示
      final StringBuilder buf = new StringBuilder(); // 创建字符串构建器
      buf.append(className(rawType)); // 添加原始类型名
      buf.append("<"); // 添加泛型参数开始标记
      int i = 0; // 初始化索引
      for (Type typeArgument : typeArguments) { // 遍历所有类型参数
        if (i++ > 0) { // 如果不是第一个参数
          buf.append(", "); // 添加逗号分隔符
        }
        buf.append(className(typeArgument)); // 添加类型参数名
      }
      buf.append(">"); // 添加泛型参数结束标记
      return buf.toString(); // 返回构建的字符串
    }

    @Override public Type[] getActualTypeArguments() { // 实现 ParameterizedType 接口方法：获取实际的类型参数数组
      return typeArguments.toArray(new Type[0]); // 将类型参数列表转换为数组并返回
    }

    @Override public Type getRawType() { // 实现 ParameterizedType 接口方法：获取原始类型
      return rawType; // 返回原始类型
    }

    @Override public @Nullable Type getOwnerType() { // 实现 ParameterizedType 接口方法：获取所有者类型
      return ownerType; // 返回所有者类型（可能为 null）
    }
  }

  /**
   * Base class for record-like types that do not mapped to (currently
   * loaded) Java {@link Class} objects. Gives the opportunity to generate
   * code that references temporary types, then generate classes for those
   * types along with the code that uses them.
   * 记录类型接口：表示类似记录的类型，这些类型不映射到（当前加载的）Java Class 对象
   * 提供了生成引用临时类型的代码的机会，然后为这些类型生成类以及使用它们的代码
   * 这种机制允许在代码生成过程中引用尚未实际生成的类
   */
  public interface RecordType extends Type { // 公共接口：记录类型，继承自 Type 接口
    List<RecordField> getRecordFields(); // 方法：获取记录字段列表

    String getName(); // 方法：获取记录类型的名称
  }

  /**
   * Field that belongs to a record.
   * 记录字段接口：表示属于记录的字段
   * 记录字段是记录类型中的命名成员，类似于类的字段
   */
  public interface RecordField extends PseudoField { // 公共接口：记录字段，继承自 PseudoField 接口
    boolean nullable(); // 方法：检查字段是否可以为 null
  }

  /**
   * Array type.
   * 数组类型类：表示 Calcite 中的数组类型
   * 该类封装了数组的组件类型、可空性和最大基数等属性
   */
  public static class ArrayType implements Type { // 公共静态类：数组类型，实现 Type 接口
    private final Type componentType; // 成员变量：组件类型（数组元素的类型）
    private final boolean componentIsNullable; // 成员变量：组件是否可空（数组元素是否可以为 null）
    private final long maximumCardinality; // 成员变量：最大基数（数组的最大长度，-1 表示无限制）

    public ArrayType(Type componentType, boolean componentIsNullable, // 构造函数：创建数组类型实例
        long maximumCardinality) { // 参数：组件类型、组件是否可空、最大基数
      this.componentType = componentType; // 设置组件类型
      this.componentIsNullable = componentIsNullable; // 设置组件是否可空
      this.maximumCardinality = Math.max(maximumCardinality, -1L); // 设置最大基数，确保不小于 -1
    }

    public ArrayType(Type componentType) { // 构造函数：创建数组类型实例（使用默认值）
      this(componentType, !Primitive.is(componentType), -1L); // 调用完整构造函数，组件可空性根据是否为基本类型决定，最大基数为 -1（无限制）
    }

    /** Returns the type of elements in the array. */
    public Type getComponentType() { // 公共方法：获取数组元素的类型
      return componentType; // 返回组件类型
    }

    /** Returns whether elements in the array may be null. */
    public boolean componentIsNullable() { // 公共方法：检查数组元素是否可以为 null
      return componentIsNullable; // 返回组件是否可空
    }

    /** Returns the maximum cardinality; -1 if there is no maximum. */
    public long maximumCardinality() { // 公共方法：获取数组的最大基数
      return maximumCardinality; // 返回最大基数（-1 表示无限制）
    }
  }

  /**
   * Map type.
   * 映射类型类：表示 Calcite 中的映射（Map）类型
   * 该类封装了映射的键类型、值类型及其可空性等属性
   */
  public static class MapType implements Type { // 公共静态类：映射类型，实现 Type 接口
    private final Type keyType; // 成员变量：键的类型
    private final boolean keyIsNullable; // 成员变量：键是否可空
    private final Type valueType; // 成员变量：值的类型
    private final boolean valueIsNullable; // 成员变量：值是否可空

    public MapType(Type keyType, boolean keyIsNullable, // 构造函数：创建映射类型实例
        Type valueType, boolean valueIsNullable) { // 参数：键类型、键是否可空、值类型、值是否可空
      this.keyType = keyType; // 设置键类型
      this.keyIsNullable = keyIsNullable; // 设置键是否可空
      this.valueType = valueType; // 设置值类型
      this.valueIsNullable = valueIsNullable; // 设置值是否可空
    }

    /** Returns the type of keys. */
    public Type getKeyType() { // 公共方法：获取键的类型
      return keyType; // 返回键类型
    }

    /** Returns whether keys may be null. */
    public boolean keyIsNullable() { // 公共方法：检查键是否可以为 null
      return keyIsNullable; // 返回键是否可空
    }

    /** Returns the type of values. */
    public Type getValueType() { // 公共方法：获取值的类型
      return valueType; // 返回值类型
    }

    /** Returns whether values may be null. */
    public boolean valueIsNullable() { // 公共方法：检查值是否可以为 null
      return valueIsNullable; // 返回值是否可空
    }

  }
}
