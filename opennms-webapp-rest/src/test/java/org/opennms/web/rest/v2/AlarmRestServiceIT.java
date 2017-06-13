/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2008-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.web.rest.v2;

import static org.opennms.web.svclayer.support.DefaultTroubleTicketProxy.createEventBuilder;

import java.util.Date;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opennms.core.test.MockLogAppender;
import org.opennms.core.test.OpenNMSJUnit4ClassRunner;
import org.opennms.core.test.db.annotations.JUnitTemporaryDatabase;
import org.opennms.core.test.rest.AbstractSpringJerseyRestTestCase;
import org.opennms.netmgt.dao.DatabasePopulator;
import org.opennms.netmgt.dao.mock.MockEventIpcManager;
import org.opennms.netmgt.events.api.EventConstants;
import org.opennms.netmgt.model.NetworkBuilder;
import org.opennms.netmgt.model.OnmsAlarm;
import org.opennms.netmgt.model.OnmsCategory;
import org.opennms.netmgt.model.OnmsEvent;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.OnmsNode.NodeType;
import org.opennms.netmgt.model.OnmsServiceType;
import org.opennms.netmgt.model.OnmsSeverity;
import org.opennms.netmgt.model.events.EventBuilder;
import org.opennms.test.JUnitConfigurationEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.ImmutableMap;

