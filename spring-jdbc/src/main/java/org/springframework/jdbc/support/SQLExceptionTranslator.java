package org.springframework.jdbc.support;

import java.sql.SQLException;

import org.springframework.dao.DataAccessException;
import org.springframework.lang.Nullable;

/**
 * 这是一个函数式接口，用来把 JDBC 抛出的 SQLException 异常
 * 转换成 Spring 统一管理的 DataAccessException 异常。
 *
 * 简单说，就是不同数据库（MySQL、Oracle、SQL Server）抛出来的异常可能格式不一样，
 * 用这个接口把它们“翻译”成 Spring 能识别的一种通用异常，方便程序统一处理。
 *
 * 常见的实现类有：
 * - SQLErrorCodeSQLExceptionTranslator（通过错误码翻译）
 * - SQLStateSQLExceptionTranslator（通过 SQL 状态码翻译）
 *
 * 因为它是一个函数式接口，所以可以用 Lambda 表达式来写。
 */
@FunctionalInterface
public interface SQLExceptionTranslator {

	/**
	 * 把 JDBC 抛出的 SQLException 异常翻译成 Spring 统一的 DataAccessException。
	 *
	 * @param task 当前在做的事情，比如 "执行查询"、"保存数据" —— 用来提示哪里出了错
	 * @param sql  你执行的 SQL 语句，没必要传也可以传 null
	 * @param ex   真正发生的 SQLException 异常
	 * @return 翻译后的 Spring 异常，方便统一处理
	 */
	@Nullable
	DataAccessException translate(String task, @Nullable String sql, SQLException ex);
}

