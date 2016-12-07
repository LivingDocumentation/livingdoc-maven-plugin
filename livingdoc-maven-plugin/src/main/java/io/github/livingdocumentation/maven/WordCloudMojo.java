package io.github.livingdocumentation.maven;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.utils.io.DirectoryScanner;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

@Mojo(name = "wordcloud")
public class WordCloudMojo extends AbstractMojo {

	private static final String OUTPUT_FILENAME = "wordcloud";
	private static final String[] DEFAULT_EXCLUDES = new String[] { "**/package-info.java" };
	private static final String[] DEFAULT_INCLUDES = new String[] { "**/*.java" };

	@Parameter(defaultValue = "${project.build.sourceDirectory}")
	private List<String> sources;

	@Parameter(defaultValue = "${project.build.directory}/generated-docs")
	private File outputDirectory;

	/**
	 * List of files to include. Specified as fileset patterns which are relative to the input directory whose contents
	 * is being parsed to include in the cloud of words.
	 */
	@Parameter
	private String[] includes = DEFAULT_INCLUDES;

	/**
	 * List of files to exclude. Specified as fileset patterns which are relative to the input directory whose contents
	 * is being parsed to include in the cloud of words.
	 */
	@Parameter
	private String[] excludes = DEFAULT_EXCLUDES;

	// keywords to be ignored
	private static final String[] KEYWORDS = { "abstract", "continue", "for", "new", "switch", "assert", "default",
			"if", "package", "synchronized", "boolean", "do", "goto", "private", "this", "break", "double",
			"implements", "protected", "throw", "byte", "else", "import", "public", "throws", "case", "enum",
			"instanceof", "return", "transient", "catch", "extends", "int", "", "short", "try", "char", "final",
			"interface", "static", "void", "class", "finally", "long", "strictfp", "volatile", "const", "float",
			"native", "super", "while" };
	
	// lower-case words to be ignored
	private static final String[] STOPWORDS = { "id", "the", "it","is", "to", "with", "what's", "by", "or", "and", "both", "be", "of",
			"in", "obj", "string", "hashcode", "equals", "other", "tostring", "false", "true", "object", "annotations" };
	private static final Set<String> ignore = initialize();

	private final static Set<String> initialize() {
		final Set<String> ignore = new HashSet<String>();
		ignore.addAll(Arrays.asList(STOPWORDS));
		ignore.addAll(Arrays.asList(KEYWORDS));
		return ignore;
	}

	private final Multiset<String> bag = HashMultiset.create();
	private int max = 0;

	public void scan(final List<String> sourceFolders) throws IOException {
		sourceFolders.forEach(folder -> {
			DirectoryScanner ds = new DirectoryScanner();
			ds.setBasedir(folder);
			ds.setIncludes(includes);
			ds.setExcludes(excludes);
			ds.scan();

			Arrays.stream(ds.getIncludedFiles())
					.forEach( f -> {
						getLog().debug(String.format("file: %s", f));
						File file = new File(ds.getBasedir(), f);

						final String content;
						try {
							content = new String(Files.readAllBytes(file.toPath()));
							filter(content);
						} catch (IOException e) {
							throw new UncheckedIOException(e);
						}
					}
			);

		});
	}

	public Multiset<String> getBag() {
		return bag;
	}

	public int getMax() {
		return max;
	}

	private void filter(final String content) {
		final StringTokenizer st = new StringTokenizer(content, ";:.,?!<><=+-^&|*/\"\t\r\n {}[]()");
		while (st.hasMoreElements()) {
			final String token = (String) st.nextElement();
			if (isMeaningful(token.trim().toLowerCase())) {
				bag.add(token.trim().toLowerCase());
				final int count = bag.count(token);
				max = Math.max(max, count);
			}
		}
	}

	private static boolean isMeaningful(String token) {
		if (token.length() <= 1) {
			return false;
		}
		if (token.startsWith("@")) {
			return false;
		}
		if (Character.isDigit(token.charAt(0))) {
			return false;
		}
		if (ignore.contains(token)) {
			return false;
		}
		return true;
	}

	public void execute() {

		try {
			scan(sources);

			final Multiset<String> bag = getBag();
			final int max = getMax();
			final double scaling = 50. / max;

			final String template = SimpleTemplate.readResource("/wordcloud-template.html");

			String title = "Word Cloud";
			String content = toJSON(bag, scaling);

			final String text = SimpleTemplate.evaluate(template, title, content);
			outputDirectory.mkdirs();
			SimpleTemplate.write("", outputDirectory + "/" + OUTPUT_FILENAME + ".html" , text);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static String toJSON(final Multiset<String> bag, double scaling) {
		final StringBuilder sb = new StringBuilder();
		for (Multiset.Entry<String> entry : bag.entrySet()) {
			sb.append("{\"text\": \"" + entry.getElement() + "\", \"size\": " + scaling * entry.getCount() + "}");
			sb.append(", ");
		}
		return sb.toString();
	}

	private String[] getIncludes() {
		if (includes != null && includes.length > 0) {
			return includes;
		}
		return DEFAULT_INCLUDES;
	}

}
