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
package org.apache.calcite.adapter.arrow;  // 定义包名，该类属于Arrow适配器包

import org.apache.calcite.schema.Table;  // 导入Calcite的Table接口，表示数据表
import org.apache.calcite.schema.impl.AbstractSchema;  // 导入Calcite的抽象Schema基类，用于实现自定义Schema
import org.apache.calcite.util.Sources;  // 导入Calcite的工具类，用于处理文件源
import org.apache.calcite.util.Util;  // 导入Calcite的工具类，提供通用工具方法

import org.apache.arrow.memory.RootAllocator;  // 导入Arrow的根内存分配器，用于管理Arrow的内存分配
import org.apache.arrow.vector.ipc.ArrowFileReader;  // 导入Arrow的文件读取器，用于读取Arrow格式文件
import org.apache.arrow.vector.ipc.SeekableReadChannel;  // 导入Arrow的可定位读取通道，支持随机访问

import com.google.common.base.Suppliers;  // 导入Guava的Suppliers工具类，用于延迟加载和缓存
import com.google.common.collect.ImmutableMap;  // 导入Guava的不可变Map类，用于创建不可变的映射

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入空值检查注解，用于标记可能为null的返回值
import org.slf4j.Logger;  // 导入SLF4J的Logger接口，用于日志记录
import org.slf4j.LoggerFactory;  // 导入SLF4J的LoggerFactory工厂类，用于创建Logger实例

import java.io.File;  // 导入Java的File类，用于文件和目录操作
import java.io.FileInputStream;  // 导入Java的FileInputStream类，用于读取文件输入流
import java.io.FileNotFoundException;  // 导入Java的FileNotFoundException异常类，用于处理文件未找到异常
import java.util.HashMap;  // 导入Java的HashMap类，用于创建哈希映射
import java.util.Locale;  // 导入Java的Locale类，用于区域设置和字符串大小写转换
import java.util.Map;  // 导入Java的Map接口，表示键值对映射
import java.util.function.Supplier;  // 导入Java的Supplier函数式接口，用于延迟计算

import static java.util.Objects.requireNonNull;  // 导入Java的Objects工具类的requireNonNull方法，用于空值检查

/**
 * Schema mapped onto a set of Arrow files.  // 类注释：这是一个映射到一组Arrow文件的Schema类
 * 
 * 该类是Calcite框架中用于Apache Arrow数据源的Schema实现。
 * Schema是Calcite中用于组织表的逻辑容器，类似于数据库中的schema或命名空间。
 * Arrow是Apache基金会的一个高性能列式内存格式，专门用于高效的数据处理和分析。
 * 
 * 该类的主要功能：
 * 1. 继承自AbstractSchema，实现了Calcite的Schema接口
 * 2. 从指定的目录中扫描所有的.arrow文件
 * 3. 为每个.arrow文件创建对应的ArrowTable对象
 * 4. 提供表名到ArrowTable的映射关系
 * 5. 使用延迟加载机制，只在第一次访问时才扫描目录并创建表映射
 * 
 * 使用场景：
 * 当需要使用Calcite查询存储在Arrow格式文件中的数据时，
 * 需要创建ArrowSchema实例，并将其注册到Calcite的SchemaFactory中。
 */
class ArrowSchema extends AbstractSchema {  // 定义ArrowSchema类，继承自AbstractSchema抽象类

  private static final Logger LOGGER = LoggerFactory.getLogger(ArrowSchema.class);  // 创建静态Logger实例，用于记录日志信息
  private final Supplier<Map<String, Table>> tableMapSupplier;  // 延迟加载的表映射提供者，使用Supplier接口实现懒加载，key是表名，value是Table对象

  /**
   * Creates an Arrow schema.  // 方法注释：创建一个Arrow Schema实例
   *
   * @param baseDirectory Base directory to look for relative files  // 参数说明：基础目录，用于查找相对路径的Arrow文件
   * 
   * 构造方法说明：
   * 1. 接收一个File对象作为参数，表示Arrow文件所在的基础目录
   * 2. 使用requireNonNull方法检查baseDirectory参数是否为null，如果为null则抛出NullPointerException
   * 3. 创建一个延迟加载的Supplier，使用Guava的Suppliers.memoize方法包装
   * 4. memoize方法确保deduceTableMap方法只被调用一次，结果会被缓存
   * 5. 这样可以避免每次访问表时都重新扫描目录，提高性能
   */
  ArrowSchema(File baseDirectory) {  // 构造方法，接收基础目录作为参数
    requireNonNull(baseDirectory, "baseDirectory");  // 检查baseDirectory参数不为null，否则抛出异常
    this.tableMapSupplier =  // 初始化tableMapSupplier成员变量
        Suppliers.memoize(() -> deduceTableMap(baseDirectory));  // 使用memoize包装deduceTableMap方法调用，实现延迟加载和结果缓存
  }

