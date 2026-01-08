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
// 声明包名，表示这个类属于org.apache.calcite.adapter.jdbc包，是Calcite JDBC适配器的一部分
package org.apache.calcite.adapter.jdbc;

// 导入Avatica的ColumnMetaData类，用于描述列的元数据信息（列名、类型等）
import org.apache.calcite.avatica.ColumnMetaData;
// 导入Avatica的DateTimeUtils工具类，用于日期时间处理（如毫秒数常量）
import org.apache.calcite.avatica.util.DateTimeUtils;
// 导入LINQ4J的Function0函数式接口，表示无参数的函数，用于生成行数据
import org.apache.calcite.linq4j.function.Function0;
// 导入LINQ4J的Function1函数式接口，表示接受一个参数的函数，用于构建行生成器
import org.apache.calcite.linq4j.function.Function1;
// 导入Calcite的SqlDialect类，表示SQL方言（不同数据库的SQL语法差异）
import org.apache.calcite.sql.SqlDialect;
// 导入Calcite的SqlDialectFactory接口，用于创建SQL方言对象的工厂
import org.apache.calcite.sql.SqlDialectFactory;
// 导入Calcite的ImmutableNullableList工具类，用于创建不可变的可空列表
import org.apache.calcite.util.ImmutableNullableList;
// 导入Calcite的Pair工具类，用于存储键值对（这里用于存储列表示和类型）
import org.apache.calcite.util.Pair;
// 导入Calcite的Util工具类，提供各种通用工具方法
import org.apache.calcite.util.Util;

// 导入Apache Commons DBCP2的BasicDataSource类，用于创建数据库连接池
import org.apache.commons.dbcp2.BasicDataSource;

// 导入Guava的CacheBuilder类，用于构建缓存
import com.google.common.cache.CacheBuilder;
// 导入Guava的CacheLoader类，用于定义缓存的加载逻辑
import com.google.common.cache.CacheLoader;
// 导入Guava的LoadingCache接口，表示自动加载值的缓存
import com.google.common.cache.LoadingCache;
// 导入Guava的Ints工具类，用于基本类型int的集合操作
import com.google.common.primitives.Ints;

// 导入Checker Framework的@Nullable注解，用于标记可空的类型
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入JDBC的Connection接口，表示数据库连接
import java.sql.Connection;
// 导入JDBC的DatabaseMetaData接口，用于获取数据库元数据信息
import java.sql.DatabaseMetaData;
// 导入JDBC的Date类，表示SQL日期类型
import java.sql.Date;
// 导入JDBC的ResultSet接口，表示数据库查询结果集
import java.sql.ResultSet;
// 导入JDBC的SQLException类，表示数据库操作异常
import java.sql.SQLException;
// 导入JDBC的Time类，表示SQL时间类型
import java.sql.Time;
// 导入JDBC的Timestamp类，表示SQL时间戳类型
import java.sql.Timestamp;
// 导入JDBC的Types类，包含各种SQL类型的常量定义
import java.sql.Types;
// 导入Java的List接口，用于列表集合
import java.util.List;
// 导入Java的TimeZone类，用于时区处理
import java.util.TimeZone;
// 导入JDBC的DataSource接口，表示数据源（数据库连接的工厂）
import javax.sql.DataSource;

/**
 * Utilities for the JDBC provider.
 * JDBC提供者的工具类，提供JDBC适配器所需的各种工具方法
 * 
 * 这个类是JDBC适配器的核心工具类，主要负责：
 * 1. 从ResultSet中读取数据并转换为Calcite内部格式
 * 2. 缓存SQL方言对象，避免重复创建
 * 3. 缓存数据源对象，避免重复创建连接池
 * 4. 处理日期时间类型的时区转换
 */
// 使用final修饰，表示这个类不能被继承
// 使用private构造函数，确保这个类不能被实例化（纯工具类模式）
final class JdbcUtils {
  // 私有构造函数，防止外部创建实例
  private JdbcUtils() {
    // 如果尝试创建实例，抛出断言错误，确保这是一个纯工具类
    throw new AssertionError("no instances!");
  }

