package io.github.livingdocumentation.maven;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import io.github.livingdocumentation.dotdiagram.DotGraph;
import io.github.livingdocumentation.dotdiagram.DotGraph.Cluster;
import io.github.livingdocumentation.dotdiagram.DotGraph.Digraph;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.function.Predicate;

import static io.github.livingdocumentation.maven.SimpleTemplate.*;
import static io.github.livingdocumentation.dotdiagram.DotStyles.ASSOCIATION_EDGE_STYLE;
import static io.github.livingdocumentation.dotdiagram.DotStyles.IMPLEMENTS_EDGE_STYLE;

/**
 * Living Diagram of the Hexagonal Architecture generated out of the code thanks
 * to the package naming conventions.
 *
 */

@Mojo(name = "diagram", requiresDependencyResolution = ResolutionScope.RUNTIME)
public class DiagramMojo extends AbstractMojo {

	private final DotGraph graph = new DotGraph("Hexagonal Architecture", "LR");

	@Parameter(defaultValue="${project}", readonly=true, required=true)
	private MavenProject project;

	@Parameter
	private String prefix;

	@Parameter(defaultValue = "domain")
	private String coreDomain;

	@Parameter(defaultValue = "${project.build.directory}/generated-docs")
	private File outputDirectory;

	@Override
	public void execute() throws MojoExecutionException {
		final ClassPath classPath = initClassPath();

		final ImmutableSet<ClassInfo> allClasses = classPath.getTopLevelClassesRecursive(prefix);

		final Digraph digraph = graph.getDigraph();
		digraph.setOptions("rankdir=LR");

		final Cluster core = digraph.addCluster("hexagon");
		core.setLabel("Core Domain");

		// add all domain model elements first
		allClasses.stream().filter(filter(prefix, coreDomain)).map(ClassInfo::load).forEach(clazz -> addNode(core, clazz));

		allClasses.stream().filter(filterNot(prefix, coreDomain)).map(ClassInfo::load).forEach(clazz -> addNode(digraph, clazz));

		allClasses.stream().filter(filterNot(prefix, coreDomain)).map(ClassInfo::load).forEach(clazz -> addAssociations(digraph, clazz));

		// then wire them together
		allClasses.stream().filter(filter(prefix, coreDomain)).map(ClassInfo::load).forEach(clazz -> addAssociations(digraph, clazz));

		// render into image
		final String template = readResource("/viz-template.html");

		String title = "Living Diagram";
		final String content = graph.render().trim();
		final String text = evaluate(template, title, content);
		try {
			outputDirectory.mkdirs();
			write(outputDirectory.getPath(), "/livinggdiagram.html", text);
		} catch (UnsupportedEncodingException | FileNotFoundException e) {
			throw new MojoExecutionException("Unable to initialize classPath", e);
		}
	}

	private ClassPath initClassPath() throws MojoExecutionException {
		final ClassPath classPath;
		try {
			try {
				classPath = ClassPath.from(getRuntimeClassLoader());
			} catch (DependencyResolutionRequiredException e) {
				throw new MojoExecutionException("Unable to load project runtime", e);
			}
		} catch (IOException e) {
			throw new MojoExecutionException("Unable to initialize classPath", e);
		}
		return classPath;
	}

	private void addAssociations(Digraph digraph, Class clazz) {
		// API
		for (Field field : clazz.getDeclaredFields()) {
            final Class<?> type = field.getType();
            if (!type.isPrimitive()) {
                digraph.addExistingAssociation(clazz.getName(), type.getName(), null, null, ASSOCIATION_EDGE_STYLE);
            }
        }
		// SPI
		for (Class intf : clazz.getInterfaces()) {
            digraph.addExistingAssociation(intf.getName(), clazz.getName(), null, null, IMPLEMENTS_EDGE_STYLE);
        }
	}

	private void addNode(DotGraph.AbstractNode digraph, Class clazz) {
		if (!clazz.getSimpleName().equalsIgnoreCase("package-info"))
			digraph.addNode(clazz.getName()).setLabel(clazz.getSimpleName()).setComment(clazz.getSimpleName());
	}

	private Predicate<ClassInfo> filter(final String prefix, final String layer) {
		return ci -> ci.getPackageName().startsWith(prefix)
				&& !ci.getSimpleName().endsWith("Test") && !ci.getSimpleName().endsWith("IT")
				&& ci.getPackageName().endsWith("." + layer);
	}

	private Predicate<ClassInfo> filterNot(final String prefix, final String layer) {
		return ci -> ci.getPackageName().startsWith(prefix)
				&& !ci.getSimpleName().endsWith("Test") && !ci.getSimpleName().endsWith("IT")
				&& !ci.getPackageName().endsWith("." + layer);
	}

	private ClassLoader getRuntimeClassLoader() throws DependencyResolutionRequiredException, MalformedURLException {
		List<String> runtimeClasspathElements = project.getRuntimeClasspathElements();
		URL[] runtimeUrls = new URL[runtimeClasspathElements.size()];
		for (int i = 0; i < runtimeClasspathElements.size(); i++) {
			String element = runtimeClasspathElements.get(i);
			runtimeUrls[i] = new File(element).toURI().toURL();
		}
		return new URLClassLoader(runtimeUrls, Thread.currentThread().getContextClassLoader());
	}

}
