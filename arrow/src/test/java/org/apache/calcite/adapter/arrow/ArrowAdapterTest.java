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
// Apache许可证头部声明,指定版权和使用条款
package org.apache.calcite.adapter.arrow;  // 包声明,该类属于org.apache.calcite.adapter.arrow包

import org.apache.calcite.jdbc.JavaTypeFactoryImpl;  // 导入Java类型工厂实现类,用于创建Java类型
import org.apache.calcite.rel.type.RelDataType;  // 导入关系数据类型接口,表示Calcite中的类型系统
import org.apache.calcite.rel.type.RelDataTypeFactory;  // 导入关系数据类型工厂接口,用于创建类型实例
import org.apache.calcite.rel.type.RelDataTypeSystem;  // 导入关系数据类型系统,定义类型系统规则
import org.apache.calcite.schema.Table;  // 导入表接口,表示数据源表
import org.apache.calcite.test.CalciteAssert;  // 导入Calcite断言工具,用于测试SQL查询
import org.apache.calcite.util.Bug;  // 导入Bug工具类,用于管理已知问题和修复状态
import org.apache.calcite.util.Sources;  // 导入Sources工具类,用于处理资源文件路径

import com.google.common.collect.ImmutableMap;  // 导入Google Guava的不可变Map,用于创建不可修改的映射

import org.junit.jupiter.api.BeforeAll;  // 导入JUnit5的BeforeAll注解,标记在所有测试前执行的方法
import org.junit.jupiter.api.Disabled;  // 导入JUnit5的Disabled注解,用于禁用特定测试
import org.junit.jupiter.api.Test;  // 导入JUnit5的Test注解,标记测试方法
import org.junit.jupiter.api.extension.ExtendWith;  // 导入JUnit5的ExtendWith注解,用于注册扩展
import org.junit.jupiter.api.io.TempDir;  // 导入JUnit5的TempDir注解,用于创建临时目录
import org.junit.jupiter.api.parallel.Execution;  // 导入JUnit5的Execution注解,控制测试执行模式
import org.junit.jupiter.api.parallel.ExecutionMode;  // 导入JUnit5的ExecutionMode枚举,定义执行模式

import java.io.File;  // 导入Java File类,用于文件和目录操作
import java.io.IOException;  // 导入Java IOException类,处理IO异常
import java.net.URL;  // 导入Java URL类,处理统一资源定位符
import java.nio.file.Files;  // 导入NIO Files类,提供文件操作方法
import java.nio.file.Path;  // 导入NIO Path类,表示文件系统路径
import java.sql.SQLException;  // 导入SQL异常类,处理数据库相关异常
import java.util.Map;  // 导入Java Map接口,表示键值对映射

import static org.apache.calcite.test.Matchers.isListOf;  // 导入静态匹配器,用于验证列表内容

import static org.hamcrest.MatcherAssert.assertThat;  // 导入静态断言方法,用于验证测试结果

import static java.util.Objects.requireNonNull;  // 导入静态方法,用于检查对象非空

/**
 * Tests for the Apache Arrow adapter.
 * Apache Arrow适配器的测试类
 * 
 * 该类专门用于测试Calcite的Arrow适配器功能,Arrow是一个跨语言的列式内存数据格式
 * 测试覆盖了Arrow适配器的各种功能,包括:
 * - Schema读取和验证
 * - 数据投影(Projection)操作
 * - 过滤(Filter)操作
 * - 聚合(Aggregation)操作
 * - 排序(Sort)和限制(Limit)操作
 * - 类型转换和类型支持
 * - 各种SQL操作符的支持情况
 * 
 * 测试使用Jupiter(JUnit 5)框架,并使用ArrowExtension扩展来管理Arrow相关的资源
 */
@Execution(ExecutionMode.SAME_THREAD)  // 指定测试执行模式为同一线程执行,确保测试顺序执行
@ExtendWith(ArrowExtension.class)  // 使用ArrowExtension扩展,该扩展提供了Arrow相关的测试支持
class ArrowAdapterTest {  // ArrowAdapterTest类定义,测试Arrow适配器的各种功能
  private static Map<String, String> arrow;  // 静态成员变量:存储Arrow模型配置的不可变Map,key为配置名,value为配置值
  private static File arrowDataDirectory;  // 静态成员变量:存储Arrow数据文件的目录对象,指向包含Arrow文件的目录

  /**
   * 在所有测试执行前初始化Arrow状态
   * 使用@BeforeAll注解确保该方法在所有测试方法之前执行一次
   * 
   * @param sharedTempDir JUnit5提供的临时目录,用于在测试间共享临时文件
   * @throws IOException 当文件操作失败时抛出
   * @throws SQLException 当数据库操作失败时抛出
   */
  @BeforeAll  // JUnit5注解,标记在所有测试前执行一次的初始化方法
  static void initializeArrowState(@TempDir Path sharedTempDir)  // 静态初始化方法,接收共享临时目录参数
      throws IOException, SQLException {  // 声明可能抛出的异常类型
    URL modelUrl =  // 声明URL变量,用于存储Arrow模型文件的URL
        requireNonNull(  // 调用requireNonNull方法确保对象非空,如果为空则抛出NullPointerException
            ArrowAdapterTest.class.getResource("/arrow-model.json"), "url");  // 从类路径获取arrow-model.json资源文件,如果找不到则抛出异常并提示"url"
    Path sourceModelFilePath = Sources.of(modelUrl).file().toPath();  // 使用Sources工具将URL转换为文件路径,获取源模型文件的Path对象
    Path modelFileTarget = sharedTempDir.resolve("arrow-model.json");  // 在共享临时目录中创建目标模型文件路径,文件名为arrow-model.json
    Files.copy(sourceModelFilePath, modelFileTarget);  // 将源模型文件复制到目标位置,为测试准备模型配置文件

    Path arrowFilesDirectory = sharedTempDir.resolve("arrow");  // 在共享临时目录中创建arrow子目录路径,用于存放Arrow数据文件
    Files.createDirectory(arrowFilesDirectory);  // 创建arrow目录,如果目录已存在则抛出异常
    arrowDataDirectory = arrowFilesDirectory.toFile();  // 将Path对象转换为File对象并赋值给静态成员变量,供后续测试使用

    File dataLocationFile = arrowFilesDirectory.resolve("arrowdata.arrow").toFile();  // 创建Arrow数据文件路径,文件名为arrowdata.arrow
    ArrowDataTest arrowDataGenerator = new ArrowDataTest();  // 创建ArrowDataTest实例,用于生成测试用的Arrow数据
    arrowDataGenerator.writeArrowData(dataLocationFile);  // 调用writeArrowData方法生成并写入Arrow数据到文件
    arrowDataGenerator.writeScottEmpData(arrowFilesDirectory);  // 调用writeScottEmpData方法生成Scott测试数据集(经典的EMP/DEPT表)

    File datatypeLocationFile = arrowFilesDirectory.resolve("arrowdatatype.arrow").toFile();  // 创建Arrow数据类型测试文件路径
    ArrowDataTest arrowtypeDataGenerator = new ArrowDataTest();  // 创建另一个ArrowDataTest实例,用于生成数据类型测试数据
    arrowtypeDataGenerator.writeArrowDataType(datatypeLocationFile);  // 调用writeArrowDataType方法生成包含各种数据类型的Arrow文件

    arrow = ImmutableMap.of("model", modelFileTarget.toAbsolutePath().toString());  // 创建不可变Map,存储模型配置,key为"model",value为模型文件的绝对路径字符串
  }

