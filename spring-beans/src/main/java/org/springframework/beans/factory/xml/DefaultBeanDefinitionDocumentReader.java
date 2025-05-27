package org.springframework.beans.factory.xml;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

/**
 * 默认的 BeanDefinitionDocumentReader 实现类，
 * 负责从 XML 配置文档中解析并注册 Bean 定义。
 */
public class DefaultBeanDefinitionDocumentReader implements BeanDefinitionDocumentReader {

	public static final String BEAN_ELEMENT = BeanDefinitionParserDelegate.BEAN_ELEMENT;

	public static final String NESTED_BEANS_ELEMENT = "beans";

	public static final String ALIAS_ELEMENT = "alias";

	public static final String NAME_ATTRIBUTE = "name";

	public static final String ALIAS_ATTRIBUTE = "alias";

	public static final String IMPORT_ELEMENT = "import";

	public static final String RESOURCE_ATTRIBUTE = "resource";

	public static final String PROFILE_ATTRIBUTE = "profile";


	protected final Log logger = LogFactory.getLog(getClass());

	@Nullable
	private XmlReaderContext readerContext;

	@Nullable
	private BeanDefinitionParserDelegate delegate;


	/**
	 * 入口方法，从给定的 Document 对象中开始解析并注册 Bean 定义。
	 * 通过 Document 的根元素调用 doRegisterBeanDefinitions 继续处理。
	 *
	 * @param doc           XML 文档对象
	 * @param readerContext 读取上下文，包含环境、资源等信息
	 */
	@Override
	public void registerBeanDefinitions(Document doc, XmlReaderContext readerContext) {
		this.readerContext = readerContext;
		doRegisterBeanDefinitions(doc.getDocumentElement());
	}


	protected final XmlReaderContext getReaderContext() {
		Assert.state(this.readerContext != null, "No XmlReaderContext available");
		return this.readerContext;
	}


	@Nullable
	protected Object extractSource(Element ele) {
		return getReaderContext().extractSource(ele);
	}


	/**
	 * 核心解析方法：
	 *    主要是解析xml标签
	 */
	@SuppressWarnings("deprecation")
	protected void doRegisterBeanDefinitions(Element root) {
		// 1. 创建 BeanDefinitionParserDelegate 解析委托类，并初始化默认设置
		BeanDefinitionParserDelegate parent = this.delegate;

		this.delegate = createDelegate(getReaderContext(), root, parent);
		// 2. 检查根元素是否属于默认命名空间，若存在 profile 属性则根据环境决定是否跳过该配置
		if (this.delegate.isDefaultNamespace(root)) {
			String profileSpec = root.getAttribute(PROFILE_ATTRIBUTE);
			if (StringUtils.hasText(profileSpec)) {
				String[] specifiedProfiles = StringUtils.tokenizeToStringArray(
						profileSpec, BeanDefinitionParserDelegate.MULTI_VALUE_ATTRIBUTE_DELIMITERS);
				// 如果环境不包含指定 profile，则跳过解析该配置文件
				if (!getReaderContext().getEnvironment().acceptsProfiles(specifiedProfiles)) {
					if (logger.isDebugEnabled()) {
						logger.debug("Skipped XML bean definition file due to specified profiles [" + profileSpec +
								"] not matching: " + getReaderContext().getResource());
					}
					return;
				}
			}
		}
		// 3. 调用 preProcessXml 预处理 XML（空方法，留给子类扩展）
		preProcessXml(root);
		// 4. 解析 Bean 定义
		parseBeanDefinitions(root, this.delegate);
		// 5. 调用 postProcessXml 后处理 XML（空方法，留给子类扩展）
		postProcessXml(root);
		// 6. 恢复之前的 delegate
		this.delegate = parent;
	}


	/**
	 * 创建并初始化 BeanDefinitionParserDelegate，
	 * 用于后续具体的元素解析工作。
	 *
	 * @param readerContext  读取上下文
	 * @param root           XML 根元素
	 * @param parentDelegate 父解析委托对象（可为 null）
	 * @return BeanDefinitionParserDelegate 实例
	 */
	protected BeanDefinitionParserDelegate createDelegate(
			XmlReaderContext readerContext, Element root, @Nullable BeanDefinitionParserDelegate parentDelegate) {

		BeanDefinitionParserDelegate delegate = new BeanDefinitionParserDelegate(readerContext);
		delegate.initDefaults(root, parentDelegate);
		return delegate;
	}


