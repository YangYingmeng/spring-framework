package org.springframework.beans;

import java.util.Map;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.lang.Nullable;

/**
 * 属性访问器接口，用于对 JavaBean 的属性进行读取和写入操作。
 * 是 Spring BeanWrapper、BeanWrapperImpl 等类的基础接口。
 */
public interface PropertyAccessor {

	// 嵌套属性的分隔符，例如 "person.address.city"
	String NESTED_PROPERTY_SEPARATOR = ".";

	// 嵌套属性的分隔符字符
	char NESTED_PROPERTY_SEPARATOR_CHAR = '.';

	// 属性键前缀（用于索引属性，如 list[0]）
	String PROPERTY_KEY_PREFIX = "[";

	// 属性键前缀字符
	char PROPERTY_KEY_PREFIX_CHAR = '[';

	// 属性键后缀
	String PROPERTY_KEY_SUFFIX = "]";

	// 属性键后缀字符
	char PROPERTY_KEY_SUFFIX_CHAR = ']';


	/**
	 * 判断指定属性名是否可读。
	 *
	 * @param propertyName 属性名称
	 * @return 如果可读取该属性，则返回 true
	 */
	boolean isReadableProperty(String propertyName);

	/**
	 * 判断指定属性名是否可写。
	 *
	 * @param propertyName 属性名称
	 * @return 如果可写该属性，则返回 true
	 */
	boolean isWritableProperty(String propertyName);

	/**
	 * 获取指定属性的类型（Class）。
	 *
	 * @param propertyName 属性名称
	 * @return 属性的类型，如果不存在则返回 null
	 * @throws BeansException 如果访问失败
	 */
	@Nullable
	Class<?> getPropertyType(String propertyName) throws BeansException;

	/**
	 * 获取指定属性的类型描述符（支持泛型等复杂类型信息）。
	 *
	 * @param propertyName 属性名称
	 * @return 类型描述符，如果不存在则返回 null
	 * @throws BeansException 如果访问失败
	 */
	@Nullable
	TypeDescriptor getPropertyTypeDescriptor(String propertyName) throws BeansException;

	/**
	 * 获取指定属性的值。
	 *
	 * @param propertyName 属性名称
	 * @return 属性的当前值
	 * @throws BeansException 如果访问失败
	 */
	@Nullable
	Object getPropertyValue(String propertyName) throws BeansException;

	/**
	 * 设置指定属性的值。
	 *
	 * @param propertyName 属性名称
	 * @param value        要设置的值
	 * @throws BeansException 如果设置失败
	 */
	void setPropertyValue(String propertyName, @Nullable Object value) throws BeansException;

	/**
	 * 设置一个属性值，使用封装好的 PropertyValue 对象。
	 *
	 * @param pv 包含属性名称和值的对象
	 * @throws BeansException 如果设置失败
	 */
	void setPropertyValue(PropertyValue pv) throws BeansException;

	/**
	 * 批量设置属性值（通常来自 Map）。
	 *
	 * @param map 属性名到属性值的映射
	 * @throws BeansException 如果设置过程中出错
	 */
	void setPropertyValues(Map<?, ?> map) throws BeansException;

	/**
	 * 批量设置属性值。
	 *
	 * @param pvs 属性值集合
	 * @throws BeansException 如果设置失败
	 */
	void setPropertyValues(PropertyValues pvs) throws BeansException;

	/**
	 * 批量设置属性值，忽略未知属性。
	 *
	 * @param pvs           属性值集合
	 * @param ignoreUnknown 是否忽略未知属性
	 * @throws BeansException 如果设置失败
	 */
	void setPropertyValues(PropertyValues pvs, boolean ignoreUnknown) throws BeansException;

	/**
	 * 批量设置属性值，允许忽略未知或无效属性。
	 *
	 * @param pvs           属性值集合
	 * @param ignoreUnknown 是否忽略未知属性
	 * @param ignoreInvalid 是否忽略无效属性（如类型不匹配）
	 * @throws BeansException 如果设置失败
	 */
	void setPropertyValues(PropertyValues pvs, boolean ignoreUnknown, boolean ignoreInvalid)
			throws BeansException;
}
