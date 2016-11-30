package org.graylog2.log4j2;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.apache.logging.log4j.core.layout.SerializedLayout;
import org.apache.logging.log4j.core.net.Severity;
import org.graylog2.message.GelfMessageBuilder;
import org.graylog2.message.GelfMessageBuilderConfiguration;
import org.graylog2.sender.GelfSender;
import org.graylog2.sender.GelfSenderConfiguration;
import org.graylog2.sender.GelfSenderFactory;

@Plugin(name = "Gelf", category = "Core", elementType = "appender", printObject = true)
public class GelfAppender extends AbstractAppender {
	private static final String LOGGER_NDC = "loggerNdc";
	private GelfMessageBuilderConfiguration gelfMessageBuilderConfiguration;
	private GelfSenderConfiguration gelfSenderConfiguration;
	private GelfSender gelfSender;
	
	public GelfAppender(String name, Filter filter, Layout<? extends Serializable> layout,
			GelfMessageBuilderConfiguration gelfMessageBuilderConfiguration,
			GelfSenderConfiguration gelfSenderConfiguration) {
		super(name, filter, layout);
		this.gelfMessageBuilderConfiguration = gelfMessageBuilderConfiguration;
		this.gelfSenderConfiguration = gelfSenderConfiguration;
	}

	@Override
	public void start() {
		if (gelfSender == null) {
			gelfSender = GelfSenderFactory.getInstance().createSender(gelfSenderConfiguration);
		}
		super.start();
	}
	
	@Override
	public boolean stop(long timeout, TimeUnit timeUnit) {
		close();
		return super.stop(timeout, timeUnit);
	}

	@Override
	public void stop() {
		close();
		super.stop();
	}

	private void close() {
		if (gelfSender != null) {
			gelfSender.close();
			gelfSender = null;
		}
	}

	public void append(LogEvent event) {
		GelfMessageBuilder builder = new GelfMessageBuilder(gelfMessageBuilderConfiguration);
		builder.setTimestamp(event.getTimeMillis());
		String formattedMessage = event.getMessage().getFormattedMessage();
		builder.setMessage(formattedMessage);
		builder.setThrowable(event.getThrown());
		builder.setLevel(String.valueOf(Severity.getSeverity(event.getLevel()).getCode()));
		builder.addField(GelfMessageBuilder.THREAD_NAME_FIELD, event.getThreadName());
		builder.addField(GelfMessageBuilder.NATIVE_LEVEL_FIELD, event.getLevel().name());
		builder.addField(GelfMessageBuilder.LOGGER_NAME_FIELD, event.getLoggerName());
		String ndc = event.getContextStack().peek();
		StackTraceElement source = event.getSource();
		if (source != null) {
			builder.addField(GelfMessageBuilder.CLASS_NAME_FIELD, source.getClassName());
			builder.addField(GelfMessageBuilder.METHOD_NAME_FIELD, source.getMethodName());
		}
		if (ndc != null) {
			builder.addField(LOGGER_NDC, ndc);
		}
		try {
			gelfSender.sendMessage(builder.build());
		} catch (Exception exception) {
			getHandler().error("Could not send gelf message", exception);
		}
	}
	
	@PluginBuilderFactory
	public static GelfAppenderBuilder createAppender() {
		return new GelfAppenderBuilder();
	}
	
	public static class GelfAppenderBuilder implements org.apache.logging.log4j.core.util.Builder<GelfAppender> {
		@PluginBuilderAttribute
        @Required(message = "A name for the GelfAppender must be specified")
        private String name;
		@PluginBuilderAttribute
        @Required(message = "A targetURI for the GelfAppender must be specified")
		private String targetURI;
		@PluginBuilderAttribute
		private boolean threaded;
		@PluginBuilderAttribute
		private int threadedQueueMaxDepth;
		@PluginBuilderAttribute
		private int threadedQueueTimeout;
		@PluginBuilderAttribute
		private int maxRetries;
		@PluginBuilderAttribute
		private boolean extractStacktrace;
		@PluginBuilderAttribute
		private String originHost;
		@PluginBuilderAttribute
		private String facility;
		@PluginElement("Layout")
        private Layout<? extends Serializable> layout;
        @PluginElement("Filter")
        private Filter filter;
        
        public GelfAppenderBuilder() {
        	this.threaded = false;
        	this.threadedQueueTimeout = GelfSenderConfiguration.DEFAULT_THREADED_QUEUE_TIMEOUT;
        	this.threadedQueueMaxDepth = GelfSenderConfiguration.DEFAULT_THREADED_QUEUE_MAX_DEPTH;
        	this.layout = SerializedLayout.createLayout();
		}
        
        public GelfAppenderBuilder setName(String name) {
			this.name = name;
			return this;
		}
        
        public GelfAppenderBuilder setTargetURI(String targetURI) {
			this.targetURI = targetURI;
			return this;
		}
        
        public GelfAppenderBuilder setThreaded(boolean threaded) {
			this.threaded = threaded;
			return this;
		}
        
        public GelfAppenderBuilder setThreadedQueueTimeout(int threadedQueueTimeout) {
			this.threadedQueueTimeout = threadedQueueTimeout;
			return this;
		}
        
        public GelfAppenderBuilder setThreadedQueueMaxDepth(int threadedQueueMaxDepth) {
			this.threadedQueueMaxDepth = threadedQueueMaxDepth;
			return this;
		}
        
        public GelfAppenderBuilder setMaxRetries(int maxRetries) {
			this.maxRetries = maxRetries;
			return this;
		}
        
        public GelfAppenderBuilder setExtractStacktrace(boolean extractStactkrace) {
			this.extractStacktrace = extractStactkrace;
			return this;
		}
        
        public GelfAppenderBuilder setOriginHost(String originHost) {
			this.originHost = originHost;
			return this;
		}
        
        public GelfAppenderBuilder setFacility(String facility) {
			this.facility = facility;
			return this;
		}
        
        public GelfAppenderBuilder setFilter(Filter filter) {
			this.filter = filter;
			return this;
		}
        
        public GelfAppenderBuilder setLayout(Layout<? extends Serializable> layout) {
			this.layout = layout;
			return this;
		}

		public GelfAppender build() {
			GelfMessageBuilderConfiguration gelfMessageBuilderConfiguration = new GelfMessageBuilderConfiguration();
			gelfMessageBuilderConfiguration.setExtractStacktrace(extractStacktrace);
			gelfMessageBuilderConfiguration.setFacility(facility);
			gelfMessageBuilderConfiguration.setOriginHost(originHost);
			
			GelfSenderConfiguration gelfSenderConfiguration = new GelfSenderConfiguration();
			gelfSenderConfiguration.setTargetURI(targetURI);
			gelfSenderConfiguration.setThreaded(threaded);
			gelfSenderConfiguration.setThreadedQueueMaxDepth(threadedQueueMaxDepth);
			gelfSenderConfiguration.setThreadedQueueTimeout(threadedQueueTimeout);
			gelfSenderConfiguration.setMaxRetries(maxRetries);
			
			return new GelfAppender(name, filter, layout, gelfMessageBuilderConfiguration, gelfSenderConfiguration);
		}
	}
}
