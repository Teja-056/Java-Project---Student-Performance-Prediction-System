package studentperformance;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

class SyllabusParserTest {
    private final SyllabusParser parser = new SyllabusParser();

    @Test void groupsSubjectUnitsTopicsAndIndentedSubtopicsWithoutRewording() throws Exception {
        String text = "Subject: Data Structures\nUNIT-I: Introduction\nArrays and linked lists\n  Singly linked list\nUNIT II - Trees\nBinary search trees";
        SyllabusDocument parsed = parser.parseText("sample.pdf", text);
        assertEquals("Data Structures", parsed.getSubjects().get(0).getName());
        assertEquals("Introduction", parsed.getSubjects().get(0).getUnits().get(0).getTitle());
        assertEquals("Arrays and linked lists", parsed.getSubjects().get(0).getUnits().get(0).getTopics().get(0).getText());
        assertEquals("Singly linked list", parsed.getSubjects().get(0).getUnits().get(0).getTopics().get(0).getSubtopics().get(0).getText());
        assertEquals(2, parsed.getSubjects().get(0).getUnits().get(1).getNumber());
    }

    @Test void keepsOriginalTextWhenSubjectDetectionIsUncertain() throws Exception {
        String source = "UNIT I: Intro\nSome topic";
        SyllabusDocument uncertain = parser.parseText("x.pdf", source);
        assertTrue(uncertain.getSubjects().isEmpty());
        assertEquals(source, uncertain.getRawExtractedText());
    }

    @Test void detectsVasaviStandaloneHeadingCodeSemesterAndFiveUnitsAcrossPages() throws Exception {
        String text = "VASAVI COLLEGE OF ENGINEERING (AUTONOMOUS)\n"
                + "DEPARTMENT OF COMPUTER SCIENCE AND ENGINEERING\n"
                + "DATA STRUCTURES\nSYLLABUS FOR B.E. III-SEMESTER\n"
                + "Course Code: UI25PC310CS Credits: 3 CIE Marks: 40\nCourse Objectives and Outcomes\n"
                + "UNIT-I: Introduction to Data Structures\nArrays and linked lists\n"
                + "\fUNIT – II: Stacks and Queues\nStack operations\n"
                + "UNIT-III: Trees\nBinary trees\nUNIT IV: Graphs\nGraph traversal\n"
                + "UNIT 5: Sorting\nSorting algorithms\nLEARNING RESOURCES\nText books";
        SyllabusDocument parsed=parser.parseText("vasavi-r25.pdf",text);
        assertEquals(1,parsed.getSubjects().size());
        SyllabusSubject subject=parsed.getSubjects().get(0);
        assertEquals("DATA STRUCTURES",subject.getName());
        assertEquals("CSE",subject.getDepartmentCode());
        assertEquals("III",subject.getSemester());
        assertEquals("UI25PC310CS",subject.getCourseCode());
        assertEquals(5,subject.getUnits().size());
        assertEquals("Stack operations",subject.getUnits().get(1).getTopics().get(0).getText());
        assertEquals("Sorting algorithms",subject.getUnits().get(4).getTopics().get(0).getText());
    }

    @Test void filtersCseSemesterAndExcludesOtherDepartmentsAndOpenElectives() throws Exception {
        String text="DEPARTMENT OF COMPUTER SCIENCE AND ENGINEERING\nDATA STRUCTURES\nSYLLABUS FOR B.E. III-SEMESTER\nCourse Code: UI25PC310CS\nUNIT-I: Basics\nArrays\n"
                +"OPEN ELECTIVE - AI\nSYLLABUS FOR B.E. III-SEMESTER\nCourse Code: UI25OE301CS\nCourse Type: Open Elective\nUNIT-I: AI\nIntro\n"
                +"DEPARTMENT OF ELECTRONICS AND COMMUNICATION ENGINEERING\nCOMPUTER ARCHITECTURE\nSYLLABUS FOR B.E. III-SEMESTER\nCourse Code: UI25PC312EC\nUNIT-I: Basics\nArchitecture\n"
                +"DEPARTMENT OF COMPUTER SCIENCE AND ENGINEERING\nJAVA PROGRAMMING\nSYLLABUS FOR B.E. IV-SEMESTER\nCourse Code: UI25PC410CS\nUNIT-I: Java\nClasses";
        SyllabusDocument parsed=parser.parseText("multiple-departments.pdf",text);
        List<SyllabusSubject> chosen=SyllabusSelector.relevantSubjects(parsed,"CSE","III");
        assertEquals(1,chosen.size());
        assertEquals("DATA STRUCTURES",chosen.get(0).getName());
        assertTrue(parsed.getSubjects().stream().anyMatch(SyllabusSubject::isOpenElective));
    }

