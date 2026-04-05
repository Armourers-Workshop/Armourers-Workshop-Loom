package net.cocoonmc.loom.api;

import org.gradle.api.Action;
import org.gradle.api.provider.Property;

public interface CocoonExtension {

    void common(Action<? super CocoonSettings> action);

    void fabric(Action<? super CocoonSettings> action);

    void forge(Action<? super CocoonSettings> action);

    Property<String> getMinecraft();

    /// Defaults: official
    Property<String> getMappings();

    ///  Defaults: false
    Property<Boolean> getCompileOnly();
}
