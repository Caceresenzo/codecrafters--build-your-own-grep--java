package grep;

import java.util.Scanner;

import lombok.Cleanup;

public class Main {

	public static void main(String[] args) {
		if (args.length != 2 || !args[0].equals("-E")) {
			System.out.println("Usage: ./your_program.sh -E <pattern>");
			System.exit(1);
		}

		String patternString = args[1];

		@Cleanup
		Scanner scanner = new Scanner(System.in);
		String inputLine = scanner.nextLine();

		final var pattern = Pattern.compile(patternString);
		final var matcher = pattern.matcher(inputLine);

		if (matcher.find(0)) {
			System.exit(0);
		} else {
			System.exit(1);
		}
	}

}