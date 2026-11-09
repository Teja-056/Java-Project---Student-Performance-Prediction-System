import React, { useState, useEffect } from 'react';
import {
  Database,
  Upload,
  CheckCircle2,
  Clock,
  AlertTriangle,
  Trash2,
  Play,
  FileSpreadsheet,
  FileText,
  Calendar,
  Layers,
  Sparkles,
  ShieldAlert,
  ArrowRight,
  Info,
  RefreshCw,
  X,
} from 'lucide-react';
import { datasetService } from '../api/datasetService';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ErrorAlert from '../components/common/ErrorAlert';
import Badge from '../components/common/Badge';

export default function DatasetManagementPage() {
  const [datasets, setDatasets] = useState([]);
  const [activeDataset, setActiveDataset] = useState(null);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);

  // Upload modal state
  const [showUploadModal, setShowUploadModal] = useState(false);
  const [uploadFile, setUploadFile] = useState(null);
  const [datasetName, setDatasetName] = useState('');
  const [semester, setSemester] = useState('Semester III');
  const [academicYear, setAcademicYear] = useState('2025-2026');
  const [activateImmediately, setActivateImmediately] = useState(true);
  const [uploadResult, setUploadResult] = useState(null);

  // Details/Report modal state
  const [selectedDataset, setSelectedDataset] = useState(null);
  const [showDetailsModal, setShowDetailsModal] = useState(false);

  // Delete confirmation modal state
  const [datasetToDelete, setDatasetToDelete] = useState(null);

  const fetchDatasets = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await datasetService.getAllDatasets();
      setDatasets(data);
      const active = data.find((d) => d.active) || null;
      setActiveDataset(active);
    } catch (err) {
      setError(err.message || 'Failed to load dataset history.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDatasets();
  }, []);

  const handleActivate = async (datasetId, datasetName) => {
    try {
      setActionLoading(true);
      setError(null);
      setSuccessMsg(null);
      await datasetService.activateDataset(datasetId);
      setSuccessMsg(`Successfully activated dataset '${datasetName}'. All predictions, analytics, and student records are now scoped to this cohort.`);
      await fetchDatasets();
    } catch (err) {
      setError(err.message || 'Failed to activate dataset.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteConfirm = async () => {
    if (!datasetToDelete) return;
    try {
      setActionLoading(true);
      setError(null);
      await datasetService.deleteDataset(datasetToDelete.datasetId);
      setSuccessMsg(`Dataset #${datasetToDelete.datasetId} ('${datasetToDelete.name}') was deleted successfully.`);
      setDatasetToDelete(null);
      await fetchDatasets();
    } catch (err) {
      setError(err.message || 'Failed to delete dataset.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleUploadSubmit = async (e) => {
    e.preventDefault();
    if (!uploadFile) {
      setError('Please select an Excel (.xlsx or .xls) file to upload.');
      return;
    }

    try {
      setActionLoading(true);
      setError(null);
      setUploadResult(null);

      const formData = new FormData();
      formData.append('file', uploadFile);
      if (datasetName.trim()) formData.append('name', datasetName.trim());
      if (semester.trim()) formData.append('semester', semester.trim());
      if (academicYear.trim()) formData.append('academicYear', academicYear.trim());
      formData.append('activateImmediately', activateImmediately);

      const result = await datasetService.uploadDataset(formData);
      setUploadResult(result);
      setSuccessMsg(result.message || 'Dataset uploaded successfully.');
      await fetchDatasets();

      if (result.success && !result.validationErrors?.length) {
        setShowUploadModal(false);
        resetUploadForm();
      }
    } catch (err) {
      setError(err.message || 'Failed to upload dataset.');
    } finally {
      setActionLoading(false);
    }
  };

  const resetUploadForm = () => {
    setUploadFile(null);
    setDatasetName('');
    setSemester('Semester III');
    setAcademicYear('2025-2026');
    setActivateImmediately(true);
    setUploadResult(null);
  };

  const parseReport = (reportStr) => {
    if (!reportStr) return null;
    try {
      return JSON.parse(reportStr);
    } catch (e) {
      return null;
    }
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto pb-12">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-6 rounded-2xl border border-slate-200/80 shadow-xs">
        <div>
          <div className="flex items-center gap-2 text-indigo-600 font-semibold text-xs tracking-wider uppercase mb-1">
            <Layers className="w-4 h-4" /> Multi-Cohort System
          </div>
          <h2 className="text-xl sm:text-2xl font-bold text-slate-900 tracking-tight">Dataset Management</h2>
          <p className="text-xs text-slate-500 mt-1 max-w-2xl leading-relaxed">
            Manage multiple independent student cohorts. Upload new Excel files without overwriting previous batches, switch between historical cohorts, and isolate analytics and predictions.
          </p>
        </div>

        <div className="flex items-center gap-2.5">
          <button
            onClick={fetchDatasets}
            disabled={loading || actionLoading}
            className="p-2 text-slate-500 hover:text-slate-700 bg-slate-50 hover:bg-slate-100 rounded-xl border border-slate-200 transition-colors cursor-pointer"
            title="Refresh datasets"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>

          <button
            onClick={() => {
              resetUploadForm();
              setShowUploadModal(true);
            }}
            className="inline-flex items-center gap-2 px-4 py-2.5 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-700 rounded-xl transition-colors shadow-sm cursor-pointer"
          >
            <Upload className="w-4 h-4" />
            Upload New Dataset
          </button>
        </div>
      </div>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      {successMsg && (
        <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-sm flex items-start justify-between gap-3 shadow-xs">
          <div className="flex items-start gap-3">
            <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
            <div>
              <p className="font-bold text-emerald-950">Action Completed</p>
              <p className="mt-0.5 text-xs text-emerald-800">{successMsg}</p>
            </div>
          </div>
          <button onClick={() => setSuccessMsg(null)} className="text-emerald-700 hover:text-emerald-900 text-xs font-semibold cursor-pointer">
            Dismiss
          </button>
        </div>
      )}

      {/* Active Dataset Hero Card */}
      {activeDataset ? (
        <div className="bg-gradient-to-br from-emerald-900 via-teal-900 to-slate-900 rounded-2xl p-6 sm:p-7 text-white shadow-md relative overflow-hidden">
          <div className="absolute right-0 top-0 translate-x-12 -translate-y-8 w-64 h-64 bg-emerald-500/10 rounded-full blur-3xl pointer-events-none"></div>

          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6 relative z-10">
            <div className="space-y-3 max-w-2xl">
              <div className="flex items-center gap-2.5">
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-emerald-400/20 text-emerald-300 border border-emerald-400/30">
                  <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
                  CURRENTLY ACTIVE COHORT
                </span>
                <span className="text-xs text-emerald-200 font-mono">ID #{activeDataset.datasetId}</span>
                <span className="text-xs text-slate-400 font-mono">({activeDataset.datasetCode})</span>
              </div>

              <div>
                <h3 className="text-xl sm:text-2xl font-black text-white tracking-tight">{activeDataset.name}</h3>
                <p className="text-xs text-emerald-100/70 mt-1 flex items-center gap-3">
                  <span>File: <strong className="text-white font-mono">{activeDataset.originalFilename}</strong></span>
                  <span>•</span>
                  <span>{activeDataset.semester} ({activeDataset.academicYear || '2025-2026'})</span>
                </p>
              </div>

              <p className="text-xs text-emerald-100/80 leading-relaxed">
                All student searches, performance prediction scenarios, subject analytics, and Gemini AI advisories are currently operating exclusively on this dataset.
              </p>
            </div>

            {/* Quick Metrics */}
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 shrink-0">
              <div className="bg-white/10 backdrop-blur-md rounded-xl p-3.5 border border-white/10">
                <span className="text-[11px] font-medium text-emerald-200 uppercase tracking-wider">Students</span>
                <p className="text-2xl font-black text-white mt-1">{activeDataset.validCount}</p>
                <span className="text-[10px] text-emerald-300 font-medium">Valid records</span>
              </div>

              <div className="bg-white/10 backdrop-blur-md rounded-xl p-3.5 border border-white/10">
                <span className="text-[11px] font-medium text-emerald-200 uppercase tracking-wider">Semester</span>
                <p className="text-base font-bold text-white mt-1 truncate">{activeDataset.semester}</p>
                <span className="text-[10px] text-emerald-300 font-medium">{activeDataset.academicYear || '2025-26'}</span>
              </div>

              <div className="bg-white/10 backdrop-blur-md rounded-xl p-3.5 border border-white/10 col-span-2 sm:col-span-1">
                <span className="text-[11px] font-medium text-emerald-200 uppercase tracking-wider">Status</span>
                <p className="text-base font-bold text-emerald-300 mt-1 flex items-center gap-1.5">
                  <CheckCircle2 className="w-4 h-4 text-emerald-400" /> Ready
                </p>
                <span className="text-[10px] text-slate-300 truncate">{activeDataset.createdAt?.split(' ')[0]}</span>
              </div>
            </div>
          </div>
        </div>
      ) : (
        <div className="bg-amber-50 rounded-2xl p-6 border border-amber-200 text-amber-900 flex items-center gap-3">
          <AlertTriangle className="w-5 h-5 text-amber-600 shrink-0" />
          <p className="text-sm font-medium">No active dataset is currently selected. Please activate a dataset below.</p>
        </div>
      )}

      {/* Dataset History Table */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
        <div className="p-6 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <h3 className="text-base font-bold text-slate-900">Dataset History & Cohort Archives</h3>
            <p className="text-xs text-slate-500 mt-0.5">
              All previously uploaded datasets remain preserved. Switch cohorts at any time to inspect historical performance.
            </p>
          </div>
          <span className="text-xs font-semibold text-slate-500 bg-slate-100 px-3 py-1 rounded-full self-start sm:self-auto">
            {datasets.length} Total Datasets
          </span>
        </div>

        {loading ? (
          <div className="p-12 text-center">
            <LoadingSpinner message="Loading dataset archives..." />
          </div>
        ) : datasets.length === 0 ? (
          <div className="p-12 text-center text-slate-500 space-y-2">
            <Database className="w-10 h-10 text-slate-300 mx-auto" />
            <p className="font-semibold text-sm text-slate-700">No datasets found in database.</p>
            <p className="text-xs text-slate-400">Upload your first student Excel sheet to get started.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50/80 text-slate-600 font-semibold border-b border-slate-200">
                <tr>
                  <th className="py-3.5 px-4 text-center">Status</th>
                  <th className="py-3.5 px-4">Dataset Name & ID</th>
                  <th className="py-3.5 px-4">Filename</th>
                  <th className="py-3.5 px-4">Semester</th>
                  <th className="py-3.5 px-4 text-center">Students</th>
                  <th className="py-3.5 px-4">Uploaded At</th>
                  <th className="py-3.5 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {datasets.map((d) => (
                  <tr
                    key={d.datasetId}
                    className={`transition-colors hover:bg-slate-50/60 ${
                      d.active ? 'bg-emerald-50/30' : ''
                    }`}
                  >
                    {/* Status Pill */}
                    <td className="py-3.5 px-4 text-center">
                      {d.active ? (
                        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-bold text-emerald-700 bg-emerald-100 border border-emerald-300">
                          <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" /> Active
                        </span>
                      ) : d.status === 'FAILED' ? (
                        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-bold text-rose-700 bg-rose-100 border border-rose-200">
                          Failed
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-medium text-slate-600 bg-slate-100 border border-slate-200">
                          Archived
                        </span>
                      )}
                    </td>

                    {/* Name & ID */}
                    <td className="py-3.5 px-4">
                      <div className="font-bold text-slate-900 text-sm flex items-center gap-1.5">
                        {d.name}
                        {d.active && <span className="text-[10px] font-extrabold text-emerald-600 uppercase tracking-wider bg-emerald-50 px-1.5 py-0.5 rounded border border-emerald-200">Live</span>}
                      </div>
                      <div className="text-[11px] text-slate-400 font-mono mt-0.5">
                        ID: #{d.datasetId} • Code: {d.datasetCode}
                      </div>
                    </td>

                    {/* Filename */}
                    <td className="py-3.5 px-4 font-mono text-slate-700">
                      <div className="flex items-center gap-1.5">
                        <FileSpreadsheet className="w-4 h-4 text-emerald-600 shrink-0" />
                        <span className="truncate max-w-xs">{d.originalFilename}</span>
                      </div>
                    </td>

                    {/* Semester */}
                    <td className="py-3.5 px-4">
                      <span className="font-semibold text-slate-800">{d.semester}</span>
                      <div className="text-[11px] text-slate-400">{d.academicYear || '2025-26'}</div>
                    </td>

                    {/* Counts */}
                    <td className="py-3.5 px-4 text-center">
                      <span className="font-bold text-slate-900 text-sm">{d.validCount}</span>
                      {d.invalidCount > 0 && (
                        <span className="text-[11px] text-rose-600 font-semibold ml-1">
                          ({d.invalidCount} invalid)
                        </span>
                      )}
                    </td>

                    {/* Uploaded At */}
                    <td className="py-3.5 px-4 text-slate-500 whitespace-nowrap">
                      {d.createdAt || 'N/A'}
                    </td>

                    {/* Actions */}
                    <td className="py-3.5 px-4 text-right">
                      <div className="flex items-center justify-end gap-2">
                        {/* Activate Button */}
                        {!d.active && d.status !== 'FAILED' && d.validCount > 0 && (
                          <button
                            onClick={() => handleActivate(d.datasetId, d.name)}
                            disabled={actionLoading}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-emerald-700 bg-emerald-50 hover:bg-emerald-100 rounded-lg border border-emerald-200 transition-colors cursor-pointer shadow-2xs"
                            title="Set as currently active dataset"
                          >
                            <Play className="w-3.5 h-3.5" />
                            Activate
                          </button>
                        )}

                        {/* View Report / Details */}
                        <button
                          onClick={() => {
                            setSelectedDataset(d);
                            setShowDetailsModal(true);
                          }}
                          className="inline-flex items-center gap-1 px-2.5 py-1.5 text-xs font-semibold text-slate-600 bg-slate-50 hover:bg-slate-100 rounded-lg border border-slate-200 transition-colors cursor-pointer"
                          title="View validation details and reports"
                        >
                          <Info className="w-3.5 h-3.5" />
                          Details
                        </button>

                        {/* Delete Button (disabled for active) */}
                        <button
                          onClick={() => setDatasetToDelete(d)}
                          disabled={d.active || actionLoading}
                          className={`p-1.5 rounded-lg transition-colors ${
                            d.active
                              ? 'text-slate-300 cursor-not-allowed'
                              : 'text-rose-500 hover:text-rose-700 hover:bg-rose-50 cursor-pointer'
                          }`}
                          title={d.active ? 'Cannot delete the active dataset' : 'Delete this historical dataset'}
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Upload New Dataset Modal */}
      {showUploadModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-xs p-4 overflow-y-auto">
          <div className="bg-white rounded-2xl border border-slate-200 max-w-xl w-full p-6 shadow-xl space-y-5 animate-in fade-in duration-200">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <div className="flex items-center gap-2 text-indigo-600 font-bold text-base">
                <Upload className="w-5 h-5" />
                Upload New Student Dataset
              </div>
              <button
                onClick={() => setShowUploadModal(false)}
                className="text-slate-400 hover:text-slate-600 p-1 rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleUploadSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1">
                  Excel Spreadsheet (.xlsx, .xls) <span className="text-rose-500">*</span>
                </label>
                <input
                  type="file"
                  accept=".xlsx, .xls"
                  onChange={(e) => {
                    if (e.target.files && e.target.files[0]) {
                      setUploadFile(e.target.files[0]);
                      if (!datasetName) {
                        setDatasetName(e.target.files[0].name.replaceFirst ? e.target.files[0].name.replaceFirst(/\.[^.]+$/, '') : e.target.files[0].name.replace(/\.[^.]+$/, ''));
                      }
                    }
                  }}
                  className="block w-full text-xs text-slate-500 file:mr-4 file:py-2 file:px-4 file:rounded-xl file:border-0 file:text-xs file:font-semibold file:bg-indigo-50 file:text-indigo-700 hover:file:bg-indigo-100 border border-slate-200 rounded-xl p-1.5 cursor-pointer"
                  required
                />
                <p className="text-[11px] text-slate-400 mt-1">Supports standard 118-column and legacy format sheets.</p>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1">
                  Cohort / Dataset Name
                </label>
                <input
                  type="text"
                  value={datasetName}
                  onChange={(e) => setDatasetName(e.target.value)}
                  placeholder="e.g. Semester III - Batch 2026 (Section A)"
                  className="w-full px-3 py-2 text-xs bg-slate-50 border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1">
                    Semester
                  </label>
                  <select
                    value={semester}
                    onChange={(e) => setSemester(e.target.value)}
                    className="w-full px-3 py-2 text-xs bg-slate-50 border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                  >
                    <option value="Semester III">Semester III</option>
                    <option value="Semester IV">Semester IV</option>
                    <option value="Semester V">Semester V</option>
                    <option value="Semester VI">Semester VI</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1">
                    Academic Year
                  </label>
                  <input
                    type="text"
                    value={academicYear}
                    onChange={(e) => setAcademicYear(e.target.value)}
                    placeholder="2025-2026"
                    className="w-full px-3 py-2 text-xs bg-slate-50 border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                  />
                </div>
              </div>

              <div className="p-3 bg-indigo-50/60 rounded-xl border border-indigo-100 flex items-center gap-3">
                <input
                  type="checkbox"
                  id="activateNow"
                  checked={activateImmediately}
                  onChange={(e) => setActivateImmediately(e.target.checked)}
                  className="rounded border-slate-300 text-indigo-600 focus:ring-indigo-500"
                />
                <label htmlFor="activateNow" className="text-xs text-indigo-950 font-medium cursor-pointer">
                  Activate this dataset immediately after upload
                  <p className="text-[11px] text-indigo-700 font-normal">
                    If checked, EduPredict will switch to this dataset as soon as import succeeds.
                  </p>
                </label>
              </div>

              {uploadResult && uploadResult.validationErrors && uploadResult.validationErrors.length > 0 && (
                <div className="p-3 bg-amber-50 rounded-xl border border-amber-200 max-h-36 overflow-y-auto space-y-1 text-xs text-amber-900 font-mono">
                  <div className="font-bold flex items-center gap-1.5 mb-1 text-amber-950">
                    <ShieldAlert className="w-4 h-4 text-amber-600" /> Validation Warnings ({uploadResult.validationErrors.length}):
                  </div>
                  {uploadResult.validationErrors.map((err, i) => (
                    <div key={i} className="text-[11px] leading-tight">• {err}</div>
                  ))}
                </div>
              )}

              <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setShowUploadModal(false)}
                  className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-xl transition-colors cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={actionLoading || !uploadFile}
                  className="inline-flex items-center gap-2 px-5 py-2 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 rounded-xl transition-colors shadow-xs cursor-pointer"
                >
                  {actionLoading ? 'Uploading & Validating...' : 'Upload & Create Dataset'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Dataset Details / Report Modal */}
      {showDetailsModal && selectedDataset && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-xs p-4 overflow-y-auto">
          <div className="bg-white rounded-2xl border border-slate-200 max-w-2xl w-full p-6 shadow-xl space-y-5 animate-in fade-in duration-200">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <div className="flex items-center gap-2 text-slate-900 font-bold text-base">
                <FileText className="w-5 h-5 text-indigo-600" />
                Dataset #{selectedDataset.datasetId} Report & Details
              </div>
              <button
                onClick={() => setShowDetailsModal(false)}
                className="text-slate-400 hover:text-slate-600 p-1 rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-4">
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                <div className="bg-slate-50 p-3 rounded-xl border border-slate-100">
                  <span className="text-[11px] text-slate-500 font-medium">Cohort Name</span>
                  <p className="text-xs font-bold text-slate-900 mt-1 truncate">{selectedDataset.name}</p>
                </div>
                <div className="bg-slate-50 p-3 rounded-xl border border-slate-100">
                  <span className="text-[11px] text-slate-500 font-medium">Valid Students</span>
                  <p className="text-base font-bold text-emerald-700 mt-1">{selectedDataset.validCount}</p>
                </div>
                <div className="bg-slate-50 p-3 rounded-xl border border-slate-100">
                  <span className="text-[11px] text-slate-500 font-medium">Invalid Rows</span>
                  <p className="text-base font-bold text-rose-700 mt-1">{selectedDataset.invalidCount}</p>
                </div>
                <div className="bg-slate-50 p-3 rounded-xl border border-slate-100">
                  <span className="text-[11px] text-slate-500 font-medium">Status</span>
                  <p className="text-xs font-bold text-slate-900 mt-1">{selectedDataset.status}</p>
                </div>
              </div>

              <div>
                <span className="text-xs font-bold text-slate-700 uppercase tracking-wider">Original File:</span>
                <span className="font-mono text-xs text-slate-600 ml-2">{selectedDataset.originalFilename}</span>
              </div>

              {/* Validation Report Details */}
              {selectedDataset.validationReport && (
                <div>
                  <h4 className="text-xs font-bold text-slate-700 uppercase tracking-wider mb-2">
                    Validation & Error Report
                  </h4>
                  {(() => {
                    const parsed = parseReport(selectedDataset.validationReport);
                    if (!parsed || !parsed.errors || parsed.errors.length === 0) {
                      return (
                        <div className="p-3 bg-emerald-50 rounded-xl border border-emerald-100 text-xs text-emerald-800 flex items-center gap-2">
                          <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                          Zero validation errors detected. All rows passed verification.
                        </div>
                      );
                    }
                    return (
                      <div className="p-3 bg-slate-50 rounded-xl border border-slate-200 max-h-56 overflow-y-auto space-y-1 font-mono text-xs text-slate-700">
                        {parsed.errors.map((err, i) => (
                          <div key={i} className="py-0.5 border-b border-slate-200/50 last:border-0">
                            {err}
                          </div>
                        ))}
                      </div>
                    );
                  })()}
                </div>
              )}
            </div>

            <div className="flex items-center justify-end pt-3 border-t border-slate-100">
              <button
                onClick={() => setShowDetailsModal(false)}
                className="px-4 py-2 text-xs font-semibold text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-xl transition-colors cursor-pointer"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {datasetToDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-xs p-4">
          <div className="bg-white rounded-2xl border border-slate-200 max-w-md w-full p-6 shadow-xl space-y-4 animate-in fade-in duration-200">
            <div className="flex items-center gap-3 text-rose-600 font-bold text-base">
              <AlertTriangle className="w-6 h-6" />
              Confirm Dataset Deletion
            </div>

            <p className="text-xs text-slate-600 leading-relaxed">
              Are you sure you want to delete dataset <strong>#{datasetToDelete.datasetId}</strong> ('{datasetToDelete.name}')?
              <br /><br />
              <strong className="text-rose-700">Warning:</strong> This will permanently delete all {datasetToDelete.validCount} student records and their associated internal assessment marks in this cohort.
            </p>

            <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-100">
              <button
                type="button"
                onClick={() => setDatasetToDelete(null)}
                className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-xl transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleDeleteConfirm}
                disabled={actionLoading}
                className="px-5 py-2 text-xs font-semibold text-white bg-rose-600 hover:bg-rose-700 rounded-xl transition-colors shadow-xs cursor-pointer"
              >
                {actionLoading ? 'Deleting...' : 'Delete Dataset'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
