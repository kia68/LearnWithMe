import { useEffect, useState } from "react";
import CategorizationItem from "./CategorizationItem";
import ClozeItem from "./ClozeItem";
import CodeOutputItem from "./CodeOutputItem";
import MatchingItem from "./MatchingItem";
import McChoice from "./McChoice";
import NumericItem from "./NumericItem";
import OrderingItem from "./OrderingItem";
import ShortAnswerItem from "./ShortAnswerItem";
import TrueFalseItem from "./TrueFalseItem";
import type {
  CategorizationPayload,
  ClozePayload,
  CodeOutputPayload,
  ItemResponseBody,
  MatchingPayload,
  McMultiPayload,
  McSinglePayload,
  NumericPayload,
  OrderingPayload,
  ShortAnswerPayload,
  TrueFalsePayload,
} from "./types";

export interface ItemRendererProps {
  type: string;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  payload: any;
  disabled: boolean;
  /** Nur nach dem Absenden gesetzt — dient dem Einfärben richtig/falsch (D4). */
  correctResponse?: unknown;
  onResponseChange: (response: ItemResponseBody | null) => void;
}

/** Typ-Dispatch analog zum Backend (§10.2 `grade()` — ein `when` ohne `else`-Äquivalent hier via
 * `default`, das TypeScript nicht erzwingen kann, aber die Struktur bewusst spiegelt). */
export default function ItemRenderer({ type, payload, disabled, correctResponse, onResponseChange }: ItemRendererProps) {
  switch (type) {
    case "MC_SINGLE":
      return <McSingleView payload={payload} disabled={disabled} correctResponse={correctResponse} onResponseChange={onResponseChange} />;
    case "MC_MULTI":
      return <McMultiView payload={payload} disabled={disabled} correctResponse={correctResponse} onResponseChange={onResponseChange} />;
    case "TRUE_FALSE":
      return <TrueFalseView payload={payload} disabled={disabled} correctResponse={correctResponse} onResponseChange={onResponseChange} />;
    case "ORDERING":
      return <OrderingView payload={payload} disabled={disabled} onResponseChange={onResponseChange} />;
    case "MATCHING":
      return <MatchingView payload={payload} disabled={disabled} onResponseChange={onResponseChange} />;
    case "CLOZE":
      return <ClozeView payload={payload} disabled={disabled} onResponseChange={onResponseChange} />;
    case "SHORT_ANSWER":
      return <ShortAnswerView payload={payload} disabled={disabled} onResponseChange={onResponseChange} />;
    case "NUMERIC":
      return <NumericView payload={payload} disabled={disabled} onResponseChange={onResponseChange} />;
    case "CATEGORIZATION":
      return <CategorizationView payload={payload} disabled={disabled} onResponseChange={onResponseChange} />;
    case "CODE_OUTPUT":
      return <CodeOutputView payload={payload} disabled={disabled} onResponseChange={onResponseChange} />;
    default:
      return <p role="alert">Unbekannter Fragetyp: {type}</p>;
  }
}

function McSingleView({
  payload,
  disabled,
  correctResponse,
  onResponseChange,
}: {
  payload: McSinglePayload;
  disabled: boolean;
  correctResponse?: unknown;
  onResponseChange: (r: ItemResponseBody | null) => void;
}) {
  const [selected, setSelected] = useState<string[]>([]);
  useEffect(() => onResponseChange(selected[0] ? { optionId: selected[0] } : null), [selected, onResponseChange]);
  const correctOptionIds = extractOptionIds(correctResponse, "optionId");
  return <McChoice options={payload.options ?? []} mode="single" selected={selected} onChange={setSelected} disabled={disabled} correctOptionIds={correctOptionIds} />;
}

function McMultiView({
  payload,
  disabled,
  correctResponse,
  onResponseChange,
}: {
  payload: McMultiPayload;
  disabled: boolean;
  correctResponse?: unknown;
  onResponseChange: (r: ItemResponseBody | null) => void;
}) {
  const [selected, setSelected] = useState<string[]>([]);
  useEffect(() => onResponseChange(selected.length > 0 ? { optionIds: selected } : null), [selected, onResponseChange]);
  const correctOptionIds = extractOptionIds(correctResponse, "optionIds");
  return <McChoice options={payload.options ?? []} mode="multi" selected={selected} onChange={setSelected} disabled={disabled} correctOptionIds={correctOptionIds} />;
}

function TrueFalseView({
  payload,
  disabled,
  correctResponse,
  onResponseChange,
}: {
  payload: TrueFalsePayload;
  disabled: boolean;
  correctResponse?: unknown;
  onResponseChange: (r: ItemResponseBody | null) => void;
}) {
  const [selected, setSelected] = useState<boolean | null>(null);
  useEffect(() => onResponseChange(selected === null ? null : { answer: selected }), [selected, onResponseChange]);
  const correctAnswer =
    correctResponse && typeof correctResponse === "object" && "answer" in correctResponse
      ? Boolean((correctResponse as { answer: unknown }).answer)
      : undefined;
  return <TrueFalseItem statement={payload.statement} selected={selected} onChange={setSelected} disabled={disabled} correctAnswer={correctAnswer} />;
}

