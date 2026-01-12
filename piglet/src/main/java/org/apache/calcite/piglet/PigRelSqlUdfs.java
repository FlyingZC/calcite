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
package org.apache.calcite.piglet; // 定义包名，该类属于org.apache.calcite.piglet包，用于Piglet相关功能实现

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂，用于创建和管理Java类型
import org.apache.calcite.linq4j.function.Functions; // 导入LINQ4J函数工具类，提供函数生成等辅助功能
import org.apache.calcite.rel.RelNode; // 导入关系表达式节点，表示关系代数操作
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型，表示Calcite中的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂，用于创建数据类型
import org.apache.calcite.rel.type.RelDataTypeFactoryImpl; // 导入关系数据类型工厂实现类
import org.apache.calcite.rel.type.RelDataTypeField; // 导入关系数据类型字段，表示结构类型的字段
import org.apache.calcite.rex.RexNode; // 导入行表达式节点，表示行级别的表达式
import org.apache.calcite.schema.ScalarFunction; // 导入标量函数接口，表示返回单个值的函数
import org.apache.calcite.schema.impl.ScalarFunctionImpl; // 导入标量函数实现类，用于创建标量函数
import org.apache.calcite.sql.SqlCallBinding; // 导入SQL调用绑定，提供SQL函数调用的上下文信息
import org.apache.calcite.sql.SqlOperandCountRange; // 导入SQL操作数计数范围，定义函数参数数量的范围
import org.apache.calcite.sql.SqlOperator; // 导入SQL操作符基类
import org.apache.calcite.sql.type.MultisetSqlType; // 导入多重集合SQL类型，表示MULTISET类型
import org.apache.calcite.sql.type.OperandTypes; // 导入操作数类型工具类，用于创建操作数元数据
import org.apache.calcite.sql.type.SqlOperandCountRanges; // 导入SQL操作数计数范围工具类
import org.apache.calcite.sql.type.SqlOperandMetadata; // 导入SQL操作数元数据接口，定义操作数的类型和数量信息
import org.apache.calcite.sql.type.SqlOperandTypeChecker; // 导入SQL操作数类型检查器接口
import org.apache.calcite.sql.type.SqlReturnTypeInference; // 导入SQL返回类型推断接口，用于推断函数的返回类型
import org.apache.calcite.sql.type.SqlTypeFamily; // 导入SQL类型族，表示SQL类型的分类（如数值型、字符串型等）
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举，定义所有SQL类型名称
import org.apache.calcite.sql.validate.SqlUserDefinedFunction; // 导入SQL用户定义函数类

import org.apache.pig.FuncSpec; // 导入Pig函数规范，描述Pig UDF的规范信息
import org.apache.pig.data.BagFactory; // 导入Pig数据包工厂，用于创建DataBag对象
import org.apache.pig.data.DataBag; // 导入Pig数据包，表示一个元组的集合
import org.apache.pig.data.Tuple; // 导入Pig元组，表示一行数据
import org.apache.pig.data.TupleFactory; // 导入Pig元组工厂，用于创建Tuple对象

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类

import java.lang.reflect.Method; // 导入反射方法类，用于获取方法信息
import java.math.BigDecimal; // 导入BigDecimal类，用于精确的十进制运算
import java.util.ArrayList; // 导入ArrayList动态数组类
import java.util.Arrays; // 导入Arrays数组工具类
import java.util.Collections; // 导入Collections集合工具类
import java.util.List; // 导入List接口

import static org.apache.calcite.piglet.PigTypes.TYPE_FACTORY; // 静态导入PigTypes中的类型工厂常量

/**
 * User-defined functions ({@link SqlUserDefinedFunction UDFs}) // 用户定义函数，用于Pig到关系表达式转换
 * needed for Pig-to-{@link RelNode} translation. // 这是Pig到关系表达式转换所需的用户定义函数集合
 * 
 * 本类提供了Pig Latin到SQL/RelNode转换过程中需要的各种用户定义函数(UDF)
 * 主要包括：
 * 1. PIG_TUPLE: 从关系操作数创建Pig Tuple（元组）
 * 2. PIG_BAG: 从关系操作数创建Pig DataBag（数据包）
 * 3. MULTISET_PROJECTION: 对多重集合进行投影操作，选择特定列
 * 
 * 这些函数在Piglet解析器将Pig Latin脚本转换为Calcite关系表达式时使用，
 * 用于处理Pig特有的数据结构（Tuple和Bag）与SQL关系模型之间的映射。
 */
