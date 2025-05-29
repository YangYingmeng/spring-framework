package org.springframework.beans;

import java.beans.PropertyChangeEvent;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.security.PrivilegedActionException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.CollectionFactory;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConverterNotFoundException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * 抽象类 AbstractNestablePropertyAccessor 是一个可嵌套的属性访问器的基础实现类。
 * 它继承自 AbstractPropertyAccessor，提供了对 Java Bean 属性的读取和写入功能，
 * 并增强了对嵌套属性（如 user.address.city）的支持。
 * <p>
 * 该类是 Spring 数据绑定框架的核心之一，广泛用于 BeanWrapper、DataBinder 等模块中。
 * <p>
 * 主要功能包括：
 * - 支持使用 "." 表示法访问嵌套属性；
 * - 支持对数组、集合、Map 中的元素访问（如 list[0]、map['key']）；
 * - 支持创建中间路径（如 user.address 为空时自动创建）；
 * - 抽象了属性访问和解析逻辑，方便子类实现实际的读取/写入策略；
 * - 提供自定义属性编辑器注册等扩展能力。
 * <p>
 * 使用示例（例如在 BeanWrapperImpl 中）：
 * AbstractNestablePropertyAccessor accessor = new BeanWrapperImpl(someBean);
 * accessor.setPropertyValue("user.address.city", "Shanghai");
 * Object city = accessor.getPropertyValue("user.address.city");
 * <p>
 * 核心设计思想：
 * - 分离属性路径解析（如提取属性名、索引等）；
 * - 分离属性值解析与设置（通过 PropertyHandler 实现）；
 * - 支持嵌套访问、类型转换、自定义编辑器、容错处理等；
 * - 提供统一访问接口，屏蔽底层 JavaBean 操作细节。
 * <p>
 * 常见子类：
 * - BeanWrapperImpl：Spring 中对 Java Bean 属性进行读取/设置的标准实现；
 * - DirectFieldAccessor：通过反射直接访问字段（非 getter/setter）；
 * - ServletRequestParameterPropertyValues：用于 Web 请求参数绑定。
 */
public abstract class AbstractNestablePropertyAccessor extends AbstractPropertyAccessor {

	private static final Log logger = LogFactory.getLog(AbstractNestablePropertyAccessor.class);

	private int autoGrowCollectionLimit = Integer.MAX_VALUE;

	@Nullable
	Object wrappedObject;

	private String nestedPath = "";

	@Nullable
	Object rootObject;

	@Nullable
	private Map<String, AbstractNestablePropertyAccessor> nestedPropertyAccessors;


	protected AbstractNestablePropertyAccessor() {
		this(true);
	}

	protected AbstractNestablePropertyAccessor(boolean registerDefaultEditors) {
		if (registerDefaultEditors) {
			registerDefaultEditors();
		}
		this.typeConverterDelegate = new TypeConverterDelegate(this);
	}

	protected AbstractNestablePropertyAccessor(Object object) {
		registerDefaultEditors();
		setWrappedInstance(object);
	}

	protected AbstractNestablePropertyAccessor(Class<?> clazz) {
		registerDefaultEditors();
		setWrappedInstance(BeanUtils.instantiateClass(clazz));
	}

	protected AbstractNestablePropertyAccessor(Object object, String nestedPath, Object rootObject) {
		registerDefaultEditors();
		setWrappedInstance(object, nestedPath, rootObject);
	}

	protected AbstractNestablePropertyAccessor(Object object, String nestedPath, AbstractNestablePropertyAccessor parent) {
		setWrappedInstance(object, nestedPath, parent.getWrappedInstance());
		setExtractOldValueForEditor(parent.isExtractOldValueForEditor());
		setAutoGrowNestedPaths(parent.isAutoGrowNestedPaths());
		setAutoGrowCollectionLimit(parent.getAutoGrowCollectionLimit());
		setConversionService(parent.getConversionService());
	}


	public void setAutoGrowCollectionLimit(int autoGrowCollectionLimit) {
		this.autoGrowCollectionLimit = autoGrowCollectionLimit;
	}

	public int getAutoGrowCollectionLimit() {
		return this.autoGrowCollectionLimit;
	}

	public void setWrappedInstance(Object object) {
		setWrappedInstance(object, "", null);
	}

	public void setWrappedInstance(Object object, @Nullable String nestedPath, @Nullable Object rootObject) {
		this.wrappedObject = ObjectUtils.unwrapOptional(object);
		Assert.notNull(this.wrappedObject, "Target object must not be null");
		this.nestedPath = (nestedPath != null ? nestedPath : "");
		this.rootObject = (!this.nestedPath.isEmpty() ? rootObject : this.wrappedObject);
		this.nestedPropertyAccessors = null;
		this.typeConverterDelegate = new TypeConverterDelegate(this, this.wrappedObject);
	}

	public final Object getWrappedInstance() {
		Assert.state(this.wrappedObject != null, "No wrapped object");
		return this.wrappedObject;
	}

	public final Class<?> getWrappedClass() {
		return getWrappedInstance().getClass();
	}

