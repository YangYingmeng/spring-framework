package org.springframework.beans.factory.xml;

import org.w3c.dom.Document;

import org.springframework.beans.factory.BeanDefinitionStoreException;

/**
 * 读取并注册 Bean 定义的接口。
 * 实现类负责从 XML Document 中解析 Bean 定义，
 * 并将其注册到 Spring 容器中。
 */
public interface BeanDefinitionDocumentReader {

	/**
	 * 从给定的 XML Document 中读取 Bean 定义，
	 * 并注册到上下文的 Bean 工厂中。
	 *
	 * @param doc           XML 文档对象，包含 Bean 定义信息
	 * @param readerContext 读取上下文，包含环境信息和注册器等
	 * @throws BeanDefinitionStoreException 注册 Bean 定义失败时抛出异常
	 */
	void registerBeanDefinitions(Document doc, XmlReaderContext readerContext)
			throws BeanDefinitionStoreException;

}

