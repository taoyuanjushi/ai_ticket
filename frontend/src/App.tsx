import type { ReactNode } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "./components/AppShell";
import { AiPage } from "./pages/AiPage";
import { ForbiddenPage } from "./pages/ForbiddenPage";
import { LoginPage } from "./pages/LoginPage";
import { LogsPage } from "./pages/LogsPage";
import { SettingsPage } from "./pages/SettingsPage";
import { TicketDetailPage } from "./pages/TicketDetailPage";
import { TicketListPage } from "./pages/TicketListPage";
import { TicketNewPage } from "./pages/TicketNewPage";
import { UsersPage } from "./pages/UsersPage";
import { canViewAdminArea, canViewOperationLogs, normalizeRole } from "./auth/permissions";
import { useAuthStore } from "./state/authStore";
import type { UserRole } from "./types/domain";

export function App() {
  const token = useAuthStore((state) => state.token);

  if (!token) {
    return <LoginPage />;
  }

  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<TicketListPage />} />
        <Route path="tickets/new" element={<TicketNewPage />} />
        <Route path="tickets/:id" element={<TicketDetailPage />} />
        <Route path="ai" element={<AiPage />} />
        <Route
          path="users"
          element={
            <RequireRole canAccess={canViewAdminArea}>
              <UsersPage />
            </RequireRole>
          }
        />
        <Route
          path="logs"
          element={
            <RequireRole canAccess={canViewOperationLogs}>
              <LogsPage />
            </RequireRole>
          }
        />
        <Route
          path="settings"
          element={
            <RequireRole canAccess={canViewAdminArea}>
              <SettingsPage />
            </RequireRole>
          }
        />
        <Route path="forbidden" element={<ForbiddenPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}

function RequireRole({
  canAccess,
  children,
}: {
  canAccess: (role?: UserRole | string | null) => boolean;
  children: ReactNode;
}) {
  const role = normalizeRole(useAuthStore((state) => state.user?.role));

  if (!canAccess(role)) {
    return <Navigate to="/forbidden" replace />;
  }

  return <>{children}</>;
}
