import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import {
  Users,
  Award,
  AlertTriangle,
  TrendingUp,
  ArrowRight,
  BookOpen,
  CheckCircle2,
  RefreshCw,
} from 'lucide-react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Legend,
} from 'recharts';
import { studentService } from '../api/studentService';
import { analyticsService } from '../api/analyticsService';
import { datasetService } from '../api/datasetService';
import StatCard from '../components/common/StatCard';
import Badge from '../components/common/Badge';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ErrorAlert from '../components/common/ErrorAlert';

export default function DashboardPage() {
  const [students, setStudents] = useState([]);
  const [subjectAnalytics, setSubjectAnalytics] = useState([]);
  const [activeDataset, setActiveDataset] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchData = async () => {
    try {
      setLoading(true);
      setError(null);
      const [studentsData, analyticsData, activeDs] = await Promise.all([
        studentService.getAllStudents(),
        analyticsService.getSubjectAnalytics(),
        datasetService.getActiveDataset().catch(() => null),
      ]);
      setStudents(studentsData || []);
      setSubjectAnalytics(analyticsData || []);
      setActiveDataset(activeDs || null);
    } catch (err) {
      setError(err.message || 'Failed to load dashboard metrics');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  if (loading) {
    return <LoadingSpinner message="Loading academic analytics from MySQL..." size="large" />;
  }

  // Calculate aggregates deterministically from backend data
  const totalStudents = students.length;
  const avgCgpa = totalStudents > 0
    ? (students.reduce((acc, s) => acc + (s.cgpa || 0), 0) / totalStudents).toFixed(2)
    : '0.00';

  const goodProgressCount = students.filter(
    (s) => s.prediction === 'Good Progress' || s.prediction === 'Consistent Performer'
  ).length;

  const monitorCount = students.filter(
    (s) => s.prediction === 'Monitor Progress'
  ).length;

  const supportCount = students.filter(
    (s) => s.prediction === 'May Need Additional Support'
  ).length;

  const attendanceWarningCount = students.filter((s) => s.attendanceWarning).length;
  const criticalAcademicWarningCount = students.filter((s) => s.criticalAcademicWarning).length;
  const avgFinalScore = totalStudents > 0
    ? (students.reduce((acc, s) => acc + (s.finalAcademicScore || 0), 0) / totalStudents).toFixed(2)
    : '0.00';
  const avgCie = totalStudents > 0
    ? (students.reduce((acc, s) => acc + (s.overallCiePercentage || 0), 0) / totalStudents).toFixed(1)
    : '0.0';

  // Pie chart data for student distribution
  const distributionData = [
    { name: 'Good Progress (≥75)', value: goodProgressCount, color: '#10b981' },
    { name: 'Monitor Progress (60-74.9)', value: monitorCount, color: '#f59e0b' },
    { name: 'Needs Additional Support (<60)', value: supportCount, color: '#ef4444' },
  ].filter((d) => d.value > 0);

  // Subject average bar chart data
  const subjectChartData = subjectAnalytics.map((s) => ({
    name: s.subjectCode,
    fullName: s.subjectName,
    average: s.averagePercentage,
    well: s.performingWellCount,
    struggling: s.requiringImprovementCount,
  }));

  // Filter subjects needing attention (students needing improvement > 0)
  const attentionSubjects = subjectAnalytics
    .filter((s) => s.requiringImprovementCount > 0)
    .sort((a, b) => b.requiringImprovementCount - a.requiringImprovementCount);

  // High priority students list sorted by lowest finalAcademicScore
  const priorityStudents = [...students]
    .filter((s) => s.prediction && s.prediction !== 'Good Progress')
    .sort((a, b) => (a.finalAcademicScore || 0) - (b.finalAcademicScore || 0))
    .slice(0, 6);

  return (
    <div className="space-y-6">
      {/* Top Banner & Refresh */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs">
        <div>
          <h2 className="text-xl font-bold text-slate-900 tracking-tight">Faculty Academic Overview</h2>
          <p className="text-xs text-slate-500 mt-0.5">
            Cohort analysis for {activeDataset?.semester || 'active'} curriculum • Real-time database metrics
          </p>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={fetchData}
            className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-xl transition-colors cursor-pointer"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            Refresh Data
          </button>
          <Link
            to="/students"
            className="inline-flex items-center gap-2 px-4 py-2 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-700 rounded-xl shadow-xs transition-colors"
          >
            <Users className="w-3.5 h-3.5" />
            View Student Roster
          </Link>
        </div>
      </div>

      <ErrorAlert message={error} retryAction={fetchData} />

      {/* Metric Cards Grid - 5 Distinct Academic Categories */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
        <StatCard
          title="Total Students"
          value={totalStudents}
          subtitle="Enrolled in active cohort"
          icon={Users}
          colorScheme="indigo"
        />
        <StatCard
          title="Cohort Avg CGPA"
          value={avgCgpa}
          subtitle="Historical academic baseline"
          icon={Award}
          colorScheme="blue"
        />
        <StatCard
          title="Good Progress"
          value={goodProgressCount}
          subtitle="Final Score ≥ 75.0 (On Track)"
          icon={CheckCircle2}
          colorScheme="emerald"
        />
        <StatCard
          title="Monitor Progress"
          value={monitorCount}
          subtitle="Final Score 60.0 – 74.99"
          icon={TrendingUp}
          colorScheme="amber"
        />
        <StatCard
          title="Needs Support"
          value={supportCount}
          subtitle="Final Score < 60.0"
          icon={AlertTriangle}
          colorScheme="rose"
        />
      </div>

      {/* Academic Performance Scoring Model Banner */}
      <div className="bg-slate-900 text-white p-4 rounded-2xl border border-slate-800 shadow-xs flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div className="space-y-1">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider bg-indigo-500/30 text-indigo-300 border border-indigo-400/30">
              Academic Scoring Model
            </span>
            <span className="text-xs text-slate-300 font-mono font-medium">
              Final Score = 70% CIE (Credit-Weighted) + 20% Previous SGPA (×10) + 10% Attendance
            </span>
          </div>
          <p className="text-xs text-slate-400">
            Cohort CIE Average: <strong className="text-slate-200">{avgCie}%</strong> • Cohort Final Academic Score Average: <strong className="text-indigo-300">{avgFinalScore} / 100</strong>
          </p>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          <span className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-xl text-xs font-semibold ${
            attendanceWarningCount > 0 ? 'bg-amber-950/80 text-amber-200 border border-amber-800/80' : 'bg-emerald-950/60 text-emerald-300 border border-emerald-800/60'
          }`}>
            <AlertTriangle className="w-3.5 h-3.5" />
            {attendanceWarningCount} Attendance Warnings (&lt;75%)
          </span>
          {criticalAcademicWarningCount > 0 && (
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-xl text-xs font-semibold bg-rose-950/80 text-rose-200 border border-rose-800/80">
              <AlertTriangle className="w-3.5 h-3.5" />
              {criticalAcademicWarningCount} Critical CIE Warnings (&lt;40%)
            </span>
          )}
        </div>
      </div>

      {/* Visual Analytics Charts Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Subject Average Performance Chart */}
        <div className="lg:col-span-2 bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="text-base font-bold text-slate-900">Subject Performance Overview</h3>
              <p className="text-xs text-slate-500">Average percentage across all {subjectAnalytics.length || 8} curriculum subjects</p>
            </div>
            <Link
              to="/analytics"
              className="text-xs font-semibold text-indigo-600 hover:text-indigo-800 flex items-center gap-1"
            >
              Details <ArrowRight className="w-3 h-3" />
            </Link>
          </div>

          <div className="h-72 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={subjectChartData} margin={{ top: 10, right: 10, left: -20, bottom: 20 }}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
                <XAxis dataKey="name" tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} />
                <YAxis domain={[0, 100]} tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} />
                <Tooltip
                  formatter={(val) => [`${val}%`, 'Class Average']}
                  contentStyle={{ backgroundColor: '#1e293b', color: '#fff', borderRadius: '8px', border: 'none', fontSize: '12px' }}
                />
                <Bar dataKey="average" fill="#6366f1" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Academic Performance Distribution */}
        <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs flex flex-col justify-between">
          <div>
            <h3 className="text-base font-bold text-slate-900">Performance Distribution</h3>
            <p className="text-xs text-slate-500 mb-2">Student classification based on assessment data</p>

            <div className="h-56 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={distributionData}
                    cx="50%"
                    cy="50%"
                    innerRadius={50}
                    outerRadius={80}
                    paddingAngle={4}
                    dataKey="value"
                  >
                    {distributionData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip
                    formatter={(val, name) => [`${val} students`, name]}
                    contentStyle={{ backgroundColor: '#1e293b', color: '#fff', borderRadius: '8px', border: 'none', fontSize: '12px' }}
                  />
                </PieChart>
              </ResponsiveContainer>
            </div>
          </div>

          <div className="space-y-2 pt-2 border-t border-slate-100">
            {distributionData.map((d) => (
              <div key={d.name} className="flex items-center justify-between text-xs">
                <span className="flex items-center gap-2 text-slate-600">
                  <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: d.color }}></span>
                  {d.name}
                </span>
                <span className="font-semibold text-slate-900">{d.value}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Two Column Section: Subject Attention & Students Requiring Monitoring */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Subject Attention Alerts */}
        <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
                <AlertTriangle className="w-4 h-4" />
              </div>
              <div>
                <h3 className="text-base font-bold text-slate-900">Subject Attention Highlights</h3>
                <p className="text-xs text-slate-500">Subjects with students below the 50% review threshold</p>
              </div>
            </div>
            <Link to="/analytics" className="text-xs font-semibold text-indigo-600 hover:text-indigo-800">
              View All
            </Link>
          </div>

          <div className="space-y-3">
            {attentionSubjects.length === 0 ? (
              <p className="text-sm text-slate-500 py-4 text-center">All subjects meet healthy cohort thresholds.</p>
            ) : (
              attentionSubjects.map((subj) => (
                <div
                  key={subj.subjectCode}
                  className="p-3 rounded-xl border border-slate-100 hover:border-slate-200 bg-slate-50/50 flex items-center justify-between"
                >
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-sm text-slate-900">{subj.subjectName}</span>
                      <span className="text-xs font-mono text-slate-500">({subj.subjectCode})</span>
                    </div>
                    <p className="text-xs text-slate-500 mt-0.5 line-clamp-1">{subj.overallSummary}</p>
                  </div>
                  <div className="text-right shrink-0 ml-4">
                    <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-rose-50 text-rose-700 border border-rose-200">
                      {subj.requiringImprovementCount} below 50%
                    </span>
                    <p className="text-[11px] text-slate-400 mt-0.5">Avg: {subj.averagePercentage}%</p>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Priority Academic Support Roster */}
        <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
                <Users className="w-4 h-4" />
              </div>
              <div>
                <h3 className="text-base font-bold text-slate-900">Monitored Students</h3>
                <p className="text-xs text-slate-500">Fast access to individual student diagnostic reports</p>
              </div>
            </div>
            <Link to="/students" className="text-xs font-semibold text-indigo-600 hover:text-indigo-800">
              Full List
            </Link>
          </div>

          <div className="divide-y divide-slate-100">
            {priorityStudents.length === 0 ? (
              <p className="text-sm text-slate-500 py-4 text-center">No students currently flagged for monitoring.</p>
            ) : (
              priorityStudents.map((st) => (
                <div key={st.rollNumber} className="py-3 flex items-center justify-between">
                  <div>
                    <div className="flex items-center gap-2">
                      <Link
                        to={`/students/${st.rollNumber}/report`}
                        className="font-semibold text-sm text-slate-900 hover:text-indigo-600 transition-colors"
                      >
                        {st.name}
                      </Link>
                      <span className="text-xs font-mono text-slate-400">{st.rollNumber}</span>
                      {st.attendanceWarning && (
                        <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-amber-50 text-amber-700 border border-amber-200" title="Attendance below 75%">
                          Attn &lt;75%
                        </span>
                      )}
                    </div>
                    <div className="flex items-center gap-2 text-xs text-slate-500 mt-1 flex-wrap">
                      <span>Final Score: <strong className="text-indigo-600 font-mono font-bold">{st.finalAcademicScore?.toFixed(2) || 'N/A'}</strong></span>
                      <span>•</span>
                      <span>CIE: <strong className="text-slate-700 font-mono">{st.overallCiePercentage?.toFixed(1) || 'N/A'}%</strong></span>
                      <span>•</span>
                      <span>Prev SGPA: <strong className="text-slate-700 font-mono">{st.lastSemesterSgpa?.toFixed(2) || 'N/A'}</strong></span>
                      <span>•</span>
                      <span>Att: <strong className={st.attendancePercent < 75 ? 'text-amber-600 font-mono font-semibold' : 'text-slate-700 font-mono'}>{st.attendancePercent != null ? `${st.attendancePercent.toFixed(1)}%` : 'N/A'}</strong></span>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge status={st.prediction} size="small" />
                    <Link
                      to={`/students/${st.rollNumber}/report`}
                      className="p-1.5 text-slate-400 hover:text-indigo-600 rounded-lg hover:bg-slate-100 transition-colors"
                      title="View Report"
                    >
                      <ArrowRight className="w-4 h-4" />
                    </Link>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
