package com.cloudhopper.smpp.demo;

/*
 * #%L
 * ch-smpp
 * %%
 * Copyright (C) 2009 - 2015 Cloudhopper by Twitter
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.commons.util.windowing.WindowFuture;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.impl.DefaultSmppClient;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.tlv.Tlv;
import com.cloudhopper.smpp.type.Address;
import com.cloudhopper.smpp.pdu.EnquireLink;
import com.cloudhopper.smpp.pdu.EnquireLinkResp;
import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;
import com.cloudhopper.smpp.pdu.SubmitSm;
import com.cloudhopper.smpp.pdu.SubmitSmResp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author joelauer (twitter: @jjlauer or <a href="http://twitter.com/jjlauer" target=window>http://twitter.com/jjlauer</a>)
 */
public class ClientMain {
    private static final Logger logger = LoggerFactory.getLogger(ClientMain.class);

    static public void main(String[] args) throws Exception {
        //
        // setup 3 things required for any session we plan on creating
        //
        
        // for monitoring thread use, it's preferable to create your own instance
        // of an executor with Executors.newCachedThreadPool() and cast it to ThreadPoolExecutor
        // this permits exposing thinks like executor.getActiveCount() via JMX possible
        // no point renaming the threads in a factory since underlying Netty 
        // framework does not easily allow you to customize your thread names
        ThreadPoolExecutor executor = (ThreadPoolExecutor)Executors.newCachedThreadPool();
        
        // to enable automatic expiration of requests, a second scheduled executor
        // is required which is what a monitor task will be executed with - this
        // is probably a thread pool that can be shared with between all client bootstraps
        ScheduledThreadPoolExecutor monitorExecutor = (ScheduledThreadPoolExecutor)Executors.newScheduledThreadPool(1, new ThreadFactory() {
            private AtomicInteger sequence = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("SmppClientSessionWindowMonitorPool-" + sequence.getAndIncrement());
                return t;
            }
        });
        
        // a single instance of a client bootstrap can technically be shared
        // between any sessions that are created (a session can go to any different
        // number of SMSCs) - each session created under
        // a client bootstrap will use the executor and monitorExecutor set
        // in its constructor - just be *very* careful with the "expectedSessions"
        // value to make sure it matches the actual number of total concurrent
        // open sessions you plan on handling - the underlying netty library
        // used for NIO sockets essentially uses this value as the max number of
        // threads it will ever use, despite the "max pool size", etc. set on
        // the executor passed in here
        DefaultSmppClient clientBootstrap = new DefaultSmppClient(Executors.newCachedThreadPool(), 1, monitorExecutor);

        //
        // setup configuration for a client session
        // 回调处理器
        //
        DefaultSmppSessionHandler sessionHandler = new ClientSmppSessionHandler();

        SmppSessionConfiguration config0 = new SmppSessionConfiguration();
        config0.setWindowSize(1);
        config0.setName("SMBPR22O");
        config0.setType(SmppBindType.TRANSCEIVER);
        config0.setSystemId("SMBPR22O");
        config0.setPassword("eeeqxxtG@#");
        config0.setPort(8888);
        //91
        config0.setHost("103.88.253.162");
        //52
        //config0.setHost("47.241.76.189");
//        config0.setPort(18777);
//        config0.setSystemId("test-otp-52");
//        config0.setPassword("888888");
//        config0.setHost("smpp.messaging-service.com");
//        config0.setPort(8888);
//        config0.setSystemId("PaulKMI");
//        config0.setPassword("KMI@2020y");
        config0.setConnectTimeout(10000);
        config0.getLoggingOptions().setLogBytes(true);
        // to enable monitoring (request expiration)
        config0.setRequestExpiryTimeout(30000);
        config0.setWindowMonitorInterval(15000);
        config0.setCountersEnabled(true);

        //
        // create session, enquire link, submit an sms, close session
        //
        SmppSession session0 = null;

