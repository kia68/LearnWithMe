import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import de from "./de";
import en from "./en";

export type Locale = "de" | "en";
export type TranslationKey = keyof typeof de;

const dictionaries: Record<Locale, Record<TranslationKey, string>> = { de, en };
const STORAGE_KEY = "learnwithme.locale";

interface I18nContextValue {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: (key: TranslationKey, params?: Record<string, string | number>) => string;
}

const I18nContext = createContext<I18nContextValue | null>(null);

function detectInitialLocale(): Locale {
  const stored = localStorage.getItem(STORAGE_KEY);
  if (stored === "de" || stored === "en") return stored;
  return navigator.language.toLowerCase().startsWith("en") ? "en" : "de";
}

function interpolate(template: string, params?: Record<string, string | number>): string {
  if (!params) return template;
  return template.replace(/\{(\w+)\}/g, (match, key) => (key in params ? String(params[key]) : match));
}

/** F6: UI de/en. Fragen-Inhalte bleiben in der Dokumentsprache (serverseitig fixiert, kein
 * Umschalt-Endpunkt) — hier geht es nur um die Bedienoberfläche. */
export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(detectInitialLocale);

  const setLocale = useCallback((next: Locale) => {
    setLocaleState(next);
    localStorage.setItem(STORAGE_KEY, next);
    document.documentElement.lang = next;
  }, []);

  const t = useCallback(
    (key: TranslationKey, params?: Record<string, string | number>) => interpolate(dictionaries[locale][key], params),
    [locale],
  );

  const value = useMemo(() => ({ locale, setLocale, t }), [locale, setLocale, t]);

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useTranslation(): I18nContextValue {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error("useTranslation must be used within I18nProvider");
  return ctx;
}
