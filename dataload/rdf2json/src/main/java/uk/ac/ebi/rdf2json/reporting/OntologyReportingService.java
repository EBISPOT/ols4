package uk.ac.ebi.rdf2json.reporting;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for tracking ontology loading status and sending notifications.
 * Integrated into the RDF2JSON process to report on:
 * - Successfully loaded ontologies
 * - Ontologies using fallback versions
 * - Ontologies that failed to load with no fallback available
 */
public class OntologyReportingService {

    private static final Logger logger = LoggerFactory.getLogger(OntologyReportingService.class);

    private static final String OLS_DEV_EMAIL = System.getenv("OLS_DEV_EMAIL");
    private static final String SMTP_HOST = System.getenv("SMTP_HOST");
    private static final String SMTP_PORT = System.getenv("SMTP_PORT");
    private static final String SMTP_FROM = System.getenv("SMTP_FROM");
    private static final String SMTP_PASSWORD = System.getenv("SMTP_PASSWORD");
    private static final String GITHUB_TOKEN = System.getenv("GITHUB_TOKEN");

    private final Map<String, OntologyLoadStatus> loadStatuses = new HashMap<>();
    private final Map<String, ContactInfo> contacts = new HashMap<>();
    private final List<String> configFilePaths;

    public OntologyReportingService(List<String> configFilePaths) {
        this.configFilePaths = configFilePaths;
        for (String configFilePath : configFilePaths) {
            try {
                parseConfigForContacts(configFilePath);
            } catch (IOException e) {
                logger.error("Failed to parse config file for contacts: {}", e.getMessage());
            }
        }
    }

    /**
     * Record that an ontology was successfully loaded.
     */
    public void recordSuccess(String ontologyId, String version) {
        loadStatuses.put(ontologyId.toLowerCase(), OntologyLoadStatus.success(ontologyId, version));
        logger.debug("Recorded success for ontology: {}", ontologyId);
    }

    /**
     * Record that an ontology failed but a fallback version was used.
     */
    public void recordFallback(String ontologyId, String version, String errorMessage) {
        loadStatuses.put(ontologyId.toLowerCase(), OntologyLoadStatus.fallback(ontologyId, version, errorMessage));
        logger.debug("Recorded fallback for ontology: {}", ontologyId);
    }

    /**
     * Record that an ontology failed to load with no fallback available.
     */
    public void recordFailedNoFallback(String ontologyId, String errorMessage) {
        loadStatuses.put(ontologyId.toLowerCase(), OntologyLoadStatus.failed(ontologyId, errorMessage));
        logger.debug("Recorded failed (no fallback) for ontology: {}", ontologyId);
    }

    /**
     * Generate and print the report, and optionally send notifications.
     */
    public void generateReportAndNotify(String reportFilePath, boolean sendNotifications) {
        logger.info("Generating ontology load report...");

        List<OntologyLoadStatus> issues = loadStatuses.values().stream()
            .filter(OntologyLoadStatus::hasIssue)
            .collect(Collectors.toList());

        // Print report to console
        printReport(issues);

        // Write report to file if specified
        if (reportFilePath != null) {
            try {
                writeReportToFile(issues, reportFilePath);
            } catch (IOException e) {
                logger.error("Failed to write report to file: {}", e.getMessage());
            }
        }

        // Send notifications if requested
        if (sendNotifications && !issues.isEmpty()) {
            sendNotifications(issues);
        }

        logger.info("Report generation completed. Total ontologies: {}, Issues: {}",
            loadStatuses.size(), issues.size());
    }

