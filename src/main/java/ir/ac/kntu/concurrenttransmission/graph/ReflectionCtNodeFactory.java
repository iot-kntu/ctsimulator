package ir.ac.kntu.concurrenttransmission.graph;

import ir.ac.kntu.concurrenttransmission.CtNode;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Reflection-based {@link CtNodeFactory} that tries to resolve node classes
 * inside one or more base packages. The factory supports both simple class
 * names and fully-qualified names provided in the graph files.
 */
public class ReflectionCtNodeFactory implements CtNodeFactory {

    private final List<String> basePackages;

    public ReflectionCtNodeFactory(List<String> basePackages) {
        Objects.requireNonNull(basePackages);
        this.basePackages = Collections.unmodifiableList(new ArrayList<>(basePackages));
    }

    public ReflectionCtNodeFactory(String basePackage) {
        this(List.of(basePackage));
    }

    @Override
    public CtNode create(String typeName, int nodeId) {
        Objects.requireNonNull(typeName, "Node type must not be null");

        List<String> candidates = buildCandidates(typeName);
        for (String candidate : candidates) {
            try {
                Class<?> nodeClass = Class.forName(candidate);
                Constructor<?> ctor = findConstructor(nodeClass);
                Object instance = ctor.getParameterCount() == 1
                        ? ctor.newInstance(nodeId)
                        : ctor.newInstance();
                return (CtNode) instance;
            } catch (ReflectiveOperationException ignored) {
                // try next candidate
            }
        }
        throw new IllegalArgumentException("Cannot create node of type '" + typeName + "'");
    }

    private Constructor<?> findConstructor(Class<?> nodeClass) throws NoSuchMethodException {
        try {
            Constructor<?> ctor = nodeClass.getDeclaredConstructor(Integer.class);
            ctor.setAccessible(true);
            return ctor;
        } catch (NoSuchMethodException e) {
            try {
                Constructor<?> ctor = nodeClass.getDeclaredConstructor(int.class);
                ctor.setAccessible(true);
                return ctor;
            } catch (NoSuchMethodException ex) {
                Constructor<?> ctor = nodeClass.getDeclaredConstructor();
                ctor.setAccessible(true);
                return ctor;
            }
        }
    }

    private List<String> buildCandidates(String typeName) {
        if (typeName.contains(".")) {
            return List.of(typeName);
        }
        List<String> candidates = new ArrayList<>(basePackages.size());
        for (String basePackage : basePackages) {
            candidates.add(basePackage + "." + typeName);
        }
        return candidates;
    }
}
