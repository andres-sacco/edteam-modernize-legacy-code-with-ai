package com.twa.flights.service;

import static com.twa.flights.support.ItineraryTestData.INBOUND_DATE;
import static com.twa.flights.support.ItineraryTestData.OUTBOUND_DATE;
import static com.twa.flights.support.ItineraryTestData.adultsOnly;
import static com.twa.flights.support.ItineraryTestData.ids;
import static com.twa.flights.support.ItineraryTestData.itinerary;
import static com.twa.flights.support.ItineraryTestData.leg;
import static com.twa.flights.support.ItineraryTestData.pax;
import static com.twa.flights.support.ItineraryTestData.roundTrip;
import static com.twa.flights.support.ItineraryTestData.segment;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.Lists;
import com.twa.flights.dto.ItineraryDTO;
import com.twa.flights.dto.PaxPriceDTO;
import com.twa.flights.dto.PriceInfoDTO;
import com.twa.flights.enums.PassengerType;
import com.twa.flights.enums.Provider;
import com.twa.flights.model.CommercialPolicy;
import com.twa.flights.model.CommercialPolicyCarrier;
import com.twa.flights.repository.CommercialPolicyRepository;
import com.twa.flights.support.AbstractIntegrationTest;

/**
 * Characterization of the markup calculation (behaviors #1, #2 and #3 of comportamientos-a-proteger.md). It uses the
 * seed policies of the Flyway migrations (V1.3 and V1.6) and, for the date rules, policies of the carrier ZZ that
 * does not exist in the seed data.
 */
class PricingServiceTest extends AbstractIntegrationTest {

    private static final String TEST_CARRIER = "ZZ";

    @Autowired
    private PricingService pricingService;

    @Autowired
    private CommercialPolicyRepository commercialPolicyRepository;

    private final List<Long> createdPolicies = Lists.newArrayList();

    @AfterEach
    void removeTestPolicies() {
        commercialPolicyRepository.deleteAllById(createdPolicies);
        createdPolicies.clear();
    }

    @Test
    @DisplayName("[#1] markup = subtotal x percentage / 10 (AR 5% of General charges 50%) and total = subtotal + markup")
    void markupDividesThePercentageByTen() {
        // 2 x (339.39 + 25.72) = 730.22, the example observed in the real search
        ItineraryDTO itinerary = roundTrip("ar", "AR", adultsOnly("339.39", "25.72", 2));

        List<ItineraryDTO> priced = price(itinerary);

        PaxPriceDTO adults = priced.get(0).getPriceInfo().getAdults();
        assertThat(adults.getSubtotal()).hasToString("730.22");
        assertThat(adults.getMarkup().getPercentage()).hasToString("5.00");
        assertThat(adults.getMarkup().getAmount()).hasToString("365.1100");
        assertThat(adults.getTotal()).hasToString("1095.3300");
    }

    @Test
    @DisplayName("[#1] the markup of the provider Gamma carriers comes from the General policy (G3 2% charges 20%)")
    void markupOfGammaCarrier() {
        ItineraryDTO itinerary = roundTrip("g3", "G3", adultsOnly("2000.00", "118.55", 1));

        PaxPriceDTO adults = price(itinerary).get(0).getPriceInfo().getAdults();

        assertThat(adults.getMarkup().getPercentage()).hasToString("2.00");
        assertThat(adults.getMarkup().getAmount()).hasToString("423.7100");
        assertThat(adults.getTotal()).hasToString("2542.2600");
    }

    @Test
    @DisplayName("[#1] the markup is calculated for each type of passenger and the missing types stay null")
    void markupForEachPassengerType() {
        PriceInfoDTO priceInfo = new PriceInfoDTO(pax(PassengerType.ADULT, "339.39", "25.72", 2),
                pax(PassengerType.CHILD, "100.00", "20.00", 1), null);
        ItineraryDTO itinerary = roundTrip("ar", "AR", priceInfo);

        PriceInfoDTO priced = price(itinerary).get(0).getPriceInfo();

        assertThat(priced.getChildren().getMarkup().getPercentage()).hasToString("5.00");
        assertThat(priced.getChildren().getMarkup().getAmount()).hasToString("60.0000");
        assertThat(priced.getChildren().getTotal()).hasToString("180.0000");
        assertThat(priced.getInfants()).isNull();
        // The base, the tax and the subtotal of the provider do not change
        assertThat(priced.getChildren().getSubtotal()).hasToString("120.00");
        assertThat(priced.getChildren().getBase()).hasToString("100.00");
        assertThat(priced.getChildren().getTax()).hasToString("20.00");
    }