	public final String getNestedPath() {
		return this.nestedPath;
	}

	public final Object getRootInstance() {
		Assert.state(this.rootObject != null, "No root object");
		return this.rootObject;
	}

	public final Class<?> getRootClass() {
		return getRootInstance().getClass();
	}

	/**
	 * 设置指定属性名对应的属性值，支持嵌套属性路径。
	 *
	 * @param propertyName 属性名，可以是嵌套路径，如 "user.address.city"
	 * @param value        要设置的属性值，允许为 null
	 * @throws BeansException 如果属性不可读、不可写或设置过程中出现异常
	 */
	@Override
	public void setPropertyValue(String propertyName, @Nullable Object value) throws BeansException {
		AbstractNestablePropertyAccessor nestedPa;
		try {
			// 获取对应嵌套属性路径的 PropertyAccessor（可能是当前对象，也可能是内部嵌套对象）
			nestedPa = getPropertyAccessorForPropertyPath(propertyName);
		} catch (NotReadablePropertyException ex) {
			// 如果路径中的嵌套属性不可读，抛出不可写异常，提示嵌套路径中属性不存在
			throw new NotWritablePropertyException(getRootClass(), this.nestedPath + propertyName,
					"Nested property in path '" + propertyName + "' does not exist", ex);
		}

		// 对属性路径进行拆分，得到最终的属性名及索引等信息（如 address[0].city -> tokens）
		PropertyTokenHolder tokens = getPropertyNameTokens(getFinalPath(nestedPa, propertyName));

		// 使用获取到的嵌套属性访问器设置属性值，传入属性路径拆分信息和属性值对象
		nestedPa.setPropertyValue(tokens, new PropertyValue(propertyName, value));
	}


	/**
	 * 根据传入的 PropertyValue 对象设置属性值。
	 * 该方法会处理嵌套属性路径的解析和缓存已解析的属性路径标记（PropertyTokenHolder）。
	 *
	 * @param pv 包含属性名和值的 PropertyValue 对象
	 * @throws BeansException 发生属性访问相关异常时抛出
	 */
	@Override
	public void setPropertyValue(PropertyValue pv) throws BeansException {
		// 从 PropertyValue 中获取已解析的属性路径标记（缓存，避免重复解析）
		PropertyTokenHolder tokens = (PropertyTokenHolder) pv.resolvedTokens;

		if (tokens == null) {
			// 如果没有缓存解析结果，则需要解析属性路径
			String propertyName = pv.getName();

			AbstractNestablePropertyAccessor nestedPa;
			try {
				// 获取该属性路径对应的嵌套属性访问器
				nestedPa = getPropertyAccessorForPropertyPath(propertyName);
			} catch (NotReadablePropertyException ex) {
				// 如果嵌套路径中某一级属性不可读，抛出不可写异常，提示路径不存在
				throw new NotWritablePropertyException(getRootClass(), this.nestedPath + propertyName,
						"Nested property in path '" + propertyName + "' does not exist", ex);
			}

			// 解析完整属性路径，得到属性名拆分标记
			tokens = getPropertyNameTokens(getFinalPath(nestedPa, propertyName));

			// 如果嵌套访问器就是当前对象，则将解析结果缓存回原始 PropertyValue 中，提升性能
			if (nestedPa == this) {
				pv.getOriginalPropertyValue().resolvedTokens = tokens;
			}

			// 使用嵌套访问器设置属性值
			nestedPa.setPropertyValue(tokens, pv);
		} else {
			// 如果已经有解析的属性路径标记，直接调用重载的 setPropertyValue 方法
			setPropertyValue(tokens, pv);
		}
	}

	/**
	 * 根据解析后的属性路径标记设置属性值。
	 * 根据属性路径中是否包含键（如索引或映射键）调用不同的处理方法。
	 *
	 * @param tokens 解析后的属性路径标记，包含属性名、索引、键等信息
	 * @param pv     封装属性名和值的 PropertyValue 对象
	 * @throws BeansException 发生属性访问相关异常时抛出
	 */
	protected void setPropertyValue(PropertyTokenHolder tokens, PropertyValue pv) throws BeansException {
		if (tokens.keys != null) {
			// 如果属性路径包含键（如数组索引、Map的键等），调用专门处理键属性的方法
			processKeyedProperty(tokens, pv);
		} else {
			// 否则，处理普通的本地属性（没有索引或键）
			processLocalProperty(tokens, pv);
		}
	}


