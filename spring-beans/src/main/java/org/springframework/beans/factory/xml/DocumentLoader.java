package org.springframework.beans.factory.xml;

import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;

/**
 * 负责加载 XML 文档并返回 org.w3c.dom.Document 对象的接口。
 */
public interface DocumentLoader {

	/**
	 * 解析给定的输入源，加载并返回 XML 文档对象。
	 *
	 * @param inputSource    XML 输入源，通常封装了输入流或字符流
	 * @param entityResolver 用于解析 XML 实体（如 DTD 或外部实体）
	 * @param errorHandler   解析过程中出现错误时的处理器
	 * @param validationMode XML 验证模式，决定是否及如何验证 XML（如 DTD 或 XSD）
	 * @param namespaceAware 是否开启命名空间支持，影响解析时对命名空间的处理
	 * @return 解析后的 org.w3c.dom.Document 对象，代表整个 XML 文档
	 * @throws Exception 解析或加载过程中可能抛出的异常
	 */
	Document loadDocument(
			InputSource inputSource, EntityResolver entityResolver,
			ErrorHandler errorHandler, int validationMode, boolean namespaceAware)
			throws Exception;

}

