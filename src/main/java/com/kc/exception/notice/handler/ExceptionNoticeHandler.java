package com.kc.exception.notice.handler;

import com.kc.exception.notice.annotation.TargetExceptionNotice;
import com.kc.exception.notice.content.ExceptionInfo;
import com.kc.exception.notice.process.INoticeProcessor;
import com.kc.exception.notice.properties.ExceptionNoticeProperties;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 异常信息通知前处理
 *
 * @author kongchong
 */
@Slf4j
public class ExceptionNoticeHandler {

    private final String separator = System.lineSeparator();

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    private final BlockingQueue<ExceptionInfo> exceptionInfoBlockingDeque = new ArrayBlockingQueue<>(1024);

    private final ExceptionNoticeProperties exceptionProperties;

    private final List<INoticeProcessor> noticeProcessors;

    public ExceptionNoticeHandler(ExceptionNoticeProperties exceptionProperties,
                                  List<INoticeProcessor> noticeProcessors) {
        this.exceptionProperties = exceptionProperties;
        this.noticeProcessors = noticeProcessors;
    }

    /**
     * 将捕获到的异常信息封装好之后发送到阻塞队列
     */
    @SuppressWarnings("java:S899")
    public void createNotice(Exception ex, JoinPoint joinPoint) {
        if (excludeException(ex)) {
            return;
        }
        log.error("捕获到异常开始发送消息通知:{}method:{}--->", separator, joinPoint.getSignature().getName());
        // 获取请求参数
        Object parameter = getParameter(joinPoint);
        // 获取当前请求对象
        ExceptionInfo exceptionInfo = getExceptionInfo(ex, joinPoint, parameter);
        offerQueue(exceptionInfo);
    }

    public void createTargetNotice(Exception ex, JoinPoint joinPoint) {
        if (excludeException(ex)) {
            return;
        }
        TargetExceptionNotice annotation = getTargetExceptionNoticeAnnotation(joinPoint);
        String targetWebhook = null;
        if (annotation != null) {
            int webHookIndex = Integer.parseInt(annotation.webHookIndex());
            boolean targetShowTrace = annotation.showTrace();
            // TargetExceptionNotice注解 是否显示堆栈
            if(!targetShowTrace) {
                exceptionProperties.setShowTrace(false);
            }

            String[] webHooks = exceptionProperties.getWeChat().getWebHooks();
            Assert.isTrue(webHooks.length > webHookIndex, "webHook array must greater than webHookIndex");
            targetWebhook = webHooks[webHookIndex];
        }
        log.error("捕获到异常开始发送消息通知:{}method:{}--->", separator, joinPoint.getSignature().getName());
        // 获取请求参数
        Object parameter = getParameter(joinPoint);
        // 获取当前请求对象
        ExceptionInfo exceptionInfo = getExceptionInfo(ex, joinPoint, parameter);
        exceptionInfo.setTargetWebhook(targetWebhook);
        offerQueue(exceptionInfo);
    }

    private void offerQueue(ExceptionInfo exceptionInfo) {
        // 仅发送追踪的文件夹
        if (exceptionInfo.getClassPath() != null && !exceptionInfo.getClassPath().isEmpty()) {
            exceptionInfo.setProject(exceptionProperties.getProjectName());
            exceptionInfoBlockingDeque.offer(exceptionInfo);
        }
    }

    private ExceptionInfo getExceptionInfo(Exception ex, JoinPoint joinPoint, Object parameter) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String address = null;
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            // 获取请求地址
            address = request.getRequestURL().toString() + ((request.getQueryString() != null && !request.getQueryString().isEmpty()) ? "?" + request.getQueryString() : "");
        }

        return new ExceptionInfo(ex, joinPoint.getSignature().getName(), exceptionProperties, parameter, address);
    }

    /**
     * 启动定时任务发送异常通知
     */
    public void start() {
        executor.scheduleAtFixedRate(() -> {
            ExceptionInfo exceptionInfo = exceptionInfoBlockingDeque.poll();
            if (null != exceptionInfo) {
                noticeProcessors.forEach(noticeProcessor -> noticeProcessor.sendNotice(exceptionInfo));
            }
        }, 6, exceptionProperties.getPeriod(), TimeUnit.SECONDS);
    }

    private boolean excludeException(Exception exception) {
        Class<? extends Exception> exceptionClass = exception.getClass();
        List<Class<? extends Exception>> list = exceptionProperties.getExcludeExceptions();
        for (Class<? extends Exception> clazz : list) {
            if (clazz.isAssignableFrom(exceptionClass)) {
                return true;
            }
        }
        return false;
    }


    /**
     * 根据方法和传入的参数获取请求参数
     */
    private Object getParameter(JoinPoint joinPoint) {
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        Method method = methodSignature.getMethod();
        Parameter[] parameters = method.getParameters();

        Object[] args = joinPoint.getArgs();
        List<Object> argList = new ArrayList<>(parameters.length);
        for (int i = 0; i < parameters.length; i++) {
            RequestBody requestBody = parameters[i].getAnnotation(RequestBody.class);
            if (requestBody != null) {
                argList.add(args[i]);
            }
            RequestParam requestParam = parameters[i].getAnnotation(RequestParam.class);
            if (requestParam != null) {
                Map<String, Object> map = new HashMap<>(1);
                String key = parameters[i].getName();
                if (!StringUtils.isEmpty(requestParam.value())) {
                    key = requestParam.value();
                }
                map.put(key, args[i]);
                argList.add(map);
            }
        }
        if (argList.isEmpty()) {
            return null;
        } else if (argList.size() == 1) {
            return argList.get(0);
        } else {
            return argList;
        }
    }


    // 获取目标方法上的TargetExceptionNotice注解
    private TargetExceptionNotice getTargetExceptionNoticeAnnotation(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        if (method.isAnnotationPresent(TargetExceptionNotice.class)) {
            return method.getAnnotation(TargetExceptionNotice.class);
        }
        return null;
    }
}
