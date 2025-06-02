package org.aopalliance.intercept;

import java.lang.reflect.AccessibleObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 表示一个连接点（Joinpoint），是AOP联盟中的核心接口。
 * Spring AOP 中的连接点通常指的是方法调用。
 */
public interface Joinpoint {

	/**
	 * 执行连接点，调用下一个拦截器链或目标方法本身。
	 *
	 * @return 方法执行后的返回值
	 * @throws Throwable 方法执行过程中可能抛出的异常
	 */
	@Nullable
	Object proceed() throws Throwable;

	/**
	 * 返回当前被代理的对象（即目标对象），可能为 null。
	 *
	 * @return 目标对象（this），或 null
	 */
	@Nullable
	Object getThis();

	/**
	 * 返回静态部分的对象信息（方法或构造器），不会改变。
	 * 例如，对于方法拦截来说，就是 Method 对象。
	 *
	 * @return 方法或构造器的静态信息
	 */
	@Nonnull
	AccessibleObject getStaticPart();
}

