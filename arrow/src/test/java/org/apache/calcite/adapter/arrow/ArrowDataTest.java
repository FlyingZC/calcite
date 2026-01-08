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
// Apache Calcite项目包声明,该包专门用于Arrow适配器相关功能
package org.apache.calcite.adapter.arrow;

// 导入Arrow JDBC适配器相关类,用于将JDBC ResultSet转换为Arrow格式
import org.apache.arrow.adapter.jdbc.ArrowVectorIterator; // Arrow向量迭代器,用于批量处理Arrow数据
import org.apache.arrow.adapter.jdbc.JdbcToArrow; // JDBC到Arrow的转换工具类
import org.apache.arrow.adapter.jdbc.JdbcToArrowConfig; // JDBC到Arrow的转换配置类
import org.apache.arrow.adapter.jdbc.JdbcToArrowConfigBuilder; // JDBC到Arrow配置构建器,使用建造者模式创建配置对象
import org.apache.arrow.adapter.jdbc.JdbcToArrowUtils; // JDBC到Arrow的工具类,提供辅助方法
import org.apache.arrow.memory.RootAllocator; // Arrow内存分配器的根分配器,负责管理Arrow向量的内存分配
import org.apache.arrow.vector.BigIntVector; // Arrow 64位整数向量,用于存储BIGINT类型数据
import org.apache.arrow.vector.BitVector; // Arrow位向量,用于存储布尔类型数据(每个元素占用1位)
import org.apache.arrow.vector.DateDayVector; // Arrow日期向量,以天为单位存储日期数据
import org.apache.arrow.vector.DecimalVector; // Arrow十进制数向量,用于存储高精度小数
import org.apache.arrow.vector.FieldVector; // Arrow字段向量接口,是所有具体向量类型的基类
import org.apache.arrow.vector.Float8Vector; // Arrow 64位浮点数向量,用于存储DOUBLE类型数据
import org.apache.arrow.vector.FloatingPointVector; // Arrow浮点数向量基类
import org.apache.arrow.vector.IntVector; // Arrow 32位整数向量,用于存储INT类型数据
import org.apache.arrow.vector.SmallIntVector; // Arrow 16位整数向量,用于存储SMALLINT类型数据
import org.apache.arrow.vector.TimeSecVector; // Arrow时间向量,以秒为单位存储时间数据
import org.apache.arrow.vector.TinyIntVector; // Arrow 8位整数向量,用于存储TINYINT类型数据
import org.apache.arrow.vector.VarCharVector; // Arrow可变长字符向量,用于存储VARCHAR类型数据
import org.apache.arrow.vector.VectorSchemaRoot; // Arrow向量模式根,包含一组字段向量及其Schema,是Arrow数据的基本容器
import org.apache.arrow.vector.ipc.ArrowFileWriter; // Arrow文件写入器,用于将Arrow数据写入IPC文件格式
import org.apache.arrow.vector.types.DateUnit; // Arrow日期单位枚举,定义日期的时间单位(如DAY、MILLISECOND等)
import org.apache.arrow.vector.types.FloatingPointPrecision; // Arrow浮点数精度枚举,定义浮点数的精度(如SINGLE、DOUBLE等)
import org.apache.arrow.vector.types.TimeUnit; // Arrow时间单位枚举,定义时间的时间单位(如SECOND、MILLISECOND等)
import org.apache.arrow.vector.types.pojo.ArrowType; // Arrow数据类型基类,定义Arrow支持的所有数据类型
import org.apache.arrow.vector.types.pojo.Field; // Arrow字段类,包含字段名、字段类型和子字段信息
import org.apache.arrow.vector.types.pojo.FieldType; // Arrow字段类型类,包含数据类型和是否可为空等信息
import org.apache.arrow.vector.types.pojo.Schema; // Arrow模式类,定义一组字段的集合,相当于数据库的表结构
import org.apache.arrow.vector.util.Text; // Arrow文本工具类,用于高效处理字符串数据

// 导入Google Guava集合工具类
import com.google.common.collect.ImmutableList; // 不可变列表构建器,用于创建线程安全的不可变列表

