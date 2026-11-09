import React, { useState, useEffect } from 'react';
import { useLocation, useNavigate, Link } from 'react-router-dom';
import { Search, Menu, Bell, ShieldCheck, Layers } from 'lucide-react';
import { datasetService } from '../../api/datasetService';

export default function Header({ onMobileMenuToggle }) {
  const location = useLocation();
  const navigate = useNavigate();
  const [quickRoll, setQuickRoll] = useState('');
  const [activeDataset, setActiveDataset] = useState(null);

  useEffect(() => {
    let isMounted = true;
    datasetService.getActiveDataset()
      .then((data) => {
        if (isMounted) setActiveDataset(data);
      })
      .catch(() => {});
    return () => { isMounted = false; };
  }, [location.pathname]);

  const getPageInfo = () => {
    const path = location.pathname;
    if (path === '/') return { title: 'Academic Performance Dashboard', subtitle: 'Cohort monitoring and predictive diagnostics' };
    if (path.startsWith('/datasets')) return { title: 'Dataset Management', subtitle: 'Multi-cohort student dataset isolation and switching' };
    if (path.startsWith('/students') && path.includes('/report')) return { title: 'Student Performance Report', subtitle: 'Detailed internal assessment breakdown and AI analysis' };
    if (path.startsWith('/students')) return { title: 'Student Management & Search', subtitle: 'Search student records and review individual standings' };
    if (path.startsWith('/analytics')) return { title: 'Subject Performance Analytics', subtitle: 'Seven core theory subjects cohort distribution' };
    if (path.startsWith('/excel-upload')) return { title: 'Bulk Student Excel Upload', subtitle: 'Spreadsheet import with preview and overwrite protection' };
    if (path.startsWith('/syllabus')) return { title: 'Syllabus Management', subtitle: 'Curriculum PDF extraction and topic grounding' };
    return { title: 'Academic Monitoring System', subtitle: 'Faculty portal' };
  };

  const handleQuickSearch = (e) => {
    e.preventDefault();
    const clean = quickRoll.trim();
    if (!clean) return;
    navigate(`/students/${encodeURIComponent(clean)}/report`);
    setQuickRoll('');
  };

  const { title, subtitle } = getPageInfo();

  return (
    <header className="h-16 bg-white border-b border-slate-200/80 px-4 sm:px-6 flex items-center justify-between sticky top-0 z-20">
      <div className="flex items-center space-x-3">
        <button
          onClick={onMobileMenuToggle}
          className="md:hidden p-2 text-slate-500 hover:text-slate-700 hover:bg-slate-100 rounded-lg"
          aria-label="Toggle navigation"
        >
          <Menu className="w-5 h-5" />
        </button>
        <div>
          <h1 className="text-base sm:text-lg font-bold text-slate-900 leading-tight truncate">{title}</h1>
          <p className="text-xs text-slate-500 hidden sm:block truncate">{subtitle}</p>
        </div>
      </div>

      <div className="flex items-center space-x-3">
        {/* Quick Roll Number Lookup */}
        <form onSubmit={handleQuickSearch} className="relative hidden md:block">
          <input
            type="text"
            value={quickRoll}
            onChange={(e) => setQuickRoll(e.target.value)}
            placeholder="Search roll (1602-25-733-XXX)..."
            className="w-64 pl-9 pr-3 py-1.5 text-xs bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all"
          />
          <Search className="w-4 h-4 text-slate-400 absolute left-2.5 top-2" />
        </form>

        <div className="h-6 w-px bg-slate-200 hidden md:block" />

        {activeDataset && (
          <Link
            to="/datasets"
            className="hidden sm:flex items-center space-x-2 bg-emerald-50 hover:bg-emerald-100 border border-emerald-200 rounded-xl px-3 py-1 text-xs text-emerald-800 transition-colors shadow-2xs group cursor-pointer"
            title="Click to manage or switch datasets"
          >
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
            <span className="font-semibold truncate max-w-[140px]">{activeDataset.name}</span>
            <span className="text-emerald-600 font-mono text-[11px]">({activeDataset.validCount} stds)</span>
          </Link>
        )}

        <div className="flex items-center space-x-2 bg-slate-50 border border-slate-200 rounded-xl px-3 py-1 text-xs text-slate-600">
          <ShieldCheck className="w-4 h-4 text-emerald-600" />
          <span className="font-semibold text-slate-800">Faculty Portal</span>
          <span className="text-slate-400">•</span>
          <span className="text-slate-500 font-mono">B.E. CSE</span>
        </div>
      </div>
    </header>
  );
}
