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
package org.obm.push.calendar;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createControl;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.fest.assertions.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.obm.filter.SlowFilterRunner;
import org.obm.push.backend.CollectionPath;
import org.obm.push.backend.CollectionPath.Builder;
import org.obm.push.backend.DataDelta;
import org.obm.push.bean.AttendeeStatus;
import org.obm.push.bean.BodyPreference;
import org.obm.push.bean.Credentials;
import org.obm.push.bean.Device;
import org.obm.push.bean.DeviceId;
import org.obm.push.bean.FilterType;
import org.obm.push.bean.FolderSyncState;
import org.obm.push.bean.FolderType;
import org.obm.push.bean.IApplicationData;
import org.obm.push.bean.ItemSyncState;
import org.obm.push.bean.MSEmail;
import org.obm.push.bean.MSEvent;
import org.obm.push.bean.MSEventUid;
import org.obm.push.bean.MSMessageClass;
import org.obm.push.bean.PIMDataType;
import org.obm.push.bean.SyncCollectionOptions;
import org.obm.push.bean.SyncKey;
import org.obm.push.bean.User;
import org.obm.push.bean.User.Factory;
import org.obm.push.bean.UserDataRequest;
import org.obm.push.bean.change.hierarchy.CollectionChange;
import org.obm.push.bean.change.hierarchy.CollectionDeletion;
import org.obm.push.bean.change.hierarchy.HierarchyCollectionChanges;
import org.obm.push.bean.change.item.ItemChange;
import org.obm.push.bean.change.item.ItemDeletion;
import org.obm.push.exception.ConversionException;
import org.obm.push.exception.DaoException;
import org.obm.push.exception.activesync.CollectionNotFoundException;
import org.obm.push.exception.activesync.HierarchyChangedException;
import org.obm.push.service.EventService;
import org.obm.push.service.impl.MappingService;
import org.obm.push.utils.DateUtils;
import org.obm.sync.NotAllowedException;
import org.obm.sync.auth.AccessToken;
import org.obm.sync.auth.AuthFault;
import org.obm.sync.auth.EventAlreadyExistException;
import org.obm.sync.auth.EventNotFoundException;
import org.obm.sync.auth.ServerFault;
import org.obm.sync.calendar.Attendee;
import org.obm.sync.calendar.CalendarInfo;
import org.obm.sync.calendar.DeletedEvent;
import org.obm.sync.calendar.Event;
import org.obm.sync.calendar.EventExtId;
import org.obm.sync.calendar.EventObmId;
import org.obm.sync.calendar.Participation;
import org.obm.sync.client.calendar.CalendarClient;
import org.obm.sync.client.calendar.ConsistencyEventChangesLogger;
import org.obm.sync.client.login.LoginService;
import org.obm.sync.items.EventChanges;
import org.obm.sync.items.ParticipationChanges;
import org.slf4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Provider;

@RunWith(SlowFilterRunner.class)
public class CalendarBackendTest {

	private User user;
	private Device device;
	private UserDataRequest userDataRequest;
	private AccessToken token;
	private FolderSyncState lastKnownState;
	private FolderSyncState outgoingSyncState;
	private String rootCalendarPath;
	private CalendarCollectionPath userCalendarCollectionPath;
	
	private MappingService mappingService;
	private CalendarClient calendarClient;
	private EventConverter eventConverter;
	private EventService eventService;
	private LoginService loginService;
	private Provider<CollectionPath.Builder> collectionPathBuilderProvider;
	private ConsistencyEventChangesLogger consistencyLogger;
	
	private CalendarBackend calendarBackend;
	private IMocksControl mockControl;
	private Builder collectionPathBuilder;
	
	private static class CalendarCollectionPath extends CollectionPath {

		public CalendarCollectionPath(String rootCalendarPath, String displayName) {
			super(String.format("%s%s", rootCalendarPath, displayName), 
					PIMDataType.CALENDAR, displayName);
		}
	}
	
	@Before
	public void setUp() {
		this.user = Factory.create().createUser("test@test", "test@domain", "displayName");
		this.device = new Device.Factory().create(null, "iPhone", "iOs 5", new DeviceId("my phone"), null);
		this.userDataRequest = new UserDataRequest(new Credentials(user, "password"), "noCommand", device);
		this.token = new AccessToken(0, "OBM");
		this.lastKnownState = buildFolderSyncState(new SyncKey("1234567890a"));
		this.outgoingSyncState = buildFolderSyncState(new SyncKey("1234567890b"));
		this.rootCalendarPath = "obm:\\\\test@test\\calendar\\";
		this.userCalendarCollectionPath = new CalendarCollectionPath(rootCalendarPath, "test@test");

		mockControl = createControl();
		this.mappingService = mockControl.createMock(MappingService.class);
		this.calendarClient = mockControl.createMock(CalendarClient.class);
		this.eventConverter = mockControl.createMock(EventConverter.class);
		this.eventService = mockControl.createMock(EventService.class);
		this.loginService = mockControl.createMock(LoginService.class);
		this.collectionPathBuilderProvider = mockControl.createMock(Provider.class);
		this.collectionPathBuilder = mockControl.createMock(CollectionPath.Builder.class);
		expect(collectionPathBuilderProvider.get()).andReturn(collectionPathBuilder).anyTimes();
		this.consistencyLogger = mockControl.createMock(ConsistencyEventChangesLogger.class);
		
		consistencyLogger.log(anyObject(Logger.class), anyObject(EventChanges.class));
		expectLastCall().anyTimes();
		
		this.calendarBackend = new CalendarBackend(mappingService, 
				calendarClient, 
				eventConverter, 
				eventService, 
				loginService, 
				collectionPathBuilderProvider, consistencyLogger);
	}
	
