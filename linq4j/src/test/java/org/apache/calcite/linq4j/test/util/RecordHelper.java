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
package org.apache.calcite.linq4j.test.util; // 定义包名，该类属于org.apache.calcite.linq4j.test.util包，是linq4j测试工具包的一部分

import org.opentest4j.TestAbortedException; // 导入TestAbortedException，用于在测试环境中跳过不支持的测试场景

import java.io.IOException; // 导入IOException，用于处理文件读写操作中的异常
import java.lang.reflect.Constructor; // 导入Constructor，用于通过反射获取类的构造方法
import java.lang.reflect.InvocationTargetException; // 导入InvocationTargetException，用于处理反射调用构造方法时的异常
import java.net.MalformedURLException; // 导入MalformedURLException，用于处理URL格式错误
import java.net.URL; // 导入URL，用于表示资源定位符，用于动态加载类
import java.net.URLClassLoader; // 导入URLClassLoader，用于从URL路径动态加载类文件
import java.nio.charset.StandardCharsets; // 导入StandardCharsets，用于指定字符编码为UTF-8
import java.nio.file.Files; // 导入Files，用于文件操作，如写入文件内容
import java.nio.file.Path; // 导入Path，用于表示文件系统中的路径
import java.util.Locale; // 导入Locale，用于本地化相关的字符串格式化
import javax.tools.JavaCompiler; // 导入JavaCompiler，用于在运行时动态编译Java源代码
import javax.tools.ToolProvider; // 导入ToolProvider，用于获取系统提供的Java编译器实例

/**
 * Helper that compiles a record instance dynamically, if compatible, and initializes instances of
 * Java records.
 */
// RecordHelper是一个辅助工具类，用于在运行时动态编译Java Record（记录）实例，并初始化这些记录对象
// Java Record是Java 14引入的特性，用于创建不可变的数据载体类
// 该类主要用于测试环境中，当JDK版本支持Record特性时，动态创建和加载Record类
// 如果JDK版本不支持Record（低于Java 14），则跳过相关测试
public class RecordHelper { // 定义RecordHelper类，这是一个工具类，提供静态方法来处理Record相关的操作

  private RecordHelper(){} // 私有构造方法，防止实例化，因为这是一个纯工具类，所有方法都是静态的

  private static final String RECORD_TEMPLATE = "public record %s(String name, int count) {}"; // 定义Record类的源代码模板，使用占位符%s表示类名，该Record包含两个字段：String类型的name和int类型的count
  private static final String JAVA_FILE_NAME_TEMPLATE = "%s.java"; // 定义Java源文件的文件名模板，使用占位符%s表示类名，例如"TestRecord.java"

  /** Creates a Java record, aborts if the JDK is non-compatible. */
  // 创建一个Java Record类的Class对象，如果当前JDK不支持Record特性，则抛出TestAbortedException跳过测试
  // 参数tempDir：临时目录路径，用于存放生成的Java源文件和编译后的class文件
  // 参数className：要创建的Record类的名称
  // 返回值：返回加载的Record类的Class对象
  public static Class<?> createRecordClass(Path tempDir, String className) { // 公共静态方法，用于创建Record类的Class对象
    if (canSupportRecords()) { // 检查当前JDK是否支持Record特性
      return compileAndLoadClass(tempDir, className); // 如果支持，则编译并加载该Record类
    } else { // 如果不支持
      throw new TestAbortedException("Records not supported"); // 抛出TestAbortedException，表示当前测试被跳过，因为JDK不支持Record
    }
  }

  static boolean canSupportRecords() { // 静态方法，检查当前JDK版本是否支持Record特性
    try { // 尝试获取Class类的isRecord方法
      Class.class.getMethod("isRecord"); // 通过反射获取Class类的isRecord方法，该方法在Java 14+中存在，用于判断一个类是否为Record
      return true; // 如果成功获取该方法，说明JDK支持Record特性，返回true
    } catch (NoSuchMethodException e) { // 如果捕获到NoSuchMethodException，说明isRecord方法不存在
      return false; // JDK不支持Record特性，返回false
    }
  }