public class PigRelSqlUdfs { // 定义PigRelSqlUdfs类，提供Pig到SQL转换所需的用户定义函数
  private PigRelSqlUdfs() { // 私有构造方法，防止实例化（工具类模式）
  } // 空构造方法体

  // Defines ScalarFunc from their implementations // 从实现定义标量函数
  private static final ScalarFunction PIG_TUPLE_FUNC = // 定义PIG_TUPLE_FUNC常量，用于创建Pig Tuple的标量函数
      ScalarFunctionImpl.create(PigRelSqlUdfs.class, "buildTuple"); // 通过反射创建标量函数，绑定到buildTuple方法
  private static final ScalarFunction PIG_BAG_FUNC = // 定义PIG_BAG_FUNC常量，用于创建Pig DataBag的标量函数
      ScalarFunctionImpl.create(PigRelSqlUdfs.class, "buildBag"); // 通过反射创建标量函数，绑定到buildBag方法
  private static final ScalarFunction MULTISET_PROJECTION_FUNC = // 定义MULTISET_PROJECTION_FUNC常量，用于多重集合投影的标量函数
      ScalarFunctionImpl.create(PigRelSqlUdfs.class, "projectMultiset"); // 通过反射创建标量函数，绑定到projectMultiset方法

  /**
   * Multiset projection projects a subset of columns from the component type // 多重集合投影操作：从多重集合的组件类型中投影列的子集
   * of a multiset type. The result is still a multiset but the component // 结果仍然是一个多重集合，但组件类型只包含原始组件类型的列子集
   * type only has a subset of columns of the original component type
   *
   * <p>For example, given a multiset type // 例如，给定一个多重集合类型
   * {@code M = [(A: int, B: double, C: varchar)]}, // M包含三个字段：A(int)、B(double)、C(varchar)
   * a projection // 进行投影操作
   * {@code MULTISET_PROJECTION(M, A, C)} // 投影A和C两个字段
   * gives a new multiset // 得到一个新的多重集合
   * {@code N = [(A: int, C: varchar)]}. // N只包含A和C两个字段
   * 
   * 这个函数用于在Pig到SQL转换过程中处理字段投影操作，
   * 因为Pig的数据模型与SQL的关系模型在表示方式上有所不同，
   * 需要通过这个函数来正确映射和转换。
   */
  static final SqlUserDefinedFunction MULTISET_PROJECTION = // 定义MULTISET_PROJECTION常量，多重集合投影用户定义函数
      new PigUserDefinedFunction("MULTISET_PROJECTION", // 创建Pig用户定义函数，函数名为MULTISET_PROJECTION
          multisetProjectionInfer(), multisetProjectionCheck(), // 设置返回类型推断和操作数类型检查器
          MULTISET_PROJECTION_FUNC); // 绑定到MULTISET_PROJECTION_FUNC实现

  /**
   * Creates a Pig Tuple from a list of relational operands. // 从关系操作数列表创建Pig Tuple（元组）
   *
   * @param operands Relational operands // 参数：关系操作数列表（RexNode对象）
   * @return Pig Tuple SqlUDF // 返回值：创建的Pig Tuple SQL用户定义函数
   * 
   * 该方法根据传入的关系操作数动态创建一个PIG_TUPLE UDF，
   * 每个操作数对应Tuple中的一个字段。这在Pig的GENERATE操作中常用，
   * 用于将关系表达式的结果转换为Pig Tuple格式。
   */
  static SqlUserDefinedFunction createPigTupleUDF(ImmutableList<RexNode> operands) { // 创建Pig Tuple用户定义函数的静态方法
    return new PigUserDefinedFunction("PIG_TUPLE", // 返回一个新的Pig用户定义函数，函数名为PIG_TUPLE
        infer(PigRelSqlUdfs.PIG_TUPLE_FUNC), // 使用infer方法推断返回类型
        OperandTypes.operandMetadata(getTypeFamilies(operands), // 创建操作数元数据，包含类型族信息
            typeFactory -> getRelDataTypes(operands), i -> "arg" + i, // 创建参数类型列表和参数名称列表
            i -> false), // 设置所有参数为非可选参数
        PigRelSqlUdfs.PIG_TUPLE_FUNC); // 绑定到PIG_TUPLE_FUNC实现
  }