	@Test
	public void testGetPIMDataType() {
		assertThat(calendarBackend.getPIMDataType()).isEqualTo(PIMDataType.CALENDAR);
	}
	
	@Test
	public void testInitialCalendarChanges() throws Exception {
		String calendarDisplayName = userDataRequest.getUser().getLoginAtDomain();
		String defaultCalendarName = rootCalendarPath + calendarDisplayName;
		
		int collectionMappingId = 1;
		List<CollectionPath> knownCollections = ImmutableList.of(); 
		expectMappingServiceListLastKnowCollection(lastKnownState, knownCollections);
		expectMappingServiceSearchThenCreateCollection(defaultCalendarName, collectionMappingId);
		expectMappingServiceSnapshot(outgoingSyncState, ImmutableSet.of(collectionMappingId));
		expectMappingServiceLookupCollection(defaultCalendarName, collectionMappingId);
		
		expectBuildCollectionPath(calendarDisplayName);

		mockControl.replay();
		
		HierarchyCollectionChanges hierarchyItemsChanges = calendarBackend.getHierarchyChanges(userDataRequest, lastKnownState, outgoingSyncState);
		
		mockControl.verify();
		
		CollectionChange expectedItemChange = CollectionChange.builder()
				.collectionId(String.valueOf(collectionMappingId))
				.parentCollectionId("0")
				.displayName(calendarDisplayName + " calendar")
				.folderType(FolderType.DEFAULT_CALENDAR_FOLDER)
				.isNew(true)
				.build();
		assertThat(hierarchyItemsChanges.getCollectionChanges()).hasSize(1);
		assertThat(hierarchyItemsChanges.getCollectionChanges()).containsOnly(expectedItemChange);
		assertThat(hierarchyItemsChanges.getCollectionDeletions()).isEmpty();
	}
	
	@Test
	public void testNoCalendarChanges() throws Exception {
		String calendarDisplayName = userDataRequest.getUser().getLoginAtDomain();
		String defaultCalendarName = rootCalendarPath + calendarDisplayName;
		
		int collectionMappingId = 1;
		List<CollectionPath> knownCollections = ImmutableList.<CollectionPath>of(
				new CollectionPathTest(defaultCalendarName, PIMDataType.CALENDAR, calendarDisplayName));
		expectMappingServiceListLastKnowCollection(lastKnownState, knownCollections);
		expectMappingServiceFindCollection(defaultCalendarName, collectionMappingId);
		expectMappingServiceSnapshot(outgoingSyncState, ImmutableSet.of(collectionMappingId));
		
		expectBuildCollectionPath(calendarDisplayName);

		mockControl.replay();
		
		HierarchyCollectionChanges hierarchyItemsChanges = calendarBackend.getHierarchyChanges(userDataRequest, lastKnownState, outgoingSyncState);
		
		mockControl.verify();
		
		assertThat(hierarchyItemsChanges.getCollectionChanges()).isEmpty();
		assertThat(hierarchyItemsChanges.getCollectionDeletions()).isEmpty();
	}

	@Test
	public void testInitialCalendarChangesWhenMultipleCalendar() throws Exception {
		FolderSyncState lastKnownState = buildFolderSyncState(new SyncKey("1234567890a"));
		FolderSyncState outgoingSyncState = buildFolderSyncState(new SyncKey("1234567890b"));

		device = new Device.Factory().create(null, "MultipleCalendarsDevice", "iOs 5", new DeviceId("my phone"), null);
		userDataRequest = new UserDataRequest(new Credentials(user, "password"), "noCommand", device);
		
		int calendar1MappingId = 1;
		String calendar1DisplayName = "test@test";
		String calendar1CollectionPath = rootCalendarPath + calendar1DisplayName;
		int calendar2MappingId = 2;
		String calendar2DisplayName = "alias@test";
		String calendar2CollectionPath = rootCalendarPath + calendar2DisplayName;

		expectLoginBehavior();
		expectObmSyncCalendarChanges(
				newCalendarInfo("1", calendar1DisplayName),
				newCalendarInfo("2", calendar2DisplayName));
		
		expectBuildCollectionPath(calendar1DisplayName);
		expectBuildCollectionPath(calendar2DisplayName);
		
		List<CollectionPath> knownCollections = ImmutableList.of(); 
		expectMappingServiceListLastKnowCollection(lastKnownState, knownCollections);
		expectMappingServiceSearchThenCreateCollection(calendar1CollectionPath, calendar1MappingId);
		expectMappingServiceSearchThenCreateCollection(calendar2CollectionPath, calendar2MappingId);
		expectMappingServiceSnapshot(outgoingSyncState, ImmutableSet.of(calendar1MappingId, calendar2MappingId));
		expectMappingServiceLookupCollection(calendar1CollectionPath, calendar1MappingId);
		expectMappingServiceLookupCollection(calendar2CollectionPath, calendar2MappingId);
		
		mockControl.replay();
		
		HierarchyCollectionChanges hierarchyItemsChanges = calendarBackend.getHierarchyChanges(userDataRequest, lastKnownState, outgoingSyncState);
		
		mockControl.verify();

		CollectionChange expectedItemChange1 = CollectionChange.builder()
				.collectionId(String.valueOf(calendar1MappingId))
				.parentCollectionId("0")
				.displayName(calendar1DisplayName + " calendar")
				.folderType(FolderType.DEFAULT_CALENDAR_FOLDER)
				.isNew(true)
				.build();
		CollectionChange expectedItemChange2 = CollectionChange.builder()
				.collectionId(String.valueOf(calendar2MappingId))
				.parentCollectionId("0")
				.displayName(calendar2DisplayName + " calendar")
				.folderType(FolderType.USER_CREATED_CALENDAR_FOLDER)
				.isNew(true)
				.build();
		
		assertThat(hierarchyItemsChanges.getCollectionChanges()).hasSize(2);
		assertThat(hierarchyItemsChanges.getCollectionChanges()).containsOnly(expectedItemChange1, expectedItemChange2);
		assertThat(hierarchyItemsChanges.getCollectionDeletions()).isEmpty();
	}

