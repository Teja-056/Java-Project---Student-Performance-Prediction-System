import React from 'react';

export default function LoadingSpinner({ message = 'Loading data...', size = 'default' }) {
  const sizeClasses = {
    small: 'w-5 h-5 border-2',
    default: 'w-8 h-8 border-3',
    large: 'w-12 h-12 border-4',
  }[size] || 'w-8 h-8 border-3';

  return (
    <div className="flex flex-col items-center justify-center p-8 text-center space-y-3">
      <div
        className={`${sizeClasses} border-indigo-200 border-t-indigo-600 rounded-full animate-spin`}
        role="status"
        aria-label="Loading"
      />
      {message && <p className="text-sm font-medium text-slate-500">{message}</p>}
    </div>
  );
}