    @Test void keepsComputerArchitectureTheoryAndDoesNotCarryLaterPeCodeOrElectiveStateBackward() throws Exception {
        String text="DEPARTMENT OF COMPUTER SCIENCE & ENGINEERING\n"
                +"COMPUTER ARCHITECTURE\nSYLLABUS FOR B.E. III-SEMESTER\n"
                +"SEE Marks : 60 Course Code:UI25PC330CS\nCredits : 3 CIE Marks : 40\n"
                +"UNIT-I\nComputer components\nUNIT-II\nInstruction codes\nUNIT-III\nCPU\nUNIT-IV\nInput output\nUNIT-V\nMemory hierarchy\nSuggested Books\n"
                +"DEPARTMENT OF COMPUTER SCIENCE & ENGINEERING\nSkill Development Course-IV (Technical Skills-III)\n"
                +"Problem Solving through Data Structures\nSYLLABUS FOR B.E. III-SEMESTER\n"
                +"L:T:P: 1:0:0 Course Code:UI25PE310CS\nCredits:1 CIE Marks:30\nCOURSE OBJECTIVES\n"
                +"UNIT-I\nCompetitive coding\n"
                +"DEPARTMENT OF COMPUTER SCIENCE & ENGINEERING\nARTIFICIAL INTELLIGENCE (Open Elective-I)\n"
                +"SYLLABUS FOR B.E. III-SEMESTER\nCourse Code:U25OE310CS\nUNIT-I\nAI topics";
        SyllabusDocument parsed=parser.parseText("r25-sample.pdf",text);
        SyllabusSubject architecture=parsed.getSubjects().stream().filter(s->s.getName().equals("COMPUTER ARCHITECTURE")).findFirst().orElseThrow();
        SyllabusSubject skills=parsed.getSubjects().stream().filter(s->s.getCourseCode().equals("UI25PE310CS")).findFirst().orElseThrow();
        SyllabusSubject elective=parsed.getSubjects().stream().filter(s->s.getName().startsWith("ARTIFICIAL INTELLIGENCE")).findFirst().orElseThrow();
        assertEquals("UI25PC330CS",architecture.getCourseCode());
        assertEquals(SyllabusCategory.THEORY,architecture.getCategory());
        assertEquals(5,architecture.getUnits().size());
        assertEquals("Problem Solving through Data Structures",skills.getName());
        assertEquals(SyllabusCategory.SKILL_DEVELOPMENT,skills.getCategory());
        assertEquals(SyllabusCategory.OPEN_ELECTIVE,elective.getCategory());
        assertTrue(SyllabusSelector.relevantSubjects(parsed,"CSE","III").contains(architecture));
        assertFalse(SyllabusSelector.relevantSubjects(parsed,"CSE","III").contains(elective));
    }

