package net.cocoonmc.loom;

import net.cocoonmc.loom.runtime.CocoonPluginImpl;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class CocoonPlugin implements Plugin<Project> {

    public static Project commonProject;

    @Override
    public void apply(Project project) {
        var plugin = new CocoonPluginImpl(project);

        // prepare the cocoon plugin in all projects.
        plugin.prepare();

        // we do not inject tasks into the root project.
        if (project.getParent() == null) {
            return;
        }

        // apply the cocoon plugin in subprojects.
        plugin.apply();
    }
}
