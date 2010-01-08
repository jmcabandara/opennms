/*
 * This file is part of the OpenNMS(R) Application.
 *
 * OpenNMS(R) is Copyright (C) 2009 The OpenNMS Group, Inc.  All rights reserved.
 * OpenNMS(R) is a derivative work, containing both original code, included code and modified
 * code that was published under the GNU General Public License. Copyrights for modified
 * and included code are below.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * Original code base Copyright (C) 1999-2001 Oculan Corp.  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * For more information contact:
 * OpenNMS Licensing       <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 */
package org.opennms.sms.monitor;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.opennms.sms.reflector.smsservice.MobileMsgResponseMatchers.and;
import static org.opennms.sms.reflector.smsservice.MobileMsgResponseMatchers.isSms;
import static org.opennms.sms.reflector.smsservice.MobileMsgResponseMatchers.isUssd;
import static org.opennms.sms.reflector.smsservice.MobileMsgResponseMatchers.textMatches;
import static org.opennms.sms.reflector.smsservice.MobileMsgResponseMatchers.ussdStatusIs;

import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.opennms.core.tasks.DefaultTaskCoordinator;
import org.opennms.protocols.rt.Messenger;
import org.opennms.sms.monitor.internal.config.MobileSequenceConfig;
import org.opennms.sms.reflector.smsservice.MobileMsgRequest;
import org.opennms.sms.reflector.smsservice.MobileMsgResponse;
import org.opennms.sms.reflector.smsservice.MobileMsgResponseCallback;
import org.opennms.sms.reflector.smsservice.MobileMsgSequence;
import org.opennms.sms.reflector.smsservice.MobileMsgTrackerImpl;
import org.opennms.sms.reflector.smsservice.SmsResponse;
import org.opennms.sms.reflector.smsservice.UssdResponse;
import org.smslib.InboundMessage;
import org.smslib.USSDDcs;
import org.smslib.USSDResponse;
import org.smslib.USSDSessionStatus;

/**
 * MobileMsgTrackerTeste
 *
 * @author brozow
 */
public class MobileMsgSequenceBuilderTest {
    
	private static final String PHONE_NUMBER = "+19195551212";
    public static final String TMOBILE_RESPONSE = "37.28 received on 08/31/09. For continued service through 10/28/09, please pay 79.56 by 09/28/09.    ";
    public static final String TMOBILE_USSD_MATCH = "^.*[\\d\\.]+ received on \\d\\d/\\d\\d/\\d\\d. For continued service through \\d\\d/\\d\\d/\\d\\d, please pay [\\d\\.]+ by \\d\\d/\\d\\d/\\d\\d.*$";

	/**
     * @author brozow
     *
     */
    public class TestMessenger implements Messenger<MobileMsgRequest, MobileMsgResponse> {
        
        Queue<MobileMsgResponse> m_q;

        /* (non-Javadoc)
         * @see org.opennms.protocols.rt.Messenger#sendRequest(java.lang.Object)
         */
        public void sendRequest(MobileMsgRequest request) throws Exception {
            // fake send this
            request.setSendTimestamp(System.currentTimeMillis());
        }

        /* (non-Javadoc)
         * @see org.opennms.protocols.rt.Messenger#start(java.util.Queue)
         */
        public void start(Queue<MobileMsgResponse> q) {
            m_q = q;
        }
        
        public void sendTestResponse(MobileMsgResponse response) {
            m_q.offer(response);
        }

        /**
         * @param msg1
         */
        public void sendTestResponse(InboundMessage msg) {
            sendTestResponse(new SmsResponse(msg, System.currentTimeMillis()));
        }

        /**
         * @param response
         */
        public void sendTestResponse(String gatewayId, USSDResponse response) {
            sendTestResponse(new UssdResponse(gatewayId, response, System.currentTimeMillis()));
        }

    }
    
    public static class TestCallback implements MobileMsgResponseCallback {
        
        CountDownLatch m_latch = new CountDownLatch(1);
        AtomicReference<MobileMsgResponse> m_response = new AtomicReference<MobileMsgResponse>(null);

        
        MobileMsgResponse getResponse() throws InterruptedException {
            m_latch.await();
            return m_response.get();
        }

        /* (non-Javadoc)
         * @see org.opennms.sms.reflector.smsservice.SmsResponseCallback#handleError(org.opennms.sms.reflector.smsservice.SmsRequest, java.lang.Throwable)
         */
        public void handleError(MobileMsgRequest request, Throwable t) {
            System.err.println("Error processing SmsRequest: " + request);
            m_latch.countDown();
        }

        /* (non-Javadoc)
         * @see org.opennms.sms.reflector.smsservice.SmsResponseCallback#handleResponse(org.opennms.sms.reflector.smsservice.SmsRequest, org.opennms.sms.reflector.smsservice.SmsResponse)
         */
        public boolean handleResponse(MobileMsgRequest request, MobileMsgResponse response) {
            m_response.set(response);
            m_latch.countDown();
            return true;
        }

        /* (non-Javadoc)
         * @see org.opennms.sms.reflector.smsservice.SmsResponseCallback#handleTimeout(org.opennms.sms.reflector.smsservice.SmsRequest)
         */
        public void handleTimeout(MobileMsgRequest request) {
            System.err.println("Timeout waiting for SmsRequest: " + request);
            m_latch.countDown();
        }