	@Test
	public void testDeletionWhenMultipleCalendar() throws Exception {
		FolderSyncState lastKnownState = buildFolderSyncState(new SyncKey("1234567890a"));
		FolderSyncState outgoingSyncState = buildFolderSyncState(new SyncKey("1234567890b"));

		device = new Device.Factory().create(null, "MultipleCalendarsDevice", "iOs 5", new DeviceId("my phone"), null);
		userDataRequest = new UserDataRequest(new Credentials(user, "password"), "noCommand", device);
		
		int calendar1MappingId = 1;
		String calendar1DisplayName = "added@test";
		String calendar1CollectionPath = rootCalendarPath + calendar1DisplayName;
		int calendar2MappingId = 2;
		String calendar2DisplayName = "deleted@test";
		String calendar2CollectionPath = rootCalendarPath + calendar2DisplayName;

		expectLoginBehavior();
		expectObmSyncCalendarChanges(newCalendarInfo("1", calendar1DisplayName));
		
		expectBuildCollectionPath(calendar1DisplayName);

		List<CollectionPath> knownCollections = ImmutableList.<CollectionPath>of(
				new CollectionPathTest(calendar2CollectionPath, PIMDataType.CALENDAR, calendar2DisplayName));
		expectMappingServiceListLastKnowCollection(lastKnownState, knownCollections);
		expectMappingServiceSearchThenCreateCollection(calendar1CollectionPath, calendar1MappingId);
		expectMappingServiceSnapshot(outgoingSyncState, ImmutableSet.of(calendar1MappingId));
		expectMappingServiceLookupCollection(calendar1CollectionPath, calendar1MappingId);
		expectMappingServiceLookupCollection(calendar2CollectionPath, calendar2MappingId);
		
		mockControl.replay();
		
		HierarchyCollectionChanges hierarchyItemsChanges = calendarBackend.getHierarchyChanges(userDataRequest, lastKnownState, outgoingSyncState);
		
		mockControl.verify();

		CollectionChange expectedItemChange1 = CollectionChange.builder()
				.collectionId(String.valueOf(calendar1MappingId))
				.parentCollectionId("0")
				.displayName(calendar1DisplayName + " calendar")
				.folderType(FolderType.USER_CREATED_CALENDAR_FOLDER)
				.isNew(true)
				.build();
		CollectionDeletion expectedItemChange2 = CollectionDeletion.builder()
				.collectionId(String.valueOf(calendar2MappingId))
				.build();
		
		assertThat(hierarchyItemsChanges.getCollectionChanges()).hasSize(1);
		assertThat(hierarchyItemsChanges.getCollectionChanges()).containsOnly(expectedItemChange1);
		assertThat(hierarchyItemsChanges.getCollectionDeletions()).hasSize(1);
		assertThat(hierarchyItemsChanges.getCollectionDeletions()).containsOnly(expectedItemChange2);
	}

	@Test
	public void collectionDisplayNameForCalendar() throws Exception {
		int calendarMappingId = 1;
		String calendarBackendName = "test@test";
		String calendarDisplayName = calendarBackendName + " calendar";
		String calendarCollectionPath = rootCalendarPath + calendarBackendName;

		expectBuildCollectionPath(calendarBackendName);

		expectMappingServiceListLastKnowCollection(lastKnownState, ImmutableList.<CollectionPath>of());
		expectMappingServiceSearchThenCreateCollection(calendarCollectionPath, calendarMappingId);
		expectMappingServiceSnapshot(outgoingSyncState, ImmutableSet.of(calendarMappingId));
		expectMappingServiceLookupCollection(calendarCollectionPath, calendarMappingId);
		
		mockControl.replay();
		HierarchyCollectionChanges hierarchyItemsChanges = calendarBackend.getHierarchyChanges(userDataRequest, lastKnownState, outgoingSyncState);
		mockControl.verify();

		assertThat(hierarchyItemsChanges.getCollectionChanges()).containsOnly(CollectionChange.builder()
				.displayName(calendarDisplayName)
				.collectionId("1")
				.parentCollectionId("0")
				.folderType(FolderType.DEFAULT_CALENDAR_FOLDER)
				.isNew(true)
				.build());
	}

	@Test
	public void collectionDisplayNameForMultipleCalendar() throws Exception {
		int calendarMappingId = 1;
		String calendarBackendName = "test@test";
		String calendarDisplayName = calendarBackendName + " calendar";
		String calendarCollectionPath = rootCalendarPath + calendarBackendName;

		int calendar2MappingId = 2;
		String calendar2BackendName = "test2@test";
		String calendar2DisplayName = calendar2BackendName + " calendar";
		String calendar2CollectionPath = rootCalendarPath + calendar2BackendName;

		device = new Device.Factory().create(null, "MultipleCalendarsDevice", "iOs 5", new DeviceId("my phone"), null);
		userDataRequest = new UserDataRequest(new Credentials(user, "password"), "noCommand", device);
		
		expectLoginBehavior();
		expectObmSyncCalendarChanges(
				newCalendarInfo("1", calendarBackendName),
				newCalendarInfo("2", calendar2BackendName));

		expectBuildCollectionPath(calendarBackendName);
		expectBuildCollectionPath(calendar2BackendName);

		expectMappingServiceListLastKnowCollection(lastKnownState, ImmutableList.<CollectionPath>of());
		expectMappingServiceSearchThenCreateCollection(calendarCollectionPath, calendarMappingId);
		expectMappingServiceSearchThenCreateCollection(calendar2CollectionPath, calendar2MappingId);
		expectMappingServiceSnapshot(outgoingSyncState, ImmutableSet.of(calendarMappingId, calendar2MappingId));
		expectMappingServiceLookupCollection(calendarCollectionPath, calendarMappingId);
		expectMappingServiceLookupCollection(calendar2CollectionPath, calendar2MappingId);
		
		mockControl.replay();

		HierarchyCollectionChanges hierarchyItemsChanges = calendarBackend.getHierarchyChanges(userDataRequest, lastKnownState, outgoingSyncState);
		
		mockControl.verify();
		assertThat(hierarchyItemsChanges.getCollectionChanges()).containsOnly(
				CollectionChange.builder()
					.displayName(calendarDisplayName)
					.collectionId("1")
					.parentCollectionId("0")
					.folderType(FolderType.DEFAULT_CALENDAR_FOLDER)
					.isNew(true)
					.build(),
				CollectionChange.builder()
					.displayName(calendar2DisplayName)
					.collectionId("2")
					.parentCollectionId("0")
					.folderType(FolderType.USER_CREATED_CALENDAR_FOLDER)
					.isNew(true)
					.build()
				);
	}

