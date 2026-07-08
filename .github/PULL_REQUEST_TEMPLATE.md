## Service(s) affected
- [ ] config-server
- [ ] eureka-service
- [ ] auth-service
- [ ] ticket-service
- [ ] venue-service
- [ ] analytics-service
- [ ] audit-service
- [ ] notification-service
- [ ] availability-locking-service
- [ ] event-service
- [ ] payment-service
- [ ] reservation-service
- [ ] libs / shared

## Type of change
- [ ] feat
- [ ] fix
- [ ] refactor
- [ ] docs
- [ ] chore
- [ ] test

## Description

<!-- What does this PR do and why? -->

## Kafka / event contract changed?
- [ ] Yes — updated the schema in `libs/` and pinged the owning teams of consuming services
- [ ] No

## Saga step changed? (auth / notification / payment / reservation / availability-locking)
- [ ] Yes — verified compensation logic still triggers correctly on failure
- [ ] No

## Checklist
- [ ] Ran `mvn spotless:apply` before pushing
- [ ] Added/updated tests where relevant
- [ ] PR title follows `type(scope): description` format
