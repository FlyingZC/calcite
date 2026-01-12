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
 */ // Apache许可证声明：本文件遵循Apache 2.0许可证，允许在遵守许可证条款的前提下使用、修改和分发
package org.apache.calcite.linq4j.tree; // 包声明：类声明查找器所在的包路径

import org.apache.calcite.linq4j.function.Function1; // 导入函数式接口，用于创建工厂函数

import org.checkerframework.checker.nullness.qual.Nullable; // 导入空值检查注解，用于标记可能为空的返回值

import java.lang.reflect.Constructor; // 导入构造器类，用于通过反射创建实例
import java.lang.reflect.InvocationTargetException; // 导入反射调用异常类，用于处理反射调用时的异常
import java.util.ArrayList; // 导入动态数组类，用于存储成员声明列表
import java.util.List; // 导入列表接口，用于定义成员声明的集合类型

/**
 * 类声明查找器（ClassDeclarationFinder）：优化器的入口点，用于将确定性表达式提取为final static字段
 * Entry point for optimizers that factor out deterministic expressions to
 * final static fields.
 * 这个类的主要作用是在表达式树遍历过程中，识别出可以优化的确定性表达式，并将它们提取为类的静态final字段
 * 这样可以避免重复计算相同的表达式，提高代码执行效率
 * 该类继承自Shuttle（访问器模式），用于遍历和修改表达式树
 * 
 * 重要说明：此类的实例不应被重用，每次优化新的表达式树时都应创建新的访问器实例
 * Instances of this class should not be reused, so new visitor should be
 * created for optimizing a new expression tree.
 * 
 * 工作原理：
 * 1. 遍历表达式树，识别类声明（ClassDeclaration）和新表达式（NewExpression）
 * 2. 对于每个类声明，创建一个子访问器来处理该类的成员
 * 3. 识别类中的确定性表达式（如常量计算）
 * 4. 将这些确定性表达式提取为final static字段
 * 5. 在后续使用这些表达式的地方，替换为对这些字段的引用
 */
public class ClassDeclarationFinder extends Shuttle { // 类声明查找器，继承自Shuttle访问器基类
  protected final @Nullable ClassDeclarationFinder parent; // 父级访问器，用于处理嵌套类时的层级关系；如果是顶级类，则为null；通过父级访问器可以将优化后的字段提升到外层类中

  /**
   * The list of new final static fields to be added to the current class.
   * 要添加到当前类的新final static字段列表
   * 这个列表存储了在优化过程中识别出的所有需要提取为静态final字段的成员声明
   * 这些字段通常是确定性表达式的结果，提取后可以避免重复计算
   */
  protected final List<MemberDeclaration> addedDeclarations = new ArrayList<>(); // 新增的成员声明列表，用于存储优化过程中提取的final static字段

  private final Function1<ClassDeclarationFinder, ClassDeclarationFinder> childFactory; // 子访问器工厂函数，用于创建处理嵌套类的子访问器；该函数接受当前访问器作为参数，返回一个新的子访问器实例

  private static final Function1<ClassDeclarationFinder, // 默认的子访问器工厂函数，用于创建DeterministicCodeOptimizer实例作为子访问器
      ClassDeclarationFinder> DEFAULT_CHILD_FACTORY = // 默认工厂，当没有指定自定义优化器时使用
      DeterministicCodeOptimizer::new; // 使用方法引用创建DeterministicCodeOptimizer实例，这是默认的确定性代码优化器

  /**
   * Creates visitor that uses default optimizer.
   * 创建使用默认优化器的访问器
   * 这是一个工厂方法，用于创建ClassDeclarationFinder实例，使用默认的DeterministicCodeOptimizer作为优化器
   * 
   * @return optimizing visitor 返回一个配置了默认优化器的访问器实例
   */
  public static ClassDeclarationFinder create() { // 静态工厂方法，创建使用默认优化器的访问器
    return create(DEFAULT_CHILD_FACTORY); // 调用重载的create方法，传入默认的子访问器工厂
  }

  /**
   * Creates visitor that uses given class as optimizer.
   * 创建使用指定类作为优化器的访问器
   * 这个方法允许用户自定义优化器类，该类必须继承自ClassDeclarationFinder并支持ClassDeclarationFinder类型的构造器
   * 
   * The implementation should support ({@code ClassDeclarationFinder})
   * constructor.
   * 实现类必须支持接受ClassDeclarationFinder参数的构造器
   * 
   * @param optimizingClass class that implements optimizations 实现优化的类，必须继承自ClassDeclarationFinder
   * @return optimizing visitor 返回使用指定优化器类的访问器实例
   */
  public static ClassDeclarationFinder create( // 静态工厂方法，创建使用自定义优化器类的访问器
      final Class<? extends ClassDeclarationFinder> optimizingClass) { // 参数：自定义的优化器类
    return create(newChildCreator(optimizingClass)); // 调用重载的create方法，传入根据指定类创建的子访问器工厂
  }