  /** Returns a function that, given a {@link ResultSet}, returns a function
   * that will yield successive rows from that result set.
   * 返回一个函数，该函数接受一个ResultSet，返回另一个函数，该函数会从结果集中逐行生成数据
   * 
   * 这个方法创建一个行构建器工厂，用于从JDBC ResultSet中读取数据
   * 返回的函数式接口链：Function1<ResultSet, Function0<Object[]>>
   * - 输入：ResultSet（数据库查询结果集）
   * - 输出：Function0<Object[]>（一个无参函数，每次调用返回一行数据）
   * 
   * 参数list包含列的元数据信息，每个元素是一个Pair：
   * - left（键）：ColumnMetaData.Rep，表示列的表示类型（如何从ResultSet中读取该列）
   * - right（值）：Integer，表示JDBC的SQL类型（如Types.VARCHAR、Types.INTEGER等）
   * 
   * 使用ObjectArrayRowBuilder1构建器，该构建器会将日期时间类型转换为本地时区
   * 
   * @param list 列元数据列表，包含每列的表示类型和JDBC类型
   * @return 返回一个函数，该函数接受ResultSet并返回行生成函数
   */
  static Function1<ResultSet, Function0<@Nullable Object[]>> rowBuilderFactory(
      final List<Pair<ColumnMetaData.Rep, Integer>> list) {
    // 从list中提取所有列的表示类型（Rep），转换为数组
    // Pair.left(list)获取所有Pair的left部分，即ColumnMetaData.Rep对象
    // toArray(new ColumnMetaData.Rep[0])将集合转换为数组
    ColumnMetaData.Rep[] reps =
        Pair.left(list).toArray(new ColumnMetaData.Rep[0]);
    // 从list中提取所有列的JDBC类型（Integer），转换为int数组
    // Pair.right(list)获取所有Pair的right部分，即Integer类型
    // Ints.toArray()将Integer集合转换为int基本类型数组
    int[] types = Ints.toArray(Pair.right(list));
    // 返回一个lambda函数：接受ResultSet参数，返回ObjectArrayRowBuilder1实例
    // ObjectArrayRowBuilder1是一个Function0实现，每次apply()返回一行数据
    return resultSet -> new ObjectArrayRowBuilder1(resultSet, reps, types);
  }

  /** Returns a function that, given a {@link ResultSet}, returns a function
   * that will yield successive rows from that result set;
   * as {@link #rowBuilderFactory(List)} except that values are in Calcite's
   * internal format (e.g. DATE represented as int).
   * 返回一个函数，该函数接受一个ResultSet，返回另一个函数，该函数会从结果集中逐行生成数据
   * 与rowBuilderFactory(List)类似，但值使用Calcite内部格式（例如DATE表示为int）
   * 
   * 这个方法与rowBuilderFactory的区别在于数据格式：
   * - rowBuilderFactory：返回JDBC原生类型（如Date、Time、Timestamp对象）
   * - rowBuilderFactory2：返回Calcite内部格式（如DATE转为int天数，TIME转为int毫秒数）
   * 
   * Calcite内部格式的好处：
   * - 更高效：使用基本类型而非对象
   * - 更统一：所有日期时间类型都转换为long或int
   * - 便于计算：可以直接进行数值运算
   * 
   * 转换规则：
   * - DATE：转换为int，表示从纪元开始的天数
   * - TIME：转换为int，表示当天的毫秒数
   * - TIMESTAMP：转换为long，表示从纪元开始的毫秒数
   * 
   * @param list 列元数据列表，包含每列的表示类型和JDBC类型
   * @return 返回一个函数，该函数接受ResultSet并返回行生成函数（使用Calcite内部格式）
   */
  static Function1<ResultSet, Function0<@Nullable Object[]>> rowBuilderFactory2(
      final List<Pair<ColumnMetaData.Rep, Integer>> list) {
    // 从list中提取所有列的表示类型，转换为数组
    ColumnMetaData.Rep[] reps =
        Pair.left(list).toArray(new ColumnMetaData.Rep[0]);
    // 从list中提取所有列的JDBC类型，转换为int数组
    int[] types = Ints.toArray(Pair.right(list));
    // 返回一个lambda函数：接受ResultSet参数，返回ObjectArrayRowBuilder2实例
    // ObjectArrayRowBuilder2继承自ObjectArrayRowBuilder1，但重写了value方法
    // 将日期时间类型转换为Calcite内部格式（基本类型）
    return resultSet -> new ObjectArrayRowBuilder2(resultSet, reps, types);
  }