	/**
	 * 处理带有键（索引或Map键）的属性赋值操作。
	 * 根据属性值类型（数组、List、Map）分别进行处理和转换，支持自动扩容数组和列表。
	 *
	 * @param tokens 解析后的属性路径标记，包含属性名、索引、键等信息
	 * @param pv     封装属性名和值的 PropertyValue 对象
	 * @throws BeansException 属性访问或类型转换异常时抛出
	 */
	private void processKeyedProperty(PropertyTokenHolder tokens, PropertyValue pv) {
		// 获取属性对应的当前值（可能是数组、List或Map）
		Object propValue = getPropertyHoldingValue(tokens);
		// 根据属性名获取本地属性处理器
		PropertyHandler ph = getLocalPropertyHandler(tokens.actualName);
		if (ph == null) {
			throw new InvalidPropertyException(
					getRootClass(), this.nestedPath + tokens.actualName, "No property handler found");
		}
		// 确保属性路径包含键
		Assert.state(tokens.keys != null, "No token keys");
		// 取最后一个键，通常是具体的索引或Map的key
		String lastKey = tokens.keys[tokens.keys.length - 1];

		if (propValue.getClass().isArray()) {
			// 如果是数组类型
			Class<?> requiredType = propValue.getClass().getComponentType(); // 数组元素类型
			int arrayIndex = Integer.parseInt(lastKey); // 转成整型索引
			Object oldValue = null;
			try {
				// 是否需要提取旧值，用于类型转换器
				if (isExtractOldValueForEditor() && arrayIndex < Array.getLength(propValue)) {
					oldValue = Array.get(propValue, arrayIndex);
				}
				// 将新值转换成数组元素类型
				Object convertedValue = convertIfNecessary(tokens.canonicalName, oldValue, pv.getValue(),
						requiredType, ph.nested(tokens.keys.length));
				int length = Array.getLength(propValue);
				// 如果索引超出当前数组长度且未超过自动增长限制，则扩容数组
				if (arrayIndex >= length && arrayIndex < this.autoGrowCollectionLimit) {
					Class<?> componentType = propValue.getClass().getComponentType();
					Object newArray = Array.newInstance(componentType, arrayIndex + 1);
					System.arraycopy(propValue, 0, newArray, 0, length);
					// 更新数组属性
					int lastKeyIndex = tokens.canonicalName.lastIndexOf('[');
					String propName = tokens.canonicalName.substring(0, lastKeyIndex);
					setPropertyValue(propName, newArray);
					propValue = getPropertyValue(propName);
				}
				// 设置转换后的值到数组指定索引
				Array.set(propValue, arrayIndex, convertedValue);
			} catch (IndexOutOfBoundsException ex) {
				throw new InvalidPropertyException(getRootClass(), this.nestedPath + tokens.canonicalName,
						"Invalid array index in property path '" + tokens.canonicalName + "'", ex);
			}
		} else if (propValue instanceof List) {
			// 如果是List类型
			Class<?> requiredType = ph.getCollectionType(tokens.keys.length); // 列表元素类型
			List<Object> list = (List<Object>) propValue;
			int index = Integer.parseInt(lastKey);
			Object oldValue = null;
			if (isExtractOldValueForEditor() && index < list.size()) {
				oldValue = list.get(index);
			}
			// 转换新值为目标元素类型
			Object convertedValue = convertIfNecessary(tokens.canonicalName, oldValue, pv.getValue(),
					requiredType, ph.nested(tokens.keys.length));
			int size = list.size();
			if (index >= size && index < this.autoGrowCollectionLimit) {
				// 如果索引超出List当前大小且未超过自动增长限制，尝试填充null以扩容
				for (int i = size; i < index; i++) {
					try {
						list.add(null);
					} catch (NullPointerException ex) {
						throw new InvalidPropertyException(getRootClass(), this.nestedPath + tokens.canonicalName,
								"Cannot set element with index " + index + " in List of size " +
										size + ", accessed using property path '" + tokens.canonicalName +
										"': List does not support filling up gaps with null elements");
					}
				}
				// 添加转换后的值到List尾部
				list.add(convertedValue);
			} else {
				// 索引在范围内，直接替换元素
				try {
					list.set(index, convertedValue);
				} catch (IndexOutOfBoundsException ex) {
					throw new InvalidPropertyException(getRootClass(), this.nestedPath + tokens.canonicalName,
							"Invalid list index in property path '" + tokens.canonicalName + "'", ex);
				}
			}
		} else if (propValue instanceof Map) {
			// 如果是Map类型
			Class<?> mapKeyType = ph.getMapKeyType(tokens.keys.length); // Map键类型
			Class<?> mapValueType = ph.getMapValueType(tokens.keys.length); // Map值类型
			Map<Object, Object> map = (Map<Object, Object>) propValue;
			TypeDescriptor typeDescriptor = TypeDescriptor.valueOf(mapKeyType);
			// 转换Map的键类型
			Object convertedMapKey = convertIfNecessary(null, null, lastKey, mapKeyType, typeDescriptor);
			Object oldValue = null;
			if (isExtractOldValueForEditor()) {
				oldValue = map.get(convertedMapKey);
			}
			// 转换Map的值类型
			Object convertedMapValue = convertIfNecessary(tokens.canonicalName, oldValue, pv.getValue(),
					mapValueType, ph.nested(tokens.keys.length));
			// 放入Map
			map.put(convertedMapKey, convertedMapValue);
		} else {
			// 如果既不是数组、列表，也不是Map，则抛出异常
			throw new InvalidPropertyException(getRootClass(), this.nestedPath + tokens.canonicalName,
					"Property referenced in indexed property path '" + tokens.canonicalName +
							"' is neither an array nor a List nor a Map; returned value was [" + propValue + "]");
		}
	}


