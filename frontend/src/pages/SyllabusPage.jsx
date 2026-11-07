import React, { useState, useEffect } from 'react';
import {
  BookOpen,
  Upload,
  CheckCircle2,
  Clock,
  Layers,
  ChevronDown,
  ChevronUp,
  FileText,
  AlertCircle,
  FileCheck,
} from 'lucide-react';
import { syllabusService } from '../api/syllabusService';
import Badge from '../components/common/Badge';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ErrorAlert from '../components/common/ErrorAlert';
import EmptyState from '../components/common/EmptyState';

export default function SyllabusPage() {
  const [syllabuses, setSyllabuses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [uploadFile, setUploadFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState(null);
  const [uploadSuccess, setUploadSuccess] = useState(null);

  const [expandedDocId, setExpandedDocId] = useState(null);
  const [approvingId, setApprovingId] = useState(null);

  useEffect(() => {
    loadSyllabuses();
  }, []);

  const loadSyllabuses = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await syllabusService.getAllSyllabuses();
      setSyllabuses(data || []);
      if (data && data.length > 0 && !expandedDocId) {
        setExpandedDocId(data[0].documentId || data[0].id);
      }
    } catch (err) {
      setError(err.message || 'Failed to load syllabus documents');
    } finally {
      setLoading(false);
    }
  };

  const handleUpload = async (e) => {
    e.preventDefault();
    if (!uploadFile) return;

    try {
      setUploading(true);
      setUploadError(null);
      setUploadSuccess(null);
      const doc = await syllabusService.uploadSyllabus(uploadFile, true);
      setUploadSuccess(`Successfully uploaded and parsed "${doc.filename}". Found ${doc.subjectCount} subject(s).`);
      setUploadFile(null);
      await loadSyllabuses();
      setExpandedDocId(doc.documentId || doc.id);
    } catch (err) {
      setUploadError(err.message || 'Failed to upload and parse syllabus PDF');
    } finally {
      setUploading(false);
    }
  };

  const handleApprove = async (id) => {
    try {
      setApprovingId(id);
      await syllabusService.approveSyllabus(id);
      await loadSyllabuses();
    } catch (err) {
      setError(err.message || `Failed to approve syllabus ${id}`);
    } finally {
      setApprovingId(null);
    }
  };

  return (
    <div className="space-y-6 max-w-5xl mx-auto">
      {/* Header Banner */}
      <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-xs">
        <h2 className="text-xl font-bold text-slate-900 tracking-tight">Syllabus Curriculum Management</h2>
        <p className="text-xs text-slate-500 mt-1 max-w-2xl leading-relaxed">
          Upload and review college syllabus PDFs. Parsed courses, units, and topics are stored in MySQL to ground Google Gemini's personalized student recommendations.
        </p>
      </div>

      <ErrorAlert message={error} retryAction={loadSyllabuses} />

      {uploadSuccess && (
        <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-sm flex items-start gap-3">
          <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
          <div>
            <p className="font-bold text-emerald-950">Syllabus Upload Succeeded</p>
            <p className="mt-0.5 text-emerald-800">{uploadSuccess}</p>
          </div>
        </div>
      )}

      {/* Upload New Syllabus PDF Box */}
      <div className="bg-white rounded-2xl border border-slate-200/80 p-6 shadow-xs">
        <h3 className="text-sm font-bold text-slate-900 mb-3 flex items-center gap-2">
          <Upload className="w-4 h-4 text-indigo-600" /> Upload College Syllabus Document
        </h3>

        <form onSubmit={handleUpload} className="flex flex-col sm:flex-row items-center gap-3">
          <div className="relative flex-1 w-full">
            <input
              type="file"
              accept=".pdf"
              onChange={(e) => {
                if (e.target.files && e.target.files[0]) {
                  setUploadFile(e.target.files[0]);
                  setUploadError(null);
                }
              }}
              className="w-full text-xs text-slate-500 file:mr-4 file:py-2 file:px-4 file:rounded-xl file:border-0 file:text-xs file:font-semibold file:bg-indigo-50 file:text-indigo-700 hover:file:bg-indigo-100 cursor-pointer"
            />
          </div>

          <button
            type="submit"
            disabled={uploading || !uploadFile}
            className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-5 py-2 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 rounded-xl transition-colors shadow-xs cursor-pointer"
          >
            <BookOpen className="w-4 h-4" />
            {uploading ? 'Extracting Text...' : 'Parse & Ground PDF'}
          </button>
        </form>

        {uploadError && (
          <div className="mt-3 text-xs text-rose-600 flex items-center gap-1.5">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{uploadError}</span>
          </div>
        )}
      </div>

      {/* Documents List & Topic Inspector */}
      {loading ? (
        <LoadingSpinner message="Loading syllabus database records..." />
      ) : syllabuses.length === 0 ? (
        <EmptyState
          title="No syllabus documents uploaded yet"
          description="Upload an official course PDF above to enable AI syllabus grounding."
          icon={BookOpen}
        />
      ) : (
        <div className="space-y-4">
          <h3 className="text-sm font-bold text-slate-900">
            Registered Curriculum Documents ({syllabuses.length})
          </h3>

          {syllabuses.map((doc) => {
            const docId = doc.documentId || doc.id;
            const isExpanded = expandedDocId === docId;
            const isApproved = doc.status === 'APPROVED';

            return (
              <div
                key={docId}
                className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden transition-all"
              >
                <div className="p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-slate-50/50">
                  <div className="flex items-start gap-3">
                    <div className="w-10 h-10 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center shrink-0 mt-0.5">
                      <FileCheck className="w-5 h-5" />
                    </div>
                    <div>
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="font-bold text-sm text-slate-900">{doc.filename}</span>
                        <Badge status={doc.status} size="small" />
                      </div>
                      <p className="text-xs text-slate-500 mt-1">
                        Document ID: #{docId} • {doc.subjectCount || doc.subjects?.length || 0} parsed course(s)
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center gap-3">
                    {!isApproved && (
                      <button
                        onClick={() => handleApprove(docId)}
                        disabled={approvingId === docId}
                        className="px-3.5 py-1.5 text-xs font-semibold text-emerald-700 bg-emerald-50 hover:bg-emerald-100 rounded-xl transition-colors cursor-pointer border border-emerald-200"
                      >
                        {approvingId === docId ? 'Approving...' : 'Approve Document'}
                      </button>
                    )}
                    <button
                      onClick={() => setExpandedDocId(isExpanded ? null : docId)}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-slate-700 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 transition-colors shadow-xs cursor-pointer"
                    >
                      {isExpanded ? (
                        <>
                          Hide Topics <ChevronUp className="w-3.5 h-3.5" />
                        </>
                      ) : (
                        <>
                          Inspect Units <ChevronDown className="w-3.5 h-3.5" />
                        </>
                      )}
                    </button>
                  </div>
                </div>

                {/* Expanded Course & Unit Tree */}
                {isExpanded && (
                  <div className="p-5 border-t border-slate-100 space-y-6">
                    {doc.subjects?.map((subj, sIdx) => (
                      <div key={sIdx} className="space-y-4">
                        <div className="flex flex-wrap items-center justify-between gap-2 pb-3 border-b border-slate-100">
                          <div>
                            <h4 className="font-bold text-sm text-slate-900">{subj.subjectName}</h4>
                            <span className="text-xs text-slate-500 font-mono">
                              Code: {subj.courseCode || 'N/A'} • Credits: {subj.credits || '3'} • CIE: {subj.totalMarks || '40'}
                            </span>
                          </div>
                          <span className="text-xs font-semibold px-2 py-0.5 rounded bg-slate-100 text-slate-600">
                            {subj.category} • Sem {subj.semester}
                          </span>
                        </div>

                        {/* Units Grid */}
                        <div className="space-y-3">
                          {subj.units?.map((u) => (
                            <div key={u.unitNumber} className="p-3.5 rounded-xl bg-slate-50 border border-slate-100">
                              <span className="font-bold text-xs text-indigo-700">
                                UNIT {u.unitNumber}: {u.unitTitle}
                              </span>
                              <div className="mt-2 flex flex-wrap gap-1.5">
                                {u.topics?.map((topic, tIdx) => (
                                  <span
                                    key={tIdx}
                                    className="px-2 py-0.5 rounded-md text-[11px] bg-white border border-slate-200 text-slate-700"
                                  >
                                    {topic}
                                  </span>
                                ))}
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
