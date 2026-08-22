import type { components } from "@learnwithme/api-client";

export type McSinglePayload = components["schemas"]["McSinglePayload"];
export type McMultiPayload = components["schemas"]["McMultiPayload"];
export type TrueFalsePayload = components["schemas"]["TrueFalsePayload"];
export type OrderingPayload = components["schemas"]["OrderingPayload"];
export type MatchingPayload = components["schemas"]["MatchingPayload"];
export type ClozePayload = components["schemas"]["ClozePayload"];
export type ShortAnswerPayload = components["schemas"]["ShortAnswerPayload"];
export type NumericPayload = components["schemas"]["NumericPayload"];
export type CategorizationPayload = components["schemas"]["CategorizationPayload"];
export type CodeOutputPayload = components["schemas"]["CodeOutputPayload"];

export type ItemType =
  | "MC_SINGLE"
  | "MC_MULTI"
  | "TRUE_FALSE"
  | "ORDERING"
  | "MATCHING"
  | "CLOZE"
  | "SHORT_ANSWER"
  | "NUMERIC"
  | "CATEGORIZATION"
  | "CODE_OUTPUT";

/** Der Response-Body, den `POST /sessions/{id}/attempts` je Typ erwartet (§10.2, assessment.ResponseGrader).
 * CODE_OUTPUT teilt sich `{ answer: string }` mit SHORT_ANSWER — gleiche Form, unterschiedliches
 * Grading (`ResponseGrader.gradeCodeOutput` vs. das asynchrone LLM-Rubric-Grading). */
export type ItemResponseBody =
  | { optionId: string }
  | { optionIds: string[] }
  | { answer: boolean }
  | { order: string[] }
  | { pairs: { leftId: string; rightId: string }[] }
  | { answers: string[] }
  | { answer: string }
  | { answer: number; unit?: string }
  | { assignments: { elementId: string; bucketId: string }[] };