	/**
	 * 获取指定的属性持有值（即去除最后一个键的父对象的属性值）
	 * 例如，对于属性路径 "person.address[0].street"，此方法返回 "person.address[0]" 对应的对象，
	 * 以便对该对象进行进一步操作（如访问数组、列表或映射中的具体元素）
	 *
	 * @param tokens 包含属性名和索引键的属性令牌持有者
	 * @return 属性持有值对象
	 * @throws NotWritablePropertyException   如果属性不可读则抛出
	 * @throws NullValueInNestedPathException 如果属性值为null且未启用自动扩展嵌套路径则抛出
	 */
	private Object getPropertyHoldingValue(PropertyTokenHolder tokens) {
		// 确保tokens的keys不为null
		Assert.state(tokens.keys != null, "No token keys");

		// 创建一个新的PropertyTokenHolder，用来获取父属性（去掉最后一个索引键）
		PropertyTokenHolder getterTokens = new PropertyTokenHolder(tokens.actualName);
		getterTokens.canonicalName = tokens.canonicalName;
		getterTokens.keys = new String[tokens.keys.length - 1];
		System.arraycopy(tokens.keys, 0, getterTokens.keys, 0, tokens.keys.length - 1);

		Object propValue;
		try {
			// 获取父属性对象的值
			propValue = getPropertyValue(getterTokens);
		} catch (NotReadablePropertyException ex) {
			// 如果属性不可读，抛出不可写异常（因为后续会写操作）
			throw new NotWritablePropertyException(getRootClass(), this.nestedPath + tokens.canonicalName,
					"Cannot access indexed value in property referenced " +
							"in indexed property path '" + tokens.canonicalName + "'", ex);
		}

		if (propValue == null) {
			if (isAutoGrowNestedPaths()) {
				// 如果启用了自动扩展嵌套路径，则为父属性设置默认值
				int lastKeyIndex = tokens.canonicalName.lastIndexOf('[');
				getterTokens.canonicalName = tokens.canonicalName.substring(0, lastKeyIndex);
				propValue = setDefaultValue(getterTokens);
			} else {
				// 否则抛出嵌套路径中值为null的异常
				throw new NullValueInNestedPathException(getRootClass(), this.nestedPath + tokens.canonicalName,
						"Cannot access indexed value in property referenced " +
								"in indexed property path '" + tokens.canonicalName + "': returned null");
			}
		}
		return propValue;
	}


	/**
	 * 处理对本地属性的赋值操作
	 * 负责对属性进行类型转换，调用属性处理器写入属性值，并处理可能的异常
	 *
	 * @param tokens 属性令牌持有者，包含属性名及路径信息
	 * @param pv     待设置的属性值封装对象
	 * @throws TypeMismatchException     如果类型不匹配
	 * @throws MethodInvocationException 调用属性 setter 时异常
	 * @throws InvalidPropertyException  如果属性不可写且不被忽略
	 */
	private void processLocalProperty(PropertyTokenHolder tokens, PropertyValue pv) {
		// 获取本地属性处理器
		PropertyHandler ph = getLocalPropertyHandler(tokens.actualName);

		// 如果找不到属性处理器或属性不可写
		if (ph == null || !ph.isWritable()) {
			if (pv.isOptional()) {
				// 可选属性且找不到对应属性则忽略赋值
				if (logger.isDebugEnabled()) {
					logger.debug("Ignoring optional value for property '" + tokens.actualName +
							"' - property not found on bean class [" + getRootClass().getName() + "]");
				}
				return;
			}
			// 如果配置为忽略不可写异常，则直接返回
			if (this.suppressNotWritablePropertyException) {
				return;
			}
			// 否则抛出不可写属性异常
			throw createNotWritablePropertyException(tokens.canonicalName);
		}

		Object oldValue = null;
		try {
			Object originalValue = pv.getValue();
			Object valueToApply = originalValue;

			// 判断是否需要转换
			if (!Boolean.FALSE.equals(pv.conversionNecessary)) {
				if (pv.isConverted()) {
					// 如果已经转换过，则直接使用转换后的值
					valueToApply = pv.getConvertedValue();
				} else {
					// 如果启用提取旧值功能且属性可读，尝试获取旧值
					if (isExtractOldValueForEditor() && ph.isReadable()) {
						try {
							oldValue = ph.getValue();
						} catch (Exception ex) {
							// 捕获并忽略无法获取旧值的异常，记录日志
							if (ex instanceof PrivilegedActionException) {
								ex = ((PrivilegedActionException) ex).getException();
							}
							if (logger.isDebugEnabled()) {
								logger.debug("Could not read previous value of property '" +
										this.nestedPath + tokens.canonicalName + "'", ex);
							}
						}
					}
					// 执行类型转换
					valueToApply = convertForProperty(
							tokens.canonicalName, oldValue, originalValue, ph.toTypeDescriptor());
				}
				// 标记是否需要转换
				pv.getOriginalPropertyValue().conversionNecessary = (valueToApply != originalValue);
			}
			// 调用属性处理器设置属性值
			ph.setValue(valueToApply);
		} catch (TypeMismatchException ex) {
			// 类型不匹配，直接抛出
			throw ex;
		} catch (InvocationTargetException ex) {
			// 反射调用属性 setter 出现异常时处理
			PropertyChangeEvent propertyChangeEvent = new PropertyChangeEvent(
					getRootInstance(), this.nestedPath + tokens.canonicalName, oldValue, pv.getValue());
			if (ex.getTargetException() instanceof ClassCastException) {
				// 如果是类型转换异常，包装为TypeMismatchException
				throw new TypeMismatchException(propertyChangeEvent, ph.getPropertyType(), ex.getTargetException());
			} else {
				// 否则抛出通用的MethodInvocationException
				Throwable cause = ex.getTargetException();
				if (cause instanceof UndeclaredThrowableException) {
					cause = cause.getCause();
				}
				throw new MethodInvocationException(propertyChangeEvent, cause);
			}
		} catch (Exception ex) {
			// 其他异常包装为MethodInvocationException
			PropertyChangeEvent pce = new PropertyChangeEvent(
					getRootInstance(), this.nestedPath + tokens.canonicalName, oldValue, pv.getValue());
			throw new MethodInvocationException(pce, ex);
		}
	}

