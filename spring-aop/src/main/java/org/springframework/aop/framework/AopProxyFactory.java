package org.springframework.aop.framework;

/**
 * AopProxyFactory 接口：AOP 代理工厂，用于根据配置信息创建对应的 AopProxy 实例。
 * 不同的实现类可以选择使用 JDK 动态代理或 CGLIB 来创建代理。
 */
public interface AopProxyFactory {

	/**
	 * 根据提供的 AOP 配置信息（AdvisedSupport）创建 AopProxy 对象。
	 *
	 * @param config AOP 代理的配置信息，包含目标对象、拦截器链、是否需要代理接口等信息
	 * @return 创建好的 AopProxy 实例（实际代理对象由 AopProxy.getProxy() 返回）
	 * @throws AopConfigException 如果配置信息无效或创建代理失败，抛出该异常
	 */
	AopProxy createAopProxy(AdvisedSupport config) throws AopConfigException;

}

