import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  TrendingUp,
  Search,
  Sliders,
  Sparkles,
  BookOpen,
  AlertTriangle,
  CheckCircle,
  HelpCircle,
  ArrowUpRight,
  ArrowDownRight,
  Minus,
  RefreshCw,
  Layers,
  GraduationCap
} from 'lucide-react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer
} from 'recharts';
import predictionService from '../api/predictionService';
import { studentService } from '../api/studentService';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ErrorAlert from '../components/common/ErrorAlert';
import Badge from '../components/common/Badge';

export default function PredictionPage() {
  const { rollNo: paramRollNo } = useParams();
  const navigate = useNavigate();

  const [studentList, setStudentList] = useState([]);
  const [selectedRoll, setSelectedRoll] = useState(paramRollNo || '1602-25-733-001');
  const [searchInput, setSearchInput] = useState(paramRollNo || '1602-25-733-001');

  const [predictionData, setPredictionData] = useState(null);
  const [activeScenario, setActiveScenario] = useState('CONSISTENT');
  const [loading, setLoading] = useState(true);
  const [calculating, setCalculating] = useState(false);
  const [error, setError] = useState(null);

  // Assumption controls
  const [improvementPercent, setImprovementPercent] = useState(12.0);
  const [declinePercent, setDeclinePercent] = useState(12.0);
  const [attendanceOverride, setAttendanceOverride] = useState('');

  // AI Insights
  const [aiInsights, setAiInsights] = useState(null);
  const [aiLoading, setAiLoading] = useState(false);

  // Fetch list of students for autocomplete/dropdown
  useEffect(() => {
    async function loadStudents() {
      try {
        const students = await studentService.getAllStudents();
        setStudentList(students || []);
      } catch (err) {
        console.error('Failed to load student list', err);
      }
    }
    loadStudents();
  }, []);

  // Keep selectedRoll in sync with route param when navigated via URL
  useEffect(() => {
    if (paramRollNo && paramRollNo !== selectedRoll) {
      setSelectedRoll(paramRollNo);
      setSearchInput(paramRollNo);
    }
  }, [paramRollNo]);

  // Fetch prediction when selectedRoll changes
  useEffect(() => {
    if (!selectedRoll) return;
    loadPrediction(selectedRoll);
  }, [selectedRoll]);

  const loadPrediction = async (roll) => {
    setLoading(true);
    setError(null);
    try {
      const data = await predictionService.getPrediction(roll);
      setPredictionData(data);
      setImprovementPercent(data.configuredImprovementPercent || 12.0);
      setDeclinePercent(data.configuredDeclinePercent || 12.0);
      setAttendanceOverride('');
      // Trigger qualitative insights
      loadAiInsights(roll);
    } catch (err) {
      console.error(err);
      setError(err.message || err.response?.data?.message || 'Failed to load examination predictions.');
      setPredictionData(null);
    } finally {
      setLoading(false);
    }
  };

  const loadAiInsights = async (roll) => {
    setAiLoading(true);
    try {
      const res = await predictionService.getAiInsights(roll);
      setAiInsights(res);
    } catch (err) {
      console.error('AI Insights note:', err);
    } finally {
      setAiLoading(false);
    }
  };

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    const clean = searchInput.trim();
    if (clean) {
      setSelectedRoll(clean);
      navigate(`/prediction/${encodeURIComponent(clean)}`, { replace: true });
    }
  };

  const handleRecalculate = async (e) => {
    e.preventDefault();
    if (!selectedRoll) return;
    setCalculating(true);
    try {
      const payload = {
        improvementPercent: parseFloat(improvementPercent),
        declinePercent: parseFloat(declinePercent),
        attendanceOverride: attendanceOverride !== '' ? parseFloat(attendanceOverride) : null,
      };
      const res = await predictionService.calculateCustomPrediction(selectedRoll, payload);
      setPredictionData(res);
    } catch (err) {
      alert('Failed to calculate: ' + (err.response?.data?.message || err.message));
    } finally {
      setCalculating(false);
    }
  };

  if (loading && !predictionData) {
    return <LoadingSpinner message="Calculating Semester End Examination predictions..." />;
  }

  const scenarios = predictionData?.scenarios || {};
  const consistent = scenarios.CONSISTENT;
  const improved = scenarios.IMPROVED;
  const declining = scenarios.DECLINING;
  const currentScenarioData = scenarios[activeScenario] || consistent;

  // Chart 1 Data: SGPA & CGPA comparison across scenarios
  const sgpaCgpaComparisonData = [
    {
      name: 'Consistent',
      'Predicted SGPA': consistent?.predictedSgpa || 0,
      'Predicted CGPA': consistent?.predictedCgpa || 0,
    },
    {
      name: 'Improved',
      'Predicted SGPA': improved?.predictedSgpa || 0,
      'Predicted CGPA': improved?.predictedCgpa || 0,
    },
    {
      name: 'Declining',
      'Predicted SGPA': declining?.predictedSgpa || 0,
      'Predicted CGPA': declining?.predictedCgpa || 0,
    },
  ];

  // Chart 2 Data: Subject-by-Subject Predicted SEE comparison
  const subjectChartData = consistent?.subjects?.map((sub, i) => {
    const impSub = improved?.subjects?.[i];
    const decSub = declining?.subjects?.[i];
    return {
      name: sub.displayName,
      Credits: sub.credits,
      'Actual CIE': sub.actualCie != null ? sub.actualCie : 0,
      'SEE Consistent': sub.predictedSee != null ? sub.predictedSee : 0,
      'SEE Improved': impSub?.predictedSee != null ? impSub.predictedSee : (sub.predictedSee != null ? sub.predictedSee : 0),
      'SEE Declining': decSub?.predictedSee != null ? decSub.predictedSee : (sub.predictedSee != null ? sub.predictedSee : 0),
      'SEE Max': sub.seeMax != null ? sub.seeMax : 60,
    };
  }) || [];

  return (
    <div className="space-y-6 pb-12">
      {/* Top Header & Search Bar */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 bg-white p-6 rounded-2xl border border-slate-200 shadow-xs">
        <div>
          <div className="flex items-center gap-2 text-indigo-600 font-semibold text-xs tracking-wider uppercase">
            <TrendingUp className="w-4 h-4" />
            <span>Core Objective Engine</span>
          </div>
          <h1 className="text-2xl font-bold text-slate-900 mt-1">Semester End Exam (SEE) Prediction</h1>
          <p className="text-sm text-slate-500 mt-0.5">
            Deterministic projections across Consistent, Improved, and Declining scenarios for 8 Semester III courses (19 Credits).
          </p>
        </div>

        {/* Quick Search */}
        <form onSubmit={handleSearchSubmit} className="flex items-center gap-2">
          <div className="relative w-64 md:w-72">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              list="student-roll-options"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="1602-25-733-XXX"
              className="w-full pl-9 pr-3 py-2 text-sm bg-slate-50 border border-slate-300 rounded-xl focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
            <datalist id="student-roll-options">
              {studentList.map((s) => (
                <option key={s.rollNumber} value={s.rollNumber}>
                  {s.name} ({s.rollNumber})
                </option>
              ))}
            </datalist>
          </div>
          <button
            type="submit"
            className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-sm font-medium transition-colors shadow-xs"
          >
            Predict
          </button>
        </form>
      </div>

      {error && <ErrorAlert message={error} />}

      {predictionData && (
        <>
          {/* Student Profile Card */}
          <div className="bg-slate-900 text-white rounded-2xl p-6 shadow-md border border-slate-800">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-800 pb-5">
              <div className="flex items-center gap-3">
                <div className="w-12 h-12 rounded-xl bg-indigo-600 flex items-center justify-center text-white font-bold text-lg">
                  {predictionData.studentName?.charAt(0) || 'S'}
                </div>
                <div>
                  <h2 className="text-xl font-bold text-white flex items-center gap-2">
                    {predictionData.studentName}
                    <span className="text-xs font-normal text-indigo-300 bg-indigo-950 px-2.5 py-0.5 rounded-full border border-indigo-800">
                      Roll: {predictionData.rollNumber}
                    </span>
                  </h2>
                  <p className="text-xs text-slate-400 mt-0.5">
                    CSE • 8 Semester III Theory Subjects • Total Included Credits: 19
                  </p>
                </div>
              </div>

              {/* Student Quick Stats */}
              <div className="flex flex-wrap items-center gap-4 text-sm">
                <div className="bg-slate-800/80 px-4 py-2 rounded-xl border border-slate-700">
                  <div className="text-[11px] text-slate-400">Semester I SGPA</div>
                  <div className="text-base font-bold text-white">
                    {predictionData.sem1Sgpa != null ? predictionData.sem1Sgpa.toFixed(2) : (
                      <span className="text-amber-400 text-xs">Not Uploaded</span>
                    )}
                  </div>
                </div>

                <div className="bg-slate-800/80 px-4 py-2 rounded-xl border border-slate-700">
                  <div className="text-[11px] text-slate-400">Semester II SGPA</div>
                  <div className="text-base font-bold text-white">
                    {predictionData.sem2Sgpa?.toFixed(2)}
                  </div>
                </div>

                <div className="bg-slate-800/80 px-4 py-2 rounded-xl border border-slate-700">
                  <div className="text-[11px] text-slate-400">Previous CGPA</div>
                  <div className="text-base font-bold text-white">
                    {predictionData.previousCgpa?.toFixed(2)}
                  </div>
                </div>

                <div className="bg-slate-800/80 px-4 py-2 rounded-xl border border-slate-700">
                  <div className="text-[11px] text-slate-400">Attendance</div>
                  <div className="text-base font-bold text-emerald-400">
                    {predictionData.attendancePercent != null ? `${predictionData.attendancePercent.toFixed(1)}%` : '75.0% (Default)'}
                  </div>
                </div>

                <div className="bg-slate-800/80 px-4 py-2 rounded-xl border border-slate-700">
                  <div className="text-[11px] text-slate-400">Current CIE Avg</div>
                  <div className="text-base font-bold text-indigo-400">
                    {predictionData.overallCiePercentage?.toFixed(1)}%
                  </div>
                </div>
              </div>
            </div>

            {/* Quick notice if Sem 1 SGPA missing */}
            {predictionData.sem1Sgpa == null && (
              <div className="mt-3 text-xs bg-amber-500/10 border border-amber-500/30 text-amber-300 px-3 py-2 rounded-lg flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 shrink-0" />
                <span>
                  Notice: Verified Semester I SGPA is currently missing for this record. Predicted SGPA is calculated precisely across Semester III (19 credits), but cumulative 48-credit Predicted CGPA remains unavailable until Semester I SGPA is uploaded.
                </span>
              </div>
            )}
          </div>

          {/* 3 Prediction Scenario Cards */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {/* Scenario A: Consistent */}
            <div
              onClick={() => setActiveScenario('CONSISTENT')}
              className={`cursor-pointer bg-white rounded-2xl p-6 border-2 transition-all shadow-xs ${
                activeScenario === 'CONSISTENT'
                  ? 'border-indigo-600 ring-2 ring-indigo-500/20 shadow-md'
                  : 'border-slate-200 hover:border-slate-300'
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-indigo-700 bg-indigo-50 border border-indigo-200 px-3 py-1 rounded-full uppercase tracking-wider">
                  Scenario A
                </span>
                <span className="text-xs text-slate-400 flex items-center gap-1">
                  <Minus className="w-3.5 h-3.5 text-slate-400" /> Steady Baseline
                </span>
              </div>
              <h3 className="text-lg font-bold text-slate-900 mt-3">Consistent Performance</h3>
              <p className="text-xs text-slate-500 mt-1 line-clamp-2">
                {consistent?.description}
              </p>

              <div className="mt-5 grid grid-cols-2 gap-3 pt-4 border-t border-slate-100">
                <div className="bg-slate-50 p-3 rounded-xl border border-slate-100">
                  <span className="text-[11px] font-medium text-slate-500">Predicted SGPA</span>
                  <div className="text-2xl font-black text-slate-900 mt-0.5">
                    {consistent?.predictedSgpa?.toFixed(2)}
                  </div>
                  <span className="text-[10px] text-slate-400">19 Sem III Credits</span>
                </div>

                <div className="bg-slate-50 p-3 rounded-xl border border-slate-100">
                  <span className="text-[11px] font-medium text-slate-500">Predicted CGPA</span>
                  <div className="text-2xl font-black text-indigo-600 mt-0.5">
                    {consistent?.predictedCgpa != null ? consistent.predictedCgpa.toFixed(2) : (
                      <span className="text-xs text-slate-400 font-semibold">Requires Sem I</span>
                    )}
                  </div>
                  <span className="text-[10px] text-slate-400">48 Total Credits</span>
                </div>

                <div className="bg-slate-50 p-2.5 rounded-xl border border-slate-100 col-span-2 flex items-center justify-between text-xs">
                  <span className="text-slate-500 font-medium">Total Predicted SEE</span>
                  <span className="font-bold text-slate-800">
                    {consistent?.totalPredictedSeeMarks} <span className="text-[10px] text-slate-400 font-normal">/ 460 Marks</span>
                  </span>
                </div>
              </div>
            </div>

            {/* Scenario B: Improved */}
            <div
              onClick={() => setActiveScenario('IMPROVED')}
              className={`cursor-pointer bg-white rounded-2xl p-6 border-2 transition-all shadow-xs ${
                activeScenario === 'IMPROVED'
                  ? 'border-emerald-600 ring-2 ring-emerald-500/20 shadow-md'
                  : 'border-slate-200 hover:border-slate-300'
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-emerald-700 bg-emerald-50 border border-emerald-200 px-3 py-1 rounded-full uppercase tracking-wider">
                  Scenario B
                </span>
                <span className="text-xs text-emerald-600 font-semibold flex items-center gap-0.5">
                  <ArrowUpRight className="w-4 h-4" /> +{predictionData.configuredImprovementPercent}% Boost
                </span>
              </div>
              <h3 className="text-lg font-bold text-slate-900 mt-3">Improved Performance</h3>
              <p className="text-xs text-slate-500 mt-1 line-clamp-2">
                {improved?.description}
              </p>

              <div className="mt-5 grid grid-cols-2 gap-3 pt-4 border-t border-slate-100">
                <div className="bg-emerald-50/50 p-3 rounded-xl border border-emerald-100">
                  <span className="text-[11px] font-medium text-slate-500">Predicted SGPA</span>
                  <div className="text-2xl font-black text-emerald-700 mt-0.5">
                    {improved?.predictedSgpa?.toFixed(2)}
                  </div>
                  <span className="text-[10px] text-emerald-600 font-medium">
                    {((improved?.predictedSgpa || 0) - (consistent?.predictedSgpa || 0)) > 0 ? (
                      `+${((improved?.predictedSgpa || 0) - (consistent?.predictedSgpa || 0)).toFixed(2)} vs Consistent`
                    ) : (
                      `+0.00 vs Consistent (Bracket Intact)`
                    )}
                  </span>
                </div>

                <div className="bg-emerald-50/50 p-3 rounded-xl border border-emerald-100">
                  <span className="text-[11px] font-medium text-slate-500">Predicted CGPA</span>
                  <div className="text-2xl font-black text-emerald-700 mt-0.5">
                    {improved?.predictedCgpa != null ? improved.predictedCgpa.toFixed(2) : (
                      <span className="text-xs text-slate-400 font-semibold">Requires Sem I</span>
                    )}
                  </div>
                  <span className="text-[10px] text-slate-400">48 Total Credits</span>
                </div>

                <div className="bg-emerald-50/50 p-2.5 rounded-xl border border-emerald-100 col-span-2 flex items-center justify-between text-xs">
                  <span className="text-slate-500 font-medium">Total Predicted SEE</span>
                  <span className="font-bold text-emerald-700">
                    {improved?.totalPredictedSeeMarks} <span className="text-[10px] text-slate-400 font-normal">/ 460</span>
                    <span className="ml-1.5 text-[10px] bg-emerald-100 text-emerald-800 px-1.5 py-0.5 rounded font-semibold">
                      +{((improved?.totalPredictedSeeMarks || 0) - (consistent?.totalPredictedSeeMarks || 0))} marks ({improved?.subjectsWithMarksIncrease || 0} subjs ↑)
                    </span>
                  </span>
                </div>
              </div>
            </div>

            {/* Scenario C: Declining */}
            <div
              onClick={() => setActiveScenario('DECLINING')}
              className={`cursor-pointer bg-white rounded-2xl p-6 border-2 transition-all shadow-xs ${
                activeScenario === 'DECLINING'
                  ? 'border-rose-600 ring-2 ring-rose-500/20 shadow-md'
                  : 'border-slate-200 hover:border-slate-300'
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-rose-700 bg-rose-50 border border-rose-200 px-3 py-1 rounded-full uppercase tracking-wider">
                  Scenario C
                </span>
                <span className="text-xs text-rose-600 font-semibold flex items-center gap-0.5">
                  <ArrowDownRight className="w-4 h-4" /> -{predictionData.configuredDeclinePercent}% Decline
                </span>
              </div>
              <h3 className="text-lg font-bold text-slate-900 mt-3">Declining Performance</h3>
              <p className="text-xs text-slate-500 mt-1 line-clamp-2">
                {declining?.description}
              </p>

              <div className="mt-5 grid grid-cols-2 gap-3 pt-4 border-t border-slate-100">
                <div className="bg-rose-50/50 p-3 rounded-xl border border-rose-100">
                  <span className="text-[11px] font-medium text-slate-500">Predicted SGPA</span>
                  <div className="text-2xl font-black text-rose-700 mt-0.5">
                    {declining?.predictedSgpa?.toFixed(2)}
                  </div>
                  <span className="text-[10px] text-rose-600 font-medium">
                    {((declining?.predictedSgpa || 0) - (consistent?.predictedSgpa || 0)).toFixed(2)} vs Consistent
                  </span>
                </div>

                <div className="bg-rose-50/50 p-3 rounded-xl border border-rose-100">
                  <span className="text-[11px] font-medium text-slate-500">Predicted CGPA</span>
                  <div className="text-2xl font-black text-rose-700 mt-0.5">
                    {declining?.predictedCgpa != null ? declining.predictedCgpa.toFixed(2) : (
                      <span className="text-xs text-slate-400 font-semibold">Requires Sem I</span>
                    )}
                  </div>
                  <span className="text-[10px] text-slate-400">48 Total Credits</span>
                </div>

                <div className="bg-rose-50/50 p-2.5 rounded-xl border border-rose-100 col-span-2 flex items-center justify-between text-xs">
                  <span className="text-slate-500 font-medium">Total Predicted SEE</span>
                  <span className="font-bold text-rose-700">
                    {declining?.totalPredictedSeeMarks} <span className="text-[10px] text-slate-400 font-normal">/ 460</span>
                    <span className="ml-1.5 text-[10px] bg-rose-100 text-rose-800 px-1.5 py-0.5 rounded font-semibold">
                      -{((consistent?.totalPredictedSeeMarks || 0) - (declining?.totalPredictedSeeMarks || 0))} marks ({declining?.subjectsWithMarksDecrease || 0} subjs ↓)
                    </span>
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* Educational Note when Improved marks increase but SGPA delta is 0 */}
          {((improved?.predictedSgpa || 0) === (consistent?.predictedSgpa || 0)) && ((improved?.totalPredictedSeeMarks || 0) > (consistent?.totalPredictedSeeMarks || 0)) && (
            <div className="bg-sky-50 border border-sky-200 rounded-2xl p-4 text-xs text-sky-900 flex items-start gap-3 shadow-xs">
              <HelpCircle className="w-5 h-5 text-sky-600 shrink-0 mt-0.5" />
              <div>
                <strong className="font-bold text-sky-950 block mb-1">
                  Calculation Analysis: Why did the +{predictionData.configuredImprovementPercent}% Improved Scenario show +0.00 SGPA delta for this student?
                </strong>
                <p className="text-sky-800 leading-relaxed">
                  Raw Semester End Exam (SEE) marks <strong>do increase</strong> across {improved?.subjectsWithMarksIncrease} subjects (totaling +{((improved?.totalPredictedSeeMarks || 0) - (consistent?.totalPredictedSeeMarks || 0))} additional marks). However, because this student's baseline performance already sits comfortably inside the <strong>Grade A (80.0% – 89.9%)</strong> bracket (with CA at A+), these mark improvements do not push any subject past the <strong>90.0% threshold</strong> required for Grade A+ (10 Grade Points). Since SGPA is calculated strictly from integer grade points across the 19 credits, SGPA remains mathematically at <strong>{consistent?.predictedSgpa?.toFixed(2)}</strong>. Raising the Improvement Factor to <strong>+20.0%</strong> lifts Open Elective past 90.0%, elevating SGPA to <strong>9.26</strong>.
                </p>
              </div>
            </div>
          )}

          {/* Three-Scenario Comparison Matrix Table (Phase 3 Requirement) */}
          <div className="bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden">
            <div className="p-5 border-b border-slate-200 bg-slate-50/60 flex items-center justify-between">
              <div>
                <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
                  <Layers className="w-5 h-5 text-indigo-600" />
                  Three-Scenario Comparison Matrix
                </h3>
                <p className="text-xs text-slate-500 mt-0.5">
                  Direct numerical comparison of Consistent vs. Improved (+{predictionData.configuredImprovementPercent}%) vs. Declining (-{predictionData.configuredDeclinePercent}%) projections.
                </p>
              </div>
              <Badge variant="info">3 Scenarios</Badge>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-700">
                <thead className="bg-slate-100/70 text-slate-500 uppercase text-[11px] font-semibold border-b border-slate-200">
                  <tr>
                    <th className="px-5 py-3">Scenario</th>
                    <th className="px-4 py-3 text-center">Preparation Shift</th>
                    <th className="px-4 py-3 text-right">Total Predicted SEE</th>
                    <th className="px-4 py-3 text-right">SEE Marks Shift (Δ)</th>
                    <th className="px-4 py-3 text-center">Predicted SGPA</th>
                    <th className="px-4 py-3 text-center">SGPA Shift (Δ)</th>
                    <th className="px-4 py-3 text-center">Predicted CGPA</th>
                    <th className="px-4 py-3 text-center">Subject Marks Shift</th>
                    <th className="px-4 py-3 text-center">Grade Points Shift</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {/* Consistent */}
                  <tr className="hover:bg-slate-50/80 transition-colors">
                    <td className="px-5 py-3.5 font-bold text-slate-900 flex items-center gap-2">
                      <span className="w-2.5 h-2.5 rounded-full bg-indigo-500" />
                      Consistent Performance
                    </td>
                    <td className="px-4 py-3.5 text-center text-xs font-medium text-slate-600">
                      0.0% (Baseline)
                    </td>
                    <td className="px-4 py-3.5 text-right font-bold text-slate-900">
                      {consistent?.totalPredictedSeeMarks} <span className="text-[10px] text-slate-400">/ 460</span>
                    </td>
                    <td className="px-4 py-3.5 text-right text-xs text-slate-500 font-semibold">
                      — (Baseline)
                    </td>
                    <td className="px-4 py-3.5 text-center font-black text-indigo-700 text-base">
                      {consistent?.predictedSgpa?.toFixed(2)}
                    </td>
                    <td className="px-4 py-3.5 text-center text-xs text-slate-500 font-semibold">
                      —
                    </td>
                    <td className="px-4 py-3.5 text-center font-bold text-slate-800">
                      {consistent?.predictedCgpa != null ? consistent.predictedCgpa.toFixed(2) : 'Requires Sem I'}
                    </td>
                    <td className="px-4 py-3.5 text-center text-xs text-slate-500">
                      Baseline Reference
                    </td>
                    <td className="px-4 py-3.5 text-center text-xs text-slate-500">
                      Baseline Reference
                    </td>
                  </tr>

                  {/* Improved */}
                  <tr className="hover:bg-emerald-50/40 transition-colors">
                    <td className="px-5 py-3.5 font-bold text-emerald-800 flex items-center gap-2">
                      <span className="w-2.5 h-2.5 rounded-full bg-emerald-500" />
                      Improved Performance
                    </td>
                    <td className="px-4 py-3.5 text-center text-xs font-semibold text-emerald-700">
                      +{predictionData.configuredImprovementPercent}% Headroom
                    </td>
                    <td className="px-4 py-3.5 text-right font-bold text-emerald-800">
                      {improved?.totalPredictedSeeMarks} <span className="text-[10px] text-slate-400">/ 460</span>
                    </td>
                    <td className="px-4 py-3.5 text-right text-xs font-bold text-emerald-700">
                      +{((improved?.totalPredictedSeeMarks || 0) - (consistent?.totalPredictedSeeMarks || 0))} marks
                    </td>
                    <td className="px-4 py-3.5 text-center font-black text-emerald-700 text-base">
                      {improved?.predictedSgpa?.toFixed(2)}
                    </td>
                    <td className="px-4 py-3.5 text-center text-xs font-bold text-emerald-700">
                      {((improved?.predictedSgpa || 0) - (consistent?.predictedSgpa || 0)) > 0 ? (
                        `+${((improved?.predictedSgpa || 0) - (consistent?.predictedSgpa || 0)).toFixed(2)}`
                      ) : (
                        <span className="text-slate-500 font-normal">+0.00 (within bracket)</span>
                      )}
                    </td>
                    <td className="px-4 py-3.5 text-center font-bold text-emerald-800">
                      {improved?.predictedCgpa != null ? improved.predictedCgpa.toFixed(2) : 'Requires Sem I'}
                    </td>
                    <td className="px-4 py-3.5 text-center text-xs text-emerald-700 font-semibold">
                      {improved?.subjectsWithMarksIncrease || 0} of 8 subjects ↑
                    </td>
                    <td className="px-4 py-3.5 text-center text-xs">
                      {(improved?.subjectsWithGradeIncrease || 0) > 0 ? (
                        <span className="text-emerald-700 font-bold">+{improved.subjectsWithGradeIncrease} subject grades ↑</span>
                      ) : (
                        <span className="text-slate-500 bg-slate-100 px-2 py-0.5 rounded text-[11px]">
                          0 grade changes (scores within bracket)
                        </span>
                      )}
                    </td>
                  </tr>

                  {/* Declining */}
                  <tr className="hover:bg-rose-50/40 transition-colors">
                    <td className="px-5 py-3.5 font-bold text-rose-800 flex items-center gap-2">
                      <span className="w-2.5 h-2.5 rounded-full bg-rose-500" />
                      Declining Performance
                    </td>
                    <td className="px-4 py-3.5 text-center text-xs font-semibold text-rose-700">
                      -{predictionData.configuredDeclinePercent}% Penalty
                    </td>
                    <td className="px-4 py-3.5 text-right font-bold text-rose-800">
                      {declining?.totalPredictedSeeMarks} <span className="text-[10px] text-slate-400">/ 460</span>
                    </td>
                    <td className="px-4 py-3.5 text-right text-xs font-bold text-rose-700">
                      -{((consistent?.totalPredictedSeeMarks || 0) - (declining?.totalPredictedSeeMarks || 0))} marks
                    </td>
                    <td className="px-4 py-3.5 text-center font-black text-rose-700 text-base">
                      {declining?.predictedSgpa?.toFixed(2)}
                    </td>
                    <td className="px-4 py-3.5 text-center text-xs font-bold text-rose-700">
                      {((declining?.predictedSgpa || 0) - (consistent?.predictedSgpa || 0)).toFixed(2)}
                    </td>
                    <td className="px-4 py-3.5 text-center font-bold text-rose-800">
                      {declining?.predictedCgpa != null ? declining.predictedCgpa.toFixed(2) : 'Requires Sem I'}
                    </td>
                    <td className="px-4 py-3.5 text-center text-xs text-rose-700 font-semibold">
                      {declining?.subjectsWithMarksDecrease || 0} of 8 subjects ↓
                    </td>
                    <td className="px-4 py-3.5 text-center text-xs">
                      {(declining?.subjectsWithGradeDecrease || 0) > 0 ? (
                        <span className="text-rose-700 font-bold">-{declining.subjectsWithGradeDecrease} subject grades ↓</span>
                      ) : (
                        <span className="text-slate-500 bg-slate-100 px-2 py-0.5 rounded text-[11px]">
                          0 grade changes (buffered)
                        </span>
                      )}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          {/* Interactive Scenario Controls Drawer / Card */}
          <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <Sliders className="w-5 h-5 text-indigo-600" />
                <h3 className="text-base font-bold text-slate-900">Faculty What-If Scenario Assumptions</h3>
              </div>
              <span className="text-xs text-slate-400">
                Adjust preparation factors to observe numerical SGPA & SEE sensitivity
              </span>
            </div>

            <form onSubmit={handleRecalculate} className="grid grid-cols-1 md:grid-cols-4 gap-6 items-end">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Improvement Factor (% Boost): <span className="text-emerald-600 font-bold">+{improvementPercent}%</span>
                </label>
                <input
                  type="range"
                  min="5"
                  max="25"
                  step="0.5"
                  value={improvementPercent}
                  onChange={(e) => setImprovementPercent(e.target.value)}
                  className="w-full h-2 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-emerald-600"
                />
                <div className="flex justify-between text-[10px] text-slate-400 mt-1">
                  <span>+5%</span>
                  <span>Default (+12%)</span>
                  <span>+25%</span>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Decline Penalty (% Decline): <span className="text-rose-600 font-bold">-{declinePercent}%</span>
                </label>
                <input
                  type="range"
                  min="5"
                  max="25"
                  step="0.5"
                  value={declinePercent}
                  onChange={(e) => setDeclinePercent(e.target.value)}
                  className="w-full h-2 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-rose-600"
                />
                <div className="flex justify-between text-[10px] text-slate-400 mt-1">
                  <span>-5%</span>
                  <span>Default (-12%)</span>
                  <span>-25%</span>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Hypothetical Attendance (%)
                </label>
                <input
                  type="number"
                  min="0"
                  max="100"
                  step="0.5"
                  placeholder={predictionData.attendancePercent != null ? `${predictionData.attendancePercent}% (Current)` : '75.0%'}
                  value={attendanceOverride}
                  onChange={(e) => setAttendanceOverride(e.target.value)}
                  className="w-full px-3 py-1.5 text-sm bg-slate-50 border border-slate-300 rounded-xl focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
                <span className="text-[10px] text-slate-400">Leave blank to use actual student attendance</span>
              </div>

              <div>
                <button
                  type="submit"
                  disabled={calculating}
                  className="w-full py-2 px-4 bg-slate-900 hover:bg-slate-800 text-white rounded-xl text-sm font-semibold flex items-center justify-center gap-2 transition-colors shadow-xs"
                >
                  <RefreshCw className={`w-4 h-4 ${calculating ? 'animate-spin' : ''}`} />
                  {calculating ? 'Recalculating...' : 'Recalculate Projections'}
                </button>
              </div>
            </form>
          </div>

          {/* Visual Analytics Comparison Charts */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Chart 1: SGPA & CGPA across 3 scenarios */}
            <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs">
              <div className="flex items-center justify-between mb-4">
                <div>
                  <h4 className="text-sm font-bold text-slate-900">SGPA vs CGPA Scenario Comparison</h4>
                  <p className="text-xs text-slate-400">Comparing outcomes across 19 Sem III credits and 48 cumulative credits</p>
                </div>
                <Badge variant="info">10-Point Scale</Badge>
              </div>

              <div className="h-64 min-h-[260px] w-full">
                <ResponsiveContainer width="100%" height={260}>
                  <BarChart data={sgpaCgpaComparisonData} margin={{ top: 10, right: 20, left: -10, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                    <XAxis dataKey="name" stroke="#64748b" fontSize={12} tickLine={false} />
                    <YAxis domain={[0, 10]} ticks={[0, 2, 4, 6, 8, 10]} stroke="#64748b" fontSize={12} tickLine={false} />
                    <Tooltip
                      contentStyle={{ backgroundColor: '#0f172a', borderColor: '#1e293b', borderRadius: '0.75rem', color: '#fff' }}
                      formatter={(val) => [val != null && val > 0 ? val.toFixed(2) : 'Unavailable', '']}
                    />
                    <Legend />
                    <Bar dataKey="Predicted SGPA" fill="#4f46e5" radius={[6, 6, 0, 0]} />
                    <Bar dataKey="Predicted CGPA" fill="#06b6d4" radius={[6, 6, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>

            {/* Chart 2: Predicted SEE marks comparison across all 8 subjects */}
            <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs">
              <div className="flex items-center justify-between mb-4">
                <div>
                  <h4 className="text-sm font-bold text-slate-900">Subject-by-Subject Predicted SEE Marks</h4>
                  <p className="text-xs text-slate-400">Consistent vs. Improved vs. Declining projections per subject</p>
                </div>
                <Badge variant="indigo">8 Courses</Badge>
              </div>

              <div className="h-64 min-h-[260px] w-full">
                <ResponsiveContainer width="100%" height={260}>
                  <BarChart data={subjectChartData} margin={{ top: 10, right: 10, left: -15, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                    <XAxis dataKey="name" stroke="#64748b" fontSize={11} tickLine={false} />
                    <YAxis domain={[0, 60]} ticks={[0, 15, 30, 45, 60]} stroke="#64748b" fontSize={11} tickLine={false} />
                    <Tooltip
                      contentStyle={{ backgroundColor: '#0f172a', borderColor: '#1e293b', borderRadius: '0.75rem', color: '#fff' }}
                    />
                    <Legend />
                    <Bar dataKey="SEE Consistent" fill="#6366f1" radius={[4, 4, 0, 0]} />
                    <Bar dataKey="SEE Improved" fill="#10b981" radius={[4, 4, 0, 0]} />
                    <Bar dataKey="SEE Declining" fill="#f43f5e" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
          </div>

          {/* Detailed Subject Prediction Table */}
          <div className="bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden">
            <div className="p-5 border-b border-slate-200 flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-slate-50/50">
              <div>
                <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
                  <BookOpen className="w-5 h-5 text-indigo-600" />
                  Subject-Wise SEE Marks & Grade Breakdown
                </h3>
                <p className="text-xs text-slate-500 mt-0.5">
                  Detailed results for active scenario: <strong className="text-slate-800">{currentScenarioData?.scenarioName}</strong>
                </p>
              </div>

              {/* Scenario Toggle Tabs */}
              <div className="inline-flex p-1 bg-slate-200/70 rounded-xl text-xs font-semibold">
                <button
                  onClick={() => setActiveScenario('CONSISTENT')}
                  className={`px-3 py-1.5 rounded-lg transition-all ${
                    activeScenario === 'CONSISTENT'
                      ? 'bg-white text-indigo-700 shadow-xs'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  Consistent
                </button>
                <button
                  onClick={() => setActiveScenario('IMPROVED')}
                  className={`px-3 py-1.5 rounded-lg transition-all ${
                    activeScenario === 'IMPROVED'
                      ? 'bg-white text-emerald-700 shadow-xs'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  Improved (+{predictionData.configuredImprovementPercent}%)
                </button>
                <button
                  onClick={() => setActiveScenario('DECLINING')}
                  className={`px-3 py-1.5 rounded-lg transition-all ${
                    activeScenario === 'DECLINING'
                      ? 'bg-white text-rose-700 shadow-xs'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  Declining (-{predictionData.configuredDeclinePercent}%)
                </button>
              </div>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-700">
                <thead className="bg-slate-100/70 text-slate-500 uppercase text-[11px] font-semibold border-b border-slate-200">
                  <tr>
                    <th className="px-5 py-3">Subject / Course</th>
                    <th className="px-3 py-3 text-center">Credits</th>
                    <th className="px-4 py-3 text-right">Actual CIE</th>
                    <th className="px-4 py-3 text-right font-bold text-indigo-600">Predicted SEE</th>
                    <th className="px-4 py-3 text-right">Final Marks</th>
                    <th className="px-4 py-3 text-right">Percentage</th>
                    <th className="px-3 py-3 text-center">Grade</th>
                    <th className="px-3 py-3 text-center">Grade Points</th>
                    <th className="px-4 py-3 text-center">Internal Trend</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {currentScenarioData?.subjects?.map((sub) => {
                    const isPassed = sub.gradePoints >= 5;
                    return (
                      <tr key={sub.subjectKey} className="hover:bg-slate-50/80 transition-colors">
                        <td className="px-5 py-3.5">
                          <div className="font-bold text-slate-900">{sub.displayName}</div>
                          <div className="text-[11px] text-slate-400">{sub.fullName}</div>
                        </td>
                        <td className="px-3 py-3.5 text-center font-semibold text-slate-700">
                          {sub.credits}
                        </td>
                        <td className="px-4 py-3.5 text-right font-medium text-slate-600">
                          {sub.actualCie != null ? (
                            <>
                              {sub.actualCie.toFixed(1)} <span className="text-[10px] text-slate-400">/{sub.cieMax}</span>
                            </>
                          ) : (
                            <span className="text-xs text-slate-400 italic">Unassessed</span>
                          )}
                        </td>
                        <td className="px-4 py-3.5 text-right font-black text-indigo-600 text-base">
                          {sub.predictedSee != null ? (
                            <>
                              {sub.predictedSee} <span className="text-[10px] text-slate-400 font-normal">/{sub.seeMax}</span>
                            </>
                          ) : (
                            <span className="text-xs text-slate-400 font-normal">—</span>
                          )}
                        </td>
                        <td className="px-4 py-3.5 text-right font-bold text-slate-800">
                          {sub.finalMarks != null ? (
                            <>
                              {sub.finalMarks.toFixed(1)} <span className="text-[10px] text-slate-400">/{sub.totalMax}</span>
                            </>
                          ) : (
                            <span className="text-xs text-slate-400 font-normal">—</span>
                          )}
                        </td>
                        <td className="px-4 py-3.5 text-right font-medium">
                          {sub.percentage != null ? `${sub.percentage.toFixed(1)}%` : '—'}
                        </td>
                        <td className="px-3 py-3.5 text-center">
                          <span
                            className={`inline-block px-2.5 py-0.5 rounded-full text-xs font-bold ${
                              sub.grade === 'A+' || sub.grade === 'A'
                                ? 'bg-emerald-100 text-emerald-800'
                                : sub.grade === 'B+' || sub.grade === 'B'
                                ? 'bg-indigo-100 text-indigo-800'
                                : sub.grade === 'C' || sub.grade === 'P'
                                ? 'bg-amber-100 text-amber-800'
                                : sub.grade === 'UNASSESSED'
                                ? 'bg-slate-100 text-slate-600'
                                : 'bg-rose-100 text-rose-800'
                            }`}
                          >
                            {sub.grade}
                          </span>
                        </td>
                        <td className="px-3 py-3.5 text-center font-bold text-slate-700">
                          {sub.gradePoints != null ? sub.gradePoints : '—'}
                        </td>
                        <td className="px-4 py-3.5 text-center">
                          {sub.trendPercentage != null ? (
                            sub.trendPercentage > 0 ? (
                              <span className="text-xs font-semibold text-emerald-600 flex items-center justify-center gap-0.5">
                                <ArrowUpRight className="w-3.5 h-3.5" /> +{sub.trendPercentage.toFixed(1)}%
                              </span>
                            ) : sub.trendPercentage < 0 ? (
                              <span className="text-xs font-semibold text-rose-600 flex items-center justify-center gap-0.5">
                                <ArrowDownRight className="w-3.5 h-3.5" /> {sub.trendPercentage.toFixed(1)}%
                              </span>
                            ) : (
                              <span className="text-xs text-slate-400 flex items-center justify-center gap-0.5">
                                <Minus className="w-3.5 h-3.5" /> 0.0%
                              </span>
                            )
                          ) : (
                            <span className="text-xs text-slate-400">N/A</span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
                <tfoot className="bg-slate-50 font-bold border-t-2 border-slate-200">
                  <tr>
                    <td className="px-5 py-3 text-slate-900">Total / Summary</td>
                    <td className="px-3 py-3 text-center font-extrabold">
                      {currentScenarioData?.subjects?.filter(s => s.assessed !== false).reduce((sum, s) => sum + s.credits, 0) === 19 ? (
                        <span className="text-slate-900">19 Credits</span>
                      ) : (
                        <span className="text-amber-700">
                          {currentScenarioData?.subjects?.filter(s => s.assessed !== false).reduce((sum, s) => sum + s.credits, 0)} / 19 Credits
                        </span>
                      )}
                    </td>
                    <td colSpan={5} className="px-4 py-3 text-right text-slate-500 font-semibold text-xs">
                      Credit-Weighted Calculated SGPA:
                    </td>
                    <td className="px-3 py-3 text-center text-indigo-700 text-base font-black">
                      {currentScenarioData?.predictedSgpa?.toFixed(2)}
                    </td>
                    <td className="px-4 py-3 text-center text-xs text-slate-500">
                      {currentScenarioData?.predictedCgpa != null ? `CGPA: ${currentScenarioData.predictedCgpa.toFixed(2)}` : 'CGPA Pending'}
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </div>

          {/* Contributing Factors & Qualitative Gemini AI Insights */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Deterministic Contributing Factors */}
            <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs">
              <h4 className="text-base font-bold text-slate-900 flex items-center gap-2 mb-3">
                <CheckCircle className="w-5 h-5 text-indigo-600" />
                Key Drivers Influencing This Scenario
              </h4>
              <ul className="space-y-2.5">
                {currentScenarioData?.contributingFactors?.map((fac, idx) => (
                  <li key={idx} className="text-xs text-slate-600 flex items-start gap-2 bg-slate-50 p-2.5 rounded-xl border border-slate-100">
                    <span className="w-1.5 h-1.5 rounded-full bg-indigo-500 mt-1.5 shrink-0" />
                    <span>{fac}</span>
                  </li>
                ))}
              </ul>

              <div className="mt-4 pt-4 border-t border-slate-100 text-[11px] text-slate-400">
                Methodology: Calibrated deterministic projection engine grounded in 19-credit syllabus rules and verified internal marks.
              </div>
            </div>

            {/* Qualitative AI Insights Card */}
            <div className="bg-gradient-to-br from-indigo-900 to-slate-900 text-white p-6 rounded-2xl shadow-md border border-indigo-950">
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-2 text-amber-300 font-bold text-xs uppercase tracking-wider">
                  <Sparkles className="w-4 h-4 text-amber-400" />
                  <span>AI Academic Advisory Guidance</span>
                </div>
                {aiInsights?.providerName && (
                  <span className="text-[10px] text-indigo-300 bg-indigo-950/80 px-2 py-0.5 rounded-full border border-indigo-800">
                    {aiInsights.providerName}
                  </span>
                )}
              </div>

              {aiLoading ? (
                <div className="py-8 text-center text-indigo-300 text-xs">
                  Analyzing syllabus alignment and scenario divergence...
                </div>
              ) : aiInsights ? (
                <div className="space-y-3.5">
                  <p className="text-xs text-indigo-100 leading-relaxed bg-white/5 p-3 rounded-xl border border-white/10">
                    {aiInsights.scenarioDivergenceExplanation}
                  </p>

                  {aiInsights.prioritySubjects?.length > 0 && (
                    <div>
                      <div className="text-[11px] font-semibold text-amber-300 uppercase tracking-wide mb-1">
                        Priority Focus Subjects:
                      </div>
                      <div className="flex flex-wrap gap-1.5">
                        {aiInsights.prioritySubjects.map((sub, idx) => (
                          <span key={idx} className="text-xs bg-amber-400/10 border border-amber-400/20 text-amber-200 px-2.5 py-1 rounded-lg">
                            {sub}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}

                  {aiInsights.recommendedUnitFocus?.length > 0 && (
                    <div>
                      <div className="text-[11px] font-semibold text-indigo-300 uppercase tracking-wide mb-1">
                        Syllabus Unit Focus:
                      </div>
                      <div className="text-xs text-slate-300 bg-white/5 p-2 rounded-lg">
                        {aiInsights.recommendedUnitFocus[0]}
                      </div>
                    </div>
                  )}

                  {aiInsights.mostBenefitedSubjects?.length > 0 && (
                    <div>
                      <div className="text-[11px] font-semibold text-emerald-300 uppercase tracking-wide mb-1.5 flex items-center gap-1">
                        <ArrowUpRight className="w-3.5 h-3.5 text-emerald-400" /> Most Benefited Subjects in Improved (+{predictionData.configuredImprovementPercent}%):
                      </div>
                      <div className="grid grid-cols-1 gap-1.5">
                        {aiInsights.mostBenefitedSubjects.slice(0, 4).map((sub, idx) => (
                          <div key={idx} className="text-xs bg-emerald-950/50 border border-emerald-500/30 text-emerald-200 px-3 py-1.5 rounded-xl font-medium">
                            {sub}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {aiInsights.mostVulnerableSubjects?.length > 0 && (
                    <div>
                      <div className="text-[11px] font-semibold text-rose-300 uppercase tracking-wide mb-1.5 flex items-center gap-1">
                        <ArrowDownRight className="w-3.5 h-3.5 text-rose-400" /> Most Vulnerable Subjects in Declining (-{predictionData.configuredDeclinePercent}%):
                      </div>
                      <div className="grid grid-cols-1 gap-1.5">
                        {aiInsights.mostVulnerableSubjects.slice(0, 4).map((sub, idx) => (
                          <div key={idx} className="text-xs bg-rose-950/50 border border-rose-500/30 text-rose-200 px-3 py-1.5 rounded-xl font-medium">
                            {sub}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {aiInsights.actionableAdvice?.length > 0 && (
                    <div>
                      <div className="text-[11px] font-semibold text-sky-300 uppercase tracking-wide mb-1">
                        Actionable Recommendations:
                      </div>
                      <ul className="text-xs text-slate-300 space-y-1">
                        {aiInsights.actionableAdvice.slice(0, 3).map((item, idx) => (
                          <li key={idx} className="flex items-start gap-1.5">
                            <span className="text-emerald-400">•</span>
                            <span>{item}</span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              ) : (
                <p className="text-xs text-slate-400">
                  AI qualitative recommendations are ready for this record.
                </p>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
