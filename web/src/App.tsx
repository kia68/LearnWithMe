import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useMemo } from "react";
import { Navigate, Route, BrowserRouter as Router, Routes } from "react-router-dom";
import { AuthProvider, useAuth } from "./auth/AuthContext";
import LoginPage from "./auth/LoginPage";
import RegisterPage from "./auth/RegisterPage";
import { I18nProvider } from "./i18n";
import AppShell from "./layout/AppShell";
import ImportPage from "./pages/ImportPage";
import LibraryPage from "./pages/LibraryPage";
import ProgressPage from "./pages/ProgressPage";
import ReviewPage from "./pages/ReviewPage";
import SessionPage from "./pages/SessionPage";
import SettingsPage from "./pages/SettingsPage";
import SourceDetailPage from "./pages/SourceDetailPage";

function ProtectedArea() {
  const { user, isInitializing } = useAuth();
  if (isInitializing) return null;
  if (!user) return <Navigate to="/login" replace />;

  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<LibraryPage />} />
        <Route path="/import" element={<ImportPage />} />
        <Route path="/sources/:sourceId" element={<SourceDetailPage />} />
        <Route path="/sessions/:sessionId" element={<SessionPage />} />
        <Route path="/review" element={<ReviewPage />} />
        <Route path="/progress" element={<ProgressPage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AppShell>
  );
}

export default function App() {
  const queryClient = useMemo(() => new QueryClient({ defaultOptions: { queries: { retry: 1 } } }), []);

  return (
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <Router>
          <AuthProvider>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
              <Route path="/*" element={<ProtectedArea />} />
            </Routes>
          </AuthProvider>
        </Router>
      </I18nProvider>
    </QueryClientProvider>
  );
}
