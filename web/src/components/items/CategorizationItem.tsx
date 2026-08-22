import { useTranslation } from "../../i18n";
import type { CategorizationPayload } from "./types";

interface CategorizationItemProps {
  payload: CategorizationPayload;
  assignments: Record<string, string>;
  onChange: (assignments: Record<string, string>) => void;
  disabled: boolean;
}

/** Wie `MatchingItem`: natives `<select>` je Element statt Drag&Drop — vollständig
 * tastaturbedienbar (F1/F5). `payload.elements[].bucketId` ist die Lösung und kommt beim Client
 * nie an (Härtung, `SessionController.stripAnswerFields`). */
export default function CategorizationItem({ payload, assignments, onChange, disabled }: CategorizationItemProps) {
  const { t } = useTranslation();

  function setBucket(elementId: string, bucketId: string) {
    onChange({ ...assignments, [elementId]: bucketId });
  }

  return (
    <div className="stack">
      <p>{t("item.categorization.instructions")}</p>
      <div className="stack">
        {payload.elements.map((element) => (
          <div key={element.id} className="row" style={{ justifyContent: "space-between" }}>
            <label htmlFor={`category-${element.id}`}>{element.text}</label>
            <select
              id={`category-${element.id}`}
              disabled={disabled}
              value={assignments[element.id] ?? ""}
              onChange={(e) => setBucket(element.id, e.target.value)}
            >
              <option value="" disabled>
                {t("item.categorization.unassigned")}
              </option>
              {payload.buckets.map((bucket) => (
                <option key={bucket.id} value={bucket.id}>
                  {bucket.label}
                </option>
              ))}
            </select>
          </div>
        ))}
      </div>
    </div>
  );
}
