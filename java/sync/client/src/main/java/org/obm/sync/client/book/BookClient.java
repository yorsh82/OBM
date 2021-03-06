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
package org.obm.sync.client.book;

import java.util.Date;
import java.util.List;

import javax.naming.NoPermissionException;

import org.apache.http.client.HttpClient;
import org.obm.breakdownduration.bean.Watch;
import org.obm.configuration.module.LoggerModule;
import org.obm.push.utils.DOMUtils;
import org.obm.sync.BreakdownGroups;
import org.obm.sync.DateParameter;
import org.obm.sync.IntegerParameter;
import org.obm.sync.Parameter;
import org.obm.sync.StringParameter;
import org.obm.sync.auth.AccessToken;
import org.obm.sync.auth.ServerFault;
import org.obm.sync.book.AddressBook;
import org.obm.sync.book.BookItemsParser;
import org.obm.sync.book.BookItemsWriter;
import org.obm.sync.book.BookType;
import org.obm.sync.book.Contact;
import org.obm.sync.client.impl.AbstractClientImpl;
import org.obm.sync.client.impl.SyncClientAssert;
import org.obm.sync.exception.ContactNotFoundException;
import org.obm.sync.items.AddressBookChangesResponse;
import org.obm.sync.items.ContactChanges;
import org.obm.sync.items.FolderChanges;
import org.obm.sync.locators.Locator;
import org.obm.sync.services.IAddressBook;
import org.slf4j.Logger;
import org.w3c.dom.Document;

import com.google.common.base.Preconditions;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

@Watch(BreakdownGroups.CLIENT_CONTACTS)
public class BookClient extends AbstractClientImpl implements IAddressBook {

	@Singleton
	public static class Factory {

		private final BookItemsParser bookItemsParser;
		private final BookItemsWriter bookItemsWriter;
		private final Locator locator;
		private final SyncClientAssert syncClientAssert;
		private final Logger obmSyncLogger;
		
		@Inject
		protected Factory(SyncClientAssert syncClientAssert, Locator locator, @Named(LoggerModule.OBM_SYNC)Logger obmSyncLogger) {
			this.syncClientAssert = syncClientAssert;
			this.locator = locator;
			this.obmSyncLogger = obmSyncLogger;
			this.bookItemsParser = new BookItemsParser();
			this.bookItemsWriter = new BookItemsWriter();
		}
		
		public BookClient create(HttpClient httpClient) {
			return new BookClient(syncClientAssert, locator, obmSyncLogger, bookItemsParser, bookItemsWriter, httpClient);
		}
		
	}
	
	private final BookItemsParser bookItemsParser;
	private final BookItemsWriter bookItemsWriter;
	private final Locator locator;

	private BookClient(SyncClientAssert syncClientException,
			Locator locator,
			@Named(LoggerModule.OBM_SYNC)Logger obmSyncLogger,
			BookItemsParser bookItemsParser,
			BookItemsWriter bookItemsWriter,
			HttpClient httpClient) {
		
		super(syncClientException, obmSyncLogger, httpClient);
		this.locator = locator;
		this.bookItemsParser = bookItemsParser;
		this.bookItemsWriter = bookItemsWriter;
	}

	@Override
	public Contact createContact(AccessToken token, Integer addressBookId, Contact contact, String clientId) 
			throws ServerFault, NoPermissionException {
		
		Multimap<String, Parameter> params = initParams(token);
		params.put("bookId", new IntegerParameter(addressBookId));
		params.put("contact", new StringParameter(bookItemsWriter.getContactAsString(contact)));
		params.put("clientId", new StringParameter(clientId));
		Document doc = execute(token, "/book/createContact", params);
		exceptionFactory.checkCreateContactException(doc);
		return bookItemsParser.parseContact(doc.getDocumentElement());
	}

	@Override
	public Contact getContactFromId(AccessToken token, Integer addressBookId, Integer contactId) 
			throws ServerFault, ContactNotFoundException {
		
		Multimap<String, Parameter> params = initParams(token);
		params.put("bookId", new IntegerParameter(addressBookId));
		params.put("id", new IntegerParameter(contactId));
		Document doc = execute(token, "/book/getContactFromId", params);
		exceptionFactory.checkContactNotFoundException(doc);
		return bookItemsParser.parseContact(doc.getDocumentElement());
	}

