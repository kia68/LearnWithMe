import type { components } from "@learnwithme/api-client";

export type McSinglePayload = components["schemas"]["McSinglePayload"];
export type McMultiPayload = components["schemas"]["McMultiPayload"];
export type TrueFalsePayload = components["schemas"]["TrueFalsePayload"];
export type OrderingPayload = components["schemas"]["OrderingPayload"];
export type MatchingPayload = components["schemas"]["MatchingPayload"];
export type ClozePayload = components["schemas"]["ClozePayload"];

/** Epic H: noch nicht im generierten `api-client` (ADR-011) — der Schema-Export brauchte einen
 * laufenden Backend-Prozess (`scripts/generate-api-client.mjs`), der in dieser Session wiederholt
 * an einer vorbestehenden, unabhängigen JDK-Instabilität scheiterte (siehe docs/progress.md).
 * Von Hand nachgezogen, exakt passend zu `authoring.internal.domain.ShortAnswerPayload`/
 * `RubricCriterion` — bei der nächsten Client-Regenerierung durch den generierten Typ ersetzen. */
export interface ShortAnswerPayload {
  rubric: { criterion: string; points: number }[];
  referenceAnswer: string;
}

export type ItemType = "MC_SINGLE" | "MC_MULTI" | "TRUE_FALSE" | "ORDERING" | "MATCHING" | "CLOZE" | "SHORT_ANSWER";

/** Der Response-Body, den `POST /sessions/{id}/attempts` je Typ erwartet (§10.2, assessment.ResponseGrader). */
export type ItemResponseBody =
  | { optionId: string }
  | { optionIds: string[] }
  | { answer: boolean }
  | { order: string[] }
  | { pairs: { leftId: string; rightId: string }[] }
  | { answers: string[] }
  | { answer: string };