  /**
   * Creates a Pig DataBag from a list of relational operands. // 从关系操作数列表创建Pig DataBag（数据包）
   *
   * @param operands Relational operands // 参数：关系操作数列表（RexNode对象）
   * @return Pig DataBag SqlUDF // 返回值：创建的Pig DataBag SQL用户定义函数
   * 
   * 该方法根据传入的关系操作数动态创建一个PIG_BAG UDF，
   * DataBag是Pig中用于存储多个Tuple的集合结构。这在Pig的GROUP、COGROUP等操作中使用，
   * 用于将多个关系表达式的结果转换为Pig DataBag格式。
   */
  static SqlUserDefinedFunction createPigBagUDF(ImmutableList<RexNode> operands) { // 创建Pig DataBag用户定义函数的静态方法
    final SqlOperandMetadata operandMetadata = // 创建操作数元数据对象
        OperandTypes.operandMetadata(getTypeFamilies(operands), // 从操作数获取类型族信息
            typeFactory -> getRelDataTypes(operands), i -> "arg" + i, // 创建参数类型列表和参数名称列表
            i -> false); // 设置所有参数为非可选参数
    return new PigUserDefinedFunction("PIG_BAG", // 返回一个新的Pig用户定义函数，函数名为PIG_BAG
        infer(PigRelSqlUdfs.PIG_BAG_FUNC), operandMetadata, // 使用infer方法推断返回类型，并设置操作数元数据
        PigRelSqlUdfs.PIG_BAG_FUNC); // 绑定到PIG_BAG_FUNC实现
  }

  /**
   * Creates a generic SqlUDF operator from a Pig UDF. // 从Pig UDF创建通用的SQL用户定义函数操作符
   *
   * @param udfName Name of the UDF // 参数：UDF的名称
   * @param method Method "exec" for implementing the UDF // 参数：实现UDF的exec方法
   * @param funcSpec Pig Funcspec // 参数：Pig函数规范，包含UDF的完整类名等信息
   * @param inputType Argument type for the input // 参数：输入参数的类型
   * @param returnType Function return data type // 参数：函数的返回数据类型
   * 
   * 该方法用于将Pig原生的用户定义函数包装成Calcite可以识别的SQL UDF，
   * 这样在Pig到SQL转换过程中可以调用Pig的UDF。这是实现Pig与SQL互操作性的关键。
   */
  static SqlUserDefinedFunction createGeneralPigUdf(String udfName, // 创建通用Pig UDF的静态方法
      Method method, FuncSpec funcSpec, RelDataType inputType, // 接收方法对象、函数规范、输入类型和返回类型
      RelDataType returnType) { // 返回类型参数
    final SqlOperandMetadata operandMetadata = // 创建操作数元数据对象
        OperandTypes.operandMetadata(ImmutableList.of(SqlTypeFamily.ANY), // 设置操作数类型为ANY（任意类型）
            typeFactory -> ImmutableList.of(inputType), i -> "arg" + i, // 创建参数类型列表和参数名称列表
            i -> false); // 设置所有参数为非可选参数
    return new PigUserDefinedFunction(udfName, opBinding -> returnType, // 返回一个新的Pig用户定义函数，使用lambda表达式返回固定类型
        operandMetadata, ScalarFunctionImpl.createUnsafe(method), funcSpec); // 设置操作数元数据、创建不安全的标量函数和Pig函数规范
  }

