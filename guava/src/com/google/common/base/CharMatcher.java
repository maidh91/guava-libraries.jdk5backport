/*
 * Copyright (C) 2008 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.base;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.Beta;
import com.google.common.annotations.GwtCompatible;

import java.util.Arrays;
import javax.annotation.CheckReturnValue;

/**
 * Determines a true or false value for any Java {@code char} value, just as {@link Predicate} does
 * for any {@link Object}. Also offers basic text processing methods based on this function.
 * Implementations are strongly encouraged to be side-effect-free and immutable.
 *
 * <p>Throughout the documentation of this class, the phrase "matching character" is used to mean
 * "any character {@code c} for which {@code this.matches(c)} returns {@code true}".
 *
 * <p><b>Note:</b> This class deals only with {@code char} values; it does not understand
 * supplementary Unicode code points in the range {@code 0x10000} to {@code 0x10FFFF}. Such logical
 * characters are encoded into a {@code String} using surrogate pairs, and a {@code CharMatcher}
 * treats these just as two separate characters.
 *
 * <p>Example usages: <pre>
 *   String trimmed = {@link #WHITESPACE WHITESPACE}.{@link #trimFrom trimFrom}(userInput);
 *   if ({@link #ASCII ASCII}.{@link #matchesAllOf matchesAllOf}(s)) { ... }</pre>
 *
 * <p>See the Guava User Guide article on <a href=
 * "http://code.google.com/p/guava-libraries/wiki/StringsExplained#CharMatcher">
 * {@code CharMatcher}</a>.
 *
 * @author Kevin Bourrillion
 * @since 1.0
 */
@Beta // Possibly change from chars to code points; decide constants vs. methods
@GwtCompatible
public abstract class CharMatcher implements Predicate<Character> {
  // Constants
  /**
   * Determines whether a character is a breaking whitespace (that is, a whitespace which can be
   * interpreted as a break between words for formatting purposes). See {@link #WHITESPACE} for a
   * discussion of that term.
   *
   * @since 2.0
   */
  public static final CharMatcher BREAKING_WHITESPACE =
      anyOf("\t\n\013\f\r \u0085\u1680\u2028\u2029\u205f\u3000")
          .or(inRange('\u2000', '\u2006'))
          .or(inRange('\u2008', '\u200a'))
          .withToString("CharMatcher.BREAKING_WHITESPACE")
          .precomputed();

  /**
   * Determines whether a character is ASCII, meaning that its code point is less than 128.
   */
  public static final CharMatcher ASCII = inRange('\0', '\u007f', "CharMatcher.ASCII");

  /**
   * Determines whether a character is a digit according to
   * <a href="http://unicode.org/cldr/utility/list-unicodeset.jsp?a=%5Cp%7Bdigit%7D">Unicode</a>.
   */
  public static final CharMatcher DIGIT;

  static {
    CharMatcher digit = inRange('0', '9');
    String zeroes =
        "\u0660\u06f0\u07c0\u0966\u09e6\u0a66\u0ae6\u0b66\u0be6\u0c66"
            + "\u0ce6\u0d66\u0e50\u0ed0\u0f20\u1040\u1090\u17e0\u1810\u1946"
            + "\u19d0\u1b50\u1bb0\u1c40\u1c50\ua620\ua8d0\ua900\uaa50\uff10";
    for (char base : zeroes.toCharArray()) {
      digit = digit.or(inRange(base, (char) (base + 9)));
    }
    DIGIT = digit.withToString("CharMatcher.DIGIT").precomputed();
  }

  /**
   * Determines whether a character is a digit according to {@link Character#isDigit(char) Java's
   * definition}. If you only care to match ASCII digits, you can use {@code inRange('0', '9')}.
   */
  public static final CharMatcher JAVA_DIGIT = new CharMatcher("CharMatcher.JAVA_DIGIT") {
    @Override public boolean matches(char c) {
      return Character.isDigit(c);
    }
  };

  /**
   * Determines whether a character is a letter according to {@link Character#isLetter(char) Java's
   * definition}. If you only care to match letters of the Latin alphabet, you can use {@code
   * inRange('a', 'z').or(inRange('A', 'Z'))}.
   */
  public static final CharMatcher JAVA_LETTER = new CharMatcher("CharMatcher.JAVA_LETTER") {
    @Override public boolean matches(char c) {
      return Character.isLetter(c);
    }

    @Override public CharMatcher precomputed() {
      return this;
    }
  };

  /**
   * Determines whether a character is a letter or digit according to {@link
   * Character#isLetterOrDigit(char) Java's definition}.
   */
  public static final CharMatcher JAVA_LETTER_OR_DIGIT =
      new CharMatcher("CharMatcher.JAVA_LETTER_OR_DIGIT") {
    @Override public boolean matches(char c) {
      return Character.isLetterOrDigit(c);
    }
  };

