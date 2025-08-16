package grep;

import java.util.regex.Pattern;

public class JavaMain {

	public static void main(String[] args) {
		final var input = "x 1 C, 2 Ds and 3 Cs";

		final var pattern = Pattern.compile("^x (\\d (C|D|C)s?(, | and )?)+$");
		final var matcher = pattern.matcher(input);
		
		if (matcher.find(0)) {
			System.out.println("Match found");
		} else {
			System.out.println("No match found");
		}
	}

}