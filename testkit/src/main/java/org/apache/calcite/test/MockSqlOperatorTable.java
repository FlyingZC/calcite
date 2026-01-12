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
package org.apache.calcite.test; // 声明包名，表示这个类属于org.apache.calcite.test包

import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建关系数据类型
import org.apache.calcite.sql.SqlAggFunction; // 导入SqlAggFunction类，表示SQL聚合函数
import org.apache.calcite.sql.SqlBasicFunction; // 导入SqlBasicFunction类，表示基本的SQL函数
import org.apache.calcite.sql.SqlCallBinding; // 导入SqlCallBinding类，表示SQL调用绑定
import org.apache.calcite.sql.SqlFunction; // 导入SqlFunction类，表示SQL函数
import org.apache.calcite.sql.SqlFunctionCategory; // 导入SqlFunctionCategory枚举，表示函数类别
import org.apache.calcite.sql.SqlIdentifier; // 导入SqlIdentifier类，表示SQL标识符
import org.apache.calcite.sql.SqlKind; // 导入SqlKind枚举，表示SQL操作符种类
import org.apache.calcite.sql.SqlNode; // 导入SqlNode接口，表示SQL语法树节点
import org.apache.calcite.sql.SqlOperandCountRange; // 导入SqlOperandCountRange接口，表示操作数数量范围
import org.apache.calcite.sql.SqlOperator; // 导入SqlOperator类，表示SQL操作符
import org.apache.calcite.sql.SqlOperatorBinding; // 导入SqlOperatorBinding类，表示SQL操作符绑定
import org.apache.calcite.sql.SqlOperatorTable; // 导入SqlOperatorTable接口，表示SQL操作符表
import org.apache.calcite.sql.SqlTableFunction; // 导入SqlTableFunction接口，表示SQL表函数
import org.apache.calcite.sql.TableCharacteristic; // 导入TableCharacteristic类，表示表特征
import org.apache.calcite.sql.fun.SqlLibrary; // 导入SqlLibrary枚举，表示SQL库
import org.apache.calcite.sql.fun.SqlLibraryOperatorTableFactory; // 导入SqlLibraryOperatorTableFactory类，用于创建SQL库操作符表
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SqlStdOperatorTable类，表示标准SQL操作符表
import org.apache.calcite.sql.parser.SqlParserPos; // 导入SqlParserPos类，表示SQL解析位置
import org.apache.calcite.sql.type.OperandTypes; // 导入OperandTypes类，提供操作数类型检查工具
import org.apache.calcite.sql.type.ReturnTypes; // 导入ReturnTypes类，提供返回类型推断工具
import org.apache.calcite.sql.type.SqlOperandCountRanges; // 导入SqlOperandCountRanges类，提供操作数数量范围工具
import org.apache.calcite.sql.type.SqlOperandMetadata; // 导入SqlOperandMetadata接口，表示操作数元数据
import org.apache.calcite.sql.type.SqlReturnTypeInference; // 导入SqlReturnTypeInference接口，表示返回类型推断
import org.apache.calcite.sql.type.SqlTypeFamily; // 导入SqlTypeFamily枚举，表示SQL类型族
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，表示SQL类型名称
import org.apache.calcite.sql.type.SqlTypeUtil; // 导入SqlTypeUtil类，提供SQL类型工具方法
import org.apache.calcite.sql.type.TableFunctionReturnTypeInference; // 导入TableFunctionReturnTypeInference类，表示表函数返回类型推断
import org.apache.calcite.sql.util.ChainedSqlOperatorTable; // 导入ChainedSqlOperatorTable类，表示链式SQL操作符表
import org.apache.calcite.sql.util.SqlOperatorTables; // 导入SqlOperatorTables类，提供SQL操作符表工具方法
import org.apache.calcite.sql.validate.SqlValidator; // 导入SqlValidator接口，表示SQL验证器
import org.apache.calcite.util.Optionality; // 导入Optionality枚举，表示可选性

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList类，表示不可变列表
import com.google.common.collect.ImmutableMap; // 导入Google Guava的ImmutableMap类，表示不可变映射
import com.google.common.collect.Iterables; // 导入Google Guava的Iterables类，提供迭代器工具
import com.google.common.collect.Lists; // 导入Google Guava的Lists类，提供列表工具

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，表示可能为null

import java.util.List; // 导入Java标准库的List接口
import java.util.Map; // 导入Java标准库的Map接口

/**
 * Mock operator table for testing purposes. Contains the standard SQL operator
 * table, plus a list of operators.
 * 用于测试目的的模拟操作符表。包含标准SQL操作符表，以及一系列额外的操作符。
 * 这个类主要用于在测试环境中提供各种自定义的SQL操作符和函数，以便测试Calcite的SQL解析、验证和优化功能。
 * 它继承自ChainedSqlOperatorTable，支持链式操作符表，可以组合多个操作符表。
 */
public class MockSqlOperatorTable extends ChainedSqlOperatorTable { // 定义MockSqlOperatorTable类，继承自ChainedSqlOperatorTable，表示这是一个模拟的SQL操作符表
  /** Internal constructor; call {@link #standard()},
   * {@link #of(SqlOperatorTable)},
   * {@link #plus(Iterable)}, or
   * {@link #extend()}. */
  // 内部构造方法；建议调用standard()、of(SqlOperatorTable)、plus(Iterable)或extend()静态方法来创建实例，而不是直接调用此构造方法
  private MockSqlOperatorTable(SqlOperatorTable parentTable) { // 私有构造方法，接受一个父操作符表作为参数
    super(ImmutableList.of(parentTable)); // 调用父类ChainedSqlOperatorTable的构造方法，将父操作符表包装在不可变列表中
  }

  /** Returns the operator table that contains only the standard operators. */
  // 返回只包含标准操作符的操作符表，这是一个静态工厂方法，用于创建包含标准SQL操作符的MockSqlOperatorTable实例
  public static MockSqlOperatorTable standard() { // 定义静态方法standard，返回MockSqlOperatorTable实例
    return of(SqlStdOperatorTable.instance()); // 调用of方法，传入标准SQL操作符表的实例
  }

