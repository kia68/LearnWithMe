import type { components } from "@learnwithme/api-client";
import { useTranslation, type TranslationKey } from "../i18n";

type SubmitAttemptResponse = components["schemas"]["SubmitAttemptResponse"];

/** D4 (sofortiges Feedback + Beleg) und E1 (`errorAnalysis`). `role="status"`/`aria-live` sorgt
 * dafür, dass Screenreader das Ergebnis automatisch ansagen (F5), ohne dass der Fokus springt. */
export default function FeedbackPanel({ result }: { result: SubmitAttemptResponse }) {
  const { t } = useTranslation();
  const outcomeKey: TranslationKey = result.outcome === "CORRECT" ? "session.correct" : result.outcome === "PARTIAL" ? "session.partial" : "session.incorrect";
  const bannerClass = result.outcome === "CORRECT" ? "banner-success" : result.outcome === "PARTIAL" ? "banner-warning" : "banner-error";

  return (
    <div className="stack card" role="status" aria-live="polite">
      <p className={`banner ${bannerClass}`} style={{ margin: 0 }}>
        {t(outcomeKey)}
      </p>
      <div>
        <h3>{t("session.explanation")}</h3>
        <p>{result.feedback.explanation}</p>
        {result.feedback.chosenOptionRationale && <p><em>{result.feedback.chosenOptionRationale}</em></p>}
      </div>
      {result.feedback.evidence && (
        <div>
          <h3>{t("session.evidence")}</h3>
          <blockquote style={{ margin: 0, paddingLeft: "1rem", borderLeft: "3px solid var(--color-border)" }}>
            “{result.feedback.evidence.quote}”
            {result.feedback.evidence.page != null && <span> (S. {result.feedback.evidence.page})</span>}
          </blockquote>
        </div>
      )}
      {result.errorAnalysis && (
        <p className="badge">{t(`session.errorCategory.${result.errorAnalysis.category}` as TranslationKey)}</p>
      )}
    </div>
  );
}
