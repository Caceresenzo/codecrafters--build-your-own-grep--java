package grep;

import java.util.regex.Pattern;

public class JavaMain {

	public static void main(String[] args) {
		final var input = "cat and cat";

		final var pattern = Pattern.compile("(cat) and \\1");
		final var matcher = pattern.matcher(input);
		
		if (matcher.find(0)) {
			System.out.println("Match found");
		} else {
			System.out.println("No match found");
		}
	}

}