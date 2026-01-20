# OLS4 Security Scanning Implementation

This document describes the comprehensive security scanning setup implemented for OLS4, providing GitHub-native alternatives to GitLab's security features.

## Overview

The security scanning implementation covers:
- **SAST (Static Application Security Testing)**: Code analysis for vulnerabilities
- **Container Scanning**: Docker image vulnerability detection
- **Secret Detection**: Preventing credential leaks
- **IaC Security**: Infrastructure configuration validation
- **Dependency Scanning**: Monitoring for vulnerable dependencies

## GitLab to GitHub Security Feature Mapping

| GitLab Feature | GitHub Equivalent | Status | Workflow |
|----------------|-------------------|--------|----------|
| GitLab SAST | CodeQL | ✅ Implemented | `security-codeql.yml` |
| Container Scanning | Trivy | ✅ Implemented | `security-container-scan.yml` + `docker.yml` |
| Secret Detection | Gitleaks + TruffleHog | ✅ Implemented | `security-secrets.yml` |
| Dependency Scanning | Dependabot | ✅ Implemented | `dependabot.yml` |
| IaC Security | Trivy + KICS + Checkov | ✅ Implemented | `security-iac.yml` |

---

## 1. SAST - CodeQL Analysis

**File**: `.github/workflows/security-codeql.yml`

### What it does
- Analyzes Java/Kotlin code (backend, dataload)
- Analyzes JavaScript/TypeScript code (frontend)
- Detects security vulnerabilities and code quality issues

### Languages Covered
- ✅ Java (Spring Boot backend)
- ✅ TypeScript/JavaScript (React frontend)

### Scan Types
- **Security-extended queries**: Comprehensive security vulnerability detection
- **Security-and-quality queries**: Both security and code quality issues

### Detected Vulnerabilities
- SQL injection
- Cross-site scripting (XSS)
- Path traversal
- Unsafe deserialization
- Command injection
- Authentication/authorization issues
- Resource leaks
- Null pointer dereferences

### When it runs
- ✅ On push to `dev` and `stable` branches
- ✅ On pull requests to `dev`
- ✅ Weekly scheduled scan (Mondays at 2 AM UTC)
- ✅ Manual trigger via workflow_dispatch

### Viewing Results
- Navigate to **Security** → **Code scanning alerts**
- Results are categorized by language
- Click on any alert for detailed remediation guidance

---

## 2. Container Security Scanning

**Files**:
- `.github/workflows/security-container-scan.yml` (post-build scanning)
- `.github/workflows/docker.yml` (inline build-time scanning)

### What it does
- Scans Docker images for CVEs in OS packages
- Detects vulnerable application dependencies
- Scans for secrets accidentally baked into images
- Validates container configurations

### Images Scanned
- ✅ `ols4-backend`
- ✅ `ols4-frontend`
- ✅ `ols4-dataload`

### Scanning Tools
- **Trivy**: Multi-purpose container scanner
  - OS package vulnerabilities
  - Application dependency vulnerabilities
  - Secret detection in images
  - Configuration issues

### Scanning Strategy

#### Build-time Scanning (docker.yml)
- Scans images **before** pushing to ghcr.io
- Blocks on CRITICAL/HIGH vulnerabilities
- Fast feedback during CI/CD

#### Post-deployment Scanning (security-container-scan.yml)
- Comprehensive scan after images are published
- Weekly scheduled scans of production images
- Generates detailed JSON reports

### When it runs

**Build-time** (docker.yml):
- ✅ Every push to `dev` and `stable`
- ✅ Every pull request to `dev`

**Post-build** (security-container-scan.yml):
- ✅ After successful image build
- ✅ Weekly scheduled scan (Sundays at 3 AM UTC)
- ✅ Manual trigger

### Viewing Results
- Navigate to **Security** → **Code scanning alerts**
- Filter by category: `container-ols4-backend`, `container-ols4-frontend`, `container-ols4-dataload`
- Download detailed JSON reports from workflow artifacts

---

## 3. Secret Detection

**File**: `.github/workflows/security-secrets.yml`

### What it does
- Scans entire git history for exposed secrets
- Detects API keys, passwords, tokens, and credentials
- Validates if detected secrets are real (TruffleHog)

### Detection Tools

#### Gitleaks
- Pattern-based secret detection
- Covers 130+ secret types
- Fast and efficient scanning
- SARIF output for GitHub Security tab

#### TruffleHog
- Secret verification (checks if secrets are valid)
- Reduces false positives
- Focuses on verified credentials

### Detected Secret Types
- AWS credentials
- GitHub tokens
- Database passwords
- API keys (Google, Slack, Stripe, etc.)
- Private keys (SSH, RSA, etc.)
- OAuth tokens
- Generic passwords in code

### When it runs
- ✅ On push to `dev` and `stable`
- ✅ On pull requests to `dev`
- ✅ Weekly full history scan (Saturdays at 4 AM UTC)
- ✅ Manual trigger

