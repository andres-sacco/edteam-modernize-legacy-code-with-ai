#!/usr/bin/env python3
"""Generate the OpenAPI contracts that Microcks uses as mocks.

Each provider has its own format of response. The contract of the catalog service (twa-flights-catalog) is generated
from the seed data of the catalog (the Flyway migrations of the monolith): its examples are the JSON that the service
returns for each city, and the tests of the service and of the client of the monolith check that JSON. Run it from the
root of the project:
    python3 microcks/generate-contracts.py
"""
import json
import re
from pathlib import Path

OUTPUT = Path(__file__).parent
MIGRATIONS = OUTPUT.parent / "twa-flights-monolith" / "src" / "main" / "resources" / "db" / "migration"
CATALOG_MIGRATIONS = ["V1.1__insert_catalog_rows.sql", "V1.5__insert_more_cities.sql"]
ITINERARIES_BY_EXAMPLE = 5
ITINERARIES_WITH_STOP = 2  # the last ones of each example
CABINS = ["economy", "premium-economy", "business"]

# Base fare range by cabin and passenger type (adult, child, infant)
FARES = {
    "economy": {"adult": (100, 899), "child": (80, 699), "infant": (20, 199)},
    "premium-economy": {"adult": (900, 1799), "child": (700, 1399), "infant": (200, 399)},
    "business": {"adult": (2000, 4999), "child": (1500, 3999), "infant": (400, 899)},
}
TAX = (20, 249)

# Baggage by cabin: (carry-on pieces, carry-on kg, checked pieces, checked kg)
BAGGAGE = {
    "economy": ("1", "8", "{{ randomValue(0,1) }}", "23"),
    "premium-economy": ("1", "10", "1", "23"),
    "business": ("1", "12", "2", "32"),
}


def param(name):
    return "{{ request.params[%s] }}" % name


def time(from_hour, to_hour):
    hours = ",".join("%02d" % hour for hour in range(from_hour, to_hour + 1))
    return "{{ randomValue(%s) }}:{{ randomValue(00,15,30,45) }}" % hours


def carrier(index):
    return "{{ carrier%d }}" % index


def flight_number():
    return "{{ randomInt(100,9999) }}"


def price(from_value, to_value):
    return "{{ randomInt(%d,%d) }}.{{ randomInt(10,99) }}" % (from_value, to_value)


def segments(round_trip, with_stop):
    """Abstract segments: list of legs (origin, destination, date, departure time, arrival time)."""
    def segment(origin, destination, date):
        if not with_stop:
            return [(origin, destination, date, time(0, 13), time(15, 23))]
        return [(origin, "{{ hub }}", date, time(0, 7), time(8, 10)),
                ("{{ hub }}", destination, date, time(11, 13), time(17, 23))]

    result = [segment(param("origin"), param("destination"), param("departureDate"))]
    if round_trip:
        result.append(segment(param("destination"), param("origin"), param("returnDate")))
    return result


def dispatcher(carriers, hubs):
    return ("def request = mockRequest.getRequest()\n"
            "def origin = request.getParameter(\"origin\")\n"
            "def destination = request.getParameter(\"destination\")\n"
            "// City used as connection, it can not be the origin or the destination\n"
            "def hubs = [%s].findAll { it != origin && it != destination }\n"
            "def random = new Random()\n"
            "requestContext.hub = hubs[random.nextInt(hubs.size())]\n"
            "// The same carrier is used in the itinerary and in the number of each flight\n"
            "def carriers = [%s]\n"
            "(0..%d).each { requestContext.put(\"carrier\" + it, carriers[random.nextInt(carriers.size())]) }\n"
            "def cabin = (request.getParameter(\"cabin\") ?: \"ECONOMY\").toLowerCase().replace('_', '-')\n"
            "if (!(cabin in ['economy', 'premium-economy', 'business'])) {\n"
            "    cabin = 'economy'\n"
            "}\n"
            "def trip = request.getParameter(\"returnDate\") ? \"round-trip\" : \"one-way\"\n"
            "return trip + \"-\" + cabin\n") % (", ".join("'%s'" % hub for hub in hubs),
                                                ", ".join("'%s'" % code for code in carriers),
                                                ITINERARIES_BY_EXAMPLE - 1)


