import { Fragment } from "react";
import { useTranslation } from "../../i18n";
import type { ClozePayload } from "./types";

interface ClozeItemProps {
  payload: ClozePayload;
  answers: string[];
  onChange: (answers: string[]) => void;
  disabled: boolean;
}

/** Rendert `template` (`{{1}}`, `{{2}}`, ...) als Fließtext mit eingebetteten Text-Feldern. */
export default function ClozeItem({ payload, answers, onChange, disabled }: ClozeItemProps) {
  const { t } = useTranslation();
  const parts = payload.template.split(/(\{\{\d+}})/g);

  function setAnswer(index: number, value: string) {
    const next = [...answers];
    next[index] = value;
    onChange(next);
  }

  return (
    <div className="stack">
      <p>{t("item.cloze.instructions")}</p>
      <p style={{ lineHeight: 2.4 }}>
        {parts.map((part, partIndex) => {
          const match = part.match(/^\{\{(\d+)}}$/);
          if (!match) return <Fragment key={partIndex}>{part}</Fragment>;
          const blankIndex = Number(match[1]) - 1;
          return (
            <Fragment key={partIndex}>
              <label className="visually-hidden" htmlFor={`blank-${blankIndex}`}>
                Lücke {blankIndex + 1}
              </label>
              <input
                id={`blank-${blankIndex}`}
                type="text"
                style={{ width: 140, display: "inline-block", margin: "0 4px" }}
                disabled={disabled}
                value={answers[blankIndex] ?? ""}
                onChange={(e) => setAnswer(blankIndex, e.target.value)}
              />
            </Fragment>
          );
        })}
      </p>
    </div>
  );
}
