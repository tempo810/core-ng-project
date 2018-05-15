package core.framework.impl.module;

import core.framework.http.HTTPMethod;
import core.framework.impl.inject.BeanFactory;
import core.framework.impl.inject.ShutdownHook;
import core.framework.impl.log.DefaultLoggerFactory;
import core.framework.impl.log.LogManager;
import core.framework.impl.log.stat.Stat;
import core.framework.impl.reflect.Classes;
import core.framework.impl.web.ControllerClassValidator;
import core.framework.impl.web.ControllerHolder;
import core.framework.impl.web.ControllerInspector;
import core.framework.impl.web.HTTPServer;
import core.framework.impl.web.management.MemoryUsageController;
import core.framework.impl.web.management.PropertyController;
import core.framework.impl.web.management.ThreadInfoController;
import core.framework.impl.web.route.PathPatternValidator;
import core.framework.util.ASCII;
import core.framework.util.Exceptions;
import core.framework.util.Lists;
import core.framework.util.Maps;
import core.framework.web.Controller;
import core.framework.web.WebContext;
import core.framework.web.site.WebDirectory;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author neo
 */
public class ModuleContext {
    public final BeanFactory beanFactory;
    public final List<Runnable> startupHook = Lists.newArrayList();
    public final ShutdownHook shutdownHook = new ShutdownHook();
    public final PropertyManager propertyManager = new PropertyManager();
    public final HTTPServer httpServer;
    public final LogManager logManager;
    public final Stat stat = new Stat();
    protected final Map<String, Object> configs = Maps.newHashMap();
    private BackgroundTaskExecutor backgroundTask;

    public ModuleContext(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;

        logManager = ((DefaultLoggerFactory) LoggerFactory.getILoggerFactory()).logManager;

        httpServer = new HTTPServer(logManager);
        beanFactory.bind(WebContext.class, null, httpServer.handler.webContext);
        beanFactory.bind(WebDirectory.class, null, httpServer.siteManager.webDirectory);
        startupHook.add(httpServer::start);
        shutdownHook.add(httpServer::stop);

        route(HTTPMethod.GET, "/_sys/memory", new MemoryUsageController(), true);
        ThreadInfoController threadInfoController = new ThreadInfoController();
        route(HTTPMethod.GET, "/_sys/thread", threadInfoController::threadUsage, true);
        route(HTTPMethod.GET, "/_sys/thread-dump", threadInfoController::threadDump, true);
        PropertyController propertyController = new PropertyController(propertyManager);
        route(HTTPMethod.GET, "/_sys/property", propertyController, true);
    }

    public BackgroundTaskExecutor backgroundTask() {
        if (backgroundTask == null) {
            backgroundTask = new BackgroundTaskExecutor();
            startupHook.add(backgroundTask::start);
            shutdownHook.add(backgroundTask::stop);
        }
        return backgroundTask;
    }

    public void route(HTTPMethod method, String path, Controller controller, boolean skipInterceptor) {
        new PathPatternValidator(path).validate();
        ControllerInspector inspector = new ControllerInspector(controller);
        new ControllerClassValidator(inspector.targetClass, inspector.targetMethod).validate();
        String action = "http:" + ASCII.toLowerCase(method.name()) + ":" + path;
        httpServer.handler.route.add(method, path, new ControllerHolder(controller, inspector.targetMethod, inspector.controllerInfo, action, skipInterceptor));
    }

    @SuppressWarnings("unchecked")
    public <T> T config(Class<T> configClass, String name) {    // use weak type convention to hide unnecessary method from config class
        return (T) configs.computeIfAbsent(configClass.getCanonicalName() + ":" + name, key -> {
            Class<T> targetConfigClass = targetConfigClass(configClass);
            try {
                Optional<Constructor<T>> constructorOptional = Classes.constructor(targetConfigClass, ModuleContext.class, String.class);
                if (constructorOptional.isPresent()) {
                    Constructor<T> constructor = constructorOptional.get();
                    constructor.setAccessible(true);
                    return constructor.newInstance(this, name);
                }
                constructorOptional = Classes.constructor(targetConfigClass, ModuleContext.class);
                if (constructorOptional.isPresent()) {
                    if (name != null) throw Exceptions.error("config class does not support name, configClass={}, name={}", targetConfigClass.getCanonicalName(), name);
                    Constructor<T> constructor = constructorOptional.get();
                    constructor.setAccessible(true);
                    return constructor.newInstance(this);
                }
                throw Exceptions.error("config class must have public constructor(context, name?), configClass={}", targetConfigClass.getCanonicalName());
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new Error(e);
            }
        });
    }

    public void validate() {    // call validate if declared, use weak type convention to hide unnecessary method from config class
        configs.values().forEach(config -> validateMethod(config).ifPresent(method -> {
            try {
                method.setAccessible(true);
                method.invoke(config);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new Error(e);
            }
        }));
    }

    protected <T> Class<T> targetConfigClass(Class<T> configClass) {
        return configClass;
    }

    protected Optional<Method> validateMethod(Object config) {
        return Classes.method(config.getClass(), "validate");
    }
}
