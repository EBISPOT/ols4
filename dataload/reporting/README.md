# OLS Reporting Service

This module provides consolidated loading report generation for the OLS dataload pipeline.

## Overview

The reporting service has been factored out from rdf2json to work better with the nextflow pipeline architecture. Since rdf2json is now invoked once per ontology in parallel, we don't want to send multiple loading reports - just one consolidated report after all ontologies have been processed.

## How It Works

1. **Status File Generation**: Each rdf2json process writes a `.status.json` file alongside its output JSON file. This file contains:
   - Ontology ID
   - Status (SUCCESS, FALLBACK, or FAILED_NO_FALLBACK)
   - Error message (if applicable)
   - Version (if applicable)

2. **Status Collection**: The nextflow pipeline collects all status files after all rdf2json processes complete.

3. **Report Generation**: The reporting service runs as a separate nextflow process that:
   - Reads the OLS config file to get contact information
   - Collects all status files from the rdf2json processes
   - Generates a consolidated report
   - Optionally sends notifications to ontology maintainers and OLS developers

## Usage

### As part of Nextflow Pipeline

The reporting service is automatically invoked by the nextflow pipeline. No manual intervention required.

### Manual Invocation

```bash
java -jar reporting-1.0-SNAPSHOT.jar \
    --config /path/to/config.json \
    --statusDir /path/to/status/files \
    --reportFile /path/to/output/report.txt \
    --sendNotifications
```

### Options

- `--config`: Path to the OLS configuration JSON file (required)
- `--statusDir`: Directory containing individual ontology status JSON files (required)
- `--reportFile`: Output file for the consolidated report (optional)
- `--sendNotifications`: Flag to enable email/GitHub notifications (optional)

## Status File Format

Each status file is a JSON file with the following structure:

```json
{
  "ontologyId": "go",
  "status": "SUCCESS",
  "errorMessage": null,
  "version": "2024-01-01"
}
```

or for a failure:

```json
{
  "ontologyId": "bfo",
  "status": "FAILED_NO_FALLBACK",
  "errorMessage": "Could not parse OWL file",
  "version": null
}
```

or for a fallback:

```json
{
  "ontologyId": "efo",
  "status": "FALLBACK",
  "errorMessage": "Latest version failed to load",
  "version": "2023-12-01"
}
```

## Notification Behavior

When `--sendNotifications` is enabled:

1. **GitHub Issues**: For ontologies with a `repository` field in the config, the service will:
   - Create a GitHub issue if none exists for this problem
   - Update an existing issue if one already exists

2. **Email**: For ontologies with contact email information, the service will send email notifications

3. **OLS Developers**: A summary email is sent to the OLS_DEV_EMAIL address (if configured)

## Environment Variables

- `OLS_DEV_EMAIL`: Email address for OLS developers (optional)
- `SMTP_HOST`: SMTP server hostname (required for email)
- `SMTP_PORT`: SMTP server port (required for email)
- `SMTP_FROM`: From address for emails (required for email)
- `SMTP_PASSWORD`: SMTP password (required for email)
- `GITHUB_TOKEN`: GitHub personal access token (required for GitHub issues)

## Migration from Old System

Previously, the reporting functionality was integrated into rdf2json. This meant:
- Reports were generated during the rdf2json process
- When running ontologies in parallel, multiple reports could be sent
- Contact information was parsed multiple times unnecessarily

With the new architecture:
- Each rdf2json process writes a simple status file
- The reporting service runs once after all ontologies are processed
- Contact information is parsed only once
- A single consolidated report is generated and sent
