// F6: eigenes, kleines Wörterbuch für das Side Panel (separate Extension-App, kein Zugriff auf
// web/src/i18n — daher bewusst schlank auf die hier tatsächlich genutzten Strings begrenzt).
const de = {
  "auth.title": "Anmelden",
  "auth.email": "E-Mail",
  "auth.password": "Passwort",
  "auth.submit": "Anmelden",
  "auth.error": "Anmeldung fehlgeschlagen.",
  "auth.hint": "Noch kein Konto? In der Web-App registrieren.",

  "selection.title": "Markierter Text",
  "selection.cta": "Frage erzeugen",
  "selection.success": "Import gestartet — Fragen erscheinen gleich unten.",

  "page.title": "Diese Seite",
  "page.cta": "Seite importieren",
  "page.unavailable": "Keine Seiteninformation verfügbar. Extension-Symbol anklicken, um Zugriff zu erlauben.",

  "library.title": "Meine Dokumente",
  "library.empty": "Noch keine Dokumente.",
  "library.startSession": "Session starten",
  "library.status.UPLOADED": "Hochgeladen",
  "library.status.EXTRACTING": "Wird extrahiert …",
  "library.status.CHUNKING": "Wird strukturiert …",
  "library.status.INDEXING": "Wird indiziert …",
  "library.status.READY": "Bereit",
  "library.status.PARTIAL": "Teilweise verfügbar",
  "library.status.FAILED": "Fehlgeschlagen",

  "session.noItems": "Keine Fragen verfügbar.",
  "session.submit": "Antworten",
  "session.next": "Weiter",
  "session.skip": "Überspringen",
  "session.finish": "Session beenden",
  "session.finished": "Session beendet.",
  "session.correct": "Richtig!",
  "session.incorrect": "Leider falsch.",
  "session.partial": "Teilweise richtig.",
  "session.unsupportedType": "Dieser Fragentyp wird im Side Panel noch nicht unterstützt.",
  "session.openInWeb": "In der Web-App öffnen",
  "item.trueFalse.true": "Wahr",
  "item.trueFalse.false": "Falsch",

  "nav.logout": "Abmelden",
  "common.loading": "Wird geladen …",
  "common.error": "Etwas ist schiefgelaufen.",
  "common.back": "Zurück",
} as const;

const en: Record<keyof typeof de, string> = {
  "auth.title": "Log in",
  "auth.email": "Email",
  "auth.password": "Password",
  "auth.submit": "Log in",
  "auth.error": "Login failed.",
  "auth.hint": "No account yet? Register in the web app.",

  "selection.title": "Selected text",
  "selection.cta": "Generate question",
  "selection.success": "Import started — questions will appear below shortly.",

  "page.title": "This page",
  "page.cta": "Import page",
  "page.unavailable": "No page info available. Click the extension icon to grant access.",

  "library.title": "My documents",
  "library.empty": "No documents yet.",
  "library.startSession": "Start session",
  "library.status.UPLOADED": "Uploaded",
  "library.status.EXTRACTING": "Extracting …",
  "library.status.CHUNKING": "Structuring …",
  "library.status.INDEXING": "Indexing …",
  "library.status.READY": "Ready",
  "library.status.PARTIAL": "Partially available",
  "library.status.FAILED": "Failed",

  "session.noItems": "No questions available.",
  "session.submit": "Submit",
  "session.next": "Next",
  "session.skip": "Skip",
  "session.finish": "Finish session",
  "session.finished": "Session finished.",
  "session.correct": "Correct!",
  "session.incorrect": "Not quite.",
  "session.partial": "Partially correct.",
  "session.unsupportedType": "This question type isn't supported in the side panel yet.",
  "session.openInWeb": "Open in web app",
  "item.trueFalse.true": "True",
  "item.trueFalse.false": "False",

  "nav.logout": "Log out",
  "common.loading": "Loading …",
  "common.error": "Something went wrong.",
  "common.back": "Back",
};

export type Locale = "de" | "en";
type Dictionary = typeof de;
const dictionaries: Record<Locale, Record<keyof Dictionary, string>> = { de, en };

export function detectLocale(): Locale {
  return navigator.language.toLowerCase().startsWith("de") ? "de" : "en";
}

export function translate(locale: Locale, key: keyof Dictionary): string {
  return dictionaries[locale][key];
}
