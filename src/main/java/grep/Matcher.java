package grep;

public class Matcher {

	final Pattern pattern;
	final CharSequence text;

	final int groupStarts[], groupEnds[];

	int first, last;
	int from, to;
	boolean hitEnd;

	Matcher(Pattern pattern, CharSequence text) {
		this.pattern = pattern;
		this.text = text;

		this.groupStarts = new int[pattern.groupCount + 1];
		this.groupEnds = new int[pattern.groupCount + 1];

		reset();
	}

	public Matcher reset() {
		first = -1;
		last = 0;

		from = 0;
		to = text.length();

		return this;
	}

	public boolean find(int from) {
		reset();
		return search(from);
	}

	public String group() {
		return group(0);
	}

	public String group(int group) {
		final var start = groupStarts[group];
		final var end = groupEnds[group];

		return text.subSequence(start, end).toString();
	}

	public int groupCount() {
		return pattern.groupCount;
	}

	boolean search(int from) {
		hitEnd = false;

		final var found = pattern.root.match(this, from, text);
		if (!found) {
			first = -1;
		}

		return found;
	}

}