# ---------------------------------------------------------------- Alpha: camelCase, nested, ISO date times
def alpha_itinerary(index, round_trip, cabin, with_stop):
    carry_pieces, carry_kg, checked_pieces, checked_kg = BAGGAGE[cabin]
    fares = FARES[cabin]
    return {
        "offerId": "{{ uuid() }}",
        "airline": {"code": carrier(index)},
        "cabinClass": {"economy": "Y", "premium-economy": "W", "business": "C"}[cabin],
        "journeys": [{"flights": [{
            "from": origin, "to": destination,
            "departure": "%sT%s" % (date, departure), "arrival": "%sT%s" % (date, arrival),
            "flightNumber": carrier(index) + flight_number()} for origin, destination, date, departure, arrival in legs]}
            for legs in segments(round_trip, with_stop)],
        "pricing": {"currency": "USD", "passengers": [
            {"type": code, "base": price(*fares[kind]), "taxes": price(*TAX)}
            for code, kind in (("ADT", "adult"), ("CHD", "child"), ("INF", "infant"))]},
        "luggage": {"cabin": {"pieces": carry_pieces, "maxWeightKg": carry_kg},
                    "hold": {"pieces": checked_pieces, "maxWeightKg": checked_kg}},
    }


def alpha_response(round_trip, cabin):
    return {"results": [alpha_itinerary(index, round_trip, cabin, index >= ITINERARIES_BY_EXAMPLE - ITINERARIES_WITH_STOP)
                        for index in range(ITINERARIES_BY_EXAMPLE)]}


ALPHA_SCHEMA = {"type": "object", "properties": {"results": {"type": "array", "items": {"type": "object", "properties": {
    "offerId": {"type": "string"},
    "airline": {"type": "object", "properties": {"code": {"type": "string"}}},
    "cabinClass": {"type": "string", "enum": ["Y", "W", "C"]},
    "journeys": {"type": "array", "items": {"type": "object", "properties": {"flights": {"type": "array", "items": {
        "type": "object", "properties": {"from": {"type": "string"}, "to": {"type": "string"},
                                         "departure": {"type": "string"}, "arrival": {"type": "string"},
                                         "flightNumber": {"type": "string"}}}}}}},
    "pricing": {"type": "object", "properties": {"currency": {"type": "string"}, "passengers": {"type": "array", "items": {
        "type": "object", "properties": {"type": {"type": "string", "enum": ["ADT", "CHD", "INF"]},
                                         "base": {"type": "string"}, "taxes": {"type": "string"}}}}}},
    "luggage": {"type": "object", "properties": {
        "cabin": {"type": "object", "properties": {"pieces": {"type": "string"}, "maxWeightKg": {"type": "string"}}},
        "hold": {"type": "object", "properties": {"pieces": {"type": "string"}, "maxWeightKg": {"type": "string"}}}}},
}}}}}


# ---------------------------------------------------------------- Beta: snake_case, envelope, prices in cents
def cents(from_value, to_value):
    return "{{ randomInt(%d,%d) }}" % (from_value * 100, to_value * 100)


def beta_itinerary(index, round_trip, cabin, with_stop):
    carry_pieces, carry_kg, checked_pieces, checked_kg = BAGGAGE[cabin]
    fares = FARES[cabin]
    return {
        "option_id": "{{ uuid() }}",
        "marketing_carrier": carrier(index),
        "cabin": cabin.replace("-", "_"),
        "bounds": [{"direction": direction, "legs": [{
            "dep_airport": origin, "arr_airport": destination,
            "dep_date": date, "dep_time": departure, "arr_date": date, "arr_time": arrival,
            "flight_no": carrier(index) + flight_number()} for origin, destination, date, departure, arrival in legs]}
            for direction, legs in zip(("OUTBOUND", "INBOUND"), segments(round_trip, with_stop))],
        "fare_breakdown": {kind: {"base_fare_cents": cents(*fares[kind]), "tax_cents": cents(*TAX)}
                           for kind in ("adult", "child", "infant")},
        "baggage_allowance": {"hand_pieces": carry_pieces, "hand_kg": carry_kg,
                              "checked_pieces": checked_pieces, "checked_kg": checked_kg},
    }


def beta_response(round_trip, cabin):
    return {"status": "OK", "data": {"flight_options": [
        beta_itinerary(index, round_trip, cabin, index >= ITINERARIES_BY_EXAMPLE - ITINERARIES_WITH_STOP)
        for index in range(ITINERARIES_BY_EXAMPLE)]}}


