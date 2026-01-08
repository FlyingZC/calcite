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
 */ // Apache许可证声明，允许在符合Apache 2.0许可证条款的情况下使用本代码
package org.apache.calcite.adapter.java; // 定义包名，表示这个类属于Calcite的Java适配器模块

import org.apache.calcite.DataContext; // 导入DataContext接口，用于提供查询执行时的上下文环境
import org.apache.calcite.adapter.enumerable.EnumUtils; // 导入EnumUtils工具类，提供枚举相关的辅助方法
import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口，表示可枚举的数据集合
import org.apache.calcite.linq4j.Enumerator; // 导入Enumerator接口，用于遍历数据集合
import org.apache.calcite.linq4j.Linq4j; // 导入Linq4j工具类，提供LINQ风格的查询功能
import org.apache.calcite.linq4j.QueryProvider; // 导入QueryProvider接口，用于提供查询功能
import org.apache.calcite.linq4j.Queryable; // 导入Queryable接口，表示可查询的数据集合
import org.apache.calcite.linq4j.function.Function1; // 导入Function1接口，表示接受一个参数的函数
import org.apache.calcite.linq4j.tree.Expression; // 导入Expression类，表示表达式树中的节点
import org.apache.calcite.linq4j.tree.Expressions; // 导入Expressions工具类，用于构建表达式树
import org.apache.calcite.linq4j.tree.Primitive; // 导入Primitive工具类，用于处理基本类型
import org.apache.calcite.rel.RelReferentialConstraint; // 导入RelReferentialConstraint类，表示关系引用约束（外键约束）
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建关系数据类型
import org.apache.calcite.schema.Function; // 导入Function接口，表示Calcite中的函数
import org.apache.calcite.schema.ScannableTable; // 导入ScannableTable接口，表示可扫描的表
import org.apache.calcite.schema.Schema; // 导入Schema接口，表示Calcite中的模式（数据库模式）
import org.apache.calcite.schema.SchemaFactory; // 导入SchemaFactory接口，用于创建Schema实例
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，扩展了Schema接口，提供额外功能
import org.apache.calcite.schema.Schemas; // 导入Schemas工具类，提供Schema相关的辅助方法
import org.apache.calcite.schema.Statistic; // 导入Statistic接口，表示表的统计信息
import org.apache.calcite.schema.Statistics; // 导入Statistics工具类，用于创建统计信息
import org.apache.calcite.schema.Table; // 导入Table接口，表示Calcite中的表
import org.apache.calcite.schema.TableMacro; // 导入TableMacro接口，表示表宏（可以动态生成表）
import org.apache.calcite.schema.TranslatableTable; // 导入TranslatableTable接口，表示可转换为关系表达式的表
import org.apache.calcite.schema.impl.AbstractSchema; // 导入AbstractSchema抽象类，提供Schema的基本实现
import org.apache.calcite.schema.impl.AbstractTableQueryable; // 导入AbstractTableQueryable抽象类，提供表查询的基本实现
import org.apache.calcite.schema.impl.ReflectiveFunctionBase; // 导入ReflectiveFunctionBase抽象类，提供反射函数的基本实现
import org.apache.calcite.util.BuiltInMethod; // 导入BuiltInMethod枚举，定义内置方法
import org.apache.calcite.util.Util; // 导入Util工具类，提供通用的辅助方法

import com.google.common.collect.ImmutableList; // 导入ImmutableList类，表示不可变列表
import com.google.common.collect.ImmutableMap; // 导入ImmutableMap类，表示不可变映射
import com.google.common.collect.ImmutableMultimap; // 导入ImmutableMultimap类，表示不可变多重映射
import com.google.common.collect.Iterables; // 导入Iterables工具类，提供迭代器相关的辅助方法
import com.google.common.collect.Multimap; // 导入Multimap接口，表示多重映射（一个键可以对应多个值）

import org.checkerframework.checker.nullness.qual.MonotonicNonNull; // 导入MonotonicNonNull注解，表示字段单调非空（从null变为非null后不再变回null）
import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，表示字段或方法返回值可能为null

