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
package org.obm.push.store.ehcache;

import static org.fest.assertions.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.obm.configuration.ConfigurationService;
import org.obm.filter.Slow;
import org.obm.filter.SlowFilterRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bitronix.tm.TransactionManagerServices;

@RunWith(SlowFilterRunner.class) @Slow
public class EhCacheStatisticsImplTest extends StoreManagerConfigurationTest {

	private static final String STATS_ENABLED_CACHE = ObjectStoreManager.MAIL_SNAPSHOT_STORE;
	
	private Logger logger;
	private ConfigurationService configurationService;
	private ObjectStoreManager cacheManager;

	@Before
	public void init() throws Exception {
		logger = LoggerFactory.getLogger(getClass());
		configurationService = super.mockConfigurationService();
	}

	public void commitThenCloseTransaction() throws Exception {
		TransactionManagerServices.getTransactionManager().commit();
		TransactionManagerServices.getTransactionManager().shutdown();
	}
	
	@Test(expected=StatisticsNotAvailableException.class)
	public void testShortTimeDiskGetsFirstTriggersNA() throws Exception {
		EhCacheStatisticsImpl testee = testeeWithConfig(new TestingEhCacheConfiguration()
			.withMaxMemoryInMB(10)
			.withStatsShortSamplingTimeInSeconds(1));
		
		try {
			commitThenCloseTransaction();
			testee.shortTimeDiskGets(STATS_ENABLED_CACHE);
		} finally {
			testee.manager.shutdown();
		}
	}
	
	@Test(expected=StatisticsNotAvailableException.class)
	public void testShortTimeDiskGetsWhenSampleNotFinishedTriggersNA() throws Exception {
		EhCacheStatisticsImpl testee = testeeWithConfig(new TestingEhCacheConfiguration()
			.withMaxMemoryInMB(10)
			.withStatsShortSamplingTimeInSeconds(10));
		
		try {
			commitThenCloseTransaction();
			testee.shortTimeDiskGets(STATS_ENABLED_CACHE);
		} catch(StatisticsNotAvailableException e) {
			// expected
		}
		try {
			testee.shortTimeDiskGets(STATS_ENABLED_CACHE);
		} finally {
			testee.manager.shutdown();
		}
	}
	
	@Test
	public void testShortTimeDiskGetsWhenNoAccess() throws Exception {
		EhCacheStatisticsImpl testee = testeeWithConfig(new TestingEhCacheConfiguration()
			.withMaxMemoryInMB(10)
			.withStatsShortSamplingTimeInSeconds(1));

		EhCacheStatisticsImpl restartedTestee = putXElementsThenRestartWithSameConfig(2, testee);
		
		try {
			startStatisticsSampling(restartedTestee);
			commitThenCloseTransaction();
			waitForStatisticsSamples(restartedTestee.config.statsShortSamplingTimeInSeconds());
			assertThat(restartedTestee.shortTimeDiskGets(STATS_ENABLED_CACHE)).isEqualTo(0);
		} finally {
			restartedTestee.manager.shutdown();
		}
	}
	
	@Test
	public void testShortTimeDiskGetsWhenGetOnly() throws Exception {
		EhCacheStatisticsImpl testee = testeeWithConfig(new TestingEhCacheConfiguration()
			.withMaxMemoryInMB(10)
			.withStatsShortSamplingTimeInSeconds(1));
			
		EhCacheStatisticsImpl restartedTestee = putXElementsThenRestartWithSameConfig(2, testee);
		
		try {
			startStatisticsSampling(restartedTestee);
			readAllElementsInCache(restartedTestee);
			commitThenCloseTransaction();
			waitForStatisticsSamples(restartedTestee.config.statsShortSamplingTimeInSeconds());
			assertThat(restartedTestee.shortTimeDiskGets(STATS_ENABLED_CACHE)).isEqualTo(2);
		} finally {
			restartedTestee.manager.shutdown();
		}
	}
	
	@Test
	public void testShortTimeDiskGetsWhenGetOnNonExistingKey() throws Exception {
		EhCacheStatisticsImpl testee = testeeWithConfig(new TestingEhCacheConfiguration()
			.withMaxMemoryInMB(10)
			.withStatsShortSamplingTimeInSeconds(1));
			
		try {
			startStatisticsSampling(testee);
			putXElements(2, testee);
			testee.manager.getStore(STATS_ENABLED_CACHE).get("nonExistingKey");
			commitThenCloseTransaction();
			waitForStatisticsSamples(testee.config.statsShortSamplingTimeInSeconds());
			assertThat(testee.shortTimeDiskGets(STATS_ENABLED_CACHE)).isEqualTo(0);
		} finally {
			testee.manager.shutdown();
		}
	}
	
