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
// Apache许可证声明，允许在遵守Apache 2.0许可证的前提下免费使用、修改和分发此代码
package org.apache.calcite.util; // 声明包名为org.apache.calcite.util，表示此测试类属于Calcite工具包

import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法

import java.math.BigDecimal; // 导入BigDecimal类，用于高精度数值计算

import static org.hamcrest.CoreMatchers.instanceOf; // 导入Hamcrest断言库的instanceOf匹配器，用于验证对象类型
import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest断言库的is匹配器，用于验证值相等
import static org.hamcrest.CoreMatchers.nullValue; // 导入Hamcrest断言库的nullValue匹配器，用于验证值为null
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest的断言方法，用于执行断言检查
import static org.hamcrest.Matchers.closeTo; // 导入Hamcrest断言库的closeTo匹配器，用于验证浮点数接近程度
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入JUnit 5的assertTrue断言方法，用于验证条件为真
import static org.junit.jupiter.api.Assertions.fail; // 导入JUnit 5的fail方法，用于显式标记测试失败

/**
 * ReflectVisitorTest类用于测试{@link ReflectUtil#invokeVisitor}和
 * {@link ReflectiveVisitor}的功能，并提供了一个如何使用它们的示例
 * 
 * 此测试类主要验证反射访问者模式(Reflective Visitor Pattern)的实现，
 * 该模式允许根据运行时对象的实际类型动态调用对应的处理方法
 * 
 * 核心功能包括：
 * 1. 测试反射方法调用的正确性
 * 2. 验证方法分派(dispatcher)机制
 * 3. 测试方法重载解析
 * 4. 验证歧义检测机制
 * 
 * 通过多个内部类演示了不同类型的访问者实现：
 * - CarelessNumberNegater：粗略的数值取反实现
 * - CarefulNumberNegater：精确的数值取反实现
 * - CluelessNumberNegater：错误的实现示例
 * - IndecisiveNumberNegater：展示歧义情况的实现
 * - SomewhatIndecisiveNumberNegater：展示如何解决歧义
 */
class ReflectVisitorTest { // 定义测试类ReflectVisitorTest
  /**
   * 测试CarelessNumberNegater类的功能
   * 
   * 此测试方法验证粗略的数值取反器能够正确处理整数输入
   * CarelessNumberNegater会将所有数值转换为double类型后取反，
   * 这种方式可能会丢失精度（如BigInteger）
   */
  @Test void testCarelessNegater() { // 使用@Test注解标记此方法为测试方法
    NumberNegater negater = new CarelessNumberNegater(); // 创建CarelessNumberNegater实例
    Number result; // 声明Number类型的变量用于存储取反结果

    // verify that negater is capable of handling integers // 验证取反器能够处理整数
    result = negater.negate(5); // 调用negate方法对整数5进行取反
    assertThat(result.intValue(), is(-5)); // 验证取反结果的整数值为-5
  }

  /**
   * 测试CarefulNumberNegater类的功能
   * 
   * 此测试方法验证精确的数值取反器能够：
   * 1. 正确处理整数并返回相同类型的Integer对象
   * 2. 继承父类CarelessNumberNegater的方法来处理long类型
   * 
   * CarefulNumberNegater为每种Number子类提供了专门的实现，
   * 确保返回值类型与输入类型一致
   */
  @Test void testCarefulNegater() { // 使用@Test注解标记此方法为测试方法
    NumberNegater negater = new CarefulNumberNegater(); // 创建CarefulNumberNegater实例
    Number result; // 声明Number类型的变量用于存储取反结果

    // verify that negater is capable of handling integers, // 验证取反器能够处理整数
    // and that result comes back with same type // 并且结果返回相同的类型
    result = negater.negate(5); // 调用negate方法对整数5进行取反
    assertThat(result.intValue(), is(-5)); // 验证取反结果的整数值为-5
    assertThat(result, instanceOf(Integer.class)); // 验证结果对象是Integer类型

    // verify that negater is capable of handling longs; // 验证取反器能够处理long类型
    // even though it doesn't provide an explicit implementation, // 即使它没有提供显式实现
    // it should inherit the one from CarelessNumberNegater // 它应该继承CarelessNumberNegater的方法
    result = negater.negate(5L); // 调用negate方法对long值5L进行取反
    assertThat(result.longValue(), is(-5L)); // 验证取反结果的long值为-5L
  }

