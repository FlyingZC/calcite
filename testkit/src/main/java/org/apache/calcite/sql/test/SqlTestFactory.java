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
package org.apache.calcite.sql.test; // 声明包名，该类属于org.apache.calcite.sql.test包，是Calcite SQL测试框架的核心工厂类

import org.apache.calcite.jdbc.JavaTypeFactoryImpl; // 导入Java类型工厂实现类，用于创建和管理Java数据类型
import org.apache.calcite.plan.Context; // 导入计划器上下文接口，用于传递配置信息给优化器
import org.apache.calcite.plan.Contexts; // 导入上下文工具类，提供创建上下文的便捷方法
import org.apache.calcite.plan.RelOptCluster; // 导入关系表达式簇类，是关系代数表达式的集合点
import org.apache.calcite.plan.RelOptPlanner; // 导入关系优化计划器接口，负责优化关系表达式树
import org.apache.calcite.plan.RelOptTable; // 导入关系优化表接口，代表优化过程中的表
import org.apache.calcite.prepare.Prepare; // 导入SQL准备类，提供SQL解析和验证的准备工作
import org.apache.calcite.rel.RelRoot; // 导入关系根节点类，代表关系表达式树的根
import org.apache.calcite.rel.type.DelegatingTypeSystem; // 导入委托类型系统类，用于包装和扩展类型系统
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，代表SQL数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建数据类型
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入关系数据类型系统接口，定义类型系统的行为
import org.apache.calcite.rex.RexBuilder; // 导入行表达式构建器类，用于构建RexNode表达式
import org.apache.calcite.sql.SqlNode; // 导入SQL节点抽象类，代表SQL语法树的节点
import org.apache.calcite.sql.SqlOperatorTable; // 导入SQL操作符表接口，包含所有可用的SQL操作符
import org.apache.calcite.sql.advise.SqlAdvisor; // 导入SQL建议器类，提供SQL补全和建议功能
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入标准SQL操作符表，包含所有标准SQL函数和操作符
import org.apache.calcite.sql.parser.SqlParseException; // 导入SQL解析异常类，表示SQL解析过程中发生的错误
import org.apache.calcite.sql.parser.SqlParser; // 导入SQL解析器类，负责将SQL字符串解析为语法树
import org.apache.calcite.sql.validate.SqlConformance; // 导入SQL一致性接口，定义SQL方言的兼容性级别
import org.apache.calcite.sql.validate.SqlValidator; // 导入SQL验证器接口，负责验证SQL语句的语义正确性
import org.apache.calcite.sql.validate.SqlValidatorCatalogReader; // 导入SQL验证器目录读取器接口，提供元数据访问
import org.apache.calcite.sql.validate.SqlValidatorUtil; // 导入SQL验证器工具类，提供验证相关的实用方法
import org.apache.calcite.sql.validate.SqlValidatorWithHints; // 导入带提示的SQL验证器接口，扩展了验证器功能
import org.apache.calcite.sql2rel.SqlToRelConverter; // 导入SQL到关系表达式转换器类，负责将SQL转换为关系代数
import org.apache.calcite.sql2rel.StandardConvertletTable; // 导入标准转换表，包含SQL表达式到Rex表达式的转换规则
import org.apache.calcite.test.CalciteAssert; // 导入Calcite断言工具类，用于测试中的断言
import org.apache.calcite.test.ConnectionFactories; // 导入连接工厂工具类，提供创建连接的便捷方法
import org.apache.calcite.test.ConnectionFactory; // 导入连接工厂接口，用于创建数据库连接
import org.apache.calcite.test.MockRelOptPlanner; // 导入模拟关系优化计划器类，用于测试
import org.apache.calcite.test.MockSqlOperatorTable; // 导入模拟SQL操作符表类，用于测试
import org.apache.calcite.test.catalog.MockCatalogReaderSimple; // 导入简单模拟目录读取器类，用于测试
import org.apache.calcite.util.SourceStringReader; // 导入源字符串读取器类，用于从字符串读取SQL

import com.google.common.base.Suppliers; // 导入Guava的Suppliers工具类，提供延迟初始化和缓存功能

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的参数

import java.util.List; // 导入List接口，用于处理列表集合
import java.util.function.Supplier; // 导入Supplier函数式接口，用于提供值的延迟获取
import java.util.function.UnaryOperator; // 导入一元操作符函数式接口，用于对单个参数进行转换

import static java.util.Objects.requireNonNull; // 导入Objects的requireNonNull静态方法，用于非空检查