  /**
   * 测试读取Arrow文件并检查其字段名称
   * 该测试验证ArrowSchema能够正确解析Arrow文件并提取字段信息
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowSchema() {  // 测试方法:验证Arrow Schema读取功能
    ArrowSchema arrowSchema = new ArrowSchema(arrowDataDirectory);  // 创建ArrowSchema实例,传入Arrow数据目录,用于读取Arrow文件的Schema
    Map<String, Table> tableMap = arrowSchema.getTableMap();  // 调用getTableMap方法获取表映射Map,key为表名,value为Table对象
    RelDataTypeFactory typeFactory =  // 创建RelDataTypeFactory实例,用于创建和管理关系数据类型
        new JavaTypeFactoryImpl(RelDataTypeSystem.DEFAULT);  // 使用Java类型工厂实现和默认类型系统创建类型工厂
    RelDataType relDataType = tableMap.get("ARROWDATA").getRowType(typeFactory);  // 从表映射中获取ARROWDATA表,并使用类型工厂获取其行类型(RelDataType)

    assertThat(relDataType.getFieldNames(),  // 使用assertThat断言验证字段名称列表
        isListOf("intField", "stringField", "floatField", "longField"));  // 验证字段名称列表是否为["intField", "stringField", "floatField", "longField"]
  }

  /**
   * 测试投影所有字段(使用SELECT *)
   * 该测试验证Arrow适配器能够正确处理SELECT *查询,返回所有字段的数据
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectAllFields() {  // 测试方法:验证投影所有字段的功能
    String sql = "select * from arrowdata\n";  // 定义SQL查询语句,使用SELECT *选择arrowdata表的所有字段
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划,从ArrowToEnumerableConverter开始
        + "  ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan节点,扫描ARROWDATA表的所有字段(索引0,1,2,3)
    String result = "intField=0; stringField=0; floatField=0.0; longField=0\n"  // 定义预期的查询结果,包含6行数据
        + "intField=1; stringField=1; floatField=1.0; longField=1\n"  // 每行包含4个字段的值
        + "intField=2; stringField=2; floatField=2.0; longField=2\n"  // 字段值与行号对应
        + "intField=3; stringField=3; floatField=3.0; longField=3\n"  // intField和stringField为整数,转换为字符串
        + "intField=4; stringField=4; floatField=4.0; longField=4\n"  // floatField为浮点数,longField为长整数
        + "intField=5; stringField=5; floatField=5.0; longField=5\n";  // 最后一行数据

    CalciteAssert.that()  // 创建CalciteAssert构建器,用于构建和执行测试断言
        .with(arrow)  // 配置Arrow模型,传入之前创建的arrow配置Map
        .query(sql)  // 设置要执行的SQL查询
        .limit(6)  // 限制结果返回前6行
        .returns(result)  // 验证查询结果是否与预期的result字符串匹配
        .explainContains(plan);  // 验证查询执行计划是否包含预期的plan字符串
  }

  /**
   * 测试显式投影所有字段(使用SELECT 字段名列表)
   * 该测试验证Arrow适配器能够正确处理显式列出所有字段的SELECT查询
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectAllFieldsExplicitly() {  // 测试方法:验证显式投影所有字段的功能
    String sql = "select \"intField\", \"stringField\", \"floatField\", \"longField\" "  // 定义SQL查询,显式列出所有4个字段名
        + "from arrowdata\n";  // 从arrowdata表查询
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描所有字段,与SELECT *相同
    String result = "intField=0; stringField=0; floatField=0.0; longField=0\n"  // 定义预期的查询结果,与testArrowProjectAllFields相同
        + "intField=1; stringField=1; floatField=1.0; longField=1\n"  // 因为查询的是相同的数据
        + "intField=2; stringField=2; floatField=2.0; longField=2\n"  // 只是查询方式不同(显式列出字段 vs SELECT *)
        + "intField=3; stringField=3; floatField=3.0; longField=3\n"  // 结果应该完全一致
        + "intField=4; stringField=4; floatField=4.0; longField=4\n"  // 验证显式列出字段不会影响结果
        + "intField=5; stringField=5; floatField=5.0; longField=5\n";  // 最后一行数据

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .limit(6)  // 限制返回6行
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试显式投影所有字段但顺序改变(字段排列)
   * 该测试验证Arrow适配器能够正确处理字段顺序改变的情况
   * 预期会生成ArrowProject节点来重新排列字段顺序
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectAllFieldsExplicitlyPermutation() {  // 测试方法:验证字段排列功能
    String sql = "select \"stringField\", \"intField\", \"longField\", \"floatField\" "  // 定义SQL查询,字段顺序与原始顺序不同
        + "from arrowdata\n";  // 原始顺序:intField(0), stringField(1), floatField(2), longField(3)
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(stringField=[$1], intField=[$0], longField=[$3], floatField=[$2])\n"  // ArrowProject节点重新排列字段顺序,$n表示字段索引
        + "    ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan仍然按原始顺序扫描所有字段
    String result = "stringField=0; intField=0; longField=0; floatField=0.0\n"  // 定义预期的查询结果,字段顺序与SQL查询一致
        + "stringField=1; intField=1; longField=1; floatField=1.0\n"  // 注意字段顺序变为:stringField, intField, longField, floatField
        + "stringField=2; intField=2; longField=2; floatField=2.0\n"  // 这与testArrowProjectAllFields的结果顺序不同
        + "stringField=3; intField=3; longField=3; floatField=3.0\n"  // 验证ArrowProject正确地重新排列了字段
        + "stringField=4; intField=4; longField=4; floatField=4.0\n"  // $1表示stringField,$0表示intField,$3表示longField,$2表示floatField
        + "stringField=5; intField=5; longField=5; floatField=5.0\n";  // 最后一行数据

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .limit(6)  // 限制返回6行
        .returns(result)  // 验证结果顺序是否正确
        .explainContains(plan);  // 验证执行计划中包含ArrowProject节点
  }

  /**
   * 测试投影单个字段
   * 该测试验证Arrow适配器能够正确处理只选择一个字段的查询
   * 预期会生成ArrowProject节点来投影单个字段
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectSingleField() {  // 测试方法:验证投影单个字段的功能
    String sql = "select \"intField\" from arrowdata\n";  // 定义SQL查询,只选择intField字段
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(intField=[$0])\n"  // ArrowProject节点只投影索引0的字段(intField)
        + "    ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan仍然扫描所有字段(优化器可能未优化)
    String result = "intField=0\nintField=1\nintField=2\n"  // 定义预期的查询结果,只包含intField值
        + "intField=3\nintField=4\nintField=5\n";  // 每行只有一个字段值

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .limit(6)  // 限制返回6行
        .returns(result)  // 验证结果只包含intField
        .explainContains(plan);  // 验证执行计划中包含ArrowProject(intField=[$0])
  }

  /**
   * 测试投影两个字段
   * 该测试验证Arrow适配器能够正确处理选择两个字段的查询
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectTwoFields() {  // 测试方法:验证投影两个字段的功能
    String sql = "select \"intField\", \"stringField\" from arrowdata\n";  // 定义SQL查询,选择intField和stringField两个字段
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(intField=[$0], stringField=[$1])\n"  // ArrowProject节点投影索引0和1的字段
        + "    ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描所有字段
    String result = "intField=0; stringField=0\n"  // 定义预期的查询结果,包含两个字段的值
        + "intField=1; stringField=1\n"  // 每行包含intField和stringField两个字段
        + "intField=2; stringField=2\n"  // 字段顺序与SELECT子句一致
        + "intField=3; stringField=3\n"  // 验证ArrowProject正确投影了两个字段
        + "intField=4; stringField=4\n"  // $0表示intField,$1表示stringField
        + "intField=5; stringField=5\n";  // 最后一行数据

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .limit(6)  // 限制返回6行
        .returns(result)  // 验证结果包含两个字段
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试带整数过滤条件的字段投影
   * 该测试验证Arrow适配器能够正确处理WHERE子句中的整数比较条件
   * 预期会生成ArrowFilter节点来应用过滤条件
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectFieldsWithIntegerFilter() {  // 测试方法:验证带整数过滤的字段投影
    String sql = "select \"intField\", \"stringField\"\n"  // 定义SQL查询,选择两个字段
        + "from arrowdata\n"  // 从arrowdata表
        + "where \"intField\" < 4";  // WHERE条件:intField小于4
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(intField=[$0], stringField=[$1])\n"  // ArrowProject节点投影两个字段
        + "    ArrowFilter(condition=[<($0, 4)])\n"  // ArrowFilter节点应用条件:字段索引0的值小于4
        + "      ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描所有字段
    String result = "intField=0; stringField=0\n"  // 定义预期的查询结果,只包含intField<4的行
        + "intField=1; stringField=1\n"  // 共4行数据:0,1,2,3
        + "intField=2; stringField=2\n"  // intField=4的行被过滤掉
        + "intField=3; stringField=3\n";  // 最后一行符合条件的记录

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returns(result)  // 验证结果只包含intField<4的记录
        .explainContains(plan);  // 验证执行计划中包含ArrowFilter(condition=[<($0, 4)])
  }

  /**
   * 测试带多个过滤条件(同一字段)的字段投影
   * 该测试验证Arrow适配器能够正确处理同一字段的多个过滤条件
   * 预期会生成使用Sarg(Search Argument)优化的ArrowFilter节点
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectFieldsWithMultipleFilterSameField() {  // 测试方法:验证同一字段的多个过滤条件
    String sql = "select \"intField\", \"stringField\"\n"  // 定义SQL查询,选择两个字段
        + "from arrowdata\n"  // 从arrowdata表
        + "where \"intField\" > 1 and \"intField\" < 4";  // WHERE条件:intField大于1且小于4(即1 < intField < 4)
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(intField=[$0], stringField=[$1])\n"  // ArrowProject节点投影两个字段
        + "    ArrowFilter(condition=[SEARCH($0, Sarg[(1..4)])])\n"  // ArrowFilter使用Sarg(搜索参数)优化:搜索范围(1..4),注意是开区间,不包括1和4
        + "      ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描所有字段
    String result = "intField=2; stringField=2\n"  // 定义预期的查询结果,只包含1 < intField < 4的行
        + "intField=3; stringField=3\n";  // 只有intField=2和intField=3符合条件

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returns(result)  // 验证结果只包含intField=2和3的记录
        .explainContains(plan);  // 验证执行计划中包含SEARCH($0, Sarg[(1..4)])
  }

  /**
   * 测试带合取过滤条件(AND)的字段投影
   * 该测试验证Arrow适配器能够正确处理AND连接的多个过滤条件
   * 预期会生成包含AND条件的ArrowFilter节点
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectFieldsWithConjunctiveFilters() {  // 测试方法:验证AND连接的过滤条件
    String sql = "select \"intField\", \"stringField\"\n"  // 定义SQL查询,选择两个字段
        + "from arrowdata\n"  // 从arrowdata表
        + "where \"intField\"=12 and \"stringField\"='12'";  // WHERE条件:intField等于12且stringField等于'12'
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(intField=[$0], stringField=[$1])\n"  // ArrowProject节点投影两个字段
        + "    ArrowFilter(condition=[AND(=($0, 12), =($1, '12'))])\n"  // ArrowFilter节点使用AND连接两个等值条件:字段0等于12 AND 字段1等于'12'
        + "      ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描所有字段
    String result = "intField=12; stringField=12\n";  // 定义预期的查询结果,只有一行同时满足两个条件

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returns(result)  // 验证结果只包含intField=12且stringField='12'的记录
        .explainContains(plan);  // 验证执行计划中包含AND条件
  }

  /**
   * 测试带析取过滤条件(OR)的字段投影
   * 该测试验证Arrow适配器能够正确处理OR连接的过滤条件
   * 根据Bug.CALCITE_6293_FIXED的状态,预期执行计划可能不同
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectFieldsWithDisjunctiveFilter() {  // 测试方法:验证OR连接的过滤条件
    String sql = "select \"intField\", \"stringField\"\n"  // 定义SQL查询,选择两个字段
        + "from arrowdata\n"  // 从arrowdata表
        + "where \"intField\"=12 or \"stringField\"='12'";  // WHERE条件:intField等于12 OR stringField等于'12'
    String plan;  // 声明plan变量,根据Bug修复状态决定预期计划
    if (Bug.CALCITE_6293_FIXED) {  // 检查Bug.CALCITE_6293是否已修复(该Bug关于OR条件的支持)
      plan = "PLAN=ArrowToEnumerableConverter\n"  // 如果Bug已修复,预期使用ArrowFilter处理OR条件
          + "  ArrowProject(intField=[$0], stringField=[$1])\n"  // ArrowProject节点投影两个字段
          + "    ArrowFilter(condition=[OR(=($0, 12), =($1, '12'))])\n"  // ArrowFilter使用OR连接两个条件
          + "      ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描所有字段
    } else {  // 如果Bug未修复
      plan = "PLAN=EnumerableCalc(expr#0..1=[{inputs}], expr#2=[12], "  // 预期使用EnumerableCalc(可枚举计算)节点
          + "expr#3=[=($t0, $t2)], expr#4=['12':VARCHAR], expr#5=[=($t1, $t4)], "  // 表达式#3:输入字段0等于12,表达式#5:输入字段1等于'12'
          + "expr#6=[OR($t3, $t5)], proj#0..1=[{exprs}], $condition=[$t6])\n"  // 表达式#6:表达式#3 OR 表达式#5,condition为表达式#6
          + "  ArrowToEnumerableConverter\n"  // ArrowToEnumerableConverter转换器
          + "    ArrowProject(intField=[$0], stringField=[$1])\n"  // ArrowProject投影两个字段
          + "      ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描所有字段
    }
    String result = "intField=12; stringField=12\n";  // 定义预期的查询结果,intField=12的记录同时也满足stringField='12'

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returns(result)  // 验证结果
        .explainContains(plan);  // 根据Bug状态验证相应的执行计划
  }

  /**
   * 测试带IN过滤条件的字段投影
   * 该测试验证Arrow适配器能够正确处理IN操作符
   * 根据Bug.CALCITE_6294_FIXED的状态,预期执行计划可能不同
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectFieldsWithInFilter() {  // 测试方法:验证IN操作符
    String sql = "select \"intField\", \"stringField\"\n"  // 定义SQL查询,选择两个字段
        + "from arrowdata\n"  // 从arrowdata表
        + "where \"intField\" in (0, 1, 2)";  // WHERE条件:intField在值列表(0, 1, 2)中
    String plan;  // 声明plan变量,根据Bug修复状态决定预期计划
    if (Bug.CALCITE_6294_FIXED) {  // 检查Bug.CALCITE_6294是否已修复(该Bug关于IN操作符的支持)
      plan = "PLAN=ArrowToEnumerableConverter\n"  // 如果Bug已修复,预期使用ArrowFilter处理IN条件
          + "  ArrowProject(intField=[$0], stringField=[$1])\n"  // ArrowProject节点投影两个字段
          + "    ArrowFilter(condition=[OR(=($0, 0), =($0, 1), =($0, 2))])\n"  // ArrowFilter使用OR连接多个等值条件(等价于IN)
          + "      ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描所有字段
    } else {  // 如果Bug未修复
      plan = "PLAN=EnumerableCalc(expr#0..1=[{inputs}], expr#2=[Sarg[0, 1, 2]], "  // 预期使用EnumerableCalc节点
          + "expr#3=[SEARCH($t0, $t2)], proj#0..1=[{exprs}], $condition=[$t3])\n"  // 使用Sarg搜索参数:搜索字段0是否在集合[0,1,2]中
          + "  ArrowToEnumerableConverter\n"  // ArrowToEnumerableConverter转换器
          + "    ArrowProject(intField=[$0], stringField=[$1])\n"  // ArrowProject投影两个字段
          + "      ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描所有字段
    }
    String result = "intField=0; stringField=0\n"  // 定义预期的查询结果,只包含intField在(0,1,2)中的行
        + "intField=1; stringField=1\n"  // 共3行数据
        + "intField=2; stringField=2\n";  // 最后一行符合条件的记录

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returns(result)  // 验证结果只包含intField为0,1,2的记录
        .explainContains(plan);  // 根据Bug状态验证相应的执行计划
  }

  /**
   * 测试带IS NOT NULL过滤条件的字段投影
   * 测试用例关联到JIRA issue CALCITE-6295:支持Arrow适配器中的IS NOT NULL操作符
   * 该测试验证Arrow适配器能够正确处理IS NOT NULL条件
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectFieldsWithIsNotNullFilter() {  // 测试方法:验证IS NOT NULL操作符
    String sql = "select \"intField\", \"stringField\"\n"  // 定义SQL查询,选择两个字段
        + "from arrowdata\n"  // 从arrowdata表
        + "where \"intField\" is not null\n";  // WHERE条件:intField不为NULL
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
          + "  ArrowProject(intField=[$0], stringField=[$1])\n"  // ArrowProject节点投影两个字段
          + "    ArrowFilter(condition=[IS NOT NULL($0)])\n"  // ArrowFilter节点应用IS NOT NULL条件:字段索引0不为NULL
          + "      ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描所有字段

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returnsCount(50)  // 验证结果行数为50行(测试数据中所有intField都不为NULL)
        .explainContains(plan);  // 验证执行计划中包含IS NOT NULL($0)
  }

  /**
   * 测试合取的IS NOT NULL过滤条件
   * 测试用例关联到JIRA issue CALCITE-6295:支持Arrow适配器中的IS NOT NULL操作符
   * 该测试验证Arrow适配器能够正确处理多个IS NOT NULL条件的AND组合
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testConjunctiveIsNotNullFilters() {  // 测试方法:验证多个IS NOT NULL条件的AND组合
    String sql = "select * from arrowdata\n"  // 定义SQL查询,选择所有字段
        + "where \"intField\" is not null and \"stringField\" is not null";  // WHERE条件:intField不为NULL AND stringField不为NULL
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowFilter(condition=[AND(IS NOT NULL($0), IS NOT NULL($1))])\n"  // ArrowFilter使用AND连接两个IS NOT NULL条件
        + "    ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描所有字段

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returnsCount(50)  // 验证结果行数为50行(测试数据中所有字段的值都不为NULL)
        .explainContains(plan);  // 验证执行计划中包含AND(IS NOT NULL($0), IS NOT NULL($1))
  }

  /**
   * 测试带等值过滤条件的字段投影
   * 该测试验证Arrow适配器能够正确处理简单的等值条件(=)
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectFieldsWithEqualFilter() {  // 测试方法:验证等值过滤条件
    String sql = "select \"intField\", \"stringField\"\n"  // 定义SQL查询,选择两个字段
        + "from arrowdata\n"  // 从arrowdata表
        + "where \"intField\" = 12";  // WHERE条件:intField等于12
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(intField=[$0], stringField=[$1])\n"  // ArrowProject节点投影两个字段
        + "    ArrowFilter(condition=[=($0, 12)])\n"  // ArrowFilter节点应用等值条件:字段索引0等于12
        + "      ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描所有字段
    String result = "intField=12; stringField=12\n";  // 定义预期的查询结果,只有一行intField=12的记录

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returns(result)  // 验证结果只包含intField=12的记录
        .explainContains(plan);  // 验证执行计划中包含=($0, 12)
  }

  /**
   * 测试带不等值过滤条件(<>)的字段投影
   * 测试用例关联到JIRA issue CALCITE-6618:支持Arrow适配器中的不等于操作符
   * 该测试验证Arrow适配器能够正确处理不等于条件
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectFieldsWithNotEqualFilter() {  // 测试方法:验证不等于过滤条件
    String sql = "select \"intField\", \"stringField\"\n"  // 定义SQL查询,选择两个字段
        + "from arrowdata\n"  // 从arrowdata表
        + "where \"intField\" <> 12";  // WHERE条件:intField不等于12(<>是不等于的标准SQL操作符)
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(intField=[$0], stringField=[$1])\n"  // ArrowProject节点投影两个字段
        + "    ArrowFilter(condition=[<>($0, 12)])\n"  // ArrowFilter节点应用不等值条件:字段索引0不等于12
        + "      ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描所有字段
    String result = "intField=0; stringField=0\n"  // 定义预期的查询结果,包含intField不等于12的记录
        + "intField=1; stringField=1\n"  // 限制返回前6行
        + "intField=2; stringField=2\n"  // intField=12的记录被过滤掉
        + "intField=3; stringField=3\n"  // 验证不等于条件正确工作
        + "intField=4; stringField=4\n"  // 共有50行数据,其中一行intField=12被过滤
        + "intField=5; stringField=5\n";  // 最后一行符合条件的数据

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .limit(6)  // 限制返回前6行
        .returns(result)  // 验证结果不包含intField=12的记录
        .explainContains(plan);  // 验证执行计划中包含<>(不等于)条件
  }

  /**
   * 测试带BETWEEN过滤条件的字段投影
   * 该测试验证Arrow适配器能够正确处理BETWEEN操作符
   * 预期会生成使用Sarg优化的ArrowFilter节点
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectFieldsWithBetweenFilter() {  // 测试方法:验证BETWEEN操作符
    String sql = "select \"intField\", \"stringField\"\n"  // 定义SQL查询,选择两个字段
        + "from arrowdata\n"  // 从arrowdata表
        + "where \"intField\" between 1 and 3";  // WHERE条件:intField在1和3之间(包含边界值)
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(intField=[$0], stringField=[$1])\n"  // ArrowProject节点投影两个字段
        + "    ArrowFilter(condition=[SEARCH($0, Sarg[[1..3]])])\n"  // ArrowFilter使用Sarg优化:搜索范围[1..3](闭区间,包含1和3)
        + "      ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描所有字段
    String result = "intField=1; stringField=1\n"  // 定义预期的查询结果,包含intField在1到3之间的行
        + "intField=2; stringField=2\n"  // 共3行数据:1,2,3
        + "intField=3; stringField=3\n";  // BETWEEN是包含边界值的,所以1和3都包含在内

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returns(result)  // 验证结果包含intField=1,2,3的记录
        .explainContains(plan);  // 验证执行计划中包含SEARCH($0, Sarg[[1..3]])
  }

  /**
   * 测试带IS NULL过滤条件的字段投影
   * 测试用例关联到JIRA issue CALCITE-6296:支持Arrow适配器中的IS NULL操作符
   * 该测试验证Arrow适配器能够正确处理IS NULL条件
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectFieldsWithIsNullFilter() {  // 测试方法:验证IS NULL操作符
    String sql = "select \"intField\", \"stringField\"\n"  // 定义SQL查询,选择两个字段
        + "from arrowdata\n"  // 从arrowdata表
        + "where \"intField\" is null";  // WHERE条件:intField为NULL
    String plan = "ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划(注意缺少PLAN=前缀)
          + "  ArrowProject(intField=[$0], stringField=[$1])\n"  // ArrowProject节点投影两个字段
          + "    ArrowFilter(condition=[IS NULL($0)])\n"  // ArrowFilter节点应用IS NULL条件:字段索引0为NULL
          + "      ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描所有字段

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returnsCount(0)  // 验证结果行数为0(测试数据中intField没有NULL值)
        .explainContains(plan);  // 验证执行计划中包含IS NULL($0)
  }

  /**
   * 测试合取的IS NULL过滤条件
   * 测试用例关联到JIRA issue CALCITE-6296:支持Arrow适配器中的IS NULL操作符
   * 该测试验证Arrow适配器能够正确处理多个IS NULL条件的AND组合
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testConjunctiveIsNullFilters() {  // 测试方法:验证多个IS NULL条件的AND组合
    String sql = "select * from arrowdata\n"  // 定义SQL查询,选择所有字段
        + "where \"intField\" is null and \"stringField\" is null";  // WHERE条件:intField为NULL AND stringField为NULL
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowFilter(condition=[AND(IS NULL($0), IS NULL($1))])\n"  // ArrowFilter使用AND连接两个IS NULL条件
        + "    ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描所有字段

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returnsCount(0)  // 验证结果行数为0(测试数据中两个字段都没有NULL值)
        .explainContains(plan);  // 验证执行计划中包含AND(IS NULL($0), IS NULL($1))
  }

  /**
   * 测试带浮点数过滤条件的字段投影
   * 该测试验证Arrow适配器能够正确处理浮点数类型的过滤条件
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectFieldsWithFloatFilter() {  // 测试方法:验证浮点数过滤条件
    String sql = "select * from arrowdata\n"  // 定义SQL查询,选择所有字段
        + " where \"floatField\"=15.0";  // WHERE条件:floatField等于15.0
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowFilter(condition=[=($2, 15.0E0)])\n"  // ArrowFilter节点应用等值条件:字段索引2(floatField)等于15.0E0(E0表示科学计数法)
        + "    ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描所有字段
    String result = "intField=15; stringField=15; floatField=15.0; longField=15\n";  // 定义预期的查询结果,只有一行floatField=15.0的记录

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returns(result)  // 验证结果只包含floatField=15.0的记录
        .explainContains(plan);  // 验证执行计划中包含浮点数比较条件
  }

  /**
   * 测试在后续批次(batch)上的过滤条件
   * 该测试验证Arrow适配器能够正确处理跨多个RecordBatch的数据过滤
   * Arrow数据可能被分成多个批次,该测试确保过滤条件在所有批次上都正确应用
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectFieldsWithFilterOnLaterBatch() {  // 测试方法:验证在后续批次上的过滤
    String sql = "select \"intField\"\n"  // 定义SQL查询,只选择intField字段
        + "from arrowdata\n"  // 从arrowdata表
        + "where \"intField\"=25";  // WHERE条件:intField等于25(假设25在后续批次中)
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(intField=[$0])\n"  // ArrowProject节点投影intField字段
        + "    ArrowFilter(condition=[=($0, 25)])\n"  // ArrowFilter节点应用等值条件:字段索引0等于25
        + "      ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描所有字段
    String result = "intField=25\n";  // 定义预期的查询结果,只有一行intField=25的记录

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returns(result)  // 验证结果只包含intField=25的记录(即使它在后续批次中)
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试子查询
   * 该测试验证Arrow适配器能够正确处理嵌套的子查询
   * 预期优化器会将子查询优化为单个查询计划
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowSubquery() {  // 测试方法:验证子查询功能
    String sql = "select \"intField\"\n"  // 定义SQL查询,外层查询选择intField字段
        + "from (select \"intField\", \"stringField\" from arrowdata where \"stringField\" = '2')\n"  // FROM子查询:选择intField和stringField,过滤stringField='2'
        + "where \"intField\" = 2";  // 外层WHERE条件:intField等于2
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(intField=[$0])\n"  // ArrowProject节点投影intField字段
        + "    ArrowFilter(condition=[AND(=($1, '2'), =($0, 2))])\n"  // ArrowFilter使用AND连接两个条件(优化器合并了子查询和外层查询的条件)
        + "      ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n"  // ArrowTableScan扫描所有字段
        + "\n";  // 空行
    String result = "intField=2\n";  // 定义预期的查询结果,只有一行同时满足两个条件的记录

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划(优化器应该将子查询合并)
  }

  /**
   * 测试UNION操作(已禁用)
   * 该测试标记为@Disabled,因为UNION操作当前不被支持
   * 该测试用例展示了UNION操作的预期行为,但实际执行时会跳过
   */
  @Disabled("UNION does not work")  // JUnit5注解,禁用该测试,说明UNION操作当前不工作
  @Test  // JUnit5注解,标记这是一个测试方法(但被禁用)
  void testArrowUnion() {  // 测试方法:验证UNION操作(当前不支持)
    String sql = "(select \"intField\"\n"  // 定义SQL查询,第一个SELECT语句
        + "from arrowdata\n"  // 从arrowdata表
        + "where \"intField\" = 2)\n"  // WHERE条件:intField等于2
        + "  union \n"  // UNION操作符,合并两个查询结果(去重)
        + "(select \"intField\"\n"  // 第二个SELECT语句
        + "from arrowdata\n"  // 从arrowdata表
        + "where \"intField\" = 1)\n";  // WHERE条件:intField等于1
    String plan = "PLAN=EnumerableUnion(all=[false])\n"  // 定义预期的查询执行计划,EnumerableUnion节点,all=false表示去重
        + "  ArrowToEnumerableConverter\n"  // 第一个分支的ArrowToEnumerableConverter
        + "    ArrowProject(intField=[$0])\n"  // ArrowProject投影intField字段
        + "      ArrowFilter(condition=[=($0, 2)])\n"  // ArrowFilter过滤intField=2的记录
        + "        ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n"  // ArrowTableScan扫描所有字段
        + "  ArrowToEnumerableConverter\n"  // 第二个分支的ArrowToEnumerableConverter
        + "    ArrowProject(intField=[$0])\n"  // ArrowProject投影intField字段
        + "      ArrowFilter(condition=[=($0, 1)])\n"  // ArrowFilter过滤intField=1的记录
        + "        ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描所有字段
    String result = "intField=1\nintField=2\n";  // 定义预期的查询结果,包含两个分支的合并结果

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试带空格的字段名
   * 该测试验证Arrow适配器能够正确处理包含空格的字段名
   * 字段名需要用双引号括起来
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testFieldWithSpace() {  // 测试方法:验证带空格的字段名
    String sql = "select \"my Field\" from (select \"intField\", \"stringField\" as \"my Field\"\n"  // 定义SQL查询,外层查询选择"my Field"字段
        + "from arrowdata)\n"  // FROM子查询,将stringField重命名为"my Field"(带空格)
        + "where \"my Field\" = '2'";  // WHERE条件:"my Field"等于'2'
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(my Field=[$1])\n"  // ArrowProject节点投影"my Field"字段(索引1)
        + "    ArrowFilter(condition=[=($1, '2')])\n"  // ArrowFilter应用等值条件:字段索引1等于'2'
        + "      ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描所有字段
    String result = "my Field=2\n";  // 定义预期的查询结果,只有一行"my Field"='2'的记录

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试带空格的字面量(已禁用)
   * 该测试标记为@Disabled,因为带空格的字面量当前不被支持
   */
  @Disabled("literal with space is not supported")  // JUnit5注解,禁用该测试,说明带空格的字面量不支持
  @Test  // JUnit5注解,标记这是一个测试方法(但被禁用)
  void testLiteralWithSpace() {  // 测试方法:验证带空格的字面量(当前不支持)
    String sql = "select \"intField\", \"stringField\" as \"my Field\"\n"  // 定义SQL查询,选择两个字段,将stringField重命名为"my Field"
        + "from arrowdata\n"  // 从arrowdata表
        + "where \"stringField\" = 'literal with space'";  // WHERE条件:stringField等于带空格的字面量'literal with space'
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(intField=[$0], my Field=[$1])\n"  // ArrowProject节点投影两个字段
        + "    ArrowFilter(condition=[=($1, '2')])\n"  // ArrowFilter应用等值条件(注意这里的条件与SQL不匹配,可能是占位符)
        + "      ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描所有字段
    String result = "";  // 定义预期的查询结果,空字符串

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试带引号的字面量
   * 该测试验证Arrow适配器能够正确处理包含引号的字面量
   * 在SQL中,单引号需要通过双写来转义
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testLiteralWithQuote() {  // 测试方法:验证带引号的字面量
    String sql = "select \"intField\", \"stringField\" as \"my Field\"\n"  // 定义SQL查询,选择两个字段,将stringField重命名为"my Field"
        + "from arrowdata\n"  // 从arrowdata表
        + "where \"stringField\" = ''''";  // WHERE条件:stringField等于包含单引号的字面量''''(两个单引号表示一个转义的单引号字符)
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(intField=[$0], stringField=[$1])\n"  // ArrowProject节点投影两个字段
        + "    ArrowFilter(condition=[=($1, '''')])\n"  // ArrowFilter应用等值条件:字段索引1等于''''(转义的单引号)
        + "      ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描所有字段
    String result = "";  // 定义预期的查询结果,空字符串(测试数据中没有值为单引号的记录)

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returns(result)  // 验证结果为空
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试TinyInt类型的投影
   * 该测试验证Arrow适配器能够正确处理TinyInt类型(8位整数)
   * 使用Scott测试数据集的DEPT表
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testTinyIntProject() {  // 测试方法:验证TinyInt类型投影
    String sql = "select DEPTNO from DEPT";  // 定义SQL查询,从DEPT表选择DEPTNO字段(DEPTNO是TinyInt类型)
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(DEPTNO=[$0])\n"  // ArrowProject节点投影DEPTNO字段(索引0)
        + "    ArrowTableScan(table=[[ARROW, DEPT]], fields=[[0, 1, 2]])\n\n";  // ArrowTableScan扫描DEPT表的3个字段
    String result = "DEPTNO=10\nDEPTNO=20\nDEPTNO=30\nDEPTNO=40\n";  // 定义预期的查询结果,DEPTNO的4个值(10,20,30,40)

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试SmallInt类型的投影
   * 该测试验证Arrow适配器能够正确处理SmallInt类型(16位整数)
   * 使用Scott测试数据集的EMP表
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testSmallIntProject() {  // 测试方法:验证SmallInt类型投影
    String sql = "select EMPNO from EMP";  // 定义SQL查询,从EMP表选择EMPNO字段(EMPNO是SmallInt类型)
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(EMPNO=[$0])\n"  // ArrowProject节点投影EMPNO字段(索引0)
        + "    ArrowTableScan(table=[[ARROW, EMP]], fields=[[0, 1, 2, 3, 4, 5, 6, 7]])\n\n";  // ArrowTableScan扫描EMP表的8个字段
    String result = "EMPNO=7369\nEMPNO=7499\nEMPNO=7521\n";  // 定义预期的查询结果,EMPNO的前3个值
    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .limit(3)  // 限制返回前3行
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试Decimal类型转换为Int类型
   * 该测试验证Arrow适配器能够正确处理Decimal到Int的类型转换
   * 使用CAST函数将Decimal字段转换为Integer
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testCastDecimalToInt() {  // 测试方法:验证Decimal到Int的转换
    String sql = "select CAST(LOSAL AS INT) as \"trunc\" from SALGRADE";  // 定义SQL查询,将LOSAL字段从Decimal转换为Int,别名"trunc"
    String plan =  // 定义预期的查询执行计划
        "PLAN=EnumerableCalc(expr#0..2=[{inputs}], expr#3=[CAST($t1):INTEGER], trunc=[$t3])\n"  // EnumerableCalc执行类型转换,表达式#3是将输入字段#1(LOSAL)转换为INTEGER
            + "  ArrowToEnumerableConverter\n"  // ArrowToEnumerableConverter转换器
            + "    ArrowTableScan(table=[[ARROW, SALGRADE]], fields=[[0, 1, 2]])\n\n";  // ArrowTableScan扫描SALGRADE表的3个字段
    String result = "trunc=700\n";  // 定义预期的查询结果,LOSAL=700转换为Integer

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .typeIs("[trunc INTEGER]")  // 验证结果类型为INTEGER数组
        .limit(1)  // 限制返回前1行
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试Decimal类型转换为Float类型
   * 该测试验证Arrow适配器能够正确处理Decimal到Float的类型转换
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testCastDecimalToFloat() {  // 测试方法:验证Decimal到Float的转换
    String sql = "select CAST(LOSAL AS FLOAT) as \"extra\" from SALGRADE";  // 定义SQL查询,将LOSAL字段从Decimal转换为Float,别名"extra"
    String plan = "PLAN=EnumerableCalc(expr#0..2=[{inputs}],"  // 定义预期的查询执行计划
        + " expr#3=[CAST($t1):FLOAT], extra=[$t3])\n"  // EnumerableCalc执行类型转换,表达式#3是将输入字段#1(LOSAL)转换为FLOAT
        + "  ArrowToEnumerableConverter\n"  // ArrowToEnumerableConverter转换器
        + "    ArrowTableScan(table=[[ARROW, SALGRADE]], fields=[[0, 1, 2]])\n\n";  // ArrowTableScan扫描SALGRADE表的3个字段
    String result = "extra=700.0\nextra=1201.0\n";  // 定义预期的查询结果,LOSAL的前2个值转换为Float

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .typeIs("[extra FLOAT]")  // 验证结果类型为FLOAT数组
        .limit(2)  // 限制返回前2行
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试Decimal类型转换为Double类型
   * 该测试验证Arrow适配器能够正确处理Decimal到Double的类型转换
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testCastDecimalToDouble() {  // 测试方法:验证Decimal到Double的转换
    String sql = "select CAST(LOSAL AS DOUBLE) as \"extra\" from SALGRADE";  // 定义SQL查询,将LOSAL字段从Decimal转换为Double,别名"extra"
    String plan =  // 定义预期的查询执行计划
        "PLAN=EnumerableCalc(expr#0..2=[{inputs}], expr#3=[CAST($t1):DOUBLE], extra=[$t3])\n"  // EnumerableCalc执行类型转换,表达式#3是将输入字段#1(LOSAL)转换为DOUBLE
            + "  ArrowToEnumerableConverter\n"  // ArrowToEnumerableConverter转换器
            + "    ArrowTableScan(table=[[ARROW, SALGRADE]], fields=[[0, 1, 2]])\n\n";  // ArrowTableScan扫描SALGRADE表的3个字段
    String result = "extra=700.0\nextra=1201.0\n";  // 定义预期的查询结果,LOSAL的前2个值转换为Double

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .typeIs("[extra DOUBLE]")  // 验证结果类型为DOUBLE数组
        .limit(2)  // 限制返回前2行
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试Int类型转换为Double类型
   * 该测试验证Arrow适配器能够正确处理Int到Double的类型转换
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testCastIntToDouble() {  // 测试方法:验证Int到Double的转换
    String sql = "select CAST(\"intField\" AS DOUBLE) as \"dbl\" from arrowdata";  // 定义SQL查询,将intField字段从Int转换为Double,别名"dbl"
    String plan =  // 定义预期的查询执行计划
        "PLAN=EnumerableCalc(expr#0..3=[{inputs}], expr#4=[CAST($t0):DOUBLE], dbl=[$t4])\n"  // EnumerableCalc执行类型转换,表达式#4是将输入字段#0(intField)转换为DOUBLE
            + "  ArrowToEnumerableConverter\n"  // ArrowToEnumerableConverter转换器
            + "    ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描ARROWDATA表的4个字段
    String result = "dbl=0.0\ndbl=1.0\n";  // 定义预期的查询结果,intField的前2个值转换为Double

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .typeIs("[dbl DOUBLE]")  // 验证结果类型为DOUBLE数组
        .limit(2)  // 限制返回前2行
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试字符串操作
   * 该测试验证Arrow适配器能够正确处理字符串连接操作(||操作符)
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testStringOperation() {  // 测试方法:验证字符串操作
    String sql = "select\n"  // 定义SQL查询
        + "  \"stringField\" || '_suffix' as \"field1\"\n"  // 选择stringField与字符串'_suffix'连接的结果,别名为"field1"
        + "from arrowdata";  // 从arrowdata表
    String plan = "PLAN=EnumerableCalc(expr#0..3=[{inputs}], expr#4=['_suffix'], "  // 定义预期的查询执行计划
            + "expr#5=[||($t1, $t4)], field1=[$t5])\n"  // EnumerableCalc执行字符串连接,表达式#5是输入字段#1(stringField)与表达式#4('_suffix')的连接
            + "  ArrowToEnumerableConverter\n"  // ArrowToEnumerableConverter转换器
            + "    ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描ARROWDATA表的4个字段
    String result = "field1=0_suffix\n";  // 定义预期的查询结果,stringField='0'与'_suffix'连接

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .limit(1)  // 限制返回前1行
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }


  /**
   * 测试JOIN操作(已禁用)
   * 该测试标记为@Disabled,因为JOIN操作当前不被支持
   */
  @Disabled("join is not supported yet")  // JUnit5注解,禁用该测试,说明JOIN操作当前不支持
  @Test  // JUnit5注解,标记这是一个测试方法(但被禁用)
  void testJoin() {  // 测试方法:验证JOIN操作(当前不支持)
    String sql = "select t1.\"intField\", t2.\"intField\" "  // 定义SQL查询,选择两个表的intField字段
        + "from arrowdata t1 join arrowdata t2 on t1.\"intField\" = t2.\"intField\"";  // FROM arrowdata表t1 JOIN arrowdata表t2 ON t1.intField = t2.intField(自连接)
    String plan = "PLAN=EnumerableJoin(condition=[=($0, $4)], joinType=[inner])\n"  // 定义预期的查询执行计划,EnumerableJoin节点,条件是字段$0等于字段$4,连接类型为inner
        + "  ArrowToEnumerableConverter\n"  // 第一个输入分支的ArrowToEnumerableConverter
        + "    ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n"  // ArrowTableScan扫描第一个ARROWDATA表
        + "  ArrowToEnumerableConverter\n"  // 第二个输入分支的ArrowToEnumerableConverter
        + "    ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描第二个ARROWDATA表
    String result = "intField=0\nintField=1\nintField=2\nintField=3\nintField=4\nintField=5\n";  // 定义预期的查询结果

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .limit(1)  // 限制返回前1行
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试不带聚合函数的聚合(DISTINCT)
   * 该测试验证Arrow适配器能够正确处理DISTINCT操作
   * DISTINCT实际上是一种特殊的聚合,用于去重
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testAggWithoutAggFunctions() {  // 测试方法:验证DISTINCT操作
    String sql = "select DISTINCT(\"intField\") as \"dep\" from arrowdata";  // 定义SQL查询,选择intField的不重复值,别名"dep"
    String plan = "PLAN=EnumerableAggregate(group=[{0}])\n"  // 定义预期的查询执行计划,EnumerableAggregate节点,group=[{0}]表示按字段0分组(实现DISTINCT)
        + "  ArrowToEnumerableConverter\n"  // ArrowToEnumerableConverter转换器
        + "    ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描ARROWDATA表的4个字段
    String result = "dep=0\ndep=1\n";  // 定义预期的查询结果,intField的前2个不重复值

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .limit(2)  // 限制返回前2行
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试带聚合函数的聚合
   * 该测试验证Arrow适配器能够正确处理GROUP BY和聚合函数(SUM)
   * 使用Scott测试数据集的EMP表
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testAggWithAggFunctions() {  // 测试方法:验证GROUP BY和聚合函数
    String sql = "select JOB, SUM(SAL) as TOTAL from EMP GROUP BY JOB";  // 定义SQL查询,按JOB分组,计算SAL的总和,别名"TOTAL"
    String plan = "PLAN=EnumerableAggregate(group=[{2}], TOTAL=[SUM($5)])\n"  // 定义预期的查询执行计划,EnumerableAggregate节点,group=[{2}]表示按字段2(JOB)分组,TOTAL=[SUM($5)]表示对字段5(SAL)求和
        + "  ArrowToEnumerableConverter\n"  // ArrowToEnumerableConverter转换器
        + "    ArrowTableScan(table=[[ARROW, EMP]], fields=[[0, 1, 2, 3, 4, 5, 6, 7]])\n\n";  // ArrowTableScan扫描EMP表的8个字段
    String result = "JOB=SALESMAN; TOTAL=5600.00\nJOB=ANALYST; TOTAL=6000.00\n";  // 定义预期的查询结果,前2个JOB及其SAL总和

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .limit(2)  // 限制返回前2行
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试带过滤条件的聚合(FILTER子句)
   * 该测试验证Arrow适配器能够正确处理聚合函数中的FILTER子句
   * FILTER子句允许在聚合前对数据进行过滤
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testFilteredAgg() {  // 测试方法:验证带FILTER子句的聚合
    String sql = "select SUM(SAL) FILTER (WHERE COMM > 400) as SALESSUM from EMP";  // 定义SQL查询,计算COMM>400的记录的SAL总和,别名"SALESSUM"
    String plan = "PLAN=EnumerableAggregate(group=[{}], SALESSUM=[SUM($0) FILTER $1])\n"  // 定义预期的查询执行计划,EnumerableAggregate节点,group=[{}]表示无分组,SALESSUM=[SUM($0) FILTER $1]表示对字段0(SAL)求和,使用字段1作为过滤条件
        + "  EnumerableCalc(expr#0..7=[{inputs}], expr#8=[CAST($t6):DECIMAL(12, 2)], expr#9=[400.00:DECIMAL(12, 2)], "  // EnumerableCalc节点,表达式#8是将字段6(COMM)转换为DECIMAL,表达式#9是常量400.00
        + "expr#10=[>($t8, $t9)], expr#11=[IS TRUE($t10)], SAL=[$t5], $f1=[$t11])\n"  // 表达式#10是比较表达式#8>表达式#9,表达式#11是将表达式#10转换为布尔值,SAL是字段5,$f1是表达式#11(过滤条件)
        + "    ArrowToEnumerableConverter\n"  // ArrowToEnumerableConverter转换器
        + "      ArrowTableScan(table=[[ARROW, EMP]], fields=[[0, 1, 2, 3, 4, 5, 6, 7]])\n\n";  // ArrowTableScan扫描EMP表的8个字段
    String result = "SALESSUM=2500.00\n";  // 定义预期的查询结果,COMM>400的记录的SAL总和

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .limit(2)  // 限制返回前2行
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试带过滤条件的聚合和分组
   * 该测试验证Arrow适配器能够正确处理GROUP BY、聚合函数和FILTER子句的组合
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testFilteredAggGroupBy() {  // 测试方法:验证GROUP BY、聚合函数和FILTER子句的组合
    String sql = "select SUM(SAL) FILTER (WHERE COMM > 400) as SALESSUM from EMP group by EMPNO";  // 定义SQL查询,按EMPNO分组,计算每组中COMM>400的记录的SAL总和
    String plan = "PLAN=EnumerableCalc(expr#0..1=[{inputs}], SALESSUM=[$t1])\n"  // 定义预期的查询执行计划,EnumerableCalc节点,从输入中选择字段1(SALESSUM)
        + "  EnumerableAggregate(group=[{0}], SALESSUM=[SUM($1) FILTER $2])\n"  // EnumerableAggregate节点,group=[{0}]表示按字段0(EMPNO)分组,SALESSUM=[SUM($1) FILTER $2]表示对字段1(SAL)求和,使用字段2作为过滤条件
        + "    EnumerableCalc(expr#0..7=[{inputs}], expr#8=[CAST($t6):DECIMAL(12, 2)], expr#9=[400.00:DECIMAL(12, 2)], "  // EnumerableCalc节点,表达式#8是将字段6(COMM)转换为DECIMAL,表达式#9是常量400.00
        + "expr#10=[>($t8, $t9)], expr#11=[IS TRUE($t10)], EMPNO=[$t0], SAL=[$t5], $f2=[$t11])\n"  // 表达式#10是比较表达式#8>表达式#9,表达式#11是将表达式#10转换为布尔值,EMPNO是字段0,SAL是字段5,$f2是表达式#11(过滤条件)
        + "      ArrowToEnumerableConverter\n"  // ArrowToEnumerableConverter转换器
        + "        ArrowTableScan(table=[[ARROW, EMP]], fields=[[0, 1, 2, 3, 4, 5, 6, 7]])\n\n";  // ArrowTableScan扫描EMP表的8个字段
    String result = "SALESSUM=1250.00\nSALESSUM=null\n";  // 定义预期的查询结果,前2个EMPNO的SALESSUM值(可能为null)

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .limit(2)  // 限制返回前2行
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试按可空字段分组
   * 该测试验证Arrow适配器能够正确处理按可空字段(可为NULL的字段)进行分组
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testAggGroupedByNullable() {  // 测试方法:验证按可空字段分组
    String sql = "select COMM, SUM(SAL) as SALESSUM from EMP GROUP BY COMM";  // 定义SQL查询,按COMM字段分组,计算SAL的总和,COMM是可空字段
    String plan = "PLAN=EnumerableAggregate(group=[{6}], SALESSUM=[SUM($5)])\n"  // 定义预期的查询执行计划,EnumerableAggregate节点,group=[{6}]表示按字段6(COMM)分组,SALESSUM=[SUM($5)]表示对字段5(SAL)求和
        + "  ArrowToEnumerableConverter\n"  // ArrowToEnumerableConverter转换器
        + "    ArrowTableScan(table=[[ARROW, EMP]], fields=[[0, 1, 2, 3, 4, 5, 6, 7]])\n\n";  // ArrowTableScan扫描EMP表的8个字段

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returnsUnordered("COMM=0.00; SALESSUM=1500.00",  // 验证结果(无序),包含多个COMM值及其对应的SAL总和
            "COMM=1400.00; SALESSUM=1250.00",  // COMM=1400.00的SAL总和为1250.00
            "COMM=300.00; SALESSUM=1600.00",  // COMM=300.00的SAL总和为1600.00
            "COMM=500.00; SALESSUM=1250.00",  // COMM=500.00的SAL总和为1250.00
            "COMM=null; SALESSUM=23425.00")  // COMM=null的SAL总和为23425.00(验证NULL值也能正确分组)
        .explainContains(plan);  // 验证执行计划
  }


  /**
   * 测试不带排序的LIMIT
   * 该测试验证Arrow适配器能够正确处理LIMIT子句
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowAdapterLimitNoSort() {  // 测试方法:验证LIMIT操作
    String sql = "select \"intField\"\n"  // 定义SQL查询,选择intField字段
        + "from arrowdata\n"  // 从arrowdata表
        + "limit 2";  // LIMIT子句:限制返回2行
    String plan = "PLAN=EnumerableCalc(expr#0..3=[{inputs}], intField=[$t0])\n"  // 定义预期的查询执行计划,EnumerableCalc节点,从输入中选择字段0(intField)
        + "  EnumerableLimit(fetch=[2])\n"  // EnumerableLimit节点,fetch=[2]表示获取前2行
        + "    ArrowToEnumerableConverter\n"  // ArrowToEnumerableConverter转换器
        + "      ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描ARROWDATA表的4个字段
    String result = "intField=0\nintField=1\n";  // 定义预期的查询结果,前2行的intField值

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试不带排序的LIMIT和OFFSET
   * 该测试验证Arrow适配器能够正确处理LIMIT和OFFSET子句的组合
   * OFFSET指定跳过的行数,LIMIT指定返回的行数
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowLimitOffsetNoSort() {  // 测试方法:验证LIMIT和OFFSET操作
    String sql = "select \"intField\"\n"  // 定义SQL查询,选择intField字段
        + "from arrowdata\n"  // 从arrowdata表
        + "limit 2 offset 2";  // LIMIT和OFFSET子句:跳过前2行,返回接下来的2行
    String plan = "PLAN=EnumerableCalc(expr#0..3=[{inputs}], intField=[$t0])\n"  // 定义预期的查询执行计划,EnumerableCalc节点,从输入中选择字段0(intField)
        + "  EnumerableLimit(offset=[2], fetch=[2])\n"  // EnumerableLimit节点,offset=[2]表示跳过前2行,fetch=[2]表示获取接下来的2行
        + "    ArrowToEnumerableConverter\n"  // ArrowToEnumerableConverter转换器
        + "      ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描ARROWDATA表的4个字段
    String result = "intField=2\nintField=3\n";  // 定义预期的查询结果,跳过前2行(0,1),返回接下来的2行(2,3)

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试按Long类型字段排序
   * 该测试验证Arrow适配器能够正确处理ORDER BY子句
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowSortOnLong() {  // 测试方法:验证按Long字段排序
    String sql = "select \"intField\" from arrowdata order by \"longField\" desc";  // 定义SQL查询,选择intField字段,按longField降序排序
    String plan = "PLAN=EnumerableSort(sort0=[$1], dir0=[DESC])\n"  // 定义预期的查询执行计划,EnumerableSort节点,sort0=[$1]表示按字段1(longField)排序,dir0=[DESC]表示降序
        + "  ArrowToEnumerableConverter\n"  // ArrowToEnumerableConverter转换器
        + "    ArrowProject(intField=[$0], longField=[$3])\n"  // ArrowProject节点投影intField(字段0)和longField(字段3)
        + "      ArrowTableScan(table=[[ARROW, ARROWDATA]], fields=[[0, 1, 2, 3]])\n\n";  // ArrowTableScan扫描ARROWDATA表的4个字段
    String result = "intField=49\nintField=48\n";  // 定义预期的查询结果,按longField降序排序后的前2行intField值

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .limit(2)  // 限制返回前2行
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试Date类型的投影
   * 测试用例关联到JIRA issue CALCITE-6637:Date类型结果不应在Arrow适配器中自动转换为Timestamp
   * 该测试验证Arrow适配器能够正确处理Date类型,不会自动转换为Timestamp
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testDateProject() {  // 测试方法:验证Date类型投影
    String sql = "select HIREDATE from EMP";  // 定义SQL查询,从EMP表选择HIREDATE字段(HIREDATE是Date类型)
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(HIREDATE=[$4])\n"  // ArrowProject节点投影HIREDATE字段(索引4)
        + "    ArrowTableScan(table=[[ARROW, EMP]], fields=[[0, 1, 2, 3, 4, 5, 6, 7]])\n\n";  // ArrowTableScan扫描EMP表的8个字段
    String result = "HIREDATE=1980-12-17\nHIREDATE=1981-02-20\nHIREDATE=1981-02-22\n";  // 定义预期的查询结果,HIREDATE的前3个值(格式为YYYY-MM-DD,不是Timestamp)

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .limit(3)  // 限制返回前3行
        .returns(result)  // 验证结果格式为Date,不是Timestamp
        .explainContains(plan);  // 验证执行计划
  }


  /**
   * 测试带IS TRUE过滤条件的字段投影
   * 测试用例关联到JIRA issue CALCITE-6638:Arrow适配器应该支持IS TRUE操作符和IS FALSE操作符
   * 该测试验证Arrow适配器能够正确处理IS TRUE条件
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectWithIsTrueFilter() {  // 测试方法:验证IS TRUE操作符
    String sql = "select \"booleanField\"\n"  // 定义SQL查询,选择booleanField字段
        + "from arrowdatatype\n"  // 从arrowdatatype表
        + "where \"booleanField\" is true";  // WHERE条件:booleanField为TRUE
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(booleanField=[$7])\n"  // ArrowProject节点投影booleanField字段(索引7)
        + "    ArrowFilter(condition=[$7])\n"  // ArrowFilter节点应用条件:字段索引7为TRUE(直接使用字段值作为条件,因为布尔字段可以直接作为条件)
        + "      ArrowTableScan(table=[[ARROW, ARROWDATATYPE]], fields=[[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]])\n\n";  // ArrowTableScan扫描ARROWDATATYPE表的12个字段
    String result = "booleanField=true\nbooleanField=true\n";  // 定义预期的查询结果,前2行booleanField=true的记录

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .limit(2)  // 限制返回前2行
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试带IS TRUE过滤条件的字段投影(条件为表达式)
   * 该测试验证Arrow适配器能够正确处理IS TRUE条件应用于表达式的情况
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectWithIsTrueFilter2() {  // 测试方法:验证IS TRUE操作符应用于表达式
    String sql = "select \"intField\"\n"  // 定义SQL查询,选择intField字段
        + "from arrowdatatype\n"  // 从arrowdatatype表
        + "where (\"intField\" > 10) is true";  // WHERE条件:表达式(intField > 10)为TRUE
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(intField=[$2])\n"  // ArrowProject节点投影intField字段(索引2)
        + "    ArrowFilter(condition=[>($2, 10)])\n"  // ArrowFilter节点应用条件:字段索引2大于10(优化器简化了IS TRUE)
        + "      ArrowTableScan(table=[[ARROW, ARROWDATATYPE]], fields=[[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]])\n\n";  // ArrowTableScan扫描ARROWDATATYPE表的12个字段
    String result = "intField=11\nintField=12\n";  // 定义预期的查询结果,前2行intField>10的记录

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .limit(2)  // 限制返回前2行
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试带IS FALSE过滤条件的字段投影
   * 该测试验证Arrow适配器能够正确处理IS FALSE条件
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectFieldsWithIsFalseFilter() {  // 测试方法:验证IS FALSE操作符
    String sql = "select \"booleanField\"\n"  // 定义SQL查询,选择booleanField字段
        + "from arrowdatatype\n"  // 从arrowdatatype表
        + "where \"booleanField\" is false";  // WHERE条件:booleanField为FALSE
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(booleanField=[$7])\n"  // ArrowProject节点投影booleanField字段(索引7)
        + "    ArrowFilter(condition=[NOT($7)])\n"  // ArrowFilter节点应用条件:字段索引7的NOT(即FALSE)
        + "      ArrowTableScan(table=[[ARROW, ARROWDATATYPE]], fields=[[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]])\n\n";  // ArrowTableScan扫描ARROWDATATYPE表的12个字段
    String result = "booleanField=false\nbooleanField=false\n";  // 定义预期的查询结果,前2行booleanField=false的记录

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .limit(2)  // 限制返回前2行
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }


  /**
   * 测试带IS NOT TRUE过滤条件的字段投影
   * 该测试验证Arrow适配器能够正确处理IS NOT TRUE条件
   * IS NOT TRUE等价于"不是TRUE",包括FALSE和NULL
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectFieldsWithIsNotTrueFilter() {  // 测试方法:验证IS NOT TRUE操作符
    String sql = "select \"booleanField\"\n"  // 定义SQL查询,选择booleanField字段
        + "from arrowdatatype\n"  // 从arrowdatatype表
        + "where \"booleanField\" is not true";  // WHERE条件:booleanField不为TRUE(即FALSE或NULL)
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(booleanField=[$7])\n"  // ArrowProject节点投影booleanField字段(索引7)
        + "    ArrowFilter(condition=[IS NOT TRUE($7)])\n"  // ArrowFilter节点应用条件:字段索引7不为TRUE
        + "      ArrowTableScan(table=[[ARROW, ARROWDATATYPE]], fields=[[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]])\n\n";  // ArrowTableScan扫描ARROWDATATYPE表的12个字段
    String result = "booleanField=null\nbooleanField=false\n";  // 定义预期的查询结果,前2行booleanField不为TRUE的记录(NULL和FALSE)

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .limit(2)  // 限制返回前2行
        .returns(result)  // 验证结果包含NULL和FALSE
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试带IS NOT FALSE过滤条件的字段投影
   * 该测试验证Arrow适配器能够正确处理IS NOT FALSE条件
   * IS NOT FALSE等价于"不是FALSE",包括TRUE和NULL
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectFieldsWithIsNotFalseFilter() {  // 测试方法:验证IS NOT FALSE操作符
    String sql = "select \"booleanField\"\n"  // 定义SQL查询,选择booleanField字段
        + "from arrowdatatype\n"  // 从arrowdatatype表
        + "where \"booleanField\" is not false";  // WHERE条件:booleanField不为FALSE(即TRUE或NULL)
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(booleanField=[$7])\n"  // ArrowProject节点投影booleanField字段(索引7)
        + "    ArrowFilter(condition=[IS NOT FALSE($7)])\n"  // ArrowFilter节点应用条件:字段索引7不为FALSE
        + "      ArrowTableScan(table=[[ARROW, ARROWDATATYPE]], fields=[[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]])\n\n";  // ArrowTableScan扫描ARROWDATATYPE表的12个字段
    String result = "booleanField=null\nbooleanField=true\n";  // 定义预期的查询结果,前2行booleanField不为FALSE的记录(NULL和TRUE)

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .limit(2)  // 限制返回前2行
        .returns(result)  // 验证结果包含NULL和TRUE
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试带IS UNKNOWN过滤条件的字段投影
   * 该测试验证Arrow适配器能够正确处理IS UNKNOWN条件
   * IS UNKNOWN等价于IS NULL,用于检查布尔字段是否为NULL
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectFieldsWithIsUnknownFilter() {  // 测试方法:验证IS UNKNOWN操作符
    String sql = "select \"booleanField\"\n"  // 定义SQL查询,选择booleanField字段
        + "from arrowdatatype\n"  // 从arrowdatatype表
        + "where \"booleanField\" is unknown";  // WHERE条件:booleanField为UNKNOWN(即NULL)
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(booleanField=[$7])\n"  // ArrowProject节点投影booleanField字段(索引7)
        + "    ArrowFilter(condition=[IS NULL($7)])\n"  // ArrowFilter节点应用条件:字段索引7为NULL(IS UNKNOWN被转换为IS NULL)
        + "      ArrowTableScan(table=[[ARROW, ARROWDATATYPE]], fields=[[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]])\n\n";  // ArrowTableScan扫描ARROWDATATYPE表的12个字段
    String result = "booleanField=null\n";  // 定义预期的查询结果,只有一行booleanField=null的记录

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .limit(1)  // 限制返回前1行
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试带Decimal类型过滤条件的字段投影
   * 测试用例关联到JIRA issue CALCITE-6684:Arrow适配器应该支持Decimal类型的过滤条件
   * 该测试验证Arrow适配器能够正确处理Decimal类型的过滤条件
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectFieldsWithDecimalFilter() {  // 测试方法:验证Decimal类型过滤条件
    String sql = "select \"decimalField\"\n"  // 定义SQL查询,选择decimalField字段
        + "from arrowdatatype\n"  // 从arrowdatatype表
        + "where \"decimalField\" = 1.00";  // WHERE条件:decimalField等于1.00
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(decimalField=[$8])\n"  // ArrowProject节点投影decimalField字段(索引8)
        + "    ArrowFilter(condition=[=($8, 1.00)])\n"  // ArrowFilter节点应用等值条件:字段索引8等于1.00
        + "      ArrowTableScan(table=[[ARROW, ARROWDATATYPE]], fields=[[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]])\n\n";  // ArrowTableScan扫描ARROWDATATYPE表的12个字段
    String result = "decimalField=1.00\n";  // 定义预期的查询结果,只有一行decimalField=1.00的记录

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试带Double类型过滤条件的字段投影
   * 该测试验证Arrow适配器能够正确处理Double类型的过滤条件
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectFieldsWithDoubleFilter() {  // 测试方法:验证Double类型过滤条件
    String sql = "select \"doubleField\"\n"  // 定义SQL查询,选择doubleField字段
        + "from arrowdatatype\n"  // 从arrowdatatype表
        + "where \"doubleField\" = 1.00";  // WHERE条件:doubleField等于1.00
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(doubleField=[$6])\n"  // ArrowProject节点投影doubleField字段(索引6)
        + "    ArrowFilter(condition=[=($6, 1.0E0)])\n"  // ArrowFilter节点应用等值条件:字段索引6等于1.0E0(科学计数法)
        + "      ArrowTableScan(table=[[ARROW, ARROWDATATYPE]], fields=[[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]])\n\n";  // ArrowTableScan扫描ARROWDATATYPE表的12个字段
    String result = "doubleField=1.0\n";  // 定义预期的查询结果,只有一行doubleField=1.0的记录

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }

  /**
   * 测试带String类型过滤条件的字段投影
   * 该测试验证Arrow适配器能够正确处理String类型的过滤条件
   */
  @Test  // JUnit5注解,标记这是一个测试方法
  void testArrowProjectFieldsWithStringFilter() {  // 测试方法:验证String类型过滤条件
    String sql = "select \"stringField\"\n"  // 定义SQL查询,选择stringField字段
        + "from arrowdatatype\n"  // 从arrowdatatype表
        + "where \"stringField\" = '1'";  // WHERE条件:stringField等于'1'
    String plan = "PLAN=ArrowToEnumerableConverter\n"  // 定义预期的查询执行计划
        + "  ArrowProject(stringField=[$3])\n"  // ArrowProject节点投影stringField字段(索引3)
        + "    ArrowFilter(condition=[=($3, '1')])\n"  // ArrowFilter节点应用等值条件:字段索引3等于'1'
        + "      ArrowTableScan(table=[[ARROW, ARROWDATATYPE]], fields=[[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]])\n\n";  // ArrowTableScan扫描ARROWDATATYPE表的12个字段
    String result = "stringField=1\n";  // 定义预期的查询结果,只有一行stringField='1'的记录

    CalciteAssert.that()  // 创建CalciteAssert构建器
        .with(arrow)  // 配置Arrow模型
        .query(sql)  // 设置SQL查询
        .returns(result)  // 验证结果
        .explainContains(plan);  // 验证执行计划
  }
}