/* ***** BEGIN LICENSE BLOCK *****
 * 
 * Copyright (C) 2011-2012  Linagora
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

import static org.easymock.EasyMock.createControl;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.fest.assertions.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;

import org.easymock.IMocksControl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.obm.annotations.transactional.ITransactionAttributeBinder;
import org.obm.annotations.transactional.TransactionException;
import org.obm.annotations.transactional.Transactional;
import org.obm.dbcp.jdbc.PostgresDriverConfiguration;
import org.obm.filter.SlowFilterRunner;
import org.slf4j.Logger;

@RunWith(SlowFilterRunner.class)
public class DatabaseConnectionProviderImplTest {

	private IMocksControl control = createControl();
	private DatabaseConnectionProviderImpl dbConnProvider;
	private ITransactionAttributeBinder transactionAttributeBinder;
	private Transactional transactional;
	private Connection connection;

	@Before
	public void setUp() {
		transactionAttributeBinder = control.createMock(ITransactionAttributeBinder.class);
		transactional = control.createMock(Transactional.class);
		connection = control.createMock(Connection.class);

		dbConnProvider = new DatabaseConnectionProviderImpl(transactionAttributeBinder, new DatabaseConfigurationFixturePostgreSQL(), new PostgresDriverConfiguration(), createNiceMock(Logger.class));
	}

	@After
	public void tearDown() {
		dbConnProvider.cleanup();
		control.verify();
	}

	@Test(expected = SQLException.class)
	public void testGetConnection() throws SQLException {
		control.replay();

		dbConnProvider.getConnection();
	}

	@Test
	public void testIsReadOnlyTransactionWhenTrue() throws TransactionException {
		expect(transactionAttributeBinder.getTransactionalInCurrentTransaction()).andReturn(transactional).once();
		expect(transactional.readOnly()).andReturn(true).once();
		control.replay();

		assertThat(dbConnProvider.isReadOnlyTransaction()).isTrue();
	}

	@Test
	public void testIsReadOnlyTransactionWhenFalse() throws TransactionException {
		expect(transactionAttributeBinder.getTransactionalInCurrentTransaction()).andReturn(transactional).once();
		expect(transactional.readOnly()).andReturn(false).once();
		control.replay();

		assertThat(dbConnProvider.isReadOnlyTransaction()).isFalse();
	}

	@Test
	public void testIsReadOnlyTransactionWhenNoTransactional() throws TransactionException {
		expect(transactionAttributeBinder.getTransactionalInCurrentTransaction()).andReturn(null).once();
		control.replay();

		assertThat(dbConnProvider.isReadOnlyTransaction()).isTrue();
	}

	@Test
	public void testSetConnectionReadOnlyIfNecessaryOnTransactionReadOnlyButConnectionReadWrite() throws TransactionException, SQLException {
		expect(transactionAttributeBinder.getTransactionalInCurrentTransaction()).andReturn(transactional).once();
		expect(transactional.readOnly()).andReturn(true).once();
		expect(connection.isReadOnly()).andReturn(false).once();
		connection.setReadOnly(true);
		expectLastCall().once();
		control.replay();

		dbConnProvider.setConnectionReadOnlyIfNecessary(connection);
	}

	@Test
	public void testSetConnectionReadOnlyIfNecessaryOnTransactionReadWriteButConnectionReadOnly() throws TransactionException, SQLException {
		expect(transactionAttributeBinder.getTransactionalInCurrentTransaction()).andReturn(transactional).once();
		expect(transactional.readOnly()).andReturn(false).once();
		expect(connection.isReadOnly()).andReturn(true).once();
		connection.setReadOnly(false);
		expectLastCall().once();
		control.replay();

		dbConnProvider.setConnectionReadOnlyIfNecessary(connection);
	}

	@Test
	public void testSetConnectionReadOnlyIfNecessaryOnTransactionReadOnlyAndConnectionReadOnly() throws TransactionException, SQLException {
		expect(transactionAttributeBinder.getTransactionalInCurrentTransaction()).andReturn(transactional).once();
		expect(transactional.readOnly()).andReturn(true).once();
		expect(connection.isReadOnly()).andReturn(true).once();
		control.replay();

		dbConnProvider.setConnectionReadOnlyIfNecessary(connection);
	}

	@Test
	public void testSetConnectionReadOnlyIfNecessaryOnTransactionReadWriteAndConnectionReadWrite() throws TransactionException, SQLException {
		expect(transactionAttributeBinder.getTransactionalInCurrentTransaction()).andReturn(transactional).once();
		expect(transactional.readOnly()).andReturn(false).once();
		expect(connection.isReadOnly()).andReturn(false).once();
		control.replay();

		dbConnProvider.setConnectionReadOnlyIfNecessary(connection);
	}
}
