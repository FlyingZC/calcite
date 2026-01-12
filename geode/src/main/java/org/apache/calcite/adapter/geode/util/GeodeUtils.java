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
package org.apache.calcite.adapter.geode.util; // Apache Calcite Geode适配器工具类所在包

import org.apache.calcite.avatica.util.DateTimeUtils; // Avatica日期时间工具类，用于日期时间转换
import org.apache.calcite.linq4j.tree.Primitive; // LINQ4J原始类型工具类，用于处理基本类型和包装类型
import org.apache.calcite.rel.type.RelDataType; // Calcite关系数据类型接口，表示表结构中的列类型
import org.apache.calcite.rel.type.RelDataTypeField; // Calcite关系数据类型字段，表示表中的列
import org.apache.calcite.util.Util; // Calcite通用工具类，提供各种辅助方法

import org.apache.commons.lang3.StringUtils; // Apache Commons字符串工具类，用于字符串操作
import org.apache.geode.cache.CacheClosedException; // Geode缓存关闭异常，当尝试访问已关闭的缓存时抛出
import org.apache.geode.cache.GemFireCache; // Geode缓存接口，提供缓存的基本操作
import org.apache.geode.cache.Region; // Geode区域接口，相当于数据库中的表
import org.apache.geode.cache.RegionExistsException; // Geode区域已存在异常，当创建已存在的区域时抛出
import org.apache.geode.cache.client.ClientCache; // Geode客户端缓存接口，用于连接远程Geode集群
import org.apache.geode.cache.client.ClientCacheFactory; // Geode客户端缓存工厂，用于创建客户端缓存实例
import org.apache.geode.cache.client.ClientRegionShortcut; // Geode客户端区域快捷方式枚举，定义常用区域配置
import org.apache.geode.cache.query.Struct; // Geode查询结果结构体，表示多字段查询结果
import org.apache.geode.pdx.PdxInstance; // Geode PDX实例接口，PDX是便携式数据交换格式
import org.apache.geode.pdx.ReflectionBasedAutoSerializer; // Geode反射自动序列化器，用于自动序列化Java对象

import org.checkerframework.checker.nullness.qual.Nullable; // CheckerFramework可空类型注解
import org.slf4j.Logger; // SLF4J日志接口
import org.slf4j.LoggerFactory; // SLF4J日志工厂，用于获取日志记录器

import java.lang.reflect.Field; // Java反射Field类，用于访问类的字段
import java.lang.reflect.Type; // Java反射Type接口，表示Java类型
import java.util.Date; // Java日期类
import java.util.Iterator; // Java迭代器接口，用于遍历集合
import java.util.List; // Java List接口，表示有序列表
import java.util.Locale; // Java Locale类，表示特定的地理、政治或文化区域
import java.util.Map; // Java Map接口，表示键值对映射
import java.util.concurrent.ConcurrentHashMap; // Java并发哈希映射，线程安全的Map实现

import static java.util.Objects.requireNonNull; // Java Objects工具类的requireNonNull方法，用于空值检查

/**
 * Utilities for the Geode adapter.
 * Geode适配器的工具类，提供与Apache Geode数据库交互的各种实用方法。
 * 主要功能包括：创建客户端缓存、创建区域代理、类型转换、自动检测表结构等。
 * 这个类是Calcite查询引擎与Geode数据库之间的桥梁，负责处理底层数据访问和类型映射。
 */
public class GeodeUtils { // Geode工具类，所有方法都是静态的，不需要实例化

  protected static final Logger LOGGER = LoggerFactory.getLogger(GeodeUtils.class.getName()); // 日志记录器，用于记录工具类的运行日志，便于调试和问题排查

  /**
   * Cache for the client proxy regions created in the current ClientCache.
   * 当前客户端缓存中创建的客户端代理区域的缓存。
   * 使用ConcurrentHashMap保证线程安全，缓存已创建的区域代理，避免重复创建。
   * Key是区域名称，Value是Region对象。
   */
  private static final Map<String, Region> REGION_MAP = new ConcurrentHashMap<>(); // 区域映射表，缓存已创建的Region代理对象，避免重复创建提高性能

  private static String currentLocatorHost = ""; // 当前Geode定位器主机地址，用于跟踪当前连接的Geode集群定位器
  private static int currentLocatorPort = -1; // 当前Geode定位器端口号，用于跟踪当前连接的Geode集群定位器端口，-1表示未初始化

