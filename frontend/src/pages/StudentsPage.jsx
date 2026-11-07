import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Search, Users, ArrowRight, Filter, AlertCircle, FileText } from 'lucide-react';
import { studentService } from '../api/studentService';
import Badge from '../components/common/Badge';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ErrorAlert from '../components/common/ErrorAlert';
import EmptyState from '../components/common/EmptyState';

export default function StudentsPage() {
  const navigate = useNavigate();
  const [students, setStudents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [searchQuery, setSearchQuery] = useState('');
  const [directRollQuery, setDirectRollQuery] = useState('');
  const [directSearchLoading, setDirectSearchLoading] = useState(false);
  const [directSearchError, setDirectSearchError] = useState(null);

  const [statusFilter, setStatusFilter] = useState('ALL');

  useEffect(() => {
    loadStudents();
  }, []);

  const loadStudents = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await studentService.getAllStudents();
      setStudents(data || []);
    } catch (err) {
      setError(err.message || 'Failed to load students');
    } finally {
      setLoading(false);
    }
  };

  const handleDirectSearch = async (e) => {
    e.preventDefault();
    const roll = directRollQuery.trim();
    if (!roll) return;

    try {
      setDirectSearchLoading(true);
      setDirectSearchError(null);
      const student = await studentService.getStudentByRoll(roll);
      if (student) {
        navigate(`/students/${encodeURIComponent(student.rollNumber)}/report`);
      }
    } catch (err) {
      setDirectSearchError(err.message || `No student found for roll number ${roll}`);
    } finally {
      setDirectSearchLoading(false);
    }
  };

  // Filter students by name or roll number, and status
  const filteredStudents = students.filter((s) => {
    const query = searchQuery.trim().toLowerCase();
    const matchesQuery =
      !query ||
      s.rollNumber.toLowerCase().includes(query) ||
      s.name.toLowerCase().includes(query);

    if (!matchesQuery) return false;

    if (statusFilter === 'ALL') return true;
    if (statusFilter === 'GOOD') return s.prediction === 'Good Progress' || s.prediction === 'Consistent Performer';
    if (statusFilter === 'MONITOR') return s.prediction === 'Monitor Progress';
    if (statusFilter === 'SUPPORT') return s.prediction === 'May Need Additional Support';
    return true;
  });

  return (
    <div className="space-y-6">
      {/* Search Header Banner */}
      <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-xs">
        <div className="max-w-2xl">
          <h2 className="text-xl font-bold text-slate-900 tracking-tight">Student Academic Roster & Lookup</h2>
          <p className="text-xs text-slate-500 mt-1 leading-relaxed">
            Directly enter any student roll number (e.g., <code className="font-mono text-indigo-600 bg-indigo-50 px-1.5 py-0.5 rounded">1602-25-733-001</code>) to open their comprehensive diagnostic report, or filter the active class cohort below.
          </p>

          {/* Direct Roll Number Search Box */}
          <form onSubmit={handleDirectSearch} className="mt-4 flex flex-col sm:flex-row gap-2">
            <div className="relative flex-1">
              <input
                type="text"
                value={directRollQuery}
                onChange={(e) => setDirectRollQuery(e.target.value)}
                placeholder="Enter exact roll number (e.g., 1602-25-733-001)..."
                className="w-full pl-9 pr-3 py-2 text-sm bg-slate-50 border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
              />
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
            </div>
            <button
              type="submit"
              disabled={directSearchLoading || !directRollQuery.trim()}
              className="px-5 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 rounded-xl transition-colors shrink-0 shadow-xs cursor-pointer"
            >
              {directSearchLoading ? 'Searching...' : 'Open Report'}
            </button>
          </form>

          {directSearchError && (
            <div className="mt-3 text-xs text-rose-600 flex items-center gap-1.5">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{directSearchError}</span>
            </div>
          )}
        </div>
      </div>

      <ErrorAlert message={error} retryAction={loadStudents} />

      {/* Roster Table Section */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
        {/* Table Controls / Filters */}
        <div className="p-4 sm:p-5 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="relative max-w-sm w-full">
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Filter roster by roll or name..."
              className="w-full pl-9 pr-3 py-1.5 text-xs bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
            />
            <Search className="w-4 h-4 text-slate-400 absolute left-2.5 top-2" />
          </div>

          {/* Status Filter Buttons */}
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1 sm:pb-0">
            <span className="text-xs text-slate-400 flex items-center gap-1 mr-1">
              <Filter className="w-3 h-3" /> Status:
            </span>
            {[
              { id: 'ALL', label: 'All Students' },
              { id: 'GOOD', label: 'On Track' },
              { id: 'MONITOR', label: 'Monitoring' },
              { id: 'SUPPORT', label: 'Needs Support' },
            ].map((f) => (
              <button
                key={f.id}
                onClick={() => setStatusFilter(f.id)}
                className={`px-3 py-1 text-xs font-semibold rounded-lg transition-colors cursor-pointer ${
                  statusFilter === f.id
                    ? 'bg-indigo-600 text-white shadow-xs'
                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                }`}
              >
                {f.label}
              </button>
            ))}
          </div>
        </div>

        {/* Table Data */}
        {loading ? (
          <div className="py-12">
            <LoadingSpinner message="Fetching students from database..." />
          </div>
        ) : filteredStudents.length === 0 ? (
          <div className="p-8">
            <EmptyState
              title="No students match your filter"
              description="Try adjusting your roll number or status filter to see results."
              icon={Users}
            />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50/80 text-slate-500 uppercase tracking-wider font-semibold border-b border-slate-100">
                <tr>
                  <th className="py-3.5 px-4">Roll Number</th>
                  <th className="py-3.5 px-4">Student Name</th>
                  <th className="py-3.5 px-3 text-right">CGPA</th>
                  <th className="py-3.5 px-3 text-right">Prev SGPA</th>
                  <th className="py-3.5 px-3 text-right">Int 1 %</th>
                  <th className="py-3.5 px-3 text-right">Int 2 %</th>
                  <th className="py-3.5 px-3 text-right">Combined %</th>
                  <th className="py-3.5 px-4 text-center">Status / Prediction</th>
                  <th className="py-3.5 px-4 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 font-medium text-slate-700">
                {filteredStudents.map((st) => (
                  <tr key={st.rollNumber} className="hover:bg-slate-50/60 transition-colors">
                    <td className="py-3.5 px-4 font-mono font-semibold text-slate-900">
                      {st.rollNumber}
                    </td>
                    <td className="py-3.5 px-4 font-semibold text-slate-800">
                      {st.name}
                    </td>
                    <td className="py-3.5 px-3 text-right font-bold text-slate-900">
                      {st.cgpa?.toFixed(2) || 'N/A'}
                    </td>
                    <td className="py-3.5 px-3 text-right text-slate-600">
                      {st.lastSemesterSgpa?.toFixed(2) || 'N/A'}
                    </td>
                    <td className="py-3.5 px-3 text-right text-slate-600">
                      {st.internal1Percentage != null ? `${st.internal1Percentage.toFixed(1)}%` : '—'}
                    </td>
                    <td className="py-3.5 px-3 text-right text-slate-600">
                      {st.internal2Percentage != null ? `${st.internal2Percentage.toFixed(1)}%` : '—'}
                    </td>
                    <td className="py-3.5 px-3 text-right font-semibold text-indigo-600">
                      {st.combinedInternalPercentage != null ? `${st.combinedInternalPercentage.toFixed(1)}%` : '—'}
                    </td>
                    <td className="py-3.5 px-4 text-center">
                      <Badge status={st.prediction} size="small" />
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      <Link
                        to={`/students/${st.rollNumber}/report`}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 font-semibold rounded-lg transition-colors"
                      >
                        <FileText className="w-3.5 h-3.5" />
                        Report
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Footer Count */}
        <div className="p-3 bg-slate-50 border-t border-slate-100 text-xs text-slate-500 flex justify-between items-center px-4">
          <span>Showing <strong>{filteredStudents.length}</strong> of <strong>{students.length}</strong> enrolled students</span>
          <span className="font-mono text-[11px]">MySQL • student_performance</span>
        </div>
      </div>
    </div>
  );
}
