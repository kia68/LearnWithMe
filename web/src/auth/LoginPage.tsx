import { useState, type FormEvent } from "react";
import { Navigate, Link } from "react-router-dom";
import { useTranslation } from "../i18n";
import { useAuth } from "./AuthContext";

export default function LoginPage() {
  const { t } = useTranslation();
  const { user, isInitializing, login } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setSubmitting] = useState(false);

  if (isInitializing) return null;
  if (user) return <Navigate to="/" replace />;

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
    } catch {
      setError(t("auth.error"));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="app-main" style={{ maxWidth: 420 }}>
      <h1>{t("auth.loginTitle")}</h1>
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
          <label htmlFor="password">{t("auth.password")}</label>
          <input
            id="password"
            type="password"
            required
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>
        <button className="btn btn-primary" type="submit" disabled={isSubmitting}>
          {t("auth.loginButton")}
        </button>
        <Link to="/register">{t("auth.switchToRegister")}</Link>
      </form>
    </main>
  );
}
