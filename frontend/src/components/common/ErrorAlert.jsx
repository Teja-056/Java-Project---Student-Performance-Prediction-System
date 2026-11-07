import React from 'react';
import { AlertCircle, X } from 'lucide-react';

export default function ErrorAlert({ message, onDismiss, retryAction }) {
  if (!message) return null;

  return (
    <div className="bg-rose-50 border border-rose-200 text-rose-800 rounded-xl p-4 flex items-start justify-between space-x-3 shadow-xs mb-6">
      <div className="flex items-start space-x-3">
        <AlertCircle className="w-5 h-5 text-rose-600 mt-0.5 shrink-0" />
        <div className="text-sm">
          <p className="font-semibold text-rose-900">Error</p>
          <p className="mt-0.5 text-rose-700 leading-relaxed">{message}</p>
          {retryAction && (
            <button
              onClick={retryAction}
              className="mt-2 text-xs font-semibold text-rose-800 hover:text-rose-950 underline cursor-pointer"
            >
              Try Again
            </button>
          )}
        </div>
      </div>
      {onDismiss && (
        <button
          onClick={onDismiss}
          className="text-rose-400 hover:text-rose-600 p-1 rounded-md transition-colors"
          aria-label="Dismiss"
        >
          <X className="w-4 h-4" />
        </button>
      )}
    </div>
  );
}
