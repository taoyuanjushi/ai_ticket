import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ShieldCheck } from "lucide-react";
import { useState, type ReactNode } from "react";
import { api } from "../api/client";
import { Button } from "../components/Button";
import { ErrorNotice } from "../components/ErrorNotice";
import { Field, TextInput } from "../components/Field";
import { useAuthStore } from "../state/authStore";

type Mode = "login" | "register";

export function LoginPage() {
  const [mode, setMode] = useState<Mode>("login");
  const [username, setUsername] = useState("staff01");
  const [password, setPassword] = useState("123456");
  const [name, setName] = useState("Staff One");
  const [age, setAge] = useState("30");
  const [email, setEmail] = useState("staff01@example.com");
  const [error, setError] = useState("");
  const setSession = useAuthStore((state) => state.setSession);
  const queryClient = useQueryClient();

  const loginMutation = useMutation({
    mutationFn: async () => api.login({ username, password }),
    onSuccess: async (data) => {
      setSession(data.token, {
        id: data.userId,
        username: data.username,
        name: data.username,
        email: `${data.username}@example.com`,
        role: data.role,
      });
      await queryClient.invalidateQueries({ queryKey: ["auth", "me"] });
    },
    onError: (err: unknown) => {
      setError(errorMessage(err));
    },
  });

  const registerMutation = useMutation({
    mutationFn: async () =>
      api.register({
        username,
        password,
        name,
        age: Number(age),
        email,
      }),
    onSuccess: async () => {
      const data = await api.login({ username, password });
      setSession(data.token, {
        id: data.userId,
        username: data.username,
        name: data.username,
        email: `${data.username}@example.com`,
        role: data.role,
      });
    },
    onError: (err: unknown) => {
      setError(errorMessage(err));
    },
  });

  const busy = loginMutation.isPending || registerMutation.isPending;

  return (
    <div className="grid min-h-screen bg-panel lg:grid-cols-[1.05fr_0.95fr]">
      <div className="flex flex-col justify-between border-r border-line bg-white px-6 py-8 lg:px-10">
        <div className="max-w-xl">
          <div className="inline-flex items-center gap-2 rounded border border-line bg-panel px-3 py-1 text-xs font-semibold text-brand">
            <ShieldCheck className="h-4 w-4" aria-hidden="true" />
            AI Ticket Desk
          </div>
          <h1 className="mt-6 text-4xl font-semibold tracking-tight text-ink">Ticket Operations Console</h1>
          <p className="mt-4 max-w-lg text-sm leading-6 text-muted">
            A focused workspace for ticket triage, replies, status changes, user administration, and AI-assisted support.
          </p>
        </div>

        <div className="mt-10 grid gap-3 text-sm text-muted sm:grid-cols-3">
          <InfoPill title="Ticket Flow" text="List, filter, inspect, reply" />
          <InfoPill title="Roles" text="USER / STAFF / ADMIN" />
          <InfoPill title="AI Assist" text="Chat and reply suggestions" />
        </div>
      </div>

      <div className="flex items-center justify-center px-6 py-10">
        <div className="w-full max-w-md rounded border border-line bg-white p-6 shadow-soft">
          <div className="flex rounded bg-panel p-1">
            <TabButton active={mode === "login"} onClick={() => setMode("login")}>
              Login
            </TabButton>
            <TabButton active={mode === "register"} onClick={() => setMode("register")}>
              Register
            </TabButton>
          </div>

          <div className="mt-6 grid gap-4">
            {error ? <ErrorNotice message={error} /> : null}
            <Field label="Username">
              <TextInput value={username} onChange={(event) => setUsername(event.target.value)} />
            </Field>
            <Field label="Password">
              <TextInput type="password" value={password} onChange={(event) => setPassword(event.target.value)} />
            </Field>

            {mode === "register" ? (
              <>
                <Field label="Name">
                  <TextInput value={name} onChange={(event) => setName(event.target.value)} />
                </Field>
                <Field label="Age">
                  <TextInput value={age} onChange={(event) => setAge(event.target.value)} inputMode="numeric" />
                </Field>
                <Field label="Email">
                  <TextInput value={email} onChange={(event) => setEmail(event.target.value)} />
                </Field>
              </>
            ) : null}

            <Button
              variant="primary"
              disabled={busy}
              onClick={() => {
                setError("");
                if (mode === "login") {
                  loginMutation.mutate();
                } else {
                  registerMutation.mutate();
                }
              }}
              className="w-full"
            >
              {busy ? "Working..." : mode === "login" ? "Enter Desk" : "Create Account"}
            </Button>

            <p className="text-xs leading-5 text-muted">
              Enable mock mode for local UI testing without Java or Python services.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

function InfoPill({ title, text }: { title: string; text: string }) {
  return (
    <div className="rounded border border-line bg-white px-4 py-3">
      <p className="text-sm font-semibold text-ink">{title}</p>
      <p className="mt-1 text-xs leading-5 text-muted">{text}</p>
    </div>
  );
}

function TabButton({
  active,
  children,
  onClick,
}: {
  active: boolean;
  children: ReactNode;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={`h-9 flex-1 rounded px-3 text-sm font-semibold ${active ? "bg-white text-ink shadow-sm" : "text-muted"}`}
    >
      {children}
    </button>
  );
}

function errorMessage(error: unknown) {
  if (error instanceof Error) return error.message;
  return "Request failed";
}
