/* ***** BEGIN LICENSE BLOCK *****
 * 
 * Copyright (C) 2013  Linagora
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
package org.obm;

import java.io.File;
import java.util.Arrays;

import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.obm.annotations.database.AutoTruncate;
import org.obm.annotations.database.DatabaseEntity;
import org.obm.annotations.database.DatabaseField;
import org.obm.annotations.transactional.ITransactionAttributeBinder;
import org.obm.annotations.transactional.Propagation;
import org.obm.annotations.transactional.TransactionException;
import org.obm.annotations.transactional.TransactionProvider;
import org.obm.annotations.transactional.Transactional;
import org.obm.annotations.transactional.TransactionalBinder;
import org.obm.annotations.transactional.TransactionalInterceptor;
import org.obm.annotations.transactional.TransactionalModule;
import org.obm.configuration.ConfigurationService;
import org.obm.configuration.ConfigurationServiceImpl;
import org.obm.configuration.ContactConfiguration;
import org.obm.configuration.DatabaseConfiguration;
import org.obm.configuration.DatabaseConfigurationImpl;
import org.obm.configuration.DatabaseFlavour;
import org.obm.configuration.DefaultTransactionConfiguration;
import org.obm.configuration.EmailConfiguration;
import org.obm.configuration.EmailConfigurationImpl;
import org.obm.configuration.SyncPermsConfigurationService;
import org.obm.configuration.TransactionConfiguration;
import org.obm.configuration.VMArgumentsUtils;
import org.obm.configuration.module.LoggerModule;
import org.obm.configuration.resourcebundle.Control;
import org.obm.configuration.store.StoreNotFoundException;
import org.obm.cyrus.imap.CyrusClientEmailConfiguration;
import org.obm.cyrus.imap.CyrusClientModule;
import org.obm.cyrus.imap.admin.CyrusImapService;
import org.obm.cyrus.imap.admin.CyrusImapServiceImpl;
import org.obm.cyrus.imap.admin.CyrusManager;
import org.obm.cyrus.imap.admin.CyrusManagerImpl;
import org.obm.cyrus.imap.admin.ImapOperationException;
import org.obm.cyrus.imap.admin.ImapPath;
import org.obm.cyrus.imap.admin.Partition;
import org.obm.cyrus.imap.admin.Quota;
import org.obm.dbcp.DatabaseConnectionProvider;
import org.obm.dbcp.DatabaseConnectionProviderImpl;
import org.obm.dbcp.jdbc.DatabaseDriverConfiguration;
import org.obm.dbcp.jdbc.DatabaseDriverConfigurationProvider;
import org.obm.domain.dao.DomainDao;
import org.obm.domain.dao.ObmInfoDao;
import org.obm.domain.dao.ObmInfoDaoJdbcImpl;
import org.obm.domain.dao.UserDao;
import org.obm.domain.dao.UserSystemDao;
import org.obm.domain.dao.UserSystemDaoJdbcImpl;
import org.obm.healthcheck.HealthCheckDefaultHandlersModule;
import org.obm.healthcheck.HealthCheckHandler;
import org.obm.healthcheck.HealthCheckModule;
import org.obm.healthcheck.handlers.JavaInformationHandler;
import org.obm.healthcheck.handlers.RootHandler;
import org.obm.icalendar.ICalendarFactory;
import org.obm.icalendar.Ical4jHelper;
import org.obm.icalendar.Ical4jUser;
import org.obm.icalendar.ical4jwrapper.EventDate;
import org.obm.icalendar.ical4jwrapper.ICalendarEvent;
import org.obm.icalendar.ical4jwrapper.ICalendarRecur;
import org.obm.icalendar.ical4jwrapper.ICalendarTimeZone;
import org.obm.locator.LocatorCacheException;
import org.obm.locator.LocatorClientException;
import org.obm.locator.LocatorClientImpl;
import org.obm.locator.store.LocatorCache;
import org.obm.locator.store.LocatorService;
import org.obm.provisioning.AuthorizingModule;
import org.obm.provisioning.BatchProcessingModule;
import org.obm.provisioning.BatchProvider;
import org.obm.provisioning.Group;
import org.obm.provisioning.GroupExtId;
import org.obm.provisioning.ObmDomainProvider;
import org.obm.provisioning.ProfileId;
import org.obm.provisioning.ProfileName;
import org.obm.provisioning.ProvisioningContextListener;
import org.obm.provisioning.ProvisioningService;
import org.obm.provisioning.annotations.PATCH;
import org.obm.provisioning.authentication.AuthenticationService;
import org.obm.provisioning.authentication.AuthenticationServiceImpl;
import org.obm.provisioning.authentication.ObmJDBCAuthorizingRealm;
import org.obm.provisioning.authorization.AuthorizationException;
import org.obm.provisioning.authorization.AuthorizationService;
import org.obm.provisioning.authorization.AuthorizationServiceImpl;
import org.obm.provisioning.authorization.ResourceAuthorizationHelper;
import org.obm.provisioning.bean.UserIdentifier;
import org.obm.provisioning.bean.UserJsonFields;
import org.obm.provisioning.beans.Batch;
import org.obm.provisioning.beans.BatchEntityType;
import org.obm.provisioning.beans.BatchStatus;
import org.obm.provisioning.beans.HttpVerb;
import org.obm.provisioning.beans.ObmDomainEntry;
import org.obm.provisioning.beans.Operation;
import org.obm.provisioning.beans.ProfileEntry;
import org.obm.provisioning.beans.Request;
import org.obm.provisioning.conf.SystemUserLdapConfiguration;
import org.obm.provisioning.dao.BatchDao;
import org.obm.provisioning.dao.BatchDaoJdbcImpl;
import org.obm.provisioning.dao.GroupDao;
import org.obm.provisioning.dao.GroupDaoJdbcImpl;
import org.obm.provisioning.dao.OperationDao;
import org.obm.provisioning.dao.OperationDaoJdbcImpl;
import org.obm.provisioning.dao.PermissionDao;
import org.obm.provisioning.dao.PermissionDaoHardcodedImpl;
import org.obm.provisioning.dao.ProfileDao;
import org.obm.provisioning.dao.ProfileDaoJdbcImpl;
import org.obm.provisioning.dao.exceptions.BatchNotFoundException;
import org.obm.provisioning.dao.exceptions.DaoException;
import org.obm.provisioning.dao.exceptions.GroupExistsException;
import org.obm.provisioning.dao.exceptions.GroupNotFoundException;
import org.obm.provisioning.dao.exceptions.GroupRecursionException;
import org.obm.provisioning.dao.exceptions.OperationNotFoundException;
import org.obm.provisioning.dao.exceptions.PermissionsNotFoundException;
import org.obm.provisioning.dao.exceptions.ProfileNotFoundException;
import org.obm.provisioning.dao.exceptions.SystemUserNotFoundException;
import org.obm.provisioning.dao.exceptions.UserNotFoundException;
import org.obm.provisioning.exception.ProcessingException;
import org.obm.provisioning.json.BatchJsonSerializer;
import org.obm.provisioning.json.GroupExtIdJsonDeserializer;
import org.obm.provisioning.json.GroupExtIdJsonSerializer;
import org.obm.provisioning.json.GroupJsonDeserializer;
import org.obm.provisioning.json.GroupJsonSerializer;
import org.obm.provisioning.json.MultimapJsonSerializer;
import org.obm.provisioning.json.ObmDomainJsonSerializer;
import org.obm.provisioning.json.ObmDomainUuidJsonDeserializer;
import org.obm.provisioning.json.ObmDomainUuidJsonSerializer;
import org.obm.provisioning.json.ObmUserJsonDeserializer;
import org.obm.provisioning.json.ObmUserJsonSerializer;
import org.obm.provisioning.json.OperationJsonSerializer;
import org.obm.provisioning.json.PatchObmUserJsonDeserializer;
import org.obm.provisioning.json.UserExtIdJsonDeserializer;
import org.obm.provisioning.json.UserExtIdJsonSerializer;
import org.obm.provisioning.ldap.client.Connection;
import org.obm.provisioning.ldap.client.ConnectionImpl;
import org.obm.provisioning.ldap.client.LdapManager;
import org.obm.provisioning.ldap.client.LdapManagerImpl;
import org.obm.provisioning.ldap.client.LdapModule;
import org.obm.provisioning.ldap.client.LdapService;
import org.obm.provisioning.ldap.client.LdapServiceImpl;
import org.obm.provisioning.ldap.client.bean.LdapDomain;
import org.obm.provisioning.ldap.client.bean.LdapGroup;
import org.obm.provisioning.ldap.client.bean.LdapUser;
import org.obm.provisioning.ldap.client.bean.LdapUserMembership;
import org.obm.provisioning.ldap.client.exception.ConnectionException;
import org.obm.provisioning.ldap.client.exception.LdapException;
import org.obm.provisioning.processing.BatchProcessingListener;
import org.obm.provisioning.processing.BatchProcessor;
import org.obm.provisioning.processing.BatchTracker;
import org.obm.provisioning.processing.OperationProcessor;
import org.obm.provisioning.processing.Processor;
import org.obm.provisioning.processing.impl.BatchProcessorImpl;
import org.obm.provisioning.processing.impl.BatchTrackerImpl;
import org.obm.provisioning.processing.impl.EntityTypeBasedOperationProcessor;
import org.obm.provisioning.processing.impl.HttpVerbBasedOperationProcessor;
import org.obm.provisioning.processing.impl.ParallelBatchProcessor;
import org.obm.provisioning.processing.impl.users.AbstractUserOperationProcessor;
import org.obm.provisioning.processing.impl.users.CreateUserOperationProcessor;
import org.obm.provisioning.processing.impl.users.DeleteUserOperationProcessor;
import org.obm.provisioning.processing.impl.users.ModifyUserOperationProcessor;
import org.obm.provisioning.processing.impl.users.PatchUserOperationProcessor;
import org.obm.provisioning.resources.AbstractBatchAwareResource;
import org.obm.provisioning.resources.BatchResource;
import org.obm.provisioning.resources.DomainBasedSubResource;
import org.obm.provisioning.resources.DomainResource;
import org.obm.provisioning.resources.GroupResource;
import org.obm.provisioning.resources.GroupWriteResource;
import org.obm.provisioning.resources.ProfileResource;
import org.obm.provisioning.resources.UserResource;
import org.obm.provisioning.resources.UserWriteResource;
import org.obm.push.LinagoraImapClientModule;
import org.obm.push.OptionalVMArguments;
import org.obm.push.bean.Builder;
import org.obm.push.mail.IMAPException;
import org.obm.push.mail.bean.Acl;
import org.obm.push.mail.imap.MinigStoreClient;
import org.obm.push.mail.imap.MinigStoreClientImpl;
import org.obm.push.minig.imap.StoreClient;
import org.obm.push.minig.imap.StoreClientImpl;
import org.obm.push.minig.imap.impl.ClientSupport;
import org.obm.push.minig.imap.impl.IResponseCallback;
import org.obm.push.utils.DOMUtils;
import org.obm.push.utils.DateUtils;
import org.obm.push.utils.FileUtils;
import org.obm.push.utils.IniFile;
import org.obm.push.utils.IntEncoder;
import org.obm.push.utils.JDBCUtils;
import org.obm.push.utils.LdapUtils;
import org.obm.push.utils.MimeContentType;
import org.obm.push.utils.NoArgFilterInputStream;
import org.obm.push.utils.SerializableInputStream;
import org.obm.push.utils.StringUtils;
import org.obm.push.utils.UUIDFactory;
import org.obm.push.utils.UserEmailParserUtils;
import org.obm.push.utils.collection.ClassToInstanceAgregateView;
import org.obm.push.utils.collection.Sets;
import org.obm.push.utils.index.IndexUtils;
import org.obm.push.utils.index.Indexed;
import org.obm.push.utils.jdbc.AbstractSQLCollectionHelper;
import org.obm.push.utils.jdbc.IntegerIndexedSQLCollectionHelper;
import org.obm.push.utils.jdbc.IntegerSQLCollectionHelper;
import org.obm.push.utils.jdbc.LongIndexedSQLCollectionHelper;
import org.obm.push.utils.jdbc.LongSQLCollectionHelper;
import org.obm.push.utils.jdbc.StringSQLCollectionHelper;
import org.obm.push.utils.jdbc.WildcardStringSQLCollectionHelper;
import org.obm.push.utils.stream.SizeLimitExceededException;
import org.obm.push.utils.stream.SizeLimitingInputStream;
import org.obm.push.utils.type.UnsignedShort;
import org.obm.push.utils.xml.XmlCharacterFilter;
import org.obm.satellite.client.Configuration;
import org.obm.satellite.client.SatelliteClientModule;
import org.obm.satellite.client.SatelliteService;
import org.obm.satellite.client.SatelliteServiceImpl;
import org.obm.satellite.client.exceptions.SatteliteClientException;
import org.obm.sync.DatabaseMetadataModule;
import org.obm.sync.DatabaseModule;
import org.obm.sync.GuiceServletContextListener;
import org.obm.sync.MessageQueueModule;
import org.obm.sync.Messages;
import org.obm.sync.NotAllowedException;
import org.obm.sync.ObmSmtpConf;
import org.obm.sync.ObmSmtpConfImpl;
import org.obm.sync.ObmSmtpProvider;
import org.obm.sync.ObmSyncModule;
import org.obm.sync.ObmSyncServicesModule;
import org.obm.sync.Right;
import org.obm.sync.ServerCapability;
import org.obm.sync.SolrJmsModule;
import org.obm.sync.XTrustProvider;
import org.obm.sync.addition.CommitedElement;
import org.obm.sync.addition.Kind;
import org.obm.sync.auth.AccessToken;
import org.obm.sync.auth.AuthFault;
import org.obm.sync.auth.BadRequestException;
import org.obm.sync.auth.ClientInformations;
import org.obm.sync.auth.Credentials;
import org.obm.sync.auth.EventAlreadyExistException;
import org.obm.sync.auth.EventNotFoundException;
import org.obm.sync.auth.LightningVersion;
import org.obm.sync.auth.Login;
import org.obm.sync.auth.MavenVersion;
import org.obm.sync.auth.OBMConnectorVersionException;
import org.obm.sync.auth.ServerFault;
import org.obm.sync.auth.Version;
import org.obm.sync.base.Category;
import org.obm.sync.base.DomainName;
import org.obm.sync.base.EmailAddress;
import org.obm.sync.base.EmailLogin;
import org.obm.sync.base.KeyList;
import org.obm.sync.book.Address;
import org.obm.sync.book.AddressBook;
import org.obm.sync.book.BookItemsParser;
import org.obm.sync.book.BookItemsWriter;
import org.obm.sync.book.BookType;
import org.obm.sync.book.Contact;
import org.obm.sync.book.ContactLabel;
import org.obm.sync.book.Folder;
import org.obm.sync.book.IMergeable;
import org.obm.sync.book.InstantMessagingId;
import org.obm.sync.book.Phone;
import org.obm.sync.book.Website;
import org.obm.sync.calendar.AllEventAttributesExceptExceptionsEquivalence;
import org.obm.sync.calendar.Anonymizable;
import org.obm.sync.calendar.Attendee;
import org.obm.sync.calendar.CalendarInfo;
import org.obm.sync.calendar.CalendarItemsParser;
import org.obm.sync.calendar.CalendarItemsWriter;
import org.obm.sync.calendar.CalendarUserType;
import org.obm.sync.calendar.Comment;
import org.obm.sync.calendar.ComparatorUsingEventHasImportantChanges;
import org.obm.sync.calendar.ContactAttendee;
import org.obm.sync.calendar.DeletedEvent;
import org.obm.sync.calendar.Event;
import org.obm.sync.calendar.EventExtId;
import org.obm.sync.calendar.EventMeetingStatus;
import org.obm.sync.calendar.EventObmId;
import org.obm.sync.calendar.EventOpacity;
import org.obm.sync.calendar.EventParticipationState;
import org.obm.sync.calendar.EventPrivacy;
import org.obm.sync.calendar.EventRecurrence;
import org.obm.sync.calendar.EventTimeUpdate;
import org.obm.sync.calendar.EventType;
import org.obm.sync.calendar.FreeBusy;
import org.obm.sync.calendar.FreeBusyInterval;
import org.obm.sync.calendar.FreeBusyRequest;
import org.obm.sync.calendar.Participation;
import org.obm.sync.calendar.ParticipationRole;
import org.obm.sync.calendar.RecurrenceDay;
import org.obm.sync.calendar.RecurrenceDays;
import org.obm.sync.calendar.RecurrenceDaysParser;
import org.obm.sync.calendar.RecurrenceDaysSerializer;
import org.obm.sync.calendar.RecurrenceId;
import org.obm.sync.calendar.RecurrenceKind;
import org.obm.sync.calendar.ResourceAttendee;
import org.obm.sync.calendar.ResourceInfo;
import org.obm.sync.calendar.SimpleAttendeeService;
import org.obm.sync.calendar.SyncRange;
import org.obm.sync.calendar.UnidentifiedAttendee;
import org.obm.sync.calendar.UserAttendee;
import org.obm.sync.client.CalendarType;
import org.obm.sync.client.login.LoginService;
import org.obm.sync.dao.TableDescription;
import org.obm.sync.date.DateProvider;
import org.obm.sync.exception.ContactNotFoundException;
import org.obm.sync.exception.IllegalRecurrenceKindException;
import org.obm.sync.exception.ObmUserNotFoundException;
import org.obm.sync.host.ObmHost;
import org.obm.sync.items.AbstractItemsParser;
import org.obm.sync.items.AbstractItemsWriter;
import org.obm.sync.items.AddressBookChanges;
import org.obm.sync.items.AddressBookChangesResponse;
import org.obm.sync.items.ContactChanges;
import org.obm.sync.items.EventChanges;
import org.obm.sync.items.FolderChanges;
import org.obm.sync.items.ParticipationChanges;
import org.obm.sync.items.UserChanges;
import org.obm.sync.login.AbstractLoginBackend;
import org.obm.sync.login.LoginBackend;
import org.obm.sync.login.LoginBindingImpl;
import org.obm.sync.login.TrustedLoginBindingImpl;
import org.obm.sync.mailingList.MLEmail;
import org.obm.sync.mailingList.MailingList;
import org.obm.sync.mailingList.MailingListItemsParser;
import org.obm.sync.mailingList.MailingListItemsWriter;
import org.obm.sync.metadata.AutoTruncateMethodInterceptor;
import org.obm.sync.metadata.DatabaseMetadataDao;
import org.obm.sync.metadata.DatabaseMetadataService;
import org.obm.sync.metadata.DatabaseMetadataServiceImpl;
import org.obm.sync.metadata.DatabaseTruncationService;
import org.obm.sync.metadata.DatabaseTruncationServiceImpl;
import org.obm.sync.resource.ResourceServlet;
import org.obm.sync.server.MailingListHandler;
import org.obm.sync.server.QueryFormatException;
import org.obm.sync.server.SyncHandlers;
import org.obm.sync.server.SyncServlet;
import org.obm.sync.server.SyncStatus;
import org.obm.sync.server.XmlResponder;
import org.obm.sync.server.auth.AuthentificationServiceFactory;
import org.obm.sync.server.auth.IAuthentificationService;
import org.obm.sync.server.auth.impl.DatabaseAuthentificationService;
import org.obm.sync.server.handler.AddressBookHandler;
import org.obm.sync.server.handler.CalendarHandler;
import org.obm.sync.server.handler.ErrorMail;
import org.obm.sync.server.handler.EventHandler;
import org.obm.sync.server.handler.ISyncHandler;
import org.obm.sync.server.handler.LoginHandler;
import org.obm.sync.server.handler.SecureSyncHandler;
import org.obm.sync.server.handler.SettingHandler;
import org.obm.sync.server.handler.TodoHandler;
import org.obm.sync.server.handler.UserHandler;
import org.obm.sync.server.handler.VersionValidator;
import org.obm.sync.server.mailer.AbstractMailer;
import org.obm.sync.server.mailer.ErrorMailer;
import org.obm.sync.server.mailer.EventChangeMailer;
import org.obm.sync.server.template.ITemplateLoader;
import org.obm.sync.server.template.TemplateLoaderFreeMarkerImpl;
import org.obm.sync.serviceproperty.ServiceProperty;
import org.obm.sync.services.AttendeeService;
import org.obm.sync.services.IAddressBook;
import org.obm.sync.services.ICalendar;
import org.obm.sync.services.IMailingList;
import org.obm.sync.services.ISetting;
import org.obm.sync.services.ImportICalendarException;
import org.obm.sync.setting.ForwardingSettings;
import org.obm.sync.setting.SettingItemsParser;
import org.obm.sync.setting.SettingItemsWriter;
import org.obm.sync.setting.VacationSettings;
import org.obm.sync.solr.ContactIndexer;
import org.obm.sync.solr.EventIndexer;
import org.obm.sync.solr.IndexerFactory;
import org.obm.sync.solr.Remover;
import org.obm.sync.solr.SolrHelper;
import org.obm.sync.solr.SolrManager;
import org.obm.sync.solr.SolrRequest;
import org.obm.sync.solr.SolrService;
import org.obm.sync.solr.jms.Command;
import org.obm.sync.solr.jms.CommandConverter;
import org.obm.sync.solr.jms.ContactCommand;
import org.obm.sync.solr.jms.ContactDeleteCommand;
import org.obm.sync.solr.jms.ContactUpdateCommand;
import org.obm.sync.solr.jms.DefaultCommandConverter;
import org.obm.sync.solr.jms.EventCommand;
import org.obm.sync.solr.jms.EventDeleteCommand;
import org.obm.sync.solr.jms.EventUpdateCommand;
import org.obm.sync.solr.jms.SolrJmsQueue;
import org.obm.sync.stream.ListenableInputStream;
import org.obm.sync.tag.Closable;
import org.obm.sync.tag.InputStreamListener;
import org.obm.sync.user.User;
import org.obm.sync.utils.DateHelper;
import org.obm.sync.utils.DisplayNameUtils;
import org.obm.sync.utils.MailUtils;
import org.obm.utils.DBUtils;
import org.obm.utils.LinkedEntity;
import org.obm.utils.ObmHelper;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.linagora.obm.sync.Producer;
import com.linagora.obm.sync.QueueManager;

import fr.aliacom.obm.common.DumpFilter;
import fr.aliacom.obm.common.FindException;
import fr.aliacom.obm.common.MailService;
import fr.aliacom.obm.common.ObmSyncVersion;
import fr.aliacom.obm.common.ObmSyncVersionNotFoundException;
import fr.aliacom.obm.common.SQLUtils;
import fr.aliacom.obm.common.StoreException;
import fr.aliacom.obm.common.addition.CommitedOperationDao;
import fr.aliacom.obm.common.addition.CommitedOperationDaoJdbcImpl;
import fr.aliacom.obm.common.calendar.AttendeeServiceJdbcImpl;
import fr.aliacom.obm.common.calendar.CalendarBindingImpl;
import fr.aliacom.obm.common.calendar.CalendarDao;
import fr.aliacom.obm.common.calendar.CalendarDaoJdbcImpl;
import fr.aliacom.obm.common.calendar.CalendarEncoding;
import fr.aliacom.obm.common.calendar.CategoryDao;
import fr.aliacom.obm.common.calendar.EventChangeHandler;
import fr.aliacom.obm.common.calendar.EventMail;
import fr.aliacom.obm.common.calendar.EventNotificationService;
import fr.aliacom.obm.common.calendar.EventNotificationServiceImpl;
import fr.aliacom.obm.common.calendar.EventUtils;
import fr.aliacom.obm.common.calendar.MessageQueueService;
import fr.aliacom.obm.common.calendar.MessageQueueServiceImpl;
import fr.aliacom.obm.common.calendar.ResourceNotFoundException;
import fr.aliacom.obm.common.calendar.loader.AlertLoader;
import fr.aliacom.obm.common.calendar.loader.AttendeeLoader;
import fr.aliacom.obm.common.calendar.loader.EventBuilder;
import fr.aliacom.obm.common.calendar.loader.EventExceptionLoader;
import fr.aliacom.obm.common.calendar.loader.EventLoader;
import fr.aliacom.obm.common.calendar.loader.ExceptionLoader;
import fr.aliacom.obm.common.calendar.loader.ResourceLoader;
import fr.aliacom.obm.common.calendar.loader.filter.DeclinedAttendeeFilter;
import fr.aliacom.obm.common.calendar.loader.filter.EventFilter;
import fr.aliacom.obm.common.contact.AddressBookBindingImpl;
import fr.aliacom.obm.common.contact.ContactDao;
import fr.aliacom.obm.common.contact.ContactMerger;
import fr.aliacom.obm.common.contact.ContactPrivacy;
import fr.aliacom.obm.common.contact.ContactUpdates;
import fr.aliacom.obm.common.contact.FolderUpdates;
import fr.aliacom.obm.common.domain.DomainCache;
import fr.aliacom.obm.common.domain.DomainService;
import fr.aliacom.obm.common.domain.ObmDomain;
import fr.aliacom.obm.common.domain.ObmDomainUuid;
import fr.aliacom.obm.common.mailingList.MailingListBindingImpl;
import fr.aliacom.obm.common.mailingList.MailingListHome;
import fr.aliacom.obm.common.resource.Resource;
import fr.aliacom.obm.common.resource.ResourceDao;
import fr.aliacom.obm.common.session.SessionManagement;
import fr.aliacom.obm.common.setting.SettingBindingImpl;
import fr.aliacom.obm.common.setting.SettingDao;
import fr.aliacom.obm.common.setting.SettingsService;
import fr.aliacom.obm.common.setting.SettingsServiceImpl;
import fr.aliacom.obm.common.system.ObmSystemUser;
import fr.aliacom.obm.common.trust.TrustToken;
import fr.aliacom.obm.common.trust.TrustTokenDao;
import fr.aliacom.obm.common.user.ObmUser;
import fr.aliacom.obm.common.user.UserExtId;
import fr.aliacom.obm.common.user.UserService;
import fr.aliacom.obm.common.user.UserServiceImpl;
import fr.aliacom.obm.common.user.UserSettings;
import fr.aliacom.obm.freebusy.DatabaseFreeBusyProvider;
import fr.aliacom.obm.freebusy.FreeBusyException;
import fr.aliacom.obm.freebusy.FreeBusyPluginModule;
import fr.aliacom.obm.freebusy.FreeBusyProvider;
import fr.aliacom.obm.freebusy.FreeBusyServlet;
import fr.aliacom.obm.freebusy.LocalFreeBusyProvider;
import fr.aliacom.obm.freebusy.PrivateFreeBusyException;
import fr.aliacom.obm.freebusy.RemoteFreeBusyProvider;
import fr.aliacom.obm.ldap.BadDirectory;
import fr.aliacom.obm.ldap.BrokenLDAPAuthConfiguration;
import fr.aliacom.obm.ldap.LDAPAuthConfig;
import fr.aliacom.obm.ldap.LDAPAuthService;
import fr.aliacom.obm.ldap.LDAPDirectory;
import fr.aliacom.obm.ldap.LDAPUtils;
import fr.aliacom.obm.ldap.PasswordHandler;
import fr.aliacom.obm.ldap.UnixCrypt;
import fr.aliacom.obm.services.constant.ObmSyncConfigurationService;
import fr.aliacom.obm.services.constant.ObmSyncConfigurationServiceImpl;
import fr.aliacom.obm.services.constant.SpecialAccounts;
import fr.aliacom.obm.utils.EventObmIdSQLCollectionHelper;
import fr.aliacom.obm.utils.HelperDao;
import fr.aliacom.obm.utils.HelperService;
import fr.aliacom.obm.utils.HelperServiceImpl;
import fr.aliacom.obm.utils.LogUtils;
import fr.aliacom.obm.utils.RFC2445;

public class DependencyResolverHelper {

	public static File[] projectDependencies(File pomFile) {
		return filterObmDependencies(allObmSyncDependencies(pomFile));
	}

	private static MavenResolvedArtifact[] allObmSyncDependencies(File pomFile) {
		return Maven.resolver()
			.offline()
			.loadPomFromFile(pomFile)
			.importRuntimeAndTestDependencies()
			.asResolvedArtifact();
	}

	private static File[] filterObmDependencies(MavenResolvedArtifact[] allObmSyncDependencies) {
		return FluentIterable.from(Arrays.asList(
				allObmSyncDependencies))
				.filter(obmDependencyPredicate())
				.transform(artifactAsFile()).toArray(File.class);
	}

	private static Function<MavenResolvedArtifact, File> artifactAsFile() {
		return new Function<MavenResolvedArtifact, File>() {
			@Override
			public File apply(MavenResolvedArtifact input) {
				return input.asFile();
			}
		};
	}

	private static Predicate<MavenResolvedArtifact> obmDependencyPredicate() {
		return new Predicate<MavenResolvedArtifact>() {

			@Override
			public boolean apply(MavenResolvedArtifact input) {
				String groupId = input.getCoordinate().getGroupId();
				return !(groupId.startsWith("com.linagora") || groupId.startsWith("org.obm"));
			}
		};
	}
	
	public static Class<?>[] projectCyrusClientClasses() {
		return new Class<?>[] {
				CyrusClientModule.class,
				Acl.class,
				org.obm.cyrus.imap.admin.Connection.class,
				org.obm.cyrus.imap.admin.ConnectionException.class,
				org.obm.cyrus.imap.admin.ConnectionImpl.class,
				CyrusImapService.class,
				CyrusImapService.class,
				CyrusImapServiceImpl.class,
				CyrusManager.class,
				CyrusManagerImpl.class,
				ImapOperationException.class,
				ImapPath.class,
				Partition.class,
				Quota.class,
				MinigStoreClient.class,
				MinigStoreClientImpl.class,
				StoreClient.class,
				StoreClientImpl.class,
				IMAPException.class,
				LinagoraImapClientModule.class,
				org.obm.push.bean.Resource.class,
				IResponseCallback.class,
				ClientSupport.class,
				CyrusClientEmailConfiguration.class
		};
	}

	public static Class<?>[] projectLdapClientClasses() {
		return new Class<?>[] { 
				LdapModule.class, 
				LdapService.class,
				LdapServiceImpl.class, 
				org.obm.provisioning.ldap.client.Configuration.class, 
				Connection.class,
				ConnectionException.class, 
				ConnectionImpl.class,
				LdapException.class, 
				LdapGroup.class,
				LdapUser.class, 
				LdapUserMembership.class,
				LdapManager.class,
				LdapManager.Factory.class,
				LdapManagerImpl.class,
				LdapDomain.class
		};
	}

	public static Class<?>[] projectSatelliteClientClasses() {
		return new Class<?>[] { 
				org.obm.satellite.client.exceptions.ConnectionException.class,
				SatteliteClientException.class,
				Configuration.class,
				org.obm.satellite.client.Connection.class,
				org.obm.satellite.client.ConnectionImpl.class,
				SatelliteClientModule.class,
				SatelliteService.class,
				SatelliteServiceImpl.class
		};
	}
	
	public static Class<?>[] projectProvisioningClasses() {
		return new Class<?>[] {
				ProvisioningService.class,
				ProvisioningContextListener.class,
				UserResource.class,
				BatchResource.class,
				DomainBasedSubResource.class,
				ProfileResource.class,
				UserIdentifier.class,
				ObmDomainProvider.class,
				ObmDomainUuidJsonSerializer.class,
				ObmDomainUuidJsonDeserializer.class,
				ObmUserJsonDeserializer.class,
				ObmUserJsonSerializer.class,
				UserJsonFields.class,
				PATCH.class,
				MultimapJsonSerializer.class,
				ObmDomainJsonSerializer.class,
				AbstractBatchAwareResource.class,
				BatchProvider.class,
				DomainResource.class,
				ProfileResource.class,
				ProvisioningContextListener.class,
				UserWriteResource.class,
				BatchJsonSerializer.class,
				OperationJsonSerializer.class,
				ObmDomainEntry.class,
				UserExtIdJsonDeserializer.class,
				UserExtIdJsonSerializer.class,
				GroupExtIdJsonDeserializer.class,
				GroupExtIdJsonSerializer.class,
				GroupJsonDeserializer.class,
				GroupJsonSerializer.class,
				BatchProcessingModule.class,
				BatchProcessor.class,
				BatchProcessorImpl.class,
				OperationProcessor.class,
				Processor.class,
				CreateUserOperationProcessor.class,
				DeleteUserOperationProcessor.class,
				ParallelBatchProcessor.class,
				HttpVerbBasedOperationProcessor.class,
				EntityTypeBasedOperationProcessor.class,
				ProcessingException.class,
				AuthorizingModule.class,
				ObmJDBCAuthorizingRealm.class,
				AuthenticationService.class,
				AuthenticationServiceImpl.class,
				AuthorizationService.class,
				AuthorizationServiceImpl.class,
				AuthorizationException.class,
				PermissionsNotFoundException.class,
				ResourceAuthorizationHelper.class,
				ServiceProperty.class,
				BatchTracker.class,
				BatchTrackerImpl.class,
				BatchProcessingListener.class,
				AbstractUserOperationProcessor.class,
				ModifyUserOperationProcessor.class,
				GroupResource.class,
				GroupWriteResource.class,
				SystemUserLdapConfiguration.class,
				PatchObmUserJsonDeserializer.class,
				PatchUserOperationProcessor.class
		};
	}

	public static Class<?>[] projectObmDaoClasses() {
		return new Class<?>[] {
				DaoException.class,
				BatchNotFoundException.class,
				GroupNotFoundException.class,
				OperationNotFoundException.class,
				ProfileNotFoundException.class,
				UserNotFoundException.class,
				DomainDao.class,
				ProfileDao.class,
				ProfileDaoJdbcImpl.class,
				OperationDao.class,
				OperationDaoJdbcImpl.class,
				UserDao.class,
				GroupDao.class,
				GroupDaoJdbcImpl.class,
				BatchDao.class,
				BatchDaoJdbcImpl.class,
				Batch.class,
				BatchEntityType.class,
				BatchStatus.class,
				HttpVerb.class,
				Operation.class,
				ProfileEntry.class,
				ProfileId.class,
				ProfileName.class,
				Request.class,
				ObmDomain.class,
				ObmDomainUuid.class,
				GroupExtId.class,
				Group.class,
				ObmUser.class,
				ObmHelper.class,
				DBUtils.class,
				DateProvider.class,
				UserExtId.class,
				DomainName.class,
				EmailLogin.class,
				LinkedEntity.class,
				SystemUserNotFoundException.class,
				UserSystemDao.class,
				UserSystemDaoJdbcImpl.class,
				ObmSystemUser.class,
				ObmHost.class,
				ObmInfoDao.class,
				ObmInfoDaoJdbcImpl.class,
				PermissionDao.class,
				PermissionDaoHardcodedImpl.class,
				GroupNotFoundException.class,
				GroupRecursionException.class,
				GroupExistsException.class
		};
	}

	public static Class<?>[] projectAnnotationsClasses() {
		return new Class<?>[] {
				ITransactionAttributeBinder.class,
				Propagation.class,
				TransactionalBinder.class,
				TransactionalInterceptor.class,
				Transactional.class,
				TransactionalModule.class,
				TransactionException.class,
				TransactionProvider.class,
				AutoTruncate.class,
				DatabaseField.class,
				DatabaseEntity.class
		};
	}

	public static Class<?>[] projectConfigurationClasses() {
		return new Class<?>[] {
				ConfigurationServiceImpl.class,
				ConfigurationService.class,
				ContactConfiguration.class,
				DatabaseConfigurationImpl.class,
				DatabaseConfiguration.class,
				DatabaseFlavour.class,
				DefaultTransactionConfiguration.class,
				EmailConfigurationImpl.class,
				EmailConfiguration.class,
				LoggerModule.class,
				Control.class,
				StoreNotFoundException.class,
				SyncPermsConfigurationService.class,
				TransactionConfiguration.class,
				VMArgumentsUtils.class,
				IniFile.class,
		};
	}
	
	public static Class<?>[] projectDBCPClasses() {
		return new Class<?>[] {
				DatabaseConnectionProviderImpl.class,
				DatabaseConnectionProvider.class,
				DatabaseDriverConfiguration.class,
				DatabaseDriverConfigurationProvider.class,
		};
	}
	
	public static Class<?>[] projectHealthCheckClasses() {
		return new Class<?>[] {
				JavaInformationHandler.class,
				RootHandler.class,
				HealthCheckDefaultHandlersModule.class,
				HealthCheckHandler.class,
				HealthCheckModule.class,
		};
	}
	
	public static Class<?>[] projectDatabaseMetadataClasses() {
		return new Class<?>[] {
				DatabaseMetadataDao.class,
				DatabaseMetadataService.class,
				DatabaseMetadataServiceImpl.class,
				AutoTruncateMethodInterceptor.class,
				DatabaseTruncationService.class,
				DatabaseTruncationServiceImpl.class,
				DatabaseMetadataModule.class,
				TableDescription.class
		};
	}
	
	public static Class<?>[] projectICalendarClasses() {
		return new Class<?>[] {
				Ical4jHelper.class,
				Ical4jUser.class,
				EventDate.class,
				ICalendarEvent.class,
				ICalendarRecur.class,
				ICalendarTimeZone.class,
				ICalendarFactory.class,
				org.obm.icalendar.ICalendar.class,
		};
	}
	
	public static Class<?>[] projectMessageQueueClasses() {
		return new Class<?>[] {
				Producer.class,
				QueueManager.class,
		};
	}
	
	public static Class<?>[] projectUtilsClasses() {
		return new Class<?>[] {
				Builder.class,
				OptionalVMArguments.class,
				ClassToInstanceAgregateView.class,
				Sets.class,
				DateUtils.class,
				DOMUtils.class,
				FileUtils.class,
				Indexed.class,
				IndexUtils.class,
				IntEncoder.class,
				AbstractSQLCollectionHelper.class,
				IntegerIndexedSQLCollectionHelper.class,
				IntegerSQLCollectionHelper.class,
				LongIndexedSQLCollectionHelper.class,
				LongSQLCollectionHelper.class,
				StringSQLCollectionHelper.class,
				WildcardStringSQLCollectionHelper.class,
				JDBCUtils.class,
				LdapUtils.class,
				MimeContentType.class,
				NoArgFilterInputStream.class,
				SerializableInputStream.class,
				SizeLimitExceededException.class,
				SizeLimitingInputStream.class,
				StringUtils.class,
				UnsignedShort.class,
				UserEmailParserUtils.class,
				UUIDFactory.class,
				XmlCharacterFilter.class,
		};
	}
	
	public static Class<?>[] projectLocatorClasses() {
		return new Class<?>[] {
				LocatorCacheException.class,
				LocatorClientException.class,
				LocatorClientImpl.class,
				LocatorCache.class,
				LocatorService.class,
		};
	}
	
	public static Class<?>[] projectServicesCommonClasses() {
		return new Class<?>[] {
				CommitedOperationDao.class,
				CommitedOperationDaoJdbcImpl.class,
				AttendeeServiceJdbcImpl.class,
				CalendarBindingImpl.class,
				CalendarDao.class,
				CalendarDaoJdbcImpl.class,
				CalendarEncoding.class,
				CategoryDao.class,
				EventChangeHandler.class,
				EventMail.class,
				EventNotificationServiceImpl.class,
				EventNotificationService.class,
				EventUtils.class,
				AlertLoader.class,
				AttendeeLoader.class,
				EventBuilder.class,
				EventExceptionLoader.class,
				EventLoader.class,
				ExceptionLoader.class,
				DeclinedAttendeeFilter.class,
				EventFilter.class,
				ResourceLoader.class,
				MailService.class,
				MessageQueueServiceImpl.class,
				MessageQueueService.class,
				ResourceNotFoundException.class,
				AddressBookBindingImpl.class,
				ContactDao.class,
				ContactMerger.class,
				ContactPrivacy.class,
				ContactUpdates.class,
				FolderUpdates.class,
				UserDao.class,
				DomainCache.class,
				DomainDao.class,
				DomainService.class,
				DumpFilter.class,
				FindException.class,
				MailingListBindingImpl.class,
				MailingListHome.class,
				fr.aliacom.obm.common.calendar.MailService.class,
				ObmSyncVersion.class,
				ObmSyncVersionNotFoundException.class,
				ResourceDao.class,
				SessionManagement.class,
				SettingBindingImpl.class,
				SettingDao.class,
				SettingsServiceImpl.class,
				SettingsService.class,
				SQLUtils.class,
				StoreException.class,
				TrustTokenDao.class,
				fr.aliacom.obm.common.contact.UserDao.class,
				UserServiceImpl.class,
				UserService.class,
				DatabaseFreeBusyProvider.class,
				FreeBusyServlet.class,
				BadDirectory.class,
				BrokenLDAPAuthConfiguration.class,
				LDAPAuthConfig.class,
				LDAPAuthService.class,
				LDAPDirectory.class,
				LDAPUtils.class,
				PasswordHandler.class,
				UnixCrypt.class,
				ObmSyncConfigurationService.class,
				ObmSyncConfigurationServiceImpl.class,
				SpecialAccounts.class,
				DBUtils.class,
				EventObmIdSQLCollectionHelper.class,
				HelperDao.class,
				HelperServiceImpl.class,
				HelperService.class,
				LinkedEntity.class,
				LogUtils.class,
				ObmHelper.class,
				RFC2445.class,
				GuiceServletContextListener.class,
				AbstractLoginBackend.class,
				LoginBackend.class,
				LoginBindingImpl.class,
				TrustedLoginBindingImpl.class,
				MessageQueueModule.class,
				Messages.class,
				ObmSmtpConfImpl.class,
				ObmSmtpConf.class,
				ObmSmtpProvider.class,
				ObmSyncModule.class,
				ObmSyncServicesModule.class,
				ResourceServlet.class,
				AuthentificationServiceFactory.class,
				IAuthentificationService.class,
				DatabaseAuthentificationService.class,
				AddressBookHandler.class,
				CalendarHandler.class,
				ErrorMail.class,
				EventHandler.class,
				ISyncHandler.class,
				LoginHandler.class,
				SecureSyncHandler.class,
				SettingHandler.class,
				TodoHandler.class,
				UserHandler.class,
				VersionValidator.class,
				AbstractMailer.class,
				ErrorMailer.class,
				EventChangeMailer.class,
				MailingListHandler.class,
				QueryFormatException.class,
				org.obm.sync.server.Request.class,
				SyncHandlers.class,
				SyncServlet.class,
				SyncStatus.class,
				ITemplateLoader.class,
				TemplateLoaderFreeMarkerImpl.class,
				XmlResponder.class,
				ContactIndexer.class,
				EventIndexer.class,
				IndexerFactory.class,
				CommandConverter.class,
				Command.class,
				ContactCommand.class,
				ContactDeleteCommand.class,
				ContactUpdateCommand.class,
				DefaultCommandConverter.class,
				EventCommand.class,
				EventDeleteCommand.class,
				EventUpdateCommand.class,
				SolrJmsQueue.class,
				Remover.class,
				SolrHelper.class,
				SolrManager.class,
				SolrRequest.class,
				SolrService.class,
				SolrJmsModule.class,
				DatabaseModule.class,
		};
	}
	
	public static Class<?>[] projectCommonClasses() {
		return new Class<?>[] {
				ObmHost.class,
				UserExtId.class,
				ObmDomain.class,
				ObmDomainUuid.class,
				Resource.class,
				TrustToken.class,
				ObmUser.class,
				UserSettings.class,
				FreeBusyException.class,
				FreeBusyPluginModule.class,
				FreeBusyProvider.class,
				LocalFreeBusyProvider.class,
				PrivateFreeBusyException.class,
				RemoteFreeBusyProvider.class,
				CommitedElement.class,
				Kind.class,
				AccessToken.class,
				AuthFault.class,
				BadRequestException.class,
				ClientInformations.class,
				Credentials.class,
				EventAlreadyExistException.class,
				EventNotFoundException.class,
				LightningVersion.class,
				Login.class,
				MavenVersion.class,
				OBMConnectorVersionException.class,
				ServerFault.class,
				Version.class,
				Category.class,
				DomainName.class,
				EmailAddress.class,
				EmailLogin.class,
				KeyList.class,
				AddressBook.class,
				Address.class,
				BookItemsParser.class,
				BookItemsWriter.class,
				BookType.class,
				Contact.class,
				ContactLabel.class,
				Folder.class,
				IMergeable.class,
				InstantMessagingId.class,
				Phone.class,
				Website.class,
				AllEventAttributesExceptExceptionsEquivalence.class,
				Anonymizable.class,
				Attendee.class,
				CalendarInfo.class,
				CalendarItemsParser.class,
				CalendarItemsWriter.class,
				CalendarUserType.class,
				Comment.class,
				ComparatorUsingEventHasImportantChanges.class,
				ContactAttendee.class,
				DeletedEvent.class,
				EventExtId.class,
				Event.class,
				EventMeetingStatus.class,
				EventObmId.class,
				EventOpacity.class,
				EventParticipationState.class,
				EventPrivacy.class,
				EventRecurrence.class,
				EventTimeUpdate.class,
				EventType.class,
				FreeBusyInterval.class,
				FreeBusy.class,
				FreeBusyRequest.class,
				Participation.class,
				ParticipationRole.class,
				RecurrenceDay.class,
				RecurrenceDays.class,
				RecurrenceDaysParser.class,
				RecurrenceDaysSerializer.class,
				RecurrenceId.class,
				RecurrenceKind.class,
				ResourceAttendee.class,
				ResourceInfo.class,
				SimpleAttendeeService.class,
				SyncRange.class,
				UnidentifiedAttendee.class,
				UserAttendee.class,
				CalendarType.class,
				LoginService.class,
				DateProvider.class,
				ContactNotFoundException.class,
				IllegalRecurrenceKindException.class,
				ObmUserNotFoundException.class,
				AbstractItemsParser.class,
				AbstractItemsWriter.class,
				AddressBookChanges.class,
				AddressBookChangesResponse.class,
				ContactChanges.class,
				EventChanges.class,
				FolderChanges.class,
				ParticipationChanges.class,
				UserChanges.class,
				MailingListItemsParser.class,
				MailingListItemsWriter.class,
				MailingList.class,
				MLEmail.class,
				NotAllowedException.class,
				Right.class,
				ServerCapability.class,
				AttendeeService.class,
				IAddressBook.class,
				ICalendar.class,
				IMailingList.class,
				ImportICalendarException.class,
				ISetting.class,
				ForwardingSettings.class,
				SettingItemsParser.class,
				SettingItemsWriter.class,
				VacationSettings.class,
				ListenableInputStream.class,
				Closable.class,
				InputStreamListener.class,
				User.class,
				DateHelper.class,
				DisplayNameUtils.class,
				MailUtils.class,
				XTrustProvider.class,
				ObmSystemUser.class
		};
	}
}
