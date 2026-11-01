"""Verify additive solo-governance evidence; does not execute application tests."""
import hashlib
import json
from pathlib import Path


HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify_manifest(path):
    members = load(path)["files"]
    names = [item["path"] for item in members]
    assert len(names) == len(set(names)), path
    for item in members:
        target = ROOT / item["path"]
        assert target.resolve().is_relative_to(ROOT.resolve()), item["path"]
        assert digest(target) == item["sha256"], item["path"]
        assert target.stat().st_size == item["bytes"], item["path"]
    return names


def flatten(value, prefix=""):
    result = {}
    for key, item in value.items():
        name = prefix + key
        if isinstance(item, dict):
            result.update(flatten(item, name + "."))
        else:
            result[name] = item
    return result


def main():
    prior = [ROOT / "artifact-manifest.json",
             ROOT / "follow-ups/20261101-4d57f30/artifact-manifest.json",
             ROOT / "follow-ups/20261101-human-acceptance-prep/artifact-manifest.json",
             HERE / "artifact-manifest.json"]
    assert [len(verify_manifest(p)) for p in prior] == [105, 187, 207, 213]
    integrity = load(HERE / "integrity-self-review.json")
    assert len(integrity["protectedFiles"]) == 208
    for name, expected in integrity["protectedFiles"].items():
        assert digest(ROOT / name) == expected, name
    confirmation = load(HERE / "hp-self-review-confirmation.json")
    assert confirmation["technicalSelfReviewConfirmed"] is True
    assert confirmation["status"] == (
        "SELF-REVIEWED BY HP — INDEPENDENT REVIEW NOT AVAILABLE (SOLO DEVELOPER)")
    assert confirmation["evidenceClassification"] == {"complete": 27, "automationLimited": 9}
    assert confirmation["humanAcceptance"]["reportedPass"] == 36
    assert confirmation["fullPostgresITRerun"] is False
    assert confirmation["draftAuthorization"] is None
    assert confirmation["mergeAuthorization"] is None
    receipt = load(HERE / "authorized-protection-change.json")
    before, after = flatten(receipt["before"]), flatten(receipt["after"])
    changes = {key for key in before if before[key] != after[key]}
    assert changes == {"required_conversation_resolution.enabled",
                       "required_pull_request_reviews.required_approving_review_count"}
    assert after["required_pull_request_reviews.required_approving_review_count"] == 0
    assert after["required_conversation_resolution.enabled"] is True
    assert after["required_status_checks.strict"] is True
    assert after["enforce_admins.enabled"] is True
    assert after["allow_force_pushes.enabled"] is False
    assert after["allow_deletions.enabled"] is False
    publication = HERE / "publication-manifest.json"
    members = set(verify_manifest(publication))
    actual = {p.relative_to(ROOT).as_posix() for p in ROOT.rglob("*")
              if p.is_file() and p != publication}
    assert members == actual, "Publication inventory mismatch"
    print(json.dumps({"result": "PASS", "priorManifestMembers": [105, 187, 207, 213],
                      "preservedFiles": 208, "publicationMembers": len(members),
                      "authorizedProtectionFieldsChanged": sorted(changes),
                      "applicationTestsRerun": False}))


if __name__ == "__main__":
    main()