  /**
   * 测试CluelessNumberNegater类的功能
   * 
   * 此测试方法验证层次根参数(hierarchyRoot)的作用：
   * 1. CluelessNumberNegater正确处理Short类型
   * 2. CluelessNumberNegater无法处理Integer类型，因为visit(Object)方法被忽略
   * 
   * 这个测试展示了hierarchyRoot参数如何过滤掉不相关的visit方法，
   * 即使存在visit(Object)方法，也不会被调用，因为它不在Number层次结构中
   */
  @Test void testCluelessNegater() { // 使用@Test注解标记此方法为测试方法
    NumberNegater negater = new CluelessNumberNegater(); // 创建CluelessNumberNegater实例
    Number result; // 声明Number类型的变量用于存储取反结果

    // verify that negater is capable of handling shorts, // 验证取反器能够处理short类型
    // and that result comes back with same type // 并且结果返回相同的类型
    result = negater.negate((short) 5); // 调用negate方法对short值5进行取反
    assertThat(result.shortValue(), is((short) -5)); // 验证取反结果的short值为-5
    assertThat(result, instanceOf(Short.class)); // 验证结果对象是Short类型

    // verify that negater is NOT capable of handling integers // 验证取反器无法处理整数
    result = negater.negate(5); // 调用negate方法对整数5进行取反
    assertThat(result, nullValue()); // 验证结果为null，因为visit(Object)方法被忽略
  }

  /**
   * 测试方法查找中的歧义检测机制
   * 
   * 此测试方法验证当多个visit方法匹配同一个参数类型时，
   * 反射访问者模式能够检测到歧义并抛出IllegalArgumentException
   * 
   * AmbiguousNumber同时实现了CrunchableNumber和FudgeableNumber接口，
   * IndecisiveNumberNegater提供了visit(CrunchableNumber)和visit(FudgeableNumber)方法，
   * 导致方法调用时出现歧义
   */
  @Test void testAmbiguity() { // 使用@Test注解标记此方法为测试方法
    NumberNegater negater = new IndecisiveNumberNegater(); // 创建IndecisiveNumberNegater实例
    Number result; // 声明Number类型的变量用于存储取反结果

    try { // 尝试执行可能抛出异常的代码块
      result = negater.negate(new AmbiguousNumber()); // 调用negate方法处理AmbiguousNumber对象
    } catch (IllegalArgumentException ex) { // 捕获IllegalArgumentException异常
      // expected // 这是预期的异常
      assertTrue(ex.getMessage().contains("ambiguity")); // 验证异常消息包含"ambiguity"字符串
      return; // 测试通过，提前返回
    }
    fail("Expected failure due to ambiguity"); // 如果没有抛出异常，测试失败
  }

  /**
   * 测试当存在更好的匹配方法时，歧义检测不会触发
   * 
   * 此测试方法验证方法解析的优先级机制：
   * - SomewhatAmbiguousNumber实现了DiceyNumber接口（继承自FudgeableNumber）
   * - SomewhatIndecisiveNumberNegater提供了visit(FudgeableNumber)和visit(AmbiguousNumber)方法
   * - visit(AmbiguousNumber)是更精确的匹配，因此会被选择
   * - 即使存在多个可能的匹配，最精确的方法会被调用，不会产生歧义
   */
  @Test void testNonAmbiguity() { // 使用@Test注解标记此方法为测试方法
    NumberNegater negater = new SomewhatIndecisiveNumberNegater(); // 创建SomewhatIndecisiveNumberNegater实例
    Number result; // 声明Number类型的变量用于存储取反结果

    result = negater.negate(new SomewhatAmbiguousNumber()); // 调用negate方法处理SomewhatAmbiguousNumber对象
    assertThat(result.doubleValue(), closeTo(0.0, 0.001)); // 验证取反结果接近0.0（误差在0.001以内）
  }