### What to do if secrets are found
1. **Rotate the exposed credential immediately**
2. Review how it was committed
3. Use GitHub Secrets or environment variables instead
4. Consider using tools like `git-filter-repo` to remove from history (if needed)

### Viewing Results
- Navigate to **Security** → **Code scanning alerts**
- Filter by category: `secret-detection`
- Each alert includes the secret type and location

---

## 4. Infrastructure as Code (IaC) Security

**File**: `.github/workflows/security-iac.yml`

### What it does
- Validates Kubernetes manifests
- Scans Dockerfiles for best practices
- Detects misconfigurations in Helm charts

### Scanned Components
- ✅ Kubernetes manifests (`k8chart/`, `k8chart-dev/`)
- ✅ Dockerfiles (`backend/`, `frontend/`, `dataload/`)
- ✅ Helm charts

### Security Tools

#### Trivy IaC Scanner
- Kubernetes misconfigurations
- Docker security issues
- Network policy validation
- Resource limit checks

#### KICS (Keeping Infrastructure as Code Secure)
- 2000+ security rules
- Kubernetes & Docker coverage
- CIS benchmarks
- Best practice validation

#### Checkov
- Policy-as-code validation
- Infrastructure compliance checks
- Cloud provider best practices

#### Hadolint
- Dockerfile linting
- Best practice enforcement
- Security recommendations

#### kubeconform
- Kubernetes schema validation
- API version compatibility
- Resource definition validation

### Common Issues Detected
- Missing resource limits/requests
- Privileged containers
- Running as root user
- Missing security contexts
- Exposed sensitive ports
- Insecure image pull policies
- Missing network policies
- Hardcoded secrets in manifests

### When it runs
- ✅ On push to `dev`/`stable` (when IaC files change)
- ✅ On pull requests to `dev` (when IaC files change)
- ✅ Weekly scheduled scan (Thursdays at 5 AM UTC)
- ✅ Manual trigger

### Viewing Results
- Navigate to **Security** → **Code scanning alerts**
- Filter by categories:
  - `iac-trivy`
  - `iac-kics`
  - `iac-checkov`
  - `dockerfile-lint`

---

## 5. Dependency Scanning (Dependabot)

**File**: `.github/dependabot.yml`

### What it does
- Monitors dependencies for known vulnerabilities
- Automatically creates PRs to update vulnerable packages
- Tracks outdated dependencies

### Ecosystems Monitored

#### Maven (Backend + Dataload)
- Java dependencies
- Spring Boot libraries
- Testing frameworks
- Scans: Monday, 4 AM UTC

#### npm (Frontend)
- React and related packages
- TypeScript dependencies
- Testing libraries
- Scans: Tuesday, 4 AM UTC

#### Docker
- Base image updates
- OS package updates
- Scans: Wednesday, 4 AM UTC

#### GitHub Actions
- Action version updates
- Security patches
- Scans: Thursday, 4 AM UTC

### Features

#### Automatic PR Creation
- Creates PRs with dependency updates
- Groups related dependencies (e.g., all Spring Boot packages)
- Includes changelog and release notes

#### Smart Grouping
- **Backend**: Spring Boot, Testing frameworks
- **Frontend**: React, MUI, Testing libraries

#### Configuration
- Max 10 open PRs for backend/frontend
- Max 5 open PRs for Docker/Actions
- Auto-assigns reviewers (requires team configuration)
- Labels PRs with: `dependencies`, `security`, component

### Viewing Results
- Navigate to **Security** → **Dependabot alerts**
- Review dependency PRs in **Pull Requests** tab
- Check severity: Critical, High, Moderate, Low

### Responding to Alerts
1. Review the Dependabot PR
2. Check the changelog for breaking changes
3. Run tests locally or wait for CI
4. Merge if tests pass and changes are safe
5. Deploy updated dependencies

---

## Security Dashboard Navigation

### GitHub Security Tab

All security findings are centralized in the **Security** tab:

```
Repository → Security Tab
├── Overview (summary of all alerts)
├── Dependabot alerts (vulnerable dependencies)
├── Code scanning alerts
│   ├── CodeQL (SAST findings)
│   ├── Trivy (container vulnerabilities)
│   ├── Gitleaks (secret detection)
│   ├── KICS/Checkov (IaC issues)
│   └── Hadolint (Dockerfile issues)
└── Secret scanning alerts (GitHub native)
```

### Filtering Alerts
- By severity: Critical, High, Medium, Low
- By tool/category
- By state: Open, Fixed, Dismissed
- By branch

---

## Workflow Schedule Summary