	/*
	 * This test is wrong, only remove should returns 0 disk access.
	 * But EHCache tells that there were 4 GETs and 2 REMOVEs
	 */
	@Test
	public void testShortTimeDiskGetsWhenNoGetButRemove() throws Exception {
		EhCacheStatisticsImpl testee = testeeWithConfig(new TestingEhCacheConfiguration()
			.withMaxMemoryInMB(10)
			.withStatsShortSamplingTimeInSeconds(1));
		
		EhCacheStatisticsImpl restartedTestee = putXElementsThenRestartWithSameConfig(2, testee);
		
		try {
			startStatisticsSampling(restartedTestee);
			removeAllElementsInCache(restartedTestee);
			commitThenCloseTransaction();
			waitForStatisticsSamples(restartedTestee.config.statsShortSamplingTimeInSeconds());
			assertThat(restartedTestee.shortTimeDiskGets(STATS_ENABLED_CACHE)).isEqualTo(2);
		} finally {
			restartedTestee.manager.shutdown();
		}
	}
	
	@Test
	public void testShortTimeDiskGetsWhenRemoveOnNonExistingKey() throws Exception {
		EhCacheStatisticsImpl testee = testeeWithConfig(new TestingEhCacheConfiguration()
			.withMaxMemoryInMB(10)
			.withStatsShortSamplingTimeInSeconds(1));
			
		try {
			startStatisticsSampling(testee);
			putXElements(2, testee);
			testee.manager.getStore(STATS_ENABLED_CACHE).remove("nonExistingKey");
			commitThenCloseTransaction();
			waitForStatisticsSamples(testee.config.statsShortSamplingTimeInSeconds());
			assertThat(testee.shortTimeDiskGets(STATS_ENABLED_CACHE)).isEqualTo(0);
		} finally {
			testee.manager.shutdown();
		}
	}
	
	@Test
	public void testShortTimeDiskGetsWhenGetThenRemove() throws Exception {
		EhCacheStatisticsImpl testee = testeeWithConfig(new TestingEhCacheConfiguration()
			.withMaxMemoryInMB(10)
			.withStatsShortSamplingTimeInSeconds(1));
			
		EhCacheStatisticsImpl restartedTestee = putXElementsThenRestartWithSameConfig(2, testee);
		
		try {
			startStatisticsSampling(restartedTestee);
			readAllElementsInCache(restartedTestee);
			removeAllElementsInCache(restartedTestee);
			commitThenCloseTransaction();
			waitForStatisticsSamples(restartedTestee.config.statsShortSamplingTimeInSeconds());
			assertThat(restartedTestee.shortTimeDiskGets(STATS_ENABLED_CACHE)).isEqualTo(2);
		} finally {
			restartedTestee.manager.shutdown();
		}
	}
	
	@Test
	public void testShortTimeDiskGetsWhenRemoveThenGet() throws Exception {
		EhCacheStatisticsImpl testee = testeeWithConfig(new TestingEhCacheConfiguration()
			.withMaxMemoryInMB(10)
			.withStatsShortSamplingTimeInSeconds(1));
			
		EhCacheStatisticsImpl restartedTestee = putXElementsThenRestartWithSameConfig(2, testee);
		
		try {
			startStatisticsSampling(restartedTestee);
			removeAllElementsInCache(restartedTestee);
			commitThenCloseTransaction();
			waitForStatisticsSamples(restartedTestee.config.statsShortSamplingTimeInSeconds());
			TransactionManagerServices.getTransactionManager().begin();
			readAllElementsInCache(restartedTestee);
			commitThenCloseTransaction();
			waitForStatisticsSamples(restartedTestee.config.statsShortSamplingTimeInSeconds());
			assertThat(restartedTestee.shortTimeDiskGets(STATS_ENABLED_CACHE)).isEqualTo(0);
		} finally {
			restartedTestee.manager.shutdown();
		}
	}
	
	@Test
	public void testShortTimeDiskGetsWhenGetThenRemoveInAnotherTransaction() throws Exception {
		EhCacheStatisticsImpl testee = testeeWithConfig(new TestingEhCacheConfiguration()
			.withMaxMemoryInMB(10)
			.withStatsShortSamplingTimeInSeconds(1));
			
		EhCacheStatisticsImpl restartedTestee = putXElementsThenRestartWithSameConfig(2, testee);
		
		try {
			startStatisticsSampling(restartedTestee);
			readAllElementsInCache(restartedTestee);
			commitThenCloseTransaction();
			waitForStatisticsSamples(restartedTestee.config.statsShortSamplingTimeInSeconds());
			TransactionManagerServices.getTransactionManager().begin();
			removeAllElementsInCache(restartedTestee);
			commitThenCloseTransaction();
			waitForStatisticsSamples(restartedTestee.config.statsShortSamplingTimeInSeconds());
			assertThat(restartedTestee.shortTimeDiskGets(STATS_ENABLED_CACHE)).isEqualTo(0);
		} finally {
			restartedTestee.manager.shutdown();
		}
	}

