# Code Signing - Necessary Code Signing

This document describes how Luma Music uses [Necessary Code Signing](https://sign.necessary.nu) for free code signing of Windows builds.

## Why Code Signing?

Windows SmartScreen blocks unsigned executables downloaded from the internet. Code signing with a trusted certificate eliminates these warnings, ensuring users can install Luma Music without extra steps.

## How It Works

1. Push a tag like `v2.1.0` to GitHub
2. GitHub Actions builds the Windows MSI and portable ZIP
3. Artifacts are signed using Necessary Code Signing API
4. The signed files are attached to the GitHub Release

## Setup Requirements

- Access granted by [Necessary Code Signing](https://sign.necessary.nu) (apply for free)
- GitHub repository secret: `SIGNING_TOKEN` - API token from Necessary Code Signing

## Free Code Signing

Free code signing provided by Necessary Code Signing.

## Links

- Necessary Code Signing: https://sign.necessary.nu
- Apply for access: https://sign.necessary.nu/#request-access
- Terms of Use: https://sign.necessary.nu/#terms
