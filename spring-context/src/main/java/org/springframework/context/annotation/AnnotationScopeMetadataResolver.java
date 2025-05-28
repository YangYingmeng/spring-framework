package org.springframework.context.annotation;

import java.lang.annotation.Annotation;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.util.Assert;

/**
 * 基于注解的作用域解析器，用于解析类上的 @Scope 注解，
 * 并返回对应的 ScopeMetadata（作用域元信息）。
 * <p>
 * 该解析器默认使用 {@link org.springframework.context.annotation.Scope} 注解进行识别，
 * 也可以通过 setScopeAnnotationType() 替换为自定义的作用域注解。
 * <p>
 * 主要用于 Spring 在注册 BeanDefinition 时确定 bean 的作用域及其代理模式。
 */
public class AnnotationScopeMetadataResolver implements ScopeMetadataResolver {

	// 默认的代理模式（默认为 NO，即不使用代理）
	private final ScopedProxyMode defaultProxyMode;

	// 当前使用的作用域注解类型，默认是 @Scope 注解
	protected Class<? extends Annotation> scopeAnnotationType = Scope.class;

	/**
	 * 默认构造函数，代理模式为 NO（不使用代理）
	 */
	public AnnotationScopeMetadataResolver() {
		this.defaultProxyMode = ScopedProxyMode.NO;
	}

	/**
	 * 可以指定默认的代理模式的构造函数。
	 *
	 * @param defaultProxyMode 当注解中未指定 proxyMode 时采用此默认代理模式
	 */
	public AnnotationScopeMetadataResolver(ScopedProxyMode defaultProxyMode) {
		Assert.notNull(defaultProxyMode, "'defaultProxyMode' must not be null");
		this.defaultProxyMode = defaultProxyMode;
	}

	/**
	 * 设置自定义的作用域注解类型（默认是 @Scope）。
	 * 可用于支持自定义作用域注解。
	 */
	public void setScopeAnnotationType(Class<? extends Annotation> scopeAnnotationType) {
		Assert.notNull(scopeAnnotationType, "'scopeAnnotationType' must not be null");
		this.scopeAnnotationType = scopeAnnotationType;
	}

	/**
	 * 根据 BeanDefinition 中的注解信息解析出作用域（如 singleton、prototype）
	 * 以及是否启用代理（如 ScopedProxyMode.TARGET_CLASS）
	 *
	 * @param definition 要解析的 BeanDefinition（通常是 AnnotatedBeanDefinition）
	 * @return 作用域元数据（ScopeMetadata）
	 */
	@Override
	public ScopeMetadata resolveScopeMetadata(BeanDefinition definition) {
		ScopeMetadata metadata = new ScopeMetadata();

		// 如果是带注解的 BeanDefinition，才能解析注解
		if (definition instanceof AnnotatedBeanDefinition) {
			AnnotatedBeanDefinition annDef = (AnnotatedBeanDefinition) definition;

			// 从类上的注解中提取 Scope 注解的属性, 将所有的注解和注解的值存放在一个 map 集合中
			AnnotationAttributes attributes = AnnotationConfigUtils.attributesFor(
					annDef.getMetadata(), this.scopeAnnotationType);

			// 将获取到的@Scope注解的值设置到要返回的对象中
			if (attributes != null) {
				// 设置作用域名，例如 "singleton" 或 "prototype"
				metadata.setScopeName(attributes.getString("value"));

				// 获取注解中配置的代理模式
				ScopedProxyMode proxyMode = attributes.getEnum("proxyMode");

				// 如果未配置代理模式，则使用默认代理模式
				if (proxyMode == ScopedProxyMode.DEFAULT) {
					proxyMode = this.defaultProxyMode;
				}

				// 设置代理模式
				metadata.setScopedProxyMode(proxyMode);
			}
		}
		// 返回解析的作用域元信息对象
		return metadata;
	}
}

