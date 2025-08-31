package org.springframework.web.servlet;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import javax.servlet.DispatcherType;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.SourceFilteringListener;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.i18n.SimpleLocaleContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.ConfigurableWebEnvironment;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.context.support.ServletRequestHandledEvent;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.context.support.XmlWebApplicationContext;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.util.NestedServletException;
import org.springframework.web.util.WebUtils;

@SuppressWarnings("serial")
public abstract class FrameworkServlet extends HttpServletBean implements ApplicationContextAware {

	public static final String DEFAULT_NAMESPACE_SUFFIX = "-servlet";

	public static final Class<?> DEFAULT_CONTEXT_CLASS = XmlWebApplicationContext.class;

	public static final String SERVLET_CONTEXT_PREFIX = FrameworkServlet.class.getName() + ".CONTEXT.";

	private static final String INIT_PARAM_DELIMITERS = ",; \t\n";


	@Nullable
	private String contextAttribute;

	private Class<?> contextClass = DEFAULT_CONTEXT_CLASS;

	@Nullable
	private String contextId;

	@Nullable
	private String namespace;

	@Nullable
	private String contextConfigLocation;

	private final List<ApplicationContextInitializer<ConfigurableApplicationContext>> contextInitializers =
			new ArrayList<>();

	@Nullable
	private String contextInitializerClasses;

	private boolean publishContext = true;

	private boolean publishEvents = true;

	private boolean threadContextInheritable = false;

	private boolean dispatchOptionsRequest = false;

	private boolean dispatchTraceRequest = false;

	private boolean enableLoggingRequestDetails = false;

	@Nullable
	private WebApplicationContext webApplicationContext;

	private boolean webApplicationContextInjected = false;

	private volatile boolean refreshEventReceived;

	private final Object onRefreshMonitor = new Object();


	public FrameworkServlet() {
	}

	public FrameworkServlet(WebApplicationContext webApplicationContext) {
		this.webApplicationContext = webApplicationContext;
	}


	public void setContextAttribute(@Nullable String contextAttribute) {
		this.contextAttribute = contextAttribute;
	}

	@Nullable
	public String getContextAttribute() {
		return this.contextAttribute;
	}

	public void setContextClass(Class<?> contextClass) {
		this.contextClass = contextClass;
	}

	public Class<?> getContextClass() {
		return this.contextClass;
	}

	public void setContextId(@Nullable String contextId) {
		this.contextId = contextId;
	}