  /** Returns a mock operator table based on the given operator table. */
  // 基于给定的操作符表返回一个模拟操作符表，这是一个静态工厂方法，用于包装任何操作符表为MockSqlOperatorTable
  public static MockSqlOperatorTable of(SqlOperatorTable operatorTable) { // 定义静态方法of，接受一个SqlOperatorTable参数，返回MockSqlOperatorTable实例
    return new MockSqlOperatorTable(operatorTable); // 调用私有构造方法，创建新的MockSqlOperatorTable实例
  }

  /** Returns this table with a few mock operators added. */
  // 返回添加了几个模拟操作符的表，这个方法用于扩展当前操作符表，添加各种测试用的自定义操作符和函数
  public MockSqlOperatorTable extend() { // 定义实例方法extend，返回扩展后的MockSqlOperatorTable实例
    // Don't use anonymous inner classes. They can't be instantiated
    // using reflection when we are deserializing from JSON.
    // 不要使用匿名内部类。当从JSON反序列化时，它们无法通过反射实例化
    final SqlOperatorTable parentTable = Iterables.getOnlyElement(tableList); // 从tableList中获取唯一的父操作符表
    return new MockSqlOperatorTable( // 创建并返回新的MockSqlOperatorTable实例
        SqlOperatorTables.chain(parentTable, // 将父操作符表与新的操作符表链式连接
            SqlOperatorTables.of(new RampFunction(), // 添加RampFunction表函数，用于生成递增序列
                new DedupFunction(), // 添加DedupFunction表函数，用于去重
                new TableFunctionReturnTableFunction(), // 添加TableFunctionReturnTableFunction表函数，用于测试表函数返回类型
                new MyFunction(), // 添加MyFunction标量函数，用户自定义函数
                new MyAvgAggFunction(), // 添加MyAvgAggFunction聚合函数，用于测试聚合功能
                new RowFunction(), // 添加RowFunction表函数，返回包含可空和非空字段的行类型
                new NotATableFunction(), // 添加NotATableFunction函数，用于测试非表函数
                new BadTableFunction(), // 添加BadTableFunction函数，用于测试错误的表函数实现
                new StructuredFunction(), // 添加StructuredFunction函数，返回结构化类型
                new CompositeFunction(), // 添加CompositeFunction函数，复合函数
                new ScoreTableFunction(), // 添加ScoreTableFunction表函数，用于评分
                new TopNTableFunction(), // 添加TopNTableFunction表函数，用于获取前N条记录
                new SimilarlityTableFunction(), // 添加SimilarlityTableFunction表函数，用于相似度分析
                new InvalidTableFunction(), // 添加InvalidTableFunction表函数，无效的表函数实现
                new CompareStringsOrNumericValues(), // 添加CompareStringsOrNumericValues函数，比较字符串或数值
                HIGHER_ORDER_FUNCTION, // 添加高阶函数HIGHER_ORDER_FUNCTION
                HIGHER_ORDER_FUNCTION2))); // 添加高阶函数HIGHER_ORDER_FUNCTION2
  }

  /** Adds a library set. */
  // 添加一个库集合，用于扩展操作符表以包含指定SQL库的操作符
  public MockSqlOperatorTable plus(Iterable<SqlLibrary> librarySet) { // 定义实例方法plus，接受一个SqlLibrary可迭代对象，返回扩展后的MockSqlOperatorTable
    final SqlOperatorTable parentTable = Iterables.getOnlyElement(tableList); // 从tableList中获取唯一的父操作符表
    return new MockSqlOperatorTable( // 创建并返回新的MockSqlOperatorTable实例
        SqlOperatorTables.chain(parentTable, // 将父操作符表与新库的操作符表链式连接
            SqlLibraryOperatorTableFactory.INSTANCE // 获取SqlLibraryOperatorTableFactory的实例
                .getOperatorTable(librarySet))); // 根据库集合获取对应的操作符表
  }

  /** "RAMP" user-defined table function. */
  // "RAMP"用户定义表函数，用于生成递增序列表，接受一个数值参数，返回从0到该数值的序列
  public static class RampFunction extends SqlFunction // 定义RampFunction类，继承自SqlFunction，实现SqlTableFunction接口
      implements SqlTableFunction { // 实现SqlTableFunction接口，表示这是一个表函数
    public RampFunction() { // 构造方法，初始化RAMP函数
      super("RAMP", // 调用父类构造方法，设置函数名为"RAMP"
          SqlKind.OTHER_FUNCTION, // 设置函数种类为其他函数
          ReturnTypes.CURSOR, // 设置返回类型为游标类型（表函数的返回类型）
          null, // 设置行类型推断为null（由getRowTypeInference方法提供）
          OperandTypes.NUMERIC, // 设置操作数类型为数值类型
          SqlFunctionCategory.USER_DEFINED_TABLE_FUNCTION); // 设置函数类别为用户定义表函数
    }

    @Override public SqlReturnTypeInference getRowTypeInference() { // 重写getRowTypeInference方法，提供返回行类型的推断逻辑
      return opBinding -> opBinding.getTypeFactory().builder() // 返回一个lambda表达式，根据操作符绑定推断行类型
          .add("I", SqlTypeName.INTEGER) // 添加一个名为"I"的整数类型字段
          .build(); // 构建并返回关系数据类型
    }
  }

  /** "DYNTYPE" user-defined table function. */
  // "DYNTYPE"用户定义表函数，用于测试动态类型推断，虽然类名是DynamicTypeFunction，但实际函数名设置为"RAMP"
  public static class DynamicTypeFunction extends SqlFunction // 定义DynamicTypeFunction类，继承自SqlFunction，实现SqlTableFunction接口
      implements SqlTableFunction { // 实现SqlTableFunction接口，表示这是一个表函数
    public DynamicTypeFunction() { // 构造方法，初始化动态类型函数
      super("RAMP", // 调用父类构造方法，设置函数名为"RAMP"（注意这里使用了与RampFunction相同的函数名）
          SqlKind.OTHER_FUNCTION, // 设置函数种类为其他函数
          ReturnTypes.CURSOR, // 设置返回类型为游标类型
          null, // 设置行类型推断为null
          OperandTypes.NUMERIC, // 设置操作数类型为数值类型
          SqlFunctionCategory.USER_DEFINED_TABLE_FUNCTION); // 设置函数类别为用户定义表函数
    }

    @Override public SqlReturnTypeInference getRowTypeInference() { // 重写getRowTypeInference方法
      return opBinding -> opBinding.getTypeFactory().builder() // 返回lambda表达式，推断行类型
          .add("I", SqlTypeName.INTEGER) // 添加整数类型字段"I"
          .build(); // 构建返回类型
    }
  }