// 导入Scott测试数据库相关类
import net.hydromatic.scott.data.hsqldb.ScottHsqldb; // Scott HSQLDB数据库工具类,提供经典的SCOTT示例数据库

// 导入Java标准库IO相关类
import java.io.File; // 文件类,用于文件和目录的抽象表示
import java.io.FileOutputStream; // 文件输出流,用于向文件写入字节数据
import java.io.IOException; // IO异常类,处理输入输出操作中的错误

// 导入Java数学相关类
import java.math.BigDecimal; // BigDecimal类,用于高精度十进制数运算

// 导入Java NIO路径相关类
import java.nio.file.Path; // 路径接口,用于表示文件系统中的路径

// 导入JDBC相关类
import java.sql.Connection; // JDBC连接接口,表示与数据库的连接
import java.sql.DriverManager; // JDBC驱动管理器,用于获取数据库连接
import java.sql.ResultSet; // JDBC结果集接口,表示SQL查询的结果
import java.sql.SQLException; // SQL异常类,处理数据库操作中的错误
import java.sql.Statement; // JDBC语句接口,用于执行SQL语句

// 导入Java时间相关类
import java.util.Calendar; // 日历类,用于处理日期和时间

// 导入Java集合框架
import java.util.List; // List接口,表示有序集合

/**
 * Class that can be used to generate Arrow sample data into a data directory.
 * 这是一个用于生成Arrow示例数据到数据目录的工具类
 * 主要功能:
 * 1. 生成包含各种数据类型的Arrow测试数据文件
 * 2. 从Scott示例数据库读取数据并转换为Arrow格式
 * 3. 支持多种Arrow数据类型:整数、浮点数、字符串、日期、时间、布尔值、十进制数等
 * 4. 用于Calcite Arrow适配器的测试数据准备
 */
public class ArrowDataTest { // 测试数据生成类,用于创建Arrow格式的测试数据文件

  private final int batchSize; // 批次大小,控制每次写入Arrow文件的数据行数,默认为20行
  private final int entries; // 总条目数,控制生成的测试数据总行数,默认为50行
  private byte  tinyIntValue; // TINYINT类型字段的当前值,用于生成递增的8位整数数据
  private short smallIntValue; // SMALLINT类型字段的当前值,用于生成递增的16位整数数据
  private int intValue; // INT类型字段的当前值,用于生成递增的32位整数数据
  private int stringValue; // VARCHAR类型字段的当前值,用于生成递增的字符串数据
  private float floatValue; // FLOAT类型字段的当前值,用于生成递增的32位浮点数数据
  private long longValue; // BIGINT类型字段的当前值,用于生成递增的64位整数数据
  private double doubleValue; // DOUBLE类型字段的当前值,用于生成递增的64位浮点数数据
  private boolean booleanValue; // BOOLEAN类型字段的当前值,用于生成交替的布尔值数据
  private BigDecimal decimalValue; // DECIMAL类型字段的当前值,用于生成递增的高精度十进制数数据
  private int timeValue; // TIME类型字段的当前值,用于生成递增的时间数据

  public ArrowDataTest() { // 默认构造函数,初始化所有成员变量为默认值
    this.batchSize = 20; // 设置批次大小为20,表示每次写入20行数据
    this.entries = 50; // 设置总条目数为50,表示总共生成50行测试数据
    this.tinyIntValue = 0; // 初始化TINYINT值为0
    this.smallIntValue = 0; // 初始化SMALLINT值为0
    this.intValue = 0; // 初始化INT值为0
    this.stringValue = 0; // 初始化字符串值为0
    this.floatValue = 0; // 初始化FLOAT值为0
    this.longValue = 0; // 初始化BIGINT值为0
    this.doubleValue = 0; // 初始化DOUBLE值为0
    this.booleanValue = false; // 初始化BOOLEAN值为false
    this.decimalValue = BigDecimal.ZERO; // 初始化DECIMAL值为0
    this.timeValue = 0; // 初始化TIME值为0
  }

