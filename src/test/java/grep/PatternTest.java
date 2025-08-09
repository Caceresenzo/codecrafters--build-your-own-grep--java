package grep;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PatternTest {

	@Test
	void literal() {
		final var pattern = Pattern.compile("a");

		assertTrue(pattern.matcher("a").find(0));
		assertFalse(pattern.matcher("b").find(0));
	}

	@Test
	void rangeDigits() {
		final var pattern = Pattern.compile("\\d");

		assertTrue(pattern.matcher("1").find(0));
		assertFalse(pattern.matcher("a").find(0));
	}

}