  /** Not valid as a table function, even though it returns CURSOR, because
   * it does not implement {@link SqlTableFunction}. */
  // 不是有效的表函数，即使它返回CURSOR类型，因为它没有实现SqlTableFunction接口，用于测试表函数验证逻辑
  public static class NotATableFunction extends SqlFunction { // 定义NotATableFunction类，继承自SqlFunction，但不实现SqlTableFunction接口
    public NotATableFunction() { // 构造方法
      super("BAD_RAMP", // 调用父类构造方法，设置函数名为"BAD_RAMP"
          SqlKind.OTHER_FUNCTION, // 设置函数种类为其他函数
          ReturnTypes.CURSOR, // 设置返回类型为游标类型（但这不是表函数）
          null, // 设置行类型推断为null
          OperandTypes.NUMERIC, // 设置操作数类型为数值类型
          SqlFunctionCategory.USER_DEFINED_FUNCTION); // 设置函数类别为用户定义函数（不是表函数）
    }
  }

  /** Another bad table function: declares itself as a table function but does
   * not return CURSOR. */
  // 另一个错误的表函数：声明自己是表函数但不返回CURSOR类型，用于测试表函数验证逻辑
  public static class BadTableFunction extends SqlFunction // 定义BadTableFunction类，继承自SqlFunction，实现SqlTableFunction接口
      implements SqlTableFunction { // 实现SqlTableFunction接口，声明为表函数
    public BadTableFunction() { // 构造方法
      super("BAD_TABLE_FUNCTION", // 调用父类构造方法，设置函数名为"BAD_TABLE_FUNCTION"
          SqlKind.OTHER_FUNCTION, // 设置函数种类为其他函数
          null, // 设置返回类型为null（这是错误的，表函数应该返回CURSOR）
          null, // 设置行类型推断为null
          OperandTypes.NUMERIC, // 设置操作数类型为数值类型
          SqlFunctionCategory.USER_DEFINED_TABLE_FUNCTION); // 设置函数类别为用户定义表函数
    }

    @Override public RelDataType inferReturnType(SqlOperatorBinding opBinding) { // 重写inferReturnType方法，推断返回类型
      // This is wrong. A table function should return CURSOR.
      // 这是错误的。表函数应该返回CURSOR类型。
      return opBinding.getTypeFactory().builder() // 返回关系数据类型构建器
          .add("I", SqlTypeName.INTEGER) // 添加整数类型字段
          .build(); // 构建返回类型（不是CURSOR，这是错误的实现）
    }

    @Override public SqlReturnTypeInference getRowTypeInference() { // 重写getRowTypeInference方法
      return this::inferReturnType; // 返回方法引用，指向inferReturnType方法
    }
  }

  /** "DEDUP" user-defined table function. */
  // "DEDUP"用户定义表函数，用于去重操作，接受可变参数，返回去重后的结果
  public static class DedupFunction extends SqlFunction // 定义DedupFunction类，继承自SqlFunction，实现SqlTableFunction接口
      implements SqlTableFunction { // 实现SqlTableFunction接口，表示这是一个表函数
    public DedupFunction() { // 构造方法，初始化DEDUP函数
      super("DEDUP", // 调用父类构造方法，设置函数名为"DEDUP"
          SqlKind.OTHER_FUNCTION, // 设置函数种类为其他函数
          ReturnTypes.CURSOR, // 设置返回类型为游标类型
          null, // 设置行类型推断为null
          OperandTypes.VARIADIC, // 设置操作数类型为可变参数
          SqlFunctionCategory.USER_DEFINED_TABLE_FUNCTION); // 设置函数类别为用户定义表函数
    }

    @Override public SqlReturnTypeInference getRowTypeInference() { // 重写getRowTypeInference方法
      return opBinding -> opBinding.getTypeFactory().builder() // 返回lambda表达式，推断行类型
          .add("NAME", SqlTypeName.VARCHAR, 1024) // 添加名为"NAME"的VARCHAR类型字段，最大长度1024
          .build(); // 构建并返回关系数据类型
    }
  }

  /** "TFRT" user-defined table function. */
  // "TFRT"用户定义表函数，用于测试TableFunctionReturnTypeInference类，展示如何使用表函数返回类型推断
  public static class TableFunctionReturnTableFunction extends SqlFunction // 定义TableFunctionReturnTableFunction类，继承自SqlFunction，实现SqlTableFunction接口
      implements SqlTableFunction { // 实现SqlTableFunction接口，表示这是一个表函数
    TableFunctionReturnTypeInference inference; // 成员变量，存储表函数返回类型推断对象

    public TableFunctionReturnTableFunction() { // 构造方法，初始化TFRT函数
      super("TFRT", // 调用父类构造方法，设置函数名为"TFRT"
          SqlKind.OTHER_FUNCTION, // 设置函数种类为其他函数
          null, // 设置返回类型为null（由inferReturnType方法动态设置）
          null, // 设置行类型推断为null
          OperandTypes.VARIADIC, // 设置操作数类型为可变参数
          SqlFunctionCategory.USER_DEFINED_FUNCTION); // 设置函数类别为用户定义函数
    }

    @Override public RelDataType inferReturnType(SqlOperatorBinding opBinding) { // 重写inferReturnType方法，推断返回类型
      inference = // 创建并初始化TableFunctionReturnTypeInference对象
          new TableFunctionReturnTypeInference(factory -> factory.builder() // 使用lambda表达式创建类型工厂
              .add("NAME", SqlTypeName.CURSOR) // 添加名为"NAME"的CURSOR类型字段
              .build(), // 构建关系数据类型
          Lists.newArrayList("NAME"), // 传递参数名称列表
          true); // 设置是否需要推断
      inference.inferReturnType(opBinding); // 调用inferReturnType方法进行类型推断
      return opBinding.getTypeFactory().createSqlType(SqlTypeName.CURSOR); // 返回CURSOR类型
    }

    @Override public @Nullable SqlReturnTypeInference getReturnTypeInference() { // 重写getReturnTypeInference方法
      return inference; // 返回存储的推断对象
    }

    @Override public SqlReturnTypeInference getRowTypeInference() { // 重写getRowTypeInference方法
      return inference; // 返回存储的推断对象
    }
  }

