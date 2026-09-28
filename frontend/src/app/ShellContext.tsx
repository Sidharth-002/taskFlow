import { createContext, useContext } from "react";

export interface ShellApi {
  openCreateTicket: (defaultProjectId?: number) => void;
  openCommandPalette: () => void;
  unreadCount: number;
  refreshUnread: () => void;
}

export const ShellContext = createContext<ShellApi | undefined>(undefined);

export function useShell(): ShellApi {
  const context = useContext(ShellContext);
  if (!context) {
    throw new Error("useShell must be used within the AppShell");
  }
  return context;
}
