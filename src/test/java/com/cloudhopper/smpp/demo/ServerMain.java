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

import com.cloudhopper.smpp.SmppServerConfiguration;
import com.cloudhopper.smpp.SmppServerHandler;
import com.cloudhopper.smpp.SmppServerSession;
import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.impl.DefaultSmppServer;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.pdu.*;
import com.cloudhopper.smpp.type.SmppProcessingException;
import java.lang.ref.WeakReference;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author joelauer (twitter: @jjlauer or <a href="http://twitter.com/jjlauer" target=window>http://twitter.com/jjlauer</a>)
 */
public class ServerMain {
    private static final Logger logger = LoggerFactory.getLogger(ServerMain.class);

    private ExecutorService executorServiceForDR = Executors.newFixedThreadPool(50);

    private ExecutorService executorServiceForPdu = Executors.newFixedThreadPool(50);

    /**
     * key smppSession
     * value last heart or request timestamp
     */
    public static ConcurrentHashMap<SmppSession,Long> smppSessionEnquireLink = new ConcurrentHashMap();
    static public void main(String[] args) throws Exception {
        //
        // setup 3 things required for a server
        //
        
        // for monitoring thread use, it's preferable to create your own instance
        // of an executor and cast it to a ThreadPoolExecutor from Executors.newCachedThreadPool()
        // this permits exposing things like executor.getActiveCount() via JMX possible
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
                t.setName("SmppServerSessionWindowMonitorPool-" + sequence.getAndIncrement());
                return t;
            }
        }); 
        
        // create a server configuration
        SmppServerConfiguration configuration = new SmppServerConfiguration();
        //开放端口 配置连接信息，提供链接
        configuration.setPort(2776);
//        configuration.setPort(3777);
        configuration.setMaxConnectionSize(10);
        configuration.setNonBlockingSocketsEnabled(true);
        configuration.setDefaultRequestExpiryTimeout(30000);
        configuration.setDefaultWindowMonitorInterval(15000);
        configuration.setDefaultWindowSize(5);
        configuration.setDefaultWindowWaitTimeout(configuration.getDefaultRequestExpiryTimeout());
        configuration.setDefaultSessionCountersEnabled(true);
        configuration.setJmxEnabled(true);
        
        // create a server, start it up
        //创建一个SmppServer等待 客户端发起连接
        DefaultSmppServer smppServer = new DefaultSmppServer(configuration, new DefaultSmppServerHandler(), executor, monitorExecutor);

        logger.info("Starting SMPP server...");
        smppServer.start();
        logger.info("SMPP server started");

        System.out.println("Press any key to stop server");
        System.in.read();

        logger.info("Stopping SMPP server...");
        smppServer.stop();
        logger.info("SMPP server stopped");
        
        logger.info("Server counters: {}", smppServer.getCounters());
    }

    public static class DefaultSmppServerHandler implements SmppServerHandler {

        @Override
        public void sessionBindRequested(Long sessionId, SmppSessionConfiguration sessionConfiguration, final BaseBind bindRequest) throws SmppProcessingException {
            // test name change of sessions
            // this name actually shows up as thread context....
            sessionConfiguration.setName("Application.SMPP." + sessionConfiguration.getSystemId());
            logger.info("开始处理连接绑定请求");

            //throw new SmppProcessingException(SmppConstants.STATUS_BINDFAIL, null);
        }

        /**
         * 创建会话 ，EMCS与 SMSmap
         * @param sessionId The unique numeric identifier assigned to the bind request.
         *      Will be the same value between sessionBindRequested, sessionCreated,
         *      and sessionDestroyed method calls.
         * @param session The server session associated with the bind request and
         *      underlying channel.
         * @param preparedBindResponse The prepared bind response that will
         *      eventually be returned to the client when "serverReady" is finally
         *      called on the session.
         * @throws SmppProcessingException
         */
        @Override
        public void sessionCreated(Long sessionId, SmppServerSession session, BaseBindResp preparedBindResponse) throws SmppProcessingException {
            logger.info("连接成功，创建会话开始Session created: {}", session);
            // need to do something it now (flag we're ready)

            session.serverReady(new TestSmppSessionHandler(session));
        }

        @Override
        public void sessionDestroyed(Long sessionId, SmppServerSession session) {
            logger.info("销毁会话，Session destroyed: {}", session);
            // print out final stats
            if (session.hasCounters()) {
                logger.info(" final session rx-submitSM: {}", session.getCounters().getRxSubmitSM());
            }
            
            // make sure it's really shutdown
            session.destroy();
        }

    }
    public static void putSessionEnquireLinkTime(SmppSession session) {
        smppSessionEnquireLink.put(session,System.currentTimeMillis());
    }

    public static class TestSmppSessionHandler extends DefaultSmppSessionHandler {
        
        private WeakReference<SmppSession> sessionRef;
        
        public TestSmppSessionHandler(SmppSession session) {
            this.sessionRef = new WeakReference<SmppSession>(session);
        }
        @Override
        public boolean firePduReceived(Pdu pdu) {
            // default handling is to accept pdu for processing up chain
            SmppSession session = sessionRef.get();
            putSessionEnquireLinkTime(session);
            return true;
        }

        
        @Override
        public PduResponse firePduRequestReceived(PduRequest pduRequest) {
            SmppSession session = sessionRef.get();
            //返回MessageId
            String messageId =  UUID.randomUUID().toString().replace("-","");
            PduResponse responsePdu = pduRequest.createResponse();
            if (responsePdu instanceof SubmitSmResp){
            SubmitSmResp submitSmResp = ((SubmitSmResp) responsePdu);
            submitSmResp.setMessageId(messageId);
            }
            // mimic how long processing could take on a slower smsc
            try {
                logger.info("监听到客户端Pdu请求，内容{}",pduRequest);
                session.sendRequestPdu(responsePdu);
            } catch (Exception e) { }
            return null;
        }
    }
    
}