  /** "Score" user-defined table function. First parameter is input table
   * with row semantics. */
  // "Score"用户定义表函数，第一个参数是具有行语义的输入表，用于对表中的数据进行评分
  public static class ScoreTableFunction extends SqlFunction // 定义ScoreTableFunction类，继承自SqlFunction，实现SqlTableFunction接口
      implements SqlTableFunction { // 实现SqlTableFunction接口，表示这是一个表函数

    private final Map<Integer, TableCharacteristic> tableParams = // 成员变量，存储表参数特征映射
        ImmutableMap.of( // 使用不可变映射
            0, // 第一个参数（索引0）
            TableCharacteristic // 创建表特征
                .builder(TableCharacteristic.Semantics.ROW) // 设置语义为行语义（保留重复行）
                .passColumnsThrough().build()); // 设置透传列（保留输入表的所有列）

    public ScoreTableFunction() { // 构造方法，初始化SCORE函数
      super("SCORE", // 调用父类构造方法，设置函数名为"SCORE"
          SqlKind.OTHER_FUNCTION, // 设置函数种类为其他函数
          ReturnTypes.CURSOR, // 设置返回类型为游标类型
          null, // 设置行类型推断为null
          new OperandMetadataImpl(), // 使用自定义的操作数元数据实现
          SqlFunctionCategory.USER_DEFINED_TABLE_FUNCTION); // 设置函数类别为用户定义表函数
    }

    private static RelDataType inferRowType(SqlOperatorBinding opBinding) { // 私有静态方法，推断行类型
      final RelDataTypeFactory typeFactory = opBinding.getTypeFactory(); // 获取类型工厂
      final RelDataType inputRowType = opBinding.getOperandType(0); // 获取第一个操作数的类型（输入表类型）
      final RelDataType bigintType = // 创建BIGINT类型
          typeFactory.createSqlType(SqlTypeName.BIGINT);
      return typeFactory.builder() // 构建返回类型
          .kind(inputRowType.getStructKind()) // 设置结构种类与输入表相同
          .addAll(inputRowType.getFieldList()) // 添加输入表的所有字段
          .add("SCORE_VALUE", bigintType).nullable(true) // 添加SCORE_VALUE字段，类型为BIGINT，可为空
          .build(); // 构建并返回关系数据类型
    }

    @Override public SqlReturnTypeInference getRowTypeInference() { // 重写getRowTypeInference方法
      return ScoreTableFunction::inferRowType; // 返回方法引用，指向inferRowType静态方法
    }

    @Override public TableCharacteristic tableCharacteristic(int ordinal) { // 重写tableCharacteristic方法，获取指定位置参数的表特征
      return tableParams.get(ordinal); // 从映射中获取指定序号的表特征
    }

    @Override public boolean argumentMustBeScalar(int ordinal) { // 重写argumentMustBeScalar方法，判断参数是否必须是标量
      return !tableParams.containsKey(ordinal); // 如果参数不在tableParams中，则必须是标量
    }

    /** Operand type checker for {@link ScoreTableFunction}. */
    // ScoreTableFunction的操作数类型检查器，用于验证操作数类型和数量
    private static class OperandMetadataImpl implements SqlOperandMetadata { // 定义内部类OperandMetadataImpl，实现SqlOperandMetadata接口

      @Override public List<RelDataType> paramTypes(RelDataTypeFactory typeFactory) { // 重写paramTypes方法，返回参数类型列表
        return ImmutableList.of(typeFactory.createSqlType(SqlTypeName.ANY)); // 返回包含ANY类型的不可变列表
      }

      @Override public List<String> paramNames() { // 重写paramNames方法，返回参数名称列表
        return ImmutableList.of("DATA"); // 返回包含"DATA"的不可变列表
      }

      @Override public boolean checkOperandTypes( // 重写checkOperandTypes方法，检查操作数类型
          SqlCallBinding callBinding, boolean throwOnFailure) { // 接受调用绑定和是否抛出异常标志
        return true; // 总是返回true，表示操作数类型检查通过
      }

      @Override public SqlOperandCountRange getOperandCountRange() { // 重写getOperandCountRange方法，获取操作数数量范围
        return SqlOperandCountRanges.of(1); // 返回操作数数量为1的范围
      }

      @Override public String getAllowedSignatures(SqlOperator op, String opName) { // 重写getAllowedSignatures方法，获取允许的签名
        return "Score(TABLE table_name)"; // 返回函数签名字符串
      }
    }
  }

