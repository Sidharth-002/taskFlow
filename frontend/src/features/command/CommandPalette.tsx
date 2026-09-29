import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useNavigate } from "react-router-dom";
import { Icon, type IconName } from "../../shared/ui/Icon";
import { useDebounced } from "../../shared/lib/hooks";
import { ticketKey } from "../../shared/lib/format";
import { listTickets } from "../tickets/api";
import type { TicketListItem } from "../tickets/types";
import { StatusPill } from "../tickets/TicketBadges";
import { NAV_ITEMS } from "../../app/navigation";
import { useDirectory } from "../../app/DirectoryContext";

interface Command {
  id: string;
  label: string;
  hint?: string;
  icon: IconName;
  group: string;
  run: () => void;
  ticket?: TicketListItem;
}

export function CommandPalette({
  onClose,
  onCreateTicket,
  onToggleTheme,
}: {
  onClose: () => void;
  onCreateTicket: () => void;
  onToggleTheme: () => void;
}) {
  const navigate = useNavigate();
  const { projects, teams } = useDirectory();
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);
  const [tickets, setTickets] = useState<TicketListItem[]>([]);
  const [searching, setSearching] = useState(false);
  const debounced = useDebounced(query.trim(), 200);
  const listRef = useRef<HTMLDivElement>(null);

  // "FD-42" or "42" jumps straight to that ticket; anything else is a title search.
  const directId = /^(fd-)?(\d+)$/i.exec(query.trim())?.[2];

  useEffect(() => {
    if (!debounced || directId) {
      setTickets([]);
      return;
    }
    let cancelled = false;
    setSearching(true);
    listTickets({ search: debounced, size: 8 })
      .then((page) => !cancelled && setTickets(page.content))
      .catch(() => !cancelled && setTickets([]))
      .finally(() => !cancelled && setSearching(false));
    return () => {
      cancelled = true;
    };
  }, [debounced, directId]);

  const commands = useMemo<Command[]>(() => {
    const go = (to: string) => () => {
      onClose();
      navigate(to);
    };
    const q = query.trim().toLowerCase();
    const matches = (label: string) => !q || label.toLowerCase().includes(q);

    const result: Command[] = [];
    if (directId) {
      result.push({
        id: "direct",
        label: `Open ${ticketKey(Number(directId))}`,
        icon: "arrowRight",
        group: "Jump to",
        run: go(`/tickets/${directId}`),
      });
    }
    tickets.forEach((t) =>
      result.push({
        id: `t${t.id}`,
        label: t.title,
        hint: ticketKey(t.id),
        icon: "list",
        group: "Issues",
        run: go(`/tickets/${t.id}`),
        ticket: t,
      }),
    );
    const actions: Command[] = [
      { id: "create", label: "Create issue", hint: "C", icon: "plus", group: "Actions", run: onCreateTicket },
      { id: "theme", label: "Toggle light / dark mode", icon: "sun", group: "Actions", run: () => { onToggleTheme(); onClose(); } },
      { id: "mine", label: "My open issues", icon: "user", group: "Actions", run: go("/board?mine=1") },
    ];
    result.push(...actions.filter((a) => matches(a.label)));
    NAV_ITEMS.filter((n) => matches(n.label)).forEach((n) =>
      result.push({ id: n.to, label: `Go to ${n.label}`, hint: `g ${n.chord}`, icon: n.icon, group: "Navigate", run: go(n.to) }),
    );
    if (q) {
      projects
        .filter((p) => matches(p.name))
        .slice(0, 4)
        .forEach((p) =>
          result.push({ id: `p${p.id}`, label: `${p.name} board`, icon: "folder", group: "Projects", run: go(`/board?project=${p.id}`) }),
        );
      teams
        .filter((t) => matches(t.name))
        .slice(0, 4)
        .forEach((t) =>
          result.push({ id: `tm${t.id}`, label: t.name, icon: "team", group: "Teams", run: go(`/teams?team=${t.id}`) }),
        );
    }
    return result;
  }, [query, directId, tickets, projects, teams, navigate, onClose, onCreateTicket, onToggleTheme]);

  useEffect(() => setActive(0), [query]);

  useEffect(() => {
    listRef.current?.querySelector(`[data-index="${active}"]`)?.scrollIntoView({ block: "nearest" });
  }, [active]);

  function onKeyDown(e: React.KeyboardEvent) {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActive((i) => Math.min(commands.length - 1, i + 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActive((i) => Math.max(0, i - 1));
    } else if (e.key === "Enter") {
      e.preventDefault();
      commands[active]?.run();
    } else if (e.key === "Escape") {
      onClose();
    }
  }

  let lastGroup = "";
  return createPortal(
    <div className="palette-overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="palette" role="dialog" aria-label="Command palette">
        <div className="palette-input">
          <Icon name="search" />
          <input
            autoFocus
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={onKeyDown}
            placeholder="Search issues, type FD-42, or jump to a page…"
          />
          {searching && <span className="spinner spinner-sm" />}
          <kbd>Esc</kbd>
        </div>
        <div className="palette-list" ref={listRef}>
          {commands.length === 0 && <p className="palette-empty">Nothing matches "{query}"</p>}
          {commands.map((c, index) => {
            const header = c.group !== lastGroup ? <div className="palette-group">{c.group}</div> : null;
            lastGroup = c.group;
            return (
              <div key={c.id}>
                {header}
                <button
                  data-index={index}
                  className={`palette-item ${index === active ? "active" : ""}`}
                  onMouseEnter={() => setActive(index)}
                  onClick={c.run}
                >
                  <Icon name={c.icon} />
                  <span className="palette-label">{c.label}</span>
                  {c.ticket && <StatusPill status={c.ticket.status} />}
                  {c.hint && <kbd>{c.hint}</kbd>}
                </button>
              </div>
            );
          })}
        </div>
        <footer className="palette-footer">
          <span><kbd>↑</kbd><kbd>↓</kbd> navigate</span>
          <span><kbd>Enter</kbd> open</span>
          <span><kbd>g</kbd> then <kbd>b</kbd> board · <kbd>d</kbd> dashboard · <kbd>i</kbd> issues</span>
        </footer>
      </div>
    </div>,
    document.body,
  );
}
