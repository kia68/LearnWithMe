import { useTranslation } from "../../i18n";
import type { NumericPayload } from "./types";

interface NumericItemProps {
  payload: NumericPayload;
  answer: string;
  unit: string;
  onChangeAnswer: (value: string) => void;
  onChangeUnit: (value: string) => void;
  disabled: boolean;
}

/** M6-Nachtrag: `payload.unit` ist nur gesetzt, wenn die Frage eine Einheit verlangt (§10.1) —
 * das Einheitsfeld wird dann zusätzlich zum Zahlenfeld gezeigt, sonst nicht. `payload.value`
 * selbst ist die Lösung und kommt beim Client nie an (Härtung, `SessionController.stripAnswerFields`). */
export default function NumericItem({ payload, answer, unit, onChangeAnswer, onChangeUnit, disabled }: NumericItemProps) {
  const { t } = useTranslation();
  return (
    <div className="row" style={{ alignItems: "flex-end" }}>
      <div className="stack" style={{ flex: 1 }}>
        <label htmlFor="numeric-response">{t("item.numeric.label")}</label>
        <input
          id="numeric-response"
          type="number"
          inputMode="decimal"
          disabled={disabled}
          value={answer}
          onChange={(e) => onChangeAnswer(e.target.value)}
        />
      </div>
      {payload.unit != null && (
        <div className="stack" style={{ flex: 1 }}>
          <label htmlFor="numeric-unit">{t("item.numeric.unitLabel")}</label>
          <input id="numeric-unit" type="text" disabled={disabled} value={unit} onChange={(e) => onChangeUnit(e.target.value)} placeholder={payload.unit} />
        </div>
      )}
    </div>
  );
}