  /** "TopN" user-defined table function. First parameter is input table
   * with set semantics. */
  // "TopN"用户定义表函数，第一个参数是具有集合语义的输入表（自动去重），用于获取前N条记录
  public static class TopNTableFunction extends SqlFunction // 定义TopNTableFunction类，继承自SqlFunction，实现SqlTableFunction接口
      implements SqlTableFunction { // 实现SqlTableFunction接口，表示这是一个表函数

    private final Map<Integer, TableCharacteristic> tableParams = // 成员变量，存储表参数特征映射
        ImmutableMap.of( // 使用不可变映射
            0, // 第一个参数（索引0）
            TableCharacteristic // 创建表特征
                .builder(TableCharacteristic.Semantics.SET) // 设置语义为集合语义（自动去重）
                .passColumnsThrough() // 设置透传列（保留输入表的所有列）
                .pruneIfEmpty() // 设置如果表为空则修剪
                .build()); // 构建表特征

    public TopNTableFunction() { // 构造方法，初始化TOPN函数
      super("TOPN", // 调用父类构造方法，设置函数名为"TOPN"
          SqlKind.OTHER_FUNCTION, // 设置函数种类为其他函数
          ReturnTypes.CURSOR, // 设置返回类型为游标类型
          null, // 设置行类型推断为null
          new OperandMetadataImpl(), // 使用自定义的操作数元数据实现
          SqlFunctionCategory.USER_DEFINED_TABLE_FUNCTION); // 设置函数类别为用户定义表函数
    }

    private static RelDataType inferRowType(SqlOperatorBinding opBinding) { // 私有静态方法，推断行类型
      final RelDataTypeFactory typeFactory = opBinding.getTypeFactory(); // 获取类型工厂
      final RelDataType inputRowType = opBinding.getOperandType(0); // 获取第一个操作数的类型（输入表类型）
      final RelDataType bigintType = // 创建BIGINT类型
          typeFactory.createSqlType(SqlTypeName.BIGINT);
      return typeFactory.builder() // 构建返回类型
          .kind(inputRowType.getStructKind()) // 设置结构种类与输入表相同
          .addAll(inputRowType.getFieldList()) // 添加输入表的所有字段
          .add("RANK_NUMBER", bigintType).nullable(true) // 添加RANK_NUMBER字段，类型为BIGINT，可为空
          .build(); // 构建并返回关系数据类型
    }

    @Override public SqlReturnTypeInference getRowTypeInference() { // 重写getRowTypeInference方法
      return TopNTableFunction::inferRowType; // 返回方法引用，指向inferRowType静态方法
    }

    @Override public TableCharacteristic tableCharacteristic(int ordinal) { // 重写tableCharacteristic方法，获取指定位置参数的表特征
      return tableParams.get(ordinal); // 从映射中获取指定序号的表特征
    }

    @Override public boolean argumentMustBeScalar(int ordinal) { // 重写argumentMustBeScalar方法，判断参数是否必须是标量
      return !tableParams.containsKey(ordinal); // 如果参数不在tableParams中，则必须是标量
    }

    /** Operand type checker for {@link TopNTableFunction}. */
    // TopNTableFunction的操作数类型检查器，用于验证操作数类型和数量
    private static class OperandMetadataImpl implements SqlOperandMetadata { // 定义内部类OperandMetadataImpl，实现SqlOperandMetadata接口

      @Override public List<RelDataType> paramTypes(RelDataTypeFactory typeFactory) { // 重写paramTypes方法，返回参数类型列表
        return ImmutableList.of( // 返回不可变列表
            typeFactory.createSqlType(SqlTypeName.ANY), // 第一个参数为ANY类型（表）
            typeFactory.createSqlType(SqlTypeName.INTEGER)); // 第二个参数为INTEGER类型
      }

      @Override public List<String> paramNames() { // 重写paramNames方法，返回参数名称列表
        return ImmutableList.of("DATA", "COL"); // 返回包含"DATA"和"COL"的不可变列表
      }

      @Override public boolean checkOperandTypes( // 重写checkOperandTypes方法，检查操作数类型
          SqlCallBinding callBinding, boolean throwOnFailure) { // 接受调用绑定和是否抛出异常标志
        final SqlNode operand1 = callBinding.operand(1); // 获取第二个操作数（索引1）
        final SqlValidator validator = callBinding.getValidator(); // 获取SQL验证器
        final RelDataType type = validator.getValidatedNodeType(operand1); // 获取操作数的验证后类型
        if (!SqlTypeUtil.isIntType(type)) { // 检查类型是否为整数类型
          if (throwOnFailure) { // 如果需要抛出异常
            throw callBinding.newValidationSignatureError(); // 抛出验证签名错误
          } else { // 如果不需要抛出异常
            return false; // 返回false表示类型检查失败
          }
        } else { // 如果是整数类型
          return true; // 返回true表示类型检查通过
        }
      }

      @Override public SqlOperandCountRange getOperandCountRange() { // 重写getOperandCountRange方法，获取操作数数量范围
        return SqlOperandCountRanges.of(2); // 返回操作数数量为2的范围
      }

      @Override public String getAllowedSignatures(SqlOperator op, String opName) { // 重写getAllowedSignatures方法，获取允许的签名
        return "TopN(TABLE table_name, BIGINT rows)"; // 返回函数签名字符串
      }
    }
  }

  /** Similarity performs an analysis on two data sets, which are both tables
   * of two columns, treated as the x and y axes of a graph. It has two input
   * tables with set semantics. */
  // Similarity对两个数据集执行分析，这两个数据集都是两列表，被视为图形的x和y轴。它有两个具有集合语义的输入表，用于计算相似度
  public static class SimilarlityTableFunction extends SqlFunction // 定义SimilarlityTableFunction类，继承自SqlFunction，实现SqlTableFunction接口
      implements SqlTableFunction { // 实现SqlTableFunction接口，表示这是一个表函数

    private final Map<Integer, TableCharacteristic> tableParams = // 成员变量，存储表参数特征映射
        ImmutableMap.of( // 使用不可变映射
            0, // 第一个参数（索引0）
            TableCharacteristic // 创建表特征
                .builder(TableCharacteristic.Semantics.SET) // 设置语义为集合语义（自动去重）
                .build(), // 构建表特征
            1, // 第二个参数（索引1）
            TableCharacteristic // 创建表特征
                .builder(TableCharacteristic.Semantics.SET) // 设置语义为集合语义（自动去重）
                .build()); // 构建表特征

    public SimilarlityTableFunction() { // 构造方法，初始化SIMILARLITY函数
      super("SIMILARLITY", // 调用父类构造方法，设置函数名为"SIMILARLITY"（注意拼写：SIMILARLITY）
          SqlKind.OTHER_FUNCTION, // 设置函数种类为其他函数
          ReturnTypes.CURSOR, // 设置返回类型为游标类型
          null, // 设置行类型推断为null
          new OperandMetadataImpl(), // 使用自定义的操作数元数据实现
          SqlFunctionCategory.USER_DEFINED_TABLE_FUNCTION); // 设置函数类别为用户定义表函数
    }

    @Override public SqlReturnTypeInference getRowTypeInference() { // 重写getRowTypeInference方法
      return opBinding -> opBinding.getTypeFactory().builder() // 返回lambda表达式，推断行类型
          .add("VAL", SqlTypeName.DECIMAL, 5, 2) // 添加名为"VAL"的DECIMAL类型字段，精度5，小数位数2
          .build(); // 构建并返回关系数据类型
    }

    @Override public TableCharacteristic tableCharacteristic(int ordinal) { // 重写tableCharacteristic方法，获取指定位置参数的表特征
      return tableParams.get(ordinal); // 从映射中获取指定序号的表特征
    }

    @Override public boolean argumentMustBeScalar(int ordinal) { // 重写argumentMustBeScalar方法，判断参数是否必须是标量
      return !tableParams.containsKey(ordinal); // 如果参数不在tableParams中，则必须是标量
    }


    /** Operand type checker for {@link TopNTableFunction}. */
    // TopNTableFunction的操作数类型检查器（注释中写的是TopNTableFunction，但实际应该是SimilarlityTableFunction）
    private static class OperandMetadataImpl implements SqlOperandMetadata { // 定义内部类OperandMetadataImpl，实现SqlOperandMetadata接口

      @Override public List<RelDataType> paramTypes(RelDataTypeFactory typeFactory) { // 重写paramTypes方法，返回参数类型列表
        return ImmutableList.of( // 返回不可变列表
            typeFactory.createSqlType(SqlTypeName.ANY), // 第一个参数为ANY类型（表）
            typeFactory.createSqlType(SqlTypeName.ANY)); // 第二个参数为ANY类型（表）
      }

      @Override public List<String> paramNames() { // 重写paramNames方法，返回参数名称列表
        return ImmutableList.of("LTABLE", "RTABLE"); // 返回包含"LTABLE"和"RTABLE"的不可变列表
      }

      @Override public boolean checkOperandTypes( // 重写checkOperandTypes方法，检查操作数类型
          SqlCallBinding callBinding, boolean throwOnFailure) { // 接受调用绑定和是否抛出异常标志
        return true; // 总是返回true，表示操作数类型检查通过
      }

      @Override public SqlOperandCountRange getOperandCountRange() { // 重写getOperandCountRange方法，获取操作数数量范围
        return SqlOperandCountRanges.of(2); // 返回操作数数量为2的范围
      }

      @Override public String getAllowedSignatures(SqlOperator op, String opName) { // 重写getAllowedSignatures方法，获取允许的签名
        return "SIMILARITY(TABLE table_name, TABLE table_name)"; // 返回函数签名字符串
      }
    }
  }

