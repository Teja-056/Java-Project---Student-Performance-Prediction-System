import React, { useState, useEffect } from 'react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import {
  BarChart3,
  AlertTriangle,
  Award,
  Users,
  CheckCircle2,
  RefreshCw,
} from 'lucide-react';
import { analyticsService } from '../api/analyticsService';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ErrorAlert from '../components/common/ErrorAlert';

export default function SubjectAnalyticsPage() {
  const [analytics, setAnalytics] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    loadAnalytics();
  }, []);

  const loadAnalytics = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await analyticsService.getSubjectAnalytics();
      setAnalytics(data || []);
    } catch (err) {
      setError(err.message || 'Failed to load subject analytics');
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return <LoadingSpinner message="Calculating subject performance analytics..." size="large" />;
  }

  // Chart data for comparing high performers vs needing improvement
  const comparisonChartData = analytics.map((s) => ({
    name: s.subjectCode,
    subjectName: s.subjectName,
    average: s.averagePercentage,
    highPerformers: s.performingWellCount,
    needingImprovement: s.requiringImprovementCount,
  }));

  // Identify subjects with highest remediation need
  const criticalSubjects = [...analytics].sort(
    (a, b) => b.requiringImprovementCount - a.requiringImprovementCount
  );

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-6 rounded-2xl border border-slate-200/80 shadow-xs">
        <div>
          <h2 className="text-xl font-bold text-slate-900 tracking-tight">Theory Subjects Class Analytics</h2>
          <p className="text-xs text-slate-500 mt-1">
            Cohort performance analysis across all 7 autonomous curriculum theory courses (Java, DS, CA, TTPS, CS, CT, HVPE)
          </p>
        </div>
        <button
          onClick={loadAnalytics}
          className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-xl transition-colors cursor-pointer shrink-0"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          Refresh Metrics
        </button>
      </div>

      <ErrorAlert message={error} retryAction={loadAnalytics} />

      {/* Visual Charts: Cohort High Performers vs Needing Improvement */}
      <div className="bg-white rounded-2xl border border-slate-200/80 p-6 shadow-xs">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-6">
          <div>
            <h3 className="text-base font-bold text-slate-900">Student Distribution per Subject</h3>
            <p className="text-xs text-slate-500">
              Students scoring $\ge 75\%$ (Distinction / High) vs students scoring $&lt; 50\%$ (Remedial Attention)
            </p>
          </div>
          <div className="flex items-center gap-4 text-xs font-medium">
            <span className="flex items-center gap-1.5 text-emerald-700">
              <span className="w-3 h-3 rounded-sm bg-emerald-500"></span> High Performers (≥75%)
            </span>
            <span className="flex items-center gap-1.5 text-rose-700">
              <span className="w-3 h-3 rounded-sm bg-rose-500"></span> Below Threshold (&lt;50%)
            </span>
          </div>
        </div>

        <div className="h-80 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={comparisonChartData} margin={{ top: 10, right: 10, left: -20, bottom: 20 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
              <XAxis dataKey="name" tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} />
              <YAxis tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} />
              <Tooltip
                formatter={(val, name) => [
                  `${val} students`,
                  name === 'highPerformers' ? 'High Performers (≥75%)' : 'Needing Support (<50%)',
                ]}
                contentStyle={{ backgroundColor: '#1e293b', color: '#fff', borderRadius: '8px', border: 'none', fontSize: '12px' }}
              />
              <Bar dataKey="highPerformers" fill="#10b981" radius={[4, 4, 0, 0]} />
              <Bar dataKey="needingImprovement" fill="#ef4444" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Grid of All 7 Subject Diagnostic Cards */}
      <div>
        <h3 className="text-base font-bold text-slate-900 mb-3">Individual Course Evaluations</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {analytics.map((subj) => {
            const hasHighStruggling = subj.requiringImprovementCount >= 15;

            return (
              <div
                key={subj.subjectCode}
                className={`bg-white rounded-2xl border p-5 shadow-xs flex flex-col justify-between transition-all ${
                  hasHighStruggling ? 'border-amber-200 hover:border-amber-300' : 'border-slate-200/80 hover:border-slate-300'
                }`}
              >
                <div>
                  <div className="flex items-center justify-between">
                    <div>
                      <h4 className="font-bold text-base text-slate-900">{subj.subjectName}</h4>
                      <span className="font-mono text-xs text-indigo-600 font-semibold">{subj.subjectCode}</span>
                    </div>
                    <span className="text-xl font-bold text-slate-900">{subj.averagePercentage}%</span>
                  </div>

                  <p className="text-xs text-slate-600 mt-3 leading-relaxed bg-slate-50 p-3 rounded-xl border border-slate-100">
                    {subj.overallSummary}
                  </p>
                </div>

                <div className="mt-4 pt-3 border-t border-slate-100 grid grid-cols-2 gap-2 text-xs">
                  <div className="bg-emerald-50/70 p-2 rounded-lg text-emerald-800">
                    <span className="text-[11px] block text-emerald-600 font-medium">Scoring ≥ 75%</span>
                    <strong className="text-base">{subj.performingWellCount}</strong>
                  </div>
                  <div className="bg-rose-50/70 p-2 rounded-lg text-rose-800">
                    <span className="text-[11px] block text-rose-600 font-medium">Scoring &lt; 50%</span>
                    <strong className="text-base">{subj.requiringImprovementCount}</strong>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
