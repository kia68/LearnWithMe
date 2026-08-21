import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { useTranslation } from "../i18n";

export default function ImportPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const [url, setUrl] = useState("");
  const [title, setTitle] = useState("");
  const [error, setError] = useState<string | null>(null);

  const uploadMutation = useMutation({
    mutationFn: async () => {
      if (!file) throw new Error("no file");
      const formData = new FormData();
      formData.append("file", file);
      const { data, error } = await api.POST("/api/v1/sources", { body: formData as never });
      if (error) throw error;
      return data;
    },
    onSuccess: (data) => {
      void queryClient.invalidateQueries({ queryKey: ["sources"] });
      if (data) navigate(`/sources/${data.id}`);
    },
    onError: () => setError(t("import.error")),
  });

  const urlMutation = useMutation({
    mutationFn: async () => {
      const { data, error } = await api.POST("/api/v1/sources", {
        body: { url, title: title || undefined },
        bodySerializer: (body) => JSON.stringify(body),
        headers: { "Content-Type": "application/json" },
      });
      if (error) throw error;
      return data;
    },
    onSuccess: (data) => {
      void queryClient.invalidateQueries({ queryKey: ["sources"] });
      if (data) navigate(`/sources/${data.id}`);
    },
    onError: () => setError(t("import.error")),
  });

  function handleFileSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    uploadMutation.mutate();
  }

  function handleUrlSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    urlMutation.mutate();
  }

  const isBusy = uploadMutation.isPending || urlMutation.isPending;

  return (
    <div className="stack">
      <h1>{t("import.title")}</h1>
      {error && (
        <p className="banner banner-error" role="alert">
          {error}
        </p>
      )}

      <form className="stack card" onSubmit={handleFileSubmit}>
        <div className="field">
          <label htmlFor="file">{t("import.fileLabel")}</label>
          <input
            id="file"
            type="file"
            accept=".pdf,.docx,.epub,.txt,.md"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          />
        </div>
        <button className="btn btn-primary" type="submit" disabled={!file || isBusy}>
          {uploadMutation.isPending ? t("import.uploading") : t("import.submit")}
        </button>
      </form>

      <form className="stack card" onSubmit={handleUrlSubmit}>
        <h2 style={{ margin: 0, fontSize: "1rem" }}>{t("import.orUrl")}</h2>
        <div className="field">
          <label htmlFor="url">{t("import.urlLabel")}</label>
          <input id="url" type="url" required value={url} onChange={(e) => setUrl(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="title">{t("import.titleLabel")}</label>
          <input id="title" type="text" value={title} onChange={(e) => setTitle(e.target.value)} />
        </div>
        <button className="btn btn-primary" type="submit" disabled={!url || isBusy}>
          {t("import.submit")}
        </button>
      </form>
    </div>
  );
}
