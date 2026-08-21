import { useEffect } from "react";
import { useTranslation } from "../../i18n";

interface TrueFalseItemProps {
  statement: string;
  selected: boolean | null;
  onChange: (value: boolean) => void;
  disabled: boolean;
  correctAnswer?: boolean;
}

/** F1: dieselbe "1/2 wählt Option"-Tastaturbedienung wie [McChoice] — die Optionen zeigen die
 * gleichen nummerierten Badges, sollten also auch gleich reagieren. */
export default function TrueFalseItem({ statement, selected, onChange, disabled, correctAnswer }: TrueFalseItemProps) {
  const { t } = useTranslation();
  const showResult = disabled && correctAnswer !== undefined;

  useEffect(() => {
    if (disabled) return;
    function handleKeyDown(e: KeyboardEvent) {
      const target = e.target as HTMLElement;
      if (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.tagName === "SELECT") return;
      if (e.key === "1") onChange(true);
      else if (e.key === "2") onChange(false);
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [disabled, onChange]);

  function classFor(value: boolean) {
    if (!showResult) return selected === value ? "is-selected" : "";
    if (value === correctAnswer) return "is-correct";
    if (selected === value) return "is-incorrect";
    return "";
  }

  return (
    <div className="stack">
      <p>{statement}</p>
      <ul className="option-list">
        {[true, false].map((value) => (
          <li key={String(value)}>
            <button
              type="button"
              className={`option-item ${classFor(value)}`}
              aria-pressed={selected === value}
              disabled={disabled}
              onClick={() => onChange(value)}
            >
              <span className="option-key" aria-hidden="true">
                {value ? 1 : 2}
              </span>
              <span>{value ? t("item.trueFalse.true") : t("item.trueFalse.false")}</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
