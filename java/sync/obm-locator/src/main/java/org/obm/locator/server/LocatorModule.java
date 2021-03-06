/* ***** BEGIN LICENSE BLOCK *****
 * 
 * Copyright (C) 2013-2014 Linagora
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
package org.obm.locator.server;

import org.obm.annotations.transactional.TransactionalModule;
import org.obm.configuration.ConfigurationModule;
import org.obm.configuration.ConfigurationService;
import org.obm.configuration.ConfigurationServiceImpl;
import org.obm.configuration.DatabaseConfigurationImpl;
import org.obm.configuration.DefaultTransactionConfiguration;
import org.obm.configuration.GlobalAppConfiguration;
import org.obm.configuration.module.LoggerModule;
import org.obm.dbcp.DatabaseModule;
import org.obm.locator.server.servlet.LocatorServletModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class LocatorModule extends AbstractModule {

	private static final String GLOBAL_CONFIGURATION_FILE = ConfigurationService.GLOBAL_OBM_CONFIGURATION_PATH;
	private static final String APPLICATION_NAME = "obm-locator";
	
	@Override
	protected void configure() {
		bind(String.class).annotatedWith(Names.named("application-name")).toInstance(APPLICATION_NAME);
		bind(Logger.class).annotatedWith(Names.named(LoggerModule.CONFIGURATION)).toInstance(LoggerFactory.getLogger(LoggerModule.CONFIGURATION));
		
		install(new DatabaseModule());
		install(new ConfigurationModule<ConfigurationService>(buildConfiguration(), ConfigurationService.class));
		install(new TransactionalModule());
		install(new LocatorServletModule());
	}

	private GlobalAppConfiguration<ConfigurationService> buildConfiguration() {
		return GlobalAppConfiguration.<ConfigurationService>builder()
					.mainConfiguration(new ConfigurationServiceImpl.Factory().create(GLOBAL_CONFIGURATION_FILE, APPLICATION_NAME))
					.databaseConfiguration(new DatabaseConfigurationImpl.Factory().create(GLOBAL_CONFIGURATION_FILE))
					.transactionConfiguration(new DefaultTransactionConfiguration.Factory().create(APPLICATION_NAME, new ConfigurationServiceImpl.Factory().create(GLOBAL_CONFIGURATION_FILE, APPLICATION_NAME)))
					.build();
	}

	
}
