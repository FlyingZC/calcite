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
 */ // Apache许可证声明，说明代码的版权和使用条款
package org.apache.calcite.adapter.enumerable; // 定义包名，该类属于enumerable适配器包，用于实现可枚举调用约定

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂，用于创建和管理Java类型
import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入代码块构建器，用于构建Java代码块
import org.apache.calcite.linq4j.tree.Expression; // 导入表达式类，用于表示Java表达式
import org.apache.calcite.linq4j.tree.Expressions; // 导入表达式工具类，用于创建各种表达式
import org.apache.calcite.linq4j.tree.ParameterExpression; // 导入参数表达式类，用于表示方法参数
import org.apache.calcite.linq4j.tree.Types; // 导入类型工具类，用于类型转换和检查
import org.apache.calcite.plan.RelOptCluster; // 导入关系表达式集群，包含优化器相关信息
import org.apache.calcite.plan.RelOptTable; // 导入关系表达式表，表示表对象
import org.apache.calcite.plan.RelTraitSet; // 导入关系特征集合，定义关系节点的特征
import org.apache.calcite.prepare.Prepare; // 导入准备工具类，用于SQL准备阶段
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，所有关系节点的基础接口
import org.apache.calcite.rel.core.TableModify; // 导入表修改基类，定义表修改操作的抽象
import org.apache.calcite.rex.RexNode; // 导入行表达式节点，表示行级别的表达式
import org.apache.calcite.schema.ModifiableTable; // 导入可修改表接口，定义表的可修改操作
import org.apache.calcite.util.BuiltInMethod; // 导入内置方法枚举，包含预定义的方法引用

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，标记可能为null的参数

import java.lang.reflect.Method; // 导入方法反射类，用于反射调用方法
import java.lang.reflect.Modifier; // 导入修饰符反射类，用于检查方法修饰符
import java.util.ArrayList; // 导入动态数组列表，用于存储表达式列表
import java.util.Collection; // 导入集合接口，表示可修改的数据集合
import java.util.List; // 导入列表接口，用于存储有序数据

import static com.google.common.base.Preconditions.checkArgument; // 静态导入参数检查方法，用于验证参数有效性

import static java.util.Objects.requireNonNull; // 静态导入非空检查方法，用于验证对象非空

/** Implementation of {@link org.apache.calcite.rel.core.TableModify} in
 * {@link org.apache.calcite.adapter.enumerable.EnumerableConvention enumerable calling convention}. */ // 类注释：这是TableModify在可枚举调用约定下的实现类
