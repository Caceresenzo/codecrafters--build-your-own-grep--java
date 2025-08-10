package grep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

public class Pattern {

	final Node root;
	final int groupCount;

	private Pattern(Node root, int groupCount) {
		this.root = root;
		this.groupCount = groupCount;
	}

	public Matcher matcher(CharSequence sequence) {
		return new Matcher(this, sequence);
	}

	public static Pattern compile(String expression) {
		return new Parser(expression).parse();
	}

	static class Parser {

		private Context context = new Context(null, new Last());

		private String expression;
		private int index = 0;

		private int groupCount = 0;

		Parser(String expression) {
			this.expression = expression;
		}

		Pattern parse() {
			while (hasNext()) {
				parseNext();
			}

			context.end();

			final var root = new Start();
			root.next = context.root;

			return new Pattern(root, groupCount);
		}

		public boolean hasNext() {
			return index < expression.length();
		}

		private boolean parseNext() {
			final var character = consume();

			switch (character) {
				case '\\' -> handleEscape();
				case '[' -> handleCharacterGroup();
				case '^' -> context.add(new Begin());
				case '$' -> context.add(new End());
				case '+' -> context.replace(new Repeat(context.current, 1, Repeat.UNBOUNDED), context.last);
				case '?' -> context.replace(new Repeat(context.current, 0, 1), context.last);
				case '.' -> context.add(new CharProperty(new CharPredicate.Any()));
				case '(' -> handleCaptureGroup();
				default -> context.add(new CharProperty(new CharPredicate.Character(character)));
			}

			return true;
		}

		private char peek() {
			return expression.charAt(index);
		}

		private char consume() {
			return expression.charAt(index++);
		}

		private void handleEscape() {
			final var character = consume();

			CharPredicate predicate;
			if (character == '\\') {
				predicate = new CharPredicate.Character(character);
			} else {
				predicate = CharacterRangeClass.fromIdentifier(character);
			}

			context.add(new CharProperty(predicate));
		}

		private void handleCharacterGroup() {
			final var array = new AsciiArrayClass();
			final var ranges = new ArrayList<CharacterRangeClass>();

			final var negate = index < expression.length() && expression.charAt(index) == '^';
			if (negate) {
				index++;
			}

			while (index < expression.length()) {
				var character = consume();

				if (character == ']') {
					break;
				}

				if (character == '\\') {
					character = consume();

					if (character == '\\') {
						array.add(character);
					} else {
						final var characterClass = CharacterRangeClass.fromIdentifier(character);
						ranges.add(characterClass);
					}
				} else {
					array.add(character);
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

			context.add(new CharProperty(predicate));
		}

		private void handleCaptureGroup() {
			final var number = ++groupCount;

			final var head = new GroupHead(number);
			final var tail = new GroupTail(number);

			final var previousContext = context;
			context = new Context(null, tail);

			while (peek() != ')') {
				parseNext();
			}
			consume(); /* closing parenthesis */

			context.end();
			head.next = context.root;

			context = previousContext;
			context.current.next = head;
			context.current = tail;
			context.previous = head;
		}

		static class Context {

			Node root;
			Node current;
			Node previous;
			Node last;

			private Context(Node root, Node last) {
				this.root = root;
				this.current = root;
				this.last = last;
			}

			void add(Node node) {
				if (root == null) {
					root = node;
					current = node;
				} else {
					previous = current;
					previous.next = current = node;
				}
			}

			void replace(Node node, Node last) {
				current.next = last;
				previous.next = current = node;
			}

			public void end() {
				current.next = last;
			}

		}

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
					matcher.groupStarts[0] = matcher.first;
					matcher.groupEnds[0] = matcher.last;
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

	@RequiredArgsConstructor
	static class GroupHead extends Node {

		final int number;

		@Override
		public boolean match(Matcher matcher, int index, CharSequence sequence) {
			matcher.groupStarts[number] = index;

			return next.match(matcher, index, sequence);
		}

		@Override
		public String toString() {
			return "(";
		}

	}

	@RequiredArgsConstructor
	static class GroupTail extends Node {

		final int number;

		@Override
		public boolean match(Matcher matcher, int index, CharSequence sequence) {
			matcher.groupEnds[number] = index;

			return next.match(matcher, index, sequence);
		}

		@Override
		public String toString() {
			return ")";
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