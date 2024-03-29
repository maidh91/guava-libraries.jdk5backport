/*
 * Copyright (C) 2011 The Guava Authors
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

package com.google.common.math;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.math.MathPreconditions.checkNoOverflow;
import static com.google.common.math.MathPreconditions.checkNonNegative;
import static com.google.common.math.MathPreconditions.checkPositive;
import static com.google.common.math.MathPreconditions.checkRoundingUnnecessary;
import static java.lang.Math.abs;
import static java.lang.Math.min;
import static java.math.RoundingMode.HALF_EVEN;
import static java.math.RoundingMode.HALF_UP;

import com.google.common.annotations.Beta;
import com.google.common.annotations.GwtCompatible;
import com.google.common.annotations.VisibleForTesting;

import java.math.RoundingMode;

/**
 * A class for arithmetic on values of type {@code int}. Where possible, methods are defined and
 * named analogously to their {@code BigInteger} counterparts.
 *
 * <p>The implementations of many methods in this class are based on material from Henry S. Warren,
 * Jr.'s <i>Hacker's Delight</i>, (Addison Wesley, 2002).
 *
 * <p>Similar functionality for {@code long} and for {@link BigInteger} can be found in
 * {@link LongMath} and {@link BigIntegerMath} respectively.  For other common operations on
 * {@code int} values, see {@link com.google.common.primitives.Ints}.
 *
 * @author Louis Wasserman
 * @since 11.0
 */
@Beta
@GwtCompatible(emulated = true)
public final class IntMath {
  // NOTE: Whenever both tests are cheap and functional, it's faster to use &, | instead of &&, ||

  /**
   * Returns {@code true} if {@code x} represents a power of two.
   *
   * <p>This differs from {@code Integer.bitCount(x) == 1}, because
   * {@code Integer.bitCount(Integer.MIN_VALUE) == 1}, but {@link Integer#MIN_VALUE} is not a power
   * of two.
   */
  public static boolean isPowerOfTwo(int x) {
    return x > 0 & (x & (x - 1)) == 0;
  }

  /**
   * Returns the base-2 logarithm of {@code x}, rounded according to the specified rounding mode.
   *
   * @throws IllegalArgumentException if {@code x <= 0}
   * @throws ArithmeticException if {@code mode} is {@link RoundingMode#UNNECESSARY} and {@code x}
   *         is not a power of two
   */
  @SuppressWarnings("fallthrough")
  public static int log2(int x, RoundingMode mode) {
    checkPositive("x", x);
    switch (mode) {
      case UNNECESSARY:
        checkRoundingUnnecessary(isPowerOfTwo(x));
        // fall through
      case DOWN:
      case FLOOR:
        return (Integer.SIZE - 1) - Integer.numberOfLeadingZeros(x);

      case UP:
      case CEILING:
        return Integer.SIZE - Integer.numberOfLeadingZeros(x - 1);

      case HALF_DOWN:
      case HALF_UP:
      case HALF_EVEN:
        // Since sqrt(2) is irrational, log2(x) - logFloor cannot be exactly 0.5
        int leadingZeros = Integer.numberOfLeadingZeros(x);
        int cmp = MAX_POWER_OF_SQRT2_UNSIGNED >>> leadingZeros;
          // floor(2^(logFloor + 0.5))
        int logFloor = (Integer.SIZE - 1) - leadingZeros;
        return (x <= cmp) ? logFloor : logFloor + 1;

      default:
        throw new AssertionError();
    }
  }

  /** The biggest half power of two that can fit in an unsigned int. */
  @VisibleForTesting static final int MAX_POWER_OF_SQRT2_UNSIGNED = 0xB504F333;

  private static int log10Floor(int x) {
    /*
     * Based on Hacker's Delight Fig. 11-5, the two-table-lookup, branch-free implementation.
     *
     * The key idea is that based on the number of leading zeros (equivalently, floor(log2(x))),
     * we can narrow the possible floor(log10(x)) values to two.  For example, if floor(log2(x))
     * is 6, then 64 <= x < 128, so floor(log10(x)) is either 1 or 2.
     */
    int y = MAX_LOG10_FOR_LEADING_ZEROS[Integer.numberOfLeadingZeros(x)];
    // y is the higher of the two possible values of floor(log10(x))

    int sgn = (x - POWERS_OF_10[y]) >>> (Integer.SIZE - 1);
    /*
     * sgn is the sign bit of x - 10^y; it is 1 if x < 10^y, and 0 otherwise. If x < 10^y, then we
     * want the lower of the two possible values, or y - 1, otherwise, we want y.
     */
    return y - sgn;
  }

  // MAX_LOG10_FOR_LEADING_ZEROS[i] == floor(log10(2^(Long.SIZE - i)))
  @VisibleForTesting static final byte[] MAX_LOG10_FOR_LEADING_ZEROS = {9, 9, 9, 8, 8, 8,
    7, 7, 7, 6, 6, 6, 6, 5, 5, 5, 4, 4, 4, 3, 3, 3, 3, 2, 2, 2, 1, 1, 1, 0, 0, 0, 0};