  /** Invalid user-defined table function with multiple input tables with
   * row semantics. */
  // 无效的用户定义表函数，具有多个具有行语义的输入表，用于测试多个表参数的场景
  public static class InvalidTableFunction extends SqlFunction // 定义InvalidTableFunction类，继承自SqlFunction，实现SqlTableFunction接口
      implements SqlTableFunction { // 实现SqlTableFunction接口，表示这是一个表函数

    private final Map<Integer, TableCharacteristic> tableParams = // 成员变量，存储表参数特征映射
        ImmutableMap.of( // 使用不可变映射
            0, // 第一个参数（索引0）
            TableCharacteristic // 创建表特征
                .builder(TableCharacteristic.Semantics.ROW) // 设置语义为行语义（保留重复行）
                .passColumnsThrough() // 设置透传列（保留输入表的所有列）
                .build(), // 构建表特征
            1, // 第二个参数（索引1）
            TableCharacteristic // 创建表特征
                .builder(TableCharacteristic.Semantics.ROW) // 设置语义为行语义（保留重复行）
                .passColumnsThrough() // 设置透传列（保留输入表的所有列）
                .build()); // 构建表特征

    public InvalidTableFunction() { // 构造方法，初始化INVALID函数
      super("INVALID", // 调用父类构造方法，设置函数名为"INVALID"
          SqlKind.OTHER_FUNCTION, // 设置函数种类为其他函数
          ReturnTypes.CURSOR, // 设置返回类型为游标类型
          null, // 设置行类型推断为null
          OperandTypes.VARIADIC, // 设置操作数类型为可变参数
          SqlFunctionCategory.USER_DEFINED_TABLE_FUNCTION); // 设置函数类别为用户定义表函数
    }

    @Override public SqlReturnTypeInference getRowTypeInference() { // 重写getRowTypeInference方法
      return opBinding -> opBinding.getTypeFactory().builder() // 返回lambda表达式，推断行类型
          .add("NAME", SqlTypeName.VARCHAR, 1024) // 添加名为"NAME"的VARCHAR类型字段，最大长度1024
          .build(); // 构建并返回关系数据类型
    }

    @Override public TableCharacteristic tableCharacteristic(int ordinal) { // 重写tableCharacteristic方法，获取指定位置参数的表特征
      return tableParams.get(ordinal); // 从映射中获取指定序号的表特征
    }

    @Override public boolean argumentMustBeScalar(int ordinal) { // 重写argumentMustBeScalar方法，判断参数是否必须是标量
      return !tableParams.containsKey(ordinal); // 如果参数不在tableParams中，则必须是标量
    }
  }

  /** "MYFUN" user-defined scalar function. */
  // "MYFUN"用户定义标量函数，接受数值参数，返回BIGINT类型的结果
  public static class MyFunction extends SqlFunction { // 定义MyFunction类，继承自SqlFunction，表示这是一个标量函数
    public MyFunction() { // 构造方法，初始化MYFUN函数
      super("MYFUN", // 调用父类构造方法，设置函数名为"MYFUN"
          new SqlIdentifier("MYFUN", SqlParserPos.ZERO), // 使用SqlIdentifier创建标识符，位置为零
          SqlKind.OTHER_FUNCTION, // 设置函数种类为其他函数
          null, // 设置返回类型推断为null（由inferReturnType方法提供）
          null, // 设置行类型推断为null
          OperandTypes.NUMERIC, // 设置操作数类型为数值类型
          SqlFunctionCategory.USER_DEFINED_FUNCTION); // 设置函数类别为用户定义函数
    }

    @Override public RelDataType inferReturnType(SqlOperatorBinding opBinding) { // 重写inferReturnType方法，推断返回类型
      final RelDataTypeFactory typeFactory = // 获取类型工厂
          opBinding.getTypeFactory();
      return typeFactory.createSqlType(SqlTypeName.BIGINT); // 返回BIGINT类型
    }
  }

  /** "MYAGGFUNC" user-defined aggregate function. This agg function accept one or more arguments
   * in order to reproduce the throws of CALCITE-3929. */
  // "MYAGGFUNC"用户定义聚合函数，接受一个或多个参数，用于复现CALCITE-3929问题
  public static class MyAggFunc extends SqlAggFunction { // 定义MyAggFunc类，继承自SqlAggFunction，表示这是一个聚合函数
    public MyAggFunc() { // 构造方法，初始化myAggFunc函数
      super("myAggFunc", null, SqlKind.OTHER_FUNCTION, ReturnTypes.BIGINT, null, // 调用父类构造方法，设置函数名为"myAggFunc"，种类为其他函数，返回类型为BIGINT
          OperandTypes.ONE_OR_MORE, SqlFunctionCategory.USER_DEFINED_FUNCTION, false, false, // 设置操作数为一个或多个，函数类别为用户定义函数，不允许null，不允许distinct
          Optionality.FORBIDDEN); // 设置可选性为禁止（参数必须提供）
    }
  }