	@Override
	@Nullable
	public Class<?> getPropertyType(String propertyName) throws BeansException {
		try {
			PropertyHandler ph = getPropertyHandler(propertyName);
			if (ph != null) {
				return ph.getPropertyType();
			} else {
				Object value = getPropertyValue(propertyName);
				if (value != null) {
					return value.getClass();
				}
				Class<?> editorType = guessPropertyTypeFromEditors(propertyName);
				if (editorType != null) {
					return editorType;
				}
			}
		} catch (InvalidPropertyException ex) {
		}
		return null;
	}

	@Override
	@Nullable
	public TypeDescriptor getPropertyTypeDescriptor(String propertyName) throws BeansException {
		try {
			AbstractNestablePropertyAccessor nestedPa = getPropertyAccessorForPropertyPath(propertyName);
			String finalPath = getFinalPath(nestedPa, propertyName);
			PropertyTokenHolder tokens = getPropertyNameTokens(finalPath);
			PropertyHandler ph = nestedPa.getLocalPropertyHandler(tokens.actualName);
			if (ph != null) {
				if (tokens.keys != null) {
					if (ph.isReadable() || ph.isWritable()) {
						return ph.nested(tokens.keys.length);
					}
				} else {
					if (ph.isReadable() || ph.isWritable()) {
						return ph.toTypeDescriptor();
					}
				}
			}
		} catch (InvalidPropertyException ex) {
		}
		return null;
	}

	@Override
	public boolean isReadableProperty(String propertyName) {
		try {
			PropertyHandler ph = getPropertyHandler(propertyName);
			if (ph != null) {
				return ph.isReadable();
			} else {
				getPropertyValue(propertyName);
				return true;
			}
		} catch (InvalidPropertyException ex) {
		}
		return false;
	}

	@Override
	public boolean isWritableProperty(String propertyName) {
		try {
			PropertyHandler ph = getPropertyHandler(propertyName);
			if (ph != null) {
				return ph.isWritable();
			} else {
				getPropertyValue(propertyName);
				return true;
			}
		} catch (InvalidPropertyException ex) {
		}
		return false;
	}

	@Nullable
	private Object convertIfNecessary(@Nullable String propertyName, @Nullable Object oldValue,
									  @Nullable Object newValue, @Nullable Class<?> requiredType, @Nullable TypeDescriptor td)
			throws TypeMismatchException {

		Assert.state(this.typeConverterDelegate != null, "No TypeConverterDelegate");
		try {
			return this.typeConverterDelegate.convertIfNecessary(propertyName, oldValue, newValue, requiredType, td);
		} catch (ConverterNotFoundException | IllegalStateException ex) {
			PropertyChangeEvent pce =
					new PropertyChangeEvent(getRootInstance(), this.nestedPath + propertyName, oldValue, newValue);
			throw new ConversionNotSupportedException(pce, requiredType, ex);
		} catch (ConversionException | IllegalArgumentException ex) {
			PropertyChangeEvent pce =
					new PropertyChangeEvent(getRootInstance(), this.nestedPath + propertyName, oldValue, newValue);
			throw new TypeMismatchException(pce, requiredType, ex);
		}
	}

	@Nullable
	protected Object convertForProperty(
			String propertyName, @Nullable Object oldValue, @Nullable Object newValue, TypeDescriptor td)
			throws TypeMismatchException {

		return convertIfNecessary(propertyName, oldValue, newValue, td.getType(), td);
	}

	@Override
	@Nullable
	public Object getPropertyValue(String propertyName) throws BeansException {
		AbstractNestablePropertyAccessor nestedPa = getPropertyAccessorForPropertyPath(propertyName);
		PropertyTokenHolder tokens = getPropertyNameTokens(getFinalPath(nestedPa, propertyName));
		return nestedPa.getPropertyValue(tokens);
	}

