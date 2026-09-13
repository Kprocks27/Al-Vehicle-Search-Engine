package com.cars24.carsearch.vocabulary;

/**
 * The complete set of comparisons the parser is allowed to emit.
 *
 * <p>Four, and only four. Every comparison here has an obvious SQL translation and an obvious
 * natural-language trigger, which keeps the parser's job small and the query builder's job
 * total: it can switch over this enum exhaustively with no default branch.
 *
 * <p>Not-equals and one-of are deliberately out of scope. They are the two that tempt a model
 * into building set logic ("not diesel, or petrol but only if automatic"), and supporting them
 * would mean the query builder has to reason about negation and nesting rather than ANDing a
 * flat list of conditions.
 *
 * <p>Bounds are inclusive. A buyer asking for cars under 15 lakh expects to be shown the car
 * priced at exactly 15,00,000 -- listing prices cluster on round numbers, so an exclusive bound
 * silently hides the most obvious matches. OVER is inclusive for the same reason, which is also
 * what lets "high safety rating" resolve to OVER 4 and mean "4 stars and up".
 */
public enum Comparison {

    /** field &lt;= value */
    UNDER(1),

    /** field &gt;= value */
    OVER(1),

    /** field = value */
    EQUALS(1),

    /** lower &lt;= field &lt;= upper */
    BETWEEN(2);

    private final int arity;

    Comparison(int arity) {
        this.arity = arity;
    }

    /** How many values this comparison needs. Checked at the validation boundary. */
    public int arity() {
        return arity;
    }

    /** True if this comparison needs an ordering on the field's type. */
    public boolean requiresOrdering() {
        return this != EQUALS;
    }
}
