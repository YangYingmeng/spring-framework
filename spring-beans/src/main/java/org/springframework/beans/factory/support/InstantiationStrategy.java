package org.springframework.beans.factory.support;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.lang.Nullable;

/**
 * Bean 实例化策略接口。
 * 用于定义不同的对象创建方式，Spring 容器在实例化 Bean 时会使用该策略。
 * <p>
 * 实现类可以选择不同的方式来创建 Bean 实例，比如使用默认构造函数、指定构造函数，或通过工厂方法。
 */
public interface InstantiationStrategy {

	/**
	 * 使用默认无参构造函数实例化 Bean。
	 *
	 * @param bd       Bean 定义信息，包含类名、构造参数等元数据
	 * @param beanName Bean 的名称（可为空）
	 * @param owner    所属的 BeanFactory
	 * @return 创建的 Bean 实例
	 * @throws BeansException 如果实例化过程中出现错误
	 */
	Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner)
			throws BeansException;

	/**
	 * 使用指定的构造函数和参数实例化 Bean。
	 *
	 * @param bd       Bean 定义信息
	 * @param beanName Bean 的名称（可为空）
	 * @param owner    所属的 BeanFactory
	 * @param ctor     要使用的构造函数
	 * @param args     构造函数参数
	 * @return 创建的 Bean 实例
	 * @throws BeansException 如果实例化过程中出现错误
	 */
	Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner,
					   Constructor<?> ctor, Object... args) throws BeansException;

	/**
	 * 使用工厂方法实例化 Bean。
	 *
	 * @param bd            Bean 定义信息
	 * @param beanName      Bean 的名称（可为空）
	 * @param owner         所属的 BeanFactory
	 * @param factoryBean   调用工厂方法的工厂实例（如果是静态方法则为 null）
	 * @param factoryMethod 工厂方法
	 * @param args          工厂方法参数
	 * @return 创建的 Bean 实例
	 * @throws BeansException 如果实例化过程中出现错误
	 */
	Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner,
					   @Nullable Object factoryBean, Method factoryMethod, Object... args)
			throws BeansException;

}

