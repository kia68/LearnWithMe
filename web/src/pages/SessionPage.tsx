import type { components } from "@learnwithme/api-client";
import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { api, authFetch } from "../api/client";
import FeedbackPanel from "../components/FeedbackPanel";
import ItemRenderer from "../components/items/ItemRenderer";
import type { ItemResponseBody } from "../components/items/types";
import { useTranslation, type TranslationKey } from "../i18n";

type NextItemResponse = components["schemas"]["NextItemResponse"];
type SubmitAttemptResponse = components["schemas"]["SubmitAttemptResponse"];
type FinishSessionResponse = components["schemas"]["FinishSessionResponse"];

/** Epic H: noch nicht im generierten Client (siehe items/types.ts-Kommentar) — von Hand passend
 * zu `assessment.internal.web.dto.PendingAttemptResponse`/`GradeStatusResponse` nachgezogen. */
interface PendingAttemptResponse {
  next: NextItemResponse | null;
}
interface GradeStatusResponse {
  status: "PENDING" | "GRADED";
  outcome: string | null;
  score: number | null;
}

/**
 * D1-D9/E1-E2/E6: die Lernschleife. `GET /sessions/{id}/next` statt des im Start-Response
 * mitgelieferten Items — funktioniert damit auch nach einem Seiten-Reload (D8: seiteneffektfreier
 * Peek), nicht nur direkt nach `SourceDetailPage`s Start-Mutation.
 */