  /** Pool of dialects.
   * SQL方言池，用于缓存和管理SQL方言对象
   * 
   * SQL方言（SqlDialect）封装了不同数据库的SQL语法差异：
   * - MySQL：使用反引号`引用标识符，LIMIT语法等
   * - PostgreSQL：使用双引号"引用标识符，LIMIT/OFFSET语法等
   * - Oracle：使用双引号"引用标识符，ROWNUM语法等
   * - SQL Server：使用方括号[]引用标识符，TOP语法等
   * 
   * 为什么需要缓存方言对象？
   * 1. 创建方言对象需要建立数据库连接，成本较高
   * 2. 方言对象是无状态的，可以安全地共享
   * 3. 同一个数据源总是使用相同的方言，不需要重复创建
   * 
   * 使用Guava LoadingCache实现缓存：
   * - 键（Key）：Pair<SqlDialectFactory, DataSource>，方言工厂和数据源的组合
   * - 值（Value）：SqlDialect，创建的方言对象
   * - softValues()：使用软引用，内存不足时可以被GC回收
   * - 自动加载：首次访问时自动创建方言对象
   * 
   * 使用单例模式：INSTANCE静态实例，全局唯一
   */
  static class DialectPool {
    // 公共静态常量，方言池的单例实例，全局唯一
    // 使用public final确保线程安全和不可变性
    public static final DialectPool INSTANCE = new DialectPool();

    // 使用Guava LoadingCache缓存方言对象
    // 键类型：Pair<SqlDialectFactory, DataSource>，方言工厂和数据源的组合
    // 值类型：SqlDialect，SQL方言对象
    // 使用LoadingCache实现自动加载：当缓存中不存在时，自动调用CacheLoader创建
    private final LoadingCache<Pair<SqlDialectFactory, DataSource>, SqlDialect> cache =
        // CacheBuilder.newBuilder()：创建缓存构建器
        // .softValues()：设置值为软引用，内存不足时可以被GC回收，避免OOM
        // .build()：构建缓存
        // CacheLoader.from(DialectPool::dialect)：设置缓存加载器，使用dialect方法创建方言
        CacheBuilder.newBuilder().softValues()
            .build(CacheLoader.from(DialectPool::dialect));

    /**
     * 根据方言工厂和数据源创建SQL方言对象
     * 
     * 这个方法是缓存的加载器，当缓存中没有对应的方言对象时会被调用
     * 
     * 创建方言的步骤：
     * 1. 从数据源获取数据库连接
     * 2. 从连接获取数据库元数据（DatabaseMetaData）
     * 3. 使用方言工厂根据元数据创建方言对象
     * 4. 关闭连接
     * 5. 返回方言对象
     * 
     * 异常处理：
     * - SQLException：包装为RuntimeException抛出
     * - 确保连接被正确关闭，使用try-finally模式
     * 
     * @param key 键，包含方言工厂和数据源
     * @return 创建的SQL方言对象
     * @throws RuntimeException 如果数据库操作失败
     */
    private static SqlDialect dialect(
        Pair<SqlDialectFactory, DataSource> key) {
      // 从键中获取方言工厂（left部分）
      SqlDialectFactory dialectFactory = key.left;
      // 从键中获取数据源（right部分）
      DataSource dataSource = key.right;
      // 初始化连接为null
      Connection connection = null;
      try {
        // 从数据源获取数据库连接，可能会抛出SQLException
        connection = dataSource.getConnection();
        // 从连接获取数据库元数据，包含数据库产品名称、版本等信息
        DatabaseMetaData metaData = connection.getMetaData();
        // 使用方言工厂根据元数据创建SQL方言对象
        // 方言工厂会根据数据库类型（MySQL、PostgreSQL等）创建对应的方言实现
        SqlDialect dialect = dialectFactory.create(metaData);
        // 关闭数据库连接，释放资源
        connection.close();
        // 将连接设为null，表示已成功关闭
        connection = null;
        // 返回创建的方言对象
        return dialect;
      } catch (SQLException e) {
        // 捕获SQL异常，包装为运行时异常抛出
        // 这样调用者不需要声明throws SQLException
        throw new RuntimeException(e);
      } finally {
        // finally块确保连接一定会被关闭
        if (connection != null) {
          // 如果连接不为null（说明之前没有成功关闭），尝试关闭
          try {
            connection.close();
          } catch (SQLException e) {
            // 忽略关闭连接时的异常，因为主逻辑已经失败
            // ignore
          }
        }
      }
    }

    /**
     * 从方言池中获取SQL方言对象
     * 
     * 这个方法是公共接口，用于外部获取方言对象
     * 如果缓存中存在，直接返回；如果不存在，自动调用dialect方法创建并缓存
     * 
     * @param dialectFactory 方言工厂，用于创建方言对象
     * @param dataSource 数据源，方言的标识（相同的data source返回相同的方言）
     * @return SQL方言对象
     */
    public SqlDialect get(SqlDialectFactory dialectFactory, DataSource dataSource) {
      // 创建缓存键，将方言工厂和数据源组合成一个Pair对象
      // Pair.of是工具方法，创建不可变的键值对
      final Pair<SqlDialectFactory, DataSource> key =
          Pair.of(dialectFactory, dataSource);
      // 从缓存中获取方言对象
      // getUnchecked方法会自动处理缓存未命中的情况，调用dialect方法创建
      // 相比get方法，getUnchecked不会抛出ExecutionException，更适合这里的使用场景
      return cache.getUnchecked(key);
    }
  }