	/**
	 * 解析根元素的子元素
	 * @param root     XML 根元素
	 * @param delegate 解析委托
	 */
	protected void parseBeanDefinitions(Element root, BeanDefinitionParserDelegate delegate) {
		// 1. 如果根元素是默认命名空间，则遍历所有子元素
		if (delegate.isDefaultNamespace(root)) {
			NodeList nl = root.getChildNodes();
			for (int i = 0; i < nl.getLength(); i++) {
				Node node = nl.item(i);
				if (node instanceof Element) {
					Element ele = (Element) node;
					// 默认命名空间的元素通过 parseDefaultElement 处理
					if (delegate.isDefaultNamespace(ele)) {
						parseDefaultElement(ele, delegate);
					} else {
						// 自定义命名空间元素通过 delegate.parseCustomElement 处理
						delegate.parseCustomElement(ele);
					}
				}
			}
		} else {
			// 2. 如果根元素是自定义命名空间，直接调用 delegate.parseCustomElement 处理
			delegate.parseCustomElement(root);
		}
	}


	/**
	 * 使用 Spring 的 Bean 规则解析 Document 元素节点
	 * @param ele      当前元素
	 * @param delegate 解析委托
	 */
	private void parseDefaultElement(Element ele, BeanDefinitionParserDelegate delegate) {
		// 如果元素节点是<Import>导入元素,进行导入解析
		if (delegate.nodeNameEquals(ele, IMPORT_ELEMENT)) {
			importBeanDefinitionResource(ele);
			// 如果元素节点是<Alias>别名元素,进行别名解析
		} else if (delegate.nodeNameEquals(ele, ALIAS_ELEMENT)) {
			processAliasRegistration(ele);
			// 元素节点既不是导入元素,也不是别名元素,即普通的<Bean>元素, 按照 Spring 的Bean 规则解析元素
		} else if (delegate.nodeNameEquals(ele, BEAN_ELEMENT)) {
			processBeanDefinition(ele, delegate);
			// 当有多个bean时循环, 递归调用doRegisterBeanDefinitions 进行bean的解析
		} else if (delegate.nodeNameEquals(ele, NESTED_BEANS_ELEMENT)) {
			doRegisterBeanDefinitions(ele);
		}
	}


	/**
	 * 处理 <import> 元素，导入其他 Bean 定义资源文件。
	 * 支持绝对路径和相对路径两种资源定位方式。
	 * 发生异常时会记录错误日志。
	 *
	 * @param ele <import> 元素
	 */
	protected void importBeanDefinitionResource(Element ele) {
		// 获取 <import> 标签中的 resource 属性
		String location = ele.getAttribute(RESOURCE_ATTRIBUTE);
		if (!StringUtils.hasText(location)) {
			getReaderContext().error("Resource location must not be empty", ele);
			return;
		}

		// 解析占位符（如 ${user.dir}），替换为真实路径
		location = getReaderContext().getEnvironment().resolveRequiredPlaceholders(location);
		// 用于收集真正被加载的 Resource 资源
		Set<Resource> actualResources = new LinkedHashSet<>(4);
		// 判断是否是绝对路径（包括 URL 或 file:/... 等）
		boolean absoluteLocation = false;
		try {
			absoluteLocation = ResourcePatternUtils.isUrl(location) || ResourceUtils.toURI(location).isAbsolute();
		} catch (URISyntaxException ex) {
			// ignore
		}

		// -------------------------------
		// 处理绝对路径的 import 加载方式
		// -------------------------------
		if (absoluteLocation) {
			try {
				// 加载该 location 下的 bean 定义，并放入 actualResources
				int importCount = getReaderContext().getReader().loadBeanDefinitions(location, actualResources);
				if (logger.isTraceEnabled()) {
					logger.trace("Imported " + importCount + " bean definitions from URL location [" + location + "]");
				}
			} catch (BeanDefinitionStoreException ex) {
				getReaderContext().error(
						"Failed to import bean definitions from URL location [" + location + "]", ele, ex);
			}
		} else {
			// -------------------------------
			// 处理相对路径的 import 加载方式
			// -------------------------------
			try {
				int importCount;
				// 尝试从当前配置文件的位置创建相对路径资源对象
				Resource relativeResource = getReaderContext().getResource().createRelative(location);
				if (relativeResource.exists()) {
					// 如果相对路径资源存在，加载它
					importCount = getReaderContext().getReader().loadBeanDefinitions(relativeResource);
					actualResources.add(relativeResource);
				} else {
					// 如果相对路径文件不存在，则尝试拼接路径再加载
					String baseLocation = getReaderContext().getResource().getURL().toString();
					importCount = getReaderContext().getReader().loadBeanDefinitions(
							StringUtils.applyRelativePath(baseLocation, location), actualResources);
				}
				if (logger.isTraceEnabled()) {
					logger.trace("Imported " + importCount + " bean definitions from relative location [" + location + "]");
				}
			} catch (IOException ex) {
				getReaderContext().error("Failed to resolve current resource location", ele, ex);
			} catch (BeanDefinitionStoreException ex) {
				getReaderContext().error(
						"Failed to import bean definitions from relative location [" + location + "]", ele, ex);
			}
		}
		// 将加载的资源转换成数组
		Resource[] actResArray = actualResources.toArray(new Resource[0]);
		// 发布导入完成的事件（用于监听器、日志或扩展）
		getReaderContext().fireImportProcessed(location, actResArray, extractSource(ele));
	}