  //~ Inner Interfaces -------------------------------------------------------

  /**
   * CrunchableNumber接口用于在类层次结构中引入歧义
   * 
   * 此接口是一个标记接口(marker interface)，不定义任何方法
   * 主要用于测试反射访问者模式在处理多重继承时的歧义检测
   */
  public interface CrunchableNumber { // 定义公共接口CrunchableNumber
  } // 接口体为空，这是一个标记接口

  /**
   * FudgeableNumber接口用于在类层次结构中引入歧义
   * 
   * 此接口是一个标记接口(marker interface)，不定义任何方法
   * 与CrunchableNumber接口一起用于测试多重继承导致的歧义情况
   */
  public interface FudgeableNumber { // 定义公共接口FudgeableNumber
  } // 接口体为空，这是一个标记接口

  /** Sub-interface of {@link FudgeableNumber}. */ // DiceyNumber是FudgeableNumber的子接口
  public interface DiceyNumber extends FudgeableNumber { // 定义公共接口DiceyNumber，继承自FudgeableNumber
  } // 接口体为空，用于测试接口继承层次

  //~ Inner Classes ----------------------------------------------------------

  /**
   * NumberNegater定义了能够对任意数值进行取反计算的抽象基类
   * 
   * 子类通过发布签名为"void visit(X x)"的方法来实现计算逻辑，
   * 其中X是Number的子类
   * 
   * 此类实现了ReflectiveVisitor接口，支持反射访问者模式
   * 核心功能：
   * 1. 使用ReflectiveVisitDispatcher缓存适用的visit方法
   * 2. 根据输入Number的实际类型动态调用对应的visit方法
   * 3. 支持层次根参数过滤不相关的visit方法
   */
  public abstract class NumberNegater implements ReflectiveVisitor { // 定义抽象内部类NumberNegater，实现ReflectiveVisitor接口
    protected Number result; // 声明受保护的Number类型成员变量result，用于存储取反结果
    private final ReflectiveVisitDispatcher<NumberNegater, Number> dispatcher = // 声明私有的最终类型成员变量dispatcher，类型为ReflectiveVisitDispatcher
        ReflectUtil.createDispatcher( // 调用ReflectUtil.createDispatcher方法创建分派器
            NumberNegater.class, // 传入访问者类类型NumberNegater.class
            Number.class); // 传入层次根类型Number.class，用于过滤visit方法

    /**
     * 对给定的数值进行取反操作
     * 
     * 此方法使用反射访问者模式，根据输入数值的实际类型动态调用对应的visit方法
     * 
     * @param n 需要取反的数值对象
     * @return 取反后的结果；不保证与n的具体类型相同；如果n的类型未被处理则返回null
     */
    public Number negate(Number n) { // 定义公共方法negate，接收Number类型参数n
      // we specify Number.class as the hierarchy root so // 我们指定Number.class作为层次根，以便
      // that extraneous visit methods are ignored // 忽略多余的visit方法
      result = null; // 将result初始化为null，表示尚未处理
      dispatcher.invokeVisitor( // 调用dispatcher的invokeVisitor方法执行反射调用
          this, // 传入当前对象作为访问者
          n, // 传入数值对象作为被访问对象
          "visit"); // 传入方法名"visit"
      return result; // 返回取反结果
    }

    /**
     * 对给定的数值进行取反操作，但不使用分派器对象缓存适用的方法
     * 
     * 此方法直接调用ReflectUtil.invokeVisitor，每次都需要查找适用的visit方法
     * 结果应该与{@link #negate(Number)}方法相同
     * 
     * @param n 需要取反的数值对象
     * @return 取反后的结果；不保证与n的具体类型相同；如果n的类型未被处理则返回null
     */
    public Number negateWithoutDispatcher(Number n) { // 定义公共方法negateWithoutDispatcher，接收Number类型参数n
      // we specify Number.class as the hierarchy root so // 我们指定Number.class作为层次根，以便
      // that extraneous visit methods are ignored // 忽略多余的visit方法
      result = null; // 将result初始化为null，表示尚未处理
      ReflectUtil.invokeVisitor( // 直接调用ReflectUtil的静态方法invokeVisitor执行反射调用
          this, // 传入当前对象作为访问者
          n, // 传入数值对象作为被访问对象
          Number.class, // 传入层次根类型Number.class
          "visit"); // 传入方法名"visit"
      return result; // 返回取反结果
    }
  }

