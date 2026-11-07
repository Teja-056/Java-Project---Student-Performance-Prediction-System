import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  Users,
  Upload,
  BarChart3,
  BookOpen,
  ChevronLeft,
  ChevronRight,
  GraduationCap,
  Sparkles,
} from 'lucide-react';

export default function Sidebar({ collapsed, setCollapsed }) {
  const navItems = [
    { name: 'Dashboard', path: '/', icon: LayoutDashboard },
    { name: 'Students', path: '/students', icon: Users },
    { name: 'Subject Analytics', path: '/analytics', icon: BarChart3 },
    { name: 'Excel Upload', path: '/excel-upload', icon: Upload },
    { name: 'Syllabus Management', path: '/syllabus', icon: BookOpen },
  ];

  return (
    <aside
      className={`fixed top-0 left-0 z-30 h-screen bg-slate-900 text-white transition-all duration-300 flex flex-col justify-between border-r border-slate-800 ${
        collapsed ? 'w-20' : 'w-64'
      }`}
    >
      <div>
        {/* Brand / Logo */}
        <div className="h-16 flex items-center justify-between px-4 border-b border-slate-800">
          <div className="flex items-center space-x-3 overflow-hidden">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-600 to-violet-500 flex items-center justify-center text-white shrink-0 shadow-md shadow-indigo-500/20">
              <GraduationCap className="w-6 h-6" />
            </div>
            {!collapsed && (
              <div className="flex flex-col truncate">
                <span className="font-bold text-sm tracking-tight text-white flex items-center gap-1.5">
                  EduPredict <Sparkles className="w-3.5 h-3.5 text-amber-400" />
                </span>
                <span className="text-[11px] text-slate-400 truncate">Faculty Analytics</span>
              </div>
            )}
          </div>
          <button
            onClick={() => setCollapsed(!collapsed)}
            className="hidden md:flex p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
            title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          >
            {collapsed ? <ChevronRight className="w-4 h-4" /> : <ChevronLeft className="w-4 h-4" />}
          </button>
        </div>

        {/* Navigation links */}
        <nav className="p-3 space-y-1.5">
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.path}
                to={item.path}
                end={item.path === '/'}
                className={({ isActive }) =>
                  `flex items-center space-x-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all ${
                    isActive
                      ? 'bg-indigo-600 text-white shadow-sm shadow-indigo-600/30'
                      : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
                  } ${collapsed ? 'justify-center px-0' : ''}`
                }
                title={collapsed ? item.name : undefined}
              >
                <Icon className="w-5 h-5 shrink-0" />
                {!collapsed && <span className="truncate">{item.name}</span>}
              </NavLink>
            );
          })}
        </nav>
      </div>

      {/* Footer System Status */}
      <div className="p-4 border-t border-slate-800">
        {!collapsed ? (
          <div className="bg-slate-800/60 rounded-xl p-3 text-xs text-slate-400 space-y-1">
            <div className="flex items-center justify-between font-medium text-slate-300">
              <span>Backend Status</span>
              <span className="flex items-center gap-1 text-emerald-400 font-semibold text-[11px]">
                <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span> Connected
              </span>
            </div>
            <p className="text-[11px] text-slate-500 truncate">Spring Boot 3.3.5 • Port 8080</p>
          </div>
        ) : (
          <div className="flex justify-center" title="System Online">
            <span className="w-2.5 h-2.5 rounded-full bg-emerald-400"></span>
          </div>
        )}
      </div>
    </aside>
  );
}