/**
 * SQL测试工厂类 - Calcite SQL测试框架的核心工厂类
 * 
 * 该类是一个无状态的工厂类，负责创建SQL测试所需的各种组件，包括：
 * 1. SQL解析器（SqlParser）- 将SQL字符串解析为语法树
 * 2. SQL验证器（SqlValidator）- 验证SQL语句的语义正确性
 * 3. SQL建议器（SqlAdvisor）- 提供SQL补全和建议功能
 * 4. SQL到关系表达式转换器（SqlToRelConverter）- 将SQL转换为关系代数表达式
 * 5. 类型工厂（RelDataTypeFactory）- 创建和管理数据类型
 * 6. 优化器计划器（RelOptPlanner）- 优化关系表达式树
 * 
 * 该类采用Builder模式，通过一系列withXxx方法可以灵活地配置各个组件的创建方式。
 * 所有配置都通过构造函数传递，每次调用方法时都会使用当前的配置创建新实例。
 * 
 * 设计特点：
 * - 无状态：所有配置都通过参数传递，不持有可变状态
 * - 不可变：所有withXxx方法都返回新实例，不修改原实例
 * - 延迟初始化：使用Supplier实现组件的延迟创建和缓存
 * - 灵活配置：支持通过函数式接口自定义各个组件的创建逻辑
 * 
 * 使用场景：
 * - 单元测试：为SQL解析、验证、转换等功能的测试提供统一的组件创建入口
 * - 集成测试：配置不同的测试环境和数据源
 * - 功能验证：验证特定SQL方言或配置下的行为
*/
public class SqlTestFactory {
  // 预定义的默认SqlTestFactory实例，使用标准配置初始化
  // 该实例作为测试的默认配置，包含了HR（人力资源）模式，可以直接用于大多数测试场景
  public static final SqlTestFactory INSTANCE =
      new SqlTestFactory( // 创建SqlTestFactory实例，传入以下配置参数
          MockCatalogReaderSimple::create, // 目录读取器工厂：使用简单模拟目录读取器
          SqlTestFactory::createTypeFactory, // 类型工厂：使用自定义的createTypeFactory方法
          MockRelOptPlanner::new, // 计划器工厂：使用模拟关系优化计划器
          Contexts.of(), // 计划器上下文：空上下文
          UnaryOperator.identity(), // 簇转换器：恒等转换，不修改RelOptCluster
          SqlValidatorUtil::newValidator, // 验证器工厂：使用标准验证器创建方法
          ConnectionFactories.empty() // 连接工厂：从空连接工厂开始
              .with(ConnectionFactories.add(CalciteAssert.SchemaSpec.HR)), // 添加HR（人力资源）模式
          SqlParser.Config.DEFAULT, // 解析器配置：使用默认解析器配置
          SqlValidator.Config.DEFAULT, // 验证器配置：使用默认验证器配置
          SqlToRelConverter.CONFIG, // SQL到关系转换器配置：使用默认转换配置
          SqlStdOperatorTable.instance(), // 操作符表：使用标准SQL操作符表
          UnaryOperator.identity()) // 类型系统转换器：恒等转换，不修改类型系统
      .withOperatorTable(o -> MockSqlOperatorTable.of(o).extend()); // 扩展操作符表，添加模拟操作符

  // 连接工厂：用于创建数据库连接，提供测试所需的数据源
  // public修饰符允许外部访问和修改连接配置
  public final ConnectionFactory connectionFactory;
  
  // 类型工厂工厂：函数式接口，用于创建RelDataTypeFactory实例
  // RelDataTypeFactory负责创建和管理SQL数据类型（如INTEGER, VARCHAR等）
  public final TypeFactoryFactory typeFactoryFactory;
  
  // 目录读取器工厂：函数式接口，用于创建SqlValidatorCatalogReader实例
  // SqlValidatorCatalogReader提供对数据库元数据的访问，如表、列、索引等信息
  // private修饰符表示只能在类内部访问
  private final CatalogReaderFactory catalogReaderFactory;
  
  // 计划器工厂：函数式接口，用于创建RelOptPlanner实例
  // RelOptPlanner负责优化关系表达式树，选择最优的执行计划
  private final PlannerFactory plannerFactory;
  
  // 计划器上下文：传递给优化器的配置信息
  // Context是一个键值对容器，可以包含优化器需要的各种配置参数
  private final Context plannerContext;
  
  // 簇转换器：一元操作符，用于转换RelOptCluster实例
  // RelOptCluster是关系表达式树的根节点，包含优化器和RexBuilder
  // 该转换器允许在创建簇时进行自定义修改
  private final UnaryOperator<RelOptCluster> clusterTransform;
  
  // 验证器工厂：函数式接口，用于创建SqlValidator实例
  // SqlValidator负责验证SQL语句的语义正确性，如表名、列名、类型匹配等
  private final ValidatorFactory validatorFactory;

  // 类型工厂提供者：延迟初始化的RelDataTypeFactory实例
  // 使用Guava的Suppliers.memoize实现缓存，避免重复创建
  // RelDataTypeFactory是创建数据类型的核心工厂
  private final Supplier<RelDataTypeFactory> typeFactorySupplier;
  
  // SQL操作符表：包含所有可用的SQL操作符和函数
  // 如+、-、*、/、COUNT、SUM等，是SQL语义验证和转换的基础
  private final SqlOperatorTable operatorTable;
  
  // 目录读取器提供者：延迟初始化的SqlValidatorCatalogReader实例
  // 使用Guava的Suppliers.memoize实现缓存，避免重复创建
  // 提供对数据库元数据的访问，用于验证和转换过程
  private final Supplier<SqlValidatorCatalogReader> catalogReaderSupplier;
  
  // 解析器配置：SqlParser的配置对象
  // 定义了SQL解析的行为，如是否区分大小写、SQL方言等
  private final SqlParser.Config parserConfig;
  
  // 验证器配置：SqlValidator的配置对象，public修饰符允许外部访问
  // 定义了SQL验证的行为，如类型检查严格程度、一致性级别等
  public final SqlValidator.Config validatorConfig;
  
  // SQL到关系转换器配置：SqlToRelConverter的配置对象，public修饰符允许外部访问
  // 定义了SQL到关系表达式转换的行为，如是否展开子查询、转换规则等
  public final SqlToRelConverter.Config sqlToRelConfig;
  