	private void expectBuildCollectionPath(String displayName) {
		CollectionPath collectionPath = new CalendarCollectionPath(rootCalendarPath, displayName);
		
		expect(collectionPathBuilder.userDataRequest(userDataRequest))
			.andReturn(collectionPathBuilder).once();
		
		expect(collectionPathBuilder.pimType(PIMDataType.CALENDAR))
			.andReturn(collectionPathBuilder).once();
		
		expect(collectionPathBuilder.backendName(displayName))
			.andReturn(collectionPathBuilder).once();
		
		expect(collectionPathBuilder.build()).andReturn(collectionPath).once();
	}

	private void expectObmSyncCalendarChanges(CalendarInfo...calendarInfos) throws ServerFault {
		expect(calendarClient.listCalendars(token))
			.andReturn(calendarInfos).once();
	}

	private CalendarInfo newCalendarInfo(String uid, String mail) {
		CalendarInfo calendarInfo = new CalendarInfo();
		calendarInfo.setUid(uid);
		calendarInfo.setMail(mail);
		return calendarInfo;
	}

	private void expectMappingServiceSnapshot(FolderSyncState outgoingSyncState, Set<Integer> collectionIds)
			throws DaoException {

		mappingService.snapshotCollections(outgoingSyncState, collectionIds);
		expectLastCall();
	}

	private void expectMappingServiceListLastKnowCollection(FolderSyncState incomingSyncState,
			List<CollectionPath> collectionPaths) throws DaoException {
		
		expect(mappingService.listCollections(userDataRequest, incomingSyncState))
			.andReturn(collectionPaths).once();
	}

	private void expectMappingServiceFindCollection(String collectionPath, Integer collectionId)
		throws CollectionNotFoundException, DaoException {
		
		expect(mappingService.getCollectionIdFor(device, collectionPath))
			.andReturn(collectionId).once();
	}
	
	private void expectMappingServiceSearchThenCreateCollection(String collectionPath, Integer collectionId)
		throws CollectionNotFoundException, DaoException {
		
		expect(mappingService.getCollectionIdFor(device, collectionPath))
			.andThrow(new CollectionNotFoundException()).once();
		
		expect(mappingService.createCollectionMapping(device, collectionPath))
			.andReturn(collectionId).once();
	}
	
	private void expectMappingServiceLookupCollection(String collectionPath, Integer collectionId)
		throws CollectionNotFoundException, DaoException {
		
		expectMappingServiceFindCollection(collectionPath, collectionId);
		expect(mappingService.collectionIdToString(collectionId))
			.andReturn(String.valueOf(collectionId)).once();
	}

	@Test
	public void testGetEstimateSize() throws Exception {
		Date currentDate = DateUtils.getCurrentDate();
		ItemSyncState lastKnownState = ItemSyncState.builder()
				.syncDate(currentDate)
				.syncKey(new SyncKey("1234567890a"))
				.build();
		int collectionId = 1;

		expect(mappingService.getCollectionPathFor(collectionId)).andReturn(userCalendarCollectionPath.collectionPath());
		expect(collectionPathBuilder.fullyQualifiedCollectionPath(userCalendarCollectionPath.collectionPath())).andReturn(collectionPathBuilder);
		expect(collectionPathBuilder.build()).andReturn(userCalendarCollectionPath);
		
		expectLoginBehavior();

		
		EventChanges eventChanges = expectTwoDeletedAndTwoUpdatedEventChanges(currentDate);
		
		expect(calendarClient.getSync(token, "test@test", currentDate))
			.andReturn(eventChanges).once();
		
		expect(calendarClient.getUserEmail(token))
			.andReturn("test@test").anyTimes();

		expectConvertUpdatedEventsToMSEvents(eventChanges);
		
		mockControl.replay();
		
		BodyPreference.Builder bodyPreferenceBuilder = BodyPreference.builder();
		BodyPreference bodyPreference = bodyPreferenceBuilder.build();
		SyncCollectionOptions syncCollectionOptions = new SyncCollectionOptions(ImmutableList.<BodyPreference> of(bodyPreference));
		syncCollectionOptions.setFilterType(FilterType.ALL_ITEMS);
		
		int itemEstimateSize = calendarBackend.getItemEstimateSize(userDataRequest, lastKnownState, collectionId, syncCollectionOptions);
		
		mockControl.verify();
		
		assertThat(itemEstimateSize).isEqualTo(4);
	}
	
