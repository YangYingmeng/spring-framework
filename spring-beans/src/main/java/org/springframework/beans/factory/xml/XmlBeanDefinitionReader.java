package org.springframework.beans.factory.xml;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.parsing.EmptyReaderEventListener;
import org.springframework.beans.factory.parsing.FailFastProblemReporter;
import org.springframework.beans.factory.parsing.NullSourceExtractor;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.parsing.ReaderEventListener;
import org.springframework.beans.factory.parsing.SourceExtractor;
import org.springframework.beans.factory.support.AbstractBeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.Constants;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.io.DescriptiveResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.xml.SimpleSaxErrorHandler;
import org.springframework.util.xml.XmlValidationModeDetector;

/**
 * XML 配置文件的 Bean 定义读取器。
 * <p>
 * 继承自 AbstractBeanDefinitionReader，实现基于 XML 格式的 Spring Bean 配置文件的加载和解析。
 * 支持校验模式设置、命名空间支持、错误处理等功能。
 */
public class XmlBeanDefinitionReader extends AbstractBeanDefinitionReader {

	// 验证模式常量，来自 XmlValidationModeDetector
	public static final int VALIDATION_NONE = XmlValidationModeDetector.VALIDATION_NONE;

	public static final int VALIDATION_AUTO = XmlValidationModeDetector.VALIDATION_AUTO;

	public static final int VALIDATION_DTD = XmlValidationModeDetector.VALIDATION_DTD;

	public static final int VALIDATION_XSD = XmlValidationModeDetector.VALIDATION_XSD;


	private static final Constants constants = new Constants(XmlBeanDefinitionReader.class);
	// 当前XML验证模式，默认自动检测
	private int validationMode = VALIDATION_AUTO;
	// 是否开启命名空间支持
	private boolean namespaceAware = false;
	// 用于解析XML的文档读取器类
	private Class<? extends BeanDefinitionDocumentReader> documentReaderClass =
			DefaultBeanDefinitionDocumentReader.class;
	// 问题报告器
	private ProblemReporter problemReporter = new FailFastProblemReporter();
	// 事件监听器
	private ReaderEventListener eventListener = new EmptyReaderEventListener();
	// 源提取器
	private SourceExtractor sourceExtractor = new NullSourceExtractor();
	// 命名空间处理器解析器
	@Nullable
	private NamespaceHandlerResolver namespaceHandlerResolver;
	// XML文档加载器
	private DocumentLoader documentLoader = new DefaultDocumentLoader();
	// 实体解析器
	@Nullable
	private EntityResolver entityResolver;
	// SAX错误处理器
	private ErrorHandler errorHandler = new SimpleSaxErrorHandler(logger);

	private final XmlValidationModeDetector validationModeDetector = new XmlValidationModeDetector();
	// 当前线程正在加载的资源集合，防止循环加载
	private final ThreadLocal<Set<EncodedResource>> resourcesCurrentlyBeingLoaded =
			new NamedThreadLocal<Set<EncodedResource>>("XML bean definition resources currently being loaded") {
				@Override
				protected Set<EncodedResource> initialValue() {
					return new HashSet<>(4);
				}
			};


	public XmlBeanDefinitionReader(BeanDefinitionRegistry registry) {
		super(registry);
	}


	public void setValidating(boolean validating) {
		this.validationMode = (validating ? VALIDATION_AUTO : VALIDATION_NONE);
		this.namespaceAware = !validating;
	}

	public void setValidationModeName(String validationModeName) {
		setValidationMode(constants.asNumber(validationModeName).intValue());
	}

	public void setValidationMode(int validationMode) {
		this.validationMode = validationMode;
	}

	public int getValidationMode() {
		return this.validationMode;
	}

	public void setNamespaceAware(boolean namespaceAware) {
		this.namespaceAware = namespaceAware;
	}

	public boolean isNamespaceAware() {
		return this.namespaceAware;
	}

	public void setProblemReporter(@Nullable ProblemReporter problemReporter) {
		this.problemReporter = (problemReporter != null ? problemReporter : new FailFastProblemReporter());
	}

	public void setEventListener(@Nullable ReaderEventListener eventListener) {
		this.eventListener = (eventListener != null ? eventListener : new EmptyReaderEventListener());
	}

	public void setSourceExtractor(@Nullable SourceExtractor sourceExtractor) {
		this.sourceExtractor = (sourceExtractor != null ? sourceExtractor : new NullSourceExtractor());
	}

	public void setNamespaceHandlerResolver(@Nullable NamespaceHandlerResolver namespaceHandlerResolver) {
		this.namespaceHandlerResolver = namespaceHandlerResolver;
	}

	public void setDocumentLoader(@Nullable DocumentLoader documentLoader) {
		this.documentLoader = (documentLoader != null ? documentLoader : new DefaultDocumentLoader());
	}

	public void setEntityResolver(@Nullable EntityResolver entityResolver) {
		this.entityResolver = entityResolver;
	}