  /**
   * Looks for a suffix on a string and returns  // 方法注释：查找字符串的后缀并返回
   * either the string with the suffix removed  // 返回值说明：移除后缀后的字符串
   * or the original string.  // 或者返回原始字符串（如果没有该后缀）
   * 
   * 方法详细说明：
   * 这是一个静态工具方法，用于从字符串中移除指定的后缀。
   * 
   * 工作原理：
   * 1. 调用trimOrNull方法尝试移除后缀
   * 2. 如果trimOrNull返回非null值（即字符串包含该后缀），则返回移除后缀后的字符串
   * 3. 如果trimOrNull返回null（即字符串不包含该后缀），则返回原始字符串
   * 
   * 使用场景：
   * 主要用于从Arrow文件名中移除".arrow"后缀，得到表名。
   * 例如：将"employees.arrow"转换为"employees"
   * 
   * @param s 要处理的字符串，通常是文件名
   * @param suffix 要移除的后缀字符串，例如".arrow"
   * @return 移除后缀后的字符串，或原始字符串（如果没有该后缀）
   */
  private static String trim(String s, String suffix) {  // 定义静态方法trim，接收字符串和后缀作为参数
    String trimmed = trimOrNull(s, suffix);  // 调用trimOrNull方法尝试移除后缀，结果可能为null
    return trimmed != null ? trimmed : s;  // 如果trimmed不为null则返回trimmed，否则返回原始字符串s
  }

  /**
   * Looks for a suffix on a string and returns  // 方法注释：查找字符串的后缀并返回
   * either the string with the suffix removed  // 返回值说明：移除后缀后的字符串
   * or null.  // 或者返回null（如果没有该后缀）
   * 
   * 方法详细说明：
   * 这是一个静态工具方法，与trim方法类似，但返回值不同。
   * 该方法用于判断字符串是否以指定后缀结尾，如果是则移除后缀返回，否则返回null。
   * 
   * 工作原理：
   * 1. 使用String的endsWith方法检查字符串s是否以suffix结尾
   * 2. 如果以suffix结尾，则使用substring方法截取从0到长度减去后缀长度的子字符串
   * 3. 如果不以suffix结尾，则返回null
   * 
   * 使用场景：
   * 作为trim方法的辅助方法，提供更明确的返回值语义。
   * 返回null可以明确表示"没有找到后缀"，而返回字符串表示"找到了并已移除"。
   * 
   * @param s 要处理的字符串，通常是文件名
   * @param suffix 要移除的后缀字符串，例如".arrow"
   * @return 移除后缀后的字符串，或null（如果没有该后缀）
   */
  private static @Nullable String trimOrNull(String s, String suffix) {  // 定义静态方法trimOrNull，使用@Nullable注解标记返回值可能为null
    return s.endsWith(suffix)  // 检查字符串s是否以suffix结尾
        ? s.substring(0, s.length() - suffix.length())  // 如果是，则截取从0到（长度减去后缀长度）的子字符串，即移除后缀
        : null;  // 如果不是，则返回null
  }

  /**
   * 获取表映射的方法，重写父类AbstractSchema的方法
   * 
   * 方法详细说明：
   * 这是Calcite Schema接口的核心方法，用于获取该Schema中包含的所有表。
   * Calcite在查询解析和优化过程中会调用此方法来获取可用的表。
   * 
   * 工作原理：
   * 1. 调用tableMapSupplier.get()方法获取表映射
   * 2. 由于tableMapSupplier使用了memoize包装，deduceTableMap方法只会在第一次调用时执行
   * 3. 后续调用会直接返回缓存的表映射，提高性能
   * 
   * 返回值说明：
   * 返回一个不可变的Map，其中：
   * - key：表名（字符串类型，大写）
   * - value：Table对象（ArrowTable实例）
   * 
   * @return 表名到Table对象的映射
   */
  @Override protected Map<String, Table> getTableMap() {  // 重写父类的getTableMap方法，使用@Override注解标记
    return tableMapSupplier.get();  // 调用Supplier的get方法，获取缓存的表映射
  }