  /**
   * "SPLIT" user-defined function. This function return array type
   * in order to reproduce the throws of CALCITE-4062.
   */
  // "SPLIT"用户定义函数，返回数组类型，用于复现CALCITE-4062问题
  public static class SplitFunction extends SqlFunction { // 定义SplitFunction类，继承自SqlFunction，表示这是一个标量函数

    public SplitFunction() { // 构造方法，初始化SPLIT函数
      super("SPLIT", new SqlIdentifier("SPLIT", SqlParserPos.ZERO), // 调用父类构造方法，设置函数名为"SPLIT"
          SqlKind.OTHER_FUNCTION, null, null, // 设置函数种类为其他函数，返回类型和行类型推断为null
          OperandTypes.family(SqlTypeFamily.STRING, SqlTypeFamily.STRING), // 设置操作数类型为两个字符串类型
          SqlFunctionCategory.USER_DEFINED_FUNCTION); // 设置函数类别为用户定义函数
    }

    @Override public RelDataType inferReturnType(SqlOperatorBinding opBinding) { // 重写inferReturnType方法，推断返回类型
      final RelDataTypeFactory typeFactory = // 获取类型工厂
          opBinding.getTypeFactory();
      return typeFactory.createArrayType(typeFactory.createSqlType(SqlTypeName.VARCHAR), -1); // 返回VARCHAR类型的数组，-1表示未知长度
    }

  }

  /**
   * "MAP" user-defined function. This function return map type
   * in order to reproduce the throws of CALCITE-4895.
   */
  // "MAP"用户定义函数，返回MAP类型，用于复现CALCITE-4895问题
  public static class MapFunction extends SqlFunction { // 定义MapFunction类，继承自SqlFunction，表示这是一个标量函数

    public MapFunction() { // 构造方法，初始化MAP函数
      super("MAP", new SqlIdentifier("MAP", SqlParserPos.ZERO), // 调用父类构造方法，设置函数名为"MAP"
          SqlKind.OTHER_FUNCTION, null, null, // 设置函数种类为其他函数，返回类型和行类型推断为null
          OperandTypes.family(SqlTypeFamily.STRING, SqlTypeFamily.STRING), // 设置操作数类型为两个字符串类型
          SqlFunctionCategory.USER_DEFINED_FUNCTION); // 设置函数类别为用户定义函数
    }

    @Override public RelDataType inferReturnType(SqlOperatorBinding opBinding) { // 重写inferReturnType方法，推断返回类型
      final RelDataTypeFactory typeFactory = // 获取类型工厂
          opBinding.getTypeFactory();
      return typeFactory.createMapType(typeFactory.createSqlType(SqlTypeName.VARCHAR), // 返回MAP类型，键为VARCHAR类型
          typeFactory.createSqlType(SqlTypeName.VARCHAR)); // 值也为VARCHAR类型
    }

  }

  /** "MYAGG" user-defined aggregate function. This agg function accept two numeric arguments
   * in order to reproduce the throws of CALCITE-2744. */
  // "MYAGG"用户定义聚合函数，接受两个数值参数，用于复现CALCITE-2744问题
  public static class MyAvgAggFunction extends SqlAggFunction { // 定义MyAvgAggFunction类，继承自SqlAggFunction，表示这是一个聚合函数
    public MyAvgAggFunction() { // 构造方法，初始化MYAGG函数
      super("MYAGG", null, SqlKind.AVG, ReturnTypes.AVG_AGG_FUNCTION, // 调用父类构造方法，设置函数名为"MYAGG"，种类为AVG，返回类型为AVG聚合函数类型
          null, OperandTypes.family(SqlTypeFamily.NUMERIC, SqlTypeFamily.NUMERIC), // 设置操作数为两个数值类型
          SqlFunctionCategory.NUMERIC, false, false, Optionality.FORBIDDEN); // 设置函数类别为数值函数，不允许null，不允许distinct，参数必须提供
    }

    @Override public boolean isDeterministic() { // 重写isDeterministic方法，判断函数是否是确定性的
      return false; // 返回false，表示这个函数不是确定性的（相同输入可能产生不同输出）
    }
  }

  /** "ROW_FUNC" user-defined table function whose return type is
   * row type with nullable and non-nullable fields. */
  // "ROW_FUNC"用户定义表函数，返回类型是包含可空和非空字段的行类型
  public static class RowFunction extends SqlFunction // 定义RowFunction类，继承自SqlFunction，实现SqlTableFunction接口
      implements SqlTableFunction { // 实现SqlTableFunction接口，表示这是一个表函数
    RowFunction() { // 构造方法，初始化ROW_FUNC函数
      super("ROW_FUNC", SqlKind.OTHER_FUNCTION, ReturnTypes.CURSOR, null, // 调用父类构造方法，设置函数名为"ROW_FUNC"，种类为其他函数，返回类型为游标类型
          OperandTypes.NILADIC, SqlFunctionCategory.USER_DEFINED_TABLE_FUNCTION); // 设置操作数为无参数，函数类别为用户定义表函数
    }

    private static RelDataType inferRowType(SqlOperatorBinding opBinding) { // 私有静态方法，推断行类型
      final RelDataTypeFactory typeFactory = opBinding.getTypeFactory(); // 获取类型工厂
      final RelDataType bigintType = // 创建BIGINT类型
          typeFactory.createSqlType(SqlTypeName.BIGINT);
      return typeFactory.builder() // 构建返回类型
          .add("NOT_NULL_FIELD", bigintType) // 添加NOT_NULL_FIELD字段，类型为BIGINT，不可为空
          .add("NULLABLE_FIELD", bigintType).nullable(true) // 添加NULLABLE_FIELD字段，类型为BIGINT，可为空
          .build(); // 构建并返回关系数据类型
    }

    @Override public SqlReturnTypeInference getRowTypeInference() { // 重写getRowTypeInference方法
      return RowFunction::inferRowType; // 返回方法引用，指向inferRowType静态方法
    }
  }

  /** "STRUCTURED_FUNC" user-defined function whose return type is structured type. */
  // "STRUCTURED_FUNC"用户定义函数，返回类型是结构化类型（包含多个字段）
  public static class StructuredFunction extends SqlFunction { // 定义StructuredFunction类，继承自SqlFunction，表示这是一个标量函数
    StructuredFunction() { // 构造方法，初始化STRUCTURED_FUNC函数
      super("STRUCTURED_FUNC", // 调用父类构造方法，设置函数名为"STRUCTURED_FUNC"
          new SqlIdentifier("STRUCTURED_FUNC", SqlParserPos.ZERO), // 使用SqlIdentifier创建标识符
          SqlKind.OTHER_FUNCTION, null, null, OperandTypes.NILADIC, // 设置函数种类为其他函数，返回类型和行类型推断为null，操作数为无参数
          SqlFunctionCategory.USER_DEFINED_FUNCTION); // 设置函数类别为用户定义函数
    }