  /**
   * CarelessNumberNegater以粗略的方式实现NumberNegater
   * 
   * 此类将输入转换为double类型然后取反，这种方式对于BigInteger等类型可能会丢失精度
   * 
   * 特点：
   * 1. 只提供一个visit(Number)方法，处理所有Number类型
   * 2. 将所有数值转换为double，可能导致精度丢失
   * 3. 作为基类，可以被子类继承以提供更精确的实现
   */
  public class CarelessNumberNegater extends NumberNegater { // 定义公共内部类CarelessNumberNegater，继承自NumberNegater
    public void visit(Number n) { // 定义公共方法visit，接收Number类型参数n
      result = -n.doubleValue(); // 将数值转换为double后取反，赋值给result成员变量
    } // 方法结束
  } // 类结束

  /**
   * CarefulNumberNegater以精确的方式实现NumberNegater
   * 
   * 此类为每个已知的Number子类提供重载方法，并返回相同子类的结果
   * 继承自CarelessNumberNegater以便仍能处理未知类型的Number
   * 
   * 特点：
   * 1. 为每种Number子类提供专门的visit方法
   * 2. 确保返回值类型与输入类型一致
   * 3. 对于未提供专门方法的类型，会使用父类的实现
   * 4. 使用断言验证返回值的类型正确性
   */
  public class CarefulNumberNegater extends CarelessNumberNegater { // 定义公共内部类CarefulNumberNegater，继承自CarelessNumberNegater
    public void visit(Integer i) { // 定义公共方法visit，接收Integer类型参数i
      result = -i; // 对整数i进行取反，赋值给result成员变量
      assert result instanceof Integer; // 断言result是Integer类型，确保类型安全
    } // 方法结束

    public void visit(Short s) { // 定义公共方法visit，接收Short类型参数s
      result = -s; // 对short值s进行取反，赋值给result成员变量
      assert result instanceof Short; // 断言result是Short类型，确保类型安全
    } // 方法结束

    // ... imagine implementations for other Number subclasses here ... // ... 想象这里还有其他Number子类的实现 ...
  } // 类结束

  /**
   * CluelessNumberNegater以一种非常错误的方式实现NumberNegater
   * 
   * 此类对Short类型做正确的处理，但试图重写visit(Object)方法
   * 这仅用于测试invokeVisitor的hierarchyRoot参数
   * 
   * 特点：
   * 1. visit(Object)方法会被忽略，因为它不在Number层次结构中
   * 2. visit(Short)方法会被正确调用
   * 3. 对于其他Number类型，返回null（因为没有匹配的visit方法）
   * 4. 演示了hierarchyRoot参数如何过滤不相关的方法
   */
  public class CluelessNumberNegater extends NumberNegater { // 定义公共内部类CluelessNumberNegater，继承自NumberNegater
    public void visit(Object obj) { // 定义公共方法visit，接收Object类型参数obj
      result = 42; // 设置result为42，但此方法不会被调用（因为Object不在Number层次结构中）
    } // 方法结束

    public void visit(Short s) { // 定义公共方法visit，接收Short类型参数s
      result = (short) -s; // 对short值s进行取反，强制转换为short后赋值给result
      assert result instanceof Short; // 断言result是Short类型，确保类型安全
    } // 方法结束
  } // 类结束

