import { useEffect, useState } from "react";
import ClozeItem from "./ClozeItem";
import MatchingItem from "./MatchingItem";
import McChoice from "./McChoice";
import OrderingItem from "./OrderingItem";
import TrueFalseItem from "./TrueFalseItem";
import type { ClozePayload, ItemResponseBody, MatchingPayload, McMultiPayload, McSinglePayload, OrderingPayload, TrueFalsePayload } from "./types";

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

function extractOptionIds(correctResponse: unknown, key: "optionId" | "optionIds"): string[] | undefined {
  if (!correctResponse || typeof correctResponse !== "object") return undefined;
  const value = (correctResponse as Record<string, unknown>)[key];
  if (typeof value === "string") return [value];
  if (Array.isArray(value)) return value as string[];
  return undefined;
}