    @Test void bridgeAndLaboratorySubjectsReceiveDistinctTypes() throws Exception {
        String text="DEPARTMENT OF COMPUTER SCIENCE AND ENGINEERING\nCOMPUTER PROGRAMMING\n"
                +"SYLLABUS FOR BRIDGE COURSE B.E. III-SEMESTER\nCourse Code:UB25ES310CS\nUNIT-I\nC programming\n"
                +"DEPARTMENT OF COMPUTER SCIENCE AND ENGINEERING\nDATA STRUCTURES LAB\nSYLLABUS FOR B.E. III-SEMESTER\nCourse Code:UI25PC311CS";
        SyllabusDocument parsed=parser.parseText("bridge-and-lab.pdf",text);
        assertEquals(SyllabusCategory.BRIDGE_COURSE,parsed.getSubjects().get(0).getCategory());
        assertEquals(SyllabusCategory.LABORATORY,parsed.getSubjects().get(1).getCategory());
    }

    @Test void handlesUnitHeadingsWithAPlainSpaceSeparator() throws Exception {
        SyllabusDocument parsed = parser.parseText("space.pdf", "Course: Operating Systems\nUNIT I PROCESS MANAGEMENT\nProcesses and threads");
        SyllabusUnit unit = parsed.getSubjects().get(0).getUnits().get(0);
        assertEquals(1, unit.getNumber());
        assertEquals("PROCESS MANAGEMENT", unit.getTitle());
        assertEquals("Processes and threads", unit.getTopics().get(0).getText());
    }

