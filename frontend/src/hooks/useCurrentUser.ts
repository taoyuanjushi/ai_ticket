import { useQuery } from "@tanstack/react-query";
import { api } from "../api/client";
import { isMockApiEnabled } from "../api/http";
import { useAuthStore } from "../state/authStore";

export function useCurrentUser() {
  const token = useAuthStore((state) => state.token);
  const setUser = useAuthStore((state) => state.setUser);

  return useQuery({
    queryKey: ["auth", "me"],
    queryFn: async () => {
      const user = await api.me();
      setUser(user);
      return user;
    },
    enabled: Boolean(token) && !isMockApiEnabled(),
    retry: false,
    staleTime: 60_000,
  });
}