	protected EntityResolver getEntityResolver() {
		if (this.entityResolver == null) {
			ResourceLoader resourceLoader = getResourceLoader();
			if (resourceLoader != null) {
				this.entityResolver = new ResourceEntityResolver(resourceLoader);
			} else {
				this.entityResolver = new DelegatingEntityResolver(getBeanClassLoader());
			}
		}
		return this.entityResolver;
	}

	public void setErrorHandler(ErrorHandler errorHandler) {
		this.errorHandler = errorHandler;
	}

	public void setDocumentReaderClass(Class<? extends BeanDefinitionDocumentReader> documentReaderClass) {
		this.documentReaderClass = documentReaderClass;
	}


	/**
	 * 载入指定资源中的 Bean 定义。
	 * 检查并避免循环导入，使用 EncodedResource 包装资源进行读取。
	 * 调用核心加载方法 doLoadBeanDefinitions 完成解析和注册。
	 */
	@Override
	public int loadBeanDefinitions(Resource resource) throws BeanDefinitionStoreException {
		return loadBeanDefinitions(new EncodedResource(resource));
	}

	/**
	 * 载入 EncodedResource 中的 Bean 定义。
	 * 通过 InputStream 获取输入源，调用 doLoadBeanDefinitions 执行核心加载。
	 * 完成后从当前线程的资源集合中移除，避免循环加载。
	 */
	public int loadBeanDefinitions(EncodedResource encodedResource) throws BeanDefinitionStoreException {
		Assert.notNull(encodedResource, "EncodedResource must not be null");
		if (logger.isTraceEnabled()) {
			logger.trace("Loading XML bean definitions from " + encodedResource);
		}

		Set<EncodedResource> currentResources = this.resourcesCurrentlyBeingLoaded.get();

		// 检测循环加载
		if (!currentResources.add(encodedResource)) {
			throw new BeanDefinitionStoreException(
					"Detected cyclic loading of " + encodedResource + " - check your import definitions!");
		}

		try (InputStream inputStream = encodedResource.getResource().getInputStream()) {
			InputSource inputSource = new InputSource(inputStream);
			if (encodedResource.getEncoding() != null) {
				inputSource.setEncoding(encodedResource.getEncoding());
			}
			// 这里是具体的读取过程
			return doLoadBeanDefinitions(inputSource, encodedResource.getResource());
		} catch (IOException ex) {
			throw new BeanDefinitionStoreException(
					"IOException parsing XML document from " + encodedResource.getResource(), ex);
		} finally {
			currentResources.remove(encodedResource);
			if (currentResources.isEmpty()) {
				this.resourcesCurrentlyBeingLoaded.remove();
			}
		}
	}

	/**
	 * 载入 SAX InputSource 指定资源的 Bean 定义，资源描述为字符串。
	 * 代理调用 doLoadBeanDefinitions 处理。
	 */
	public int loadBeanDefinitions(InputSource inputSource) throws BeanDefinitionStoreException {
		return loadBeanDefinitions(inputSource, "resource loaded through SAX InputSource");
	}

	/**
	 * 载入 SAX InputSource 资源，使用传入的资源描述。
	 * 代理调用 doLoadBeanDefinitions 完成核心加载。
	 */
	public int loadBeanDefinitions(InputSource inputSource, @Nullable String resourceDescription)
			throws BeanDefinitionStoreException {

		return doLoadBeanDefinitions(inputSource, new DescriptiveResource(resourceDescription));
	}


	/**
	 * 核心加载方法。
	 * 先调用 doLoadDocument 解析 XML 为 Document 对象，
	 * 再调用 registerBeanDefinitions 注册所有 Bean 定义，
	 * 处理多种解析异常并统一抛出 BeanDefinitionStoreException。
	 */
	protected int doLoadBeanDefinitions(InputSource inputSource, Resource resource)
			throws BeanDefinitionStoreException {

		try {
			// 解析 XML 为 Document 对象，
			Document doc = doLoadDocument(inputSource, resource);
			// registerBeanDefinitions 注册所有 Bean 定义
			int count = registerBeanDefinitions(doc, resource);
			if (logger.isDebugEnabled()) {
				logger.debug("Loaded " + count + " bean definitions from " + resource);
			}
			return count;
		} catch (BeanDefinitionStoreException ex) {
			throw ex;
		} catch (SAXParseException ex) {
			throw new XmlBeanDefinitionStoreException(resource.getDescription(),
					"Line " + ex.getLineNumber() + " in XML document from " + resource + " is invalid", ex);
		} catch (SAXException ex) {
			throw new XmlBeanDefinitionStoreException(resource.getDescription(),
					"XML document from " + resource + " is invalid", ex);
		} catch (ParserConfigurationException ex) {
			throw new BeanDefinitionStoreException(resource.getDescription(),
					"Parser configuration exception parsing XML from " + resource, ex);
		} catch (IOException ex) {
			throw new BeanDefinitionStoreException(resource.getDescription(),
					"IOException parsing XML document from " + resource, ex);
		} catch (Throwable ex) {
			throw new BeanDefinitionStoreException(resource.getDescription(),
					"Unexpected exception parsing XML document from " + resource, ex);
		}
	}

