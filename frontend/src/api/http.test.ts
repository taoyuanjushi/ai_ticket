import { afterEach, describe, expect, it } from "vitest";
import { clearAuthSessionIfExpired, isAuthExpiredMessage } from "./http";
import { useAuthStore } from "../state/authStore";

const testUser = {
  id: 1,
  username: "staff01",
  name: "Staff One",
  email: "staff01@example.com",
  role: "STAFF" as const,
};

describe("http auth handling", () => {
  afterEach(() => {
    useAuthStore.getState().clearSession();
  });

  it("recognizes Java token expired messages", () => {
    expect(isAuthExpiredMessage("Token格式错误")).toBe(true);
    expect(isAuthExpiredMessage("登录状态已失效，请重新登录。")).toBe(true);
    expect(isAuthExpiredMessage("请重新登录")).toBe(true);
    expect(isAuthExpiredMessage("token invalid")).toBe(true);
  });

  it("clears local auth state for 401 responses", () => {
    useAuthStore.getState().setSession("token", testUser);

    const cleared = clearAuthSessionIfExpired(401, "登录状态已失效，请重新登录。");

    expect(cleared).toBe(true);
    expect(useAuthStore.getState().token).toBeNull();
    expect(useAuthStore.getState().user).toBeNull();
  });

  it("does not clear local auth state for permission failures", () => {
    useAuthStore.getState().setSession("token", testUser);

    const cleared = clearAuthSessionIfExpired(403, "你没有权限执行该操作。");

    expect(cleared).toBe(false);
    expect(useAuthStore.getState().token).toBe("token");
    expect(useAuthStore.getState().user).toEqual(testUser);
  });
});
