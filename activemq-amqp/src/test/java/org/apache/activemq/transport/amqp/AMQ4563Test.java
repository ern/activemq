/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.transport.amqp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.spring.SpringSslContext;
import org.apache.activemq.store.kahadb.KahaDBStore;
import org.apache.activemq.transport.amqp.joram.ActiveMQAdmin;
import org.apache.qpid.amqp_1_0.jms.impl.ConnectionFactoryImpl;
import org.apache.qpid.amqp_1_0.jms.impl.QueueImpl;
import org.junit.Test;

public class AMQ4563Test extends AmqpTestSupport {

    public static final String KAHADB_DIRECTORY = "target/activemq-data/kahadb-amq4563";

    private String openwireUri;

    @Test(timeout = 60000)
    public void testMessagesAreAckedAMQProducer() throws Exception {
        int messagesSent = 3;
        ActiveMQAdmin.enableJMSFrameTracing();
        QueueImpl queue = new QueueImpl("queue://txqueue");
        assertTrue(brokerService.isPersistent());

        Connection connection = createAMQConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination destination = session.createQueue("txqueue");
        MessageProducer p = session.createProducer(destination);
        TextMessage message = null;
        for (int i=0; i < messagesSent; i++) {
            message = session.createTextMessage();
            String messageText = "Hello " + i + " sent at " + new java.util.Date().toString();
            message.setText(messageText);
            LOG.debug(">>>> Sent [" + messageText + "]");
            p.send(message);
        }

        // After the first restart we should get all messages sent above
        restartBroker(connection, session);
        int messagesReceived = readAllMessages(queue);
        assertEquals(messagesSent, messagesReceived);

        // This time there should be no messages on this queue
        restartBroker(connection, session);
        messagesReceived = readAllMessages(queue);
        assertEquals(0, messagesReceived);
    }

    @Test(timeout = 60000)
    public void testSelectingOnAMQPMessageID() throws Exception {
        ActiveMQAdmin.enableJMSFrameTracing();
        QueueImpl queue = new QueueImpl("queue://txqueue");
        assertTrue(brokerService.isPersistent());

        Connection connection = createAMQPConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination destination = session.createQueue("txqueue");
        MessageProducer p = session.createProducer(destination);
        TextMessage message = session.createTextMessage();
        String messageText = "Hello sent at " + new java.util.Date().toString();
        message.setText(messageText);
        p.send(message);

        // Restart broker.
        restartBroker(connection, session);
        String selector = "JMSMessageID = '" + message.getJMSMessageID() + "'";
        LOG.info("Using selector: "+selector);
        int messagesReceived = readAllMessages(queue, selector);
        assertEquals(1, messagesReceived);
    }

    @Test(timeout = 60000)
    public void testSelectingOnActiveMQMessageID() throws Exception {
        ActiveMQAdmin.enableJMSFrameTracing();
        QueueImpl queue = new QueueImpl("queue://txqueue");
        assertTrue(brokerService.isPersistent());

        Connection connection = createAMQConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination destination = session.createQueue("txqueue");
        MessageProducer p = session.createProducer(destination);
        TextMessage message = session.createTextMessage();
        String messageText = "Hello sent at " + new java.util.Date().toString();
        message.setText(messageText);
        p.send(message);

        // Restart broker.
        restartBroker(connection, session);
        String selector = "JMSMessageID = '" + message.getJMSMessageID() + "'";
        LOG.info("Using selector: "+selector);
        int messagesReceived = readAllMessages(queue, selector);
        assertEquals(1, messagesReceived);
    }

