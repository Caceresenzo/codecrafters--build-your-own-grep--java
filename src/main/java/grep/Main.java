package grep;

import java.io.IOException;
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

		final var patternString = commandLine.getArgList().get(0);

		try (final var scanner = new Scanner(System.in)) {
			String inputLine = scanner.nextLine();

			final var pattern = Pattern.compile(patternString);
			final var matcher = pattern.matcher(inputLine);

			if (matcher.find(0)) {
				System.exit(0);
			} else {
				System.exit(1);
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			System.exit(2);
		}
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