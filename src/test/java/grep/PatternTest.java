package grep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class PatternTest {

	private static final String TEST_NAME = "\"{0}\" matches /{1}/ is {2}";

	@Nested
	@Order(1)
	@DisplayName("Core Challenge")
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	class Base {

		@Order(10)
		@DisplayName("Match a literal character")
		@ParameterizedTest(name = TEST_NAME)
		@CsvSource({
			"d, dog, true",
			"f, dog, false",
		})
		void matchALiteralCharacter(String regex, String input, boolean expected) {
			testWithPatten(regex, input, expected);
		}

		@Order(20)
		@DisplayName("Match digits")
		@ParameterizedTest(name = TEST_NAME)
		@CsvSource({
			"\\d, 3, true",
			"\\d, foo101, true",
			"\\d, c, false",
			"\\d, Art, false",
		})
		void matchDigits(String regex, String input, boolean expected) {
			testWithPatten(regex, input, expected);
		}

		@Order(30)
		@DisplayName("Match word characters")
		@ParameterizedTest(name = TEST_NAME)
		@CsvSource({
			"\\w, alpha_num3ric, true",
			"\\w, foo101, true",
			"\\w, $!?, false",
		})
		void matchWordCharacters(String regex, String input, boolean expected) {
			testWithPatten(regex, input, expected);
		}

		@Order(40)
		@DisplayName("Positive Character Groups")
		@ParameterizedTest(name = TEST_NAME)
		@CsvSource({
			"[abc], apple, true",
			"[abc], cab, true",
			"[abc], dog, false",
			"[123], a1b2c3, true",
		})
		void positiveCharacterGroups(String regex, String input, boolean expected) {
			testWithPatten(regex, input, expected);
		}

		@Order(50)
		@DisplayName("Negative Character Groups")
		@ParameterizedTest(name = TEST_NAME)
		@CsvSource({
			"[^abc], cat, true",
			"[^abc], cab, false",
		})
		void negativeCharacterGroups(String regex, String input, boolean expected) {
			testWithPatten(regex, input, expected);
		}

		@Order(60)
		@DisplayName("Combining Character Classes")
		@ParameterizedTest(name = TEST_NAME)
		@CsvSource({
			"\\d apple, 1 apple, true",
			"\\d apple, 1 orange, false",
			"\\d\\d\\d apples, I got 100 apples from the store, true",
			"\\d\\d\\d apples, I got 1 apple from the store, false",
			"\\d \\w\\w\\ws, 4 cats, true",
			"\\d \\w\\w\\ws, 1 dog, false",
		})
		void combiningCharacterClasses(String regex, String input, boolean expected) {
			testWithPatten(regex, input, expected);
		}

		@Order(70)
		@DisplayName("Start of string anchor")
		@ParameterizedTest(name = TEST_NAME)
		@CsvSource({
			"^log, log, true",
			"^log, logs, true",
			"^log, slog, false",
			"^\\d\\d\\d, 123abc, true",
		})
		void startOfStringAnchor(String regex, String input, boolean expected) {
			testWithPatten(regex, input, expected);
		}

		@Order(80)
		@DisplayName("End of string anchor")
		@ParameterizedTest(name = TEST_NAME)
		@CsvSource({
			"dog$, dog, true",
			"dog$, hotdog, true",
			"dog$, dogs, false",
			"\\d\\d\\d$, abc123, true",
			"\\w\\w\\w$, abc123@, false",
			"\\w\\w\\w$, abc123cde, true",
		})
		void endOfStringAnchor(String regex, String input, boolean expected) {
			testWithPatten(regex, input, expected);
		}

		@Order(90)
		@DisplayName("Match one or more times")
		@ParameterizedTest(name = TEST_NAME)
		@CsvSource({
			"a+, apple, true",
			"a+, SaaS, true",
			"a+, dog, false",
			"ca+ts, cats, true",
			"ca+ts, caats, true",
			"ca+ts, cts, false",
			"\\d+, 123, true",
		})
		void matchOneOrMoreTimes(String regex, String input, boolean expected) {
			testWithPatten(regex, input, expected);
		}

		@Order(100)
		@DisplayName("Match zero or one times")
		@ParameterizedTest(name = TEST_NAME)
		@CsvSource({
			"dogs?, dog, true",
			"dogs?, dogs, true",
			// "dogs?, dogss, false",
			"dogs?, cat, false",
			"colou?r, color, true",
			"colou?r, colour, true",
			"\\d?, 5, true",
			"\\d?, '', true",
		})
		void matchZeroOrOneTimes(String regex, String input, boolean expected) {
			testWithPatten(regex, input, expected);
		}

		@Order(110)
		@DisplayName("Wildcard")
		@ParameterizedTest(name = TEST_NAME)
		@CsvSource({
			"d.g, dog, true",
			"d.g, dag, true",
			"d.g, d9g, true",
			"d.g, cog, false",
			"d.g, dg, false",
			"..., cat, true",
			".\\d., a1b, true",
		})
		void wildcard(String regex, String input, boolean expected) {
			testWithPatten(regex, input, expected);
		}

		@Order(115)
		@Test
		void capture() {
			final var pattern = Pattern.compile("a(b.)de");
			final var matcher = pattern.matcher("abcde");

			assertTrue(matcher.find(0));
			assertEquals("abcde", matcher.group(0));
			assertEquals("bc", matcher.group(1));
		}

		@Order(116)
		@Test
		void captureWithBacktrack() {
			final var pattern = Pattern.compile("_([^a]+),");

			assertTrue(pattern.matcher("_bbb, c").find(0));
		}

		@Order(120)
		@DisplayName("Alternation")
		@ParameterizedTest(name = TEST_NAME)
		@CsvSource({
			"(cat|dog), cat, true",
			"(cat|dog), dog, true",
			"(cat|dog), apple, false",
			"(cat|dog), doghouse, true",
			"I like (cats|dogs), I like cats, true",
			"I like (cats|dogs), I like dogs, true",
			"(red|blue|green), blue, true",
		})
		void alternation(String regex, String input, boolean expected) {
			testWithPatten(regex, input, expected);
		}

		@Order(121)
		@Test
		void alternationInner() {
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

		@Order(122)
		@Test
		void alternationInnerWithQuantifier() {
			final var pattern = Pattern.compile("^I see (\\d (cat|dog|cow)s?(, | and )?)+$");

			final var matcher = pattern.matcher("I see 1 cat, 2 dogs and 3 cows");
			assertTrue(matcher.find(0));
			assertEquals("I see 1 cat, 2 dogs and 3 cows", matcher.group(0));
			assertEquals("3 cows", matcher.group(1));
			assertEquals("cow", matcher.group(2));
			assertEquals(" and ", matcher.group(3));
		}

		@Order(999)
		@Test
		void anyPlusAtStart() {
			final var pattern = Pattern.compile(".+ar");
			final var matcher = pattern.matcher("carx");

			assertTrue(matcher.find(0));
		}

	}

	@Nested
	@Order(2)
	@DisplayName("Backreferences Extension")
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	class Backreferences {

		@Order(10)
		@DisplayName("Single Backreference")
		@ParameterizedTest(name = TEST_NAME)
		@CsvSource({
			"(cat) and \\1, cat and cat, true",
			"(cat) and \\1, cat and dog, false",
			"(\\w+) and \\1, cat and cat, true",
			"(\\w+) and \\1, dog and dog, true",
			"(\\w+) and \\1, cat and dog, false",
			"(\\d+)-\\1, 123-123, true",
		})
		void singleBackreference(String regex, String input, boolean expected) {
			testWithPatten(regex, input, expected);
		}

		@Order(20)
		@DisplayName("Multiple Backreferences")
		@ParameterizedTest(name = TEST_NAME)
		@CsvSource({
			"(\\d+) (\\w+) and \\1 \\2, 3 red and 3 red, true",
			"(\\d+) (\\w+) and \\1 \\2, 3 red and 4 red, false",
			"(\\d+) (\\w+) and \\1 \\2, 3 red and 3 blue, false",
			"(cat) and (dog) are \\2 and \\1, cat and dog are dog and cat, true",
			"(\\w+)-(\\w+)-\\1-\\2, foo-bar-foo-bar, true",
		})
		void multipleBackreferences(String regex, String input, boolean expected) {
			testWithPatten(regex, input, expected);
		}

		@Order(30)
		@DisplayName("Nested Backreferences")
		@ParameterizedTest(name = TEST_NAME)
		@CsvSource({
			"((dog)-\\2), dog-dog, true",
			"((\\w+) \\2) and \\1, cat cat and cat cat, true",
		})
		void nestedBackreferences(String regex, String input, boolean expected) {
			testWithPatten(regex, input, expected);
		}

	}

	@Nested
	@Order(3)
	@DisplayName("Quantifiers Extension")
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	class Quantifiers {

		@Order(10)
		@DisplayName("Match zero or more times")
		@ParameterizedTest(name = TEST_NAME)
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

		@Order(20)
		@DisplayName("Match exactly n times")
		@ParameterizedTest(name = TEST_NAME)
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

		@Order(30)
		@DisplayName("Match at least n times")
		@ParameterizedTest(name = TEST_NAME)
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

		@Order(40)
		@DisplayName("Match between n and m times")
		@ParameterizedTest(name = TEST_NAME)
		@CsvSource({
			"'ca{2,4}t', caat, true",
			"'ca{2,4}t', caaat, true",
			"'ca{2,4}t', caaaat, true",
			"'ca{2,4}t', caaaaat, false",
			"'n\\d{1,3}m', n123m, true",
			"'n\\d{1,3}m', n1234m, false",
			"'p[xyz]{2,3}q', pzzzq, true",
			"'p[xyz]{2,3}q', pxq, false",
			"'p[xyz]{2,3}q', pxyzyq, false",
		})
		void matchBetweenNAndMTimes(String regex, String input, boolean expected) {
			testWithPatten(regex, input, expected);
		}

	}

	private void testWithPatten(String regex, String input, boolean expected) {
		final var pattern = Pattern.compile(regex);
		final var matcher = pattern.matcher(input);

		assertEquals(expected, matcher.find(0));
	}

}