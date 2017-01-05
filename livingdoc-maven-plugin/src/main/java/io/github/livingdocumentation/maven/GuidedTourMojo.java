package io.github.livingdocumentation.maven;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.*;
import io.github.livingdocumentation.maven.commons.SimpleTemplate;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.*;
import java.text.MessageFormat;
import java.util.*;

/**
 *
 */
@Mojo(name = "tour", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class GuidedTourMojo extends AbstractMojo {

    private static final String SEP = "\n\n";

    @Parameter(defaultValue = "${project.build.sourceDirectory}", readonly=true)
    private List<String> sources;

    /**
     * Package prefix of classes to browse for Guided Tour documentation
     */
    @Parameter
    private String prefix;

    /**
     * Fully qualified annotation marking classes of the guided tour
     */
    @Parameter
    private String tourAnnotation;

    /**
     * Root link to CVS, used for links in generated documentation
     */
    @Parameter
    private String repositoryLink;

    /**
     * Strapdown theme name (united or cerulean)
     */
    @Parameter
    private String theme = "united";

    /**
     * Directory where to generated docs
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-docs")
    private File outputDirectory;

    private PrintWriter writer;

    private final Map<String, Tour> tours = new HashMap<String, Tour>();

    private static class Tour {
        private final SortedMap<Integer, String> sites = new TreeMap<Integer, String>();

        public String put(int step, String describtion) {
            return sites.put(step, describtion);
        }

        @Override
        public String toString() {
            return sites.toString();
        }

    }

    private static class TourStep {
        private final String name;
        private final String description;
        private final int step;

        public String name() {
            return name;
        }

        public String description() {
            return description;
        }

        public int step() {
            return step;
        }

        public TourStep(String name, String description, int step) {
            this.name = name;
            this.description = description;
            this.step = step;
        }

    }

    public void execute() {
        try {
            JavaProjectBuilder builder = new JavaProjectBuilder();
            // Adding all .java files in a source tree (recursively).
//            builder.addSourceTree(new File("src/main/java"));
            sources.stream().forEach(s -> builder.addSourceTree(new File(s)) );
            printAll(builder);

            final String template = SimpleTemplate.readResource("/strapdown-template.html");
            for (String tourName : tours.keySet()) {
                writeSightSeeingTour(tourName, template);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void writeSightSeeingTour(String tourName, final String template) throws UnsupportedEncodingException,
            FileNotFoundException {
        final StringWriter out = new StringWriter();
        writer = new PrintWriter(out);
        final Tour tour = tours.get(tourName);
        int count = 1;
        for (String step : tour.sites.values()) {
            writer.println(SEP);
            writer.println("## " + count++ + ". " + step);
        }
        String title = tourName;
        String content = out.toString();
        final String text = MessageFormat.format(template, new Object[] { title, theme, content });
        SimpleTemplate.write(outputDirectory.getPath(), tourName.replaceAll(" ", "_") + ".html", text);
        writer.close();

    }

    private void printAll(JavaProjectBuilder builder) {
        for (JavaPackage p : builder.getPackages()) {
            if (!p.getName().startsWith(prefix)) {
                continue;
            }
            final TourStep step = getQuickDevTourStep(p);
            if (step != null) {
                // process(p);
            }
        }
        for (JavaClass c : builder.getClasses()) {
            if (!c.getPackageName().startsWith(prefix)) {
                continue;
            }
            process(c);
        }

    }

    private TourStep getQuickDevTourStep(JavaAnnotatedElement doc) {
        for (JavaAnnotation annotation : doc.getAnnotations()) {
            if (annotation.getType().getFullyQualifiedName().equals(tourAnnotation)) {
                final String tourName = (String) annotation.getNamedParameter("name");
                final String step = (String) annotation.getNamedParameter("rank");
                final String desc = (String) annotation.getNamedParameter("description");
                return new TourStep(tourName.replaceAll("\"", ""), desc.replaceAll("\"", ""), Integer.valueOf(step));
            }
        }
        return null;
    }

    protected String title(final String title) {
        return "\n### " + title;
    }

    protected String listItem(final String bullet) {
        return "- " + bullet;
    }

    protected String link(final String name, String url) {
        return "[" + name + "](" + url + ")";
    }

    protected String linkSrcJava(final String name, String qName, int lineNumber) {
        return link(name, repositoryLink + "/src/main/java/" + qName.replace('.', '/') + ".java#L" + lineNumber);
    }

    protected void process(JavaClass c) {
        final String comment = blockQuote(c.getComment());
        addTourStep(getQuickDevTourStep(c), c.getName(), c.getFullyQualifiedName(), comment, c.getLineNumber());

        if (c.isEnum()) {
            for (JavaField field : c.getEnumConstants()) {
                // printEnumConstant(field);
            }
            for (JavaMethod method : c.getMethods(false)) {
                //
            }
        } else if (c.isInterface()) {
            for (JavaClass subClass : c.getDerivedClasses()) {
                // printSubClass(subClass);
            }
        } else {
            for (JavaField field : c.getFields()) {
                // printField(field);
            }
            for (JavaMethod m : c.getMethods(false)) {
                final String name = m.getCallSignature();
                final String qName = c.getFullyQualifiedName();
                final String codeBlock = code(m.getCodeBlock());
                final int lineNumber = m.getLineNumber();
                final TourStep step = getQuickDevTourStep(m);
                addTourStep(step, name, qName, codeBlock, lineNumber);

            }
        }
    }

    private String code(String codeBlock) {
        return "\n```\n" + codeBlock + "\n```";
    }

    private String blockQuote(String quote) {
        return quote == null ? "" : "> " + quote.replaceAll("\n", "\n> ");
    }

    private void addTourStep(final TourStep step, final String name, final String qName, final String comment,
                             final int lineNumber) {
        if (step != null) {
            final StringBuilder content = new StringBuilder();
            // content.append(name);
            content.append(linkSrcJava(name, qName, lineNumber));
            if (step.description() != null) {
                content.append(SEP);
                content.append("*" + step.description().trim() + "*");
            }
            if (comment != null) {
                content.append(SEP);
                content.append(comment);
            }
            content.append(SEP);

            getTourNamed(step.name()).put(step.step(), content.toString());
        }
    }

    private Tour getTourNamed(String name) {
        Tour tour = tours.get(name);
        if (tour == null) {
            tour = new Tour();
            tours.put(name, tour);
        }
        return tour;
    }
}
