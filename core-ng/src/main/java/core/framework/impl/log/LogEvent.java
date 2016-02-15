package core.framework.impl.log;

import core.framework.api.util.Exceptions;
import core.framework.api.util.Strings;
import core.framework.impl.log.marker.ErrorCodeMarker;
import org.slf4j.Marker;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * @author neo
 */
final class LogEvent {
    final LogLevel level;
    private final String logger;
    private final Marker marker;
    private final long time = System.currentTimeMillis();
    private final String message;
    private final Object[] arguments;
    private final Throwable exception;

    private String logMessage;

    LogEvent(String logger, Marker marker, LogLevel level, String message, Object[] arguments, Throwable exception) {
        this.level = level;
        this.marker = marker;
        this.logger = logger;
        this.message = message;
        this.arguments = arguments;
        this.exception = exception;
    }

    String logMessage() {
        if (logMessage == null) {
            StringBuilder builder = new StringBuilder(64);
            builder.append(DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(time)))
                .append(" [")
                .append(Thread.currentThread().getName())
                .append("] ")
                .append(level.name())
                .append(' ')
                .append(logger)
                .append(" - ");

            if (marker != null) {
                builder.append('[').append(marker.getName()).append("] ");
            }

            builder.append(message(100000));    // limit 100K per event log

            builder.append(System.lineSeparator());
            if (exception != null)
                builder.append(Exceptions.stackTrace(exception));

            logMessage = builder.toString();
        }
        return logMessage;
    }

    String message(int maxSize) {
        String result;
        if (arguments == null) {
            result = message;
        } else {
            result = Strings.format(message, arguments);
        }
        if (result.length() > maxSize) {
            return result.substring(0, maxSize) + "...(truncated)";
        }
        return result;
    }

    String errorCode() {
        if (marker instanceof ErrorCodeMarker) return marker.getName();
        return null;
    }
}
