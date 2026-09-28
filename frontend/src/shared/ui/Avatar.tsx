const GRADIENTS = [
  ["#7c3aed", "#ec4899"],
  ["#06b6d4", "#3b82f6"],
  ["#f59e0b", "#ef4444"],
  ["#10b981", "#06b6d4"],
  ["#8b5cf6", "#06b6d4"],
  ["#f43f5e", "#f97316"],
  ["#84cc16", "#10b981"],
  ["#6366f1", "#a855f7"],
];

function hash(value: string): number {
  let h = 0;
  for (let i = 0; i < value.length; i++) {
    h = (h * 31 + value.charCodeAt(i)) | 0;
  }
  return Math.abs(h);
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/);
  return ((parts[0]?.[0] ?? "?") + (parts.length > 1 ? parts[parts.length - 1][0] : "")).toUpperCase();
}

export function Avatar({ name, size = 28, title }: { name: string | null; size?: number; title?: string }) {
  if (!name) {
    return (
      <span className="avatar avatar-empty" style={{ width: size, height: size }} title={title ?? "Unassigned"}>
        ?
      </span>
    );
  }
  const [from, to] = GRADIENTS[hash(name) % GRADIENTS.length];
  return (
    <span
      className="avatar"
      title={title ?? name}
      style={{
        width: size,
        height: size,
        fontSize: size * 0.4,
        background: `linear-gradient(135deg, ${from}, ${to})`,
      }}
    >
      {initials(name)}
    </span>
  );
}

export function AvatarStack({ names, max = 4 }: { names: string[]; max?: number }) {
  const shown = names.slice(0, max);
  return (
    <span className="avatar-stack">
      {shown.map((n) => (
        <Avatar key={n} name={n} size={26} />
      ))}
      {names.length > max && <span className="avatar avatar-more">+{names.length - max}</span>}
    </span>
  );
}
