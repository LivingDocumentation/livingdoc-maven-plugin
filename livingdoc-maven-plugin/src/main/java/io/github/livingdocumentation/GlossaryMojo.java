package io.github.livingdocumentation;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaClass;
import io.github.robwin.markup.builder.MarkupDocBuilder;
import io.github.robwin.markup.builder.asciidoc.AsciiDocBuilder;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.OptionsBuilder;
import org.asciidoctor.SafeMode;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

@Mojo(name = "glossary")
public class GlossaryMojo extends AbstractMojo {

    private static final String OUTPUT_FILENAME = "glossary";

    @Parameter(defaultValue = "${project.build.sourceDirectory}")
    private List<String> sources;

    @Parameter(defaultValue = "${project.build.directory}/generated-docs")
    private File outputDirectory;

    @Parameter(defaultValue = "html")
    private String format;

    @Parameter(defaultValue = "Glossary")
    private String annotation;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        JavaProjectBuilder javaDocBuilder = buildJavaProjectBuilder();

        AsciiDocBuilder asciiDocBuilder = new AsciiDocBuilder();
        asciiDocBuilder.documentTitle("Glossary");

        javaDocBuilder.getClasses()
                .stream()
                .filter(this::hasAnnotation)
                .forEach(c -> {
                    asciiDocBuilder.sectionTitleLevel1(c.getName());
                    writeBlankLine(asciiDocBuilder);
                    asciiDocBuilder.textLine(c.getComment());
                    writeBlankLine(asciiDocBuilder);
                });

        outputDirectory.mkdirs();
        try {
            asciiDocBuilder.writeToFile(outputDirectory.getAbsolutePath(), OUTPUT_FILENAME, StandardCharsets.UTF_8);
            if ("html".equals(format)) {
                Asciidoctor asciidoctor = Asciidoctor.Factory.create();
                File asciidocFile = new File(outputDirectory, OUTPUT_FILENAME  + ".adoc");
                asciidoctor.convertFile(asciidocFile, OptionsBuilder.options().backend("html5").safe(SafeMode.UNSAFE).asMap());
                Files.deleteIfExists(asciidocFile.toPath());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static MarkupDocBuilder writeBlankLine(AsciiDocBuilder asciiDocBuilder) {
        return asciiDocBuilder.textLine("");
    }

    private boolean hasAnnotation(JavaClass javaClass) {
        return javaClass.getAnnotations().stream()
                .anyMatch(a -> a.getType().getFullyQualifiedName().endsWith(annotation));
    }

    private JavaProjectBuilder buildJavaProjectBuilder() {
        JavaProjectBuilder javaDocBuilder = new JavaProjectBuilder();
        javaDocBuilder.setErrorHandler(e -> getLog().warn(e.getMessage()));
        sources.stream().map(File::new).forEach(javaDocBuilder::addSourceTree);
        return javaDocBuilder;
    }
}