  /** Builder that calls {@link ResultSet#getObject(int)} for every column,
   * or {@code getXxx} if the result type is a primitive {@code xxx},
   * and returns an array of objects for each row.
   * 行构建器，为每一列调用ResultSet.getObject(int)方法，
   * 或者如果结果类型是基本类型xxx则调用getXxx方法，
   * 并为每一行返回一个对象数组
   * 
   * 这个抽象类定义了从ResultSet中读取行数据的基本框架：
   * 1. 接收ResultSet和列的元数据信息
   * 2. 实现Function0接口，每次调用apply()返回一行数据
   * 3. 使用value(int)抽象方法由子类实现具体的列值读取逻辑
   * 
   * 成员变量：
   * - resultSet：JDBC结果集，从中读取数据
   * - columnCount：列数，从结果集元数据中获取
   * - reps：列的表示类型数组，定义如何读取每列数据
   * - types：列的JDBC类型数组，定义每列的SQL类型
   * 
   * 设计模式：
   * - 模板方法模式：apply()定义算法框架，value()由子类实现具体步骤
   * - 策略模式：不同的子类实现不同的值读取策略（时区转换、内部格式等）
   * 
   * 子类：
   * - ObjectArrayRowBuilder1：将日期时间类型转换为本地时区
   * - ObjectArrayRowBuilder2：将日期时间类型转换为Calcite内部格式
   */
  abstract static class ObjectArrayRowBuilder
      implements Function0<@Nullable Object[]> {
    // JDBC结果集，从中读取数据
    // 使用protected修饰，允许子类访问
    protected final ResultSet resultSet;
    // 结果集的列数，从元数据中获取
    // 使用protected修饰，允许子类访问
    protected final int columnCount;
    // 列的表示类型数组，定义如何读取每列数据
    // ColumnMetaData.Rep是枚举类型，定义了各种类型的读取方法（如jdbcGet）
    protected final ColumnMetaData.Rep[] reps;
    // 列的JDBC类型数组，定义每列的SQL类型（如Types.VARCHAR、Types.INTEGER）
    protected final int[] types;

    /**
     * 构造函数，初始化行构建器
     * 
     * @param resultSet JDBC结果集，从中读取数据
     * @param reps 列的表示类型数组，定义如何读取每列
     * @param types 列的JDBC类型数组，定义每列的SQL类型
     */
    ObjectArrayRowBuilder(ResultSet resultSet, ColumnMetaData.Rep[] reps,
        int[] types) {
      // 保存结果集引用
      this.resultSet = resultSet;
      // 保存列表示类型数组
      this.reps = reps;
      // 保存列JDBC类型数组
      this.types = types;
      try {
        // 从结果集元数据中获取列数
        // getMetaData()获取结果集的元数据对象
        // getColumnCount()获取列数
        this.columnCount = resultSet.getMetaData().getColumnCount();
      } catch (SQLException e) {
        // 捕获SQL异常，使用Util工具类转换为运行时异常抛出
        // Util.throwAsRuntime会将检查型异常转换为非检查型异常
        throw Util.throwAsRuntime(e);
      }
    }

    /**
     * 实现Function0接口的apply方法，返回一行数据
     * 
     * 每次调用这个方法，会从ResultSet中读取当前行的所有列值
     * 返回一个Object数组，数组的每个元素对应一列的值
     * 
     * 执行流程：
     * 1. 创建Object数组，大小为列数
     * 2. 遍历每一列，调用value(i)获取列值
     * 3. 将列值存入数组
     * 4. 返回数组
     * 
     * @return 包含当前行所有列值的Object数组，如果某列为null则对应元素为null
     */
    @Override public @Nullable Object[] apply() {
      try {
        // 创建Object数组，用于存储当前行的所有列值
        // 数组大小为列数columnCount
        final @Nullable Object[] values = new Object[columnCount];
        // 遍历每一列（从0到columnCount-1）
        for (int i = 0; i < columnCount; i++) {
          // 调用抽象方法value(i)获取第i列的值
          // value方法由子类实现具体的读取逻辑
          values[i] = value(i);
        }
        // 返回包含所有列值的数组
        return values;
      } catch (SQLException e) {
        // 捕获SQL异常，包装为运行时异常抛出
        throw new RuntimeException(e);
      }
    }

    /**
     * 从JDBC结果集的指定列获取值
     * 
     * 这是一个抽象方法，由子类实现具体的列值读取逻辑
     * 参数i是列索引（0-based），但JDBC使用1-based索引，所以调用时需要i+1
     * 
     * @param i 列索引（0-based，从0开始）
     * @return 列值，可能为null
     * @throws SQLException 如果读取列值时发生错误
     */
    protected abstract @Nullable Object value(int i) throws SQLException;

    /**
     * 将Timestamp转换为long毫秒数
     * 
     * 这是一个默认实现，子类可以重写以添加时区转换等逻辑
     * 
     * @param v Timestamp对象
     * @return 从1970-01-01 00:00:00 UTC到该时间戳的毫秒数
     */
    long timestampToLong(Timestamp v) {
      // 调用Timestamp的getTime()方法，返回毫秒数
      return v.getTime();
    }

    /**
     * 将Time转换为long毫秒数
     * 
     * 这是一个默认实现，子类可以重写以添加时区转换等逻辑
     * 
     * @param v Time对象
     * @return 从1970-01-01 00:00:00 UTC到该时间的毫秒数
     */
    long timeToLong(Time v) {
      // 调用Time的getTime()方法，返回毫秒数
      return v.getTime();
    }

    /**
     * 将Date转换为long毫秒数
     * 
     * 这是一个默认实现，子类可以重写以添加时区转换等逻辑
     * 
     * @param v Date对象
     * @return 从1970-01-01 00:00:00 UTC到该日期的毫秒数
     */
    long dateToLong(Date v) {
      // 调用Date的getTime()方法，返回毫秒数
      return v.getTime();
    }
  }