  /**
   * Returns a {@link SqlReturnTypeInference} for multiset projection operator. // 返回多重集合投影操作符的返回类型推断器
   * 
   * 该方法创建一个SqlReturnTypeInference对象，用于在运行时推断MULTISET_PROJECTION函数的返回类型。
   * 返回类型取决于输入多重集合的类型和要投影的字段索引。
   */
  private static SqlReturnTypeInference multisetProjectionInfer() { // 创建多重集合投影返回类型推断器的私有静态方法
    return opBinding -> { // 返回一个lambda表达式，接收SqlCallBinding参数
      final MultisetSqlType source = (MultisetSqlType) opBinding.getOperandType(0); // 获取第一个操作数的类型（源多重集合）
      final List<RelDataTypeField> fields = source.getComponentType().getFieldList(); // 获取源多重集合组件类型的所有字段列表
      // Project a multiset of single column // 投影单个列的多重集合
      if (opBinding.getOperandCount() == 2) { // 如果只有两个操作数（源多重集合+一个字段索引）
        final int fieldNo = opBinding.getOperandLiteralValue(1, Integer.class); // 获取第二个操作数的值（要投影的字段索引）
        if (fields.size() == 1) { // 如果源多重集合只有一个字段
          // Corner case: source with only single column, nothing to do. // 边界情况：源只有一个列，无需处理
          assert fieldNo == 0; // 断言字段索引为0
          return source; // 直接返回源类型
        } else { // 如果源多重集合有多个字段
          return TYPE_FACTORY.createMultisetType(fields.get(fieldNo).getType(), -1); // 创建新的多重集合类型，只包含指定字段
        }
      }
      // Construct a multiset of records of the input argument types // 构造包含输入参数类型记录的多重集合
      final List<String> destNames = new ArrayList<>(); // 创建目标字段名称列表
      final List<RelDataType> destTypes = new ArrayList<>(); // 创建目标字段类型列表
      for (int i = 1; i < opBinding.getOperandCount(); i++) { // 遍历除第一个外的所有操作数（字段索引）
        final int fieldNo = opBinding.getOperandLiteralValue(i, Integer.class); // 获取当前操作数的值（字段索引）
        destNames.add(fields.get(fieldNo).getName()); // 将该字段名称添加到目标名称列表
        destTypes.add(fields.get(fieldNo).getType()); // 将该字段类型添加到目标类型列表
      }
      return TYPE_FACTORY.createMultisetType( // 创建新的多重集合类型
          TYPE_FACTORY.createStructType(destTypes, destNames), -1); // 使用目标类型和名称创建结构类型作为组件类型
    }; // 结束lambda表达式
  }

