package org.springframework.context.annotation;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionDefaults;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.PatternMatchUtils;

/**
 * ClassPathBeanDefinitionScanner 用于扫描指定包路径下的类，并将符合条件的类注册为 Spring 容器中的 Bean。
 * 它是 Spring 注解驱动配置机制的核心组成部分。
 */
public class ClassPathBeanDefinitionScanner extends ClassPathScanningCandidateComponentProvider {

	// 用于注册 BeanDefinition 的注册器（一般为 BeanFactory 或 ApplicationContext）
	private final BeanDefinitionRegistry registry;

	// 默认的 BeanDefinition 属性配置，例如是否懒加载、自动注入模式等
	private BeanDefinitionDefaults beanDefinitionDefaults = new BeanDefinitionDefaults();

	// 指定自动注入候选的匹配模式（可用于限制哪些 Bean 参与自动注入）
	@Nullable
	private String[] autowireCandidatePatterns;

	// Bean 名称生成策略（默认使用 AnnotationBeanNameGenerator）
	private BeanNameGenerator beanNameGenerator = AnnotationBeanNameGenerator.INSTANCE;

	// Bean 的作用域解析器（如 singleton、prototype、request 等）
	private ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();

	// 是否注册 @Configuration、@Autowired 等注解处理器
	private boolean includeAnnotationConfig = true;


