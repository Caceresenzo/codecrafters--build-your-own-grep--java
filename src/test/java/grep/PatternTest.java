package grep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
	void positiveCharacterGroupWithBackslash() {
		final var pattern = Pattern.compile("[abc\\\\]");

		assertTrue(pattern.matcher("\\").find(0));
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

	@Test
	void captureWithBacktrack() {
		final var pattern = Pattern.compile("_([^a]+),");

		assertTrue(pattern.matcher("_bbb, c").find(0));
	}

	@Test
	void branch() {
		final var pattern = Pattern.compile("a(a|b)");

		assertTrue(pattern.matcher("aa").find(0));
		assertTrue(pattern.matcher("ab").find(0));
		assertFalse(pattern.matcher("ac").find(0));
	}

	@Test
	void branchInner() {
		final var pattern = Pattern.compile("^I see (\\d (cat|dog|cow)s?)$");

		final var matcher = pattern.matcher("I see 1 cat");
		assertTrue(matcher.find(0));
		assertEquals("I see 1 cat", matcher.group(0));
		assertEquals("1 cat", matcher.group(1));
		assertEquals("cat", matcher.group(2));

		assertTrue(pattern.matcher("I see 3 cats").find(0));
		assertTrue(pattern.matcher("I see 1 dog").find(0));
		assertTrue(pattern.matcher("I see 3 dogs").find(0));
		assertTrue(pattern.matcher("I see 1 cow").find(0));
		assertTrue(pattern.matcher("I see 3 cows").find(0));
		assertFalse(pattern.matcher("I see 1 rabbit").find(0));
	}

	@Test
	void branchInnerWithQuantifier() {
		final var pattern = Pattern.compile("^I see (\\d (cat|dog|cow)s?(, | and )?)+$");

		final var matcher = pattern.matcher("I see 1 cat, 2 dogs and 3 cows");
		assertTrue(matcher.find(0));
		assertEquals("I see 1 cat, 2 dogs and 3 cows", matcher.group(0));
		assertEquals("3 cows", matcher.group(1));
		assertEquals("cow", matcher.group(2));
		assertEquals(" and ", matcher.group(3));
	}

	@Test
	void backReferenceStatic() {
		final var pattern = Pattern.compile("(cat) and \\1");

		assertTrue(pattern.matcher("cat and cat").find(0));
		assertFalse(pattern.matcher("cat and dog").find(0));
	}

	@Test
	void backReferencePattern() {
		final var pattern = Pattern.compile("([abcd]+) is \\1");

		assertTrue(pattern.matcher("abcd is abcd").find(0));
	}

	@Test
	void multipleBackReference() {
		final var pattern = Pattern.compile("(\\d+) (\\w+) squares and \\1 \\2 circles");

		assertTrue(pattern.matcher("3 red squares and 3 red circles").find(0));
		assertFalse(pattern.matcher("3 red squares and 4 red circles").find(0));
	}

	@Test
	void nestedBackReference() {
		final var pattern = Pattern.compile("('(cat) and \\2') is the same as \\1");

		assertTrue(pattern.matcher("'cat and cat' is the same as 'cat and cat'").find(0));
	}

	@Test
	void nestedBackReference2() {
		final var pattern = Pattern.compile("((c.t|d.g) and (f..h|b..d)), \\2 with \\3, \\1");
		final var matcher = pattern.matcher("cat and fish, cat with fish, cat and fish");

		assertTrue(matcher.find(0));
		assertEquals("cat and fish, cat with fish, cat and fish", matcher.group(0));
		assertEquals("cat and fish", matcher.group(1));
		assertEquals("cat", matcher.group(2));
		assertEquals("fish", matcher.group(3));
	}

	@Test
	void anyPlusAtStart() {
		final var pattern = Pattern.compile(".+ar");
		final var matcher = pattern.matcher("carx");

		assertTrue(matcher.find(0));
	}

	@ParameterizedTest
	@CsvSource({
		"ca*t, ct, true",
		"ca*t, caaat, true",
		"ca*t, dog, false",
		"k\\d*t, kt, true",
		"k\\d*t, k1t, true",
		"k\\d*t, kabct, false",
		"k[abc]*t, kt, true",
		"k[abc]*t, kat, true",
		"k[abc]*t, kabct, true",
		"k[abc]*t, kaxyzt, false",
	})
	void matchZeroOrMoreTimes(String regex, String input, boolean expected) {
		testWithPatten(regex, input, expected);
	}

	@ParameterizedTest
	@CsvSource({
		"ca{3}t, caaat, true",
		"ca{3}t, caat, false",
		"ca{3}t, caaaat, false",
		"d\\d{2}g, d42g, true",
		"d\\d{2}g, d1g, false",
		"d\\d{2}g, d123g, false",
		"c[xyz]{4}w, czyxzw, true",
		"c[xyz]{4}w, cxyzw, false",
	})
	void matchExactlyNTimes(String regex, String input, boolean expected) {
		testWithPatten(regex, input, expected);
	}

	@ParameterizedTest
	@CsvSource({
		"'ca{2,}t', caat, true",
		"'ca{2,}t', caaaaat, true",
		"'ca{2,}t', cat, false",
		"'x\\d{3,}y', x9999y, true",
		"'x\\d{3,}y', x42y, false",
		"'b[aeiou]{2,}r', baeiour, true",
		"'b[aeiou]{2,}r', bar, false",
	})
	void matchAtLeastNTimes(String regex, String input, boolean expected) {
		testWithPatten(regex, input, expected);
	}

	private void testWithPatten(String regex, String input, boolean expected) {
		final var pattern = Pattern.compile(regex);
		final var matcher = pattern.matcher(input);

		assertEquals(expected, matcher.find(0));
	}

}