export default function SessionPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const { t } = useTranslation();
  const navigate = useNavigate();

  const [currentItem, setCurrentItem] = useState<NextItemResponse | null>(null);
  const [response, setResponse] = useState<ItemResponseBody | null>(null);
  const [result, setResult] = useState<SubmitAttemptResponse | null>(null);
  const [summary, setSummary] = useState<FinishSessionResponse | null>(null);
  const [attemptCount, setAttemptCount] = useState(0);
  const [isLoading, setLoading] = useState(true);
  const [isSubmitting, setSubmitting] = useState(false);
  const itemStartedAt = useRef(Date.now());

  // Epic H: SHORT_ANSWER hat kein synchrones `result` — die LLM-Rubric-Bewertung läuft async
  // (§6.5, N1). `pendingGradeItemId` trackt das zuletzt async eingereichte Item, während der
  // Nutzer (optimistisch, ohne zu warten) schon beim nächsten Item ist; `pendingGradeOutcome`
  // zeigt das Ergebnis kurz an, sobald das Polling es findet.
  const [pendingGradeItemId, setPendingGradeItemId] = useState<string | null>(null);
  const [pendingGradeOutcome, setPendingGradeOutcome] = useState<GradeStatusResponse | null>(null);

  useEffect(() => {
    if (!sessionId || !pendingGradeItemId) return;
    let cancelled = false;
    let attempts = 0;
    const interval = setInterval(() => {
      attempts += 1;
      void authFetch(`/api/v1/sessions/${sessionId}/items/${pendingGradeItemId}/grade`).then(async (res) => {
        if (cancelled || !res.ok) return;
        const status = (await res.json()) as GradeStatusResponse;
        if (status.status === "GRADED") {
          setPendingGradeOutcome(status);
          setPendingGradeItemId(null);
        } else if (attempts >= 30) {
          // Nach ~75s aufgeben statt endlos zu pollen (z.B. Job dauerhaft fehlgeschlagen) —
          // die Antwort bleibt trotzdem gespeichert, nur ohne UI-Rückmeldung hier.
          setPendingGradeItemId(null);
        }
      });
    }, 2500);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [sessionId, pendingGradeItemId]);

  useEffect(() => {
    if (!sessionId) return;
    api.GET("/api/v1/sessions/{id}/next", { params: { path: { id: sessionId } } }).then(({ data }) => {
      setCurrentItem(data ?? null);
      itemStartedAt.current = Date.now();
      setLoading(false);
    });
  }, [sessionId]);

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key !== "Enter") return;
      const target = e.target as HTMLElement;
      if (target.tagName === "TEXTAREA" || (target.tagName === "INPUT" && (target as HTMLInputElement).type === "text")) return;
      e.preventDefault();
      if (result) advance();
      else if (response) void submit();
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [result, response]);

  async function submit() {
    if (!sessionId || !currentItem || !response || isSubmitting) return;
    setSubmitting(true);
    const elapsedMs = Date.now() - itemStartedAt.current;
    const submittedItemId = currentItem.itemId;
    const { data, response: httpResponse } = await api.POST("/api/v1/sessions/{id}/attempts", {
      params: { path: { id: sessionId } },
      body: { itemId: submittedItemId, response, elapsedMs },
    });
    setSubmitting(false);
    setAttemptCount((c) => c + 1);

    if (httpResponse.status === 202) {
      // Epic H: SHORT_ANSWER — kein Feedback jetzt, `next` kommt trotzdem sofort (optimistisches
      // UI). `data` ist hier eigentlich ein PendingAttemptResponse, im (noch nicht regenerierten)
      // Client-Typ aber als SubmitAttemptResponse deklariert — s. Kommentar oben.
      setPendingGradeItemId(submittedItemId);
      setPendingGradeOutcome(null);
      advanceTo((data as unknown as PendingAttemptResponse | undefined)?.next ?? null);
      return;
    }
    if (data) setResult(data);
  }

  async function skip() {
    if (!sessionId || !currentItem || isSubmitting) return;
    setSubmitting(true);
    const { data } = await api.POST("/api/v1/sessions/{id}/skip", {
      params: { path: { id: sessionId } },
      body: { itemId: currentItem.itemId },
    });
    setSubmitting(false);
    advanceTo(data?.next ?? null);
  }

  function advance() {
    advanceTo(result?.next ?? null);
  }

  function advanceTo(next: NextItemResponse | null) {
    setResult(null);
    setResponse(null);
    setCurrentItem(next);
    itemStartedAt.current = Date.now();
    if (!next) void finish();
  }

  async function finish() {
    if (!sessionId) return;
    const { data } = await api.POST("/api/v1/sessions/{id}/finish", { params: { path: { id: sessionId } } });
    if (data) setSummary(data);
  }

  if (!sessionId) return null;
  if (isLoading) return <p>{t("common.loading")}</p>;

  if (summary) {
    return (
      <div className="stack card">
        <h1>{t("session.finished")}</h1>
        <p>{t("session.summary", { count: summary.attemptCount, accuracy: Math.round(summary.accuracy * 100) })}</p>
        <button className="btn btn-primary" onClick={() => navigate("/")}>
          {t("nav.library")}
        </button>
      </div>
    );
  }

  if (!currentItem) {
    return (
      <div className="stack card">
        <p>{t("session.noItems")}</p>
        <button className="btn btn-primary" onClick={() => void finish()}>
          {t("session.finish")}
        </button>
      </div>
    );
  }

  return (
    <div className="stack">
      <p className="visually-hidden" aria-live="polite">
        {t("session.keyboardHint")}
      </p>
      {(pendingGradeItemId || pendingGradeOutcome) && (
        <p className="badge" role="status" aria-live="polite">
          {pendingGradeItemId && t("session.pendingGrade")}
          {pendingGradeOutcome && t(gradeOutcomeKey(pendingGradeOutcome.outcome), { score: Math.round((pendingGradeOutcome.score ?? 0) * 100) })}
        </p>
      )}
      <div className="row" style={{ justifyContent: "space-between" }}>
        <span className="badge">{currentItem.type}</span>
        <span className="badge">#{attemptCount + 1}</span>
        <span className="badge">{Math.round(currentItem.meta.expectedSuccess * 100)}%</span>
      </div>
      <h1>{currentItem.stem}</h1>

      <ItemRenderer
        type={currentItem.type}
        payload={currentItem.payload}
        disabled={result !== null}
        correctResponse={result?.feedback.correctResponse}
        onResponseChange={setResponse}
      />

      {result && <FeedbackPanel result={result} />}

      <div className="row">
        {!result && (
          <>
            <button className="btn btn-primary" onClick={() => void submit()} disabled={!response || isSubmitting}>
              {t("session.submit")}
            </button>
            <button className="btn" onClick={() => void skip()} disabled={isSubmitting}>
              {t("session.skip")}
            </button>
          </>
        )}
        {result && (
          <button className="btn btn-primary" onClick={advance} autoFocus>
            {t("session.next")}
          </button>
        )}
      </div>
    </div>
  );
}

function gradeOutcomeKey(outcome: string | null): TranslationKey {
  if (outcome === "CORRECT") return "session.gradedCorrect";
  if (outcome === "PARTIAL") return "session.gradedPartial";
  return "session.gradedIncorrect";
}
