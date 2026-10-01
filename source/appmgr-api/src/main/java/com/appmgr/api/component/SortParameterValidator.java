package com.appmgr.api.component;

import com.appmgr.api.exception.BadRequestException;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import javax.persistence.metamodel.*;
import java.util.*;
import java.util.stream.Collectors;

public class SortParameterValidator {
    private static final String IGNORE_CASE = "ignorecase";

    private final Map<Class<?>, Set<String>> pathsByEntity;
    private final Set<String> allPaths;
    private final int maxOrders;

    public SortParameterValidator(Map<Class<?>, Set<String>> pathsByEntity, int maxOrders) {
        this.pathsByEntity = pathsByEntity;
        this.allPaths = pathsByEntity.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        this.maxOrders = maxOrders;
    }

    public List<String> validate(Class<?> entity, String[] sortValues) {
        List<String> properties = new ArrayList<>();
        if (sortValues == null) {
            return properties;
        }
        Set<String> allowed = pathsByEntity.getOrDefault(entity, allPaths);
        for (String value : sortValues) {
            for (String property : propertyTokens(value)) {
                if (!allowed.contains(property)) {
                    throw new BadRequestException("Invalid sort parameter");
                }
                properties.add(property);
                if (properties.size() > maxOrders) {
                    throw new BadRequestException("Too many sort properties, max " + maxOrders);
                }
            }
        }
        return properties;
    }

    // mirrors Spring Data 2.7.18 SortOrderParser: blank/dot-only tokens dropped, keywords only as
    // trailing [direction][,ignorecase], no trim — anything else reaches the query as a property
    private static List<String> propertyTokens(String value) {
        String[] tokens = Arrays.stream(value.split(","))
                .filter(token -> StringUtils.hasText(token.replace(".", "")))
                .toArray(String[]::new);
        int end = tokens.length;
        if (end > 0 && IGNORE_CASE.equalsIgnoreCase(tokens[end - 1])) {
            end--;
        }
        if (end > 0 && Sort.Direction.fromOptionalString(tokens[end - 1]).isPresent()) {
            end--;
        }
        return Arrays.asList(tokens).subList(0, end);
    }

    public static Map<Class<?>, Set<String>> sortablePathsByEntity(Metamodel metamodel) {
        Map<Class<?>, Set<String>> pathsByEntity = new HashMap<>();
        for (EntityType<?> entity : metamodel.getEntities()) {
            Set<String> paths = new HashSet<>();
            for (Attribute<?, ?> attribute : entity.getAttributes()) {
                if (attribute instanceof SingularAttribute) {
                    paths.add(attribute.getName());
                    addNestedPaths(paths, attribute.getName(), ((SingularAttribute<?, ?>) attribute).getType());
                }
            }
            pathsByEntity.put(entity.getJavaType(), Collections.unmodifiableSet(paths));
        }
        return Collections.unmodifiableMap(pathsByEntity);
    }

    // not recursive: exactly one nested level ("group.name", never "group.owner.email")
    private static void addNestedPaths(Set<String> paths, String parent, Type<?> type) {
        if (!(type instanceof ManagedType)) {
            return;
        }
        for (Attribute<?, ?> nested : ((ManagedType<?>) type).getAttributes()) {
            if (nested instanceof SingularAttribute) {
                paths.add(parent + "." + nested.getName());
            }
        }
    }
}
