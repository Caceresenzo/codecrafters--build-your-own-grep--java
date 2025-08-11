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

	public void debug() {
		new Printer(root).print();
	}

	public static Pattern compile(String expression) {
		return new Parser(expression).parse();
	}

	static class Parser {

		private Node absoluteLast = new Last();
		private Context context = new Context();

		private String expression;
		private int index = 0;

		private int groupCount = 0;

		Parser(String expression) {
			this.expression = expression;
		}

		Pattern parse() {
			final var contexts = new ArrayList<Context>();
			contexts.add(context);

			while (hasNext()) {
				if (match('|')) {
					context = new Context();
					contexts.add(context);

					// TODO Handle double pipes
					continue;
				}

				parseNext();
			}

			final var root = new Start();
			root.next = toBranchIfNecessary(contexts, absoluteLast);

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
				case '+' -> context.replace(new Repeat(context.current, 1, Repeat.UNBOUNDED));
				case '?' -> context.replace(new LazyRepeat(context.current, 0, 1));
				case '.' -> context.add(new Char(new CharPredicate.Any()));
				case '(' -> handleCaptureGroup();
				default -> context.add(new Char(new CharPredicate.Character(character)));
			}

			return true;
		}

		private char peek() {
			return expression.charAt(index);
		}

		private boolean match(char character) {
			if (peek() == character) {
				consume();
				return true;
			}

			return false;
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

			context.add(new Char(predicate));
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

			context.add(new Char(predicate));
		}

		private void handleCaptureGroup() {
			final var number = ++groupCount;

			final var previousContext = context;

			final var contexts = new ArrayList<Context>();
			contexts.add(context = new Context());

			while (!match(')')) {
				if (match('|')) {
					context = new Context();
					contexts.add(context);

					// TODO Handle consecutive pipes, as context.root will be null
					continue;
				}

				parseNext();
			}

			final var root = toBranchIfNecessary(contexts, new Last());
			final var group = new Group(number, root);

			context = previousContext;
			context.add(group);
		}

		private Node toBranchIfNecessary(List<Context> contexts, Node last) {
			if (contexts.size() == 1) {
				context.end(last);
				return context.root;
			} else {
				final var roots = new ArrayList<Node>(contexts.size());

				for (final var context : contexts) {
					context.end(last);
					roots.add(context.root);
				}

				final var branch = new Branch(roots);
				branch.next = last;

				return branch;
			}
		}

		static class Context {

			Node root;
			Node current;
			Node previous;

			final List<Node> toLinkToEnd = new ArrayList<>();

			void add(Node node) {
				if (root == null) {
					root = node;
					current = node;
				} else {
					previous = current;
					previous.next = current = node;
				}
			}

			void replace(Node node) {
				toLinkToEnd.add(current);
				previous.next = current = node;
			}

			public void end(Node last) {
				current.next = last;

				for (final var node : toLinkToEnd) {
					node.next = last;
				}
			}

		}

	}

	@RequiredArgsConstructor
	static class Printer {

		private int index;
		private int depth = 1;
		private final Node root;

		public void print() {
			printNode(root);
		}

		private void printNode(Node node) {
			if (node == null) {
				return;
			}

			final var indent = "%3d: ".formatted(index) + "  ".repeat(depth);
			final var blankIndent = "     " + "  ".repeat(depth);
			++index;

			if (node instanceof Start start) {
				System.out.println(indent + "<Start>");

				printNode(start.next);
			} else if (node instanceof Char char_) {
				System.out.println(indent + "<Char `" + char_.predicate + "`>");

				printNode(char_.next);
			} else if (node instanceof Begin begin) {
				System.out.println(indent + "<Begin>");

				printNode(begin.next);
			} else if (node instanceof End end) {
				System.out.println(indent + "<End>");

				printNode(end.next);
			} else if (node instanceof Repeat repeat) {
				System.out.println(indent + "<Repeat " + repeat + ">");

				++depth;
				printNode(repeat.atom);
				--depth;

				System.out.println(blankIndent + "</Repeat>");

				printNode(repeat.next);
			} else if (node instanceof Group group) {
				System.out.println(indent + "<Group " + group.number + ">");

				++depth;
				printNode(group.atom);
				--depth;

				System.out.println(blankIndent + "</Group " + group.number + ">");

				printNode(group.next);
			} else if (node instanceof Branch branch) {
				System.out.println(indent + "<Branch>");

				++depth;
				for (var index = 0; index < branch.atoms.size(); ++index) {
					if (index > 0) {
						System.out.println(blankIndent + "---");
					}

					printNode(branch.atoms.get(index));
				}
				--depth;

				System.out.println(blankIndent + "</Branch>");

				printNode(branch.next);
			} else if (node instanceof Last) {
				System.out.println(indent + "<Last>");
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
	static class Char extends Node {

		final CharPredicate predicate;

		@Override
		public boolean match(Matcher matcher, int index, CharSequence sequence) {
			if (index < matcher.to) {
				char value = sequence.charAt(index);

				// TODO currently only support ascii
				return predicate.test(value)
					&& next.match(matcher, index + 1, sequence);
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

			return matchMax(matcher, index, sequence, count);
		}

		public boolean matchMax(Matcher matcher, int index, CharSequence sequence, int count) {
			final var maxCount = this.max == UNBOUNDED ? Integer.MAX_VALUE : this.max;

			while (index < matcher.to && count++ < maxCount) {
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
			if (min == 0 && max == 1) {
				return "?";
			} else if (min == 0 && max == UNBOUNDED) {
				return "*";
			} else if (min == 1 && max == UNBOUNDED) {
				return "+";
			} else if (max == UNBOUNDED) {
				return "{" + min + ",}";
			} else if (min == UNBOUNDED) {
				return "{," + max + "}";
			} else if (min == max) {
				return "{" + min + "}";
			}

			return "{" + min + "," + max + "}";
		}

	}

	static class LazyRepeat extends Repeat {

		public LazyRepeat(Node atom, int min, int max) {
			super(atom, min, max);
		}

		@Override
		public boolean matchMax(Matcher matcher, int index, CharSequence sequence, int count) {
			final var maxCount = this.max == UNBOUNDED ? Integer.MAX_VALUE : this.max;

			while (index < matcher.to && count++ < maxCount) {
				if (!atom.match(matcher, index, sequence)) {
					break;
				}

				index = matcher.last;

				if (next.match(matcher, index, sequence)) {
					return true;
				}
			}

			return next.match(matcher, index, sequence);
		}

	}

	@RequiredArgsConstructor
	static class Group extends Node {

		final int number;
		final Node atom;

		@Override
		public boolean match(Matcher matcher, int index, CharSequence sequence) {
			matcher.groupStarts[number] = index;

			if (!atom.match(matcher, index, sequence)) {
				matcher.groupStarts[number] = -1;
				return false;
			}

			final var endIndex = matcher.groupEnds[number] = matcher.last;

			if (!next.match(matcher, endIndex, sequence)) {
				matcher.groupStarts[number] = -1;
				matcher.groupEnds[number] = -1;
				return false;
			}

			return true;
		}

		@Override
		public String toString() {
			return "Group(" + number + ")";
		}

	}

	@RequiredArgsConstructor
	static class Branch extends Node {

		final List<Node> atoms;

		@Override
		public boolean match(Matcher matcher, int index, CharSequence sequence) {
			for (final var atom : atoms) {
				if (atom.match(matcher, index, sequence)) {
					final var endIndex = matcher.last;

					if (next.match(matcher, endIndex, sequence)) {
						return true;
					}
				}
			}

			return false;
		}

		@Override
		public String toString() {
			return "|";
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
				return (character >= '0' && character <= '9') || (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z') || (character == '_');
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