  private static final JavaTypeFactoryExtImpl JAVA_TYPE_FACTORY = new JavaTypeFactoryExtImpl(); // Java类型工厂扩展实现，用于在Java类型和Calcite关系类型之间进行转换，是类型系统的核心组件

  private GeodeUtils() { // 私有构造方法，防止实例化，因为这是一个纯工具类，所有方法都是静态的
  } // 私有构造方法结束

  /**
   * Creates a Geode client instance connected to locator and configured to
   * support PDX instances.
   * 创建一个连接到定位器的Geode客户端实例，并配置为支持PDX实例。
   * PDX（Portable Data Exchange）是Geode的便携式数据交换格式，允许在不反序列化的情况下访问数据。
   *
   * <p>If an old instance exists, it will be destroyed and re-created.
   * 如果存在旧实例，将被销毁并重新创建。这确保了当连接参数变化时能够正确切换。
   *
   * @param locatorHost               Locator's host address - 定位器的主机地址，通常是服务器IP或域名
   * @param locatorPort               Locator's port - 定位器的端口号，默认通常是10334
   * @param autoSerializerPackagePath package name of the Domain classes loaded in the regions - 区域中加载的域类的包名，用于自动序列化配置
   * @return Returns a Geode {@link ClientCache} instance connected to Geode cluster - 返回连接到Geode集群的Geode客户端缓存实例
   */
  public static synchronized ClientCache createClientCache(String locatorHost, // 创建Geode客户端缓存方法，使用synchronized保证线程安全
      int locatorPort, String autoSerializerPackagePath, // 参数：定位器端口、自动序列化包路径
      boolean readSerialized) { // 参数：是否以序列化形式读取PDX数据，true表示保持序列化形式，false表示自动反序列化
    if (locatorPort != currentLocatorPort // 检查定位器端口是否发生变化
        || !StringUtils.equalsIgnoreCase(currentLocatorHost, locatorHost)) { // 检查定位器主机是否发生变化（忽略大小写）
      LOGGER.info("Close existing ClientCache [" // 记录日志：关闭现有的客户端缓存
          + currentLocatorHost + ":" + currentLocatorPort + "] for new Locator connection at: [" // 日志内容：当前连接信息
          + locatorHost + ":" + locatorPort + "]"); // 日志内容：新的连接信息
      currentLocatorHost = locatorHost; // 更新当前定位器主机地址
      currentLocatorPort = locatorPort; // 更新当前定位器端口号
      closeClientCache(); // 关闭现有的客户端缓存，释放资源
    } // if条件判断结束

    try { // 尝试获取现有的客户端缓存实例
      // If exists returns the existing client cache. This requires that the pre-created
      // client proxy regions can also be resolved from the regionMap
      // 如果存在，返回现有的客户端缓存。这要求预创建的客户端代理区域也能从regionMap中解析
      return ClientCacheFactory.getAnyInstance(); // 获取任何现有的客户端缓存实例，避免重复创建
    } catch (CacheClosedException cce) { // 捕获缓存关闭异常
      // Do nothing if there is no existing instance
      // 如果没有现有实例，什么都不做，继续创建新的缓存
    } // try-catch块结束

    return new ClientCacheFactory() // 创建新的客户端缓存工厂实例
        .addPoolLocator(locatorHost, locatorPort) // 添加定位器到连接池，指定主机和端口
        .setPdxSerializer(new ReflectionBasedAutoSerializer(autoSerializerPackagePath)) // 设置PDX序列化器，使用反射自动序列化指定包中的类
        .setPdxReadSerialized(readSerialized) // 设置PDX读取模式，是否以序列化形式读取
        .create(); // 创建并返回客户端缓存实例
  } // createClientCache方法结束

  public static synchronized void closeClientCache() { // 关闭客户端缓存方法，使用synchronized保证线程安全
    try { // 尝试关闭客户端缓存
      ClientCacheFactory.getAnyInstance().close(); // 获取并关闭客户端缓存实例
    } catch (CacheClosedException cce) { // 捕获缓存关闭异常
      // Do nothing if there is no existing instance
      // 如果没有现有实例，什么都不做
    } // try-catch块结束
    REGION_MAP.clear(); // 清空区域映射表，释放所有缓存的Region代理
  } // closeClientCache方法结束

