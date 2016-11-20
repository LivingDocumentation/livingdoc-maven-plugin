package io.github.livingdocumentation;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

@Mojo(name = "wordcloud")
public class WordCloudMojo extends AbstractMojo {

	private static final String OUTPUT_FILENAME = "wordcloud";

	@Parameter(defaultValue = "${project.build.sourceDirectory}")
	private List<String> sources;

	@Parameter(defaultValue = "${project.build.directory}/generated-docs")
	private File outputDirectory;


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
		sourceFolders.forEach(folder -> { try {
			scan(new File(folder));
		}
		catch(IOException e) {
			throw new UncheckedIOException(e);
		}});
	}

	public Multiset<String> getBag() {
		return bag;
	}

	public int getMax() {
		return max;
	}

	private void scan(final File f) throws IOException {
		final File[] listOfFiles = f.listFiles();
		for (File file : listOfFiles) {
			if (file.isDirectory() && !file.getName().endsWith("annotations")) {
				scan(file);
			}
			if (file.isFile() && file.getName().endsWith(".java")) {
				final String content = new String(Files.readAllBytes(file.toPath()));
				filter(content);
			}
		}
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
	// (final String sourceFolder, String outputFileName) throws IOException,
//			UnsupportedEncodingException, FileNotFoundException {
		final WordCloudMojo wordCloudMojo = new WordCloudMojo();

		try {
			wordCloudMojo.scan(sources);

			final Multiset<String> bag = wordCloudMojo.getBag();
			final int max = wordCloudMojo.getMax();
			final double scaling = 50. / max;

			final String template = SimpleTemplate.readResource("/wordcloud-template.html");

			String title = "Word Cloud";
			String content = toJSON(bag, scaling);

			final String text = SimpleTemplate.evaluate(template, title, content);
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

}
