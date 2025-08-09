package grep;

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
					final var characterClass = CharacterClass.fromIdentifier(currentChar);

					current.next = current = new Range(characterClass);
					break;
				}

				default: {
					current.next = current = new Literal(currentChar);
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
	static class Literal extends Node {

		final char character;

		@Override
		public boolean match(Matcher matcher, int index, CharSequence sequence) {
			if (index < matcher.to) {
				char value = sequence.charAt(index);

				++index;

				if (index <= matcher.to) {
					return value == character && next.match(matcher, index, sequence);
				}
			}

			matcher.hitEnd = true;
			return false;
		}

	}

	@RequiredArgsConstructor
	static class Range extends Node {

		final CharacterClass characterClass;

		@Override
		public boolean match(Matcher matcher, int index, CharSequence sequence) {
			if (index < matcher.to) {
				char value = sequence.charAt(index);

				++index;

				if (index <= matcher.to) {
					return characterClass.test(value) && next.match(matcher, index, sequence);
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

}