  /**
   * Obtains a proxy pointing to an existing Region on the server.
   * 获取指向服务器上现有区域的代理。
   * 区域代理是客户端对服务器端区域的引用，允许客户端通过代理访问远程区域数据。
   *
   * @param cache {@link GemFireCache} instance to interact with the Geode server - GemFire缓存实例，用于与Geode服务器交互
   * @param regionName  Name of the region to create proxy for. - 要为其创建代理的区域名称
   * @return Returns a Region proxy to a remote (on the Server) regions. - 返回指向远程（服务器上）区域的Region代理
   */
  public static synchronized Region createRegion(GemFireCache cache, String regionName) { // 创建区域代理方法，使用synchronized保证线程安全
    requireNonNull(cache, "cache"); // 验证cache参数不为null，否则抛出NullPointerException
    requireNonNull(regionName, "regionName"); // 验证regionName参数不为null，否则抛出NullPointerException
    Region region = REGION_MAP.get(regionName); // 从映射表中获取已缓存的区域代理
    if (region == null) { // 如果缓存中没有找到该区域的代理
      try { // 尝试创建客户端区域代理
        region = ((ClientCache) cache) // 将缓存转换为客户端缓存
            .createClientRegionFactory(ClientRegionShortcut.PROXY) // 创建客户端区域工厂，使用PROXY快捷方式（代理模式，数据在服务器端）
            .create(regionName); // 创建指定名称的区域代理
      } catch (IllegalStateException | RegionExistsException e) { // 捕获非法状态异常或区域已存在异常
        // means this is a server cache (probably part of embedded testing
        // or clientCache is passed directly)
        // 这意味着这是服务器缓存（可能是嵌入式测试的一部分，或者直接传递了clientCache）
        region = cache.getRegion(regionName); // 直接从缓存中获取区域（适用于服务器端缓存）
      } // try-catch块结束

      REGION_MAP.put(regionName, region); // 将新创建的区域代理放入映射表缓存
    } // if条件判断结束

    return region; // 返回区域代理
  } // createRegion方法结束

  /**
   * Converts a Geode object into a Row tuple.
   * 将Geode对象转换为行元组。
   * 这个方法是数据转换的核心，负责将Geode查询结果转换为Calcite可处理的行数据格式。
   * 支持三种Geode对象类型：Struct（结构体）、PdxInstance（PDX实例）和普通Java对象。
   *
   * @param relDataTypeFields Table relation types - 表关系类型字段列表，定义了结果集的列结构
   * @param geodeResultObject Object value returned by Geode query - Geode查询返回的对象值
   * @return List of objects values corresponding to the relDataTypeFields - 对应关系类型字段的对象值列表
   */
  public static @Nullable Object convertToRowValues( // 将Geode对象转换为行值的方法，返回值可能为null
      List<RelDataTypeField> relDataTypeFields, Object geodeResultObject) { // 参数：关系类型字段列表、Geode结果对象

    Object values; // 声明转换后的值对象

    if (geodeResultObject instanceof Struct) { // 如果Geode结果对象是Struct类型（多字段查询结果）
      values = handleStructEntry(relDataTypeFields, geodeResultObject); // 调用处理Struct类型的方法
    } else if (geodeResultObject instanceof PdxInstance) { // 如果Geode结果对象是PdxInstance类型（PDX格式数据）
      values = handlePdxInstanceEntry(relDataTypeFields, geodeResultObject); // 调用处理PdxInstance类型的方法
    } else { // 如果Geode结果对象是普通Java对象
      values = handleJavaObjectEntry(relDataTypeFields, geodeResultObject); // 调用处理Java对象的方法
    } // if-else条件判断结束

    return values; // 返回转换后的值
  } // convertToRowValues方法结束

  private static Object handleStructEntry( // 处理Struct类型条目的私有方法
      List<RelDataTypeField> relDataTypeFields, Object obj) { // 参数：关系类型字段列表、要处理的对象

    Struct struct = (Struct) obj; // 将对象强制转换为Struct类型

    Object[] values = new Object[relDataTypeFields.size()]; // 创建对象数组，大小等于字段数量，用于存储转换后的值

    int index = 0; // 初始化数组索引为0
    for (RelDataTypeField relDataTypeField : relDataTypeFields) { // 遍历所有关系类型字段
      Type javaType = JAVA_TYPE_FACTORY.getJavaClass(relDataTypeField.getType()); // 从工厂获取字段的Java类型
      Object rawValue; // 声明原始值对象
      try { // 尝试获取字段值
        rawValue = struct.get(relDataTypeField.getName()); // 从Struct中获取指定名称的字段值
      } catch (IllegalArgumentException e) { // 捕获非法参数异常（字段不存在）
        rawValue = "<error>"; // 设置原始值为错误标记
        System.err.println("Could find field : " + relDataTypeField.getName()); // 输出错误信息到标准错误流
        e.printStackTrace(); // 打印异常堆栈跟踪
      } // try-catch块结束
      values[index++] = convert(rawValue, (Class) javaType); // 将原始值转换为目标Java类型并存入数组，然后索引递增
    } // for循环结束

    if (values.length == 1) { // 如果数组只有一个元素（单字段查询）
      return values[0]; // 直接返回第一个元素，而不是数组
    } // if条件判断结束

    return values; // 返回值数组
  } // handleStructEntry方法结束