    private void printReport(List<OntologyLoadStatus> issues) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("OLS ONTOLOGY LOAD REPORT");
        System.out.println("=".repeat(60));
        System.out.println("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        System.out.println("Total Ontologies Processed: " + loadStatuses.size());
        System.out.println("Issues Found: " + issues.size());

        if (issues.isEmpty()) {
            System.out.println("\n✅ All ontologies loaded successfully!");
            return;
        }

        // Group issues by status
        Map<OntologyLoadStatus.Status, Long> statusCounts = issues.stream()
            .collect(Collectors.groupingBy(OntologyLoadStatus::getStatus, Collectors.counting()));

        System.out.println("\nIssue Summary:");
        statusCounts.forEach((status, count) ->
            System.out.println("  " + status + ": " + count));

        System.out.println("\nDetailed Issues:");
        for (OntologyLoadStatus issue : issues) {
            System.out.println("\n⚠️  " + issue.getOntologyId() + " (" + issue.getStatus() + ")");
            if (issue.getErrorMessage() != null) {
                System.out.println("    Error: " + issue.getErrorMessage());
            }
            if (issue.getVersion() != null) {
                System.out.println("    Fallback Version: " + issue.getVersion());
            }
            ContactInfo contact = contacts.get(issue.getOntologyId().toLowerCase());
            if (contact != null && contact.getEmail() != null) {
                System.out.println("    Contact: " + contact.getEmail());
            }
        }
        System.out.println("\n" + "=".repeat(60));
    }

