package org.springframework.beans.factory.xml;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.xml.XmlValidationModeDetector;
import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;

/**
 * 默认的 DocumentLoader 实现，使用 JAXP API 来加载和解析 XML 文档。
 */
public class DefaultDocumentLoader implements DocumentLoader {

	// JAXP 用于指定 XML Schema 验证属性的常量
	private static final String SCHEMA_LANGUAGE_ATTRIBUTE = "http://java.sun.com/xml/jaxp/properties/schemaLanguage";

	// XML Schema 的命名空间 URI
	private static final String XSD_SCHEMA_LANGUAGE = "http://www.w3.org/2001/XMLSchema";

	private static final Log logger = LogFactory.getLog(DefaultDocumentLoader.class);

	/**
	 * 加载 XML 文档，返回解析后的 Document 对象。
	 *
	 * @param inputSource    XML 输入源
	 * @param entityResolver 实体解析器，用于解析DTD或外部实体
	 * @param errorHandler   错误处理器，用于处理解析时的错误
	 * @param validationMode 验证模式，决定是否启用验证及验证方式
	 * @param namespaceAware 是否支持命名空间
	 * @return 解析后的 Document 对象
	 * @throws Exception 解析过程中抛出的异常
	 */
	@Override
	public Document loadDocument(InputSource inputSource, EntityResolver entityResolver,
								 ErrorHandler errorHandler, int validationMode, boolean namespaceAware) throws Exception {

		// 创建 DocumentBuilderFactory 并根据验证模式和命名空间配置
		DocumentBuilderFactory factory = createDocumentBuilderFactory(validationMode, namespaceAware);
		if (logger.isTraceEnabled()) {
			logger.trace("Using JAXP provider [" + factory.getClass().getName() + "]");
		}
		// 创建 DocumentBuilder，设置实体解析器和错误处理器
		DocumentBuilder builder = createDocumentBuilder(factory, entityResolver, errorHandler);
		// 解析输入源，返回 Document 对象
		return builder.parse(inputSource);
	}

	/**
	 * 根据验证模式和命名空间支持情况，创建并配置 DocumentBuilderFactory。
	 *
	 * @param validationMode 验证模式
	 * @param namespaceAware 是否支持命名空间
	 * @return 配置好的 DocumentBuilderFactory 实例
	 * @throws ParserConfigurationException 配置错误时抛出
	 */
	protected DocumentBuilderFactory createDocumentBuilderFactory(int validationMode, boolean namespaceAware)
			throws ParserConfigurationException {

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(namespaceAware);

		if (validationMode != XmlValidationModeDetector.VALIDATION_NONE) {
			factory.setValidating(true);
			if (validationMode == XmlValidationModeDetector.VALIDATION_XSD) {
				// 启用命名空间支持，以满足 XSD 验证要求
				factory.setNamespaceAware(true);
				try {
					// 设置验证类型为 XML Schema
					factory.setAttribute(SCHEMA_LANGUAGE_ATTRIBUTE, XSD_SCHEMA_LANGUAGE);
				} catch (IllegalArgumentException ex) {
					// 当前 JAXP 实现不支持 XML Schema 验证，抛出异常提示
					ParserConfigurationException pcex = new ParserConfigurationException(
							"Unable to validate using XSD: Your JAXP provider [" + factory +
									"] does not support XML Schema. Are you running on Java 1.4 with Apache Crimson? " +
									"Upgrade to Apache Xerces (or Java 1.5) for full XSD support.");
					pcex.initCause(ex);
					throw pcex;
				}
			}
		}

		return factory;
	}

	/**
	 * 根据给定的 DocumentBuilderFactory 创建 DocumentBuilder，并设置实体解析器和错误处理器。
	 *
	 * @param factory        DocumentBuilderFactory 实例
	 * @param entityResolver 实体解析器（可为空）
	 * @param errorHandler   错误处理器（可为空）
	 * @return 配置好的 DocumentBuilder 实例
	 * @throws ParserConfigurationException 配置错误时抛出
	 */
	protected DocumentBuilder createDocumentBuilder(DocumentBuilderFactory factory,
													@Nullable EntityResolver entityResolver, @Nullable ErrorHandler errorHandler)
			throws ParserConfigurationException {

		DocumentBuilder docBuilder = factory.newDocumentBuilder();
		if (entityResolver != null) {
			docBuilder.setEntityResolver(entityResolver);
		}
		if (errorHandler != null) {
			docBuilder.setErrorHandler(errorHandler);
		}
		return docBuilder;
	}

}
