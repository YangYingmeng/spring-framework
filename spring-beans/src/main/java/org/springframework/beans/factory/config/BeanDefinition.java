package org.springframework.beans.factory.config;

import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.core.AttributeAccessor;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;

/**
 * BeanDefinition 是 Spring 中用于描述 Bean 配置信息的核心接口。
 * 它定义了 Bean 的类名、作用域、初始化和销毁方法、构造参数、属性值等元数据。
 *
 * BeanDefinition 实例在 Spring 容器启动时被解析和注册，用于构造和管理 Bean 的整个生命周期。
 *
 * 实现类包括 GenericBeanDefinition、RootBeanDefinition 等。
 */
public interface BeanDefinition extends AttributeAccessor, BeanMetadataElement {

	// 单例作用域：容器中只存在一个共享的 Bean 实例
	String SCOPE_SINGLETON = ConfigurableBeanFactory.SCOPE_SINGLETON;

	// 原型作用域：每次请求都会创建一个新的 Bean 实例
	String SCOPE_PROTOTYPE = ConfigurableBeanFactory.SCOPE_PROTOTYPE;

	// 应用级别角色：用户自定义的 Bean
	int ROLE_APPLICATION = 0;

	// 支持级别角色：用于配置和支持的 Bean
	int ROLE_SUPPORT = 1;

	// 基础设施角色：框架内部使用的 Bean
	int ROLE_INFRASTRUCTURE = 2;

	/**
	 * 设置父 Bean 的名称
	 */
	void setParentName(@Nullable String parentName);

	/**
	 * 获取父 Bean 的名称
	 */
	@Nullable
	String getParentName();

	/**
	 * 设置 Bean 的类名
	 */
	void setBeanClassName(@Nullable String beanClassName);

	/**
	 * 获取 Bean 的类名
	 */
	@Nullable
	String getBeanClassName();

	/**
	 * 设置作用域（singleton 或 prototype）
	 */
	void setScope(@Nullable String scope);

	/**
	 * 获取作用域
	 */
	@Nullable
	String getScope();

	/**
	 * 设置是否懒加载
	 */
	void setLazyInit(boolean lazyInit);

	/**
	 * 是否懒加载
	 */
	boolean isLazyInit();

	/**
	 * 设置依赖的 Bean 名称数组
	 */
	void setDependsOn(@Nullable String... dependsOn);

	/**
	 * 获取依赖的 Bean 名称数组
	 */
	@Nullable
	String[] getDependsOn();

	/**
	 * 设置是否为自动注入候选者
	 */
	void setAutowireCandidate(boolean autowireCandidate);

	/**
	 * 是否为自动注入候选者
	 */
	boolean isAutowireCandidate();

	/**
	 * 设置是否为首选候选者（primary）
	 */
	void setPrimary(boolean primary);

	/**
	 * 是否为首选候选者
	 */
	boolean isPrimary();

	/**
	 * 设置用于创建此 Bean 的工厂 Bean 名称
	 */
	void setFactoryBeanName(@Nullable String factoryBeanName);

	/**
	 * 获取工厂 Bean 名称
	 */
	@Nullable
	String getFactoryBeanName();

	/**
	 * 设置用于创建此 Bean 的工厂方法名
	 */
	void setFactoryMethodName(@Nullable String factoryMethodName);

	/**
	 * 获取工厂方法名
	 */
	@Nullable
	String getFactoryMethodName();

	/**
	 * 获取构造函数参数值集合
	 */
	ConstructorArgumentValues getConstructorArgumentValues();

	/**
	 * 是否包含构造函数参数值
	 */
	default boolean hasConstructorArgumentValues() {
		return !getConstructorArgumentValues().isEmpty();
	}

	/**
	 * 获取属性值集合
	 */
	MutablePropertyValues getPropertyValues();

	/**
	 * 是否包含属性值
	 */
	default boolean hasPropertyValues() {
		return !getPropertyValues().isEmpty();
	}

	/**
	 * 设置初始化方法名
	 */
	void setInitMethodName(@Nullable String initMethodName);

	/**
	 * 获取初始化方法名
	 */
	@Nullable
	String getInitMethodName();

	/**
	 * 设置销毁方法名
	 */
	void setDestroyMethodName(@Nullable String destroyMethodName);

	/**
	 * 获取销毁方法名
	 */
	@Nullable
	String getDestroyMethodName();

	/**
	 * 设置 Bean 的角色（用户、自定义支持、基础设施）
	 */
	void setRole(int role);

	/**
	 * 获取 Bean 的角色
	 */
	int getRole();

	/**
	 * 设置描述信息
	 */
	void setDescription(@Nullable String description);

	/**
	 * 获取描述信息
	 */
	@Nullable
	String getDescription();

	/**
	 * 获取可解析类型（包含泛型信息）
	 */
	ResolvableType getResolvableType();

	/**
	 * 是否为单例作用域
	 */
	boolean isSingleton();

	/**
	 * 是否为原型作用域
	 */
	boolean isPrototype();

	/**
	 * 是否为抽象 Bean 定义
	 */
	boolean isAbstract();

	/**
	 * 获取资源描述（例如定义该 Bean 的配置文件位置）
	 */
	@Nullable
	String getResourceDescription();

	/**
	 * 获取原始定义的 BeanDefinition（如为合并 Bean 时使用）
	 */
	@Nullable
	BeanDefinition getOriginatingBeanDefinition();
}
