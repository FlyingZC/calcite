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
package org.apache.calcite.piglet; // 包声明，定义该类所属的包为 org.apache.calcite.piglet

import com.google.common.collect.ImmutableMap; // 导入 Google Guava 库的不可变 Map 类，用于创建不可变的映射集合

import java.lang.reflect.Method; // 导入 Java 反射 API 的 Method 类，用于表示类的方法
import java.util.HashMap; // 导入 Java 集合框架的 HashMap 类，用于创建可变的哈希映射
import java.util.Locale; // 导入 Java 国际化 API 的 Locale 类，用于处理地区相关的字符串转换
import java.util.Map; // 导入 Java 集合框架的 Map 接口，表示键值对映射

import static org.apache.calcite.util.ReflectUtil.isPublic; // 静态导入 ReflectUtil 工具类的 isPublic 方法，用于检查方法是否为 public

/**
 * Utility class to find the implementation method object for a given Pig UDF
 * class. // 工具类，用于查找给定 Pig UDF（用户定义函数）类的实现方法对象。Pig 是 Apache 的一个数据分析平台，UDF 是用户自定义函数，Calcite 需要通过反射找到这些 UDF 的具体实现方法来生成可执行代码
 */
class PigUdfFinder { // 定义 PigUdfFinder 类，这是一个工具类，用于查找 Pig UDF 的实现方法
  /**
   * For Pig UDF classes where the "exec" method is declared in parent class,
   * the Calcite enumerable engine will generate incorrect Java code that
   * instantiates an object of the parent class, not object of the actual UDF
   * class. If the parent class is an abstract class, the auto-generated code
   * failed to compile (we can not instantiate an object of an abstract class).
   * // 对于那些 "exec" 方法在父类中声明的 Pig UDF 类，Calcite 的可枚举引擎会生成错误的 Java 代码，它会实例化父类的对象而不是实际的 UDF 类对象。如果父类是抽象类，自动生成的代码将无法编译（因为不能实例化抽象类的对象）
   *
   * <p>Workaround is to write a wrapper for such UDFs to instantiate the
   * correct UDF object. See method {@link PigUdfs#bigdecimalsum} as an example
   * and add others if needed.
   * // 解决方案是为这些 UDF 编写包装器来实例化正确的 UDF 对象。可以参考 {@link PigUdfs#bigdecimalsum} 方法作为示例，如果需要可以添加其他包装器方法
   */
  private final ImmutableMap<String, Method> udfWrapper; // 定义一个不可变的映射，键是 UDF 类的简单名称（小写），值是对应的包装器方法对象。这个映射用于存储需要特殊处理的 UDF 包装器方法，解决 exec 方法在父类中声明导致的实例化问题

  PigUdfFinder() { // 构造方法，初始化 PigUdfFinder 对象，构建 UDF 包装器映射
    final Map<String, Method> map = new HashMap<>(); // 创建一个可变的 HashMap 用于临时存储包装器方法
    for (Method method : PigUdfs.class.getMethods()) { // 遍历 PigUdfs 类的所有公共方法（包括继承的方法）
      if (isPublic(method) // 检查方法是否为 public 访问修饰符
          && method.getReturnType() != Method.class) { // 检查方法的返回类型不是 Method.class（排除返回 Method 对象的方法）
        map.put(method.getName(), method); // 将方法名称（作为键）和方法对象（作为值）存入映射中
      }
    }
    udfWrapper = ImmutableMap.copyOf(map); // 将可变 HashMap 转换为不可变的 ImmutableMap 并赋值给 udfWrapper 成员变量，确保映射内容不会被修改
  }

  /**
   * Finds the implementation method object for a given Pig UDF class.
   * // 查找给定 Pig UDF 类的实现方法对象。这个方法会尝试从多个位置查找 exec 方法，包括包装器映射、类声明的方法和继承的方法
   *
   * @param clazz The Pig UDF class
   * // 参数：clazz - 要查找的 Pig UDF 类的 Class 对象
   *
   * @throws IllegalArgumentException if not found
   * // 异常：如果找不到 exec 方法，抛出 IllegalArgumentException 异常
   */
  Method findPigUdfImplementationMethod(Class clazz) { // 定义查找 Pig UDF 实现方法的方法，参数是 UDF 类的 Class 对象，返回找到的 Method 对象
    // Find implementation method in the wrapper map
    // 在包装器映射中查找实现方法
    Method returnedMethod = // 声明一个 Method 变量用于存储找到的方法
        udfWrapper.get(clazz.getSimpleName().toLowerCase(Locale.US)); // 使用 UDF 类的简单名称（转换为小写，使用 US 地区设置）作为键从 udfWrapper 映射中查找对应的包装器方法
    if (returnedMethod != null) { // 如果在包装器映射中找到了方法
      return returnedMethod; // 直接返回找到的包装器方法
    }

    // Find exec method in the declaring class
    // 在声明类中查找 exec 方法
    returnedMethod = findExecMethod(clazz.getDeclaredMethods()); // 调用 findExecMethod 方法，在类的声明方法中（不包括继承的方法）查找名为 "exec" 的方法
    if (returnedMethod != null) { // 如果在声明类中找到了 exec 方法
      return returnedMethod; // 返回找到的 exec 方法
    }

    // Find exec method in all parent classes.
    // 在所有父类中查找 exec 方法
    returnedMethod = findExecMethod(clazz.getMethods()); // 调用 findExecMethod 方法，在类的所有公共方法中（包括继承的方法）查找名为 "exec" 的方法
    if (returnedMethod != null) { // 如果在所有公共方法中找到了 exec 方法
      return returnedMethod; // 返回找到的 exec 方法
    }

    throw new IllegalArgumentException( // 如果在所有位置都找不到 exec 方法，抛出 IllegalArgumentException 异常
        "Could not find 'exec' method for PigUDF class of " + clazz.getName()); // 异常消息包含 UDF 类的完整名称，提示找不到 exec 方法
  }

  /**
   * Finds "exec" method from a given array of methods.
   * // 从给定的方法数组中查找名为 "exec" 的方法。Pig UDF 的核心方法是 exec，这个方法负责实际的函数执行逻辑
   */
  private static Method findExecMethod(Method[] methods) { // 定义静态方法，从方法数组中查找 exec 方法，参数是方法数组，返回找到的 Method 对象
    if (methods == null) { // 检查方法数组是否为 null
      return null; // 如果为 null，直接返回 null
    }

    Method returnedMethod = null; // 声明一个 Method 变量，初始值为 null，用于存储找到的 exec 方法
    for (Method method : methods) { // 遍历方法数组中的每个方法
      if (method.getName().equals("exec")) { // 检查方法名称是否为 "exec"
        // There may be two methods named "exec", one of them just returns a
        // Java object. We will need to look for the other one if existing.
        // 可能存在两个名为 "exec" 的方法，其中一个只返回 Java 对象（Object 类型）。如果存在另一个方法，我们需要找到那个非 Object 返回类型的方法，因为那个才是真正的实现方法
        if (method.getReturnType() != Object.class) { // 检查方法的返回类型是否不是 Object.class
          return method; // 如果返回类型不是 Object，说明找到了真正的 exec 方法，直接返回
        } else { // 如果返回类型是 Object.class
          returnedMethod = method; // 暂时保存这个方法作为备选，继续查找是否有更好的方法
        }
      }
    }
    return returnedMethod; // 返回找到的 exec 方法（可能是非 Object 返回类型的方法，也可能是唯一找到的 Object 返回类型的方法）
  }
}
