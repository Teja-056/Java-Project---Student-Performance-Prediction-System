import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  ArrowLeft,
  Sparkles,
  Award,
  AlertTriangle,
  TrendingUp,
  TrendingDown,
  BookOpen,
  Clock,
  CheckCircle2,
  HelpCircle,
  FileText,
  Printer,
  Compass,
} from 'lucide-react';
import { studentService } from '../api/studentService';
import Badge from '../components/common/Badge';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ErrorAlert from '../components/common/ErrorAlert';

export default function StudentReportPage() {
  const { rollNo } = useParams();
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Gemini AI Analysis State
  const [aiData, setAiData] = useState(null);
  const [aiLoading, setAiLoading] = useState(false);
  const [aiError, setAiError] = useState(null);

  useEffect(() => {
    loadReport();
  }, [rollNo]);

  const loadReport = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await studentService.getStudentReport(rollNo);
      setReport(data);
    } catch (err) {
      setError(err.message || `Failed to load report for student ${rollNo}`);
    } finally {
      setLoading(false);
    }
  };

  const handleGenerateAi = async () => {
    try {
      setAiLoading(true);
      setAiError(null);
      const data = await studentService.getAiAnalysis(rollNo);
      setAiData(data);
    } catch (err) {
      setAiError(err.message || 'Failed to generate Gemini AI analysis. Please verify your GEMINI_API_KEY.');
    } finally {
      setAiLoading(false);
    }
  };

  if (loading) {
    return <LoadingSpinner message={`Generating academic report for ${rollNo}...`} size="large" />;
  }

  if (error || !report) {
    return (
      <div className="space-y-4">
        <Link to="/students" className="inline-flex items-center gap-1.5 text-xs font-semibold text-indigo-600 hover:text-indigo-800">
          <ArrowLeft className="w-4 h-4" /> Back to Student Roster
        </Link>
        <ErrorAlert message={error || 'Student report could not be found.'} retryAction={loadReport} />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Navigation & Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <Link
          to="/students"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-600 hover:text-slate-900 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" /> Back to Student Roster
        </Link>
        <div className="flex items-center gap-3">
          <Link
            to={`/prediction/${encodeURIComponent(rollNo)}`}
            className="inline-flex items-center gap-1.5 px-3.5 py-1.5 text-xs font-semibold text-white bg-indigo-600 rounded-xl hover:bg-indigo-700 transition-colors shadow-xs"
          >
            <TrendingUp className="w-3.5 h-3.5" />
            SEE Predictions
          </Link>
          <button
            onClick={() => window.print()}
            className="inline-flex items-center gap-1.5 px-3.5 py-1.5 text-xs font-semibold text-slate-700 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 transition-colors shadow-xs cursor-pointer"
          >
            <Printer className="w-3.5 h-3.5 text-slate-500" />
            Print / Export
          </button>
        </div>
      </div>

      {/* Section A: Student Overview Card */}
      <div className="bg-white rounded-2xl border border-slate-200/80 p-6 shadow-xs">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-6 border-b border-slate-100">
          <div>
            <div className="flex items-center gap-3 flex-wrap">
              <h2 className="text-2xl font-bold text-slate-900 tracking-tight">{report.name}</h2>
              <span className="font-mono text-xs px-2.5 py-1 bg-slate-100 text-slate-700 rounded-lg font-semibold">
                {report.rollNumber}
              </span>
              <Badge status={report.riskClassification} />
            </div>
            <p className="text-xs text-slate-500 mt-1">
              B.E. Computer Science and Engineering • Autonomous Curriculum
            </p>
          </div>

          <div className="flex items-center gap-4 bg-slate-50 px-4 py-2.5 rounded-xl border border-slate-100 flex-wrap">
            {report.sem1Sgpa != null && (
              <>
                <div>
                  <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">Semester I SGPA</span>
                  <p className="text-xl font-bold text-slate-900">{report.sem1Sgpa.toFixed(2)}</p>
                </div>
                <div className="w-px h-8 bg-slate-200" />
              </>
            )}
            <div>
              <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">Semester II SGPA</span>
              <p className="text-xl font-bold text-slate-900">{report.lastSemesterSgpa?.toFixed(2) || 'N/A'}</p>
            </div>
            <div className="w-px h-8 bg-slate-200" />
            <div>
              <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">Previous CGPA</span>
              <p className="text-xl font-bold text-slate-900">{report.cgpa?.toFixed(2) || 'N/A'}</p>
            </div>
            {report.attendancePercent != null && (
              <>
                <div className="w-px h-8 bg-slate-200" />
                <div>
                  <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">Attendance</span>
                  <p className="text-xl font-bold text-emerald-600">{report.attendancePercent.toFixed(1)}%</p>
                </div>
              </>
            )}
          </div>
        </div>

        {/* Section B: Overall Internal Assessment Standing */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 pt-6">
          <div className="p-3.5 bg-slate-50/80 rounded-xl border border-slate-100">
            <span className="text-xs text-slate-500 font-medium">Internal 1 Percentage</span>
            <p className="text-lg font-bold text-slate-900 mt-1 font-mono">
              {report.internal1Percentage != null ? `${report.internal1Percentage.toFixed(1)}%` : '—'}
            </p>
            <span className="text-[11px] text-slate-400">
              {report.internal1IsPartial ? 'Partial Assessment (<290 marks)' : 'Units 1 & 2 Syllabus (Max: 290)'}
            </span>
          </div>

          <div className="p-3.5 bg-slate-50/80 rounded-xl border border-slate-100">
            <span className="text-xs text-slate-500 font-medium">Internal 2 Percentage</span>
            <p className="text-lg font-bold text-slate-900 mt-1 font-mono">
              {report.internal2Percentage != null ? `${report.internal2Percentage.toFixed(1)}%` : '—'}
            </p>
            <span className="text-[11px] text-slate-400">
              {report.internal2IsPartial ? 'Partial Assessment (<290 marks)' : 'Units 3, 4 & 5 Syllabus (Max: 290)'}
            </span>
          </div>

          <div className="p-3.5 bg-slate-50/80 rounded-xl border border-slate-100">
            <span className="text-xs text-slate-500 font-medium">Combined Internal Avg</span>
            <p className="text-lg font-bold text-indigo-600 mt-1 font-mono">
              {report.combinedInternalPercentage != null ? `${report.combinedInternalPercentage.toFixed(1)}%` : '—'}
            </p>
            <span className="text-[11px] text-slate-400">Weighted CIE Aggregate</span>
          </div>

          <div className="p-3.5 bg-slate-50/80 rounded-xl border border-slate-100">
            <span className="text-xs text-slate-500 font-medium">Domain Trajectory</span>
            <div className="flex items-center gap-1.5 mt-1">
              <span className="text-xs font-bold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded">
                +{report.strongestSubject || 'N/A'}
              </span>
              <span className="text-xs font-bold text-rose-700 bg-rose-50 px-2 py-0.5 rounded">
                -{report.weakestSubject || 'N/A'}
              </span>
            </div>
            <span className="text-[11px] text-slate-400">Strongest vs. Weakest</span>
          </div>
        </div>
      </div>

      {/* Academic Performance Scoring Model Card */}
      <div className="bg-white rounded-2xl border border-slate-200/80 p-6 shadow-xs">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-5 border-b border-slate-100">
          <div>
            <div className="flex items-center gap-2">
              <span className="px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider bg-indigo-50 text-indigo-700 border border-indigo-200">
                Performance Evaluation Model
              </span>
              <h3 className="text-base font-bold text-slate-900">Academic Performance Scoring Breakdown</h3>
            </div>
            <p className="text-xs text-slate-500 mt-1">
              Formula: <code className="font-mono text-indigo-600 bg-slate-50 px-1 py-0.5 rounded">Final Score = 70% CIE (Credit-Weighted) + 20% Previous SGPA (×10) + 10% Attendance</code>
            </p>
          </div>

          <div className="flex items-center gap-3 bg-slate-50 px-4 py-2.5 rounded-xl border border-slate-100">
            <div>
              <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">Final Academic Score</span>
              <p className="text-2xl font-bold font-mono text-indigo-600">
                {report.finalAcademicScore != null ? report.finalAcademicScore.toFixed(2) : '—'}
                <span className="text-xs text-slate-400 font-normal"> / 100</span>
              </p>
            </div>
            <div className="w-px h-8 bg-slate-200" />
            <div>
              <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-400 block mb-0.5">Classification</span>
              <Badge status={report.riskClassification} />
            </div>
          </div>
        </div>

        {/* 3 Formula Components Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-5">
          {/* CIE Component (70%) */}
          <div className="p-4 rounded-xl bg-slate-50/80 border border-slate-100">
            <div className="flex items-center justify-between mb-1">
              <span className="text-xs font-bold text-slate-700">1. Current Semester CIE</span>
              <span className="text-[11px] font-bold px-2 py-0.5 rounded bg-indigo-100 text-indigo-700">70% Weight</span>
            </div>
            <div className="mt-2 space-y-1">
              <div className="flex justify-between text-xs">
                <span className="text-slate-500">Credit-Weighted CIE:</span>
                <span className="font-bold font-mono text-slate-800">{report.overallCiePercentage != null ? `${report.overallCiePercentage.toFixed(2)}%` : '—'}</span>
              </div>
              <div className="flex justify-between text-xs">
                <span className="text-slate-500">Contribution (0.70 × CIE):</span>
                <span className="font-bold font-mono text-indigo-600">+{report.cieComponent != null ? report.cieComponent.toFixed(2) : '—'} pts</span>
              </div>
              <p className="text-[10px] text-slate-400 pt-1 border-t border-slate-200/60 mt-1">
                Weighted across 19 total curriculum credits
              </p>
            </div>
          </div>

          {/* SGPA Component (20%) */}
          <div className="p-4 rounded-xl bg-slate-50/80 border border-slate-100">
            <div className="flex items-center justify-between mb-1">
              <span className="text-xs font-bold text-slate-700">2. Previous Semester SGPA</span>
              <span className="text-[11px] font-bold px-2 py-0.5 rounded bg-blue-100 text-blue-700">20% Weight</span>
            </div>
            <div className="mt-2 space-y-1">
              <div className="flex justify-between text-xs">
                <span className="text-slate-500">Semester II SGPA (×10):</span>
                <span className="font-bold font-mono text-slate-800">{report.lastSemesterSgpa != null ? `${(report.lastSemesterSgpa * 10).toFixed(1)}%` : '—'}</span>
              </div>
              <div className="flex justify-between text-xs">
                <span className="text-slate-500">Contribution (0.20 × SGPA × 10):</span>
                <span className="font-bold font-mono text-blue-600">+{report.sgpaComponent != null ? report.sgpaComponent.toFixed(2) : '—'} pts</span>
              </div>
              <p className="text-[10px] text-slate-400 pt-1 border-t border-slate-200/60 mt-1">
                Measured on 10.0 scale ({report.lastSemesterSgpa?.toFixed(2) || 'N/A'})
              </p>
            </div>
          </div>

          {/* Attendance Component (10%) */}
          <div className="p-4 rounded-xl bg-slate-50/80 border border-slate-100">
            <div className="flex items-center justify-between mb-1">
              <span className="text-xs font-bold text-slate-700">3. Student Attendance</span>
              <span className="text-[11px] font-bold px-2 py-0.5 rounded bg-emerald-100 text-emerald-700">10% Weight</span>
            </div>
            <div className="mt-2 space-y-1">
              <div className="flex justify-between text-xs">
                <span className="text-slate-500">Attendance Percentage:</span>
                <span className="font-bold font-mono text-slate-800">{report.attendancePercent != null ? `${report.attendancePercent.toFixed(1)}%` : '—'}</span>
              </div>
              <div className="flex justify-between text-xs">
                <span className="text-slate-500">Contribution (0.10 × Att):</span>
                <span className="font-bold font-mono text-emerald-600">+{report.attendanceComponent != null ? report.attendanceComponent.toFixed(2) : '—'} pts</span>
              </div>
              <p className="text-[10px] text-slate-400 pt-1 border-t border-slate-200/60 mt-1">
                Configurable project default threshold: 75.0%
              </p>
            </div>
          </div>
        </div>

        {/* Warning Flags Bar */}
        <div className="mt-4 pt-4 border-t border-slate-100 flex flex-wrap gap-2.5 items-center">
          <span className="text-xs font-semibold text-slate-500">Warning Indicators:</span>

          {report.attendanceWarning ? (
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg text-xs font-semibold bg-amber-50 text-amber-800 border border-amber-200">
              <AlertTriangle className="w-3.5 h-3.5 text-amber-600" />
              Attendance Warning: {report.attendancePercent?.toFixed(1)}% is below 75.0% project default threshold
            </span>
          ) : (
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg text-xs font-semibold bg-emerald-50 text-emerald-800 border border-emerald-200">
              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
              Attendance Meets Default Threshold (≥ 75.0%)
            </span>
          )}

          {report.criticalAcademicWarning ? (
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg text-xs font-semibold bg-rose-50 text-rose-800 border border-rose-200">
              <AlertTriangle className="w-3.5 h-3.5 text-rose-600" />
              Critical Academic Warning: Overall CIE ({report.overallCiePercentage?.toFixed(1)}%) &lt; 40.0%
            </span>
          ) : (
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg text-xs font-semibold bg-emerald-50 text-emerald-800 border border-emerald-200">
              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
              CIE Meets Passing Minimum (≥ 40.0%)
            </span>
          )}

          {report.incompleteAssessmentWarning && (
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg text-xs font-semibold bg-amber-50 text-amber-800 border border-amber-200">
              <AlertTriangle className="w-3.5 h-3.5 text-amber-600" />
              Incomplete Assessment Warning: Missing marks detected
            </span>
          )}
        </div>
      </div>

      {/* Section C: Subject-Wise Performance Detailed Breakdown */}
      <div className="bg-white rounded-2xl border border-slate-200/80 p-6 shadow-xs">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h3 className="text-base font-bold text-slate-900">Subject-Wise Assessment Breakdown</h3>
            <p className="text-xs text-slate-500">Internal 1 and Internal 2 marks comparison for the eight theory subjects</p>
          </div>
          <span className="text-xs text-slate-400 font-mono">Max: 40/30 CIE</span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-50 text-slate-500 uppercase tracking-wider font-semibold border-b border-slate-100">
              <tr>
                <th className="py-3 px-4">Subject</th>
                <th className="py-3 px-3 text-right">Max Marks</th>
                <th className="py-3 px-3 text-right">Int 1 Marks</th>
                <th className="py-3 px-3 text-right">Int 1 %</th>
                <th className="py-3 px-3 text-right">Int 2 Marks</th>
                <th className="py-3 px-3 text-right">Int 2 %</th>
                <th className="py-3 px-4 text-center">Trend (% Change)</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 font-medium text-slate-700">
              {report.subjectBreakdown?.map((subj) => {
                const change = subj.trendPercentage;
                const isPositive = change != null && change > 0;
                const isNegative = change != null && change < 0;

                return (
                  <tr key={subj.subjectCode} className="hover:bg-slate-50/50 transition-colors">
                    <td className="py-3 px-4">
                      <span className="font-semibold text-slate-900">{subj.subjectName}</span>
                      <span className="ml-2 font-mono text-slate-400 text-[11px]">({subj.subjectCode})</span>
                    </td>
                    <td className="py-3 px-3 text-right text-slate-500 font-mono">{subj.maxMarks}</td>
                    <td className="py-3 px-3 text-right font-mono text-slate-800">
                      {subj.internal1Marks != null ? subj.internal1Marks.toFixed(1) : '—'}
                    </td>
                    <td className="py-3 px-3 text-right text-slate-600 font-semibold">
                      {subj.internal1Percentage != null ? `${subj.internal1Percentage.toFixed(1)}%` : '—'}
                    </td>
                    <td className="py-3 px-3 text-right font-mono text-slate-800">
                      {subj.internal2Marks != null ? subj.internal2Marks.toFixed(1) : '—'}
                    </td>
                    <td className="py-3 px-3 text-right text-slate-600 font-semibold">
                      {subj.internal2Percentage != null ? `${subj.internal2Percentage.toFixed(1)}%` : '—'}
                    </td>
                    <td className="py-3 px-4 text-center">
                      {change != null ? (
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
                          {isPositive ? `+${change.toFixed(1)}%` : `${change.toFixed(1)}%`}
                        </span>
                      ) : (
                        <span className="text-xs text-slate-400">N/A</span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* Section E: Unit-Level Remedial Advice & Recommendations */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs">
          <div className="flex items-center gap-2 mb-3">
            <div className="w-8 h-8 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
              <BookOpen className="w-4 h-4" />
            </div>
            <div>
              <h4 className="text-sm font-bold text-slate-900">Unit-Level Remedial Recommendations</h4>
              <p className="text-[11px] text-slate-500">Assessment Unit Mapping (Int 1: Units 1-2 | Int 2: Units 3-5)</p>
            </div>
          </div>

          <div className="space-y-2 mt-3">
            {report.unitRecommendations?.map((rec, idx) => (
              <div key={idx} className="p-3 rounded-xl bg-slate-50 border border-slate-100 text-xs text-slate-700 flex items-start gap-2">
                <CheckCircle2 className="w-4 h-4 text-indigo-500 shrink-0 mt-0.5" />
                <span className="leading-relaxed">{rec}</span>
              </div>
            ))}
          </div>

          <p className="text-[10px] text-slate-400 mt-3 italic">
            * Note: System mapping links underperformance in Internal 1 to Units 1–2, and Internal 2 to Units 3–5. Exact unit-level marks are never artificially fabricated.
          </p>
        </div>

        <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs">
          <div className="flex items-center gap-2 mb-3">
            <div className="w-8 h-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
              <Compass className="w-4 h-4" />
            </div>
            <div>
              <h4 className="text-sm font-bold text-slate-900">Calculated Academic Guidance</h4>
              <p className="text-[11px] text-slate-500">Deterministic rules based on CGPA and internal trajectory</p>
            </div>
          </div>

          <div className="space-y-2 mt-3">
            {report.generalRecommendations?.map((rec, idx) => (
              <div key={idx} className="p-3 rounded-xl bg-slate-50 border border-slate-100 text-xs text-slate-700 flex items-start gap-2">
                <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
                <span className="leading-relaxed">{rec}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Section F: Google Gemini AI-Powered Academic Analysis */}
      <div className="bg-gradient-to-br from-indigo-950 via-slate-900 to-indigo-900 rounded-2xl border border-indigo-800 p-6 sm:p-8 text-white shadow-lg">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-6 border-b border-indigo-800/60">
          <div>
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-lg bg-indigo-500/20 text-indigo-300 flex items-center justify-center border border-indigo-400/30">
                <Sparkles className="w-4.5 h-4.5 text-indigo-300" />
              </div>
              <h3 className="text-lg font-bold text-white tracking-tight">AI-Powered Academic Diagnostics</h3>
              <span className="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-full bg-indigo-500/30 text-indigo-200 border border-indigo-400/30">
                Google Gemini API
              </span>
            </div>
            <p className="text-xs text-indigo-200/70 mt-1">
              Generates genuine personalized academic guidance grounded directly in the college syllabus
            </p>
          </div>

          <button
            onClick={handleGenerateAi}
            disabled={aiLoading}
            className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl font-semibold text-xs text-white bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 transition-all shadow-md shadow-indigo-600/30 shrink-0 cursor-pointer"
          >
            <Sparkles className="w-4 h-4" />
            {aiLoading ? 'Analyzing via Gemini...' : aiData ? 'Regenerate AI Analysis' : 'Generate AI Analysis'}
          </button>
        </div>

        {aiError && (
          <div className="mt-4 p-4 rounded-xl bg-rose-950/50 border border-rose-800 text-rose-200 text-xs">
            <p className="font-bold">AI Generation Issue:</p>
            <p className="mt-1">{aiError}</p>
          </div>
        )}

        {aiLoading && (
          <div className="py-12 flex flex-col items-center justify-center text-center space-y-3">
            <div className="w-10 h-10 border-3 border-indigo-400/30 border-t-indigo-400 rounded-full animate-spin"></div>
            <p className="text-sm font-medium text-indigo-200">Connecting to Google Gemini API (gemini-flash-lite-latest)...</p>
            <p className="text-xs text-indigo-300/60">Sending anonymized metrics and syllabus topics for real-time analysis...</p>
          </div>
        )}

        {!aiLoading && !aiData && !aiError && (
          <div className="py-10 text-center">
            <p className="text-sm text-indigo-200/80 max-w-md mx-auto leading-relaxed">
              Click <strong>"Generate AI Analysis"</strong> to obtain an AI diagnostic report analyzing academic performance trends, strengths, weaknesses, and syllabus-grounded focus topics.
            </p>
          </div>
        )}

        {aiData && !aiLoading && (
          <div className="mt-6 space-y-6">
            {/* Grounding & Verification Panel */}
            <div className="p-4 rounded-xl bg-indigo-900/40 border border-indigo-700/50 space-y-2.5 text-xs text-indigo-200">
              <div className="flex flex-wrap items-center justify-between gap-2 pb-2 border-b border-indigo-800/60">
                <span className="flex items-center gap-1.5 font-semibold">
                  <span className="w-2 h-2 rounded-full bg-emerald-400"></span>
                  AI Provider: <strong>{aiData.provider}</strong> ({aiData.model})
                </span>
                <span>Generated: <strong>{aiData.generatedAt}</strong></span>
              </div>
              <div className="text-xs">
                <span className="text-indigo-300 font-semibold block mb-0.5">Curriculum Grounding Status:</span>
                <p className="text-indigo-100 font-medium">{aiData.syllabusGroundingStatus}</p>
              </div>
              {aiData.groundedSubjects?.length > 0 && (
                <div className="pt-1.5">
                  <span className="text-[11px] font-bold text-emerald-300 uppercase tracking-wide block mb-1">
                    Verified Syllabus Grounded Courses:
                  </span>
                  <div className="flex flex-wrap gap-1.5">
                    {aiData.groundedSubjects.map((gs, idx) => (
                      <span key={idx} className="bg-emerald-950/60 border border-emerald-500/40 text-emerald-200 px-2.5 py-1 rounded-lg text-xs font-mono">
                        ✓ {gs}
                      </span>
                    ))}
                  </div>
                </div>
              )}
              {aiData.missingSyllabusSubjects?.length > 0 && (
                <div className="pt-1.5">
                  <span className="text-[11px] font-bold text-amber-300 uppercase tracking-wide block mb-0.5">
                    No Approved Syllabus Available For:
                  </span>
                  <p className="text-[11px] text-amber-200/80 mb-1.5">
                    Notice: No approved syllabus PDF is uploaded for these subjects. Recommendations for these subjects are grounded strictly in measured internal exam marks and performance trends.
                  </p>
                  <div className="flex flex-wrap gap-1.5">
                    {aiData.missingSyllabusSubjects.map((ms, idx) => (
                      <span key={idx} className="bg-amber-950/40 border border-amber-500/30 text-amber-200 px-2 py-0.5 rounded-lg text-[11px]">
                        • {ms}
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* Academic Summary */}
            <div className="p-4 rounded-xl bg-white/5 border border-white/10">
              <h4 className="text-xs uppercase font-bold tracking-wider text-indigo-300 mb-1">Executive Summary</h4>
              <p className="text-sm leading-relaxed text-indigo-50">{aiData.academicSummary}</p>
            </div>

            {/* Strengths & Weaknesses */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="p-4 rounded-xl bg-white/5 border border-white/10">
                <h4 className="text-xs uppercase font-bold tracking-wider text-emerald-400 mb-2 flex items-center gap-1.5">
                  <CheckCircle2 className="w-4 h-4" /> Academic Strengths
                </h4>
                <ul className="space-y-1.5 text-xs text-indigo-100">
                  {aiData.strengths?.map((st, i) => (
                    <li key={i} className="flex items-start gap-2">
                      <span className="text-emerald-400 mt-0.5">•</span>
                      <span>{st}</span>
                    </li>
                  ))}
                </ul>
              </div>

              <div className="p-4 rounded-xl bg-white/5 border border-white/10">
                <h4 className="text-xs uppercase font-bold tracking-wider text-rose-400 mb-2 flex items-center gap-1.5">
                  <AlertTriangle className="w-4 h-4" /> Focus & Weakness Areas
                </h4>
                <ul className="space-y-1.5 text-xs text-indigo-100">
                  {aiData.weaknesses?.map((w, i) => (
                    <li key={i} className="flex items-start gap-2">
                      <span className="text-rose-400 mt-0.5">•</span>
                      <span>{w}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </div>

            {/* Trend Analysis & Future Estimate */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="p-4 rounded-xl bg-white/5 border border-white/10">
                <h4 className="text-xs uppercase font-bold tracking-wider text-indigo-300 mb-1">Performance Trend Analysis</h4>
                <p className="text-xs leading-relaxed text-indigo-100">{aiData.trendAnalysis}</p>
              </div>
              <div className="p-4 rounded-xl bg-white/5 border border-white/10">
                <h4 className="text-xs uppercase font-bold tracking-wider text-indigo-300 mb-1">Estimated Future Performance</h4>
                <p className="text-xs leading-relaxed text-indigo-100">{aiData.estimatedFuturePerformance}</p>
              </div>
            </div>

            {/* Recommended Interventions */}
            <div className="p-4 rounded-xl bg-white/5 border border-white/10">
              <div className="flex items-center justify-between mb-2">
                <h4 className="text-xs uppercase font-bold tracking-wider text-amber-400">Actionable Interventions</h4>
              </div>
              <ul className="space-y-1.5 text-xs text-indigo-100">
                {aiData.recommendedInterventions?.map((inv, i) => (
                  <li key={i} className="flex items-start gap-2">
                    <span className="text-amber-400 mt-0.5">→</span>
                    <span>{inv}</span>
                  </li>
                ))}
              </ul>
            </div>

            {/* Syllabus-Grounded Focus Topics */}
            {aiData.focusTopics && aiData.focusTopics.length > 0 && (
              <div className="p-4 rounded-xl bg-indigo-900/50 border border-indigo-700/60">
                <h4 className="text-xs uppercase font-bold tracking-wider text-indigo-300 mb-2 flex items-center gap-1.5">
                  <BookOpen className="w-4 h-4 text-indigo-400" /> Syllabus Focus Topics (Grounded from Approved College Curriculum)
                </h4>
                <div className="flex flex-wrap gap-2">
                  {aiData.focusTopics.map((topic, i) => (
                    <span
                      key={i}
                      className="px-3 py-1 rounded-lg text-xs font-medium bg-indigo-800/80 text-indigo-100 border border-indigo-600/50"
                    >
                      {topic}
                    </span>
                  ))}
                </div>
              </div>
            )}

            {/* Subject-Wise Improvement Plan */}
            {aiData.subjectWiseImprovementPlan && Object.keys(aiData.subjectWiseImprovementPlan).length > 0 && (
              <div className="p-4 rounded-xl bg-white/5 border border-white/10">
                <h4 className="text-xs uppercase font-bold tracking-wider text-indigo-300 mb-3">Subject-Specific Remedial Plan</h4>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {Object.entries(aiData.subjectWiseImprovementPlan).map(([subject, plan]) => (
                    <div key={subject} className="p-3 rounded-lg bg-indigo-950/60 border border-indigo-800/40 text-xs">
                      <span className="font-bold text-indigo-200 block mb-1">{subject}</span>
                      <p className="text-indigo-100/90 leading-relaxed">{plan}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