  // 类型系统转换器：一元操作符，用于转换RelDataTypeSystem实例
  // RelDataTypeSystem定义了类型系统的行为，如类型转换规则、精度处理等
  // 该转换器允许自定义类型系统的行为
  public final UnaryOperator<RelDataTypeSystem> typeSystemTransform;

  // 受保护的构造方法：创建SqlTestFactory实例
  // protected修饰符允许子类继承和扩展
  // 
  // 参数说明：
  // @param catalogReaderFactory 目录读取器工厂，用于创建SqlValidatorCatalogReader
  // @param typeFactoryFactory 类型工厂工厂，用于创建RelDataTypeFactory
  // @param plannerFactory 计划器工厂，用于创建RelOptPlanner
  // @param plannerContext 计划器上下文，包含优化器的配置信息
  // @param clusterTransform 簇转换器，用于转换RelOptCluster
  // @param validatorFactory 验证器工厂，用于创建SqlValidator
  // @param connectionFactory 连接工厂，用于创建数据库连接
  // @param parserConfig 解析器配置，定义SQL解析行为
  // @param validatorConfig 验证器配置，定义SQL验证行为
  // @param sqlToRelConfig SQL到关系转换器配置，定义转换行为
  // @param operatorTable SQL操作符表，包含所有可用操作符
  // @param typeSystemTransform 类型系统转换器，用于转换类型系统
  protected SqlTestFactory(CatalogReaderFactory catalogReaderFactory,
      TypeFactoryFactory typeFactoryFactory, PlannerFactory plannerFactory,
      Context plannerContext, UnaryOperator<RelOptCluster> clusterTransform,
      ValidatorFactory validatorFactory,
      ConnectionFactory connectionFactory,
      SqlParser.Config parserConfig, SqlValidator.Config validatorConfig,
      SqlToRelConverter.Config sqlToRelConfig, SqlOperatorTable operatorTable,
      UnaryOperator<RelDataTypeSystem> typeSystemTransform) {
    // 验证并保存目录读取器工厂，使用requireNonNull确保不为null
    this.catalogReaderFactory =
        requireNonNull(catalogReaderFactory, "catalogReaderFactory");
    // 验证并保存类型工厂工厂，使用requireNonNull确保不为null
    this.typeFactoryFactory =
        requireNonNull(typeFactoryFactory, "typeFactoryFactory");
    // 验证并保存计划器工厂，使用requireNonNull确保不为null
    this.plannerFactory = requireNonNull(plannerFactory, "plannerFactory");
    // 验证并保存计划器上下文，使用requireNonNull确保不为null
    this.plannerContext = requireNonNull(plannerContext, "plannerContext");
    // 验证并保存簇转换器，使用requireNonNull确保不为null
    this.clusterTransform =
        requireNonNull(clusterTransform, "clusterTransform");
    // 验证并保存验证器工厂，使用requireNonNull确保不为null
    this.validatorFactory =
        requireNonNull(validatorFactory, "validatorFactory");
    // 验证并保存连接工厂，使用requireNonNull确保不为null
    this.connectionFactory =
        requireNonNull(connectionFactory, "connectionFactory");
    // 验证并保存SQL到关系转换器配置，使用requireNonNull确保不为null
    this.sqlToRelConfig = requireNonNull(sqlToRelConfig, "sqlToRelConfig");
    // 保存操作符表，允许为null
    this.operatorTable = operatorTable;
    // 保存类型系统转换器，允许为null
    this.typeSystemTransform = typeSystemTransform;
    // 创建类型工厂提供者，使用Guava的Suppliers.memoize实现延迟初始化和缓存
    // 当第一次调用get()时，会调用typeFactoryFactory.create()创建RelDataTypeFactory
    // 后续调用直接返回缓存的实例
    // 传入参数：验证器配置的一致性级别 和 应用类型系统转换后的默认类型系统
    this.typeFactorySupplier = Suppliers.memoize(() ->
        typeFactoryFactory.create(validatorConfig.conformance(),
            typeSystemTransform.apply(RelDataTypeSystem.DEFAULT)));
    // 创建目录读取器提供者，使用Guava的Suppliers.memoize实现延迟初始化和缓存
    // 当第一次调用get()时，会调用catalogReaderFactory.create()创建SqlValidatorCatalogReader
    // 后续调用直接返回缓存的实例
    // 传入参数：从typeFactorySupplier获取的类型工厂 和 解析器配置的大小写敏感标志
    this.catalogReaderSupplier = Suppliers.memoize(() ->
        catalogReaderFactory.create(this.typeFactorySupplier.get(),
            parserConfig.caseSensitive()));
    // 保存解析器配置
    this.parserConfig = parserConfig;
    // 保存验证器配置
    this.validatorConfig = validatorConfig;
  }

  // 创建SQL解析器
  // 该方法根据当前配置创建一个SqlParser实例，用于将SQL字符串解析为语法树
  // 
  // @param sql 要解析的SQL字符串
  // @return 配置好的SqlParser实例
  /** Creates a parser. */
  public SqlParser createParser(String sql) {
    // 特殊处理：如果SQL字符串为空，替换为单个空格
    // 这是因为JavaCC生成的语法无法接受空字符串，会导致token reader抛出异常
    if (sql.isEmpty()) {
      // I could not figure out how to convince the grammar to accept an empty
      // string.  Without this change I get an exception in the token reader in code
      // generated by JavaCC
      sql = " ";
    }
    // 获取当前配置的解析器配置对象
    SqlParser.Config parserConfig = parserConfig();
    // 创建并返回SqlParser实例
    // 使用SourceStringReader从字符串读取SQL，传入解析器配置
    return SqlParser.create(new SourceStringReader(sql), parserConfig);
  }