	@Test 
	public void testGetChanged() throws Exception {
		Date currentDate = DateUtils.getCurrentDate();
		SyncKey syncKey = new SyncKey("1234567890a");
		ItemSyncState lastKnownState = ItemSyncState.builder()
				.syncDate(currentDate)
				.syncKey(syncKey)
				.build();

		int collectionId = 1;

		expect(mappingService.getCollectionPathFor(collectionId)).andReturn(userCalendarCollectionPath.collectionPath());
		expect(collectionPathBuilder.fullyQualifiedCollectionPath(userCalendarCollectionPath.collectionPath())).andReturn(collectionPathBuilder);
		expect(collectionPathBuilder.build()).andReturn(userCalendarCollectionPath);
		
		expectLoginBehavior();

		EventChanges eventChanges = expectTwoDeletedAndTwoUpdatedEventChanges(currentDate);
		
		expect(calendarClient.getSync(token, "test@test", currentDate))
			.andReturn(eventChanges).once();
		
		expect(calendarClient.getUserEmail(token))
			.andReturn("test@test").anyTimes();

		expectConvertUpdatedEventsToMSEvents(eventChanges);
		
		mockControl.replay();
		
		BodyPreference.Builder bodyPreferenceBuilder = BodyPreference.builder();
		BodyPreference bodyPreference = bodyPreferenceBuilder.build();
		SyncCollectionOptions syncCollectionOptions = new SyncCollectionOptions(ImmutableList.<BodyPreference> of(bodyPreference));
		syncCollectionOptions.setFilterType(FilterType.ALL_ITEMS);
		
		DataDelta dataDelta = calendarBackend.getChanged(userDataRequest, lastKnownState, collectionId, syncCollectionOptions, syncKey);
		
		mockControl.verify();
		
		assertThat(dataDelta.getSyncDate()).isEqualTo(currentDate);
		assertThat(dataDelta.getDeletions()).hasSize(2);
		assertThat(dataDelta.getChanges()).hasSize(2);
	}

	private EventChanges expectTwoDeletedAndTwoUpdatedEventChanges(Date currentDate) {
		EventChanges eventChanges = new EventChanges();
		Set<DeletedEvent> deletedEvents = ImmutableSet.of(
				createDeletedEvent(new EventObmId(11), new EventExtId("11")),
				createDeletedEvent(new EventObmId(12), new EventExtId("12")));
		eventChanges.setDeletedEvents(deletedEvents);
		
		eventChanges.setParticipationUpdated(ImmutableList.<ParticipationChanges> of());
		
		List<Event> updated = new ArrayList<Event>();
		updated.add(createEvent(21));
		updated.add(createEvent(22));
		eventChanges.setUpdated(updated);
		
		eventChanges.setLastSync(currentDate);
		
		return eventChanges;
	}

	private Event createEvent(int obmId) {
		Event event = new Event();
		event.setUid(new EventObmId(obmId));
		return event;
	}

	private DeletedEvent createDeletedEvent(EventObmId eventObmId, EventExtId eventExtId) {
		return new DeletedEvent(eventObmId, eventExtId);
	}
	
	private void expectConvertUpdatedEventsToMSEvents(EventChanges eventChanges) throws DaoException, ConversionException {
		for (Event event : eventChanges.getUpdated()) {
			expect(eventService.convertEventToMSEvent(userDataRequest, event))
				.andReturn(null).once();
		}
	}

	@Test
	public void testCreateOrUpdate() throws Exception {
		int collectionId = 1;
		String itemId = "2";
		String clientId = "3";
		IApplicationData data = null;


		expect(mappingService.getCollectionPathFor(collectionId)).andReturn(userCalendarCollectionPath.collectionPath());
		expect(collectionPathBuilder.fullyQualifiedCollectionPath(userCalendarCollectionPath.collectionPath())).andReturn(collectionPathBuilder);
		expect(collectionPathBuilder.build()).andReturn(userCalendarCollectionPath);
		
		expectLoginBehavior();
		
		Event event = new Event();
		expectGetAndModifyEvent(itemId, event);

		expectEventConvertion(event, true);
		
		mockControl.replay();
		
		String serverIdFor = calendarBackend.createOrUpdate(userDataRequest, collectionId, itemId, clientId, data);
		
		mockControl.verify();
		
		assertThat(serverIdFor).isEqualTo(collectionId + ":" + itemId);
	}

	private void expectGetAndModifyEvent(String serverId, Event event) 
			throws ServerFault, EventNotFoundException, NotAllowedException {
		
		expect(calendarClient.getEventFromId(token, "test@test", new EventObmId(serverId)))
			.andReturn(event).once();
		
		expect(calendarClient.modifyEvent(token, "test@test", event, true, true))
			.andReturn(event).once();
	}

	@Test
	public void testDelete() throws Exception {
		int collectionId = 1;
		String serverId = "2";
		int itemId = 3;

		expect(mappingService.getCollectionPathFor(collectionId)).andReturn(userCalendarCollectionPath.collectionPath());
		expect(collectionPathBuilder.fullyQualifiedCollectionPath(userCalendarCollectionPath.collectionPath())).andReturn(collectionPathBuilder);
		expect(collectionPathBuilder.build()).andReturn(userCalendarCollectionPath);
		
		expectLoginBehavior();
		
		expect(mappingService.getItemIdFromServerId(serverId))
			.andReturn(itemId).once();
		
		expectGetAndRemoveEventFromId(itemId);
		
		mockControl.replay();
		
		calendarBackend.delete(userDataRequest, collectionId, serverId, true);
		
		mockControl.verify();
	}

	private void expectGetAndRemoveEventFromId(int itemId)
			throws ServerFault, EventNotFoundException, NotAllowedException {
		
		EventObmId eventObmId = new EventObmId(itemId);
		Event event = new Event();
		event.setUid(eventObmId);
		expect(calendarClient.getEventFromId(token, "test@test", eventObmId))
			.andReturn(event).once();
		
		calendarClient.removeEventById(token, "test@test", event.getObmId(), event.getSequence(), true);
		expectLastCall();
	}
	
