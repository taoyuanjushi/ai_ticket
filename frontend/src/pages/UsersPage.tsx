import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api, roleOptions } from "../api/client";
import { Button } from "../components/Button";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Field, SelectInput, TextInput } from "../components/Field";
import { Loading } from "../components/Loading";
import { PageHeader } from "../components/PageHeader";
import { useI18n } from "../i18n";
import type { User, UserRole } from "../types/domain";

export function UsersPage() {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const usersQuery = useQuery({
    queryKey: ["users"],
    queryFn: api.getUsers,
  });
  const [form, setForm] = useState<Partial<User>>({
    username: "",
    password: "",
    name: "",
    age: 20,
    email: "",
    role: "USER",
  });

  const createMutation = useMutation({
    mutationFn: () =>
      api.createUser({
        username: String(form.username ?? ""),
        password: String(form.password ?? ""),
        name: String(form.name ?? ""),
        age: Number(form.age ?? 0),
        email: String(form.email ?? ""),
        role: (form.role as UserRole) ?? "USER",
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["users"] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.deleteUser(id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["users"] });
    },
  });

  return (
    <div>
      <PageHeader eyebrow={t("admin.admin")} title={t("admin.users")} description={t("admin.usersDescription")} />
      <div className="grid gap-5 px-5 py-5 xl:grid-cols-[1fr_360px]">
        <section className="rounded border border-line bg-white">
          {usersQuery.isLoading ? <Loading /> : null}
          {usersQuery.error instanceof Error ? <ErrorNotice message={usersQuery.error.message} /> : null}
          {usersQuery.data ? (
            usersQuery.data.length > 0 ? (
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-line">
                  <thead className="bg-panel text-left text-xs font-semibold uppercase tracking-wide text-muted">
                    <tr>
                      <th className="px-4 py-3">ID</th>
                      <th className="px-4 py-3">{t("auth.username")}</th>
                      <th className="px-4 py-3">{t("auth.name")}</th>
                      <th className="px-4 py-3">{t("auth.email")}</th>
                      <th className="px-4 py-3">{t("auth.role")}</th>
                      <th className="px-4 py-3"></th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line text-sm">
                    {usersQuery.data.map((user) => (
                      <tr key={user.id}>
                        <td className="px-4 py-3">{user.id}</td>
                        <td className="px-4 py-3">{user.username}</td>
                        <td className="px-4 py-3">{user.name}</td>
                        <td className="px-4 py-3">{user.email}</td>
                        <td className="px-4 py-3">{user.role}</td>
                        <td className="px-4 py-3 text-right">
                          <Button variant="danger" onClick={() => deleteMutation.mutate(user.id)}>
                            {t("common.delete")}
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <EmptyState title={t("admin.noUsers")} text={t("admin.noUsersText")} />
            )
          ) : null}
        </section>

        <aside className="rounded border border-line bg-white p-5">
          <h2 className="text-base font-semibold">{t("admin.createUser")}</h2>
          <div className="mt-4 grid gap-4">
            <Field label={t("auth.username")}>
              <TextInput value={String(form.username ?? "")} onChange={(event) => setForm((prev) => ({ ...prev, username: event.target.value }))} />
            </Field>
            <Field label={t("auth.password")}>
              <TextInput type="password" value={String(form.password ?? "")} onChange={(event) => setForm((prev) => ({ ...prev, password: event.target.value }))} />
            </Field>
            <Field label={t("auth.name")}>
              <TextInput value={String(form.name ?? "")} onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))} />
            </Field>
            <Field label={t("auth.age")}>
              <TextInput value={String(form.age ?? 20)} onChange={(event) => setForm((prev) => ({ ...prev, age: Number(event.target.value) }))} />
            </Field>
            <Field label={t("auth.email")}>
              <TextInput value={String(form.email ?? "")} onChange={(event) => setForm((prev) => ({ ...prev, email: event.target.value }))} />
            </Field>
            <Field label={t("auth.role")}>
              <SelectInput value={String(form.role ?? "USER")} onChange={(event) => setForm((prev) => ({ ...prev, role: event.target.value as UserRole }))}>
                {roleOptions.map((role) => (
                  <option key={role} value={role}>
                    {role}
                  </option>
                ))}
              </SelectInput>
            </Field>
            <Button variant="primary" onClick={() => createMutation.mutate()}>
              {t("admin.createUser")}
            </Button>
          </div>
        </aside>
      </div>
    </div>
  );
}
