package grep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

public class Pattern {

	final Node root;

	private Pattern(Node root) {
		this.root = root;
	}

	public Matcher matcher(CharSequence sequence) {
		return new Matcher(this, sequence);
	}

	public static Pattern compile(String expression) {
		Node root = new Start();
		var current = root;
		Node previous = null;

		int index = 0;

		char currentChar;
		while (index < expression.length()) {
			currentChar = expression.charAt(index++);

			switch (currentChar) {
				case '\\': {
					currentChar = expression.charAt(index++);

					CharPredicate predicate;
					if (currentChar == '\\') {
						predicate = new CharPredicate.Character(currentChar);
					} else {
						predicate = CharacterRangeClass.fromIdentifier(currentChar);
					}

					previous = current;
					previous.next = current = new CharProperty(predicate);
					break;
				}

				case '[': {
					final var array = new AsciiArrayClass();
					final var ranges = new ArrayList<CharacterRangeClass>();

					final var negate = index < expression.length() && expression.charAt(index) == '^';
					if (negate) {
						index++;
					}

					while (index < expression.length()) {
						currentChar = expression.charAt(index++);

						if (currentChar == ']') {
							break;
						}

						if (currentChar == '\\') {
							currentChar = expression.charAt(index++);

							if (currentChar == '\\') {
								array.add(currentChar);
							} else {
								final var characterClass = CharacterRangeClass.fromIdentifier(currentChar);
								ranges.add(characterClass);
							}
						} else {
							array.add(currentChar);
						}
					}

					CharPredicate predicate;
					if (ranges.isEmpty()) {
						predicate = array;
					} else {
						predicate = new CharPredicate.Or(array, ranges.toArray(CharPredicate[]::new));
					}

					if (negate) {
						predicate = new CharPredicate.Not(predicate);
					}

					previous = current;
					previous.next = current = new CharProperty(predicate);
					break;
				}

				case '^': {
					previous = current;
					previous.next = current = new Begin();
					break;
				}

				case '$': {
					previous = current;
					previous.next = current = new End();
					break;
				}

				case '+': {
					current.next = new Last();

					final var repeat = new Repeat(current, 1, Repeat.UNBOUNDED);
					previous.next = current = repeat;
					break;
				}

				case '?': {
					current.next = new Last();

					final var repeat = new Repeat(current, 0, 1);
					previous.next = current = repeat;
					break;
				}

				case '.': {
					previous = current;
					previous.next = current = new CharProperty(new CharPredicate.Any());
					break;
				}

				default: {
					previous = current;
					previous.next = current = new CharProperty(new CharPredicate.Character(currentChar));
					break;
				}
			}
		}

		current.next = new Last();
		return new Pattern(root);
	}

	static class Node {

		Node next;

		public boolean match(Matcher matcher, int index, CharSequence sequence) {
			return true;
		}

	}

	@RequiredArgsConstructor
	static class Start extends Node {

		@Override
		public boolean match(Matcher matcher, int index, CharSequence sequence) {
			final var to = matcher.to;
			for (; index <= to; ++index) {
				if (next.match(matcher, index, sequence)) {
					matcher.first = index;
					return true;
				}
			}

			matcher.hitEnd = true;
			return false;
		}

		@Override
		public String toString() {
			return "-START-";
		}

	}

	@RequiredArgsConstructor
	static class CharProperty extends Node {

		final CharPredicate predicate;

		@Override
		public boolean match(Matcher matcher, int index, CharSequence sequence) {
			if (index < matcher.to) {
				char value = sequence.charAt(index);

				// TODO currently only support ascii
				return predicate.test(value) && next.match(matcher, index + 1, sequence);
			}

			matcher.hitEnd = true;
			return false;
		}

		@Override
		public String toString() {
			return predicate.toString();
		}

	}

	static class Begin extends Node {

		@Override
		public boolean match(Matcher matcher, int index, CharSequence sequence) {
			final var startIndex = matcher.from;

			if (index == startIndex && next.match(matcher, index, sequence)) {
				matcher.first = index;
				return true;
			}

			return false;
		}

		@Override
		public String toString() {
			return "^";
		}

	}

	static class End extends Node {

		@Override
		public boolean match(Matcher matcher, int index, CharSequence sequence) {
			final var endIndex = matcher.to;

			if (index == endIndex && next.match(matcher, index, sequence)) {
				matcher.hitEnd = true;
				return true;
			}

			return false;
		}

		@Override
		public String toString() {
			return "$";
		}

	}

	@RequiredArgsConstructor
	static class Repeat extends Node {

		static final int UNBOUNDED = -1;