  /**
   * Returns a {@link SqlOperandTypeChecker} for multiset projection operator. // 返回多重集合投影操作符的操作数类型检查器
   * 
   * 该方法创建一个SqlOperandMetadata对象，用于在编译时验证MULTISET_PROJECTION函数的参数是否合法。
   * 检查内容包括：参数数量、第一个参数是否为多重集合类型、后续参数是否为有效的字段索引。
   */
  private static SqlOperandMetadata multisetProjectionCheck() { // 创建多重集合投影操作数元数据检查器的私有静态方法
    // This should not really be a UDF. A SQL UDF has a fixed number of named // 这实际上不应该是一个UDF。SQL UDF通常有固定数量的命名参数
    // parameters, and this does not. But let's pretend that it has two // 但这个函数不是。让我们假装它有两个
    // parameters of type 'ANY' // 类型为'ANY'的参数
    final int paramCount = 2; // 设置参数数量为2（用于元数据，实际参数可变）

    return new SqlOperandMetadata() { // 返回一个匿名内部类实现SqlOperandMetadata接口
      @Override public boolean checkOperandTypes( // 重写checkOperandTypes方法，检查操作数类型
          SqlCallBinding callBinding, boolean throwOnFailure) { // 接收调用绑定和失败时是否抛出异常标志
        // Need at least two arguments // 至少需要两个参数
        if (callBinding.getOperandCount() < 2) { // 如果操作数数量小于2
          return false; // 返回false表示类型检查失败
        }

        // The first argument should be a multiset // 第一个参数应该是多重集合类型
        if (!(callBinding.getOperandType(0) instanceof MultisetSqlType)) { // 如果第一个操作数不是MultisetSqlType
          return false; // 返回false表示类型检查失败
        }

        // All the subsequent arguments should be appropriate integers // 所有后续参数应该是适当的整数
        final MultisetSqlType source = (MultisetSqlType) callBinding.getOperandType(0); // 获取源多重集合类型
        final int maxFieldNo = source.getComponentType().getFieldCount() - 1; // 计算最大有效字段索引

        for (int i = 1; i < callBinding.getOperandCount(); i++) { // 遍历除第一个外的所有操作数
          if (!(callBinding.getOperandLiteralValue(i, Comparable.class) // 检查操作数值是否为BigDecimal
              instanceof BigDecimal)) { // 如果不是BigDecimal类型
            return false; // 返回false表示类型检查失败
          }
          final int fieldNo = // 获取字段索引
              callBinding.getOperandLiteralValue(i, Integer.class); // 将BigDecimal转换为Integer
          // Field number should between 0 and maxFieldNo // 字段索引应该在0到maxFieldNo之间
          if (fieldNo < 0 || fieldNo > maxFieldNo) { // 如果字段索引超出有效范围
            return false; // 返回false表示类型检查失败
          }
        }
        return true; // 所有检查通过，返回true
      }

      @Override public SqlOperandCountRange getOperandCountRange() { // 重写getOperandCountRange方法，获取操作数数量范围
        return SqlOperandCountRanges.from(2); // 返回从2开始的范围（至少2个参数）
      }

      @Override public String getAllowedSignatures(SqlOperator op, String opName) { // 重写getAllowedSignatures方法，获取允许的函数签名
        return opName + "(...)"; // 返回函数名称加省略号，表示可变参数
      }

      @Override public List<RelDataType> paramTypes( // 重写paramTypes方法，获取参数类型列表
          RelDataTypeFactory typeFactory) { // 接收类型工厂参数
        return Functions.generate(paramCount, // 生成paramCount个参数类型
            i -> typeFactory.createSqlType(SqlTypeName.ANY)); // 每个参数类型为ANY
      }

      @Override public List<String> paramNames() { // 重写paramNames方法，获取参数名称列表
        return Functions.generate(paramCount,  i -> "arg" + i); // 生成paramCount个参数名称（arg0, arg1, ...）
      }

      @Override public boolean isFixedParameters() { // 重写isFixedParameters方法，判断参数是否固定
        return true; // 返回true表示参数固定（虽然实际是可变的，但为了兼容性返回true）
      }
    }; // 结束匿名内部类
  }

  /**
   * Helper method to return a list of SqlTypeFamily for a given list of // 辅助方法：返回给定关系操作数列表的SqlTypeFamily列表
   * relational operands.
   *
   * @param operands List of relational operands // 参数：关系操作数列表（RexNode对象）
   * @return List of SqlTypeFamily objects // 返回值：SqlTypeFamily对象列表
   * 
   * 该方法遍历所有关系操作数，提取每个操作数的类型族信息。
   * 类型族是SQL类型的分类，如INTEGER、STRING、NUMERIC等，用于类型匹配和检查。
   */
  private static List<SqlTypeFamily> getTypeFamilies(ImmutableList<RexNode> operands) { // 获取类型族列表的私有静态方法
    List<SqlTypeFamily> ret = new ArrayList<>(); // 创建返回列表
    for (RexNode operand : operands) { // 遍历所有关系操作数
      SqlTypeFamily family = operand.getType().getSqlTypeName().getFamily(); // 获取操作数的类型族
      ret.add(family != null ? family : SqlTypeFamily.ANY); // 如果类型族不为null则添加，否则添加ANY
    }
    return ret; // 返回类型族列表
  }

