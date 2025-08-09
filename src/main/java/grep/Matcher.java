package grep;

public class Matcher {

	final Pattern pattern;
	final CharSequence text;

	int first, last;
	int from, to;
	boolean hitEnd;

	Matcher(Pattern pattern, CharSequence text) {
		super();
		this.pattern = pattern;
		this.text = text;

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

	boolean search(int from) {
		hitEnd = false;

		final var found = pattern.root.match(this, from, text);
		if (!found) {
			first = -1;
		}

		return found;
	}

}