  /** Row builder that shifts DATE, TIME, TIMESTAMP values into local time
   * zone.
   * 行构建器，将DATE、TIME、TIMESTAMP值转换为本地时区
   * 
   * 这个类继承自ObjectArrayRowBuilder，专门处理日期时间类型的时区转换
   * 
   * 为什么需要时区转换？
   * 1. MySQL等数据库返回的时间戳可能已经转换为本地时区
   * 2. 使用getTimestamp(int, Calendar)配合UTC日历应该可以避免，但实际无效
   * 3. 因此需要手动进行时区转换，确保数据的一致性
   * 
   * 转换逻辑：
   * - 获取默认时区（TimeZone.getDefault()）
   * - 计算时区偏移量（offset）
   * - 将原始毫秒数加上偏移量，得到本地时区的毫秒数
   * 
   * 特殊处理：
   * - TIME：时间会循环，需要对一天的毫秒数取模
   * - TIMESTAMP：直接加上偏移量
   * - DATE：直接加上偏移量
   * 
   * 使用场景：
   * - 当需要与本地系统时间保持一致时
   * - 当数据库返回的时间已经转换为本地时区时
   */
  static class ObjectArrayRowBuilder1 extends ObjectArrayRowBuilder {
    // 获取默认时区，用于时区转换
    // TimeZone.getDefault()返回JVM的默认时区（通常与操作系统时区一致）
    final TimeZone timeZone = TimeZone.getDefault();

    /**
     * 构造函数，初始化行构建器
     * 
     * @param resultSet JDBC结果集
     * @param reps 列的表示类型数组
     * @param types 列的JDBC类型数组
     */
    ObjectArrayRowBuilder1(ResultSet resultSet, ColumnMetaData.Rep[] reps,
        int[] types) {
      // 调用父类构造函数，初始化成员变量
      super(resultSet, reps, types);
    }