BETA_FARE = {"type": "object", "properties": {"base_fare_cents": {"type": "string"}, "tax_cents": {"type": "string"}}}
BETA_SCHEMA = {"type": "object", "properties": {"status": {"type": "string"}, "data": {"type": "object", "properties": {
    "flight_options": {"type": "array", "items": {"type": "object", "properties": {
        "option_id": {"type": "string"}, "marketing_carrier": {"type": "string"},
        "cabin": {"type": "string", "enum": ["economy", "premium_economy", "business"]},
        "bounds": {"type": "array", "items": {"type": "object", "properties": {
            "direction": {"type": "string", "enum": ["OUTBOUND", "INBOUND"]},
            "legs": {"type": "array", "items": {"type": "object", "properties": {
                name: {"type": "string"} for name in ("dep_airport", "arr_airport", "dep_date", "dep_time",
                                                      "arr_date", "arr_time", "flight_no")}}}}}},
        "fare_breakdown": {"type": "object", "properties": {"adult": BETA_FARE, "child": BETA_FARE, "infant": BETA_FARE}},
        "baggage_allowance": {"type": "object", "properties": {
            name: {"type": "string"} for name in ("hand_pieces", "hand_kg", "checked_pieces", "checked_kg")}},
    }}}}}}}


# ---------------------------------------------------------------- Gamma: PascalCase, flat list of flights
def gamma_bag(pieces, kg):
    if pieces.startswith("{{"):  # random number of pieces
        return "{{ randomValue(0PC,1PC%sKG) }}" % kg
    return "%sPC%sKG" % (pieces, kg)


def gamma_itinerary(index, round_trip, cabin, with_stop):
    carry_pieces, carry_kg, checked_pieces, checked_kg = BAGGAGE[cabin]
    fares = FARES[cabin]
    flights = []
    for segment_index, legs in enumerate(segments(round_trip, with_stop)):
        for sequence, (origin, destination, date, departure, arrival) in enumerate(legs, start=1):
            flights.append({"SegmentIndex": str(segment_index), "Sequence": str(sequence),
                            "Origin": origin, "Destination": destination,
                            "DepartureDateTime": "%sT%s:00" % (date, departure),
                            "ArrivalDateTime": "%sT%s:00" % (date, arrival),
                            "OperatingCarrier": carrier(index), "FlightNumber": flight_number()})
    return {
        "Key": "{{ uuid() }}",
        "ValidatingCarrier": carrier(index),
        "CabinType": {"economy": "ECO", "premium-economy": "PEY", "business": "BUS"}[cabin],
        "Flights": flights,
        "Fares": [{"PaxType": code, "BaseAmount": price(*fares[kind]), "TaxAmount": price(*TAX)}
                  for code, kind in (("ADULT", "adult"), ("CHILD", "child"), ("INFANT", "infant"))],
        "Bags": {"CarryOn": gamma_bag(carry_pieces, carry_kg), "Checked": gamma_bag(checked_pieces, checked_kg)},
    }


def gamma_response(round_trip, cabin):
    return {"Itineraries": [gamma_itinerary(index, round_trip, cabin, index >= ITINERARIES_BY_EXAMPLE - ITINERARIES_WITH_STOP)
                            for index in range(ITINERARIES_BY_EXAMPLE)]}


GAMMA_SCHEMA = {"type": "object", "properties": {"Itineraries": {"type": "array", "items": {"type": "object", "properties": {
    "Key": {"type": "string"}, "ValidatingCarrier": {"type": "string"},
    "CabinType": {"type": "string", "enum": ["ECO", "PEY", "BUS"]},
    "Flights": {"type": "array", "items": {"type": "object", "properties": {
        name: {"type": "string"} for name in ("SegmentIndex", "Sequence", "Origin", "Destination", "DepartureDateTime",
                                              "ArrivalDateTime", "OperatingCarrier", "FlightNumber")}}},
    "Fares": {"type": "array", "items": {"type": "object", "properties": {
        "PaxType": {"type": "string", "enum": ["ADULT", "CHILD", "INFANT"]},
        "BaseAmount": {"type": "string"}, "TaxAmount": {"type": "string"}}}},
    "Bags": {"type": "object", "properties": {"CarryOn": {"type": "string"}, "Checked": {"type": "string"}},
             "description": "Pieces and weight, e.g. 1PC23KG. 0PC means no baggage"},
}}}}}


# ---------------------------------------------------------------- Contract
def query(name, description, required, kind="string", extra=None):
    return {"name": name, "in": "query", "required": required, "description": description,
            "schema": dict({"type": kind}, **(extra or {}))}


