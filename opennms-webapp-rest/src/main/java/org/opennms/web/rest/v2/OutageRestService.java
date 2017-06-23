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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.Path;
import javax.ws.rs.core.UriInfo;

import org.opennms.core.config.api.JaxbListWrapper;
import org.opennms.core.criteria.Alias.JoinType;
import org.opennms.core.criteria.CriteriaBuilder;
import org.opennms.netmgt.dao.api.OutageDao;
import org.opennms.netmgt.model.OnmsOutage;
import org.opennms.netmgt.model.OnmsOutageCollection;
import org.opennms.web.rest.support.Aliases;
import org.opennms.web.rest.support.CriteriaBehavior;
import org.opennms.web.rest.support.CriteriaBehaviors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Basic Web Service using REST for {@link OnmsOutage} entity.
 *
 * @author <a href="seth@opennms.org">Seth Leger</a>
 */
@Component
@Path("outages")
@Transactional
public class OutageRestService extends AbstractDaoRestService<OnmsOutage,OnmsOutage,Integer,Integer> {

    @Autowired
    private OutageDao m_dao;

    @Override
    protected OutageDao getDao() {
        return m_dao;
    }

    @Override
    protected Class<OnmsOutage> getDaoClass() {
        return OnmsOutage.class;
    }

    @Override
    protected Class<OnmsOutage> getQueryBeanClass() {
        return OnmsOutage.class;
    }

    @Override
    protected CriteriaBuilder getCriteriaBuilder(UriInfo uriInfo) {
        final CriteriaBuilder builder = new CriteriaBuilder(OnmsOutage.class);
        // 1st level JOINs
        builder.alias("monitoredService", "monitoredService", JoinType.LEFT_JOIN);
        builder.alias("serviceLostEvent", "serviceLostEvent", JoinType.LEFT_JOIN);
        builder.alias("serviceRegainedEvent", "serviceRegainedEvent", JoinType.LEFT_JOIN); 

        // 2nd level JOINs
        builder.alias("monitoredService.ipInterface", Aliases.ipInterface.toString(), JoinType.LEFT_JOIN);
        builder.alias("monitoredService.serviceType", Aliases.serviceType.toString(), JoinType.LEFT_JOIN);
        builder.alias("serviceLostEvent.distPoller", Aliases.distPoller.toString(), JoinType.LEFT_JOIN);

        // 3rd level JOINs
        builder.alias(Aliases.ipInterface.prop("node"), Aliases.node.toString(), JoinType.LEFT_JOIN);

        // 4th level JOINs
        builder.alias(Aliases.node.prop("assetRecord"), Aliases.assetRecord.toString(), JoinType.LEFT_JOIN);
        // TODO: Only add this alias when filtering by category so that we can specify a join condition
        //builder.alias(Aliases.node.prop("categories"), Aliases.category.toString(), JoinType.LEFT_JOIN);
        builder.alias(Aliases.node.prop("location"), Aliases.location.toString(), JoinType.LEFT_JOIN);

        // NOTE: Left joins on a toMany relationship need a join condition so that only one row is returned

        // Order by ID by default
        builder.orderBy("id").desc();

        return builder;
    }

    @Override
    protected JaxbListWrapper<OnmsOutage> createListWrapper(Collection<OnmsOutage> list) {
        return new OnmsOutageCollection(list);
    }

    @Override
    protected Map<String,CriteriaBehavior<?>> getCriteriaBehaviors() {
        final Map<String,CriteriaBehavior<?>> map = new HashMap<>();

        // Root alias
        map.putAll(CriteriaBehaviors.OUTAGE_BEHAVIORS);

        // 1st level JOINs
        map.putAll(CriteriaBehaviors.ALARM_BEHAVIORS);

        // 2nd level JOINs
        map.putAll(CriteriaBehaviors.DIST_POLLER_BEHAVIORS);
        map.putAll(CriteriaBehaviors.IP_INTERFACE_BEHAVIORS);
        map.putAll(CriteriaBehaviors.SERVICE_TYPE_BEHAVIORS);

        // 3rd level JOINs
        map.putAll(CriteriaBehaviors.NODE_BEHAVIORS);

        // 4th level JOINs
        map.putAll(CriteriaBehaviors.ASSET_RECORD_BEHAVIORS);
        map.putAll(CriteriaBehaviors.MONITORING_LOCATION_BEHAVIORS);
        map.putAll(CriteriaBehaviors.NODE_CATEGORY_BEHAVIORS);

        // TODO: Figure out how to join in the snmpInterface fields
        //map.putAll(CriteriaBehaviors.SNMP_INTERFACE_BEHAVIORS);

        return map;
    }

    @Override
    protected OnmsOutage doGet(UriInfo uriInfo, Integer id) {
        return getDao().get(id);
    }

}
