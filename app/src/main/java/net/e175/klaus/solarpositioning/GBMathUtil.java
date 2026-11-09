package net.e175.klaus.solarpositioning;

import android.os.Build;

import java.math.BigDecimal;

public class GBMathUtil {
    /**
     * Math.fma is only available in SDK >= 33.
     * <p>
     * Implementation copied from AOSP at SDK 37, licensed under GPLv2.
     */
    public static double fma(double a, double b, double c) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Math.fma(a, b, c);
        } else {
            /*
             * Infinity and NaN arithmetic is not quite the same with two
             * roundings as opposed to just one so the simple expression
             * "a * b + c" cannot always be used to compute the correct
             * result.  With two roundings, the product can overflow and
             * if the addend is infinite, a spurious NaN can be produced
             * if the infinity from the overflow and the infinite addend
             * have opposite signs.
             */

            // First, screen for and handle non-finite input values whose
            // arithmetic is not supported by BigDecimal.
            if (Double.isNaN(a) || Double.isNaN(b) || Double.isNaN(c)) {
                return Double.NaN;
            } else { // All inputs non-NaN
                boolean infiniteA = Double.isInfinite(a);
                boolean infiniteB = Double.isInfinite(b);
                boolean infiniteC = Double.isInfinite(c);
                double result;

                if (infiniteA || infiniteB || infiniteC) {
                    if (infiniteA && b == 0.0 ||
                        infiniteB && a == 0.0) {
                        return Double.NaN;
                    }
                    // Store product in a double field to cause an
                    // overflow even if non-strictfp evaluation is being
                    // used.
                    double product = a * b;
                    if (Double.isInfinite(product) && !infiniteA && !infiniteB) {
                        // Intermediate overflow; might cause a
                        // spurious NaN if added to infinite c.
                        assert Double.isInfinite(c);
                        return c;
                    } else {
                        result = product + c;
                        assert !Double.isFinite(result);
                        return result;
                    }
                } else { // All inputs finite
                    BigDecimal product = (new BigDecimal(a)).multiply(new BigDecimal(b));
                    if (c == 0.0) { // Positive or negative zero
                        // If the product is an exact zero, use a
                        // floating-point expression to compute the sign
                        // of the zero final result. The product is an
                        // exact zero if and only if at least one of a and
                        // b is zero.
                        if (a == 0.0 || b == 0.0) {
                            return a * b + c;
                        } else {
                            // The sign of a zero addend doesn't matter if
                            // the product is nonzero. The sign of a zero
                            // addend is not factored in the result if the
                            // exact product is nonzero but underflows to
                            // zero; see IEEE-754 2008 section 6.3 "The
                            // sign bit".
                            return product.doubleValue();
                        }
                    } else {
                        return product.add(new BigDecimal(c)).doubleValue();
                    }
                }
            }
        }
    }
}
