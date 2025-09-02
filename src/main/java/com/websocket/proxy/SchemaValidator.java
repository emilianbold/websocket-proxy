package com.websocket.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.*;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SchemaValidator {
    private static final Logger logger = LoggerFactory.getLogger(SchemaValidator.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Pattern to match log lines with JSON content
    private static final Pattern LOG_LINE_PATTERN = Pattern.compile(
        "^\\[(.*?)\\] \\[CONN_(\\d+)\\] \\[(CLIENT_TO_SERVER|SERVER_TO_CLIENT)\\] (.*)$"
    );
    
    private final Map<String, JsonSchema> schemaCache = new HashMap<>();
    private final Path schemaDirectory;
    private final JsonSchemaFactory schemaFactory;
    
    // Validation statistics
    private int totalMessages = 0;
    private int validMessages = 0;
    private int invalidMessages = 0;
    private final List<ValidationError> errors = new ArrayList<>();
    
    public SchemaValidator(Path schemaDirectory) {
        this.schemaDirectory = schemaDirectory;
        this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        loadSchemas();
    }
    
    private void loadSchemas() {
        if (!Files.exists(schemaDirectory)) {
            logger.warn("Schema directory does not exist: {}", schemaDirectory);
            return;
        }
        
        try {
            // Load all schema files recursively
            Files.walk(schemaDirectory)
                .filter(path -> path.toString().endsWith(".schema.json"))
                .forEach(this::loadSchema);
                
            logger.info("Loaded {} schemas from {}", schemaCache.size(), schemaDirectory);
        } catch (IOException e) {
            logger.error("Failed to load schemas", e);
        }
    }
    
    private void loadSchema(Path schemaPath) {
        try {
            String schemaContent = Files.readString(schemaPath);
            JsonNode schemaNode = objectMapper.readTree(schemaContent);
            
            // Generate a key based on the relative path
            Path relativePath = schemaDirectory.relativize(schemaPath);
            String schemaKey = relativePath.toString().replace(".schema.json", "").replace(File.separator, "/");
            
            JsonSchema schema = schemaFactory.getSchema(schemaNode);
            schemaCache.put(schemaKey, schema);
            
            logger.debug("Loaded schema: {}", schemaKey);
        } catch (Exception e) {
            logger.error("Failed to load schema: {}", schemaPath, e);
        }
    }
    
    public void validateLogFile(Path logFile, OutputFormat outputFormat) {
        logger.info("Validating log file: {}", logFile);
        
        try (BufferedReader reader = Files.newBufferedReader(logFile)) {
            String line;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                processLogLine(line, lineNumber);
            }
            
            generateReport(outputFormat);
            
        } catch (IOException e) {
            logger.error("Failed to read log file", e);
        }
    }
    
    private void processLogLine(String line, int lineNumber) {
        Matcher matcher = LOG_LINE_PATTERN.matcher(line);
        if (!matcher.matches()) {
            // Skip non-matching lines (events, binary messages, etc.)
            return;
        }
        
        String timestamp = matcher.group(1);
        String connectionId = matcher.group(2);
        String direction = matcher.group(3);
        String jsonContent = matcher.group(4);
        
        // Skip binary message indicators
        if (jsonContent.startsWith("[BINARY]")) {
            return;
        }
        
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonContent);
            validateMessage(jsonNode, direction, timestamp, lineNumber);
        } catch (Exception e) {
            // Not valid JSON, skip
            logger.debug("Skipping non-JSON content at line {}", lineNumber);
        }
    }
    
    private void validateMessage(JsonNode message, String direction, String timestamp, int lineNumber) {
        totalMessages++;
        
        // Determine which schema to use
        JsonSchema schema = selectSchema(message, direction);
        
        if (schema == null) {
            logger.debug("No schema found for message at line {}", lineNumber);
            validMessages++; // Count as valid if no schema defined
            return;
        }
        
        // Validate the message
        Set<ValidationMessage> validationMessages = schema.validate(message);
        
        if (validationMessages.isEmpty()) {
            validMessages++;
        } else {
            invalidMessages++;
            
            ValidationError error = new ValidationError(
                lineNumber,
                timestamp,
                direction,
                message,
                validationMessages
            );
            errors.add(error);
        }
    }
    
    private JsonSchema selectSchema(JsonNode message, String direction) {
        String schemaKey = null;
        
        // For JSON-RPC messages, use method-based selection
        if (message.has("jsonrpc")) {
            if (message.has("method")) {
                // Request message
                String method = message.get("method").asText();
                schemaKey = direction.toLowerCase().replace("_", "-") + "/" + method;
            } else if (message.has("result")) {
                // Response message
                schemaKey = direction.toLowerCase().replace("_", "-") + "/response";
            } else if (message.has("error")) {
                // Error message
                schemaKey = direction.toLowerCase().replace("_", "-") + "/error";
            }
        }
        
        // Try to find specific schema
        JsonSchema schema = schemaCache.get(schemaKey);
        
        // Fall back to default schema for direction
        if (schema == null && schemaKey != null) {
            String defaultKey = direction.toLowerCase().replace("_", "-") + "/default";
            schema = schemaCache.get(defaultKey);
        }
        
        return schema;
    }
    
    private void generateReport(OutputFormat format) {
        switch (format) {
            case SUMMARY:
                generateSummaryReport();
                break;
            case DETAILED:
                generateDetailedReport();
                break;
            case JSON:
                generateJsonReport();
                break;
        }
    }
    
    private void generateSummaryReport() {
        System.out.println("\n=== Schema Validation Summary ===");
        System.out.println("Total messages: " + totalMessages);
        System.out.println("Valid messages: " + validMessages);
        System.out.println("Invalid messages: " + invalidMessages);
        
        if (!errors.isEmpty()) {
            System.out.println("\nValidation errors by type:");
            Map<String, Long> errorTypes = errors.stream()
                .flatMap(e -> e.validationMessages.stream())
                .collect(Collectors.groupingBy(
                    ValidationMessage::getType,
                    Collectors.counting()
                ));
            
            errorTypes.forEach((type, count) -> 
                System.out.println("  " + type + ": " + count));
        }
        
        double successRate = totalMessages > 0 
            ? (100.0 * validMessages / totalMessages) 
            : 100.0;
        System.out.printf("\nValidation success rate: %.2f%%\n", successRate);
    }
    
    private void generateDetailedReport() {
        generateSummaryReport();
        
        if (!errors.isEmpty()) {
            System.out.println("\n=== Detailed Validation Errors ===");
            
            for (ValidationError error : errors) {
                System.out.println("\nLine " + error.lineNumber + " [" + error.timestamp + "] " + error.direction);
                System.out.println("Message: " + error.message.toString());
                System.out.println("Errors:");
                
                for (ValidationMessage msg : error.validationMessages) {
                    System.out.println("  - " + msg.getType() + ": " + msg.getMessage());
                    if (msg.getPath() != null && !msg.getPath().isEmpty()) {
                        System.out.println("    Path: " + msg.getPath());
                    }
                }
            }
        }
    }
    
    private void generateJsonReport() {
        try {
            Map<String, Object> report = new HashMap<>();
            report.put("totalMessages", totalMessages);
            report.put("validMessages", validMessages);
            report.put("invalidMessages", invalidMessages);
            
            if (!errors.isEmpty()) {
                List<Map<String, Object>> errorList = new ArrayList<>();
                for (ValidationError error : errors) {
                    Map<String, Object> errorMap = new HashMap<>();
                    errorMap.put("line", error.lineNumber);
                    errorMap.put("timestamp", error.timestamp);
                    errorMap.put("direction", error.direction);
                    errorMap.put("message", error.message);
                    
                    List<Map<String, String>> violations = new ArrayList<>();
                    for (ValidationMessage msg : error.validationMessages) {
                        Map<String, String> violation = new HashMap<>();
                        violation.put("type", msg.getType());
                        violation.put("message", msg.getMessage());
                        if (msg.getPath() != null) {
                            violation.put("path", msg.getPath());
                        }
                        violations.add(violation);
                    }
                    errorMap.put("violations", violations);
                    errorList.add(errorMap);
                }
                report.put("errors", errorList);
            }
            
            String jsonReport = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(report);
            System.out.println(jsonReport);
            
        } catch (Exception e) {
            logger.error("Failed to generate JSON report", e);
        }
    }
    
    // Inner class to hold validation error information
    private static class ValidationError {
        final int lineNumber;
        final String timestamp;
        final String direction;
        final JsonNode message;
        final Set<ValidationMessage> validationMessages;
        
        ValidationError(int lineNumber, String timestamp, String direction, 
                       JsonNode message, Set<ValidationMessage> validationMessages) {
            this.lineNumber = lineNumber;
            this.timestamp = timestamp;
            this.direction = direction;
            this.message = message;
            this.validationMessages = validationMessages;
        }
    }
    
    // Output format enum
    public enum OutputFormat {
        SUMMARY, DETAILED, JSON
    }
    
    // Main method for command-line execution
    public static void main(String[] args) {
        Options options = new Options();
        
        Option logFile = new Option("l", "log-file", true, "Log file to validate");
        logFile.setRequired(true);
        options.addOption(logFile);
        
        Option schemaDir = new Option("s", "schema-dir", true, "Directory containing schema files");
        schemaDir.setRequired(true);
        options.addOption(schemaDir);
        
        Option outputFormat = new Option("f", "format", true, 
            "Output format: summary, detailed, or json (default: summary)");
        outputFormat.setRequired(false);
        options.addOption(outputFormat);
        
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;
        
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            formatter.printHelp("schema-validator", options);
            System.exit(1);
            return;
        }
        
        Path logPath = Paths.get(cmd.getOptionValue("log-file"));
        Path schemaPath = Paths.get(cmd.getOptionValue("schema-dir"));
        String formatStr = cmd.getOptionValue("format", "summary").toUpperCase();
        
        OutputFormat format;
        try {
            format = OutputFormat.valueOf(formatStr);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid output format: " + formatStr);
            System.exit(1);
            return;
        }
        
        SchemaValidator validator = new SchemaValidator(schemaPath);
        validator.validateLogFile(logPath, format);
    }
}