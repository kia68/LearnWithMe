import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import { useTranslation, type TranslationKey } from "../i18n";

export default function LibraryPage() {
  const { t } = useTranslation();
  const { data, isLoading, isError } = useQuery({
    queryKey: ["sources"],
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/sources", { params: { query: { page: 0, size: 50 } } });
      if (error) throw error;
      return data;
    },
  });

  return (
    <div className="stack">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h1>{t("library.title")}</h1>
        <Link className="btn btn-primary" to="/import">
          {t("library.importCta")}
        </Link>
      </div>

      {isLoading && <p>{t("common.loading")}</p>}
      {isError && (
        <p className="banner banner-error" role="alert">
          {t("common.error")}
        </p>
      )}
      {data && data.items.length === 0 && <p>{t("library.empty")}</p>}

      {data && data.items.length > 0 && (
        <ul className="card-list" style={{ listStyle: "none", padding: 0, margin: 0 }}>
          {data.items.map((source) => (
            <li key={source.id} className="card row" style={{ justifyContent: "space-between" }}>
              <div>
                <strong>{source.title}</strong>
                <div className="row">
                  <span className="badge">{t(`library.status.${source.status}` as TranslationKey)}</span>
                  <span className="badge">{source.kind}</span>
                </div>
              </div>
              <Link className="btn" to={`/sources/${source.id}`}>
                {t("library.open")}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
