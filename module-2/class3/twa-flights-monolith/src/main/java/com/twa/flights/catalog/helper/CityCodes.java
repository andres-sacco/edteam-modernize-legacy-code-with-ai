package com.twa.flights.catalog.helper;

import java.text.Collator;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

/**
 * Comparison of the codes of the cities with the same rules of the collation of MySQL (utf8mb4_0900_ai_ci): without
 * distinguishing upper and lower case, accents or the full width letters of the Asian keyboards (ＢＵＥ is BUE), and
 * with the spaces as part of the code. The catalog finds "mia" or
 * "BUÉ" because of that collation, so the search of several cities in one query has to match each city with the code
 * that was requested in the same way.
 */
public final class CityCodes {

    private static final Collator COLLATOR = Collator.getInstance(Locale.ROOT);

    static {
        COLLATOR.setStrength(Collator.PRIMARY);
        // The compatibility decomposition (NFKD) makes ＢＵＥ equal to BUE, like MySQL
        COLLATOR.setDecomposition(Collator.FULL_DECOMPOSITION);
    }

    private CityCodes() {
        // just to avoid create instances
    }

    public static boolean matches(String requestedCode, String cityCode) {
        return requestedCode != null && cityCode != null && COLLATOR.equals(requestedCode, cityCode);
    }

    // The codes to search, in the order of the request and without the empty ones (the catalog returns null for them)
    public static Set<String> distinct(Collection<String> codes) {
        return codes.stream().filter(Objects::nonNull).filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