    @Test void extractsTextFromPdfAndReportsScannedPdfNeedsOcr() throws Exception {
        Path textPdf = Files.createTempFile("syllabus", ".pdf");
        Path imageLikePdf = Files.createTempFile("scanned-syllabus", ".pdf");
        try {
            try (PDDocument document = new PDDocument()) {
                PDPage page = new PDPage(); document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 750); stream.showText("Subject: Networks"); stream.newLineAtOffset(0, -20);
                    stream.showText("Unit 1: Basics"); stream.newLineAtOffset(0, -20); stream.showText("Network models"); stream.endText();
                }
                document.save(textPdf.toFile());
            }
            try (PDDocument document = new PDDocument()) { document.addPage(new PDPage()); document.save(imageLikePdf.toFile()); }
            assertEquals("Networks", parser.parse(textPdf).getSubjects().get(0).getName());
            IOException ocr = assertThrows(IOException.class, () -> parser.parse(imageLikePdf));
            assertTrue(ocr.getMessage().contains("OCR is needed"));
        } finally { Files.deleteIfExists(textPdf); Files.deleteIfExists(imageLikePdf); }
    }

    @Test void reportsMalformedPdf() throws Exception {
        Path malformed = Files.createTempFile("not-a-pdf", ".pdf");
        try { Files.writeString(malformed, "not a pdf"); assertThrows(IOException.class, () -> parser.parse(malformed)); }
        finally { Files.deleteIfExists(malformed); }
    }

    @Test void distinguishesCorePeAndOeAndFiltersNoiseAcrossPageBreaks() throws Exception {
        String text = "DEPARTMENT OF COMPUTER SCIENCE AND ENGINEERING\n"
                + "COMPUTER ARCHITECTURE\n"
                + "SYLLABUS FOR B.E. III-SEMESTER\n"
                + "Course Code: UI25PE310CS Credits: 3 CIE Marks: 40\n"
                + "UNIT-I: Processor Basics\n"
                + "ALU and Register Organization\n"
                + "\f42\nVASAVI COLLEGE OF ENGINEERING (AUTONOMOUS)\n"
                + "Instruction cycle and sequencing\n"
                + "UNIT II - Memory Hierarchy\n"
                + "Cache and Virtual Memory\n"
                + "DEPARTMENT OF COMPUTER SCIENCE AND ENGINEERING\n"
                + "COMPUTER PROGRAMMING\n"
                + "SYLLABUS FOR B.E. IV-SEMESTER\n"
                + "Course Code: UI25PC451CS Credits: 3 CIE Marks: 40\n"
                + "UNIT 1: C Programming Review\n"
                + "Pointers and Structures\n"
                + "DEPARTMENT OF COMPUTER SCIENCE AND ENGINEERING\n"
                + "ARTIFICIAL INTELLIGENCE AND MACHINE LEARNING\n"
                + "SYLLABUS FOR B.E. IV-SEMESTER\n"
                + "Course Code: UI25PE410CS Credits: 3 CIE Marks: 40\n"
                + "UNIT-1: Search Strategies\n"
                + "Informed and Uninformed Search\n";

        SyllabusDocument parsed = parser.parseText("vasavi-advanced.pdf", text);
        assertEquals(3, parsed.getSubjects().size());

        SyllabusSubject ca = parsed.getSubjects().get(0);
        assertEquals("COMPUTER ARCHITECTURE", ca.getName());
        assertEquals("UI25PE310CS", ca.getCourseCode());
        assertEquals(SyllabusCategory.PROFESSIONAL_ELECTIVE, ca.getCategory());
        assertFalse(ca.isOpenElective());
        // Verify noise was filtered out
        List<String> caTopics = ca.getUnits().get(0).getTopics().stream().map(SyllabusTopic::getText).toList();
        assertTrue(caTopics.contains("ALU and Register Organization"));
        assertTrue(caTopics.contains("Instruction cycle and sequencing"));
        assertFalse(caTopics.contains("42"));
        assertFalse(caTopics.contains("VASAVI COLLEGE OF ENGINEERING (AUTONOMOUS)"));

        SyllabusSubject cp = parsed.getSubjects().get(1);
        assertEquals("COMPUTER PROGRAMMING", cp.getName());
        assertEquals("UI25PC451CS", cp.getCourseCode());
        assertEquals(SyllabusCategory.THEORY, cp.getCategory());
        assertEquals("IV", cp.getSemester());
        assertFalse(cp.isOpenElective());

        SyllabusSubject aiml = parsed.getSubjects().get(2);
        assertEquals("ARTIFICIAL INTELLIGENCE AND MACHINE LEARNING", aiml.getName());
        assertEquals("UI25PE410CS", aiml.getCourseCode());
        assertEquals(SyllabusCategory.PROFESSIONAL_ELECTIVE, aiml.getCategory());
        assertEquals("IV", aiml.getSemester());
        assertFalse(aiml.isOpenElective());

        // Test SyllabusSelector normalized matching
        List<SyllabusSubject> sem3Eligible = SyllabusSelector.relevantSubjects(parsed, "CSE", "3");
        assertEquals(1, sem3Eligible.size());
        assertEquals("COMPUTER ARCHITECTURE", sem3Eligible.get(0).getName());

        List<SyllabusSubject> sem4Eligible = SyllabusSelector.relevantSubjects(parsed, "CSE", "IV");
        assertEquals(2, sem4Eligible.size());
    }

    @Test void generateSampleSyllabusPdf() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 750);
                String[] lines = {
                        "VASAVI COLLEGE OF ENGINEERING (AUTONOMOUS)",
                        "DEPARTMENT OF COMPUTER SCIENCE AND ENGINEERING",
                        "DATA STRUCTURES",
                        "SYLLABUS FOR B.E. III-SEMESTER",
                        "Course Code: UI25PC310CS Credits: 3 CIE Marks: 40",
                        "Course Objectives and Outcomes",
                        "UNIT-I: Introduction to Data Structures",
                        "Arrays and linked lists",
                        "  Singly linked list",
                        "UNIT-II: Stacks and Queues",
                        "Stack operations and applications",
                        "  Infix to postfix conversion",
                        "UNIT-III: Trees",
                        "Binary trees and binary search trees",
                        "  Tree traversals",
                        "UNIT-IV: Graphs",
                        "Graph representations and traversals",
                        "  Breadth First Search and Depth First Search",
                        "UNIT-V: Sorting and Hashing",
                        "Sorting algorithms and hash tables",
                        "  Quicksort, Mergesort, Open addressing",
                        "LEARNING RESOURCES",
                        "Text books"
                };
                for (String l : lines) {
                    stream.showText(l);
                    stream.newLineAtOffset(0, -18);
                }
                stream.endText();
            }
            document.save("vasavi_cse_sem3_data_structures_syllabus.pdf");
        }
    }
}
