import { useState, type FormEvent } from "react";
import { Navigate, Link } from "react-router-dom";
import { useTranslation } from "../i18n";
import { useAuth } from "./AuthContext";

export default function RegisterPage() {
  const { t } = useTranslation();
  const { user, isInitializing, register } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setSubmitting] = useState(false);

  if (isInitializing) return null;
  if (user) return <Navigate to="/" replace />;

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await register(email, password, displayName || undefined);
    } catch {
      setError(t("auth.error"));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="app-main" style={{ maxWidth: 420 }}>
      <h1>{t("auth.registerTitle")}</h1>
      <form className="stack" onSubmit={handleSubmit} noValidate>
        {error && (
          <p className="banner banner-error" role="alert">
            {error}
          </p>
        )}
        <div className="field">
          <label htmlFor="email">{t("auth.email")}</label>
          <input id="email" type="email" required autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="displayName">{t("auth.displayName")}</label>
          <input id="displayName" type="text" autoComplete="name" value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="password">{t("auth.password")}</label>
          <input
            id="password"
            type="password"
            required
            minLength={8}
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>
        <button className="btn btn-primary" type="submit" disabled={isSubmitting}>
          {t("auth.registerButton")}
        </button>
        <Link to="/login">{t("auth.switchToLogin")}</Link>
      </form>
    </main>
  );
}
