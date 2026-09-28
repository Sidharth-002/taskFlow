import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useCurrentUser, useAuth } from "../features/auth/AuthContext";
import { listNotifications } from "../features/notifications/api";
import { CreateTicketModal } from "../features/tickets/CreateTicketModal";
import { CommandPalette } from "../features/command/CommandPalette";
import { Icon } from "../shared/ui/Icon";
import { Avatar } from "../shared/ui/Avatar";
import { Logo } from "../shared/ui/Feedback";
import { RolePill } from "../shared/ui/RolePill";
import { fullName } from "../shared/lib/format";
import { isTypingTarget } from "../shared/lib/hooks";
import { ShellContext, type ShellApi } from "./ShellContext";
import { useTheme } from "./theme";
import { NAV_ITEMS } from "./navigation";

const COLLAPSED_KEY = "flowdesk.sidebarCollapsed";
const UNREAD_POLL_MS = 30_000;

function readCollapsed(): boolean {
  try {
    return localStorage.getItem(COLLAPSED_KEY) === "1";
  } catch {
    return false;
  }
}

export function AppShell() {
  const me = useCurrentUser();
  const { logout } = useAuth();
  const navigate = useNavigate();
  const { theme, toggleTheme } = useTheme();

  const [collapsed, setCollapsed] = useState(readCollapsed);
  const [createFor, setCreateFor] = useState<{ projectId?: number } | null>(null);
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const [menuOpen, setMenuOpen] = useState(false);
  const pendingChord = useRef<number | null>(null);

  const refreshUnread = useCallback(() => {
    listNotifications({ size: 100 })
      .then((page) => setUnreadCount(page.content.filter((n) => !n.read).length))
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    refreshUnread();
    const handle = window.setInterval(refreshUnread, UNREAD_POLL_MS);
    return () => window.clearInterval(handle);
  }, [refreshUnread]);

  useEffect(() => {
    try {
      localStorage.setItem(COLLAPSED_KEY, collapsed ? "1" : "0");
    } catch {
      /* storage unavailable - the toggle just won't persist */
    }
  }, [collapsed]);

  // Global shortcuts: Ctrl/Cmd+K or / for the palette, C to create, and
  // Jira/GitHub-style "g then <key>" chords for navigation.
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setPaletteOpen((open) => !open);
        return;
      }
      if (e.metaKey || e.ctrlKey || e.altKey || isTypingTarget(e.target) || document.querySelector(".modal-overlay")) {
        return;
      }
      const key = e.key.toLowerCase();
      if (pendingChord.current !== null) {
        window.clearTimeout(pendingChord.current);
        pendingChord.current = null;
        const item = NAV_ITEMS.find((n) => n.chord === key);
        if (item) {
          e.preventDefault();
          navigate(item.to);
        }
        return;
      }
      if (key === "g") {
        pendingChord.current = window.setTimeout(() => (pendingChord.current = null), 900);
      } else if (key === "c") {
        e.preventDefault();
        setCreateFor({});
      } else if (key === "/") {
        e.preventDefault();
        setPaletteOpen(true);
      }
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [navigate]);

  const shell = useMemo<ShellApi>(
    () => ({
      openCreateTicket: (projectId) => setCreateFor({ projectId }),
      openCommandPalette: () => setPaletteOpen(true),
      unreadCount,
      refreshUnread,
    }),
    [unreadCount, refreshUnread],
  );

  async function handleLogout() {
    await logout();
    navigate("/login");
  }

  return (
    <ShellContext.Provider value={shell}>
      <div className={`shell ${collapsed ? "collapsed" : ""}`}>
        <aside className="sidebar">
          <div className="sidebar-brand">
            <Logo size={34} />
            <span className="brand-text">FlowDesk</span>
          </div>

          <button className="btn btn-primary btn-create" onClick={() => setCreateFor({})} title="Create issue (C)">
            <Icon name="plus" />
            <span className="nav-label">Create</span>
            <kbd className="nav-label">C</kbd>
          </button>

          <nav className="sidebar-nav">
            {NAV_ITEMS.map((item) => (
              <NavLink key={item.to} to={item.to} className="nav-item" title={`${item.label} (g ${item.chord})`}>
                <Icon name={item.icon} />
                <span className="nav-label">{item.label}</span>
                {item.to === "/notifications" && unreadCount > 0 && (
                  <span className="nav-badge">{unreadCount > 99 ? "99+" : unreadCount}</span>
                )}
              </NavLink>
            ))}
          </nav>

          <button
            className="nav-item collapse-toggle"
            onClick={() => setCollapsed((c) => !c)}
            aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
          >
            <Icon name={collapsed ? "chevronRight" : "chevronLeft"} />
            <span className="nav-label">Collapse</span>
          </button>
        </aside>

        <div className="shell-main">
          <header className="topbar">
            <button className="search-trigger" onClick={() => setPaletteOpen(true)}>
              <Icon name="search" />
              <span>Search issues, jump anywhere…</span>
              <kbd>Ctrl K</kbd>
            </button>

            <div className="topbar-actions">
              <button
                className="icon-btn"
                onClick={toggleTheme}
                aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} mode`}
                title="Toggle theme"
              >
                <Icon name={theme === "dark" ? "sun" : "moon"} />
              </button>
              <NavLink to="/notifications" className="icon-btn bell" aria-label="Inbox">
                <Icon name="bell" />
                {unreadCount > 0 && <span className="bell-dot" />}
              </NavLink>
              <div className="user-menu">
                <button className="user-chip" onClick={() => setMenuOpen((o) => !o)} aria-expanded={menuOpen}>
                  <Avatar name={fullName(me)} size={32} />
                  <span className="user-chip-text">
                    <strong>{me.firstName}</strong>
                    <small>{me.email}</small>
                  </span>
                </button>
                {menuOpen && (
                  <>
                    <div className="menu-backdrop" onClick={() => setMenuOpen(false)} />
                    <div className="menu" role="menu">
                      <div className="menu-head">
                        <Avatar name={fullName(me)} size={40} />
                        <div>
                          <strong>{fullName(me)}</strong>
                          <RolePill role={me.role} />
                        </div>
                      </div>
                      <button className="menu-item" role="menuitem" onClick={handleLogout}>
                        <Icon name="logout" /> Log out
                      </button>
                    </div>
                  </>
                )}
              </div>
            </div>
          </header>

          <main className="content">
            <Outlet />
          </main>
        </div>
      </div>

      {createFor && <CreateTicketModal onClose={() => setCreateFor(null)} defaultProjectId={createFor.projectId} />}
      {paletteOpen && (
        <CommandPalette
          onClose={() => setPaletteOpen(false)}
          onCreateTicket={() => {
            setPaletteOpen(false);
            setCreateFor({});
          }}
          onToggleTheme={toggleTheme}
        />
      )}
    </ShellContext.Provider>
  );
}