  // 创建SQL验证器
  // 该方法根据当前配置创建一个SqlValidator实例，用于验证SQL语句的语义正确性
  // 
  // @return 配置好的SqlValidator实例
  /** Creates a validator. */
  public SqlValidator createValidator() {
    // 使用验证器工厂创建SqlValidator实例
    // 传入参数：
    // - operatorTable: SQL操作符表，包含所有可用的操作符和函数
    // - catalogReaderSupplier.get(): 从提供者获取目录读取器，提供元数据访问
    // - typeFactorySupplier.get(): 从提供者获取类型工厂，用于创建数据类型
    // - validatorConfig: 验证器配置，定义验证行为
    return validatorFactory.create(operatorTable, catalogReaderSupplier.get(),
        typeFactorySupplier.get(), validatorConfig);
  }

  // 创建SQL建议器
  // 该方法创建一个SqlAdvisor实例，用于提供SQL补全和建议功能
  // SqlAdvisor可以帮助用户完成SQL语句，提供表名、列名等自动补全
  // 
  // @return SqlAdvisor实例
  // @throws UnsupportedOperationException 如果验证器不支持提示功能
  public SqlAdvisor createAdvisor() {
    // 首先创建一个SQL验证器
    SqlValidator validator = createValidator();
    // 检查验证器是否实现了SqlValidatorWithHints接口（带提示的验证器）
    // SqlAdvisor需要验证器提供额外的元数据信息来生成建议
    if (validator instanceof SqlValidatorWithHints) {
      // 如果支持，创建SqlAdvisor实例
      // 传入参数：
      // - (SqlValidatorWithHints) validator: 强制转换为带提示的验证器
      // - parserConfig: 解析器配置，用于解析不完整的SQL语句
      return new SqlAdvisor((SqlValidatorWithHints) validator, parserConfig);
    }
    // 如果验证器不支持提示功能，抛出异常
    // SqlAdvisor需要特定的验证器实现才能工作
    throw new UnsupportedOperationException(
        "Validator should implement SqlValidatorWithHints, actual validator is " + validator);
  }

  // 设置类型工厂工厂，返回新的SqlTestFactory实例
  // 该方法使用Builder模式，不修改当前实例，而是返回一个新实例
  // 如果新值与当前值相同，直接返回当前实例，避免不必要的对象创建
  // 
  // @param typeFactoryFactory 新的类型工厂工厂
  // @return 新的SqlTestFactory实例（如果值相同则返回当前实例）
  public SqlTestFactory withTypeFactoryFactory(
      TypeFactoryFactory typeFactoryFactory) {
    // 如果新值与当前值相同，直接返回当前实例
    if (typeFactoryFactory.equals(this.typeFactoryFactory)) {
      return this;
    }
    // 创建并返回新的SqlTestFactory实例，只修改typeFactoryFactory参数
    // 其他参数保持不变
    return new SqlTestFactory(catalogReaderFactory, typeFactoryFactory,
        plannerFactory, plannerContext, clusterTransform, validatorFactory,
        connectionFactory, parserConfig, validatorConfig, sqlToRelConfig,
        operatorTable, typeSystemTransform);
  }

  // 设置类型系统转换器，返回新的SqlTestFactory实例
  // 该方法使用Builder模式，不修改当前实例，而是返回一个新实例
  // 类型系统转换器可以自定义类型系统的行为，如类型转换规则、精度处理等
  // 如果新值与当前值相同，直接返回当前实例，避免不必要的对象创建
  // 
  // @param typeSystemTransform 新的类型系统转换器
  // @return 新的SqlTestFactory实例（如果值相同则返回当前实例）
  public SqlTestFactory withTypeSystem(
      UnaryOperator<RelDataTypeSystem> typeSystemTransform) {
    // 如果新值与当前值相同，直接返回当前实例
    if (typeSystemTransform.equals(this.typeSystemTransform)) {
      return this;
    }
    // 创建并返回新的SqlTestFactory实例，只修改typeSystemTransform参数
    // 其他参数保持不变
    return new SqlTestFactory(catalogReaderFactory, typeFactoryFactory,
        plannerFactory, plannerContext, clusterTransform, validatorFactory,
        connectionFactory, parserConfig, validatorConfig, sqlToRelConfig,
        operatorTable, typeSystemTransform);
  }

  // 设置计划器工厂，返回新的SqlTestFactory实例
  // 该方法使用Builder模式，不修改当前实例，而是返回一个新实例
  // 计划器工厂用于创建RelOptPlanner，负责优化关系表达式树
  // 如果新值与当前值相同，直接返回当前实例，避免不必要的对象创建
  // 
  // @param plannerFactory 新的计划器工厂
  // @return 新的SqlTestFactory实例（如果值相同则返回当前实例）
  public SqlTestFactory withPlannerFactory(PlannerFactory plannerFactory) {
    // 如果新值与当前值相同，直接返回当前实例
    if (plannerFactory.equals(this.plannerFactory)) {
      return this;
    }
    // 创建并返回新的SqlTestFactory实例，只修改plannerFactory参数
    // 其他参数保持不变
    return new SqlTestFactory(catalogReaderFactory, typeFactoryFactory,
        plannerFactory, plannerContext, clusterTransform, validatorFactory,
        connectionFactory, parserConfig, validatorConfig, sqlToRelConfig,
        operatorTable, typeSystemTransform);
  }