        try {
            // create session a session by having the bootstrap connect a
            // socket, send the bind request, and wait for a bind response
            session0 = clientBootstrap.bind(config0, sessionHandler);
            logger.info("等待连接绑定响应---------------------------------------------------");
//            //System.in.read();

            // demo of a "synchronous" enquireLink call - send it and wait for a response
            EnquireLinkResp enquireLinkResp1 = session0.enquireLink(new EnquireLink(), 10000);
            logger.info("enquire_link_resp #1: commandStatus [" + enquireLinkResp1.getCommandStatus() + "=" + enquireLinkResp1.getResultMessage() + "]");
            
//            System.out.println("Press any key to send enquireLink #2");
//            System.in.read();

            // demo of an "asynchronous" enquireLink call - send it, get a future,
            // and then optionally choose to pick when we wait for it
            WindowFuture<Integer,PduRequest,PduResponse> future0 = session0.sendRequestPdu(new EnquireLink(), 10000, true);
            if (!future0.await()) {
                logger.error("Failed to receive enquire_link_resp within specified time");
            } else if (future0.isSuccess()) {
                EnquireLinkResp enquireLinkResp2 = (EnquireLinkResp)future0.getResponse();
                logger.info("enquire_link_resp #2: commandStatus [" + enquireLinkResp2.getCommandStatus() + "=" + enquireLinkResp2.getResultMessage() + "]");
            } else {
                logger.error("Failed to properly receive enquire_link_resp: " + future0.getCause());
            }

            System.out.println("Press any key to send submit #1");
            System.in.read();
            SubmitSm submit0 = new SubmitSm();

            for (int i = 1; i < 2; i++) {
                //TODO GSM编码格式不支持西班牙小语种的部分重读字符，CharsetUtil工具类目前能解析 à 无法解析 á，目前可通过查看GSMCharset 源码得知哪些字符可以正常解析
                //UTF-8支持
                String text160 = "Best color prediction game.Highest multiplier:1.9/4.4,and has 98% accurate team planning. Registration for free Rs100,click to contact me https://bit.ly/3v1UGiC";
                byte[] textBytes = CharsetUtil.encode(text160, CharsetUtil.CHARSET_UTF_8);
                // add delivery receipt
                //submit0.setRegisteredDelivery(SmppConstants.REGISTERED_DELIVERY_SMSC_RECEIPT_REQUESTED);

                // address 包括发送方信息，接收方信息
                submit0.setSourceAddress(new Address((byte) 0x03, (byte) 0x00, "159483"));
                //submit0.setDestAddress(new Address((byte) 0x01, (byte) 0x01, toList.get(1)));//Tk

                submit0.setShortMessage(textBytes);
                //接收方号码
                submit0.setDestAddress(new Address((byte) 0x01, (byte) 0x01, "919582473827"));//Tk
                //logger.info("sendWindow.size: {}", session0.getSendWindow().getSize());
                short pe_id  = 0x1400;
                short template_id = 0x1401;
                String entity_id_value = "12312312312368412";
                String template_id_value = "yweyruywerywrewr";
                submit0.addOptionalParameter(new Tlv(pe_id, CharsetUtil.encode(entity_id_value, CharsetUtil.CHARSET_GSM), ""));
                submit0.addOptionalParameter(new Tlv(template_id, CharsetUtil.encode(template_id_value, CharsetUtil.CHARSET_GSM), ""));
                SubmitSmResp submitResp = session0.submit(submit0, 10000);
                logger.info("submitResponse:{}", submitResp);
            }
                System.out.println("Press any key to unbind and close sessions");
                System.in.read();
            session0.unbind(5000);
        } catch (Exception e) {
            logger.error("", e);
        }

        if (session0 != null) {
            logger.info("Cleaning up session... (final counters)");
            if (session0.hasCounters()) {
                logger.info("tx-enquireLink: {}", session0.getCounters().getTxEnquireLink());
                logger.info("tx-submitSM: {}", session0.getCounters().getTxSubmitSM());
                logger.info("tx-deliverSM: {}", session0.getCounters().getTxDeliverSM());
                logger.info("tx-dataSM: {}", session0.getCounters().getTxDataSM());
                logger.info("rx-enquireLink: {}", session0.getCounters().getRxEnquireLink());
                logger.info("rx-submitSM: {}", session0.getCounters().getRxSubmitSM());
                logger.info("rx-deliverSM: {}", session0.getCounters().getRxDeliverSM());
                logger.info("rx-dataSM: {}", session0.getCounters().getRxDataSM());
            }
            
            session0.destroy();
            // alternatively, could call close(), get outstanding requests from
            // the sendWindow (if we wanted to retry them later), then call shutdown()
        }

        // this is required to not causing server to hang from non-daemon threads
        // this also makes sure all open Channels are closed to I *think*
        logger.info("Shutting down client bootstrap and executors...");
        clientBootstrap.destroy();
        executor.shutdownNow();
        monitorExecutor.shutdownNow();
        
        logger.info("Done. Exiting");
    }

    /**
     * Could either implement SmppSessionHandler or only override select methods
     * by extending a DefaultSmppSessionHandler.
     */
    public static class ClientSmppSessionHandler extends DefaultSmppSessionHandler {

        public ClientSmppSessionHandler() {
            super(logger);
        }

        @Override
        public void firePduRequestExpired(PduRequest pduRequest) {
            logger.warn("PDU request expired: {}", pduRequest);
        }

        @Override
        public PduResponse firePduRequestReceived(PduRequest pduRequest) {
            PduResponse response = pduRequest.createResponse();

            // do any logic here
            
            return response;
        }
        
    }
    
}