	@Override
	public ContactChanges listContactsChanged(AccessToken token, Date lastSync) throws ServerFault {
		Multimap<String, Parameter> params = initParams(token);
		params.put("lastSync", new DateParameter(lastSync));
		Document doc = execute(token, "/book/listAllChanges", params);
		exceptionFactory.checkServerFaultException(doc);
		return bookItemsParser.parseChanges(doc);
	}

	@Override
	public boolean isReadOnly(AccessToken token, BookType book) throws ServerFault {
		Multimap<String, Parameter> params = initParams(token);
		params.put("book", new StringParameter(book.toString()));
		Document doc = execute(token, "/book/isReadOnly", params);
		exceptionFactory.checkServerFaultException(doc);
		return "true".equals(bookItemsParser.parseArrayOfString(doc)[0]);
	}

	@Override
	public BookType[] listBooks(AccessToken token) throws ServerFault {
		Multimap<String, Parameter> params = initParams(token);
		Document doc = execute(token, "/book/listBooks", params);
		exceptionFactory.checkServerFaultException(doc);
		String[] sa = bookItemsParser.parseArrayOfString(doc);
		BookType[] bts = new BookType[sa.length];
		for (int i = 0; i < sa.length; i++) {
			bts[i] = BookType.valueOf(sa[i]);
		}
		return bts;
	}

	@Override
	public List<AddressBook> listAllBooks(AccessToken token) throws ServerFault {
		Multimap<String, Parameter> params = initParams(token);
		Document doc = execute(token, "/book/listAllBooks", params);
		exceptionFactory.checkServerFaultException(doc);
		List<AddressBook> addressBooks = bookItemsParser.parseListAddressBook(doc);
		return addressBooks;
	}
	
	@Override
	public Contact modifyContact(AccessToken token, Integer addressBookId, Contact contact)
			throws ServerFault, NoPermissionException, ContactNotFoundException {

		Multimap<String, Parameter> params = initParams(token);
		params.put("bookId", new IntegerParameter(addressBookId));
		params.put("contact", new StringParameter(bookItemsWriter.getContactAsString(contact)));
		Document doc = execute(token, "/book/modifyContact", params);
		exceptionFactory.checkModifyContactException(doc);
		return bookItemsParser.parseContact(doc.getDocumentElement());
	}

	@Override
	public Contact removeContact(AccessToken token, Integer addressBookId , Integer contactId) 
			throws ServerFault, ContactNotFoundException, NoPermissionException {
		
		Multimap<String, Parameter> params = initParams(token);
		params.put("bookId", new IntegerParameter(addressBookId));
		params.put("id", new IntegerParameter(contactId));
		Document doc = execute(token, "/book/removeContact", params);
		exceptionFactory.checkRemoveContactException(doc);
		return bookItemsParser.parseContact(doc.getDocumentElement());
	}

	@Override
	public List<Contact> searchContact(AccessToken token, String query, int limit, Integer offset) throws ServerFault {
		Preconditions.checkNotNull(offset);
		Multimap<String, Parameter> params = initParams(token);
		params.put("query", new StringParameter(query));
		params.put("limit", new IntegerParameter(limit));
		Document doc = execute(token, "/book/searchContact", params);
		exceptionFactory.checkServerFaultException(doc);
		return bookItemsParser.parseListContact(doc.getDocumentElement());
	}

	@Override
	public List<Contact> searchContactInGroup(AccessToken token, AddressBook group, String query, int limit, Integer offset) throws ServerFault {
		Multimap<String, Parameter> params = initParams(token);
		params.put("query", new StringParameter(query));
		params.put("limit", new IntegerParameter(limit));
		params.put("offset", new IntegerParameter(offset));
		params.put("group", new IntegerParameter(group.getUid().getId()));
		Document doc = execute(token, "/book/searchContactInGroup", params);
		exceptionFactory.checkServerFaultException(doc);
		return bookItemsParser.parseListContact(doc.getDocumentElement());
	}
	