  /**
   * Creates visitor that uses given factory to create optimizers.
   * 创建使用给定工厂创建优化器的访问器
   * 这个方法提供了最大的灵活性，允许用户完全控制子访问器的创建过程
   * 
   * @param childFactory factory that creates optimizers 用于创建优化器的工厂函数
   * @return optimizing visitor 返回使用指定工厂的访问器实例
   */
  public static ClassDeclarationFinder create( // 静态工厂方法，创建使用自定义工厂的访问器
      Function1<ClassDeclarationFinder, ClassDeclarationFinder> childFactory) { // 参数：子访问器工厂函数
    return new ClassDeclarationFinder(childFactory); // 创建ClassDeclarationFinder实例，传入子访问器工厂
  }

  /**
   * Creates factory that creates instances of optimizing visitors.
   * 创建用于创建优化器访问器实例的工厂
   * 这个方法通过反射获取指定类的构造器，然后返回一个工厂函数，该函数可以创建该类的实例
   * 
   * The implementation should support ({@code ClassDeclarationFinder})
   * constructor.
   * 实现类必须支持接受ClassDeclarationFinder参数的构造器
   * 
   * @param optimizingClass class that implements optimizations 实现优化的类，必须继承自ClassDeclarationFinder
   * @return factory that creates instances of given classes 返回一个工厂函数，用于创建指定优化器类的实例
   */
  private static Function1<ClassDeclarationFinder, ClassDeclarationFinder> newChildCreator( // 私有静态方法，创建子访问器创建器
      Class<? extends ClassDeclarationFinder> optimizingClass) { // 参数：自定义的优化器类
    try { // 尝试获取构造器
      final Constructor<? extends ClassDeclarationFinder> constructor = // 通过反射获取接受ClassDeclarationFinder参数的构造器
          optimizingClass.getConstructor(ClassDeclarationFinder.class); // 获取指定参数类型的构造器
      return a0 -> { // 返回一个lambda表达式作为工厂函数，接受父访问器参数
        try { // 尝试创建实例
          return constructor.newInstance(a0); // 使用构造器创建新的优化器实例，传入父访问器
        } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) { // 捕获反射调用时的各种异常
          throw new IllegalStateException( // 如果创建失败，抛出非法状态异常
              "Unable to create optimizer via " + constructor, e); // 异常信息说明无法通过指定构造器创建优化器
        }
      };
    } catch (NoSuchMethodException e) { // 捕获找不到指定构造器的异常
      throw new IllegalArgumentException("Given class " + optimizingClass // 抛出非法参数异常，说明类不支持所需构造器
          + "does not support (ClassDeclarationFinder) constructor", e); // 异常信息说明给定的类不支持ClassDeclarationFinder构造器
    }
  }

  /**
   * Creates optimizer with no parent.
   * 创建没有父级的优化器（顶级优化器）
   * 这是一个私有构造器，用于创建最顶层的优化器实例，没有父级访问器
   * 这个构造器通过create()静态方法调用
   * 
   */
  private ClassDeclarationFinder( // 私有构造器，创建顶级优化器
      Function1<ClassDeclarationFinder, ClassDeclarationFinder> childFactory) { // 参数：子访问器工厂函数
    this.parent = null; // 设置父级访问器为null，表示这是顶级优化器
    this.childFactory = childFactory; // 保存子访问器工厂，用于创建处理嵌套类的子访问器
  }

  /**
   * Creates a child optimizer.
   * 创建子级优化器
   * 这个构造器用于创建处理嵌套类的子访问器，每个类声明都会创建一个子优化器
   * 子优化器会继承父优化器的子访问器工厂，保持一致的行为
   * 
   * Typically a child is created for each class declaration,
   * so each optimizer collects fields for exactly one class.
   * 通常为每个类声明创建一个子优化器，因此每个优化器只为一个类收集字段
   * 
   * @param parent parent optimizer 父级优化器访问器
   */
  protected ClassDeclarationFinder(ClassDeclarationFinder parent) { // 保护构造器，创建子优化器
    this.parent = parent; // 保存父级访问器引用，用于将优化后的字段提升到外层类
    this.childFactory = parent.childFactory; // 从父级访问器继承子访问器工厂，保持一致的行为
  }

  /**
   * Creates optimizer local to the newly generated anonymous class.
   * 创建专门用于新生成的匿名类的本地优化器
   * 当访问NewExpression（新表达式）时，如果该表达式包含成员声明（即匿名类），则创建一个子访问器来处理
   * 
   * @param newExpression expression to optimize 要优化的新表达式
   * @return nested visitor if anonymous class is given 如果是匿名类则返回嵌套访问器，否则返回当前访问器
   */
  @Override public Shuttle preVisit(NewExpression newExpression) { // 重写preVisit方法，处理新表达式的前置访问
    if (newExpression.memberDeclarations == null) { // 检查新表达式是否包含成员声明
      return this; // 如果没有成员声明（不是匿名类），返回当前访问器继续处理
    }
    ClassDeclarationFinder visitor = goDeeper(); // 创建子访问器，用于处理匿名类的成员
    visitor.learnFinalStaticDeclarations(newExpression.memberDeclarations); // 让子访问器学习现有的final static声明，避免重复创建
    return visitor; // 返回子访问器，用于处理匿名类的内部表达式
  }

  /**
   * Creates optimizer local to the newly generated class.
   * 创建专门用于新生成的类的本地优化器
   * 当访问ClassDeclaration（类声明）时，创建一个子访问器来处理该类的成员
   * 
   * @param classDeclaration expression to optimize 要优化的类声明表达式
   * @return nested visitor 返回嵌套访问器
   */
  @Override public Shuttle preVisit(ClassDeclaration classDeclaration) { // 重写preVisit方法，处理类声明的前置访问
    ClassDeclarationFinder visitor = goDeeper(); // 创建子访问器，用于处理类的成员
    visitor.learnFinalStaticDeclarations(classDeclaration.memberDeclarations); // 让子访问器学习现有的final static声明，避免重复创建
    return visitor; // 返回子访问器，用于处理类内部的成员声明
  }

  @Override public Expression visit(NewExpression newExpression, // 重写visit方法，处理新表达式的访问
      List<Expression> arguments, @Nullable List<MemberDeclaration> memberDeclarations) { // 参数：构造函数参数列表和成员声明列表
    if (parent == null) { // 检查是否有父级访问器
      // Unable to optimize since no wrapper class exists to put fields to.
      // 无法优化，因为没有包装类可以放置字段
      arguments = newExpression.arguments; // 如果没有父级，保持参数不变，因为没有地方可以放置提取的字段
    } else if (memberDeclarations != null) { // 检查是否有成员声明（匿名类）
      // Arguments to new Test(1+2) { ... } should be optimized via parent
      // optimizer.
      // new Test(1+2) { ... } 的参数应该通过父级优化器进行优化
      arguments = Expressions.acceptExpressions(newExpression.arguments, parent); // 使用父级访问器优化构造函数参数，将参数中的确定性表达式提取到父类
    }

    Expression result = // 调用父类的visit方法，处理表达式树的其他部分
        super.visit(newExpression, arguments, memberDeclarations); // 传递优化后的参数和成员声明

    if (memberDeclarations == null) { // 检查是否为普通对象创建（没有成员声明）
      return tryOptimizeNewInstance((NewExpression) result); // 尝试优化普通对象创建表达式（子类可以重写此方法实现具体优化逻辑）
    }

    memberDeclarations = optimizeDeclarations(memberDeclarations); // 优化成员声明列表，添加提取的final static字段
    return super.visit((NewExpression) result, arguments, // 再次调用父类的visit方法，处理优化后的成员声明
        memberDeclarations); // 返回优化后的表达式
  }

  /**
   * Processes the list of declarations when class expression detected.
   * 当检测到类表达式时，处理成员声明列表
   * 这个方法允许子类在开始优化之前学习现有的final static字段声明，以便可以重用这些字段而不是创建新的
   * 
   * Sub-classes might figure out the existing fields for reuse.
   * 子类可以找出现有的字段以便重用
   * 
   * @param memberDeclarations list of declarations to process. 要处理的声明列表
   */
  protected void learnFinalStaticDeclarations( // 保护方法，用于学习现有的final static声明
      List<MemberDeclaration> memberDeclarations) { // 参数：成员声明列表
  } // 默认实现为空，子类可以重写此方法来记录现有的字段声明

  /**
   * Optimizes {@code new Type()} constructs.
   * 优化 {@code new Type()} 构造表达式
   * 这个方法用于优化普通的对象创建表达式（非匿名类），子类可以重写此方法实现具体的优化逻辑
   * 
   * @param newExpression expression to optimize 要优化的表达式
   * @return always returns un-optimized expression 总是返回未优化的表达式（默认实现）
   */
  protected Expression tryOptimizeNewInstance(NewExpression newExpression) { // 保护方法，尝试优化普通对象创建
    return newExpression; // 默认实现不进行任何优化，直接返回原表达式
  }

  @Override public ClassDeclaration visit(ClassDeclaration classDeclaration, // 重写visit方法，处理类声明的访问
      List<MemberDeclaration> memberDeclarations) { // 参数：成员声明列表
    memberDeclarations = optimizeDeclarations(memberDeclarations); // 优化成员声明，将提取的final static字段添加到列表中
    return super.visit(classDeclaration, memberDeclarations); // 调用父类的visit方法，返回优化后的类声明
  }

  /**
   * Adds new declarations (e.g. final static fields) to the list of existing
   * ones.
   * 将新的声明（如final static字段）添加到现有声明列表中
   * 这个方法将优化过程中提取的所有新字段添加到类的成员声明列表中
   * 
   * @param memberDeclarations existing list of declarations 现有的声明列表
   * @return new list of declarations or the same if no modifications required 返回新的声明列表，如果没有修改则返回原列表
   */
  protected List<MemberDeclaration> optimizeDeclarations( // 保护方法，优化成员声明列表
      List<MemberDeclaration> memberDeclarations) { // 参数：原始的成员声明列表
    if (addedDeclarations.isEmpty()) { // 检查是否有需要添加的新声明
      return memberDeclarations; // 如果没有新声明，直接返回原列表
    }
    List<MemberDeclaration> newDecls = // 创建新的列表，容量为原列表大小加上新声明数量
        new ArrayList<>(memberDeclarations.size() // 计算新列表的初始容量，避免扩容
            + addedDeclarations.size()); // 加上新声明的数量
    newDecls.addAll(memberDeclarations); // 添加所有原始声明
    newDecls.addAll(addedDeclarations); // 添加所有新提取的final static字段声明
    return newDecls; // 返回包含原始声明和新声明的完整列表
  }

  /**
   * Verifies if the expression is effectively constant.
   * 验证表达式是否实际上是常量（确定性表达式）
   * 常量表达式是指在程序运行期间值不会改变的表达式，如字面量、final变量的引用等
   * 
   * This method should be overridden in sub-classes.
   * 这个方法应该在子类中重写，以实现具体的常量检测逻辑
   * 
   * @param expression expression to test 要测试的表达式
   * @return always returns false 默认总是返回false（子类应重写此方法）
   */
  protected boolean isConstant(Expression expression) { // 保护方法，检查表达式是否为常量
    return false; // 默认实现返回false，表示不确定是否为常量，子类应重写此方法
  }

  /**
   * Verifies if all the expressions in given list are  effectively constant.
   * 验证给定列表中的所有表达式是否实际上都是常量
   * 这个方法遍历表达式列表，检查每个表达式是否都是常量
   * 
   * @param list list of expressions to test 要测试的表达式列表
   * @return true when all the expressions are known to be constant 当所有表达式都是常量时返回true
   */
  protected boolean isConstant(Iterable<? extends Expression> list) { // 保护方法，检查表达式列表是否全部为常量
    for (Expression expression : list) { // 遍历列表中的每个表达式
      if (!isConstant(expression)) { // 检查当前表达式是否为常量
        return false; // 如果发现任何非常量表达式，立即返回false
      }
    }
    return true; // 如果所有表达式都是常量，返回true
  }

  /**
   * Finds if there exists ready for reuse declaration for given expression.
   * 查找是否存在可以重用的声明来表示给定的表达式
   * 这个方法用于在提取final static字段时，检查是否已经存在相同的字段可以重用，避免重复创建
   * 
   * This method should be overridden in sub-classes.
   * 这个方法应该在子类中重写，以实现具体的字段查找逻辑
   * 
   * @param expression input expression 输入表达式
   * @return always returns null 默认总是返回null（子类应重写此方法）
   */
  protected @Nullable ParameterExpression findDeclaredExpression(Expression expression) { // 保护方法，查找可重用的声明
    return null; // 默认实现返回null，表示没有找到可重用的声明，子类应重写此方法
  }

  /**
   * Verifies if the variable name is already in use.
   * 验证变量名是否已被使用
   * 这个方法用于在创建新的final static字段时，检查字段名是否与现有字段冲突
   * 
   * This method should be overridden in sub-classes.
   * 这个方法应该在子类中重写，以实现具体的名称检查逻辑
   * 
   * @param name name of the variable to test 要测试的变量名
   * @return always returns false 默认总是返回false（子类应重写此方法）
   */
  protected boolean hasField(String name) { // 保护方法，检查字段名是否已存在
    return false; // 默认实现返回false，表示名称未被使用，子类应重写此方法
  }

  /**
   * Creates child visitor. It is used to traverse nested class declarations.
   * 创建子访问器，用于遍历嵌套的类声明
   * 当遇到嵌套类或匿名类时，需要创建一个新的访问器实例来处理该类的成员，以保持每个类的优化上下文独立
   * 
   * @return new {@code Visitor} that is used to optimize class declarations 返回用于优化类声明的新访问器实例
   */
  protected ClassDeclarationFinder goDeeper() { // 保护方法，创建子访问器
    return childFactory.apply(this); // 使用子访问器工厂创建新的访问器实例，传入当前访问器作为父级
  }
} // 类定义结束
