package org.springframework.beans.factory.annotation;

import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

@SuppressWarnings("serial")
/**
 * 基于注解的通用 BeanDefinition 实现类，用于在注解驱动的 Spring 配置中描述一个 bean 定义。
 * 它实现了 AnnotatedBeanDefinition 接口，能获取注解元信息。
 */
public class AnnotatedGenericBeanDefinition extends GenericBeanDefinition implements AnnotatedBeanDefinition {

	/**
	 * 用于保存该 bean 的注解元数据（如类上的 @Component、@Scope 等注解信息）。
	 */
	private final AnnotationMetadata metadata;

	/**
	 * 如果 bean 是通过工厂方法创建的，保存对应的工厂方法的元数据信息。
	 */
	@Nullable
	private MethodMetadata factoryMethodMetadata;

	/**
	 * 根据给定的 Class 对象创建 BeanDefinition。
	 * 会从该类上提取注解元数据（使用 ASM 或反射）。
	 *
	 * @param beanClass 要注册为 Bean 的类
	 */
	public AnnotatedGenericBeanDefinition(Class<?> beanClass) {
		// 设置 bean 的 class 类型
		setBeanClass(beanClass);
		// 解析注解元数据（包括类级别注解、方法、字段等）
		this.metadata = AnnotationMetadata.introspect(beanClass);
	}

	/**
	 * 使用已有的注解元数据构造 BeanDefinition，通常用于读取外部的元数据（如 ASM 扫描结果）。
	 *
	 * @param metadata 注解元数据对象
	 */
	public AnnotatedGenericBeanDefinition(AnnotationMetadata metadata) {
		Assert.notNull(metadata, "AnnotationMetadata must not be null");

		// 如果是标准元数据，直接拿到 Class 对象设置 beanClass
		if (metadata instanceof StandardAnnotationMetadata) {
			setBeanClass(((StandardAnnotationMetadata) metadata).getIntrospectedClass());
		} else {
			// 否则只能设置类名（无法反射到 Class 对象）
			setBeanClassName(metadata.getClassName());
		}

		this.metadata = metadata;
	}

	/**
	 * 构造函数，既支持注解元数据，也支持通过某个工厂方法创建 bean。
	 * 常见于配置类中的 @Bean 方法。
	 *
	 * @param metadata              注解元数据
	 * @param factoryMethodMetadata 工厂方法元数据
	 */
	public AnnotatedGenericBeanDefinition(AnnotationMetadata metadata, MethodMetadata factoryMethodMetadata) {
		this(metadata);
		Assert.notNull(factoryMethodMetadata, "MethodMetadata must not be null");
		// 设置工厂方法名
		setFactoryMethodName(factoryMethodMetadata.getMethodName());
		this.factoryMethodMetadata = factoryMethodMetadata;
	}

	/**
	 * 获取类的注解元数据（包括 @Component、@Scope、@Lazy 等）。
	 *
	 * @return 注解元数据对象
	 */
	@Override
	public final AnnotationMetadata getMetadata() {
		return this.metadata;
	}

	/**
	 * 如果该 bean 是通过工厂方法定义的（例如 @Bean 方法），则返回工厂方法的元数据。
	 *
	 * @return 工厂方法的元数据，或 null
	 */
	@Override
	@Nullable
	public final MethodMetadata getFactoryMethodMetadata() {
		return this.factoryMethodMetadata;
	}
}

