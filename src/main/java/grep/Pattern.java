package grep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

		int index = 0;

		char currentChar;
		while (index < expression.length()) {
			currentChar = expression.charAt(index++);

			switch (currentChar) {
				case '\\': {
					currentChar = expression.charAt(index++);

					CharPredicate predicate;
					if (currentChar == '\\') {
						predicate = new AsciiClass(currentChar);
					} else {
						predicate = CharacterRangeClass.fromIdentifier(currentChar);
					}

					current.next = current = new CharProperty(predicate);
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

					current.next = current = new CharProperty(predicate);
					break;
				}

				case '^': {
					current.next = current = new Begin();
					break;
				}

				default: {
					current.next = current = new CharProperty(new AsciiClass(currentChar));
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

	}

	@RequiredArgsConstructor
	static class CharProperty extends Node {

		final CharPredicate predicate;

		@Override
		public boolean match(Matcher matcher, int index, CharSequence sequence) {
			if (index < matcher.to) {
				char value = sequence.charAt(index);

				++index;

				if (index <= matcher.to) {
					return predicate.test(value) && next.match(matcher, index, sequence);
				}
			}

			matcher.hitEnd = true;
			return false;
		}

	}

	static class Begin extends Node {

		@Override
		public boolean match(Matcher matcher, int index, CharSequence sequence) {
			final var from = matcher.from;

			if (index == from && next.match(matcher, index, sequence)) {
				matcher.first = index;
				return true;
			}

			return false;
		}

	}

	@RequiredArgsConstructor
	static class Last extends Node {

		@Override
		public boolean match(Matcher matcher, int index, CharSequence sequence) {
			matcher.last = index;
			return true;
		}

	}

	@FunctionalInterface
	static interface CharPredicate {

		boolean test(char character);

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

		}

		@RequiredArgsConstructor
		static class Not implements CharPredicate {

			private final CharPredicate predicate;

			@Override
			public boolean test(char character) {
				return !predicate.test(character);
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

		public static CharacterRangeClass fromIdentifier(char identifier) {
			for (CharacterRangeClass characterClass : values()) {
				if (characterClass.identifier == identifier) {
					return characterClass;
				}
			}

			throw new IllegalArgumentException("unknown character class identifier: " + identifier);
		}

	}

	@RequiredArgsConstructor
	static class AsciiClass implements CharPredicate {

		private final char value;

		@Override
		public boolean test(char character) {
			return value == character;
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

	}

}