  // 设置计划器上下文，返回新的SqlTestFactory实例
  // 该方法接受一个转换函数，用于修改当前的计划器上下文
  // 计划器上下文包含优化器的配置信息，可以传递各种配置参数
  // 如果转换后的值与当前值相同，直接返回当前实例，避免不必要的对象创建
  // 
  // @param transform 转换函数，接受当前上下文并返回新上下文
  // @return 新的SqlTestFactory实例（如果值相同则返回当前实例）
  public SqlTestFactory withPlannerContext(
      UnaryOperator<Context> transform) {
    // 应用转换函数到当前的计划器上下文
    final Context plannerContext = transform.apply(this.plannerContext);
    // 如果转换后的值与当前值相同，直接返回当前实例
    if (plannerContext.equals(this.plannerContext)) {
      return this;
    }
    // 创建并返回新的SqlTestFactory实例，只修改plannerContext参数
    // 其他参数保持不变
    return new SqlTestFactory(catalogReaderFactory, typeFactoryFactory,
        plannerFactory, plannerContext, clusterTransform, validatorFactory,
        connectionFactory, parserConfig, validatorConfig, sqlToRelConfig,
        operatorTable, typeSystemTransform);
  }

  // 设置簇转换器，返回新的SqlTestFactory实例
  // 该方法接受一个转换函数，用于修改RelOptCluster的创建过程
  // RelOptCluster是关系表达式树的根节点，包含优化器和RexBuilder
  // 新的转换函数会与现有转换函数组合，形成转换链
  // 
  // @param transform 转换函数，接受RelOptCluster并返回修改后的RelOptCluster
  // @return 新的SqlTestFactory实例
  public SqlTestFactory withCluster(UnaryOperator<RelOptCluster> transform) {
    // 将新的转换函数与现有转换函数组合
    // 使用andThen方法形成转换链：先执行this.clusterTransform，再执行transform
    // ::apply方法引用确保组合后的转换器正确应用
    final UnaryOperator<RelOptCluster> clusterTransform =
        this.clusterTransform.andThen(transform)::apply;
    // 创建并返回新的SqlTestFactory实例，使用组合后的clusterTransform
    return new SqlTestFactory(catalogReaderFactory, typeFactoryFactory,
        plannerFactory, plannerContext, clusterTransform, validatorFactory,
        connectionFactory, parserConfig, validatorConfig, sqlToRelConfig,
        operatorTable, typeSystemTransform);
  }

  // 设置目录读取器工厂，返回新的SqlTestFactory实例
  // 该方法使用Builder模式，不修改当前实例，而是返回一个新实例
  // 目录读取器工厂用于创建SqlValidatorCatalogReader，提供元数据访问
  // 如果新值与当前值相同，直接返回当前实例，避免不必要的对象创建
  // 
  // @param catalogReaderFactory 新的目录读取器工厂
  // @return 新的SqlTestFactory实例（如果值相同则返回当前实例）
  public SqlTestFactory withCatalogReader(
      CatalogReaderFactory catalogReaderFactory) {
    // 如果新值与当前值相同，直接返回当前实例
    if (catalogReaderFactory.equals(this.catalogReaderFactory)) {
      return this;
    }
    // 创建并返回新的SqlTestFactory实例，只修改catalogReaderFactory参数
    // 其他参数保持不变
    return new SqlTestFactory(catalogReaderFactory, typeFactoryFactory,
        plannerFactory, plannerContext, clusterTransform, validatorFactory,
        connectionFactory, parserConfig, validatorConfig, sqlToRelConfig,
        operatorTable, typeSystemTransform);
  }

  // 设置验证器工厂，返回新的SqlTestFactory实例
  // 该方法使用Builder模式，不修改当前实例，而是返回一个新实例
  // 验证器工厂用于创建SqlValidator，负责验证SQL语句的语义正确性
  // 如果新值与当前值相同，直接返回当前实例，避免不必要的对象创建
  // 
  // @param validatorFactory 新的验证器工厂
  // @return 新的SqlTestFactory实例（如果值相同则返回当前实例）
  public SqlTestFactory withValidator(ValidatorFactory validatorFactory) {
    // 如果新值与当前值相同，直接返回当前实例
    if (validatorFactory.equals(this.validatorFactory)) {
      return this;
    }
    // 创建并返回新的SqlTestFactory实例，只修改validatorFactory参数
    // 其他参数保持不变
    return new SqlTestFactory(catalogReaderFactory, typeFactoryFactory,
        plannerFactory, plannerContext, clusterTransform, validatorFactory,
        connectionFactory, parserConfig, validatorConfig, sqlToRelConfig,
        operatorTable, typeSystemTransform);
  }

