import { useTranslation } from "../../i18n";
import type { MatchingPayload } from "./types";

interface MatchingItemProps {
  payload: MatchingPayload;
  pairs: Record<string, string>;
  onChange: (pairs: Record<string, string>) => void;
  disabled: boolean;
}

/** Native `<select>` je linkem Element statt Drag&Drop — vollständig tastaturbedienbar (F1/F5). */
export default function MatchingItem({ payload, pairs, onChange, disabled }: MatchingItemProps) {
  const { t } = useTranslation();
  const rightOptions = [...payload.right, ...payload.distractorsRight];

  function setPair(leftId: string, rightId: string) {
    onChange({ ...pairs, [leftId]: rightId });
  }

  return (
    <div className="stack">
      <p>{t("item.matching.instructions")}</p>
      <div className="stack">
        {payload.left.map((leftItem) => (
          <div key={leftItem.id} className="row" style={{ justifyContent: "space-between" }}>
            <label htmlFor={`match-${leftItem.id}`}>{leftItem.text}</label>
            <select
              id={`match-${leftItem.id}`}
              disabled={disabled}
              value={pairs[leftItem.id] ?? ""}
              onChange={(e) => setPair(leftItem.id, e.target.value)}
            >
              <option value="" disabled>
                {t("item.matching.unmatched")}
              </option>
              {rightOptions.map((rightItem) => (
                <option key={rightItem.id} value={rightItem.id}>
                  {rightItem.text}
                </option>
              ))}
            </select>
          </div>
        ))}
      </div>
    </div>
  );
}