    private void writeReportToFile(List<OntologyLoadStatus> issues, String reportPath) throws IOException {
        logger.info("Writing report to: {}", reportPath);

        try (PrintWriter writer = new PrintWriter(new FileWriter(reportPath))) {
            writer.println("OLS Ontology Load Report");
            writer.println("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            writer.println("Total Ontologies Processed: " + loadStatuses.size());
            writer.println("Issues Found: " + issues.size());
            writer.println();

            for (OntologyLoadStatus issue : issues) {
                writer.println("=".repeat(50));
                writer.println("Ontology ID: " + issue.getOntologyId());
                writer.println("Status: " + issue.getStatus());
                if (issue.getErrorMessage() != null) {
                    writer.println("Error: " + issue.getErrorMessage());
                }
                if (issue.getVersion() != null) {
                    writer.println("Version: " + issue.getVersion());
                }
                ContactInfo contact = contacts.get(issue.getOntologyId().toLowerCase());
                if (contact != null && contact.getEmail() != null) {
                    writer.println("Contact: " + contact.getName() + " <" + contact.getEmail() + ">");
                }
                writer.println();
            }
        }
    }

    private void sendNotifications(List<OntologyLoadStatus> issues) {
        logger.info("Sending notifications for {} issues", issues.size());

        List<OntologyLoadStatus> olsDevIssues = new ArrayList<>();

        for (OntologyLoadStatus issue : issues) {
            boolean notificationSent = false;
            ContactInfo contact = contacts.get(issue.getOntologyId().toLowerCase());

            if (contact == null) {
                logger.warn("No contact information found for ontology: {}", issue.getOntologyId());
                olsDevIssues.add(issue);
                continue;
            }

            // Skip notifications for deprecated ontologies
            if (contact.isDeprecated()) {
                logger.info("Skipping notification for deprecated ontology: {}", issue.getOntologyId());
                olsDevIssues.add(issue);
                continue;
            }

            // Try GitHub issue creation first
            if (contact.getRepository() != null) {
                try {
                    if (createGitHubIssue(issue, contact)) {
                        logger.info("Created GitHub issue for ontology: {}", issue.getOntologyId());
                        notificationSent = true;
                    }
                } catch (Exception e) {
                    logger.info("Failed to create GitHub issue for {}: {}", issue.getOntologyId(), e.getMessage());
                }
            }

            // Fall back to email if GitHub issue wasn't created
            if (!notificationSent && contact.getEmail() != null && isEmailConfigured()) {
                try {
                    sendOwnerNotification(contact.getEmail(), Collections.singletonList(issue));
                    logger.info("Sent email notification for ontology: {}", issue.getOntologyId());
                    notificationSent = true;
                } catch (Exception e) {
                    logger.error("Failed to send email notification for {}: {}", issue.getOntologyId(), e.getMessage());
                }
            }

            if (!notificationSent) {
                logger.info("No notification method available for ontology: {} (no repository or email)",
                    issue.getOntologyId());
            }

            olsDevIssues.add(issue);
        }

        // Send summary email to OLS developers
        if (OLS_DEV_EMAIL != null && isEmailConfigured()) {
            try {
                sendOlsDevNotification(OLS_DEV_EMAIL, olsDevIssues);
                logger.info("Sent summary email to OLS developers: {}", OLS_DEV_EMAIL);
            } catch (Exception e) {
                logger.error("Failed to send summary email to OLS developers: {}", e.getMessage());
            }
        } else {
            logger.info("OLS_DEV_EMAIL or email not configured. Skipping OLS developer notification.");
        }
    }

    /*private boolean isEmailConfigured() {
        return SMTP_HOST != null && SMTP_PORT != null && SMTP_FROM != null && SMTP_PASSWORD != null;
    }*/

    private boolean isEmailConfigured() {
        return SMTP_HOST != null && SMTP_FROM != null;
    }

    private boolean createGitHubIssue(OntologyLoadStatus issue, ContactInfo contact) {
        if (GITHUB_TOKEN == null || GITHUB_TOKEN.isEmpty()) {
            logger.info("GITHUB_TOKEN not set. Skipping GitHub issue creation.");
            return false;
        }

        try {
            GitHub github = new GitHubBuilder().withOAuthToken(GITHUB_TOKEN).build();

            String repoUrl = contact.getRepository();
            String[] parts = repoUrl.replace("https://github.com/", "")
                .replace("http://github.com/", "").split("/");

            if (parts.length < 2) {
                logger.warn("Invalid repository URL format: {}", repoUrl);
                return false;
            }

            String owner = parts[0];
            String repo = parts[1].replaceAll("\\.git$", "");

            GHRepository repository = github.getRepository(owner + "/" + repo);

            // Generate unique title with ontology ID
            String title = generateIssueTitle(issue);

            // Check for existing open issues with the same title
            GHIssue existingIssue = findExistingIssue(repository, title);

            if (existingIssue != null) {
                // Issue already exists, add a comment with updated status
                logger.info("Found existing issue #{} for ontology {}. Adding update comment.",
                    existingIssue.getNumber(), issue.getOntologyId());

                logger.info("GitHub issue #{} already exist for ontology {} at {}. Doing nothing and moving on!",
                    existingIssue.getNumber(), issue.getOntologyId(), existingIssue.getHtmlUrl());

                return true;
            }

            // No existing issue found, create a new one
            StringBuilder body = new StringBuilder();

            switch (issue.getStatus()) {
                case FALLBACK:
                    body.append("## Issue Description\n\n");
                    body.append("The latest version of your ontology is failing to load in OLS. ");
                    body.append("We are currently serving the last successful version as a fallback.\n\n");
                    break;
                case FAILED_NO_FALLBACK:
                    body.append("## Issue Description\n\n");
                    body.append("Your ontology is failing to load in OLS and no previous successful version is available as a fallback. ");
                    body.append("This means the ontology is currently not available in OLS.\n\n");
                    break;
                default:
                    return false;
            }

            body.append("## Details\n\n");
            body.append("- **Ontology ID**: `").append(issue.getOntologyId()).append("`\n");
            body.append("- **Status**: `").append(issue.getStatus()).append("`\n");

            if (issue.getVersion() != null) {
                body.append("- **Fallback Version**: `").append(issue.getVersion()).append("`\n");
            }

            if (issue.getErrorMessage() != null) {
                body.append("- **Error Message**: \n```\n").append(issue.getErrorMessage()).append("\n```\n");
            }

            body.append("\n## Action Required\n\n");
            body.append("Please investigate and fix the loading issue to ensure the latest version can be indexed correctly in OLS.\n");

            body.append("\n---\n");
            body.append("*This issue was automatically created by the OLS Reporting Service.*\n");

            GHIssue createdIssue = repository.createIssue(title)
                .body(body.toString())
                .create();

            logger.info("Created GitHub issue #{} for ontology {} at {}",
                createdIssue.getNumber(), issue.getOntologyId(), createdIssue.getHtmlUrl());

            return true;

        } catch (Exception e) {
            logger.error("Error creating GitHub issue for ontology {}: {}", issue.getOntologyId(), e.getMessage());
            return false;
        }
    }

    /**
     * Generate a unique issue title using the ontology ID.
     */
    private String generateIssueTitle(OntologyLoadStatus issue) {
        String upperOntologyId = issue.getOntologyId().toUpperCase();

        switch (issue.getStatus()) {
            case FALLBACK:
                return "[OLS] " + upperOntologyId + " is failing to load - using fallback version";
            case FAILED_NO_FALLBACK:
                return "[OLS] " + upperOntologyId + " is failing to load - no fallback available";
            default:
                return "[OLS] " + upperOntologyId + " loading issue";
        }
    }

    /**
     * Search for existing open issues with the same title.
     */
    private GHIssue findExistingIssue(GHRepository repository, String expectedTitle) {
        try {
            List<GHIssue> openIssues = repository.getIssues(org.kohsuke.github.GHIssueState.OPEN);

            for (GHIssue existingIssue : openIssues) {
                if (existingIssue.getTitle().equals(expectedTitle)) {
                    return existingIssue;
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to search for existing issues: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Generate an update comment for an existing issue.
     */
    private String generateUpdateComment(OntologyLoadStatus issue) {
        StringBuilder comment = new StringBuilder();

        comment.append("## Status Update\n\n");
        comment.append("This issue is still occurring as of **")
            .append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .append("**.\n\n");

        comment.append("### Current Status\n\n");
        comment.append("- **Status**: `").append(issue.getStatus()).append("`\n");

        if (issue.getVersion() != null) {
            comment.append("- **Fallback Version**: `").append(issue.getVersion()).append("`\n");
        }

        if (issue.getErrorMessage() != null) {
            comment.append("\n### Latest Error\n\n");
            comment.append("```\n").append(issue.getErrorMessage()).append("\n```\n");
        }

        comment.append("\n---\n");
        comment.append("*This update was automatically posted by the OLS Reporting Service.*\n");

        return comment.toString();
    }

    private void sendOwnerNotification(String ownerEmail, List<OntologyLoadStatus> issues) {
        try {
            String subject = "OLS Ontology Loading Issues - " + issues.size() + " issue(s) detected";

            StringBuilder body = new StringBuilder();
            body.append("Dear Ontology Maintainer,\n\n");
            body.append("The OLS data loading process has detected issues with your ontology(ies):\n\n");

            for (OntologyLoadStatus issue : issues) {
                body.append("Ontology: ").append(issue.getOntologyId()).append("\n");
                body.append("Status: ").append(issue.getStatus()).append("\n");
                if (issue.getErrorMessage() != null) {
                    body.append("Error: ").append(issue.getErrorMessage()).append("\n");
                }
                if (issue.getVersion() != null) {
                    body.append("Fallback Version: ").append(issue.getVersion()).append("\n");
                }
                body.append("\n");
            }

            body.append("Please investigate and resolve these issues to ensure your ontology loads correctly in OLS.\n\n");
            body.append("Best regards,\n");
            body.append("OLS Team\n");

            sendEmail(ownerEmail, subject, body.toString());
            logger.info("Sent notification to owner: {}", ownerEmail);

        } catch (Exception e) {
            logger.error("Failed to send notification to {}: {}", ownerEmail, e.getMessage());
        }
    }

    private void sendOlsDevNotification(String olsDevEmail, List<OntologyLoadStatus> issues) {
        try {
            String subject = "OLS Build Report - " + issues.size() + " ontology issue(s) detected";

            StringBuilder body = new StringBuilder();
            body.append("OLS Development Team,\n\n");
            body.append("The OLS data loading process has completed with the following results:\n\n");
            body.append("Total Ontologies Processed: ").append(loadStatuses.size()).append("\n");
            body.append("Issues Detected: ").append(issues.size()).append("\n\n");

            if (!issues.isEmpty()) {
                Map<OntologyLoadStatus.Status, Long> statusCounts = issues.stream()
                    .collect(Collectors.groupingBy(OntologyLoadStatus::getStatus, Collectors.counting()));

                body.append("Issue Summary:\n");
                statusCounts.forEach((status, count) ->
                    body.append("  ").append(status).append(": ").append(count).append("\n"));

                body.append("\nDetailed Issues:\n");
                for (OntologyLoadStatus issue : issues) {
                    body.append("- ").append(issue.getOntologyId()).append(" (").append(issue.getStatus()).append(")\n");
                    if (issue.getErrorMessage() != null) {
                        body.append("  Error: ").append(issue.getErrorMessage()).append("\n");
                    }
                    ContactInfo contact = contacts.get(issue.getOntologyId().toLowerCase());
                    if (contact != null && contact.getEmail() != null) {
                        body.append("  Contact: ").append(contact.getEmail()).append("\n");
                    }
                    body.append("\n");
                }
            } else {
                body.append("✅ No issues detected - all ontologies loaded successfully!\n\n");
            }

            body.append("This automated report was generated by the OLS Reporting Service.\n");

            sendEmail(olsDevEmail, subject, body.toString());
            logger.info("Sent summary notification to OLS developers: {}", olsDevEmail);

        } catch (Exception e) {
            logger.error("Failed to send notification to OLS developers {}: {}", olsDevEmail, e.getMessage());
        }
    }

    private void sendEmail(String to, String subject, String body) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);
        props.put("mail.smtp.auth", "true");

        props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        props.put("mail.smtp.ssl.trust", SMTP_HOST);
        props.put("mail.smtp.connectiontimeout", "30000");
        props.put("mail.smtp.timeout", "30000");
        props.put("mail.smtp.writetimeout", "30000");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SMTP_FROM, SMTP_PASSWORD);
            }
        });

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(SMTP_FROM));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject);
        message.setText(body);

        Transport.send(message);
    }

    private void parseConfigForContacts(String configPath) throws IOException {
        Gson gson = new Gson();

        logger.info("Parsing contact information from: {}", configPath);

        try (JsonReader reader = new JsonReader(getConfigReader(configPath))) {
            reader.beginObject();

            while (reader.peek() != JsonToken.END_OBJECT) {
                String name = reader.nextName();

                if ("ontologies".equals(name)) {
                    reader.beginArray();

                    while (reader.peek() != JsonToken.END_ARRAY) {
                        Map<String, Object> ontologyConfig = gson.fromJson(reader, Map.class);
                        String ontologyId = (String) ontologyConfig.get("id");
                        if (ontologyId != null) {
                            ontologyId = ontologyId.toLowerCase();

                            ContactInfo contact = new ContactInfo();

                            Object contactObj = ontologyConfig.get("contact");
                            if (contactObj instanceof Map) {
                                Map<String, Object> contactMap = (Map<String, Object>) contactObj;
                                contact.setEmail((String) contactMap.get("email"));
                                contact.setName((String) contactMap.get("label"));
                                contact.setGithub((String) contactMap.get("github"));
                            }

                            if (contact.getEmail() == null) {
                                contact.setEmail((String) ontologyConfig.get("mailing_list"));
                            }

                            contact.setTitle((String) ontologyConfig.get("title"));
                            contact.setHomepage((String) ontologyConfig.get("homepage"));
                            contact.setRepository((String) ontologyConfig.get("repository"));
                            contact.setTracker((String) ontologyConfig.get("tracker"));

                            // Parse is_deprecated flag
                            Object isDeprecatedObj = ontologyConfig.get("is_deprecated");
                            if (isDeprecatedObj instanceof Boolean) {
                                contact.setDeprecated((Boolean) isDeprecatedObj);
                            }

                            // Check if we already have a contact for this ontology (from this or a previous config file)
                            ContactInfo existingContact = this.contacts.get(ontologyId);
                            if (existingContact != null) {
                                // Update the is_deprecated flag from this config
                                if (isDeprecatedObj instanceof Boolean) {
                                    existingContact.setDeprecated((Boolean) isDeprecatedObj);
                                }
                            } else if (contact.hasContactMethod()) {
                                // Only add new contact if it has contact methods
                                this.contacts.put(ontologyId, contact);
                            }
                        }
                    }

                    reader.endArray();
                } else {
                    reader.skipValue();
                }
            }

            reader.endObject();
        }

        logger.info("Parsed contact information for {} ontologies", this.contacts.size());
    }

    private Reader getConfigReader(String configPath) throws IOException {
        if (configPath.contains("://")) {
            return new InputStreamReader(new URL(configPath).openStream());
        } else {
            return new FileReader(configPath);
        }
    }
}