  /**
   * 推断并创建表映射的静态方法
   * 
   * 方法详细说明：
   * 这是ArrowSchema的核心方法，负责扫描指定目录中的所有Arrow文件，
   * 并为每个文件创建对应的ArrowTable对象，最后返回表名到表的映射。
   * 
   * 工作流程：
   * 1. 扫描基础目录，查找所有以".arrow"结尾的文件
   * 2. 如果目录不存在或无法访问，记录日志并返回空映射
   * 3. 对于每个Arrow文件：
   *    a. 创建FileInputStream读取文件
   *    b. 创建SeekableReadChannel封装文件通道，支持随机访问
   *    c. 创建RootAllocator管理Arrow内存分配
   *    d. 创建ArrowFileReader读取Arrow文件内容
   *    e. 从文件名中提取表名（移除.arrow后缀并转为大写）
   *    f. 创建ArrowTable对象并包装ArrowFileReader
   *    g. 将表名和ArrowTable对象加入映射
   * 4. 返回不可变的表映射
   * 
   * 技术细节：
   * - ArrowFileReader是Arrow IPC格式的读取器，可以高效读取Arrow文件
   * - RootAllocator是Arrow的内存管理器，负责分配和释放Arrow向量使用的内存
   * - SeekableReadChannel实现了Arrow的SeekableByteChannel接口，支持随机访问
   * - 表名转换为大写是为了匹配SQL的标识符大小写不敏感特性（大多数数据库默认行为）
   * 
   * @param baseDirectory 基础目录，用于扫描Arrow文件
   * @return 表名到Table对象的不可变映射
   */
  private static Map<String, Table> deduceTableMap(File baseDirectory) {  // 定义静态方法deduceTableMap，接收基础目录作为参数
    File[] files = baseDirectory.listFiles((dir, name) -> name.endsWith(".arrow"));  // 列出目录中所有以".arrow"结尾的文件，使用Lambda表达式作为过滤器
    if (files == null) {  // 检查files数组是否为null（目录不存在或无法访问）
      LOGGER.info("directory " + baseDirectory + " not found");  // 记录信息日志，提示目录未找到
      return ImmutableMap.of();  // 返回空的不可变Map
    }  // 结束if语句

    final Map<String, Table> tables = new HashMap<>();  // 创建HashMap用于存储表名到Table对象的映射
    for (File file : files) {  // 遍历所有Arrow文件
      final File arrowFile = new File(Sources.of(file).path());  // 使用Sources工具类规范化文件路径，创建新的File对象
      final FileInputStream fileInputStream;  // 声明FileInputStream变量，用于读取文件
      try {  // 开始try块，用于捕获文件未找到异常
        fileInputStream = new FileInputStream(arrowFile);  // 创建FileInputStream，打开Arrow文件进行读取
      } catch (FileNotFoundException e) {  // 捕获FileNotFoundException异常
        throw Util.toUnchecked(e);  // 将检查型异常转换为非检查型异常并抛出，使用Calcite的Util工具方法
      }  // 结束try-catch块
      final SeekableReadChannel seekableReadChannel =  // 创建SeekableReadChannel对象
          new SeekableReadChannel(fileInputStream.getChannel());  // 使用FileInputStream的底层通道创建可定位读取通道，支持随机访问
      final RootAllocator allocator = new RootAllocator();  // 创建RootAllocator实例，用于管理Arrow的内存分配
      final ArrowFileReader arrowFileReader =  // 创建ArrowFileReader对象
          new ArrowFileReader(seekableReadChannel, allocator);  // 使用读取通道和内存分配器创建Arrow文件读取器
      final String tableName =  // 声明表名变量
          trim(file.getName(), ".arrow").toUpperCase(Locale.ROOT);  // 从文件名中移除.arrow后缀，并转为大写（使用ROOT区域设置）
      final ArrowTable table =  // 声明ArrowTable变量
          new ArrowTable(null, arrowFileReader);  // 创建ArrowTable对象，第一个参数为null（表示没有父Schema），第二个参数是ArrowFileReader
      tables.put(tableName, table);  // 将表名和ArrowTable对象加入映射
    }  // 结束for循环

    return ImmutableMap.copyOf(tables);  // 将HashMap转换为不可变的ImmutableMap并返回，防止后续修改
  }  // 结束deduceTableMap方法
}  // 结束ArrowSchema类