  /** Compiles and loads a Java record dynamically. */
  // 动态编译并加载一个Java Record类
  // 参数tempDir：临时目录路径，用于存放生成的Java源文件和编译后的class文件
  // 参数className：要创建的Record类的名称
  // 返回值：返回加载的Record类的Class对象
  private static Class<?> compileAndLoadClass(Path tempDir, String className) { // 私有静态方法，用于编译并加载Record类
    createAndCompileTempClass(tempDir, className); // 首先创建并编译临时Java类文件

    try { // 尝试加载编译后的类
      return Class.forName(className, // 使用Class.forName方法加载指定名称的类
          true, // 第二个参数为true，表示初始化该类（执行静态初始化块）
          URLClassLoader.newInstance(new URL[] {tempDir.toUri().toURL() })); // 创建一个URLClassLoader，从临时目录加载类文件
    } catch (ClassNotFoundException | MalformedURLException e) { // 捕获类未找到异常或URL格式异常
      throw new IllegalArgumentException("Could not load class."); // 抛出IllegalArgumentException，表示无法加载类
    }
  }

  /** Creates a temporary Java record and compiles it. */
  // 创建一个临时的Java Record源文件并编译它
  // 参数tempDir：临时目录路径，用于存放生成的Java源文件
  // 参数className：要创建的Record类的名称
  public static void createAndCompileTempClass(Path tempDir, String className) { // 公共静态方法，用于创建并编译临时Record类
    String classSourceCode = // 定义字符串变量，用于存储生成的Record类的源代码
        String.format(Locale.ROOT, RECORD_TEMPLATE, className); // 使用String.format方法，根据模板生成Record类的源代码，Locale.ROOT确保格式化不受本地化影响
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler(); // 获取系统提供的Java编译器实例，用于在运行时编译Java源代码


    Path tempJavaClassFile = // 定义Path变量，用于表示临时Java源文件的完整路径
        tempDir.resolve(String.format(Locale.ROOT, JAVA_FILE_NAME_TEMPLATE, className)); // 使用tempDir.resolve方法，结合文件名模板，生成完整的文件路径
    try { // 尝试写入Java源文件
      Files.write(tempJavaClassFile, classSourceCode.getBytes(StandardCharsets.UTF_8)); // 将生成的Record类源代码写入到临时文件中，使用UTF-8编码
    } catch (IOException e) { // 捕获IO异常，表示文件写入失败
      throw new IllegalArgumentException("Could not write file."); // 抛出IllegalArgumentException，表示无法写入文件
    }
    compiler.run(null, null, null, tempJavaClassFile.toAbsolutePath().toString()); // 调用编译器的run方法编译Java源文件，前三个参数为null表示使用标准输入输出和错误流，最后一个参数是要编译的文件路径
  }

  /** Creates new instances of the loaded class. */
  // 创建已加载类的新实例
  // 参数clazz：要创建实例的Class对象
  // 参数nameFieldValue：Record中name字段的值
  // 参数countFieldValue：Record中count字段的值
  // 返回值：返回创建的Record实例对象
  public static Object createInstance(Class<?> clazz, String nameFieldValue, int countFieldValue) { // 公共静态方法，用于创建Record类的实例
    Constructor<?> constructor = null; // 声明Constructor变量，用于存储获取到的构造方法
    try { // 尝试获取构造方法
      constructor = clazz.getDeclaredConstructor(String.class, int.class); // 通过反射获取类中参数类型为String和int的构造方法
    } catch (NoSuchMethodException e) { // 捕获方法未找到异常
      throw new IllegalArgumentException("Could not find constructor"); // 抛出IllegalArgumentException，表示找不到指定的构造方法
    }
    try { // 尝试创建实例
      return constructor.newInstance(nameFieldValue, countFieldValue); // 使用获取到的构造方法创建新实例，传入name和count字段的值
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) { // 捕获实例化异常、访问权限异常或调用目标异常
      throw new IllegalArgumentException("Could not create instance"); // 抛出IllegalArgumentException，表示无法创建实例
    }
  }
} // RecordHelper类定义结束