    /**
     * 从结果集中获取指定列的值，并进行时区转换
     * 
     * 这个方法重写了父类的value方法，添加了日期时间类型的特殊处理
     * 
     * 处理逻辑：
     * 1. 根据列的JDBC类型（types[i]）进行判断
     * 2. 如果是TIMESTAMP、TIME或DATE类型，进行时区转换
     * 3. 其他类型使用默认的jdbcGet方法读取
     * 
     * 注意：
     * - JDBC的列索引是1-based，所以使用i+1
     * - 如果值为null，直接返回null，不进行转换
     * - 转换后创建新的对象（Timestamp、Time、Date）
     * 
     * @param i 列索引（0-based）
     * @return 列值，可能为null
     * @throws SQLException 如果读取列值时发生错误
     */
    @Override protected @Nullable Object value(int i) throws SQLException {
      // MySQL returns timestamps shifted into local time. Using
      // getTimestamp(int, Calendar) with a UTC calendar should prevent this,
      // but does not. So we shift explicitly.
      // MySQL返回的时间戳已经转换为本地时区。使用getTimestamp(int, Calendar)
      // 配合UTC日历应该可以避免，但实际无效。因此我们手动进行转换。
      // 根据列的JDBC类型进行判断
      switch (types[i]) {
      // 如果是时间戳类型（TIMESTAMP）
      case Types.TIMESTAMP:
        // 从结果集中获取时间戳值（i+1因为JDBC使用1-based索引）
        final Timestamp timestamp = resultSet.getTimestamp(i + 1);
        // 如果值为null，返回null；否则创建新的Timestamp对象，使用转换后的毫秒数
        return timestamp == null ? null : new Timestamp(timestampToLong(timestamp));
      // 如果是时间类型（TIME）
      case Types.TIME:
        // 从结果集中获取时间值
        final Time time = resultSet.getTime(i + 1);
        // 如果值为null，返回null；否则创建新的Time对象，使用转换后的毫秒数
        return time == null ? null : new Time(timeToLong(time));
      // 如果是日期类型（DATE）
      case Types.DATE:
        // 从结果集中获取日期值
        final Date date = resultSet.getDate(i + 1);
        // 如果值为null，返回null；否则创建新的Date对象，使用转换后的毫秒数
        return date == null ? null : new Date(dateToLong(date));
      // 其他类型，不特殊处理
      default:
        // 跳出switch，继续执行后面的代码
        break;
      }
      // 对于非日期时间类型，使用列表示类型的jdbcGet方法读取
      // reps[i]是第i列的表示类型，jdbcGet是读取方法
      // i+1因为JDBC使用1-based索引
      return reps[i].jdbcGet(resultSet, i + 1);
    }

    /**
     * 将Timestamp转换为本地时区的long毫秒数
     * 
     * 重写父类方法，添加时区偏移量计算
     * 
     * @param v Timestamp对象
     * @return 转换为本地时区后的毫秒数
     */
    @Override long timestampToLong(Timestamp v) {
      // 获取原始毫秒数（UTC时间）
      long time = v.getTime();
      // 获取该时间点的时区偏移量（毫秒）
      // getOffset返回给定时间点相对于UTC的偏移量
      int offset = timeZone.getOffset(time);
      // 返回加上偏移量后的毫秒数（本地时区时间）
      return time + offset;
    }

    /**
     * 将Time转换为本地时区的long毫秒数
     * 
     * 重写父类方法，添加时区偏移量计算，并对一天的毫秒数取模
     * 
     * @param v Time对象
     * @return 转换为本地时区后的毫秒数（0到86399999之间）
     */
    @Override long timeToLong(Time v) {
      // 获取原始毫秒数（UTC时间）
      long time = v.getTime();
      // 获取该时间点的时区偏移量（毫秒）
      int offset = timeZone.getOffset(time);
      // 返回加上偏移量后的毫秒数，并对一天的毫秒数取模
      // DateTimeUtils.MILLIS_PER_DAY是一天的毫秒数（86400000）
      // 取模确保时间在0到23:59:59.999之间
      return (time + offset) % DateTimeUtils.MILLIS_PER_DAY;
    }

    /**
     * 将Date转换为本地时区的long毫秒数
     * 
     * 重写父类方法，添加时区偏移量计算
     * 
     * @param v Date对象
     * @return 转换为本地时区后的毫秒数
     */
    @Override long dateToLong(Date v) {
      // 获取原始毫秒数（UTC时间）
      long time = v.getTime();
      // 获取该时间点的时区偏移量（毫秒）
      int offset = timeZone.getOffset(time);
      // 返回加上偏移量后的毫秒数（本地时区时间）
      return time + offset;
    }
  }