    @Override public RelDataType inferReturnType(SqlOperatorBinding opBinding) { // 重写inferReturnType方法，推断返回类型
      final RelDataTypeFactory typeFactory = opBinding.getTypeFactory(); // 获取类型工厂
      final RelDataType bigintType = // 创建BIGINT类型
          typeFactory.createSqlType(SqlTypeName.BIGINT);
      final RelDataType varcharType = // 创建VARCHAR类型，长度为20
          typeFactory.createSqlType(SqlTypeName.VARCHAR, 20);
      return typeFactory.builder() // 构建返回类型
          .add("F0", bigintType) // 添加F0字段，类型为BIGINT
          .add("F1", varcharType) // 添加F1字段，类型为VARCHAR(20)
          .build(); // 构建并返回关系数据类型
    }
  }

  /** "COMPOSITE" user-defined scalar function. */
  // "COMPOSITE"用户定义标量函数，接受可变参数，支持1个或2个或更多参数的组合
  public static class CompositeFunction extends SqlFunction { // 定义CompositeFunction类，继承自SqlFunction，表示这是一个标量函数
    public CompositeFunction() { // 构造方法，初始化COMPOSITE函数
      super("COMPOSITE", // 调用父类构造方法，设置函数名为"COMPOSITE"
          new SqlIdentifier("COMPOSITE", SqlParserPos.ZERO), // 使用SqlIdentifier创建标识符
          SqlKind.OTHER_FUNCTION, // 设置函数种类为其他函数
          null, // 设置返回类型推断为null（由inferReturnType方法提供）
          null, // 设置行类型推断为null
          OperandTypes.variadic(SqlOperandCountRanges.from(1)) // 设置操作数为可变参数，从1个开始
              .or(OperandTypes.variadic(SqlOperandCountRanges.from(2))), // 或者从2个开始的可变参数（使用or逻辑组合）
          SqlFunctionCategory.USER_DEFINED_FUNCTION); // 设置函数类别为用户定义函数
    }

    @Override public RelDataType inferReturnType(SqlOperatorBinding opBinding) { // 重写inferReturnType方法，推断返回类型
      final RelDataTypeFactory typeFactory = // 获取类型工厂
          opBinding.getTypeFactory();
      return typeFactory.createSqlType(SqlTypeName.BIGINT); // 返回BIGINT类型
    }
  }

  /**
   * "COMPARE_STRINGS_OR_NUMERIC_VALUES" is a user-defined function whose arguments can be either
   * two strings or two numeric values of the same type.
   */
  // "COMPARE_STRINGS_OR_NUMERIC_VALUES"是用户定义函数，参数可以是两个字符串或两个相同类型的数值
  public static class CompareStringsOrNumericValues extends SqlFunction { // 定义CompareStringsOrNumericValues类，继承自SqlFunction
    public CompareStringsOrNumericValues() { // 构造方法，初始化COMPARE_STRINGS_OR_NUMERIC_VALUES函数
      super("COMPARE_STRINGS_OR_NUMERIC_VALUES", // 调用父类构造方法，设置函数名
          new SqlIdentifier("COMPARE_STRINGS_OR_NUMERIC_VALUES", SqlParserPos.ZERO), // 使用SqlIdentifier创建标识符
          SqlKind.OTHER_FUNCTION, // 设置函数种类为其他函数
          null, // 设置返回类型推断为null
          null, // 设置行类型推断为null
          OperandTypes.STRING_SAME_SAME.or( // 设置操作数类型为：两个字符串且类型相同
              OperandTypes.NUMERIC_NUMERIC.and(OperandTypes.SAME_SAME)), // 或者两个数值且类型相同（使用or逻辑组合）
          SqlFunctionCategory.USER_DEFINED_FUNCTION); // 设置函数类别为用户定义函数
    }

    @Override public RelDataType inferReturnType(SqlOperatorBinding opBinding) { // 重写inferReturnType方法，推断返回类型
      return opBinding.getOperandType(0); // 返回第一个操作数的类型
    }
  }

  private static final SqlFunction HIGHER_ORDER_FUNCTION = // 定义静态常量HIGHER_ORDER_FUNCTION，表示高阶函数
      SqlBasicFunction.create("HIGHER_ORDER_FUNCTION", // 创建名为"HIGHER_ORDER_FUNCTION"的基本SQL函数
          ReturnTypes.ARG0, // 设置返回类型为第一个参数的类型
          OperandTypes.sequence("HIGHER_ORDER_FUNCTION(INTEGER, FUNCTION(STRING, ANY) -> NUMERIC)", // 设置操作数序列签名
              OperandTypes.family(SqlTypeFamily.INTEGER), // 第一个参数为INTEGER类型
              OperandTypes.function( // 第二个参数为函数类型
                  SqlTypeFamily.NUMERIC, SqlTypeFamily.STRING, SqlTypeFamily.ANY)), // 函数返回NUMERIC，接受STRING和ANY参数
          SqlFunctionCategory.SYSTEM); // 设置函数类别为系统函数

  private static final SqlFunction HIGHER_ORDER_FUNCTION2 = // 定义静态常量HIGHER_ORDER_FUNCTION2，表示另一个高阶函数
      SqlBasicFunction.create("HIGHER_ORDER_FUNCTION2", // 创建名为"HIGHER_ORDER_FUNCTION2"的基本SQL函数
          ReturnTypes.ARG0, // 设置返回类型为第一个参数的类型
          OperandTypes.sequence("HIGHER_ORDER_FUNCTION(INTEGER, FUNCTION() -> NUMERIC)", // 设置操作数序列签名
              OperandTypes.family(SqlTypeFamily.INTEGER), // 第一个参数为INTEGER类型
              OperandTypes.function(SqlTypeFamily.NUMERIC)), // 第二个参数为函数类型，返回NUMERIC，无参数
          SqlFunctionCategory.SYSTEM); // 设置函数类别为系统函数
} // 类定义结束
