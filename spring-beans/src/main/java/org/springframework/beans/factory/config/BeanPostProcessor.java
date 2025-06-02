package org.springframework.beans.factory.config;

import org.springframework.beans.BeansException;
import org.springframework.lang.Nullable;

/**
 * BeanPostProcessor 是 Spring 提供的一个扩展点接口，
 * 允许开发者在 Spring 容器实例化 bean 之后、初始化前后进行自定义处理。
 * 通常用于修改 bean 的属性、包装 bean 或执行某些初始化逻辑。
 */
public interface BeanPostProcessor {

	/**
	 * 在 bean 初始化方法（如 @PostConstruct 或 afterPropertiesSet）调用之前执行。
	 * 可用于修改 bean 的属性或执行自定义逻辑。
	 *
	 * @param bean     当前正在初始化的 bean 实例
	 * @param beanName 当前 bean 在容器中的名称
	 * @return 处理后的 bean 实例，或者原始的 bean 实例
	 * @throws BeansException 可以抛出异常终止 bean 的创建
	 */
	@Nullable
	default Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	/**
	 * 在 bean 初始化方法执行之后执行。
	 * 可用于为 bean 添加代理（如 AOP）、动态增强等操作。
	 *
	 * @param bean     当前已经初始化完成的 bean 实例
	 * @param beanName 当前 bean 在容器中的名称
	 * @return 处理后的 bean 实例，或者原始的 bean 实例
	 * @throws BeansException 可以抛出异常终止 bean 的创建
	 */
	@Nullable
	default Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}
}

