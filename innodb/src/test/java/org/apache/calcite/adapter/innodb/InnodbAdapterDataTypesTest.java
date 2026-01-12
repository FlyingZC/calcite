/*
 * Licensed to the Apache Software Foundation (ASF) under one or more  // 授权给Apache软件基金会（ASF），根据一个或多个
 * contributor license agreements.  See the NOTICE file distributed with  // 贡献者许可协议。查看随此工作分发的NOTICE文件
 * this work for additional information regarding copyright ownership.  // 以获取有关版权所有权的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0  // ASF根据Apache许可证2.0版将此文件授权给您
 * (the "License"); you may not use this file except in compliance with  // （"许可证"）；除非遵守许可证，否则您不得使用此文件
 * the License.  You may obtain a copy of the License at  // 您可以在以下位置获取许可证副本：
 *
 * http://www.apache.org/licenses/LICENSE-2.0  // Apache许可证2.0的网址
 *
 * Unless required by applicable law or agreed to in writing, software  // 除非适用法律要求或书面同意，否则
 * distributed under the License is distributed on an "AS IS" BASIS,  // 根据许可证分发的软件是按"原样"基础分发的，
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  // 不附任何明示或暗示的保证或条件
 * See the License for the specific language governing permissions and  // 查看许可证以了解有关许可的特定语言
 * limitations under the License.  // 和许可证下的限制
 */
package org.apache.calcite.adapter.innodb;  // 定义包名，属于InnoDB适配器包

import org.apache.calcite.test.CalciteAssert;  // 导入Calcite断言工具类，用于测试SQL查询
import org.apache.calcite.util.Sources;  // 导入Sources工具类，用于处理资源文件路径

import org.apache.commons.lang3.StringUtils;  // 导入Apache Commons Lang的StringUtils工具类，用于字符串操作

import com.alibaba.innodb.java.reader.util.Utils;  // 导入Alibaba InnoDB Reader的Utils工具类，用于日期时间解析
import com.google.common.collect.ImmutableMap;  // 导入Google Guava的ImmutableMap类，用于创建不可变映射

import org.junit.jupiter.api.Test;  // 导入JUnit 5的Test注解，用于标记测试方法

import java.time.Instant;  // 导入Instant类，表示时间线上的一个瞬时点
import java.time.LocalDateTime;  // 导入LocalDateTime类，表示不带时区的日期时间
import java.time.OffsetDateTime;  // 导入OffsetDateTime类，表示带时区偏移的日期时间
import java.time.ZoneId;  // 导入ZoneId类，表示时区ID
import java.time.ZoneOffset;  // 导入ZoneOffset类，表示时区偏移量
import java.time.zone.ZoneRules;  // 导入ZoneRules类，表示时区规则

import static java.util.Objects.requireNonNull;  // 导入requireNonNull静态方法，用于检查对象非空

/**
 * Tests for the {@code org.apache.calcite.adapter.innodb} package related to data types.
 * 测试org.apache.calcite.adapter.innodb包中与数据类型相关的功能
 * 本测试类用于验证InnoDB适配器对各种MySQL数据类型的支持情况
 * 包括整数类型、浮点类型、日期时间类型、字符串类型、二进制类型、枚举和集合类型等
 *
 * <p>Will read InnoDB data file {@code test_types.ibd}.
 * 将读取InnoDB数据文件test_types.ibd，该文件包含了各种数据类型的测试数据
 * 通过查询测试表test_types来验证数据类型映射和转换是否正确
 */
public class InnodbAdapterDataTypesTest {

  // 定义InnoDB模型的静态不可变映射，用于配置Calcite连接到InnoDB数据源
  private static final ImmutableMap<String, String> INNODB_MODEL =
      ImmutableMap.of("model",  // 模型配置的键名，指定模型文件的路径
          Sources.of(  // Sources工具类用于处理资源文件
                  requireNonNull(  // 确保资源URL不为null，如果为null则抛出NullPointerException
                      InnodbAdapterTest.class.getResource("/model.json"),  // 从类路径加载model.json配置文件
                      "url"))  // 错误提示信息
              .file().getAbsolutePath());  // 获取资源文件的绝对路径

