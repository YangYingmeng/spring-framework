package org.springframework.context.support;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.CachedIntrospectionResults;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.support.ResourceEditorRegistrar;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ApplicationStartupAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.HierarchicalMessageSource;
import org.springframework.context.LifecycleProcessor;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.expression.StandardBeanExpressionResolver;
import org.springframework.context.weaving.LoadTimeWeaverAware;
import org.springframework.context.weaving.LoadTimeWeaverAwareProcessor;
import org.springframework.core.NativeDetector;
import org.springframework.core.ResolvableType;
import org.springframework.core.SpringProperties;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

/**
 * 抽象应用上下文类，作为大多数 Spring 应用上下文实现的基础。
 * 该类实现了 ConfigurableApplicationContext 接口，是 Bean 生命周期、事件发布、资源加载等功能的核心支持类。
 */
public abstract class AbstractApplicationContext extends DefaultResourceLoader
		implements ConfigurableApplicationContext {

	// Spring 中 messageSource 的默认 bean 名称
	public static final String MESSAGE_SOURCE_BEAN_NAME = "messageSource";
	// Spring 中 applicationEventMulticaster 的默认 bean 名称
	public static final String APPLICATION_EVENT_MULTICASTER_BEAN_NAME = "applicationEventMulticaster";
	// Spring 中生命周期处理器的默认 bean 名称
	public static final String LIFECYCLE_PROCESSOR_BEAN_NAME = "lifecycleProcessor";

	// 是否忽略 Spring 表达式语言（SpEL）
	private static final boolean shouldIgnoreSpel = SpringProperties.getFlag("spring.spel.ignore");

	static {
		// 提前加载 ContextClosedEvent，避免 WebLogic 8.1 关闭时的类加载问题
		ContextClosedEvent.class.getName();
	}

	// 日志记录器
	protected final Log logger = LogFactory.getLog(getClass());

	// 应用上下文唯一 ID 和显示名称（可配置）
	private String id = ObjectUtils.identityToString(this);
	private String displayName = ObjectUtils.identityToString(this);

	// 父上下文（可选）
	@Nullable
	private ApplicationContext parent;

	// Spring 的环境配置（可配置）
	@Nullable
	private ConfigurableEnvironment environment;

	// BeanFactory 后置处理器列表
	private final List<BeanFactoryPostProcessor> beanFactoryPostProcessors = new ArrayList<>();

	// 启动时间戳
	private long startupDate;

	// 标记是否已激活或已关闭
	private final AtomicBoolean active = new AtomicBoolean();
	private final AtomicBoolean closed = new AtomicBoolean();

	// 启动和关闭操作的同步锁
	private final Object startupShutdownMonitor = new Object();

	// JVM 关闭钩子线程
	@Nullable
	private Thread shutdownHook;

	// 资源路径解析器
	private final ResourcePatternResolver resourcePatternResolver;

	// 生命周期处理器、消息源、多播器、启动监控等
	@Nullable
	private LifecycleProcessor lifecycleProcessor;
	@Nullable
	private MessageSource messageSource;
	@Nullable
	private ApplicationEventMulticaster applicationEventMulticaster;
	private ApplicationStartup applicationStartup = ApplicationStartup.DEFAULT;

	// 应用事件监听器集合
	private final Set<ApplicationListener<?>> applicationListeners = new LinkedHashSet<>();

	// 早期注册的监听器和事件（刷新前临时缓存）
	@Nullable
	private Set<ApplicationListener<?>> earlyApplicationListeners;
	@Nullable
	private Set<ApplicationEvent> earlyApplicationEvents;

	// 构造方法
	public AbstractApplicationContext() {
		this.resourcePatternResolver = getResourcePatternResolver();
	}

	public AbstractApplicationContext(@Nullable ApplicationContext parent) {
		this();
		setParent(parent);
	}

	// 设置和获取 ID、显示名称、父容器等基本信息
	@Override
	public void setId(String id) {
		this.id = id;
	}

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public String getApplicationName() {
		return "";
	}

	public void setDisplayName(String displayName) {
		Assert.hasLength(displayName, "Display name must not be empty");
		this.displayName = displayName;
	}

	@Override
	public String getDisplayName() {
		return this.displayName;
	}

	@Override
	@Nullable
	public ApplicationContext getParent() {
		return this.parent;
	}

	@Override
	public void setEnvironment(ConfigurableEnvironment environment) {
		this.environment = environment;
	}

	@Override
	public ConfigurableEnvironment getEnvironment() {
		if (this.environment == null) {
			this.environment = createEnvironment();
		}
		return this.environment;
	}

	// 创建默认环境
	protected ConfigurableEnvironment createEnvironment() {
		return new StandardEnvironment();
	}

	// 获取自动注入 Bean 工厂（在 refresh 之后才可用）
	@Override
	public AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException {
		return getBeanFactory();
	}

	@Override
	public long getStartupDate() {
		return this.startupDate;
	}

	// 发布事件（重载支持 Object 与 ApplicationEvent）
	@Override
	public void publishEvent(ApplicationEvent event) {
		publishEvent(event, null);
	}

	@Override
	public void publishEvent(Object event) {
		publishEvent(event, null);
	}

	// 核心发布事件方法（包括父容器传递）
	protected void publishEvent(Object event, @Nullable ResolvableType eventType) {
		Assert.notNull(event, "Event must not be null");

		// 若非 ApplicationEvent，则包装为 PayloadApplicationEvent
		ApplicationEvent applicationEvent;
		if (event instanceof ApplicationEvent) {
			applicationEvent = (ApplicationEvent) event;
		} else {
			applicationEvent = new PayloadApplicationEvent<>(this, event);
			if (eventType == null) {
				eventType = ((PayloadApplicationEvent<?>) applicationEvent).getResolvableType();
			}
		}

		// 还未初始化多播器前，暂存在 earlyApplicationEvents 中
		if (this.earlyApplicationEvents != null) {
			this.earlyApplicationEvents.add(applicationEvent);
		} else {
			getApplicationEventMulticaster().multicastEvent(applicationEvent, eventType);
		}

		// 传播到父容器
		if (this.parent != null) {
			if (this.parent instanceof AbstractApplicationContext) {
				((AbstractApplicationContext) this.parent).publishEvent(event, eventType);
			} else {
				this.parent.publishEvent(event);
			}
		}
	}

	// 获取事件多播器，未初始化时抛异常
	ApplicationEventMulticaster getApplicationEventMulticaster() throws IllegalStateException {
		if (this.applicationEventMulticaster == null) {
			throw new IllegalStateException("ApplicationEventMulticaster not initialized - " +
					"call 'refresh' before multicasting events via the context: " + this);
		}
		return this.applicationEventMulticaster;
	}

	@Override
	public void setApplicationStartup(ApplicationStartup applicationStartup) {
		Assert.notNull(applicationStartup, "ApplicationStartup must not be null");
		this.applicationStartup = applicationStartup;
	}

	@Override
	public ApplicationStartup getApplicationStartup() {
		return this.applicationStartup;
	}

	// 获取生命周期处理器（需在 refresh 后才可用）
	LifecycleProcessor getLifecycleProcessor() throws IllegalStateException {
		if (this.lifecycleProcessor == null) {
			throw new IllegalStateException("LifecycleProcessor not initialized - " +
					"call 'refresh' before invoking lifecycle methods via the context: " + this);
		}
		return this.lifecycleProcessor;
	}

	// 获取资源路径解析器（用于加载 classpath:* 等资源）
	protected ResourcePatternResolver getResourcePatternResolver() {
		return new PathMatchingResourcePatternResolver(this);
	}

	// 设置父上下文，并合并其 environment 配置
	@Override
	public void setParent(@Nullable ApplicationContext parent) {
		this.parent = parent;
		if (parent != null) {
			Environment parentEnvironment = parent.getEnvironment();
			if (parentEnvironment instanceof ConfigurableEnvironment) {
				getEnvironment().merge((ConfigurableEnvironment) parentEnvironment);
			}
		}
	}

	// 添加 BeanFactoryPostProcessor
	@Override
	public void addBeanFactoryPostProcessor(BeanFactoryPostProcessor postProcessor) {
		Assert.notNull(postProcessor, "BeanFactoryPostProcessor must not be null");
		this.beanFactoryPostProcessors.add(postProcessor);
	}

	public List<BeanFactoryPostProcessor> getBeanFactoryPostProcessors() {
		return this.beanFactoryPostProcessors;
	}

	// 添加应用监听器
	@Override
	public void addApplicationListener(ApplicationListener<?> listener) {
		Assert.notNull(listener, "ApplicationListener must not be null");
		if (this.applicationEventMulticaster != null) {
			this.applicationEventMulticaster.addApplicationListener(listener);
		}
		this.applicationListeners.add(listener);
	}

	public Collection<ApplicationListener<?>> getApplicationListeners() {
		return this.applicationListeners;
	}

	/**
	 * Spring 容器刷新核心入口方法
	 */
	@Override
	public void refresh() throws BeansException, IllegalStateException {
		synchronized (this.startupShutdownMonitor) {
			StartupStep contextRefresh = this.applicationStartup.start("spring.context.refresh");

			// 1：准备刷新，设置环境、时间戳、标志位等
			prepareRefresh();

			// 2：获取 fresh 的 BeanFactory
			ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

			// 3：为 BeanFactory 做各种配置（ClassLoader、后处理器等）
			prepareBeanFactory(beanFactory);

			try {
				// 4：为容器的某些子类指定特殊的BeanPost 事件处理器
				postProcessBeanFactory(beanFactory);

				StartupStep beanPostProcess = this.applicationStartup.start("spring.context.beans.post-process");

				// 5：调用所有注册的Bean FactoryPostProcessor 的 Bean
				invokeBeanFactoryPostProcessors(beanFactory);

				// 6：为Bean Factory 注册 BeanPost 事件处理器.（例如 AOP、@Autowired 等）
				registerBeanPostProcessors(beanFactory);
				beanPostProcess.end();

				// 7：初始化信息源,和国际化相关.
				initMessageSource();

				// 8: 初始化容器事件传播器.
				initApplicationEventMulticaster();

				// 9: 调用子类的某些特殊 Bean 初始化方法
				onRefresh();

				// 10：为事件传播器注册事件监听器.
				registerListeners();

				// 11：初始化剩余非懒加载单例 Bean
				finishBeanFactoryInitialization(beanFactory);

				// 12：完成刷新，发布生命周期事件
				finishRefresh();
			} catch (BeansException ex) {
				if (logger.isWarnEnabled()) {
					logger.warn("初始化 Spring 应用上下文异常，取消刷新: " + ex);
				}
				// 销毁已创建的 bean
				destroyBeans();
				// 取消刷新标志位
				cancelRefresh(ex);
				throw ex;
			} finally {
				//
				resetCommonCaches();
				contextRefresh.end();
			}
		}
	}

	/**
	 * 容器准备阶段，设置启动时间、earlyApplicationListeners 等
	 */
	protected void prepareRefresh() {
		this.startupDate = System.currentTimeMillis();
		this.closed.set(false);
		this.active.set(true);

		if (logger.isDebugEnabled()) {
			logger.debug("刷新 Spring 应用上下文：" + getDisplayName());
		}

		// 初始化占位符配置（模板方法）
		initPropertySources();

		// 校验 environment 中配置项的有效性
		getEnvironment().validateRequiredProperties();

		// 保存早期监听器
		if (this.earlyApplicationListeners == null) {
			this.earlyApplicationListeners = new LinkedHashSet<>(this.applicationListeners);
		} else {
			this.applicationListeners.clear();
			this.applicationListeners.addAll(this.earlyApplicationListeners);
		}

		// 初始化早期事件集合（刷新阶段缓存）
		this.earlyApplicationEvents = new LinkedHashSet<>();
	}


	// 初始化属性资源（子类可重写添加属性源）
	protected void initPropertySources() {
	}

	// 获取新的 BeanFactory，通常在刷新上下文时调用
	protected ConfigurableListableBeanFactory obtainFreshBeanFactory() {
		refreshBeanFactory(); // 刷新 BeanFactory，清除旧的 Bean 定义
		return getBeanFactory(); // 返回新的 BeanFactory 实例
	}

	// 为 BeanFactory 做各种准备工作，比如注册后处理器、忽略某些接口的依赖注入等
	protected void prepareBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		// 设置类加载器
		beanFactory.setBeanClassLoader(getClassLoader());

		// 注册 Spring 表达式语言支持
		if (!shouldIgnoreSpel) {
			beanFactory.setBeanExpressionResolver(new StandardBeanExpressionResolver(beanFactory.getBeanClassLoader()));
		}

		// 注册资源编辑器（处理如 Resource 类型的属性）
		beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this, getEnvironment()));

		// 添加各种 Aware 处理器（ApplicationContextAware 等）
		beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));
		beanFactory.ignoreDependencyInterface(EnvironmentAware.class);
		beanFactory.ignoreDependencyInterface(EmbeddedValueResolverAware.class);
		beanFactory.ignoreDependencyInterface(ResourceLoaderAware.class);
		beanFactory.ignoreDependencyInterface(ApplicationEventPublisherAware.class);
		beanFactory.ignoreDependencyInterface(MessageSourceAware.class);
		beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);
		beanFactory.ignoreDependencyInterface(ApplicationStartupAware.class);

		// 注册一些自动解析的依赖类型
		beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);
		beanFactory.registerResolvableDependency(ResourceLoader.class, this);
		beanFactory.registerResolvableDependency(ApplicationEventPublisher.class, this);
		beanFactory.registerResolvableDependency(ApplicationContext.class, this);

		// 监听器探测器，判断是否实现 ApplicationListener 接口
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(this));

		// 如果存在 LoadTimeWeaver，注册相关后处理器和临时类加载器
		if (!NativeDetector.inNativeImage() && beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
			beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
			beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
		}

		// 注册 Environment、SystemProperties、SystemEnvironment、ApplicationStartup 等单例
		if (!beanFactory.containsLocalBean(ENVIRONMENT_BEAN_NAME)) {
			beanFactory.registerSingleton(ENVIRONMENT_BEAN_NAME, getEnvironment());
		}
		if (!beanFactory.containsLocalBean(SYSTEM_PROPERTIES_BEAN_NAME)) {
			beanFactory.registerSingleton(SYSTEM_PROPERTIES_BEAN_NAME, getEnvironment().getSystemProperties());
		}
		if (!beanFactory.containsLocalBean(SYSTEM_ENVIRONMENT_BEAN_NAME)) {
			beanFactory.registerSingleton(SYSTEM_ENVIRONMENT_BEAN_NAME, getEnvironment().getSystemEnvironment());
		}
		if (!beanFactory.containsLocalBean(APPLICATION_STARTUP_BEAN_NAME)) {
			beanFactory.registerSingleton(APPLICATION_STARTUP_BEAN_NAME, getApplicationStartup());
		}
	}

	// 提供给子类进行自定义 BeanFactory 的扩展（默认空实现）
	protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
	}

	// 执行 BeanFactory 的后处理器（包括 BeanDefinitionRegistryPostProcessor）
	protected void invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory) {
		PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors(beanFactory, getBeanFactoryPostProcessors());

		// 再次判断 LoadTimeWeaver 是否存在
		if (!NativeDetector.inNativeImage() && beanFactory.getTempClassLoader() == null &&
				beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
			beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
			beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
		}
	}

	// 注册所有 BeanPostProcessor（自动注入、生命周期回调等）
	protected void registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory) {
		PostProcessorRegistrationDelegate.registerBeanPostProcessors(beanFactory, this);
	}

	// 初始化 MessageSource，用于国际化支持
	protected void initMessageSource() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		if (beanFactory.containsLocalBean(MESSAGE_SOURCE_BEAN_NAME)) {
			this.messageSource = beanFactory.getBean(MESSAGE_SOURCE_BEAN_NAME, MessageSource.class);
			// 如果是层级 MessageSource，则设置父级
			if (this.parent != null && this.messageSource instanceof HierarchicalMessageSource) {
				HierarchicalMessageSource hms = (HierarchicalMessageSource) this.messageSource;
				if (hms.getParentMessageSource() == null) {
					hms.setParentMessageSource(getInternalParentMessageSource());
				}
			}
		} else {
			// 没有自定义的 MessageSource，使用默认的
			DelegatingMessageSource dms = new DelegatingMessageSource();
			dms.setParentMessageSource(getInternalParentMessageSource());
			this.messageSource = dms;
			beanFactory.registerSingleton(MESSAGE_SOURCE_BEAN_NAME, this.messageSource);
		}
	}

	// 初始化 ApplicationEventMulticaster，负责事件广播
	protected void initApplicationEventMulticaster() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		if (beanFactory.containsLocalBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME)) {
			this.applicationEventMulticaster =
					beanFactory.getBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, ApplicationEventMulticaster.class);
		} else {
			this.applicationEventMulticaster = new SimpleApplicationEventMulticaster(beanFactory);
			beanFactory.registerSingleton(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, this.applicationEventMulticaster);
		}
	}

	// 初始化生命周期处理器（负责容器启动与关闭时生命周期管理）
	protected void initLifecycleProcessor() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		if (beanFactory.containsLocalBean(LIFECYCLE_PROCESSOR_BEAN_NAME)) {
			this.lifecycleProcessor = beanFactory.getBean(LIFECYCLE_PROCESSOR_BEAN_NAME, LifecycleProcessor.class);
		} else {
			DefaultLifecycleProcessor defaultProcessor = new DefaultLifecycleProcessor();
			defaultProcessor.setBeanFactory(beanFactory);
			this.lifecycleProcessor = defaultProcessor;
			beanFactory.registerSingleton(LIFECYCLE_PROCESSOR_BEAN_NAME, this.lifecycleProcessor);
		}
	}

	// 提供给子类自定义刷新逻辑（默认空实现）
	protected void onRefresh() throws BeansException {
	}

	// 注册 ApplicationListener（监听器），包括早期事件广播
	protected void registerListeners() {
		// 注册通过编程方式添加的监听器
		for (ApplicationListener<?> listener : getApplicationListeners()) {
			getApplicationEventMulticaster().addApplicationListener(listener);
		}
		// 注册配置的监听器 Bean
		String[] listenerBeanNames = getBeanNamesForType(ApplicationListener.class, true, false);
		for (String listenerBeanName : listenerBeanNames) {
			getApplicationEventMulticaster().addApplicationListenerBean(listenerBeanName);
		}
		// 处理早期发布的事件
		Set<ApplicationEvent> earlyEventsToProcess = this.earlyApplicationEvents;
		this.earlyApplicationEvents = null;
		if (!CollectionUtils.isEmpty(earlyEventsToProcess)) {
			for (ApplicationEvent earlyEvent : earlyEventsToProcess) {
				getApplicationEventMulticaster().multicastEvent(earlyEvent);
			}
		}
	}

	// 完成 BeanFactory 初始化，包括：ConversionService、嵌入值解析器、单例预实例化等
	protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
		// 配置 ConversionService
		if (beanFactory.containsBean(CONVERSION_SERVICE_BEAN_NAME) &&
				beanFactory.isTypeMatch(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class)) {
			beanFactory.setConversionService(beanFactory.getBean(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class));
		}

		// 配置嵌入值解析器（占位符处理）
		if (!beanFactory.hasEmbeddedValueResolver()) {
			beanFactory.addEmbeddedValueResolver(strVal -> getEnvironment().resolvePlaceholders(strVal));
		}

		// 提前实例化实现 LoadTimeWeaverAware 的 Bean
		String[] weaverAwareNames = beanFactory.getBeanNamesForType(LoadTimeWeaverAware.class, false, false);
		for (String weaverAwareName : weaverAwareNames) {
			getBean(weaverAwareName);
		}

		beanFactory.setTempClassLoader(null); // 移除临时类加载器

		beanFactory.freezeConfiguration(); // 冻结配置，禁止后续修改

		beanFactory.preInstantiateSingletons(); // 实例化非懒加载单例 Bean
	}

	// 刷新完成后的最后处理
	protected void finishRefresh() {
		clearResourceCaches(); // 清除缓存
		initLifecycleProcessor(); // 初始化生命周期处理器
		getLifecycleProcessor().onRefresh(); // 通知刷新完成
		publishEvent(new ContextRefreshedEvent(this)); // 发布 ContextRefreshedEvent
		if (!NativeDetector.inNativeImage()) {
			LiveBeansView.registerApplicationContext(this); // 注册 LiveBeans 视图（用于监控）
		}
	}

	// 取消刷新操作，设置容器为非活动状态
	protected void cancelRefresh(BeansException ex) {
		this.active.set(false);
	}

	// 重置通用缓存（反射、注解、类型等）
	protected void resetCommonCaches() {
		ReflectionUtils.clearCache();
		AnnotationUtils.clearCache();
		ResolvableType.clearCache();
		CachedIntrospectionResults.clearClassLoader(getClassLoader());
	}

	// 注册 JVM 关闭钩子，优雅关闭容器
	@Override
	public void registerShutdownHook() {
		if (this.shutdownHook == null) {
			this.shutdownHook = new Thread(SHUTDOWN_HOOK_THREAD_NAME) {
				@Override
				public void run() {
					synchronized (startupShutdownMonitor) {
						doClose();
					}
				}
			};
			Runtime.getRuntime().addShutdownHook(this.shutdownHook);
		}
	}

	// 销毁方法（旧版）
	@Deprecated
	public void destroy() {
		close();
	}

	// 手动关闭容器
	@Override
	public void close() {
		synchronized (this.startupShutdownMonitor) {
			doClose();
			if (this.shutdownHook != null) {
				try {
					Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
				} catch (IllegalStateException ex) {
					// JVM 正在关闭
				}
			}
		}
	}

	// 执行关闭操作，包括事件发布、Bean销毁、BeanFactory关闭等
	@SuppressWarnings("deprecation")
	protected void doClose() {
		if (this.active.get() && this.closed.compareAndSet(false, true)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Closing " + this);
			}
			if (!NativeDetector.inNativeImage()) {
				LiveBeansView.unregisterApplicationContext(this);
			}
			try {
				publishEvent(new ContextClosedEvent(this));
			} catch (Throwable ex) {
				logger.warn("Exception thrown from ApplicationListener handling ContextClosedEvent", ex);
			}
			if (this.lifecycleProcessor != null) {
				try {
					this.lifecycleProcessor.onClose();
				} catch (Throwable ex) {
					logger.warn("Exception thrown from LifecycleProcessor on context close", ex);
				}
			}
			destroyBeans(); // 销毁所有单例 Bean
			closeBeanFactory(); // 关闭 BeanFactory
			onClose(); // 提供给子类自定义关闭逻辑
			resetCommonCaches(); // 重置缓存
			if (this.earlyApplicationListeners != null) {
				this.applicationListeners.clear();
				this.applicationListeners.addAll(this.earlyApplicationListeners);
			}
			this.active.set(false); // 标记为关闭
		}
	}

	// 销毁所有单例 Bean
	protected void destroyBeans() {
		getBeanFactory().destroySingletons();
	}

	// 子类可重写的关闭钩子
	protected void onClose() {
	}

	// 判断上下文是否处于活动状态
	@Override
	public boolean isActive() {
		return this.active.get();
	}

	// 断言 BeanFactory 活动状态（抛出未刷新或已关闭异常）
	protected void assertBeanFactoryActive() {
		if (!this.active.get()) {
			if (this.closed.get()) {
				throw new IllegalStateException(getDisplayName() + " has been closed already");
			} else {
				throw new IllegalStateException(getDisplayName() + " has not been refreshed yet");
			}
		}
	}


	@Override
	public Object getBean(String name) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name);
	}

	@Override
	public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name, requiredType);
	}

	@Override
	public Object getBean(String name, Object... args) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name, args);
	}

	@Override
	public <T> T getBean(Class<T> requiredType) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(requiredType);
	}

	@Override
	public <T> T getBean(Class<T> requiredType, Object... args) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(requiredType, args);
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanProvider(requiredType);
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanProvider(requiredType);
	}

	@Override
	public boolean containsBean(String name) {
		return getBeanFactory().containsBean(name);
	}

	@Override
	public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isSingleton(name);
	}

	@Override
	public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isPrototype(name);
	}

	@Override
	public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isTypeMatch(name, typeToMatch);
	}

	@Override
	public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isTypeMatch(name, typeToMatch);
	}

	@Override
	@Nullable
	public Class<?> getType(String name) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().getType(name);
	}

	@Override
	@Nullable
	public Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().getType(name, allowFactoryBeanInit);
	}

	@Override
	public String[] getAliases(String name) {
		return getBeanFactory().getAliases(name);
	}


	@Override
	public boolean containsBeanDefinition(String beanName) {
		return getBeanFactory().containsBeanDefinition(beanName);
	}

	@Override
	public int getBeanDefinitionCount() {
		return getBeanFactory().getBeanDefinitionCount();
	}

	@Override
	public String[] getBeanDefinitionNames() {
		return getBeanFactory().getBeanDefinitionNames();
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType, boolean allowEagerInit) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanProvider(requiredType, allowEagerInit);
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType, boolean allowEagerInit) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanProvider(requiredType, allowEagerInit);
	}

	@Override
	public String[] getBeanNamesForType(ResolvableType type) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type);
	}

	@Override
	public String[] getBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
	}

	@Override
	public String[] getBeanNamesForType(@Nullable Class<?> type) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type);
	}

	@Override
	public String[] getBeanNamesForType(@Nullable Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
	}

	@Override
	public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBeansOfType(type);
	}

	@Override
	public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type, boolean includeNonSingletons, boolean allowEagerInit)
			throws BeansException {

		assertBeanFactoryActive();
		return getBeanFactory().getBeansOfType(type, includeNonSingletons, allowEagerInit);
	}

	@Override
	public String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForAnnotation(annotationType);
	}

	@Override
	public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType)
			throws BeansException {

		assertBeanFactoryActive();
		return getBeanFactory().getBeansWithAnnotation(annotationType);
	}

	@Override
	@Nullable
	public <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType)
			throws NoSuchBeanDefinitionException {

		assertBeanFactoryActive();
		return getBeanFactory().findAnnotationOnBean(beanName, annotationType);
	}

	@Override
	@Nullable
	public <A extends Annotation> A findAnnotationOnBean(
			String beanName, Class<A> annotationType, boolean allowFactoryBeanInit)
			throws NoSuchBeanDefinitionException {

		assertBeanFactoryActive();
		return getBeanFactory().findAnnotationOnBean(beanName, annotationType, allowFactoryBeanInit);
	}


	@Override
	@Nullable
	public BeanFactory getParentBeanFactory() {
		return getParent();
	}

	@Override
	public boolean containsLocalBean(String name) {
		return getBeanFactory().containsLocalBean(name);
	}

	@Nullable
	protected BeanFactory getInternalParentBeanFactory() {
		return (getParent() instanceof ConfigurableApplicationContext ?
				((ConfigurableApplicationContext) getParent()).getBeanFactory() : getParent());
	}


	@Override
	public String getMessage(String code, @Nullable Object[] args, @Nullable String defaultMessage, Locale locale) {
		return getMessageSource().getMessage(code, args, defaultMessage, locale);
	}

	@Override
	public String getMessage(String code, @Nullable Object[] args, Locale locale) throws NoSuchMessageException {
		return getMessageSource().getMessage(code, args, locale);
	}

	@Override
	public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
		return getMessageSource().getMessage(resolvable, locale);
	}

	private MessageSource getMessageSource() throws IllegalStateException {
		if (this.messageSource == null) {
			throw new IllegalStateException("MessageSource not initialized - " +
					"call 'refresh' before accessing messages via the context: " + this);
		}
		return this.messageSource;
	}

	@Nullable
	protected MessageSource getInternalParentMessageSource() {
		return (getParent() instanceof AbstractApplicationContext ?
				((AbstractApplicationContext) getParent()).messageSource : getParent());
	}


	@Override
	public Resource[] getResources(String locationPattern) throws IOException {
		return this.resourcePatternResolver.getResources(locationPattern);
	}


	@Override
	public void start() {
		getLifecycleProcessor().start();
		publishEvent(new ContextStartedEvent(this));
	}

	@Override
	public void stop() {
		getLifecycleProcessor().stop();
		publishEvent(new ContextStoppedEvent(this));
	}

	@Override
	public boolean isRunning() {
		return (this.lifecycleProcessor != null && this.lifecycleProcessor.isRunning());
	}


	protected abstract void refreshBeanFactory() throws BeansException, IllegalStateException;

	protected abstract void closeBeanFactory();

	@Override
	public abstract ConfigurableListableBeanFactory getBeanFactory() throws IllegalStateException;


	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(getDisplayName());
		sb.append(", started on ").append(new Date(getStartupDate()));
		ApplicationContext parent = getParent();
		if (parent != null) {
			sb.append(", parent: ").append(parent.getDisplayName());
		}
		return sb.toString();
	}

}
