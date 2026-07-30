#!/usr/bin/env python3
"""
LearnWithMe — GitHub-Backlog aus backlog.json erzeugen.

Legt Labels, Milestones, Epic-Issues, User-Story-Sub-Issues, Spikes und ein
Projects-v2-Board an. Das Script ist idempotent: ein zweiter Lauf legt nichts
doppelt an, sondern aktualisiert vorhandene Einträge.

Voraussetzungen
---------------
  * Python 3.9+ (nur Standardbibliothek)
  * GitHub CLI:  https://cli.github.com
  * Angemeldet mit ausreichenden Scopes:

        gh auth login
        gh auth refresh -s project,read:project,repo

    Ohne `project`-Scope schlagen nur die Board-Schritte fehl; Issues,
    Labels und Milestones werden trotzdem angelegt.

Verwendung
----------
    python seed_github.py --dry-run                  # nichts schreiben, nur zeigen
    python seed_github.py                            # alles anlegen
    python seed_github.py --only labels,milestones   # gezielt einzelne Schritte
    python seed_github.py --repo kia/LearnWithMe     # anderes Repo
    python seed_github.py --no-project               # Board überspringen

Idempotenz
----------
Jedes erzeugte Issue trägt am Ende des Bodys einen unsichtbaren Marker
(`<!-- lwm:key=A1 -->`). Beim erneuten Lauf wird darüber zugeordnet — auch
dann, wenn der Titel in GitHub inzwischen geändert wurde.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path
from typing import Any

HERE = Path(__file__).resolve().parent
BACKLOG_FILE = HERE / "backlog.json"

MARKER_RE = re.compile(r"<!--\s*lwm:key=([A-Za-z0-9\-]+)\s*-->")
STEPS = ["labels", "milestones", "epics", "stories", "spikes", "subissues", "project"]

PRIORITY_LABEL = {"must": "prio:must", "should": "prio:should", "could": "prio:could"}
PRIORITY_FIELD = {"must": "Must", "should": "Should", "could": "Could"}

# Farben für Single-Select-Optionen im Projekt-Board.
# Die GitHub-API akzeptiert nur diese Namen.
SELECT_COLORS = ["GRAY", "BLUE", "GREEN", "YELLOW", "ORANGE", "RED", "PINK", "PURPLE"]


# ─────────────────────────────────────────────────────────────────────────────
#  Ausgabe
# ─────────────────────────────────────────────────────────────────────────────

class Out:
    _use_color = sys.stdout.isatty() and os.name != "nt"

    @classmethod
    def _c(cls, code: str, text: str) -> str:
        return f"\033[{code}m{text}\033[0m" if cls._use_color else text

    @classmethod
    def step(cls, text: str) -> None:
        print(f"\n{cls._c('1;36', '▶ ' + text)}")

    @classmethod
    def ok(cls, text: str) -> None:
        print(f"  {cls._c('32', '✓')} {text}")

    @classmethod
    def skip(cls, text: str) -> None:
        print(f"  {cls._c('90', '·')} {cls._c('90', text)}")

    @classmethod
    def warn(cls, text: str) -> None:
        print(f"  {cls._c('33', '!')} {text}")

    @classmethod
    def fail(cls, text: str) -> None:
        print(f"  {cls._c('31', '✗')} {text}", file=sys.stderr)


# ─────────────────────────────────────────────────────────────────────────────
#  GitHub-CLI-Wrapper
# ─────────────────────────────────────────────────────────────────────────────

class GitHubError(RuntimeError):
    pass


class Gh:
    def __init__(self, dry_run: bool = False) -> None:
        self.dry_run = dry_run
        self.bin = shutil.which("gh") or shutil.which("gh.exe")
        if not self.bin:
            raise GitHubError(
                "GitHub CLI (`gh`) wurde nicht gefunden.\n"
                "Installation: https://cli.github.com — danach `gh auth login`."
            )

    def _run(self, args: list[str], payload: Any | None = None) -> str:
        proc = subprocess.run(
            [self.bin, *args],
            input=json.dumps(payload, ensure_ascii=False) if payload is not None else None,
            capture_output=True,
            text=True,
            encoding="utf-8",
        )
        if proc.returncode != 0:
            raise GitHubError((proc.stderr or proc.stdout).strip())
        return proc.stdout

    # ── REST ────────────────────────────────────────────────────────────────
    def rest(self, method: str, path: str, payload: dict | None = None,
             paginate: bool = False, write: bool = True) -> Any:
        if write and self.dry_run and method != "GET":
            return {"__dry_run__": True}
        args = ["api", "-X", method, path, "-H", "Accept: application/vnd.github+json"]
        if paginate:
            args += ["--paginate", "--slurp"]
        if payload is not None:
            args += ["--input", "-"]
        raw = self._run(args, payload)
        if not raw.strip():
            return None
        data = json.loads(raw)
        # --slurp liefert eine Liste von Seiten → flach machen
        if paginate and isinstance(data, list) and data and isinstance(data[0], list):
            return [item for page in data for item in page]
        return data

    # ── GraphQL ─────────────────────────────────────────────────────────────
    def graphql(self, query: str, variables: dict | None = None,
                write: bool = True) -> Any:
        if write and self.dry_run:
            return {"__dry_run__": True}
        body = {"query": query, "variables": variables or {}}
        raw = self._run(["api", "graphql", "--input", "-"], body)
        data = json.loads(raw)
        if "errors" in data:
            raise GitHubError(json.dumps(data["errors"], ensure_ascii=False, indent=2))
        return data["data"]

    def current_repo(self) -> str:
        raw = self._run(["repo", "view", "--json", "nameWithOwner"])
        return json.loads(raw)["nameWithOwner"]


# ─────────────────────────────────────────────────────────────────────────────
#  Body-Rendering
# ─────────────────────────────────────────────────────────────────────────────

def marker(key: str) -> str:
    return f"<!-- lwm:key={key} -->"


def _checklist(items: list[str]) -> str:
    return "\n".join(f"- [ ] {i}" for i in items)


def _bullets(items: list[str]) -> str:
    return "\n".join(f"- {i}" for i in items)


def render_epic_body(epic: dict, plan_ref: str) -> str:
    parts = [
        f"> **Plan:** [`{plan_ref}`]({plan_ref}) {epic.get('planRef', '')}".rstrip(),
        "",
        "## Ziel",
        epic["goal"],
        "",
        "## Angestrebtes Ergebnis",
        epic["outcome"],
    ]
    if epic.get("risk"):
        parts += ["", "## ⚠ Risiko", epic["risk"]]
    parts += [
        "",
        "## Stories",
        "_Die User Stories hängen als Sub-Issues an diesem Epic — die Liste oben "
        "wird von GitHub gepflegt und zeigt den Fortschritt automatisch._",
        "",
        "---",
        marker(f"epic-{epic['key']}"),
    ]
    return "\n".join(parts)


def render_story_body(story: dict, epic: dict | None, plan_ref: str) -> str:
    prio = PRIORITY_FIELD.get(story.get("priority", ""), "—")
    head = [f"**Priorität:** {prio}", f"**Schätzung:** {story.get('points', '—')}"]
    if epic:
        head.append(f"**Epic:** {epic['title']}")
    if story.get("planRef") or epic:
        ref = story.get("planRef") or f"§4.2 {story['key']}"
        head.append(f"**Plan:** `{plan_ref}` {ref}")

    parts = ["> " + " · ".join(head), ""]

    if story.get("story"):
        parts += ["## User Story", story["story"], ""]

    if story.get("question"):
        parts += ["## Frage", story["question"], ""]
    if story.get("why"):
        parts += ["## Warum das jetzt geklärt werden muss", story["why"], ""]
    if story.get("timebox"):
        parts += [f"**Timebox:** {story['timebox']}", ""]

    parts += ["## Akzeptanzkriterien", _checklist(story.get("ac", [])), ""]

    if story.get("notes"):
        parts += ["## Hinweise", _bullets(story["notes"]), ""]

    if story.get("blocks"):
        parts += ["## Blockiert", _bullets(story["blocks"]), ""]

    parts += [
        "## Definition of Done",
        _checklist([
            "Alle Akzeptanzkriterien erfüllt",
            "Tests geschrieben und grün (Unit + Integration, wo sinnvoll)",
            "`./gradlew architectureTest` grün — keine Modulgrenze verletzt",
            "Bei API-Änderung: OpenAPI aktualisiert, TS-Client neu generiert",
            "Bei Architekturentscheidung: ADR in `docs/PLAN.md` ergänzt oder aktualisiert",
        ]),
        "",
        "---",
        marker(story["key"]),
    ]
    return "\n".join(parts)


# ─────────────────────────────────────────────────────────────────────────────
#  Seeder
# ─────────────────────────────────────────────────────────────────────────────

class Seeder:
    def __init__(self, gh: Gh, repo: str, backlog: dict, dry_run: bool) -> None:
        self.gh = gh
        self.repo = repo
        self.owner, self.name = repo.split("/", 1)
        self.backlog = backlog
        self.dry_run = dry_run
        self.plan_ref = backlog["meta"].get("planRef", "docs/PLAN.md")
        self.milestone_numbers: dict[str, int] = {}
        self.issues_by_key: dict[str, dict] = {}   # key → {number, node_id, title}

    # ── Labels ──────────────────────────────────────────────────────────────
    def seed_labels(self) -> None:
        Out.step("Labels")
        existing = {l["name"]: l for l in
                    self.gh.rest("GET", f"repos/{self.repo}/labels?per_page=100",
                                 paginate=True, write=False) or []}
        for label in self.backlog["labels"]:
            name = label["name"]
            payload = {"name": name, "color": label["color"],
                       "description": label.get("description", "")}
            if name in existing:
                cur = existing[name]
                if (cur.get("color") != label["color"]
                        or (cur.get("description") or "") != payload["description"]):
                    self.gh.rest("PATCH", f"repos/{self.repo}/labels/{name}", payload)
                    Out.ok(f"{name} (aktualisiert)")
                else:
                    Out.skip(f"{name}")
            else:
                self.gh.rest("POST", f"repos/{self.repo}/labels", payload)
                Out.ok(name)

    # ── Milestones ──────────────────────────────────────────────────────────
    def seed_milestones(self) -> None:
        Out.step("Milestones")
        existing = {m["title"]: m for m in
                    self.gh.rest("GET", f"repos/{self.repo}/milestones?state=all&per_page=100",
                                 paginate=True, write=False) or []}
        for ms in self.backlog["milestones"]:
            title = ms["title"]
            payload = {
                "title": title,
                "description": ms.get("description", ""),
                "due_on": f"{ms['due_on']}T12:00:00Z" if ms.get("due_on") else None,
            }
            payload = {k: v for k, v in payload.items() if v is not None}
            if title in existing:
                number = existing[title]["number"]
                self.gh.rest("PATCH", f"repos/{self.repo}/milestones/{number}", payload)
                self.milestone_numbers[title] = number
                Out.skip(f"{title} (aktualisiert)")
                continue

            created = self.gh.rest("POST", f"repos/{self.repo}/milestones", payload)
            if isinstance(created, dict) and "number" in created:
                self.milestone_numbers[title] = created["number"]
            Out.ok(f"[dry-run] {title}" if self.dry_run else title)

    def _load_milestones(self) -> None:
        if self.milestone_numbers:
            return
        for m in self.gh.rest("GET", f"repos/{self.repo}/milestones?state=all&per_page=100",
                              paginate=True, write=False) or []:
            self.milestone_numbers[m["title"]] = m["number"]

    # ── Issues ──────────────────────────────────────────────────────────────
    def _load_existing_issues(self) -> dict[str, dict]:
        """Bestehende Issues über den Marker im Body zuordnen."""
        found: dict[str, dict] = {}
        issues = self.gh.rest(
            "GET", f"repos/{self.repo}/issues?state=all&per_page=100",
            paginate=True, write=False) or []
        for issue in issues:
            if "pull_request" in issue:
                continue
            m = MARKER_RE.search(issue.get("body") or "")
            if m:
                found[m.group(1)] = {
                    "number": issue["number"],
                    "node_id": issue["node_id"],
                    "title": issue["title"],
                }
        return found

    def _upsert_issue(self, key: str, title: str, body: str,
                      labels: list[str], milestone: str | None) -> None:
        self._load_milestones()
        payload: dict[str, Any] = {"title": title, "body": body, "labels": labels}
        if milestone and milestone in self.milestone_numbers:
            payload["milestone"] = self.milestone_numbers[milestone]
        elif milestone and not self.dry_run:
            # Im Dry-Run existieren die Milestones noch nicht — das ist erwartet
            # und keine Warnung wert.
            Out.warn(f"Milestone '{milestone}' nicht gefunden — {key} ohne Milestone")

        if key in self.issues_by_key:
            number = self.issues_by_key[key]["number"]
            self.gh.rest("PATCH", f"repos/{self.repo}/issues/{number}", payload)
            Out.skip(f"#{number} {title} (aktualisiert)")
            return

        created = self.gh.rest("POST", f"repos/{self.repo}/issues", payload)
        if self.dry_run:
            Out.ok(f"[dry-run] {title}")
            self.issues_by_key[key] = {"number": -1, "node_id": "", "title": title}
            return
        self.issues_by_key[key] = {
            "number": created["number"],
            "node_id": created["node_id"],
            "title": title,
        }
        Out.ok(f"#{created['number']} {title}")
        time.sleep(0.35)   # freundlich zur sekundären Rate-Limit-Grenze

    def seed_epics(self) -> None:
        Out.step("Epic-Issues")
        self.issues_by_key.update(self._load_existing_issues())
        for epic in self.backlog["epics"]:
            self._upsert_issue(
                key=f"epic-{epic['key']}",
                title=epic["title"],
                body=render_epic_body(epic, self.plan_ref),
                labels=["type:epic", epic["label"]],
                milestone=epic.get("milestone"),
            )

    def seed_stories(self) -> None:
        Out.step("User Stories")
        if not self.issues_by_key:
            self.issues_by_key.update(self._load_existing_issues())
        for epic in self.backlog["epics"]:
            for story in epic["stories"]:
                labels = ["type:story", epic["label"]]
                labels.append(PRIORITY_LABEL.get(story.get("priority", ""), "prio:should"))
                labels += [l for l in story.get("labels", []) if not l.startswith("type:")]
                self._upsert_issue(
                    key=story["key"],
                    title=f"{story['key']} · {story['title']}",
                    body=render_story_body(story, epic, self.plan_ref),
                    labels=sorted(set(labels)),
                    milestone=story.get("milestone"),
                )

    def seed_spikes(self) -> None:
        Out.step("Spikes (offene Entscheidungen)")
        if not self.issues_by_key:
            self.issues_by_key.update(self._load_existing_issues())
        for spike in self.backlog.get("spikes", []):
            labels = ["type:spike", "epic:platform",
                      PRIORITY_LABEL.get(spike.get("priority", ""), "prio:should")]
            labels += [l for l in spike.get("labels", []) if not l.startswith("type:")]
            self._upsert_issue(
                key=spike["key"],
                title=f"{spike['key']} · {spike['title']}",
                body=render_story_body(spike, None, self.plan_ref),
                labels=sorted(set(labels)),
                milestone=spike.get("milestone"),
            )

    # ── Sub-Issues ──────────────────────────────────────────────────────────
    def link_subissues(self) -> None:
        Out.step("Stories als Sub-Issues an die Epics hängen")
        if not self.issues_by_key:
            self.issues_by_key.update(self._load_existing_issues())

        mutation = """
        mutation($parent: ID!, $child: ID!) {
          addSubIssue(input: {issueId: $parent, subIssueId: $child}) {
            issue { number }
          }
        }
        """
        for epic in self.backlog["epics"]:
            parent = self.issues_by_key.get(f"epic-{epic['key']}")
            if not parent:
                Out.warn(f"Epic {epic['key']} nicht gefunden — übersprungen")
                continue
            for story in epic["stories"]:
                child = self.issues_by_key.get(story["key"])
                if not child:
                    continue
                if self.dry_run:
                    Out.ok(f"[dry-run] {story['key']} → {epic['key']}")
                    continue
                try:
                    self.gh.graphql(mutation, {"parent": parent["node_id"],
                                               "child": child["node_id"]})
                    Out.ok(f"{story['key']} → Epic {epic['key']}")
                except GitHubError as exc:
                    msg = str(exc)
                    # Erneuter Lauf: die Verknüpfung besteht bereits.
                    if "already" in msg.lower() or "duplicate" in msg.lower():
                        Out.skip(f"{story['key']} → Epic {epic['key']} (bestand bereits)")
                    else:
                        Out.warn(f"{story['key']}: {msg.splitlines()[0]}")
                time.sleep(0.25)

        # Spikes hängen am Plattform-Epic.
        platform = self.issues_by_key.get("epic-G")
        if platform:
            for spike in self.backlog.get("spikes", []):
                child = self.issues_by_key.get(spike["key"])
                if not child or self.dry_run:
                    continue
                try:
                    self.gh.graphql(mutation, {"parent": platform["node_id"],
                                               "child": child["node_id"]})
                    Out.ok(f"{spike['key']} → Epic G")
                except GitHubError as exc:
                    if "already" in str(exc).lower():
                        Out.skip(f"{spike['key']} → Epic G (bestand bereits)")
                    else:
                        Out.warn(f"{spike['key']}: {str(exc).splitlines()[0]}")
                time.sleep(0.25)

    # ── Projects v2 ─────────────────────────────────────────────────────────
    def seed_project(self) -> None:
        cfg = self.backlog.get("project")
        if not cfg:
            return
        Out.step(f"Projekt-Board «{cfg['title']}»")
        if self.dry_run:
            Out.ok(f"[dry-run] Board mit {len(cfg['fields'])} Feldern und "
                   f"{len(self.issues_by_key)} Items")
            return

        try:
            owner_id, is_org = self._owner_id()
            project_id = self._find_or_create_project(cfg, owner_id, is_org)
            fields = self._ensure_fields(project_id, cfg["fields"])
            self._add_items(project_id, fields, cfg)
        except GitHubError as exc:
            Out.fail("Projekt-Board fehlgeschlagen. Issues und Labels sind trotzdem angelegt.")
            Out.warn("Fehlt vermutlich der Scope:  gh auth refresh -s project,read:project")
            Out.warn(str(exc).splitlines()[0])

    def _owner_id(self) -> tuple[str, bool]:
        data = self.gh.graphql(
            "query($login: String!) { repositoryOwner(login: $login) { id __typename } }",
            {"login": self.owner}, write=False)
        owner = data["repositoryOwner"]
        return owner["id"], owner["__typename"] == "Organization"

    def _find_or_create_project(self, cfg: dict, owner_id: str, is_org: bool) -> str:
        field = "organization" if is_org else "user"
        query = f"""
        query($login: String!) {{
          {field}(login: $login) {{
            projectsV2(first: 50) {{ nodes {{ id title }} }}
          }}
        }}
        """
        data = self.gh.graphql(query, {"login": self.owner}, write=False)
        for node in data[field]["projectsV2"]["nodes"]:
            if node["title"] == cfg["title"]:
                Out.skip(f"Board «{cfg['title']}» besteht bereits")
                return node["id"]

        repo_data = self.gh.graphql(
            "query($o: String!, $n: String!) { repository(owner: $o, name: $n) { id } }",
            {"o": self.owner, "n": self.name}, write=False)
        created = self.gh.graphql(
            """
            mutation($owner: ID!, $title: String!, $repo: ID!) {
              createProjectV2(input: {ownerId: $owner, title: $title, repositoryId: $repo}) {
                projectV2 { id }
              }
            }
            """,
            {"owner": owner_id, "title": cfg["title"], "repo": repo_data["repository"]["id"]})
        Out.ok(f"Board «{cfg['title']}» angelegt")
        return created["createProjectV2"]["projectV2"]["id"]

    def _project_fields(self, project_id: str) -> dict[str, dict]:
        data = self.gh.graphql(
            """
            query($id: ID!) {
              node(id: $id) {
                ... on ProjectV2 {
                  fields(first: 50) {
                    nodes {
                      ... on ProjectV2Field { id name dataType }
                      ... on ProjectV2SingleSelectField {
                        id name dataType options { id name }
                      }
                    }
                  }
                }
              }
            }
            """,
            {"id": project_id}, write=False)
        out = {}
        for node in data["node"]["fields"]["nodes"]:
            if node:
                out[node["name"]] = node
        return out

    def _ensure_fields(self, project_id: str, wanted: list[dict]) -> dict[str, dict]:
        fields = self._project_fields(project_id)
        for spec in wanted:
            name = spec["name"]
            if name in fields:
                Out.skip(f"Feld «{name}» besteht bereits")
                continue
            variables: dict[str, Any] = {
                "project": project_id,
                "name": name,
                "dataType": spec["dataType"],
            }
            if spec["dataType"] == "SINGLE_SELECT":
                variables["options"] = [
                    {"name": opt,
                     "color": SELECT_COLORS[i % len(SELECT_COLORS)],
                     "description": ""}
                    for i, opt in enumerate(spec["options"])
                ]
                mutation = """
                mutation($project: ID!, $name: String!, $dataType: ProjectV2CustomFieldType!,
                         $options: [ProjectV2SingleSelectFieldOptionInput!]) {
                  createProjectV2Field(input: {
                    projectId: $project, name: $name,
                    dataType: $dataType, singleSelectOptions: $options
                  }) { projectV2Field { ... on ProjectV2SingleSelectField { id } } }
                }
                """
            else:
                mutation = """
                mutation($project: ID!, $name: String!, $dataType: ProjectV2CustomFieldType!) {
                  createProjectV2Field(input: {
                    projectId: $project, name: $name, dataType: $dataType
                  }) { projectV2Field { ... on ProjectV2Field { id } } }
                }
                """
            self.gh.graphql(mutation, variables)
            Out.ok(f"Feld «{name}» angelegt")
            time.sleep(0.3)
        return self._project_fields(project_id)

    def _add_items(self, project_id: str, fields: dict[str, dict], cfg: dict) -> None:
        meta = self._item_metadata()
        add = """
        mutation($project: ID!, $content: ID!) {
          addProjectV2ItemById(input: {projectId: $project, contentId: $content}) {
            item { id }
          }
        }
        """
        added = 0
        for key, issue in self.issues_by_key.items():
            if not issue.get("node_id"):
                continue
            try:
                res = self.gh.graphql(add, {"project": project_id,
                                            "content": issue["node_id"]})
                item_id = res["addProjectV2ItemById"]["item"]["id"]
            except GitHubError as exc:
                Out.warn(f"{key}: {str(exc).splitlines()[0]}")
                continue

            values = dict(cfg.get("defaults", {}))
            values.update(meta.get(key, {}))
            for field_name, value in values.items():
                self._set_field(project_id, item_id, fields.get(field_name), value)
            added += 1
            time.sleep(0.25)
        Out.ok(f"{added} Items im Board")

    def _item_metadata(self) -> dict[str, dict]:
        """key → {Feldname: Wert} für die Projektfelder."""
        meta: dict[str, dict] = {}
        for epic in self.backlog["epics"]:
            meta[f"epic-{epic['key']}"] = {"Epic": epic.get("projectEpic")}
            for story in epic["stories"]:
                meta[story["key"]] = {
                    "Epic": epic.get("projectEpic"),
                    "Priorität": PRIORITY_FIELD.get(story.get("priority", "")),
                    "Schätzung": story.get("points"),
                }
        for spike in self.backlog.get("spikes", []):
            meta[spike["key"]] = {
                "Epic": "G Platform",
                "Priorität": PRIORITY_FIELD.get(spike.get("priority", "")),
                "Schätzung": spike.get("points"),
            }
        return meta

    def _set_field(self, project_id: str, item_id: str,
                   field: dict | None, value: Any) -> None:
        if field is None or value is None:
            return
        if field.get("dataType") == "SINGLE_SELECT":
            option = next((o for o in field.get("options", []) if o["name"] == value), None)
            if option is None:
                return
            payload = {"singleSelectOptionId": option["id"]}
        elif field.get("dataType") == "NUMBER":
            payload = {"number": float(value)}
        else:
            payload = {"text": str(value)}

        self.gh.graphql(
            """
            mutation($project: ID!, $item: ID!, $field: ID!,
                     $value: ProjectV2FieldValue!) {
              updateProjectV2ItemFieldValue(input: {
                projectId: $project, itemId: $item, fieldId: $field, value: $value
              }) { projectV2Item { id } }
            }
            """,
            {"project": project_id, "item": item_id,
             "field": field["id"], "value": payload})


# ─────────────────────────────────────────────────────────────────────────────
#  Einstieg
# ─────────────────────────────────────────────────────────────────────────────

def summarize(backlog: dict) -> None:
    epics = backlog["epics"]
    stories = sum(len(e["stories"]) for e in epics)
    spikes = len(backlog.get("spikes", []))
    points = sum(s.get("points", 0) for e in epics for s in e["stories"])
    print(f"\nBacklog: {len(epics)} Epics · {stories} Stories · {spikes} Spikes "
          f"· {stories + spikes + len(epics)} Issues gesamt · {points} Story Points")
    for e in epics:
        must = sum(1 for s in e["stories"] if s.get("priority") == "must")
        print(f"  {e['key']}  {len(e['stories']):2d} Stories ({must} must)  {e['title']}")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="LearnWithMe-Backlog in GitHub anlegen (idempotent).")
    parser.add_argument("--repo", help="owner/repo — Default: das aktuelle Repository")
    parser.add_argument("--backlog", type=Path, default=BACKLOG_FILE)
    parser.add_argument("--dry-run", action="store_true",
                        help="nichts schreiben, nur zeigen was passieren würde")
    parser.add_argument("--only", help=f"Teilschritte, kommagetrennt: {','.join(STEPS)}")
    parser.add_argument("--no-project", action="store_true",
                        help="Projects-v2-Board überspringen")
    args = parser.parse_args()

    if not args.backlog.exists():
        Out.fail(f"Backlog-Datei nicht gefunden: {args.backlog}")
        return 1
    try:
        backlog = json.loads(args.backlog.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        Out.fail(f"backlog.json ist kein gültiges JSON: {exc}")
        return 1

    summarize(backlog)

    try:
        gh = Gh(dry_run=args.dry_run)
        repo = args.repo or gh.current_repo()
    except GitHubError as exc:
        Out.fail(str(exc))
        return 1

    steps = [s.strip() for s in args.only.split(",")] if args.only else list(STEPS)
    if args.no_project and "project" in steps:
        steps.remove("project")
    unknown = [s for s in steps if s not in STEPS]
    if unknown:
        Out.fail(f"Unbekannte Schritte: {', '.join(unknown)} — erlaubt: {', '.join(STEPS)}")
        return 1

    mode = " (DRY RUN — es wird nichts geschrieben)" if args.dry_run else ""
    print(f"\nZiel-Repository: {repo}{mode}")

    seeder = Seeder(gh, repo, backlog, args.dry_run)
    actions = {
        "labels": seeder.seed_labels,
        "milestones": seeder.seed_milestones,
        "epics": seeder.seed_epics,
        "stories": seeder.seed_stories,
        "spikes": seeder.seed_spikes,
        "subissues": seeder.link_subissues,
        "project": seeder.seed_project,
    }
    try:
        for step in STEPS:
            if step in steps:
                actions[step]()
    except GitHubError as exc:
        Out.fail(str(exc))
        return 1
    except KeyboardInterrupt:
        Out.warn("Abgebrochen. Ein erneuter Lauf setzt dort fort, wo es aufgehört hat.")
        return 130

    print("\nFertig." if not args.dry_run else "\nDry run beendet — nichts verändert.")
    if not args.dry_run:
        print(f"  Issues:   https://github.com/{repo}/issues")
        print(f"  Meilenst: https://github.com/{repo}/milestones")
    return 0


if __name__ == "__main__":
    sys.exit(main())
