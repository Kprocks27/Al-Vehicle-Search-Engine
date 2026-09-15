package com.cars24.carsearch.nlq;

/**
 * Turns a shopper's sentence into structure. The only place language is understood.
 *
 * <p>The contract is narrow on purpose, and the narrowness is the design: a parser receives text
 * and returns conditions. The interface hands over no repository, no EntityManager and no
 * connection, and no existing implementation takes one -- so none of them can reach the database
 * or emit SQL. Nothing stops a future implementation from being given one, though. The compiler
 * does not enforce this; keeping the database out of parsers is a rule for whoever writes the next.
 *
 * <p>Implementations may return conditions that are wrong, impossible, or invented. Callers must
 * treat the result as untrusted input and put it through validation before use.
 */
public interface QueryParser {

    /**
     * @param query raw shopper text, e.g. "diesel automatic under 80k km"
     * @return the conditions the parser believes the text asks for
     * @throws QueryNotUnderstoodException if the text cannot be interpreted at all
     */
    ParsedQuery parse(String query);
}