function OrderingView({
  payload,
  disabled,
  onResponseChange,
}: {
  payload: OrderingPayload;
  disabled: boolean;
  onResponseChange: (r: ItemResponseBody | null) => void;
}) {
  const [order, setOrder] = useState<string[]>(() => payload.elements.map((e) => e.id));
  useEffect(() => onResponseChange({ order }), [order, onResponseChange]);
  return <OrderingItem payload={payload} order={order} onChange={setOrder} disabled={disabled} />;
}

function MatchingView({
  payload,
  disabled,
  onResponseChange,
}: {
  payload: MatchingPayload;
  disabled: boolean;
  onResponseChange: (r: ItemResponseBody | null) => void;
}) {
  const [pairs, setPairs] = useState<Record<string, string>>({});
  useEffect(() => {
    const entries = Object.entries(pairs);
    onResponseChange(entries.length === payload.left.length ? { pairs: entries.map(([leftId, rightId]) => ({ leftId, rightId })) } : null);
  }, [pairs, payload.left.length, onResponseChange]);
  return <MatchingItem payload={payload} pairs={pairs} onChange={setPairs} disabled={disabled} />;
}

function ClozeView({
  payload,
  disabled,
  onResponseChange,
}: {
  payload: ClozePayload;
  disabled: boolean;
  onResponseChange: (r: ItemResponseBody | null) => void;
}) {
  const [answers, setAnswers] = useState<string[]>(() => payload.blanks.map(() => ""));
  useEffect(() => onResponseChange({ answers }), [answers, onResponseChange]);
  return <ClozeItem payload={payload} answers={answers} onChange={setAnswers} disabled={disabled} />;
}

function ShortAnswerView({
  payload,
  disabled,
  onResponseChange,
}: {
  payload: ShortAnswerPayload;
  disabled: boolean;
  onResponseChange: (r: ItemResponseBody | null) => void;
}) {
  const [answer, setAnswer] = useState("");
  useEffect(() => onResponseChange(answer.trim() ? { answer } : null), [answer, onResponseChange]);
  return <ShortAnswerItem payload={payload} value={answer} onChange={setAnswer} disabled={disabled} />;
}

function NumericView({
  payload,
  disabled,
  onResponseChange,
}: {
  payload: NumericPayload;
  disabled: boolean;
  onResponseChange: (r: ItemResponseBody | null) => void;
}) {
  const [answer, setAnswer] = useState("");
  const [unit, setUnit] = useState("");
  useEffect(() => {
    const parsed = Number(answer);
    if (answer.trim() === "" || Number.isNaN(parsed)) {
      onResponseChange(null);
    } else {
      onResponseChange(payload.unit != null ? { answer: parsed, unit } : { answer: parsed });
    }
  }, [answer, unit, payload.unit, onResponseChange]);
  return <NumericItem payload={payload} answer={answer} unit={unit} onChangeAnswer={setAnswer} onChangeUnit={setUnit} disabled={disabled} />;
}

function CategorizationView({
  payload,
  disabled,
  onResponseChange,
}: {
  payload: CategorizationPayload;
  disabled: boolean;
  onResponseChange: (r: ItemResponseBody | null) => void;
}) {
  const [assignments, setAssignments] = useState<Record<string, string>>({});
  useEffect(() => {
    const entries = Object.entries(assignments);
    onResponseChange(
      entries.length === payload.elements.length ? { assignments: entries.map(([elementId, bucketId]) => ({ elementId, bucketId })) } : null,
    );
  }, [assignments, payload.elements.length, onResponseChange]);
  return <CategorizationItem payload={payload} assignments={assignments} onChange={setAssignments} disabled={disabled} />;
}

function CodeOutputView({
  payload,
  disabled,
  onResponseChange,
}: {
  payload: CodeOutputPayload;
  disabled: boolean;
  onResponseChange: (r: ItemResponseBody | null) => void;
}) {
  const [answer, setAnswer] = useState("");
  useEffect(() => onResponseChange(answer.trim() ? { answer } : null), [answer, onResponseChange]);
  return <CodeOutputItem payload={payload} value={answer} onChange={setAnswer} disabled={disabled} />;
}

function extractOptionIds(correctResponse: unknown, key: "optionId" | "optionIds"): string[] | undefined {
  if (!correctResponse || typeof correctResponse !== "object") return undefined;
  const value = (correctResponse as Record<string, unknown>)[key];
  if (typeof value === "string") return [value];
  if (Array.isArray(value)) return value as string[];
  return undefined;
}