	@Nullable
	public String getContextId() {
		return this.contextId;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public String getNamespace() {
		return (this.namespace != null ? this.namespace : getServletName() + DEFAULT_NAMESPACE_SUFFIX);
	}

	public void setContextConfigLocation(@Nullable String contextConfigLocation) {
		this.contextConfigLocation = contextConfigLocation;
	}

	@Nullable
	public String getContextConfigLocation() {
		return this.contextConfigLocation;
	}

	@SuppressWarnings("unchecked")
	public void setContextInitializers(@Nullable ApplicationContextInitializer<?>... initializers) {
		if (initializers != null) {
			for (ApplicationContextInitializer<?> initializer : initializers) {
				this.contextInitializers.add((ApplicationContextInitializer<ConfigurableApplicationContext>) initializer);
			}
		}
	}

	public void setContextInitializerClasses(String contextInitializerClasses) {
		this.contextInitializerClasses = contextInitializerClasses;
	}

	public void setPublishContext(boolean publishContext) {
		this.publishContext = publishContext;
	}

	public void setPublishEvents(boolean publishEvents) {
		this.publishEvents = publishEvents;
	}

	public void setThreadContextInheritable(boolean threadContextInheritable) {
		this.threadContextInheritable = threadContextInheritable;
	}

	public void setDispatchOptionsRequest(boolean dispatchOptionsRequest) {
		this.dispatchOptionsRequest = dispatchOptionsRequest;
	}

	public void setDispatchTraceRequest(boolean dispatchTraceRequest) {
		this.dispatchTraceRequest = dispatchTraceRequest;
	}

	public void setEnableLoggingRequestDetails(boolean enable) {
		this.enableLoggingRequestDetails = enable;
	}

	public boolean isEnableLoggingRequestDetails() {
		return this.enableLoggingRequestDetails;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		if (this.webApplicationContext == null && applicationContext instanceof WebApplicationContext) {
			this.webApplicationContext = (WebApplicationContext) applicationContext;
			this.webApplicationContextInjected = true;
		}
	}


	@Override
	protected final void initServletBean() throws ServletException {
		// 在 ServletContext 中记录初始化日志，表明当前 Servlet 开始初始化
		getServletContext().log("Initializing Spring " + getClass().getSimpleName() + " '" + getServletName() + "'");

		// 如果日志级别为 INFO，则输出初始化 Servlet 的日志
		if (logger.isInfoEnabled()) {
			logger.info("Initializing Servlet '" + getServletName() + "'");
		}

		// 记录初始化开始时间，用于后续计算耗时
		long startTime = System.currentTimeMillis();

		try {
			// 初始化 WebApplicationContext（即 Spring 的 Web 容器）
			this.webApplicationContext = initWebApplicationContext();

			// 初始化 FrameworkServlet 本身（子类可扩展此方法以完成额外初始化逻辑）
			initFrameworkServlet();
		}
		catch (ServletException | RuntimeException ex) {
			// 如果初始化过程抛出异常，则记录错误日志并重新抛出
			logger.error("Context initialization failed", ex);
			throw ex;
		}

		// 如果日志级别为 DEBUG，输出是否启用详细请求日志的相关提示
		if (logger.isDebugEnabled()) {
			String value = this.enableLoggingRequestDetails ?
					"shown which may lead to unsafe logging of potentially sensitive data" :
					"masked to prevent unsafe logging of potentially sensitive data";
			logger.debug("enableLoggingRequestDetails='" + this.enableLoggingRequestDetails +
					"': request parameters and headers will be " + value);
		}

		// 输出初始化完成日志及耗时信息
		if (logger.isInfoEnabled()) {
			logger.info("Completed initialization in " + (System.currentTimeMillis() - startTime) + " ms");
		}
	}

	protected WebApplicationContext initWebApplicationContext() {
		// 从 ServletContext 中获取 root WebApplicationContext（通常是 ContextLoaderListener 加载的）
		WebApplicationContext rootContext =
				WebApplicationContextUtils.getWebApplicationContext(getServletContext());

		WebApplicationContext wac = null;

		// 如果当前 Servlet 自身已经设置了 webApplicationContext（通常是通过 set 方法注入）
		if (this.webApplicationContext != null) {
			wac = this.webApplicationContext;

			// 如果它是可配置的（可以设置父容器、手动刷新等）
			if (wac instanceof ConfigurableWebApplicationContext) {
				ConfigurableWebApplicationContext cwac = (ConfigurableWebApplicationContext) wac;

				// 如果尚未激活（即还没有 refresh）
				if (!cwac.isActive()) {
					// 如果还未设置父容器，则设置为 rootContext
					if (cwac.getParent() == null) {
						cwac.setParent(rootContext);
					}

					// 配置并刷新当前 WebApplicationContext, IOC容器初始化
					configureAndRefreshWebApplicationContext(cwac);
				}
			}
		}

		// 如果 wac 仍为空，尝试从 ServletContext 中查找（例如通过 contextAttribute 指定）
		if (wac == null) {
			wac = findWebApplicationContext();
		}

		// 如果仍然找不到，就创建一个新的 WebApplicationContext（使用默认或配置的 contextClass）
		if (wac == null) {
			wac = createWebApplicationContext(rootContext);
		}

		// 如果当前 Servlet 尚未收到 ContextRefreshedEvent（说明 context 刚刚初始化完成）
		if (!this.refreshEventReceived) {
			// 使用同步块确保 onRefresh 不会被并发调用
			synchronized (this.onRefreshMonitor) {
				// 调用模板方法，初始化 Spring MVC 的各类组件（如 HandlerMapping、ViewResolver 等）
				onRefresh(wac);
			}
		}

		// 如果配置了要将 context 发布到 ServletContext 中，则设置为属性，供其他组件共享使用
		if (this.publishContext) {
			String attrName = getServletContextAttributeName();
			getServletContext().setAttribute(attrName, wac);
		}

		// 返回最终初始化完成的 WebApplicationContext
		return wac;
	}

	@Nullable
	protected WebApplicationContext findWebApplicationContext() {
		String attrName = getContextAttribute();
		if (attrName == null) {
			return null;
		}
		WebApplicationContext wac =
				WebApplicationContextUtils.getWebApplicationContext(getServletContext(), attrName);
		if (wac == null) {
			throw new IllegalStateException("No WebApplicationContext found: initializer not registered?");
		}
		return wac;
	}

	protected WebApplicationContext createWebApplicationContext(@Nullable ApplicationContext parent) {
		// XmlWebApplicationContext
		Class<?> contextClass = getContextClass();
		if (!ConfigurableWebApplicationContext.class.isAssignableFrom(contextClass)) {
			throw new ApplicationContextException(
					"Fatal initialization error in servlet with name '" + getServletName() +
					"': custom WebApplicationContext class [" + contextClass.getName() +
					"] is not of type ConfigurableWebApplicationContext");
		}
		ConfigurableWebApplicationContext wac =
				(ConfigurableWebApplicationContext) BeanUtils.instantiateClass(contextClass);

		wac.setEnvironment(getEnvironment());
		wac.setParent(parent);
		String configLocation = getContextConfigLocation();
		if (configLocation != null) {
			wac.setConfigLocation(configLocation);
		}
		configureAndRefreshWebApplicationContext(wac);

		return wac;
	}

	protected void configureAndRefreshWebApplicationContext(ConfigurableWebApplicationContext wac) {
		if (ObjectUtils.identityToString(wac).equals(wac.getId())) {
			if (this.contextId != null) {
				wac.setId(this.contextId);
			}
			else {
				wac.setId(ConfigurableWebApplicationContext.APPLICATION_CONTEXT_ID_PREFIX +
						ObjectUtils.getDisplayString(getServletContext().getContextPath()) + '/' + getServletName());
			}
		}

		wac.setServletContext(getServletContext());
		wac.setServletConfig(getServletConfig());
		wac.setNamespace(getNamespace());
		wac.addApplicationListener(new SourceFilteringListener(wac, new ContextRefreshListener()));

		ConfigurableEnvironment env = wac.getEnvironment();
		if (env instanceof ConfigurableWebEnvironment) {
			((ConfigurableWebEnvironment) env).initPropertySources(getServletContext(), getServletConfig());
		}

		postProcessWebApplicationContext(wac);
		applyInitializers(wac);
		// 真正开始初始化IOC容器
		wac.refresh();
	}

	protected WebApplicationContext createWebApplicationContext(@Nullable WebApplicationContext parent) {
		return createWebApplicationContext((ApplicationContext) parent);
	}

	protected void postProcessWebApplicationContext(ConfigurableWebApplicationContext wac) {
	}

	protected void applyInitializers(ConfigurableApplicationContext wac) {
		String globalClassNames = getServletContext().getInitParameter(ContextLoader.GLOBAL_INITIALIZER_CLASSES_PARAM);
		if (globalClassNames != null) {
			for (String className : StringUtils.tokenizeToStringArray(globalClassNames, INIT_PARAM_DELIMITERS)) {
				this.contextInitializers.add(loadInitializer(className, wac));
			}
		}

		if (this.contextInitializerClasses != null) {
			for (String className : StringUtils.tokenizeToStringArray(this.contextInitializerClasses, INIT_PARAM_DELIMITERS)) {
				this.contextInitializers.add(loadInitializer(className, wac));
			}
		}

		AnnotationAwareOrderComparator.sort(this.contextInitializers);
		for (ApplicationContextInitializer<ConfigurableApplicationContext> initializer : this.contextInitializers) {
			initializer.initialize(wac);
		}
	}

	@SuppressWarnings("unchecked")
	private ApplicationContextInitializer<ConfigurableApplicationContext> loadInitializer(
			String className, ConfigurableApplicationContext wac) {
		try {
			Class<?> initializerClass = ClassUtils.forName(className, wac.getClassLoader());
			Class<?> initializerContextClass =
					GenericTypeResolver.resolveTypeArgument(initializerClass, ApplicationContextInitializer.class);
			if (initializerContextClass != null && !initializerContextClass.isInstance(wac)) {
				throw new ApplicationContextException(String.format(
						"Could not apply context initializer [%s] since its generic parameter [%s] " +
						"is not assignable from the type of application context used by this " +
						"framework servlet: [%s]", initializerClass.getName(), initializerContextClass.getName(),
						wac.getClass().getName()));
			}
			return BeanUtils.instantiateClass(initializerClass, ApplicationContextInitializer.class);
		}
		catch (ClassNotFoundException ex) {
			throw new ApplicationContextException(String.format("Could not load class [%s] specified " +
					"via 'contextInitializerClasses' init-param", className), ex);
		}
	}

	public String getServletContextAttributeName() {
		return SERVLET_CONTEXT_PREFIX + getServletName();
	}

	@Nullable
	public final WebApplicationContext getWebApplicationContext() {
		return this.webApplicationContext;
	}


	protected void initFrameworkServlet() throws ServletException {
	}

	public void refresh() {
		WebApplicationContext wac = getWebApplicationContext();
		if (!(wac instanceof ConfigurableApplicationContext)) {
			throw new IllegalStateException("WebApplicationContext does not support refresh: " + wac);
		}
		((ConfigurableApplicationContext) wac).refresh();
	}

	public void onApplicationEvent(ContextRefreshedEvent event) {
		this.refreshEventReceived = true;
		synchronized (this.onRefreshMonitor) {
			onRefresh(event.getApplicationContext());
		}
	}

	protected void onRefresh(ApplicationContext context) {
	}

	@Override
	public void destroy() {
		getServletContext().log("Destroying Spring FrameworkServlet '" + getServletName() + "'");
		if (this.webApplicationContext instanceof ConfigurableApplicationContext && !this.webApplicationContextInjected) {
			((ConfigurableApplicationContext) this.webApplicationContext).close();
		}
	}


	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		HttpMethod httpMethod = HttpMethod.resolve(request.getMethod());
		if (httpMethod == HttpMethod.PATCH || httpMethod == null) {
			processRequest(request, response);
		}
		else {
			super.service(request, response);
		}
	}

