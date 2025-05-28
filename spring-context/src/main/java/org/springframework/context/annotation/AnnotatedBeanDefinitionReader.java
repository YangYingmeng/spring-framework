package org.springframework.context.annotation;

import java.lang.annotation.Annotation;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionCustomizer;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * 用于将带有注解的类注册为 Spring Bean 的读取器。
 * 它会读取类上的注解，如 @Component、@Configuration 等，生成对应的 BeanDefinition，并注册到 BeanDefinitionRegistry 中。
 */
public class AnnotatedBeanDefinitionReader {

	// 用于注册 BeanDefinition 的目标容器
	private final BeanDefinitionRegistry registry;

	// Bean 名称生成器，默认使用 AnnotationBeanNameGenerator
	private BeanNameGenerator beanNameGenerator = AnnotationBeanNameGenerator.INSTANCE;

	// Scope 元信息解析器，默认支持 @Scope 注解
	private ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();

	// 条件判断器，用于解析 @Conditional 注解
	private ConditionEvaluator conditionEvaluator;

	/**
	 * 构造器：传入注册器，从注册器中获取或构造 Environment
	 */
	public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry) {
		this(registry, getOrCreateEnvironment(registry));
	}

	/**
	 * 构造器：可传入注册器和环境变量
	 */
	public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry, Environment environment) {
		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
		Assert.notNull(environment, "Environment must not be null");
		this.registry = registry;
		this.conditionEvaluator = new ConditionEvaluator(registry, environment, null);

		// 注册处理常见注解的 BeanPostProcessor，例如 ConfigurationClassPostProcessor
		AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
	}

	public final BeanDefinitionRegistry getRegistry() {
		return this.registry;
	}

	// 设置环境信息，更新条件评估器
	public void setEnvironment(Environment environment) {
		this.conditionEvaluator = new ConditionEvaluator(this.registry, environment, null);
	}

	// 设置 Bean 名称生成器
	public void setBeanNameGenerator(@Nullable BeanNameGenerator beanNameGenerator) {
		this.beanNameGenerator =
				(beanNameGenerator != null ? beanNameGenerator : AnnotationBeanNameGenerator.INSTANCE);
	}

	// 设置作用域元信息解析器
	public void setScopeMetadataResolver(@Nullable ScopeMetadataResolver scopeMetadataResolver) {
		this.scopeMetadataResolver =
				(scopeMetadataResolver != null ? scopeMetadataResolver : new AnnotationScopeMetadataResolver());
	}

	// 注册一个或多个类，类上必须有 @Component 或 @Configuration 注解
	public void register(Class<?>... componentClasses) {
		for (Class<?> componentClass : componentClasses) {
			registerBean(componentClass);
		}
	}

	// 注册一个类为 Bean（默认使用类名生成 Bean 名）
	public void registerBean(Class<?> beanClass) {
		doRegisterBean(beanClass, null, null, null, null);
	}

	// 注册一个类为 Bean，指定名称
	public void registerBean(Class<?> beanClass, @Nullable String name) {
		doRegisterBean(beanClass, name, null, null, null);
	}

	// 注册一个类为 Bean，并带有多个限定符注解（如 @Primary, @Lazy）
	@SuppressWarnings("unchecked")
	public void registerBean(Class<?> beanClass, Class<? extends Annotation>... qualifiers) {
		doRegisterBean(beanClass, null, qualifiers, null, null);
	}

	// 注册一个类为 Bean，指定名称并附加限定符注解
	@SuppressWarnings("unchecked")
	public void registerBean(Class<?> beanClass, @Nullable String name,
							 Class<? extends Annotation>... qualifiers) {
		doRegisterBean(beanClass, name, qualifiers, null, null);
	}

	// 注册一个带有实例提供者的 Bean（通常用于 Lambda 实例化）
	public <T> void registerBean(Class<T> beanClass, @Nullable Supplier<T> supplier) {
		doRegisterBean(beanClass, null, null, supplier, null);
	}

	// 注册一个 Bean，指定名称和 Supplier
	public <T> void registerBean(Class<T> beanClass, @Nullable String name, @Nullable Supplier<T> supplier) {
		doRegisterBean(beanClass, name, null, supplier, null);
	}

	// 注册一个 Bean，指定名称、Supplier 及自定义 BeanDefinition 操作
	public <T> void registerBean(Class<T> beanClass, @Nullable String name, @Nullable Supplier<T> supplier,
								 BeanDefinitionCustomizer... customizers) {
		doRegisterBean(beanClass, name, null, supplier, customizers);
	}

	/**
	 * 核心注册逻辑，将指定类注册为一个 BeanDefinition 并放入 BeanDefinitionRegistry 中
	 */
	private <T> void doRegisterBean(Class<T> beanClass, @Nullable String name,
									@Nullable Class<? extends Annotation>[] qualifiers, @Nullable Supplier<T> supplier,
									@Nullable BeanDefinitionCustomizer[] customizers) {

		// 创建注解式 BeanDefinition, 将指定注解bean  ->  BeanDefinition
		AnnotatedGenericBeanDefinition abd = new AnnotatedGenericBeanDefinition(beanClass);

		// 如果不满足条件（如 @Conditional），则跳过注册
		if (this.conditionEvaluator.shouldSkip(abd.getMetadata())) {
			return;
		}

		// 设置实例工厂方法（如 Lambda）
		abd.setInstanceSupplier(supplier);

		// 设置作用域（默认 singleton，也支持 @Scope）
		ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(abd);
		abd.setScope(scopeMetadata.getScopeName());

		// 自动生成 Bean 名称或使用指定名称
		String beanName = (name != null ? name : this.beanNameGenerator.generateBeanName(abd, this.registry));

		// 处理常见注解，如 @Lazy、@Primary、@DependsOn 等
		AnnotationConfigUtils.processCommonDefinitionAnnotations(abd);

		// 如果在向容器注册注解 Bean 定义时,使用了额外的限定符注解,则解析限定符注解。
		// 主要是配置的关于 autowiring 自动依赖注入装配的限定条件,即@Qualifier 注解,
		// 主要是配置的关于 autowiring 自动依赖注入装配的限定条件,即@Qualifier 注解
		if (qualifiers != null) {
			for (Class<? extends Annotation> qualifier : qualifiers) {
				//如果配置了@Primary 注解,设置该 Bean 为 autowiring 自动依赖注入装配时的首选
				if (Primary.class == qualifier) {
					abd.setPrimary(true);
					//如果配置了@Lazy 注解,则设置该 Bean 为非延迟初始化,如果没有配置,则该 Bean 为预实例化
				} else if (Lazy.class == qualifier) {
					abd.setLazyInit(true);
					// 如果使用了除@Primary和@Lazy以外的其他注解,则为该 Bean 添加一个 autowiring 自动依赖注入装配限定符,
					// 该 Bean 在进 autowiring 自动依赖注入装配时,根据名称装配限定符指定的 Bean
				} else {
					abd.addQualifier(new AutowireCandidateQualifier(qualifier));
				}
			}
		}

		// 应用自定义的属性配置器
		if (customizers != null) {
			for (BeanDefinitionCustomizer customizer : customizers) {
				customizer.customize(abd);
			}
		}

		// 创建一个指定 Bean 名称的 Bean 定义对象,封装注解 Bean 定义类数据
		BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(abd, beanName);
		// 根据注解 Bean 定义类中配置的作用域,创建相应的代理对象
		definitionHolder = AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);

		// 向 IOC 容器注册注解 Bean 类定义对象
		BeanDefinitionReaderUtils.registerBeanDefinition(definitionHolder, this.registry);
	}

	/**
	 * 获取或创建 Environment，优先从 registry 中获取
	 */
	private static Environment getOrCreateEnvironment(BeanDefinitionRegistry registry) {
		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
		if (registry instanceof EnvironmentCapable) {
			return ((EnvironmentCapable) registry).getEnvironment();
		}
		return new StandardEnvironment();
	}
}