  /**
   * Helper method to return a list of RelDataType for a given list of // 辅助方法：返回给定关系操作数列表的RelDataType列表
   * relational operands.
   *
   * @param operands List of relational operands // 参数：关系操作数列表（RexNode对象）
   * @return List of RelDataTypes // 返回值：RelDataType对象列表
   * 
   * 该方法遍历所有关系操作数，提取每个操作数的具体数据类型。
   * 与getTypeFamilies方法不同，这个方法返回的是完整的数据类型信息，而不仅仅是类型族。
   */
  private static List<RelDataType> getRelDataTypes(ImmutableList<RexNode> operands) { // 获取关系数据类型列表的私有静态方法
    List<RelDataType> ret = new ArrayList<>(); // 创建返回列表
    for (RexNode operand : operands) { // 遍历所有关系操作数
      ret.add(operand.getType()); // 添加操作数的类型到列表
    }
    return ret; // 返回关系数据类型列表
  }

  /**
   * Gets the SqlReturnTypeInference that can infer the return type from a // 获取能够从函数推断返回类型的SqlReturnTypeInference
   * function.
   *
   * @param function ScalarFunction // 参数：标量函数对象
   * @return SqlReturnTypeInference // 返回值：SQL返回类型推断器
   * 
   * 该方法为给定的标量函数创建一个返回类型推断器。
   * 推断器在运行时根据函数的元数据信息推断返回类型。
   */
  private static SqlReturnTypeInference infer(final ScalarFunction function) { // 创建返回类型推断器的私有静态方法
    return opBinding -> getRelDataType(function); // 返回lambda表达式，调用getRelDataType方法获取返回类型
  }

  /**
   * Gets the return data type for a given function. // 获取给定函数的返回数据类型
   *
   * @param function ScalarFunction // 参数：标量函数对象
   * @return returned data type // 返回值：返回的数据类型
   * 
   * 该方法通过调用标量函数的getReturnType方法获取返回类型，
   * 并对特殊情况进行处理（如Object类型转换为ANY类型）。
   */
  private static RelDataType getRelDataType(ScalarFunction function) { // 获取关系数据类型的私有静态方法
    final JavaTypeFactory typeFactory = TYPE_FACTORY; // 获取类型工厂实例
    final RelDataType type = function.getReturnType(typeFactory); // 调用函数的getReturnType方法获取返回类型
    if (type instanceof RelDataTypeFactoryImpl.JavaType // 如果返回类型是JavaType
        && ((RelDataTypeFactoryImpl.JavaType) type).getJavaClass() // 且Java类是Object
        == Object.class) { // 检查是否为Object类型
      return typeFactory.createTypeWithNullability( // 创建可空类型
          typeFactory.createSqlType(SqlTypeName.ANY), true); // 创建ANY类型并设置为可空
    }
    return typeFactory.toSql(type); // 将Java类型转换为SQL类型并返回
  }

  /**
   * Implementation for PIG_TUPLE functions. Builds a Pig Tuple from // PIG_TUPLE函数的实现。从对象数组构建Pig Tuple
   * an array of objects
   *
   * @param elements Array of element objects // 参数：元素对象数组
   * @return Pig Tuple // 返回值：Pig Tuple对象
   * 
   * 该方法是PIG_TUPLE UDF的实际实现，接收任意数量的对象参数，
   * 将它们封装成一个Pig Tuple。Tuple是Pig中表示一行数据的基本单位，
   * 类似于关系数据库中的记录或行。
   */
  public static Tuple buildTuple(Object... elements) { // 构建Pig Tuple的公共静态方法，接收可变参数
    return TupleFactory.getInstance().newTuple(Arrays.asList(elements)); // 使用TupleFactory创建Tuple，将参数数组转换为List
  }


