package grep;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum CharacterClass {

	DIGITS('d') {

		@Override
		public boolean test(char character) {
			return character >= '0' && character <= '9';
		}

	};

	private final char identifier;

	public abstract boolean test(char character);

	public static CharacterClass fromIdentifier(char identifier) {
		for (CharacterClass characterClass : values()) {
			if (characterClass.identifier == identifier) {
				return characterClass;
			}
		}

		throw new IllegalArgumentException("unknown character class identifier: " + identifier);
	}

}