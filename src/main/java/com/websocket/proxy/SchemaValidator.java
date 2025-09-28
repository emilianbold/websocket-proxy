package com.websocket.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    private int partialMatches = 0;
    private int recursiveValidations = 0;
    private final List<ValidationError> errors = new ArrayList<>();
    private final Set<String> seenMessages = new HashSet<>();
    private boolean deduplicateMessages = false;
    
    public SchemaValidator(Path schemaDirectory) {
        this(schemaDirectory, false);
    }

    public SchemaValidator(Path schemaDirectory, boolean deduplicateMessages) {
        this.schemaDirectory = schemaDirectory;
        this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        this.deduplicateMessages = deduplicateMessages;
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
                .filter(path -> path.toString().endsWith(".json"))
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
            String schemaKey = relativePath.toString().replace(".json", "").replace(File.separator, "/");
            
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

        // Get preferred schema keys first
        List<String> preferredSchemaKeys = getPreferredSchemaKeys(message, direction);

        // Try preferred schemas first
        for (String schemaKey : preferredSchemaKeys) {
            JsonSchema schema = schemaCache.get(schemaKey);
            if (schema != null) {
                Set<ValidationMessage> validationMessages = schema.validate(message);
                if (validationMessages.isEmpty() && isValidSchemaMatch(schema, message)) {
                    logger.info("Validated with preferred schema: {}", schemaKey);
                    validMessages++;
                    validateRecursively(message, direction, timestamp, lineNumber);
                    return;
                }
            }
        }

        // If no preferred schemas match, try all available schemas
        for (Map.Entry<String, JsonSchema> entry : schemaCache.entrySet()) {
            String schemaKey = entry.getKey();
            JsonSchema schema = entry.getValue();

            // Skip if already tried as preferred
            if (preferredSchemaKeys.contains(schemaKey)) {
                continue;
            }

            Set<ValidationMessage> validationMessages = schema.validate(message);
            if (validationMessages.isEmpty() && isValidSchemaMatch(schema, message)) {
                logger.debug("Validated with fallback schema: {}", schemaKey);
                validMessages++;
                validateRecursively(message, direction, timestamp, lineNumber);
                return;
            }
        }

        // No schema matched, but still validate recursively
        logger.warn("No matching schema found for message at line {} among {} schemas",
                   lineNumber, schemaCache.size());

        // Track if any recursive validations occur
        int recursiveCountBefore = recursiveValidations;
        validateRecursively(message, direction, timestamp, lineNumber);

        // Determine if this was a partial match (some embedded content validated)
        if (recursiveValidations > recursiveCountBefore) {
            partialMatches++;
            logger.info("Partial match at line {} - top-level failed but {} embedded validations succeeded",
                       lineNumber, recursiveValidations - recursiveCountBefore);
        } else {
            invalidMessages++;
        }

        // Create error with appropriate message
        String errorType = (recursiveValidations > recursiveCountBefore) ? "PARTIAL_MATCH" : "NO_SCHEMA_MATCH";
        ValidationError error = new ValidationError(
            lineNumber,
            timestamp,
            direction,
            message,
            Collections.emptySet(), // No specific validation errors, just no matching schema
            errorType
        );

        // Check for duplicates if deduplication is enabled
        if (deduplicateMessages) {
            String messageHash = getMessageHashWithoutId(message);
            if (seenMessages.contains(messageHash)) {
                logger.debug("Skipping duplicate message at line {}", lineNumber);
                return;
            }
            seenMessages.add(messageHash);
        }

        errors.add(error);
    }
    
    private List<String> getPreferredSchemaKeys(JsonNode message, String direction) {
        List<String> preferredKeys = new ArrayList<>();
        String baseKey = direction.toLowerCase().replace("_", "-");

        // For JSON-RPC messages, use method-based selection
        if (message.has("jsonrpc")) {
            if (message.has("method")) {
                // Request message
                String method = message.get("method").asText();
                preferredKeys.add(baseKey + "/" + method);
            } else if (message.has("result")) {
                // Response message
                preferredKeys.add(baseKey + "/response");
            } else if (message.has("error")) {
                // Error message
                preferredKeys.add(baseKey + "/error");
            }
        }

        // Always try default schema for direction
        preferredKeys.add(baseKey + "/default");

        return preferredKeys;
    }

    private boolean isValidSchemaMatch(JsonSchema schema, JsonNode message) {
        // Check if the schema actually validates something meaningful
        // by testing against a minimal valid message that should fail

        // Create a minimal valid JSON object that should fail most schemas
        JsonNode minimalMessage = objectMapper.createObjectNode();

        // Test if the schema rejects this minimal message
        Set<ValidationMessage> minimalValidation = schema.validate(minimalMessage);

        // If the schema accepts the minimal message, it's too permissive
        // Only accept schemas that actually validate something
        return !minimalValidation.isEmpty();
    }

    private String getMessageHashWithoutId(JsonNode message) {
        if (!message.isObject()) {
            return message.toString();
        }

        // Create a copy without the "id" field for deduplication
        ObjectNode messageWithoutId = objectMapper.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = message.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!"id".equals(field.getKey())) {
                messageWithoutId.set(field.getKey(), field.getValue());
            }
        }

        return messageWithoutId.toString();
    }

    private void validateRecursively(JsonNode node, String direction, String timestamp, int lineNumber) {
        if (node.isObject()) {
            // Check all string values in the object
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();

                if (value.isTextual()) {
                    String textValue = value.asText();
                    if (isPotentialJsonString(textValue)) {
                        try {
                            JsonNode embeddedJson = objectMapper.readTree(textValue);
                            recursiveValidations++;
                            validateMessage(embeddedJson, direction, timestamp, lineNumber);
                        } catch (Exception e) {
                            // Not valid JSON, continue
                        }
                    }
                } else if (value.isObject() || value.isArray()) {
                    // Recursively check nested objects and arrays
                    validateRecursively(value, direction, timestamp, lineNumber);
                }
            }
        } else if (node.isArray()) {
            // Check all elements in the array
            for (JsonNode element : node) {
                if (element.isTextual()) {
                    String textValue = element.asText();
                    if (isPotentialJsonString(textValue)) {
                        try {
                            JsonNode embeddedJson = objectMapper.readTree(textValue);
                            recursiveValidations++;
                            validateMessage(embeddedJson, direction, timestamp, lineNumber);
                        } catch (Exception e) {
                            // Not valid JSON, continue
                        }
                    }
                } else if (element.isObject() || element.isArray()) {
                    // Recursively check nested objects and arrays
                    validateRecursively(element, direction, timestamp, lineNumber);
                }
            }
        }
    }

    private boolean isPotentialJsonString(String text) {
        if (text == null || text.length() < 2) {
            return false;
        }

        String trimmed = text.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
               (trimmed.startsWith("[") && trimmed.endsWith("]"));
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
        System.out.println("Partial matches: " + partialMatches);
        System.out.println("Invalid messages: " + invalidMessages);
        System.out.println("Recursive validations: " + recursiveValidations);

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

        if (partialMatches > 0) {
            double partialRate = totalMessages > 0
                ? (100.0 * partialMatches / totalMessages)
                : 0.0;
            System.out.printf("Partial match rate: %.2f%%\n", partialRate);
        }
    }
    
    private void generateDetailedReport() {
        generateSummaryReport();

        if (!errors.isEmpty()) {
            System.out.println("\n=== Detailed Validation Errors ===");

            for (ValidationError error : errors) {
                System.out.println("\nLine " + error.lineNumber + " [" + error.timestamp + "] " + error.direction);
                System.out.println("Error Type: " + error.errorType);
                System.out.println("Message: " + error.message.toString());

                if (error.validationMessages.isEmpty()) {
                    if ("PARTIAL_MATCH".equals(error.errorType)) {
                        System.out.println("Errors: Top-level schema not found, but embedded content validated");
                    } else {
                        System.out.println("Errors: No matching schema found");
                    }
                } else {
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
    }
    
    private void generateJsonReport() {
        try {
            Map<String, Object> report = new HashMap<>();
            report.put("totalMessages", totalMessages);
            report.put("validMessages", validMessages);
            report.put("partialMatches", partialMatches);
            report.put("invalidMessages", invalidMessages);
            report.put("recursiveValidations", recursiveValidations);

            if (!errors.isEmpty()) {
                List<Map<String, Object>> errorList = new ArrayList<>();
                for (ValidationError error : errors) {
                    Map<String, Object> errorMap = new HashMap<>();
                    errorMap.put("line", error.lineNumber);
                    errorMap.put("timestamp", error.timestamp);
                    errorMap.put("direction", error.direction);
                    errorMap.put("message", error.message);
                    errorMap.put("errorType", error.errorType);

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
        final String errorType;

        ValidationError(int lineNumber, String timestamp, String direction,
                       JsonNode message, Set<ValidationMessage> validationMessages, String errorType) {
            this.lineNumber = lineNumber;
            this.timestamp = timestamp;
            this.direction = direction;
            this.message = message;
            this.validationMessages = validationMessages;
            this.errorType = errorType;
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

        Option deduplicate = new Option("d", "deduplicate", false,
            "Remove duplicate messages from error output");
        deduplicate.setRequired(false);
        options.addOption(deduplicate);
        
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
        boolean shouldDeduplicate = cmd.hasOption("deduplicate");

        OutputFormat format;
        try {
            format = OutputFormat.valueOf(formatStr);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid output format: " + formatStr);
            System.exit(1);
            return;
        }

        SchemaValidator validator = new SchemaValidator(schemaPath, shouldDeduplicate);
        validator.validateLogFile(logPath, format);
    }
}