import { useEffect } from "react";
import type { components } from "@learnwithme/api-client";

type Option = components["schemas"]["Option"];

interface McChoiceProps {
  options: Option[];
  mode: "single" | "multi";
  selected: string[];
  onChange: (selected: string[]) => void;
  disabled: boolean;
  /** Nach dem Absenden bekannt — färbt richtig/falsch statt nur "ausgewählt" (F1/D4). */
  correctOptionIds?: string[];
}

/** F1: Tastaturbedienung — Zifferntasten 1-9 wählen Optionen direkt, ohne vorheriges Tabben.
 * Global auf `window` registriert (wie der Enter-Handler in `SessionPage`) statt scoped auf den
 * Options-Container: ein Klick/Tab davor wäre sonst Voraussetzung, bevor Ziffern überhaupt
 * ankommen — genau das soll die Tastaturbedienung vermeiden. */
export default function McChoice({ options, mode, selected, onChange, disabled, correctOptionIds }: McChoiceProps) {
  useEffect(() => {
    if (disabled) return;
    function handleKeyDown(e: KeyboardEvent) {
      const target = e.target as HTMLElement;
      if (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.tagName === "SELECT") return;
      const digit = Number(e.key);
      if (!Number.isInteger(digit) || digit < 1 || digit > 9) return;
      const option = options[digit - 1];
      if (!option) return;
      e.preventDefault();
      toggle(option.id);
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [options, selected, disabled]);

  function toggle(optionId: string) {
    if (disabled) return;
    if (mode === "single") {
      onChange([optionId]);
    } else {
      onChange(selected.includes(optionId) ? selected.filter((id) => id !== optionId) : [...selected, optionId]);
    }
  }

  return (
    <ul className="option-list">
      {options.map((option, index) => {
        const isSelected = selected.includes(option.id);
        const isCorrect = correctOptionIds?.includes(option.id);
        const showResult = disabled && correctOptionIds !== undefined;
        const resultClass = showResult ? (isCorrect ? "is-correct" : isSelected ? "is-incorrect" : "") : "";
        return (
          <li key={option.id}>
            <button
              type="button"
              className={`option-item ${resultClass}`}
              aria-pressed={isSelected}
              disabled={disabled}
              onClick={() => toggle(option.id)}
            >
              <span className="option-key" aria-hidden="true">
                {index + 1}
              </span>
              <span>{option.text}</span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}
