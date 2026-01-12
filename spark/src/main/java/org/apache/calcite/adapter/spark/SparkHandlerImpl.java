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
package org.apache.calcite.adapter.spark; // 声明包名，该类属于org.apache.calcite.adapter.spark包，提供了Calcite与Spark集成的适配器功能

import org.apache.calcite.adapter.enumerable.EnumerableRules; // 导入EnumerableRules类，包含可枚举规则，用于规则注册和移除
import org.apache.calcite.config.CalciteSystemProperty; // 导入Calcite系统属性配置类，用于访问调试等系统属性
import org.apache.calcite.jdbc.CalcitePrepare; // 导入CalcitePrepare类，定义了SparkHandler接口，该接口需要被实现
import org.apache.calcite.linq4j.tree.ClassDeclaration; // 导入ClassDeclaration类，表示LINQ4j中的类声明，用于代码生成
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner接口，表示关系代数优化器，用于执行优化规则
import org.apache.calcite.plan.RelOptRule; // 导入RelOptRule类，表示优化规则，用于转换和优化关系表达式
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数表达式树中的节点
import org.apache.calcite.runtime.ArrayBindable; // 导入ArrayBindable接口，表示可绑定的数组，用于数据绑定和执行
import org.apache.calcite.util.Util; // 导入Util工具类，提供各种实用方法如日历操作
import org.apache.calcite.util.javac.JaninoCompiler; // 导入JaninoCompiler类，用于在运行时编译Java源代码

import org.apache.spark.SparkConf; // 导入SparkConf类，用于配置Spark应用程序的各种参数
import org.apache.spark.api.java.JavaSparkContext; // 导入JavaSparkContext类，是Spark功能的主要入口点

import java.io.File; // 导入File类，用于文件和目录路径的抽象表示
import java.io.Serializable; // 导入Serializable接口，标记类可以被序列化，用于Spark分布式计算
import java.lang.reflect.Constructor; // 导入Constructor类，用于通过反射获取类的构造方法
import java.lang.reflect.InvocationTargetException; // 导入InvocationTargetException类，处理反射调用方法时抛出的异常
import java.util.Calendar; // 导入Calendar类，用于日期和时间操作，生成唯一的类名
import java.util.concurrent.atomic.AtomicInteger; // 导入AtomicInteger类，提供原子操作的整数，用于生成唯一的类ID

/**
 * Implementation of
 * {@link org.apache.calcite.jdbc.CalcitePrepare.SparkHandler}. Gives the core
 * Calcite engine access to rules that only exist in the Spark module.
 */ // 该类实现了CalcitePrepare.SparkHandler接口，为Calcite核心引擎提供对Spark模块中特有规则的访问
public class SparkHandlerImpl implements CalcitePrepare.SparkHandler { // 定义SparkHandlerImpl类，实现SparkHandler接口，作为Calcite与Spark集成的处理器
  private final HttpServer classServer; // HTTP服务器实例，用于在运行时提供编译后的类文件给Spark执行器下载
  private final AtomicInteger classId; // 原子整数计数器，用于生成唯一的类名ID，避免类名冲突
  private final SparkConf sparkConf = // 定义Spark配置对象，用于配置Spark应用程序的各种参数
      new SparkConf().set("spark.driver.bindAddress", "localhost"); // 创建新的SparkConf实例并设置驱动程序绑定地址为本地主机
  private final JavaSparkContext sparkContext = // 定义Java Spark上下文对象，是Spark功能的主要入口点
      new JavaSparkContext("local[1]", "calcite", sparkConf); // 创建本地模式的Spark上下文，使用1个线程，应用名为calcite

  /** Thread-safe holder. */ // 线程安全的持有者类，用于实现延迟初始化的单例模式
  private static class Holder { // 定义静态内部类Holder，用于线程安全地持有SparkHandlerImpl单例实例
    private static final SparkHandlerImpl INSTANCE = new SparkHandlerImpl(); // 定义静态常量INSTANCE，持有SparkHandlerImpl的唯一实例，在类加载时初始化
  }

  private static final File CLASS_DIR = new File("build/sparkServer/classes"); // 定义常量CLASS_DIR，指定编译后类文件的存储目录路径

  /** Creates a SparkHandlerImpl. */ // 私有构造方法，创建SparkHandlerImpl实例
  private SparkHandlerImpl() { // 私有构造方法，确保只能通过Holder类创建单例实例
    if (!CLASS_DIR.isDirectory() && !CLASS_DIR.mkdirs()) { // 检查CLASS_DIR是否不是目录，并且尝试创建目录失败
      System.err.println("Unable to create temporary folder " + CLASS_DIR); // 如果创建目录失败，输出错误信息到标准错误流
    }
    classServer = new HttpServer(CLASS_DIR); // 创建HTTP服务器实例，监听CLASS_DIR目录，用于提供类文件下载服务

    // Start the classServer and store its URI in a spark system property
    // (which will be passed to executors so that they can connect to it)
    classServer.start(); // 启动HTTP服务器，开始监听并提供类文件下载服务
    System.setProperty("spark.repl.class.uri", classServer.uri()); // 将HTTP服务器的URI设置为Spark系统属性，使执行器能够连接并下载类文件

    // Generate a starting point for class names that is unlikely to clash with
    // previous classes. A better solution would be to clear the class directory
    // on startup.
    final Calendar calendar = Util.calendar(); // 获取当前日历对象，用于生成基于时间的唯一类名
    classId = // 初始化原子计数器classId，生成一个基于当前时间的起始值
        new AtomicInteger(calendar.get(Calendar.HOUR_OF_DAY) * 10000 // 使用小时数乘以10000作为高位，确保类名的唯一性
            + calendar.get(Calendar.MINUTE) * 100 // 加上分钟数乘以100作为中间位
            + calendar.get(Calendar.SECOND)); // 加上秒数作为低位，生成最终的初始值
  }

