package org.springframework.aop.framework;

import org.springframework.lang.Nullable;

/**
 * AOP 代理接口，定义了获取代理对象的方法。
 * 实现类可以基于 JDK 动态代理或 CGLIB 创建代理对象。
 */
public interface AopProxy {

	/**
	 * 获取当前 AOP 代理对象。
	 * 通常会使用默认的类加载器来创建代理。
	 *
	 * @return 生成的代理对象
	 */
	Object getProxy();

	/**
	 * 使用指定的类加载器来获取代理对象。
	 * 当默认类加载器不适用时（如模块隔离、不同类加载器环境下）可使用该方法。
	 *
	 * @param classLoader 指定用于生成代理对象的类加载器，可以为 null
	 * @return 生成的代理对象
	 */
	Object getProxy(@Nullable ClassLoader classLoader);
}

