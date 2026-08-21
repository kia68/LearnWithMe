import { useTranslation } from "../../i18n";
import type { OrderingPayload } from "./types";

interface OrderingItemProps {
  payload: OrderingPayload;
  order: string[];
  onChange: (order: string[]) => void;
  disabled: boolean;
}

/** Auf-/Abwärts-Buttons statt Drag&Drop — per Definition mit Tastatur bedienbar (F1/F5), kein
 * Pointer-only-Interaktionsmuster. */
export default function OrderingItem({ payload, order, onChange, disabled }: OrderingItemProps) {
  const { t } = useTranslation();
  const elementsById = new Map(payload.elements.map((element) => [element.id, element]));

  function move(index: number, delta: number) {
    const target = index + delta;
    if (target < 0 || target >= order.length) return;
    const next = [...order];
    [next[index], next[target]] = [next[target], next[index]];
    onChange(next);
  }

  return (
    <div className="stack">
      <p>{t("item.ordering.instructions")}</p>
      <ol className="option-list" style={{ listStyleType: "decimal", paddingLeft: "1.5rem" }}>
        {order.map((id, index) => (
          <li key={id} className="option-item" style={{ justifyContent: "space-between" }}>
            <span>{elementsById.get(id)?.text ?? id}</span>
            <span className="row">
              <button
                type="button"
                className="btn"
                disabled={disabled || index === 0}
                onClick={() => move(index, -1)}
                aria-label={`${elementsById.get(id)?.text ?? id} nach oben`}
              >
                ↑
              </button>
              <button
                type="button"
                className="btn"
                disabled={disabled || index === order.length - 1}
                onClick={() => move(index, 1)}
                aria-label={`${elementsById.get(id)?.text ?? id} nach unten`}
              >
                ↓
              </button>
            </span>
          </li>
        ))}
      </ol>
    </div>
  );
}
