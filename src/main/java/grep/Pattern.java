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
					final var characterClass = CharacterRangeClass.fromIdentifier(currentChar);

					current.next = current = new CharProperty(characterClass);
					break;
				}

				case '[': {
					final var array = new AsciiArrayClass();
					final var ranges = new ArrayList<CharacterRangeClass>();

					while (index < expression.length()) {
						currentChar = expression.charAt(index++);

						if (currentChar == ']') {
							break;
						}

						if (currentChar == '\\') {
							currentChar = expression.charAt(index++);

							final var characterClass = CharacterRangeClass.fromIdentifier(currentChar);
							ranges.add(characterClass);
						} else {
							array.add(currentChar);
						}
					}

					final CharPredicate predicate;

					if (ranges.isEmpty()) {
						predicate = array;
					} else {
						predicate = new OrCharPredicate(array, ranges.toArray(CharPredicate[]::new));
					}

					current.next = current = new CharProperty(predicate);
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

	@RequiredArgsConstructor
	static class OrCharPredicate implements CharPredicate {

		private final List<CharPredicate> children;

		public OrCharPredicate(CharPredicate first, CharPredicate... others) {
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

}