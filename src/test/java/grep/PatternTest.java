package grep;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

	@Test
	void rangeWords() {
		final var pattern = Pattern.compile("\\w");

		assertTrue(pattern.matcher("a").find(0));
		assertFalse(pattern.matcher("?").find(0));
	}

	@Test
	void positiveCharacterGroup() {
		final var pattern = Pattern.compile("[abc]");

		assertTrue(pattern.matcher("a").find(0));
		assertFalse(pattern.matcher("d").find(0));
	}

	@Test
	void positiveCharacterGroupWithDigits() {
		final var pattern = Pattern.compile("[abc\\d]");

		assertTrue(pattern.matcher("a").find(0));
		assertTrue(pattern.matcher("1").find(0));
		assertFalse(pattern.matcher("d").find(0));
	}

	@Test
	void negativeCharacterGroup() {
		final var pattern = Pattern.compile("[^abc]");

		assertFalse(pattern.matcher("a").find(0));
		assertTrue(pattern.matcher("d").find(0));
	}

	@Test
	void negativeCharacterGroupWithDigits() {
		final var pattern = Pattern.compile("[^abc\\d]");

		assertFalse(pattern.matcher("a").find(0));
		assertFalse(pattern.matcher("1").find(0));
		assertTrue(pattern.matcher("d").find(0));
	}

	@Test
	void escape() {
		final var pattern = Pattern.compile("\\d\\\\d\\\\d apples");

		assertFalse(pattern.matcher("sally has 12 apples").find(0));
	}

	@Test
	void startAnchor() {
		final var pattern = Pattern.compile("^log");

		assertTrue(pattern.matcher("log").find(0));
		assertFalse(pattern.matcher("slog").find(0));
	}

	@Test
	void endAnchor() {
		final var pattern = Pattern.compile("dog$");

		assertTrue(pattern.matcher("dog").find(0));
		assertFalse(pattern.matcher("dogs").find(0));
	}

	@Test
	void matchOneOrMoreTimes() {
		final var pattern = Pattern.compile("ca+ts");

		assertFalse(pattern.matcher("ca").find(0));
		assertTrue(pattern.matcher("cats").find(0));
		assertTrue(pattern.matcher("caats").find(0));
		assertTrue(pattern.matcher("caaats").find(0));
		assertFalse(pattern.matcher("dog").find(0));
	}

	@Test
	void matchOneOrMoreTimesWithAfter() {
		final var pattern = Pattern.compile("ca+at");

		assertTrue(pattern.matcher("caaats").find(0));
	}

	@Test
	void matchZeroOrOneTimes() {
		final var pattern = Pattern.compile("dogs?");

		assertTrue(pattern.matcher("dog").find(0));
		assertTrue(pattern.matcher("dogs").find(0));
	}

	@Test
	void wildcard() {
		final var pattern = Pattern.compile("d.g");

		assertTrue(pattern.matcher("dog").find(0));
	}

	@Test
	void capture() {
		final var pattern = Pattern.compile("a(b.)de");
		final var matcher = pattern.matcher("abcde");

		assertTrue(matcher.find(0));
		assertEquals("abcde", matcher.group(0));
		assertEquals("bc", matcher.group(1));
	}

}