		final Node atom;
		final int min;
		final int max;

		@Override
		public boolean match(Matcher matcher, int index, CharSequence sequence) {
			int count = 0;
			for (; count < min; ++count) {
				if (!atom.match(matcher, index, sequence)) {
					return false;
				}

				index = matcher.last;
			}

			final var max = this.max == UNBOUNDED ? Integer.MAX_VALUE : this.max;

			while (index < matcher.to && count++ < max) {
				if (next.match(matcher, index, sequence)) {
					return true;
				}

				if (!atom.match(matcher, index, sequence)) {
					break;
				}

				index = matcher.last;
			}

			return next.match(matcher, index, sequence);
		}

		@Override
		public String toString() {
			if (min == 0 && max == UNBOUNDED) {
				return atom + "*";
			} else if (min == 1 && max == UNBOUNDED) {
				return atom + "+";
			} else if (max == UNBOUNDED) {
				return atom + "{" + min + ",}";
			} else if (min == UNBOUNDED) {
				return atom + "{," + max + "}";
			} else if (min == max) {
				return atom + "{" + min + "}";
			}

			return atom + "{" + min + "," + max + "}";
		}

	}

	static class Last extends Node {

		@Override
		public boolean match(Matcher matcher, int index, CharSequence sequence) {
			matcher.last = index;
			return true;
		}

		@Override
		public String toString() {
			return "-LAST-";
		}

	}

	@FunctionalInterface
	static interface CharPredicate {

		boolean test(char character);

		@RequiredArgsConstructor
		static class Character implements CharPredicate {

			private final char value;

			@Override
			public boolean test(char character) {
				return value == character;
			}

			@Override
			public String toString() {
				if (value == '\\') {
					return "\\\\";
				}

				return String.valueOf(value);
			}

		}

		@RequiredArgsConstructor
		static class Or implements CharPredicate {

			private final List<CharPredicate> children;

			public Or(CharPredicate first, CharPredicate... others) {
				this.children = new ArrayList<>(1 + others.length);

				children.add(first);
				Collections.addAll(children, others);
			}

			@Override
			public boolean test(char character) {
				for (final var child : children) {
					if (child.test(character)) {
						return true;
					}
				}

				return false;
			}

			@Override
			public String toString() {
				return children.stream()
					.map(CharPredicate::toString)
					.collect(Collectors.joining("", "[", "]"));
			}

		}

		@RequiredArgsConstructor
		static class Not implements CharPredicate {

			private final CharPredicate predicate;

			@Override
			public boolean test(char character) {
				return !predicate.test(character);
			}

			@Override
			public String toString() {
				if (predicate instanceof Or or) {
					return or.children.stream()
						.map(CharPredicate::toString)
						.collect(Collectors.joining("", "[^", "]"));
				} else {
					return "[^" + predicate.toString() + "]";
				}
			}

		}

		@RequiredArgsConstructor
		static class Any implements CharPredicate {

			@Override
			public boolean test(char character) {
				return true;
			}

			@Override
			public String toString() {
				return ".";
			}

		}

	}

	@RequiredArgsConstructor
	enum CharacterRangeClass implements CharPredicate {

		DIGITS('d') {

			@Override
			public boolean test(char character) {
				return character >= '0' && character <= '9';
			}

		},

		WORDS('w') {

			@Override
			public boolean test(char character) {
				return (character >= '0' && character <= '9')
					|| (character >= 'a' && character <= 'z')
					|| (character >= 'A' && character <= 'Z')
					|| (character == '_');
			}

		};

		private final char identifier;

		@Override
		public String toString() {
			return "\\" + identifier;
		}

		public static CharacterRangeClass fromIdentifier(char identifier) {
			for (CharacterRangeClass characterClass : values()) {
				if (characterClass.identifier == identifier) {
					return characterClass;
				}
			}

			throw new IllegalArgumentException("unknown character class identifier: " + identifier);
		}

	}

	static class AsciiArrayClass implements CharPredicate {

		private final boolean[] characters;

		public AsciiArrayClass() {
			this.characters = new boolean[256];
		}

		public AsciiArrayClass(boolean[] characters) {
			this.characters = characters;
		}

		public boolean add(char character) {
			characters[character] = true;
			return true;
		}

		@Override
		public boolean test(char character) {
			return characters[character];
		}

		@Override
		public String toString() {
			final var builder = new StringBuilder();

			for (var index = 0; index < characters.length; ++index) {
				if (characters[index]) {
					if (builder.length() > 0) {
						builder.append(',');
					}

					builder.append((char) index);
				}
			}

			return builder.toString();
		}

	}

}