  // 测试方法：验证test_types表的行类型定义是否正确
  // 该方法测试InnoDB适配器能否正确识别和映射MySQL的各种数据类型到Calcite的数据类型
  @Test void testTypesRowType() {
    CalciteAssert.that()  // 创建Calcite断言构建器，用于测试SQL查询
        .with(INNODB_MODEL)  // 配置InnoDB模型，指定数据源连接信息
        .query("select * from \"test_types\"")  // 执行SQL查询，选择test_types表的所有字段
        .typeIs("[id INTEGER NOT NULL, "  // 验证查询结果的行类型，id字段映射为INTEGER类型且不允许为空
            + "f_tinyint TINYINT NOT NULL, "  // MySQL的TINYINT类型映射为Calcite的TINYINT类型
            + "f_smallint SMALLINT NOT NULL, "  // MySQL的SMALLINT类型映射为Calcite的SMALLINT类型
            + "f_mediumint INTEGER NOT NULL, "  // MySQL的MEDIUMINT类型映射为Calcite的INTEGER类型
            + "f_int INTEGER NOT NULL, "  // MySQL的INT类型映射为Calcite的INTEGER类型
            + "f_bigint BIGINT NOT NULL, "  // MySQL的BIGINT类型映射为Calcite的BIGINT类型
            + "f_datetime TIMESTAMP NOT NULL, "  // MySQL的DATETIME类型映射为Calcite的TIMESTAMP类型
            + "f_timestamp TIMESTAMP_WITH_LOCAL_TIME_ZONE NOT NULL, "  // MySQL的TIMESTAMP类型映射为带时区的TIMESTAMP
            + "f_time TIME NOT NULL, "  // MySQL的TIME类型映射为Calcite的TIME类型
            + "f_year SMALLINT NOT NULL, "  // MySQL的YEAR类型映射为Calcite的SMALLINT类型
            + "f_date DATE NOT NULL, "  // MySQL的DATE类型映射为Calcite的DATE类型
            + "f_float REAL NOT NULL, "  // MySQL的FLOAT类型映射为Calcite的REAL类型
            + "f_double DOUBLE NOT NULL, "  // MySQL的DOUBLE类型映射为Calcite的DOUBLE类型
            + "f_decimal1 DECIMAL NOT NULL, "  // MySQL的DECIMAL类型映射为Calcite的DECIMAL类型
            + "f_decimal2 DECIMAL NOT NULL, "  // DECIMAL类型支持精度和标度的配置
            + "f_decimal3 DECIMAL NOT NULL, "  // DECIMAL类型可以存储大数值
            + "f_decimal4 DECIMAL NOT NULL, "  // DECIMAL类型保持数值的精确性
            + "f_decimal5 DECIMAL NOT NULL, "  // DECIMAL类型适合财务计算
            + "f_decimal6 DECIMAL, "  // DECIMAL类型允许为空
            + "f_varchar VARCHAR NOT NULL, "  // MySQL的VARCHAR类型映射为Calcite的VARCHAR类型
            + "f_varchar_overflow VARCHAR NOT NULL, "  // 测试VARCHAR溢出情况的处理
            + "f_varchar_null VARCHAR, "  // VARCHAR类型允许为空
            + "f_char_32 CHAR NOT NULL, "  // MySQL的CHAR(32)类型映射为Calcite的CHAR类型
            + "f_char_255 CHAR NOT NULL, "  // MySQL的CHAR(255)类型映射为Calcite的CHAR类型
            + "f_char_null CHAR, "  // CHAR类型允许为空
            + "f_boolean BOOLEAN NOT NULL, "  // MySQL的BOOLEAN类型映射为Calcite的BOOLEAN类型
            + "f_bool BOOLEAN NOT NULL, "  // MySQL的BOOL类型（BOOLEAN的别名）映射为BOOLEAN类型
            + "f_tinytext VARCHAR NOT NULL, "  // MySQL的TINYTEXT类型映射为Calcite的VARCHAR类型
            + "f_text VARCHAR NOT NULL, "  // MySQL的TEXT类型映射为Calcite的VARCHAR类型
            + "f_mediumtext VARCHAR NOT NULL, "  // MySQL的MEDIUMTEXT类型映射为Calcite的VARCHAR类型
            + "f_longtext VARCHAR NOT NULL, "  // MySQL的LONGTEXT类型映射为Calcite的VARCHAR类型
            + "f_tinyblob VARBINARY NOT NULL, "  // MySQL的TINYBLOB类型映射为Calcite的VARBINARY类型
            + "f_blob VARBINARY NOT NULL, "  // MySQL的BLOB类型映射为Calcite的VARBINARY类型
            + "f_mediumblob VARBINARY NOT NULL, "  // MySQL的MEDIUMBLOB类型映射为Calcite的VARBINARY类型
            + "f_longblob VARBINARY NOT NULL, "  // MySQL的LONGBLOB类型映射为Calcite的VARBINARY类型
            + "f_varbinary VARBINARY NOT NULL, "  // MySQL的VARBINARY类型映射为Calcite的VARBINARY类型
            + "f_varbinary_overflow VARBINARY NOT NULL, "  // 测试VARBINARY溢出情况的处理
            + "f_enum VARCHAR NOT NULL, "  // MySQL的ENUM类型映射为Calcite的VARCHAR类型
            + "f_set VARCHAR NOT NULL]");  // MySQL的SET类型映射为Calcite的VARCHAR类型
  }

