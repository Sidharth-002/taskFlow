import { useState } from "react";

export interface Slice {
  key: string;
  label: string;
  value: number;
  color: string;
}

/** SVG donut with a hover-driven centre readout. */
export function Donut({ slices, size = 200, label }: { slices: Slice[]; size?: number; label: string }) {
  const [hovered, setHovered] = useState<string | null>(null);
  const total = slices.reduce((sum, s) => sum + s.value, 0);
  const radius = size / 2 - 14;
  const circumference = 2 * Math.PI * radius;
  const gap = total > 0 && slices.filter((s) => s.value > 0).length > 1 ? 3 : 0;
  const active = slices.find((s) => s.key === hovered);

  let offset = 0;
  return (
    <div className="donut-wrap">
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label={label}>
        <circle cx={size / 2} cy={size / 2} r={radius} className="donut-track" strokeWidth={20} fill="none" />
        {total > 0 &&
          slices.map((s) => {
            if (s.value === 0) return null;
            const length = (s.value / total) * circumference;
            const dash = Math.max(0, length - gap);
            const el = (
              <circle
                key={s.key}
                cx={size / 2}
                cy={size / 2}
                r={radius}
                fill="none"
                stroke={s.color}
                strokeWidth={hovered === s.key ? 26 : 20}
                strokeDasharray={`${dash} ${circumference - dash}`}
                strokeDashoffset={-offset}
                strokeLinecap="butt"
                transform={`rotate(-90 ${size / 2} ${size / 2})`}
                className="donut-slice"
                onMouseEnter={() => setHovered(s.key)}
                onMouseLeave={() => setHovered(null)}
              >
                <title>{`${s.label}: ${s.value}`}</title>
              </circle>
            );
            offset += length;
            return el;
          })}
        <text x="50%" y="47%" textAnchor="middle" className="donut-value">
          {active ? active.value : total}
        </text>
        <text x="50%" y="60%" textAnchor="middle" className="donut-caption">
          {active ? active.label : "total"}
        </text>
      </svg>
      <ul className="legend">
        {slices.map((s) => (
          <li
            key={s.key}
            onMouseEnter={() => setHovered(s.key)}
            onMouseLeave={() => setHovered(null)}
            className={hovered === s.key ? "active" : ""}
          >
            <span className="dot" style={{ background: s.color }} />
            <span>{s.label}</span>
            <strong>{s.value}</strong>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** Horizontal bars, each labelled directly with its value. */
export function BarList({ rows, emptyText }: { rows: Slice[]; emptyText?: string }) {
  const max = Math.max(1, ...rows.map((r) => r.value));
  if (rows.length === 0) return <p className="muted">{emptyText ?? "No data yet"}</p>;
  return (
    <ul className="bar-list">
      {rows.map((r) => (
        <li key={r.key}>
          <span className="bar-label">{r.label}</span>
          <span className="bar-track">
            <span className="bar-fill" style={{ width: `${(r.value / max) * 100}%`, background: r.color }} />
          </span>
          <strong className="bar-value">{r.value}</strong>
        </li>
      ))}
    </ul>
  );
}

/** Two big opposing numbers with a split bar - used for created vs closed this week. */
export function Versus({
  left,
  right,
}: {
  left: { label: string; value: number; color: string };
  right: { label: string; value: number; color: string };
}) {
  const total = left.value + right.value;
  const leftPct = total === 0 ? 50 : (left.value / total) * 100;
  return (
    <div className="versus">
      <div className="versus-numbers">
        <div>
          <strong style={{ color: left.color }}>{left.value}</strong>
          <span>{left.label}</span>
        </div>
        <div className="right">
          <strong style={{ color: right.color }}>{right.value}</strong>
          <span>{right.label}</span>
        </div>
      </div>
      <div className="versus-bar">
        <span style={{ width: `${leftPct}%`, background: left.color }} />
        <span style={{ width: `${100 - leftPct}%`, background: right.color }} />
      </div>
    </div>
  );
}
