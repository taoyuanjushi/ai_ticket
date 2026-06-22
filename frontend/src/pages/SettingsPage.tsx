import { Database, Server, ShieldCheck } from "lucide-react";
import { PageHeader } from "../components/PageHeader";
import { StatCard } from "../components/StatCard";

export function SettingsPage() {
  return (
    <div>
      <PageHeader eyebrow="System" title="System Overview" description="Frontend integration points for Java and Python services." />
      <div className="grid gap-4 px-5 py-5 md:grid-cols-3">
        <StatCard label="Java API" value="8080" icon={Server} tone="blue" />
        <StatCard label="Python AI" value="via Java" icon={Database} tone="amber" />
        <StatCard label="Auth" value="JWT" icon={ShieldCheck} tone="green" />
      </div>
      <div className="mx-5 rounded border border-line bg-white p-5 text-sm leading-6 text-muted">
        <p>
          Development uses the Vite <code>/api</code> proxy for Java. AI requests also go through Java <code>/ai/*</code>,
          and Java forwards the trusted user identity to Python.
        </p>
      </div>
    </div>
  );
}