  /**
   * Determines whether a character is upper case according to {@link Character#isUpperCase(char)
   * Java's definition}.
   */
  public static final CharMatcher JAVA_UPPER_CASE =
      new CharMatcher("CharMatcher.JAVA_UPPER_CASE") {
    @Override public boolean matches(char c) {
      return Character.isUpperCase(c);
    }
  };

  /**
   * Determines whether a character is lower case according to {@link Character#isLowerCase(char)
   * Java's definition}.
   */
  public static final CharMatcher JAVA_LOWER_CASE =
      new CharMatcher("CharMatcher.JAVA_LOWER_CASE") {
    @Override public boolean matches(char c) {
      return Character.isLowerCase(c);
    }
  };

  /**
   * Determines whether a character is an ISO control character as specified by {@link
   * Character#isISOControl(char)}.
   */
  public static final CharMatcher JAVA_ISO_CONTROL =
      inRange('\u0000', '\u001f')
      .or(inRange('\u007f', '\u009f'))
      .withToString("CharMatcher.JAVA_ISO_CONTROL");

  /**
   * Determines whether a character is invisible; that is, if its Unicode category is any of
   * SPACE_SEPARATOR, LINE_SEPARATOR, PARAGRAPH_SEPARATOR, CONTROL, FORMAT, SURROGATE, and
   * PRIVATE_USE according to ICU4J.
   */
  public static final CharMatcher INVISIBLE = inRange('\u0000', '\u0020')
      .or(inRange('\u007f', '\u00a0'))
      .or(is('\u00ad'))
      .or(inRange('\u0600', '\u0604'))
      .or(anyOf("\u06dd\u070f\u1680\u180e"))
      .or(inRange('\u2000', '\u200f'))
      .or(inRange('\u2028', '\u202f'))
      .or(inRange('\u205f', '\u2064'))
      .or(inRange('\u206a', '\u206f'))
      .or(is('\u3000'))
      .or(inRange('\ud800', '\uf8ff'))
      .or(anyOf("\ufeff\ufff9\ufffa\ufffb"))
      .withToString("CharMatcher.INVISIBLE")
      .precomputed();

  /**
   * Determines whether a character is single-width (not double-width). When in doubt, this matcher
   * errs on the side of returning {@code false} (that is, it tends to assume a character is
   * double-width).
   *
   * <p><b>Note:</b> as the reference file evolves, we will modify this constant to keep it up to
   * date.
   */
  public static final CharMatcher SINGLE_WIDTH = inRange('\u0000', '\u04f9')
      .or(is('\u05be'))
      .or(inRange('\u05d0', '\u05ea'))
      .or(is('\u05f3'))
      .or(is('\u05f4'))
      .or(inRange('\u0600', '\u06ff'))
      .or(inRange('\u0750', '\u077f'))
      .or(inRange('\u0e00', '\u0e7f'))
      .or(inRange('\u1e00', '\u20af'))
      .or(inRange('\u2100', '\u213a'))
      .or(inRange('\ufb50', '\ufdff'))
      .or(inRange('\ufe70', '\ufeff'))
      .or(inRange('\uff61', '\uffdc'))
      .withToString("CharMatcher.SINGLE_WIDTH")
      .precomputed();

  /** Matches any character. */
  public static final CharMatcher ANY =
      new CharMatcher("CharMatcher.ANY") {
        @Override public boolean matches(char c) {
          return true;
        }

        @Override public int indexIn(CharSequence sequence) {
          return (sequence.length() == 0) ? -1 : 0;
        }

        @Override public int indexIn(CharSequence sequence, int start) {
          int length = sequence.length();
          Preconditions.checkPositionIndex(start, length);
          return (start == length) ? -1 : start;
        }

        @Override public int lastIndexIn(CharSequence sequence) {
          return sequence.length() - 1;
        }

        @Override public boolean matchesAllOf(CharSequence sequence) {
          checkNotNull(sequence);
          return true;
        }

        @Override public boolean matchesNoneOf(CharSequence sequence) {
          return sequence.length() == 0;
        }

        @Override public String removeFrom(CharSequence sequence) {
          checkNotNull(sequence);
          return "";
        }

        @Override public String replaceFrom(CharSequence sequence, char replacement) {
          char[] array = new char[sequence.length()];
          Arrays.fill(array, replacement);
          return new String(array);
        }

        @Override public String replaceFrom(CharSequence sequence, CharSequence replacement) {
          StringBuilder retval = new StringBuilder(sequence.length() * replacement.length());
          for (int i = 0; i < sequence.length(); i++) {
            retval.append(replacement);
          }
          return retval.toString();
        }

        @Override public String collapseFrom(CharSequence sequence, char replacement) {
          return (sequence.length() == 0) ? "" : String.valueOf(replacement);
        }

        @Override public String trimFrom(CharSequence sequence) {
          checkNotNull(sequence);
          return "";
        }

        @Override public int countIn(CharSequence sequence) {
          return sequence.length();
        }

        @Override public CharMatcher and(CharMatcher other) {
          return checkNotNull(other);
        }

        @Override public CharMatcher or(CharMatcher other) {
          checkNotNull(other);
          return this;
        }

        @Override public CharMatcher negate() {
          return NONE;
        }

        @Override public CharMatcher precomputed() {
          return this;
        }
      };

