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
package org.obm.push.mail.imap;

import java.util.Properties;

import javax.mail.NoSuchProviderException;
import javax.mail.Session;

import org.minig.imap.IdleClient;
import org.minig.imap.StoreClient;
import org.obm.configuration.EmailConfiguration;
import org.obm.locator.LocatorClientException;
import org.obm.locator.store.LocatorService;
import org.obm.push.bean.BackendSession;
import org.obm.push.exception.NoImapClientAvailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.sun.mail.imap.IMAPStore;

public class ImapClientProviderImpl implements ImapClientProvider {

	private static final Logger logger = LoggerFactory.getLogger(ImapClientProviderImpl.class);
	
	private final LocatorService locatorService;
	private final ManagedLifecycleImapStore.Factory managedLifecycleImapStoreFactory;
	private final boolean loginWithDomain;
	private final int imapPort;
	private final Session defaultSession;

	@Inject
	private ImapClientProviderImpl(EmailConfiguration emailConfiguration, LocatorService locatorService,
			ManagedLifecycleImapStore.Factory managedLifecycleImapStoreFactory) {
		this.locatorService = locatorService;
		this.managedLifecycleImapStoreFactory = managedLifecycleImapStoreFactory;
		this.loginWithDomain = emailConfiguration.loginWithDomain();
		this.imapPort = emailConfiguration.imapPort();
		
		Properties imapProperties = buildProperties(emailConfiguration);
		this.defaultSession = Session.getInstance(imapProperties);
	}

	private Properties buildProperties(EmailConfiguration emailConfiguration) {
		Properties properties = new Properties();

		boolean activateTls = emailConfiguration.activateTls();
		logger.debug("Java Mail settings : STARTTLS=" + activateTls);
		properties.put("mail.imap.starttls.enable", activateTls);
		
		int imapTimeout = emailConfiguration.imapTimeout();
		logger.debug("Java Mail settings : TIMEOUT=" + imapTimeout);
		properties.put("mail.imap.timeout", imapTimeout);
		properties.put("mail.imap.fetchsize", emailConfiguration.getImapFetchBlockSize());
		properties.put("mail.debug", "false");
		
		return properties;
	}

	
	@Override
	public String locateImap(BackendSession bs) throws LocatorClientException {
		String imapLocation = locatorService.
				getServiceLocation("mail/imap_frontend", bs.getUser().getLoginAtDomain());
		logger.info("Using {} as imap host.", imapLocation);
		return imapLocation;
	}

	@Override
	public StoreClient getImapClient(BackendSession bs) throws LocatorClientException {
		final String imapHost = locateImap(bs);
		final String login = getLogin(bs);
		StoreClient storeClient = new StoreClient(imapHost, imapPort, login, bs.getPassword()); 
		
		logger.debug("Creating storeClient with login {} : " +
				"loginWithDomain = {}", 
				new Object[]{login, loginWithDomain});
		
		return storeClient; 
	}
	
	@Override
	public ImapStore getImapClientWithJM(BackendSession bs) throws LocatorClientException, NoImapClientAvailableException {
		final String imapHost = locateImap(bs);
		final String login = getLogin(bs);
		
		try {
			logger.debug("Creating storeClient with login {} : loginWithDomain = {}", 
					new Object[]{login, loginWithDomain});

			IMAPStore javaMailStore = (IMAPStore) defaultSession.getStore(EmailConfiguration.IMAP_PROTOCOL);
			return managedLifecycleImapStoreFactory.create(
					defaultSession, javaMailStore, login, bs.getPassword(), imapHost, imapPort);
		} catch (NoSuchProviderException e) {
			throw new NoImapClientAvailableException(
					"No client available for protocol : " + EmailConfiguration.IMAP_PROTOCOL, e);
		}
	}

	private String getLogin(BackendSession bs) {
		String login = bs.getUser().getLoginAtDomain();
		if (!loginWithDomain) {
			int at = login.indexOf("@");
			if (at > 0) {
				login = login.substring(0, at);
			}
		}
		return login;
	}


	@Override
	public IdleClient getImapIdleClient(BackendSession bs)
			throws LocatorClientException {
		String login = getLogin(bs);
		logger.debug("Creating idleClient with login: {}, (useDomain {})", login, loginWithDomain);
		return new IdleClient(locateImap(bs), 143, login, bs.getPassword());
	}

}
