package org.graylog2.logging;

import java.util.Map;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import org.graylog2.message.GelfMessageBuilder;
import org.graylog2.message.GelfMessageBuilderConfiguration;
import org.graylog2.message.GelfMessageBuilderException;

public class GelfFormatter extends Formatter {
	private GelfFormatterConfiguration gelfFormatterConfiguration;
	private GelfMessageBuilderConfiguration gelfMessageBuilderConfiguration;

	public GelfFormatter() {
		configure(new JULProperties(LogManager.getLogManager(), System.getProperties(), getClass().getName()));
	}

	public GelfFormatter(JULProperties properties) {
		configure(properties);
	}

	private void configure(JULProperties properties) {
		this.gelfFormatterConfiguration = JULConfigurationManager.getGelfFormatterConfiguration(properties);
		this.gelfMessageBuilderConfiguration = JULConfigurationManager.getGelfMessageBuilderConfiguration(properties);
	}

	@Override
	public String format(LogRecord record) {
		return formatMessage(record);
	}

	@Override
	public String formatMessage(LogRecord record) throws GelfMessageBuilderException {
		if (record == null) {
			return null;
		}
		GelfMessageBuilder builder = new GelfMessageBuilder(gelfMessageBuilderConfiguration);

		Map<String, ? extends Object> fields;
		builder.setMessage(formatMessageField(record));
		builder.setThrowable(record.getThrown());
		if (record instanceof GelfLogRecord) {
			GelfLogRecord gelfLogRecord = (GelfLogRecord) record;
			fields = gelfLogRecord.getFields();
		} else if (gelfFormatterConfiguration.getFieldExtractor() != null) {
			fields = gelfFormatterConfiguration.getFieldExtractor().getFields(record);
		} else {
			fields = null;
		}
		builder.setLevel(String.valueOf(levelToSyslogLevel(record.getLevel())));
		builder.addField(GelfMessageBuilder.NATIVE_LEVEL_FIELD, record.getLevel());
		builder.addField(GelfMessageBuilder.LOGGER_NAME_FIELD, record.getLoggerName());
		builder.addField(GelfMessageBuilder.THREAD_NAME_FIELD, Thread.currentThread().getName());
		if (gelfFormatterConfiguration.isIncludeLocation()) {
			builder.addField(GelfMessageBuilder.CLASS_NAME_FIELD, record.getSourceClassName());
			builder.addField(GelfMessageBuilder.METHOD_NAME_FIELD, record.getSourceMethodName());
		}
		builder.addFields(fields);
		
		return builder.build().toJson();
	}

	private String formatMessageField(LogRecord record) {
		if (record == null) {
			return "";
		}
		String format = record.getMessage();
		java.util.ResourceBundle catalog = record.getResourceBundle();
		if (catalog != null) {
			try {
				format = catalog.getString(record.getMessage());
			} catch (java.util.MissingResourceException ex) {
				format = record.getMessage();
			}
		}
		try {
			Object parameters[] = record.getParameters();
			if (parameters == null || parameters.length == 0) {
				return format;
			}
			if (format.indexOf("{0") >= 0 || format.indexOf("{1") >= 0 || format.indexOf("{2") >= 0
					|| format.indexOf("{3") >= 0) {
				return java.text.MessageFormat.format(format, parameters);
			} else {
				return String.format(format, parameters);
			}
		} catch (Exception exception) {
			return format;
		}
	}

	private int levelToSyslogLevel(Level level) {
		final int syslogLevel;
		if (level.intValue() == Level.SEVERE.intValue()) {
			syslogLevel = 3;
		} else if (level.intValue() == Level.WARNING.intValue()) {
			syslogLevel = 4;
		} else if (level.intValue() == Level.INFO.intValue()) {
			syslogLevel = 6;
		} else {
			syslogLevel = 7;
		}
		return syslogLevel;
	}
}