  @VisibleForTesting static final int[] POWERS_OF_10 = {1, 10, 100, 1000, 10000,
    100000, 1000000, 10000000, 100000000, 1000000000};

  // HALF_POWERS_OF_10[i] = largest int less than 10^(i + 0.5)
  @VisibleForTesting static final int[] HALF_POWERS_OF_10 =
      {3, 31, 316, 3162, 31622, 316227, 3162277, 31622776, 316227766, Integer.MAX_VALUE};

  private static int sqrtFloor(int x) {
    // There is no loss of precision in converting an int to a double, according to
    // http://java.sun.com/docs/books/jls/third_edition/html/conversions.html#5.1.2
    return (int) Math.sqrt(x);
  }

  /**
   * Returns the result of dividing {@code p} by {@code q}, rounding using the specified
   * {@code RoundingMode}.
   *
   * @throws ArithmeticException if {@code q == 0}, or if {@code mode == UNNECESSARY} and {@code a}
   *         is not an integer multiple of {@code b}
   */
  @SuppressWarnings("fallthrough")
  public static int divide(int p, int q, RoundingMode mode) {
    checkNotNull(mode);
    if (q == 0) {
      throw new ArithmeticException("/ by zero"); // for GWT
    }
    int div = p / q;
    int rem = p - q * div; // equal to p % q

    if (rem == 0) {
      return div;
    }

    /*
     * Normal Java division rounds towards 0, consistently with RoundingMode.DOWN. We just have to
     * deal with the cases where rounding towards 0 is wrong, which typically depends on the sign of
     * p / q.
     *
     * signum is 1 if p and q are both nonnegative or both negative, and -1 otherwise.
     */
    int signum = 1 | ((p ^ q) >> (Integer.SIZE - 1));
    boolean increment;
    switch (mode) {
      case UNNECESSARY:
        checkRoundingUnnecessary(rem == 0);
        // fall through
      case DOWN:
        increment = false;
        break;
      case UP:
        increment = true;
        break;
      case CEILING:
        increment = signum > 0;
        break;
      case FLOOR:
        increment = signum < 0;
        break;
      case HALF_EVEN:
      case HALF_DOWN:
      case HALF_UP:
        int absRem = abs(rem);
        int cmpRemToHalfDivisor = absRem - (abs(q) - absRem);
        // subtracting two nonnegative ints can't overflow
        // cmpRemToHalfDivisor has the same sign as compare(abs(rem), abs(q) / 2).
        if (cmpRemToHalfDivisor == 0) { // exactly on the half mark
          increment = (mode == HALF_UP || (mode == HALF_EVEN & (div & 1) != 0));
        } else {
          increment = cmpRemToHalfDivisor > 0; // closer to the UP value
        }
        break;
      default:
        throw new AssertionError();
    }
    return increment ? div + signum : div;
  }

  /**
   * Returns {@code x mod m}. This differs from {@code x % m} in that it always returns a
   * non-negative result.
   *
   * <p>For example:<pre> {@code
   *
   * mod(7, 4) == 3
   * mod(-7, 4) == 1
   * mod(-1, 4) == 3
   * mod(-8, 4) == 0
   * mod(8, 4) == 0}</pre>
   *
   * @throws ArithmeticException if {@code m <= 0}
   */
  public static int mod(int x, int m) {
    if (m <= 0) {
      throw new ArithmeticException("Modulus " + m + " must be > 0");
    }
    int result = x % m;
    return (result >= 0) ? result : result + m;
  }

  /**
   * Returns the greatest common divisor of {@code a, b}. Returns {@code 0} if
   * {@code a == 0 && b == 0}.
   *
   * @throws IllegalArgumentException if {@code a < 0} or {@code b < 0}
   */
  public static int gcd(int a, int b) {
    /*
     * The reason we require both arguments to be >= 0 is because otherwise, what do you return on
     * gcd(0, Integer.MIN_VALUE)? BigInteger.gcd would return positive 2^31, but positive 2^31
     * isn't an int.
     */
    checkNonNegative("a", a);
    checkNonNegative("b", b);
    if (a == 0) {
      // 0 % b == 0, so b divides a, but the converse doesn't hold.
      // BigInteger.gcd is consistent with this decision.
      return b;
    } else if (b == 0) {
      return a; // similar logic
    }
    /*
     * Uses the binary GCD algorithm; see http://en.wikipedia.org/wiki/Binary_GCD_algorithm.
     * This is >40% faster than the Euclidean algorithm in benchmarks.
     */
    int aTwos = Integer.numberOfTrailingZeros(a);
    a >>= aTwos; // divide out all 2s
    int bTwos = Integer.numberOfTrailingZeros(b);
    b >>= bTwos; // divide out all 2s
    while (a != b) { // both a, b are odd
      // The key to the binary GCD algorithm is as follows:
      // Both a and b are odd.  Assume a > b; then gcd(a - b, b) = gcd(a, b).
      // But in gcd(a - b, b), a - b is even and b is odd, so we can divide out powers of two.

      // We bend over backwards to avoid branching, adapting a technique from
      // http://graphics.stanford.edu/~seander/bithacks.html#IntegerMinOrMax

      int delta = a - b; // can't overflow, since a and b are nonnegative

      int minDeltaOrZero = delta & (delta >> (Integer.SIZE - 1));
      // equivalent to Math.min(delta, 0)

      a = delta - minDeltaOrZero - minDeltaOrZero; // sets a to Math.abs(a - b)
      // a is now nonnegative and even

      b += minDeltaOrZero; // sets b to min(old a, b)
      a >>= Integer.numberOfTrailingZeros(a); // divide out all 2s, since 2 doesn't divide b
    }
    return a << min(aTwos, bTwos);
  }