  /** Row builder that converts JDBC values into internal values.
   * 行构建器，将JDBC值转换为Calcite内部值
   * 
   * 这个类继承自ObjectArrayRowBuilder1，进一步将日期时间类型转换为Calcite内部格式
   * 
   * Calcite内部格式：
   * - DATE：int，表示从纪元开始的天数（除以一天的毫秒数）
   * - TIME：int，表示当天的毫秒数
   * - TIMESTAMP：long，表示从纪元开始的毫秒数
   * 
   * 为什么使用内部格式？
   * 1. 更高效：使用基本类型而非对象，减少内存占用和GC压力
   * 2. 更统一：所有日期时间类型都转换为数值类型，便于比较和计算
   * 3. 更便携：不依赖JDBC的Date/Time/Timestamp类
   * 
   * 转换规则：
   * - TIMESTAMP：调用父类的timestampToLong方法，返回long
   * - TIME：调用父类的timeToLong方法，转换为int
   * - DATE：调用父类的dateToLong方法，除以一天的毫秒数，转换为int
   * 
   * 使用场景：
   * - 当需要使用Calcite的内部表示时
   * - 当需要提高性能时
   * - 当需要进行日期时间计算时
   */
  static class ObjectArrayRowBuilder2 extends ObjectArrayRowBuilder1 {
    /**
     * 构造函数，初始化行构建器
     * 
     * @param resultSet JDBC结果集
     * @param reps 列的表示类型数组
     * @param types 列的JDBC类型数组
     */
    ObjectArrayRowBuilder2(ResultSet resultSet, ColumnMetaData.Rep[] reps,
        int[] types) {
      // 调用父类构造函数，初始化成员变量
      super(resultSet, reps, types);
    }

    /**
     * 从结果集中获取指定列的值，并转换为Calcite内部格式
     * 
     * 这个方法重写了父类的value方法，将日期时间类型转换为基本类型
     * 
     * 处理逻辑：
     * 1. 根据列的JDBC类型（types[i]）进行判断
     * 2. 如果是TIMESTAMP类型，转换为long
     * 3. 如果是TIME类型，转换为int
     * 4. 如果是DATE类型，转换为int（天数）
     * 5. 其他类型使用默认的jdbcGet方法读取
     * 
     * 注意：
     * - 不再创建新的Date/Time/Timestamp对象，直接返回基本类型
     * - DATE需要除以一天的毫秒数，得到天数
     * - TIME需要转换为int（毫秒数不会超过int范围）
     * 
     * @param i 列索引（0-based）
     * @return 列值，可能为null，TIMESTAMP返回long，TIME和DATE返回int
     * @throws SQLException 如果读取列值时发生错误
     */
    @Override protected @Nullable Object value(int i) throws SQLException {
      // 根据列的JDBC类型进行判断
      switch (types[i]) {
      // 如果是时间戳类型（TIMESTAMP）
      case Types.TIMESTAMP:
        // 从结果集中获取时间戳值
        final Timestamp timestamp = resultSet.getTimestamp(i + 1);
        // 如果值为null，返回null；否则调用父类的timestampToLong方法，返回long
        return timestamp == null ? null : timestampToLong(timestamp);
      // 如果是时间类型（TIME）
      case Types.TIME:
        // 从结果集中获取时间值
        final Time time = resultSet.getTime(i + 1);
        // 如果值为null，返回null；否则调用父类的timeToLong方法，转换为int
        return time == null ? null : (int) timeToLong(time);
      // 如果是日期类型（DATE）
      case Types.DATE:
        // 从结果集中获取日期值
        final Date date = resultSet.getDate(i + 1);
        // 如果值为null，返回null；否则调用父类的dateToLong方法，除以一天的毫秒数，转换为int天数
        return date == null ? null
            : (int) (dateToLong(date) / DateTimeUtils.MILLIS_PER_DAY);
      // 其他类型，使用默认的jdbcGet方法读取
      default:
        return reps[i].jdbcGet(resultSet, i + 1);
      }
    }
  }

  /** Ensures that if two data sources have the same definition, they will use
   * the same object.
   * 确保如果两个数据源具有相同的定义，它们将使用同一个对象
   *
   * <p>This in turn makes it easier to cache
   * {@link org.apache.calcite.sql.SqlDialect} objects. Otherwise, each time we
   * see a new data source, we have to open a connection to find out what
   * database product and version it is.
   * 这使得缓存SQL方言对象变得更容易。否则，每次看到一个新的数据源，
   * 我们都必须打开连接来找出它是什么数据库产品和版本。
   * 
   * 这个类实现了数据源池，用于缓存和管理数据源对象
   * 
   * 为什么需要数据源池？
   * 1. 避免重复创建相同的数据源对象
   * 2. 提高性能：创建数据源（连接池）需要一定开销
   * 3. 节省资源：避免创建多个连接池，浪费数据库连接
   * 4. 便于缓存方言对象：相同的数据源总是返回相同的方言
   * 
   * 数据源的定义：
   * - URL：数据库连接字符串（如jdbc:mysql://localhost:3306/db）
   * - Username：用户名
   * - Password：密码
   * - DriverClassName：驱动类名（如com.mysql.jdbc.Driver）
   * 
   * 使用Guava LoadingCache实现缓存：
   * - 键（Key）：List<String>，包含URL、用户名、密码、驱动类名
   * - 值（Value）：BasicDataSource，Apache Commons DBCP2的数据源实现
   * - softValues()：使用软引用，内存不足时可以被GC回收
   * - 自动加载：首次访问时自动创建数据源对象
   * 
   * 使用单例模式：INSTANCE静态实例，全局唯一
   * 
   * 工作流程：
   * 1. 客户端调用get方法，传入数据源定义
   * 2. 将定义转换为键（List）
   * 3. 从缓存中查找
   * 4. 如果存在，直接返回；如果不存在，自动创建并缓存
   */
  static class DataSourcePool {
    // 公共静态常量，数据源池的单例实例，全局唯一
    public static final DataSourcePool INSTANCE = new DataSourcePool();

