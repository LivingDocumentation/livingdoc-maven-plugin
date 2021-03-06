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
import org.apache.maven.plugins.annotations.LifecyclePhase;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.github.livingdocumentation.maven.commons.SimpleTemplate.*;
import static io.github.livingdocumentation.dotdiagram.DotStyles.ASSOCIATION_EDGE_STYLE;
import static io.github.livingdocumentation.dotdiagram.DotStyles.IMPLEMENTS_EDGE_STYLE;

/**
 * Living Diagram of the Hexagonal Architecture generated out of the code thanks
 * to the package naming conventions.
 *
 */

@Mojo(name = "diagram", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
		defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class DiagramMojo extends AbstractMojo {

	private final DotGraph graph = new DotGraph("Hexagonal Architecture", "LR");

	/**
	 * The project being processed by the plugin (readonly)
	 */
	@Parameter(defaultValue="${project}", readonly=true, required=true)
	private MavenProject project;

	/**
	 * Package name prefix of classes included in the diagram
	 */
	@Parameter
	private String prefix;

	/**
	 * End of package name that contains core business domain classes
	 */
	@Parameter(defaultValue = "domain")
	private String coreDomain;

	/**
	 * Directory where the diagram files will be generated
	 */
	@Parameter(defaultValue = "${project.build.directory}/generated-docs")
	private File outputDirectory;

	/**
	 * List of packages to exclude. Specified as regexp.
	 */
	@Parameter
	private String[] packageExcludes = new String[0];

	/**
	 * List of packages suffix to map to a cluster in diagram
	 */
	@Parameter
	private String[] clusters = new String[0];


	@Override
	public void execute() throws MojoExecutionException {
		final ClassPath classPath = initClassPath();

		final ImmutableSet<ClassInfo> allClasses = classPath.getTopLevelClassesRecursive(prefix);

		final Digraph digraph = graph.getDigraph();
		digraph.setOptions("rankdir=LR");

		final Cluster core = digraph.addCluster("hexagon");
		core.setLabel("Core Domain");

		Map<String, Cluster> digraphClusters = Arrays.stream(clusters)
				.map(s -> {Cluster c = digraph.addCluster(s);
					c.setLabel(s); return c;})
				.collect(Collectors.toMap(Cluster::getLabel, Function.identity()));

		// add all domain model elements first
		allClasses.stream().filter(filter(prefix, coreDomain)).map(ClassInfo::load).forEach(clazz -> addNode(core, clazz));

		// add clusters
		if (clusters.length > 0) {
			allClasses.stream().filter(filter(prefix, clusters))
					.forEach(ci -> addNode(digraphClusters.get(getSuffixe(ci.getPackageName())), ci.load()));
		}

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

	private ClassLoader getRuntimeClassLoader() throws DependencyResolutionRequiredException, MalformedURLException {
		List<String> runtimeClasspathElements = project.getRuntimeClasspathElements();
		List<String> compileClasspathElements = project.getCompileClasspathElements();
		URL[] runtimeUrls = new URL[runtimeClasspathElements.size() + compileClasspathElements.size()];
		for (int i = 0; i < runtimeClasspathElements.size(); i++) {
			String element = runtimeClasspathElements.get(i);
			runtimeUrls[i] = new File(element).toURI().toURL();
		}

		int j = runtimeClasspathElements.size();

		for (int i = 0; i < compileClasspathElements.size(); i++) {
			String element = compileClasspathElements.get(i);
			runtimeUrls[i + j] = new File(element).toURI().toURL();
		}

		return new URLClassLoader(runtimeUrls, Thread.currentThread().getContextClassLoader());
	}

	private String getSuffixe(String packageName) {
		return packageName.substring(packageName.lastIndexOf('.')+1);
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
				&& ci.getPackageName().endsWith("." + layer)
				&& Arrays.stream(packageExcludes).noneMatch( excl -> ci.getPackageName().matches(excl) ) ;
	}

	private Predicate<ClassInfo> filter(final String prefix, final String[] clusters) {
		return ci -> ci.getPackageName().startsWith(prefix)
				&& !ci.getSimpleName().endsWith("Test") && !ci.getSimpleName().endsWith("IT")
				&& Arrays.stream(clusters).anyMatch(c -> ci.getPackageName().endsWith("." + c))
				&& Arrays.stream(packageExcludes).noneMatch( excl -> ci.getPackageName().matches(excl) ) ;
	}

	private Predicate<ClassInfo> filterNot(final String prefix, final String layer) {
		return ci -> ci.getPackageName().startsWith(prefix)
				&& !ci.getSimpleName().endsWith("Test") && !ci.getSimpleName().endsWith("IT")
				&& !ci.getPackageName().endsWith("." + layer)
				&& Arrays.stream(packageExcludes).noneMatch( excl -> ci.getPackageName().matches(excl) );
	}

}
