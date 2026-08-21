import { useQuery } from "@tanstack/react-query";
import { api } from "../api/client";
import { useTranslation, type TranslationKey } from "../i18n";

/** D7/E3/E5. `conceptId` wird gekürzt angezeigt statt aufgelöst — es gibt keinen
 * quellenübergreifenden "Konzeptname per ID"-Endpunkt (nur `/sources/{id}/concepts`), siehe
 * docs/progress.md Epic F. Der Wochenreport hat Namen bereits serverseitig aufgelöst (E5). */
export default function ProgressPage() {
  const { t } = useTranslation();

  const overviewQuery = useQuery({
    queryKey: ["progress-overview"],
    queryFn: async () => (await api.GET("/api/v1/progress/overview")).data ?? null,
  });
  const dueQuery = useQuery({
    queryKey: ["progress-due"],
    queryFn: async () => (await api.GET("/api/v1/progress/due")).data ?? [],
  });
  const misconceptionsQuery = useQuery({
    queryKey: ["progress-misconceptions"],
    queryFn: async () => (await api.GET("/api/v1/progress/misconceptions")).data ?? [],
  });
  const weeklyReportQuery = useQuery({
    queryKey: ["weekly-report"],
    queryFn: async () => (await api.GET("/api/v1/reports/weekly")).data ?? null,
  });

  return (
    <div className="stack">
      <h1>{t("progress.title")}</h1>

      <section className="card">
        <h2>{t("progress.overview")}</h2>
        {overviewQuery.data && (
          <>
            <p>{t("progress.conceptsMastered", { count: overviewQuery.data.conceptCount, average: Math.round(overviewQuery.data.averageMastery * 100) })}</p>
            <div className="progress-bar" role="img" aria-label={`${Math.round(overviewQuery.data.averageMastery * 100)}%`}>
              <div className="progress-bar-fill" style={{ width: `${Math.round(overviewQuery.data.averageMastery * 100)}%` }} />
            </div>
          </>
        )}
      </section>

      <section className="card">
        <h2>{t("progress.due", { count: dueQuery.data?.length ?? 0 })}</h2>
        <ul>
          {dueQuery.data?.map((concept) => (
            <li key={concept.conceptId}>
              {concept.conceptId.slice(0, 8)} — {Math.round(concept.mastery * 100)}%
            </li>
          ))}
        </ul>
      </section>

      <section className="card">
        <h2>{t("progress.misconceptions")}</h2>
        {misconceptionsQuery.data?.length === 0 && <p>{t("progress.misconceptions.empty")}</p>}
        <ul>
          {misconceptionsQuery.data?.map((m) => (
            <li key={m.id}>
              {t(`session.errorCategory.${m.category}` as TranslationKey)} — {m.conceptId.slice(0, 8)} ({m.occurrences}×)
              {m.flagged && <span className="badge" style={{ marginLeft: 8 }}>⚠</span>}
            </li>
          ))}
        </ul>
      </section>

      <section className="card">
        <h2>{t("progress.weeklyReport")}</h2>
        {weeklyReportQuery.data && weeklyReportQuery.data.topGaps.length === 0 && <p>{t("progress.weeklyReport.empty")}</p>}
        {weeklyReportQuery.data && weeklyReportQuery.data.recommendedFocus && (
          <p>{t("progress.weeklyReport.focus", { concept: weeklyReportQuery.data.recommendedFocus.conceptName })}</p>
        )}
        <ul>
          {weeklyReportQuery.data?.topGaps.map((gap) => (
            <li key={gap.conceptId}>
              {gap.conceptName} — {Math.round(gap.mastery * 100)}%
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
