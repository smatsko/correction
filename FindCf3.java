//
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FindCf3 {

    // =============================================================
    // FAIL in:X out:Y
    // =============================================================

    private static final Pattern FAIL_PATTERN =
            Pattern.compile("FAIL in:(\\d+) +out:(\\d+)");


    // =============================================================
    // TYPE 1
    //
    // name value1 value2 value3 value4 value5 indicator value6
    //
    // Example:
    //
    // \cf3 Length-X 127.889 128.000 -0.100 +0.100
    //               -0.111 <--+--- -0.011\par
    // =============================================================

    private static final Pattern CF3_TYPE1_PATTERN = Pattern.compile(
            "^\\\\cf3\\s+" +
            "(.+?)" +
            "(\\s+)([+-]?\\d+\\.\\d+)" +
            "(\\s+)([+-]?\\d+\\.\\d+)" +
            "(\\s+)([+-]?\\d+\\.\\d+)" +
            "(\\s+)([+-]?\\d+\\.\\d+)" +
            "(\\s+)([+-]?\\d+\\.\\d+)" +
            "(\\s+)([+*<>-]+)" +             // indicator
            "(\\s+)([+-]?\\d+\\.\\d+)" +
            "(\\\\par.*)$"
    );


    // =============================================================
    // TYPE 2
    //
    // name value1 value2,DAT(...) indicator value3
    //
    // Example:
    //
    // \cf3 Circ. Runout 0.159 0.100,DAT(A) +--> 0.059\par
    // =============================================================

    private static final Pattern CF3_TYPE2_PATTERN = Pattern.compile(
            "^\\\\cf3\\s+" +
            "(.+?)" +
            "(\\s+)([+-]?\\d+\\.\\d+)" +
            "(\\s+)([+-]?\\d+\\.\\d+)" +
            ",(DAT\\([^)]*\\))" +
            "(\\s+)([+*<>-]+)" +             // indicator
            "(\\s+)([+-]?\\d+\\.\\d+)" +
            "(\\\\par.*)$"
    );


    // =============================================================
    // TYPE 3
    //
    // name value1 value2 indicator value3
    //
    // Example:
    //
    // \cf3 Cylindricity 0.081 0.050 +--> 0.031\par
    // =============================================================

    private static final Pattern CF3_TYPE3_PATTERN = Pattern.compile(
            "^\\\\cf3\\s+" +
            "(.+?)" +
            "(\\s+)([+-]?\\d+\\.\\d+)" +
            "(\\s+)([+-]?\\d+\\.\\d+)" +
            "(\\s+)([+*<>-]+)" +             // indicator
            "(\\s+)([+-]?\\d+\\.\\d+)" +
            "(\\\\par.*)$"
    );


    // =============================================================
    // TYPE 4
    //
    // name value1 value2 value3 indicator value4
    //
    // Example:
    //
    // \cf3 Angularity 0.107 0.100 0.100 +--> 0.007\par
    // =============================================================

    private static final Pattern CF3_TYPE4_PATTERN = Pattern.compile(
            "^\\\\cf3\\s+" +
            "(.+?)" +
            "(\\s+)([+-]?\\d+\\.\\d+)" +
            "(\\s+)([+-]?\\d+\\.\\d+)" +
            "(\\s+)([+-]?\\d+\\.\\d+)" +
            "(\\s+)([+*<>-]+)" +             // indicator
            "(\\s+)([+-]?\\d+\\.\\d+)" +
            "(\\\\par.*)$"
    );


    // =============================================================
    // MAIN
    // =============================================================

    public static void main(String[] args) {

        if (args.length == 0) {
            System.out.println(
                    "Usage: java FindCf3.java \"*.rtf\""
            );
            return;
        }

        String mask = args[0];

        Path directory =
                Paths.get(".");


        // =========================================================
        // BACKUP DIRECTORY
        // =========================================================

        String timestamp =
                LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern(
                                "yyyyMMdd_HHmmss"
                        )
                );

        Path backupDirectory =
                directory.resolve(
                        "backup_" + timestamp
                );


        try {

            Files.createDirectories(
                    backupDirectory
            );

            System.out.println(
                    "Backup directory: "
                    + backupDirectory.toAbsolutePath()
            );

        } catch (IOException e) {

            System.err.println(
                    "Cannot create backup directory: "
                    + e.getMessage()
            );

            return;
        }


        // =========================================================
        // FILES
        // =========================================================

        try (DirectoryStream<Path> files =
                     Files.newDirectoryStream(
                             directory,
                             mask
                     )) {

            for (Path file : files) {

                if (!Files.isRegularFile(file)) {
                    continue;
                }

                // -------------------------------------------------
                // Backup before modification
                // -------------------------------------------------

                if (!backupFile(
                        file,
                        backupDirectory
                )) {

                    System.err.println(
                            "File skipped: "
                            + file.getFileName()
                    );

                    continue;
                }

                processFile(file);
            }

        } catch (IOException e) {

            System.err.println(
                    "Error: "
                    + e.getMessage()
            );
        }
    }


    // =============================================================
    // BACKUP
    // =============================================================

    private static boolean backupFile(
            Path file,
            Path backupDirectory) {

        Path target =
                backupDirectory.resolve(
                        file.getFileName()
                );

        try {

            Files.copy(
                    file,
                    target,
                    StandardCopyOption.COPY_ATTRIBUTES,
                    StandardCopyOption.REPLACE_EXISTING
            );

            System.out.println(
                    "Backup: "
                    + file.getFileName()
            );

            return true;

        } catch (IOException e) {

            System.err.println(
                    "Cannot backup "
                    + file.getFileName()
                    + ": "
                    + e.getMessage()
            );

            return false;
        }
    }


    // =============================================================
    // PROCESS FILE
    // =============================================================

    private static void processFile(
            Path file) {

        try {

            FileTime originalTime =
                    Files.getLastModifiedTime(file);


            String content =
                    Files.readString(
                            file,
                            StandardCharsets.UTF_8
                    );


            String originalContent =
                    content;


            // CF3 -> CF2
            content =
                    processCf3Lines(
                            content,
                            file
                    );


            // FAIL -> PASS
            content =
                    processFail(
                            content,
                            file
                    );


            // =====================================================
            // WRITE ONLY IF SOMETHING CHANGED
            // =====================================================

            if (!content.equals(originalContent)) {

                Files.writeString(
                        file,
                        content,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING
                );


                // Restore original modification time
                Files.setLastModifiedTime(
                        file,
                        originalTime
                );


                System.out.println(
                        "Modified: "
                        + file.getFileName()
                );
            }

        } catch (Exception e) {

            System.err.println(
                    "Cannot process "
                    + file
                    + ": "
                    + e.getMessage()
            );
        }
    }


    // =============================================================
    // PROCESS ALL PHYSICAL LINES STARTING WITH \cf3
    // =============================================================

    private static String processCf3Lines(
            String content,
            Path file) {

        Pattern linePattern =
                Pattern.compile(
                        "(?m)^\\\\cf3[^\\r\\n]*"
                );


        Matcher matcher =
                linePattern.matcher(content);


        StringBuffer result =
                new StringBuffer();


        while (matcher.find()) {

            String oldLine =
                    matcher.group();


            String newLine =
                    processCf3Line(
                            oldLine,
                            file
                    );


            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(
                            newLine
                    )
            );
        }


        matcher.appendTail(result);

        return result.toString();
    }


    // =============================================================
    // DETECT TYPE
    //
    // IMPORTANT:
    //
    // TYPE 4 is checked BEFORE TYPE 3.
    // =============================================================

    private static String processCf3Line(
            String line,
            Path file) {


        // ---------------------------------------------------------
        // TYPE 1
        // ---------------------------------------------------------

        Matcher type1 =
                CF3_TYPE1_PATTERN.matcher(line);

        if (type1.matches()) {

            return processType1(
                    type1,
                    line,
                    file
            );
        }


        // ---------------------------------------------------------
        // TYPE 2
        // ---------------------------------------------------------

        Matcher type2 =
                CF3_TYPE2_PATTERN.matcher(line);

        if (type2.matches()) {

            return processType2(
                    type2,
                    line,
                    file
            );
        }


        // ---------------------------------------------------------
        // TYPE 4
        //
        // Must be checked BEFORE TYPE 3.
        // ---------------------------------------------------------

        Matcher type4 =
                CF3_TYPE4_PATTERN.matcher(line);

        if (type4.matches()) {

            return processType4(
                    type4,
                    line,
                    file
            );
        }


        // ---------------------------------------------------------
        // TYPE 3
        // ---------------------------------------------------------

        Matcher type3 =
                CF3_TYPE3_PATTERN.matcher(line);

        if (type3.matches()) {

            return processType3(
                    type3,
                    line,
                    file
            );
        }


        // ---------------------------------------------------------
        // Unknown CF3 structure
        // ---------------------------------------------------------

        System.out.println();

        System.out.println(
                file.getFileName()
                + ": CF3 skipped:"
        );

        System.out.println(line);

        return line;
    }


    // =============================================================
    // TYPE 1
    // =============================================================

    private static String processType1(
            Matcher m,
            String line,
            Path file) {

        String name =
                m.group(1);


        double value1 =
                Double.parseDouble(
                        m.group(3)
                );

        double value2 =
                Double.parseDouble(
                        m.group(5)
                );

        double value3 =
                Double.parseDouble(
                        m.group(7)
                );

        double value4 =
                Double.parseDouble(
                        m.group(9)
                );

        double value5 =
                Double.parseDouble(
                        m.group(11)
                );

        String indicator =
                m.group(13);


        // Final excess value.
        // It is deliberately not written back.
        Double.parseDouble(
                m.group(15)
        );


        // =========================================================
        // FIELD WIDTHS
        // =========================================================

        int width1 =
                m.group(2).length()
                + m.group(3).length();

        int width2 =
                m.group(4).length()
                + m.group(5).length();

        int width3 =
                m.group(6).length()
                + m.group(7).length();

        int width4 =
                m.group(8).length()
                + m.group(9).length();

        int width5 =
                m.group(10).length()
                + m.group(11).length();

        int indicatorWidth =
                m.group(12).length()
                + m.group(13).length();


        // =========================================================
        // CALCULATION
        // =========================================================

        int indicatorLength =
                indicator.length();


        if (indicatorLength == 0) {

            System.err.println(
                    file.getFileName()
                    + ": empty TYPE 1 indicator"
            );

            return line;
        }


        double step =
                Math.abs(
                        value3 - value4
                )
                / indicatorLength;


        step =
                round3(step);


        double randomOffset =
                Math.random()
                * (step / 2.0);


        double lowerLimit =
                value2 + value3;


        double upperLimit =
                value2 + value4;


        if (value1 < lowerLimit) {

            value1 =
                    lowerLimit
                    + randomOffset;

        } else {

            value1 =
                    upperLimit
                    - randomOffset;
        }


        value1 =
                round3(value1);


        value5 =
                round3(
                        value1 - value2
                );


        indicator =
                cleanIndicator(
                        indicator
                );


        // =========================================================
        // BUILD RESULT
        // =========================================================

        StringBuilder result =
                new StringBuilder();


        result.append("\\cf2 ");
        result.append(name);


        result.append(
                formatNumber(
                        value1,
                        width1,
                        false
                )
        );


        result.append(
                formatNumber(
                        value2,
                        width2,
                        false
                )
        );


        result.append(
                formatNumber(
                        value3,
                        width3,
                        false
                )
        );


        result.append(
                formatNumber(
                        value4,
                        width4,
                        true
                )
        );


        result.append(
                formatNumber(
                        value5,
                        width5,
                        false
                )
        );


        appendIndicator(
                result,
                indicator,
                indicatorWidth
        );


        // Final value and spaces before it are removed.
        // Keep \par.
        result.append(
                m.group(16)
        );


        String newLine =
                result.toString();


        printChange(
                file,
                "TYPE 1",
                line,
                newLine
        );


        return newLine;
    }


    // =============================================================
    // TYPE 2
    //
    // value1 value2,DAT(...) indicator excess
    // =============================================================

    private static String processType2(
            Matcher m,
            String line,
            Path file) {

        String name =
                m.group(1);


        double value1 =
                Double.parseDouble(
                        m.group(3)
                );


        double value2 =
                Double.parseDouble(
                        m.group(5)
                );


        String datum =
                m.group(6);


        String indicator =
                m.group(8);


        // Final excess value.
        Double.parseDouble(
                m.group(10)
        );


        // =========================================================
        // WIDTHS
        // =========================================================

        int width1 =
                m.group(2).length()
                + m.group(3).length();


        int width2 =
                m.group(4).length()
                + m.group(5).length();


        int indicatorWidth =
                m.group(7).length()
                + m.group(8).length();


        // =========================================================
        // CALCULATION
        // =========================================================

        int indicatorLength =
                indicator.length();


        if (indicatorLength == 0) {

            System.err.println(
                    file.getFileName()
                    + ": empty TYPE 2 indicator"
            );

            return line;
        }


        double step =
                value2
                / indicatorLength;


        step =
                round3(step);


        double randomOffset =
                Math.random()
                * (step / 2.0);


        value1 =
                value2
                - randomOffset;


        value1 =
                round3(value1);


        indicator =
                cleanIndicator(
                        indicator
                );


        // =========================================================
        // BUILD RESULT
        // =========================================================

        StringBuilder result =
                new StringBuilder();


        result.append("\\cf2 ");
        result.append(name);


        result.append(
                formatNumber(
                        value1,
                        width1,
                        false
                )
        );


        result.append(
                formatNumber(
                        value2,
                        width2,
                        false
                )
        );


        result.append(",");
        result.append(datum);


        appendIndicator(
                result,
                indicator,
                indicatorWidth
        );


        // Remove final excess value and preceding spaces.
        // Keep \par.
        result.append(
                m.group(11)
        );


        String newLine =
                result.toString();


        printChange(
                file,
                "TYPE 2",
                line,
                newLine
        );


        return newLine;
    }


    // =============================================================
    // TYPE 3
    //
    // name value1 value2 indicator excess
    //
    // Example:
    //
    // \cf3 Cylindricity 0.081 0.050 +--> 0.031\par
    // =============================================================

    private static String processType3(
            Matcher m,
            String line,
            Path file) {

        String name =
                m.group(1);


        double value1 =
                Double.parseDouble(
                        m.group(3)
                );


        double value2 =
                Double.parseDouble(
                        m.group(5)
                );


        String indicator =
                m.group(7);


        // Final excess value.
        Double.parseDouble(
                m.group(9)
        );


        // =========================================================
        // WIDTHS
        // =========================================================

        int width1 =
                m.group(2).length()
                + m.group(3).length();


        int width2 =
                m.group(4).length()
                + m.group(5).length();


        int indicatorWidth =
                m.group(6).length()
                + m.group(7).length();


        // =========================================================
        // CALCULATION
        // =========================================================

        int indicatorLength =
                indicator.length();


        if (indicatorLength == 0) {

            System.err.println(
                    file.getFileName()
                    + ": empty TYPE 3 indicator"
            );

            return line;
        }


        double step =
                value2
                / indicatorLength;


        step =
                round3(step);


        double randomOffset =
                Math.random()
                * (step / 2.0);


        value1 =
                value2
                - randomOffset;


        value1 =
                round3(value1);


        indicator =
                cleanIndicator(
                        indicator
                );


        // =========================================================
        // BUILD RESULT
        // =========================================================

        StringBuilder result =
                new StringBuilder();


        result.append("\\cf2 ");
        result.append(name);


        result.append(
                formatNumber(
                        value1,
                        width1,
                        false
                )
        );


        result.append(
                formatNumber(
                        value2,
                        width2,
                        false
                )
        );


        appendIndicator(
                result,
                indicator,
                indicatorWidth
        );


        // Remove final excess value and preceding spaces.
        // Keep \par.
        result.append(
                m.group(10)
        );


        String newLine =
                result.toString();


        printChange(
                file,
                "TYPE 3",
                line,
                newLine
        );


        return newLine;
    }


    // =============================================================
    // TYPE 4
    //
    // name value1 value2 value3 indicator excess
    //
    // Example:
    //
    // \cf3 Angularity     0.107
    //                     0.100
    //                     0.100
    //                     +-->
    //                     0.007\par
    // =============================================================

    private static String processType4(
            Matcher m,
            String line,
            Path file) {

        String name =
                m.group(1);


        // Actual measured value
        double value1 =
                Double.parseDouble(
                        m.group(3)
                );


        // Additional value
        double value2 =
                Double.parseDouble(
                        m.group(5)
                );


        // Maximum allowed value
        double value3 =
                Double.parseDouble(
                        m.group(7)
                );


        String indicator =
                m.group(9);


        // Final excess value.
        // Read for validation but do not write it back.
        Double.parseDouble(
                m.group(11)
        );


        // =========================================================
        // WIDTHS
        // =========================================================

        int width1 =
                m.group(2).length()
                + m.group(3).length();


        int width2 =
                m.group(4).length()
                + m.group(5).length();


        int width3 =
                m.group(6).length()
                + m.group(7).length();


        int indicatorWidth =
                m.group(8).length()
                + m.group(9).length();


        // =========================================================
        // CALCULATION
        // =========================================================

        int indicatorLength =
                indicator.length();


        if (indicatorLength == 0) {

            System.err.println(
                    file.getFileName()
                    + ": empty TYPE 4 indicator"
            );

            return line;
        }


        // Use value3 as upper permissible value.
        double step =
                value3
                / indicatorLength;


        step =
                round3(step);


        double randomOffset =
                Math.random()
                * (step / 2.0);


        // Put measured value slightly below the limit.
        value1 =
                value3
                - randomOffset;


        value1 =
                round3(value1);


        // Replace < and > with *
        indicator =
                cleanIndicator(
                        indicator
                );


        // =========================================================
        // BUILD RESULT
        // =========================================================

        StringBuilder result =
                new StringBuilder();


        result.append("\\cf2 ");
        result.append(name);


        // VALUE 1
        result.append(
                formatNumber(
                        value1,
                        width1,
                        false
                )
        );


        // VALUE 2
        result.append(
                formatNumber(
                        value2,
                        width2,
                        false
                )
        );


        // VALUE 3
        result.append(
                formatNumber(
                        value3,
                        width3,
                        false
                )
        );


        // INDICATOR
        appendIndicator(
                result,
                indicator,
                indicatorWidth
        );


        // =========================================================
        // Do NOT append:
        //
        // m.group(10) = spaces before excess
        // m.group(11) = excess value
        //
        // Append only \par...
        // =========================================================

        result.append(
                m.group(12)
        );


        String newLine =
                result.toString();


        printChange(
                file,
                "TYPE 4",
                line,
                newLine
        );


        return newLine;
    }


    // =============================================================
    // CLEAN INDICATOR
    //
    // < -> *
    // > -> *
    // =============================================================

    private static String cleanIndicator(
            String indicator) {

        return indicator
                .replace('<', '*')
                .replace('>', '*');
    }


    // =============================================================
    // APPEND INDICATOR
    //
    // Preserve original right position.
    // =============================================================

    private static void appendIndicator(
            StringBuilder result,
            String indicator,
            int indicatorWidth) {

        int spaces =
                indicatorWidth
                - indicator.length();


        result.append(
                " ".repeat(
                        Math.max(
                                0,
                                spaces
                        )
                )
        );


        result.append(
                indicator
        );
    }


    // =============================================================
    // PRINT CHANGE
    // =============================================================

    private static void printChange(
            Path file,
            String type,
            String oldLine,
            String newLine) {

        System.out.println();


        System.out.println(
                file.getFileName()
                + " "
                + type
        );


        System.out.println(
                "OLD: "
                + oldLine
        );


        System.out.println(
                "NEW: "
                + newLine
        );
    }


    // =============================================================
    // ROUND TO 3 DECIMAL PLACES
    // =============================================================

    private static double round3(
            double value) {

        return Math.round(
                value * 1000.0
        ) / 1000.0;
    }


    // =============================================================
    // FORMAT NUMBER
    //
    // 3 decimal places.
    // Preserve right alignment.
    // =============================================================

    private static String formatNumber(
            double value,
            int width,
            boolean forcePlus) {

        String number;


        if (forcePlus && value >= 0) {

            number =
                    String.format(
                            Locale.US,
                            "+%.3f",
                            value
                    );

        } else {

            number =
                    String.format(
                            Locale.US,
                            "%.3f",
                            value
                    );
        }


        if (number.length() > width) {

            throw new IllegalArgumentException(
                    "Number does not fit field: "
                    + number
                    + ", width="
                    + width
            );
        }


        return " ".repeat(
                width - number.length()
        ) + number;
    }


    // =============================================================
    // FAIL -> PASS
    //
    // FAIL in:X   out:Y
    //
    // ->
    //
    // PASS in:X+Y out:0
    //
    // Preserve total text length.
    // =============================================================

    private static String processFail(
            String content,
            Path file) {

        Matcher matcher =
                FAIL_PATTERN.matcher(content);


        StringBuilder result =
                new StringBuilder();


        int lastPosition =
                0;


        while (matcher.find()) {

            result.append(
                    content,
                    lastPosition,
                    matcher.start()
            );


            int in =
                    Integer.parseInt(
                            matcher.group(1)
                    );


            int out =
                    Integer.parseInt(
                            matcher.group(2)
                    );


            int sum =
                    in + out;


            String oldText =
                    matcher.group();


            String newText =
                    "PASS in:"
                    + sum
                    + " out:0";


            int difference =
                    newText.length()
                    - oldText.length();


            // =====================================================
            // New text shorter or same length
            // =====================================================

            if (difference <= 0) {

                result.append(
                        newText
                );


                result.append(
                        " ".repeat(
                                -difference
                        )
                );


                lastPosition =
                        matcher.end();


                System.out.println(
                        file.getFileName()
                        + ": "
                        + oldText
                        + " -> "
                        + newText
                );


                continue;
            }


            // =====================================================
            // New text is longer.
            // Count spaces immediately after original text.
            // =====================================================

            int position =
                    matcher.end();


            int spacesAvailable =
                    0;


            while (
                    position + spacesAvailable
                            < content.length()

                    && content.charAt(
                            position
                            + spacesAvailable
                    ) == ' '
            ) {

                spacesAvailable++;
            }


            // =====================================================
            // Enough following spaces
            // =====================================================

            if (spacesAvailable >= difference) {

                result.append(
                        newText
                );


                lastPosition =
                        matcher.end()
                        + difference;


                System.out.println(
                        file.getFileName()
                        + ": "
                        + oldText
                        + " -> "
                        + newText
                );

            } else {

                // =================================================
                // Not enough spaces.
                // Keep original text unchanged.
                // =================================================

                result.append(
                        oldText
                );


                lastPosition =
                        matcher.end();


                System.err.println(
                        file.getFileName()
                        + ": not enough spaces after: "
                        + oldText
                );
            }
        }


        result.append(
                content,
                lastPosition,
                content.length()
        );


        return result.toString();
    }
}