  // 测试方法：验证test_types表中各种数据类型的实际值是否正确读取
  // 该方法测试InnoDB适配器能否正确读取和转换MySQL数据文件中的实际数据值
  // 包括正数、负数、日期时间、字符串、二进制数据等各种类型的测试用例
  @Test void testTypesValues() {
    CalciteAssert.that()  // 创建Calcite断言构建器
        .with(INNODB_MODEL)  // 配置InnoDB模型
        .query("select * from \"test_types\"")  // 执行查询
        .returnsOrdered(  // 验证返回的结果按顺序匹配预期值
            "id=1; "  // 第一行数据：id字段值为1
                + "f_tinyint=100; "  // TINYINT类型：测试正整数100
                + "f_smallint=10000; "  // SMALLINT类型：测试正整数10000
                + "f_mediumint=1000000; "  // MEDIUMINT类型：测试正整数1000000
                + "f_int=10000000; "  // INT类型：测试正整数10000000
                + "f_bigint=100000000000; "  // BIGINT类型：测试大整数
                + "f_datetime=2019-10-02 10:59:59; "  // DATETIME类型：测试日期时间值
                + "f_timestamp=" + expectedLocalTime("1988-11-23 22:10:08") + "; "  // TIMESTAMP类型：测试时间戳，需要转换为本地时区
                + "f_time=00:36:52; "  // TIME类型：测试时间值
                + "f_year=2012; "  // YEAR类型：测试年份值
                + "f_date=2020-01-29; "  // DATE类型：测试日期值
                + "f_float=0.9876543; "  // FLOAT类型：测试单精度浮点数
                + "f_double=1.23456789012345E9; "  // DOUBLE类型：测试双精度浮点数，使用科学计数法
                + "f_decimal1=123456; "  // DECIMAL类型：测试整数精度
                + "f_decimal2=12345.67890; "  // DECIMAL类型：测试带小数的精度
                + "f_decimal3=12345678901; "  // DECIMAL类型：测试大整数
                + "f_decimal4=123.100; "  // DECIMAL类型：测试保留小数位数
                + "f_decimal5=12346; "  // DECIMAL类型：测试四舍五入
                + "f_decimal6=12345.1234567890123456789012345; "  // DECIMAL类型：测试高精度小数
                + "f_varchar=c" + StringUtils.repeat('x', 31) + "; "  // VARCHAR类型：测试ASCII字符重复
                + "f_varchar_overflow=c" + StringUtils.repeat("データ", 300) + "; "  // VARCHAR类型：测试日文字符和溢出处理
                + "f_varchar_null=null; "  // VARCHAR类型：测试NULL值
                + "f_char_32=c" + StringUtils.repeat("данные", 2) + "; "  // CHAR(32)类型：测试西里尔字母字符
                + "f_char_255=c" + StringUtils.repeat("数据", 100) + "; "  // CHAR(255)类型：测试中文字符
                + "f_char_null=null; "  // CHAR类型：测试NULL值
                + "f_boolean=false; "  // BOOLEAN类型：测试false值
                + "f_bool=true; "  // BOOL类型：测试true值
                + "f_tinytext=c" + StringUtils.repeat("Data", 50) + "; "  // TINYTEXT类型：测试小文本（最多255字节）
                + "f_text=c" + StringUtils.repeat("Daten", 200) + "; "  // TEXT类型：测试中等文本（最多65535字节）
                + "f_mediumtext=c" + StringUtils.repeat("Datos", 200) + "; "  // MEDIUMTEXT类型：测试中等大文本（最多16MB）
                + "f_longtext=c" + StringUtils.repeat("Les données", 800) + "; "  // LONGTEXT类型：测试大文本（最多4GB）
                + "f_tinyblob="  // TINYBLOB类型：测试小二进制数据
                + genByteArrayString("63", (byte) 0x0a, 100) + "; "  // 生成100个0x0a字节的十六进制字符串
                + "f_blob="  // BLOB类型：测试中等二进制数据
                + genByteArrayString("63", (byte) 0x0b, 400) + "; "  // 生成400个0x0b字节的十六进制字符串
                + "f_mediumblob="  // MEDIUMBLOB类型：测试中等大二进制数据
                + genByteArrayString("63", (byte) 0x0c, 800) + "; "  // 生成800个0x0c字节的十六进制字符串
                + "f_longblob="  // LONGBLOB类型：测试大二进制数据
                + genByteArrayString("63", (byte) 0x0d, 1000) + "; "  // 生成1000个0x0d字节的十六进制字符串
                + "f_varbinary="  // VARBINARY类型：测试可变长度二进制数据
                + genByteArrayString("63", (byte) 0x0e, 8) + "; "  // 生成8个0x0e字节的十六进制字符串
                + "f_varbinary_overflow="  // VARBINARY类型：测试溢出情况
                + genByteArrayString("63", (byte) 0xff, 100) + "; "  // 生成100个0xff字节的十六进制字符串
                + "f_enum=MYSQL; "  // ENUM类型：测试枚举值MYSQL
                + "f_set=z",  // SET类型：测试集合值z
            "id=2; "  // 第二行数据：id字段值为2
                + "f_tinyint=-100; "  // TINYINT类型：测试负整数-100
                + "f_smallint=-10000; "  // SMALLINT类型：测试负整数-10000
                + "f_mediumint=-1000000; "  // MEDIUMINT类型：测试负整数-1000000
                + "f_int=-10000000; "  // INT类型：测试负整数-10000000
                + "f_bigint=-9223372036854775807; "  // BIGINT类型：测试接近最小值的负数
                + "f_datetime=2255-01-01 12:12:12; "  // DATETIME类型：测试未来日期时间
                + "f_timestamp=" + expectedLocalTime("2020-01-01 00:00:00") + "; "  // TIMESTAMP类型：测试2020年的时间戳
                + "f_time=23:11:00; "  // TIME类型：测试接近午夜的时间
                + "f_year=0; "  // YEAR类型：测试年份0（MySQL中YEAR的最小值）
                + "f_date=1970-01-01; "  // DATE类型：测试Unix纪元日期
                + "f_float=-1.2345678E7; "  // FLOAT类型：测试负浮点数，使用科学计数法
                + "f_double=-1.234567890123456E9; "  // DOUBLE类型：测试负双精度浮点数
                + "f_decimal1=9; "  // DECIMAL类型：测试小整数
                + "f_decimal2=-567.89100; "  // DECIMAL类型：测试负小数
                + "f_decimal3=987654321; "  // DECIMAL类型：测试另一个大整数
                + "f_decimal4=456.000; "  // DECIMAL类型：测试带尾随零的小数
                + "f_decimal5=0; "  // DECIMAL类型：测试零值
                + "f_decimal6=-0.0123456789012345678912345; "  // DECIMAL类型：测试高精度负小数
                + "f_varchar=d" + StringUtils.repeat('y', 31) + "; "  // VARCHAR类型：测试ASCII字符y重复
                + "f_varchar_overflow=d" + StringUtils.repeat("データ", 300) + "; "  // VARCHAR类型：测试日文字符和溢出处理
                + "f_varchar_null=null; "  // VARCHAR类型：测试NULL值
                + "f_char_32=d" + StringUtils.repeat("данные", 2) + "; "  // CHAR(32)类型：测试西里尔字母字符
                + "f_char_255=d" + StringUtils.repeat("数据", 100) + "; "  // CHAR(255)类型：测试中文字符
                + "f_char_null=null; "  // CHAR类型：测试NULL值
                + "f_boolean=false; "  // BOOLEAN类型：测试false值
                + "f_bool=true; "  // BOOL类型：测试true值
                + "f_tinytext=d" + StringUtils.repeat("Data", 50) + "; "  // TINYTEXT类型：测试小文本
                + "f_text=d" + StringUtils.repeat("Daten", 200) + "; "  // TEXT类型：测试中等文本
                + "f_mediumtext=d" + StringUtils.repeat("Datos", 200) + "; "  // MEDIUMTEXT类型：测试中等大文本
                + "f_longtext=d" + StringUtils.repeat("Les données", 800) + "; "  // LONGTEXT类型：测试大文本
                + "f_tinyblob="  // TINYBLOB类型：测试小二进制数据
                + genByteArrayString("64", (byte) 0x0a, 100) + "; "  // 生成100个0x0a字节的十六进制字符串
                + "f_blob="  // BLOB类型：测试中等二进制数据
                + genByteArrayString("64", (byte) 0x0b, 400) + "; "  // 生成400个0x0b字节的十六进制字符串
                + "f_mediumblob="  // MEDIUMBLOB类型：测试中等大二进制数据
                + genByteArrayString("64", (byte) 0x0c, 800) + "; "  // 生成800个0x0c字节的十六进制字符串
                + "f_longblob="  // LONGBLOB类型：测试大二进制数据
                + genByteArrayString("64", (byte) 0x0d, 1000) + "; "  // 生成1000个0x0d字节的十六进制字符串
                + "f_varbinary="  // VARBINARY类型：测试可变长度二进制数据
                + genByteArrayString("64", (byte) 0x0e, 8) + "; "  // 生成8个0x0e字节的十六进制字符串
                + "f_varbinary_overflow="  // VARBINARY类型：测试溢出情况
                + genByteArrayString("64", (byte) 0xff, 100) + "; "  // 生成100个0xff字节的十六进制字符串
                + "f_enum=Hello; "  // ENUM类型：测试枚举值Hello
                + "f_set=a,e,i,o,u");  // SET类型：测试多个值的集合a,e,i,o,u
  }

