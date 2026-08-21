import type { components } from "@learnwithme/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { api } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { useTranslation } from "../i18n";

type AiProvider = components["schemas"]["CreateCredentialRequest"]["provider"];

export default function SettingsPage() {
  const { t } = useTranslation();
  const { logout } = useAuth();
  const queryClient = useQueryClient();

  const providersQuery = useQuery({
    queryKey: ["ai-providers"],
    queryFn: async () => (await api.GET("/api/v1/ai/providers")).data ?? [],
  });
  const credentialsQuery = useQuery({
    queryKey: ["ai-credentials"],
    queryFn: async () => (await api.GET("/api/v1/ai/credentials")).data ?? [],
  });
  const usageQuery = useQuery({
    queryKey: ["ai-usage"],
    queryFn: async () => (await api.GET("/api/v1/ai/usage")).data ?? null,
  });

  const [provider, setProvider] = useState<AiProvider>("OPENAI");
  const [apiKey, setApiKey] = useState("");

  const createCredentialMutation = useMutation({
    mutationFn: async () => {
      const { error } = await api.POST("/api/v1/ai/credentials", { body: { provider, apiKey } });
      if (error) throw error;
    },
    onSuccess: () => {
      setApiKey("");
      void queryClient.invalidateQueries({ queryKey: ["ai-credentials"] });
    },
  });

  const deleteAccountMutation = useMutation({
    mutationFn: async () => {
      const { error } = await api.DELETE("/api/v1/me");
      if (error) throw error;
    },
    onSuccess: () => void logout(),
  });

  function handleAddCredential(e: FormEvent) {
    e.preventDefault();
    createCredentialMutation.mutate();
  }

  function handleDeleteAccount() {
    if (window.confirm(t("settings.deleteAccountConfirm"))) deleteAccountMutation.mutate();
  }

  return (
    <div className="stack">
      <h1>{t("settings.title")}</h1>

      <section className="card stack">
        <h2>{t("settings.aiCredentials")}</h2>
        {credentialsQuery.data?.length === 0 && <p>{t("settings.aiCredentials.empty")}</p>}
        <ul>
          {credentialsQuery.data?.map((credential) => (
            <li key={credential.id}>
              {credential.provider} — {credential.keyHint ?? "…"} ({credential.status})
            </li>
          ))}
        </ul>
        <form className="row" onSubmit={handleAddCredential}>
          <div className="field">
            <label htmlFor="provider">{t("settings.aiCredentials.provider")}</label>
            <select id="provider" value={provider} onChange={(e) => setProvider(e.target.value as AiProvider)}>
              {(providersQuery.data ?? []).map((p) => (
                <option key={p.id} value={p.id}>
                  {p.displayName}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="apiKey">{t("settings.aiCredentials.apiKey")}</label>
            <input id="apiKey" type="password" value={apiKey} onChange={(e) => setApiKey(e.target.value)} />
          </div>
          <button className="btn btn-primary" type="submit" disabled={!apiKey || createCredentialMutation.isPending}>
            {t("settings.aiCredentials.add")}
          </button>
        </form>
      </section>

      <section className="card">
        <h2>{t("settings.usage")}</h2>
        {usageQuery.data && (
          <p>
            {usageQuery.data.totalInputTokens + usageQuery.data.totalOutputTokens} Tokens · {(usageQuery.data.totalCostMicros / 1_000_000).toFixed(2)} €
          </p>
        )}
      </section>

      <section className="card">
        <button className="btn btn-danger" onClick={handleDeleteAccount}>
          {t("settings.deleteAccount")}
        </button>
      </section>
    </div>
  );
}