  // 设置验证器配置，返回新的SqlTestFactory实例
  // 该方法接受一个转换函数，用于修改当前的验证器配置
  // 验证器配置定义了SQL验证的行为，如类型检查严格程度、一致性级别等
  // 如果转换后的值与当前值相同，直接返回当前实例，避免不必要的对象创建
  // 
  // @param transform 转换函数，接受当前配置并返回新配置
  // @return 新的SqlTestFactory实例（如果值相同则返回当前实例）
  public SqlTestFactory withValidatorConfig(
      UnaryOperator<SqlValidator.Config> transform) {
    // 应用转换函数到当前的验证器配置
    final SqlValidator.Config validatorConfig =
        transform.apply(this.validatorConfig);
    // 如果转换后的值与当前值相同，直接返回当前实例
    if (validatorConfig.equals(this.validatorConfig)) {
      return this;
    }
    // 创建并返回新的SqlTestFactory实例，只修改validatorConfig参数
    // 其他参数保持不变
    return new SqlTestFactory(catalogReaderFactory, typeFactoryFactory,
        plannerFactory, plannerContext, clusterTransform, validatorFactory,
        connectionFactory, parserConfig, validatorConfig, sqlToRelConfig,
        operatorTable, typeSystemTransform);
  }

  // 设置SQL到关系转换器配置，返回新的SqlTestFactory实例
  // 该方法接受一个转换函数，用于修改当前的SQL到关系转换器配置
  // SQL到关系转换器配置定义了SQL转换为关系表达式树的行为
  // 如果转换后的值与当前值相同，直接返回当前实例，避免不必要的对象创建
  // 
  // @param transform 转换函数，接受当前配置并返回新配置
  // @return 新的SqlTestFactory实例（如果值相同则返回当前实例）
  public SqlTestFactory withSqlToRelConfig(
      UnaryOperator<SqlToRelConverter.Config> transform) {
    // 应用转换函数到当前的SQL到关系转换器配置
    final SqlToRelConverter.Config sqlToRelConfig =
        transform.apply(this.sqlToRelConfig);
    // 如果转换后的值与当前值相同，直接返回当前实例
    if (sqlToRelConfig.equals(this.sqlToRelConfig)) {
      return this;
    }
    // 创建并返回新的SqlTestFactory实例，只修改sqlToRelConfig参数
    // 其他参数保持不变
    return new SqlTestFactory(catalogReaderFactory, typeFactoryFactory,
        plannerFactory, plannerContext, clusterTransform, validatorFactory,
        connectionFactory, parserConfig, validatorConfig, sqlToRelConfig,
        operatorTable, typeSystemTransform);
  }

  // 私有静态方法：创建类型工厂
  // 该方法根据SQL一致性级别和类型系统创建RelDataTypeFactory实例
  // RelDataTypeFactory是创建和管理SQL数据类型的核心工厂
  // 
  // @param conformance SQL一致性级别，定义SQL方言的兼容性
  // @param typeSystem 类型系统，定义类型系统的行为
  // @return RelDataTypeFactory实例
  private static RelDataTypeFactory createTypeFactory(SqlConformance conformance,
      RelDataTypeSystem typeSystem) {
    // 检查一致性级别是否要求将不规则联合类型转换为可变类型
    // 不规则联合类型是指UNION操作中各分支的列数或类型不一致的情况
    if (conformance.shouldConvertRaggedUnionTypesToVarying()) {
      // 如果需要，创建一个委托类型系统包装器
      // 使用匿名内部类重写shouldConvertRaggedUnionTypesToVarying方法
      // 返回true表示启用不规则联合类型转换
      typeSystem = new DelegatingTypeSystem(typeSystem) {
        @Override public boolean shouldConvertRaggedUnionTypesToVarying() {
          return true;
        }
      };
    }
    // 创建并返回JavaTypeFactoryImpl实例
    // JavaTypeFactoryImpl是基于Java类型系统的类型工厂实现
    return new JavaTypeFactoryImpl(typeSystem);
  }

  // 设置解析器配置，返回新的SqlTestFactory实例
  // 该方法接受一个转换函数，用于修改当前的解析器配置
  // 解析器配置定义了SQL解析的行为，如是否区分大小写、SQL方言等
  // 如果转换后的值与当前值相同，直接返回当前实例，避免不必要的对象创建
  // 
  // @param transform 转换函数，接受当前配置并返回新配置
  // @return 新的SqlTestFactory实例（如果值相同则返回当前实例）
  public SqlTestFactory withParserConfig(
      UnaryOperator<SqlParser.Config> transform) {
    // 应用转换函数到当前的解析器配置
    final SqlParser.Config parserConfig = transform.apply(this.parserConfig);
    // 如果转换后的值与当前值相同，直接返回当前实例
    if (parserConfig.equals(this.parserConfig)) {
      return this;
    }
    // 创建并返回新的SqlTestFactory实例，只修改parserConfig参数
    // 其他参数保持不变
    return new SqlTestFactory(catalogReaderFactory, typeFactoryFactory,
        plannerFactory, plannerContext, clusterTransform, validatorFactory,
        connectionFactory, parserConfig, validatorConfig, sqlToRelConfig,
        operatorTable, typeSystemTransform);
  }