import java.lang.reflect.Array; // 导入Array类，提供反射操作数组的方法
import java.lang.reflect.Constructor; // 导入Constructor类，表示类的构造函数
import java.lang.reflect.Field; // 导入Field类，表示类的字段
import java.lang.reflect.InvocationTargetException; // 导入InvocationTargetException异常，表示调用方法时抛出的异常
import java.lang.reflect.Method; // 导入Method类，表示类的方法
import java.lang.reflect.Type; // 导入Type接口，表示Java类型
import java.util.Collection; // 导入Collection接口，表示集合
import java.util.Collections; // 导入Collections工具类，提供集合操作的辅助方法
import java.util.List; // 导入List接口，表示列表
import java.util.Map; // 导入Map接口，表示映射

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于检查对象不为null

/**
 * Implementation of {@link org.apache.calcite.schema.Schema} that exposes the
 * public fields and methods in a Java object.
 */ // 类文档注释：这是Schema接口的实现，通过反射暴露Java对象的公共字段和方法作为表和函数
public class ReflectiveSchema // 定义ReflectiveSchema类，继承自AbstractSchema抽象类
    extends AbstractSchema { // 继承AbstractSchema，获得Schema的基本实现
  private final Class<?> clazz; // 成员变量：存储目标对象的Class对象，用于反射获取字段和方法
  private final Object target; // 成员变量：存储被包装的目标Java对象，该对象的字段将成为表
  private @MonotonicNonNull Map<String, Table> tableMap; // 成员变量：缓存表名到Table对象的映射，使用MonotonicNonNull注解表示初始化为null，之后变为非null后不再变回null
  private @MonotonicNonNull Multimap<String, Function> functionMap; // 成员变量：缓存函数名到Function对象的映射，一个函数名可以对应多个函数（重载），使用MonotonicNonNull注解

  /**
   * Creates a ReflectiveSchema.
   *
   * @param target Object whose fields will be sub-objects of the schema
   */ // 构造方法文档注释：创建一个ReflectiveSchema实例
  public ReflectiveSchema(Object target) { // 构造方法：接收一个Java对象作为参数
    super(); // 调用父类AbstractSchema的构造方法
    this.clazz = target.getClass(); // 获取目标对象的Class对象并保存到clazz字段
    this.target = target; // 保存目标对象到target字段
  } // 构造方法结束

  @Override public String toString() { // 重写toString方法，返回对象的字符串表示
    return "ReflectiveSchema(target=" + target + ")"; // 返回包含目标对象的字符串
  } // toString方法结束

  /** Returns the wrapped object.
   *
   * <p>May not appear to be used, but is used in generated code via
   * {@link org.apache.calcite.util.BuiltInMethod#REFLECTIVE_SCHEMA_GET_TARGET}.
   */ // 方法文档注释：返回被包装的目标对象，虽然看起来未被使用，但通过反射在生成的代码中被调用
  public Object getTarget() { // 公共方法：获取被包装的目标对象
    return target; // 返回target字段
  } // getTarget方法结束

  @SuppressWarnings({ "rawtypes", "unchecked" }) // 抑制编译器警告：因为使用了原始类型和未检查的类型转换
  @Override protected Map<String, Table> getTableMap() { // 重写父类方法：获取表名到Table对象的映射
    if (tableMap == null) { // 如果tableMap尚未初始化（为null）
      tableMap = createTableMap(); // 调用createTableMap方法创建并初始化tableMap
    } // if语句结束
    return tableMap; // 返回tableMap
  } // getTableMap方法结束

  private Map<String, Table> createTableMap() { // 私有方法：创建表名到Table对象的映射
    final ImmutableMap.Builder<String, Table> builder = ImmutableMap.builder(); // 创建ImmutableMap.Builder用于构建不可变的Map
    for (Field field : clazz.getFields()) { // 遍历目标类的所有公共字段
      final String fieldName = field.getName(); // 获取字段名称
      final Table table = fieldRelation(field); // 调用fieldRelation方法尝试将字段转换为Table对象
      if (table == null) { // 如果字段不能转换为表（返回null）
        continue; // 跳过该字段，继续处理下一个字段
      } // if语句结束
      builder.put(fieldName, table); // 将字段名和对应的Table对象放入builder
    } // for循环结束
    Map<String, Table> tableMap = builder.build(); // 构建不可变的Map并保存到局部变量tableMap
    // Unique-Key - Foreign-Key // 注释：处理唯一键和外键约束
    for (Field field : clazz.getFields()) { // 再次遍历所有公共字段，查找外键约束
      if (RelReferentialConstraint.class.isAssignableFrom(field.getType())) { // 如果字段类型是RelReferentialConstraint（表示外键约束）
        RelReferentialConstraint rc; // 声明RelReferentialConstraint变量
        try { // 尝试执行可能抛出异常的代码
          rc = (RelReferentialConstraint) field.get(target); // 通过反射从目标对象中获取该字段的值（外键约束对象）
        } catch (IllegalAccessException e) { // 捕获非法访问异常
          throw new RuntimeException( // 抛出运行时异常
              "Error while accessing field " + field, e); // 异常消息包含字段信息
        } // try-catch块结束
        requireNonNull(rc, () -> "field must not be null: " + field); // 检查rc不为null，否则抛出NullPointerException
        FieldTable<?> table = // 声明FieldTable变量
            (FieldTable<?>) // 强制类型转换为FieldTable
                requireNonNull( // 检查不为null
                    tableMap.get(Util.last(rc.getSourceQualifiedName()))); // 从tableMap中获取源表（外键约束的源表）
        List<RelReferentialConstraint> referentialConstraints = // 声明引用约束列表
            table.getStatistic().getReferentialConstraints(); // 从表的统计信息中获取现有的引用约束列表
        if (referentialConstraints == null) { // 如果现有的引用约束列表为null
          // This enables to keep the same Statistics.of below // 注释：这样可以保持下面的Statistics.of调用一致
          referentialConstraints = ImmutableList.of(); // 创建一个空的不可变列表
        } // if语句结束
        table.statistic = // 更新表的统计信息
            Statistics.of( // 创建新的统计信息对象
                ImmutableList.copyOf( // 将列表转换为不可变列表
                    Iterables.concat(referentialConstraints, // 连接现有的引用约束
                        Collections.singleton(rc)))); // 并添加新的引用约束（当前字段）
      } // if语句结束
    } // for循环结束
    return tableMap; // 返回构建好的表映射
  } // createTableMap方法结束

  @Override protected Multimap<String, Function> getFunctionMultimap() { // 重写父类方法：获取函数名到Function对象的多重映射
    if (functionMap == null) { // 如果functionMap尚未初始化（为null）
      functionMap = createFunctionMap(); // 调用createFunctionMap方法创建并初始化functionMap
    } // if语句结束
    return functionMap; // 返回functionMap
  } // getFunctionMultimap方法结束

  private Multimap<String, Function> createFunctionMap() { // 私有方法：创建函数名到Function对象的多重映射
    final ImmutableMultimap.Builder<String, Function> builder = // 创建ImmutableMultimap.Builder用于构建不可变的多重映射
        ImmutableMultimap.builder();
    for (Method method : clazz.getMethods()) { // 遍历目标类的所有公共方法
      final String methodName = method.getName(); // 获取方法名称
      if (method.getDeclaringClass() == Object.class // 如果方法是在Object类中声明的（如equals、hashCode等）
          || methodName.equals("toString")) { // 或者方法名是toString
        continue; // 跳过该方法，继续处理下一个方法
      } // if语句结束
      if (TranslatableTable.class.isAssignableFrom(method.getReturnType())) { // 如果方法的返回类型是TranslatableTable（表宏）
        final TableMacro tableMacro = // 创建TableMacro对象
            new MethodTableMacro(this, method); // 创建MethodTableMacro实例，传入当前schema和方法
        builder.put(methodName, tableMacro); // 将方法名和对应的TableMacro对象放入builder
      } // if语句结束
    } // for循环结束
    return builder.build(); // 构建并返回不可变的多重映射
  } // createFunctionMap方法结束

  /** Returns an expression for the object wrapped by this schema (not the
   * schema itself).
   */ // 方法文档注释：返回被包装对象的表达式（不是schema本身的表达式）
  Expression getTargetExpression(@Nullable SchemaPlus parentSchema, String name) { // 方法：获取目标对象的表达式，参数为父schema和名称
    return EnumUtils.convert( // 调用EnumUtils.convert转换表达式
        Expressions.call( // 创建方法调用表达式
            Schemas.unwrap( // 解包schema获取ReflectiveSchema实例
                getExpression(parentSchema, name), // 获取当前schema的表达式
                ReflectiveSchema.class), // 指定要解包的类型为ReflectiveSchema
            BuiltInMethod.REFLECTIVE_SCHEMA_GET_TARGET.method), // 调用getTarget方法
        target.getClass()); // 转换为目标对象的类型
  } // getTargetExpression方法结束

  /** Returns a table based on a particular field of this schema. If the
   * field is not of the right type to be a relation, returns null.
   */ // 方法文档注释：基于schema的特定字段返回表，如果字段类型不适合作为关系，返回null
  private <T> @Nullable Table fieldRelation(final Field field) { // 私有泛型方法：将字段转换为Table对象
    final Type elementType = getElementType(field.getType()); // 调用getElementType方法获取字段的元素类型（数组元素类型或集合元素类型）
    if (elementType == null) { // 如果元素类型为null（表示字段不是集合或数组）
      return null; // 返回null，表示该字段不能作为表
    } // if语句结束
    Object o; // 声明Object变量用于存储字段的值
    try { // 尝试执行可能抛出异常的代码
      o = field.get(target); // 通过反射从目标对象中获取字段的值
    } catch (IllegalAccessException e) { // 捕获非法访问异常
      throw new RuntimeException( // 抛出运行时异常
          "Error while accessing field " + field, e); // 异常消息包含字段信息
    } // try-catch块结束
    requireNonNull(o, () -> "field " + field + " is null for " + target); // 检查字段值不为null，否则抛出NullPointerException
    @SuppressWarnings("unchecked") // 抑制未检查的类型转换警告
    final Enumerable<T> enumerable = toEnumerable(o); // 调用toEnumerable方法将对象转换为可枚举集合
    final Double rows = getRowCount(o); // 调用getRowCount方法获取行数（集合大小）
    final Statistic statistic = rows == null // 如果行数为null
        ? Statistics.UNKNOWN // 使用UNKNOWN统计信息
        : Statistics.of(rows, null); // 否则创建包含行数的统计信息
    return new FieldTable<>(field, elementType, enumerable, statistic); // 创建并返回FieldTable实例
  } // fieldRelation方法结束

  /** Deduces a collection's element type;
   * same logic as {@link #toEnumerable}.
   */ // 方法文档注释：推断集合的元素类型，逻辑与toEnumerable方法相同
  private static @Nullable Type getElementType(Class<?> clazz) { // 私有静态方法：推断类的元素类型
    if (clazz.isArray()) { // 如果类是数组类型
      return clazz.getComponentType(); // 返回数组的组件类型（元素类型）
    } // if语句结束
    if (Iterable.class.isAssignableFrom(clazz)) { // 如果类是Iterable的子类（实现了Iterable接口）
      return Object.class; // 返回Object.class作为元素类型（因为Iterable的元素类型在运行时被擦除）
    } // if语句结束
    return null; // 返回null，表示不是集合、数组或Iterable
  } // getElementType方法结束

  @SuppressWarnings("unchecked") // 抑制未检查的类型转换警告
  private static <T> Enumerable<T> toEnumerable(final Object o) { // 私有静态泛型方法：将对象转换为可枚举集合
    if (o.getClass().isArray()) { // 如果对象是数组
      if (o instanceof Object[]) { // 如果是对象数组
        return Linq4j.asEnumerable((T[]) o); // 使用Linq4j将对象数组转换为可枚举集合
      } else { // 如果是基本类型数组
        return Linq4j.asEnumerable((List<T>) Primitive.asList(o)); // 使用Primitive.asList将基本类型数组转换为List，再转换为可枚举集合
      } // if-else块结束
    } // if语句结束
    if (o instanceof Iterable) { // 如果对象实现了Iterable接口
      return Linq4j.asEnumerable((Iterable<T>) o); // 使用Linq4j将Iterable转换为可枚举集合
    } // if语句结束
    throw new RuntimeException( // 抛出运行时异常
        "Cannot convert " + o.getClass() + " into a Enumerable"); // 异常消息说明无法转换的类型
  } // toEnumerable方法结束

  protected @Nullable Double getRowCount(final Object o) { // 受保护方法：获取对象的行数（元素个数）
    if (o.getClass().isArray()) { // 如果对象是数组
      return (double) Array.getLength(o); // 返回数组长度，转换为double类型
    } else if (o instanceof Collection) { // 如果对象是Collection的实例
      return (double) ((Collection<?>) o).size(); // 返回集合大小，转换为double类型
    } // if-else块结束
    return null; // 返回null，表示无法确定行数
  } // getRowCount方法结束

  /** Table that is implemented by reading from a Java object.
   */ // 内部类文档注释：通过读取Java对象实现的表
  private static class ReflectiveTable // 定义ReflectiveTable内部静态类
      extends AbstractQueryableTable // 继承AbstractQueryableTable，获得可查询表的基本实现
      implements Table, ScannableTable { // 实现Table和ScannableTable接口
    private final Enumerable enumerable; // 成员变量：存储可枚举的数据集合

    ReflectiveTable(Type elementType, Enumerable<?> enumerable) { // 构造方法：接收元素类型和可枚举集合
      super(elementType); // 调用父类构造方法，传入元素类型
      this.enumerable = enumerable; // 保存可枚举集合
    } // 构造方法结束

    @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写方法：获取表的行类型（每一行的数据结构）
      return ((JavaTypeFactory) typeFactory).createType(elementType); // 使用JavaTypeFactory创建元素类型对应的关系数据类型
    } // getRowType方法结束

    @Override public Statistic getStatistic() { // 重写方法：获取表的统计信息
      return Statistics.UNKNOWN; // 返回UNKNOWN统计信息（表示统计信息未知）
    } // getStatistic方法结束

    @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 重写方法：扫描表数据，返回可枚举的对象数组
      if (elementType == Object[].class) { // 如果元素类型本身就是Object[]（已经是数组）
        //noinspection unchecked // 抑制未检查的类型转换警告
        return enumerable; // 直接返回可枚举集合
      } else { // 如果元素类型不是Object[]
        //noinspection unchecked // 抑制未检查的类型转换警告
        return enumerable.select(new FieldSelector((Class) elementType)); // 使用FieldSelector将每个对象转换为其字段值数组
      } // if-else块结束
    } // scan方法结束

    @Override public <T> Queryable<T> asQueryable(QueryProvider queryProvider, // 重写方法：将表转换为可查询对象
        SchemaPlus schema, String tableName) { // 参数：查询提供者、schema、表名
      return new AbstractTableQueryable<T>(queryProvider, schema, this, // 创建AbstractTableQueryable匿名子类
          tableName) { // 传入参数
        @SuppressWarnings("unchecked") // 抑制未检查的类型转换警告
        @Override public Enumerator<T> enumerator() { // 重写enumerator方法，返回枚举器
          return (Enumerator<T>) enumerable.enumerator(); // 返回可枚举集合的枚举器
        } // enumerator方法结束
      }; // 匿名类定义结束
    } // asQueryable方法结束
  } // ReflectiveTable内部类结束

  /** Factory that creates a schema by instantiating an object and looking at
   * its public fields.
   *
   * <p>The following example instantiates a {@code FoodMart} object as a schema
   * that contains tables called {@code EMPS} and {@code DEPTS} based on the
   * object's fields.
   *
   * <blockquote><pre>
   * schemas: [
   *     {
   *       name: "foodmart",
   *       type: "custom",
   *       factory: "org.apache.calcite.adapter.java.ReflectiveSchema$Factory",
   *       operand: {
   *         class: "com.acme.FoodMart",
   *         staticMethod: "instance"
   *       }
   *     }
   *   ]
   * &nbsp;
   * class FoodMart {
   *   public static final FoodMart instance() {
   *     return new FoodMart();
   *   }
   * &nbsp;
   *   Employee[] EMPS;
   *   Department[] DEPTS;
   * }</pre></blockquote>
   */ // 内部类文档注释：Schema工厂，通过实例化对象并查看其公共字段来创建schema，包含使用示例
  public static class Factory implements SchemaFactory { // 定义Factory内部静态类，实现SchemaFactory接口
    @Override public Schema create(SchemaPlus parentSchema, String name, // 重写方法：创建Schema实例
        Map<String, Object> operand) { // 参数：父schema、名称、操作数（包含配置信息）
      Class<?> clazz; // 声明Class变量
      Object target; // 声明目标对象变量
      final Object className = operand.get("class"); // 从操作数中获取类名
      if (className != null) { // 如果类名不为null
        try { // 尝试执行可能抛出异常的代码
          clazz = Class.forName((String) className); // 使用类名加载Class对象
        } catch (ClassNotFoundException e) { // 捕获类未找到异常
          throw new RuntimeException("Error loading class " + className, e); // 抛出运行时异常
        } // try-catch块结束
      } else { // 如果类名为null
        throw new RuntimeException("Operand 'class' is required"); // 抛出运行时异常，说明class操作数是必需的
      } // if-else块结束
      final Object methodName = operand.get("staticMethod"); // 从操作数中获取静态方法名
      if (methodName != null) { // 如果静态方法名不为null
        try { // 尝试执行可能抛出异常的代码
          //noinspection unchecked // 抑制未检查的类型转换警告
          Method method = clazz.getMethod((String) methodName); // 获取指定名称的公共静态方法
          target = method.invoke(null); // 调用静态方法（传入null因为静态方法不需要对象实例）
          requireNonNull(target, () -> "method " + method + " returns null"); // 检查方法返回值不为null
        } catch (Exception e) { // 捕获所有异常
          throw new RuntimeException("Error invoking method " + methodName, e); // 抛出运行时异常
        } // try-catch块结束
      } else { // 如果静态方法名为null
        try { // 尝试执行可能抛出异常的代码
          final Constructor<?> constructor = clazz.getConstructor(); // 获取无参构造函数
          target = constructor.newInstance(); // 使用无参构造函数创建实例
        } catch (Exception e) { // 捕获所有异常
          throw new RuntimeException("Error instantiating class " + className, // 抛出运行时异常
              e); // 异常信息包含类名
        } // try-catch块结束
      } // if-else块结束
      return new ReflectiveSchema(target); // 创建并返回ReflectiveSchema实例
    } // create方法结束
  } // Factory内部类结束

  /** Table macro based on a Java method.
   */ // 内部类文档注释：基于Java方法的表宏
  private static class MethodTableMacro extends ReflectiveFunctionBase // 定义MethodTableMacro内部静态类，继承ReflectiveFunctionBase
      implements TableMacro { // 实现TableMacro接口
    private final ReflectiveSchema schema; // 成员变量：存储所属的ReflectiveSchema实例

    MethodTableMacro(ReflectiveSchema schema, Method method) { // 构造方法：接收schema和方法
      super(method); // 调用父类构造方法，传入方法
      this.schema = schema; // 保存schema
      assert TranslatableTable.class.isAssignableFrom(method.getReturnType()) // 断言方法返回类型是TranslatableTable
          : "Method should return TranslatableTable so the macro can be " // 否则抛出断言错误
          + "expanded"; // 说明方法应该返回TranslatableTable以便宏可以展开
    } // 构造方法结束

    @Override public String toString() { // 重写toString方法
      return "Member {method=" + method + "}"; // 返回包含方法信息的字符串
    } // toString方法结束

    @Override public TranslatableTable apply(final List<? extends @Nullable Object> arguments) { // 重写方法：应用表宏，接收参数列表
      try { // 尝试执行可能抛出异常的代码
        final Object o = method.invoke(schema.getTarget(), arguments.toArray()); // 通过反射调用方法，传入目标对象和参数
        requireNonNull(o, // 检查返回值不为null
            () -> "method " + method + " returned null for arguments " + arguments); // 否则抛出NullPointerException
        return (TranslatableTable) o; // 强制类型转换并返回TranslatableTable
      } catch (IllegalAccessException | InvocationTargetException e) { // 捕获非法访问异常或调用目标异常
        throw new RuntimeException(e); // 抛出运行时异常
      } // try-catch块结束
    } // apply方法结束
  } // MethodTableMacro内部类结束

  /** Table based on a Java field.
   *
   * @param <T> element type */ // 内部类文档注释：基于Java字段的表，泛型参数T表示元素类型
  private static class FieldTable<T> extends ReflectiveTable { // 定义FieldTable内部静态类，继承ReflectiveTable
    private final Field field; // 成员变量：存储对应的Java字段
    private Statistic statistic; // 成员变量：存储表的统计信息

    FieldTable(Field field, Type elementType, Enumerable<T> enumerable) { // 构造方法：接收字段、元素类型、可枚举集合
      this(field, elementType, enumerable, Statistics.UNKNOWN); // 调用另一个构造方法，传入UNKNOWN统计信息
    } // 构造方法结束

    FieldTable(Field field, Type elementType, Enumerable<T> enumerable, // 构造方法：接收字段、元素类型、可枚举集合、统计信息
        Statistic statistic) { // 参数：统计信息
      super(elementType, enumerable); // 调用父类构造方法
      this.field = field; // 保存字段
      this.statistic = statistic; // 保存统计信息
    } // 构造方法结束

    @Override public String toString() { // 重写toString方法
      return "Relation {field=" + field.getName() + "}"; // 返回包含字段名的字符串
    } // toString方法结束

    @Override public Statistic getStatistic() { // 重写方法：获取统计信息
      return statistic; // 返回statistic字段
    } // getStatistic方法结束

    @Override public Expression getExpression(SchemaPlus schema, // 重写方法：获取表的表达式
        String tableName, Class clazz) { // 参数：schema、表名、类
      ReflectiveSchema reflectiveSchema = // 声明ReflectiveSchema变量
          requireNonNull(schema.unwrap(ReflectiveSchema.class), // 解包schema获取ReflectiveSchema实例
              () -> "schema.unwrap(ReflectiveSchema.class) for " + schema); // 检查不为null
      return Expressions.field( // 创建字段访问表达式
          reflectiveSchema.getTargetExpression( // 获取目标对象的表达式
              schema.getParentSchema(), schema.getName()), // 传入父schema和当前schema名称
          field); // 指定要访问的字段
    } // getExpression方法结束
  } // FieldTable内部类结束

  /** Function that returns an array of a given object's field values.
   */ // 内部类文档注释：返回对象字段值数组的函数
  private static class FieldSelector implements Function1<Object, @Nullable Object[]> { // 定义FieldSelector内部静态类，实现Function1接口
    private final Field[] fields; // 成员变量：存储类的所有公共字段

    FieldSelector(Class elementType) { // 构造方法：接收元素类型（类）
      this.fields = elementType.getFields(); // 获取该类的所有公共字段并保存
    } // 构造方法结束

    @Override public @Nullable Object[] apply(Object o) { // 重写方法：应用函数，将对象转换为其字段值数组
      try { // 尝试执行可能抛出异常的代码
        final @Nullable Object[] objects = new Object[fields.length]; // 创建字段值数组
        for (int i = 0; i < fields.length; i++) { // 遍历所有字段
          objects[i] = fields[i].get(o); // 通过反射获取对象的字段值
        } // for循环结束
        return objects; // 返回字段值数组
      } catch (IllegalAccessException e) { // 捕获非法访问异常
        throw new RuntimeException(e); // 抛出运行时异常
      } // try-catch块结束
    } // apply方法结束
  } // FieldSelector内部类结束
} // ReflectiveSchema类结束