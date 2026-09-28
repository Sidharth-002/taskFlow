import { useMemo } from "react";
import { useSearchParams } from "react-router-dom";
import type { TicketListItem, TicketPriority } from "./types";

/** Board filters, kept in the URL so a filtered board can be bookmarked or shared. */
export function useBoardFilters() {
  const [params, setParams] = useSearchParams();

  const filters = useMemo(
    () => ({
      text: params.get("q") ?? "",
      projectId: params.get("project") ? Number(params.get("project")) : null,
      teamId: params.get("team") ? Number(params.get("team")) : null,
      assignee: params.get("assignee") ?? "", // user id, "none", or ""
      priorities: (params.get("prio")?.split(",").filter(Boolean) ?? []) as TicketPriority[],
      mine: params.get("mine") === "1",
    }),
    [params],
  );

  function set(key: string, value: string | null) {
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        if (value) next.set(key, value);
        else next.delete(key);
        return next;
      },
      { replace: true },
    );
  }

  function togglePriority(p: TicketPriority) {
    const next = filters.priorities.includes(p)
      ? filters.priorities.filter((x) => x !== p)
      : [...filters.priorities, p];
    set("prio", next.join(","));
  }

  const active =
    !!filters.text ||
    filters.projectId !== null ||
    filters.teamId !== null ||
    !!filters.assignee ||
    filters.priorities.length > 0 ||
    filters.mine;

  function clear() {
    setParams(new URLSearchParams(), { replace: true });
  }

  function apply(tickets: TicketListItem[], myId: number): TicketListItem[] {
    const text = filters.text.trim().toLowerCase();
    return tickets.filter(
      (t) =>
        (!text || t.title.toLowerCase().includes(text) || `fd-${t.id}` === text) &&
        (filters.projectId === null || t.projectId === filters.projectId) &&
        (filters.teamId === null || t.teamId === filters.teamId) &&
        (!filters.assignee ||
          (filters.assignee === "none" ? t.assignedToId === null : t.assignedToId === Number(filters.assignee))) &&
        (filters.priorities.length === 0 || filters.priorities.includes(t.priority)) &&
        (!filters.mine || t.assignedToId === myId || t.createdById === myId),
    );
  }

  return { filters, set, togglePriority, active, clear, apply };
}