    @Test(timeout = 60000)
    public void testMessagesAreAckedAMQPProducer() throws Exception {
        int messagesSent = 3;
        ActiveMQAdmin.enableJMSFrameTracing();
        QueueImpl queue = new QueueImpl("queue://txqueue");
        assertTrue(brokerService.isPersistent());

        Connection connection = createAMQPConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer p = session.createProducer(queue);
        TextMessage message = null;
        for (int i=0; i < messagesSent; i++) {
            message = session.createTextMessage();
            String messageText = "Hello " + i + " sent at " + new java.util.Date().toString();
            message.setText(messageText);
            LOG.debug(">>>> Sent [" + messageText + "]");
            p.send(message);
        }

        // After the first restart we should get all messages sent above
        restartBroker(connection, session);
        int messagesReceived = readAllMessages(queue);
        assertEquals(messagesSent, messagesReceived);

        // This time there should be no messages on this queue
        restartBroker(connection, session);
        messagesReceived = readAllMessages(queue);
        assertEquals(0, messagesReceived);
    }

    private int readAllMessages(QueueImpl queue) throws JMSException {
        return readAllMessages(queue, null);
    }

    private int readAllMessages(QueueImpl queue, String selector) throws JMSException {
        Connection connection = createAMQPConnection();
        try {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            int messagesReceived = 0;
            MessageConsumer consumer;
            if( selector==null ) {
                consumer = session.createConsumer(queue);
            } else {
                consumer = session.createConsumer(queue, selector);
            }
            Message msg = consumer.receive(5000);
            while(msg != null) {
                assertNotNull(msg);
                assertTrue(msg instanceof TextMessage);
                TextMessage textMessage = (TextMessage) msg;
                LOG.debug(">>>> Received [" + textMessage.getText() + "]");
                messagesReceived++;
                msg = consumer.receive(5000);
            }
            consumer.close();

            return messagesReceived;
        } finally {
            connection.close();
        }
    }

    private void restartBroker(Connection connection, Session session) throws Exception {
        session.close();
        connection.close();

        stopBroker();
        createBroker(false);
    }

    private Connection createAMQPConnection() throws JMSException {
        LOG.debug(">>> In createConnection using port " + port);
        final ConnectionFactoryImpl factory = new ConnectionFactoryImpl("localhost", port, "admin", "password");
        final Connection connection = factory.createConnection();
        connection.setExceptionListener(new ExceptionListener() {
            @Override
            public void onException(JMSException exception) {
                exception.printStackTrace();
            }
        });
        connection.start();
        return connection;
    }

    private Connection createAMQConnection() throws JMSException {
        LOG.debug(">>> In createConnection using port " + port);
        final ConnectionFactory factory = new ActiveMQConnectionFactory("admin", "password", openwireUri);
        final Connection connection = factory.createConnection();
        connection.setExceptionListener(new ExceptionListener() {
            @Override
            public void onException(JMSException exception) {
                exception.printStackTrace();
            }
        });
        connection.start();
        return connection;
    }

    @Override
    public void startBroker() throws Exception {
        createBroker(true);
    }

    /**
     * Copied from AmqpTestSupport, modified to use persistence
     */
    public void createBroker(boolean deleteAllMessages) throws Exception {
        KahaDBStore kaha = new KahaDBStore();
        kaha.setDirectory(new File(KAHADB_DIRECTORY));

        brokerService = new BrokerService();
        brokerService.setDeleteAllMessagesOnStartup(deleteAllMessages);
        brokerService.setPersistent(true);
        brokerService.setPersistenceAdapter(kaha);
        brokerService.setAdvisorySupport(false);
        brokerService.setUseJmx(false);
        brokerService.setStoreOpenWireVersion(10);
        openwireUri = brokerService.addConnector("tcp://0.0.0.0:0").getPublishableConnectString();

        // Setup SSL context...
        final File classesDir = new File(AmqpProtocolConverter.class.getProtectionDomain().getCodeSource().getLocation().getFile());
        File keystore = new File(classesDir, "../../src/test/resources/keystore");
        final SpringSslContext sslContext = new SpringSslContext();
        sslContext.setKeyStore(keystore.getCanonicalPath());
        sslContext.setKeyStorePassword("password");
        sslContext.setTrustStore(keystore.getCanonicalPath());
        sslContext.setTrustStorePassword("password");
        sslContext.afterPropertiesSet();
        brokerService.setSslContext(sslContext);

        addAMQPConnector();
        brokerService.start();
        this.numberOfMessages = 2000;
    }
}
