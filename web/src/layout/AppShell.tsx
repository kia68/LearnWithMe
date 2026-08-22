import type { ReactNode } from "react";
import { NavLink } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { useTranslation, type Locale } from "../i18n";

/** F1 (responsive Shell, Tastaturbedienung über native `<a>`/`<button>`) + F5 (Skip-Link,
 * `aria-current` auf dem aktiven Nav-Eintrag, ausreichende Touch-/Klickziele in styles.css). */
export default function AppShell({ children }: { children: ReactNode }) {
  const { t, locale, setLocale } = useTranslation();
  const { user, logout } = useAuth();

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        {t("nav.skipToContent")}
      </a>
      <header className="app-header">
        <strong>{t("app.name")}</strong>
        <nav className="app-nav" aria-label={t("nav.library")}>
          <NavLink to="/" end>
            {t("nav.library")}
          </NavLink>
          <NavLink to="/import">{t("nav.import")}</NavLink>
          <NavLink to="/review">{t("nav.review")}</NavLink>
          <NavLink to="/progress">{t("nav.progress")}</NavLink>
          <NavLink to="/settings">{t("nav.settings")}</NavLink>
        </nav>
        <div className="row">
          <label className="visually-hidden" htmlFor="locale-select">
            {t("settings.language")}
          </label>
          <select id="locale-select" value={locale} onChange={(e) => setLocale(e.target.value as Locale)}>
            <option value="de">Deutsch</option>
            <option value="en">English</option>
          </select>
          {user && (
            <button className="btn" onClick={() => void logout()}>
              {t("nav.logout")}
            </button>
          )}
        </div>
      </header>
      <main id="main-content" className="app-main" tabIndex={-1}>
        {children}
      </main>
    </div>
  );
}