        /**
         * @return
         * @throws InterruptedException 
         */
        public InboundMessage getMessage() throws InterruptedException {
            MobileMsgResponse response = getResponse();
            if (response instanceof SmsResponse) {
                return ((SmsResponse)response).getMessage();
            }
            return null;
            
        }
        
        public USSDResponse getUSSDResponse() throws InterruptedException{
            MobileMsgResponse response = getResponse();
            if (response instanceof UssdResponse) {
                return ((UssdResponse)response).getMessage();
            }
            return null;
        }
        
    }

    TestMessenger m_messenger;
    MobileMsgTrackerImpl m_tracker;
	DefaultTaskCoordinator m_coordinator;
	@SuppressWarnings("unused")
	private Properties m_session;
    
    @Before
    public void setUp() throws Exception {
        m_messenger = new TestMessenger();
        m_tracker = new MobileMsgTrackerImpl("test", m_messenger);
        m_tracker.start();
        
        m_session = new Properties();
        
        m_coordinator = new DefaultTaskCoordinator(Executors.newSingleThreadExecutor());

        System.err.println("=== STARTING TEST ===");
    }

	private USSDResponse sendTmobileUssdResponse(String gatewayId) {
		USSDResponse response = new USSDResponse();
        response.setContent(TMOBILE_RESPONSE);
        response.setUSSDSessionStatus(USSDSessionStatus.NO_FURTHER_ACTION_REQUIRED);
        response.setDcs(USSDDcs.UNSPECIFIED_7BIT);
        
        m_messenger.sendTestResponse(gatewayId, response);
		return response;
	}

    @Test(expected=java.net.SocketTimeoutException.class)
    public void testPingTimeoutWithBuilder() throws Throwable {
        MobileMsgSequenceBuilder builder = new MobileMsgSequenceBuilder(new MobileSequenceConfig());

        builder.sendSms("SMS Ping", "G", PHONE_NUMBER, "ping").expects(and(isSms(), textMatches("^pong$")));
        MobileMsgSequence sequence = builder.getSequence();
        System.err.println("sequence = " + sequence);
        assertNotNull(sequence);

        sequence.execute(m_tracker, m_coordinator);
    }

    @Test
    public void testPingWithBuilder() throws Throwable {
        MobileMsgSequenceBuilder builder = new MobileMsgSequenceBuilder(new MobileSequenceConfig());

        builder.sendSms("SMS Ping", "G", PHONE_NUMBER, "ping").expects(and(isSms(), textMatches("^pong$")));
        MobileMsgSequence sequence = builder.getSequence();
        System.err.println("sequence = " + sequence);
        assertNotNull(sequence);

        sequence.start(m_tracker, m_coordinator);

        Thread.sleep(500);
        InboundMessage msg = new InboundMessage(new Date(), PHONE_NUMBER, "pong", 0, "0");
		m_messenger.sendTestResponse(msg);
        
        Map<String,Number> timing = sequence.getLatency();

        assertNotNull(timing);
        assertTrue(timing.size() > 0);
        assertTrue(timing.get("SMS Ping").doubleValue() > 400);
    }

    @Test
    public void testUssdWithBuilder() throws Throwable {
    	MobileMsgSequenceBuilder builder = new MobileMsgSequenceBuilder(new MobileSequenceConfig());

        builder.sendUssd("USSD request", "G", "#225#").expects(and(isUssd(), textMatches(TMOBILE_USSD_MATCH), ussdStatusIs(USSDSessionStatus.NO_FURTHER_ACTION_REQUIRED)));
        MobileMsgSequence sequence = builder.getSequence();
        System.err.println("sequence = " + sequence);
        assertNotNull(sequence);

        sequence.start(m_tracker, m_coordinator);

        Thread.sleep(500);
        sendTmobileUssdResponse("G");
        
        Map<String,Number> timing = sequence.getLatency();

        assertNotNull(timing);
        assertTrue(timing.size() > 0);
        assertTrue(timing.get("USSD request").doubleValue() > 400);
    }

    @Test
    public void testMultipleStepSequenceBuilder() throws Throwable {
    	MobileMsgSequenceBuilder builder = new MobileMsgSequenceBuilder(new MobileSequenceConfig());
    	
        builder.sendSms("SMS Ping", "G", PHONE_NUMBER, "ping").expects(and(isSms(), textMatches("^pong$")));
        builder.sendUssd("USSD request", "G", "#225#").expects(and(isUssd(), textMatches(TMOBILE_USSD_MATCH), ussdStatusIs(USSDSessionStatus.NO_FURTHER_ACTION_REQUIRED)));
        MobileMsgSequence sequence = builder.getSequence();
        assertNotNull(sequence);
        
        sequence.start(m_tracker, m_coordinator);

        Thread.sleep(100);
        InboundMessage msg = new InboundMessage(new Date(), PHONE_NUMBER, "pong", 0, "0");
		m_messenger.sendTestResponse(msg);
		
        Thread.sleep(100);
        sendTmobileUssdResponse("G");

        Map<String,Number> timing = sequence.getLatency();

        assertNotNull(timing);
        assertTrue(timing.size() == 2);
        System.err.println(timing);
        assertTrue(timing.get("SMS Ping").doubleValue() > 50);
        assertTrue(timing.get("USSD request").doubleValue() > 50);
    }
    

}