  private static Object handlePdxInstanceEntry( // 处理PdxInstance类型条目的私有方法
      List<RelDataTypeField> relDataTypeFields, Object obj) { // 参数：关系类型字段列表、要处理的对象

    PdxInstance pdxEntry = (PdxInstance) obj; // 将对象强制转换为PdxInstance类型

    Object[] values = new Object[relDataTypeFields.size()]; // 创建对象数组，大小等于字段数量，用于存储转换后的值

    int index = 0; // 初始化数组索引为0
    for (RelDataTypeField relDataTypeField : relDataTypeFields) { // 遍历所有关系类型字段
      Type javaType = JAVA_TYPE_FACTORY.getJavaClass(relDataTypeField.getType()); // 从工厂获取字段的Java类型
      Object rawValue = pdxEntry.getField(relDataTypeField.getName()); // 从PdxInstance中获取指定名称的字段值
      values[index++] = convert(rawValue, (Class) javaType); // 将原始值转换为目标Java类型并存入数组，然后索引递增
    } // for循环结束

    if (values.length == 1) { // 如果数组只有一个元素（单字段查询）
      return values[0]; // 直接返回第一个元素，而不是数组
    } // if条件判断结束

    return values; // 返回值数组
  } // handlePdxInstanceEntry方法结束

  @SuppressWarnings("CatchAndPrintStackTrace") // 抑制警告：捕获异常并打印堆栈跟踪
  private static @Nullable Object handleJavaObjectEntry( // 处理Java对象条目的私有方法，返回值可能为null
      List<RelDataTypeField> relDataTypeFields, Object obj) { // 参数：关系类型字段列表、要处理的对象

    Class<?> clazz = obj.getClass(); // 获取对象的Class对象
    if (relDataTypeFields.size() == 1) { // 如果只有一个字段（单字段查询）
      try { // 尝试通过反射获取字段值
        Field javaField = clazz.getDeclaredField(relDataTypeFields.get(0).getName()); // 获取类中指定名称的声明字段
        javaField.setAccessible(true); // 设置字段可访问，即使是私有字段
        return javaField.get(obj); // 从对象中获取字段值并返回
      } catch (Exception e) { // 捕获所有异常
        e.printStackTrace(); // 打印异常堆栈跟踪
      } // try-catch块结束
      return null; // 如果发生异常，返回null
    } // if条件判断结束

    Object[] values = new Object[relDataTypeFields.size()]; // 创建对象数组，大小等于字段数量，用于存储转换后的值

    int index = 0; // 初始化数组索引为0
    for (RelDataTypeField relDataTypeField : relDataTypeFields) { // 遍历所有关系类型字段
      try { // 尝试通过反射获取字段值
        Field javaField = clazz.getDeclaredField(relDataTypeField.getName()); // 获取类中指定名称的声明字段
        javaField.setAccessible(true); // 设置字段可访问，即使是私有字段
        values[index++] = javaField.get(obj); // 从对象中获取字段值并存入数组，然后索引递增
      } catch (Exception e) { // 捕获所有异常
        e.printStackTrace(); // 打印异常堆栈跟踪
      } // try-catch块结束
    } // for循环结束
    return values; // 返回值数组
  } // handleJavaObjectEntry方法结束