	@Override
	protected final void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		processRequest(request, response);
	}

	@Override
	protected final void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		processRequest(request, response);
	}

	@Override
	protected final void doPut(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		processRequest(request, response);
	}

	@Override
	protected final void doDelete(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		processRequest(request, response);
	}

	@Override
	protected void doOptions(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		if (this.dispatchOptionsRequest || CorsUtils.isPreFlightRequest(request)) {
			processRequest(request, response);
			if (response.containsHeader("Allow")) {
				return;
			}
		}

		super.doOptions(request, new HttpServletResponseWrapper(response) {
			@Override
			public void setHeader(String name, String value) {
				if ("Allow".equals(name)) {
					value = (StringUtils.hasLength(value) ? value + ", " : "") + HttpMethod.PATCH.name();
				}
				super.setHeader(name, value);
			}
		});
	}

	@Override
	protected void doTrace(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		if (this.dispatchTraceRequest) {
			processRequest(request, response);
			if ("message/http".equals(response.getContentType())) {
				return;
			}
		}
		super.doTrace(request, response);
	}

	protected final void processRequest(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		long startTime = System.currentTimeMillis();
		Throwable failureCause = null;

		LocaleContext previousLocaleContext = LocaleContextHolder.getLocaleContext();
		LocaleContext localeContext = buildLocaleContext(request);

		RequestAttributes previousAttributes = RequestContextHolder.getRequestAttributes();
		ServletRequestAttributes requestAttributes = buildRequestAttributes(request, response, previousAttributes);

		WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
		asyncManager.registerCallableInterceptor(FrameworkServlet.class.getName(), new RequestBindingInterceptor());

		initContextHolders(request, localeContext, requestAttributes);

		try {
			doService(request, response);
		}
		catch (ServletException | IOException ex) {
			failureCause = ex;
			throw ex;
		}
		catch (Throwable ex) {
			failureCause = ex;
			throw new NestedServletException("Request processing failed", ex);
		}

		finally {
			resetContextHolders(request, previousLocaleContext, previousAttributes);
			if (requestAttributes != null) {
				requestAttributes.requestCompleted();
			}
			logResult(request, response, failureCause, asyncManager);
			publishRequestHandledEvent(request, response, startTime, failureCause);
		}
	}

	@Nullable
	protected LocaleContext buildLocaleContext(HttpServletRequest request) {
		return new SimpleLocaleContext(request.getLocale());
	}

	@Nullable
	protected ServletRequestAttributes buildRequestAttributes(HttpServletRequest request,
			@Nullable HttpServletResponse response, @Nullable RequestAttributes previousAttributes) {

		if (previousAttributes == null || previousAttributes instanceof ServletRequestAttributes) {
			return new ServletRequestAttributes(request, response);
		}
		else {
			return null;
		}
	}

	private void initContextHolders(HttpServletRequest request,
			@Nullable LocaleContext localeContext, @Nullable RequestAttributes requestAttributes) {

		if (localeContext != null) {
			LocaleContextHolder.setLocaleContext(localeContext, this.threadContextInheritable);
		}
		if (requestAttributes != null) {
			RequestContextHolder.setRequestAttributes(requestAttributes, this.threadContextInheritable);
		}
	}

	private void resetContextHolders(HttpServletRequest request,
			@Nullable LocaleContext prevLocaleContext, @Nullable RequestAttributes previousAttributes) {

		LocaleContextHolder.setLocaleContext(prevLocaleContext, this.threadContextInheritable);
		RequestContextHolder.setRequestAttributes(previousAttributes, this.threadContextInheritable);
	}

	private void logResult(HttpServletRequest request, HttpServletResponse response,
			@Nullable Throwable failureCause, WebAsyncManager asyncManager) {

		if (!logger.isDebugEnabled()) {
			return;
		}

		DispatcherType dispatchType = request.getDispatcherType();
		boolean initialDispatch = (dispatchType == DispatcherType.REQUEST);

		if (failureCause != null) {
			if (!initialDispatch) {
				if (logger.isDebugEnabled()) {
					logger.debug("Unresolved failure from \"" + dispatchType + "\" dispatch: " + failureCause);
				}
			}
			else if (logger.isTraceEnabled()) {
				logger.trace("Failed to complete request", failureCause);
			}
			else {
				logger.debug("Failed to complete request: " + failureCause);
			}
			return;
		}

		if (asyncManager.isConcurrentHandlingStarted()) {
			logger.debug("Exiting but response remains open for further handling");
			return;
		}

		int status = response.getStatus();
		String headers = "";

		if (logger.isTraceEnabled()) {
			Collection<String> names = response.getHeaderNames();
			if (this.enableLoggingRequestDetails) {
				headers = names.stream().map(name -> name + ":" + response.getHeaders(name))
						.collect(Collectors.joining(", "));
			}
			else {
				headers = names.isEmpty() ? "" : "masked";
			}
			headers = ", headers={" + headers + "}";
		}

		if (!initialDispatch) {
			logger.debug("Exiting from \"" + dispatchType + "\" dispatch, status " + status + headers);
		}
		else {
			HttpStatus httpStatus = HttpStatus.resolve(status);
			logger.debug("Completed " + (httpStatus != null ? httpStatus : status) + headers);
		}
	}

	private void publishRequestHandledEvent(HttpServletRequest request, HttpServletResponse response,
			long startTime, @Nullable Throwable failureCause) {

		if (this.publishEvents && this.webApplicationContext != null) {
			long processingTime = System.currentTimeMillis() - startTime;
			this.webApplicationContext.publishEvent(
					new ServletRequestHandledEvent(this,
							request.getRequestURI(), request.getRemoteAddr(),
							request.getMethod(), getServletConfig().getServletName(),
							WebUtils.getSessionId(request), getUsernameForRequest(request),
							processingTime, failureCause, response.getStatus()));
		}
	}

	@Nullable
	protected String getUsernameForRequest(HttpServletRequest request) {
		Principal userPrincipal = request.getUserPrincipal();
		return (userPrincipal != null ? userPrincipal.getName() : null);
	}


	protected abstract void doService(HttpServletRequest request, HttpServletResponse response)
			throws Exception;


	private class ContextRefreshListener implements ApplicationListener<ContextRefreshedEvent> {

		@Override
		public void onApplicationEvent(ContextRefreshedEvent event) {
			FrameworkServlet.this.onApplicationEvent(event);
		}
	}


	private class RequestBindingInterceptor implements CallableProcessingInterceptor {

		@Override
		public <T> void preProcess(NativeWebRequest webRequest, Callable<T> task) {
			HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
			if (request != null) {
				HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
				initContextHolders(request, buildLocaleContext(request),
						buildRequestAttributes(request, response, null));
			}
		}
		@Override
		public <T> void postProcess(NativeWebRequest webRequest, Callable<T> task, Object concurrentResult) {
			HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
			if (request != null) {
				resetContextHolders(request, null, null);
			}
		}
	}

}