	// 构造器：默认启用默认过滤器（即扫描 @Component、@Service 等注解）
	public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry) {
		this(registry, true);
	}

	// 构造器：可指定是否启用默认过滤器
	public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, boolean useDefaultFilters) {
		this(registry, useDefaultFilters, getOrCreateEnvironment(registry));
	}

	// 构造器：可自定义 Environment（用于支持 @Profile、@Value 等）
	public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, boolean useDefaultFilters,
										  Environment environment) {

		this(registry, useDefaultFilters, environment,
				(registry instanceof ResourceLoader ? (ResourceLoader) registry : null));
	}

	// 构造器：自定义过滤器、环境变量、资源加载器
	public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, boolean useDefaultFilters,
										  Environment environment, @Nullable ResourceLoader resourceLoader) {

		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
		this.registry = registry;

		if (useDefaultFilters) {
			registerDefaultFilters(); // 注册默认过滤器（例如 @Component）
		}
		setEnvironment(environment);
		setResourceLoader(resourceLoader);
	}

	// 获取 BeanDefinition 注册器
	@Override
	public final BeanDefinitionRegistry getRegistry() {
		return this.registry;
	}

	// 设置默认的 BeanDefinition 属性
	public void setBeanDefinitionDefaults(@Nullable BeanDefinitionDefaults beanDefinitionDefaults) {
		this.beanDefinitionDefaults =
				(beanDefinitionDefaults != null ? beanDefinitionDefaults : new BeanDefinitionDefaults());
	}

	public BeanDefinitionDefaults getBeanDefinitionDefaults() {
		return this.beanDefinitionDefaults;
	}

	// 设置自动注入候选的匹配模式（支持通配符）
	public void setAutowireCandidatePatterns(@Nullable String... autowireCandidatePatterns) {
		this.autowireCandidatePatterns = autowireCandidatePatterns;
	}

	// 设置 Bean 名称生成器
	public void setBeanNameGenerator(@Nullable BeanNameGenerator beanNameGenerator) {
		this.beanNameGenerator =
				(beanNameGenerator != null ? beanNameGenerator : AnnotationBeanNameGenerator.INSTANCE);
	}

	// 设置作用域解析器
	public void setScopeMetadataResolver(@Nullable ScopeMetadataResolver scopeMetadataResolver) {
		this.scopeMetadataResolver =
				(scopeMetadataResolver != null ? scopeMetadataResolver : new AnnotationScopeMetadataResolver());
	}

	// 设置作用域代理模式（如代理作用域为 request）
	public void setScopedProxyMode(ScopedProxyMode scopedProxyMode) {
		this.scopeMetadataResolver = new AnnotationScopeMetadataResolver(scopedProxyMode);
	}

	// 设置是否包含注解配置处理器（如 @Autowired、@Value 等处理器）
	public void setIncludeAnnotationConfig(boolean includeAnnotationConfig) {
		this.includeAnnotationConfig = includeAnnotationConfig;
	}

	/**
	 * 扫描指定包路径，并注册为 Bean
	 * @param basePackages 扫描的基础包
	 * @return 注册的 Bean 个数
	 */
	public int scan(String... basePackages) {

		// 获取容器中已经注册的 Bean 个数
		int beanCountAtScanStart = this.registry.getBeanDefinitionCount();
		// 启动扫描器扫描给定包
		doScan(basePackages);
		// 注册注解配置(Annotation config)处理器
		if (this.includeAnnotationConfig) {
			// 注册常用注解处理器：AutowiredAnnotationBeanPostProcessor、CommonAnnotationBeanPostProcessor 等
			AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
		}
		// 返回注册的Bean 个数
		return (this.registry.getBeanDefinitionCount() - beanCountAtScanStart);
	}

	/**
	 * 核心扫描逻辑：遍历包路径，筛选候选类，生成 BeanDefinition，注册到 Spring 容器
	 */
	protected Set<BeanDefinitionHolder> doScan(String... basePackages) {

		Assert.notEmpty(basePackages, "At least one base package must be specified");
		// 创建一个集合,存放扫描到 Bean 定义的封装类
		Set<BeanDefinitionHolder> beanDefinitions = new LinkedHashSet<>();

		for (String basePackage : basePackages) {
			// 找到所有候选组件（符合条件的类）
			Set<BeanDefinition> candidates = findCandidateComponents(basePackage);
			for (BeanDefinition candidate : candidates) {
				// 为 Bean 设置注解配置的作用域
				ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(candidate);
				candidate.setScope(scopeMetadata.getScopeName());

				// 生成 Bean 名称
				String beanName = this.beanNameGenerator.generateBeanName(candidate, this.registry);

				// 如果扫描到的 Bean 不是 Spring 的注解 Bean,则为 Bean 设置默认值,
				// 设置 Bean 的自动依赖注入装配属性等
				if (candidate instanceof AbstractBeanDefinition) {
					postProcessBeanDefinition((AbstractBeanDefinition) candidate, beanName);
				}

				// 如果扫描到的Bean 是 Spring 的注解 Bean,则处理其通用的Spring 注解
				if (candidate instanceof AnnotatedBeanDefinition) {
					AnnotationConfigUtils.processCommonDefinitionAnnotations((AnnotatedBeanDefinition) candidate);
				}

				// 根据 Bean 名称检查指定的Bean 是否需要在容器中注册,或者在容器中冲突
				if (checkCandidate(beanName, candidate)) {
					// 创建 BeanDefinitionHolder（封装了 BeanDefinition 与名称）
					BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(candidate, beanName);

					// 根据注解中配置的作用域,为 Bean 应用相应的代理模式
					definitionHolder =
							AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);

					beanDefinitions.add(definitionHolder);
					// 向容器注册扫描到的 Bean
					registerBeanDefinition(definitionHolder, this.registry);
				}
			}
		}
		return beanDefinitions;
	}

	// 应用默认配置和自动注入候选规则
	protected void postProcessBeanDefinition(AbstractBeanDefinition beanDefinition, String beanName) {
		beanDefinition.applyDefaults(this.beanDefinitionDefaults);
		if (this.autowireCandidatePatterns != null) {
			beanDefinition.setAutowireCandidate(PatternMatchUtils.simpleMatch(this.autowireCandidatePatterns, beanName));
		}
	}

	// 注册 BeanDefinition 到注册表
	protected void registerBeanDefinition(BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry registry) {
		BeanDefinitionReaderUtils.registerBeanDefinition(definitionHolder, registry);
	}

	// 检查是否为有效的 Bean 候选者（判断是否与已有 Bean 冲突）
	protected boolean checkCandidate(String beanName, BeanDefinition beanDefinition) throws IllegalStateException {
		if (!this.registry.containsBeanDefinition(beanName)) {
			return true;
		}
		BeanDefinition existingDef = this.registry.getBeanDefinition(beanName);
		BeanDefinition originatingDef = existingDef.getOriginatingBeanDefinition();
		if (originatingDef != null) {
			existingDef = originatingDef;
		}
		if (isCompatible(beanDefinition, existingDef)) {
			return false;
		}
		throw new ConflictingBeanDefinitionException("Annotation-specified bean name '" + beanName +
				"' for bean class [" + beanDefinition.getBeanClassName() + "] conflicts with existing, " +
				"non-compatible bean definition of same name and class [" + existingDef.getBeanClassName() + "]");
	}

	// 判断两个 BeanDefinition 是否兼容
	protected boolean isCompatible(BeanDefinition newDef, BeanDefinition existingDef) {
		return (!(existingDef instanceof ScannedGenericBeanDefinition) ||
				(newDef.getSource() != null && newDef.getSource().equals(existingDef.getSource())) ||
				newDef.equals(existingDef));
	}

	// 获取环境变量对象，如果注册器支持则从中获取，否则新建一个标准环境
	private static Environment getOrCreateEnvironment(BeanDefinitionRegistry registry) {
		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
		if (registry instanceof EnvironmentCapable) {
			return ((EnvironmentCapable) registry).getEnvironment();
		}
		return new StandardEnvironment();
	}
}