def contract(name, title, carriers, hubs, response, schema):
    examples = {}
    for round_trip in (True, False):
        for cabin in CABINS:
            examples["%s-%s" % ("round-trip" if round_trip else "one-way", cabin)] = {"value": response(round_trip, cabin)}

    return {
        "openapi": "3.0.3",
        "info": {"title": name, "version": "1.0", "description": "Mock of the external flights provider %s" % title},
        "paths": {"/itineraries": {"get": {
            "operationId": "availability", "summary": "Search the availability of flights",
            "x-microcks-operation": {"dispatcher": "SCRIPT", "dispatcherRules": dispatcher(carriers, hubs)},
            "parameters": [
                query("origin", "IATA code of the origin city", True),
                query("destination", "IATA code of the destination city", True),
                query("departureDate", "Departure date (yyyy-MM-dd)", True, extra={"format": "date"}),
                query("returnDate", "Return date (yyyy-MM-dd)", False, extra={"format": "date"}),
                query("adults", "Adults quantity", True, "integer"),
                query("children", "Children quantity", False, "integer"),
                query("infants", "Infants quantity", False, "integer"),
                query("cabin", "Cabin class, by default ECONOMY", False,
                      extra={"enum": ["ECONOMY", "PREMIUM_ECONOMY", "BUSINESS"]}),
                query("amount", "Maximum number of itineraries to return", False, "integer")],
            "responses": {"200": {"description": "Available itineraries", "content": {"application/json": {
                "schema": schema, "examples": examples}}}}}}},
    }


PROVIDERS = [
    ("provider-alpha", "Alpha", ["AR", "AA", "DL"], ["PTY", "LIM", "BOG", "MIA", "SAO"], alpha_response, ALPHA_SCHEMA),
    ("provider-beta", "Beta", ["AR", "AA", "DL", "IB", "FY", "LA", "AF", "KL", "JJ"], ["PTY", "LIM", "SCL", "MEX", "SAO"],
     beta_response, BETA_SCHEMA),
    ("provider-gamma", "Gamma", ["CM", "AV", "G3", "JA", "H2", "LA"], ["BOG", "PTY", "SCL", "LIM", "SAO"],
     gamma_response, GAMMA_SCHEMA),
]

# ---------------------------------------------------------------------------------------------------------------------
# Catalog service (twa-flights-catalog)
# ---------------------------------------------------------------------------------------------------------------------

def catalog_cities():
    """The cities of the seed data with their country and continent, in the JSON of the service, ordered by code."""
    text = r"'((?:[^']|'')*)'"

    def unquote(value):
        return value.replace("''", "'")

    continents, countries, cities, country_ids_by_code = {}, {}, [], {}
    for migration in CATALOG_MIGRATIONS:
        for line in (MIGRATIONS / migration).read_text().splitlines():
            if line.startswith("INSERT INTO `continent`"):
                for continent_id, code, name in re.findall(r"\((\d+),\s*%s,\s*%s\)" % (text, text), line):
                    continents[continent_id] = {"code": unquote(code), "name": unquote(name)}
            elif line.startswith("INSERT INTO `country`"):
                for country_id, code, name, continent_id in re.findall(
                        r"\((\d+),\s*%s,\s*%s,\s*(\d+)\)" % (text, text), line):
                    countries[country_id] = (unquote(code), unquote(name), continent_id)
                    country_ids_by_code[unquote(code)] = country_id
            elif line.startswith("INSERT INTO `city`") and "SELECT" in line:
                # INSERT ... SELECT <id>, '<code>', '<name>', '<zone>', `id` FROM `country` WHERE `code` = '<country>'
                quoted = [unquote(value) for value in re.findall(text, line.split("SELECT", 1)[1])]
                cities.append((quoted[0], quoted[1], quoted[2], country_ids_by_code[quoted[3]]))
            elif line.startswith("INSERT INTO `city`"):
                # INSERT ... VALUES ('<id>', '<code>', '<name>', '<zone>', '<country id>')
                quoted = [unquote(value) for value in re.findall(text, line.split("VALUES", 1)[1])]
                cities.append((quoted[1], quoted[2], quoted[3], quoted[4]))

    result = []
    for code, name, zone, country_id in cities:
        country_code, country_name, continent_id = countries[country_id]
        result.append({"code": code, "name": name, "timeZone": zone, "country": {
            "code": country_code, "name": country_name, "continent": continents[continent_id]}})
    return sorted(result, key=lambda city: city["code"])


