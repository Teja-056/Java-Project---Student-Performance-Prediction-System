package studentperformance;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Generates structured, professional academic and AI recommendation PDF reports
 * using Apache PDFBox 3.0.5.
 */
public class PdfReportGenerator {

    public void generateReport(Student student, PerformanceAnalyzer analyzer, AiAnalysisResult aiResult, Path destination) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font fontItalic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                float y = 790;
                float margin = 40;
                float width = PDRectangle.A4.getWidth() - 2 * margin;

                // Title
                stream.beginText();
                stream.setFont(fontBold, 16);
                stream.newLineAtOffset(margin, y);
                stream.showText("STUDENT ACADEMIC PERFORMANCE & AI ADVISORY REPORT");
                stream.endText();
                y -= 20;

                // Subtitle
                stream.beginText();
                stream.setFont(fontItalic, 10);
                stream.newLineAtOffset(margin, y);
                stream.showText("Vasavi College of Engineering - Department of Computer Science & Engineering");
                stream.endText();
                y -= 25;

                // Separator line
                stream.setLineWidth(1.0f);
                stream.moveTo(margin, y);
                stream.lineTo(margin + width, y);
                stream.stroke();
                y -= 20;

                // Student Identity Box
                stream.beginText();
                stream.setFont(fontBold, 11);
                stream.newLineAtOffset(margin, y);
                stream.showText(String.format("Student Name: %s      Roll Number: %s", student.getName(), student.getRollNumber()));
                stream.endText();
                y -= 16;

                stream.beginText();
                stream.setFont(fontRegular, 10);
                stream.newLineAtOffset(margin, y);
                stream.showText(String.format(Locale.ROOT, "CGPA: %.2f | Previous SGPA: %.2f | Overall Internal: %.2f%% | Category: %s",
                        student.getCgpa(), student.getLastSemesterSgpa(), analyzer.currentInternalPercentage(student), analyzer.prediction(student)));
                stream.endText();
                y -= 20;

                // Internal Exam Summary Table
                stream.beginText();
                stream.setFont(fontBold, 11);
                stream.newLineAtOffset(margin, y);
                stream.showText("ACADEMIC PERFORMANCE SUMMARY");
                stream.endText();
                y -= 14;

                double int1 = analyzer.percentage(student, 1);
                double int2 = analyzer.percentage(student, 2);
                Subject strongest = analyzer.strongestSubject(student);
                Subject weakest = analyzer.weakestSubject(student);

                stream.beginText();
                stream.setFont(fontRegular, 9);
                stream.newLineAtOffset(margin + 10, y);
                stream.showText(String.format(Locale.ROOT, "Internal 1 Total: %.2f%%   |   Internal 2 Total: %.2f%%   |   Strongest: %s   |   Weakest: %s",
                        int1, int2, strongest == null ? "N/A" : strongest.getDisplayName(), weakest == null ? "N/A" : weakest.getDisplayName()));
                stream.endText();
                y -= 20;

                // Subject breakdown
                stream.beginText();
                stream.setFont(fontBold, 9);
                stream.newLineAtOffset(margin + 10, y);
                stream.showText("Subject-wise Performance & Trends:");
                stream.endText();
                y -= 13;

                for (Subject s : Subject.values()) {
                    double p1 = analyzer.subjectPercentage(student, 1, s);
                    double p2 = analyzer.subjectPercentage(student, 2, s);
                    double chg = analyzer.subjectChange(student, s);
                    stream.beginText();
                    stream.setFont(fontRegular, 8.5f);
                    stream.newLineAtOffset(margin + 15, y);
                    stream.showText(String.format(Locale.ROOT, "%-6s: Int 1 = %5.1f%%   Int 2 = %5.1f%%   Shift = %+5.1f%%",
                            s.getDisplayName(), p1, p2, chg));
                    stream.endText();
                    y -= 11;
                }
                y -= 12;

