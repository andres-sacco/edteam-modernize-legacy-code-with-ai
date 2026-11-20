package com.twa.flights.dto;

import java.util.Objects;

public class LegDTO {

    private String origin;
    private String destination;
    private String departureDate;
    private String departureTime;
    private String arrivalDate;
    private String arrivalTime;
    private Integer number;
    private String flightDuration;

    public LegDTO() {
    }

    public LegDTO(String origin, String destination, String departureDate, String departureTime, String arrivalDate,
            String arrivalTime, Integer number, String flightDuration) {
        this.origin = origin;
        this.destination = destination;
        this.departureDate = departureDate;
        this.departureTime = departureTime;
        this.arrivalDate = arrivalDate;
        this.arrivalTime = arrivalTime;
        this.number = number;
        this.flightDuration = flightDuration;
    }

    public LegDTO(LegDTO other) {
        this(other.origin, other.destination, other.departureDate, other.departureTime, other.arrivalDate,
                other.arrivalTime, other.number, other.flightDuration);
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getDepartureDate() {
        return departureDate;
    }

    public void setDepartureDate(String departureDate) {
        this.departureDate = departureDate;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(String departureTime) {
        this.departureTime = departureTime;
    }

    public String getArrivalDate() {
        return arrivalDate;
    }

    public void setArrivalDate(String arrivalDate) {
        this.arrivalDate = arrivalDate;
    }

    public String getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(String arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public String getFlightDuration() {
        return flightDuration;
    }

    public void setFlightDuration(String flightDuration) {
        this.flightDuration = flightDuration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        LegDTO that = (LegDTO) o;
        return Objects.equals(origin, that.origin) && Objects.equals(destination, that.destination)
                && Objects.equals(departureDate, that.departureDate)
                && Objects.equals(departureTime, that.departureTime) && Objects.equals(arrivalDate, that.arrivalDate)
                && Objects.equals(arrivalTime, that.arrivalTime) && Objects.equals(number, that.number)
                && Objects.equals(flightDuration, that.flightDuration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(origin, destination, departureDate, departureTime, arrivalDate, arrivalTime, number,
                flightDuration);
    }

    @Override
    public String toString() {
        return "LegDTO{" + "origin='" + origin + '\'' + ", destination='" + destination + '\'' + ", departureDate='"
                + departureDate + '\'' + ", departureTime='" + departureTime + '\'' + ", arrivalDate='" + arrivalDate
                + '\'' + ", arrivalTime='" + arrivalTime + '\'' + ", number=" + number + ", flightDuration='"
                + flightDuration + '\'' + '}';
    }
}
