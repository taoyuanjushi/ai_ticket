import {
  Activity,
  LayoutDashboard,
  LogOut,
  Menu,
  MessageSquare,
  Plus,
  Shield,
  Ticket,
  Users,
  type LucideIcon,
} from "lucide-react";
import { NavLink, Outlet } from "react-router-dom";
import { Button } from "./Button";
import { LanguageSwitcher } from "./LanguageSwitcher";
import {
  canCreateTicket,
  canViewAdminArea,
  canViewOperationLogs,
  normalizeRole,
} from "../auth/permissions";
import { useI18n } from "../i18n";
import { useAuthStore } from "../state/authStore";

export function AppShell() {
  const user = useAuthStore((state) => state.user);
  const clearSession = useAuthStore((state) => state.clearSession);
  const role = normalizeRole(user?.role);
  const { t } = useI18n();

  return (
    <div className="min-h-screen bg-panel text-ink">
      <div className="grid min-h-screen lg:grid-cols-[260px_1fr]">
        <aside className="border-r border-line bg-white">
          <div className="flex h-16 items-center justify-between border-b border-line px-5">
            <div>
              <p className="text-sm font-semibold">AI Ticket Desk</p>
              <p className="text-xs text-muted">{role}</p>
            </div>
            <div className="flex items-center gap-2">
              <LanguageSwitcher />
              <Menu className="h-5 w-5 text-slate-400 lg:hidden" aria-hidden="true" />
            </div>
          </div>

          <nav className="space-y-1 px-3 py-4">
            <NavItem to="/" icon={Ticket} label={t("nav.ticketDesk")} />
            {canCreateTicket(role) ? <NavItem to="/tickets/new" icon={Plus} label={t("nav.createTicket")} /> : null}
            <NavItem to="/ai" icon={MessageSquare} label={t("nav.aiAssistant")} />
            {canViewAdminArea(role) ? <NavItem to="/admin/dashboard" icon={LayoutDashboard} label={t("nav.dashboard")} /> : null}
            {canViewAdminArea(role) ? <NavItem to="/users" icon={Users} label={t("nav.users")} /> : null}
            {canViewOperationLogs(role) ? <NavItem to="/logs" icon={Activity} label={t("nav.operationLogs")} /> : null}
            {canViewAdminArea(role) ? <NavItem to="/settings" icon={Shield} label={t("nav.system")} /> : null}
          </nav>

          <div className="border-t border-line p-4">
            <div className="rounded border border-line bg-panel p-3">
              <p className="text-sm font-semibold">{user?.name ?? t("nav.guest")}</p>
              <p className="text-xs text-muted">{user?.email ?? ""}</p>
            </div>
            <Button className="mt-3 w-full" variant="secondary" onClick={() => clearSession()}>
              <LogOut className="h-4 w-4" aria-hidden="true" />
              {t("nav.signOut")}
            </Button>
          </div>
        </aside>

        <main className="min-w-0">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

function NavItem({ to, icon: Icon, label }: { to: string; icon: LucideIcon; label: string }) {
  return (
    <NavLink
      to={to}
      end={to === "/"}
      className={({ isActive }) =>
        `flex items-center gap-3 rounded px-3 py-2 text-sm font-medium transition ${
          isActive ? "bg-blue-50 text-brand" : "text-slate-700 hover:bg-slate-50"
        }`
      }
    >
      <Icon className="h-4 w-4" aria-hidden="true" />
      {label}
    </NavLink>
  );
}
