import unittest

from scripts.efpp_model import (
    Failure,
    repeat_risk,
    consequence_chain,
    governing_defaults,
    simulated_failure_build,
)


def failure(**overrides):
    values = dict(
        component="sensor-binder",
        operation="bind-observation",
        state="permission-granted|sensor-present",
        dependencies=("android-api-35", "sensor-service"),
        authority_scope="observation-only",
        error="STALE_OBSERVATION",
        severity="HARMFUL",
    )
    values.update(overrides)
    return Failure(**values)


class EvidenceFirstFailureTests(unittest.TestCase):

    def test_exact_harmful_repeat_is_held(self):
        self.assertEqual(
            repeat_risk(failure(), failure()),
            "HOLD_REPEAT"
        )

    def test_different_state_is_not_assumed_same_failure(self):
        self.assertEqual(
            repeat_risk(
                failure(),
                failure(state="permission-denied")
            ),
            "NO_EXACT_MATCH"
        )

    def test_dependency_change_changes_signature(self):
        self.assertEqual(
            repeat_risk(
                failure(),
                failure(dependencies=("different-version",))
            ),
            "NO_EXACT_MATCH"
        )

    def test_unknown_record_does_not_become_safe(self):
        self.assertEqual(repeat_risk(None, failure()), "UNKNOWN")

    def test_consequence_chain_accepts_acyclic_graph(self):
        self.assertTrue(consequence_chain([
            ("failure", "data-loss-risk"),
            ("data-loss-risk", "service-impact"),
            ("service-impact", "human-impact"),
        ]))

    def test_consequence_cycle_is_rejected(self):
        self.assertFalse(consequence_chain([
            ("failure", "impact"),
            ("impact", "recovery"),
            ("recovery", "failure"),
        ]))

    def test_governance_defaults_remain_false(self):
        values = governing_defaults()
        self.assertFalse(values["PROVEN"])
        self.assertFalse(values["SEALED"])
        self.assertFalse(values["PRODUCTION_AUTHORIZED"])
        self.assertFalse(values["UNKNOWN_IS_FALSE"])
        self.assertFalse(values["PATCHED_IS_VERIFIED"])
        self.assertFalse(values["CI_GREEN_IS_AUTHORIZATION"])

    def test_failure_build_is_simulation_only(self):
        result = simulated_failure_build("unknown_authority")
        self.assertEqual(result["environment"], "SIMULATED_ONLY")
        self.assertFalse(result["device_action"])
        self.assertFalse(result["production_action"])

    def test_unapproved_failure_scenario_is_rejected(self):
        with self.assertRaises(ValueError):
            simulated_failure_build("real_bank_app")

    def test_severity_does_not_change_failure_identity(self):
        low = failure(severity="LOW")
        critical = failure(severity="CRITICAL")

        # Severity is risk metadata, not failure identity.
        self.assertEqual(low.signature(), critical.signature())

    def test_severity_escalation_holds_matching_failure(self):
        low = failure(severity="LOW")
        critical = failure(severity="CRITICAL")

        # Escalating severity must never make a matching failure safe.
        self.assertEqual(
            repeat_risk(low, critical),
            "HOLD_REPEAT"
        )

    def test_harmful_previous_failure_blocks_low_severity_repeat(self):
        previous = failure(severity="HARMFUL")
        current = failure(severity="LOW")

        self.assertEqual(
            repeat_risk(previous, current),
            "HOLD_REPEAT"
        )


if __name__ == "__main__":
    unittest.main()
