package grep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import grep.Pattern.Parser.Quantifier;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Pattern {

	final String expression;
	final Node root;
	final int groupCount;

	public Matcher matcher(CharSequence sequence) {
		return new Matcher(this, sequence);
	}

	public void debug() {
		new Printer(root).print();
	}

	@Override
	public String toString() {
		return "Pattern{%s}".formatted(expression);
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

					// TODO Handle double pipes, aka zero-length
					continue;
				}

				parseNext();
			}

			final var root = new Start();
			root.next = toBranchIfNecessary(contexts, absoluteLast, absoluteLast);

			return new Pattern(expression, root, groupCount);
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
				case '+' -> throw new IllegalArgumentException("unescaped `+` is not allowed");
				case '?' -> throw new IllegalArgumentException("unescaped `?` is not allowed");
				case '.' -> handleCharacter(new CharPredicate.Any());
				case '(' -> handleCaptureGroup();
				default -> handleCharacter(new CharPredicate.Character(character));
			}

			return true;
		}

		private char peek() {
			if (index >= expression.length()) {
				return '\0';
			}

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

		private void handleCharacter(CharPredicate predicate) {
			final var node = new Char(predicate);
			context.add(node);

			final var quantifier = matchQuantifier();
			if (quantifier != null) {
				node.next = new Last();

				final var repeat = new Repeat(node, quantifier);
				context.replace(repeat);
			}
		}

		private void handleEscape() {
			final var character = consume();

			if (character == '\\') {
				handleCharacter(new CharPredicate.Character(character));
			} else if (Character.isDigit(character)) {
				context.add(new BackReference(Character.digit(character, 10)));
			} else {
				handleCharacter(CharacterRangeClass.fromIdentifier(character));
			}
		}

		private void handleCharacterGroup() {
			final var array = new AsciiArrayClass();
			final var ranges = new ArrayList<CharacterRangeClass>();

			final var negate = match('^');

			while (hasNext()) {
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

			handleCharacter(predicate);
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

			final var tail = new GroupTail(number);
			final var head = new GroupHead(number, tail);

			final var root = toBranchIfNecessary(contexts, tail, new Last());
			head.next = root;

			context = previousContext;

			final var quantifier = matchQuantifier();
			if (quantifier != null) {
				final var node = new Repeat(head, quantifier);
				tail.next = new Last();

				context.add(node);
			} else {
				context.add(head, tail);
			}
		}

		private Quantifier matchQuantifier() {
			if (match('+')) {
				return Quantifier.oneOrMore();
			} else if (match('?')) {
				return Quantifier.zeroOrOne();
			} else if (match('*')) {
				return Quantifier.zeroOrMore();
			} else if (!match('{')) {
				return null;
			}

			final var minimum = parseNumber();
			if (match('}')) {
				return Quantifier.exactly(minimum);
			}

			if (match(',')) {
				match('}');

				return new Quantifier(minimum, Quantifier.UNBOUNDED);
			}

			throw new UnsupportedOperationException();
		}

		private int parseNumber() {
			final var digits = consumeWhile(Character::isDigit);

			return Integer.parseInt(digits);
		}

		private String consumeWhile(CharPredicate condition) {
			final var builder = new StringBuilder();

			char character;
			while ((character = peek()) != '\0') {
				if (!condition.test(character)) {
					break;
				}

				builder.append(consume());
			}

			return builder.toString();
		}

		private Node toBranchIfNecessary(List<Context> contexts, Node last, Node intermediateLast) {
			if (contexts.size() == 1) {
				context.end(last, intermediateLast);
				return context.root;
			} else {
				final var roots = new ArrayList<Node>(contexts.size());

				for (final var context : contexts) {
					context.end(intermediateLast, intermediateLast);
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

			void add(Node head, Node tail) {
				if (root == null) {
					root = head;
					current = tail;
				} else {
					previous = current;
					previous.next = head;
					current = tail;
				}
			}

			void replace(Node node) {
				toLinkToEnd.add(current);

				if (previous == null) {
					root = current = node;
				} else {
					previous.next = current = node;
				}
			}

			public void end(Node last, Node intermediateLast) {
				for (final var node : toLinkToEnd) {
					node.next = intermediateLast;
				}

				current.next = last;
			}

		}

		record Quantifier(
			int min,
			int max
		) {

			public static final int UNBOUNDED = Repeat.UNBOUNDED;

			public static Quantifier oneOrMore() {
				return new Quantifier(1, Repeat.UNBOUNDED);
			}

			public static Quantifier zeroOrOne() {
				return new Quantifier(0, 1);
			}

			public static Quantifier zeroOrMore() {
				return new Quantifier(0, Repeat.UNBOUNDED);
			}

			public static Quantifier exactly(int times) {
				return new Quantifier(times, times);
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
			} else if (node instanceof GroupHead groupHead) {
				System.out.println(indent + "<Group.head " + groupHead.number + ">");

				printNode(groupHead.next);
			} else if (node instanceof GroupTail groupTail) {
				System.out.println(indent + "<Group.tail " + groupTail.number + ">");

				printNode(groupTail.next);
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
			} else if (node instanceof BackReference backReference) {
				System.out.println(indent + "<BackReference " + backReference.groupNumber + ">");

				printNode(backReference.next);
			} else if (node instanceof Last) {
				System.out.println(indent + "<Last>");
			}
		}

	}

	abstract static class Node {

		Node next;

		public abstract boolean match(Matcher matcher, int index, CharSequence sequence);

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
			if (index >= matcher.to) {
				matcher.hitEnd = true;
				return false;
			}

			char value = sequence.charAt(index);

			// TODO currently only support ascii
			return predicate.test(value)
				&& next.match(matcher, index + 1, sequence);
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

		public Repeat(Node atom, Quantifier quantifier) {
			this(atom, quantifier.min, quantifier.max);
		}

		@Override
		public boolean match(Matcher matcher, int index, CharSequence sequence) {
			int count = 0;
			for (; count < min; ++count) {
				if (!atom.match(matcher, index, sequence)) {
					return false;
				}

				index = matcher.last;
			}

			final var maxCount = this.max == UNBOUNDED ? Integer.MAX_VALUE : this.max;

			return matchMax(matcher, index, sequence, count, maxCount);
		}

		public boolean matchMax(Matcher matcher, int index, CharSequence sequence, int count, int maxCount) {
			if (index < matcher.to && count++ < maxCount && atom.match(matcher, index, sequence)) {
				final var lastIndex = matcher.last;

				if (matchMax(matcher, lastIndex, sequence, count, maxCount)) {
					return true;
				}

				if (next.match(matcher, lastIndex, sequence)) {
					return true;
				}
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

	@RequiredArgsConstructor
	static class GroupHead extends Node {

		final int number;
		final Node tail;

		@Override
		public boolean match(Matcher matcher, int index, CharSequence sequence) {
			matcher.groupStarts[number] = index;

			if (!next.match(matcher, index, sequence)) {
				matcher.groupStarts[number] = -1;
				return false;
			}

			return true;
		}

		@Override
		public String toString() {
			return "Group.head(" + number + ")";
		}

	}

	@RequiredArgsConstructor
	static class GroupTail extends Node {

		final int number;

		@Override
		public boolean match(Matcher matcher, int index, CharSequence sequence) {
			matcher.groupEnds[number] = index;

			if (!next.match(matcher, index, sequence)) {
				matcher.groupEnds[number] = -1;
				return false;
			}

			return true;
		}

		@Override
		public String toString() {
			return "Group.tail(" + number + ")";
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

	@RequiredArgsConstructor
	static class BackReference extends Node {

		final int groupNumber;

		@Override
		public boolean match(Matcher matcher, int index, CharSequence sequence) {
			final var start = matcher.groupStarts[groupNumber];
			final var end = matcher.groupEnds[groupNumber];

			final var length = end - start;

			/* group not matched */
			if (length < 0) {
				return false;
			}

			/* not enough characters left */
			if (index + length > matcher.to) {
				matcher.hitEnd = true;
				return false;
			}

			for (var jndex = 0; jndex < length; ++jndex) {
				if (sequence.charAt(start + jndex) != sequence.charAt(index + jndex)) {
					return false;
				}
			}

			return next.match(matcher, index + length, sequence);
		}

		@Override
		public String toString() {
			return "\\" + groupNumber + "";
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