  /**
   * Implementation for PIG_BAG functions. Builds a Pig DataBag from // PIG_BAG函数的实现。从输入构建Pig DataBag
   * the corresponding input
   *
   * @param elements Input that contains a bag // 参数：包含数据包的输入
   * @return Pig Tuple // 返回值：Pig Tuple对象（包含DataBag作为第一个元素）
   * 
   * 该方法是PIG_BAG UDF的实际实现，接收一个或多个对象参数，
   * 第一个参数可以是List（多行数据）或单个对象，将它们转换为Tuple并封装成DataBag。
   * DataBag是Pig中表示元组集合的数据结构，类似于关系数据库中的表或结果集。
   * 返回的Tuple包含DataBag作为第一个元素，可能还包含其他元素。
   */
  public static Tuple buildBag(Object... elements) { // 构建Pig DataBag的公共静态方法，接收可变参数
    final TupleFactory tupleFactory = TupleFactory.getInstance(); // 获取TupleFactory实例
    final BagFactory bagFactory = BagFactory.getInstance(); // 获取BagFactory实例
    // Convert each row into a Tuple // 将每一行转换为Tuple
    List<Tuple> tupleList = new ArrayList<>(); // 创建Tuple列表用于存储转换后的Tuple
    if (elements != null) { // 如果输入元素不为null
      // The first input contains a list of rows for the bag // 第一个输入包含数据包的行列表
      final List bag = (elements[0] instanceof List) // 检查第一个元素是否为List类型
          ? (List) elements[0] // 如果是List则直接使用
          : Collections.singletonList(elements[0]); // 否则将其包装为单元素List
      for (Object row : bag) { // 遍历bag中的每一行
        tupleList.add(tupleFactory.newTuple(Arrays.asList(row))); // 将每一行转换为Tuple并添加到列表
      }
    }

    // Then build a bag from the tuple list // 然后从tuple列表构建bag
    DataBag resultBag = bagFactory.newDefaultBag(tupleList); // 使用BagFactory创建默认的DataBag

    // The returned result is a new Tuple with the newly constructed DataBag // 返回的结果是一个新的Tuple，包含新构建的DataBag
    // as the first item. // 作为第一个元素
    List<Object> finalTuple = new ArrayList<>(); // 创建最终Tuple的元素列表
    finalTuple.add(resultBag); // 将DataBag添加到列表中

    if (elements != null) { // 如果输入元素不为null
      // Add the remaining elements from the input // 添加输入中的剩余元素
      for (int i = 1; i < elements.length; i++) { // 从第二个元素开始遍历
        finalTuple.add(elements[i]); // 将剩余元素添加到最终Tuple列表
      }
    }

    return tupleFactory.newTuple(finalTuple); // 使用finalTuple列表创建并返回Tuple
  }

  /**
   * Implementation for BAG_PROJECTION functions. Builds a new multiset by // BAG_PROJECTION函数的实现。通过从另一个多重集合投影特定列来构建新的多重集合
   * projecting certain columns from another multiset.
   *
   * @param objects Input argument, the first one is a multiset, the remaining // 参数：输入参数，第一个是多重集合，剩余的是要投影的列索引
   *                are indexes of column to project.
   * @return The projected multiset // 返回值：投影后的多重集合
   * 
   * 该方法是MULTISET_PROJECTION UDF的实际实现，接收一个多重集合和多个列索引，
   * 从原多重集合的每一行中提取指定索引的列，构建新的多重集合。
   * 如果只投影一列，结果是单列的多重集合；如果投影多列，结果是结构类型的多重集合。
   */
  public static List projectMultiset(Object... objects) { // 投影多重集合的公共静态方法，接收可变参数
    // The first input is a multiset // 第一个输入是多重集合
    final List<Object[]> inputMultiset = (List) objects[0]; // 获取输入多重集合（List<Object[]>）
    final List projectedMultiset = new ArrayList<>(); // 创建投影后的多重集合列表

    for (Object[] row : inputMultiset) { // 遍历输入多重集合的每一行
      if (objects.length > 2) { // 如果要投影的列数大于1
        // Projecting more than one column, the projected multiset should have // 投影多列，投影后的多重集合应该有行类型的组件类型
        // the component type of a row
        Object[] newRow = new Object[objects.length - 1]; // 创建新行数组，长度为列数
        for (int j = 1; j < objects.length; j++) { // 遍历所有列索引
          newRow[j - 1] = row[(Integer) objects[j]]; // 从原行中提取指定列的值
        }
        projectedMultiset.add(newRow); // 将新行添加到投影后的多重集合
      } else { // 如果只投影一列
        // Projecting a single column // 投影单个列
        projectedMultiset.add(row[(Integer) objects[1]]); // 从原行中提取指定列的值并添加
      }
    }
    return projectedMultiset; // 返回投影后的多重集合
  }
} // 结束PigRelSqlUdfs类定义
