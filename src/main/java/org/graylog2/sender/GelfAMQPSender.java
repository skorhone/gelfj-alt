package org.graylog2.sender;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class GelfAMQPSender implements GelfSender {
	private volatile boolean shutdown;
	private final ConnectionFactory factory;
	private Connection connection;
	private Channel channel;
	private AMQPBufferManager bufferManager;
	private final String exchangeName;
	private final String routingKey;

	public GelfAMQPSender(GelfSenderConfiguration configuration)
			throws IOException, URISyntaxException, NoSuchAlgorithmException, KeyManagementException {
		this.factory = new ConnectionFactory();
		this.factory.setUri(configuration.getTargetURI());
		this.exchangeName = configuration.getURIOption("exchange");
		this.routingKey = configuration.getURIOption("routingKey");
		this.bufferManager = new AMQPBufferManager();
	}

	public synchronized void sendMessage(String message) throws GelfSenderException {
		if (shutdown) {
			throw new GelfSenderException(GelfSenderException.ERROR_CODE_SHUTTING_DOWN);
		}
		String uuid = UUID.randomUUID().toString();
		String messageid = "gelf-" + uuid;

		try {
			send(messageid, message);
		} catch (Exception exception) {
			closeConnection();
			throw new GelfSenderException(GelfSenderException.ERROR_CODE_GENERIC_ERROR, exception);
		}
	}

	private void send(String messageid, String message) throws IOException, InterruptedException {
		if (!isConnected()) {
			connect();
		}
		BasicProperties.Builder propertiesBuilder = new BasicProperties.Builder();
		propertiesBuilder.contentType("application/json; charset=utf-8");
		propertiesBuilder.contentEncoding("gzip");
		propertiesBuilder.messageId(messageid);
		BasicProperties properties = propertiesBuilder.build();
		channel.basicPublish(exchangeName, routingKey, properties, bufferManager.toAMQPBuffer(message));
		channel.waitForConfirms();
	}

	private void connect() throws IOException {
		connection = factory.newConnection();
		channel = connection.createChannel();
		channel.confirmSelect();
	}

	private boolean isConnected() {
		return channel != null;
	}

	public synchronized void close() {
		if (!shutdown) {
			shutdown = true;
			closeConnection();
		}
	}

	private void closeConnection() {
		try {
			channel.close();
		} catch (Exception e) {
		}
		try {
			connection.close();
		} catch (Exception e) {
		}
		channel = null;
		connection = null;
	}

	public static class AMQPBufferManager extends AbstractBufferManager {
		public byte[] toAMQPBuffer(String message) {
			return getMessageAsBytes(message);
		}
	}
}