  private Schema makeArrowDateTypeSchema() { // 创建包含日期时间类型的Arrow Schema(表结构)
    ImmutableList.Builder<Field> childrenBuilder = ImmutableList.builder(); // 创建不可变字段列表构建器,用于构建Schema的字段列表
    FieldType tinyIntType = FieldType.nullable(new ArrowType.Int(8, true)); // 创建TINYINT字段类型:8位有符号整数,可为空
    FieldType smallIntType = FieldType.nullable(new ArrowType.Int(16, true)); // 创建SMALLINT字段类型:16位有符号整数,可为空
    FieldType intType = FieldType.nullable(new ArrowType.Int(32, true)); // 创建INT字段类型:32位有符号整数,可为空
    FieldType stringType = FieldType.nullable(new ArrowType.Utf8()); // 创建VARCHAR字段类型:UTF-8编码的字符串,可为空
    FieldType floatType = // 创建FLOAT字段类型:单精度浮点数(32位),可为空
        FieldType.nullable(new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE));
    FieldType longType = FieldType.nullable(new ArrowType.Int(64, true)); // 创建BIGINT字段类型:64位有符号整数,可为空
    FieldType doubleType = // 创建DOUBLE字段类型:双精度浮点数(64位),可为空
        FieldType.nullable(new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE));
    FieldType booleanType = FieldType.nullable(new ArrowType.Bool()); // 创建BOOLEAN字段类型:布尔值,可为空
    FieldType decimalType = FieldType.nullable(new ArrowType.Decimal(12, 2, 128)); // 创建DECIMAL字段类型:精度12位,小数点后2位,最大128位,可为空
    FieldType decimalType2 = FieldType.nullable(new ArrowType.Decimal(12, 3, 128)); // 创建DECIMAL字段类型2:精度12位,小数点后3位,最大128位,可为空
    FieldType dateType = FieldType.nullable(new ArrowType.Date(DateUnit.DAY)); // 创建DATE字段类型:以天为单位的日期,可为空
    FieldType timeType = FieldType.nullable(new ArrowType.Time(TimeUnit.SECOND, 32)); // 创建TIME字段类型:以秒为单位的时间,32位存储,可为空

    childrenBuilder.add(new Field("tinyIntField", tinyIntType, null)); // 添加TINYINT字段:字段名为"tinyIntField",无子字段
    childrenBuilder.add(new Field("smallIntField", smallIntType, null)); // 添加SMALLINT字段:字段名为"smallIntField",无子字段
    childrenBuilder.add(new Field("intField", intType, null)); // 添加INT字段:字段名为"intField",无子字段
    childrenBuilder.add(new Field("stringField", stringType, null)); // 添加VARCHAR字段:字段名为"stringField",无子字段
    childrenBuilder.add(new Field("floatField", floatType, null)); // 添加FLOAT字段:字段名为"floatField",无子字段
    childrenBuilder.add(new Field("longField", longType, null)); // 添加BIGINT字段:字段名为"longField",无子字段
    childrenBuilder.add(new Field("doubleField", doubleType, null)); // 添加DOUBLE字段:字段名为"doubleField",无子字段
    childrenBuilder.add(new Field("booleanField", booleanType, null)); // 添加BOOLEAN字段:字段名为"booleanField",无子字段
    childrenBuilder.add(new Field("decimalField", decimalType, null)); // 添加DECIMAL字段:字段名为"decimalField",无子字段
    childrenBuilder.add(new Field("dateField", dateType, null)); // 添加DATE字段:字段名为"dateField",无子字段
    childrenBuilder.add(new Field("decimalField2", decimalType2, null)); // 添加DECIMAL字段2:字段名为"decimalField2",无子字段
    childrenBuilder.add(new Field("timeField", timeType, null)); // 添加TIME字段:字段名为"timeField",无子字段

    return new Schema(childrenBuilder.build(), null); // 创建并返回Schema对象,包含所有定义的字段
  }

  private Schema makeArrowSchema() { // 创建基础Arrow Schema(表结构),包含基本数据类型
    ImmutableList.Builder<Field> childrenBuilder = ImmutableList.builder(); // 创建不可变字段列表构建器
    FieldType intType = FieldType.nullable(new ArrowType.Int(32, true)); // 创建INT字段类型:32位有符号整数,可为空
    FieldType stringType = FieldType.nullable(new ArrowType.Utf8()); // 创建VARCHAR字段类型:UTF-8编码的字符串,可为空
    FieldType floatType = // 创建FLOAT字段类型:单精度浮点数(32位),可为空
        FieldType.nullable(new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE));
    FieldType longType = FieldType.nullable(new ArrowType.Int(64, true)); // 创建BIGINT字段类型:64位有符号整数,可为空

    childrenBuilder.add(new Field("intField", intType, null)); // 添加INT字段:字段名为"intField",无子字段
    childrenBuilder.add(new Field("stringField", stringType, null)); // 添加VARCHAR字段:字段名为"stringField",无子字段
    childrenBuilder.add(new Field("floatField", floatType, null)); // 添加FLOAT字段:字段名为"floatField",无子字段
    childrenBuilder.add(new Field("longField", longType, null)); // 添加BIGINT字段:字段名为"longField",无子字段

    return new Schema(childrenBuilder.build(), null); // 创建并返回Schema对象,包含4个基本字段
  }


  public void writeScottEmpData(Path arrowDataDirectory) throws IOException, SQLException { // 将Scott示例数据库的表数据写入Arrow文件
    List<String> tableNames = ImmutableList.of("EMP", "DEPT", "SALGRADE"); // 定义要导出的表名列表:员工表、部门表、薪资等级表

    Connection connection = // 创建与Scott HSQLDB数据库的连接
        DriverManager.getConnection(ScottHsqldb.URI, ScottHsqldb.USER, ScottHsqldb.PASSWORD);

    for (String tableName : tableNames) { // 遍历每个表名,逐个处理表数据
      String sql = "SELECT * FROM " + tableName; // 构建SQL查询语句,查询表的所有数据
      Statement statement = connection.createStatement(); // 创建JDBC语句对象,用于执行SQL语句
      ResultSet resultSet = statement.executeQuery(sql); // 执行SQL查询,获取结果集

      Calendar calendar = JdbcToArrowUtils.getUtcCalendar(); // 获取UTC时区的日历对象,用于处理日期时间转换

      RootAllocator rootAllocator = new RootAllocator(); // 创建Arrow根内存分配器,用于管理Arrow向量的内存
      JdbcToArrowConfig config = new JdbcToArrowConfigBuilder() // 创建JDBC到Arrow的转换配置
          .setAllocator(rootAllocator) // 设置内存分配器
          .setReuseVectorSchemaRoot(true) // 设置重用VectorSchemaRoot,提高性能
          .setCalendar(calendar) // 设置日历对象,用于日期时间转换
          .setTargetBatchSize(1024) // 设置目标批次大小为1024行
          .build(); // 构建配置对象

      ArrowVectorIterator vectorIterator = JdbcToArrow.sqlToArrowVectorIterator(resultSet, config); // 将JDBC结果集转换为Arrow向量迭代器
      Path tablePath = arrowDataDirectory.resolve(tableName + ".arrow"); // 构建Arrow文件路径:目录/表名.arrow

      FileOutputStream fileOutputStream = new FileOutputStream(tablePath.toFile()); // 创建文件输出流,用于写入Arrow文件

      VectorSchemaRoot vectorSchemaRoot = vectorIterator.next(); // 获取第一批数据的VectorSchemaRoot对象

      ArrowFileWriter arrowFileWriter = // 创建Arrow文件写入器,用于将数据写入IPC格式文件
          new ArrowFileWriter(vectorSchemaRoot, null, fileOutputStream.getChannel());

      arrowFileWriter.start(); // 启动Arrow文件写入器,开始写入文件头
      arrowFileWriter.writeBatch(); // 写入第一批数据

      while (vectorIterator.hasNext()) { // 循环处理剩余批次的数据
        // refreshes the data in the VectorSchemaRoot with the next batch
        vectorIterator.next(); // 获取下一批数据,并刷新VectorSchemaRoot中的数据
        arrowFileWriter.writeBatch(); // 写入当前批次数据
      }

      arrowFileWriter.close(); // 关闭Arrow文件写入器,完成文件写入
    }
  }

  public void writeArrowData(File file) throws IOException { // 写入基础数据类型的Arrow测试数据文件
    FileOutputStream fileOutputStream = new FileOutputStream(file); // 创建文件输出流,用于写入Arrow文件
    Schema arrowSchema = makeArrowSchema(); // 创建基础Schema,包含INT、VARCHAR、FLOAT、BIGINT四种类型
    VectorSchemaRoot vectorSchemaRoot = // 创建VectorSchemaRoot对象,作为Arrow数据的容器
        VectorSchemaRoot.create(arrowSchema, new RootAllocator(Integer.MAX_VALUE)); // 使用最大整数作为内存分配器限制
    ArrowFileWriter arrowFileWriter = // 创建Arrow文件写入器
        new ArrowFileWriter(vectorSchemaRoot, null, fileOutputStream.getChannel());

    arrowFileWriter.start(); // 启动Arrow文件写入器,写入文件头

    for (int i = 0; i < this.entries;) { // 循环生成数据,直到达到总条目数
      int numRows = Math.min(this.batchSize, this.entries - i); // 计算当前批次应写入的行数:批次大小与剩余行数的较小值
      vectorSchemaRoot.setRowCount(numRows); // 设置VectorSchemaRoot的行数
      for (Field field : vectorSchemaRoot.getSchema().getFields()) { // 遍历Schema中的每个字段
        FieldVector vector = vectorSchemaRoot.getVector(field.getName()); // 获取字段对应的向量对象
        switch (vector.getMinorType()) { // 根据向量类型选择对应的处理方法
        case INT: // 如果是INT类型
          intField(vector, numRows); // 调用intField方法填充INT向量数据
          break; // 跳出switch
        case FLOAT4: // 如果是FLOAT类型(单精度浮点数)
          floatField(vector, numRows); // 调用floatField方法填充FLOAT向量数据
          break; // 跳出switch
        case VARCHAR: // 如果是VARCHAR类型
          varCharField(vector, numRows); // 调用varCharField方法填充VARCHAR向量数据
          break; // 跳出switch
        case BIGINT: // 如果是BIGINT类型
          longField(vector, numRows); // 调用longField方法填充BIGINT向量数据
          break; // 跳出switch
        default: // 其他不支持的数据类型
          throw new IllegalStateException("Not supported type yet: " + vector.getMinorType()); // 抛出异常,提示不支持该类型
        }
      }
      arrowFileWriter.writeBatch(); // 将当前批次数据写入文件
      i += numRows; // 更新已写入的行数
    }
    arrowFileWriter.end(); // 结束Arrow文件写入,写入文件尾
    arrowFileWriter.close(); // 关闭Arrow文件写入器
    fileOutputStream.flush(); // 刷新文件输出流,确保所有数据写入磁盘
    fileOutputStream.close(); // 关闭文件输出流
  }

  public void writeArrowDataType(File file) throws IOException { // 写入包含所有数据类型的Arrow测试数据文件
    FileOutputStream fileOutputStream = new FileOutputStream(file); // 创建文件输出流,用于写入Arrow文件
    Schema arrowSchema = makeArrowDateTypeSchema(); // 创建包含日期时间类型的Schema
    VectorSchemaRoot vectorSchemaRoot = // 创建VectorSchemaRoot对象
        VectorSchemaRoot.create(arrowSchema, new RootAllocator(Integer.MAX_VALUE)); // 使用最大整数作为内存分配器限制
    ArrowFileWriter arrowFileWriter = // 创建Arrow文件写入器
        new ArrowFileWriter(vectorSchemaRoot, null, fileOutputStream.getChannel());

    arrowFileWriter.start(); // 启动Arrow文件写入器,写入文件头

    for (int i = 0; i < this.entries;) { // 循环生成数据,直到达到总条目数
      int numRows = Math.min(this.batchSize, this.entries - i); // 计算当前批次应写入的行数
      vectorSchemaRoot.setRowCount(numRows); // 设置VectorSchemaRoot的行数
      for (Field field : vectorSchemaRoot.getSchema().getFields()) { // 遍历Schema中的每个字段
        FieldVector vector = vectorSchemaRoot.getVector(field.getName()); // 获取字段对应的向量对象
        switch (field.getName()) { // 根据字段名选择对应的处理方法
        case "tinyIntField": // 如果是TINYINT字段
          tinyIntField(vector, numRows); // 调用tinyIntField方法填充TINYINT向量数据
          break; // 跳出switch
        case "smallIntField": // 如果是SMALLINT字段
          smallIntFiled(vector, numRows); // 调用smallIntFiled方法填充SMALLINT向量数据
          break; // 跳出switch
        case "intField": // 如果是INT字段
          intField(vector, numRows); // 调用intField方法填充INT向量数据
          break; // 跳出switch
        case "floatField": // 如果是FLOAT字段
          floatField(vector, numRows); // 调用floatField方法填充FLOAT向量数据
          break; // 跳出switch
        case "stringField": // 如果是VARCHAR字段
          varCharField(vector, numRows); // 调用varCharField方法填充VARCHAR向量数据
          break; // 跳出switch
        case "longField": // 如果是BIGINT字段
          longField(vector, numRows); // 调用longField方法填充BIGINT向量数据
          break; // 跳出switch
        case "doubleField": // 如果是DOUBLE字段
          doubleField(vector, numRows); // 调用doubleField方法填充DOUBLE向量数据
          break; // 跳出switch
        case "booleanField": // 如果是BOOLEAN字段
          booleanField(vector, numRows); // 调用booleanField方法填充BOOLEAN向量数据
          break; // 跳出switch
        case "decimalField": // 如果是DECIMAL字段
          decimalField(vector, numRows); // 调用decimalField方法填充DECIMAL向量数据
          break; // 跳出switch
        case "decimalField2": // 如果是DECIMAL字段2
          decimalField2(vector, numRows); // 调用decimalField2方法填充DECIMAL向量数据
          break; // 跳出switch
        case "dateField": // 如果是DATE字段
          dateField(vector, numRows); // 调用dateField方法填充DATE向量数据
          break; // 跳出switch
        case "timeField": // 如果是TIME字段
          timeField(vector, numRows); // 调用timeField方法填充TIME向量数据
          break; // 跳出switch
        default: // 其他不支持的字段
          throw new IllegalStateException("Not supported type yet: " + vector.getMinorType()); // 抛出异常,提示不支持该类型
        }
      }
      arrowFileWriter.writeBatch(); // 将当前批次数据写入文件
      i += numRows; // 更新已写入的行数
    }
    arrowFileWriter.end(); // 结束Arrow文件写入,写入文件尾
    arrowFileWriter.close(); // 关闭Arrow文件写入器
    fileOutputStream.flush(); // 刷新文件输出流,确保所有数据写入磁盘
    fileOutputStream.close(); // 关闭文件输出流
  }

  private void tinyIntField(FieldVector fieldVector, int rowCount) { // 填充TINYINT类型字段的数据
    TinyIntVector tinyIntVector = (TinyIntVector) fieldVector; // 将通用向量转换为TINYINT向量
    tinyIntVector.setInitialCapacity(rowCount); // 设置向量的初始容量,避免频繁扩容
    tinyIntVector.allocateNew(); // 为向量分配新的内存空间
    for (int i = 0; i < rowCount; i++) { // 循环填充每行数据
      tinyIntVector.set(i, this.tinyIntValue); // 设置第i行的值为当前tinyIntValue
      this.tinyIntValue++; // 递增tinyIntValue,生成递增序列
    }
    fieldVector.setValueCount(rowCount); // 设置向量的有效值数量
  }

  private void smallIntFiled(FieldVector fieldVector, int rowCount) { // 填充SMALLINT类型字段的数据
    SmallIntVector smallIntVector = (SmallIntVector) fieldVector; // 将通用向量转换为SMALLINT向量
    smallIntVector.setInitialCapacity(rowCount); // 设置向量的初始容量
    smallIntVector.allocateNew(); // 为向量分配新的内存空间
    for (int i = 0; i < rowCount; i++) { // 循环填充每行数据
      smallIntVector.set(i, this.smallIntValue); // 设置第i行的值为当前smallIntValue
      this.smallIntValue++; // 递增smallIntValue,生成递增序列
    }
    fieldVector.setValueCount(rowCount); // 设置向量的有效值数量
  }

  private void intField(FieldVector fieldVector, int rowCount) { // 填充INT类型字段的数据
    IntVector intVector = (IntVector) fieldVector; // 将通用向量转换为INT向量
    intVector.setInitialCapacity(rowCount); // 设置向量的初始容量
    intVector.allocateNew(); // 为向量分配新的内存空间
    for (int i = 0; i < rowCount; i++) { // 循环填充每行数据
      intVector.set(i, 1, intValue); // 设置第i行的值为1和intValue(注意:这里可能有bug,应该只传一个值)
      this.intValue++; // 递增intValue,生成递增序列
    }
    fieldVector.setValueCount(rowCount); // 设置向量的有效值数量
  }

  private void floatField(FieldVector fieldVector, int rowCount) { // 填充FLOAT类型字段的数据
    FloatingPointVector floatingPointVector = (FloatingPointVector) fieldVector; // 将通用向量转换为浮点数向量
    floatingPointVector.setInitialCapacity(rowCount); // 设置向量的初始容量
    floatingPointVector.allocateNew(); // 为向量分配新的内存空间
    for (int i = 0; i < rowCount; i++) { // 循环填充每行数据
      float value = this.floatValue; // 获取当前floatValue
      floatingPointVector.setWithPossibleTruncate(i, value); // 设置第i行的值,如果需要则进行截断
      this.floatValue++; // 递增floatValue,生成递增序列
    }
    fieldVector.setValueCount(rowCount); // 设置向量的有效值数量
  }

  private void varCharField(FieldVector fieldVector, int rowCount) { // 填充VARCHAR类型字段的数据
    VarCharVector varCharVector = (VarCharVector) fieldVector; // 将通用向量转换为VARCHAR向量
    varCharVector.setInitialCapacity(rowCount); // 设置向量的初始容量
    varCharVector.allocateNew(); // 为向量分配新的内存空间
    for (int i = 0; i < rowCount; i++) { // 循环填充每行数据
      String value = String.valueOf(this.stringValue); // 将stringValue转换为字符串
      varCharVector.set(i, new Text(value)); // 设置第i行的值为转换后的文本
      this.stringValue++; // 递增stringValue,生成递增序列
    }
    fieldVector.setValueCount(rowCount); // 设置向量的有效值数量
  }

  private void longField(FieldVector fieldVector, int rowCount) { // 填充BIGINT类型字段的数据
    BigIntVector longVector = (BigIntVector) fieldVector; // 将通用向量转换为BIGINT向量
    longVector.setInitialCapacity(rowCount); // 设置向量的初始容量
    longVector.allocateNew(); // 为向量分配新的内存空间
    for (int i = 0; i < rowCount; i++) { // 循环填充每行数据
      longVector.set(i, this.longValue); // 设置第i行的值为当前longValue
      this.longValue++; // 递增longValue,生成递增序列
    }
    fieldVector.setValueCount(rowCount); // 设置向量的有效值数量
  }

  private void doubleField(FieldVector fieldVector, int rowCount) { // 填充DOUBLE类型字段的数据
    Float8Vector float8Vector = (Float8Vector) fieldVector; // 将通用向量转换为DOUBLE向量(64位浮点数)
    float8Vector.setInitialCapacity(rowCount); // 设置向量的初始容量
    float8Vector.allocateNew(); // 为向量分配新的内存空间
    for (int i = 0; i < rowCount; i++) { // 循环填充每行数据
      float8Vector.set(i, this.doubleValue); // 设置第i行的值为当前doubleValue
      this.doubleValue++; // 递增doubleValue,生成递增序列
    }
    fieldVector.setValueCount(rowCount); // 设置向量的有效值数量
  }

  private void booleanField(FieldVector fieldVector, int rowCount) { // 填充BOOLEAN类型字段的数据
    BitVector bitVector = (BitVector) fieldVector; // 将通用向量转换为位向量(用于存储布尔值)
    bitVector.setInitialCapacity(rowCount); // 设置向量的初始容量
    bitVector.allocateNew(); // 为向量分配新的内存空间
    for (int i = 0; i < rowCount; i++) { // 循环填充每行数据
      if (i % 3 == 0) { // 如果是第3的倍数行
        bitVector.setNull(i); // 设置该行为NULL值
      } else { // 其他行
        bitVector.set(i, this.booleanValue ? 1 : 0); // 设置该行为1或0,对应true或false
      }
      this.booleanValue = !this.booleanValue; // 切换booleanValue,生成交替的true/false序列
    }
    fieldVector.setValueCount(rowCount); // 设置向量的有效值数量
  }

  private void decimalField(FieldVector fieldVector, int rowCount) { // 填充DECIMAL类型字段的数据
    DecimalVector decimalVector = (DecimalVector) fieldVector; // 将通用向量转换为DECIMAL向量
    decimalVector.setInitialCapacity(rowCount); // 设置向量的初始容量
    decimalVector.allocateNew(); // 为向量分配新的内存空间
    for (int i = 0; i < rowCount; i++) { // 循环填充每行数据
      decimalVector.set(i, this.decimalValue.setScale(2)); // 设置第i行的值为当前decimalValue,并设置小数点后2位
      this.decimalValue = this.decimalValue.add(BigDecimal.ONE); // 递增decimalValue,每次加1
    }
    fieldVector.setValueCount(rowCount); // 设置向量的有效值数量
  }

  private void decimalField2(FieldVector fieldVector, int rowCount) { // 填充DECIMAL类型字段2的数据(小数点后3位)
    DecimalVector decimalVector = (DecimalVector) fieldVector; // 将通用向量转换为DECIMAL向量
    decimalVector.setInitialCapacity(rowCount); // 设置向量的初始容量
    decimalVector.allocateNew(); // 为向量分配新的内存空间
    for (int i = 0; i < rowCount; i++) { // 循环填充每行数据
      decimalVector.set(i, this.decimalValue.setScale(3)); // 设置第i行的值为当前decimalValue,并设置小数点后3位
      this.decimalValue = this.decimalValue.add(BigDecimal.ONE); // 递增decimalValue,每次加1
    }
    fieldVector.setValueCount(rowCount); // 设置向量的有效值数量
  }

  private void dateField(FieldVector fieldVector, int rowCount) { // 填充DATE类型字段的数据
    DateDayVector dateDayVector = (DateDayVector) fieldVector; // 将通用向量转换为日期向量(以天为单位)
    dateDayVector.setInitialCapacity(rowCount); // 设置向量的初始容量
    dateDayVector.allocateNew(); // 为向量分配新的内存空间
    for (int i = 0; i < rowCount; i++) { // 循环填充每行数据
      dateDayVector.set(i, i); // 设置第i行的值为i,表示从第0天开始的日期序列
    }
    fieldVector.setValueCount(rowCount); // 设置向量的有效值数量
  }

  private void timeField(FieldVector fieldVector, int rowCount) { // 填充TIME类型字段的数据
    TimeSecVector timeVector = (TimeSecVector) fieldVector; // 将通用向量转换为时间向量(以秒为单位)
    timeVector.setInitialCapacity(rowCount); // 设置向量的初始容量
    timeVector.allocateNew(); // 为向量分配新的内存空间
    for (int i = 0; i < rowCount; i++) { // 循环填充每行数据
      timeVector.set(i, i * 1000); // 设置第i行的值为i*1000,表示以秒为单位的时间序列
    }
    fieldVector.setValueCount(rowCount); // 设置向量的有效值数量
  }
}