  /**
   * IndecisiveNumberNegater以一种犹豫不决的方式实现NumberNegater
   * 
   * 当遇到AmbiguousNumber时，它不知道该做什么，因为存在两个匹配的visit方法
   * 
   * 特点：
   * 1. 提供visit(CrunchableNumber)和visit(FudgeableNumber)两个方法
   * 2. 当输入同时实现这两个接口时，会产生歧义
   * 3. 用于测试反射访问者模式的歧义检测机制
   * 4. 两个visit方法都是空实现，不设置result值
   */
  public class IndecisiveNumberNegater extends NumberNegater { // 定义公共内部类IndecisiveNumberNegater，继承自NumberNegater
    public void visit(CrunchableNumber n) { // 定义公共方法visit，接收CrunchableNumber类型参数n
    } // 方法体为空，不执行任何操作

    public void visit(FudgeableNumber n) { // 定义公共方法visit，接收FudgeableNumber类型参数n
    } // 方法体为空，不执行任何操作
  } // 类结束

  /**
   * SomewhatIndecisiveNumberNegater以一种稍微犹豫的方式实现NumberNegater
   * 
   * 当遇到SomewhatAmbiguousNumber时，它知道该做什么，因为存在更精确的匹配
   * 
   * 特点：
   * 1. 提供visit(FudgeableNumber)和visit(AmbiguousNumber)两个方法
   * 2. visit(AmbiguousNumber)是更精确的匹配，会被优先选择
   * 3. 演示了方法解析的优先级机制
   * 4. 对于AmbiguousNumber类型，能够正确取反并返回Double类型结果
   */
  public class SomewhatIndecisiveNumberNegater extends NumberNegater { // 定义公共内部类SomewhatIndecisiveNumberNegater，继承自NumberNegater
    public void visit(FudgeableNumber n) { // 定义公共方法visit，接收FudgeableNumber类型参数n
    } // 方法体为空，不执行任何操作

    public void visit(AmbiguousNumber n) { // 定义公共方法visit，接收AmbiguousNumber类型参数n
      result = -n.doubleValue(); // 将AmbiguousNumber转换为double后取反，赋值给result
      assert result instanceof Double; // 断言result是Double类型，确保类型安全
    } // 方法结束
  } // 类结束

  /**
   * AmbiguousNumber类继承两个接口，导致歧义
   * 
   * 此类同时实现CrunchableNumber和FudgeableNumber接口，
   * 当IndecisiveNumberNegater处理此类型时会产生歧义
   * 
   * 特点：
   * 1. 继承自BigDecimal，具有高精度数值计算能力
   * 2. 同时实现两个接口，用于测试多重继承导致的歧义
   * 3. 构造函数初始化为0值
   * 4. 用于测试反射访问者模式的歧义检测
   */
  public class AmbiguousNumber extends BigDecimal // 定义公共内部类AmbiguousNumber，继承自BigDecimal
      implements CrunchableNumber, FudgeableNumber { // 同时实现CrunchableNumber和FudgeableNumber接口
    AmbiguousNumber() { // 定义默认构造函数
      super("0"); // 调用父类BigDecimal的构造函数，初始化值为"0"
    } // 构造函数结束
  } // 类结束

  /**
   * SomewhatAmbiguousNumber类以两种不同的方式继承同一个根接口(FudgeableNumber)
   * 
   * 在某些情况下不应导致歧义，因为存在更精确的匹配方法
   * 
   * 特点：
   * 1. 继承自AmbiguousNumber，间接实现了CrunchableNumber和FudgeableNumber
   * 2. 直接实现DiceyNumber接口（DiceyNumber继承自FudgeableNumber）
   * 3. 通过两种方式间接实现了FudgeableNumber接口
   * 4. 用于测试方法解析的优先级，验证最精确的方法会被选择
   * 5. 当SomewhatIndecisiveNumberNegater处理此类型时，会调用visit(AmbiguousNumber)方法
   */
  public class SomewhatAmbiguousNumber extends AmbiguousNumber // 定义公共内部类SomewhatAmbiguousNumber，继承自AmbiguousNumber
      implements DiceyNumber { // 实现DiceyNumber接口
  } // 类结束
} // ReflectVisitorTest类结束