public class EnumerableTableModify extends TableModify // 定义类，继承自TableModify基类，实现可枚举关系节点接口
    implements EnumerableRel { // 实现EnumerableRel接口，支持可枚举调用约定
  public EnumerableTableModify(RelOptCluster cluster, RelTraitSet traits, // 构造方法：集群对象，包含优化器和类型工厂等信息
      RelOptTable table, Prepare.CatalogReader catalogReader, RelNode child, // 要修改的表对象，目录读取器，子关系节点（提供数据源）
      Operation operation, @Nullable List<String> updateColumnList, // 操作类型（INSERT/UPDATE/DELETE），更新列列表（可为null）
      @Nullable List<RexNode> sourceExpressionList, boolean flattened) { // 源表达式列表（可为null），是否扁平化标志
    super(cluster, traits, table, catalogReader, child, operation, // 调用父类构造方法，初始化TableModify基类
        updateColumnList, sourceExpressionList, flattened); // 传递更新列列表、源表达式列表和扁平化标志
    assert child.getConvention() instanceof EnumerableConvention; // 断言子节点的调用约定必须是EnumerableConvention类型
    assert getConvention() instanceof EnumerableConvention; // 断言当前节点的调用约定必须是EnumerableConvention类型
    final ModifiableTable modifiableTable = // 获取可修改表对象，用于执行修改操作
        table.unwrap(ModifiableTable.class); // 通过unwrap方法尝试将表转换为ModifiableTable接口
    if (modifiableTable == null) { // 如果表不支持可修改操作（unwrap返回null）
      throw new AssertionError(); // TODO: user error in validator // 抛出断言错误，应该在验证器中捕获并转换为用户友好的错误消息
    }
  } // 构造方法结束

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法，用于复制关系节点
    return new EnumerableTableModify( // 创建并返回新的EnumerableTableModify实例
        getCluster(), // 使用相同的集群对象
        traitSet, // 使用新的特征集合
        getTable(), // 使用相同的表对象
        getCatalogReader(), // 使用相同的目录读取器
        sole(inputs), // 从输入列表中获取唯一的子节点
        getOperation(), // 使用相同的操作类型
        getUpdateColumnList(), // 使用相同的更新列列表
        getSourceExpressionList(), // 使用相同的源表达式列表
        isFlattened()); // 使用相同的扁平化标志
  } // copy方法结束

  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) { // 重写implement方法，生成可枚举实现代码
    final BlockBuilder builder = new BlockBuilder(); // 创建代码块构建器，用于构建生成的Java代码块
    final Result result = // 访问子节点，获取子节点的实现结果
        implementor.visitChild(this, 0, (EnumerableRel) getInput(), pref); // 调用实现器的visitChild方法，处理子节点
    Expression childExp = // 将子节点的代码块作为表达式添加到构建器中
        builder.append( // 添加表达式到代码块构建器
            "child", result.block); // 表达式名称为"child"，值为子节点的代码块
    final ParameterExpression collectionParameter = // 创建集合参数表达式，用于引用可修改集合
        Expressions.parameter(Collection.class, // 参数类型为Collection集合接口
            builder.newName("collection")); // 参数名称为自动生成的"collection"
    final Expression expression = table.getExpression(ModifiableTable.class); // 获取表的ModifiableTable表达式对象
    requireNonNull(expression, "expression"); // TODO: user error in validator // 验证表达式非空，否则抛出异常
    checkArgument( // 检查表达式类型是否可赋值给ModifiableTable类
        ModifiableTable.class.isAssignableFrom( // 使用反射检查类型兼容性
            Types.toClass(expression.getType())), // 将表达式类型转换为Class对象
        "not assignable from type %s", expression.getType()); // 如果类型不兼容，抛出参数异常
    builder.add( // 添加变量声明到代码块
        Expressions.declare( // 创建变量声明表达式
            Modifier.FINAL, // 变量修饰符为final，表示不可修改
            collectionParameter, // 参数表达式，变量名
            Expressions.call( // 初始化值为方法调用表达式
                expression, // 调用对象为表表达式
                BuiltInMethod.MODIFIABLE_TABLE_GET_MODIFIABLE_COLLECTION // 调用getModifiableCollection方法
                    .method))); // 获取可修改集合的方法对象
    final Expression countParameter = // 创建计数参数表达式，用于记录修改前的集合大小
        builder.append( // 添加表达式到代码块构建器
            "count", // 表达式名称为"count"
            Expressions.call(collectionParameter, "size"), // 调用集合的size()方法获取大小
            false); // 不需要语句形式，仅为表达式
    Expression convertedChildExp; // 声明转换后的子表达式变量
    if (!getInput().getRowType().equals(getRowType())) { // 如果子节点的行类型与当前节点的行类型不同，需要进行类型转换
      final JavaTypeFactory typeFactory = // 获取Java类型工厂，用于创建Java类型
          (JavaTypeFactory) getCluster().getTypeFactory(); // 从集群中获取类型工厂并强制类型转换
      final JavaRowFormat format = EnumerableTableScan.deduceFormat(table); // 推断表的Java行格式（ARRAY/SCALAR等）
      PhysType physType = // 创建物理类型对象，表示表行的物理表示
          PhysTypeImpl.of(typeFactory, table.getRowType(), format); // 使用类型工厂、行类型和行格式创建
      List<Expression> expressionList = new ArrayList<>(); // 创建表达式列表，用于存储字段引用表达式
      final PhysType childPhysType = result.physType; // 获取子节点的物理类型
      final ParameterExpression o_ = // 创建参数表达式，表示lambda表达式的输入参数
          Expressions.parameter(childPhysType.getJavaRowType(), "o"); // 参数类型为子节点的Java行类型，参数名为"o"
      final int fieldCount = // 获取字段数量
          childPhysType.getRowType().getFieldCount(); // 从子节点的行类型中获取字段数
      for (int i = 0; i < fieldCount; i++) { // 遍历所有字段
        expressionList.add( // 将字段引用表达式添加到列表
            childPhysType.fieldReference(o_, i, physType.getJavaFieldType(i))); // 创建字段引用，引用第i个字段，类型转换为目标类型
      }
      convertedChildExp = // 创建转换后的子表达式，使用select操作进行类型转换
          builder.append( // 添加表达式到代码块构建器
              "convertedChild", // 表达式名称为"convertedChild"
              Expressions.call( // 创建方法调用表达式
                  childExp, // 调用对象为子表达式
                  BuiltInMethod.SELECT.method, // 调用select方法
                  Expressions.lambda( // 创建lambda表达式作为参数
                      physType.record(expressionList), o_))); // lambda函数将输入参数转换为目标格式
    } else { // 如果行类型相同，不需要转换
      convertedChildExp = childExp; // 直接使用子表达式
    }
    final Method method; // 声明方法变量，用于存储要调用的修改操作方法
    switch (getOperation()) { // 根据操作类型选择相应的内置方法
    case INSERT: // 如果操作类型是INSERT
      method = BuiltInMethod.INTO.method; // 使用INTO方法（将数据插入集合）
      break; // 跳出switch语句
    case DELETE: // 如果操作类型是DELETE
      method = BuiltInMethod.REMOVE_ALL.method; // 使用REMOVE_ALL方法（从集合中删除所有匹配项）
      break; // 跳出switch语句
    default: // 其他操作类型（如UPDATE）
      throw new AssertionError(getOperation()); // 抛出断言错误，当前实现不支持UPDATE操作
    }
    builder.add( // 添加语句到代码块
        Expressions.statement( // 创建语句表达式
            Expressions.call( // 创建方法调用表达式
                convertedChildExp, method, collectionParameter))); // 调用转换后的子表达式的修改方法，传入集合参数
    final Expression updatedCountParameter = // 创建更新计数参数表达式，用于记录修改后的集合大小
        builder.append( // 添加表达式到代码块构建器
            "updatedCount", // 表达式名称为"updatedCount"
            Expressions.call(collectionParameter, "size"), // 调用集合的size()方法获取更新后的大小
            false); // 不需要语句形式，仅为表达式
    builder.add( // 添加返回语句到代码块
        Expressions.return_( // 创建返回语句表达式
            null, // 返回值为null（表示从当前方法返回）
            Expressions.call( // 创建方法调用表达式
                BuiltInMethod.SINGLETON_ENUMERABLE.method, // 调用SINGLETON_ENUMERABLE方法，创建只包含一个元素的枚举器
                Expressions.convert_( // 创建类型转换表达式
                    Expressions.condition( // 创建条件表达式
                        Expressions.greaterThanOrEqual( // 比较更新后计数与修改前计数
                            updatedCountParameter, countParameter), // 如果updatedCount >= count（INSERT操作）
                        Expressions.subtract( // 则返回插入的行数
                            updatedCountParameter, countParameter), // updatedCount - count
                        Expressions.subtract( // 否则返回删除的行数（DELETE操作）
                            countParameter, updatedCountParameter)), // count - updatedCount
                    long.class))))); // 将结果转换为long类型
    final PhysType physType = // 创建物理类型对象，用于返回结果
        PhysTypeImpl.of( // 使用PhysTypeImpl工厂方法创建
            implementor.getTypeFactory(), // 使用实现器的类型工厂
            getRowType(), // 使用当前节点的行类型
            pref == Prefer.ARRAY // 根据偏好选择行格式
                ? JavaRowFormat.ARRAY : JavaRowFormat.SCALAR); // 如果偏好ARRAY则用ARRAY格式，否则用SCALAR格式
    return implementor.result(physType, builder.toBlock()); // 返回实现结果，包含物理类型和生成的代码块
  } // implement方法结束

} // 类定义结束
