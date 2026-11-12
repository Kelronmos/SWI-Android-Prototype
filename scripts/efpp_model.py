"""SWI-EFPP-001: Evidence-first failure prevention reference model.

This is a local research/test model, not an execution-authority engine.
"""
from dataclasses import dataclass
from hashlib import sha256
import json


@dataclass(frozen=True)
class Failure:
    component: str
    operation: str
    state: str
    dependencies: tuple
    authority_scope: str
    error: str
    severity: str = "LOW"

    def signature(self):
        payload = {
            "component": self.component,
            "operation": self.operation,
            "state": self.state,
            "dependencies": sorted(self.dependencies),
            "authority_scope": self.authority_scope,
            "error": self.error,
        }
        raw = json.dumps(payload, sort_keys=True, separators=(",", ":"))
        return sha256(raw.encode()).hexdigest()


def repeat_risk(previous, current):
    """Exact relevant-condition match; not proof of identical causation."""
    if previous is None or current is None:
        return "UNKNOWN"
    if previous.signature() != current.signature():
        return "NO_EXACT_MATCH"
    if previous.severity in ("HARMFUL", "CRITICAL") or \
       current.severity in ("HARMFUL", "CRITICAL"):
        return "HOLD_REPEAT"
    return "MATCH_REQUIRES_REVIEW"


def consequence_chain(edges):
    """Validate a directed consequence graph; reject cycles."""
    graph = {}
    for source, target in edges:
        graph.setdefault(source, []).append(target)
        graph.setdefault(target, [])

    visiting, visited = set(), set()

    def visit(node):
        if node in visiting:
            return False
        if node in visited:
            return True
        visiting.add(node)
        for child in graph[node]:
            if not visit(child):
                return False
        visiting.remove(node)
        visited.add(node)
        return True

    return all(visit(node) for node in graph)


def governing_defaults():
    return {
        "PROVEN": False,
        "SEALED": False,
        "PRODUCTION_AUTHORIZED": False,
        "UNKNOWN_IS_FALSE": False,
        "PATCHED_IS_VERIFIED": False,
        "CI_GREEN_IS_AUTHORIZATION": False,
    }


def simulated_failure_build(scenario):
    """Controlled in-memory failure only; never invokes real device actions."""
    allowed = {
        "missing_dependency",
        "stale_observation",
        "unknown_authority",
        "replayed_failure",
        "consequence_cycle",
    }
    if scenario not in allowed:
        raise ValueError("Scenario is not in the approved simulation set")
    return {
        "scenario": scenario,
        "environment": "SIMULATED_ONLY",
        "device_action": False,
        "production_action": False,
        "result": "INJECTED_FOR_TEST",
    }