  /** Matches no characters. */
  public static final CharMatcher NONE =
      new CharMatcher("CharMatcher.NONE") {
        @Override public boolean matches(char c) {
          return false;
        }

        @Override public int indexIn(CharSequence sequence) {
          checkNotNull(sequence);
          return -1;
        }

        @Override public int indexIn(CharSequence sequence, int start) {
          int length = sequence.length();
          Preconditions.checkPositionIndex(start, length);
          return -1;
        }

        @Override public int lastIndexIn(CharSequence sequence) {
          checkNotNull(sequence);
          return -1;
        }

        @Override public boolean matchesAllOf(CharSequence sequence) {
          return sequence.length() == 0;
        }

        @Override public boolean matchesNoneOf(CharSequence sequence) {
          checkNotNull(sequence);
          return true;
        }

        @Override public String removeFrom(CharSequence sequence) {
          return sequence.toString();
        }

        @Override public String replaceFrom(CharSequence sequence, char replacement) {
          return sequence.toString();
        }

        @Override public String replaceFrom(CharSequence sequence, CharSequence replacement) {
          checkNotNull(replacement);
          return sequence.toString();
        }

        @Override public String collapseFrom(CharSequence sequence, char replacement) {
          return sequence.toString();
        }

        @Override public String trimFrom(CharSequence sequence) {
          return sequence.toString();
        }

        @Override public int countIn(CharSequence sequence) {
          checkNotNull(sequence);
          return 0;
        }

        @Override public CharMatcher and(CharMatcher other) {
          checkNotNull(other);
          return this;
        }

        @Override public CharMatcher or(CharMatcher other) {
          return checkNotNull(other);
        }

        @Override public CharMatcher negate() {
          return ANY;
        }

        @Override void setBits(LookupTable table) {}

        @Override public CharMatcher precomputed() {
          return this;
        }
      };

  // Static factories

  /**
   * Returns a {@code char} matcher that matches only one specified character.
   */
  public static CharMatcher is(final char match) {
    String description = new StringBuilder("CharMatcher.is(")
        .append(Integer.toHexString(match))
        .append(")")
        .toString();
    return new CharMatcher(description) {
      @Override public boolean matches(char c) {
        return c == match;
      }

      @Override public String replaceFrom(CharSequence sequence, char replacement) {
        return sequence.toString().replace(match, replacement);
      }

      @Override public CharMatcher and(CharMatcher other) {
        return other.matches(match) ? this : NONE;
      }

      @Override public CharMatcher or(CharMatcher other) {
        return other.matches(match) ? other : super.or(other);
      }

      @Override public CharMatcher negate() {
        return isNot(match);
      }

      @Override void setBits(LookupTable table) {
        table.set(match);
      }

      @Override public CharMatcher precomputed() {
        return this;
      }
    };
  }

  /**
   * Returns a {@code char} matcher that matches any character except the one specified.
   *
   * <p>To negate another {@code CharMatcher}, use {@link #negate()}.
   */
  public static CharMatcher isNot(final char match) {
    String description = new StringBuilder("CharMatcher.isNot(")
        .append(Integer.toHexString(match))
        .append(")")
        .toString();
    return new CharMatcher(description) {
      @Override public boolean matches(char c) {
        return c != match;
      }

      @Override public CharMatcher and(CharMatcher other) {
        return other.matches(match) ? super.and(other) : other;
      }

      @Override public CharMatcher or(CharMatcher other) {
        return other.matches(match) ? ANY : this;
      }

      @Override public CharMatcher negate() {
        return is(match);
      }
    };
  }

  /**
   * Returns a {@code char} matcher that matches any character present in the given character
   * sequence.
   */
  public static CharMatcher anyOf(final CharSequence sequence) {
    switch (sequence.length()) {
      case 0:
        return NONE;
      case 1:
        return is(sequence.charAt(0));
      case 2:
        final char match1 = sequence.charAt(0);
        final char match2 = sequence.charAt(1);
        return new CharMatcher(
            new StringBuilder("CharMatcher.anyOf(\"").append(sequence).append("\")").toString()) {
          @Override public boolean matches(char c) {
            return c == match1 || c == match2;
          }

          @Override void setBits(LookupTable table) {
            table.set(match1);
            table.set(match2);
          }

          @Override public CharMatcher precomputed() {
            return this;
          }
        };
    }
    final char[] chars = sequence.toString().toCharArray();
    Arrays.sort(chars);

    return new CharMatcher(new StringBuilder("CharMatcher.anyOf(\"").append(chars)
        .append("\")").toString()) {
          @Override public boolean matches(char c) {
            return Arrays.binarySearch(chars, c) >= 0;
          }
    };
  }

