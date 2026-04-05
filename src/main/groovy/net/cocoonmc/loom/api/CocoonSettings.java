package net.cocoonmc.loom.api;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

public interface CocoonSettings {

    Property<String> getApi();

    Property<String> getLoader();

    RegularFileProperty getAccessWidenerPath();
}
