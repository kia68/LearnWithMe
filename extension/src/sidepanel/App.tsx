import type { components } from "@learnwithme/api-client";
import { useEffect, useRef, useState } from "react";
import { api, getStoredRefreshToken, login, logout, tryRestoreSession } from "./api";
import { detectLocale, translate, type Locale } from "./i18n";

type SourceResponse = components["schemas"]["SourceResponse"];
type NextItemResponse = components["schemas"]["NextItemResponse"];
type SubmitAttemptResponse = components["schemas"]["SubmitAttemptResponse"];

/** Epic H: `POST attempts` kann jetzt auch `PendingAttemptResponse` (202, `SHORT_ANSWER`) liefern —
 * das Side-Panel unterstützt `SHORT_ANSWER` inline aber ohnehin nicht (`isSupported`-Gate unten,
 * siehe docs/progress.md „Bekannte Lücken" Epic H), dieser Zweig ist zur Laufzeit also unerreichbar,
 * braucht aber eine Typ-Engführung, seit der generierte Client beide Formen unterscheidet. */
function isSubmitAttemptResponse(data: SubmitAttemptResponse | components["schemas"]["PendingAttemptResponse"]): data is SubmitAttemptResponse {
  return "attemptId" in data;
}

interface CapturedSelection {
  html: string;
  text: string;
  url: string;
  title: string;
}

const WEB_APP_URL = "http://localhost:5173";

export default function App() {
  const locale = useRef<Locale>(detectLocale()).current;
  const t = (key: Parameters<typeof translate>[1]) => translate(locale, key);

  const [isAuthenticated, setAuthenticated] = useState<boolean | null>(null);
  const [sessionId, setSessionId] = useState<string | null>(null);

  useEffect(() => {
    if (!getStoredRefreshToken()) {
      setAuthenticated(false);
      return;
    }
    void tryRestoreSession().then(setAuthenticated);
  }, []);

  if (isAuthenticated === null) return <p className="hint">{t("common.loading")}</p>;
  if (!isAuthenticated) return <LoginView t={t} onLoggedIn={() => setAuthenticated(true)} />;
  if (sessionId) return <SessionView t={t} sessionId={sessionId} onExit={() => setSessionId(null)} />;
  return <LibraryView t={t} onStartSession={setSessionId} onLoggedOut={() => setAuthenticated(false)} />;
}

function LoginView({ t, onLoggedIn }: { t: (k: Parameters<typeof translate>[1]) => string; onLoggedIn: () => void }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(false);
  const [isSubmitting, setSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(false);
    const ok = await login(email, password);
    setSubmitting(false);
    if (ok) onLoggedIn();
    else setError(true);
  }

  return (
    <form className="stack" onSubmit={(e) => void handleSubmit(e)}>
      <h1>{t("auth.title")}</h1>
      <label htmlFor="email">{t("auth.email")}</label>
      <input id="email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
      <label htmlFor="password">{t("auth.password")}</label>
      <input id="password" type="password" required value={password} onChange={(e) => setPassword(e.target.value)} />
      {error && (
        <p className="banner banner-error" role="alert">
          {t("auth.error")}
        </p>
      )}
      <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
        {t("auth.submit")}
      </button>
      <p className="hint">{t("auth.hint")}</p>
    </form>
  );
}