| Day | Time (UTC) | Workflow | Purpose |
|-----|-----------|----------|---------|
| Monday | 02:00 | CodeQL | SAST scan |
| Monday | 04:00 | Dependabot (Maven) | Backend dependencies |
| Tuesday | 04:00 | Dependabot (npm) | Frontend dependencies |
| Wednesday | 04:00 | Dependabot (Docker) | Container base images |
| Thursday | 04:00 | Dependabot (Actions) | GitHub Actions |
| Thursday | 05:00 | IaC Security | Infrastructure validation |
| Saturday | 04:00 | Secret Detection | Full history scan |
| Sunday | 03:00 | Container Scanning | Production image scan |

---

## CI/CD Integration

### Pull Request Checks

When a PR is created, the following scans run automatically:
1. ✅ CodeQL SAST scan
2. ✅ Secret detection (Gitleaks + TruffleHog)
3. ✅ IaC security (if K8s/Docker files changed)
4. ✅ Container build + inline Trivy scan

### Branch Protection (Recommended)

Enable branch protection rules for `dev` and `stable`:

```yaml
Required status checks:
  - CodeQL / Analyze Java
  - CodeQL / Analyze JavaScript/TypeScript
  - Gitleaks Secret Scanning
  - Build & publish ols4 images
```

This ensures no code with security issues is merged.

---

## Kubernetes Deployment Security

### Current Setup
- **Primary cluster**: Production workloads
- **Fallback cluster**: Disaster recovery

### Security Considerations

#### Pre-deployment Validation
1. ✅ IaC security scanning validates manifests
2. ✅ Container images scanned before push
3. ✅ Helm charts validated with KICS/Checkov

#### Deployment Best Practices
- Use specific image tags (SHA-based), not `latest`
- Enable Pod Security Standards
- Apply Network Policies
- Use Kubernetes Secrets for sensitive data
- Enable audit logging
- Regular security updates via Dependabot

#### Monitoring in Production
- Weekly container scans detect new CVEs
- Dependabot alerts for base image updates
- Consider runtime security tools (Falco, Sysdig)

---

## Troubleshooting

### CodeQL Scan Failures

**Issue**: CodeQL fails to build Java code
```
Solution:
- Check Maven build succeeds locally
- Ensure pom.xml is valid
- Review CodeQL logs in workflow
```

**Issue**: CodeQL skipping too many files
```
Solution:
- Review .github/codeql/codeql-config.yml
- Check paths and paths-ignore settings
- Ensure source code is in expected locations
```

### Container Scan Issues

**Issue**: Too many vulnerabilities found
```
Solution:
1. Update base images in Dockerfiles
2. Review Trivy reports for false positives
3. Create .trivyignore file for known acceptable risks
4. Update application dependencies
```

**Issue**: Scan timeout
```
Solution:
- Increase timeout in workflow (default 10m)
- Reduce scan scope
- Use specific vulnerability databases
```

### Secret Detection False Positives

**Issue**: Fake secrets triggering alerts
```
Solution:
1. Review if it's actually a secret
2. Add to .gitleaksignore if false positive
3. Use placeholders like EXAMPLE_API_KEY
```

### IaC Scan Warnings

**Issue**: Many low-priority issues
```
Solution:
1. Focus on CRITICAL and HIGH first
2. Create exceptions for accepted risks
3. Document security decisions
4. Update resource definitions incrementally
```

---

## Additional Recommendations

### 1. Enable GitHub Advanced Security (if available)
- Enhanced secret scanning
- Code scanning autofix suggestions
- Supply chain security

### 2. Implement SBOM (Software Bill of Materials)
- Track all dependencies
- Vulnerability tracking
- License compliance

### 3. Runtime Security
- Consider Falco for Kubernetes
- Enable audit logging
- Monitor for anomalies

### 4. Security Training
- Regular security awareness training
- Secure coding practices
- Incident response procedures

---

## Support and Documentation

### GitHub Security Resources
- [Code scanning documentation](https://docs.github.com/en/code-security/code-scanning)
- [Dependabot documentation](https://docs.github.com/en/code-security/dependabot)
- [Secret scanning documentation](https://docs.github.com/en/code-security/secret-scanning)

### Tool Documentation
- [CodeQL](https://codeql.github.com/docs/)
- [Trivy](https://aquasecurity.github.io/trivy/)
- [Gitleaks](https://github.com/gitleaks/gitleaks)
- [KICS](https://docs.kics.io/)
- [Checkov](https://www.checkov.io/documentation.html)

---

## Maintenance

### Regular Tasks
- [ ] Review security alerts weekly
- [ ] Merge Dependabot PRs after testing
- [ ] Update security scanning tools quarterly
- [ ] Review and update .trivyignore, .gitleaksignore as needed
- [ ] Audit security configurations monthly

### Quarterly Security Review
- [ ] Review all dismissed alerts
- [ ] Update security policies
- [ ] Audit access controls
- [ ] Review incident response procedures
- [ ] Update security documentation

---

**Last Updated**: January 2025
**Maintained by**: OLS4 Security Team
