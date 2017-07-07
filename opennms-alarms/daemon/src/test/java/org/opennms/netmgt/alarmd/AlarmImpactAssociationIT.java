/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017-2017 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2017 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.alarmd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.opennms.core.test.OpenNMSJUnit4ClassRunner;
import org.opennms.core.test.db.annotations.JUnitTemporaryDatabase;
import org.opennms.netmgt.dao.api.AlarmDao;
import org.opennms.netmgt.dao.api.DistPollerDao;
import org.opennms.netmgt.model.OnmsAlarm;
import org.opennms.test.JUnitConfigurationEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

@RunWith(OpenNMSJUnit4ClassRunner.class)
@ContextConfiguration(locations={
        "classpath:/META-INF/opennms/applicationContext-soa.xml",
        "classpath:/META-INF/opennms/applicationContext-commonConfigs.xml",
        "classpath:/META-INF/opennms/applicationContext-minimal-conf.xml",
        "classpath:/META-INF/opennms/applicationContext-dao.xml",
        "classpath*:/META-INF/opennms/component-dao.xml",
        "classpath:/META-INF/opennms/applicationContext-daemon.xml",
        "classpath:/META-INF/opennms/mockEventIpcManager.xml",
        "classpath:/META-INF/opennms/applicationContext-alarmd.xml"
})
@JUnitConfigurationEnvironment
@JUnitTemporaryDatabase(reuseDatabase=false)
public class AlarmImpactAssociationIT {

    @Autowired
    private AlarmDao m_alarmDao;

    @Autowired
    private DistPollerDao m_distPollerDao;

    @Test
    @Transactional
    public void canAssociateImpactingAlarms() {
        // Create and save a first alarm
        OnmsAlarm linkDownAlarmOnR1 = new OnmsAlarm();
        linkDownAlarmOnR1.setDistPoller(m_distPollerDao.whoami());
        linkDownAlarmOnR1.setCounter(1);
        linkDownAlarmOnR1.setUei("linkDown");
        m_alarmDao.save(linkDownAlarmOnR1);
        verifyInitialState(linkDownAlarmOnR1);

        // Create and save a second alarm
        OnmsAlarm linkDownAlarmOnR2 = new OnmsAlarm();
        linkDownAlarmOnR2.setDistPoller(m_distPollerDao.whoami());
        linkDownAlarmOnR2.setCounter(1);
        linkDownAlarmOnR2.setUei("linkDown");

        // Associate
        associate(linkDownAlarmOnR1, linkDownAlarmOnR2);

        // Verify
        verifyAssociation(linkDownAlarmOnR1, linkDownAlarmOnR2);

        // Now reload the entities
        linkDownAlarmOnR1 = m_alarmDao.get(linkDownAlarmOnR1.getId());
        linkDownAlarmOnR2 = m_alarmDao.get(linkDownAlarmOnR2.getId());

        // And verify again
        verifyAssociation(linkDownAlarmOnR1, linkDownAlarmOnR2);
    }

    void associate(OnmsAlarm cause, OnmsAlarm impacted) {
        cause.getImpacts().add(impacted);
        cause.setCause(true);
        impacted.getCauses().add(cause);
        impacted.setImpacted(true);
        m_alarmDao.saveOrUpdate(cause);
        m_alarmDao.saveOrUpdate(impacted);
    }

    void verifyInitialState(OnmsAlarm alarm) {
        assertFalse("cause should default to false", alarm.isCause());
        assertFalse("impacted should default to false", alarm.isImpacted());
        assertEquals(0, alarm.getImpacts().size());
        assertEquals(0, alarm.getCauses().size());
    }

    void verifyAssociation(OnmsAlarm cause, OnmsAlarm impacted) {
        assertEquals(1, cause.getImpacts().size());
        assertEquals(0, cause.getCauses().size());
        assertTrue(cause.isCause());
        assertFalse(cause.isImpacted());

        assertEquals(0, impacted.getImpacts().size());
        assertEquals(1, impacted.getCauses().size());
        assertFalse(impacted.isCause());
        assertTrue(impacted.isImpacted());
    }
}