  @SuppressWarnings("JavaUtilDate") // 抑制警告：使用Java的旧版Date类
  private static Object convert(Object o, Class clazz) { // 将对象转换为目标类型的私有方法
    if (o == null) { // 如果对象为null
      return null; // 直接返回null
    } // if条件判断结束
    Primitive primitive = Primitive.of(clazz); // 获取目标类型的原始类型信息（如int、long等）
    if (primitive != null) { // 如果目标类型是原始类型或其包装类型
      clazz = primitive.boxClass; // 使用对应的包装类（如Integer、Long等）
    } else { // 如果目标类型不是原始类型
      primitive = Primitive.ofBox(clazz); // 尝试从包装类获取原始类型信息
    } // if-else条件判断结束
    if (clazz == null) { // 如果类型判断失败
      return o.toString(); // 将对象转换为字符串返回
    } // if条件判断结束
    if (Map.class.isAssignableFrom(clazz) // 如果目标类型是Map或其子类
            && o instanceof PdxInstance) { // 并且对象是PdxInstance类型
      // This is in case of nested Objects!
      // 这是处理嵌套对象的情况！
      return Util.toString( // 将PDX实例转换为字符串表示
              ((PdxInstance) o).getFieldNames(), "PDX[", ",", "]"); // 获取字段名并格式化为"PDX[field1,field2,...]"形式
    } // if条件判断结束
    if (clazz.isInstance(o)) { // 如果对象已经是目标类型的实例
      return o; // 直接返回对象，无需转换
    } // if条件判断结束
    if (o instanceof Date && primitive != null) { // 如果对象是Date类型且目标类型是原始类型
      o = ((Date) o).getTime() / DateTimeUtils.MILLIS_PER_DAY; // 将日期转换为天数（从1970-01-01开始的天数）
    } // if条件判断结束
    if (o instanceof Number && primitive != null) { // 如果对象是数字类型且目标类型是原始类型
      return primitive.number((Number) o); // 将数字转换为目标原始类型（如将Double转为int）
    } // if条件判断结束
    return o; // 其他情况直接返回原对象
  } // convert方法结束

  /**
   * Extract the first entity of each Regions and use it to build a table types.
   * 提取每个区域的第一个实体，并用它来构建表类型。
   * 这是自动检测表结构的核心方法，通过分析区域中的实际数据来推断表的结构。
   *
   * @param region existing region - 现有区域，要从中检测类型的区域
   * @return derived data type. - 推导出的数据类型，包含表结构信息
   */
  public static RelDataType autodetectRelTypeFromRegion(Region<?, ?> region) { // 从区域自动检测关系类型的方法
    requireNonNull(region, "region"); // 验证region参数不为null，否则抛出NullPointerException

    // try to detect type using value constraints (if they exists)
    // 尝试使用值约束来检测类型（如果存在的话）
    final Class<?> constraint = region.getAttributes().getValueConstraint(); // 获取区域的值约束（限制区域中值的类型）
    if (constraint != null && !PdxInstance.class.isAssignableFrom(constraint)) { // 如果约束存在且不是PdxInstance类型
      return new JavaTypeFactoryExtImpl().createStructType(constraint); // 直接根据约束类创建结构类型
    } // if条件判断结束

    final Iterator<?> iter; // 声明迭代器变量
    if (region.getAttributes().getPoolName() == null) { // 如果区域属性中没有连接池名称
      // means current cache is server (not ClientCache)
      // 这意味着当前缓存是服务器（不是客户端缓存）
      iter = region.keySet().iterator(); // 获取本地key集合的迭代器
    } else { // 如果有连接池名称
      // for ClientCache
      // 用于客户端缓存
      iter = region.keySetOnServer().iterator(); // 获取服务器端key集合的迭代器
    } // if-else条件判断结束

    if (!iter.hasNext()) { // 如果迭代器没有下一个元素（区域为空）
      String message = String.format(Locale.ROOT, "Region %s is empty, can't " // 格式化错误消息
          + "autodetect type(s)", region.getName()); // 消息内容：区域为空，无法自动检测类型
      throw new IllegalStateException(message); // 抛出非法状态异常
    } // if条件判断结束

    final Object entry = region.get(iter.next()); // 获取第一个条目（值）
    return createRelDataType(entry); // 根据条目创建关系类型并返回
  } // autodetectRelTypeFromRegion方法结束

  // Create Relational Type by inferring a Geode entry or response instance.
  // 通过推断Geode条目或响应实例来创建关系类型。
  private static RelDataType createRelDataType(Object regionEntry) { // 根据区域条目创建关系类型的私有方法
    JavaTypeFactoryExtImpl typeFactory = new JavaTypeFactoryExtImpl(); // 创建Java类型工厂实例
    if (regionEntry instanceof PdxInstance) { // 如果条目是PdxInstance类型
      return typeFactory.createPdxType((PdxInstance) regionEntry); // 根据PDX实例创建PDX类型
    } else { // 如果条目是普通Java对象
      return typeFactory.createStructType(regionEntry.getClass()); // 根据对象的类创建结构类型
    } // if-else条件判断结束
  } // createRelDataType方法结束

} // GeodeUtils类结束