	/**
	 * 解析 XML 输入源，返回 Document 对象。
	 * 调用 documentLoader 结合实体解析器、错误处理器和验证模式执行解析。
	 */
	protected Document doLoadDocument(InputSource inputSource, Resource resource) throws Exception {
		return this.documentLoader.loadDocument(inputSource, getEntityResolver(), this.errorHandler,
				getValidationModeForResource(resource), isNamespaceAware());
	}

	/**
	 * 获取资源的验证模式。
	 * 优先返回用户设置的模式，若为自动，则调用 detectValidationMode 进行检测，
	 * 默认使用 XSD 模式。
	 */
	protected int getValidationModeForResource(Resource resource) {
		int validationModeToUse = getValidationMode();
		if (validationModeToUse != VALIDATION_AUTO) {
			return validationModeToUse;
		}
		int detectedMode = detectValidationMode(resource);
		if (detectedMode != VALIDATION_AUTO) {
			return detectedMode;
		}
		return VALIDATION_XSD;
	}

	/**
	 * 自动检测资源的 XML 验证模式。
	 * 通过打开资源流检测是否使用DTD或XSD验证，若资源已打开则抛异常。
	 */
	protected int detectValidationMode(Resource resource) {
		if (resource.isOpen()) {
			throw new BeanDefinitionStoreException(
					"Passed-in Resource [" + resource + "] contains an open stream: " +
							"cannot determine validation mode automatically. Either pass in a Resource " +
							"that is able to create fresh streams, or explicitly specify the validationMode " +
							"on your XmlBeanDefinitionReader instance.");
		}

		InputStream inputStream;
		try {
			inputStream = resource.getInputStream();
		} catch (IOException ex) {
			throw new BeanDefinitionStoreException(
					"Unable to determine validation mode for [" + resource + "]: cannot open InputStream. " +
							"Did you attempt to load directly from a SAX InputSource without specifying the " +
							"validationMode on your XmlBeanDefinitionReader instance?", ex);
		}

		try {
			return this.validationModeDetector.detectValidationMode(inputStream);
		} catch (IOException ex) {
			throw new BeanDefinitionStoreException("Unable to determine validation mode for [" +
					resource + "]: an error occurred whilst reading from the InputStream.", ex);
		}
	}

	/**
	 * 注册解析文档中所有的 Bean 定义。
	 * 先创建 BeanDefinitionDocumentReader 实例，调用其注册方法。
	 * 返回新注册的 Bean 定义数量。
	 */
	public int registerBeanDefinitions(Document doc, Resource resource) throws BeanDefinitionStoreException {
		//得到 BeanDefinitionDocument Reader 来对xml 格式的 BeanDefinition 解析
		BeanDefinitionDocumentReader documentReader = createBeanDefinitionDocumentReader();
		//获得容器中注册的 Bean 数量
		int countBefore = getRegistry().getBeanDefinitionCount();
		//解析过程入口,这里使用了委派模式,BeanDefinitionDocumentReader 只是个接口,
		//具体的解析实现过程有实现类 Default BeanDefinitionDocument Reader 完成
		documentReader.registerBeanDefinitions(doc, createReaderContext(resource));
		//统计解析的Bean 数量
		return getRegistry().getBeanDefinitionCount() - countBefore;
	}

	/**
	 * 创建 BeanDefinitionDocumentReader 实例，用于文档解析。
	 * 默认通过反射实例化 documentReaderClass。
	 */
	protected BeanDefinitionDocumentReader createBeanDefinitionDocumentReader() {
		return BeanUtils.instantiateClass(this.documentReaderClass);
	}

	/**
	 * 创建解析上下文，包含资源、问题报告器、事件监听器等。
	 */
	public XmlReaderContext createReaderContext(Resource resource) {
		return new XmlReaderContext(resource, this.problemReporter, this.eventListener,
				this.sourceExtractor, this, getNamespaceHandlerResolver());
	}

	/**
	 * 获取命名空间处理器解析器。
	 * 若未设置，则创建默认的 DefaultNamespaceHandlerResolver。
	 */
	public NamespaceHandlerResolver getNamespaceHandlerResolver() {
		if (this.namespaceHandlerResolver == null) {
			this.namespaceHandlerResolver = createDefaultNamespaceHandlerResolver();
		}
		return this.namespaceHandlerResolver;
	}

	/**
	 * 创建默认的命名空间处理器解析器。
	 * 使用资源加载器或类加载器作为 ClassLoader。
	 */
	protected NamespaceHandlerResolver createDefaultNamespaceHandlerResolver() {
		ClassLoader cl = (getResourceLoader() != null ? getResourceLoader().getClassLoader() : getBeanClassLoader());
		return new DefaultNamespaceHandlerResolver(cl);
	}

}