	@SuppressWarnings("unchecked")
	@Nullable
	protected Object getPropertyValue(PropertyTokenHolder tokens) throws BeansException {
		String propertyName = tokens.canonicalName;
		String actualName = tokens.actualName;
		PropertyHandler ph = getLocalPropertyHandler(actualName);
		if (ph == null || !ph.isReadable()) {
			throw new NotReadablePropertyException(getRootClass(), this.nestedPath + propertyName);
		}
		try {
			Object value = ph.getValue();
			if (tokens.keys != null) {
				if (value == null) {
					if (isAutoGrowNestedPaths()) {
						value = setDefaultValue(new PropertyTokenHolder(tokens.actualName));
					} else {
						throw new NullValueInNestedPathException(getRootClass(), this.nestedPath + propertyName,
								"Cannot access indexed value of property referenced in indexed " +
										"property path '" + propertyName + "': returned null");
					}
				}
				StringBuilder indexedPropertyName = new StringBuilder(tokens.actualName);
				for (int i = 0; i < tokens.keys.length; i++) {
					String key = tokens.keys[i];
					if (value == null) {
						throw new NullValueInNestedPathException(getRootClass(), this.nestedPath + propertyName,
								"Cannot access indexed value of property referenced in indexed " +
										"property path '" + propertyName + "': returned null");
					} else if (value.getClass().isArray()) {
						int index = Integer.parseInt(key);
						value = growArrayIfNecessary(value, index, indexedPropertyName.toString());
						value = Array.get(value, index);
					} else if (value instanceof List) {
						int index = Integer.parseInt(key);
						List<Object> list = (List<Object>) value;
						growCollectionIfNecessary(list, index, indexedPropertyName.toString(), ph, i + 1);
						value = list.get(index);
					} else if (value instanceof Set) {
						Set<Object> set = (Set<Object>) value;
						int index = Integer.parseInt(key);
						if (index < 0 || index >= set.size()) {
							throw new InvalidPropertyException(getRootClass(), this.nestedPath + propertyName,
									"Cannot get element with index " + index + " from Set of size " +
											set.size() + ", accessed using property path '" + propertyName + "'");
						}
						Iterator<Object> it = set.iterator();
						for (int j = 0; it.hasNext(); j++) {
							Object elem = it.next();
							if (j == index) {
								value = elem;
								break;
							}
						}
					} else if (value instanceof Map) {
						Map<Object, Object> map = (Map<Object, Object>) value;
						Class<?> mapKeyType = ph.getResolvableType().getNested(i + 1).asMap().resolveGeneric(0);
						TypeDescriptor typeDescriptor = TypeDescriptor.valueOf(mapKeyType);
						Object convertedMapKey = convertIfNecessary(null, null, key, mapKeyType, typeDescriptor);
						value = map.get(convertedMapKey);
					} else {
						throw new InvalidPropertyException(getRootClass(), this.nestedPath + propertyName,
								"Property referenced in indexed property path '" + propertyName +
										"' is neither an array nor a List nor a Set nor a Map; returned value was [" + value +
										"]");
					}
					indexedPropertyName.append(PROPERTY_KEY_PREFIX).append(key).append(PROPERTY_KEY_SUFFIX);
				}
			}
			return value;
		} catch (IndexOutOfBoundsException ex) {
			throw new InvalidPropertyException(getRootClass(), this.nestedPath + propertyName,
					"Index of out of bounds in property path '" + propertyName + "'", ex);
		} catch (NumberFormatException | TypeMismatchException ex) {
			throw new InvalidPropertyException(getRootClass(), this.nestedPath + propertyName,
					"Invalid index in property path '" + propertyName + "'", ex);
		} catch (InvocationTargetException ex) {
			throw new InvalidPropertyException(getRootClass(), this.nestedPath + propertyName,
					"Getter for property '" + actualName + "' threw exception", ex);
		} catch (Exception ex) {
			throw new InvalidPropertyException(getRootClass(), this.nestedPath + propertyName,
					"Illegal attempt to get property '" + actualName + "' threw exception", ex);
		}
	}


	@Nullable
	protected PropertyHandler getPropertyHandler(String propertyName) throws BeansException {
		Assert.notNull(propertyName, "Property name must not be null");
		AbstractNestablePropertyAccessor nestedPa = getPropertyAccessorForPropertyPath(propertyName);
		return nestedPa.getLocalPropertyHandler(getFinalPath(nestedPa, propertyName));
	}

	@Nullable
	protected abstract PropertyHandler getLocalPropertyHandler(String propertyName);

	protected abstract AbstractNestablePropertyAccessor newNestedPropertyAccessor(Object object, String nestedPath);

	protected abstract NotWritablePropertyException createNotWritablePropertyException(String propertyName);