  // 设置连接工厂，返回新的SqlTestFactory实例
  // 该方法接受一个转换函数，用于修改当前的连接工厂
  // 连接工厂用于创建数据库连接，提供测试所需的数据源
  // 如果转换后的值与当前值相同，直接返回当前实例，避免不必要的对象创建
  // 
  // @param transform 转换函数，接受当前连接工厂并返回新连接工厂
  // @return 新的SqlTestFactory实例（如果值相同则返回当前实例）
  public SqlTestFactory withConnectionFactory(
      UnaryOperator<ConnectionFactory> transform) {
    // 应用转换函数到当前的连接工厂
    final ConnectionFactory connectionFactory =
        transform.apply(this.connectionFactory);
    // 如果转换后的值与当前值相同，直接返回当前实例
    if (connectionFactory.equals(this.connectionFactory)) {
      return this;
    }
    // 创建并返回新的SqlTestFactory实例，只修改connectionFactory参数
    // 其他参数保持不变
    return new SqlTestFactory(catalogReaderFactory, typeFactoryFactory,
        plannerFactory, plannerContext, clusterTransform, validatorFactory,
        connectionFactory, parserConfig, validatorConfig, sqlToRelConfig,
        operatorTable, typeSystemTransform);
  }

  // 设置操作符表，返回新的SqlTestFactory实例
  // 该方法接受一个转换函数，用于修改当前的操作符表
  // 操作符表包含所有可用的SQL操作符和函数，如+、-、*、/、COUNT、SUM等
  // 如果转换后的值与当前值相同，直接返回当前实例，避免不必要的对象创建
  // 
  // @param transform 转换函数，接受当前操作符表并返回新操作符表
  // @return 新的SqlTestFactory实例（如果值相同则返回当前实例）
  public SqlTestFactory withOperatorTable(
      UnaryOperator<SqlOperatorTable> transform) {
    // 应用转换函数到当前的操作符表
    final SqlOperatorTable operatorTable =
        transform.apply(this.operatorTable);
    // 如果转换后的值与当前值相同，直接返回当前实例
    if (operatorTable.equals(this.operatorTable)) {
      return this;
    }
    // 创建并返回新的SqlTestFactory实例，只修改operatorTable参数
    // 其他参数保持不变
    return new SqlTestFactory(catalogReaderFactory, typeFactoryFactory,
        plannerFactory, plannerContext, clusterTransform, validatorFactory,
        connectionFactory, parserConfig, validatorConfig, sqlToRelConfig,
        operatorTable, typeSystemTransform);
  }

  // 获取解析器配置
  // 该方法返回当前的解析器配置对象
  // 解析器配置定义了SQL解析的行为，如是否区分大小写、SQL方言等
  // 
  // @return 当前的SqlParser.Config实例
  public SqlParser.Config parserConfig() {
    return parserConfig;
  }

  // 获取类型工厂
  // 该方法返回RelDataTypeFactory实例，用于创建和管理SQL数据类型
  // 使用延迟初始化的提供者，避免重复创建
  // 
  // @return RelDataTypeFactory实例
  public RelDataTypeFactory getTypeFactory() {
    // 从提供者获取类型工厂实例
    // 如果是第一次调用，会创建并缓存实例
    // 后续调用直接返回缓存的实例
    return typeFactorySupplier.get();
  }

  // 创建SQL到关系表达式转换器
  // 该方法创建一个SqlToRelConverter实例，负责将SQL转换为关系表达式树
  // 转换过程包括：解析、验证、转换为关系代数表达式
  // 
  // @return 配置好的SqlToRelConverter实例
  public SqlToRelConverter createSqlToRelConverter() {
    // 获取类型工厂，用于创建数据类型
    final RelDataTypeFactory typeFactory = getTypeFactory();
    // 获取目录读取器，提供元数据访问，强制转换为Prepare.CatalogReader类型
    final Prepare.CatalogReader catalogReader =
        (Prepare.CatalogReader) catalogReaderSupplier.get();
    // 创建SQL验证器，用于验证SQL语句的语义正确性
    final SqlValidator validator = createValidator();
    // 创建行表达式构建器，用于构建RexNode表达式
    final RexBuilder rexBuilder = new RexBuilder(typeFactory);
    // 创建关系优化计划器，用于优化关系表达式树
    final RelOptPlanner planner = plannerFactory.create(plannerContext);
    // 创建关系表达式簇，并应用簇转换器
    // RelOptCluster是关系表达式树的根节点，包含优化器和RexBuilder
    final RelOptCluster cluster =
        clusterTransform.apply(RelOptCluster.create(planner, rexBuilder));
    // 创建视图扩展器，用于展开视图定义
    // MockViewExpander是测试用的视图扩展器实现
    RelOptTable.ViewExpander viewExpander =
        new MockViewExpander(validator, catalogReader, cluster,
            sqlToRelConfig);
    // 创建并返回SQL到关系表达式转换器
    // 传入参数：
    // - viewExpander: 视图扩展器，用于展开视图
    // - validator: SQL验证器，用于验证SQL
    // - catalogReader: 目录读取器，提供元数据访问
    // - cluster: 关系表达式簇，是关系表达式树的根节点
    // - StandardConvertletTable.INSTANCE: 标准转换表，包含表达式转换规则
    // - sqlToRelConfig: SQL到关系转换器配置，定义转换行为
    return new SqlToRelConverter(viewExpander, validator, catalogReader, cluster,
        StandardConvertletTable.INSTANCE, sqlToRelConfig);
  }

