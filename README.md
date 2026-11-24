# quote-service

_Built with AI assistance._

Freight quotes over REST: create a quote, price it from a versioned rate
table, let it expire, accept it once.

Java 21, Spring Boot 3.4, Spring Data JPA, Flyway, MySQL 8.

```
POST /api/quotes                 price a parcel, returns 201 and a 30 minute quote
GET  /api/quotes/{id}
GET  /api/quotes?status=OPEN
POST /api/quotes/{id}/accept     200 once, 409 every time after
POST /api/quotes/expire          what the scheduled sweep does, on demand

POST /api/rate-tables            new version, never an edit
GET  /api/rate-tables            all versions
GET  /api/rate-tables/{version}
GET  /api/rate-tables/active     the table in force now
```

## Pricing

```
price = band.base + ceil(weightKg) * band.perKg
then   x express multiplier   (EXPRESS only)
then   x (1 + fuel surcharge)
then   at least the table's minimum charge
```

Money is whole cents in a `long`. Each percentage step rounds half up to a
cent once. The order matters: express before fuel gives a different cent than
fuel before express, so `PricingEngineTest` pins it with worked numbers.
Weight is billed in whole kilos, rounded up, but the band is chosen on the
real weight. 10 kg is band one, 10.01 kg is band two billed as 11.

## Rate tables are versions

A rate table is a numbered, immutable row with an effective date and its
bands. Changing a price means creating version N+1. The table in force is the
one with the latest `effective_from` not in the future, highest version on a
tie. A future-dated table can be loaded ahead of time and takes over at its
date without a deploy.

A quote stores the version it was priced under. Accepting it later charges the
price it showed, whatever has been published since. `RateTableApiIT` covers
both sides: new quotes use the new table, an open quote keeps the old one.

## Accept exactly once

`Quote` has a `@Version` column. Two requests can both read the same OPEN
row, but the `UPDATE ... WHERE version = ?` lets only one through. The other
gets an optimistic lock failure, re-reads, and reports what the row now says:
409 already accepted, or 409 expired. It never returns a 500 for losing.

The bulk expiry statement is a JPQL update, and Hibernate does not version a
bulk update. It sets `version = version + 1` itself. Without that, an accept
holding the old version could commit over a row the sweep had just expired.

Accept also checks the clock, so a quote past its deadline is refused even if
the sweep has not run yet. The sweep is housekeeping, not the gate.

## Errors

Everything leaves as `application/problem+json` with a stable type:
`urn:quote-service:problem:quote-expired`, `...:quote-already-accepted`,
`...:no-rate`, `...:validation` and so on. Validation failures list every bad
field in an `errors` array, sorted by field name, so a client sees all of them
in one round trip.

## What the tests are

```
mvn verify
```

67 tests. `mvn verify` passed on my Mac with JDK 21 and Docker through Colima.
That is the only place it has run so far.

| Class | Tests | Needs Docker | What it checks |
|---|---|---|---|
| `PricingEngineTest` | 14 | no | band edges, rounding, express, fuel, minimum charge |
| `QuoteTest` | 5 | no | the status transitions and the inclusive deadline |
| `QuoteApiIT` | 20 | yes | create, validate, accept, expire, sweep, list |
| `RateTableApiIT` | 12 | yes | versions, effective dates, quotes across a price change |
| `ConcurrencyIT` | 7 | yes | real threads against MySQL |
| `MigrationIT` | 9 | yes | empty database to latest, v2 data into v3, constraints |

The `IT` classes use Testcontainers to start `mysql:8.0`. They do not use H2,
because what is under test is a unique index, an optimistic lock, a check
constraint and a bulk update, and H2 behaves differently on each. Unit tests
run in Surefire, the `IT` classes in Failsafe, so `mvn test` works without
Docker and `mvn verify` runs everything.

`ConcurrencyIT` fires 16 threads at one quote and asserts one 200 and fifteen
conflicts, races accept against the sweep both inside and after the window,
and creates eight rate tables at once.

Run it locally:

    docker compose up -d
    DB_URL=jdbc:mysql://localhost:3306/quotes mvn spring-boot:run

## The bug the race test found

The first version of `RateTableService.create` read `max(version)`, added one
and inserted. It looked fine and every sequential test passed. The race test
for eight parallel creates failed straight away: two requests read the same
maximum, one hit the unique index and the caller got a
`DataIntegrityViolationException`. The unique index did its job, nothing was
corrupted, but a caller got an error for a request that was valid. The fix is
a retry that re-reads the maximum. The index stays the guarantee.

## What this does not do

**No authentication.** Anyone who can reach the port can create a rate table.
It would sit behind a gateway or get Spring Security, and I did not want to
fake one.

**Retry spins without backoff.** The rate table retry gives up after 25
attempts and does not sleep between them. Eight parallel creates settle well
inside that. A hundred at once might not. Rate tables are created by a person
a few times a year, so I stopped there.

**Expiry is checked against the app's clock.** Two app servers with clocks a
few seconds apart will disagree about a quote right at its deadline. The
sweep and accept both use the same `Clock` bean, so one server is consistent
with itself, and several would want the database's `NOW()`.

**One currency per table, no conversion.** Quotes copy the table's currency.

**CI is untested.** The workflow in `.github/workflows/ci.yml` has never run on
GitHub, because the repo has no remote yet. It also repeats the race class
three more times, since a race that passes once proves little, and I ran that
loop zero times locally.

## Layout

```
src/main/java/dev/virk/quotes/
  quote/    Quote, QuoteService (accept, expire), ExpirySweep
  rates/    RateTable, RateBand, PricingEngine, RateTableService
  api/      controllers, DTOs, ApiExceptionHandler
src/main/resources/db/migration/
  V1 tables, V2 seed rate table v1, V3 service_level column
```

MIT.