function LibraryView({
  t,
  onStartSession,
  onLoggedOut,
}: {
  t: (k: Parameters<typeof translate>[1]) => string;
  onStartSession: (sessionId: string) => void;
  onLoggedOut: () => void;
}) {
  const [pendingSelection, setPendingSelection] = useState<CapturedSelection | null>(null);
  const [currentTab, setCurrentTab] = useState<{ url: string; title: string } | null>(null);
  const [sources, setSources] = useState<SourceResponse[] | null>(null);
  const [importMessage, setImportMessage] = useState<string | null>(null);
  const [isStarting, setStarting] = useState<string | null>(null);

  useEffect(() => {
    void chrome.storage.session.get("pendingSelection").then((result) => {
      const selection = result.pendingSelection as CapturedSelection | undefined;
      if (selection?.text) setPendingSelection(selection);
    });
    void chrome.tabs.query({ active: true, currentWindow: true }).then(([tab]) => {
      if (tab?.url && tab.url.startsWith("http")) setCurrentTab({ url: tab.url, title: tab.title ?? tab.url });
    });
    void refreshSources();
  }, []);

  async function refreshSources() {
    const { data } = await api.GET("/api/v1/sources", {});
    setSources(data?.items ?? []);
  }

  async function importSelection() {
    if (!pendingSelection) return;
    await api.POST("/api/v1/sources", {
      body: { html: pendingSelection.html, url: pendingSelection.url, title: pendingSelection.title },
    });
    await chrome.storage.session.remove("pendingSelection");
    setPendingSelection(null);
    setImportMessage(t("selection.success"));
    await refreshSources();
  }

  async function importCurrentPage() {
    if (!currentTab) return;
    await api.POST("/api/v1/sources", { body: { url: currentTab.url, title: currentTab.title } });
    setImportMessage(t("selection.success"));
    await refreshSources();
  }

  async function startSession(sourceId: string) {
    setStarting(sourceId);
    const { data } = await api.POST("/api/v1/sessions", {
      body: { scopeKind: "SOURCE", scopeId: sourceId, goalKind: "ITEM_COUNT", goalValue: 5 },
    });
    setStarting(null);
    if (data) onStartSession(data.sessionId);
  }

  return (
    <div className="stack">
      <div className="row" style={{ justifyContent: "flex-end" }}>
        <button
          type="button"
          className="btn-link"
          onClick={() => {
            logout();
            onLoggedOut();
          }}
        >
          {t("nav.logout")}
        </button>
      </div>

      {importMessage && (
        <p className="banner banner-success" role="status">
          {importMessage}
        </p>
      )}

      {pendingSelection && (
        <section className="card stack">
          <h2>{t("selection.title")}</h2>
          <blockquote>“{pendingSelection.text.slice(0, 200)}{pendingSelection.text.length > 200 ? "…" : ""}”</blockquote>
          <button type="button" className="btn btn-primary" onClick={() => void importSelection()}>
            {t("selection.cta")}
          </button>
        </section>
      )}

      <section className="card stack">
        <h2>{t("page.title")}</h2>
        {currentTab ? (
          <>
            <p className="hint">{currentTab.title}</p>
            <button type="button" className="btn" onClick={() => void importCurrentPage()}>
              {t("page.cta")}
            </button>
          </>
        ) : (
          <p className="hint">{t("page.unavailable")}</p>
        )}
      </section>

      <section className="card stack">
        <h2>{t("library.title")}</h2>
        {sources === null && <p className="hint">{t("common.loading")}</p>}
        {sources?.length === 0 && <p className="hint">{t("library.empty")}</p>}
        <ul className="stack">
          {sources?.map((source) => (
            <li key={source.id} className="row" style={{ justifyContent: "space-between" }}>
              <div>
                <strong>{source.title}</strong>
                <span className="badge">{t(`library.status.${source.status}` as Parameters<typeof translate>[1])}</span>
              </div>
              {source.status === "READY" && (
                <button type="button" className="btn" disabled={isStarting === source.id} onClick={() => void startSession(source.id)}>
                  {t("library.startSession")}
                </button>
              )}
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}

function SessionView({
  t,
  sessionId,
  onExit,
}: {
  t: (k: Parameters<typeof translate>[1]) => string;
  sessionId: string;
  onExit: () => void;
}) {
  const [currentItem, setCurrentItem] = useState<NextItemResponse | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [boolAnswer, setBoolAnswer] = useState<boolean | null>(null);
  const [result, setResult] = useState<SubmitAttemptResponse | null>(null);
  const [finished, setFinished] = useState(false);
  const itemStartedAt = useRef(Date.now());

  useEffect(() => {
    void api.GET("/api/v1/sessions/{id}/next", { params: { path: { id: sessionId } } }).then(({ data }) => {
      setCurrentItem(data ?? null);
      itemStartedAt.current = Date.now();
    });
  }, [sessionId]);

  const isSupported = currentItem && ["MC_SINGLE", "MC_MULTI", "TRUE_FALSE"].includes(currentItem.type);

  function buildResponse(): unknown {
    if (!currentItem) return null;
    if (currentItem.type === "MC_SINGLE") return { optionId: selected[0] };
    if (currentItem.type === "MC_MULTI") return { optionIds: selected };
    if (currentItem.type === "TRUE_FALSE") return { answer: boolAnswer };
    return null;
  }

  async function submit() {
    if (!currentItem) return;
    const response = buildResponse();
    if (response == null) return;
    const { data } = await api.POST("/api/v1/sessions/{id}/attempts", {
      params: { path: { id: sessionId } },
      body: { itemId: currentItem.itemId, response, elapsedMs: Date.now() - itemStartedAt.current },
    });
    if (data && isSubmitAttemptResponse(data)) setResult(data);
  }

  async function advance() {
    const next = result?.next ?? null;
    setResult(null);
    setSelected([]);
    setBoolAnswer(null);
    setCurrentItem(next);
    itemStartedAt.current = Date.now();
    if (!next) {
      await api.POST("/api/v1/sessions/{id}/finish", { params: { path: { id: sessionId } } });
      setFinished(true);
    }
  }

  if (finished) {
    return (
      <div className="stack card">
        <p>{t("session.finished")}</p>
        <button type="button" className="btn btn-primary" onClick={onExit}>
          {t("common.back")}
        </button>
      </div>
    );
  }

  if (!currentItem) {
    return (
      <div className="stack card">
        <p>{t("session.noItems")}</p>
        <button type="button" className="btn btn-primary" onClick={onExit}>
          {t("common.back")}
        </button>
      </div>
    );
  }

  if (!isSupported) {
    return (
      <div className="stack card">
        <p>{t("session.unsupportedType")}</p>
        <a className="btn btn-primary" href={`${WEB_APP_URL}/sessions/${sessionId}`} target="_blank" rel="noreferrer">
          {t("session.openInWeb")}
        </a>
        <button type="button" className="btn" onClick={onExit}>
          {t("common.back")}
        </button>
      </div>
    );
  }

  const payload = currentItem.payload as { options?: { id: string; text: string }[]; statement?: string };

  return (
    <div className="stack">
      <button type="button" className="btn-link" onClick={onExit}>
        {t("common.back")}
      </button>
      <h2>{currentItem.stem}</h2>

      {currentItem.type !== "TRUE_FALSE" &&
        payload.options?.map((option, index) => (
          <button
            key={option.id}
            type="button"
            className="option-item"
            aria-pressed={selected.includes(option.id)}
            disabled={result !== null}
            onClick={() => {
              if (currentItem.type === "MC_SINGLE") setSelected([option.id]);
              else setSelected((prev) => (prev.includes(option.id) ? prev.filter((id) => id !== option.id) : [...prev, option.id]));
            }}
          >
            <span className="option-key" aria-hidden="true">
              {index + 1}
            </span>
            {option.text}
          </button>
        ))}

      {currentItem.type === "TRUE_FALSE" &&
        [true, false].map((value) => (
          <button
            key={String(value)}
            type="button"
            className="option-item"
            aria-pressed={boolAnswer === value}
            disabled={result !== null}
            onClick={() => setBoolAnswer(value)}
          >
            {value ? t("item.trueFalse.true") : t("item.trueFalse.false")}
          </button>
        ))}

      {result && (
        <div className="card stack" role="status">
          <p className={`banner ${result.outcome === "CORRECT" ? "banner-success" : result.outcome === "PARTIAL" ? "banner-warning" : "banner-error"}`}>
            {t(result.outcome === "CORRECT" ? "session.correct" : result.outcome === "PARTIAL" ? "session.partial" : "session.incorrect")}
          </p>
          <p>{result.feedback.explanation}</p>
        </div>
      )}

      <div className="row">
        {!result && (
          <button type="button" className="btn btn-primary" disabled={selected.length === 0 && boolAnswer === null} onClick={() => void submit()}>
            {t("session.submit")}
          </button>
        )}
        {result && (
          <button type="button" className="btn btn-primary" onClick={() => void advance()}>
            {t("session.next")}
          </button>
        )}
      </div>
    </div>
  );
}