    @Test
    @DisplayName("[#1] a policy of 0% keeps the itinerary with a markup of zero (AF in General)")
    void zeroPercentageKeepsTheItinerary() {
        ItineraryDTO itinerary = roundTrip("af", "AF", adultsOnly("339.39", "25.72", 2));

        List<ItineraryDTO> priced = price(itinerary);

        assertThat(priced).hasSize(1);
        PaxPriceDTO adults = priced.get(0).getPriceInfo().getAdults();
        assertThat(adults.getMarkup().getPercentage()).hasToString("0.00");
        assertThat(adults.getMarkup().getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(adults.getTotal()).isEqualByComparingTo("730.22");
    }

    @Test
    @DisplayName("[#1] a policy of 100% multiplies the subtotal by 11")
    void oneHundredPercentMultipliesByEleven() {
        createPolicy("Test 100%", 1, true, "2024-01-01T00:00", "2030-01-01T00:00", "100");
        ItineraryDTO itinerary = roundTrip("zz", TEST_CARRIER, adultsOnly("100.00", "0.00", 1));

        PaxPriceDTO adults = price(itinerary).get(0).getPriceInfo().getAdults();

        assertThat(adults.getMarkup().getAmount()).isEqualByComparingTo("1000.00");
        assertThat(adults.getTotal()).isEqualByComparingTo("1100.00");
    }

    @Test
    @DisplayName("[#2] with several policies the highest priority wins (General 10 beats Special Carrier 5 and Ciber Week 1)")
    void highestPriorityWinsWithSeedPolicies() {
        // AR: General 5% vs Special Carrier 10%; AA: General 2% vs Special Carrier 1%; BA: General 3% vs Ciber Week 1%
        List<ItineraryDTO> priced = price(roundTrip("ar", "AR", adultsOnly("100.00", "0.00", 1)),
                roundTrip("aa", "AA", adultsOnly("100.00", "0.00", 1)),
                roundTrip("ba", "BA", adultsOnly("100.00", "0.00", 1)),
                roundTrip("lh", "LH", adultsOnly("100.00", "0.00", 1)));

        assertThat(priced).extracting(itinerary -> itinerary.getPriceInfo().getAdults().getMarkup().getPercentage()
                .toPlainString()).containsExactly("5.00", "2.00", "3.00", "8.00");
    }

    @Test
    @DisplayName("[#2] the priority is a number: the highest one wins even if it was created before")
    void highestPriorityWinsWithNewPolicies() {
        createPolicy("Low", 3, true, "2024-01-01T00:00", "2030-01-01T00:00", "1");
        createPolicy("High", 7, true, "2024-01-01T00:00", "2030-01-01T00:00", "9");

        ItineraryDTO priced = price(roundTrip("zz", TEST_CARRIER, adultsOnly("100.00", "0.00", 1))).get(0);

        assertThat(priced.getPriceInfo().getAdults().getMarkup().getPercentage()).hasToString("9.00");
    }

    @Test
    @DisplayName("[#2] an inactive policy is ignored even with a higher priority")
    void inactivePolicyIsIgnored() {
        createPolicy("Inactive", 50, false, "2024-01-01T00:00", "2030-01-01T00:00", "9");
        createPolicy("Active", 1, true, "2024-01-01T00:00", "2030-01-01T00:00", "1");

        ItineraryDTO priced = price(roundTrip("zz", TEST_CARRIER, adultsOnly("100.00", "0.00", 1))).get(0);

        assertThat(priced.getPriceInfo().getAdults().getMarkup().getPercentage()).hasToString("1.00");
    }

    @Test
    @DisplayName("[#2] the start date is compared with the departure of the first leg (inclusive)")
    void startDateIsInclusiveWithTheFirstDeparture() {
        // The itinerary departs at 2027-01-14 08:00
        createPolicy("Starts at the departure", 1, true, OUTBOUND_DATE + "T08:00", "2030-01-01T00:00", "1");
        assertThat(price(roundTrip("zz", TEST_CARRIER, adultsOnly("100.00", "0.00", 1)))).hasSize(1);

        removeTestPolicies();
        createPolicy("Starts after the departure", 1, true, OUTBOUND_DATE + "T08:01", "2030-01-01T00:00", "1");
        assertThat(price(roundTrip("zz", TEST_CARRIER, adultsOnly("100.00", "0.00", 1)))).isEmpty();
    }

    @Test
    @DisplayName("[#2] the expire date is compared with the arrival of the FIRST leg of the last segment (inclusive)")
    void expireDateUsesTheFirstLegOfTheLastSegment() {
        createPolicy("Expires in the middle of the return", 1, true, "2024-01-01T00:00", INBOUND_DATE + "T12:00", "1");

        // The first leg of the return arrives at 12:00 and the last one at 18:00: the policy is applied
        ItineraryDTO withConnection = itinerary("connection", Provider.ALPHA, TEST_CARRIER,
                adultsOnly("100.00", "0.00", 1),
                segment("10:00", leg("BUE", "MIA", OUTBOUND_DATE + "T08:00", OUTBOUND_DATE + "T16:00", 100)),
                segment("10:00", leg("MIA", "LIM", INBOUND_DATE + "T06:00", INBOUND_DATE + "T12:00", 200),
                        leg("LIM", "BUE", INBOUND_DATE + "T13:00", INBOUND_DATE + "T18:00", 201)));
        // The first leg of the return arrives one minute after the expiration: the itinerary is discarded
        ItineraryDTO lateArrival = itinerary("late", Provider.ALPHA, TEST_CARRIER, adultsOnly("100.00", "0.00", 1),
                segment("10:00", leg("BUE", "MIA", OUTBOUND_DATE + "T08:00", OUTBOUND_DATE + "T16:00", 100)),
                segment("10:00", leg("MIA", "BUE", INBOUND_DATE + "T06:00", INBOUND_DATE + "T12:01", 200)));

        assertThat(ids(price(withConnection, lateArrival))).containsExactly("connection");
    }

    @Test
    @DisplayName("[#3] an itinerary without an applicable policy is discarded silently")
    void itineraryWithoutPolicyIsDiscarded() {
        // LA and FY are carriers of the providers without a commercial policy
        List<ItineraryDTO> priced = price(roundTrip("ar", "AR", adultsOnly("100.00", "0.00", 1)),
                roundTrip("la", "LA", adultsOnly("100.00", "0.00", 1)),
                roundTrip("fy", "FY", adultsOnly("100.00", "0.00", 1)));

        assertThat(ids(priced)).containsExactly("ar");
    }

    @Test
    @DisplayName("[#3] the policies of other dates do not apply: after 2030-01-01 there is no policy for AR")
    void itineraryOutsideTheSeedPoliciesIsDiscarded() {
        ItineraryDTO itinerary = itinerary("2030", Provider.ALPHA, "AR", adultsOnly("100.00", "0.00", 1),
                segment("08:00", leg("BUE", "MIA", "2030-02-01T08:00", "2030-02-01T16:00", 100)));

        assertThat(price(itinerary)).isEmpty();
    }

    // private function(s)

    private List<ItineraryDTO> price(ItineraryDTO... itineraries) {
        return pricingService.priceItineraries(Lists.newArrayList(itineraries));
    }

    private void createPolicy(String name, int priority, boolean active, String startDate, String expireDate,
            String percentage) {
        CommercialPolicyCarrier carrier = new CommercialPolicyCarrier();
        carrier.setCarrier(TEST_CARRIER);
        carrier.setPercentage(new BigDecimal(percentage));

        CommercialPolicy policy = new CommercialPolicy();
        policy.setName(name);
        policy.setPriority(priority);
        policy.setActive(active);
        policy.setStartDate(LocalDateTime.parse(startDate));
        policy.setExpireDate(LocalDateTime.parse(expireDate));
        policy.setValidatingCarriers(Lists.newArrayList(carrier));

        createdPolicies.add(commercialPolicyRepository.saveAndFlush(policy).getId());
    }
}
