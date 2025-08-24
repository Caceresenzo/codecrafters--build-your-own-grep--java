package grep;

import java.io.FileInputStream;
import java.util.Scanner;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.help.HelpFormatter;

import lombok.SneakyThrows;

public class Main {

	public static void main(String[] args) {
		final var helpOption = new Option(null, "help", false, "display this help text and exit");

		final var extendedRegexpOption = new Option("E", "extended-regexp", false, "PATTERNS are extended regular expressions");
		extendedRegexpOption.setRequired(true);

		final var options = new Options();
		options.addOption(helpOption);
		options.addOption(extendedRegexpOption);

		final CommandLine commandLine;

		try {
			commandLine = new DefaultParser().parse(options, args);
		} catch (ParseException exception) {
			System.err.println(exception.getMessage());
			throw printUsage(options);
		}

		if (commandLine.hasOption(helpOption)) {
			throw printUsage(options);
		}

		final var argList = commandLine.getArgList();
		if (argList.isEmpty()) {
			System.err.println("A PATTERN is required.");
			throw printUsage(options);
		}

		if (!commandLine.hasOption("E")) {
			System.err.println("The -E option is required.");
			throw printUsage(options);
		}

		final var patternString = argList.get(0);
		final var filePaths = argList.subList(1, argList.size());

		var found = false;

		try {
			final var pattern = Pattern.compile(patternString);

			if (filePaths.isEmpty()) {
				found = findFromStdin(pattern);
			} else {
				for (final var filePath : filePaths) {
					found |= findFromFile(pattern, filePath);
				}
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			System.exit(2);
		}

		if (found) {
			System.exit(0);
		} else {
			System.exit(1);
		}
	}

	@SneakyThrows
	static boolean findFromStdin(Pattern pattern) {
		try (
			final var scanner = new Scanner(System.in)
		) {
			final var inputLine = scanner.nextLine();

			return handleMatcher(pattern, inputLine);
		}
	}

	@SneakyThrows
	static boolean findFromFile(Pattern pattern, String filePath) {
		var found = false;

		try (
			final var fileInputStream = new FileInputStream(filePath);
			final var scanner = new Scanner(fileInputStream)
		) {
			while (scanner.hasNextLine()) {
				final var inputLine = scanner.nextLine();

				found |= handleMatcher(pattern, inputLine);
			}
		}

		return found;
	}

	static boolean handleMatcher(Pattern pattern, String inputLine) {
		final var matcher = pattern.matcher(inputLine);

		if (matcher.find(0)) {
			System.out.println(matcher.group());
			return true;
		}

		return false;
	}

	@SneakyThrows
	static RuntimeException printUsage(Options options) {
		final var helpFormatter = HelpFormatter.builder()
			.setShowSince(false)
			.get();

		helpFormatter.printHelp("grep [OPTION]... PATTERNS [FILE]...", "Search for PATTERNS in each FILE.", options, null, false);
		System.exit(1);

		return null;
	}

}