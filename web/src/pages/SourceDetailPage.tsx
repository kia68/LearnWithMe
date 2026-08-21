import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../api/client";
import { useTranslation, type TranslationKey } from "../i18n";

export default function SourceDetailPage() {
  const { sourceId } = useParams<{ sourceId: string }>();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const sourceQuery = useQuery({
    queryKey: ["source", sourceId],
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/sources/{id}", { params: { path: { id: sourceId! } } });
      if (error) throw error;
      return data;
    },
    enabled: !!sourceId,
    refetchInterval: (query) => (query.state.data?.status === "READY" || query.state.data?.status === "FAILED" ? false : 2000),
  });

  const conceptsQuery = useQuery({
    queryKey: ["concepts", sourceId],
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/sources/{sourceId}/concepts", { params: { path: { sourceId: sourceId! } } });
      if (error) throw error;
      return data ?? [];
    },
    enabled: !!sourceId,
  });

  const startSessionMutation = useMutation({
    mutationFn: async (scope: { scopeKind: "SOURCE" | "CONCEPT"; scopeId?: string }) => {
      const { data, error } = await api.POST("/api/v1/sessions", {
        body: { scopeKind: scope.scopeKind, scopeId: scope.scopeId, goalKind: "ITEM_COUNT", goalValue: 10 },
      });
      if (error) throw error;
      return data;
    },
    onSuccess: (data) => {
      if (data) navigate(`/sessions/${data.sessionId}`);
    },
  });

  const generateMutation = useMutation({
    mutationFn: async ({ conceptId, count }: { conceptId: string; count: number }) => {
      const { error } = await api.POST("/api/v1/concepts/{conceptId}/items:generate", {
        params: { path: { conceptId } },
        body: { count, types: [] },
      });
      if (error) throw error;
    },
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["concepts", sourceId] }),
  });

  if (!sourceId) return null;
  const source = sourceQuery.data;

  return (
    <div className="stack">
      {sourceQuery.isLoading && <p>{t("common.loading")}</p>}
      {source && (
        <>
          <div className="row" style={{ justifyContent: "space-between" }}>
            <div>
              <h1>{source.title}</h1>
              <span className="badge">{t(`library.status.${source.status}` as TranslationKey)}</span>
            </div>
            {source.status === "READY" || source.status === "PARTIAL" ? (
              <button
                className="btn btn-primary"
                onClick={() => startSessionMutation.mutate({ scopeKind: "SOURCE" })}
                disabled={startSessionMutation.isPending}
              >
                {t("library.startSession")}
              </button>
            ) : null}
          </div>

          <section className="stack">
            <h2>{t("source.concepts")}</h2>
            {conceptsQuery.data?.length === 0 && <p>{t("source.noConceptsYet")}</p>}
            <ul className="card-list" style={{ listStyle: "none", padding: 0, margin: 0 }}>
              {conceptsQuery.data?.map((concept) => (
                <ConceptRow
                  key={concept.id}
                  conceptId={concept.id}
                  name={concept.name}
                  onStartSession={() => startSessionMutation.mutate({ scopeKind: "CONCEPT", scopeId: concept.id })}
                  onGenerate={(count) => generateMutation.mutate({ conceptId: concept.id, count })}
                  isGenerating={generateMutation.isPending}
                />
              ))}
            </ul>
          </section>
        </>
      )}
    </div>
  );
}

function ConceptRow({
  conceptId,
  name,
  onStartSession,
  onGenerate,
  isGenerating,
}: {
  conceptId: string;
  name: string;
  onStartSession: () => void;
  onGenerate: (count: number) => void;
  isGenerating: boolean;
}) {
  const { t } = useTranslation();
  const [count, setCount] = useState(5);

  const itemsQuery = useQuery({
    queryKey: ["items", conceptId],
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/concepts/{conceptId}/items", { params: { path: { conceptId } } });
      if (error) throw error;
      return data ?? [];
    },
  });

  const publishedCount = itemsQuery.data?.filter((item) => item.status === "PUBLISHED").length ?? 0;

  return (
    <li className="card stack">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <strong>{name}</strong>
        <span className="badge">{t("source.itemCount", { count: publishedCount })}</span>
      </div>
      <div className="row">
        <button className="btn btn-primary" onClick={onStartSession} disabled={publishedCount === 0}>
          {t("library.startSession")}
        </button>
        <label className="visually-hidden" htmlFor={`count-${conceptId}`}>
          {t("source.generateCount")}
        </label>
        <input
          id={`count-${conceptId}`}
          type="number"
          min={1}
          max={20}
          style={{ width: 72 }}
          value={count}
          onChange={(e) => setCount(Number(e.target.value))}
        />
        <button className="btn" onClick={() => onGenerate(count)} disabled={isGenerating}>
          {t("source.generateSubmit")}
        </button>
      </div>
    </li>
  );
}