	@Test
	public void testHandleMettingResponse() throws Exception {
		String calendarDisplayName = userDataRequest.getUser().getLoginAtDomain();
		String defaultCalendarName = rootCalendarPath + calendarDisplayName;
		
		MSEventUid msEventUid = new MSEventUid("1");
		MSEvent msEvent = new MSEvent();
		msEvent.setUid(msEventUid);
		MSEmail invitation  = new MSEmail();
		invitation.setInvitation(msEvent, MSMessageClass.NOTE);

		expectLoginBehavior();
		
		EventExtId eventExtId = new EventExtId("1");
		expect(eventService.getEventExtIdFor(msEventUid, device))
			.andReturn(eventExtId).once();

		Event event = new Event();
		event.setUid(new EventObmId(1));
		expectGetAndModifyEvent(eventExtId, event);
		expect(calendarClient.changeParticipationState(token, userDataRequest.getUser().getLoginAtDomain(), eventExtId, null, 0, true))
			.andReturn(true);
		
		expectEventConvertion(event, false);
		expect(eventConverter.getParticipation(null, AttendeeStatus.ACCEPT))
			.andReturn(null).once();
		
		expect(mappingService.getCollectionIdFor(device, defaultCalendarName))
			.andReturn(1).once();
		
		expectBuildCollectionPath(calendarDisplayName);
		
		mockControl.replay();

		String serverIdResponse = calendarBackend.handleMeetingResponse(userDataRequest, invitation, AttendeeStatus.ACCEPT);
		
		mockControl.verify();
		assertThat(serverIdResponse).isEqualTo("1:1");
	}
	
	private void expectGetAndModifyEvent(EventExtId eventExtId, Event event) 
			throws ServerFault, EventNotFoundException, NotAllowedException {
		
		expect(calendarClient.getEventFromExtId(token, userDataRequest.getUser().getLoginAtDomain(), eventExtId))
			.andReturn(event).once();
		
		expect(calendarClient.modifyEvent(token, userDataRequest.getUser().getLoginAtDomain(), event, true, false))
			.andReturn(event).once();
	}
	
	private void expectEventConvertion(Event event, boolean defaultValue) throws ConversionException {
		expect(eventConverter.isInternalEvent(event, defaultValue))
			.andReturn(false).once();
		
		expect(eventConverter.convert(eq(userDataRequest.getUser()), eq(event), anyObject(MSEvent.class), eq(false)))
			.andReturn(event).once();
	}
	
	@Test
	public void testFetch() throws Exception {
		String serverId1 = "1:1";
		String serverId2 = "1:2";
		Integer itemId1 = 1;
		Integer itemId2 = 2;
		int collectionId = 1;
		
		expect(mappingService.getCollectionPathFor(collectionId)).andReturn(userCalendarCollectionPath.collectionPath());
		expect(collectionPathBuilder.fullyQualifiedCollectionPath(userCalendarCollectionPath.collectionPath())).andReturn(collectionPathBuilder);
		expect(collectionPathBuilder.build()).andReturn(userCalendarCollectionPath);

		expectLoginBehavior();
		
		expectGetItemIdFromServerId(serverId1, itemId1);
		expectGetItemIdFromServerId(serverId2, itemId2);
		
		Event event1 = expectGetEventFromId(itemId1);
		Event event2 = expectGetEventFromId(itemId2);

		expectConvertEventToMSEvent(serverId1, event1);
		expectConvertEventToMSEvent(serverId2, event2);
		
		mockControl.replay();
		
		List<String> itemIds = ImmutableList.of(serverId1, serverId2);

		List<ItemChange> itemChanges = calendarBackend.fetch(userDataRequest, collectionId, itemIds, null, null, null);
		
		mockControl.verify();
		
		assertThat(itemChanges).hasSize(2);
	}

	private void expectGetItemIdFromServerId(String serverId, Integer itemId) {
		expect(mappingService.getItemIdFromServerId(serverId))
			.andReturn(itemId).once();
	}

	private Event expectGetEventFromId(Integer itemId) 
			throws ServerFault, EventNotFoundException, NotAllowedException {
		
		EventObmId eventObmId = new EventObmId(itemId);
		Event event = new Event();
		event.setUid(eventObmId);
		expect(calendarClient.getEventFromId(token, "test@test", eventObmId))
			.andReturn(event).once();
		
		return event;
	}

	private void expectConvertEventToMSEvent(String serverId, Event event) 
			throws DaoException, ConversionException {
		
		MSEvent msEvent = new MSEvent();
		msEvent.setUid(new MSEventUid(serverId));
		expect(eventService.convertEventToMSEvent(userDataRequest, event))
			.andReturn(msEvent).once();
	}

	private void expectLoginBehavior() throws AuthFault {
		expect(loginService.login(userDataRequest.getUser().getLoginAtDomain(), userDataRequest.getPassword()))
			.andReturn(token).once();
		
		loginService.logout(token);
		expectLastCall().once();
	}
	
	private static class CollectionPathTest extends CollectionPath {

		public CollectionPathTest(String collectionPath, PIMDataType pimType, String displayName) {
			super(collectionPath, pimType, displayName);
		}
		
	}
	
