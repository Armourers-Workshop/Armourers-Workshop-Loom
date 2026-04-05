package net.cocoonmc.loom.core;

@FunctionalInterface
public interface ThrowableAction<T> {

    void execute(T var1) throws Exception;
}
