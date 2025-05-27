package org.springframework.beans.factory.support;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.core.AliasRegistry;

/**
 * BeanDefinition 注册表接口，用于管理 Spring 容器中的 BeanDefinition 定义。
 * 继承自 AliasRegistry，支持别名注册功能。
 */
public interface BeanDefinitionRegistry extends AliasRegistry {

	/**
	 * 向注册表中注册一个新的 BeanDefinition。
	 *
	 * @param beanName       Bean 的名称（唯一标识）
	 * @param beanDefinition Bean 的定义信息，包含类信息、属性、生命周期等
	 * @throws BeanDefinitionStoreException 如果注册失败，例如 Bean 名称冲突
	 */
	void registerBeanDefinition(String beanName, BeanDefinition beanDefinition)
			throws BeanDefinitionStoreException;

	/**
	 * 从注册表中移除指定名称的 BeanDefinition。
	 *
	 * @param beanName Bean 的名称
	 * @throws NoSuchBeanDefinitionException 如果指定名称的 BeanDefinition 不存在
	 */
	void removeBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;

	/**
	 * 根据 Bean 名称获取对应的 BeanDefinition。
	 *
	 * @param beanName Bean 的名称
	 * @return 对应的 BeanDefinition
	 * @throws NoSuchBeanDefinitionException 如果指定名称的 BeanDefinition 不存在
	 */
	BeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;

	/**
	 * 判断注册表中是否包含指定名称的 BeanDefinition。
	 *
	 * @param beanName Bean 的名称
	 * @return 如果包含返回 true，否则 false
	 */
	boolean containsBeanDefinition(String beanName);

	/**
	 * 获取注册表中所有已注册的 BeanDefinition 名称数组。
	 *
	 * @return 所有 Bean 名称的字符串数组
	 */
	String[] getBeanDefinitionNames();

	/**
	 * 获取当前注册表中 BeanDefinition 的数量。
	 *
	 * @return BeanDefinition 数量
	 */
	int getBeanDefinitionCount();

	/**
	 * 判断指定的 Bean 名称是否正在使用中（包括别名）。
	 *
	 * @param beanName Bean 的名称或别名
	 * @return 如果名称已被占用返回 true，否则 false
	 */
	boolean isBeanNameInUse(String beanName);

}