    // 使用Guava LoadingCache缓存数据源对象
    // 键类型：List<@Nullable String>，包含URL、用户名、密码、驱动类名
    // 值类型：BasicDataSource，Apache Commons DBCP2的数据源实现
    // 使用LoadingCache实现自动加载：当缓存中不存在时，自动调用CacheLoader创建
    private final LoadingCache<List<@Nullable String>, BasicDataSource> cache =
        // CacheBuilder.newBuilder()：创建缓存构建器
        // .softValues()：设置值为软引用，内存不足时可以被GC回收
        // .build()：构建缓存
        // CacheLoader.from(DataSourcePool::dataSource)：设置缓存加载器，使用dataSource方法创建数据源
        CacheBuilder.newBuilder().softValues()
            .build(CacheLoader.from(DataSourcePool::dataSource));

    /**
     * 根据数据源定义创建BasicDataSource对象
     * 
     * 这个方法是缓存的加载器，当缓存中没有对应的数据源对象时会被调用
     * 
     * 创建数据源的步骤：
     * 1. 创建BasicDataSource实例
     * 2. 设置URL（第0个元素）
     * 3. 设置用户名（第1个元素）
     * 4. 设置密码（第2个元素）
     * 5. 设置驱动类名（第3个元素）
     * 6. 返回配置好的数据源
     * 
     * BasicDataSource是Apache Commons DBCP2提供的数据源实现：
     * - 支持连接池，提高性能
     * - 支持连接验证，确保连接可用
     * - 支持连接超时，避免连接泄漏
     * - 支持连接回收，自动关闭空闲连接
     * 
     * @param key 键，包含URL、用户名、密码、驱动类名
     * @return 创建的BasicDataSource对象
     */
    private static BasicDataSource dataSource(
          List<? extends @Nullable String> key) {
      // 创建BasicDataSource实例
      BasicDataSource dataSource = new BasicDataSource();
      // 设置数据库连接URL（第0个元素）
      dataSource.setUrl(key.get(0));
      // 设置数据库用户名（第1个元素）
      dataSource.setUsername(key.get(1));
      // 设置数据库密码（第2个元素）
      dataSource.setPassword(key.get(2));
      // 设置JDBC驱动类名（第3个元素）
      dataSource.setDriverClassName(key.get(3));
      // 返回配置好的数据源
      return dataSource;
    }

    /**
     * 从数据源池中获取数据源对象
     * 
     * 这个方法是公共接口，用于外部获取数据源对象
     * 如果缓存中存在，直接返回；如果不存在，自动调用dataSource方法创建并缓存
     * 
     * @param url 数据库连接URL（如jdbc:mysql://localhost:3306/db）
     * @param driverClassName JDBC驱动类名（如com.mysql.jdbc.Driver），可以为null
     * @param username 数据库用户名，可以为null
     * @param password 数据库密码，可以为null
     * @return 数据源对象
     */
    public DataSource get(String url, @Nullable String driverClassName,
        @Nullable String username, @Nullable String password) {
      // Get data source objects from a cache, so that we don't have to sniff
      // out what kind of database they are quite as often.
      // 从缓存中获取数据源对象，这样我们就不必经常探测它们是什么类型的数据库。
      // 创建缓存键，将URL、用户名、密码、驱动类名组合成一个不可变列表
      // ImmutableNullableList.of创建不可变的可空列表
      // 注意顺序：url、username、password、driverClassName
      final List<@Nullable String> key =
          ImmutableNullableList.of(url, username, password, driverClassName);
      // 从缓存中获取数据源对象
      // getUnchecked方法会自动处理缓存未命中的情况，调用dataSource方法创建
      // 相比get方法，getUnchecked不会抛出ExecutionException，更适合这里的使用场景
      return cache.getUnchecked(key);
    }
  }
}
