package studentperformance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/** Extracts PDFs as independent syllabus sections so metadata cannot bleed between courses. */
public class SyllabusParser {
    private static final Pattern UNIT = Pattern.compile("(?i)^\\s*UNIT\\s*(?:[-–—:：.]\\s*)?([0-9]+|[ivxlcdm]+)(?:\\s*(?:[-–—:：.]\\s*|\\s+)(.*))?\\s*$");
    private static final Pattern CODE = Pattern.compile("(?i)\\b[A-Z]{1,}\\d{2,}[A-Z0-9]*\\b");
    private static final Pattern SEMESTER = Pattern.compile("(?i)(?:\\b(I|II|III|IV|V|VI|VII|VIII|[1-8])\\s*[- ]?SEMESTER\\b|\\bSEMESTER\\s*[- ]?(I|II|III|IV|V|VI|VII|VIII|[1-8])\\b)");
    private static final Pattern CREDITS = Pattern.compile("(?i)\\bcredits?\\s*[:= -]?\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern MARKS = Pattern.compile("(?i)\\b(?:SEE\\s+)?marks?\\s*[:= -]?\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern OPEN_ELECTIVE = Pattern.compile("(?i)(?:\\bOPEN\\s+ELECTIVE(?:\\s*[-–—]?\\s*[IVX0-9]+)?\\b|\\bOE\\s*[-–—]?\\s*[IVX0-9]+\\b)");
    private static final Pattern PROFESSIONAL_ELECTIVE = Pattern.compile("(?i)(?:\\bPROFESSIONAL\\s+ELECTIVE(?:\\s*[-–—]?\\s*[IVX0-9]+)?\\b|\\bPE\\s*[-–—]?\\s*[IVX0-9]+\\b)");


    public SyllabusDocument parse(Path pdf) throws IOException {
        if (pdf == null || !pdf.toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) throw new IOException("Select a PDF file.");
        if (!Files.isRegularFile(pdf)) throw new IOException("PDF file does not exist: " + pdf);
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            String text = new PDFTextStripper().getText(document);
            if (text == null || text.isBlank()) throw new IOException("No selectable text was found. This appears to be a scanned/image-only PDF; OCR is needed before extraction.");
            return parseText(pdf.getFileName().toString(), text);
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException ex) {
            throw new IOException("The PDF is password-protected and cannot be read.", ex);
        } catch (IOException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("No selectable text")) throw ex;
            throw new IOException("The PDF is malformed or cannot be opened: " + ex.getMessage(), ex);
        }
    }

    /** Package-visible for parser tests built from exact PDFTextStripper-style text. */
    SyllabusDocument parseText(String filename, String text) throws IOException {
        SyllabusDocument result = new SyllabusDocument(filename);
        result.setRawExtractedText(text);
        String[] lines=text.replace('\f','\n').split("\\R",-1);
        List<Integer> starts=new ArrayList<>();
        for(int i=0;i<lines.length;i++)if(isSyllabusSectionHeading(lines[i]))starts.add(i);
        if(!starts.isEmpty()) parseSections(result,lines,starts);
        else parseExplicitLabels(result,lines);
        return result;
    }

    private void parseSections(SyllabusDocument result,String[] lines,List<Integer> starts) throws IOException {
        for(int section=0;section<starts.size();section++) {
            int start=starts.get(section), end=section+1<starts.size()?starts.get(section+1):lines.length;
            Heading heading=findHeading(lines,start,section==0?0:starts.get(section-1));
            if(heading.name().isBlank())continue;
            SyllabusSubject subject=new SyllabusSubject(heading.name());
            subject.setDepartmentCode(findDepartment(lines,start,section==0?0:starts.get(section-1)));
            subject.setSemester(semesterFrom(lines[start]));
            if("OPEN_ELECTIVE".equals(heading.typeLabel())) subject.setCategory(SyllabusCategory.OPEN_ELECTIVE);
            else if("PROFESSIONAL_ELECTIVE".equals(heading.typeLabel())) subject.setCategory(SyllabusCategory.PROFESSIONAL_ELECTIVE);
            else if("SKILL_DEVELOPMENT".equals(heading.typeLabel())) subject.setCategory(SyllabusCategory.SKILL_DEVELOPMENT);
            if(lines[start].toUpperCase(Locale.ROOT).contains("BRIDGE COURSE"))subject.setCategory(SyllabusCategory.BRIDGE_COURSE);

            // Course metadata is scoped strictly to this syllabus heading and before its first unit.
            for(int i=start+1;i<end&&!UNIT.matcher(lines[i]).matches();i++) {
                String raw=lines[i]; String upper=raw.toUpperCase(Locale.ROOT);
                Matcher code=CODE.matcher(raw);
                if(subject.getCourseCode().isBlank()&&upper.contains("COURSE CODE")&&code.find())subject.setCourseCode(code.group());
                Matcher credits=CREDITS.matcher(raw);if(credits.find())subject.setCredits(credits.group(1));
                Matcher marks=MARKS.matcher(raw);if(marks.find()&&subject.getTotalMarks().isBlank())subject.setTotalMarks(marks.group(1));
                if(upper.startsWith("COURSE TYPE")||upper.startsWith("TYPE OF COURSE")||upper.startsWith("CATEGORY")) {
                    if(OPEN_ELECTIVE.matcher(raw).find()) subject.setCategory(SyllabusCategory.OPEN_ELECTIVE);
                    else if(PROFESSIONAL_ELECTIVE.matcher(raw).find()) subject.setCategory(SyllabusCategory.PROFESSIONAL_ELECTIVE);
                }
            }
            subject.classifyFromHeading();
            if(lines[start].toUpperCase(Locale.ROOT).contains("BRIDGE COURSE"))subject.setCategory(SyllabusCategory.BRIDGE_COURSE);
            else if("SKILL_DEVELOPMENT".equals(heading.typeLabel())&&!subject.isOpenElective())
                subject.setCategory(SyllabusCategory.SKILL_DEVELOPMENT);
            else if("PROFESSIONAL_ELECTIVE".equals(heading.typeLabel())&&!subject.isOpenElective())
                subject.setCategory(SyllabusCategory.PROFESSIONAL_ELECTIVE);

            if((subject.getCategory()==SyllabusCategory.THEORY||subject.getCategory()==SyllabusCategory.OTHER)
                    &&!subject.isOpenElective()&&subject.getCourseCode().toUpperCase(Locale.ROOT).matches(".*\\b[A-Z0-9]*PE\\d+.*")) {
                subject.setCategory(SyllabusCategory.PROFESSIONAL_ELECTIVE);
            }

            SyllabusUnit unit=null;SyllabusTopic lastTopic=null;
            for(int i=start+1;i<end;i++) {
                String raw=lines[i];if(raw.isBlank())continue;
                Matcher unitMatch=UNIT.matcher(raw);
                if(unitMatch.matches()) {
                    int number=unitNumber(unitMatch.group(1));
                    String title=unitMatch.group(2)==null||unitMatch.group(2).isBlank()?raw.trim():unitMatch.group(2).trim();
                    unit=new SyllabusUnit(number,title);subject.getUnits().add(unit);lastTopic=null;continue;
                }
                if(unit!=null) {
                    String line=raw.trim();
                    if(isSectionFooter(line)){unit=null;lastTopic=null;continue;}
                    if(line.isEmpty()||isNoiseOrPageHeader(line))continue;
                    if(Character.isWhitespace(raw.charAt(0))&&lastTopic!=null)lastTopic.getSubtopics().add(new SyllabusTopic(line));
                    else {lastTopic=new SyllabusTopic(line);unit.getTopics().add(lastTopic);}
                }
            }
            result.getSubjects().add(subject);
        }
    }

    private record Heading(String name,String typeLabel) { }

    private Heading findHeading(String[] lines,int start,int lowerBound) {
        String name="",typeLabel="";
        for(int i=start-1;i>=lowerBound&&i>=start-14;i--) {
            String value=lines[i].trim();if(value.isEmpty())continue;
            String upper=value.toUpperCase(Locale.ROOT);
            if(upper.contains("DEPARTMENT"))break;
            if(upper.contains("VASAVI COLLEGE")||upper.contains("ACCREDITED")||upper.contains("IBRAHIMBAGH")
                    ||upper.startsWith("SCHEME OF INSTRUCTION")||upper.startsWith("WITH EFFECT"))continue;
            if(OPEN_ELECTIVE.matcher(value).find()) typeLabel="OPEN_ELECTIVE";
            else if(PROFESSIONAL_ELECTIVE.matcher(value).find()) typeLabel="PROFESSIONAL_ELECTIVE";
            else if(upper.contains("SKILL DEVELOPMENT")) typeLabel="SKILL_DEVELOPMENT";

            if(value.startsWith("(") && value.endsWith(")")) continue;

            if(name.isEmpty()&&isPlausibleHeading(value)){
                name=value;
                continue;
            }
            if(!name.isEmpty())break;
        }
        return new Heading(name,typeLabel);
    }

    private boolean isPlausibleHeading(String value) {
        String upper=value.toUpperCase(Locale.ROOT);
        return value.length()>=4&&value.length()<=120&&!value.matches(".*[.!?;:]$")
                &&!upper.startsWith("COURSE CODE")&&!upper.startsWith("L:")&&!upper.startsWith("L :")
                &&!upper.startsWith("CREDITS")&&!upper.contains("COURSE OBJECTIVE")&&!upper.contains("COURSE OUTCOME")
                &&!upper.contains("SYLLABUS")&&!upper.contains("COLLEGE OF ENGINEERING")
                &&!upper.matches(".*\\b(DEPARTMENT|SCHEME OF INSTRUCTION|COURSE CODE)\\b.*");
    }

    private String findDepartment(String[] lines,int start,int lowerBound) {
        for(int i=start-1;i>=lowerBound&&i>=start-35;i--) {
            String department=departmentFromLine(lines[i]);if(department!=null)return department;
        }
        return "UNKNOWN";
    }

    private boolean isSyllabusSectionHeading(String line) {
        return line.toUpperCase(Locale.ROOT).contains("SYLLABUS FOR")&&SEMESTER.matcher(line).find();
    }
    private String semesterFrom(String line) {
        Matcher matcher=SEMESTER.matcher(line);if(!matcher.find())return "UNKNOWN";
        String found = matcher.group(1)!=null?matcher.group(1):matcher.group(2);
        return SyllabusSelector.normalizeSemester(found);
    }
    private boolean isNoiseOrPageHeader(String line) {
        String upper=line.toUpperCase(Locale.ROOT);
        return upper.matches("^\\d+$")
                || upper.matches("^PAGE\\s+\\d+(?:\\s+OF\\s+\\d+)?$")
                || upper.contains("VASAVI COLLEGE OF ENGINEERING")
                || upper.contains("IBRAHIMBAGH")
                || upper.contains("ACCREDITED BY NAAC")
                || upper.startsWith("SCHEME OF INSTRUCTION")
                || upper.startsWith("WITH EFFECT FROM")
                || (upper.startsWith("DEPARTMENT OF") && !upper.contains("UNIT"));
    }
    private String departmentFromLine(String line) {
        String upper=line.toUpperCase(Locale.ROOT);
        if(!upper.contains("DEPARTMENT")&&!upper.matches(".*\\b(CSE|ECE|EEE|IT|ME|CE)\\b.*"))return null;
        if(upper.contains("COMPUTER SCIENCE")||upper.matches(".*\\bCSE\\b.*"))return "CSE";
        if(upper.contains("ELECTRONICS AND COMMUNICATION")||upper.contains("ELECTRONICS & COMMUNICATION")||upper.matches(".*\\bECE\\b.*"))return "ECE";
        if(upper.contains("ELECTRICAL AND ELECTRONICS")||upper.matches(".*\\bEEE\\b.*"))return "EEE";
        if(upper.contains("INFORMATION TECHNOLOGY")||upper.matches(".*\\bIT\\b.*"))return "IT";
        if(upper.contains("MECHANICAL ENGINEERING")||upper.matches(".*\\bME\\b.*"))return "ME";
        if(upper.contains("CIVIL ENGINEERING")||upper.matches(".*\\bCE\\b.*"))return "CE";
        return "OTHER";
    }

    private void parseExplicitLabels(SyllabusDocument result,String[] lines) throws IOException {
        String department="UNKNOWN";SyllabusSubject subject=null;SyllabusUnit unit=null;SyllabusTopic lastTopic=null;
        for(String raw:lines) {
            if(raw.isBlank())continue;
            String dept=departmentFromLine(raw);if(dept!=null){department=dept;continue;}
            Matcher label=Pattern.compile("(?i)^\\s*(?:subject|course|paper)\\s*(?:name|title)?\\s*[:：-]\\s*(.+)$").matcher(raw);
            if(label.matches()) {
                subject=new SyllabusSubject(label.group(1).trim());subject.setDepartmentCode(department);
                subject.classifyFromHeading();result.getSubjects().add(subject);unit=null;lastTopic=null;continue;
            }
            if(subject!=null&&unit==null)updateMetadata(subject,raw);
            Matcher unitMatch=UNIT.matcher(raw);
            if(unitMatch.matches()&&subject!=null) {
                int number=unitNumber(unitMatch.group(1));String title=unitMatch.group(2)==null||unitMatch.group(2).isBlank()?raw.trim():unitMatch.group(2).trim();
                unit=new SyllabusUnit(number,title);subject.getUnits().add(unit);lastTopic=null;continue;
            }
            if(unit!=null) {
                String line=raw.trim();if(isSectionFooter(line)){unit=null;lastTopic=null;continue;}if(line.isEmpty())continue;
                if(Character.isWhitespace(raw.charAt(0))&&lastTopic!=null)lastTopic.getSubtopics().add(new SyllabusTopic(line));
                else {lastTopic=new SyllabusTopic(line);unit.getTopics().add(lastTopic);}
            }
        }
    }

    private void updateMetadata(SyllabusSubject subject,String line) {
        Matcher semester=SEMESTER.matcher(line);
        if(semester.find()) {
            String found = semester.group(1)!=null?semester.group(1):semester.group(2);
            subject.setSemester(SyllabusSelector.normalizeSemester(found));
        }
        Matcher code=CODE.matcher(line);if(subject.getCourseCode().isBlank()&&line.toUpperCase(Locale.ROOT).contains("COURSE CODE")&&code.find())subject.setCourseCode(code.group());
        Matcher credits=CREDITS.matcher(line);if(credits.find())subject.setCredits(credits.group(1));
        Matcher marks=MARKS.matcher(line);if(marks.find()&&subject.getTotalMarks().isBlank())subject.setTotalMarks(marks.group(1));
        if(OPEN_ELECTIVE.matcher(line).find())subject.setOpenElective(true);
        else if(PROFESSIONAL_ELECTIVE.matcher(line).find())subject.setCategory(SyllabusCategory.PROFESSIONAL_ELECTIVE);
        subject.classifyFromHeading();
        if((subject.getCategory()==SyllabusCategory.THEORY||subject.getCategory()==SyllabusCategory.OTHER)
                &&!subject.isOpenElective()&&subject.getCourseCode().toUpperCase(Locale.ROOT).matches(".*\\b[A-Z0-9]*PE\\d+.*")) {
            subject.setCategory(SyllabusCategory.PROFESSIONAL_ELECTIVE);
        }
    }

    private boolean isSectionFooter(String line) {
        String upper=line.toUpperCase(Locale.ROOT);
        return upper.startsWith("LEARNING RESOURCES")||upper.startsWith("SUGGESTED BOOKS")||upper.startsWith("TEXT BOOKS")
                ||upper.startsWith("REFERENCE BOOKS")||upper.startsWith("COURSE OUTCOMES")||upper.startsWith("THE BREAK-UP OF CIE")
                ||upper.startsWith("ONLINE RESOURCES")||upper.startsWith("WEB RESOURCES");
    }
    private int unitNumber(String value) throws IOException {
        try{return Integer.parseInt(value);}catch(NumberFormatException ignored){}
        String roman=value.toUpperCase(Locale.ROOT);int total=0,previous=0;
        for(int i=roman.length()-1;i>=0;i--) {
            int current=switch(roman.charAt(i)){case 'I'->1;case 'V'->5;case 'X'->10;case 'L'->50;case 'C'->100;case 'D'->500;case 'M'->1000;default->0;};
            if(current==0)throw new IOException("Invalid unit number: "+value);total+=current<previous?-current:current;previous=current;
        }
        return total;
    }
}
