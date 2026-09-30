import type { IconName } from "../shared/ui/Icon";

export interface NavItem {
  to: string;
  label: string;
  icon: IconName;
  chord: string;
}

export const NAV_ITEMS: NavItem[] = [
  { to: "/dashboard", label: "Dashboard", icon: "dashboard", chord: "d" },
  { to: "/board", label: "Board", icon: "board", chord: "b" },
  { to: "/tickets", label: "Issues", icon: "list", chord: "i" },
  { to: "/projects", label: "Projects", icon: "folder", chord: "p" },
  { to: "/teams", label: "Teams", icon: "team", chord: "t" },
  { to: "/people", label: "People", icon: "people", chord: "u" },
  { to: "/notifications", label: "Inbox", icon: "bell", chord: "n" },
];
