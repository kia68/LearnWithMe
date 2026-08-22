import type { components } from "@learnwithme/api-client";
import { useTranslation } from "../../i18n";

type ItemResponse = components["schemas"]["ItemResponse"];

/**
 * Dozenten-Review-Workflow (C7-Frontend): anders als `ItemRenderer` (Lernenden-Ansicht, Antworten
 * absichtlich gefiltert — Härtung nach Epic H) zeigt diese Komponente ALLES inklusive der Lösung,
 * denn genau das braucht ein Reviewer, um die Qualität eines generierten Items zu beurteilen.
 * Bewusst eine reine Textzusammenfassung statt der interaktiven Lern-Widgets wiederzuverwenden —
 * ein Reviewer beantwortet die Frage nicht, er liest sie.
 */
export default function ReviewItemPayload({ item }: { item: ItemResponse }) {
  const { t } = useTranslation();
  const payload = item.payload as Record<string, unknown>;

  switch (item.type) {
    case "MC_SINGLE":
    case "MC_MULTI": {
      const options = (payload.options as { id: string; text: string; correct: boolean; rationale: string; misconceptionCategory?: string }[]) ?? [];
      return (
        <ul className="stack" style={{ margin: 0, paddingLeft: "1.25rem" }}>
          {options.map((o) => (
            <li key={o.id} style={{ fontWeight: o.correct ? 700 : 400 }}>
              {o.correct ? "✓ " : "✗ "}
              {o.text}
              <span style={{ color: "var(--color-text-muted)" }}> — {o.rationale}</span>
              {o.misconceptionCategory && <span className="badge" style={{ marginLeft: 6 }}>{o.misconceptionCategory}</span>}
            </li>
          ))}
        </ul>
      );
    }
    case "TRUE_FALSE":
      return (
        <p>
          {String(payload.statement)} → <strong>{payload.answer ? t("item.trueFalse.true") : t("item.trueFalse.false")}</strong>
          <br />
          <span style={{ color: "var(--color-text-muted)" }}>{String(payload.rationale)}</span>
        </p>
      );
    case "ORDERING": {
      const elements = (payload.elements as { id: string; text: string }[]) ?? [];
      const correctOrder = (payload.correctOrder as string[]) ?? [];
      const byId = new Map(elements.map((e) => [e.id, e.text]));
      return (
        <ol style={{ margin: 0, paddingLeft: "1.25rem" }}>
          {correctOrder.map((id) => (
            <li key={id}>{byId.get(id) ?? id}</li>
          ))}
        </ol>
      );
    }
    case "MATCHING": {
      const left = (payload.left as { id: string; text: string }[]) ?? [];
      const right = (payload.right as { id: string; text: string }[]) ?? [];
      const pairs = (payload.pairs as { leftId: string; rightId: string }[]) ?? [];
      const leftById = new Map(left.map((e) => [e.id, e.text]));
      const rightById = new Map(right.map((e) => [e.id, e.text]));
      return (
        <ul style={{ margin: 0, paddingLeft: "1.25rem" }}>
          {pairs.map((p) => (
            <li key={p.leftId}>
              {leftById.get(p.leftId) ?? p.leftId} → {rightById.get(p.rightId) ?? p.rightId}
            </li>
          ))}
        </ul>
      );
    }
    case "CLOZE": {
      const blanks = (payload.blanks as { accepted: string[] }[]) ?? [];
      return (
        <div className="stack">
          <p style={{ fontFamily: "monospace" }}>{String(payload.template)}</p>
          <ul style={{ margin: 0, paddingLeft: "1.25rem" }}>
            {blanks.map((b, i) => (
              <li key={i}>
                {`{{${i + 1}}}`}: {b.accepted.join(" / ")}
              </li>
            ))}
          </ul>
        </div>
      );
    }
    case "SHORT_ANSWER": {
      const rubric = (payload.rubric as { criterion: string; points: number }[]) ?? [];
      return (
        <div className="stack">
          <p>
            <em>{String(payload.referenceAnswer)}</em>
          </p>
          <ul style={{ margin: 0, paddingLeft: "1.25rem" }}>
            {rubric.map((r, i) => (
              <li key={i}>
                {r.criterion} ({r.points} {t("review.points")})
              </li>
            ))}
          </ul>
        </div>
      );
    }
    case "NUMERIC":
      return (
        <p>
          {String(payload.value)} ± {String(payload.tolerance)} {payload.unit != null ? String(payload.unit) : ""}
        </p>
      );
    case "CATEGORIZATION": {
      const buckets = (payload.buckets as { id: string; label: string }[]) ?? [];
      const elements = (payload.elements as { id: string; text: string; bucketId: string }[]) ?? [];
      return (
        <div className="stack">
          {buckets.map((b) => (
            <p key={b.id}>
              <strong>{b.label}:</strong> {elements.filter((e) => e.bucketId === b.id).map((e) => e.text).join(", ")}
            </p>
          ))}
        </div>
      );
    }
    case "CODE_OUTPUT":
      return (
        <div className="stack">
          <pre className="card" style={{ overflowX: "auto", margin: 0 }}>
            <code>{String(payload.snippet)}</code>
          </pre>
          <p>
            → <strong>{String(payload.expected)}</strong>
          </p>
        </div>
      );
    default:
      return <pre>{JSON.stringify(payload, null, 2)}</pre>;
  }
}
