package org.springframework.context.annotation;

import java.util.Arrays;
import java.util.function.Supplier;

import org.springframework.beans.factory.config.BeanDefinitionCustomizer;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.metrics.StartupStep;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * AnnotationConfigApplicationContext 是基于注解配置的 Spring 容器，
 * 它支持通过 @Configuration、@Component 等注解来注册 Bean。
 * 是最常用的 Java 配置方式（替代 XML 配置）。
 * <p>
 * 继承自 GenericApplicationContext，支持手动注册 BeanDefinition。
 * 实现了 AnnotationConfigRegistry 接口，支持通过 register()、scan() 方法加载配置类或扫描包。
 */
public class AnnotationConfigApplicationContext extends GenericApplicationContext implements AnnotationConfigRegistry {

	// 用于注册通过 @Configuration、@Component 等注解定义的 Bean
	private final AnnotatedBeanDefinitionReader reader;

	// 用于扫描指定包路径下的注解类（如 @Component）
	private final ClassPathBeanDefinitionScanner scanner;

	/**
	 * 无参构造器：初始化 reader 和 scanner，不会自动 refresh。
	 */
	public AnnotationConfigApplicationContext() {
		StartupStep createAnnotatedBeanDefReader = getApplicationStartup()
				.start("spring.context.annotated-bean-reader.create");

		// 创建用于注册注解 Bean 的工具类
		this.reader = new AnnotatedBeanDefinitionReader(this);
		createAnnotatedBeanDefReader.end();

		// 创建用于扫描包的工具类
		this.scanner = new ClassPathBeanDefinitionScanner(this);
	}

	/**
	 * 提供已有的 BeanFactory 构造上下文（通常用于集成场景）
	 */
	public AnnotationConfigApplicationContext(DefaultListableBeanFactory beanFactory) {
		super(beanFactory);
		this.reader = new AnnotatedBeanDefinitionReader(this);
		this.scanner = new ClassPathBeanDefinitionScanner(this);
	}
	/**
	 * /最常用的构造函, 根据指定的类注册为 Bean（通常是 @Configuration/@Component 注解类），并自动 refresh。
	 */
	public AnnotationConfigApplicationContext(Class<?>... componentClasses) {
		this(); // 初始化 reader 和 scanner
		register(componentClasses); // 注册注解类为 BeanDefinition
		refresh(); // 刷新容器，完成 Bean 的初始化和依赖注入
	}

	/**
	 * 根据指定包路径进行扫描，自动注册 @Component 注解类为 Bean，并 refresh。
	 */
	public AnnotationConfigApplicationContext(String... basePackages) {
		this();
		scan(basePackages); // 包扫描，注册为 BeanDefinition
		refresh(); // 刷新容器
	}

	/**
	 * 设置环境变量（如 dev/test/prod），会同步设置 reader 和 scanner 的环境。
	 */
	@Override
	public void setEnvironment(ConfigurableEnvironment environment) {
		super.setEnvironment(environment);
		this.reader.setEnvironment(environment);
		this.scanner.setEnvironment(environment);
	}

	/**
	 * 设置 Bean 命名策略，并注册到容器中。
	 */
	public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
		this.reader.setBeanNameGenerator(beanNameGenerator);
		this.scanner.setBeanNameGenerator(beanNameGenerator);

		// 注册到 BeanFactory，供全局使用
		getBeanFactory().registerSingleton(
				AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR, beanNameGenerator);
	}

	/**
	 * 设置作用域解析器，如 @Scope("prototype") 的处理方式。
	 */
	public void setScopeMetadataResolver(ScopeMetadataResolver scopeMetadataResolver) {
		this.reader.setScopeMetadataResolver(scopeMetadataResolver);
		this.scanner.setScopeMetadataResolver(scopeMetadataResolver);
	}

	/**
	 * 注册一个或多个注解配置类（如 @Configuration、@Component 等）
	 */
	@Override
	public void register(Class<?>... componentClasses) {
		Assert.notEmpty(componentClasses, "At least one component class must be specified");

		StartupStep registerComponentClass = getApplicationStartup()
				.start("spring.context.component-classes.register")
				.tag("classes", () -> Arrays.toString(componentClasses));

		// 由 AnnotatedBeanDefinitionReader 负责解析并注册
		this.reader.register(componentClasses);
		registerComponentClass.end();
	}

	/**
	 * 扫描指定包路径下的 @Component 注解类，并注册为 Bean。
	 */
	@Override
	public void scan(String... basePackages) {
		Assert.notEmpty(basePackages, "At least one base package must be specified");

		StartupStep scanPackages = getApplicationStartup()
				.start("spring.context.base-packages.scan")
				.tag("packages", () -> Arrays.toString(basePackages));

		this.scanner.scan(basePackages);
		scanPackages.end();
	}

	/**
	 * 注册一个 Bean，同时支持指定名称、工厂方法和自定义 BeanDefinition 属性。
	 */
	@Override
	public <T> void registerBean(@Nullable String beanName, Class<T> beanClass,
								 @Nullable Supplier<T> supplier, BeanDefinitionCustomizer... customizers) {

		this.reader.registerBean(beanClass, beanName, supplier, customizers);
	}
}