	@Test
	public void buildDeltaDataMustNotTransformDeclinedEventIntoRemoved() throws ServerFault, DaoException, ConversionException {
		int collectionId = 1;
		EventObmId eventObmId = new EventObmId(132453);
		EventExtId eventExtId = new EventExtId("event-ext-id-bla-bla");
		Attendee attendee = new Attendee();
		attendee.setEmail(user.getLoginAtDomain());
		attendee.setParticipation(Participation.declined());
		Event event = new Event();
		event.setExtId(eventExtId);
		event.setUid(eventObmId);
		event.addAttendee(attendee);
		DeletedEvent deletedEvent = createDeletedEvent(eventObmId, eventExtId);
		EventChanges eventChanges = EventChanges.builder()
			.deletes(Arrays.asList(deletedEvent))
			.updates(Arrays.asList(event))
			.lastSync(org.obm.DateUtils.date("2012-10-10"))
			.build();
		
		expect(calendarClient.getUserEmail(token)).andReturn("test@test").anyTimes();
		
		
		mockControl.replay();
		
		DataDelta dataDelta = calendarBackend.buildDataDelta(userDataRequest, collectionId, token, eventChanges);
		
		mockControl.verify();
		assertThat(dataDelta.getDeletions()).hasSize(1).containsOnly(
				ItemDeletion.builder()
				.serverId("1:132453")
				.build());
	}

	private FolderSyncState buildFolderSyncState(SyncKey syncKey) {
		return FolderSyncState.builder()
				.syncKey(syncKey)
				.build();
	}
	
	@Test (expected=HierarchyChangedException.class)
	public void testGetChangedThrowsHierarchyChangedException() throws Exception {
		Date currentDate = DateUtils.getCurrentDate();
		SyncKey syncKey = new SyncKey("1234567890a");
		ItemSyncState lastKnownState = ItemSyncState.builder()
				.syncDate(currentDate)
				.syncKey(syncKey)
				.build();

		int collectionId = 1;

		expect(mappingService.getCollectionPathFor(collectionId)).andReturn(userCalendarCollectionPath.collectionPath());
		expect(collectionPathBuilder.fullyQualifiedCollectionPath(userCalendarCollectionPath.collectionPath())).andReturn(collectionPathBuilder);
		expect(collectionPathBuilder.build()).andReturn(userCalendarCollectionPath);
		
		expectLoginBehavior();
		
		expect(calendarClient.getSync(token, "test@test", currentDate))
			.andThrow(new NotAllowedException("Not Allowed")).once();
		
		mockControl.replay();
		
		BodyPreference.Builder bodyPreferenceBuilder = BodyPreference.builder();
		BodyPreference bodyPreference = bodyPreferenceBuilder.build();
		SyncCollectionOptions syncCollectionOptions = new SyncCollectionOptions(ImmutableList.<BodyPreference> of(bodyPreference));
		syncCollectionOptions.setFilterType(FilterType.ALL_ITEMS);
		
		calendarBackend.getChanged(userDataRequest, lastKnownState, collectionId, syncCollectionOptions, syncKey);
	}
	
	@Test (expected=HierarchyChangedException.class)
	public void testCreateOrUpdateThrowsHierarchyChangedException() throws Exception {
		int collectionId = 1;
		String serverId = "2";
		String clientId = "3";
		IApplicationData data = null;

		expect(mappingService.getCollectionPathFor(collectionId)).andReturn(userCalendarCollectionPath.collectionPath());
		expect(collectionPathBuilder.fullyQualifiedCollectionPath(userCalendarCollectionPath.collectionPath())).andReturn(collectionPathBuilder);
		expect(collectionPathBuilder.build()).andReturn(userCalendarCollectionPath);
		
		expectLoginBehavior();
		
		expect(mappingService.getServerIdFor(collectionId, serverId))
			.andReturn(serverId).once();
		
		expect(calendarClient.getEventFromId(token, userDataRequest.getUser().getLoginAtDomain(), new EventObmId(serverId)))
			.andThrow(new NotAllowedException("Not allowed")).once();

		mockControl.replay();
		
		calendarBackend.createOrUpdate(userDataRequest, collectionId, serverId, clientId, data);
	}
	
	@Test (expected=HierarchyChangedException.class)
	public void testCreateOrUpdateThrowsHierarchyChangedExceptionOnUpdateCalendarEntity() throws Exception {
		int collectionId = 1;
		String serverId = "2";
		String clientId = "3";
		IApplicationData data = null;

		expect(mappingService.getCollectionPathFor(collectionId)).andReturn(userCalendarCollectionPath.collectionPath());
		expect(collectionPathBuilder.fullyQualifiedCollectionPath(userCalendarCollectionPath.collectionPath())).andReturn(collectionPathBuilder);
		expect(collectionPathBuilder.build()).andReturn(userCalendarCollectionPath);
		
		expectLoginBehavior();
		
		expect(mappingService.getServerIdFor(collectionId, serverId))
			.andReturn(serverId).once();
		
		Event event = new Event();
		expect(calendarClient.getEventFromId(token, userDataRequest.getUser().getLoginAtDomain(), new EventObmId(serverId)))
			.andReturn(event).once();
	
		expect(calendarClient.modifyEvent(token, "test@test", event, true, true))
			.andThrow(new NotAllowedException("Not allowed")).once();

		expectEventConvertion(event, true);
		
		mockControl.replay();
		
		calendarBackend.createOrUpdate(userDataRequest, collectionId, serverId, clientId, data);
	}
	
