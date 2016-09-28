package core.framework.impl.log;

import core.framework.api.log.ErrorCode;
import core.framework.api.log.Markers;
import core.framework.api.log.MessageFilter;
import core.framework.api.log.Severity;
import org.slf4j.Logger;
import org.slf4j.Marker;

/**
 * @author neo
 */
public final class LogManager {
    public final String appName;

    private final ThreadLocal<ActionLog> actionLog = new ThreadLocal<>();
    private final Logger logger = new LoggerImpl(LoggerImpl.abbreviateLoggerName(LogManager.class.getCanonicalName()), this, LogLevel.INFO, LogLevel.DEBUG);
    public ActionLogger actionLogger;
    public TraceLogger traceLogger;
    public KafkaLogForwarder logForwarder;
    public MessageFilter filter;

    public LogManager() {
        this.appName = System.getProperty("core.appName");
    }

    public void begin(String message) {
        this.actionLog.set(new ActionLog(message));
    }

    public void end(String message) {
        ActionLog actionLog = currentActionLog();
        this.actionLog.remove();
        actionLog.end(message);

        if (traceLogger != null) traceLogger.write(actionLog);  // trace log generate logPath context to action log, so make it process first
        if (actionLogger != null) actionLogger.write(actionLog);
        if (logForwarder != null) logForwarder.forwardLog(actionLog);
    }

    public void process(LogEvent event) {
        ActionLog actionLog = currentActionLog();
        if (actionLog != null) actionLog.process(event);    // process is called by loggerImpl.log, begin() may not be called before
    }

    public void start() {
        if (logForwarder != null) logForwarder.start();
    }

    public void stop() {
        if (logForwarder != null) logForwarder.stop();
        if (actionLogger != null) actionLogger.close();
    }

    public ActionLog currentActionLog() {
        return actionLog.get();
    }

    public void logError(Throwable e) {
        String errorMessage = e.getMessage();
        String errorCode = e instanceof ErrorCode ? ((ErrorCode) e).errorCode() : e.getClass().getCanonicalName();
        Marker marker = Markers.errorCode(errorCode);
        if (e instanceof ErrorCode && ((ErrorCode) e).severity() == Severity.WARN) {
            logger.warn(marker, errorMessage, e);
        } else {
            logger.error(marker, errorMessage, e);
        }
    }
}
