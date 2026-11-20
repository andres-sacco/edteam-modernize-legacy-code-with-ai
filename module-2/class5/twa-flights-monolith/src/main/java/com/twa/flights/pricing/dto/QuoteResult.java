package com.twa.flights.pricing.dto;

/**
 * Result of the quoting of one request: the markup of each type of passenger, or a result without policy when no
 * commercial policy applies to the request.
 */
public class QuoteResult {

    private String key;
    private boolean withPolicy;
    private QuotePaxResult adults;
    private QuotePaxResult children;
    private QuotePaxResult infants;

    public static QuoteResult withoutPolicy(String key) {
        QuoteResult result = new QuoteResult();
        result.setKey(key);
        result.setWithPolicy(false);
        return result;
    }

    /**
     * The key of the request (the id of the itinerary).
     */
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public boolean isWithPolicy() {
        return withPolicy;
    }

    public void setWithPolicy(boolean withPolicy) {
        this.withPolicy = withPolicy;
    }

    public QuotePaxResult getAdults() {
        return adults;
    }

    public void setAdults(QuotePaxResult adults) {
        this.adults = adults;
    }

    /**
     * Null when the request does not have children.
     */
    public QuotePaxResult getChildren() {
        return children;
    }

    public void setChildren(QuotePaxResult children) {
        this.children = children;
    }

    /**
     * Null when the request does not have infants.
     */
    public QuotePaxResult getInfants() {
        return infants;
    }

    public void setInfants(QuotePaxResult infants) {
        this.infants = infants;
    }
}
