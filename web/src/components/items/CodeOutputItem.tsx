import { useTranslation } from "../../i18n";
import type { CodeOutputPayload } from "./types";

interface CodeOutputItemProps {
  payload: CodeOutputPayload;
  value: string;
  onChange: (value: string) => void;
  disabled: boolean;
}

/** M6-Nachtrag: `payload.expected` ist die Lösung und kommt beim Client nie an (Härtung,
 * `SessionController.stripAnswerFields`) — nur `snippet`/`language` werden gezeigt. Exakter,
 * groß-/kleinschreibungssensitiver Vergleich beim Grading (siehe `ResponseGrader.gradeCodeOutput`),
 * deshalb hier bewusst kein Hinweis auf Normalisierung wie bei CLOZE. */
export default function CodeOutputItem({ payload, value, onChange, disabled }: CodeOutputItemProps) {
  const { t } = useTranslation();
  return (
    <div className="stack">
      <p>{t("item.codeOutput.instructions")}</p>
      <pre className="card" style={{ overflowX: "auto" }}>
        <code>{payload.snippet}</code>
      </pre>
      <label htmlFor="code-output-response">{t("item.codeOutput.label")}</label>
      <input
        id="code-output-response"
        type="text"
        disabled={disabled}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={t("item.codeOutput.placeholder")}
      />
    </div>
  );
}
