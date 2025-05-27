package org.springframework.beans.factory.xml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanMetadataAttribute;
import org.springframework.beans.BeanMetadataAttributeAccessor;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanNameReference;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.parsing.BeanEntry;
import org.springframework.beans.factory.parsing.ConstructorArgumentEntry;
import org.springframework.beans.factory.parsing.ParseState;
import org.springframework.beans.factory.parsing.PropertyEntry;
import org.springframework.beans.factory.parsing.QualifierEntry;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.BeanDefinitionDefaults;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.LookupOverride;
import org.springframework.beans.factory.support.ManagedArray;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.support.ManagedProperties;
import org.springframework.beans.factory.support.ManagedSet;
import org.springframework.beans.factory.support.MethodOverrides;
import org.springframework.beans.factory.support.ReplaceOverride;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.PatternMatchUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class BeanDefinitionParserDelegate {

	// Spring Beans命名空间URI
	public static final String BEANS_NAMESPACE_URI = "http://www.springframework.org/schema/beans";

	// 多值属性分隔符
	public static final String MULTI_VALUE_ATTRIBUTE_DELIMITERS = ",; ";

	// 代表true的字符串
	public static final String TRUE_VALUE = "true";

	// 代表false的字符串
	public static final String FALSE_VALUE = "false";

	// 默认值字符串
	public static final String DEFAULT_VALUE = "default";

	// 描述元素名称
	public static final String DESCRIPTION_ELEMENT = "description";

	// 自动装配相关常量，代表不自动装配
	public static final String AUTOWIRE_NO_VALUE = "no";

	// 自动装配按名称
	public static final String AUTOWIRE_BY_NAME_VALUE = "byName";

	// 自动装配按类型
	public static final String AUTOWIRE_BY_TYPE_VALUE = "byType";

	// 自动装配构造器
	public static final String AUTOWIRE_CONSTRUCTOR_VALUE = "constructor";

	// 自动装配自动检测
	public static final String AUTOWIRE_AUTODETECT_VALUE = "autodetect";

	// 属性名
	public static final String NAME_ATTRIBUTE = "name";

	// Bean元素名称
	public static final String BEAN_ELEMENT = "bean";

	// 元数据元素名称
	public static final String META_ELEMENT = "meta";

	// id属性名称
	public static final String ID_ATTRIBUTE = "id";

	// 父bean属性名称
	public static final String PARENT_ATTRIBUTE = "parent";

	// class属性名称
	public static final String CLASS_ATTRIBUTE = "class";

	// abstract属性名称
	public static final String ABSTRACT_ATTRIBUTE = "abstract";

	// scope属性名称
	public static final String SCOPE_ATTRIBUTE = "scope";

	// singleton属性名称（私有）
	private static final String SINGLETON_ATTRIBUTE = "singleton";

	// lazy-init属性名称
	public static final String LAZY_INIT_ATTRIBUTE = "lazy-init";

	// autowire属性名称
	public static final String AUTOWIRE_ATTRIBUTE = "autowire";

	// autowire-candidate属性名称
	public static final String AUTOWIRE_CANDIDATE_ATTRIBUTE = "autowire-candidate";

	// primary属性名称
	public static final String PRIMARY_ATTRIBUTE = "primary";

	// depends-on属性名称
	public static final String DEPENDS_ON_ATTRIBUTE = "depends-on";

	// init-method属性名称
	public static final String INIT_METHOD_ATTRIBUTE = "init-method";

	// destroy-method属性名称
	public static final String DESTROY_METHOD_ATTRIBUTE = "destroy-method";

	// factory-method属性名称
	public static final String FACTORY_METHOD_ATTRIBUTE = "factory-method";

	// factory-bean属性名称
	public static final String FACTORY_BEAN_ATTRIBUTE = "factory-bean";

	// 构造器参数元素名称
	public static final String CONSTRUCTOR_ARG_ELEMENT = "constructor-arg";

	// index属性名称
	public static final String INDEX_ATTRIBUTE = "index";

	// type属性名称
	public static final String TYPE_ATTRIBUTE = "type";

	// value-type属性名称
	public static final String VALUE_TYPE_ATTRIBUTE = "value-type";

	// key-type属性名称
	public static final String KEY_TYPE_ATTRIBUTE = "key-type";

	// property元素名称
	public static final String PROPERTY_ELEMENT = "property";

	// ref属性名称
	public static final String REF_ATTRIBUTE = "ref";

	// value属性名称
	public static final String VALUE_ATTRIBUTE = "value";

	// lookup-method元素名称
	public static final String LOOKUP_METHOD_ELEMENT = "lookup-method";

	// replaced-method元素名称
	public static final String REPLACED_METHOD_ELEMENT = "replaced-method";

	// replacer属性名称
	public static final String REPLACER_ATTRIBUTE = "replacer";

	// arg-type元素名称
	public static final String ARG_TYPE_ELEMENT = "arg-type";

	// 匹配属性名称
	public static final String ARG_TYPE_MATCH_ATTRIBUTE = "match";

	// ref元素名称
	public static final String REF_ELEMENT = "ref";

	// idref元素名称
	public static final String IDREF_ELEMENT = "idref";

	// bean属性名称
	public static final String BEAN_REF_ATTRIBUTE = "bean";

	// parent属性名称
	public static final String PARENT_REF_ATTRIBUTE = "parent";

	// value元素名称
	public static final String VALUE_ELEMENT = "value";

	// null元素名称
	public static final String NULL_ELEMENT = "null";

	// array元素名称
	public static final String ARRAY_ELEMENT = "array";

	// list元素名称
	public static final String LIST_ELEMENT = "list";

	// set元素名称
	public static final String SET_ELEMENT = "set";

	// map元素名称
	public static final String MAP_ELEMENT = "map";

	// entry元素名称
	public static final String ENTRY_ELEMENT = "entry";

	// key元素名称
	public static final String KEY_ELEMENT = "key";

	// key属性名称
	public static final String KEY_ATTRIBUTE = "key";

	// key-ref属性名称
	public static final String KEY_REF_ATTRIBUTE = "key-ref";

	// value-ref属性名称
	public static final String VALUE_REF_ATTRIBUTE = "value-ref";

	// props元素名称
	public static final String PROPS_ELEMENT = "props";

	// prop元素名称
	public static final String PROP_ELEMENT = "prop";

	// merge属性名称
	public static final String MERGE_ATTRIBUTE = "merge";

	// qualifier元素名称
	public static final String QUALIFIER_ELEMENT = "qualifier";

	// qualifier的属性元素名称
	public static final String QUALIFIER_ATTRIBUTE_ELEMENT = "attribute";

	// 默认lazy-init属性名称
	public static final String DEFAULT_LAZY_INIT_ATTRIBUTE = "default-lazy-init";

	// 默认merge属性名称
	public static final String DEFAULT_MERGE_ATTRIBUTE = "default-merge";

	// 默认autowire属性名称
	public static final String DEFAULT_AUTOWIRE_ATTRIBUTE = "default-autowire";

	// 默认autowire候选者属性名称
	public static final String DEFAULT_AUTOWIRE_CANDIDATES_ATTRIBUTE = "default-autowire-candidates";

	// 默认init-method属性名称
	public static final String DEFAULT_INIT_METHOD_ATTRIBUTE = "default-init-method";

	// 默认destroy-method属性名称
	public static final String DEFAULT_DESTROY_METHOD_ATTRIBUTE = "default-destroy-method";


	// 日志对象，用于记录解析过程中的日志信息
	protected final Log logger = LogFactory.getLog(getClass());

	// XML读取上下文，封装了读取XML相关的环境和工具
	private final XmlReaderContext readerContext;

	// 文档中默认配置的定义，包含默认的属性值
	private final DocumentDefaultsDefinition defaults = new DocumentDefaultsDefinition();

	// 解析状态，用于追踪当前解析的节点栈等状态
	private final ParseState parseState = new ParseState();

	// 已使用的bean名称集合，防止重复命名
	private final Set<String> usedNames = new HashSet<>();


	// 构造方法，必须传入XmlReaderContext
	public BeanDefinitionParserDelegate(XmlReaderContext readerContext) {
		Assert.notNull(readerContext, "XmlReaderContext must not be null");
		this.readerContext = readerContext;
	}


	// 获取XML读取上下文
	public final XmlReaderContext getReaderContext() {
		return this.readerContext;
	}

	// 从XML元素中提取源信息，用于错误定位
	@Nullable
	protected Object extractSource(Element ele) {
		return this.readerContext.extractSource(ele);
	}

	// 记录解析错误，关联当前解析状态快照
	protected void error(String message, Node source) {
		this.readerContext.error(message, source, this.parseState.snapshot());
	}

	// 记录解析错误，关联当前解析状态快照
	protected void error(String message, Element source) {
		this.readerContext.error(message, source, this.parseState.snapshot());
	}

	// 记录带异常的解析错误，关联当前解析状态快照
	protected void error(String message, Element source, Throwable cause) {
		this.readerContext.error(message, source, this.parseState.snapshot(), cause);
	}


	// 初始化默认配置，从根元素中读取默认值
	public void initDefaults(Element root) {
		initDefaults(root, null);
	}

	// 初始化默认配置，可以继承父解析器的默认配置
	public void initDefaults(Element root, @Nullable BeanDefinitionParserDelegate parent) {
		populateDefaults(this.defaults, (parent != null ? parent.defaults : null), root);
		this.readerContext.fireDefaultsRegistered(this.defaults);
	}

	protected void populateDefaults(DocumentDefaultsDefinition defaults, @Nullable DocumentDefaultsDefinition parentDefaults,
									Element root) {
		// 从当前XML根元素读取默认的lazy-init属性值
		String lazyInit = root.getAttribute(DEFAULT_LAZY_INIT_ATTRIBUTE);
		// 如果当前元素未定义该属性，则尝试从父defaults中继承，否则默认false
		if (isDefaultValue(lazyInit)) {
			lazyInit = (parentDefaults != null ? parentDefaults.getLazyInit() : FALSE_VALUE);
		}
		defaults.setLazyInit(lazyInit);

		// 从当前XML根元素读取默认的merge属性值
		String merge = root.getAttribute(DEFAULT_MERGE_ATTRIBUTE);
		// 同样尝试继承父defaults的merge属性，或默认false
		if (isDefaultValue(merge)) {
			merge = (parentDefaults != null ? parentDefaults.getMerge() : FALSE_VALUE);
		}
		defaults.setMerge(merge);

		// 读取默认的autowire属性值
		String autowire = root.getAttribute(DEFAULT_AUTOWIRE_ATTRIBUTE);
		// 继承父defaults，或者默认no
		if (isDefaultValue(autowire)) {
			autowire = (parentDefaults != null ? parentDefaults.getAutowire() : AUTOWIRE_NO_VALUE);
		}
		defaults.setAutowire(autowire);

		// 读取默认的autowire-candidates属性值
		if (root.hasAttribute(DEFAULT_AUTOWIRE_CANDIDATES_ATTRIBUTE)) {
			defaults.setAutowireCandidates(root.getAttribute(DEFAULT_AUTOWIRE_CANDIDATES_ATTRIBUTE));
		} else if (parentDefaults != null) {
			defaults.setAutowireCandidates(parentDefaults.getAutowireCandidates());
		}

		// 读取默认的init-method属性值
		if (root.hasAttribute(DEFAULT_INIT_METHOD_ATTRIBUTE)) {
			defaults.setInitMethod(root.getAttribute(DEFAULT_INIT_METHOD_ATTRIBUTE));
		} else if (parentDefaults != null) {
			defaults.setInitMethod(parentDefaults.getInitMethod());
		}

		// 读取默认的destroy-method属性值
		if (root.hasAttribute(DEFAULT_DESTROY_METHOD_ATTRIBUTE)) {
			defaults.setDestroyMethod(root.getAttribute(DEFAULT_DESTROY_METHOD_ATTRIBUTE));
		} else if (parentDefaults != null) {
			defaults.setDestroyMethod(parentDefaults.getDestroyMethod());
		}

		// 设置defaults的source信息，用于错误定位或调试
		defaults.setSource(this.readerContext.extractSource(root));
	}

	public DocumentDefaultsDefinition getDefaults() {
		return this.defaults;
	}

	public BeanDefinitionDefaults getBeanDefinitionDefaults() {
		// 创建BeanDefinitionDefaults实例，转化DocumentDefaultsDefinition中部分属性
		BeanDefinitionDefaults bdd = new BeanDefinitionDefaults();
		// 设置是否延迟加载
		bdd.setLazyInit(TRUE_VALUE.equalsIgnoreCase(this.defaults.getLazyInit()));
		// 设置自动装配模式，默认为"default"的值
		bdd.setAutowireMode(getAutowireMode(DEFAULT_VALUE));
		// 设置初始化方法名称
		bdd.setInitMethodName(this.defaults.getInitMethod());
		// 设置销毁方法名称
		bdd.setDestroyMethodName(this.defaults.getDestroyMethod());
		return bdd;
	}

	@Nullable
	public String[] getAutowireCandidatePatterns() {
		// 从defaults中读取autowire候选者的匹配模式，转换为字符串数组返回
		String candidatePattern = this.defaults.getAutowireCandidates();
		return (candidatePattern != null ? StringUtils.commaDelimitedListToStringArray(candidatePattern) : null);
	}

	@Nullable
	public BeanDefinitionHolder parseBeanDefinitionElement(Element ele) {
		// 调用重载方法，无包含Bean定义
		return parseBeanDefinitionElement(ele, null);
	}

	@Nullable
	public BeanDefinitionHolder parseBeanDefinitionElement(Element ele, @Nullable BeanDefinition containingBean) {
		// 获取元素的id属性
		String id = ele.getAttribute(ID_ATTRIBUTE);
		// 获取元素的name属性
		String nameAttr = ele.getAttribute(NAME_ATTRIBUTE);

		// 解析name属性，支持多个用逗号、分号、空格分隔的别名
		List<String> aliases = new ArrayList<>();
		if (StringUtils.hasLength(nameAttr)) {
			String[] nameArr = StringUtils.tokenizeToStringArray(nameAttr, MULTI_VALUE_ATTRIBUTE_DELIMITERS);
			aliases.addAll(Arrays.asList(nameArr));
		}

		// 以id作为主名称
		String beanName = id;
		// 如果id为空，且存在别名，则第一个别名作为beanName，剩余的作为aliases
		if (!StringUtils.hasText(beanName) && !aliases.isEmpty()) {
			beanName = aliases.remove(0);
			if (logger.isTraceEnabled()) {
				logger.trace("No XML 'id' specified - using '" + beanName +
						"' as bean name and " + aliases + " as aliases");
			}
		}

		// 若当前Bean不是包含在其他Bean中的，检查beanName及别名的唯一性
		if (containingBean == null) {
			checkNameUniqueness(beanName, aliases, ele);
		}

		// 解析BeanDefinition（解析具体属性、构造参数等）
		AbstractBeanDefinition beanDefinition = parseBeanDefinitionElement(ele, beanName, containingBean);
		if (beanDefinition != null) {
			// 如果beanName为空，则生成一个唯一名称
			if (!StringUtils.hasText(beanName)) {
				try {
					if (containingBean != null) {
						beanName = BeanDefinitionReaderUtils.generateBeanName(
								beanDefinition, this.readerContext.getRegistry(), true);
					} else {
						beanName = this.readerContext.generateBeanName(beanDefinition);
						// 处理生成的beanName是否和类名冲突，若是则加入类名为别名
						String beanClassName = beanDefinition.getBeanClassName();
						if (beanClassName != null &&
								beanName.startsWith(beanClassName) && beanName.length() > beanClassName.length() &&
								!this.readerContext.getRegistry().isBeanNameInUse(beanClassName)) {
							aliases.add(beanClassName);
						}
					}
					if (logger.isTraceEnabled()) {
						logger.trace("Neither XML 'id' nor 'name' specified - " +
								"using generated bean name [" + beanName + "]");
					}
				} catch (Exception ex) {
					error(ex.getMessage(), ele);
					return null;
				}
			}
			// 将别名列表转为数组
			String[] aliasesArray = StringUtils.toStringArray(aliases);
			// 返回包装了BeanDefinition及其名称、别名的Holder对象
			return new BeanDefinitionHolder(beanDefinition, beanName, aliasesArray);
		}

		return null;
	}

	protected void checkNameUniqueness(String beanName, List<String> aliases, Element beanElement) {
		String foundName = null;

		// 检查主名称是否已使用
		if (StringUtils.hasText(beanName) && this.usedNames.contains(beanName)) {
			foundName = beanName;
		}
		// 如果主名称没重复，再检查别名列表中是否有已使用名称
		if (foundName == null) {
			foundName = CollectionUtils.findFirstMatch(this.usedNames, aliases);
		}
		// 若有重复名称，则报错
		if (foundName != null) {
			error("Bean name '" + foundName + "' is already used in this <beans> element", beanElement);
		}

		// 记录当前beanName和所有别名，避免重复
		this.usedNames.add(beanName);
		this.usedNames.addAll(aliases);
	}


	@Nullable
	public AbstractBeanDefinition parseBeanDefinitionElement(
			Element ele, String beanName, @Nullable BeanDefinition containingBean) {

		// 记录当前正在解析的 Bean 名称，push 到解析状态栈
		this.parseState.push(new BeanEntry(beanName));

		String className = null;
		if (ele.hasAttribute(CLASS_ATTRIBUTE)) {
			// 获取 class 属性值（类全限定名）
			className = ele.getAttribute(CLASS_ATTRIBUTE).trim();
		}
		String parent = null;
		if (ele.hasAttribute(PARENT_ATTRIBUTE)) {
			// 获取 parent 属性值（父 Bean 名称）
			parent = ele.getAttribute(PARENT_ATTRIBUTE);
		}

		try {
			// 根据 className 和 parent 创建 BeanDefinition 实例
			AbstractBeanDefinition bd = createBeanDefinition(className, parent);

			// 解析 <bean> 标签上的通用属性，如 scope、lazy-init、init-method 等
			parseBeanDefinitionAttributes(ele, beanName, containingBean, bd);
			// 设置 Bean 描述信息，从 <description> 子元素读取
			bd.setDescription(DomUtils.getChildElementValueByTagName(ele, DESCRIPTION_ELEMENT));

			// 解析 <meta> 子元素，增加自定义元数据
			parseMetaElements(ele, bd);
			// 解析 <lookup-method> 动态查找方法覆盖
			parseLookupOverrideSubElements(ele, bd.getMethodOverrides());
			// 解析 <replaced-method> 方法替换元素
			parseReplacedMethodSubElements(ele, bd.getMethodOverrides());

			// 解析构造函数参数 <constructor-arg>
			parseConstructorArgElements(ele, bd);
			// 解析属性注入 <property>
			parsePropertyElements(ele, bd);
			// 解析自动装配限定符 <qualifier>
			parseQualifierElements(ele, bd);

			// 记录资源和来源信息，便于后续追踪和错误定位
			bd.setResource(this.readerContext.getResource());
			bd.setSource(extractSource(ele));

			return bd;
		} catch (ClassNotFoundException ex) {
			error("Bean class [" + className + "] not found", ele, ex);
		} catch (NoClassDefFoundError err) {
			error("Class that bean class [" + className + "] depends on not found", ele, err);
		} catch (Throwable ex) {
			error("Unexpected failure during bean definition parsing", ele, ex);
		} finally {
			// 解析结束，弹出解析状态栈
			this.parseState.pop();
		}

		return null;
	}

	public AbstractBeanDefinition parseBeanDefinitionAttributes(Element ele, String beanName,
																@Nullable BeanDefinition containingBean,
																AbstractBeanDefinition bd) {

		// 处理过时的 singleton 属性提示错误
		if (ele.hasAttribute(SINGLETON_ATTRIBUTE)) {
			error("Old 1.x 'singleton' attribute in use - upgrade to 'scope' declaration", ele);
		} else if (ele.hasAttribute(SCOPE_ATTRIBUTE)) {
			// 读取 scope 属性，设置作用域（singleton/prototype 等）
			bd.setScope(ele.getAttribute(SCOPE_ATTRIBUTE));
		} else if (containingBean != null) {
			// 如果当前 Bean 是嵌套 Bean，继承外层 Bean 的作用域
			bd.setScope(containingBean.getScope());
		}

		// 解析 abstract 属性，标记 Bean 是否抽象
		if (ele.hasAttribute(ABSTRACT_ATTRIBUTE)) {
			bd.setAbstract(TRUE_VALUE.equals(ele.getAttribute(ABSTRACT_ATTRIBUTE)));
		}

		// 解析 lazy-init 属性，是否懒加载
		String lazyInit = ele.getAttribute(LAZY_INIT_ATTRIBUTE);
		if (isDefaultValue(lazyInit)) {
			lazyInit = this.defaults.getLazyInit();
		}
		bd.setLazyInit(TRUE_VALUE.equals(lazyInit));

		// 解析 autowire 属性，设置自动装配模式
		String autowire = ele.getAttribute(AUTOWIRE_ATTRIBUTE);
		bd.setAutowireMode(getAutowireMode(autowire));

		// 解析 depends-on 属性，声明 Bean 依赖关系
		if (ele.hasAttribute(DEPENDS_ON_ATTRIBUTE)) {
			String dependsOn = ele.getAttribute(DEPENDS_ON_ATTRIBUTE);
			bd.setDependsOn(StringUtils.tokenizeToStringArray(dependsOn, MULTI_VALUE_ATTRIBUTE_DELIMITERS));
		}

		// 解析 autowire-candidate 属性，标识是否为自动装配候选
		String autowireCandidate = ele.getAttribute(AUTOWIRE_CANDIDATE_ATTRIBUTE);
		if (isDefaultValue(autowireCandidate)) {
			String candidatePattern = this.defaults.getAutowireCandidates();
			if (candidatePattern != null) {
				String[] patterns = StringUtils.commaDelimitedListToStringArray(candidatePattern);
				// 支持通配符匹配 beanName
				bd.setAutowireCandidate(PatternMatchUtils.simpleMatch(patterns, beanName));
			}
		} else {
			bd.setAutowireCandidate(TRUE_VALUE.equals(autowireCandidate));
		}

		// 解析 primary 属性，标识自动装配首选候选 Bean
		if (ele.hasAttribute(PRIMARY_ATTRIBUTE)) {
			bd.setPrimary(TRUE_VALUE.equals(ele.getAttribute(PRIMARY_ATTRIBUTE)));
		}

		// 解析 init-method 属性，指定初始化方法
		if (ele.hasAttribute(INIT_METHOD_ATTRIBUTE)) {
			String initMethodName = ele.getAttribute(INIT_METHOD_ATTRIBUTE);
			bd.setInitMethodName(initMethodName);
		} else if (this.defaults.getInitMethod() != null) {
			// 使用默认初始化方法
			bd.setInitMethodName(this.defaults.getInitMethod());
			bd.setEnforceInitMethod(false);
		}

		// 解析 destroy-method 属性，指定销毁方法
		if (ele.hasAttribute(DESTROY_METHOD_ATTRIBUTE)) {
			String destroyMethodName = ele.getAttribute(DESTROY_METHOD_ATTRIBUTE);
			bd.setDestroyMethodName(destroyMethodName);
		} else if (this.defaults.getDestroyMethod() != null) {
			// 使用默认销毁方法
			bd.setDestroyMethodName(this.defaults.getDestroyMethod());
			bd.setEnforceDestroyMethod(false);
		}

		// 解析工厂方法名 factory-method
		if (ele.hasAttribute(FACTORY_METHOD_ATTRIBUTE)) {
			bd.setFactoryMethodName(ele.getAttribute(FACTORY_METHOD_ATTRIBUTE));
		}
		// 解析工厂 Bean 名称 factory-bean
		if (ele.hasAttribute(FACTORY_BEAN_ATTRIBUTE)) {
			bd.setFactoryBeanName(ele.getAttribute(FACTORY_BEAN_ATTRIBUTE));
		}

		return bd;
	}


	protected AbstractBeanDefinition createBeanDefinition(@Nullable String className, @Nullable String parentName)
			throws ClassNotFoundException {
		// 通过工具类创建 BeanDefinition，传入父类名称和类加载器
		return BeanDefinitionReaderUtils.createBeanDefinition(
				parentName, className, this.readerContext.getBeanClassLoader());
	}

	public void parseMetaElements(Element ele, BeanMetadataAttributeAccessor attributeAccessor) {
		// 解析 <meta> 子元素，将 key/value 存入 BeanMetadataAttribute
		NodeList nl = ele.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (isCandidateElement(node) && nodeNameEquals(node, META_ELEMENT)) {
				Element metaElement = (Element) node;
				String key = metaElement.getAttribute(KEY_ATTRIBUTE);
				String value = metaElement.getAttribute(VALUE_ATTRIBUTE);
				BeanMetadataAttribute attribute = new BeanMetadataAttribute(key, value);
				attribute.setSource(extractSource(metaElement));
				attributeAccessor.addMetadataAttribute(attribute);
			}
		}
	}

	@SuppressWarnings("deprecation")
	public int getAutowireMode(String attrValue) {
		// 将 autowire 的字符串配置转换为对应的 int 常量
		String attr = attrValue;
		if (isDefaultValue(attr)) {
			attr = this.defaults.getAutowire();
		}
		int autowire = AbstractBeanDefinition.AUTOWIRE_NO;
		if (AUTOWIRE_BY_NAME_VALUE.equals(attr)) {
			autowire = AbstractBeanDefinition.AUTOWIRE_BY_NAME;
		} else if (AUTOWIRE_BY_TYPE_VALUE.equals(attr)) {
			autowire = AbstractBeanDefinition.AUTOWIRE_BY_TYPE;
		} else if (AUTOWIRE_CONSTRUCTOR_VALUE.equals(attr)) {
			autowire = AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR;
		} else if (AUTOWIRE_AUTODETECT_VALUE.equals(attr)) {
			autowire = AbstractBeanDefinition.AUTOWIRE_AUTODETECT;
		}
		return autowire;
	}

	public void parseConstructorArgElements(Element beanEle, BeanDefinition bd) {
		// 解析 <constructor-arg> 子元素，收集构造函数参数配置
		NodeList nl = beanEle.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (isCandidateElement(node) && nodeNameEquals(node, CONSTRUCTOR_ARG_ELEMENT)) {
				parseConstructorArgElement((Element) node, bd);
			}
		}
	}

	public void parsePropertyElements(Element beanEle, BeanDefinition bd) {
		// 解析 <property> 子元素，收集属性注入配置
		NodeList nl = beanEle.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (isCandidateElement(node) && nodeNameEquals(node, PROPERTY_ELEMENT)) {
				parsePropertyElement((Element) node, bd);
			}
		}
	}

	public void parseQualifierElements(Element beanEle, AbstractBeanDefinition bd) {
		// 解析 <qualifier> 子元素，支持自动装配限定符
		NodeList nl = beanEle.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (isCandidateElement(node) && nodeNameEquals(node, QUALIFIER_ELEMENT)) {
				parseQualifierElement((Element) node, bd);
			}
		}
	}

	public void parseLookupOverrideSubElements(Element beanEle, MethodOverrides overrides) {
		// 解析 <lookup-method> 子元素，实现动态查找方法覆盖
		NodeList nl = beanEle.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (isCandidateElement(node) && nodeNameEquals(node, LOOKUP_METHOD_ELEMENT)) {
				Element ele = (Element) node;
				String methodName = ele.getAttribute(NAME_ATTRIBUTE);
				String beanRef = ele.getAttribute(BEAN_ELEMENT);
				LookupOverride override = new LookupOverride(methodName, beanRef);
				override.setSource(extractSource(ele));
				overrides.addOverride(override);
			}
		}
	}

	public void parseReplacedMethodSubElements(Element beanEle, MethodOverrides overrides) {
		NodeList nl = beanEle.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (isCandidateElement(node) && nodeNameEquals(node, REPLACED_METHOD_ELEMENT)) {
				Element replacedMethodEle = (Element) node;
				String name = replacedMethodEle.getAttribute(NAME_ATTRIBUTE);
				String callback = replacedMethodEle.getAttribute(REPLACER_ATTRIBUTE);
				ReplaceOverride replaceOverride = new ReplaceOverride(name, callback);
				List<Element> argTypeEles = DomUtils.getChildElementsByTagName(replacedMethodEle, ARG_TYPE_ELEMENT);
				for (Element argTypeEle : argTypeEles) {
					String match = argTypeEle.getAttribute(ARG_TYPE_MATCH_ATTRIBUTE);
					match = (StringUtils.hasText(match) ? match : DomUtils.getTextValue(argTypeEle));
					if (StringUtils.hasText(match)) {
						replaceOverride.addTypeIdentifier(match);
					}
				}
				replaceOverride.setSource(extractSource(replacedMethodEle));
				overrides.addOverride(replaceOverride);
			}
		}
	}

	public void parseConstructorArgElement(Element ele, BeanDefinition bd) {
		String indexAttr = ele.getAttribute(INDEX_ATTRIBUTE);
		String typeAttr = ele.getAttribute(TYPE_ATTRIBUTE);
		String nameAttr = ele.getAttribute(NAME_ATTRIBUTE);
		if (StringUtils.hasLength(indexAttr)) {
			try {
				int index = Integer.parseInt(indexAttr);
				if (index < 0) {
					error("'index' cannot be lower than 0", ele);
				} else {
					try {
						this.parseState.push(new ConstructorArgumentEntry(index));
						Object value = parsePropertyValue(ele, bd, null);
						ConstructorArgumentValues.ValueHolder valueHolder = new ConstructorArgumentValues.ValueHolder(value);
						if (StringUtils.hasLength(typeAttr)) {
							valueHolder.setType(typeAttr);
						}
						if (StringUtils.hasLength(nameAttr)) {
							valueHolder.setName(nameAttr);
						}
						valueHolder.setSource(extractSource(ele));
						if (bd.getConstructorArgumentValues().hasIndexedArgumentValue(index)) {
							error("Ambiguous constructor-arg entries for index " + index, ele);
						} else {
							bd.getConstructorArgumentValues().addIndexedArgumentValue(index, valueHolder);
						}
					} finally {
						this.parseState.pop();
					}
				}
			} catch (NumberFormatException ex) {
				error("Attribute 'index' of tag 'constructor-arg' must be an integer", ele);
			}
		} else {
			try {
				this.parseState.push(new ConstructorArgumentEntry());
				Object value = parsePropertyValue(ele, bd, null);
				ConstructorArgumentValues.ValueHolder valueHolder = new ConstructorArgumentValues.ValueHolder(value);
				if (StringUtils.hasLength(typeAttr)) {
					valueHolder.setType(typeAttr);
				}
				if (StringUtils.hasLength(nameAttr)) {
					valueHolder.setName(nameAttr);
				}
				valueHolder.setSource(extractSource(ele));
				bd.getConstructorArgumentValues().addGenericArgumentValue(valueHolder);
			} finally {
				this.parseState.pop();
			}
		}
	}

	public void parsePropertyElement(Element ele, BeanDefinition bd) {
		String propertyName = ele.getAttribute(NAME_ATTRIBUTE);
		if (!StringUtils.hasLength(propertyName)) {
			error("Tag 'property' must have a 'name' attribute", ele);
			return;
		}
		this.parseState.push(new PropertyEntry(propertyName));
		try {
			if (bd.getPropertyValues().contains(propertyName)) {
				error("Multiple 'property' definitions for property '" + propertyName + "'", ele);
				return;
			}
			Object val = parsePropertyValue(ele, bd, propertyName);
			PropertyValue pv = new PropertyValue(propertyName, val);
			parseMetaElements(ele, pv);
			pv.setSource(extractSource(ele));
			bd.getPropertyValues().addPropertyValue(pv);
		} finally {
			this.parseState.pop();
		}
	}

	public void parseQualifierElement(Element ele, AbstractBeanDefinition bd) {
		String typeName = ele.getAttribute(TYPE_ATTRIBUTE);
		if (!StringUtils.hasLength(typeName)) {
			error("Tag 'qualifier' must have a 'type' attribute", ele);
			return;
		}
		this.parseState.push(new QualifierEntry(typeName));
		try {
			AutowireCandidateQualifier qualifier = new AutowireCandidateQualifier(typeName);
			qualifier.setSource(extractSource(ele));
			String value = ele.getAttribute(VALUE_ATTRIBUTE);
			if (StringUtils.hasLength(value)) {
				qualifier.setAttribute(AutowireCandidateQualifier.VALUE_KEY, value);
			}
			NodeList nl = ele.getChildNodes();
			for (int i = 0; i < nl.getLength(); i++) {
				Node node = nl.item(i);
				if (isCandidateElement(node) && nodeNameEquals(node, QUALIFIER_ATTRIBUTE_ELEMENT)) {
					Element attributeEle = (Element) node;
					String attributeName = attributeEle.getAttribute(KEY_ATTRIBUTE);
					String attributeValue = attributeEle.getAttribute(VALUE_ATTRIBUTE);
					if (StringUtils.hasLength(attributeName) && StringUtils.hasLength(attributeValue)) {
						BeanMetadataAttribute attribute = new BeanMetadataAttribute(attributeName, attributeValue);
						attribute.setSource(extractSource(attributeEle));
						qualifier.addMetadataAttribute(attribute);
					} else {
						error("Qualifier 'attribute' tag must have a 'name' and 'value'", attributeEle);
						return;
					}
				}
			}
			bd.addQualifier(qualifier);
		} finally {
			this.parseState.pop();
		}
	}

	@Nullable
	public Object parsePropertyValue(Element ele, BeanDefinition bd, @Nullable String propertyName) {
		String elementName = (propertyName != null ?
				"<property> element for property '" + propertyName + "'" :
				"<constructor-arg> element");

		NodeList nl = ele.getChildNodes();
		Element subElement = null;
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (node instanceof Element && !nodeNameEquals(node, DESCRIPTION_ELEMENT) &&
					!nodeNameEquals(node, META_ELEMENT)) {
				if (subElement != null) {
					error(elementName + " must not contain more than one sub-element", ele);
				} else {
					subElement = (Element) node;
				}
			}
		}

		boolean hasRefAttribute = ele.hasAttribute(REF_ATTRIBUTE);
		boolean hasValueAttribute = ele.hasAttribute(VALUE_ATTRIBUTE);
		if ((hasRefAttribute && hasValueAttribute) ||
				((hasRefAttribute || hasValueAttribute) && subElement != null)) {
			error(elementName +
					" is only allowed to contain either 'ref' attribute OR 'value' attribute OR sub-element", ele);
		}

		if (hasRefAttribute) {
			String refName = ele.getAttribute(REF_ATTRIBUTE);
			if (!StringUtils.hasText(refName)) {
				error(elementName + " contains empty 'ref' attribute", ele);
			}
			RuntimeBeanReference ref = new RuntimeBeanReference(refName);
			ref.setSource(extractSource(ele));
			return ref;
		} else if (hasValueAttribute) {
			TypedStringValue valueHolder = new TypedStringValue(ele.getAttribute(VALUE_ATTRIBUTE));
			valueHolder.setSource(extractSource(ele));
			return valueHolder;
		} else if (subElement != null) {
			return parsePropertySubElement(subElement, bd);
		} else {
			error(elementName + " must specify a ref or value", ele);
			return null;
		}
	}

	@Nullable
	public Object parsePropertySubElement(Element ele, @Nullable BeanDefinition bd) {
		return parsePropertySubElement(ele, bd, null);
	}

	@Nullable
	public Object parsePropertySubElement(Element ele, @Nullable BeanDefinition bd, @Nullable String defaultValueType) {
		if (!isDefaultNamespace(ele)) {
			return parseNestedCustomElement(ele, bd);
		} else if (nodeNameEquals(ele, BEAN_ELEMENT)) {
			BeanDefinitionHolder nestedBd = parseBeanDefinitionElement(ele, bd);
			if (nestedBd != null) {
				nestedBd = decorateBeanDefinitionIfRequired(ele, nestedBd, bd);
			}
			return nestedBd;
		} else if (nodeNameEquals(ele, REF_ELEMENT)) {
			String refName = ele.getAttribute(BEAN_REF_ATTRIBUTE);
			boolean toParent = false;
			if (!StringUtils.hasLength(refName)) {
				refName = ele.getAttribute(PARENT_REF_ATTRIBUTE);
				toParent = true;
				if (!StringUtils.hasLength(refName)) {
					error("'bean' or 'parent' is required for <ref> element", ele);
					return null;
				}
			}
			if (!StringUtils.hasText(refName)) {
				error("<ref> element contains empty target attribute", ele);
				return null;
			}
			RuntimeBeanReference ref = new RuntimeBeanReference(refName, toParent);
			ref.setSource(extractSource(ele));
			return ref;
		} else if (nodeNameEquals(ele, IDREF_ELEMENT)) {
			return parseIdRefElement(ele);
		} else if (nodeNameEquals(ele, VALUE_ELEMENT)) {
			return parseValueElement(ele, defaultValueType);
		} else if (nodeNameEquals(ele, NULL_ELEMENT)) {
			TypedStringValue nullHolder = new TypedStringValue(null);
			nullHolder.setSource(extractSource(ele));
			return nullHolder;
		} else if (nodeNameEquals(ele, ARRAY_ELEMENT)) {
			return parseArrayElement(ele, bd);
		} else if (nodeNameEquals(ele, LIST_ELEMENT)) {
			return parseListElement(ele, bd);
		} else if (nodeNameEquals(ele, SET_ELEMENT)) {
			return parseSetElement(ele, bd);
		} else if (nodeNameEquals(ele, MAP_ELEMENT)) {
			return parseMapElement(ele, bd);
		} else if (nodeNameEquals(ele, PROPS_ELEMENT)) {
			return parsePropsElement(ele);
		} else {
			error("Unknown property sub-element: [" + ele.getNodeName() + "]", ele);
			return null;
		}
	}

	@Nullable
	public Object parseIdRefElement(Element ele) {
		String refName = ele.getAttribute(BEAN_REF_ATTRIBUTE);
		if (!StringUtils.hasLength(refName)) {
			error("'bean' is required for <idref> element", ele);
			return null;
		}
		if (!StringUtils.hasText(refName)) {
			error("<idref> element contains empty target attribute", ele);
			return null;
		}
		RuntimeBeanNameReference ref = new RuntimeBeanNameReference(refName);
		ref.setSource(extractSource(ele));
		return ref;
	}

	public Object parseValueElement(Element ele, @Nullable String defaultTypeName) {
		String value = DomUtils.getTextValue(ele);
		String specifiedTypeName = ele.getAttribute(TYPE_ATTRIBUTE);
		String typeName = specifiedTypeName;
		if (!StringUtils.hasText(typeName)) {
			typeName = defaultTypeName;
		}
		try {
			TypedStringValue typedValue = buildTypedStringValue(value, typeName);
			typedValue.setSource(extractSource(ele));
			typedValue.setSpecifiedTypeName(specifiedTypeName);
			return typedValue;
		} catch (ClassNotFoundException ex) {
			error("Type class [" + typeName + "] not found for <value> element", ele, ex);
			return value;
		}
	}

	protected TypedStringValue buildTypedStringValue(String value, @Nullable String targetTypeName)
			throws ClassNotFoundException {

		ClassLoader classLoader = this.readerContext.getBeanClassLoader();
		TypedStringValue typedValue;
		if (!StringUtils.hasText(targetTypeName)) {
			typedValue = new TypedStringValue(value);
		} else if (classLoader != null) {
			Class<?> targetType = ClassUtils.forName(targetTypeName, classLoader);
			typedValue = new TypedStringValue(value, targetType);
		} else {
			typedValue = new TypedStringValue(value, targetTypeName);
		}
		return typedValue;
	}

	public Object parseArrayElement(Element arrayEle, @Nullable BeanDefinition bd) {
		String elementType = arrayEle.getAttribute(VALUE_TYPE_ATTRIBUTE);
		NodeList nl = arrayEle.getChildNodes();
		ManagedArray target = new ManagedArray(elementType, nl.getLength());
		target.setSource(extractSource(arrayEle));
		target.setElementTypeName(elementType);
		target.setMergeEnabled(parseMergeAttribute(arrayEle));
		parseCollectionElements(nl, target, bd, elementType);
		return target;
	}

	public List<Object> parseListElement(Element collectionEle, @Nullable BeanDefinition bd) {
		String defaultElementType = collectionEle.getAttribute(VALUE_TYPE_ATTRIBUTE);
		NodeList nl = collectionEle.getChildNodes();
		ManagedList<Object> target = new ManagedList<>(nl.getLength());
		target.setSource(extractSource(collectionEle));
		target.setElementTypeName(defaultElementType);
		target.setMergeEnabled(parseMergeAttribute(collectionEle));
		parseCollectionElements(nl, target, bd, defaultElementType);
		return target;
	}

	public Set<Object> parseSetElement(Element collectionEle, @Nullable BeanDefinition bd) {
		String defaultElementType = collectionEle.getAttribute(VALUE_TYPE_ATTRIBUTE);
		NodeList nl = collectionEle.getChildNodes();
		ManagedSet<Object> target = new ManagedSet<>(nl.getLength());
		target.setSource(extractSource(collectionEle));
		target.setElementTypeName(defaultElementType);
		target.setMergeEnabled(parseMergeAttribute(collectionEle));
		parseCollectionElements(nl, target, bd, defaultElementType);
		return target;
	}

	protected void parseCollectionElements(
			NodeList elementNodes, Collection<Object> target, @Nullable BeanDefinition bd, String defaultElementType) {

		for (int i = 0; i < elementNodes.getLength(); i++) {
			Node node = elementNodes.item(i);
			if (node instanceof Element && !nodeNameEquals(node, DESCRIPTION_ELEMENT)) {
				target.add(parsePropertySubElement((Element) node, bd, defaultElementType));
			}
		}
	}

	public Map<Object, Object> parseMapElement(Element mapEle, @Nullable BeanDefinition bd) {
		String defaultKeyType = mapEle.getAttribute(KEY_TYPE_ATTRIBUTE);
		String defaultValueType = mapEle.getAttribute(VALUE_TYPE_ATTRIBUTE);

		List<Element> entryEles = DomUtils.getChildElementsByTagName(mapEle, ENTRY_ELEMENT);
		ManagedMap<Object, Object> map = new ManagedMap<>(entryEles.size());
		map.setSource(extractSource(mapEle));
		map.setKeyTypeName(defaultKeyType);
		map.setValueTypeName(defaultValueType);
		map.setMergeEnabled(parseMergeAttribute(mapEle));

		for (Element entryEle : entryEles) {
			NodeList entrySubNodes = entryEle.getChildNodes();
			Element keyEle = null;
			Element valueEle = null;
			for (int j = 0; j < entrySubNodes.getLength(); j++) {
				Node node = entrySubNodes.item(j);
				if (node instanceof Element) {
					Element candidateEle = (Element) node;
					if (nodeNameEquals(candidateEle, KEY_ELEMENT)) {
						if (keyEle != null) {
							error("<entry> element is only allowed to contain one <key> sub-element", entryEle);
						} else {
							keyEle = candidateEle;
						}
					} else {
						if (nodeNameEquals(candidateEle, DESCRIPTION_ELEMENT)) {
						} else if (valueEle != null) {
							error("<entry> element must not contain more than one value sub-element", entryEle);
						} else {
							valueEle = candidateEle;
						}
					}
				}
			}

			Object key = null;
			boolean hasKeyAttribute = entryEle.hasAttribute(KEY_ATTRIBUTE);
			boolean hasKeyRefAttribute = entryEle.hasAttribute(KEY_REF_ATTRIBUTE);
			if ((hasKeyAttribute && hasKeyRefAttribute) ||
					(hasKeyAttribute || hasKeyRefAttribute) && keyEle != null) {
				error("<entry> element is only allowed to contain either " +
						"a 'key' attribute OR a 'key-ref' attribute OR a <key> sub-element", entryEle);
			}
			if (hasKeyAttribute) {
				key = buildTypedStringValueForMap(entryEle.getAttribute(KEY_ATTRIBUTE), defaultKeyType, entryEle);
			} else if (hasKeyRefAttribute) {
				String refName = entryEle.getAttribute(KEY_REF_ATTRIBUTE);
				if (!StringUtils.hasText(refName)) {
					error("<entry> element contains empty 'key-ref' attribute", entryEle);
				}
				RuntimeBeanReference ref = new RuntimeBeanReference(refName);
				ref.setSource(extractSource(entryEle));
				key = ref;
			} else if (keyEle != null) {
				key = parseKeyElement(keyEle, bd, defaultKeyType);
			} else {
				error("<entry> element must specify a key", entryEle);
			}

			Object value = null;
			boolean hasValueAttribute = entryEle.hasAttribute(VALUE_ATTRIBUTE);
			boolean hasValueRefAttribute = entryEle.hasAttribute(VALUE_REF_ATTRIBUTE);
			boolean hasValueTypeAttribute = entryEle.hasAttribute(VALUE_TYPE_ATTRIBUTE);
			if ((hasValueAttribute && hasValueRefAttribute) ||
					(hasValueAttribute || hasValueRefAttribute) && valueEle != null) {
				error("<entry> element is only allowed to contain either " +
						"'value' attribute OR 'value-ref' attribute OR <value> sub-element", entryEle);
			}
			if ((hasValueTypeAttribute && hasValueRefAttribute) ||
					(hasValueTypeAttribute && !hasValueAttribute) ||
					(hasValueTypeAttribute && valueEle != null)) {
				error("<entry> element is only allowed to contain a 'value-type' " +
						"attribute when it has a 'value' attribute", entryEle);
			}
			if (hasValueAttribute) {
				String valueType = entryEle.getAttribute(VALUE_TYPE_ATTRIBUTE);
				if (!StringUtils.hasText(valueType)) {
					valueType = defaultValueType;
				}
				value = buildTypedStringValueForMap(entryEle.getAttribute(VALUE_ATTRIBUTE), valueType, entryEle);
			} else if (hasValueRefAttribute) {
				String refName = entryEle.getAttribute(VALUE_REF_ATTRIBUTE);
				if (!StringUtils.hasText(refName)) {
					error("<entry> element contains empty 'value-ref' attribute", entryEle);
				}
				RuntimeBeanReference ref = new RuntimeBeanReference(refName);
				ref.setSource(extractSource(entryEle));
				value = ref;
			} else if (valueEle != null) {
				value = parsePropertySubElement(valueEle, bd, defaultValueType);
			} else {
				error("<entry> element must specify a value", entryEle);
			}

			map.put(key, value);
		}

		return map;
	}

	protected final Object buildTypedStringValueForMap(String value, String defaultTypeName, Element entryEle) {
		try {
			TypedStringValue typedValue = buildTypedStringValue(value, defaultTypeName);
			typedValue.setSource(extractSource(entryEle));
			return typedValue;
		} catch (ClassNotFoundException ex) {
			error("Type class [" + defaultTypeName + "] not found for Map key/value type", entryEle, ex);
			return value;
		}
	}

	@Nullable
	protected Object parseKeyElement(Element keyEle, @Nullable BeanDefinition bd, String defaultKeyTypeName) {
		NodeList nl = keyEle.getChildNodes();
		Element subElement = null;
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (node instanceof Element) {
				if (subElement != null) {
					error("<key> element must not contain more than one value sub-element", keyEle);
				} else {
					subElement = (Element) node;
				}
			}
		}
		if (subElement == null) {
			return null;
		}
		return parsePropertySubElement(subElement, bd, defaultKeyTypeName);
	}

	public Properties parsePropsElement(Element propsEle) {
		ManagedProperties props = new ManagedProperties();
		props.setSource(extractSource(propsEle));
		props.setMergeEnabled(parseMergeAttribute(propsEle));

		List<Element> propEles = DomUtils.getChildElementsByTagName(propsEle, PROP_ELEMENT);
		for (Element propEle : propEles) {
			String key = propEle.getAttribute(KEY_ATTRIBUTE);
			String value = DomUtils.getTextValue(propEle).trim();
			TypedStringValue keyHolder = new TypedStringValue(key);
			keyHolder.setSource(extractSource(propEle));
			TypedStringValue valueHolder = new TypedStringValue(value);
			valueHolder.setSource(extractSource(propEle));
			props.put(keyHolder, valueHolder);
		}

		return props;
	}

	public boolean parseMergeAttribute(Element collectionElement) {
		String value = collectionElement.getAttribute(MERGE_ATTRIBUTE);
		if (isDefaultValue(value)) {
			value = this.defaults.getMerge();
		}
		return TRUE_VALUE.equals(value);
	}

	@Nullable
	public BeanDefinition parseCustomElement(Element ele) {
		return parseCustomElement(ele, null);
	}

	@Nullable
	public BeanDefinition parseCustomElement(Element ele, @Nullable BeanDefinition containingBd) {
		String namespaceUri = getNamespaceURI(ele);
		if (namespaceUri == null) {
			return null;
		}
		NamespaceHandler handler = this.readerContext.getNamespaceHandlerResolver().resolve(namespaceUri);
		if (handler == null) {
			error("Unable to locate Spring NamespaceHandler for XML schema namespace [" + namespaceUri + "]", ele);
			return null;
		}
		return handler.parse(ele, new ParserContext(this.readerContext, this, containingBd));
	}

	public BeanDefinitionHolder decorateBeanDefinitionIfRequired(Element ele, BeanDefinitionHolder originalDef) {
		return decorateBeanDefinitionIfRequired(ele, originalDef, null);
	}

	public BeanDefinitionHolder decorateBeanDefinitionIfRequired(
			Element ele, BeanDefinitionHolder originalDef, @Nullable BeanDefinition containingBd) {

		BeanDefinitionHolder finalDefinition = originalDef;

		NamedNodeMap attributes = ele.getAttributes();
		for (int i = 0; i < attributes.getLength(); i++) {
			Node node = attributes.item(i);
			finalDefinition = decorateIfRequired(node, finalDefinition, containingBd);
		}

		NodeList children = ele.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node node = children.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				finalDefinition = decorateIfRequired(node, finalDefinition, containingBd);
			}
		}
		return finalDefinition;
	}

	public BeanDefinitionHolder decorateIfRequired(
			Node node, BeanDefinitionHolder originalDef, @Nullable BeanDefinition containingBd) {

		String namespaceUri = getNamespaceURI(node);
		if (namespaceUri != null && !isDefaultNamespace(namespaceUri)) {
			NamespaceHandler handler = this.readerContext.getNamespaceHandlerResolver().resolve(namespaceUri);
			if (handler != null) {
				BeanDefinitionHolder decorated =
						handler.decorate(node, originalDef, new ParserContext(this.readerContext, this, containingBd));
				if (decorated != null) {
					return decorated;
				}
			} else if (namespaceUri.startsWith("http://www.springframework.org/schema/")) {
				error("Unable to locate Spring NamespaceHandler for XML schema namespace [" + namespaceUri + "]", node);
			} else {
				if (logger.isDebugEnabled()) {
					logger.debug("No Spring NamespaceHandler found for XML schema namespace [" + namespaceUri + "]");
				}
			}
		}
		return originalDef;
	}

	@Nullable
	private BeanDefinitionHolder parseNestedCustomElement(Element ele, @Nullable BeanDefinition containingBd) {
		BeanDefinition innerDefinition = parseCustomElement(ele, containingBd);
		if (innerDefinition == null) {
			error("Incorrect usage of element '" + ele.getNodeName() + "' in a nested manner. " +
					"This tag cannot be used nested inside <property>.", ele);
			return null;
		}
		String id = ele.getNodeName() + BeanDefinitionReaderUtils.GENERATED_BEAN_NAME_SEPARATOR +
				ObjectUtils.getIdentityHexString(innerDefinition);
		if (logger.isTraceEnabled()) {
			logger.trace("Using generated bean name [" + id +
					"] for nested custom element '" + ele.getNodeName() + "'");
		}
		return new BeanDefinitionHolder(innerDefinition, id);
	}


	@Nullable
	public String getNamespaceURI(Node node) {
		return node.getNamespaceURI();
	}

	public String getLocalName(Node node) {
		return node.getLocalName();
	}

	public boolean nodeNameEquals(Node node, String desiredName) {
		return desiredName.equals(node.getNodeName()) || desiredName.equals(getLocalName(node));
	}

	public boolean isDefaultNamespace(@Nullable String namespaceUri) {
		return !StringUtils.hasLength(namespaceUri) || BEANS_NAMESPACE_URI.equals(namespaceUri);
	}

	public boolean isDefaultNamespace(Node node) {
		return isDefaultNamespace(getNamespaceURI(node));
	}

	private boolean isDefaultValue(String value) {
		return !StringUtils.hasLength(value) || DEFAULT_VALUE.equals(value);
	}

	private boolean isCandidateElement(Node node) {
		return (node instanceof Element && (isDefaultNamespace(node) || !isDefaultNamespace(node.getParentNode())));
	}

}
