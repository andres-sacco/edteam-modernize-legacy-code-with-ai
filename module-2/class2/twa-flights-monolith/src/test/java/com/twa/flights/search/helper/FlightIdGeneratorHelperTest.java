package com.twa.flights.search.helper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.twa.flights.search.dto.request.ClustersAvailabilityRequestDTO;
import com.twa.flights.support.Requests;

// Characterization of the id of a search (it is part of the contract, the client sends it back to paginate)
class FlightIdGeneratorHelperTest {

    // The timestamp is the LocalDateTime.now() of the generation, it changes in each search
    private static final String TIMESTAMP = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?";

    private final FlightIdGeneratorHelper helper = new FlightIdGeneratorHelper();

    @Test
    @DisplayName("[#14] the id has the timestamp and all the parameters of the request")
    void idWithTheRequest() {
        ClustersAvailabilityRequestDTO request = Requests.request("BUE,MIA", "MIA,BUE", "2027-01-14,2027-01-24", 2, 1,
                0);
        request.setCabin("business");

        assertThat(helper.generate(request)).matches("TWA_g=" + TIMESTAMP
                + "_f=BUE,MIA_t=MIA,BUE_d=2027-01-14,2027-01-24_a=2_c=1_i=0_cb=BUSINESS");
    }

    @Test
    @DisplayName("[#14] without children, infants or cabin the id uses 0, 0 and ECONOMY")
    void idWithDefaults() {
        assertThat(helper.generate(Requests.request("BUE", "MIA", "2027-01-14", 1, null, null)))
                .matches("TWA_g=" + TIMESTAMP + "_f=BUE_t=MIA_d=2027-01-14_a=1_c=0_i=0_cb=ECONOMY");
    }
}