@RunWith(OpenNMSJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration(locations={
        "classpath:/META-INF/opennms/applicationContext-soa.xml",
        "classpath:/META-INF/opennms/applicationContext-commonConfigs.xml",
        "classpath:/META-INF/opennms/applicationContext-minimal-conf.xml",
        "classpath:/META-INF/opennms/applicationContext-dao.xml",
        "classpath*:/META-INF/opennms/component-service.xml",
        "classpath*:/META-INF/opennms/component-dao.xml",
        "classpath:/META-INF/opennms/applicationContext-databasePopulator.xml",
        "classpath:/META-INF/opennms/mockEventIpcManager.xml",
        "classpath:/applicationContext-troubleTicketer.xml",
        "file:src/main/webapp/WEB-INF/applicationContext-svclayer.xml",
        "file:src/main/webapp/WEB-INF/applicationContext-cxf-common.xml"
})
@JUnitConfigurationEnvironment
@JUnitTemporaryDatabase
@Transactional
public class AlarmRestServiceIT extends AbstractSpringJerseyRestTestCase {

    public AlarmRestServiceIT() {
        super(CXF_REST_V2_CONTEXT_PATH);
    }

    @Autowired
    private DatabasePopulator m_databasePopulator;

    @Autowired
    private MockEventIpcManager m_eventMgr;

    @Override
    protected void afterServletStart() throws Exception {
        MockLogAppender.setupLogging(true, "DEBUG");

        final OnmsCategory linux = createCategory("Linux");
        final OnmsCategory macOS = createCategory("macOS");

        final OnmsServiceType icmp = new OnmsServiceType("ICMP");
        m_databasePopulator.getServiceTypeDao().save(icmp);
        m_databasePopulator.getServiceTypeDao().flush();

        final NetworkBuilder builder = new NetworkBuilder();

        final OnmsNode node1 = createNode(builder, "server01", "192.168.1.1", linux);
        final OnmsNode node2 = createNode(builder, "server02", "192.168.1.2", macOS);

        createAlarm(node1, "uei.opennms.org/test/somethingWentWrong", OnmsSeverity.MAJOR);
        createAlarm(node1, "uei.opennms.org/test/somethingIsStillHappening", OnmsSeverity.WARNING);
        createAlarm(node1, "uei.opennms.org/test/somethingIsOkNow", OnmsSeverity.NORMAL);

        createAlarm(node2, "uei.opennms.org/test/somethingWentWrong", OnmsSeverity.MAJOR);
        createAlarm(node2, "uei.opennms.org/test/somethingIsStillHappening", OnmsSeverity.WARNING);
        createAlarm(node2, "uei.opennms.org/test/somethingIsOkNow", OnmsSeverity.NORMAL);
    }

    @Test
    public void testAlarms() throws Exception {
        String url = "/alarms";

        executeQueryAndVerify("limit=0", 6);

        executeQueryAndVerify("limit=0&_s=severity==NORMAL", 2);

        executeQueryAndVerify("limit=0&_s=severity==WARNING", 2);

        sendRequest(GET, url, parseParamData("limit=0&_s=severity==CRITICAL"), 204);

        executeQueryAndVerify("limit=0&_s=severity=gt=NORMAL", 4);

        executeQueryAndVerify("limit=0&_s=severity=gt=NORMAL;node.label==server01", 2);
    }

    @Test
    public void testCollectionsAndMappings() throws Exception {
        executeQueryAndVerify("_s=node.categories.name==Linux", 3);
        executeQueryAndVerify("_s=categoryName==Linux", 3);
        executeQueryAndVerify("_s=uei==*somethingWentWrong", 2);
        executeQueryAndVerify("_s=uei==*somethingWentWrong;categoryName==Linux", 1);

        executeQueryAndVerify("_s=uei==*something*", 6);
        executeQueryAndVerify("_s=uei!=*somethingIs*", 2);

        // Verify service queries
        executeQueryAndVerify("_s=service==ICMP", 6);
        executeQueryAndVerify("_s=service!=ICMP", 0);
        executeQueryAndVerify("_s=service==SNMP", 0);
        executeQueryAndVerify("_s=service==*MP", 6);

        // Verify ip address queries
        executeQueryAndVerify("_s=ipAddr==192.168.1.1", 3);
        executeQueryAndVerify("_s=ipAddr==192.168.1.2", 3);
        executeQueryAndVerify("_s=ipAddr==127.0.0.1", 0);
        executeQueryAndVerify("_s=ipAddr!=127.0.0.1", 6);

        // TODO: These should also work
        //executeQueryAndVerify("_s=ipInterface.ipAddress==192.168.1.1", 3);
        //executeQueryAndVerify("_s=ipInterface.ipAddress==192.168.1.2", 3);
        //executeQueryAndVerify("_s=ipInterface.ipAddress==127.0.0.1", 0);
        //executeQueryAndVerify("_s=ipInterface.ipAddress!=127.0.0.1", 6);

        executeQueryAndVerify("_s=node.label==server01", 3);
        executeQueryAndVerify("_s=node.label!=server01", 3);
        executeQueryAndVerify("_s=(node.label==server01,node.label==server02)", 6);
        executeQueryAndVerify("_s=node.label!=\u0000", 6);

        executeQueryAndVerify("_s=location.locationName==Default", 6);
    }

    @Test
    public void testOrderBy() throws Exception {
        String url = "/alarms";

        // Test orderby for properties of OnmsAlarm
        sendRequest(GET, url, parseParamData("orderBy=alarmAckTime"), 200);
        sendRequest(GET, url, parseParamData("orderBy=alarmAckUser"), 200);
        sendRequest(GET, url, parseParamData("orderBy=alarmType"), 200);
        sendRequest(GET, url, parseParamData("orderBy=applicationDN"), 200);
        sendRequest(GET, url, parseParamData("orderBy=clearKey"), 200);
        sendRequest(GET, url, parseParamData("orderBy=counter"), 200);
        sendRequest(GET, url, parseParamData("orderBy=description"), 200);
        sendRequest(GET, url, parseParamData("orderBy=details"), 200);
        sendRequest(GET, url, parseParamData("orderBy=distPoller"), 200);
        // TODO: Cannot sort by parms since they are all stored in one database column
        //sendRequest(GET, url, parseParamData("orderBy=eventParameters"), 200);
        sendRequest(GET, url, parseParamData("orderBy=eventParms"), 200);
        sendRequest(GET, url, parseParamData("orderBy=firstAutomationTime"), 200);
        sendRequest(GET, url, parseParamData("orderBy=firstEventTime"), 200);
        sendRequest(GET, url, parseParamData("orderBy=id"), 200);
        sendRequest(GET, url, parseParamData("orderBy=ifIndex"), 200);
        sendRequest(GET, url, parseParamData("orderBy=ipAddr"), 200);
        sendRequest(GET, url, parseParamData("orderBy=lastAutomationTime"), 200);
        sendRequest(GET, url, parseParamData("orderBy=lastEvent"), 200);
        sendRequest(GET, url, parseParamData("orderBy=lastEventTime"), 200);
        sendRequest(GET, url, parseParamData("orderBy=logMsg"), 200);
        sendRequest(GET, url, parseParamData("orderBy=managedObjectInstance"), 200);
        sendRequest(GET, url, parseParamData("orderBy=managedObjectType"), 200);
        sendRequest(GET, url, parseParamData("orderBy=mouseOverText"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node"), 200);
        sendRequest(GET, url, parseParamData("orderBy=operInstruct"), 200);
        sendRequest(GET, url, parseParamData("orderBy=ossPrimaryKey"), 200);
        sendRequest(GET, url, parseParamData("orderBy=qosAlarmState"), 200);
        sendRequest(GET, url, parseParamData("orderBy=reductionKey"), 200);
        sendRequest(GET, url, parseParamData("orderBy=reductionKeyMemo"), 200);
        sendRequest(GET, url, parseParamData("orderBy=serviceType"), 200);
        sendRequest(GET, url, parseParamData("orderBy=severity"), 200);
        // TODO: Figure out how to do this, OnmsSeverity is an enum
        //sendRequest(GET, url, parseParamData("orderBy=severity.id"), 200);
        //sendRequest(GET, url, parseParamData("orderBy=severity.label"), 200);
        sendRequest(GET, url, parseParamData("orderBy=stickyMemo"), 200);
        sendRequest(GET, url, parseParamData("orderBy=suppressedTime"), 200);
        sendRequest(GET, url, parseParamData("orderBy=suppressedUntil"), 200);
        sendRequest(GET, url, parseParamData("orderBy=suppressedUser"), 200);
        // TODO: I can't figure out the bean property name for these properties
        //sendRequest(GET, url, parseParamData("orderBy=tticketId"), 200);
        //sendRequest(GET, url, parseParamData("orderBy=tticketState"), 200);
        sendRequest(GET, url, parseParamData("orderBy=uei"), 200);
        sendRequest(GET, url, parseParamData("orderBy=x733AlarmType"), 200);
        sendRequest(GET, url, parseParamData("orderBy=x733ProbableCause"), 200);

        // Test orderby for properties of OnmsNode
        sendRequest(GET, url, parseParamData("orderBy=node.assetRecord"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.categories"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.cdpElement"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.createTime"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.foreignId"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.foreignSource"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.id"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.isisElement"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.label"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.labelSource"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.lastCapsdPoll"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.lldpElement"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.lldpLinks"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.location"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.netBiosDomain"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.netBiosName"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.operatingSystem"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.ospfElement"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.parent"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.pathElement"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.snmpInterfaces"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.sysContact"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.sysDescription"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.sysLocation"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.sysName"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.sysObjectId"), 200);
        sendRequest(GET, url, parseParamData("orderBy=node.type"), 200);
    }

    @Test
    @Ignore
    public void verifyTicketerMustBeEnabled() throws Exception {
        final OnmsAlarm alarm = m_databasePopulator.getAlarmDao().findAll().stream()
                .filter(a -> a.getSeverity().isGreaterThanOrEqual(OnmsSeverity.NORMAL) && a.getAlarmAckTime() == null)
                .findFirst().orElseThrow(() -> new IllegalStateException("No unacknowledged alarm with severity >= Normal found"));
        String url = "/alarms/";

        // TroubleTicketerPlugin is disabled, therefore it should fail
        sendPost(url + alarm.getId() + "/ticket/create", "", 501);
        sendPost(url + alarm.getId() + "/ticket/update", "", 501);
        sendPost(url + alarm.getId() + "/ticket/close", "", 501);

        // enable TroubleTicketeRPlugin and try again
        System.setProperty("opennms.alarmTroubleTicketEnabled", "true");
        verifyAnticipatedEvents();

        anticipateEvent(createEventBuilder(EventConstants.TROUBLETICKET_CREATE_UEI, alarm, ImmutableMap.of("user", "ulf")));
        sendPost(url + alarm.getId() + "/ticket/create", "", 202);
        verifyAnticipatedEvents();

        anticipateEvent(createEventBuilder(EventConstants.TROUBLETICKET_UPDATE_UEI, alarm, null));
        sendPost(url + alarm.getId() + "/ticket/update", "", 202);
        verifyAnticipatedEvents();

        anticipateEvent(createEventBuilder(EventConstants.TROUBLETICKET_CLOSE_UEI, alarm, null));
        sendPost(url + alarm.getId() + "/ticket/close", "", 202);
        verifyAnticipatedEvents();
    }

    private void anticipateEvent(EventBuilder eventBuilder) {
        m_eventMgr.getEventAnticipator().anticipateEvent(eventBuilder.getEvent());
    }

    private void verifyAnticipatedEvents() {
        m_eventMgr.getEventAnticipator().verifyAnticipated(10000, 0, 0, 0, 0);
    }

    private OnmsNode createNode(final NetworkBuilder builder, final String label, final String ipAddress, final OnmsCategory category) {
        builder.addNode(label).setForeignSource("JUnit").setForeignId(label).setType(NodeType.ACTIVE);
        builder.addCategory(category);
        builder.setBuilding("HQ");
        builder.addSnmpInterface(1)
        .setCollectionEnabled(true)
        .setIfOperStatus(1)
        .setIfSpeed(10000000)
        .setIfName("eth0")
        .setIfType(6)
        .setPhysAddr("C9D2DFC7CB68")
        .addIpInterface(ipAddress).setIsManaged("M").setIsSnmpPrimary("S");
        builder.addService(m_databasePopulator.getServiceTypeDao().findByName("ICMP"));
        final OnmsNode node = builder.getCurrentNode();
        m_databasePopulator.getNodeDao().save(node);
        return node;
    }

    private OnmsCategory createCategory(final String categoryName) {
        final OnmsCategory cat = new OnmsCategory(categoryName);
        m_databasePopulator.getCategoryDao().save(cat);
        m_databasePopulator.getCategoryDao().flush();
        return cat;
    }

    private void createAlarm(final OnmsNode node, final String eventUei, final OnmsSeverity severity) {
        final OnmsEvent event = new OnmsEvent();
        event.setDistPoller(m_databasePopulator.getDistPollerDao().whoami());
        event.setEventCreateTime(new Date());
        event.setEventDisplay("Y");
        event.setEventHost("127.0.0.1");
        event.setEventLog("Y");
        event.setEventSeverity(1);
        event.setEventSource("JUnit");
        event.setEventTime(new Date());
        event.setEventUei(eventUei);
        event.setIpAddr(node.getIpInterfaces().iterator().next().getIpAddress());
        event.setNode(node);
        event.setServiceType(m_databasePopulator.getServiceTypeDao().findByName("ICMP"));
        event.setEventSeverity(severity.getId());
        m_databasePopulator.getEventDao().save(event);
        m_databasePopulator.getEventDao().flush();

        final OnmsAlarm alarm = new OnmsAlarm();
        alarm.setDistPoller(m_databasePopulator.getDistPollerDao().whoami());
        alarm.setUei(event.getEventUei());
        alarm.setAlarmType(1);
        alarm.setNode(node);
        alarm.setDescription("This is a test alarm");
        alarm.setLogMsg("this is a test alarm log message");
        alarm.setCounter(1);
        alarm.setIpAddr(node.getIpInterfaces().iterator().next().getIpAddress());
        alarm.setSeverity(severity);
        alarm.setFirstEventTime(event.getEventTime());
        alarm.setLastEvent(event);
        alarm.setEventParms(event.getEventParms());
        alarm.setServiceType(m_databasePopulator.getServiceTypeDao().findByName("ICMP"));
        m_databasePopulator.getAlarmDao().save(alarm);
        m_databasePopulator.getAlarmDao().flush();
    }

    private void executeQueryAndVerify(String query, int totalCount) throws Exception {
        if (totalCount == 0) {
            sendRequest(GET, "/alarms", parseParamData(query), 204);
        } else {
            JSONObject object = new JSONObject(sendRequest(GET, "/alarms", parseParamData(query), 200));
            Assert.assertEquals(totalCount, object.getInt("totalCount"));
        }
    }

}