	@Override
	public int countContactsInGroup(AccessToken token, int gid) throws ServerFault {
		Multimap<String, Parameter> params = initParams(token);
	    params.put("group", new IntegerParameter(gid));
	    Document doc = execute(token, "/book/countContactsInGroup", params);
	    exceptionFactory.checkServerFaultException(doc);
	    return bookItemsParser.parseCountContactsInGroup(doc.getDocumentElement());
	}

	
	@Override
	public AddressBookChangesResponse getAddressBookSync(AccessToken token, Date lastSync) throws ServerFault {
		Multimap<String, Parameter> params = initParams(token);
		if (lastSync != null) {
			params.put("lastSync", new DateParameter(lastSync));
		} else {
			params.put("lastSync", new StringParameter("0"));
		}

		Document doc = execute(token, "/book/getAddressBookSync", params);
		exceptionFactory.checkServerFaultException(doc);
		return bookItemsParser.parseAddressBookChanges(doc);
	}
	
	@Override
	public boolean unsubscribeBook(AccessToken token, Integer addressBookId) throws ServerFault {
		Multimap<String, Parameter> params = initParams(token);
		params.put("bookId", new IntegerParameter(addressBookId));
		Document doc = execute(token, "/book/unsubscribeBook", params);
		exceptionFactory.checkServerFaultException(doc);
		return "true".equalsIgnoreCase(DOMUtils.getElementText(doc
				.getDocumentElement(), "value"));
	}

	@Override
	public FolderChanges listAddressBooksChanged(AccessToken token, Date lastSync) throws ServerFault {
		Multimap<String, Parameter> params = initParams(token);
		params.put("lastSync", new DateParameter(lastSync));
		Document doc = execute(token, "/book/listAddressBooksChanged", params);
		exceptionFactory.checkServerFaultException(doc);
		return bookItemsParser.parseFolderChangesResponse(doc);
	}
	
	@Override
	public ContactChanges listContactsChanged(AccessToken token, Date lastSync, Integer addressBookId) throws ServerFault {
		Multimap<String, Parameter> params = initParams(token);
		params.put("lastSync", new DateParameter(lastSync));
		params.put("bookId", new IntegerParameter(addressBookId));
		Document doc = execute(token, "/book/listAllChanges", params);
		exceptionFactory.checkServerFaultException(doc);
		return bookItemsParser.parseChanges(doc);
	}

	@Override
	public ContactChanges firstListContactsChanged(AccessToken token, Date lastSync) throws ServerFault {
		Multimap<String, Parameter> params = initParams(token);
		params.put("lastSync", new DateParameter(lastSync));
		Document doc = execute(token, "/book/firstListAllChanges", params);
		exceptionFactory.checkServerFaultException(doc);
		return bookItemsParser.parseChanges(doc);
	}
	
	@Override
	public ContactChanges firstListContactsChanged(AccessToken token, Date lastSync, Integer addressBookId) throws ServerFault {
		Multimap<String, Parameter> params = initParams(token);
		params.put("lastSync", new DateParameter(lastSync));
		params.put("bookId", new IntegerParameter(addressBookId));
		Document doc = execute(token, "/book/firstListAllChanges", params);
		exceptionFactory.checkServerFaultException(doc);
		return bookItemsParser.parseChanges(doc);
	}

	@Override
	public List<Contact> searchContactsInSynchronizedAddressBooks(AccessToken token, String query, int limit, Integer offset) throws ServerFault {
		Multimap<String, Parameter> params = initParams(token);
		params.put("query", new StringParameter(query));
		params.put("limit", new IntegerParameter(limit));
		if( offset != null ) {
			params.put("offset", new IntegerParameter(offset));
		}
		Document doc = execute(token, "/book/searchContactsInSynchronizedAddressBooks", params);
		exceptionFactory.checkServerFaultException(doc);
		return bookItemsParser.parseListContact(doc.getDocumentElement());
	}
	
	@Override
	protected Locator getLocator() {
		return locator;
	}

	@Override
	public Contact storeContact(AccessToken token, Integer addressBookId, Contact contact, String clientId)
			throws NoPermissionException, ServerFault, ContactNotFoundException {
		Multimap<String, Parameter> params = initParams(token);
		params.put("bookId", new IntegerParameter(addressBookId));
		params.put("contact", new StringParameter(bookItemsWriter.getContactAsString(contact)));
		Document doc = execute(token, "/book/storeContact", params);
		exceptionFactory.checkModifyContactException(doc);
		return bookItemsParser.parseContact(doc.getDocumentElement());
	}
	
}