  /**
   * Returns a {@code char} matcher that matches any character not present in the given character
   * sequence.
   */
  public static CharMatcher noneOf(CharSequence sequence) {
    return anyOf(sequence).negate();
  }

  /**
   * Returns a {@code char} matcher that matches any character in a given range (both endpoints are
   * inclusive). For example, to match any lowercase letter of the English alphabet, use {@code
   * CharMatcher.inRange('a', 'z')}.
   *
   * @throws IllegalArgumentException if {@code endInclusive < startInclusive}
   */
  public static CharMatcher inRange(final char startInclusive, final char endInclusive) {
    checkArgument(endInclusive >= startInclusive);
    String description = new StringBuilder("CharMatcher.inRange(")
        .append(Integer.toHexString(startInclusive))
        .append(", ")
        .append(Integer.toHexString(endInclusive))
        .append(")")
        .toString();
    return inRange(startInclusive, endInclusive, description);
  }

  static CharMatcher inRange(final char startInclusive, final char endInclusive,
      String description) {
    char[] chars = new char[endInclusive - startInclusive + 1];
    for (int i = startInclusive; i <= endInclusive; i++) {
      chars[i - startInclusive] = (char) i;
    }
    return new CharMatcher(description) {
      @Override public boolean matches(char c) {
        return startInclusive <= c && c <= endInclusive;
      }

      @Override void setBits(LookupTable table) {
        char c = startInclusive;
        while (true) {
          table.set(c);
          if (c++ == endInclusive) {
            break;
          }
        }
      }

      @Override public CharMatcher precomputed() {
        return this;
      }
    };
  }

  /**
   * Returns a matcher with identical behavior to the given {@link Character}-based predicate, but
   * which operates on primitive {@code char} instances instead.
   */
  public static CharMatcher forPredicate(final Predicate<? super Character> predicate) {
    checkNotNull(predicate);
    if (predicate instanceof CharMatcher) {
      return (CharMatcher) predicate;
    }
    String description = new StringBuilder("CharMatcher.forPredicate(")
        .append(predicate)
        .append(')')
        .toString();
    return new CharMatcher(description) {
      @Override public boolean matches(char c) {
        return predicate.apply(c);
      }

      @Override public boolean apply(Character character) {
        return predicate.apply(checkNotNull(character));
      }
    };
  }

  // State
  final String description;

  // Constructors

  /**
   * Sets the {@code toString()} from the given description.
   */
  CharMatcher(String description) {
    this.description = description;
  }

  /**
   * Constructor for use by subclasses. When subclassing, you may want to override
   * {@code toString()} to provide a useful description.
   */
  protected CharMatcher() {
    description = "UnknownCharMatcher";
  }

  // Abstract methods

  /** Determines a true or false value for the given character. */
  public abstract boolean matches(char c);

  // Non-static factories

  /**
   * Returns a matcher that matches any character not matched by this matcher.
   */
  public CharMatcher negate() {
    final CharMatcher original = this;
    return new CharMatcher(original + ".negate()") {
      @Override public boolean matches(char c) {
        return !original.matches(c);
      }

      @Override public boolean matchesAllOf(CharSequence sequence) {
        return original.matchesNoneOf(sequence);
      }

      @Override public boolean matchesNoneOf(CharSequence sequence) {
        return original.matchesAllOf(sequence);
      }

      @Override public int countIn(CharSequence sequence) {
        return sequence.length() - original.countIn(sequence);
      }

      @Override public CharMatcher negate() {
        return original;
      }
    };
  }

  /**
   * Returns a matcher that matches any character matched by both this matcher and {@code other}.
   */
  public CharMatcher and(CharMatcher other) {
    return new And(this, checkNotNull(other));
  }

  private static class And extends CharMatcher {
    final CharMatcher first;
    final CharMatcher second;

    And(CharMatcher a, CharMatcher b) {
      this(a, b, "CharMatcher.and(" + a + ", " + b + ")");
    }

    And(CharMatcher a, CharMatcher b, String description) {
      super(description);
      first = checkNotNull(a);
      second = checkNotNull(b);
    }

    @Override
    public CharMatcher and(CharMatcher other) {
      return new And(this, other);
    }

    @Override
    public boolean matches(char c) {
      return first.matches(c) && second.matches(c);
    }

    @Override
    CharMatcher withToString(String description) {
      return new And(first, second, description);
    }
  }

  /**
   * Returns a matcher that matches any character matched by either this matcher or {@code other}.
   */
  public CharMatcher or(CharMatcher other) {
    return new Or(this, checkNotNull(other));
  }