	@Test (expected=HierarchyChangedException.class)
	public void testCreateOrUpdateThrowsHierarchyChangedExceptionOnCreateCalendarEntity() throws Exception {
		int collectionId = 1;
		String serverId = "2";
		String clientId = "3";
		IApplicationData data = new MSEvent();

		expect(mappingService.getCollectionPathFor(collectionId)).andReturn(userCalendarCollectionPath.collectionPath());
		expect(collectionPathBuilder.fullyQualifiedCollectionPath(userCalendarCollectionPath.collectionPath())).andReturn(collectionPathBuilder);
		expect(collectionPathBuilder.build()).andReturn(userCalendarCollectionPath);
		
		expectLoginBehavior();
		
		expect(mappingService.getServerIdFor(collectionId, serverId))
			.andReturn(serverId).once();
		
		Event event = new Event();
		expect(calendarClient.getEventFromId(token, userDataRequest.getUser().getLoginAtDomain(), new EventObmId(serverId)))
			.andReturn(null).once();
	
		expect(calendarClient.createEvent(token, "test@test", event, true))
			.andThrow(new NotAllowedException("Not allowed")).once();

		expect(eventConverter.isInternalEvent(null, true))
			.andReturn(false).once();
		
		expect(eventConverter.convert(eq(userDataRequest.getUser()), EasyMock.anyObject(Event.class), anyObject(MSEvent.class), eq(false)))
			.andReturn(event).once();
		
		eventService.trackEventExtIdMSEventUidTranslation(anyObject(EventExtId.class), anyObject(MSEventUid.class), eq(userDataRequest.getDevice()));
		expectLastCall();
		
		mockControl.replay();
		
		calendarBackend.createOrUpdate(userDataRequest, collectionId, null, clientId, data);
	}
	
	@Test (expected=HierarchyChangedException.class)
	public void testCreateOrUpdateThrowsHierarchyChangedExceptionOnEventAlreadyExist() throws Exception {
		int collectionId = 1;
		String serverId = "2";
		String clientId = "3";
		IApplicationData data = new MSEvent();

		expect(mappingService.getCollectionPathFor(collectionId)).andReturn(userCalendarCollectionPath.collectionPath());
		expect(collectionPathBuilder.fullyQualifiedCollectionPath(userCalendarCollectionPath.collectionPath())).andReturn(collectionPathBuilder);
		expect(collectionPathBuilder.build()).andReturn(userCalendarCollectionPath);
		
		expectLoginBehavior();
		
		expect(mappingService.getServerIdFor(collectionId, serverId))
			.andReturn(serverId).once();
		
		Event event = new Event();
		expect(calendarClient.getEventFromId(token, userDataRequest.getUser().getLoginAtDomain(), new EventObmId(serverId)))
			.andReturn(null).once();
	
		expect(calendarClient.createEvent(token, "test@test", event, true))
			.andThrow(new EventAlreadyExistException("Already exist")).once();

		expect(calendarClient.getEventObmIdFromExtId(eq(token), eq("test@test"), anyObject(EventExtId.class)))
			.andThrow(new NotAllowedException("Not allowed")).once();
		
		expect(eventConverter.isInternalEvent(null, true))
			.andReturn(false).once();
		
		expect(eventConverter.convert(eq(userDataRequest.getUser()), EasyMock.anyObject(Event.class), anyObject(MSEvent.class), eq(false)))
			.andReturn(event).once();
		
		eventService.trackEventExtIdMSEventUidTranslation(anyObject(EventExtId.class), anyObject(MSEventUid.class), eq(userDataRequest.getDevice()));
		expectLastCall();
		
		mockControl.replay();
		
		calendarBackend.createOrUpdate(userDataRequest, collectionId, null, clientId, data);
	}
	
	@Test (expected=HierarchyChangedException.class)
	public void testHandleMettingResponseThrowsHierarchyChangedException() throws Exception {
		String calendarDisplayName = userDataRequest.getUser().getLoginAtDomain();
		String defaultCalendarName = rootCalendarPath + calendarDisplayName;
		
		MSEventUid msEventUid = new MSEventUid("1");
		MSEvent msEvent = new MSEvent();
		msEvent.setUid(msEventUid);
		MSEmail invitation  = new MSEmail();
		invitation.setInvitation(msEvent, MSMessageClass.NOTE);

		expectLoginBehavior();
		
		EventExtId eventExtId = new EventExtId("1");
		expect(eventService.getEventExtIdFor(msEventUid, device))
			.andReturn(eventExtId).once();

		Event event = new Event();
		event.setUid(new EventObmId(1));
		expectGetAndModifyEvent(eventExtId, event);
		expect(calendarClient.changeParticipationState(token, userDataRequest.getUser().getLoginAtDomain(), eventExtId, null, 0, true))
			.andThrow(new NotAllowedException("Not allowed")).once();
		
		expectEventConvertion(event, false);
		expect(eventConverter.getParticipation(null, AttendeeStatus.ACCEPT))
			.andReturn(null).once();
		
		String serverId = "123";
		expect(mappingService.getCollectionIdFor(device, defaultCalendarName))
			.andReturn(1).once();
		expect(mappingService.getServerIdFor(1, "1"))
			.andReturn(serverId);
		
		expectBuildCollectionPath(calendarDisplayName);
		
		mockControl.replay();

		calendarBackend.handleMeetingResponse(userDataRequest, invitation, AttendeeStatus.ACCEPT);
	}
	
	@Test (expected=HierarchyChangedException.class)
	public void testFetchThrowsHierarchyChangedException() throws Exception {
		String serverId = "1:1";
		Integer itemId = 1;
		int collectionId = 1;
		
		expect(mappingService.getCollectionPathFor(collectionId)).andReturn(userCalendarCollectionPath.collectionPath());
		expect(collectionPathBuilder.fullyQualifiedCollectionPath(userCalendarCollectionPath.collectionPath())).andReturn(collectionPathBuilder);
		expect(collectionPathBuilder.build()).andReturn(userCalendarCollectionPath);
		
		expectLoginBehavior();
		
		expectGetItemIdFromServerId(serverId, itemId);
		
		EventObmId eventObmId = new EventObmId(itemId);
		Event event = new Event();
		event.setUid(eventObmId);
		expect(calendarClient.getEventFromId(token, "test@test", eventObmId))
			.andThrow(new NotAllowedException("Not allowed")).once();
		
		mockControl.replay();
		
		List<String> itemIds = ImmutableList.of(serverId);

		calendarBackend.fetch(userDataRequest, collectionId, itemIds, null, null, null);
	}
}