	private Object growArrayIfNecessary(Object array, int index, String name) {
		if (!isAutoGrowNestedPaths()) {
			return array;
		}
		int length = Array.getLength(array);
		if (index >= length && index < this.autoGrowCollectionLimit) {
			Class<?> componentType = array.getClass().getComponentType();
			Object newArray = Array.newInstance(componentType, index + 1);
			System.arraycopy(array, 0, newArray, 0, length);
			for (int i = length; i < Array.getLength(newArray); i++) {
				Array.set(newArray, i, newValue(componentType, null, name));
			}
			setPropertyValue(name, newArray);
			Object defaultValue = getPropertyValue(name);
			Assert.state(defaultValue != null, "Default value must not be null");
			return defaultValue;
		} else {
			return array;
		}
	}

	private void growCollectionIfNecessary(Collection<Object> collection, int index, String name,
										   PropertyHandler ph, int nestingLevel) {

		if (!isAutoGrowNestedPaths()) {
			return;
		}
		int size = collection.size();
		if (index >= size && index < this.autoGrowCollectionLimit) {
			Class<?> elementType = ph.getResolvableType().getNested(nestingLevel).asCollection().resolveGeneric();
			if (elementType != null) {
				for (int i = collection.size(); i < index + 1; i++) {
					collection.add(newValue(elementType, null, name));
				}
			}
		}
	}

	protected String getFinalPath(AbstractNestablePropertyAccessor pa, String nestedPath) {
		if (pa == this) {
			return nestedPath;
		}
		return nestedPath.substring(PropertyAccessorUtils.getLastNestedPropertySeparatorIndex(nestedPath) + 1);
	}

	protected AbstractNestablePropertyAccessor getPropertyAccessorForPropertyPath(String propertyPath) {
		int pos = PropertyAccessorUtils.getFirstNestedPropertySeparatorIndex(propertyPath);
		if (pos > -1) {
			String nestedProperty = propertyPath.substring(0, pos);
			String nestedPath = propertyPath.substring(pos + 1);
			AbstractNestablePropertyAccessor nestedPa = getNestedPropertyAccessor(nestedProperty);
			return nestedPa.getPropertyAccessorForPropertyPath(nestedPath);
		} else {
			return this;
		}
	}

	private AbstractNestablePropertyAccessor getNestedPropertyAccessor(String nestedProperty) {
		if (this.nestedPropertyAccessors == null) {
			this.nestedPropertyAccessors = new HashMap<>();
		}
		PropertyTokenHolder tokens = getPropertyNameTokens(nestedProperty);
		String canonicalName = tokens.canonicalName;
		Object value = getPropertyValue(tokens);
		if (value == null || (value instanceof Optional && !((Optional<?>) value).isPresent())) {
			if (isAutoGrowNestedPaths()) {
				value = setDefaultValue(tokens);
			} else {
				throw new NullValueInNestedPathException(getRootClass(), this.nestedPath + canonicalName);
			}
		}

		AbstractNestablePropertyAccessor nestedPa = this.nestedPropertyAccessors.get(canonicalName);
		if (nestedPa == null || nestedPa.getWrappedInstance() != ObjectUtils.unwrapOptional(value)) {
			if (logger.isTraceEnabled()) {
				logger.trace("Creating new nested " + getClass().getSimpleName() + " for property '" + canonicalName + "'");
			}
			nestedPa = newNestedPropertyAccessor(value, this.nestedPath + canonicalName + NESTED_PROPERTY_SEPARATOR);
			copyDefaultEditorsTo(nestedPa);
			copyCustomEditorsTo(nestedPa, canonicalName);
			this.nestedPropertyAccessors.put(canonicalName, nestedPa);
		} else {
			if (logger.isTraceEnabled()) {
				logger.trace("Using cached nested property accessor for property '" + canonicalName + "'");
			}
		}
		return nestedPa;
	}

	private Object setDefaultValue(PropertyTokenHolder tokens) {
		PropertyValue pv = createDefaultPropertyValue(tokens);
		setPropertyValue(tokens, pv);
		Object defaultValue = getPropertyValue(tokens);
		Assert.state(defaultValue != null, "Default value must not be null");
		return defaultValue;
	}

	private PropertyValue createDefaultPropertyValue(PropertyTokenHolder tokens) {
		TypeDescriptor desc = getPropertyTypeDescriptor(tokens.canonicalName);
		if (desc == null) {
			throw new NullValueInNestedPathException(getRootClass(), this.nestedPath + tokens.canonicalName,
					"Could not determine property type for auto-growing a default value");
		}
		Object defaultValue = newValue(desc.getType(), desc, tokens.canonicalName);
		return new PropertyValue(tokens.canonicalName, defaultValue);
	}

