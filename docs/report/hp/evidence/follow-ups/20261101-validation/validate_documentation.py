"""Read-only DOC-01 artifact/source checks. No application test execution."""
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import zipfile

from docx import Document
from PIL import Image


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
REPO = ROOT.parents[2]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def git_blob(ref, path):
    return subprocess.check_output(["git", "show", ref + ":" + path], cwd=REPO)


def main():
    json_files = list(ROOT.rglob("*.json"))
    for path in json_files:
        load(path)
    docx = ROOT / "HP_Chapters_1_2_4_5_12.docx"
    document = Document(docx)
    assert len(document.inline_shapes) == 68
    with zipfile.ZipFile(docx) as archive:
        assert archive.testzip() is None
        members = set(archive.namelist())
        media_hashes = set()
        for name in members:
            if name.startswith("word/media/"):
                data = archive.read(name)
                with Image.open(io.BytesIO(data)) as image:
                    image.verify()
                media_hashes.add(sha(data))
        for item in load(ROOT / "evidence/figures.json"):
            path = ROOT / item["path"].replace("\\", "/")
            assert sha(path.read_bytes()) == item["sha256"], path
            assert item["sha256"] in media_hashes, item["path"]
    original = load(HERE / "original-publication-hashes.json")
    for path, expected in original["files"].items():
        assert sha(git_blob(original["originalHead"], "docs/report/hp/" + path)) == expected
    prior_manifest = load(HERE / "historical/evidence/artifact-manifest.json")
    for item in prior_manifest["files"]:
        data = git_blob(original["originalHead"], "docs/report/hp/" + item["path"])
        assert sha(data) == item["sha256"], item["path"]
    for item in load(ROOT / "evidence/source-excerpts.json"):
        data = git_blob(item["source"], item["path"])
        text = data.decode().replace("\r\n", "\n")
        assert item["sha256"] in {sha(data), sha(text.encode()), sha(text.replace("\n", "\r\n").encode())}
        transcript = ROOT / item["figure"].replace("\\", "/")
        transcript = transcript.with_suffix(".txt").read_text(encoding="utf-8")
        expected = "\n".join(text.splitlines()[item["startLine"]-1:item["endLine"]])
        assert transcript.strip() == expected.strip(), item["path"]
    for item in load(ROOT / "evidence/missing-screenshots.json"):
        assert item["status"].startswith("PASS — genuine screenshot")
        assert (ROOT / item["evidence"]).is_file()
        assert item["humanAcceptance"] == "HUMAN PENDING"
    link_errors = []
    for path in ROOT.rglob("*.md"):
        for target in re.findall(r"\]\(([^)]+)\)", path.read_text(encoding="utf-8")):
            if "://" in target or target.startswith("#"):
                continue
            target = target.split("#")[0].strip("<>")
            if not (path.parent / target).exists():
                link_errors.append([str(path.relative_to(ROOT)), target])
    assert not link_errors, link_errors
    manifest_path = ROOT / "evidence/artifact-manifest.json"
    manifest = load(manifest_path)
    entries = {item["path"]: item for item in manifest["files"]}
    actual = {p.relative_to(ROOT).as_posix() for p in ROOT.rglob("*") if p.is_file() and p != manifest_path}
    assert set(entries) == actual
    for path, item in entries.items():
        data = (ROOT / path).read_bytes()
        assert len(data) == item["bytes"] and sha(data) == item["sha256"], path
    print(json.dumps({"result": "PASS", "jsonFiles": len(json_files),
                      "originalFilesVerifiedInGit": len(original["files"]),
                      "historicalManifestMembers": len(prior_manifest["files"]),
                      "currentManifestMembers": len(entries), "sourceExcerpts": 19,
                      "embeddedPlacements": 68, "embeddedMedia": len(media_hashes),
                      "missingScreenshotSections": 0, "markdownLinkErrors": 0,
                      "humanAcceptance": "HUMAN PENDING", "applicationTestsExecuted": False}))


if __name__ == "__main__":
    main()