CITY_SCHEMA = {"type": "object", "required": ["code", "name", "timeZone", "country"], "properties": {
    "code": {"type": "string"}, "name": {"type": "string"},
    "timeZone": {"type": "string", "description": "IANA id of the time zone, never an offset"},
    "country": {"type": "object", "required": ["code", "name", "continent"], "properties": {
        "code": {"type": "string"}, "name": {"type": "string"},
        "continent": {"type": "object", "required": ["code", "name"], "properties": {
            "code": {"type": "string"}, "name": {"type": "string"}}}}}}}
ERROR_SCHEMA = {"type": "object", "properties": {
    "code": {"type": "integer"}, "shortDescription": {"type": "string"},
    "reasons": {"type": "array", "items": {"type": "string"}}}}
UNKNOWN = "unknown"


def fallback(dispatcher, rules):
    return {"dispatcher": "FALLBACK", "dispatcherRules": json.dumps(
        {"dispatcher": dispatcher, "dispatcherRules": rules, "fallback": UNKNOWN})}


def codes_dispatcher(cities):
    """Microcks does not compute the criteria of a query parameter inside FALLBACK: the script chooses the example."""
    return "\n".join([
        "def code = (mockRequest.getRequest().getParameter(\"codes\") ?: \"\").toUpperCase()",
        "def known = [%s]" % ", ".join("'%s'" % city["code"] for city in cities),
        "return known.contains(code) ? code : \"%s\"" % UNKNOWN,
        ""])


def catalog_contract(cities):
    def json_content(schema, examples):
        return {"application/json": {"schema": schema, "examples": examples}}

    code_examples = {city["code"]: {"value": city["code"]} for city in cities}
    code_examples[UNKNOWN] = {"value": "XXX"}
    not_found = {"code": 40401, "shortDescription": "The city does not exist", "reasons": ["XXX"]}

    return {
        "openapi": "3.0.3",
        "info": {"title": "twa-flights-catalog", "version": "1.0",
                 "description": "Catalog of cities extracted from the monolith. Generated from the seed data of the "
                                "catalog by microcks/generate-contracts.py. The mock of GET /cities answers one code "
                                "at a time; the service accepts up to 50 codes separated by comma"},
        "paths": {
            "/cities/{code}": {"get": {
                "operationId": "getCity", "summary": "Get one city by its code",
                "x-microcks-operation": fallback("URI_PARTS", "code"),
                "parameters": [{"name": "code", "in": "path", "required": True, "schema": {"type": "string"},
                                "examples": code_examples}],
                "responses": {
                    "200": {"description": "The city", "content": json_content(
                        CITY_SCHEMA, {city["code"]: {"value": city} for city in cities})},
                    "404": {"description": "The city does not exist", "content": json_content(
                        ERROR_SCHEMA, {UNKNOWN: {"value": not_found}})}}}},
            "/cities": {"get": {
                "operationId": "getCities", "summary": "Get several cities by their codes",
                "x-microcks-operation": {"dispatcher": "SCRIPT", "dispatcherRules": codes_dispatcher(cities)},
                "parameters": [{"name": "codes", "in": "query", "required": True, "schema": {"type": "string"},
                                "examples": code_examples}],
                "responses": {"200": {"description": "The cities that exist", "content": json_content(
                    {"type": "array", "items": CITY_SCHEMA},
                    dict({city["code"]: {"value": [city]} for city in cities}, **{UNKNOWN: {"value": []}}))}}}},
        },
    }


def check_hubs(cities):
    """The connections of the providers have to exist in the catalog, or their itineraries are discarded."""
    codes = {city["code"] for city in cities}
    for name, _, _, hubs, _, _ in PROVIDERS:
        missing = sorted(set(hubs) - codes)
        if missing:
            raise SystemExit("The hubs %s of %s are not in the catalog" % (missing, name))


if __name__ == "__main__":
    for name, title, carriers, hubs, response, schema in PROVIDERS:
        path = OUTPUT / ("%s-openapi.json" % name)
        path.write_text(json.dumps(contract(name, title, carriers, hubs, response, schema), indent=2) + "\n")
        print("Generated", path)

    catalog = catalog_cities()
    check_hubs(catalog)
    path = OUTPUT / "catalog-openapi.json"
    path.write_text(json.dumps(catalog_contract(catalog), indent=2, ensure_ascii=False) + "\n")
    print("Generated", path, "with", len(catalog), "cities")