	private Object newValue(Class<?> type, @Nullable TypeDescriptor desc, String name) {
		try {
			if (type.isArray()) {
				Class<?> componentType = type.getComponentType();
				if (componentType.isArray()) {
					Object array = Array.newInstance(componentType, 1);
					Array.set(array, 0, Array.newInstance(componentType.getComponentType(), 0));
					return array;
				} else {
					return Array.newInstance(componentType, 0);
				}
			} else if (Collection.class.isAssignableFrom(type)) {
				TypeDescriptor elementDesc = (desc != null ? desc.getElementTypeDescriptor() : null);
				return CollectionFactory.createCollection(type, (elementDesc != null ? elementDesc.getType() : null), 16);
			} else if (Map.class.isAssignableFrom(type)) {
				TypeDescriptor keyDesc = (desc != null ? desc.getMapKeyTypeDescriptor() : null);
				return CollectionFactory.createMap(type, (keyDesc != null ? keyDesc.getType() : null), 16);
			} else {
				Constructor<?> ctor = type.getDeclaredConstructor();
				if (Modifier.isPrivate(ctor.getModifiers())) {
					throw new IllegalAccessException("Auto-growing not allowed with private constructor: " + ctor);
				}
				return BeanUtils.instantiateClass(ctor);
			}
		} catch (Throwable ex) {
			throw new NullValueInNestedPathException(getRootClass(), this.nestedPath + name,
					"Could not instantiate property type [" + type.getName() + "] to auto-grow nested property path", ex);
		}
	}

	private PropertyTokenHolder getPropertyNameTokens(String propertyName) {
		String actualName = null;
		List<String> keys = new ArrayList<>(2);
		int searchIndex = 0;
		while (searchIndex != -1) {
			int keyStart = propertyName.indexOf(PROPERTY_KEY_PREFIX, searchIndex);
			searchIndex = -1;
			if (keyStart != -1) {
				int keyEnd = getPropertyNameKeyEnd(propertyName, keyStart + PROPERTY_KEY_PREFIX.length());
				if (keyEnd != -1) {
					if (actualName == null) {
						actualName = propertyName.substring(0, keyStart);
					}
					String key = propertyName.substring(keyStart + PROPERTY_KEY_PREFIX.length(), keyEnd);
					if (key.length() > 1 && (key.startsWith("'") && key.endsWith("'")) ||
							(key.startsWith("\"") && key.endsWith("\""))) {
						key = key.substring(1, key.length() - 1);
					}
					keys.add(key);
					searchIndex = keyEnd + PROPERTY_KEY_SUFFIX.length();
				}
			}
		}
		PropertyTokenHolder tokens = new PropertyTokenHolder(actualName != null ? actualName : propertyName);
		if (!keys.isEmpty()) {
			tokens.canonicalName += PROPERTY_KEY_PREFIX +
					StringUtils.collectionToDelimitedString(keys, PROPERTY_KEY_SUFFIX + PROPERTY_KEY_PREFIX) +
					PROPERTY_KEY_SUFFIX;
			tokens.keys = StringUtils.toStringArray(keys);
		}
		return tokens;
	}

	private int getPropertyNameKeyEnd(String propertyName, int startIndex) {
		int unclosedPrefixes = 0;
		int length = propertyName.length();
		for (int i = startIndex; i < length; i++) {
			switch (propertyName.charAt(i)) {
				case PropertyAccessor.PROPERTY_KEY_PREFIX_CHAR:
					unclosedPrefixes++;
					break;
				case PropertyAccessor.PROPERTY_KEY_SUFFIX_CHAR:
					if (unclosedPrefixes == 0) {
						return i;
					} else {
						unclosedPrefixes--;
					}
					break;
			}
		}
		return -1;
	}


	@Override
	public String toString() {
		String className = getClass().getName();
		if (this.wrappedObject == null) {
			return className + ": no wrapped object set";
		}
		return className + ": wrapping object [" + ObjectUtils.identityToString(this.wrappedObject) + ']';
	}


	protected abstract static class PropertyHandler {

		@Nullable
		private final Class<?> propertyType;

		private final boolean readable;

		private final boolean writable;

		public PropertyHandler(@Nullable Class<?> propertyType, boolean readable, boolean writable) {
			this.propertyType = propertyType;
			this.readable = readable;
			this.writable = writable;
		}

		@Nullable
		public Class<?> getPropertyType() {
			return this.propertyType;
		}

		public boolean isReadable() {
			return this.readable;
		}

		public boolean isWritable() {
			return this.writable;
		}

		public abstract TypeDescriptor toTypeDescriptor();

		public abstract ResolvableType getResolvableType();

		@Nullable
		public Class<?> getMapKeyType(int nestingLevel) {
			return getResolvableType().getNested(nestingLevel).asMap().resolveGeneric(0);
		}

		@Nullable
		public Class<?> getMapValueType(int nestingLevel) {
			return getResolvableType().getNested(nestingLevel).asMap().resolveGeneric(1);
		}

		@Nullable
		public Class<?> getCollectionType(int nestingLevel) {
			return getResolvableType().getNested(nestingLevel).asCollection().resolveGeneric();
		}

		@Nullable
		public abstract TypeDescriptor nested(int level);

		@Nullable
		public abstract Object getValue() throws Exception;

		public abstract void setValue(@Nullable Object value) throws Exception;
	}


	protected static class PropertyTokenHolder {

		public PropertyTokenHolder(String name) {
			this.actualName = name;
			this.canonicalName = name;
		}

		public String actualName;

		public String canonicalName;

		@Nullable
		public String[] keys;
	}

}
