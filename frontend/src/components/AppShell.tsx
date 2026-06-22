import {
  Activity,
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
import { useAuthStore } from "../state/authStore";
import type { UserRole } from "../types/domain";

export function AppShell() {
  const user = useAuthStore((state) => state.user);
  const clearSession = useAuthStore((state) => state.clearSession);
  const role = user?.role ?? "USER";

  return (
    <div className="min-h-screen bg-panel text-ink">
      <div className="grid min-h-screen lg:grid-cols-[260px_1fr]">
        <aside className="border-r border-line bg-white">
          <div className="flex h-16 items-center justify-between border-b border-line px-5">
            <div>
              <p className="text-sm font-semibold">AI Ticket Desk</p>
              <p className="text-xs text-muted">{role}</p>
            </div>
            <Menu className="h-5 w-5 text-slate-400 lg:hidden" aria-hidden="true" />
          </div>

          <nav className="space-y-1 px-3 py-4">
            <NavItem to="/" icon={Ticket} label="Ticket Desk" />
            <NavItem to="/tickets/new" icon={Plus} label="Create Ticket" />
            <NavItem to="/ai" icon={MessageSquare} label="AI Assistant" />
            {hasRole(role, "ADMIN") ? <NavItem to="/users" icon={Users} label="Users" /> : null}
            {hasRole(role, "ADMIN") ? <NavItem to="/logs" icon={Activity} label="Operation Logs" /> : null}
            {hasRole(role, "ADMIN") ? <NavItem to="/settings" icon={Shield} label="System" /> : null}
          </nav>

          <div className="border-t border-line p-4">
            <div className="rounded border border-line bg-panel p-3">
              <p className="text-sm font-semibold">{user?.name ?? "Guest"}</p>
              <p className="text-xs text-muted">{user?.email ?? ""}</p>
            </div>
            <Button className="mt-3 w-full" variant="secondary" onClick={() => clearSession()}>
              <LogOut className="h-4 w-4" aria-hidden="true" />
              Sign Out
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

function hasRole(role: UserRole, minimum: "STAFF" | "ADMIN") {
  if (minimum === "STAFF") {
    return role === "STAFF" || role === "ADMIN";
  }
  return role === "ADMIN";
}
