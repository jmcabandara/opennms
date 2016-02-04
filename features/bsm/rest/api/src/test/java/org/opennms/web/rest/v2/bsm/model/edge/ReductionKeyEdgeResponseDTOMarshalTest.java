/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2015 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2015 The OpenNMS Group, Inc.
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

package org.opennms.web.rest.v2.bsm.model.edge;

import static org.opennms.web.rest.v2.bsm.model.TestHelper.createMapFunctionDTO;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.junit.runners.Parameterized;
import org.opennms.core.test.xml.MarshalAndUnmarshalTest;
import org.opennms.netmgt.bsm.service.model.Status;
import org.opennms.web.rest.api.ApiVersion;
import org.opennms.web.rest.api.ResourceLocation;
import org.opennms.web.rest.v2.bsm.model.MapFunctionType;

public class ReductionKeyEdgeResponseDTOMarshalTest extends MarshalAndUnmarshalTest<ReductionKeyEdgeResponseDTO> {

    public ReductionKeyEdgeResponseDTOMarshalTest(Class<ReductionKeyEdgeResponseDTO> clazz, ReductionKeyEdgeResponseDTO sampleObject, String sampleJson, String sampleXml) {
        super(clazz, sampleObject, sampleJson, sampleXml);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() throws IOException {
        ReductionKeyEdgeResponseDTO edge = new ReductionKeyEdgeResponseDTO();
        edge.setLocation(new ResourceLocation(ApiVersion.Version2, "business-services", "edges", "1"));
        edge.setReductionKey("my-custom-reduction-key");
        edge.setOperationalStatus(Status.WARNING);
        edge.setMapFunction(createMapFunctionDTO(MapFunctionType.SetTo, new String[]{"key1", "value1"}));
        edge.setId(1);
        edge.setWeight(17);

        return Arrays.asList(new Object[][]{{
                ReductionKeyEdgeResponseDTO.class,
                edge,
                "{" +
                "  \"id\" : 1," +
                "  \"operationalStatus\" : \"WARNING\"," +
                "  \"mapFunction\" : {" +
                "       \"type\" : \"SetTo\"," +
                "       \"properties\" : {" +
                "           \"key1\" : \"value1\"" +
                "       }" +
                "   }," +
                "  \"location\" : \"/api/v2/business-services/edges/1\"," +
                "  \"reductionKeys\" : [" +
                "       \"my-custom-reduction-key\"" +
                "   ]," +
                "  \"weight\" : 17," +
                "}",
                "<reduction-key-edge>\n" +
                "   <id>1</id>\n" +
                "   <operational-status>WARNING</operational-status>\n" +
                "   <map-function>\n" +
                "       <type>SetTo</type>\n" +
                "       <properties>\n" +
                "           <entry>\n" +
                "              <key>key1</key>\n" +
                "               <value>value1</value>\n" +
                "           </entry>\n" +
                "       </properties>\n" +
                "   </map-function>\n" +
                "   <location>/api/v2/business-services/edges/1</location>\n" +
                "   <reductionKeys>\n" +
                "       <reductionKey>my-custom-reduction-key</reductionKey>\n" +
                "   </reductionKeys>\n" +
                "   <weight>17</weight>\n" +
                "</reduction-key-edge>"
        }});
    }
}