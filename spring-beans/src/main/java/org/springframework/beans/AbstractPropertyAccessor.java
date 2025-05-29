package org.springframework.beans;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.lang.Nullable;

/**
 * 抽象的属性访问器基类，实现了 ConfigurablePropertyAccessor 接口。
 * 该类封装了属性的批量设置逻辑，以及嵌套属性处理、异常捕获控制等公共逻辑。
 * 实际访问属性的逻辑由子类实现（如 BeanWrapperImpl）。
 */
public abstract class AbstractPropertyAccessor extends TypeConverterSupport implements ConfigurablePropertyAccessor {

	// 是否在设置属性之前提取旧值（常用于 PropertyEditor 编辑器比较前后值）
	private boolean extractOldValueForEditor = false;

	// 是否自动为嵌套路径中为 null 的部分创建对象（如 person.address.city，当 address 为 null 时是否自动创建）
	private boolean autoGrowNestedPaths = false;

	// 是否抑制属性不可写异常（用于批量设置属性时忽略未知属性）
	boolean suppressNotWritablePropertyException = false;

	// 设置是否在编辑器中提取旧值
	@Override
	public void setExtractOldValueForEditor(boolean extractOldValueForEditor) {
		this.extractOldValueForEditor = extractOldValueForEditor;
	}

	// 获取是否在编辑器中提取旧值的设置
	@Override
	public boolean isExtractOldValueForEditor() {
		return this.extractOldValueForEditor;
	}

	// 设置是否自动创建嵌套路径中的中间对象
	@Override
	public void setAutoGrowNestedPaths(boolean autoGrowNestedPaths) {
		this.autoGrowNestedPaths = autoGrowNestedPaths;
	}

	// 获取是否自动创建嵌套路径中间对象的设置
	@Override
	public boolean isAutoGrowNestedPaths() {
		return this.autoGrowNestedPaths;
	}

	// 设置单个属性值（封装为 PropertyValue）
	@Override
	public void setPropertyValue(PropertyValue pv) throws BeansException {
		setPropertyValue(pv.getName(), pv.getValue());
	}

	// 将 Map 结构的属性值转换为 PropertyValues 并设置
	@Override
	public void setPropertyValues(Map<?, ?> map) throws BeansException {
		setPropertyValues(new MutablePropertyValues(map));
	}

	// 默认不忽略任何异常地批量设置属性值
	@Override
	public void setPropertyValues(PropertyValues pvs) throws BeansException {
		setPropertyValues(pvs, false, false);
	}

	// 可配置是否忽略未知属性的批量设置
	@Override
	public void setPropertyValues(PropertyValues pvs, boolean ignoreUnknown) throws BeansException {
		setPropertyValues(pvs, ignoreUnknown, false);
	}

	/**
	 * 核心批量设置属性值方法，支持忽略未知属性和无效嵌套路径。
	 *
	 * @param pvs           属性值集合
	 * @param ignoreUnknown 是否忽略未知属性（找不到 setter）
	 * @param ignoreInvalid 是否忽略嵌套路径中 null 的情况
	 */
	@Override
	public void setPropertyValues(PropertyValues pvs, boolean ignoreUnknown, boolean ignoreInvalid)
			throws BeansException {

		// 用于收集过程中出现的属性访问异常
		List<PropertyAccessException> propertyAccessExceptions = null;

		// 获取属性值列表（适配不同类型的 PropertyValues）
		List<PropertyValue> propertyValues = (pvs instanceof MutablePropertyValues ?
				((MutablePropertyValues) pvs).getPropertyValueList() : Arrays.asList(pvs.getPropertyValues()));

		if (ignoreUnknown) {
			this.suppressNotWritablePropertyException = true;
		}

		try {
			// 遍历所有属性值，逐一设置
			for (PropertyValue pv : propertyValues) {
				try {
					// 实现属性依赖注入功能
					setPropertyValue(pv);
				} catch (NotWritablePropertyException ex) {
					if (!ignoreUnknown) {
						throw ex; // 未知属性，未设置忽略，直接抛出异常
					}
				} catch (NullValueInNestedPathException ex) {
					if (!ignoreInvalid) {
						throw ex; // 嵌套路径中有 null，未设置忽略，抛出异常
					}
				} catch (PropertyAccessException ex) {
					// 其他属性访问异常，先收集
					if (propertyAccessExceptions == null) {
						propertyAccessExceptions = new ArrayList<>();
					}
					propertyAccessExceptions.add(ex);
				}
			}
		} finally {
			// 恢复抑制标志位
			if (ignoreUnknown) {
				this.suppressNotWritablePropertyException = false;
			}
		}

		// 如果有多个属性设置失败，则统一抛出批量异常
		if (propertyAccessExceptions != null) {
			PropertyAccessException[] paeArray = propertyAccessExceptions.toArray(new PropertyAccessException[0]);
			throw new PropertyBatchUpdateException(paeArray);
		}
	}

	// 返回指定属性路径的类型（此处未实现，返回 null，由子类覆盖）
	@Override
	@Nullable
	public Class<?> getPropertyType(String propertyPath) {
		return null;
	}

	// 抽象方法：获取属性值，由子类实现
	@Override
	@Nullable
	public abstract Object getPropertyValue(String propertyName) throws BeansException;

	// 抽象方法：设置属性值，由子类实现
	@Override
	public abstract void setPropertyValue(String propertyName, @Nullable Object value) throws BeansException;
}

