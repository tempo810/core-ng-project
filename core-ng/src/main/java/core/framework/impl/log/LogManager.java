package core.framework.impl.log;

import core.framework.impl.log.filter.LogFilter;
import core.framework.log.ErrorCode;
import core.framework.log.Markers;
import core.framework.log.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

/**
 * @author neo
 */
public class LogManager {
    public static final ThreadLocal<ActionLog> CURRENT_ACTION_LOG = new ThreadLocal<>();
    public static final String APP_NAME;

    static final ActionIdGenerator ID_GENERATOR = new ActionIdGenerator();
    private static final Logger LOGGER = LoggerFactory.getLogger(LogManager.class);

    static {
        String appName = System.getProperty("core.appName");
        if (appName == null) {
            LOGGER.info("not found -Dcore.appName, this should only happen in local dev env or test, use \"local\" as appName");
            appName = "local";
        }
        APP_NAME = appName;
    }

    public final LogFilter filter = new LogFilter();
    public Appender appender;

    public ActionLog begin(String message) {
        var actionLog = new ActionLog(message);
        CURRENT_ACTION_LOG.set(actionLog);
        return actionLog;
    }

    public void end(String message) {
        ActionLog actionLog = CURRENT_ACTION_LOG.get();
        CURRENT_ACTION_LOG.remove();
        actionLog.end(message);

        if (appender != null) appender.append(actionLog, filter);
    }

    public void logError(Throwable e) {
        String errorMessage = e.getMessage();
        String errorCode = errorCode(e);
        Marker marker = Markers.errorCode(errorCode);
        if (e instanceof ErrorCode && ((ErrorCode) e).severity() == Severity.WARN) {
            LOGGER.warn(marker, errorMessage, e);
        } else {
            LOGGER.error(marker, errorMessage, e);
        }
    }

    String errorCode(Throwable e) {
        return e instanceof ErrorCode ? ((ErrorCode) e).errorCode() : e.getClass().getCanonicalName();
    }
}
