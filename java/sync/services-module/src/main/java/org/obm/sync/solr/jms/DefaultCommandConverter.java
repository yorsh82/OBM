/* ***** BEGIN LICENSE BLOCK *****
 * Copyright (C) 2011-2012  Linagora
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
package org.obm.sync.solr.jms;

import java.io.Serializable;
import java.util.EnumMap;

import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.obm.locator.store.LocatorService;
import org.obm.sync.solr.ContactIndexer;
import org.obm.sync.solr.EventIndexer;
import org.obm.sync.solr.IndexerFactory;
import org.obm.sync.solr.SolrRequest;
import org.obm.sync.solr.SolrService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class DefaultCommandConverter implements CommandConverter {
	private final LocatorService locatorService;
	private final EnumMap<SolrService, IndexerFactory<? extends Serializable>> solrServiceToFactoryMap;
	
	@Inject
	@VisibleForTesting
	public DefaultCommandConverter(LocatorService locatorService, ContactIndexer.Factory contactIndexerFactory, EventIndexer.Factory eventIndexerFactory) {
		this.locatorService = locatorService;
		
		solrServiceToFactoryMap = new EnumMap<SolrService, IndexerFactory<? extends Serializable>>(SolrService.class);
		solrServiceToFactoryMap.put(SolrService.EVENT_SERVICE, eventIndexerFactory);
		solrServiceToFactoryMap.put(SolrService.CONTACT_SERVICE, contactIndexerFactory);
	}

	@Override
	public <T extends Serializable> SolrRequest convert(Command<T> command) throws Exception {
		SolrService solrService = command.getSolrService();
		IndexerFactory<T> factory = (IndexerFactory<T>) solrServiceToFactoryMap.get(solrService);
		
		if (factory == null) {
			throw new IllegalArgumentException("Unknown SolR service '" + solrService + "'.");
		}
		
		String solrLocation = locatorService.getServiceLocation(solrService.getName(), command.getDomain().getName());
		CommonsHttpSolrServer server = new CommonsHttpSolrServer("http://" + solrLocation + ":8080/" + solrService.getName());
		
		return command.asSolrRequest(server, factory);
	}
	
}