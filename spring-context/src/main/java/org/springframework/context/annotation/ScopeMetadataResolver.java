package org.springframework.context.annotation;

import org.springframework.beans.factory.config.BeanDefinition;

/**
 * 用于解析 Bean 的作用域元信息（ScopeMetadata）的策略接口。
 *
 * <p>Spring 中的 Bean 默认是单例（singleton），但也支持其他作用域，
 * 如 prototype、request、session、application 等。
 * ScopeMetadataResolver 就是用来根据 BeanDefinition 来判断应该使用哪种作用域的。
 *
 * <p>通常和注解（如 @Scope）结合使用，常见实现类是 {@link org.springframework.context.annotation.AnnotationScopeMetadataResolver}。
 *
 * <p>该接口是函数式接口（@FunctionalInterface），可以使用 lambda 表达式或方法引用实现。
 */
@FunctionalInterface
public interface ScopeMetadataResolver {

	/**
	 * 根据给定的 BeanDefinition 解析其作用域元信息（ScopeMetadata）。
	 *
	 * @param definition 要解析的 BeanDefinition，通常是 AnnotatedBeanDefinition
	 * @return ScopeMetadata，其中包含作用域名称（如 "singleton"、"prototype" 等）
	 */
	ScopeMetadata resolveScopeMetadata(BeanDefinition definition);

}