                // AI Diagnostics
                if (aiResult != null) {
                    stream.beginText();
                    stream.setFont(fontBold, 11);
                    stream.newLineAtOffset(margin, y);
                    stream.showText("AI PERFORMANCE ANALYSIS & DIAGNOSTICS");
                    stream.endText();
                    y -= 14;

                    stream.beginText();
                    stream.setFont(fontBold, 9);
                    stream.newLineAtOffset(margin + 10, y);
                    stream.showText("Academic Risk Level: " + aiResult.getRiskLevel() + "    |    Suggested Daily Study: " + aiResult.getSuggestedDailyStudyHours() + " hrs/day");
                    stream.endText();
                    y -= 14;

                    y = drawWrappedText(stream, fontRegular, 8.5f, "Summary: " + aiResult.getAcademicSummary(), margin + 10, y, width - 20);
                    y -= 6;

                    // Strengths & Weaknesses
                    if (!aiResult.getStrengths().isEmpty()) {
                        String strList = "Strengths: " + String.join(", ", aiResult.getStrengths());
                        y = drawWrappedText(stream, fontRegular, 8.5f, strList, margin + 10, y, width - 20);
                        y -= 4;
                    }
                    if (!aiResult.getWeaknesses().isEmpty()) {
                        String weakList = "Areas to Strengthen: " + String.join(", ", aiResult.getWeaknesses());
                        y = drawWrappedText(stream, fontRegular, 8.5f, weakList, margin + 10, y, width - 20);
                        y -= 6;
                    }

                    // Key Focus Topics
                    if (!aiResult.getFocusTopics().isEmpty()) {
                        String focusList = "Priority Revision Topics: " + String.join(", ", aiResult.getFocusTopics());
                        y = drawWrappedText(stream, fontBold, 8.5f, focusList, margin + 10, y, width - 20);
                        y -= 8;
                    }

                    // Revision Strategies
                    if (!aiResult.getRevisionStrategies().isEmpty()) {
                        stream.beginText();
                        stream.setFont(fontBold, 9);
                        stream.newLineAtOffset(margin + 10, y);
                        stream.showText("Recommended Learning Strategies:");
                        stream.endText();
                        y -= 12;

                        for (String strat : aiResult.getRevisionStrategies()) {
                            y = drawWrappedText(stream, fontRegular, 8f, "  * " + strat, margin + 15, y, width - 30);
                            y -= 2;
                        }
                    }

                    // Future Forecast
                    if (!aiResult.getEstimatedFuturePerformance().isBlank()) {
                        y -= 4;
                        y = drawWrappedText(stream, fontItalic, 8f, "Forecast: " + aiResult.getEstimatedFuturePerformance(), margin + 10, y, width - 20);
                    }
                }

                // Footer
                float footerY = 35;
                stream.setLineWidth(0.5f);
                stream.moveTo(margin, footerY + 12);
                stream.lineTo(margin + width, footerY + 12);
                stream.stroke();

                stream.beginText();
                stream.setFont(fontItalic, 8);
                stream.newLineAtOffset(margin, footerY);
                stream.showText("Report generated deterministically with AI diagnostic advisory. Confidential academic record.");
                stream.endText();
            }

            document.save(destination.toFile());
        }
    }

    private float drawWrappedText(PDPageContentStream stream, PDType1Font font, float fontSize, String text, float x, float y, float maxLineWidth) throws IOException {
        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
            float lineWidth = (font.getStringWidth(testLine) / 1000f) * fontSize;
            if (lineWidth > maxLineWidth && !currentLine.isEmpty()) {
                stream.beginText();
                stream.setFont(font, fontSize);
                stream.newLineAtOffset(x, y);
                stream.showText(currentLine.toString());
                stream.endText();
                y -= (fontSize + 3);
                currentLine = new StringBuilder(word);
            } else {
                currentLine = new StringBuilder(testLine);
            }
        }
        if (!currentLine.isEmpty()) {
            stream.beginText();
            stream.setFont(font, fontSize);
            stream.newLineAtOffset(x, y);
            stream.showText(currentLine.toString());
            stream.endText();
            y -= (fontSize + 3);
        }
        return y;
    }
}