  /**
   * Returns the sum of {@code a} and {@code b}, provided it does not overflow.
   *
   * @throws ArithmeticException if {@code a + b} overflows in signed {@code int} arithmetic
   */
  public static int checkedAdd(int a, int b) {
    long result = (long) a + b;
    checkNoOverflow(result == (int) result);
    return (int) result;
  }

  /**
   * Returns the difference of {@code a} and {@code b}, provided it does not overflow.
   *
   * @throws ArithmeticException if {@code a - b} overflows in signed {@code int} arithmetic
   */
  public static int checkedSubtract(int a, int b) {
    long result = (long) a - b;
    checkNoOverflow(result == (int) result);
    return (int) result;
  }

  /**
   * Returns the product of {@code a} and {@code b}, provided it does not overflow.
   *
   * @throws ArithmeticException if {@code a * b} overflows in signed {@code int} arithmetic
   */
  public static int checkedMultiply(int a, int b) {
    long result = (long) a * b;
    checkNoOverflow(result == (int) result);
    return (int) result;
  }

  /**
   * Returns the {@code b} to the {@code k}th power, provided it does not overflow.
   *
   * <p>{@link #pow} may be faster, but does not check for overflow.
   *
   * @throws ArithmeticException if {@code b} to the {@code k}th power overflows in signed
   *         {@code int} arithmetic
   */
  public static int checkedPow(int b, int k) {
    checkNonNegative("exponent", k);
    switch (b) {
      case 0:
        return (k == 0) ? 1 : 0;
      case 1:
        return 1;
      case (-1):
        return ((k & 1) == 0) ? 1 : -1;
      case 2:
        checkNoOverflow(k < Integer.SIZE - 1);
        return 1 << k;
      case (-2):
        checkNoOverflow(k < Integer.SIZE);
        return ((k & 1) == 0) ? 1 << k : -1 << k;
    }
    int accum = 1;
    while (true) {
      switch (k) {
        case 0:
          return accum;
        case 1:
          return checkedMultiply(accum, b);
        default:
          if ((k & 1) != 0) {
            accum = checkedMultiply(accum, b);
          }
          k >>= 1;
          if (k > 0) {
            checkNoOverflow(-FLOOR_SQRT_MAX_INT <= b & b <= FLOOR_SQRT_MAX_INT);
            b *= b;
          }
      }
    }
  }

  @VisibleForTesting static final int FLOOR_SQRT_MAX_INT = 46340;

  /**
   * Returns {@code n!}, that is, the product of the first {@code n} positive
   * integers, {@code 1} if {@code n == 0}, or {@link Integer#MAX_VALUE} if the
   * result does not fit in a {@code int}.
   *
   * @throws IllegalArgumentException if {@code n < 0}
   */
  public static int factorial(int n) {
    checkNonNegative("n", n);
    return (n < FACTORIALS.length) ? FACTORIALS[n] : Integer.MAX_VALUE;
  }

  static final int[] FACTORIALS = {
      1,
      1,
      1 * 2,
      1 * 2 * 3,
      1 * 2 * 3 * 4,
      1 * 2 * 3 * 4 * 5,
      1 * 2 * 3 * 4 * 5 * 6,
      1 * 2 * 3 * 4 * 5 * 6 * 7,
      1 * 2 * 3 * 4 * 5 * 6 * 7 * 8,
      1 * 2 * 3 * 4 * 5 * 6 * 7 * 8 * 9,
      1 * 2 * 3 * 4 * 5 * 6 * 7 * 8 * 9 * 10,
      1 * 2 * 3 * 4 * 5 * 6 * 7 * 8 * 9 * 10 * 11,
      1 * 2 * 3 * 4 * 5 * 6 * 7 * 8 * 9 * 10 * 11 * 12};

  // binomial(BIGGEST_BINOMIALS[k], k) fits in an int, but not binomial(BIGGEST_BINOMIALS[k]+1,k).
  @VisibleForTesting static int[] BIGGEST_BINOMIALS = {
    Integer.MAX_VALUE,
    Integer.MAX_VALUE,
    65536,
    2345,
    477,
    193,
    110,
    75,
    58,
    49,
    43,
    39,
    37,
    35,
    34,
    34,
    33
  };

  private IntMath() {}
}
