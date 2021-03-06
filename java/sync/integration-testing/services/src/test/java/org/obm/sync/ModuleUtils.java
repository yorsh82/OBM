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
package org.obm.sync;

import java.io.Serializable;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.apache.commons.io.FileUtils;
import org.hornetq.core.remoting.impl.netty.NettyAcceptorFactory;
import org.hornetq.core.remoting.impl.netty.TransportConstants;
import org.obm.Configuration;
import org.obm.Configuration.ObmSyncWithLDAPAuth;
import org.obm.ConfigurationModule;
import org.obm.configuration.DatabaseConfiguration;
import org.obm.configuration.LocatorConfiguration;
import org.obm.dao.utils.H2ConnectionProvider;
import org.obm.dbcp.DatabaseConfigurationFixtureH2;
import org.obm.dbcp.DatabaseConnectionProvider;
import org.obm.dbcp.jdbc.DatabaseDriverConfiguration;
import org.obm.dbcp.jdbc.H2DriverConfiguration;
import org.obm.locator.LocatorClientException;
import org.obm.locator.store.LocatorService;
import org.obm.sync.ObmSyncStaticConfigurationService.ObmSyncConfiguration;
import org.obm.sync.solr.SolrRequest;
import org.obm.sync.solr.jms.Command;
import org.obm.sync.solr.jms.CommandConverter;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import com.linagora.obm.sync.HornetQConfiguration;

import fr.aliacom.obm.services.constant.ObmSyncConfigurationService;

public class ModuleUtils {

	public static Module buildDummyConfigurationModuleWithLDAPAuth(final Configuration configuration) {
		return Modules.override(buildDummyConfigurationModule(configuration)).with(new AbstractModule() {

			@Override
			protected void configure() {
				bind(ObmSyncConfigurationService.class).toInstance(new ObmSyncConfiguration(configuration, new ObmSyncWithLDAPAuth()));
			}

		});
	}

	public static Module buildDummyConfigurationModule(final Configuration configuration) {
		return new AbstractModule() {

			@Override
			protected void configure() {
				Multibinder<LifecycleListener> lifecycleListeners = Multibinder.newSetBinder(binder(), LifecycleListener.class);
				lifecycleListeners.addBinding().toInstance(new LifecycleListener() {
					@Override
					public void shutdown() throws Exception {
						FileUtils.deleteDirectory(configuration.dataDir);						
					}
				});
				install(new ConfigurationModule(configuration));
				Multibinder<DatabaseDriverConfiguration> databaseDrivers = Multibinder.newSetBinder(binder(), DatabaseDriverConfiguration.class);
				databaseDrivers.addBinding().to(H2DriverConfiguration.class);
				bind(DatabaseConnectionProvider.class).to(H2ConnectionProvider.class);
				bind(DatabaseConfiguration.class).to(DatabaseConfigurationFixtureH2.class);
				ObmSyncStaticConfigurationService.ObmSyncConfiguration obmSyncConfiguration = 
						new ObmSyncStaticConfigurationService.ObmSyncConfiguration(configuration, new Configuration.ObmSync());
				bind(ObmSyncConfigurationService.class).toInstance(obmSyncConfiguration);
				bind(LocatorConfiguration.class).toInstance(obmSyncConfiguration);
				bind(LocatorService.class).toInstance(new LocatorService() {
					
					@Override
					public String getServiceLocation(String serviceSlashProperty, String loginAtDomain) throws LocatorClientException {
						return "localhost";
					}
				});
				
				String jmsDataDirectory = configuration.dataDir + "/" + "jms/data";
				bind(org.hornetq.core.config.Configuration.class).toInstance(
						HornetQConfiguration.configuration()
						.enablePersistence(true)
						.enableSecurity(false)
						.largeMessagesDirectory(jmsDataDirectory + "/large-messages")
						.bindingsDirectory(jmsDataDirectory + "/bindings")
						.journalDirectory(jmsDataDirectory + "/journal")
						.connector(HornetQConfiguration.Connector.HornetQInVMCore)
						.acceptor(HornetQConfiguration.Acceptor.HornetQInVMCore)
						.build());
			}
		};
	}
	
	public static Module buildDummySolrModule() {
		return new AbstractModule() {

			@Override
			protected void configure() {
				bind(CommandConverter.class).toInstance(new CommandConverter() {
					
					@Override
					public <T extends Serializable> SolrRequest convert(Command<T> command) throws Exception {
						return new SolrRequest(null) {
							
							@Override
							public void run() throws Exception {
								// do nothing
							}
						};
					}
				});
			}
		};
	}

	public static Module buildSocketJMSModule(final Configuration configuration) {
		return new AbstractModule() {
			
			@Override
			protected void configure() {
				String jmsDataDirectory = configuration.dataDir + "/" + "jms/data";
				bind(org.hornetq.core.config.Configuration.class).toInstance(
						HornetQConfiguration.configuration()
						.enablePersistence(true)
						.enableSecurity(false)
						.largeMessagesDirectory(jmsDataDirectory + "/large-messages")
						.bindingsDirectory(jmsDataDirectory + "/bindings")
						.journalDirectory(jmsDataDirectory + "/journal")
						.connector(HornetQConfiguration.Connector.HornetQInVMCore)
						.acceptor(HornetQConfiguration.Acceptor.HornetQInVMCore)
						.acceptor(HornetQConfiguration.acceptorBuilder()
							.name("obmAcceptor")
							.factory(NettyAcceptorFactory.class)
							.param(TransportConstants.HOST_PROP_NAME, "127.0.0.1")
							.param(TransportConstants.PORT_PROP_NAME, 5446)
							.build())
						.build());
			}
		};
	}

	public static Module buildDummySmtpModule() {
		return new AbstractModule() {

			@Override
			protected void configure() {
				bind(ObmSmtpService.class).toInstance(new ObmSmtpService() {
					
					@Override
					public void sendEmail(MimeMessage message, Session session) throws MessagingException {
						// do nothing
					}
					
				});
			}
		};
	}
}
