/* ***** BEGIN LICENSE BLOCK *****
 * 
 * Copyright (C) 2011-2014  Linagora
 *
 * This program is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU Affero General Public License as 
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version, provided you comply 
 * with the Additional Terms applicable for OBM connector by Linagora 
 * pursuant to Section 7 of the GNU Affero General Public License, 
 * subsections (b), (c), and (e), pursuant to which you must notably (i) retain 
 * the “Message sent thanks to OBM, Free Communication by Linagora” 
 * signature notice appended to any and all outbound messages 
 * (notably e-mail and meeting requests), (ii) retain all hypertext links between 
 * OBM and obm.org, as well as between Linagora and linagora.com, and (iii) refrain 
 * from infringing Linagora intellectual property rights over its trademarks 
 * and commercial brands. Other Additional Terms apply, 
 * see <http://www.linagora.com/licenses/> for more details. 
 *
 * This program is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License 
 * for more details. 
 *
 * You should have received a copy of the GNU Affero General Public License 
 * and its applicable Additional Terms for OBM along with this program. If not, 
 * see <http://www.gnu.org/licenses/> for the GNU Affero General Public License version 3 
 * and <http://www.linagora.com/licenses/> for the Additional Terms applicable to 
 * OBM connectors. 
 * 
 * ***** END LICENSE BLOCK ***** */
package org.obm.dbcp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.obm.annotations.transactional.ITransactionAttributeBinder;
import org.obm.annotations.transactional.TransactionException;
import org.obm.annotations.transactional.Transactional;
import org.obm.dbcp.jdbc.DatabaseDriverConfiguration;
import org.obm.push.utils.JDBCUtils;
import org.obm.sync.LifecycleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class DatabaseConnectionProviderImpl implements DatabaseConnectionProvider, LifecycleListener {

	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	private final ITransactionAttributeBinder transactionAttributeBinder;
	private final DatabaseDriverConfiguration driverConfiguration;
	private final PoolingDataSourceDecorator poolingDataSource;

	@Inject
	public DatabaseConnectionProviderImpl(
			ITransactionAttributeBinder transactionAttributeBinder,
			PoolingDataSourceDecorator poolingDataSource) {
		this.transactionAttributeBinder = transactionAttributeBinder;

		logger.info("Starting OBM connection pool...");

		this.poolingDataSource = poolingDataSource;
		driverConfiguration = poolingDataSource.getDatabaseDriverConfiguration();
	}

	public int lastInsertId(Connection con) throws SQLException {
		int ret = 0;
		Statement st = null;
		ResultSet rs = null;
		try {
			st = con.createStatement();
			rs = st.executeQuery(driverConfiguration.getLastInsertIdQuery());
			if (rs.next()) {
				ret = rs.getInt(1);
			}
		} finally {
			JDBCUtils.cleanup(null, st, rs);
		}
		return ret;
	}

	@Override
	public Connection getConnection() throws SQLException {
		Connection connection = null;
		try {
			connection = poolingDataSource.getConnection();
			setConnectionReadOnlyIfNecessary(connection);
			setTimeZoneToUTC(connection);
			return connection;
		} catch (Throwable e) {
			if (connection != null) {
				JDBCUtils.cleanup(connection, null, null);
			}
			throw new SQLException(e);
		}
	}

	private void setTimeZoneToUTC(Connection connection) throws SQLException {
		String gmtTimezoneQuery = driverConfiguration.getGMTTimezoneQuery();
		if (!Strings.isNullOrEmpty(gmtTimezoneQuery)) {
			PreparedStatement ps = connection.prepareStatement(gmtTimezoneQuery);
			try {
				ps.executeUpdate();
			}
			catch (SQLException ex) {
				ps.close();
				throw ex;
			}
		}
	}

	@VisibleForTesting void setConnectionReadOnlyIfNecessary(Connection connection) throws SQLException {
		if (driverConfiguration.readOnlySupported()) {
			try {
				boolean isReadOnlyTransaction = isReadOnlyTransaction();
				if (connection.isReadOnly() != isReadOnlyTransaction) {
					connection.setReadOnly(isReadOnlyTransaction);
				}
			} catch (TransactionException e) {
				logger.warn("Error while getting the current transaction, a read-only connection is returned");
				connection.setReadOnly(true);
			}
		}
	}

	@VisibleForTesting boolean isReadOnlyTransaction() throws TransactionException {
		Transactional transactional = transactionAttributeBinder.getTransactionalInCurrentTransaction();
		return transactional == null || transactional.readOnly();
	}

	@Override
	public Object getJdbcObject(String type, String value) throws SQLException {
		return driverConfiguration.getJDBCObject(type, value);
	}

	@Override
	public String getIntegerCastType() {
		return driverConfiguration.getIntegerCastType();
	}

	@Override
	public void shutdown() throws Exception {
		poolingDataSource.close();
	}
}