	/**
	 * 处理 <alias> 元素，注册别名。
	 * 校验 name 和 alias 是否非空，异常时记录错误日志。
	 *
	 * @param ele <alias> 元素
	 */
	protected void processAliasRegistration(Element ele) {
		String name = ele.getAttribute(NAME_ATTRIBUTE);
		String alias = ele.getAttribute(ALIAS_ATTRIBUTE);
		boolean valid = true;
		if (!StringUtils.hasText(name)) {
			getReaderContext().error("Name must not be empty", ele);
			valid = false;
		}
		if (!StringUtils.hasText(alias)) {
			getReaderContext().error("Alias must not be empty", ele);
			valid = false;
		}
		if (valid) {
			try {
				getReaderContext().getRegistry().registerAlias(name, alias);
			} catch (Exception ex) {
				getReaderContext().error("Failed to register alias '" + alias +
						"' for bean with name '" + name + "'", ele, ex);
			}
			getReaderContext().fireAliasRegistered(name, alias, extractSource(ele));
		}
	}


	/**
	 * 处理 <bean> 元素，调用委托类解析 Bean 定义并注册到容器。
	 * 解析成功后，还会进行可能的装饰处理（如注解驱动的后置处理），
	 * 注册过程中异常会记录错误日志。
	 *
	 * @param ele      <bean> 元素
	 * @param delegate 解析委托
	 */
	protected void processBeanDefinition(Element ele, BeanDefinitionParserDelegate delegate) {
		// 解析 <bean> 元素，封装为 BeanDefinitionHolder（包含 beanName、别名、BeanDefinition）
		BeanDefinitionHolder bdHolder = delegate.parseBeanDefinitionElement(ele);
		if (bdHolder != null) {
			// 对 bean 进行自定义装饰（如 <bean> 标签中嵌套了自定义的命名空间或属性）
			bdHolder = delegate.decorateBeanDefinitionIfRequired(ele, bdHolder);
			try {
				// 将解析好的 BeanDefinition 注册到 Spring 的 BeanDefinitionRegistry 中
				BeanDefinitionReaderUtils.registerBeanDefinition(bdHolder, getReaderContext().getRegistry());
			} catch (BeanDefinitionStoreException ex) {
				getReaderContext().error("Failed to register bean definition with name '" +
						bdHolder.getBeanName() + "'", ele, ex);
			}
			// 发布组件注册事件（用于监听器或工具扩展，如工具类打印已注册的 bean）
			getReaderContext().fireComponentRegistered(new BeanComponentDefinition(bdHolder));
		}
	}


	/**
	 * 解析前的预处理钩子，子类可重写扩展。
	 *
	 * @param root XML 根元素
	 */
	protected void preProcessXml(Element root) {
		// 默认空实现
	}


	/**
	 * 解析后的后处理钩子，子类可重写扩展。
	 *
	 * @param root XML 根元素
	 */
	protected void postProcessXml(Element root) {
		// 默认空实现
	}

}
