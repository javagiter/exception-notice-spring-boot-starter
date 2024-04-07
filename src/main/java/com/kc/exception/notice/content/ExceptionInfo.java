package com.kc.exception.notice.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kc.exception.notice.properties.ExceptionNoticeProperties;
import lombok.Data;
import lombok.SneakyThrows;
import org.springframework.util.DigestUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * 异常信息数据model
 *
 * @author kongchong
 */
@Data
public class ExceptionInfo {

    public static final String FONT_STR = "</font>";
    public static final String YYYYMMDD_HHMMSS_STR = "yyyy-MM-dd HH:mm:ss";
    /**
     * 工程名
     */
    private String project;

    /**
     * 异常的标识码
     */
    private String uid;

    /**
     * 请求地址
     */
    private String reqAddress;

    /**
     * 方法名
     */
    private String methodName;

    /**
     * 方法参数信息
     */
    private Object params;

    /**
     * 类路径
     */
    private String classPath;

    /**
     * 异常信息
     */
    private String exceptionMessage;

    /**
     * 自定义目标地址
     */
    private String targetWebhook;

    /**
     * 异常追踪信息
     */
    private List<String> traceInfo = new ArrayList<>();

    /**
     * 最后一次出现的时间
     */
    private LocalDateTime latestShowTime = LocalDateTime.now();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExceptionInfo(Throwable ex, String methodName, ExceptionNoticeProperties exceptionProperties, Object args, String reqAddress) {
        this.exceptionMessage = gainExceptionMessage(ex);
        this.reqAddress = reqAddress;
        this.params = args;
        List<String> includeTracePackages = exceptionProperties.getIncludedTracePackages();
        boolean isShowTrace = exceptionProperties.isShowTrace();
        List<StackTraceElement> list = Arrays.stream(ex.getStackTrace())
                .filter(x -> includeTracePackages == null || includeTracePackages.stream().allMatch(y -> x.getClassName().startsWith(y)))
                .filter(x -> !"<generated>".equals(x.getFileName()))
                .collect(toList());
        if (!list.isEmpty()) {
            this.classPath = list.get(0).getClassName();
            this.methodName = null == methodName ? list.get(0).getMethodName() : methodName;
            if (isShowTrace) {
                this.traceInfo = list.stream().map(StackTraceElement::toString).collect(toList());
            }
        }
        this.uid = calUid();
    }


    private String gainExceptionMessage(Throwable exception) {
        String em = exception.toString();
        if (exception.getCause() != null) {
            em = em + "\r\n\tcaused by : " + gainExceptionMessage(exception.getCause());
        }
        return em;
    }

    private String calUid() {
        return DigestUtils.md5DigestAsHex(
                String.format("%s-%s", exceptionMessage, !traceInfo.isEmpty() ? traceInfo.get(0) : "").getBytes());
    }

    @SneakyThrows
    public String createText() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("项目名称：").append(project).append("\n");
        stringBuilder.append("类路径：").append(classPath).append("\n");
        stringBuilder.append("请求地址：").append(reqAddress).append("\n");
        stringBuilder.append("方法名：").append(methodName).append("\n");
        if (params != null) {
            stringBuilder.append("方法参数：").append(objectMapper.writeValueAsString(params)).append("\n");
        }
        stringBuilder.append("异常信息：").append("\n").append(exceptionMessage).append("\n");
        stringBuilder.append("异常追踪：").append("\n").append(String.join("\n", traceInfo)).append("\n");
        stringBuilder.append("最后一次出现时间：")
                .append(latestShowTime.format(DateTimeFormatter.ofPattern(YYYYMMDD_HHMMSS_STR)));
        return stringBuilder.toString();
    }

    @SneakyThrows
    public String createWeChatMarkDown() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(">项目名称：<font color=\"info\">").append(project).append(FONT_STR).append("\n");
        stringBuilder.append(">类路径：<font color=\"info\">").append(classPath).append(FONT_STR).append("\n");
        stringBuilder.append(">请求地址：<font color=\"info\">").append(reqAddress).append(FONT_STR).append("\n");
        stringBuilder.append(">方法名：<font color=\"info\">").append(methodName).append(FONT_STR).append("\n");
        if (params != null) {
            stringBuilder.append(">方法参数：<font color=\"info\">").append(objectMapper.writeValueAsString(params)).append(FONT_STR).append("\n");
        }
        stringBuilder.append(">异常信息：<font color=\"red\">").append("\n").append(exceptionMessage).append(FONT_STR).append("\n");
        stringBuilder.append(">异常追踪：<font color=\"info\">").append("\n").append(String.join("\n", traceInfo)).append(FONT_STR).append("\n");
        stringBuilder.append(">最后一次出现时间：<font color=\"info\">")
                .append(latestShowTime.format(DateTimeFormatter.ofPattern(YYYYMMDD_HHMMSS_STR))).append(FONT_STR);
        return stringBuilder.toString();
    }

    @SneakyThrows
    public String createDingTalkMarkDown() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("#### 项目名称：").append("\n").append("> ").append(project).append("\n");
        stringBuilder.append("#### 类路径：").append("\n").append("> ").append(classPath).append("\n");
        stringBuilder.append("#### 请求地址：").append("\n").append("> ").append(reqAddress).append("\n");
        stringBuilder.append("#### 方法名：").append("\n").append("> ").append(methodName).append("\n");
        if (params != null) {
            stringBuilder.append("#### 方法参数：").append(objectMapper.writeValueAsString(params)).append("\n");
        }
        stringBuilder.append("#### 异常信息：").append("\n").append("> ").append(exceptionMessage).append("\n");
        stringBuilder.append("#### 异常追踪：").append("\n").append("> ").append(String.join("\n", traceInfo)).append("\n");
        stringBuilder.append("#### 最后一次出现时间：")
                .append(latestShowTime.format(DateTimeFormatter.ofPattern(YYYYMMDD_HHMMSS_STR)));
        return stringBuilder.toString();
    }

}
