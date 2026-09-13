package com.cars24.carsearch.nlq;

/**
 * Turns a shopper's sentence into structure. The only place language is understood.
 *
 * <p>The contract is narrow on purpose, and the narrowness is the design: a parser receives text
 * and returns conditions. It gets no repository, no EntityManager, no connection -- so no
 * implementation, however it is built, can reach the database or emit SQL. When the LLM-backed
 * implementation lands it inherits that constraint for free, because there is nothing in this
 * interface to abuse.
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