  // 辅助方法：生成字节数组的十六进制字符串表示
  // 用于测试BLOB和VARBINARY类型的数据，将字节数组转换为可读的十六进制字符串格式
  // 参数说明：
  //   prefix - 字符串前缀，用于标识不同的二进制字段（如"63"表示第一行，"64"表示第二行）
  //   b - 要重复的字节值（0-255）
  //   repeat - 重复次数，控制生成的字符串长度
  // 返回值：格式为prefix + 十六进制字符串的结果
  private String genByteArrayString(String prefix, byte b, int repeat) {
    StringBuilder str = new StringBuilder();  // 创建字符串构建器，用于高效拼接字符串
    str.append(prefix);  // 添加前缀标识
    for (int i = 0; i < repeat; i++) {  // 循环repeat次，生成指定长度的十六进制字符串
      String hexString = Integer.toHexString(b & 0xFF);  // 将字节转换为十六进制字符串，使用& 0xFF确保处理负数
      if (hexString.length() < 2) {  // 如果十六进制字符串长度小于2（即数值小于16）
        str.append("0");  // 在前面补0，确保每个字节都是两位十六进制数
      }
      str.append(hexString);  // 添加十六进制字符串
    }
    return str.toString();  // 返回生成的十六进制字符串
  }

  // 静态辅助方法：将UTC时间字符串转换为本地时区的时间字符串
  // 用于处理TIMESTAMP类型的数据，因为MySQL的TIMESTAMP存储为UTC时间，查询时需要转换为本地时区
  // 参数说明：
  //   dateTime - UTC时间字符串，格式为"yyyy-MM-dd HH:mm:ss"
  // 返回值：本地时区的时间字符串，格式与MySQL的TIMESTAMP显示格式一致
  private static String expectedLocalTime(String dateTime) {
    ZoneRules rules = ZoneId.systemDefault().getRules();  // 获取系统默认时区的规则
    LocalDateTime ldt = Utils.parseDateTimeText(dateTime);  // 解析日期时间字符串为LocalDateTime对象
    Instant instant = ldt.toInstant(ZoneOffset.of("+00:00"));  // 将LocalDateTime转换为UTC时区的Instant对象
    ZoneOffset standardOffset = rules.getOffset(instant);  // 根据Instant获取该时刻的标准时区偏移量
    OffsetDateTime odt = instant.atOffset(standardOffset);  // 创建带时区偏移的OffsetDateTime对象
    return odt.toLocalDateTime().format(Utils.TIME_FORMAT_TIMESTAMP[0]);  // 转换为LocalDateTime并格式化为字符串返回
  }
}
