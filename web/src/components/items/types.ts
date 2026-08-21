import type { components } from "@learnwithme/api-client";

export type McSinglePayload = components["schemas"]["McSinglePayload"];
export type McMultiPayload = components["schemas"]["McMultiPayload"];
export type TrueFalsePayload = components["schemas"]["TrueFalsePayload"];
export type OrderingPayload = components["schemas"]["OrderingPayload"];
export type MatchingPayload = components["schemas"]["MatchingPayload"];
export type ClozePayload = components["schemas"]["ClozePayload"];

export type ItemType = "MC_SINGLE" | "MC_MULTI" | "TRUE_FALSE" | "ORDERING" | "MATCHING" | "CLOZE";

/** Der Response-Body, den `POST /sessions/{id}/attempts` je Typ erwartet (§10.2, assessment.ResponseGrader). */
export type ItemResponseBody =
  | { optionId: string }
  | { optionIds: string[] }
  | { answer: boolean }
  | { order: string[] }
  | { pairs: { leftId: string; rightId: string }[] }
  | { answers: string[] };