  private static class Or extends CharMatcher {
    final CharMatcher first;
    final CharMatcher second;

    Or(CharMatcher a, CharMatcher b, String description) {
      super(description);
      first = checkNotNull(a);
      second = checkNotNull(b);
    }

    Or(CharMatcher a, CharMatcher b) {
      this(a, b, "CharMatcher.or(" + a + ", " + b + ")");
    }

    @Override
    public CharMatcher or(CharMatcher other) {
      return new Or(this, checkNotNull(other));
    }

    @Override
    public boolean matches(char c) {
      return first.matches(c) || second.matches(c);
    }

    @Override
    CharMatcher withToString(String description) {
      return new Or(first, second, description);
    }
  }

  /**
   * Returns a {@code char} matcher functionally equivalent to this one, but which may be faster to
   * query than the original; your mileage may vary. Precomputation takes time and is likely to be
   * worthwhile only if the precomputed matcher is queried many thousands of times.
   *
   * <p>This method has no effect (returns {@code this}) when called in GWT: it's unclear whether a
   * precomputed matcher is faster, but it certainly consumes more memory, which doesn't seem like a
   * worthwhile tradeoff in a browser.
   */
  public CharMatcher precomputed() {
    return Platform.precomputeCharMatcher(this);
  }

  /**
   * Construct an array of all possible chars in the slowest way possible.
   */
  char[] slowGetChars() {
    char[] allChars = new char[65536];
    int size = 0;
    for (int c = Character.MIN_VALUE; c <= Character.MAX_VALUE; c++) {
      if (matches((char) c)) {
        allChars[size++] = (char) c;
      }
    }
    char[] retValue = new char[size];
    System.arraycopy(allChars, 0, retValue, 0, size);
    return retValue;
  }

  /**
   * This is the actual implementation of {@link #precomputed}, but we bounce calls through a method
   * on {@link Platform} so that we can have different behavior in GWT.
   *
   * <p>If the number of matched characters is small enough, we try to build a small hash
   * table to contain all of the characters. Otherwise, we record the characters in eight-kilobyte
   * bit array. In many situations this produces a matcher which is faster to query
   * than the original.
   */
  CharMatcher precomputedInternal() {
    final char[] chars = slowGetChars();
    int totalCharacters = chars.length;
    if (totalCharacters == 0) {
      return NONE;
    } else if (totalCharacters == 1) {
      return is(chars[0]);
    } else if (totalCharacters < SmallCharMatcher.MAX_SIZE) {
      return SmallCharMatcher.from(chars, toString());
    } else if (totalCharacters < MediumCharMatcher.MAX_SIZE) {
      return MediumCharMatcher.from(chars, toString());
    }
    // Otherwise, make the full lookup table.
    final LookupTable table = new LookupTable();
    setBits(table);
    final CharMatcher outer = this;

    return new CharMatcher(outer.toString()) {
      @Override public boolean matches(char c) {
        return table.get(c);
      }

      // TODO(kevinb): make methods like negate() smart?

      @Override public CharMatcher precomputed() {
        return this;
      }
    };
  }

  /**
   * Subclasses should provide a new CharMatcher with the same characteristics as {@code this},
   * but with their {@code toString} method overridden with the new description.
   *
   * <p>This is unsupported by default.
   */
  CharMatcher withToString(String description) {
    throw new UnsupportedOperationException();

  }

  /**
   * For use by implementors; sets the bit corresponding to each character ('\0' to '{@literal
   * \}uFFFF') that matches this matcher in the given bit array, leaving all other bits untouched.
   *
   * <p>The default implementation loops over every possible character value, invoking {@link
   * #matches} for each one.
   */
  void setBits(LookupTable table) {
    char c = Character.MIN_VALUE;
    while (true) {
      if (matches(c)) {
        table.set(c);
      }
      if (c++ == Character.MAX_VALUE) {
        break;
      }
    }
  }

  /**
   * A bit array with one bit per {@code char} value, used by {@link CharMatcher#precomputed}.
   *
   * <p>TODO(kevinb): possibly share a common BitArray class with BloomFilter and others... a
   * simpler java.util.BitSet.
   */
  private static final class LookupTable {
    int[] data = new int[2048];

    void set(char index) {
      data[index >> 5] |= (1 << index);
    }

    boolean get(char index) {
      return (data[index >> 5] & (1 << index)) != 0;
    }
  }

  // Text processing routines

  /**
   * Returns {@code true} if a character sequence contains at least one matching character.
   * Equivalent to {@code !matchesNoneOf(sequence)}.
   *
   * <p>The default implementation iterates over the sequence, invoking {@link #matches} for each
   * character, until this returns {@code true} or the end is reached.
   *
   * @param sequence the character sequence to examine, possibly empty
   * @return {@code true} if this matcher matches at least one character in the sequence
   * @since 8.0
   */
  public boolean matchesAnyOf(CharSequence sequence) {
    return !matchesNoneOf(sequence);
  }