  // 类型工厂工厂接口 - 用于创建RelDataTypeFactory实例
  // 这是一个函数式接口，允许自定义类型工厂的创建逻辑
  // RelDataTypeFactory是创建和管理SQL数据类型的核心工厂
  /** Creates a {@link RelDataTypeFactory} for tests. */
  public interface TypeFactoryFactory {
    // 创建RelDataTypeFactory实例
    // 
    // @param conformance SQL一致性级别，定义SQL方言的兼容性
    // @param typeSystem 类型系统，定义类型系统的行为
    // @return RelDataTypeFactory实例
    RelDataTypeFactory create(SqlConformance conformance,
        RelDataTypeSystem typeSystem);
  }

  // 计划器工厂接口 - 用于创建RelOptPlanner实例
  // 这是一个函数式接口，允许自定义优化计划器的创建逻辑
  // RelOptPlanner负责优化关系表达式树，选择最优的执行计划
  /** Creates a {@link RelOptPlanner} for tests. */
  public interface PlannerFactory {
    // 创建RelOptPlanner实例
    // 
    // @param context 计划器上下文，包含优化器的配置信息
    // @return RelOptPlanner实例
    RelOptPlanner create(Context context);
  }

  // 验证器工厂接口 - 用于创建SqlValidator实例
  // 这是一个函数式接口，允许自定义SQL验证器的创建逻辑
  // SqlValidator负责验证SQL语句的语义正确性
  /** Creates {@link SqlValidator} for tests. */
  public interface ValidatorFactory {
    // 创建SqlValidator实例
    // 
    // @param opTab SQL操作符表，包含所有可用的操作符和函数
    // @param catalogReader 目录读取器，提供元数据访问
    // @param typeFactory 类型工厂，用于创建数据类型
    // @param config 验证器配置，定义验证行为
    // @return SqlValidator实例
    SqlValidator create(
        SqlOperatorTable opTab,
        SqlValidatorCatalogReader catalogReader,
        RelDataTypeFactory typeFactory,
        SqlValidator.Config config);
  }

  // 目录读取器工厂接口 - 用于创建SqlValidatorCatalogReader实例
  // 这是一个函数式接口，使用@FunctionalInterface注解标记
  // SqlValidatorCatalogReader提供对数据库元数据的访问
  /** Creates a {@link SqlValidatorCatalogReader} for tests. */
  @FunctionalInterface
  public interface CatalogReaderFactory {
    // 创建SqlValidatorCatalogReader实例
    // 
    // @param typeFactory 类型工厂，用于创建数据类型
    // @param caseSensitive 是否区分大小写，影响标识符的匹配规则
    // @return SqlValidatorCatalogReader实例
    SqlValidatorCatalogReader create(RelDataTypeFactory typeFactory,
        boolean caseSensitive);
  }

  // Mock视图扩展器类 - RelOptTable.ViewExpander的测试实现
  // 该类用于在测试中展开视图定义，将视图的SQL语句转换为关系表达式
  // 视图扩展器是SQL到关系转换过程中的关键组件
  /** Implementation for {@link RelOptTable.ViewExpander} for testing. */
  private static class MockViewExpander implements RelOptTable.ViewExpander {
    // SQL验证器，用于验证视图定义的SQL语句
    private final SqlValidator validator;
    // 目录读取器，提供元数据访问
    private final Prepare.CatalogReader catalogReader;
    // 关系表达式簇，是关系表达式树的根节点
    private final RelOptCluster cluster;
    // SQL到关系转换器配置，定义转换行为
    private final SqlToRelConverter.Config config;

    // 构造方法：创建MockViewExpander实例
    // 
    // @param validator SQL验证器，用于验证视图定义
    // @param catalogReader 目录读取器，提供元数据访问
    // @param cluster 关系表达式簇
    // @param config SQL到关系转换器配置
    MockViewExpander(SqlValidator validator,
        Prepare.CatalogReader catalogReader, RelOptCluster cluster,
        SqlToRelConverter.Config config) {
      // 保存SQL验证器
      this.validator = validator;
      // 保存目录读取器
      this.catalogReader = catalogReader;
      // 保存关系表达式簇
      this.cluster = cluster;
      // 保存转换器配置
      this.config = config;
    }

    // 展开视图定义，将视图的SQL语句转换为关系表达式
    // 
    // @param rowType 视图的行类型，定义视图的列结构
    // @param queryString 视图的SQL定义语句
    // @param schemaPath 模式路径，用于解析表名
    // @param viewPath 视图路径，可能为null
    // @return RelRoot对象，代表展开后的关系表达式树的根
    // @throws RuntimeException 如果解析或转换过程中发生错误
    @Override public RelRoot expandView(RelDataType rowType, String queryString,
        List<String> schemaPath, @Nullable List<String> viewPath) {
      try {
        // 解析视图的SQL定义语句，生成SQL语法树
        SqlNode parsedNode = SqlParser.create(queryString).parseStmt();
        // 验证SQL语法树，确保语义正确
        SqlNode validatedNode = validator.validate(parsedNode);
        // 创建SQL到关系表达式转换器
        SqlToRelConverter converter =
            new SqlToRelConverter(this, validator, catalogReader, cluster,
                StandardConvertletTable.INSTANCE, config);
        // 将验证后的SQL转换为关系表达式树
        // 参数说明：
        // - validatedNode: 验证后的SQL节点
        // - false: 不需要验证（已经验证过）
        // - true: 需要顶层转换
        return converter.convertQuery(validatedNode, false, true);
      } catch (SqlParseException e) {
        // 如果解析或转换过程中发生异常，包装为RuntimeException抛出
        throw new RuntimeException("Error happened while expanding view.", e);
      }
    }
  }
}
