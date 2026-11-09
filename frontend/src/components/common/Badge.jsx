import React from 'react';

export default function Badge({ status, text, children, size = 'default', variant }) {
  const label = children || text || status || 'General';
  const norm = String(label).trim().toLowerCase();

  let styles = 'bg-slate-100 text-slate-700 border-slate-200';

  if (variant === 'info' || norm.includes('10-point') || norm.includes('scale')) {
    styles = 'bg-sky-50 text-sky-700 border-sky-200';
  } else if (variant === 'indigo' || norm.includes('course') || norm.includes('credits')) {
    styles = 'bg-indigo-50 text-indigo-700 border-indigo-200';
  } else if (norm.includes('good') || norm.includes('approved') || norm.includes('high') || norm.includes('low risk')) {
    styles = 'bg-emerald-50 text-emerald-700 border-emerald-200';
  } else if (norm.includes('consistent') || norm.includes('stable') || norm.includes('medium')) {
    styles = 'bg-blue-50 text-blue-700 border-blue-200';
  } else if (norm.includes('monitor') || norm.includes('review') || norm.includes('moderate')) {
    styles = 'bg-amber-50 text-amber-700 border-amber-200';
  } else if (norm.includes('support') || norm.includes('critical') || norm.includes('high risk') || norm.includes('attention')) {
    styles = 'bg-rose-50 text-rose-700 border-rose-200';
  }

  const sizeClass = size === 'small' ? 'px-2 py-0.5 text-xs' : 'px-2.5 py-1 text-xs font-semibold';

  return (
    <span className={`inline-flex items-center rounded-full border ${sizeClass} ${styles} whitespace-nowrap`}>
      {label}
    </span>
  );
}
