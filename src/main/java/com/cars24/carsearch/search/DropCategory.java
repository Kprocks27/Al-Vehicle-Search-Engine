package com.cars24.carsearch.search;

/**
 * Why a condition was dropped -- specifically, whose problem it is.
 *
 * <p>Internal ops signal. It never appears in the API response: the shopper is told what was
 * ignored and why in plain words, and does not need our classification of whose fault it was.
 *
 * <p>The split exists because the two kinds have completely different meanings and completely
 * different baselines. Logging them as one bucket would bury a real alarm under routine traffic.
 */
public enum DropCategory {

    /**
     * The shopper asked for something the catalogue cannot express -- a property we do not store,
     * a feature nobody stocks, a condition that needs a comparison we deliberately do not support.
     *
     * <p>Expected, and worth reading: a steady stream of these is a product signal about what
     * people want and we do not offer. Logged at INFO.
     */
    UNSUPPORTED_REQUEST,

    /**
     * The parser broke its own contract on a field it was told about -- a comparison the field
     * cannot take, the wrong number of values, a value that is not the type the field holds.
     *
     * <p>Nothing a shopper types should produce this. Its baseline is zero, so any of it means the
     * prompt or the model has drifted. Logged at WARN.
     */
    PARSER_DRIFT
}