  /**
   * Returns {@code true} if a character sequence contains only matching characters.
   *
   * <p>The default implementation iterates over the sequence, invoking {@link #matches} for each
   * character, until this returns {@code false} or the end is reached.
   *
   * @param sequence the character sequence to examine, possibly empty
   * @return {@code true} if this matcher matches every character in the sequence, including when
   *         the sequence is empty
   */
  public boolean matchesAllOf(CharSequence sequence) {
    for (int i = sequence.length() - 1; i >= 0; i--) {
      if (!matches(sequence.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns {@code true} if a character sequence contains no matching characters. Equivalent to
   * {@code !matchesAnyOf(sequence)}.
   *
   * <p>The default implementation iterates over the sequence, invoking {@link #matches} for each
   * character, until this returns {@code false} or the end is reached.
   *
   * @param sequence the character sequence to examine, possibly empty
   * @return {@code true} if this matcher matches every character in the sequence, including when
   *         the sequence is empty
   */
  public boolean matchesNoneOf(CharSequence sequence) {
    return indexIn(sequence) == -1;
  }

  /**
   * Returns the index of the first matching character in a character sequence, or {@code -1} if no
   * matching character is present.
   *
   * <p>The default implementation iterates over the sequence in forward order calling {@link
   * #matches} for each character.
   *
   * @param sequence the character sequence to examine from the beginning
   * @return an index, or {@code -1} if no character matches
   */
  public int indexIn(CharSequence sequence) {
    int length = sequence.length();
    for (int i = 0; i < length; i++) {
      if (matches(sequence.charAt(i))) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Returns the index of the first matching character in a character sequence, starting from a
   * given position, or {@code -1} if no character matches after that position.
   *
   * <p>The default implementation iterates over the sequence in forward order, beginning at {@code
   * start}, calling {@link #matches} for each character.
   *
   * @param sequence the character sequence to examine
   * @param start the first index to examine; must be nonnegative and no greater than {@code
   *        sequence.length()}
   * @return the index of the first matching character, guaranteed to be no less than {@code start},
   *         or {@code -1} if no character matches
   * @throws IndexOutOfBoundsException if start is negative or greater than {@code
   *         sequence.length()}
   */
  public int indexIn(CharSequence sequence, int start) {
    int length = sequence.length();
    Preconditions.checkPositionIndex(start, length);
    for (int i = start; i < length; i++) {
      if (matches(sequence.charAt(i))) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Returns the index of the last matching character in a character sequence, or {@code -1} if no
   * matching character is present.
   *
   * <p>The default implementation iterates over the sequence in reverse order calling {@link
   * #matches} for each character.
   *
   * @param sequence the character sequence to examine from the end
   * @return an index, or {@code -1} if no character matches
   */
  public int lastIndexIn(CharSequence sequence) {
    for (int i = sequence.length() - 1; i >= 0; i--) {
      if (matches(sequence.charAt(i))) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Returns the number of matching characters found in a character sequence.
   */
  public int countIn(CharSequence sequence) {
    int count = 0;
    for (int i = 0; i < sequence.length(); i++) {
      if (matches(sequence.charAt(i))) {
        count++;
      }
    }
    return count;
  }

  /**
   * Returns a string containing all non-matching characters of a character sequence, in order. For
   * example: <pre>   {@code
   *
   *   CharMatcher.is('a').removeFrom("bazaar")}</pre>
   *
   * ... returns {@code "bzr"}.
   */
  @CheckReturnValue
  public String removeFrom(CharSequence sequence) {
    String string = sequence.toString();
    int pos = indexIn(string);
    if (pos == -1) {
      return string;
    }

    char[] chars = string.toCharArray();
    int spread = 1;

    // This unusual loop comes from extensive benchmarking
    OUT: while (true) {
      pos++;
      while (true) {
        if (pos == chars.length) {
          break OUT;
        }
        if (matches(chars[pos])) {
          break;
        }
        chars[pos - spread] = chars[pos];
        pos++;
      }
      spread++;
    }
    return new String(chars, 0, pos - spread);
  }

  /**
   * Returns a string containing all matching characters of a character sequence, in order. For
   * example: <pre>   {@code
   *
   *   CharMatcher.is('a').retainFrom("bazaar")}</pre>
   *
   * ... returns {@code "aaa"}.
   */
  @CheckReturnValue
  public String retainFrom(CharSequence sequence) {
    return negate().removeFrom(sequence);
  }

  /**
   * Returns a string copy of the input character sequence, with each character that matches this
   * matcher replaced by a given replacement character. For example: <pre>   {@code
   *
   *   CharMatcher.is('a').replaceFrom("radar", 'o')}</pre>
   *
   * ... returns {@code "rodor"}.
   *
   * <p>The default implementation uses {@link #indexIn(CharSequence)} to find the first matching
   * character, then iterates the remainder of the sequence calling {@link #matches(char)} for each
   * character.
   *
   * @param sequence the character sequence to replace matching characters in
   * @param replacement the character to append to the result string in place of each matching
   *        character in {@code sequence}
   * @return the new string
   */
  @CheckReturnValue
  public String replaceFrom(CharSequence sequence, char replacement) {
    String string = sequence.toString();
    int pos = indexIn(string);
    if (pos == -1) {
      return string;
    }
    char[] chars = string.toCharArray();
    chars[pos] = replacement;
    for (int i = pos + 1; i < chars.length; i++) {
      if (matches(chars[i])) {
        chars[i] = replacement;
      }
    }
    return new String(chars);
  }

  /**
   * Returns a string copy of the input character sequence, with each character that matches this
   * matcher replaced by a given replacement sequence. For example: <pre>   {@code
   *
   *   CharMatcher.is('a').replaceFrom("yaha", "oo")}</pre>
   *
   * ... returns {@code "yoohoo"}.
   *
   * <p><b>Note:</b> If the replacement is a fixed string with only one character, you are better
   * off calling {@link #replaceFrom(CharSequence, char)} directly.
   *
   * @param sequence the character sequence to replace matching characters in
   * @param replacement the characters to append to the result string in place of each matching
   *        character in {@code sequence}
   * @return the new string
   */
  @CheckReturnValue
  public String replaceFrom(CharSequence sequence, CharSequence replacement) {
    int replacementLen = replacement.length();
    if (replacementLen == 0) {
      return removeFrom(sequence);
    }
    if (replacementLen == 1) {
      return replaceFrom(sequence, replacement.charAt(0));
    }

    String string = sequence.toString();
    int pos = indexIn(string);
    if (pos == -1) {
      return string;
    }

    int len = string.length();
    StringBuilder buf = new StringBuilder((len * 3 / 2) + 16);

    int oldpos = 0;
    do {
      buf.append(string, oldpos, pos);
      buf.append(replacement);
      oldpos = pos + 1;
      pos = indexIn(string, oldpos);
    } while (pos != -1);

    buf.append(string, oldpos, len);
    return buf.toString();
  }

  /**
   * Returns a substring of the input character sequence that omits all characters this matcher
   * matches from the beginning and from the end of the string. For example: <pre>   {@code
   *
   *   CharMatcher.anyOf("ab").trimFrom("abacatbab")}</pre>
   *
   * ... returns {@code "cat"}.
   *
   * <p>Note that: <pre>   {@code
   *
   *   CharMatcher.inRange('\0', ' ').trimFrom(str)}</pre>
   *
   * ... is equivalent to {@link String#trim()}.
   */
  @CheckReturnValue
  public String trimFrom(CharSequence sequence) {
    int len = sequence.length();
    int first;
    int last;

    for (first = 0; first < len; first++) {
      if (!matches(sequence.charAt(first))) {
        break;
      }
    }
    for (last = len - 1; last > first; last--) {
      if (!matches(sequence.charAt(last))) {
        break;
      }
    }

    return sequence.subSequence(first, last + 1).toString();
  }

  /**
   * Returns a substring of the input character sequence that omits all characters this matcher
   * matches from the beginning of the string. For example: <pre> {@code
   *
   *   CharMatcher.anyOf("ab").trimLeadingFrom("abacatbab")}</pre>
   *
   * ... returns {@code "catbab"}.
   */
  @CheckReturnValue
  public String trimLeadingFrom(CharSequence sequence) {
    int len = sequence.length();
    int first;

    for (first = 0; first < len; first++) {
      if (!matches(sequence.charAt(first))) {
        break;
      }
    }

    return sequence.subSequence(first, len).toString();
  }

  /**
   * Returns a substring of the input character sequence that omits all characters this matcher
   * matches from the end of the string. For example: <pre> {@code
   *
   *   CharMatcher.anyOf("ab").trimTrailingFrom("abacatbab")}</pre>
   *
   * ... returns {@code "abacat"}.
   */
  @CheckReturnValue
  public String trimTrailingFrom(CharSequence sequence) {
    int len = sequence.length();
    int last;

    for (last = len - 1; last >= 0; last--) {
      if (!matches(sequence.charAt(last))) {
        break;
      }
    }

    return sequence.subSequence(0, last + 1).toString();
  }

  /**
   * Returns a string copy of the input character sequence, with each group of consecutive
   * characters that match this matcher replaced by a single replacement character. For example:
   * <pre>   {@code
   *
   *   CharMatcher.anyOf("eko").collapseFrom("bookkeeper", '-')}</pre>
   *
   * ... returns {@code "b-p-r"}.
   *
   * <p>The default implementation uses {@link #indexIn(CharSequence)} to find the first matching
   * character, then iterates the remainder of the sequence calling {@link #matches(char)} for each
   * character.
   *
   * @param sequence the character sequence to replace matching groups of characters in
   * @param replacement the character to append to the result string in place of each group of
   *        matching characters in {@code sequence}
   * @return the new string
   */
  @CheckReturnValue
  public String collapseFrom(CharSequence sequence, char replacement) {
    int first = indexIn(sequence);
    if (first == -1) {
      return sequence.toString();
    }

    // TODO(kevinb): see if this implementation can be made faster
    StringBuilder builder = new StringBuilder(sequence.length())
        .append(sequence.subSequence(0, first))
        .append(replacement);
    boolean in = true;
    for (int i = first + 1; i < sequence.length(); i++) {
      char c = sequence.charAt(i);
      if (matches(c)) {
        if (!in) {
          builder.append(replacement);
          in = true;
        }
      } else {
        builder.append(c);
        in = false;
      }
    }
    return builder.toString();
  }

  /**
   * Collapses groups of matching characters exactly as {@link #collapseFrom} does, except that
   * groups of matching characters at the start or end of the sequence are removed without
   * replacement.
   */
  @CheckReturnValue
  public String trimAndCollapseFrom(CharSequence sequence, char replacement) {
    int first = negate().indexIn(sequence);
    if (first == -1) {
      return ""; // everything matches. nothing's left.
    }
    StringBuilder builder = new StringBuilder(sequence.length());
    boolean inMatchingGroup = false;
    for (int i = first; i < sequence.length(); i++) {
      char c = sequence.charAt(i);
      if (matches(c)) {
        inMatchingGroup = true;
      } else {
        if (inMatchingGroup) {
          builder.append(replacement);
          inMatchingGroup = false;
        }
        builder.append(c);
      }
    }
    return builder.toString();
  }

  // Predicate interface

  /**
   * Returns {@code true} if this matcher matches the given character.
   *
   * @throws NullPointerException if {@code character} is null
   */
  @Override public boolean apply(Character character) {
    return matches(character);
  }

  /**
   * Returns a string representation of this {@code CharMatcher}, such as
   * {@code CharMatcher.or(WHITESPACE, JAVA_DIGIT)}.
   */
  @Override
  public String toString() {
    return description;
  }

  /**
   * Determines whether a character is whitespace according to the latest Unicode standard, as
   * illustrated
   * <a href="http://unicode.org/cldr/utility/list-unicodeset.jsp?a=%5Cp%7Bwhitespace%7D">here</a>.
   * This is not the same definition used by other Java APIs. (See a
   * <a href="http://spreadsheets.google.com/pub?key=pd8dAQyHbdewRsnE5x5GzKQ">comparison of several
   * definitions of "whitespace"</a>.)
   *
   * <p><b>Note:</b> as the Unicode definition evolves, we will modify this constant to keep it up
   * to date.
   */
  public static final CharMatcher WHITESPACE = new CharMatcher("CharMatcher.WHITESPACE") {
    /**
     * A special-case CharMatcher for Unicode whitespace characters that is extremely
     * efficient both in space required and in time to check for matches.
     *
     * Implementation details.
     * It turns out that all current (early 2012) Unicode characters are unique modulo 79:
     * so we can construct a lookup table of exactly 79 entries, and just check the character code
     * mod 79, and see if that character is in the table.
     *
     * There is a 1 at the beginning of the table so that the null character is not listed
     * as whitespace.
     *
     * Other things we tried that did not prove to be beneficial, mostly due to speed concerns:
     *
     *   * Binary search into the sorted list of characters, i.e., what
     *     CharMatcher.anyOf() does</li>
     *   * Perfect hash function into a table of size 26 (using an offset table and a special
     *     Jenkins hash function)</li>
     *   * Perfect-ish hash function that required two lookups into a single table of size 26.</li>
     *   * Using a power-of-2 sized hash table (size 64) with linear probing.</li>
     *
     * --Christopher Swenson, February 2012.
     */

    // Mod-79 lookup table.
    private final char[] table = {1, 0, 160, 0, 0, 0, 0, 0, 0, 9, 10, 11, 12, 13, 0, 0,
        8232, 8233, 0, 0, 0, 0, 0, 8239, 0, 0, 0, 0, 0, 0, 0, 0, 32, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        12288, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 133, 8192, 8193, 8194, 8195, 8196, 8197, 8198, 8199,
        8200, 8201, 8202, 0, 0, 0, 0, 0, 8287, 5760, 0, 0, 6158, 0, 0, 0};

    @Override public boolean matches(char c) {
      return table[c % 79] == c;
    }

    @Override public CharMatcher precomputed() {
      return this;
    }
  };
}
