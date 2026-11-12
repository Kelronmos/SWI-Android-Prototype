# SWI-EFPP-001 — Evidence-First Failure Prevention

Status: IMPLEMENTATION UNDER TEST
PROVEN=NO · SEALED=NO · PRODUCTION_AUTHORIZED=NO

## Governing requirement

SWI must preserve the evidence of a failure, identify the relevant
conditions, evaluate downstream consequences, and prevent silent
repetition of matching harmful conditions.

A patch is not proof. A matching signature is not proof of identical
causation. A passing test proves only its defined test scope.

## Required failure record

- Component and operation
- Exact relevant runtime/state conditions
- Dependency identities and versions
- Authority scope
- Error and observed outcome
- Evidence reference and timestamp
- Immediate and downstream consequences
- Severity and uncertainty
- Correction and independent retest result

## Repeat prevention

An exact matching harmful or critical failure condition must return
HOLD_REPEAT for review. It must not silently retry.

A different signature is not automatically safe. It requires a new
risk assessment. Missing information remains UNKNOWN.

## Consequence propagation

Consequence relationships must be represented as a directed graph.
Cycles are rejected for review because they can conceal unresolved
feedback or recovery paths.

## Experimental failure builds

Failure injection is restricted to in-memory/local simulations.
The test harness must not deliberately damage real devices, access
financial/government/private apps, bypass Android permissions, or
execute production actions.

## Verification boundary

Unit tests, Android build, lint, emulator tests and physical-device
tests are separate evidence classes. Never combine them into one
unqualified PASS.

CI_GREEN != SWI_AUTHORIZED
TESTED != PROVEN
PROVEN != SEALED
SEALED != PRODUCTION_AUTHORIZED
