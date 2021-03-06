/* ***** BEGIN LICENSE BLOCK *****
 * Copyright (C) 2011-2014  Linagora
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version, provided you comply with the Additional Terms applicable for OBM
 * software by Linagora pursuant to Section 7 of the GNU Affero General Public
 * License, subsections (b), (c), and (e), pursuant to which you must notably (i)
 * retain the displaying by the interactive user interfaces of the “OBM, Free
 * Communication by Linagora” Logo with the “You are using the Open Source and
 * free version of OBM developed and supported by Linagora. Contribute to OBM R&D
 * by subscribing to an Enterprise offer !” infobox, (ii) retain all hypertext
 * links between OBM and obm.org, between Linagora and linagora.com, as well as
 * between the expression “Enterprise offer” and pro.obm.org, and (iii) refrain
 * from infringing Linagora intellectual property rights over its trademarks and
 * commercial brands. Other Additional Terms apply, see
 * <http://www.linagora.com/licenses/> for more details.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License and
 * its applicable Additional Terms for OBM along with this program. If not, see
 * <http://www.gnu.org/licenses/> for the GNU Affero General   Public License
 * version 3 and <http://www.linagora.com/licenses/> for the Additional Terms
 * applicable to the OBM software.
 * ***** END LICENSE BLOCK ***** */
package org.obm.sync.solr;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createMockBuilder;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.replay;

import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.hornetq.core.config.Configuration;
import org.hornetq.jms.server.config.JMSConfiguration;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.obm.locator.LocatorClientException;
import org.obm.locator.store.LocatorService;
import org.obm.sync.auth.AccessToken;
import org.obm.sync.book.Contact;
import org.obm.sync.solr.jms.DefaultCommandConverter;
import org.obm.sync.solr.jms.SolrJmsQueue;

import com.linagora.obm.sync.HornetQConfiguration;
import com.linagora.obm.sync.QueueManager;

import fr.aliacom.obm.ToolBox;
import fr.aliacom.obm.common.domain.ObmDomain;
import fr.aliacom.obm.services.constant.ObmSyncConfigurationService;

public class SolrHelperFactoryTest {

	private Contact contact;
	private AccessToken accessToken;
	private LocatorService locatorClient;
	private ContactIndexer.Factory contactIndexerFactory;
	private ContactIndexer contactIndexer;
	private SolrHelper.Factory factory;
	private SolrManager manager;
	private QueueManager queueManager;
	private ObmSyncConfigurationService configurationService;

	private static JMSConfiguration jmsConfiguration() {
		return 
			HornetQConfiguration.jmsConfiguration()
			.connectionFactory(
					HornetQConfiguration.connectionFactoryConfigurationBuilder()
					.name("ConnectionFactory")
					.connector(HornetQConfiguration.Connector.HornetQInVMCore)
					.binding("ConnectionFactory")
					.build())
			.topic("calendarChanges", SolrJmsQueue.CALENDAR_CHANGES_QUEUE.getName())
			.topic("contactChanges", SolrJmsQueue.CONTACT_CHANGES_QUEUE.getName())
			.build();
	}
	
	public static Configuration hornetQConfiguration() {
		return HornetQConfiguration.configuration()
				.enablePersistence(false)
				.enableSecurity(false)
				.journalDirectory("target/jms-journal")
				.connector(HornetQConfiguration.Connector.HornetQInVMCore)
				.acceptor(HornetQConfiguration.Acceptor.HornetQInVMCore)
				.build();
	}
	
	@Before
	public void setUp() throws Exception {
		queueManager = new QueueManager(hornetQConfiguration(), jmsConfiguration());
		queueManager.start();

		contact = new Contact();
		accessToken = ToolBox.mockAccessToken();
		locatorClient = createMock(LocatorService.class);
		configurationService = createMock(ObmSyncConfigurationService.class);
		contactIndexerFactory = createMockBuilder(ContactIndexer.Factory.class).addMockedMethod("createIndexer", CommonsHttpSolrServer.class, ObmDomain.class, Contact.class).createMock();
		contactIndexer = createMockBuilder(ContactIndexer.class).createMock();
		
		expect(configurationService.solrCheckingInterval()).andReturn(10);
		expect(locatorClient.getServiceLocation(isA(String.class), isA(String.class))).andReturn(null).anyTimes();
		expect(contactIndexerFactory.createIndexer(isA(CommonsHttpSolrServer.class), isA(ObmDomain.class), eq(contact))).andReturn(contactIndexer).once();

		replay(accessToken, locatorClient, contactIndexerFactory, configurationService);
		
		manager = new SolrManager(configurationService, queueManager, new DefaultCommandConverter(locatorClient, contactIndexerFactory, null));
		factory = new SolrHelper.Factory(manager, locatorClient);

		contact.setUid(1);

		
	}

	@After
	public void tearDown() throws Exception {
		manager.stop();
		queueManager.stop();
	}

	@Test(expected = IllegalStateException.class)
	public void create_contact_solr_remains_unavailable() throws LocatorClientException {
		index(false);
	}

	@Test(expected = IllegalStateException.class)
	public void delete_contact_solr_remains_unavailable() throws LocatorClientException {
		delete(false);
	}

	@Test
	public void create_contact_solr_available() throws LocatorClientException {
		try {
			index(true);
		}
		catch (IllegalStateException e) {
			fail();
		}
	}

	@Test
	public void delete_contact_solr_available() throws LocatorClientException {
		try {
			delete(true);
		}
		catch (IllegalStateException e) {
			fail();
		}
	}

	@Test
	public void create_contact_solr_becomes_available() throws  LocatorClientException {
		try {
			index(false);
		}
		catch (IllegalStateException e) {
		}
		create_contact_solr_available();
	}

	@Test
	public void delete_contact_solr_becomes_available() throws  LocatorClientException {
		try {
			delete(false);
		}
		catch (IllegalStateException e) {
		}
		delete_contact_solr_available();
	}

	@Test(expected = IllegalStateException.class)
	public void create_contact_solr_becomes_unavailable() throws Exception, LocatorClientException {
		create_contact_solr_available();
		index(false);
	}

	@Test(expected = IllegalStateException.class)
	public void delete_contact_solr_becomes_unavailable() throws LocatorClientException {
		delete_contact_solr_available();
		delete(false);
	}

	private void index(boolean solrUp) {
		manager.setSolrAvailable(solrUp);
		factory.createClient(accessToken).createOrUpdate(contact);
	}

	private void delete(boolean solrUp) {
		manager.setSolrAvailable(solrUp);
		factory.createClient(accessToken).delete(contact);
	}

	private void fail() {
		Assert.fail("creation should silently pass when the Indexer is available");
	}
}