  /** Creates a SparkHandlerImpl, initializing on first call. Calcite-core calls
   * this via reflection. */ // 创建SparkHandlerImpl实例，在首次调用时初始化，Calcite核心通过反射调用此方法
  @SuppressWarnings("UnusedDeclaration") // 抑制未使用声明的警告，因为该方法通过反射调用
  public static CalcitePrepare.SparkHandler instance() { // 公共静态方法，返回SparkHandler单例实例
    return Holder.INSTANCE; // 返回Holder类中持有的唯一SparkHandlerImpl实例
  }

  @Override public RelNode flattenTypes(RelOptPlanner planner, RelNode rootRel, // 重写flattenTypes方法，用于扁平化关系表达式树中的类型
      boolean restructure) { // restructure参数指示是否需要重构关系表达式树
    RelNode root2 = // 定义中间变量root2，存储第一次trait转换后的关系节点
        planner.changeTraits(rootRel, // 使用优化器改变关系节点的trait集合
            rootRel.getTraitSet().plus(SparkRel.CONVENTION).simplify()); // 添加Spark约定并简化trait集合，转换为Spark关系节点
    return planner.changeTraits(root2, rootRel.getTraitSet().simplify()); // 再次转换trait集合，恢复原始trait但保持简化状态，返回最终的关系节点
  }

  @Override public void registerRules(RuleSetBuilder builder) { // 重写registerRules方法，向规则集构建器注册Spark特定的优化规则
    for (RelOptRule rule : SparkRules.rules()) { // 遍历SparkRules中定义的所有规则
      builder.addRule(rule); // 将每个Spark规则添加到规则集构建器中
    }
    builder.removeRule(EnumerableRules.ENUMERABLE_VALUES_RULE); // 从规则集构建器中移除可枚举值规则，因为Spark有自己的实现
  }

  @Override public Object sparkContext() { // 重写sparkContext方法，返回Spark上下文对象
    return sparkContext; // 返回JavaSparkContext实例，供外部使用Spark功能
  }

  @Override public boolean enabled() { // 重写enabled方法，检查Spark处理器是否启用
    return true; // 返回true表示Spark处理器始终启用
  }

  @Override public ArrayBindable compile(ClassDeclaration expr, String s) { // 重写compile方法，编译类声明并返回可绑定的数组对象
    final String className = "CalciteProgram" + classId.getAndIncrement(); // 生成唯一的类名，使用前缀"CalciteProgram"加上递增的ID
    final String classFileName = className + ".java"; // 生成Java源文件名，类名加上.java扩展名
    String source = "public class " + className + "\n" // 构建Java源代码字符串，定义公共类
        + "    implements " + ArrayBindable.class.getName() // 实现ArrayBindable接口
        + ", " + Serializable.class.getName() // 实现Serializable接口，支持序列化
        + " {\n" // 开始类体
        + s + "\n" // 插入传入的方法实现代码
        + "}\n"; // 结束类体

    if (CalciteSystemProperty.DEBUG.value()) { // 检查是否启用了调试模式
      Util.debugCode(System.out, source); // 如果启用调试，将生成的源代码输出到标准输出
    }

    JaninoCompiler compiler = new JaninoCompiler(); // 创建Janino编译器实例，用于在内存中编译Java代码
    compiler.getArgs().setDestdir(CLASS_DIR.getAbsolutePath()); // 设置编译器输出目录为CLASS_DIR的绝对路径
    compiler.getArgs().setSource(source, classFileName); // 设置编译器源代码内容和文件名
    compiler.getArgs().setFullClassName(className); // 设置编译器的完整类名
    compiler.compile(); // 执行编译操作，将源代码编译为字节码
    try { // 开始异常处理块
      @SuppressWarnings("unchecked") // 抑制未检查的类型转换警告
      final Class<ArrayBindable> clazz = // 定义clazz变量，存储加载的类对象
          (Class<ArrayBindable>) compiler.getClassLoader().loadClass(className); // 使用编译器的类加载器加载编译后的类，并强制转换为ArrayBindable类型
      final Constructor<ArrayBindable> constructor = clazz.getConstructor(); // 获取类的无参构造方法
      return constructor.newInstance(); // 通过反射创建类的实例并返回
    } catch (ClassNotFoundException | InstantiationException // 捕获类未找到异常或实例化异常
        | IllegalAccessException | NoSuchMethodException // 捕获非法访问异常或无此方法异常
        | InvocationTargetException e) { // 捕获调用目标异常
      throw new RuntimeException(e); // 将捕获的异常包装为运行时异常并抛出
    }
  }
} // 类定义结束
