import { useTranslation } from "../../i18n";
import type { ShortAnswerPayload } from "./types";

interface ShortAnswerItemProps {
  payload: ShortAnswerPayload;
  value: string;
  onChange: (value: string) => void;
  disabled: boolean;
}

/** Epic H (E4): Freitextantwort — im Gegensatz zu allen anderen Fragetypen kein Sofort-Feedback
 * (§6.5: LLM-Rubric-Bewertung läuft asynchron, kein LLM im Antwort-kritischen Pfad, N1). Das
 * Rubric selbst wird bewusst nicht vor der Antwort gezeigt (würde die Frage trivialisieren) —
 * erst im Feedback danach (Kriterium-Punktzahlen, siehe `GradeFreeTextJobHandler`). */
export default function ShortAnswerItem({ value, onChange, disabled }: ShortAnswerItemProps) {
  const { t } = useTranslation();
  return (
    <div className="stack">
      <label className="visually-hidden" htmlFor="short-answer-response">
        {t("item.shortAnswer.label")}
      </label>
      <textarea
        id="short-answer-response"
        rows={5}
        disabled={disabled}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={t("item.shortAnswer.placeholder")}
      />
      {disabled && <p style={{ color: "var(--color-text-muted)" }}>{t("item.shortAnswer.grading")}</p>}
    </div>
  );
}
