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
package org.obm.sync.client.setting;

import java.util.Map;

import org.apache.http.client.HttpClient;
import org.obm.breakdownduration.bean.Watch;
import org.obm.configuration.module.LoggerModule;
import org.obm.sync.BooleanParameter;
import org.obm.sync.BreakdownGroups;
import org.obm.sync.LongParameter;
import org.obm.sync.Parameter;
import org.obm.sync.StringParameter;
import org.obm.sync.auth.AccessToken;
import org.obm.sync.auth.ServerFault;
import org.obm.sync.client.impl.AbstractClientImpl;
import org.obm.sync.client.impl.SyncClientAssert;
import org.obm.sync.locators.Locator;
import org.obm.sync.services.ISetting;
import org.obm.sync.setting.ForwardingSettings;
import org.obm.sync.setting.SettingItemsParser;
import org.obm.sync.setting.VacationSettings;
import org.slf4j.Logger;
import org.w3c.dom.Document;

import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

@Watch(BreakdownGroups.CLIENT_SETTING)
public class SettingClient extends AbstractClientImpl implements ISetting {

	@Singleton
	public static class Factory {

		private final SyncClientAssert syncClientAssert;
		private final Locator locator;
		private final Logger obmSyncLogger;

		@Inject
		private Factory(SyncClientAssert syncClientAssert, Locator locator, @Named(LoggerModule.OBM_SYNC)Logger obmSyncLogger) {
			this.syncClientAssert = syncClientAssert;
			this.locator = locator;
			this.obmSyncLogger = obmSyncLogger;
		}
		
		public SettingClient create(HttpClient httpClient) {
			return new SettingClient(syncClientAssert, locator, obmSyncLogger, httpClient);
		}
	}
	
	private final SettingItemsParser respParser;
	private final Locator locator;

	private SettingClient(SyncClientAssert syncClientAssert, 
			Locator locator, 
			@Named(LoggerModule.OBM_SYNC)Logger obmSyncLogger, 
			HttpClient httpClient) {
		
		super(syncClientAssert, obmSyncLogger, httpClient);
		this.locator = locator;
		this.respParser = new SettingItemsParser();
	}

	@Override
	public Map<String, String> getSettings(AccessToken token) throws ServerFault {
		Multimap<String, Parameter> params = initParams(token);
		Document doc = execute(token, "/setting/getSettings", params);
		exceptionFactory.checkServerFaultException(doc);
		return respParser.parseListSettings(doc);
	}

	@Override
	public void setVacationSettings(AccessToken token, VacationSettings vs) throws ServerFault {
		Multimap<String, Parameter> params = initParams(token);
		params.put("enabled", new BooleanParameter(vs.isEnabled()));
		if (vs.isEnabled()) {
			if (vs.getStart() != null) {
				params.put("start", new LongParameter(vs.getStart().getTime()));
			}
			if (vs.getEnd() != null) {
				params.put("end", new LongParameter(vs.getEnd().getTime()));
			}
			params.put("text", new StringParameter(vs.getText()));
		}
		Document doc = execute(token, "/setting/setVacationSettings", params);
		exceptionFactory.checkServerFaultException(doc);
	}

	@Override
	public void setEmailForwarding(AccessToken token, ForwardingSettings fs) throws ServerFault {
		Multimap<String, Parameter> params = initParams(token);
		params.put("enabled", new BooleanParameter(fs.isEnabled()));
		if (fs.getEmail() != null && fs.isEnabled()) {
			params.put("email", new StringParameter(fs.getEmail()));
		}
		params.put("localCopy", new BooleanParameter(fs.isLocalCopy()));
		Document doc = execute(token, "/setting/setEmailForwarding", params);
		exceptionFactory.checkServerFaultException(doc);
	}

	@Override
	public ForwardingSettings getEmailForwarding(AccessToken token) throws ServerFault {
		Multimap<String, Parameter> params = initParams(token);
		Document doc = execute(token, "/setting/getEmailForwarding", params);
		exceptionFactory.checkServerFaultException(doc);
		return respParser.parseForwarding(doc);
	}

	@Override
	public VacationSettings getVacationSettings(AccessToken token) throws ServerFault {
		Multimap<String, Parameter> params = initParams(token);
		Document doc = execute(token, "/setting/getVacationSettings", params);
		exceptionFactory.checkServerFaultException(doc);
		return respParser.parseVacation(doc);
	}
	
	@Override
	protected Locator getLocator() {
		return locator;
	}
	
}
