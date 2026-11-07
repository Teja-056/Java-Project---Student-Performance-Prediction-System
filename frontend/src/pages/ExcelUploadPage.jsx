import React, { useState } from 'react';
import {
  Upload,
  FileSpreadsheet,
  Download,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  FileText,
  ArrowRight,
  ShieldAlert,
} from 'lucide-react';
import { excelService } from '../api/excelService';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ErrorAlert from '../components/common/ErrorAlert';

export default function ExcelUploadPage() {
  const [file, setFile] = useState(null);
  const [dragActive, setDragActive] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [committing, setCommitting] = useState(false);
  const [error, setError] = useState(null);
  const [uploadResult, setUploadResult] = useState(null);
  const [commitSuccess, setCommitSuccess] = useState(null);

  const handleDrag = (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === 'dragenter' || e.type === 'dragover') {
      setDragActive(true);
    } else if (e.type === 'dragleave') {
      setDragActive(false);
    }
  };

  const handleDrop = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      handleFileSelected(e.dataTransfer.files[0]);
    }
  };

  const handleFileInput = (e) => {
    if (e.target.files && e.target.files[0]) {
      handleFileSelected(e.target.files[0]);
    }
  };

  const handleFileSelected = (selectedFile) => {
    setError(null);
    setUploadResult(null);
    setCommitSuccess(null);

    const name = selectedFile.name.toLowerCase();
    if (!name.endsWith('.xlsx') && !name.endsWith('.xls')) {
      setError('Invalid file format. Please upload an Excel spreadsheet (.xlsx or .xls).');
      return;
    }

    setFile(selectedFile);
  };

  const handlePreviewUpload = async () => {
    if (!file) return;

    try {
      setUploading(true);
      setError(null);
      setCommitSuccess(null);
      const res = await excelService.uploadExcel(file, false);
      setUploadResult(res);
    } catch (err) {
      setError(err.message || 'Failed to process and validate Excel file');
    } finally {
      setUploading(false);
    }
  };

  const handleCommitImport = async () => {
    if (!file) return;

    try {
      setCommitting(true);
      setError(null);
      const res = await excelService.uploadExcel(file, true);
      setCommitSuccess(res.message || `Successfully imported ${res.validCount} student records into MySQL.`);
      setUploadResult(res);
    } catch (err) {
      setError(err.message || 'Failed to commit student records into database');
    } finally {
      setCommitting(false);
    }
  };

  const handleDownloadTemplate = async () => {
    try {
      await excelService.downloadTemplate();
    } catch (err) {
      setError(err.message || 'Failed to download Excel template');
    }
  };

  return (
    <div className="space-y-6 max-w-5xl mx-auto">
      {/* Header & Guidelines */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-6 rounded-2xl border border-slate-200/80 shadow-xs">
        <div>
          <h2 className="text-xl font-bold text-slate-900 tracking-tight">Bulk Student Academic Import</h2>
          <p className="text-xs text-slate-500 mt-1 max-w-xl leading-relaxed">
            Upload institutional Excel sheets containing student profile info, Internal 1, and Internal 2 marks. Overwrite protection ensures existing database records are preserved.
          </p>
        </div>
        <button
          onClick={handleDownloadTemplate}
          className="inline-flex items-center gap-2 px-4 py-2.5 text-xs font-semibold text-indigo-700 bg-indigo-50 hover:bg-indigo-100 rounded-xl transition-colors shrink-0 border border-indigo-200/60 cursor-pointer shadow-xs"
        >
          <Download className="w-4 h-4" />
          Download Template (.xlsx)
        </button>
      </div>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      {commitSuccess && (
        <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-sm flex items-start gap-3">
          <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
          <div>
            <p className="font-bold text-emerald-950">Import Completed Successfully</p>
            <p className="mt-0.5 text-emerald-800">{commitSuccess}</p>
          </div>
        </div>
      )}

      {/* Upload Drag & Drop Zone */}
      <div className="bg-white rounded-2xl border border-slate-200/80 p-6 shadow-xs">
        <div
          onDragEnter={handleDrag}
          onDragOver={handleDrag}
          onDragLeave={handleDrag}
          onDrop={handleDrop}
          className={`border-2 border-dashed rounded-xl p-8 text-center transition-all ${
            dragActive
              ? 'border-indigo-500 bg-indigo-50/50'
              : 'border-slate-200 hover:border-slate-300 bg-slate-50/30'
          }`}
        >
          <div className="w-12 h-12 rounded-2xl bg-indigo-50 text-indigo-600 flex items-center justify-center mx-auto mb-3 shadow-xs">
            <FileSpreadsheet className="w-6 h-6" />
          </div>

          <h3 className="text-sm font-bold text-slate-900">
            {file ? file.name : 'Select or drag your Excel spreadsheet here'}
          </h3>
          <p className="text-xs text-slate-500 mt-1">
            {file ? `${(file.size / 1024).toFixed(1)} KB` : 'Supports Microsoft Excel (.xlsx, .xls) up to 25MB'}
          </p>

          <div className="mt-4 flex items-center justify-center gap-3">
            <label className="inline-flex items-center gap-2 px-4 py-2 text-xs font-semibold text-slate-700 bg-white border border-slate-300 hover:bg-slate-50 rounded-xl transition-colors cursor-pointer shadow-xs">
              <Upload className="w-3.5 h-3.5" />
              Browse Computer
              <input
                type="file"
                accept=".xlsx, .xls"
                onChange={handleFileInput}
                className="hidden"
              />
            </label>

            {file && (
              <button
                onClick={handlePreviewUpload}
                disabled={uploading}
                className="inline-flex items-center gap-2 px-5 py-2 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 rounded-xl transition-colors shadow-xs cursor-pointer"
              >
                {uploading ? 'Validating...' : 'Validate & Preview Sheet'}
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Validation Result / Preview Card */}
      {uploadResult && (
        <div className="bg-white rounded-2xl border border-slate-200/80 p-6 shadow-xs space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-100">
            <div>
              <h3 className="text-base font-bold text-slate-900">Import Validation Summary</h3>
              <p className="text-xs text-slate-500">{uploadResult.filename}</p>
            </div>

            {uploadResult.validCount > 0 && !uploadResult.committed && (
              <button
                onClick={handleCommitImport}
                disabled={committing}
                className="inline-flex items-center gap-2 px-5 py-2 text-xs font-semibold text-white bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 rounded-xl transition-colors shadow-xs cursor-pointer"
              >
                <CheckCircle2 className="w-4 h-4" />
                {committing ? 'Importing...' : `Import ${uploadResult.validCount} Valid Records`}
              </button>
            )}
          </div>

          {/* Counts Grid */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            <div className="p-3.5 bg-slate-50 rounded-xl border border-slate-100">
              <span className="text-xs text-slate-500 font-medium">Total Rows Read</span>
              <p className="text-xl font-bold text-slate-900 mt-1">{uploadResult.totalRows}</p>
            </div>
            <div className="p-3.5 bg-emerald-50 rounded-xl border border-emerald-100">
              <span className="text-xs text-emerald-700 font-medium">Valid Records</span>
              <p className="text-xl font-bold text-emerald-900 mt-1">{uploadResult.validCount}</p>
            </div>
            <div className="p-3.5 bg-rose-50 rounded-xl border border-rose-100">
              <span className="text-xs text-rose-700 font-medium">Invalid / Conflict Rows</span>
              <p className="text-xl font-bold text-rose-900 mt-1">{uploadResult.invalidCount}</p>
            </div>
            <div className="p-3.5 bg-slate-50 rounded-xl border border-slate-100">
              <span className="text-xs text-slate-500 font-medium">Commit Status</span>
              <p className={`text-sm font-bold mt-1 ${uploadResult.committed ? 'text-emerald-600' : 'text-amber-600'}`}>
                {uploadResult.committed ? 'Committed to MySQL' : 'Preview Only'}
              </p>
            </div>
          </div>

          {/* Validation Warnings / Overwrite Prevention Feedback */}
          {uploadResult.validationErrors && uploadResult.validationErrors.length > 0 && (
            <div className="p-4 rounded-xl bg-amber-50/70 border border-amber-200">
              <div className="flex items-center gap-2 text-amber-900 font-bold text-xs mb-2">
                <ShieldAlert className="w-4 h-4 text-amber-600" />
                <span>Detected Validation Issues & Overwrite Protections ({uploadResult.validationErrors.length}):</span>
              </div>
              <div className="max-h-48 overflow-y-auto space-y-1 text-xs text-amber-900 font-mono pr-2">
                {uploadResult.validationErrors.map((err, i) => (
                  <div key={i} className="py-1 border-b border-amber-100/60 last:border-0 leading-tight">
                    {err}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Preview Table of Valid Students */}
          {uploadResult.preview && uploadResult.preview.length > 0 && (
            <div>
              <h4 className="text-xs uppercase font-bold tracking-wider text-slate-500 mb-3">
                Preview of Valid Records ({uploadResult.preview.length})
              </h4>
              <div className="overflow-x-auto max-h-60 border border-slate-200 rounded-xl">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-50 text-slate-500 sticky top-0 border-b border-slate-200">
                    <tr>
                      <th className="py-2.5 px-3">Roll Number</th>
                      <th className="py-2.5 px-3">Name</th>
                      <th className="py-2.5 px-3 text-right">CGPA</th>
                      <th className="py-2.5 px-3 text-right">SGPA</th>
                      <th className="py-2.5 px-3 text-center">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {uploadResult.preview.map((p) => (
                      <tr key={p.rollNumber} className="hover:bg-slate-50/50">
                        <td className="py-2 px-3 font-mono font-semibold text-slate-900">{p.rollNumber}</td>
                        <td className="py-2 px-3 text-slate-800">{p.name}</td>
                        <td className="py-2 px-3 text-right font-bold text-slate-900">{p.cgpa}</td>
                        <td className="py-2 px-3 text-right text-slate-600">{p.lastSemesterSgpa}</td>
                        <td className="py-2 px-3 text-center">
                          <span className="text-[11px] font-semibold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded">
                            Valid
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
