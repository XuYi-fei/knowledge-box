package com.knowledgebox.service.integration;

import io.agentscope.core.tool.Tool;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ToolRuntimeFactoryService {

    private final ApplicationContext applicationContext;
    private final AutowireCapableBeanFactory beanFactory;

    public ToolRuntimeFactoryService(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.beanFactory = applicationContext.getAutowireCapableBeanFactory();
    }

    public ToolValidationResult validate(String className, String beanName) {
        Class<?> clazz = resolveClass(className);
        List<String> toolNames = collectToolNames(clazz);
        if (toolNames.isEmpty()) {
            throw new IllegalArgumentException("Tool class does not expose any @Tool method: " + className);
        }
        createToolObject(className, beanName);
        return new ToolValidationResult(clazz.getName(), toolNames);
    }

    public Object createToolObject(String className, String beanName) {
        Class<?> clazz = resolveClass(className);
        if (StringUtils.hasText(beanName)) {
            if (!applicationContext.containsBean(beanName)) {
                throw new IllegalArgumentException("Tool bean not found: " + beanName);
            }
            Object bean = applicationContext.getBean(beanName);
            if (!clazz.isInstance(bean)) {
                throw new IllegalArgumentException(
                        "Configured bean '" + beanName + "' is not an instance of class " + clazz.getName()
                );
            }
            return bean;
        }

        try {
            return applicationContext.getBean(clazz);
        } catch (Exception ignore) {
            return beanFactory.createBean(clazz);
        }
    }

    private Class<?> resolveClass(String className) {
        if (!StringUtils.hasText(className)) {
            throw new IllegalArgumentException("Tool className is required");
        }
        try {
            return Class.forName(className.strip());
        } catch (ClassNotFoundException exception) {
            throw new IllegalArgumentException("Tool class not found: " + className, exception);
        }
    }

    private List<String> collectToolNames(Class<?> clazz) {
        List<String> names = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Tool.class)) {
                continue;
            }
            Tool annotation = method.getAnnotation(Tool.class);
            String toolName = StringUtils.hasText(annotation.name()) ? annotation.name().trim() : method.getName();
            names.add(toolName);
        }
        return names;
    }

    public record ToolValidationResult(String className, List<String> toolNames) {
    }
}