	@Test
	public void testShortTimeDiskGetsWhenGetAfterUpdate() throws Exception {
		EhCacheStatisticsImpl testee = testeeWithConfig(new TestingEhCacheConfiguration()
			.withMaxMemoryInMB(10)
			.withStatsShortSamplingTimeInSeconds(1));
			
		EhCacheStatisticsImpl restartedTestee = putXElementsThenRestartWithSameConfig(2, testee);
		
		try {
			startStatisticsSampling(restartedTestee);
			readAllElementsInCache(restartedTestee);
			putXElements(2, restartedTestee);
			readAllElementsInCache(restartedTestee);
			commitThenCloseTransaction();
			waitForStatisticsSamples(restartedTestee.config.statsShortSamplingTimeInSeconds());
			assertThat(restartedTestee.shortTimeDiskGets(STATS_ENABLED_CACHE)).isEqualTo(2);
		} finally {
			restartedTestee.manager.shutdown();
		}
	}
	
	@Test
	public void testShortTimeDiskGetsWhenGetAfterUpdateInAnotherTransaction() throws Exception {
		EhCacheStatisticsImpl testee = testeeWithConfig(new TestingEhCacheConfiguration()
			.withMaxMemoryInMB(10)
			.withStatsShortSamplingTimeInSeconds(1));
			
		EhCacheStatisticsImpl restartedTestee = putXElementsThenRestartWithSameConfig(2, testee);
		
		try {
			startStatisticsSampling(restartedTestee);
			readAllElementsInCache(restartedTestee);
			commitThenCloseTransaction();
			TransactionManagerServices.getTransactionManager().begin();
			putXElements(2, restartedTestee);
			readAllElementsInCache(restartedTestee);
			commitThenCloseTransaction();
			waitForStatisticsSamples(restartedTestee.config.statsShortSamplingTimeInSeconds());
			assertThat(restartedTestee.shortTimeDiskGets(STATS_ENABLED_CACHE)).isEqualTo(0);
		} finally {
			restartedTestee.manager.shutdown();
		}
	}
	
	@Test
	public void testShortTimeDiskGetsWhenPutOnly() throws Exception {
		EhCacheStatisticsImpl testee = testeeWithConfig(new TestingEhCacheConfiguration()
			.withMaxMemoryInMB(10)
			.withStatsShortSamplingTimeInSeconds(1));
			
		try {
			startStatisticsSampling(testee);
			putXElements(2, testee);
			commitThenCloseTransaction();
			waitForStatisticsSamples(testee.config.statsShortSamplingTimeInSeconds());
			assertThat(testee.shortTimeDiskGets(STATS_ENABLED_CACHE)).isEqualTo(0);
		} finally {
			testee.manager.shutdown();
		}
	}
	
	@Test
	public void testShortTimeDiskGetsWhenPutThenGet() throws Exception {
		EhCacheStatisticsImpl testee = testeeWithConfig(new TestingEhCacheConfiguration()
			.withMaxMemoryInMB(10)
			.withStatsShortSamplingTimeInSeconds(1));
			
		try {
			startStatisticsSampling(testee);
			putXElements(5, testee);
			readAllElementsInCache(testee);
			commitThenCloseTransaction();
			waitForStatisticsSamples(testee.config.statsShortSamplingTimeInSeconds());
			assertThat(testee.shortTimeDiskGets(STATS_ENABLED_CACHE)).isEqualTo(0);
		} finally {
			testee.manager.shutdown();
		}
	}
	
	@Test
	public void testShortTimeDiskGetsWhenGetOnDiskAndInHeap() throws Exception {
		EhCacheStatisticsImpl testee = testeeWithConfig(new TestingEhCacheConfiguration()
			.withMaxMemoryInMB(10)
			.withStatsShortSamplingTimeInSeconds(1));

		EhCacheStatisticsImpl restartedTestee = putXElementsThenRestartWithSameConfig(2, testee);
		
		try {
			startStatisticsSampling(restartedTestee);
			putXElements(3, 5, restartedTestee);
			readAllElementsInCache(restartedTestee);
			commitThenCloseTransaction();
			waitForStatisticsSamples(restartedTestee.config.statsShortSamplingTimeInSeconds());
			assertThat(restartedTestee.shortTimeDiskGets(STATS_ENABLED_CACHE)).isEqualTo(2);
		} finally {
			restartedTestee.manager.shutdown();
		}
	}
	
