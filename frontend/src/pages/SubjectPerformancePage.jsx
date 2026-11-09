import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import {
  BarChart,
  Bar,
  PieChart as RechartsPie,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from 'recharts';
import {
  BarChart3,
  Award,
  AlertTriangle,
  TrendingUp,
  TrendingDown,
  Users,
  Search,
  RefreshCw,
  FileText,
  CheckCircle2,
  HelpCircle,
  BookOpen,
  Filter,
  ArrowRight,
  ShieldAlert,
} from 'lucide-react';
import { analyticsService } from '../api/analyticsService';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ErrorAlert from '../components/common/ErrorAlert';
import EmptyState from '../components/common/EmptyState';

const SUBJECTS = [
  { code: 'DS', name: 'Data Structures', short: 'Data Structures' },
  { code: 'JAVA', name: 'OOP through Java', short: 'Java' },
  { code: 'CA', name: 'Computer Architecture', short: 'Architecture' },
  { code: 'TTPS', name: 'Transform Techniques, Probability & Statistics', short: 'TTPS' },
  { code: 'OE', name: 'Open Elective', short: 'Open Elective' },
  { code: 'CS', name: 'Communication Skills', short: 'Communication' },
  { code: 'CT', name: 'Critical Thinking', short: 'Critical Thinking' },
  { code: 'HVPE', name: 'HVPE', short: 'HVPE' },
];

const ASSESSMENT_FILTERS = [
  { id: 'COMBINED', label: 'Combined Performance' },
  { id: 'INTERNAL_1', label: 'Internal 1' },
  { id: 'INTERNAL_2', label: 'Internal 2' },
];

const CATEGORY_COLORS = {
  Excellent: '#10b981',           // emerald-500
  'Very Good': '#3b82f6',         // blue-500
  Good: '#6366f1',                // indigo-500
  Average: '#f59e0b',             // amber-500
  'Needs Improvement': '#f97316', // orange-500
  'At Risk': '#ef4444',           // rose-500
};

const CATEGORY_STYLES = {
  Excellent: {
    bg: 'bg-emerald-50',
    border: 'border-emerald-200',
    text: 'text-emerald-800',
    badgeBg: 'bg-emerald-100 text-emerald-800',
    ring: 'ring-2 ring-emerald-500',
  },
  'Very Good': {
    bg: 'bg-blue-50',
    border: 'border-blue-200',
    text: 'text-blue-800',
    badgeBg: 'bg-blue-100 text-blue-800',
    ring: 'ring-2 ring-blue-500',
  },
  Good: {
    bg: 'bg-indigo-50',
    border: 'border-indigo-200',
    text: 'text-indigo-800',
    badgeBg: 'bg-indigo-100 text-indigo-800',
    ring: 'ring-2 ring-indigo-500',
  },
  Average: {
    bg: 'bg-amber-50',
    border: 'border-amber-200',
    text: 'text-amber-800',
    badgeBg: 'bg-amber-100 text-amber-800',
    ring: 'ring-2 ring-amber-500',
  },
  'Needs Improvement': {
    bg: 'bg-orange-50',
    border: 'border-orange-200',
    text: 'text-orange-800',
    badgeBg: 'bg-orange-100 text-orange-800',
    ring: 'ring-2 ring-orange-500',
  },
  'At Risk': {
    bg: 'bg-rose-50',
    border: 'border-rose-200',
    text: 'text-rose-800',
    badgeBg: 'bg-rose-100 text-rose-800',
    ring: 'ring-2 ring-rose-500',
  },
};

export default function SubjectPerformancePage() {
  const [selectedSubject, setSelectedSubject] = useState('DS');
  const [selectedAssessment, setSelectedAssessment] = useState('COMBINED');
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Drilldown Filter & Search
  const [selectedCategory, setSelectedCategory] = useState(null); // null means All
  const [searchQuery, setSearchQuery] = useState('');
  const [activeTab, setActiveTab] = useState('assessed'); // 'assessed' | 'unassessed'

  useEffect(() => {
    loadPerformance();
  }, [selectedSubject, selectedAssessment]);

  const loadPerformance = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await analyticsService.getSubjectPerformance(selectedSubject, selectedAssessment);
      setData(res);
      setSelectedCategory(null); // Reset category filter on subject change
    } catch (err) {
      setError(err.message || 'Failed to load subject performance data');
    } finally {
      setLoading(false);
    }
  };

  // Filter students based on active category card and search query
  const filteredStudents = (data?.students || []).filter((s) => {
    if (selectedCategory && s.category !== selectedCategory) {
      return false;
    }
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase().trim();
      return (
        s.rollNumber.toLowerCase().includes(q) ||
        s.studentName.toLowerCase().includes(q)
      );
    }
    return true;
  });

  // Prepare chart data for Bar Chart
  const barChartData = (data?.categoryStats || []).map((c) => ({
    category: c.category,
    range: c.range,
    count: c.count,
    percentage: c.percentage,
    fill: CATEGORY_COLORS[c.category] || '#64748b',
  }));

  // Prepare chart data for Donut Chart (filter out 0-count categories for neat donut)
  const donutChartData = (data?.categoryStats || [])
    .filter((c) => c.count > 0)
    .map((c) => ({
      name: c.category,
      value: c.count,
      percentage: c.percentage,
      range: c.range,
      fill: CATEGORY_COLORS[c.category] || '#64748b',
    }));

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-6 rounded-2xl border border-slate-200/80 shadow-xs">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl font-bold text-slate-900 tracking-tight">Subject Performance Analytics</h2>
            <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-indigo-50 text-indigo-700 font-mono">
              Phase 4 Dedicated Analytics
            </span>
          </div>
          <p className="text-xs text-slate-500 mt-1">
            Comprehensive cohort performance distribution, 6-category classification, and Internal 1 vs Internal 2 trajectory.
          </p>
        </div>
        <button
          onClick={loadPerformance}
          className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-xl transition-colors cursor-pointer shrink-0"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          Refresh Metrics
        </button>
      </div>

      <ErrorAlert message={error} retryAction={loadPerformance} />

      {/* Control Navigation: Subject Selector & Assessment Filters */}
      <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs space-y-4">
        {/* Subject Selector Pills */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Select Semester III Subject (8 Courses)
            </span>
            {data && (
              <span className="text-[11px] font-mono text-slate-400">
                Code: {data.courseCode} • Credits: {data.credits} • Max CIE: {data.cieMaxMarks}
              </span>
            )}
          </div>
          <div className="flex flex-wrap gap-2">
            {SUBJECTS.map((s) => {
              const isSelected = selectedSubject === s.code;
              return (
                <button
                  key={s.code}
                  onClick={() => setSelectedSubject(s.code)}
                  className={`px-3.5 py-2 rounded-xl text-xs font-semibold transition-all cursor-pointer flex items-center gap-1.5 ${
                    isSelected
                      ? 'bg-indigo-600 text-white shadow-sm shadow-indigo-600/30'
                      : 'bg-slate-50 text-slate-700 hover:bg-slate-100 border border-slate-200/80'
                  }`}
                >
                  <span>{s.short}</span>
                  <span className={`text-[10px] font-mono px-1.5 py-0.2 rounded ${
                    isSelected ? 'bg-indigo-700/80 text-white' : 'bg-slate-200 text-slate-600'
                  }`}>
                    {s.code}
                  </span>
                </button>
              );
            })}
          </div>
        </div>

        {/* Assessment Filter Pills */}
        <div className="pt-3 border-t border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500 mr-1 flex items-center gap-1">
              <Filter className="w-3.5 h-3.5" /> Assessment Filter:
            </span>
            <div className="flex items-center gap-1.5">
              {ASSESSMENT_FILTERS.map((f) => (
                <button
                  key={f.id}
                  onClick={() => setSelectedAssessment(f.id)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors cursor-pointer ${
                    selectedAssessment === f.id
                      ? 'bg-slate-900 text-white shadow-xs'
                      : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                  }`}
                >
                  {f.label}
                </button>
              ))}
            </div>
          </div>

          {data && (
            <div className="text-xs text-slate-500 font-medium">
              Showing <strong>{data.fullName}</strong> ({data.assessmentType.replace('_', ' ')})
            </div>
          )}
        </div>
      </div>

      {loading ? (
        <div className="py-16">
          <LoadingSpinner message="Calculating subject performance analytics..." size="large" />
        </div>
      ) : data ? (
        <>
          {/* Section 1: Summary Metric Cards */}
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 sm:gap-4">
            {/* Total Students */}
            <div className="bg-white p-4 rounded-xl border border-slate-200/80 shadow-xs">
              <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">Total Enrolled</span>
              <p className="text-2xl font-black text-slate-900 mt-1">{data.totalStudents}</p>
              <span className="text-[11px] text-slate-500">Cohort Roster</span>
            </div>

            {/* Assessed Students */}
            <div className="bg-white p-4 rounded-xl border border-slate-200/80 shadow-xs">
              <span className="text-[11px] font-bold text-emerald-600 uppercase tracking-wider">Assessed</span>
              <p className="text-2xl font-black text-emerald-600 mt-1">{data.assessedCount}</p>
              <span className="text-[11px] text-slate-500">
                {data.totalStudents > 0 ? `${((data.assessedCount / data.totalStudents) * 100).toFixed(0)}% evaluated` : '—'}
              </span>
            </div>

            {/* Unassessed Students */}
            <div className="bg-white p-4 rounded-xl border border-slate-200/80 shadow-xs">
              <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">Not Assessed</span>
              <p className={`text-2xl font-black mt-1 ${data.unassessedCount > 0 ? 'text-rose-600' : 'text-slate-400'}`}>
                {data.unassessedCount}
              </p>
              <span className="text-[11px] text-slate-500">Missing marks</span>
            </div>

            {/* Subject Average */}
            <div className="bg-white p-4 rounded-xl border border-slate-200/80 shadow-xs">
              <span className="text-[11px] font-bold text-indigo-600 uppercase tracking-wider">Class Average</span>
              <p className="text-2xl font-black text-indigo-600 mt-1">
                {data.averagePercentage?.toFixed(1)}%
              </p>
              <span className="text-[11px] text-slate-500 font-mono">
                {data.averageMarks?.toFixed(1)} / {data.cieMaxMarks} marks
              </span>
            </div>

            {/* Highest Performer */}
            <div className="bg-white p-4 rounded-xl border border-slate-200/80 shadow-xs">
              <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">Highest Score</span>
              <div className="flex items-baseline gap-1 mt-1">
                <p className="text-2xl font-black text-slate-900">{data.highestScore?.toFixed(1)}</p>
                <span className="text-xs text-slate-400 font-mono">/{data.cieMaxMarks}</span>
              </div>
              <p className="text-[11px] text-slate-600 truncate font-semibold" title={data.highestStudentName}>
                {data.highestStudentName || 'N/A'}
              </p>
            </div>

            {/* Lowest Performer */}
            <div className="bg-white p-4 rounded-xl border border-slate-200/80 shadow-xs">
              <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">Lowest Score</span>
              <div className="flex items-baseline gap-1 mt-1">
                <p className="text-2xl font-black text-slate-900">{data.lowestScore?.toFixed(1)}</p>
                <span className="text-xs text-slate-400 font-mono">/{data.cieMaxMarks}</span>
              </div>
              <p className="text-[11px] text-slate-600 truncate font-semibold" title={data.lowestStudentName}>
                {data.lowestStudentName || 'N/A'}
              </p>
            </div>
          </div>

          {/* Section 2: Visual Charts: Bar Chart & Donut Chart */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Bar Chart: Performance Category Distribution */}
            <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs">
              <div className="flex items-center justify-between mb-4">
                <div>
                  <h3 className="text-sm font-bold text-slate-900 flex items-center gap-1.5">
                    <BarChart3 className="w-4 h-4 text-indigo-600" />
                    Performance Category Distribution
                  </h3>
                  <p className="text-xs text-slate-500">Student count across the 6 autonomous grading tiers</p>
                </div>
                <span className="text-[11px] font-mono text-slate-400">Total Assessed: {data.assessedCount}</span>
              </div>

              <div className="h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={barChartData} margin={{ top: 10, right: 10, left: -20, bottom: 20 }}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
                    <XAxis
                      dataKey="category"
                      tick={{ fill: '#64748b', fontSize: 11 }}
                      interval={0}
                      angle={-20}
                      textAnchor="end"
                      height={45}
                    />
                    <YAxis allowDecimals={false} tick={{ fill: '#64748b', fontSize: 11 }} />
                    <Tooltip
                      formatter={(val, name, item) => [
                        `${val} students (${item.payload.percentage.toFixed(1)}%)`,
                        'Count',
                      ]}
                      labelFormatter={(label, payload) => {
                        const item = payload?.[0]?.payload;
                        return item ? `${label} (${item.range})` : label;
                      }}
                      contentStyle={{
                        borderRadius: '12px',
                        border: '1px solid #e2e8f0',
                        fontSize: '12px',
                        boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)',
                      }}
                    />
                    <Bar dataKey="count" radius={[6, 6, 0, 0]}>
                      {barChartData.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={entry.fill} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>

            {/* Donut Chart: Percentage Distribution */}
            <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs flex flex-col justify-between">
              <div className="flex items-center justify-between mb-2">
                <div>
                  <h3 className="text-sm font-bold text-slate-900 flex items-center gap-1.5">
                    <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                    Cohort Percentage Distribution
                  </h3>
                  <p className="text-xs text-slate-500">Relative share of students in each tier</p>
                </div>
              </div>

              <div className="h-64 flex items-center justify-center">
                <ResponsiveContainer width="100%" height="100%">
                  <RechartsPie>
                    <Pie
                      data={donutChartData}
                      cx="50%"
                      cy="50%"
                      innerRadius={55}
                      outerRadius={85}
                      paddingAngle={3}
                      dataKey="value"
                    >
                      {donutChartData.map((entry, index) => (
                        <Cell key={`donut-${index}`} fill={entry.fill} />
                      ))}
                    </Pie>
                    <Tooltip
                      formatter={(val, name, item) => [
                        `${val} students (${item.payload.percentage.toFixed(1)}%)`,
                        name,
                      ]}
                      contentStyle={{
                        borderRadius: '12px',
                        border: '1px solid #e2e8f0',
                        fontSize: '12px',
                        boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)',
                      }}
                    />
                    <Legend
                      verticalAlign="bottom"
                      height={36}
                      formatter={(value, entry) => (
                        <span className="text-[11px] font-medium text-slate-700">
                          {value} ({entry.payload.percentage.toFixed(0)}%)
                        </span>
                      )}
                    />
                  </RechartsPie>
                </ResponsiveContainer>
              </div>
            </div>
          </div>

          {/* Section 3: Clickable Performance Category Cards (Interactive Drilldown Filter) */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-sm font-bold text-slate-900">
                  Performance Categories (Click any card to filter student drilldown)
                </h3>
                <p className="text-xs text-slate-500">
                  Select a category card below to filter the student roster table.
                </p>
              </div>
              {selectedCategory && (
                <button
                  onClick={() => setSelectedCategory(null)}
                  className="px-2.5 py-1 text-xs font-semibold text-indigo-600 bg-indigo-50 hover:bg-indigo-100 rounded-lg transition-colors cursor-pointer"
                >
                  Clear Filter (Show All)
                </button>
              )}
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
              {(data.categoryStats || []).map((cat) => {
                const style = CATEGORY_STYLES[cat.category] || CATEGORY_STYLES.Average;
                const isSelected = selectedCategory === cat.category;

                return (
                  <button
                    key={cat.category}
                    onClick={() => setSelectedCategory(isSelected ? null : cat.category)}
                    className={`p-3.5 rounded-xl border text-left transition-all cursor-pointer relative overflow-hidden ${
                      style.bg
                    } ${style.border} ${isSelected ? style.ring + ' shadow-md' : 'hover:shadow-sm'}`}
                  >
                    <div className="flex items-center justify-between">
                      <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${style.badgeBg}`}>
                        {cat.range}
                      </span>
                      {isSelected && (
                        <span className="w-2 h-2 rounded-full bg-indigo-600 animate-pulse" />
                      )}
                    </div>
                    <h4 className={`text-xs font-bold mt-2 ${style.text}`}>{cat.category}</h4>
                    <p className="text-xl font-black text-slate-900 mt-0.5">{cat.count}</p>
                    <div className="flex items-center justify-between text-[11px] text-slate-500 mt-1">
                      <span>{cat.percentage.toFixed(1)}%</span>
                      <span className="text-[10px] text-slate-400">of assessed</span>
                    </div>

                    {/* Progress track */}
                    <div className="w-full bg-slate-200/60 h-1.5 rounded-full mt-2 overflow-hidden">
                      <div
                        className="h-full rounded-full"
                        style={{
                          width: `${Math.min(100, cat.percentage)}%`,
                          backgroundColor: CATEGORY_COLORS[cat.category] || '#64748b',
                        }}
                      />
                    </div>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Section 4: Internal 1 vs Internal 2 Comparative Cohort Analysis */}
          <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-4">
              <div>
                <h3 className="text-sm font-bold text-slate-900 flex items-center gap-1.5">
                  <TrendingUp className="w-4 h-4 text-indigo-600" />
                  Internal 1 vs Internal 2 Comparative Cohort Momentum
                </h3>
                <p className="text-xs text-slate-500">
                  Direct tracking of cohort progress between syllabus segments (Units 1-2 vs Units 3-5)
                </p>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-xs font-semibold px-2.5 py-1 rounded-lg bg-slate-100 text-slate-700 font-mono">
                  Net Trajectory: {data.averageChangePercentage != null ? (
                    data.averageChangePercentage >= 0 ? (
                      <span className="text-emerald-700 font-bold">+{data.averageChangePercentage.toFixed(1)}%</span>
                    ) : (
                      <span className="text-rose-700 font-bold">{data.averageChangePercentage.toFixed(1)}%</span>
                    )
                  ) : 'N/A'}
                </span>
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
              <div className="p-3 bg-slate-50 rounded-xl border border-slate-100">
                <span className="text-xs text-slate-500 font-medium">Internal 1 Class Avg</span>
                <p className="text-xl font-bold text-slate-900 mt-1">
                  {data.internal1AveragePercentage != null ? `${data.internal1AveragePercentage.toFixed(1)}%` : '—'}
                </p>
                <span className="text-[11px] text-slate-400">Units 1 & 2</span>
              </div>

              <div className="p-3 bg-slate-50 rounded-xl border border-slate-100">
                <span className="text-xs text-slate-500 font-medium">Internal 2 Class Avg</span>
                <p className="text-xl font-bold text-slate-900 mt-1">
                  {data.internal2AveragePercentage != null ? `${data.internal2AveragePercentage.toFixed(1)}%` : '—'}
                </p>
                <span className="text-[11px] text-slate-400">Units 3, 4 & 5</span>
              </div>

              <div className="p-3 bg-emerald-50/60 rounded-xl border border-emerald-100">
                <span className="text-xs text-emerald-800 font-medium flex items-center gap-1">
                  <TrendingUp className="w-3.5 h-3.5 text-emerald-600" /> Students Improved
                </span>
                <p className="text-xl font-bold text-emerald-700 mt-1">{data.improvedCount}</p>
                <span className="text-[11px] text-emerald-600">Higher Int 2 marks</span>
              </div>

              <div className="p-3 bg-rose-50/60 rounded-xl border border-rose-100">
                <span className="text-xs text-rose-800 font-medium flex items-center gap-1">
                  <TrendingDown className="w-3.5 h-3.5 text-rose-600" /> Students Declined
                </span>
                <p className="text-xl font-bold text-rose-700 mt-1">{data.declinedCount}</p>
                <span className="text-[11px] text-rose-600">Lower Int 2 marks</span>
              </div>
            </div>
          </div>

          {/* Section 5: Student Performance Drilldown Table */}
          <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
            {/* Table Header and Search Bar */}
            <div className="p-4 sm:p-5 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div>
                <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                  <span>Student Performance Drilldown</span>
                  {selectedCategory && (
                    <span className={`text-[11px] font-bold px-2 py-0.5 rounded-full ${
                      CATEGORY_STYLES[selectedCategory]?.badgeBg || 'bg-slate-100 text-slate-700'
                    }`}>
                      Category: {selectedCategory}
                    </span>
                  )}
                </h3>
                <p className="text-xs text-slate-500 mt-0.5">
                  Showing individual student marks and performance classification
                </p>
              </div>

              <div className="flex items-center gap-2">
                <div className="relative w-full sm:w-64">
                  <input
                    type="text"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    placeholder="Search roll or student name..."
                    className="w-full pl-9 pr-3 py-1.5 text-xs bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                  />
                  <Search className="w-4 h-4 text-slate-400 absolute left-2.5 top-2" />
                </div>
              </div>
            </div>

            {/* Table */}
            {filteredStudents.length === 0 ? (
              <div className="p-8">
                <EmptyState
                  title={selectedCategory ? `No students in ${selectedCategory} category` : 'No matching students found'}
                  description="Try adjusting your search query or selecting a different category."
                  icon={Users}
                />
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-50/80 text-slate-500 uppercase tracking-wider font-semibold border-b border-slate-100">
                    <tr>
                      <th className="py-3 px-4">Roll Number</th>
                      <th className="py-3 px-4">Student Name</th>
                      <th className="py-3 px-3 text-right">Obtained Marks</th>
                      <th className="py-3 px-3 text-right">Max Marks</th>
                      <th className="py-3 px-3 text-right">Percentage</th>
                      <th className="py-3 px-4 text-center">Performance Category</th>
                      <th className="py-3 px-3 text-right">Int 1</th>
                      <th className="py-3 px-3 text-right">Int 2</th>
                      <th className="py-3 px-4 text-center">Int 1 vs 2 Trend</th>
                      <th className="py-3 px-4 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 font-medium text-slate-700">
                    {filteredStudents.map((st) => {
                      const trend = st.trendPercentage;
                      const isPositive = trend != null && trend > 0;
                      const isNegative = trend != null && trend < 0;
                      const catStyle = CATEGORY_STYLES[st.category] || CATEGORY_STYLES.Average;

                      return (
                        <tr key={st.rollNumber} className="hover:bg-slate-50/60 transition-colors">
                          <td className="py-3 px-4 font-mono font-semibold text-slate-900">
                            {st.rollNumber}
                          </td>
                          <td className="py-3 px-4 font-semibold text-slate-800">
                            {st.studentName}
                          </td>
                          <td className="py-3 px-3 text-right font-mono font-bold text-slate-900">
                            {st.obtainedMarks?.toFixed(1) || '—'}
                          </td>
                          <td className="py-3 px-3 text-right text-slate-400 font-mono">
                            {st.maxMarks}
                          </td>
                          <td className="py-3 px-3 text-right font-bold text-indigo-600">
                            {st.percentage?.toFixed(1)}%
                          </td>
                          <td className="py-3 px-4 text-center">
                            <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-[11px] font-semibold ${catStyle.badgeBg}`}>
                              {st.category}
                            </span>
                          </td>
                          <td className="py-3 px-3 text-right text-slate-600 font-mono">
                            {st.internal1Marks != null ? st.internal1Marks.toFixed(1) : '—'}
                          </td>
                          <td className="py-3 px-3 text-right text-slate-600 font-mono">
                            {st.internal2Marks != null ? st.internal2Marks.toFixed(1) : '—'}
                          </td>
                          <td className="py-3 px-4 text-center">
                            {trend != null ? (
                              <span
                                className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-semibold ${
                                  isPositive
                                    ? 'bg-emerald-50 text-emerald-700'
                                    : isNegative
                                    ? 'bg-rose-50 text-rose-700'
                                    : 'bg-slate-100 text-slate-600'
                                }`}
                              >
                                {isPositive && <TrendingUp className="w-3 h-3" />}
                                {isNegative && <TrendingDown className="w-3 h-3" />}
                                {isPositive ? `+${trend.toFixed(1)}%` : `${trend.toFixed(1)}%`}
                              </span>
                            ) : (
                              <span className="text-slate-400">—</span>
                            )}
                          </td>
                          <td className="py-3 px-4 text-right space-x-1 whitespace-nowrap">
                            <Link
                              to={`/prediction/${encodeURIComponent(st.rollNumber)}`}
                              className="inline-flex items-center gap-1 px-2 py-1 bg-emerald-50 hover:bg-emerald-100 text-emerald-700 font-semibold rounded-lg transition-colors text-[11px]"
                              title="SEE Predictions"
                            >
                              <TrendingUp className="w-3 h-3" /> Predict
                            </Link>
                            <Link
                              to={`/students/${encodeURIComponent(st.rollNumber)}/report`}
                              className="inline-flex items-center gap-1 px-2 py-1 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 font-semibold rounded-lg transition-colors text-[11px]"
                              title="Detailed Report"
                            >
                              <FileText className="w-3 h-3" /> Report
                            </Link>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}

            {/* Table Footer Count */}
            <div className="p-3 bg-slate-50 border-t border-slate-100 text-xs text-slate-500 flex justify-between items-center px-4">
              <span>
                Showing <strong>{filteredStudents.length}</strong> of <strong>{data.assessedCount}</strong> assessed students
                {selectedCategory && ` in "${selectedCategory}" tier`}
              </span>
              <span className="font-mono text-[11px] text-slate-400">
                Subject Max CIE: {data.cieMaxMarks} • Autonomous Scheme
              </span>
            </div>
          </div>
        </>
      ) : null}
    </div>
  );
}
