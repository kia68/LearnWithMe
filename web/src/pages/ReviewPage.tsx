import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import ReviewItemPayload from "../components/items/ReviewItemPayload";
import { api } from "../api/client";
import { useTranslation, type TranslationKey } from "../i18n";

type Status = "DRAFT" | "PUBLISHED" | "REJECTED" | "RETIRED";
const STATUSES: Status[] = ["DRAFT", "PUBLISHED", "REJECTED", "RETIRED"];

/** Dozenten-Review-Workflow (C7-Frontend, M6-Nachtrag) — der Backend-Teil (`ItemController`)
 * existiert seit Epic C, hatte aber nie eine UI. Anders als überall sonst in der App (Lernenden-
 * Sicht) zeigt diese Seite Items MIT Lösung — ein Reviewer muss die Qualität beurteilen können. */
export default function ReviewPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<Status>("DRAFT");
  const [selected, setSelected] = useState<Set<string>>(new Set());

  const queueQuery = useQuery({
    queryKey: ["review-queue", status],
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/items/review-queue", { params: { query: { status, size: 50 } } });
      if (error) throw error;
      return data;
    },
  });

  const invalidate = () => {
    setSelected(new Set());
    void queryClient.invalidateQueries({ queryKey: ["review-queue"] });
  };

  const publishMutation = useMutation({
    mutationFn: async (id: string) => {
      const { error } = await api.POST("/api/v1/items/{id}/publish", { params: { path: { id } } });
      if (error) throw error;
    },
    onSuccess: invalidate,
  });

  const rejectMutation = useMutation({
    mutationFn: async (id: string) => {
      const { error } = await api.POST("/api/v1/items/{id}/reject", { params: { path: { id } } });
      if (error) throw error;
    },
    onSuccess: invalidate,
  });

  const bulkPublishMutation = useMutation({
    mutationFn: async (ids: string[]) => {
      const { error } = await api.POST("/api/v1/items:bulk-publish", { body: { ids } });
      if (error) throw error;
    },
    onSuccess: invalidate,
  });

  const bulkRejectMutation = useMutation({
    mutationFn: async (ids: string[]) => {
      const { error } = await api.POST("/api/v1/items:bulk-reject", { body: { ids } });
      if (error) throw error;
    },
    onSuccess: invalidate,
  });

  const items = queueQuery.data?.items ?? [];
  const isBusy = publishMutation.isPending || rejectMutation.isPending || bulkPublishMutation.isPending || bulkRejectMutation.isPending;

  function toggle(id: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function toggleAll() {
    setSelected((prev) => (prev.size === items.length ? new Set() : new Set(items.map((i) => i.id))));
  }

  return (
    <div className="stack">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h1>{t("review.title")}</h1>
        <div className="row">
          <label className="visually-hidden" htmlFor="review-status">
            {t("review.statusFilter")}
          </label>
          <select id="review-status" value={status} onChange={(e) => setStatus(e.target.value as Status)}>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {t(`review.status.${s}` as TranslationKey)}
              </option>
            ))}
          </select>
        </div>
      </div>

      {status === "DRAFT" && items.length > 0 && (
        <div className="row" style={{ justifyContent: "space-between" }}>
          <label className="row">
            <input type="checkbox" checked={selected.size === items.length && items.length > 0} onChange={toggleAll} />
            {t("review.selectAll", { count: selected.size })}
          </label>
          <div className="row">
            <button
              className="btn btn-primary"
              disabled={selected.size === 0 || isBusy}
              onClick={() => bulkPublishMutation.mutate([...selected])}
            >
              {t("review.bulkPublish")}
            </button>
            <button className="btn" disabled={selected.size === 0 || isBusy} onClick={() => bulkRejectMutation.mutate([...selected])}>
              {t("review.bulkReject")}
            </button>
          </div>
        </div>
      )}

      {queueQuery.isLoading && <p>{t("common.loading")}</p>}
      {!queueQuery.isLoading && items.length === 0 && <p>{t("review.empty")}</p>}

      <ul className="stack" style={{ listStyle: "none", padding: 0, margin: 0 }}>
        {items.map((item) => (
          <li key={item.id} className="card stack">
            <div className="row" style={{ justifyContent: "space-between", alignItems: "flex-start" }}>
              <div className="row" style={{ alignItems: "flex-start" }}>
                {status === "DRAFT" && (
                  <input type="checkbox" checked={selected.has(item.id)} onChange={() => toggle(item.id)} style={{ marginTop: 4 }} />
                )}
                <div>
                  <span className="badge">{item.type}</span>
                  <span className="badge" style={{ marginLeft: 4 }}>
                    {item.bloomLevel}
                  </span>
                  {item.reportCount > 0 && (
                    <span className="badge" style={{ marginLeft: 4 }}>
                      {t("review.reportCount", { count: item.reportCount })}
                    </span>
                  )}
                  <h3 style={{ margin: "0.25rem 0" }}>{item.stem}</h3>
                </div>
              </div>
              {status === "DRAFT" && (
                <div className="row">
                  <button className="btn btn-primary" disabled={isBusy} onClick={() => publishMutation.mutate(item.id)}>
                    {t("review.publish")}
                  </button>
                  <button className="btn" disabled={isBusy} onClick={() => rejectMutation.mutate(item.id)}>
                    {t("review.reject")}
                  </button>
                </div>
              )}
            </div>
            <ReviewItemPayload item={item} />
            <p style={{ color: "var(--color-text-muted)", margin: 0 }}>{item.explanation}</p>
          </li>
        ))}
      </ul>
    </div>
  );
}
