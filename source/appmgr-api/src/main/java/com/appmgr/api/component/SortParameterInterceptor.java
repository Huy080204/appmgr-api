package com.appmgr.api.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManagerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rejects {@code ?sort=} values that are not property paths of the entity the handler sorts,
 * on handlers that take {@link Pageable} or {@link Sort}, before Spring Data parses them.
 */
@Slf4j
@Component
public class SortParameterInterceptor implements HandlerInterceptor {
    private static final String SORT_PARAMETER = "sort";
    private static final int MAX_SORT_ORDERS = 3;
    // "no criteria entity found": not an entity, so the validator checks against all entities
    private static final Class<?> UNKNOWN_ENTITY = Object.class;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private SortParameterValidator validator;

    private final Map<Method, Class<?>> entityByHandler = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        Map<Class<?>, Set<String>> paths = SortParameterValidator.sortablePathsByEntity(entityManagerFactory.getMetamodel());
        validator = new SortParameterValidator(paths, MAX_SORT_ORDERS);
        log.info("Sort parameter whitelist loaded for {} entities", paths.size());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod) || !acceptsSort((HandlerMethod) handler)) {
            return true;
        }
        Class<?> entity = entityByHandler.computeIfAbsent(((HandlerMethod) handler).getMethod(), SortParameterInterceptor::sortedEntity);
        validator.validate(entity, request.getParameterValues(SORT_PARAMETER));
        return true;
    }

    private static boolean acceptsSort(HandlerMethod handler) {
        for (MethodParameter parameter : handler.getMethodParameters()) {
            Class<?> type = parameter.getParameterType();
            if (Pageable.class.isAssignableFrom(type) || Sort.class.isAssignableFrom(type)) {
                return true;
            }
        }
        return false;
    }

    static Class<?> sortedEntity(Method handlerMethod) {
        for (Class<?> parameterType : handlerMethod.getParameterTypes()) {
            Class<?> entity = specificationEntity(parameterType);
            if (entity != null) {
                return entity;
            }
        }
        log.warn("No criteria entity for {}#{}: sort is checked against all entities",
                handlerMethod.getDeclaringClass().getSimpleName(), handlerMethod.getName());
        return UNKNOWN_ENTITY;
    }

    // criteria classes expose a no-arg method returning Specification<Entity> (getCriteria / getSpecification)
    private static Class<?> specificationEntity(Class<?> criteriaType) {
        for (Method method : criteriaType.getMethods()) {
            Type returnType = method.getGenericReturnType();
            if (method.getParameterCount() == 0 && Specification.class.equals(method.getReturnType())
                    && returnType instanceof ParameterizedType) {
                Type argument = ((ParameterizedType) returnType).getActualTypeArguments()[0];
                if (argument instanceof Class) {
                    return (Class<?>) argument;
                }
            }
        }
        return null;
    }
}