	@Test
	public void testShortTimeDiskGetsWhenAccessesAreOutOfScope() throws Exception {
		EhCacheStatisticsImpl testee = testeeWithConfig(new TestingEhCacheConfiguration()
			.withMaxMemoryInMB(10)
			.withStatsShortSamplingTimeInSeconds(1));
			
		EhCacheStatisticsImpl restartedTestee = putXElementsThenRestartWithSameConfig(2, testee);
		
		try {
			startStatisticsSampling(restartedTestee);
			readAllElementsInCache(restartedTestee);
			commitThenCloseTransaction();
			waitForStatisticsSamples(restartedTestee.config.statsShortSamplingTimeInSeconds());
			waitForStatisticsSamples(restartedTestee.config.statsShortSamplingTimeInSeconds());
			assertThat(restartedTestee.shortTimeDiskGets(STATS_ENABLED_CACHE)).isEqualTo(0);
		} finally {
			restartedTestee.manager.shutdown();
		}
	}
	
	@Test
	public void testLongTimeDiskGetsWhenAccessDuringManySample() throws Exception {
		int oneSampleInSeconds = 2;
		int waitBetweenReadsInSeconds = 1;
		int totalReadTimeInSeconds = 5;

		EhCacheStatisticsImpl testee = testeeWithConfig(new TestingEhCacheConfiguration()
			.withMaxMemoryInMB(10)
			.withStatsShortSamplingTimeInSeconds(oneSampleInSeconds)
			.withStatsLongSamplingTimeInSeconds(totalReadTimeInSeconds));
			
		EhCacheStatisticsImpl restartedTestee = putXElementsThenRestartWithSameConfig(5, testee);
		
		try {
			startStatisticsSampling(restartedTestee);
			Thread.sleep(100); // First read must be in first sample
			readAllElementsInCacheWithWait(restartedTestee, waitBetweenReadsInSeconds);
			commitThenCloseTransaction();
			assertThat(restartedTestee.longTimeDiskGets(STATS_ENABLED_CACHE)).isEqualTo(5);
		} finally {
			restartedTestee.manager.shutdown();
		}
	}

	private void readAllElementsInCacheWithWait(EhCacheStatisticsImpl testee, int waitInSeconds) throws Exception {
		Cache cache = testee.manager.getStore(STATS_ENABLED_CACHE);
		for (Object key : cache.getKeys()) {
			cache.get(key);
			Thread.sleep(TimeUnit.SECONDS.toMillis(waitInSeconds));
		}
	}
	
	private void readAllElementsInCache(EhCacheStatisticsImpl testee) {
		Cache cache = testee.manager.getStore(STATS_ENABLED_CACHE);
		for (Object key : cache.getKeys()) {
			cache.get(key);
		}
	}
	
	private void removeAllElementsInCache(EhCacheStatisticsImpl testee) {
		testee.manager.getStore(STATS_ENABLED_CACHE).removeAll();
	}

	private EhCacheStatisticsImpl putXElementsThenRestartWithSameConfig(int elementsCount, EhCacheStatisticsImpl testee)
			throws Exception {
		
		EhCacheConfiguration previousConfigReference = testee.config;
		putXElements(elementsCount, testee);
		commitThenCloseTransaction();
		testee.manager.shutdown();

		return testeeWithConfig(previousConfigReference);
	}

	private void putXElements(int elementsCount, EhCacheStatisticsImpl testee) {
		putXElements(0, elementsCount, testee);
	}

	private void putXElements(int startIndex, int elementsCount, EhCacheStatisticsImpl testee) {
		int putUntil = elementsCount + startIndex;
		for (int putCount = startIndex; putCount < putUntil; putCount++) {
			testee.manager.getStore(STATS_ENABLED_CACHE).put(new Element("a" + putCount, "b" + putCount));
		}
	}

	private void startStatisticsSampling(EhCacheStatisticsImpl testee) {
		try {
			testee.shortTimeDiskGets(STATS_ENABLED_CACHE);
		} catch(StatisticsNotAvailableException e) {
			// expected
		}
	}

	private void waitForStatisticsSamples(int samplingTimeInSeconds) throws Exception {
		Thread.sleep(TimeUnit.SECONDS.toMillis(samplingTimeInSeconds) + 20);
	}

	private EhCacheStatisticsImpl testeeWithConfig(EhCacheConfiguration config) throws Exception {
		TransactionManagerServices.getTransactionManager().begin();
		cacheManager = new ObjectStoreManager(configurationService, config, logger);
		return new EhCacheStatisticsImpl(config, cacheManager);
	}
}