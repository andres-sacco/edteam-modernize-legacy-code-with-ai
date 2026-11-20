package com.twa.flights.specification;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;

import com.google.common.collect.Lists;
import com.twa.flights.pricing.model.CommercialPolicy;
import com.twa.flights.pricing.model.CommercialPolicyCarrier;

public class CommercialPolicyCarrierSpecification implements Specification<CommercialPolicy> {

    private static final long serialVersionUID = 7724219200247668775L;

    private static final String ACTIVE = "active";
    private static final String START_DATE = "startDate";
    private static final String EXPIRE_DATE = "expireDate";
    private static final String VALIDATING_CARRIERS = "validatingCarriers";
    private static final String CARRIER = "carrier";
    private static final String PRIORITY = "priority";

    private final String carrier;
    private final LocalDateTime departure;
    private final LocalDateTime arrival;

    public CommercialPolicyCarrierSpecification(String carrier, LocalDateTime departure, LocalDateTime arrival) {
        this.carrier = carrier;
        this.departure = departure;
        this.arrival = arrival;
    }

    private Predicate[] convertPredicatesToArray(List<Predicate> restrictions) {
        return restrictions.toArray(new Predicate[restrictions.size()]);
    }

    @Override
    public Predicate toPredicate(Root<CommercialPolicy> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
        List<Predicate> restrictions = Lists.newArrayList();

        restrictions.add(builder.isTrue(root.get(CommercialPolicyCarrierSpecification.ACTIVE)));

        restrictions.add(builder.lessThanOrEqualTo(root.get(CommercialPolicyCarrierSpecification.START_DATE),
                departure));
        restrictions.add(builder.greaterThanOrEqualTo(root.get(CommercialPolicyCarrierSpecification.EXPIRE_DATE),
                arrival));

        Join<CommercialPolicy, CommercialPolicyCarrier> carriers = root
                .join(CommercialPolicyCarrierSpecification.VALIDATING_CARRIERS);
        restrictions
                .add(builder.equal(carriers.get(CommercialPolicyCarrierSpecification.CARRIER), carrier));

        query.orderBy(builder.desc(root.get(CommercialPolicyCarrierSpecification.PRIORITY)));

        return builder.and(convertPredicatesToArray(restrictions));
    }
}
